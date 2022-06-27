package dev.vale.parsing

import dev.vale.{Err, Interner, Ok, Result, StrI, U, vassert, vassertSome, vimpl, vwat}
import dev.vale.parsing.ast.{AbstractP, ConstructingMemberNameDeclarationP, DestructureP, INameDeclarationP, IgnoredLocalNameDeclarationP, LocalNameDeclarationP, NameP, PatternPP}
import dev.vale.parsing.templex.TemplexParser
import dev.vale.lexing.{BadDestructureError, BadLocalName, BadNameBeforeDestructure, BadThingAfterTypeInPattern, EmptyParameter, EmptyPattern, FoundBothAbstractAndOverride, FoundParameterWithoutType, INodeLE, IParseError, RangeL, RangedInternalErrorP, ScrambleLE, SquaredLE, SymbolLE, WordLE}
import dev.vale.parsing.ast._

import scala.collection.mutable

class PatternParser(interner: Interner, templexParser: TemplexParser) {
  val VIRTUAL = interner.intern(StrI("virtual"))
  val IMPL = interner.intern(StrI("impl"))
  val IN = interner.intern(StrI("in"))

//  // Remember, for pattern parsers, something *must* be present, don't match empty.
//  // Luckily, for this rule, we always have the expr identifier.
//  private[parser] def patternCapture: Parser[INameDeclarationP] = {
//    (pos <~ "&") ~ ("this"|"self") ~ pos ^^ {
//      case begin ~ name ~ end => LocalNameDeclarationP(NameP(RangeP(begin, end), name))
//    } |
//      pos ~ existsMW("this.") ~ (exprIdentifier <~ opt("!")) ~ pos ^^ {
//        case begin ~ None ~ name ~ end => LocalNameDeclarationP(name)
//        case begin ~ Some(thisdot) ~ name ~ end => ConstructingMemberNameDeclarationP(NameP(ast.RangeP(begin, name.range.end), name.str))
//      }
//  }

  def parsePatternCapture(iter: ScrambleIterator): Result[INameDeclarationP, IParseError] = {
    vimpl()
//    val begin = iter.getPos()
//
//    if (iter.trySkip(() => "^_\\b".r)) {
//      return Ok(IgnoredLocalNameDeclarationP(RangeP(begin, iter.getPos())))
//    }
//
//    if (iter.trySkip(() => "^&self".r)) {
//      return Ok(LocalNameDeclarationP(NameP(RangeP(begin, iter.getPos()), "self")))
//    }
//
//    if (iter.trySkip(() => "^self\\.".r)) {
//      val name =
//        Parser.parseLocalOrMemberName(iter) match {
//          case None => return Err(BadLocalName(iter.getPos()))
//          case Some(n) => n
//        }
//      return Ok(ConstructingMemberNameDeclarationP(NameP(ast.RangeP(begin, name.range.end), name.str)))
//    }
//
//    if (iter.trySkip(() => "^let".r)) {
//      iter.consumeWhitespace()
//
//      if (iter.trySkip(() => "^mut".r)) {
//        iter.consumeWhitespace()
//      }
//    }
//
//    val name =
//      Parser.parseLocalOrMemberName(iter) match {
//        case None => return Err(BadLocalName(iter.getPos()))
//        case Some(n) => n
//      }
//    iter.trySkip(() => "^!".r)
//    return Ok(LocalNameDeclarationP(name))
  }

