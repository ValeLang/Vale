package net.verdagon.vale.hammer

import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal._
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.vfail

object TypeHammer {
  def translateMembers(hinputs: Hinputs, hamuts: HamutsBox, structName: FullName2[IName2], members: List[StructMember2]):
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

  def translateMember(hinputs: Hinputs, hamuts: HamutsBox, structName: FullName2[IName2], member2: StructMember2):
  (StructMemberH) = {
    val (memberH) =
      member2.tyype match {
        case ReferenceMemberType2(coord) => {
          TypeHammer.translateReference(hinputs, hamuts, coord)
        }
        case AddressMemberType2(coord) => {
          val (referenceH) =
            TypeHammer.translateReference(hinputs, hamuts, coord)
          val (boxStructRefH) =
            StructHammer.makeBox(hinputs, hamuts, member2.variability, coord, referenceH)
          // The stack owns the box, closure structs just borrow it.
          (ReferenceH(m.BorrowH, boxStructRefH))
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
//        val (pointerH) = translatePointer(hinputs, hamuts, innerType)
//        (AddressibleH(pointerH))
//      }
//      case Coord(ownership, innerType) => {
//        val (pointerH) = translate(hinputs, hamuts, innerType)
//        (PointerH(ownership, pointerH))
//      }
//    }
//  }

  //  def translatePointer(tyype: Coord): PointerH = {
  //  }

  def translateKind(hinputs: Hinputs, hamuts: HamutsBox, tyype: Kind):
  (ReferendH) = {
    tyype match {
      case Never2() => NeverH()
      case Int2() => IntH()
      case Bool2() => BoolH()
      case Float2() => FloatH()
      case Str2() => StrH()
      case Void2() => ProgramH.emptyTupleStructRef
      case s @ StructRef2(_) => StructHammer.translateStructRef(hinputs, hamuts, s)

      case i @ InterfaceRef2(_) => StructHammer.translateInterfaceRef(hinputs, hamuts, i)

//      // A Closure2 is really just a struct ref under the hood. The dinstinction is only meaningful
//      // to the Templar.
//      case OrdinaryClosure2(_, handleStructRef, prototype) => translate(hinputs, hamuts, handleStructRef)
//      case TemplatedClosure2(_, handleStructRef, terry) => translate(hinputs, hamuts, handleStructRef)
      case OverloadSet(_, _, understructRef2) => {
        StructHammer.translateStructRef(hinputs, hamuts, understructRef2)
      }

      // A PackT2 is really just a struct ref under the hood. The dinstinction is only meaningful
      // to the Templar.
      case p @ PackT2(_, underlyingStruct) => StructHammer.translateStructRef(hinputs, hamuts, underlyingStruct)
      case p @ TupleT2(_, underlyingStruct) => StructHammer.translateStructRef(hinputs, hamuts, underlyingStruct)
      case a @ KnownSizeArrayT2(_, _) => translateKnownSizeArray(hinputs, hamuts, a)
      case a @ UnknownSizeArrayT2(_) => translateUnknownSizeArray(hinputs, hamuts, a)
    }
  }

  def translateReference(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      coord: Coord):
  (ReferenceH[ReferendH]) = {
    val Coord(ownership, innerType) = coord;
    val (innerH) = translateKind(hinputs, hamuts, innerType);
    (ReferenceH(Conversions.evaluateOwnership(ownership), innerH))
  }

  def translateReferences(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      references2: List[Coord]):
  (List[ReferenceH[ReferendH]]) = {
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

  def checkConversion(expected: ReferenceH[ReferendH], actual: ReferenceH[ReferendH]): Unit = {
    if (actual != expected) {
      vfail("Expected a " + expected + " but was a " + actual);
    }
  }

  def translateKnownSizeArray(hinputs: Hinputs, hamuts: HamutsBox, type2: KnownSizeArrayT2): KnownSizeArrayTH = {
    val name = NameHammer.translateFullName(hinputs, hamuts, type2.name)
    val memberReferenceH = TypeHammer.translateReference(hinputs, hamuts, type2.array.memberType)
    val mutability = Conversions.evaluateMutability(type2.array.mutability)
    KnownSizeArrayTH(name, type2.size, RawArrayTH(mutability, memberReferenceH))
  }

  def translateUnknownSizeArray(hinputs: Hinputs, hamuts: HamutsBox, type2: UnknownSizeArrayT2): UnknownSizeArrayTH = {
    val nameH = NameHammer.translateFullName(hinputs, hamuts, type2.name)
    val (memberReferenceH) = TypeHammer.translateReference(hinputs, hamuts, type2.array.memberType)
    val mutability = Conversions.evaluateMutability(type2.array.mutability)
    (UnknownSizeArrayTH(nameH, RawArrayTH(mutability, memberReferenceH)))
  }
}
