package net.verdagon.vale.solver

import net.verdagon.vale.{Err, Ok, Result, vassert, vcurious, vfail, vimpl}

object Planner {
  def solve[RuleID, Literal, Lookup](
    originalPlannerState: PlannerState[RuleID, Literal, Lookup]
  ): (Array[Int], Array[Boolean]) = {
    val world = originalPlannerState.world
    val plannerState = originalPlannerState.deepClone()

    val runeToIsSolved: Array[Boolean] = world.runeToPuzzles.map(_ => false)

    plannerState.sanityCheck(runeToIsSolved)

    var numRulesExecuted = 0
    val orderedRules: Array[Int] = plannerState.world.rules.map(_ => -1)

    while (plannerState.numUnknownsToNumPuzzles(0) > 0) {
      vassert(plannerState.numUnknownsToPuzzles(0)(0) >= 0)

      val numSolvableRules = plannerState.numUnknownsToNumPuzzles(0)

      val solvingPuzzle = plannerState.numUnknownsToPuzzles(0)(numSolvableRules - 1)
      vassert(solvingPuzzle >= 0)
      vassert(plannerState.puzzleToIndexInNumUnknowns(solvingPuzzle) == numSolvableRules - 1)

      val solvingRule = world.puzzleToRule(solvingPuzzle)
      val ruleRunes = world.ruleToRunes(solvingRule)

      world.ruleToPuzzles(solvingRule).foreach(rulePuzzle => {
        vassert(!plannerState.puzzleToExecuted(rulePuzzle))
      })

      orderedRules(numRulesExecuted) = solvingRule
      numRulesExecuted = numRulesExecuted + 1

      world.ruleToPuzzles(solvingRule).foreach(rulePuzzle => {
        plannerState.puzzleToExecuted(rulePuzzle) = true
      })

      ruleRunes.foreach({ case newlySolvedRune =>
        if (!runeToIsSolved(newlySolvedRune)) {
          runeToIsSolved(newlySolvedRune) = true
          markRuneSolved(plannerState, runeToIsSolved, newlySolvedRune)
        }
      })

      markRuleSolved(plannerState, solvingRule)

      plannerState.sanityCheck(runeToIsSolved)
    }

    (orderedRules.slice(0, numRulesExecuted), runeToIsSolved)

//    // We're just doublechecking here, we could disable all this
//    val complete =
//      plannerState.world.puzzleToRunes.indices.forall({ case puzzleIndex =>
//        val numUnknownRunes = plannerState.puzzleToNumUnknownRunes(puzzleIndex);
//        vassert(numUnknownRunes != 0) // Should have been attempted in the main solver loop
//        if (plannerState.puzzleToNumUnknownRunes(puzzleIndex) == -1) {
//          vassert(plannerState.puzzleToExecuted(puzzleIndex))
//          true
//        } else {
//          // An incomplete solve
//          false
//        }
//      })
//
//    if (complete) {
//      CompletePlan(runeToIsSolved, orderedRules.slice(0, numRulesExecuted))
//    } else {
//      IncompletePlan(runeToIsSolved, orderedRules.slice(0, numRulesExecuted))
//    }
  }

