package net.verdagon.vale.parser

import net.verdagon.vale.{vassert, vfail}

trait TestParseUtils {
  def compileProgramWithComments(code: String): Program0 = {
    Parser.runParserForProgramAndCommentRanges(code) match {
      case ParseFailure(err) => vfail(err.toString)
      case ParseSuccess(result) => result._1
    }
  }
  def compileProgram(code: String): Program0 = {
    Parser.runParser(code) match {
      case ParseFailure(err) => vfail(err.toString)
      case ParseSuccess(result) => result
    }
  }

  def compileProgramForError(code: String): IParseError = {
    Parser.runParser(code) match {
      case ParseFailure(err) => err
      case ParseSuccess(result) => vfail("Expected error, but actually parsed invalid program:\n" + result)
    }
  }

  def compile[T](parser: CombinatorParsers.Parser[T], code: String): T = {
    // The strip is in here because things inside the parser don't expect whitespace before and after
    CombinatorParsers.parse(parser, code.strip().toCharArray()) match {
      case CombinatorParsers.NoSuccess(msg, input) => {
        vfail("Couldn't parse!\n" + input.pos.longString);
      }
      case CombinatorParsers.Success(expr, rest) => {
        vassert(rest.atEnd)
        expr
      }
    }
  }
}
