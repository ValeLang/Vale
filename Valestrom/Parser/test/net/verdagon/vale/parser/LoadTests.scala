package net.verdagon.vale.parser

import net.verdagon.vale.vassert
import net.verdagon.von.{JsonSyntax, VonPrinter}
import org.scalatest.{FunSuite, Matchers}


class LoadTests extends FunSuite with Matchers with Collector {
  private def compileProgramWithComments(code: String): FileP = {
    Parser.runParserForProgramAndCommentRanges(code) match {
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(result) => result._1
    }
  }
  private def compileProgram(code: String): FileP = {
    // The strip is in here because things inside the parser don't expect whitespace before and after
    Parser.runParser(code) match {
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(result) => result
    }
  }

  private def compile[T](parser: CombinatorParsers.Parser[T], code: String): T = {
    // The strip is in here because things inside the parser don't expect whitespace before and after
    CombinatorParsers.parse(parser, code.strip().toCharArray()) match {
      case CombinatorParsers.NoSuccess(msg, input) => {
        fail("Couldn't parse!\n" + input.pos.longString);
      }
      case CombinatorParsers.Success(expr, rest) => {
        vassert(rest.atEnd)
        expr
      }
    }
  }

  test("round trip") {
    val originalFile = Parser.runParser("""fn main() { 42 }""").get()
    val von = ParserVonifier.vonifyFile(originalFile)
    val json = new VonPrinter(JsonSyntax, 120).print(von)
    val loadedFile = ParsedLoader.load(json).get()
    originalFile shouldEqual loadedFile
  }
}
