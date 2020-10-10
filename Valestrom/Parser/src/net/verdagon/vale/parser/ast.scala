// good

package net.verdagon.vale.parser

import net.verdagon.vale.vassert

case class Range(begin: Int, end: Int) {
  vassert(begin == end || begin <= end)
}
object Range {
  val zero = Range(0, 0)
}
// Something that exists in the source code. An Option[UnitP] is better than a boolean
// because it also contains the range it was found.
case class UnitP(range: Range)
case class StringP(range: Range, str: String)

case class FileP(topLevelThings: List[ITopLevelThingP]) {
  def lookupFunction(name: String) = {
    val results =
      topLevelThings.collect({
        case TopLevelFunctionP(f) if f.header.name.exists(_.str == name) => f
      })
    vassert(results.size == 1)
    results.head
  }
}

sealed trait ITopLevelThingP
case class TopLevelFunctionP(function: FunctionP) extends ITopLevelThingP
case class TopLevelStructP(struct: StructP) extends ITopLevelThingP
case class TopLevelInterfaceP(interface: InterfaceP) extends ITopLevelThingP
case class TopLevelImplP(impl: ImplP) extends ITopLevelThingP

case class ImplP(
  range: Range,
  identifyingRunes: Option[IdentifyingRunesP],
  rules: Option[TemplateRulesP],
  struct: ITemplexPT,
  interface: ITemplexPT)

sealed trait ICitizenAttributeP
case class ExportP(range: Range) extends ICitizenAttributeP
case class WeakableP(range: Range) extends ICitizenAttributeP
case class SealedP(range: Range) extends ICitizenAttributeP

case class StructP(
  range: Range,
  name: StringP,
  attributes: List[ICitizenAttributeP],
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
    attributes: List[ICitizenAttributeP],
    mutability: MutabilityP,
    maybeIdentifyingRunes: Option[IdentifyingRunesP],
    templateRules: Option[TemplateRulesP],
    members: List[FunctionP])

sealed trait IFunctionAttributeP
case class AbstractAttributeP(range: Range) extends IFunctionAttributeP
case class ExternAttributeP(range: Range) extends IFunctionAttributeP
case class ExportAttributeP(range: Range) extends IFunctionAttributeP
case class PureAttributeP(range: Range) extends IFunctionAttributeP

sealed trait IRuneAttributeP
case class TypeRuneAttributeP(range: Range, tyype: ITypePR) extends IRuneAttributeP
case class ReadOnlyRuneAttributeP(range: Range) extends IRuneAttributeP
case class PoolRuneAttributeP(range: Range) extends IRuneAttributeP
case class ArenaRuneAttributeP(range: Range) extends IRuneAttributeP
case class BumpRuneAttributeP(range: Range) extends IRuneAttributeP

case class IdentifyingRuneP(range: Range, name: StringP, attributes: List[IRuneAttributeP])

case class IdentifyingRunesP(range: Range, runes: List[IdentifyingRuneP])
case class TemplateRulesP(range: Range, rules: List[IRulexPR])
case class ParamsP(range: Range, patterns: List[PatternPP])

case class FunctionP(
  range: Range,
  header: FunctionHeaderP,
  body: Option[BlockPE])

case class FunctionReturnP(
  range: Range,
  inferRet: Option[UnitP],
  retType: Option[ITemplexPT]
)

case class FunctionHeaderP(
  range: Range,
  name: Option[StringP],
  attributes: List[IFunctionAttributeP],

  // If Some(List()), should show up like the <> in fn moo<>(a int, b bool)
  maybeUserSpecifiedIdentifyingRunes: Option[IdentifyingRunesP],
  templateRules: Option[TemplateRulesP],

  params: Option[ParamsP],
  ret: FunctionReturnP
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
case object WeakP extends OwnershipP
case object ShareP extends OwnershipP

sealed trait PermissionP
case object ReadonlyP extends PermissionP
case object ReadwriteP extends PermissionP
case object ExclusiveReadwriteP extends PermissionP

sealed trait LocationP
case object InlineP extends LocationP
case object YonderP extends LocationP
