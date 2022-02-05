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

  def dropSince(
    temputs: Temputs,
    startingNenv: NodeEnvironment,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    unresultifiedUndestructedExpressions: ReferenceExpressionTE):
  ReferenceExpressionTE
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
      unneveredUnresultifiedUndestructedRootExpression

//    val unneveredUnresultifiedUndestructedExpressions =
//      unneveredUnresultifiedUndestructedRootExpression match {
//        case ConsecutorTE(exprs) => exprs
//        case other => Vector(other)
//      }

    val unresultifiedUndestructedExpressions =
      unneveredUnresultifiedUndestructedExpressions
//    val unresultifiedUndestructedExpressions =
//      unneveredUnresultifiedUndestructedExpressions.indexWhere(_.kind == NeverT()) match {
//        case -1 => unneveredUnresultifiedUndestructedExpressions
//        case indexOfFirstNever => {
//          unneveredUnresultifiedUndestructedExpressions.zipWithIndex.map({ case (e, i) =>
//            if (i <= indexOfFirstNever) {
//              e
//            } else {
//              UnreachableMootTE(e)
//            }
//          })
//        }
//      }

    val newExpr =
      delegate.dropSince(
        temputs, startingNenv, nenv, life, unresultifiedUndestructedExpressions)

    (newExpr, returnsFromExprs)
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

}
