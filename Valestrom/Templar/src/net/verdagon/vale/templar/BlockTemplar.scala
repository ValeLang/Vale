package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.{BlockAE, IExpressionAE}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser.FinalP
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.{vassert, vcurious}

import scala.collection.immutable.{List, Set}

trait IBlockTemplarDelegate {
  def evaluateAndCoerceToReferenceExpression(
    temputs: TemputsBox,
    fate: FunctionEnvironmentBox,
    expr1: IExpressionAE):
  (ReferenceExpression2, Set[Coord])
}

class BlockTemplar(
    opts: TemplarOptions,
    destructorTemplar: DestructorTemplar,
    localHelper: LocalHelper,
    delegate: IBlockTemplarDelegate) {
  // This is NOT USED FOR EVERY BLOCK!
  // This is just for the simplest kind of block.
  // This can serve as an example for how we can use together all the tools provided by BlockTemplar.
  def evaluateBlock(
    parentFate: FunctionEnvironmentBox,
    temputs: TemputsBox,
    block1: BlockAE):
  (Block2, Set[Coord]) = {
    val newCounter = 1
    val fate =
      FunctionEnvironmentBox(
        FunctionEnvironment(
          parentFate.snapshot, parentFate.fullName, parentFate.function, Map(), parentFate.maybeReturnType, List(), newCounter, List(), Set()))
    val startingFate = fate.snapshot

    fate.addScoutedLocals(block1.locals)

    val (expressionsWithResult, returnsFromExprs) =
      evaluateBlockStatements(temputs, startingFate, fate, block1.exprs)

    val block2 = Block2(expressionsWithResult)

    // We don't just use the old parentFate because this one might have had moveds added to it.
    val newParentFate @ FunctionEnvironment(_, _, _, _, _, _, _, _, _) = fate.parentEnv
    parentFate.functionEnvironment = newParentFate

    (block2, returnsFromExprs)
  }

  def evaluateBlockStatements(
    temputs: TemputsBox,
    startingFate: FunctionEnvironment,
    fate: FunctionEnvironmentBox,
    exprs: List[IExpressionAE]):
  (List[ReferenceExpression2], Set[Coord]) = {
    val (unneveredUnresultifiedUndestructedExpressions, returnsFromExprs) =
      evaluateBlockStatementsInner(temputs, fate, exprs);

    val unreversedVariablesToDestruct = getUnmovedVariablesIntroducedSince(startingFate, fate)

    val unresultifiedUndestructedExpressions =
      if (unneveredUnresultifiedUndestructedExpressions.exists(_.referend == Never2()) &&
          unneveredUnresultifiedUndestructedExpressions.last.referend != Never2()) {
        unneveredUnresultifiedUndestructedExpressions :+ UnreachableMootE2(VoidLiteral2())
      } else {
        unneveredUnresultifiedUndestructedExpressions
      }

    val newExpressionsList =
      if (unreversedVariablesToDestruct.isEmpty) {
        unresultifiedUndestructedExpressions
      } else if (unresultifiedUndestructedExpressions.last.referend == Never2()) {
        val moots = mootAll(temputs, fate, unreversedVariablesToDestruct)
        unresultifiedUndestructedExpressions ++ moots
      } else {
        val (resultifiedExpressions, resultLocalVariable) =
          resultifyExpressions(fate, unresultifiedUndestructedExpressions)

        val reversedVariablesToDestruct = unreversedVariablesToDestruct.reverse
        // Dealiasing should be done by hammer. But destructors are done here
        val destroyExpressions = localHelper.unletAll(temputs, fate, reversedVariablesToDestruct)

        (resultifiedExpressions ++ destroyExpressions) :+ localHelper.unletLocal(fate, resultLocalVariable)
      }

    (newExpressionsList, returnsFromExprs)
  }

  def getUnmovedVariablesIntroducedSince(
    sinceFate: FunctionEnvironment,
    currentFate: FunctionEnvironmentBox):
  List[ILocalVariable2] = {
    val localsAsOfThen =
      sinceFate.variables.collect({
        case x @ ReferenceLocalVariable2(_, _, _) => x
        case x @ AddressibleLocalVariable2(_, _, _) => x
      })
    val localsAsOfNow =
      currentFate.variables.collect({
        case x @ ReferenceLocalVariable2(_, _, _) => x
        case x @ AddressibleLocalVariable2(_, _, _) => x
      })

    vassert(localsAsOfNow.startsWith(localsAsOfThen))
    val localsDeclaredSinceThen = localsAsOfNow.slice(localsAsOfThen.size, localsAsOfNow.size)
    vassert(localsDeclaredSinceThen.size == localsAsOfNow.size - localsAsOfThen.size)

    val unmovedLocalsDeclaredSinceThen =
      localsDeclaredSinceThen.filter(x => !currentFate.moveds.contains(x.id))

    unmovedLocalsDeclaredSinceThen
  }

  // Makes the last expression stored in a variable.
  // Dont call this for void or never or no expressions.
  // Maybe someday we can do this even for Never and Void, for consistency and so
  // we dont have any special casing.
  def resultifyExpressions(fate: FunctionEnvironmentBox, exprs: List[ReferenceExpression2]):
  (List[ReferenceExpression2], ReferenceLocalVariable2) = {
    vassert(exprs.nonEmpty)
    val lastExpr = exprs.last
    val resultVarNum = fate.nextVarCounter()
    val resultVarId = fate.fullName.addStep(TemplarBlockResultVarName2(resultVarNum))
    val resultVariable = ReferenceLocalVariable2(resultVarId, Final, lastExpr.resultRegister.reference)
    val resultLet = LetNormal2(resultVariable, lastExpr)
    fate.addVariable(resultVariable)
    (exprs.init :+ resultLet, resultVariable)
  }

  private def evaluateBlockStatementsInner(
    temputs: TemputsBox,
    fate: FunctionEnvironmentBox,
    expr1: List[IExpressionAE]):
  (List[ReferenceExpression2], Set[Coord]) = {
    expr1 match {
      case Nil => (List(), Set())
      case first1 :: rest1 => {
        val (perhapsUndestructedFirstExpr2, returnsFromFirst) =
          delegate.evaluateAndCoerceToReferenceExpression(
            temputs, fate, first1);

        val destructedFirstExpr2 =
          if (rest1.isEmpty) {
            // This is the last expression, which means it's getting returned,
            // so don't destruct it.
            (perhapsUndestructedFirstExpr2) // Do nothing
          } else {
            // This isn't the last expression
            perhapsUndestructedFirstExpr2.resultRegister.referend match {
              case Void2() => perhapsUndestructedFirstExpr2
              case _ => destructorTemplar.drop(fate, temputs, perhapsUndestructedFirstExpr2)
            }
          }

        val (restExprs2, returnsFromRest) =
          evaluateBlockStatementsInner(temputs, fate, rest1)

        (destructedFirstExpr2 +: restExprs2, returnsFromFirst ++ returnsFromRest)
      }
    }
  }

  def mootAll(
    temputs: TemputsBox,
    fate: FunctionEnvironmentBox,
    variables: List[ILocalVariable2]):
  (List[ReferenceExpression2]) = {
    variables match {
      case Nil => (List())
      case head :: tail => {
        val unlet = UnreachableMootE2(localHelper.unletLocal(fate, head))
        val tailExprs2 = mootAll(temputs, fate, tail)
        (unlet :: tailExprs2)
      }
    }
  }
}
