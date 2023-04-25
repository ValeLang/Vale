package dev.vale.parsing.patterns

import dev.vale.{Collector, StrI, vimpl}
import dev.vale.parsing.ast.{DestinationLocalP, DestructureP, LocalNameDeclarationP, NameOrRunePT, NameP, PatternPP, TuplePT}
import dev.vale.parsing._
import dev.vale.parsing.ast.Patterns.capturedWithType
import dev.vale.parsing.ast.{DestructureP, LocalNameDeclarationP, NameOrRunePT, NameP, PatternPP, TuplePT}
import org.scalatest.{FunSuite, Matchers}

class CaptureAndDestructureTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): PatternPP = {
    compilePattern(code)
//    compile(x => new PatternParser().parsePattern(x), code)
  }

  test("Capture with destructure with type inside") {
    compile("a [a int, b bool]") shouldHave {
      case PatternPP(_,_,
          Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("a"))), None)),
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
    compilePattern("[]") shouldHave
      { case PatternPP(_,None,None,None,Some(DestructureP(_,Vector())),None) => }
  }
  test("capture with empty destructure") {
    // Needs the space between the braces, see https://github.com/ValeLang/Vale/issues/434
    compile("a [ ]") shouldHave {
      case PatternPP(_,_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("a"))), None)),None,Some(DestructureP(_,Vector())),None) =>
    }
  }
  test("Destructure with nested atom") {
    compile("a [b int]") shouldHave {
      case PatternPP(_,_,
          Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("a"))), None)),
          None,
          Some(
          DestructureP(_,
            Vector(capturedWithType("b", NameOrRunePT(NameP(_, StrI("int"))))))),
          None) =>
    }
  }
}
