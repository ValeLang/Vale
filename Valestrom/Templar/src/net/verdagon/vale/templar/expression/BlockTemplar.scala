package net.verdagon.vale.templar.expression

//import net.verdagon.vale.astronomer.{BlockSE, IExpressionSE}
import net.verdagon.vale.scout.{BlockSE, ExpressionScout, IExpressionSE}
import net.verdagon.vale.templar.{ast, _}
import net.verdagon.vale.templar.ast.{BlockTE, ConsecutorTE, LetNormalTE, LocationInFunctionEnvironment, ReferenceExpressionTE, UnreachableMootTE}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.templar.names.{FullNameT, IVarNameT, TemplarBlockResultVarNameT}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.vassert

import scala.collection.immutable.{List, Set}

trait IBlockTemplarDelegate {
  def evaluateAndCoerceToReferenceExpression(
    temputs: Temputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    expr1: IExpressionSE):
  (ReferenceExpressionTE, Set[CoordT])
}

class BlockTemplar(
    opts: TemplarOptions,

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
    life: LocationInFunctionEnvironment,
    block1: BlockSE):
  (BlockTE, Set[FullNameT[IVarNameT]], Set[CoordT]) = {
    val nenv = NodeEnvironmentBox(parentFate.makeChildNodeEnvironment(block1, life))
    val startingNenv = nenv.snapshot

    val (expressionsWithResult, returnsFromExprs) =
      evaluateBlockStatements(
        temputs, startingNenv, nenv, life, block1)

    val block2 = BlockTE(expressionsWithResult)

    val (unstackifiedAncestorLocals) = nenv.snapshot.getEffectsSince(startingNenv)
    (block2, unstackifiedAncestorLocals, returnsFromExprs)
  }

  def evaluateBlockStatements(
    temputs: Temputs,
    startingNenv: NodeEnvironment,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    blockSE: BlockSE):
  (ReferenceExpressionTE, Set[CoordT]) = {
    val (unneveredUnresultifiedUndestructedRootExpression, returnsFromExprs) =
      delegate.evaluateAndCoerceToReferenceExpression(temputs, nenv, life + 0, blockSE.expr);

    val unneveredUnresultifiedUndestructedExpressions =
      unneveredUnresultifiedUndestructedRootExpression match {
        case ConsecutorTE(exprs) => exprs
        case other => Vector(other)
      }

    val unreversedVariablesToDestruct = getUnmovedVariablesIntroducedSince(startingNenv, nenv)

    val unresultifiedUndestructedExpressions =
      unneveredUnresultifiedUndestructedExpressions.indexWhere(_.kind == NeverT()) match {
        case -1 => unneveredUnresultifiedUndestructedExpressions
        case indexOfFirstNever => {
          unneveredUnresultifiedUndestructedExpressions.zipWithIndex.map({ case (e, i) =>
            if (i <= indexOfFirstNever) {
              e
            } else {
              UnreachableMootTE(e)
            }
          })
        }
      }

    val newExpressionsList =
      if (unreversedVariablesToDestruct.isEmpty) {
        unresultifiedUndestructedExpressions
      } else if (unresultifiedUndestructedExpressions.last.kind == NeverT()) {
        val moots = mootAll(temputs, nenv, unreversedVariablesToDestruct)
        unresultifiedUndestructedExpressions ++ moots
      } else {
        val (resultifiedExpressions, resultLocalVariable) =
          resultifyExpressions(nenv, life + 1, unresultifiedUndestructedExpressions.toVector)

        val reversedVariablesToDestruct = unreversedVariablesToDestruct.reverse
        // Dealiasing should be done by hammer. But destructors are done here
        val destroyExpressions = localHelper.unletAll(temputs, nenv, reversedVariablesToDestruct)

        (resultifiedExpressions ++ destroyExpressions) :+ localHelper.unletLocal(nenv, resultLocalVariable)
      }

    (Templar.consecutive(newExpressionsList.toVector), returnsFromExprs)
  }

  def getUnmovedVariablesIntroducedSince(
    sinceNenv: NodeEnvironment,
    currentNenv: NodeEnvironmentBox):
  Vector[ILocalVariableT] = {
    val localsAsOfThen =
      sinceNenv.declaredLocals.collect({
        case x @ ReferenceLocalVariableT(_, _, _) => x
        case x @ AddressibleLocalVariableT(_, _, _) => x
      })
    val localsAsOfNow =
      currentNenv.declaredLocals.collect({
        case x @ ReferenceLocalVariableT(_, _, _) => x
        case x @ AddressibleLocalVariableT(_, _, _) => x
      })

    vassert(localsAsOfNow.startsWith(localsAsOfThen))
    val localsDeclaredSinceThen = localsAsOfNow.slice(localsAsOfThen.size, localsAsOfNow.size)
    vassert(localsDeclaredSinceThen.size == localsAsOfNow.size - localsAsOfThen.size)

    val unmovedLocalsDeclaredSinceThen =
      localsDeclaredSinceThen.filter(x => !currentNenv.unstackifieds.contains(x.id))

    unmovedLocalsDeclaredSinceThen
  }

  // Makes the last expression stored in a variable.
  // Dont call this for void or never or no expressions.
  // Maybe someday we can do this even for Never and Void, for consistency and so
  // we dont have any special casing.
  def resultifyExpressions(
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    exprs: Vector[ReferenceExpressionTE]):
  (Vector[ReferenceExpressionTE], ReferenceLocalVariableT) = {
    vassert(exprs.nonEmpty)
    val lastExpr = exprs.last
    val resultVarId = nenv.fullName.addStep(TemplarBlockResultVarNameT(life))
    val resultVariable = ReferenceLocalVariableT(resultVarId, FinalT, lastExpr.result.reference)
    val resultLet = LetNormalTE(resultVariable, lastExpr)
    nenv.addVariable(resultVariable)
    (exprs.init :+ resultLet, resultVariable)
  }

//  private def evaluateBlockStatementsInner(
//    temputs: Temputs,
//    nenv: FunctionEnvironmentBox,
//    life: LocationInFunctionEnvironment,
//    expr1: List[IExpressionSE]):
//  (List[ReferenceExpressionTE], Set[CoordT]) = {
//    expr1 match {
//      case Nil => (Nil, Set())
//      case first1 :: rest1 => {
//        val (perhapsUndestructedFirstExpr2, returnsFromFirst) =
//          delegate.evaluateAndCoerceToReferenceExpression(
//            temputs, nenv, life + 0, first1);
//
//        val destructedFirstExpr2 =
//          if (rest1.isEmpty) {
//            // This is the last expression, which means it's getting returned,
//            // so don't destruct it.
//            (perhapsUndestructedFirstExpr2) // Do nothing
//          } else {
//            // This isn't the last expression
//            perhapsUndestructedFirstExpr2.result.kind match {
//              case VoidT() => perhapsUndestructedFirstExpr2
//              case _ => destructorTemplar.drop(nenv, temputs, perhapsUndestructedFirstExpr2)
//            }
//          }
//
//        val (restExprs2, returnsFromRest) =
//          evaluateBlockStatementsInner(temputs, nenv, life + 1, rest1)
//
//        (destructedFirstExpr2 +: restExprs2, returnsFromFirst ++ returnsFromRest)
//      }
//    }
//  }

  def mootAll(
    temputs: Temputs,
    nenv: NodeEnvironmentBox,
    variables: Vector[ILocalVariableT]):
  (Vector[ReferenceExpressionTE]) = {
    variables.map({ case head =>
      ast.UnreachableMootTE(localHelper.unletLocal(nenv, head))
    })
  }
}
