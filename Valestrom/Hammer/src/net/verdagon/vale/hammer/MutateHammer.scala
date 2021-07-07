package net.verdagon.vale.hammer

import net.verdagon.vale.hammer.ExpressionHammer.{translate, translateDeferreds}
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal.{BorrowH => _, Variability => _, _}
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.{AddressibleLocalVariableT, ReferenceLocalVariableT}
import net.verdagon.vale.templar.templata.FunctionHeaderT
import net.verdagon.vale.templar.types._
import net.verdagon.vale.vassert

object MutateHammer {

  def translateMutate(
      hinputs: Hinputs,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      mutate2: MutateTE):
  (ExpressionH[KindH]) = {
    val MutateTE(destinationExpr2, sourceExpr2) = mutate2

    val (sourceExprResultLine, sourceDeferreds) =
      translate(hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2);
    val (sourceResultPointerTypeH) =
      TypeHammer.translateReference(hinputs, hamuts, sourceExpr2.resultRegister.reference)

    val (oldValueAccess, destinationDeferreds) =
      destinationExpr2 match {
        case LocalLookupTE(_,ReferenceLocalVariableT(varId, variability, reference), varType2, _) => {
          translateMundaneLocalMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, varId)
        }
        case LocalLookupTE(_,AddressibleLocalVariableT(varId, variability, reference), varType2, _) => {
          translateAddressibleLocalMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, sourceResultPointerTypeH, varId, variability, reference)
        }
        case ReferenceMemberLookupTE(_,structExpr2, memberName, _, _, _) => {
          translateMundaneMemberMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, structExpr2, memberName)
        }
        case AddressMemberLookupTE(_,structExpr2, memberName, memberType2, _) => {
          translateAddressibleMemberMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, structExpr2, memberName)
        }
        case StaticSizedArrayLookupTE(_, arrayExpr2, _, indexExpr2, _, _) => {
          translateMundaneStaticSizedArrayMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, arrayExpr2, indexExpr2)
        }
        case RuntimeSizedArrayLookupTE(_, arrayExpr2, _, indexExpr2, _, _) => {
          translateMundaneRuntimeSizedArrayMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, arrayExpr2, indexExpr2)
        }
      }

    translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, oldValueAccess, sourceDeferreds ++ destinationDeferreds)
  }

  private def translateMundaneRuntimeSizedArrayMutate(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    sourceExprResultLine: ExpressionH[KindH],
    arrayExpr2: ReferenceExpressionTE,
    indexExpr2: ReferenceExpressionTE
  ): (ExpressionH[KindH], List[ExpressionT]) = {
    val (destinationResultLine, destinationDeferreds) =
      translate(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);
    val (indexExprResultLine, indexDeferreds) =
      translate(hinputs, hamuts, currentFunctionHeader, locals, indexExpr2);
    val resultType =
      hamuts.getRuntimeSizedArray(
        destinationResultLine.expectRuntimeSizedArrayAccess().resultType.kind)
        .rawArray.elementType
    // We're storing into a regular reference element of an array.
    val storeNode =
        RuntimeSizedArrayStoreH(
          destinationResultLine.expectRuntimeSizedArrayAccess(),
          indexExprResultLine.expectIntAccess(),
          sourceExprResultLine,
          resultType)

    (storeNode, destinationDeferreds ++ indexDeferreds)
  }

  private def translateMundaneStaticSizedArrayMutate(
                                                    hinputs: Hinputs,
                                                    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
                                                    locals: LocalsBox,
                                                    sourceExprResultLine: ExpressionH[KindH],
                                                    arrayExpr2: ReferenceExpressionTE,
                                                    indexExpr2: ReferenceExpressionTE
  ): (ExpressionH[KindH], List[ExpressionT]) = {
    val (destinationResultLine, destinationDeferreds) =
      translate(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);
    val (indexExprResultLine, indexDeferreds) =
      translate(hinputs, hamuts, currentFunctionHeader, locals, indexExpr2);
    val resultType =
      hamuts.getStaticSizedArray(
        destinationResultLine.expectStaticSizedArrayAccess().resultType.kind)
        .rawArray.elementType
    // We're storing into a regular reference element of an array.
    val storeNode =
        StaticSizedArrayStoreH(
          destinationResultLine.expectStaticSizedArrayAccess(),
          indexExprResultLine.expectIntAccess(),
          sourceExprResultLine,
          resultType)

    (storeNode, destinationDeferreds ++ indexDeferreds)
  }

  private def translateAddressibleMemberMutate(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    sourceExprResultLine: ExpressionH[KindH],
    structExpr2: ReferenceExpressionTE,
    memberName: FullNameT[IVarNameT]
  ): (ExpressionH[KindH], List[ExpressionT]) = {
    val (destinationResultLine, destinationDeferreds) =
      translate(hinputs, hamuts, currentFunctionHeader, locals, structExpr2);

    val structRefT =
      structExpr2.resultRegister.reference.kind match {
        case sr @ StructRefT(_) => sr
        case TupleTT(_, sr) => sr
        case PackTT(_, sr) => sr
      }
    val structDefT = hinputs.lookupStruct(structRefT)
    val memberIndex = structDefT.members.indexWhere(member => structDefT.fullName.addStep(member.name) == memberName)
    vassert(memberIndex >= 0)
    val member2 = structDefT.members(memberIndex)

    val variability = member2.variability

    val boxedType2 = member2.tyype.expectAddressMember().reference

    val (boxedTypeH) =
      TypeHammer.translateReference(hinputs, hamuts, boxedType2);

    val (boxStructRefH) =
      StructHammer.makeBox(hinputs, hamuts, variability, boxedType2, boxedTypeH)

    // Remember, structs can never own boxes, they only borrow them
    val expectedStructBoxMemberType = ReferenceH(m.BorrowH, YonderH, ReadwriteH, boxStructRefH)

    // We're storing into a struct's member that is a box. The stack is also
    // pointing at this box. First, get the box, then mutate what's inside.
    val nameH = NameHammer.translateFullName(hinputs, hamuts, memberName)
    val loadResultType =
      ReferenceH(
        m.BorrowH,
        YonderH,
        // The box should be readwrite, but targetPermission is taken into account when we load/mutate from the
        // box's member.
        ReadwriteH,
        boxStructRefH)
    val loadBoxNode =
        MemberLoadH(
          destinationResultLine.expectStructAccess(),
          memberIndex,
          expectedStructBoxMemberType,
          loadResultType,
          nameH)
    val storeNode =
        MemberStoreH(
          boxedTypeH,
          loadBoxNode.expectStructAccess(),
          StructHammer.BOX_MEMBER_INDEX,
          sourceExprResultLine,
          NameHammer.addStep(hamuts, boxStructRefH.fullName, StructHammer.BOX_MEMBER_NAME))
    (storeNode, destinationDeferreds)
  }

  private def translateMundaneMemberMutate(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    sourceExprResultLine: ExpressionH[KindH],
    structExpr2: ReferenceExpressionTE,
    memberName: FullNameT[IVarNameT]
  ): (ExpressionH[KindH], List[ExpressionT]) = {
    val (destinationResultLine, destinationDeferreds) =
      translate(hinputs, hamuts, currentFunctionHeader, locals, structExpr2);

    val structRefT =
      structExpr2.resultRegister.reference.kind match {
        case sr @ StructRefT(_) => sr
      }
    val structDefT = hinputs.lookupStruct(structRefT)
    val memberIndex =
      structDefT.members
        .indexWhere(member => structDefT.fullName.addStep(member.name) == memberName)
    vassert(memberIndex >= 0)

    val structDefH = hamuts.structDefsByRef2(structRefT)

    // We're storing into a regular reference member of a struct.
    val storeNode =
        MemberStoreH(
          structDefH.members(memberIndex).tyype,
          destinationResultLine.expectStructAccess(),
          memberIndex,
          sourceExprResultLine,
          NameHammer.translateFullName(hinputs, hamuts, memberName))
    (storeNode, destinationDeferreds)
  }

  private def translateAddressibleLocalMutate(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    sourceExprResultLine: ExpressionH[KindH],
    sourceResultPointerTypeH: ReferenceH[KindH],
    varId: FullNameT[IVarNameT],
    variability: VariabilityT,
    reference: CoordT
  ): (ExpressionH[KindH], List[ExpressionT]) = {
    val local = locals.get(varId).get
    val (boxStructRefH) =
      StructHammer.makeBox(hinputs, hamuts, variability, reference, sourceResultPointerTypeH)

    val structDefH = hamuts.structDefs.find(_.getRef == boxStructRefH).get

    // This means we're trying to mutate a local variable that holds a box.
    // We need to load the box, then mutate its contents.
    val nameH = NameHammer.translateFullName(hinputs, hamuts, varId)
    val loadBoxNode =
      LocalLoadH(
        local,
        m.BorrowH,
        // The box should be readwrite, but targetPermission is taken into account when we load from its member.
        ReadwriteH,
        nameH)
    val storeNode =
        MemberStoreH(
          structDefH.members.head.tyype,
          loadBoxNode.expectStructAccess(),
          StructHammer.BOX_MEMBER_INDEX,
          sourceExprResultLine,
          NameHammer.addStep(hamuts, boxStructRefH.fullName, StructHammer.BOX_MEMBER_NAME))
    (storeNode, List.empty)
  }

  private def translateMundaneLocalMutate(
                                           hinputs: Hinputs,
                                           hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
                                           locals: LocalsBox,
                                           sourceExprResultLine: ExpressionH[KindH],
                                           varId: FullNameT[IVarNameT]
  ): (ExpressionH[KindH], List[ExpressionT]) = {
    val local = locals.get(varId).get
    vassert(!locals.unstackifiedVars.contains(local.id))
    val newStoreNode =
        LocalStoreH(
          local,
          sourceExprResultLine,
          NameHammer.translateFullName(hinputs, hamuts, varId))
    (newStoreNode, List.empty)
  }
}
