package net.verdagon.vale.astronomer

import net.verdagon.vale.astronomer.ruletyper.{IRuleTyperEvaluatorDelegate, RuleTyperEvaluator, RuleTyperSolveFailure, RuleTyperSolveSuccess}
import net.verdagon.vale.parser.{ConstraintP, WeakP}
import net.verdagon.vale.scout.rules.{EqualsSR, IntTypeSR, MutabilityTypeSR, TemplexSR, TypedSR, VariabilityTypeSR}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.{vassertSome, vfail}

object ExpressionAstronomer {
  def translateBlock(env: Environment, astrouts: AstroutsBox, blockS: BlockSE): BlockAE = {
    val BlockSE(range, locals, exprsS) = blockS
    val childEnv = env.addLocals(locals.map(translateLocalVariable))
    val exprsA = exprsS.map(translateExpression(childEnv, astrouts, _))
    BlockAE(range, exprsA)
  }

  def translateLocalVariable(varS: LocalVariable1): LocalVariableA = {
    val LocalVariable1(varNameS, variability, selfBorrowed, selfMoved, selfMutated, childBorrowed, childMoved, childMutated) = varS
    val varNameA = Astronomer.translateVarNameStep(varNameS)
    LocalVariableA(varNameA, variability, selfBorrowed, selfMoved, selfMutated, childBorrowed, childMoved, childMutated)
  }

