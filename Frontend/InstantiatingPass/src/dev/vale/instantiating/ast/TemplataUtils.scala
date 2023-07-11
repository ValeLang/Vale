package dev.vale.instantiating.ast

import dev.vale.typing.ast.{FunctionHeaderT, FunctionDefinitionT, PrototypeT}
import dev.vale.typing.names._
import dev.vale.typing.ast._
import dev.vale.typing.names._

object simpleNameI {
  def unapply[R <: IRegionsModeI](id: IdI[R, INameI[R]]): Option[String] = {
    id.localName match {
//      case ImplDeclareNameI(_) => None
      case LambdaCallFunctionNameI(_, _, _) => Some("__call")
      case LetNameI(_) => None
      case UnnamedLocalNameI(_) => None
      case FunctionBoundNameI(FunctionBoundTemplateNameI(humanName, _), _, _) => Some(humanName.str)
      case ClosureParamNameI(_) => None
      case MagicParamNameI(_) => None
      case CodeVarNameI(name) => Some(name.str)
      case FunctionNameIX(FunctionTemplateNameI(humanName, _), _, _) => Some(humanName.str)
      case LambdaCitizenNameI(_) => None
      case StructNameI(StructTemplateNameI(humanName), _) => Some(humanName.str)
      case StructTemplateNameI(humanName) => Some(humanName.str)
      case InterfaceNameI(InterfaceTemplateNameI(humanName), _) => Some(humanName.str)
      case InterfaceTemplateNameI(humanName) => Some(humanName.str)
      case AnonymousSubstructTemplateNameI(InterfaceTemplateNameI(humanNamee)) => Some(humanNamee.str)
    }
  }
}

object functionNameI {
  def unapply(function2: FunctionDefinitionI): Option[String] = {
    unapply(function2.header)
  }
  def unapply(header: FunctionHeaderI): Option[String] = {
    simpleNameI.unapply(header.id)
  }
  def unapply[R <: IRegionsModeI](prototype: PrototypeI[R]): Option[String] = {
    simpleNameI.unapply(prototype.id)
  }
}

