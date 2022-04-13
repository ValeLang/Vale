package dev.vale.parsing

import dev.vale.{Collector, Interner, StrI, vimpl}
import dev.vale.parsing.ast.{AugmentPE, BlockPE, BorrowP, ConsecutorPE, ConstantBoolPE, ConstantIntPE, ConstantStrPE, DestructPE, DestructureP, DotPE, EachPE, FunctionCallPE, IExpressionPE, IfPE, LetPE, LocalNameDeclarationP, LookupNameP, LookupPE, MutatePE, NameOrRunePT, NameP, PatternPP, Patterns, ReturnPE, TuplePE, UnletPE, VoidPE}
import dev.vale.parsing.ast._
import dev.vale.lexing.ForgotSetKeyword
import dev.vale.options.GlobalOptions
import org.scalatest.{FunSuite, Matchers}

class StatementTests extends FunSuite with Collector with TestParseUtils {

  def compileBlockContents(code: String): IExpressionPE = {
    vimpl()
//    compile(
//      new ExpressionParser(new Interner(), GlobalOptions(true, true, true, true))
//        .parseBlockContents(_), code)
  }

  def compileStatement(code: String): IExpressionPE = {
    vimpl()
//    compile(
//      new ExpressionParser(GlobalOptions(true, true, true, true))
//        .parseStatement(_, stopBefore, expectResult), code)
  }

