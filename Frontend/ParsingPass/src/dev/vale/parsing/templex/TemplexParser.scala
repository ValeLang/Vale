package dev.vale.parsing.templex

import dev.vale.{Err, Interner, Ok, Profiler, Result, StrI, U, vassert, vimpl, vwat}
import dev.vale.parsing.{Parser, StopBeforeCloseSquare, StopBeforeComma, ast}
import dev.vale.parsing.ast.{AnonymousRunePT, BoolPT, BorrowP, BuiltinCallPR, CallPT, ComponentsPR, EqualsPR, FinalP, IRulexPR, ITemplexPT, ImmutableP, InlineP, IntPT, InterpretedPT, LocationPT, MutabilityPT, MutableP, NameOrRunePT, NameP, OwnP, OwnershipPT, PrototypePT, RegionRunePT, RuntimeSizedArrayPT, ShareP, StaticSizedArrayPT, StringPT, TemplexPR, TuplePT, TypedPR, VariabilityPT, VaryingP, WeakP, YonderP}
import dev.vale.lexing.{AngledLE, BadArraySizer, BadArraySizerEnd, BadPrototypeName, BadPrototypeParams, BadRegionName, BadRuleCallParam, BadStringChar, BadStringInTemplex, BadTemplateCallParam, BadTypeExpression, BadUnicodeChar, FoundBothImmutableAndMutabilityInArray, INodeLE, IParseError, ParsedDoubleLE, ParsedIntegerLE, RangeL, RangedInternalErrorP, ScrambleLE, SquaredLE, StringLE, StringPartLiteral, SymbolLE, WordLE}
import dev.vale.parsing._
import dev.vale.parsing.ast._

import scala.collection.mutable

class TemplexParser(interner: Interner) {
  val UNDERSCORE = interner.intern(StrI("_"))
  val TRUE = interner.intern(StrI("true"))
  val FALSE = interner.intern(StrI("false"))
  val OWN = interner.intern(StrI("own"))
  val BORROW = interner.intern(StrI("borrow"))
  val WEAK = interner.intern(StrI("weak"))
  val SHARE = interner.intern(StrI("share"))
  val INL = interner.intern(StrI("inl"))
  val HEAP = interner.intern(StrI("heap"))
  val IMM = interner.intern(StrI("imm"))
  val MUT = interner.intern(StrI("mut"))
  val VARY = interner.intern(StrI("vary"))
  val FINAL = interner.intern(StrI("final"))

