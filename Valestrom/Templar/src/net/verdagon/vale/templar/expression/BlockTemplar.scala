package net.verdagon.vale.templar.expression

import net.verdagon.vale.astronomer.{BlockAE, IExpressionAE}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.templar.types._
import net.verdagon.vale.vassert

import scala.collection.immutable.{List, Set}

trait IBlockTemplarDelegate {
  def evaluateAndCoerceToReferenceExpression(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    expr1: IExpressionAE):
  (ReferenceExpressionTE, Set[CoordT])
}

class BlockTemplar(
    opts: TemplarOptions,
    newTemplataStore: () => TemplatasStore,
    destructorTemplar: DestructorTemplar,
    localHelper: LocalHelper,
    delegate: IBlockTemplarDelegate) {
  // This is NOT USED FOR EVERY BLOCK!
  // This is just for the simplest kind of block.
  // This can serve as an example for how we can use together all the tools provided by BlockTemplar.
  // Returns:
  // - The resulting block expression
  // - All locals from outside the block that we unstackified inside the block
  // - Num anonymous variables that were made
  // - Types of all returns from inside the block
  def evaluateBlock(
    parentFate: FunctionEnvironmentBox,
    temputs: Temputs,
    block1: BlockAE):
  (BlockTE, Set[FullNameT[IVarNameT]], Int, Set[CoordT]) = {
    val fate = parentFate.makeChildEnvironment(newTemplataStore)
    val startingFate = fate.snapshot

    val (expressionsWithResult, returnsFromExprs) =
      evaluateBlockStatements(temputs, startingFate, fate, block1.exprs)

    val block2 = BlockTE(expressionsWithResult)

    val (unstackifiedAncestorLocals, varCountersUsed) = fate.getEffects()
    (block2, unstackifiedAncestorLocals, varCountersUsed, returnsFromExprs)
  }

  def evaluateBlockStatements(
    temputs: Temputs,
    startingFate: FunctionEnvironment,
    fate: FunctionEnvironmentBox,
    exprs: List[IExpressionAE]):
  (List[ReferenceExpressionTE], Set[CoordT]) = {
    val (unneveredUnresultifiedUndestructedExpressions, returnsFromExprs) =
      evaluateBlockStatementsInner(temputs, fate, exprs);

    val unreversedVariablesToDestruct = getUnmovedVariablesIntroducedSince(startingFate, fate)

    val unresultifiedUndestructedExpressions =
      if (unneveredUnresultifiedUndestructedExpressions.exists(_.kind == NeverT()) &&
          unneveredUnresultifiedUndestructedExpressions.last.kind != NeverT()) {
        unneveredUnresultifiedUndestructedExpressions :+ UnreachableMootTE(VoidLiteralTE())
      } else {
        unneveredUnresultifiedUndestructedExpressions
      }

    val newExpressionsList =
      if (unreversedVariablesToDestruct.isEmpty) {
        unresultifiedUndestructedExpressions
      } else if (unresultifiedUndestructedExpressions.last.kind == NeverT()) {
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
  List[ILocalVariableT] = {
    val localsAsOfThen =
      sinceFate.locals.collect({
        case x @ ReferenceLocalVariableT(_, _, _) => x
        case x @ AddressibleLocalVariableT(_, _, _) => x
      })
    val localsAsOfNow =
      currentFate.locals.collect({
        case x @ ReferenceLocalVariableT(_, _, _) => x
        case x @ AddressibleLocalVariableT(_, _, _) => x
      })

    vassert(localsAsOfNow.startsWith(localsAsOfThen))
    val localsDeclaredSinceThen = localsAsOfNow.slice(localsAsOfThen.size, localsAsOfNow.size)
    vassert(localsDeclaredSinceThen.size == localsAsOfNow.size - localsAsOfThen.size)

    val unmovedLocalsDeclaredSinceThen =
      localsDeclaredSinceThen.filter(x => !currentFate.unstackifieds.contains(x.id))

    unmovedLocalsDeclaredSinceThen
  }

  // Makes the last expression stored in a variable.
  // Dont call this for void or never or no expressions.
  // Maybe someday we can do this even for Never and Void, for consistency and so
  // we dont have any special casing.
  def resultifyExpressions(fate: FunctionEnvironmentBox, exprs: List[ReferenceExpressionTE]):
  (List[ReferenceExpressionTE], ReferenceLocalVariableT) = {
    vassert(exprs.nonEmpty)
    val lastExpr = exprs.last
    val resultVarNum = fate.nextVarCounter()
    val resultVarId = fate.fullName.addStep(TemplarBlockResultVarNameT(resultVarNum))
    val resultVariable = ReferenceLocalVariableT(resultVarId, FinalT, lastExpr.resultRegister.reference)
    val resultLet = LetNormalTE(resultVariable, lastExpr)
    fate.addVariable(resultVariable)
    (exprs.init :+ resultLet, resultVariable)
  }

  private def evaluateBlockStatementsInner(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    expr1: List[IExpressionAE]):
  (List[ReferenceExpressionTE], Set[CoordT]) = {
    expr1 match {
      case Nil => (List.empty, Set())
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
            perhapsUndestructedFirstExpr2.resultRegister.kind match {
              case VoidT() => perhapsUndestructedFirstExpr2
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
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    variables: List[ILocalVariableT]):
  (List[ReferenceExpressionTE]) = {
    variables match {
      case Nil => (List.empty)
      case head :: tail => {
        val unlet = UnreachableMootTE(localHelper.unletLocal(fate, head))
        val tailExprs2 = mootAll(temputs, fate, tail)
        (unlet :: tailExprs2)
      }
    }
  }
}
