package net.verdagon.vale.parser

import net.verdagon.vale.{Samples, vassert}
import org.scalatest.{FunSuite, Matchers}



class BiggerTests extends FunSuite with Matchers with Collector {
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
      BlockPE(_, List(BoolLiteralPE(_, true))),
      BlockPE(_, List(FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "doBlarks"), None), List(LendPE(_,LookupPE(StringP(_, "x"), None), BorrowP)), BorrowP))),
      BlockPE(_, List(VoidPE(_)))) =>
    }
  }

  test("if let") {
    compile(CombinatorParsers.ifLadder, "if ((u) = a) {}") shouldHave {
      case IfPE(_,
        BlockPE(_,
          List(
            LetPE(_,None,
              PatternPP(_,None,None,None,
                Some(
                  DestructureP(_,
                    List(
                      PatternPP(_,None,Some(CaptureP(_,LocalNameP(StringP(_,"u")),FinalP)),None,None,None)))),
                None),
              LookupPE(StringP(_,"a"),None)))),
        BlockPE(_,List(VoidPE(_))),
        BlockPE(_,List(VoidPE(_)))) =>
    }
  }


  test("19") {
    compile(CombinatorParsers.statement,
      "newLen = if (num == 0) { 1 } else { 2 };") shouldHave {
      case LetPE(_,
      None,
      PatternPP(_, _,Some(CaptureP(_,LocalNameP(StringP(_, "newLen")), FinalP)), None, None, None),
      IfPE(_,
      BlockPE(_, List(FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "=="), None), List(LookupPE(StringP(_, "num"), None), IntLiteralPE(_, 0)), BorrowP))),
      BlockPE(_, List(IntLiteralPE(_, 1))),
      BlockPE(_, List(IntLiteralPE(_, 2))))) =>
    }
  }
}
