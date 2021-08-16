package net.verdagon.vale.parser

import net.verdagon.vale.{Tests, vassert}
import org.scalatest.{FunSuite, Matchers}



class IfTests extends FunSuite with Matchers with Collector {
  private def compileProgramWithComments(code: String): FileP = {
    Parser.runParserForProgramAndCommentRanges(code) match {
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(result) => result._1
    }
  }
  private def compileProgram(code: String): FileP = {
    // The strip is in here because things inside the parser don't expect whitespace before and after
    Parser.runParser(code) match {
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(result) => result
    }
  }

  private def compile[T](parser: CombinatorParsers.Parser[T], code: String): T = {
    // The strip is in here because things inside the parser don't expect whitespace before and after
    CombinatorParsers.parse(parser, code.strip().toCharArray()) match {
      case CombinatorParsers.NoSuccess(msg, input) => {
        fail("Couldn't parse!\n" + input.pos.longString);
      }
      case CombinatorParsers.Success(expr, rest) => {
        vassert(rest.atEnd)
        expr
      }
    }
  }

  test("ifs") {
    compile(CombinatorParsers.ifLadder, "if (true) { doBlarks(&x) } else { }") shouldHave {
      case IfPE(_,
        BlockPE(_, Vector(ConstantBoolPE(_, true))),
        BlockPE(_,
          Vector(
            FunctionCallPE(_,
              None, _, false, LookupPE(NameP(_, "doBlarks"), None),
              Vector(
                LendPE(_,LookupPE(NameP(_, "x"), None), LendConstraintP(Some(ReadonlyP)))),
              LendConstraintP(Some(ReadonlyP))))),
        BlockPE(_, Vector(VoidPE(_)))) =>
    }
  }

  test("if let") {
    compile(CombinatorParsers.ifLadder, "if ((u) = a) {}") shouldHave {
      case IfPE(_,
        BlockPE(_,
          Vector(
            LetPE(_,None,
              PatternPP(_,None,None,None,
                Some(
                  DestructureP(_,
                    Vector(
                      PatternPP(_,None,Some(CaptureP(_,LocalNameP(NameP(_,"u")))),None,None,None)))),
                None),
              LookupPE(NameP(_,"a"),None)))),
        BlockPE(_,Vector(VoidPE(_))),
        BlockPE(_,Vector(VoidPE(_)))) =>
    }
  }

  test("19") {
    compile(CombinatorParsers.statement,
      "newLen = if (num == 0) { 1 } else { 2 };") shouldHave {
      case LetPE(_,
      None,
      PatternPP(_, _,Some(CaptureP(_,LocalNameP(NameP(_, "newLen")))), None, None, None),
      IfPE(_,
      BlockPE(_, Vector(FunctionCallPE(_, None, _, false, LookupPE(NameP(_, "=="), None), Vector(LookupPE(NameP(_, "num"), None), ConstantIntPE(_, 0, _)), LendConstraintP(Some(ReadonlyP))))),
      BlockPE(_, Vector(ConstantIntPE(_, 1, _))),
      BlockPE(_, Vector(ConstantIntPE(_, 2, _))))) =>
    }
  }
}
