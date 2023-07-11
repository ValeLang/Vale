package dev.vale.parsing.patterns

import dev.vale.{Collector, StrI, parsing}
import dev.vale.parsing.{PatternParser, TestParseUtils}
import dev.vale.parsing.ast.{AnonymousRunePT, BorrowP, CallPT, FinalP, IgnoredLocalNameDeclarationP, ImmutableP, IntPT, InterpretedPT, MutabilityPT, MutableP, NameOrRunePT, NameP, PatternPP, StaticSizedArrayPT, TuplePT, VariabilityPT, VaryingP, WeakP}
import dev.vale.parsing.ast.Patterns.{fromEnv, withType}
import dev.vale.parsing._
import dev.vale.parsing.ast._
import org.scalatest._

class TypeTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): PatternPP = {
    compilePattern(code)
//    compile(new PatternParser().parsePattern(_), code)
  }

  test("Ignoring name") {
    compile("_ int") shouldHave { case fromEnv("int") => }
  }

  test("15a") {
    compile("_ [#3]MutableStruct") shouldHave {
      case withType(
          StaticSizedArrayPT(_,
              MutabilityPT(_,MutableP),
              VariabilityPT(_,FinalP),
              IntPT(_,3),
              NameOrRunePT(NameP(_, StrI("MutableStruct"))))) =>
    }
  }

  test("15b") {
    compile("_ [#3]<imm>MutableStruct") shouldHave {
      case withType(
        StaticSizedArrayPT(_,
          MutabilityPT(_,ImmutableP),
          VariabilityPT(_,FinalP),
          IntPT(_,3),
          NameOrRunePT(NameP(_, StrI("MutableStruct"))))) =>
    }
  }

  test("15c") {
    compile("_ [#3]<imm, vary>MutableStruct") shouldHave {
      case withType(
      StaticSizedArrayPT(_,
      MutabilityPT(_,ImmutableP),
      VariabilityPT(_,VaryingP),
      IntPT(_,3),
      NameOrRunePT(NameP(_, StrI("MutableStruct"))))) =>
    }
  }

  test("15d") {
    compile("_ #[]int") shouldHave {
      case withType(
        RuntimeSizedArrayPT(_,
          MutabilityPT(_,ImmutableP),
          NameOrRunePT(NameP(_, StrI("int"))))) =>
    }
  }



  test("Sequence type") {
    compile("_ (int, bool)") shouldHave {
      case withType(
          TuplePT(_,
            Vector(
              NameOrRunePT(NameP(_, StrI("int"))),
              NameOrRunePT(NameP(_, StrI("bool")))))) =>
    }
  }
  test("15") {
    compile("_ &[#3]MutableStruct") shouldHave {
      case PatternPP(_,
        Some(DestinationLocalP(IgnoredLocalNameDeclarationP(_), None)),
        Some(
          InterpretedPT(_,
            Some(OwnershipPT(_, BorrowP)),
            None,
            StaticSizedArrayPT(_,
              MutabilityPT(_,MutableP),
              VariabilityPT(_,FinalP),
              IntPT(_,3),
              NameOrRunePT(NameP(_, StrI("MutableStruct")))))),
        None) =>
    }
  }
  test("15m") {
    compile("_ &&[#3]<_, _>MutableStruct") shouldHave {
      case PatternPP(_,
        Some(DestinationLocalP(IgnoredLocalNameDeclarationP(_), None)),
        Some(
          InterpretedPT(_,
            Some(OwnershipPT(_, WeakP)),
            None,
            StaticSizedArrayPT(_,
              AnonymousRunePT(_),
              AnonymousRunePT(_),
              IntPT(_,3),
              NameOrRunePT(NameP(_, StrI("MutableStruct")))))),
        None) =>
    }
  }
  test("15z") {
    compile("_ MyOption<MyList<int>>") shouldHave {
      case PatternPP(_,
        Some(DestinationLocalP(IgnoredLocalNameDeclarationP(_), None)),
        Some(
          CallPT(
            _,
            NameOrRunePT(NameP(_, StrI("MyOption"))),
            Vector(
              CallPT(_,
                NameOrRunePT(NameP(_, StrI("MyList"))),
                Vector(
                  NameOrRunePT(NameP(_, StrI("int")))))))),
        None) =>
    }
  }
}
