package net.verdagon.vale.hammer

import net.verdagon.vale.metal._
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.templar.{Hinputs, _}
import net.verdagon.vale.templar.names.{CitizenNameT, CitizenTemplateNameT, FullNameT, INameT}
//import net.verdagon.vale.templar.templata.FunctionHeaderT
import net.verdagon.vale.templar.types._
import net.verdagon.vale.vfail

object TypeHammer {
  def translateMembers(hinputs: Hinputs, hamuts: HamutsBox, structName: FullNameT[INameT], members: Vector[StructMemberT]):
  (Vector[StructMemberH]) = {
    members.map(translateMember(hinputs, hamuts, structName, _))
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
          (ReferenceH(m.PointerH, YonderH, ReadwriteH, boxStructRefH))
        }
      }
    StructMemberH(
      NameHammer.translateFullName(hinputs, hamuts, structName.addStep(member2.name)),
      Conversions.evaluateVariability(member2.variability),
      memberH)
  }

  def translateKind(hinputs: Hinputs, hamuts: HamutsBox, tyype: KindT):
  (KindH) = {
    tyype match {
      case NeverT() => NeverH()
      case IntT(bits) => IntH(bits)
      case BoolT() => BoolH()
      case FloatT() => FloatH()
      case StrT() => StrH()
      case VoidT() => ProgramH.emptyTupleStructRef
      case s @ StructTT(_) => StructHammer.translateStructRef(hinputs, hamuts, s)

      case i @ InterfaceTT(_) => StructHammer.translateInterfaceRef(hinputs, hamuts, i)

      case OverloadSet(_, _, understructTT) => {
        StructHammer.translateStructRef(hinputs, hamuts, understructTT)
      }

      case a @ StaticSizedArrayTT(_, _, _, _) => translateStaticSizedArray(hinputs, hamuts, a)
      case a @ RuntimeSizedArrayTT(_, _) => translateRuntimeSizedArray(hinputs, hamuts, a)
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
        case (PointerT, _) => YonderH
        case (BorrowT, _) => YonderH
        case (WeakT, _) => YonderH
        case (ShareT, OverloadSet(_, _, _)) => InlineH
//        case (ShareT, PackTT(_, _)) => InlineH
//        case (ShareT, TupleTT(_, _)) => InlineH
        case (ShareT, StructTT(FullNameT(_, Vector(), CitizenNameT(CitizenTemplateNameT("Tup"), _)))) => InlineH
        case (ShareT, VoidT() | IntT(_) | BoolT() | FloatT() | NeverT()) => InlineH
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
      references2: Vector[CoordT]):
  (Vector[ReferenceH[KindH]]) = {
    references2.map(translateReference(hinputs, hamuts, _))
  }

  def checkConversion(expected: ReferenceH[KindH], actual: ReferenceH[KindH]): Unit = {
    if (actual != expected) {
      vfail("Expected a " + expected + " but was a " + actual);
    }
  }

  def translateStaticSizedArray(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      type2: StaticSizedArrayTT):
  StaticSizedArrayHT = {
    val name = NameHammer.translateFullName(hinputs, hamuts, type2.name)
    val StaticSizedArrayTT(_, mutabilityT, variabilityT, memberType) = type2
    val memberReferenceH = TypeHammer.translateReference(hinputs, hamuts, memberType)
    val mutability = Conversions.evaluateMutability(mutabilityT)
    val variability = Conversions.evaluateVariability(variabilityT)
    val definition = StaticSizedArrayDefinitionTH(name, type2.size, mutability, variability, memberReferenceH)
    hamuts.addStaticSizedArray(definition)
    StaticSizedArrayHT(name)
  }

  def translateRuntimeSizedArray(hinputs: Hinputs, hamuts: HamutsBox, type2: RuntimeSizedArrayTT): RuntimeSizedArrayHT = {
    val nameH = NameHammer.translateFullName(hinputs, hamuts, type2.name)
    val RuntimeSizedArrayTT(mutabilityT, memberType) = type2
    val memberReferenceH = TypeHammer.translateReference(hinputs, hamuts, memberType)
    val mutability = Conversions.evaluateMutability(mutabilityT)
//    val variability = Conversions.evaluateVariability(variabilityT)
    val definition = RuntimeSizedArrayDefinitionTH(nameH, mutability, memberReferenceH)
    hamuts.addRuntimeSizedArray(definition)
    RuntimeSizedArrayHT(nameH)
  }
}