  def translateExpression(env: Environment, astrouts: AstroutsBox, iexprS: IExpressionSE): IExpressionAE = {
    iexprS match {
      case LetSE(range, rules, allRunesS, localRunesS, patternS, expr) => {
        val allRunesA = allRunesS.map(Astronomer.translateRune)
        val localRunesA = localRunesS.map(Astronomer.translateRune)
        val (conclusions, rulesA) =
          Astronomer.makeRuleTyper().solve(astrouts, env, rules, range, List(patternS), Some(allRunesA)) match {
            case (_, rtsf @ RuleTyperSolveFailure(_, _, _, _)) => throw CompileErrorExceptionA(RangedInternalErrorA(range, rtsf.toString))
            case (c, RuleTyperSolveSuccess(r)) => (c, r)
          }
        val exprA = translateExpression(env, astrouts, expr)

        val patternA = Astronomer.translateAtom(env, patternS)

        LetAE(
          range,
          rulesA,
          conclusions.typeByRune,
          localRunesA,
          patternA,
          exprA)
      }
      case IfSE(range, conditionS, thenBodyS, elseBodyS) => {
        val conditionA = translateBlock(env, astrouts, conditionS)
        val thenBodyA = translateBlock(env, astrouts, thenBodyS)
        val elseBodyA = translateBlock(env, astrouts, elseBodyS)
        IfAE(range, conditionA, thenBodyA, elseBodyA)
      }
      case WhileSE(range, conditionS, bodyS) => {
        val conditionA = translateBlock(env, astrouts, conditionS)
        val bodyA = translateBlock(env, astrouts, bodyS)
        WhileAE(range, conditionA, bodyA)
      }
      case DestructSE(range, innerS) => {
        val exprA = translateExpression(env, astrouts, innerS)
        DestructAE(range, exprA)
      }
      case ExprMutateSE(rangeS, mutateeS, exprS) => {
        val conditionA = translateExpression(env, astrouts, mutateeS)
        val bodyA = translateExpression(env, astrouts, exprS)
        ExprMutateAE(rangeS, conditionA, bodyA)
      }
      case GlobalMutateSE(range, name, exprS) => {
        val exprA = translateExpression(env, astrouts, exprS)
        GlobalMutateAE(range, Astronomer.translateImpreciseName(name), exprA)
      }
      case LocalMutateSE(range, nameS, exprS) => {
        val exprA = translateExpression(env, astrouts, exprS)
        LocalMutateAE(range, Astronomer.translateVarNameStep(nameS), exprA)
      }
      case OwnershippedSE(range, innerExprS, targetOwnership) => {
        val innerExprA = translateExpression(env, astrouts, innerExprS)
        LendAE(range, innerExprA, targetOwnership)
      }
      case ReturnSE(range, innerExprS) => {
        val innerExprA = translateExpression(env, astrouts, innerExprS)
        (ReturnAE(range, innerExprA))
      }
      case blockS @ BlockSE(_, _, _) => translateBlock(env, astrouts, blockS)
      case ArgLookupSE(range, index) => (ArgLookupAE(range, index))
      case CheckRefCountSE(range, refExprS, category, numExprS) => {
        val refExprA = translateExpression(env, astrouts, refExprS)
        val numExprA = translateExpression(env, astrouts, numExprS)
        (CheckRefCountAE(range, refExprA, category, numExprA))
      }
      case RepeaterBlockSE(range, exprS) => {
        val exprA = translateExpression(env, astrouts, exprS)
        (RepeaterBlockAE(range, exprA))
      }
      case RepeaterBlockIteratorSE(range, exprS) => {
        val exprA = translateExpression(env, astrouts, exprS)
        (RepeaterBlockIteratorAE(range, exprA))
      }
      case VoidSE(range) => VoidAE(range)
      case TupleSE(range, elementsS) => {
        val elementsA = elementsS.map(translateExpression(env, astrouts, _))
        TupleAE(range, elementsA)
      }
      case StaticArrayFromValuesSE(range, maybeMutabilityST, maybeVariabilityST, maybeSizeST, elementsS) => {
        val rules =
          ((maybeMutabilityST.toList.map(mutabilityST => {
            EqualsSR(range, TypedSR(range, ArrayMutabilityImplicitRuneS(), MutabilityTypeSR), TemplexSR(mutabilityST))
          })) ++
          (maybeVariabilityST.toList.map(variabilityST => {
            EqualsSR(range, TypedSR(range, ArrayVariabilityImplicitRuneS(), VariabilityTypeSR), TemplexSR(variabilityST))
          })) ++
          (maybeSizeST.toList.map(sizeST => {
            EqualsSR(range, TypedSR(range, ArraySizeImplicitRuneS(), IntTypeSR), TemplexSR(sizeST))
          })))

        val maybeMutabilityRuneA = maybeMutabilityST.map(_ => ArrayMutabilityImplicitRuneA())
        val maybeVariabilityRuneA = maybeVariabilityST.map(_ => ArrayVariabilityImplicitRuneA())
        val maybeSizeRuneA = maybeSizeST.map(_ => ArraySizeImplicitRuneA())
        val runesA = maybeMutabilityRuneA.toList ++ maybeVariabilityRuneA.toList ++ maybeSizeRuneA.toList

        val (conclusions, rulesA) =
          makeRuleTyper().solve(astrouts, env, rules, range, List(), Some(runesA.toSet)) match {
            case (_, rtsf @ RuleTyperSolveFailure(_, _, _, _)) => vfail(rtsf.toString)
            case (c, RuleTyperSolveSuccess(r)) => (c, r)
          }

        val elementsA = elementsS.map(translateExpression(env, astrouts, _))

        StaticArrayFromValuesAE(range, rulesA, conclusions.typeByRune, maybeSizeRuneA, maybeMutabilityRuneA, maybeVariabilityRuneA, elementsA)
      }
      case StaticArrayFromCallableSE(range, maybeMutabilityST, maybeVariabilityST, sizeST, callableSE) => {
        val rules =
          ((maybeMutabilityST.toList.map(mutabilityST => {
            EqualsSR(range, TypedSR(range, ArrayMutabilityImplicitRuneS(), MutabilityTypeSR), TemplexSR(mutabilityST))
          })) ++
            (maybeVariabilityST.toList.map(variabilityST => {
              EqualsSR(range, TypedSR(range, ArrayVariabilityImplicitRuneS(), VariabilityTypeSR), TemplexSR(variabilityST))
            })) ++
            List(EqualsSR(range, TypedSR(range, ArraySizeImplicitRuneS(), IntTypeSR), TemplexSR(sizeST))))

        val maybeMutabilityRuneA = maybeMutabilityST.map(_ => ArrayMutabilityImplicitRuneA())
        val maybeVariabilityRuneA = maybeVariabilityST.map(_ => ArrayVariabilityImplicitRuneA())
        val sizeRuneA = ArraySizeImplicitRuneA()
        val runesA = maybeMutabilityRuneA.toList ++ maybeVariabilityRuneA.toList ++ List(sizeRuneA)

        val (conclusions, rulesA) =
          makeRuleTyper().solve(astrouts, env, rules, range, List(), Some(runesA.toSet)) match {
            case (_, rtsf @ RuleTyperSolveFailure(_, _, _, _)) => vfail(rtsf.toString)
            case (c, RuleTyperSolveSuccess(r)) => (c, r)
          }

        val callableAE = translateExpression(env, astrouts, callableSE)

        StaticArrayFromCallableAE(range, rulesA, conclusions.typeByRune, sizeRuneA, maybeMutabilityRuneA, maybeVariabilityRuneA, callableAE)
      }
      case RuntimeArrayFromCallableSE(range, maybeMutabilityST, maybeVariabilityST, sizeSE, callableSE) => {
        val rules =
          ((maybeMutabilityST.toList.map(mutabilityST => {
            EqualsSR(range, TypedSR(range, ArrayMutabilityImplicitRuneS(), MutabilityTypeSR), TemplexSR(mutabilityST))
          })) ++
          (maybeVariabilityST.toList.map(variabilityST => {
            EqualsSR(range, TypedSR(range, ArrayVariabilityImplicitRuneS(), VariabilityTypeSR), TemplexSR(variabilityST))
          })))

        val maybeMutabilityRuneA = maybeMutabilityST.map(_ => ArrayMutabilityImplicitRuneA())
        val maybeVariabilityRuneA = maybeVariabilityST.map(_ => ArrayVariabilityImplicitRuneA())
        val runesA = maybeMutabilityRuneA.toList ++ maybeVariabilityRuneA.toList

        val (conclusions, rulesA) =
          makeRuleTyper().solve(astrouts, env, rules, range, List(), Some(runesA.toSet)) match {
            case (_, rtsf @ RuleTyperSolveFailure(_, _, _, _)) => vfail(rtsf.toString)
            case (c, RuleTyperSolveSuccess(r)) => (c, r)
          }

        val sizeAE = translateExpression(env, astrouts, sizeSE)
        val callableAE = translateExpression(env, astrouts, callableSE)

        RuntimeArrayFromCallableAE(range, rulesA, conclusions.typeByRune, maybeMutabilityRuneA, maybeVariabilityRuneA, sizeAE, callableAE)
      }
      case RepeaterPackSE(range, exprS) => {
        val elementsA = translateExpression(env, astrouts, exprS)
        RepeaterPackAE(range, elementsA)
      }
      case RepeaterPackIteratorSE(range, exprS) => {
        val exprA = translateExpression(env, astrouts, exprS)
        RepeaterPackIteratorAE(range, exprA)
      }
      case RuneLookupSE(range, runeS) => {
        val runeA = Astronomer.translateRune(runeS)
        val tyype = env.lookupRune(runeA)
        RuneLookupAE(range, runeA, tyype)
      }
      case IntLiteralSE(range, value) => IntLiteralAE(range, value)
      case BoolLiteralSE(range, value) => BoolLiteralAE(range, value)
      case StrLiteralSE(range, value) => StrLiteralAE(range, value)
      case FloatLiteralSE(range, value) => FloatLiteralAE(range, value)
      case FunctionSE(functionS) => {
        val functionA = Astronomer.translateFunction(astrouts, env, functionS)
        val lambdaName = functionA.name match { case n @ LambdaNameA(_) => n }
        FunctionAE(lambdaName, functionA)
      }
      case DotSE(range, leftS, member, borrowContainer) => {
        val leftA = translateExpression(env, astrouts, leftS)
        DotAE(range, leftA, member, borrowContainer)
      }
      case DotCallSE(range, leftS, indexExprS) => {
        val leftA = translateExpression(env, astrouts, leftS)
        val indexExprA = translateExpression(env, astrouts, indexExprS)
        DotCallAE(range, leftA, indexExprA)
      }
      case FunctionCallSE(rangeS, callableExprS, argsExprsS) => {
        val callableExprA = translateExpression(env, astrouts, callableExprS)
        val argsExprsA = argsExprsS.map(translateExpression(env, astrouts, _))
        FunctionCallAE(rangeS, callableExprA, argsExprsA)
      }
      case LocalLoadSE(range, name, targetOwnership) => {
        LocalLoadAE(range, Astronomer.translateVarNameStep(name), targetOwnership)
      }
      case OutsideLoadSE(range, name, None, targetOwnership) => {
        OutsideLoadAE(range, name, targetOwnership)
      }
      case OutsideLoadSE(range, name, Some(templateArgsS), targetOwnership) => {
        // We don't translate the templexes, we can't until we know what the template expects.
        TemplateSpecifiedLookupAE(range, name, templateArgsS, targetOwnership)
      }
      case UnletSE(range, name) => UnletAE(range, name)
    }
  }

  def makeRuleTyper(): RuleTyperEvaluator[Environment, AstroutsBox] = {
    new RuleTyperEvaluator[Environment, AstroutsBox](
      new IRuleTyperEvaluatorDelegate[Environment, AstroutsBox] {
        override def lookupType(state: AstroutsBox, env: Environment, range: RangeS, name: CodeTypeNameS): (ITemplataType) = {
          Astronomer.lookupType(state, env, range, name)
        }

        override def lookupType(state: AstroutsBox, env: Environment, range: RangeS, name: INameS): ITemplataType = {
          Astronomer.lookupType(state, env, range, name)
        }
      })
  }
}
