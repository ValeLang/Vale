package net.verdagon.vale.scout

import net.verdagon.vale.parser._
import net.verdagon.vale.{scout, vassert, vfail, vimpl, vwat}
import net.verdagon.vale.scout.Scout.{noDeclarations, noVariableUses}
import net.verdagon.vale.scout.patterns.{LetRuleState, PatternScout, RuleState, RuleStateBox}
import net.verdagon.vale.scout.predictor.Conclusions
import net.verdagon.vale.scout.rules.RuleScout
import net.verdagon.vale.scout.templatepredictor.PredictorEvaluator

object ExpressionScout {
  sealed trait IScoutResult[+T <: IExpressionSE]
  // Will contain the address of a local.
  case class LocalLookupResult(range: RangeS, name: IVarNameS) extends IScoutResult[IExpressionSE]
  // Looks up something that's not a local.
  // Should be just a function, but its also super likely that the user just forgot
  // to declare a variable, and we interpreted it as an outside lookup.
  case class OutsideLookupResult(range: RangeS, name: String, templateArgs: Option[List[ITemplexS]]) extends IScoutResult[IExpressionSE]
  // Anything else, such as:
  // - Result of a function call
  // - Address inside a struct
  case class NormalResult[+T <: IExpressionSE](range: RangeS, expr: T) extends IScoutResult[T]


  def scoutBlock(
    parentStackFrame: StackFrame,
    blockPE: BlockPE,
    // For example if a function wants to put some params in or something, the
    // declarations will go here.
    initialDeclarations: VariableDeclarations):
  (IScoutResult[BlockSE], VariableUses, VariableUses) = {
    val initialStackFrame =
      StackFrame(
        parentStackFrame.file,
        parentStackFrame.name,
        parentStackFrame.parentEnv,
        Some(parentStackFrame),
        initialDeclarations)

    val (stackFrameBeforeConstructing, exprsWithoutConstructingS, selfUsesBeforeConstructing, childUsesBeforeConstructing) =
      scoutElementsAsExpressions(initialStackFrame, blockPE.elements)

    // If we had for example:
    //   fn MyStruct() {
    //     this.a = 5;
    //     println("flamscrankle");
    //     this.b = true;
    //   }
    // then here's where we insert the final
    //     MyStruct(`this.a`, `this.b`);
    val constructedMembersNames =
      stackFrameBeforeConstructing.locals.vars.map(_.name).collect({ case ConstructingMemberNameS(n) => n })

    val (stackFrame, exprsWithConstructingIfNecessary, selfUses, childUses) =
      if (constructedMembersNames.isEmpty) {
        (stackFrameBeforeConstructing, exprsWithoutConstructingS, selfUsesBeforeConstructing, childUsesBeforeConstructing)
      } else {
        val rangeAtEnd = Range(blockPE.range.end, blockPE.range.end)
        val constructorCallP =
          FunctionCallPE(
            rangeAtEnd,
            None,
            Range.zero,
            false,
            LookupPE(
              stackFrameBeforeConstructing.parentEnv.name match {
                case FunctionNameS(n, _) => NameP(rangeAtEnd, n)
                case _ => vwat()
              }, None),
            constructedMembersNames.map(n => DotPE(rangeAtEnd, LookupPE(NameP(rangeAtEnd, "this"), None), Range.zero, false, NameP(rangeAtEnd, n))),
            LendConstraintP(None))

        val (stackFrameAfterConstructing, NormalResult(_, constructExpression), selfUsesAfterConstructing, childUsesAfterConstructing) =
          scoutExpression(stackFrameBeforeConstructing, constructorCallP)
        val exprsAfterConstructing = exprsWithoutConstructingS :+ constructExpression
        (stackFrameAfterConstructing, exprsAfterConstructing, selfUsesBeforeConstructing.thenMerge(selfUsesAfterConstructing), childUsesAfterConstructing.thenMerge(childUsesAfterConstructing))
      }

    val locals =
        stackFrame.locals.vars.map({ declared =>
        LocalVariable1(
          declared.name,
          declared.variability,
          selfUses.isBorrowed(declared.name),
          selfUses.isMoved(declared.name),
          selfUses.isMutated(declared.name),
          childUses.isBorrowed(declared.name),
          childUses.isMoved(declared.name),
          childUses.isMutated(declared.name))
      })

    val selfUsesOfThingsFromAbove =
      VariableUses(selfUses.uses.filter(selfUseName => !locals.map(_.varName).contains(selfUseName.name)))
    val childUsesOfThingsFromAbove =
      VariableUses(childUses.uses.filter(selfUseName => !locals.map(_.varName).contains(selfUseName.name)))

    // Notice how the fate is continuing on
    (NormalResult(Scout.evalRange(parentStackFrame.file, blockPE.range), BlockSE(Scout.evalRange(parentStackFrame.file, blockPE.range), locals, exprsWithConstructingIfNecessary)), selfUsesOfThingsFromAbove, childUsesOfThingsFromAbove)
  }

