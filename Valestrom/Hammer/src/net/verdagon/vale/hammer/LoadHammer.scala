package net.verdagon.vale.hammer

import net.verdagon.vale.hammer.ExpressionHammer.translate
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal.{BorrowH, ShareH, Variability => _, Varying => _, _}
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.templar.{types => t}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.{AddressibleLocalVariable2, ReferenceLocalVariable2}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{vassert, vfail}

object LoadHammer {

  def translateLoad(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      locals: LocalsBox,
      load2: SoftLoad2):
  (ExpressionH[ReferendH], List[Expression2]) = {
    val SoftLoad2(sourceExpr2, targetOwnership) = load2

    val (loadedAccessH, sourceDeferreds) =
      sourceExpr2 match {
        case LocalLookup2(_,ReferenceLocalVariable2(varId, variability, reference), varType2) => {
          vassert(reference == varType2)
          translateMundaneLocalLoad(hinputs, hamuts, locals, varId, reference, targetOwnership)
        }
        case LocalLookup2(_,AddressibleLocalVariable2(varId, variability, localReference2), varType2) => {
          vassert(localReference2 == varType2)
          translateAddressibleLocalLoad(hinputs, hamuts, locals, varId, variability, localReference2, targetOwnership)
        }
        case ReferenceMemberLookup2(_,structExpr2, memberName, memberType2) => {
          translateMundaneMemberLoad(hinputs, hamuts, locals, structExpr2, memberType2, memberName, targetOwnership)
        }
        case AddressMemberLookup2(_,structExpr2, memberName, memberType2) => {
          translateAddressibleMemberLoad(hinputs, hamuts, locals, structExpr2, memberName, memberType2, targetOwnership)
        }
        case UnknownSizeArrayLookup2(_,arrayExpr2, arrayType, indexExpr2) => {
          translateMundaneUnknownSizeArrayLoad(hinputs, hamuts, locals, arrayExpr2, indexExpr2, targetOwnership)
        }
        case ArraySequenceLookup2(_,arrayExpr2, arrayType, indexExpr2) => {
          translateMundaneKnownSizeArrayLoad(hinputs, hamuts, locals, arrayExpr2, indexExpr2, targetOwnership)
        }
      }

    // Note how we return the deferreds upward, see Hammer doc for why.

    (loadedAccessH, sourceDeferreds)
  }

  private def translateMundaneUnknownSizeArrayLoad(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      locals: LocalsBox,
      arrayExpr2: ReferenceExpression2,
      indexExpr2: ReferenceExpression2,
      targetOwnershipT: t.Ownership
  ): (ExpressionH[ReferendH], List[Expression2]) = {
    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipT)

    val (arrayResultLine, arrayDeferreds) =
      translate(hinputs, hamuts, locals, arrayExpr2);
    val arrayAccess = arrayResultLine.expectUnknownSizeArrayAccess()

    val (indexExprResultLine, indexDeferreds) =
      translate(hinputs, hamuts, locals, indexExpr2);
    val indexAccess = indexExprResultLine.expectIntAccess()

    vassert(targetOwnership == BorrowH || targetOwnership == ShareH)

    // We're storing into a regular reference element of an array.
    val loadedNodeH =
        UnknownSizeArrayLoadH(
          arrayAccess,
          indexAccess,
          targetOwnership)

