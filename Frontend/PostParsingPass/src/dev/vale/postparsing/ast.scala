package dev.vale.postparsing

import dev.vale._
import dev.vale.parsing.ast.{IMacroInclusionP, IRuneAttributeP, MutabilityP, VariabilityP}
import dev.vale.postparsing.rules.{IRulexSR, RuneUsage}
import dev.vale.parsing._
import dev.vale.postparsing.patterns._
import dev.vale.postparsing.rules._

import scala.collection.immutable.List

trait IExpressionSE {
  def range: RangeS
}

case class ProgramS(
    structs: Vector[StructS],
    interfaces: Vector[InterfaceS],
    impls: Vector[ImplS],
    implementedFunctions: Vector[FunctionS],
    exports: Vector[ExportAsS],
    imports: Vector[ImportS]) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  def lookupFunction(name: String): FunctionS = {
    val matches =
      implementedFunctions
        .find(f => f.name match { case FunctionNameS(n, _) => n.str == name })
    vassert(matches.size == 1)
    matches.head
  }
  def lookupInterface(name: String): InterfaceS = {
    val matches =
      interfaces
        .find(f => f.name match { case TopLevelCitizenDeclarationNameS(n, _) => n.str == name })
    vassert(matches.size == 1)
    matches.head
  }
  def lookupStruct(name: String): StructS = {
    val matches =
      structs
        .find(f => f.name match { case TopLevelCitizenDeclarationNameS(n, _) => n.str == name })
    vassert(matches.size == 1)
    matches.head
  }
}

sealed trait ICitizenAttributeS
sealed trait IFunctionAttributeS
case class ExternS(packageCoord: PackageCoordinate, defaultRegionRune: IRuneS) extends IFunctionAttributeS with ICitizenAttributeS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case object PureS extends IFunctionAttributeS
case object AdditiveS extends IFunctionAttributeS
case object SealedS extends ICitizenAttributeS
case class BuiltinS(generatorName: StrI) extends IFunctionAttributeS with ICitizenAttributeS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case class MacroCallS(range: RangeS, include: IMacroInclusionP, macroName: StrI) extends ICitizenAttributeS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case class ExportS(packageCoordinate: PackageCoordinate, defaultRegionRune: IRuneS) extends IFunctionAttributeS with ICitizenAttributeS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case object UserFunctionS extends IFunctionAttributeS // Whether it was written by a human. Mostly for tests right now.

sealed trait ICitizenS {
  def name: ICitizenDeclarationNameS
  def tyype: TemplateTemplataType
  def genericParams: Vector[GenericParameterS]
  def regionRune: IRuneS
}

case class StructS(
    range: RangeS,
    name: TopLevelStructDeclarationNameS,
    attributes: Vector[ICitizenAttributeS],
    weakable: Boolean,
    genericParams: Vector[GenericParameterS],
    mutabilityRune: RuneUsage,

    // This is needed for recursive structures like
    //   struct ListNode<T> imm where T Ref {
    //     tail ListNode<T>;
    //   }
    maybePredictedMutability: Option[MutabilityP],
    tyype: TemplateTemplataType,

    // These are separated so that these alone can be run during resolving, see SMRASDR.
    headerRuneToExplicitType: Map[IRuneS, ITemplataType],
    headerPredictedRuneToType: Map[IRuneS, ITemplataType],
    headerRules: Vector[IRulexSR],
    // These are separated so they can be skipped during resolving, see SMRASDR.
    membersRuneToExplicitType: Map[IRuneS, ITemplataType],
    membersPredictedRuneToType: Map[IRuneS, ITemplataType],
    memberRules: Vector[IRulexSR],

    regionRune: IRuneS,

    members: Vector[IStructMemberS]
) extends ICitizenS {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

//  vassert(isTemplate == identifyingRunes.nonEmpty)
}

sealed trait IStructMemberS {
  def range: RangeS
  def variability: VariabilityP
  def typeRune: RuneUsage
}
case class NormalStructMemberS(
    range: RangeS,
    name: StrI,
    variability: VariabilityP,
    typeRune: RuneUsage) extends IStructMemberS {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}
