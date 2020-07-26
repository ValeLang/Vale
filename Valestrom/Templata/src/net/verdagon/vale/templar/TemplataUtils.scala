package net.verdagon.vale.templar

import net.verdagon.vale.templar.templata.{FunctionHeader2, Prototype2}

object simpleName {
//  def apply(name: String): FullName2 = {
//    FullName2(List(FunctionNamePart2(name, Some(List()), None, None)))
//  }
  def unapply(fullName: FullName2[IName2]): Option[String] = {
    fullName.last match {
      case ImplDeclareName2(_) => None
      case LetName2(_) => None
      case UnnamedLocalName2(_) => None
      case ClosureParamName2() => None
      case MagicParamName2(_) => None
      case CodeVarName2(name) => Some(name)
//      case CodeRune2(name) => Some(name)
//      case ImplicitRune2(_) => None
//      case MemberRune2(_) => None
//      case MagicImplicitRune2(_) => None
//      case ReturnRune2() => None
      case FunctionName2(humanName, _, _) => Some(humanName)
//      case LambdaName2(_, _, _) => None
//      case CitizenName2(humanName, _) => Some(humanName)
      case TupleName2(_) => None
      case LambdaCitizenName2(_) => None
      case CitizenName2(humanName, _) => Some(humanName)
      case ImmConcreteDestructorName2(_) => None
      case ImmInterfaceDestructorName2(_, _) => None
    }
  }
}

object functionName {
  def unapply(function2: Function2): Option[String] = {
    unapply(function2.header)
  }
  def unapply(header: FunctionHeader2): Option[String] = {
    simpleName.unapply(header.fullName)
  }
  def unapply(prototype: Prototype2): Option[String] = {
    simpleName.unapply(prototype.fullName)
  }
}

