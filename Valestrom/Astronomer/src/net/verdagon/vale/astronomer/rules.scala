package net.verdagon.vale.astronomer

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.CodeLocationS
import net.verdagon.vale.{vassert, vimpl, vwat}

import scala.collection.immutable.List

//sealed trait IRulexAR
//case class EqualsAR(left: IRulexAR, right: IRulexAR) extends IRulexAR
//case class IsaAR(sub: IRulexAR, suuper: IRulexAR) extends IRulexAR
//case class OrAR(alternatives: List[IRulexAR]) extends IRulexAR
//case class ComponentsAR(
//  // This is a TypedAR so that we can know the type, so we can know whether this is
//  // a kind components rule or a coord components rule.
//  container: TypedAR,
//  components: List[IRulexAR]
//) extends IRulexAR
//case class TypedAR(rune: Option[String], tyype: ITypeAR) extends IRulexAR
//case class TemplexAR(templex: ITemplexS) extends IRulexAR
//// This is for built-in parser functions, such as exists() or isBaseOf() etc.
//case class CallAR(name: String, args: List[IRulexAR]) extends IRulexAR {
//}
//
//sealed trait ITypeAR
//case object IntTypeAR extends ITypeAR
//case object BoolTypeAR extends ITypeAR
//case object OwnershipTypeAR extends ITypeAR
//case object MutabilityTypeAR extends ITypeAR
//case object PermissionTypeAR extends ITypeAR
//case object LocationTypeAR extends ITypeAR
//case object CoordTypeAR extends ITypeAR
//case object KindTypeAR extends ITypeAR
//case object CitizenTemplateTypeAR extends ITypeAR
////case object StructTypeAR extends ITypeAR
////case object SequenceTypeAR extends ITypeAR
//// We need PackTypeAR because we have a built-in templated destructor whose rules
//// only match packs... PackTypeAR is how it does that.
////case object PackTypeAR extends ITypeAR
////case object ArrayTypeAR extends ITypeAR
////case object CallableTypeAR extends ITypeAR
////case object InterfaceTypeAR extends ITypeAR
//
//object RuleSUtils {
//
//  def getDistinctOrderedRunesForRulex(rulex: IRulexAR): List[String] = {
//    rulex match {
//      case EqualsAR(left, right) => (getDistinctOrderedRunesForRulex(left) ++ getDistinctOrderedRunesForRulex(right)).distinct
//      case IsaAR(left, right) => (getDistinctOrderedRunesForRulex(left) ++ getDistinctOrderedRunesForRulex(right)).distinct
//      case OrAR(possibilities) => possibilities.flatMap(getDistinctOrderedRunesForRulex).distinct
//      case ComponentsAR(container, components) => {
//        getDistinctOrderedRunesForRulex(container) ++
//          components.flatMap(getDistinctOrderedRunesForRulex).toSet
//      }
//      case TypedAR(maybeRune, tyype) => maybeRune.toList
//      case TemplexAR(templex) => TemplexSUtils.getDistinctOrderedRunesForTemplex(templex)
//      case CallAR(name, args) => args.flatMap(getDistinctOrderedRunesForRulex).distinct
//    }
//  }
//
//  def getDistinctOrderedRunesForRulexes(rulexes: List[IRulexAR]): List[String] = {
//    rulexes.flatMap(getDistinctOrderedRunesForRulex).distinct
//  }
//
//  // This doesn't have the snazzy ability to choose the appropriate ownership that
//  // knownCoordRule has... but it does work in inferring.
//  // If these are a problem, make toRef able to do inferring.
//  def unknownCoordRule(kindRulexAR: IRulexAR): IRulexAR = {
//    ComponentsAR(TypedAR(None, CoordTypeAR), List(TemplexAR(AnonymousRuneAT()), kindRulexAR))
//  }
//  // This can make a ref for the given kind, choosing the appropriate ownership.
//  // However, it can't figure out an unknown kind given a coord, so it's not that
//  // useful in inferring.
//  // We COULD make it possible to infer through this. Might be worth considering.
//  def knownCoordRule(kindRulexAR: IRulexAR): IRulexAR = {
//    CallAR("toRef", List(kindRulexAR))
//  }
//}


