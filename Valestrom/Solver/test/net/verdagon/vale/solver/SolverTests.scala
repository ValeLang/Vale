package net.verdagon.vale.solver

import net.verdagon.vale.{Collector, Err, Ok, Result, vassert, vassertOne, vassertSome, vfail, vimpl, vwat}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.Map

class SolverTests extends FunSuite with Matchers with Collector {
  val complexRuleSet =
    Array(
      Literal(-3L, "1448"),
      CoordComponents(-6L, -5L, -5L),
      Literal(-2L, "1337"),
      Equals(-4L, -2L),
      OneOf(-4L, Array("1337", "73")),
      Equals(-1L, -5L),
      CoordComponents(-1L, -2L, -3L),
      Equals(-6L, -7L))
  val complexRuleSetEqualsRules = Array(3, 5, 7)

  def testSimpleAndOptimized(testName: String, testTags : org.scalatest.Tag*)(testFun : Boolean => scala.Any)(implicit pos : org.scalactic.source.Position) : scala.Unit = {
    test(testName + " (simple solver)", testTags: _*){ testFun(false) }(pos)
    test(testName + " (optimized solver)", testTags: _*){ testFun(true) }(pos)
  }

  test("Simple int rule") {
    val rules =
      Array(
        Literal(-1L, "1337"))
    getConclusions(rules, true) shouldEqual Map(-1L -> "1337")
  }

