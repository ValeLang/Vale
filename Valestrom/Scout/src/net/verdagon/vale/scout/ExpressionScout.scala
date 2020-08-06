package net.verdagon.vale.scout

import net.verdagon.vale.parser._
import net.verdagon.vale.{scout, vfail, vimpl, vwat}
import net.verdagon.vale.scout.Scout.{noDeclarations, noVariableUses}
import net.verdagon.vale.scout.patterns.{LetRuleState, PatternScout, RuleState, RuleStateBox}
import net.verdagon.vale.scout.predictor.Conclusions
import net.verdagon.vale.scout.rules.RuleScout
import net.verdagon.vale.scout.templatepredictor.PredictorEvaluator

object ExpressionScout {
  sealed trait IScoutResult[+T <: IExpressionSE]
  // Will contain the address of a local.
  case class LocalLookupResult(name: IVarNameS) extends IScoutResult[IExpressionSE]
  // Looks up a function.
  case class FunctionLookupResult(name: GlobalFunctionFamilyNameS) extends IScoutResult[IExpressionSE]
  // Anything else, such as:
  // - Result of a function call
  // - Address inside a struct
  case class NormalResult[+T <: IExpressionSE](expr: T) extends IScoutResult[T]


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
                case FunctionNameS(n, _) => StringP(rangeAtEnd, n)
                case _ => vwat()
              }, None),
            constructedMembersNames.map(n => DotPE(rangeAtEnd, LookupPE(StringP(rangeAtEnd, "this"), None), Range.zero, false, LookupPE(StringP(rangeAtEnd, n), None))),
            BorrowP)

        val (stackFrameAfterConstructing, NormalResult(constructExpression), selfUsesAfterConstructing, childUsesAfterConstructing) =
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
    (NormalResult(BlockSE(locals, exprsWithConstructingIfNecessary)), selfUsesOfThingsFromAbove, childUsesOfThingsFromAbove)
  }

  // Returns:
  // - new seq num
  // - declared variables
  // - new expression
  // - variable uses by self
  // - variable uses by child blocks
  private def scoutExpression(stackFrame0: StackFrame,  expr: IExpressionPE):
      (StackFrame, IScoutResult[IExpressionSE], VariableUses, VariableUses) = {
    expr match {
      case VoidPE(_) => (stackFrame0, NormalResult(VoidSE()), noVariableUses, noVariableUses)
      case lam @ LambdaPE(captures,_) => {
        val (function1, childUses) =
          FunctionScout.scoutLambda(stackFrame0, lam.function)

        // See maybify() for why we need this.
        val childMaybeUses = childUses.maybify()

        (stackFrame0, NormalResult(FunctionSE(function1)), noVariableUses, childMaybeUses)
      }
      case LendPE(_, innerPE, targetOwnership) => {
        val (stackFrame1, inner1, innerSelfUses, innerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, innerPE, targetOwnership)
        (stackFrame1, NormalResult(LendSE(inner1, targetOwnership)), innerSelfUses, innerChildUses)
      }
      case ReturnPE(_, innerPE) => {
        val (stackFrame1, inner1, innerSelfUses, innerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, innerPE, OwnP)
        (stackFrame1, NormalResult(ReturnSE(inner1)), innerSelfUses, innerChildUses)
      }
      case PackPE(elements) => {
        val (stackFrame1, elements1, selfUses, childUses) =
          scoutElementsAsExpressions(stackFrame0, elements)
        (stackFrame1, NormalResult(PackSE(elements1)), selfUses, childUses)
      }
      case IntLiteralPE(_, value) => (stackFrame0, NormalResult(IntLiteralSE(value)), noVariableUses, noVariableUses)
      case BoolLiteralPE(_,value) => (stackFrame0, NormalResult(BoolLiteralSE(value)), noVariableUses, noVariableUses)
      case StrLiteralPE(StringP(_, value)) => (stackFrame0, NormalResult(StrLiteralSE(value)), noVariableUses, noVariableUses)
      case FloatLiteralPE(_,value) => (stackFrame0, NormalResult(FloatLiteralSE(value)), noVariableUses, noVariableUses)

      case MethodCallPE(range, container, operatorRange, targetOwnership, isMapCall, memberLookup, methodArgs) => {
        val maybeLendContainer =
          if (targetOwnership == BorrowP)
            LendPE(Range(range.begin, range.begin), container, BorrowP)
          else
            container
        // Correct method calls like anExpr.bork(4) from FunctionCall(Dot(anExpr, bork), List(4))
        // to FunctionCall(bork, List(anExpr, 4))
        val newExprP = FunctionCallPE(range, None, operatorRange, isMapCall, memberLookup, maybeLendContainer :: methodArgs, OwnP)
        // Try again, with this new transformed expression.
        scoutExpression(stackFrame0, newExprP)
      }
      case MagicParamLookupPE(range) => {
        val name = MagicParamNameS(Scout.evalPos(stackFrame0.file, range.begin))
        val lookup = LocalLookupResult(name)
        // We dont declare it here, because then scoutBlock will think its a local and
        // hide it from those above.
        //   val declarations = VariableDeclarations(List(VariableDeclaration(lookup.name, FinalP)))
        // Leave it to scoutLambda to declare it.
        (stackFrame0, lookup, noVariableUses.markMoved(name), noVariableUses)
      }
      case LookupPE(StringP(_, name), None) => {
        val (lookup, declarations) =
          stackFrame0.findVariable(name) match {
            case Some(fullName) => {
              (LocalLookupResult(fullName), noDeclarations)
            }
            case None => {
              if (stackFrame0.parentEnv.allUserDeclaredRunes().contains(CodeRuneS(name))) {
                (NormalResult(RuneLookupSE(CodeRuneS(name))), noDeclarations)
              } else {
                (FunctionLookupResult(GlobalFunctionFamilyNameS(name)), noDeclarations)
              }
            }
          }
        (stackFrame0, lookup, noVariableUses, noVariableUses)
      }
      case DestructPE(_, innerPE) => {
        val (stackFrame1, inner1, innerSelfUses, innerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, innerPE, OwnP)
        (stackFrame1, NormalResult(DestructSE(inner1)), innerSelfUses, innerChildUses)
      }
      case LookupPE(StringP(_, templateName), Some(TemplateArgsP(_, templateArgs))) => {
        val result =
          NormalResult(
            TemplateSpecifiedLookupSE(
              templateName,
              templateArgs.map(TemplexScout.translateTemplex(stackFrame0.parentEnv, _))))
        (stackFrame0, result, noVariableUses, noVariableUses)
      }
      case FunctionCallPE(_, inline, _, isMapCall, callablePE, args, borrowCallable) => {
        val (stackFrame1, callable1, callableSelfUses, callableChildUses) =
          scoutExpressionAndCoerce(stackFrame0, callablePE, borrowCallable)
        val (stackFrame2, args1, argsSelfUses, argsChildUses) =
          scoutElementsAsExpressions(stackFrame1, args)
        val result = NormalResult(FunctionCallSE(callable1, args1))
        (stackFrame2, result, callableSelfUses.thenMerge(argsSelfUses), callableChildUses.thenMerge(argsChildUses))
      }
      case SequencePE(_, elementsPE) => {
        val (stackFrame1, elements1, selfUses, childUses) =
          scoutElementsAsExpressions(stackFrame0, elementsPE)
        (stackFrame1, NormalResult(scout.SequenceESE(elements1)), selfUses, childUses)
      }
      case b @ BlockPE(_, _) => {
        val (result, selfUses, childUses) =
          scoutBlock(stackFrame0, b, noDeclarations)
        (stackFrame0, result, selfUses, childUses)
      }
      case IfPE(_, condition, thenBody, elseBody) => {
        val (NormalResult(cond1), condUses, condChildUses) =
          scoutBlock(stackFrame0, condition, noDeclarations)
        val (NormalResult(then1), thenUses, thenChildUses) =
          scoutBlock(stackFrame0, thenBody, noDeclarations)
        val (NormalResult(else1), elseUses, elseChildUses) =
          scoutBlock(stackFrame0, elseBody, noDeclarations)

        val selfCaseUses = thenUses.branchMerge(elseUses)
        val selfUses = condUses.thenMerge(selfCaseUses);
        val childCaseUses = thenChildUses.branchMerge(elseChildUses)
        val childUses = condChildUses.thenMerge(childCaseUses);

        (stackFrame0, NormalResult(IfSE(cond1, then1, else1)), selfUses, childUses)
      }
      case WhilePE(_, condition, body) => {
        val (NormalResult(cond1), condSelfUses, condChildUses) =
          scoutBlock(stackFrame0, condition, noDeclarations)

        // Ignoring exported names
        val (NormalResult(body1), bodySelfUses, bodyChildUses) =
          scoutBlock(stackFrame0, body, noDeclarations)

        val bodySelfMaybeUses = bodySelfUses.maybify()
        val bodyChildMaybeUses = bodyChildUses.maybify()

        // Condition's uses isn't sent through a branch merge because the condition
        // is *always* evaluated (at least once).
        val selfUses = condSelfUses.thenMerge(bodySelfMaybeUses)
        val childUses = condChildUses.thenMerge(bodyChildMaybeUses)

        (stackFrame0, NormalResult(WhileSE(cond1, body1)), selfUses, childUses)
      }
      case let @ LetPE(range, rulesP, patternP, exprPE) => {
        val codeLocation = Scout.evalPos(stackFrame0.file, range.begin)
        val (stackFrame1, expr1, selfUses, childUses) =
          scoutExpressionAndCoerce(stackFrame0, exprPE, OwnP);

        val letFullName = LetNameS(codeLocation)

        val ruleState = RuleStateBox(LetRuleState(letFullName, codeLocation, 0))
        val userRulesS =
          RuleScout.translateRulexes(
            stackFrame0.parentEnv, ruleState, stackFrame1.parentEnv.allUserDeclaredRunes(), rulesP)
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
        (stackFrame1 ++ declarationsFromPattern, NormalResult(LetSE(rulesS, allUnknownRunes, localRunes, patternS, expr1)), selfUses, childUses)
      }
      case MutatePE(_, destinationExprPE, sourceExprPE) => {
        val (stackFrame1, sourceExpr1, sourceInnerSelfUses, sourceChildUses) =
          scoutExpressionAndCoerce(stackFrame0, sourceExprPE, OwnP);
        val (stackFrame2, destinationResult1, destinationSelfUses, destinationChildUses) =
          scoutExpression(stackFrame1, destinationExprPE);
        val (mutateExpr1, sourceSelfUses) =
          destinationResult1 match {
            case LocalLookupResult(name) => {
              (LocalMutateSE(name, sourceExpr1), sourceInnerSelfUses.markMutated(name))
            }
            case FunctionLookupResult(_) => {
              vfail("Cant mutate a function")
//              (GlobalMutateSE(name, sourceExpr1), sourceInnerSelfUses.markMutated(name))
            }
            case NormalResult(destinationExpr1) => {
              (ExprMutateSE(destinationExpr1, sourceExpr1), sourceInnerSelfUses)
            }
          }
        (stackFrame2, NormalResult(mutateExpr1), sourceSelfUses.thenMerge(destinationSelfUses), sourceChildUses.thenMerge(destinationChildUses))
      }
      case DotPE(_, containerExprPE, _, isMapCall, LookupPE(StringP(_, memberName), templateArgs)) => {
        if (templateArgs.nonEmpty) {
          // such as myStruct.someField<Foo>.
          // Can't think of a good use for it yet.
          vimpl("havent implemented looking up templated members yet")
        }
        containerExprPE match {
          // Here, we're special casing lookups of this.x when we're in a constructor.
          // We know we're in a constructor if there's no `this` variable yet. After all,
          // in a constructor, `this` is just an imaginary concept until we actually
          // fill all the variables.
          case LookupPE(StringP(_, "this"), _) if (stackFrame0.findVariable("this").isEmpty) => {
            val result = LocalLookupResult(ConstructingMemberNameS(memberName))
            (stackFrame0, result, noVariableUses, noVariableUses)
          }
          case _ => {
            val (stackFrame1, containerExpr, selfUses, childUses) =
              scoutExpressionAndCoerce(stackFrame0, containerExprPE, BorrowP);
            (stackFrame1, NormalResult(DotSE(containerExpr, memberName, true)), selfUses, childUses)
          }
        }
      }
      case IndexPE(_, containerExprPE, List(indexExprPE)) => {
        val (stackFrame1, containerExpr1, containerSelfUses, containerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, containerExprPE, BorrowP);
        val (stackFrame2, indexExpr1, indexSelfUses, indexChildUses) =
          scoutExpressionAndCoerce(stackFrame1, indexExprPE, OwnP);
        val dot1 = DotCallSE(containerExpr1, indexExpr1)
        (stackFrame2, NormalResult(dot1), containerSelfUses.thenMerge(indexSelfUses), containerChildUses.thenMerge(indexChildUses))
      }
    }
  }

  // If we load an immutable with targetOwnershipIfLookupResult = Own or Borrow, it will just be Share.
  def scoutExpressionAndCoerce(
      stackFramePE: StackFrame,  exprPE: IExpressionPE, targetOwnershipIfLookupResult: OwnershipP):
  (StackFrame, IExpressionSE, VariableUses, VariableUses) = {
    val (namesFromInsideFirst, firstResult1, firstInnerSelfUses, firstChildUses) =
      scoutExpression(stackFramePE, exprPE);
    val (firstExpr1, firstSelfUses) =
      firstResult1 match {
        case LocalLookupResult(name) => {
          val uses =
            targetOwnershipIfLookupResult match {
              case BorrowP => firstInnerSelfUses.markBorrowed(name)
              case WeakP => firstInnerSelfUses.markBorrowed(name)
              case _ => firstInnerSelfUses.markMoved(name)
            }
          (LocalLoadSE(name, targetOwnershipIfLookupResult), uses)
        }
        case FunctionLookupResult(name) => {
          (FunctionLoadSE(name), firstInnerSelfUses)
        }
        case NormalResult(innerExpr1) => {
          (innerExpr1, firstInnerSelfUses)
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
          scoutExpressionAndCoerce(stackFramePE, firstPE, OwnP)
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
