package net.verdagon.vale.hammer

import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal._
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.templata.FunctionHeaderT
import net.verdagon.vale.templar.types._
import net.verdagon.vale.vfail

object TypeHammer {
  def translateMembers(hinputs: Hinputs, hamuts: HamutsBox, structName: FullNameT[INameT], members: List[StructMemberT]):
  (List[StructMemberH]) = {
    members match {
      case Nil => Nil
      case headMember2 :: tailMembers2 => {
        val (headMemberH) = translateMember(hinputs, hamuts, structName, headMember2)
        val (tailMembersH) = translateMembers(hinputs, hamuts, structName, tailMembers2)
        (headMemberH :: tailMembersH)
      }
    }
  }

  def translateMember(hinputs: Hinputs, hamuts: HamutsBox, structName: FullNameT[INameT], member2: StructMemberT):
  (StructMemberH) = {
    val (memberH) =
      member2.tyype match {
        case ReferenceMemberTypeT(coord) => {
          TypeHammer.translateReference(hinputs, hamuts, coord)
        }
        case AddressMemberTypeT(coord) => {
          val (referenceH) =
            TypeHammer.translateReference(hinputs, hamuts, coord)
          val (boxStructRefH) =
            StructHammer.makeBox(hinputs, hamuts, member2.variability, coord, referenceH)
          // The stack owns the box, closure structs just borrow it.
          (ReferenceH(m.BorrowH, YonderH, ReadwriteH, boxStructRefH))
        }
      }
    StructMemberH(
      NameHammer.translateFullName(hinputs, hamuts, structName.addStep(member2.name)),
      Conversions.evaluateVariability(member2.variability),
      memberH)
  }

//
//  def translateType(hinputs: Hinputs, hamuts: HamutsBox, tyype: BaseType2):
//  (BaseTypeH) = {
//    tyype match {
//      case Addressible2(innerType) => {
//        val (pointerH) = translatePointer(hinputs, hamuts, currentFunctionHeader, innerType)
//        (AddressibleH(pointerH))
//      }
//      case Coord(ownership, innerType) => {
//        val (pointerH) = translate(hinputs, hamuts, currentFunctionHeader, innerType)
//        (PointerH(ownership, pointerH))
//      }
//    }
//  }

  //  def translatePointer(tyype: Coord): PointerH = {
  //  }

  def translateKind(hinputs: Hinputs, hamuts: HamutsBox, tyype: KindT):
  (KindH) = {
    tyype match {
      case NeverT() => NeverH()
      case IntT(bits) => IntH(bits)
      case BoolT() => BoolH()
      case FloatT() => FloatH()
      case StrT() => StrH()
      case VoidT() => ProgramH.emptyTupleStructRef
      case s @ StructRefT(_) => StructHammer.translateStructRef(hinputs, hamuts, s)

      case i @ InterfaceRefT(_) => StructHammer.translateInterfaceRef(hinputs, hamuts, i)

//      // A Closure2 is really just a struct ref under the hood. The dinstinction is only meaningful
//      // to the Templar.
//      case OrdinaryClosure2(_, handleStructRef, prototype) => translate(hinputs, hamuts, currentFunctionHeader, handleStructRef)
//      case TemplatedClosure2(_, handleStructRef, terry) => translate(hinputs, hamuts, currentFunctionHeader, handleStructRef)
      case OverloadSet(_, _, understructRef2) => {
        StructHammer.translateStructRef(hinputs, hamuts, understructRef2)
      }

      // A PackT2 is really just a struct ref under the hood. The dinstinction is only meaningful
      // to the Templar.
      case p @ PackTT(_, underlyingStruct) => StructHammer.translateStructRef(hinputs, hamuts, underlyingStruct)
      case p @ TupleTT(_, underlyingStruct) => StructHammer.translateStructRef(hinputs, hamuts, underlyingStruct)
      case a @ StaticSizedArrayTT(_, _) => translateStaticSizedArray(hinputs, hamuts, a)
      case a @ RuntimeSizedArrayTT(_) => translateRuntimeSizedArray(hinputs, hamuts, a)
    }
  }

