package dev.vale.parsing

import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast._
import dev.vale.parsing.templex.TemplexParser
import dev.vale._
import dev.vale.lexing._
import dev.vale.parsing.ast._
import dev.vale.von.{JsonSyntax, VonPrinter}

import scala.collection.immutable.{List, Map}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex


class Parser(interner: Interner, opts: GlobalOptions) {
  val keywords = new Keywords(interner)
  val templexParser = new TemplexParser(interner, keywords)
  val patternParser = new PatternParser(interner, keywords, templexParser)
  val expressionParser = new ExpressionParser(interner, keywords, opts, patternParser, templexParser)

//  def runParserForProgramAndCommentRanges(codeWithComments: String): Result[(FileP, Vector[(Int, Int)]), IParseError] = {
//    Profiler.frame(() => {
//      val regex = "(\\.\\.\\.|//[^\\r\\n]*|«\\w+»)".r
//      val commentRanges = regex.findAllMatchIn(codeWithComments).map(mat => (mat.start, mat.end)).toVector
//      var code = codeWithComments
//      commentRanges.foreach({ case (begin, end) =>
//        code = code.substring(0, begin) + repeatStr(" ", (end - begin)) + code.substring(end)
//      })
//      val codeWithoutComments = code
//
//      runParser(codeWithoutComments) match {
//        case f@Err(err) => Err(err)
//        case Ok(program0) => Ok((program0, commentRanges))
//      }
//    })
//  }

  private[parsing] def parseDenizen(denizen: IDenizenL): Result[IDenizenP, IParseError] = {
    denizen match {
      case TopLevelFunctionL(f @ FunctionL(_, _, _)) => parseFunction(f).map(TopLevelFunctionP)
    }
  }

  def parseTemplateRules(node: INodeLE):
  Result[Option[TemplateRulesP], IParseError] = {
    vimpl()
//    val rules = ArrayBuffer[IRulexPR]()
//
//    rules +=
//      (templexParser.parseRule(iter) match {
//        case Err(e) => return Err(e)
//        case Ok(x) => x
//      })
//
//    while (iter.trySkip(",")) {
//      rules +=
//        (templexParser.parseRule(iter) match {
//          case Err(e) => return Err(e)
//          case Ok(x) => x
//        })
//    }
//
//    Ok(Some(TemplateRulesP(node.range, rules.toVector)))
  }

  private def parseCitizenSuffix(iter: ScrambleIterator):
  Result[(RangeL, Option[ITemplexPT], Option[TemplateRulesP]), IParseError] = {
    vimpl()
//    parseTemplateRules(iter) match {
//      case Err(e) => return Err(e)
//      case Ok(Some(r)) => Ok(RangeL(iter.getPos(), iter.getPos()), None, Some(r))
//      case Ok(None) => {
//        val mutabilityBegin = iter.getPos()
//        val maybeMutability =
//          if (iter.peek(() => "^\\s*where")) {
//            None
//          } else if (iter.peek(() => "^\\s*\\{")) {
//            None
//          } else {
//            templexParser.parseTemplex(iter) match {
//              case Err(e) => return Err(e)
//              case Ok(x) => Some(x)
//            }
//          }
//        val mutabilityRange = RangeL(mutabilityBegin, iter.getPos())
//
//        parseTemplateRules(iter) match {
//          case Err(e) => return Err(e)
//          case Ok(maybeTemplateRules) => Ok((mutabilityRange, maybeMutability, maybeTemplateRules))
//        }
//      }
//    }
  }

  val ro = interner.intern(StrI("ro"))
  val rw = interner.intern(StrI("rw"))
  val imm = interner.intern(StrI("imm"))

