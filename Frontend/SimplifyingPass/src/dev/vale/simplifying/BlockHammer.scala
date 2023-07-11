package dev.vale.simplifying

import dev.vale.finalast.{BlockH, ImmutabilifyH, ImmutableBorrowH, ImmutableShareH, MutabilifyH, MutableBorrowH, MutableShareH, NeverHT}
import dev.vale.instantiating.ast._
import dev.vale.{vassert, vcurious, vfail, vimpl, vwat, finalast => m}

class BlockHammer(expressionHammer: ExpressionHammer, typeHammer: TypeHammer) {
  def translateBlock(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    parentLocals: LocalsBox,
    block2: BlockIE):
  (BlockH) = {
    val blockLocals = LocalsBox(parentLocals.snapshot)

    val exprH =
      expressionHammer.translateExpressionsAndDeferreds(
        hinputs, hamuts, currentFunctionHeader, blockLocals, Vector(block2.inner));

    // We dont vassert(deferreds.isEmpty) here, see BMHD for why.

    val localIdsInThisBlock = blockLocals.locals.keys.toSet.diff(parentLocals.locals.keys.toSet)
//    val localsInThisBlock = localIdsInThisBlock.map(blockLocals.locals)
    val unstackifiedLocalIdsInThisBlock = blockLocals.unstackifiedVars.intersect(localIdsInThisBlock)

    if (localIdsInThisBlock != unstackifiedLocalIdsInThisBlock) {
      exprH.resultType.kind match {
        case NeverHT(_) => {
          // Then it's forgivable, because the block never reaches its end.
        }
        case _ => {
          // This probably means that there was no UnletH or DestructureH for that variable.
          vfail("Ununstackified local: " + (localIdsInThisBlock -- unstackifiedLocalIdsInThisBlock))
        }
      }
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

//    start here, we're returning locals and thats not optimal
    BlockH(exprH)
  }

  def translateMutabilify(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    node: MutabilifyIE):
  MutabilifyH = {
    val MutabilifyIE(innerIE, resultCoordI) = node

    val innerHE =
      expressionHammer.translateExpressionsAndDeferreds(
        hinputs, hamuts, currentFunctionHeader, locals, Vector(innerIE))

    val resultCoordH =
      typeHammer.translateCoord(hinputs, hamuts, resultCoordI)

    vassert(
      resultCoordH.ownership ==
        (innerHE.resultType.ownership match {
          case ImmutableShareH => MutableShareH
          case ImmutableBorrowH => MutableBorrowH
          case other => other
        }))

    MutabilifyH(innerHE)
  }

  def translateImmutabilify(
      hinputs: HinputsI,
      hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderI,
      locals: LocalsBox,
      node: ImmutabilifyIE):
  ImmutabilifyH = {
    val ImmutabilifyIE(innerIE, resultCoordI) = node

    val innerHE =
      expressionHammer.translateExpressionsAndDeferreds(
        hinputs, hamuts, currentFunctionHeader, locals, Vector(innerIE))

    val resultCoordH =
      typeHammer.translateCoord(hinputs, hamuts, resultCoordI)

    vassert(
      resultCoordH.ownership ==
          (innerHE.resultType.ownership match {
            case MutableShareH => ImmutableShareH
            case MutableBorrowH => ImmutableBorrowH
            case other => other
          }))

    ImmutabilifyH(innerHE)
  }
}
