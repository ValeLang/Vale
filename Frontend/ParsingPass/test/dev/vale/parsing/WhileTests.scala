package dev.vale.parsing

import dev.vale.{Collector, Interner, StrI}
import dev.vale.parsing.ast._
import dev.vale.lexing.{Lexer, LexingIterator}
import dev.vale.options.GlobalOptions
import org.scalatest._


class WhileTests extends FunSuite with Collector with TestParseUtils {
  test("Simple while loop") {
    compileBlockContentsExpect("while true {}") shouldHave {
      case WhilePE(_, ConstantBoolPE(_, true), BlockPE(_, None,None,VoidPE(_))) =>
    }
  }

  test("Result after while loop") {
    compileBlockContentsExpect("while true {} false") shouldHave {
      case WhilePE(_, ConstantBoolPE(_, true), BlockPE(_, None,None,VoidPE(_))) =>
    }
  }

  test("While with condition declarations") {
    compileBlockContentsExpect("while x = 4; x > 6 { }") shouldHave {
      case WhilePE(_,
        ConsecutorPE(
          Vector(
            LetPE(_,PatternPP(_,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("x"))), None)),None,None),ConstantIntPE(_,4,None)),
          BinaryCallPE(_,NameP(_,StrI(">")),LookupPE(LookupNameP(NameP(_, StrI("x"))),None),ConstantIntPE(_,6,None)))),
        BlockPE(_,None,None,VoidPE(_))) =>
    }
  }
}
