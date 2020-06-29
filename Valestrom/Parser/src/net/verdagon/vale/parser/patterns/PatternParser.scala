package net.verdagon.vale.parser.patterns

import net.verdagon.vale.parser.{ITemplexPT, _}
import net.verdagon.vale.vfail

import scala.util.parsing.combinator.RegexParsers

trait PatternParser extends TemplexParser with RegexParsers with ParserUtils {

  // Things needed from other parsers
  private[parser] def exprIdentifier: Parser[StringP]


  // Add any new rules to the "Nothing matches empty string" test!

  // Remember, for pattern parsers, something *must* be present, don't match empty.
  // And it relies on that fact for the subpatterns too.
  private[parser] def atomPattern: Parser[PatternPP] = positioned {

    pos ~
    opt("virtual" ~> white) ~
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
        existsMW("&") ~ underscoreOr(patternCapture) ^^ { case preBorrow ~ capture => (preBorrow, capture, None, None) }
    ) ~
    opt(white ~> "impl" ~> white ~> templex) ~
    pos ^^ {
      case begin ~ maybeVirtual ~ maybePreBorrowAndMaybeCaptureAndMaybeTypeAndMaybeDestructure ~ maybeInterface ~ end => {
        val (maybePreBorrow, maybeCapture, maybeType, maybeDestructure) = maybePreBorrowAndMaybeCaptureAndMaybeTypeAndMaybeDestructure
        val maybeVirtuality =
          (maybeVirtual, maybeInterface) match {
            case (None, None) => None
            case (Some(_), None) => Some(AbstractP)
            case (None, Some(interface)) => Some(OverrideP(interface))
            case (Some(_), Some(_)) => vfail()
          }
        PatternPP(Range(begin, end), maybePreBorrow, maybeCapture, maybeType, maybeDestructure, maybeVirtuality)
      }
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  // Remember, for pattern parsers, something *must* be present, don't match empty.
  // Luckily, for this rule, we always have the expr identifier.
  private[parser] def patternCapture: Parser[CaptureP] = {
    pos ~ existsMW("this.") ~ exprIdentifier ~ opt("!") ~ pos ^^ {
      case begin ~ None ~ name ~ maybeMutable ~ end => CaptureP(Range(begin, end), LocalNameP(name), if (maybeMutable.nonEmpty) VaryingP else FinalP)
      case begin ~ Some(thisdot) ~ name ~ maybeMutable ~ end => CaptureP(Range(begin, end), ConstructingMemberNameP(StringP(Range(begin, name.range.end), name.str)), if (maybeMutable.nonEmpty) VaryingP else FinalP)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  // Remember, for pattern parsers, something *must* be present, don't match empty.
  case class PatternTypePPI(ownership: Option[OwnershipP], runeOrKind: ITemplexPT)
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
      case begin ~ inners ~ end => DestructureP(Range(begin, end), inners)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def patternOwnership: Parser[OwnershipP] = {
    // See "Capturing Kinds and Ownerships" for why we don't capture a rune here.
    (("^" ^^^ OwnP) | ("&" ^^^ BorrowP) | ("*" ^^^ ShareP))
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def runeOrKindPattern: Parser[ITemplexPT] = {
    templex
//    callableKindPattern |
//        repeaterSequenceKindPattern |
//        manualSequenceKindPattern |
//        kindPatternAtomic
  }

//  // Atomic means no neighboring, see parser doc.
//  def kindPatternAtomic: Parser[IKindPP] = {
//    templateCallKindPattern | namedKindPattern
//  }
//
//  def templateCallKindPattern: Parser[TemplateCallKindPP] = {
//    templateCallKindFilterRuleAtomic ^^ TemplateCallKindPP
//  }
//
//  def namedKindPattern: Parser[NameTemplexPP] = {
//    typeIdentifier ^^ NameTemplexPP
//  }
//
//  // Add any new rules to the "Nothing matches empty string" test!
//
//  private[parser] def callableKindPattern: Parser[CallableKindPP] = {
//    ("(" ~> optWhite ~> repsep(underscoreOr(patternCoordRule), optWhite ~> "," <~ optWhite) <~ optWhite <~ ")" <~ optWhite <~ ":" <~ optWhite) ~ underscoreOr(patternCoordRule) ^^ {
//      case params ~ ret => CallableKindPP(CallableKindFilterPR(params, ret))
//    }
//  }
//
//  // Add any new rules to the "Nothing matches empty string" test!
//
//  private[parser] def repeaterSequenceKindPattern: Parser[RepeaterSequenceTemplexPP] = {
//    "[" ~> optWhite ~> (underscoreOr(patternIntRule) <~ optWhite <~ "*" <~ optWhite) ~ underscoreOr(patternCoordRule) <~ optWhite <~ "]" ^^ {
//      case size ~ coordRule => RepeaterSequenceKindPP(RepeaterSequenceTemplexPR(size, coordRule))
//    }
//  }
//
//  // Add any new rules to the "Nothing matches empty string" test!
//
//  private[parser] def manualSequenceKindPattern: Parser[ManualSequenceTemplexPP] = {
//    "[" ~> optWhite ~> repsep(underscoreOr(patternCoordRule), optWhite <~ "," <~ optWhite) <~ optWhite <~ "]" ^^ {
//      case elements => ManualSequenceTemplexPP(ManualSequenceTemplexPR(Some(elements)))
//    }
//  }

  // Add any new rules to the "Nothing matches empty string" test!

//  private[parser] def patternCoordRule: Parser[CoordPR] = {
//    opt(patternOwnership <~ optWhite) ~ underscoreOr(atLeastOneOfWW(rune, patternKindRule)) ^^ {
//      case maybeOwnership ~ None => {
//        CoordPR(
//          None,
//          CoordPR(
//            maybeOwnership.map(a => OwnershipPR(None, OwnershipPR(maybeOwnership.map(a => Set(a))))),
//            None))
//      }
//      case maybeOwnership ~ Some(maybeRune ~ maybeKind) => {
//        CoordPR(
//          maybeRune,
//          CoordPR(
//            maybeOwnership.map(a => OwnershipPR(None, OwnershipPR(maybeOwnership.map(a => Set(a))))),
//            maybeKind.map(k => KindPR(None, k))))
//      }
//    }
//  }

  // Add any new rules to the "Nothing matches empty string" test!

//  private[parser] def patternIntRule: Parser[IntPR] = {
//    atLeastOneOfWW(rune, int) ^^ {
//      case maybeRune ~ maybeInt => {
//        IntPR(maybeRune, IntPR(maybeInt.map(int => Set(int))))
//      }
//    }
//  }

  // Add any new rules to the "Nothing matches empty string" test!

//  // Like the "Moo" in "a: &Moo", we're parsing the kind in a pattern.
//  private[parser] def patternKindRule: Parser[IKindTypePR] = {
//    // When you add anything here, update kindPattern too.
//    // When you add anything here, update kindFilterRule too, though not everything in there will be in here.
//    (patternCallableKindRule |
//        patternRepeaterSequenceKindRule |
//        patternManualSequenceKindRule |
//        patternTemplateCallKindFilterRule |
//        (typeIdentifier ^^ NamedKindPR))
//    // When you add anything here, update kindPattern too.
//    // When you add anything here, update kindFilterRule too, though not everything in there will be in here.
//  }

//  // Atomic means no neighboring, see parser doc.
//  private[parser] def patternKindRuleInner: Parser[KindPR] = {
////    (patternTemplateCallKindFilterRule ^^ (t => KindPR(None, KindPR(Some(t))))) |
//        ((typeIdentifier ^^ NamedKindPR) ^^ (a => KindPR(None, a)))
//  }
//
//  private[parser] def patternCallableKindRule: Parser[CallableKindFilterPR] = {
//    ("(" ~> optWhite ~> repsep(patternCoordRule, optWhite ~> "," <~ optWhite) <~ optWhite <~ ")" <~ optWhite <~ ":" <~ optWhite) ~ patternCoordRule ^^ {
//      case params ~ ret => CallableKindFilterPR(params.map(p => Some(p)), Some(ret))
//    }
//  }
//
//  private[parser] def patternTemplateCallKindFilterRule: Parser[TemplateCallKindFilterPR] = {
//    (((underscoreOr(patternKindRuleInner) <~ optWhite <~ ":" <~ optWhite) ~ underscoreOr(runeableTemplataRuleAtomic)) ^^ {
//      case template ~ arg => TemplateCallKindFilterPR(template, List(arg))
//    }) |
//    (((underscoreOr(patternKindRuleInner) <~ optWhite <~ ":" <~ optWhite <~ "(" <~ optWhite) ~ repsep(underscoreOr(runeableTemplataRule), optWhite ~> "," <~ optWhite) <~ optWhite <~ ")") ^^ {
//      case template ~ args => TemplateCallKindFilterPR(template, args)
//    })
//  }
//
//  // Add any new rules to the "Nothing matches empty string" test!
//
//  private[parser] def patternManualSequenceKindRule: Parser[ManualSequenceTemplexPR] = {
//    "[" ~> optWhite ~> repsep(patternCoordRule, optWhite ~> "," <~ optWhite) <~ optWhite <~ "]" ^^ {
//      case elements => ManualSequenceTemplexPR(Some(elements.map(e => Some(e))))
//    }
//  }
//
//  // Add any new rules to the "Nothing matches empty string" test!
//
//  private[parser] def patternRepeaterSequenceKindRule: Parser[RepeaterSequenceTemplexPR] = {
//    "[" ~> optWhite ~> (underscoreOr(patternIntRule) <~ optWhite <~ "*" <~ optWhite) ~ underscoreOr(patternCoordRule) <~ optWhite <~ "]" ^^ {
//      case size ~ coordRule => RepeaterSequenceTemplexPR(size, coordRule)
//    }
//  }
//
//  // Add any new rules to the "Nothing matches empty string" test!

}
