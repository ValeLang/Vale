package net.verdagon.vale.parser

import net.verdagon.vale.SourceCodeUtils.lineAndCol
import net.verdagon.vale.SourceCodeUtils.nextThingAndRestOfLine

object ParseErrorHumanizer {
  def humanize(text: String, err: IParseError): String = {
    err match {
      case CombinatorParseError(pos, msg) => lineAndCol(text, pos) + ": " + msg + " when trying to parse:\n" + nextThingAndRestOfLine(text, pos) + "\n"
      case UnrecognizedTopLevelThingError(pos) => lineAndCol(text, pos) + ": expected fn, struct, interface, or impl, but found:\n"  + nextThingAndRestOfLine(text, pos) + "\n"
      case BadFunctionBodyError(pos) => lineAndCol(text, pos) + ": expected a function body, or `;` to note there is none. Found:\n" + nextThingAndRestOfLine(text, pos) + "\n"
      case BadStartOfStatementError(pos) => lineAndCol(text, pos) + ": expected `}` to end the block, but found:\n" + nextThingAndRestOfLine(text, pos) + "\n"
      case StatementAfterResult(pos) => lineAndCol(text, pos) + ": result statement must be last in the block, but instead found:\n" + nextThingAndRestOfLine(text, pos) + "\n"
      case StatementAfterReturn(pos) => lineAndCol(text, pos) + ": return statement must be last in the block, but instead found:\n" + nextThingAndRestOfLine(text, pos) + "\n"
      case BadExpressionEnd(pos) => lineAndCol(text, pos) + ": expected `;` or `}` after expression, but found:\n" + nextThingAndRestOfLine(text, pos) + "\n"
    }
  }
}
