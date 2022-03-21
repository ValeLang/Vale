package net.verdagon.vale.parser

import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.parser.Parser.{ParsedDouble, ParsedInteger, atEnd, parseFunctionOrLocalOrMemberName, parseLocalOrMemberName}
import net.verdagon.vale.parser.ast._
import net.verdagon.vale.parser.expressions.StringParser
import net.verdagon.vale.parser.templex.TemplexParser
import net.verdagon.vale.{Err, FileCoordinateMap, IPackageResolver, Ok, PackageCoordinate, Profiler, Result, repeatStr, vassert, vassertSome, vcurious, vfail, vimpl, vwat}
import net.verdagon.von.{JsonSyntax, VonPrinter}

import scala.collection.immutable.{List, Map}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex


case class ParsingIterator(code: String, var position: Int = 0) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();

  def currentChar(): Char = code.charAt(position)
  def advance() { position = position + 1 }

  override def clone(): ParsingIterator = ParsingIterator(code, position)

  def atEnd(): Boolean = { position >= code.length }

  def skipTo(newPosition: Int) = {
    vassert(newPosition >= position)
    position = newPosition
  }

  def getPos(): Int = {
    position
  }

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

  def trySkipIfPeekNext(
    toConsume: Regex,
    ifNextPeek: Regex):
  Boolean = {
    val tentativeIter = this.clone()
    if (!tentativeIter.trySkip(toConsume)) {
      return false
    }
    val pos = tentativeIter.getPos()
    if (!tentativeIter.peek(ifNextPeek)) {
      return false
    }
    this.skipTo(pos)
    return true
  }
}

class Parser(opts: GlobalOptions) {
  def runParserForProgramAndCommentRanges(codeWithComments: String): Result[(FileP, Vector[(Int, Int)]), IParseError] = {
    val regex = "(\\.\\.\\.|//[^\\r\\n]*|«\\w+»)".r
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

  def parseExpectTopLevelThing(iter: ParsingIterator): Result[ITopLevelThingP, IParseError] = {
    parseTopLevelThing(iter) match {
      case Err(e) => Err(e)
      case Ok(None) => Err(UnrecognizedTopLevelThingError(iter.getPos()))
      case Ok(Some(x)) => Ok(x)
    }
  }

  def parseTopLevelThing(iter: ParsingIterator): Result[Option[ITopLevelThingP], IParseError] = {
    val attributes = ArrayBuffer[IAttributeP]()
    while ({
      parseAttribute(iter) match {
        case Err(e) => return Err(e)
        case Ok(Some(x)) => {
          attributes += x
          iter.consumeWhitespace()
          true
        }
        case Ok(None) => false
      }
    }) {}

    parseImpl(iter, attributes.toVector) match {
      case Err(err) => return Err(err)
      case Ok(Some(result)) => return Ok(Some(TopLevelImplP(result)))
      case Ok(None) =>
    }
    parseStruct(iter, attributes.toVector) match {
      case Err(err) => return Err(err)
      case Ok(Some(result)) => return Ok(Some(TopLevelStructP(result)))
      case Ok(None) =>
    }
    parseInterface(iter, attributes.toVector) match {
      case Err(err) => return Err(err)
      case Ok(Some(result)) => return Ok(Some(TopLevelInterfaceP(result)))
      case Ok(None) =>
    }
    parseExportAs(iter, attributes.toVector) match {
      case Err(err) => return Err(err)
      case Ok(Some(result)) => return Ok(Some(TopLevelExportAsP(result)))
      case Ok(None) =>
    }
    parseImport(iter, attributes.toVector) match {
      case Err(err) => return Err(err)
      case Ok(Some(result)) => return Ok(Some(TopLevelImportP(result)))
      case Ok(None) =>
    }
    parseFunction(iter, attributes.toVector, StopBeforeCloseBrace) match {
      case Err(err) => return Err(err)
      case Ok(Some(result)) => return Ok(Some(TopLevelFunctionP(result)))
      case Ok(None) =>
    }
    return Ok(None)
  }

  private[parser] def runParserInner(iter: ParsingIterator): Result[FileP, IParseError] = {
    val topLevelThings = new mutable.MutableList[ITopLevelThingP]()

    iter.consumeWhitespace()

    while (!Parser.atEnd(iter, StopBeforeFileEnd)) {
      parseExpectTopLevelThing(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => {
          topLevelThings += x
          iter.consumeWhitespace()
        }
      }
    }

    val program0 = ast.FileP(topLevelThings.toVector)
    Ok(program0)
  }

  def parseTemplateRules(iter: ParsingIterator):
  Result[Option[TemplateRulesP], IParseError] = {
    val begin = iter.getPos()

    if (!iter.trySkip("^where\\b".r)) {
      return Ok(None)
    }
    val rules = ArrayBuffer[IRulexPR]()

    iter.consumeWhitespace()

    rules +=
      (new TemplexParser().parseRule(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      })

    while (iter.trySkip("^\\s*,".r)) {
      iter.consumeWhitespace()
      rules +=
        (new TemplexParser().parseRule(iter) match {
          case Err(e) => return Err(e)
          case Ok(x) => x
        })
    }

    val end = iter.getPos()

    Ok(Some(TemplateRulesP(RangeP(begin, end), rules.toVector)))
  }

  private def parseCitizenSuffix(iter: ParsingIterator):
  Result[(RangeP, Option[ITemplexPT], Option[TemplateRulesP]), IParseError] = {
    parseTemplateRules(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(r)) => Ok(RangeP(iter.getPos(), iter.getPos()), None, Some(r))
      case Ok(None) => {
        val mutabilityBegin = iter.getPos()
        val maybeMutability =
          if (iter.peek("^\\s*where\\b".r)) {
            None
          } else if (iter.peek("^\\s*\\{".r)) {
            None
          } else {
            new TemplexParser().parseTemplex(iter) match {
              case Err(e) => return Err(e)
              case Ok(x) => Some(x)
            }
          }
        val mutabilityRange = RangeP(mutabilityBegin, iter.getPos())
        iter.consumeWhitespace()
        parseTemplateRules(iter) match {
          case Err(e) => return Err(e)
          case Ok(maybeTemplateRules) => Ok((mutabilityRange, maybeMutability, maybeTemplateRules))
        }
      }
    }
  }

