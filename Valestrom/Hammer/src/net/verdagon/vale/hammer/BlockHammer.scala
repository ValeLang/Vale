package net.verdagon.vale.hammer

import net.verdagon.vale.hammer.ExpressionHammer.translateDeferreds
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.metal._
import net.verdagon.vale.templar.BlockTE
import net.verdagon.vale.templar.templata.FunctionHeaderT
import net.verdagon.vale.{vassert, vfail}

object BlockHammer {
  def translateBlock(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    parentLocals: LocalsBox,
    block2: BlockTE):
  (BlockH) = {
    val blockLocals = LocalsBox(parentLocals.snapshot)

    val unfilteredExprsH =
      ExpressionHammer.translateExpressionsAndDeferreds(
        hinputs, hamuts, currentFunctionHeader, blockLocals, block2.exprs);

    val indexOfFirstNever = unfilteredExprsH.indexWhere(_.resultType.kind == NeverH())
    // If there's an expression returning a Never, then remove all the expressions after that.
    val exprsH =
      if (indexOfFirstNever >= 0) {
        unfilteredExprsH.slice(0, indexOfFirstNever + 1)
      } else {
        unfilteredExprsH
      }
    vassert(exprsH.nonEmpty)

    val exprH =
      exprsH match {
        case List(onlyExprH) => onlyExprH
        case zeroOrOneExprs => ConsecutorH(zeroOrOneExprs)
      }

    // We dont vassert(deferreds.isEmpty) here, see BMHD for why.

    val localIdsInThisBlock = blockLocals.locals.keys.toSet.diff(parentLocals.locals.keys.toSet)
    val localsInThisBlock = localIdsInThisBlock.map(blockLocals.locals)
    val unstackifiedLocalIdsInThisBlock = blockLocals.unstackifiedVars.intersect(localIdsInThisBlock)

    if (localIdsInThisBlock != unstackifiedLocalIdsInThisBlock) {
      // This probably means that there was no UnletH or DestructureH for that variable.
      vfail("Ununstackified local: " + (localIdsInThisBlock -- unstackifiedLocalIdsInThisBlock))
    }

    // All the parent locals...
    val unstackifiedLocalsFromParent =
      parentLocals.locals.keySet
        // ...minus the ones that were unstackified before...
        .diff(parentLocals.unstackifiedVars)
        // ...which were unstackified by the block.
        .intersect(blockLocals.unstackifiedVars)
    unstackifiedLocalsFromParent.foreach(parentLocals.markUnstackified)
    parentLocals.setNextLocalIdNumber(blockLocals.nextLocalIdNumber)

    val resultType = exprH.resultType
//    start here, we're returning locals and thats not optimal
    BlockH(exprH)
  }
}
