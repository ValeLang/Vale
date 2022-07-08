package dev.vale.simplifying

import dev.vale.finalast.{BoolH, FloatH, InlineH, IntH, KindH, NeverH, PrototypeH, ReferenceH, RuntimeSizedArrayDefinitionHT, RuntimeSizedArrayHT, StaticSizedArrayDefinitionHT, StaticSizedArrayHT, StrH, VoidH, YonderH}
import dev.vale.typing.Hinputs
import dev.vale.typing.ast.PrototypeT
import dev.vale.typing.types.{BoolT, BorrowT, CoordT, FloatT, IntT, InterfaceTT, KindT, NeverT, OverloadSetT, OwnT, RuntimeSizedArrayTT, ShareT, StaticSizedArrayTT, StrT, StructTT, VoidT, WeakT}
import dev.vale.{Interner, Keywords, vfail, finalast => m}
import dev.vale.finalast._
import dev.vale.typing._
import dev.vale.typing.names.CitizenTemplateNameT
//import dev.vale.typingpass.templata.FunctionHeaderT
import dev.vale.typing.types._

class TypeHammer(
    interner: Interner,
    keywords: Keywords,
    nameHammer: NameHammer,
    structHammer: StructHammer) {
  def translateKind(hinputs: Hinputs, hamuts: HamutsBox, tyype: KindT):
  (KindH) = {
    tyype match {
      case NeverT(fromBreak) => NeverH(fromBreak)
      case IntT(bits) => IntH(bits)
      case BoolT() => BoolH()
      case FloatT() => FloatH()
      case StrT() => StrH()
      case VoidT() => VoidH()
      case s @ StructTT(_) => structHammer.translateStructRef(hinputs, hamuts, s)

      case i @ InterfaceTT(_) => structHammer.translateInterfaceRef(hinputs, hamuts, i)

      case OverloadSetT(_, _) => VoidH()

      case a @ StaticSizedArrayTT(_, _, _, _) => translateStaticSizedArray(hinputs, hamuts, a)
      case a @ RuntimeSizedArrayTT(_, _) => translateRuntimeSizedArray(hinputs, hamuts, a)
    }
  }

  def translateReference(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      coord: CoordT):
  (ReferenceH[KindH]) = {
    val CoordT(ownership, innerType) = coord;
    val location = {
      (ownership, innerType) match {
        case (OwnT, _) => YonderH
        case (BorrowT, _) => YonderH
        case (WeakT, _) => YonderH
        case (ShareT, OverloadSetT(_, _)) => InlineH
//        case (ShareT, PackTT(_, _)) => InlineH
//        case (ShareT, TupleTT(_, _)) => InlineH
//        case (ShareT, StructTT(FullNameT(_, Vector(), CitizenNameT(CitizenTemplateNameT("Tup"), _)))) => InlineH
        case (ShareT, VoidT() | IntT(_) | BoolT() | FloatT() | NeverT(_)) => InlineH
        case (ShareT, StrT()) => YonderH
        case (ShareT, _) => YonderH
      }
    }
    val (innerH) = translateKind(hinputs, hamuts, innerType);
    (ReferenceH(Conversions.evaluateOwnership(ownership), location, innerH))
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
      ssaTT: StaticSizedArrayTT):
  StaticSizedArrayHT = {
    hamuts.staticSizedArrays.get(ssaTT) match {
      case Some(x) => x.kind
      case None => {
        val name = nameHammer.translateFullName(hinputs, hamuts, ssaTT.getName(interner, keywords))
        val StaticSizedArrayTT(_, mutabilityT, variabilityT, memberType) = ssaTT
        val memberReferenceH = translateReference(hinputs, hamuts, memberType)
        val mutability = Conversions.evaluateMutability(mutabilityT)
        val variability = Conversions.evaluateVariability(variabilityT)
        val definition = StaticSizedArrayDefinitionHT(name, ssaTT.size, mutability, variability, memberReferenceH)
        hamuts.addStaticSizedArray(ssaTT, definition)
        StaticSizedArrayHT(name)
      }
    }
  }

  def translateRuntimeSizedArray(hinputs: Hinputs, hamuts: HamutsBox, rsaTT: RuntimeSizedArrayTT): RuntimeSizedArrayHT = {
    hamuts.runtimeSizedArrays.get(rsaTT) match {
      case Some(x) => x.kind
      case None => {
        val nameH = nameHammer.translateFullName(hinputs, hamuts, rsaTT.getName(interner, keywords))
        val RuntimeSizedArrayTT(mutabilityT, memberType) = rsaTT
        val memberReferenceH = translateReference(hinputs, hamuts, memberType)
        val mutability = Conversions.evaluateMutability(mutabilityT)
        //    val variability = Conversions.evaluateVariability(variabilityT)
        val definition = RuntimeSizedArrayDefinitionHT(nameH, mutability, memberReferenceH)
        hamuts.addRuntimeSizedArray(rsaTT, definition)
        RuntimeSizedArrayHT(nameH)
      }
    }
  }

  def translatePrototype(
    hinputs: Hinputs, hamuts: HamutsBox,
    prototype2: PrototypeT):
  (PrototypeH) = {
    val PrototypeT(fullName2, returnType2) = prototype2;
    val (paramsTypesH) = translateReferences(hinputs, hamuts, prototype2.paramTypes)
    val (returnTypeH) = translateReference(hinputs, hamuts, returnType2)
    val (fullNameH) = nameHammer.translateFullName(hinputs, hamuts, fullName2)
    val prototypeH = PrototypeH(fullNameH, paramsTypesH, returnTypeH)
    (prototypeH)
  }

}