  def markRuneSolved[RuleID, Literal, Lookup](
      plannerState: PlannerState[RuleID, Literal, Lookup],
      runeToIsSolved: Array[Boolean],
      rune: Int) = {
    val world = plannerState.world
    val puzzlesWithNewlySolvedRune = world.runeToPuzzles(rune)

    puzzlesWithNewlySolvedRune.foreach(puzzle => {
      val puzzleRunes = world.puzzleToRunes(puzzle)
      vassert(puzzleRunes.contains(rune))

      val oldNumUnknownRunes = plannerState.puzzleToNumUnknownRunes(puzzle)
      val newNumUnknownRunes = oldNumUnknownRunes - 1
      // == newNumUnknownRunes because we already registered it as a conclusion
      vassert(puzzleRunes.count(!runeToIsSolved(_)) == newNumUnknownRunes)
      plannerState.puzzleToNumUnknownRunes(puzzle) = newNumUnknownRunes

      val puzzleUnknownRunes = plannerState.puzzleToUnknownRunes(puzzle)

      // Should be O(5), no rule has more than 5 unknowns
      val indexOfUnknownRune = puzzleUnknownRunes.indexOf(rune)
      vassert(indexOfUnknownRune >= 0)
      // Swap the last thing into this one's place
      puzzleUnknownRunes(indexOfUnknownRune) = puzzleUnknownRunes(newNumUnknownRunes)
      // This is unnecessary, but might make debugging easier
      puzzleUnknownRunes(newNumUnknownRunes) = -1

      vassert(
        puzzleUnknownRunes.slice(0, newNumUnknownRunes).distinct.sorted sameElements
          puzzleRunes.filter(!runeToIsSolved(_)).distinct.sorted)

      val oldNumUnknownsBucket = plannerState.numUnknownsToPuzzles(oldNumUnknownRunes)

      val oldNumUnknownsBucketOldSize = plannerState.numUnknownsToNumPuzzles(oldNumUnknownRunes)
      vassert(oldNumUnknownsBucketOldSize == oldNumUnknownsBucket.count(_ >= 0))
      val oldNumUnknownsBucketNewSize = oldNumUnknownsBucketOldSize - 1
      plannerState.numUnknownsToNumPuzzles(oldNumUnknownRunes) = oldNumUnknownsBucketNewSize

      val indexOfPuzzleInOldNumUnknownsBucket = plannerState.puzzleToIndexInNumUnknowns(puzzle)
      vassert(indexOfPuzzleInOldNumUnknownsBucket == oldNumUnknownsBucket.indexOf(puzzle))

      // Swap the last thing into this one's place
      val newPuzzleForThisSpotInOldNumUnknownsBucket = oldNumUnknownsBucket(oldNumUnknownsBucketNewSize)
      vassert(plannerState.puzzleToIndexInNumUnknowns(newPuzzleForThisSpotInOldNumUnknownsBucket) == oldNumUnknownsBucketNewSize)
      oldNumUnknownsBucket(indexOfPuzzleInOldNumUnknownsBucket) = newPuzzleForThisSpotInOldNumUnknownsBucket
      plannerState.puzzleToIndexInNumUnknowns(newPuzzleForThisSpotInOldNumUnknownsBucket) = indexOfPuzzleInOldNumUnknownsBucket
      // This is unnecessary, but might make debugging easier
      oldNumUnknownsBucket(oldNumUnknownsBucketNewSize) = -1

      val newNumUnknownsBucketOldSize = plannerState.numUnknownsToNumPuzzles(newNumUnknownRunes)
      val newNumUnknownsBucketNewSize = newNumUnknownsBucketOldSize + 1
      plannerState.numUnknownsToNumPuzzles(newNumUnknownRunes) = newNumUnknownsBucketNewSize

      val newNumUnknownsBucket = plannerState.numUnknownsToPuzzles(newNumUnknownRunes)
      vassert(newNumUnknownsBucket(newNumUnknownsBucketOldSize) == -1)
      val indexOfPuzzleInNewNumUnknownsBucket = newNumUnknownsBucketOldSize
      newNumUnknownsBucket(indexOfPuzzleInNewNumUnknownsBucket) = puzzle

      plannerState.puzzleToIndexInNumUnknowns(puzzle) = indexOfPuzzleInNewNumUnknownsBucket
    })
  }

  def markRuleSolved[RuleID, Literal, Lookup](
    plannerState: PlannerState[RuleID, Literal, Lookup],
    rule: Int) = {
    val puzzlesForRule = plannerState.world.ruleToPuzzles(rule)
    puzzlesForRule.foreach(puzzle => {
      val numUnknowns = plannerState.puzzleToNumUnknownRunes(puzzle)
      vassert(numUnknowns == 0)
      plannerState.puzzleToNumUnknownRunes(puzzle) = -1
      val indexInNumUnknowns = plannerState.puzzleToIndexInNumUnknowns(puzzle)

      val oldNumPuzzlesInNumUnknownsBucket = plannerState.numUnknownsToNumPuzzles(0)
      val lastSlotInNumUnknownsBucket = oldNumPuzzlesInNumUnknownsBucket - 1

      // Swap the last one into this spot
      val newPuzzleForThisSpot = plannerState.numUnknownsToPuzzles(0)(lastSlotInNumUnknownsBucket)
      plannerState.numUnknownsToPuzzles(0)(indexInNumUnknowns) = newPuzzleForThisSpot

      // We just moved something in the numUnknownsToPuzzle, so we have to update that thing's knowledge of
      // where it is in the list.
      plannerState.puzzleToIndexInNumUnknowns(newPuzzleForThisSpot) = indexInNumUnknowns

      // Mark our position as -1
      plannerState.puzzleToIndexInNumUnknowns(puzzle) = -1

      // Clear the last slot to -1
      plannerState.numUnknownsToPuzzles(0)(lastSlotInNumUnknownsBucket) = -1

      // Reduce the number of puzzles in that bucket by 1
      val newNumPuzzlesInNumUnknownsBucket = oldNumPuzzlesInNumUnknownsBucket - 1
      plannerState.numUnknownsToNumPuzzles(0) = newNumPuzzlesInNumUnknownsBucket
    })
  }
}
