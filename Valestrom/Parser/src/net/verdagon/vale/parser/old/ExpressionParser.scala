package net.verdagon.vale.parser.old

import net.verdagon.vale.parser.ast.{AndPE, BlockPE, ConsecutorPE, ConstantIntPE, ConstantStrPE, ConstructArrayPE, DestructPE, DotPE, EachPE, FunctionCallPE, FunctionHeaderP, FunctionP, FunctionReturnP, IArraySizeP, IExpressionPE, ITemplexPT, IfPE, IndexPE, LambdaPE, LetPE, LoadAsBorrowOrIfContainerIsPointerThenPointerP, LoadAsBorrowP, LoadAsPointerP, LoadAsWeakP, AugmentPE, LookupNameP, LookupPE, MagicParamLookupPE, MethodCallPE, MutatePE, NameP, OrPE, PackPE, ParamsP, PatternPP, RangeP, ReadonlyP, ReadwriteP, ReturnPE, RuntimeSizedP, ShortcallPE, StaticSizedP, StrInterpolatePE, TemplateArgsP, TemplateRulesP, TuplePE, UseP, VoidPE, WhilePE}
import net.verdagon.vale.parser.ast
import net.verdagon.vale.vcurious

import scala.util.parsing.combinator.RegexParsers

trait ExpressionParser extends RegexParsers with ParserUtils with TemplexParser {

  private[parser] def atomPattern: Parser[PatternPP]
  //  private[old] def templex: Parser[ITemplexPT]

  private[old] def comparisonOperators: Parser[NameP] = {
    (pstr("<=>") | pstr("<=") | pstr("<") | pstr(">=") | pstr(">") | pstr("===") | pstr("==") | pstr("!="))
  }

  private[old] def patternPrototypeParam: Parser[PatternPP] = {
    atomPattern ^^ {
      case pattern => pattern
    }
  }

  private[parser] def patternPrototypeParams: Parser[ParamsP] = {
    pos ~ ("(" ~> optWhite ~> repsep(optWhite ~> patternPrototypeParam, optWhite ~> ",") <~ optWhite <~ ")") ~ pos ^^ {
      case begin ~ params ~ end => ast.ParamsP(RangeP(begin, end), params.toVector)
    }
  }
}
