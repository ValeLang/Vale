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
    initialLocals: Locals,
    block2: Block2):
  (BlockH) = {
    val locals = LocalsBox(initialLocals)

    val (exprsWithoutDeferredsH, deferreds) =
      ExpressionHammer.translateExpressions(
        hinputs, hamuts, locals, block2.exprs);

    // We dont vassert(deferreds.isEmpty) here, see BMHD for why.

    val exprsWithDeferredsH =
      translateDeferreds(hinputs, hamuts, locals, ExpressionHammer.flattenAndMakeBlock(exprsWithoutDeferredsH), deferreds)


    val localIdsInThisBlock = locals.locals.keys.toSet.diff(initialLocals.locals.keys.toSet)
    val localsInThisBlock = localIdsInThisBlock.map(locals.locals)
    val unstackifiedLocalIdsInThisBlock = locals.unstackifiedVars.intersect(localIdsInThisBlock)

//    if (localIdsInThisBlock != unstackifiedLocalIdsInThisBlock) {
//      // This probably means that there was no UnletH or DestructureH for that variable.
//      vfail("Ununstackified local: " + (localIdsInThisBlock -- unstackifiedLocalIdsInThisBlock))
//    }

    val resultType = exprsWithDeferredsH.resultType
//    start here, we're returning locals and thats not optimal
    println("debt: put checking back in for unstackified things!")
    ExpressionHammer.flattenAndMakeBlock(List(exprsWithDeferredsH))
  }
}
