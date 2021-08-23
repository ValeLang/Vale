package net.verdagon.vale.solver

import net.verdagon.vale.{Err, Ok, Result, vassert, vcurious, vfail, vimpl}

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
  ): Result[Array[Option[Conclusion]], ISolverError[Conclusion, ErrType]] = {
    val runeWorld = originalSolverState.runeWorld
    val solverState = originalSolverState.deepClone()

    val conclusions: Array[Option[Conclusion]] = runeWorld.runeToPuzzles.map(_ => None)

    while (solverState.numUnknownsToNumPuzzles(0) > 0) {
      vassert(solverState.numUnknownsToPuzzle(0)(0) >= 0)

      val numSolvableRules = solverState.numUnknownsToNumPuzzles(0)

      val solvingPuzzle = solverState.numUnknownsToPuzzle(0)(numSolvableRules - 1)
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
          case Err(e) => return Err(RuleError(solvingRule, e))
        }
      runeWorld.ruleToPuzzles(solvingRule).foreach(rulePuzzle => {
        solverState.puzzleToExecuted(rulePuzzle) = true
      })

      newlySolvedRuneToConclusion.foreach({ case (newlySolvedRune, newConclusion) =>
        conclusions(newlySolvedRune) match {
          case None => conclusions(newlySolvedRune) = Some(newConclusion)
          case Some(existingConclusion) => {
            if (existingConclusion != newConclusion) {
              return Err(SolverConflict(solvingRule, newlySolvedRune, existingConclusion, newConclusion))
            }
          }
        }

        markRuneSolved(solverState, conclusions, newlySolvedRune)
      })

      markRuleSolved(solverState, solvingRule)

//      runeWorld.ruleToPuzzles(solvingRule).foreach(puzzleForThisSolvingRule => {
//        markPuzzleSolved(solverState, puzzleForThisSolvingRule)
//      })
    }

    // We're just doublechecking here, we could disable all this
    solverState.runeWorld.puzzleToRunes.indices.foreach({ case puzzleIndex =>
      vcurious(solverState.puzzleToExecuted(puzzleIndex))

      if (!solverState.puzzleToExecuted(puzzleIndex)) {
        val puzzleRunes = runeWorld.puzzleToRunes(puzzleIndex)
        val puzzleRuneToTemplata =
          puzzleRunes.map(rune => {
            (rune -> conclusions(rune).get)
          }).toMap
        val ruleIndex = runeWorld.puzzleToRule(puzzleIndex)
        val rule = runeWorld.rules(ruleIndex)
        delegate.solve(state, env, invocationRange, rule, puzzleRuneToTemplata) match {
          case Ok(x) =>
          case Err(e) => return Err(RuleError(ruleIndex, e))
        }
        solverState.puzzleToExecuted(puzzleIndex) = true
      }
    })

    Ok(conclusions)
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

      val oldNumUnknownsBucket = solverState.numUnknownsToPuzzle(oldNumUnknownRunes)

      val oldNumUnknownsBucketOldSize = solverState.numUnknownsToNumPuzzles(oldNumUnknownRunes)
      vassert(oldNumUnknownsBucketOldSize == oldNumUnknownsBucket.count(_ >= 0))
      val oldNumUnknownsBucketNewSize = oldNumUnknownsBucketOldSize - 1
      solverState.numUnknownsToNumPuzzles(oldNumUnknownRunes) = oldNumUnknownsBucketNewSize

      val indexOfPuzzleInOldNumUnknownsBucket = solverState.puzzleToIndexInNumUnknowns(puzzle)
      vassert(indexOfPuzzleInOldNumUnknownsBucket == oldNumUnknownsBucket.indexOf(puzzle))

      // Swap the last thing into this one's place
      oldNumUnknownsBucket(indexOfPuzzleInOldNumUnknownsBucket) = oldNumUnknownsBucket(oldNumUnknownsBucketNewSize)
      // This is unnecessary, but might make debugging easier
      oldNumUnknownsBucket(oldNumUnknownsBucketNewSize) = -1

      val newNumUnknownsBucketOldSize = solverState.numUnknownsToNumPuzzles(newNumUnknownRunes)
      val newNumUnknownsBucketNewSize = newNumUnknownsBucketOldSize + 1
      solverState.numUnknownsToNumPuzzles(newNumUnknownRunes) = newNumUnknownsBucketNewSize

      val newNumUnknownsBucket = solverState.numUnknownsToPuzzle(newNumUnknownRunes)
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
      solverState.puzzleToIndexInNumUnknowns(puzzle) = -1

      val oldNumPuzzlesInNumUnknownsBucket = solverState.numUnknownsToNumPuzzles(0)
      val lastSlotInNumUnknownsBucket = oldNumPuzzlesInNumUnknownsBucket - 1
      // Swap the last one into this spot
      val newPuzzleForThisSpot = solverState.numUnknownsToPuzzle(0)(lastSlotInNumUnknownsBucket)
      solverState.numUnknownsToPuzzle(0)(indexInNumUnknowns) = newPuzzleForThisSpot
      // Clear the last slot to -1
      solverState.numUnknownsToPuzzle(0)(lastSlotInNumUnknownsBucket) = -1

      // We just moved something in the numUnknownsToPuzzle, so we have to update that thing's knowledge of
      // where it is in the list.
      solverState.puzzleToIndexInNumUnknowns(newPuzzleForThisSpot) = indexInNumUnknowns

      // Reduce the number of puzzles in that bucket by 1
      val newNumPuzzlesInNumUnknownsBucket = oldNumPuzzlesInNumUnknownsBucket - 1
      solverState.numUnknownsToNumPuzzles(0) = newNumPuzzlesInNumUnknownsBucket
    })
  }
}
