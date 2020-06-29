package net.verdagon.vale.parser

sealed trait ITemplexPT

case class NullablePT(inner: ITemplexPT) extends ITemplexPT

case class InlinePT(range: Range, inner: ITemplexPT) extends ITemplexPT
//case class BorrowPT(range: Range, inner: ITemplexPT) extends ITemplexPT
case class OwnershippedPT(range: Range, ownership: OwnershipP, inner: ITemplexPT) extends ITemplexPT
//case class OwnPT(inner: ITemplexPT) extends ITemplexPT
case class AnonymousRunePT() extends ITemplexPT
case class NameOrRunePT(rune: StringP) extends ITemplexPT
//case class SharePT(inner: ITemplexPT) extends ITemplexPT

case class CallPT(range: Range, template: ITemplexPT, args: List[ITemplexPT]) extends ITemplexPT
//case class RepeaterSequencePT(mutability: ITemplexPT, size: ITemplexPT, element: ITemplexPT) extends ITemplexPT
case class RepeaterSequencePT(range: Range, mutability: ITemplexPT, size: ITemplexPT, element: ITemplexPT) extends ITemplexPT
case class ManualSequencePT(range: Range, members: List[ITemplexPT]) extends ITemplexPT


case class IntPT(range: Range, value: Int) extends ITemplexPT
case class BoolPT(value: Boolean) extends ITemplexPT
case class OwnershipPT(ownership: OwnershipP) extends ITemplexPT
case class MutabilityPT(mutability: MutabilityP) extends ITemplexPT
case class LocationPT(location: LocationP) extends ITemplexPT
case class PermissionPT(permission: PermissionP) extends ITemplexPT
