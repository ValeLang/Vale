package dev.vale.parsing

import dev.vale.{Collector, StrI}
import dev.vale.parsing.ast._
import dev.vale.Collector
import org.scalatest.{FunSuite, Matchers}

class SignatureTests extends FunSuite with Collector with TestParseUtils {
  // func maxHp(this: virtual IUnit): Int;

  test("Impl function") {
    compileTopLevel(
      "func maxHp(virtual this Marine) { return 5; }") shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, StrI("maxHp"))),Vector(), None, None,
          Some(
            ParamsP(
              _,
              Vector(
                PatternPP(_,_,
                  Some(LocalNameDeclarationP(NameP(_, StrI("this")))),
                  Some(NameOrRunePT(NameP(_, StrI("Marine")))),
                  None,
                  Some(AbstractP(_)))))),
          FunctionReturnP(_, None,None)),
        Some(BlockPE(_, _))) =>
    }
  }

  test("Param") {
    val program = compileTopLevel("func call(f F){f()}")
    program shouldHave {
      case PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, StrI("f")))),Some(NameOrRunePT(NameP(_, StrI("F")))),None,None) =>
    }
  }

  test("Func with rules") {
    compileTopLevel(
      "func sum () where X int {3}") shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, StrI("sum"))), Vector(), None, Some(_), Some(_), FunctionReturnP(_, None, None)),
        Some(BlockPE(_, ConstantIntPE(_, 3, _)))) =>
    }
  }


  test("Identifying runes") {
    compileTopLevel(
      "func wrap<A, F>(a A) { }") shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, StrI("wrap"))), Vector(),
          Some(
            IdentifyingRunesP(_,
              Vector(
              IdentifyingRuneP(_, NameP(_, StrI("A")), Vector()),
              IdentifyingRuneP(_, NameP(_, StrI("F")), Vector())))),
          None,
          Some(ParamsP(_, Vector(Patterns.capturedWithTypeRune("a", "A")))),
          FunctionReturnP(_, None, None)),
        Some(BlockPE(_, VoidPE(_)))) =>
    }
  }

  test("Never signature") {
    // This test is here because we were parsing the first _ of __Never as an anonymous
    // rune then stopping.
    compileTopLevel(
      "func __vbi_panic() __Never {}") shouldHave {
      case NameOrRunePT(NameP(_, StrI("__Never"))) =>
    }
  }

  test("Short self") {
    compileTopLevel(
      "func moo(&self) {}") shouldHave {
      case TopLevelFunctionP(
        FunctionP(_,
          FunctionHeaderP(_,
            Some(NameP(_, StrI("moo"))),
            Vector(),None,None,
            Some(
              ParamsP(_,
                Vector(
                  PatternPP(_,
                    None,
                    Some(LocalNameDeclarationP(NameP(_, StrI("self")))),
                    None,None,None)))),
            FunctionReturnP(_,None,None)),
          Some(BlockPE(_,VoidPE(_))))) =>
    }
  }

}
