package net.verdagon.vale.hammer

import net.verdagon.vale.hammer.ExpressionHammer.translate
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.{vassert, vassertSome, vfail, vimpl, metal => m}
import net.verdagon.vale.metal.{Variability => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.{AddressibleLocalVariable2, ReferenceLocalVariable2}
import net.verdagon.vale.templar.types._

object LetHammer {

  def translateLet(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      locals: LocalsBox,
      let2: LetNormal2):
  ExpressionH[ReferendH] = {
    val LetNormal2(localVariable, sourceExpr2) = let2

    val (sourceExprResultLine, deferreds) =
      translate(hinputs, hamuts, locals, sourceExpr2);
    val (sourceResultPointerTypeH) =
      TypeHammer.translateReference(hinputs, hamuts, sourceExpr2.resultRegister.reference)

    val stackifyNode =
      localVariable match {
        case ReferenceLocalVariable2(varId, variability, type2) => {
          translateMundaneLet(
            hinputs, hamuts, locals, sourceExprResultLine, sourceResultPointerTypeH, varId)
        }
        case AddressibleLocalVariable2(varId, variability, reference) => {
          translateAddressibleLet(
            hinputs, hamuts, locals, sourceExprResultLine, sourceResultPointerTypeH, varId, variability, reference)
        }
      }

    ExpressionHammer.translateDeferreds(
      hinputs, hamuts, locals, stackifyNode, deferreds)
  }

  def translateLetAndLend(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      locals: LocalsBox,
      let2: LetAndLend2):
  (ExpressionH[ReferendH]) = {
    val LetAndLend2(localVariable, sourceExpr2) = let2

    val (sourceExprResultLine, deferreds) =
      translate(hinputs, hamuts, locals, sourceExpr2);
    val (sourceResultPointerTypeH) =
      TypeHammer.translateReference(hinputs, hamuts, sourceExpr2.resultRegister.reference)

    val borrowAccess =
      localVariable match {
        case ReferenceLocalVariable2(varId, variability, type2) => {
          translateMundaneLetAndLend(
            hinputs, hamuts, locals, sourceExpr2, sourceExprResultLine, sourceResultPointerTypeH, let2, varId)
        }
        case AddressibleLocalVariable2(varId, variability, reference) => {
          translateAddressibleLetAndLend(
            hinputs, hamuts, locals, sourceExpr2, sourceExprResultLine, sourceResultPointerTypeH, let2, varId, variability, reference)
        }
      }

    ExpressionHammer.translateDeferreds(
      hinputs, hamuts, locals, borrowAccess, deferreds)
  }

  private def translateAddressibleLet(
                                       hinputs: Hinputs,
                                       hamuts: HamutsBox,
                                       locals: LocalsBox,
                                       sourceExprResultLine: ExpressionH[ReferendH],
                                       sourceResultPointerTypeH: ReferenceH[ReferendH],
                                       varId: FullName2[IVarName2],
                                       variability: Variability,
                                       reference: Coord):
  ExpressionH[ReferendH] = {
    val (boxStructRefH) =
      StructHammer.makeBox(hinputs, hamuts, variability, reference, sourceResultPointerTypeH)
    val expectedLocalBoxType = ReferenceH(m.OwnH, InlineH, boxStructRefH)

    val local =
      locals.addTemplarLocal(hinputs, hamuts, varId, expectedLocalBoxType)

    StackifyH(
      NewStructH(
        List(sourceExprResultLine),
        expectedLocalBoxType),
      local,
      Some(NameHammer.translateFullName(hinputs, hamuts, varId)))
  }

  private def translateAddressibleLetAndLend(
                                              hinputs: Hinputs,
                                              hamuts: HamutsBox,
                                              locals: LocalsBox,
                                              sourceExpr2: ReferenceExpression2,
                                              sourceExprResultLine: ExpressionH[ReferendH],
                                              sourceResultPointerTypeH: ReferenceH[ReferendH],
                                              let2: LetAndLend2,
                                              varId: FullName2[IVarName2],
                                              variability: Variability,
                                              reference: Coord):
  (ExpressionH[ReferendH]) = {
    val stackifyH =
      translateAddressibleLet(
        hinputs, hamuts, locals, sourceExprResultLine, sourceResultPointerTypeH, varId, variability, reference)
    val (borrowAccess, List()) =
      LoadHammer.translateAddressibleLocalLoad(
        hinputs,
        hamuts,
        locals,
        varId,
        variability,
        sourceExpr2.resultRegister.reference,
        let2.resultRegister.reference.ownership)
    BlockH(List(stackifyH, borrowAccess))
  }

  private def translateMundaneLet(
                                   hinputs: Hinputs,
                                   hamuts: HamutsBox,
                                   locals: LocalsBox,
                                   sourceExprResultLine: ExpressionH[ReferendH],
                                   sourceResultPointerTypeH: ReferenceH[ReferendH],
                                   varId: FullName2[IVarName2]):
  StackifyH = {
    val localIndex =
      locals.addTemplarLocal(hinputs, hamuts, varId, sourceResultPointerTypeH)
    val stackNode =
      StackifyH(
        sourceExprResultLine,
        localIndex,
        Some(NameHammer.translateFullName(hinputs, hamuts, varId)))
    stackNode
  }

    private def translateMundaneLetAndLend(
                                            hinputs: Hinputs,
                                            hamuts: HamutsBox,
                                            locals: LocalsBox,
                                            sourceExpr2: ReferenceExpression2,
                                            sourceExprResultLine: ExpressionH[ReferendH],
                                            sourceResultPointerTypeH: ReferenceH[ReferendH],
                                            let2: LetAndLend2,
                                            varId: FullName2[IVarName2]):
    ExpressionH[ReferendH] = {

      val stackifyH =
        translateMundaneLet(
          hinputs,
          hamuts,
          locals,
          sourceExprResultLine,
          sourceResultPointerTypeH,
          varId)

    val (borrowAccess, List()) =
      LoadHammer.translateMundaneLocalLoad(
        hinputs,
        hamuts,
        locals,
        varId,
        sourceExpr2.resultRegister.reference,
        let2.resultRegister.reference.ownership)

      BlockH(List(stackifyH, borrowAccess))
  }

  def translateUnlet(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      locals: LocalsBox,
      unlet2: Unlet2):
  (ExpressionH[ReferendH]) = {
    val local =
      locals.get(unlet2.variable.id) match {
        case None => {
          vfail("Unletting an unknown variable: " + unlet2.variable.id)
        }
        case Some(local) => local
      }

    unlet2.variable match {
      case ReferenceLocalVariable2(varId, _, localType2) => {
        val localTypeH = TypeHammer.translateReference(hinputs, hamuts, localType2)
        val unstackifyNode = UnstackifyH(local)
        locals.markUnstackified(varId)
        unstackifyNode
      }
      case AddressibleLocalVariable2(varId, variability, innerType2) => {
        val innerTypeH = TypeHammer.translateReference(hinputs, hamuts, innerType2)
        val structRefH =
          StructHammer.makeBox(hinputs, hamuts, variability, innerType2, innerTypeH)

        val unstackifyBoxNode = UnstackifyH(local)
        locals.markUnstackified(varId)

        val innerLocal = locals.addHammerLocal(innerTypeH)

        val desH =
          DestroyH(
            unstackifyBoxNode.expectStructAccess(),
            List(innerTypeH),
            Vector(innerLocal))
        locals.markUnstackified(innerLocal.id)

        val unstackifyContentsNode = UnstackifyH(innerLocal)

        val blockH =
          ExpressionHammer.flattenAndMakeBlock(List(desH, unstackifyContentsNode))

        blockH
      }
    }
  }

  def translateDestructureArraySequence(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    locals: LocalsBox,
    des2: DestroyArraySequenceIntoLocals2
  ): ExpressionH[ReferendH] = {
    val DestroyArraySequenceIntoLocals2(sourceExpr2, arrSeqT, destinationReferenceLocalVariables) = des2

    val (sourceExprResultLine, sourceExprDeferreds) =
      translate(hinputs, hamuts, locals, sourceExpr2);

    vassert(destinationReferenceLocalVariables.size == arrSeqT.size)

    // Destructure2 will immediately destroy any addressible references inside it
    // (see Destructure2 comments).
    // In the post-addressible world with all our boxes and stuff, an addressible
    // reference member is actually a borrow reference to a box.
    // Destructure2's destroying of addressible references translates to hammer
    // unborrowing the references to boxes.
    // However, the templar only supplied variables for the reference members,
    // so we need to introduce our own local variables here.

    val (localTypes, localIndices) =
      destinationReferenceLocalVariables
        .map(destinationReferenceLocalVariable => {
          val (memberRefTypeH) =
            TypeHammer.translateReference(hinputs, hamuts, arrSeqT.array.memberType)
          val localIndex =
            locals.addTemplarLocal(
              hinputs, hamuts, destinationReferenceLocalVariable.id, memberRefTypeH)
          (memberRefTypeH, localIndex)
        })
        .unzip

    val stackNode =
        DestructureArraySequenceH(
          sourceExprResultLine.expectKnownSizeArrayAccess(),
          localTypes,
          localIndices.toVector)

    ExpressionHammer.translateDeferreds(
      hinputs, hamuts, locals, stackNode, sourceExprDeferreds)
  }

  def translateDestroy(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      locals: LocalsBox,
      des2: Destroy2):
  ExpressionH[ReferendH] = {
    val Destroy2(sourceExpr2, structRef2, destinationReferenceLocalVariables) = des2

    val (sourceExprResultLine, sourceExprDeferreds) =
      translate(hinputs, hamuts, locals, sourceExpr2);

    val structDef2 = hinputs.lookupStruct(structRef2)

    // Destructure2 will immediately destroy any addressible references inside it
    // (see Destructure2 comments).
    // In the post-addressible world with all our boxes and stuff, an addressible
    // reference member is actually a borrow reference to a box.
    // Destructure2's destroying of addressible references translates to hammer
    // unborrowing the references to boxes.
    // However, the templar only supplied variables for the reference members,
    // so we need to introduce our own local variables here.

    // We put List() here to make sure that we've consumed all the destination
    // reference local variables.
    val (List(), localTypes, localIndices) =
      structDef2.members.foldLeft((destinationReferenceLocalVariables, List[ReferenceH[ReferendH]](), List[Local]()))({
        case ((remainingDestinationReferenceLocalVariables, previousLocalTypes, previousLocalIndices), member2) => {
          member2.tyype match {
            case ReferenceMemberType2(memberRefType2) => {
              val destinationReferenceLocalVariable = remainingDestinationReferenceLocalVariables.head

              val (memberRefTypeH) =
                TypeHammer.translateReference(hinputs, hamuts, memberRefType2)
              val localIndex =
                locals.addTemplarLocal(
                  hinputs, hamuts, destinationReferenceLocalVariable.id, memberRefTypeH)
              (remainingDestinationReferenceLocalVariables.tail, previousLocalTypes :+ memberRefTypeH, previousLocalIndices :+ localIndex)
            }
            // The struct might have addressibles in them, which translate to
            // borrow refs of boxes which contain things. We're moving that borrow
            // ref into a local variable. We'll then unlet the local variable, and
            // unborrow it.
            case AddressMemberType2(memberRefType2) => {
              val (memberRefTypeH) =
                TypeHammer.translateReference(hinputs, hamuts, memberRefType2);
              // In the case of an addressible struct member, its variability refers to the
              // variability of the pointee variable, see StructMember2
              val (boxStructRefH) =
                StructHammer.makeBox(hinputs, hamuts, member2.variability, memberRefType2, memberRefTypeH)
              // Structs only ever borrow boxes, boxes are only ever owned by the stack.
              val localBoxType = ReferenceH(m.BorrowH, YonderH, boxStructRefH)
              val localIndex = locals.addHammerLocal(localBoxType)

              (remainingDestinationReferenceLocalVariables, previousLocalTypes :+ localBoxType, previousLocalIndices :+ localIndex)
            }
          }
        }
      })

    val destructureH =
        DestroyH(
          sourceExprResultLine.expectStructAccess(),
          localTypes,
          localIndices.toVector)

    val unboxingsH =
      structDef2.members.zip(localTypes.zip(localIndices)).flatMap({
        case (structMember2, (localType, local)) => {
          structMember2.tyype match {
            case ReferenceMemberType2(_) => List()
            case AddressMemberType2(_) => {
              // localType is the box type.
              // First, unlet it, then discard the contents.
              // We discard instead of putting it into a local because address members
              // can't own, they only refer to a box owned elsewhere.
              val unstackifyNode = UnstackifyH(local)
              locals.markUnstackified(local.id)

              val discardNode = DiscardH(unstackifyNode)
              List(discardNode)
            }
          }
        }
      })

    val destructureAndUnboxings = ExpressionHammer.flattenAndMakeBlock(destructureH :: unboxingsH)

    ExpressionHammer.translateDeferreds(
      hinputs, hamuts, locals, destructureAndUnboxings, sourceExprDeferreds)
  }
}
