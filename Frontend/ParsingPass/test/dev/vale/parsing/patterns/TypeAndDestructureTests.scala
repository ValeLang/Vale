package dev.vale.parsing.patterns

import dev.vale.{Collector, StrI}
import dev.vale.parsing.ast.{CallPT, DestinationLocalP, DestructureP, IgnoredLocalNameDeclarationP, LocalNameDeclarationP, NameOrRunePT, NameP, PatternPP, TuplePT}
import dev.vale.parsing.ast.Patterns._
import dev.vale.parsing._
import dev.vale.Collector
import org.scalatest._

class TypeAndDestructureTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): PatternPP = {
    compilePattern(code)
//    compile(new PatternParser().parsePattern(_), code)
  }

  test("Empty destructure") {
    compile("_ Muta[]") shouldHave {
      case PatternPP(_,
          Some(DestinationLocalP(IgnoredLocalNameDeclarationP(_), None)),
          Some(NameOrRunePT(NameP(_, StrI("Muta")))),
          Some(DestructureP(_,Vector()))) =>
    }
  }

  test("Templated destructure") {
    compile("_ Muta<int>[]") shouldHave {
      case PatternPP(_,
          Some(DestinationLocalP(IgnoredLocalNameDeclarationP(_), None)),
          Some(
            CallPT(_,
              NameOrRunePT(NameP(_, StrI("Muta"))),
              Vector(NameOrRunePT(NameP(_, StrI("int")))))),
          Some(DestructureP(_,Vector()))) =>
    }
    compile("_ Muta<R>[]") shouldHave {
        case PatternPP(_,
          Some(DestinationLocalP(IgnoredLocalNameDeclarationP(_), None)),
          Some(
            CallPT(_,
              NameOrRunePT(NameP(_, StrI("Muta"))),
              Vector(NameOrRunePT(NameP(_, StrI("R")))))),
          Some(DestructureP(_,Vector()))) =>
    }
  }


  test("Destructure with type outside") {
    compile("_ (int, bool)[a, b]") shouldHave {
      case PatternPP(_,
          Some(DestinationLocalP(IgnoredLocalNameDeclarationP(_), None)),
          Some(
            TuplePT(_,
                Vector(
                  NameOrRunePT(NameP(_, StrI("int"))),
                  NameOrRunePT(NameP(_, StrI("bool")))))),
          Some(DestructureP(_,Vector(capture("a"), capture("b"))))) =>
    }
  }
  test("Destructure with typeless capture") {
    compile("_ Muta[b]") shouldHave {
      case PatternPP(_,
          Some(DestinationLocalP(IgnoredLocalNameDeclarationP(_), None)),
          Some(NameOrRunePT(NameP(_, StrI("Muta")))),
          Some(DestructureP(_,Vector(PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("b"))), None)),None,None))))) =>
    }
  }
  test("Destructure with typed capture") {
    compile("_ Muta[b Marine]") shouldHave {
      case PatternPP(_,
          Some(DestinationLocalP(IgnoredLocalNameDeclarationP(_), None)),
          Some(NameOrRunePT(NameP(_, StrI("Muta")))),
          Some(DestructureP(_,Vector(PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("b"))), None)),Some(NameOrRunePT(NameP(_, StrI("Marine")))),None))))) =>
    }
  }
  test("Destructure with unnamed capture") {
    compile("_ Muta[_ Marine]") shouldHave {
      case PatternPP(_,
          Some(DestinationLocalP(IgnoredLocalNameDeclarationP(_), None)),
          Some(NameOrRunePT(NameP(_, StrI("Muta")))),
          Some(DestructureP(_,Vector(PatternPP(_,Some(DestinationLocalP(IgnoredLocalNameDeclarationP(_), None)),Some(NameOrRunePT(NameP(_, StrI("Marine")))),None))))) =>
    }
  }
  test("Destructure with runed capture") {
    compile("_ Muta[_ R]") shouldHave {
      case PatternPP(_,
          Some(DestinationLocalP(IgnoredLocalNameDeclarationP(_), None)),
          Some(NameOrRunePT(NameP(_, StrI("Muta")))),
          Some(DestructureP(_,Vector(PatternPP(_,Some(DestinationLocalP(IgnoredLocalNameDeclarationP(_), None)),Some(NameOrRunePT(NameP(_, StrI("R")))),None))))) =>
        }
  }
}
