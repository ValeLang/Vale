package net.verdagon.vale.parser.patterns

import net.verdagon.vale.parser.Patterns.{capturedWithType, capturedWithTypeRune}
import net.verdagon.vale.parser.CombinatorParsers._
import net.verdagon.vale.parser._
import net.verdagon.vale.vfail
import org.scalatest.{FunSuite, Matchers}

class CaptureAndTypeTests extends FunSuite with Matchers with Collector {
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

  test("No capture, with type") {
    compile("_ int") shouldHave {
      case PatternPP(_,None, _, Some(NameOrRunePT(NameP(_, "int"))), None, None) =>
    }
  }
  test("Capture with type") {
    compile("a int") shouldHave {
      case capturedWithType("a", NameOrRunePT(NameP(_, "int"))) =>
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
      Some(CaptureP(_,LocalNameP(NameP(_, "arr")))),
      Some(InterpretedPT(_,ConstraintP,ReadonlyP, NameOrRunePT(NameP(_, "R")))),
      None,
      None) =>
    }
  }
  test("Capture with this. in front") {
    compile("this.arr &&R") shouldHave {
      case PatternPP(_,_,
      Some(CaptureP(_, ConstructingMemberNameP(NameP(_, "arr")))),
      Some(InterpretedPT(_,WeakP,ReadonlyP, NameOrRunePT(NameP(_, "R")))),
      None,
      None) =>
    }
  }
}
