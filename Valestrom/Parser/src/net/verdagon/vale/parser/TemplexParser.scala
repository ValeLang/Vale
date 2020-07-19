package net.verdagon.vale.parser

import scala.util.parsing.combinator.RegexParsers

trait TemplexParser extends RegexParsers with ParserUtils {

  def repeaterSeqTemplex: Parser[ITemplexPT] = {
    (pos ~ ("[" ~> optWhite ~> templex) ~ (white ~> "*" ~> white ~> templex <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~ numElements ~ elementType ~ end => {
        RepeaterSequencePT(Range(begin, end), MutabilityPT(MutableP), numElements, elementType)
      }
    }) |
    (pos ~ ("[<" ~> optWhite ~> unaryTemplex <~ optWhite <~ ">") ~ (optWhite ~> templex) ~ (optWhite ~> "*" ~> optWhite ~> templex <~ optWhite <~ "]") ~ pos ^^ {
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

  private[parser] def unaryTemplex: Parser[ITemplexPT] = {
    ("(" ~> optWhite ~> templex <~ optWhite <~ ")") |
    repeaterSeqTemplex |
    manualSeqTemplex |
    (pos ~ int ~ pos ^^ { case begin ~ value ~ end => IntPT(Range(begin, end), value) }) |
    "true" ^^^ BoolPT(true) |
    "false" ^^^ BoolPT(false) |
    "own" ^^^ OwnershipPT(OwnP) |
    "borrow" ^^^ OwnershipPT(BorrowP) |
    "weak" ^^^ OwnershipPT(WeakP) |
    "share" ^^^ OwnershipPT(ShareP) |
    "mut" ^^^ MutabilityPT(MutableP) |
    "imm" ^^^ MutabilityPT(ImmutableP) |
    "inl" ^^^ LocationPT(InlineP) |
    "yon" ^^^ LocationPT(YonderP) |
    "xrw" ^^^ PermissionPT(ExclusiveReadwriteP) |
    "rw" ^^^ PermissionPT(ReadwriteP) |
    "ro" ^^^ PermissionPT(ReadonlyP) |
    ("_" ^^^ AnonymousRunePT()) |
    (typeIdentifier ^^ NameOrRunePT)
  }

  private[parser] def templex: Parser[ITemplexPT] = {
    ("?" ~> optWhite ~> templex ^^ NullablePT) |
    (pos ~ ("&&" ~> optWhite ~> templex) ~ pos) ^^ { case begin ~ inner ~ end => OwnershippedPT(Range(begin, end), WeakP, inner) } |
    (pos ~ ("&" ~> optWhite ~> templex) ~ pos) ^^ { case begin ~ inner ~ end => OwnershippedPT(Range(begin, end), BorrowP, inner) } |
    // Replace these 'a& rules when we actually implement regions
    (pos ~ (("'" ~ opt(exprIdentifier <~ optWhite) <~ "&") ~> optWhite ~> templex) ~ pos) ^^ { case begin ~ inner ~ end => OwnershippedPT(Range(begin, end), BorrowP, inner) } |
    (pos ~ (("'" ~ opt(exprIdentifier <~ optWhite) <~ "&&") ~> optWhite ~> templex) ~ pos) ^^ { case begin ~ inner ~ end => OwnershippedPT(Range(begin, end), WeakP, inner) } |
    (pos ~ ("^" ~> optWhite ~> templex) ~ pos) ^^ { case begin ~ inner ~ end => OwnershippedPT(Range(begin, end), OwnP, inner) } |
    (pos ~ ("*" ~> optWhite ~> templex) ~ pos) ^^ { case begin ~ inner ~ end => OwnershippedPT(Range(begin, end), ShareP, inner) } |
    (pos ~ ("inl" ~> white ~> templex) ~ pos) ^^ { case begin ~ inner ~ end => InlinePT(Range(begin, end), inner) } |
    (pos ~ ((unaryTemplex <~ optWhite) ~ ("<" ~> optWhite ~> repsep(templex, optWhite ~ "," ~ optWhite) <~ optWhite <~ ">")) ~ pos ^^ {
      case begin ~ (template ~ args) ~ end => CallPT(Range(begin, end), template, args)
    }) |
    unaryTemplex
  }

}
