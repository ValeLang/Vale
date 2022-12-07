package dev.vale.solver

import dev.vale.{Collector, Err, Interner, Ok, RangeS, vassert, vfail}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.Map

class SolverTests extends FunSuite with Matchers with Collector {
  val complexRuleSet =
    Vector(
      Literal(-3L, "1448"),
      CoordComponents(-6L, -5L, -5L),
      Literal(-2L, "1337"),
      Equals(-4L, -2L),
      OneOf(-4L, Vector("1337", "73")),
      Equals(-1L, -5L),
      CoordComponents(-1L, -2L, -3L),
      Equals(-6L, -7L))
  val complexRuleSetEqualsRules = Vector(3, 5, 7)

  def testSimpleAndOptimized(testName: String, testTags : org.scalatest.Tag*)(testFun : Boolean => scala.Any)(implicit pos : org.scalactic.source.Position) : scala.Unit = {
    test(testName + " (simple solver)", testTags: _*){ testFun(false) }(pos)
    test(testName + " (optimized solver)", testTags: _*){ testFun(true) }(pos)
  }

  test("Simple int rule") {
    val rules =
      Vector(
        Literal(-1L, "1337"))
    getConclusions(rules, true) shouldEqual Map(-1L -> "1337")
  }

  test("Equals transitive") {
    val rules =
      Vector(
        Equals(-2L, -1L),
        Literal(-1L, "1337"))
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "1337", -2L -> "1337")
  }


  test("Incomplete solve") {
    val rules =
      Vector(
        OneOf(-1L, Vector("1448", "1337")))
    getConclusions(rules, false) shouldEqual Map()
  }


  test("Half-complete solve") {
    // Note how these two rules aren't connected to each other at all
    val rules =
      Vector(
        OneOf(-1L, Vector("1448", "1337")),
        Literal(-2L, "1337"))
    getConclusions(rules, false) shouldEqual Map(-2L -> "1337")
  }


  test("OneOf") {
    val rules =
      Vector(
        OneOf(-1L, Vector("1448", "1337")),
        Literal(-1L, "1337"))
    getConclusions(rules, true) shouldEqual Map(-1L -> "1337")
  }


  test("Solves a components rule") {
    val rules =
      Vector(
        CoordComponents(-1L, -2L, -3L),
        Literal(-2L, "1337"),
        Literal(-3L, "1448"))
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "1337/1448", -2L -> "1337", -3L -> "1448")
  }


  test("Reverse-solve a components rule") {
    val rules =
      Vector(
        CoordComponents(-1L, -2L, -3L),
        Literal(-1L, "1337/1448"))
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "1337/1448", -2L -> "1337", -3L -> "1448")
  }


  test("Test infer Pack") {
    val rules =
      Vector(
        Literal(-1L, "1337"),
        Literal(-2L, "1448"),
        Pack(-3L, Vector(-1L, -2L)))
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "1337", -2L -> "1448", -3L -> "1337,1448")
  }


  test("Test infer Pack from result") {
    val rules =
      Vector(
        Literal(-3L, "1337,1448"),
        Pack(-3L, Vector(-1L, -2L)))
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "1337", -2L -> "1448", -3L -> "1337,1448")
  }


  test("Test infer Pack from empty result") {
    val rules =
      Vector(
        Literal(-3L, ""),
        Pack(-3L, Vector()))
    getConclusions(rules, true) shouldEqual
      Map(-3L -> "")
  }


  test("Test cant solve empty Pack") {
    val rules =
      Vector(
        Pack(-3L, Vector()))
    getConclusions(rules, false) shouldEqual Map()
  }


  test("Complex rule set") {
    val conclusions = getConclusions(complexRuleSet, true)
    conclusions.get(-7L) shouldEqual Some("1337/1448/1337/1448")
  }


  test("Test receiving struct to struct") {
    val rules =
      Vector(
        Literal(-1L, "Firefly"),
        Send(-2L, -1L))
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "Firefly", -2L -> "Firefly")
  }


  test("Test receive struct from sent interface") {
    val rules =
      Vector(
        Literal(-1L, "Firefly"),
        Literal(-2L, "ISpaceship"),
        Send(-2L, -1L))
    expectSolveFailure(rules) match {
      case FailedSolve(steps, unsolvedRules, err) => {
        steps.flatMap(_.conclusions).toSet shouldEqual
          Set((-1,"Firefly"), (-2,"ISpaceship"), (-2,"Firefly"))
        unsolvedRules.toSet shouldEqual Set(Send(-2, -1))
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
      Vector(
        Literal(-1L, "ISpaceship"),
        Literal(-2L, "Firefly"),
        Send(-2L, -1L))
    // Should be a successful solve
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "ISpaceship", -2L -> "Firefly")
  }


  test("Test complex solve: most specific ancestor") {
    val rules =
      Vector(
        Literal(-2L, "Firefly"),
        Send(-2L, -1L))
    // Should be a successful solve
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "Firefly", -2L -> "Firefly")
  }


  test("Test complex solve: calculate common ancestor") {
    val rules =
      Vector(
        Literal(-2L, "Firefly"),
        Literal(-3L, "Serenity"),
        Send(-2L, -1L),
        Send(-3L, -1L))
    // Should be a successful solve
    getConclusions(rules, true) shouldEqual
      Map(-1L -> "ISpaceship", -2L -> "Firefly", -3L -> "Serenity")
  }


  test("Test complex solve: descendant satisfying call") {
    val rules =
      Vector(
        Literal(-2L, "Flamethrower:int"),
        Send(-2L, -1L),
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
    val interner = new Interner()

    // It'll be nice to partially solve some rules, for example before we put them in the overload index.

    // Note how these two rules aren't connected to each other at all
    val rules =
      Vector(
        Literal(-2, "A"),
        Call(-3, -1, -2)) // We dont know the template, -1, yet


    val solver =
      new Solver(
        true,
        true,
        interner,
        (rule: IRule) => rule.allPuzzles,
        (rule: IRule) => rule.allRunes.toVector,
        new TestRuleSolver(interner),
        List(RangeS.testZero(interner)),
        rules,
        Map())

    while ( {
      solver.advance(Unit, Unit) match {
        case Ok(continue) => continue
        case Err(e) => vfail(e)
      }
    }) {}
    val firstConclusions = solver.userifyConclusions().toMap

    firstConclusions.toMap shouldEqual Map(-2 -> "A")
    solver.markRulesSolved(Vector(), Map(solver.getCanonicalRune(-1) -> "Firefly"))

    while ( {
      solver.advance(Unit, Unit) match {
        case Ok(continue) => continue
        case Err(e) => vfail(e)
      }
    }) {}
    val secondConclusions = solver.userifyConclusions().toMap

    secondConclusions.toMap shouldEqual
      Map(-1 -> "Firefly", -2 -> "A", -3 -> "Firefly:A")
  }
//
//  test("bork") {
//    // It'll be nice to partially solve some rules, for example before we put them in the overload index.
//
//    // Note how these two rules aren't connected to each other at all
//    val rules =
//      Vector(
//        Lookup(-5, "Firefly"),
//        Equals(-2, -5),
//        Send(-1,-5)) // We dont know the template, -1, yet
//    getConclusions(rules, true, Map(-5L -> "Firefly")) shouldEqual
//      Map(-1L -> "ISpaceship", -2L -> "Firefly")
//  }

  test("Predicting") {
    // "Predicting" is when the rules arent completely solvable, but we can still run some of them
    // to figure out what we can.
    // For example, in:
    //   #2 = 1337
    //   #3 = #1<#2>
    // we can figure out that #2 is 1337, even if we don't know #1 yet.
    // This is useful for recursive types.
    // See: Recursive Types Must Have Types Predicted (RTMHTP)

    def solveWithPuzzler(puzzler: IRule => Vector[Vector[Long]]) = {
      val interner = new Interner()

      // Below, we're reporting that Lookup has no puzzles that can solve it.
      val rules =
        Vector(
          Lookup(-1, "Firefly"),
          Literal(-2, "1337"),
          Call(-3, -1, -2)) // X = Firefly<A>

      val solver =
        new Solver[IRule, Long, Unit, Unit, String, String](
          true,
          true,
          interner,
          puzzler,
          (rule: IRule) => rule.allRunes.toVector,
          new TestRuleSolver(interner),
          List(RangeS.testZero(interner)),
          rules,
          Map())


      while ( {
        solver.advance(Unit, Unit) match {
          case Ok(continue) => continue
          case Err(e) => vfail(e)
        }
      }) {}
      val conclusions = solver.userifyConclusions().toMap
      conclusions
    }

    val predictions =
      solveWithPuzzler({
        // This Vector() makes it unsolvable
        case Lookup(rune, name) => Vector()
        case rule => rule.allPuzzles
      })
//    vassert(predictionRuleExecutionOrder sameElements Vector(1))
    vassert(predictions.size == 1)
    vassert(predictions(-2) == "1337")

    val conclusions = solveWithPuzzler(_.allPuzzles)
//    vassert(ruleExecutionOrder.length == 3)
    conclusions shouldEqual Map(-1L -> "Firefly", -2L -> "1337", -3L -> "Firefly:1337")
  }


  test("Test conflict") {
    val rules =
      Vector(
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
    val interner = new Interner()

    val solver =
      new Solver[IRule, Long, Unit, Unit, String, String](
        true,
        true,
        interner,
        (rule: IRule) => rule.allPuzzles,
        (rule: IRule) => rule.allRunes.toVector,
        new TestRuleSolver(interner),
        List(RangeS.testZero(interner)),
        rules,
        Map())
    while ( {
      solver.advance(Unit, Unit) match {
        case Ok(continue) => continue
        case Err(e) => return e
      }
    }) {}
    vfail("Incorrectly completed the solve")
  }

  private def getConclusions(
    rules: IndexedSeq[IRule],
    expectCompleteSolve: Boolean,
    initiallyKnownRunes: Map[Long, String] = Map()):
  Map[Long, String] = {
    val interner = new Interner()

    val solver =
      new Solver[IRule, Long, Unit, Unit, String, String](
        true,
        true,
        interner,
        (r: IRule) => r.allPuzzles,
        (rule: IRule) => rule.allRunes.toVector,
        new TestRuleSolver(interner),
        List(RangeS.testZero(interner)),
        rules,
        initiallyKnownRunes)

    while ( {
      solver.advance(Unit, Unit) match {
        case Ok(continue) => continue
        case Err(e) => vfail(e)
      }
    }) {}
    // If we get here, then there's nothing more the solver can do.
    val conclusionsMap = solver.userifyConclusions().toMap

    vassert(expectCompleteSolve == (conclusionsMap.keySet == rules.flatMap(_.allRunes).toSet))
    conclusionsMap
  }
}
