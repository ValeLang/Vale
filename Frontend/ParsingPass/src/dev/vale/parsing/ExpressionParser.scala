package dev.vale.parsing

import dev.vale.options.GlobalOptions
import dev.vale.parsing.templex.TemplexParser
import ExpressionParser.{MAX_PRECEDENCE, MIN_PRECEDENCE}
import dev.vale.parsing.ast._
import dev.vale.{Accumulator, Err, Interner, Keywords, Ok, Profiler, Result, StrI, U, parsing, vassert, vcurious, vfail, vimpl, vwat}
import dev.vale.parsing.ast._
import dev.vale.lexing._

import scala.collection.immutable.{List, Map}
import scala.collection.mutable
import scala.util.matching.Regex


sealed trait IStopBefore
case object StopBeforeComma extends IStopBefore
case object StopBeforeFileEnd extends IStopBefore
case object StopBeforeCloseBrace extends IStopBefore
case object StopBeforeCloseParen extends IStopBefore
case object StopBeforeEquals extends IStopBefore
case object StopBeforeCloseSquare extends IStopBefore
case object StopBeforeCloseChevron extends IStopBefore
// Such as after the if's condition or the foreach's iterable.
case object StopBeforeOpenBrace extends IStopBefore

sealed trait IExpressionElement
case class DataElement(expr: IExpressionPE) extends IExpressionElement
case class BinaryCallElement(symbol: NameP, precedence: Int) extends IExpressionElement

object ExpressionParser {
  val MAX_PRECEDENCE = 6
  val MIN_PRECEDENCE = 1
}

class ScrambleIterator(
    val scramble: ScrambleLE,
    var index: Int,
    var end: Int) {
  def this(scramble: ScrambleLE) {
    this(scramble, 0, scramble.elements.length)
  }

  assert(end <= scramble.elements.length)

  def range: RangeL = {
    vassert(index < end)
    RangeL(
      scramble.elements(index).range.begin,
      scramble.elements(end - 1).range.end)
  }

  def getPos(): Int = {
    if (index >= end) {
      scramble.range.end
    } else {
      scramble.elements(index).range.begin
    }
  }
  def getPrevEndPos(): Int = {
    if (index == 0) {
      scramble.range.begin
    } else {
      scramble.elements(index - 1).range.end
    }
  }
  def skipTo(that: ScrambleIterator): Unit = {
    index = that.index
  }
  def stop(): Unit = {
    index = end
  }
  override def clone(): ScrambleIterator = new ScrambleIterator(scramble, index, end)
  def hasNext: Boolean = index < end
  def peek(): Option[INodeLE] = {
    if (index >= end) None
    else Some(scramble.elements(index))
  }
  def take(): Option[INodeLE] = {
    if (index >= end) None
    else Some(advance())
  }
  // This is an Vector[Option[INodeLE]] instead of an Vector[INodeLE]
  // because we like to be able to ignore the tail end of something like
  // case Vector(Some(whatever), _)
  def peek(n: Int): Vector[Option[INodeLE]] = {
    U.mapRange[Option[INodeLE]](
      index,
      index + n,
      i => {
        if (i < end) Some(scramble.elements(i))
        else None
      })
  }
  def peek2(): (Option[INodeLE], Option[INodeLE]) = {
    (
      (if (index + 0 < end) Some(scramble.elements(index + 0)) else None),
      (if (index + 1 < end) Some(scramble.elements(index + 1)) else None))
  }
  def peek3(): (Option[INodeLE], Option[INodeLE], Option[INodeLE]) = {
    (
      (if (index + 0 < end) Some(scramble.elements(index + 0)) else None),
      (if (index + 1 < end) Some(scramble.elements(index + 1)) else None),
      (if (index + 2 < end) Some(scramble.elements(index + 2)) else None))
  }
  def peekWord(word: StrI): Boolean = {
    peek() match {
      case Some(WordLE(_, s)) => s == word
      case _ => false
    }
  }
  def advance(): INodeLE = {
    vassert(hasNext)
    val result = scramble.elements(index)
    index = index + 1
    result
  }
  def trySkip[R](f: PartialFunction[INodeLE, R]): Option[INodeLE] = {
    peek().filter(f.isDefinedAt)
  }

  def trySkipSymbol(symbol: Char): Boolean = {
    peek() match {
      case Some(SymbolLE(_, s)) if s == symbol => {
        advance()
        true
      }
      case _ => false
    }
  }
  def trySkipSymbols(symbols: Vector[Char]): Boolean = {
    if (index + symbols.length >= end) {
      return false
    }
    var i = 0
    while (i < symbols.length) {
      scramble.elements(index + i) match {
        case SymbolLE(_, s) if s == symbols(i) =>
        case _ => return false
      }
      i = i + 1
    }
    index = index + symbols.length
    true
  }
  def nextWord(): Option[WordLE] = {
    peek() match {
      case Some(w @ WordLE(_, _)) => {
        advance()
        Some(w)
      }
      case _ => None
    }
  }
  def expectWord(str: StrI): Unit = {
    val found = trySkipWord(str).nonEmpty
    vassert(found)
  }
  def trySkipWord(str: StrI): Option[RangeL] = {
    peek() match {
      case Some(WordLE(range, s)) if s == str => {
        advance()
        Some(range)
      }
      case _ => None
    }
  }
//  def exists(func: scala.Function1[INodeLE, Boolean]): Boolean = {
//    U.exists(scramble.elements, func, index, end)
//  }
  def findIndexWhere(func: scala.Function1[INodeLE, Boolean]): Option[Int] = {
    U.findIndexWhereFromUntil(scramble.elements, func, index, end)
  }


  // We use this splitOnSymbol method for things like comma-separated
  // lists and things.
  // TODO: Soon, it will fall apart on certain cases. For example,
  // in a struct, we can have:
  //   struct Moo {
  //     x int;
  //     func bork() { }
  //     func zork() { }
  //   }
  // so it doesn't make much sense to split on semicolon.
  // Instead, we should make the iterator go until it finds a certain symbol.
  //
  // includeEmptyTrailingSection means that if we end with a needle,
  // we'll still return an empty iterator for the end.
  def splitOnSymbol(needle: Char, includeEmptyTrailing: Boolean): Vector[ScrambleIterator] = {
    val iters = new Accumulator[ScrambleIterator]()
    var start = index
    var i = start
    while (i < end) {
      scramble.elements(i) match {
        case SymbolLE(_, c) if c == needle => {
          iters.add(new ScrambleIterator(scramble, start, i))
          start = i + 1
          i = i + 1 // Note the 2 here
        }
        case _ => {
          i = i + 1
        }
      }
    }
    if (start < end) {
      // If we get in here, the scramble didnt end in this needle.
      // So, just add this as the last result.
      iters.add(new ScrambleIterator(scramble, start, end))
    } else if (start == end) {
      // If start == end, then we ended in a needle.
      if (includeEmptyTrailing) {
        iters.add(new ScrambleIterator(scramble, start, end))
      }
    }

    iters.buildArray()
  }
}

class ExpressionParser(interner: Interner, keywords: Keywords, opts: GlobalOptions, patternParser: PatternParser, templexParser: TemplexParser) {
//  val stringParser = new StringParser(this)

