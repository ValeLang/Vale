// good

package net.verdagon.vale.parser.ast

import net.verdagon.vale.{vassert, vcurious, vpass}

case class RangeP(begin: Int, end: Int) {
  override def hashCode(): Int = vcurious()
  vassert(begin == end || begin <= end)
}
object RangeP {
  val zero = RangeP(0, 0)
}
// Something that exists in the source code. An Option[UnitP] is better than a boolean
// because it also contains the range it was found.
case class UnitP(range: RangeP) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class NameP(range: RangeP, str: String) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class FileP(topLevelThings: Vector[ITopLevelThingP]) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
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
case class TopLevelFunctionP(function: FunctionP) extends ITopLevelThingP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelStructP(struct: StructP) extends ITopLevelThingP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelInterfaceP(interface: InterfaceP) extends ITopLevelThingP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelImplP(impl: ImplP) extends ITopLevelThingP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelExportAsP(export: ExportAsP) extends ITopLevelThingP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelImportP(imporrt: ImportP) extends ITopLevelThingP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class ImplP(
  range: RangeP,
  identifyingRunes: Option[IdentifyingRunesP],
  templateRules: Option[TemplateRulesP],
  // Option because we can say `impl MyInterface;` inside a struct.
  struct: Option[ITemplexPT],
  interface: ITemplexPT,
  attributes: Vector[IAttributeP]
) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class ExportAsP(
  range: RangeP,
  struct: ITemplexPT,
  exportedName: NameP) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class ImportP(
  range: RangeP,
  moduleName: NameP,
  packageSteps: Vector[NameP],
  importeeName: NameP) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

//sealed trait IAttributeP
//case class ExportP(range: RangeP) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class WeakableAttributeP(range: RangeP) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class SealedAttributeP(range: RangeP) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

sealed trait IMacroInclusion
case object CallMacro extends IMacroInclusion
case object DontCallMacro extends IMacroInclusion
case class MacroCallP(range: RangeP, inclusion: IMacroInclusion, name: NameP) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class StructP(
  range: RangeP,
  name: NameP,
  attributes: Vector[IAttributeP],
  mutability: ITemplexPT,
  identifyingRunes: Option[IdentifyingRunesP],
  templateRules: Option[TemplateRulesP],
  members: StructMembersP) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class StructMembersP(
  range: RangeP,
  contents: Vector[IStructContent]) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
sealed trait IStructContent
case class StructMethodP(func: FunctionP) extends IStructContent { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class NormalStructMemberP(
  range: RangeP,
  name: NameP,
  variability: VariabilityP,
  tyype: ITemplexPT
) extends IStructContent { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class VariadicStructMemberP(
  range: RangeP,
  variability: VariabilityP,
  tyype: ITemplexPT
) extends IStructContent { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class InterfaceP(
  range: RangeP,
  name: NameP,
  attributes: Vector[IAttributeP],
  mutability: ITemplexPT,
  maybeIdentifyingRunes: Option[IdentifyingRunesP],
  templateRules: Option[TemplateRulesP],
  members: Vector[FunctionP]) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

sealed trait IAttributeP
case class AbstractAttributeP(range: RangeP) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ExternAttributeP(range: RangeP) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BuiltinAttributeP(range: RangeP, generatorName: NameP) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ExportAttributeP(range: RangeP) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class PureAttributeP(range: RangeP) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
//case class RuleAttributeP(rule: IRulexPR) extends IAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

sealed trait IRuneAttributeP
case class TypeRuneAttributeP(range: RangeP, tyype: ITypePR) extends IRuneAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ReadOnlyRuneAttributeP(range: RangeP) extends IRuneAttributeP {
  vpass()
}
case class PoolRuneAttributeP(range: RangeP) extends IRuneAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ArenaRuneAttributeP(range: RangeP) extends IRuneAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BumpRuneAttributeP(range: RangeP) extends IRuneAttributeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class IdentifyingRuneP(range: RangeP, name: NameP, attributes: Vector[IRuneAttributeP]) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class IdentifyingRunesP(range: RangeP, runes: Vector[IdentifyingRuneP]) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TemplateRulesP(range: RangeP, rules: Vector[IRulexPR]) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ParamsP(range: RangeP, patterns: Vector[PatternPP]) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class FunctionP(
  range: RangeP,
  header: FunctionHeaderP,
  body: Option[BlockPE]) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class FunctionReturnP(
  range: RangeP,
  inferRet: Option[UnitP],
  retType: Option[ITemplexPT]
) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class FunctionHeaderP(
  range: RangeP,
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
