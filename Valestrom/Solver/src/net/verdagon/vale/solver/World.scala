package net.verdagon.vale.solver

import net.verdagon.vale.{vassert, vcurious, vfail}


case class World[RuneID, RuleID, Literal, Lookup](
  rules: Array[IRulexAR[RuneID, RuleID, Literal, Lookup]],

  // For each rule, what are all the runes involved in it
  ruleToRunes: Array[Array[Int]],

  // For example, if rule 7 says:
  //   1 = Ref(2, 3, 4, 5)
  // then 2, 3, 4, 5 together could solve the rule, or 1 could solve the rule.
  // In other words, the two sets of runes that could solve the rule are:
  // - [1]
  // - [2, 3, 4, 5]
  // Here we have two "puzzles". The runes in a puzzle are called "pieces".
  // Puzzles are identified up-front by Astronomer.

  // This tracks, for each puzzle, what rule does it refer to
  puzzleToRule: Array[Int],
  // This tracks, for each puzzle, what rules does it have
  puzzleToRunes: Array[Array[Int]],

  // For every rule, this is which puzzles can solve it.
  ruleToPuzzles: Array[Array[Int]],

  // For every rune, this is which puzzles it participates in.
  runeToPuzzles: Array[Array[Int]],

  // For every rune, which other rune might describe the interface that it must
  // inherit from.
  kindRuneToBoundingInterfaceRune: Array[Int])

case class PlannerState[RuleID, Literal, Lookup](
  world: World[Int, RuleID, Literal, Lookup], // immutable

  // For each rule, whether it's been actually executed or not
  puzzleToExecuted: Array[Boolean],

  // Together, these basically form a Array[Vector[Int]]
  puzzleToNumUnknownRunes: Array[Int],
  puzzleToUnknownRunes: Array[Array[Int]],
  // This is the puzzle's index in the below numUnknownsToPuzzle map.
  puzzleToIndexInNumUnknowns: Array[Int],

  // Together, these basically form a Array[Vector[Int]]
  // which will have five elements: 0, 1, 2, 3, 4
  // At slot 4 is all the puzzles that have 4 unknowns left
  // At slot 3 is all the puzzles that have 3 unknowns left
  // At slot 2 is all the puzzles that have 2 unknowns left
  // At slot 1 is all the puzzles that have 1 unknowns left
  // At slot 0 is all the puzzles that have 0 unknowns left
  // We will:
  // - Move a puzzle from one set to the next set if we solve one of its runes
  // - Solve any puzzle that has 0 unknowns left
  numUnknownsToNumPuzzles: Array[Int],
  numUnknownsToPuzzles: Array[Array[Int]]
) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vfail() // is mutable, should never be hashed

  def deepClone(): PlannerState[RuleID, Literal, Lookup] = {
    PlannerState(
      world,
      puzzleToExecuted.clone(),
      puzzleToNumUnknownRunes.clone(),
      puzzleToUnknownRunes.map(_.clone()).clone(),
      puzzleToIndexInNumUnknowns.clone(),
      numUnknownsToNumPuzzles.clone(),
      numUnknownsToPuzzles.map(_.clone()).clone())
  }

  def sanityCheck[Conclusion](runeToIsSolved: Array[Boolean]) = {
    puzzleToExecuted.zipWithIndex.foreach({ case (executed, puzzle) =>
      if (executed) {
        vassert(puzzleToIndexInNumUnknowns(puzzle) == -1)
        vassert(puzzleToNumUnknownRunes(puzzle) == -1)
        world.puzzleToRunes(puzzle).foreach(rune => vassert(runeToIsSolved(rune)))
        puzzleToUnknownRunes(puzzle).foreach(unknownRune => vassert(unknownRune == -1))
        numUnknownsToPuzzles.foreach(_.foreach(p => vassert(p != puzzle)))
      } else {
        // An un-executed puzzle might have all known runes. It just means that it hasn't been
        // executed yet, it'll probably be executed very soon.

        vassert(puzzleToIndexInNumUnknowns(puzzle) != -1)
        vassert(puzzleToNumUnknownRunes(puzzle) != -1)

        // Make sure it only appears in one place in numUnknownsToPuzzles
        vassert(numUnknownsToPuzzles.flatMap(_.map(p => if (p == puzzle) 1 else 0)).sum == 1)
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
      unknownRunes.foreach(rune => vassert(!runeToIsSolved(rune)))
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
