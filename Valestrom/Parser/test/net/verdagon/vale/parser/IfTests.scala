package net.verdagon.vale.parser

import net.verdagon.vale.parser.ExpressionParser.StopBeforeCloseBrace
import net.verdagon.vale.parser.ast._
import net.verdagon.vale.parser.old.CombinatorParsers
import net.verdagon.vale.{Collector, Tests, vassert}
import org.scalatest.{FunSuite, Matchers}


class IfTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("ifs") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), "if true { doBlarks(*x) } else { }") shouldHave {

      case IfPE(_,
        ConstantBoolPE(_, true),
        BlockPE(_,
          FunctionCallPE(_,_,LookupPE(LookupNameP(NameP(_,"doBlarks")),None),
            Vector(
              AugmentPE(_,PointerP,Some(ReadonlyP),LookupPE(LookupNameP(NameP(_,"x")),None))),false)),
        BlockPE(_,VoidPE(_))) =>
    }
  }

  test("if let") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), "if [u] = a {}") shouldHave {
      case IfPE(_,
        LetPE(_,
          PatternPP(_,None,None,None,
            Some(
              DestructureP(_,
                Vector(
                  PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,"u"))),None,None,None)))),
            None),
          LookupPE(LookupNameP(NameP(_, "a")),None)),
        BlockPE(_,VoidPE(_)),
        BlockPE(_,VoidPE(_))) =>
    }
  }

  test("If with condition declarations") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace),"if x = 4; not x.isEmpty() { }") shouldHave {
      case IfPE(_,
        ConsecutorPE(
          Vector(
            LetPE(_,PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,"x"))),None,None,None),ConstantIntPE(_,4,32)),
            NotPE(_,MethodCallPE(_,LookupPE(LookupNameP(NameP(_,"x")),None),_,false,LookupPE(LookupNameP(NameP(_,"isEmpty")),None),Vector())))),
        BlockPE(_,VoidPE(_)),
        BlockPE(_,VoidPE(_))) =>
    }
  }

  test("19") {
    compile(ExpressionParser.parseBlockContents(_, StopBeforeCloseBrace, false),
      "newLen = if num == 0 { 1 } else { 2 };") shouldHave {
      case ConsecutorPE(
        Vector(
          LetPE(_,
            PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_,"newLen"))),None,None,None),
            IfPE(_,
              BinaryCallPE(_,NameP(_,"=="),LookupPE(LookupNameP(NameP(_,"num")),None),ConstantIntPE(_,0,_)),
              BlockPE(_,ConstantIntPE(_,1,_)),
              BlockPE(_,ConstantIntPE(_,2,32)))),
          VoidPE(_))) =>
    }
  }
}
