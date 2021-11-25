package net.verdagon.vale.astronomer

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.rules.{IRulexSR, RuneUsage}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.{PackageCoordinate, RangeS, vassert, vcurious, vimpl, vpass, vwat}

import scala.collection.immutable.List

//trait IExpressionSE {
//  def range: RangeS
//}

case class ProgramA(
    structs: Vector[StructA],
    interfaces: Vector[InterfaceA],
    impls: Vector[ImplA],
    functions: Vector[FunctionA],
    exports: Vector[ExportAsA]) {
  override def hashCode(): Int = vcurious()

  def lookupFunction(name: INameS) = {
    val matches = functions.filter(_.name == name)
    vassert(matches.size == 1)
    matches.head
  }
  def lookupFunction(name: String) = {
    val matches = functions.filter(function => {
      function.name match {
        case FunctionNameS(n, _) => n == name
        case _ => false
      }
    })
    vassert(matches.size == 1)
    matches.head
  }
  def lookupInterface(name: INameS) = {
    val matches = interfaces.find(_.name == name)
    vassert(matches.size == 1)
    matches.head match {
      case i @ InterfaceA(_, _, _, _, _, _, _, _, _, _, _) => i
    }
  }
  def lookupStruct(name: INameS) = {
    val matches = structs.find(_.name == name)
    vassert(matches.size == 1)
    matches.head match {
      case i @ StructA(_, _, _, _, _, _, _, _, _, _, _) => i
    }
  }
  def lookupStruct(name: String) = {
    val matches = structs.filter(struct => {
      struct.name match {
        case TopLevelCitizenDeclarationNameS(n, _) => n == name
        case _ => false
      }
    })
    vassert(matches.size == 1)
    matches.head
  }
}


trait TypeDefinitionA {
  def name: INameS;
}

case class StructA(
    range: RangeS,
    name: ICitizenDeclarationNameS,
    attributes: Vector[ICitizenAttributeS],
    weakable: Boolean,
    mutabilityRune: RuneUsage,

    // This is needed for recursive structures like
    //   struct ListNode<T> imm rules(T Ref) {
    //     tail ListNode<T>;
    //   }
    maybePredictedMutability: Option[MutabilityP],
    tyype: ITemplataType,
//    knowableRunes: Set[IRuneS],
    identifyingRunes: Vector[RuneUsage],
//    localRunes: Set[IRuneS],
    runeToType: Map[IRuneS, ITemplataType],
    rules: Vector[IRulexSR],
    members: Vector[IStructMemberS]
) extends TypeDefinitionA {
  val hash = range.hashCode() + name.hashCode()
  override def hashCode(): Int = hash;

  vpass()

  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[StructA]) { return false }
    val that = obj.asInstanceOf[StructA]
    return range == that.range && name == that.name;
  }

//  vassert((knowableRunes -- runeToType.keySet).isEmpty)
//  vassert((localRunes -- runeToType.keySet).isEmpty)

  def isTemplate: Boolean = tyype match {
    case KindTemplataType => false
    case TemplateTemplataType(_, _) => true
    case _ => vwat()
  }
}

//case class StructMemberA(
//    range: RangeS,
//    name: String,
//    variability: VariabilityP,
//    typeRune: RuneUsage) {
//  override def hashCode(): Int = vcurious()
//}

case class ImplA(
  range: RangeS,
  name: ImplDeclarationNameS,
  impreciseName: ImplImpreciseNameS, // The name of an impl is the human name of the subcitizen, see INSHN.
  identifyingRunes: Vector[RuneUsage],
  rules: Vector[IRulexSR],
  runeToType: Map[IRuneS, ITemplataType],
  structKindRune: RuneUsage,
  interfaceKindRune: RuneUsage) {

  val hash = range.hashCode() + name.hashCode()
  override def hashCode(): Int = hash;
  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[ImplA]) { return false }
    val that = obj.asInstanceOf[ImplA]
    return range == that.range && name == that.name;
  }

  def isTemplate: Boolean = identifyingRunes.nonEmpty
}

case class ExportAsA(
    range: RangeS,
    exportedName: String,
  rules: Vector[IRulexSR],
    runeToType: Map[IRuneS, ITemplataType],
    typeRune: RuneUsage) {
  val hash = range.hashCode() + exportedName.hashCode
  override def hashCode(): Int = hash;
  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[ImplA]) { return false }
    val that = obj.asInstanceOf[ExportAsA]
    return range == that.range && exportedName == that.exportedName;
  }
}

//case class AliasA(
//  codeLocation: CodeLocation,
//  rules: RuneWorldSolverState,
//  runeToType: Map[String, ITemplataType],
//  aliasRune: String,
//  aliaseeRune: String) { override def hashCode(): Int = vcurious() }


