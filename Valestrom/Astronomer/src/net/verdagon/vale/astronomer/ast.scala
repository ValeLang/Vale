package net.verdagon.vale.astronomer

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{IEnvironment => _, FunctionEnvironment => _, Environment => _, _}
import net.verdagon.vale.{vassert, vwat}

import scala.collection.immutable.List

trait IExpressionAE

case class ProgramA(
    structs: List[StructA],
    interfaces: List[InterfaceA],
    impls: List[ImplA],
    functions: List[FunctionA]) {
  def lookupFunction(name: INameA) = {
    val matches = functions.filter(_.name == name)
    vassert(matches.size == 1)
    matches.head
  }
  def lookupInterface(name: INameA) = {
    val matches = interfaces.find(_.name == name)
    vassert(matches.size == 1)
    matches.head match {
      case i @ InterfaceA(_, _, _, _, _, _, _, _, _, _, _, _) => i
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
    export: Boolean,
    weakable: Boolean,
    mutabilityRune: IRuneA,

    // This is needed for recursive structures like
    //   struct ListNode<T> imm rules(T Ref) {
    //     tail ListNode<T>;
    //   }
    maybePredictedMutability: Option[MutabilityP],
    tyype: ITemplataType,
    knowableRunes: Set[IRuneA],
    identifyingRunes: List[IRuneA],
    localRunes: Set[IRuneA],
    typeByRune: Map[IRuneA, ITemplataType],
    rules: List[IRulexAR],
    members: List[StructMemberA]
) extends TypeDefinitionA {
  vassert((knowableRunes -- typeByRune.keySet).isEmpty)
  vassert((localRunes -- typeByRune.keySet).isEmpty)

  def isTemplate: Boolean = tyype match {
    case KindTemplataType => false
    case TemplateTemplataType(_, _) => true
    case _ => vwat()
  }
}

case class StructMemberA(
    name: String,
    variability: VariabilityP,
    typeRune: IRuneA)

case class ImplA(
    name: ImplNameA,
    rules: List[IRulexAR],
    typeByRune: Map[IRuneA, ITemplataType],
    localRunes: Set[IRuneA],
    structKindRune: IRuneA,
    interfaceKindRune: IRuneA)

//case class AliasA(
//  codeLocation: CodeLocation,
//  rules: List[IRulexAR],
//  typeByRune: Map[String, ITemplataType],
//  aliasRune: String,
//  aliaseeRune: String)

case class InterfaceA(
    range: RangeS,
    name: TopLevelCitizenDeclarationNameA,
    weakable: Boolean,
    mutabilityRune: IRuneA,
    // This is needed for recursive structures like
    //   struct ListNode<T> imm rules(T Ref) {
    //     tail ListNode<T>;
    //   }
    maybePredictedMutability: Option[MutabilityP],
    tyype: ITemplataType,
    knowableRunes: Set[IRuneA],
    identifyingRunes: List[IRuneA],
    localRunes: Set[IRuneA],
    typeByRune: Map[IRuneA, ITemplataType],
    rules: List[IRulexAR],
    // See IMRFDI
    internalMethods: List[FunctionA]) {
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

// remember, by doing a "m", CaptureSP("m", Destructure("Marine", List("hp, "item"))), by having that
// CaptureSP/"m" there, we're changing the nature of that Destructure; "hp" and "item" will be
// borrows rather than owns.

// So, when the scout is assigning everything a name, it's actually forcing us to always have
// borrowing destructures.

// We should change Scout to not assign names... or perhaps, it can assign names for the parameters,
// but secretly, templar will consider arguments to have actual names of __arg_0, __arg_1, and let
// the PatternTemplar introduce the actual names.

// Also remember, if a parameter has no name, it can't be varying.

// template params.

// Underlying class for all XYZFunctionS types
case class FunctionA(
    range: RangeS,
    name: IFunctionDeclarationNameA,
    isUserFunction: Boolean,

    tyype: ITemplataType,
    knowableRunes: Set[IRuneA],
    // This is not necessarily only what the user specified, the compiler can add
    // things to the end here, see CCAUIR.
    identifyingRunes: List[IRuneA],
    localRunes: Set[IRuneA],

    typeByRune: Map[IRuneA, ITemplataType],

    params: List[ParameterA],

    // We need to leave it an option to signal that the compiler can infer the return type.
    maybeRetCoordRune: Option[IRuneA],

    templateRules: List[IRulexAR],
    body: IBodyA
) {
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

  // The name they supplied, or a generated one. This is actually not used at all by the templar,
  // it's probably only used by IDEs. The templar gets arguments by index.
  def name = pattern.name
}

case class CaptureA(
  name: IVarNameA,
  variability: VariabilityP)

sealed trait IBodyA
case object ExternBodyA extends IBodyA
case object AbstractBodyA extends IBodyA
case class GeneratedBodyA(generatorId: String) extends IBodyA
case class CodeBodyA(bodyA: BodyAE) extends IBodyA

case class BFunctionA(
  origin: FunctionA,
  body: BodyAE)
