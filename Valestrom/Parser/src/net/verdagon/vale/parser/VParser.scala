package net.verdagon.vale.parser

import net.verdagon.vale.parser.patterns.PatternParser
import net.verdagon.vale.parser.rules._
import net.verdagon.vale.{vassert, vfail}

import scala.util.parsing.combinator.RegexParsers

// Rules of thumb:
// - A rule shouldn't match the empty string.
// - A rule shouldn't match whitespace on either side (it makes it hard to require
//   whitespace, like inside the let statement).

object VParser
    extends RuleParser
        with RuleTemplexParser
        with RegexParsers
        with ParserUtils
        with TemplexParser
        with PatternParser
//        with PatternTemplexParser
        with ExpressionParser {
  override def skipWhitespace = false
  override val whiteSpace = "[ \t\r\f]+".r

  def filledBody: Parser[BlockPE] = bracedBlock

  def emptyBody: Parser[BlockPE] = {
    pos ~ ("{" ~> optWhite ~> pos <~ optWhite <~ "}") ~ pos ^^ {
      case begin ~ middle ~ end => BlockPE(Range(begin, end), List(VoidPE(Range(middle, middle))))
    }
  }

  def noBody: Parser[Unit] = {
    ";" ^^^ ()
  }

  def maybeBody: Parser[Option[BlockPE]] = {
    (filledBody ^^ (x => Some(x))) |
      (emptyBody ^^^ None) |
      (noBody ^^^ None)
  }

  def topLevelFunction: Parser[FunctionP] = {
        pos ~
        existsW("abstract") ~
        existsW("extern") ~
        ("fn" ~> optWhite ~> exprIdentifier <~ optWhite) ~
        opt(identifyingRunesPR <~ optWhite) ~
        (patternPrototypeParams <~ optWhite) ~
        // We have template rules before and after the return type because the return type likes
        // to parse the `rules` in `rules(stuff here)` as a type and then fail when it hits the
        // parentheses.
        opt(templateRulesPR <~ optWhite) ~
        opt(templex <~ optWhite) ~
        opt(templateRulesPR <~ optWhite) ~
        (maybeBody) ~
        pos ^^ {
      case begin ~ maybeAbstract ~ maybeExtern ~ name ~ identifyingRunes ~ patternParams ~ maybeTemplateRulesBeforeReturn ~ maybeReturnType ~ maybeTemplateRulesAfterReturn ~ maybeBody ~ end => {
        vassert(!(maybeTemplateRulesBeforeReturn.nonEmpty && maybeTemplateRulesAfterReturn.nonEmpty))
        FunctionP(
          Range(begin, end),
          Some(name),
          maybeExtern,
          maybeAbstract,
          identifyingRunes,
          (maybeTemplateRulesBeforeReturn.toList ++ maybeTemplateRulesAfterReturn.toList).headOption,
          Some(patternParams),
          maybeReturnType,
          maybeBody)
      }
    }
  }

  def structMember: Parser[StructMemberP] = {
    pos ~ (exprIdentifier ~ opt("!") <~ optWhite) ~ (templex <~ optWhite <~ ";") ~ pos ^^ {
      case begin ~ (name ~ None) ~ tyype ~ end => StructMemberP(Range(begin, end), name, FinalP, tyype)
      case begin ~ (name ~ Some(_)) ~ tyype ~ end => StructMemberP(Range(begin, end), name, VaryingP, tyype)
    }
  }

  def structContent: Parser[IStructContent] = {
    structMember | (topLevelFunction ^^ StructMethodP)
  }

  private[parser] def interface: Parser[InterfaceP] = {
    pos ~
        ("interface " ~> optWhite ~> exprIdentifier <~ optWhite) ~
        opt(identifyingRunesPR <~ optWhite) ~
        existsMW("sealed") ~
        (opt("imm") <~ optWhite) ~
        (opt(templateRulesPR) <~ optWhite <~ "{" <~ optWhite) ~
        repsep(topLevelFunction, optWhite) ~
        (optWhite ~> "}" ~> pos) ^^ {
      case begin ~ name ~ maybeIdentifyingRunes ~ seealed ~ imm ~ maybeTemplateRules ~ members ~ end => {
        val mutability = if (imm == Some("imm")) ImmutableP else MutableP
        InterfaceP(Range(begin, end), name, seealed, mutability, maybeIdentifyingRunes, maybeTemplateRules, members)
      }
    }
  }

  def struct: Parser[StructP] = {
    pos ~ ("struct" ~> optWhite ~> exprIdentifier <~ optWhite) ~
        opt(identifyingRunesPR <~ optWhite) ~
        (opt("export") <~ optWhite) ~
        (opt("imm") <~ optWhite) ~
        (opt(templateRulesPR) <~ optWhite) ~
        (pos <~ "{" <~ optWhite) ~
        ("..." <~ optWhite ^^^ List() | repsep(structContent, optWhite)) ~
        (optWhite ~> "}" ~> pos) ^^ {
      case begin ~ name ~ identifyingRunes ~ export ~ imm ~ maybeTemplateRules ~ membersBegin ~ members ~ end => {
        val mutability = if (imm == Some("imm")) ImmutableP else MutableP
        StructP(Range(begin, end), name, export.nonEmpty, mutability, identifyingRunes, maybeTemplateRules, StructMembersP(Range(membersBegin, end), members))
      }
    }
  }

  private[parser] def impl: Parser[ImplP] = {
    pos ~ (("impl" ~> optWhite ~>
      opt(identifyingRunesPR <~ optWhite) ~
      opt(templateRulesPR) <~ optWhite) ~
      (templex <~ optWhite <~ "for" <~ optWhite) ~
      (templex <~ optWhite <~ ";")) ~ pos ^^ {
      case begin ~ (maybeIdentifyingRunes ~ maybeTemplateRules ~ structType ~ interfaceType) ~ end => {
        ImplP(Range(begin, end), maybeIdentifyingRunes, maybeTemplateRules, structType, interfaceType)
      }
    }
  }

  private[parser] def topLevelThing: Parser[ITopLevelThing] = {
    struct ^^ TopLevelStruct |
    topLevelFunction ^^ TopLevelFunction |
    interface ^^ TopLevelInterface |
    impl ^^ TopLevelImpl
  }

  def program: Parser[Program0] = {
    optWhite ~> repsep(topLevelThing, optWhite) <~ optWhite ^^ Program0
  }

  def repeatStr(str: String, n: Int): String = {
    var result = "";
    (0 until n).foreach(i => {
      result = result + str
    })
    result
  }

  def runParser(codeWithComments: String): ParseResult[(Program0, List[(Int, Int)])] = {
    val regex = "//[^\\r\\n]*".r
    val commentRanges = regex.findAllMatchIn(codeWithComments).map(mat => (mat.start, mat.end)).toList
    var code = codeWithComments
    commentRanges.foreach({ case (begin, end) =>
      code = code.substring(0, begin) + repeatStr(" ", (end - begin)) + code.substring(end)
    })

    VParser.parse(VParser.program, code.toCharArray) match {
      case VParser.NoSuccess(msg, next) => VParser.Failure(msg, next)
      case VParser.Success(program0, rest) => {
        if (!rest.atEnd) {
          vfail("Unexpected at: " + rest.source)
        } else {
          vassert(rest.offset == code.length)
          VParser.Success((program0, commentRanges), rest)
        }
      }
    }
  }
}
