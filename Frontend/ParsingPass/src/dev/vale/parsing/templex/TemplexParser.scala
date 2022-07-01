package dev.vale.parsing.templex

import dev.vale.{Err, Interner, Keywords, Ok, Profiler, Result, StrI, U, vassert, vassertSome, vimpl, vwat}
import dev.vale.parsing.{Parser, StopBeforeCloseSquare, StopBeforeComma, ast}
import dev.vale.parsing.ast.{AnonymousRunePT, BoolPT, BorrowP, BuiltinCallPR, CallPT, ComponentsPR, EqualsPR, FinalP, IRulexPR, ITemplexPT, ImmutableP, InlineP, IntPT, InterpretedPT, LocationPT, MutabilityPT, MutableP, NameOrRunePT, NameP, OwnP, OwnershipPT, PrototypePT, RegionRunePT, RuntimeSizedArrayPT, ShareP, StaticSizedArrayPT, StringPT, TemplexPR, TuplePT, TypedPR, VariabilityPT, VaryingP, WeakP, YonderP}
import dev.vale.lexing.{AngledLE, BadArraySizer, BadArraySizerEnd, BadPrototypeName, BadPrototypeParams, BadRegionName, BadRuleCallParam, BadRuneTypeError, BadStringChar, BadStringInTemplex, BadTemplateCallParam, BadTupleElement, BadTypeExpression, BadUnicodeChar, CurliedLE, FoundBothImmutableAndMutabilityInArray, INodeLE, IParseError, ParendLE, ParsedDoubleLE, ParsedIntegerLE, RangeL, RangedInternalErrorP, ScrambleLE, SquaredLE, StringLE, StringPartLiteral, SymbolLE, WordLE}
import dev.vale.parsing._
import dev.vale.parsing.ast._

import scala.collection.mutable

class TemplexParser(interner: Interner, keywords: Keywords) {
  def parseArray(originalIter: ScrambleIterator): Result[Option[ITemplexPT], IParseError] = {
    val begin = originalIter.getPos()

    val tentativeIter = originalIter.clone()

    val immutable = tentativeIter.trySkipSymbol('#')

    val sizeScrambleIterL =
      tentativeIter.peek() match {
        case Some(SquaredLE(range, squareContents)) => {
          tentativeIter.advance()
          new ScrambleIterator(squareContents)
        }
        case _ => return Ok(None)
      }

    originalIter.skipTo(tentativeIter)
    val iter = originalIter

    val maybeSizeTemplex =
      if (sizeScrambleIterL.hasNext) {
        if (sizeScrambleIterL.trySkipSymbol('#')) {
          parseTemplex(sizeScrambleIterL) match {
            case Err(e) => return Err(e)
            case Ok(x) => Some(x)
          }
        } else {
          None
        }
      } else {
        None
      }

    val templateArgsBegin = iter.getPos()
    val maybeTemplateArgs =
      parseTemplateCallArgs(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }
    val templateArgsEnd = iter.getPos()
    val mutability =
      (immutable, maybeTemplateArgs.toList.flatten.lift(0)) match {
        case (true, Some(_)) => return Err(FoundBothImmutableAndMutabilityInArray(begin))
        case (false, Some(templex)) => templex
        case (true, None) => MutabilityPT(RangeL(templateArgsBegin, templateArgsEnd), ImmutableP)
        case (false, None) => MutabilityPT(RangeL(templateArgsBegin, templateArgsEnd), MutableP)
      }
    val variability =
      maybeTemplateArgs.toList.flatten.lift(1)
        .getOrElse(VariabilityPT(RangeL(templateArgsBegin, templateArgsEnd), FinalP))

    val elementType = parseTemplex(iter) match { case Err(e) => return Err(e) case Ok(x) => x }

    val result =
      maybeSizeTemplex match {
        case None => {
          RuntimeSizedArrayPT(
            RangeL(begin, iter.getPrevEndPos()),
            mutability,
            elementType)
        }
        case Some(sizeTemplex) => {
          StaticSizedArrayPT(
            RangeL(begin, iter.getPrevEndPos()),
            mutability,
            variability,
            sizeTemplex,
            elementType)
        }
      }
    Ok(Some(result))
  }

