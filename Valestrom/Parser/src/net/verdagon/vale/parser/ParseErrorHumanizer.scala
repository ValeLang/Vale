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

      case UnrecognizedTopLevelThingError(pos) => humanizePos(filenamesAndSources, file, pos) + ": expected fn, struct, interface, or impl, but found:\n"  + nextThingAndRestOfLine(filenamesAndSources, file, pos) + "\n"
      case BadFunctionBodyError(pos) => humanizePos(filenamesAndSources, file, pos) + ": expected a function body, or `;` to note there is none. Found:\n" + nextThingAndRestOfLine(filenamesAndSources, file, pos) + "\n"
      case BadStartOfStatementError(pos) => humanizePos(filenamesAndSources, file, pos) + ": expected `}` to end the block, but found:\n" + nextThingAndRestOfLine(filenamesAndSources, file, pos) + "\n"
      case StatementAfterResult(pos) => humanizePos(filenamesAndSources, file, pos) + ": result statement must be last in the block, but instead found:\n" + nextThingAndRestOfLine(filenamesAndSources, file, pos) + "\n"
      case StatementAfterReturn(pos) => humanizePos(filenamesAndSources, file, pos) + ": return statement must be last in the block, but instead found:\n" + nextThingAndRestOfLine(filenamesAndSources, file, pos) + "\n"
      case BadExpressionEnd(pos) => humanizePos(filenamesAndSources, file, pos) + ": expected `;` or `}` after expression, but found:\n" + nextThingAndRestOfLine(filenamesAndSources, file, pos) + "\n"
      case BadStartOfWhileCondition(pos) => humanizePos(filenamesAndSources, file, pos) + ": Bad start of while condition, expected ("
      case BadEndOfWhileCondition(pos) => humanizePos(filenamesAndSources, file, pos) + ": Bad end of while condition, expected )"
      case BadStartOfWhileBody(pos) => humanizePos(filenamesAndSources, file, pos) + ": Bad start of while body, expected {"
      case BadEndOfWhileBody(pos) => humanizePos(filenamesAndSources, file, pos) + ": Bad end of while body, expected }"
      case BadStartOfIfCondition(pos) => humanizePos(filenamesAndSources, file, pos) + ": Bad start of if condition, expected ("
      case BadEndOfIfCondition(pos) => humanizePos(filenamesAndSources, file, pos) + ": Bad end of if condition, expected )"
      case BadStartOfIfBody(pos) => humanizePos(filenamesAndSources, file, pos) + ": Bad start of if body, expected {"
      case BadEndOfIfBody(pos) => humanizePos(filenamesAndSources, file, pos) + ": Bad end of if body, expected }"
      case BadStartOfElseBody(pos) => humanizePos(filenamesAndSources, file, pos) + ": Bad start of else body, expected {"
      case BadEndOfElseBody(pos) => humanizePos(filenamesAndSources, file, pos) + ": Bad end of else body, expected }"
      case BadLetEqualsError(pos) => humanizePos(filenamesAndSources, file, pos) + ": Expected = after declarations"
      case BadMutateEqualsError(pos) => humanizePos(filenamesAndSources, file, pos) + ": Expected = after mut destination"
      case BadLetEndError(pos) => humanizePos(filenamesAndSources, file, pos) + ": Expected ; after declarations source"
      case BadWhileCondition(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this while condition. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadWhileBody(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this while body. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadIfCondition(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this if condition. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadIfBody(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this if body. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadElseBody(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this else body. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadStruct(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this struct. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadInterface(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this interface. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadImpl(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this impl. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadFunctionHeaderError(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this function header. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadEachError(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this each/eachI. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadBlockError(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this block. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadResultError(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere in this result expression. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadReturnError(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this return expression. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadDestructError(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this destruct expression. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadStandaloneExpressionError(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this expression. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadMutDestinationError(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this mut destination expression. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadMutSourceError(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this mut source expression. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadLetDestinationError(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this let destination pattern. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
      case BadLetSourceError(pos, cause) => humanizePos(filenamesAndSources, file, pos) + ": Parse error somewhere inside this let source expression. Imprecise inner error: " + humanize(filenamesAndSources, file, cause)
    }
  }

  def humanize(
    filenamesAndSources: List[(String, String)],
    file: Int,
    cpe: CombinatorParseError) = {

    val CombinatorParseError(pos, msg) = cpe
    humanizePos(filenamesAndSources, file, pos) + ": " + msg + " when trying to parse:\n" + nextThingAndRestOfLine(filenamesAndSources, file, pos) + "\n"
  }
}
