package net.verdagon.vale.parser.patterns

import net.verdagon.vale.parser.Patterns._
import net.verdagon.vale.parser.VParser._
import net.verdagon.vale.parser._
import net.verdagon.vale.{vfail, vimpl}
import org.scalatest.{FunSuite, Matchers}

class CaptureAndDestructureTests extends FunSuite with Matchers with Collector {
  private def compile[T](parser: VParser.Parser[T], code: String): T = {
    VParser.parse(parser, code.toCharArray()) match {
      case VParser.NoSuccess(msg, input) => {
        fail();
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


  test("Capture with destructure with type inside") {
    compile("a (a Int, b Bool)") shouldHave {
      case PatternPP(_,_,
          Some(CaptureP(_,LocalNameP(StringP(_, "a")),FinalP)),
          None,
          Some(
          DestructureP(_,
            List(
              capturedWithType("a", NameOrRunePT(StringP(_, "Int"))),
              capturedWithType("b", NameOrRunePT(StringP(_, "Bool")))))),
          None) =>
    }
  }
  test("capture with empty sequence type") {
    compile("a []") shouldHave {
      case capturedWithType("a", ManualSequencePT(_,List())) =>
    }
  }
  test("empty destructure") {
    compile(destructure,"()") shouldHave { case List() =>
    }
  }
  test("capture with empty destructure") {
    compile("a ()") shouldHave {
      case PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "a")),FinalP)),None,Some(DestructureP(_,List())),None) =>
    }
  }
  test("Destructure with nested atom") {
    compile("a (b Int)") shouldHave {
      case PatternPP(_,_,
          Some(CaptureP(_,LocalNameP(StringP(_, "a")), FinalP)),
          None,
          Some(
          DestructureP(_,
            List(capturedWithType("b", NameOrRunePT(StringP(_, "Int")))))),
          None) =>
    }
  }
}
