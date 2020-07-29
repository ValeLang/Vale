package net.verdagon.vale.parser

import net.verdagon.vale.vassert
import org.scalatest.{FunSuite, Matchers}

class ExpressionTests extends FunSuite with Matchers with Collector {
  private def compile[T](parser: CombinatorParsers.Parser[IExpressionPE], code: String): IExpressionPE = {
    CombinatorParsers.parse(parser, code.toCharArray()) match {
      case CombinatorParsers.NoSuccess(msg, input) => {
        fail();
      }
      case CombinatorParsers.Success(expr, rest) => {
        vassert(
          rest.atEnd,
          "Parsed \"" + code.slice(0, rest.offset) + "\" as \"" + expr + "\" but stopped at \"" + code.slice(rest.offset, code.length) + "\"")
        expr
      }
    }
  }
  private def compile(code: String): IExpressionPE = {
    compile(CombinatorParsers.expression, code)
  }

  test("PE") {
    compile("4") shouldHave { case IntLiteralPE(_, 4) => }
  }

  test("2") {
    compile("4 + 5") shouldHave { case FunctionCallPE(_,None,_,false,LookupPE(StringP(_, "+"), None), List(IntLiteralPE(_, 4), IntLiteralPE(_, 5)),BorrowP) => }
  }

  test("Floats") {
    compile("4.2") shouldHave { case FloatLiteralPE(_, 4.2f) => }
  }

  test("4") {
    compile("+(4, 5)") shouldHave { case FunctionCallPE(_,None,_,false,LookupPE(StringP(_, "+"), None), List(IntLiteralPE(_, 4), IntLiteralPE(_, 5)),BorrowP) => }
  }

  test("5") {
    compile("x(y)") shouldHave { case FunctionCallPE(_,None,_,false,LookupPE(StringP(_, "x"), None), List(LookupPE(StringP(_, "y"), None)),BorrowP) => }
  }

  test("6") {
    compile("not y") shouldHave { case FunctionCallPE(_,None,_,false,LookupPE(StringP(_, "not"), None), List(LookupPE(StringP(_, "y"), None)),BorrowP) => }
  }

  test("Lending result of function call") {
    compile("&Muta()") shouldHave { case LendPE(_,FunctionCallPE(_,None,_,false,LookupPE(StringP(_, "Muta"), None), List(),BorrowP), BorrowP) => }
  }

  test("inline call") {
    compile("inl Muta()") shouldHave { case FunctionCallPE(_,Some(UnitP(_)),_,false,LookupPE(StringP(_,"Muta"),None),List(),BorrowP) => }
  }

  test("Method call") {
    compile("x . shout ()") shouldHave {
      case MethodCallPE(
      _,
      LookupPE(StringP(_,"x"),None),
      _,BorrowP,
      false,
      LookupPE(StringP(_,"shout"),None),List()) =>
    }
  }

  test("Method on member") {
    compile("x.moo.shout()") shouldHave {
      case MethodCallPE(Range(Pos(1,1),Pos(1,14)),
        DotPE(Range(Pos(1,1),Pos(1,6)),
          LookupPE(StringP(Range(Pos(1,1),Pos(1,2)),x),None),
          Range(Pos(1,2),Pos(1,3)),
          false,
          LookupPE(StringP(Range(Pos(1,3),Pos(1,6)),"moo"),None)),
        Range(Pos(1,6),Pos(1,7)),
        BorrowP,
        false,
        LookupPE(StringP(Range(Pos(1,7),Pos(1,12)),"shout"),None),List()) =>
    }
  }

  test("Moving method call") {
    compile("x ^.shout()") shouldHave {
      case MethodCallPE(_,
        LookupPE(StringP(_,"x"),None),
        _,OwnP,false,
        LookupPE(StringP(_,"shout"),None),
        List()) =>
    }
  }

  test("Map method call") {
    compile("x*. shout()") shouldHave {
      case MethodCallPE(_,
      LookupPE(StringP(_,"x"),None),
      _,BorrowP,true,
      LookupPE(StringP(_,"shout"),None),
      List()) =>
    }
  }

