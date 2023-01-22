package dev.vale.parsing

import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast._
import dev.vale.parsing.templex.TemplexParser
import dev.vale._
import dev.vale.lexing._
import dev.vale.parsing.Parser.{parsePrefixingRegion, parseRegion}
import dev.vale.parsing.ast._
import dev.vale.von.{JsonSyntax, VonPrinter}

import scala.collection.immutable.{List, Map}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex


class Parser(interner: Interner, keywords: Keywords, opts: GlobalOptions) {
  val templexParser = new TemplexParser(interner, keywords)
  val patternParser = new PatternParser(interner, keywords, templexParser)
  val expressionParser = new ExpressionParser(interner, keywords, opts, patternParser, templexParser)

  private[parsing] def parseGenericParameter(iter: ScrambleIterator):
  Result[GenericParameterP, IParseError] = {
    val range = iter.range

    val maybeCoordRegion =
      parsePrefixingRegion(iter) match {
        case Err(x) => return Err(x)
        case Ok(maybeRegion) => maybeRegion
      }

    val (name, maybeType, attributes) =
      parseRegion(iter) match {
        case Err(x) => return Err(x)
        case Ok(None) => {
          val name =
            iter.peek() match {
              case Some(WordLE(range, str)) => {
                iter.advance()
                NameP(range, str)
              }
              case _ => return Err(BadRuneNameError(iter.getPos()))
            }

          val typeBegin = iter.getPos()
          val maybeRuneType =
            templexParser.parseRuneType(iter) match {
              case Err(e) => return Err(e)
              case Ok(Some(x)) => Some(GenericParameterTypeP(RangeL(typeBegin, iter.getPrevEndPos()), x))
              case Ok(None) => None
            }

          val maybeAttrs =
            iter.trySkipWord(keywords.imm) match {
              case Some(range) => Vector(ImmutableRuneAttributeP(range))
              case None => Vector()
            }

          (name, maybeRuneType, maybeAttrs)
        }
        case Ok(Some(region)) => {
          val attributes =
              (iter.trySkipWord(keywords.ro) match {
                case Some(range) => Vector(ReadOnlyRegionRuneAttributeP(range))
                case None => {
                  iter.trySkipWord(keywords.rw) match {
                    case Some(range) => Vector(ReadWriteRegionRuneAttributeP(range))
                    case None => {
                      iter.trySkipWord(keywords.imm) match {
                        case Some(range) => Vector(ImmutableRegionRuneAttributeP(range))
                        case None => Vector()
                      }
                    }
                  }
                }
              })

          val tyype =
            GenericParameterTypeP(RangeL(range.begin, iter.getPrevEndPos()), RegionTypePR)
          (region.name, Some(tyype), attributes)
        }
      }

    val maybeDefaultPT =
      if (iter.trySkipSymbol('=')) {
        templexParser.parseTemplex(iter) match {
          case Err(e) => return Err(e)
          case Ok(x) => Some(x)
        }
      } else {
        None
      }

    Ok(GenericParameterP(range, NameP(name.range, name.str), maybeType, maybeCoordRegion, attributes, maybeDefaultPT))
  }

  private[parsing] def parseIdentifyingRunes(node: AngledLE):
  Result[GenericParametersP, IParseError] = {
    val runesP =
      U.map[ScrambleIterator, GenericParameterP](
        new ScrambleIterator(node.contents).splitOnSymbol(',', false),
        inner => {
        parseGenericParameter(inner) match {
          case Err(e) => return Err(e)
          case Ok(x) => x
        }
      })

    Ok(GenericParametersP(node.range, runesP.toVector))
  }

