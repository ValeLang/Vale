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
          Some(NameP(_, "maxHp")),Nil, None, None,
          Some(
            ParamsP(
              _,
              List(
                PatternPP(_,_,
                  Some(CaptureP(_,LocalNameP(NameP(_, "this")))),
                  Some(NameOrRunePT(NameP(_, "Marine"))),
                  None,
                  Some(OverrideP(_,NameOrRunePT(NameP(_, "IUnit")))))))),
          FunctionReturnP(_, None,None)),
        Some(BlockPE(_, List(ConstantIntPE(_, 5, _))))) =>
    }
  }

  test("Param") {
    val program = compileProgram("fn call(f F){f()}")
    program shouldHave {
      case PatternPP(_,_,Some(CaptureP(_,LocalNameP(NameP(_, "f")))),Some(NameOrRunePT(NameP(_, "F"))),None,None) =>
    }
  }

  test("Templated function") {
    compile(CombinatorParsers.topLevelFunction, "fn sum () rules() {3}") shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")), Nil, None, Some(_), Some(_), FunctionReturnP(_, None, None)),
        Some(BlockPE(_, List(ConstantIntPE(_, 3, _))))) =>
    }
  }


  test("Identifying runes") {
    compile(
      CombinatorParsers.topLevelFunction,
      "fn wrap<A, F>(a A) { }") shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "wrap")), Nil,
          Some(
            IdentifyingRunesP(_,
              List(
              IdentifyingRuneP(_, NameP(_, "A"), Nil),
              IdentifyingRuneP(_, NameP(_, "F"), Nil)))),
          None,
          Some(ParamsP(_, List(Patterns.capturedWithTypeRune("a", "A")))),
          FunctionReturnP(_, None, None)),
        Some(BlockPE(_, List(VoidPE(_))))) =>
    }
  }
}
