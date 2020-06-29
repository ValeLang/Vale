// good

package net.verdagon.vale.parser

import net.verdagon.vale.vassert

case class Pos(line: Int, col: Int) {
  def <(that: Pos): Boolean = {
    if (this.line < that.line) { true }
    else if (that.line < this.line) { false }
    else if (this.col < that.col) { true }
    else if (that.col < this.col) { false }
    else { false }
  }
}

case class Range(begin: Pos, end: Pos) {
  vassert(begin == end || begin < end)
}
object Range {
  val zero = Range(Pos(0, 0), Pos(0, 0))
}
// Something that exists in the source code. An Option[UnitP] is better than a boolean
// because it also contains the range it was found.
case class UnitP(range: Range)
case class StringP(range: Range, str: String)

case class Program0(topLevelThings: List[ITopLevelThing]) {
  def lookupFunction(name: String) = {
    val results =
      topLevelThings.collect({
        case TopLevelFunction(f) if f.name.exists(_.str == name) => f
      })
    vassert(results.size == 1)
    results.head
  }
}

sealed trait ITopLevelThing
case class TopLevelFunction(function: FunctionP) extends ITopLevelThing
case class TopLevelStruct(struct: StructP) extends ITopLevelThing
case class TopLevelInterface(interface: InterfaceP) extends ITopLevelThing
case class TopLevelImpl(impl: ImplP) extends ITopLevelThing

case class ImplP(
  range: Range,
  identifyingRunes: Option[IdentifyingRunesP],
  rules: Option[TemplateRulesP],
  struct: ITemplexPT,
  interface: ITemplexPT)

case class StructP(
  range: Range,
  name: StringP,
  export: Boolean,
  mutability: MutabilityP,
  identifyingRunes: Option[IdentifyingRunesP],
  templateRules: Option[TemplateRulesP],
  members: StructMembersP)

case class StructMembersP(
  range: Range,
  contents: List[IStructContent])
sealed trait IStructContent
case class StructMethodP(func: FunctionP) extends IStructContent
case class StructMemberP(
  range: Range,
  name: StringP,
  variability: VariabilityP,
  tyype: ITemplexPT) extends IStructContent

case class InterfaceP(
    range: Range,
    name: StringP,
    seealed: Option[UnitP],
    mutability: MutabilityP,
    maybeIdentifyingRunes: Option[IdentifyingRunesP],
    templateRules: Option[TemplateRulesP],
    members: List[FunctionP])

case class IdentifyingRunesP(range: Range, runes: List[StringP])
case class TemplateRulesP(range: Range, rules: List[IRulexPR])
case class ParamsP(range: Range, patterns: List[PatternPP])
case class FunctionP(
  range: Range,
  name: Option[StringP],
  isExtern: Option[UnitP],
  isAbstract: Option[UnitP],

  // If Some(List()), should show up like the <> in fn moo<>(a Int, b Bool)
  maybeUserSpecifiedIdentifyingRunes: Option[IdentifyingRunesP],
  templateRules: Option[TemplateRulesP],

  params: Option[ParamsP],
  ret: Option[ITemplexPT],
  body: Option[BlockPE]
)




sealed trait MutabilityP
case object MutableP extends MutabilityP
case object ImmutableP extends MutabilityP

sealed trait VariabilityP
case object FinalP extends VariabilityP
case object VaryingP extends VariabilityP

sealed trait OwnershipP
case object OwnP extends OwnershipP
case object BorrowP extends OwnershipP
case object ShareP extends OwnershipP

sealed trait PermissionP
case object ReadonlyP extends PermissionP
case object ReadwriteP extends PermissionP
case object ExclusiveReadwriteP extends PermissionP

sealed trait LocationP
case object InlineP extends LocationP
case object YonderP extends LocationP
