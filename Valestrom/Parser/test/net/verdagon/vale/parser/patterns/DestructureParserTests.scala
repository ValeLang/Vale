package net.verdagon.vale.parser.patterns

import net.verdagon.vale.parser.Patterns._
import net.verdagon.vale.parser.CombinatorParsers._
import net.verdagon.vale.parser._
import net.verdagon.vale.{Collector, vfail, vimpl}
import org.scalatest.{FunSuite, Matchers}

class DestructureParserTests extends FunSuite with Matchers with Collector {
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

  test("Only empty destructure") {
    compile("()") shouldHave {
      case withDestructure(Vector()) =>
    }
  }
  test("One element destructure") {
    compile("(a)") shouldHave {
      case withDestructure(Vector(capture("a"))) =>
    }
  }
  test("One typed element destructure") {
    compile("( _ A )") shouldHave {
      case withDestructure(Vector(withType(NameOrRunePT(NameP(_, "A"))))) =>
    }
  }
  test("Only two-element destructure") {
    compile("(a, b)") shouldHave {
      case withDestructure(Vector(capture("a"), capture("b"))) =>
    }
  }
  test("Two-element destructure with ignore") {
    compile("(_, b)") shouldHave {
      case PatternPP(_,_,
          None,None,
          Some(DestructureP(_,Vector(PatternPP(_,_,None, None, None, None), capture("b")))),
          None) =>
    }
  }
  test("Capture with destructure") {
    compile("a (x, y)") shouldHave {
      case PatternPP(_,_,
        Some(CaptureP(_,LocalNameP(NameP(_, "a")))),
        None,
        Some(DestructureP(_,Vector(capture("x"), capture("y")))),
        None) =>
    }
  }
  test("Type with destructure") {
    compile("A(a, b)") shouldHave {
      case PatternPP(_,_,
        None,
        Some(NameOrRunePT(NameP(_, "A"))),
        Some(DestructureP(_,Vector(capture("a"), capture("b")))),
        None) =>
    }
  }
  test("Capture and type with destructure") {
    compile("a A(x, y)") shouldHave {
      case PatternPP(_,_,
        Some(CaptureP(_,LocalNameP(NameP(_, "a")))),
        Some(NameOrRunePT(NameP(_, "A"))),
        Some(DestructureP(_,Vector(capture("x"), capture("y")))),
        None) =>
    }
  }
  test("Capture with types inside") {
    compile("a (_ int, _ bool)") shouldHave {
      case PatternPP(_,_,
          Some(CaptureP(_,LocalNameP(NameP(_, "a")))),
          None,
          Some(DestructureP(_,Vector(fromEnv("int"), fromEnv("bool")))),
          None) =>
    }
  }
  test("Destructure with type inside") {
    compile("(a int, b bool)") shouldHave {
      case withDestructure(
      Vector(
          capturedWithType("a", NameOrRunePT(NameP(_, "int"))),
          capturedWithType("b", NameOrRunePT(NameP(_, "bool"))))) =>
    }
  }
  test("Nested destructures A") {
    compile("(a, (b, c))") shouldHave {
      case withDestructure(
        Vector(
          capture("a"),
          withDestructure(
          Vector(
            capture("b"),
            capture("c"))))) =>
    }
  }
  test("Nested destructures B") {
    compile("((a), b)") shouldHave {
      case withDestructure(
      Vector(
          withDestructure(
          Vector(
            capture("a"))),
          capture("b"))) =>
    }
  }
  test("Nested destructures C") {
    compile("(((a)))") shouldHave {
      case withDestructure(
      Vector(
          withDestructure(
          Vector(
            withDestructure(
            Vector(
              capture("a"))))))) =>
    }
  }
}
