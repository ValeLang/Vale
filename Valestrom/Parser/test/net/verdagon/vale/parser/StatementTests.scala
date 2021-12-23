package net.verdagon.vale.parser

import net.verdagon.vale.{Collector, vassert, vimpl}
import org.scalatest.{FunSuite, Matchers}

class StatementTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("Simple let") {
    compile(CombinatorParsers.statement, "x = 4;") shouldHave {
      case LetPE(_,None, PatternPP(_,_,Some(CaptureP(_,LocalNameP(NameP(_, "x")))), None, None, None), ConstantIntPE(_, 4, _)) =>
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
                Vector(
                  PatternPP(_,_,Some(CaptureP(_,LocalNameP(NameP(_, "x")))),None,None,None),
                  PatternPP(_,_,Some(CaptureP(_,LocalNameP(NameP(_, "y")))),None,None,None)))),
            None),
          TuplePE(_,Vector(ConstantIntPE(_, 4, _), ConstantIntPE(_, 5, _)))) =>
    }
  }

  test("9") {
    compile(CombinatorParsers.statement, "set x.a = 5;") shouldHave {
      case MutatePE(_, DotPE(_, LookupPE(NameP(_, "x"), None), _, false, NameP(_, "a")), ConstantIntPE(_, 5, _)) =>
    }
  }

  test("1PE") {
    compile(CombinatorParsers.statement, """set board.PE.PE.symbol = "v";""") shouldHave {
      case MutatePE(_, DotPE(_, DotPE(_, DotPE(_, LookupPE(NameP(_, "board"), None), _, false, NameP(_, "PE")), _, false, NameP(_, "PE")), _, false, NameP(_, "symbol")), ConstantStrPE(_, "v")) =>
    }
  }

  test("Test simple let") {
    compile(CombinatorParsers.statement, "x = 3;") shouldHave {
      case LetPE(_,None,PatternPP(_,_,Some(CaptureP(_,LocalNameP(NameP(_, "x")))),None,None,None),ConstantIntPE(_, 3, _)) =>
    }
  }

  test("Test varying let") {
    compile(CombinatorParsers.statement, "x! = 3;") shouldHave {
      case LetPE(_,None,PatternPP(_,_,Some(CaptureP(_,LocalNameP(NameP(_, "x")))),None,None,None),ConstantIntPE(_, 3, _)) =>
    }
  }

  test("Test simple mut") {
    compile(CombinatorParsers.statement, "set x = 5;") shouldHave {
      case MutatePE(_, LookupPE(NameP(_, "x"), None),ConstantIntPE(_, 5, _)) =>
    }
  }

  test("Test expr starting with ret") {
    // This test is here because we had a bug where we didn't check that there
    // was whitespace after a "ret".
    compile(CombinatorParsers.statement, "retcode();") shouldHave {
      case FunctionCallPE(_,_,_,_,LookupPE(NameP(_,"retcode"),None),Vector(),_) =>
    }
  }

  test("Test destruct") {
    compile(CombinatorParsers.statement, "destruct x;") shouldHave {
      case DestructPE(_,LookupPE(NameP(_,"x"), None)) =>
    }
  }

  test("Dot on function call's result") {
    compile(CombinatorParsers.statement, "Wizard(8).charges;") shouldHave {
      case DotPE(_,
          FunctionCallPE(_,None,_, false,
            LookupPE(NameP(_, "Wizard"), None),
            Vector(ConstantIntPE(_, 8, _)),
            LoadAsBorrowP(Some(ReadonlyP))),
        _, false,
        NameP(_, "charges")) =>
    }
  }

  test("Let with pattern with only a capture") {
    compile(CombinatorParsers.statement, "a = m;") shouldHave {
      case LetPE(_,None,Patterns.capture("a"),LookupPE(NameP(_, "m"), None)) =>
    }
  }

  test("Let with simple pattern") {
    compile(CombinatorParsers.statement, "a Moo = m;") shouldHave {
      case LetPE(_,
      None,
          PatternPP(_,_,Some(CaptureP(_,LocalNameP(NameP(_, "a")))),Some(NameOrRunePT(NameP(_, "Moo"))),None,None),
          LookupPE(NameP(_, "m"), None)) =>
    }
  }

  test("Let with simple pattern in seq") {
    compile(CombinatorParsers.statement, "(a Moo) = m;") shouldHave {
      case LetPE(_,
      None,
          PatternPP(_,_,
            None,
            None,
            Some(DestructureP(_,Vector(PatternPP(_,_,Some(CaptureP(_,LocalNameP(NameP(_, "a")))),Some(NameOrRunePT(NameP(_, "Moo"))),None,None)))),
            None),
          LookupPE(NameP(_, "m"), None)) =>
    }
  }

  test("Let with destructuring pattern") {
    compile(CombinatorParsers.statement, "Muta() = m;") shouldHave {
      case LetPE(_,None,PatternPP(_,_,None,Some(NameOrRunePT(NameP(_, "Muta"))),Some(DestructureP(_,Vector())),None),LookupPE(NameP(_, "m"), None)) =>
    }
  }

  test("Ret") {
    compile(CombinatorParsers.statement, "ret 3;") shouldHave {
      case ReturnPE(_,ConstantIntPE(_, 3, _)) =>
    }
  }


  test("eachI") {
    compile(CombinatorParsers.statement, "eachI row (cellI, cell){ 0 }") shouldHave {
      case FunctionCallPE(_,None,_, false,
        LookupPE(NameP(_, "eachI"), None),
        Vector(
          LookupPE(NameP(_, "row"), None),
          LambdaPE(_,
            FunctionP(_,
              FunctionHeaderP(_,
                None,Vector(),None,None,
                Some(
                  ParamsP(_,
                    Vector(
                      PatternPP(_,_,Some(CaptureP(_,LocalNameP(NameP(_, "cellI")))),None,None,None),
                      PatternPP(_,_,Some(CaptureP(_,LocalNameP(NameP(_, "cell")))),None,None,None)))),
                FunctionReturnP(_, None, None)),
              Some(BlockPE(_,Vector(ConstantIntPE(_, 0, _))))))),
        LoadAsBorrowP(None)) =>
    }
  }

  test("eachI with borrow") {
    compile(CombinatorParsers.statement, "eachI *row (cellI, cell){ 0 }") shouldHave {
      case FunctionCallPE(_,None,_, false,
      LookupPE(NameP(_, "eachI"), None),
        Vector(
          LoadPE(_,LookupPE(NameP(_, "row"), None), LoadAsPointerP(Some(ReadonlyP))),
          LambdaPE(_,
            FunctionP(_,
              FunctionHeaderP(
                _,None,Vector(),None,None,
                Some(
                  ParamsP(_,
                    Vector(
                      PatternPP(_,_,Some(CaptureP(_,LocalNameP(NameP(_, "cellI")))),None,None,None),
                      PatternPP(_,_,Some(CaptureP(_,LocalNameP(NameP(_, "cell")))),None,None,None)))),
                FunctionReturnP(_, None, None)),
              Some(BlockPE(_,Vector(ConstantIntPE(_, 0, _))))))),
        LoadAsBorrowP(None)) =>
    }
  }

  test("Test block's trailing void presence") {
    compile(CombinatorParsers.filledBody, "{ moo() }") shouldHave {
      case BlockPE(_, Vector(FunctionCallPE(_, None, _, false, LookupPE(NameP(_, "moo"), None), Vector(), LoadAsBorrowP(Some(ReadonlyP))))) =>
    }

    compile(CombinatorParsers.filledBody, "{ moo(); }") shouldHave {
      case BlockPE(_, Vector(FunctionCallPE(_, None, _, false, LookupPE(NameP(_, "moo"), None), Vector(), LoadAsBorrowP(Some(ReadonlyP))), VoidPE(_))) =>
    }
  }


  test("Block with only a result") {
    compile(
      CombinatorParsers.blockExprs,
      "= doThings(a);") shouldHave {
      case Vector(FunctionCallPE(_, None, _, false, LookupPE(NameP(_, "doThings"), None), Vector(LookupPE(NameP(_, "a"), None)), LoadAsBorrowP(Some(ReadonlyP)))) =>
    }
  }


  test("Block with statement and result") {
    compile(
      CombinatorParsers.blockExprs,
      """
        |b;
        |= a;
      """.stripMargin) shouldHave {
      case Vector(LookupPE(NameP(_, "b"), None), LookupPE(NameP(_, "a"), None)) =>
    }
  }


  test("Block with result") {
    compile(CombinatorParsers.blockExprs,"= 3;") shouldHave {
      case Vector(ConstantIntPE(_, 3, _)) =>
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
      case Vector(
        LetPE(_,None, PatternPP(_, _,Some(CaptureP(_,LocalNameP(NameP(_, "a")))), None, None, None), ConstantIntPE(_, 2, _)),
        FunctionCallPE(_, None, _, false, LookupPE(NameP(_, "doThings"), None), Vector(LookupPE(NameP(_, "a"), None)), LoadAsBorrowP(Some(ReadonlyP)))) =>
    }
  }

  test("Mutating as statement") {
    val program = compile(CombinatorParsers.topLevelFunction, "fn main() int export { set x = 6; }")
    program shouldHave {
      case MutatePE(_,LookupPE(NameP(_, "x"), None),ConstantIntPE(_, 6, _)) =>
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

  // To support the examples on the site for the syntax highlighter
  test("empty") {
    compile(CombinatorParsers.statement,"...") shouldHave {
      case LookupPE(NameP(_,"..."),None) =>
    }
  }
}
