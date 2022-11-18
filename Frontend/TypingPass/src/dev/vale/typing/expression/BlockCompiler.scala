package dev.vale.typing.expression

//import dev.vale.astronomer.{BlockSE, IExpressionSE}
import dev.vale.RangeS
import dev.vale.postparsing._
import dev.vale.typing.{TypingPassOptions, CompilerOutputs}
import dev.vale.typing.ast.{BlockTE, LocationInFunctionEnvironment, ReferenceExpressionTE}
import dev.vale.typing.env.{FunctionEnvironmentBox, NodeEnvironment, NodeEnvironmentBox}
import dev.vale.typing.function.DestructorCompiler
import dev.vale.typing.names.{FullNameT, IVarNameT}
import dev.vale.typing.types.CoordT
import dev.vale.postparsing.ExpressionScout
import dev.vale.typing.{ast, _}
import dev.vale.typing.ast._
import dev.vale.typing.env._
import dev.vale.typing.names.TypingPassBlockResultVarNameT
import dev.vale.typing.types._
import dev.vale.RangeS

import scala.collection.immutable.{List, Set}

trait IBlockCompilerDelegate {
  def evaluateAndCoerceToReferenceExpression(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    parentRanges: List[RangeS],
    expr1: IExpressionSE):
  (ReferenceExpressionTE, Set[CoordT])

  def dropSince(
    coutputs: CompilerOutputs,
    startingNenv: NodeEnvironment,
    nenv: NodeEnvironmentBox,
    range: List[RangeS],
    life: LocationInFunctionEnvironment,
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
  // - Num anonymous variables that were made
  // - Types of all returns from inside the block
  def evaluateBlock(
    parentFate: FunctionEnvironmentBox,
    coutputs: CompilerOutputs,
    life: LocationInFunctionEnvironment,
    parentRanges: List[RangeS],
    block1: BlockSE):
  (BlockTE, Set[FullNameT[IVarNameT]], Set[CoordT]) = {
    val nenv = NodeEnvironmentBox(parentFate.makeChildNodeEnvironment(block1, life))
    val startingNenv = nenv.snapshot

    val (expressionsWithResult, returnsFromExprs) =
      evaluateBlockStatements(
        coutputs, startingNenv, nenv, parentRanges, life, block1)

    val block2 = BlockTE(expressionsWithResult)

    val (unstackifiedAncestorLocals) = nenv.snapshot.getEffectsSince(startingNenv)
    (block2, unstackifiedAncestorLocals, returnsFromExprs)
  }


  def evaluateBlockStatements(
    coutputs: CompilerOutputs,
    startingNenv: NodeEnvironment,
    nenv: NodeEnvironmentBox,
    parentRanges: List[RangeS],
    life: LocationInFunctionEnvironment,
    blockSE: BlockSE):
  (ReferenceExpressionTE, Set[CoordT]) = {
    val (unneveredUnresultifiedUndestructedRootExpression, returnsFromExprs) =
      delegate.evaluateAndCoerceToReferenceExpression(coutputs, nenv, life + 0, parentRanges, blockSE.expr);

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
            coutputs, startingNenv, nenv, RangeS(blockSE.range.end, blockSE.range.end) :: parentRanges, life, unresultifiedUndestructedExpressions)

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