  private[parser] def parseIdentifyingRune(iter: ParsingIterator):
  Result[IdentifyingRuneP, IParseError] = {

    val begin = iter.getPos()

    if (iter.trySkip("^'".r)) {
      iter.consumeWhitespace()

      val name =
        parseFunctionOrLocalOrMemberName(iter) match {
          case Some(n) => n
          case None => return Err(BadRuneNameError(iter.getPos()))
        }

      iter.consumeWhitespace()

      val regionTypeBegin = iter.getPos()
      val attributes =
        Vector(TypeRuneAttributeP(RangeP(regionTypeBegin, iter.getPos()), RegionTypePR)) ++
        (if (iter.trySkip("^ro\\b".r)) {
          Vector(ReadOnlyRuneAttributeP(RangeP(regionTypeBegin, iter.getPos())))
        } else if (iter.trySkip("^rw\\b".r)) {
          Vector(ReadWriteRuneAttributeP(RangeP(regionTypeBegin, iter.getPos())))
        } else if (iter.trySkip("^imm\\b".r)) {
          Vector(ImmutableRuneAttributeP(RangeP(regionTypeBegin, iter.getPos())))
        } else {
          Vector()
        })

      if (iter.trySkip("^\\s*=\\s*".r)) {
        new TemplexParser().parseTemplex(iter) match {
          case Err(e) => return Err(e)
          case Ok(x) => // ignore it
        }
      }

      Ok(IdentifyingRuneP(RangeP(begin, iter.getPos()), name, attributes))
    } else {
      val name =
        parseFunctionOrLocalOrMemberName(iter) match {
          case Some(n) => n
          case None => return Err(BadRuneNameError(iter.getPos()))
        }

      iter.consumeWhitespace()

      val typeBegin = iter.getPos()
      val maybeRuneType =
        Parser.parseRuneType(iter, Vector(StopBeforeCloseChevron, StopBeforeComma, StopBeforeEquals)) match {
          case Err(e) => return Err(e)
          case Ok(Some(x)) => Some(TypeRuneAttributeP(RangeP(typeBegin, iter.getPos()), x))
          case Ok(None) => None
        }
      Ok(IdentifyingRuneP(RangeP(begin, iter.getPos()), name, maybeRuneType.toVector))
    }
  }

