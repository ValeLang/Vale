package net.verdagon.vale.solver

import net.verdagon.vale.{Err, Ok, Result, vassert, vcurious, vfail, vimpl}

sealed trait ISolverOutcome[Conclusion, ErrType] {
  def getOrDie(): Array[Option[Conclusion]]
}
case class CompleteSolve[Conclusion, ErrType](
  conclusions: Array[Option[Conclusion]]
) extends ISolverOutcome[Conclusion, ErrType] {
  override def getOrDie(): Array[Option[Conclusion]] = conclusions
}
case class IncompleteSolve[Conclusion, ErrType](
  conclusions: Array[Option[Conclusion]]
) extends ISolverOutcome[Conclusion, ErrType] {
  override def getOrDie(): Array[Option[Conclusion]] = vfail()
}

case class FailedSolve[Conclusion, ErrType](
  error: ISolverError[Conclusion, ErrType],
  conclusions: Array[Option[Conclusion]]
) extends ISolverOutcome[Conclusion, ErrType] {
  override def getOrDie(): Array[Option[Conclusion]] = vfail()
}

sealed trait ISolverError[Conclusion, ErrType]
case class SolverConflict[Conclusion, ErrType](
  rule: Int,
  rune: Int,
  previousConclusion: Conclusion,
  newConclusion: Conclusion
) extends ISolverError[Conclusion, ErrType]
case class RuleError[Conclusion, ErrType](
  rule: Int,
  err: ErrType
) extends ISolverError[Conclusion, ErrType]

trait ISolverDelegate[RuleID, Literal, Lookup, Env, State, Conclusion, ErrType] {
  def solve(
    state: State,
    env: Env,
    range: RuleID,
    rule: IRulexAR[Int, RuleID, Literal, Lookup],
    runes: Map[Int, Conclusion]
  ): Result[Map[Int, Conclusion], ErrType]
}

// Given enough user specified template params and param inputs, we should be able to
// infer everything.
// This class's purpose is to take those things, and see if it can figure out as many
// inferences as possible.

