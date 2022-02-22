package net.verdagon.vale.scout

import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.parser._
import net.verdagon.vale.parser.ast.{PointerP, ReadonlyP}
import net.verdagon.vale.scout.patterns.{AtomSP, CaptureS}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.{Collector, Err, FileCoordinate, FileCoordinateMap, Interner, Ok, vassert, vfail, vimpl}
import org.scalatest.{FunSuite, Matchers}

class ScoutParametersTests extends FunSuite with Matchers with Collector {

  private def compile(code: String): ProgramS = {
    Parser.runParser(code) match {
      case Err(err) => fail(err.toString)
      case Ok(program0) => {
        new Scout(GlobalOptions.test(), new Interner())
            .scoutProgram(FileCoordinate.test, program0) match {
          case Err(e) => vfail(ScoutErrorHumanizer.humanize(FileCoordinateMap.test(code), e))
          case Ok(t) => t
        }
      }
    }
  }

  test("Simple rune rule") {
    val program1 = compile("""func main<T>(moo T) infer-ret { }""")
    val main = program1.lookupFunction("main")

    vassert(main.runeToPredictedType.size == 1)

    main.identifyingRunes match {
      case Vector(RuneUsage(_, CodeRuneS("T"))) =>
    }
  }

  test("Returned rune") {
    val program1 = compile("""func main<T>(moo T) T { moo }""")
    val main = program1.lookupFunction("main")

    vassert(main.identifyingRunes.map(_.rune).contains(CodeRuneS("T")))
    main.maybeRetCoordRune match { case Some(RuneUsage(_, CodeRuneS("T"))) => }
  }

  test("Borrowed rune") {
    val program1 = compile("""func main<T>(moo *T) infer-ret { }""")
    val main = program1.lookupFunction("main")
    val Vector(param) = main.params

    val tCoordRuneFromParams =
      param match {
        case ParameterS(
          AtomSP(_,
            Some(CaptureS(CodeVarNameS("moo"))),
            None,
            Some(RuneUsage(_, tcr @ ImplicitRuneS(_))),
            None)) => tcr
      }

    val tCoordRuneFromRules =
      main.rules shouldHave {
        case AugmentSR(_, tcr, PointerP,ReadonlyP, RuneUsage(_, CodeRuneS("T"))) => tcr
      }

    tCoordRuneFromParams shouldEqual tCoordRuneFromRules.rune
  }

  test("Anonymous, typed param") {
    val program1 = compile("""func main(_ int) infer-ret { }""")
    val main = program1.lookupFunction("main")
    val Vector(param) = main.params
    val paramRune =
      param match {
        case ParameterS(
          AtomSP(_,
          None,
            None,
            Some(RuneUsage(_, pr @ ImplicitRuneS(_))),
            None)) => pr
      }

    main.rules shouldHave {
      case LookupSR(_, pr, CodeNameS("int")) => vassert(pr.rune == paramRune)
    }
  }

  test("Rune destructure") {
    // This is an ambiguous case but we decided it should destructure a struct or sequence, see CSTODTS in docs.
    val program1 = compile("""func main<T>(moo T[a int]) infer-ret { }""")
    val main = program1.lookupFunction("main")

    val Vector(param) = main.params

    val (aRune, tRune) =
      param match {
        case ParameterS(
          AtomSP(_,
            Some(CaptureS(CodeVarNameS("moo"))),
            None,
            Some(tr),
            Some(
              Vector(
                AtomSP(_,
                  Some(CaptureS(CodeVarNameS("a"))),
                  None,
                  Some(RuneUsage(_, ar @ ImplicitRuneS(_))),
                None))))) => (ar, tr)
      }

    main.rules shouldHave {
      case LookupSR(_, air, CodeNameS("int")) => vassert(air.rune == aRune)
    }

    // See CCAUIR.
    main.identifyingRunes.map(_.rune) shouldEqual Vector(tRune.rune)
  }

  test("Regioned pure function") {
    val bork = compile("func main<'r ro>(ship 'r *Spaceship) pure 't { }")

    val main = bork.lookupFunction("main")
    // We dont support regions yet, so scout should filter them out.
    main.identifyingRunes.size shouldEqual 0
  }

  test("Test param-less lambda identifying runes") {
    val bork = compile(
      """
        |exported func main() int {do({ ret 3; })}
        |""".stripMargin)

    val main = bork.lookupFunction("main")
    // We dont support regions yet, so scout should filter them out.
    main.identifyingRunes.size shouldEqual 0
    val lambda = Collector.onlyOf(main.body, classOf[FunctionSE])
    lambda.function.identifyingRunes.size shouldEqual 0
  }

  test("Test one-param lambda identifying runes") {
    val bork = compile(
      """
        |exported func main() int {do({ _ })}
        |""".stripMargin)

    val main = bork.lookupFunction("main")
    // We dont support regions yet, so scout should filter them out.
    main.identifyingRunes.size shouldEqual 0
    val lambda = Collector.onlyOf(main.body, classOf[FunctionSE])
    lambda.function.identifyingRunes.size shouldEqual 1
  }

  test("Test one-anonymous-param lambda identifying runes") {
    val bork = compile(
      """
        |exported func main() int {do((_) => { true })}
        |""".stripMargin)

    val main = bork.lookupFunction("main")
    // We dont support regions yet, so scout should filter them out.
    main.identifyingRunes.size shouldEqual 0
    val lambda = Collector.onlyOf(main.body, classOf[FunctionSE])
    lambda.function.identifyingRunes.size shouldEqual 1
  }

}
