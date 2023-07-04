package dev.vale.simplifying

import dev.vale.{Keywords, finalast, vassert, vfail, vimpl}
import dev.vale.finalast.{BorrowH, ExpressionH, KindHT, LocalLoadH, MemberLoadH, OwnH, CoordH, RuntimeSizedArrayLoadH, ShareH, StaticSizedArrayLoadH, YonderH}
import dev.vale.typing.Hinputs
import dev.vale.typing.ast.{AddressMemberLookupTE, ExpressionT, FunctionHeaderT, LocalLookupTE, ReferenceExpressionTE, ReferenceMemberLookupTE, RuntimeSizedArrayLookupTE, SoftLoadTE, StaticSizedArrayLookupTE}
import dev.vale.typing.env.{AddressibleLocalVariableT, ReferenceLocalVariableT}
import dev.vale.typing.names.{IdT, IVarNameT}
import dev.vale.typing.types._
import dev.vale.finalast._
import dev.vale.typing.{types => t, _}
import dev.vale.typing.ast._
import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.names.IVarNameT
import dev.vale.typing.types._

class LoadHammer(
    keywords: Keywords,
    typeHammer: TypeHammer,
    nameHammer: NameHammer,
    structHammer: StructHammer,
    expressionHammer: ExpressionHammer) {

  def translateLoad(
      hinputs: Hinputs,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      load2: SoftLoadTE):
  (ExpressionH[KindHT], Vector[ExpressionT]) = {
    val SoftLoadTE(sourceExpr2, targetOwnership) = load2

    val (loadedAccessH, sourceDeferreds) =
      sourceExpr2 match {
        case LocalLookupTE(_,ReferenceLocalVariableT(varId, variability, reference)) => {
          translateMundaneLocalLoad(hinputs, hamuts, currentFunctionHeader, locals, varId, reference, targetOwnership)
        }
        case LocalLookupTE(_,AddressibleLocalVariableT(varId, variability, localReference2)) => {
          translateAddressibleLocalLoad(hinputs, hamuts, currentFunctionHeader, locals, varId, variability, localReference2, targetOwnership)
        }
        case ReferenceMemberLookupTE(_,structExpr2, memberName, memberType2, _) => {
          translateMundaneMemberLoad(hinputs, hamuts, currentFunctionHeader, locals, structExpr2, memberType2, memberName, targetOwnership)
        }
        case AddressMemberLookupTE(_,structExpr2, memberName, memberType2, _) => {
          translateAddressibleMemberLoad(hinputs, hamuts, currentFunctionHeader, locals, structExpr2, memberName, memberType2, targetOwnership)
        }
        case RuntimeSizedArrayLookupTE(_, arrayExpr2, _, indexExpr2, _) => {
          translateMundaneRuntimeSizedArrayLoad(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2, indexExpr2, targetOwnership)
        }
        case StaticSizedArrayLookupTE(_, arrayExpr2, _, indexExpr2, _) => {
          translateMundaneStaticSizedArrayLoad(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2, indexExpr2, targetOwnership)
        }
      }

    // Note how we return the deferreds upward, see Hammer doc for why.

    (loadedAccessH, sourceDeferreds)
  }

  private def translateMundaneRuntimeSizedArrayLoad(
      hinputs: Hinputs,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      arrayExpr2: ReferenceExpressionTE,
      indexExpr2: ReferenceExpressionTE,
      targetOwnershipT: OwnershipT,
  ): (ExpressionH[KindHT], Vector[ExpressionT]) = {
    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipT)

    val (arrayResultLine, arrayDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);
    val arrayAccess = arrayResultLine.expectRuntimeSizedArrayAccess()

    val (indexExprResultLine, indexDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, indexExpr2);
    val indexAccess = indexExprResultLine.expectIntAccess()

    vassert(
      targetOwnership == BorrowH ||
        targetOwnership == ShareH)

    val rsa = hamuts.getRuntimeSizedArray(arrayAccess.resultType.kind)
    val expectedElementType = rsa.elementType
    val resultType = {
      val location =
        (targetOwnership, expectedElementType.location) match {
          case (BorrowH, _) => YonderH
          case (OwnH, location) => location
          case (ShareH, location) => location
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
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    arrayExpr2: ReferenceExpressionTE,
    indexExpr2: ReferenceExpressionTE,
    targetOwnershipT: OwnershipT,
  ): (ExpressionH[KindHT], Vector[ExpressionT]) = {
    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipT)

    val (arrayResultLine, arrayDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);
    val arrayAccess = arrayResultLine.expectStaticSizedArrayAccess()

    val (indexExprResultLine, indexDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, indexExpr2);
    val indexAccess = indexExprResultLine.expectIntAccess()

    vassert(targetOwnership == finalast.BorrowH || targetOwnership == finalast.ShareH)

    val ssa = hamuts.getStaticSizedArray(arrayAccess.resultType.kind)
    val expectedElementType = ssa.elementType
    val resultType = {
      val location =
        (targetOwnership, expectedElementType.location) match {
          case (BorrowH, _) => YonderH
          case (OwnH, location) => location
          case (ShareH, location) => location
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
      hinputs: Hinputs,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      structExpr2: ReferenceExpressionTE,
      memberName: IVarNameT,
      expectedType2: CoordT,
      targetOwnershipT: OwnershipT,
  ): (ExpressionH[KindHT], Vector[ExpressionT]) = {
    val (structResultLine, structDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, structExpr2);

    val structTT =
      structExpr2.result.coord.kind match {
        case sr @ StructTT(_) => sr
//        case TupleTT(_, sr) => sr
//        case PackTT(_, sr) => sr
      }
    val structDefT = structHammer.lookupStruct(hinputs, hamuts, structTT)
    val memberIndex = structDefT.members.indexWhere(_.name == memberName)
    vassert(memberIndex >= 0)
    val member2 =
      structDefT.members(memberIndex) match {
        case n @ NormalStructMemberT(name, variability, tyype) => n
        case VariadicStructMemberT(name, tyype) => vimpl()
      }

    val variability = member2.variability

    val boxedType2 = member2.tyype.expectAddressMember().reference

    val (boxedTypeH) =
      typeHammer.translateCoord(hinputs, hamuts, boxedType2);

    val (boxStructRefH) =
      structHammer.makeBox(hinputs, hamuts, variability, boxedType2, boxedTypeH)

    val boxInStructCoord = CoordH(BorrowH, YonderH, boxStructRefH)

    // We're storing into a struct's member that is a box. The stack is also
    // pointing at this box. First, get the box, then mutate what's inside.
    var varFullNameH =
    nameHammer.translateFullName(
      hinputs, hamuts, currentFunctionHeader.id.addStep(memberName))
    val loadBoxNode =
        MemberLoadH(
          structResultLine.expectStructAccess(),
          memberIndex,
          boxInStructCoord,
          boxInStructCoord,
          varFullNameH)

    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipT)
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
          nameHammer.addStep(hamuts, boxStructRefH.fullName, keywords.BOX_MEMBER_NAME.str))
    (loadedNodeH, structDeferreds)
  }

  private def translateMundaneMemberLoad(
      hinputs: Hinputs,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      structExpr2: ReferenceExpressionTE,
      expectedMemberCoord: CoordT,
      memberName: IVarNameT,
//      resultCoord: Coord,
      targetOwnershipT: OwnershipT,
  ): (ExpressionH[KindHT], Vector[ExpressionT]) = {
    val (structResultLine, structDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, structExpr2);

    val (expectedMemberTypeH) =
      typeHammer.translateCoord(hinputs, hamuts, expectedMemberCoord);
//    val (resultTypeH) =
//      typeHammer.translateReference(hinputs, hamuts, resultCoord);

    val structTT =
      structExpr2.result.coord.kind match {
        case sr @ StructTT(_) => sr
//        case TupleTT(_, sr) => sr
//        case PackTT(_, sr) => sr
      }
    val structDefT = structHammer.lookupStruct(hinputs, hamuts, structTT)
    val memberIndex = structDefT.members.indexWhere(_.name == memberName)
    vassert(memberIndex >= 0)

    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipT)
    val loadResultType = CoordH(targetOwnership, expectedMemberTypeH.location, expectedMemberTypeH.kind)

    // We're loading from a regular reference member of a struct.
    val loadedNode =
        MemberLoadH(
          structResultLine.expectStructAccess(),
          memberIndex,
          expectedMemberTypeH,
          loadResultType,
          nameHammer.translateFullName(hinputs, hamuts, currentFunctionHeader.id.addStep(memberName)))
    (loadedNode, structDeferreds)
  }

  def translateAddressibleLocalLoad(
      hinputs: Hinputs,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      varId: IVarNameT,
      variability: VariabilityT,
      localReference2: CoordT,
      targetOwnershipT: OwnershipT,
  ): (ExpressionH[KindHT], Vector[ExpressionT]) = {
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
      hinputs, hamuts, currentFunctionHeader.id.addStep(varId))
    val loadBoxNode =
        LocalLoadH(
          local,
          finalast.BorrowH,
          varNameH)

    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipT)
    val loadResultType = CoordH(targetOwnership, localTypeH.location, localTypeH.kind)

    val loadedNode =
        MemberLoadH(
          loadBoxNode.expectStructAccess(),
          LetHammer.BOX_MEMBER_INDEX,
          localTypeH,
          loadResultType,
          nameHammer.addStep(hamuts, boxStructRefH.fullName, keywords.BOX_MEMBER_NAME.str))
    (loadedNode, Vector.empty)
  }

  def translateMundaneLocalLoad(
      hinputs: Hinputs,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      varId: IVarNameT,
      expectedType2: CoordT,
      targetOwnershipT: OwnershipT,
  ): (ExpressionH[KindHT], Vector[ExpressionT]) = {
    val targetOwnership = Conversions.evaluateOwnership(targetOwnershipT)

    val local = locals.get(varId) match {
      case Some(x) => x
      case None => {
        vfail("wot")
      }
    }
    vassert(!locals.unstackifiedVars.contains(local.id))

    val (expectedTypeH) =
      typeHammer.translateCoord(hinputs, hamuts, expectedType2);
    vassert(expectedTypeH == local.typeH)

    val loadedNode =
        LocalLoadH(
          local,
          targetOwnership,
          nameHammer.translateFullName(
            hinputs, hamuts, currentFunctionHeader.id.addStep(varId)))
    (loadedNode, Vector.empty)
  }

  def translateLocalAddress(
      hinputs: Hinputs,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      lookup2: LocalLookupTE):
  (ExpressionH[KindHT]) = {
    val LocalLookupTE(_,localVar) = lookup2;

    val local = locals.get(localVar.name).get
    vassert(!locals.unstackifiedVars.contains(local.id))
    val (boxStructRefH) =
      structHammer.makeBox(hinputs, hamuts, localVar.variability, localVar.coord, local.typeH)

    // This means we're trying to load from a local variable that holds a box.
    // We need to load the box, then mutate its contents.
    val loadBoxNode =
      LocalLoadH(
        local,
        finalast.BorrowH,
        nameHammer.translateFullName(hinputs, hamuts, currentFunctionHeader.id.addStep(localVar.name)))
    loadBoxNode
  }

  // In this, we're basically taking an addressible lookup, in other words,
  // a reference to a box.
  def translateMemberAddress(
      hinputs: Hinputs,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      lookup2: AddressMemberLookupTE):
  (ExpressionH[KindHT], Vector[ExpressionT]) = {
    val AddressMemberLookupTE(_,structExpr2, memberName, resultType2, _) = lookup2;

    val (structResultLine, structDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, structExpr2);

    val structTT =
      structExpr2.result.coord.kind match {
        case sr @ StructTT(_) => sr
//        case TupleTT(_, sr) => sr
//        case PackTT(_, sr) => sr
      }
    val structDefT = structHammer.lookupStruct(hinputs, hamuts, structTT)
    val memberIndex = structDefT.members.indexWhere(_.name == memberName)
    vassert(memberIndex >= 0)
    val member2 =
      structDefT.members(memberIndex) match {
        case n @ NormalStructMemberT(name, variability, tyype) => n
        case VariadicStructMemberT(name, tyype) => vimpl()
      }

    val variability = member2.variability
    vassert(variability == VaryingT, "Expected varying for member " + memberName) // curious

    val boxedType2 = member2.tyype.expectAddressMember().reference

    val (boxedTypeH) =
      typeHammer.translateCoord(hinputs, hamuts, boxedType2);

    val (boxStructRefH) =
      structHammer.makeBox(hinputs, hamuts, variability, boxedType2, boxedTypeH)

    // We expect a borrow because structs never own boxes, they only borrow them
    val expectedStructBoxMemberType = CoordH(finalast.BorrowH, YonderH, boxStructRefH)

    val loadResultType =
      CoordH(
        // Boxes are either owned or borrowed. We only own boxes from locals,
        // and we're loading from a struct here, so we're getting a borrow to the
        // box from the struct.
        BorrowH,
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
        nameHammer.translateFullName(hinputs, hamuts, currentFunctionHeader.id.addStep(memberName)))

    (loadBoxNode, structDeferreds)
  }

  def getBorrowedLocation(memberType: CoordH[KindHT]) = {
    (memberType.ownership, memberType.location) match {
      case (OwnH, _) => YonderH
      case (BorrowH, _) => YonderH
      case (ShareH, location) => location
    }
  }
}
