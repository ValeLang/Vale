package net.verdagon.vale.scout

import net.verdagon.vale.parser.{RuntimeSizedP, _}
import net.verdagon.vale.{RangeS, scout, vassert, vcurious, vfail, vimpl, vwat}
import net.verdagon.vale.scout.Scout.{noDeclarations, noVariableUses}
import net.verdagon.vale.scout.patterns.PatternScout
import net.verdagon.vale.scout.rules.RuneUsage
//import net.verdagon.vale.scout.predictor.{Conclusions, PredictorEvaluator}
import net.verdagon.vale.scout.rules.{ILiteralSL, IntLiteralSL, LiteralSR, MutabilityLiteralSL, TemplexScout, VariabilityLiteralSL}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
//import net.verdagon.vale.scout.predictor.Conclusions
import net.verdagon.vale.scout.rules.{IRulexSR, RuleScout}
//import net.verdagon.vale.scout.templatepredictor.PredictorEvaluator

trait IExpressionScoutDelegate {
  def scoutLambda(
    parentStackFrame: StackFrame,
    lambdaFunction0: FunctionP):
  (FunctionS, VariableUses)
}

sealed trait IScoutResult[+T <: IExpressionSE]
// Will contain the address of a local.
case class LocalLookupResult(range: RangeS, name: IVarNameS) extends IScoutResult[IExpressionSE] {
  override def hashCode(): Int = vcurious()
}
// Looks up something that's not a local.
// Should be just a function, but its also super likely that the user just forgot
// to declare a variable, and we interpreted it as an outside lookup.
case class OutsideLookupResult(
  range: RangeS,
  name: String,
  templateArgs: Option[Array[ITemplexPT]]
) extends IScoutResult[IExpressionSE] {
  override def hashCode(): Int = vcurious()
}
// Anything else, such as:
// - Result of a function call
// - Address inside a struct
case class NormalResult[+T <: IExpressionSE](range: RangeS, expr: T) extends IScoutResult[T] {
  override def hashCode(): Int = vcurious()
}

class ExpressionScout(delegate: IExpressionScoutDelegate) {
  def scoutBlock(
    functionBodyEnv: FunctionEnvironment,
    parentStackFrame: Option[StackFrame],
    lidb: LocationInDenizenBuilder,
    blockPE: BlockPE,
    // When we scout a function, it might hand in things here because it wants them to be considered part of
    // the body's block, so that we get to reuse the code at the bottom of function, tracking uses etc.
    initialLocals: VariableDeclarations):
  (IScoutResult[BlockSE], VariableUses, VariableUses) = {
    val initialStackFrame =
      StackFrame(functionBodyEnv.file, functionBodyEnv.name, functionBodyEnv, parentStackFrame, initialLocals)

    val (stackFrameBeforeConstructing, exprsWithoutConstructingS, selfUsesBeforeConstructing, childUsesBeforeConstructing) =
      scoutElementsAsExpressions(initialStackFrame, lidb.child(), blockPE.elements.toList)

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
          scoutExpression(stackFrameBeforeConstructing, lidb.child(), constructorCallP)
        val exprsAfterConstructing = exprsWithoutConstructingS :+ constructExpression
        (stackFrameAfterConstructing, exprsAfterConstructing, selfUsesBeforeConstructing.thenMerge(selfUsesAfterConstructing), childUsesBeforeConstructing.thenMerge(childUsesAfterConstructing))
      }

