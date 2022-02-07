package net.verdagon.vale.parser.old

import net.verdagon.vale.parser.ast.{AnonymousRunePT, BoolPT, BorrowP, CallPT, ExclusiveReadwriteP, FinalP, ITemplexPT, ImmutableP, InlineP, InlinePT, IntPT, InterpretedPT, LocationPT, TuplePT, MutabilityPT, MutableP, NameOrRunePT, OwnP, OwnershipPT, PermissionPT, PointerP, ReadonlyP, ReadwriteP, RegionRune, RuntimeSizedArrayPT, ShareP, StaticSizedArrayPT, VariabilityPT, VaryingP, WeakP, YonderP}
import net.verdagon.vale.parser.{ast, _}

import scala.util.parsing.combinator.RegexParsers

trait TemplexParser extends RegexParsers with ParserUtils {

  def staticSizedArrayTemplex: Parser[ITemplexPT] = {
    pos ~
      ("[" ~> optWhite ~> "#" ~> optWhite ~> templex <~ optWhite <~ "]") ~
      opt("<" ~> optWhite ~> repsep(templex, optWhite ~> "," <~ optWhite) <~ optWhite <~ ">") ~
      templex ~
      pos ^^ {
      case begin ~ numElements ~ maybeTemplateArgs ~ elementType ~ end => {
        val mutability =
          maybeTemplateArgs.toList.flatten.lift(0)
            .getOrElse(MutabilityPT(ast.RangeP(begin, end), MutableP))
        val variability =
          maybeTemplateArgs.toList.flatten.lift(1)
            .getOrElse(VariabilityPT(ast.RangeP(begin, end), FinalP))
        StaticSizedArrayPT(
          ast.RangeP(begin, end),
          mutability,
          variability,
          numElements,
          elementType)
      }
    }
  }


  def runtimeSizedArrayTemplex: Parser[ITemplexPT] = {
    (pos <~ "[" <~ optWhite <~ "]") ~
      opt("<" ~> optWhite ~> repsep(templex, optWhite ~> "," <~ optWhite) <~ optWhite <~ ">") ~
      templex ~
      pos ^^ {
      case begin ~ maybeTemplateArgs ~ elementType ~ end => {
        val mutability =
          maybeTemplateArgs.toList.flatten.lift(0)
            .getOrElse(MutabilityPT(ast.RangeP(begin, end),MutableP))
        RuntimeSizedArrayPT(
          ast.RangeP(begin, end),
          mutability,
          elementType)
      }
    }
//
//    (pos ~ ("[" ~> optWhite ~> templex) ~ (white ~> "*" ~> white ~> templex <~ optWhite <~ "]") ~ pos ^^ {
//      case begin ~ numElements ~ elementType ~ end => {
//        StaticSizedArrayPT(ast.RangeP(begin, end), MutabilityPT(ast.RangeP(begin, end), MutableP), VariabilityPT(ast.RangeP(begin, end), FinalP), numElements, elementType)
//      }
//    }) |
//      (pos ~ ("[<" ~> optWhite ~> atomTemplex <~ optWhite <~ ">") ~ (optWhite ~> templex) ~ (optWhite ~> "*" ~> optWhite ~> templex <~ optWhite <~ "]") ~ pos ^^ {
//        case begin ~ mutability ~ numElements ~ elementType ~ end => {
//          StaticSizedArrayPT(ast.RangeP(begin, end), mutability, VariabilityPT(ast.RangeP(begin, end), FinalP), numElements, elementType)
//        }
//      }) |
//      (pos ~ ("[<" ~> optWhite ~> atomTemplex <~ optWhite <~ ",") ~ (optWhite ~> atomTemplex <~ optWhite <~ ">") ~ (optWhite ~> templex) ~ (optWhite ~> "*" ~> optWhite ~> templex <~ optWhite <~ "]") ~ pos ^^ {
//        case begin ~ mutability ~ variability ~ numElements ~ elementType ~ end => {
//          StaticSizedArrayPT(ast.RangeP(begin, end), mutability, variability, numElements, elementType)
//        }
//      })
  }

  private[parser] def tupleTemplex: Parser[ITemplexPT] = {
    (pos <~ "(" <~ optWhite <~ ")") ~ pos ^^ {
      case begin ~ end => TuplePT(ast.RangeP(begin, end), Vector.empty)
    } |
    pos ~ ("(" ~> optWhite ~> repsep(templex, optWhite ~> "," <~ optWhite) <~ optWhite <~ "," <~ optWhite <~ ")") ~ pos ^^ {
      case begin ~ members ~ end => TuplePT(ast.RangeP(begin, end), members.toVector)
    } |
    pos ~
      ("(" ~> optWhite ~> templex <~ optWhite <~ "," <~ optWhite) ~
      (repsep(templex, optWhite ~> "," <~ optWhite) <~ optWhite <~ ")") ~
      pos ^^ {
      case begin ~ first ~ rest ~ end => TuplePT(ast.RangeP(begin, end), (first :: rest).toVector)
    }
    // Old:
    //  pos ~ ("[" ~> optWhite ~> repsep(templex, optWhite ~> "," <~ optWhite) <~ optWhite <~ "]") ~ pos ^^ {
    //    case begin ~ members ~ end => ManualSequencePT(ast.RangeP(begin, end), members.toVector)
    //  }
  }

