package net.verdagon.vale.parser.rules

import net.verdagon.vale.parser._

import scala.util.parsing.combinator.RegexParsers

trait RuleParser extends RegexParsers with ParserUtils {

  private[parser] def ruleTemplexPR: Parser[ITemplexPRT]

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def level0PR: Parser[IRulexPR] = {
    ruleTemplexPR ^^ TemplexPR
  }

  private[parser] def typedPR: Parser[TypedPR] = {
    (underscoreOr(typeIdentifier) ~ (white ~> typePR)) ^^ {
      case maybeRune ~ tyype => TypedPR(maybeRune, tyype)
    }
  }

  private[parser] def typePR: Parser[ITypePR] = {
    "Ownership" ^^^ OwnershipTypePR |
    "Mutability" ^^^ MutabilityTypePR |
    "Permission" ^^^ PermissionTypePR |
    "Location" ^^^ LocationTypePR |
    "Ref" ^^^ CoordTypePR |
    "Prot" ^^^ PrototypeTypePR |
//    "Struct" ^^^ StructTypePR |
//    "Seq" ^^^ SequenceTypePR |
//    "Callable" ^^^ CallableTypePR |
//    "Interface" ^^^ InterfaceTypePR |
    // Int must be after Interface, otherwise we'll have a hanging "erface"
    // Same with Kint and KindTemplate
    "Int" ^^^ IntTypePR |
    "Kind" ^^^ KindTypePR
  }

  private[parser] def destructurePR: Parser[IRulexPR] = {
    ((typePR <~ "(" <~ optWhite) ~
      repsep(rulePR, optWhite ~> "," <~ optWhite) <~ optWhite <~ ")" ^^ {
      case tyype ~ components => ComponentsPR(TypedPR(None, tyype), components)
    }) |
    ((typedPR <~ "(" <~ optWhite) ~
      repsep(rulePR, optWhite ~> "," <~ optWhite) <~ optWhite <~ ")" ^^ {
      case container ~ components => ComponentsPR(container, components)
    })
  }

  private[parser] def dotPR(innerRule: Parser[IRulexPR]): Parser[IRulexPR] = {
    (innerRule <~ optWhite <~ "." <~ optWhite) ~ typeIdentifier ^^ {
      case inner ~ name => DotPR(inner, name)
    }
  }

  private[parser] def orPR(inner: Parser[IRulexPR]): Parser[IRulexPR] = {
    (inner <~ optWhite <~ "|" <~ optWhite) ~ rep1sep(inner, optWhite ~> "|" <~ optWhite) ^^ {
      case firstPossibility ~ restPossibilities => OrPR(firstPossibility :: restPossibilities)
    }
  }

  private[parser] def level1PR: Parser[IRulexPR] = {
    typedPR | level0PR
  }

  private[parser] def level2PR: Parser[IRulexPR] = {
    destructurePR | level1PR
  }

  private[parser] def level3PR: Parser[IRulexPR] = {
    implementsPR |
    existsPR |
    dotPR(level2PR) |
    level2PR
  }

  private[parser] def level4PR: Parser[IRulexPR] = {
    orPR(level3PR) | level3PR
  }

  private[parser] def level5PR: Parser[IRulexPR] = {
    equalsPR(level4PR) |
    level4PR
  }

  private[parser] def rulePR: Parser[IRulexPR] = {
    level5PR
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def identifyingRune: Parser[StringP] = {
    pos ~ opt("'") ~ exprIdentifier ^^ {
      case begin ~ None ~ e => e
      case begin ~ Some(_) ~ StringP(r, name) => StringP(Range(begin, r.end), name)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def identifyingRunesPR: Parser[IdentifyingRunesP] = {
    pos ~ ("<" ~> optWhite ~> repsep(identifyingRune, optWhite ~> "," <~ optWhite) <~ optWhite <~ ">") ~ pos ^^ {
      case begin ~ runes ~ end => IdentifyingRunesP(Range(begin, end), runes)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  def templateRulesPR: Parser[TemplateRulesP] = {
    pos ~ ("rules" ~> optWhite ~> "(" ~> optWhite ~> repsep(rulePR, optWhite ~> "," <~ optWhite) <~ optWhite <~ ")") ~ pos ^^ {
      case begin ~ rules ~ end => TemplateRulesP(Range(begin, end), rules)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  // Atomic means no neighboring, see parser doc.
  private[parser] def implementsPR: Parser[IRulexPR] = {
    pstr("implements") ~ (optWhite ~> "(" ~> optWhite ~> rulePR <~ optWhite <~ "," <~ optWhite) ~
        (rulePR <~ optWhite <~ ")") ^^ {
      case impl ~ struct ~ interface => CallPR(impl, List(struct, interface))
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  // Atomic means no neighboring, see parser doc.
  private[parser] def existsPR: Parser[IRulexPR] = {
    pstr("exists") ~ (optWhite ~> "(" ~> optWhite ~> rulePR <~ optWhite <~ ")") ^^ {
      case exists ~ thing => CallPR(exists, List(thing))
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def packPR: Parser[PackPR] = {
    ("(" ~> optWhite ~> repsep(rulePR, optWhite ~> "," <~ optWhite) <~ optWhite <~ ")") ^^ PackPR
  }

  // Add any new rules to the "Nothing matches empty string" test!

  private[parser] def equalsPR(inner: Parser[IRulexPR]): Parser[EqualsPR] = {
    (inner <~ optWhite <~ "=" <~ optWhite) ~ inner ^^ {
      case left ~ right => EqualsPR(left, right)
    }
  }

  // Add any new rules to the "Nothing matches empty string" test!
}
