package dev.vale.simplifying

import dev.vale._
import dev.vale.finalast._
import dev.vale.instantiating.ast._

class MutateHammer(
    keywords: Keywords,
    typeHammer: TypeHammer,
    nameHammer: NameHammer,
    structHammer: StructHammer,
    expressionHammer: ExpressionHammer) {

  def translateMutate(
      hinputs: HinputsI,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
      locals: LocalsBox,
      mutate2: MutateIE):
  (ExpressionH[KindHT]) = {
    val MutateIE(destinationExpr2, sourceExpr2, _) = mutate2

    val (sourceExprResultLine, sourceDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2);
    val (sourceResultPointerTypeH) =
      typeHammer.translateCoord(hinputs, hamuts, sourceExpr2.result)

    val (oldValueAccess, destinationDeferreds) =
      destinationExpr2 match {
        case LocalLookupIE(ReferenceLocalVariableI(varId, variability, reference), _) => {
          translateMundaneLocalMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, varId)
        }
        case LocalLookupIE(AddressibleLocalVariableI(varId, variability, reference), _) => {
          vimpl()
          translateAddressibleLocalMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, sourceResultPointerTypeH, varId, variability, reference)
        }
        case ReferenceMemberLookupIE(_,structExpr2, memberName, _, _) => {
          translateMundaneMemberMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, structExpr2, memberName)
        }
        case AddressMemberLookupIE(structExpr2, memberName, memberType2, _) => {
          translateAddressibleMemberMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, structExpr2, memberName)
        }
        case StaticSizedArrayLookupIE(_, arrayExpr2, indexExpr2, _, _) => {
          translateMundaneStaticSizedArrayMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, arrayExpr2, indexExpr2)
        }
        case RuntimeSizedArrayLookupIE(arrayExpr2, indexExpr2, _, _) => {
          translateMundaneRuntimeSizedArrayMutate(hinputs, hamuts, currentFunctionHeader, locals, sourceExprResultLine, arrayExpr2, indexExpr2)
        }
      }

    expressionHammer.translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, oldValueAccess, sourceDeferreds ++ destinationDeferreds)
  }

  private def translateMundaneRuntimeSizedArrayMutate(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    sourceExprResultLine: ExpressionH[KindHT],
    arrayExpr2: ReferenceExpressionIE,
    indexExpr2: ReferenceExpressionIE
  ): (ExpressionH[KindHT], Vector[ExpressionI]) = {
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
                                                    hinputs: HinputsI,
                                                    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
                                                    locals: LocalsBox,
                                                    sourceExprResultLine: ExpressionH[KindHT],
                                                    arrayExpr2: ReferenceExpressionIE,
                                                    indexExpr2: ReferenceExpressionIE
  ): (ExpressionH[KindHT], Vector[ExpressionI]) = {
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
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    sourceExprResultLine: ExpressionH[KindHT],
    structExpr2: ReferenceExpressionIE,
    memberName: IVarNameI[cI]
  ): (ExpressionH[KindHT], Vector[ExpressionI]) = {
    val (destinationResultLine, destinationDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, structExpr2);

    val structIT =
      structExpr2.result.kind match {
        case sr @ StructIT(_) => sr
//        case TupleIT(_, sr) => sr
//        case PackIT(_, sr) => sr
      }
    val structDefI = hinputs.lookupStruct(structIT.id)
    val memberIndex = structDefI.members.indexWhere(_.name == memberName)
    vassert(memberIndex >= 0)
    val member2 =
      structDefI.members(memberIndex) match {
        case n @ StructMemberI(name, variability, tyype) => n
      }

    val variability = member2.variability

    val boxedType2 = member2.tyype.expectAddressMember().reference

    val (boxedTypeH) =
      typeHammer.translateCoord(hinputs, hamuts, boxedType2);

    val (boxStructRefH) =
      structHammer.makeBox(hinputs, hamuts, variability, boxedType2, boxedTypeH)

    // Remember, structs can never own boxes, they only borrow them
    val expectedStructBoxMemberType = CoordH(vimpl(/*BorrowH*/), YonderH, boxStructRefH)

    // We're storing into a struct's member that is a box. The stack is also
    // pointing at this box. First, get the box, then mutate what's inside.
    val nameH = nameHammer.translateFullName(hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, memberName))
    val loadResultType =
      CoordH(
        vimpl(/*BorrowH*/),
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
          nameHammer.addStep(hamuts, boxStructRefH.id, keywords.BOX_MEMBER_NAME.str))
    (storeNode, destinationDeferreds)
  }

  private def translateMundaneMemberMutate(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    sourceExprResultLine: ExpressionH[KindHT],
    structExpr2: ReferenceExpressionIE,
    memberName: IVarNameI[cI],
  ): (ExpressionH[KindHT], Vector[ExpressionI]) = {
    val (destinationResultLine, destinationDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, structExpr2);

    val structIT =
      structExpr2.result.kind match {
        case sr @ StructIT(_) => sr
      }
    val structDefI = hinputs.lookupStruct(structIT.id)
    val memberIndex =
      structDefI.members
        .indexWhere(_.name == memberName)
    vassert(memberIndex >= 0)

    val structDefH = hamuts.structTToStructDefH(structIT)

    // We're storing into a regular reference member of a struct.
    val storeNode =
        MemberStoreH(
          structDefH.members(memberIndex).tyype,
          destinationResultLine.expectStructAccess(),
          memberIndex,
          sourceExprResultLine,
          nameHammer.translateFullName(hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, memberName)))
    (storeNode, destinationDeferreds)
  }

  private def translateAddressibleLocalMutate(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    sourceExprResultLine: ExpressionH[KindHT],
    sourceResultPointerTypeH: CoordH[KindHT],
    varId: IVarNameI[cI],
    variability: VariabilityI,
    reference: CoordI[cI]
  ): (ExpressionH[KindHT], Vector[ExpressionI]) = {
    val local = locals.get(varId).get
    val (boxStructRefH) =
      structHammer.makeBox(hinputs, hamuts, variability, reference, sourceResultPointerTypeH)

    val structDefH = hamuts.structDefs.find(_.getRef == boxStructRefH).get

    // This means we're trying to mutate a local variable that holds a box.
    // We need to load the box, then mutate its contents.
    val nameH = nameHammer.translateFullName(hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, varId))
    val loadBoxNode =
      LocalLoadH(
        local,
        vimpl(/*BorrowH*/),
        nameH)
    val storeNode =
        MemberStoreH(
          structDefH.members.head.tyype,
          loadBoxNode.expectStructAccess(),
          LetHammer.BOX_MEMBER_INDEX,
          sourceExprResultLine,
          nameHammer.addStep(hamuts, boxStructRefH.id, keywords.BOX_MEMBER_NAME.str))
    (storeNode, Vector.empty)
  }

  private def translateMundaneLocalMutate(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    sourceExprResultLine: ExpressionH[KindHT],
    varId: IVarNameI[cI]
  ): (ExpressionH[KindHT], Vector[ExpressionI]) = {
    val local = locals.get(varId).get
    vassert(!locals.unstackifiedVars.contains(local.id))
    val newStoreNode =
        LocalStoreH(
          local,
          sourceExprResultLine,
          nameHammer.translateFullName(hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, varId)))
    (newStoreNode, Vector.empty)
  }
}
