package net.verdagon.vale.solver

import net.verdagon.vale.{Err, Ok, Result, vassert, vfail, vimpl}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object OptimizedSolverState {
  def apply[Rule, RuneID, Conclusion](): OptimizedSolverState[Rule, RuneID, Conclusion] = {
    OptimizedSolverState[Rule, RuneID, Conclusion](
      mutable.HashMap[RuneID, Int](),
      mutable.ArrayBuffer[Rule](),
      mutable.ArrayBuffer[Array[Int]](),
      mutable.ArrayBuffer[Int](),
      mutable.ArrayBuffer[Array[Int]](),
      mutable.ArrayBuffer[mutable.ArrayBuffer[Int]](),
      mutable.ArrayBuffer[mutable.ArrayBuffer[Int]](),
      mutable.ArrayBuffer[Int](),
      mutable.ArrayBuffer[Boolean](),
      mutable.ArrayBuffer[Int](),
      mutable.ArrayBuffer[Array[Int]](),
      mutable.ArrayBuffer[Int](),
      0.to(4).map(_ => 0).toArray,
      0.to(4).map(_ => mutable.ArrayBuffer[Int]()).toArray,
      mutable.ArrayBuffer[Option[Conclusion]]())
  }
}

case class OptimizedSolverState[Rule, RuneID, Conclusion](
  private val userRuneToCanonicalRune: mutable.HashMap[RuneID, Int],

  private val rules: mutable.ArrayBuffer[Rule],

  // For each rule, what are all the runes involved in it
  private val ruleToRunes: mutable.ArrayBuffer[Array[Int]],

  // For example, if rule 7 says:
  //   1 = Ref(2, 3, 4, 5)
  // then 2, 3, 4, 5 together could solve the rule, or 1 could solve the rule.
  // In other words, the two sets of runes that could solve the rule are:
  // - [1]
  // - [2, 3, 4, 5]
  // Here we have two "puzzles". The runes in a puzzle are called "pieces".
  // Puzzles are identified up-front by Astronomer.

  // This tracks, for each puzzle, what rule does it refer to
  private val puzzleToRule: mutable.ArrayBuffer[Int],
  // This tracks, for each puzzle, what rules does it have
  private val puzzleToRunes: mutable.ArrayBuffer[Array[Int]],

  // For every rule, this is which puzzles can solve it.
  private val ruleToPuzzles: mutable.ArrayBuffer[mutable.ArrayBuffer[Int]],

  // For every rune, this is which puzzles it participates in.
  private val runeToPuzzles: mutable.ArrayBuffer[mutable.ArrayBuffer[Int]],

  // Rules that we don't need to execute (e.g. Equals rules)
  private val noopRules: mutable.ArrayBuffer[Int],

  // For each rule, whether it's been actually executed or not
  private val puzzleToExecuted: mutable.ArrayBuffer[Boolean],

  // Together, these basically form a Array[mutable.ArrayBuffer[Int]]
  private val puzzleToNumUnknownRunes: mutable.ArrayBuffer[Int],
  private val puzzleToUnknownRunes: mutable.ArrayBuffer[Array[Int]],
  // This is the puzzle's index in the below numUnknownsToPuzzle map.
  private val puzzleToIndexInNumUnknowns: mutable.ArrayBuffer[Int],

  // Together, these basically form a mutable.ArrayBuffer[mutable.ArrayBuffer[Int]]
  // which will have five elements: 0, 1, 2, 3, 4
  // At slot 4 is all the puzzles that have 4 unknowns left
  // At slot 3 is all the puzzles that have 3 unknowns left
  // At slot 2 is all the puzzles that have 2 unknowns left
  // At slot 1 is all the puzzles that have 1 unknowns left
  // At slot 0 is all the puzzles that have 0 unknowns left
  // We will:
  // - Move a puzzle from one set to the next set if we solve one of its runes
  // - Solve any puzzle that has 0 unknowns left
  private val numUnknownsToNumPuzzles: Array[Int],
  private val numUnknownsToPuzzles: Array[mutable.ArrayBuffer[Int]],

  // For each rune, whether it's solved already
  private val runeToConclusion: mutable.ArrayBuffer[Option[Conclusion]]
) extends ISolverState[Rule, RuneID, Conclusion] {

  override def hashCode(): Int = vfail() // is mutable, should never be hashed

  override def deepClone(): OptimizedSolverState[Rule, RuneID, Conclusion] = {
    OptimizedSolverState[Rule, RuneID, Conclusion](
      userRuneToCanonicalRune.clone(),
      rules.clone(),
      ruleToRunes.map(_.clone()).clone(),
      puzzleToRule.clone(),
      puzzleToRunes.map(_.clone()).clone(),
      ruleToPuzzles.map(_.clone()).clone(),
      runeToPuzzles.map(_.clone()).clone(),
      noopRules.clone(),
      puzzleToExecuted.clone(),
      puzzleToNumUnknownRunes.clone(),
      puzzleToUnknownRunes.map(_.clone()).clone(),
      puzzleToIndexInNumUnknowns.clone(),
      numUnknownsToNumPuzzles.clone(),
      numUnknownsToPuzzles.map(_.clone()).clone(),
      runeToConclusion.clone())
  }

  override def getAllRunes(): Set[Int] = {
    vimpl()
//    userRuneToCanonicalRune.keySet.toSet
  }

  override def complexStep[ErrType](ruleToPuzzles: Rule => Array[Array[RuneID]], step: IStepState[Rule, RuneID, Conclusion] => Result[Unit, ISolverError[RuneID, Conclusion, ErrType]]): Result[Step[Rule, RuneID, Conclusion], ISolverError[RuneID, Conclusion, ErrType]] = vimpl()

  override def getSteps(): Vector[Step[Rule, RuneID, Conclusion]] = vimpl()

  override def simpleStep[ErrType](ruleToPuzzles: Rule => Array[Array[RuneID]], ruleIndex: Int, rule: Rule, step: IStepState[Rule, RuneID, Conclusion] => Result[Unit, ISolverError[RuneID, Conclusion, ErrType]]): Result[Step[Rule, RuneID, Conclusion], ISolverError[RuneID, Conclusion, ErrType]] = vimpl()

  override def initialStep[ErrType](ruleToPuzzles: Rule => Array[Array[RuneID]], step: IStepState[Rule, RuneID, Conclusion] => Result[Unit, ISolverError[RuneID, Conclusion, ErrType]]): Result[Step[Rule, RuneID, Conclusion], ISolverError[RuneID, Conclusion, ErrType]] = vimpl()

  override def getCanonicalRune(rune: RuneID): Int = {
    userRuneToCanonicalRune.get(rune) match {
      case Some(s) => s
      case None => {
        val canonicalRune = userRuneToCanonicalRune.size
        userRuneToCanonicalRune += (rune -> canonicalRune)
        vassert(canonicalRune == runeToPuzzles.size)
        runeToPuzzles += mutable.ArrayBuffer()
        runeToConclusion += None
        sanityCheck()
        canonicalRune
      }
    }
  }

  override def getRule(ruleIndex: Int): Rule = {
    rules(ruleIndex)
  }

  private def addRule(rule: Rule, runes: Array[Int]): Int = {
    vassert(runes sameElements runes.distinct)

    val ruleIndex = rules.size
    rules += rule
    assert(ruleIndex == ruleToRunes.size)
    ruleToRunes += runes
    assert(ruleIndex == ruleToPuzzles.size)
    ruleToPuzzles += mutable.ArrayBuffer()
    sanityCheck()
    ruleIndex
  }

  private def addPuzzle(ruleIndex: Int): Int = {
    val runes: Array[Int] = vimpl()
    vassert(runes sameElements runes.distinct)

    val puzzleIndex = puzzleToRule.size
    assert(puzzleIndex == puzzleToRunes.size)
    ruleToPuzzles(ruleIndex) += puzzleIndex
    puzzleToRule += ruleIndex
    puzzleToRunes += runes
    runes.foreach(rune => {
      runeToPuzzles(rune) += puzzleIndex
      vassert(ruleToRunes(ruleIndex).contains(rune))
    })

    assert(puzzleIndex == puzzleToExecuted.size)
    puzzleToExecuted += false

    val unknownRunes = runes.filter(runeToConclusion(_).isEmpty)
    assert(puzzleIndex == puzzleToUnknownRunes.size)
    puzzleToUnknownRunes += unknownRunes

    val numUnknowns = unknownRunes.length
    assert(puzzleIndex == puzzleToNumUnknownRunes.size)
    puzzleToNumUnknownRunes += numUnknowns

    vassert(numUnknowns < numUnknownsToNumPuzzles.length)
    val indexInNumUnknownsBucket = numUnknownsToNumPuzzles(numUnknowns)
    numUnknownsToNumPuzzles(numUnknowns) += 1

    // Every entry in this table should have enough room for all rules to be in there at the same time
    numUnknownsToPuzzles.zipWithIndex.foreach({ case (puzzles, numUnknowns) =>
      puzzles += -1
      vassert(puzzles.length == puzzleToRule.length)
    })
    // And now put our new puzzle into a -1 slot.
    vassert(numUnknownsToPuzzles(numUnknowns)(indexInNumUnknownsBucket) == -1)
    numUnknownsToPuzzles(numUnknowns)(indexInNumUnknownsBucket) = puzzleIndex
    assert(puzzleIndex == puzzleToIndexInNumUnknowns.size)
    puzzleToIndexInNumUnknowns += indexInNumUnknownsBucket

    sanityCheck()
    puzzleIndex
  }

  private def hasNextSolvable(): Boolean = {
    numUnknownsToNumPuzzles(0) > 0
  }

  override def getUserRune(rune: Int): RuneID = vimpl()

  override def getNextSolvable(): Option[Int] = {
    if (numUnknownsToPuzzles(0)(0) == 0) {
      return None
    }

    val numSolvableRules = numUnknownsToNumPuzzles(0)

    val solvingPuzzle = numUnknownsToPuzzles(0)(numSolvableRules - 1)
    vassert(solvingPuzzle >= 0)
    vassert(puzzleToIndexInNumUnknowns(solvingPuzzle) == numSolvableRules - 1)

    val solvingRule = puzzleToRule(solvingPuzzle)
//    val ruleRunes = ruleToRunes(solvingRule)

    ruleToPuzzles(solvingRule).foreach(rulePuzzle => {
      vassert(!puzzleToExecuted(rulePuzzle))
    })

    Some(solvingRule)
  }

  override def getConclusion(rune: RuneID): Option[Conclusion] = {
    runeToConclusion(getCanonicalRune(rune))
  }

  override def addRune(rune: RuneID): Int = vimpl()

  override def addRule(rule: Rule): Int = vimpl()

  override def getConclusions(): Stream[(Int, Conclusion)] = vimpl()

  override def getAllRules(): Vector[Rule] = vimpl()

  override def addPuzzle(ruleIndex: Int, runes: Array[Int]): Unit = vimpl()

  override def markRulesSolved[ErrType](ruleIndices: Array[Int], newConclusions: Map[Int, Conclusion]):
  Result[Int, ISolverError[RuneID, Conclusion, ErrType]] = {
    val numNewConclusions =
      newConclusions.map({ case (newlySolvedCanonicalRune, newConclusion) =>
        runeToConclusion(newlySolvedCanonicalRune) match {
          case None => {
            vimpl()//concludeRune(newlySolvedCanonicalRune, newConclusion)
            1
          }
          case Some(existingConclusion) => {
            if (existingConclusion != newConclusion) {
              return Err(
                SolverConflict(
                  vimpl(),//newlySolvedCanonicalRune,
                  existingConclusion,
                  newConclusion))
            }
            0
          }
        }
      }).sum

    ruleIndices.foreach(ruleIndex => {
      removeRule(ruleIndex)
    })

    Ok(numNewConclusions)
  }

  override def userifyConclusions(): Stream[(RuneID, Conclusion)] = {
    userRuneToCanonicalRune.toStream.flatMap({ case (userRune, canonicalRune) =>
      runeToConclusion(canonicalRune).map(userRune -> _)
    })
  }

  override def getUnsolvedRules(): Vector[Rule] = {
    puzzleToNumUnknownRunes
      .zipWithIndex
      .filter(_._1 > 0)
      .map(_._2)
      .map(puzzleToRule)
      .distinct
      .sorted
      .map(rules)
      .toVector
  }

  private def concludeRune(newlySolvedRune: Int, conclusion: Conclusion): Unit = {
    runeToConclusion(newlySolvedRune) match {
      case Some(x) => {
        vassert(x == conclusion)
        return
      }
      case None =>
    }
    runeToConclusion(newlySolvedRune) = Some(conclusion)

    val puzzlesWithNewlySolvedRune = runeToPuzzles(newlySolvedRune)

    puzzlesWithNewlySolvedRune.foreach(puzzle => {
      val puzzleRunes = puzzleToRunes(puzzle)
      vassert(puzzleRunes.contains(newlySolvedRune))

      val oldNumUnknownRunes = puzzleToNumUnknownRunes(puzzle)
      val newNumUnknownRunes = oldNumUnknownRunes - 1
      // == newNumUnknownRunes because we already registered it as a conclusion
      vassert(puzzleRunes.count(runeToConclusion(_).isEmpty) == newNumUnknownRunes)
      puzzleToNumUnknownRunes(puzzle) = newNumUnknownRunes

      val puzzleUnknownRunes = puzzleToUnknownRunes(puzzle)

      // Should be O(5), no rule has more than 5 unknowns
      val indexOfNewlySolvedRune = puzzleUnknownRunes.indexOf(newlySolvedRune)
      vassert(indexOfNewlySolvedRune >= 0)
      // Swap the last thing into this one's place
      puzzleUnknownRunes(indexOfNewlySolvedRune) = puzzleUnknownRunes(newNumUnknownRunes)
      // This is unnecessary, but might make debugging easier
      puzzleUnknownRunes(newNumUnknownRunes) = -1

      vassert(
        puzzleUnknownRunes.slice(0, newNumUnknownRunes).distinct.sorted sameElements
          puzzleRunes.filter(runeToConclusion(_).isEmpty).distinct.sorted)

      val oldNumUnknownsBucket = numUnknownsToPuzzles(oldNumUnknownRunes)

      val oldNumUnknownsBucketOldSize = numUnknownsToNumPuzzles(oldNumUnknownRunes)
      vassert(oldNumUnknownsBucketOldSize == oldNumUnknownsBucket.count(_ >= 0))
      val oldNumUnknownsBucketNewSize = oldNumUnknownsBucketOldSize - 1
      numUnknownsToNumPuzzles(oldNumUnknownRunes) = oldNumUnknownsBucketNewSize

      val indexOfPuzzleInOldNumUnknownsBucket = puzzleToIndexInNumUnknowns(puzzle)
      vassert(indexOfPuzzleInOldNumUnknownsBucket == oldNumUnknownsBucket.indexOf(puzzle))

      // Swap the last thing into this one's place
      val newPuzzleForThisSpotInOldNumUnknownsBucket = oldNumUnknownsBucket(oldNumUnknownsBucketNewSize)
      vassert(puzzleToIndexInNumUnknowns(newPuzzleForThisSpotInOldNumUnknownsBucket) == oldNumUnknownsBucketNewSize)
      oldNumUnknownsBucket(indexOfPuzzleInOldNumUnknownsBucket) = newPuzzleForThisSpotInOldNumUnknownsBucket
      puzzleToIndexInNumUnknowns(newPuzzleForThisSpotInOldNumUnknownsBucket) = indexOfPuzzleInOldNumUnknownsBucket
      // This is unnecessary, but might make debugging easier
      oldNumUnknownsBucket(oldNumUnknownsBucketNewSize) = -1

      val newNumUnknownsBucketOldSize = numUnknownsToNumPuzzles(newNumUnknownRunes)
      val newNumUnknownsBucketNewSize = newNumUnknownsBucketOldSize + 1
      numUnknownsToNumPuzzles(newNumUnknownRunes) = newNumUnknownsBucketNewSize

      val newNumUnknownsBucket = numUnknownsToPuzzles(newNumUnknownRunes)
      vassert(newNumUnknownsBucket(newNumUnknownsBucketOldSize) == -1)
      val indexOfPuzzleInNewNumUnknownsBucket = newNumUnknownsBucketOldSize
      newNumUnknownsBucket(indexOfPuzzleInNewNumUnknownsBucket) = puzzle

      puzzleToIndexInNumUnknowns(puzzle) = indexOfPuzzleInNewNumUnknownsBucket
    })
  }

  private def removeRule(ruleIndex: Int) = {
    // Here we used to check that the rule's runes were solved, but we don't do that anymore
    // because some rules leave their runes as mysteries, see SAIRFU.
    //val ruleRunes = ruleToRunes(ruleIndex)
    //ruleRunes.foreach(canonicalRune => {
    //  vassert(getConclusion(canonicalRune).nonEmpty, "Didn't conclude a rune!")
    //})

    ruleToPuzzles(ruleIndex).foreach(rulePuzzle => {
      puzzleToExecuted(rulePuzzle) = true
    })

    val puzzlesForRule = ruleToPuzzles(ruleIndex)
    puzzlesForRule.foreach(puzzle => {
      removePuzzle(puzzle)
    })
  }

  private def removePuzzle(puzzle: Int) = {
    // Here we used to check that the rule's runes were solved, but we don't do that anymore
    // because some rules leave their runes as mysteries, see SAIRFU.
    //val numUnknowns = puzzleToNumUnknownRunes(puzzle)
    //vassert(numUnknowns == 0)

    puzzleToNumUnknownRunes(puzzle) = -1
    val indexInNumUnknowns = puzzleToIndexInNumUnknowns(puzzle)

    val oldNumPuzzlesInNumUnknownsBucket = numUnknownsToNumPuzzles(0)
    val lastSlotInNumUnknownsBucket = oldNumPuzzlesInNumUnknownsBucket - 1

    // Swap the last one into this spot
    val newPuzzleForThisSpot = numUnknownsToPuzzles(0)(lastSlotInNumUnknownsBucket)
    numUnknownsToPuzzles(0)(indexInNumUnknowns) = newPuzzleForThisSpot

    // We just moved something in the numUnknownsToPuzzle, so we have to update that thing's knowledge of
    // where it is in the list.
    puzzleToIndexInNumUnknowns(newPuzzleForThisSpot) = indexInNumUnknowns

    // Mark our position as -1
    puzzleToIndexInNumUnknowns(puzzle) = -1

    // Clear the last slot to -1
    numUnknownsToPuzzles(0)(lastSlotInNumUnknownsBucket) = -1

    // Reduce the number of puzzles in that bucket by 1
    val newNumPuzzlesInNumUnknownsBucket = oldNumPuzzlesInNumUnknownsBucket - 1
    numUnknownsToNumPuzzles(0) = newNumPuzzlesInNumUnknownsBucket
  }

  override def sanityCheck() = {
    puzzleToRunes.foreach(runes => vassert(runes.distinct sameElements runes))
    runeToPuzzles.foreach(puzzles => vassert(puzzles.distinct sameElements puzzles))
    ruleToRunes.foreach(runes => vassert(runes.distinct sameElements runes))
    ruleToPuzzles.foreach(puzzles => vassert(puzzles.distinct sameElements puzzles))

    ruleToPuzzles.zipWithIndex.map({ case (puzzleIndices, ruleIndex) =>
      vassert(puzzleIndices.distinct == puzzleIndices)
      puzzleIndices.map(puzzleIndex => {
        assert(puzzleToRule(puzzleIndex) == ruleIndex)

        puzzleToRunes(puzzleIndex).map(rune => {
          assert(runeToPuzzles(rune).contains(puzzleIndex))
          assert(ruleToRunes(ruleIndex).contains(rune))
        })
      })
    })

    puzzleToExecuted.zipWithIndex.foreach({ case (executed, puzzle) =>
      if (executed) {
        vassert(puzzleToIndexInNumUnknowns(puzzle) == -1)
        vassert(puzzleToNumUnknownRunes(puzzle) == -1)
        // Here we used to check that the puzzle's runes were solved, but we don't do that anymore
        // because some rules leave their runes as mysteries, see SAIRFU.
        //puzzleToRunes(puzzle).foreach(rune => vassert(runeToConclusion(rune).nonEmpty))
        //puzzleToUnknownRunes(puzzle).foreach(unknownRune => vassert(unknownRune == -1))
        numUnknownsToPuzzles.foreach(_.foreach(p => vassert(p != puzzle)))
      } else {
        // An un-executed puzzle might have all known runes. It just means that it hasn't been
        // executed yet, it'll probably be executed very soon.

        vassert(puzzleToIndexInNumUnknowns(puzzle) != -1)
        vassert(puzzleToNumUnknownRunes(puzzle) != -1)

        // Make sure it only appears in one place in numUnknownsToPuzzles
        val appearances = numUnknownsToPuzzles.flatMap(_.map(p => if (p == puzzle) 1 else 0)).sum
        vassert(appearances == 1)
      }
    })

    puzzleToNumUnknownRunes.zipWithIndex.foreach({ case (numUnknownRunes, puzzle) =>
      if (numUnknownRunes == -1) {
        // If numUnknownRunes is -1, then it's been marked solved, and it should appear nowhere.
        vassert(puzzleToUnknownRunes(puzzle).forall(_ == -1))
        vassert(!numUnknownsToPuzzles.exists(_.contains(puzzle)))
        vassert(puzzleToIndexInNumUnknowns(puzzle) == -1)
      } else {
        vassert(puzzleToUnknownRunes(puzzle).count(_ != -1) == numUnknownRunes)
        vassert(numUnknownsToPuzzles(numUnknownRunes).count(_ == puzzle) == 1)
        vassert(puzzleToIndexInNumUnknowns(puzzle) == numUnknownsToPuzzles(numUnknownRunes).indexOf(puzzle))
      }
      vassert((numUnknownRunes == -1) == puzzleToExecuted(puzzle))
    })

    puzzleToUnknownRunes.zipWithIndex.foreach({ case (unknownRunesWithNegs, puzzle) =>
      val unknownRunes = unknownRunesWithNegs.filter(_ != -1)
      val numUnknownRunes = unknownRunes.length
      if (puzzleToExecuted(puzzle)) {
        vassert(puzzleToNumUnknownRunes(puzzle) == -1)
      } else {
        if (numUnknownRunes == 0) {
          vassert(
            puzzleToNumUnknownRunes(puzzle) == 0 ||
              puzzleToNumUnknownRunes(puzzle) == -1)
        } else {
          vassert(puzzleToNumUnknownRunes(puzzle) == numUnknownRunes)
        }
      }
      unknownRunes.foreach(rune => vassert(runeToConclusion(rune).isEmpty))
    })

    numUnknownsToNumPuzzles.zipWithIndex.foreach({ case (numPuzzles, numUnknowns) =>
      vassert(puzzleToNumUnknownRunes.count(_ == numUnknowns) == numPuzzles)
    })

    numUnknownsToPuzzles.zipWithIndex.foreach({ case (puzzlesWithNegs, numUnknowns) =>
      val puzzles = puzzlesWithNegs.filter(_ != -1)
      puzzles.foreach(puzzle => {
        vassert(puzzleToNumUnknownRunes(puzzle) == numUnknowns)
      })
    })
  }

}
