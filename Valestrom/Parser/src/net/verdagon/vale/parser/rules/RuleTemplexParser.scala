package net.verdagon.vale.parser.rules

import net.verdagon.vale.parser
import net.verdagon.vale.parser._

import scala.util.parsing.combinator.RegexParsers

trait RuleTemplexParser extends RegexParsers with ParserUtils {
  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def keywordOrIdentifierOrRuneRuleTemplexPR: Parser[ITemplexPRT] = {
    "true" ^^^ BoolPRT(true) |
    "false" ^^^ BoolPRT(false) |
    "own" ^^^ OwnershipPRT(OwnP) |
    "borrow" ^^^ OwnershipPRT(BorrowP) |
    "weak" ^^^ OwnershipPRT(WeakP) |
    "share" ^^^ OwnershipPRT(ShareP) |
    "mut" ^^^ MutabilityPRT(MutableP) |
    "imm" ^^^ MutabilityPRT(ImmutableP) |
    "inl" ^^^ LocationPRT(InlineP) |
    "yon" ^^^ LocationPRT(YonderP) |
    "xrw" ^^^ PermissionPRT(ExclusiveReadwriteP) |
    "rw" ^^^ PermissionPRT(ReadwriteP) |
    "ro" ^^^ PermissionPRT(ReadonlyP) |
    typeIdentifier ^^ NameOrRunePRT
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def ruleTemplexPR: Parser[ITemplexPRT] = {
    // The template calls are first because Moo:(Int, Bool) is ambiguous, that (Int, Bool)
    // could be interpreted as a pack.
    ("_" ^^^ AnonymousRunePRT()) |
    (string ^^ StringPRT) |
    (("&" ~> optWhite ~> ruleTemplexPR) ^^ BorrowPRT) |
    (("*" ~> optWhite ~> ruleTemplexPR) ^^ SharePRT) |
    ((keywordOrIdentifierOrRuneRuleTemplexPR <~ optWhite <~ "<" <~ optWhite) ~ repsep(ruleTemplexPR, optWhite ~> "," <~ optWhite) <~ optWhite <~ ">" ^^ {
      case template ~ args => CallPRT(template, args)
    }) |
    prototypeRulePR |
    callableRulePR |
    packRulePR |
    manualSeqRulePR |
    repeaterSeqRulePR |
    (int ^^ IntPRT) |
    keywordOrIdentifierOrRuneRuleTemplexPR
  }

  private[parser] def ruleTemplexSetPR: Parser[List[ITemplexPRT]] = {
    rep1sep(ruleTemplexPR, optWhite ~> "|" <~ optWhite)
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def manualSeqRulePR: Parser[ITemplexPRT] = {
    ("[" ~> optWhite ~> repsep(ruleTemplexPR, optWhite ~> "," <~ optWhite) <~ optWhite <~ "]") ^^ {
      case members => ManualSequencePRT(members)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def repeaterSeqRulePR: Parser[ITemplexPRT] = {
    (("[" ~> optWhite ~> ruleTemplexPR <~ optWhite <~ "*" <~ optWhite) ~ (ruleTemplexPR <~ optWhite <~ "]") ^^ {
      case size ~ element => RepeaterSequencePRT(MutabilityPRT(MutableP), size, element)
    }) |
    ((("[<" ~> optWhite ~> ruleTemplexPR <~ optWhite <~ ">") ~ (optWhite ~> ruleTemplexPR) <~ optWhite <~ "*" <~ optWhite) ~ (ruleTemplexPR <~ optWhite <~ "]") ^^ {
      case mutability ~ size ~ element => RepeaterSequencePRT(mutability, size, element)
    })
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def prototypeRulePR: Parser[ITemplexPRT] = {
    ("fn" ~> optWhite ~> exprIdentifier <~ optWhite <~ "(" <~ optWhite) ~
        (repsep(ruleTemplexPR, optWhite ~ "," ~ optWhite) <~ optWhite <~ ")" <~ optWhite) ~
        ruleTemplexPR ^^ {
      case name ~ params ~ ret => PrototypePRT(name, params, ret)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def callableRulePR: Parser[ITemplexPRT] = {
    pos ~ ("fn" ~> optWhite ~> opt(":" ~> optWhite ~> ruleTemplexPR) ~ ("(" ~> optWhite ~> repsep(ruleTemplexPR, optWhite ~ "," ~ optWhite) <~ optWhite <~ ")") ~ (optWhite ~> ruleTemplexPR)) ~ pos ^^ {
      case begin ~ (mutability ~ params ~ ret) ~ end => FunctionPRT(Range(begin, end), mutability, PackPRT(params), ret)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def packRulePR: Parser[ITemplexPRT] = {
    ("(" ~> optWhite ~> repsep(ruleTemplexPR, optWhite ~ "," ~ optWhite) <~ optWhite <~ ")") ^^ {
      case members => PackPRT(members)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!
}
