package dev.vale.parsing

import dev.vale.{Collector, StrI}
import dev.vale.lexing.{CantUseBreakInExpression, CantUseReturnInExpression}
import dev.vale.parsing.ast.{AugmentPE, BinaryCallPE, BlockPE, BorrowP, BraceCallPE, CallPT, ConstantBoolPE, ConstantFloatPE, ConstantIntPE, ConstructArrayPE, DotPE, FunctionCallPE, FunctionHeaderP, FunctionP, FunctionReturnP, ImmutableP, IntPT, LambdaPE, LocalNameDeclarationP, LookupNameP, LookupPE, MagicParamLookupPE, MethodCallPE, MutabilityPT, MutableP, NameOrRunePT, NameP, NotPE, OrPE, OwnP, ParamsP, PatternPP, RangePE, RuntimeSizedP, StaticSizedP, SubExpressionPE, TemplateArgsP, TuplePE, UnletPE, _}
import org.scalatest.FunSuite

class ExpressionTests extends FunSuite with Collector with TestParseUtils {
  test("Simple int") {
    val expr = compileExpressionExpect("4")
     expr shouldHave { case ConstantIntPE(_, 4, None) => }
  }

  test("Simple bool") {
    compileExpressionExpect("true") shouldHave
      { case ConstantBoolPE(_, true) => }
  }

  test("i64") {
    compileExpressionExpect("4i64") shouldHave
      { case ConstantIntPE(_, 4L, Some(64L)) => }
  }

  test("Binary operator") {
    val expr = compileExpressionExpect("4 + 5")
    expr shouldHave
      { case BinaryCallPE(_,NameP(_,StrI("+")),ConstantIntPE(_,4,_),ConstantIntPE(_,5,_)) => }
  }

  test("Floats") {
    compileExpressionExpect("4.2") shouldHave
      { case ConstantFloatPE(_, 4.2) => }
  }

  test("Number range") {
    compileExpressionExpect("0..5") shouldHave
      { case RangePE(_,ConstantIntPE(_,0,_),ConstantIntPE(_,5,_)) => }
  }

  test("add as call") {
    compileExpressionExpect("+(4, 5)") shouldHave
      { case FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_, StrI("+"))), None), Vector(ConstantIntPE(_, 4, _), ConstantIntPE(_, 5, _))) => }
  }

  test("Passing == overload set") {
    compileExpressionExpect("moo(4, ==)") shouldHave {
      case FunctionCallPE(_,_,
        _,
        Vector(
          _,
          LookupPE(LookupNameP(NameP(_,StrI("=="))),None))) =>
    }
  }

  test("Call then binary operator") {
    compileExpressionExpect("str(i) + 5") shouldHave {
      case BinaryCallPE(_,
        NameP(_,StrI("+")),
        FunctionCallPE(_,
          _,
          LookupPE(LookupNameP(NameP(_,StrI("str"))),None),
          Vector(LookupPE(LookupNameP(NameP(_,StrI("i"))),None))),
        ConstantIntPE(_,5,None)) =>
    }
  }

  test("range") {
    compileExpressionExpect("a..b") shouldHave
      { case RangePE(_,LookupPE(LookupNameP(NameP(_,StrI("a"))),None),LookupPE(LookupNameP(NameP(_,StrI("b"))),None)) =>}
  }

  test("regular call") {
    compileExpressionExpect("x(y)") shouldHave
      { case FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_, StrI("x"))), None), Vector(LookupPE(LookupNameP(NameP(_, StrI("y"))), None))) => }
  }

  test("not") {
    compileExpressionExpect("not y") shouldHave
      { case NotPE(_,LookupPE(LookupNameP(NameP(_,StrI("y"))),None)) => }
  }

  test("Borrowing result of function call") {
    compileExpressionExpect("&Muta()") shouldHave
      { case AugmentPE(_,BorrowP,FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_,StrI("Muta"))),None),Vector())) => }
  }

  test("Specifying heap") {
    compileExpressionExpect("^Muta()") shouldHave
      { case AugmentPE(_,OwnP,FunctionCallPE(_,_,_,_)) => }
  }

  test("inline call ignored") {
    compileExpressionExpect("inl Muta()") shouldHave
      { case FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_, StrI("Muta"))),None),Vector()) => }
  }

  test("Method call") {
    compileExpressionExpect("x . shout ()") shouldHave
      { case MethodCallPE(_,LookupPE(LookupNameP(NameP(_,StrI("x"))),None),_,LookupPE(LookupNameP(NameP(_,StrI("shout"))),None),Vector()) => }
  }

  test("Mapping method call") {
    // These arent implemented yet, we currently just parse these as method calls to support
    // snippets on the site.
    compileExpressionExpect("x *. shout ()") shouldHave
      { case MethodCallPE(_,LookupPE(LookupNameP(NameP(_,StrI("x"))),None),_,LookupPE(LookupNameP(NameP(_,StrI("shout"))),None),Vector()) => }
  }

  test("Method on member") {
    compileExpressionExpect("x.moo.shout()") shouldHave
      {
        case MethodCallPE(_,
          DotPE(_, LookupPE(LookupNameP(NameP(_, StrI("x"))),None), _, NameP(_,StrI("moo"))),
          _,
          LookupPE(LookupNameP(NameP(_, StrI("shout"))),None),
          Vector()) =>
      }
  }

  test("Moving method call") {
    compileExpressionExpect("(x ).shout()") shouldHave
      {
      case MethodCallPE(_,
        SubExpressionPE(_, LookupPE(LookupNameP(NameP(_, StrI("x"))),None)),
        _,
        LookupPE(LookupNameP(NameP(_, StrI("shout"))),None),
        Vector()) =>
    }
  }

