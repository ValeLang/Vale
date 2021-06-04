package net.verdagon.vale.hammer

import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal._
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.templata.FunctionHeader2
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

  def translateKind(hinputs: Hinputs, hamuts: HamutsBox, tyype: Kind):
  (KindH) = {
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
//      case OrdinaryClosure2(_, handleStructRef, prototype) => translate(hinputs, hamuts, currentFunctionHeader, handleStructRef)
//      case TemplatedClosure2(_, handleStructRef, terry) => translate(hinputs, hamuts, currentFunctionHeader, handleStructRef)
      case OverloadSet(_, _, understructRef2) => {
        StructHammer.translateStructRef(hinputs, hamuts, understructRef2)
      }

      // A PackT2 is really just a struct ref under the hood. The dinstinction is only meaningful
      // to the Templar.
      case p @ PackT2(_, underlyingStruct) => StructHammer.translateStructRef(hinputs, hamuts, underlyingStruct)
      case p @ TupleT2(_, underlyingStruct) => StructHammer.translateStructRef(hinputs, hamuts, underlyingStruct)
      case a @ StaticSizedArrayT2(_, _) => translateStaticSizedArray(hinputs, hamuts, a)
      case a @ RuntimeSizedArrayT2(_) => translateRuntimeSizedArray(hinputs, hamuts, a)
    }
  }

  def translateReference(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      coord: Coord):
  (ReferenceH[KindH]) = {
    val Coord(ownership, permission, innerType) = coord;
    val location = {
      (ownership, innerType) match {
        case (Own, _) => YonderH
        case (Constraint, _) => YonderH
        case (Weak, _) => YonderH
        case (Share, OverloadSet(_, _, _)) => InlineH
        case (Share, PackT2(_, _)) => InlineH
        case (Share, TupleT2(_, _)) => InlineH
        case (Share, StructRef2(FullName2(_, _, TupleName2(_)))) => InlineH
        case (Share, Void2()) => InlineH
        case (Share, Int2()) => InlineH
        case (Share, Bool2()) => InlineH
        case (Share, Float2()) => InlineH
        case (Share, Never2()) => InlineH
        case (Share, Str2()) => YonderH
        case (Share, _) => YonderH
      }
    }
    val permissionH = permission match {
      case Readwrite => ReadwriteH
      case Readonly => ReadonlyH
    }
    val (innerH) = translateKind(hinputs, hamuts, innerType);
    (ReferenceH(Conversions.evaluateOwnership(ownership), location, permissionH, innerH))
  }

  def translateReferences(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      references2: List[Coord]):
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
      type2: StaticSizedArrayT2):
  StaticSizedArrayTH = {
    val name = NameHammer.translateFullName(hinputs, hamuts, type2.name)
    val StaticSizedArrayT2(_, RawArrayT2(memberType, mutabilityT, variabilityT)) = type2
    val memberReferenceH = TypeHammer.translateReference(hinputs, hamuts, memberType)
    val mutability = Conversions.evaluateMutability(mutabilityT)
    val variability = Conversions.evaluateVariability(variabilityT)
    val definition = StaticSizedArrayDefinitionTH(name, type2.size, RawArrayTH(mutability, variability, memberReferenceH))
    hamuts.addStaticSizedArray(definition)
    StaticSizedArrayTH(name)
  }

  def translateRuntimeSizedArray(hinputs: Hinputs, hamuts: HamutsBox, type2: RuntimeSizedArrayT2): RuntimeSizedArrayTH = {
    val nameH = NameHammer.translateFullName(hinputs, hamuts, type2.name)
    val RuntimeSizedArrayT2(RawArrayT2(memberType, mutabilityT, variabilityT)) = type2
    val memberReferenceH = TypeHammer.translateReference(hinputs, hamuts, memberType)
    val mutability = Conversions.evaluateMutability(mutabilityT)
    val variability = Conversions.evaluateVariability(variabilityT)
    val definition = RuntimeSizedArrayDefinitionTH(nameH, RawArrayTH(mutability, variability, memberReferenceH))
    hamuts.addRuntimeSizedArray(definition)
    RuntimeSizedArrayTH(nameH)
  }
}