  def parsePrototype(iter: ScrambleIterator): Result[Option[ITemplexPT], IParseError] = {
    val begin = iter.getPos()

    if (iter.trySkipWord(keywords.func).isEmpty) {
      return Ok(None)
    }

    val name =
      iter.peek() match {
        case Some(WordLE(range, name)) => {
          iter.advance()
          NameP(range, name)
        }
        case Some(SymbolLE(_, _)) => {
          val begin = iter.getPos()
          iter.peek3() match {
            case (Some(SymbolLE(_, '=')), Some(SymbolLE(_, '=')), Some(SymbolLE(_, '='))) => {
              iter.advance()
              iter.advance()
              iter.advance()
              NameP(RangeL(begin, iter.getPrevEndPos()), keywords.tripleEquals)
            }
            case (Some(SymbolLE(_, '<')), Some(SymbolLE(_, '=')), Some(SymbolLE(_, '>'))) => {
              iter.advance()
              iter.advance()
              iter.advance()
              NameP(RangeL(begin, iter.getPrevEndPos()), keywords.SPACESHIP)
            }
            case (Some(SymbolLE(_, '=')), Some(SymbolLE(_, '=')), _) => {
              iter.advance()
              iter.advance()
              NameP(RangeL(begin, iter.getPrevEndPos()), keywords.doubleEquals)
            }
            case (Some(SymbolLE(_, '!')), Some(SymbolLE(_, '=')), _) => {
              iter.advance()
              iter.advance()
              NameP(RangeL(begin, iter.getPrevEndPos()), keywords.notEquals)
            }
            case (Some(SymbolLE(_, '<')), Some(SymbolLE(_, '=')), _) => {
              iter.advance()
              iter.advance()
              NameP(RangeL(begin, iter.getPrevEndPos()), keywords.lessEquals)
            }
            case (Some(SymbolLE(_, '>')), Some(SymbolLE(_, '=')), _) => {
              iter.advance()
              iter.advance()
              NameP(RangeL(begin, iter.getPrevEndPos()), keywords.greaterEquals)
            }
            case (Some(SymbolLE(_, '<')), _, _) => {
              iter.advance()
              NameP(RangeL(begin, iter.getPrevEndPos()), keywords.less)
            }
            case (Some(SymbolLE(_, '>')), _, _) => {
              iter.advance()
              NameP(RangeL(begin, iter.getPrevEndPos()), keywords.greater)
            }
            case (Some(SymbolLE(_, '+')), _, _) => {
              iter.advance()
              NameP(RangeL(begin, iter.getPrevEndPos()), keywords.plus)
            }
            case (Some(SymbolLE(_, '-')), _, _) => {
              iter.advance()
              NameP(RangeL(begin, iter.getPrevEndPos()), keywords.minus)
            }
            case (Some(SymbolLE(_, '*')), _, _) => {
              iter.advance()
              NameP(RangeL(begin, iter.getPrevEndPos()), keywords.asterisk)
            }
            case (Some(SymbolLE(_, '/')), _, _) => {
              iter.advance()
              NameP(RangeL(begin, iter.getPrevEndPos()), keywords.slash)
            }
            case _ => return Err(BadPrototypeName(iter.getPos()))
          }
        }
        case Some(ParendLE(range, _)) => {
          // Dont iter.advance(), we do that below.
          NameP(RangeL(range.begin, range.begin), keywords.underscoresCall)
        }
        case _ => return Err(BadPrototypeName(iter.getPos()))
      }

    val args =
      parseTuple(iter) match {
        case Err(e) => return Err(e)
        case Ok(None) => return Err(BadPrototypeParams(iter.getPos()))
        case Ok(Some(x)) => x.elements
      }

    val returnType = parseTemplex(iter) match { case Err(e) => return Err(e) case Ok(x) => x }

    val result = PrototypePT(RangeL(begin, iter.getPrevEndPos()), name, args, returnType)
    Ok(Some(result))
  }

