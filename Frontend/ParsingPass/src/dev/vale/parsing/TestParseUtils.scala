package dev.vale.parsing

import dev.vale.lexing.{FailedParse, IParseError, Lexer, LexingIterator}
import dev.vale.{Err, FileCoordinate, FileCoordinateMap, IPackageResolver, Interner, Keywords, Ok, PackageCoordinate, PackageCoordinateMap, Result, SourceCodeUtils, U, vassertOne, vassertSome, vfail, vimpl}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.{FileP, IDenizenP, IExpressionPE, IRulexPR, ITemplexPT, PatternPP}
import dev.vale.parsing.templex.TemplexParser

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

  def compileExpressionForError(code: String): IParseError = {
    compileExpression(code).expectErr()
  }

  def makeParser(): Parser = {
    vimpl()
//    new Parser(GlobalOptions(true, true, true, true))
  }

  def makeExpressionParser(): ExpressionParser = {
    vimpl()
//    new ExpressionParser(GlobalOptions(true, true, true, true))
  }

  def compileFileInner(
    interner: Interner,
    keywords: Keywords,
    codeMap: FileCoordinateMap[String]):
  Result[FileP, FailedParse] = {
    val opts = GlobalOptions(true, true, true, true)
    val p = new Parser(interner, keywords, opts)
    ParseAndExplore.parseAndExploreAndCollect(
      interner,
      keywords,
      opts,
      p,
      Vector(PackageCoordinate.TEST_TLD(interner, keywords)),
      new IPackageResolver[Map[String, String]]() {
        override def resolve(packageCoord: PackageCoordinate): Option[Map[String, String]] = {
          // For testing the parser, we dont want it to fetch things with import statements
          Some(codeMap.resolve(packageCoord).getOrElse(Map()))
        }
      }) match {
      case Err(e) => Err(e)
      case Ok(x) => Ok(vassertOne(U.map[(String, FileP), FileP](x.buildArray(), _._2)))
    }
  }

  def compileFile(code: String): Result[FileP, FailedParse] = {
    val interner = new Interner()
    val keywords = new Keywords(interner)
    val codeMap = FileCoordinateMap.test(interner, Vector(code))
    compileFileInner(interner, keywords, codeMap)
  }

  def compileFileExpect(code: String): FileP = {
    val interner = new Interner()
    val keywords = new Keywords(interner)
    val codeMap = FileCoordinateMap.test(interner, Vector(code))
    compileFileInner(interner, keywords, codeMap) match {
      case Err(e) => vfail(ParseErrorHumanizer.humanizeFromMap(codeMap.fileCoordToContents.toMap, e.fileCoord, e.error))
      case Ok(x) => x
    }
  }

  def compileDenizens(code: String): Result[Vector[IDenizenP], FailedParse] = {
    compileFile(code) match {
      case Err(e) => Err(e)
      case Ok(x) => Ok(x.denizens)
    }
  }


  def compileDenizen(code: String): Result[IDenizenP, FailedParse] = {
    compileDenizens(code) match {
      case Err(e) => Err(e)
      case Ok(x) => Ok(vassertOne(x))
    }
  }

  def compileDenizenExpect(code: String): IDenizenP = {
    compileDenizen(code) match {
      case Err(FailedParse(code, fileCoord, error)) => {
        vfail(
          ParseErrorHumanizer.humanize(
            SourceCodeUtils.humanizeFile(fileCoord), code, error))
      }
      case Ok(x) => x
    }
  }

  def compileExpression(code: String): Result[IExpressionPE, IParseError] = {
    val opts = GlobalOptions(true, false, true, true)
    val interner = new Interner()
    val keywords = new Keywords(interner)
    val lexer = new Lexer(interner, keywords)
    val node =
      lexer.lexScramble(LexingIterator(code), false, false, false)
        .getOrDie()
    val parser = new Parser(interner, keywords, opts)
    parser.expressionParser.parseExpression(new ScrambleIterator(node), false)
  }

  def compileExpressionExpect(code: String): IExpressionPE = {
    compileExpression(code).getOrDie()
  }

  def compileBlockContents(code: String): Result[IExpressionPE, IParseError] = {
    val opts = GlobalOptions(true, false, true, true)
    val interner = new Interner()
    val keywords = new Keywords(interner)
    val lexer = new Lexer(interner, keywords)
    val iter = LexingIterator(code)
    val node =
      lexer.lexScramble(iter, false, false, false)
        .getOrDie()
    val parser = new Parser(interner, keywords, opts)
    parser.expressionParser.parseBlockContents(new ScrambleIterator(node), false)
  }

  def compileBlockContentsExpect(code: String): IExpressionPE = {
    compileBlockContents(code).getOrDie()
  }

  def compileStatement(code: String): Result[IExpressionPE, IParseError] = {
    val opts = GlobalOptions(true, false, true, true)
    val interner = new Interner()
    val keywords = new Keywords(interner)
    val lexer = new Lexer(interner, keywords)
    val node =
      lexer.lexScramble(LexingIterator(code), false, false, false) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }
    val parser = new Parser(interner, keywords, opts)
    parser.expressionParser.parseStatement(new ScrambleIterator(node), false)
  }

  def compileStatementExpect(code: String): IExpressionPE = {
    compileStatement(code).getOrDie()
  }

  def compilePattern(code: String): PatternPP = {
    val interner = new Interner()
    val keywords = new Keywords(interner)
    val lexer = new Lexer(interner, keywords)
    val node =
      lexer.lexScramble(LexingIterator(code), false, false, false)
        .getOrDie()
    val templexParser = new TemplexParser(interner, keywords)
    val exprP =
      new PatternParser(interner, keywords, templexParser)
        .parsePattern(new ScrambleIterator(node), node.range.begin, 0, false, false, false, None)
        .getOrDie()
    exprP
  }

  def compileTemplex(code: String): ITemplexPT = {
    val interner = new Interner()
    val keywords = new Keywords(interner)
    val node =
      new Lexer(interner, keywords)
        .lexScramble(LexingIterator(code), false, false, true)
        .getOrDie()
    val exprP =
        new TemplexParser(interner, keywords)
          .parseTemplex(new ScrambleIterator(node))
          .getOrDie()
    exprP
  }

  def compileRulex(code: String): IRulexPR = {
    val interner = new Interner()
    val keywords = new Keywords(interner)
    val node =
      new Lexer(interner, keywords)
        .lexScramble(LexingIterator(code), false, false, true)
        .getOrDie()
    val exprP =
          new TemplexParser(interner, keywords)
            .parseRule(new ScrambleIterator(node))
            .getOrDie()
    exprP
  }
}
