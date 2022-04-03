package dev.vale.solver

import dev.vale.{Err, Ok, Result, vassert, vassertSome, vcurious, vfail}
import dev.vale.Err

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


object SimpleSolverState {
  def apply[Rule, Rune, Conclusion](): SimpleSolverState[Rule, Rune, Conclusion] = {
    SimpleSolverState[Rule, Rune, Conclusion](
      Vector(),
      Map[Rune, Int](),
      Map[Int, Rune](),
      Vector[Rule](),
      Map[Int, Array[Array[Int]]](),
      Map[Int, Conclusion]())
  }
}

case class SimpleSolverState[Rule, Rune, Conclusion](
  private var steps: Vector[Step[Rule, Rune, Conclusion]],

  private var userRuneToCanonicalRune: Map[Rune, Int],
  private var canonicalRuneToUserRune: Map[Int, Rune],

  private var rules: Vector[Rule],

  private var openRuleToPuzzleToRunes: Map[Int, Array[Array[Int]]],

  private var canonicalRuneToConclusion: Map[Int, Conclusion]
) extends ISolverState[Rule, Rune, Conclusion] {

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vfail() // is mutable, should never be hashed

  def deepClone(): SimpleSolverState[Rule, Rune, Conclusion] = {
    vcurious()
    SimpleSolverState(
      steps,
      userRuneToCanonicalRune,
      canonicalRuneToUserRune,
      rules,
      openRuleToPuzzleToRunes,
      canonicalRuneToConclusion)
  }

  override def sanityCheck(): Unit = {
//    vassert(rules == rules.distinct)
  }

  override def getRule(ruleIndex: Int): Rule = rules(ruleIndex)

  override def getConclusion(rune: Rune): Option[Conclusion] = canonicalRuneToConclusion.get(getCanonicalRune(rune))

  override def getUserRune(rune: Int): Rune = canonicalRuneToUserRune(rune)

  override def getConclusions(): Stream[(Int, Conclusion)] = {
    canonicalRuneToConclusion.toStream
  }

  override def userifyConclusions(): Stream[(Rune, Conclusion)] = {
    canonicalRuneToConclusion
      .toStream
      .map({ case (canonicalRune, conclusion) => (canonicalRuneToUserRune(canonicalRune), conclusion) })
  }

  override def getAllRunes(): Set[Int] = {
    openRuleToPuzzleToRunes.values.flatten.flatten.toSet
  }

  override def addRune(rune: Rune): Int = {
    vassert(!userRuneToCanonicalRune.contains(rune))
    val newCanonicalRune = userRuneToCanonicalRune.size
    userRuneToCanonicalRune = userRuneToCanonicalRune + (rune -> newCanonicalRune)
    canonicalRuneToUserRune = canonicalRuneToUserRune + (newCanonicalRune -> rune)
    newCanonicalRune
  }

  override def getAllRules(): Vector[Rule] = {
    rules
  }

  override def addRule(rule: Rule): Int = {
    val newCanonicalRule = rules.size
    rules = rules :+ rule
//    canonicalRuleToUserRule = canonicalRuleToUserRule + (newCanonicalRule -> rule)
    newCanonicalRule
  }

  override def getCanonicalRune(rune: Rune): Int = {
    vassertSome(userRuneToCanonicalRune.get(rune))
  }

  override def addPuzzle(ruleIndex: Int, runes: Array[Int]): Unit = {
    val thisRulePuzzleToRunes = openRuleToPuzzleToRunes.getOrElse(ruleIndex, Array())
    openRuleToPuzzleToRunes = openRuleToPuzzleToRunes + (ruleIndex -> (thisRulePuzzleToRunes :+ runes))
  }

  override def getNextSolvable(): Option[Int] = {
    openRuleToPuzzleToRunes
      .filter({ case (_, puzzleToRunes) =>
        puzzleToRunes.exists(runes => {
          runes.forall(rune => canonicalRuneToConclusion.contains(rune))
        })
      })
      // Get rule with lowest ID, keep it deterministic
      .keySet
      .headOption
  }

  override def getUnsolvedRules(): Vector[Rule] = {
    openRuleToPuzzleToRunes.keySet.toVector.map(rules)
  }

  // Returns whether it's a new conclusion
  def concludeRune[ErrType](newlySolvedRune: Int, newConclusion: Conclusion):
  Result[Boolean, ISolverError[Rune, Conclusion, ErrType]] = {
    val isNew =
      canonicalRuneToConclusion.get(newlySolvedRune) match {
        case Some(existingConclusion) => {
          if (existingConclusion != newConclusion) {
            return Err(
              SolverConflict(
                canonicalRuneToUserRune(newlySolvedRune),
                existingConclusion,
                newConclusion))
          }
          false
        }
        case None => true
      }
    canonicalRuneToConclusion = canonicalRuneToConclusion + (newlySolvedRune -> newConclusion)
    Ok(isNew)
  }

  // Success returns number of new conclusions
  override def markRulesSolved[ErrType](ruleIndices: Array[Int], newConclusions: Map[Int, Conclusion]):
  Result[Int, ISolverError[Rune, Conclusion, ErrType]] = {
    val numNewConclusions =
      newConclusions.map({ case (newlySolvedRune, newConclusion) =>
        concludeRune[ErrType](newlySolvedRune, newConclusion) match {
          case Err(e) => return Err(e)
          case Ok(isNew) => isNew
        }
      }).count(_ == true)

    ruleIndices.foreach(removeRule)

    Ok(numNewConclusions)
  }

  private def removeRule(ruleIndex: Int) = {
    openRuleToPuzzleToRunes = openRuleToPuzzleToRunes - ruleIndex
  }

  class SimpleStepState(
    ruleToPuzzles: Rule => Array[Array[Rune]],
    complex: Boolean,
    rules: Vector[(Int, Rule)]
  ) extends IStepState[Rule, Rune, Conclusion] {
    private var alive = true
    private var tentativeStep: Step[Rule, Rune, Conclusion] = Step(complex, rules, Vector(), Map())

    def close(): Step[Rule, Rune, Conclusion] = {
      vassert(alive)
      alive = false
      tentativeStep
    }

    override def getConclusion(requestedUserRune: Rune): Option[Conclusion] = {
      vassert(alive)
      SimpleSolverState.this.getConclusion(requestedUserRune)
    }

    override def addRule(rule: Rule): Unit = {
      vassert(alive)
      val ruleIndex = SimpleSolverState.this.addRule(rule)
      tentativeStep = tentativeStep.copy(addedRules = tentativeStep.addedRules :+ rule)
      ruleToPuzzles(rule).foreach(puzzleUserRunes => {
        val puzzleCanonicalRunes = puzzleUserRunes.map(SimpleSolverState.this.getCanonicalRune)
        SimpleSolverState.this.addPuzzle(ruleIndex, puzzleCanonicalRunes)
      })
    }

    override def getUnsolvedRules(): Vector[Rule] = {
      vassert(alive)
      SimpleSolverState.this.getUnsolvedRules()
    }

    override def concludeRune[ErrType](newlySolvedUserRune: Rune, conclusion: Conclusion): Unit = {
      vassert(alive)
//      val newlySolvedCanonicalRune = SimpleSolverState.this.userRuneToCanonicalRune(newlySolvedUserRune)
      tentativeStep = tentativeStep.copy(conclusions = tentativeStep.conclusions + (newlySolvedUserRune -> conclusion))
//      Ok(true)
    }
  }

  override def initialStep[ErrType](
    ruleToPuzzles: Rule => Array[Array[Rune]],
    step: IStepState[Rule, Rune, Conclusion] => Result[Unit, ISolverError[Rune, Conclusion, ErrType]]):
  Result[Step[Rule, Rune, Conclusion], ISolverError[Rune, Conclusion, ErrType]] = {
    val stepState = new SimpleStepState(ruleToPuzzles, false, Vector())
    step(stepState) match {
      case Ok(()) => {
        val step = stepState.close()
        steps = steps :+ step
        Ok(step)
      }
      case Err(e) => {
        stepState.close()
        Err(e)
      }
    }
  }

  override def simpleStep[ErrType](
    ruleToPuzzles: Rule => Array[Array[Rune]],
    ruleIndex: Int,
    rule: Rule,
    step: IStepState[Rule, Rune, Conclusion] => Result[Unit, ISolverError[Rune, Conclusion, ErrType]]):
  Result[Step[Rule, Rune, Conclusion], ISolverError[Rune, Conclusion, ErrType]] = {
    val stepState = new SimpleStepState(ruleToPuzzles, false, Vector((ruleIndex, rule)))
    step(stepState) match {
      case Ok(()) => {
        val step = stepState.close()
        steps = steps :+ step
        Ok(step)
      }
      case Err(e) => {
        stepState.close()
        Err(e)
      }
    }
  }

  override def complexStep[ErrType](
    ruleToPuzzles: Rule => Array[Array[Rune]],
    step: IStepState[Rule, Rune, Conclusion] => Result[Unit, ISolverError[Rune, Conclusion, ErrType]]):
  Result[Step[Rule, Rune, Conclusion], ISolverError[Rune, Conclusion, ErrType]] = {
    val stepState = new SimpleStepState(ruleToPuzzles, true, Vector())
    step(stepState) match {
      case Ok(()) => {
        val step = stepState.close()
        steps = steps :+ step
        Ok(step)
      }
      case Err(e) => {
        stepState.close()
        Err(e)
      }
    }
  }

  override def getSteps(): Vector[Step[Rule, Rune, Conclusion]] = steps
}