case class InterfaceA(
    range: RangeS,
    name: TopLevelCitizenDeclarationNameS,
    attributes: Vector[ICitizenAttributeS],
    weakable: Boolean,
    mutabilityRune: RuneUsage,
    // This is needed for recursive structures like
    //   struct ListNode<T> imm rules(T Ref) {
    //     tail ListNode<T>;
    //   }
    maybePredictedMutability: Option[MutabilityP],
    tyype: ITemplataType,
//    knowableRunes: Set[IRuneS],
    identifyingRunes: Vector[RuneUsage],
//    localRunes: Set[IRuneS],
    runeToType: Map[IRuneS, ITemplataType],
  rules: Vector[IRulexSR],
    // See IMRFDI
    internalMethods: Vector[FunctionA]) {
  val hash = range.hashCode() + name.hashCode()
  override def hashCode(): Int = hash;
  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[InterfaceA]) { return false }
    val that = obj.asInstanceOf[InterfaceA]
    return range == that.range && name == that.name;
  }

//  vassert((knowableRunes -- runeToType.keySet).isEmpty)
//  vassert((localRunes -- runeToType.keySet).isEmpty)

  internalMethods.foreach(internalMethod => {
    vassert(identifyingRunes == internalMethod.identifyingRunes)
    vassert(isTemplate == internalMethod.isTemplate);
  })

  def isTemplate: Boolean = tyype match {
    case KindTemplataType => false
    case TemplateTemplataType(_, _) => true
    case _ => vwat()
  }
}

object interfaceName {
  // The extraction method (mandatory)
  def unapply(interfaceA: InterfaceA): Option[INameS] = {
    Some(interfaceA.name)
  }
}

object structName {
  // The extraction method (mandatory)
  def unapply(structA: StructA): Option[INameS] = {
    Some(structA.name)
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


//sealed trait ICitizenAttributeA
//sealed trait IFunctionAttributeA
//case class ExternA(packageCoord: PackageCoordinate) extends IFunctionAttributeA with ICitizenAttributeA { override def hashCode(): Int = vcurious() }
//case class ExportA(packageCoord: PackageCoordinate) extends IFunctionAttributeA with ICitizenAttributeA { override def hashCode(): Int = vcurious() }
//case object PureA extends IFunctionAttributeA with ICitizenAttributeA
//case object UserFunctionA extends IFunctionAttributeA // Whether it was written by a human. Mostly for tests right now.


// Underlying class for all XYZFunctionS types
case class FunctionA(
    range: RangeS,
    name: IFunctionDeclarationNameS,

    // One day we might put a List of import statements here. After all, imports apply to
    // everything in the file.

    attributes: Vector[IFunctionAttributeS],

    tyype: ITemplataType,
    // This is not necessarily only what the user specified, the compiler can add
    // things to the end here, see CCAUIR.
    identifyingRunes: Vector[RuneUsage],

    runeToType: Map[IRuneS, ITemplataType],

    params: Vector[ParameterS],

    // We need to leave it an option to signal that the compiler can infer the return type.
    maybeRetCoordRune: Option[RuneUsage],

    rules: Vector[IRulexSR],
    body: IBodyS
) {
  val hash = range.hashCode() + name.hashCode()
  vpass()

  vassert(range.begin.file.packageCoordinate == name.packageCoordinate)

  override def hashCode(): Int = hash;
  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[FunctionA]) { return false }
    val that = obj.asInstanceOf[FunctionA]
    return range == that.range && name == that.name;
  }

  rules.foreach(rule => rule.runeUsages.foreach(rune => vassert(runeToType.contains(rune.rune))))
  params.flatMap(_.pattern.coordRune).foreach(runeUsage => {
    vassert(runeToType.contains(runeUsage.rune))
  })

//  // Make sure we have to solve all the identifying runes.
//  vassert((identifyingRunes.toSet -- localRunes).isEmpty)
//
//  vassert((knowableRunes -- runeToType.keySet).isEmpty)
//  vassert((localRunes -- runeToType.keySet).isEmpty)

  def isLight(): Boolean = {
    body match {
      case ExternBodyS | AbstractBodyS | GeneratedBodyS(_) => true
      case CodeBodyS(bodyA) => bodyA.closuredNames.isEmpty
    }
  }

  def isTemplate: Boolean = tyype match {
    case FunctionTemplataType => false
    case TemplateTemplataType(_, _) => true
    case _ => vwat()
  }
}


//case class ParameterA(
//    // Note the lack of a VariabilityP here. The only way to get a variability is with a Capture.
//    pattern: AtomSP) {
//  override def hashCode(): Int = vcurious()
//}

//case class CaptureA(local: LocalA) { override def hashCode(): Int = vcurious() }

//sealed trait IBodyS
//case object ExternBodyS extends IBodyS
//case object AbstractBodyS extends IBodyS
//case class GeneratedBodyS(generatorId: String) extends IBodyS { override def hashCode(): Int = vcurious() }
//case class CodeBodyS(bodyA: BodySE) extends IBodyS { override def hashCode(): Int = vcurious() }
//
//case class BFunctionA(
//  origin: FunctionA,
//  body: BodySE) { override def hashCode(): Int = vcurious() }