  //  private[parser] def tupleTemplex: Parser[ITemplexPT] = {
  //    (pos <~ "(" <~ optWhite <~ ")") ~ pos ^^ {
  //      case begin ~ end => TuplePT(ast.RangeP(begin, end), Vector.empty)
  //    } |
  //      pos ~ ("(" ~> optWhite ~> repsep(templex, optWhite ~> "," <~ optWhite) <~ optWhite <~ "," <~ optWhite <~ ")") ~ pos ^^ {
  //        case begin ~ members ~ end => TuplePT(ast.RangeP(begin, end), members.toVector)
  //      } |
  //      pos ~
  //        ("(" ~> optWhite ~> templex <~ optWhite <~ "," <~ optWhite) ~
  //        (repsep(templex, optWhite ~> "," <~ optWhite) <~ optWhite <~ ")") ~
  //        pos ^^ {
  //        case begin ~ first ~ rest ~ end => TuplePT(ast.RangeP(begin, end), (first :: rest).toVector)
  //      }
  //    // Old:
  //    //  pos ~ ("[" ~> optWhite ~> repsep(templex, optWhite ~> "," <~ optWhite) <~ optWhite <~ "]") ~ pos ^^ {
  //    //    case begin ~ members ~ end => ManualSequencePT(ast.RangeP(begin, end), members.toVector)
  //    //  }
  //  }
  //
  //  private[parser] def atomTemplex: Parser[ITemplexPT] = {
  //    ("(" ~> optWhite ~> templex <~ optWhite <~ ")") |
  //      staticSizedArrayTemplex |
  //      runtimeSizedArrayTemplex |
  //      tupleTemplex |
  //      (pos ~ long ~ pos ^^ { case begin ~ value ~ end => IntPT(ast.RangeP(begin, end), value) }) |
  //      pos ~ "true" ~ pos ^^ { case begin ~ _ ~ end => BoolPT(ast.RangeP(begin, end), true) } |
  //      pos ~ "false" ~ pos ^^ { case begin ~ _ ~ end => BoolPT(ast.RangeP(begin, end), false) } |
  //      pos ~ "own" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(ast.RangeP(begin, end), OwnP) } |
  //      pos ~ "borrow" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(ast.RangeP(begin, end), BorrowP) } |
  //      pos ~ "ptr" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(ast.RangeP(begin, end), PointerP) } |
  //      pos ~ "weak" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(ast.RangeP(begin, end), WeakP) } |
  //      pos ~ "share" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(ast.RangeP(begin, end), ShareP) } |
  //      mutabilityAtomTemplex |
  //      variabilityAtomTemplex |
  //      pos ~ "inl" ~ pos ^^ { case begin ~ _ ~ end => LocationPT(ast.RangeP(begin, end), InlineP) } |
  //      pos ~ "yon" ~ pos ^^ { case begin ~ _ ~ end => LocationPT(ast.RangeP(begin, end), YonderP) } |
  //      pos ~ "xrw" ~ pos ^^ { case begin ~ _ ~ end => PermissionPT(ast.RangeP(begin, end), ExclusiveReadwriteP) } |
  //      pos ~ "rw" ~ pos ^^ { case begin ~ _ ~ end => PermissionPT(ast.RangeP(begin, end), ReadwriteP) } |
  //      pos ~ "ro" ~ pos ^^ { case begin ~ _ ~ end => PermissionPT(ast.RangeP(begin, end), ReadonlyP) } |
  //      pos ~ ("_\\b".r) ~ pos ^^ { case begin ~ _ ~ end => AnonymousRunePT(ast.RangeP(begin, end)) } |
  //      (typeIdentifier ^^ NameOrRunePT)
  //  }
  //
  //  def mutabilityAtomTemplex: Parser[MutabilityPT] = {
  //    pos ~ "mut" ~ pos ^^ { case begin ~ _ ~ end => MutabilityPT(ast.RangeP(begin, end), MutableP) } |
  //      pos ~ "imm" ~ pos ^^ { case begin ~ _ ~ end => MutabilityPT(ast.RangeP(begin, end), ImmutableP) }
  //  }
  //
  //  def variabilityAtomTemplex: Parser[VariabilityPT] = {
  //    pos ~ "vary" ~ pos ^^ { case begin ~ _ ~ end => VariabilityPT(ast.RangeP(begin, end), VaryingP) } |
  //      pos ~ "final" ~ pos ^^ { case begin ~ _ ~ end => VariabilityPT(ast.RangeP(begin, end), FinalP) }
  //  }
  //

