package net.verdagon.vale.parser

import net.verdagon.vale.{Err, FileCoordinateMap, IPackageResolver, IProfiler, NullProfiler, Ok, PackageCoordinate, Result, repeatStr, vassert, vassertSome, vcurious, vfail, vimpl, vwat}
import net.verdagon.von.{JsonSyntax, VonPrinter}

import scala.collection.immutable.{List, Map}
import scala.collection.mutable
import scala.util.matching.Regex
import scala.util.parsing.input.{CharSequenceReader, Position}

sealed trait IParseResult[+T] {
  def get(): T
}
case class ParseFailure[T](error: IParseError) extends IParseResult[T] {
  override def hashCode(): Int = vcurious();
  override def get(): T = { vfail(error) }
}
case class ParseSuccess[T](result: T) extends IParseResult[T] {
  override def hashCode(): Int = vcurious();
  override def get(): T = result
}

object Parser {
  case class ParsingIterator(code: String, var position: Int = 0) {
    override def hashCode(): Int = vcurious();

    def currentChar(): Char = code.charAt(position)
    def advance() { position = position + 1 }

    def atEnd(): Boolean = { position >= code.length }

    def skipTo(newPosition: Int) = {
      vassert(newPosition >= position)
      position = newPosition
    }

    def getPos(): Int = {
      CombinatorParsers.parse(CombinatorParsers.pos, toReader()) match {
        case CombinatorParsers.NoSuccess(_, _) => vwat()
        case CombinatorParsers.Success(result, _) => result
      }
    }

    def toReader() = new CharSequenceReader(code, position)

    def consumeWhitespace(): Unit = {
      while (!atEnd()) {
        currentChar() match {
          case ' ' =>
          case '\t' =>
          case '\n' =>
          case '\r' =>
          case _ => return
        }
        advance()
      }
    }

    private def at(regex: Regex): Boolean = {
      vassert(regex.pattern.pattern().startsWith("^"))
      regex.findFirstIn(code.slice(position, code.length)).nonEmpty
    }

    def tryConsume(regex: Regex): Boolean = {
      vassert(regex.pattern.pattern().startsWith("^"))
      regex.findFirstIn(code.slice(position, code.length)) match {
        case None => false
        case Some(matchedStr) => {
          skipTo(position + matchedStr.length)
          true
        }
      }
    }

    def peek(regex: Regex): Boolean = at(regex)

    def peek[T](parser: CombinatorParsers.Parser[T]): Boolean = {
      CombinatorParsers.parse(parser, toReader()) match {
        case CombinatorParsers.NoSuccess(msg, next) => false
        case CombinatorParsers.Success(result, rest) => true
      }
    }

    def consumeWithCombinator[T](parser: CombinatorParsers.Parser[T]): Result[T, CombinatorParseError] = {
      CombinatorParsers.parse(parser, toReader()) match {
        case CombinatorParsers.NoSuccess(msg, next) => return Err(CombinatorParseError(position, msg))
        case CombinatorParsers.Success(result, rest) => {
          skipTo(rest.offset)
          Ok(result)
        }
      }
    }
  }

  def runParserForProgramAndCommentRanges(codeWithComments: String): IParseResult[(FileP, Vector[(Int, Int)])] = {
    val regex = "(//[^\\r\\n]*|«\\w+»)".r
    val commentRanges = regex.findAllMatchIn(codeWithComments).map(mat => (mat.start, mat.end)).toVector
    var code = codeWithComments
    commentRanges.foreach({ case (begin, end) =>
      code = code.substring(0, begin) + repeatStr(" ", (end - begin)) + code.substring(end)
    })
    val codeWithoutComments = code

    runParser(codeWithoutComments) match {
      case f @ ParseFailure(err) => ParseFailure(err)
      case ParseSuccess(program0) => ParseSuccess((program0, commentRanges))
    }
  }

