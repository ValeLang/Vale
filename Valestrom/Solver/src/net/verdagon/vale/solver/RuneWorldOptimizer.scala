package net.verdagon.vale.solver

import net.verdagon.vale.{solver, vassert, vassertSome, vfail, vimpl}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object RuneWorldOptimizer {
  def optimize[RuleID, Literal, Lookup](
    builder: RuneWorldBuilder[RuleID, Literal, Lookup]
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

    val solverState = optimizeInner(builder.rules, runeToCanonicalRune, nextCanonicalRune)
    (runeToCanonicalRune, solverState)
  }

  def optimizeInner[RuleID, Literal, Lookup](
    inputRules: Iterable[IRulexAR[TentativeRune, RuleID, Literal, Lookup]],
    runeToCanonicalRune: TentativeRune => Int,
    numCanonicalRunes: Int,
  ): RuneWorldSolverState[RuleID, Literal, Lookup] = {
    val newRulesAndPuzzles = inputRules.map(optimizeRule(_, runeToCanonicalRune)).toArray
    val (newRules, ruleIndexToPuzzles) = newRulesAndPuzzles.unzip
    val kindRuneToBoundingInterfaceRuneAsMap =
      newRules.collect({ case IsaAR(_, sub, interface) => (sub, interface) }).toMap
    val kindRuneToBoundingInterfaceRune =
      (0 until numCanonicalRunes).map(rune => kindRuneToBoundingInterfaceRuneAsMap.getOrElse(rune, -1)).toArray

    val puzzlesToRuleAndUnknownRunesAndIndexInNumUnknowns = ArrayBuffer[(Int, Array[Int], Int)]()
    val numUnknownsToPuzzles =
      Array(
        ArrayBuffer[Int](),
        ArrayBuffer[Int](),
        ArrayBuffer[Int](),
        ArrayBuffer[Int](),
        ArrayBuffer[Int]())
    val ruleToPuzzles = newRules.map(_ => ArrayBuffer[Int]())
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

  def optimizeRule[RuneID, RuleID, Literal, Lookup](
    inputRule: IRulexAR[TentativeRune, RuleID, Literal, Lookup],
    runeToCanonicalRune: TentativeRune => Int
  ): (IRulexAR[Int, RuleID, Literal, Lookup], Array[Array[Int]]) = {
    inputRule match {
      case LiteralAR(range, uncanonicalResultRune, value) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        (LiteralAR(range, canonicalResultRune, value), Array(Array()))
      }
      case LookupAR(range, uncanonicalResultRune, name) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        (LookupAR(range, canonicalResultRune, name), Array(Array()))
      }
      //      case IntAR(range, uncanonicalResultRune, value) => {
      //        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
      //        (IntAR(range, canonicalResultRune, value), Array(Array()))
      //      }
      //      case LocationAR(range, uncanonicalResultRune, location) => {
      //        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
      //        (LocationAR(range, canonicalResultRune, location), Array(Array()))
      //      }
      //      case MutabilityAR(range, uncanonicalResultRune, mutability) => {
      //        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
      //        (MutabilityAR(range, canonicalResultRune, mutability), Array(Array()))
      //      }
      //      case NameAR(range, uncanonicalResultRune, name) => {
      //        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
      //        (NameAR(range, canonicalResultRune, name), Array(Array()))
      //      }
      //      case OwnershipAR(range, uncanonicalResultRune, ownership) => {
      //        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
      //        (OwnershipAR(range, canonicalResultRune, ownership), Array(Array()))
      //      }
      //      case PermissionAR(range, uncanonicalResultRune, permission) => {
      //        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
      //        (PermissionAR(range, canonicalResultRune, permission), Array(Array()))
      //      }
      //      case StringAR(range, uncanonicalResultRune, value) => {
      //        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
      //        (StringAR(range, canonicalResultRune, value), Array(Array()))
      //      }
      //      case VariabilityAR(range, uncanonicalResultRune, variability) => {
      //        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
      //        (VariabilityAR(range, canonicalResultRune, variability), Array(Array()))
      //      }
      //      case AbsoluteNameAR(range, uncanonicalResultRune, name) => {
      //        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
      //        (AbsoluteNameAR(range, canonicalResultRune, name), Array(Array()))
      //      }
      //      case BoolAR(range, uncanonicalResultRune, value) => {
      //        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
      //        (BoolAR(range, canonicalResultRune, value), Array(Array()))
      //      }
      case BuiltinCallAR(range, uncanonicalResultRune, name, args) => {
        vimpl() // split into actual things
        //            val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        //            (BoolAR(range, canonicalResultRune, value), Array(Array()))
      }
      case CallAR(range, uncanonicalResultRune, uncanonicalTemplateRune, uncanonicalArgRunes) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        val canonicalTemplateRune = runeToCanonicalRune(uncanonicalTemplateRune)
        val canonicalArgRunes = uncanonicalArgRunes.map(runeToCanonicalRune).toArray
        (CallAR(range, canonicalResultRune, canonicalTemplateRune, canonicalArgRunes), Array(Array(canonicalResultRune), canonicalArgRunes))
      }
      case CoordComponentsAR(range, uncanonicalCoordRune, uncanonicalOwnershipRune, uncanonicalPermissionRune, uncanonicalKindRune) => {
        val canonicalCoordRune = runeToCanonicalRune(uncanonicalCoordRune)
        val canonicalOwnershipRune = runeToCanonicalRune(uncanonicalOwnershipRune)
        val canonicalPermissionRune = runeToCanonicalRune(uncanonicalPermissionRune)
        val canonicalKindRune = runeToCanonicalRune(uncanonicalKindRune)
        (
          CoordComponentsAR(
            range, canonicalCoordRune, canonicalOwnershipRune, canonicalPermissionRune, canonicalKindRune),
          Array(
            Array(canonicalOwnershipRune, canonicalPermissionRune, canonicalKindRune),
            Array(canonicalCoordRune)))
      }
      case CoordListAR(range, uncanonicalResultRune, uncanonicalElementRunes) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        val canonicalElementRunes = uncanonicalElementRunes.map(runeToCanonicalRune).toArray
        (
          CoordListAR(range, canonicalResultRune, canonicalElementRunes),
          Array(Array(canonicalResultRune), canonicalElementRunes))
      }
      case AugmentAR(range, uncanonicalResultRune, literal, uncanonicalInnerRune) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        val canonicalInnerRune = runeToCanonicalRune(uncanonicalInnerRune)
        (solver.AugmentAR(range, canonicalResultRune, literal, canonicalInnerRune), Array(Array(canonicalResultRune), Array(canonicalInnerRune)))
      }
      case IsaAR(range, uncanonicalSubRune, uncanonicalInterfaceRune) => {
        val canonicalSubRune = runeToCanonicalRune(uncanonicalSubRune)
        val canonicalInterfaceRune = runeToCanonicalRune(uncanonicalInterfaceRune)
        (IsaAR(range, canonicalSubRune, canonicalInterfaceRune), Array(Array(vimpl())))
      }
      case KindComponentsAR(range, uncanonicalKindRune, uncanonicalMutabilityRune) => {
        val canonicalKindRune = runeToCanonicalRune(uncanonicalKindRune)
        val canonicalMutabilityRune = runeToCanonicalRune(uncanonicalMutabilityRune)
        (
          KindComponentsAR(range, canonicalKindRune, canonicalMutabilityRune),
          Array(Array(canonicalKindRune), Array(canonicalMutabilityRune)))
      }
      case ManualSequenceAR(range, uncanonicalResultRune, uncanonicalElementRunes) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        val canonicalElementRunes = uncanonicalElementRunes.map(runeToCanonicalRune).toArray
        (
          ManualSequenceAR(range, canonicalResultRune, canonicalElementRunes),
          Array(Array(canonicalResultRune), canonicalElementRunes))
      }
      case OrAR(range, possibilities) => {
        vimpl()
        //            val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)

      }
      case PrototypeAR(range, uncanonicalResultRune, name, uncanonicalParameterRunes, uncanonicalReturnRune) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        val canonicalParameterRunes = uncanonicalParameterRunes.map(runeToCanonicalRune).toArray
        val canonicalReturnRune = runeToCanonicalRune(uncanonicalReturnRune)
        (
          PrototypeAR(range, canonicalResultRune, name, canonicalParameterRunes, canonicalReturnRune),
          Array(Array(canonicalResultRune), canonicalParameterRunes :+ canonicalReturnRune))
      }
      case RepeaterSequenceAR(range, uncanonicalResultRune, uncanonicalMutabilityRune, uncanonicalVariabilityRune, uncanonicalSizeRune, uncanonicalElementRune) => {
        val canonicalResultRune = runeToCanonicalRune(uncanonicalResultRune)
        val canonicalMutabilityRune = runeToCanonicalRune(uncanonicalMutabilityRune)
        val canonicalVariabilityRune = runeToCanonicalRune(uncanonicalVariabilityRune)
        val canonicalSizeRune = runeToCanonicalRune(uncanonicalSizeRune)
        val canonicalElementRune = runeToCanonicalRune(uncanonicalElementRune)
        (
          RepeaterSequenceAR(range, canonicalResultRune, canonicalMutabilityRune, canonicalVariabilityRune, canonicalSizeRune, canonicalElementRune),
          Array(Array(canonicalResultRune), Array(canonicalMutabilityRune, canonicalVariabilityRune, canonicalSizeRune, canonicalElementRune)))
      }
      case _ => vfail()
    }
  }
}
