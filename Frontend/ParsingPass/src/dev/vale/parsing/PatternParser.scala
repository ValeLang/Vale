package dev.vale.parsing

import dev.vale.{Err, Interner, Keywords, Ok, Result, StrI, U, vassert, vassertSome, vimpl, vwat}
import dev.vale.parsing.ast.{AbstractP, ConstructingMemberNameDeclarationP, DestructureP, INameDeclarationP, IgnoredLocalNameDeclarationP, LocalNameDeclarationP, NameP, PatternPP}
import dev.vale.parsing.templex.TemplexParser
import dev.vale.lexing.{CantUseThatLocalName, BadLocalName, BadNameBeforeDestructure, BadThingAfterTypeInPattern, EmptyParameter, EmptyPattern, FoundBothAbstractAndOverride, INodeLE, IParseError, LightFunctionMustHaveParamTypes, RangeL, RangedInternalErrorP, ScrambleLE, SquaredLE, SymbolLE, WordLE}
import dev.vale.parsing.ast._

import scala.collection.mutable

class PatternParser(interner: Interner, keywords: Keywords, templexParser: TemplexParser) {

  def parseParameter(iter: ScrambleIterator, index: Int, isInCitizen: Boolean, isInFunction: Boolean, isInLambda: Boolean): Result[ParameterP, IParseError] = {
    val patternBegin = iter.getPos()
    val patternRange = iter.range

    if (!iter.hasNext) {
      return Err(EmptyPattern(patternBegin))
    }

    val maybeVirtual =
      iter.peek() match {
        case None => return Err(EmptyParameter(patternRange.begin))
        case Some(WordLE(range, s)) if s == keywords.virtual => {
          iter.advance()
          Some(AbstractP(range))
        }
        case Some(_) => None
      }

    val maybeSelfBorrow =
      iter.peek(2) match {
        case Vector() => return Err(EmptyParameter(patternRange.begin))
        case Vector(None) => return Err(EmptyParameter(patternRange.begin))
        case Vector(None, None) => return Err(EmptyParameter(patternRange.begin))
        case Vector(Some(SymbolLE(RangeL(begin, _), '&')), Some(WordLE(RangeL(_, end), s))) if s == keywords.self => {
          iter.advance()
          Some(RangeL(begin, end))
        }
        case _ => None
      }
    maybeSelfBorrow match {
      case Some(_) => {
        Ok(ParameterP(patternRange, maybeVirtual, None, maybeSelfBorrow, None))
      }
      case None => {
        val maybeName =
          iter.peek2() match {
            case (Some(SquaredLE(_, _)), _) => {
              // This is a destructure parameter with no name or type, like func moo([a, b, c])
              None
            }
            case (Some(w@WordLE(_, _)), Some(SquaredLE(_, _))) => {
              // This is a destructure parameter with no name, like func moo(Vec3[a, b, c])
              None
            }
            case (Some(w @ WordLE(range, str)), _) => {
              iter.advance()
              Some(w)
            }
            case _ => return Err(BadLocalName(iter.getPos()))
          }

        val maybePreChecked = iter.trySkipWord(keywords.pre)

        parsePattern(iter, patternBegin, index, isInCitizen, isInFunction, isInLambda, maybeName) match {
          case Ok(pattern) => Ok(ParameterP(patternRange, maybeVirtual, maybePreChecked, maybeSelfBorrow, Some(pattern)))
          case Err(x) => Err(x)
        }
      }
    }
  }