  private[parser] def parseIdentifyingRunes(
    iter: ParsingIterator):
  Result[Option[IdentifyingRunesP], IParseError] = {
    val begin = iter.getPos()
    if (!iter.trySkip("^<".r)) {
      return Ok(None)
    }
    iter.consumeWhitespace()
    val runes = mutable.ArrayBuffer[IdentifyingRuneP]()
    while ({
      runes +=
        (parseIdentifyingRune(iter) match {
          case Err(e) => return Err(e)
          case Ok(x) => x
        })
      if (iter.trySkip("^\\s*>".r)) {
        false
      } else if (iter.trySkip("^\\s*,".r)) {
        iter.consumeWhitespace()
        true
      } else {
        return Err(BadRuneEnd(iter.getPos()))
      }
    }) { }
    Ok(Some(IdentifyingRunesP(RangeP(begin, iter.getPos()), runes.toVector)))
  }

  private[parser] def parseStructMember(
    iter: ParsingIterator):
  Result[IStructContent, IParseError] = {
    val begin = iter.getPos()

    val name =
      parseLocalOrMemberName(iter) match {
        case None => return Err(BadStructMember(iter.getPos()))
        case Some(x) => x
      }

    iter.consumeWhitespace()

    val variability = if (iter.trySkip("^!".r)) VaryingP else FinalP

    iter.consumeWhitespace()

    val variadic = iter.trySkip("^\\.\\.".r)

    iter.consumeWhitespace()

    val tyype =
      new TemplexParser().parseTemplex(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    if (!iter.trySkip("^;".r)) {
      return Err(BadMemberEnd(iter.getPos()))
    }

    if (variadic) {
      if (name.str != "_") {
        return Err(VariadicStructMemberHasName(iter.getPos()))
      }

      Ok(VariadicStructMemberP(RangeP(begin, iter.getPos()), variability, tyype))
    } else {
      Ok(NormalStructMemberP(RangeP(begin, iter.getPos()), name, variability, tyype))
    }
  }

  private[parser] def parseStruct(
    iter: ParsingIterator,
    attributes: Vector[IAttributeP]):
  Result[Option[StructP], IParseError] = {
    val begin = iter.getPos()

    if (!iter.trySkip("^struct\\b".r)) {
      return Ok(None)
    }

    iter.consumeWhitespace()

    val name =
      Parser.parseTypeName(iter) match {
        case None => return Err(BadStructName(iter.getPos()))
        case Some(x) => x
      }

    iter.consumeWhitespace()

    val maybeIdentifyingRunes =
      parseIdentifyingRunes(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    iter.consumeWhitespace()

    val (mutabilityRange, maybeMutability, maybeTemplateRules) =
      parseCitizenSuffix(iter) match {
        case Err(e) => return Err(e)
        case Ok((a, b, c)) => (a, b, c)
      }

    iter.consumeWhitespace()

    val contentsBegin = iter.getPos()

    if (!iter.trySkip("^\\{".r)) {
      return Err(BadStructContentsBegin(iter.getPos()))
    }

    val contents = ArrayBuffer[IStructContent]()

    iter.consumeWhitespace()
    while (!Parser.atEnd(iter, StopBeforeCloseBrace)) {
      iter.consumeWhitespace()
      parseTopLevelThing(iter) match {
        case Err(e) => return Err(e)
        case Ok(Some(TopLevelFunctionP(func))) => contents += StructMethodP(func)
        case Ok(Some(_)) => {
          // Ignore these, we have `impl MyInterface;` inside structs in articles
        }
        case Ok(None) => {
          parseStructMember(iter) match {
            case Ok(m) => contents += m
            case Err(e) => return Err(e)
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
        attributes.toVector,
        maybeMutability.getOrElse(MutabilityPT(mutabilityRange, MutableP)),
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

  private def parseInterface(
    iter: ParsingIterator,
    attributes: Vector[IAttributeP]):
  Result[Option[InterfaceP], IParseError] = {
    val begin = iter.getPos()

    if (!iter.trySkip("^interface\\b".r)) {
      return Ok(None)
    }

    iter.consumeWhitespace()

    val name =
      Parser.parseTypeName(iter) match {
        case None => return Err(BadStructName(iter.getPos()))
        case Some(x) => x
      }

    iter.consumeWhitespace()

    val maybeIdentifyingRunes =
      parseIdentifyingRunes(iter) match {
        case Err(e) => vwat()
        case Ok(x) => x
      }

    iter.consumeWhitespace()

    val (mutabilityRange, maybeMutability, maybeTemplateRules) =
      parseCitizenSuffix(iter) match {
        case Err(e) => return Err(e)
        case Ok((a, b, c)) => (a, b, c)
      }

    iter.consumeWhitespace()

    val contentsBegin = iter.getPos()

    if (!iter.trySkip("^\\{".r)) {
      return Err(BadStructContentsBegin(iter.getPos()))
    }

    val methods = ArrayBuffer[FunctionP]()

    while (!Parser.atEnd(iter, StopBeforeCloseBrace)) {
      iter.consumeWhitespace()
      parseTopLevelThing(iter) match {
        case Err(e) => return Err(e)
        case Ok(Some(TopLevelFunctionP(f))) => methods += f
        case Ok(Some(other)) => {
          return Err(UnexpectedTopLevelThing(iter.getPos(), other))
        }
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
        attributes.toVector,
        maybeMutability.getOrElse(MutabilityPT(mutabilityRange, MutableP)),
        maybeIdentifyingRunes,
        maybeTemplateRules,
        methods.toVector)
    Ok(Some(interface))
  }

  private def parseImpl(
    iter: ParsingIterator,
    attributes: Vector[IAttributeP]
  ): Result[Option[ImplP], IParseError] = {
    val begin = iter.getPos()

    if (!iter.trySkip("^impl\\b".r)) {
      return Ok(None)
    }

    iter.consumeWhitespace()

    val maybeIdentifyingRunes =
      parseIdentifyingRunes(iter) match {
        case Err(e) => vwat()
        case Ok(x) => x
      }

    //
    //    iter.consumeWhitespace()
    //
    //    val maybeTemplateRules =
    //      iter.consumeWithCombinator(CombinatorParsers.opt(CombinatorParsers.templateRulesPR)) match {
    //        case Err(e) => vwat()
    //        case Ok(e) => e
    //      }

    iter.consumeWhitespace()

    val interface =
      new TemplexParser().parseTemplex(iter) match {
        case Err(e) => vwat()
        case Ok(e) => e
      }

    iter.consumeWhitespace()

    val struct =
      if (iter.trySkip("^for\\b".r)) {
        iter.consumeWhitespace()

        new TemplexParser().parseTemplex(iter) match {
          case Err(e) => vwat()
          case Ok(e) => Some(e)
        }
      } else {
        None
      }

    val maybeTemplateRules =
      parseTemplateRules(iter) match {
        case Err(e) => return Err(e)
        case Ok(r) => r
      }

    iter.consumeWhitespace()

    if (!iter.trySkip("^;".r)) {
      return Err(NeedSemicolon(iter.getPos()))
    }

    val impl =
      ast.ImplP(
        ast.RangeP(begin, iter.getPos()),
        maybeIdentifyingRunes,
        maybeTemplateRules,
        struct,
        interface,
        attributes)
    Ok(Some(impl))
  }

  private def parseExportAs(
    iter: ParsingIterator,
    attributes: Vector[IAttributeP]):
  Result[Option[ExportAsP], IParseError] = {
    val begin = iter.getPos()

    if (!iter.trySkip("^export\\b".r)) {
      return Ok(None)
    }

    iter.consumeWhitespace()

    if (attributes.nonEmpty) {
      return Err(UnexpectedAttributes(iter.getPos()))
    }

    val templex =
      new TemplexParser().parseTemplex(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    iter.consumeWhitespace()

    if (!iter.trySkip("^as\\b".r)) {
      return Err(BadExportAs(iter.getPos()))
    }

    iter.consumeWhitespace()

    val name =
      Parser.parseTypeName(iter) match {
        case None => return Err(BadExportName(iter.getPos()))
        case Some(n) => n
      }

    if (!iter.trySkip("^;".r)) {
      return Err(BadExportEnd(iter.getPos()))
    }

    Ok(Some(ast.ExportAsP(ast.RangeP(begin, iter.getPos()), templex, name)))
  }

  private def parseImport(
    iter: ParsingIterator,
    attributes: Vector[IAttributeP]):
  Result[Option[ImportP], IParseError] = {
    val begin = iter.getPos()

    if (!iter.trySkip("^import\\b".r)) {
      return Ok(None)
    }

    if (attributes.nonEmpty) {
      return Err(UnexpectedAttributes(iter.getPos()))
    }

    iter.consumeWhitespace()

    val steps = mutable.ArrayBuffer[NameP]()
    while ({
      val stepBegin = iter.getPos()
      val name =
        if (iter.trySkip("^\\*".r)) {
          NameP(RangeP(stepBegin, iter.getPos()), "*")
        } else {
          Parser.parseTypeName(iter) match {
            case None => return Err(BadImportName(iter.getPos()))
            case Some(n) => n
          }
        }
      steps += name
      iter.consumeWhitespace()
      if (iter.trySkip("^\\.".r)) {
        iter.consumeWhitespace()
        true
      } else if (iter.trySkip("^;".r)) {
        false
      } else {
        return Err(BadImportEnd(iter.getPos()))
      }
    }) {}

    val moduleName = steps.head
    val importee = steps.last
    val packageSteps = steps.init.tail
    val imporrt = ast.ImportP(ast.RangeP(begin, iter.getPos()), moduleName, packageSteps.toVector, importee)
    Ok(Some(imporrt))
  }

  // Returns:
  // - The infer-ret range, if any
  def parseAttribute(iter: ParsingIterator):
  Result[Option[IAttributeP], IParseError] = {
    val begin = iter.getPos()
    if (iter.trySkip("^exported\\b".r)) {
      Ok(Some(ExportAttributeP(RangeP(begin, iter.getPos()))))
    } else if (iter.trySkip("^extern\\b".r)) {
      if (iter.trySkip("^\\s*\\(".r)) {
        val nameBegin = iter.getPos()
        if (!iter.trySkip("^\\s*\"".r)) {
          return Err(BadAttributeError(iter.getPos()))
        }
        val name =
          iter.tryy("^[^\"]+".r) match {
            case None => return Err(BadAttributeError(iter.getPos()))
            case Some(s) => s
          }
        if (!iter.trySkip("^\\s*\"".r)) {
          return Err(BadAttributeError(iter.getPos()))
        }
        val nameEnd = iter.getPos()
        if (!iter.trySkip("^\\s*\\)".r)) {
          return Err(BadAttributeError(iter.getPos()))
        }
        Ok(Some(BuiltinAttributeP(RangeP(begin, iter.getPos()), NameP(RangeP(nameBegin, nameEnd), name))))
      } else {
        Ok(Some(ExternAttributeP(RangeP(begin, iter.getPos()))))
      }
    } else if (iter.trySkip("^abstract\\b".r)) {
      Ok(Some(AbstractAttributeP(RangeP(begin, iter.getPos()))))
    } else if (iter.trySkip("^pure\\b".r)) {
      Ok(Some(PureAttributeP(RangeP(begin, iter.getPos()))))
    } else if (iter.trySkip("^sealed\\b".r)) {
      Ok(Some(SealedAttributeP(RangeP(begin, iter.getPos()))))
    } else if (iter.trySkip("^weakable\\b".r)) {
      Ok(Some(WeakableAttributeP(RangeP(begin, iter.getPos()))))
    } else if (iter.trySkip("^#".r)) {
      val dont = iter.trySkip("^\\!".r)
      val name =
        Parser.parseTypeName(iter) match {
          case None => return Err(BadAttributeError(iter.getPos()))
          case Some(x) => x
        }
      val call =
        MacroCallP(ast.RangeP(begin, iter.getPos()), if (dont) DontCallMacro else CallMacro, name)
      Ok(Some(call))
    } else {
      Ok(None)
    }
  }

  private[parser] def parseFunction(
    iter: ParsingIterator,
    attributes: Vector[IAttributeP],
    stopBefore: IStopBefore):
  Result[Option[FunctionP], IParseError] = {
    val funcBegin = iter.getPos()
    if (!iter.trySkip("^(func|funky)\\b".r)) {
      return Ok(None)
    }
    iter.consumeWhitespace()

    val name =
      Parser.parseFunctionOrLocalOrMemberName(iter) match {
        case None => return Err(BadFunctionName(iter.getPos()))
        case Some(n) => n
      }

    iter.consumeWhitespace()

    val maybeIdentifyingRunes =
      parseIdentifyingRunes(iter) match {
        case Err(cpe) => return Err(cpe)
        case Ok(x) => x
      }

    iter.consumeWhitespace()

    val paramsBegin = iter.getPos()
    if (!iter.trySkip("^\\(".r)) {
      return Err(BadFunctionParamsBegin(iter.getPos()))
    }
    val params =
      if (iter.trySkip("^\\s*\\)".r)) {
        ParamsP(RangeP(paramsBegin, iter.getPos()), Vector())
      } else {
        val paramsSoFar = mutable.ArrayBuffer[PatternPP]()
        while ({
          iter.consumeWhitespace()
          val param = new PatternParser().parsePattern(iter) match { case Err(e) => return Err(e) case Ok(x) => x }
          paramsSoFar += param
          iter.consumeWhitespace()
          if (iter.trySkip("^\\s*,".r)) {
            true
          } else if (iter.trySkip("^\\s*\\)".r)) {
            false
          } else {
            return Err(BadFunctionAfterParam(iter.getPos()))
          }
        }) {}
        ParamsP(RangeP(paramsBegin, iter.getPos()), paramsSoFar.toVector)
      }

    iter.consumeWhitespace()

    val maybeDefaultRegion =
      new TemplexParser().parseRegion(iter) match {
        case Err(cpe) => return Err(cpe)
        case Ok(x) => x
      }

    iter.consumeWhitespace()

    val retBegin = iter.getPos()
    val (maybeTemplateRules, maybeInferRet, maybeReturnType) =
      parseTemplateRules(iter) match {
        case Err(e) => return Err(e)
        case Ok(Some(templateRules)) => (Some(templateRules), None, None)
        case Ok(None) => {
          val (maybeInferRet: Option[UnitP], maybeReturnType: Option[ITemplexPT]) =
            if (iter.trySkip("^\\s*infer-ret\\b".r)) {
              (Some(UnitP(RangeP(retBegin, iter.getPos()))), None)
            } else if (iter.peek("^\\s*where\\b".r)) {
              (None, None)
            } else if (iter.peek("^\\s*\\{".r)) {
              (None, None)
            } else if (iter.peek("^\\s*;".r)) {
              (None, None)
            } else {
              iter.consumeWhitespace()
              new TemplexParser().parseTemplex(iter) match {
                case Err(e) => return Err(e)
                case Ok(x) => (None, Some(x))
              }
            }

          iter.consumeWhitespace()

          parseTemplateRules(iter) match {
            case Err(e) => return Err(e)
            case Ok(maybeTemplateRules) => {
              (maybeTemplateRules, maybeInferRet, maybeReturnType)
            }
          }
        }
      }
    val retEnd = iter.getPos()

    iter.consumeWhitespace()

    val header =
      FunctionHeaderP(
        ast.RangeP(funcBegin, iter.getPos()),
        Some(name),
        attributes,
        maybeIdentifyingRunes,
        maybeTemplateRules,
        Some(params),
        FunctionReturnP(
          ast.RangeP(retBegin, retEnd), maybeInferRet, maybeReturnType))

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
      new ExpressionParser(opts).parseBlockContents(iter, StopBeforeCloseBrace) match {
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

class ParserCompilation(
  opts: GlobalOptions,
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]]
) {
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
    Profiler.frame(() => {
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
          new Parser(opts).runParserForProgramAndCommentRanges(code) match {
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
    })
  }

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
          loadAndParse(packagesToBuild, packageToContentsResolver) match {
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

object Parser {
  def atEnd(iter: ParsingIterator, stopBefore: IStopBefore): Boolean = {
    if (iter.peek("^\\s*$".r)) {
      return true
    }
    stopBefore match {
      case StopBeforeComma => iter.peek("^\\s*,".r)
      case StopBeforeEquals => iter.peek("^\\s*=".r)
      case StopBeforeCloseBrace => iter.peek("^\\s*\\}".r)
      case StopBeforeCloseParen => iter.peek("^\\s*\\)".r)
      case StopBeforeCloseSquare => iter.peek("^\\s*\\]".r)
      case StopBeforeCloseChevron => iter.peek("^(>|\\s+>\\S)".r)
      case StopBeforeOpenBrace => iter.peek("^\\s*\\{".r)
      case StopBeforeFileEnd => false
    }
  }

  def atEnd(iter: ParsingIterator, stopBefore: Vector[IStopBefore]): Boolean = {
    stopBefore.exists(atEnd(iter, _))
  }

  def parseFunctionOrLocalOrMemberName(iter: ParsingIterator): Option[NameP] = {
    val begin = iter.getPos()
    iter.tryy("""^(<=>|<=|<|>=|>|===|==|!=|[^\s\.\!\$\&\,\:\(\)\;\[\]\{\}\'\@\^\"\<\>\=\`]+)""".r) match {
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

  def parseRuneType(iter: ParsingIterator, stopBefore: Vector[IStopBefore]):
  Result[Option[ITypePR], IParseError] = {
    if (atEnd(iter, stopBefore)) {
      return Ok(None)
    }
    iter.tryy("^\\w+".r) match {
      case Some("int") => Ok(Some(IntTypePR))
      case Some("Ref") => Ok(Some(CoordTypePR))
      case Some("Kind") => Ok(Some(KindTypePR))
      case Some("Prot") => Ok(Some(PrototypeTypePR))
      case Some("RefList") => Ok(Some(CoordListTypePR))
      case Some("Ownership") => Ok(Some(OwnershipTypePR))
      case Some("Variability") => Ok(Some(VariabilityTypePR))
      case Some("Mutability") => Ok(Some(MutabilityTypePR))
      case Some("Location") => Ok(Some(LocationTypePR))
      case _ => return Err(BadRuneTypeError(iter.getPos()))
    }
  }

  sealed trait IParsedNumber
  case class ParsedInteger(range: RangeP, int: Long, bits: Int) extends IParsedNumber
  case class ParsedDouble(range: RangeP, double: Double, bits: Int) extends IParsedNumber

  def parseNumber(originalIter: ParsingIterator): Result[Option[IParsedNumber], IParseError] = {
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
      Ok(Some(ParsedInteger(RangeP(begin, iter.getPos()), integer, defaultBits)))
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
      Ok(Some(ParsedDouble(RangeP(begin, iter.getPos()), result, defaultBits)))
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

      Ok(Some(ParsedInteger(RangeP(begin, iter.getPos()), result, bits)))
    }
  }
}

case class InputException(message: String) extends Throwable {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def toString: String = message
}
