package net.verdagon.vale.parser.ast

import net.verdagon.vale.{vassert, vcurious, vpass}

// See PVSBUFI

sealed trait ITemplexPT {
  def range: RangeP
}

case class AnonymousRunePT(range: RangeP) extends ITemplexPT {
  override def hashCode(): Int = vcurious()
  vpass()
}
case class BoolPT(range: RangeP, value: Boolean) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class BorrowPT(range: RangeP, inner: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class PointPT(range: RangeP, inner: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
// This is for example fn(Int)Bool, fn:imm(Int, Int)Str, fn:mut()(Str, Bool)
// It's shorthand for IFunction:(mut, (Int), Bool), IFunction:(mut, (Int, Int), Str), IFunction:(mut, (), (Str, Bool))
case class CallPT(range: RangeP, template: ITemplexPT, args: Vector[ITemplexPT]) extends ITemplexPT { override def hashCode(): Int = vcurious() }
// Mutability is Optional because they can leave it out, and mut will be assumed.
case class FunctionPT(range: RangeP, mutability: Option[ITemplexPT], parameters: PackPT, returnType: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class InlinePT(range: RangeP, inner: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class IntPT(range: RangeP, value: Long) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class RegionRune(range: RangeP, name: NameP) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class LocationPT(range: RangeP, location: LocationP) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class TuplePT(range: RangeP, elements: Vector[ITemplexPT]) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class MutabilityPT(range: RangeP, mutability: MutabilityP) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class NameOrRunePT(name: NameP) extends ITemplexPT {
  override def hashCode(): Int = vcurious()
  def range = name.range
  vassert(name.str != "_")
}
//case class NullablePT(range: Range, inner: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class InterpretedPT(range: RangeP, ownership: OwnershipP, permission: PermissionP, inner: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class OwnershipPT(range: RangeP, ownership: OwnershipP) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class PackPT(range: RangeP, members: Vector[ITemplexPT]) extends ITemplexPT { override def hashCode(): Int = vcurious() }
//case class PermissionedPT(range: Range, permission: PermissionP, inner: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class PermissionPT(range: RangeP, permission: PermissionP) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class PrototypePT(range: RangeP, name: NameP, parameters: Vector[ITemplexPT], returnType: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class StaticSizedArrayPT(
  range: RangeP,
  mutability: ITemplexPT,
  variability: ITemplexPT,
  size: ITemplexPT,
  element: ITemplexPT
) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class RuntimeSizedArrayPT(
  range: RangeP,
  mutability: ITemplexPT,
  element: ITemplexPT
) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class SharePT(range: RangeP, inner: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class StringPT(range: RangeP, str: String) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class TypedRunePT(range: RangeP, rune: NameP, tyype: ITypePR) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class VariabilityPT(range: RangeP, variability: VariabilityP) extends ITemplexPT { override def hashCode(): Int = vcurious() }