  private[parsing] def parseIdentifyingRune(node: INodeLE):
  Result[IdentifyingRuneP, IParseError] = {
    val scramble =
      node match {
        case s @ ScrambleLE(_, _) => s
        case other => ScrambleLE(other.range, Array(other))
      }
    val iter = new ScrambleIterator(scramble, 0, scramble.elements.length)
    if (iter.trySkipSymbol('\'')) {
      val name =
        iter.nextWord() match {
          case Some(n) => n
          case None => return Err(BadRuneNameError(iter.getPos()))
        }

      val regionTypeBegin = iter.getPos()
      val attributes =
        Vector(TypeRuneAttributeP(RangeL(regionTypeBegin, iter.getPos()), RegionTypePR)) ++
        (if (iter.trySkipWord(ro)) {
          Vector(ReadOnlyRuneAttributeP(RangeL(regionTypeBegin, iter.getPos())))
        } else if (iter.trySkipWord(rw)) {
          Vector(ReadWriteRuneAttributeP(RangeL(regionTypeBegin, iter.getPos())))
        } else if (iter.trySkipWord(imm)) {
          Vector(ImmutableRuneAttributeP(RangeL(regionTypeBegin, iter.getPos())))
        } else {
          Vector()
        })

      if (iter.trySkipSymbol('=')) {
        templexParser.parseTemplex(iter) match {
          case Err(e) => return Err(e)
          case Ok(x) => // ignore it
        }
      }

      Ok(IdentifyingRuneP(node.range, NameP(name.range, name.str), attributes))
    } else {
      val name =
        iter.advance() match {
          case WordLE(range, str) => NameP(range, str)
          case _ => return Err(BadRuneNameError(iter.getPos()))
        }

      val typeBegin = iter.getPos()
      val maybeRuneType =
        templexParser.parseRuneType(iter) match {
          case Err(e) => return Err(e)
          case Ok(Some(x)) => Some(ast.TypeRuneAttributeP(RangeL(typeBegin, iter.getPos()), x))
          case Ok(None) => None
        }
      Ok(IdentifyingRuneP(node.range, name, maybeRuneType.toVector))
    }
  }

  private[parsing] def parseIdentifyingRunes(node: AngledLE):
  Result[IdentifyingRunesP, IParseError] = {
    val runesP =
      U.map(node.contents.elements, (inner: INodeLE) => {
        parseIdentifyingRune(inner) match {
          case Err(e) => return Err(e)
          case Ok(x) => x
        }
      })

    Ok(IdentifyingRunesP(node.range, runesP.toVector))
  }

