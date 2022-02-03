package net.verdagon.vale.parser

import net.verdagon.vale.parser.ExpressionParser.{IStopBefore, StopBeforeCloseBrace, StopBeforeCloseChevron, StopBeforeCloseParen, StopBeforeCloseSquare, StopBeforeEquals, StopBeforeOpenBrace}
import net.verdagon.vale.parser.ast.{AugmentPE, BinaryCallPE, BlockPE, BorrowP, ConsecutorPE, ConstantFloatPE, ConstantIntPE, DestructPE, DotPE, EachPE, ExportAsP, FileP, FunctionCallPE, FunctionP, IExpressionPE, IStructContent, ITopLevelThingP, IfPE, ImplP, ImportP, InterfaceP, LetPE, LoadAsBorrowP, LocalNameDeclarationP, LookupNameP, LookupPE, MethodCallPE, MutabilityPT, MutableP, MutatePE, NameP, NotPE, OwnP, PackPE, PointerP, RangeP, RangePE, ReadonlyP, ReadwriteP, ReturnPE, StructMembersP, StructMethodP, StructP, TopLevelExportAsP, TopLevelFunctionP, TopLevelImplP, TopLevelImportP, TopLevelInterfaceP, TopLevelStructP, VoidPE, WeakP, WhilePE}
import net.verdagon.vale.parser.expressions.ParseString
import net.verdagon.vale.parser.old.CombinatorParsers
import net.verdagon.vale.{Err, FileCoordinateMap, IPackageResolver, IProfiler, NullProfiler, Ok, PackageCoordinate, Result, repeatStr, vassert, vassertSome, vcurious, vfail, vimpl, vwat}
import net.verdagon.von.{JsonSyntax, VonPrinter}

import scala.collection.immutable.{List, Map}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex
import scala.util.parsing.input.{CharSequenceReader, Position}


