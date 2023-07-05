package dev.vale.simplifying

import dev.vale.finalast.{BoolHT, FloatHT, InlineH, IntHT, KindHT, NeverHT, PrototypeH, CoordH, RuntimeSizedArrayDefinitionHT, RuntimeSizedArrayHT, StaticSizedArrayDefinitionHT, StaticSizedArrayHT, StrHT, VoidHT, YonderH}
import dev.vale.typing.Hinputs
import dev.vale.typing.ast.PrototypeT
import dev.vale.typing.types._
import dev.vale.{Interner, Keywords, vfail, vwat, finalast => m}
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
  (KindHT) = {
    tyype match {
      case NeverT(fromBreak) => NeverHT(fromBreak)
      case IntT(bits) => IntHT(bits)
      case BoolT() => BoolHT()
      case FloatT() => FloatHT()
      case StrT() => StrHT()
      case VoidT() => VoidHT()
      case s @ StructTT(_) => structHammer.translateStructT(hinputs, hamuts, s)

      case i @ InterfaceTT(_) => structHammer.translateInterface(hinputs, hamuts, i)

      case OverloadSetT(_, _) => VoidHT()

      case a @ contentsStaticSizedArrayTT(_, _, _, _) => translateStaticSizedArray(hinputs, hamuts, a)
      case a @ contentsRuntimeSizedArrayTT(_, _) => translateRuntimeSizedArray(hinputs, hamuts, a)
      case KindPlaceholderT(fullName) => {
        // this is a bit of a hack. sometimes lambda templates like to remember their original
        // defining generics, and we dont translate those in the instantiator, so it can later
        // use them to find those original templates.
        // because of that, they make their way into the hammer, right here.
        // long term, we should probably find a way to tostring templatas cleanly rather than
        // converting them to hammer first.
        // See DMPOGN for why these make it into the hammer.
        VoidHT()
      }
    }
  }

  def translateCoord(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      coord: CoordT):
  (CoordH[KindHT]) = {
    val CoordT(ownership, _, innerType) = coord;
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
    (CoordH(Conversions.evaluateOwnership(ownership), location, innerH))
  }

  def translateCoords(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      references2: Vector[CoordT]):
  (Vector[CoordH[KindHT]]) = {
    references2.map(translateCoord(hinputs, hamuts, _))
  }

  def checkConversion(expected: CoordH[KindHT], actual: CoordH[KindHT]): Unit = {
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
        val name = nameHammer.translateFullName(hinputs, hamuts, ssaTT.name)
        val contentsStaticSizedArrayTT(_, mutabilityT, variabilityT, memberType) = ssaTT
        val memberReferenceH = translateCoord(hinputs, hamuts, memberType)
        val mutability = Conversions.evaluateMutabilityTemplata(mutabilityT)
        val variability = Conversions.evaluateVariabilityTemplata(variabilityT)
        val size = Conversions.evaluateIntegerTemplata(ssaTT.size)
        val definition = StaticSizedArrayDefinitionHT(name, size, mutability, variability, memberReferenceH)
        hamuts.addStaticSizedArray(ssaTT, definition)
        StaticSizedArrayHT(name)
      }
    }
  }

  def translateRuntimeSizedArray(hinputs: Hinputs, hamuts: HamutsBox, rsaTT: RuntimeSizedArrayTT): RuntimeSizedArrayHT = {
    hamuts.runtimeSizedArrays.get(rsaTT) match {
      case Some(x) => x.kind
      case None => {
        val nameH = nameHammer.translateFullName(hinputs, hamuts, rsaTT.name)
        val contentsRuntimeSizedArrayTT(mutabilityT, memberType) = rsaTT
        val memberReferenceH = translateCoord(hinputs, hamuts, memberType)
        val mutability = Conversions.evaluateMutabilityTemplata(mutabilityT)
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
    val (paramsTypesH) = translateCoords(hinputs, hamuts, prototype2.paramTypes)
    val (returnTypeH) = translateCoord(hinputs, hamuts, returnType2)
    val (fullNameH) = nameHammer.translateFullName(hinputs, hamuts, fullName2)
    val prototypeH = PrototypeH(fullNameH, paramsTypesH, returnTypeH)
    (prototypeH)
  }

}
