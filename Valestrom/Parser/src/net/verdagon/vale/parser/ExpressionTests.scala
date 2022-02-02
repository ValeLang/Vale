package net.verdagon.vale.parser

import net.verdagon.vale.parser.ExpressionParser.StopBeforeCloseBrace
import net.verdagon.vale.{Collector, vimpl}
import net.verdagon.vale.parser.ast._
import net.verdagon.vale.parser.expressions.ParseString
import net.verdagon.vale.parser.expressions.ParseString.StringPartChar
import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.{FunSuite, Matchers}

class ExpressionTests extends FunSuite with Collector with TestParseUtils {
  test("Simple int") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), "4") shouldHave
      { case ConstantIntPE(_, 4, 32) => }
  }

  test("Simple bool") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), "true") shouldHave
      { case ConstantBoolPE(_, true) => }
  }

  test("i64") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), "4i64") shouldHave
      { case ConstantIntPE(_, 4, 64) => }
  }

  test("Binary operator") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"4 + 5") shouldHave
      { case BinaryCallPE(_,NameP(_,"+"),ConstantIntPE(_,4,_),ConstantIntPE(_,5,_)) => }
  }

  test("Floats") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"4.2") shouldHave
      { case ConstantFloatPE(_, 4.2) => }
  }

  test("add as call") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"+(4, 5)") shouldHave
      { case FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_, "+")), None), Vector(ConstantIntPE(_, 4, _), ConstantIntPE(_, 5, _)),false) => }
  }

  test("range") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"a..b") shouldHave
      { case RangePE(_,LookupPE(LookupNameP(NameP(_,"a")),None),LookupPE(LookupNameP(NameP(_,"b")),None)) =>}
  }

  test("regular call") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"x(y)") shouldHave
      { case FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_, "x")), None), Vector(LookupPE(LookupNameP(NameP(_, "y")), None)),false) => }
  }

  test("not") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"not y") shouldHave
      { case NotPE(_,LookupPE(LookupNameP(NameP(_,"y")),None)) => }
  }

  test("Pointing result of function call") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"*Muta()") shouldHave
    { case AugmentPE(_,PointerP,ReadonlyP,FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_,"Muta")),None),Vector(),false)) => }
  }

  test("Borrowing result of function call") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"&Muta()") shouldHave
      { case AugmentPE(_,BorrowP,ReadonlyP,FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_,"Muta")),None),Vector(),false)) => }
  }

  test("Specifying heap") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"^Muta()") shouldHave
      { case AugmentPE(_,OwnP,ReadwriteP,FunctionCallPE(_,_,_,_,_)) => }
  }

  test("inline call ignored") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"inl Muta()") shouldHave
      { case FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_, "Muta")),None),Vector(),false) => }
  }

  test("Method call") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"x . shout ()") shouldHave
      { case MethodCallPE(_,LookupPE(LookupNameP(NameP(_,"x")),None),_,false,LookupPE(LookupNameP(NameP(_,"shout")),None),Vector()) => }
  }

  test("Method call readwrite") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"x !. shout ()") shouldHave
      { case MethodCallPE(_,LookupPE(LookupNameP(NameP(_,"x")),None),_,true,LookupPE(LookupNameP(NameP(_,"shout")),None),Vector()) => }
  }

  test("Method on member") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"x.moo.shout()") shouldHave
      {
        case MethodCallPE(_,
          DotPE(_, LookupPE(LookupNameP(NameP(_, "x")),None), _, NameP(_,"moo")),
            _,
            false,
            LookupPE(LookupNameP(NameP(_, "shout")),None), Vector()) =>
      }
  }

  test("Moving method call") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"(x ).shout()") shouldHave
      {
      case MethodCallPE(_,
        SubExpressionPE(_, LookupPE(LookupNameP(NameP(_, "x")),None)),
        _,false,
        LookupPE(LookupNameP(NameP(_, "shout")),None),
        Vector()) =>
    }
  }