class Solver[RuleID, Literal, Lookup, Env, State, Conclusion, ErrType](
  delegate: ISolverDelegate[RuleID, Literal, Lookup, Env, State, Conclusion, ErrType]) {

  def solve(
    state: State,
    env: Env,
    originalSolverState: RuneWorldSolverState[RuleID, Literal, Lookup],
    invocationRange: RuleID
  ): ISolverOutcome[Conclusion, ErrType] = {
    val runeWorld = originalSolverState.runeWorld
    val solverState = originalSolverState.deepClone()

    val conclusions: Array[Option[Conclusion]] = runeWorld.runeToPuzzles.map(_ => None)

    solverState.sanityCheck(conclusions)

    while (solverState.numUnknownsToNumPuzzles(0) > 0) {
      vassert(solverState.numUnknownsToPuzzles(0)(0) >= 0)

      val numSolvableRules = solverState.numUnknownsToNumPuzzles(0)

      val solvingPuzzle = solverState.numUnknownsToPuzzles(0)(numSolvableRules - 1)
      vassert(solvingPuzzle >= 0)
      vassert(solverState.puzzleToIndexInNumUnknowns(solvingPuzzle) == numSolvableRules - 1)


      val solvedPuzzleRunes = runeWorld.puzzleToRunes(solvingPuzzle)
      val puzzleRuneToTemplata =
        solvedPuzzleRunes.map(rune => {
          (rune -> conclusions(rune).get)
        }).toMap

      val solvingRule = runeWorld.puzzleToRule(solvingPuzzle)

      runeWorld.ruleToPuzzles(solvingRule).foreach(rulePuzzle => {
        vassert(!solverState.puzzleToExecuted(solvingPuzzle))
      })
      val newlySolvedRuneToConclusion =
        delegate.solve(state, env, invocationRange, runeWorld.rules(solvingRule), puzzleRuneToTemplata) match {
          case Ok(x) => x
          case Err(e) => return FailedSolve(RuleError(solvingRule, e), conclusions)
        }
      runeWorld.ruleToPuzzles(solvingRule).foreach(rulePuzzle => {
        solverState.puzzleToExecuted(rulePuzzle) = true
      })

      newlySolvedRuneToConclusion.foreach({ case (newlySolvedRune, newConclusion) =>
        conclusions(newlySolvedRune) match {
          case None => conclusions(newlySolvedRune) = Some(newConclusion)
          case Some(existingConclusion) => {
            if (existingConclusion != newConclusion) {
              return FailedSolve(SolverConflict(solvingRule, newlySolvedRune, existingConclusion, newConclusion), conclusions)
            }
          }
        }

        markRuneSolved(solverState, conclusions, newlySolvedRune)
      })

      markRuleSolved(solverState, solvingRule)

//      runeWorld.ruleToPuzzles(solvingRule).foreach(puzzleForThisSolvingRule => {
//        markPuzzleSolved(solverState, puzzleForThisSolvingRule)
//      })

      solverState.sanityCheck(conclusions)
    }

    // We're just doublechecking here, we could disable all this
    val complete =
      solverState.runeWorld.puzzleToRunes.indices.forall({ case puzzleIndex =>
        val numUnknownRunes = solverState.puzzleToNumUnknownRunes(puzzleIndex);
        vassert(numUnknownRunes != 0) // Should have been attempted in the main solver loop
        if (solverState.puzzleToNumUnknownRunes(puzzleIndex) == -1) {
          vassert(solverState.puzzleToExecuted(puzzleIndex))
          true
        } else {
          // An incomplete solve
          false
        }

  //      if (!solverState.puzzleToExecuted(puzzleIndex)) {
  //        val puzzleRunes = runeWorld.puzzleToRunes(puzzleIndex)
  //        val puzzleRuneToTemplata =
  //          puzzleRunes.map(rune => {
  //            (rune -> conclusions(rune).get)
  //          }).toMap
  //        val ruleIndex = runeWorld.puzzleToRule(puzzleIndex)
  //        val rule = runeWorld.rules(ruleIndex)
  //        delegate.solve(state, env, invocationRange, rule, puzzleRuneToTemplata) match {
  //          case Ok(x) =>
  //          case Err(e) => return Err(RuleError(ruleIndex, e))
  //        }
  //        solverState.puzzleToExecuted(puzzleIndex) = true
  //      }
      })

    if (complete) {
      CompleteSolve(conclusions)
    } else {
      IncompleteSolve(conclusions)
    }
  }

  def markRuneSolved(
      solverState: RuneWorldSolverState[RuleID, Literal, Lookup],
      conclusions: Array[Option[Conclusion]],
      rune: Int) = {
    val runeWorld = solverState.runeWorld
    val puzzlesWithNewlySolvedRune = runeWorld.runeToPuzzles(rune)

    puzzlesWithNewlySolvedRune.foreach(puzzle => {
      val puzzleRunes = runeWorld.puzzleToRunes(puzzle)
      vassert(puzzleRunes.contains(rune))

      val oldNumUnknownRunes = solverState.puzzleToNumUnknownRunes(puzzle)
      val newNumUnknownRunes = oldNumUnknownRunes - 1
      // == newNumUnknownRunes because we already registered it as a conclusion
      vassert(puzzleRunes.count(conclusions(_).isEmpty) == newNumUnknownRunes)
      solverState.puzzleToNumUnknownRunes(puzzle) = newNumUnknownRunes

      val puzzleUnknownRunes = solverState.puzzleToUnknownRunes(puzzle)

      // Should be O(5), no rule has more than 5 unknowns
      val indexOfUnknownRune = puzzleUnknownRunes.indexOf(rune)
      vassert(indexOfUnknownRune >= 0)
      // Swap the last thing into this one's place
      puzzleUnknownRunes(indexOfUnknownRune) = puzzleUnknownRunes(newNumUnknownRunes)
      // This is unnecessary, but might make debugging easier
      puzzleUnknownRunes(newNumUnknownRunes) = -1

      vassert(
        puzzleUnknownRunes.slice(0, newNumUnknownRunes).distinct.sorted sameElements
          puzzleRunes.filter(conclusions(_).isEmpty).distinct.sorted)

      val oldNumUnknownsBucket = solverState.numUnknownsToPuzzles(oldNumUnknownRunes)

      val oldNumUnknownsBucketOldSize = solverState.numUnknownsToNumPuzzles(oldNumUnknownRunes)
      vassert(oldNumUnknownsBucketOldSize == oldNumUnknownsBucket.count(_ >= 0))
      val oldNumUnknownsBucketNewSize = oldNumUnknownsBucketOldSize - 1
      solverState.numUnknownsToNumPuzzles(oldNumUnknownRunes) = oldNumUnknownsBucketNewSize

      val indexOfPuzzleInOldNumUnknownsBucket = solverState.puzzleToIndexInNumUnknowns(puzzle)
      vassert(indexOfPuzzleInOldNumUnknownsBucket == oldNumUnknownsBucket.indexOf(puzzle))

      // Swap the last thing into this one's place
      val newPuzzleForThisSpotInOldNumUnknownsBucket = oldNumUnknownsBucket(oldNumUnknownsBucketNewSize)
      vassert(solverState.puzzleToIndexInNumUnknowns(newPuzzleForThisSpotInOldNumUnknownsBucket) == oldNumUnknownsBucketNewSize)
      oldNumUnknownsBucket(indexOfPuzzleInOldNumUnknownsBucket) = newPuzzleForThisSpotInOldNumUnknownsBucket
      solverState.puzzleToIndexInNumUnknowns(newPuzzleForThisSpotInOldNumUnknownsBucket) = indexOfPuzzleInOldNumUnknownsBucket
      // This is unnecessary, but might make debugging easier
      oldNumUnknownsBucket(oldNumUnknownsBucketNewSize) = -1

      val newNumUnknownsBucketOldSize = solverState.numUnknownsToNumPuzzles(newNumUnknownRunes)
      val newNumUnknownsBucketNewSize = newNumUnknownsBucketOldSize + 1
      solverState.numUnknownsToNumPuzzles(newNumUnknownRunes) = newNumUnknownsBucketNewSize

      val newNumUnknownsBucket = solverState.numUnknownsToPuzzles(newNumUnknownRunes)
      vassert(newNumUnknownsBucket(newNumUnknownsBucketOldSize) == -1)
      val indexOfPuzzleInNewNumUnknownsBucket = newNumUnknownsBucketOldSize
      newNumUnknownsBucket(indexOfPuzzleInNewNumUnknownsBucket) = puzzle

      solverState.puzzleToIndexInNumUnknowns(puzzle) = indexOfPuzzleInNewNumUnknownsBucket
    })
  }

  def markRuleSolved(
    solverState: RuneWorldSolverState[RuleID, Literal, Lookup],
    rule: Int) = {
    val puzzlesForRule = solverState.runeWorld.ruleToPuzzles(rule)
    puzzlesForRule.foreach(puzzle => {
      val numUnknowns = solverState.puzzleToNumUnknownRunes(puzzle)
      vassert(numUnknowns == 0)
      solverState.puzzleToNumUnknownRunes(puzzle) = -1
      val indexInNumUnknowns = solverState.puzzleToIndexInNumUnknowns(puzzle)

      val oldNumPuzzlesInNumUnknownsBucket = solverState.numUnknownsToNumPuzzles(0)
      val lastSlotInNumUnknownsBucket = oldNumPuzzlesInNumUnknownsBucket - 1

      // Swap the last one into this spot
      val newPuzzleForThisSpot = solverState.numUnknownsToPuzzles(0)(lastSlotInNumUnknownsBucket)
      solverState.numUnknownsToPuzzles(0)(indexInNumUnknowns) = newPuzzleForThisSpot

      // We just moved something in the numUnknownsToPuzzle, so we have to update that thing's knowledge of
      // where it is in the list.
      solverState.puzzleToIndexInNumUnknowns(newPuzzleForThisSpot) = indexInNumUnknowns

      // Mark our position as -1
      solverState.puzzleToIndexInNumUnknowns(puzzle) = -1

      // Clear the last slot to -1
      solverState.numUnknownsToPuzzles(0)(lastSlotInNumUnknownsBucket) = -1

      // Reduce the number of puzzles in that bucket by 1
      val newNumPuzzlesInNumUnknownsBucket = oldNumPuzzlesInNumUnknownsBucket - 1
      solverState.numUnknownsToNumPuzzles(0) = newNumPuzzlesInNumUnknownsBucket
    })
  }
}