  def parsePattern(iter: ScrambleIterator, patternBegin: Int, index: Int, isInCitizen: Boolean, isInFunction: Boolean, isInLambda: Boolean, maybeNameFromParameter: Option[WordLE]): Result[PatternPP, IParseError] = {
    if (!iter.hasNext) {
      maybeNameFromParameter match {
        case None => return Err(EmptyPattern(patternBegin))
        case Some(WordLE(range, str)) => {
          Ok(
            PatternPP(
              RangeL(patternBegin, iter.getPrevEndPos()),
              Some(DestinationLocalP(LocalNameDeclarationP(NameP(range, str)), None)),
              None,
              None))
        }
      }

    }

    val isConstructing =
      iter.peek2() match {
        case (Some(WordLE(_, self)), Some(SymbolLE(range, '.')))
          if self == keywords.self => {
          iter.advance()
          iter.advance()
          true
        }
        case _ => false
      }

    val maybeMutate = iter.trySkipWord(keywords.set)
    if (maybeMutate.nonEmpty && !iter.hasNext) {
      return Err(CantUseThatLocalName(iter.getPos(), "set"))
    }

    val maybeDestinationLocal =
      maybeNameFromParameter match {
        case Some(WordLE(range, str)) => {
          if (str == keywords.UNDERSCORE) {
            Some(DestinationLocalP(IgnoredLocalNameDeclarationP(range), None))
          } else {
            Some(DestinationLocalP(LocalNameDeclarationP(NameP(range, str)), None))
          }
        }
        case None => {
          val nameIsNext =
            iter.peek2() match {
              case (None, None) => vwat() // impossible
              case (Some(_), None) => true
              case (Some(first), Some(second)) => {
                if (first.range.end < second.range.begin) {
                  // There's a space after the first thing, so it's a name.
                  true
                } else {
                  // There's no space after the first thing, so not a name.
                  false
                }
              }
            }
          if (nameIsNext) {
            iter.peek() match {
              case Some(WordLE(range, str)) => {
                iter.advance()
                if (str == keywords.UNDERSCORE) {
                  Some(DestinationLocalP(IgnoredLocalNameDeclarationP(range), maybeMutate))
                } else {
                  if (isConstructing) {
                    Some(DestinationLocalP(ConstructingMemberNameDeclarationP(NameP(range, str)), maybeMutate))
                  } else {
                    Some(DestinationLocalP(LocalNameDeclarationP(NameP(range, str)), maybeMutate))
                  }
                }
              }
              case Some(SquaredLE(_, _)) => None
              case _ => return Err(BadLocalName(iter.getPos()))
            }
          } else {
            None
          }
        }
      }

    // We look ahead so we dont parse "in" as a type in: foreach x in myList { ... }
    iter.peek() match {
      case None =>
      case Some(WordLE(_, in)) if in == keywords.in => iter.stop()
      case Some(_) =>
    }

    // The next thing might be a type or a destructure.
    // If it's a square-braced thing with nothing after it, it's a destructure.
    // See https://github.com/ValeLang/Vale/issues/434
    val nextIsType =
      iter.peek2() match {
        case (None, None) => false
        case (Some(SquaredLE(_, _)), maybeAfter) => {
          // If there's something after it, it's an array.
          maybeAfter.nonEmpty
        }
        case (Some(_), _) => {
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
        if (isInLambda) {
          // Allow it, lambdas can figure out their type from the callee.
          None
        } else if (isInCitizen) {
          // Allow it, just assume it's the containing struct.
          None
        } else if (isInFunction) {
          return Err(LightFunctionMustHaveParamTypes(patternBegin, index))
        } else {
          // Allow it, just a regular pattern
          None
        }
      }

    val maybeDestructure =
      iter.peek() match {
        case Some(SquaredLE(destructureRange, destructureElements)) => {
          iter.advance()
          val destructure =
            DestructureP(
              destructureRange,
              U.mapWithIndex[ScrambleIterator, PatternPP](
                new ScrambleIterator(destructureElements).splitOnSymbol(',', false),
                (index, destructureElementIter) => {
                  parsePattern(destructureElementIter, destructureElementIter.getPos(), index, false, false, false, None) match {
                    case Err(e) => return Err(e)
                    case Ok(x) => x
                  }
              }).toVector)
          Some(destructure)
        }
        case Some(other) => return Err(BadThingAfterTypeInPattern(other.range.begin))
        case None => None
      }

      Ok(
        PatternPP(
          RangeL(patternBegin, iter.getPrevEndPos()),
          maybeDestinationLocal, maybeType, maybeDestructure))
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
  //        existsMW("*") ~ existsMW("!") ~ underscoreOr(patternCapture) ^^ { case selfBorrow ~ readwrite ~ capture => (selfBorrow, capture, None, None) }
  //    ) ~
  //    opt(white ~> "impl" ~> white ~> templex) ~
  //    pos ^^ {
  //      case begin ~ maybeVirtual ~ maybeselfBorrowAndMaybeCaptureAndMaybeTypeAndMaybeDestructure ~ maybeInterface ~ end => {
  //        val (maybeselfBorrow, maybeCapture, maybeType, maybeDestructure) = maybeselfBorrowAndMaybeCaptureAndMaybeTypeAndMaybeDestructure
  //        val maybeVirtuality =
  //          (maybeVirtual, maybeInterface) match {
  //            case (None, None) => None
  //            case (Some(range), None) => Some(AbstractP(range.range))
  //            case (None, Some(interface)) => Some(OverrideP(ast.RangeP(begin, end), interface))
  //            case (Some(_), Some(_)) => vfail()
  //          }
  //        ast.PatternPP(ast.RangeP(begin, end), maybeselfBorrow, maybeCapture, maybeType, maybeDestructure, maybeVirtuality)
  //      }
  //    }
}
