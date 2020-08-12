package net.verdagon.vale.parser

import scala.collection.immutable.List

sealed trait IRulexPR
case class EqualsPR(range: Range, left: IRulexPR, right: IRulexPR) extends IRulexPR
case class OrPR(range: Range, possibilities: List[IRulexPR]) extends IRulexPR
case class DotPR(range: Range, container: IRulexPR, memberName: StringP) extends IRulexPR
case class ComponentsPR(
  range: Range,
  // This is a TypedPR so that we can know the type, so we can know whether this is
  // a kind components rule or a coord components rule.
  container: TypedPR,
  components: List[IRulexPR]
) extends IRulexPR
case class TypedPR(range: Range, rune: Option[StringP], tyype: ITypePR) extends IRulexPR
case class TemplexPR(templex: ITemplexPRT) extends IRulexPR
// This is for built-in parser functions, such as exists() or isBaseOf() etc.
case class CallPR(name: StringP, args: List[IRulexPR]) extends IRulexPR
case class ResolveSignaturePR(nameStrRule: IRulexPR, argsPackRule: PackPR) extends IRulexPR
case class PackPR(elements: List[IRulexPR]) extends IRulexPR

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

// See PVSBUFI
sealed trait ITemplexPRT
case class IntPRT(range: Range, value: Int) extends ITemplexPRT
case class MutabilityPRT(range: Range, mutability: MutabilityP) extends ITemplexPRT
case class PermissionPRT(range: Range, permission: PermissionP) extends ITemplexPRT
case class LocationPRT(range: Range, location: LocationP) extends ITemplexPRT
case class OwnershipPRT(range: Range, ownership: OwnershipP) extends ITemplexPRT
case class VariabilityPRT(range: Range, variability: VariabilityP) extends ITemplexPRT
case class BoolPRT(range: Range, value: Boolean) extends ITemplexPRT
case class NameOrRunePRT(name: StringP) extends ITemplexPRT
case class TypedRunePRT(range: Range, rune: StringP, tyype: ITypePR) extends ITemplexPRT
case class AnonymousRunePRT(range: Range) extends ITemplexPRT
case class BorrowPRT(range: Range, inner: ITemplexPRT) extends ITemplexPRT
case class StringPRT(range: Range, str: StringP) extends ITemplexPRT
case class SharePRT(range: Range, inner: ITemplexPRT) extends ITemplexPRT
case class CallPRT(range: Range, template: ITemplexPRT, args: List[ITemplexPRT]) extends ITemplexPRT
// This is for example fn(Int)Bool, fn:imm(Int, Int)Str, fn:mut()(Str, Bool)
// It's shorthand for IFunction:(mut, (Int), Bool), IFunction:(mut, (Int, Int), Str), IFunction:(mut, (), (Str, Bool))
case class FunctionPRT(
    range: Range,
    // This is Optional because they can leave it out, and mut will be assumed.
    mutability: Option[ITemplexPRT],
    parameters: PackPRT,
    returnType: ITemplexPRT
) extends ITemplexPRT
case class PrototypePRT(
  range: Range,
    name: StringP,
    parameters: List[ITemplexPRT],
    returnType: ITemplexPRT
) extends ITemplexPRT
case class PackPRT(
  range: Range,
    members: List[ITemplexPRT]
) extends ITemplexPRT
case class RepeaterSequencePRT(
  range: Range,
    mutability: ITemplexPRT,
    size: ITemplexPRT,
    element: ITemplexPRT
) extends ITemplexPRT
case class ManualSequencePRT(
  range: Range,
    elements: List[ITemplexPRT]
) extends ITemplexPRT


object RulePUtils {

  def getOrderedRuneDeclarationsFromRulexesWithDuplicates(rulexes: List[IRulexPR]):
  List[String] = {
    rulexes.flatMap(getOrderedRuneDeclarationsFromRulexWithDuplicates)
  }

  def getOrderedRuneDeclarationsFromRulexWithDuplicates(rulex: IRulexPR): List[String] = {
    rulex match {
      case PackPR(elements) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(elements)
      case ResolveSignaturePR(nameStrRule, argsPackRule) =>getOrderedRuneDeclarationsFromRulexWithDuplicates(nameStrRule) ++ getOrderedRuneDeclarationsFromRulexWithDuplicates(argsPackRule)
      case EqualsPR(range, left, right) => getOrderedRuneDeclarationsFromRulexWithDuplicates(left) ++ getOrderedRuneDeclarationsFromRulexWithDuplicates(right)
      case OrPR(range, possibilities) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(possibilities)
      case DotPR(range, container, memberName) => getOrderedRuneDeclarationsFromRulexWithDuplicates(container)
      case ComponentsPR(_, container, components) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(container :: components)
      case TypedPR(range, maybeRune, tyype) => maybeRune.map(_.str).toList
      case TemplexPR(templex) => getOrderedRuneDeclarationsFromTemplexWithDuplicates(templex)
      case CallPR(name, args) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(args)
    }
  }

  def getOrderedRuneDeclarationsFromTemplexesWithDuplicates(templexes: List[ITemplexPRT]): List[String] = {
    templexes.flatMap(getOrderedRuneDeclarationsFromTemplexWithDuplicates)
  }

  def getOrderedRuneDeclarationsFromTemplexWithDuplicates(templex: ITemplexPRT): List[String] = {
    templex match {
      case BorrowPRT(_, inner) => getOrderedRuneDeclarationsFromTemplexWithDuplicates(inner)
      case StringPRT(_, value) => List()
      case IntPRT(_, value) => List()
      case MutabilityPRT(_, mutability) => List()
      case PermissionPRT(_, permission) => List()
      case LocationPRT(_, location) => List()
      case OwnershipPRT(_, ownership) => List()
      case BoolPRT(_, value) => List()
      case NameOrRunePRT(name) => List()
      case TypedRunePRT(_, name, tyype) => List(name.str)
      case AnonymousRunePRT(_) => List()
      case CallPRT(_, template, args) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates((template :: args))
      case FunctionPRT(range, mutability, parameters, returnType) => {
        getOrderedRuneDeclarationsFromTemplexesWithDuplicates(mutability.toList) ++
          getOrderedRuneDeclarationsFromTemplexWithDuplicates(parameters) ++
          getOrderedRuneDeclarationsFromTemplexWithDuplicates(returnType)
      }
      case PrototypePRT(_, name, parameters, returnType) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates((parameters :+ returnType))
      case PackPRT(_, members) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates(members)
      case RepeaterSequencePRT(_, mutability, size, element) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates(List(mutability, size, element))
      case ManualSequencePRT(_, elements) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates(elements)
    }
  }
}
