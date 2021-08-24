package net.verdagon.vale.solver

import net.verdagon.vale.{solver, vassert, vassertSome, vfail, vimpl}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

trait IRunePuzzler[RuleID, Literal, Lookup] {
  def getPuzzles(rulexAR: IRulexAR[Int, RuleID, Literal, Lookup]): Array[Array[Int]]
}

// Let's move this out to templar once we've finished migrating templar to it
object TemplarPuzzler {
  def apply[RuneID, RuleID, Literal, Lookup](
    inputRule: IRulexAR[Int, RuleID, Literal, Lookup]
  ): Array[Array[Int]] = {
    inputRule match {
      case LiteralAR(range, canonicalResultRune, value) => {
        Array(Array())
      }
      case LookupAR(range, canonicalResultRune, name) => {
        Array(Array())
      }
      case IsConcreteAR(range, canonicalArgRune) => {
        Array(Array(canonicalArgRune))
      }
      case IsInterfaceAR(range, canonicalArgRune) => {
        Array(Array(canonicalArgRune))
      }
      case IsStructAR(range, canonicalArgRune) => {
        Array(Array(canonicalArgRune))
      }
      case CoerceToCoord(range, coordRune, kindRune) => {
        Array(Array(kindRune))
      }
      case CallAR(range, canonicalResultRune, canonicalTemplateRune, canonicalArgRunes) => {
        Array(Array(canonicalResultRune, canonicalTemplateRune), (Vector(canonicalTemplateRune) ++ canonicalArgRunes).toArray)
      }
      case CoordComponentsAR(range, canonicalCoordRune, canonicalOwnershipRune, canonicalPermissionRune, canonicalKindRune) => {
        Array(Array(canonicalOwnershipRune, canonicalPermissionRune, canonicalKindRune), Array(canonicalCoordRune))
      }
      case CoordListAR(range, canonicalResultRune, canonicalElementRunes) => {
        Array(Array(canonicalResultRune), canonicalElementRunes)
      }
      case AugmentAR(range, canonicalResultRune, literal, canonicalInnerRune) => {
        Array(Array(canonicalResultRune), Array(canonicalInnerRune))
      }
      case IsaAR(range, canonicalSubRune, canonicalInterfaceRune) => {
        Array(Array(vimpl()))
      }
      case KindComponentsAR(range, canonicalKindRune, canonicalMutabilityRune) => {
        Array(Array(canonicalKindRune), Array(canonicalMutabilityRune))
      }
      case ManualSequenceAR(range, canonicalResultRune, canonicalElementRunes) => {
        Array(Array(canonicalResultRune), canonicalElementRunes)
      }
      case OneOfAR(range, canonicalResultRune, possibilities) => {
        Array(Array(canonicalResultRune))
      }
      case PrototypeAR(range, canonicalResultRune, name, canonicalParameterRunes, canonicalReturnRune) => {
        Array(Array(canonicalResultRune), canonicalParameterRunes :+ canonicalReturnRune)
      }
      case RepeaterSequenceAR(range, canonicalResultRune, canonicalMutabilityRune, canonicalVariabilityRune, canonicalSizeRune, canonicalElementRune) => {
        Array(Array(canonicalResultRune), Array(canonicalMutabilityRune, canonicalVariabilityRune, canonicalSizeRune, canonicalElementRune))
      }
      case _ => vfail()
    }
  }
}

object RuneWorldOptimizer {
  def optimize[RuleID, Literal, Lookup](
    builder: RuneWorldBuilder[RuleID, Literal, Lookup],
    puzzler: IRunePuzzler[RuleID, Literal, Lookup]
  ): (mutable.HashMap[TentativeRune, Int], RuneWorldSolverState[RuleID, Literal, Lookup]) = {
    // Right now, the original runes are spaced out. Even if we have 8 original runes, their numbers might be
    // 3, 6, 14, 16, 19, 24, 27, 30.
    // Let's re-number them so they're all dense. These will be the "canonical" runes.
    var nextCanonicalRune = 0
    val runeToCanonicalRune = mutable.HashMap[TentativeRune, Int]()
    (0 until builder.nextTentativeRuneIndex).foreach(tentativeRuneIndex => {
      val tentativeRune = TentativeRune(tentativeRuneIndex)
      builder.redundantRuneToOriginalRune.get(tentativeRune) match {
        case None => {
          // This rune isnt redundant with anything, give it its own canonical rune.
          val canonicalRune = nextCanonicalRune
          nextCanonicalRune = nextCanonicalRune + 1
          runeToCanonicalRune.put(tentativeRune, canonicalRune)
        }
        case Some(originalRune) => {
          // The originalRune should be less than this rune, so it should already have a canonical one assigned.
          val canonicalRune = vassertSome(runeToCanonicalRune.get(originalRune))
          runeToCanonicalRune.put(tentativeRune, canonicalRune)
        }
      }
    })

    val newRules = builder.rules.map(canonicalizeRule(_, runeToCanonicalRune)).toArray
    val ruleIndexToPuzzles = newRules.map(TemplarPuzzler.apply)

    val kindRuneToBoundingInterfaceRuneAsMap =
      newRules.collect({ case IsaAR(_, sub, interface) => (sub, interface) }).toMap
    val kindRuneToBoundingInterfaceRune =
      (0 until nextCanonicalRune).map(rune => kindRuneToBoundingInterfaceRuneAsMap.getOrElse(rune, -1)).toArray

    val solverState = optimizeInner(newRules, ruleIndexToPuzzles, nextCanonicalRune, kindRuneToBoundingInterfaceRune)

    (runeToCanonicalRune, solverState)
  }

