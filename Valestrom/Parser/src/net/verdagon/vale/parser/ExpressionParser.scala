package net.verdagon.vale.parser

import net.verdagon.vale.parser.Parser.{parseFunctionOrLocalOrMemberName, parseLocalOrMemberName}
import net.verdagon.vale.parser.ast.{AndPE, AugmentPE, BinaryCallPE, BlockPE, BorrowP, BraceCallPE, BreakPE, ConsecutorPE, ConstantBoolPE, ConstantFloatPE, ConstantIntPE, ConstructArrayPE, DestructPE, DotPE, EachPE, ExportAsP, FileP, FunctionCallPE, FunctionHeaderP, FunctionP, FunctionReturnP, IExpressionPE, ITemplexPT, ITopLevelThingP, IfPE, ImmutableP, ImplP, ImportP, InterfaceP, LambdaPE, LetPE, LoadAsBorrowOrIfContainerIsPointerThenPointerP, LoadAsBorrowP, LocalNameDeclarationP, LookupNameP, LookupPE, MagicParamLookupPE, MethodCallPE, MutabilityPT, MutableP, MutatePE, NameP, NotPE, OrPE, OwnP, PackPE, ParamsP, PatternPP, PointerP, RangeP, RangePE, ReadonlyP, ReadwriteP, ReturnPE, RuntimeSizedP, StaticSizedP, StructP, SubExpressionPE, TemplateArgsP, TopLevelExportAsP, TopLevelFunctionP, TopLevelImplP, TopLevelImportP, TopLevelInterfaceP, TopLevelStructP, TuplePE, UseP, VoidPE, WeakP, WhilePE}
import net.verdagon.vale.parser.expressions.ParseString
import net.verdagon.vale.parser.old.CombinatorParsers
import net.verdagon.vale.{Err, FileCoordinateMap, IPackageResolver, Profiler, Ok, PackageCoordinate, Result, repeatStr, vassert, vassertSome, vcurious, vfail, vimpl, vwat}
import net.verdagon.von.{JsonSyntax, VonPrinter}

import scala.collection.immutable.{List, Map}
import scala.collection.mutable
import scala.util.matching.Regex
import scala.util.parsing.input.{CharSequenceReader, Position}


object ExpressionParser {

  private def parseWhile(iter: ParsingIterator): Result[Option[WhilePE], IParseError] = {
    val whileBegin = iter.getPos()
    if (!iter.trySkip("^while\\b".r)) {
      return Ok(None)
    }

    iter.consumeWhitespace()
    val condition =
      parseBlockContents(iter, StopBeforeOpenBrace, true) match {
        case Ok(result) => result
        case Err(cpe) => return Err(cpe)
      }
    iter.consumeWhitespace()

    iter.consumeWhitespace()
    val bodyBegin = iter.getPos()
    if (!iter.trySkip("^\\{".r)) {
      return Err(BadStartOfWhileBody(iter.position))
    }
    iter.consumeWhitespace()
    val body =
      parseBlockContents(iter, StopBeforeCloseBrace, false) match {
        case Ok(result) => result
        case Err(cpe) => return Err(cpe)
      }
    val bodyEnd = iter.getPos()
    iter.consumeWhitespace()
    if (!iter.trySkip("^\\}".r)) {
      return Err(BadEndOfWhileBody(iter.position))
    }
    val whileEnd = iter.getPos()

    Ok(
      Some(
        WhilePE(
          RangeP(whileBegin, whileEnd),
          condition,
          BlockPE(RangeP(bodyBegin, bodyEnd), body))))
  }

  private def parseForeach(
    originalIter: ParsingIterator,
    expectResult: Boolean):
  Result[Option[EachPE], IParseError] = {
    val eachBegin = originalIter.getPos()
    val tentativeIter = originalIter.clone()
    tentativeIter.trySkip("^parallel\\s+".r)
    if (!tentativeIter.trySkip("^foreach".r)) {
      return Ok(None)
    }
    originalIter.skipTo(tentativeIter.position)
    val iter = originalIter
    iter.consumeWhitespace()
    val pattern =
      iter.consumeWithCombinator(CombinatorParsers.atomPattern) match {
        case Ok(result) => result
        case Err(cpe) => return Err(BadLetDestinationError(iter.getPos(), cpe))
      }
    iter.consumeWhitespace()

    val inBegin = iter.getPos()
    if (!iter.trySkip("^in\\b".r)) {
      return Err(BadForeachInError(iter.getPos()))
    }
    val inEnd = iter.getPos()

    iter.consumeWhitespace()

    val iterableExpr =
      parseBlockContents(iter, StopBeforeOpenBrace, true) match {
        case Err(err) => return Err(err)
        case Ok(expression) => expression
      }

    iter.consumeWhitespace()
    val bodyBegin = iter.getPos()
    if (!iter.trySkip("^\\{".r)) {
      return Err(BadStartOfWhileBody(iter.position))
    }
    iter.consumeWhitespace()
    val body =
      parseBlockContents(iter, StopBeforeCloseBrace, expectResult) match {
        case Ok(result) => result
        case Err(cpe) => return Err(cpe)
      }
    val bodyEnd = iter.getPos()
    iter.consumeWhitespace()
    if (!iter.trySkip("^\\}".r)) {
      return Err(BadEndOfWhileBody(iter.position))
    }
    val eachEnd = iter.getPos()

    Ok(
      Some(
        ast.EachPE(
          RangeP(eachBegin, eachEnd),
          pattern,
          RangeP(inBegin, inEnd),
          iterableExpr,
          BlockPE(RangeP(bodyBegin, bodyEnd), body))))
  }