  private def parseWhile(iter: ScrambleIterator): Result[Option[WhilePE], IParseError] = {
    val whileBegin = iter.getPos()

    if (iter.trySkipWord(keywords.whiile).isEmpty) {
      return Ok(None)
    }

    val condition =
      parseBlockContents(iter, true) match {
        case Ok(result) => result
        case Err(cpe) => return Err(cpe)
      }

    val body =
      iter.peek() match {
        case Some(CurliedLE(range, contents)) => {
          iter.advance()
          parseBlockContents(new ScrambleIterator(contents), false) match {
            case Ok(result) => result
            case Err(cpe) => return Err(cpe)
          }
        }
        case _ => return Err(BadStartOfWhileBody(iter.getPos()))
      }

    Ok(
      Some(
        ast.WhilePE(
          RangeL(whileBegin, iter.getPrevEndPos()),
          condition,
          BlockPE(body.range, None, body))))
  }

  private def parseForeach(
    originalIter: ScrambleIterator):
  Result[Option[EachPE], IParseError] = {
    val eachBegin = originalIter.getPos()

    val tentativeIter = originalIter.clone()

    if (tentativeIter.trySkipWord(keywords.parallel).nonEmpty) {
      // do nothing for now
    }

    if (tentativeIter.trySkipWord(keywords.foreeach).isEmpty) {
      return Ok(None)
    }
    originalIter.skipTo(tentativeIter)
    val iter = originalIter

    val (inRange, pattern) =
      ParseUtils.trySkipPastKeywordWhile(
        iter,
        keywords.in,
        it => {
          it.peek() match {
            // Stop if we hit the end or a semicolon or a curly brace
            case None => false
            case Some(SymbolLE(_, ';')) => false
            case Some(CurliedLE(_, _)) => false
            // Continue for anything else
            case Some(_) => true
          }
        }) match {
        case None => return Err(BadForeachInError(iter.getPos()))
        case Some((in, patternIter)) => {
          patternParser.parsePattern(patternIter, 0, false, false, false) match {
            case Err(cpe) => return Err(cpe)
            case Ok(result) => (in.range, result)
          }
        }
      }

    val iterableExpr =
      parseBlockContents(iter, true) match {
        case Err(err) => return Err(err)
        case Ok(expression) => expression
      }

    val bodyBegin = iter.getPos()

    val body =
      iter.peek() match {
        case Some(CurliedLE(_, contents)) => {
          iter.advance()
          parseBlockContents(new ScrambleIterator(contents), false) match {
            case Err(cpe) => return Err(cpe)
            case Ok(result) => result
          }
        }
        case _ => return Err(BadStartOfWhileBody(iter.getPos()))
      }

    Ok(
      Some(
        EachPE(
          RangeL(eachBegin, iter.getPrevEndPos()),
          pattern,
          inRange,
          iterableExpr,
          ast.BlockPE(RangeL(bodyBegin, iter.getPrevEndPos()), None, body))))
  }

  private def parseIfLadder(iter: ScrambleIterator): Result[Option[IfPE], IParseError] = {
    val ifLadderBegin = iter.getPos()

    iter.peek() match {
      case Some(WordLE(_, str)) if str == keywords.iff =>
      case _ => return Ok(None)
    }

    val rootIf =
      parseIfPart(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    val ifElses = mutable.MutableList[(IExpressionPE, BlockPE)]()
    while (iter.peek2() match {
      case (Some(WordLE(_, elsse)), Some(WordLE(_, iff)))
        if elsse == keywords.elsse && iff == keywords.iff => true
      case _ => false
    }) {
      iter.advance() // Skip the else

      ifElses += (
        parseIfPart(iter) match {
          case Err(e) => return Err(e)
          case Ok(x) => x
        })
    }

    val elseBegin = iter.getPos()
    val maybeElseBlock =
      if (iter.trySkipWord(keywords.elsse).nonEmpty) {
        val body =
          iter.peek() match {
            case Some(b @ CurliedLE(_, _)) => iter.advance(); b
            case _ => return Err(BadStartOfElseBody(iter.getPos()))
          }

        val elseBody =
          parseBlockContents(new ScrambleIterator(body.contents), false) match {
            case Ok(result) => result
            case Err(cpe) => return Err(cpe)
          }

        val elseEnd = iter.getPos()
        Some(ast.BlockPE(RangeL(elseBegin, elseEnd), None, elseBody))
      } else {
        None
      }

    val finalElse: BlockPE =
      maybeElseBlock match {
        case None => {
          BlockPE(
            RangeL(iter.getPrevEndPos(), iter.getPrevEndPos()),
            None,
            VoidPE(RangeL(iter.getPrevEndPos(), iter.getPrevEndPos())))
        }
        case Some(block) => block
      }
    val rootElseBlock =
      ifElses.foldRight(finalElse)({
        case ((condBlock, thenBlock), elseBlock) => {
          // We don't check that both branches produce because of cases like:
          //   if blah {
          //     return 3;
          //   } else {
          //     6
          //   }
          BlockPE(
            RangeL(condBlock.range.begin, thenBlock.range.end),
            None,
            IfPE(
              RangeL(condBlock.range.begin, thenBlock.range.end),
              condBlock, thenBlock, elseBlock))
        }
      })
    val (rootConditionLambda, rootThenLambda) = rootIf
    // We don't check that both branches produce because of cases like:
    //   if blah {
    //     return 3;
    //   } else {
    //     6
    //   }
    Ok(
      Some(
        ast.IfPE(
          RangeL(ifLadderBegin, iter.getPrevEndPos()),
          rootConditionLambda,
          rootThenLambda,
          rootElseBlock)))
  }

  private def nextIsSetExpr(iter: ScrambleIterator): Boolean = {
    iter.peek2() match {
      case (Some(WordLE(setRange, set)), Some(other))
        if set == keywords.set && setRange.end < other.range.begin => {
        // Then there's indeed a space after the set. Continue!
        true
      }
      case _ => false
    }
  }

  private def parseMutExpr(
    iter: ScrambleIterator,
    stopOnCurlied: Boolean):
  Result[Option[MutatePE], IParseError] = {

    val mutateBegin = iter.getPos()
    if (!nextIsSetExpr(iter)) {
      return Ok(None)
    }
    iter.advance()

    val mutateeExpr =
      ParseUtils.trySkipPastEqualsWhile(iter, scoutingIter => {
        scoutingIter.peek() match {
          case None => false
          case Some(SymbolLE(_, ';')) => false
          case _ => true
        }
      }) match {
        case None => return Err(BadMutateEqualsError(iter.getPos()))
        case Some(destIter) => {
          parseExpression(destIter, stopOnCurlied) match {
            case Err(err) => return Err(err)
            case Ok(expression) => expression
          }
        }
      }

    val sourceExpr =
      parseExpression(iter, stopOnCurlied) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    Ok(Some(MutatePE(RangeL(mutateBegin, iter.getPrevEndPos()), mutateeExpr, sourceExpr)))
  }

  private def parseLet(
    patternIter: ScrambleIterator,
    iter: ScrambleIterator,
    stopOnCurlied: Boolean):
  Result[LetPE, IParseError] = {
    val pattern =
      patternParser.parsePattern(patternIter, 0, false, false, false) match {
        case Ok(result) => result
        case Err(e) => return Err(e)
      }

    pattern.destination match {
      case Some(DestinationLocalP(LocalNameDeclarationP(name), None)) => vassert(name.str != keywords.set)
      case _ =>
    }

    val sourceExpr =
      parseExpression(iter, stopOnCurlied) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }
    Ok(LetPE(RangeL(pattern.range.begin, sourceExpr.range.end), pattern, sourceExpr))
  }

