package dev.vale.parsing

import dev.vale.{Collector, StrI, vimpl}
import dev.vale.parsing.ast.{AugmentPE, BinaryCallPE, BlockPE, BorrowP, ConsecutorPE, ConstantBoolPE, ConstantIntPE, DestructureP, FunctionCallPE, IfPE, LetPE, LocalNameDeclarationP, LookupNameP, LookupPE, MethodCallPE, NameP, NotPE, PatternPP, VoidPE}
import dev.vale.parsing.ast._
import dev.vale.options.GlobalOptions
import org.scalatest.{FunSuite, Matchers}


class IfTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("ifs") {
    compileExpression("if true { doBlarks(&x) } else { }") shouldHave {

      case IfPE(_,
        ConstantBoolPE(_, true),
        BlockPE(_,
          FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_, StrI("doBlarks"))),None),
            Vector(
              AugmentPE(_,BorrowP,LookupPE(LookupNameP(NameP(_, StrI("x"))),None))))),
        BlockPE(_,VoidPE(_))) =>
    }
  }

  test("if let") {
    compileExpression("if [u] = a {}") shouldHave {
      case IfPE(_,
        LetPE(_,
          PatternPP(_,None,None,None,
            Some(
              DestructureP(_,
                Vector(
                  PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_, StrI("u")))),None,None,None)))),
            None),
          LookupPE(LookupNameP(NameP(_, StrI("a"))),None)),
        BlockPE(_,VoidPE(_)),
        BlockPE(_,VoidPE(_))) =>
    }
  }

  test("If with condition declarations") {
    compileExpression("if x = 4; not x.isEmpty() { }") shouldHave {
      case IfPE(_,
        ConsecutorPE(
          Vector(
            LetPE(_,PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_, StrI("x")))),None,None,None),ConstantIntPE(_,4,None)),
            NotPE(_,MethodCallPE(_,LookupPE(LookupNameP(NameP(_, StrI("x"))),None),_,LookupPE(LookupNameP(NameP(_, StrI("isEmpty"))),None),Vector())))),
        BlockPE(_,VoidPE(_)),
        BlockPE(_,VoidPE(_))) =>
    }
  }

  test("19") {
    compileBlockContents(
      "newLen = if num == 0 { 1 } else { 2 };") shouldHave {
      case ConsecutorPE(
        Vector(
          LetPE(_,
            PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, StrI("newLen")))),None,None,None),
            IfPE(_,
              BinaryCallPE(_,NameP(_,StrI("==")),LookupPE(LookupNameP(NameP(_, StrI("num"))),None),ConstantIntPE(_,0,_)),
              BlockPE(_,ConstantIntPE(_,1,_)),
              BlockPE(_,ConstantIntPE(_,2,None)))),
          VoidPE(_))) =>
    }
  }
}