case class VariadicStructMemberS(
  range: RangeS,
  variability: VariabilityP,
  typeRune: RuneUsage) extends IStructMemberS {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

case class InterfaceS(
  range: RangeS,
  name: TopLevelInterfaceDeclarationNameS,
  attributes: Vector[ICitizenAttributeS],
  weakable: Boolean,
  genericParams: Vector[GenericParameterS],
  runeToExplicitType: Map[IRuneS, ITemplataType],
  mutabilityRune: RuneUsage,

  // This is needed for recursive structures like
  //   struct ListNode<T> imm where T Ref {
  //     tail ListNode<T>;
  //   }
  maybePredictedMutability: Option[MutabilityP],
  predictedRuneToType: Map[IRuneS, ITemplataType],
  tyype: TemplateTemplataType,

  rules: Vector[IRulexSR],

  regionRune: IRuneS,

  // See IMRFDI
  internalMethods: Vector[FunctionS]
) extends ICitizenS {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  internalMethods.foreach(internalMethod => {
    // .init because every method has a default region as the last region param.
    vassert(genericParams == internalMethod.genericParams.init)
  })

}

case class ImplS(
    range: RangeS,
    // The name of an impl is the human name of the subcitizen, see INSHN.
    name: ImplDeclarationNameS,
    userSpecifiedIdentifyingRunes: Vector[GenericParameterS],
    rules: Vector[IRulexSR],
    runeToExplicitType: Map[IRuneS, ITemplataType],
    tyype: ITemplataType,
    structKindRune: RuneUsage,
    subCitizenImpreciseName: IImpreciseNameS,
    interfaceKindRune: RuneUsage,
    superInterfaceImpreciseName: IImpreciseNameS) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

case class ExportAsS(
  range: RangeS,
  rules: Vector[IRulexSR],
  regionGenericParam: GenericParameterS,
  defaultRegionRune: IRuneS,
  exportName: ExportAsNameS,
  rune: RuneUsage,
  exportedName: StrI) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

case class ImportS(
  range: RangeS,
  moduleName: StrI,
  packageNames: Vector[StrI],
  importeeName: StrI) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

object interfaceSName {
  // The extraction method (mandatory)
  def unapply(interfaceS: InterfaceS): Option[TopLevelCitizenDeclarationNameS] = {
    Some(interfaceS.name)
  }
}

object structSName {
  // The extraction method (mandatory)
  def unapply(structS: StructS): Option[TopLevelCitizenDeclarationNameS] = {
    Some(structS.name)
  }
}

// remember, by doing a "m", CaptureSP("m", Destructure("Marine", Vector("hp, "item"))), by having that
// CaptureSP/"m" there, we're changing the nature of that Destructure; "hp" and "item" will be
// borrows rather than owns.

// So, when the scout is assigning everything a name, it's actually forcing us to always have
// borrowing destructures.

// We should change Scout to not assign names... or perhaps, it can assign names for the parameters,
// but secretly, typingpass will consider arguments to have actual names of __arg_0, __arg_1, and let
// the PatternCompiler introduce the actual names.

// Also remember, if a parameter has no name, it can't be varying.

case class ParameterS(
  range: RangeS,
  virtuality: Option[AbstractSP],
  preChecked: Boolean,
  outerRegionRune: IRuneS, // See PMHBRS
  pattern: AtomSP) {

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  vassert(pattern.coordRune.nonEmpty)
}

case class AbstractSP(
  range: RangeS,
  // True if this is defined inside an interface
  // False if this is a free function somewhere else
  isInternalMethod: Boolean
)

case class SimpleParameterS(
    origin: Option[AtomSP],
    name: String,
    virtuality: Option[AbstractSP],
    tyype: IRulexSR) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

sealed trait IBodyS
case object ExternBodyS extends IBodyS
case object AbstractBodyS extends IBodyS
case class GeneratedBodyS(generatorId: StrI) extends IBodyS {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}
case class CodeBodyS(body: BodySE) extends IBodyS {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

sealed trait IRegionMutabilityS
case object ReadWriteRegionS extends IRegionMutabilityS
case object ReadOnlyRegionS extends IRegionMutabilityS
case object ImmutableRegionS extends IRegionMutabilityS
case object AdditiveRegionS extends IRegionMutabilityS

object IGenericParameterTypeS {
  def expectRegion(x: IGenericParameterTypeS): RegionGenericParameterTypeS = {
    x match {
      case z @ RegionGenericParameterTypeS(_) => z
      case _ => vfail()
    }
  }
}
sealed trait IGenericParameterTypeS {
  def tyype: ITemplataType
}
case class RegionGenericParameterTypeS(mutability: IRegionMutabilityS) extends IGenericParameterTypeS {
  def tyype: ITemplataType = RegionTemplataType()
}
case class CoordGenericParameterTypeS(
    coordRegion: Option[RuneUsage],
    kindMutable: Boolean,
    regionMutable: Boolean
) extends IGenericParameterTypeS {
  vassert(coordRegion.isEmpty) // not implemented yet

  def tyype: ITemplataType = CoordTemplataType()
}
case class OtherGenericParameterTypeS(tyype: ITemplataType) extends IGenericParameterTypeS {
  tyype match {
    case RegionTemplataType() | CoordTemplataType() => vwat() // Use other types for this
    case _ =>
  }
}

case class GenericParameterS(
  range: RangeS,
  rune: RuneUsage,
  tyype: IGenericParameterTypeS,
  default: Option[GenericParameterDefaultS])

//sealed trait IRuneAttributeS
//case class ImmutableRuneAttributeS(range: RangeS) extends IRuneAttributeS
//case class ReadWriteRuneAttributeS(range: RangeS) extends IRuneAttributeS
//case class ReadOnlyRuneAttributeS(range: RangeS) extends IRuneAttributeS

case class GenericParameterDefaultS(
  // One day, when we want more rules in here, we might need to have a runeToType map
  // and other things to make it its own little world.
  resultRune: IRuneS,
  rules: Vector[IRulexSR])

// Underlying class for all XYZFunctionS types
case class FunctionS(
  range: RangeS,
  name: IFunctionDeclarationNameS,
  attributes: Vector[IFunctionAttributeS],

  genericParams: Vector[GenericParameterS],
  runeToPredictedType: Map[IRuneS, ITemplataType],
  tyype: TemplateTemplataType,

  params: Vector[ParameterS],

  // We need to leave it an option to signal that the compiler can infer the return type.
  maybeRetCoordRune: Option[RuneUsage],

  defaultRegion: IRuneS,

  rules: Vector[IRulexSR],
  body: IBodyS
) {
  vpass()

  // Every function needs a region generic parameter, see DRIAGP.
  vassert(genericParams.nonEmpty)
  vassert(genericParams.last.rune.rune == defaultRegion)

  body match {
    case ExternBodyS | AbstractBodyS | GeneratedBodyS(_) => {
      name match {
        case LambdaDeclarationNameS(_) => vwat()
        case _ =>
      }
    }
    case CodeBodyS(body) => {
      if (body.closuredNames.nonEmpty) {
        name match {
          case LambdaDeclarationNameS(_) =>
          case _ => vwat()
        }
      }
    }
  }

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  def isLight(): Boolean = {
    body match {
      case ExternBodyS | AbstractBodyS | GeneratedBodyS(_) => false
      case CodeBodyS(bodyS) => bodyS.closuredNames.nonEmpty
    }
  }
}

// A Denizen is a thing at the top level of a file, like structs, functions, impls, exports, etc.
// This is a class with a consumed boolean so that we're sure we don't use it twice.
// Anyone that uses it should call the consume() method.
// Move semantics would be nice here... alas.
class LocationInDenizenBuilder(path: Vector[Int]) {
  private var consumed: Boolean = false
  private var nextChild: Int = 1

  // Note how this is hashing `path`, not `this` like usual.
  val hash = runtime.ScalaRunTime._hashCode(path.toList); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();

  def child(): LocationInDenizenBuilder = {
    val child = nextChild
    nextChild = nextChild + 1
    new LocationInDenizenBuilder(path :+ child)
  }

  def consume(): LocationInDenizen = {
    assert(!consumed, "Location in denizen was already used for something, add a .child() somewhere.")
    consumed = true
    LocationInDenizen(path)
  }

  override def toString: String = path.mkString(".")
}

case class LocationInDenizen(path: Vector[Int]) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def equals(obj: Any): Boolean = {
    obj match {
      case LocationInDenizen(thatPath) => path == thatPath
      case _ => false
    }
  }

  def before(that: LocationInDenizen): Boolean = {
    this.path.zip(that.path).foreach({ case (thisStep, thatStep) =>
      if (thisStep < thatStep) {
        return true
      }
      if (thisStep > thatStep) {
        return false
      }
    })
    // If we get here, their steps match up... but one might have more steps than the other.
    if (this.path.length < that.path.length) {
      return true
    }
    if (this.path.length > that.path.length) {
      return false
    }
    // They're equal.
    return false
  }
}


sealed trait IDenizenS
case class TopLevelFunctionS(function: FunctionS) extends IDenizenS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelImplS(impl: ImplS) extends IDenizenS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelExportAsS(export: ExportAsS) extends IDenizenS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelImportS(imporrt: ImportS) extends IDenizenS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

object ICitizenDenizenS {
  def unapply(x: IDenizenS): Option[ICitizenS] = {
    x match {
      case TopLevelStructS(s) => Some(s)
      case TopLevelInterfaceS(i) => Some(i)
      case _ => None
    }
  }
}
sealed trait ICitizenDenizenS extends IDenizenS {
  def citizen: ICitizenS
}
case class TopLevelStructS(struct: StructS) extends ICitizenDenizenS {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def citizen: ICitizenS = struct
}
case class TopLevelInterfaceS(interface: InterfaceS) extends ICitizenDenizenS {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def citizen: ICitizenS = interface
}

case class FileS(denizens: Vector[IDenizenS])