  private def parseIfLadder(iter: ParsingIterator, expectResult: Boolean): Result[Option[IfPE], IParseError] = {
    val ifLadderBegin = iter.getPos()

    if (!iter.peek("^if".r)) {
      return Ok(None)
    }

    val rootIf =
      parseIfPart(iter, expectResult) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    val ifElses = mutable.MutableList[(IExpressionPE, BlockPE)]()
    while (iter.peek("^\\s*else\\s+if".r)) {
      iter.consumeWhitespace()
      if (!iter.trySkip("^\\s*else".r)) { vwat() }
      iter.consumeWhitespace()
      ifElses += (
        parseIfPart(iter, expectResult) match {
          case Err(e) => return Err(e)
          case Ok(x) => x
        })
    }

    val maybeElseBlock =
      if (iter.trySkip("^\\s*else".r)) {
        iter.consumeWhitespace()
        if (!iter.trySkip("^\\{".r)) { return Err(BadStartOfElseBody(iter.getPos())) }
        val elseBegin = iter.getPos()
        iter.consumeWhitespace()
        val elseBody =
          parseBlockContents(iter, StopBeforeCloseBrace, expectResult) match {
            case Ok(result) => result
            case Err(cpe) => return Err(cpe)
          }
        iter.consumeWhitespace()
        val elseEnd = iter.getPos()
        if (!iter.trySkip("^\\}".r)) { return Err(BadEndOfElseBody(iter.getPos())) }
        Some(BlockPE(RangeP(elseBegin, elseEnd), elseBody))
      } else {
        None
      }

    val ifLadderEnd = iter.getPos()

    val finalElse: BlockPE =
      maybeElseBlock match {
        case None => BlockPE(RangeP(ifLadderEnd, ifLadderEnd), VoidPE(RangeP(ifLadderEnd, ifLadderEnd)))
        case Some(block) => block
      }
    val rootElseBlock =
      ifElses.foldRight(finalElse)({
        case ((condBlock, thenBlock), elseBlock) => {
          // We don't check that both branches produce because of cases like:
          //   if blah {
          //     ret 3;
          //   } else {
          //     6
          //   }
          BlockPE(
            RangeP(condBlock.range.begin, thenBlock.range.end),
            ast.IfPE(
              RangeP(condBlock.range.begin, thenBlock.range.end),
              condBlock, thenBlock, elseBlock))
        }
      })
    val (rootConditionLambda, rootThenLambda) = rootIf
    // We don't check that both branches produce because of cases like:
    //   if blah {
    //     ret 3;
    //   } else {
    //     6
    //   }
    Ok(
      Some(
        ast.IfPE(
          RangeP(ifLadderBegin, ifLadderEnd),
          rootConditionLambda,
          rootThenLambda,
          rootElseBlock)))
  }

  private def parseMut(
    iter: ParsingIterator,
    stopBefore: IStopBefore,
    expectResult: Boolean):
  Result[Option[MutatePE], IParseError] = {
    val mutateBegin = iter.getPos()
    if (!iter.trySkip("^(set|mut)\\s".r)) {
      return Ok(None)
    }
    iter.consumeWhitespace()
    val mutatee =
      parseExpression(iter, StopBeforeEquals) match {
        case Err(err) => return Err(err)
        case Ok(expression) => expression
      }
    iter.consumeWhitespace()
    if (!iter.trySkip("^=[^=]".r)) {
      return Err(BadMutateEqualsError(iter.position))
    }
    iter.consumeWhitespace()

    val source =
      // This is for when the articles do:
      //   set x = ...;
      // and we strip out the ... to get
      //   set x =    ;
      // so let's just interpret it as a void
      if (iter.peek("^\\s*;".r)) {
        iter.consumeWhitespace()
        VoidPE(RangeP(iter.getPos(), iter.getPos()))
      } else {
        parseExpression(iter, stopBefore) match {
          case Err(err) => return Err(err)
          case Ok(expression) => expression
        }
      }
    val mutateEnd = iter.getPos()

    Ok(Some(MutatePE(RangeP(mutateBegin, mutateEnd), mutatee, source)))
  }

  private def parseLet(
    originalIter: ParsingIterator,
    stopBefore: IStopBefore,
    expectResult: Boolean):
  Result[Option[LetPE], IParseError] = {
    val tentativeIter = originalIter.clone()

    val letBegin = tentativeIter.getPos()

    val pattern =
      tentativeIter.consumeWithCombinator(CombinatorParsers.atomPattern) match {
        case Ok(result) => result
        case Err(cpe) => return Ok(None)
      }

    tentativeIter.consumeWhitespace()
    // Because == would be a binary == operator
    // and => would be a lambda
    if (!tentativeIter.trySkip("^=[^=>]".r)) {
      return Ok(None)
    }
    tentativeIter.consumeWhitespace()

    // We know that this is a valid pattern, so let's commit to it.
    originalIter.skipTo(tentativeIter.position)
    val iter = originalIter

    pattern.capture match {
      case Some(LocalNameDeclarationP(name)) => vassert(name.str != "set" && name.str != "mut")
      case _ =>
    }


    val source =
      // This is for when the articles do:
      //   x = ...;
      // and we strip out the ... to get
      //   x =    ;
      // so let's just interpret it as a void
      if (iter.peek("^\\s*;".r)) {
        iter.consumeWhitespace()
        VoidPE(RangeP(iter.getPos(), iter.getPos()))
      } else {
        parseExpression(iter, stopBefore) match {
          case Err(err) => return Err(err)
          case Ok(expression) => expression
        }
      }
    val letEnd = iter.getPos()

    iter.consumeWhitespace()
    //    if (!iter.tryConsume("^;".r)) { return Err(BadLetEndError(iter.getPos())) }

    Ok(Some(LetPE(RangeP(letBegin, letEnd), pattern, source)))
  }

  private def parseIfPart(
    iter: ParsingIterator,
    expectResult: Boolean):
  Result[(IExpressionPE, BlockPE), IParseError] = {
    if (!iter.trySkip("^if".r)) {
      vwat()
    }
    iter.consumeWhitespace()
    val condition =
      parseBlockContents(iter, StopBeforeOpenBrace, true) match {
        case Ok(result) => result
        case Err(cpe) => return Err(cpe)
      }
    iter.consumeWhitespace()
    val bodyBegin = iter.getPos()
    if (!iter.trySkip("^\\{".r)) {
      return Err(BadStartOfIfBody(iter.position))
    }
    iter.consumeWhitespace()
    val body =
      parseBlockContents(iter, StopBeforeCloseBrace, expectResult) match {
        case Ok(result) => result
        case Err(cpe) => return Err(cpe)
      }
    val bodyEnd = iter.getPos()
    iter.consumeWhitespace()
    if (!iter.trySkip("^\\}".r)) {
      return Err(BadEndOfIfBody(iter.position))
    }

    Ok(
      (
        condition,
        BlockPE(RangeP(bodyBegin, bodyEnd), body)))
  }

