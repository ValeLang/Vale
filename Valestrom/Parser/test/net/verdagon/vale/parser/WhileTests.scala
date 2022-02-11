package net.verdagon.vale.parser

import net.verdagon.vale.parser.ExpressionParser.StopBeforeCloseBrace
import net.verdagon.vale.parser.ast.{BinaryCallPE, BlockPE, ConsecutorPE, ConstantBoolPE, ConstantIntPE, LetPE, LocalNameDeclarationP, LookupNameP, LookupPE, NameP, PatternPP, VoidPE, WhilePE}
import net.verdagon.vale.parser.old.CombinatorParsers
import net.verdagon.vale.{Collector, Tests, vassert}
import org.scalatest.{FunSuite, Matchers}


class WhileTests extends FunSuite with Collector with TestParseUtils {
  test("Simple while loop") {
    compile(ExpressionParser.parseBlockContents(_, StopBeforeCloseBrace, false),"while true {}") shouldHave {
      case ConsecutorPE(Vector(WhilePE(_, ConstantBoolPE(_, true), BlockPE(_, VoidPE(_))), VoidPE(_))) =>
    }
  }

  test("Result after while loop") {
    compile(ExpressionParser.parseBlockContents(_, StopBeforeCloseBrace, false), "while true {} false") shouldHave {
      case Vector(
      WhilePE(_, ConstantBoolPE(_, true), BlockPE(_, VoidPE(_))),
      ConstantBoolPE(_, false)) =>
    }
  }

  test("While with condition declarations") {
    compile(ExpressionParser.parseBlockContents(_, StopBeforeCloseBrace, false), "while x = 4; x > 6; { }") shouldHave {
      case ConsecutorPE(
        Vector(
          WhilePE(_,
            ConsecutorPE(
              Vector(
                LetPE(_,PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,"x"))),None,None,None),ConstantIntPE(_,4,32)),
              BinaryCallPE(_,NameP(_,">"),LookupPE(LookupNameP(NameP(_,"x")),None),ConstantIntPE(_,6,32)),
              VoidPE(_))),
            BlockPE(_,VoidPE(_))),
        VoidPE(_))) =>
    }
  }
}
