package net.verdagon.vale.hammer

import net.verdagon.vale.hammer.ExpressionHammer.{ translate, translateDeferreds}
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal.{BorrowH => _, Variability => _, _}
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.{AddressibleLocalVariable2, ReferenceLocalVariable2}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.vassert

object MutateHammer {

  def translateMutate(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      locals: LocalsBox,
      mutate2: Mutate2):
  (ExpressionH[ReferendH]) = {
    val Mutate2(destinationExpr2, sourceExpr2) = mutate2

    val (sourceExprResultLine, sourceDeferreds) =
      translate(hinputs, hamuts, locals, sourceExpr2);
    val (sourceResultPointerTypeH) =
      TypeHammer.translateReference(hinputs, hamuts, sourceExpr2.resultRegister.reference)

    val (oldValueAccess, destinationDeferreds) =
      destinationExpr2 match {
        case LocalLookup2(ReferenceLocalVariable2(varId, variability, reference), varType2) => {
          translateMundaneLocalMutate(hinputs, hamuts, locals, sourceExprResultLine, varId)
        }
        case LocalLookup2(AddressibleLocalVariable2(varId, variability, reference), varType2) => {
          translateAddressibleLocalMutate(hinputs, hamuts, locals, sourceExprResultLine, sourceResultPointerTypeH, varId, variability, reference)
        }
        case ReferenceMemberLookup2(structExpr2, memberName, memberType2) => {
          translateMundaneMemberMutate(hinputs, hamuts, locals, sourceExprResultLine, structExpr2, memberName)
        }
        case AddressMemberLookup2(structExpr2, memberName, memberType2) => {
          translateAddressibleMemberMutate(hinputs, hamuts, locals, sourceExprResultLine, structExpr2, memberName)
        }
        case ArraySequenceLookup2(arrayExpr2, arrayType, indexExpr2) => {
          translateMundaneKnownSizeArrayMutate(hinputs, hamuts, locals, sourceExprResultLine, arrayExpr2, indexExpr2)
        }
        case UnknownSizeArrayLookup2(arrayExpr2, arrayType, indexExpr2) => {
          translateMundaneUnknownSizeArrayMutate(hinputs, hamuts, locals, sourceExprResultLine, arrayExpr2, indexExpr2)
        }
      }

    translateDeferreds(hinputs, hamuts, locals, oldValueAccess, sourceDeferreds ++ destinationDeferreds)
  }

  private def translateMundaneUnknownSizeArrayMutate(
                                                      hinputs: Hinputs,
                                                      hamuts: HamutsBox,
                                                      locals: LocalsBox,
                                                      sourceExprResultLine: ExpressionH[ReferendH],
                                                      arrayExpr2: ReferenceExpression2,
                                                      indexExpr2: ReferenceExpression2
  ): (ExpressionH[ReferendH], List[Expression2]) = {
    val (destinationResultLine, destinationDeferreds) =
      translate(hinputs, hamuts, locals, arrayExpr2);
    val (indexExprResultLine, indexDeferreds) =
      translate(hinputs, hamuts, locals, indexExpr2);
    // We're storing into a regular reference element of an array.
    val storeNode =
        UnknownSizeArrayStoreH(
          destinationResultLine.expectUnknownSizeArrayAccess(),
          indexExprResultLine.expectIntAccess(),
          sourceExprResultLine)

    (storeNode, destinationDeferreds ++ indexDeferreds)
  }

  private def translateMundaneKnownSizeArrayMutate(
                                                    hinputs: Hinputs,
                                                    hamuts: HamutsBox,
                                                    locals: LocalsBox,
                                                    sourceExprResultLine: ExpressionH[ReferendH],
                                                    arrayExpr2: ReferenceExpression2,
                                                    indexExpr2: ReferenceExpression2
  ): (ExpressionH[ReferendH], List[Expression2]) = {
    val (destinationResultLine, destinationDeferreds) =
      translate(hinputs, hamuts, locals, arrayExpr2);
    val (indexExprResultLine, indexDeferreds) =
      translate(hinputs, hamuts, locals, indexExpr2);
    // We're storing into a regular reference element of an array.
    val storeNode =
        KnownSizeArrayStoreH(
          destinationResultLine.expectKnownSizeArrayAccess(),
          indexExprResultLine.expectIntAccess(),
          sourceExprResultLine)

    (storeNode, destinationDeferreds ++ indexDeferreds)
  }