sealed trait IRulexAR {
  def resultType: ITemplataType
}
case class EqualsAR(left: IRulexAR, right: IRulexAR) extends IRulexAR {
  override def resultType: ITemplataType = left.resultType
}
case class OrAR(possibilities: List[IRulexAR]) extends IRulexAR {
  vassert(possibilities.nonEmpty)
  override def resultType: ITemplataType = possibilities.head.resultType
}
case class ComponentsAR(
  tyype: ITemplataType,
  components: List[IRulexAR]
) extends IRulexAR {
  override def resultType: ITemplataType = tyype
}
case class TemplexAR(templex: ITemplexA) extends IRulexAR {
  override def resultType: ITemplataType = templex.resultType
}
// This is for built-in parser functions, such as exists() or isBaseOf() etc.
case class CallAR(
  name: String,
  args: List[IRulexAR],
  resultType: ITemplataType
) extends IRulexAR

case class IsaAR(
  subRule: IRulexAR,
  interfaceRule: IRulexAR
) extends IRulexAR {
  override def resultType: ITemplataType = subRule.resultType
}

// See PVSBUFI
sealed trait ITemplexA {
  def resultType: ITemplataType
}
case class IntAT(value: Int) extends ITemplexA {
  override def resultType: ITemplataType = IntegerTemplataType
}
case class StringAT(value: String) extends ITemplexA {
  override def resultType: ITemplataType = StringTemplataType
}
case class BoolAT(value: Boolean) extends ITemplexA {
  override def resultType: ITemplataType = BooleanTemplataType
}
case class MutabilityAT(mutability: MutabilityP) extends ITemplexA {
  override def resultType: ITemplataType = MutabilityTemplataType
}
case class PermissionAT(permission: PermissionP) extends ITemplexA {
  override def resultType: ITemplataType = PermissionTemplataType
}
case class LocationAT(location: LocationP) extends ITemplexA {
  override def resultType: ITemplataType = LocationTemplataType
}
case class OwnershipAT(ownership: OwnershipP) extends ITemplexA {
  override def resultType: ITemplataType = OwnershipTemplataType
}
case class VariabilityAT(variability: VariabilityP) extends ITemplexA {
  override def resultType: ITemplataType = VariabilityTemplataType
}
case class CoordListAT(elements: List[ITemplexA]) extends ITemplexA {
  override def resultType: ITemplataType = PackTemplataType(CoordTemplataType)
}

case class NameAT(
  name: IImpreciseNameStepA,
  resultType: ITemplataType
) extends ITemplexA {
//  println("hi")
}

case class AbsoluteNameAT(
  name: INameA,
  resultType: ITemplataType
) extends ITemplexA {
//  println("hi")
}

// We have both NameAT and RuneAT even though theyre syntactically identical
// because in the template engine, when we try to match an incoming type
// against a NameAT/RuneAT, we do different things. For NameAT, we take the thing
// from the environment and make sure it matches. For RuneAT, we might put
// something into the environment.
case class RuneAT(
  rune: IRuneA,
  resultType: ITemplataType
) extends ITemplexA

case class OwnershippedAT(
  ownership: OwnershipP,
  inner: ITemplexA
) extends ITemplexA {
  vassert(inner.resultType == CoordTemplataType)
  override def resultType: ITemplataType = CoordTemplataType
}

case class NullableAT(inner: ITemplexA) extends ITemplexA {
  override def resultType: ITemplataType = KindTemplataType
}

case class CallAT(
  template: ITemplexA,
  args: List[ITemplexA],
  // This is here because we might want to coerce the result. We do this for
  // calls, packs, etc.
  resultType: ITemplataType
) extends ITemplexA

//case class FunctionAT(
//  mutability: Option[ITemplexA],
//  parameters: List[Option[ITemplexA]],
//  returnType: Option[ITemplexA]
//) extends ITemplexA

case class PrototypeAT(
  name: String,
  parameters: List[ITemplexA],
  returnType: ITemplexA
) extends ITemplexA {
  override def resultType: ITemplataType = vimpl()
}

//case class PackAT(
//  members: List[ITemplexA],
//  // This is here because we might want to coerce the result. We do this for
//  // calls, packs, etc.
//  resultType: ITemplataType
//) extends ITemplexA

case class RepeaterSequenceAT(
  mutability: ITemplexA,
  size: ITemplexA,
  element: ITemplexA,
  // This is here because we might want to coerce the result. We do this for
  // calls, packs, etc.
  resultType: ITemplataType
) extends ITemplexA

case class ManualSequenceAT(
  elements: List[ITemplexA],
  // This is here because we might want to coerce the result. We do this for
  // calls, packs, etc.
  resultType: ITemplataType
) extends ITemplexA
