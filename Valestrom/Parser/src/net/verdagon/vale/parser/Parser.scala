package net.verdagon.vale.parser

import net.verdagon.vale.{Err, FileCoordinateMap, INamespaceResolver, IProfiler, NamespaceCoordinate, NullProfiler, Ok, Result, repeatStr, vassert, vassertSome, vfail, vimpl, vwat}
import net.verdagon.von.{JsonSyntax, VonPrinter}

import scala.collection.immutable.{List, Map}
import scala.collection.mutable
import scala.util.matching.Regex
import scala.util.parsing.input.{CharSequenceReader, Position}

sealed trait IParseResult[T] {
  def get(): T
}
case class ParseFailure[T](error: IParseError) extends IParseResult[T] {
  override def get(): T = { vfail() }
}
case class ParseSuccess[T](result: T) extends IParseResult[T] {
  override def get(): T = result
}

object Parser {
  case class ParsingIterator(code: String, var position: Int = 0) {
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

    val program0 = FileP(topLevelThings.toList)
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
        case ParseSuccess(result) => BlockPE(Range(condBegin, iter.getPos()), result)
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
        BlockPE(Range(condBegin, condEnd), List(condition)),
        BlockPE(Range(bodyBegin, bodyEnd), body)))
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
        Some(BlockPE(Range(elseBegin, elseEnd), elseBody))
      } else {
        None
      }

    val ifLadderEnd = iter.getPos()

    val finalElse: BlockPE =
      maybeElseBlock match {
        case None => BlockPE(Range(ifLadderEnd, ifLadderEnd), List(VoidPE(Range(ifLadderEnd, ifLadderEnd))))
        case Some(block) => block
      }
    val rootElseBlock =
      ifElses.foldRight(finalElse)({
        case ((condBlock, thenBlock), elseBlock) => {
          BlockPE(
            Range(condBlock.range.begin, thenBlock.range.end),
            List(
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
      iter.consumeWithCombinator(CombinatorParsers.expression) match {
        case Ok(result) => result
        case Err(cpe) => return ParseFailure(BadMutDestinationError(iter.getPos(), cpe))
      }
    iter.consumeWhitespace()
    if (!iter.tryConsume("^=".r)) {
      return ParseFailure(BadMutateEqualsError(iter.position))
    }
    iter.consumeWhitespace()
    val source =
      iter.consumeWithCombinator(CombinatorParsers.expression) match {
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
      iter.consumeWithCombinator(CombinatorParsers.expression) match {
        case Ok(result) => result
        case Err(cpe) => return ParseFailure(BadLetSourceError(iter.getPos(), cpe))
      }
    val letEnd = iter.getPos()

    pattern.capture match {
      case Some(CaptureP(_, LocalNameP(name), _)) => vassert(name.str != "set" && name.str != "mut")
      case _ =>
    }

    iter.consumeWhitespace()
    if (!iter.tryConsume("^;".r)) { return ParseFailure(BadLetEndError(iter.getPos())) }

    ParseSuccess(LetPE(Range(letBegin, letEnd), None, pattern, source))
  }

  private def parseBadLet(iter: ParsingIterator): IParseResult[BadLetPE] = {
    if (!iter.peek(CombinatorParsers.badLetBegin)) {
      vwat()
    }
    iter.consumeWhitespace()
    val badLetBegin = iter.getPos()
    val pattern =
      iter.consumeWithCombinator(CombinatorParsers.expressionLevel5) match {
        case Ok(result) => result
        case Err(cpe) => return ParseFailure(BadLetDestinationError(iter.getPos(), cpe))
      }
    iter.consumeWhitespace()
    if (!iter.tryConsume("^=".r)) {
      return ParseFailure(BadLetEqualsError(iter.position))
    }
    iter.consumeWhitespace()
    val source =
      iter.consumeWithCombinator(CombinatorParsers.expression) match {
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
    if (!iter.tryConsume("^\\(".r)) {
      return ParseFailure(BadStartOfIfCondition(iter.position))
    }
    val condition =
      iter.consumeWithCombinator(CombinatorParsers.let) match {
        case Ok(result) => result
        case Err(cpe) => {
          iter.consumeWithCombinator(CombinatorParsers.badLet) match {
            case Ok(result) => result
            case Err(cpe) => {
              iter.consumeWithCombinator(CombinatorParsers.expression) match {
                case Ok(result) => result
                case Err(cpe) => return ParseFailure(BadIfCondition(iter.getPos(), cpe))
              }
            }
          }
        }
      }
    val condEnd = iter.getPos()
    if (!iter.tryConsume("^\\)".r)) {
      return ParseFailure(BadEndOfIfCondition(iter.position))
    }
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
        BlockPE(Range(condBegin, condEnd), List(condition)),
        BlockPE(Range(bodyBegin, bodyEnd), body)))
  }

  private def parseBlockContents(iter: ParsingIterator): IParseResult[List[IExpressionPE]] = {
    val statements = new mutable.MutableList[IExpressionPE]

    // Just ignore this if we see it (as a hack for the syntax highlighter).
    iter.tryConsume("^\\s*\\.\\.\\.".r)
    iter.tryConsume("^\\s*;".r)

    while (!iter.peek("^\\s*\\}".r) && !iter.peek("^\\s*\\)".r)) {
      if (iter.peek("^\\s*\\)".r) || iter.peek("^\\s*\\]".r)) {
        return ParseFailure(BadStartOfStatementError(iter.position))
      }

      iter.consumeWhitespace()

      if (iter.peek("^while\\s".r)) {
        parseWhile(iter) match {
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
          case Err(err) => return ParseFailure(BadEachError(iter.getPos(), err))
          case Ok(result) => {
            statements += result
            if (iter.peek("^\\s*\\}".r)) {
              statements += VoidPE(Range(iter.getPos(), iter.getPos()))
            }
          }
        }
      } else if (iter.peek("^if\\s".r)) {
        parseIfLadder(iter) match {
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
          case Err(err) => return ParseFailure(BadBlockError(iter.getPos(), err))
          case Ok(result) => {
            statements += result
            if (iter.peek("^\\s*\\}".r)) {
              statements += VoidPE(Range(iter.getPos(), iter.getPos()))
            }
          }
        }
      } else if (iter.peek("^=\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.statementOrResult) match {
          case Err(err) => return ParseFailure(BadResultError(iter.getPos(), err))
          case Ok(result) => {
            statements += result._1
            if (!iter.peek("^\\s*[})]"r)) {
              return ParseFailure(StatementAfterResult(iter.position))
            }
          }
        }
      } else if (iter.peek("^ret\\s".r)) {
        iter.consumeWithCombinator(CombinatorParsers.statementOrResult) match {
          case Err(err) => return ParseFailure(BadReturnError(iter.getPos(), err))
          case Ok(result) => {
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
          case Err(err) => return ParseFailure(BadDestructError(iter.getPos(), err))
          case Ok(result) => statements += result
        }
        // mut must come before let, or else set a = 3; is interpreted as a var named `mut` of type `a`.
      } else if (iter.peek("^(set|mut)\\s".r)) {
        parseMut(iter) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(expression) => {
            val werePreviousStatements = statements.nonEmpty
            statements += expression

            endExpression(iter, werePreviousStatements) match {
              case Some(Ok(toAdd)) => {
                statements ++= toAdd
                return ParseSuccess(statements.toList)
              }
              case Some(Err(e)) => return ParseFailure(e)
              case None => // continue
            }
          }
        }
      } else if (iter.peek(CombinatorParsers.letBegin)) {
        // let is special in that it _must_ have a semicolon after it,
        // it can't be used as a block result.
        parseLet(iter) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(result) => {
            statements += result
          }
        }
      } else if (iter.peek(CombinatorParsers.badLetBegin)) {
        // let is special in that it _must_ have a semicolon after it,
        // it can't be used as a block result.
        parseBadLet(iter) match {
          case ParseFailure(err) => return ParseFailure(err)
          case ParseSuccess(result) => {
            statements += result
          }
        }
      } else {
        iter.consumeWithCombinator(CombinatorParsers.expression) match {
          case Err(err) => return ParseFailure(BadStandaloneExpressionError(iter.getPos(), err))
          case Ok(expression) => {
            val werePreviousStatements = statements.nonEmpty
            statements += expression

            iter.consumeWhitespace()

            endExpression(iter, werePreviousStatements) match {
              case Some(Ok(toAdd)) => {
                statements ++= toAdd
                return ParseSuccess(statements.toList)
              }
              case Some(Err(e)) => return ParseFailure(e)
              case None => // continue
            }
          }
        }
      }

      iter.consumeWhitespace()
    }

    if (statements.isEmpty) {
      statements += VoidPE(Range(iter.getPos(), iter.getPos()))
    }

    ParseSuccess(statements.toList)
  }

  // If returns Some(Err), then thats what the caller should return
  // If returns Some(Ok(list)), then caller should add that to the end and return
  // If returns None, then continue
  private def endExpression(
    iter: ParsingIterator,
    werePreviousExprs: Boolean
  ): Option[Result[List[IExpressionPE], BadExpressionEnd]] = {
    if (iter.peek("^\\s*;\\s*[)}]".r)) {
      iter.consumeWhitespace()
      if (!iter.tryConsume("^;".r)) {
        vwat()
      }
      iter.consumeWhitespace()
      return Some(Ok(List(VoidPE(Range(iter.getPos(), iter.getPos())))))
    }

    if (iter.peek("^\\s*[)}]".r)) {
      iter.consumeWhitespace()
      if (werePreviousExprs) {
        return Some(Err(BadExpressionEnd(iter.getPos())))
      } else {
        return Some(Ok(List()))
      }
    }

    if (!iter.tryConsume("^\\s*;".r)) {
      return Some(Err(BadExpressionEnd(iter.getPos())))
    }

    None
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
        BlockPE(Range(bodyBegin, bodyEnd), statements.toList)
      } else {
        BlockPE(Range(bodyBegin, bodyEnd), List(VoidPE(Range(bodyBegin, bodyEnd))))
      }

    ParseSuccess(FunctionP(Range(funcBegin, bodyEnd), header, Some(body)))
  }
}

object ParserCompilation {

  def loadAndParse(
    neededModules: List[String],
    resolver: INamespaceResolver[Map[String, String]]):
  (FileCoordinateMap[String], FileCoordinateMap[(FileP, List[(Int, Int)])]) = {
    vassert(neededModules.size == neededModules.distinct.size, "Duplicate modules in: " + neededModules.mkString(", "))

    loadAndParseIteration(neededModules, FileCoordinateMap(Map()), FileCoordinateMap(Map()), resolver)
  }

  def loadAndParseIteration(
    neededModules: List[String],
    alreadyFoundCodeMap: FileCoordinateMap[String],
    alreadyParsedProgramPMap: FileCoordinateMap[(FileP, List[(Int, Int)])],
    resolver: INamespaceResolver[Map[String, String]]):
  (FileCoordinateMap[String], FileCoordinateMap[(FileP, List[(Int, Int)])]) = {
    val neededNamespaceCoords =
      neededModules.map(module => NamespaceCoordinate(module, List())) ++
        alreadyParsedProgramPMap.flatMap({ case (fileCoord, file) =>
          file._1.topLevelThings.collect({
            case TopLevelImportP(ImportP(_, moduleName, namespaceSteps, importeeName)) => {
              NamespaceCoordinate(moduleName.str, namespaceSteps.map(_.str))
            }
          })
        }).toList.flatten.filter(namespaceCoord => {
          !alreadyParsedProgramPMap.moduleToNamespacesToFilenameToContents
            .getOrElse(namespaceCoord.module, Map())
            .contains(namespaceCoord.namespaces)
        })

    if (neededNamespaceCoords.isEmpty) {
      return (alreadyFoundCodeMap, alreadyParsedProgramPMap)
    }

    val neededCodeMapFlat =
      neededNamespaceCoords.flatMap(neededNamespaceCoord => {
        val filepathsAndContents = vassertSome(resolver.resolve(neededNamespaceCoord))
        // Note that filepathsAndContents *can* be empty, see ImportTests.
        List((neededNamespaceCoord.module, neededNamespaceCoord.namespaces, filepathsAndContents))
      })
    val grouped =
      neededCodeMapFlat.groupBy(_._1).mapValues(_.groupBy(_._2).mapValues(_.map(_._3).head))
    val neededCodeMap = FileCoordinateMap(grouped)

    val newProgramPMap =
      neededCodeMap.map({ case (fileCoord, code) =>
        Parser.runParserForProgramAndCommentRanges(code) match {
          case ParseFailure(err) => {
            vwat(ParseErrorHumanizer.humanize(neededCodeMap, fileCoord, err))
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

    val combinedCodeMap = alreadyFoundCodeMap.mergeNonOverlapping(neededCodeMap)
    val combinedProgramPMap = alreadyParsedProgramPMap.mergeNonOverlapping(newProgramPMap)

    loadAndParseIteration(List(), combinedCodeMap, combinedProgramPMap, resolver)
  }
}

class ParserCompilation(
  modulesToBuild: List[String],
  namespaceToContentsResolver: INamespaceResolver[Map[String, String]]) {
  var codeMapCache: Option[FileCoordinateMap[String]] = None
  var vpstMapCache: Option[FileCoordinateMap[String]] = None
  var parsedsCache: Option[FileCoordinateMap[(FileP, List[(Int, Int)])]] = None

  def getCodeMap(): FileCoordinateMap[String] = {
    getParseds()
    codeMapCache.get
  }
  def getParseds(): FileCoordinateMap[(FileP, List[(Int, Int)])] = {
    parsedsCache match {
      case Some(parseds) => parseds
      case None => {
        // Also build the "" module, which has all the builtins
        val (codeMap, programPMap) =
          ParserCompilation.loadAndParse(modulesToBuild, namespaceToContentsResolver)
        codeMapCache = Some(codeMap)
        parsedsCache = Some(programPMap)
        parsedsCache.get
      }
    }
  }

  def getVpstMap(): FileCoordinateMap[String] = {
    vpstMapCache match {
      case Some(vpst) => vpst
      case None => {
        getParseds().map({ case (fileCoord, (programP, commentRanges)) =>
          val von = ParserVonifier.vonifyFile(programP)
          val json = new VonPrinter(JsonSyntax, 120).print(von)
          json
        })
      }
    }
  }
}
