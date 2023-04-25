package dev.vale.parsing.patterns

import dev.vale.{Collector, StrI}
import dev.vale.parsing.ast.{DestinationLocalP, DestructureP, IgnoredLocalNameDeclarationP, LocalNameDeclarationP, NameOrRunePT, NameP, PatternPP}
import dev.vale.parsing.ast.Patterns._
import dev.vale.parsing._
import dev.vale.Collector
import org.scalatest.{FunSuite, Matchers}

class DestructureParserTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): PatternPP = {
    compilePattern(code)
//    compile(new PatternParser().parsePattern(_), code)
  }

  test("Only empty destructure") {
    compile("[]") shouldHave {
      case withDestructure(Vector()) =>
    }
  }
  test("One element destructure") {
    compile("[a]") shouldHave {
      case withDestructure(Vector(capture("a"))) =>
    }
  }
  test("One typed element destructure") {
    compile("[ _ A ]") shouldHave {
      case withDestructure(Vector(withType(NameOrRunePT(NameP(_, StrI("A")))))) =>
    }
  }
  test("Only two-element destructure") {
    compile("[a, b]") shouldHave {
      case withDestructure(Vector(capture("a"), capture("b"))) =>
    }
  }
  test("Two-element destructure with ignore") {
    compile("[_, b]") shouldHave {
      case PatternPP(_,_,
          None,None,
          Some(DestructureP(_,Vector(PatternPP(_,_,Some(DestinationLocalP(IgnoredLocalNameDeclarationP(_), None)), None, None, None), capture("b")))),
          None) =>
    }
  }
  test("Capture with destructure") {
    compile("a [x, y]") shouldHave {
      case PatternPP(_,_,
        Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("a"))), None)),
        None,
        Some(DestructureP(_,Vector(capture("x"), capture("y")))),
        None) =>
    }
  }
  test("Type with destructure") {
    compile("A[a, b]") shouldHave {
      case PatternPP(_,_,
        None,
        Some(NameOrRunePT(NameP(_, StrI("A")))),
        Some(DestructureP(_,Vector(capture("a"), capture("b")))),
        None) =>
    }
  }
  test("Capture and type with destructure") {
    compile("a A[x, y]") shouldHave {
      case PatternPP(_,_,
        Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("a"))), None)),
        Some(NameOrRunePT(NameP(_, StrI("A")))),
        Some(DestructureP(_,Vector(capture("x"), capture("y")))),
        None) =>
    }
  }
  test("Capture with types inside") {
    compile("a [_ int, _ bool]") shouldHave {
      case PatternPP(_,_,
          Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("a"))), None)),
          None,
          Some(DestructureP(_,Vector(fromEnv("int"), fromEnv("bool")))),
          None) =>
    }
  }
  test("Destructure with type inside") {
    compile("[a int, b bool]") shouldHave {
      case withDestructure(
      Vector(
          capturedWithType("a", NameOrRunePT(NameP(_, StrI("int")))),
          capturedWithType("b", NameOrRunePT(NameP(_, StrI("bool")))))) =>
    }
  }
  test("Nested destructures A") {
    compile("[a, [b, c]]") shouldHave {
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
    compile("[[a], b]") shouldHave {
      case withDestructure(
      Vector(
          withDestructure(
          Vector(
            capture("a"))),
          capture("b"))) =>
    }
  }
  test("Nested destructures C") {
    compile("[[[a]]]") shouldHave {
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
