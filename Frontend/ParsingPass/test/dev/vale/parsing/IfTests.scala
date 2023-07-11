package dev.vale.parsing

import dev.vale.{Collector, StrI, vimpl}
import dev.vale.parsing.ast.{AugmentPE, BinaryCallPE, BlockPE, BorrowP, ConsecutorPE, ConstantBoolPE, ConstantIntPE, DestructureP, FunctionCallPE, IfPE, LetPE, LocalNameDeclarationP, LookupNameP, LookupPE, MethodCallPE, NameP, NotPE, PatternPP, VoidPE}
import dev.vale.parsing.ast._
import dev.vale.options.GlobalOptions
import org.scalatest._


class IfTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("ifs") {
    compileExpressionExpect("if true { doBlarks(&x) } else { }") shouldHave {

      case IfPE(_,
        ConstantBoolPE(_, true),
        BlockPE(_,None,None,
          FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_, StrI("doBlarks"))),None),
            Vector(
              AugmentPE(_,BorrowP,LookupPE(LookupNameP(NameP(_, StrI("x"))),None))))),
        BlockPE(_,None,None,VoidPE(_))) =>
    }
  }

  test("if let") {
    compileExpressionExpect("if [u] = a {}") shouldHave {
      case IfPE(_,
        LetPE(_,
          PatternPP(_,None,None,
            Some(
              DestructureP(_,
                Vector(
                  PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("u"))), None)),None,None))))),
          LookupPE(LookupNameP(NameP(_, StrI("a"))),None)),
        BlockPE(_,None,None,VoidPE(_)),
        BlockPE(_,None,None,VoidPE(_))) =>
    }
  }

  test("If with condition declarations") {
    compileExpressionExpect("if x = 4; not x.isEmpty() { }") shouldHave {
      case IfPE(_,
        ConsecutorPE(
          Vector(
            LetPE(_,PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("x"))), None)),None,None),ConstantIntPE(_,4,None)),
            NotPE(_,MethodCallPE(_,LookupPE(LookupNameP(NameP(_, StrI("x"))),None),_,LookupPE(LookupNameP(NameP(_, StrI("isEmpty"))),None),Vector())))),
        BlockPE(_,None,None,VoidPE(_)),
        BlockPE(_,None,None,VoidPE(_))) =>
    }
  }

  test("19") {
    compileBlockContentsExpect(
      "newLen = if num == 0 { 1 } else { 2 };") shouldHave {
      case ConsecutorPE(
        Vector(
          LetPE(_,
            PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("newLen"))), None)),None,None),
            IfPE(_,
              BinaryCallPE(_,NameP(_,StrI("==")),LookupPE(LookupNameP(NameP(_, StrI("num"))),None),ConstantIntPE(_,0,_)),
              BlockPE(_,None,None,ConstantIntPE(_,1,_)),
              BlockPE(_,None,None,ConstantIntPE(_,2,None)))),
          VoidPE(_))) =>
    }
  }
}
