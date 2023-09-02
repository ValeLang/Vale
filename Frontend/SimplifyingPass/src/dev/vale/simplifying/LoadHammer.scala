package dev.vale.simplifying

import dev.vale._
import dev.vale.finalast._
import dev.vale.finalast._
import dev.vale.instantiating.ast._
import dev.vale.postparsing.RegionTemplataType

class LoadHammer(
    keywords: Keywords,
    typeHammer: TypeHammer,
    nameHammer: NameHammer,
    structHammer: StructHammer,
    expressionHammer: ExpressionHammer) {

  def translateLoad(
      hinputs: HinputsI,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
      locals: LocalsBox,
      load2: SoftLoadIE):
  (ExpressionH[KindHT], Vector[ExpressionI]) = {
    val SoftLoadIE(sourceExpr2, targetOwnership, _) = load2

    val (loadedAccessH, sourceDeferreds) =
      sourceExpr2 match {
        case LocalLookupIE(ReferenceLocalVariableI(varId, variability, reference), _) => {
          // DO NOI SUBMIT combine this with below
          val combinedTargetOwnership = targetOwnership
//            (targetOwnership, sourceRegion) match {
//              case (OwnI, _) => OwnI
//              case (BorrowI, RegionTemplata(true)) => MutableBorrowI
//              case (BorrowI, RegionTemplata(false)) => ImmutableBorrowI
//              case (ShareI, RegionTemplata(true)) => MutableShareI
//              case (ShareI, RegionTemplata(false)) => ImmutableShareI
//              case (WeakI, _) => vimpl()
//            }
          translateMundaneLocalLoad(hinputs, hamuts, currentFunctionHeader, locals, varId, reference, combinedTargetOwnership)
        }
        case LocalLookupIE(AddressibleLocalVariableI(varId, variability, localReference2), _) => {
          // DO NOI SUBMIT combine this with below
          val combinedTargetOwnership = vimpl()
//            (targetOwnership, sourceRegion) match {
//              case (OwnI, _) => OwnI
//              case (BorrowI, RegionTemplata(true)) => MutableBorrowI
//              case (BorrowI, RegionTemplata(false)) => ImmutableBorrowI
//              case (ShareI, RegionTemplata(true)) => MutableShareI
//              case (ShareI, RegionTemplata(false)) => ImmutableShareI
//              case (WeakI, _) => vimpl()
//            }
          translateAddressibleLocalLoad(hinputs, hamuts, currentFunctionHeader, locals, varId, variability, localReference2, combinedTargetOwnership)
        }
        case ReferenceMemberLookupIE(_,structExpr2, memberName, memberType2, _) => {
//          val sourceRegion: ITemplata[RegionTemplataType] = vimpl()
          // DO NOI SUBMIT combine this with below
//          val combinedTargetOwnership =
//            (targetOwnership, sourceRegion) match {
//              case (OwnI, _) => OwnI
//              case (BorrowI, RegionTemplata(true)) => MutableBorrowI
//              case (BorrowI, RegionTemplata(false)) => ImmutableBorrowI
//              case (ShareI, RegionTemplata(true)) => MutableShareI
//              case (ShareI, RegionTemplata(false)) => ImmutableShareI
//              case (WeakI, _) => vimpl()
//            }
          translateMundaneMemberLoad(hinputs, hamuts, currentFunctionHeader, locals, structExpr2, memberType2, memberName, targetOwnership)
        }
        case AddressMemberLookupIE(structExpr2, memberName, memberType2, _) => {
//          val sourceRegion: ITemplataI[RegionTemplataType] = vimpl()
          // DO NOI SUBMIT combine this with below
          val combinedTargetOwnership = vimpl()
//          val combinedTargetOwnership =
//            (targetOwnership, sourceRegion) match {
//              case (OwnI, _) => OwnI
//              case (BorrowI, RegionTemplata(true)) => MutableBorrowI
//              case (BorrowI, RegionTemplata(false)) => ImmutableBorrowI
//              case (ShareI, RegionTemplata(true)) => MutableShareI
//              case (ShareI, RegionTemplata(false)) => ImmutableShareI
//              case (WeakI, _) => vimpl()
//            }
          translateAddressibleMemberLoad(hinputs, hamuts, currentFunctionHeader, locals, structExpr2, memberName, memberType2, combinedTargetOwnership)
        }
        case RuntimeSizedArrayLookupIE(arrayExpr2, indexExpr2, _, _) => {
//          val sourceRegion: ITemplataI[RegionTemplataType] = vimpl()
          // DO NOI SUBMIT combine this with below
          val combinedTargetOwnership = targetOwnership
//            (targetOwnership, sourceRegion) match {
//              case (OwnI, _) => OwnI
//              case (BorrowI, RegionTemplata(true)) => MutableBorrowI
//              case (BorrowI, RegionTemplata(false)) => ImmutableBorrowI
//              case (ShareI, RegionTemplata(true)) => MutableShareI
//              case (ShareI, RegionTemplata(false)) => ImmutableShareI
//              case (WeakI, _) => vimpl()
//            }
          translateMundaneRuntimeSizedArrayLoad(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2, indexExpr2, combinedTargetOwnership)
        }
        case StaticSizedArrayLookupIE(_, arrayExpr2, indexExpr2, _, _) => {
//          val sourceRegion: ITemplataI[RegionTemplataType] = vimpl()
          // DO NOI SUBMIT combine this with below
//          val combinedTargetOwnership =
//            (targetOwnership, sourceRegion) match {
//              case (OwnI, _) => OwnI
//              case (BorrowI, RegionTemplata(true)) => MutableBorrowI
//              case (BorrowI, RegionTemplata(false)) => ImmutableBorrowI
//              case (ShareI, RegionTemplata(true)) => MutableShareI
//              case (ShareI, RegionTemplata(false)) => ImmutableShareI
//              case (WeakI, _) => vimpl()
//            }
          translateMundaneStaticSizedArrayLoad(
            hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2, indexExpr2, targetOwnership)
        }
      }

    // Note how we return the deferreds upward, see Hammer doc for why.

    (loadedAccessH, sourceDeferreds)
  }

  private def translateMundaneRuntimeSizedArrayLoad(
      hinputs: HinputsI,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
      locals: LocalsBox,
      arrayExpr2: ReferenceExpressionIE,
      indexExpr2: ReferenceExpressionIE,
      targetOwnershipI: OwnershipI,
  ): (ExpressionH[KindHT], Vector[ExpressionI]) = {
    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipI)

    val (arrayResultLine, arrayDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);
    val arrayAccess = arrayResultLine.expectRuntimeSizedArrayAccess()

    val (indexExprResultLine, indexDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, indexExpr2);
    val indexAccess = indexExprResultLine.expectIntAccess()

    vassert(
      targetOwnership == MutableBorrowH ||
      targetOwnership == ImmutableBorrowH ||
        targetOwnership == ImmutableShareH ||
        targetOwnership == MutableShareH)

    val rsa = hamuts.getRuntimeSizedArray(arrayAccess.resultType.kind)
    val expectedElementType = rsa.elementType
    val resultType = {
      val location =
        (targetOwnership, expectedElementType.location) match {
          case (ImmutableBorrowH, _) => YonderH
          case (MutableBorrowH, _) => YonderH
          case (OwnH, location) => location
          case (MutableShareH, location) => location
          case (ImmutableShareH, location) => location
        }
      CoordH(targetOwnership, location, expectedElementType.kind)
    }

    // We're storing into a regular reference element of an array.
    val loadedNodeH =
        RuntimeSizedArrayLoadH(
          arrayAccess,
          indexAccess,
          targetOwnership,
          expectedElementType,
          resultType)

    (loadedNodeH, arrayDeferreds ++ indexDeferreds)
  }

  private def translateMundaneStaticSizedArrayLoad(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    arrayExpr2: ReferenceExpressionIE,
    indexExpr2: ReferenceExpressionIE,
    targetOwnershipI: OwnershipI,
  ): (ExpressionH[KindHT], Vector[ExpressionI]) = {
    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipI)

    val (arrayResultLine, arrayDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);
    val arrayAccess = arrayResultLine.expectStaticSizedArrayAccess()

    val (indexExprResultLine, indexDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, indexExpr2);
    val indexAccess = indexExprResultLine.expectIntAccess()

    vassert(
      targetOwnership == finalast.MutableBorrowH ||
        targetOwnership == finalast.ImmutableBorrowH ||
        targetOwnership == finalast.MutableShareH ||
        targetOwnership == finalast.ImmutableShareH)

    val ssa = hamuts.getStaticSizedArray(arrayAccess.resultType.kind)
    val expectedElementType = ssa.elementType
    val resultType = {
      val location =
        (targetOwnership, expectedElementType.location) match {
          case (MutableBorrowH | ImmutableBorrowH, _) => YonderH
          case (OwnH, location) => location
          case (ImmutableShareH | MutableShareH, location) => location
        }
      CoordH(targetOwnership, location, expectedElementType.kind)
    }

    // We're storing into a regular reference element of an array.
    val loadedNodeH =
        StaticSizedArrayLoadH(
          arrayAccess,
          indexAccess,
          targetOwnership,
          expectedElementType,
          ssa.size,
          resultType)

    (loadedNodeH, arrayDeferreds ++ indexDeferreds)
  }

  private def translateAddressibleMemberLoad(
      hinputs: HinputsI,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
      locals: LocalsBox,
      structExpr2: ReferenceExpressionIE,
      memberName: IVarNameI[cI],
      expectedType2: CoordI[cI],
      targetOwnershipI: OwnershipI,
  ): (ExpressionH[KindHT], Vector[ExpressionI]) = {
    val (structResultLine, structDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, structExpr2);

    val structIT =
      structExpr2.result.kind match {
        case sr @ StructIT(_) => sr
//        case TupleIT(_, sr) => sr
//        case PackIT(_, sr) => sr
      }
    val structDefI = structHammer.lookupStruct(hinputs, hamuts, structIT)
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

    val boxInStructCoord = CoordH(vimpl(/*BorrowH*/), YonderH, boxStructRefH)

    // We're storing into a struct's member that is a box. The stack is also
    // pointing at this box. First, get the box, then mutate what's inside.
    var varFullNameH =
    nameHammer.translateFullName(
      hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, memberName))
    val loadBoxNode =
        MemberLoadH(
          structResultLine.expectStructAccess(),
          memberIndex,
          boxInStructCoord,
          boxInStructCoord,
          varFullNameH)

    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipI)
    val loadResultType =
      CoordH(
        targetOwnership,
        boxedTypeH.location,
        boxedTypeH.kind)
    val loadedNodeH =
        MemberLoadH(
          loadBoxNode.expectStructAccess(),
          LetHammer.BOX_MEMBER_INDEX,
          boxedTypeH,
          loadResultType,
          nameHammer.addStep(hamuts, boxStructRefH.id, keywords.BOX_MEMBER_NAME.str))
    (loadedNodeH, structDeferreds)
  }

  private def translateMundaneMemberLoad(
      hinputs: HinputsI,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
      locals: LocalsBox,
      structExpr2: ReferenceExpressionIE,
      expectedMemberCoord: CoordI[cI],
      memberName: IVarNameI[cI],
//      resultCoord: Coord,
      targetOwnershipI: OwnershipI,
  ): (ExpressionH[KindHT], Vector[ExpressionI]) = {
    val (structResultLine, structDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, structExpr2);

    val (expectedMemberTypeH) =
      typeHammer.translateCoord(hinputs, hamuts, expectedMemberCoord);
//    val (resultTypeH) =
//      typeHammer.translateReference(hinputs, hamuts, resultCoord);

    val structIT =
      structExpr2.result.kind match {
        case sr @ StructIT(_) => sr
//        case TupleIT(_, sr) => sr
//        case PackIT(_, sr) => sr
      }
    val structDefI = structHammer.lookupStruct(hinputs, hamuts, structIT)
    val memberIndex = structDefI.members.indexWhere(_.name == memberName)
    vassert(memberIndex >= 0)

    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipI)
    val loadResultType = CoordH(targetOwnership, expectedMemberTypeH.location, expectedMemberTypeH.kind)

    // We're loading from a regular reference member of a struct.
    val loadedNode =
        MemberLoadH(
          structResultLine.expectStructAccess(),
          memberIndex,
          expectedMemberTypeH,
          loadResultType,
          nameHammer.translateFullName(hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, memberName)))
    (loadedNode, structDeferreds)
  }

  def translateAddressibleLocalLoad(
      hinputs: HinputsI,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
      locals: LocalsBox,
      varId: IVarNameI[cI],
      variability: VariabilityI,
      localReference2: CoordI[cI],
      targetOwnershipI: OwnershipI,
  ): (ExpressionH[KindHT], Vector[ExpressionI]) = {
    val local = locals.get(varId).get
    vassert(!locals.unstackifiedVars.contains(local.id))

    val (localTypeH) =
      typeHammer.translateCoord(hinputs, hamuts, localReference2);
    val (boxStructRefH) =
      structHammer.makeBox(hinputs, hamuts, variability, localReference2, localTypeH)
    vassert(local.typeH.kind == boxStructRefH)

    // This means we're trying to load from a local variable that holds a box.
    // We need to load the box, then mutate its contents.
    val varNameH =
    nameHammer.translateFullName(
      hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, varId))
    val loadBoxNode =
        LocalLoadH(
          local,
          vimpl(/*BorrowH*/),
          varNameH)

    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipI)
    val loadResultType = CoordH(targetOwnership, localTypeH.location, localTypeH.kind)

    val loadedNode =
        MemberLoadH(
          loadBoxNode.expectStructAccess(),
          LetHammer.BOX_MEMBER_INDEX,
          localTypeH,
          loadResultType,
          nameHammer.addStep(hamuts, boxStructRefH.id, keywords.BOX_MEMBER_NAME.str))
    (loadedNode, Vector.empty)
  }

  def translateMundaneLocalLoad(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    varId: IVarNameI[cI],
    localType: CoordI[cI],
    targetOwnershipI: OwnershipI,
  ): (ExpressionH[KindHT], Vector[ExpressionI]) = {
    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipI)

    val local = locals.get(varId) match {
      case Some(x) => x
      case None => {
        vfail("wot")
      }
    }
    vassert(!locals.unstackifiedVars.contains(local.id))

