package net.verdagon.vale.astronomer

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.{PackageCoordinate, vassert, vcurious, vimpl, vwat}

import scala.collection.immutable.List

trait IExpressionAE {
  def range: RangeS
}

case class ProgramA(
    structs: Vector[StructA],
    interfaces: Vector[InterfaceA],
    impls: Vector[ImplA],
    functions: Vector[FunctionA],
    exports: Vector[ExportAsA]) {
  override def hashCode(): Int = vcurious()

  def lookupFunction(name: INameA) = {
    val matches = functions.filter(_.name == name)
    vassert(matches.size == 1)
    matches.head
  }
  def lookupInterface(name: INameA) = {
    val matches = interfaces.find(_.name == name)
    vassert(matches.size == 1)
    matches.head match {
      case i @ InterfaceA(_, _, _, _, _, _, _, _, _, _, _, _, _) => i
    }
  }
  def lookupStruct(name: INameA) = {
    val matches = structs.find(_.name == name)
    vassert(matches.size == 1)
    matches.head match {
      case i @ StructA(_, _, _, _, _, _, _, _, _, _, _, _, _) => i
    }
  }
}


trait TypeDefinitionA {
  def name: INameA;
}

case class StructA(
    range: RangeS,
    name: TopLevelCitizenDeclarationNameA,
    attributes: Vector[ICitizenAttributeA],
    weakable: Boolean,
    mutabilityRune: IRuneA,

    // This is needed for recursive structures like
    //   struct ListNode<T> imm rules(T Ref) {
    //     tail ListNode<T>;
    //   }
    maybePredictedMutability: Option[MutabilityP],
    tyype: ITemplataType,
    knowableRunes: Set[IRuneA],
    identifyingRunes: Vector[IRuneA],
    localRunes: Set[IRuneA],
    typeByRune: Map[IRuneA, ITemplataType],
    rules: Vector[IRulexAR],
    members: Vector[StructMemberA]
) extends TypeDefinitionA {
  val hash = range.hashCode() + name.hashCode()
  override def hashCode(): Int = hash;
  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[StructA]) { return false }
    val that = obj.asInstanceOf[StructA]
    return range == that.range && name == that.name;
  }

  vassert((knowableRunes -- typeByRune.keySet).isEmpty)
  vassert((localRunes -- typeByRune.keySet).isEmpty)

  def isTemplate: Boolean = tyype match {
    case KindTemplataType => false
    case TemplateTemplataType(_, _) => true
    case _ => vwat()
  }
}

case class StructMemberA(
    range: RangeS,
    name: String,
    variability: VariabilityP,
    typeRune: IRuneA) {
  override def hashCode(): Int = vcurious()
}

case class ImplA(
    range: RangeS,
    // The name of an impl is the human name of the subcitizen, see INSHN.
    name: ImplNameA,
    rulesFromStructDirection: Vector[IRulexAR],
    rulesFromInterfaceDirection: Vector[IRulexAR],
    typeByRune: Map[IRuneA, ITemplataType],
    localRunes: Set[IRuneA],
    structKindRune: IRuneA,
    interfaceKindRune: IRuneA) {
  val hash = range.hashCode() + name.hashCode()
  override def hashCode(): Int = hash;
  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[ImplA]) { return false }
    val that = obj.asInstanceOf[ImplA]
    return range == that.range && name == that.name;
  }
}

