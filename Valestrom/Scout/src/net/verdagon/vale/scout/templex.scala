package net.verdagon.vale.scout

import net.verdagon.vale.parser._
import net.verdagon.vale.{vassert, vcurious, vwat}

import scala.collection.immutable.List

//// An absolute name is one where we know *exactly* where it's defined; if parser and scout
//// put their brains together they could know exactly where the thing is.
//case class AbsoluteNameS[+T <: INameS](file: String, initSteps: List[INameS], last: T) {// extends IImpreciseNameS[T] {
//  def addStep[Y <: INameS](newLast: Y): Y = Y(file, initSteps :+ last, newLast)
//  def steps: List[INameS] = initSteps :+ last
//  def init: INameS = INameS(file, initSteps.init, initSteps.last)
//}
//// An imprecise name is one where we don't know exactly where the thing is defined.
//// For example, in
////   fn main() {
////     doStuff("hello");
////   }
//// we don't know exactly where doStuff was defined, that depends on what overload the
//// typing stage decides.
//case class ImpreciseNameS[+T <: IImpreciseNameStepS](init: List[IImpreciseNameStepS], last: T) {//extends IImpreciseNameS[T] {
//  def addStep[Y <: IImpreciseNameStepS](newLast: Y): ImpreciseNameS[Y] = ImpreciseNameS[Y](init :+ last, newLast)
//  def steps: List[IImpreciseNameStepS] = init :+ last
//}

// We namespace runes with a full name so we don't have to worry about collisions
// between, for example, two ImplicitRune(0)s.

// We have this INameS stuff so we don't have to have prefixes and names like
// __magic_0 __magic_1 __Closure etc.

sealed trait INameS
sealed trait IVarNameS extends INameS
sealed trait IFunctionDeclarationNameS extends INameS
case class LambdaNameS(
//  parentName: INameS,
  codeLocation: CodeLocationS
) extends IFunctionDeclarationNameS
case class FunctionNameS(name: String, codeLocation: CodeLocationS) extends IFunctionDeclarationNameS
case class TopLevelCitizenDeclarationNameS(name: String, codeLocation: CodeLocationS) extends INameS
case class LambdaStructNameS(lambdaName: LambdaNameS) extends INameS
case class ImplNameS(codeLocation: CodeLocationS) extends INameS
case class LetNameS(codeLocation: CodeLocationS) extends INameS
case class UnnamedLocalNameS(codeLocation: CodeLocationS) extends IVarNameS
case class ClosureParamNameS() extends IVarNameS
case class MagicParamNameS(codeLocation: CodeLocationS) extends IVarNameS
case class CodeVarNameS(name: String) extends IVarNameS
case class ConstructingMemberNameS(name: String) extends IVarNameS
// We differentiate rune names from regular names, we scout out what's actually
// a rune so we can inform the templar. The templar wants to know so it can know
// how to handle this thing; if it's a name, we expect it to exist in the
// environment already, but if it's a rune we can assign something into it.
// Also, we might refer to a rune that was defined in our container's container's
// container, so asking "is this thing here a rune" involves looking at all our
// containers. That's much easier for the scout, so thats a nice bonus.
// We have all these subclasses instead of a string so we don't have to have
// prefixes and names like __implicit_0, __paramRune_0, etc.
sealed trait IRuneS
case class CodeRuneS(name: String) extends IRuneS
case class ImplicitRuneS(containerName: INameS, name: Int) extends IRuneS
case class LetImplicitRuneS(codeLocationS: CodeLocationS, name: Int) extends IRuneS
case class MagicParamRuneS(codeLocationS: CodeLocationS) extends IRuneS
case class MemberRuneS(memberIndex: Int) extends IRuneS
case class ReturnRuneS() extends IRuneS
// These are only made by the templar
case class ExplicitTemplateArgRuneS(index: Int) extends IRuneS

sealed trait IImpreciseNameStepS
case class CodeTypeNameS(name: String) extends IImpreciseNameStepS
// When we're calling a function, we're addressing an overload set, not a specific function.
// If we want a specific function, we use TopLevelDeclarationNameS.
case class GlobalFunctionFamilyNameS(name: String) extends IImpreciseNameStepS {
}
case class ImpreciseCodeVarNameS(name: String) extends IImpreciseNameStepS


