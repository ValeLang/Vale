package net.verdagon.vale.templar.infer

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser._
import net.verdagon.vale.templar.{IName2, IRune2}
import net.verdagon.vale.{vassert, vimpl, vwat}

import scala.collection.immutable.List

// These are different from IRulexA because those use IRuneA, not IRune2 which
// has more possibilities.
sealed trait IRulexTR {
  def resultType: ITemplataType
}
case class EqualsTR(left: IRulexTR, right: IRulexTR) extends IRulexTR {
  override def resultType: ITemplataType = left.resultType
}
case class OrTR(possibilities: List[IRulexTR]) extends IRulexTR {
  vassert(possibilities.nonEmpty)
  override def resultType: ITemplataType = possibilities.head.resultType
}
case class ComponentsTR(
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
  name: String,
  args: List[IRulexTR],
  resultType: ITemplataType
) extends IRulexTR

case class IsaTR(
  subRule: IRulexTR,
  interfaceRule: IRulexTR
) extends IRulexTR {
  override def resultType: ITemplataType = subRule.resultType
}

// See PVSBUFI
sealed trait ITemplexT {
  def resultType: ITemplataType
}
case class IntTT(value: Int) extends ITemplexT {
  override def resultType: ITemplataType = IntegerTemplataType
}
case class StringTT(value: String) extends ITemplexT {
  override def resultType: ITemplataType = StringTemplataType
}
case class BoolTT(value: Boolean) extends ITemplexT {
  override def resultType: ITemplataType = BooleanTemplataType
}
case class MutabilityTT(mutability: MutabilityP) extends ITemplexT {
  override def resultType: ITemplataType = MutabilityTemplataType
}
case class PermissionTT(permission: PermissionP) extends ITemplexT {
  override def resultType: ITemplataType = PermissionTemplataType
}
case class LocationTT(location: LocationP) extends ITemplexT {
  override def resultType: ITemplataType = LocationTemplataType
}
case class OwnershipTT(ownership: OwnershipP) extends ITemplexT {
  override def resultType: ITemplataType = OwnershipTemplataType
}
case class VariabilityTT(variability: VariabilityP) extends ITemplexT {
  override def resultType: ITemplataType = VariabilityTemplataType
}

case class NameTT(
  name: IImpreciseNameStepA,
  resultType: ITemplataType
) extends ITemplexT {
//  println("hi")
}

case class AbsoluteNameTT(
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
  rune: IRune2,
  resultType: ITemplataType
) extends ITemplexT

case class OwnershippedTT(
  ownership: OwnershipP,
  inner: ITemplexT
) extends ITemplexT {
  vassert(inner.resultType == CoordTemplataType)
  override def resultType: ITemplataType = CoordTemplataType
}

case class NullableTT(inner: ITemplexT) extends ITemplexT {
  override def resultType: ITemplataType = KindTemplataType
}

case class CallTT(
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
  mutability: ITemplexT,
  size: ITemplexT,
  element: ITemplexT,
  // This is here because we might want to coerce the result. We do this for
  // calls, packs, etc.
  resultType: ITemplataType
) extends ITemplexT

case class ManualSequenceTT(
  elements: List[ITemplexT],
  // This is here because we might want to coerce the result. We do this for
  // calls, packs, etc.
  resultType: ITemplataType
) extends ITemplexT

case class CoordListTT(
  elements: List[ITemplexT]
) extends ITemplexT {
  override def resultType: ITemplataType = PackTemplataType(CoordTemplataType)
}
