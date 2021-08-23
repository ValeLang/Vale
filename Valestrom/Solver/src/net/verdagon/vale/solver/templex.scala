package net.verdagon.vale.astronomer

//import net.verdagon.vale.parser._
import net.verdagon.vale.vcurious

import scala.collection.immutable.List

//// See PVSBUFI
//sealed trait IRulexAR
//case class IntAR(value: Int) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class MutabilityAR(mutability: MutabilityP) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class PermissionAR(permission: PermissionP) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class LocationAR(location: LocationP) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class OwnershipAR(ownership: OwnershipP) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class VariabilityAR(variability: VariabilityP) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class BoolAR(value: Boolean) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class NameAR(name: String) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class RuneAR(rune: String) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class AnonymousRuneAT() extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class InterpretedAT(ownership: OwnershipP, inner: IRulexAR) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class NullableAT(inner: IRulexAR) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class CallAT(
//    template: IRulexAR,
//    args: Vector[IRulexAR]) extends IRulexAR {
//}
//case class PrototypeAT(
//  name: String,
//  parameters: Vector[IRulexAR],
//  returnType: IRulexAR
//) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class PackAT(
//  members: Vector[IRulexAR]
//) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class RepeaterSequenceAT(
//  size: IRulexAR,
//  element: IRulexAR
//) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class ManualSequenceAT(
//  elements: Vector[IRulexAR]
//) extends IRulexAR { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//
//object TemplexSUtils {
//  def getDistinctOrderedRunesForTemplex(templex: IRulexAR): Vector[String] = {
//    templex match {
//      case IntAR(value) => Vector.empty
//      case MutabilityAR(mutability) => Vector.empty
//      case PermissionAR(permission) => Vector.empty
//      case LocationAR(location) => Vector.empty
//      case OwnershipAR(ownership) => Vector.empty
//      case VariabilityAR(variability) => Vector.empty
//      case BoolAR(value) => Vector.empty
//      case NameAR(name) => Vector.empty
//      case RuneAR(rune) => Vector(rune)
//      case AnonymousRuneAT() => Vector.empty
//      case InterpretedAT(_, inner) => getDistinctOrderedRunesForTemplex(inner)
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
//        Vector(size, element).flatMap(getDistinctOrderedRunesForTemplex).distinct
//      }
//      case ManualSequenceAT(elements) => {
//        elements.flatMap(getDistinctOrderedRunesForTemplex).distinct
//      }
//    }
//  }
//
//  // DO NOT COPY this without considering using a traverse pattern like
//  // we do elsewhere.
//  def templexNamesToRunes(runes: Set[String])(templex: IRulexAR): IRulexAR = {
//    templex match {
//      case IntAR(value) => IntAR(value)
//      case MutabilityAR(mutability) => MutabilityAR(mutability)
//      case PermissionAR(permission) => PermissionAR(permission)
//      case LocationAR(location) => LocationAR(location)
//      case OwnershipAR(ownership) => OwnershipAR(ownership)
//      case VariabilityAR(variability) => VariabilityAR(variability)
//      case BoolAR(value) => BoolAR(value)
//      case NameAR(name) => if (runes.contains(name)) RuneAR(name) else NameAR(name)
//      case RuneAR(rune) => RuneAR(rune)
//      case AnonymousRuneAT() => AnonymousRuneAT()
//      case InterpretedAT(ownership, inner) => InterpretedAT(ownership, templexNamesToRunes(runes)(inner))
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