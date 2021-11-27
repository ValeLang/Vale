package net.verdagon.vale.scout.rules

import net.verdagon.vale.{RangeS, vassert, vcurious, vimpl, vpass, vwat}
import net.verdagon.vale.parser.{LocationP, MutabilityP, OwnershipP, PermissionP, VariabilityP}
import net.verdagon.vale.scout._

import scala.collection.immutable.List

case class RuneUsage(range: RangeS, rune: IRuneS)

// This isn't generic over e.g.  because we shouldnt reuse
// this between layers. The generics solver doesn't even know about IRulexSR, doesn't
// need to, it relies on delegates to do any rule-specific things.
// Different stages will likely need different kinds of rules, so best not prematurely
// combine them.
trait IRulexSR {
  def range: RangeS
  def runeUsages: Array[RuneUsage]
}

case class EqualsSR(range: RangeS, left: RuneUsage, right: RuneUsage) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(left, right)
}

// See SAIRFU and SRCAMP for what's going on with these rules.
case class CoordSendSR(range: RangeS, senderRune: RuneUsage, receiverRune: RuneUsage) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(senderRune, receiverRune)
}

case class CoordIsaSR(range: RangeS, subRune: RuneUsage, superRune: RuneUsage) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(subRune, superRune)
}

case class KindIsaSR(range: RangeS, subRune: RuneUsage, superRune: RuneUsage) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(subRune, superRune)
}

case class KindComponentsSR(
  range: RangeS,
  kindRune: RuneUsage,
  mutabilityRune: RuneUsage
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(kindRune, mutabilityRune)
}

case class CoordComponentsSR(
  range: RangeS,
  resultRune: RuneUsage,
  ownershipRune: RuneUsage,
  permissionRune: RuneUsage,
  kindRune: RuneUsage
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(resultRune, ownershipRune, permissionRune, kindRune)
}

case class PrototypeComponentsSR(
  range: RangeS,
  resultRune: RuneUsage,
  nameRune: RuneUsage,
  paramsListRune: RuneUsage,
  returnRune: RuneUsage
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(resultRune, nameRune, paramsListRune, returnRune)
}

// See Possible Values Shouldnt Be Used For Inference (PVSBUFI)
case class OneOfSR(
  range: RangeS,
  rune: RuneUsage,
  literals: Array[ILiteralSL]
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  vassert(literals.nonEmpty)
  override def runeUsages: Array[RuneUsage] = Array(rune)
}

case class IsConcreteSR(
  range: RangeS,
  rune: RuneUsage
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(rune)
}

case class IsInterfaceSR(
  range: RangeS,
  rune: RuneUsage
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(rune)
}

case class IsStructSR(
  range: RangeS,
  rune: RuneUsage
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(rune)
4}

case class CoerceToCoordSR(
  range: RangeS,
  coordRune: RuneUsage,
  kindRune: RuneUsage
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(coordRune, kindRune)
}

case class RefListCompoundMutabilitySR(
  range: RangeS,
  resultRune: RuneUsage,
  coordListRune: RuneUsage,
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(resultRune, coordListRune)
}

case class LiteralSR(
  range: RangeS,
  rune: RuneUsage,
  literal: ILiteralSL
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(rune)
}

case class LookupSR(
  range: RangeS,
  rune: RuneUsage,
  name: IImpreciseNameS
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  vpass()
  override def runeUsages: Array[RuneUsage] = Array(rune)
}

case class IndexListSR(
  range: RangeS,
  resultRune: RuneUsage,
  listRune: RuneUsage,
  index: Int
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  vpass()
  override def runeUsages: Array[RuneUsage] = Array(resultRune, listRune)
}

case class RuneParentEnvLookupSR(
  range: RangeS,
  rune: RuneUsage
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  vpass()
  override def runeUsages: Array[RuneUsage] = Array(rune)
}

// InterpretedAR will overwrite inner's permission and ownership to the given ones.
// We turned InterpretedAR into this
case class AugmentSR(
  range: RangeS,
  resultRune: RuneUsage,
  // Lets try and figure out a way to only have one thing here instead of a Vector
  literal: Vector[ILiteralSL],
  innerRune: RuneUsage
) extends IRulexSR {
  vpass()
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(resultRune, innerRune)
}

case class CallSR(
  range: RangeS,
  resultRune: RuneUsage,
  templateRune: RuneUsage,
  args: Array[RuneUsage]
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(resultRune, templateRune) ++ args
}

case class PrototypeSR(
  range: RangeS,
  resultRune: RuneUsage,
  name: String,
  parameters: Array[RuneUsage],
  returnTypeRune: RuneUsage
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(resultRune, returnTypeRune) ++ parameters
}

case class PackSR(
  range: RangeS,
  resultRune: RuneUsage,
  members: Array[RuneUsage]
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(resultRune) ++ members
}

case class RepeaterSequenceSR(
  range: RangeS,
  resultRune: RuneUsage,
  mutabilityRune: RuneUsage,
  variabilityRune: RuneUsage,
  sizeRune: RuneUsage,
  elementRune: RuneUsage
) extends IRulexSR {
  override def hashCode(): Int = vcurious()
  override def runeUsages: Array[RuneUsage] = Array(resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune)
}

sealed trait ILiteralSL {
  def getType(): ITemplataType
}

case class IntLiteralSL(value: Long) extends ILiteralSL {
  override def hashCode(): Int = vcurious()
  override def getType(): ITemplataType = IntegerTemplataType
}
case class StringLiteralSL(value: String) extends ILiteralSL {
  override def hashCode(): Int = vcurious()
  override def getType(): ITemplataType = StringTemplataType
}
case class BoolLiteralSL(value: Boolean) extends ILiteralSL {
  override def hashCode(): Int = vcurious()
  override def getType(): ITemplataType = BooleanTemplataType
}
case class MutabilityLiteralSL(mutability: MutabilityP) extends ILiteralSL {
  override def hashCode(): Int = vcurious()
  override def getType(): ITemplataType = MutabilityTemplataType
}
case class PermissionLiteralSL(permission: PermissionP) extends ILiteralSL {
  override def hashCode(): Int = vcurious()
  override def getType(): ITemplataType = PermissionTemplataType
}
case class LocationLiteralSL(location: LocationP) extends ILiteralSL {
  override def hashCode(): Int = vcurious()
  override def getType(): ITemplataType = LocationTemplataType
}
case class OwnershipLiteralSL(ownership: OwnershipP) extends ILiteralSL {
  override def hashCode(): Int = vcurious()
  override def getType(): ITemplataType = OwnershipTemplataType
}
case class VariabilityLiteralSL(variability: VariabilityP) extends ILiteralSL {
  override def hashCode(): Int = vcurious()
  override def getType(): ITemplataType = VariabilityTemplataType
}
