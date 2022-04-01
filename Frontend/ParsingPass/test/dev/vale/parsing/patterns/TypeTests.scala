package dev.vale.parsing.patterns

import dev.vale.Collector
import dev.vale.parsing.{PatternParser, TestParseUtils}
import dev.vale.parsing.ast.{AnonymousRunePT, BorrowP, CallPT, FinalP, IgnoredLocalNameDeclarationP, ImmutableP, IntPT, InterpretedPT, MutabilityPT, MutableP, NameOrRunePT, NameP, PatternPP, StaticSizedArrayPT, TuplePT, VariabilityPT, VaryingP, WeakP}
import dev.vale.parsing
import dev.vale.parsing.ast.Patterns.{fromEnv, withType}
import dev.vale.parsing._
import dev.vale.parsing.ast._
import org.scalatest.{FunSuite, Matchers}

class TypeTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): PatternPP = {
    compile(new PatternParser().parsePattern(_), code)
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
              NameOrRunePT(NameP(_, "MutableStruct")))) =>
    }
  }

  test("15b") {
    compile("_ [#3]<imm>MutableStruct") shouldHave {
      case withType(
        StaticSizedArrayPT(_,
          MutabilityPT(_,ImmutableP),
          VariabilityPT(_,FinalP),
          IntPT(_,3),
          NameOrRunePT(NameP(_, "MutableStruct")))) =>
    }
  }

  test("15c") {
    compile("_ [#3]<imm, vary>MutableStruct") shouldHave {
      case withType(
      StaticSizedArrayPT(_,
      MutabilityPT(_,ImmutableP),
      VariabilityPT(_,VaryingP),
      IntPT(_,3),
      NameOrRunePT(NameP(_, "MutableStruct")))) =>
    }
  }

  test("Sequence type") {
    compile("_ (int, bool)") shouldHave {
      case withType(
          TuplePT(_,
            Vector(
              NameOrRunePT(NameP(_, "int")),
              NameOrRunePT(NameP(_, "bool"))))) =>
    }
  }
  test("15") {
    compile("_ &[#3]MutableStruct") shouldHave {
      case PatternPP(_,_,
        Some(IgnoredLocalNameDeclarationP(_)),
        Some(
          InterpretedPT(_,
            BorrowP,
            StaticSizedArrayPT(_,
              MutabilityPT(_,MutableP),
              VariabilityPT(_,FinalP),
              IntPT(_,3),
              NameOrRunePT(NameP(_, "MutableStruct"))))),
        None,
        None) =>
    }
  }
  test("15m") {
    compile("_ &&[#3]<_, _>MutableStruct") shouldHave {
      case PatternPP(_,_,
        Some(IgnoredLocalNameDeclarationP(_)),
        Some(
          InterpretedPT(_,
            WeakP,
            StaticSizedArrayPT(_,
              AnonymousRunePT(_),
              AnonymousRunePT(_),
              IntPT(_,3),
              NameOrRunePT(NameP(_, "MutableStruct"))))),
        None,
        None) =>
    }
  }
  test("15z") {
    compile("_ MyOption<MyList<int>>") shouldHave {
      case PatternPP(_,_,
        Some(IgnoredLocalNameDeclarationP(_)),
        Some(
          CallPT(
            _,
            NameOrRunePT(NameP(_, "MyOption")),
            Vector(
              CallPT(_,
                NameOrRunePT(NameP(_, "MyList")),
                Vector(
                  NameOrRunePT(NameP(_, "int"))))))),
        None,
        None) =>
    }
  }
}
