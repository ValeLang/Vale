package net.verdagon.vale.parser.templex

import net.verdagon.vale.parser.Parser.{ParsedDouble, ParsedInteger, parseRuneType, parseTypeName}
import net.verdagon.vale.{Err, Ok, Result, vimpl, vwat}
import net.verdagon.vale.parser._
import net.verdagon.vale.parser.ast._
import net.verdagon.vale.parser.expressions.StringParser

import scala.collection.mutable

class TemplexParser {
  def parseArray(iter: ParsingIterator): Result[Option[ITemplexPT], IParseError] = {
    val begin = iter.getPos()

    if (!iter.trySkip("^\\[".r)) {
      return Ok(None)
    }

    iter.consumeWhitespace()

    val maybeSizeTemplex =
      if (iter.trySkip("^#".r)) {
        iter.consumeWhitespace()
        parseTemplex(iter) match { case Err(e) => return Err(e) case Ok(x) => Some(x) }
      } else {
        None
      }

    if (!iter.trySkip("^]".r)) {
      return Err(BadArraySizerEnd(iter.getPos()))
    }

    val templateArgsBegin = iter.getPos()
    val maybeTemplateArgs =
      parseTemplateCallArgs(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }
    val templateArgsEnd = iter.getPos()
    val mutability =
      maybeTemplateArgs.toList.flatten.lift(0)
        .getOrElse(MutabilityPT(RangeP(templateArgsBegin, templateArgsEnd), MutableP))
    val variability =
      maybeTemplateArgs.toList.flatten.lift(1)
        .getOrElse(VariabilityPT(RangeP(templateArgsBegin, templateArgsEnd), FinalP))

    val elementType = parseTemplex(iter) match { case Err(e) => return Err(e) case Ok(x) => x }

    val result =
      maybeSizeTemplex match {
        case None => {
          RuntimeSizedArrayPT(
            RangeP(begin, iter.getPos()),
            mutability,
            elementType)
        }
        case Some(sizeTemplex) => {
          StaticSizedArrayPT(
            RangeP(begin, iter.getPos()),
            mutability,
            variability,
            sizeTemplex,
            elementType)
        }
      }
    Ok(Some(result))
  }