    val locals =
        stackFrame.locals.vars.map({ declared =>
        LocalS(
          declared.name,
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
    (NormalResult(Scout.evalRange(functionBodyEnv.file, blockPE.range), BlockSE(Scout.evalRange(functionBodyEnv.file, blockPE.range), locals, exprsWithConstructingIfNecessary.toVector)), selfUsesOfThingsFromAbove, childUsesOfThingsFromAbove)
  }

  // Returns:
  // - new seq num
  // - declared variables
  // - new expression
  // - variable uses by self
  // - variable uses by child blocks
  private def scoutExpression(
    stackFrame0: StackFrame,
    lidb: LocationInDenizenBuilder,
    expr: IExpressionPE):
  (StackFrame, IScoutResult[IExpressionSE], VariableUses, VariableUses) = {
    val evalRange = (range: Range) => Scout.evalRange(stackFrame0.file, range)

    expr match {
      case VoidPE(range) => (stackFrame0, NormalResult(evalRange(range), VoidSE(evalRange(range))), noVariableUses, noVariableUses)
      case lam @ LambdaPE(captures,_) => {
        val (function1, childUses) =
          delegate.scoutLambda(stackFrame0, lam.function)

        (stackFrame0, NormalResult(function1.range, FunctionSE(function1)), noVariableUses, childUses)
      }
      case StrInterpolatePE(range, partsPE) => {
        val (stackFrame1, partsSE, partsSelfUses, partsChildUses) =
          scoutElementsAsExpressions(stackFrame0, lidb.child(), partsPE.toList)

        val rangeS = evalRange(range)
        val startingExpr: IExpressionSE = ConstantStrSE(RangeS(rangeS.begin, rangeS.begin), "")
        val addedExpr =
          partsSE.foldLeft(startingExpr)({
            case (prevExpr, partSE) => {
              val addCallRange = RangeS(prevExpr.range.end, partSE.range.begin)
              FunctionCallSE(
                addCallRange,
                OutsideLoadSE(addCallRange, Array(), CodeNameS("+"), None, LendConstraintP(None)),
                Vector(prevExpr, partSE))
            }
          })
        (stackFrame1, NormalResult(rangeS, addedExpr), partsSelfUses, partsChildUses)
      }
      case LendPE(range, innerPE, targetOwnership) => {
        val (stackFrame1, inner1, innerSelfUses, innerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), innerPE, targetOwnership)
        inner1 match {
          case OwnershippedSE(_, _, loadAs) => vassert(loadAs == targetOwnership)
          case LocalLoadSE(_, _, loadAs) => vassert(loadAs == targetOwnership)
          case OutsideLoadSE(_, _, _, _, loadAs) => vassert(loadAs == targetOwnership)
          case _ => vwat()
        }
        (stackFrame1, NormalResult(evalRange(range), inner1), innerSelfUses, innerChildUses)
      }
      case ReturnPE(range, innerPE) => {
        val (stackFrame1, inner1, innerSelfUses, innerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), innerPE, UseP)
        (stackFrame1, NormalResult(evalRange(range), ReturnSE(evalRange(range), inner1)), innerSelfUses, innerChildUses)
      }
      case ConstantIntPE(range, value, bits) => (stackFrame0, NormalResult(evalRange(range), ConstantIntSE(evalRange(range), value, bits)), noVariableUses, noVariableUses)
      case ConstantBoolPE(range,value) => (stackFrame0, NormalResult(evalRange(range), ConstantBoolSE(evalRange(range), value)), noVariableUses, noVariableUses)
      case ConstantStrPE(range, value) => (stackFrame0, NormalResult(evalRange(range), ConstantStrSE(evalRange(range), value)), noVariableUses, noVariableUses)
      case ConstantFloatPE(range,value) => (stackFrame0, NormalResult(evalRange(range), ConstantFloatSE(evalRange(range), value)), noVariableUses, noVariableUses)

      case MagicParamLookupPE(range) => {
        val name = MagicParamNameS(Scout.evalPos(stackFrame0.file, range.begin))
        val lookup = LocalLookupResult(evalRange(range), name)
        // We dont declare it here, because then scoutBlock will think its a local and
        // hide it from those above.
        //   val declarations = VariableDeclarations(Vector(VariableDeclaration(lookup.name, FinalP)))
        // Leave it to scoutLambda to declare it.
        (stackFrame0, lookup, noVariableUses.markMoved(name), noVariableUses)
      }
      case LookupPE(NameP(range, name), None) => {
        val lookup =
          stackFrame0.findVariable(name) match {
            case Some(fullName) => {
              (LocalLookupResult(evalRange(range), fullName))
            }
            case None => {
              if (stackFrame0.parentEnv.allDeclaredRunes().contains(CodeRuneS(name))) {
                (NormalResult(evalRange(range), RuneLookupSE(evalRange(range), CodeRuneS(name))))
              } else {
                (OutsideLookupResult(evalRange(range), name, None))
              }
            }
          }
        (stackFrame0, lookup, noVariableUses, noVariableUses)
      }
      case LookupPE(NameP(range, templateName), Some(TemplateArgsP(_, templateArgs))) => {

//        val ruleState = RuleStateBox(RuleState(vimpl(), 0))
//        val ruleBuilder = mutable.ArrayBuffer[IRulexSR]()
//        val userDeclaredRunes = stackFrame0.parentEnv.allUserDeclaredRunes()
//        val argRunes =
//          templateArgs.map(
//            TemplexScout.translateTemplex(
//              stackFrame0.parentEnv, lidb.child(), ruleBuilder, _))

        val result =
          OutsideLookupResult(
            evalRange(range),
            templateName,
            Some(templateArgs.toArray))
        (stackFrame0, result, noVariableUses, noVariableUses)
      }
      case DestructPE(range, innerPE) => {
        val (stackFrame1, inner1, innerSelfUses, innerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), innerPE, UseP)
        (stackFrame1, NormalResult(evalRange(range), DestructSE(evalRange(range), inner1)), innerSelfUses, innerChildUses)
      }
      case PackPE(range, innersPE) => {
        vassert(innersPE.size == 1)
        val (stackFrame1, inner1, innerSelfUses, innerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), innersPE.head, UseP)
        (stackFrame1, NormalResult(evalRange(range), inner1), innerSelfUses, innerChildUses)
      }
      case FunctionCallPE(range, inline, _, isMapCall, callablePE, args, borrowCallable) => {
        val (stackFrame1, callable1, callableSelfUses, callableChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), callablePE, borrowCallable)
        val (stackFrame2, args1, argsSelfUses, argsChildUses) =
          scoutElementsAsExpressions(stackFrame1, lidb.child(), args.toList)
        val result = NormalResult(evalRange(range), FunctionCallSE(evalRange(range), callable1, args1.toVector))
        (stackFrame2, result, callableSelfUses.thenMerge(argsSelfUses), callableChildUses.thenMerge(argsChildUses))
      }
      case MethodCallPE(range, inline, subjectExpr, operatorRange, subjectTargetOwnership, isMapCall, memberLookup, methodArgs) => {
        val (stackFrame1, callable1, callableSelfUses, callableChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), memberLookup, LendConstraintP(None))
        val (stackFrame2, subject1, subjectSelfUses, subjectChildUses) =
          scoutExpressionAndCoerce(stackFrame1, lidb.child(), subjectExpr, subjectTargetOwnership)
        val (stackFrame3, tailArgs1, tailArgsSelfUses, tailArgsChildUses) =
          scoutElementsAsExpressions(stackFrame2, lidb.child(), methodArgs.toList)

        val selfUses = callableSelfUses.thenMerge(subjectSelfUses).thenMerge(tailArgsSelfUses)
        val childUses = callableChildUses.thenMerge(subjectChildUses).thenMerge(tailArgsChildUses)
        val args = Vector(subject1) ++ tailArgs1

        val result = NormalResult(evalRange(range), FunctionCallSE(evalRange(range), callable1, args))
        (stackFrame3, result, selfUses, childUses)
      }
      case TuplePE(range, elementsPE) => {
        val (stackFrame1, elements1, selfUses, childUses) =
          scoutElementsAsExpressions(stackFrame0, lidb.child(), elementsPE.toList)
        (stackFrame1, NormalResult(evalRange(range), TupleSE(evalRange(range), elements1.toVector)), selfUses, childUses)
      }
      case ConstructArrayPE(rangeP, maybeMutabilityPT, maybeVariabilityPT, size, initializingIndividualElements, argsPE) => {
        val rangeS = evalRange(rangeP)
        val ruleBuilder = mutable.ArrayBuffer[IRulexSR]()
        val mutabilityRuneS =
          maybeMutabilityPT match {
            case None => {
              val rune = RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
              ruleBuilder += LiteralSR(rangeS, rune, MutabilityLiteralSL(MutableP))
              rune
            }
            case Some(mutabilityPT) => {
              TemplexScout.translateTemplex(
                stackFrame0.parentEnv, lidb.child(), ruleBuilder, mutabilityPT)
            }
          }
        val variabilityRuneS =
          maybeVariabilityPT match {
            case None => {
              val rune = RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
              ruleBuilder += LiteralSR(rangeS, rune, VariabilityLiteralSL(FinalP))
              rune
            }
            case Some(variabilityPT) => {
              TemplexScout.translateTemplex(
                stackFrame0.parentEnv, lidb.child(), ruleBuilder, variabilityPT)
            }
          }

        val (stackFrame1, argsSE, selfUses, childUses) =
          scoutElementsAsExpressions(stackFrame0, lidb.child(), argsPE.toList)

        val result =
          size match {
            case RuntimeSizedP => {
              if (initializingIndividualElements) {
                throw CompileErrorExceptionS(CantInitializeIndividualElementsOfRuntimeSizedArray(rangeS))
              }
              if (argsSE.size != 2) {
                throw CompileErrorExceptionS(InitializingRuntimeSizedArrayRequiresSizeAndCallable(rangeS))
              }
              val List(sizeSE, callableSE) = argsSE

              RuntimeArrayFromCallableSE(rangeS, ruleBuilder.toArray, mutabilityRuneS, variabilityRuneS, sizeSE, callableSE)
            }
            case StaticSizedP(maybeSizePT) => {
              val maybeSizeRuneS =
                maybeSizePT match {
                  case None => None
                  case Some(sizePT) => {
                    Some(
                      TemplexScout.translateTemplex(
                        stackFrame0.parentEnv,
                        lidb.child(),
                        ruleBuilder,
                        sizePT))
                  }
                }

              if (initializingIndividualElements) {
                val sizeRuneS =
                  maybeSizeRuneS match {
                    case Some(s) => s
                    case None => {
                      val runeS = RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
                      ruleBuilder += LiteralSR(rangeS, runeS, IntLiteralSL(argsSE.size))
                      runeS
                    }
                  }

                StaticArrayFromValuesSE(
                  rangeS, ruleBuilder.toArray, mutabilityRuneS, variabilityRuneS, sizeRuneS, argsSE.toVector)
              } else {
                if (argsSE.size != 1) {
                  throw CompileErrorExceptionS(InitializingStaticSizedArrayRequiresSizeAndCallable(rangeS))
                }
                val sizeRuneS =
                  maybeSizeRuneS match {
                    case Some(s) => s
                    case None => throw CompileErrorExceptionS(InitializingStaticSizedArrayFromCallableNeedsSizeTemplex(rangeS))
                  }
                val List(callableSE) = argsSE
                StaticArrayFromCallableSE(rangeS, ruleBuilder.toArray, mutabilityRuneS, variabilityRuneS, sizeRuneS, callableSE)
              }
            }
          }

        (stackFrame1, NormalResult(rangeS, result), selfUses, childUses)
      }
      case b @ BlockPE(_, _) => {
        val (result, selfUses, childUses) =
          scoutBlock(stackFrame0.parentEnv, Some(stackFrame0), lidb.child(), b, noDeclarations)
        (stackFrame0, result, selfUses, childUses)
      }
      case AndPE(range, leftPE, rightPE) => {
        val rightRange = evalRange(rightPE.range)
        val endRange = RangeS(rightRange.end, rightRange.end)

        val (NormalResult(_, condSE), condUses, condChildUses) =
          scoutBlock(stackFrame0.parentEnv, Some(stackFrame0), lidb.child(), leftPE, noDeclarations)
        val (NormalResult(_, thenSE), thenUses, thenChildUses) =
          scoutBlock(stackFrame0.parentEnv, Some(stackFrame0), lidb.child(), rightPE, noDeclarations)
        val elseUses = VariableUses(Vector.empty)
        val elseChildUses = VariableUses(Vector.empty)
        val elseSE = BlockSE(endRange, Vector.empty, Vector(ConstantBoolSE(endRange, false)))

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
          scoutBlock(stackFrame0.parentEnv, Some(stackFrame0), lidb.child(), leftPE, noDeclarations)
        val thenUses = VariableUses(Vector.empty)
        val thenChildUses = VariableUses(Vector.empty)
        val thenSE = BlockSE(endRange, Vector.empty, Vector(ConstantBoolSE(endRange, true)))
        val (NormalResult(_, elseSE), elseUses, elseChildUses) =
          scoutBlock(stackFrame0.parentEnv, Some(stackFrame0), lidb.child(), rightPE, noDeclarations)

        val selfCaseUses = thenUses.branchMerge(elseUses)
        val selfUses = condUses.thenMerge(selfCaseUses);
        val childCaseUses = thenChildUses.branchMerge(elseChildUses)
        val childUses = condChildUses.thenMerge(childCaseUses);

        val ifSE = IfSE(evalRange(range), condSE, thenSE, elseSE)
        (stackFrame0, NormalResult(evalRange(range), ifSE), selfUses, childUses)
      }
      case IfPE(range, condition, thenBody, elseBody) => {
        val (NormalResult(_, cond1), condUses, condChildUses) =
          scoutBlock(stackFrame0.parentEnv, Some(stackFrame0), lidb.child(), condition, noDeclarations)
        val (NormalResult(_, then1), thenUses, thenChildUses) =
          scoutBlock(stackFrame0.parentEnv, Some(stackFrame0), lidb.child(), thenBody, noDeclarations)
        val (NormalResult(_, else1), elseUses, elseChildUses) =
          scoutBlock(stackFrame0.parentEnv, Some(stackFrame0), lidb.child(), elseBody, noDeclarations)

        val selfCaseUses = thenUses.branchMerge(elseUses)
        val selfUses = condUses.thenMerge(selfCaseUses);
        val childCaseUses = thenChildUses.branchMerge(elseChildUses)
        val childUses = condChildUses.thenMerge(childCaseUses);

        val ifSE = IfSE(evalRange(range), cond1, then1, else1)
        (stackFrame0, NormalResult(evalRange(range), ifSE), selfUses, childUses)
      }
      case WhilePE(range, condition, body) => {
        val (NormalResult(_, cond1), condSelfUses, condChildUses) =
          scoutBlock(stackFrame0.parentEnv, Some(stackFrame0), lidb.child(), condition, noDeclarations)

        // Ignoring exported names
        val (NormalResult(_, body1), bodySelfUses, bodyChildUses) =
          scoutBlock(stackFrame0.parentEnv, Some(stackFrame0), lidb.child(), body, noDeclarations)

        // Condition's uses isn't sent through a branch merge because the condition
        // is *always* evaluated (at least once).
        val selfUses = condSelfUses.thenMerge(bodySelfUses)
        val childUses = condChildUses.thenMerge(bodyChildUses)

        (stackFrame0, NormalResult(evalRange(range), WhileSE(evalRange(range), cond1, body1)), selfUses, childUses)
      }
      case BadLetPE(range) => {
        throw CompileErrorExceptionS(ForgotSetKeywordError(evalRange(range)))
      }
      case let @ LetPE(range, rulesP, patternP, exprPE) => {
        val codeLocation = Scout.evalPos(stackFrame0.file, range.begin)
        val (stackFrame1, expr1, selfUses, childUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), exprPE, UseP);

        val letFullName = LetNameS(codeLocation)

        val ruleBuilder = ArrayBuffer[IRulexSR]()
        val runeToExplicitType = mutable.HashMap[IRuneS, ITemplataType]()

        RuleScout.translateRulexes(
          stackFrame0.parentEnv, lidb.child(), ruleBuilder, runeToExplicitType, rulesP.toVector.flatMap(_.rules))
