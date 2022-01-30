package net.verdagon.vale.parser.rules

import net.verdagon.vale.parser
import net.verdagon.vale.parser.ast.{AnonymousRunePT, BoolPT, BorrowP, BorrowPT, CallPT, ExclusiveReadwriteP, FinalP, FunctionPT, ITemplexPT, ImmutableP, InlineP, IntPT, InterpretedPT, LocationPT, ManualSequencePT, MutabilityPT, MutableP, NameOrRunePT, OwnP, OwnershipPT, PackPT, PermissionPT, PointPT, PointerP, PrototypePT, ReadonlyP, ReadwriteP, RepeaterSequencePT, ShareP, SharePT, StringPT, VariabilityPT, WeakP, YonderP}
import net.verdagon.vale.parser.{ast, _}
import net.verdagon.vale.parser.old.ParserUtils

import scala.util.parsing.combinator.RegexParsers

trait RuleTemplexParser extends RegexParsers with ParserUtils {
  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def keywordOrIdentifierOrRuneRuleTemplexPR: Parser[ITemplexPT] = {
    pos ~ "true" ~ pos ^^ { case begin ~ _ ~ end => BoolPT(ast.RangeP(begin, end), true) } |
    pos ~ "false" ~ pos ^^ { case begin ~ _ ~ end => BoolPT(ast.RangeP(begin, end), false) } |
    pos ~ "own" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(ast.RangeP(begin, end), OwnP) } |
    pos ~ "borrow" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(ast.RangeP(begin, end), BorrowP) } |
    pos ~ "ptr" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(ast.RangeP(begin, end), PointerP) } |
    pos ~ "weak" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(ast.RangeP(begin, end), WeakP) } |
    pos ~ "share" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(ast.RangeP(begin, end), ShareP) } |
    pos ~ "mut" ~ pos ^^ { case begin ~ _ ~ end => MutabilityPT(ast.RangeP(begin, end), MutableP) } |
    pos ~ "imm" ~ pos ^^ { case begin ~ _ ~ end => MutabilityPT(ast.RangeP(begin, end), ImmutableP) } |
    pos ~ "inl" ~ pos ^^ { case begin ~ _ ~ end => LocationPT(ast.RangeP(begin, end), InlineP) } |
    pos ~ "yon" ~ pos ^^ { case begin ~ _ ~ end => LocationPT(ast.RangeP(begin, end), YonderP) } |
    pos ~ "xrw" ~ pos ^^ { case begin ~ _ ~ end => PermissionPT(ast.RangeP(begin, end), ExclusiveReadwriteP) } |
    pos ~ "rw" ~ pos ^^ { case begin ~ _ ~ end => PermissionPT(ast.RangeP(begin, end), ReadwriteP) } |
    pos ~ "ro" ~ pos ^^ { case begin ~ _ ~ end => PermissionPT(ast.RangeP(begin, end), ReadonlyP) } |
    pos ~ ("_\\b".r) ~ pos ^^ { case begin ~ _ ~ end => AnonymousRunePT(ast.RangeP(begin, end)) } |
    typeIdentifier ^^ { case inner => NameOrRunePT(inner) }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def ruleTemplexPR: Parser[ITemplexPT] = {
    // The template calls are first because Moo:(Int, Bool) is ambiguous, that (Int, Bool)
    // could be interpreted as a pack.
    (pos ~ string ~ pos ^^ { case begin ~ inner ~ end => StringPT(ast.RangeP(begin, end), inner.str) }) |
    (pos ~ ("&!" ~> optWhite ~> ruleTemplexPR) ~ pos ^^ { case begin ~ inner ~ end => InterpretedPT(ast.RangeP(begin, end), BorrowP, ReadwriteP, inner) }) |
    (pos ~ ("&" ~> optWhite ~> ruleTemplexPR) ~ pos ^^ { case begin ~ inner ~ end => BorrowPT(ast.RangeP(begin, end), inner) }) |
    (pos ~ ("*" ~> optWhite ~> ruleTemplexPR) ~ pos ^^ { case begin ~ inner ~ end => PointPT(ast.RangeP(begin, end), inner) }) |
    (pos ~ ("@" ~> optWhite ~> ruleTemplexPR) ~ pos ^^ { case begin ~ inner ~ end => SharePT(ast.RangeP(begin, end), inner) }) |
    (pos ~ (keywordOrIdentifierOrRuneRuleTemplexPR <~ optWhite <~ "<" <~ optWhite) ~ (repsep(ruleTemplexPR, optWhite ~> "," <~ optWhite) <~ optWhite <~ ">") ~ pos ^^ {
      case begin ~ template ~ args ~ end => CallPT(ast.RangeP(begin, end), template, args.toVector)
    }) |
    prototypeRulePR |
    callableRulePR |
    packRulePR |
    manualSeqRulePR |
    repeaterSeqRulePR |
    (pos ~ long ~ pos ^^ { case begin ~ inner ~ end => IntPT(ast.RangeP(begin, end), inner) }) |
    keywordOrIdentifierOrRuneRuleTemplexPR |
    // This is at the end because we dont want to preclude identifiers like __Never
    (pos ~ "_" ~ pos ^^ { case begin ~ inner ~ end => AnonymousRunePT(ast.RangeP(begin, end)) })
  }

  private[parser] def ruleTemplexSetPR: Parser[Vector[ITemplexPT]] = {
    rep1sep(ruleTemplexPR, optWhite ~> "|" <~ optWhite) ^^ (a => a.toVector)
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def manualSeqRulePR: Parser[ITemplexPT] = {
    pos ~ ("[" ~> optWhite ~> repsep(ruleTemplexPR, optWhite ~> "," <~ optWhite) <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~ members ~ end => ManualSequencePT(ast.RangeP(begin, end), members.toVector)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def repeaterSeqRulePR: Parser[ITemplexPT] = {
    (pos ~ ("[" ~> optWhite ~> ruleTemplexPR <~ optWhite <~ "*" <~ optWhite) ~ (ruleTemplexPR <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~ size ~ element ~ end => {
        RepeaterSequencePT(ast.RangeP(begin, end), MutabilityPT(ast.RangeP(begin, begin), MutableP), VariabilityPT(ast.RangeP(begin, begin), FinalP), size, element)
      }
    }) |
    (pos ~ ("[<" ~> optWhite ~> ruleTemplexPR <~ optWhite <~ ">") ~ ((optWhite ~> ruleTemplexPR) <~ optWhite <~ "*" <~ optWhite) ~ (ruleTemplexPR <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~ mutability ~ size ~ element ~ end => {
        RepeaterSequencePT(ast.RangeP(begin, end), mutability, VariabilityPT(ast.RangeP(begin, begin), FinalP), size, element)
      }
    }) |
    (pos ~ ("[<" ~> optWhite ~> ruleTemplexPR <~ optWhite <~ ",") ~ (optWhite ~> ruleTemplexPR <~ optWhite <~ ">") ~ ((optWhite ~> ruleTemplexPR) <~ optWhite <~ "*" <~ optWhite) ~ (ruleTemplexPR <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~ mutability ~ variability ~ size ~ element ~ end => {
        RepeaterSequencePT(ast.RangeP(begin, end), mutability, variability, size, element)
      }
    })
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def prototypeRulePR: Parser[ITemplexPT] = {
    pos ~
      ("fn" ~> optWhite ~> exprIdentifier <~ optWhite <~ "(" <~ optWhite) ~
      (repsep(ruleTemplexPR, optWhite ~ "," ~ optWhite) <~ optWhite <~ ")" <~ optWhite) ~
        ruleTemplexPR ~
      pos ^^ {
      case begin ~ name ~ params ~ ret ~ end => PrototypePT(ast.RangeP(begin, end), name, params.toVector, ret)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def callableRulePR: Parser[ITemplexPT] = {
    pos ~ ("fn" ~> optWhite ~> opt(":" ~> optWhite ~> ruleTemplexPR) ~ ("(" ~> optWhite ~> repsep(ruleTemplexPR, optWhite ~ "," ~ optWhite) <~ optWhite <~ ")") ~ (optWhite ~> ruleTemplexPR)) ~ pos ^^ {
      case begin ~ (mutability ~ params ~ ret) ~ end => FunctionPT(ast.RangeP(begin, end), mutability, PackPT(ast.RangeP(begin, end), params.toVector), ret)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def packRulePR: Parser[ITemplexPT] = {
    pos ~ ("(" ~> optWhite ~> repsep(ruleTemplexPR, optWhite ~ "," ~ optWhite) <~ optWhite <~ ")") ~ pos ^^ {
      case begin ~ members ~ end => PackPT(ast.RangeP(begin, end), members.toVector)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!
}
