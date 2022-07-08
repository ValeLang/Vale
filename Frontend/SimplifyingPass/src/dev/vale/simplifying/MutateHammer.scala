package dev.vale.simplifying

import dev.vale.{Keywords, finalast, vassert}
import dev.vale.finalast.{BorrowH, ExpressionH, KindH, LocalLoadH, LocalStoreH, MemberLoadH, MemberStoreH, ReferenceH, RuntimeSizedArrayStoreH, StaticSizedArrayStoreH, YonderH}
import dev.vale.typing.Hinputs
import dev.vale.typing.ast.{AddressMemberLookupTE, ExpressionT, FunctionHeaderT, LocalLookupTE, MutateTE, ReferenceExpressionTE, ReferenceMemberLookupTE, RuntimeSizedArrayLookupTE, StaticSizedArrayLookupTE}
import dev.vale.typing.env.{AddressibleLocalVariableT, ReferenceLocalVariableT}
import dev.vale.typing.names.{FullNameT, IVarNameT}
import dev.vale.typing.types.{CoordT, StructTT, VariabilityT}
import dev.vale.finalast._
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.names.IVarNameT
import dev.vale.typing.types._

class MutateHammer(
    keywords: Keywords,
    typeHammer: TypeHammer,
    nameHammer: NameHammer,
    structHammer: StructHammer,
    expressionHammer: ExpressionHammer) {

  def translateMutate(
      hinputs: Hinputs,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      mutate2: MutateTE):
  (ExpressionH[KindH]) = {
    val MutateTE(destinationExpr2, sourceExpr2) = mutate2

    val (sourceExprResultLine, sourceDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2);
    val (sourceResultPointerTypeH) =
      typeHammer.translateReference(hinputs, hamuts, sourceExpr2.result.reference)

    val (oldValueAccess, destinationDeferreds) =
      destinationExpr2 match {
        case LocalLookupTE(_,ReferenceLocalVariableT(varId, variability, reference)) => {
          translateMundaneLocalMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, varId)
        }
        case LocalLookupTE(_,AddressibleLocalVariableT(varId, variability, reference)) => {
          translateAddressibleLocalMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, sourceResultPointerTypeH, varId, variability, reference)
        }
        case ReferenceMemberLookupTE(_,structExpr2, memberName, _, _) => {
          translateMundaneMemberMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, structExpr2, memberName)
        }
        case AddressMemberLookupTE(_,structExpr2, memberName, memberType2, _) => {
          translateAddressibleMemberMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, structExpr2, memberName)
        }
        case StaticSizedArrayLookupTE(_, arrayExpr2, _, indexExpr2, _) => {
          translateMundaneStaticSizedArrayMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, arrayExpr2, indexExpr2)
        }
        case RuntimeSizedArrayLookupTE(_, arrayExpr2, _, indexExpr2, _) => {
          translateMundaneRuntimeSizedArrayMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, arrayExpr2, indexExpr2)
        }
      }

    expressionHammer.translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, oldValueAccess, sourceDeferreds ++ destinationDeferreds)
  }

  private def translateMundaneRuntimeSizedArrayMutate(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    sourceExprResultLine: ExpressionH[KindH],
    arrayExpr2: ReferenceExpressionTE,
    indexExpr2: ReferenceExpressionTE
  ): (ExpressionH[KindH], Vector[ExpressionT]) = {
    val (destinationResultLine, destinationDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);
    val (indexExprResultLine, indexDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, indexExpr2);
    val resultType =
      hamuts.getRuntimeSizedArray(
        destinationResultLine.expectRuntimeSizedArrayAccess().resultType.kind)
        .elementType
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
  ): (ExpressionH[KindH], Vector[ExpressionT]) = {
    val (destinationResultLine, destinationDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);
    val (indexExprResultLine, indexDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, indexExpr2);
    val resultType =
      hamuts.getStaticSizedArray(
        destinationResultLine.expectStaticSizedArrayAccess().resultType.kind)
        .elementType
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
  ): (ExpressionH[KindH], Vector[ExpressionT]) = {
    val (destinationResultLine, destinationDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, structExpr2);

    val structTT =
      structExpr2.result.reference.kind match {
        case sr @ StructTT(_) => sr
//        case TupleTT(_, sr) => sr
//        case PackTT(_, sr) => sr
      }
    val structDefT = hinputs.lookupStruct(structTT)
    val memberIndex = structDefT.members.indexWhere(member => structDefT.fullName.addStep(member.name) == memberName)
    vassert(memberIndex >= 0)
    val member2 = structDefT.members(memberIndex)

    val variability = member2.variability

    val boxedType2 = member2.tyype.expectAddressMember().reference

    val (boxedTypeH) =
      typeHammer.translateReference(hinputs, hamuts, boxedType2);

    val (boxStructRefH) =
      structHammer.makeBox(hinputs, hamuts, variability, boxedType2, boxedTypeH)

    // Remember, structs can never own boxes, they only borrow them
    val expectedStructBoxMemberType = ReferenceH(BorrowH, YonderH, boxStructRefH)

    // We're storing into a struct's member that is a box. The stack is also
    // pointing at this box. First, get the box, then mutate what's inside.
    val nameH = nameHammer.translateFullName(hinputs, hamuts, memberName)
    val loadResultType =
      ReferenceH(
        finalast.BorrowH,
        YonderH,
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
          LetHammer.BOX_MEMBER_INDEX,
          sourceExprResultLine,
          nameHammer.addStep(hamuts, boxStructRefH.fullName, keywords.BOX_MEMBER_NAME.str))
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
  ): (ExpressionH[KindH], Vector[ExpressionT]) = {
    val (destinationResultLine, destinationDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, structExpr2);

    val structTT =
      structExpr2.result.reference.kind match {
        case sr @ StructTT(_) => sr
      }
    val structDefT = hinputs.lookupStruct(structTT)
    val memberIndex =
      structDefT.members
        .indexWhere(member => structDefT.fullName.addStep(member.name) == memberName)
    vassert(memberIndex >= 0)

    val structDefH = hamuts.structDefsByRefT(structTT)

    // We're storing into a regular reference member of a struct.
    val storeNode =
        MemberStoreH(
          structDefH.members(memberIndex).tyype,
          destinationResultLine.expectStructAccess(),
          memberIndex,
          sourceExprResultLine,
          nameHammer.translateFullName(hinputs, hamuts, memberName))
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
  ): (ExpressionH[KindH], Vector[ExpressionT]) = {
    val local = locals.get(varId).get
    val (boxStructRefH) =
      structHammer.makeBox(hinputs, hamuts, variability, reference, sourceResultPointerTypeH)

    val structDefH = hamuts.structDefs.find(_.getRef == boxStructRefH).get

    // This means we're trying to mutate a local variable that holds a box.
    // We need to load the box, then mutate its contents.
    val nameH = nameHammer.translateFullName(hinputs, hamuts, varId)
    val loadBoxNode =
      LocalLoadH(
        local,
        finalast.BorrowH,
        nameH)
    val storeNode =
        MemberStoreH(
          structDefH.members.head.tyype,
          loadBoxNode.expectStructAccess(),
          LetHammer.BOX_MEMBER_INDEX,
          sourceExprResultLine,
          nameHammer.addStep(hamuts, boxStructRefH.fullName, keywords.BOX_MEMBER_NAME.str))
    (storeNode, Vector.empty)
  }

  private def translateMundaneLocalMutate(
                                           hinputs: Hinputs,
                                           hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
                                           locals: LocalsBox,
                                           sourceExprResultLine: ExpressionH[KindH],
                                           varId: FullNameT[IVarNameT]
  ): (ExpressionH[KindH], Vector[ExpressionT]) = {
    val local = locals.get(varId).get
    vassert(!locals.unstackifiedVars.contains(local.id))
    val newStoreNode =
        LocalStoreH(
          local,
          sourceExprResultLine,
          nameHammer.translateFullName(hinputs, hamuts, varId))
    (newStoreNode, Vector.empty)
  }
}
