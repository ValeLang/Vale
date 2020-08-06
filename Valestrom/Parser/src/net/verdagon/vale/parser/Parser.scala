package net.verdagon.vale.parser

import net.verdagon.vale.parser.CombinatorParsers.repeatStr
import net.verdagon.vale.{vassert, vfail, vimpl, vwat}

import scala.collection.mutable
import scala.util.matching.Regex
import scala.util.parsing.input.{CharSequenceReader, Position}

sealed trait IParseResult[T]
case class ParseFailure[T](error: IParseError) extends IParseResult[T]
case class ParseSuccess[T](result: T) extends IParseResult[T]

object Parser {
  case class ParsingIterator(code: String, var position: Int = 0) {
    def currentChar(): Char = code.charAt(position)
    def advance() { position = position + 1 }

    def atEnd(): Boolean = { position >= code.length }

    def skipTo(newPosition: Int) = {
      vassert(newPosition > position)
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

//    private def at(str: String): Boolean = {
//      code.slice(position, position + str.length) == str
//    }

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

//    def peek(str: String): Boolean = at(str)

    def peek(str: Regex): Boolean = at(str)

    def peek[T](parser: CombinatorParsers.Parser[T]): Boolean = {
      CombinatorParsers.parse(parser, toReader()) match {
        case CombinatorParsers.NoSuccess(msg, next) => false
        case CombinatorParsers.Success(result, rest) => true
      }
    }

    def consumeWithCombinator[T](parser: CombinatorParsers.Parser[T]): IParseResult[T] = {
      CombinatorParsers.parse(parser, toReader()) match {
        case CombinatorParsers.NoSuccess(msg, next) => return ParseFailure(CombinatorParseError(position, msg))
        case CombinatorParsers.Success(result, rest) => {
          skipTo(rest.offset)
          ParseSuccess(result)
        }
      }
    }
  }

  def runParserForProgramAndCommentRanges(codeWithComments: String): IParseResult[(FileP, List[(Int, Int)])] = {
    val regex = "(//[^\\r\\n]*|«\\w+»)".r
    val commentRanges = regex.findAllMatchIn(codeWithComments).map(mat => (mat.start, mat.end)).toList
    var code = codeWithComments
    commentRanges.foreach({ case (begin, end) =>
      code = code.substring(0, begin) + repeatStr(" ", (end - begin)) + code.substring(end)
    })
    val codeWithoutComments = code

    runParser(codeWithoutComments) match {
      case f @ ParseFailure(err) => ParseFailure(err)
      case ParseSuccess(program0) => ParseSuccess(program0, commentRanges)
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

    val program0 = FileP(topLevelThings.toList)
    ParseSuccess(program0)
  }

  private def parseStruct(iter: ParsingIterator): IParseResult[StructP] = {
    iter.consumeWithCombinator(CombinatorParsers.struct)
  }

  private def parseInterface(iter: ParsingIterator): IParseResult[InterfaceP] = {
    iter.consumeWithCombinator(CombinatorParsers.interface)
  }

  private def parseImpl(iter: ParsingIterator): IParseResult[ImplP] = {
    iter.consumeWithCombinator(CombinatorParsers.impl)
  }

  private def parseFunction(iter: ParsingIterator): IParseResult[FunctionP] = {
    val funcBegin = iter.getPos()
    val header =
      iter.consumeWithCombinator(CombinatorParsers.topLevelFunctionBegin) match {
        case ParseFailure(err) => return ParseFailure(err)
        case ParseSuccess(result) => result
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

    val statements = new mutable.MutableList[IExpressionPE]

    while (!iter.peek("^\\}".r)) {
      if (iter.peek("^\\)".r) || iter.peek("^\\]".r)) {
        return ParseFailure(BadStartOfStatementError(iter.position))
      }

      if (iter.peek("^while\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.whiile) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(result) => {
            statements += result
            if (iter.peek("^\\s*\\}".r)) {
              statements += VoidPE(Range(iter.getPos(), iter.getPos()))
            }
          }
        }
      } else if (iter.peek("^(eachI|each)\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.eachOrEachI) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(result) => {
            statements += result
            if (iter.peek("^\\s*\\}".r)) {
              statements += VoidPE(Range(iter.getPos(), iter.getPos()))
            }
          }
        }
      } else if (iter.peek("^if\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.ifLadder) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(result) => {
            statements += result
            if (iter.peek("^\\s*\\}".r)) {
              statements += VoidPE(Range(iter.getPos(), iter.getPos()))
            }
          }
        }
      } else if (iter.peek("^block\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.blockStatement) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(result) => {
            statements += result
            if (iter.peek("^\\s*\\}".r)) {
              statements += VoidPE(Range(iter.getPos(), iter.getPos()))
            }
          }
        }
      } else if (iter.peek("^=\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.statementOrResult) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(result) => {
            statements += result._1
            if (!iter.peek("^\\s*\\}"r)) {
              return ParseFailure(StatementAfterResult(iter.position))
            }
          }
        }
      } else if (iter.peek("^ret\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.statementOrResult) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(result) => {
            statements += result._1
            if (!iter.peek("^\\s*\\}"r)) {
              return ParseFailure(StatementAfterReturn(iter.position))
            }
          }
        }
      } else if (iter.peek("^destruct\\s".r)) {
        // destruct is special in that it _must_ have a semicolon after it,
        // it can't be used as a block result.
        iter.consumeWithCombinator(CombinatorParsers.destruct <~ CombinatorParsers.optWhite <~ ";") match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(result) => statements += result
        }
        // mut must come before let, or else mut a = 3; is interpreted as a var named `mut` of type `a`.
      } else if (iter.peek("^mut\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.mutate) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(expression) => {
            statements += expression
            endExpression(iter) match {
              case ParseFailure(err) => return ParseFailure(err)
              case ParseSuccess(maybeExtra) => statements ++= maybeExtra
            }
          }
        }
      } else if (iter.peek(CombinatorParsers.letBegin)) {
        // let is special in that it _must_ have a semicolon after it,
        // it can't be used as a block result.
        iter.consumeWithCombinator(CombinatorParsers.let <~ CombinatorParsers.optWhite <~ ";") match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(result) => {
            result.pattern.capture match {
              case Some(CaptureP(_, LocalNameP(name), _)) => vassert(name.str != "mut")
              case _ =>
            }
            statements += result
          }
        }
      } else {
        iter.consumeWithCombinator(CombinatorParsers.expression) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(expression) => {
            statements += expression
            endExpression(iter) match {
              case ParseFailure(err) => return ParseFailure(err)
              case ParseSuccess(maybeExtra) => statements ++= maybeExtra
            }
          }
        }
      }

      iter.consumeWhitespace()
    }
    vassert(iter.peek("^\\}".r))
    iter.advance()
    val bodyEnd = iter.getPos()
    val body =
      if (statements.nonEmpty) {
        BlockPE(Range(bodyBegin, bodyEnd), statements.toList)
      } else {
        BlockPE(Range(bodyBegin, bodyEnd), List(VoidPE(Range(bodyBegin, bodyEnd))))
      }

    ParseSuccess(FunctionP(Range(funcBegin, bodyEnd), header, Some(body)))
  }

  // An expression is something that can return a value, such as `foo()` or `mut x = 7`, and doesn't
  // include statements, like `destruct 7;`
  // Returns a possible extra VoidPE to put on the end, if we're ending the block with a semicolon.
  private def endExpression(iter: ParsingIterator): IParseResult[Option[IExpressionPE]] = {
    if (iter.peek("^\\s*;\\s*\\}".r)) {
      if (!iter.tryConsume("^\\s*;" r)) {
        vwat()
      }
      ParseSuccess(Some(VoidPE(Range(iter.getPos(), iter.getPos()))))
      // continue, and the while loop will break itself
    } else if (iter.peek("^\\s*\\}".r)) {
      ParseSuccess(None)
      // continue, and the while loop will break itself
    } else if (iter.tryConsume("^\\s*;".r)) {
      ParseSuccess(None)
    } else {
      ParseFailure(BadExpressionEnd(iter.position))
    }
  }
}
