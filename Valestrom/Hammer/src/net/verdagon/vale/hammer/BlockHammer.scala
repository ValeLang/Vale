package net.verdagon.vale.hammer

import net.verdagon.vale.hammer.ExpressionHammer.translateDeferreds
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.metal._
import net.verdagon.vale.templar.Block2
import net.verdagon.vale.{vassert, vfail}

object BlockHammer {
  def translateBlock(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    parentLocals: LocalsBox,
    block2: Block2):
  (BlockH) = {
    val blockLocals = LocalsBox(parentLocals.snapshot)

    val (exprsWithoutDeferredsH, deferreds) =
      ExpressionHammer.translateExpressions(
        hinputs, hamuts, blockLocals, block2.exprs);

    // We dont vassert(deferreds.isEmpty) here, see BMHD for why.

    val exprsWithDeferredsH =
      translateDeferreds(hinputs, hamuts, blockLocals, BlockH(ConsecutorH(exprsWithoutDeferredsH)), deferreds)


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

    val resultType = exprsWithDeferredsH.resultType
//    start here, we're returning locals and thats not optimal
    BlockH(ConsecutorH(List(exprsWithDeferredsH)))
  }
}