  def optimizeInner[RuleID, Literal, Lookup](
    newRules: Array[IRulexAR[Int, RuleID, Literal, Lookup]],
    ruleIndexToPuzzles: Array[Array[Array[Int]]],
    numCanonicalRunes: Int,
    kindRuneToBoundingInterfaceRune: Array[Int]
  ): RuneWorldSolverState[RuleID, Literal, Lookup] = {
    val puzzlesToRuleAndUnknownRunesAndIndexInNumUnknowns = ArrayBuffer[(Int, Array[Int], Int)]()
    val numUnknownsToPuzzles =
      Array(
        ArrayBuffer[Int](),
        ArrayBuffer[Int](),
        ArrayBuffer[Int](),
        ArrayBuffer[Int](),
        ArrayBuffer[Int]())
    val ruleToPuzzles = newRules.map(_ => ArrayBuffer[Int]()).toArray
    val runeToPuzzles = (0 until numCanonicalRunes).map(_ => ArrayBuffer[Int]()).toArray
    ruleIndexToPuzzles.zipWithIndex.foreach({ case (puzzlesForRule, ruleIndex) =>
      puzzlesForRule.foreach(puzzleUnknownRunes => {
        val puzzle = puzzlesToRuleAndUnknownRunesAndIndexInNumUnknowns.size

        val indexInNumUnknowns = numUnknownsToPuzzles(puzzleUnknownRunes.length).length
        numUnknownsToPuzzles(puzzleUnknownRunes.length) += puzzle

        val thing = (ruleIndex, puzzleUnknownRunes, indexInNumUnknowns)
        puzzlesToRuleAndUnknownRunesAndIndexInNumUnknowns += thing
        ruleToPuzzles(ruleIndex) += puzzle
        puzzleUnknownRunes.foreach(unknownRune => {
          runeToPuzzles(unknownRune) += puzzle
        })
      })
    })

    val numPuzzles = puzzlesToRuleAndUnknownRunesAndIndexInNumUnknowns.size
    val puzzleToRule = puzzlesToRuleAndUnknownRunesAndIndexInNumUnknowns.map(_._1)
    val puzzleToUnknownRunes = puzzlesToRuleAndUnknownRunesAndIndexInNumUnknowns.map(_._2)
    val puzzleToIndexInNumUnknowns = puzzlesToRuleAndUnknownRunesAndIndexInNumUnknowns.map(_._3)
//    val ruleToRunes = ruleToPuzzles.map(puzzles => puzzles.map(puzzleToUnknownRunes).flatten.toArray)
    val puzzleToSatisfied = puzzleToRule.indices.map(_ => false).toArray

    puzzleToUnknownRunes.zipWithIndex.foreach({ case (unknownRunes, puzzle) =>
      vassert(unknownRunes.length == unknownRunes.distinct.length)
    })

    val runeWorld =
      RuneWorld[Int, RuleID, Literal, Lookup](
        newRules,
//        ruleToRunes.toArray,
        puzzleToRule.toArray,
        puzzleToUnknownRunes.map(_.clone()).toArray,
        ruleToPuzzles.map(_.toArray),
        runeToPuzzles.map(_.toArray),
        kindRuneToBoundingInterfaceRune)

    RuneWorldSolverState[RuleID, Literal, Lookup](
      runeWorld,
      puzzleToSatisfied.clone(),
      puzzleToUnknownRunes.map(_.length).toArray,
      puzzleToUnknownRunes.map(_.clone()).toArray,
      puzzleToIndexInNumUnknowns.toArray.clone(),
      numUnknownsToPuzzles.map(_.length).toArray,
      numUnknownsToPuzzles.map(puzzles => {
        // Fill in the rest with -1s
        puzzles ++= (0 until (numPuzzles - puzzles.length)).map(_ => -1)
        vassert(puzzles.length == numPuzzles)
        puzzles.toArray.clone()
      }).toArray)
  }

