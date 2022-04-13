package dev.vale.parsing.patterns

import dev.vale.Collector
import dev.vale.parsing.{PatternParser, TestParseUtils}
import dev.vale.parsing.ast.{DestructureP, LocalNameDeclarationP, NameOrRunePT, NameP, PatternPP, TuplePT}
import dev.vale.parsing.ast.Patterns._
import dev.vale.parsing._
import dev.vale.parsing.ast.{DestructureP, LocalNameDeclarationP, NameOrRunePT, NameP, PatternPP, TuplePT}
import dev.vale.Collector
import org.scalatest.{FunSuite, Matchers}

class CaptureAndDestructureTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): PatternPP = {
    compile(x => new PatternParser().parsePattern(x), code)
  }

  test("Capture with destructure with type inside") {
    compile("a [a int, b bool]") shouldHave {
      case PatternPP(_,_,
          Some(LocalNameDeclarationP(NameP(_, StrI("a")))),
          None,
          Some(
          DestructureP(_,
            Vector(
              capturedWithType("a", NameOrRunePT(NameP(_, StrI("int")))),
              capturedWithType("b", NameOrRunePT(NameP(_, StrI("bool"))))))),
          None) =>
    }
  }
  test("capture with empty sequence type") {
    compile("a ()") shouldHave {
      case capturedWithType("a", TuplePT(_,Vector())) =>
    }
  }
  test("empty destructure") {
    compile(new PatternParser().parseDestructure(_),"[]") shouldHave { case Nil => }
  }
  test("capture with empty destructure") {
    // Needs the space between the braces, see https://github.com/ValeLang/Vale/issues/434
    compile("a [ ]") shouldHave {
      case PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, StrI("a")))),None,Some(DestructureP(_,Vector())),None) =>
    }
  }
  test("Destructure with nested atom") {
    compile("a [b int]") shouldHave {
      case PatternPP(_,_,
          Some(LocalNameDeclarationP(NameP(_, StrI("a")))),
          None,
          Some(
          DestructureP(_,
            Vector(capturedWithType("b", NameOrRunePT(NameP(_, StrI("int"))))))),
          None) =>
    }
  }
}
