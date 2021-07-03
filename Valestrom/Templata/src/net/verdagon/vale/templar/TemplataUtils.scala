package net.verdagon.vale.templar

import net.verdagon.vale.templar.templata.{FunctionHeaderT, PrototypeT}

object simpleName {
//  def apply(name: String): FullName2 = {
//    FullName2(List(FunctionNamePart2(name, Some(List()), None, None)))
//  }
  def unapply(fullName: FullNameT[INameT]): Option[String] = {
    fullName.last match {
      case ImplDeclareNameT(_, _) => None
      case LetNameT(_) => None
      case UnnamedLocalNameT(_) => None
      case ClosureParamNameT() => None
      case MagicParamNameT(_) => None
      case CodeVarNameT(name) => Some(name)
//      case CodeRune2(name) => Some(name)
//      case ImplicitRune2(_) => None
//      case MemberRune2(_) => None
//      case MagicImplicitRune2(_) => None
//      case ReturnRune2() => None
      case FunctionNameT(humanName, _, _) => Some(humanName)
//      case LambdaName2(_, _, _) => None
//      case CitizenName2(humanName, _) => Some(humanName)
      case TupleNameT(_) => None
      case LambdaCitizenNameT(_) => None
      case CitizenNameT(humanName, _) => Some(humanName)
      case ImmConcreteDestructorNameT(_) => None
      case ImmInterfaceDestructorNameT(_, _) => None
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