  private[parsing] def parseStructMember(
    iter: ScrambleIterator):
  Result[IStructContent, IParseError] = {
    val begin = iter.getPos()

    val name =
      iter.nextWord() match {
        case None => return Err(BadStructMember(iter.getPos()))
        case Some(WordLE(range, str)) => NameP(range, str)
      }

    val variability = if (iter.trySkipSymbol('!')) VaryingP else FinalP

    val variadic =
      iter.peek(2) match {
        case Array(Some(SymbolLE(_, '.')), Some(SymbolLE(_, '.'))) => {
          iter.advance()
          iter.advance()
          true
        }
        case _ => false
      }

    val tyype =
      templexParser.parseTemplex(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    if (variadic) {
      if (name.str != keywords.UNDERSCORE) {
        return Err(VariadicStructMemberHasName(iter.getPos()))
      }

      Ok(VariadicStructMemberP(RangeL(begin, iter.getPos()), variability, tyype))
    } else {
      Ok(NormalStructMemberP(RangeL(begin, iter.getPos()), name, variability, tyype))
    }
  }

  def parseStruct(functionL: StructL):
  Result[StructP, IParseError] = {
    Profiler.frame(() => {
      val StructL(structRange, nameL, attributesL, maybeMutabilityL, maybeIdentifyingRunesL, maybeTemplateRulesL, contentsL) = functionL

      val maybeIdentifyingRunes =
        maybeIdentifyingRunesL.map(userSpecifiedIdentifyingRunes => {
          parseIdentifyingRunes(userSpecifiedIdentifyingRunes) match {
            case Err(cpe) => return Err(cpe)
            case Ok(x) => x
          }
        })


      val maybeTemplateRulesP =
        maybeTemplateRulesL.map(templateRulesScramble => {
          val elementsPR =
            U.map[ScrambleIterator, IRulexPR](
              new ScrambleIterator(templateRulesScramble).splitOnSymbol(',', false),
              ruleIter => {
                templexParser.parseRule(ruleIter) match {
                  case Err(e) => return Err(e)
                  case Ok(x) => x
                }
              })
          TemplateRulesP(templateRulesScramble.range, elementsPR.toVector)
        })

      val attributesP =
        U.map[IAttributeL, IAttributeP](
          attributesL,
          attributeL => {
            parseAttribute(attributeL) match {
              case Err(e) => return Err(e)
              case Ok(x) => x
            }
          })

      val maybeMutabilityP =
        maybeMutabilityL.map(returnTypeL => {
          val scramble =
            returnTypeL match {
              case s @ ScrambleLE(_, _) => s
              case other => ScrambleLE(other.range, Array(other))
            }
          templexParser.parseTemplex(new ScrambleIterator(scramble, 0, scramble.elements.length)) match {
            case Err(e) => return Err(e)
            case Ok(x) => x
          }
        })

      val membersP =
        StructMembersP(
          contentsL.range,
          U.map[ScrambleIterator, IStructContent](
            new ScrambleIterator(contentsL).splitOnSymbol(';', false),
            member => {
              parseStructMember(member) match {
                case Err(e) => return Err(e)
                case Ok(x) => x
              }
            }).toVector)

      val struct =
        StructP(
          structRange,
          toName(nameL),
          attributesP.toVector,
          maybeMutabilityP,
          maybeIdentifyingRunes,
          maybeTemplateRulesP,
          membersP)
      Ok(struct)
    })
  }

  private[parsing] def parseStruct(
    iter: ScrambleIterator,
    begin: Int,
    attributes: Vector[IAttributeP]):
  Result[Option[StructP], IParseError] = {
    vimpl()

//    if (!iter.trySkip("struct")) {
//      return Ok(None)
//    }
//
//    val name =
//      Parser.parseTypeName(iter) match {
//        case None => return Err(BadStructName(iter.getPos()))
//        case Some(x) => x
//      }
//
//    val maybeIdentifyingRunes =
//      parseIdentifyingRunes(iter) match {
//        case Err(e) => return Err(e)
//        case Ok(x) => x
//      }
//
//    val (mutabilityRange, maybeMutability, maybeTemplateRules) =
//      parseCitizenSuffix(iter) match {
//        case Err(e) => return Err(e)
//        case Ok((a, b, c)) => (a, b, c)
//      }
//
//
//
//    val contentsBegin = iter.getPos()
//
//    if (!iter.trySkip("\\{")) {
//      return Err(BadStructContentsBegin(iter.getPos()))
//    }
//
//    val contents = ArrayBuffer[IStructContent]()
//
//
//    while (!Parser.atEnd(iter, StopBeforeCloseBrace)) {
//
//      parseDenizen(iter) match {
//        case Err(e) => return Err(e)
//        case Ok(Some(TopLevelFunctionP(func))) => contents += StructMethodP(func)
//        case Ok(Some(_)) => {
//          // Ignore these, we have `impl MyInterface;` inside structs in articles
//        }
//        case Ok(None) => {
//          parseStructMember(iter) match {
//            case Ok(m) => contents += m
//            case Err(e) => return Err(e)
//          }
//        }
//      }
//    }
//
//
//    if (!iter.trySkip("\\}")) {
//      return Err(BadStructContentsEnd(iter.getPos()))
//    }
//
//    val contentsEnd = iter.getPos()
//
//    val struct =
//      ast.StructP(
//        ast.RangeL(begin, iter.getPos()),
//        name,
//        attributes.toVector,
//        maybeMutability.getOrElse(MutabilityPT(mutabilityRange, MutableP)),
//        maybeIdentifyingRunes,
//        maybeTemplateRules,
//        StructMembersP(ast.RangeL(contentsBegin, contentsEnd),
//        contents.toVector))
//    Ok(Some(struct))
  }

  private def parseInterface(
    iter: ScrambleIterator,
    begin: Int,
    attributes: Vector[IAttributeP]):
  Result[Option[InterfaceP], IParseError] = {
    vimpl()

//    if (!iter.trySkip("interface")) {
//      return Ok(None)
//    }
//
//    val name =
//      Parser.parseTypeName(iter) match {
//        case None => return Err(BadStructName(iter.getPos()))
//        case Some(x) => x
//      }
//
//    val maybeIdentifyingRunes =
//      parseIdentifyingRunes(iter) match {
//        case Err(e) => vwat()
//        case Ok(x) => x
//      }
//
//    val (mutabilityRange, maybeMutability, maybeTemplateRules) =
//      parseCitizenSuffix(iter) match {
//        case Err(e) => return Err(e)
//        case Ok((a, b, c)) => (a, b, c)
//      }
//
//
//
//    val contentsBegin = iter.getPos()
//
//    if (!iter.trySkip("\\{")) {
//      return Err(BadStructContentsBegin(iter.getPos()))
//    }
//
//    val methods = ArrayBuffer[FunctionP]()
//
//    while (!Parser.atEnd(iter, StopBeforeCloseBrace)) {
//
//      parseDenizen(iter) match {
//        case Err(e) => return Err(e)
//        case Ok(Some(TopLevelFunctionP(f))) => methods += f
//        case Ok(Some(other)) => {
//          return Err(UnexpectedDenizen(iter.getPos(), other))
//        }
//        case Ok(None) => return Err(BadInterfaceMember(iter.getPos()))
//      }
//    }
//
//
//
//    if (!iter.trySkip("\\}")) {
//      return Err(BadStructContentsEnd(iter.getPos()))
//    }
//
//    val contentsEnd = iter.getPos()
//
//    val interface =
//      ast.InterfaceP(
//        ast.RangeL(begin, iter.getPos()),
//        name,
//        attributes.toVector,
//        maybeMutability.getOrElse(ast.MutabilityPT(mutabilityRange, MutableP)),
//        maybeIdentifyingRunes,
//        maybeTemplateRules,
//        methods.toVector)
//    Ok(Some(interface))
  }

  private[parsing] def parseImpl(
    implL: ImplL
  ): Result[ImplP, IParseError] = {
    vimpl()

//    if (!iter.trySkip("impl")) {
//      return Ok(None)
//    }
//
//    val maybeIdentifyingRunes =
//      parseIdentifyingRunes(iter) match {
//        case Err(e) => vwat()
//        case Ok(x) => x
//      }
//
//    //
//    //
//    //
//    //    val maybeTemplateRules =
//    //      iter.consumeWithCombinator(CombinatorParsers.opt(CombinatorParsers.templateRulesPR)) match {
//    //        case Err(e) => vwat()
//    //        case Ok(e) => e
//    //      }
//
//    val interface =
//      templexParser.parseTemplex(iter) match {
//        case Err(e) => vwat()
//        case Ok(e) => e
//      }
//
//
//
//    val struct =
//      if (iter.trySkip("for")) {
//
//
//        templexParser.parseTemplex(iter) match {
//          case Err(e) => vwat()
//          case Ok(e) => Some(e)
//        }
//      } else {
//        None
//      }
//
//    val maybeTemplateRules =
//      parseTemplateRules(iter) match {
//        case Err(e) => return Err(e)
//        case Ok(r) => r
//      }
//
//    if (!iter.trySkipSymbol(';')) {
//      return Err(NeedSemicolon(iter.getPos()))
//    }
//
//    val impl =
//      ast.ImplP(
//        ast.RangeL(begin, iter.getPos()),
//        maybeIdentifyingRunes,
//        maybeTemplateRules,
//        struct,
//        interface,
//        attributes)
//    Ok(Some(impl))
  }

  val export = interner.intern(StrI("export"))

  private def parseExportAs(
    iter: ScrambleIterator,
    begin: Int,
    attributes: Vector[IAttributeP]):
  Result[Option[ExportAsP], IParseError] = {
    vimpl()

//    if (!iter.trySkipWord(export)) {
//      return Ok(None)
//    }
//
//
//
//    if (attributes.nonEmpty) {
//      return Err(UnexpectedAttributes(iter.getPos()))
//    }
//
//    val templex =
//      templexParser.parseTemplex(iter) match {
//        case Err(e) => return Err(e)
//        case Ok(x) => x
//      }
//
//
//
//    if (!iter.trySkipWord(as)) {
//      return Err(BadExportAs(iter.getPos()))
//    }
//
//
//
//    val name =
//      Parser.parseTypeName(iter) match {
//        case None => return Err(BadExportName(iter.getPos()))
//        case Some(n) => n
//      }
//
//    if (!iter.trySkipSymbol(';')) {
//      return Err(BadExportEnd(iter.getPos()))
//    }
//
//    Ok(Some(ast.ExportAsP(ast.RangeL(begin, iter.getPos()), templex, name)))
  }

  val as = interner.intern(StrI("as"))
  val impoort = interner.intern(StrI("import"))

  private def parseImport(
    iter: ScrambleIterator,
    begin: Int,
    attributes: Vector[IAttributeP]):
  Result[Option[ImportP], IParseError] = {
    vimpl()

//    if (!iter.trySkipWord(impoort)) {
//      return Ok(None)
//    }
//
//    if (attributes.nonEmpty) {
//      return Err(UnexpectedAttributes(iter.getPos()))
//    }
//
//    val steps = mutable.ArrayBuffer[NameP]()
//    while ({
//      val stepBegin = iter.getPos()
//      val name =
//        if (iter.trySkip("\\*")) {
//          NameP(RangeL(stepBegin, iter.getPos()), "*")
//        } else {
//          Parser.parseTypeName(iter) match {
//            case None => return Err(BadImportName(iter.getPos()))
//            case Some(n) => n
//          }
//        }
//      steps += name
//
//      if (iter.trySkip("\\.")) {
//
//        true
//      } else if (iter.trySkip(";")) {
//        false
//      } else {
//        return Err(BadImportEnd(iter.getPos()))
//      }
//    }) {}
//
//    val moduleName = steps.head
//    val importee = steps.last
//    val packageSteps = steps.init.tail
//    val imporrt = ast.ImportP(ast.RangeL(begin, iter.getPos()), moduleName, packageSteps.toVector, importee)
//    Ok(Some(imporrt))
  }

  // Returns:
  // - The infer-return range, if any
  def parseAttribute(attrL: IAttributeL):
  Result[IAttributeP, IParseError] = {
    attrL match {
      case AbstractAttributeL(range) => Ok(AbstractAttributeP(range))
      case ExternAttributeL(range) => Ok(ExternAttributeP(range))
      case ExportAttributeL(range) => Ok(ExportAttributeP(range))
      case PureAttributeL(range) => Ok(PureAttributeP(range))
      case WeakableAttributeL(range) => Ok(WeakableAttributeP(range))
      case SealedAttributeL(range) => Ok(SealedAttributeP(range))
      case MacroCallL(range, inclusion, name) => {
        Ok(
          MacroCallP(
            range,
            inclusion match {
              case CallMacroL => CallMacroP
              case DontCallMacroL => DontCallMacroP
            },
            toName(name)))
      }
    }
  }

  def parseFunction(functionL: FunctionL):
  Result[FunctionP, IParseError] = {
    Profiler.frame(() => {
      val FunctionL(funcRangeL, headerL, maybeBodyL) = functionL
      val FunctionHeaderL(headerRangeL, nameL, attributesL, maybeIdentifyingRunesL, maybeTemplateRulesL, paramsL, returnL) = headerL
      val FunctionReturnL(returnRangeL, maybeInferRetL, maybeReturnTypeL) = returnL

      val maybeIdentifyingRunes =
        maybeIdentifyingRunesL.map(userSpecifiedIdentifyingRunes => {
          parseIdentifyingRunes(userSpecifiedIdentifyingRunes) match {
            case Err(cpe) => return Err(cpe)
            case Ok(x) => x
          }
        })

      val paramsP =
        ParamsP(
          paramsL.range,
          U.map[ScrambleIterator, PatternPP](
            new ScrambleIterator(paramsL.contents).splitOnSymbol(',', false),
            patternIter => {
              patternParser.parsePattern(patternIter) match {
                case Err(e) => return Err(e)
                case Ok(x) => x
              }
            }).toVector)

      val maybeTemplateRulesP =
        maybeTemplateRulesL.map(templateRules => {
          TemplateRulesP(
            templateRules.range,
            U.map[ScrambleIterator, IRulexPR](
              new ScrambleIterator(templateRules).splitOnSymbol(',', false),
              templexL => {
                templexParser.parseRule(templexL) match {
                  case Err(e) => return Err(e)
                  case Ok(x) => x
                }
              }).toVector)
        })

      val attributesP =
        U.map[IAttributeL, IAttributeP](
          attributesL,
          attributeL => {
            parseAttribute(attributeL) match {
              case Err(e) => return Err(e)
              case Ok(x) => x
            }
          })

      val maybeReturnTypeP =
        maybeReturnTypeL.map(returnTypeL => {
          val scramble =
            returnTypeL match {
              case s @ ScrambleLE(_, _) => s
              case other => ScrambleLE(other.range, Array(other))
            }
          templexParser.parseTemplex(new ScrambleIterator(scramble, 0, scramble.elements.length)) match {
            case Err(e) => return Err(e)
            case Ok(x) => x
          }
        })

      val header =
        FunctionHeaderP(
          headerL.range,
          Some(toName(nameL)),
          attributesP.toVector,
          maybeIdentifyingRunes,
          maybeTemplateRulesP,
          Some(paramsP),
          FunctionReturnP(
            returnRangeL, maybeInferRetL, maybeReturnTypeP))

      val bodyP =
        maybeBodyL.map(bodyL => {
          val FunctionBodyL(maybeDefaultRegionL, blockL) = bodyL
          val maybeDefaultRegionP =
            maybeDefaultRegionL.map(defaultRegionL => {
              templexParser.parseRegion(defaultRegionL) match {
                case Err(cpe) => return Err(cpe)
                case Ok(x) => x
              }
            })
          val statementsP =
            expressionParser.parseBlock(blockL) match {
              case Err(err) => return Err(err)
              case Ok(result) => result
            }
          BlockPE(blockL.range, statementsP)
        })

      Ok(FunctionP(funcRangeL, header, bodyP))
    })
  }

  def toName(wordL: WordLE): NameP = {
    val WordLE(range, s) = wordL
    NameP(range, s)
  }
}

class ParserCompilation(
  opts: GlobalOptions,
  interner: Interner,
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]]
) {
  def loadAndParse(
    neededPackages: Vector[PackageCoordinate],
    resolver: IPackageResolver[Map[String, String]]):
  Result[(FileCoordinateMap[String], FileCoordinateMap[(FileP, Vector[(Int, Int)])]), FailedParse] = {
    vimpl()

//    vassert(neededPackages.size == neededPackages.distinct.size, "Duplicate modules in: " + neededPackages.mkString(", "))
//
////    neededPackages.foreach(x => println("Originally requested package: " + x))
//
//    val foundCodeMap = new FileCoordinateMap[String]()
//    val parsedMap = new FileCoordinateMap[(FileP, Vector[(Int, Int)])]()
//    loadAndParseIteration(neededPackages, foundCodeMap, parsedMap, resolver) match {
//      case Err(e) => return Err(e)
//      case Ok(()) =>
//    }
//    Ok((foundCodeMap, parsedMap))
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
//        vfail(ParseErrorHumanizer.humanize(codeMap, fileCoord, err))
        vimpl()
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
//  def atEnd(iter: ScrambleIterator, stopBefore: IStopBefore): Boolean = {
//    if (iter.peek(() => "^\\s*$")) {
//      return true
//    }
//    stopBefore match {
//      case StopBeforeComma => iter.peek(() => "^\\s*,")
//      case StopBeforeEquals => iter.peek(() => "^\\s*=")
//      case StopBeforeCloseBrace => iter.peek(() => "^\\s*\\}")
//      case StopBeforeCloseParen => iter.peek(() => "^\\s*\\)")
//      case StopBeforeCloseSquare => iter.peek(() => "^\\s*\\]")
//      case StopBeforeCloseChevron => iter.peek(() => "^(>|\\s+>\\S)")
//      case StopBeforeOpenBrace => iter.peek(() => "^\\s*\\{")
//      case StopBeforeFileEnd => false
//    }
//  }
//
//  def atEnd(iter: ScrambleIterator, stopBefore: Vector[IStopBefore]): Boolean = {
//    stopBefore.exists(atEnd(iter, _))
//  }
//
//  def parseFunctionOrLocalOrMemberName(iter: ScrambleIterator): Option[NameP] = {
//    val begin = iter.getPos()
//    iter.tryy("""^(<=>|<=|<|>=|>|===|==|!=|[^\s\.\!\$\&\,\:\(\)\;\[\]\{\}\'\@\^\"\<\>\=\`]+)""") match {
//      case Some(str) => Some(NameP(RangeL(begin, iter.getPos()), str))
//      case None => None
//    }
//  }
//
//  def parseLocalOrMemberName(iter: ScrambleIterator): Option[NameP] = {
//    val begin = iter.getPos()
//    iter.tryy("[A-Za-z_][A-Za-z0-9_]*") match {
//      case Some(str) => Some(NameP(RangeL(begin, iter.getPos()), str))
//      case None => None
//    }
//  }

//  def parseTypeName(iter: ScrambleIterator): Option[NameP] = {
//    val begin = iter.getPos()
//    iter.tryy("[A-Za-z_][A-Za-z0-9_]*") match {
//      case Some(str) => Some(NameP(RangeL(begin, iter.getPos()), str))
//      case None => None
//    }
//  }
}
