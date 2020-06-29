package net.verdagon.vale.scout

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.patterns.{AtomSP, PatternSUtils, VirtualitySP}
import net.verdagon.vale.scout.rules.{IRulexSR, ITypeSR, RuleSUtils, TypedSR}
import net.verdagon.vale.{vassert, vwat}

import scala.collection.immutable.List


//sealed trait MutabilityP
//case object MutableS extends MutabilityP
//case object ImmutableS extends MutabilityP
//
//sealed trait VariabilityP
//case object FinalP extends VariabilityP
//case object VaryingP extends VariabilityP
//
//sealed trait OwnershipS
//case object OwnS extends OwnershipS
//case object BorrowS extends OwnershipS
//case object ShareS extends OwnershipS
//case object RawS extends OwnershipS
//
//sealed trait PermissionS
//case object ReadonlyS extends PermissionS
//case object ReadwriteS extends PermissionS
//case object ExclusiveReadwriteS extends PermissionS
//
//sealed trait LocationS
//case object InlineS extends LocationS
//case object YonderS extends LocationS


trait IExpressionSE

case class ProgramS(
    structs: List[StructS],
    interfaces: List[InterfaceS],
    impls: List[ImplS],
    implementedFunctions: List[FunctionS]) {
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

case class CodeLocationS(line: Int, char: Int)

case class StructS(
    name: TopLevelCitizenDeclarationNameS,
    export: Boolean,
    mutabilityRune: IRuneS,
    // This is needed for recursive structures like
    //   struct ListNode<T> imm rules(T Ref) {
    //     tail ListNode<T>;
    //   }
    maybePredictedMutability: Option[MutabilityP],
    knowableRunes: Set[IRuneS],
    identifyingRunes: List[IRuneS],
    localRunes: Set[IRuneS],
    maybePredictedType: Option[ITypeSR],
    isTemplate: Boolean,
    rules: List[IRulexSR],
    members: List[StructMemberS]) {
  vassert(isTemplate == identifyingRunes.nonEmpty)
}

case class StructMemberS(
    name: String,
    variability: VariabilityP,
    typeRune: IRuneS)

case class ImplS(
    name: ImplNameS,
    rules: List[IRulexSR],
    knowableRunes: Set[IRuneS],
    localRunes: Set[IRuneS],
    isTemplate: Boolean,
    structKindRune: IRuneS,
    interfaceKindRune: IRuneS)

case class InterfaceS(
    name: TopLevelCitizenDeclarationNameS,
    mutabilityRune: IRuneS,
    // This is needed for recursive structures like
    //   struct ListNode<T> imm rules(T Ref) {
    //     tail ListNode<T>;
    //   }
    maybePredictedMutability: Option[MutabilityP],
    knowableRunes: Set[IRuneS],
    identifyingRunes: List[IRuneS],
    localRunes: Set[IRuneS],
    maybePredictedType: Option[ITypeSR],
    isTemplate: Boolean,
    rules: List[IRulexSR],
    // See IMRFDI
    internalMethods: List[FunctionS]) {
  vassert(isTemplate == identifyingRunes.nonEmpty)

  internalMethods.foreach(internalMethod => {
    vassert(!internalMethod.isTemplate)
  })
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

// remember, by doing a "m", CaptureSP("m", Destructure("Marine", List("hp, "item"))), by having that
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
  // The name they supplied, or a generated one. This is actually not used at all by the templar,
  // it's probably only used by IDEs. The templar gets arguments by index.
  def name = pattern.name
}

case class SimpleParameter1(
    origin: Option[AtomSP],
    name: String,
    virtuality: Option[VirtualitySP],
    tyype: ITemplexS)

sealed trait IBody1
case object ExternBody1 extends IBody1
case object AbstractBody1 extends IBody1
case class GeneratedBody1(generatorId: String) extends IBody1
case class CodeBody1(body1: BodySE) extends IBody1

// template params.

// Underlying class for all XYZFunctionS types
case class FunctionS(
    name: IFunctionDeclarationNameS,

    // Runes that we can know without looking at args or template args.
    knowableRunes: Set[IRuneS],
    // This is not necessarily only what the user specified, the compiler can add
    // things to the end here, see CCAUIR.
    identifyingRunes: List[IRuneS],
    // Runes that we need the args or template args to indirectly figure out.
    localRunes: Set[IRuneS],

    maybePredictedType: Option[ITypeSR],

    params: List[ParameterS],

    // We need to leave it an option to signal that the compiler can infer the return type.
    maybeRetCoordRune: Option[IRuneS],

    isTemplate: Boolean,
    templateRules: List[IRulexSR],
    body: IBody1
) {

  // Make sure we have to solve all identifying runes
  vassert((identifyingRunes.toSet -- localRunes).isEmpty)

  vassert(isTemplate == identifyingRunes.nonEmpty)

  body match {
    case ExternBody1 | AbstractBody1 | GeneratedBody1(_) => {
      name match {
        case LambdaNameS(_) => vwat()
        case _ =>
      }
    }
    case CodeBody1(body1) => {
      if (body1.closuredNames.nonEmpty) {
        name match {
          case LambdaNameS(_) =>
          case _ => vwat()
        }
      }
    }
  }

  def isLight(): Boolean = {
    body match {
      case ExternBody1 | AbstractBody1 | GeneratedBody1(_) => false
      case CodeBody1(body1) => body1.closuredNames.nonEmpty
    }
  }

  //  def orderedIdentifyingRunes: List[String] = {
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
//  private def orderedRunes: List[String] = {
//    (
//      maybeUserSpecifiedIdentifyingRunes.getOrElse(List()) ++
//      params.map(_.pattern).flatMap(PatternSUtils.getDistinctOrderedRunesForPattern) ++
//      RuleSUtils.getDistinctOrderedRunesForRulexes(templateRules) ++
//      maybeRetCoordRune.toList
//    ).distinct
//  }
}

case class BFunctionS(
  origin: FunctionS,
  name: String,
  body: BodySE)

