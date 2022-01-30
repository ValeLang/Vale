package net.verdagon.vale.scout

import net.verdagon.vale.parser.ast.{AndPE, BlockPE, ConsecutorPE, ConstantBoolPE, ConstantFloatPE, ConstantIntPE, ConstantStrPE, ConstructArrayPE, DestructPE, DotPE, EachPE, FinalP, FunctionCallPE, FunctionP, IExpressionPE, ITemplexPT, IfPE, IndexPE, IterableNameDeclarationP, IterableNameP, IterationOptionNameDeclarationP, IterationOptionNameP, IteratorNameDeclarationP, IteratorNameP, LambdaPE, LetPE, LoadAsBorrowOrIfContainerIsPointerThenPointerP, LoadAsBorrowP, LoadAsP, LoadAsPointerP, LoadAsWeakP, LoadPE, LookupNameP, LookupPE, MagicParamLookupPE, MethodCallPE, MoveP, MutableP, MutatePE, NameP, OrPE, PackPE, PatternPP, ReturnPE, RuntimeSizedP, ShortcallPE, StaticSizedP, StrInterpolatePE, TemplateArgsP, TuplePE, UseP, VoidPE, WhilePE}
import net.verdagon.vale.parser.{ast, _}
import net.verdagon.vale.{RangeS, scout, vassert, vcurious, vfail, vimpl, vwat}
import net.verdagon.vale.scout.Scout.{evalRange, noDeclarations, noVariableUses}
import net.verdagon.vale.scout.patterns.{AtomSP, CaptureS, PatternScout}
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
case class NormalResult[+T <: IExpressionSE](expr: T) extends IScoutResult[T] {
  override def hashCode(): Int = vcurious()
  def range: RangeS = expr.range
}

class ExpressionScout(delegate: IExpressionScoutDelegate) {
  def endsWithReturn(exprSE: IExpressionSE): Boolean = {
    exprSE match {
      case ReturnSE(_, _) => true
      case ConsecutorSE(exprs) => endsWithReturn(exprs.last)
      case _ => false
    }
  }

  def scoutBlock(
    parentStackFrame: StackFrame,
    lidb: LocationInDenizenBuilder,
    // When we scout a function, it might hand in things here because it wants them to be considered part of
    // the body's block, so that we get to reuse the code at the bottom of function, tracking uses etc.
    initialLocals: VariableDeclarations,
    resultRequested: Boolean,
    blockPE: BlockPE):
  (BlockSE, VariableUses, VariableUses) = {
    val BlockPE(range, inner) = blockPE
    newBlock(
      parentStackFrame.parentEnv,
      Some(parentStackFrame),
      lidb.child(),
      Scout.evalRange(parentStackFrame.file, range),
      initialLocals,
      resultRequested,
      (stackFrame1, lidb, resultRequested) => {
        val (stackFrame2, exprSE, selfUses, childUses) =
          scoutExpressionAndCoerce(
            stackFrame1, lidb, inner, UseP, resultRequested)
//        val maybeVoidedExprSE =
//          if (resultRequested) {
//            exprSE
//          } else {
//            Scout.consecutive(
//              Vector(
//                exprSE,
//                VoidSE(
//                  Scout.evalRange(parentStackFrame.file, Range(range.end, range.end)))))
//          }
        (stackFrame2, exprSE, selfUses, childUses)
      })
  }