    (loadedNodeH, arrayDeferreds ++ indexDeferreds)
  }

  private def translateMundaneKnownSizeArrayLoad(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    locals: LocalsBox,
    arrayExpr2: ReferenceExpression2,
    indexExpr2: ReferenceExpression2,
    targetOwnershipT: t.Ownership
  ): (ExpressionH[ReferendH], List[Expression2]) = {
    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipT)

    val (arrayResultLine, arrayDeferreds) =
      translate(hinputs, hamuts, locals, arrayExpr2);
    val arrayAccess = arrayResultLine.expectKnownSizeArrayAccess()

    val (indexExprResultLine, indexDeferreds) =
      translate(hinputs, hamuts, locals, indexExpr2);
    val indexAccess = indexExprResultLine.expectIntAccess()

    vassert(targetOwnership == m.BorrowH || targetOwnership == m.ShareH)

    // We're storing into a regular reference element of an array.
    val loadedNodeH =
        KnownSizeArrayLoadH(
          arrayAccess,
          indexAccess,
          targetOwnership)

    (loadedNodeH, arrayDeferreds ++ indexDeferreds)
  }

  private def translateAddressibleMemberLoad(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      locals: LocalsBox,
      structExpr2: ReferenceExpression2,
      memberName: FullName2[IVarName2],
      expectedType2: Coord,
      targetOwnershipT: t.Ownership
  ): (ExpressionH[ReferendH], List[Expression2]) = {
    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipT)

    val (structResultLine, structDeferreds) =
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

    val boxInStructCoord = ReferenceH(BorrowH, YonderH, boxStructRefH)

    // The above box is borrowed by the struct.
    // We want to borrow it too.
    val desiredOwnership = BorrowH

    // We're storing into a struct's member that is a box. The stack is also
    // pointing at this box. First, get the box, then mutate what's inside.
    var varFullNameH = NameHammer.translateFullName(hinputs, hamuts, memberName)
    val loadBoxNode =
        MemberLoadH(
          structResultLine.expectStructAccess(),
          memberIndex,
          desiredOwnership,
          boxInStructCoord,
          varFullNameH)
    val loadedNodeH =
        MemberLoadH(
          loadBoxNode.expectStructAccess(),
          StructHammer.BOX_MEMBER_INDEX,
          targetOwnership,
          boxedTypeH,
          NameHammer.addStep(hamuts, boxStructRefH.fullName, StructHammer.BOX_MEMBER_NAME))
    (loadedNodeH, structDeferreds)
  }

  private def translateMundaneMemberLoad(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      locals: LocalsBox,
      structExpr2: ReferenceExpression2,
      expectedMemberType2: Coord,
      memberName: FullName2[IVarName2],
      targetOwnershipT: t.Ownership
  ): (ExpressionH[ReferendH], List[Expression2]) = {
    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipT)

    val (structResultLine, structDeferreds) =
      translate(hinputs, hamuts, locals, structExpr2);

    val (expectedMemberTypeH) =
      TypeHammer.translateReference(hinputs, hamuts, expectedMemberType2);

    val structRef2 =
      structExpr2.resultRegister.reference.referend match {
        case sr @ StructRef2(_) => sr
        case TupleT2(_, sr) => sr
        case PackT2(_, sr) => sr
      }
    val structDef2 = hinputs.lookupStruct(structRef2)
    val memberIndex = structDef2.members.indexWhere(member => structDef2.fullName.addStep(member.name) == memberName)
    vassert(memberIndex >= 0)

    // We're loading from a regular reference member of a struct.
    val loadedNode =
        MemberLoadH(
          structResultLine.expectStructAccess(),
          memberIndex,
          targetOwnership,
          expectedMemberTypeH,
          NameHammer.translateFullName(hinputs, hamuts, memberName))
    (loadedNode, structDeferreds)
  }

  def translateAddressibleLocalLoad(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      locals: LocalsBox,
      varId: FullName2[IVarName2],
      variability: Variability,
      localReference2: Coord,
      targetOwnershipT: t.Ownership
  ): (ExpressionH[ReferendH], List[Expression2]) = {
    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipT)

    val local = locals.get(varId).get
    vassert(!locals.unstackifiedVars.contains(local.id))

    val (localTypeH) =
      TypeHammer.translateReference(hinputs, hamuts, localReference2);
    val (boxStructRefH) =
      StructHammer.makeBox(hinputs, hamuts, variability, localReference2, localTypeH)
    vassert(local.typeH.kind == boxStructRefH)

    // This means we're trying to load from a local variable that holds a box.
    // We need to load the box, then mutate its contents.
    val varNameH = NameHammer.translateFullName(hinputs, hamuts, varId)
    val loadBoxNode =
        LocalLoadH(
          local,
          m.BorrowH,
          varNameH)

    val loadedNode =
        MemberLoadH(
          loadBoxNode.expectStructAccess(),
          StructHammer.BOX_MEMBER_INDEX,
          targetOwnership,
          localTypeH,
          NameHammer.addStep(hamuts, boxStructRefH.fullName, StructHammer.BOX_MEMBER_NAME))
    (loadedNode, List())
  }

  def translateMundaneLocalLoad(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      locals: LocalsBox,
      varId: FullName2[IVarName2],
      expectedType2: Coord,
      targetOwnershipT: t.Ownership
  ): (ExpressionH[ReferendH], List[Expression2]) = {
    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipT)


    val local = locals.get(varId) match {
      case Some(x) => x
      case None => {
        vfail("wot")
      }
    }
    vassert(!locals.unstackifiedVars.contains(local.id))

    val (expectedTypeH) =
      TypeHammer.translateReference(hinputs, hamuts, expectedType2);
    vassert(expectedTypeH == local.typeH)

    val loadedNode =
        LocalLoadH(
          local,
          targetOwnership,
          NameHammer.translateFullName(hinputs, hamuts, varId))
    (loadedNode, List())
  }

  def translateLocalAddress(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      locals: LocalsBox,
      lookup2: LocalLookup2):
  (ExpressionH[ReferendH]) = {
    val LocalLookup2(_,localVar, type2) = lookup2;
    vassert(type2 == localVar.reference)

    val local = locals.get(localVar.id).get
    vassert(!locals.unstackifiedVars.contains(local.id))
    val (boxStructRefH) =
      StructHammer.makeBox(hinputs, hamuts, localVar.variability, localVar.reference, local.typeH)

    // This means we're trying to load from a local variable that holds a box.
    // We need to load the box, then mutate its contents.
    val loadBoxNode =
      LocalLoadH(
        local,
        m.BorrowH,
        NameHammer.translateFullName(hinputs, hamuts, localVar.id))
    loadBoxNode
  }

  def translateMemberAddress(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      locals: LocalsBox,
      lookup2: AddressMemberLookup2):
  (ExpressionH[ReferendH], List[Expression2]) = {
    val AddressMemberLookup2(_,structExpr2, memberName, resultType2) = lookup2;

    val (structResultLine, structDeferreds) =
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
    vassert(variability == Varying) // curious

    val boxedType2 = member2.tyype.expectAddressMember().reference

    val (boxedTypeH) =
      TypeHammer.translateReference(hinputs, hamuts, boxedType2);

    val (boxStructRefH) =
      StructHammer.makeBox(hinputs, hamuts, variability, boxedType2, boxedTypeH)

    // We expect a borrow because structs never own boxes, they only borrow them
    val expectedStructBoxMemberType = ReferenceH(m.BorrowH, YonderH, boxStructRefH)

    // We're storing into a struct's member that is a box. The stack is also
    // pointing at this box. First, get the box, then mutate what's inside.
    val loadBoxNode =
      MemberLoadH(
        structResultLine.expectStructAccess(),
        memberIndex,
        m.BorrowH,
        expectedStructBoxMemberType,
        NameHammer.translateFullName(hinputs, hamuts, memberName))

    (loadBoxNode, structDeferreds)
  }

  def getBorrowedLocation(memberType: ReferenceH[ReferendH]) = {
    (memberType.ownership, memberType.location) match {
      case (OwnH, _) => YonderH
      case (BorrowH, _) => YonderH
      case (ShareH, location) => location
    }
  }
}
