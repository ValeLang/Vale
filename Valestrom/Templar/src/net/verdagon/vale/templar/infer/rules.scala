package net.verdagon.vale.templar.infer

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.{IName2, IRune2}
import net.verdagon.vale.{vassert, vimpl, vwat}

import scala.collection.immutable.List

// These are different from IRulexA because those use IRuneA, not IRune2 which
// has more possibilities.
sealed trait IRulexTR {
  def resultType: ITemplataType
}
case class EqualsTR(range: RangeS, left: IRulexTR, right: IRulexTR) extends IRulexTR {
  override def resultType: ITemplataType = left.resultType
}
case class OrTR(range: RangeS, possibilities: List[IRulexTR]) extends IRulexTR {
  vassert(possibilities.nonEmpty)
  override def resultType: ITemplataType = possibilities.head.resultType
}
case class ComponentsTR(
  range: RangeS,
  tyype: ITemplataType,
  components: List[IRulexTR]
) extends IRulexTR {
  override def resultType: ITemplataType = tyype
}
case class TemplexTR(templex: ITemplexT) extends IRulexTR {
  override def resultType: ITemplataType = templex.resultType
}
// This is for built-in parser functions, such as exists() or isBaseOf() etc.
case class CallTR(
  range: RangeS,
  name: String,
  args: List[IRulexTR],
  resultType: ITemplataType
) extends IRulexTR

case class IsaTR(
  range: RangeS,
  subRule: IRulexTR,
  interfaceRule: IRulexTR
) extends IRulexTR {
  override def resultType: ITemplataType = subRule.resultType
}

// See PVSBUFI
sealed trait ITemplexT {
  def resultType: ITemplataType
  def range: RangeS
}
case class IntTT(range: RangeS, value: Int) extends ITemplexT {
  override def resultType: ITemplataType = IntegerTemplataType
}
case class StringTT(range: RangeS, value: String) extends ITemplexT {
  override def resultType: ITemplataType = StringTemplataType
}
case class BoolTT(range: RangeS, value: Boolean) extends ITemplexT {
  override def resultType: ITemplataType = BooleanTemplataType
}
case class MutabilityTT(range: RangeS, mutability: MutabilityP) extends ITemplexT {
  override def resultType: ITemplataType = MutabilityTemplataType
}
case class PermissionTT(range: RangeS, permission: PermissionP) extends ITemplexT {
  override def resultType: ITemplataType = PermissionTemplataType
}
case class LocationTT(range: RangeS, location: LocationP) extends ITemplexT {
  override def resultType: ITemplataType = LocationTemplataType
}
case class OwnershipTT(range: RangeS, ownership: OwnershipP) extends ITemplexT {
  override def resultType: ITemplataType = OwnershipTemplataType
}
case class VariabilityTT(range: RangeS, variability: VariabilityP) extends ITemplexT {
  override def resultType: ITemplataType = VariabilityTemplataType
}

case class NameTT(
  range: RangeS,
  name: IImpreciseNameStepA,
  resultType: ITemplataType
) extends ITemplexT {
//  println("hi")
}

case class AbsoluteNameTT(
  range: RangeS,
  name: INameA,
  resultType: ITemplataType
) extends ITemplexT {
//  println("hi")
}

// We have both NameAT and RuneAT even though theyre syntactically identical
// because in the template engine, when we try to match an incoming type
// against a NameAT/RuneAT, we do different things. For NameAT, we take the thing
// from the environment and make sure it matches. For RuneAT, we might put
// something into the environment.
case class RuneTT(
  range: RangeS,
  rune: IRune2,
  resultType: ITemplataType
) extends ITemplexT

// InterpretedTT will overwrite inner's permission and ownership to the given ones.
case class InterpretedTT(
  range: RangeS,
  ownership: OwnershipP,
  permission: PermissionP,
  inner: ITemplexT
) extends ITemplexT {
  vassert(inner.resultType == CoordTemplataType)
  override def resultType: ITemplataType = CoordTemplataType
}

case class NullableTT(
  range: RangeS,
  inner: ITemplexT) extends ITemplexT {
  override def resultType: ITemplataType = KindTemplataType
}

case class CallTT(
  range: RangeS,
  template: ITemplexT,
  args: List[ITemplexT],
  // This is here because we might want to coerce the result. We do this for
  // calls, packs, etc.
  resultType: ITemplataType
) extends ITemplexT

//case class FunctionTT(
//  mutability: Option[ITemplexT],
//  parameters: List[Option[ITemplexT]],
//  returnType: Option[ITemplexT]
//) extends ITemplexT

case class PrototypeTT(
  range: RangeS,
  name: String,
  parameters: List[ITemplexT],
  returnType: ITemplexT
) extends ITemplexT {
  override def resultType: ITemplataType = vimpl()
}

//case class PackTT(
//  members: List[ITemplexT],
//  // This is here because we might want to coerce the result. We do this for
//  // calls, packs, etc.
//  resultType: ITemplataType
//) extends ITemplexT

case class RepeaterSequenceTT(
  range: RangeS,
  mutability: ITemplexT,
  size: ITemplexT,
  element: ITemplexT,
  // This is here because we might want to coerce the result. We do this for
  // calls, packs, etc.
  resultType: ITemplataType
) extends ITemplexT

case class ManualSequenceTT(
  range: RangeS,
  elements: List[ITemplexT],
  // This is here because we might want to coerce the result. We do this for
  // calls, packs, etc.
  resultType: ITemplataType
) extends ITemplexT

case class CoordListTT(
  range: RangeS,
  elements: List[ITemplexT]
) extends ITemplexT {
  override def resultType: ITemplataType = PackTemplataType(CoordTemplataType)
}
