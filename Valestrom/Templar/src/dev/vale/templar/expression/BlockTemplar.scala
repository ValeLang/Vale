package dev.vale.templar.expression

//import dev.vale.astronomer.{BlockSE, IExpressionSE}
import dev.vale.RangeS
import dev.vale.scout.{BlockSE, IExpressionSE}
import dev.vale.templar.{TemplarOptions, Temputs}
import dev.vale.templar.ast.{BlockTE, LocationInFunctionEnvironment, ReferenceExpressionTE}
import dev.vale.templar.env.{FunctionEnvironmentBox, NodeEnvironment, NodeEnvironmentBox}
import dev.vale.templar.function.DestructorTemplar
import dev.vale.templar.names.{FullNameT, IVarNameT}
import dev.vale.templar.types.CoordT
import dev.vale.scout.ExpressionScout
import dev.vale.templar.{ast, _}
import dev.vale.templar.ast._
import dev.vale.templar.env._
import dev.vale.templar.names.TemplarBlockResultVarNameT
import dev.vale.templar.types._
import dev.vale.RangeS

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
    range: RangeS,
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
            temputs, startingNenv, nenv, RangeS(blockSE.range.end, blockSE.range.end), life, unresultifiedUndestructedExpressions)

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
