package net.verdagon.vale.parser

import net.verdagon.vale.{Collector, vassert, vimpl}
import org.scalatest.{FunSuite, Matchers}

class SignatureTests extends FunSuite with Matchers with Collector with TestParseUtils {
  // fn maxHp(this: virtual IUnit): Int;

  test("Impl function") {
    compile(
      CombinatorParsers.topLevelFunction,
      "fn maxHp(this Marine impl IUnit) { 5 }") shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "maxHp")),Vector(), None, None,
          Some(
            ParamsP(
              _,
              Vector(
                PatternPP(_,_,
                  Some(CaptureP(_,LocalNameP(NameP(_, "this")))),
                  Some(NameOrRunePT(NameP(_, "Marine"))),
                  None,
                  Some(OverrideP(_,NameOrRunePT(NameP(_, "IUnit")))))))),
          FunctionReturnP(_, None,None)),
        Some(BlockPE(_, Vector(ConstantIntPE(_, 5, _))))) =>
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
          Some(NameP(_, "sum")), Vector(), None, Some(_), Some(_), FunctionReturnP(_, None, None)),
        Some(BlockPE(_, Vector(ConstantIntPE(_, 3, _))))) =>
    }
  }


  test("Identifying runes") {
    compile(
      CombinatorParsers.topLevelFunction,
      "fn wrap<A, F>(a A) { }") shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "wrap")), Vector(),
          Some(
            IdentifyingRunesP(_,
              Vector(
              IdentifyingRuneP(_, NameP(_, "A"), Vector()),
              IdentifyingRuneP(_, NameP(_, "F"), Vector())))),
          None,
          Some(ParamsP(_, Vector(Patterns.capturedWithTypeRune("a", "A")))),
          FunctionReturnP(_, None, None)),
        Some(BlockPE(_, Vector(VoidPE(_))))) =>
    }
  }
}
