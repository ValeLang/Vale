package net.verdagon.vale.solver

import net.verdagon.vale.{vassert, vcurious, vimpl, vwat}

// These are different from IRulexA because those use IRuneA, not IRuneT which
// has more possibilities.
// See PVSBUFI
sealed trait IRulexAR[RuneID, RuleID, Literal, Lookup] {
  def range: RuleID
  def allRunes: Vector[RuneID]
}
case class OneOfAR[RuneID, RuleID, Literal, Lookup](
  range: RuleID,
  resultRune: RuneID,
  literals: Array[Literal]
) extends IRulexAR[RuneID, RuleID, Literal, Lookup] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def allRunes: Vector[RuneID] = Vector(resultRune)
  vassert(literals.nonEmpty)
}
case class CoordComponentsAR[RuneID, RuleID, Literal, Lookup](
  range: RuleID,
  coordRune: RuneID,
  ownershipRune: RuneID,
  permissionRune: RuneID,
  kindRune: RuneID,
) extends IRulexAR[RuneID, RuleID, Literal, Lookup] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def allRunes: Vector[RuneID] = Vector(coordRune, ownershipRune, permissionRune, kindRune)
}
case class KindComponentsAR[RuneID, RuleID, Literal, Lookup](
  range: RuleID,
  kindRune: RuneID,
  mutabilityRune: RuneID,
) extends IRulexAR[RuneID, RuleID, Literal, Lookup] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def allRunes: Vector[RuneID] = Vector(kindRune, mutabilityRune)
}

case class IsConcreteAR[RuneID, RuleID, Literal, Lookup](
  range: RuleID,
  rune: RuneID
) extends IRulexAR[RuneID, RuleID, Literal, Lookup] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def allRunes: Vector[RuneID] = Vector(rune)
}

case class IsInterfaceAR[RuneID, RuleID, Literal, Lookup](
  range: RuleID,
  rune: RuneID
) extends IRulexAR[RuneID, RuleID, Literal, Lookup] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def allRunes: Vector[RuneID] = Vector(rune)
}

case class IsStructAR[RuneID, RuleID, Literal, Lookup](
  range: RuleID,
  rune: RuneID
) extends IRulexAR[RuneID, RuleID, Literal, Lookup] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def allRunes: Vector[RuneID] = Vector(rune)
}

case class CoerceToCoordAR[RuneID, RuleID, Literal, Lookup](
  range: RuleID,
  coordRune: RuneID,
  kindRune: RuneID
) extends IRulexAR[RuneID, RuleID, Literal, Lookup] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def allRunes: Vector[RuneID] = Vector(coordRune, kindRune)
}

case class IsaAR[RuneID, RuleID, Literal, Lookup](
  range: RuleID,
  subRune: RuneID,
  interfaceRune: RuneID
) extends IRulexAR[RuneID, RuleID, Literal, Lookup] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def allRunes: Vector[RuneID] = Vector(subRune, interfaceRune)
}

case class LiteralAR[RuneID, RuleID, Literal, Lookup](
  range: RuleID,
  rune: RuneID,
  literal: Literal
) extends IRulexAR[RuneID, RuleID, Literal, Lookup] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def allRunes: Vector[RuneID] = Vector(rune)
}

case class LookupAR[RuneID, RuleID, Literal, Lookup](
  range: RuleID,
  rune: RuneID,
  literal: Lookup
) extends IRulexAR[RuneID, RuleID, Literal, Lookup] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def allRunes: Vector[RuneID] = Vector(rune)
}

// InterpretedAR will overwrite inner's permission and ownership to the given ones.
// We turned InterpretedAR into this
case class AugmentAR[RuneID, RuleID, Literal, Lookup](
  range: RuleID,
  resultRune: RuneID,
  // Lets try and figure out a way to only have one thing here instead of a Vector
  literal: Vector[Literal],
  innerRune: RuneID
) extends IRulexAR[RuneID, RuleID, Literal, Lookup] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def allRunes: Vector[RuneID] = Vector(resultRune, innerRune)
}

case class CallAR[RuneID, RuleID, Literal, Lookup](
  range: RuleID,
  resultRune: RuneID,
  templateRune: RuneID,
  args: Array[RuneID]
) extends IRulexAR[RuneID, RuleID, Literal, Lookup] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def allRunes: Vector[RuneID] = Vector(resultRune, templateRune) ++ args
}

case class PrototypeAR[RuneID, RuleID, Literal, Lookup](
  range: RuleID,
  resultRune: RuneID,
  name: String,
  parameters: Array[RuneID],
  returnTypeRune: RuneID
) extends IRulexAR[RuneID, RuleID, Literal, Lookup] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def allRunes: Vector[RuneID] = Vector(resultRune) ++ parameters ++ Vector(returnTypeRune)
}

case class RepeaterSequenceAR[RuneID, RuleID, Literal, Lookup](
  range: RuleID,
  resultRune: RuneID,
  mutabilityRune: RuneID,
  variabilityRune: RuneID,
  sizeRune: RuneID,
  elementRune: RuneID
) extends IRulexAR[RuneID, RuleID, Literal, Lookup] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def allRunes: Vector[RuneID] = Vector(resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune)
}

case class ManualSequenceAR[RuneID, RuleID, Literal, Lookup](
  range: RuleID,
  resultRune: RuneID,
  elements: Array[RuneID]
) extends IRulexAR[RuneID, RuleID, Literal, Lookup] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def allRunes: Vector[RuneID] = Vector(resultRune) ++ elements
}

case class CoordListAR[RuneID, RuleID, Literal, Lookup](
  range: RuleID,
  resultRune: RuneID,
  elements: Array[RuneID]
) extends IRulexAR[RuneID, RuleID, Literal, Lookup] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def allRunes: Vector[RuneID] = Vector(resultRune) ++ elements
}
