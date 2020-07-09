package net.verdagon.vale.parser.patterns

import net.verdagon.vale.parser.Patterns.{capturedWithType, capturedWithTypeRune}
import net.verdagon.vale.parser.VParser._
import net.verdagon.vale.parser._
import net.verdagon.vale.vfail
import org.scalatest.{FunSuite, Matchers}

class CaptureAndTypeTests extends FunSuite with Matchers with Collector {
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

  test("No capture, with type") {
    compile("_ int") shouldHave {
      case PatternPP(_,None, _, Some(NameOrRunePT(StringP(_, "int"))), None, None) =>
    }
  }
  test("Capture with type") {
    compile("a int") shouldHave {
      case capturedWithType("a", NameOrRunePT(StringP(_, "int"))) =>
    }
  }
  test("Simple capture with tame") {
    compile("a T") shouldHave {
      case capturedWithTypeRune("a","T") =>
    }
  }
  test("Capture with borrow tame") {
    compile("arr &R") shouldHave {
      case PatternPP(_,_,
      Some(CaptureP(_,LocalNameP(StringP(_, "arr")),FinalP)),
      Some(OwnershippedPT(_,BorrowP, NameOrRunePT(StringP(_, "R")))),
      None,
      None) =>
    }
  }
  test("Capture with this. in front") {
    compile("this.arr &R") shouldHave {
      case PatternPP(_,_,
      Some(CaptureP(_, ConstructingMemberNameP(StringP(_, "arr")),FinalP)),
      Some(OwnershippedPT(_,BorrowP, NameOrRunePT(StringP(_, "R")))),
      None,
      None) =>
    }
  }
}
