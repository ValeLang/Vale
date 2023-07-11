package dev.vale.parsing

import dev.vale.{Collector, Interner, StrI, vimpl, vwat}
import dev.vale.parsing.ast.{AugmentPE, BlockPE, BorrowP, ConsecutorPE, ConstantBoolPE, ConstantIntPE, ConstantStrPE, DestructPE, DestructureP, DotPE, EachPE, FunctionCallPE, IExpressionPE, IfPE, LetPE, LocalNameDeclarationP, LookupNameP, LookupPE, MutatePE, NameOrRunePT, NameP, PatternPP, Patterns, ReturnPE, TuplePE, UnletPE, VoidPE}
import dev.vale.parsing.ast._
import dev.vale.lexing.{CantUseThatLocalName, BadExpressionEnd, BadStartOfStatementError, ForgotSetKeyword}
import dev.vale.options.GlobalOptions
import org.scalatest._

class StatementTests extends FunSuite with Collector with TestParseUtils {

  test("Simple let") {
    compileBlockContentsExpect( "x = 4;") shouldHave {
      case LetPE(_, PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("x"))), None)), None, None), ConstantIntPE(_, 4, _)) =>
    }
  }

  test("multiple statements") {
    compileBlockContentsExpect(
      """4""".stripMargin) shouldHave {
      case ConstantIntPE(_, 4, _) =>
    }

    compileBlockContentsExpect(
      """4;""".stripMargin) shouldHave {
      case ConsecutorPE(Vector(ConstantIntPE(_, 4, _), VoidPE(_))) =>
    }

    compileBlockContentsExpect(
      """4; 3""".stripMargin) shouldHave {
      case ConsecutorPE(Vector(ConstantIntPE(_, 4, _), ConstantIntPE(_, 3, _))) =>
    }

    compileBlockContentsExpect(
      """4; 3;""".stripMargin) shouldHave {
      case ConsecutorPE(Vector(ConstantIntPE(_, 4, _), ConstantIntPE(_, 3, _), VoidPE(_))) =>
    }
  }

  test("8") {
    compileStatementExpect("[x, y] = (4, 5);") shouldHave {
      case LetPE(_,
          PatternPP(_,
            None,
            None,
            Some(
              DestructureP(_,
                Vector(
                  PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("x"))), None)),None,None),
                  PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("y"))), None)),None,None))))),
          TuplePE(_,Vector(ConstantIntPE(_, 4, _), ConstantIntPE(_, 5, _)))) =>
    }
  }

  test("9") {
    compileStatementExpect("set x.a = 5;") shouldHave {
      case MutatePE(_, DotPE(_, LookupPE(LookupNameP(NameP(_, StrI("x"))), None), _, NameP(_, StrI("a"))), ConstantIntPE(_, 5, _)) =>
    }
  }

  test("1PE") {
    compileStatementExpect("""set board.PE.PE.symbol = "v";""") shouldHave {
      case MutatePE(_, DotPE(_, DotPE(_, DotPE(_, LookupPE(LookupNameP(NameP(_, StrI("board"))), None), _, NameP(_, StrI("PE"))), _, NameP(_, StrI("PE"))), _, NameP(_, StrI("symbol"))), ConstantStrPE(_, "v")) =>
    }
  }

  test("Test simple let") {
    compileStatementExpect("x = 3;") shouldHave {
      case LetPE(_,PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("x"))), None)),None,None),ConstantIntPE(_, 3, _)) =>
    }
  }

  test("Test simple mut") {
    compileStatementExpect("set x = 5;") shouldHave {
      case MutatePE(_, LookupPE(LookupNameP(NameP(_, StrI("x"))), None),ConstantIntPE(_, 5, _)) =>
    }
  }

  test("Test expr starting with return") {
    // This test is here because we had a bug where we didn't check that there
    // was whitespace after a "return".
    compileStatementExpect("retcode()") shouldHave {
      case FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_, StrI("retcode"))),None),Vector()) =>
    }
  }

  test("Test inner set") {
    // This test is here because we had a bug where we didn't check that there
    // was whitespace after a "return".
    compileStatementExpect(
      "oldArray = set list.array = newArray;") shouldHave {
      case LetPE(_,
        PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("oldArray"))), None)),None,None),
        MutatePE(_,
          DotPE(_,LookupPE(LookupNameP(NameP(_, StrI("list"))),None),_,NameP(_, StrI("array"))),
          LookupPE(LookupNameP(NameP(_, StrI("newArray"))),None))) =>
    }
  }

  test("Test if-statement producing") {
    // This test is here because we had a bug where we didn't check that there
    // was whitespace after a "return".
    compileStatementExpect(
      "if true { 3 } else { 4 }") shouldHave {
      case IfPE(_,
        ConstantBoolPE(_,true),
        BlockPE(_,None,None,ConstantIntPE(_,3,_)),
        BlockPE(_,None,None,ConstantIntPE(_,4,_))) =>
    }
  }

  test("Test destruct") {
    compileStatementExpect("destruct x;") shouldHave {
      case DestructPE(_,LookupPE(LookupNameP(NameP(_, StrI("x"))), None)) =>
    }
  }

  test("Test unlet") {
    compileStatementExpect("unlet x") shouldHave {
      case UnletPE(_,LookupNameP(NameP(_, StrI("x")))) =>
    }
  }

  test("Dot on function call's result") {
    compileStatementExpect("Wizard(8).charges") shouldHave {
      case DotPE(_,
          FunctionCallPE(_,_,
            LookupPE(LookupNameP(NameP(_, StrI("Wizard"))), None),
            Vector(ConstantIntPE(_, 8, _))),
        _,
      NameP(_, StrI("charges"))) =>
    }
  }

  test("Let with pattern with only a capture") {
    compileStatementExpect("a = m;") shouldHave {
      case LetPE(_,Patterns.capture("a"),LookupPE(LookupNameP(NameP(_, StrI("m"))), None)) =>
    }
  }

  test("Let with simple pattern") {
    compileStatementExpect("a Moo = m;") shouldHave {
      case LetPE(_,
        PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("a"))), None)),Some(NameOrRunePT(NameP(_, StrI("Moo")))),None),
        LookupPE(LookupNameP(NameP(_, StrI("m"))), None)) =>
    }
  }

  test("Let with simple pattern in destructure") {
    compileStatementExpect("[a Moo] = m;") shouldHave {
      case LetPE(_,
          PatternPP(_,_,
            None,
            Some(DestructureP(_,Vector(PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("a"))), None)),Some(NameOrRunePT(NameP(_, StrI("Moo")))),None))))),
          LookupPE(LookupNameP(NameP(_, StrI("m"))), None)) =>
    }
  }

  test("Let with destructuring pattern") {
    compileStatementExpect("Muta[ ] = m;") shouldHave {
      case LetPE(_,PatternPP(_,None,Some(NameOrRunePT(NameP(_, StrI("Muta")))),Some(DestructureP(_,Vector()))),LookupPE(LookupNameP(NameP(_, StrI("m"))), None)) =>
    }
  }

  test("Destructure pattern with let and set") {
    compileStatementExpect("[a, set x] = m;") shouldHave {
      case LetPE(_,
        PatternPP(_,
          None,None,
          Some(
            DestructureP(_,
              Vector(
                PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_,StrI("a"))),None)),None,None),
                PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_,StrI("x"))),Some(_))),None,None))))),
        _) =>
    }
  }

  test("Ret") {
    compileStatementExpect("return 3;") shouldHave {
      case ReturnPE(_,ConstantIntPE(_, 3, _)) =>
    }
  }

  test("foreach") {
    compileStatementExpect("foreach i in myList { }") shouldHave {
      case EachPE(_,
      None,
      PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("i"))), None)),None,None),
      _,
      LookupPE(LookupNameP(NameP(_, StrI("myList"))),None),
      BlockPE(_,None,None,_)) =>
    }
  }

  test("foreach with borrow") {
    compileStatementExpect("foreach i in &myList { }") shouldHave {
      case EachPE(_,
      None,
      PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("i"))), None)),None,None),
      _,
      AugmentPE(_, BorrowP, LookupPE(LookupNameP(NameP(_, StrI("myList"))),None)),
      BlockPE(_,None,None,_)) =>
    }
  }

  test("foreach with two receivers") {
    compileStatementExpect("foreach [a, b] in myList { }") shouldHave {
      case EachPE(_,
        None,
        PatternPP(_,
          None,None,
          Some(
            DestructureP(_,
              Vector(
                PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("a"))), None)),None,None),
                PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("b"))), None)),None,None))))),
        _,
        LookupPE(LookupNameP(NameP(_, StrI("myList"))),None),
        BlockPE(_,None,None,_)) =>
    }
  }

  test("foreach complex iterable") {
    compileStatementExpect("foreach i in myList = 3; myList { }") shouldHave {
      case EachPE(_,
        None,
        PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("i"))), None)),None,None),
        _,
        ConsecutorPE(
          Vector(
            LetPE(_,PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("myList"))), None)),None,None),ConstantIntPE(_,3,_)),
            LookupPE(LookupNameP(NameP(_, StrI("myList"))),None))),
        BlockPE(_,None,None,VoidPE(_))) =>
    }
  }

  test("Multiple statements") {
    compileBlockContentsExpect(
      """
        |42;
        |43;
        |""".stripMargin)
  }

  test("If and another statement") {
    compileBlockContentsExpect(
      """
        |newCapacity = if (true) { 1 } else { 2 };
        |newArray = 3;
        |""".stripMargin)
  }

  test("Test block's trailing void presence") {
    compileBlockContentsExpect(
      "moo()") shouldHave {
      case FunctionCallPE(_, _, LookupPE(LookupNameP(NameP(_, StrI("moo"))), None), Vector()) =>
    }

    compileBlockContentsExpect(
      "moo();") shouldHave {
      case ConsecutorPE(Vector(FunctionCallPE(_, _, LookupPE(LookupNameP(NameP(_, StrI("moo"))), None), Vector()), VoidPE(_))) =>
    }
  }


  test("Block with statement and result") {
    compileBlockContentsExpect(
      """
        |b;
        |a
      """.stripMargin) shouldHave {
      case Vector(LookupPE(LookupNameP(NameP(_, StrI("b"))), None), LookupPE(LookupNameP(NameP(_, StrI("a"))), None)) =>
    }
  }


  test("Block with result") {
    compileStatementExpect("3") shouldHave {
      case ConstantIntPE(_, 3, _) =>
    }
  }

  test("Block with result that could be an expr") {
    // = doThings(a); could be misinterpreted as an expression doThings(=, a) if we're
    // not careful.
    compileBlockContentsExpect(
      """
        |a = 2;
        |doThings(a)
      """.stripMargin) shouldHave {
      case Vector(
        LetPE(_, PatternPP(_, Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("a"))), None)), None, None), ConstantIntPE(_, 2, _)),
        FunctionCallPE(_, _, LookupPE(LookupNameP(NameP(_, StrI("doThings"))), None), Vector(LookupPE(LookupNameP(NameP(_, StrI("a"))), None)))) =>
    }
  }

  test("Mutating as statement") {
    val program =
      compileBlockContentsExpect(
        "set x = 6;")
    program shouldHave {
      case MutatePE(_,LookupPE(LookupNameP(NameP(_, StrI("x"))), None),ConstantIntPE(_, 6, _)) =>
    }
  }

  test("Lone block") {
    compileBlockContentsExpect(
      """
        |block {
        |  a
        |}
      """.stripMargin) shouldHave {
      case BlockPE(_,None,None,LookupPE(LookupNameP(NameP(_, StrI("a"))),None)) =>
    }
  }

  test("Pure block") {
    // Just make sure it parses, so that we can highlight it.
    // The pure block feature doesn't actually exist yet.
    compileBlockContentsExpect(
      """
        |pure block {
        |  a
        |}
      """.stripMargin)
  }

  test("Unsafe pure block") {
    // Just make sure it parses, so that we can highlight it.
    // The unsafe pure block feature doesn't actually exist yet.
    compileBlockContentsExpect(
      """
        |unsafe pure block {
        |  a
        |}
      """.stripMargin)
  }


  test("Report leaving out semicolon or ending body after expression, for square") {
    compileStatement(
      """
        |block {
        |  floop() ]
        |}
        """.stripMargin).expectErr() match {
      case BadStartOfStatementError(_) =>
    }
  }

  test("Empty block") {
    compileBlockContentsExpect(
      """
        |block {
        |}
        |return 3;
    """.stripMargin) match {
      case ConsecutorPE(
        Vector(
          BlockPE(_,None,None,VoidPE(_)),
          ReturnPE(_,ConstantIntPE(_,3,None)), VoidPE(_))) =>
    }
  }

  test("Cant use set as a local name") {
    val error = compileStatement(
      """[set] = (6,)""".stripMargin).expectErr()
    error match {
      case CantUseThatLocalName(_, "set") =>
    }
  }

  test("foreach 2") {
    val programS =
      compileBlockContentsExpect(
        """
          |foreach i in a {
          |  i
          |}
          |""".stripMargin)
    programS shouldHave {
      case EachPE(_,
        None,
        PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("i"))), None)),None,None),
        _,
        LookupPE(LookupNameP(NameP(_, StrI("a"))),None),
        BlockPE(_,None,None,
          LookupPE(LookupNameP(NameP(_, StrI("i"))),None))) =>
    }
  }

  test("foreach expr") {
    val programS =
      compileBlockContentsExpect(
        """
          |a = foreach i in c { i };
          |""".stripMargin)
    programS shouldHave {
      case ConsecutorPE(Vector(
        LetPE(_,
          PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_,StrI("a"))), None)),None,None),
          EachPE(_,_,_,_,_,_)),
        VoidPE(_))) =>
    }
  }

}
