package net.verdagon.vale.templar.infer

import net.verdagon.vale.astronomer.{CoordTemplataType, ITemplataType, IntegerTemplataType, KindTemplataType, MutabilityTemplataType, OwnershipTemplataType, PackTemplataType, PermissionTemplataType, PrototypeTemplataType, StringTemplataType}
import net.verdagon.vale.templar.IRune2
import net.verdagon.vale.templar.templata.{CoordListTemplata, CoordTemplata, ITemplata, IntegerTemplata, KindTemplata, MutabilityTemplata, OwnershipTemplata, PermissionTemplata, PrototypeTemplata, StringTemplata}
import net.verdagon.vale.{vassert, vwat}

case class Inferences(
  typeByRune: Map[IRune2, ITemplataType], // Here for doublechecking
  templatasByRune: Map[IRune2, ITemplata],
  possibilitiesByRune: Map[IRune2, List[ITemplata]]) {
  def addConclusion(rune: IRune2, templata: ITemplata): Inferences = {
    templatasByRune.get(rune) match {
      case None =>
      case Some(existingConclusion) => vassert(templata == existingConclusion)
    }

    // If theres a declared type, make sure it matches.
    // SolverKindRune wouldnt have a type for example.
    (typeByRune.get(rune), templata) match {
      case (None, _) =>
      case (Some(CoordTemplataType), CoordTemplata(_)) =>
      case (Some(StringTemplataType), StringTemplata(_)) =>
      case (Some(KindTemplataType), KindTemplata(_)) =>
      case (Some(IntegerTemplataType), IntegerTemplata(_)) =>
      case (Some(MutabilityTemplataType), MutabilityTemplata(_)) =>
      case (Some(OwnershipTemplataType), OwnershipTemplata(_)) =>
      case (Some(PermissionTemplataType), PermissionTemplata(_)) =>
      case (Some(PrototypeTemplataType), PrototypeTemplata(_)) =>
      case (Some(PackTemplataType(CoordTemplataType)), CoordListTemplata(_)) =>
      case _ => vwat()
    }

    Inferences(
      typeByRune,
      templatasByRune + (rune -> templata),
      possibilitiesByRune - rune)
  }
  def addPossibilities(rune: IRune2, possibilities: List[ITemplata]): Inferences = {
    if (possibilities.size == 0) {
      vwat()
    } else if (possibilities.size == 1) {
      addConclusion(rune, possibilities.head)
    } else {
      vassert(!templatasByRune.contains(rune))
      possibilitiesByRune.get(rune) match {
        case None =>
        case Some(existingPossibilities) => vassert(possibilities == existingPossibilities)
      }
      Inferences(
        typeByRune,
        templatasByRune,
        possibilitiesByRune + (rune -> possibilities))
    }
  }
  // Returns an Inferences without this rune, and gives all the possibilities for that rune
  def pop(rune: IRune2): (Inferences, List[ITemplata]) = {
    val inferencesWithoutThatRune = Inferences(typeByRune, templatasByRune, possibilitiesByRune - rune)
    (inferencesWithoutThatRune, possibilitiesByRune(rune))
  }
}

case class InferencesBox(var inferences: Inferences) {
  def templatasByRune: Map[IRune2, ITemplata] = inferences.templatasByRune
  def possibilitiesByRune: Map[IRune2, List[ITemplata]] = inferences.possibilitiesByRune

  def addConclusion(rune: IRune2, templata: ITemplata): Unit = {
    inferences = inferences.addConclusion(rune, templata)
  }
  def addPossibilities(rune: IRune2, possibilities: List[ITemplata]): Unit = {
    inferences = inferences.addPossibilities(rune, possibilities)
  }
  // Returns an Inferences without this rune, and gives all the possibilities for that rune
  def pop(rune: IRune2): List[ITemplata] = {
    val (newInferences, result) = inferences.pop(rune)
    inferences = newInferences
    result
  }
}
