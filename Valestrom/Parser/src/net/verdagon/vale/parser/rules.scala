package net.verdagon.vale.parser

import scala.collection.immutable.List

sealed trait IRulexPR {
  def range: Range
}
case class EqualsPR(range: Range, left: IRulexPR, right: IRulexPR) extends IRulexPR
case class OrPR(range: Range, possibilities: List[IRulexPR]) extends IRulexPR
case class DotPR(range: Range, container: IRulexPR, memberName: NameP) extends IRulexPR
case class ComponentsPR(
  range: Range,
  // This is a TypedPR so that we can know the type, so we can know whether this is
  // a kind components rule or a coord components rule.
  container: TypedPR,
  components: List[IRulexPR]
) extends IRulexPR
case class TypedPR(range: Range, rune: Option[NameP], tyype: ITypePR) extends IRulexPR
case class TemplexPR(templex: ITemplexPT) extends IRulexPR {
  def range = templex.range
}
// This is for built-in parser functions, such as exists() or isBaseOf() etc.
case class CallPR(range: Range, name: NameP, args: List[IRulexPR]) extends IRulexPR
case class ResolveSignaturePR(range: Range, nameStrRule: IRulexPR, argsPackRule: PackPR) extends IRulexPR
case class PackPR(range: Range, elements: List[IRulexPR]) extends IRulexPR

sealed trait ITypePR
case object IntTypePR extends ITypePR
case object BoolTypePR extends ITypePR
case object OwnershipTypePR extends ITypePR
case object MutabilityTypePR extends ITypePR
case object PermissionTypePR extends ITypePR
case object LocationTypePR extends ITypePR
case object CoordTypePR extends ITypePR
case object PrototypeTypePR extends ITypePR
case object KindTypePR extends ITypePR
case object RegionTypePR extends ITypePR
case object CitizenTemplateTypePR extends ITypePR
//case object StructTypePR extends ITypePR
//case object SequenceTypePR extends ITypePR
//case object ArrayTypePR extends ITypePR
//case object CallableTypePR extends ITypePR
//case object InterfaceTypePR extends ITypePR


object RulePUtils {

  def getOrderedRuneDeclarationsFromRulexesWithDuplicates(rulexes: List[IRulexPR]):
  List[String] = {
    rulexes.flatMap(getOrderedRuneDeclarationsFromRulexWithDuplicates)
  }

  def getOrderedRuneDeclarationsFromRulexWithDuplicates(rulex: IRulexPR): List[String] = {
    rulex match {
      case PackPR(range, elements) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(elements)
      case ResolveSignaturePR(range, nameStrRule, argsPackRule) =>getOrderedRuneDeclarationsFromRulexWithDuplicates(nameStrRule) ++ getOrderedRuneDeclarationsFromRulexWithDuplicates(argsPackRule)
      case EqualsPR(range, left, right) => getOrderedRuneDeclarationsFromRulexWithDuplicates(left) ++ getOrderedRuneDeclarationsFromRulexWithDuplicates(right)
      case OrPR(range, possibilities) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(possibilities)
      case DotPR(range, container, memberName) => getOrderedRuneDeclarationsFromRulexWithDuplicates(container)
      case ComponentsPR(_, container, components) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(container :: components)
      case TypedPR(range, maybeRune, tyype) => maybeRune.map(_.str).toList
      case TemplexPR(templex) => getOrderedRuneDeclarationsFromTemplexWithDuplicates(templex)
      case CallPR(range, name, args) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(args)
    }
  }

  def getOrderedRuneDeclarationsFromTemplexesWithDuplicates(templexes: List[ITemplexPT]): List[String] = {
    templexes.flatMap(getOrderedRuneDeclarationsFromTemplexWithDuplicates)
  }

  def getOrderedRuneDeclarationsFromTemplexWithDuplicates(templex: ITemplexPT): List[String] = {
    templex match {
      case BorrowPT(_, inner) => getOrderedRuneDeclarationsFromTemplexWithDuplicates(inner)
      case StringPT(_, value) => List.empty
      case IntPT(_, value) => List.empty
      case MutabilityPT(_, mutability) => List.empty
      case VariabilityPT(_, mutability) => List.empty
      case PermissionPT(_, permission) => List.empty
      case LocationPT(_, location) => List.empty
      case OwnershipPT(_, ownership) => List.empty
      case BoolPT(_, value) => List.empty
      case NameOrRunePT(name) => List.empty
      case TypedRunePT(_, name, tyype) => List(name.str)
      case AnonymousRunePT(_) => List.empty
      case CallPT(_, template, args) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates((template :: args))
      case FunctionPT(range, mutability, parameters, returnType) => {
        getOrderedRuneDeclarationsFromTemplexesWithDuplicates(mutability.toList) ++
          getOrderedRuneDeclarationsFromTemplexWithDuplicates(parameters) ++
          getOrderedRuneDeclarationsFromTemplexWithDuplicates(returnType)
      }
      case PrototypePT(_, name, parameters, returnType) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates((parameters :+ returnType))
      case PackPT(_, members) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates(members)
      case RepeaterSequencePT(_, mutability, variability, size, element) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates(List(mutability, variability, size, element))
      case ManualSequencePT(_, elements) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates(elements)
    }
  }
}
