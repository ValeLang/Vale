package dev.vale.postparsing.rules

import dev.vale.parsing.ast.{LocationP, MutabilityP, OwnershipP, VariabilityP}
import dev.vale.postparsing._
import dev.vale.{RangeS, StrI, vassert, vassertSome, vcurious, vpass}
import dev.vale.parsing.ast._
import dev.vale.postparsing._

import scala.collection.immutable.List

case class RuneUsage(range: RangeS, rune: IRuneS) {
  vpass()
}

// This isn't generic over e.g.  because we shouldnt reuse
// this between layers. The generics solver doesn't even know about IRulexSR, doesn't
// need to, it relies on delegates to do any rule-specific things.
// Different stages will likely need different kinds of rules, so best not prematurely
// combine them.
trait IRulexSR {
  def range: RangeS
  def runeUsages: Vector[RuneUsage]
}

case class EqualsSR(range: RangeS, left: RuneUsage, right: RuneUsage) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(left, right)
}

// See SAIRFU and SRCAMP for what's going on with these rules.
case class CoordSendSR(
  range: RangeS,
  senderRune: RuneUsage,
  receiverRune: RuneUsage
) extends IRulexSR {
  vpass()

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(senderRune, receiverRune)
}

case class DefinitionCoordIsaSR(range: RangeS, resultRune: RuneUsage, subRune: RuneUsage, superRune: RuneUsage) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(resultRune, subRune, superRune)
}

case class CallSiteCoordIsaSR(
  range: RangeS,
  // This is here because when we add this CallSiteCoordIsaSR and its companion DefinitionCoordIsaSR,
  // the DefinitionCoordIsaSR has a resultRune that it usually populates with an ImplTemplata.
  // That rune is in the rules somewhere, but when we filter out the DefinitionCoordIsaSR for call site
  // solves, that rune is still there, and all runes must be solved, so we need something to solve it.
  // So, we make CallSiteCoordIsaSR solve it, and populate it with an ImplTemplata or ImplDefinitionTemplata.
  // It's also similar to how Definition/CallSiteFuncSR work.
  // It also means the call site has access to the impls, which might be nice for ONBIFS and NBIFP.
  // It's an Option because CoordSendSR sometimes produces one of these, and it doesn't care about
  // the result.
  resultRune: Option[RuneUsage],
  subRune: RuneUsage,
  superRune: RuneUsage
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = resultRune.toVector ++ Vector(subRune, superRune)
}

case class KindComponentsSR(
  range: RangeS,
  kindRune: RuneUsage,
  mutabilityRune: RuneUsage
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(kindRune, mutabilityRune)
}

case class CoordComponentsSR(
  range: RangeS,
  resultRune: RuneUsage,
  ownershipRune: RuneUsage,
  regionRune: RuneUsage,
  kindRune: RuneUsage
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(resultRune, ownershipRune, regionRune, kindRune)
}

case class PrototypeComponentsSR(
  range: RangeS,
  resultRune: RuneUsage,
  paramsRune: RuneUsage,
  returnRune: RuneUsage
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(resultRune, paramsRune, returnRune)
}

case class ResolveSR(
  range: RangeS,
  resultRune: RuneUsage,
  name: StrI,
  paramsListRune: RuneUsage,
  returnRune: RuneUsage
) extends IRulexSR {
  vpass()
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(resultRune, paramsListRune, returnRune)
}

case class CallSiteFuncSR(
  range: RangeS,
  prototypeRune: RuneUsage,
  name: StrI,
  paramsListRune: RuneUsage,
  returnRune: RuneUsage
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(prototypeRune, paramsListRune, returnRune)
}

case class DefinitionFuncSR(
  range: RangeS,
  resultRune: RuneUsage,
  name: StrI,
  paramsListRune: RuneUsage,
  returnRune: RuneUsage
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(resultRune, paramsListRune, returnRune)
}

// See Possible Values Shouldnt Be Used For Inference (PVSBUFI)
case class OneOfSR(
  range: RangeS,
  rune: RuneUsage,
  literals: Vector[ILiteralSL]
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vassert(literals.nonEmpty)
  override def runeUsages: Vector[RuneUsage] = Vector(rune)
}

case class IsConcreteSR(
  range: RangeS,
  rune: RuneUsage
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(rune)
}

case class IsInterfaceSR(
  range: RangeS,
  rune: RuneUsage
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(rune)
}

case class IsStructSR(
  range: RangeS,
  rune: RuneUsage
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(rune)
}

// TODO: Get rid of this in favor of just CoordComponentsSR.
case class CoerceToCoordSR(
  range: RangeS,
  coordRune: RuneUsage,
  regionRune: RuneUsage,
  kindRune: RuneUsage
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(coordRune, regionRune, kindRune)
}

case class RefListCompoundMutabilitySR(
  range: RangeS,
  resultRune: RuneUsage,
  coordListRune: RuneUsage,
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(resultRune, coordListRune)
}