//
//        // See MKKRFA
//        val knowableRunesFromAbove = stackFrame1.parentEnv.allDeclaredRunes()

        val patternS =
          PatternScout.translatePattern(
            stackFrame1, lidb.child(), ruleBuilder, runeToExplicitType, patternP)
//
//        val (tentativeRuneToCanonicalRune, world) =
//          Optimizer.optimize(
//            ruleBuilder.builder,
//            (inputRule: IRulexSR[Int, RangeS, IValueSR, IValueSR]) => TemplarPuzzler.apply(inputRule))
//
//        val Conclusions(knowableValueRunesS, predictedruneToType) =
//          PredictorEvaluator.solve(
//            evalRange(range),
//            ruleBuilder.runeSToTentativeRune,
//            tentativeRuneToCanonicalRune,
//            ruleBuilder.tentativeRuneToType,
//            world)

        val declarationsFromPattern = VariableDeclarations(PatternScout.getParameterCaptures(patternS))

        val nameConflictVarNames =
          stackFrame1.locals.vars.map(_.name).intersect(declarationsFromPattern.vars.map(_.name))
        nameConflictVarNames.headOption match {
          case None =>
          case Some(nameConflictVarName) => {
            throw CompileErrorExceptionS(VariableNameAlreadyExists(evalRange(range), nameConflictVarName))
          }
        }