  def parseDestructure(iter: ScrambleIterator): Result[DestructureP, IParseError] = {
    vimpl()
//    val begin = iter.getPos()
//
//    if (!iter.trySkip(() => "^\\[".r)) {
//      return Err(RangedInternalErrorP(iter.getPos(), "No [ ?"))
//    }
//    iter.consumeWhitespace()
//
//    val destructurees = mutable.ArrayBuffer[PatternPP]()
//    if (iter.trySkip(() => "^\\s*\\]".r)) {
//      return Ok(DestructureP(RangeL(begin, iter.getPos()), Vector()))
//    }
//    while ({
//      val destructuree = parsePattern(iter) match { case Err(e) => return Err(e) case Ok(p) => p }
//      destructurees += destructuree
//      if (iter.trySkip(() => "^\\s*,".r)) {
//        iter.consumeWhitespace()
//        true
//      } else if (iter.trySkip(() => "^\\s*]".r)) {
//        false
//      } else {
//        return Err(BadDestructureError(iter.getPos()))
//      }
//    }) {}
//
//    Ok(DestructureP(RangeL(begin, iter.getPos()), destructurees.toVector))
  }


//  def parseOverride(iter: ParsingIterator): Result[Option[OverrideP], IParseError] = {
//    val begin = iter.getPos()
//    if (!iter.trySkip(() => "^\\s*impl\\s+".r)) {
//      return Ok(None)
//    }
//    val tyype =
//      new TemplexParser().parseTemplex(iter) match {
//        case Err(e) => return Err(e)
//        case Ok(p) => p
//      }
//    Ok(Some(OverrideP(RangeP(begin, iter.getPos()), tyype)))
//  }

  def parseParameter(node: ScrambleLE, isInLambda: Boolean): Result[PatternPP, IParseError] = {
    val patternRange = node.range

    val iter = new ScrambleIterator(node, 0, node.elements.length)

    val maybeVirtual =
      iter.peek() match {
        case None => return Err(EmptyParameter(patternRange.begin))
        case Some(WordLE(range, s)) if s == VIRTUAL => {
          iter.advance()
          Some(AbstractP(range))
        }
        case Some(_) => None
      }

    val maybePreBorrow =
      iter.peek() match {
        case None => return Err(EmptyParameter(patternRange.begin))
        case Some(SymbolLE(range, '&')) => Some(range)
        case Some(_) => None
      }

    val name =
      iter.peek() match {
        case None => return Err(EmptyParameter(patternRange.begin))
        case Some(WordLE(range, str)) => Some(LocalNameDeclarationP(NameP(range, str)))
        case Some(other) => return Err(BadLocalName(other.range.begin))
      }

    val maybeType =
      if (iter.hasNext) {
        templexParser.parseTemplex(iter) match {
          case Err(e) => return Err(e)
          case Ok(x) => Some(x)
        }
      } else {
        if (isInLambda) {
          // Allow it
          None
        } else {
          return Err(FoundParameterWithoutType(patternRange.end))
        }
      }

    Ok(PatternPP(patternRange, maybePreBorrow, name, maybeType, None, maybeVirtual))
  }

  def parsePattern(iter: ScrambleIterator): Result[PatternPP, IParseError] = {
    val patternBegin = iter.getPos()

    if (!iter.hasNext) {
      return Err(EmptyPattern(patternBegin))
    }

    val nameIsNext =
      iter.peek(2) match {
        case Array(None, None) => vwat() // impossible
        case Array(Some(_), None) => true
        case Array(Some(first), Some(second)) => {
          if (first.range.begin < second.range.end) {
            // There's a space after the first thing, so it's a name.
            true
          } else {
            // There's no space after the first thing, so not a name.
            false
          }
        }
      }
    val maybeName =
      if (nameIsNext) {
        iter.advance() match {
          case WordLE(range, str) => Some(LocalNameDeclarationP(NameP(range, str)))
          case other => return Err(BadLocalName(other.range.begin))
        }
      } else {
        None
      }

    // We look ahead so we dont parse "in" as a type in: foreach x in myList { ... }
    iter.peek() match {
      case None =>
      case Some(WordLE(_, in)) if in == IN => iter.stop()
      case Some(_) =>
    }

    // The next thing might be a type or a destructure.
    // If it's a square-braced thing with nothing after it, it's a destructure.
    // See https://github.com/ValeLang/Vale/issues/434
    val nextIsType =
      iter.peek(2) match {
        case Array(None, None) => false
        case Array(Some(SquaredLE(_, _)), maybeAfter) => {
          // If there's something after it, it's an array.
          maybeAfter.nonEmpty
        }
        case Array(Some(_), _) => {
          // There's something that's not square-braced, so it's a type.
          true
        }
      }
    val maybeType =
      if (nextIsType) {
        templexParser.parseTemplex(iter) match {
          case Err(e) => return Err(e)
          case Ok(x) => Some(x)
        }
      } else {
        None
      }

    val maybeDestructure =
      iter.take() match {
        case Some(SquaredLE(destructureRange, destructureElements)) => {
          val destructure =
            DestructureP(
              destructureRange,
              U.map[ScrambleIterator, PatternPP](
                new ScrambleIterator(destructureElements).splitOnSymbol(',', false),
                destructureElementIter => {
                  parsePattern(destructureElementIter) match {
                    case Err(e) => return Err(e)
                    case Ok(x) => x
                  }
              }).toVector)
          Some(destructure)
        }
        case Some(other) => return Err(BadThingAfterTypeInPattern(other.range.begin))
        case None => None
      }

      Ok(PatternPP(RangeL(patternBegin, iter.getPos()), None, maybeName, maybeType, maybeDestructure, None))
  }

