package net.verdagon.vale.parser

sealed trait ITemplexPT

case class NullablePT(range: Range, inner: ITemplexPT) extends ITemplexPT
case class InlinePT(range: Range, inner: ITemplexPT) extends ITemplexPT
case class PermissionedPT(range: Range, permission: PermissionP, inner: ITemplexPT) extends ITemplexPT
case class OwnershippedPT(range: Range, ownership: OwnershipP, inner: ITemplexPT) extends ITemplexPT
case class AnonymousRunePT(range: Range) extends ITemplexPT
case class NameOrRunePT(rune: StringP) extends ITemplexPT
case class CallPT(range: Range, template: ITemplexPT, args: List[ITemplexPT]) extends ITemplexPT
case class RepeaterSequencePT(range: Range, mutability: ITemplexPT, size: ITemplexPT, element: ITemplexPT) extends ITemplexPT
case class ManualSequencePT(range: Range, members: List[ITemplexPT]) extends ITemplexPT
case class IntPT(range: Range, value: Int) extends ITemplexPT
case class BoolPT(range: Range, value: Boolean) extends ITemplexPT
case class OwnershipPT(range: Range, ownership: OwnershipP) extends ITemplexPT
case class MutabilityPT(range: Range, mutability: MutabilityP) extends ITemplexPT
case class LocationPT(range: Range, location: LocationP) extends ITemplexPT
case class PermissionPT(range: Range, permission: PermissionP) extends ITemplexPT