//    val (expectedTypeH) =
//      typeHammer.translateCoord(hinputs, hamuts, expectedType2);
//    vassert(localType == local.typeH)

    val loadedNode =
        LocalLoadH(
          local,
          targetOwnership,
          nameHammer.translateFullName(
            hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, varId)))
    (loadedNode, Vector.empty)
  }

  def translateLocalAddress(
      hinputs: HinputsI,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
      locals: LocalsBox,
      lookup2: LocalLookupIE):
  (ExpressionH[KindHT]) = {
    val LocalLookupIE(localVar, _) = lookup2;
    vimpl()

    val local = locals.get(localVar.name).get
    vassert(!locals.unstackifiedVars.contains(local.id))
    val (boxStructRefH) =
      structHammer.makeBox(hinputs, hamuts, localVar.variability, localVar.collapsedCoord, local.typeH)

    // This means we're trying to load from a local variable that holds a box.
    // We need to load the box, then mutate its contents.
    val loadBoxNode =
      LocalLoadH(
        local,
        vimpl(/*BorrowH*/),
        nameHammer.translateFullName(hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, localVar.name)))
    loadBoxNode
  }

  // In this, we're basically taking an addressible lookup, in other words,
  // a reference to a box.
  def translateMemberAddress(
      hinputs: HinputsI,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
      locals: LocalsBox,
      lookup2: AddressMemberLookupIE):
  (ExpressionH[KindHT], Vector[ExpressionI]) = {
    val AddressMemberLookupIE(structExpr2, memberName, resultType2, _) = lookup2;

    val (structResultLine, structDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, structExpr2);

    val structIT =
      structExpr2.result.kind match {
        case sr @ StructIT(_) => sr
//        case TupleIT(_, sr) => sr
//        case PackIT(_, sr) => sr
      }
    val structDefI = structHammer.lookupStruct(hinputs, hamuts, structIT)
    val memberIndex = structDefI.members.indexWhere(_.name == memberName)
    vassert(memberIndex >= 0)
    val member2 =
      structDefI.members(memberIndex) match {
        case n @ StructMemberI(name, variability, tyype) => n
      }

    val variability = member2.variability
    vassert(variability == VaryingI, "Expected varying for member " + memberName) // curious

    val boxedType2 = member2.tyype.expectAddressMember().reference

    val (boxedTypeH) =
      typeHammer.translateCoord(hinputs, hamuts, boxedType2);

    val (boxStructRefH) =
      structHammer.makeBox(hinputs, hamuts, variability, boxedType2, boxedTypeH)

    // We expect a borrow because structs never own boxes, they only borrow them
    val expectedStructBoxMemberType = CoordH(vimpl(/*BorrowH*/), YonderH, boxStructRefH)

    val loadResultType =
      CoordH(
        // Boxes are either owned or borrowed. We only own boxes from locals,
        // and we're loading from a struct here, so we're getting a borrow to the
        // box from the struct.
        vimpl(/*BorrowH*/),
        YonderH,
        boxStructRefH)

    // We're storing into a struct's member that is a box. The stack is also
    // pointing at this box. First, get the box, then mutate what's inside.
    val loadBoxNode =
      MemberLoadH(
        structResultLine.expectStructAccess(),
        memberIndex,
        expectedStructBoxMemberType,
        loadResultType,
        nameHammer.translateFullName(hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, memberName)))

    (loadBoxNode, structDeferreds)
  }

  def getBorrowedLocation(memberType: CoordH[KindHT]) = {
    (memberType.ownership, memberType.location) match {
      case (OwnH, _) => YonderH
      case (ImmutableBorrowH | MutableBorrowH, _) => YonderH
      case (MutableShareH | ImmutableShareH, location) => location
    }
  }
}
