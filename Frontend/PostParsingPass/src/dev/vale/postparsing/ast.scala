package dev.vale.postparsing

import dev.vale.{PackageCoordinate, RangeS, vassert, vcurious, vpass, vwat}
import dev.vale.parsing.ast.{IMacroInclusionP, MutabilityP, VariabilityP}
import dev.vale.postparsing.patterns.{AbstractSP, AtomSP}
import dev.vale.postparsing.rules.{IRulexSR, RuneUsage}
import dev.vale.parsing._
import dev.vale.parsing.ast.VariabilityP
import dev.vale.postparsing.patterns.{AbstractSP, AtomSP}
import dev.vale.postparsing.rules._
import dev.vale.RangeS

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
        .find(f => f.name match { case FunctionNameS(n, _) => n == name })
    vassert(matches.size == 1)
    matches.head
  }
  def lookupInterface(name: String): InterfaceS = {
    val matches =
      interfaces
        .find(f => f.name match { case TopLevelCitizenDeclarationNameS(n, _) => n == name })
    vassert(matches.size == 1)
    matches.head
  }
  def lookupStruct(name: String): StructS = {
    val matches =
      structs
        .find(f => f.name match { case TopLevelCitizenDeclarationNameS(n, _) => n == name })
    vassert(matches.size == 1)
    matches.head
  }
}

sealed trait ICitizenAttributeS
sealed trait IFunctionAttributeS
case class ExternS(packageCoord: PackageCoordinate) extends IFunctionAttributeS with ICitizenAttributeS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case object PureS extends IFunctionAttributeS
case object SealedS extends ICitizenAttributeS
case class BuiltinS(generatorName: String) extends IFunctionAttributeS with ICitizenAttributeS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case class MacroCallS(range: RangeS, include: IMacroInclusionP, macroName: String) extends ICitizenAttributeS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case class ExportS(packageCoordinate: PackageCoordinate) extends IFunctionAttributeS with ICitizenAttributeS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case object UserFunctionS extends IFunctionAttributeS // Whether it was written by a human. Mostly for tests right now.

case class StructS(
    range: RangeS,
    name: TopLevelCitizenDeclarationNameS,
    attributes: Vector[ICitizenAttributeS],
    weakable: Boolean,
    userSpecifiedIdentifyingRunes: Vector[RuneUsage],
    runeToExplicitType: Map[IRuneS, ITemplataType],
    mutabilityRune: RuneUsage,

    // This is needed for recursive structures like
    //   struct ListNode<T> imm where T Ref {
    //     tail ListNode<T>;
    //   }
    maybePredictedMutability: Option[MutabilityP],
    predictedRuneToType: Map[IRuneS, ITemplataType],
    maybePredictedType: Option[ITemplataType],

    rules: Array[IRulexSR],
    members: Vector[IStructMemberS]) {
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
    name: String,
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
  name: TopLevelCitizenDeclarationNameS,
  attributes: Vector[ICitizenAttributeS],
  weakable: Boolean,
  identifyingRunes: Vector[RuneUsage],
  runeToExplicitType: Map[IRuneS, ITemplataType],
  mutabilityRune: RuneUsage,

  // This is needed for recursive structures like
  //   struct ListNode<T> imm where T Ref {
  //     tail ListNode<T>;
  //   }
  maybePredictedMutability: Option[MutabilityP],
  predictedRuneToType: Map[IRuneS, ITemplataType],
  maybePredictedType: Option[ITemplataType],

  rules: Array[IRulexSR],
  // See IMRFDI
  internalMethods: Vector[FunctionS]) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

case class ImplS(
    range: RangeS,
    // The name of an impl is the human name of the subcitizen, see INSHN.
    name: ImplDeclarationNameS,
    userSpecifiedIdentifyingRunes: Vector[RuneUsage],
    rules: Array[IRulexSR],
  runeToExplicitType: Map[IRuneS, ITemplataType],
    structKindRune: RuneUsage,
    interfaceKindRune: RuneUsage) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

case class ExportAsS(
    range: RangeS,
    rules: Array[IRulexSR],
    exportName: ExportAsNameS,
    rune: RuneUsage,
    exportedName: String) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

case class ImportS(
  range: RangeS,
  moduleName: String,
  packageNames: Vector[String],
  importeeName: String) {
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
    // Note the lack of a VariabilityP here. The only way to get a variability is with a Capture.
    pattern: AtomSP) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  vassert(pattern.coordRune.nonEmpty)
}

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
case class GeneratedBodyS(generatorId: String) extends IBodyS {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}
case class CodeBodyS(body: BodySE) extends IBodyS {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

// template params.

// Underlying class for all XYZFunctionS types
case class FunctionS(
    range: RangeS,
    name: IFunctionDeclarationNameS,
    attributes: Vector[IFunctionAttributeS],

    identifyingRunes: Vector[RuneUsage],
    runeToPredictedType: Map[IRuneS, ITemplataType],

    params: Vector[ParameterS],

    // We need to leave it an option to signal that the compiler can infer the return type.
    maybeRetCoordRune: Option[RuneUsage],

    rules: Array[IRulexSR],
    body: IBodyS
) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()

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
}


sealed trait IDenizenS
case class TopLevelFunctionS(function: FunctionS) extends IDenizenS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelStructS(struct: StructS) extends IDenizenS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelInterfaceS(interface: InterfaceS) extends IDenizenS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelImplS(impl: ImplS) extends IDenizenS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelExportAsS(export: ExportAsS) extends IDenizenS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelImportS(imporrt: ImportS) extends IDenizenS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class FileS(denizens: Vector[IDenizenS])