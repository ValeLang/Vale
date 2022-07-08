package dev.vale.postparsing

import dev.vale.{Collector, Err, FileCoordinateMap, Interner, Ok, StrI, vassert, vfail}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.BorrowP
import dev.vale.postparsing.patterns.{AtomSP, CaptureS}
import dev.vale.postparsing.rules.{AugmentSR, LookupSR, RuneUsage}
import dev.vale.parsing._
import dev.vale.parsing.ast._
import dev.vale.postparsing.patterns.AtomSP
import dev.vale.postparsing.rules._
import org.scalatest.{FunSuite, Matchers}

class PostParsingParametersTests extends FunSuite with Matchers with Collector {

  private def compile(code: String, interner: Interner = new Interner()): ProgramS = {
    val compilation = PostParserTestCompilation.test(code, interner)
    compilation.getScoutput() match {
      case Err(e) => vfail(PostParserErrorHumanizer.humanize(compilation.getCodeMap().getOrDie(), e))
      case Ok(t) => t.expectOne()
    }
  }

  test("Simple rune rule") {
    val program1 = compile("""func main<T>(moo T) infer-return { }""")
    val main = program1.lookupFunction("main")

    vassert(main.runeToPredictedType.size == 1)

    main.identifyingRunes match {
      case Vector(RuneUsage(_, CodeRuneS(StrI("T")))) =>
    }
  }

  test("Returned rune") {
    val interner = new Interner()
    val program1 = compile("""func main<T>(moo T) T { moo }""", interner)
    val main = program1.lookupFunction("main")

    vassert(main.identifyingRunes.map(_.rune).contains(CodeRuneS(interner.intern(StrI("T")))))
    main.maybeRetCoordRune match { case Some(RuneUsage(_, CodeRuneS(StrI("T")))) => }
  }

  test("Borrowed rune") {
    val program1 = compile("""func main<T>(moo &T) infer-return { }""")
    val main = program1.lookupFunction("main")
    val Vector(param) = main.params

    val tCoordRuneFromParams =
      param match {
        case ParameterS(
          AtomSP(_,
            Some(CaptureS(CodeVarNameS(StrI("moo")))),
            None,
            Some(RuneUsage(_, tcr @ ImplicitRuneS(_))),
            None)) => tcr
      }

    val tCoordRuneFromRules =
      main.rules shouldHave {
        case AugmentSR(_, tcr, BorrowP, RuneUsage(_, CodeRuneS(StrI("T")))) => tcr
      }

    tCoordRuneFromParams shouldEqual tCoordRuneFromRules.rune
  }

  test("Anonymous, typed param") {
    val program1 = compile("""func main(_ int) infer-return { }""")
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
      case LookupSR(_, pr, CodeNameS(StrI("int"))) => vassert(pr.rune == paramRune)
    }
  }

  test("Regioned pure function") {
    val bork = compile("pure func main<'r>(ship 'r &Spaceship) 't { }")

    val main = bork.lookupFunction("main")
    // We dont support regions yet, so scout should filter them out.
    main.identifyingRunes.size shouldEqual 0
  }

  test("Test param-less lambda identifying runes") {
    val bork = compile(
      """
        |exported func main() int {do({ return 3; })}
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
