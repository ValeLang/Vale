package dev.vale.parsing

import dev.vale.{Collector, Interner, StrI}
import dev.vale.parsing.ast.{BinaryCallPE, BlockPE, ConsecutorPE, ConstantBoolPE, ConstantIntPE, IExpressionPE, LetPE, LocalNameDeclarationP, LookupNameP, LookupPE, NameP, PatternPP, VoidPE, WhilePE}
import dev.vale.lexing.{Lexer, LexingIterator}
import dev.vale.options.GlobalOptions
import org.scalatest.{FunSuite, Matchers}


class WhileTests extends FunSuite with Collector with TestParseUtils {
  test("Simple while loop") {
    compileExpr("while true {}") shouldHave {
      case ConsecutorPE(Vector(WhilePE(_, ConstantBoolPE(_, true), BlockPE(_, VoidPE(_))), VoidPE(_))) =>
    }
  }

  test("Result after while loop") {
    compileExpr("while true {} false") shouldHave {
      case Vector(
      WhilePE(_, ConstantBoolPE(_, true), BlockPE(_, VoidPE(_))),
      ConstantBoolPE(_, false)) =>
    }
  }

  test("While with condition declarations") {
    compileExpr("while x = 4; x > 6; { }") shouldHave {
      case ConsecutorPE(
        Vector(
          WhilePE(_,
            ConsecutorPE(
              Vector(
                LetPE(_,PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_, StrI("x")))),None,None,None),ConstantIntPE(_,4,32)),
              BinaryCallPE(_,NameP(_,StrI(">")),LookupPE(LookupNameP(NameP(_, StrI("x"))),None),ConstantIntPE(_,6,32)),
              VoidPE(_))),
            BlockPE(_,VoidPE(_))),
        VoidPE(_))) =>
    }
  }
}
