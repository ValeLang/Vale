package net.verdagon.vale.astronomer

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{CodeLocationS, RangeS}
import net.verdagon.vale.{vassert, vimpl, vwat}

import scala.collection.immutable.List

//sealed trait IRulexAR
//case class EqualsAR(left: IRulexAR, right: IRulexAR) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class IsaAR(sub: IRulexAR, suuper: IRulexAR) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class OrAR(alternatives: Vector[IRulexAR]) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class ComponentsAR(
//  // This is a TypedAR so that we can know the type, so we can know whether this is
//  // a kind components rule or a coord components rule.
//  container: TypedAR,
//  components: Vector[IRulexAR]
//) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class TypedAR(rune: Option[String], tyype: ITypeAR) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class TemplexAR(templex: ITemplexS) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//// This is for built-in parser functions, such as exists() or isBaseOf() etc.
//case class CallAR(name: String, args: Vector[IRulexAR]) extends IRulexAR {
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
//  def getDistinctOrderedRunesForRulex(rulex: IRulexAR): Vector[String] = {
//    rulex match {
//      case EqualsAR(left, right) => (getDistinctOrderedRunesForRulex(left) ++ getDistinctOrderedRunesForRulex(right)).distinct
//      case IsaAR(left, right) => (getDistinctOrderedRunesForRulex(left) ++ getDistinctOrderedRunesForRulex(right)).distinct
//      case OrAR(possibilities) => possibilities.flatMap(getDistinctOrderedRunesForRulex).distinct
//      case ComponentsAR(container, components) => {
//        getDistinctOrderedRunesForRulex(container) ++
//          components.flatMap(getDistinctOrderedRunesForRulex).toSet
//      }
//      case TypedAR(maybeRune, tyype) => maybeRune.toVector
//      case TemplexAR(templex) => TemplexSUtils.getDistinctOrderedRunesForTemplex(templex)
//      case CallAR(name, args) => args.flatMap(getDistinctOrderedRunesForRulex).distinct
//    }
//  }
//
//  def getDistinctOrderedRunesForRulexes(rulexes: Vector[IRulexAR]): Vector[String] = {
//    rulexes.flatMap(getDistinctOrderedRunesForRulex).distinct
//  }
//
//  // This doesn't have the snazzy ability to choose the appropriate ownership that
//  // knownCoordRule has... but it does work in inferring.
//  // If these are a problem, make toRef able to do inferring.
//  def unknownCoordRule(kindRulexAR: IRulexAR): IRulexAR = {
//    ComponentsAR(TypedAR(None, CoordTypeAR), Vector(TemplexAR(AnonymousRuneAT()), kindRulexAR))
//  }
//  // This can make a ref for the given kind, choosing the appropriate ownership.
//  // However, it can't figure out an unknown kind given a coord, so it's not that
//  // useful in inferring.
//  // We COULD make it possible to infer through this. Might be worth considering.
//  def knownCoordRule(kindRulexAR: IRulexAR): IRulexAR = {
//    CallAR("toRef", Vector(kindRulexAR))
//  }
//}


sealed trait IRulexAR {
  def resultType: ITemplataType
}
case class EqualsAR(range: RangeS, left: IRulexAR, right: IRulexAR) extends IRulexAR {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def resultType: ITemplataType = left.resultType
}
case class OrAR(range: RangeS, possibilities: Vector[IRulexAR]) extends IRulexAR {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vassert(possibilities.nonEmpty)
  override def resultType: ITemplataType = possibilities.head.resultType
}
case class ComponentsAR(
  range: RangeS,
  tyype: ITemplataType,
  components: Vector[IRulexAR]
) extends IRulexAR {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def resultType: ITemplataType = tyype
}
case class TemplexAR(templex: ITemplexA) extends IRulexAR {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def resultType: ITemplataType = templex.resultType
}
// This is for built-in parser functions, such as exists() or isBaseOf() etc.
case class CallAR(
  range: RangeS,
  name: String,
  args: Vector[IRulexAR],
  resultType: ITemplataType
) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class IsaAR(
  range: RangeS,
  subRule: IRulexAR,
  interfaceRule: IRulexAR
) extends IRulexAR {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def resultType: ITemplataType = subRule.resultType
}

