package net.verdagon.vale.parser

import net.verdagon.vale.parser.CombinatorParsers.repeatStr
import net.verdagon.vale.{vassert, vfail, vimpl, vwat}

import scala.collection.mutable
import scala.util.matching.Regex
import scala.util.parsing.input.{CharSequenceReader, Position}

sealed trait IParseResult[T]
case class ParseFailure[T](pos: Pos, message: String) extends IParseResult[T]
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

    def getPos(): Pos = {
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
        case CombinatorParsers.NoSuccess(msg, next) => return ParseFailure(getPos(), msg + " when trying to parse: " + code.slice(position, code.length).trim().split("\\n")(0))
        case CombinatorParsers.Success(result, rest) => {
          skipTo(rest.offset)
          ParseSuccess(result)
        }
      }
    }
  }

  def runParserForProgramAndCommentRanges(codeWithComments: String): IParseResult[(Program0, List[(Int, Int)])] = {
    val regex = "(//[^\\r\\n]*|«\\w+»)".r
    val commentRanges = regex.findAllMatchIn(codeWithComments).map(mat => (mat.start, mat.end)).toList
    var code = codeWithComments
    commentRanges.foreach({ case (begin, end) =>
      code = code.substring(0, begin) + repeatStr(" ", (end - begin)) + code.substring(end)
    })
    val codeWithoutComments = code

    runParser(codeWithoutComments) match {
      case f @ ParseFailure(pos, msg) => ParseFailure(pos, msg)
      case ParseSuccess(program0) => ParseSuccess(program0, commentRanges)
    }
  }

  def runParser(codeWithoutComments: String): IParseResult[Program0] = {
    val topLevelThings = new mutable.MutableList[ITopLevelThing]()

    val iter = ParsingIterator(codeWithoutComments, 0)
    iter.consumeWhitespace()

    while (!iter.atEnd()) {
      if (iter.peek("^struct\\b".r)) {
        parseStruct(iter) match {
          case ParseFailure(pos, err) => return ParseFailure(pos, err)
          case ParseSuccess(result) => topLevelThings += TopLevelStruct(result)
        }
      } else if (iter.peek("^interface\\b".r)) {
        parseInterface(iter) match {
          case ParseFailure(pos, err) => return ParseFailure(pos, err)
          case ParseSuccess(result) => topLevelThings += TopLevelInterface(result)
        }
      } else if (iter.peek("^impl\\b".r)) {
        parseImpl(iter) match {
          case ParseFailure(pos, err) => return ParseFailure(pos, err)
          case ParseSuccess(result) => topLevelThings += TopLevelImpl(result)
        }
      } else if (iter.peek("^fn\\b".r)) {
        parseFunction(iter) match {
          case ParseFailure(pos, err) => return ParseFailure(pos, err)
          case ParseSuccess(result) => topLevelThings += TopLevelFunction(result)
        }
      } else {
        vfail("Couldn't parse, at: " + codeWithoutComments.slice(iter.position, codeWithoutComments.length))
      }
      iter.consumeWhitespace()
    }

    val program0 = Program0(topLevelThings.toList)
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
        case ParseFailure(pos, msg) => return ParseFailure(pos, msg)
        case ParseSuccess(result) => result
      }
    iter.consumeWhitespace()
    if (iter.tryConsume("^;".r)) {
      return ParseSuccess(FunctionP(Range(funcBegin, iter.getPos()), header, None))
    }
    val bodyBegin = iter.getPos()
    if (!iter.tryConsume("^\\{".r)) {
      return ParseFailure(iter.getPos(), "Expected `;` to note no function body, or `{` to start a function body!");
    }
    iter.consumeWhitespace()

    val statements = new mutable.MutableList[IExpressionPE]
    var alreadyFoundResultOrReturn = false

    while (!iter.peek("^\\}".r)) {
      if (iter.peek("^\\)".r)) {
        return ParseFailure(iter.getPos(), "Expected `}` but found `)`")
      }
      if (iter.peek("^\\]".r)) {
        return ParseFailure(iter.getPos(), "Expected `}` but found `]`")
      }
      if (alreadyFoundResultOrReturn) {
        return ParseFailure(iter.getPos(), "Already encountered a result statement or return statement!")
      }

      if (iter.peek("^while\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.whiile) match {
          case ParseFailure(pos, msg) => return ParseFailure(pos, msg)
          case ParseSuccess(result) => {
            statements += result
            if (iter.peek("^\\s*\\}".r)) {
              statements += VoidPE(Range(iter.getPos(), iter.getPos()))
            }
          }
        }
      } else if (iter.peek("^(eachI|each)\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.eachOrEachI) match {
          case ParseFailure(pos, msg) => return ParseFailure(pos, msg)
          case ParseSuccess(result) => {
            statements += result
            if (iter.peek("^\\s*\\}".r)) {
              statements += VoidPE(Range(iter.getPos(), iter.getPos()))
            }
          }
        }
      } else if (iter.peek("^if\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.ifLadder) match {
          case ParseFailure(pos, msg) => return ParseFailure(pos, msg)
          case ParseSuccess(result) => {
            statements += result
            if (iter.peek("^\\s*\\}".r)) {
              statements += VoidPE(Range(iter.getPos(), iter.getPos()))
            }
          }
        }
      } else if (iter.peek("^block\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.blockStatement) match {
          case ParseFailure(pos, msg) => return ParseFailure(pos, msg)
          case ParseSuccess(result) => {
            statements += result
            if (iter.peek("^\\s*\\}".r)) {
              statements += VoidPE(Range(iter.getPos(), iter.getPos()))
            }
          }
        }
      } else if (iter.peek("^=\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.statementOrResult) match {
          case ParseFailure(pos, msg) => return ParseFailure(pos, msg)
          case ParseSuccess(result) => {
            statements += result._1
            if (!iter.peek("^\\s*\\}"r)) {
              return ParseFailure(iter.getPos(), "Result statement must be the last in the block!")
            }
          }
        }
      } else if (iter.peek("^ret\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.statementOrResult) match {
          case ParseFailure(pos, msg) => return ParseFailure(pos, msg)
          case ParseSuccess(result) => {
            statements += result._1
            if (!iter.peek("^\\s*\\}"r)) {
              return ParseFailure(iter.getPos(), "Return statement must be the last in the block!")
            }
          }
        }
      } else if (iter.peek("^destruct\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.destruct) match {
          case ParseFailure(pos, msg) => return ParseFailure(pos, msg)
          case ParseSuccess(result) => {
            statements += result
            // destruct is special in that it _must_ have a semicolon after it,
            // it can't be used as a block result.
            if (!iter.tryConsume("^\\s*;".r)) {
              return ParseFailure(iter.getPos(), "Expected `;` after destruct expression, but found: " + iter.code.slice(iter.position, iter.code.length).split("\\n")(0))
            }
          }
        }
        // mut must come before let, or else mut a = 3; is interpreted as a var named `mut` of type `a`.
      } else if (iter.peek("^mut\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.mutate) match {
          case ParseFailure(pos, msg) => return ParseFailure(pos, msg)
          case ParseSuccess(expression) => {
            if (iter.peek("^\\s*;\\s*\\}".r)) {
              if (!iter.tryConsume("^\\s*;" r)) {
                vwat()
              }
              statements ++= List(expression, VoidPE(Range(iter.getPos(), iter.getPos())))
              // continue, and the while loop will break itself
            } else if (iter.peek("^\\s*\\}".r)) {
              statements += expression
              // continue, and the while loop will break itself
            } else if (iter.tryConsume("^\\s*;".r)) {
              statements += expression
              // continue, the while loop will proceed
            } else {
              return ParseFailure(iter.getPos(), "Expected `;` or `}` after expression, but found: " + iter.code.slice(iter.position, iter.code.length).split("\\n")(0))
            }
          }
        }
      } else if (iter.peek(CombinatorParsers.letBegin)) {
        iter.consumeWithCombinator(CombinatorParsers.let) match {
          case ParseFailure(pos, msg) => return ParseFailure(pos, msg)
          case ParseSuccess(result) => {
            result.pattern.capture match {
              case Some(CaptureP(_, LocalNameP(name), _)) => vassert(name.str != "mut")
              case _ =>
            }
            statements += result
            // let is special in that it _must_ have a semicolon after it,
            // it can't be used as a block result.
            if (!iter.tryConsume("^\\s*;".r)) {
              return ParseFailure(iter.getPos(), "Expected `;` after let-expression, but found: " + iter.code.slice(iter.position, iter.code.length).split("\\n")(0))
            }
          }
        }
      } else {
        iter.consumeWithCombinator(CombinatorParsers.expression) match {
          case ParseFailure(pos, msg) => return ParseFailure(pos, msg)
          case ParseSuccess(expression) => {
            if (iter.peek("^\\s*;\\s*\\}".r)) {
              if (!iter.tryConsume("^\\s*;" r)) {
                vwat()
              }
              statements ++= List(expression, VoidPE(Range(iter.getPos(), iter.getPos())))
              // continue, and the while loop will break itself
            } else if (iter.peek("^\\s*\\}".r)) {
              statements += expression
              // continue, and the while loop will break itself
            } else if (iter.tryConsume("^\\s*;".r)) {
              statements += expression
              // continue, the while loop will proceed
            } else {
              return ParseFailure(iter.getPos(), "Expected `;` or `}` after expression, but found: " + iter.code.slice(iter.position, iter.code.length).split("\\n")(0))
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
}
