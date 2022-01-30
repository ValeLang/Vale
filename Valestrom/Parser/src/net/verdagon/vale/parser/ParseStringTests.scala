package net.verdagon.vale.parser

import net.verdagon.vale.parser.ExpressionParser.StopBeforeCloseBrace
import net.verdagon.vale.parser.ast._
import net.verdagon.vale.parser.expressions.ParseString
import net.verdagon.vale.parser.expressions.ParseString.StringPartChar
import net.verdagon.vale.{Collector, vimpl}
import org.scalatest.FunSuite
import org.scalatest.Matchers.convertToAnyShouldWrapper

class ParseStringTests extends FunSuite with Collector with TestParseUtils {
  test("Simple string") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), """"moo"""") shouldHave
      { case ConstantStrPE(_, "moo") => }
  }

  test("String with quote inside") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), """"m\"oo"""") shouldHave
      { case ConstantStrPE(_, "m\"oo") => }
  }

  test("String with unicode") {
    ParseString.parseFourDigitHexNum(ParsingIterator("000a")) shouldEqual Some(10)
    compile(ParseString.parseStringPart, "\\u000a") shouldEqual StringPartChar('\n')
    compile(ParseString.parseStringPart, "\\u001b") shouldEqual StringPartChar('\u001b')
    compileMaybe(ParseString.parseString, "\"\\u001b\"") match { case ConstantStrPE(_, "\u001b") => }
    compileMaybe(ParseString.parseString, "\"foo\\u001bbar\"") match { case ConstantStrPE(_, "foo\u001bbar") => }

    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), "\"foo\\u001bbar\"") match { case ConstantStrPE(_, "foo\u001bbar") => }
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
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), """"m'oo"""") shouldHave
      { case ConstantStrPE(_, "m'oo") => }
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), "\"\"\"m\'oo\"\"\"") shouldHave
      { case ConstantStrPE(_, "m'oo") => }
  }

  test("Short string interpolating") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), """"bl{4}rg"""") shouldHave
      { case StrInterpolatePE(_, Vector(ConstantStrPE(_, "bl"), ConstantIntPE(_, 4, _), ConstantStrPE(_, "rg"))) => }
  }

  test("Short string interpolating with call") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), """"bl{ns(4)}rg"""") shouldHave
      {
      case StrInterpolatePE(_,
        Vector(
          ConstantStrPE(_, "bl"),
          FunctionCallPE(_, _, LookupPE(LookupNameP(NameP(_, "ns")), _), Vector(ConstantIntPE(_, 4, _)), _),
          ConstantStrPE(_, "rg"))) =>
    }
  }

  test("Long string interpolating") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), "\"\"\"bl{4}rg\"\"\"") shouldHave
      { case StrInterpolatePE(_, Vector(ConstantStrPE(_, "bl"), ConstantIntPE(_, 4, _), ConstantStrPE(_, "rg"))) => }
  }

  test("Long string interpolating with call") {
    compile(ExpressionParser.parseExpression(_, StopBeforeCloseBrace), "\"\"\"bl\"{ns(4)}rg\"\"\"") shouldHave
      {
      case StrInterpolatePE(_,
      Vector(
        ConstantStrPE(_, "bl\""),
        FunctionCallPE(_, _, LookupPE(LookupNameP(NameP(_, "ns")), _), Vector(ConstantIntPE(_, 4, _)), _),
        ConstantStrPE(_, "rg"))) =>
    }
  }
}