// See PVSBUFI
sealed trait ITemplexS
case class IntST(value: Int) extends ITemplexS
case class StringST(value: String) extends ITemplexS
case class MutabilityST(mutability: MutabilityP) extends ITemplexS
case class PermissionST(permission: PermissionP) extends ITemplexS
case class LocationST(location: LocationP) extends ITemplexS
case class OwnershipST(ownership: OwnershipP) extends ITemplexS
case class VariabilityST(variability: VariabilityP) extends ITemplexS
case class BoolST(value: Boolean) extends ITemplexS
case class AbsoluteNameST(name: INameS) extends ITemplexS
case class NameST(name: CodeTypeNameS) extends ITemplexS
case class RuneST(rune: IRuneS) extends ITemplexS
case class OwnershippedST(ownership: OwnershipP, inner: ITemplexS) extends ITemplexS
case class NullableST(inner: ITemplexS) extends ITemplexS
case class CallST(
    template: ITemplexS,
    args: List[ITemplexS]) extends ITemplexS {
}
//case class FunctionST(
//  mutability: Option[ITemplexS],
//  parameters: List[Option[ITemplexS]],
//  returnType: Option[ITemplexS]
//) extends ITemplexS
case class PrototypeST(
  name: String,
  parameters: List[ITemplexS],
  returnType: ITemplexS
) extends ITemplexS
case class PackST(
  members: List[ITemplexS]
) extends ITemplexS
case class BorrowST(
  inner: ITemplexS
) extends ITemplexS
case class RepeaterSequenceST(
  mutability: ITemplexS,
  size: ITemplexS,
  element: ITemplexS
) extends ITemplexS
case class ManualSequenceST(
  elements: List[ITemplexS]
) extends ITemplexS

object TemplexSUtils {
  def getDistinctOrderedRunesForTemplex(templex: ITemplexS): List[IRuneS] = {
    templex match {
      case StringST(_) => List()
      case IntST(_) => List()
      case MutabilityST(_) => List()
      case PermissionST(_) => List()
      case LocationST(_) => List()
      case OwnershipST(_) => List()
      case VariabilityST(_) => List()
      case BoolST(_) => List()
      case NameST(_) => List()
      case AbsoluteNameST(_) => List()
      case RuneST(rune) => List(rune)
      case OwnershippedST(_, inner) => getDistinctOrderedRunesForTemplex(inner)
      case BorrowST(inner) => getDistinctOrderedRunesForTemplex(inner)
      case CallST(template, args) => {
        (template :: args).flatMap(getDistinctOrderedRunesForTemplex).distinct
      }
      case PrototypeST(name, parameters, returnType) => {
        (parameters :+ returnType).flatMap(getDistinctOrderedRunesForTemplex).distinct
      }
      case PackST(members) => {
        members.flatMap(getDistinctOrderedRunesForTemplex).distinct
      }
      case RepeaterSequenceST(mutability, size, element) => {
        List(mutability, size, element).flatMap(getDistinctOrderedRunesForTemplex).distinct
      }
      case ManualSequenceST(elements) => {
        elements.flatMap(getDistinctOrderedRunesForTemplex).distinct
      }
    }
  }

//  // DO NOT COPY this without considering using a traverse pattern like
//  // we do elsewhere.
//  def templexNamesToRunes(envName: INameS, runes: Set[IRuneS])(templex: ITemplexS): ITemplexS = {
//    templex match {
//      case NameST(ImpreciseNameS(List(), CodeTypeNameS(name))) if (runes.exists(_.last == CodeRuneS(name))) => RuneST(envName.addStep(CodeRuneS(name)))
//      case NameST(iname) => NameST(iname)
//      case IntST(value) => IntST(value)
//      case MutabilityST(mutability) => MutabilityST(mutability)
//      case PermissionST(permission) => PermissionST(permission)
//      case LocationST(location) => LocationST(location)
//      case OwnershipST(ownership) => OwnershipST(ownership)
//      case VariabilityST(variability) => VariabilityST(variability)
//      case BoolST(value) => BoolST(value)
//      case RuneST(rune) => RuneST(rune)
//      case OwnershippedST(ownership, inner) => OwnershippedST(ownership, templexNamesToRunes(envName, runes)(inner))
//      case CallST(template, args) => {
//        CallST(
//          templexNamesToRunes(envName, runes)(template),
//          args.map(templexNamesToRunes(envName, runes)))
//      }
//      case PrototypeST(name, parameters, returnType) => {
//        PrototypeST(
//          name,
//          parameters.map(templexNamesToRunes(envName, runes)),
//          templexNamesToRunes(envName, runes)(returnType))
//      }
//      case PackST(members) => {
//        PackST(members.map(templexNamesToRunes(envName, runes)))
//      }
//      case RepeaterSequenceST(mutability, size, element) => {
//        RepeaterSequenceST(
//          templexNamesToRunes(envName, runes)(mutability),
//          templexNamesToRunes(envName, runes)(size),
//          templexNamesToRunes(envName, runes)(element))
//      }
//      case ManualSequenceST(elements) => {
//        ManualSequenceST(elements.map(templexNamesToRunes(envName, runes)))
//      }
//    }
//  }
}