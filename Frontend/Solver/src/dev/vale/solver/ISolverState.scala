package dev.vale.solver

import dev.vale.{Err, RangeS, Result}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

trait IStepState[Rule, Rune, Conclusion] {
  def getConclusion(rune: Rune): Option[Conclusion]
  def addRule(rule: Rule): Unit
//  def addPuzzle(ruleIndex: Int, runes: Vector[Rune])
  def getUnsolvedRules(): Vector[Rule]
  def concludeRune[ErrType](rangeS: List[RangeS], newlySolvedRune: Rune, conclusion: Conclusion): Unit
}

trait ISolverState[Rule, Rune, Conclusion] {
  def deepClone(): ISolverState[Rule, Rune, Conclusion]
  def getCanonicalRune(rune: Rune): Int
  def getUserRune(rune: Int): Rune
  def getRule(ruleIndex: Int): Rule
  def getConclusion(rune: Rune): Option[Conclusion]
  def getConclusions(): Stream[(Int, Conclusion)]
  def userifyConclusions(): Stream[(Rune, Conclusion)]
  def getUnsolvedRules(): Vector[Rule]
  def getNextSolvable(): Option[Int]
  def getSteps(): Stream[Step[Rule, Rune, Conclusion]]

  def addRule(rule: Rule): Int
  def addRune(rune: Rune): Int

  def getAllRunes(): Set[Int]
  def getAllRules(): Vector[Rule]

  def addPuzzle(ruleIndex: Int, runes: Vector[Int]): Unit

  def sanityCheck(): Unit

  // Success returns number of new conclusions
  def markRulesSolved[ErrType](ruleIndices: Vector[Int], newConclusions: Map[Int, Conclusion]):
  Result[Int, ISolverError[Rune, Conclusion, ErrType]]

  def initialStep[ErrType](
    ruleToPuzzles: Rule => Vector[Vector[Rune]],
    step: IStepState[Rule, Rune, Conclusion] => Result[Unit, ISolverError[Rune, Conclusion, ErrType]]):
  Result[Step[Rule, Rune, Conclusion], ISolverError[Rune, Conclusion, ErrType]]

  def simpleStep[ErrType](
    ruleToPuzzles: Rule => Vector[Vector[Rune]],
    ruleIndex: Int,
    rule: Rule,
    step: IStepState[Rule, Rune, Conclusion] => Result[Unit, ISolverError[Rune, Conclusion, ErrType]]):
  Result[Step[Rule, Rune, Conclusion], ISolverError[Rune, Conclusion, ErrType]]

  def complexStep[ErrType](
    ruleToPuzzles: Rule => Vector[Vector[Rune]],
    step: IStepState[Rule, Rune, Conclusion] => Result[Unit, ISolverError[Rune, Conclusion, ErrType]]):
  Result[Step[Rule, Rune, Conclusion], ISolverError[Rune, Conclusion, ErrType]]

  def concludeRune[ErrType](newlySolvedRune: Int, conclusion: Conclusion):
  Result[Boolean, ISolverError[Rune, Conclusion, ErrType]]
}