  def translateReference(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      coord: CoordT):
  (ReferenceH[KindH]) = {
    val CoordT(ownership, permission, innerType) = coord;
    val location = {
      (ownership, innerType) match {
        case (OwnT, _) => YonderH
        case (ConstraintT, _) => YonderH
        case (WeakT, _) => YonderH
        case (ShareT, OverloadSet(_, _, _)) => InlineH
        case (ShareT, PackTT(_, _)) => InlineH
        case (ShareT, TupleTT(_, _)) => InlineH
        case (ShareT, StructRefT(FullNameT(_, _, TupleNameT(_)))) => InlineH
        case (ShareT, VoidT()) => InlineH
        case (ShareT, IntT(_)) => InlineH
        case (ShareT, BoolT()) => InlineH
        case (ShareT, FloatT()) => InlineH
        case (ShareT, NeverT()) => InlineH
        case (ShareT, StrT()) => YonderH
        case (ShareT, _) => YonderH
      }
    }
    val permissionH = permission match {
      case ReadwriteT => ReadwriteH
      case ReadonlyT => ReadonlyH
    }
    val (innerH) = translateKind(hinputs, hamuts, innerType);
    (ReferenceH(Conversions.evaluateOwnership(ownership), location, permissionH, innerH))
  }

  def translateReferences(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      references2: List[CoordT]):
  (List[ReferenceH[KindH]]) = {
    references2 match {
      case Nil => Nil
      case headReference2 :: tailPointers2 => {
        val (headPointerH) = translateReference(hinputs, hamuts, headReference2)
        val (tailPointersH) = translateReferences(hinputs, hamuts, tailPointers2)
        (headPointerH :: tailPointersH)
      }
    }
  }

//  def checkReference(baseTypeH: BaseTypeH): ReferenceH = {
//    baseTypeH match {
//      case AddressibleH(_) => vfail("Expected a pointer, was an addressible!")
//      case p @ ReferenceH(_, _) => p
//    }
//  }

  def checkConversion(expected: ReferenceH[KindH], actual: ReferenceH[KindH]): Unit = {
    if (actual != expected) {
      vfail("Expected a " + expected + " but was a " + actual);
    }
  }

  def translateStaticSizedArray(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      type2: StaticSizedArrayTT):
  StaticSizedArrayTH = {
    val name = NameHammer.translateFullName(hinputs, hamuts, type2.name)
    val StaticSizedArrayTT(_, RawArrayTT(memberType, mutabilityT, variabilityT)) = type2
    val memberReferenceH = TypeHammer.translateReference(hinputs, hamuts, memberType)
    val mutability = Conversions.evaluateMutability(mutabilityT)
    val variability = Conversions.evaluateVariability(variabilityT)
    val definition = StaticSizedArrayDefinitionTH(name, type2.size, RawArrayTH(mutability, variability, memberReferenceH))
    hamuts.addStaticSizedArray(definition)
    StaticSizedArrayTH(name)
  }

  def translateRuntimeSizedArray(hinputs: Hinputs, hamuts: HamutsBox, type2: RuntimeSizedArrayTT): RuntimeSizedArrayTH = {
    val nameH = NameHammer.translateFullName(hinputs, hamuts, type2.name)
    val RuntimeSizedArrayTT(RawArrayTT(memberType, mutabilityT, variabilityT)) = type2
    val memberReferenceH = TypeHammer.translateReference(hinputs, hamuts, memberType)
    val mutability = Conversions.evaluateMutability(mutabilityT)
    val variability = Conversions.evaluateVariability(variabilityT)
    val definition = RuntimeSizedArrayDefinitionTH(nameH, RawArrayTH(mutability, variability, memberReferenceH))
    hamuts.addRuntimeSizedArray(definition)
    RuntimeSizedArrayTH(nameH)
  }
}
