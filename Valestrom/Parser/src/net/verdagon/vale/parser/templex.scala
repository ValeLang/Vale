package net.verdagon.vale.parser

import scala.collection.immutable.List

// See PVSBUFI

sealed trait ITemplexPT

case class AnonymousRunePT(range: Range) extends ITemplexPT
case class BoolPT(range: Range, value: Boolean) extends ITemplexPT
case class BorrowPT(range: Range, inner: ITemplexPT) extends ITemplexPT
// This is for example fn(Int)Bool, fn:imm(Int, Int)Str, fn:mut()(Str, Bool)
// It's shorthand for IFunction:(mut, (Int), Bool), IFunction:(mut, (Int, Int), Str), IFunction:(mut, (), (Str, Bool))
case class CallPT(range: Range, template: ITemplexPT, args: List[ITemplexPT]) extends ITemplexPT
// Mutability is Optional because they can leave it out, and mut will be assumed.
case class FunctionPT(range: Range, mutability: Option[ITemplexPT], parameters: PackPT, returnType: ITemplexPT) extends ITemplexPT
case class InlinePT(range: Range, inner: ITemplexPT) extends ITemplexPT
case class IntPT(range: Range, value: Int) extends ITemplexPT
case class LocationPT(range: Range, location: LocationP) extends ITemplexPT
case class ManualSequencePT(range: Range, elements: List[ITemplexPT]) extends ITemplexPT
case class MutabilityPT(range: Range, mutability: MutabilityP) extends ITemplexPT
case class NameOrRunePT(name: StringP) extends ITemplexPT
case class NullablePT(range: Range, inner: ITemplexPT) extends ITemplexPT
case class OwnershippedPT(range: Range, ownership: OwnershipP, inner: ITemplexPT) extends ITemplexPT
case class OwnershipPT(range: Range, ownership: OwnershipP) extends ITemplexPT
case class PackPT(range: Range, members: List[ITemplexPT]) extends ITemplexPT
case class PermissionedPT(range: Range, permission: PermissionP, inner: ITemplexPT) extends ITemplexPT
case class PermissionPT(range: Range, permission: PermissionP) extends ITemplexPT
case class PrototypePT(range: Range, name: StringP, parameters: List[ITemplexPT], returnType: ITemplexPT) extends ITemplexPT
case class RepeaterSequencePT(range: Range, mutability: ITemplexPT, size: ITemplexPT, element: ITemplexPT) extends ITemplexPT
case class SharePT(range: Range, inner: ITemplexPT) extends ITemplexPT
case class StringPT(range: Range, str: String) extends ITemplexPT
case class TypedRunePT(range: Range, rune: StringP, tyype: ITypePR) extends ITemplexPT
case class VariabilityPT(range: Range, variability: VariabilityP) extends ITemplexPT
