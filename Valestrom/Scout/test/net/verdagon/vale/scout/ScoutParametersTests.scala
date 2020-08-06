package net.verdagon.vale.scout

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.patterns.{AtomSP, CaptureS}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.{vassert, vfail}
import org.scalatest.{FunSuite, Matchers}

class ScoutParametersTests extends FunSuite with Matchers {

  private def compile(code: String): ProgramS = {
    Parser.runParser(code) match {
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(program0) => Scout.scoutProgram(List(program0))
    }
  }

  test("Simple rune rule") {
    val program1 = compile("""fn main<T>(moo T) { }""")
    val main = program1.lookupFunction("main")

    val runeInRules =
      main.templateRules match {
        case List(TypedSR(rune @ CodeRuneS("T"),CoordTypeSR)) => rune
      }
    RuleSUtils.getDistinctOrderedRunesForRulexes(main.templateRules) match {
      case List(runeFromFunc) => vassert(runeInRules == runeFromFunc)
    }
  }

  test("Borrowed rune") {
    val program1 = compile("""fn main<T>(moo &T) { }""")
    val main = program1.lookupFunction("main")
    val List(param) = main.params

    val tCoordRune =
      param match {
        case ParameterS(
          AtomSP(
            CaptureS(CodeVarNameS("moo"),FinalP),
            None,
            tcr @ ImplicitRuneS(_, 1),
            None)) => tcr
      }

    main.templateRules match {
      case List(
        TypedSR(ImplicitRuneS(_, 0),KindTypeSR),
        TypedSR(CodeRuneS("T"),CoordTypeSR),
        TypedSR(ImplicitRuneS(_, 1),CoordTypeSR),
        ComponentsSR(
          TypedSR(CodeRuneS("T"),CoordTypeSR),
          List(
            TemplexSR(OwnershipST(OwnP)),
            TemplexSR(RuneST(ImplicitRuneS(_, 0))))),
        ComponentsSR(
          TypedSR(ImplicitRuneS(_, 1),CoordTypeSR),
          List(
            TemplexSR(OwnershipST(BorrowP)),
            TemplexSR(RuneST(ImplicitRuneS(_, 0)))))) =>
    }

    RuleSUtils.getDistinctOrderedRunesForRulexes(main.templateRules) match {
      case List(
        ImplicitRuneS(_, 0),
          CodeRuneS("T"),
          ImplicitRuneS(_, 1)) =>
    }
  }

  test("Anonymous typed param") {
    val program1 = compile("""fn main(_ int) { }""")
    val main = program1.lookupFunction("main")
    val List(param) = main.params
    val paramRune =
      param match {
        case ParameterS(
          AtomSP(
            CaptureS(UnnamedLocalNameS(_),FinalP),
            None,
            pr @ ImplicitRuneS(_, 0),
            None)) => pr
      }

    main.templateRules match {
      case List(
        EqualsSR(
          TypedSR(pr,CoordTypeSR),
          TemplexSR(NameST(_, CodeTypeNameS("int"))))) => {
        vassert(pr == paramRune)
      }
    }

    RuleSUtils.getDistinctOrderedRunesForRulexes(main.templateRules) shouldEqual
      List(paramRune)
  }

  test("Anonymous untyped param") {
    val program1 = compile("""fn main(_) { }""")
    val main = program1.lookupFunction("main")
    val List(param) = main.params
    val paramRune =
      param match {
        case ParameterS(
         AtomSP(
          CaptureS(UnnamedLocalNameS(_),FinalP),
          None,
          pr @ ImplicitRuneS(_, 0),
          None)) => pr
      }

    main.templateRules match {
      case List(TypedSR(pr,CoordTypeSR)) => {
        vassert(pr == paramRune)
      }
    }

    RuleSUtils.getDistinctOrderedRunesForRulexes(main.templateRules) shouldEqual
      List(paramRune)
  }

  test("Rune destructure") {
    // This is an ambiguous case but we decided it should destructure a struct or sequence, see CSTODTS in docs.
    val program1 = compile("""fn main<T>(moo T(a int)) { }""")
    val main = program1.lookupFunction("main")

    val List(param) = main.params

    val (aRune, tRune) =
      param match {
        case ParameterS(
            AtomSP(
              CaptureS(CodeVarNameS("moo"),FinalP),
              None,
              tr @ CodeRuneS("T"),
              Some(
                List(
                  AtomSP(
                    CaptureS(CodeVarNameS("a"),FinalP),
                    None,
                    ar @ ImplicitRuneS(_, 0),
                    None))))) => (ar, tr)
      }

    main.templateRules match {
      case List(
        TypedSR(tr,CoordTypeSR),
        EqualsSR(
          TypedSR(ar,CoordTypeSR),
          TemplexSR(NameST(_, CodeTypeNameS("int"))))) => {
        vassert(tr == tRune)
        vassert(ar == aRune)
      }
    }

    RuleSUtils.getDistinctOrderedRunesForRulexes(main.templateRules) shouldEqual
      List(tRune, aRune)

    // See CCAUIR.
    main.identifyingRunes shouldEqual List(tRune)
  }
}