  // Returns:
  // - new seq num
  // - declared variables
  // - new expression
  // - variable uses by self
  // - variable uses by child blocks
  private def scoutExpression(stackFrame0: StackFrame,  expr: IExpressionPE):
      (StackFrame, IScoutResult[IExpressionSE], VariableUses, VariableUses) = {
    val evalRange = (range: Range) => Scout.evalRange(stackFrame0.file, range)

    expr match {
      case VoidPE(range) => (stackFrame0, NormalResult(evalRange(range), VoidSE(evalRange(range))), noVariableUses, noVariableUses)
      case lam @ LambdaPE(captures,_) => {
        val (function1, childUses) =
          FunctionScout.scoutLambda(stackFrame0, lam.function)

        // See maybify() for why we need this.
        val childMaybeUses = childUses.maybify()

        (stackFrame0, NormalResult(function1.range, FunctionSE(function1)), noVariableUses, childMaybeUses)
      }
      case StrInterpolatePE(range, partsPE) => {
        val (stackFrame1, partsSE, partsSelfUses, partsChildUses) =
          scoutElementsAsExpressions(stackFrame0, partsPE)

        val rangeS = evalRange(range)
        val startingExpr: IExpressionSE = StrLiteralSE(RangeS(rangeS.begin, rangeS.begin), "")
        val addedExpr =
          partsSE.foldLeft(startingExpr)({
            case (prevExpr, partSE) => {
              val addCallRange = RangeS(prevExpr.range.end, partSE.range.begin)
              FunctionCallSE(
                addCallRange,
                OutsideLoadSE(addCallRange, "+", None, LendConstraintP(None)),
                List(prevExpr, partSE))
            }
          })
        (stackFrame1, NormalResult(rangeS, addedExpr), partsSelfUses, partsChildUses)
      }
      case LendPE(range, innerPE, targetOwnership) => {
        val (stackFrame1, inner1, innerSelfUses, innerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, innerPE, targetOwnership)
        inner1 match {
          case OwnershippedSE(_, _, loadAs) => vassert(loadAs == targetOwnership)
          case LocalLoadSE(_, _, loadAs) => vassert(loadAs == targetOwnership)
          case OutsideLoadSE(_, _, _, loadAs) => vassert(loadAs == targetOwnership)
          case _ => vwat()
        }
        (stackFrame1, NormalResult(evalRange(range), inner1), innerSelfUses, innerChildUses)
      }
      case ReturnPE(range, innerPE) => {
        val (stackFrame1, inner1, innerSelfUses, innerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, innerPE, UseP)
        (stackFrame1, NormalResult(evalRange(range), ReturnSE(evalRange(range), inner1)), innerSelfUses, innerChildUses)
      }
      case IntLiteralPE(range, value) => (stackFrame0, NormalResult(evalRange(range), IntLiteralSE(evalRange(range), value)), noVariableUses, noVariableUses)
      case BoolLiteralPE(range,value) => (stackFrame0, NormalResult(evalRange(range), BoolLiteralSE(evalRange(range), value)), noVariableUses, noVariableUses)
      case StrLiteralPE(range, value) => (stackFrame0, NormalResult(evalRange(range), StrLiteralSE(evalRange(range), value)), noVariableUses, noVariableUses)
      case FloatLiteralPE(range,value) => (stackFrame0, NormalResult(evalRange(range), FloatLiteralSE(evalRange(range), value)), noVariableUses, noVariableUses)

      case MagicParamLookupPE(range) => {
        val name = MagicParamNameS(Scout.evalPos(stackFrame0.file, range.begin))
        val lookup = LocalLookupResult(evalRange(range), name)
        // We dont declare it here, because then scoutBlock will think its a local and
        // hide it from those above.
        //   val declarations = VariableDeclarations(List(VariableDeclaration(lookup.name, FinalP)))
        // Leave it to scoutLambda to declare it.
        (stackFrame0, lookup, noVariableUses.markMoved(name), noVariableUses)
      }
      case LookupPE(NameP(range, name), None) => {
        val (lookup, declarations) =
          stackFrame0.findVariable(name) match {
            case Some(fullName) => {
              (LocalLookupResult(evalRange(range), fullName), noDeclarations)
            }
            case None => {
              if (stackFrame0.parentEnv.allUserDeclaredRunes().contains(CodeRuneS(name))) {
                (NormalResult(evalRange(range), RuneLookupSE(evalRange(range), CodeRuneS(name))), noDeclarations)
              } else {
                (OutsideLookupResult(evalRange(range), name, None), noDeclarations)
              }
            }
          }
        (stackFrame0, lookup, noVariableUses, noVariableUses)
      }
      case LookupPE(NameP(range, templateName), Some(TemplateArgsP(_, templateArgs))) => {
        val result =
          OutsideLookupResult(
            evalRange(range),
            templateName,
            Some(templateArgs.map(TemplexScout.translateTemplex(stackFrame0.parentEnv, _))))
        (stackFrame0, result, noVariableUses, noVariableUses)
      }
      case DestructPE(range, innerPE) => {
        val (stackFrame1, inner1, innerSelfUses, innerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, innerPE, UseP)
        (stackFrame1, NormalResult(evalRange(range), DestructSE(evalRange(range), inner1)), innerSelfUses, innerChildUses)
      }
      case FunctionCallPE(range, inline, _, isMapCall, callablePE, args, borrowCallable) => {
        val (stackFrame1, callable1, callableSelfUses, callableChildUses) =
          scoutExpressionAndCoerce(stackFrame0, callablePE, borrowCallable)
        val (stackFrame2, args1, argsSelfUses, argsChildUses) =
          scoutElementsAsExpressions(stackFrame1, args)
        val result = NormalResult(evalRange(range), FunctionCallSE(evalRange(range), callable1, args1))
        (stackFrame2, result, callableSelfUses.thenMerge(argsSelfUses), callableChildUses.thenMerge(argsChildUses))
      }
      case MethodCallPE(range, subjectExpr, operatorRange, subjectTargetOwnership, isMapCall, memberLookup, methodArgs) => {
        val (stackFrame1, callable1, callableSelfUses, callableChildUses) =
          scoutExpressionAndCoerce(stackFrame0, memberLookup, LendConstraintP(None))
        val (stackFrame2, subject1, subjectSelfUses, subjectChildUses) =
          scoutExpressionAndCoerce(stackFrame1, subjectExpr, subjectTargetOwnership)
        val (stackFrame3, tailArgs1, tailArgsSelfUses, tailArgsChildUses) =
          scoutElementsAsExpressions(stackFrame2, methodArgs)

        val selfUses = callableSelfUses.thenMerge(subjectSelfUses).thenMerge(tailArgsSelfUses)
        val childUses = callableChildUses.thenMerge(subjectChildUses).thenMerge(tailArgsChildUses)
        val args = subject1 :: tailArgs1

        val result = NormalResult(evalRange(range), FunctionCallSE(evalRange(range), callable1, args))
        (stackFrame3, result, selfUses, childUses)
      }
      case TuplePE(range, elementsPE) => {
        val (stackFrame1, elements1, selfUses, childUses) =
          scoutElementsAsExpressions(stackFrame0, elementsPE)
        (stackFrame1, NormalResult(evalRange(range), TupleSE(evalRange(range), elements1)), selfUses, childUses)
      }
      case b @ BlockPE(_, _) => {
        val (result, selfUses, childUses) =
          scoutBlock(stackFrame0, b, noDeclarations)
        (stackFrame0, result, selfUses, childUses)
      }
      case AndPE(range, leftPE, rightPE) => {
        val rightRange = evalRange(rightPE.range)
        val endRange = RangeS(rightRange.end, rightRange.end)

        val (NormalResult(_, condSE), condUses, condChildUses) =
          scoutBlock(stackFrame0, leftPE, VariableDeclarations(List()))
        val (NormalResult(_, thenSE), thenUses, thenChildUses) =
          scoutBlock(stackFrame0, rightPE, VariableDeclarations(List()))
        val elseUses = VariableUses(List())
        val elseChildUses = VariableUses(List())
        val elseSE = BlockSE(endRange, List(), List(BoolLiteralSE(endRange, false)))

        val selfCaseUses = thenUses.branchMerge(elseUses)
        val selfUses = condUses.thenMerge(selfCaseUses);
        val childCaseUses = thenChildUses.branchMerge(elseChildUses)
        val childUses = condChildUses.thenMerge(childCaseUses);

        val ifSE = IfSE(evalRange(range), condSE, thenSE, elseSE)
        (stackFrame0, NormalResult(evalRange(range), ifSE), selfUses, childUses)
      }
      case OrPE(range, leftPE, rightPE) => {
        val rightRange = evalRange(rightPE.range)
        val endRange = RangeS(rightRange.end, rightRange.end)

        val (NormalResult(_, condSE), condUses, condChildUses) =
          scoutBlock(stackFrame0, leftPE, VariableDeclarations(List()))
        val thenUses = VariableUses(List())
        val thenChildUses = VariableUses(List())
        val thenSE = BlockSE(endRange, List(), List(BoolLiteralSE(endRange, true)))
        val (NormalResult(_, elseSE), elseUses, elseChildUses) =
          scoutBlock(stackFrame0, rightPE, VariableDeclarations(List()))

        val selfCaseUses = thenUses.branchMerge(elseUses)
        val selfUses = condUses.thenMerge(selfCaseUses);
        val childCaseUses = thenChildUses.branchMerge(elseChildUses)
        val childUses = condChildUses.thenMerge(childCaseUses);

        val ifSE = IfSE(evalRange(range), condSE, thenSE, elseSE)
        (stackFrame0, NormalResult(evalRange(range), ifSE), selfUses, childUses)
      }
      case IfPE(range, condition, thenBody, elseBody) => {
        val (NormalResult(_, cond1), condUses, condChildUses) =
          scoutBlock(stackFrame0, condition, noDeclarations)
        val (NormalResult(_, then1), thenUses, thenChildUses) =
          scoutBlock(stackFrame0, thenBody, noDeclarations)
        val (NormalResult(_, else1), elseUses, elseChildUses) =
          scoutBlock(stackFrame0, elseBody, noDeclarations)

        val selfCaseUses = thenUses.branchMerge(elseUses)
        val selfUses = condUses.thenMerge(selfCaseUses);
        val childCaseUses = thenChildUses.branchMerge(elseChildUses)
        val childUses = condChildUses.thenMerge(childCaseUses);

        val ifSE = IfSE(evalRange(range), cond1, then1, else1)
        (stackFrame0, NormalResult(evalRange(range), ifSE), selfUses, childUses)
      }
      case WhilePE(range, condition, body) => {
        val (NormalResult(_, cond1), condSelfUses, condChildUses) =
          scoutBlock(stackFrame0, condition, noDeclarations)

        // Ignoring exported names
        val (NormalResult(_, body1), bodySelfUses, bodyChildUses) =
          scoutBlock(stackFrame0, body, noDeclarations)

        val bodySelfMaybeUses = bodySelfUses.maybify()
        val bodyChildMaybeUses = bodyChildUses.maybify()

        // Condition's uses isn't sent through a branch merge because the condition
        // is *always* evaluated (at least once).
        val selfUses = condSelfUses.thenMerge(bodySelfMaybeUses)
        val childUses = condChildUses.thenMerge(bodyChildMaybeUses)

        (stackFrame0, NormalResult(evalRange(range), WhileSE(evalRange(range), cond1, body1)), selfUses, childUses)
      }
      case BadLetPE(range) => {
        throw CompileErrorExceptionS(ForgotSetKeywordError(evalRange(range)))
      }
      case let @ LetPE(range, rulesP, patternP, exprPE) => {
        val codeLocation = Scout.evalPos(stackFrame0.file, range.begin)
        val (stackFrame1, expr1, selfUses, childUses) =
          scoutExpressionAndCoerce(stackFrame0, exprPE, UseP);

        val letFullName = LetNameS(codeLocation)

        val ruleState = RuleStateBox(LetRuleState(letFullName, codeLocation, 0))
        val userRulesS =
          RuleScout.translateRulexes(
            stackFrame0.parentEnv, ruleState, stackFrame1.parentEnv.allUserDeclaredRunes(), rulesP.toList.flatMap(_.rules))
        val (implicitRulesS, patternS) =
          PatternScout.translatePattern(
            stackFrame1,
            ruleState,
            patternP)
        val rulesS = userRulesS ++ implicitRulesS

        val allRunes = PredictorEvaluator.getAllRunes(List(), rulesS, List(patternS), None)

        // See MKKRFA
        val knowableRunesFromAbove = stackFrame1.parentEnv.allUserDeclaredRunes()
        val allUnknownRunes = allRunes -- knowableRunesFromAbove
        val Conclusions(knowableValueRunes, predictedTypeByRune) =
          PredictorEvaluator.solve(Set(), rulesS, List())
        val localRunes = allRunes -- knowableRunesFromAbove

        val declarationsFromPattern = VariableDeclarations(PatternScout.getParameterCaptures(patternS))

        val nameConflictVarNames =
          stackFrame1.locals.vars.map(_.name).intersect(declarationsFromPattern.vars.map(_.name))
        nameConflictVarNames.headOption match {
          case None =>
          case Some(nameConflictVarName) => {
            throw CompileErrorExceptionS(VariableNameAlreadyExists(evalRange(range), nameConflictVarName))
          }
        }
        (stackFrame1 ++ declarationsFromPattern, NormalResult(evalRange(range), LetSE(evalRange(range), rulesS, allUnknownRunes, localRunes, patternS, expr1)), selfUses, childUses)
      }
      case MutatePE(mutateRange, destinationExprPE, sourceExprPE) => {
        val (stackFrame1, sourceExpr1, sourceInnerSelfUses, sourceChildUses) =
          scoutExpressionAndCoerce(stackFrame0, sourceExprPE, UseP);
        val (stackFrame2, destinationResult1, destinationSelfUses, destinationChildUses) =
          scoutExpression(stackFrame1, destinationExprPE);
        val (mutateExpr1, sourceSelfUses) =
          destinationResult1 match {
            case LocalLookupResult(range, name) => {
              (LocalMutateSE(range, name, sourceExpr1), sourceInnerSelfUses.markMutated(name))
            }
            case OutsideLookupResult(range, name, maybeTemplateArgs) => {
              throw CompileErrorExceptionS(CouldntFindVarToMutateS(range, name))
            }
            case NormalResult(range, destinationExpr1) => {
              (ExprMutateSE(range, destinationExpr1, sourceExpr1), sourceInnerSelfUses)
            }
          }
        (stackFrame2, NormalResult(evalRange(mutateRange), mutateExpr1), sourceSelfUses.thenMerge(destinationSelfUses), sourceChildUses.thenMerge(destinationChildUses))
      }
      case DotPE(rangeP, containerExprPE, _, isMapCall, NameP(_, memberName)) => {
        containerExprPE match {
          // Here, we're special casing lookups of this.x when we're in a constructor.
          // We know we're in a constructor if there's no `this` variable yet. After all,
          // in a constructor, `this` is just an imaginary concept until we actually
          // fill all the variables.
          case LookupPE(NameP(range, "this"), _) if (stackFrame0.findVariable("this").isEmpty) => {
            val result = LocalLookupResult(evalRange(range), ConstructingMemberNameS(memberName))
            (stackFrame0, result, noVariableUses, noVariableUses)
          }
          case _ => {
            val (stackFrame1, containerExpr, selfUses, childUses) =
              scoutExpressionAndCoerce(stackFrame0, containerExprPE, LendConstraintP(None));
            (stackFrame1, NormalResult(evalRange(rangeP), DotSE(evalRange(rangeP), containerExpr, memberName, true)), selfUses, childUses)
          }
        }
      }
      case IndexPE(range, containerExprPE, List(indexExprPE)) => {
        val (stackFrame1, containerExpr1, containerSelfUses, containerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, containerExprPE, LendConstraintP(None));
        val (stackFrame2, indexExpr1, indexSelfUses, indexChildUses) =
          scoutExpressionAndCoerce(stackFrame1, indexExprPE, UseP);
        val dot1 = DotCallSE(evalRange(range), containerExpr1, indexExpr1)
        (stackFrame2, NormalResult(evalRange(range), dot1), containerSelfUses.thenMerge(indexSelfUses), containerChildUses.thenMerge(indexChildUses))
      }
    }
  }

