package net.verdagon.vale.parser

import net.verdagon.vale.{FileCoordinate, FileCoordinateMap, vassert, vfail}

trait TestParseUtils {
  def compileProgramWithComments(code: String): FileP = {
    Parser.runParserForProgramAndCommentRanges(code) match {
      case ParseFailure(err) => {
        vfail(
          ParseErrorHumanizer.humanize(
            FileCoordinateMap(Map()).add("my", Vector.empty, "0", code),
            FileCoordinate("my", Vector.empty, "0"),
            err))
      }
      case ParseSuccess(result) => result._1
    }
  }
  def compileProgram(code: String): FileP = {
    Parser.runParser(code) match {
      case ParseFailure(err) => {
        vfail(
          ParseErrorHumanizer.humanize(
            FileCoordinateMap(Map()).add("my", Vector.empty, "0", code),
            FileCoordinate("my", Vector.empty, "0"),
            err))
      }
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
        vfail("Couldn't parse!\n" + input.pos.longString + "\n" + msg);
      }
      case CombinatorParsers.Success(expr, rest) => {
        if (!rest.atEnd) {
          vfail("Couldn't parse all of the input. Remaining:\n" + code.slice(rest.offset, code.length))
        }
        expr
      }
    }
  }

  def compileForRest[T](parser: CombinatorParsers.Parser[T], code: String): String = {
    // The strip is in here because things inside the parser don't expect whitespace before and after
    CombinatorParsers.parse(parser, code.strip().toCharArray()) match {
      case CombinatorParsers.NoSuccess(msg, input) => {
        vfail("Couldn't parse!\n" + input.pos.longString + "\n" + msg);
      }
      case CombinatorParsers.Success(expr, rest) => {
        code.slice(rest.offset, code.length)
      }
    }
  }
}
