package net.verdagon.vale.parser.patterns

import net.verdagon.vale.parser._

import scala.util.parsing.combinator.RegexParsers

//trait PatternTemplexParser extends RegexParsers with ParserUtils {
//  // Add any new patterns to the "Check no parser patterns match empty" test!
//
//  private[parser] def nameOrRunePatternTemplex: Parser[NameOrRunePT] = {
//    typeIdentifier ^^ NameOrRunePT
//  }
//
//  // Add any new patterns to the "Check no parser patterns match empty" test!
//
//  private[parser] def patternTemplex: Parser[ITemplexPT] = {
//    ownershippedTemplex |
////    callablePatternTemplex |
//    manualSeqPatternTemplex |
//    repeaterSeqPatternTemplex |
//    ((nameOrRunePatternTemplex <~ optWhite <~ "<" <~ optWhite) ~ repsep(patternTemplex, optWhite ~> "," <~ optWhite) <~ optWhite <~ ">" ^^ {
//      case template ~ args => CallPT(template, args)
//    }) |
//    (pos ~ int ~ pos ^^ { case begin ~ value ~ end => IntPT(Range(begin, end), value) }) |
//    ("mut" ^^^ MutabilityPT(MutableP)) |
//    ("imm" ^^^ MutabilityPT(ImmutableP)) |
//    ("true" ^^^ BoolPT(true)) |
//    ("false" ^^^ BoolPT(false)) |
//    "_" ^^^ AnonymousRunePT() |
//    nameOrRunePatternTemplex
//  }
//
//  // Add any new patterns to the "Check no parser patterns match empty" test!
//
//  private[parser] def ownershippedTemplex: Parser[ITemplexPT] = {
//    (pos ~ (("&&"|"&"| ("'" ~ opt(exprIdentifier <~ optWhite) <~ "&")) ~> optWhite ~> patternTemplex) ~ pos ^^ {
//      case begin ~ inner ~ end => OwnershippedPT(Range(begin, end), BorrowP, inner)
//    }) |
//    (pos ~ ("*" ~> optWhite ~> patternTemplex) ~ pos ^^ {
//      case begin ~ inner ~ end => OwnershippedPT(Range(begin, end), ShareP, inner)
//    }) |
//    (pos ~ ("^" ~> optWhite ~> patternTemplex) ~ pos ^^ {
//      case begin ~ inner ~ end => OwnershippedPT(Range(begin, end), OwnP, inner)
//    })
//  }
//
//  // Add any new patterns to the "Check no parser patterns match empty" test!
//
//  private[parser] def manualSeqPatternTemplex: Parser[ITemplexPT] = {
//    ("[" ~> optWhite ~> repsep(patternTemplex, optWhite ~> "," <~ optWhite) <~ optWhite <~ "]") ^^ {
//      case members => ManualSequencePT(members)
//    }
//  }
//
//  // Add any new patterns to the "Check no parser patterns match empty" test!
//
//  private[parser] def repeaterSeqPatternTemplex: Parser[ITemplexPT] = {
//    (pos ~ ("[" ~> optWhite ~> patternTemplex <~ optWhite <~ "*" <~ optWhite) ~ (patternTemplex <~ optWhite <~ "]") ~ pos ^^ {
//      case begin ~ size ~ element ~ end => RepeaterSequencePT(Range(begin, end), MutabilityPT(MutableP), size, element)
//    }) |
//    ((pos ~ ("[<" ~> optWhite ~> patternTemplex <~ optWhite <~ ">") ~ (optWhite ~> patternTemplex) <~ optWhite <~ "*" <~ optWhite) ~ (patternTemplex <~ optWhite <~ "]") ~ pos ^^ {
//      case begin ~ mutability ~ size ~ element ~ end => RepeaterSequencePT(Range(begin, end), mutability, size, element)
//    })
//  }
////
////  // Add any new patterns to the "Check no parser patterns match empty" test!
////
////  private[parser] def callablePatternTemplex: Parser[ITemplexPT] = {
////    ("fn" ~> optWhite ~> opt(":" ~> optWhite ~> patternTemplex <~ optWhite)) ~ ("(" ~> optWhite ~> repsep(patternTemplex, optWhite ~ "," ~ optWhite) <~ optWhite <~ ")") ~ (optWhite ~> patternTemplex) ^^ {
////      case mutability ~ params ~ ret => FunctionPT(mutability, params, ret)
////    }
////  }
//
//  // Add any new patterns to the "Check no parser patterns match empty" test!
//}