  def parseArray(originalIter: ScrambleIterator): Result[Option[ITemplexPT], IParseError] = {
    val begin = originalIter.getPos()

    val tentativeIter = originalIter.clone()

    val immutable = tentativeIter.trySkipSymbol('#')

    val maybeSizeScrambleL =
      tentativeIter.peek() match {
        case Some(SquaredLE(range, elements)) => {
          tentativeIter.advance()
          if (elements.elements.isEmpty) {
            None
          } else {
            val scramble = elements.elements.head
            Some(scramble)
          }
        }
        case _ => return Ok(None)
      }

    originalIter.skipTo(tentativeIter)
    val iter = originalIter

    val maybeSizeTemplex =
      maybeSizeScrambleL match {
        case None => None
        case Some(scramble) => {
          val iter = new ScrambleIterator(scramble, 0, scramble.elements.length)
          if (iter.trySkipSymbol('#')) {
            parseTemplex(iter) match {
              case Err(e) => return Err(e)
              case Ok(x) => Some(x)
            }
          } else {
            None
          }
        }
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
            RangeL(begin, iter.getPos()),
            mutability,
            elementType)
        }
        case Some(sizeTemplex) => {
          StaticSizedArrayPT(
            RangeL(begin, iter.getPos()),
            mutability,
            variability,
            sizeTemplex,
            elementType)
        }
      }
    Ok(Some(result))
  }

  val FUNC = interner.intern(StrI("func"))

  def parsePrototype(iter: ScrambleIterator): Result[Option[ITemplexPT], IParseError] = {
    val begin = iter.getPos()

    if (!iter.trySkipWord(FUNC)) {
      return Ok(None)
    }

    val name = vimpl()
//      if (iter.peek(() => "^\\(".r)) {
//        NameP(RangeP(iter.getPos(), iter.getPos()), "__call")
//      } else {
//        Parser.parseFunctionOrLocalOrMemberName(iter) match {
//          case None => return Err(BadPrototypeName(iter.getPos()))
//          case Some(x) => x
//        }
//      }

    val args =
      parseTuple(iter) match {
        case Err(e) => return Err(e)
        case Ok(None) => return Err(BadPrototypeParams(iter.getPos()))
        case Ok(Some(x)) => x.elements
      }

    val returnType = parseTemplex(iter) match { case Err(e) => return Err(e) case Ok(x) => x }

    val result = PrototypePT(RangeL(begin, iter.getPos()), name, args, returnType)
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

    val inner =
      parseTemplexAtomAndCallAndPrefixes(iter) match {
        case Err(e) => return Err(e)
        case Ok(t) => t
      }

    Ok(Some(inner))
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

    Ok(Some(ast.InterpretedPT(RangeL(begin, iter.getPos()), ownership, inner)))
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
    val begin = iter.getPos()

    if (iter.trySkipWord(UNDERSCORE)) {
      return Ok(AnonymousRunePT(RangeL(begin, iter.getPos())))
    }
    if (iter.trySkipWord(TRUE)) {
      return Ok(BoolPT(RangeL(begin, iter.getPos()), true))
    }
    if (iter.trySkipWord(FALSE)) {
      return Ok(BoolPT(RangeL(begin, iter.getPos()), false))
    }
    if (iter.trySkipWord(OWN)) {
      return Ok(OwnershipPT(RangeL(begin, iter.getPos()), OwnP))
    }
    if (iter.trySkipWord(BORROW)) {
      return Ok(OwnershipPT(RangeL(begin, iter.getPos()), BorrowP))
    }
    if (iter.trySkipWord(WEAK)) {
      return Ok(OwnershipPT(RangeL(begin, iter.getPos()), WeakP))
    }
    if (iter.trySkipWord(SHARE)) {
      return Ok(OwnershipPT(RangeL(begin, iter.getPos()), ShareP))
    }
    if (iter.trySkipWord(INL)) {
      return Ok(LocationPT(RangeL(begin, iter.getPos()), InlineP))
    }
    if (iter.trySkipWord(HEAP)) {
      return Ok(LocationPT(RangeL(begin, iter.getPos()), YonderP))
    }
    if (iter.trySkipWord(IMM)) {
      return Ok(MutabilityPT(RangeL(begin, iter.getPos()), ImmutableP))
    }
    if (iter.trySkipWord(MUT)) {
      return Ok(MutabilityPT(RangeL(begin, iter.getPos()), MutableP))
    }
    if (iter.trySkipWord(VARY)) {
      return Ok(VariabilityPT(RangeL(begin, iter.getPos()), VaryingP))
    }
    if (iter.trySkipWord(FINAL)) {
      return Ok(VariabilityPT(RangeL(begin, iter.getPos()), FinalP))
    }
    if (iter.trySkipWord(BORROW)) {
      return Ok(OwnershipPT(RangeL(begin, iter.getPos()), BorrowP))
    }
    if (iter.trySkipWord(WEAK)) {
      return Ok(OwnershipPT(RangeL(begin, iter.getPos()), WeakP))
    }
    if (iter.trySkipWord(OWN)) {
      return Ok(OwnershipPT(RangeL(begin, iter.getPos()), OwnP))
    }
    if (iter.trySkipWord(SHARE)) {
      return Ok(OwnershipPT(RangeL(begin, iter.getPos()), ShareP))
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
    iter.advance() match {
      case StringLE(range, parts) => {
        parts match {
          case Array(StringPartLiteral(range, s)) => Ok(StringPT(range, s))
          case _ => return Err(BadStringInTemplex(range.begin))
        }
      }
      case ParsedIntegerLE(range, int, bits) => Ok(IntPT(range, int))
      case ParsedDoubleLE(range, double, bits) => return Err(RangedInternalErrorP(range.begin, "Floats in types not supported!"))
      case WordLE(range, str) => Ok(NameOrRunePT(NameP(range, str)))
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
      U.map[ScrambleLE, ITemplexPT](
        angled.contents.elements,
        element => {
          parseTemplex(new ScrambleIterator(element, 0, element.elements.length)) match {
            case Err(e) => return Err(e)
            case Ok(x) => x
          }
        })
    Ok(Some(elementsP.toVector))
  }

  def parseTuple(iter: ScrambleIterator): Result[Option[TuplePT], IParseError] = {
    val begin = iter.getPos()
    if (!iter.trySkipSymbol('(')) {
      return Ok(None)
    }

    if (iter.trySkipSymbol(')')) {
      return Ok(Some(TuplePT(RangeL(begin, iter.getPos()), Vector())))
    }
    val args = mutable.ArrayBuffer[ITemplexPT]()
    while ({
      val arg =
        parseTemplex(iter) match {
          case Err(e) => return Err(e)
          case Ok(x) => x
        }
      args += arg

      if (iter.trySkipSymbol(')')) {
        false
      } else if (iter.trySkipSymbol(',')) {
        true
      } else {
        return Err(BadTemplateCallParam(iter.getPos()))
      }
    }) {}

    Ok(Some(TuplePT(RangeL(begin, iter.getPos()), args.toVector)))
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
      case Ok(Some(args)) => return Ok(CallPT(RangeL(begin, iter.getPos()), atom, args))
      case Ok(None) =>
    }

    Ok(atom)
  }

  def parseTemplexAtomAndCallAndPrefixes(iter: ScrambleIterator): Result[ITemplexPT, IParseError] = {
    assert(iter.hasNext)

    iter.peek() match {
      case Some(WordLE(_, StrI("in"))) => {
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
    val begin = originalIter.getPos()

    val tentativeIter = originalIter.clone()

    val maybeRuneName =
      if (tentativeIter.trySkipWord(UNDERSCORE)) {
        None
      } else {
        vimpl()
//        Parser.parseTypeName(tentativeIter) match {
//          case None => return Ok(None)
//          case Some(x) => Some(x)
//        }
      }

    val runeType = vimpl()
//      Parser.parseRuneType(tentativeIter, Vector()) match {
//        case Err(e) => return Ok(None)
//        case Ok(None) => return Ok(None)
//        case Ok(Some(x)) => x
//      }

    originalIter.skipTo(tentativeIter)
    val iter = originalIter

    Ok(Some(ast.TypedPR(RangeL(begin, iter.getPos()), maybeRuneName, runeType)))
  }

  def parseRuleCall(iter: ScrambleIterator): Result[Option[IRulexPR], IParseError] = {
    vimpl()

//    val tentativeIter = originalIter.clone()
//
//    val begin = tentativeIter.getPos()
//    val nameAndOpenParen =
//      tentativeIter.tryy(() => "^\\w+\\(".r) match {
//        case None => return Ok(None)
//        case Some(nameAndOpenParen) => nameAndOpenParen
//      }
//
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
    vimpl()
//    val begin = originalIter.getPos()
//
//    val tentativeIter = originalIter.clone()
//
//    val tyype =
//      parseRuneType(tentativeIter, Vector(StopBeforeComma, StopBeforeCloseSquare)) match {
//        case Err(e) => return Ok(None)
//        case Ok(None) => return Ok(None)
//        case Ok(Some(t)) => t
//      }
//
//    val typeEnd = tentativeIter.getPos()
//
//    if (!tentativeIter.trySkip(() => "^\\[".r)) {
//      return Ok(None)
//    }
//
//    originalIter.skipTo(tentativeIter.getPos())
//    val iter = originalIter
//
////    val name = NameP(RangeP(begin, nameEnd), nameStr)
//
//    val args = mutable.ArrayBuffer[IRulexPR]()
//    while ({
//
//      val arg = parseRule(iter) match { case Err(e) => return Err(e) case Ok(t) => t }
//      args += arg
//      if (iter.trySkip(() => "^\\s*,".r)) {
//
//        true
//      } else if (iter.trySkip(() => "^\\s*]".r)) {
//        false
//      } else {
//        return Err(BadRuleCallParam(iter.getPos()))
//      }
//    }) {}
//
//    Ok(Some(ComponentsPR(RangeL(begin, iter.getPos()), tyype, args.toVector)))
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
    vimpl()
//    Profiler.frame(() => {
//      val begin = iter.getPos()
//      val inner =
//        parseRuleAtom(iter) match {
//          case Err(e) => return Err(e)
//          case Ok(t) => t
//        }
//
//      if (iter.trySkip(() => "^\\s*=\\s*".r)) {
//        val right =
//          parseRuleUpToEqualsPrecedence(iter) match {
//            case Err(e) => return Err(e)
//            case Ok(t) => t
//          }
//        return Ok(EqualsPR(RangeL(begin, iter.getPos()), inner, right))
//      }
//
//      return Ok(inner)
//    })
  }

  def parseRule(s: ScrambleLE): Result[IRulexPR, IParseError] = {
    parseRuleUpToEqualsPrecedence(new ScrambleIterator(s, 0, s.elements.length))
  }
}