  // If we load an immutable with targetOwnershipIfLookupResult = Own or Borrow, it will just be Share.
  def scoutExpressionAndCoerce(
      stackFramePE: StackFrame,  exprPE: IExpressionPE, targetOwnershipIfLookupResult: LoadAsP):
  (StackFrame, IExpressionSE, VariableUses, VariableUses) = {
    val (namesFromInsideFirst, firstResult1, firstInnerSelfUses, firstChildUses) =
      scoutExpression(stackFramePE, exprPE);
    val (firstExpr1, firstSelfUses) =
      firstResult1 match {
        case LocalLookupResult(range, name) => {
          val uses =
            targetOwnershipIfLookupResult match {
              case LendConstraintP(_) => firstInnerSelfUses.markBorrowed(name)
              case LendWeakP(_) => firstInnerSelfUses.markBorrowed(name)
              case _ => firstInnerSelfUses.markMoved(name)
            }
          (LocalLoadSE(range, name, targetOwnershipIfLookupResult), uses)
        }
        case OutsideLookupResult(range, name, maybeTemplateArgs) => {
          (OutsideLoadSE(range, name, maybeTemplateArgs, targetOwnershipIfLookupResult), firstInnerSelfUses)
        }
        case NormalResult(range, innerExpr1) => {
          targetOwnershipIfLookupResult match {
            case UseP => (innerExpr1, firstInnerSelfUses)
            case _ => (OwnershippedSE(range, innerExpr1, targetOwnershipIfLookupResult), firstInnerSelfUses)
          }
        }
      }
    (namesFromInsideFirst, firstExpr1, firstSelfUses, firstChildUses)
  }

