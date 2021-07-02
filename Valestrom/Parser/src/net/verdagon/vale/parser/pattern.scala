package net.verdagon.vale.parser

import net.verdagon.vale.{vassert, vcheck}

import scala.collection.immutable.List
import scala.util.parsing.input.Positional

sealed trait IVirtualityP
case object AbstractP extends IVirtualityP
case class OverrideP(range: Range, tyype: ITemplexPT) extends IVirtualityP

case class PatternPP(
    range: Range,
    preBorrow: Option[UnitP],
    capture: Option[CaptureP],

//    ownership: Option[OwnershipP],
//    // See RCKC for why we can't capture the kind rune.
//    coordRune: Option[String],
//    kind: Option[ITemplexPT],

    // If they just have a destructure, this will probably be a ManualSequence(None).
    // If they have just parens, this will probably be a Pack(None).
    // Let's be careful to not allow destructuring packs without Pack here, see MEDP.
    templex: Option[ITemplexPT],

    // Eventually, add an ellipsis: Boolean field here... except we also have
    // to account for the difference between a: T... and a...: T (in one, T is a
    // single type and in the other, T is a pack of types). And we might also want
    // to account for nested parens, like struct Fn:((#Params...), (#Rets...))

    destructure: Option[DestructureP],
    virtuality: Option[IVirtualityP]) extends Positional

case class DestructureP(
  range: Range,
  patterns: List[PatternPP])

sealed trait ICaptureNameP
case class LocalNameP(name: NameP) extends ICaptureNameP
case class ConstructingMemberNameP(name: NameP) extends ICaptureNameP

case class CaptureP(
    range: Range,
    name: ICaptureNameP,
    variability: VariabilityP)

//sealed trait ITemplexPT
//case class IntPT(range: Range, value: Int) extends ITemplexPT
//case class BoolPT(value: Boolean) extends ITemplexPT
//case class AnonymousRunePT() extends ITemplexPT
//case class NameOrRunePT(name: StringP) extends ITemplexPT
//case class MutabilityPT(mutability: MutabilityP) extends ITemplexPT
//case class OwnershippedPT(range: Range, ownership: OwnershipP, inner: ITemplexPT) extends ITemplexPT
//case class CallPT(template: ITemplexPT, args: List[ITemplexPT]) extends ITemplexPT
//// We could phrase these all as ICallTemplexPPs but we want to be able to reconstruct
//// a program from this AST.
//case class RepeaterSequencePT(range: Range, mutability: ITemplexPT, size: ITemplexPT, element: ITemplexPT) extends ITemplexPT
//case class ManualSequencePT(members: List[ITemplexPT]) extends ITemplexPT
//case class FunctionPT(mutable: Option[ITemplexPT], params: List[ITemplexPT], ret: ITemplexPT) extends ITemplexPT

object Patterns {
  object capturedWithTypeRune {
    def unapply(arg: PatternPP): Option[(String, String)] = {
      arg match {
        case PatternPP(_, _, Some(CaptureP(_, LocalNameP(NameP(_, name)), FinalP)), Some(NameOrRunePT(NameP(_, kindRune))), None, None) => Some((name, kindRune))
        case _ => None
      }
    }
  }
  object withType {
    def unapply(arg: PatternPP): Option[ITemplexPT] = {
      arg.templex
    }
  }
  object capture {
    def unapply(arg: PatternPP): Option[String] = {
      arg match {
        case PatternPP(_, _, Some(CaptureP(_, LocalNameP(NameP(_, name)), FinalP)), None, None, None) => Some(name)
        case _ => None
      }
    }
  }
  object fromEnv {
    def unapply(arg: PatternPP): Option[String] = {
      arg match {
        case PatternPP(_, _, None, Some(NameOrRunePT(NameP(_, kindName))), None, None) => Some(kindName)
        case _ => None
      }
    }
  }
  object withDestructure {
    def unapply(arg: PatternPP): Option[List[PatternPP]] = {
      arg.destructure match {
        case None => None
        case Some(DestructureP(_, patterns)) => Some(patterns)
      }
    }
  }
  object capturedWithType {
    def unapply(arg: PatternPP): Option[(String, ITemplexPT)] = {
      arg match {
        case PatternPP(_, _, Some(CaptureP(_, LocalNameP(NameP(_, name)), FinalP)), Some(templex), None, None) => Some((name, templex))
        case _ => None
      }
    }
  }
}