  test("Simple let") {
    compileBlockContents( "x = 4;") shouldHave {
      case LetPE(_, PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, StrI("x")))), None, None, None), ConstantIntPE(_, 4, _)) =>
    }
  }

  test("multiple statements") {
    compileBlockContents(
      """4""".stripMargin) shouldHave {
      case ConstantIntPE(_, 4, _) =>
    }

    compileBlockContents(
      """4;""".stripMargin) shouldHave {
      case ConsecutorPE(Vector(ConstantIntPE(_, 4, _), VoidPE(_))) =>
    }

    compileBlockContents(
      """4; 3""".stripMargin) shouldHave {
      case ConsecutorPE(Vector(ConstantIntPE(_, 4, _), ConstantIntPE(_, 3, _))) =>
    }

    compileBlockContents(
      """4; 3;""".stripMargin) shouldHave {
      case ConsecutorPE(Vector(ConstantIntPE(_, 4, _), ConstantIntPE(_, 3, _), VoidPE(_))) =>
    }
  }

  test("8") {
    compileStatement("[x, y] = (4, 5)") shouldHave {
      case LetPE(_,
          PatternPP(_,_,
            None,
            None,
            Some(
              DestructureP(_,
                Vector(
                  PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, StrI("x")))),None,None,None),
                  PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, StrI("y")))),None,None,None)))),
            None),
          TuplePE(_,Vector(ConstantIntPE(_, 4, _), ConstantIntPE(_, 5, _)))) =>
    }
  }

  test("9") {
    compileStatement("set x.a = 5") shouldHave {
      case MutatePE(_, DotPE(_, LookupPE(LookupNameP(NameP(_, StrI("x"))), None), _, NameP(_, StrI("a"))), ConstantIntPE(_, 5, _)) =>
    }
  }

  test("1PE") {
    compileStatement("""set board.PE.PE.symbol = "v"""") shouldHave {
      case MutatePE(_, DotPE(_, DotPE(_, DotPE(_, LookupPE(LookupNameP(NameP(_, StrI("board"))), None), _, NameP(_, StrI("PE"))), _, NameP(_, StrI("PE"))), _, NameP(_, StrI("symbol"))), ConstantStrPE(_, "v")) =>
    }
  }

  test("Test simple let") {
    compileStatement("x = 3") shouldHave {
      case LetPE(_,PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, StrI("x")))),None,None,None),ConstantIntPE(_, 3, _)) =>
    }
  }

  test("Test simple mut") {
    compileStatement("set x = 5") shouldHave {
      case MutatePE(_, LookupPE(LookupNameP(NameP(_, StrI("x"))), None),ConstantIntPE(_, 5, _)) =>
    }
  }

  test("Test expr starting with return") {
    // This test is here because we had a bug where we didn't check that there
    // was whitespace after a "return".
    compileStatement("retcode()") shouldHave {
      case FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_, StrI("retcode"))),None),Vector()) =>
    }
  }

  test("Test inner set") {
    // This test is here because we had a bug where we didn't check that there
    // was whitespace after a "return".
    compileStatement(
      "oldArray = set list.array = newArray") shouldHave {
      case LetPE(_,
        PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_, StrI("oldArray")))),None,None,None),
        MutatePE(_,
          DotPE(_,LookupPE(LookupNameP(NameP(_, StrI("list"))),None),_,NameP(_, StrI("array"))),
          LookupPE(LookupNameP(NameP(_, StrI("newArray"))),None))) =>
    }
  }

  test("Test if-statement producing") {
    // This test is here because we had a bug where we didn't check that there
    // was whitespace after a "return".
    compileStatement(
      "if true { 3 } else { 4 }") shouldHave {
      case IfPE(_,
        ConstantBoolPE(_,true),
        BlockPE(_,ConstantIntPE(_,3,_)),
        BlockPE(_,ConstantIntPE(_,4,_))) =>
    }
  }

  test("Test destruct") {
    compileStatement("destruct x") shouldHave {
      case DestructPE(_,LookupPE(LookupNameP(NameP(_, StrI("x"))), None)) =>
    }
  }

  test("Test unlet") {
    compileStatement("unlet x") shouldHave {
      case UnletPE(_,LookupNameP(NameP(_, StrI("x")))) =>
    }
  }

  test("Dot on function call's result") {
    compileStatement("Wizard(8).charges") shouldHave {
      case DotPE(_,
          FunctionCallPE(_,_,
            LookupPE(LookupNameP(NameP(_, StrI("Wizard"))), None),
            Vector(ConstantIntPE(_, 8, _))),
        _,
      NameP(_, StrI("charges"))) =>
    }
  }

  test("Let with pattern with only a capture") {
    compileStatement("a = m") shouldHave {
      case LetPE(_,Patterns.capture("a"),LookupPE(LookupNameP(NameP(_, StrI("m"))), None)) =>
    }
  }

  test("Let with simple pattern") {
    compileStatement("a Moo = m") shouldHave {
      case LetPE(_,
        PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, StrI("a")))),Some(NameOrRunePT(NameP(_, StrI("Moo")))),None,None),
        LookupPE(LookupNameP(NameP(_, StrI("m"))), None)) =>
    }
  }

  test("Let with simple pattern in seq") {
    compileStatement("[a Moo] = m") shouldHave {
      case LetPE(_,
          PatternPP(_,_,
            None,
            None,
            Some(DestructureP(_,Vector(PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, StrI("a")))),Some(NameOrRunePT(NameP(_, StrI("Moo")))),None,None)))),
            None),
          LookupPE(LookupNameP(NameP(_, StrI("m"))), None)) =>
    }
  }

  test("Let with destructuring pattern") {
    compileStatement("Muta[ ] = m") shouldHave {
      case LetPE(_,PatternPP(_,_,None,Some(NameOrRunePT(NameP(_, StrI("Muta")))),Some(DestructureP(_,Vector())),None),LookupPE(LookupNameP(NameP(_, StrI("m"))), None)) =>
    }
  }

  test("Ret") {
    compileStatement("return 3") shouldHave {
      case ReturnPE(_,ConstantIntPE(_, 3, _)) =>
    }
  }

  test("foreach") {
    compileStatement("foreach i in myList { }") shouldHave {
      case EachPE(_,
      PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_, StrI("i")))),None,None,None),
      _,
      LookupPE(LookupNameP(NameP(_, StrI("myList"))),None),
      BlockPE(_,_)) =>
    }
  }

  test("foreach with borrow") {
    compileStatement("foreach i in &myList { }") shouldHave {
      case EachPE(_,
      PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_, StrI("i")))),None,None,None),
      _,
      AugmentPE(_, BorrowP, LookupPE(LookupNameP(NameP(_, StrI("myList"))),None)),
      BlockPE(_,_)) =>
    }
  }

  test("foreach with two receivers") {
    compileStatement("foreach [a, b] in myList { }") shouldHave {
      case EachPE(_,
        PatternPP(_,
          None,None,None,
          Some(
            DestructureP(_,
              Vector(
                PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_, StrI("a")))),None,None,None),
                PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_, StrI("b")))),None,None,None)))),
          None),
        _,
        LookupPE(LookupNameP(NameP(_, StrI("myList"))),None),
        BlockPE(_,_)) =>
    }
  }

  test("foreach complex iterable") {
    compileStatement("foreach i in myList = 3; myList { }") shouldHave {
      case EachPE(_,
        PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_, StrI("i")))),None,None,None),
        _,
        ConsecutorPE(
          Vector(
            LetPE(_,PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_, StrI("myList")))),None,None,None),ConstantIntPE(_,3,_)),
            LookupPE(LookupNameP(NameP(_, StrI("myList"))),None))),
        BlockPE(_,VoidPE(_))) =>
    }
  }

  test("Multiple statements") {
    compileBlockContents(
      """
        |42;
        |43;
        |""".stripMargin)
  }

  test("If and another statement") {
    compileBlockContents(
      """
        |newCapacity = if (true) { 1 } else { 2 };
        |newArray = 3;
        |""".stripMargin)
  }

  test("Test block's trailing void presence") {
    compileBlockContents(
      "moo()") shouldHave {
      case FunctionCallPE(_, _, LookupPE(LookupNameP(NameP(_, StrI("moo"))), None), Vector()) =>
    }

    compileBlockContents(
      "moo();") shouldHave {
      case ConsecutorPE(Vector(FunctionCallPE(_, _, LookupPE(LookupNameP(NameP(_, StrI("moo"))), None), Vector()), VoidPE(_))) =>
    }
  }


  test("Block with statement and result") {
    compileBlockContents(
      """
        |b;
        |a
      """.stripMargin) shouldHave {
      case Vector(LookupPE(LookupNameP(NameP(_, StrI("b"))), None), LookupPE(LookupNameP(NameP(_, StrI("a"))), None)) =>
    }
  }


  test("Block with result") {
    compileStatement("3") shouldHave {
      case ConstantIntPE(_, 3, _) =>
    }
  }

  test("Block with result that could be an expr") {
    // = doThings(a); could be misinterpreted as an expression doThings(=, a) if we're
    // not careful.
    compileBlockContents(
      """
        |a = 2;
        |doThings(a)
      """.stripMargin) shouldHave {
      case Vector(
        LetPE(_, PatternPP(_, _,Some(LocalNameDeclarationP(NameP(_, StrI("a")))), None, None, None), ConstantIntPE(_, 2, _)),
        FunctionCallPE(_, _, LookupPE(LookupNameP(NameP(_, StrI("doThings"))), None), Vector(LookupPE(LookupNameP(NameP(_, StrI("a"))), None)))) =>
    }
  }

  test("Mutating as statement") {
    val program =
      compileBlockContents(
        "set x = 6;")
    program shouldHave {
      case MutatePE(_,LookupPE(LookupNameP(NameP(_, StrI("x"))), None),ConstantIntPE(_, 6, _)) =>
    }
  }

  test("Lone block") {
    compileBlockContents(
      """
        |block {
        |  a
        |}
      """.stripMargin) shouldHave {
      case BlockPE(_,LookupPE(LookupNameP(NameP(_, StrI("a"))),None)) =>
    }
  }

  test("Pure block") {
    // Just make sure it parses, so that we can highlight it.
    // The pure block feature doesn't actually exist yet.
    compileBlockContents(
      """
        |pure {
        |  a
        |}
      """.stripMargin)
  }

  test("Unsafe pure block") {
    // Just make sure it parses, so that we can highlight it.
    // The unsafe pure block feature doesn't actually exist yet.
    compileBlockContents(
      """
        |unsafe pure {
        |  a
        |}
      """.stripMargin)
  }


  test("Forgetting set when changing") {
    vimpl()
//    val error =
//      compileForError(
//        new ExpressionParser(GlobalOptions(true, true, true, true))
//          .parseBlockContents(_, StopBeforeCloseBrace),
//        """
//          |ship.x = 4;
//          |""".stripMargin)
//    error match {
//      case ForgotSetKeyword(_) =>
//    }
  }

  test("foreach 2") {
    val programS =
      compileBlockContents(
        """
          |foreach i in a {
          |  i
          |}
          |""".stripMargin)
    programS shouldHave {
      case EachPE(_,
        PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_, StrI("i")))),None,None,None),
        _,
        LookupPE(LookupNameP(NameP(_, StrI("a"))),None),
        BlockPE(_,
          LookupPE(LookupNameP(NameP(_, StrI("i"))),None))) =>
    }
  }

  test("foreach expr") {
    val programS =
      compileBlockContents(
        """
          |a = foreach i in c { i };
          |""".stripMargin)
    programS shouldHave {
      case ConsecutorPE(Vector(
        LetPE(_,
          PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,a))),None,None,None),
          EachPE(_,_,_,_,_)),
        VoidPE(_))) =>
    }
  }

}