  def parseRegioned(iter: ScrambleIterator): Result[Option[ITemplexPT], IParseError] = {
    val begin = iter.getPos()
    if (!iter.trySkipSymbol('\'')) {
      return Ok(None)
    }

    val name =
      iter.nextWord() match {
        case None => return Err(BadRegionName(iter.getPos()))
        case Some(x) => x
      }

    if (iter.hasNext) {
      val inner =
        parseTemplexAtomAndCallAndPrefixes(iter) match {
          case Err(e) => return Err(e)
          case Ok(t) => t
        }
      Ok(Some(inner))
    } else {
      val rune =
        RegionRunePT(
          RangeL(begin, iter.getPrevEndPos()),
          NameP(name.range, name.str))
      Ok(Some(rune))
    }
  }

  def parseInterpreted(iter: ScrambleIterator): Result[Option[InterpretedPT], IParseError] = {
    val begin = iter.getPos()

    val ownership =
      if (iter.trySkipSymbol('^')) { OwnP }
      else if (iter.trySkipSymbol('@')) { ShareP }
      else if (iter.trySkipSymbols(Array('&', '&'))) { WeakP }
      else if (iter.trySkipSymbol('&')) { BorrowP }
      else { return Ok(None) }

    val inner =
      parseTemplexAtomAndCallAndPrefixes(iter) match {
        case Err(e) => return Err(e)
        case Ok(t) => t
      }

    Ok(Some(ast.InterpretedPT(RangeL(begin, iter.getPrevEndPos()), ownership, inner)))
  }


