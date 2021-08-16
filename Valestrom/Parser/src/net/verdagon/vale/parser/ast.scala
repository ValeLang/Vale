// good

package net.verdagon.vale.parser

import net.verdagon.vale.{vassert, vcurious, vimpl, vpass}

case class Range(begin: Int, end: Int) {
  override def hashCode(): Int = vcurious()
  vassert(begin == end || begin <= end)
}
object Range {
  val zero = Range(0, 0)
}
// Something that exists in the source code. An Option[UnitP] is better than a boolean
// because it also contains the range it was found.
case class UnitP(range: Range) { override def hashCode(): Int = vcurious() }
case class NameP(range: Range, str: String) { override def hashCode(): Int = vcurious() }

case class FileP(topLevelThings: Vector[ITopLevelThingP]) {
  override def hashCode(): Int = vcurious()
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
case class TopLevelFunctionP(function: FunctionP) extends ITopLevelThingP { override def hashCode(): Int = vcurious() }
case class TopLevelStructP(struct: StructP) extends ITopLevelThingP { override def hashCode(): Int = vcurious() }
case class TopLevelInterfaceP(interface: InterfaceP) extends ITopLevelThingP { override def hashCode(): Int = vcurious() }
case class TopLevelImplP(impl: ImplP) extends ITopLevelThingP { override def hashCode(): Int = vcurious() }
case class TopLevelExportAsP(export: ExportAsP) extends ITopLevelThingP { override def hashCode(): Int = vcurious() }
case class TopLevelImportP(imporrt: ImportP) extends ITopLevelThingP { override def hashCode(): Int = vcurious() }

case class ImplP(
  range: Range,
  identifyingRunes: Option[IdentifyingRunesP],
  rules: Option[TemplateRulesP],
  struct: ITemplexPT,
  interface: ITemplexPT) { override def hashCode(): Int = vcurious() }

case class ExportAsP(
  range: Range,
  struct: ITemplexPT,
  exportedName: NameP) { override def hashCode(): Int = vcurious() }

case class ImportP(
  range: Range,
  moduleName: NameP,
  packageSteps: Vector[NameP],
  importeeName: NameP) { override def hashCode(): Int = vcurious() }

sealed trait ICitizenAttributeP
case class ExportP(range: Range) extends ICitizenAttributeP { override def hashCode(): Int = vcurious() }
case class WeakableP(range: Range) extends ICitizenAttributeP { override def hashCode(): Int = vcurious() }
case class SealedP(range: Range) extends ICitizenAttributeP { override def hashCode(): Int = vcurious() }

case class StructP(
  range: Range,
  name: NameP,
  attributes: Vector[ICitizenAttributeP],
  mutability: MutabilityP,
  identifyingRunes: Option[IdentifyingRunesP],
  templateRules: Option[TemplateRulesP],
  members: StructMembersP) { override def hashCode(): Int = vcurious() }

case class StructMembersP(
  range: Range,
  contents: Vector[IStructContent]) { override def hashCode(): Int = vcurious() }
sealed trait IStructContent
case class StructMethodP(func: FunctionP) extends IStructContent { override def hashCode(): Int = vcurious() }
case class StructMemberP(
  range: Range,
  name: NameP,
  variability: VariabilityP,
  tyype: ITemplexPT) extends IStructContent { override def hashCode(): Int = vcurious() }

case class InterfaceP(
                       range: Range,
                       name: NameP,
                       attributes: Vector[ICitizenAttributeP],
                       mutability: MutabilityP,
                       maybeIdentifyingRunes: Option[IdentifyingRunesP],
                       templateRules: Option[TemplateRulesP],
                       members: Vector[FunctionP]) { override def hashCode(): Int = vcurious() }

sealed trait IFunctionAttributeP
case class AbstractAttributeP(range: Range) extends IFunctionAttributeP { override def hashCode(): Int = vcurious() }
case class ExternAttributeP(range: Range) extends IFunctionAttributeP { override def hashCode(): Int = vcurious() }
case class BuiltinAttributeP(range: Range, generatorName: NameP) extends IFunctionAttributeP { override def hashCode(): Int = vcurious() }
case class ExportAttributeP(range: Range) extends IFunctionAttributeP { override def hashCode(): Int = vcurious() }
case class PureAttributeP(range: Range) extends IFunctionAttributeP { override def hashCode(): Int = vcurious() }

sealed trait IRuneAttributeP
case class TypeRuneAttributeP(range: Range, tyype: ITypePR) extends IRuneAttributeP { override def hashCode(): Int = vcurious() }
case class ReadOnlyRuneAttributeP(range: Range) extends IRuneAttributeP {
  vpass()
}
case class PoolRuneAttributeP(range: Range) extends IRuneAttributeP { override def hashCode(): Int = vcurious() }
case class ArenaRuneAttributeP(range: Range) extends IRuneAttributeP { override def hashCode(): Int = vcurious() }
case class BumpRuneAttributeP(range: Range) extends IRuneAttributeP { override def hashCode(): Int = vcurious() }

case class IdentifyingRuneP(range: Range, name: NameP, attributes: Vector[IRuneAttributeP]) { override def hashCode(): Int = vcurious() }

case class IdentifyingRunesP(range: Range, runes: Vector[IdentifyingRuneP]) { override def hashCode(): Int = vcurious() }
case class TemplateRulesP(range: Range, rules: Vector[IRulexPR]) { override def hashCode(): Int = vcurious() }
case class ParamsP(range: Range, patterns: Vector[PatternPP]) { override def hashCode(): Int = vcurious() }

case class FunctionP(
  range: Range,
  header: FunctionHeaderP,
  body: Option[BlockPE]) { override def hashCode(): Int = vcurious() }

case class FunctionReturnP(
  range: Range,
  inferRet: Option[UnitP],
  retType: Option[ITemplexPT]
) { override def hashCode(): Int = vcurious() }

case class FunctionHeaderP(
                            range: Range,
                            name: Option[NameP],
                            attributes: Vector[IFunctionAttributeP],

                            // If Some(Vector.empty), should show up like the <> in fn moo<>(a int, b bool)
                            maybeUserSpecifiedIdentifyingRunes: Option[IdentifyingRunesP],
                            templateRules: Option[TemplateRulesP],

                            params: Option[ParamsP],
                            ret: FunctionReturnP
) {
  override def hashCode(): Int = vcurious()
}


sealed trait MutabilityP
case object MutableP extends MutabilityP { override def toString: String = "mut" }
case object ImmutableP extends MutabilityP { override def toString: String = "imm" }

sealed trait VariabilityP
case object FinalP extends VariabilityP { override def toString: String = "final" }
case object VaryingP extends VariabilityP { override def toString: String = "vary" }

sealed trait OwnershipP
case object OwnP extends OwnershipP { override def toString: String = "own" }
case object ConstraintP extends OwnershipP { override def toString: String = "constraint" }
case object WeakP extends OwnershipP { override def toString: String = "weak" }
case object ShareP extends OwnershipP { override def toString: String = "share" }

// This represents how to load something.
// If something's a Share, then nothing will happen,
// so this only applies to mutables.
sealed trait LoadAsP
// This means we want to move it. Thisll become a OwnP or ShareP.
case object MoveP extends LoadAsP
// This means we want to use it, but don't want to own it. This will
// probably become a BorrowP or ShareP.
// If permission is None, then we're probably in a dot. For example, x.launch()
// should be mapped to launch(&!x) if x is mutable, or launch(&x) if it's readonly.
case class LendConstraintP(permission: Option[PermissionP]) extends LoadAsP { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
// This means we want to get a weak reference to it. Thisll become a WeakP.
case class LendWeakP(permission: PermissionP) extends LoadAsP { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
// This represents unspecified. It basically means, use whatever ownership already there.
case object UseP extends LoadAsP

sealed trait PermissionP
case object ReadonlyP extends PermissionP { override def toString: String = "ro" }
case object ReadwriteP extends PermissionP { override def toString: String = "rw" }
case object ExclusiveReadwriteP extends PermissionP { override def toString: String = "xrw" }

sealed trait LocationP
case object InlineP extends LocationP { override def toString: String = "inl" }
case object YonderP extends LocationP { override def toString: String = "heap" }
