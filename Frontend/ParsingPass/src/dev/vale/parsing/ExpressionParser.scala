package dev.vale.parsing

import dev.vale.options.GlobalOptions
import dev.vale.parsing.templex.TemplexParser
import ExpressionParser.{MAX_PRECEDENCE, MIN_PRECEDENCE}
import dev.vale.parsing.ast.{AndPE, AugmentPE, BinaryCallPE, BlockPE, BorrowP, BraceCallPE, BreakPE, ConsecutorPE, ConstantBoolPE, ConstantFloatPE, ConstantIntPE, ConstructArrayPE, DestructPE, DotPE, EachPE, FunctionCallPE, FunctionHeaderP, FunctionP, FunctionReturnP, IExpressionPE, ITemplexPT, IfPE, ImmutableP, LambdaPE, LetPE, LocalNameDeclarationP, LookupNameP, LookupPE, MagicParamLookupPE, MethodCallPE, MutabilityPT, MutableP, MutatePE, NameP, NotPE, OrPE, OwnP, ParamsP, PatternPP, RangePE, ReturnPE, RuntimeSizedP, StaticSizedP, SubExpressionPE, TemplateArgsP, TuplePE, UnletPE, VoidPE, WeakP, WhilePE}
import dev.vale.{Accumulator, Err, Interner, Ok, Profiler, Result, StrI, U, parsing, vassert, vcurious, vfail, vimpl, vwat}
import dev.vale.parsing.ast._
import dev.vale.lexing.{AngledLE, BadArraySizerEnd, BadArraySpecifier, BadBinaryFunctionName, BadDot, BadEndOfBlock, BadEndOfElseBody, BadEndOfIfBody, BadEndOfWhileBody, BadExpressionBegin, BadExpressionEnd, BadForeachInError, BadForeachIterableError, BadLocalNameInUnlet, BadMutateEqualsError, BadRangeOperand, BadStartOfBlock, BadStartOfElseBody, BadStartOfIfBody, BadStartOfWhileBody, BadTemplateCallee, CantTemplateCallMember, CantUseBreakInExpression, CantUseReturnInExpression, CommaSeparatedListLE, CurliedLE, DontNeedSemicolon, ForgotSetKeyword, INodeLE, IParseError, NeedSemicolon, NeedWhitespaceAroundBinaryOperator, ParendLE, ParsedDoubleLE, ParsedIntegerLE, RangeL, ScrambleLE, SemicolonSeparatedListLE, SquaredLE, StringLE, StringPart, StringPartExpr, StringPartLiteral, SymbolLE, UnknownTupleOrSubExpression, UnrecognizableExpressionAfterAugment, WordLE}

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

class ScrambleIterator(scramble: ScrambleLE, var index: Int, var end: Int) {
  assert(end <= scramble.elements.length)

