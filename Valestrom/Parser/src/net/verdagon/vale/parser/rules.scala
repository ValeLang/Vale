package net.verdagon.vale.parser

import scala.collection.immutable.List

sealed trait IRulexPR
case class EqualsPR(left: IRulexPR, right: IRulexPR) extends IRulexPR
case class OrPR(possibilities: List[IRulexPR]) extends IRulexPR
case class DotPR(container: IRulexPR, memberName: StringP) extends IRulexPR
case class ComponentsPR(
  // This is a TypedPR so that we can know the type, so we can know whether this is
  // a kind components rule or a coord components rule.
  container: TypedPR,
  components: List[IRulexPR]
) extends IRulexPR
case class TypedPR(rune: Option[StringP], tyype: ITypePR) extends IRulexPR
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
case object CitizenTemplateTypePR extends ITypePR
//case object StructTypePR extends ITypePR
//case object SequenceTypePR extends ITypePR
//case object ArrayTypePR extends ITypePR
//case object CallableTypePR extends ITypePR
//case object InterfaceTypePR extends ITypePR

// See PVSBUFI
sealed trait ITemplexPRT
case class IntPRT(value: Int) extends ITemplexPRT
case class MutabilityPRT(mutability: MutabilityP) extends ITemplexPRT
case class PermissionPRT(permission: PermissionP) extends ITemplexPRT
case class LocationPRT(location: LocationP) extends ITemplexPRT
case class OwnershipPRT(ownership: OwnershipP) extends ITemplexPRT
case class VariabilityPRT(variability: VariabilityP) extends ITemplexPRT
case class BoolPRT(value: Boolean) extends ITemplexPRT
case class NameOrRunePRT(name: StringP) extends ITemplexPRT
case class TypedRunePRT(rune: StringP, tyype: ITypePR) extends ITemplexPRT
case class AnonymousRunePRT() extends ITemplexPRT
case class BorrowPRT(inner: ITemplexPRT) extends ITemplexPRT
case class StringPRT(str: StringP) extends ITemplexPRT
case class SharePRT(inner: ITemplexPRT) extends ITemplexPRT
case class CallPRT(template: ITemplexPRT, args: List[ITemplexPRT]) extends ITemplexPRT
// This is for example fn(Int)Bool, fn:imm(Int, Int)Str, fn:mut()(Str, Bool)
// It's shorthand for IFunction:(mut, (Int), Bool), IFunction:(mut, (Int, Int), Str), IFunction:(mut, (), (Str, Bool))
case class FunctionPRT(
    // This is Optional because they can leave it out, and mut will be assumed.
    mutability: Option[ITemplexPRT],
    parameters: PackPRT,
    returnType: ITemplexPRT
) extends ITemplexPRT
case class PrototypePRT(
    name: StringP,
    parameters: List[ITemplexPRT],
    returnType: ITemplexPRT
) extends ITemplexPRT
case class PackPRT(
    members: List[ITemplexPRT]
) extends ITemplexPRT
case class RepeaterSequencePRT(
    mutability: ITemplexPRT,
    size: ITemplexPRT,
    element: ITemplexPRT
) extends ITemplexPRT
case class ManualSequencePRT(
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
      case EqualsPR(left, right) => getOrderedRuneDeclarationsFromRulexWithDuplicates(left) ++ getOrderedRuneDeclarationsFromRulexWithDuplicates(right)
      case OrPR(possibilities) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(possibilities)
      case DotPR(container, memberName) => getOrderedRuneDeclarationsFromRulexWithDuplicates(container)
      case ComponentsPR(container, components) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(container :: components)
      case TypedPR(maybeRune, tyype) => maybeRune.map(_.str).toList
      case TemplexPR(templex) => getOrderedRuneDeclarationsFromTemplexWithDuplicates(templex)
      case CallPR(name, args) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(args)
    }
  }

  def getOrderedRuneDeclarationsFromTemplexesWithDuplicates(templexes: List[ITemplexPRT]): List[String] = {
    templexes.flatMap(getOrderedRuneDeclarationsFromTemplexWithDuplicates)
  }

  def getOrderedRuneDeclarationsFromTemplexWithDuplicates(templex: ITemplexPRT): List[String] = {
    templex match {
      case BorrowPRT(inner) => getOrderedRuneDeclarationsFromTemplexWithDuplicates(inner)
      case StringPRT(value) => List()
      case IntPRT(value) => List()
      case MutabilityPRT(mutability) => List()
      case PermissionPRT(permission) => List()
      case LocationPRT(location) => List()
      case OwnershipPRT(ownership) => List()
      case BoolPRT(value) => List()
      case NameOrRunePRT(name) => List()
      case TypedRunePRT(name, tyype) => List(name.str)
      case AnonymousRunePRT() => List()
      case CallPRT(template, args) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates((template :: args))
      case FunctionPRT(mutability, parameters, returnType) => {
        getOrderedRuneDeclarationsFromTemplexesWithDuplicates(mutability.toList) ++
          getOrderedRuneDeclarationsFromTemplexWithDuplicates(parameters) ++
          getOrderedRuneDeclarationsFromTemplexWithDuplicates(returnType)
      }
      case PrototypePRT(name, parameters, returnType) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates((parameters :+ returnType))
      case PackPRT(members) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates(members)
      case RepeaterSequencePRT(mutability, size, element) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates(List(mutability, size, element))
      case ManualSequencePRT(elements) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates(elements)
    }
  }
}
