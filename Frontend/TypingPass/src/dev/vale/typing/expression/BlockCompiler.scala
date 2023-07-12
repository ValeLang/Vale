package dev.vale.typing.expression

//import dev.vale.astronomer.{BlockSE, IExpressionSE}
import dev.vale.RangeS
import dev.vale.postparsing._
import dev.vale.typing._
import dev.vale.typing.ast.{BlockTE, LocationInFunctionEnvironmentT, ReferenceExpressionTE}
import dev.vale.typing.env._
import dev.vale.typing.function.DestructorCompiler
import dev.vale.typing.names._
import dev.vale.typing.types.CoordT
import dev.vale.postparsing.ExpressionScout
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.env._
import dev.vale.typing.types._
import dev.vale.RangeS
import dev.vale.typing.templata._

import scala.collection.immutable.{List, Set}

trait IBlockCompilerDelegate {
  def evaluateAndCoerceToReferenceExpression(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironmentT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    region: RegionT,
    expr1: IExpressionSE):
  (ReferenceExpressionTE, Set[CoordT])

  def dropSince(
    coutputs: CompilerOutputs,
    startingNenv: NodeEnvironmentT,
    nenv: NodeEnvironmentBox,
    range: List[RangeS],
    callLocation: LocationInDenizen,
    life: LocationInFunctionEnvironmentT,
    region: RegionT,
    unresultifiedUndestructedExpressions: ReferenceExpressionTE):
  ReferenceExpressionTE
}

class BlockCompiler(
    opts: TypingPassOptions,

    destructorCompiler: DestructorCompiler,
    localHelper: LocalHelper,
    delegate: IBlockCompilerDelegate) {

  // This is NOT USED FOR EVERY BLOCK!
  // This is just for the simplest kind of block.
  // This can serve as an example for how we can use together all the tools provided by BlockCompiler.
  // Returns:
  // - The resulting block expression
  // - All locals from outside the block that we unstackified inside the block
  // - All locals from outside the block that we restackified inside the block
  // - Num anonymous variables that were made
  // - Types of all returns from inside the block
  def evaluateBlock(
    parentFate: FunctionEnvironmentBoxT,
    coutputs: CompilerOutputs,
    life: LocationInFunctionEnvironmentT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    region: RegionT,
    block1: BlockSE):
  (BlockTE, Set[IVarNameT], Set[IVarNameT], Set[CoordT]) = {
    val nenv = NodeEnvironmentBox(parentFate.makeChildNodeEnvironment(block1, life))
    val startingNenv = nenv.snapshot

    val (expressionsWithResult, returnsFromExprs) =
      evaluateBlockStatements(
        coutputs, startingNenv, nenv, parentRanges, callLocation, life, region, block1)

    val block2 = BlockTE(expressionsWithResult)

    val (unstackifiedAncestorLocals, restackifiedAncestorLocals) = nenv.snapshot.getEffectsSince(startingNenv)
    (block2, unstackifiedAncestorLocals, restackifiedAncestorLocals, returnsFromExprs)
  }


  def evaluateBlockStatements(
    coutputs: CompilerOutputs,
    startingNenv: NodeEnvironmentT,
    nenv: NodeEnvironmentBox,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    life: LocationInFunctionEnvironmentT,
    region: RegionT,
    blockSE: BlockSE):
  (ReferenceExpressionTE, Set[CoordT]) = {
    val (unneveredUnresultifiedUndestructedRootExpression, returnsFromExprs) =
      delegate.evaluateAndCoerceToReferenceExpression(
        coutputs, nenv, life + 0, parentRanges,
        callLocation, region, blockSE.expr);

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
        coutputs,
        startingNenv,
        nenv,
        RangeS(blockSE.range.end, blockSE.range.end) :: parentRanges,
        callLocation,
        life,
        region,
        unresultifiedUndestructedExpressions)

    (newExpr, returnsFromExprs)
  }

//  private def evaluateBlockStatementsInner(
//    coutputs: CompilerOutputs,
//    nenv: FunctionEnvironmentBox,
//    life: LocationInFunctionEnvironment,
//    expr1: List[IExpressionSE]):
//  (List[ReferenceExpressionTE], Set[CoordT]) = {
//    expr1 match {
//      case Nil => (Nil, Set())
//      case first1 :: rest1 => {
//        val (perhapsUndestructedFirstExpr2, returnsFromFirst) =
//          delegate.evaluateAndCoerceToReferenceExpression(
//            coutputs, nenv, life + 0, first1);
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
//              case _ => destructorCompiler.drop(nenv, coutputs, perhapsUndestructedFirstExpr2)
//            }
//          }
//
//        val (restExprs2, returnsFromRest) =
//          evaluateBlockStatementsInner(coutputs, nenv, life + 1, rest1)
//
//        (destructedFirstExpr2 +: restExprs2, returnsFromFirst ++ returnsFromRest)
//      }
//    }
//  }

}