  // Need a better name for this...
  // It's more like, scout elements as non-lookups, in other words,
  // if we get lookups then coerce them into moves.
  def scoutElementsAsExpressions(stackFramePE: StackFrame,  exprs: List[IExpressionPE]):
  (StackFrame, List[IExpressionSE], VariableUses, VariableUses) = {
    exprs match {
      case Nil => (stackFramePE, Nil, noVariableUses, noVariableUses)
      case firstPE :: restPE => {
        val (stackFrame1, firstExpr1, firstSelfUses, firstChildUses) =
          scoutExpressionAndCoerce(stackFramePE, firstPE, UseP)
        val (finalStackFrame, rest1, restSelfUses, restChildUses) =
          scoutElementsAsExpressions(stackFrame1, restPE);
        (finalStackFrame, firstExpr1 :: rest1, firstSelfUses.thenMerge(restSelfUses), firstChildUses.thenMerge(restChildUses))
      }
    }
  }

//  private def flattenScramble(elements: List[Expression1]): List[Expression1] = {
//    elements.map((expr: Expression1) => {
//      expr match {
//        case Scramble1(elementElements) => flattenScramble(elementElements)
//        case _ => List(expr)
//      }
//    }).foldLeft(List[Expression1]())(_ ++ _)
//  }
}
