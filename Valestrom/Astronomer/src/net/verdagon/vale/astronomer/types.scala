package net.verdagon.vale.astronomer

import net.verdagon.vale.vassert

import scala.collection.immutable.List

sealed trait ITemplataType
case object CoordTemplataType extends ITemplataType
case object KindTemplataType extends ITemplataType
case object FunctionTemplataType extends ITemplataType
case object IntegerTemplataType extends ITemplataType
case object BooleanTemplataType extends ITemplataType
case object MutabilityTemplataType extends ITemplataType
case object PrototypeTemplataType extends ITemplataType
case object StringTemplataType extends ITemplataType
case object PermissionTemplataType extends ITemplataType
case object LocationTemplataType extends ITemplataType
case object OwnershipTemplataType extends ITemplataType
case object VariabilityTemplataType extends ITemplataType
case class PackTemplataType(elementType: ITemplataType) extends ITemplataType
// This is CitizenTemplataType instead of separate ones for struct and interface
// because the RuleTyper doesn't care whether something's a struct or an interface.
case class TemplateTemplataType(
  paramTypes: List[ITemplataType],
  returnType: ITemplataType
) extends ITemplataType {
  vassert(paramTypes.nonEmpty)
}
