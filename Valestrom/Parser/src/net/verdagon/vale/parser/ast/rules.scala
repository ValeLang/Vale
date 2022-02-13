package net.verdagon.vale.parser.ast

import net.verdagon.vale.vcurious

sealed trait IRulexPR {
  def range: RangeP
}
case class EqualsPR(range: RangeP, left: IRulexPR, right: IRulexPR) extends IRulexPR { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class OrPR(range: RangeP, possibilities: Vector[IRulexPR]) extends IRulexPR { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class DotPR(range: RangeP, container: IRulexPR, memberName: NameP) extends IRulexPR { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ComponentsPR(
  range: RangeP,
  container: ITypePR,
  components: Vector[IRulexPR]
) extends IRulexPR { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TypedPR(range: RangeP, rune: Option[NameP], tyype: ITypePR) extends IRulexPR { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TemplexPR(templex: ITemplexPT) extends IRulexPR {
  def range = templex.range
}
// This is for built-in parser functions, such as exists() or isBaseOf() etc.
case class BuiltinCallPR(range: RangeP, name: NameP, args: Vector[IRulexPR]) extends IRulexPR { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ResolveSignaturePR(range: RangeP, nameStrRule: IRulexPR, argsPackRule: PackPR) extends IRulexPR { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class PackPR(range: RangeP, elements: Vector[IRulexPR]) extends IRulexPR { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

sealed trait ITypePR
case object IntTypePR extends ITypePR
case object BoolTypePR extends ITypePR
case object OwnershipTypePR extends ITypePR
case object MutabilityTypePR extends ITypePR
case object VariabilityTypePR extends ITypePR
case object LocationTypePR extends ITypePR
case object CoordTypePR extends ITypePR
case object CoordListTypePR extends ITypePR
case object PrototypeTypePR extends ITypePR
case object KindTypePR extends ITypePR
case object RegionTypePR extends ITypePR
case object CitizenTemplateTypePR extends ITypePR

object RulePUtils {

  def getOrderedRuneDeclarationsFromRulexesWithDuplicates(rulexes: Vector[IRulexPR]):
  Vector[NameP] = {
    rulexes.flatMap(getOrderedRuneDeclarationsFromRulexWithDuplicates)
  }

  def getOrderedRuneDeclarationsFromRulexWithDuplicates(rulex: IRulexPR): Vector[NameP] = {
    rulex match {
      case PackPR(range, elements) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(elements)
      case ResolveSignaturePR(range, nameStrRule, argsPackRule) =>getOrderedRuneDeclarationsFromRulexWithDuplicates(nameStrRule) ++ getOrderedRuneDeclarationsFromRulexWithDuplicates(argsPackRule)
      case EqualsPR(range, left, right) => getOrderedRuneDeclarationsFromRulexWithDuplicates(left) ++ getOrderedRuneDeclarationsFromRulexWithDuplicates(right)
      case OrPR(range, possibilities) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(possibilities)
      case DotPR(range, container, memberName) => getOrderedRuneDeclarationsFromRulexWithDuplicates(container)
      case ComponentsPR(_, container, components) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(components)
      case TypedPR(range, maybeRune, tyype) => maybeRune.toVector
      case TemplexPR(templex) => getOrderedRuneDeclarationsFromTemplexWithDuplicates(templex)
      case BuiltinCallPR(range, name, args) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(args)
    }
  }

  def getOrderedRuneDeclarationsFromTemplexesWithDuplicates(templexes: Vector[ITemplexPT]): Vector[NameP] = {
    templexes.flatMap(getOrderedRuneDeclarationsFromTemplexWithDuplicates)
  }

  def getOrderedRuneDeclarationsFromTemplexWithDuplicates(templex: ITemplexPT): Vector[NameP] = {
    templex match {
      case BorrowPT(_, inner) => getOrderedRuneDeclarationsFromTemplexWithDuplicates(inner)
      case InterpretedPT(_, _, inner) => getOrderedRuneDeclarationsFromTemplexWithDuplicates(inner)
      case StringPT(_, value) => Vector.empty
      case IntPT(_, value) => Vector.empty
      case MutabilityPT(_, mutability) => Vector.empty
      case VariabilityPT(_, mutability) => Vector.empty
      case LocationPT(_, location) => Vector.empty
      case OwnershipPT(_, ownership) => Vector.empty
      case BoolPT(_, value) => Vector.empty
      case NameOrRunePT(name) => Vector.empty
      case TypedRunePT(_, name, tyype) => Vector(name)
      case AnonymousRunePT(_) => Vector.empty
      case CallPT(_, template, args) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates((Vector(template) ++ args))
      case FunctionPT(range, mutability, parameters, returnType) => {
        getOrderedRuneDeclarationsFromTemplexesWithDuplicates(mutability.toVector) ++
          getOrderedRuneDeclarationsFromTemplexWithDuplicates(parameters) ++
          getOrderedRuneDeclarationsFromTemplexWithDuplicates(returnType)
      }
      case PrototypePT(_, name, parameters, returnType) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates((parameters :+ returnType))
      case PackPT(_, members) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates(members)
      case StaticSizedArrayPT(_, mutability, variability, size, element) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates(Vector(mutability, variability, size, element))
      case RuntimeSizedArrayPT(_, mutability, element) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates(Vector(mutability, element))
      case TuplePT(_, elements) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates(elements)
    }
  }
}
