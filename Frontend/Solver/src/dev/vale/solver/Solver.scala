package dev.vale.solver

import dev.vale.{Err, Interner, Ok, Profiler, RangeS, Result, vassert, vfail, vimpl, vpass}

import scala.collection.immutable.Map
import scala.collection.mutable

case class Step[Rule, Rune, Conclusion](complex: Boolean, solvedRules: Vector[(Int, Rule)], addedRules: Vector[Rule], conclusions: Map[Rune, Conclusion])


sealed trait ISolverOutcome[Rule, Rune, Conclusion, ErrType] {
  def getOrDie(): Map[Rune, Conclusion]
}
sealed trait IIncompleteOrFailedSolve[Rule, Rune, Conclusion, ErrType] extends ISolverOutcome[Rule, Rune, Conclusion, ErrType] {
  def unsolvedRules: Vector[Rule]
  def unsolvedRunes: Vector[Rune]
  def steps: Stream[Step[Rule, Rune, Conclusion]]
}
case class CompleteSolve[Rule, Rune, Conclusion, ErrType](
  steps: Stream[Step[Rule, Rune, Conclusion]],
  conclusions: Map[Rune, Conclusion]
) extends ISolverOutcome[Rule, Rune, Conclusion, ErrType] {
  override def getOrDie(): Map[Rune, Conclusion] = conclusions
}
case class IncompleteSolve[Rule, Rune, Conclusion, ErrType](
  steps: Stream[Step[Rule, Rune, Conclusion]],
  unsolvedRules: Vector[Rule],
  unknownRunes: Set[Rune],
  incompleteConclusions: Map[Rune, Conclusion]
) extends IIncompleteOrFailedSolve[Rule, Rune, Conclusion, ErrType] {
  vassert(unknownRunes.nonEmpty)
  vpass()
  override def getOrDie(): Map[Rune, Conclusion] = vfail()
  override def unsolvedRunes: Vector[Rune] = unknownRunes.toVector
}

case class FailedSolve[Rule, Rune, Conclusion, ErrType](
  steps: Stream[Step[Rule, Rune, Conclusion]],
  unsolvedRules: Vector[Rule],
  error: ISolverError[Rune, Conclusion, ErrType]
) extends IIncompleteOrFailedSolve[Rule, Rune, Conclusion, ErrType] {
  override def getOrDie(): Map[Rune, Conclusion] = vfail()
  vpass()
  override def unsolvedRunes: Vector[Rune] = Vector()
}

sealed trait ISolverError[Rune, Conclusion, ErrType]
case class SolverConflict[Rune, Conclusion, ErrType](
  rune: Rune,
  previousConclusion: Conclusion,
  newConclusion: Conclusion
) extends ISolverError[Rune, Conclusion, ErrType] {
  vpass()

  if (previousConclusion.toString == "CoordTemplata(CoordT(own,PlaceholderT(FullNameT(PackageCoordinate(stdlib,Vector(collections, hashset)),Vector(FunctionTemplateNameT(diff_iter,FileCoordinate(PackageCoordinate(stdlib,Vector(collections, hashset)),/Users/verdagon/Vale/stdlib/src/collections/hashset/hash_set_diff.vale):161)),PlaceholderNameT(PlaceholderTemplateNameT(0))))))" &&
  newConclusion.toString == "CoordTemplata(CoordT(share,PlaceholderT(FullNameT(PackageCoordinate(stdlib,Vector(collections, hashset)),Vector(FunctionTemplateNameT(diff_iter,FileCoordinate(PackageCoordinate(stdlib,Vector(collections, hashset)),/Users/verdagon/Vale/stdlib/src/collections/hashset/hash_set_diff.vale):161)),PlaceholderNameT(PlaceholderTemplateNameT(0))))))") {
    vpass()
  }
}
case class RuleError[Rune, Conclusion, ErrType](
//  ruleIndex: Int,
  err: ErrType
) extends ISolverError[Rune, Conclusion, ErrType]

// Given enough user specified template params and param inputs, we should be able to
// infer everything.
// This class's purpose is to take those things, and see if it can figure out as many
// inferences as possible.

trait ISolveRule[Rule, Rune, Env, State, Conclusion, ErrType] {
  def solve(
    state: State,
    env: Env,
    solverState: ISolverState[Rule, Rune, Conclusion],
    ruleIndex: Int,
    rule: Rule,
    stepState: IStepState[Rule, Rune, Conclusion]):
  Result[Unit, ISolverError[Rune, Conclusion, ErrType]]

  // Called when we can't do any regular solves, we don't have enough
  // runes. This is where we do more interesting rules, like SMCMST.
  // See CSALR for more.
  def complexSolve(
    state: State,
    env: Env,
    solverState: ISolverState[Rule, Rune, Conclusion],
    stepState: IStepState[Rule, Rune, Conclusion]
  ): Result[Unit, ISolverError[Rune, Conclusion, ErrType]]

  def sanityCheckConclusion(env: Env, state: State, rune: Rune, conclusion: Conclusion): Unit
}