  sealed trait IStopBefore
  case object StopBeforeFileEnd extends IStopBefore
  case object StopBeforeCloseBrace extends IStopBefore
  case object StopBeforeCloseParen extends IStopBefore
  case object StopBeforeEquals extends IStopBefore
  case object StopBeforeCloseSquare extends IStopBefore
  case object StopBeforeCloseChevron extends IStopBefore
  // Such as after the if's condition or the foreach's iterable.
  case object StopBeforeOpenBrace extends IStopBefore

  def parseBlockContents(iter: ParsingIterator, stopBefore: IStopBefore, expectResult: Boolean): Result[IExpressionPE, IParseError] = {
    val statements = new mutable.MutableList[IExpressionPE]

    // Just ignore this if we see it (as a hack for the syntax highlighter).
//    iter.trySkip("^\\s*\\.\\.\\.".r)
    iter.trySkip("^\\s*;".r)


    def endingBlock() =
      iter.atEnd() ||
        iter.peek("^\\s*[\\)\\]\\}]".r) ||
        (stopBefore == StopBeforeOpenBrace && iter.peek("^\\s*\\{".r))

    var continuing = !endingBlock()
    while (continuing) {
      if (Parser.atEnd(iter, stopBefore)) {
        vcurious()
      }

      val newStatement =
        parseStatement(iter, stopBefore, false) match {
          case Err(error) => return Err(error)
          case Ok(newStatement) => newStatement
        }
      statements += newStatement
      iter.consumeWhitespace()
      val hadSemicolon = iter.trySkip("^;".r)
      iter.consumeWhitespace()

      if (endingBlock()) {
        continuing = false;

        if (hadSemicolon) {
          if (newStatement.needsSemicolonBeforeNextStatement) {
            if (newStatement.producesResult()) {
              // Example: { 4 }
              // Last statement, has a semicolon.
              // End with a void.
              statements += VoidPE(RangeP(iter.getPos(), iter.getPos()))
            } else {
              // Example: { a = 4; }
              // Last statement, produces no result but would need a semicolon to continue on.
              // Moot, because we aren't continuing on anyway.
              // End with a void.
              statements += VoidPE(RangeP(iter.getPos(), iter.getPos()))
            }
          } else {
            return Err(DontNeedSemicolon(iter.getPos()))
          }
        } else {
          if (newStatement.needsSemicolonBeforeNextStatement) {
            // No semicolon, but we don't need one, so its fine, continue.
            if (newStatement.producesResult()) {
              // This is the last statement, the expression produces a result, and has no semicolon.
              // Let's end with this statement.
            } else {
              // Example:
              //   if (a) = x { ... }
              //      ^^^^^^^
              // Let's end with this statement.
            }
          } else {
            if (newStatement.producesResult()) {
              // Example, the if in:
              //   exported func main() {
              //     if true { 3 } else { 4 }
              //   }
              // Let's end with this statement, don't add a void afterward.
            } else {
              // Example:
              //   while true { }
              //                 ^
              // Add a void here, because the while loop doesnt produce a result.
              statements += VoidPE(RangeP(iter.getPos(), iter.getPos()))
            }
          }
        }
      } else {
        if (hadSemicolon) {
          if (newStatement.needsSemicolonBeforeNextStatement) {
            // Semicolon ended a statement that wanted one, continue.
          } else {
            return Err(DontNeedSemicolon(iter.getPos()))
          }
        } else {
          if (newStatement.needsSemicolonBeforeNextStatement) {
            return Err(NeedSemicolon(iter.getPos()))
          } else {
            // Example:
            //   while true { } false
            //                 ^
            // Just continue.
          }
        }
      }


      if (continuing && Parser.atEnd(iter, stopBefore)) {
        /// statements += Ok(VoidPE(RangeP(iter.getPos(), iter.getPos())))
        vcurious()
      }
    }

    if (statements.isEmpty) {
      Ok(VoidPE(RangeP(iter.getPos(), iter.getPos())))
    } else if (statements.size == 1) {
      Ok(statements.head)
    } else {
      Ok(ConsecutorPE(statements.toVector))
    }
  }

  private def parseLoneBlock(
    iter: ParsingIterator,
    expectResult: Boolean):
  Result[Option[IExpressionPE], IParseError] = {
    if (!iter.trySkip("^block".r)) {
      return Ok(None)
    }
    iter.consumeWhitespace()
    val begin = iter.getPos()
    if (!iter.trySkip("^\\{".r)) {
      return Err(BadStartOfBlock(iter.position))
    }
    iter.consumeWhitespace()
    val contents =
      parseBlockContents(iter, StopBeforeCloseBrace, expectResult) match {
        case Err(error) => return Err(error)
        case Ok(result) => result
      }
    iter.consumeWhitespace()
    if (!iter.trySkip("^\\}".r)) {
      return Err(BadEndOfBlock(iter.position))
    }
    val end = iter.getPos()
    Ok(Some(BlockPE(RangeP(begin, end), contents)))
  }

