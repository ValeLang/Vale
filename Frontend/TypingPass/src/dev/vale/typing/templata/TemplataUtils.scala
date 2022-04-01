package dev.vale.typing.templata

import dev.vale.typing.ast.{FunctionHeaderT, FunctionT, PrototypeT}
import dev.vale.typing.names.{CitizenNameT, CitizenTemplateNameT, ClosureParamNameT, CodeVarNameT, FreeNameT, FullNameT, FunctionNameT, INameT, ImplDeclareNameT, LambdaCitizenNameT, LetNameT, MagicParamNameT, UnnamedLocalNameT}
import dev.vale.typing.ast._
import dev.vale.typing.names._

object simpleName {
  def unapply(fullName: FullNameT[INameT]): Option[String] = {
    fullName.last match {
      case ImplDeclareNameT(_) => None
      case LetNameT(_) => None
      case UnnamedLocalNameT(_) => None
      case FreeNameT(_, _) => None
      case ClosureParamNameT() => None
      case MagicParamNameT(_) => None
      case CodeVarNameT(name) => Some(name)
      case FunctionNameT(humanName, _, _) => Some(humanName)
      case LambdaCitizenNameT(_) => None
      case CitizenNameT(CitizenTemplateNameT(humanName), _) => Some(humanName)
    }
  }
}

object functionName {
  def unapply(function2: FunctionT): Option[String] = {
    unapply(function2.header)
  }
  def unapply(header: FunctionHeaderT): Option[String] = {
    simpleName.unapply(header.fullName)
  }
  def unapply(prototype: PrototypeT): Option[String] = {
    simpleName.unapply(prototype.fullName)
  }
}

