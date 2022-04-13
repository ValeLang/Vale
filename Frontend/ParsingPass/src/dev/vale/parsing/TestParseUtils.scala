package dev.vale.parsing

import dev.vale.lexing.{IParseError, Lexer, LexingIterator}
import dev.vale.{Err, FileCoordinate, FileCoordinateMap, IPackageResolver, Interner, Ok, PackageCoordinate, PackageCoordinateMap, Result, vassertOne, vassertSome, vfail, vimpl}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.{FileP, IDenizenP, IExpressionPE}

import scala.collection.immutable.Map

trait TestParseUtils {
  def compileMaybe[T](parser: (ScrambleIterator) => Result[Option[T], IParseError], untrimpedCode: String): T = {
    vimpl()
//    val interner = new Interner()
//    val code = untrimpedCode.trim()
//    // The trim is in here because things inside the parser don't expect whitespace before and after
//    val iter = new ScrambleIterator(code.trim(), 0)
//    parser(iter) match {
//      case Ok(None) => {
//        vfail("Couldn't parse, not applicable!");
//      }
//      case Err(err) => {
//        vfail(
//          "Couldn't parse!\n" +
//            ParseErrorHumanizer.humanize(
//              FileCoordinateMap.test(interner, code).fileCoordToContents.toMap,
//              FileCoordinate.test(interner),
//              err))
//      }
//      case Ok(Some(result)) => {
//        if (!iter.atEnd()) {
//          vfail("Couldn't parse all of the input. Remaining:\n" + code.slice(iter.getPos(), code.length))
//        }
//        result
//      }
//    }
  }

  def compile[T](parser: (LexingIterator) => Result[T, IParseError], untrimpedCode: String): T = {
    vimpl()
//    val interner = new Interner()
//    val code = untrimpedCode.trim()
//    // The trim is in here because things inside the parser don't expect whitespace before and after
//    val iter = ParsingIterator(code.trim(), 0)
//    parser(iter) match {
//      case Err(err) => {
//        vfail(
//          "Couldn't parse!\n" +
//            ParseErrorHumanizer.humanize(
//              FileCoordinateMap.test(interner, code).fileCoordToContents.toMap,
//              FileCoordinate.test(interner),
//              err))
//      }
//      case Ok(result) => {
//        if (!iter.atEnd()) {
//          vfail("Couldn't parse all of the input. Remaining:\n" + code.slice(iter.getPos(), code.length))
//        }
//        result
//      }
//    }
  }

  def compileForError[T](parser: (ScrambleIterator) => Result[T, IParseError], untrimpedCode: String): IParseError = {
    vimpl()
//    val code = untrimpedCode.trim()
//    // The trim is in here because things inside the parser don't expect whitespace before and after
//    val iter = ParsingIterator(code.trim(), 0)
//    parser(iter) match {
//      case Err(err) => {
//        err
//      }
//      case Ok(result) => {
//        if (!iter.atEnd()) {
//          vfail("Couldn't parse all of the input. Remaining:\n" + code.slice(iter.getPos(), code.length))
//        }
//        vfail("We expected parse to fail, but it succeeded:\n" + result)
//      }
//    }
  }

  def compileForRest[T](parser: (ScrambleIterator) => Result[T, IParseError], untrimpedCode: String, expectedRest: String): Unit = {
    vimpl()
//    val interner = new Interner()
//    val code = untrimpedCode.trim()
//    // The trim is in here because things inside the parser don't expect whitespace before and after
//    val iter = ParsingIterator(code.trim(), 0)
//    parser(iter) match {
//      case Err(err) => {
//        vfail(
//          "Couldn't parse!\n" +
//            ParseErrorHumanizer.humanize(
//              FileCoordinateMap.test(interner, code).fileCoordToContents.toMap,
//              FileCoordinate.test(interner),
//              err))
//      }
//      case Ok(_) => {
//        val rest = iter.code.slice(iter.position, iter.code.length)
//        if (rest != expectedRest) {
//          vfail("Couldn't parse all of the input. Remaining:\n" + code.slice(iter.getPos(), code.length))
//        }
//      }
//    }
  }

  def compileExpression(code: String): IExpressionPE = {
    vimpl()
//    compile(
//      makeExpressionParser()
//        .parseExpression(_, StopBeforeCloseBrace), code)
  }

  def compileTopLevel(code: String): IDenizenP = {
    vimpl()
//    vassertSome(compile(makeParser().parseDenizen(_), code))
  }

  def compileExpressionForError(code: String): IParseError = {
    vimpl()
//    compileForError(
//      makeExpressionParser()
//        .parseBlockContents(_, StopBeforeCloseBrace), code)
  }

  def makeParser(): Parser = {
    vimpl()
//    new Parser(GlobalOptions(true, true, true, true))
  }

  def makeExpressionParser(): ExpressionParser = {
    vimpl()
//    new ExpressionParser(GlobalOptions(true, true, true, true))
  }

  def compileDenizen(code: String, interner: Interner = new Interner()): IDenizenP = {
    val l = new Lexer(interner)
    val p = new Parser(interner, GlobalOptions(true, true, true, true))
    compile(
      x => p.parseDenizen(l.lexDenizen(x).getOrDie()),
      code)
  }

  def compileFile(code: String, interner: Interner = new Interner()): FileP = {
    val opts = GlobalOptions(true, true, true, true)
    val p = new Parser(interner, opts)
    val codeMap = FileCoordinateMap.test(interner, Vector(code))
    vassertOne(
      ParseAndExplore.parseAndExplore[IDenizenP, FileP](
        interner,
        opts,
        p,
        Array(PackageCoordinate.TEST_TLD(interner)),
        new IPackageResolver[Map[String, String]]() {
          override def resolve(packageCoord: PackageCoordinate): Option[Map[String, String]] = {
            // For testing the parser, we dont want it to fetch things with import statements
            Some(codeMap.resolve(packageCoord).getOrElse(Map("" -> "")))
          }
        },
        (fileCoord, code, imports, denizenP) => denizenP,
        (fileCoord, code, imports, denizensP) => {
          FileP(denizensP.buildArray().toVector)
        }).getOrDie().buildArray())
//
//    compile(
//      x => p.parseDenizen(l.lexDenizen(x).getOrDie()),
//      code)
  }
}