  test("Templated function call") {
    compile("toArray<imm>( &result)") shouldHave {
      case FunctionCallPE(_,None,_,false,
      LookupPE(StringP(_, "toArray"),Some(TemplateArgsP(_, List(MutabilityPT(ImmutableP))))),
        List(LendPE(_,LookupPE(StringP(_, "result"),None),BorrowP)),
        BorrowP) =>
    }
  }

  test("Templated method call") {
    compile("result.toArray <imm> ()") shouldHave {
      case MethodCallPE(_,LookupPE(StringP(_,"result"),None),_,BorrowP,false,LookupPE(StringP(_,"toArray"),Some(TemplateArgsP(_, List(MutabilityPT(ImmutableP))))),List()) =>
    }
  }

  test("Custom binaries") {
    compile("not y florgle not x") shouldHave {
      case FunctionCallPE(_,None,_,false,
      LookupPE(StringP(_, "florgle"), None),
          List(
            FunctionCallPE(_,None,_,false,
            LookupPE(StringP(_, "not"), None),
              List(LookupPE(StringP(_, "y"), None)),
              BorrowP),
            FunctionCallPE(_,None,_,false,
            LookupPE(StringP(_, "not"), None),
              List(LookupPE(StringP(_, "x"), None)),
              BorrowP)),
          BorrowP) =>
    }
  }

  test("Custom with noncustom binaries") {
    compile("a + b florgle x * y") shouldHave {
      case FunctionCallPE(_,None,_,false,
        LookupPE(StringP(_, "florgle"), None),
          List(
            FunctionCallPE(_,None,_,false,
            LookupPE(StringP(_, "+"), None),
              List(LookupPE(StringP(_, "a"), None), LookupPE(StringP(_, "b"), None)),
              BorrowP),
            FunctionCallPE(_,None,_,false,
            LookupPE(StringP(_, "*"), None),
              List(LookupPE(StringP(_, "x"), None), LookupPE(StringP(_, "y"), None)),
              BorrowP)),
          BorrowP) =>
    }
  }

  test("Template calling") {
    compile("MyNone< int >()") shouldHave {
      case FunctionCallPE(_,None,_,false,LookupPE(StringP(_, "MyNone"), Some(TemplateArgsP(_, List(NameOrRunePT(StringP(_, "int")))))),List(), BorrowP) =>
    }
    compile("MySome< MyNone <int> >()") shouldHave {
      case FunctionCallPE(_,None,_,false,LookupPE(StringP(_, "MySome"), Some(TemplateArgsP(_, List(CallPT(_,NameOrRunePT(StringP(_, "MyNone")),List(NameOrRunePT(StringP(_, "int")))))))),List(), BorrowP) =>
    }
  }

  test(">=") {
    // It turns out, this was only parsing "9 >=" because it was looking for > specifically (in fact, it was looking
    // for + - * / < >) so it parsed as >(9, =) which was bad. We changed the infix operator parser to expect the
    // whitespace on both sides, so that it was forced to parse the entire thing.
    compile(CombinatorParsers.expression,"9 >= 3") shouldHave {
      case FunctionCallPE(_,None,_,false,LookupPE(StringP(_, ">="),None),List(IntLiteralPE(_, 9), IntLiteralPE(_, 3)),BorrowP) =>
    }
  }

  test("Indexing") {
    compile(CombinatorParsers.expression,"arr [4]") shouldHave {
      case DotCallPE(_,LookupPE(StringP(_,arr),None),List(IntLiteralPE(_,4))) =>
    }
  }

  test("Identity lambda") {
    compile(CombinatorParsers.expression, "{_}") shouldHave {
      case LambdaPE(_,FunctionP(_,FunctionHeaderP(_, None,List(),None,None,None,None),Some(BlockPE(_,List(MagicParamLookupPE(_)))))) =>
    }
  }


  // debt: fix
//  test("Array index") {
//    compile("board.(i)") shouldEqual DotCallPE(LookupPE(StringP(_, "board"), None),PackPE(List(LookupPE(StringP(_, "i"), None))),true)
//    compile("this.board.(i)") shouldEqual
//      DotCallPE(DotPE(_,LookupPE(StringP(_, "this"), None), "board", true),PackPE(List(LookupPE(StringP(_, "i"), None))),true)
//  }
}
