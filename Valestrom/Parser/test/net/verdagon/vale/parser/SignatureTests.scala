package net.verdagon.vale.parser

import net.verdagon.vale.{vassert, vimpl}
import org.scalatest.{FunSuite, Matchers}

class SignatureTests extends FunSuite with Matchers with Collector with TestParseUtils {
  // fn maxHp(this: virtual IUnit): Int;

  test("Impl function") {
    compile(
      CombinatorParsers.topLevelFunction,
      "fn maxHp(this Marine impl IUnit) { 5 }") shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "maxHp")),List(), None, None,
          Some(
            ParamsP(
              _,
              List(
                PatternPP(_,_,
                  Some(CaptureP(_,LocalNameP(StringP(_, "this")),FinalP)),
                  Some(NameOrRunePT(StringP(_, "Marine"))),
                  None,
                  Some(OverrideP(NameOrRunePT(StringP(_, "IUnit")))))))),
          None),
        Some(BlockPE(_, List(IntLiteralPE(_, 5))))) =>
    }
  }

  test("Param") {
    val program = compileProgram("fn call(f F){f()}")
    program shouldHave {
      case PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "f")),FinalP)),Some(NameOrRunePT(StringP(_, "F"))),None,None) =>
    }
  }

  test("Templated function") {
    compile(CombinatorParsers.topLevelFunction, "fn sum () rules() {3}") shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "sum")), List(), None, Some(_), Some(_), None),
        Some(BlockPE(_, List(IntLiteralPE(_, 3))))) =>
    }
  }

  test("Identifying runes") {
    compile(
      CombinatorParsers.topLevelFunction,
      "fn wrap<A, F>(a A) { }") shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "wrap")), List(),
          Some(
            IdentifyingRunesP(_,
              List(
              IdentifyingRuneP(_, StringP(_, "A"), List()),
              IdentifyingRuneP(_, StringP(_, "F"), List())))),
          None,
          Some(ParamsP(_, List(Patterns.capturedWithTypeRune("a", "A")))),
          None),
        Some(BlockPE(_, List(VoidPE(_))))) =>
    }
  }
}
