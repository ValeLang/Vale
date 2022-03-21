package net.verdagon.vale.parser

import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.parser.ast.{FileP, IExpressionPE, ITopLevelThingP}
import net.verdagon.vale.{Err, FileCoordinate, FileCoordinateMap, Ok, Result, vassertSome, vfail}

trait TestParseUtils {
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

  def compileForRest[T](parser: (ParsingIterator) => Result[T, IParseError], untrimpedCode: String, expectedRest: String): Unit = {
    val code = untrimpedCode.trim()
    // The trim is in here because things inside the parser don't expect whitespace before and after
    val iter = ParsingIterator(code.trim(), 0)
    parser(iter) match {
      case Err(err) => {
        vfail("Couldn't parse!\n" + ParseErrorHumanizer.humanize(FileCoordinateMap.test(code), FileCoordinate.test, err))
      }
      case Ok(_) => {
        val rest = iter.code.slice(iter.position, iter.code.length)
        if (rest != expectedRest) {
          vfail("Couldn't parse all of the input. Remaining:\n" + code.slice(iter.getPos(), code.length))
        }
      }
    }
  }

  def compileExpression(code: String): IExpressionPE = {
    compile(
      makeExpressionParser()
        .parseExpression(_, StopBeforeCloseBrace), code)
  }

  def compileTopLevel(code: String): ITopLevelThingP = {
    vassertSome(compile(makeParser().parseTopLevelThing(_), code))
  }

  def compileExpressionForError(code: String): IParseError = {
    compileForError(
      makeExpressionParser()
        .parseBlockContents(_, StopBeforeCloseBrace), code)
  }

  def makeParser(): Parser = {
    new Parser(GlobalOptions(true, true, true, true))
  }

  def makeExpressionParser(): ExpressionParser = {
    new ExpressionParser(GlobalOptions(true, true, true, true))
  }
}
