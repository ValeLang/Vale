package net.verdagon.vale.hammer

import net.verdagon.vale.hammer.ExpressionHammer.translate
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.{vassert, vassertSome, vfail, vimpl, metal => m}
import net.verdagon.vale.metal.{Variability => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.{AddressibleLocalVariableT, ReferenceLocalVariableT}
import net.verdagon.vale.templar.templata.FunctionHeaderT
import net.verdagon.vale.templar.types._

object LetHammer {

  def translateLet(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      let2: LetNormalTE):
  ExpressionH[KindH] = {
    val LetNormalTE(localVariable, sourceExpr2) = let2

    val (sourceExprResultLine, deferreds) =
      translate(hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2);
    val (sourceResultPointerTypeH) =
      TypeHammer.translateReference(hinputs, hamuts, sourceExpr2.resultRegister.reference)

    val stackifyNode =
      localVariable match {
        case ReferenceLocalVariableT(varId, variability, type2) => {
          translateMundaneLet(
            hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, sourceResultPointerTypeH, varId, variability)
        }
        case AddressibleLocalVariableT(varId, variability, reference) => {
          translateAddressibleLet(
            hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, sourceResultPointerTypeH, varId, variability, reference)
        }
      }

    ExpressionHammer.translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, stackifyNode, deferreds)
  }

  def translateLetAndLend(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    let2: LetAndLendTE):
  (ExpressionH[KindH]) = {
    val LetAndLendTE(localVariable, sourceExpr2) = let2

    val (sourceExprResultLine, deferreds) =
      translate(hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2);
    val (sourceResultPointerTypeH) =
      TypeHammer.translateReference(hinputs, hamuts, sourceExpr2.resultRegister.reference)

    val borrowAccess =
      localVariable match {
        case ReferenceLocalVariableT(varId, variability, type2) => {
          translateMundaneLetAndLend(
            hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2, sourceExprResultLine, sourceResultPointerTypeH, let2, varId, variability)
        }
        case AddressibleLocalVariableT(varId, variability, reference) => {
          translateAddressibleLetAndLend(
            hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2, sourceExprResultLine, sourceResultPointerTypeH, let2, varId, variability, reference)
        }
      }

    ExpressionHammer.translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, borrowAccess, deferreds)
  }

  private def translateAddressibleLet(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    sourceExprResultLine: ExpressionH[KindH],
    sourceResultPointerTypeH: ReferenceH[KindH],
    varId: FullNameT[IVarNameT],
    variability: VariabilityT,
    reference: CoordT):
  ExpressionH[KindH] = {
    val (boxStructRefH) =
      StructHammer.makeBox(hinputs, hamuts, variability, reference, sourceResultPointerTypeH)
    val expectedLocalBoxType = ReferenceH(m.OwnH, YonderH, ReadwriteH, boxStructRefH)

    val local =
      locals.addTemplarLocal(hinputs, hamuts, varId, Conversions.evaluateVariability(variability), expectedLocalBoxType)

    StackifyH(
      NewStructH(
        List(sourceExprResultLine),
        hamuts.structDefs.find(_.getRef == boxStructRefH).get.members.map(_.name),
        expectedLocalBoxType),
      local,
      Some(NameHammer.translateFullName(hinputs, hamuts, varId)))
  }

  private def translateAddressibleLetAndLend(
    hinputs: Hinputs,
    hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    sourceExpr2: ReferenceExpressionTE,
    sourceExprResultLine: ExpressionH[KindH],
    sourceResultPointerTypeH: ReferenceH[KindH],
    let2: LetAndLendTE,
    varId: FullNameT[IVarNameT],
    variability: VariabilityT,
    reference: CoordT):
  (ExpressionH[KindH]) = {
    val stackifyH =
      translateAddressibleLet(
        hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, sourceResultPointerTypeH, varId, variability, reference)
    val (borrowAccess, Nil) =
      LoadHammer.translateAddressibleLocalLoad(
        hinputs,
        hamuts,
        currentFunctionHeader,
        locals,
        varId,
        variability,
        sourceExpr2.resultRegister.reference,
        let2.resultRegister.reference.ownership,
        let2.resultRegister.reference.permission)
    ConsecutorH(List(stackifyH, borrowAccess))
  }

  private def translateMundaneLet(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    sourceExprResultLine: ExpressionH[KindH],
    sourceResultPointerTypeH: ReferenceH[KindH],
    varId: FullNameT[IVarNameT],
    variability: VariabilityT):
  StackifyH = {
    val localIndex =
      locals.addTemplarLocal(hinputs, hamuts, varId, Conversions.evaluateVariability(variability), sourceResultPointerTypeH)
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
      currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      sourceExpr2: ReferenceExpressionTE,
      sourceExprResultLine: ExpressionH[KindH],
      sourceResultPointerTypeH: ReferenceH[KindH],
      let2: LetAndLendTE,
      varId: FullNameT[IVarNameT],
      variability: VariabilityT):
    ExpressionH[KindH] = {

      val stackifyH =
        translateMundaneLet(
          hinputs,
          hamuts,
          currentFunctionHeader,
          locals,
          sourceExprResultLine,
          sourceResultPointerTypeH,
          varId,
          variability)

    val (borrowAccess, Nil) =
      LoadHammer.translateMundaneLocalLoad(
        hinputs,
        hamuts,
        currentFunctionHeader,
        locals,
        varId,
        sourceExpr2.resultRegister.reference,
        let2.resultRegister.reference.ownership,
        let2.resultRegister.reference.permission)

      ConsecutorH(List(stackifyH, borrowAccess))
  }

  def translateUnlet(
      hinputs: Hinputs,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      unlet2: UnletTE):
  (ExpressionH[KindH]) = {
    val local =
      locals.get(unlet2.variable.id) match {
        case None => {
          vfail("Unletting an unknown variable: " + unlet2.variable.id)
        }
        case Some(local) => local
      }

    unlet2.variable match {
      case ReferenceLocalVariableT(varId, _, localType2) => {
        val localTypeH = TypeHammer.translateReference(hinputs, hamuts, localType2)
        val unstackifyNode = UnstackifyH(local)
        locals.markUnstackified(varId)
        unstackifyNode
      }
      case AddressibleLocalVariableT(varId, variability, innerType2) => {
        val innerTypeH = TypeHammer.translateReference(hinputs, hamuts, innerType2)
        val structRefH =
          StructHammer.makeBox(hinputs, hamuts, variability, innerType2, innerTypeH)

        val unstackifyBoxNode = UnstackifyH(local)
        locals.markUnstackified(varId)

        val innerLocal = locals.addHammerLocal(innerTypeH, Conversions.evaluateVariability(variability))

        val desH =
          DestroyH(
            unstackifyBoxNode.expectStructAccess(),
            List(innerTypeH),
            Vector(innerLocal))
        locals.markUnstackified(innerLocal.id)

        val unstackifyContentsNode = UnstackifyH(innerLocal)

        ConsecutorH(List(desH, unstackifyContentsNode))
      }
    }
  }

  def translateDestructureStaticSizedArray(
    hinputs: Hinputs,
    hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    des2: DestroyStaticSizedArrayIntoLocalsTE
  ): ExpressionH[KindH] = {
    val DestroyStaticSizedArrayIntoLocalsTE(sourceExpr2, arrSeqT, destinationReferenceLocalVariables) = des2

    val (sourceExprResultLine, sourceExprDeferreds) =
      translate(hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2);

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
              hinputs, hamuts, destinationReferenceLocalVariable.id, Conversions.evaluateVariability(destinationReferenceLocalVariable.variability), memberRefTypeH)
          (memberRefTypeH, localIndex)
        })
        .unzip

    val stackNode =
        DestroyStaticSizedArrayIntoLocalsH(
          sourceExprResultLine.expectStaticSizedArrayAccess(),
          localTypes,
          localIndices.toVector)

    ExpressionHammer.translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, stackNode, sourceExprDeferreds)
  }

  def translateDestroy(
      hinputs: Hinputs,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      des2: DestroyTE):
  ExpressionH[KindH] = {
    val DestroyTE(sourceExpr2, structRefT, destinationReferenceLocalVariables) = des2

    val (sourceExprResultLine, sourceExprDeferreds) =
      translate(hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2);

    val structDefT = hinputs.lookupStruct(structRefT)

    // Destructure2 will immediately destroy any addressible references inside it
    // (see Destructure2 comments).
    // In the post-addressible world with all our boxes and stuff, an addressible
    // reference member is actually a borrow reference to a box.
    // Destructure2's destroying of addressible references translates to hammer
    // unborrowing the references to boxes.
    // However, the templar only supplied variables for the reference members,
    // so we need to introduce our own local variables here.

    // We put List.empty here to make sure that we've consumed all the destination
    // reference local variables.
    val (Nil, localTypes, localIndices) =
      structDefT.members.foldLeft((destinationReferenceLocalVariables, List[ReferenceH[KindH]](), List[Local]()))({
        case ((remainingDestinationReferenceLocalVariables, previousLocalTypes, previousLocalIndices), member2) => {
          member2.tyype match {
            case ReferenceMemberTypeT(memberRefType2) => {
              val destinationReferenceLocalVariable = remainingDestinationReferenceLocalVariables.head

              val (memberRefTypeH) =
                TypeHammer.translateReference(hinputs, hamuts, memberRefType2)
              val localIndex =
                locals.addTemplarLocal(
                  hinputs, hamuts, destinationReferenceLocalVariable.id, Conversions.evaluateVariability(destinationReferenceLocalVariable.variability), memberRefTypeH)
              (remainingDestinationReferenceLocalVariables.tail, previousLocalTypes :+ memberRefTypeH, previousLocalIndices :+ localIndex)
            }
            // The struct might have addressibles in them, which translate to
            // borrow refs of boxes which contain things. We're moving that borrow
            // ref into a local variable. We'll then unlet the local variable, and
            // unborrow it.
            case AddressMemberTypeT(memberRefType2) => {
              val (memberRefTypeH) =
                TypeHammer.translateReference(hinputs, hamuts, memberRefType2);
              // In the case of an addressible struct member, its variability refers to the
              // variability of the pointee variable, see structMember2
              val (boxStructRefH) =
                StructHammer.makeBox(hinputs, hamuts, member2.variability, memberRefType2, memberRefTypeH)
              // Structs only ever borrow boxes, boxes are only ever owned by the stack.
              val localBoxType = ReferenceH(m.BorrowH, YonderH, ReadwriteH, boxStructRefH)
              val localIndex = locals.addHammerLocal(localBoxType, m.Final)

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
      structDefT.members.zip(localTypes.zip(localIndices)).flatMap({
        case (structMember2, (localType, local)) => {
          structMember2.tyype match {
            case ReferenceMemberTypeT(_) => List.empty
            case AddressMemberTypeT(_) => {
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

    val destructureAndUnboxings = ConsecutorH(destructureH :: unboxingsH)

    ExpressionHammer.translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, destructureAndUnboxings, sourceExprDeferreds)
  }
}
