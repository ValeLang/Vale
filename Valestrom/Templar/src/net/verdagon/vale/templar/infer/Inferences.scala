package net.verdagon.vale.templar.infer

import net.verdagon.vale.templar.IRune2
import net.verdagon.vale.templar.templata.ITemplata
import net.verdagon.vale.{vassert, vwat}

case class Inferences(
  templatasByRune: Map[IRune2, ITemplata],
  possibilitiesByRune: Map[IRune2, List[ITemplata]]) {
  def addConclusion(rune: IRune2, templata: ITemplata): Inferences = {
    templatasByRune.get(rune) match {
      case None =>
      case Some(existingConclusion) => vassert(templata == existingConclusion)
    }
    Inferences(
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
        templatasByRune,
        possibilitiesByRune + (rune -> possibilities))
    }
  }
  // Returns an Inferences without this rune, and gives all the possibilities for that rune
  def pop(rune: IRune2): (Inferences, List[ITemplata]) = {
    val inferencesWithoutThatRune = Inferences(templatasByRune, possibilitiesByRune - rune)
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
