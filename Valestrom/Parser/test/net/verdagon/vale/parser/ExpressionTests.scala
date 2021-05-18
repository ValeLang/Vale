package net.verdagon.vale.parser

import net.verdagon.vale.vassert
import org.scalatest.{FunSuite, Matchers}

class ExpressionTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("PE") {
    compile(CombinatorParsers.expression, "4") shouldHave { case IntLiteralPE(_, 4) => }
  }

  test("2") {
    compile(CombinatorParsers.expression,"4 + 5") shouldHave { case FunctionCallPE(_,None,_,false,LookupPE(NameP(_, "+"), None), List(IntLiteralPE(_, 4), IntLiteralPE(_, 5)),LendConstraintP(None)) => }
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
          FunctionCallPE(_, _, _, _, LookupPE(NameP(_, "ns"), _), List(IntLiteralPE(_, 4)), _),
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
        FunctionCallPE(_, _, _, _, LookupPE(NameP(_, "ns"), _), List(IntLiteralPE(_, 4)), _),
        StrLiteralPE(_, "rg"))) =>
    }
  }

  test("add as call") {
    compile(CombinatorParsers.expression,"+(4, 5)") shouldHave { case FunctionCallPE(_,None,_,false,LookupPE(NameP(_, "+"), None), List(IntLiteralPE(_, 4), IntLiteralPE(_, 5)),LendConstraintP(None)) => }
  }

  test("regular call") {
    compile(CombinatorParsers.expression,"x(y)") shouldHave { case FunctionCallPE(_,None,_,false,LookupPE(NameP(_, "x"), None), List(LookupPE(NameP(_, "y"), None)),LendConstraintP(None)) => }
  }

  test("6") {
    compile(CombinatorParsers.expression,"not y") shouldHave { case FunctionCallPE(_,None,_,false,LookupPE(NameP(_, "not"), None), List(LookupPE(NameP(_, "y"), None)),LendConstraintP(None)) => }
  }

  test("Lending result of function call") {
    compile(CombinatorParsers.expression,"&Muta()") shouldHave { case LendPE(_,FunctionCallPE(_,None,_,false,LookupPE(NameP(_, "Muta"), None), List(),LendConstraintP(None)), LendConstraintP(Some(ReadonlyP))) => }
  }

  test("inline call") {
    compile(CombinatorParsers.expression,"inl Muta()") shouldHave { case FunctionCallPE(_,Some(UnitP(_)),_,false,LookupPE(NameP(_,"Muta"),None),List(),LendConstraintP(None)) => }
  }

  test("Method call") {
    compile(CombinatorParsers.expression,"x . shout ()") shouldHave {
      case MethodCallPE(
      _,
      _,
      LookupPE(NameP(_,"x"),None),
      _,LendConstraintP(Some(ReadonlyP)),
      false,
      LookupPE(NameP(_,"shout"),None),List()) =>
    }
  }

  test("Method on member") {
    compile(CombinatorParsers.expression,"x.moo.shout()") shouldHave {
      case MethodCallPE(_,
        _,
        DotPE(_,
          LookupPE(NameP(_,"x"),None),
          _,
          false,
          NameP(_,"moo")),
        _,
      LendConstraintP(Some(ReadonlyP)),
        false,
        LookupPE(NameP(_,"shout"),None),List()) =>
    }
  }

  test("Moving method call") {
    compile(CombinatorParsers.expression,"x ^.shout()") shouldHave {
      case MethodCallPE(_,
      _,
        LookupPE(NameP(_,"x"),None),
        _,MoveP,false,
        LookupPE(NameP(_,"shout"),None),
        List()) =>
    }
  }

  test("Map method call") {
    compile(CombinatorParsers.expression,"x*. shout()") shouldHave {
      case MethodCallPE(_,
      _,
      LookupPE(NameP(_,"x"),None),
      _,LendConstraintP(Some(ReadonlyP)),true,
      LookupPE(NameP(_,"shout"),None),
      List()) =>
    }
  }

  test("Templated function call") {
    compile(CombinatorParsers.expression,"toArray<imm>( &result)") shouldHave {
      case FunctionCallPE(_,None,_,false,
      LookupPE(NameP(_, "toArray"),Some(TemplateArgsP(_, List(MutabilityPT(_,ImmutableP))))),
        List(LendPE(_,LookupPE(NameP(_, "result"),None),LendConstraintP(Some(ReadonlyP)))),
      LendConstraintP(None)) =>
    }
  }

  test("Templated method call") {
    compile(CombinatorParsers.expression,"result.toArray <imm> ()") shouldHave {
      case MethodCallPE(_,_,LookupPE(NameP(_,"result"),None),_,LendConstraintP(Some(ReadonlyP)),false,LookupPE(NameP(_,"toArray"),Some(TemplateArgsP(_, List(MutabilityPT(_,ImmutableP))))),List()) =>
    }
  }

  test("Custom binaries") {
    compile(CombinatorParsers.expression,"not y florgle not x") shouldHave {
      case FunctionCallPE(_,None,_,false,
      LookupPE(NameP(_, "florgle"), None),
          List(
            FunctionCallPE(_,None,_,false,
            LookupPE(NameP(_, "not"), None),
              List(LookupPE(NameP(_, "y"), None)),
            LendConstraintP(None)),
            FunctionCallPE(_,None,_,false,
            LookupPE(NameP(_, "not"), None),
              List(LookupPE(NameP(_, "x"), None)),
            LendConstraintP(None))),
      LendConstraintP(None)) =>
    }
  }

  test("Custom with noncustom binaries") {
    compile(CombinatorParsers.expression,"a + b florgle x * y") shouldHave {
      case FunctionCallPE(_,None,_,false,
        LookupPE(NameP(_, "florgle"), None),
          List(
            FunctionCallPE(_,None,_,false,
            LookupPE(NameP(_, "+"), None),
              List(LookupPE(NameP(_, "a"), None), LookupPE(NameP(_, "b"), None)),
              LendConstraintP(None)),
            FunctionCallPE(_,None,_,false,
            LookupPE(NameP(_, "*"), None),
              List(LookupPE(NameP(_, "x"), None), LookupPE(NameP(_, "y"), None)),
              LendConstraintP(None))),
          LendConstraintP(None)) =>
    }
  }

  test("Template calling") {
    compile(CombinatorParsers.expression,"MyNone< int >()") shouldHave {
      case FunctionCallPE(_,None,_,false,LookupPE(NameP(_, "MyNone"), Some(TemplateArgsP(_, List(NameOrRunePT(NameP(_, "int")))))),List(), LendConstraintP(None)) =>
    }
    compile(CombinatorParsers.expression,"MySome< MyNone <int> >()") shouldHave {
      case FunctionCallPE(_,None,_,false,LookupPE(NameP(_, "MySome"), Some(TemplateArgsP(_, List(CallPT(_,NameOrRunePT(NameP(_, "MyNone")),List(NameOrRunePT(NameP(_, "int")))))))),List(), LendConstraintP(None)) =>
    }
  }

  test(">=") {
    // It turns out, this was only parsing "9 >=" because it was looking for > specifically (in fact, it was looking
    // for + - * / < >) so it parsed as >(9, =) which was bad. We changed the infix operator parser to expect the
    // whitespace on both sides, so that it was forced to parse the entire thing.
    compile(CombinatorParsers.expression,"9 >= 3") shouldHave {
      case FunctionCallPE(_,None,_,false,LookupPE(NameP(_, ">="),None),List(IntLiteralPE(_, 9), IntLiteralPE(_, 3)),LendConstraintP(None)) =>
    }
  }

  test("Indexing") {
    compile(CombinatorParsers.expression,"arr [4]") shouldHave {
      case IndexPE(_,LookupPE(NameP(_,arr),None),List(IntLiteralPE(_,4))) =>
    }
  }

  test("Identity lambda") {
    compile(CombinatorParsers.expression, "{_}") shouldHave {
      case LambdaPE(_,FunctionP(_,FunctionHeaderP(_, None,List(),None,None,None,FunctionReturnP(_, _, _)),Some(BlockPE(_,List(MagicParamLookupPE(_)))))) =>
    }
  }

  test("20") {
    compile(CombinatorParsers.expression,
      "weapon.owner.map()") shouldHave {
      case MethodCallPE(_,
      _,
      DotPE(_,
      LookupPE(NameP(_,"weapon"),None),
      _, false,
      NameP(_,"owner")),
      _, LendConstraintP(Some(ReadonlyP)),
      false,
      LookupPE(NameP(_,"map"),None),
      List()) =>
    }
  }

  test("!=") {
    compile(CombinatorParsers.expression,"3 != 4") shouldHave {
      case FunctionCallPE(_, None, _, false, LookupPE(NameP(_, "!="), None), List(IntLiteralPE(_, 3), IntLiteralPE(_, 4)), LendConstraintP(None)) =>
    }
  }

  test("lambda without surrounding parens") {
    compile(CombinatorParsers.expression, "{ 0 }()") shouldHave {
      case FunctionCallPE(_,None,_,false,LambdaPE(None,_),List(),_) =>
    }
  }

  test("Test templated lambda param") {
    val program = compile(CombinatorParsers.expression, "((a){a + a})(3)")
    program shouldHave { case FunctionCallPE(_, None, _, false, LambdaPE(_, _), List(IntLiteralPE(_, 3)),LendConstraintP(None)) => }
    program shouldHave {
      case PatternPP(_,_, Some(CaptureP(_,LocalNameP(NameP(_, "a")),FinalP)),None,None,None) =>
    }
    program shouldHave {
      case FunctionCallPE(_, None, _, false, LookupPE(NameP(_, "+"), None),List(LookupPE(NameP(_, "a"), None), LookupPE(NameP(_, "a"), None)),LendConstraintP(None)) =>
    }
  }


  test("Function call") {
    val program = compile(CombinatorParsers.expression, "call(sum)")
    //    val main = program.lookupFunction("main")

    program shouldHave {
      case FunctionCallPE(_, None, _, false, LookupPE(NameP(_, "call"), None),List(LookupPE(NameP(_, "sum"), None)),LendConstraintP(None)) =>
    }
  }

  test("Report leaving out semicolon or ending body after expression") {
    compileProgramForError(
      """
        |fn doCivicDance(virtual this Car) {
        |  a = 3;
        |  set x = 7 )
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
          LookupPE(NameP(_,"*"),None),
          List(
            IntLiteralPE(_,2),
            FunctionCallPE(_,None,_,false,
              LookupPE(NameP(_,"-"),None),
              List(IntLiteralPE(_,5), IntLiteralPE(_,7)),
              LendConstraintP(None))),
          LendConstraintP(None)) =>
    }
  }

  test("static array " +
    "from values") {
    compile(CombinatorParsers.expression,
      "[][3, 5, 6]") shouldHave {
//      case StaticArrayFromValuesPE(_,List(IntLiteralPE(_,3), IntLiteralPE(_,5), IntLiteralPE(_,6))) =>
//      case null =>
      case ConstructArrayPE(_,None,None,StaticSizedP(None),true,List(_, _, _)) =>
    }
  }

  test("static array from callable with rune") {
    compile(CombinatorParsers.expression,
      "[N]({_ * 2})") shouldHave {
//      case StaticArrayFromCallablePE(_,NameOrRunePT(NameP(_, "N")),_,_) =>
//      case null =>
      case ConstructArrayPE(_,
        None,
        None,
        StaticSizedP(Some(NameOrRunePT(NameP(_,"N")))),
        false,
        List(LambdaPE(None,_))) =>
    }
  }

  test("static array from callable") {
    compile(CombinatorParsers.expression,
      "[3](triple)") shouldHave {
      case ConstructArrayPE(_,
        None,
        None,
        StaticSizedP(Some(IntPT(_,3))),
        false,
        List(_)) =>
    }
  }

  test("mutable static array from callable") {
    compile(CombinatorParsers.expression,
      "[mut 3](triple)") shouldHave {
      case ConstructArrayPE(_,
        Some(MutabilityPT(_,MutableP)),
        None,
        StaticSizedP(Some(IntPT(_,3))),
        false,
        List(_)) =>
    }
  }

  test("mutable static array from callable, no size") {
    compile(CombinatorParsers.expression,
      "[mut][3, 4, 5]") shouldHave {
      case ConstructArrayPE(_,
        Some(MutabilityPT(_,MutableP)),
        None,
        StaticSizedP(None),
        true,
        List(_, _, _)) =>
    }
  }

  test("runtime array from callable with rune") {
    compile(CombinatorParsers.expression,
      "[*](6, {_ * 2})") shouldHave {
      //      case StaticArrayFromCallablePE(_,NameOrRunePT(NameP(_, "N")),_,_) =>
      //      case null =>
      case ConstructArrayPE(_,
        None,
        None,
        RuntimeSizedP,
        false,
        List(_, _)) =>
    }
  }

  test("runtime array from callable") {
    compile(CombinatorParsers.expression,
      "[*](6, triple)") shouldHave {
      case ConstructArrayPE(_,
        None,
        None,
        RuntimeSizedP,
        false,
        List(_, _)) =>
    }
  }

  test("mutable runtime array from callable") {
    compile(CombinatorParsers.expression,
      "[mut *](6, triple)") shouldHave {
      case ConstructArrayPE(_,
        Some(MutabilityPT(_,MutableP)),
        None,
        RuntimeSizedP,
        false,
        List(_, _)) =>
    }
  }


  test("Call callable expr") {
    compile(CombinatorParsers.expression,
      "(something.callable)(3)") shouldHave {
      case FunctionCallPE(
          _,None,_,false,
          DotPE(_,LookupPE(NameP(_,"something"),None),_,false,NameP(_,"callable")),
          List(_),LendConstraintP(None)) =>
    }
  }

  test("Array indexing") {
    compile(CombinatorParsers.expression,
      "board[i]") shouldHave {
      case IndexPE(_,LookupPE(NameP(_,"board"),None),List(LookupPE(NameP(_,"i"),None))) =>
    }
    compile(CombinatorParsers.expression,
      "this.board[i]") shouldHave {
      case IndexPE(_,DotPE(_,LookupPE(NameP(_,"this"),None),_,false,NameP(_,"board")),List(LookupPE(NameP(_,"i"),None))) =>
    }
  }

  test("mod and == precedence") {
    compile(CombinatorParsers.expression,
      """8 mod 2 == 0""") shouldHave {
      case FunctionCallPE(_,
        None,_,false,
        LookupPE(NameP(_,"=="),None),
        List(
          FunctionCallPE(_,
            None,_,false,
            LookupPE(NameP(_,"mod"),None),
            List(
              IntLiteralPE(_,8),
              IntLiteralPE(_,2)),
            LendConstraintP(None)),
          IntLiteralPE(_,0)),
        LendConstraintP(None)) =>
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
              LookupPE(NameP(_,"=="),None),
              List(
                IntLiteralPE(_,2),
                IntLiteralPE(_,0)),
              LendConstraintP(None)))),
        BlockPE(_, List(BoolLiteralPE(_,false)))) =>
    }
  }

//  // See https://github.com/ValeLang/Vale/issues/108
//  test("Calling with space") {
//    compile(CombinatorParsers.expression,
//      """len (cached_dims)""") shouldHave {
//      case FunctionCallPE(_,_,_,_,LookupPE(StringP(_,"len"),None),List(LookupPE(StringP(_,"cached_dims"),None)),_) =>
//    }
//  }
}
