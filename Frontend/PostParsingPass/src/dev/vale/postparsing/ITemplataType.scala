package dev.vale.postparsing

import dev.vale.vcurious

sealed trait ITemplataType
case class CoordTemplataType() extends ITemplataType
case class ImplTemplataType() extends ITemplataType
case class KindTemplataType() extends ITemplataType
case class FunctionTemplataType() extends ITemplataType
case class IntegerTemplataType() extends ITemplataType
case class BooleanTemplataType() extends ITemplataType
case class MutabilityTemplataType() extends ITemplataType
case class PrototypeTemplataType() extends ITemplataType
case class StringTemplataType() extends ITemplataType
case class LocationTemplataType() extends ITemplataType
case class OwnershipTemplataType() extends ITemplataType
case class VariabilityTemplataType() extends ITemplataType
case class PackTemplataType(elementType: ITemplataType) extends ITemplataType {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def equals(obj: Any): Boolean = {
    obj match {
      case PackTemplataType(thatElementType) => elementType == thatElementType
      case _ => false
    }
  }
}
// This is CitizenTemplataType() instead of separate ones for struct and interface
// because the RuleTyper doesn't care whether something's a struct or an interface.
case class TemplateTemplataType(
  paramTypes: Vector[ITemplataType],
  returnType: ITemplataType
) extends ITemplataType {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