// See PVSBUFI
sealed trait ITemplexA {
  def resultType: ITemplataType
}
case class IntAT(range: RangeS, value: Long) extends ITemplexA {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def resultType: ITemplataType = IntegerTemplataType
}
case class StringAT(range: RangeS, value: String) extends ITemplexA {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def resultType: ITemplataType = StringTemplataType
}
case class BoolAT(range: RangeS, value: Boolean) extends ITemplexA {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def resultType: ITemplataType = BooleanTemplataType
}
case class MutabilityAT(range: RangeS, mutability: MutabilityP) extends ITemplexA {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def resultType: ITemplataType = MutabilityTemplataType
}
case class PermissionAT(range: RangeS, permission: PermissionP) extends ITemplexA {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def resultType: ITemplataType = PermissionTemplataType
}
case class LocationAT(range: RangeS, location: LocationP) extends ITemplexA {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def resultType: ITemplataType = LocationTemplataType
}
case class OwnershipAT(range: RangeS, ownership: OwnershipP) extends ITemplexA {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def resultType: ITemplataType = OwnershipTemplataType
}
case class VariabilityAT(range: RangeS, variability: VariabilityP) extends ITemplexA {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def resultType: ITemplataType = VariabilityTemplataType
}
case class CoordListAT(range: RangeS, elements: Vector[ITemplexA]) extends ITemplexA {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def resultType: ITemplataType = PackTemplataType(CoordTemplataType)
}

case class NameAT(
  rangeS: RangeS,
  name: IImpreciseNameStepA,
  resultType: ITemplataType
) extends ITemplexA {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  println("hi")
}

case class AbsoluteNameAT(
  rangeS: RangeS,
  name: INameA,
  resultType: ITemplataType
) extends ITemplexA {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  println("hi")
}

// We have both NameAT and RuneAT even though theyre syntactically identical
// because in the template engine, when we try to match an incoming type
// against a NameAT/RuneAT, we do different things. For NameAT, we take the thing
// from the environment and make sure it matches. For RuneAT, we might put
// something into the environment.
case class RuneAT(
  rangeS: RangeS,
  rune: IRuneA,
  resultType: ITemplataType
) extends ITemplexA { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class InterpretedAT(
  rangeS: RangeS,
  ownership: OwnershipP,
  permission: PermissionP,
  inner: ITemplexA
) extends ITemplexA {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vassert(inner.resultType == CoordTemplataType)
  override def resultType: ITemplataType = CoordTemplataType
}

case class NullableAT(
  rangeS: RangeS,
  inner: ITemplexA) extends ITemplexA {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def resultType: ITemplataType = KindTemplataType
}

case class CallAT(
  rangeS: RangeS,
  template: ITemplexA,
  args: Vector[ITemplexA],
  // This is here because we might want to coerce the result. We do this for
  // calls, packs, etc.
  resultType: ITemplataType
) extends ITemplexA { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

//case class FunctionAT(
//  mutability: Option[ITemplexA],
//  parameters: Vector[Option[ITemplexA]],
//  returnType: Option[ITemplexA]
//) extends ITemplexA { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class PrototypeAT(
  rangeS: RangeS,
  name: String,
  parameters: Vector[ITemplexA],
  returnType: ITemplexA
) extends ITemplexA {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def resultType: ITemplataType = vimpl()
}

//case class PackAT(
//  members: Vector[ITemplexA],
//  // This is here because we might want to coerce the result. We do this for
//  // calls, packs, etc.
//  resultType: ITemplataType
//) extends ITemplexA { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class RepeaterSequenceAT(
  rangeS: RangeS,
  mutability: ITemplexA,
  variability: ITemplexA,
  size: ITemplexA,
  element: ITemplexA,
  // This is here because we might want to coerce the result. We do this for
  // calls, packs, etc.
  resultType: ITemplataType
) extends ITemplexA { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class ManualSequenceAT(
  rangeS: RangeS,
  elements: Vector[ITemplexA],
  // This is here because we might want to coerce the result. We do this for
  // calls, packs, etc.
  resultType: ITemplataType
) extends ITemplexA { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
