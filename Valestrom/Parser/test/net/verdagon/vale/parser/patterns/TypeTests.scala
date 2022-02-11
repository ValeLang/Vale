package net.verdagon.vale.parser.patterns

import net.verdagon.vale.{Collector, parser, vfail, vimpl}
import net.verdagon.vale.parser.ast.Patterns.{fromEnv, withType}
import net.verdagon.vale.parser.old.CombinatorParsers._
import net.verdagon.vale.parser._
import net.verdagon.vale.parser.ast.{AnonymousRunePT, CallPT, FinalP, ImmutableP, IntPT, InterpretedPT, TuplePT, MutabilityPT, MutableP, NameOrRunePT, NameP, PatternPP, PointerP, ReadonlyP, StaticSizedArrayPT, VariabilityPT, VaryingP, WeakP}
import net.verdagon.vale.parser.old.CombinatorParsers
import org.scalatest.{FunSuite, Matchers}

class TypeTests extends FunSuite with Matchers with Collector {
  private def compile[T](parser: CombinatorParsers.Parser[T], code: String): T = {
    CombinatorParsers.parse(parser, code.toCharArray()) match {
      case CombinatorParsers.NoSuccess(msg, input) => {
        fail(msg + "\n" + input);
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
    compile("_ *[#3]MutableStruct") shouldHave {
      case PatternPP(_,_,
        None,
        Some(
          InterpretedPT(_,
            PointerP,
            ReadonlyP,
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
    compile("_ **[#3]<_, _>MutableStruct") shouldHave {
      case PatternPP(_,_,
        None,
        Some(
          InterpretedPT(_,
            WeakP,
            ReadonlyP,
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
        None,
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
