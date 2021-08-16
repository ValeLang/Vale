package net.verdagon.vale.scout

import net.verdagon.vale.parser._
import net.verdagon.vale.{vassert, vcheck, vcurious, vimpl, vpass, vwat}

import scala.collection.immutable.List
import scala.runtime
import scala.runtime.ScalaRunTime
import scala.util.hashing.MurmurHash3

// We paackage runes with a full name so we don't have to worry about collisions
// between, for example, two ImplicitRune(0)s.

// We have this INameS stuff so we don't have to have prefixes and names like
// __magic_0 __magic_1 __Closure etc.

sealed trait INameS
sealed trait IVarNameS extends INameS
sealed trait IFunctionDeclarationNameS extends INameS
case class LambdaNameS(
//  parentName: INameS,
  codeLocation: CodeLocationS
) extends IFunctionDeclarationNameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class FunctionNameS(name: String, codeLocation: CodeLocationS) extends IFunctionDeclarationNameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class TopLevelCitizenDeclarationNameS(name: String, codeLocation: CodeLocationS) extends INameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class LambdaStructNameS(lambdaName: LambdaNameS) extends INameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ImplNameS(subCitizenHumanName: String, codeLocation: CodeLocationS) extends INameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ExportAsNameS(codeLocation: CodeLocationS) extends INameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class LetNameS(codeLocation: CodeLocationS) extends INameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class UnnamedLocalNameS(codeLocation: CodeLocationS) extends IVarNameS {  override def hashCode(): Int = vcurious() }
case class ClosureParamNameS() extends IVarNameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class MagicParamNameS(codeLocation: CodeLocationS) extends IVarNameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class CodeVarNameS(name: String) extends IVarNameS {
  vcheck(name != "set", "Can't name a variable 'set'")
  vcheck(name != "mut", "Can't name a variable 'mut'")
}
case class ConstructingMemberNameS(name: String) extends IVarNameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
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
case class CodeRuneS(name: String) extends IRuneS {
  vpass()
}
case class ImplicitRuneS(containerName: INameS, name: Int) extends IRuneS {
  vpass()
}
case class LetImplicitRuneS(codeLocationS: CodeLocationS, name: Int) extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class MagicParamRuneS(codeLocationS: CodeLocationS) extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class MemberRuneS(memberIndex: Int) extends IRuneS {
   val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
// Used to type the templex handed to the size part of the static sized array expressions
case class ArraySizeImplicitRuneS() extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
// Used to type the templex handed to the mutability part of the static sized array expressions
case class ArrayMutabilityImplicitRuneS() extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
// Used to type the templex handed to the variability part of the static sized array expressions
case class ArrayVariabilityImplicitRuneS() extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ReturnRuneS() extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
// These are only made by the templar
case class ExplicitTemplateArgRuneS(index: Int) extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

sealed trait IImpreciseNameStepS
case class CodeTypeNameS(name: String) extends IImpreciseNameStepS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
// When we're calling a function, we're addressing an overload set, not a specific function.
// If we want a specific function, we use TopLevelDeclarationNameS.
case class GlobalFunctionFamilyNameS(name: String) extends IImpreciseNameStepS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case class ImpreciseCodeVarNameS(name: String) extends IImpreciseNameStepS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }


