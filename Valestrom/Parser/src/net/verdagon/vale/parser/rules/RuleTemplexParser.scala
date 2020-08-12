package net.verdagon.vale.parser.rules

import net.verdagon.vale.parser
import net.verdagon.vale.parser._

import scala.util.parsing.combinator.RegexParsers

trait RuleTemplexParser extends RegexParsers with ParserUtils {
  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def keywordOrIdentifierOrRuneRuleTemplexPR: Parser[ITemplexPRT] = {
    pos ~ "true" ~ pos ^^ { case begin ~ _ ~ end => BoolPRT(Range(begin, end), true) } |
    pos ~ "false" ~ pos ^^ { case begin ~ _ ~ end => BoolPRT(Range(begin, end), false) } |
    pos ~ "own" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPRT(Range(begin, end), OwnP) } |
    pos ~ "borrow" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPRT(Range(begin, end), BorrowP) } |
    pos ~ "weak" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPRT(Range(begin, end), WeakP) } |
    pos ~ "share" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPRT(Range(begin, end), ShareP) } |
    pos ~ "mut" ~ pos ^^ { case begin ~ _ ~ end => MutabilityPRT(Range(begin, end), MutableP) } |
    pos ~ "imm" ~ pos ^^ { case begin ~ _ ~ end => MutabilityPRT(Range(begin, end), ImmutableP) } |
    pos ~ "inl" ~ pos ^^ { case begin ~ _ ~ end => LocationPRT(Range(begin, end), InlineP) } |
    pos ~ "yon" ~ pos ^^ { case begin ~ _ ~ end => LocationPRT(Range(begin, end), YonderP) } |
    pos ~ "xrw" ~ pos ^^ { case begin ~ _ ~ end => PermissionPRT(Range(begin, end), ExclusiveReadwriteP) } |
    pos ~ "rw" ~ pos ^^ { case begin ~ _ ~ end => PermissionPRT(Range(begin, end), ReadwriteP) } |
    pos ~ "ro" ~ pos ^^ { case begin ~ _ ~ end => PermissionPRT(Range(begin, end), ReadonlyP) } |
    typeIdentifier ^^ { case inner => NameOrRunePRT(inner) }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def ruleTemplexPR: Parser[ITemplexPRT] = {
    // The template calls are first because Moo:(Int, Bool) is ambiguous, that (Int, Bool)
    // could be interpreted as a pack.
    (pos ~ "_" ~ pos ^^ { case begin ~ inner ~ end => AnonymousRunePRT(Range(begin, end)) }) |
    (pos ~ string ~ pos ^^ { case begin ~ inner ~ end => StringPRT(Range(begin, end), inner) }) |
    (pos ~ ("&" ~> optWhite ~> ruleTemplexPR) ~ pos ^^ { case begin ~ inner ~ end => BorrowPRT(Range(begin, end), inner) }) |
    (pos ~ ("*" ~> optWhite ~> ruleTemplexPR) ~ pos ^^ { case begin ~ inner ~ end => SharePRT(Range(begin, end), inner) }) |
    (pos ~ (keywordOrIdentifierOrRuneRuleTemplexPR <~ optWhite <~ "<" <~ optWhite) ~ (repsep(ruleTemplexPR, optWhite ~> "," <~ optWhite) <~ optWhite <~ ">") ~ pos ^^ {
      case begin ~ template ~ args ~ end => CallPRT(Range(begin, end), template, args)
    }) |
    prototypeRulePR |
    callableRulePR |
    packRulePR |
    manualSeqRulePR |
    repeaterSeqRulePR |
    (pos ~ int ~ pos ^^ { case begin ~ inner ~ end => IntPRT(Range(begin, end), inner) }) |
    keywordOrIdentifierOrRuneRuleTemplexPR
  }

  private[parser] def ruleTemplexSetPR: Parser[List[ITemplexPRT]] = {
    rep1sep(ruleTemplexPR, optWhite ~> "|" <~ optWhite)
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def manualSeqRulePR: Parser[ITemplexPRT] = {
    pos ~ ("[" ~> optWhite ~> repsep(ruleTemplexPR, optWhite ~> "," <~ optWhite) <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~ members ~ end => ManualSequencePRT(Range(begin, end), members)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def repeaterSeqRulePR: Parser[ITemplexPRT] = {
    (pos ~ ("[" ~> optWhite ~> ruleTemplexPR <~ optWhite <~ "*" <~ optWhite) ~ (ruleTemplexPR <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~ size ~ element ~ end => RepeaterSequencePRT(Range(begin, end), MutabilityPRT(Range(begin, begin), MutableP), size, element)
    }) |
    (pos ~ ("[<" ~> optWhite ~> ruleTemplexPR <~ optWhite <~ ">") ~ ((optWhite ~> ruleTemplexPR) <~ optWhite <~ "*" <~ optWhite) ~ (ruleTemplexPR <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~ mutability ~ size ~ element ~ end => RepeaterSequencePRT(Range(begin, end), mutability, size, element)
    })
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def prototypeRulePR: Parser[ITemplexPRT] = {
    pos ~
      ("fn" ~> optWhite ~> exprIdentifier <~ optWhite <~ "(" <~ optWhite) ~
      (repsep(ruleTemplexPR, optWhite ~ "," ~ optWhite) <~ optWhite <~ ")" <~ optWhite) ~
        ruleTemplexPR ~
      pos ^^ {
      case begin ~ name ~ params ~ ret ~ end => PrototypePRT(Range(begin, end), name, params, ret)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def callableRulePR: Parser[ITemplexPRT] = {
    pos ~ ("fn" ~> optWhite ~> opt(":" ~> optWhite ~> ruleTemplexPR) ~ ("(" ~> optWhite ~> repsep(ruleTemplexPR, optWhite ~ "," ~ optWhite) <~ optWhite <~ ")") ~ (optWhite ~> ruleTemplexPR)) ~ pos ^^ {
      case begin ~ (mutability ~ params ~ ret) ~ end => FunctionPRT(Range(begin, end), mutability, PackPRT(Range(begin, end), params), ret)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def packRulePR: Parser[ITemplexPRT] = {
    pos ~ ("(" ~> optWhite ~> repsep(ruleTemplexPR, optWhite ~ "," ~ optWhite) <~ optWhite <~ ")") ~ pos ^^ {
      case begin ~ members ~ end => PackPRT(Range(begin, end), members)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!
}
