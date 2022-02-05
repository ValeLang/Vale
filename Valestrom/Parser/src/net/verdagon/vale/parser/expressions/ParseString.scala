package net.verdagon.vale.parser.expressions

import net.verdagon.vale.parser.ExpressionParser.StopBeforeCloseBrace
import net.verdagon.vale.{Err, Ok, Result, vimpl}
import net.verdagon.vale.parser.{BadStringInterpolationEnd, BadUnicodeChar, ExpressionParser, IParseError, Parser, ParsingIterator, ast}
import net.verdagon.vale.parser.ast.{ConstantStrPE, IExpressionPE, RangeP, StrInterpolatePE}

import scala.collection.mutable

object ParseString {
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
      parseStringPart(iter) match {
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

  def parseFourDigitHexNum(iter: ParsingIterator): Option[Int] = {
    iter.tryy("^([0-9a-fA-F]{4})".r).map(Integer.parseInt(_, 16))
  }

  sealed trait StringPart
  case class StringPartChar(c: Char) extends StringPart
  case class StringPartExpr(expr: IExpressionPE) extends StringPart
  def parseStringPart(iter: ParsingIterator): Result[StringPart, IParseError] = {
    if (iter.trySkip("^\\{".r)) {
      (ExpressionParser.parseExpression(iter, StopBeforeCloseBrace) match {
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
        Ok(StringPartChar('{'))
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
      Ok(StringPartChar(iter.tryy("^.".r).get.charAt(0)))
    }
  }

}