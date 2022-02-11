package net.verdagon.vale.parser

import net.verdagon.vale.parser.ExpressionParser.StopBeforeCloseBrace
import net.verdagon.vale.parser.ast.{BlockPE, ConstantIntPE, FunctionHeaderP, FunctionP, FunctionReturnP, IdentifyingRuneP, IdentifyingRunesP, LocalNameDeclarationP, NameOrRunePT, NameP, OverrideP, ParamsP, PatternPP, Patterns, TopLevelFunctionP, VoidPE}
import net.verdagon.vale.parser.old.{CombinatorParsers, OldTestParseUtils}
import net.verdagon.vale.{Collector, vassert, vimpl}
import org.scalatest.{FunSuite, Matchers}

class SignatureTests extends FunSuite with Collector with TestParseUtils {
  // func maxHp(this: virtual IUnit): Int;

  test("Impl function") {
    compile(
      Parser.parseTopLevelThing(_),
      "func maxHp(this Marine impl IUnit) { ret 5; }") shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "maxHp")),Vector(), None, None,
          Some(
            ParamsP(
              _,
              Vector(
                PatternPP(_,_,
                  Some(LocalNameDeclarationP(NameP(_, "this"))),
                  Some(NameOrRunePT(NameP(_, "Marine"))),
                  None,
                  Some(OverrideP(_,NameOrRunePT(NameP(_, "IUnit")))))))),
          FunctionReturnP(_, None,None)),
        Some(BlockPE(_, _))) =>
    }
  }

  test("Param") {
    val program =
      compile(
        Parser.parseTopLevelThing(_),
        "func call(f F){f()}")
    program shouldHave {
      case PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, "f"))),Some(NameOrRunePT(NameP(_, "F"))),None,None) =>
    }
  }

  test("Func with rules") {
    compile(
      Parser.parseTopLevelThing(_),
      "func sum () where X int {3}") shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")), Vector(), None, Some(_), Some(_), FunctionReturnP(_, None, None)),
        Some(BlockPE(_, ConstantIntPE(_, 3, _)))) =>
    }
  }


  test("Identifying runes") {
    compile(
      Parser.parseTopLevelThing(_),
      "func wrap<A, F>(a A) { }") shouldHave {
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
        Some(BlockPE(_, VoidPE(_)))) =>
    }
  }

  test("Never signature") {
    // This test is here because we were parsing the first _ of __Never as an anonymous
    // rune then stopping.
    compile(
      Parser.parseTopLevelThing(_),
      "func __vbi_panic() __Never {}") shouldHave {
      case NameOrRunePT(NameP(_,"__Never")) =>
    }
  }

  test("Short self") {
    compile(
      Parser.parseTopLevelThing(_),
      "func moo(&self impl IMoo) {}") shouldHave {
      case Some(
        TopLevelFunctionP(
          FunctionP(_,
            FunctionHeaderP(_,
              Some(NameP(_,"moo")),
              Vector(),None,None,
              Some(
                ParamsP(_,
                  Vector(
                    PatternPP(_,
                      None,
                      Some(LocalNameDeclarationP(NameP(_,"self"))),
                      None,None,
                      Some(OverrideP(_,NameOrRunePT(NameP(_,"IMoo")))))))),
              FunctionReturnP(_,None,None)),
            Some(BlockPE(_,VoidPE(_)))))) =>
    }
  }

}
