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
  ruleIndex: Int,
  err: ErrType
) extends ISolverError[Conclusion, ErrType]

trait ISolverDelegate[RuleID, Literal, Lookup, Env, State, Conclusion, ErrType] {
  def solve(
    state: State,
    env: Env,
    range: RuleID,
    rule: IRulexAR[Int, RuleID, Literal, Lookup],
    runes: Int => Option[Conclusion]
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
    orderedCanonicalRules: Array[IRulexAR[Int, RuleID, Literal, Lookup]],
    numRunes: Int,
    invocationRange: RuleID
  ): Result[Array[Option[Conclusion]], FailedSolve[Conclusion, ErrType]] = {
    val conclusions: Array[Option[Conclusion]] = (0 until numRunes).map(_ => None).toArray

    orderedCanonicalRules.zipWithIndex.foreach({ case (solvingRule, solvingRuleIndex) =>
      val newlySolvedRuneToConclusion =
        delegate.solve(state, env, invocationRange, solvingRule, i => conclusions(i)) match {
          case Ok(x) => x
          case Err(e) => return Err(FailedSolve(RuleError(solvingRuleIndex, e), conclusions))
        }

      newlySolvedRuneToConclusion.foreach({ case (newlySolvedRune, newConclusion) =>
        conclusions(newlySolvedRune) match {
          case None => conclusions(newlySolvedRune) = Some(newConclusion)
          case Some(existingConclusion) => {
            if (existingConclusion != newConclusion) {
              return Err(FailedSolve(SolverConflict(solvingRuleIndex, newlySolvedRune, existingConclusion, newConclusion), conclusions))
            }
          }
        }
      })
    })

    Ok(conclusions)
  }
}
