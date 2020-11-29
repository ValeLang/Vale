package net.verdagon.vale.parser

import net.verdagon.vale.{vassert, vimpl}
import org.scalatest.{FunSuite, Matchers}

class StatementTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("Simple let") {
    compile(CombinatorParsers.statement, "x = 4;") shouldHave {
      case LetPE(_,None, PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "x")), FinalP)), None, None, None), IntLiteralPE(_,4)) =>
    }
  }

  test("8") {
    compile(CombinatorParsers.statement, "(x, y) = [4, 5];") shouldHave {
      case LetPE(_,
      None,
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
    compile(CombinatorParsers.statement, "mut x.a = 5;") shouldHave {
      case MutatePE(_, DotPE(_, LookupPE(StringP(_, "x"), None, BorrowP), _, false, BorrowP, StringP(_, "a")), IntLiteralPE(_,5)) =>
    }
  }

  test("1PE") {
    compile(CombinatorParsers.statement, """mut board.PE.PE.symbol = "v";""") shouldHave {
      case MutatePE(_, DotPE(_, DotPE(_, DotPE(_, LookupPE(StringP(_, "board"), None, BorrowP), _, false, BorrowP, StringP(_, "PE")), _, false, BorrowP, StringP(_, "PE")), _, false, BorrowP, StringP(_, "symbol")), StrLiteralPE(_, "v")) =>
    }
  }

  test("Test simple let") {
    compile(CombinatorParsers.statement, "x = 3;") shouldHave {
      case LetPE(_,None,PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "x")),FinalP)),None,None,None),IntLiteralPE(_,3)) =>
    }
  }

  test("Test varying let") {
    compile(CombinatorParsers.statement, "x! = 3;") shouldHave {
      case LetPE(_,None,PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "x")),VaryingP)),None,None,None),IntLiteralPE(_,3)) =>
    }
  }

  test("Test simple mut") {
    compile(CombinatorParsers.statement, "mut x = 5;") shouldHave {
      case MutatePE(_, LookupPE(StringP(_, "x"), None, BorrowP),IntLiteralPE(_,5)) =>
    }
  }

  test("Test destruct") {
    compile(CombinatorParsers.statement, "destruct ^x;") shouldHave {
      case DestructPE(_,LookupPE(StringP(_,"x"), None, OwnP)) =>
    }
  }

  test("Dot on function call's result") {
    compile(CombinatorParsers.statement, "Wizard(8).charges;") shouldHave {
      case DotPE(_,
          FunctionCallPE(_,None,_, false,
            LookupPE(StringP(_, "Wizard"), None, BorrowP),
            List(IntLiteralPE(_,8))),
        _, false,
        BorrowP,
        StringP(_, "charges")) =>
    }
  }

  test("Let with pattern with only a capture") {
    compile(CombinatorParsers.statement, "a = m;") shouldHave {
      case LetPE(_,None,Patterns.capture("a"),LookupPE(StringP(_, "m"), None, BorrowP)) =>
    }
  }

  test("Let with simple pattern") {
    compile(CombinatorParsers.statement, "a Moo = m;") shouldHave {
      case LetPE(_,
      None,
          PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "a")),FinalP)),Some(NameOrRunePT(StringP(_, "Moo"))),None,None),
          LookupPE(StringP(_, "m"), None, BorrowP)) =>
    }
  }

  test("Let with simple pattern in seq") {
    compile(CombinatorParsers.statement, "(a Moo) = m;") shouldHave {
      case LetPE(_,
      None,
          PatternPP(_,_,
            None,
            None,
            Some(DestructureP(_,List(PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "a")),FinalP)),Some(NameOrRunePT(StringP(_, "Moo"))),None,None)))),
            None),
          LookupPE(StringP(_, "m"), None, BorrowP)) =>
    }
  }

  test("Let with destructuring pattern") {
    compile(CombinatorParsers.statement, "Muta() = m;") shouldHave {
      case LetPE(_,None,PatternPP(_,_,None,Some(NameOrRunePT(StringP(_, "Muta"))),Some(DestructureP(_,List())),None),LookupPE(StringP(_, "m"), None, BorrowP)) =>
    }
  }

  test("Ret") {
    compile(CombinatorParsers.statement, "ret 3;") shouldHave {
      case ReturnPE(_,IntLiteralPE(_,3)) =>
    }
  }


  test("eachI") {
    compile(CombinatorParsers.statement, "eachI row (cellI, cell){ 0 }") shouldHave {
      case FunctionCallPE(_,None,_, false,
      LookupPE(StringP(_, "eachI"), None, BorrowP),
        List(
          LookupPE(StringP(_, "row"), None, BorrowP),
          LambdaPE(_,
            FunctionP(_,
              FunctionHeaderP(_,
                None,List(),None,None,
                Some(
                  ParamsP(_,
                    List(
                      PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "cellI")),FinalP)),None,None,None),
                      PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "cell")),FinalP)),None,None,None)))),
                FunctionReturnP(_, None, None)),
              Some(BlockPE(_,List(IntLiteralPE(_,0)))))))) =>
    }
  }

  test("eachI with move") {
    compile(CombinatorParsers.statement, "eachI ^row (cellI, cell){ 0 }") shouldHave {
      case FunctionCallPE(_,None,_, false,
        LookupPE(StringP(_, "eachI"), None, BorrowP),
          List(
            LookupPE(StringP(_, "row"), None, OwnP),
            LambdaPE(_,
              FunctionP(_,
                FunctionHeaderP(
                  _,None,List(),None,None,
                  Some(
                    ParamsP(_,
                      List(
                        PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "cellI")),FinalP)),None,None,None),
                        PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "cell")),FinalP)),None,None,None)))),
                  FunctionReturnP(_, None, None)),
                Some(BlockPE(_,List(IntLiteralPE(_,0)))))))) =>
    }
  }

  test("Test block's trailing void presence") {
    compile(CombinatorParsers.filledBody, "{ moo() }") shouldHave {
      case BlockPE(_, List(FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "moo"), None, BorrowP), List()))) =>
    }

    compile(CombinatorParsers.filledBody, "{ moo(); }") shouldHave {
      case BlockPE(_, List(FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "moo"), None, BorrowP), List()), VoidPE(_))) =>
    }
  }


  test("Block with only a result") {
    compile(
      CombinatorParsers.blockExprs,
      "= doThings(a);") shouldHave {
      case List(FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "doThings"), None, BorrowP), List(LookupPE(StringP(_, "a"), None, BorrowP)))) =>
    }
  }


  test("Block with statement and result") {
    compile(
      CombinatorParsers.blockExprs,
      """
        |^b;
        |= a;
      """.stripMargin) shouldHave {
      case List(LookupPE(StringP(_, "b"), None, OwnP), LookupPE(StringP(_, "a"), None, BorrowP)) =>
    }
  }


  test("Block with result") {
    compile(CombinatorParsers.blockExprs,"= 3;") shouldHave {
      case List(IntLiteralPE(_, 3)) =>
    }
  }

  test("Block with result that could be an expr") {
    // = doThings(a); could be misinterpreted as an expression doThings(=, a) if we're
    // not careful.
    compile(
      CombinatorParsers.blockExprs,
      """
        |a = 2;
        |= doThings(a);
      """.stripMargin) shouldHave {
      case List(
      LetPE(_,None, PatternPP(_, _,Some(CaptureP(_,LocalNameP(StringP(_, "a")), FinalP)), None, None, None), IntLiteralPE(_, 2)),
      FunctionCallPE(_, None, _, false,
        LookupPE(StringP(_, "doThings"), None, BorrowP),
        List(LookupPE(StringP(_, "a"), None, BorrowP)))) =>
    }
  }

  test("Mutating as statement") {
    val program = compile(CombinatorParsers.topLevelFunction, "fn main() int { mut x = 6; }")
    program shouldHave {
      case MutatePE(_,LookupPE(StringP(_, "x"), None, BorrowP),IntLiteralPE(_, 6)) =>
    }
  }

  test("Bad start of statement") {
    compileProgramForError(
      """
        |fn doCivicDance(virtual this Car) {
        |  )
        |}
        """.stripMargin) match {
      case BadStartOfStatementError(_) =>
    }
    compileProgramForError(
      """
        |fn doCivicDance(virtual this Car) {
        |  ]
        |}
        """.stripMargin) match {
      case BadStartOfStatementError(_) =>
    }
  }
  test("Statement after result or return") {
    compileProgramForError(
      """
        |fn doCivicDance(virtual this Car) {
        |  = 4;
        |  7
        |}
        """.stripMargin) match {
      case StatementAfterResult(_) =>
    }
    compileProgramForError(
      """
        |fn doCivicDance(virtual this Car) {
        |  ret 4;
        |  7
        |}
        """.stripMargin) match {
      case StatementAfterReturn(_) =>
    }
  }
}