  def parseTemplexAtomAndCallAndPrefixesAndSuffixes(originalIter: ScrambleIterator): Result[ITemplexPT, IParseError] = {
    val inner =
      parseTemplexAtomAndCallAndPrefixes(originalIter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    return Ok(inner)
  }

  def parseTemplexAtom(iter: ScrambleIterator): Result[ITemplexPT, IParseError] = {
    vassert(iter.peek().nonEmpty)
    val begin = iter.getPos()

    iter.trySkipWord(keywords.UNDERSCORE) match {
      case Some(range) => return Ok(AnonymousRunePT(range))
      case None =>
    }
    iter.trySkipWord(keywords.truue) match {
      case Some(range) => return Ok(BoolPT(range, true))
      case None =>
    }
    iter.trySkipWord(keywords.faalse) match {
      case Some(range) => return Ok(BoolPT(range, false))
      case None =>
    }
    iter.trySkipWord(keywords.OWN) match {
      case Some(range) => return Ok(OwnershipPT(range, OwnP))
      case None =>
    }
    iter.trySkipWord(keywords.BORROW) match {
      case Some(range) => return Ok(OwnershipPT(range, BorrowP))
      case None =>
    }
    iter.trySkipWord(keywords.WEAK) match {
      case Some(range) => return Ok(OwnershipPT(range, WeakP))
      case None =>
    }
    iter.trySkipWord(keywords.SHARE) match {
      case Some(range) => return Ok(OwnershipPT(range, ShareP))
      case None =>
    }
    iter.trySkipWord(keywords.INL) match {
      case Some(range) => return Ok(LocationPT(range, InlineP))
      case None =>
    }
    iter.trySkipWord(keywords.HEAP) match {
      case Some(range) => return Ok(LocationPT(range, YonderP))
      case None =>
    }
    iter.trySkipWord(keywords.IMM) match {
      case Some(range) => return Ok(MutabilityPT(range, ImmutableP))
      case None =>
    }
    iter.trySkipWord(keywords.MUT) match {
      case Some(range) => return Ok(MutabilityPT(range, MutableP))
      case None =>
    }
    iter.trySkipWord(keywords.VARY) match {
      case Some(range) => return Ok(VariabilityPT(range, VaryingP))
      case None =>
    }
    iter.trySkipWord(keywords.FINAL) match {
      case Some(range) => return Ok(VariabilityPT(range, FinalP))
      case None =>
    }
    iter.trySkipWord(keywords.BORROW) match {
      case Some(range) => return Ok(OwnershipPT(range, BorrowP))
      case None =>
    }
    iter.trySkipWord(keywords.WEAK) match {
      case Some(range) => return Ok(OwnershipPT(range, WeakP))
      case None =>
    }
    iter.trySkipWord(keywords.OWN) match {
      case Some(range) => return Ok(OwnershipPT(range, OwnP))
      case None =>
    }
    iter.trySkipWord(keywords.SHARE) match {
      case Some(range) => return Ok(OwnershipPT(range, ShareP))
      case None =>
    }
    parsePrototype(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(tup)) => return Ok(tup)
      case Ok(None) =>
    }
    parseTuple(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(tup)) => return Ok(tup)
      case Ok(None) =>
    }
    parseArray(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(array)) => return Ok(array)
      case Ok(None) =>
    }
    vassertSome(iter.peek()) match {
      case StringLE(range, parts) => {
        iter.advance()
        parts match {
          case Array(StringPartLiteral(range, s)) => Ok(StringPT(range, s))
          case _ => return Err(BadStringInTemplex(range.begin))
        }
      }
      case ParsedIntegerLE(range, int, bits) => {
        iter.advance()
        Ok(IntPT(range, int))
      }
      case ParsedDoubleLE(range, double, bits) => {
        iter.advance()
        return Err(RangedInternalErrorP(range.begin, "Floats in types not supported!"))
      }
      case WordLE(range, str) => {
        iter.advance()
        Ok(NameOrRunePT(NameP(range, str)))
      }
      case _ => return Err(BadTypeExpression(iter.getPos()))
    }
  }

  def parseTemplateCallArgs(iter: ScrambleIterator): Result[Option[Vector[ITemplexPT]], IParseError] = {
    val angled =
      iter.peek() match {
        case Some(a @ AngledLE(range, contents)) => a
        case Some(_) => return Ok(None)
        case None => return Ok(None)
      }
    iter.advance()
    val elementsP =
      U.map[ScrambleIterator, ITemplexPT](
        new ScrambleIterator(angled.contents).splitOnSymbol(',', false),
        elementIter => {
          parseTemplex(elementIter) match {
            case Err(e) => return Err(e)
            case Ok(x) => x
          }
        })
    Ok(Some(elementsP.toVector))
  }

  def parseTuple(outerIter: ScrambleIterator): Result[Option[TuplePT], IParseError] = {
    val begin = outerIter.getPos()
    outerIter.peek() match {
      case Some(ParendLE(range, contents)) => {
        outerIter.advance()
        val elements =
          U.map[ScrambleIterator, ITemplexPT](
            new ScrambleIterator(contents).splitOnSymbol(',', false),
            iter => {
              parseTemplex(iter) match {
                case Err(e) => return Err(e)
                case Ok(x) => x
              }
            })
        Ok(Some(TuplePT(range, elements.toVector)))
      }
      case _ => Ok(None)
    }
  }

  def parseTemplexAtomAndCall(iter: ScrambleIterator): Result[ITemplexPT, IParseError] = {
    val begin = iter.getPos()

    val atom =
      parseTemplexAtom(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    parseTemplateCallArgs(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(args)) => return Ok(CallPT(RangeL(begin, iter.getPrevEndPos()), atom, args))
      case Ok(None) =>
    }

    Ok(atom)
  }

  def parseTemplexAtomAndCallAndPrefixes(iter: ScrambleIterator): Result[ITemplexPT, IParseError] = {
    vassert(iter.hasNext)

    iter.peek() match {
      case Some(WordLE(_, in)) if in == keywords.in => {
        // This is here so if we say:
        //   foreach x in myList { ... }
        // We won't interpret `x in` as a pattern, because
        // we don't interpret `in` as a valid templex.
        // The caller should prevent this.
        vwat()
      }
      case _ =>
    }

    val begin = iter.getPos()

    //    if (iter.trySkip(() => "^inl\\b".r)) {
    //
    //      val inner = parseTemplexAtomAndCallAndPrefixes(iter) match { case Err(e) => return Err(e) case Ok(t) => t }
    //      return Ok(InlinePT(RangeP(begin, iter.getPos()), inner))
    //    }

    parseRegioned(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseInterpreted(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseTemplexAtomAndCall(iter)
  }

  def parseRegion(node: INodeLE): Result[Option[RegionRunePT], IParseError] = {
    vimpl()
//    val begin = iter.getPos()
//    if (!iter.trySkip(() => "^'".r)) {
//      return Ok(None)
//    }
//
//    val regionName =
//      Parser.parseTypeName(iter) match {
//        case None => return Err(BadRegionName(iter.getPos()))
//        case Some(x) => x
//      }
//    Ok(Some(RegionRunePT(RangeL(begin, iter.getPos()), regionName)))
  }

  def parseTemplex(iter: ScrambleIterator): Result[ITemplexPT, IParseError] = {
    Profiler.frame(() => {
      parseTemplexAtomAndCallAndPrefixesAndSuffixes(iter)
    })
  }

  def parseTypedRune(originalIter: ScrambleIterator): Result[Option[TypedPR], IParseError] = {
    originalIter.peek2() match {
      // So we dont parse func moo()void
      case (Some(WordLE(nameRange, nameStr)), _) if nameStr == keywords.func => {
        Ok(None)
      }
      case (Some(WordLE(nameRange, nameStr)), Some(WordLE(typeRange, _))) => {
        val maybeName =
          if (nameStr == keywords.UNDERSCORE) {
            None
          } else {
            Some(NameP(nameRange, nameStr))
          }
        originalIter.advance()
        val tyype =
          parseRuneType(originalIter) match {
            case Err(e) => return Err(e)
            case Ok(None) => vwat()
            case Ok(Some(x)) => x
          }
        Ok(Some(ast.TypedPR(RangeL(nameRange.begin, typeRange.end), maybeName, tyype)))
      }
      case _ => Ok(None)
    }
  }

  def parseRuleCall(iter: ScrambleIterator): Result[Option[IRulexPR], IParseError] = {
    iter.peek2() match {
      case (Some(WordLE(_, StrI("func"))), _) => return Ok(None)
      case (Some(WordLE(nameRange, name)), Some(ParendLE(argsRange, argsLR))) => {
        val range = RangeL(nameRange.begin, argsRange.end)
        val argsPR =
          U.map[ScrambleIterator, IRulexPR](
            new ScrambleIterator(argsLR).splitOnSymbol(',', false),
            argIter => {
              parseRule(argIter) match {
                case Err(e) => return Err(e)
                case Ok(x) => x
              }
            })
        Ok(Some(BuiltinCallPR(range, NameP(nameRange, name), argsPR.toVector)))
      }
      case _ => return Ok(None)
    }

//    val nameStr = nameAndOpenParen.init
//    if (nameStr == "func") {
//      return Ok(None)
//    }
//
//    val nameEnd = tentativeIter.getPos() - 1
//    val name = NameP(RangeP(begin, nameEnd), nameStr)
//
//    originalIter.skipTo(tentativeIter.getPos())
//    val iter = originalIter
//
//    iter.consumeWhitespace()
//    if (iter.trySkip(() => "^\\s*\\)".r)) {
//      return Ok(Some(BuiltinCallPR(RangeP(begin, iter.getPos()), name, Vector())))
//    }
  }

  def parseRuleDestructure(originalIter: ScrambleIterator): Result[Option[IRulexPR], IParseError] = {
    originalIter.peek2() match {
      case (Some(WordLE(RangeL(begin, _), _)), Some(SquaredLE(RangeL(_, end), componentsL))) => {
        val runeType =
          parseRuneType(originalIter) match {
            case Ok(None) => vwat()
            case Err(e) => return Err(e)
            case Ok(Some(x)) => x
          }
        originalIter.advance()
        val componentsP =
          U.map[ScrambleIterator, IRulexPR](
            new ScrambleIterator(componentsL).splitOnSymbol(',', false),
            componentIter => {
              parseRule(componentIter) match {
                case Err(e) => return Err(e)
                case Ok(x) => x
              }
            })
        Ok(Some(ComponentsPR(RangeL(begin, end), runeType, componentsP.toVector)))
      }
      case _ => Ok(None)
    }
  }

  def parseRuleAtom(iter: ScrambleIterator): Result[IRulexPR, IParseError] = {
    val begin = iter.getPos()

    parseRuleCall(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseRuleDestructure(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseTypedRune(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

    parseTemplex(iter) match {
      case Err(e) => return Err(e)
      case Ok(t) => Ok(TemplexPR(t))
    }
  }

  def parseRuleUpToEqualsPrecedence(iter: ScrambleIterator): Result[IRulexPR, IParseError] = {
    Profiler.frame(() => {
      ParseUtils.trySkipPastEqualsWhile(iter, scoutingIter => {
        scoutingIter.peek() match {
          case None => false
          // Stop on comma
          case Some(SymbolLE(_, ',')) => false
          // Stop if we hit an open brace, its the function body
          case Some(CurliedLE(_, _)) => false
          case _ => true
        }
      })  match {
        case None => parseRuleAtom(iter)
        case Some(beforeIter) => {
          val left =
            parseRuleAtom(beforeIter) match {
              case Err(e) => return Err(e)
              case Ok(x) => x
            }
          val right =
            parseRuleAtom(iter) match {
              case Err(e) => return Err(e)
              case Ok(x) => x
            }
          Ok(EqualsPR(RangeL(left.range.begin, right.range.end), left, right))
        }
      }
    })
  }

  def parseRule(s: ScrambleIterator): Result[IRulexPR, IParseError] = {
    parseRuleUpToEqualsPrecedence(s)
  }

  def parseRuneType(iter: ScrambleIterator):
  Result[Option[ITypePR], IParseError] = {
    iter.peek() match {
      case None => Ok(None)

      case Some(WordLE(_, w)) if w == keywords.INT => {
        iter.advance(); Ok(Some(IntTypePR))
      }
      case Some(WordLE(_, w)) if w == keywords.REF => {
        iter.advance(); Ok(Some(CoordTypePR))
      }
      case Some(WordLE(_, w)) if w == keywords.KIND => {
        iter.advance(); Ok(Some(KindTypePR))
      }
      case Some(WordLE(_, w)) if w == keywords.PROT => {
        iter.advance(); Ok(Some(PrototypeTypePR))
      }
      case Some(WordLE(_, w)) if w == keywords.REFLIST => {
        iter.advance(); Ok(Some(CoordListTypePR))
      }
      case Some(WordLE(_, w)) if w == keywords.OWNERSHIP => {
        iter.advance(); Ok(Some(OwnershipTypePR))
      }
      case Some(WordLE(_, w)) if w == keywords.VARIABILITY => {
        iter.advance(); Ok(Some(VariabilityTypePR))
      }
      case Some(WordLE(_, w)) if w == keywords.MUTABILITY => {
        iter.advance(); Ok(Some(MutabilityTypePR))
      }
      case Some(WordLE(_, w)) if w == keywords.LOCATION => {
        iter.advance(); Ok(Some(LocationTypePR))
      }
      case _ => return Err(BadRuneTypeError(iter.getPos()))
    }
  }
}