// See PVSBUFI
sealed trait ITemplexS { def range: RangeS }
case class IntST(range: RangeS, value: Long) extends ITemplexS { override def hashCode(): Int = vcurious() }
case class StringST(range: RangeS, value: String) extends ITemplexS { override def hashCode(): Int = vcurious() }
case class MutabilityST(range: RangeS, mutability: MutabilityP) extends ITemplexS { override def hashCode(): Int = vcurious() }
case class PermissionST(range: RangeS, permission: PermissionP) extends ITemplexS { override def hashCode(): Int = vcurious() }
case class LocationST(range: RangeS, location: LocationP) extends ITemplexS { override def hashCode(): Int = vcurious() }
case class OwnershipST(range: RangeS, ownership: OwnershipP) extends ITemplexS { override def hashCode(): Int = vcurious() }
case class VariabilityST(range: RangeS, variability: VariabilityP) extends ITemplexS { override def hashCode(): Int = vcurious() }
case class BoolST(range: RangeS, value: Boolean) extends ITemplexS { override def hashCode(): Int = vcurious() }
case class AbsoluteNameST(range: RangeS, name: INameS) extends ITemplexS { override def hashCode(): Int = vcurious() }
case class NameST(range: RangeS, name: CodeTypeNameS) extends ITemplexS { override def hashCode(): Int = vcurious() }
case class RuneST(range: RangeS, rune: IRuneS) extends ITemplexS { override def hashCode(): Int = vcurious() }
case class InterpretedST(range: RangeS, ownership: OwnershipP, permission: PermissionP, inner: ITemplexS) extends ITemplexS { override def hashCode(): Int = vcurious() }
//case class PermissionedST(range: RangeS, permission: PermissionP, inner: ITemplexS) extends ITemplexS {  override def hashCode(): Int = vcurious() }
case class NullableST(range: RangeS, inner: ITemplexS) extends ITemplexS { override def hashCode(): Int = vcurious() }
case class CallST(range: RangeS,
    template: ITemplexS,
    args: Vector[ITemplexS]) extends ITemplexS {
}
//case class FunctionST(
//  mutability: Option[ITemplexS],
//  parameters: Vector[Option[ITemplexS]],
//  returnType: Option[ITemplexS]
//) extends ITemplexS {  override def hashCode(): Int = vcurious() }
case class PrototypeST(
  range: RangeS,
  name: String,
  parameters: Vector[ITemplexS],
  returnType: ITemplexS
) extends ITemplexS { override def hashCode(): Int = vcurious() }
case class PackST(
  range: RangeS,
  members: Vector[ITemplexS]
) extends ITemplexS { override def hashCode(): Int = vcurious() }
case class BorrowST(
  range: RangeS,
  inner: ITemplexS
) extends ITemplexS { override def hashCode(): Int = vcurious() }
case class RepeaterSequenceST(
  range: RangeS,
  mutability: ITemplexS,
  variability: ITemplexS,
  size: ITemplexS,
  element: ITemplexS
) extends ITemplexS { override def hashCode(): Int = vcurious() }
case class ManualSequenceST(
  range: RangeS,
  elements: Vector[ITemplexS]
) extends ITemplexS { override def hashCode(): Int = vcurious() }

object TemplexSUtils {
  def getDistinctOrderedRunesForTemplex(templex: ITemplexS): Vector[IRuneS] = {
    templex match {
      case StringST(_, _) => Vector.empty
      case IntST(_, _) => Vector.empty
      case MutabilityST(_, _) => Vector.empty
      case PermissionST(_, _) => Vector.empty
      case LocationST(_, _) => Vector.empty
      case OwnershipST(_, _) => Vector.empty
      case VariabilityST(_, _) => Vector.empty
      case BoolST(_, _) => Vector.empty
      case NameST(_, _) => Vector.empty
      case AbsoluteNameST(_, _) => Vector.empty
      case RuneST(_, rune) => Vector(rune)
      case InterpretedST(_, _, _, inner) => getDistinctOrderedRunesForTemplex(inner)
      case BorrowST(_, inner) => getDistinctOrderedRunesForTemplex(inner)
      case CallST(_, template, args) => {
        (Vector(template) ++ args).flatMap(getDistinctOrderedRunesForTemplex).distinct
      }
      case PrototypeST(_, name, parameters, returnType) => {
        (parameters :+ returnType).flatMap(getDistinctOrderedRunesForTemplex).distinct
      }
      case PackST(_, members) => {
        members.flatMap(getDistinctOrderedRunesForTemplex).distinct
      }
      case RepeaterSequenceST(_, mutability, variability, size, element) => {
        Vector(mutability, variability, size, element).flatMap(getDistinctOrderedRunesForTemplex).distinct
      }
      case ManualSequenceST(_, elements) => {
        elements.flatMap(getDistinctOrderedRunesForTemplex).distinct
      }
    }
  }

//  // DO NOT COPY this without considering using a traverse pattern like
//  // we do elsewhere.
//  def templexNamesToRunes(envName: INameS, runes: Set[IRuneS])(templex: ITemplexS): ITemplexS = {
//    templex match {
//      case NameST(ImpreciseNameS(Vector.empty, CodeTypeNameS(name))) if (runes.exists(_.last == CodeRuneS(name))) => RuneST(envName.addStep(CodeRuneS(name)))
//      case NameST(iname) => NameST(iname)
//      case IntST(value) => IntST(value)
//      case MutabilityST(mutability) => MutabilityST(mutability)
//      case PermissionST(permission) => PermissionST(permission)
//      case LocationST(location) => LocationST(location)
//      case OwnershipST(ownership) => OwnershipST(ownership)
//      case VariabilityST(variability) => VariabilityST(variability)
//      case BoolST(value) => BoolST(value)
//      case RuneST(rune) => RuneST(rune)
//      case InterpretedST(ownership, inner) => InterpretedST(ownership, templexNamesToRunes(envName, runes)(inner))
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