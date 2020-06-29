package net.verdagon.vale.astronomer

import net.verdagon.vale.parser._
import net.verdagon.vale.vcurious

import scala.collection.immutable.List

//// See PVSBUFI
//sealed trait ITemplexA
//case class IntAT(value: Int) extends ITemplexA
//case class MutabilityAT(mutability: MutabilityP) extends ITemplexA
//case class PermissionAT(permission: PermissionP) extends ITemplexA
//case class LocationAT(location: LocationP) extends ITemplexA
//case class OwnershipAT(ownership: OwnershipP) extends ITemplexA
//case class VariabilityAT(variability: VariabilityP) extends ITemplexA
//case class BoolAT(value: Boolean) extends ITemplexA
//case class NameAT(name: String) extends ITemplexA
//case class RuneAT(rune: String) extends ITemplexA
//case class AnonymousRuneAT() extends ITemplexA
//case class OwnershippedAT(ownership: OwnershipP, inner: ITemplexA) extends ITemplexA
//case class NullableAT(inner: ITemplexA) extends ITemplexA
//case class CallAT(
//    template: ITemplexA,
//    args: List[ITemplexA]) extends ITemplexA {
//}
//case class PrototypeAT(
//  name: String,
//  parameters: List[ITemplexA],
//  returnType: ITemplexA
//) extends ITemplexA
//case class PackAT(
//  members: List[ITemplexA]
//) extends ITemplexA
//case class RepeaterSequenceAT(
//  size: ITemplexA,
//  element: ITemplexA
//) extends ITemplexA
//case class ManualSequenceAT(
//  elements: List[ITemplexA]
//) extends ITemplexA
//
//object TemplexSUtils {
//  def getDistinctOrderedRunesForTemplex(templex: ITemplexA): List[String] = {
//    templex match {
//      case IntAT(value) => List()
//      case MutabilityAT(mutability) => List()
//      case PermissionAT(permission) => List()
//      case LocationAT(location) => List()
//      case OwnershipAT(ownership) => List()
//      case VariabilityAT(variability) => List()
//      case BoolAT(value) => List()
//      case NameAT(name) => List()
//      case RuneAT(rune) => List(rune)
//      case AnonymousRuneAT() => List()
//      case OwnershippedAT(_, inner) => getDistinctOrderedRunesForTemplex(inner)
//      case CallAT(template, args) => {
//        (template :: args).flatMap(getDistinctOrderedRunesForTemplex).distinct
//      }
//      case PrototypeAT(name, parameters, returnType) => {
//        (parameters :+ returnType).flatMap(getDistinctOrderedRunesForTemplex).distinct
//      }
//      case PackAT(members) => {
//        members.flatMap(getDistinctOrderedRunesForTemplex).distinct
//      }
//      case RepeaterSequenceAT(size, element) => {
//        List(size, element).flatMap(getDistinctOrderedRunesForTemplex).distinct
//      }
//      case ManualSequenceAT(elements) => {
//        elements.flatMap(getDistinctOrderedRunesForTemplex).distinct
//      }
//    }
//  }
//
//  // DO NOT COPY this without considering using a traverse pattern like
//  // we do elsewhere.
//  def templexNamesToRunes(runes: Set[String])(templex: ITemplexA): ITemplexA = {
//    templex match {
//      case IntAT(value) => IntAT(value)
//      case MutabilityAT(mutability) => MutabilityAT(mutability)
//      case PermissionAT(permission) => PermissionAT(permission)
//      case LocationAT(location) => LocationAT(location)
//      case OwnershipAT(ownership) => OwnershipAT(ownership)
//      case VariabilityAT(variability) => VariabilityAT(variability)
//      case BoolAT(value) => BoolAT(value)
//      case NameAT(name) => if (runes.contains(name)) RuneAT(name) else NameAT(name)
//      case RuneAT(rune) => RuneAT(rune)
//      case AnonymousRuneAT() => AnonymousRuneAT()
//      case OwnershippedAT(ownership, inner) => OwnershippedAT(ownership, templexNamesToRunes(runes)(inner))
//      case CallAT(template, args) => {
//        CallAT(
//          templexNamesToRunes(runes)(template),
//          args.map(templexNamesToRunes(runes)))
//      }
//      case PrototypeAT(name, parameters, returnType) => {
//        PrototypeAT(
//          name,
//          parameters.map(templexNamesToRunes(runes)),
//          templexNamesToRunes(runes)(returnType))
//      }
//      case PackAT(members) => {
//        PackAT(members.map(templexNamesToRunes(runes)))
//      }
//      case RepeaterSequenceAT(size, element) => {
//        RepeaterSequenceAT(
//          templexNamesToRunes(runes)(size),
//          templexNamesToRunes(runes)(element))
//      }
//      case ManualSequenceAT(elements) => {
//        ManualSequenceAT(elements.map(templexNamesToRunes(runes)))
//      }
//    }
//  }
//}