//        val runeSToCanonicalRune = ruleBuilder.runeSToTentativeRune.mapValues(tentativeRune => tentativeRuneToCanonicalRune(tentativeRune))

        val letSE = LetSE(evalRange(range), ruleBuilder.toArray, patternS, expr1)
        (stackFrame1 ++ declarationsFromPattern, NormalResult(evalRange(range), letSE), selfUses, childUses)
      }
      case MutatePE(mutateRange, destinationExprPE, sourceExprPE) => {
        val (stackFrame1, sourceExpr1, sourceInnerSelfUses, sourceChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), sourceExprPE, UseP);
        val (stackFrame2, destinationResult1, destinationSelfUses, destinationChildUses) =
          scoutExpression(stackFrame1, lidb.child(), destinationExprPE);
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
              scoutExpressionAndCoerce(stackFrame0, lidb.child(), containerExprPE, LendConstraintP(None));
            (stackFrame1, NormalResult(evalRange(rangeP), DotSE(evalRange(rangeP), containerExpr, memberName, true)), selfUses, childUses)
          }
        }
      }
      case IndexPE(range, containerExprPE, Vector(indexExprPE)) => {
        val (stackFrame1, containerExpr1, containerSelfUses, containerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), containerExprPE, LendConstraintP(None));
        val (stackFrame2, indexExpr1, indexSelfUses, indexChildUses) =
          scoutExpressionAndCoerce(stackFrame1, lidb.child(), indexExprPE, UseP);
        val dot1 = IndexSE(evalRange(range), containerExpr1, indexExpr1)
        (stackFrame2, NormalResult(evalRange(range), dot1), containerSelfUses.thenMerge(indexSelfUses), containerChildUses.thenMerge(indexChildUses))
      }
    }
  }

  // If we load an immutable with targetOwnershipIfLookupResult = Own or Borrow, it will just be Share.
  def scoutExpressionAndCoerce(
      stackFramePE: StackFrame,
    lidb: LocationInDenizenBuilder,
    exprPE: IExpressionPE,
    targetOwnershipIfLookupResult: LoadAsP):
  (StackFrame, IExpressionSE, VariableUses, VariableUses) = {
    val (namesFromInsideFirst, firstResult1, firstInnerSelfUses, firstChildUses) =
      scoutExpression(stackFramePE, lidb.child(), exprPE);
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
          val ruleBuilder = ArrayBuffer[IRulexSR]()
          val maybeTemplateArgRunes =
            maybeTemplateArgs.map(templateArgs => {
              templateArgs.map(templateArgPT => {
                TemplexScout.translateTemplex(
                  stackFramePE.parentEnv, lidb.child(), ruleBuilder, templateArgPT)
              })
            })
          val load = OutsideLoadSE(range, ruleBuilder.toArray, CodeNameS(name), maybeTemplateArgRunes, targetOwnershipIfLookupResult)
          (load, firstInnerSelfUses)
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
  def scoutElementsAsExpressions(
    stackFramePE: StackFrame,
    lidb: LocationInDenizenBuilder,
    exprs: List[IExpressionPE]):
  (StackFrame, List[IExpressionSE], VariableUses, VariableUses) = {
    exprs match {
      case Nil => (stackFramePE, List(), noVariableUses, noVariableUses)
      case firstPE :: restPE => {
        val (stackFrame1, firstExpr1, firstSelfUses, firstChildUses) =
          scoutExpressionAndCoerce(stackFramePE, lidb.child(), firstPE, UseP)
        val (finalStackFrame, rest1, restSelfUses, restChildUses) =
          scoutElementsAsExpressions(stackFrame1, lidb.child(), restPE);
        (finalStackFrame, firstExpr1 :: rest1, firstSelfUses.thenMerge(restSelfUses), firstChildUses.thenMerge(restChildUses))
      }
    }
  }

//  private def flattenScramble(elements: Vector[Expression1]): Vector[Expression1] = {
//    elements.map((expr: Expression1) => {
//      expr match {
//        case Scramble1(elementElements) => flattenScramble(elementElements)
//        case _ => Vector(expr)
//      }
//    }).foldLeft(Vector[Expression1]())(_ ++ _)
//  }
}
