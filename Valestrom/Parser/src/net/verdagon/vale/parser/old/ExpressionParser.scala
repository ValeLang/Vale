package net.verdagon.vale.parser.old

import net.verdagon.vale.parser.ast._
import net.verdagon.vale.parser.ast
import net.verdagon.vale.vcurious

import scala.util.parsing.combinator.RegexParsers

trait ExpressionParser extends RegexParsers with ParserUtils with TemplexParser {

//  private[parser] def atomPattern: Parser[PatternPP]
  //  private[old] def templex: Parser[ITemplexPT]

  private[old] def comparisonOperators: Parser[NameP] = {
    (pstr("<=>") | pstr("<=") | pstr("<") | pstr(">=") | pstr(">") | pstr("===") | pstr("==") | pstr("!="))
  }

//  private[old] def patternPrototypeParam: Parser[PatternPP] = {
//    atomPattern ^^ {
//      case pattern => pattern
//    }
//  }

//  private[parser] def patternPrototypeParams: Parser[ParamsP] = {
//    pos ~ ("(" ~> optWhite ~> repsep(optWhite ~> patternPrototypeParam, optWhite ~> ",") <~ optWhite <~ ")") ~ pos ^^ {
//      case begin ~ params ~ end => ast.ParamsP(RangeP(begin, end), params.toVector)
//    }
//  }
}
