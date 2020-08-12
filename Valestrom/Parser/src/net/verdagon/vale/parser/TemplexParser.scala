package net.verdagon.vale.parser

import net.verdagon.vale.{vcheck, vfail}

import scala.util.parsing.combinator.RegexParsers

trait TemplexParser extends RegexParsers with ParserUtils {

  def repeaterSeqTemplex: Parser[ITemplexPT] = {
    (pos ~ ("[" ~> optWhite ~> templex) ~ (white ~> "*" ~> white ~> templex <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~ numElements ~ elementType ~ end => {
        RepeaterSequencePT(Range(begin, end), MutabilityPT(Range(begin, end), MutableP), numElements, elementType)
      }
    }) |
    (pos ~ ("[<" ~> optWhite ~> atomTemplex <~ optWhite <~ ">") ~ (optWhite ~> templex) ~ (optWhite ~> "*" ~> optWhite ~> templex <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~  mutability ~ numElements ~ elementType ~ end => {
        RepeaterSequencePT(Range(begin, end), mutability, numElements, elementType)
      }
    })
  }

  private[parser] def manualSeqTemplex: Parser[ITemplexPT] = {
    pos ~ ("[" ~> optWhite ~> repsep(templex, optWhite ~> "," <~ optWhite) <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~ members ~ end => ManualSequencePT(Range(begin, end), members)
    }
  }

  private[parser] def atomTemplex: Parser[ITemplexPT] = {
    ("(" ~> optWhite ~> templex <~ optWhite <~ ")") |
    repeaterSeqTemplex |
    manualSeqTemplex |
    (pos ~ int ~ pos ^^ { case begin ~ value ~ end => IntPT(Range(begin, end), value) }) |
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
    pos ~ "_" ~ pos ^^ { case begin ~ _ ~ end => AnonymousRunePT(Range(begin, end)) } |
    (typeIdentifier ^^ NameOrRunePT)
  }

  private[parser] def unariedTemplex: Parser[ITemplexPT] = {
    (pos ~ ("!" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => PermissionedPT(Range(begin, end), ReadwriteP, inner) }) |
    (pos ~ ("?" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => NullablePT(Range(begin, end), inner) }) |
    (pos ~ ("^" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => OwnershippedPT(Range(begin, end), OwnP, inner) }) |
    (pos ~ ("*" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => OwnershippedPT(Range(begin, end), ShareP, inner) }) |
    (pos ~ ("&&" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => OwnershippedPT(Range(begin, end), WeakP, inner) }) |
    (pos ~ ("&" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => OwnershippedPT(Range(begin, end), BorrowP, inner) }) |
    (pos ~ ("inl" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => InlinePT(Range(begin, end), inner) }) |
    // A hack to do region highlighting
    ((pos ~ ("'" ~> optWhite ~> exprIdentifier <~ optWhite) ~ templex ~ pos) ^^ { case begin ~ regionName ~ inner ~ end => inner }) |
    (pos ~ ((atomTemplex <~ optWhite) ~ ("<" ~> optWhite ~> repsep(templex, optWhite ~ "," ~ optWhite) <~ optWhite <~ ">")) ~ pos ^^ {
      case begin ~ (template ~ args) ~ end => CallPT(Range(begin, end), template, args)
    }) |
    atomTemplex
  }

  private[parser] def templex: Parser[ITemplexPT] = {
    unariedTemplex
  }
}
