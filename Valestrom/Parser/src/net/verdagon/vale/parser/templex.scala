package net.verdagon.vale.parser

import net.verdagon.vale.{vassert, vcurious, vimpl, vpass}

import scala.collection.immutable.List

// See PVSBUFI

sealed trait ITemplexPT {
  def range: Range
}

case class AnonymousRunePT(range: Range) extends ITemplexPT {
  override def hashCode(): Int = vcurious()
  vpass()
}
case class BoolPT(range: Range, value: Boolean) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class BorrowPT(range: Range, inner: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
// This is for example fn(Int)Bool, fn:imm(Int, Int)Str, fn:mut()(Str, Bool)
// It's shorthand for IFunction:(mut, (Int), Bool), IFunction:(mut, (Int, Int), Str), IFunction:(mut, (), (Str, Bool))
case class CallPT(range: Range, template: ITemplexPT, args: Vector[ITemplexPT]) extends ITemplexPT { override def hashCode(): Int = vcurious() }
// Mutability is Optional because they can leave it out, and mut will be assumed.
case class FunctionPT(range: Range, mutability: Option[ITemplexPT], parameters: PackPT, returnType: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class InlinePT(range: Range, inner: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class IntPT(range: Range, value: Long) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class RegionRune(range: Range, name: NameP) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class LocationPT(range: Range, location: LocationP) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class ManualSequencePT(range: Range, elements: Vector[ITemplexPT]) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class MutabilityPT(range: Range, mutability: MutabilityP) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class NameOrRunePT(name: NameP) extends ITemplexPT {
  override def hashCode(): Int = vcurious()
  def range = name.range
  vassert(name.str != "_")
}
//case class NullablePT(range: Range, inner: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class InterpretedPT(range: Range, ownership: OwnershipP, permission: PermissionP, inner: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class OwnershipPT(range: Range, ownership: OwnershipP) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class PackPT(range: Range, members: Vector[ITemplexPT]) extends ITemplexPT { override def hashCode(): Int = vcurious() }
//case class PermissionedPT(range: Range, permission: PermissionP, inner: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class PermissionPT(range: Range, permission: PermissionP) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class PrototypePT(range: Range, name: NameP, parameters: Vector[ITemplexPT], returnType: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class RepeaterSequencePT(
  range: Range,
  mutability: ITemplexPT,
  variability: ITemplexPT,
  size: ITemplexPT,
  element: ITemplexPT
) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class SharePT(range: Range, inner: ITemplexPT) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class StringPT(range: Range, str: String) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class TypedRunePT(range: Range, rune: NameP, tyype: ITypePR) extends ITemplexPT { override def hashCode(): Int = vcurious() }
case class VariabilityPT(range: Range, variability: VariabilityP) extends ITemplexPT { override def hashCode(): Int = vcurious() }