case class ExportAsA(
    range: RangeS,
    exportedName: String,
    rules: Vector[IRulexAR],
    typeByRune: Map[IRuneA, ITemplataType],
    typeRune: IRuneA) {
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
//  rules: Vector[IRulexAR],
//  typeByRune: Map[String, ITemplataType],
//  aliasRune: String,
//  aliaseeRune: String) { override def hashCode(): Int = vcurious() }


case class InterfaceA(
    range: RangeS,
    name: TopLevelCitizenDeclarationNameA,
    attributes: Vector[ICitizenAttributeA],
    weakable: Boolean,
    mutabilityRune: IRuneA,
    // This is needed for recursive structures like
    //   struct ListNode<T> imm rules(T Ref) {
    //     tail ListNode<T>;
    //   }
    maybePredictedMutability: Option[MutabilityP],
    tyype: ITemplataType,
    knowableRunes: Set[IRuneA],
    identifyingRunes: Vector[IRuneA],
    localRunes: Set[IRuneA],
    typeByRune: Map[IRuneA, ITemplataType],
    rules: Vector[IRulexAR],
    // See IMRFDI
    internalMethods: Vector[FunctionA]) {
  val hash = range.hashCode() + name.hashCode()
  override def hashCode(): Int = hash;
  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[InterfaceA]) { return false }
    val that = obj.asInstanceOf[InterfaceA]
    return range == that.range && name == that.name;
  }

  vassert((knowableRunes -- typeByRune.keySet).isEmpty)
  vassert((localRunes -- typeByRune.keySet).isEmpty)

  internalMethods.foreach(internalMethod => {
    vassert(!internalMethod.isTemplate);
  })

  def isTemplate: Boolean = tyype match {
    case KindTemplataType => false
    case TemplateTemplataType(_, _) => true
    case _ => vwat()
  }
}

object interfaceName {
  // The extraction method (mandatory)
  def unapply(interfaceA: InterfaceA): Option[INameA] = {
    Some(interfaceA.name)
  }
}

object structName {
  // The extraction method (mandatory)
  def unapply(structA: StructA): Option[INameA] = {
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


sealed trait ICitizenAttributeA
sealed trait IFunctionAttributeA
case class ExternA(packageCoord: PackageCoordinate) extends IFunctionAttributeA with ICitizenAttributeA { override def hashCode(): Int = vcurious() }
case class ExportA(packageCoord: PackageCoordinate) extends IFunctionAttributeA with ICitizenAttributeA { override def hashCode(): Int = vcurious() }
case object PureA extends IFunctionAttributeA with ICitizenAttributeA
case object UserFunctionA extends IFunctionAttributeA // Whether it was written by a human. Mostly for tests right now.


// Underlying class for all XYZFunctionS types
case class FunctionA(
    range: RangeS,
    name: IFunctionDeclarationNameA,
    attributes: Vector[IFunctionAttributeA],

    tyype: ITemplataType,
    knowableRunes: Set[IRuneA],
    // This is not necessarily only what the user specified, the compiler can add
    // things to the end here, see CCAUIR.
    identifyingRunes: Vector[IRuneA],
    localRunes: Set[IRuneA],

    typeByRune: Map[IRuneA, ITemplataType],

    params: Vector[ParameterA],

    // We need to leave it an option to signal that the compiler can infer the return type.
    maybeRetCoordRune: Option[IRuneA],

    templateRules: Vector[IRulexAR],
    body: IBodyA
) {
  val hash = range.hashCode() + name.hashCode()
  override def hashCode(): Int = hash;
  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[FunctionA]) { return false }
    val that = obj.asInstanceOf[FunctionA]
    return range == that.range && name == that.name;
  }

  // Make sure we have to solve all the identifying runes.
  vassert((identifyingRunes.toSet -- localRunes).isEmpty)

  vassert((knowableRunes -- typeByRune.keySet).isEmpty)
  vassert((localRunes -- typeByRune.keySet).isEmpty)

  def isLight(): Boolean = {
    body match {
      case ExternBodyA | AbstractBodyA | GeneratedBodyA(_) => true
      case CodeBodyA(bodyA) => bodyA.closuredNames.isEmpty
    }
  }

  def isTemplate: Boolean = tyype match {
    case FunctionTemplataType => false
    case TemplateTemplataType(_, _) => true
    case _ => vwat()
  }
}


case class ParameterA(
    // Note the lack of a VariabilityP here. The only way to get a variability is with a Capture.
    pattern: AtomAP) {
  override def hashCode(): Int = vcurious()
}

case class CaptureA(local: LocalA) { override def hashCode(): Int = vcurious() }

sealed trait IBodyA
case object ExternBodyA extends IBodyA
case object AbstractBodyA extends IBodyA
case class GeneratedBodyA(generatorId: String) extends IBodyA { override def hashCode(): Int = vcurious() }
case class CodeBodyA(bodyA: BodyAE) extends IBodyA { override def hashCode(): Int = vcurious() }

case class BFunctionA(
  origin: FunctionA,
  body: BodyAE) { override def hashCode(): Int = vcurious() }
