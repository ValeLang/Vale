package net.verdagon.vale.parser.rules

import net.verdagon.vale.parser
import net.verdagon.vale.parser._

import scala.util.parsing.combinator.RegexParsers

trait RuleTemplexParser extends RegexParsers with ParserUtils {
  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def keywordOrIdentifierOrRuneRuleTemplexPR: Parser[ITemplexPT] = {
    pos ~ "true" ~ pos ^^ { case begin ~ _ ~ end => BoolPT(Range(begin, end), true) } |
    pos ~ "false" ~ pos ^^ { case begin ~ _ ~ end => BoolPT(Range(begin, end), false) } |
    pos ~ "own" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(Range(begin, end), OwnP) } |
    pos ~ "borrow" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(Range(begin, end), BorrowP) } |
    pos ~ "weak" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(Range(begin, end), WeakP) } |
    pos ~ "share" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(Range(begin, end), ShareP) } |
    pos ~ "mut" ~ pos ^^ { case begin ~ _ ~ end => MutabilityPT(Range(begin, end), MutableP) } |
    pos ~ "imm" ~ pos ^^ { case begin ~ _ ~ end => MutabilityPT(Range(begin, end), ImmutableP) } |
    pos ~ "inl" ~ pos ^^ { case begin ~ _ ~ end => LocationPT(Range(begin, end), InlineP) } |
    pos ~ "yon" ~ pos ^^ { case begin ~ _ ~ end => LocationPT(Range(begin, end), YonderP) } |
    pos ~ "xrw" ~ pos ^^ { case begin ~ _ ~ end => PermissionPT(Range(begin, end), ExclusiveReadwriteP) } |
    pos ~ "rw" ~ pos ^^ { case begin ~ _ ~ end => PermissionPT(Range(begin, end), ReadwriteP) } |
    pos ~ "ro" ~ pos ^^ { case begin ~ _ ~ end => PermissionPT(Range(begin, end), ReadonlyP) } |
    typeIdentifier ^^ { case inner => NameOrRunePT(inner) }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def ruleTemplexPR: Parser[ITemplexPT] = {
    // The template calls are first because Moo:(Int, Bool) is ambiguous, that (Int, Bool)
    // could be interpreted as a pack.
    (pos ~ "_" ~ pos ^^ { case begin ~ inner ~ end => AnonymousRunePT(Range(begin, end)) }) |
    (pos ~ string ~ pos ^^ { case begin ~ inner ~ end => StringPT(Range(begin, end), inner.str) }) |
    (pos ~ ("&" ~> optWhite ~> ruleTemplexPR) ~ pos ^^ { case begin ~ inner ~ end => BorrowPT(Range(begin, end), inner) }) |
    (pos ~ ("*" ~> optWhite ~> ruleTemplexPR) ~ pos ^^ { case begin ~ inner ~ end => SharePT(Range(begin, end), inner) }) |
    (pos ~ (keywordOrIdentifierOrRuneRuleTemplexPR <~ optWhite <~ "<" <~ optWhite) ~ (repsep(ruleTemplexPR, optWhite ~> "," <~ optWhite) <~ optWhite <~ ">") ~ pos ^^ {
      case begin ~ template ~ args ~ end => CallPT(Range(begin, end), template, args)
    }) |
    prototypeRulePR |
    callableRulePR |
    packRulePR |
    manualSeqRulePR |
    repeaterSeqRulePR |
    (pos ~ int ~ pos ^^ { case begin ~ inner ~ end => IntPT(Range(begin, end), inner) }) |
    keywordOrIdentifierOrRuneRuleTemplexPR
  }

  private[parser] def ruleTemplexSetPR: Parser[List[ITemplexPT]] = {
    rep1sep(ruleTemplexPR, optWhite ~> "|" <~ optWhite)
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def manualSeqRulePR: Parser[ITemplexPT] = {
    pos ~ ("[" ~> optWhite ~> repsep(ruleTemplexPR, optWhite ~> "," <~ optWhite) <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~ members ~ end => ManualSequencePT(Range(begin, end), members)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def repeaterSeqRulePR: Parser[ITemplexPT] = {
    (pos ~ ("[" ~> optWhite ~> ruleTemplexPR <~ optWhite <~ "*" <~ optWhite) ~ (ruleTemplexPR <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~ size ~ element ~ end => RepeaterSequencePT(Range(begin, end), MutabilityPT(Range(begin, begin), MutableP), size, element)
    }) |
    (pos ~ ("[<" ~> optWhite ~> ruleTemplexPR <~ optWhite <~ ">") ~ ((optWhite ~> ruleTemplexPR) <~ optWhite <~ "*" <~ optWhite) ~ (ruleTemplexPR <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~ mutability ~ size ~ element ~ end => RepeaterSequencePT(Range(begin, end), mutability, size, element)
    })
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def prototypeRulePR: Parser[ITemplexPT] = {
    pos ~
      ("fn" ~> optWhite ~> exprIdentifier <~ optWhite <~ "(" <~ optWhite) ~
      (repsep(ruleTemplexPR, optWhite ~ "," ~ optWhite) <~ optWhite <~ ")" <~ optWhite) ~
        ruleTemplexPR ~
      pos ^^ {
      case begin ~ name ~ params ~ ret ~ end => PrototypePT(Range(begin, end), name, params, ret)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def callableRulePR: Parser[ITemplexPT] = {
    pos ~ ("fn" ~> optWhite ~> opt(":" ~> optWhite ~> ruleTemplexPR) ~ ("(" ~> optWhite ~> repsep(ruleTemplexPR, optWhite ~ "," ~ optWhite) <~ optWhite <~ ")") ~ (optWhite ~> ruleTemplexPR)) ~ pos ^^ {
      case begin ~ (mutability ~ params ~ ret) ~ end => FunctionPT(Range(begin, end), mutability, PackPT(Range(begin, end), params), ret)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def packRulePR: Parser[ITemplexPT] = {
    pos ~ ("(" ~> optWhite ~> repsep(ruleTemplexPR, optWhite ~ "," ~ optWhite) <~ optWhite <~ ")") ~ pos ^^ {
      case begin ~ members ~ end => PackPT(Range(begin, end), members)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!
}
