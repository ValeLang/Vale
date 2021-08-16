package net.verdagon.vale.parser.patterns

import net.verdagon.vale.parser.Patterns._
import net.verdagon.vale.parser.CombinatorParsers._
import net.verdagon.vale.parser._
import net.verdagon.vale.{vfail, vimpl}
import org.scalatest.{FunSuite, Matchers}

class CaptureAndDestructureTests extends FunSuite with Matchers with Collector {
  private def compile[T](parser: CombinatorParsers.Parser[T], code: String): T = {
    CombinatorParsers.parse(parser, code.toCharArray()) match {
      case CombinatorParsers.NoSuccess(msg, input) => {
        fail();
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


  test("Capture with destructure with type inside") {
    compile("a (a int, b bool)") shouldHave {
      case PatternPP(_,_,
          Some(CaptureP(_,LocalNameP(NameP(_, "a")))),
          None,
          Some(
          DestructureP(_,
            Vector(
              capturedWithType("a", NameOrRunePT(NameP(_, "int"))),
              capturedWithType("b", NameOrRunePT(NameP(_, "bool")))))),
          None) =>
    }
  }
  test("capture with empty sequence type") {
    compile("a []") shouldHave {
      case capturedWithType("a", ManualSequencePT(_,Vector())) =>
    }
  }
  test("empty destructure") {
    compile(destructure,"()") shouldHave { case Nil =>
    }
  }
  test("capture with empty destructure") {
    compile("a ()") shouldHave {
      case PatternPP(_,_,Some(CaptureP(_,LocalNameP(NameP(_, "a")))),None,Some(DestructureP(_,Vector())),None) =>
    }
  }
  test("Destructure with nested atom") {
    compile("a (b int)") shouldHave {
      case PatternPP(_,_,
          Some(CaptureP(_,LocalNameP(NameP(_, "a")))),
          None,
          Some(
          DestructureP(_,
            Vector(capturedWithType("b", NameOrRunePT(NameP(_, "int")))))),
          None) =>
    }
  }
}
