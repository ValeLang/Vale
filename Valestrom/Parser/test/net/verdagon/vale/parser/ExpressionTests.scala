package net.verdagon.vale.parser

import net.verdagon.vale.{Collector, vassert, vfail}
import org.scalatest.{FunSuite, Matchers}

class ExpressionTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("PE") {
    compile(CombinatorParsers.expression, "4") shouldHave { case ConstantIntPE(_, 4, 32) => }
  }

  test("i64") {
    compile(CombinatorParsers.expression, "4i64") shouldHave { case ConstantIntPE(_, 4, 64) => }
  }

  test("2") {
    compile(CombinatorParsers.expression,"4 + 5") shouldHave { case FunctionCallPE(_,None,_,false,LookupPE(NameP(_, "+"), None), Vector(ConstantIntPE(_, 4, _), ConstantIntPE(_, 5, _)),LendConstraintP(Some(ReadonlyP))) => }
  }

  test("Floats") {
    compile(CombinatorParsers.expression,"4.2") shouldHave { case ConstantFloatPE(_, 4.2f) => }
  }

  test("Simple string") {
    compile(CombinatorParsers.stringExpr, """"moo"""") shouldHave { case ConstantStrPE(_, "moo") => }
  }

  test("String with quote inside") {
    compile(CombinatorParsers.expression, """"m\"oo"""") shouldHave { case ConstantStrPE(_, "m\"oo") => }
  }

  test("String with unicode") {
    compile(CombinatorParsers.fourDigitHexNumber, "000a") shouldEqual 10
    compile(CombinatorParsers.shortStringPart, "\\u000a") match { case ConstantStrPE(_, "\n") => }
    compile(CombinatorParsers.shortStringPart, "\\u001b") match { case ConstantStrPE(_, "\u001b") => }
    compile(CombinatorParsers.stringExpr, "\"\\u001b\"") match { case ConstantStrPE(_, "\u001b") => }
    compile(CombinatorParsers.stringExpr, "\"foo\\u001bbar\"") match { case ConstantStrPE(_, "foo\u001bbar") => }
    compile(CombinatorParsers.expression, "\"foo\\u001bbar\"") match { case ConstantStrPE(_, "foo\u001bbar") => }
    // FALL NOT TO TEMPTATION
    // Scala has some issues here.
    // The above "\"\\u001b\"" seems like it could be expressed """"\\u001b"""" but it can't.
    // Nothing seems to work:
    // - vassert("\"\\u001b\"" == """"\u001b"""") fails
    // - vassert("\"\\u001b\"" == """"\\u001b"""") fails
    // - vassert("\"\\u001b\"" == """\"\\u001b\"""") fails
    // This took quite a while to figure out.
    // So, just stick with regular scala string literals, scala's good with those.
    // Other tests have this, search TEMPTATION.
    // NOW GO YE AND PROSPER
  }

  test("String with apostrophe inside") {
    compile(CombinatorParsers.expression, """"m'oo"""") shouldHave { case ConstantStrPE(_, "m'oo") => }
    compile(CombinatorParsers.expression, "\"\"\"m\'oo\"\"\"") shouldHave { case ConstantStrPE(_, "m'oo") => }
  }

  test("Short string interpolating") {
    compile(CombinatorParsers.expression, """"bl{4}rg"""") shouldHave { case StrInterpolatePE(_, Vector(ConstantStrPE(_, "bl"), ConstantIntPE(_, 4, _), ConstantStrPE(_, "rg"))) => }
  }

  test("Short string interpolating with call") {
    compile(CombinatorParsers.expression, """"bl{ns(4)}rg"""") shouldHave {
      case StrInterpolatePE(_,
        Vector(
          ConstantStrPE(_, "bl"),
          FunctionCallPE(_, _, _, _, LookupPE(NameP(_, "ns"), _), Vector(ConstantIntPE(_, 4, _)), _),
          ConstantStrPE(_, "rg"))) =>
    }
  }

  test("Long string interpolating") {
    compile(CombinatorParsers.expression, "\"\"\"bl{4}rg\"\"\"") shouldHave { case StrInterpolatePE(_, Vector(ConstantStrPE(_, "bl"), ConstantIntPE(_, 4, _), ConstantStrPE(_, "rg"))) => }
  }

  test("Long string interpolating with call") {
    compile(CombinatorParsers.expression, "\"\"\"bl\"{ns(4)}rg\"\"\"") shouldHave {
      case StrInterpolatePE(_,
      Vector(
        ConstantStrPE(_, "bl\""),
        FunctionCallPE(_, _, _, _, LookupPE(NameP(_, "ns"), _), Vector(ConstantIntPE(_, 4, _)), _),
        ConstantStrPE(_, "rg"))) =>
    }
  }

  test("add as call") {
    compile(CombinatorParsers.expression,"+(4, 5)") shouldHave { case FunctionCallPE(_,None,_,false,LookupPE(NameP(_, "+"), None), Vector(ConstantIntPE(_, 4, _), ConstantIntPE(_, 5, _)),LendConstraintP(Some(ReadonlyP))) => }
  }

  test("regular call") {
    compile(CombinatorParsers.expression,"x(y)") shouldHave { case FunctionCallPE(_,None,_,false,LookupPE(NameP(_, "x"), None), Vector(LookupPE(NameP(_, "y"), None)),LendConstraintP(Some(ReadonlyP))) => }
  }

  test("6") {
    compile(CombinatorParsers.expression,"not y") shouldHave { case FunctionCallPE(_,None,_,false,LookupPE(NameP(_, "not"), None), Vector(LookupPE(NameP(_, "y"), None)),LendConstraintP(Some(ReadonlyP))) => }
  }

  test("Lending result of function call") {
    compile(CombinatorParsers.expression,"&Muta()") shouldHave { case LendPE(_,FunctionCallPE(_,None,_,false,LookupPE(NameP(_, "Muta"), None), Vector(),LendConstraintP(Some(ReadonlyP))), LendConstraintP(Some(ReadonlyP))) => }
  }

  test("inline call") {
    compile(CombinatorParsers.expression,"inl Muta()") shouldHave { case FunctionCallPE(_,Some(UnitP(_)),_,false,LookupPE(NameP(_,"Muta"),None),Vector(),LendConstraintP(Some(ReadonlyP))) => }
  }

  test("Method call") {
    compile(CombinatorParsers.expression,"x . shout ()") shouldHave {
      case MethodCallPE(
      _,
      _,
      LookupPE(NameP(_,"x"),None),
      _,LendConstraintP(Some(ReadonlyP)),
      false,
      LookupPE(NameP(_,"shout"),None),Vector()) =>
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
        LookupPE(NameP(_,"shout"),None), Vector()) =>
    }
  }

  test("Moving method call") {
    compile(CombinatorParsers.expression,"(x ).shout()") shouldHave {
      case MethodCallPE(_,
      _,
        PackPE(_, Vector(LookupPE(NameP(_,"x"),None))),
        _,UseP,false,
        LookupPE(NameP(_,"shout"),None),
        Vector()) =>
    }
  }

  test("Map method call") {
    compile(CombinatorParsers.expression,"x*. shout()") shouldHave {
      case MethodCallPE(_,
      _,
      LookupPE(NameP(_,"x"),None),
      _,LendConstraintP(Some(ReadonlyP)),true,
      LookupPE(NameP(_,"shout"),None),
      Vector()) =>
    }
  }

  test("Templated function call") {
    compile(CombinatorParsers.expression,"toArray<imm>( &result)") shouldHave {
      case FunctionCallPE(_,None,_,false,
      LookupPE(NameP(_, "toArray"),Some(TemplateArgsP(_, Vector(MutabilityPT(_,ImmutableP))))),
        Vector(LendPE(_,LookupPE(NameP(_, "result"),None),LendConstraintP(Some(ReadonlyP)))),
      LendConstraintP(Some(ReadonlyP))) =>
    }
  }

  test("Templated method call") {
    compile(CombinatorParsers.expression,"result.toArray <imm> ()") shouldHave {
      case MethodCallPE(_,_,LookupPE(NameP(_,"result"),None),_,LendConstraintP(Some(ReadonlyP)),false,LookupPE(NameP(_,"toArray"),Some(TemplateArgsP(_, Vector(MutabilityPT(_,ImmutableP))))),Vector()) =>
    }
  }

  test("Custom binaries") {
    compile(CombinatorParsers.expression,"not y florgle not x") shouldHave {
      case FunctionCallPE(_,None,_,false,
      LookupPE(NameP(_, "florgle"), None),
          Vector(
            FunctionCallPE(_,None,_,false,
            LookupPE(NameP(_, "not"), None),
              Vector(LookupPE(NameP(_, "y"), None)),
            LendConstraintP(Some(ReadonlyP))),
            FunctionCallPE(_,None,_,false,
            LookupPE(NameP(_, "not"), None),
              Vector(LookupPE(NameP(_, "x"), None)),
            LendConstraintP(Some(ReadonlyP)))),
      LendConstraintP(Some(ReadonlyP))) =>
    }
  }

  test("Custom with noncustom binaries") {
    compile(CombinatorParsers.expression,"a + b florgle x * y") shouldHave {
      case FunctionCallPE(_,None,_,false,
        LookupPE(NameP(_, "florgle"), None),
          Vector(
            FunctionCallPE(_,None,_,false,
            LookupPE(NameP(_, "+"), None),
              Vector(LookupPE(NameP(_, "a"), None), LookupPE(NameP(_, "b"), None)),
              LendConstraintP(Some(ReadonlyP))),
            FunctionCallPE(_,None,_,false,
            LookupPE(NameP(_, "*"), None),
              Vector(LookupPE(NameP(_, "x"), None), LookupPE(NameP(_, "y"), None)),
              LendConstraintP(Some(ReadonlyP)))),
          LendConstraintP(Some(ReadonlyP))) =>
    }
  }

  test("Template calling") {
    compile(CombinatorParsers.expression,"MyNone< int >()") shouldHave {
      case FunctionCallPE(_,None,_,false,LookupPE(NameP(_, "MyNone"), Some(TemplateArgsP(_, Vector(NameOrRunePT(NameP(_, "int")))))),Vector(), LendConstraintP(Some(ReadonlyP))) =>
    }
    compile(CombinatorParsers.expression,"MySome< MyNone <int> >()") shouldHave {
      case FunctionCallPE(_,None,_,false,LookupPE(NameP(_, "MySome"), Some(TemplateArgsP(_, Vector(CallPT(_,NameOrRunePT(NameP(_, "MyNone")),Vector(NameOrRunePT(NameP(_, "int")))))))),Vector(), LendConstraintP(Some(ReadonlyP))) =>
    }
  }

  test(">=") {
    // It turns out, this was only parsing "9 >=" because it was looking for > specifically (in fact, it was looking
    // for + - * / < >) so it parsed as >(9, =) which was bad. We changed the infix operator parser to expect the
    // whitespace on both sides, so that it was forced to parse the entire thing.
    compile(CombinatorParsers.expression,"9 >= 3") shouldHave {
      case FunctionCallPE(_,None,_,false,LookupPE(NameP(_, ">="),None),Vector(ConstantIntPE(_, 9, _), ConstantIntPE(_, 3, _)),LendConstraintP(Some(ReadonlyP))) =>
    }
  }

  test("Indexing") {
    compile(CombinatorParsers.expression,"arr [4]") shouldHave {
      case IndexPE(_,LookupPE(NameP(_,arr),None),Vector(ConstantIntPE(_, 4, _))) =>
    }
  }

  test("Identity lambda") {
    compile(CombinatorParsers.expression, "{_}") shouldHave {
      case LambdaPE(_,FunctionP(_,FunctionHeaderP(_, None,Vector(),None,None,None,FunctionReturnP(_, _, _)),Some(BlockPE(_,Vector(MagicParamLookupPE(_)))))) =>
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
      Vector()) =>
    }
  }

  test("!=") {
    compile(CombinatorParsers.expression,"3 != 4") shouldHave {
      case FunctionCallPE(_, None, _, false, LookupPE(NameP(_, "!="), None), Vector(ConstantIntPE(_, 3, _), ConstantIntPE(_, 4, _)), LendConstraintP(Some(ReadonlyP))) =>
    }
  }

  test("lambda without surrounding parens") {
    compile(CombinatorParsers.expression, "{ 0 }()") shouldHave {
      case FunctionCallPE(_,None,_,false,LambdaPE(None,_),Vector(),_) =>
    }
  }

  test("Test templated lambda param") {
    val program = compile(CombinatorParsers.expression, "((a){a + a})!(3)")
    program shouldHave {
      case FunctionCallPE(_, None, _, false, PackPE(_, Vector(LambdaPE(_, _))), Vector(ConstantIntPE(_, 3, _)),UseP) =>
    }
    program shouldHave {
      case PatternPP(_,_, Some(CaptureP(_,LocalNameP(NameP(_, "a")))),None,None,None) =>
    }
    program shouldHave {
      case FunctionCallPE(_, None, _, false, LookupPE(NameP(_, "+"), None),Vector(LookupPE(NameP(_, "a"), None), LookupPE(NameP(_, "a"), None)),LendConstraintP(Some(ReadonlyP))) =>
    }
  }


  test("Function call") {
    val program = compile(CombinatorParsers.expression, "call(sum)")
    //    val main = program.lookupFunction("main")

    program shouldHave {
      case FunctionCallPE(_, None, _, false, LookupPE(NameP(_, "call"), None),Vector(LookupPE(NameP(_, "sum"), None)),LendConstraintP(Some(ReadonlyP))) =>
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
          Vector(
            ConstantIntPE(_, 2, _),
            PackPE(_,
              Vector(
                FunctionCallPE(_,None,_,false,
                  LookupPE(NameP(_,"-"),None),
                  Vector(ConstantIntPE(_, 5, _), ConstantIntPE(_, 7, _)),
                  LendConstraintP(Some(ReadonlyP)))))),
          LendConstraintP(Some(ReadonlyP))) =>
    }
  }

  test("static array " +
    "from values") {
    compile(CombinatorParsers.expression,
      "[][3, 5, 6]") shouldHave {
//      case StaticArrayFromValuesPE(_,Vector(ConstantIntPE(_, 3, _), ConstantIntPE(_, 5, _), ConstantIntPE(_, 6, _))) =>
//      case null =>
      case ConstructArrayPE(_,None,None,StaticSizedP(None),true,Vector(_, _, _)) =>
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
        Vector(LambdaPE(None,_))) =>
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
        Vector(_)) =>
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
        Vector(_)) =>
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
        Vector(_, _, _)) =>
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
        Vector(_, _)) =>
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
        Vector(_, _)) =>
    }
  }

  test("mutable runtime array from callable") {
    compile(CombinatorParsers.constructArrayExpr,
      "[mut *](6, triple)") shouldHave {
      case ConstructArrayPE(_,
        Some(MutabilityPT(_,MutableP)),
        None,
        RuntimeSizedP,
        false,
        Vector(_, _)) =>
    }

    compile(CombinatorParsers.expression,
      "[mut *](6, triple)") shouldHave {
      case ConstructArrayPE(_,
        Some(MutabilityPT(_,MutableP)),
        None,
        RuntimeSizedP,
        false,
        Vector(_, _)) =>
    }
  }


  test("Call callable expr") {
    compile(CombinatorParsers.expression,
      "(something.callable)(3)") shouldHave {
      case FunctionCallPE(
          _,None,_,false,
          PackPE(_, Vector(DotPE(_,LookupPE(NameP(_,"something"),None),_,false,NameP(_,"callable")))),
          Vector(_),UseP) =>
    }
  }

  test("Call callable expr with readwrite") {
    compile(CombinatorParsers.expression,
      "(something.callable)!(3)") shouldHave {
      case FunctionCallPE(
        _,None,_,false,
        PackPE(_, Vector(DotPE(_,LookupPE(NameP(_,"something"),None),_,false,NameP(_,"callable")))),
        Vector(_),
        // Note this UseP; the ! in the test is actually ignored because we're already Use-ing what came from the pack
        UseP) =>
    }
  }

  test("Array indexing") {
    compile(CombinatorParsers.expression,
      "board[i]") shouldHave {
      case IndexPE(_,LookupPE(NameP(_,"board"),None),Vector(LookupPE(NameP(_,"i"),None))) =>
    }
    compile(CombinatorParsers.expression,
      "this.board[i]") shouldHave {
      case IndexPE(_,DotPE(_,LookupPE(NameP(_,"this"),None),_,false,NameP(_,"board")),Vector(LookupPE(NameP(_,"i"),None))) =>
    }
  }

  test("mod and == precedence") {
    compile(CombinatorParsers.expression,
      """8 mod 2 == 0""") shouldHave {
      case FunctionCallPE(_,
        None,_,false,
        LookupPE(NameP(_,"=="),None),
        Vector(
          FunctionCallPE(_,
            None,_,false,
            LookupPE(NameP(_,"mod"),None),
            Vector(
              ConstantIntPE(_, 8, _),
              ConstantIntPE(_, 2, _)),
            LendConstraintP(Some(ReadonlyP))),
          ConstantIntPE(_, 0, _)),
        LendConstraintP(Some(ReadonlyP))) =>
    }
  }

  test("or and == precedence") {
    compile(CombinatorParsers.expression,
      """2 == 0 or false""") shouldHave {
      case OrPE(_,
        BlockPE(_,
          Vector(
            FunctionCallPE(_,
              None,_,false,
              LookupPE(NameP(_,"=="),None),
              Vector(
                ConstantIntPE(_, 2, _),
                ConstantIntPE(_, 0, _)),
              LendConstraintP(Some(ReadonlyP))))),
        BlockPE(_, Vector(ConstantBoolPE(_,false)))) =>
    }
  }

  test("Parenthesized method syntax will move instead of borrow") {
    val compilation =
      compile(
        CombinatorParsers.expression,
        """(bork).consumeBork()""".stripMargin)
    val (subjectExpr, loadAs) =
      compilation shouldHave {
        case MethodCallPE(_,_,
          subjectExpr,
          _,
          loadAs,
          false,
          LookupPE(NameP(_, "consumeBork"), _),
          _) => (subjectExpr, loadAs)
      }
    loadAs shouldEqual UseP
    subjectExpr match {
      case PackPE(_, Vector(LookupPE(NameP(_, "bork"), None))) =>
    }
  }

//  // See https://github.com/ValeLang/Vale/issues/108
//  test("Calling with space") {
//    compile(CombinatorParsers.expression,
//      """len (cached_dims)""") shouldHave {
//      case FunctionCallPE(_,_,_,_,LookupPE(StringP(_,"len"),None),Vector(LookupPE(StringP(_,"cached_dims"),None)),_) =>
//    }
//  }
}