  private def parseIfPart(
    iter: ScrambleIterator):
  Result[(IExpressionPE, BlockPE), IParseError] = {
    if (iter.trySkipWord(keywords.iff).isEmpty) {
      vwat()
    }

    val conditionPE =
      parseBlockContents(iter, true) match {
        case Err(err) => return Err(err)
        case Ok(expression) => expression
      }

    val body =
      iter.peek() match {
        case Some(CurliedLE(_, contents)) => {
          iter.advance()
          parseBlockContents(new ScrambleIterator(contents), false) match {
            case Ok(result) => result
            case Err(cpe) => return Err(cpe)
          }
        }
        case None => return Err(BadStartOfIfBody(iter.getPos()))
      }

    Ok(
      (
        conditionPE,
        ast.BlockPE(body.range, None, body)))
  }

  def parseBlock(blockL: CurliedLE): Result[IExpressionPE, IParseError] = {
    parseBlockContents(new ScrambleIterator(blockL.contents), false)
  }

  def parseBlockContents(iter: ScrambleIterator, stopOnCurlied: Boolean): Result[IExpressionPE, IParseError] = {
    val statementsP = new Accumulator[IExpressionPE]()

    //    val endedInSemicolon =
    //      if (iter.scramble.elements.nonEmpty) {
    //        iter.scramble.elements(iter.end - 1) match {
    //          case SymbolLE(range, ';') => true
    //          case _ => false
    //        }
    //      } else {
    //        false
    //      }

    while (iter.peek() match {
      case None => false
      case Some(CurliedLE(range, contents)) if stopOnCurlied => false
      case Some(_) => {
        val statementP =
          parseStatement(iter, stopOnCurlied) match {
            case Err(error) => return Err(error)
            case Ok(s) => s
          }
        statementsP.add(statementP)
        true
      }
    }) {}

    // If we just ate a semicolon, but there's nothing after it, then add a void.
    if (iter.hasNext) {
      iter.peek() match {
        case Some(SymbolLE(_, ')')) => vcurious()
        case Some(SymbolLE(_, ']')) => vcurious()
        case _ =>
      }
    } else {
      if (iter.scramble.elements.nonEmpty) {
        iter.scramble.elements(iter.index - 1) match {
          case SymbolLE(range, ';') => {
            statementsP.add(VoidPE(RangeL(range.end, range.end)))
          }
          case _ =>
        }
      }
    }

    statementsP.size match {
      case 0 => Ok(VoidPE(RangeL(iter.getPos(), iter.getPos())))
      case 1 => Ok(statementsP.head)
      case _ => Ok(ConsecutorPE(statementsP.buildArray().toVector))
    }
  }

  private def parseLoneBlock(
    originalIter: ScrambleIterator):
  Result[Option[IExpressionPE], IParseError] = {
    val tentativeIter = originalIter.clone()

    // The pure/unsafe is a hack to get syntax highlighting work for
    // the future pure block feature.
    tentativeIter.trySkipWord(keywords.unsafe)
    tentativeIter.trySkipWord(keywords.pure)

    if (tentativeIter.trySkipWord(keywords.block).isEmpty) {
      return Ok(None)
    }

    originalIter.skipTo(tentativeIter)
    val iter = originalIter

    val begin = iter.getPos()

    val contents =
      iter.peek() match {
        case Some(CurliedLE(_, contents)) => {
          iter.advance()
          parseBlockContents(new ScrambleIterator(contents), false) match {
            case Err(error) => return Err(error)
            case Ok(result) => result
          }
        }
        case None => {
          return Err(BadStartOfBlock(iter.getPos()))
        }
      }

    Ok(Some(ast.BlockPE(RangeL(begin, iter.getPrevEndPos()), None, contents)))
  }

  private def parseDestruct(
    iter: ScrambleIterator,
    stopOnCurlied: Boolean):
  Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()
    if (iter.trySkipWord(keywords.destruct).isEmpty) {
      return Ok(None)
    }

    val innerExpr =
      parseExpression(iter, stopOnCurlied) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    if (!iter.trySkipSymbol(';')) {
      return Err(BadExpressionEnd(iter.getPos()))
    }

