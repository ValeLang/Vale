package dev.vale.parsing.ast

import dev.vale.lexing.RangeL
import dev.vale.{vassert, vcurious, vpass}
import dev.vale.vpass

// See PVSBUFI

sealed trait ITemplexPT {
  def range: RangeL
}

case class AnonymousRunePT(range: RangeL) extends ITemplexPT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class BoolPT(range: RangeL, value: Boolean) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BorrowPT(range: RangeL, inner: ITemplexPT) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class PointPT(range: RangeL, inner: ITemplexPT) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
// This is for example func(Int)Bool, func:imm(Int, Int)Str, func:mut()(Str, Bool)
// It's shorthand for IFunction:(mut, (Int), Bool), IFunction:(mut, (Int, Int), Str), IFunction:(mut, (), (Str, Bool))
case class CallPT(range: RangeL, template: ITemplexPT, args: Vector[ITemplexPT]) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
// Mutability is Optional because they can leave it out, and mut will be assumed.
case class FunctionPT(range: RangeL, mutability: Option[ITemplexPT], parameters: PackPT, returnType: ITemplexPT) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class InlinePT(range: RangeL, inner: ITemplexPT) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class IntPT(range: RangeL, value: Long) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class RegionRunePT(range: RangeL, name: NameP) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class LocationPT(range: RangeL, location: LocationP) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TuplePT(range: RangeL, elements: Vector[ITemplexPT]) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class MutabilityPT(range: RangeL, mutability: MutabilityP) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class NameOrRunePT(name: NameP) extends ITemplexPT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  def range = name.range
  vassert(name.str != "_")
}
//case class NullablePT(range: Range, inner: ITemplexPT) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class InterpretedPT(range: RangeL, ownership: OwnershipP, inner: ITemplexPT) extends ITemplexPT {
  override def equals(obj: Any): Boolean = vcurious();
  override def hashCode(): Int = vcurious()
}
case class OwnershipPT(range: RangeL, ownership: OwnershipP) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class PackPT(range: RangeL, members: Vector[ITemplexPT]) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class PrototypePT(range: RangeL, name: NameP, parameters: Vector[ITemplexPT], returnType: ITemplexPT) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class StaticSizedArrayPT(
  range: RangeL,
  mutability: ITemplexPT,
  variability: ITemplexPT,
  size: ITemplexPT,
  element: ITemplexPT
) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class RuntimeSizedArrayPT(
  range: RangeL,
  mutability: ITemplexPT,
  element: ITemplexPT
) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class SharePT(range: RangeL, inner: ITemplexPT) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class StringPT(range: RangeL, str: String) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TypedRunePT(range: RangeL, rune: NameP, tyype: ITypePR) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class VariabilityPT(range: RangeL, variability: VariabilityP) extends ITemplexPT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