object PatternPUtils {
//  def getOrderedIdentifyingRunesFromPattern(atom: PatternPP): List[String] = {
//    val PatternPP(capture, templex, virtuality, destructures) = atom
//
//    // We don't care about capture, it can have no runes.
//    val _ = capture
//
//    // No identifying runes come from overrides, see NIPFO.
//
//    // No identifying runes come from destructures, see DCSIR.
//
//    // So we just care about identifying runes from the templex.
//    // Note, this assumes that we've filled the pattern (it's present, no anonymous runes anywhere in it).
//    vassert(templex.nonEmpty)
//    templex.toList.flatMap(getOrderedRunesFromTemplexWithDuplicates).distinct
//  }
//
//  def getOrderedRunesFromPatternWithDuplicates(atom: PatternPP): List[String] = {
//    val destructures = atom.destructure.toList.flatten.flatten
//
//    atom.virtuality.toList.flatMap(getOrderedRunesFromVirtualityWithDuplicates) ++
//    atom.templex.toList.flatMap(getOrderedRunesFromTemplexWithDuplicates) ++
//    destructures.flatMap(getOrderedRunesFromPatternWithDuplicates)
//  }
//
//  private def getOrderedRunesFromVirtualityWithDuplicates(virtuality: IVirtualityP): List[String] = {
//    virtuality match {
//      case AbstractP => List.empty
//      case OverrideP(tyype: ITemplexPT) => getOrderedRunesFromTemplexWithDuplicates(tyype)
//    }
//  }
//  private def getOrderedRunesFromTemplexesWithDuplicates(templexes: List[ITemplexPT]): List[String] = {
//    templexes.foldLeft(List[String]())({
//      case (previous, current) => previous ++ getOrderedRunesFromTemplexWithDuplicates(current)
//    })
//  }

//  def getOrderedRunesFromTemplexWithDuplicates(templex: ITemplexPT): List[String] = {
//    templex match {
//      case IntPT(value) => List.empty
//      case BoolPT(value) => List.empty
//      case NameOrRunePT(name) => List.empty
//      case MutabilityPT(_) => List.empty
//      case OwnershippedPT(_, inner) => getOrderedRunesFromTemplexWithDuplicates(inner)
//      case CallPT(template, args) => getOrderedRunesFromTemplexesWithDuplicates((template :: args))
//      case RepeaterSequencePT(mutability, size, element) => getOrderedRunesFromTemplexesWithDuplicates(List(mutability, size, element))
//      case ManualSequencePT(members) => getOrderedRunesFromTemplexesWithDuplicates(members)
//      case FunctionPT(mutable, params, ret) => getOrderedRunesFromTemplexesWithDuplicates(params :+ ret)
//    }
//  }

//  def traverseTemplex(
//    templex: ITemplexPT,
//    handler: ITemplexPT => ITemplexPT):
//  (ITemplexPT) = {
//    templex match {
//      case AnonymousRunePT() => handler(templex)
//      case IntPT(value) => handler(templex)
//      case BoolPT(value) => handler(templex)
////      case RunePT(rune) => handler(templex)
//      case NameOrRunePT(name) => handler(templex)
//      case MutabilityPT(mutability) => handler(templex)
//      case OwnershippedPT(_, borrow, innerA) => {
//        val innerB = traverseTemplex(innerA, handler)
//        val newTemplex = OwnershippedPT(borrow, innerB)
//        handler(newTemplex)
//      }
//      case CallPT(templateA, argsA) => {
//        val templateB = traverseTemplex(templateA, handler)
//        val argsB = argsA.map(traverseTemplex(_, handler))
//        val newTemplex = CallPT(templateB, argsB)
//        handler(newTemplex)
//      }
//      case RepeaterSequencePT(mutabilityA, sizeA, elementA) => {
//        val mutabilityB = traverseTemplex(mutabilityA, handler)
//        val sizeB = traverseTemplex(sizeA, handler)
//        val elementB = traverseTemplex(elementA, handler)
//        val newTemplex = RepeaterSequencePT(mutabilityB, sizeB, elementB)
//        handler(newTemplex)
//      }
//      case ManualSequencePT(membersA) => {
//        val membersB = membersA.map(traverseTemplex(_, handler))
//        val newTemplex = ManualSequencePT(membersB)
//        handler(newTemplex)
//      }
//      case FunctionPT(mutable, paramsA, retA) => {
//        val paramsB = paramsA.map(traverseTemplex(_, handler))
//        val retB = traverseTemplex(retA, handler)
//        val newTemplex = FunctionPT(mutable, paramsB, retB)
//        handler(newTemplex)
//      }
//    }
//  }
}