class Solver[Rule, Rune, Env, State, Conclusion, ErrType](
    sanityCheck: Boolean,
    useOptimizedSolver: Boolean,
    interner: Interner,
    ruleToPuzzles: Rule => Vector[Vector[Rune]],
    ruleToRunes: Rule => Iterable[Rune],
    solveRule: ISolveRule[Rule, Rune, Env, State, Conclusion, ErrType],
    setupRange: List[RangeS],
    initialRules: IndexedSeq[Rule],
    initiallyKnownRunes: Map[Rune, Conclusion]) {

  private val solverState =
    if (useOptimizedSolver) {
      OptimizedSolverState[Rule, Rune, Conclusion]()
    } else {
      SimpleSolverState[Rule, Rune, Conclusion]()
    }

  Profiler.frame(() => {
    (initialRules.flatMap(ruleToRunes) ++ initiallyKnownRunes.keys)
      .distinct.foreach(solverState.addRune)

    if (sanityCheck) {
      solverState.sanityCheck()
    }

    val step =
      solverState.initialStep(ruleToPuzzles, (stepState: IStepState[Rule, Rune, Conclusion]) => {
        initiallyKnownRunes.foreach({ case (rune, conclusion) =>
          stepState.concludeRune(RangeS.internal(interner, -6434324) :: setupRange, rune, conclusion)
        })
        Ok(())
      }).getOrDie()
    step.conclusions.foreach({ case (rune, conclusion) =>
      solverState.concludeRune(solverState.getCanonicalRune(rune), conclusion)
    })

    if (sanityCheck) {
      solverState.sanityCheck()
    }

    initialRules.foreach(rule => {
      val ruleIndex = solverState.addRule(rule)
      if (sanityCheck) {
        solverState.sanityCheck()
      }
      ruleToPuzzles(rule).foreach(puzzleRunes => {
        solverState.addPuzzle(ruleIndex, puzzleRunes.map(solverState.getCanonicalRune).distinct)
      })
      if (sanityCheck) {
        solverState.sanityCheck()
      }
    })

    if (sanityCheck) {
      solverState.sanityCheck()
    }
    solverState
  })

  def getAllRules(): Vector[Rule] = {
    solverState.getAllRules()
  }

  def userifyConclusions(): Stream[(Rune, Conclusion)] = {
    solverState.userifyConclusions()
  }

  def markRulesSolved[ErrType](ruleIndices: Vector[Int], newConclusions: Map[Int, Conclusion]):
  Result[Int, ISolverError[Rune, Conclusion, ErrType]] = {
    solverState.markRulesSolved(ruleIndices, newConclusions)
  }

  def getCanonicalRune(rune: Rune): Int = {
    solverState.getCanonicalRune(rune)
  }

  def getSteps(): Stream[Step[Rule, Rune, Conclusion]] = {
    solverState.getSteps()
  }

  def getAllRunes(): Set[Int] = {
    solverState.getAllRunes()
  }

  def getUserRune(rune: Int): Rune = {
    solverState.getUserRune(rune)
  }

  def getUnsolvedRules(): Vector[Rule] = {
    solverState.getUnsolvedRules()
  }

  // Returns true if there's more to be done, false if we've gotten as far as we can.
  def advance(env: Env, state: State):
  Result[Boolean, FailedSolve[Rule, Rune, Conclusion, ErrType]] = {
    Profiler.frame(() => {

      if (sanityCheck) {
        solverState.sanityCheck()

        solverState.userifyConclusions().foreach({ case (rune, conclusion) =>
          solveRule.sanityCheckConclusion(env, state, rune, conclusion)
        })
      }

      // Stage 1: Do simple solves

      solverState.getNextSolvable() match {
        case None => // continue onto the next stage
        case Some(solvingRuleIndex) => {
          val rule = solverState.getRule(solvingRuleIndex)
          val step =
            solverState.simpleStep[ErrType](ruleToPuzzles, solvingRuleIndex, rule, solveRule.solve(state, env, solverState, solvingRuleIndex, rule, _)) match {
              case Ok(step) => step
              case Err(e) => return Err(FailedSolve(solverState.getSteps(), solverState.getUnsolvedRules(), e))
            }

          val canonicalConclusions =
            step.conclusions.map({ case (userRune, conclusion) => solverState.getCanonicalRune(userRune) -> conclusion }).toMap

          solverState.markRulesSolved[ErrType](Vector(solvingRuleIndex), canonicalConclusions) match {
            case Err(e) => return Err(FailedSolve(solverState.getSteps(), solverState.getUnsolvedRules(), e))
            case Ok(_) =>
          }

          if (sanityCheck) {
            step.conclusions.foreach({ case (rune, conclusion) =>
              solveRule.sanityCheckConclusion(env, state, rune, conclusion)
            })
            solverState.sanityCheck()
          }
          // Go back to the beginning. Next step, if there's no simple rule ready to solve, then
          // it'll start doing a complex solve if available, or just finish.
          return Ok(true)
        }
      }

      // Stage 2: Do a complex solve if available.

      if (solverState.getUnsolvedRules().nonEmpty) {
        val step =
          solverState.complexStep(ruleToPuzzles, solveRule.complexSolve(state, env, solverState, _)) match {
            case Ok(step) => step
            case Err(e) => return Err(FailedSolve(solverState.getSteps(), solverState.getUnsolvedRules(), e))
          }
        val canonicalConclusions =
          step.conclusions.map({ case (userRune, conclusion) => solverState.getCanonicalRune(userRune) -> conclusion }).toMap
        solverState.markRulesSolved[ErrType](step.solvedRules.map(_._1).toVector, canonicalConclusions) match {
          case Ok(0) => {
            if (sanityCheck) {
              solverState.sanityCheck()
            }
            // There's nothing more to be done. Let's continue on to stage 3.
          }
          case Ok(_) => {
            if (sanityCheck) {
              solverState.sanityCheck()
            }
            return Ok(true) // Go back to stage 1
          }
          case Err(e) => return Err(FailedSolve(solverState.getSteps(), solverState.getUnsolvedRules(), e))
        }
      } else {
        // No more rules to solve, so continue to the wrapping up stages of the solve.
      }

      // Stage 3: We're done! The user should look at the conclusions to see if they're all solved,
      // and they can even add more rules if they want.

      return Ok(false)
    })
  }
}
