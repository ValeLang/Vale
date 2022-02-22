package net.verdagon.vale.solver

import net.verdagon.vale.{Err, Ok, Result, vassert, vcurious, vfail, vimpl, vpass}

import scala.collection.immutable.Map
import scala.collection.mutable

case class Step[Rule, Rune, Conclusion](complex: Boolean, solvedRules: Vector[(Int, Rule)], addedRules: Vector[Rule], conclusions: Map[Rune, Conclusion])

sealed trait ISolverOutcome[Rule, Rune, Conclusion, ErrType] {
  def getOrDie(): Map[Rune, Conclusion]
}
sealed trait IIncompleteOrFailedSolve[Rule, Rune, Conclusion, ErrType] extends ISolverOutcome[Rule, Rune, Conclusion, ErrType] {
  def unsolvedRules: Vector[Rule]
  def unsolvedRunes: Vector[Rune]
  def steps: Vector[Step[Rule, Rune, Conclusion]]
}
case class CompleteSolve[Rule, Rune, Conclusion, ErrType](
  conclusions: Map[Rune, Conclusion]
) extends ISolverOutcome[Rule, Rune, Conclusion, ErrType] {
  override def getOrDie(): Map[Rune, Conclusion] = conclusions
}
case class IncompleteSolve[Rule, Rune, Conclusion, ErrType](
  steps: Vector[Step[Rule, Rune, Conclusion]],
  unsolvedRules: Vector[Rule],
  unknownRunes: Set[Rune]
) extends IIncompleteOrFailedSolve[Rule, Rune, Conclusion, ErrType] {
  vassert(unknownRunes.nonEmpty)
  vpass()
  override def getOrDie(): Map[Rune, Conclusion] = vfail()
  override def unsolvedRunes: Vector[Rune] = unknownRunes.toVector
}

case class FailedSolve[Rule, Rune, Conclusion, ErrType](
  steps: Vector[Step[Rule, Rune, Conclusion]],
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
    stepState: IStepState[Rule, Rune, Conclusion]
  ): Result[Unit, ISolverError[Rune, Conclusion, ErrType]]
}

class Solver[Rule, Rune, Env, State, Conclusion, ErrType](sanityCheck: Boolean, useOptimizedSolver: Boolean) {
  def solve(
    ruleToPuzzles: Rule => Array[Array[Rune]],
    state: State,
    env: Env,
    solverState: ISolverState[Rule, Rune, Conclusion],
    solveRule: ISolveRule[Rule, Rune, Env, State, Conclusion, ErrType]
  ): Result[(Stream[Step[Rule, Rune, Conclusion]], Stream[(Rune, Conclusion)]), FailedSolve[Rule, Rune, Conclusion, ErrType]] = {

    while ({
      while ({
        solverState.getNextSolvable() match {
          case None => false
          case Some(solvingRuleIndex) => {
            val rule = solverState.getRule(solvingRuleIndex)
            val step =
              solverState.simpleStep[ErrType](ruleToPuzzles, solvingRuleIndex, rule, solveRule.solve(state, env, solvingRuleIndex, rule, _)) match {
                case Ok(step) => step
                case Err(e) => return Err(FailedSolve(solverState.getSteps(), solverState.getUnsolvedRules(), e))
              }

            val canonicalConclusions =
              step.conclusions.map({ case (userRune, conclusion) => solverState.getCanonicalRune(userRune) -> conclusion }).toMap
            solverState.markRulesSolved[ErrType](Array(solvingRuleIndex), canonicalConclusions) match {
              case Ok(_) =>
              case Err(e) => return Err(FailedSolve(solverState.getSteps(), solverState.getUnsolvedRules(), e))
            }

            if (sanityCheck) {
              solverState.sanityCheck()
            }
            true
          }
        }
      }) {}

      if (solverState.getUnsolvedRules().nonEmpty) {
        val step =
          solverState.complexStep(ruleToPuzzles, solveRule.complexSolve(state, env, _)) match {
            case Ok(step) => step
            case Err(e) => return Err(FailedSolve(solverState.getSteps(), solverState.getUnsolvedRules(), e))
          }
        val canonicalConclusions =
          step.conclusions.map({ case (userRune, conclusion) => solverState.getCanonicalRune(userRune) -> conclusion }).toMap
        val continue =
          solverState.markRulesSolved[ErrType](step.solvedRules.map(_._1).toArray, canonicalConclusions) match {
            case Ok(0) => false // Do nothing, we're done
            case Ok(_) => true // continue
            case Err(e) => return Err(FailedSolve(solverState.getSteps(), solverState.getUnsolvedRules(), e))
          }

        solverState.sanityCheck()
        continue
      } else {
        false // no more rules to solve, halt
      }
    }) {}

    Ok((solverState.getSteps().toStream, solverState.userifyConclusions()))
  }

  def makeInitialSolverState(
    initialRules: IndexedSeq[Rule],
    ruleToRunes: Rule => Iterable[Rune],
    ruleToPuzzles: Rule => Array[Array[Rune]],
    initiallyKnownRunes: Map[Rune, Conclusion]):
  ISolverState[Rule, Rune, Conclusion] = {
    val solverState =
//      if (useOptimizedSolver) {
//        OptimizedSolverState[Rule, Rune, Conclusion]()
//      } else {
        SimpleSolverState[Rule, Rune, Conclusion]()
//      }

    (initialRules.flatMap(ruleToRunes) ++ initiallyKnownRunes.keys).distinct.foreach(solverState.addRune)

    val step =
      solverState.initialStep(ruleToPuzzles, (stepState: IStepState[Rule, Rune, Conclusion]) => {
        initiallyKnownRunes.foreach({ case (rune, conclusion) =>
          stepState.concludeRune(rune, conclusion)
        })
        Ok(())
      }).getOrDie()
    step.conclusions.foreach({ case (rune, conclusion) =>
      solverState.concludeRune(solverState.getCanonicalRune(rune), conclusion)
    })


    initialRules.foreach(rule => {
      val ruleIndex = solverState.addRule(rule)
      ruleToPuzzles(rule).foreach(puzzleRunes => {
        solverState.addPuzzle(ruleIndex, puzzleRunes.map(solverState.getCanonicalRune).distinct)
      })
    })

    if (sanityCheck) {
      solverState.sanityCheck()
    }
    solverState
  }
}
