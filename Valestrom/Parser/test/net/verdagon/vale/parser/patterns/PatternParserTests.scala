package net.verdagon.vale.parser.patterns

import net.verdagon.vale.parser.Patterns._
import net.verdagon.vale.parser.VParser._
import net.verdagon.vale.parser._
import net.verdagon.vale.{vfail, vimpl}
import org.scalatest.{FunSuite, Matchers}

class PatternParserTests extends FunSuite with Matchers with Collector {
  private def compile[T](parser: VParser.Parser[T], code: String): T = {
    VParser.parse(parser, code.toCharArray()) match {
      case VParser.NoSuccess(msg, input) => {
        fail(msg);
      }
      case VParser.Success(expr, rest) => {
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

  private def checkFail[T](parser: VParser.Parser[T], code: String) = {
    VParser.parse(parser, code) match {
      case VParser.NoSuccess(_, _) =>
      case VParser.Success(_, rest) => {
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
    compile(typeIdentifier,"Int") shouldHave { case "Int" => }
    compile(runeOrKindPattern,"Int") shouldHave { case NameOrRunePT(StringP(_, "Int")) => }
    compile(patternType,"Int") shouldHave { case PatternTypePPI(None, NameOrRunePT(StringP(_, "Int"))) => }
    compile(atomPattern,"_ Int") shouldHave { case Patterns.fromEnv("Int") => }
  }
  test("Pattern Templexes") {
    compile(patternType,"Int") shouldHave { case PatternTypePPI(None, NameOrRunePT(StringP(_, "Int"))) => }
    compile(patternType,"*Int") shouldHave { case PatternTypePPI(Some(ShareP), NameOrRunePT(StringP(_, "Int"))) => }
  }
  test("Name-only Capture") {
    compile(atomPattern,"a") match {
      case PatternPP(_, _,Some(CaptureP(_,LocalNameP(StringP(_, "a")), FinalP)), None, None, None) =>
    }
  }
  test("Empty pattern list") {
    compile(patternPrototypeParams,"()").patterns shouldEqual List()
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
          Some(CaptureP(_,LocalNameP(StringP(_, "a")),FinalP)),
          Some(NameOrRunePT(StringP(_, "Moo"))),
          Some(DestructureP(_,List(capture("a"),capture("b")))),
          None) =>
    }
  }


  test("CSTODTS") {
    // This tests us handling an ambiguity properly, see CSTODTS in docs.
    compile("moo T(a Int)") shouldHave {
      case PatternPP(
          _,_,
          Some(CaptureP(_,LocalNameP(StringP(_, "moo")),FinalP)),
          Some(NameOrRunePT(StringP(_, "T"))),
          Some(DestructureP(_,List(PatternPP(_,_, Some(CaptureP(_,LocalNameP(StringP(_, "a")),FinalP)),Some(NameOrRunePT(StringP(_, "Int"))),None,None)))),
          None) =>
    }
  }

  test("Capture with destructure with type outside") {
    compile("a [Int, Bool](a, b)") shouldHave {
      case PatternPP(
          _,_,
          Some(CaptureP(_,LocalNameP(StringP(_, "a")),FinalP)),
          Some(
            ManualSequencePT(_,
                  List(
                    NameOrRunePT(StringP(_, "Int")),
                    NameOrRunePT(StringP(_, "Bool"))))),
          Some(DestructureP(_,List(capture("a"), capture("b")))),
          None) =>
    }
  }

  test("Virtual function") {
    compile(VParser.atomPattern, "virtual this Car") shouldHave {
      case PatternPP(_, _,Some(CaptureP(_,LocalNameP(StringP(_, "this")),FinalP)),Some(NameOrRunePT(StringP(_, "Car"))),None,Some(AbstractP)) =>
    }
  }
}
