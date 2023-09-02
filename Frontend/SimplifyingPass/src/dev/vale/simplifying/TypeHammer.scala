package dev.vale.simplifying

import dev.vale.finalast.{BoolHT, CoordH, FloatHT, InlineH, IntHT, KindHT, NeverHT, PrototypeH, RuntimeSizedArrayDefinitionHT, RuntimeSizedArrayHT, StaticSizedArrayDefinitionHT, StaticSizedArrayHT, StrHT, VoidHT, YonderH}
import dev.vale.{Interner, Keywords, vassert, vfail, vimpl, vregionmut, vwat, finalast => m}
import dev.vale.finalast._
import dev.vale.instantiating.ast._

class TypeHammer(
    interner: Interner,
    keywords: Keywords,
    nameHammer: NameHammer,
    structHammer: StructHammer) {
  def translateKind(hinputs: HinputsI, hamuts: HamutsBox, tyype: KindIT[cI]):
  (KindHT) = {
    tyype match {
      case NeverIT(fromBreak) => NeverHT(fromBreak)
      case IntIT(bits) => IntHT(bits)
      case BoolIT() => BoolHT()
      case FloatIT() => FloatHT()
      case StrIT() => StrHT()
      case VoidIT() => VoidHT()
      case s @ StructIT(_) => structHammer.translateStructI(hinputs, hamuts, s)

      case i @ InterfaceIT(_) => structHammer.translateInterface(hinputs, hamuts, i)

//      case OverloadSetI(_, _) => VoidHT()

      case a @ contentsStaticSizedArrayIT(_, _, _, _, _) => translateStaticSizedArray(hinputs, hamuts, a)
      case a @ contentsRuntimeSizedArrayIT(_, _, _) => translateRuntimeSizedArray(hinputs, hamuts, a)
//      case KindPlaceholderI(fullName) => {
//        // this is a bit of a hack. sometimes lambda templates like to remember their original
//        // defining generics, and we dont translate those in the instantiator, so it can later
//        // use them to find those original templates.
//        // because of that, they make their way into the hammer, right here.
//        // long term, we should probably find a way to tostring templatas cleanly rather than
//        // converting them to hammer first.
//        // See DMPOGN for why these make it into the hammer.
//        VoidHT()
//      }
    }
  }

  def translateRegion(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    region: RegionTemplataI[cI]):
  RegionH = {
    RegionH()
  }

  def translateCoord(
      hinputs: HinputsI,
      hamuts: HamutsBox,
      coord: CoordI[cI]):
  (CoordH[KindHT]) = {
    val CoordI(ownership, innerType) = coord;
    val location = {
      (ownership, innerType) match {
        case (OwnI, _) => YonderH
        case (ImmutableBorrowI | MutableBorrowI, _) => YonderH
        case (WeakI, _) => YonderH
//        case (ImmutableShareI | MutableShareI, OverloadSetI(_, _)) => InlineH
//        case (ShareI, PackIT(_, _)) => InlineH
//        case (ShareI, TupleIT(_, _)) => InlineH
//        case (ShareI, StructIT(FullNameI(_, Vector(), CitizenNameI(CitizenTemplateNameI("Tup"), _)))) => InlineH
        case (ImmutableShareI | MutableShareI, VoidIT() | IntIT(_) | BoolIT() | FloatIT() | NeverIT(_)) => InlineH
        case (ImmutableShareI | MutableShareI, StrIT()) => YonderH
        case (ImmutableShareI | MutableShareI, _) => YonderH
      }
    }
    val (innerH) = translateKind(hinputs, hamuts, innerType);
    (CoordH(Conversions.evaluateOwnership(ownership), location, innerH))
  }

  def translateCoords(
      hinputs: HinputsI,
      hamuts: HamutsBox,
      references2: Vector[CoordI[cI]]):
  (Vector[CoordH[KindHT]]) = {
    references2.map(translateCoord(hinputs, hamuts, _))
  }

  def checkConversion(expected: CoordH[KindHT], actual: CoordH[KindHT]): Unit = {
    if (actual != expected) {
      vfail("Expected a " + expected + " but was a " + actual);
    }
  }

  def translateStaticSizedArray(
      hinputs: HinputsI,
      hamuts: HamutsBox,
      ssaIT: StaticSizedArrayIT[cI]):
  StaticSizedArrayHT = {
    hamuts.staticSizedArrays.get(ssaIT) match {
      case Some(x) => x.kind
      case None => {
        val name = nameHammer.translateFullName(hinputs, hamuts, ssaIT.name)
        val contentsStaticSizedArrayIT(_, mutabilityI, variabilityI, memberType, arrRegion) = ssaIT
        vregionmut(arrRegion) // what do with arrRegion?
        val memberReferenceH = translateCoord(hinputs, hamuts, memberType.coord)
        val mutability = Conversions.evaluateMutabilityTemplata(mutabilityI)
        val variability = Conversions.evaluateVariabilityTemplata(variabilityI)
        val size = ssaIT.size
        val definition = StaticSizedArrayDefinitionHT(name, size, mutability, variability, memberReferenceH)
        hamuts.addStaticSizedArray(ssaIT, definition)
        StaticSizedArrayHT(name)
      }
    }
  }

  def translateRuntimeSizedArray(hinputs: HinputsI, hamuts: HamutsBox, rsaIT: RuntimeSizedArrayIT[cI]): RuntimeSizedArrayHT = {
    hamuts.runtimeSizedArrays.get(rsaIT) match {
      case Some(x) => x.kind
      case None => {
        val nameH = nameHammer.translateFullName(hinputs, hamuts, rsaIT.name)
        val contentsRuntimeSizedArrayIT(mutabilityI, memberType, arrRegion) = rsaIT
        vregionmut(arrRegion) // what do with arrRegion?
        val memberReferenceH = translateCoord(hinputs, hamuts, memberType.coord)
        val mutability = Conversions.evaluateMutabilityTemplata(mutabilityI)
        //    val variability = Conversions.evaluateVariability(variabilityI)
        val definition = RuntimeSizedArrayDefinitionHT(nameH, mutability, memberReferenceH)
        val result = RuntimeSizedArrayHT(nameH)
        // DO NOT SUBMIT a few options:
        // - do this for SSAs too
        // - nuke the way we were doing names for hamuts, make proper names finally
        hamuts.runtimeSizedArrays.values.find(_.kind == result) match {
          case Some(x) =>
          case None => hamuts.addRuntimeSizedArray(rsaIT, definition)
        }
        result
      }
    }
  }

  def translatePrototype(
    hinputs: HinputsI, hamuts: HamutsBox,
    prototype2: PrototypeI[cI]):
  (PrototypeH) = {
    val PrototypeI(fullName2, returnType2) = prototype2;
    val (paramsTypesH) = translateCoords(hinputs, hamuts, prototype2.paramTypes)
    val (returnTypeH) = translateCoord(hinputs, hamuts, returnType2)
    val (fullNameH) = nameHammer.translateFullName(hinputs, hamuts, fullName2)
    val prototypeH = PrototypeH(fullNameH, paramsTypesH, returnTypeH)
    (prototypeH)
  }

}