  private[parser] def atomTemplex: Parser[ITemplexPT] = {
    ("(" ~> optWhite ~> templex <~ optWhite <~ ")") |
      staticSizedArrayTemplex |
      runtimeSizedArrayTemplex |
      tupleTemplex |
      (pos ~ long ~ pos ^^ { case begin ~ value ~ end => IntPT(ast.RangeP(begin, end), value) }) |
      pos ~ "true" ~ pos ^^ { case begin ~ _ ~ end => BoolPT(ast.RangeP(begin, end), true) } |
      pos ~ "false" ~ pos ^^ { case begin ~ _ ~ end => BoolPT(ast.RangeP(begin, end), false) } |
      pos ~ "own" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(ast.RangeP(begin, end), OwnP) } |
      pos ~ "borrow" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(ast.RangeP(begin, end), BorrowP) } |
      pos ~ "ptr" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(ast.RangeP(begin, end), PointerP) } |
      pos ~ "weak" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(ast.RangeP(begin, end), WeakP) } |
      pos ~ "share" ~ pos ^^ { case begin ~ _ ~ end => OwnershipPT(ast.RangeP(begin, end), ShareP) } |
      mutabilityAtomTemplex |
      variabilityAtomTemplex |
      pos ~ "inl" ~ pos ^^ { case begin ~ _ ~ end => LocationPT(ast.RangeP(begin, end), InlineP) } |
      pos ~ "yon" ~ pos ^^ { case begin ~ _ ~ end => LocationPT(ast.RangeP(begin, end), YonderP) } |
      pos ~ "xrw" ~ pos ^^ { case begin ~ _ ~ end => PermissionPT(ast.RangeP(begin, end), ExclusiveReadwriteP) } |
      pos ~ "rw" ~ pos ^^ { case begin ~ _ ~ end => PermissionPT(ast.RangeP(begin, end), ReadwriteP) } |
      pos ~ "ro" ~ pos ^^ { case begin ~ _ ~ end => PermissionPT(ast.RangeP(begin, end), ReadonlyP) } |
      pos ~ ("_\\b".r) ~ pos ^^ { case begin ~ _ ~ end => AnonymousRunePT(ast.RangeP(begin, end)) } |
      (typeIdentifier ^^ NameOrRunePT)
  }

  def mutabilityAtomTemplex: Parser[MutabilityPT] = {
    pos ~ "mut" ~ pos ^^ { case begin ~ _ ~ end => MutabilityPT(ast.RangeP(begin, end), MutableP) } |
      pos ~ "imm" ~ pos ^^ { case begin ~ _ ~ end => MutabilityPT(ast.RangeP(begin, end), ImmutableP) }
  }

  def variabilityAtomTemplex: Parser[VariabilityPT] = {
    pos ~ "vary" ~ pos ^^ { case begin ~ _ ~ end => VariabilityPT(ast.RangeP(begin, end), VaryingP) } |
      pos ~ "final" ~ pos ^^ { case begin ~ _ ~ end => VariabilityPT(ast.RangeP(begin, end), FinalP) }
  }

  private[parser] def unariedTemplex: Parser[ITemplexPT] = {
    //    (pos ~ ("?" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => NullablePT(Range(begin, end), inner) }) |
    (pos ~ ("^" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => InterpretedPT(ast.RangeP(begin, end), OwnP, ReadwriteP, inner) }) |
      (pos ~ ("@" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => InterpretedPT(ast.RangeP(begin, end), ShareP, ReadonlyP, inner) }) |
      (pos ~ ("**!" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => InterpretedPT(ast.RangeP(begin, end), WeakP, ReadwriteP, inner) }) |
      (pos ~ ("*!" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => InterpretedPT(ast.RangeP(begin, end), PointerP, ReadwriteP, inner) }) |
      (pos ~ ("&!" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => InterpretedPT(ast.RangeP(begin, end), BorrowP, ReadwriteP, inner) }) |
      (pos ~ ("**" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => InterpretedPT(ast.RangeP(begin, end), WeakP, ReadonlyP, inner) }) |
      (pos ~ ("*" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => InterpretedPT(ast.RangeP(begin, end), PointerP, ReadonlyP, inner) }) |
      (pos ~ ("&!" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => InterpretedPT(ast.RangeP(begin, end), BorrowP, ReadwriteP, inner) }) |
      (pos ~ ("&" ~> optWhite ~> templex) ~ pos ^^ { case begin ~ inner ~ end => InterpretedPT(ast.RangeP(begin, end), BorrowP, ReadonlyP, inner) }) |
      (pos ~ ("inl" ~> white ~> templex) ~ pos ^^ { case begin ~ inner ~ end => InlinePT(ast.RangeP(begin, end), inner) }) |
      // A hack to do region highlighting
      ((pos ~ ("'" ~> optWhite ~> exprIdentifier) ~ opt(white ~> templex) ~ pos) ^^ {
        case begin ~ regionName ~ None ~ end => RegionRune(ast.RangeP(begin, end), regionName)
        case begin ~ regionName ~ Some(inner) ~ end => inner
      }) |
      (pos ~ ((atomTemplex <~ optWhite) ~ ("<" ~> optWhite ~> repsep(templex, optWhite ~ "," ~ optWhite) <~ optWhite <~ ">")) ~ pos ^^ {
        case begin ~ (template ~ args) ~ end => CallPT(ast.RangeP(begin, end), template, args.toVector)
      }) |
      atomTemplex
  }

  def templex: Parser[ITemplexPT] = {
    // This is here so if we say:
    //   foreach x in myList { ... }
    // We won't interpret `x in` as a pattern, because
    // we don't interpret `in` as a valid templex.
    not("in ") ~> unariedTemplex
  }
}