  private def parseDestruct(
    iter: ParsingIterator,
    stopBefore: IStopBefore,
    expectResult: Boolean):
  Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()
    if (!iter.trySkip("^destruct\\b".r)) {
      return Ok(None)
    }
    iter.consumeWhitespace()
    parseExpression(iter, stopBefore)
      .map(x => Some(DestructPE(RangeP(begin, iter.getPos()), x)))
  }

  private def parseReturn(
    iter: ParsingIterator,
    stopBefore: IStopBefore,
    expectResult: Boolean):
  Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()
    if (!iter.trySkip("^ret\\b".r)) {
      return Ok(None)
    }
    iter.consumeWhitespace()
    parseExpression(iter, stopBefore)
      .map(x => Some(ReturnPE(RangeP(begin, iter.getPos()), x)))
  }

  private def parseBreak(
    iter: ParsingIterator):
  Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()
    if (!iter.trySkip("^break\\b".r)) {
      return Ok(None)
    }
    Ok(Some(BreakPE(RangeP(begin, iter.getPos()))))
  }

  // expectEnder should be true if we should expect to end with a semicolon or a right brace.
  // expectResult should be true if we should expect the statement to produce a result.
  private[parser] def parseStatement(
    iter: ParsingIterator,
    stopBefore: IStopBefore,
    expectResult: Boolean):
  Result[IExpressionPE, IParseError] = {
    parseWhile(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseIfLadder(iter, expectResult) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseForeach(iter, expectResult) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseLoneBlock(iter, expectResult) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseBreak(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseReturn(iter, stopBefore, expectResult) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseDestruct(iter, stopBefore, expectResult) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseMut(iter, stopBefore, expectResult) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseLet(iter, stopBefore, expectResult) match {
      case Ok(None) =>
      case Ok(Some(result)) => return Ok(result)
      case Err(e) => return Err(e)
    }

    parseExpression(iter, stopBefore)
  }

  val MAX_PRECEDENCE = 6
  val MIN_PRECEDENCE = 1

  sealed trait IExpressionElement
  case class DataElement(expr: IExpressionPE) extends IExpressionElement
  case class BinaryCallElement(symbol: NameP) extends IExpressionElement {
    vassert(MIN_PRECEDENCE == 1)
    vassert(MAX_PRECEDENCE == 6)
    val precedence =
      symbol.str match {
        case ".." => 6
        case "*" | "/" => 5
        case "+" | "-" => 4
        // case _ => 3 Everything else is 3, see end case
        case "<=>" | "<=" | "<" | ">=" | ">" | "===" | "==" | "!=" => 2
        case "and" | "or" => 1
        case _ => 3 // This is so we can have 3 mod 2 == 1
      }
  }

  def parseExpression(iter: ParsingIterator, stopBefore: IStopBefore): Result[IExpressionPE, IParseError] = {
    Profiler.frame(() => {
      //    if (iter.peek("^if\\s".r)) {
      //      parseIfLadder(iter)
      //    } else if (iter.peek("^foreach\\s".r) || iter.peek("^parallel\\s+foreach\\s".r)) {
      //      parseForeach(iter)
      //    } else if (iter.peek("^(set|mut)\\s".r)) {
      //      parseMut(iter)
      //    } else {
      //      parseExpression(allowLambda)(iter)
      //    }

      val elements = mutable.ArrayBuffer[IExpressionElement]()

      var continue = true
      while (continue) {
        val subExpr =
          parseExpressionDataElement(iter, stopBefore) match {
            case Err(error) => return Err(error)
            case Ok(Some(x)) => x
            case Ok(None) => return Err(BadExpressionBegin(iter.getPos()))
          }
        elements += DataElement(subExpr)

        if (atExpressionEnd(iter, stopBefore)) {
          continue = false
        } else {
          parseBinaryCall(iter) match {
            case Err(error) => return Err(error)
            case Ok(None) => continue = false
            case Ok(Some(symbol)) => elements += BinaryCallElement(symbol)
          }
        }
      }

      val (exprPE, _) =
        descramble(elements.toArray, 0, elements.size - 1, MIN_PRECEDENCE)
      Ok(exprPE)
    })
  }

  def parseLookup(iter: ParsingIterator): Option[IExpressionPE] = {
    parseFunctionOrLocalOrMemberName(iter) match {
      case Some(name) => Some(LookupPE(LookupNameP(name), None))
      case None => None
    }
  }

  def parseBoolean(iter: ParsingIterator): Option[IExpressionPE] = {
    val start = iter.getPos()
    if (iter.trySkip("^\\s*true\\b".r)) {
      return Some(ConstantBoolPE(RangeP(start, iter.getPos()), true))
    }
    if (iter.trySkip("^\\s*false\\b".r)) {
      return Some(ConstantBoolPE(RangeP(start, iter.getPos()), false))
    }
    return None
  }

  def parseAtom(iter: ParsingIterator, stopBefore: IStopBefore): Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()

    // See BRCOBS
    if (iter.trySkip("^break\\b".r)) {
      return Err(CantUseBreakInExpression(iter.getPos()))
    }
    // See BRCOBS
    if (iter.trySkip("^ret\\b".r)) {
      return Err(CantUseReturnInExpression(iter.getPos()))
    }
    if (iter.trySkip("^_\\b".r)) {
      return Ok(Some(MagicParamLookupPE(RangeP(begin, iter.getPos()))))
    }
    parseBoolean(iter) match {
      case Some(e) => return Ok(Some(e))
      case None =>
    }
    parseForeach(iter, true) match {
      case Err(err) => return Err(err)
      case Ok(Some(e)) => return Ok(Some(e))
      case Ok(None) =>
    }
    ParseString.parseString(iter) match {
      case Err(err) => return Err(err)
      case Ok(Some(e)) => return Ok(Some(e))
      case Ok(None) =>
    }
    parseNumber(iter) match {
      case Err(err) => return Err(err)
      case Ok(Some(e)) => return Ok(Some(e))
      case Ok(None) =>
    }
    parseArray(iter) match {
      case Err(err) => return Err(err)
      case Ok(Some(e)) => return Ok(Some(e))
      case Ok(None) =>
    }
    parseLambda(iter, stopBefore) match {
      case Err(err) => return Err(err)
      case Ok(Some(e)) => return Ok(Some(e))
      case Ok(None) =>
    }
    parseMut(iter, stopBefore, true) match {
      case Err(err) => return Err(err)
      case Ok(Some(e)) => return Ok(Some(e))
      case Ok(None) =>
    }
    parseLookup(iter) match {
      case Some(e) => return Ok(Some(e))
      case None =>
    }
    parseTupleOrSubExpression(iter) match {
      case Err(err) => return Err(err)
      case Ok(Some(e)) => {
        return Ok(Some(e))
      }
      case Ok(None) =>
    }
    Ok(None)
  }

  def parseSpreeStep(spreeBegin: Int, iter: ParsingIterator, exprSoFar: IExpressionPE, stopBefore: IStopBefore):
  Result[Option[IExpressionPE], IParseError] = {
    val operatorBegin = iter.getPos()

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
              RangeP(spreeBegin, iter.getPos()),
              RangeP(operatorBegin, iter.getPos()),
              exprSoFar,
              args,
              false)))
      }
      case Ok(None) =>
    }

    if (iter.trySkip("^\\s*\\.\\.".r)) {
      parseAtom(iter, stopBefore) match {
        case Err(err) => return Err(err)
        case Ok(None) => return Err(BadRangeOperand(iter.getPos()))
        case Ok(Some(operand)) => {
          val rangePE = RangePE(RangeP(spreeBegin, iter.getPos()), exprSoFar, operand)
          return Ok(Some(rangePE))
        }
      }
    }

    iter.tryy("^\\s*!?\\s*\\.".r) match {
      case None =>
      case Some(op) => {
        val subjectReadwrite = op.contains('!')

        val operatorEnd = iter.getPos()
        iter.consumeWhitespace()
        val nameBegin = iter.getPos()
        val name =
          iter.tryy("^\\d+".r) match {
            case Some(x) => {
              NameP(RangeP(nameBegin, iter.getPos()), x)
            }
            case None => {
              parseFunctionOrLocalOrMemberName(iter) match {
                case Some(n) => n
                case None => return Err(BadDot(iter.getPos()))
              }
            }
          }

        val maybeTemplateArgs =
          parseChevronPack(iter) match {
            case Err(e) => return Err(e)
            case Ok(None) => None
            case Ok(Some(templateArgs)) => {
              Some(TemplateArgsP(RangeP(operatorBegin, iter.getPos()), templateArgs))
            }
          }

        parsePack(iter) match {
          case Err(e) => return Err(e)
          case Ok(Some(x)) => {
            return Ok(
              Some(
                MethodCallPE(
                  RangeP(spreeBegin, iter.getPos()),
                  exprSoFar,
                  RangeP(operatorBegin, operatorEnd),
                  subjectReadwrite,
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
                  RangeP(spreeBegin, iter.getPos()),
                  exprSoFar,
                  RangeP(operatorBegin, operatorEnd),
                  name)))
          }
        }
      }
    }

    Ok(None)
  }

  def parseFunctionCall(originalIter: ParsingIterator, spreeBegin: Int, exprSoFar: IExpressionPE):
  Result[Option[IExpressionPE], IParseError] = {
    val tentativeIter = originalIter.clone()
    val operatorBegin = tentativeIter.getPos()
    val readwrite = tentativeIter.trySkip("^!".r)

    parsePack(tentativeIter) match {
      case Err(e) => Err(e)
      case Ok(None) => Ok(None)
      case Ok(Some(args)) => {
        originalIter.skipTo(tentativeIter.position)
        val iter = originalIter
        Ok(
          Some(
            FunctionCallPE(
              RangeP(spreeBegin, iter.getPos()),
              RangeP(operatorBegin, iter.getPos()),
              exprSoFar,
              args,
              readwrite)))
      }
    }
  }

  def parseAtomAndTightSuffixes(iter: ParsingIterator, stopBefore: IStopBefore):
  Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()

    var exprSoFar =
      parseAtom(iter, stopBefore) match {
        case Err(err) => return Err(err)
        case Ok(None) => return Ok(None)
        case Ok(Some(e)) => e
      }

    var continuing = true
    while (continuing && !atExpressionEnd(iter, stopBefore)) {
      parseSpreeStep(begin, iter, exprSoFar, stopBefore) match {
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

  def parseChevronPack(iter: ParsingIterator): Result[Option[Vector[ITemplexPT]], IParseError] = {
    // We need one side of the chevron to not have spaces, to avoid ambiguity with
    // the binary < operator.
    if (iter.peek("^\\s+<\\s+".r)) {
      // This is a binary < operator, because there's space on both sides.
      return Ok(None)
    } else if (iter.peek("^\\s*<=".r)) {
      // This is a binary <= operator, bail.
      return Ok(None)
    } else if (iter.peek("^\\s+<[\\S]".r)) {
      // This is a template call like:
      //   x = myFunc <int> ();
      val y = iter.trySkip("^\\s+<".r)
      vassert(y)
      // continue
    } else if (iter.trySkip("^<\\s*".r)) {
      // continue
    } else {
      // Nothing we recognize, bail out.
      return Ok(None)
    }
    iter.consumeWhitespace()
    val elements = new mutable.ArrayBuffer[ITemplexPT]()
    while (!iter.trySkip("^\\>".r)) {
      val expr =
        iter.consumeWithCombinator(CombinatorParsers.templex) match {
          case Err(e) => return Err(e)
          case Ok(expr) => expr
        }
      elements += expr
      iter.consumeWhitespace()
      iter.trySkip("^,".r)
      iter.consumeWhitespace()
    }

    Ok(Some(elements.toVector))
  }

  def parseTemplateLookup(iter: ParsingIterator, exprSoFar: IExpressionPE): Result[Option[LookupPE], IParseError] = {
    val operatorBegin = iter.getPos()

    val templateArgs =
      parseChevronPack(iter) match {
        case Err(e) => return Err(e)
        case Ok(None) => return Ok(None)
        case Ok(Some(templateArgs)) => {
          TemplateArgsP(RangeP(operatorBegin, iter.getPos()), templateArgs)
        }
      }

    val resultPE =
      exprSoFar match {
        case LookupPE(name, None) => LookupPE(name, Some(templateArgs))
        case _ => return Err(BadTemplateCallee(operatorBegin))
      }

    Ok(Some(resultPE))
  }

  def parsePack(iter: ParsingIterator): Result[Option[Vector[IExpressionPE]], IParseError] = {
    if (!iter.trySkip("^\\s*\\(".r)) {
      return Ok(None)
    }
    iter.consumeWhitespace()
    val elements = new mutable.ArrayBuffer[IExpressionPE]()
    while (!iter.trySkip("^\\)".r)) {
      val expr =
        parseExpression(iter, StopBeforeCloseParen) match {
          case Err(e) => return Err(e)
          case Ok(expr) => expr
        }
      elements += expr
      iter.consumeWhitespace()
      iter.trySkip("^,".r)
      iter.consumeWhitespace()
    }
    Ok(Some(elements.toVector))
  }

  def parseSquarePack(iter: ParsingIterator): Result[Option[Vector[IExpressionPE]], IParseError] = {
    if (!iter.trySkip("^\\s*\\[".r)) {
      return Ok(None)
    }
    iter.consumeWhitespace()
    val elements = new mutable.ArrayBuffer[IExpressionPE]()
    while (!iter.trySkip("^\\]".r)) {
      val expr =
        parseExpression(iter, StopBeforeCloseSquare) match {
          case Err(e) => return Err(e)
          case Ok(expr) => expr
        }
      elements += expr
      iter.consumeWhitespace()
      iter.trySkip("^,".r)
      iter.consumeWhitespace()
    }
    Ok(Some(elements.toVector))
  }

  def parseBracePack(iter: ParsingIterator): Result[Option[Vector[IExpressionPE]], IParseError] = {
    if (!iter.trySkip("^\\s*\\[".r)) {
      return Ok(None)
    }
    iter.consumeWhitespace()
    val elements = new mutable.ArrayBuffer[IExpressionPE]()
    while (!iter.trySkip("^\\]".r)) {
      val expr =
        parseExpression(iter, StopBeforeCloseBrace) match {
          case Err(e) => return Err(e)
          case Ok(expr) => expr
        }
      elements += expr
      iter.consumeWhitespace()
      iter.trySkip("^,".r)
      iter.consumeWhitespace()
    }
    Ok(Some(elements.toVector))
  }

  def parseTupleOrSubExpression(iter: ParsingIterator): Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()
    if (!iter.trySkip("^\\s*\\(".r)) {
      return Ok(None)
    }
    iter.consumeWhitespace()

    if (iter.trySkip("^\\)".r)) {
      return Ok(Some(TuplePE(RangeP(begin, iter.getPos()), Vector())))
    }

    val elements = new mutable.ArrayBuffer[IExpressionPE]()

    val expr =
      parseExpression(iter, StopBeforeCloseParen) match {
        case Err(e) => return Err(e)
        case Ok(expr) => expr
      }
    iter.consumeWhitespace()

    // One-element tuple
    if (iter.trySkip("^,\\s*\\)".r)) {
      return Ok(Some(TuplePE(RangeP(begin, iter.getPos()), Vector(expr))))
    }

    // Just one expression, no comma at end, so its some parens just for
    // a sub expression.
    if (iter.trySkip("^\\s*\\)".r)) {
      return Ok(Some(SubExpressionPE(RangeP(begin, iter.getPos()), expr)))
    }

    elements += expr

    if (!iter.trySkip("^\\s*,".r)) {
      return Err(UnknownTupleOrSubExpression(iter.getPos()))
    }
    iter.consumeWhitespace()

    while (!iter.trySkip("^\\s*\\)".r)) {
      val expr =
        parseExpression(iter, StopBeforeCloseParen) match {
          case Err(e) => return Err(e)
          case Ok(expr) => expr
        }
      elements += expr
      iter.consumeWhitespace()
      if (iter.peek("^\\s*,\\s*\\)".r)) {
        val found = iter.trySkip("^\\s*,".r)
        vassert(found)
        vassert(iter.peek("^\\s*\\)".r))
      }
      iter.consumeWhitespace()
      iter.trySkip("^,".r)
      iter.consumeWhitespace()
    }
    Ok(Some(TuplePE(RangeP(begin, iter.getPos()), elements.toVector)))
  }

  def parseExpressionDataElement(iter: ParsingIterator, stopBefore: IStopBefore): Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()
//    iter.tryy("^\\.\\.\\.".r) match {
//      case Some(n) => return Ok(Some(VoidPE(RangeP(begin, iter.getPos()))))
//      case None =>
//    }

    // We'll ignore these prefixes, they're for documentation and blogs:
    if (iter.trySkip("^inl\\b".r)) {
      iter.consumeWhitespace()
      return parseExpressionDataElement(iter, stopBefore)
    }
    if (iter.trySkip("^'\\w+\\b".r)) {
      iter.consumeWhitespace()
      return parseExpressionDataElement(iter, stopBefore)
    }

    // First, get the prefixes out of the way, such as & not etc.
    // Then we'll parse the atom and suffixes (.moo, ..5, etc.) and
    // *then* wrap those in the prefixes, so we get e.g. not(x.moo)
    if (iter.trySkip("^not\\b".r)) {
      iter.consumeWhitespace()
      val innerPE =
        parseExpressionDataElement(iter, stopBefore) match {
          case Err(err) => return Err(err)
          case Ok(None) => vwat()
          case Ok(Some(e)) => e
        }
      val notPE = NotPE(RangeP(begin, iter.getPos()), innerPE)
      return Ok(Some(notPE))
    }

    parseIfLadder(iter, true) match {
      case Err(e) => return Err(e)
      case Ok(Some(e)) => return Ok(Some(e))
      case Ok(None) =>
    }

    if (iter.trySkip("^not\\b".r)) {
      iter.consumeWhitespace()
      val innerPE =
        parseExpressionDataElement(iter, stopBefore) match {
          case Err(err) => return Err(err)
          case Ok(None) => vwat()
          case Ok(Some(e)) => e
        }
      val notPE = NotPE(RangeP(begin, iter.getPos()), innerPE)
      return Ok(Some(notPE))
    }

    iter.tryy("^(\\^|&!|&|\\*\\*!|\\*\\*|\\*!|\\*)".r) match {
      case None =>
      case Some(str) => {
        val innerPE =
          parseExpressionDataElement(iter, stopBefore) match {
            case Err(err) => return Err(err)
            case Ok(None) => vwat()
            case Ok(Some(e)) => e
          }

        val (targetOwnership, targetPermission) =
          str match {
            case "^" => (OwnP, ReadwriteP)
            case "&!" => (BorrowP, ReadwriteP)
            case "&" => (BorrowP, ReadonlyP)
            case "*!" => (PointerP, ReadwriteP)
            case "*" => (PointerP, ReadonlyP)
            case "**!" => (WeakP, ReadwriteP)
            case "**" => (WeakP, ReadonlyP)
          }
        val augmentPE = AugmentPE(RangeP(begin, iter.getPos()), targetOwnership, Some(targetPermission), innerPE)
        return Ok(Some(augmentPE))
      }
    }

    // Now, do some "right recursion"; parse the atom (e.g. true, 4, x)
    // and then parse any suffixes, like
    // .moo
    // .foo(5)
    // ..5
    // which all have tighter precedence than the prefixes.
    // Then we'll return, and our callers will wrap it in the prefixes
    // like & not etc.
    parseAtomAndTightSuffixes(iter, stopBefore) match {
      case Err(err) => return Err(err)
      case Ok(Some(e)) => return Ok(Some(e))
      case Ok(None) =>
    }
    Ok(None)
  }

  def parseBracedBody(iter: ParsingIterator):  Result[Option[BlockPE], IParseError] = {
    if (!iter.trySkip("^\\s*\\{".r)) {
      return Ok(None)
    }
    iter.consumeWhitespace()
    val bodyBegin = iter.getPos()
    val bodyContents =
      parseBlockContents(iter, StopBeforeCloseBrace, false) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }
    if (!iter.trySkip("^\\}".r)) {
      vwat()
    }
    Ok(Some(BlockPE(RangeP(bodyBegin, iter.getPos()), bodyContents)))
  }

  def parseSingleArgLambdaBegin(originalIter: ParsingIterator): Option[ParamsP] = {
    val tentativeIter = originalIter.clone()
    val begin = tentativeIter.getPos()
    val argName =
      parseLocalOrMemberName(tentativeIter) match {
        case None => return None
        case Some(n) => n
      }
    val paramsEnd = tentativeIter.getPos()

    tentativeIter.consumeWhitespace()
    if (!tentativeIter.trySkip("^=>".r)) {
      return None
    }

    originalIter.skipTo(tentativeIter.position)

    val range = RangeP(begin, paramsEnd)
    val capture = LocalNameDeclarationP(argName)
    val pattern = PatternPP(RangeP(begin, paramsEnd), None, Some(capture), None, None, None)
    Some(ParamsP(range, Vector(pattern)))
  }

  def parseMultiArgLambdaBegin(originalIter: ParsingIterator): Option[ParamsP] = {
    val tentativeIter = originalIter.clone()

    val begin = tentativeIter.getPos()
    if (!tentativeIter.trySkip("^\\s*\\(".r)) {
      return None
    }
    tentativeIter.consumeWhitespace()
    val patterns = new mutable.ArrayBuffer[PatternPP]()

    while (!tentativeIter.trySkip("^\\s*\\)".r)) {
      val pattern =
        tentativeIter.consumeWithCombinator(CombinatorParsers.atomPattern) match {
          case Ok(result) => result
          case Err(cpe) => return None
        }
      patterns += pattern
      tentativeIter.consumeWhitespace()
      if (tentativeIter.peek("^\\s*,\\s*\\)".r)) {
        val found = tentativeIter.trySkip("^\\s*,".r)
        vassert(found)
        vassert(tentativeIter.peek("^\\s*\\)".r))
      } else if (tentativeIter.trySkip("^\\s*,".r)) {
        // good, continue
      } else if (tentativeIter.peek("^\\s*\\)".r)) {
        // good, continue
      } else {
        // At some point, we should return an error here.
        // With a pre-parser that looks for => it would be possible.
        return None
      }
      tentativeIter.consumeWhitespace()
    }

    val paramsEnd = tentativeIter.getPos()

    tentativeIter.consumeWhitespace()
    if (!tentativeIter.trySkip("^=>".r)) {
      return None
    }

    val params = ParamsP(RangeP(begin, paramsEnd), patterns.toVector)

    originalIter.skipTo(tentativeIter.position)

    Some(params)
  }

  def parseLambda(iter: ParsingIterator, stopBefore: IStopBefore): Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()
    val maybeParams =
      parseSingleArgLambdaBegin(iter) match {
        case Some(p) => Some(p)
        case None => {
          parseMultiArgLambdaBegin(iter) match {
            case Some(p) => Some(p)
            case None => {
              if (stopBefore == StopBeforeOpenBrace && iter.peek("^\\s*\\{".r)) {
                return Ok(None)
              } else {
                None
              }
            }
          }
        }
      }
    iter.consumeWhitespace()

    val body =
      parseBracedBody(iter) match {
        case Err(e) => return Err(e)
        case Ok(Some(x)) => x
        case Ok(None) => {
          if (maybeParams.isEmpty) {
            // If theres no params and no braces, we're done here
            return Ok(None)
          }
          val bodyBegin = iter.getPos()
          parseExpression(iter, stopBefore) match {
            case Err(e) => return Err(e)
            case Ok(x) => BlockPE(RangeP(bodyBegin, iter.getPos()), x)
          }
        }
      }
    val lam =
      LambdaPE(
        None,
        FunctionP(
          RangeP(begin, iter.getPos()),
          FunctionHeaderP(
            RangeP(begin, iter.getPos()),
            None,
            Vector(),
            None,
            None,
            maybeParams,
            FunctionReturnP(RangeP(iter.getPos(), iter.getPos()), None, None)),
          Some(body)))
    Ok(Some(lam))
  }

  def parseArray(originalIter: ParsingIterator): Result[Option[IExpressionPE], IParseError] = {
    val tentativeIter = originalIter.clone()
    val begin = tentativeIter.getPos()

    val mutability =
      if (tentativeIter.trySkip("^#".r)) {
        MutabilityPT(RangeP(begin, tentativeIter.getPos()), ImmutableP)
      } else {
        MutabilityPT(RangeP(begin, tentativeIter.getPos()), MutableP)
      }

    if (!tentativeIter.trySkip("^\\[".r)) {
      return Ok(None)
    }
    originalIter.skipTo(tentativeIter.position)
    val iter = originalIter

    val size =
      if (iter.trySkip("^#".r)) {
        val sizeTemplex =
          if (iter.peek("^\\s*\\]".r)) {
            None
          } else {
            iter.consumeWithCombinator(CombinatorParsers.templex) match {
              case Err(e) => return Err(CombinatorParseError(iter.getPos(), e.msg))
              case Ok(e) => Some(e)
            }
          }
        StaticSizedP(sizeTemplex)
      } else {
        RuntimeSizedP
      }
    if (!iter.trySkip("^\\]".r)) {
      return Err(BadArraySizerEnd(iter.getPos()))
    }

    val tyype =
      if (!iter.peek("^\\s*[\\[\\(]".r)) {
        iter.consumeWithCombinator(CombinatorParsers.templex) match {
          case Err(e) => return Err(CombinatorParseError(iter.getPos(), e.msg))
          case Ok(e) => Some(e)
        }
      } else {
        None
      }

    val (initializingByValues, args) =
      if (iter.peek("^\\[".r)) {
        val values =
          parseSquarePack(iter) match {
            case Ok(None) => vwat()
            case Ok(Some(e)) => e
            case Err(e) => return Err(e)
          }
        (true, values)
      } else if (iter.peek("^\\(".r)) {
        val args =
          parsePack(iter) match {
            case Ok(None) => vwat()
            case Ok(Some(e)) => e
            case Err(e) => return Err(e)
          }
        (false, args)
      } else {
        return Err(BadArraySpecifier(iter.getPos()))
      }
    val arrayPE =
      ConstructArrayPE(
        RangeP(begin, iter.getPos()),
        tyype,
        Some(mutability),
        None,
        size,
        initializingByValues,
        args)
    Ok(Some(arrayPE))
  }

  def parseNumber(originalIter: ParsingIterator): Result[Option[IExpressionPE], IParseError] = {
    val defaultBits = 32
    val begin = originalIter.getPos()

    val tentativeIter = originalIter.clone()

    val negative = tentativeIter.trySkip("^-".r)

    if (!tentativeIter.peek("^\\d".r)) {
      return Ok(None)
    }

    originalIter.skipTo(tentativeIter.position)
    val iter = originalIter

    var digitsConsumed = 0
    var integer = 0L
    while (iter.tryy("^\\d".r) match {
      case Some(d) => {
        integer = integer * 10L + d.toLong
        digitsConsumed += 1
      }; true
      case None => false
    }) {}
    vassert(digitsConsumed > 0)

    if (iter.peek("^\\.\\.".r)) {
      // This is followed by the range operator, so just stop here.
      Ok(Some(ConstantIntPE(RangeP(begin, iter.getPos()), integer, defaultBits)))
    } else if (iter.trySkip("^\\.".r)) {
      var mantissa = 0.0
      var digitMultiplier = 1.0
      while (iter.tryy("^\\d".r) match {
        case Some(d) => {
          digitMultiplier = digitMultiplier * 0.1
          mantissa = mantissa + d.toInt * digitMultiplier
          true
        }
        case None => false
      }) {}

      if (iter.trySkip("^f".r)) {
        vimpl()
      }

      val result = (integer + mantissa) * (if (negative) -1 else 1)
      Ok(Some(ConstantFloatPE(RangeP(begin, iter.getPos()), result)))
    } else {
      val bits =
        if (iter.trySkip("^i".r)) {
          var bits = 0
          while (iter.tryy("^\\d".r) match {
            case Some(d) => bits = bits * 10 + d.toInt; true
            case None => false
          }) {}
          vassert(bits > 0)
          bits
        } else {
          defaultBits
        }

      val result = integer * (if (negative) -1 else 1)

      Ok(Some(ConstantIntPE(RangeP(begin, iter.getPos()), result, bits)))
    }
  }

  // Returns the index we stopped at, which will be either
  // the end of the array or one past endIndexInclusive.
  def descramble(
    elements: Array[ExpressionParser.IExpressionElement],
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
          case "and" => {
            AndPE(
              RangeP(leftOperand.range.begin, leftOperand.range.end),
              leftOperand,
              BlockPE(rightOperand.range, rightOperand))
          }
          case "or" => {
            OrPE(
              RangeP(leftOperand.range.begin, leftOperand.range.end),
              leftOperand,
              BlockPE(rightOperand.range, rightOperand))
          }
          case _ => {
            BinaryCallPE(
              RangeP(leftOperand.range.begin, leftOperand.range.end),
              binaryCall.symbol,
              leftOperand,
              rightOperand)
          }
        }
    }

    (leftOperand, nextIndex)
  }

  def parseBinaryCall(iter: ParsingIterator):
  Result[Option[NameP], IParseError] = {
    if (!iter.consumeWhitespace()) {
      return Ok(None)
    }
    if (iter.peek("^\\)".r)) {
      return Err(BadExpressionEnd(iter.getPos()))
    }
    if (iter.peek("^\\]".r)) {
      return Err(BadExpressionEnd(iter.getPos()))
    }
    parseFunctionOrLocalOrMemberName(iter) match {
      case Some(x) => {
        if (!iter.consumeWhitespace()) {
          return Err(NeedWhitespaceAroundBinaryOperator(iter.getPos()))
        }
        return Ok(Some(x))
      }
      case None =>
    }
    if (iter.peek("^=".r)) {
      return Err(ForgotSetKeyword(iter.getPos()))
    }
    Err(BadBinaryFunctionName(iter.getPos()))
  }

  def atExpressionEnd(iter: ParsingIterator, stopBefore: IStopBefore): Boolean = {
    return Parser.atEnd(iter, stopBefore) || iter.peek("^\\s*;".r)
  }
}
