package net.verdagon.vale.parser.patterns

import net.verdagon.vale.parser.ast.{AbstractP, BorrowP, ConstructingMemberNameDeclarationP, DestructureP, INameDeclarationP, ITemplexPT, LocalNameDeclarationP, NameP, OverrideP, OwnP, OwnershipP, PatternPP, PointerP, ShareP, WeakP}
import net.verdagon.vale.parser.old.{ParserUtils, TemplexParser}
import net.verdagon.vale.parser.{ast, _}
import net.verdagon.vale.{vcurious, vfail, vimpl}

import scala.util.parsing.combinator.RegexParsers

trait PatternParser extends TemplexParser with RegexParsers with ParserUtils {

  // Things needed from other parsers
  private[parser] def exprIdentifier: Parser[NameP]


  // Add any new rules to the "Nothing matches empty string" test!

  // Remember, for pattern parsers, something *must* be present, don't match empty.
  // And it relies on that fact for the subpatterns too.
  private[parser] def atomPattern: Parser[PatternPP] = positioned {

    pos ~
    opt(pstr("virtual") <~ white) ~
    (
      // The order here matters, we don't want the "a" rule to match "a A(_, _)" just because one starts with the other.

        // First, the ones with destructuring:
        // Yes capture, yes type, yes destructure:
        underscoreOr(patternCapture) ~ (white ~> templex) ~ destructure ^^ { case capture ~ tyype ~ destructure => (None, capture, Some(tyype), Some(destructure)) } |
        // Yes capture, no type, yes destructure:
        underscoreOr(patternCapture) ~ (white ~> destructure) ^^ { case capture ~ destructure => (None, capture, None, Some(destructure)) } |
        // No capture, yes type, yes destructure:
        templex ~ destructure ^^ { case tyype ~ destructure => (None, None, Some(tyype), Some(destructure)) } |
        // No capture, no type, yes destructure:
        destructure ^^ { case destructure => (None, None, None, Some(destructure)) } |
      // Now, the ones with types:
        // No capture, yes type, no destructure: impossible.
        // Yes capture, yes type, no destructure:
        underscoreOr(patternCapture) ~ (white ~> templex) ^^ { case capture ~ tyype => (None, capture, Some(tyype), None) } |
      // Now, a simple capture:
        // Yes capture, no type, no destructure:
        underscoreOr(patternCapture) ^^ { case capture => (None, capture, None, None) } |
        // Hacked in for highlighting, still need to incorporate into the above
        existsMW("*") ~ existsMW("!") ~ underscoreOr(patternCapture) ^^ { case preBorrow ~ readwrite ~ capture => (preBorrow, capture, None, None) }
    ) ~
    opt(white ~> "impl" ~> white ~> templex) ~
    pos ^^ {
      case begin ~ maybeVirtual ~ maybePreBorrowAndMaybeCaptureAndMaybeTypeAndMaybeDestructure ~ maybeInterface ~ end => {
        val (maybePreBorrow, maybeCapture, maybeType, maybeDestructure) = maybePreBorrowAndMaybeCaptureAndMaybeTypeAndMaybeDestructure
        val maybeVirtuality =
          (maybeVirtual, maybeInterface) match {
            case (None, None) => None
            case (Some(range), None) => Some(AbstractP(range.range))
            case (None, Some(interface)) => Some(OverrideP(ast.RangeP(begin, end), interface))
            case (Some(_), Some(_)) => vfail()
          }
        ast.PatternPP(ast.RangeP(begin, end), maybePreBorrow, maybeCapture, maybeType, maybeDestructure, maybeVirtuality)
      }
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  // Remember, for pattern parsers, something *must* be present, don't match empty.
  // Luckily, for this rule, we always have the expr identifier.
  private[parser] def patternCapture: Parser[INameDeclarationP] = {
    pos ~ existsMW("this.") ~ (exprIdentifier <~ opt("!")) ~ pos ^^ {
      case begin ~ None ~ name ~ end => LocalNameDeclarationP(name)
      case begin ~ Some(thisdot) ~ name ~ end => ConstructingMemberNameDeclarationP(NameP(ast.RangeP(begin, name.range.end), name.str))
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  // Remember, for pattern parsers, something *must* be present, don't match empty.
  case class PatternTypePPI(ownership: Option[OwnershipP], runeOrKind: ITemplexPT) {   override def hashCode(): Int = vcurious()
  }
  private[parser] def patternType: Parser[PatternTypePPI] = {
    opt(patternOwnership <~ optWhite) ~ runeOrKindPattern ^^ {
      case maybeOwnershipP ~ maybeRuneOrKind => {
        PatternTypePPI(maybeOwnershipP, maybeRuneOrKind)
      }
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def destructure: Parser[DestructureP] = {
    pos ~ ("(" ~> optWhite ~> repsep(atomPattern, optWhite ~> "," <~ optWhite) <~ optWhite <~ ")") ~ pos ^^ {
      case begin ~ inners ~ end => DestructureP(ast.RangeP(begin, end), inners.toVector)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def patternOwnership: Parser[OwnershipP] = {
    // See "Capturing Kinds and Ownerships" for why we don't capture a rune here.
    (("^" ^^^ OwnP) | ("*" ^^^ PointerP) | ("&" ^^^ BorrowP) | ("**" ^^^ WeakP) | ("@" ^^^ ShareP))
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def runeOrKindPattern: Parser[ITemplexPT] = {
    templex
  }
}
