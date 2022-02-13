package net.verdagon.vale.parser.expressions

import net.verdagon.vale.{Err, Ok, Result, vassert, vassertSome, vimpl}
import net.verdagon.vale.parser.{BadStringChar, BadStringInterpolationEnd, BadUnicodeChar, ExpressionParser, IParseError, Parser, ParsingIterator, StopBeforeCloseBrace, ast}
import net.verdagon.vale.parser.ast.{ConstantStrPE, IExpressionPE, RangeP, StrInterpolatePE}
import net.verdagon.vale.parser.expressions.StringParser.{StringPart, StringPartChar, StringPartExpr, parseFourDigitHexNum}

import scala.collection.mutable
import scala.util.matching.Regex

class StringParser(expressionParser: ExpressionParser) {
  def parseStringEnd(iter: ParsingIterator, isLongString: Boolean): Boolean = {
    iter.atEnd() || iter.trySkip(if (isLongString) "^\"\"\"".r else "^\"".r)
  }

  def parseString(iter: ParsingIterator): Result[Option[IExpressionPE], IParseError] = {
    val begin = iter.getPos()
    val isLongString =
      if (iter.trySkip("^\"\"\"".r)) {
        true
      } else if (iter.trySkip("^\"".r)) {
        false
      } else {
        return Ok(None)
      }

    val parts = new mutable.MutableList[IExpressionPE]()
    var stringSoFarBegin = iter.getPos()
    var stringSoFar = new StringBuilder()

    while (!parseStringEnd(iter, isLongString)) {
      parseStringPart(iter, begin) match {
        case Err(e) => return Err(e)
        case Ok(StringPartChar(c)) => {
          stringSoFar += c
        }
        case Ok(StringPartExpr(expr)) => {
          if (stringSoFar.nonEmpty) {
            parts += ConstantStrPE(RangeP(stringSoFarBegin, iter.getPos()), stringSoFar.toString())
            stringSoFar.clear()
          }
          parts += expr
          if (!iter.trySkip("^\\}".r)) {
            return Err(BadStringInterpolationEnd(iter.getPos()))
          }
          stringSoFarBegin = iter.getPos()
        }
      }
    }
    if (stringSoFar.nonEmpty) {
      parts += ConstantStrPE(RangeP(stringSoFarBegin, iter.getPos()), stringSoFar.toString())
      stringSoFar.clear()
    }
    if (parts.isEmpty) {
      Ok(Some(ConstantStrPE(RangeP(stringSoFarBegin, iter.getPos()), "")))
    } else if (parts.size == 1) {
      Ok(Some(parts.head))
    } else {
      Ok(Some(StrInterpolatePE(RangeP(begin, iter.getPos()), parts.toVector)))
    }
  }

  def parseStringPart(iter: ParsingIterator, stringBeginPos: Int): Result[StringPart, IParseError] = {
    // The newline is because we dont want to interpolate when its a { then a newline.
    // If they want that, then they should do {\
    if (iter.trySkip("^\\{\\\\".r) ||
      iter.trySkipIfPeekNext("^\\{".r, "^[^\\n]".r)) {
      iter.consumeWhitespace()
      (expressionParser.parseExpression(iter, StopBeforeCloseBrace) match {
        case Err(e) => return Err(e)
        case Ok(e) => Ok(StringPartExpr(e))
      })
    } else if (iter.trySkip("^\\\\".r)) {
      if (iter.trySkip("^r".r) || iter.trySkip("^\\r".r)) {
        Ok(StringPartChar('\r'))
      } else if (iter.trySkip("^t".r)) {
        Ok(StringPartChar('\t'))
      } else if (iter.trySkip("^n".r) || iter.trySkip("^\\n".r)) {
        Ok(StringPartChar('\n'))
      } else if (iter.trySkip("^\\\\".r)) {
        Ok(StringPartChar('\\'))
      } else if (iter.trySkip("^\"".r)) {
        Ok(StringPartChar('\"'))
      } else if (iter.trySkip("^/".r)) {
        Ok(StringPartChar('/'))
      } else if (iter.trySkip("^\\{".r)) {
        Ok(StringPartChar('{'))
      } else if (iter.trySkip("^\\}".r)) {
        Ok(StringPartChar('}'))
      } else if (iter.trySkip("^u".r)) {
        val num =
          parseFourDigitHexNum(iter) match {
            case None => {
              return Err(BadUnicodeChar(iter.getPos()))
            }
            case Some(x) => x
          }
        Ok(StringPartChar(num.toChar))
      } else {
        Ok(StringPartChar(iter.tryy("^.".r).get.charAt(0)))
      }
    } else {
      val c =
        iter.tryy("^(.|\\n)".r) match {
          case None => {
            return Err(BadStringChar(stringBeginPos, iter.getPos()))
          }
          case Some(x) => x
        }
      Ok(StringPartChar(c.charAt(0)))
    }
  }
}

object StringParser {

  sealed trait StringPart
  case class StringPartChar(c: Char) extends StringPart
  case class StringPartExpr(expr: IExpressionPE) extends StringPart

  def parseFourDigitHexNum(iter: ParsingIterator): Option[Int] = {
    iter.tryy("^([0-9a-fA-F]{4})".r).map(Integer.parseInt(_, 16))
  }
}