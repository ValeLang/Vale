package net.verdagon.vale.parser

import net.verdagon.vale.parser.patterns.PatternParser
import net.verdagon.vale.parser.rules._
import net.verdagon.vale.{vassert, vfail}

import scala.collection.mutable
import scala.util.parsing.combinator.RegexParsers
import scala.util.parsing.input.CharSequenceReader

// Rules of thumb:
// - A rule shouldn't match the empty string.
// - A rule shouldn't match whitespace on either side (it makes it hard to require
//   whitespace, like inside the let statement).

object CombinatorParsers
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

  def filledBody: Parser[BlockPE] = {
    // A hack to do region highlighting
    opt("'" ~> optWhite ~> exprIdentifier <~ optWhite) ~>
      bracedBlock
  }

  def emptyBody: Parser[BlockPE] = {
    // A hack to do region highlighting
    pos ~ opt("'" ~> optWhite ~> exprIdentifier <~ optWhite) ~
    ("{" ~> optWhite ~> pos <~ optWhite <~ "}") ~ pos ^^ {
      case begin ~ maybeRegion ~ middle ~ end => BlockPE(Range(begin, end), List(VoidPE(Range(middle, middle))))
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

  def functionAttribute: Parser[IFunctionAttributeP] = {
    pos ~ "abstract" ~ pos ^^ { case begin ~ _ ~ end => AbstractAttributeP(Range(begin, end)) } |
    pos ~ "extern" ~ pos ^^ { case begin ~ _ ~ end => ExternAttributeP(Range(begin, end)) } |
    pos ~ "export" ~ pos ^^ { case begin ~ _ ~ end => ExportAttributeP(Range(begin, end)) } |
    pos ~ "pure" ~ pos ^^ { case begin ~ _ ~ end => PureAttributeP(Range(begin, end)) }
  }

  def topLevelFunctionBegin = {
    pos ~
      ("fn" ~> optWhite ~> (comparisonOperators | exprIdentifier) <~ optWhite) ~
      opt(identifyingRunesPR <~ optWhite) ~
      (patternPrototypeParams <~ optWhite) ~
      // We have template rules before and after the return type because the return type likes
      // to parse the `rules` in `rules(stuff here)` as a type and then fail when it hits the
      // parentheses.
      opt(templateRulesPR <~ optWhite) ~
      (repsep(functionAttribute, white) <~ optWhite) ~
      opt(templex <~ optWhite) ~
      opt(templateRulesPR <~ optWhite) ~
      (repsep(functionAttribute, white) <~ optWhite) ~
    pos ^^ {
      case begin ~ name ~ identifyingRunes ~ patternParams ~ maybeTemplateRulesBeforeReturn ~ attributesBeforeReturn ~ maybeReturnType ~ maybeTemplateRulesAfterReturn ~ attributesBeforeBody ~ end => {
        vassert(!(maybeTemplateRulesBeforeReturn.nonEmpty && maybeTemplateRulesAfterReturn.nonEmpty))
        FunctionHeaderP(
          Range(begin, end),
          Some(name),
          attributesBeforeReturn ++ attributesBeforeBody,
          identifyingRunes,
          (maybeTemplateRulesBeforeReturn.toList ++ maybeTemplateRulesAfterReturn.toList).headOption,
          Some(patternParams),
          maybeReturnType)
      }
    }
  }

  def topLevelFunction: Parser[FunctionP] = {
        pos ~
        topLevelFunctionBegin ~
        maybeBody ~
        pos ^^ {
          case begin ~ header ~ maybeBody ~ end => {
        FunctionP(Range(begin, end), header, maybeBody)
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
        (repsep(citizenAttribute, white) <~ optWhite) ~
        (opt("imm") <~ optWhite) ~
        (opt(templateRulesPR) <~ optWhite) ~
        // A hack to do region highlighting
        (opt("'" ~> optWhite ~> exprIdentifier <~ optWhite) <~ "{" <~ optWhite) ~
        repsep(topLevelFunction, optWhite) ~
        (optWhite ~> "}" ~> pos) ^^ {
      case begin ~ name ~ maybeIdentifyingRunes ~ seealed ~ imm ~ maybeTemplateRules ~ defaultRegion ~ members ~ end => {
        val mutability = if (imm == Some("imm")) ImmutableP else MutableP
        InterfaceP(Range(begin, end), name, seealed, mutability, maybeIdentifyingRunes, maybeTemplateRules, members)
      }
    }
  }

  def citizenAttribute: Parser[ICitizenAttributeP] = {
    pos ~ "export" ~ pos ^^ { case begin ~ _ ~ end => ExportP(Range(begin, end)) } |
    pos ~ "weakable" ~ pos ^^ { case begin ~ _ ~ end => WeakableP(Range(begin, end)) } |
    pos ~ "sealed" ~ pos ^^ { case begin ~ _ ~ end => SealedP(Range(begin, end)) }
  }

  def struct: Parser[StructP] = {
    pos ~ ("struct" ~> optWhite ~> exprIdentifier <~ optWhite) ~
        opt(identifyingRunesPR <~ optWhite) ~
        (repsep(citizenAttribute, white) <~ optWhite) ~
        (opt("imm") <~ optWhite) ~
        (opt(templateRulesPR) <~ optWhite) ~
        // A hack to do region highlighting
        opt("'" ~> optWhite ~> exprIdentifier <~ optWhite) ~
        (pos <~ "{" <~ optWhite) ~
        ("..." <~ optWhite ^^^ List() | repsep(structContent, optWhite)) ~
        (optWhite ~> "}" ~> pos) ^^ {
      case begin ~ name ~ identifyingRunes ~ attributes ~ imm ~ maybeTemplateRules ~ defaultRegion ~ membersBegin ~ members ~ end => {
        val mutability = if (imm == Some("imm")) ImmutableP else MutableP
        StructP(Range(begin, end), name, attributes, mutability, identifyingRunes, maybeTemplateRules, StructMembersP(Range(membersBegin, end), members))
      }
    }
  }

  private[parser] def impl: Parser[ImplP] = {
    pos ~ (("impl" ~> optWhite ~>
      opt(identifyingRunesPR <~ optWhite) ~
      opt(templateRulesPR) <~ optWhite) ~
      (templex <~ optWhite <~ "for" <~ optWhite) ~
      (templex <~ optWhite <~ ";")) ~ pos ^^ {
      case begin ~ (maybeIdentifyingRunes ~ maybeTemplateRules ~ interfaceType ~ structType) ~ end => {
        ImplP(Range(begin, end), maybeIdentifyingRunes, maybeTemplateRules, structType, interfaceType)
      }
    }
  }

//  private[parser] def topLevelThing: Parser[ITopLevelThing] = {
//    struct ^^ TopLevelStruct |
//    topLevelFunction ^^ TopLevelFunction |
//    interface ^^ TopLevelInterface |
//    impl ^^ TopLevelImpl
//  }
//
//  def program: Parser[Program0] = {
//    optWhite ~> repsep(topLevelThing, optWhite) <~ optWhite ^^ Program0
//  }

  def repeatStr(str: String, n: Int): String = {
    var result = "";
    (0 until n).foreach(i => {
      result = result + str
    })
    result
  }

//  def runOldParser(codeWithComments: String): ParseResult[(Program0, List[(Int, Int)])] = {
//    val regex = "(//[^\\r\\n]*|«\\w+»)".r
//    val commentRanges = regex.findAllMatchIn(codeWithComments).map(mat => (mat.start, mat.end)).toList
//    var code = codeWithComments
//    commentRanges.foreach({ case (begin, end) =>
//      code = code.substring(0, begin) + repeatStr(" ", (end - begin)) + code.substring(end)
//    })
//
//    VParser.parse(VParser.program, code.toCharArray) match {
//      case VParser.NoSuccess(msg, next) => VParser.Failure(msg, next)
//      case VParser.Success(program0, rest) => {
//        if (!rest.atEnd) {
//          vfail("Unexpected at: " + rest.source)
//        } else {
//          vassert(rest.offset == code.length)
//          VParser.Success((program0, commentRanges), rest)
//        }
//      }
//    }
//  }
}
