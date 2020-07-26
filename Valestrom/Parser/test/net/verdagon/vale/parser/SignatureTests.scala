package net.verdagon.vale.parser

import net.verdagon.vale.{vassert, vimpl}
import org.scalatest.{FunSuite, Matchers}

class SignatureTests extends FunSuite with Matchers with Collector {
  private def compile[T](parser: VParser.Parser[T], code: String): T = {
    // The strip is in here because things inside the parser don't expect whitespace before and after
    VParser.parse(parser, code.strip().toCharArray()) match {
      case VParser.NoSuccess(msg, input) => {
        fail("Couldn't parse!\n" + input.pos.longString);
      }
      case VParser.Success(expr, rest) => {
        vassert(rest.atEnd)
        expr
      }
    }
  }

  // fn maxHp(this: virtual IUnit): Int;

  test("Impl function") {
    compile(
      VParser.topLevelFunction,
      "fn maxHp(this Marine impl IUnit) { 5 }") shouldHave {
      case FunctionP(_,
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
          None,
          Some(BlockPE(_, List(IntLiteralPE(_, 5))))) =>
    }
  }

  test("Param") {
    val program = compile(VParser.program, "fn call(f F){f()}")
    program shouldHave {
      case PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "f")),FinalP)),Some(NameOrRunePT(StringP(_, "F"))),None,None) =>
    }
  }

  test("Templated function") {
    compile(VParser.topLevelFunction, "fn sum () rules() {3}") shouldHave {
      case FunctionP(_, Some(StringP(_, "sum")), List(), None, Some(_), Some(_), None, Some(BlockPE(_, List(IntLiteralPE(_, 3))))) =>
    }
  }

  test("Identifying runes") {
    compile(
      VParser.topLevelFunction,
      "fn wrap<A, F>(a A) { }") shouldHave {
      case FunctionP(_,
        Some(StringP(_, "wrap")), List(),
        Some(
          IdentifyingRunesP(_,
            List(
            IdentifyingRuneP(_, StringP(_, "A"), List()),
            IdentifyingRuneP(_, StringP(_, "F"), List())))),
        None,
        Some(ParamsP(_, List(Patterns.capturedWithTypeRune("a", "A")))),
        None,
        Some(BlockPE(_, List(VoidPE(_))))) =>
    }
  }
}