  def runParser(codeWithoutComments: String): IParseResult[FileP] = {
    val topLevelThings = new mutable.MutableList[ITopLevelThingP]()

    val iter = ParsingIterator(codeWithoutComments, 0)
    iter.consumeWhitespace()

    while (!iter.atEnd()) {
      if (iter.peek("^struct\\b".r)) {
        parseStruct(iter) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(result) => topLevelThings += TopLevelStructP(result)
        }
      } else if (iter.peek("^interface\\b".r)) {
        parseInterface(iter) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(result) => topLevelThings += TopLevelInterfaceP(result)
        }
      } else if (iter.peek("^impl\\b".r)) {
        parseImpl(iter) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(result) => topLevelThings += TopLevelImplP(result)
        }
      } else if (iter.peek("^export\\b".r)) {
        parseExportAs(iter) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(result) => topLevelThings += TopLevelExportAsP(result)
        }
      } else if (iter.peek("^import\\b".r)) {
        parseImport(iter) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(result) => topLevelThings += TopLevelImportP(result)
        }
      } else if (iter.peek("^fn\\b".r)) {
        parseFunction(iter) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(result) => topLevelThings += TopLevelFunctionP(result)
        }
      } else {
        return ParseFailure(UnrecognizedTopLevelThingError(iter.position))
      }
      iter.consumeWhitespace()
    }

    val program0 = FileP(topLevelThings.toVector)
    ParseSuccess(program0)
  }

  private def parseStruct(iter: ParsingIterator): IParseResult[StructP] = {
    iter.consumeWithCombinator(CombinatorParsers.struct) match {
      case Err(e) => ParseFailure(BadStruct(iter.getPos(), e))
      case Ok(s) => ParseSuccess(s)
    }
  }

  private def parseInterface(iter: ParsingIterator): IParseResult[InterfaceP] = {
    iter.consumeWithCombinator(CombinatorParsers.interface) match {
      case Err(e) => ParseFailure(BadInterface(iter.getPos(), e))
      case Ok(s) => ParseSuccess(s)
    }
  }

  private def parseImpl(iter: ParsingIterator): IParseResult[ImplP] = {
    iter.consumeWithCombinator(CombinatorParsers.impl) match {
      case Err(e) => ParseFailure(BadImpl(iter.getPos(), e))
      case Ok(s) => ParseSuccess(s)
    }
  }

  private def parseExportAs(iter: ParsingIterator): IParseResult[ExportAsP] = {
    iter.consumeWithCombinator(CombinatorParsers.`export`) match {
      case Err(e) => ParseFailure(BadExport(iter.getPos(), e))
      case Ok(s) => ParseSuccess(s)
    }
  }

  private def parseImport(iter: ParsingIterator): IParseResult[ImportP] = {
    iter.consumeWithCombinator(CombinatorParsers.`import`) match {
      case Err(e) => ParseFailure(BadImport(iter.getPos(), e))
      case Ok(s) => ParseSuccess(s)
    }
  }

  private def parseWhile(iter: ParsingIterator): IParseResult[WhilePE] = {
    val whileBegin = iter.getPos()
    if (!iter.tryConsume("^while".r)) {
      vwat()
    }
    iter.consumeWhitespace()
    if (!iter.tryConsume("^\\(".r)) {
      return ParseFailure(BadStartOfWhileCondition(iter.position))
    }
    val condBegin = iter.getPos()
    iter.consumeWhitespace()
    val condition =
      parseBlockContents(iter) match {
        case ParseSuccess(result) => BlockPE(Range(condBegin, iter.getPos()), result.toVector)
        case ParseFailure(cpe) => return ParseFailure(cpe)
      }
    iter.consumeWhitespace()
    val condEnd = iter.getPos()
    if (!iter.tryConsume("^\\)".r)) {
      return ParseFailure(BadEndOfWhileCondition(iter.position))
    }
    iter.consumeWhitespace()
    val bodyBegin = iter.getPos()
    if (!iter.tryConsume("^\\{".r)) {
      return ParseFailure(BadStartOfWhileBody(iter.position))
    }
    iter.consumeWhitespace()
    val body =
      parseBlockContents(iter) match {
        case ParseSuccess(result) => result
        case ParseFailure(cpe) => return ParseFailure(cpe)
      }
    val bodyEnd = iter.getPos()
    iter.consumeWhitespace()
    if (!iter.tryConsume("^\\}".r)) {
      return ParseFailure(BadEndOfWhileBody(iter.position))
    }
    val whileEnd = iter.getPos()

    ParseSuccess(
      WhilePE(
        Range(whileBegin, whileEnd),
        BlockPE(Range(condBegin, condEnd), Vector(condition)),
        BlockPE(Range(bodyBegin, bodyEnd), body.toVector)))
  }

  private def parseForeach(iter: ParsingIterator): IParseResult[EachPE] = {
    val whileBegin = iter.getPos()
    iter.tryConsume("^parallel\\s+".r)
    if (!iter.tryConsume("^foreach".r)) {
      vwat()
    }
    iter.consumeWhitespace()
    val pattern =
      iter.consumeWithCombinator(CombinatorParsers.atomPattern) match {
        case Ok(result) => result
        case Err(cpe) => return ParseFailure(BadLetDestinationError(iter.getPos(), cpe))
      }
    iter.consumeWhitespace()

    if (!iter.tryConsume("\\s+in\\s+".r)) {
      return ParseFailure(BadForeachInError(iter.getPos()))
    }

    val iterableExpr =
      iter.consumeWithCombinator(CombinatorParsers.expression(false)) match {
        case Err(err) => return ParseFailure(BadForeachIterableError(iter.getPos(), err))
        case Ok(expression) => expression
      }

    iter.consumeWhitespace()
    val bodyBegin = iter.getPos()
    if (!iter.tryConsume("^\\{".r)) {
      return ParseFailure(BadStartOfWhileBody(iter.position))
    }
    iter.consumeWhitespace()
    val body =
      parseBlockContents(iter) match {
        case ParseSuccess(result) => result
        case ParseFailure(cpe) => return ParseFailure(cpe)
      }
    val bodyEnd = iter.getPos()
    iter.consumeWhitespace()
    if (!iter.tryConsume("^\\}".r)) {
      return ParseFailure(BadEndOfWhileBody(iter.position))
    }
    val whileEnd = iter.getPos()

    ParseSuccess(
      EachPE(
        Range(whileBegin, whileEnd),
        pattern,
        iterableExpr,
        BlockPE(Range(bodyBegin, bodyEnd), body.toVector)))
  }

  private def parseIfLadder(iter: ParsingIterator): IParseResult[IfPE] = {
    val ifLadderBegin = iter.getPos()

    val rootIf =
      parseIfPart(iter) match {
        case ParseFailure(e) => return ParseFailure(e)
        case ParseSuccess(x) => x
      }

    val ifElses = mutable.MutableList[(BlockPE, BlockPE)]()
    while (iter.peek("^\\s*else\\s+if".r)) {
      iter.consumeWhitespace()
      if (!iter.tryConsume("^\\s*else".r)) { vwat() }
      iter.consumeWhitespace()
      ifElses += (
        parseIfPart(iter) match {
          case ParseFailure(e) => return ParseFailure(e)
          case ParseSuccess(x) => x
        })
    }

    val maybeElseBlock =
      if (iter.tryConsume("^\\s*else".r)) {
        iter.consumeWhitespace()
        if (!iter.tryConsume("^\\{".r)) { return ParseFailure(BadStartOfElseBody(iter.getPos())) }
        val elseBegin = iter.getPos()
        iter.consumeWhitespace()
        val elseBody =
          parseBlockContents(iter) match {
            case ParseSuccess(result) => result
            case ParseFailure(cpe) => return ParseFailure(cpe)
          }
        iter.consumeWhitespace()
        val elseEnd = iter.getPos()
        if (!iter.tryConsume("^\\}".r)) { return ParseFailure(BadEndOfElseBody(iter.getPos())) }
        Some(BlockPE(Range(elseBegin, elseEnd), elseBody.toVector))
      } else {
        None
      }

    val ifLadderEnd = iter.getPos()

    val finalElse: BlockPE =
      maybeElseBlock match {
        case None => BlockPE(Range(ifLadderEnd, ifLadderEnd), Vector(VoidPE(Range(ifLadderEnd, ifLadderEnd))))
        case Some(block) => block
      }
    val rootElseBlock =
      ifElses.foldRight(finalElse)({
        case ((condBlock, thenBlock), elseBlock) => {
          BlockPE(
            Range(condBlock.range.begin, thenBlock.range.end),
            Vector(
              IfPE(
                Range(condBlock.range.begin, thenBlock.range.end),
                condBlock, thenBlock, elseBlock)))
        }
      })
    val (rootConditionLambda, rootThenLambda) = rootIf
    ParseSuccess(
      IfPE(
        Range(ifLadderBegin, ifLadderEnd),
        rootConditionLambda,
        rootThenLambda,
        rootElseBlock))
  }

  private def parseMut(iter: ParsingIterator): IParseResult[MutatePE] = {
    val mutateBegin = iter.getPos()
    if (!iter.tryConsume("^(set|mut)".r)) {
      vwat()
    }
    iter.consumeWhitespace()
    val mutatee =
      iter.consumeWithCombinator(CombinatorParsers.expression(true)) match {
        case Ok(result) => result
        case Err(cpe) => return ParseFailure(BadMutDestinationError(iter.getPos(), cpe))
      }
    iter.consumeWhitespace()
    if (!iter.tryConsume("^=".r)) {
      return ParseFailure(BadMutateEqualsError(iter.position))
    }
    iter.consumeWhitespace()
    val source =
      iter.consumeWithCombinator(CombinatorParsers.expression(true)) match {
        case Ok(result) => result
        case Err(cpe) => return ParseFailure(BadMutSourceError(iter.getPos(), cpe))
      }
    val mutateEnd = iter.getPos()

    ParseSuccess(MutatePE(Range(mutateBegin, mutateEnd), mutatee, source))
  }

  private def parseLet(iter: ParsingIterator): IParseResult[LetPE] = {
    if (!iter.peek(CombinatorParsers.letBegin)) {
      vwat()
    }
    iter.consumeWhitespace()
    val letBegin = iter.getPos()
    val pattern =
      iter.consumeWithCombinator(CombinatorParsers.atomPattern) match {
        case Ok(result) => result
        case Err(cpe) => return ParseFailure(BadLetDestinationError(iter.getPos(), cpe))
      }
    iter.consumeWhitespace()
    if (!iter.tryConsume("^=".r)) {
      return ParseFailure(BadLetEqualsError(iter.position))
    }
    iter.consumeWhitespace()
    val source =
      parseExpression(iter) match {
        case ParseFailure(cpe) => return ParseFailure(BadLetSourceError(iter.getPos(), cpe))
        case ParseSuccess(result) => result
      }
    val letEnd = iter.getPos()

    pattern.capture match {
      case Some(CaptureP(_, LocalNameP(name))) => vassert(name.str != "set" && name.str != "mut")
      case _ =>
    }

    iter.consumeWhitespace()
//    if (!iter.tryConsume("^;".r)) { return ParseFailure(BadLetEndError(iter.getPos())) }

    ParseSuccess(LetPE(Range(letBegin, letEnd), None, pattern, source))
  }

  private def parseBadLet(iter: ParsingIterator): IParseResult[BadLetPE] = {
    if (!iter.peek(CombinatorParsers.badLetBegin)) {
      vwat()
    }
    iter.consumeWhitespace()
    val badLetBegin = iter.getPos()
    val pattern =
      iter.consumeWithCombinator(CombinatorParsers.expressionLevel5(true)) match {
        case Ok(result) => result
        case Err(cpe) => return ParseFailure(BadLetDestinationError(iter.getPos(), cpe))
      }
    iter.consumeWhitespace()
    if (!iter.tryConsume("^=".r)) {
      return ParseFailure(BadLetEqualsError(iter.position))
    }
    iter.consumeWhitespace()
    val source =
      iter.consumeWithCombinator(CombinatorParsers.expression(true)) match {
        case Ok(result) => result
        case Err(cpe) => return ParseFailure(BadLetSourceError(iter.getPos(), cpe))
      }
    val badLetEnd = iter.getPos()

    iter.consumeWhitespace()
    if (!iter.tryConsume("^;".r)) { return ParseFailure(BadLetEndError(iter.getPos())) }

    ParseSuccess(BadLetPE(Range(badLetBegin, badLetEnd)))
  }

  private def parseIfPart(iter: ParsingIterator): IParseResult[(BlockPE, BlockPE)] = {
    if (!iter.tryConsume("^if".r)) {
      vwat()
    }
    iter.consumeWhitespace()
    val condBegin = iter.getPos()
    val condition =
      iter.consumeWithCombinator(CombinatorParsers.let(false)) match {
        case Ok(result) => result
        case Err(cpe) => {
          iter.consumeWithCombinator(CombinatorParsers.badLet(false)) match {
            case Ok(result) => result
            case Err(cpe) => {
              iter.consumeWithCombinator(CombinatorParsers.expression(false)) match {
                case Ok(result) => result
                case Err(cpe) => return ParseFailure(BadIfCondition(iter.getPos(), cpe))
              }
            }
          }
        }
      }
    val condEnd = iter.getPos()
    iter.consumeWhitespace()
    val bodyBegin = iter.getPos()
    if (!iter.tryConsume("^\\{".r)) {
      return ParseFailure(BadStartOfIfBody(iter.position))
    }
    iter.consumeWhitespace()
    val body =
      parseBlockContents(iter) match {
        case ParseSuccess(result) => result
        case ParseFailure(cpe) => return ParseFailure(cpe)
      }
    val bodyEnd = iter.getPos()
    iter.consumeWhitespace()
    if (!iter.tryConsume("^\\}".r)) {
      return ParseFailure(BadEndOfIfBody(iter.position))
    }

    ParseSuccess(
      (
        BlockPE(Range(condBegin, condEnd), Vector(condition)),
        BlockPE(Range(bodyBegin, bodyEnd), body.toVector)))
  }

  private def parseBlockContents(iter: ParsingIterator): IParseResult[Vector[IExpressionPE]] = {
    val statements = new mutable.MutableList[IExpressionPE]

    // Just ignore this if we see it (as a hack for the syntax highlighter).
    iter.tryConsume("^\\s*\\.\\.\\.".r)
    iter.tryConsume("^\\s*;".r)

    while (!iter.peek("^\\s*\\}".r) && !iter.peek("^\\s*\\)".r)) {
      if (iter.peek("^\\s*\\)".r) || iter.peek("^\\s*\\]".r)) {
        return ParseFailure(BadStartOfStatementError(iter.position))
      }

      iter.consumeWhitespace()

      val newStatement =
        parseStatement(iter) match {
          case ParseFailure(error) => return ParseFailure(error)
          case ParseSuccess(newStatement) => newStatement
        }
      val anyPreviousStatements = statements.nonEmpty
      statements += newStatement

      iter.consumeWhitespace()

      maybeAddVoid(iter, newStatement, anyPreviousStatements) match {
        case Err(e) => return ParseFailure(e)
        case Ok(false) => // continue
        case Ok(true) => {
          statements += VoidPE(Range(iter.getPos(), iter.getPos()))
        }
      }
    }

    if (statements.isEmpty) {
      statements += VoidPE(Range(iter.getPos(), iter.getPos()))
    }

    ParseSuccess(statements.toVector)
  }

  private def parseStatement(iter: ParsingIterator):
  IParseResult[IExpressionPE] = {
    if (iter.peek("^while\\s".r)) {
      parseWhile(iter)
    } else if (iter.peek("^(eachI|each)\\s".r)) {
      iter.consumeWithCombinator(CombinatorParsers.eachOrEachI) match {
        case Err(err) => return ParseFailure(BadEachError(iter.getPos(), err))
        case Ok(result) => ParseSuccess(result)
      }
    } else if (iter.peek("^if\\s".r)) {
      parseIfLadder(iter)
    } else if (iter.peek("^foreach\\s".r) || iter.peek("^parallel\\s+foreach\\s".r)) {
      parseForeach(iter)
    } else if (iter.peek("^block\\s".r)) {
      iter.consumeWithCombinator(CombinatorParsers.blockStatement) match {
        case Err(err) => return ParseFailure(BadBlockError(iter.getPos(), err))
        case Ok(result) => ParseSuccess(result)
      }
    } else if (iter.tryConsume("^=\\s+".r)) {
      val begin = iter.getPos()
      parseExpression(iter) match {
        case ParseSuccess(result) => ParseSuccess(ResultPE(Range(begin, iter.getPos()), result))
        case pf @ ParseFailure(_) => pf
      }
    } else if (iter.tryConsume("^ret\\s+".r)) {
      val begin = iter.getPos()
      parseExpression(iter) match {
        case ParseSuccess(result) => ParseSuccess(ReturnPE(Range(begin, iter.getPos()), result))
        case pf @ ParseFailure(_) => pf
      }
    } else if (iter.peek("^destruct\\s".r)) {
      // destruct is special in that it _must_ have a semicolon after it,
      // it can't be used as a block result.
      iter.consumeWithCombinator(CombinatorParsers.destruct <~ CombinatorParsers.optWhite <~ ";") match {
        case Err(err) => return ParseFailure(BadDestructError(iter.getPos(), err))
        case Ok(result) => ParseSuccess(result)
      }
      // mut must come before let, or else set a = 3; is interpreted as a var named `mut` of type `a`.
    } else if (iter.peek("^(set|mut)\\s".r)) {
      parseMut(iter)
    } else if (iter.peek(CombinatorParsers.letBegin)) {
      // let is special in that it _must_ have a semicolon after it,
      // it can't be used as a block result.
      parseLet(iter)
    } else if (iter.peek(CombinatorParsers.badLetBegin)) {
      // let is special in that it _must_ have a semicolon after it,
      // it can't be used as a block result.
      parseBadLet(iter)
    } else {
      iter.consumeWithCombinator(CombinatorParsers.expression(true)) match {
        case Err(err) => return ParseFailure(BadStandaloneExpressionError(iter.getPos(), err))
        case Ok(expression) => ParseSuccess(expression)
      }
    }
  }

  def parseExpression(iter: ParsingIterator): IParseResult[IExpressionPE] = {
    if (iter.peek("^(eachI|each)\\s".r)) {
      iter.consumeWithCombinator(CombinatorParsers.eachOrEachI) match {
        case Err(err) => return ParseFailure(BadEachError(iter.getPos(), err))
        case Ok(result) => ParseSuccess(result)
      }
    } else if (iter.peek("^if\\s".r)) {
      parseIfLadder(iter)
    } else if (iter.peek("^foreach\\s".r) || iter.peek("^parallel\\s+foreach\\s".r)) {
      parseForeach(iter)
    } else if (iter.peek("^(set|mut)\\s".r)) {
      parseMut(iter)
    } else {
      iter.consumeWithCombinator(CombinatorParsers.expression(true)) match {
        case Err(err) => return ParseFailure(BadStandaloneExpressionError(iter.getPos(), err))
        case Ok(expression) => ParseSuccess(expression)
      }
    }
  }

  sealed trait ExpressionResultBehavior
  case object Block extends ExpressionResultBehavior
  case object Expression extends ExpressionResultBehavior
  // Ret or =
  case object Producer extends ExpressionResultBehavior

  private def calcResultBehavior(expr: IExpressionPE): ExpressionResultBehavior = {
    expr match {
      case WhilePE(_, _, _) => Block
      case EachPE(_, _, _, _) => Block
      case IfPE(_, _, _, _) => Block
      case BlockPE(_, _) => Block
      case ReturnPE(_, _) => Producer
      case ResultPE(_, _) => Producer
      case _ => Expression
    }
  }

  // Returns whether we should add a void
  private def maybeAddVoid(
    iter: ParsingIterator,
    precedingStatement: IExpressionPE,
    werePreviousExprs: Boolean
  ): Result[Boolean, BadExpressionEnd] = {

    val resultBehavior = calcResultBehavior(precedingStatement)

    val semicolon = iter.tryConsume("^\\s*;".r)
    val ending = iter.peek("^\\s*[)}]".r)

    (werePreviousExprs, resultBehavior, semicolon, ending) match {
      // Error, no semicolon needed
      case (true, Block, true, true) => Err(BadExpressionEnd(iter.getPos()))
      case (true, Block, true, false) => Err(BadExpressionEnd(iter.getPos()))
      case (false, Block, true, true) => Err(BadExpressionEnd(iter.getPos()))
      case (false, Block, true, false) => Err(BadExpressionEnd(iter.getPos()))

      // error, need semicolon cuz theres another expr comin
      case (false, Expression, false, false) => Err(BadExpressionEnd(iter.getPos()))
      case (false, Producer, false, false) => Err(BadExpressionEnd(iter.getPos()))
      case (true, Expression, false, false) => Err(BadExpressionEnd(iter.getPos()))
      case (true, Producer, false, false) => Err(BadExpressionEnd(iter.getPos()))

      // error, need semicolon because this is a naked expression at the end
      case (true, Expression, false, true) => Err(BadExpressionEnd(iter.getPos()))
      case (true, Producer, false, true) => Err(BadExpressionEnd(iter.getPos()))

      // It'll stop naturally, but add a void.
      case (false, Expression, true, true) => Ok(true)
      case (false, Producer, true, true) => Ok(true)

      // We're ending with = block. It'll stop naturally, don't add a void.
      case (true, Block, false, true) => Ok(false)
      case (false, Block, false, true) => Ok(false)

      // Continue on
      case (true, Expression, true, false) => Ok(false)
      case (true, Producer, true, false) => Ok(false)
      case (true, Block, false, false) => Ok(false)
      case (false, Expression, true, false) => Ok(false)
      case (false, Producer, true, false) => Ok(false)
      case (false, Block, false, false) => Ok(false)
      // It'll stop naturally, and don't add a void.
      case (true, Expression, true, true) => Ok(false)
      case (true, Producer, true, true) => Ok(false)
      case (false, Expression, false, true) => Ok(false)
      case (false, Producer, false, true) => Ok(false)
    }
  }

  private def parseFunction(iter: ParsingIterator): IParseResult[FunctionP] = {
    val funcBegin = iter.getPos()
    val header =
      iter.consumeWithCombinator(CombinatorParsers.topLevelFunctionBegin) match {
        case Err(err) => return ParseFailure(BadFunctionHeaderError(iter.getPos(), err))
        case Ok(result) => result
      }
    iter.consumeWhitespace()
    if (iter.tryConsume("^;".r)) {
      return ParseSuccess(FunctionP(Range(funcBegin, iter.getPos()), header, None))
    }
    val bodyBegin = iter.getPos()
    if (!iter.tryConsume("^('\\w+\\s*)?\\{".r)) {
      return ParseFailure(BadFunctionBodyError(iter.position))
    }
    iter.consumeWhitespace()

    val statements =
      parseBlockContents(iter) match {
        case ParseFailure(err) => return ParseFailure(err)
        case ParseSuccess(result) => result
      }

    if (iter.peek("^\\s*\\)".r)) {
      return ParseFailure(BadStartOfStatementError(iter.getPos()))
    }
    vassert(iter.peek("^\\s*\\}".r))
    iter.consumeWhitespace()
    iter.advance()
    val bodyEnd = iter.getPos()
    val body =
      if (statements.nonEmpty) {
        BlockPE(Range(bodyBegin, bodyEnd), statements.toVector)
      } else {
        BlockPE(Range(bodyBegin, bodyEnd), Vector(VoidPE(Range(bodyBegin, bodyEnd))))
      }

    ParseSuccess(FunctionP(Range(funcBegin, bodyEnd), header, Some(body)))
  }
}