  def getPos(): Int = {
    if (index >= end) {
      scramble.range.end
    } else {
      scramble.elements(index).range.begin
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
  def peek(n: Int): Array[Option[INodeLE]] = {
    index.until(index + n).map(scramble.elements.lift).toArray
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
  def peekLast(): Option[INodeLE] = {
    if (hasNext) Some(scramble.elements.last)
    else None
  }
  def takeLast(): INodeLE = {
    peekLast() match {
      case Some(x) => {
        end = end - 1
        x
      }
      case None => assert(false); vfail()
    }
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
  def trySkipSymbols(symbols: Array[Char]): Boolean = {
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
    val found = trySkipWord(str)
    vassert(found)
  }
  def trySkipWord(str: StrI): Boolean = {
    peek() match {
      case Some(WordLE(_, s)) if s == str => {
        advance()
        true
      }
      case _ => false
    }
  }
}

class ExpressionParser(interner: Interner, keywords: Keywords, opts: GlobalOptions, templexParser: TemplexParser) {
  val patternParser = new PatternParser(interner, templexParser)
//  val stringParser = new StringParser(this)

  private def parseWhile(iter: ScrambleIterator): Result[Option[WhilePE], IParseError] = {
    vimpl()
//    if (!iter.trySkipWord(whiile)) {
//      return Ok(None)
//    }
//
//    val condition =
//      parseBlockContents(iter.advance()) match {
//        case Ok(result) => result
//        case Err(cpe) => return Err(cpe)
//      }
//
//
//
//    val bodyBegin = iter.getPos()
//    if (!iter.trySkipWord("\\{")) {
//      return Err(BadStartOfWhileBody(iter.position))
//    }
//
//    val body =
//      parseBlockContents(iter) match {
//        case Ok(result) => result
//        case Err(cpe) => return Err(cpe)
//      }
//    val bodyEnd = iter.getPos()
//
//    if (!iter.trySkipWord("\\}")) {
//      return Err(BadEndOfWhileBody(iter.position))
//    }
//    val whileEnd = iter.getPos()
//
//    Ok(
//      Some(
//        ast.WhilePE(
//          RangeL(whileBegin, whileEnd),
//          condition,
//          BlockPE(RangeL(bodyBegin, bodyEnd), body))))
  }

  private def parseForeach(
    originalIter: ScrambleIterator):
  Result[Option[EachPE], IParseError] = {
    val eachBegin = originalIter.getPos()

    val tentativeIter = originalIter.clone()

    if (tentativeIter.trySkipWord(keywords.parallel)) {
      // do nothing for now
    }

    if (!tentativeIter.trySkipWord(keywords.foreeach)) {
      return Ok(None)
    }
    originalIter.skipTo(tentativeIter)
    val iter = originalIter

    val pattern =
      new PatternParser(interner, templexParser).parsePattern(iter) match {
        case Err(cpe) => return Err(cpe)
        case Ok(result) => result
      }


    val inBegin = iter.getPos()
    if (!iter.trySkipWord(keywords.in)) {
      return Err(BadForeachInError(iter.getPos()))
    }
    val inEnd = iter.getPos()



    val iterableExpr =
      iter.peek() match {
        case None => return Err(BadForeachIterableError(iter.getPos()))
        case Some(CurliedLE(range, contents)) => {
          parseBlockContents(contents) match {
            case Err(err) => return Err(err)
            case Ok(expression) => expression
          }
        }
        case Some(s) => {
          parseExpression(s) match {
            case Err(err) => return Err(err)
            case Ok(expression) => expression
          }
        }
      }

    val bodyBegin = iter.getPos()

    val body =
      iter.peek() match {
        case Some(CurliedLE(_, contents)) => {
          parseBlockContents(contents) match {
            case Err(cpe) => return Err(cpe)
            case Ok(result) => result
          }
        }
        case _ => return Err(BadStartOfWhileBody(iter.getPos()))
      }

    val bodyEnd = iter.getPos()
    val eachEnd = iter.getPos()

    Ok(
      Some(
        EachPE(
          RangeL(eachBegin, eachEnd),
          pattern,
          RangeL(inBegin, inEnd),
          iterableExpr,
          ast.BlockPE(RangeL(bodyBegin, bodyEnd), body))))
  }

  private def parseIfLadder(iter: ScrambleIterator): Result[Option[IfPE], IParseError] = {
    val ifLadderBegin = iter.getPos()

    if (!iter.trySkipWord(keywords.iff)) {
      return Ok(None)
    }

    val rootIf =
      parseIfPart(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    val ifElses = mutable.MutableList[(IExpressionPE, BlockPE)]()
    while (iter.peek(2) match {
      case Array(Some(WordLE(_, elsse)), Some(WordLE(_, iff)))
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
      if (iter.trySkipWord(keywords.elsse)) {
        val body =
          iter.peek() match {
            case Some(b @ CurliedLE(_, _)) => iter.advance(); b
            case _ => return Err(BadStartOfElseBody(iter.getPos()))
          }

        val elseBody =
          parseBlockContents(body.contents) match {
            case Ok(result) => result
            case Err(cpe) => return Err(cpe)
          }

        val elseEnd = iter.getPos()
        Some(ast.BlockPE(RangeL(elseBegin, elseEnd), elseBody))
      } else {
        None
      }

    val ifLadderEnd = iter.getPos()

    val finalElse: BlockPE =
      maybeElseBlock match {
        case None => BlockPE(RangeL(ifLadderEnd, ifLadderEnd), VoidPE(RangeL(ifLadderEnd, ifLadderEnd)))
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
          RangeL(ifLadderBegin, ifLadderEnd),
          rootConditionLambda,
          rootThenLambda,
          rootElseBlock)))
  }

  private def parseMut(
    iter: ScrambleIterator):
  Result[Option[MutatePE], IParseError] = {
    val mutateBegin = iter.getPos()
    if (!iter.trySkipWord(keywords.sett)) {
      return Ok(None)
    }

    val mutatee =
      parseExpression(iter) match {
        case Err(err) => return Err(err)
        case Ok(expression) => expression
      }

    // Expect an = after this
    iter.peek(2) match {
      case Array(Some(SymbolLE(_, '=')), Some(SymbolLE(_, '='))) => {
        return Err(BadMutateEqualsError(iter.getPos()))
      }
      case Array(Some(SymbolLE(_, '=')), Some(_)) => // Good, continue
      case _ => return Err(BadMutateEqualsError(iter.getPos()))
    }
    iter.advance()


    val source =
      parseExpression(iter) match {
        case Err(err) => return Err(err)
        case Ok(expression) => expression
      }
    val mutateEnd = iter.getPos()

    Ok(Some(ast.MutatePE(RangeL(mutateBegin, mutateEnd), mutatee, source)))
  }

  private def parseLet(
    originalIter: ScrambleIterator):
  Result[LetPE, IParseError] = {
    vimpl()
//    val tentativeIter = originalIter.clone()
//
//    val letBegin = tentativeIter.getPos()
//
//    val pattern =
//      new PatternParser().parsePattern(tentativeIter) match {
//        case Ok(result) => result
//        case Err(cpe) => {
//          // someday we'll have a preparser that means we dont have to throw this away
//          return Ok(None)
//        }
//      }
//
//    tentativeIter.consumeWhitespace()
//    // Because == would be a binary == operator
//    // and => would be a lambda
//    if (!tentativeIter.trySkipWord("=[^=>]")) {
//      return Ok(None)
//    }
//    tentativeIter.consumeWhitespace()
//
//    // We know that this is a valid pattern, so let's commit to it.
//    originalIter.skipTo(tentativeIter.position)
//    val iter = originalIter
//
//    pattern.capture match {
//      case Some(LocalNameDeclarationP(name)) => vassert(name.str != "set" && name.str != "mut")
//      case _ =>
//    }
//
//
//    val source =
//      // This is for when the articles do:
//      //   x = ...;
//      // and we strip out the ... to get
//      //   x =    ;
//      // so let's just interpret it as a void
//      if (iter.peek(() => "^\\s*;")) {
//
//        VoidPE(RangeL(iter.getPos(), iter.getPos()))
//      } else {
//        parseExpression(iter) match {
//          case Err(err) => return Err(err)
//          case Ok(expression) => expression
//        }
//      }
//    val letEnd = iter.getPos()
//
//
//    //    if (!iter.tryConsume("^;")) { return Err(BadLetEndError(iter.getPos())) }
//
//    Ok(Some(ast.LetPE(RangeL(letBegin, letEnd), pattern, source)))
  }

  private def parseIfPart(
    iter: ScrambleIterator):
  Result[(IExpressionPE, BlockPE), IParseError] = {
    vimpl()
//    if (!iter.trySkipWord("if")) {
//      vwat()
//    }
//
//    val condition =
//      parseBlockContents(iter) match {
//        case Ok(result) => result
//        case Err(cpe) => return Err(cpe)
//      }
//
//    val bodyBegin = iter.getPos()
//    if (!iter.trySkipWord("\\{")) {
//      return Err(BadStartOfIfBody(iter.position))
//    }
//
//    val body =
//      parseBlockContents(iter) match {
//        case Ok(result) => result
//        case Err(cpe) => return Err(cpe)
//      }
//
//    if (!iter.trySkipWord("\\}")) {
//      return Err(BadEndOfIfBody(iter.position))
//    }
//    val bodyEnd = iter.getPos()
//
//    Ok(
//      (
//        condition,
//        ast.BlockPE(RangeL(bodyBegin, bodyEnd), body)))
  }

  def parseBlock(blockL: CurliedLE): Result[IExpressionPE, IParseError] = {
    blockL.contents match {
      case s @ SemicolonSeparatedListLE(_, _, _) => {
        parseBlockContents(s)
      }
      case _ => vimpl()
    }
  }

  def parseBlockContents(statementsL: SemicolonSeparatedListLE): Result[IExpressionPE, IParseError] = {
    val statementsP = new Accumulator[IExpressionPE]()

    U.foreach[ScrambleLE](statementsL.elements, statement => {
      parseStatement(statement) match {
        case Err(error) => return Err(error)
        case Ok(newStatement) => statementsP.add(newStatement)
      }
    })

//    while continuing {
//      val hadSemicolon = statementsL.trailingSemicolon
//
//
//      if (endingBlock()) {
//        continuing = false;
//
//        if (hadSemicolon) {
//          if (newStatement.needsSemicolonBeforeNextStatement) {
//            if (newStatement.producesResult()) {
//              // Example: { 4 }
//              // Last statement, has a semicolon.
//              // End with a void.
//              statements += VoidPE(RangeL(iter.getPos(), iter.getPos()))
//            } else {
//              // Example: { a = 4; }
//              // Last statement, produces no result but would need a semicolon to continue on.
//              // Moot, because we aren't continuing on anyway.
//              // End with a void.
//              statements += VoidPE(RangeL(iter.getPos(), iter.getPos()))
//            }
//          } else {
//            return Err(DontNeedSemicolon(iter.getPos()))
//          }
//        } else {
//          if (newStatement.needsSemicolonBeforeNextStatement) {
//            // No semicolon, but we don't need one, so its fine, continue.
//            if (newStatement.producesResult()) {
//              // This is the last statement, the expression produces a result, and has no semicolon.
//              // Let's end with this statement.
//            } else {
//              // Example:
//              //   if (a) = x { ... }
//              //      ^^^^^^^
//              // Let's end with this statement.
//            }
//          } else {
//            if (newStatement.producesResult()) {
//              // Example, the if in:
//              //   exported func main() {
//              //     if true { 3 } else { 4 }
//              //   }
//              // Let's end with this statement, don't add a void afterward.
//            } else {
//              // Example:
//              //   while true { }
//              //                 ^
//              // Add a void here, because the while loop doesnt produce a result.
//              statements += VoidPE(RangeL(iter.getPos(), iter.getPos()))
//            }
//          }
//        }
//      } else {
//        if (hadSemicolon) {
//          if (newStatement.needsSemicolonBeforeNextStatement) {
//            // Semicolon ended a statement that wanted one, continue.
//          } else {
//            return Err(DontNeedSemicolon(iter.getPos()))
//          }
//        } else {
//          if (newStatement.needsSemicolonBeforeNextStatement) {
//            return Err(NeedSemicolon(iter.getPos()))
//          } else {
//            // Example:
//            //   while true { } false
//            //                 ^
//            // Just continue.
//          }
//        }
//      }
//
//
//      if (continuing && Parser.atEnd(iter)) {
//        /// statements += Ok(VoidPE(RangeP(iter.getPos(), iter.getPos())))
//        vcurious()
//      }
//    }

    if (statementsL.trailingSemicolon) {
      statementsP.add(VoidPE(statementsL.range))
    }

    statementsP.size match {
      case 0 => Ok(VoidPE(statementsL.range))
      case 1 => Ok(statementsP.head)
      case _ => Ok(ConsecutorPE(statementsP.buildArray().toVector))
    }
  }

  private def parseLoneBlock(
    iter: ScrambleIterator):
  Result[Option[IExpressionPE], IParseError] = {
    vimpl()
//    // The pure is a hack to get syntax highlighting work for
//    // the future pure block feature.
//    if (!iter.trySkipWord("(unsafe\\s+pure|pure|block)")) {
//      return Ok(None)
//    }
//
//    val begin = iter.getPos()
//    if (!iter.trySkipWord("\\{")) {
//      return Err(BadStartOfBlock(iter.position))
//    }
//
//    val contents =
//      parseBlockContents(iter) match {
//        case Err(error) => return Err(error)
//        case Ok(result) => result
//      }
//
//    if (!iter.trySkipWord("\\}")) {
//      return Err(BadEndOfBlock(iter.position))
//    }
//    val end = iter.getPos()
//    Ok(Some(ast.BlockPE(RangeL(begin, end), contents)))
  }

  private def parseDestruct(
    iter: ScrambleIterator):
  Result[Option[IExpressionPE], IParseError] = {
    vimpl()
//    val begin = iter.getPos()
//    if (!iter.trySkipWord("destruct")) {
//      return Ok(None)
//    }
//
//    parseExpression(iter)
//      .map(x => Some(DestructPE(RangeL(begin, iter.getPos()), x)))
  }

  private def parseUnlet(
    iter: ScrambleIterator):
  Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()
    if (!iter.trySkipWord(keywords.unlet)) {
      return Ok(None)
    }
    val local =
      iter.nextWord() match {
        case None => return Err(BadLocalNameInUnlet(iter.getPos()))
        case Some(WordLE(range, str)) => LookupNameP(NameP(range, str))
      }
    Ok(Some(UnletPE(RangeL(begin, iter.getPos()), local)))
  }

  private def parseReturn(
    iter: ScrambleIterator):
  Result[Option[IExpressionPE], IParseError] = {
    vimpl()
//    val begin = iter.getPos()
//    if (!iter.trySkipWord("return")) {
//      return Ok(None)
//    }
//
//    parseExpression(iter)
//      .map(x => Some(ReturnPE(RangeL(begin, iter.getPos()), x)))
  }

  private def parseBreak(
    iter: ScrambleIterator):
  Result[Option[IExpressionPE], IParseError] = {
    vimpl()
//    val begin = iter.getPos()
//    if (!iter.trySkipWord("break")) {
//      return Ok(None)
//    }
//    Ok(Some(BreakPE(RangeL(begin, iter.getPos()))))
  }

  // expectEnder should be true if we should expect to end with a semicolon or a right brace.
  // expectResult should be true if we should expect the statement to produce a result.
  private[parsing] def parseStatement(
    node: INodeLE):
  Result[IExpressionPE, IParseError] = {
    vimpl() // move the maybeEqualsIndex check up here so we can parse the pattern first and then continue with the rest

    val scramble =
      node match {
        case s @ ScrambleLE(_, _, _) => s
        case CommaSeparatedListLE(_, _, _) => vfail()
        case only => ScrambleLE(only.range, Array(only), None)
      }
    vassert(scramble.elements.nonEmpty)

    val iter = new ScrambleIterator(scramble, 0, scramble.elements.length)

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

    parseReturn(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseDestruct(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseMut(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    if (scramble.maybeEqualsIndex.nonEmpty) {
      parseLet(iter)
    } else {
      parseExpression(iter)
    }
  }

  def parseExpression(node: INodeLE): Result[IExpressionPE, IParseError] = {
    val scramble =
      node match {
        case s @ ScrambleLE(_, _, _) => s
        case other => ScrambleLE(other.range, Array(other), None)
      }
    parseExpression(new ScrambleIterator(scramble, 0, scramble.elements.length))
  }

  def getPrecedence(str: StrI): Int = {
    if (str == keywords.DOT_DOT) 6
    else if (str == keywords.ASTERISK || str == keywords.SLASH) 5
    else if (str == keywords.PLUS || str == keywords.MINUS) 4
    // _ => 3 Everything else is 3, see end case
    else if (str == keywords.ASTERISK || str == keywords.SLASH) 5
    else if (str == keywords.SPACESHIP || str == keywords.LESS_THAN_OR_EQUAL ||
      str == keywords.LESS_THAN || str == keywords.GREATER_THAN_OR_EQUAL ||
      str == keywords.GREATER_THAN || str == keywords.TRIPLE_EQUALS ||
      str == keywords.DOUBLE_EQUALS || str == keywords.NOT_EQUAL) 2
    else if (str == keywords.AND || str == keywords.OR) 1
    else 3 // This is so we can have 3 mod 2 == 1
  }

  def parseExpression(iter: ScrambleIterator): Result[IExpressionPE, IParseError] = {
    Profiler.frame(() => {
      //    if (iter.peek(() => "^if\\s")) {
      //      parseIfLadder(iter)
      //    } else if (iter.peek(() => "^foreach\\s") || iter.peek(() => "^parallel\\s+foreach\\s")) {
      //      parseForeach(iter)
      //    } else if (iter.peek(() => "^(set|mut)\\s")) {
      //      parseMut(iter)
      //    } else {
      //      parseExpression(allowLambda)(iter)
      //    }

      val elements = mutable.ArrayBuffer[IExpressionElement]()

      var continue = true
      while (continue) {
        val subExpr =
          parseExpressionDataElement(iter) match {
            case Err(error) => return Err(error)
            case Ok(Some(x)) => x
            case Ok(None) => return Err(BadExpressionBegin(iter.getPos()))
          }
        elements += parsing.DataElement(subExpr)

        if (atExpressionEnd(iter)) {
          continue = false
        } else {
          if (subExpr.range.end == iter.getPos()) {
            return Err(NeedWhitespaceAroundBinaryOperator(iter.getPos()))
          }

          parseBinaryCall(iter) match {
            case Err(error) => return Err(error)
            case Ok(None) => continue = false
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
            }
          }
        }
      }

      val (exprPE, _) =
        descramble(elements.toArray, 0, elements.size - 1, MIN_PRECEDENCE)
      Ok(exprPE)
    })
  }

  def parseLookup(iter: ScrambleIterator): Option[IExpressionPE] = {
    vimpl()
//    Parser.parseFunctionOrLocalOrMemberName(iter) match {
//      case Some(name) => Some(LookupPE(LookupNameP(name), None))
//      case None => None
//    }
  }

  val truue = interner.intern(StrI("true"))
  val faalse = interner.intern(StrI("false"))

  def parseBoolean(iter: ScrambleIterator): Option[IExpressionPE] = {
    val start = iter.getPos()
    if (iter.trySkipWord(truue)) {
      return Some(ConstantBoolPE(RangeL(start, iter.getPos()), true))
    }
    if (iter.trySkipWord(faalse)) {
      return Some(ConstantBoolPE(RangeL(start, iter.getPos()), false))
    }
    return None
  }

  def parseAtom(iter: ScrambleIterator): Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()

    // See BRCOBS
    if (iter.trySkipWord(keywords.break)) {
      return Err(CantUseBreakInExpression(iter.getPos()))
    }
    // See BRCOBS
    if (iter.trySkipWord(keywords.retuurn)) {
      return Err(CantUseReturnInExpression(iter.getPos()))
    }
    if (iter.trySkipWord(keywords.UNDERSCORE)) {
      return Ok(Some(MagicParamLookupPE(RangeL(begin, iter.getPos()))))
    }
    parseForeach(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(Some(x))
      case Ok(None) =>
    }

    parseMut(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(Some(x))
      case Ok(None) =>
    }

    iter.peek() match {
      case Some(ParsedIntegerLE(range, num, bits)) => {
        iter.advance()
        return Ok(Some(ConstantIntPE(range, num, bits)))
      }
      case Some(ParsedDoubleLE(range, num, bits)) => {
        iter.advance()
        return Ok(Some(ConstantFloatPE(range, num)))
      }
      case Some(StringLE(range, Array(StringPartLiteral(_, s)))) => {
        iter.advance()
        return Ok(Some(ConstantStrPE(range, s)))
      }
      case Some(StringLE(range, partsL)) => {
        iter.advance()
        val partsP =
          U.map[StringPart, IExpressionPE](partsL, {
            case StringPartLiteral(range, s) => ConstantStrPE(range, s)
            case StringPartExpr(expr) => {
              parseExpression(expr) match {
                case Err(e) => return Err(e)
                case Ok(x) => x
              }
            }
          })
        Ok(StrInterpolatePE(range, partsP.toVector))
      }
      case _ =>
    }
    parseBoolean(iter) match {
      case Some(e) => return Ok(Some(e))
      case None =>
    }
    vimpl()
//    parseArray(iter) match {
//      case Err(err) => return Err(err)
//      case Ok(Some(e)) => return Ok(Some(e))
//      case Ok(None) =>
//    }
//    parseLambda(iter) match {
//      case Err(err) => return Err(err)
//      case Ok(Some(e)) => return Ok(Some(e))
//      case Ok(None) =>
//    }
//    parseLookup(iter) match {
//      case Some(e) => return Ok(Some(e))
//      case None =>
//    }
//    parseTupleOrSubExpression(iter) match {
//      case Err(err) => return Err(err)
//      case Ok(Some(e)) => {
//        return Ok(Some(e))
//      }
//      case Ok(None) =>
//    }
//    Ok(None)
  }

//  def parseNumberExpr(originalIter: ScrambleIterator): Result[Option[IExpressionPE], IParseError] = {
//    val tentativeIter = originalIter.clone()
//
//    val begin = tentativeIter.getPos()
//
//    val isNegative =
//      tentativeIter.peek(2) match {
//        case Array(SymbolLE(range, '-'), IntLE(intRange, _, _)) => {
//          // Only consider it a negative if it's right next to the next thing
//          if (range.end != intRange.begin) {
//            return Ok(None)
//          }
//          tentativeIter.advance()
//          true
//        }
//        case Array(IntLE(_, _, _), _) => false
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

  def parseSpreeStep(spreeBegin: Int, iter: ScrambleIterator, exprSoFar: IExpressionPE):
  Result[Option[IExpressionPE], IParseError] = {
    val operatorBegin = iter.getPos()

    if (iter.trySkipSymbol('&')) {
      val rangePE = AugmentPE(RangeL(spreeBegin, iter.getPos()), BorrowP, exprSoFar)
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
              RangeL(spreeBegin, iter.getPos()),
              RangeL(operatorBegin, iter.getPos()),
              exprSoFar,
              args,
              false)))
      }
      case Ok(None) =>
    }

    if (iter.trySkipSymbols(Array('.', '.'))) {
      parseAtom(iter) match {
        case Err(err) => return Err(err)
        case Ok(None) => return Err(BadRangeOperand(iter.getPos()))
        case Ok(Some(operand)) => {
          val rangePE = RangePE(RangeL(spreeBegin, iter.getPos()), exprSoFar, operand)
          return Ok(Some(rangePE))
        }
      }
    }

    if (iter.trySkipSymbol('.')) {
      val operatorEnd = iter.getPos()

      val nameBegin = iter.getPos()
      val name =
        vimpl()
//        iter.tryy(() => "^\\d+") match {
//          case Some(x) => {
//            NameP(RangeL(nameBegin, iter.getPos()), x)
//          }
//          case None => {
//            Parser.parseFunctionOrLocalOrMemberName(iter) match {
//              case Some(n) => n
//              case None => return Err(BadDot(iter.getPos()))
//            }
//          }
//        }

      val maybeTemplateArgs =
        parseChevronPack(iter) match {
          case Err(e) => return Err(e)
          case Ok(None) => None
          case Ok(Some(templateArgs)) => {
            Some(TemplateArgsP(RangeL(operatorBegin, iter.getPos()), templateArgs))
          }
        }

      parsePack(iter) match {
        case Err(e) => return Err(e)
        case Ok(Some(x)) => {
          return Ok(
            Some(
              MethodCallPE(
                RangeL(spreeBegin, iter.getPos()),
                exprSoFar,
                RangeL(operatorBegin, operatorEnd),
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
                RangeL(spreeBegin, iter.getPos()),
                exprSoFar,
                RangeL(operatorBegin, operatorEnd),
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
      case Ok(Some(args)) => {
        originalIter.skipTo(tentativeIter)
        val iter = originalIter
        Ok(
          Some(
            FunctionCallPE(
              RangeL(spreeBegin, iter.getPos()),
              RangeL(operatorBegin, iter.getPos()),
              exprSoFar,
              args)))
      }
    }
  }

  def parseAtomAndTightSuffixes(iter: ScrambleIterator):
  Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()

    var exprSoFar =
      parseAtom(iter) match {
        case Err(err) => return Err(err)
        case Ok(None) => return Ok(None)
        case Ok(Some(e)) => e
      }

    var continuing = true
    while (continuing && iter.hasNext) {
      parseSpreeStep(begin, iter, exprSoFar) match {
        case Err(err) => return Err(err)
        case Ok(None) => {
          continuing = false
        }
        case Ok(Some(newExpr)) => {
          exprSoFar = newExpr
        }
      }
    }

    Ok(Some(exprSoFar))
  }

  def parseChevronPack(iter: ScrambleIterator): Result[Option[Vector[ITemplexPT]], IParseError] = {
    iter.peek() match {
      case Some(AngledLE(range, inners)) => {
        iter.advance()
        Ok(
          Some(
            U.map[ScrambleLE, ITemplexPT](
              inners.elements,
              el => {
                new TemplexParser(interner, keywords).parseTemplex(new ScrambleIterator(el, 0, el.elements.length)) match {
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
          ast.TemplateArgsP(RangeL(operatorBegin, iter.getPos()), templateArgs)
        }
      }

    val resultPE =
      exprSoFar match {
        case LookupPE(name, None) => ast.LookupPE(name, Some(templateArgs))
        case _ => return Err(BadTemplateCallee(operatorBegin))
      }

    Ok(Some(resultPE))
  }

  def parsePack(iter: ScrambleIterator): Result[Option[Vector[IExpressionPE]], IParseError] = {
    val parendLE =
      iter.peek() match {
        case Some(p @ ParendLE(_, _)) => iter.advance(); p
        case _ => return Ok(None)
      }

    val elements =
      U.map[ScrambleLE, IExpressionPE](parendLE.contents.elements, scramble => {
        parseExpression(scramble) match {
          case Err(e) => return Err(e)
          case Ok(expr) => expr
        }
      })
    Ok(Some(elements.toVector))
  }

  def parseSquarePack(iter: ScrambleIterator): Result[Option[Vector[IExpressionPE]], IParseError] = {
    val squaredLE =
      iter.peek() match {
        case Some(p @ ParendLE(_, _)) => iter.advance(); p
        case None => return Ok(None)
      }
    val elementsP =
      U.map[ScrambleLE, IExpressionPE](
        squaredLE.contents.elements,
        node => {
          parseExpression(node) match {
            case Err(e) => return Err(e)
            case Ok(expr) => expr
          }
        })
    Ok(Some(elementsP.toVector))
  }

  def parseBracePack(iter: ScrambleIterator): Result[Option[Vector[IExpressionPE]], IParseError] = {
    iter.peek() match {
      case Some(SquaredLE(_, contents)) => {
        val elements =
          contents.elements.map(line => {
            parseExpression(line) match {
              case Err(e) => return Err(e)
              case Ok(expr) => expr
            }
          })
        Ok(Some(elements.toVector))
      }
      case _ => Ok(None)
    }
  }

  def parseTupleOrSubExpression(iter: ScrambleIterator): Result[IExpressionPE, IParseError] = {
    vimpl()
//    val begin = iter.getPos()
//    if (!iter.trySkipWord("\\s*\\(")) {
//      return Ok(None)
//    }
//
//
//    if (iter.trySkipWord("\\)")) {
//      return Ok(Some(TuplePE(RangeL(begin, iter.getPos()), Vector())))
//    }
//
//    val elements = new mutable.ArrayBuffer[IExpressionPE]()
//
//    val expr =
//      parseExpression(iter) match {
//        case Err(e) => return Err(e)
//        case Ok(expr) => expr
//      }
//
//
//    // One-element tuple
//    if (iter.trySkipWord(",\\s*\\)")) {
//      return Ok(Some(ast.TuplePE(RangeL(begin, iter.getPos()), Vector(expr))))
//    }
//
//    // Just one expression, no comma at end, so its some parens just for
//    // a sub expression.
//    if (iter.trySkipWord("\\s*\\)")) {
//      return Ok(Some(SubExpressionPE(RangeL(begin, iter.getPos()), expr)))
//    }
//
//    elements += expr
//
//    if (!iter.trySkipWord("\\s*,")) {
//      return Err(UnknownTupleOrSubExpression(iter.getPos()))
//    }
//
//
//    while (!iter.trySkipWord("\\s*\\)")) {
//      val expr =
//        parseExpression(iter) match {
//          case Err(e) => return Err(e)
//          case Ok(expr) => expr
//        }
//      elements += expr
//
//      if (iter.peek(() => "^\\s*,\\s*\\)")) {
//        val found = iter.trySkipWord("\\s*,")
//        vassert(found)
//        vassert(iter.peek(() => "^\\s*\\)"))
//      }
//
//      iter.trySkipWord(",")
//
//    }
//    Ok(Some(TuplePE(RangeL(begin, iter.getPos()), elements.toVector)))
  }

  def parseExpressionDataElement(iter: ScrambleIterator): Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()
    if (iter.trySkipSymbol('…')) {
      return Ok(Some(ConstantIntPE(RangeL(begin, iter.getPos()), 0, 32)))
    }

    iter.peek(2) match {
      case Array(Some(SymbolLE(_, '\'')), Some(WordLE(range, str))) => {
        iter.advance()
        iter.advance()
        return parseExpressionDataElement(iter)
      }
      case _ =>
    }

    // First, get the prefixes out of the way, such as & not etc.
    // Then we'll parse the atom and suffixes (.moo, ..5, etc.) and
    // *then* wrap those in the prefixes, so we get e.g. not(x.moo)
    if (iter.trySkipWord(keywords.NOT)) {
      val innerPE =
        parseExpressionDataElement(iter) match {
          case Err(e) => return Err(e)
          case Ok(None) => vwat()
          case Ok(Some(x)) => x
        }
      return Ok(Some(NotPE(RangeL(begin, iter.getPos()), innerPE)))
    }

    parseIfLadder(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(e)) => return Ok(Some(e))
      case Ok(None) =>
    }

    parseUnlet(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(Some(x))
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
              iter.advance()
              Some(BorrowP)
            }
          }
        }
        case _ => None
      }
    maybeTargetOwnership match {
      case Some(targetOwnership) => {
        val innerPE =
          parseExpressionDataElement(iter) match {
            case Err(err) => return Err(err)
            case Ok(None) => return Err(UnrecognizableExpressionAfterAugment(iter.getPos()))
            case Ok(Some(e)) => e
          }
        val augmentPE = ast.AugmentPE(RangeL(begin, iter.getPos()), targetOwnership, innerPE)
        return Ok(Some(augmentPE))
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
    parseAtomAndTightSuffixes(iter) match {
      case Err(err) => return Err(err)
      case Ok(Some(e)) => return Ok(Some(e))
      case Ok(None) =>
    }

    Ok(None)
  }

  def parseBracedBody(iter: ScrambleIterator):  Result[BlockPE, IParseError] = {
    vimpl()
//    if (!iter.trySkipWord("\\s*\\{")) {
//      return Ok(None)
//    }
//
//    val bodyBegin = iter.getPos()
//    val bodyContents =
//      parseBlockContents(iter) match {
//        case Err(e) => return Err(e)
//        case Ok(x) => x
//      }
//    if (!iter.trySkipWord("\\}")) {
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

  def parseLambda(iter: ScrambleIterator): Result[IExpressionPE, IParseError] = {
    vimpl()
//    val begin = iter.getPos()
//    val maybeParams =
//      parseSingleArgLambdaBegin(iter) match {
//        case Some(p) => Some(p)
//        case None => {
//          parseMultiArgLambdaBegin(iter) match {
//            case Some(p) => Some(p)
//            case None => {
//              if (stopBefore == StopBeforeOpenBrace && iter.peek(() => "^\\s*\\{")) {
//                return Ok(None)
//              } else {
//                None
//              }
//            }
//          }
//        }
//      }
//
//
//    val body =
//      parseBracedBody(iter) match {
//        case Err(e) => return Err(e)
//        case Ok(Some(x)) => x
//        case Ok(None) => {
//          if (maybeParams.isEmpty) {
//            // If theres no params and no braces, we're done here
//            return Ok(None)
//          }
//          val bodyBegin = iter.getPos()
//          parseExpression(iter) match {
//            case Err(e) => return Err(e)
//            case Ok(x) => BlockPE(RangeL(bodyBegin, iter.getPos()), x)
//          }
//        }
//      }
//    val lam =
//      LambdaPE(
//        None,
//        FunctionP(
//          RangeL(begin, iter.getPos()),
//          FunctionHeaderP(
//            RangeL(begin, iter.getPos()),
//            None,
//            Vector(),
//            None,
//            None,
//            maybeParams,
//            FunctionReturnP(RangeL(iter.getPos(), iter.getPos()), None, None)),
//          Some(body)))
//    Ok(Some(lam))
  }

  def parseArray(originalIter: ScrambleIterator): Result[IExpressionPE, IParseError] = {
    vimpl()
//    val tentativeIter = originalIter.clone()
//    val begin = tentativeIter.getPos()
//
//    val mutability =
//      if (tentativeIter.trySkipWord("#")) {
//        MutabilityPT(RangeL(begin, tentativeIter.getPos()), ImmutableP)
//      } else {
//        MutabilityPT(RangeL(begin, tentativeIter.getPos()), MutableP)
//      }
//
//    if (!tentativeIter.trySkipWord("\\[")) {
//      return Ok(None)
//    }
//    originalIter.skipTo(tentativeIter.position)
//    val iter = originalIter
//
//    val size =
//      if (iter.trySkipWord("#")) {
//        val sizeTemplex =
//          if (iter.peek(() => "^]")) {
//            None
//          } else {
//            new TemplexParser().parseTemplex(iter) match {
//              case Err(e) => return Err(e)
//              case Ok(e) => Some(e)
//            }
//          }
//        StaticSizedP(sizeTemplex)
//      } else {
//        RuntimeSizedP
//      }
//    if (!iter.trySkipWord("\\]")) {
//      return Err(BadArraySizerEnd(iter.getPos()))
//    }
//
//    val tyype =
//      if (!iter.peek(() => "^\\s*[\\[\\(]")) {
//        new TemplexParser().parseTemplex(iter) match {
//          case Err(e) => return Err(e)
//          case Ok(e) => Some(e)
//        }
//      } else {
//        None
//      }
//
//    val (initializingByValues, args) =
//      if (iter.peek(() => "^\\[")) {
//        val values =
//          parseSquarePack(iter) match {
//            case Ok(None) => vwat()
//            case Ok(Some(e)) => e
//            case Err(e) => return Err(e)
//          }
//        (true, values)
//      } else if (iter.peek(() => "^\\(")) {
//        val args =
//          parsePack(iter) match {
//            case Ok(None) => vwat()
//            case Ok(Some(e)) => e
//            case Err(e) => return Err(e)
//          }
//        (false, args)
//      } else {
//        return Err(BadArraySpecifier(iter.getPos()))
//      }
//    val arrayPE =
//      ConstructArrayPE(
//        RangeL(begin, iter.getPos()),
//        tyype,
//        Some(mutability),
//        None,
//        size,
//        initializingByValues,
//        args)
//    Ok(Some(arrayPE))
  }

  // Returns the index we stopped at, which will be either
  // the end of the array or one past endIndexInclusive.
  def descramble(
    elements: Array[IExpressionElement],
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
          case s if s == keywords.AND => {
            AndPE(
              RangeL(leftOperand.range.begin, leftOperand.range.end),
              leftOperand,
              BlockPE(rightOperand.range, rightOperand))
          }
          case s if s == keywords.OR => {
            OrPE(
              RangeL(leftOperand.range.begin, leftOperand.range.end),
              leftOperand,
              BlockPE(rightOperand.range, rightOperand))
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
      iter.peek() match {
        case None => return Ok(None)
        case Some(WordLE(range, str)) => NameP(range, str)
        case Some(_) => return Err(BadBinaryFunctionName(iter.getPos()))
      }
    iter.advance()
    Ok(Some(name))
  }

  def atExpressionEnd(iter: ScrambleIterator): Boolean = {
    !iter.hasNext
//    return Parser.atEnd(iter) || iter.peek(() => "^\\s*;")
  }
}
