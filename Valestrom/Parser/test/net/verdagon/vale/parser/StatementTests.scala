package net.verdagon.vale.parser

import net.verdagon.vale.{vassert, vimpl}
import org.scalatest.{FunSuite, Matchers}

class StatementTests extends FunSuite with Matchers with Collector {
  private def compile(code: String): IExpressionPE = {
    VParser.parse(VParser.statement, code.toCharArray()) match {
      case VParser.NoSuccess(msg, input) => {
        fail(msg);
      }
      case VParser.Success(expr, rest) => {
        vassert(rest.atEnd)
        expr
      }
    }
  }

  test("Simple let") {
    compile("x = 4;") shouldHave {
      case LetPE(_,List(), PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "x")), FinalP)), None, None, None), IntLiteralPE(_,4)) =>
    }
  }

  test("8") {
    compile("(x, y) = [4, 5];") shouldHave {
      case LetPE(_,
      List(),
          PatternPP(_,_,
            None,
            None,
            Some(
              DestructureP(_,
                List(
                  PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "x")),FinalP)),None,None,None),
                  PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "y")),FinalP)),None,None,None)))),
            None),
          SequencePE(_,List(IntLiteralPE(_,4), IntLiteralPE(_,5)))) =>
    }
  }

  test("9") {
    compile("mut x.a = 5;") shouldHave {
      case MutatePE(_, DotPE(_, LookupPE(StringP(_, "x"), None), LookupPE(StringP(_, "a"), None)), IntLiteralPE(_,5)) =>
    }
  }

  test("1PE") {
    compile("""mut board.PE.PE.symbol = "v";""") shouldHave {
      case MutatePE(_, DotPE(_, DotPE(_, DotPE(_, LookupPE(StringP(_, "board"), None), LookupPE(StringP(_, "PE"), None)), LookupPE(StringP(_, "PE"), None)), LookupPE(StringP(_, "symbol"), None)), StrLiteralPE(StringP(_, "v"))) =>
    }
  }

  test("Test simple let") {
    compile("x = 3;") shouldHave {
      case LetPE(_,List(),PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "x")),FinalP)),None,None,None),IntLiteralPE(_,3)) =>
    }
  }

  test("Test varying let") {
    compile("x! = 3;") shouldHave {
      case LetPE(_,List(),PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "x")),VaryingP)),None,None,None),IntLiteralPE(_,3)) =>
    }
  }

  test("Test simple mut") {
    compile("mut x = 5;") shouldHave {
      case MutatePE(_, LookupPE(StringP(_, "x"), None),IntLiteralPE(_,5)) =>
    }
  }

  test("Test destruct") {
    compile("destruct x;") shouldHave {
      case DestructPE(_,LookupPE(StringP(_,"x"),None)) =>
    }
  }

  test("Dot on function call's result") {
    compile("Wizard(8).charges;") shouldHave {
      case DotPE(_,
          FunctionCallPE(_,None,
            LookupPE(StringP(_, "Wizard"), None),
            List(IntLiteralPE(_,8)),
            BorrowP),
          LookupPE(StringP(_, "charges"), None)) =>
    }
  }

  test("Let with pattern with only a capture") {
    compile("a = m;") shouldHave {
      case LetPE(_,List(),Patterns.capture("a"),LookupPE(StringP(_, "m"), None)) =>
    }
  }

  test("Let with simple pattern") {
    compile("a Moo = m;") shouldHave {
      case LetPE(_,
      List(),
          PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "a")),FinalP)),Some(NameOrRunePT(StringP(_, "Moo"))),None,None),
          LookupPE(StringP(_, "m"), None)) =>
    }
  }

  test("Let with simple pattern in seq") {
    compile("(a Moo) = m;") shouldHave {
      case LetPE(_,
      List(),
          PatternPP(_,_,
            None,
            None,
            Some(DestructureP(_,List(PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "a")),FinalP)),Some(NameOrRunePT(StringP(_, "Moo"))),None,None)))),
            None),
          LookupPE(StringP(_, "m"), None)) =>
    }
  }

  test("Let with destructuring pattern") {
    compile("Muta() = m;") shouldHave {
      case LetPE(_,List(),PatternPP(_,_,None,Some(NameOrRunePT(StringP(_, "Muta"))),Some(DestructureP(_,List())),None),LookupPE(StringP(_, "m"), None)) =>
    }
  }

  test("Ret") {
    compile("ret 3;") shouldHave {
      case ReturnPE(_,IntLiteralPE(_,3)) =>
    }
  }


  test("eachI") {
    compile("eachI row (cellI, cell){ 0 }") shouldHave {
      case FunctionCallPE(_,None,
        LookupPE(StringP(_, "eachI"),None),
        List(
          LookupPE(StringP(_, "row"),None),
          LambdaPE(_,
            FunctionP(
              _,None,None,None,None,None,
              Some(
                ParamsP(_,
                  List(
                    PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "cellI")),FinalP)),None,None,None),
                    PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "cell")),FinalP)),None,None,None)))),
              None,
              Some(BlockPE(_,List(IntLiteralPE(_,0))))))),
        BorrowP) =>
    }
  }

  test("eachI with borrow") {
    compile("eachI &row (cellI, cell){ 0 }") shouldHave {
      case FunctionCallPE(_,None,
        LookupPE(StringP(_, "eachI"),None),
        List(
          LendPE(_,LookupPE(StringP(_, "row"),None)),
          LambdaPE(_,
            FunctionP(
              _,None,None,None,None,None,
              Some(
                ParamsP(_,
                  List(
                    PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "cellI")),FinalP)),None,None,None),
                    PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "cell")),FinalP)),None,None,None)))),
              None,
              Some(BlockPE(_,List(IntLiteralPE(_,0))))))),
        BorrowP) =>
    }
  }
}
