package dev.vale.parser

import dev.vale.parser.expressions.StringParser
import dev.vale.parser.ast._
import StringParser.StringPartChar
import dev.vale.Collector
import dev.vale.options.GlobalOptions
import dev.vale.parser.ast.{ConstantIntPE, ConstantStrPE, FunctionCallPE, LookupNameP, LookupPE, NameP, StrInterpolatePE}
import dev.vale.Collector
import org.scalatest.FunSuite
import org.scalatest.Matchers.convertToAnyShouldWrapper

class StringParserTests extends FunSuite with Collector with TestParseUtils {
  test("Simple string") {
    compileExpression(""""moo"""") shouldHave
      { case ConstantStrPE(_, "moo") => }
  }

  test("String with newline") {
    compileExpression("\"\"\"m\noo\"\"\"") shouldHave
      { case ConstantStrPE(_, "m\noo") => }
  }

  test("String with escaped braces") {
    compileExpression("\"\\{\\}\"") shouldHave
      { case ConstantStrPE(_, "{}") => }
  }

  test("String with quote inside") {
    compileExpression(""""m\"oo"""") shouldHave
      { case ConstantStrPE(_, "m\"oo") => }
  }

  test("String with unicode") {
    val e = new ExpressionParser(GlobalOptions(true, true, true, true))
    StringParser.parseFourDigitHexNum(ParsingIterator("000a")) shouldEqual Some(10)
    compile(e.stringParser.parseStringPart(_, 0), "\\u000a") shouldEqual StringPartChar('\n')
    compile(e.stringParser.parseStringPart(_, 0), "\\u001b") shouldEqual StringPartChar('\u001b')
    compileMaybe(e.stringParser.parseString, "\"\\u001b\"") match { case ConstantStrPE(_, "\u001b") => }
    compileMaybe(e.stringParser.parseString, "\"foo\\u001bbar\"") match { case ConstantStrPE(_, "foo\u001bbar") => }

    compileExpression("\"foo\\u001bbar\"") match { case ConstantStrPE(_, "foo\u001bbar") => }
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
    compileExpression(""""m'oo"""") shouldHave
      { case ConstantStrPE(_, "m'oo") => }
    compileExpression("\"\"\"m\'oo\"\"\"") shouldHave
      { case ConstantStrPE(_, "m'oo") => }
  }

  test("Short string interpolating") {
    compileExpression(""""bl{4}rg"""") shouldHave
      { case StrInterpolatePE(_, Vector(ConstantStrPE(_, "bl"), ConstantIntPE(_, 4, _), ConstantStrPE(_, "rg"))) => }
  }

  test("Short string interpolating with call") {
    compileExpression(""""bl{ns(4)}rg"""") shouldHave
      {
      case StrInterpolatePE(_,
        Vector(
          ConstantStrPE(_, "bl"),
          FunctionCallPE(_, _, LookupPE(LookupNameP(NameP(_, "ns")), _), Vector(ConstantIntPE(_, 4, _))),
          ConstantStrPE(_, "rg"))) =>
    }
  }

  test("Long string interpolating") {
    compileExpression("\"\"\"bl{4}rg\"\"\"") shouldHave
      { case StrInterpolatePE(_, Vector(ConstantStrPE(_, "bl"), ConstantIntPE(_, 4, _), ConstantStrPE(_, "rg"))) => }
  }

  test("Long string doesnt interpolate with brace then newline") {
    compileExpression(
      "\"\"\"bl{\n4}rg\"\"\"") shouldHave
      { case ConstantStrPE(_, "bl{\n4}rg") => }
  }

  test("Long string interpolates with brace then backslash") {
    compileExpression(
      "\"\"\"bl{\\\n4}rg\"\"\"") shouldHave
      { case StrInterpolatePE(_, Vector(ConstantStrPE(_, "bl"), ConstantIntPE(_, 4, _), ConstantStrPE(_, "rg"))) => }
  }

  test("Long string interpolating with call") {
    compileExpression("\"\"\"bl\"{ns(4)}rg\"\"\"") shouldHave
      {
      case StrInterpolatePE(_,
      Vector(
        ConstantStrPE(_, "bl\""),
        FunctionCallPE(_, _, LookupPE(LookupNameP(NameP(_, "ns")), _), Vector(ConstantIntPE(_, 4, _))),
        ConstantStrPE(_, "rg"))) =>
    }
  }
}