  def newBlock(
    functionBodyEnv: FunctionEnvironment,
    parentStackFrame: Option[StackFrame],
    lidb: LocationInDenizenBuilder,
    rangeS: RangeS,
    // When we scout a function, it might hand in things here because it wants them to be considered part of
    // the body's block, so that we get to reuse the code at the bottom of function, tracking uses etc.
    initialLocals: VariableDeclarations,
    resultRequested: Boolean,
    // If there's anything else we'd like to put at the end of the block, we can pass it in here
    scoutContents: (StackFrame, LocationInDenizenBuilder, Boolean) => (StackFrame, IExpressionSE, VariableUses, VariableUses)):
  (BlockSE, VariableUses, VariableUses) = {
    val initialStackFrame =
      StackFrame(functionBodyEnv.file, functionBodyEnv.name, functionBodyEnv, parentStackFrame, initialLocals)
//    val rangeS = evalRange(functionBodyEnv.file, blockPE.range)
//
//    val (stackFrameBeforeExtrasAndConstructing, exprsWithoutExtrasWithoutConstructingWithoutVoidS, selfUsesBeforeExtrasAndConstructing, childUsesBeforeExtrasAndConstructing) =
//      scoutElementsAsExpressions(initialStackFrame, lidb.child(), blockPE.elements, resultRequested)

    val (stackFrameBeforeConstructing, exprWithoutConstructingWithoutVoidS, selfUsesBeforeConstructing, childUsesBeforeConstructing) =
      scoutContents(initialStackFrame, lidb.child(), resultRequested)

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

    val (stackFrame, exprWithConstructingIfNecessary, selfUses, childUses) =
      if (constructedMembersNames.isEmpty) {
        // Add a void to the end, unless:
        // - we're constructing
        // - we end with a return
        // - a result was requested.
        val exprsS =
            (if (endsWithReturn(exprWithoutConstructingWithoutVoidS)) {
              exprWithoutConstructingWithoutVoidS
            } else {
              if (resultRequested) {
                exprWithoutConstructingWithoutVoidS
              } else {
                Scout.consecutive(
                  Vector(
                    exprWithoutConstructingWithoutVoidS,
                    VoidSE(RangeS(rangeS.end, rangeS.end))))
              }
            })
        (stackFrameBeforeConstructing, exprsS, selfUsesBeforeConstructing, childUsesBeforeConstructing)
      } else {
        val rangeAtEnd = ast.RangeP(rangeS.end.offset, rangeS.end.offset)
        val constructorCallP =
          FunctionCallPE(
            rangeAtEnd,
            ast.RangeP.zero,
            LookupPE(
              stackFrameBeforeConstructing.parentEnv.name match {
                case FunctionNameS(n, _) => LookupNameP(NameP(rangeAtEnd, n))
                case _ => vwat()
              }, None),
            constructedMembersNames.map(n => DotPE(rangeAtEnd, LookupPE(LookupNameP(NameP(rangeAtEnd, "this")), None), ast.RangeP.zero, NameP(rangeAtEnd, n))),
            false)

        val (stackFrameAfterConstructing, NormalResult(constructExpression), selfUsesAfterConstructing, childUsesAfterConstructing) =
          scoutExpression(stackFrameBeforeConstructing, lidb.child(), constructorCallP, true)
        val exprAfterConstructing =
          Scout.consecutive(
            Vector(
              exprWithoutConstructingWithoutVoidS,
              constructExpression))
        (stackFrameAfterConstructing, exprAfterConstructing, selfUsesBeforeConstructing.thenMerge(selfUsesAfterConstructing), childUsesBeforeConstructing.thenMerge(childUsesAfterConstructing))
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
    (BlockSE(rangeS, locals, exprWithConstructingIfNecessary), selfUsesOfThingsFromAbove, childUsesOfThingsFromAbove)
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
    expr: IExpressionPE,
    // Whether the parent expression wants to put the result somewhere.
    // This trickles down and affects whether e.g. if-statements produce anything.
    resultRequested: Boolean):
  (StackFrame, IScoutResult[IExpressionSE], VariableUses, VariableUses) = {
    val evalRange = (range: ast.RangeP) => Scout.evalRange(stackFrame0.file, range)

    expr match {
      case VoidPE(range) => (stackFrame0, NormalResult(VoidSE(evalRange(range))), noVariableUses, noVariableUses)
      case lam @ LambdaPE(captures,_) => {
        val (function1, childUses) =
          delegate.scoutLambda(stackFrame0, lam.function)

        (stackFrame0, NormalResult(FunctionSE(function1)), noVariableUses, childUses)
      }
      case StrInterpolatePE(range, partsPE) => {
        val (stackFrame1, partsSE, partsSelfUses, partsChildUses) =
          scoutElementsAsExpressions(stackFrame0, lidb.child(), partsPE, true)

        val rangeS = evalRange(range)
        val startingExpr: IExpressionSE = ConstantStrSE(RangeS(rangeS.begin, rangeS.begin), "")
        val addedExpr =
          partsSE.foldLeft(startingExpr)({
            case (prevExpr, partSE) => {
              val addCallRange = RangeS(prevExpr.range.end, partSE.range.begin)
              FunctionCallSE(
                addCallRange,
                OutsideLoadSE(addCallRange, Array(), CodeNameS("+"), None, LoadAsBorrowP(None)),
                Vector(prevExpr, partSE))
            }
          })
        (stackFrame1, NormalResult(addedExpr), partsSelfUses, partsChildUses)
      }
      case LoadPE(range, innerPE, targetOwnership) => {
        val (stackFrame1, inner1, innerSelfUses, innerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), innerPE, targetOwnership, true)
        inner1 match {
          case OwnershippedSE(_, _, loadAs) => vassert(loadAs == targetOwnership)
          case LocalLoadSE(_, _, loadAs) => vassert(loadAs == targetOwnership)
          case OutsideLoadSE(_, _, _, _, loadAs) => vassert(loadAs == targetOwnership)
          case _ => vwat()
        }
        (stackFrame1, NormalResult(inner1), innerSelfUses, innerChildUses)
      }
      case ReturnPE(range, innerPE) => {
        val (stackFrame1, inner1, innerSelfUses, innerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), innerPE, UseP, true)
        (stackFrame1, NormalResult(ReturnSE(evalRange(range), inner1)), innerSelfUses, innerChildUses)
      }
      case ConstantIntPE(range, value, bits) => (stackFrame0, NormalResult(ConstantIntSE(evalRange(range), value, bits)), noVariableUses, noVariableUses)
      case ConstantBoolPE(range,value) => (stackFrame0, NormalResult(ConstantBoolSE(evalRange(range), value)), noVariableUses, noVariableUses)
      case ConstantStrPE(range, value) => (stackFrame0, NormalResult(ConstantStrSE(evalRange(range), value)), noVariableUses, noVariableUses)
      case ConstantFloatPE(range,value) => (stackFrame0, NormalResult(ConstantFloatSE(evalRange(range), value)), noVariableUses, noVariableUses)

      case MagicParamLookupPE(range) => {
        val name = MagicParamNameS(Scout.evalPos(stackFrame0.file, range.begin))
        val lookup = LocalLookupResult(evalRange(range), name)
        // We dont declare it here, because then scoutBlock will think its a local and
        // hide it from those above.
        //   val declarations = VariableDeclarations(Vector(VariableDeclaration(lookup.name, FinalP)))
        // Leave it to scoutLambda to declare it.
        (stackFrame0, lookup, noVariableUses.markMoved(name), noVariableUses)
      }
      case LookupPE(lookupName, None) => {
        val rangeS = evalRange(lookupName.range)
        val impreciseNameS = Scout.translateImpreciseName(stackFrame0.file, lookupName)
        val lookup =
          stackFrame0.findVariable(impreciseNameS) match {
            case Some(fullName) => {
              (LocalLookupResult(rangeS, fullName))
            }
            case None => {
              impreciseNameS match {
                case CodeNameS(name) => {
                  if (stackFrame0.parentEnv.allDeclaredRunes().contains(CodeRuneS(name))) {
                    (NormalResult(RuneLookupSE(rangeS, CodeRuneS(name))))
                  } else {
                    (OutsideLookupResult(rangeS, name, None))
                  }
                }
                case other => vwat(other)
              }
            }
          }
        (stackFrame0, lookup, noVariableUses, noVariableUses)
      }
      case LookupPE(impreciseName, Some(TemplateArgsP(_, templateArgs))) => {
        val (range, templateName) =
          impreciseName match {
            case LookupNameP(NameP(range, templateName)) => (range, templateName)
            case _ => vwat()
          }
        val result =
          OutsideLookupResult(
            evalRange(range),
            templateName,
            Some(templateArgs.toArray))
        (stackFrame0, result, noVariableUses, noVariableUses)
      }
      case DestructPE(range, innerPE) => {
        val (stackFrame1, inner1, innerSelfUses, innerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), innerPE, UseP, true)
        (stackFrame1, NormalResult(DestructSE(evalRange(range), inner1)), innerSelfUses, innerChildUses)
      }
//      case ResultPE(range, innerPE) => {
//        scoutExpression(stackFrame0, lidb.child(), innerPE, true)
//      }
      case PackPE(range, innersPE) => {
        vassert(innersPE.size == 1)
        val (stackFrame1, inner1, innerSelfUses, innerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), innersPE.head, UseP, true)
        (stackFrame1, NormalResult(inner1), innerSelfUses, innerChildUses)
      }
      case FunctionCallPE(range, _, callablePE, args, borrowCallable) => {
        val (stackFrame1, callable1, callableSelfUses, callableChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), callablePE, borrowCallable, true)
        val (stackFrame2, args1, argsSelfUses, argsChildUses) =
          scoutElementsAsExpressions(stackFrame1, lidb.child(), args, true)
        val result = NormalResult(FunctionCallSE(evalRange(range), callable1, args1.toVector))
        (stackFrame2, result, callableSelfUses.thenMerge(argsSelfUses), callableChildUses.thenMerge(argsChildUses))
      }
      case MethodCallPE(range, subjectExpr, operatorRange, subjectTargetOwnership, memberLookup, methodArgs) => {
        val (stackFrame1, callable1, callableSelfUses, callableChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), memberLookup, LoadAsBorrowP(None), true)
        val (stackFrame2, subject1, subjectSelfUses, subjectChildUses) =
          scoutExpressionAndCoerce(stackFrame1, lidb.child(), subjectExpr, subjectTargetOwnership, true)
        val (stackFrame3, tailArgs1, tailArgsSelfUses, tailArgsChildUses) =
          scoutElementsAsExpressions(stackFrame2, lidb.child(), methodArgs, true)

        val selfUses = callableSelfUses.thenMerge(subjectSelfUses).thenMerge(tailArgsSelfUses)
        val childUses = callableChildUses.thenMerge(subjectChildUses).thenMerge(tailArgsChildUses)
        val args = Vector(subject1) ++ tailArgs1

        val result = NormalResult(FunctionCallSE(evalRange(range), callable1, args))
        (stackFrame3, result, selfUses, childUses)
      }
      case TuplePE(range, elementsPE) => {
        val (stackFrame1, elements1, selfUses, childUses) =
          scoutElementsAsExpressions(stackFrame0, lidb.child(), elementsPE, true)
        (stackFrame1, NormalResult(TupleSE(evalRange(range), elements1.toVector)), selfUses, childUses)
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
          scoutElementsAsExpressions(stackFrame0, lidb.child(), argsPE, true)

        val result =
          size match {
            case RuntimeSizedP => {
              if (initializingIndividualElements) {
                throw CompileErrorExceptionS(CantInitializeIndividualElementsOfRuntimeSizedArray(rangeS))
              }
              if (argsSE.size != 2) {
                throw CompileErrorExceptionS(InitializingRuntimeSizedArrayRequiresSizeAndCallable(rangeS))
              }
              val Vector(sizeSE, callableSE) = argsSE

              RuntimeArrayFromCallableSE(rangeS, ruleBuilder.toArray, mutabilityRuneS, sizeSE, callableSE)
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
                val Vector(callableSE) = argsSE
                StaticArrayFromCallableSE(rangeS, ruleBuilder.toArray, mutabilityRuneS, variabilityRuneS, sizeRuneS, callableSE)
              }
            }
          }

        (stackFrame1, NormalResult(result), selfUses, childUses)
      }
      case b @ BlockPE(_, _) => {
        val (resultSE, selfUses, childUses) =
          scoutBlock(stackFrame0, lidb.child(), noDeclarations, resultRequested, b)
        (stackFrame0, NormalResult(resultSE), selfUses, childUses)
      }
      case ConsecutorPE(inners) => {
        val (stackFrame1, resultsSE, selfUses, childUses) =
          scoutElementsAsExpressions(stackFrame0, lidb.child(), inners, resultRequested)
        (stackFrame1, NormalResult(Scout.consecutive(resultsSE)), selfUses, childUses)
      }
      case AndPE(range, leftPE, rightPE) => {
        val rightRange = evalRange(rightPE.range)
        val endRange = RangeS(rightRange.end, rightRange.end)

        val (stackFrame1, condSE, condUses, condChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), leftPE, UseP, true)
        val (thenSE, thenUses, thenChildUses) =
          scoutBlock(stackFrame1, lidb.child(), noDeclarations, resultRequested, rightPE)
        val elseUses = VariableUses(Vector.empty)
        val elseChildUses = VariableUses(Vector.empty)
        val elseSE = BlockSE(endRange, Vector.empty, ConstantBoolSE(endRange, false))

        val selfCaseUses = thenUses.branchMerge(elseUses)
        val selfUses = condUses.thenMerge(selfCaseUses);
        val childCaseUses = thenChildUses.branchMerge(elseChildUses)
        val childUses = condChildUses.thenMerge(childCaseUses);

        val ifSE = IfSE(evalRange(range), condSE, thenSE, elseSE)
        (stackFrame1, NormalResult(ifSE), selfUses, childUses)
      }
      case OrPE(range, leftPE, rightPE) => {
        val rightRange = evalRange(rightPE.range)
        val endRange = RangeS(rightRange.end, rightRange.end)

        val (stackFrame1, condSE, condUses, condChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), leftPE, UseP, true)
        val thenUses = VariableUses(Vector.empty)
        val thenChildUses = VariableUses(Vector.empty)
        val thenSE = BlockSE(endRange, Vector.empty, ConstantBoolSE(endRange, true))
        val (elseSE, elseUses, elseChildUses) =
          scoutBlock(stackFrame1, lidb.child(), noDeclarations, true, rightPE)

        val selfCaseUses = thenUses.branchMerge(elseUses)
        val selfUses = condUses.thenMerge(selfCaseUses);
        val childCaseUses = thenChildUses.branchMerge(elseChildUses)
        val childUses = condChildUses.thenMerge(childCaseUses);

        val ifSE = IfSE(evalRange(range), condSE, thenSE, elseSE)
        (stackFrame1, NormalResult(ifSE), selfUses, childUses)
      }
      case IfPE(range, condition, thenBody, elseBody) => {
        val (resultSE, selfUses, childUses) =
          newBlock(
            stackFrame0.parentEnv,
            Some(stackFrame0),
            lidb.child(),
            evalRange(range),
            noDeclarations,
            resultRequested,
            (stackFrame1, lidb, resultRequested) => {
              val (stackFrame2, condSE, condUses, condChildUses) =
                scoutExpressionAndCoerce(stackFrame1, lidb.child(), condition, UseP, true)
              val (thenSE, thenUses, thenChildUses) =
                scoutBlock(stackFrame2, lidb.child(), noDeclarations, resultRequested, thenBody)
              val (elseSE, elseUses, elseChildUses) =
                scoutBlock(stackFrame2, lidb.child(), noDeclarations, resultRequested, elseBody)

              val selfCaseUses = thenUses.branchMerge(elseUses)
              val selfUses = condUses.thenMerge(selfCaseUses);
              val childCaseUses = thenChildUses.branchMerge(elseChildUses)
              val childUses = condChildUses.thenMerge(childCaseUses);

              val ifSE = IfSE(evalRange(range), condSE, thenSE, elseSE)
              (stackFrame2, ifSE, selfUses, childUses)
            })
        (stackFrame0, NormalResult(resultSE), selfUses, childUses)
      }
      case WhilePE(range, conditionPE, uncombinedBodyPE) => {
        val (combinedBodySE, selfUses, childUses) =
          newBlock(
            stackFrame0.parentEnv,
            Some(stackFrame0),
            lidb.child(),
            evalRange(range),
            noDeclarations,
            true,
            (stackFrame1, lidb, resultRequested) => {
              vassert(resultRequested)
              val (stackFrame2, condSE, condSelfUses, condChildUses) =
                scoutExpressionAndCoerce(stackFrame1, lidb.child(), conditionPE, UseP, true)

              val (thenSE, thenSelfUses, thenChildUses) =
                scoutBlock(
                  stackFrame2, lidb.child(), noDeclarations, true,
                  BlockPE(range, ConsecutorPE(Vector(uncombinedBodyPE, ConstantBoolPE(range, true)))))

              val elseSE = BlockSE(evalRange(range), Vector(), ConstantBoolSE(evalRange(range), false))

              // Condition's uses isn't sent through a branch merge because the condition
              // is *always* evaluated (at least once).
              val selfCaseUses = thenSelfUses.branchMerge(noVariableUses)
              val selfUses = condSelfUses.thenMerge(selfCaseUses);
              val childCaseUses = thenChildUses.branchMerge(noVariableUses)
              val childUses = condChildUses.thenMerge(childCaseUses);

              val ifSE = IfSE(evalRange(range), condSE, thenSE, elseSE)
              (stackFrame2, ifSE, selfUses, childUses)
            })

        (stackFrame0, NormalResult(WhileSE(evalRange(range), combinedBodySE)), selfUses, childUses)
      }
      case EachPE(range, entryPatternPP, inKeywordRange, iterableExpr, body) => {
        val transformedPE =
          transformEach(range, entryPatternPP, inKeywordRange, iterableExpr, body)
        scoutExpression(stackFrame0, lidb.child(), transformedPE, resultRequested)
      }
      case BadLetPE(range) => {
        throw CompileErrorExceptionS(ForgotSetKeywordError(evalRange(range)))
      }
      case LetPE(range, rulesP, patternP, exprPE) => {
        val codeLocation = Scout.evalPos(stackFrame0.file, range.begin)
        val (stackFrame1, expr1, selfUses, childUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), exprPE, UseP, true);

        val ruleBuilder = ArrayBuffer[IRulexSR]()
        val runeToExplicitType = mutable.HashMap[IRuneS, ITemplataType]()

        RuleScout.translateRulexes(
          stackFrame0.parentEnv, lidb.child(), ruleBuilder, runeToExplicitType, rulesP.toVector.flatMap(_.rules))

        val patternS =
          PatternScout.translatePattern(
            stackFrame1, lidb.child(), ruleBuilder, runeToExplicitType, patternP)

        val declarationsFromPattern = VariableDeclarations(PatternScout.getParameterCaptures(patternS))

        val nameConflictVarNames =
          stackFrame1.locals.vars.map(_.name).intersect(declarationsFromPattern.vars.map(_.name))
        nameConflictVarNames.headOption match {
          case None =>
          case Some(nameConflictVarName) => {
            throw CompileErrorExceptionS(VariableNameAlreadyExists(evalRange(range), nameConflictVarName))
          }
        }

        val letSE = LetSE(evalRange(range), ruleBuilder.toArray, patternS, expr1)
        (stackFrame1 ++ declarationsFromPattern, NormalResult(letSE), selfUses, childUses)
      }
      case MutatePE(mutateRange, destinationExprPE, sourceExprPE) => {
        val (stackFrame1, sourceExpr1, sourceInnerSelfUses, sourceChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), sourceExprPE, UseP, true);
        val (stackFrame2, destinationResult1, destinationSelfUses, destinationChildUses) =
          scoutExpression(stackFrame1, lidb.child(), destinationExprPE, true);
        val (mutateExpr1, sourceSelfUses) =
          destinationResult1 match {
            case LocalLookupResult(range, name) => {
              (LocalMutateSE(range, name, sourceExpr1), sourceInnerSelfUses.markMutated(name))
            }
            case OutsideLookupResult(range, name, maybeTemplateArgs) => {
              throw CompileErrorExceptionS(CouldntFindVarToMutateS(range, name))
            }
            case NormalResult(destinationExpr1) => {
              (ExprMutateSE(destinationExpr1.range, destinationExpr1, sourceExpr1), sourceInnerSelfUses)
            }
          }
        (stackFrame2, NormalResult(mutateExpr1), sourceSelfUses.thenMerge(destinationSelfUses), sourceChildUses.thenMerge(destinationChildUses))
      }
      case DotPE(rangeP, containerExprPE, _, isMapCall, NameP(_, memberName)) => {
        containerExprPE match {
          // Here, we're special casing lookups of this.x when we're in a constructor.
          // We know we're in a constructor if there's no `this` variable yet. After all,
          // in a constructor, `this` is just an imaginary concept until we actually
          // fill all the variables.
          case LookupPE(LookupNameP(NameP(range, "this")), _) if (stackFrame0.findVariable(CodeNameS("this")).isEmpty) => {
            val result = LocalLookupResult(evalRange(range), ConstructingMemberNameS(memberName))
            (stackFrame0, result, noVariableUses, noVariableUses)
          }
          case _ => {
            val (stackFrame1, containerExpr, selfUses, childUses) =
              scoutExpressionAndCoerce(stackFrame0, lidb.child(), containerExprPE, LoadAsBorrowOrIfContainerIsPointerThenPointerP(None), true)
            (stackFrame1, NormalResult(DotSE(evalRange(rangeP), containerExpr, memberName, true)), selfUses, childUses)
          }
        }
      }
      case IndexPE(range, containerExprPE, Vector(indexExprPE)) => {
        val (stackFrame1, containerExpr1, containerSelfUses, containerChildUses) =
          scoutExpressionAndCoerce(stackFrame0, lidb.child(), containerExprPE, LoadAsBorrowOrIfContainerIsPointerThenPointerP(None), true)
        val (stackFrame2, indexExpr1, indexSelfUses, indexChildUses) =
          scoutExpressionAndCoerce(stackFrame1, lidb.child(), indexExprPE, UseP, true);
        val dot1 = IndexSE(evalRange(range), containerExpr1, indexExpr1)
        (stackFrame2, NormalResult(dot1), containerSelfUses.thenMerge(indexSelfUses), containerChildUses.thenMerge(indexChildUses))
      }
      case ShortcallPE(range, argExprs) => {
        throw CompileErrorExceptionS(UnimplementedExpression(evalRange(range), "shortcalling"));
      }
    }
  }

  private def transformEach(range: ast.RangeP, entryPatternPP: PatternPP, inKeywordRange: ast.RangeP, iterableExpr: IExpressionPE, body: BlockPE) = {
    BlockPE(
      range,
      ConsecutorPE(
        Vector(
          LetPE(
            inKeywordRange,
            None,
            PatternPP(inKeywordRange, None, Some(IterableNameDeclarationP(inKeywordRange)), None, None, None),
            iterableExpr),
          LetPE(
            inKeywordRange,
            None,
            PatternPP(inKeywordRange, None, Some(IteratorNameDeclarationP(inKeywordRange)), None, None, None),
            FunctionCallPE(
              inKeywordRange,
              None,
              inKeywordRange,
              false,
              LookupPE(LookupNameP(NameP(inKeywordRange, "begin")), None),
              Vector(
                LoadPE(
                  inKeywordRange,
                  LookupPE(IterableNameP(inKeywordRange), None),
                  LoadAsBorrowP(None))),
              LoadAsBorrowP(None))),
          WhilePE(
            range,
            ConsecutorPE(
              Vector(
                LetPE(
                  entryPatternPP.range,
                  None,
                  PatternPP(inKeywordRange, None, Some(IterationOptionNameDeclarationP(inKeywordRange)), None, None, None),
                  FunctionCallPE(
                    inKeywordRange,
                    None,
                    inKeywordRange,
                    false,
                    LookupPE(LookupNameP(NameP(inKeywordRange, "next")), None),
                    Vector(
                      LoadPE(
                        inKeywordRange,
                        LookupPE(IteratorNameP(inKeywordRange), None),
                        LoadAsBorrowP(None))),
                    LoadAsBorrowP(None))),
                FunctionCallPE(
                  inKeywordRange,
                  None,
                  inKeywordRange,
                  false,
                  LookupPE(LookupNameP(NameP(inKeywordRange, "not")), None),
                  Vector(
                    FunctionCallPE(
                      inKeywordRange,
                      None,
                      inKeywordRange,
                      false,
                      LookupPE(LookupNameP(NameP(inKeywordRange, "isEmpty")), None),
                      Vector(
                        LoadPE(
                          inKeywordRange,
                          LookupPE(IterationOptionNameP(inKeywordRange), None),
                          LoadAsBorrowP(None))),
                      LoadAsBorrowP(None))),
                  LoadAsBorrowP(None)))),
              BlockPE(
                body.range,
                ConsecutorPE(
                  Vector(
                    LetPE(
                      inKeywordRange,
                      None,
                      entryPatternPP,
                      FunctionCallPE(
                        inKeywordRange,
                        None,
                        inKeywordRange,
                        false,
                        LookupPE(LookupNameP(NameP(inKeywordRange, "get")), None),
                        Vector(
                          LookupPE(IterationOptionNameP(inKeywordRange), None)),
                        LoadAsBorrowP(None))),
                    body)))))))
  }

  // If we load an immutable with targetOwnershipIfLookupResult = Own or Borrow, it will just be Share.
  def scoutExpressionAndCoerce(
      stackFramePE: StackFrame,
    lidb: LocationInDenizenBuilder,
    exprPE: IExpressionPE,
    loadAsP: LoadAsP,
    resultRequested: Boolean):
  (StackFrame, IExpressionSE, VariableUses, VariableUses) = {
    val (namesFromInsideFirst, firstResult1, firstInnerSelfUses, firstChildUses) =
      scoutExpression(stackFramePE, lidb.child(), exprPE, resultRequested);
    val (firstExpr1, firstSelfUses) =
      firstResult1 match {
        case LocalLookupResult(range, name) => {
          val uses =
            loadAsP match {
              case LoadAsBorrowP(_) => firstInnerSelfUses.markBorrowed(name)
              case LoadAsPointerP(_) => firstInnerSelfUses.markBorrowed(name)
              case LoadAsWeakP(_) => firstInnerSelfUses.markBorrowed(name)
              case LoadAsBorrowOrIfContainerIsPointerThenPointerP(_) => firstInnerSelfUses.markBorrowed(name)
              case UseP | MoveP => firstInnerSelfUses.markMoved(name)
            }
          (LocalLoadSE(range, name, loadAsP), uses)
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
          val load = OutsideLoadSE(range, ruleBuilder.toArray, CodeNameS(name), maybeTemplateArgRunes, loadAsP)
          (load, firstInnerSelfUses)
        }
        case NormalResult(innerExpr1) => {
          loadAsP match {
            case UseP => (innerExpr1, firstInnerSelfUses)
            case _ => (OwnershippedSE(innerExpr1.range, innerExpr1, loadAsP), firstInnerSelfUses)
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
    exprs: Vector[IExpressionPE],
    resultRequested: Boolean):
  (StackFrame, Vector[IExpressionSE], VariableUses, VariableUses) = {
    exprs.toList match {
      case Nil => (stackFramePE, Vector(), noVariableUses, noVariableUses)
      case firstPE :: restPE => {
        val innerResultRequested = if (restPE.isEmpty) resultRequested else false
        val (stackFrame1, firstExpr1, firstSelfUses, firstChildUses) =
          scoutExpressionAndCoerce(stackFramePE, lidb.child(), firstPE, UseP, innerResultRequested)
        val (finalStackFrame, rest1, restSelfUses, restChildUses) =
          scoutElementsAsExpressions(stackFrame1, lidb.child(), restPE.toVector, resultRequested);
        (finalStackFrame, firstExpr1 +: rest1, firstSelfUses.thenMerge(restSelfUses), firstChildUses.thenMerge(restChildUses))
      }
    }
  }
}

object ExpressionScout {
  def flattenExpressions(expr: IExpressionSE): Vector[IExpressionSE] = {
    expr match {
      case ConsecutorSE(exprs) => exprs.flatMap(flattenExpressions)
      case other => Vector(other)
    }
  }
}