object ParserCompilation {

  def loadAndParse(
    neededPackages: Vector[PackageCoordinate],
    resolver: IPackageResolver[Map[String, String]]):
  Result[(FileCoordinateMap[String], FileCoordinateMap[(FileP, Vector[(Int, Int)])]), FailedParse] = {
    vassert(neededPackages.size == neededPackages.distinct.size, "Duplicate modules in: " + neededPackages.mkString(", "))

//    neededPackages.foreach(x => println("Originally requested package: " + x))

    loadAndParseIteration(neededPackages, FileCoordinateMap(Map()), FileCoordinateMap(Map()), resolver)
  }

  def loadAndParseIteration(
    neededPackages: Vector[PackageCoordinate],
    alreadyFoundCodeMap: FileCoordinateMap[String],
    alreadyParsedProgramPMap: FileCoordinateMap[(FileP, Vector[(Int, Int)])],
    resolver: IPackageResolver[Map[String, String]]):
  Result[(FileCoordinateMap[String], FileCoordinateMap[(FileP, Vector[(Int, Int)])]), FailedParse] = {
    val neededPackageCoords =
      neededPackages ++
        alreadyParsedProgramPMap.flatMap({ case (fileCoord, file) =>
          file._1.topLevelThings.collect({
            case TopLevelImportP(ImportP(_, moduleName, packageSteps, importeeName)) => {
              PackageCoordinate(moduleName.str, packageSteps.map(_.str))
            }
          })
        }).toVector.flatten.filter(packageCoord => {
          !alreadyParsedProgramPMap.moduleToPackagesToFilenameToContents
            .getOrElse(packageCoord.module, Map())
            .contains(packageCoord.packages)
        })

    if (neededPackageCoords.isEmpty) {
      return Ok((alreadyFoundCodeMap, alreadyParsedProgramPMap))
    }
//    println("Need packages: " + neededPackageCoords.mkString(", "))

    val neededCodeMapFlat =
      neededPackageCoords.flatMap(neededPackageCoord => {
        val filepathsAndContents =
          resolver.resolve(neededPackageCoord) match {
            case None => {
              throw InputException("Couldn't find: " + neededPackageCoord)
            }
            case Some(fac) => fac
          }

        // Note that filepathsAndContents *can* be empty, see ImportTests.
        Vector((neededPackageCoord.module, neededPackageCoord.packages, filepathsAndContents))
      })
    val grouped =
      neededCodeMapFlat.groupBy(_._1).mapValues(_.groupBy(_._2).mapValues(_.map(_._3).head))
    val neededCodeMap = FileCoordinateMap(grouped)

    val combinedCodeMap = alreadyFoundCodeMap.mergeNonOverlapping(neededCodeMap)

    val newProgramPMap =
      neededCodeMap.map({ case (fileCoord, code) =>
        Parser.runParserForProgramAndCommentRanges(code) match {
          case ParseFailure(err) => {
            return Err(FailedParse(combinedCodeMap, fileCoord, err))
          }
          case ParseSuccess((program0, commentsRanges)) => {
            val von = ParserVonifier.vonifyFile(program0)
            val vpstJson = new VonPrinter(JsonSyntax, 120).print(von)
            ParsedLoader.load(vpstJson) match {
              case ParseFailure(error) => vwat(ParseErrorHumanizer.humanize(neededCodeMap, fileCoord, error))
              case ParseSuccess(program0) => (program0, commentsRanges)
            }
          }
        }
      })

    val combinedProgramPMap = alreadyParsedProgramPMap.mergeNonOverlapping(newProgramPMap)

    loadAndParseIteration(Vector(), combinedCodeMap, combinedProgramPMap, resolver)
  }
}