  private def translateAddressibleMemberMutate(
                                                hinputs: Hinputs,
                                                hamuts: HamutsBox,
                                                locals: LocalsBox,
                                                sourceExprResultLine: ExpressionH[ReferendH],
                                                structExpr2: ReferenceExpression2,
                                                memberName: FullName2[IVarName2]
  ): (ExpressionH[ReferendH], List[Expression2]) = {
    val (destinationResultLine, destinationDeferreds) =
      translate(hinputs, hamuts, locals, structExpr2);

    val structRef2 =
      structExpr2.resultRegister.reference.referend match {
        case sr @ StructRef2(_) => sr
        case TupleT2(_, sr) => sr
        case PackT2(_, sr) => sr
      }
    val structDef2 = hinputs.lookupStruct(structRef2)
    val memberIndex = structDef2.members.indexWhere(member => structDef2.fullName.addStep(member.name) == memberName)
    vassert(memberIndex >= 0)
    val member2 = structDef2.members(memberIndex)

    val variability = member2.variability

    val boxedType2 = member2.tyype.expectAddressMember().reference

    val (boxedTypeH) =
      TypeHammer.translateReference(hinputs, hamuts, boxedType2);

    val (boxStructRefH) =
      StructHammer.makeBox(hinputs, hamuts, variability, boxedType2, boxedTypeH)

    // Remember, structs can never own boxes, they only borrow them
    val expectedStructBoxMemberType = ReferenceH(m.BorrowH, boxStructRefH)
    val expectedBorrowBoxResultType = ReferenceH(m.BorrowH, boxStructRefH)

    // We're storing into a struct's member that is a box. The stack is also
    // pointing at this box. First, get the box, then mutate what's inside.
    val nameH = NameHammer.translateFullName(hinputs, hamuts, memberName)
    val loadBoxNode =
        MemberLoadH(
          destinationResultLine.expectStructAccess(),
          memberIndex,
          m.BorrowH,
          expectedStructBoxMemberType,
          expectedBorrowBoxResultType,
          nameH)
    val storeNode =
        MemberStoreH(
          boxedTypeH,
          loadBoxNode.expectStructAccess(),
          StructHammer.BOX_MEMBER_INDEX,
          sourceExprResultLine,
          nameH.addStep(StructHammer.BOX_MEMBER_NAME))
    (storeNode, destinationDeferreds)
  }

  private def translateMundaneMemberMutate(
                                            hinputs: Hinputs,
                                            hamuts: HamutsBox,
                                            locals: LocalsBox,
                                            sourceExprResultLine: ExpressionH[ReferendH],
                                            structExpr2: ReferenceExpression2,
                                            memberName: FullName2[IVarName2]
  ): (ExpressionH[ReferendH], List[Expression2]) = {
    val (destinationResultLine, destinationDeferreds) =
      translate(hinputs, hamuts, locals, structExpr2);

    val structRef2 =
      structExpr2.resultRegister.reference.referend match {
        case sr @ StructRef2(_) => sr
      }
    val structDef2 = hinputs.lookupStruct(structRef2)
    val memberIndex =
      structDef2.members
        .indexWhere(member => structDef2.fullName.addStep(member.name) == memberName)
    vassert(memberIndex >= 0)

    val structDefH = hamuts.structDefsByRef2(structRef2)

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
                                               locals: LocalsBox,
                                               sourceExprResultLine: ExpressionH[ReferendH],
                                               sourceResultPointerTypeH: ReferenceH[ReferendH],
                                               varId: FullName2[IVarName2],
                                               variability: Variability,
                                               reference: Coord
  ): (ExpressionH[ReferendH], List[Expression2]) = {
    val local = locals.get(varId).get
    val (boxStructRefH) =
      StructHammer.makeBox(hinputs, hamuts, variability, reference, sourceResultPointerTypeH)
    val expectedLocalBoxType = ReferenceH(m.OwnH, boxStructRefH)
    val expectedBorrowBoxResultType = ReferenceH(m.BorrowH, boxStructRefH)

    val structDefH = hamuts.structDefs.find(_.getRef == boxStructRefH).get

    // This means we're trying to mutate a local variable that holds a box.
    // We need to load the box, then mutate its contents.
    val nameH = NameHammer.translateFullName(hinputs, hamuts, varId)
    val loadBoxNode =
      LocalLoadH(
        local,
        m.BorrowH,
        nameH)
    val storeNode =
        MemberStoreH(
          structDefH.members.head.tyype,
          loadBoxNode.expectStructAccess(),
          StructHammer.BOX_MEMBER_INDEX,
          sourceExprResultLine,
          nameH.addStep(StructHammer.BOX_MEMBER_NAME))
    (storeNode, List())
  }

  private def translateMundaneLocalMutate(
                                           hinputs: Hinputs,
                                           hamuts: HamutsBox,
                                           locals: LocalsBox,
                                           sourceExprResultLine: ExpressionH[ReferendH],
                                           varId: FullName2[IVarName2]
  ): (ExpressionH[ReferendH], List[Expression2]) = {
    val local = locals.get(varId).get
    val newStoreNode =
        LocalStoreH(
          local,
          sourceExprResultLine,
          NameHammer.translateFullName(hinputs, hamuts, varId))
    (newStoreNode, List())
  }
}