  def canonicalizeRule[RuneID, RuleID, Literal, Lookup](
    inputRule: IRulexAR[TentativeRune, RuleID, Literal, Lookup],
    runeToCanonicalRune: TentativeRune => Int
  ): IRulexAR[Int, RuleID, Literal, Lookup] = {
    inputRule match {
      case LiteralAR(range, uncanonicalResultRune, value) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        LiteralAR(range, canonicalResultRune, value)
      }
      case LookupAR(range, uncanonicalResultRune, name) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        LookupAR(range, canonicalResultRune, name)
      }
      case IsConcreteAR(range, uncanonicalRune) => {
        val canonicalRune = runeToCanonicalRune(uncanonicalRune)
        IsConcreteAR(range, canonicalRune)
      }
      case IsInterfaceAR(range, uncanonicalRune) => {
        val canonicalRune = runeToCanonicalRune(uncanonicalRune)
        IsInterfaceAR(range, canonicalRune)
      }
      case IsStructAR(range, uncanonicalRune) => {
        val canonicalRune = runeToCanonicalRune(uncanonicalRune)
        IsStructAR(range, canonicalRune)
      }
      case CoerceToCoord(range, uncanonicalCoordRune, uncanonicalKindRune) => {
        val canonicalCoordRune = runeToCanonicalRune(uncanonicalCoordRune)
        val canonicalKindRune = runeToCanonicalRune(uncanonicalKindRune)
        CoerceToCoord(range, canonicalCoordRune, canonicalKindRune)
      }
      case CallAR(range, uncanonicalResultRune, uncanonicalTemplateRune, uncanonicalArgRunes) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        val canonicalTemplateRune = runeToCanonicalRune(uncanonicalTemplateRune)
        val canonicalArgRunes = uncanonicalArgRunes.map(runeToCanonicalRune).toArray
        CallAR(range, canonicalResultRune, canonicalTemplateRune, canonicalArgRunes)
      }
      case CoordComponentsAR(range, uncanonicalCoordRune, uncanonicalOwnershipRune, uncanonicalPermissionRune, uncanonicalKindRune) => {
        val canonicalCoordRune = runeToCanonicalRune(uncanonicalCoordRune)
        val canonicalOwnershipRune = runeToCanonicalRune(uncanonicalOwnershipRune)
        val canonicalPermissionRune = runeToCanonicalRune(uncanonicalPermissionRune)
        val canonicalKindRune = runeToCanonicalRune(uncanonicalKindRune)
        CoordComponentsAR(
          range, canonicalCoordRune, canonicalOwnershipRune, canonicalPermissionRune, canonicalKindRune)
      }
      case CoordListAR(range, uncanonicalResultRune, uncanonicalElementRunes) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        val canonicalElementRunes = uncanonicalElementRunes.map(runeToCanonicalRune).toArray
        CoordListAR(range, canonicalResultRune, canonicalElementRunes)
      }
      case AugmentAR(range, uncanonicalResultRune, literal, uncanonicalInnerRune) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        val canonicalInnerRune = runeToCanonicalRune(uncanonicalInnerRune)
        solver.AugmentAR(range, canonicalResultRune, literal, canonicalInnerRune)
      }
      case IsaAR(range, uncanonicalSubRune, uncanonicalInterfaceRune) => {
        val canonicalSubRune = runeToCanonicalRune(uncanonicalSubRune)
        val canonicalInterfaceRune = runeToCanonicalRune(uncanonicalInterfaceRune)
        IsaAR(range, canonicalSubRune, canonicalInterfaceRune)
      }
      case KindComponentsAR(range, uncanonicalKindRune, uncanonicalMutabilityRune) => {
        val canonicalKindRune = runeToCanonicalRune(uncanonicalKindRune)
        val canonicalMutabilityRune = runeToCanonicalRune(uncanonicalMutabilityRune)
        KindComponentsAR(range, canonicalKindRune, canonicalMutabilityRune)
      }
      case ManualSequenceAR(range, uncanonicalResultRune, uncanonicalElementRunes) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        val canonicalElementRunes = uncanonicalElementRunes.map(runeToCanonicalRune).toArray
        ManualSequenceAR(range, canonicalResultRune, canonicalElementRunes)
      }
      case OneOfAR(range, uncanonicalResultRune, possibilities) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        OneOfAR(range, canonicalResultRune, possibilities)
      }
      case PrototypeAR(range, uncanonicalResultRune, name, uncanonicalParameterRunes, uncanonicalReturnRune) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        val canonicalParameterRunes = uncanonicalParameterRunes.map(runeToCanonicalRune).toArray
        val canonicalReturnRune = runeToCanonicalRune(uncanonicalReturnRune)
        PrototypeAR(range, canonicalResultRune, name, canonicalParameterRunes, canonicalReturnRune)
      }
      case RepeaterSequenceAR(range, uncanonicalResultRune, uncanonicalMutabilityRune, uncanonicalVariabilityRune, uncanonicalSizeRune, uncanonicalElementRune) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        val canonicalMutabilityRune = runeToCanonicalRune(uncanonicalMutabilityRune)
        val canonicalVariabilityRune = runeToCanonicalRune(uncanonicalVariabilityRune)
        val canonicalSizeRune = runeToCanonicalRune(uncanonicalSizeRune)
        val canonicalElementRune = runeToCanonicalRune(uncanonicalElementRune)
        RepeaterSequenceAR(range, canonicalResultRune, canonicalMutabilityRune, canonicalVariabilityRune, canonicalSizeRune, canonicalElementRune)
      }
      case _ => vfail()
    }
  }
}