    Ok(Some(DestructPE(RangeL(begin, iter.getPrevEndPos()), innerExpr)))
  }

  private def parseUnlet(
    iter: ScrambleIterator):
  Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()
    if (iter.trySkipWord(keywords.unlet).isEmpty) {
      return Ok(None)
    }
    val local =
      iter.nextWord() match {
        case None => return Err(BadLocalNameInUnlet(iter.getPos()))
        case Some(WordLE(range, str)) => LookupNameP(NameP(range, str))
      }
    Ok(Some(UnletPE(RangeL(begin, iter.getPrevEndPos()), local)))
  }

  private def parseReturn(
    iter: ScrambleIterator,
      stopOnCurlied: Boolean):
  Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()
    if (iter.trySkipWord(keywords.retuurn).isEmpty) {
      return Ok(None)
    }

    val innerExpr =
      parseExpression(iter, stopOnCurlied) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    if (!iter.trySkipSymbol(';')) {
      return Err(BadExpressionEnd(iter.getPos()))
    }

    Ok(Some(ReturnPE(RangeL(begin, iter.getPrevEndPos()), innerExpr)))
  }

  private def parseBreak(
    iter: ScrambleIterator):
  Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()
    if (iter.trySkipWord(keywords.break).isEmpty) {
      return Ok(None)
    }
    if (!iter.trySkipSymbol(';')) {
      return Err(BadExpressionEnd(iter.getPos()))
    }
    Ok(Some(BreakPE(RangeL(begin, iter.getPrevEndPos()))))
  }

  private[parsing] def parseStatement(
    iter: ScrambleIterator,
    stopOnCurlied: Boolean):
  Result[IExpressionPE, IParseError] = {
    if (!iter.hasNext) {
      return Err(BadExpressionBegin(iter.getPos()))
    }

    parseWhile(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }
    parseIfLadder(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }
    parseForeach(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseLoneBlock(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseBreak(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseReturn(iter, stopOnCurlied) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseDestruct(iter, stopOnCurlied) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    vassert(iter.hasNext)

    val letOrLoneExpr =
      if (nextIsSetExpr(iter)) {
        parseMutExpr(iter, stopOnCurlied) match {
          case Err(e) => return Err(e)
          case Ok(None) => vwat()
          case Ok(Some(x)) => x
        }
      } else {
        ParseUtils.trySkipPastEqualsWhile(iter, scoutingIter => {
          scoutingIter.peek() match {
            case None => false
            case Some(CurliedLE(range, contents)) if stopOnCurlied => false
            case Some(SymbolLE(_, ';')) => false
            case _ => true
          }
        }) match {
          case Some(destIter) => {
            parseLet(destIter, iter, stopOnCurlied) match {
              case Err(e) => return Err(e)
              case Ok(x) => x
            }
          }
          case None => {
            parseExpression(iter, stopOnCurlied) match {
              case Err(e) => return Err(e)
              case Ok(x) => x
            }
          }
        }
      }
    iter.peek() match {
      case None => // okay, hit the end, continue
      case Some(CurliedLE(range, contents)) if stopOnCurlied => // okay, hit the end, continue
      case Some(SymbolLE(range, ';')) => {
        iter.advance() // consume it to end the statement.
        // continue
      }
      case _ => return Err(BadExpressionEnd(iter.getPos()))
    }
    Ok(letOrLoneExpr)
  }

  def getPrecedence(str: StrI): Int = {
    if (str == keywords.DOT_DOT) 6
    else if (str == keywords.asterisk || str == keywords.slash) 5
    else if (str == keywords.plus || str == keywords.minus) 4
    // _ => 3 Everything else is 3, see end case
    else if (str == keywords.asterisk || str == keywords.slash) 5
    else if (str == keywords.spaceship || str == keywords.lessEquals ||
      str == keywords.less || str == keywords.greaterEquals ||
      str == keywords.greater || str == keywords.tripleEquals ||
      str == keywords.doubleEquals || str == keywords.notEquals) 2
    else if (str == keywords.and || str == keywords.or) 1
    else 3 // This is so we can have 3 mod 2 == 1
  }

  def parseExpression(iter: ScrambleIterator, stopOnCurlied: Boolean): Result[IExpressionPE, IParseError] = {
    Profiler.frame(() => {
      if (!iter.hasNext) {
        return Err(BadExpressionBegin(iter.getPos()))
      }

      val elements = mutable.ArrayBuffer[IExpressionElement]()

      while ({
        val subExpr =
          parseExpressionDataElement(iter, stopOnCurlied) match {
            case Err(error) => return Err(error)
            case Ok(x) => x
          }
        elements += parsing.DataElement(subExpr)

        if (atExpressionEnd(iter, stopOnCurlied)) {
          false
        } else {
          if (subExpr.range.end == iter.getPos()) {
            return Err(NeedWhitespaceAroundBinaryOperator(iter.getPos()))
          }

          parseBinaryCall(iter) match {
            case Err(error) => return Err(error)
            case Ok(None) => false
            case Ok(Some(symbol)) => {
              vassert(MIN_PRECEDENCE == 1)
              vassert(MAX_PRECEDENCE == 6)
              val precedence = getPrecedence(symbol.str)
              elements += parsing.BinaryCallElement(symbol, precedence)


              iter.peek() match {
                case None => return new Err(BadExpressionEnd(iter.getPos()))
                case Some(node) => {
                  if (symbol.range.end == node.range.begin) {
                    return Err(NeedWhitespaceAroundBinaryOperator(iter.getPos()))
                  }
                }
              }
              true
            }
          }
        }
      }) {}

      val (exprPE, _) =
        descramble(elements.toVector, 0, elements.size - 1, MIN_PRECEDENCE)
      Ok(exprPE)
    })
  }

  def parseLookup(iter: ScrambleIterator): Option[IExpressionPE] = {
    val begin = iter.getPos()
    iter.peek3() match {
      case (Some(SymbolLE(_, '<')), Some(SymbolLE(_, '=')), Some(SymbolLE(_, '>'))) => {
        iter.advance()
        iter.advance()
        iter.advance()
        Some(
          LookupPE(
            LookupNameP(NameP(RangeL(begin, iter.getPrevEndPos()), keywords.spaceship)),
            None))
      }
      case (Some(SymbolLE(range1, c1 @ ('=' | '>' | '<' | '!'))), Some(SymbolLE(range2, c2 @ '=')), _) => {
        iter.advance()
        iter.advance()
        Some(
          LookupPE(
            LookupNameP(NameP(RangeL(range1.begin, range2.end), interner.intern(StrI(c1.toString + c2)))),
            None))
      }
      case (Some(SymbolLE(range, c)), _, _) => {
        iter.advance()
        Some(
          LookupPE(
            LookupNameP(NameP(range, interner.intern(StrI(c.toString)))),
            None))
      }
      case (Some(WordLE(range, str)), _, _) => {
        iter.advance()
        Some(
          LookupPE(
            LookupNameP(NameP(range, str)),
            None))
      }
      case _ => None
    }
//    Parser.parseFunctionOrLocalOrMemberName(iter) match {
//      case Some(name) => Some(LookupPE(LookupNameP(name), None))
//      case None => None
//    }
  }

  def parseBoolean(iter: ScrambleIterator): Option[IExpressionPE] = {
    val start = iter.getPos()
    iter.trySkipWord(keywords.truue) match {
      case Some(range) => return Some(ConstantBoolPE(range, true))
      case _ =>
    }
    iter.trySkipWord(keywords.faalse) match {
      case Some(range) => return Some(ConstantBoolPE(range, false))
      case _ =>
    }
    return None
  }

  // Note that this can consume an unbounded number of tokens, for example if
  // we encounter a set expression.
  def parseAtom(iter: ScrambleIterator, stopOnCurlied: Boolean): Result[IExpressionPE, IParseError] = {
    vassert(iter.hasNext)
    val begin = iter.getPos()

    // See BRCOBS
    if (iter.trySkipWord(keywords.break).nonEmpty) {
      return Err(CantUseBreakInExpression(iter.getPos()))
    }
    // See BRCOBS
    if (iter.trySkipWord(keywords.retuurn).nonEmpty) {
      return Err(CantUseReturnInExpression(iter.getPos()))
    }
    if (iter.trySkipWord(keywords.whiile).nonEmpty) {
      return Err(CantUseWhileInExpression(iter.getPos()))
    }
    iter.trySkipWord(keywords.UNDERSCORE) match {
      case Some(range) => return Ok(MagicParamLookupPE(range))
      case _ =>
    }
    parseForeach(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseMutExpr(iter, stopOnCurlied) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    iter.peek() match {
      case Some(ParsedIntegerLE(range, num, bits)) => {
        iter.advance()
        return Ok(ConstantIntPE(range, num, bits))
      }
      case Some(ParsedDoubleLE(range, num, bits)) => {
        iter.advance()
        return Ok(ConstantFloatPE(range, num))
      }
      case Some(StringLE(range, Vector(StringPartLiteral(_, s)))) => {
        iter.advance()
        return Ok(ConstantStrPE(range, s))
      }
      case Some(StringLE(range, partsL)) => {
        iter.advance()
        val partsP =
          U.map[StringPart, IExpressionPE](partsL, {
            case StringPartLiteral(range, s) => ConstantStrPE(range, s)
            case StringPartExpr(scramble) => {
              parseExpression(new ScrambleIterator(scramble), false) match {
                case Err(e) => return Err(e)
                case Ok(x) => x
              }
            }
          })
        return Ok(StrInterpolatePE(range, partsP.toVector))
      }
      case _ =>
    }
    parseBoolean(iter) match {
      case Some(e) => return Ok(e)
      case None =>
    }
    parseArray(iter) match {
      case Err(err) => return Err(err)
      case Ok(Some(e)) => return Ok(e)
      case Ok(None) =>
    }
    parseLambda(iter) match {
      case Err(err) => return Err(err)
      case Ok(Some(e)) => return Ok(e)
      case Ok(None) =>
    }
    parseLookup(iter) match {
      case Some(e) => return Ok(e)
      case None =>
    }
    parseTupleOrSubExpression(iter) match {
      case Err(err) => return Err(err)
      case Ok(Some(e)) => {
        return Ok(e)
      }
      case Ok(None) =>
    }
    return Err(BadExpressionBegin(iter.getPos()))
  }

//  def parseNumberExpr(originalIter: ScrambleIterator): Result[Option[IExpressionPE], IParseError] = {
//    val tentativeIter = originalIter.clone()
//
//    val begin = tentativeIter.getPos()
//
//    val isNegative =
//      tentativeIter.peek(2) match {
//        case Vector(SymbolLE(range, '-'), IntLE(intRange, _, _)) => {
//          // Only consider it a negative if it's right next to the next thing
//          if (range.end != intRange.begin) {
//            return Ok(None)
//          }
//          tentativeIter.advance()
//          true
//        }
//        case Vector(IntLE(_, _, _), _) => false
//        case _ => return Ok(None)
//      }
//    val integer =
//      tentativeIter.advance() match {
//        case IntLE(_, innt, _) => innt
//        case _ => return Ok(None)
//      }
//    originalIter.skipTo(tentativeIter)
//
//    if (tentativeIter.trySkipSymbol('.')) {
//      val mantissaPos = tentativeIter.getPos()
//      val mantissa =
//        tentativeIter.advance() match {
//          case IntLE(range, innt, numDigits) => innt.toDouble / numDigits
//          case _ => return Err(BadMantissa(mantissaPos))
//        }
//      val double = (if (isNegative) -1 else 1) * (integer + mantissa)
//      originalIter.skipTo(tentativeIter)
//      Ok(ConstantFloatPE(RangeL(begin, tentativeIter.getPos()), double))
//    } else {
//      if (tentativeIter.trySkipSymbol('i'))
//
//      originalIter.skipTo(tentativeIter)
//      Ok(ConstantIntPE(RangeL(begin, tentativeIter.getPos()), integer, bits))
//    }
//
//    Parser.parseNumber(originalIter) match {
//      case Ok(Some(ParsedInteger(range, int, bits))) => Ok(Some(ConstantIntPE(range, int, bits)))
//      case Ok(Some(ParsedDouble(range, int, bits))) => Ok(Some(ConstantFloatPE(range, int)))
//      case Ok(None) => Ok(None)
//      case Err(e) => Err(e)
//    }
//  }

  def parseSpreeStep(spreeBegin: Int, iter: ScrambleIterator, exprSoFar: IExpressionPE, stopOnCurlied: Boolean):
  Result[Option[IExpressionPE], IParseError] = {
    val operatorBegin = iter.getPos()

    if (iter.trySkipSymbol('&')) {
      val rangePE = AugmentPE(RangeL(spreeBegin, iter.getPrevEndPos()), BorrowP, exprSoFar)
      return Ok(Some(rangePE))
    }

    parseTemplateLookup(iter, exprSoFar) match {
      case Err(e) => return Err(e)
      case Ok(Some(call)) => return Ok(Some(call))
      case Ok(None) =>
    }

    parseFunctionCall(iter, spreeBegin, exprSoFar) match {
      case Err(e) => return Err(e)
      case Ok(Some(call)) => return Ok(Some(call))
      case Ok(None) =>
    }

    parseBracePack(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(args)) => {
        return Ok(
          Some(
            BraceCallPE(
              RangeL(spreeBegin, iter.getPrevEndPos()),
              RangeL(operatorBegin, iter.getPrevEndPos()),
              exprSoFar,
              args,
              false)))
      }
      case Ok(None) =>
    }

    if (iter.trySkipSymbols(Vector('.', '.'))) {
      parseAtom(iter, stopOnCurlied) match {
        case Err(err) => return Err(err)
        case Ok(operand) => {
          val rangePE = RangePE(RangeL(spreeBegin, iter.getPrevEndPos()), exprSoFar, operand)
          return Ok(Some(rangePE))
        }
      }
    }

    val isMapCall = iter.trySkipSymbols(Vector('*', '.'))
    val isMethodCall =
      if (isMapCall) { false }
      else iter.trySkipSymbol('.')

    if (isMethodCall || isMapCall) {
      val nameBegin = iter.getPos()
      val name =
        iter.peek() match {
          case Some(ParsedIntegerLE(_, int, bits)) => {
            iter.advance()
            if (int < 0) {
              return Err(BadDot(iter.getPos()))
            }
            if (bits.nonEmpty) {
              return Err(BadDot(iter.getPos()))
            }
            NameP(RangeL(nameBegin, iter.getPrevEndPos()), interner.intern(StrI(int.toString)))
          }
          case Some(SymbolLE(_, _)) => {
            val name =
              iter.peek3() match {
                case (Some(SymbolLE(_, '<')), Some(SymbolLE(_, '=')), Some(SymbolLE(_, '>'))) => keywords.spaceship
                case (Some(SymbolLE(_, '=')), Some(SymbolLE(_, '=')), Some(SymbolLE(_, '='))) => keywords.tripleEquals
                case (Some(SymbolLE(_, '>')), Some(SymbolLE(_, '=')), _) => keywords.greaterEquals
                case (Some(SymbolLE(_, '<')), Some(SymbolLE(_, '=')), _) => keywords.lessEquals
                case (Some(SymbolLE(_, '!')), Some(SymbolLE(_, '=')), _) => keywords.notEquals
                case (Some(SymbolLE(_, '=')), Some(SymbolLE(_, '=')), _) => keywords.doubleEquals
                case (Some(SymbolLE(_, '+')), _, _) => keywords.plus
                case (Some(SymbolLE(_, '-')), _, _) => keywords.minus
                case (Some(SymbolLE(_, '*')), _, _) => keywords.asterisk
                case (Some(SymbolLE(_, '/')), _, _) => keywords.slash
              }
            U.loop(name.str.length, _ => iter.advance())
            NameP(RangeL(nameBegin, iter.getPrevEndPos()), name)
          }
          case Some(WordLE(_, str)) => {
            iter.advance()
            NameP(RangeL(nameBegin, iter.getPrevEndPos()), str)
          }
          case _ => return Err(BadDot(iter.getPos()))
        }

      val maybeTemplateArgs =
        parseChevronPack(iter) match {
          case Err(e) => return Err(e)
          case Ok(None) => None
          case Ok(Some(templateArgs)) => {
            Some(TemplateArgsP(RangeL(operatorBegin, iter.getPrevEndPos()), templateArgs))
          }
        }

      parsePack(iter) match {
        case Err(e) => return Err(e)
        case Ok(Some((range, x))) => {
          return Ok(
            Some(
              MethodCallPE(
                range,
                exprSoFar,
                RangeL(operatorBegin, iter.getPrevEndPos()),
                LookupPE(LookupNameP(name), maybeTemplateArgs),
                x)))
        }
        case Ok(None) => {
          if (maybeTemplateArgs.nonEmpty) {
            return Err(CantTemplateCallMember(iter.getPos()))
          }

          return Ok(
            Some(
              DotPE(
                RangeL(spreeBegin, iter.getPrevEndPos()),
                exprSoFar,
                RangeL(operatorBegin, iter.getPrevEndPos()),
                name)))
        }
      }
    }

    Ok(None)
  }

  def parseFunctionCall(originalIter: ScrambleIterator, spreeBegin: Int, exprSoFar: IExpressionPE):
  Result[Option[IExpressionPE], IParseError] = {
    val tentativeIter = originalIter.clone()
    val operatorBegin = tentativeIter.getPos()

    parsePack(tentativeIter) match {
      case Err(e) => Err(e)
      case Ok(None) => Ok(None)
      case Ok(Some((range, args))) => {
        originalIter.skipTo(tentativeIter)
        val iter = originalIter
        Ok(
          Some(
            FunctionCallPE(
              RangeL(spreeBegin, range.end),
              RangeL(operatorBegin, range.end),
              exprSoFar,
              args)))
      }
    }
  }

  def parseAtomAndTightSuffixes(iter: ScrambleIterator, stopOnCurlied: Boolean):
  Result[IExpressionPE, IParseError] = {
    vassert(iter.hasNext)
    val begin = iter.getPos()

    var exprSoFar =
      parseAtom(iter, stopOnCurlied) match {
        case Err(err) => return Err(err)
        case Ok(e) => e
      }

    var continuing = true
    while (continuing && iter.hasNext) {
      parseSpreeStep(begin, iter, exprSoFar, stopOnCurlied) match {
        case Err(err) => return Err(err)
        case Ok(None) => {
          continuing = false
        }
        case Ok(Some(newExpr)) => {
          exprSoFar = newExpr
        }
      }
    }

    Ok(exprSoFar)
  }

  def parseChevronPack(iter: ScrambleIterator): Result[Option[Vector[ITemplexPT]], IParseError] = {
    iter.peek() match {
      case Some(AngledLE(range, innerScramble)) => {
        iter.advance()

        Ok(
          Some(
            U.map[ScrambleIterator, ITemplexPT](
              new ScrambleIterator(innerScramble).splitOnSymbol(',', false),
              elementIter => {
                templexParser.parseTemplex(elementIter) match {
                  case Err(e) => return Err(e)
                  case Ok(x) => x
                }
              }).toVector))
      }
      case _ => Ok(None)
    }
  }

  def parseTemplateLookup(iter: ScrambleIterator, exprSoFar: IExpressionPE): Result[Option[LookupPE], IParseError] = {
    val operatorBegin = iter.getPos()

    val templateArgs =
      parseChevronPack(iter) match {
        case Err(e) => return Err(e)
        case Ok(None) => return Ok(None)
        case Ok(Some(templateArgs)) => {
          ast.TemplateArgsP(RangeL(operatorBegin, iter.getPrevEndPos()), templateArgs)
        }
      }

    val resultPE =
      exprSoFar match {
        case LookupPE(name, None) => ast.LookupPE(name, Some(templateArgs))
        case _ => return Err(BadTemplateCallee(operatorBegin))
      }

    Ok(Some(resultPE))
  }

  def parsePack(iter: ScrambleIterator):
  Result[Option[(RangeL, Vector[IExpressionPE])], IParseError] = {
    val parendLE =
      iter.peek() match {
        case Some(p @ ParendLE(_, _)) => iter.advance(); p
        case _ => return Ok(None)
      }

    val elements =
      U.map[ScrambleIterator, IExpressionPE](
        new ScrambleIterator(parendLE.contents).splitOnSymbol(',', false),
        elementIter => {
          parseExpression(elementIter, false) match {
            case Err(e) => return Err(e)
            case Ok(expr) => expr
          }
      })
    Ok(Some((parendLE.range, elements.toVector)))
  }

  def parseSquarePack(iter: ScrambleIterator): Result[Option[Vector[IExpressionPE]], IParseError] = {
    val squaredLE =
      iter.peek() match {
        case Some(p @ SquaredLE(_, _)) => iter.advance(); p
        case None => return Ok(None)
      }

    val elementsP =
      U.map[ScrambleIterator, IExpressionPE](
        new ScrambleIterator(squaredLE.contents).splitOnSymbol(',', false),
        elementIter => {
          parseExpression(elementIter, false) match {
            case Err(e) => return Err(e)
            case Ok(expr) => expr
          }
        })
    Ok(Some(elementsP.toVector))
  }

  def parseBracePack(iter: ScrambleIterator): Result[Option[Vector[IExpressionPE]], IParseError] = {
    iter.peek() match {
      case Some(SquaredLE(_, contents)) => {
        iter.advance()
        val elements =
          U.map[ScrambleIterator, IExpressionPE](
            new ScrambleIterator(contents).splitOnSymbol(',', false),
            elementIter => {
              parseExpression(elementIter, false) match {
                case Err(e) => return Err(e)
                case Ok(expr) => expr
              }
            })
        Ok(Some(elements.toVector))
      }
      case _ => Ok(None)
    }
  }

  def parseTupleOrSubExpression(iter: ScrambleIterator): Result[Option[IExpressionPE], IParseError] = {
    iter.peek() match {
      case Some(ParendLE(range, contents)) => {
        iter.advance()
        val iters =
          new ScrambleIterator(contents).splitOnSymbol(',', true)
        vassert(iters.nonEmpty)
        if (iters.length == 1) {
          if (!iters.head.hasNext) {
            // Then we have e.g. ()
            return Ok(Some(TuplePE(range, Vector())))
          } else {
            // Then we have e.g. (true)
            val inner =
              parseExpression(iters.head, false) match {
                case Err(e) => return Err(e)
                case Ok(x) => x
              }
            return Ok(Some(SubExpressionPE(range, inner)))
          }
        } else {
          // Then we have e.g. (true,) or (true,true) etc.
          val elementIters =
            if (!iters.last.hasNext) {
              // Last is empty, like in (true,) so take it out
              iters.init
            } else {
              iters
            }
          val elementsP =
            U.map[ScrambleIterator, IExpressionPE](
              elementIters,
              elementIter => {
                parseExpression(elementIter, false) match {
                  case Err(e) => return Err(e)
                  case Ok(x) => x
                }
              })
          Ok(Some(TuplePE(range, elementsP.toVector)))
        }
      }
      case _ => Ok(None)
    }
  }

  // An expression data element is an expression without binary operators. It has a definite end.
  def parseExpressionDataElement(iter: ScrambleIterator, stopOnCurlied: Boolean): Result[IExpressionPE, IParseError] = {
    vassert(iter.hasNext)

    val begin = iter.getPos()
    if (iter.trySkipSymbol('…')) {
      return Ok(ConstantIntPE(RangeL(begin, iter.getPrevEndPos()), 0, None))
    }

    iter.peek2() match {
      case (Some(SymbolLE(_, '\'')), Some(WordLE(range, str))) => {
        iter.advance()
        iter.advance()
        return parseExpressionDataElement(iter, stopOnCurlied)
      }
      case _ =>
    }

    // First, get the prefixes out of the way, such as & not etc.
    // Then we'll parse the atom and suffixes (.moo, ..5, etc.) and
    // *then* wrap those in the prefixes, so we get e.g. not(x.moo)
    if (iter.trySkipWord(keywords.not).nonEmpty) {
      val innerPE =
        parseExpressionDataElement(iter, stopOnCurlied) match {
          case Err(e) => return Err(e)
          case Ok(x) => x
        }
      return Ok(NotPE(RangeL(begin, innerPE.range.end), innerPE))
    }

    parseIfLadder(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(e)) => return Ok(e)
      case Ok(None) =>
    }

    parseForeach(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(e)) => return Ok(e)
      case Ok(None) =>
    }

    parseUnlet(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    val maybeTargetOwnership =
      iter.peek() match {
        case Some(SymbolLE(range, '^')) => {
          iter.advance()
          Some(OwnP)
        }
        case Some(SymbolLE(range, '&')) => {
          iter.advance()
          iter.peek() match {
            case Some(SymbolLE(range, '&')) => {
              iter.advance()
              Some(WeakP)
            }
            case _ => {
              Some(BorrowP)
            }
          }
        }
        // This is just a hack to get the syntax highlighter to highlight inl
        case Some(WordLE(range, inl)) if inl == keywords.inl => {
          iter.advance()
          Some(OwnP)
        }
        case _ => None
      }
    maybeTargetOwnership match {
      case Some(targetOwnership) => {
        val innerPE =
          parseAtomAndTightSuffixes(iter, stopOnCurlied) match {
            case Err(err) => return Err(err)
            case Ok(e) => e
          }
        val augmentPE = ast.AugmentPE(RangeL(begin, iter.getPrevEndPos()), targetOwnership, innerPE)
        return Ok(augmentPE)
      }
      case None =>
    }

    // Now, do some "right recursion"; parse the atom (e.g. true, 4, x)
    // and then parse any suffixes, like
    // .moo
    // .foo(5)
    // ..5
    // which all have tighter precedence than the prefixes.
    // Then we'll ret, and our callers will wrap it in the prefixes
    // like & not etc.
    return parseAtomAndTightSuffixes(iter, stopOnCurlied)
  }

  def parseBracedBody(iter: ScrambleIterator):  Result[BlockPE, IParseError] = {
    vimpl()
//    if (iter.trySkipWord("\\s*\\{").isEmpty) {
//      return Ok(None)
//    }
//
//    val bodyBegin = iter.getPos()
//    val bodyContents =
//      parseBlockContents(iter) match {
//        case Err(e) => return Err(e)
//        case Ok(x) => x
//      }
//    if (iter.trySkipWord("\\}").isEmpty) {
//      vwat()
//    }
//    Ok(Some(ast.BlockPE(RangeL(bodyBegin, iter.getPos()), bodyContents)))
  }

  def parseSingleArgLambdaBegin(originalIter: ScrambleIterator): Option[ParamsP] = {
    vimpl()
//    val tentativeIter = originalIter.clone()
//    val begin = tentativeIter.getPos()
//    val argName =
//      Parser.parseLocalOrMemberName(tentativeIter) match {
//        case None => return None
//        case Some(n) => n
//      }
//    val paramsEnd = tentativeIter.getPos()
//
//    tentativeIter.consumeWhitespace()
//    if (!tentativeIter.trySkipWord("=>")) {
//      return None
//    }
//
//    originalIter.skipTo(tentativeIter.position)
//
//    val range = RangeL(begin, paramsEnd)
//    val capture = LocalNameDeclarationP(argName)
//    val pattern = PatternPP(RangeL(begin, paramsEnd), None, Some(capture), None, None, None)
//    Some(ParamsP(range, Vector(pattern)))
  }

  def parseMultiArgLambdaBegin(originalIter: ScrambleIterator): Option[ParamsP] = {
    vimpl()
//    val tentativeIter = originalIter.clone()
//
//    val begin = tentativeIter.getPos()
//    if (!tentativeIter.trySkipWord("\\s*\\(")) {
//      return None
//    }
//    tentativeIter.consumeWhitespace()
//    val patterns = new mutable.ArrayBuffer[PatternPP]()
//
//    while (!tentativeIter.trySkipWord("\\s*\\)")) {
//      val pattern =
//        new PatternParser().parsePattern(tentativeIter) match {
//          case Ok(result) => result
//          case Err(cpe) => return None
//        }
//      patterns += pattern
//      tentativeIter.consumeWhitespace()
//      if (tentativeIter.peek(() => "^\\s*,\\s*\\)")) {
//        val found = tentativeIter.trySkipWord("\\s*,")
//        vassert(found)
//        vassert(tentativeIter.peek(() => "^\\s*\\)"))
//      } else if (tentativeIter.trySkipWord("\\s*,")) {
//        // good, continue
//      } else if (tentativeIter.peek(() => "^\\s*\\)")) {
//        // good, continue
//      } else {
//        // At some point, we should return an error here.
//        // With a pre-parser that looks for => it would be possible.
//        return None
//      }
//      tentativeIter.consumeWhitespace()
//    }
//
//    val paramsEnd = tentativeIter.getPos()
//
//    tentativeIter.consumeWhitespace()
//    if (!tentativeIter.trySkipWord("=>")) {
//      return None
//    }
//
//    val params = ast.ParamsP(RangeL(begin, paramsEnd), patterns.toVector)
//
//    originalIter.skipTo(tentativeIter.position)
//
//    Some(params)
  }

  def parseLambda(iter: ScrambleIterator): Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()
    val headerP =
      iter.peek3() match {
        case (Some(CurliedLE(range, contents)), _, _) => {
          val retuurn = FunctionReturnP(RangeL(iter.getPos(), iter.getPos()), None)
          // Don't iter.advance() because we still need to parse this later
          FunctionHeaderP(range, None, Vector(), None, None, None, retuurn)
        }
        case (Some(CurliedLE(range, contents)), _, _) => {
          val retuurn = FunctionReturnP(RangeL(iter.getPos(), iter.getPos()), None)
          // Don't iter.advance() because we still need to parse this later
          FunctionHeaderP(range, None, Vector(), None, None, None, retuurn)
        }
        case (Some(WordLE(paramRange, paramName)), Some(SymbolLE(eqRange, '=')), Some(SymbolLE(gtRange, '>'))) => {
          if (eqRange.end != gtRange.begin) {
            return Err(BadLambdaBegin(eqRange.begin))
          }
          iter.advance()
          iter.advance()
          iter.advance()
          val param = PatternPP(paramRange, None, Some(DestinationLocalP(LocalNameDeclarationP(NameP(paramRange, paramName)), None)), None, None, None)
          val params = ParamsP(paramRange, Vector(param))
          val retuurn = FunctionReturnP(RangeL(iter.getPos(), iter.getPos()), None)
          val range = RangeL(begin, iter.getPrevEndPos())
          FunctionHeaderP(range, None, Vector(), None, None, Some(params), retuurn)
        }
        case (Some(ParendLE(paramsRange, paramsContents)), Some(SymbolLE(eqRange, '=')), Some(SymbolLE(gtRange, '>'))) => {
          if (eqRange.end != gtRange.begin) {
            return Err(BadLambdaBegin(eqRange.begin))
          }
          iter.advance()
          iter.advance()
          iter.advance()
          val paramsP =
            ParamsP(
              paramsRange,
              U.mapWithIndex[ScrambleIterator, PatternPP](
                new ScrambleIterator(paramsContents).splitOnSymbol(',', false),
                (index, patternIter) => {
                  patternParser.parsePattern(patternIter, index, false, true, true) match {
                    case Err(e) => return Err(e)
                    case Ok(x) => x
                  }
                }).toVector)
          val retuurn = FunctionReturnP(RangeL(iter.getPos(), iter.getPos()), None)
          val range = RangeL(begin, iter.getPrevEndPos())
          FunctionHeaderP(range, None, Vector(), None, None, Some(paramsP), retuurn)
        }
        case (_, _, _) => return Ok(None)
      }

    val bodyP =
      iter.peek() match {
        case Some(blockL@CurliedLE(range, contents)) => {
          iter.advance()
          val statementsP =
            parseBlock(blockL) match {
              case Err(err) => return Err(err)
              case Ok(result) => result
            }
          BlockPE(
            blockL.range,
            // Would we ever want a lambda with a different default region?
            None,
            statementsP)
        }
        case Some(_) => {
          parseExpression(iter, false) match {
            case Err(err) => return Err(err)
            case Ok(result) => {
              BlockPE(
                result.range,
                // Would we ever want a lambda with a different default region?
                None,
                result)
            }
          }
        }
        case _ => vwat()
      }

    val lam = LambdaPE(None, FunctionP(RangeL(begin, iter.getPrevEndPos()), headerP, Some(bodyP)))
    Ok(Some(lam))
  }

  def parseArray(originalIter: ScrambleIterator): Result[Option[IExpressionPE], IParseError] = {
    val tentativeIter = originalIter.clone()
    val begin = tentativeIter.getPos()

    val mutability =
      if (tentativeIter.trySkipSymbol('#')) {
        MutabilityPT(RangeL(begin, tentativeIter.getPrevEndPos()), ImmutableP)
      } else {
        MutabilityPT(RangeL(begin, begin), MutableP)
      }

    // If there's no square, we're not making an array.
    val sizer =
      tentativeIter.peek() match {
        case Some(s @ SquaredLE(_, _)) => s
        case _ => return Ok(None)
      }
    tentativeIter.advance()


    val isArray =
      tentativeIter.peek() match {
        // If there's nothing after the square brackets, it's not an array.
        case None => false
        case Some(SymbolLE(range, '.')) => false
        case _ => true
      }

    if (!isArray) {
      // Not an array, bail.
      // TODO: Someday, we could interpret this occurrence as a way to make a List.
      return Ok(None)
    }

    originalIter.skipTo(tentativeIter)
    val iter = originalIter

    val sizerIter = new ScrambleIterator(sizer.contents)
    val size =
      if (sizerIter.trySkipSymbol('#')) {
        val sizeTemplex =
          if (sizerIter.hasNext) {
            templexParser.parseTemplex(sizerIter) match {
              case Err(e) => return Err(e)
              case Ok(e) => Some(e)
            }
          } else {
            None
          }
        StaticSizedP(sizeTemplex)
      } else {
        RuntimeSizedP
      }

    val tyype =
      iter.peek() match {
        case Some(ParendLE(range, contents)) => None
        case _ => {
          templexParser.parseTemplex(iter) match {
            case Err(e) => return Err(e)
            case Ok(e) => Some(e)
          }
        }
        case _ => return Err(BadArraySpecifier(iter.getPos()))
      }

    val args =
      parsePack(iter) match {
        case Ok(None) => return Err(BadArraySpecifier(iter.getPos()))
        case Ok(Some((range, e))) => e
        case Err(e) => return Err(e)
      }

    val initializingByValues =
      (size, args.size) match {
        case (StaticSizedP(None), _) => true // e.g. [#](3, 4, 5)
        case (StaticSizedP(Some(_)), _) => false // Anything else should take a function.
        case (RuntimeSizedP, _) => false // Any runtime sized array should take a function.
      }

    val arrayPE =
      ConstructArrayPE(
        RangeL(begin, iter.getPrevEndPos()),
        tyype,
        Some(mutability),
        None,
        size,
        initializingByValues,
        args)
    Ok(Some(arrayPE))
  }

  // Returns the index we stopped at, which will be either
  // the end of the array or one past endIndexInclusive.
  def descramble(
    elements: Vector[IExpressionElement],
    beginIndexInclusive: Int,
    endIndexInclusive: Int,
    minPrecedence: Int):
  (IExpressionPE, Int) = {
    vassert(elements.nonEmpty)
    vassert(elements.size % 2 == 1)

    if (beginIndexInclusive == endIndexInclusive) {
      val onlyElement = elements(beginIndexInclusive).asInstanceOf[DataElement].expr
      return (onlyElement, beginIndexInclusive + 1)
    }
    if (minPrecedence == MAX_PRECEDENCE) {
      val onlyElement = elements(beginIndexInclusive).asInstanceOf[DataElement].expr
      return (onlyElement, beginIndexInclusive + 1)
    }

    var (leftOperand, nextIndex) =
      descramble(elements, beginIndexInclusive, endIndexInclusive, minPrecedence + 1)

    while (nextIndex < endIndexInclusive &&
      elements(nextIndex).asInstanceOf[BinaryCallElement].precedence == minPrecedence) {

      val binaryCall = elements(nextIndex).asInstanceOf[BinaryCallElement]
      nextIndex += 1

      val (rightOperand, newNextIndex) =
        descramble(elements, nextIndex, endIndexInclusive, minPrecedence + 1)
      nextIndex = newNextIndex

      leftOperand =
        binaryCall.symbol.str match {
          case s if s == keywords.and => {
            AndPE(
              RangeL(leftOperand.range.begin, leftOperand.range.end),
              leftOperand,
              BlockPE(rightOperand.range, None, rightOperand))
          }
          case s if s == keywords.or => {
            OrPE(
              RangeL(leftOperand.range.begin, leftOperand.range.end),
              leftOperand,
              BlockPE(rightOperand.range, None, rightOperand))
          }
          case _ => {
            BinaryCallPE(
              RangeL(leftOperand.range.begin, leftOperand.range.end),
              binaryCall.symbol,
              leftOperand,
              rightOperand)
          }
        }
    }

    (leftOperand, nextIndex)
  }

  def parseBinaryCall(iter: ScrambleIterator):
  Result[Option[NameP], IParseError] = {
    val name =
      iter.peek3() match {
        case (Some(WordLE(range, str)), _, _) => {
          iter.advance()
          NameP(range, str)
        }
        case (Some(SymbolLE(range, s @ ('+' | '-' | '*' | '/'))), _, _) => {
          iter.advance()
          NameP(range, interner.intern(StrI(s.toString)))
        }
        case (Some(SymbolLE(_, '=')), Some(SymbolLE(_, '=')), Some(SymbolLE(_, '='))) => {
          val begin = iter.getPos()
          iter.advance()
          iter.advance()
          iter.advance()
          val end = iter.getPrevEndPos()
          NameP(RangeL(begin, end), keywords.tripleEquals)
        }
        case (Some(SymbolLE(_, '<')), Some(SymbolLE(_, '=')), Some(SymbolLE(_, '>'))) => {
          val begin = iter.getPos()
          iter.advance()
          iter.advance()
          iter.advance()
          val end = iter.getPrevEndPos()
          NameP(RangeL(begin, end), keywords.spaceship)
        }
        case (Some(SymbolLE(range1, s1 @ ('>' | '<' | '=' | '!'))), Some(SymbolLE(range2, '=')), _) => {
          iter.advance()
          iter.advance()
          NameP(RangeL(range1.begin, range2.end), interner.intern(StrI(s1.toString + '=')))
        }
        case (Some(SymbolLE(range, s @ ('>' | '<'))), _, _) => {
          iter.advance()
          NameP(range, interner.intern(StrI(s.toString)))
        }
        case _ => return Ok(None)
      }

    Ok(Some(name))
  }

  def atExpressionEnd(iter: ScrambleIterator, stopOnCurlied: Boolean): Boolean = {
    iter.peek() match {
      case None => true
      case Some(SymbolLE(range, ';')) => true
      case Some(CurliedLE(range, contents)) if stopOnCurlied => true
      case _ => false
    }
//    return Parser.atEnd(iter) || iter.peek(() => "^\\s*;")
  }
}