//  test("Map method call") {
//    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"x*. shout()") shouldHave
//      {
//      case MethodCallPE(_,
//      LookupPE(LookupNameP(NameP(_, "x")),None),
//      _,false,
//      LookupPE(LookupNameP(NameP(_, "shout")),None),
//      Vector()) =>
//    }
//  }

  test("Templated function call") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"toArray<imm>( *result)") shouldHave
      {
        case FunctionCallPE(_,_,
        LookupPE(LookupNameP(NameP(_,"toArray")),Some(TemplateArgsP(_,Vector(MutabilityPT(_,ImmutableP))))),
        Vector(AugmentPE(_,PointerP,ReadonlyP,LookupPE(LookupNameP(NameP(_,"result")),None))),
        false) =>    }
  }

  test("Templated method call") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"result.toArray <imm> ()") shouldHave
      {
      case MethodCallPE(_,LookupPE(LookupNameP(NameP(_, "result")),None),_,false,LookupPE(LookupNameP(NameP(_, "toArray")),Some(TemplateArgsP(_, Vector(MutabilityPT(_,ImmutableP))))),Vector()) =>
    }
  }

  test("Custom binaries") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"not y florgle not x") shouldHave
      { case BinaryCallPE(_,NameP(_,"florgle"),NotPE(_,LookupPE(LookupNameP(NameP(_,"y")),None)),NotPE(_,LookupPE(LookupNameP(NameP(_,"x")),None))) => }
  }

  test("Custom with noncustom binaries") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"a + b florgle x * y") shouldHave
      {
        case BinaryCallPE(_,
          NameP(_,"florgle"),
          BinaryCallPE(_,
            NameP(_,"+"),
            LookupPE(LookupNameP(NameP(_,"a")),None),
            LookupPE(LookupNameP(NameP(_,"b")),None)),
          BinaryCallPE(_,
            NameP(_,"*"),
            LookupPE(LookupNameP(NameP(_,"x")),None),
            LookupPE(LookupNameP(NameP(_,"y")),None))) =>
      }
  }

  test("Template calling") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"MyNone< int >()") shouldHave
      {
        case FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_, "MyNone")),Some(TemplateArgsP(_,Vector(NameOrRunePT(NameP(_,"int")))))),Vector(),false) =>
    }
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"MySome< MyNone <int> >()") shouldHave
      {
      case FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_, "MySome")), Some(TemplateArgsP(_, Vector(CallPT(_,NameOrRunePT(NameP(_, "MyNone")),Vector(NameOrRunePT(NameP(_, "int")))))))),Vector(), false) =>
    }
  }

  test(">=") {
    // It turns out, this was only parsing "9 >=" because it was looking for > specifically (in fact, it was looking
    // for + - * / < >) so it parsed as >(9, =) which was bad. We changed the infix operator parser to expect the
    // whitespace on both sides, so that it was forced to parse the entire thing.
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"9 >= 3") shouldHave
      {
        case BinaryCallPE(_,NameP(_,">="),ConstantIntPE(_,9,_),ConstantIntPE(_,3,_)) =>
    }
  }

  test("Indexing") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"arr [4]") shouldHave
      { case BraceCallPE(_,_,LookupPE(LookupNameP(NameP(_,"arr")),None),Vector(ConstantIntPE(_,4,_)),_) => }
  }

  test("Single arg brace lambda") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), "x => { x }") shouldHave
      {
        case LambdaPE(_,
          FunctionP(_,
            FunctionHeaderP(_,
              None,Vector(),None,None,
              Some(ParamsP(_,Vector(PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,"x"))),None,None,None)))),
              FunctionReturnP(_,None,None)),
            Some(BlockPE(_,LookupPE(LookupNameP(NameP(_,"x")),None))))) =>
      }
  }

  test("Single arg no-brace lambda") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), "x => x") shouldHave
      {
        case LambdaPE(_,
          FunctionP(_,
            FunctionHeaderP(_,
              None,Vector(),None,None,
              Some(ParamsP(_,Vector(PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,"x"))),None,None,None)))),
              FunctionReturnP(_,None,None)),
            Some(BlockPE(_,LookupPE(LookupNameP(NameP(_,"x")),None))))) =>
      }
  }

  test("Single arg typed brace lambda") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), "(x int) => { x }") shouldHave
      {
        case LambdaPE(_,
          FunctionP(_,
            FunctionHeaderP(_,
              None,Vector(),None,None,
              Some(ParamsP(_,Vector(PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,"x"))),Some(NameOrRunePT(NameP(_,"int"))),None,None)))),
            _),
          _)) =>
      }
  }

  test("Argless lambda") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), "{_}") shouldHave
      {
        case LambdaPE(
          None,
          FunctionP(_,
            FunctionHeaderP(_,
              None,Vector(),None,None,None,FunctionReturnP(_,None,None)),
              Some(BlockPE(_,MagicParamLookupPE(_))))) =>
      }
  }

  test("Multi arg typed brace lambda") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), "(x, y) => x") shouldHave
      {
        case LambdaPE(
          None,
          FunctionP(_,
            FunctionHeaderP(_,
              None,Vector(),None,None,
              Some(
                ParamsP(_,
                  Vector(
                    PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,"x"))),None,None,None),
                    PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,"y"))),None,None,None)))),
              FunctionReturnP(_,None,None)),Some(BlockPE(_,LookupPE(LookupNameP(NameP(_,"x")),None))))) =>
      }
  }

  test("!=") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"3 != 4") shouldHave
      {
        case BinaryCallPE(_,NameP(_,"!="),ConstantIntPE(_,3,_),ConstantIntPE(_,4,_)) =>
    }
  }

  test("lambda without surrounding parens") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), "{ 0 }()") shouldHave
      {
      case FunctionCallPE(_,_,LambdaPE(None,_),Vector(),_) =>
    }
  }


  test("Function call") {
    val program = compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), "call(sum)")
    //    val main = program.lookupFunction("main")

    program shouldHave
      {
      case FunctionCallPE(_, _, LookupPE(LookupNameP(NameP(_, "call")), None),Vector(LookupPE(LookupNameP(NameP(_, "sum")), None)),false) =>
    }
  }

  test("Report leaving out semicolon or ending body after expression") {
    compileForError(
      ExpressionParser.parseBlockContents(_, StopBeforeCloseBrace, false),
      """
        |  a = 3;
        |  set x = 7 )
        """.stripMargin) match {
      case BadExpressionEnd(_) =>
    }
    compileForError(
      ExpressionParser.parseBlockContents(_, StopBeforeCloseBrace, false),
      """
        |  floop() ]
        """.stripMargin) match {
      case BadExpressionEnd(_) =>
    }
  }

  test("parens") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "2 * (5 - 7)") shouldHave
    { case BinaryCallPE(_,NameP(_,"*"),ConstantIntPE(_,2,_),SubExpressionPE(_, BinaryCallPE(_,NameP(_,"-"),ConstantIntPE(_,5,_),ConstantIntPE(_,7,_)))) => }
  }

  test("Precedence 1") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "(5 - 7) * 2") shouldHave
      { case BinaryCallPE(_,NameP(_,"*"),SubExpressionPE(_, BinaryCallPE(_,NameP(_,"-"),ConstantIntPE(_,5,_),ConstantIntPE(_,7,_))), ConstantIntPE(_,2,_)) => }
  }
  test("Precedence 2") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "5 - 7 * 2") shouldHave
      { case BinaryCallPE(_,NameP(_,"-"),ConstantIntPE(_,5,_),BinaryCallPE(_,NameP(_,"*"),ConstantIntPE(_,7,_),ConstantIntPE(_,2,_))) => }
  }

  test("static array from values") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "[#][3, 5, 6]") shouldHave
      {
//      case StaticArrayFromValuesPE(_,Vector(ConstantIntPE(_, 3, _), ConstantIntPE(_, 5, _), ConstantIntPE(_, 6, _))) =>
//      case null =>
      case ConstructArrayPE(_,None,Some(MutabilityPT(_,MutableP)),None,StaticSizedP(None),true,Vector(_, _, _)) =>
    }
  }

  test("static array from callable with rune") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "[#N]({_ * 2})") shouldHave
      {
//      case StaticArrayFromCallablePE(_,NameOrRunePT(NameP(_, "N")),_,_) =>
//      case null =>
      case ConstructArrayPE(_,
        None,
        Some(MutabilityPT(_,MutableP)),
        None,
        StaticSizedP(Some(NameOrRunePT(NameP(_,"N")))),
        false,
        Vector(LambdaPE(None,_))) =>
    }
  }

  test("Less than or equal") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "a <= b") shouldHave
      {
        case BinaryCallPE(_,NameP(_,"<="),LookupPE(LookupNameP(NameP(_,"a")),None),LookupPE(LookupNameP(NameP(_,"b")),None)) =>
      }
  }

  test("static array from callable") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "[#3](triple)") shouldHave
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
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "#[#3](triple)") shouldHave
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
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "#[#][3, 4, 5]") shouldHave
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
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "[](6, {_ * 2})") shouldHave
      {
      //      case StaticArrayFromCallablePE(_,NameOrRunePT(NameP(_, "N")),_,_) =>
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
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "[](6, triple)") shouldHave
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

  test("immutable runtime array from callable") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "#[](6, triple)") shouldHave
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
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "(3,)") shouldHave
      { case TuplePE(_,Vector(ConstantIntPE(_,3,_))) => }
  }

  test("Zero element tuple") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "()") shouldHave
      { case TuplePE(_,Vector()) => }
  }

  test("Two element tuple") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "(3,4)") shouldHave
      { case TuplePE(_,Vector(ConstantIntPE(_,3,_), ConstantIntPE(_,4,_))) => }
  }

  test("Three element tuple") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "(3,4,5)") shouldHave
      { case TuplePE(_,Vector(ConstantIntPE(_,3,_), ConstantIntPE(_,4,_), ConstantIntPE(_,5,_))) => }
  }

  test("Three element tuple trailing comma") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "(3,4,5,)") shouldHave
      { case TuplePE(_,Vector(ConstantIntPE(_,3,_), ConstantIntPE(_,4,_), ConstantIntPE(_,5,_))) => }
  }

  test("Call callable expr") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "(something.callable)(3)") shouldHave
      {
      case FunctionCallPE(
          _,_,
          SubExpressionPE(_, DotPE(_,LookupPE(LookupNameP(NameP(_, "something")),None),_,NameP(_,"callable"))),
          Vector(_),false) =>
      }
  }

  test("Call callable expr with readwrite") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "(something.callable)!(3)") shouldHave
      {
      case FunctionCallPE(
        _,
        _,
        SubExpressionPE(_, DotPE(_,LookupPE(LookupNameP(NameP(_, "something")),None),_,NameP(_,"callable"))),
        Vector(_),
        true) =>
    }
  }

  test("Array indexing") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "board[i]") shouldHave
      {
      case BraceCallPE(_,_,LookupPE(LookupNameP(NameP(_,"board")),None),Vector(LookupPE(LookupNameP(NameP(_,"i")),None)),false) =>
      }
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      "this.board[i]") shouldHave
      {
      case BraceCallPE(_,_,DotPE(_,LookupPE(LookupNameP(NameP(_, "this")),None),_,NameP(_,"board")),Vector(LookupPE(LookupNameP(NameP(_,"i")),None)),false) =>
      }
  }

  test("mod and == precedence") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      """8 mod 2 == 0""") shouldHave
      {
      case BinaryCallPE(_,
        NameP(_, "mod"),
        ConstantIntPE(_, 8, _),
        BinaryCallPE(_,
          NameP(_, "=="),
          ConstantIntPE(_, 2, _),
          ConstantIntPE(_, 0, _))) =>
    }
  }

  test("or and == precedence") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
      """2 == 0 or false""") shouldHave
      {
      case OrPE(_,
        BinaryCallPE(_,
          NameP(_, "=="),
          ConstantIntPE(_, 2, _),
          ConstantIntPE(_, 0, _)),
        BlockPE(_, ConstantBoolPE(_,false))) =>
    }
  }

  test("Test templated lambda param") {
    val program =
      compile(
        ExpressionParser.parseExpression(_, StopBeforeCloseBrace),
        "(a => a + a)!(3)")
    program shouldHave {
      case FunctionCallPE(_, _, SubExpressionPE(_, LambdaPE(_, _)), Vector(ConstantIntPE(_, 3, _)), true) =>
    }
    program shouldHave {
      case PatternPP(_,_, Some(LocalNameDeclarationP(NameP(_, "a"))),None,None,None) =>
    }
    program shouldHave {
      case BinaryCallPE(_, NameP(_, "+"), LookupPE(LookupNameP(NameP(_, "a")), None), LookupPE(LookupNameP(NameP(_, "a")), None)) =>
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