case class ParsingIterator(code: String, var position: Int = 0) {
  override def hashCode(): Int = vcurious();

  def currentChar(): Char = code.charAt(position)
  def advance() { position = position + 1 }

  override def clone(): ParsingIterator = ParsingIterator(code, position)

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

  def consumeWhitespace(): Boolean = {
    var foundAny = false
    while (!atEnd()) {
      currentChar() match {
        case ' ' | '\t' | '\n' | '\r' => foundAny = true
        case _ => return foundAny
      }
      advance()
    }
    return false
  }

  private def at(regex: Regex): Boolean = {
    vassert(regex.pattern.pattern().startsWith("^"))
    regex.findFirstIn(code.slice(position, code.length)).nonEmpty
  }

  def trySkip(regex: Regex): Boolean = {
    vassert(regex.pattern.pattern().startsWith("^"))
    regex.findFirstIn(code.slice(position, code.length)) match {
      case None => false
      case Some(matchedStr) => {
        skipTo(position + matchedStr.length)
        true
      }
    }
  }

  def tryy(regex: Regex): Option[String] = {
    vassert(regex.pattern.pattern().startsWith("^"))
    regex.findFirstIn(code.slice(position, code.length)) match {
      case None => None
      case Some(matchedStr) => {
        skipTo(position + matchedStr.length)
        Some(matchedStr)
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

object Parser {
  def runParserForProgramAndCommentRanges(codeWithComments: String): Result[(FileP, Vector[(Int, Int)]), IParseError] = {
    val regex = "(//[^\\r\\n]*|«\\w+»)".r
    val commentRanges = regex.findAllMatchIn(codeWithComments).map(mat => (mat.start, mat.end)).toVector
    var code = codeWithComments
    commentRanges.foreach({ case (begin, end) =>
      code = code.substring(0, begin) + repeatStr(" ", (end - begin)) + code.substring(end)
    })
    val codeWithoutComments = code

    runParser(codeWithoutComments) match {
      case f @ Err(err) => Err(err)
      case Ok(program0) => Ok((program0, commentRanges))
    }
  }

  def runParser(codeWithoutComments: String): Result[FileP, IParseError] = {
    val iter = ParsingIterator(codeWithoutComments, 0)
    iter.consumeWhitespace()
    runParserInner(iter)
  }

  private[parser] def runParserInner(iter: ParsingIterator): Result[FileP, IParseError] = {
    val topLevelThings = new mutable.MutableList[ITopLevelThingP]()

    iter.consumeWhitespace()

    while (!iter.atEnd()) {
      if (iter.peek("^struct\\b".r)) {
        parseStruct(iter) match {
          case Err(err) => return Err(err)
          case Ok(None) => vimpl() // restructure this function
          case Ok(Some(result)) => topLevelThings += TopLevelStructP(result)
        }
      } else if (iter.peek("^interface\\b".r)) {
        parseInterface(iter) match {
          case Err(err) => return Err(err)
          case Ok(None) => vimpl() // restructure this function
          case Ok(Some(result)) => topLevelThings += TopLevelInterfaceP(result)
        }
      } else if (iter.peek("^impl\\b".r)) {
        parseImpl(iter) match {
          case Err(err) => return Err(err)
          case Ok(result) => topLevelThings += TopLevelImplP(result)
        }
      } else if (iter.peek("^export\\b".r)) {
        parseExportAs(iter) match {
          case Err(err) => return Err(err)
          case Ok(result) => topLevelThings += TopLevelExportAsP(result)
        }
      } else if (iter.peek("^import\\b".r)) {
        parseImport(iter) match {
          case Err(err) => return Err(err)
          case Ok(result) => topLevelThings += TopLevelImportP(result)
        }
      } else if (iter.peek("^fn\\b".r)) {
        parseFunction(iter, StopBeforeCloseBrace) match {
          case Err(err) => return Err(err)
          case Ok(None) => vimpl() // restructure this function
          case Ok(Some(result)) => topLevelThings += TopLevelFunctionP(result)
        }
      } else {
        return Err(UnrecognizedTopLevelThingError(iter.position))
      }
      iter.consumeWhitespace()
    }

    val program0 = ast.FileP(topLevelThings.toVector)
    Ok(program0)
  }

  def parseFunctionOrLocalOrMemberName(iter: ParsingIterator): Option[NameP] = {
    val begin = iter.getPos()
    iter.tryy("""^^(<=>|<=|<|>=|>|===|==|!=|[^\s\.\!\$\&\,\:\(\)\;\[\]\{\}\'\@\^\"\<\>\=\`]+)""".r) match {
      case Some(str) => Some(NameP(RangeP(begin, iter.getPos()), str))
      case None => None
    }
  }

  def parseLocalOrMemberName(iter: ParsingIterator): Option[NameP] = {
    val begin = iter.getPos()
    iter.tryy("^[A-Za-z_][A-Za-z0-9_]*".r) match {
      case Some(str) => Some(NameP(RangeP(begin, iter.getPos()), str))
      case None => None
    }
  }

  def parseTypeName(iter: ParsingIterator): Option[NameP] = {
    val begin = iter.getPos()
    iter.tryy("^[A-Za-z_][A-Za-z0-9_]*".r) match {
      case Some(str) => Some(NameP(RangeP(begin, iter.getPos()), str))
      case None => None
    }
  }

  private[parser] def parseStruct(iter: ParsingIterator): Result[Option[StructP], IParseError] = {
    val begin = iter.getPos()

    if (!iter.trySkip("^struct\\b".r)) {
      return Ok(None)
    }

    iter.consumeWhitespace()

    val name =
      parseTypeName(iter) match {
        case None => return Err(BadStructName(iter.getPos()))
        case Some(x) => x
      }

    iter.consumeWhitespace()

    val maybeIdentifyingRunes =
      iter.consumeWithCombinator(CombinatorParsers.opt(CombinatorParsers.identifyingRunesPR)) match {
        case Err(e) => vwat()
        case Ok(x) => x
      }

    iter.consumeWhitespace()

    val attributes =
      iter.consumeWithCombinator(CombinatorParsers.repsep(CombinatorParsers.citizenAttribute, CombinatorParsers.white)) match {
        case Err(e) => vwat()
        case Ok(x) => x.toVector
      }

    iter.consumeWhitespace()

    val maybeTemplateRules =
      iter.consumeWithCombinator(CombinatorParsers.opt(CombinatorParsers.templateRulesPR)) match {
        case Err(e) => vwat()
        case Ok(e) => e
      }

    iter.consumeWhitespace()

    val mutabilityBegin = iter.getPos()
    val maybeMutability =
      iter.consumeWithCombinator(CombinatorParsers.opt(CombinatorParsers.templex)) match {
        case Err(e) => vwat()
        case Ok(e) => e
      }
    val mutabilityEnd = iter.getPos()

    iter.consumeWhitespace()

    val contentsBegin = iter.getPos()

    if (!iter.trySkip("^\\{".r)) {
      return Err(BadStructContentsBegin(iter.getPos()))
    }

    val contents = ArrayBuffer[IStructContent]()

    iter.consumeWhitespace()
    while (!atEnd(iter, StopBeforeCloseBrace)) {
      iter.consumeWhitespace()
      parseFunction(iter, StopBeforeCloseBrace) match {
        case Err(e) => return Err(e)
        case Ok(Some(f)) => contents += StructMethodP(f)
        case Ok(None) => {
          iter.consumeWithCombinator(CombinatorParsers.variadicStructMember) match {
            case Ok(m) => contents += m
            case Err(e) => {
              iter.consumeWithCombinator(CombinatorParsers.normalStructMember) match {
                case Ok(m) => contents += m
                case Err(e) => {
                  return Err(BadStructMember(iter.getPos()))
                }
              }
            }
          }
        }
      }
    }
    iter.consumeWhitespace()

    if (!iter.trySkip("^\\}".r)) {
      return Err(BadStructContentsEnd(iter.getPos()))
    }

    val contentsEnd = iter.getPos()

    val struct =
      ast.StructP(
        ast.RangeP(begin, iter.getPos()),
        name,
        attributes,
        maybeMutability.getOrElse(MutabilityPT(ast.RangeP(mutabilityBegin, mutabilityEnd), MutableP)),
        maybeIdentifyingRunes,
        maybeTemplateRules,
        StructMembersP(ast.RangeP(contentsBegin, contentsEnd),
        contents.toVector))
    Ok(Some(struct))

    //      // A hack to do region highlighting
//      opt("'" ~> optWhite ~> exprIdentifier <~ optWhite) ~
//      pos ~
//      (opt(templex) <~ optWhite) ~
//      (pos <~ "{" <~ optWhite) ~
//      pos ~
//      ("..." <~ optWhite ^^^ Vector.empty | repsep(structContent, optWhite)) ~
//      (optWhite ~> "}" ~> pos) ^^ {
//      case begin ~ name ~ identifyingRunes ~ attributes ~ maybeTemplateRules ~ defaultRegion ~ mutabilityBegin ~ maybeMutability ~ mutabilityEnd ~ membersBegin ~ members ~ end => {
//        ast.StructP(ast.RangeP(begin, end), name, attributes.toVector, maybeMutability.getOrElse(MutabilityPT(ast.RangeP(mutabilityBegin, mutabilityEnd), MutableP)), identifyingRunes, maybeTemplateRules, StructMembersP(ast.RangeP(membersBegin, end), members.toVector))
//      }
//    }
//    iter.consumeWithCombinator(CombinatorParsers.struct) match {
//      case Err(e) => Err(BadStruct(iter.getPos(), e))
//      case Ok(s) => Ok(s)
//    }
  }

  private def parseInterface(iter: ParsingIterator): Result[Option[InterfaceP], IParseError] = {
//    pos ~
//      ("interface " ~> optWhite ~> exprIdentifier <~ optWhite) ~
//      opt(identifyingRunesPR <~ optWhite) ~
//      (repsep(citizenAttribute, white) <~ optWhite) ~
//      (opt(templateRulesPR) <~ optWhite) ~
//      // A hack to do region highlighting
//      opt("'" ~> optWhite ~> exprIdentifier <~ optWhite) ~
//      pos ~
//      (opt(templex) <~ optWhite) ~
//      (pos <~ "{" <~ optWhite) ~
//      repsep(topLevelFunction, optWhite) ~
//      (optWhite ~> "}" ~> pos) ^^ {
//      case begin ~ name ~ maybeIdentifyingRunes ~ seealed ~ maybeTemplateRules ~ defaultRegion ~ mutabilityBegin ~ maybeMutability ~ mutabilityEnd ~ members ~ end => {
//        ast.InterfaceP(ast.RangeP(begin, end), name, seealed.toVector, maybeMutability.getOrElse(MutabilityPT(ast.RangeP(mutabilityBegin, mutabilityEnd), MutableP)), maybeIdentifyingRunes, maybeTemplateRules, members.toVector)
//      }
//    }
//    iter.consumeWithCombinator(CombinatorParsers.interface) match {
//      case Err(e) => Err(BadInterface(iter.getPos(), e))
//      case Ok(s) => Ok(s)
//    }
    val begin = iter.getPos()

    if (!iter.trySkip("^interface\\b".r)) {
      return Ok(None)
    }

    iter.consumeWhitespace()

    val name =
      parseTypeName(iter) match {
        case None => return Err(BadStructName(iter.getPos()))
        case Some(x) => x
      }

    iter.consumeWhitespace()

    val maybeIdentifyingRunes =
      iter.consumeWithCombinator(CombinatorParsers.opt(CombinatorParsers.identifyingRunesPR)) match {
        case Err(e) => vwat()
        case Ok(x) => x
      }

    iter.consumeWhitespace()

    val attributes =
      iter.consumeWithCombinator(CombinatorParsers.repsep(CombinatorParsers.citizenAttribute, CombinatorParsers.white)) match {
        case Err(e) => vwat()
        case Ok(x) => x.toVector
      }

    iter.consumeWhitespace()

    val maybeTemplateRules =
      iter.consumeWithCombinator(CombinatorParsers.opt(CombinatorParsers.templateRulesPR)) match {
        case Err(e) => vwat()
        case Ok(e) => e
      }

    iter.consumeWhitespace()

    val mutabilityBegin = iter.getPos()
    val maybeMutability =
      iter.consumeWithCombinator(CombinatorParsers.opt(CombinatorParsers.templex)) match {
        case Err(e) => vwat()
        case Ok(e) => e
      }
    val mutabilityEnd = iter.getPos()

    iter.consumeWhitespace()

    val contentsBegin = iter.getPos()

    if (!iter.trySkip("^\\{".r)) {
      return Err(BadStructContentsBegin(iter.getPos()))
    }

    val methods = ArrayBuffer[FunctionP]()

    while (!atEnd(iter, StopBeforeCloseBrace)) {
      iter.consumeWhitespace()
      parseFunction(iter, StopBeforeCloseBrace) match {
        case Err(e) => return Err(e)
        case Ok(Some(f)) => methods += f
        case Ok(None) => return Err(BadInterfaceMember(iter.getPos()))
      }
    }

    iter.consumeWhitespace()

    if (!iter.trySkip("^\\}".r)) {
      return Err(BadStructContentsEnd(iter.getPos()))
    }

    val contentsEnd = iter.getPos()

    val interface =
      ast.InterfaceP(
        ast.RangeP(begin, iter.getPos()),
        name,
        attributes,
        maybeMutability.getOrElse(MutabilityPT(ast.RangeP(mutabilityBegin, mutabilityEnd), MutableP)),
        maybeIdentifyingRunes,
        maybeTemplateRules,
        methods.toVector)
    Ok(Some(interface))
  }

  private def parseImpl(iter: ParsingIterator): Result[ImplP, IParseError] = {
    iter.consumeWithCombinator(CombinatorParsers.impl) match {
      case Err(e) => Err(BadImpl(iter.getPos(), e))
      case Ok(s) => Ok(s)
    }
  }

  private def parseExportAs(iter: ParsingIterator): Result[ExportAsP, IParseError] = {
    iter.consumeWithCombinator(CombinatorParsers.`export`) match {
      case Err(e) => Err(BadExport(iter.getPos(), e))
      case Ok(s) => Ok(s)
    }
  }

  private def parseImport(iter: ParsingIterator): Result[ImportP, IParseError] = {
    iter.consumeWithCombinator(CombinatorParsers.`import`) match {
      case Err(e) => Err(BadImport(iter.getPos(), e))
      case Ok(s) => Ok(s)
    }
  }

  def atEnd(iter: ParsingIterator, stopBefore: IStopBefore): Boolean = {
    if (iter.peek("^\\s*$".r)) {
      return true
    }
    stopBefore match {
      case StopBeforeEquals => iter.peek("^\\s*=".r)
      case StopBeforeCloseBrace => iter.peek("^\\s*\\}".r)
      case StopBeforeCloseParen => iter.peek("^\\s*\\)".r)
      case StopBeforeCloseSquare => iter.peek("^\\s*\\]".r)
      case StopBeforeCloseChevron => iter.peek("^(\\s+>|>\\s+)".r)
      case StopBeforeOpenBrace => iter.peek("^\\s*\\{".r)
    }
  }

  private[parser] def parseFunction(iter: ParsingIterator, stopBefore: IStopBefore): Result[Option[FunctionP], IParseError] = {
    if (!iter.peek("^fn\\b".r)) {
      return Ok(None)
    }
    val funcBegin = iter.getPos()
    val header =
      iter.consumeWithCombinator(CombinatorParsers.topLevelFunctionBegin) match {
        case Err(err) => return Err(BadFunctionHeaderError(iter.getPos(), err))
        case Ok(result) => result
      }
    iter.consumeWhitespace()
    if (iter.trySkip("^;".r)) {
      return Ok(Some(ast.FunctionP(RangeP(funcBegin, iter.getPos()), header, None)))
    }
    val bodyBegin = iter.getPos()
    if (!iter.trySkip("^('\\w+\\s*)?\\{".r)) {
      return Err(BadFunctionBodyError(iter.position))
    }
    iter.consumeWhitespace()

    val statements =
      ExpressionParser.parseBlockContents(iter, StopBeforeCloseBrace, false) match {
        case Err(err) => return Err(err)
        case Ok(result) => result
      }

    if (iter.peek("^\\s*[\\)\\]]".r)) {
      return Err(BadStartOfStatementError(iter.getPos()))
    }
    vassert(iter.peek("^\\s*\\}".r))
    iter.consumeWhitespace()
    iter.advance()
    val bodyEnd = iter.getPos()
    val body = BlockPE(RangeP(bodyBegin, bodyEnd), statements)

    Ok(Some(ast.FunctionP(RangeP(funcBegin, bodyEnd), header, Some(body))))
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
          case Err(err) => {
            return Err(FailedParse(combinedCodeMap, fileCoord, err))
          }
          case Ok((program0, commentsRanges)) => {
            val von = ParserVonifier.vonifyFile(program0)
            val vpstJson = new VonPrinter(JsonSyntax, 120).print(von)
            ParsedLoader.load(vpstJson) match {
              case Err(error) => vwat(ParseErrorHumanizer.humanize(neededCodeMap, fileCoord, error))
              case Ok(program0) => (program0, commentsRanges)
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
