package net.verdagon.vale.scout

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.patterns.{AtomSP, VirtualitySP}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.{FileCoordinate, PackageCoordinate, RangeS, vassert, vcurious, vimpl, vpass, vwat}

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
  override def hashCode(): Int = vcurious()

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
case class MacroCallS(range: RangeS, include: IMacroInclusion, macroName: String) extends ICitizenAttributeS {
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
    //   struct ListNode<T> imm rules(T Ref) {
    //     tail ListNode<T>;
    //   }
    maybePredictedMutability: Option[MutabilityP],
    predictedRuneToType: Map[IRuneS, ITemplataType],
    maybePredictedType: Option[ITemplataType],

//    knowableRunes: Set[IRuneS],
//    identifyingRunes: Vector[IRuneS],
//    localRunes: Set[IRuneS],
//    maybePredictedType: Option[ITypeSR],
//    isTemplate: Boolean,
    rules: Array[IRulexSR],
//    runeSToCanonicalRune: collection.Map[IRuneS, Int],
    members: Vector[IStructMemberS]) {
  override def hashCode(): Int = vcurious()

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
  override def hashCode(): Int = vcurious()
}
case class VariadicStructMemberS(
  range: RangeS,
  variability: VariabilityP,
  typeRune: RuneUsage) extends IStructMemberS {
  override def hashCode(): Int = vcurious()
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
  //   struct ListNode<T> imm rules(T Ref) {
  //     tail ListNode<T>;
  //   }
  maybePredictedMutability: Option[MutabilityP],
  predictedRuneToType: Map[IRuneS, ITemplataType],
  maybePredictedType: Option[ITemplataType],

//  knowableRunes: Set[IRuneS],
//  identifyingRunes: Vector[IRuneS],
//  localRunes: Set[IRuneS],
//  isTemplate: Boolean,
  rules: Array[IRulexSR],
//  runeSToCanonicalRune: collection.Map[IRuneS, Int],
  // See IMRFDI
  internalMethods: Vector[FunctionS]) {
  override def hashCode(): Int = vcurious()
//  vassert(isTemplate == identifyingRunes.nonEmpty)

//  internalMethods.foreach(internalMethod => {
//    vassert(!internalMethod.isTemplate)
//  })
}

case class ImplS(
    range: RangeS,
    // The name of an impl is the human name of the subcitizen, see INSHN.
    name: ImplDeclarationNameS,
    userSpecifiedIdentifyingRunes: Vector[RuneUsage],
    rules: Array[IRulexSR],
//    runeSToCanonicalRune: collection.Map[IRuneS, Int],
//    knowableRunes: Set[Int],
//    localRunes: Set[Int],
//    isTemplate: Boolean,
  runeToExplicitType: Map[IRuneS, ITemplataType],
    structKindRune: RuneUsage,
    interfaceKindRune: RuneUsage) {
  override def hashCode(): Int = vcurious()
}

case class ExportAsS(
    range: RangeS,
    rules: Array[IRulexSR],
    exportName: ExportAsNameS,
    rune: RuneUsage,
    exportedName: String) {
  override def hashCode(): Int = vcurious()
}

case class ImportS(
  range: RangeS,
  moduleName: String,
  packageNames: Vector[String],
  importeeName: String) {
  override def hashCode(): Int = vcurious()
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
// but secretly, templar will consider arguments to have actual names of __arg_0, __arg_1, and let
// the PatternTemplar introduce the actual names.

// Also remember, if a parameter has no name, it can't be varying.

case class ParameterS(
    // Note the lack of a VariabilityP here. The only way to get a variability is with a Capture.
    pattern: AtomSP) {
  override def hashCode(): Int = vcurious()

  vassert(pattern.coordRune.nonEmpty)
}

case class SimpleParameterS(
    origin: Option[AtomSP],
    name: String,
    virtuality: Option[VirtualitySP],
    tyype: IRulexSR) {
  override def hashCode(): Int = vcurious()
}

sealed trait IBodyS
case object ExternBodyS extends IBodyS
case object AbstractBodyS extends IBodyS
case class GeneratedBodyS(generatorId: String) extends IBodyS {
  override def hashCode(): Int = vcurious()
}
case class CodeBodyS(body: BodySE) extends IBodyS {
  override def hashCode(): Int = vcurious()
}

// template params.

// Underlying class for all XYZFunctionS types
case class FunctionS(
    range: RangeS,
    name: IFunctionDeclarationNameS,
    attributes: Vector[IFunctionAttributeS],

    identifyingRunes: Vector[RuneUsage],
    runeToPredictedType: Map[IRuneS, ITemplataType],

//    // Runes that we can know without looking at args or template args.
//    knowableRunes: Set[IRuneS],
//    // This is not necessarily only what the user specified, the compiler can add
//    // things to the end here, see CCAUIR.
//    identifyingRunes: Vector[IRuneS],
//    // Runes that we need the args or template args to indirectly figure out.
////    localRunes: Set[IRuneS],
//
//    maybePredictedType: Option[ITypeSR],

    params: Vector[ParameterS],

    // We need to leave it an option to signal that the compiler can infer the return type.
    maybeRetCoordRune: Option[RuneUsage],

//    isTemplate: Boolean,
    rules: Array[IRulexSR],
    body: IBodyS
) {
  override def hashCode(): Int = vcurious()
  vpass()

  // Make sure we have to solve all identifying runes
//  vassert((identifyingRunes.toSet -- localRunes).isEmpty)

//  vassert(isTemplate == identifyingRunes.nonEmpty)

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

  //  def orderedIdentifyingRunes: Vector[String] = {
//    maybeUserSpecifiedIdentifyingRunes match {
//      case Some(userSpecifiedIdentifyingRunes) => userSpecifiedIdentifyingRunes
//      case None => {
//        // Grab the ones from the patterns.
//        // We don't use the ones from the return type because we won't identify a function
//        // from its return type, see CIFFRT.
//        params.map(_.pattern).flatMap(PatternSUtils.getDistinctOrderedRunesForPattern)
//      }
//    }
//  }

//  // This should start with the original runes from the FunctionP in the same order,
//  // See SSRR.
//  private def orderedRunes: Vector[String] = {
//    (
//      maybeUserSpecifiedIdentifyingRunes.getOrElse(Vector.empty) ++
//      params.map(_.pattern).flatMap(PatternSUtils.getDistinctOrderedRunesForPattern) ++
//      RuleSUtils.getDistinctOrderedRunesForRulexes(templateRules) ++
//      maybeRetCoordRune.toVector
//    ).distinct
//  }
}

// A Denizen is a thing at the top level of a file, like structs, functions, impls, exports, etc.
// This is a class with a consumed boolean so that we're sure we don't use it twice.
// Anyone that uses it should call the consume() method.
// Move semantics would be nice here... alas.
class LocationInDenizenBuilder(path: Vector[Int]) {
  private var consumed: Boolean = false
  private var nextChild: Int = 1

  // Note how this is hashing `path`, not `this` like usual.
  val hash = runtime.ScalaRunTime._hashCode(path.toList); override def hashCode(): Int = hash;

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
}
