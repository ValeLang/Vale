package net.verdagon.vale.scout.rules

import net.verdagon.vale.scout._
import net.verdagon.vale.{vcurious, vimpl}

import scala.collection.immutable.List

sealed trait IRulexSR {
  def range: RangeS
}
case class EqualsSR(range: RangeS, left: IRulexSR, right: IRulexSR) extends IRulexSR { override def hashCode(): Int = vcurious() }
case class IsaSR(range: RangeS, sub: IRulexSR, suuper: IRulexSR) extends IRulexSR { override def hashCode(): Int = vcurious() }
case class OrSR(range: RangeS, alternatives: Vector[IRulexSR]) extends IRulexSR { override def hashCode(): Int = vcurious() }
case class ComponentsSR(
  range: RangeS,
  // This is a TypedSR so that we can know the type, so we can know whether this is
  // a kind components rule or a coord components rule.
  container: TypedSR,
  components: Vector[IRulexSR]
) extends IRulexSR { override def hashCode(): Int = vcurious() }
//case class PackSR(elements: Vector[IRulexSR]) extends IRulexSR { override def hashCode(): Int = vcurious() }
case class TypedSR(range: RangeS, rune: IRuneS, tyype: ITypeSR) extends IRulexSR { override def hashCode(): Int = vcurious() }
case class TemplexSR(templex: ITemplexS) extends IRulexSR {
  override def range: RangeS = templex.range
}
// This is for built-in parser functions, such as exists() or isBaseOf() etc.
case class CallSR(range: RangeS, name: String, args: Vector[IRulexSR]) extends IRulexSR {
}

sealed trait ITypeSR
case object IntTypeSR extends ITypeSR
case object PrototypeTypeSR extends ITypeSR
case object BoolTypeSR extends ITypeSR
case object OwnershipTypeSR extends ITypeSR
case object MutabilityTypeSR extends ITypeSR
case object PermissionTypeSR extends ITypeSR
case object LocationTypeSR extends ITypeSR
case object CoordTypeSR extends ITypeSR
case object KindTypeSR extends ITypeSR
case object FunctionTypeSR extends ITypeSR
case class TemplateTypeSR(params: Vector[ITypeSR], result: ITypeSR) extends ITypeSR { override def hashCode(): Int = vcurious() }
case object VariabilityTypeSR extends ITypeSR
//case object StructTypeSR extends ITypeSR
//case object SequenceTypeSR extends ITypeSR
// We need PackTypeSR because we have a built-in templated destructor whose rules
// only match packs... PackTypeSR is how it does that.
//case object PackTypeSR extends ITypeSR
//case object ArrayTypeSR extends ITypeSR
//case object CallableTypeSR extends ITypeSR
//case object InterfaceTypeSR extends ITypeSR

object RuleSUtils {

  def getDistinctOrderedRunesForRulex(rulex: IRulexSR): Vector[IRuneS] = {
    rulex match {
//      case PackSR(elements) => getDistinctOrderedRunesForRulexes(elements)
      case EqualsSR(range, left, right) => (getDistinctOrderedRunesForRulex(left) ++ getDistinctOrderedRunesForRulex(right)).distinct
      case IsaSR(range, left, right) => (getDistinctOrderedRunesForRulex(left) ++ getDistinctOrderedRunesForRulex(right)).distinct
      case OrSR(range, possibilities) => possibilities.flatMap(getDistinctOrderedRunesForRulex).distinct
      case ComponentsSR(_, container, components) => {
        getDistinctOrderedRunesForRulex(container) ++ components.flatMap(getDistinctOrderedRunesForRulex).toSet
      }
      case TypedSR(_, rune, tyype) => Vector(rune)
      case TemplexSR(templex) => TemplexSUtils.getDistinctOrderedRunesForTemplex(templex)
      case CallSR(_, name, args) => args.flatMap(getDistinctOrderedRunesForRulex).distinct
    }
  }

  def getDistinctOrderedRunesForRulexes(rulexes: Vector[IRulexSR]): Vector[IRuneS] = {
    rulexes.flatMap(getDistinctOrderedRunesForRulex).distinct
  }
}
