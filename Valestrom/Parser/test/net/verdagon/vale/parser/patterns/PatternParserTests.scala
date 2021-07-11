package net.verdagon.vale.parser.patterns

import net.verdagon.vale.parser.Patterns._
import net.verdagon.vale.parser.CombinatorParsers._
import net.verdagon.vale.parser._
import net.verdagon.vale.{vfail, vimpl}
import org.scalatest.{FunSuite, Matchers}

class PatternParserTests extends FunSuite with Matchers with Collector {
  private def compile[T](parser: CombinatorParsers.Parser[T], code: String): T = {
    CombinatorParsers.parse(parser, code.toCharArray()) match {
      case CombinatorParsers.NoSuccess(msg, input) => {
        fail(msg);
      }
      case CombinatorParsers.Success(expr, rest) => {
        if (!rest.atEnd) {
          vfail(rest.pos.longString)
        }
        expr
      }
    }
  }
  private def compile[T](code: String): PatternPP = {
    compile(atomPattern, code)
  }

  private def checkFail[T](parser: CombinatorParsers.Parser[T], code: String) = {
    CombinatorParsers.parse(parser, code) match {
      case CombinatorParsers.NoSuccess(_, _) =>
      case CombinatorParsers.Success(_, rest) => {
        if (!rest.atEnd) {
          // That's good, it didn't parse all of it
        } else {
          fail()
        }
      }
    }
  }

  test("Simple Int") {
    // Make sure every pattern on the way down to kind can match Int
    compile(typeIdentifier,"int") shouldHave { case "int" => }
    compile(runeOrKindPattern,"int") shouldHave { case NameOrRunePT(NameP(_, "int")) => }
    compile(patternType,"int") shouldHave { case PatternTypePPI(None, NameOrRunePT(NameP(_, "int"))) => }
    compile(atomPattern,"_ int") shouldHave { case Patterns.fromEnv("int") => }
  }
  test("Pattern Templexes") {
    compile(patternType,"int") shouldHave { case PatternTypePPI(None, NameOrRunePT(NameP(_, "int"))) => }
    compile(patternType,"*int") shouldHave { case PatternTypePPI(Some(ShareP), NameOrRunePT(NameP(_, "int"))) => }
  }
  test("Name-only Capture") {
    compile(atomPattern,"a") match {
      case PatternPP(_, _,Some(CaptureP(_,LocalNameP(NameP(_, "a")))), None, None, None) =>
    }
  }
  test("Empty pattern list") {
    compile(patternPrototypeParams,"()").patterns shouldEqual List.empty
  }
  test("Pattern list with only two captures") {
    val list = compile(patternPrototypeParams, "(a, b)")
    list.patterns shouldHave {
      case List(capture("a"), capture("b")) =>
    }
  }
  test("Simple pattern doesn't eat = after it") {
    compile(atomPattern, "a Int")
    checkFail(atomPattern, "a Int=")
    checkFail(atomPattern, "a Int =")
    checkFail(atomPattern, "a Int = m")
    checkFail(atomPattern, "a Int = m;")
  }
  test("Empty pattern") {
    compile("_") match { case PatternPP(_,_, None,None,None,None) => }
  }

  test("Capture with type with destructure") {
    compile("a Moo(a, b)") shouldHave {
      case PatternPP(
          _,_,
          Some(CaptureP(_,LocalNameP(NameP(_, "a")))),
          Some(NameOrRunePT(NameP(_, "Moo"))),
          Some(DestructureP(_,List(capture("a"),capture("b")))),
          None) =>
    }
  }


  test("CSTODTS") {
    // This tests us handling an ambiguity properly, see CSTODTS in docs.
    compile("moo T(a int)") shouldHave {
      case PatternPP(
          _,_,
          Some(CaptureP(_,LocalNameP(NameP(_, "moo")))),
          Some(NameOrRunePT(NameP(_, "T"))),
          Some(DestructureP(_,List(PatternPP(_,_, Some(CaptureP(_,LocalNameP(NameP(_, "a")))),Some(NameOrRunePT(NameP(_, "int"))),None,None)))),
          None) =>
    }
  }

  test("Capture with destructure with type outside") {
    compile("a [int, bool](a, b)") shouldHave {
      case PatternPP(
          _,_,
          Some(CaptureP(_,LocalNameP(NameP(_, "a")))),
          Some(
            ManualSequencePT(_,
                  List(
                    NameOrRunePT(NameP(_, "int")),
                    NameOrRunePT(NameP(_, "bool"))))),
          Some(DestructureP(_,List(capture("a"), capture("b")))),
          None) =>
    }
  }

  test("Virtual function") {
    compile(CombinatorParsers.atomPattern, "virtual this Car") shouldHave {
      case PatternPP(_, _,Some(CaptureP(_,LocalNameP(NameP(_, "this")))),Some(NameOrRunePT(NameP(_, "Car"))),None,Some(AbstractP)) =>
    }
  }
}
