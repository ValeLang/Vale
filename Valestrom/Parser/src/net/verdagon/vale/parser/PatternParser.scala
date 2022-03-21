package net.verdagon.vale.parser

import net.verdagon.vale.{Err, Ok, Result, vassert, vwat}
import net.verdagon.vale.parser.ast._
import net.verdagon.vale.parser.templex.TemplexParser

import scala.collection.mutable

class PatternParser {

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

  def parsePatternCapture(iter: ParsingIterator): Result[INameDeclarationP, IParseError] = {
    val begin = iter.getPos()

    if (iter.trySkip("^_\\b".r)) {
      return Ok(IgnoredLocalNameDeclarationP(RangeP(begin, iter.getPos())))
    }

    if (iter.trySkip("^&self".r)) {
      return Ok(LocalNameDeclarationP(NameP(RangeP(begin, iter.getPos()), "self")))
    }

    if (iter.trySkip("^self\\.".r)) {
      val name =
        Parser.parseLocalOrMemberName(iter) match {
          case None => return Err(BadLocalName(iter.getPos()))
          case Some(n) => n
        }
      return Ok(ConstructingMemberNameDeclarationP(NameP(ast.RangeP(begin, name.range.end), name.str)))
    }

    val name =
      Parser.parseLocalOrMemberName(iter) match {
        case None => return Err(BadLocalName(iter.getPos()))
        case Some(n) => n
      }
    return Ok(LocalNameDeclarationP(name))
  }

  def parseDestructure(iter: ParsingIterator): Result[DestructureP, IParseError] = {
    val begin = iter.getPos()

    if (!iter.trySkip("^\\[".r)) {
      return Err(RangedInternalErrorP(iter.getPos(), "No [ ?"))
    }
    iter.consumeWhitespace()

    val destructurees = mutable.ArrayBuffer[PatternPP]()
    if (iter.trySkip("^\\s*\\]".r)) {
      return Ok(DestructureP(RangeP(begin, iter.getPos()), Vector()))
    }
    while ({
      val destructuree = parsePattern(iter) match { case Err(e) => return Err(e) case Ok(p) => p }
      destructurees += destructuree
      if (iter.trySkip("^\\s*,".r)) {
        iter.consumeWhitespace()
        true
      } else if (iter.trySkip("^\\s*]".r)) {
        false
      } else {
        return Err(BadDestructureError(iter.getPos()))
      }
    }) {}

    Ok(DestructureP(RangeP(begin, iter.getPos()), destructurees.toVector))
  }


//  def parseOverride(iter: ParsingIterator): Result[Option[OverrideP], IParseError] = {
//    val begin = iter.getPos()
//    if (!iter.trySkip("^\\s*impl\\s+".r)) {
//      return Ok(None)
//    }
//    val tyype =
//      new TemplexParser().parseTemplex(iter) match {
//        case Err(e) => return Err(e)
//        case Ok(p) => p
//      }
//    Ok(Some(OverrideP(RangeP(begin, iter.getPos()), tyype)))
//  }

  def parsePattern(originalIter: ParsingIterator): Result[PatternPP, IParseError] = {
    val tentativeIter = originalIter.clone()
    val begin = tentativeIter.getPos()

    val maybeAbstract =
      if (tentativeIter.trySkip("^virtual\\b".r)) {
        val virtualEnd = tentativeIter.getPos()
        tentativeIter.consumeWhitespace()
        Some(AbstractP(RangeP(begin, virtualEnd)))
      } else {
        None
      }

    // The order here matters.
    // We dont want to mix up the type []Ship with the destructure [], so types need to come first.
    // We don't want the "a" rule to match "a A[_, _]" just because one starts with the other.

    if (tentativeIter.peek("^\\[".r)) {
      originalIter.skipTo(tentativeIter.getPos())
      val iter = originalIter
      val destructure = parseDestructure(iter) match { case Err(e) => return Err(e) case Ok(p) => p }
      return Ok(PatternPP(RangeP(begin, iter.getPos()), None, None, None, Some(destructure), None))
    }

    val captureOrType =
      parsePatternCapture(tentativeIter) match { case Err(e) => return Err(e) case Ok(p) => p }

    if (tentativeIter.peek("^\\s*(=|,|\\)|\\])".r)) {
      // We're ending here, and captureOrType was a capture.
      originalIter.skipTo(tentativeIter.getPos())
      val iter = originalIter
      val capture = captureOrType
      return Ok(PatternPP(RangeP(begin, iter.getPos()), None, Some(capture), None, None, None))
    }

    // If we get here, there's something after the pattern.

    // If next is a [# or a [] then it's an array, see https://github.com/ValeLang/Vale/issues/434
    if (tentativeIter.peek("^\\s+\\[[^#\\]]".r)) {
      tentativeIter.consumeWhitespace()
      val destructure = parseDestructure(tentativeIter) match { case Err(e) => return Err(e) case Ok(p) => p }
      originalIter.skipTo(tentativeIter.getPos())
      val iter = originalIter
      // There's space between the capture/type thing and the destructure, so it was a capture.
      val capture = captureOrType
      return Ok(PatternPP(RangeP(begin, iter.getPos()), None, Some(capture), None, Some(destructure), None))
    }

    val (maybeCapture, maybeType, maybeDestructure) =
      // We look ahead so we dont parse "in" as a type in: foreach x in myList { ... }
      if (tentativeIter.atEnd() || tentativeIter.peek("^\\s*(in|impl)\\b".r)) {
        originalIter.skipTo(tentativeIter.getPos())
        val iter = originalIter
        val capture = captureOrType
        (Some(capture), None, None)
      } else if (tentativeIter.peek("^\\[[^#\\]]".r)) { // If next is a [# or a [] then it's an array, see https://github.com/ValeLang/Vale/issues/434
        // Note the lack of whitespace before the [
        // That means that we were wrong, the capture wasn't a capture, it was a type.

        // Throw away the tentative iter, we're going all in on it being a type.
        val iter = originalIter

        val tyype = new TemplexParser().parseTemplex(iter) match { case Err(e) => return Err(e) case Ok(p) => p }

//        // We should be at the same place.
//        vassert(iter.getPos() == tentativeIter.getPos())

        val destructure = parseDestructure(iter) match { case Err(e) => return Err(e) case Ok(p) => p }

        (None, Some(tyype), Some(destructure))
      } else {
        // If we get here, the capture/type thing was a capture, and there's a type after the capture.
        originalIter.skipTo(tentativeIter.getPos())
        val iter = originalIter
        val capture = captureOrType
        iter.consumeWhitespace()

        val tyype = new TemplexParser().parseTemplex(iter) match { case Err(e) => return Err(e) case Ok(p) => p }

        val maybeDestructure =
          if (iter.peek("^\\s*\\[".r)) {
            val destructure = parseDestructure(iter) match { case Err(e) => return Err(e) case Ok(p) => p }
            Some(destructure)
          } else {
            None
          }

        (Some(capture), Some(tyype), maybeDestructure)
      }
    val iter = originalIter

//    val maybeOverride = parseOverride(iter) match { case Err(e) => return Err(e) case Ok(p) => p }
//    val maybeVirtuality =
//      (maybeAbstract, maybeOverride) match {
//        case (Some(_), Some(_)) => return Err(FoundBothAbstractAndOverride(begin))
//        case (a, b) => (a ++ b).headOption
//      }

    val pattern =
      PatternPP(RangeP(begin, iter.getPos()), None, maybeCapture, maybeType, maybeDestructure, maybeAbstract)
    Ok(pattern)
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