  def parsePrototype(iter: ParsingIterator): Result[Option[ITemplexPT], IParseError] = {
    val begin = iter.getPos()

    if (!iter.trySkip("^func\\b".r)) {
      return Ok(None)
    }

    iter.consumeWhitespace()

    val name =
      Parser.parseFunctionOrLocalOrMemberName(iter) match {
        case None => return Err(BadPrototypeName(iter.getPos()))
        case Some(x) => x
      }

    val args =
      parseTuple(iter) match {
        case Err(e) => return Err(e)
        case Ok(None) => return Err(BadPrototypeParams(iter.getPos()))
        case Ok(Some(x)) => x.elements
      }

    val returnType = parseTemplex(iter) match { case Err(e) => return Err(e) case Ok(x) => x }

    val result = PrototypePT(RangeP(begin, iter.getPos()), name, args, returnType)
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

  def parseInterpreted(iter: ParsingIterator): Result[Option[InterpretedPT], IParseError] = {
    val begin = iter.getPos()

    val ownership =
      if (iter.trySkip("^\\^".r)) { OwnP }
      else if (iter.trySkip("^@".r)) { ShareP }
      else if (iter.trySkip("^\\*\\*".r)) { WeakP }
      else if (iter.trySkip("^\\*".r)) { PointerP }
      else if (iter.trySkip("^&".r)) { BorrowP }
      else { return Ok(None) }

    val permission =
      if (iter.trySkip("^!".r)) {
        ReadwriteP
      } else {
        if (ownership == OwnP) {
          ReadwriteP
        } else {
          ReadonlyP
        }
      }

    (ownership, permission) match {
      case (ShareP, ReadwriteP) => return Err(ShareCantBeReadwrite(iter.getPos()))
      case _ =>
    }

    val inner =
      parseTemplexAtomAndCallAndPrefixes(iter) match {
        case Err(e) => return Err(e)
        case Ok(t) => t
      }

    Ok(Some(InterpretedPT(RangeP(begin, iter.getPos()), ownership, permission, inner)))
  }


  def parseTemplexAtomAndCallAndPrefixesAndSuffixes(originalIter: ParsingIterator): Result[ITemplexPT, IParseError] = {
    val inner =
      parseTemplexAtomAndCallAndPrefixes(originalIter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    val tentativeIter = originalIter.clone()
    tentativeIter.consumeWhitespace()

    if (tentativeIter.trySkip("^'".r)) {
      originalIter.skipTo(tentativeIter.getPos())
      val iter = originalIter

      val regionName =
        Parser.parseTypeName(iter) match {
          case None => return Err(BadRegionName(iter.getPos()))
          case Some(x) => x
        }
      // One day we'll actually have a RegionedPT(...) or something.
      // For now, just return the inner.
      return Ok(inner)
    }

    return Ok(inner)
  }


  def parseStringPart(iter: ParsingIterator, stringBeginPos: Int): Result[Char, IParseError] = {
    if (iter.trySkip("^\\\\".r)) {
      if (iter.trySkip("^r".r) || iter.trySkip("^\\r".r)) {
        Ok('\r')
      } else if (iter.trySkip("^t".r)) {
        Ok('\t')
      } else if (iter.trySkip("^n".r) || iter.trySkip("^\\n".r)) {
        Ok('\n')
      } else if (iter.trySkip("^\\\\".r)) {
        Ok('\\')
      } else if (iter.trySkip("^\"".r)) {
        Ok('\"')
      } else if (iter.trySkip("^/".r)) {
        Ok('/')
      } else if (iter.trySkip("^\\{".r)) {
        Ok('{')
      } else if (iter.trySkip("^\\}".r)) {
        Ok('}')
      } else if (iter.trySkip("^u".r)) {
        val num =
          StringParser.parseFourDigitHexNum(iter) match {
            case None => {
              return Err(BadUnicodeChar(iter.getPos()))
            }
            case Some(x) => x
          }
        Ok(num.toChar)
      } else {
        Ok(iter.tryy("^.".r).get.charAt(0))
      }
    } else {
      val c =
        iter.tryy("^(.|\\n)".r) match {
          case None => {
            return Err(BadStringChar(stringBeginPos, iter.getPos()))
          }
          case Some(x) => x
        }
      Ok(c.charAt(0))
    }
  }

  def parseString(iter: ParsingIterator): Result[Option[StringPT], IParseError] = {
    val begin = iter.getPos()
    if (!iter.trySkip("^\"".r)) {
      return Ok(None)
    }
    val stringSoFar = new StringBuilder()
    while (!(iter.atEnd() || iter.trySkip("^\"".r))) {
      val c = parseStringPart(iter, begin) match { case Err(e) => return Err(e) case Ok(c) => c }
      stringSoFar += c
    }
    Ok(Some(StringPT(RangeP(begin, iter.getPos()), stringSoFar.toString())))
  }


  def parseTemplexAtom(iter: ParsingIterator): Result[ITemplexPT, IParseError] = {
    val begin = iter.getPos()

    if (iter.trySkip("^_\\b".r)) {
      return Ok(AnonymousRunePT(RangeP(begin, iter.getPos())))
    }
    if (iter.trySkip("^true\\b".r)) {
      return Ok(BoolPT(RangeP(begin, iter.getPos()), true))
    }
    if (iter.trySkip("^false\\b".r)) {
      return Ok(BoolPT(RangeP(begin, iter.getPos()), false))
    }
    if (iter.trySkip("^own\\b".r)) {
      return Ok(OwnershipPT(RangeP(begin, iter.getPos()), OwnP))
    }
    if (iter.trySkip("^borrow\\b".r)) {
      return Ok(OwnershipPT(RangeP(begin, iter.getPos()), BorrowP))
    }
    if (iter.trySkip("^weak\\b".r)) {
      return Ok(OwnershipPT(RangeP(begin, iter.getPos()), WeakP))
    }
    if (iter.trySkip("^pointer\\b".r)) {
      return Ok(OwnershipPT(RangeP(begin, iter.getPos()), PointerP))
    }
    if (iter.trySkip("^share\\b".r)) {
      return Ok(OwnershipPT(RangeP(begin, iter.getPos()), ShareP))
    }
    if (iter.trySkip("^inl\\b".r)) {
      return Ok(LocationPT(RangeP(begin, iter.getPos()), InlineP))
    }
    if (iter.trySkip("^heap\\b".r)) {
      return Ok(LocationPT(RangeP(begin, iter.getPos()), YonderP))
    }
    if (iter.trySkip("^xrw\\b".r)) {
      return Ok(PermissionPT(RangeP(begin, iter.getPos()), ExclusiveReadwriteP))
    }
    if (iter.trySkip("^rw\\b".r)) {
      return Ok(PermissionPT(RangeP(begin, iter.getPos()), ReadwriteP))
    }
    if (iter.trySkip("^ro\\b".r)) {
      return Ok(PermissionPT(RangeP(begin, iter.getPos()), ReadonlyP))
    }
    if (iter.trySkip("^imm\\b".r)) {
      return Ok(MutabilityPT(RangeP(begin, iter.getPos()), ImmutableP))
    }
    if (iter.trySkip("^mut\\b".r)) {
      return Ok(MutabilityPT(RangeP(begin, iter.getPos()), MutableP))
    }
    if (iter.trySkip("^vary\\b".r)) {
      return Ok(VariabilityPT(RangeP(begin, iter.getPos()), VaryingP))
    }
    if (iter.trySkip("^final\\b".r)) {
      return Ok(VariabilityPT(RangeP(begin, iter.getPos()), FinalP))
    }
    if (iter.trySkip("^ptr\\b".r)) {
      return Ok(OwnershipPT(RangeP(begin, iter.getPos()), PointerP))
    }
    if (iter.trySkip("^borrow\\b".r)) {
      return Ok(OwnershipPT(RangeP(begin, iter.getPos()), BorrowP))
    }
    if (iter.trySkip("^weak\\b".r)) {
      return Ok(OwnershipPT(RangeP(begin, iter.getPos()), WeakP))
    }
    if (iter.trySkip("^own\\b".r)) {
      return Ok(OwnershipPT(RangeP(begin, iter.getPos()), OwnP))
    }
    if (iter.trySkip("^share\\b".r)) {
      return Ok(OwnershipPT(RangeP(begin, iter.getPos()), ShareP))
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
    parseString(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(s)) => return Ok(s)
      case Ok(None) =>
    }
    Parser.parseNumber(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(ParsedInteger(range, int, bits))) => return Ok(IntPT(range, int))
      case Ok(Some(ParsedDouble(range, int, bits))) => vimpl()
      case Ok(None) =>
    }
    Parser.parseTypeName(iter) match {
      case Some(name) => return Ok(NameOrRunePT(name))
      case None =>
    }
    return Err(BadTypeExpression(iter.getPos()))
  }

  def parseTemplateCallArgs(iter: ParsingIterator): Result[Option[Vector[ITemplexPT]], IParseError] = {
    val begin = iter.getPos()
    if (!iter.trySkip("^\\s*<".r)) {
      return Ok(None)
    }
    iter.consumeWhitespace()
    val args = mutable.ArrayBuffer[ITemplexPT]()
    while ({
      val arg =
        parseTemplex(iter) match {
          case Err(e) => return Err(e)
          case Ok(x) => x
        }
      args += arg
      iter.consumeWhitespace()
      if (iter.trySkip("^>".r)) {
        false
      } else if (iter.trySkip("^,".r)) {
        iter.consumeWhitespace()
        true
      } else {
        return Err(BadTemplateCallParam(iter.getPos()))
      }
    }) {}

    Ok(Some(args.toVector))
  }

  def parseTuple(iter: ParsingIterator): Result[Option[TuplePT], IParseError] = {
    val begin = iter.getPos()
    if (!iter.trySkip("^\\(".r)) {
      return Ok(None)
    }
    iter.consumeWhitespace()
    if (iter.trySkip("^\\)".r)) {
      return Ok(Some(TuplePT(RangeP(begin, iter.getPos()), Vector())))
    }
    val args = mutable.ArrayBuffer[ITemplexPT]()
    while ({
      val arg =
        parseTemplex(iter) match {
          case Err(e) => return Err(e)
          case Ok(x) => x
        }
      args += arg
      iter.consumeWhitespace()
      if (iter.trySkip("^\\)".r)) {
        false
      } else if (iter.trySkip("^,".r)) {
        iter.consumeWhitespace()
        true
      } else {
        return Err(BadTemplateCallParam(iter.getPos()))
      }
    }) {}

    Ok(Some(TuplePT(RangeP(begin, iter.getPos()), args.toVector)))
  }

  def parseTemplexAtomAndCall(iter: ParsingIterator): Result[ITemplexPT, IParseError] = {
    val begin = iter.getPos()

    val atom =
      parseTemplexAtom(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    parseTemplateCallArgs(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(args)) => return Ok(CallPT(RangeP(begin, iter.getPos()), atom, args))
      case Ok(None) =>
    }

    Ok(atom)
  }

  def parseTemplexAtomAndCallAndPrefixes(iter: ParsingIterator): Result[ITemplexPT, IParseError] = {
    if (iter.peek("^in\\b".r)) {
      // This is here so if we say:
      //   foreach x in myList { ... }
      // We won't interpret `x in` as a pattern, because
      // we don't interpret `in` as a valid templex.
      // The caller should prevent this.
      vwat()
    }
    if (iter.peek("^impl\\b".r)) {
      // func moo(a impl IFoo) { ... }
      // The caller should prevent this.
      vwat()
    }

    val begin = iter.getPos()

    parseInterpreted(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }

//    if (iter.trySkip("^inl\\b".r)) {
//      iter.consumeWhitespace()
//      val inner = parseTemplexAtomAndCallAndPrefixes(iter) match { case Err(e) => return Err(e) case Ok(t) => t }
//      return Ok(InlinePT(RangeP(begin, iter.getPos()), inner))
//    }

    if (iter.trySkip("^'".r)) {
      iter.consumeWhitespace()
      val regionName =
        Parser.parseTypeName(iter) match {
          case None => return Err(BadRegionName(iter.getPos()))
          case Some(x) => x
        }
      return Ok(RegionRunePT(RangeP(begin, iter.getPos()), regionName))
    }

    parseTemplexAtomAndCall(iter)
  }

  def parseRegion(iter: ParsingIterator): Result[Option[RegionRunePT], IParseError] = {
    val begin = iter.getPos()
    if (!iter.trySkip("^'".r)) {
      return Ok(None)
    }
    iter.consumeWhitespace()
    val regionName =
      Parser.parseTypeName(iter) match {
        case None => return Err(BadRegionName(iter.getPos()))
        case Some(x) => x
      }
    Ok(Some(RegionRunePT(RangeP(begin, iter.getPos()), regionName)))
  }

  def parseTemplex(iter: ParsingIterator): Result[ITemplexPT, IParseError] = {
    parseTemplexAtomAndCallAndPrefixesAndSuffixes(iter)
  }

  def parseTypedRune(originalIter: ParsingIterator): Result[Option[TypedPR], IParseError] = {
    val begin = originalIter.getPos()

    val tentativeIter = originalIter.clone()

    val maybeRuneName =
      if (tentativeIter.trySkip("^_\\b".r)) {
        None
      } else {
        Parser.parseTypeName(tentativeIter) match {
          case None => return Ok(None)
          case Some(x) => Some(x)
        }
      }

    tentativeIter.consumeWhitespace()

    val runeType =
      Parser.parseRuneType(tentativeIter, Vector()) match {
        case Err(e) => return Ok(None)
        case Ok(None) => return Ok(None)
        case Ok(Some(x)) => x
      }

    tentativeIter.consumeWhitespace()

    originalIter.skipTo(tentativeIter.getPos())
    val iter = originalIter

    Ok(Some(TypedPR(RangeP(begin, iter.getPos()), maybeRuneName, runeType)))
  }

  def parseRuleCall(iter: ParsingIterator): Result[Option[IRulexPR], IParseError] = {
    val begin = iter.getPos()
    val nameAndOpenParen =
    iter.tryy("^\\w+\\(".r) match {
      case None => return Ok(None)
      case Some(nameAndOpenParen) => nameAndOpenParen
    }

    val nameStr = nameAndOpenParen.init
    val nameEnd = iter.getPos() - 1
    val name = NameP(RangeP(begin, nameEnd), nameStr)

    iter.consumeWhitespace()
    if (iter.trySkip("^\\s*\\)".r)) {
      return Ok(Some(BuiltinCallPR(RangeP(begin, iter.getPos()), name, Vector())))
    }

    val args = mutable.ArrayBuffer[IRulexPR]()
    while ({
      iter.consumeWhitespace()
      val arg = parseRule(iter) match { case Err(e) => return Err(e) case Ok(t) => t }
      args += arg
      if (iter.trySkip("^\\s*,".r)) {
        true
      } else if (iter.trySkip("^\\s*\\)".r)) {
        false
      } else {
        return Err(BadRuleCallParam(iter.getPos()))
      }
    }) {}

    Ok(Some(BuiltinCallPR(RangeP(begin, iter.getPos()), name, args.toVector)))
  }

  def parseRuleDestructure(originalIter: ParsingIterator): Result[Option[IRulexPR], IParseError] = {
    val begin = originalIter.getPos()

    val tentativeIter = originalIter.clone()

    val tyype =
      parseRuneType(tentativeIter, Vector(StopBeforeComma, StopBeforeCloseSquare)) match {
        case Err(e) => return Ok(None)
        case Ok(None) => return Ok(None)
        case Ok(Some(t)) => t
      }

    val typeEnd = tentativeIter.getPos()

    if (!tentativeIter.trySkip("^\\[".r)) {
      return Ok(None)
    }

    originalIter.skipTo(tentativeIter.getPos())
    val iter = originalIter

//    val name = NameP(RangeP(begin, nameEnd), nameStr)

    val args = mutable.ArrayBuffer[IRulexPR]()
    while ({
      iter.consumeWhitespace()
      val arg = parseRule(iter) match { case Err(e) => return Err(e) case Ok(t) => t }
      args += arg
      if (iter.trySkip("^\\s*,".r)) {
        iter.consumeWhitespace()
        true
      } else if (iter.trySkip("^\\s*]".r)) {
        false
      } else {
        return Err(BadRuleCallParam(iter.getPos()))
      }
    }) {}

    Ok(Some(ComponentsPR(RangeP(begin, iter.getPos()), tyype, args.toVector)))
  }

  def parseRuleAtom(iter: ParsingIterator): Result[IRulexPR, IParseError] = {
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

  def parseRuleUpToEqualsPrecedence(iter: ParsingIterator): Result[IRulexPR, IParseError] = {
    val begin = iter.getPos()
    val inner =
      parseRuleAtom(iter) match { case Err(e) => return Err(e) case Ok(t) => t }

    if (iter.trySkip("^\\s*=\\s*".r)) {
      val right =
        parseRuleUpToEqualsPrecedence(iter) match { case Err(e) => return Err(e) case Ok(t) => t }
      return Ok(EqualsPR(RangeP(begin, iter.getPos()), inner, right))
    }

    return Ok(inner)
  }

  def parseRule(iter: ParsingIterator): Result[IRulexPR, IParseError] = {
    parseRuleUpToEqualsPrecedence(iter)
  }
}