  test("Equals transitive") {
    val rules =
      Array(
        Equals(-2L, -1L),
        Literal(-1L, "1337"))
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "1337", -2L -> "1337")
  }


  test("Incomplete solve") {
    val rules =
      Array(
        OneOf(-1L, Array("1448", "1337")))
    getConclusions(rules, false) shouldEqual Map()
  }


  test("Half-complete solve") {
    // Note how these two rules aren't connected to each other at all
    val rules =
      Array(
        OneOf(-1L, Array("1448", "1337")),
        Literal(-2L, "1337"))
    getConclusions(rules, false) shouldEqual Map(-2L -> "1337")
  }


  test("OneOf") {
    val rules =
      Array(
        OneOf(-1L, Array("1448", "1337")),
        Literal(-1L, "1337"))
    getConclusions(rules, true) shouldEqual Map(-1L -> "1337")
  }


  test("Solves a components rule") {
    val rules =
      Array(
        CoordComponents(-1L, -2L, -3L),
        Literal(-2L, "1337"),
        Literal(-3L, "1448"))
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "1337/1448", -2L -> "1337", -3L -> "1448")
  }


  test("Reverse-solve a components rule") {
    val rules =
      Array(
        CoordComponents(-1L, -2L, -3L),
        Literal(-1L, "1337/1448"))
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "1337/1448", -2L -> "1337", -3L -> "1448")
  }


  test("Test infer Pack") {
    val rules =
      Array(
        Literal(-1L, "1337"),
        Literal(-2L, "1448"),
        Pack(-3L, Array(-1L, -2L)))
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "1337", -2L -> "1448", -3L -> "1337,1448")
  }


  test("Test infer Pack from result") {
    val rules =
      Array(
        Literal(-3L, "1337,1448"),
        Pack(-3L, Array(-1L, -2L)))
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "1337", -2L -> "1448", -3L -> "1337,1448")
  }


  test("Test infer Pack from empty result") {
    val rules =
      Array(
        Literal(-3L, ""),
        Pack(-3L, Array()))
    getConclusions(rules, true) shouldEqual
      Map(-3L -> "")
  }


  test("Test cant solve empty Pack") {
    val rules =
      Array(
        Pack(-3L, Array()))
    getConclusions(rules, false) shouldEqual Map()
  }


  test("Complex rule set") {
    val conclusions = getConclusions(complexRuleSet, true)
    conclusions.get(-7L) shouldEqual Some("1337/1448/1337/1448")
  }


  test("Test receiving struct to struct") {
    val rules =
      Array(
        Literal(-1L, "Firefly"),
        Receive(-1L, -2L))
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "Firefly", -2L -> "Firefly")
  }


  test("Test receive struct from sent interface") {
    val rules =
      Array(
        Literal(-1L, "Firefly"),
        Literal(-2L, "ISpaceship"),
        Receive(-1L, -2L))
    expectSolveFailure(rules) match {
      case FailedSolve(steps, unsolvedRules, err) => {
        steps.flatMap(_.conclusions) shouldEqual Vector((-1,"Firefly"), (-2,"ISpaceship"), (-2,"Firefly"))
        unsolvedRules shouldEqual Vector(Receive(-1,-2))
        err match {
          case SolverConflict(
            -2,
            // Already concluded this
            "ISpaceship",
            // But now we're concluding that it should have been a Firefly
            "Firefly") =>
        }
      }
    }
  }


  test("Test receive interface from sent struct") {
    val rules =
      Array(
        Literal(-1L, "ISpaceship"),
        Literal(-2L, "Firefly"),
        Receive(-1L, -2L))
    // Should be a successful solve
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "ISpaceship", -2L -> "Firefly")
  }


  test("Test complex solve: most specific ancestor") {
    val rules =
      Array(
        Literal(-2L, "Firefly"),
        Receive(-1L, -2L))
    // Should be a successful solve
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "Firefly", -2L -> "Firefly")
  }


  test("Test complex solve: calculate common ancestor") {
    val rules =
      Array(
        Literal(-2L, "Firefly"),
        Literal(-3L, "Serenity"),
        Receive(-1L, -2L),
        Receive(-1L, -3L))
    // Should be a successful solve
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "ISpaceship", -2L -> "Firefly", -3L -> "Serenity")
  }


  test("Test complex solve: descendant satisfying call") {
    val rules =
      Array(
        Literal(-2L, "Flamethrower:int"),
        Receive(-1L, -2L),
        Call(-1L, -3L, -4L),
        Literal(-3L, "IWeapon"))
    // Should be a successful solve
    getConclusions(rules, true) shouldEqual
      Map(
        -1 -> "IWeapon:int",
        -4 -> "int",
        -2 -> "Flamethrower:int",
        -3 -> "IWeapon")
  }


  test("Partial Solve") {
    // It'll be nice to partially solve some rules, for example before we put them in the overload index.

    // Note how these two rules aren't connected to each other at all
    val rules =
      Vector(
        Literal(-2, "A"),
        Call(-3, -1, -2)) // We dont know the template, -1, yet


    val solverState =
      Solver.makeInitialSolverState[IRule, Long, String](
        true,
        true,
        rules,
        (rule: IRule) => rule.allRunes.toVector,
        (rule: IRule) => rule.allPuzzles,
        Map())
    val firstConclusions =
      Solver.solve((r: IRule) => r.allPuzzles, (), (), solverState, TestRuleSolver) match {
        case Ok(c) => c._2
        case Err(e) => vfail(e)
      }
    firstConclusions.toMap shouldEqual Map(-2 -> "A")
    solverState.markRulesSolved(Array(), Map(solverState.getCanonicalRune(-1) -> "Firefly"))

    val secondConclusions =
      Solver.solve((r: IRule) => r.allPuzzles, (), (), solverState, TestRuleSolver) match {
        case Ok(c) => c._2
        case Err(e) => vfail(e)
      }
    secondConclusions.toMap shouldEqual
      Map(-1 -> "Firefly", -2 -> "A", -3 -> "Firefly:A")
  }


  test("Predicting") {
    // "Predicting" is when the rules arent completely solvable, but we can still run some of them
    // to figure out what we can.
    // For example, in:
    //   #2 = 1337
    //   #3 = #1<#2>
    // we can figure out that #2 is 1337, even if we don't know #1 yet.
    // This is useful for recursive types.
    // See: Recursive Types Must Have Types Predicted (RTMHTP)

    def solveWithPuzzler(puzzler: IRule => Array[Array[Long]]) = {
      // Below, we're reporting that Lookup has no puzzles that can solve it.
      val rules =
        Vector(
          Lookup(-1, "Firefly"),
          Literal(-2, "1337"),
          Call(-3, -1, -2)) // X = Firefly<A>

      val solverState =
        Solver.makeInitialSolverState[IRule, Long, String](
          true,
          true,
          rules,
          (rule: IRule) => rule.allRunes.toVector,
          puzzler,
          Map())
      val conclusions: Map[Long, String] =
        Solver.solve((r: IRule) => r.allPuzzles, (), (), solverState, TestRuleSolver) match {
          case Ok(c) => c._2.toMap
          case Err(e) => vfail(e)
        }
      conclusions
    }

    val predictions =
      solveWithPuzzler({
        // This Array() makes it unsolvable
        case Lookup(rune, name) => Array()
        case rule => rule.allPuzzles
      })
//    vassert(predictionRuleExecutionOrder sameElements Array(1))
    vassert(predictions.size == 1)
    vassert(predictions(-2) == "1337")

    val conclusions = solveWithPuzzler(_.allPuzzles)
//    vassert(ruleExecutionOrder.length == 3)
    conclusions shouldEqual Map(-1L -> "Firefly", -2L -> "1337", -3L -> "Firefly:1337")
  }


  test("Test conflict") {
    val rules =
      Array(
        Literal(-1L, "1448"),
        Literal(-1L, "1337"))
    expectSolveFailure(rules) match {
      case FailedSolve(_, _, SolverConflict(_, conclusionA, conclusionB)) => {
        Vector(conclusionA, conclusionB).sorted shouldEqual Vector("1337", "1448").sorted
      }
    }
  }

  private def expectSolveFailure(rules: IndexedSeq[IRule]):
  FailedSolve[IRule, Long, String, String] = {
    val solverState =
      Solver.makeInitialSolverState[IRule, Long, String](
        true,
        true,
        rules,
        (rule: IRule) => rule.allRunes.toVector,
        (rule: IRule) => rule.allPuzzles,
        Map())
    Solver.solve((r: IRule) => r.allPuzzles, (), (), solverState, TestRuleSolver) match {
      case Ok(c) => vfail(c)
      case Err(e) => e
    }
  }

  private def getConclusions(
    rules: IndexedSeq[IRule],
    expectCompleteSolve: Boolean,
    initiallyKnownRunes: Map[Long, String] = Map()):
  Map[Long, String] = {
    val solverState =
      Solver.makeInitialSolverState[IRule, Long, String](
        true,
        true,
        rules,
        (rule: IRule) => rule.allRunes.toVector,
        (rule: IRule) => rule.allPuzzles,
        initiallyKnownRunes)
    val conclusions =
      Solver.solve((r: IRule) => r.allPuzzles, (), (), solverState, TestRuleSolver) match {
          case Ok(c) => c._2
          case Err(e) => vfail(e)
        }
    val conclusionsMap = conclusions.toMap
    vassert(expectCompleteSolve == (conclusionsMap.keySet == rules.flatMap(_.allRunes).toSet))
    conclusionsMap
  }
}
