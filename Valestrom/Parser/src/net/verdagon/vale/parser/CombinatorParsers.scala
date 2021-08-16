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
  //override val whiteSpace = "[ \t\r\f]+".r

  def filledBody: Parser[BlockPE] = {
    // A hack to do region highlighting
    opt("'" ~> optWhite ~> exprIdentifier <~ optWhite) ~>
      bracedBlock
  }

  def emptyBody: Parser[BlockPE] = {
    // A hack to do region highlighting
    pos ~ opt("'" ~> optWhite ~> exprIdentifier <~ optWhite) ~
    ("{" ~> optWhite ~> pos <~ optWhite <~ "}") ~ pos ^^ {
      case begin ~ maybeRegion ~ middle ~ end => BlockPE(Range(begin, end), Vector(VoidPE(Range(middle, middle))))
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
    pos ~ ("extern(" ~> exprIdentifier <~ ")") ~ pos ^^ { case begin ~ generatorName ~ end => BuiltinAttributeP(Range(begin, end), generatorName) } |
    pos ~ "extern" ~ pos ^^ { case begin ~ _ ~ end => ExternAttributeP(Range(begin, end)) } |
    pos ~ "export" ~ pos ^^ { case begin ~ _ ~ end => ExportAttributeP(Range(begin, end)) } |
    pos ~ "pure" ~ pos ^^ { case begin ~ _ ~ end => PureAttributeP(Range(begin, end)) }
  }

  def topLevelFunctionBegin = {
    pos ~
      ("fn" ~> optWhite ~> (comparisonOperators | functionIdentifier) <~ optWhite) ~
      opt(identifyingRunesPR <~ optWhite) ~
      (patternPrototypeParams <~ optWhite) ~
      pos ~
      existsMW("infer-ret") ~
      // We have template rules before and after the return type because the return type likes
      // to parse the `rules` in `rules(stuff here)` as a type and then fail when it hits the
      // parentheses.
      opt(templateRulesPR <~ optWhite) ~
      (repsep(functionAttribute, white) <~ optWhite) ~
      opt(templex <~ optWhite) ~
      pos ~
      opt(templateRulesPR <~ optWhite) ~
      (repsep(functionAttribute, white) <~ optWhite) ~
    pos ^^ {
      case begin ~ name ~ identifyingRunes ~ patternParams ~ retBegin ~ maybeInferRet ~ maybeTemplateRulesBeforeReturn ~ attributesBeforeReturn ~ maybeRetType ~ retEnd ~ maybeTemplateRulesAfterReturn ~ attributesBeforeBody ~ end => {
        vassert(!(maybeTemplateRulesBeforeReturn.nonEmpty && maybeTemplateRulesAfterReturn.nonEmpty))
        FunctionHeaderP(
          Range(begin, end),
          Some(name),
          (attributesBeforeReturn ++ attributesBeforeBody).toVector,
          identifyingRunes,
          (maybeTemplateRulesBeforeReturn.toVector ++ maybeTemplateRulesAfterReturn.toVector).headOption,
          Some(patternParams),
          FunctionReturnP(Range(retBegin, retEnd), maybeInferRet, maybeRetType))
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
        InterfaceP(Range(begin, end), name, seealed.toVector, mutability, maybeIdentifyingRunes, maybeTemplateRules, members.toVector)
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
        ("..." <~ optWhite ^^^ Vector.empty | repsep(structContent, optWhite)) ~
        (optWhite ~> "}" ~> pos) ^^ {
      case begin ~ name ~ identifyingRunes ~ attributes ~ imm ~ maybeTemplateRules ~ defaultRegion ~ membersBegin ~ members ~ end => {
        val mutability = if (imm == Some("imm")) ImmutableP else MutableP
        StructP(Range(begin, end), name, attributes.toVector, mutability, identifyingRunes, maybeTemplateRules, StructMembersP(Range(membersBegin, end), members.toVector))
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

  private[parser] def export: Parser[ExportAsP] = {
    pos ~ ("export" ~> white ~>
      (templex <~ white <~ "as" <~ white) ~
      (exprIdentifier <~ optWhite <~ ";")) ~ pos ^^ {
      case begin ~ (tyype ~ name) ~ end => {
        ExportAsP(Range(begin, end), tyype, name)
      }
    }
  }

  private[parser] def `import`: Parser[ImportP] = {
    (pos <~ "import" <~ white) ~
      rep(exprIdentifier <~ optWhite <~ "." <~ optWhite) ~
      (exprIdentifier | pstr("*")) ~
      (optWhite ~> ";" ~> pos) ^^ {
      case begin ~ steps ~ importee ~ end => {
        val moduleName = steps.head
        val packageSteps = steps.tail
        ImportP(Range(begin, end), moduleName, packageSteps.toVector, importee)
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


//  def runOldParser(codeWithComments: String): ParseResult[(Program0, Vector[(Int, Int)])] = {
//    val regex = "(//[^\\r\\n]*|«\\w+»)".r
//    val commentRanges = regex.findAllMatchIn(codeWithComments).map(mat => (mat.start, mat.end)).toVector
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
