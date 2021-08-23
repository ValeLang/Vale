package net.verdagon.vale.solver

import net.verdagon.vale.{Collector, Ok, Result, vassert, vassertSome, vfail, vimpl, vwat}
import org.scalatest.{FunSuite, Matchers}

case class SimpleLookup(lookupInt: Int)
case class SimpleLiteral(value: String)

class RuleTyperTests extends FunSuite with Matchers with Collector {
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
            case CoordComponentsAR(_, coordRune, ownershipRune, permissionRune, kindRune) => {
//              val maybeCoord = runes.get(coordRune)
//              val maybeOwnership = runes.get(ownershipRune)
//              val maybePermission = runes.get(permissionRune)
//              val maybeKind = runes.get(kindRune)
//
//              (maybeCoord, maybeOwnership, maybePermission, maybeKind) match {
//                case (Some(coord), Some(ownership), Some(permission), Some(kind)) => {
//
//                }
//                case (None, Some(ownership), Some(permission), Some(kind)) => {
//
//                }
//                case (Some(coord), _, _, _) => {
//                  val Array(ownership, permission, kind) = coord.split("/")
//                  Ok(Map(ownershipRune -> ownership, permissionRune -> permission, kindRune -> kind))
//                }
//                case _ => vwat()
//              }
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
    getConclusions(builder) shouldEqual Map(tentativeRuneA -> "1337")
  }

  test("Equals transitive") {
    val builder = RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]()
    val tentativeRuneA = builder.addRune()
    val tentativeRuneB = builder.addRune()
    builder.noteRunesEqual(tentativeRuneA, tentativeRuneB)
    builder.addRule(LiteralAR(Unit, tentativeRuneB, SimpleLiteral("1337")))
    getConclusions(builder) shouldEqual Map(
      tentativeRuneA -> "1337",
      tentativeRuneB -> "1337")
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
    getConclusions(builder) shouldEqual Map(
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
    getConclusions(builder) shouldEqual Map(
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

//  test("Reverse-solves a components rule") {
//    val rules =
//      Vector(
//        EqualsSR(tr, RuneSR(tr, CodeRuneS("C")), StringSR(tr, "turquoise/bicycle/shoe")),
//        ComponentsSR(tr,
//          TypedSR(tr, CodeRuneS("C"), CoordTypeSR),
//          Vector(RuneSR(tr, CodeRuneS("X")), RuneSR(tr, CodeRuneS("Y")), RuneSR(tr, CodeRuneS("Z")))))
//    solve(rules) shouldEqual Map(
//      CodeRuneA("X") -> "turquoise",
//      CodeRuneA("Y") -> "bicycle",
//      CodeRuneA("Z") -> "shoe",
//      CodeRuneA("C") -> "turquoise/bicycle/shoe")
//  }

  private def getConclusions(builder: RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]):
  collection.Map[TentativeRune, String] = {
    getSolverStateAndConclusions(builder)._2
  }

  private def getSolverStateAndConclusions(builder: RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]):
  (RuneWorldSolverState[Unit, SimpleLiteral, SimpleLookup], collection.Map[TentativeRune, String]) = {
    val (tentativeRuneToRune, solverState) = RuneWorldOptimizer.optimize(builder)
    val runeToConclusion = makeSolver().solve(Unit, Unit, solverState, Unit).getOrDie()
    val conclusions = tentativeRuneToRune.mapValues(rune => runeToConclusion(rune).get)
    (solverState, conclusions)
  }

  private def getSolverStateAndError(builder: RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]):
  (RuneWorldSolverState[Unit, SimpleLiteral, SimpleLookup], ISolverError[String, String]) = {
    val (tentativeRuneToRune, solverState) = RuneWorldOptimizer.optimize(builder)
    val err = makeSolver().solve(Unit, Unit, solverState, Unit).expectErr()
    (solverState, err)
  }

  private def getError(builder: RuneWorldBuilder[Unit, SimpleLiteral, SimpleLookup]): ISolverError[String, String] = {
    getSolverStateAndError(builder)._2
  }
}