case class LiteralSR(
  range: RangeS,
  rune: RuneUsage,
  literal: ILiteralSL
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(rune)
}

case class MaybeCoercingLookupSR(
  range: RangeS,
  rune: RuneUsage,
  // The region to use *if* we're coercing this lookup to a coord, see LNTKTR.
  contextRegionRune: RuneUsage,
  name: IImpreciseNameS
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
  override def runeUsages: Vector[RuneUsage] = Vector(rune, contextRegionRune)
}

// A rule that looks up something that's not a Kind, so it doesn't need a default region.
case class LookupSR(
  range: RangeS,
  rune: RuneUsage,
  name: IImpreciseNameS
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
  override def runeUsages: Vector[RuneUsage] = Vector(rune)
}

// This comes out of the post-parser, it's not sure whether it'll be coercing the call result to a coord or not.
// The higher-typer will turn this into a CallSR or a CoercingCallSR.
case class MaybeCoercingCallSR(
  range: RangeS,
  resultRune: RuneUsage,
  // The region to use *if* we're coercing this call result to a coord, see LNTKTR.
  contextRegionRune: RuneUsage,
  templateRune: RuneUsage,
  args: Vector[RuneUsage]
) extends IRulexSR {
  vpass()
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(resultRune, contextRegionRune, templateRune) ++ args
}

// // This rule coerces the call result to a coord, which is why it has a region.
// case class CoercingCallSR(
//     range: RangeS,
//     resultRune: RuneUsage,
//     // The region to use *if* we're coercing this call result to a coord, see LNTKTR.
//     contextRegionRune: RuneUsage,
//     templateRune: RuneUsage,
//     args: Vector[RuneUsage]
// ) extends IRulexSR {
//   override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//   override def runeUsages: Vector[RuneUsage] = Vector(resultRune, contextRegionRune, templateRune) ++ args
// }

// This doesn't do any coercing.
// ...this might not ever even get to the typing phase. Vale doesn't yet have any templates that don't produce kinds.
case class CallSR(
  range: RangeS,
  resultRune: RuneUsage,
  templateRune: RuneUsage,
  args: Vector[RuneUsage]
) extends IRulexSR {
  vpass()

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(resultRune, templateRune) ++ args
}

case class IndexListSR(
  range: RangeS,
  resultRune: RuneUsage,
  listRune: RuneUsage,
  index: Int
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
  override def runeUsages: Vector[RuneUsage] = Vector(resultRune, listRune)
}

case class RuneParentEnvLookupSR(
  range: RangeS,
  rune: RuneUsage
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
  override def runeUsages: Vector[RuneUsage] = Vector(rune)
}

// InterpretedAR will overwrite inner's permission and ownership to the given ones.
// We turned InterpretedAR into this
case class AugmentSR(
  range: RangeS,
  resultRune: RuneUsage,
  ownership: Option[OwnershipP],
  region: Option[RuneUsage],
  innerRune: RuneUsage
) extends IRulexSR {
  vpass()
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(resultRune, innerRune) ++ region
}

case class PackSR(
  range: RangeS,
  resultRune: RuneUsage,
  members: Vector[RuneUsage]
) extends IRulexSR {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def runeUsages: Vector[RuneUsage] = Vector(resultRune) ++ members
}

//case class StaticSizedArraySR(
//  range: RangeS,
//  resultRune: RuneUsage,
//  mutabilityRune: RuneUsage,
//  variabilityRune: RuneUsage,
//  sizeRune: RuneUsage,
//  elementRune: RuneUsage
//) extends IRulexSR {
//  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def runeUsages: Vector[RuneUsage] = Vector(resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune)
//}
//
//case class RuntimeSizedArraySR(
//  range: RangeS,
//  resultRune: RuneUsage,
//  mutabilityRune: RuneUsage,
//  elementRune: RuneUsage
//) extends IRulexSR {
//  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def runeUsages: Vector[RuneUsage] = Vector(resultRune, mutabilityRune, elementRune)
//}

sealed trait ILiteralSL {
  def getType(): ITemplataType
}

case class IntLiteralSL(value: Long) extends ILiteralSL {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def getType(): ITemplataType = IntegerTemplataType()
}
case class StringLiteralSL(value: String) extends ILiteralSL {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def getType(): ITemplataType = StringTemplataType()
}
case class BoolLiteralSL(value: Boolean) extends ILiteralSL {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def getType(): ITemplataType = BooleanTemplataType()
}
case class MutabilityLiteralSL(mutability: MutabilityP) extends ILiteralSL {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def getType(): ITemplataType = MutabilityTemplataType()
}
case class LocationLiteralSL(location: LocationP) extends ILiteralSL {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def getType(): ITemplataType = LocationTemplataType()
}
case class OwnershipLiteralSL(ownership: OwnershipP) extends ILiteralSL {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def getType(): ITemplataType = OwnershipTemplataType()
}
case class VariabilityLiteralSL(variability: VariabilityP) extends ILiteralSL {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def getType(): ITemplataType = VariabilityTemplataType()
}
