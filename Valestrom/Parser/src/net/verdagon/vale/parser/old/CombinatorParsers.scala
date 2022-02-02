package net.verdagon.vale.parser.old

import net.verdagon.vale.parser.ast.{AbstractAttributeP, BlockPE, BuiltinAttributeP, CallMacro, DontCallMacro, ExportAsP, ExportAttributeP, ExportP, ExternAttributeP, FinalP, FunctionHeaderP, FunctionP, FunctionReturnP, ICitizenAttributeP, IFunctionAttributeP, IStructContent, ImplP, ImportP, InterfaceP, MacroCallP, MutabilityPT, MutableP, NormalStructMemberP, PureAttributeP, SealedP, StructMembersP, StructMethodP, StructP, VariadicStructMemberP, VaryingP, VoidPE, WeakableP}
import net.verdagon.vale.parser.patterns.PatternParser
import net.verdagon.vale.parser.rules.{RuleParser, RuleTemplexParser}
import net.verdagon.vale.parser.{ast, _}
import net.verdagon.vale.vassert

import scala.util.parsing.combinator.RegexParsers

object CombinatorParsers
  extends RuleParser
    with RuleTemplexParser
    with RegexParsers
    with ParserUtils
    with TemplexParser
    with PatternParser
    with ExpressionParser {
  override def skipWhitespace = false
  //override val whiteSpace = "[ \t\r\f]+".r

  def functionAttribute: Parser[IFunctionAttributeP] = {
    pos ~ "abstract" ~ pos ^^ { case begin ~ _ ~ end => AbstractAttributeP(ast.RangeP(begin, end)) } |
      pos ~ ("extern(" ~> exprIdentifier <~ ")") ~ pos ^^ { case begin ~ generatorName ~ end => BuiltinAttributeP(ast.RangeP(begin, end), generatorName) } |
      pos ~ "extern" ~ pos ^^ { case begin ~ _ ~ end => ExternAttributeP(ast.RangeP(begin, end)) } |
      pos ~ "export" ~ pos ^^ { case begin ~ _ ~ end => ExportAttributeP(ast.RangeP(begin, end)) } |
      pos ~ "pure" ~ pos ^^ { case begin ~ _ ~ end => PureAttributeP(ast.RangeP(begin, end)) }
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
          ast.RangeP(begin, end),
          Some(name),
          (attributesBeforeReturn ++ attributesBeforeBody).toVector,
          identifyingRunes,
          (maybeTemplateRulesBeforeReturn.toVector ++ maybeTemplateRulesAfterReturn.toVector).headOption,
          Some(patternParams),
          FunctionReturnP(ast.RangeP(retBegin, retEnd), maybeInferRet, maybeRetType))
      }
    }
  }

//  def topLevelFunction: Parser[FunctionP] = {
//    pos ~
//      topLevelFunctionBegin ~
//      maybeBody ~
//      pos ^^ {
//      case begin ~ header ~ maybeBody ~ end => {
//        ast.FunctionP(ast.RangeP(begin, end), header, maybeBody)
//      }
//    }
//  }

  def normalStructMember: Parser[NormalStructMemberP] = {
    pos ~ (exprIdentifier ~ opt("!") <~ optWhite) ~ (templex <~ optWhite <~ ";") ~ pos ^^ {
      case begin ~ (name ~ None) ~ tyype ~ end => ast.NormalStructMemberP(ast.RangeP(begin, end), name, FinalP, tyype)
      case begin ~ (name ~ Some(_)) ~ tyype ~ end => ast.NormalStructMemberP(ast.RangeP(begin, end), name, VaryingP, tyype)
    }
  }

  def variadicStructMember: Parser[VariadicStructMemberP] = {
    pos ~ ("_" ~> opt("!") <~ white) ~ ("..." ~> optWhite ~> templex <~ optWhite <~ ";") ~ pos ^^ {
      case begin ~ (None) ~ tyype ~ end => ast.VariadicStructMemberP(ast.RangeP(begin, end), FinalP, tyype)
      case begin ~ (Some(_)) ~ tyype ~ end => ast.VariadicStructMemberP(ast.RangeP(begin, end), VaryingP, tyype)
    }
  }

//  def structContent: Parser[IStructContent] = {
//    variadicStructMember | normalStructMember | (topLevelFunction ^^ StructMethodP)
//  }

  def citizenAttribute: Parser[ICitizenAttributeP] = {
    pos ~ "export" ~ pos ^^ { case begin ~ _ ~ end => ExportP(ast.RangeP(begin, end)) } |
      pos ~ "weakable" ~ pos ^^ { case begin ~ _ ~ end => WeakableP(ast.RangeP(begin, end)) } |
      pos ~ "sealed" ~ pos ^^ { case begin ~ _ ~ end => SealedP(ast.RangeP(begin, end)) } |
      pos ~ ("#" ~> opt("!")) ~ typeIdentifier ~ pos ^^ {
        case begin ~ dontCall ~ name ~ end => {
          MacroCallP(ast.RangeP(begin, end), if (dontCall.isEmpty) CallMacro else DontCallMacro, name)
        }
      }
  }

//  private[parser] def interface: Parser[InterfaceP] = {
//
//  }

//  def struct: Parser[StructP] = {
//  }

  private[parser] def impl: Parser[ImplP] = {
    pos ~ (("impl" ~> optWhite ~>
      opt(identifyingRunesPR <~ optWhite) ~
      opt(templateRulesPR) <~ optWhite) ~
      (templex <~ optWhite <~ "for" <~ optWhite) ~
      (templex <~ optWhite <~ ";")) ~ pos ^^ {
      case begin ~ (maybeIdentifyingRunes ~ maybeTemplateRules ~ interfaceType ~ structType) ~ end => {
        ast.ImplP(ast.RangeP(begin, end), maybeIdentifyingRunes, maybeTemplateRules, structType, interfaceType)
      }
    }
  }

  private[parser] def export: Parser[ExportAsP] = {
    pos ~ ("export" ~> white ~>
      (templex <~ white <~ "as" <~ white) ~
      (exprIdentifier <~ optWhite <~ ";")) ~ pos ^^ {
      case begin ~ (tyype ~ name) ~ end => {
        ast.ExportAsP(ast.RangeP(begin, end), tyype, name)
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
        ast.ImportP(ast.RangeP(begin, end), moduleName, packageSteps.toVector, importee)
      }
    }
  }
}
