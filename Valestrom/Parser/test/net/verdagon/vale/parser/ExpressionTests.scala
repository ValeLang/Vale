package net.verdagon.vale.parser

import net.verdagon.vale.vassert
import org.scalatest.{FunSuite, Matchers}

class ExpressionTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("PE") {
    compile(CombinatorParsers.expression, "4") shouldHave { case IntLiteralPE(_, 4) => }
  }

  test("2") {
    compile(CombinatorParsers.expression,"4 + 5") shouldHave { case FunctionCallPE(_,None,_,false,LookupPE(StringP(_, "+"), None, BorrowP), List(IntLiteralPE(_, 4), IntLiteralPE(_, 5))) => }
  }

  test("Floats") {
    compile(CombinatorParsers.expression,"4.2") shouldHave { case FloatLiteralPE(_, 4.2f) => }
  }

  test("Simple string") {
    compile(CombinatorParsers.stringExpr, """"moo"""") shouldHave { case StrLiteralPE(_, "moo") => }
  }

  test("String with quote inside") {
    compile(CombinatorParsers.expression, """"m\"oo"""") shouldHave { case StrLiteralPE(_, "m\"oo") => }
  }

  test("String with apostrophe inside") {
    compile(CombinatorParsers.expression, """"m'oo"""") shouldHave { case StrLiteralPE(_, "m'oo") => }
  }

  test("Short string interpolating") {
    compile(CombinatorParsers.expression, """"bl{4}rg"""") shouldHave { case StrInterpolatePE(_, List(StrLiteralPE(_, "bl"), IntLiteralPE(_, 4), StrLiteralPE(_, "rg"))) => }
  }

  test("Short string interpolating with call") {
    compile(CombinatorParsers.expression, """"bl{ns(4)}rg"""") shouldHave {
      case StrInterpolatePE(_,
        List(
          StrLiteralPE(_, "bl"),
          FunctionCallPE(_, _, _, _, LookupPE(StringP(_, "ns"), _, BorrowP), List(IntLiteralPE(_, 4))),
          StrLiteralPE(_, "rg"))) =>
    }
  }

  test("Long string interpolating") {
    compile(CombinatorParsers.expression, "\"\"\"bl{4}rg\"\"\"") shouldHave { case StrInterpolatePE(_, List(StrLiteralPE(_, "bl"), IntLiteralPE(_, 4), StrLiteralPE(_, "rg"))) => }
  }

  test("Long string interpolating with call") {
    compile(CombinatorParsers.expression, "\"\"\"bl\"{ns(4)}rg\"\"\"") shouldHave {
      case StrInterpolatePE(_,
      List(
        StrLiteralPE(_, "bl\""),
        FunctionCallPE(_, _, _, _, LookupPE(StringP(_, "ns"), _, BorrowP), List(IntLiteralPE(_, 4))),
        StrLiteralPE(_, "rg"))) =>
    }
  }

  test("4") {
    compile(CombinatorParsers.expression,"+(4, 5)") shouldHave { case FunctionCallPE(_,None,_,false,LookupPE(StringP(_, "+"), None, BorrowP), List(IntLiteralPE(_, 4), IntLiteralPE(_, 5))) => }
  }

  test("5") {
    compile(CombinatorParsers.expression,"x(y)") shouldHave { case FunctionCallPE(_,None,_,false,LookupPE(StringP(_, "x"), None, BorrowP), List(LookupPE(StringP(_, "y"), None, BorrowP))) => }
  }

  test("6") {
    compile(CombinatorParsers.expression,"not y") shouldHave { case FunctionCallPE(_,None,_,false,LookupPE(StringP(_, "not"), None, BorrowP), List(LookupPE(StringP(_, "y"), None, BorrowP))) => }
  }

  test("Lending result of function call") {
    compile(CombinatorParsers.expression,"&Muta()") shouldHave { case OwnershippedPE(_,FunctionCallPE(_,None,_,false,LookupPE(StringP(_, "Muta"), None, BorrowP), List()), BorrowP) => }
  }

  test("inline call") {
    compile(CombinatorParsers.expression,"inl Muta()") shouldHave { case FunctionCallPE(_,Some(UnitP(_)),_,false,LookupPE(StringP(_,"Muta"),None, BorrowP),List()) => }
  }

  test("Member") {
    compile(CombinatorParsers.expression,"x.moo") shouldHave {
      case DotPE(_,
        LookupPE(StringP(_,"x"), None,BorrowP),
        _,
        false,
        BorrowP,
        StringP(_,"moo")) =>
    }
  }

  test("Method call") {
    compile(CombinatorParsers.expression,"x . shout()") shouldHave {
      case MethodCallPE(
      _,
      LookupPE(StringP(_,"x"), None,BorrowP),
      _,
      false,
      LookupPE(StringP(_,"shout"),None, BorrowP),List()) =>
    }
  }

  test("Method on member") {
    compile(CombinatorParsers.expression,"x.moo.shout()") shouldHave {
      case MethodCallPE(_,
        DotPE(_,
          LookupPE(StringP(_,"x"), None,BorrowP),
          _,
          false,
          BorrowP,
          StringP(_,"moo")),
        _,
        false,
        LookupPE(StringP(_,"shout"),None, BorrowP),List()) =>
    }
  }

  test("Moving method call") {
    compile(CombinatorParsers.expression,"x^.shout()") shouldHave {
      case MethodCallPE(_,
        LookupPE(StringP(_,"x"), None,OwnP),
        _,false,
        LookupPE(StringP(_,"shout"),None, BorrowP),
        List()) =>
    }
  }

  test("Lending method call") {
    compile(CombinatorParsers.expression,"x&.shout()") shouldHave {
      case MethodCallPE(_,
        LookupPE(StringP(_,"x"), None,BorrowP),
        _,false,
        LookupPE(StringP(_,"shout"),None, BorrowP),
        List()) =>
    }
  }

  test("Weak lending method call") {
    compile(CombinatorParsers.expression,"x&&.shout()") shouldHave {
      case MethodCallPE(_,
      LookupPE(StringP(_,"x"), None,WeakP),
      _,false,
      LookupPE(StringP(_,"shout"),None, BorrowP),
      List()) =>
    }
  }

  test("Map method call") {
    compile(CombinatorParsers.expression,"x..shout()") shouldHave {
      case MethodCallPE(_,
      LookupPE(StringP(_,"x"), None,BorrowP),
      _,true,
      LookupPE(StringP(_,"shout"),None, BorrowP),
      List()) =>
    }
  }

  test("Templated function call") {
    compile(CombinatorParsers.expression,"toArray<imm>(result)") shouldHave {
      case FunctionCallPE(_,None,_,false,
        LookupPE(
          StringP(_, "toArray"),
          Some(TemplateArgsP(_, List(MutabilityPT(_,ImmutableP)))),
          BorrowP),
        List(LookupPE(StringP(_, "result"),None,BorrowP))) =>
    }
  }

  test("Templated method call") {
    compile(CombinatorParsers.expression,"result.toArray <imm> ()") shouldHave {
      case MethodCallPE(_,LookupPE(StringP(_,"result"),None, BorrowP),_,false,LookupPE(StringP(_,"toArray"),Some(TemplateArgsP(_, List(MutabilityPT(_,ImmutableP)))), BorrowP),List()) =>
    }
  }

  test("Custom binaries") {
    compile(CombinatorParsers.expression,"not y florgle not x") shouldHave {
      case FunctionCallPE(_,None,_,false,
        LookupPE(StringP(_, "florgle"), None, BorrowP),
        List(
          FunctionCallPE(_,None,_,false,
          LookupPE(
            StringP(_, "not"), None, BorrowP),
            List(LookupPE(StringP(_, "y"), None, BorrowP))),
          FunctionCallPE(_,None,_,false,
          LookupPE(
            StringP(_, "not"), None, BorrowP),
            List(LookupPE(StringP(_, "x"), None, BorrowP))))) =>
    }
  }

  test("Custom with noncustom binaries") {
    compile(CombinatorParsers.expression,"a + b florgle x * y") shouldHave {
      case FunctionCallPE(_,None,_,false,
        LookupPE(StringP(_, "florgle"), None, BorrowP),
          List(
            FunctionCallPE(_,None,_,false,
            LookupPE(StringP(_, "+"), None, BorrowP),
              List(LookupPE(StringP(_, "a"), None, BorrowP), LookupPE(StringP(_, "b"), None, BorrowP))),
            FunctionCallPE(_,None,_,false,
            LookupPE(StringP(_, "*"), None, BorrowP),
              List(LookupPE(StringP(_, "x"), None, BorrowP), LookupPE(StringP(_, "y"), None, BorrowP))))) =>
    }
  }

  test("Template calling") {
    compile(CombinatorParsers.expression,"MyNone< int >()") shouldHave {
      case FunctionCallPE(_,None,_,false,LookupPE(StringP(_, "MyNone"), Some(TemplateArgsP(_, List(NameOrRunePT(StringP(_, "int"))))), BorrowP),List()) =>
    }
    compile(CombinatorParsers.expression,"MySome< MyNone <int> >()") shouldHave {
      case FunctionCallPE(_,None,_,false,LookupPE(StringP(_, "MySome"), Some(TemplateArgsP(_, List(CallPT(_,NameOrRunePT(StringP(_, "MyNone")),List(NameOrRunePT(StringP(_, "int"))))))), BorrowP),List()) =>
    }
  }

  test(">=") {
    // It turns out, this was only parsing "9 >=" because it was looking for > specifically (in fact, it was looking
    // for + - * / < >) so it parsed as >(9, =) which was bad. We changed the infix operator parser to expect the
    // whitespace on both sides, so that it was forced to parse the entire thing.
    compile(CombinatorParsers.expression,"9 >= 3") shouldHave {
      case FunctionCallPE(_,None,_,false,LookupPE(StringP(_, ">="),None, BorrowP),List(IntLiteralPE(_, 9), IntLiteralPE(_, 3))) =>
    }
  }

  test("Indexing") {
    compile(CombinatorParsers.expression,"arr [4]") shouldHave {
      case IndexPE(_,LookupPE(StringP(_,"arr"),None, BorrowP),List(IntLiteralPE(_,4))) =>
    }
  }

  test("Identity lambda") {
    compile(CombinatorParsers.expression, "{_}") shouldHave {
      case LambdaPE(_,FunctionP(_,FunctionHeaderP(_, None,List(),None,None,None,FunctionReturnP(_, _, _)),Some(BlockPE(_,List(MagicParamLookupPE(_, OwnP)))))) =>
    }
  }

  test("20") {
    compile(CombinatorParsers.expression,
      "weapon.owner.map()") shouldHave {
      case MethodCallPE(_,
        DotPE(_,
          LookupPE(StringP(_,"weapon"),None, BorrowP),
          _, false,
          BorrowP,
          StringP(_,"owner")),
        _,
        false,
        LookupPE(StringP(_,"map"),None, BorrowP),
      List()) =>
    }
  }

  test("!=") {
    compile(CombinatorParsers.expression,"3 != 4") shouldHave {
      case FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "!="), None, BorrowP), List(IntLiteralPE(_, 3), IntLiteralPE(_, 4))) =>
    }
  }

  test("Test templated lambda param") {
    val program = compile(CombinatorParsers.expression, "(a){a + a}(3)")
    program shouldHave { case FunctionCallPE(_, None, _, false, LambdaPE(_, _), List(IntLiteralPE(_, 3))) => }
    program shouldHave {
      case PatternPP(_,_, Some(CaptureP(_,LocalNameP(StringP(_, "a")),FinalP)),None,None,None) =>
    }
    program shouldHave {
      case FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "+"), None, BorrowP),List(LookupPE(StringP(_, "a"), None, BorrowP), LookupPE(StringP(_, "a"), None, BorrowP))) =>
    }
  }


  test("Function call") {
    val program = compile(CombinatorParsers.expression, "call(sum)")
    //    val main = program.lookupFunction("main")

    program shouldHave {
      case FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "call"), None, BorrowP),List(LookupPE(StringP(_, "sum"), None, BorrowP))) =>
    }
  }

  test("Report leaving out semicolon or ending body after expression") {
    compileProgramForError(
      """
        |fn doCivicDance(virtual this Car) {
        |  a = 3;
        |  mut x = 7 )
        |}
        """.stripMargin) match {
      case BadExpressionEnd(_) =>
    }
    compileProgramForError(
      """
        |fn doCivicDance(virtual this Car) {
        |  floop() ]
        |}
        """.stripMargin) match {
      case BadExpressionEnd(_) =>
    }
  }

  test("parens") {
    compile(CombinatorParsers.expression,
      "2 * (5 - 7)") shouldHave {
        case FunctionCallPE(_,None,_,false,
          LookupPE(StringP(_,"*"),None, BorrowP),
          List(
            IntLiteralPE(_,2),
            FunctionCallPE(_,None,_,false,
              LookupPE(StringP(_,"-"),None, BorrowP),
              List(IntLiteralPE(_,5), IntLiteralPE(_,7))))) =>
    }
  }

  test("Array indexing") {
    compile(CombinatorParsers.expression,
      "board[i]") shouldHave {
      case IndexPE(_,LookupPE(StringP(_,"board"),None, BorrowP),List(LookupPE(StringP(_,"i"),None, BorrowP))) =>
    }
    compile(CombinatorParsers.expression,
      "this.board[i]") shouldHave {
      case IndexPE(_,DotPE(_,LookupPE(StringP(_,"this"),None, BorrowP),_,false,BorrowP,StringP(_,"board")),List(LookupPE(StringP(_,"i"),None, BorrowP))) =>
    }
  }

  test("mod and == precedence") {
    compile(CombinatorParsers.expression,
      """8 mod 2 == 0""") shouldHave {
      case FunctionCallPE(_,
        None,_,false,
        LookupPE(StringP(_,"=="),None, BorrowP),
        List(
          FunctionCallPE(_,
            None,_,false,
            LookupPE(StringP(_,"mod"),None, BorrowP),
            List(
              IntLiteralPE(_,8),
              IntLiteralPE(_,2))),
          IntLiteralPE(_,0))) =>
    }
  }

  test("or and == precedence") {
    compile(CombinatorParsers.expression,
      """2 == 0 or false""") shouldHave {
      case OrPE(_,
        BlockPE(_,
          List(
            FunctionCallPE(_,
              None,_,false,
              LookupPE(StringP(_,"=="),None, BorrowP),
              List(
                IntLiteralPE(_,2),
                IntLiteralPE(_,0))))),
        BlockPE(_, List(BoolLiteralPE(_,false)))) =>
    }
  }
}
