package net.verdagon.vale.solver

import net.verdagon.vale.{Collector, Err, Ok, Result, vassert, vassertSome, vfail, vimpl, vwat}
import org.scalatest.{FunSuite, Matchers}

case class SimpleLookup(lookupInt: Int)
case class SimpleLiteral(value: String)

class RuleTyperTests extends FunSuite with Matchers with Collector {
  def makePuzzler() = {
    new IRunePuzzler[Unit, SimpleLiteral, SimpleLookup] {
      override def getPuzzles(rulexAR: IRulexAR[Int, Unit, SimpleLiteral, SimpleLookup]): Array[Array[Int]] = {
        TemplarPuzzler.apply(rulexAR)
      }
    }
  }

  def makeSolver() = {
    new Solver[Unit, SimpleLiteral, SimpleLookup, Unit, Unit, String, String](
      new ISolverDelegate[Unit, SimpleLiteral, SimpleLookup, Unit, Unit, String, String] {
        override def solve(state: Unit, env: Unit, range: Unit, rule: IRulexAR[Int, Unit, SimpleLiteral, SimpleLookup], runes: Map[Int, String]): Result[Map[Int, String], String] = {
          rule match {
            case LookupAR(range, rune, lookup) => {
              Ok(Map(rune -> ("thingFor" + lookup.lookupInt)))
            }
            case LiteralAR(range, rune, literal) => {
              Ok(Map(rune -> literal.value))
            }
            case OneOfAR(range, rune, literals) => {
              val literal = runes(rune)
              if (literals.map(_.value).contains(literal)) {
                Ok(Map())
              } else {
                Err("conflict!")
              }
            }
            case CoordComponentsAR(_, coordRune, ownershipRune, permissionRune, kindRune) => {
              runes.get(coordRune) match {
                case Some(combined) => {
                  val Array(ownership, permission, kind) = combined.split("/")
                  Ok(Map(ownershipRune -> ownership, permissionRune -> permission, kindRune -> kind))
                }
                case None => {
                  (runes.get(ownershipRune), runes.get(permissionRune), runes.get(kindRune)) match {
                    case (Some(ownership), Some(permission), Some(kind)) => {
                      Ok(Map(coordRune -> (ownership + "/" + permission + "/" + kind)))
                    }
                    case _ => vfail()
                  }
                }
              }
            }
          }
        }
      })
  }

//  def solve(rulesSR: Vector[IRulexSR]): Map[IRuneA, String] = {
//    solveAndGetState(rulesSR)._1
//  }
//
//  def solveAndGetState(rulesSR: Vector[IRulexSR]): (Map[IRuneA, String], RuneWorldSolverState) = {
//    val solver = makeSolver()
//    val (runeToIndex, runeToType, solverState) = RuleFlattener.flattenAndCompileRules(rulesSR)
//    val rawConclusions =
//      solver.solve((), (), solverState, tr).getOrDie()
//    val conclusions = runeToIndex.mapValues(i => vassertSome(rawConclusions(i)))
//    (conclusions, solverState)
//  }

  test("Simple int rule") {
    val builder = RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneA = builder.addRune()
    builder.addRule(LiteralAR(Unit, tentativeRuneA, SimpleLiteral("1337")))
    getSuccessConclusions(builder) shouldEqual Map(tentativeRuneA -> "1337")
  }

  test("Equals transitive") {
    val builder = RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneA = builder.addRune()
    val tentativeRuneB = builder.addRune()
    builder.noteRunesEqual(tentativeRuneA, tentativeRuneB)
    builder.addRule(LiteralAR(Unit, tentativeRuneB, SimpleLiteral("1337")))
    getSuccessConclusions(builder) shouldEqual Map(
      tentativeRuneA -> "1337",
      tentativeRuneB -> "1337")
  }

  test("Incomplete solve") {
    val builder = RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneA = builder.addRune()
    builder.addRule(OneOfAR(Unit, tentativeRuneA, Array(SimpleLiteral("1448"), SimpleLiteral("1337"))))
    getIncompleteConclusions(builder) shouldEqual Map()
  }

  test("Half-complete solve") {
    val builder = RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneA = builder.addRune()
    val tentativeRuneB = builder.addRune()
    builder.addRule(OneOfAR(Unit, tentativeRuneA, Array(SimpleLiteral("1448"), SimpleLiteral("1337"))))
    builder.addRule(LiteralAR(Unit, tentativeRuneB, SimpleLiteral("1337")))
    getIncompleteConclusions(builder) shouldEqual Map(tentativeRuneB -> "1337")
  }

