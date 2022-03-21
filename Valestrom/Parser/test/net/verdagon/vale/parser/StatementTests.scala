package net.verdagon.vale.parser

import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.parser.ast._
import net.verdagon.vale.{Collector, vassert, vfail, vimpl}
import org.scalatest.{FunSuite, Matchers}

class StatementTests extends FunSuite with Collector with TestParseUtils {

  def compileBlockContents(stopBefore: IStopBefore, code: String): IExpressionPE = {
    compile(
      new ExpressionParser(GlobalOptions(true, true, true, true))
        .parseBlockContents(_, stopBefore), code)
  }

  def compileStatement(stopBefore: IStopBefore, expectResult: Boolean, code: String): IExpressionPE = {
    compile(
      new ExpressionParser(GlobalOptions(true, true, true, true))
        .parseStatement(_, stopBefore, expectResult), code)
  }

  test("Simple let") {
    compileBlockContents(StopBeforeCloseBrace, "x = 4;") shouldHave {
      case LetPE(_, PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, "x"))), None, None, None), ConstantIntPE(_, 4, _)) =>
    }
  }

  test("multiple statements") {
    compileBlockContents(StopBeforeCloseBrace,
      """4""".stripMargin) shouldHave {
      case ConstantIntPE(_, 4, _) =>
    }

    compileBlockContents(StopBeforeCloseBrace,
      """4;""".stripMargin) shouldHave {
      case ConsecutorPE(Vector(ConstantIntPE(_, 4, _), VoidPE(_))) =>
    }

    compileBlockContents(StopBeforeCloseBrace,
      """4; 3""".stripMargin) shouldHave {
      case ConsecutorPE(Vector(ConstantIntPE(_, 4, _), ConstantIntPE(_, 3, _))) =>
    }

    compileBlockContents(StopBeforeCloseBrace,
      """4; 3;""".stripMargin) shouldHave {
      case ConsecutorPE(Vector(ConstantIntPE(_, 4, _), ConstantIntPE(_, 3, _), VoidPE(_))) =>
    }
  }

  test("8") {
    compileStatement(StopBeforeCloseBrace, false, "[x, y] = (4, 5)") shouldHave {
      case LetPE(_,
          PatternPP(_,_,
            None,
            None,
            Some(
              DestructureP(_,
                Vector(
                  PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, "x"))),None,None,None),
                  PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, "y"))),None,None,None)))),
            None),
          TuplePE(_,Vector(ConstantIntPE(_, 4, _), ConstantIntPE(_, 5, _)))) =>
    }
  }

  test("9") {
    compileStatement(StopBeforeCloseBrace, false, "set x.a = 5") shouldHave {
      case MutatePE(_, DotPE(_, LookupPE(LookupNameP(NameP(_, "x")), None), _, NameP(_, "a")), ConstantIntPE(_, 5, _)) =>
    }
  }

  test("1PE") {
    compileStatement(StopBeforeCloseBrace, false, """set board.PE.PE.symbol = "v"""") shouldHave {
      case MutatePE(_, DotPE(_, DotPE(_, DotPE(_, LookupPE(LookupNameP(NameP(_, "board")), None), _, NameP(_, "PE")), _, NameP(_, "PE")), _, NameP(_, "symbol")), ConstantStrPE(_, "v")) =>
    }
  }

  test("Test simple let") {
    compileStatement(StopBeforeCloseBrace, false, "x = 3") shouldHave {
      case LetPE(_,PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, "x"))),None,None,None),ConstantIntPE(_, 3, _)) =>
    }
  }

  test("Test simple mut") {
    compileStatement(StopBeforeCloseBrace, false, "set x = 5") shouldHave {
      case MutatePE(_, LookupPE(LookupNameP(NameP(_, "x")), None),ConstantIntPE(_, 5, _)) =>
    }
  }

  test("Test expr starting with ret") {
    // This test is here because we had a bug where we didn't check that there
    // was whitespace after a "ret".
    compileStatement(StopBeforeCloseBrace, false, "retcode()") shouldHave {
      case FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_, "retcode")),None),Vector()) =>
    }
  }

  test("Test inner set") {
    // This test is here because we had a bug where we didn't check that there
    // was whitespace after a "ret".
    compileStatement(StopBeforeCloseBrace, false,
      "oldArray = set list.array = newArray") shouldHave {
      case LetPE(_,
        PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,"oldArray"))),None,None,None),
        MutatePE(_,
          DotPE(_,LookupPE(LookupNameP(NameP(_,"list")),None),_,NameP(_,"array")),
          LookupPE(LookupNameP(NameP(_,"newArray")),None))) =>
    }
  }

  test("Test if-statement producing") {
    // This test is here because we had a bug where we didn't check that there
    // was whitespace after a "ret".
    compileStatement(StopBeforeCloseBrace, false,
      "if true { 3 } else { 4 }") shouldHave {
      case IfPE(_,
        ConstantBoolPE(_,true),
        BlockPE(_,ConstantIntPE(_,3,_)),
        BlockPE(_,ConstantIntPE(_,4,_))) =>
    }
  }

  test("Test destruct") {
    compileStatement(StopBeforeCloseBrace, false, "destruct x") shouldHave {
      case DestructPE(_,LookupPE(LookupNameP(NameP(_, "x")), None)) =>
    }
  }

  test("Test unlet") {
    compileStatement(StopBeforeCloseBrace, false, "unlet x") shouldHave {
      case UnletPE(_,LookupNameP(NameP(_, "x"))) =>
    }
  }

  test("Dot on function call's result") {
    compileStatement(StopBeforeCloseBrace, false, "Wizard(8).charges") shouldHave {
      case DotPE(_,
          FunctionCallPE(_,_,
            LookupPE(LookupNameP(NameP(_, "Wizard")), None),
            Vector(ConstantIntPE(_, 8, _))),
        _,
      NameP(_, "charges")) =>
    }
  }

  test("Let with pattern with only a capture") {
    compileStatement(StopBeforeCloseBrace, false, "a = m") shouldHave {
      case LetPE(_,Patterns.capture("a"),LookupPE(LookupNameP(NameP(_, "m")), None)) =>
    }
  }

  test("Let with simple pattern") {
    compileStatement(StopBeforeCloseBrace, false, "a Moo = m") shouldHave {
      case LetPE(_,
        PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, "a"))),Some(NameOrRunePT(NameP(_, "Moo"))),None,None),
        LookupPE(LookupNameP(NameP(_, "m")), None)) =>
    }
  }

  test("Let with simple pattern in seq") {
    compileStatement(StopBeforeCloseBrace, false, "[a Moo] = m") shouldHave {
      case LetPE(_,
          PatternPP(_,_,
            None,
            None,
            Some(DestructureP(_,Vector(PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, "a"))),Some(NameOrRunePT(NameP(_, "Moo"))),None,None)))),
            None),
          LookupPE(LookupNameP(NameP(_, "m")), None)) =>
    }
  }

  test("Let with destructuring pattern") {
    compileStatement(StopBeforeCloseBrace, false, "Muta[ ] = m") shouldHave {
      case LetPE(_,PatternPP(_,_,None,Some(NameOrRunePT(NameP(_, "Muta"))),Some(DestructureP(_,Vector())),None),LookupPE(LookupNameP(NameP(_, "m")), None)) =>
    }
  }

  test("Ret") {
    compileStatement(StopBeforeCloseBrace, false, "ret 3") shouldHave {
      case ReturnPE(_,ConstantIntPE(_, 3, _)) =>
    }
  }

  test("foreach") {
    compileStatement(StopBeforeCloseBrace, false, "foreach i in myList { }") shouldHave {
      case EachPE(_,
      PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,"i"))),None,None,None),
      _,
      LookupPE(LookupNameP(NameP(_, "myList")),None),
      BlockPE(_,_)) =>
    }
  }

  test("foreach with borrow") {
    compileStatement(StopBeforeCloseBrace, false, "foreach i in &myList { }") shouldHave {
      case EachPE(_,
      PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,"i"))),None,None,None),
      _,
      AugmentPE(_, BorrowP, LookupPE(LookupNameP(NameP(_, "myList")),None)),
      BlockPE(_,_)) =>
    }
  }

  test("foreach with two receivers") {
    compileStatement(StopBeforeCloseBrace, false, "foreach [a, b] in myList { }") shouldHave {
      case EachPE(_,
        PatternPP(_,
          None,None,None,
          Some(
            DestructureP(_,
              Vector(
                PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,"a"))),None,None,None),
                PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,"b"))),None,None,None)))),
          None),
        _,
        LookupPE(LookupNameP(NameP(_, "myList")),None),
        BlockPE(_,_)) =>
    }
  }

  test("foreach complex iterable") {
    compileStatement(StopBeforeCloseBrace, false, "foreach i in myList = 3; myList { }") shouldHave {
      case EachPE(_,
        PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,"i"))),None,None,None),
        _,
        ConsecutorPE(
          Vector(
            LetPE(_,PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,"myList"))),None,None,None),ConstantIntPE(_,3,_)),
            LookupPE(LookupNameP(NameP(_,"myList")),None))),
        BlockPE(_,VoidPE(_))) =>
    }
  }

  test("Multiple statements") {
    compileBlockContents(StopBeforeCloseBrace,
      """
        |42;
        |43;
        |""".stripMargin)
  }

  test("If and another statement") {
    compileBlockContents(StopBeforeCloseBrace,
      """
        |newCapacity = if (true) { 1 } else { 2 };
        |newArray = 3;
        |""".stripMargin)
  }

  test("Test block's trailing void presence") {
    compileBlockContents(StopBeforeCloseBrace,
      "moo()") shouldHave {
      case FunctionCallPE(_, _, LookupPE(LookupNameP(NameP(_, "moo")), None), Vector()) =>
    }

    compileBlockContents(StopBeforeCloseBrace,
      "moo();") shouldHave {
      case ConsecutorPE(Vector(FunctionCallPE(_, _, LookupPE(LookupNameP(NameP(_, "moo")), None), Vector()), VoidPE(_))) =>
    }
  }


  test("Block with statement and result") {
    compileBlockContents(StopBeforeCloseBrace,
      """
        |b;
        |a
      """.stripMargin) shouldHave {
      case Vector(LookupPE(LookupNameP(NameP(_, "b")), None), LookupPE(LookupNameP(NameP(_, "a")), None)) =>
    }
  }


  test("Block with result") {
    compileStatement(StopBeforeCloseBrace, false, "3") shouldHave {
      case ConstantIntPE(_, 3, _) =>
    }
  }

  test("Block with result that could be an expr") {
    // = doThings(a); could be misinterpreted as an expression doThings(=, a) if we're
    // not careful.
    compileBlockContents(StopBeforeCloseBrace,
      """
        |a = 2;
        |doThings(a)
      """.stripMargin) shouldHave {
      case Vector(
        LetPE(_, PatternPP(_, _,Some(LocalNameDeclarationP(NameP(_, "a"))), None, None, None), ConstantIntPE(_, 2, _)),
        FunctionCallPE(_, _, LookupPE(LookupNameP(NameP(_, "doThings")), None), Vector(LookupPE(LookupNameP(NameP(_, "a")), None)))) =>
    }
  }

  test("Mutating as statement") {
    val program =
      compileBlockContents(StopBeforeCloseBrace,
        "set x = 6;")
    program shouldHave {
      case MutatePE(_,LookupPE(LookupNameP(NameP(_, "x")), None),ConstantIntPE(_, 6, _)) =>
    }
  }

  test("Forgetting set when changing") {
    val error =
      compileForError(
        new ExpressionParser(GlobalOptions(true, true, true, true))
          .parseBlockContents(_, StopBeforeCloseBrace),
        """
          |ship.x = 4;
          |""".stripMargin)
    error match {
      case ForgotSetKeyword(_) =>
    }
  }

  test("foreach 2") {
    val programS =
      compileBlockContents(StopBeforeCloseBrace,
        """
          |foreach i in a {
          |  i
          |}
          |""".stripMargin)
    programS shouldHave {
      case EachPE(_,
        PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,"i"))),None,None,None),
        _,
        LookupPE(LookupNameP(NameP(_,"a")),None),
        BlockPE(_,
          LookupPE(LookupNameP(NameP(_,"i")),None))) =>
    }
  }

  test("foreach expr") {
    val programS =
      compileBlockContents(StopBeforeCloseBrace,
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