  //    pos ~
  //    opt(pstr("virtual") <~ white) ~
  //    (
  //
  //      // First, the ones with types:
  //        // Yes capture, yes type, yes destructure:
  //        underscoreOr(patternCapture) ~ (white ~> templex) ~ destructure ^^ { case capture ~ tyype ~ destructure => (None, capture, Some(tyype), Some(destructure)) } |
  //        // Yes capture, yes type, no destructure:
  //        underscoreOr(patternCapture) ~ (white ~> templex) ^^ { case capture ~ tyype => (None, capture, Some(tyype), None) } |
  //        // No capture, yes type, yes destructure:
  //        templex ~ destructure ^^ { case tyype ~ destructure => (None, None, Some(tyype), Some(destructure)) } |
  //        // No capture, yes type, no destructure: impossible.
  //      // Now, the ones with destructuring:
  //        // Yes capture, no type, yes destructure:
  //        underscoreOr(patternCapture) ~ (white ~> destructure) ^^ { case capture ~ destructure => (None, capture, None, Some(destructure)) } |
  //        // No capture, no type, yes destructure:
  //        destructure ^^ { case destructure => (None, None, None, Some(destructure)) } |
  //      // Now, a simple capture:
  //        // Yes capture, no type, no destructure:
  //        underscoreOr(patternCapture) ^^ { case capture => (None, capture, None, None) } |
  //        // Hacked in for highlighting, still need to incorporate into the above
  //        existsMW("*") ~ existsMW("!") ~ underscoreOr(patternCapture) ^^ { case preBorrow ~ readwrite ~ capture => (preBorrow, capture, None, None) }
  //    ) ~
  //    opt(white ~> "impl" ~> white ~> templex) ~
  //    pos ^^ {
  //      case begin ~ maybeVirtual ~ maybePreBorrowAndMaybeCaptureAndMaybeTypeAndMaybeDestructure ~ maybeInterface ~ end => {
  //        val (maybePreBorrow, maybeCapture, maybeType, maybeDestructure) = maybePreBorrowAndMaybeCaptureAndMaybeTypeAndMaybeDestructure
  //        val maybeVirtuality =
  //          (maybeVirtual, maybeInterface) match {
  //            case (None, None) => None
  //            case (Some(range), None) => Some(AbstractP(range.range))
  //            case (None, Some(interface)) => Some(OverrideP(ast.RangeP(begin, end), interface))
  //            case (Some(_), Some(_)) => vfail()
  //          }
  //        ast.PatternPP(ast.RangeP(begin, end), maybePreBorrow, maybeCapture, maybeType, maybeDestructure, maybeVirtuality)
  //      }
  //    }
}