//  test("Map method call") {
//    compileExpression("x*. shout()") shouldHave
//      {
//      case MethodCallPE(_,
//      LookupPE(LookupNameP(NameP(_, StrI("x"))),None),
//      _,false,
//      LookupPE(LookupNameP(NameP(_, StrI("shout"))),None),
//      Vector()) =>
//    }
//  }

  test("Templated function call") {
    compileExpressionExpect("toArray<imm>( &result)") shouldHave
      {
        case FunctionCallPE(_,_,
        LookupPE(LookupNameP(NameP(_,StrI("toArray"))),Some(TemplateArgsP(_,Vector(MutabilityPT(_,ImmutableP))))),
        Vector(AugmentPE(_,BorrowP,LookupPE(LookupNameP(NameP(_,StrI("result"))),None)))) =>
      }
  }

  test("Templated method call") {
    compileExpressionExpect("result.toArray <imm> ()") shouldHave
      {
      case MethodCallPE(_,LookupPE(LookupNameP(NameP(_, StrI("result"))),None),_,LookupPE(LookupNameP(NameP(_, StrI("toArray"))),Some(TemplateArgsP(_, Vector(MutabilityPT(_,ImmutableP))))),Vector()) =>
    }
  }

  test("Custom binaries") {
    compileExpressionExpect("not y florgle not x") shouldHave
      { case BinaryCallPE(_,NameP(_,StrI("florgle")),NotPE(_,LookupPE(LookupNameP(NameP(_,StrI("y"))),None)),NotPE(_,LookupPE(LookupNameP(NameP(_,StrI("x"))),None))) => }
  }

  test("Custom with noncustom binaries") {
    compileExpressionExpect("a + b florgle x * y") shouldHave
      {
        case BinaryCallPE(_,
          NameP(_,StrI("florgle")),
          BinaryCallPE(_,
            NameP(_,StrI("+")),
            LookupPE(LookupNameP(NameP(_,StrI("a"))),None),
            LookupPE(LookupNameP(NameP(_,StrI("b"))),None)),
          BinaryCallPE(_,
            NameP(_,StrI("*")),
            LookupPE(LookupNameP(NameP(_,StrI("x"))),None),
            LookupPE(LookupNameP(NameP(_,StrI("y"))),None))) =>
      }
  }

  test("Template calling") {
    compileExpressionExpect("MyNone< int >()") shouldHave
      {
        case FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_, StrI("MyNone"))),Some(TemplateArgsP(_,Vector(NameOrRunePT(NameP(_,StrI("int"))))))),Vector()) =>
    }
    compileExpressionExpect("MySome< MyNone <int> >()") shouldHave
      {
      case FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_, StrI("MySome"))), Some(TemplateArgsP(_, Vector(CallPT(_,NameOrRunePT(NameP(_, StrI("MyNone"))),Vector(NameOrRunePT(NameP(_, StrI("int"))))))))),Vector()) =>
    }
  }

  test(">=") {
    // It turns out, this was only parsing "9 >=" because it was looking for > specifically (in fact, it was looking
    // for + - * / < >) so it parsed as >(9, =) which was bad. We changed the infix operator parser to expect the
    // whitespace on both sides, so that it was forced to parse the entire thing.
    compileExpressionExpect("9 >= 3") shouldHave
      {
        case BinaryCallPE(_,NameP(_,StrI(">=")),ConstantIntPE(_,9,_),ConstantIntPE(_,3,_)) =>
    }
  }

  test("Indexing") {
    compileExpressionExpect("arr [4]") shouldHave
      { case BraceCallPE(_,_,LookupPE(LookupNameP(NameP(_,StrI("arr"))),None),Vector(ConstantIntPE(_,4,_)),_) => }
  }

  test("Single arg brace lambda") {
    compileExpressionExpect("x => { x }") shouldHave
      {
        case LambdaPE(_,
          FunctionP(_,
            FunctionHeaderP(_,
              None,Vector(),None,None,
              Some(ParamsP(_,Vector(PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,StrI("x")))),None,None,None)))),
              FunctionReturnP(_,None)),
            Some(BlockPE(_,_,LookupPE(LookupNameP(NameP(_,StrI("x"))),None))))) =>
      }
  }

  test("Single arg no-brace lambda") {
    compileExpressionExpect("x => x") shouldHave
      {
        case LambdaPE(_,
          FunctionP(_,
            FunctionHeaderP(_,
              None,Vector(),None,None,
              Some(ParamsP(_,Vector(PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,StrI("x")))),None,None,None)))),
              FunctionReturnP(_,None)),
            Some(BlockPE(_,_,LookupPE(LookupNameP(NameP(_,StrI("x"))),None))))) =>
      }
  }

  test("Single arg typed brace lambda") {
    compileExpressionExpect("(x int) => { x }") shouldHave
      {
        case LambdaPE(_,
          FunctionP(_,
            FunctionHeaderP(_,
              None,Vector(),None,None,
              Some(ParamsP(_,Vector(PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,StrI("x")))),Some(NameOrRunePT(NameP(_,StrI("int")))),None,None)))),
              _),
          _)) =>
      }
  }

  test("Argless lambda") {
    compileExpressionExpect("{_}") shouldHave
      {
        case LambdaPE(
          None,
          FunctionP(_,
            FunctionHeaderP(_,
              None,Vector(),None,None,None,FunctionReturnP(_,None)),
              Some(BlockPE(_,_,MagicParamLookupPE(_))))) =>
      }
  }

  test("Multi arg typed brace lambda") {
    compileExpressionExpect("(x, y) => x") shouldHave
      {
        case LambdaPE(
          None,
          FunctionP(_,
            FunctionHeaderP(_,
              None,Vector(),None,None,
              Some(
                ParamsP(_,
                  Vector(
                    PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,StrI("x")))),None,None,None),
                    PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,StrI("y")))),None,None,None)))),
              FunctionReturnP(_,None)),
            Some(BlockPE(_,_,LookupPE(LookupNameP(NameP(_,StrI("x"))),None))))) =>
      }
  }

  test("Destructuring lambda") {
    compileExpressionExpect("([x, y]) => x") shouldHave
      {
        case LambdaPE(
          None,
          FunctionP(_,
            FunctionHeaderP(_,
              None,Vector(),None,None,
              Some(
                ParamsP(_,
                  Vector(
                    PatternPP(_,
                      None,None,None,
                      Some(
                        DestructureP(_,
                          Vector(
                            PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,StrI("x")))),None,None,None),
                            PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,StrI("y")))),None,None,None)))),
                      None)))),
              FunctionReturnP(_,None)),
            Some(BlockPE(_,_,LookupPE(LookupNameP(NameP(_,StrI("x"))),None))))) =>
      }
  }

  test("dot symbol") {
    compileExpressionExpect("""myPath./("subdir")""") shouldHave
      {
        case MethodCallPE(_,
          LookupPE(LookupNameP(NameP(_,StrI("myPath"))),None),
          _,
          LookupPE(LookupNameP(NameP(_,StrI("/"))),None),
          Vector(ConstantStrPE(_,"subdir"))) =>
      }
  }

  test("!=") {
    compileExpressionExpect("3 != 4") shouldHave
      {
        case BinaryCallPE(_,NameP(_,StrI("!=")),ConstantIntPE(_,3,_),ConstantIntPE(_,4,_)) =>
    }
  }

  test("set call isn't interpreted as a set expression") {
    compileExpressionExpect("set(true)") shouldHave {
      case FunctionCallPE(_,_, LookupPE(LookupNameP(NameP(_,StrI("set"))),None), _) =>
    }
  }

  test("2D array access") {
    // We had a bug where the lexer was interpreting that 2.1 as a float.
    compileExpressionExpect("arr.2.1") shouldHave {
      case DotPE(_,
        DotPE(_,
          LookupPE(LookupNameP(NameP(_,StrI("arr"))),None),
          _,
          NameP(_,StrI("2"))),
        _,
        NameP(_,StrI("1"))) =>
    }
  }

  test("lambda without surrounding parens") {
    compileExpressionExpect("{ 0 }()") shouldHave
      {
      case FunctionCallPE(_,_,LambdaPE(None,_),Vector()) =>
    }
  }


  test("Function call") {
    val program = compileExpressionExpect("call(sum)")
    //    val main = program.lookupFunction("main")

    program shouldHave
      {
      case FunctionCallPE(_, _, LookupPE(LookupNameP(NameP(_, StrI("call"))), None),Vector(LookupPE(LookupNameP(NameP(_, StrI("sum"))), None))) =>
    }
  }

  test("Test inner expression unlet") {
    val program = compileExpressionExpect("destroy(unlet enemy)")

    program shouldHave {
      case FunctionCallPE(_, _, LookupPE(LookupNameP(NameP(_, StrI("destroy"))), None),Vector(UnletPE(_, LookupNameP(NameP(_, StrI("enemy")))))) =>
    }
  }

  test("Detect break in expr") {
    // See BRCOBS
    compileExpressionForError(
      """
        |a(b, break)
        |""".stripMargin) match {
      case CantUseBreakInExpression(_) =>
    }
  }

  test("Detect return in expr") {
    // See BRCOBS
    compileExpressionForError(
      """
        |a(b, return)
        |""".stripMargin) match {
      case CantUseReturnInExpression(_) =>
    }
  }

  test("parens") {
    compileExpressionExpect("2 * (5 - 7)") shouldHave
    { case BinaryCallPE(_,NameP(_,StrI("*")),ConstantIntPE(_,2,_),SubExpressionPE(_, BinaryCallPE(_,NameP(_,StrI("-")),ConstantIntPE(_,5,_),ConstantIntPE(_,7,_)))) => }
  }

  test("Precedence 1") {
    compileExpressionExpect("(5 - 7) * 2") shouldHave
      { case BinaryCallPE(_,NameP(_,StrI("*")),SubExpressionPE(_, BinaryCallPE(_,NameP(_,StrI("-")),ConstantIntPE(_,5,_),ConstantIntPE(_,7,_))), ConstantIntPE(_,2,_)) => }
  }
  test("Precedence 2") {
    compileExpressionExpect("5 - 7 * 2") shouldHave
      { case BinaryCallPE(_,NameP(_,StrI("-")),ConstantIntPE(_,5,_),BinaryCallPE(_,NameP(_,StrI("*")),ConstantIntPE(_,7,_),ConstantIntPE(_,2,_))) => }
  }

  test("static array from values") {
    compileExpressionExpect("[#](3, 5, 6)") shouldHave
      {
//      case StaticArrayFromValuesPE(_,Vector(ConstantIntPE(_, 3, _), ConstantIntPE(_, 5, _), ConstantIntPE(_, 6, _))) =>
//      case null =>
      case ConstructArrayPE(_,None,Some(MutabilityPT(_,MutableP)),None,StaticSizedP(None),true,Vector(_, _, _)) =>
      }
  }

  test("static array from values with newlines") {
    compileExpressionExpect("[#](\n3\n)") shouldHave
      {
        //      case StaticArrayFromValuesPE(_,Vector(ConstantIntPE(_, 3, _), ConstantIntPE(_, 5, _), ConstantIntPE(_, 6, _))) =>
        //      case null =>
        case ConstructArrayPE(_,_,_,_,_,_,_) =>
      }
  }

  test("static array from callable with rune") {
    compileExpressionExpect("[#N]({_ * 2})") shouldHave
      {
//      case StaticArrayFromCallablePE(_,NameOrRunePT(NameP(_, StrI("N"))),_,_) =>
//      case null =>
      case ConstructArrayPE(_,
        None,
        Some(MutabilityPT(_,MutableP)),
        None,
        StaticSizedP(Some(NameOrRunePT(NameP(_,StrI("N"))))),
        false,
        Vector(LambdaPE(None,_))) =>
    }
  }

  test("Less than or equal") {
    compileExpressionExpect("a <= b") shouldHave
      {
        case BinaryCallPE(_,NameP(_,StrI("<=")),LookupPE(LookupNameP(NameP(_,StrI("a"))),None),LookupPE(LookupNameP(NameP(_,StrI("b"))),None)) =>
      }
  }

  test("static array from callable") {
    compileExpressionExpect("[#3](triple)") shouldHave
      {
      case ConstructArrayPE(_,
        None,
        Some(MutabilityPT(_,MutableP)),
        None,
        StaticSizedP(Some(IntPT(_,3))),
        false,
        Vector(_)) =>
    }
  }

  test("immutable static array from callable") {
    compileExpressionExpect("#[#3](triple)") shouldHave
      {
      case ConstructArrayPE(_,
        None,
        Some(MutabilityPT(_,ImmutableP)),
        None,
        StaticSizedP(Some(IntPT(_,3))),
        false,
        Vector(_)) =>
    }
  }

  test("immutable static array from callable, no size") {
    compileExpressionExpect("#[#](3, 4, 5)") shouldHave
      {
      case ConstructArrayPE(_,
        None,
        Some(MutabilityPT(_,ImmutableP)),
        None,
        StaticSizedP(None),
        true,
        Vector(_, _, _)) =>
    }
  }

  test("runtime array from callable with rune") {
    compileExpressionExpect("[](6, {_ * 2})") shouldHave
      {
      //      case StaticArrayFromCallablePE(_,NameOrRunePT(NameP(_, StrI("N"))),_,_) =>
      //      case null =>
      case ConstructArrayPE(_,
        None,
        Some(MutabilityPT(_,MutableP)),
        None,
        RuntimeSizedP,
        false,
        Vector(_, _)) =>
    }
  }

  test("runtime array from callable") {
    compileExpressionExpect("[](6, triple)") shouldHave
      {
        case ConstructArrayPE(_,
        None,
        Some(MutabilityPT(_,MutableP)),
        None,
        RuntimeSizedP,
        false,
        Vector(_, _)) =>
      }
  }

  test("Double RSA with type") {
    compileExpressionExpect("[][]bool(42)") shouldHave
      {
        case ConstructArrayPE(_,
        Some(RuntimeSizedArrayPT(_,MutabilityPT(_,MutableP),NameOrRunePT(NameP(_,StrI("bool"))))),
        Some(MutabilityPT(_,MutableP)),
        None,
        RuntimeSizedP,
        false,
        Vector(ConstantIntPE(_,42,None)))
        =>
      }
  }

  test("immutable runtime array from callable") {
    compileExpressionExpect("#[](6, triple)") shouldHave
      {
      case ConstructArrayPE(_,
        None,
        Some(MutabilityPT(_,ImmutableP)),
        None,
        RuntimeSizedP,
        false,
        Vector(_, _)) =>
    }
  }


  test("One element tuple") {
    compileExpressionExpect("(3,)") shouldHave
      { case TuplePE(_,Vector(ConstantIntPE(_,3,_))) => }
  }

  test("Zero element tuple") {
    compileExpressionExpect("()") shouldHave
      { case TuplePE(_,Vector()) => }
  }

  test("Two element tuple") {
    compileExpressionExpect("(3,4)") shouldHave
      { case TuplePE(_,Vector(ConstantIntPE(_,3,_), ConstantIntPE(_,4,_))) => }
  }

  test("Three element tuple") {
    compileExpressionExpect("(3,4,5)") shouldHave
      { case TuplePE(_,Vector(ConstantIntPE(_,3,_), ConstantIntPE(_,4,_), ConstantIntPE(_,5,_))) => }
  }

  test("Three element tuple trailing comma") {
    compileExpressionExpect("(3,4,5,)") shouldHave
      { case TuplePE(_,Vector(ConstantIntPE(_,3,_), ConstantIntPE(_,4,_), ConstantIntPE(_,5,_))) => }
  }

  test("Call callable expr") {
    compileExpressionExpect("(something.callable)(3)") shouldHave
      {
      case FunctionCallPE(
          _,_,
          SubExpressionPE(_, DotPE(_,LookupPE(LookupNameP(NameP(_, StrI("something"))),None),_,NameP(_,StrI("callable")))),
          Vector(_)) =>
      }
  }

  test("Array indexing") {
    compileExpressionExpect("board[i]") shouldHave
      {
      case BraceCallPE(_,_,LookupPE(LookupNameP(NameP(_,StrI("board"))),None),Vector(LookupPE(LookupNameP(NameP(_,StrI("i"))),None)),false) =>
      }
    compileExpressionExpect("this.board[i]") shouldHave
      {
      case BraceCallPE(_,_,DotPE(_,LookupPE(LookupNameP(NameP(_, StrI("this"))),None),_,NameP(_,StrI("board"))),Vector(LookupPE(LookupNameP(NameP(_,StrI("i"))),None)),false) =>
      }
  }

  test("mod and == precedence") {
    compileExpressionExpect("""8 mod 2 == 0""") shouldHave
      {
      case BinaryCallPE(_,
      NameP(_, StrI("==")),
        BinaryCallPE(_,
          NameP(_, StrI("mod")),
          ConstantIntPE(_, 8, _),
          ConstantIntPE(_, 2, _)),
        ConstantIntPE(_, 0, _)) =>
    }
  }

  test("or and == precedence") {
    compileExpressionExpect("""2 == 0 or false""") shouldHave
      {
      case OrPE(_,
        BinaryCallPE(_,
          NameP(_, StrI("==")),
          ConstantIntPE(_, 2, _),
          ConstantIntPE(_, 0, _)),
        BlockPE(_, _, ConstantBoolPE(_,false))) =>
    }
  }

  test("Test templated lambda param") {
    val program = compileExpressionExpect("(a => a + a)(3)")
    program shouldHave {
      case FunctionCallPE(_, _, SubExpressionPE(_, LambdaPE(_, _)), Vector(ConstantIntPE(_, 3, _))) =>
    }
    program shouldHave {
      case PatternPP(_,_, Some(LocalNameDeclarationP(NameP(_, StrI("a")))),None,None,None) =>
    }
    program shouldHave {
      case BinaryCallPE(_, NameP(_, StrI("+")), LookupPE(LookupNameP(NameP(_, StrI("a"))), None), LookupPE(LookupNameP(NameP(_, StrI("a"))), None)) =>
    }
  }

//  // See https://github.com/ValeLang/Vale/issues/108
//  test("Calling with space") {
//    compile(CombinatorParsers.expression(true),
//      """len (cached_dims)""") shouldHave {
//      case FunctionCallPE(_,_,_,_,LookupPE(StringP(_,"len"),None),Vector(LookupPE(StringP(_,"cached_dims"),None)),_) =>
//    }
//  }
}
