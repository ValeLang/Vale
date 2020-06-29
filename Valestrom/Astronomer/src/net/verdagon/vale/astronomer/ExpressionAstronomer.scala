package net.verdagon.vale.astronomer

import net.verdagon.vale.astronomer.ruletyper.{RuleTyperSolveFailure, RuleTyperSolveSuccess}
import net.verdagon.vale.scout.{IEnvironment => _, FunctionEnvironment => _, Environment => _, _}
import net.verdagon.vale.vfail

object ExpressionAstronomer {
  def translateBlock(env: Environment, astrouts: AstroutsBox, blockS: BlockSE): BlockAE = {
    val BlockSE(locals, exprsS) = blockS
    val exprsA = exprsS.map(translateExpression(env, astrouts, _))
    BlockAE(locals.map(translateLocalVariable), exprsA)
  }

  def translateLocalVariable(varS: LocalVariable1): LocalVariableA = {
    val LocalVariable1(varNameS, variability, selfBorrowed, selfMoved, selfMutated, childBorrowed, childMoved, childMutated) = varS
    val varNameA = Astronomer.translateVarNameStep(varNameS)
    LocalVariableA(varNameA, variability, selfBorrowed, selfMoved, selfMutated, childBorrowed, childMoved, childMutated)
  }

  def translateExpression(env: Environment, astrouts: AstroutsBox, iexprS: IExpressionSE): IExpressionAE = {
    iexprS match {
      case LetSE(rules, allRunesS, localRunesS, patternS, expr) => {
        val allRunesA = allRunesS.map(Astronomer.translateRune)
        val localRunesA = localRunesS.map(Astronomer.translateRune)
        val (conclusions, rulesA) =
          Astronomer.makeRuleTyper().solve(astrouts, env, rules, List(patternS), Some(allRunesA)) match {
            case (_, rtsf @ RuleTyperSolveFailure(_, _, _)) => vfail(rtsf.toString)
            case (c, RuleTyperSolveSuccess(r)) => (c, r)
          }
        val exprA = translateExpression(env, astrouts, expr)

        val patternA = Astronomer.translateAtom(patternS)

        LetAE(
          rulesA,
          conclusions.typeByRune,
          localRunesA,
          patternA,
          exprA)
      }
      case IfSE(conditionS, thenBodyS, elseBodyS) => {
        val conditionA = translateBlock(env, astrouts, conditionS)
        val thenBodyA = translateBlock(env, astrouts, thenBodyS)
        val elseBodyA = translateBlock(env, astrouts, elseBodyS)
        IfAE(conditionA, thenBodyA, elseBodyA)
      }
      case WhileSE(conditionS, bodyS) => {
        val conditionA = translateBlock(env, astrouts, conditionS)
        val bodyA = translateBlock(env, astrouts, bodyS)
        WhileAE(conditionA, bodyA)
      }
      case DestructSE(innerS) => {
        val exprA = translateExpression(env, astrouts, innerS)
        DestructAE(exprA)
      }
      case ExprMutateSE(mutateeS, exprS) => {
        val conditionA = translateExpression(env, astrouts, mutateeS)
        val bodyA = translateExpression(env, astrouts, exprS)
        ExprMutateAE(conditionA, bodyA)
      }
      case GlobalMutateSE(name, exprS) => {
        val exprA = translateExpression(env, astrouts, exprS)
        GlobalMutateAE(Astronomer.translateImpreciseName(name), exprA)
      }
      case LocalMutateSE(nameS, exprS) => {
        val exprA = translateExpression(env, astrouts, exprS)
        LocalMutateAE(Astronomer.translateVarNameStep(nameS), exprA)
      }
      case ExpressionLendSE(innerExprS) => {
        val innerExprA = translateExpression(env, astrouts, innerExprS)
        ExpressionLendAE(innerExprA)
      }
      case ReturnSE(innerExprS) => {
        val innerExprA = translateExpression(env, astrouts, innerExprS)
        (ReturnAE(innerExprA))
      }
      case blockS @ BlockSE(_, _) => translateBlock(env, astrouts, blockS)
      case ArgLookupSE(index) => (ArgLookupAE(index))
      case CheckRefCountSE(refExprS, category, numExprS) => {
        val refExprA = translateExpression(env, astrouts, refExprS)
        val numExprA = translateExpression(env, astrouts, numExprS)
        (CheckRefCountAE(refExprA, category, numExprA))
      }
      case RepeaterBlockSE(exprS) => {
        val exprA = translateExpression(env, astrouts, exprS)
        (RepeaterBlockAE(exprA))
      }
      case RepeaterBlockIteratorSE(exprS) => {
        val exprA = translateExpression(env, astrouts, exprS)
        (RepeaterBlockIteratorAE(exprA))
      }
      case packS @ PackSE(_) => translatePack(astrouts, env, packS)
      case VoidSE() => VoidAE()
      case SequenceESE(elementsS) => {
        val elementsA = elementsS.map(translateExpression(env, astrouts, _))
        SequenceEAE(elementsA)
      }
      case RepeaterPackSE(exprS) => {
        val elementsA = translateExpression(env, astrouts, exprS)
        RepeaterPackAE(elementsA)
      }
      case RepeaterPackIteratorSE(exprS) => {
        val exprA = translateExpression(env, astrouts, exprS)
        RepeaterPackIteratorAE(exprA)
      }
      case RuneLookupSE(runeS) => {
        val runeA = Astronomer.translateRune(runeS)
        val tyype = env.lookupRune(runeA)
        RuneLookupAE(runeA, tyype)
      }
      case IntLiteralSE(value) => IntLiteralAE(value)
      case BoolLiteralSE(value) => BoolLiteralAE(value)
      case StrLiteralSE(value) => StrLiteralAE(value)
      case FloatLiteralSE(value) => FloatLiteralAE(value)
      case FunctionSE(functionS) => {
        val functionA = Astronomer.translateFunction(astrouts, env, functionS)
        val lambdaName = functionA.name match { case n @ LambdaNameA(_) => n }
        FunctionAE(lambdaName, functionA)
      }
      case DotSE(leftS, member, borrowContainer) => {
        val leftA = translateExpression(env, astrouts, leftS)
        DotAE(leftA, member, borrowContainer)
      }
      case DotCallSE(leftS, indexExprS) => {
        val leftA = translateExpression(env, astrouts, leftS)
        val indexExprA = translateExpression(env, astrouts, indexExprS)
        DotCallAE(leftA, indexExprA)
      }
      case FunctionCallSE(callableExprS, argsExprsS) => {
        val callableExprA = translateExpression(env, astrouts, callableExprS)
        val argsExprsA = argsExprsS.map(translateExpression(env, astrouts, _))
        FunctionCallAE(callableExprA, argsExprsA)
      }
      case TemplateSpecifiedLookupSE(name, templateArgsS) => {
        // We don't translate the templexes, we can't until we know what the template expects.
        TemplateSpecifiedLookupAE(name, templateArgsS)
      }
      case LocalLoadSE(name, borrow) => {
        LocalLoadAE(Astronomer.translateVarNameStep(name), borrow)
      }
      case FunctionLoadSE(name) => {
        FunctionLoadAE(Astronomer.translateGlobalFunctionFamilyName(name))
      }
      case UnletSE(name) => UnletAE(name)
    }
  }

  private def translatePack(astrouts: AstroutsBox, env: Environment, packS: PackSE):
  PackAE = {
    val elementsA = packS.elements.map(translateExpression(env, astrouts, _))
    PackAE(elementsA)
  }
}