  test("OneOf") {
    val builder = RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneA = builder.addRune()
    builder.addRule(LiteralAR(Unit, tentativeRuneA, SimpleLiteral("1337")))
    builder.addRule(OneOfAR(Unit, tentativeRuneA, Array(SimpleLiteral("1448"), SimpleLiteral("1337"))))
    getSuccessConclusions(builder) shouldEqual Map(
      tentativeRuneA -> "1337")
  }

  test("Solves a components rule") {
    val builder = RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneO = builder.addRune()
    val tentativeRuneR = builder.addRune()
    val tentativeRuneK = builder.addRune()
    val tentativeRuneC = builder.addRune()
    builder.addRule(LiteralAR(Unit, tentativeRuneO, SimpleLiteral("turquoise")))
    builder.addRule(LiteralAR(Unit, tentativeRuneR, SimpleLiteral("bicycle")))
    builder.addRule(LiteralAR(Unit, tentativeRuneK, SimpleLiteral("shoe")))
    builder.addRule(CoordComponentsAR(Unit, tentativeRuneC, tentativeRuneO, tentativeRuneR, tentativeRuneK))
    getSuccessConclusions(builder) shouldEqual Map(
      tentativeRuneO -> "turquoise",
      tentativeRuneR -> "bicycle",
      tentativeRuneK -> "shoe",
      tentativeRuneC -> "turquoise/bicycle/shoe")
  }

  test("Reverse-solve a components rule") {
    val builder = RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneO = builder.addRune()
    val tentativeRuneR = builder.addRune()
    val tentativeRuneK = builder.addRune()
    val tentativeRuneC = builder.addRune()
    builder.addRule(LiteralAR(Unit, tentativeRuneC, SimpleLiteral("turquoise/bicycle/shoe")))
    builder.addRule(CoordComponentsAR(Unit, tentativeRuneC, tentativeRuneO, tentativeRuneR, tentativeRuneK))
    getSuccessConclusions(builder) shouldEqual Map(
      tentativeRuneO -> "turquoise",
      tentativeRuneR -> "bicycle",
      tentativeRuneK -> "shoe",
      tentativeRuneC -> "turquoise/bicycle/shoe")
  }

  test("Test conflict") {
    val builder = RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneO = builder.addRune()
    builder.addRule(LiteralAR(Unit, tentativeRuneO, SimpleLiteral("turquoise")))
    builder.addRule(LiteralAR(Unit, tentativeRuneO, SimpleLiteral("bicycle")))
    getError(builder) match {
      case SolverConflict(_, _, conclusionA, conclusionB) => {
        Vector(conclusionA, conclusionB).sorted shouldEqual Vector("turquoise", "bicycle").sorted
      }
    }
  }

  private def getSuccessConclusions(builder: RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]):
  collection.Map[TentativeRune, String] = {
    getSuccessSolverStateAndConclusions(builder)._2
  }

  private def getSuccessSolverStateAndConclusions(builder: RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]):
  (RuneWorldSolverState[Unit, SimpleLiteral, SimpleLookup], collection.Map[TentativeRune, String]) = {
    val (tentativeRuneToRune, solverState) = RuneWorldOptimizer.optimize(builder, makePuzzler())
    val runeToConclusion = makeSolver().solve(Unit, Unit, solverState, Unit).getOrDie()
    val conclusions = tentativeRuneToRune.mapValues(rune => runeToConclusion(rune).get)
    (solverState, conclusions)
  }

  private def getIncompleteConclusions(builder: RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]):
  collection.Map[TentativeRune, String] = {
    getIncompleteSolverStateAndConclusions(builder)._2
  }

  private def getIncompleteSolverStateAndConclusions(builder: RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]):
  (RuneWorldSolverState[Unit, SimpleLiteral, SimpleLookup], collection.Map[TentativeRune, String]) = {
    val (tentativeRuneToRune, solverState) = RuneWorldOptimizer.optimize(builder, makePuzzler())
    val runeToConclusion =
      makeSolver().solve(Unit, Unit, solverState, Unit) match { case IncompleteSolve(c) => c }
    val conclusions = tentativeRuneToRune.flatMap({ case (tentativeRune, rune) =>
      runeToConclusion(rune) match {
        case None => List()
        case Some(x) => List(tentativeRune -> x)
      }
    }).toMap
    (solverState, conclusions)
  }

  private def getSolverStateAndError(builder: RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]):
  (RuneWorldSolverState[Unit, SimpleLiteral, SimpleLookup], ISolverError[String, String]) = {
    val (tentativeRuneToRune, solverState) = RuneWorldOptimizer.optimize(builder, makePuzzler())
    val err =
      makeSolver().solve(Unit, Unit, solverState, Unit) match {
        case FailedSolve(error, conclusions) => error
      }
    (solverState, err)
  }

  private def getError(builder: RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]): ISolverError[String, String] = {
    getSolverStateAndError(builder)._2
  }
}
