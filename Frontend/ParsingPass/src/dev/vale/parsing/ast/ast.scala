// good

package dev.vale.parsing.ast

import dev.vale.lexing.RangeL
import dev.vale.{FileCoordinate, StrI, vassert, vcurious, vpass}

// Something that exists in the source code. An Option[UnitP] is better than a boolean
// because it also contains the range it was found.
case class UnitP(range: RangeL) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class NameP(range: RangeL, str: StrI) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class FileP(
  fileCoord: FileCoordinate,
  commentsRanges: Array[RangeL],
  denizens: Array[IDenizenP]) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  def lookupFunction(name: String) = {
    val results =
      denizens.collect({
        case TopLevelFunctionP(f) if f.header.name.exists(_.str == name) => f
      })
    vassert(results.size == 1)
    results.head
  }
}

sealed trait IDenizenP
case class TopLevelFunctionP(function: FunctionP) extends IDenizenP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelStructP(struct: StructP) extends IDenizenP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelInterfaceP(interface: InterfaceP) extends IDenizenP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelImplP(impl: ImplP) extends IDenizenP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelExportAsP(export: ExportAsP) extends IDenizenP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelImportP(imporrt: ImportP) extends IDenizenP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class ImplP(
  range: RangeL,
  identifyingRunes: Option[IdentifyingRunesP],
  templateRules: Option[TemplateRulesP],
  // Option because we can say `impl MyInterface;` inside a struct.
  struct: Option[ITemplexPT],
  interface: ITemplexPT,
  attributes: Vector[IAttributeP]
) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class ExportAsP(
  range: RangeL,
  struct: ITemplexPT,
  exportedName: NameP) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class ImportP(
  range: RangeL,
  moduleName: NameP,
  packageSteps: Vector[NameP],
  importeeName: NameP) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

//sealed trait IAttributeP
//case class ExportP(range: RangeP) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class WeakableAttributeP(range: RangeL) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class SealedAttributeP(range: RangeL) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

sealed trait IMacroInclusionP
case object CallMacroP extends IMacroInclusionP
case object DontCallMacroP extends IMacroInclusionP
case class MacroCallP(range: RangeL, inclusion: IMacroInclusionP, name: NameP) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class StructP(
  range: RangeL,
  name: NameP,
  attributes: Vector[IAttributeP],
  mutability: Option[ITemplexPT],
  identifyingRunes: Option[IdentifyingRunesP],
  templateRules: Option[TemplateRulesP],
  members: StructMembersP) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class StructMembersP(
  range: RangeL,
  contents: Vector[IStructContent]) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
sealed trait IStructContent
case class StructMethodP(func: FunctionP) extends IStructContent { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class NormalStructMemberP(
  range: RangeL,
  name: NameP,
  variability: VariabilityP,
  tyype: ITemplexPT
) extends IStructContent { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class VariadicStructMemberP(
  range: RangeL,
  variability: VariabilityP,
  tyype: ITemplexPT
) extends IStructContent { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class InterfaceP(
  range: RangeL,
  name: NameP,
  attributes: Vector[IAttributeP],
  mutability: ITemplexPT,
  maybeIdentifyingRunes: Option[IdentifyingRunesP],
  templateRules: Option[TemplateRulesP],
  members: Vector[FunctionP]) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

sealed trait IAttributeP
case class AbstractAttributeP(range: RangeL) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ExternAttributeP(range: RangeL) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BuiltinAttributeP(range: RangeL, generatorName: NameP) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ExportAttributeP(range: RangeL) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class PureAttributeP(range: RangeL) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
//case class RuleAttributeP(rule: IRulexPR) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

sealed trait IRuneAttributeP
case class TypeRuneAttributeP(range: RangeL, tyype: ITypePR) extends IRuneAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ReadOnlyRuneAttributeP(range: RangeL) extends IRuneAttributeP
case class ReadWriteRuneAttributeP(range: RangeL) extends IRuneAttributeP
case class ImmutableRuneAttributeP(range: RangeL) extends IRuneAttributeP
case class PoolRuneAttributeP(range: RangeL) extends IRuneAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ArenaRuneAttributeP(range: RangeL) extends IRuneAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BumpRuneAttributeP(range: RangeL) extends IRuneAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class IdentifyingRuneP(range: RangeL, name: NameP, attributes: Vector[IRuneAttributeP]) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class IdentifyingRunesP(range: RangeL, runes: Vector[IdentifyingRuneP]) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TemplateRulesP(range: RangeL, rules: Vector[IRulexPR]) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ParamsP(range: RangeL, patterns: Vector[PatternPP]) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class FunctionP(
  range: RangeL,
  header: FunctionHeaderP,
  body: Option[BlockPE]) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class FunctionReturnP(
  range: RangeL,
  inferRet: Option[RangeL],
  retType: Option[ITemplexPT]
) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class FunctionHeaderP(
  range: RangeL,
  name: Option[NameP],
  attributes: Vector[IAttributeP],

  // If Some(Vector.empty), should show up like the <> in func moo<>(a int, b bool)
  maybeUserSpecifiedIdentifyingRunes: Option[IdentifyingRunesP],
  templateRules: Option[TemplateRulesP],

  params: Option[ParamsP],
  ret: FunctionReturnP
) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}


sealed trait MutabilityP
case object MutableP extends MutabilityP { override def toString: String = "mut" }
case object ImmutableP extends MutabilityP { override def toString: String = "imm" }

sealed trait VariabilityP
case object FinalP extends VariabilityP { override def toString: String = "final" }
case object VaryingP extends VariabilityP { override def toString: String = "vary" }

sealed trait OwnershipP
case object OwnP extends OwnershipP { override def toString: String = "own" }
case object BorrowP extends OwnershipP { override def toString: String = "borrow" }
case object WeakP extends OwnershipP { override def toString: String = "weak" }
case object ShareP extends OwnershipP { override def toString: String = "share" }

// This represents how to load something.
// If something's a Share, then nothing will happen,
// so this only applies to mutables.
sealed trait LoadAsP
// This means we want to move it. Thisll become a OwnP or ShareP.
case object MoveP extends LoadAsP
// This means we want to use it, and want to make sure that it doesn't drop.
// If permission is None, then we're probably in a dot. For example, x.launch()
// should be mapped to launch(&!x) if x is mutable, or launch(&x) if it's readonly.
case object LoadAsBorrowP extends LoadAsP { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
// This means we want to get a weak reference to it. Thisll become a WeakP.
case object LoadAsWeakP extends LoadAsP { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
// This represents unspecified. It basically means, use whatever ownership already there.
case object UseP extends LoadAsP

sealed trait LocationP
case object InlineP extends LocationP { override def toString: String = "inl" }
case object YonderP extends LocationP { override def toString: String = "heap" }
