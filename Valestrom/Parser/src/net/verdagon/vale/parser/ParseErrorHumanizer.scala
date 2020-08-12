package net.verdagon.vale.parser

import net.verdagon.vale.SourceCodeUtils.humanizePos
import net.verdagon.vale.SourceCodeUtils.nextThingAndRestOfLine

object ParseErrorHumanizer {
  def humanize(
      filenamesAndSources: List[(String, String)],
      file: Int,
      err: IParseError):
  String = {
    err match {
      case CombinatorParseError(pos, msg) => humanizePos(filenamesAndSources, file, pos) + ": " + msg + " when trying to parse:\n" + nextThingAndRestOfLine(filenamesAndSources, file, pos) + "\n"
      case UnrecognizedTopLevelThingError(pos) => humanizePos(filenamesAndSources, file, pos) + ": expected fn, struct, interface, or impl, but found:\n"  + nextThingAndRestOfLine(filenamesAndSources, file, pos) + "\n"
      case BadFunctionBodyError(pos) => humanizePos(filenamesAndSources, file, pos) + ": expected a function body, or `;` to note there is none. Found:\n" + nextThingAndRestOfLine(filenamesAndSources, file, pos) + "\n"
      case BadStartOfStatementError(pos) => humanizePos(filenamesAndSources, file, pos) + ": expected `}` to end the block, but found:\n" + nextThingAndRestOfLine(filenamesAndSources, file, pos) + "\n"
      case StatementAfterResult(pos) => humanizePos(filenamesAndSources, file, pos) + ": result statement must be last in the block, but instead found:\n" + nextThingAndRestOfLine(filenamesAndSources, file, pos) + "\n"
      case StatementAfterReturn(pos) => humanizePos(filenamesAndSources, file, pos) + ": return statement must be last in the block, but instead found:\n" + nextThingAndRestOfLine(filenamesAndSources, file, pos) + "\n"
      case BadExpressionEnd(pos) => humanizePos(filenamesAndSources, file, pos) + ": expected `;` or `}` after expression, but found:\n" + nextThingAndRestOfLine(filenamesAndSources, file, pos) + "\n"
    }
  }
}