  private[parsing] def parseStructMember(
    iter: ScrambleIterator):
  Result[IStructContent, IParseError] = {
    val begin = iter.getPos()

    val name =
      iter.peek() match {
        case Some(ParsedIntegerLE(range, int, _)) => {
          // This is just temporary until we add proper variadics again, see TAVWG.
          iter.advance()
          NameP(range, interner.intern(StrI(int.toString)))
        }
        case _ => {
          iter.nextWord() match {
            case None => return Err(BadStructMember(iter.getPos()))
            case Some(WordLE(range, str)) => NameP(range, str)
          }
        }
      }

    val variability = if (iter.trySkipSymbol('!')) VaryingP else FinalP

    val variadic =
      iter.peek2() match {
        case (Some(SymbolLE(_, '.')), Some(SymbolLE(_, '.'))) => {
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

      Ok(VariadicStructMemberP(RangeL(begin, iter.getPrevEndPos()), variability, tyype))
    } else {
      Ok(NormalStructMemberP(RangeL(begin, iter.getPrevEndPos()), name, variability, tyype))
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
              case other => ScrambleLE(other.range, Vector(other))
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
          None,
          contentsL.range,
          membersP)
      Ok(struct)
    })
  }

  def parseInterface(interfaceL: InterfaceL):
  Result[InterfaceP, IParseError] = {
    Profiler.frame(() => {
      val InterfaceL(interfaceRange, nameL, attributesL, maybeMutabilityL, maybeIdentifyingRunesL, maybeTemplateRulesL, bodyRange, methodsL) = interfaceL

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
              case other => ScrambleLE(other.range, Vector(other))
            }
          templexParser.parseTemplex(new ScrambleIterator(scramble, 0, scramble.elements.length)) match {
            case Err(e) => return Err(e)
            case Ok(x) => x
          }
        })

      val membersP =
          U.map[FunctionL, FunctionP](
            methodsL,
            methodL => {
              parseFunction(methodL, true) match {
                case Err(e) => return Err(e)
                case Ok(x) => x
              }
            })

      val interface =
        InterfaceP(
          interfaceRange,
          toName(nameL),
          attributesP.toVector,
          maybeMutabilityP,
          maybeIdentifyingRunes,
          maybeTemplateRulesP,
          None,
          bodyRange,
          membersP.toVector)
      Ok(interface)
    })

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

  def parseImpl(functionL: ImplL):
  Result[ImplP, IParseError] = {
    Profiler.frame(() => {
      val ImplL(implRange, maybeIdentifyingRunesL, maybeTemplateRulesL, structL, interfaceL, attributesL) = functionL

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

      val structP =
        structL match {
          case None => None
          case Some(structL) => {
            templexParser.parseTemplex(new ScrambleIterator(structL)) match {
              case Err(e) => return Err(e)
              case Ok(x) => Some(x)
            }
          }
        }

      val interfaceP =
        templexParser.parseTemplex(new ScrambleIterator(interfaceL)) match {
          case Err(e) => return Err(e)
          case Ok(x) => x
        }

      val attributesP =
        U.map[IAttributeL, IAttributeP](
          attributesL,
          attributeL => {
            parseAttribute(attributeL) match {
              case Err(e) => return Err(e)
              case Ok(x) => x
            }
          })

      val impl =
        ImplP(
          implRange,
          maybeIdentifyingRunes,
          maybeTemplateRulesP,
          structP,
          interfaceP,
          attributesP.toVector)
      Ok(impl)
    })
  }

  val export = interner.intern(StrI("export"))

  def parseExportAs(
    expoort: ExportAsL):
  Result[ExportAsP, IParseError] = {
    val iter = new ScrambleIterator(expoort.contents)

    val exportee =
      ParseUtils.trySkipPastKeywordWhile(
        iter,
        keywords.as,
        iter => iter.peek() match {
          case None => false
          case Some(SymbolLE(range, ';')) => false
          case _ => true
        }) match {
        case None => return Err(BadExportAs(iter.getPos()))
        case Some((asKeyword, beforeAsIter)) => {
          val templex =
            templexParser.parseTemplex(beforeAsIter) match {
              case Err(e) => return Err(e)
              case Ok(x) => x
            }
          templex
        }
      }

    val name =
      iter.peek() match {
        case None => return Err(BadExportEnd(iter.getPos()))
        case Some(WordLE(range, str)) => NameP(range, str)
      }

    Ok(ast.ExportAsP(expoort.range, exportee, name))
  }

  def parseImport(
    importL: ImportL):
  Result[ImportP, IParseError] = {
    val ImportL(range, moduleNameL, packageStepsL, importeeNameL) = importL

    val WordLE(moduleNameRange, moduleNameStr) = moduleNameL
    val moduleNameP = NameP(moduleNameRange, moduleNameStr)

    val packageStepsP =
      U.map[WordLE, NameP](packageStepsL, { case WordLE(moduleNameRange, moduleNameStr) =>
        NameP(moduleNameRange, moduleNameStr)
      })

    val WordLE(importeeNameRange, importeeNameStr) = importeeNameL
    val importeeNameP = NameP(importeeNameRange, importeeNameStr)

    Ok(ImportP(range, moduleNameP, packageStepsP.toVector, importeeNameP))
  }

  def parseAttribute(attrL: IAttributeL):
  Result[IAttributeP, IParseError] = {
    attrL match {
      case AbstractAttributeL(range) => Ok(AbstractAttributeP(range))
      case ExternAttributeL(range, None) => Ok(ExternAttributeP(range))
      case ExternAttributeL(range, Some(maybeName)) => {
        val name =
          maybeName.contents match {
            case ScrambleLE(_, Vector(StringLE(_, Vector(StringPartLiteral(range, s))))) => {
              NameP(range, interner.intern(StrI(s)))
            }
            case _ => vfail("Bad builtin extern!")
          }
        Ok(BuiltinAttributeP(range, name))
      }
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

  def parseFunction(functionL: FunctionL, isInCitizen: Boolean):
  Result[FunctionP, IParseError] = {
    Profiler.frame(() => {
      val FunctionL(funcRangeL, headerL, maybeBodyL) = functionL
      val FunctionHeaderL(headerRangeL, nameL, attributesL, maybeIdentifyingRunesL, paramsL, originalTrailingDetailsL) = headerL

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
          U.mapWithIndex[ScrambleIterator, PatternPP](
            new ScrambleIterator(paramsL.contents).splitOnSymbol(',', false),
            (index, patternIter) => {
              patternParser.parsePattern(patternIter, index, isInCitizen, true, false) match {
                case Err(e) => return Err(e)
                case Ok(x) => x
              }
            }).toVector)

      val trailingDetailsWithReturnAndWhereAndDefaultRegion = originalTrailingDetailsL
      val (trailingDetailsWithReturnAndWhere, maybeDefaultRegion) =
        parseBodyDefaultRegion(trailingDetailsWithReturnAndWhereAndDefaultRegion)

      // TODO: simplify this. It's really just trying to split on "where".
      val returnAndWhereIter = new ScrambleIterator(trailingDetailsWithReturnAndWhere)
      val (maybeReturnIter, returnEndPos, maybeRulesIter) =
        ParseUtils.trySkipPastKeywordWhile(
          returnAndWhereIter, keywords.where, it => it.hasNext) match {
          case None => (Some(returnAndWhereIter), returnAndWhereIter.scramble.range.end, None) // No "where" was found. Use everything remaining.
          case Some((_, returnIter)) => (Some(returnIter), returnIter.scramble.range.end, Some(returnAndWhereIter))
        }

      val returnBeginPos = returnAndWhereIter.scramble.range.begin
      val maybeReturnTypeP =
        maybeReturnIter.flatMap(returnIter => {
          if (returnIter.hasNext) {
            templexParser.parseTemplex(returnIter) match {
              case Err(e) => return Err(e)
              case Ok(x) => Some(x)
            }
          } else {
            None
          }
        })
      val returnP = FunctionReturnP(RangeL(returnBeginPos, returnEndPos), maybeReturnTypeP)

      val maybeRulesP =
        maybeRulesIter.map(rulesIter => {
          val begin = rulesIter.getPos()
          val rules =
            U.map[ScrambleIterator, IRulexPR](
            rulesIter.splitOnSymbol(',', false),
            templexL => {
              templexParser.parseRule(templexL) match {
                case Err(e) => return Err(e)
                case Ok(x) => x
              }
            })
          TemplateRulesP(rulesIter.scramble.range, rules)
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


      val header =
        FunctionHeaderP(
          headerL.range,
          Some(toName(nameL)),
          attributesP.toVector,
          maybeIdentifyingRunes,
          maybeRulesP,
          Some(paramsP),
          returnP)

      val bodyP =
        maybeBodyL.map(bodyL => {
          val FunctionBodyL(blockL) = bodyL
          val statementsP =
            expressionParser.parseBlock(blockL) match {
              case Err(err) => return Err(err)
              case Ok(result) => result
            }
          BlockPE(blockL.range, maybeDefaultRegion, statementsP)
        })

      Ok(FunctionP(funcRangeL, header, bodyP))
    })
  }

  // Returns:
  // - A scramble of everything before the default region. If there's no default region, it's the
  //   same as the input scramble.
  // - The default region if it existed.
  private def parseBodyDefaultRegion(inputScramble: ScrambleLE):
  (ScrambleLE, Option[RegionRunePT]) = {
    if (inputScramble.elements.size < 2) {
      return (inputScramble, None)
    }

    val lastTwo =
      inputScramble.elements.slice(inputScramble.elements.length - 2, inputScramble.elements.length)

    val defaultRegion =
      lastTwo match {
        case Vector(WordLE(wordRange, regionName), SymbolLE(symbolRange, '\'')) => {
          val range = RangeL(wordRange.begin, symbolRange.end)
          RegionRunePT(range, NameP(wordRange, regionName))
        }
        case _ => return (inputScramble, None)
      }

    val precedingElements = inputScramble.elements.slice(0, inputScramble.elements.length - 2)
    val precedingElementsRange =
      if (precedingElements.isEmpty) {
        RangeL(inputScramble.range.begin, inputScramble.range.begin)
      } else {
        RangeL(precedingElements.head.range.begin, precedingElements.last.range.end)
      }
    val precedingElementsScramble = ScrambleLE(precedingElementsRange, precedingElements)

    (precedingElementsScramble, Some(defaultRegion))
  }

  def toName(wordL: WordLE): NameP = {
    val WordLE(range, s) = wordL
    NameP(range, s)
  }
}

class ParserCompilation(
  opts: GlobalOptions,
  interner: Interner,
  keywords: Keywords,
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]]
) {
  val parser = new Parser(interner, keywords, opts)

  def loadAndParse(
    neededPackages: Vector[PackageCoordinate],
    resolver: IPackageResolver[Map[String, String]]):
  Result[(FileCoordinateMap[String], FileCoordinateMap[(FileP, Vector[RangeL])]), FailedParse] = {
    vassert(neededPackages.size == neededPackages.distinct.size, "Duplicate modules in: " + neededPackages.mkString(", "))

    val foundCodeMap = new FileCoordinateMap[String]()
    val parsedMap = new FileCoordinateMap[(FileP, Vector[RangeL])]()

    ParseAndExplore.parseAndExplore[IDenizenP, Unit](
      interner, keywords, opts, parser, packagesToBuild.toVector, resolver,
      (fileCoord, code, imports, denizen) => denizen,
      (fileCoord, code, commentRanges, denizens) => {
        foundCodeMap.put(fileCoord, code)
        val file = FileP(fileCoord, commentRanges.buildArray(), denizens.buildArray())

        if (opts.sanityCheck) {
          val json = new VonPrinter(JsonSyntax, 120).print(ParserVonifier.vonifyFile(file))
          val loadedFile = new ParsedLoader(interner).load(json).getOrDie()
          val secondJson = new VonPrinter(JsonSyntax, 120).print(ParserVonifier.vonifyFile(loadedFile))
          vassert(json == secondJson)
        }

        parsedMap.put(fileCoord, (file, commentRanges.buildArray().toVector))
      }) match {
      case Err(e) => return Err(e)
      case Ok(_) =>
    }

    Ok((foundCodeMap, parsedMap))
  }

  var codeMapCache: Option[FileCoordinateMap[String]] = None
  var vpstMapCache: Option[FileCoordinateMap[String]] = None
  var parsedsCache: Option[FileCoordinateMap[(FileP, Vector[RangeL])]] = None

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = {
    getParseds() match {
      case Ok(_) => Ok(codeMapCache.get)
      case Err(e) => Err(e)
    }
  }
  def expectCodeMap(): FileCoordinateMap[String] = {
    vassertSome(codeMapCache)
  }

  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[RangeL])], FailedParse] = {
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
  def expectParseds(): FileCoordinateMap[(FileP, Vector[RangeL])] = {
    getParseds() match {
      case Err(FailedParse(code, fileCoord, err)) => {
        vfail(ParseErrorHumanizer.humanize(SourceCodeUtils.humanizeFile(fileCoord), code, err))
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
  // A prefixing region is one that appears before something else to modify it, like t'T.
  def parsePrefixingRegion(originalIter: ScrambleIterator): Result[Option[RegionRunePT], IParseError] = {
    val tentativeIter = originalIter.clone()

    val region =
      parseRegion(tentativeIter) match {
        case Err(x) => return Err(x)
        case Ok(Some(region)) => {
          tentativeIter.peek() match {
            case Some(next) if next.range.begin == region.range.end => {
              region
            }
            case _ => return Ok(None)
          }
        }
        case _ => return Ok(None)
      }

    originalIter.skipTo(tentativeIter)

    Ok(Some(region))
  }

  def parseRegion(originalIter: ScrambleIterator): Result[Option[RegionRunePT], IParseError] = {
    val tentativeIter = originalIter.clone()

    val regionRune =
      tentativeIter.nextWord() match {
        case None => return Ok(None)
        case Some(r) => r
      }

    if (!tentativeIter.trySkipSymbol('\'')) {
      return Ok(None)
    }

    originalIter.skipTo(tentativeIter)

    val range = RangeL(regionRune.range.begin, tentativeIter.getPrevEndPos())
    return Ok(Some(RegionRunePT(range, NameP(regionRune.range, regionRune.str))))
  }
}
