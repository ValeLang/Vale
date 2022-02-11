package net.verdagon.vale.parser

import net.verdagon.vale.parser.ast.FileP
import net.verdagon.vale.{Err, FileCoordinate, FileCoordinateMap, Ok, Result, vfail}

trait TestParseUtils {
//  def compileProgramWithComments(code: String): FileP = {
//    Parser.runParserForProgramAndCommentRanges(code) match {
//      case ParseFailure(err) => {
//        vfail(
//          ParseErrorHumanizer.humanize(
//            FileCoordinateMap(Map()).add("my", Vector.empty, "0", code),
//            FileCoordinate("my", Vector.empty, "0"),
//            err))
//      }
//      case ParseSuccess(result) => result._1
//    }
//  }
//
//  def compileProgram(code: String): FileP = {
//    Parser.runParser(code) match {
//      case ParseFailure(err) => {
//        vfail(
//          ParseErrorHumanizer.humanize(
//            FileCoordinateMap(Map()).add("my", Vector.empty, "0", code),
//            FileCoordinate("my", Vector.empty, "0"),
//            err))
//      }
//      case ParseSuccess(result) => result
//    }
//  }
//
//  def compileProgramForError(code: String): IParseError = {
//    Parser.runParser(code) match {
//      case ParseFailure(err) => err
//      case ParseSuccess(result) => vfail("Expected error, but actually parsed invalid program:\n" + result)
//    }
//  }

  def compileMaybe[T](parser: (ParsingIterator) => Result[Option[T], IParseError], untrimpedCode: String): T = {
    val code = untrimpedCode.trim()
    // The trim is in here because things inside the parser don't expect whitespace before and after
    val iter = ParsingIterator(code.trim(), 0)
    parser(iter) match {
      case Ok(None) => {
        vfail("Couldn't parse, not applicable!");
      }
      case Err(err) => {
        vfail("Couldn't parse!\n" + ParseErrorHumanizer.humanize(FileCoordinateMap.test(code), FileCoordinate.test, err))
      }
      case Ok(Some(result)) => {
        if (!iter.atEnd()) {
          vfail("Couldn't parse all of the input. Remaining:\n" + code.slice(iter.getPos(), code.length))
        }
        result
      }
    }
  }

  def compile[T](parser: (ParsingIterator) => Result[T, IParseError], untrimpedCode: String): T = {
    val code = untrimpedCode.trim()
    // The trim is in here because things inside the parser don't expect whitespace before and after
    val iter = ParsingIterator(code.trim(), 0)
    parser(iter) match {
      case Err(err) => {
        vfail("Couldn't parse!\n" + ParseErrorHumanizer.humanize(FileCoordinateMap.test(code), FileCoordinate.test, err))
      }
      case Ok(result) => {
        if (!iter.atEnd()) {
          vfail("Couldn't parse all of the input. Remaining:\n" + code.slice(iter.getPos(), code.length))
        }
        result
      }
    }
  }

  def compileForError[T](parser: (ParsingIterator) => Result[T, IParseError], untrimpedCode: String): IParseError = {
    val code = untrimpedCode.trim()
    // The trim is in here because things inside the parser don't expect whitespace before and after
    val iter = ParsingIterator(code.trim(), 0)
    parser(iter) match {
      case Err(err) => {
        err
      }
      case Ok(result) => {
        if (!iter.atEnd()) {
          vfail("Couldn't parse all of the input. Remaining:\n" + code.slice(iter.getPos(), code.length))
        }
        vfail("We expected parse to fail, but it succeeded:\n" + result)
      }
    }
  }
}
