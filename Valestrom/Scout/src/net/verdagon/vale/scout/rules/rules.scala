package net.verdagon.vale.scout.rules

import net.verdagon.vale.scout._

import scala.collection.immutable.List

sealed trait IRulexSR {
  def range: RangeS
}
case class EqualsSR(range: RangeS, left: IRulexSR, right: IRulexSR) extends IRulexSR
case class IsaSR(range: RangeS, sub: IRulexSR, suuper: IRulexSR) extends IRulexSR
case class OrSR(range: RangeS, alternatives: List[IRulexSR]) extends IRulexSR
case class ComponentsSR(
  range: RangeS,
  // This is a TypedSR so that we can know the type, so we can know whether this is
  // a kind components rule or a coord components rule.
  container: TypedSR,
  components: List[IRulexSR]
) extends IRulexSR
//case class PackSR(elements: List[IRulexSR]) extends IRulexSR
case class TypedSR(range: RangeS, rune: IRuneS, tyype: ITypeSR) extends IRulexSR
case class TemplexSR(templex: ITemplexS) extends IRulexSR {
  override def range: RangeS = templex.range
}
// This is for built-in parser functions, such as exists() or isBaseOf() etc.
case class CallSR(range: RangeS, name: String, args: List[IRulexSR]) extends IRulexSR {
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
case class TemplateTypeSR(params: List[ITypeSR], result: ITypeSR) extends ITypeSR
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

  def getDistinctOrderedRunesForRulex(rulex: IRulexSR): List[IRuneS] = {
    rulex match {
//      case PackSR(elements) => getDistinctOrderedRunesForRulexes(elements)
      case EqualsSR(range, left, right) => (getDistinctOrderedRunesForRulex(left) ++ getDistinctOrderedRunesForRulex(right)).distinct
      case IsaSR(range, left, right) => (getDistinctOrderedRunesForRulex(left) ++ getDistinctOrderedRunesForRulex(right)).distinct
      case OrSR(range, possibilities) => possibilities.flatMap(getDistinctOrderedRunesForRulex).distinct
      case ComponentsSR(_, container, components) => {
        getDistinctOrderedRunesForRulex(container) ++ components.flatMap(getDistinctOrderedRunesForRulex).toSet
      }
      case TypedSR(_, rune, tyype) => List(rune)
      case TemplexSR(templex) => TemplexSUtils.getDistinctOrderedRunesForTemplex(templex)
      case CallSR(_, name, args) => args.flatMap(getDistinctOrderedRunesForRulex).distinct
    }
  }

  def getDistinctOrderedRunesForRulexes(rulexes: List[IRulexSR]): List[IRuneS] = {
    rulexes.flatMap(getDistinctOrderedRunesForRulex).distinct
  }
}
