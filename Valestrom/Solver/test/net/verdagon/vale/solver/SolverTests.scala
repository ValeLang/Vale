package net.verdagon.vale.solver

import net.verdagon.vale.{Collector, Err, Ok, Result, vassert, vassertSome, vfail, vimpl, vwat}
import org.scalatest.{FunSuite, Matchers}

case class SimpleLookup(lookupInt: Int)
case class SimpleLiteral(value: String)

class SolverTests extends FunSuite with Matchers with Collector {
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
        override def solve(state: Unit, env: Unit, range: Unit, rule: IRulexAR[Int, Unit, SimpleLiteral, SimpleLookup], runes: Int => Option[String]): Result[Map[Int, String], String] = {
          rule match {
            case LookupAR(range, rune, lookup) => {
              Ok(Map(rune -> ("thingFor" + lookup.lookupInt)))
            }
            case LiteralAR(range, rune, literal) => {
              Ok(Map(rune -> literal.value))
            }
            case OneOfAR(range, rune, literals) => {
              val literal = runes(rune).get
              if (literals.map(_.value).contains(literal)) {
                Ok(Map())
              } else {
                Err("conflict!")
              }
            }
            case CoordComponentsAR(_, coordRune, ownershipRune, permissionRune, kindRune) => {
              runes(coordRune) match {
                case Some(combined) => {
                  val Array(ownership, permission, kind) = combined.split("/")
                  Ok(Map(ownershipRune -> ownership, permissionRune -> permission, kindRune -> kind))
                }
                case None => {
                  (runes(ownershipRune), runes(permissionRune), runes(kindRune)) match {
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
//  def solveAndGetState(rulesSR: Vector[IRulexSR]): (Map[IRuneA, String], PlannerState) = {
//    val solver = makeSolver()
//    val (runeToIndex, runeToType, plannerState) = RuleFlattener.flattenAndCompileRules(rulesSR)
//    val rawConclusions =
//      solver.solve((), (), plannerState, tr).getOrDie()
//    val conclusions = runeToIndex.mapValues(i => vassertSome(rawConclusions(i)))
//    (conclusions, plannerState)
//  }

  test("Simple int rule") {
    val builder = Builder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneA = builder.addRune()
    builder.addRule(LiteralAR(Unit, tentativeRuneA, SimpleLiteral("1337")))
    getConclusions(builder, true) shouldEqual Map(tentativeRuneA -> "1337")
  }

  test("Equals transitive") {
    val builder = Builder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneA = builder.addRune()
    val tentativeRuneB = builder.addRune()
    builder.noteRunesEqual(tentativeRuneA, tentativeRuneB)
    builder.addRule(LiteralAR(Unit, tentativeRuneB, SimpleLiteral("1337")))
    getConclusions(builder, true) shouldEqual Map(
      tentativeRuneA -> "1337",
      tentativeRuneB -> "1337")
  }

  test("Incomplete solve") {
    val builder = Builder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneA = builder.addRune()
    builder.addRule(OneOfAR(Unit, tentativeRuneA, Array(SimpleLiteral("1448"), SimpleLiteral("1337"))))
    getConclusions(builder, false) shouldEqual Map()
  }

  test("Half-complete solve") {
    val builder = Builder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneA = builder.addRune()
    val tentativeRuneB = builder.addRune()
    builder.addRule(OneOfAR(Unit, tentativeRuneA, Array(SimpleLiteral("1448"), SimpleLiteral("1337"))))
    builder.addRule(LiteralAR(Unit, tentativeRuneB, SimpleLiteral("1337")))
    getConclusions(builder, false) shouldEqual Map(tentativeRuneB -> "1337")
  }

  test("OneOf") {
    val builder = Builder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneA = builder.addRune()
    builder.addRule(LiteralAR(Unit, tentativeRuneA, SimpleLiteral("1337")))
    builder.addRule(OneOfAR(Unit, tentativeRuneA, Array(SimpleLiteral("1448"), SimpleLiteral("1337"))))
    getConclusions(builder, true) shouldEqual Map(
      tentativeRuneA -> "1337")
  }

  test("Solves a components rule") {
    val builder = Builder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneO = builder.addRune()
    val tentativeRuneR = builder.addRune()
    val tentativeRuneK = builder.addRune()
    val tentativeRuneC = builder.addRune()
    builder.addRule(LiteralAR(Unit, tentativeRuneO, SimpleLiteral("turquoise")))
    builder.addRule(LiteralAR(Unit, tentativeRuneR, SimpleLiteral("bicycle")))
    builder.addRule(LiteralAR(Unit, tentativeRuneK, SimpleLiteral("shoe")))
    builder.addRule(CoordComponentsAR(Unit, tentativeRuneC, tentativeRuneO, tentativeRuneR, tentativeRuneK))
    getConclusions(builder, true) shouldEqual Map(
      tentativeRuneO -> "turquoise",
      tentativeRuneR -> "bicycle",
      tentativeRuneK -> "shoe",
      tentativeRuneC -> "turquoise/bicycle/shoe")
  }

  test("Reverse-solve a components rule") {
    val builder = Builder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneO = builder.addRune()
    val tentativeRuneR = builder.addRune()
    val tentativeRuneK = builder.addRune()
    val tentativeRuneC = builder.addRune()
    builder.addRule(LiteralAR(Unit, tentativeRuneC, SimpleLiteral("turquoise/bicycle/shoe")))
    builder.addRule(CoordComponentsAR(Unit, tentativeRuneC, tentativeRuneO, tentativeRuneR, tentativeRuneK))
    getConclusions(builder, true) shouldEqual Map(
      tentativeRuneO -> "turquoise",
      tentativeRuneR -> "bicycle",
      tentativeRuneK -> "shoe",
      tentativeRuneC -> "turquoise/bicycle/shoe")
  }

  test("Test conflict") {
    val builder = Builder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneO = builder.addRune()
    builder.addRule(LiteralAR(Unit, tentativeRuneO, SimpleLiteral("turquoise")))
    builder.addRule(LiteralAR(Unit, tentativeRuneO, SimpleLiteral("bicycle")))
    expectSolveFailure(builder) match {
      case FailedSolve(SolverConflict(_, _, conclusionA, conclusionB), _) => {
        Vector(conclusionA, conclusionB).sorted shouldEqual Vector("turquoise", "bicycle").sorted
      }
    }
  }

  private def expectSolveFailure(builder: Builder[Unit, SimpleLiteral, SimpleLookup]):
  FailedSolve[String, String] = {
    val (orderedCanonicalRules, tentativeRuneToCanonicalRune, canonicalRuneToIsSolved) =
      Optimizer.optimize(builder, makePuzzler())
    makeSolver().solve(Unit, Unit, orderedCanonicalRules, canonicalRuneToIsSolved.length, Unit) match {
      case Ok(c) => vfail(c)
      case Err(e) => e
    }
  }

  private def getConclusions(
    builder: Builder[Unit, SimpleLiteral, SimpleLookup],
    expectCompleteSolve: Boolean):
  Map[TentativeRune, String] = {
    val (orderedCanonicalRules, tentativeRuneToCanonicalRune, canonicalRuneToIsSolved) =
      Optimizer.optimize(builder, makePuzzler())
    val canonicalRuneToConclusion =
      makeSolver().solve(Unit, Unit, orderedCanonicalRules, canonicalRuneToIsSolved.length, Unit) match {
        case Ok(c) => c
        case Err(e) => vfail(e)
      }
    val conclusions =
      tentativeRuneToCanonicalRune.flatMap({ case (tentativeRune, canonicalRune) =>
        canonicalRuneToConclusion(canonicalRune) match {
          case None => List()
          case Some(x) => List(tentativeRune -> x)
        }
      }).toMap
    vassert(expectCompleteSolve == !canonicalRuneToIsSolved.contains(false))
    conclusions
  }
}