class ParserCompilation(
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]]) {
  var codeMapCache: Option[FileCoordinateMap[String]] = None
  var vpstMapCache: Option[FileCoordinateMap[String]] = None
  var parsedsCache: Option[FileCoordinateMap[(FileP, Vector[(Int, Int)])]] = None

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = {
    getParseds() match {
      case Ok(_) => Ok(codeMapCache.get)
      case Err(e) => Err(e)
    }
  }
  def expectCodeMap(): FileCoordinateMap[String] = {
    getCodeMap().getOrDie()
  }

  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[(Int, Int)])], FailedParse] = {
    parsedsCache match {
      case Some(parseds) => Ok(parseds)
      case None => {
        // Also build the "" module, which has all the builtins
        val (codeMap, programPMap) =
          ParserCompilation.loadAndParse(packagesToBuild, packageToContentsResolver) match {
            case Ok((codeMap, programPMap)) => (codeMap, programPMap)
            case Err(e) => return Err(e)
          }
        codeMapCache = Some(codeMap)
        parsedsCache = Some(programPMap)
        Ok(parsedsCache.get)
      }
    }
  }
  def expectParseds(): FileCoordinateMap[(FileP, Vector[(Int, Int)])] = {
    getParseds() match {
      case Err(FailedParse(codeMap, fileCoord, err)) => {
        vfail(ParseErrorHumanizer.humanize(codeMap, fileCoord, err))
      }
      case Ok(x) => x
    }
  }

  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = {
    vpstMapCache match {
      case Some(vpst) => Ok(vpst)
      case None => {
        getParseds() match {
          case Err(e) => Err(e)
          case Ok(parseds) => {
            Ok(
              parseds.map({ case (fileCoord, (programP, commentRanges)) =>
                val von = ParserVonifier.vonifyFile(programP)
                val json = new VonPrinter(JsonSyntax, 120).print(von)
                json
              }))
          }
        }
      }
    }
  }
  def expectVpstMap(): FileCoordinateMap[String] = {
    getVpstMap().getOrDie()
  }
}

case class InputException(message: String) extends Throwable {
  override def hashCode(): Int = vcurious();
  override def toString: String = message
}
