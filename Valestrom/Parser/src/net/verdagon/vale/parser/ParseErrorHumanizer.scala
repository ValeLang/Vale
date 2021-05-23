package net.verdagon.vale.parser

import net.verdagon.vale.{FileCoordinate, FileCoordinateMap}
import net.verdagon.vale.SourceCodeUtils.humanizePos
import net.verdagon.vale.SourceCodeUtils.nextThingAndRestOfLine

object ParseErrorHumanizer {
  def humanize(
      fileMap: FileCoordinateMap[String],
      fileCoord: FileCoordinate,
      err: IParseError):
  String = {
    err match {
      case CombinatorParseError(pos, msg) => humanizePos(fileMap, fileCoord, pos) + ": Internal parser error: " + msg + ":\n"  + nextThingAndRestOfLine(fileMap, fileCoord, pos) + "\n"
      case UnrecognizedTopLevelThingError(pos) => humanizePos(fileMap, fileCoord, pos) + ": expected fn, struct, interface, impl, import, or export, but found:\n"  + nextThingAndRestOfLine(fileMap, fileCoord, pos) + "\n"
      case BadFunctionBodyError(pos) => humanizePos(fileMap, fileCoord, pos) + ": expected a function body, or `;` to note there is none. Found:\n" + nextThingAndRestOfLine(fileMap, fileCoord, pos) + "\n"
      case BadStartOfStatementError(pos) => humanizePos(fileMap, fileCoord, pos) + ": expected `}` to end the block, but found:\n" + nextThingAndRestOfLine(fileMap, fileCoord, pos) + "\n"
      case StatementAfterResult(pos) => humanizePos(fileMap, fileCoord, pos) + ": result statement must be last in the block, but instead found:\n" + nextThingAndRestOfLine(fileMap, fileCoord, pos) + "\n"
      case StatementAfterReturn(pos) => humanizePos(fileMap, fileCoord, pos) + ": return statement must be last in the block, but instead found:\n" + nextThingAndRestOfLine(fileMap, fileCoord, pos) + "\n"
      case BadExpressionEnd(pos) => humanizePos(fileMap, fileCoord, pos) + ": expected `;` or `}` after expression, but found:\n" + nextThingAndRestOfLine(fileMap, fileCoord, pos) + "\n"
      case BadImport(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": bad import:\n" + nextThingAndRestOfLine(fileMap, fileCoord, pos) + "\n" + cause.toString + "\n"
      case BadStartOfWhileCondition(pos) => humanizePos(fileMap, fileCoord, pos) + ": Bad start of while condition, expected ("
      case BadEndOfWhileCondition(pos) => humanizePos(fileMap, fileCoord, pos) + ": Bad end of while condition, expected )"
      case BadStartOfWhileBody(pos) => humanizePos(fileMap, fileCoord, pos) + ": Bad start of while body, expected {"
      case BadEndOfWhileBody(pos) => humanizePos(fileMap, fileCoord, pos) + ": Bad end of while body, expected }"
      case BadStartOfIfCondition(pos) => humanizePos(fileMap, fileCoord, pos) + ": Bad start of if condition, expected ("
      case BadEndOfIfCondition(pos) => humanizePos(fileMap, fileCoord, pos) + ": Bad end of if condition, expected )"
      case BadStartOfIfBody(pos) => humanizePos(fileMap, fileCoord, pos) + ": Bad start of if body, expected {"
      case BadEndOfIfBody(pos) => humanizePos(fileMap, fileCoord, pos) + ": Bad end of if body, expected }"
      case BadStartOfElseBody(pos) => humanizePos(fileMap, fileCoord, pos) + ": Bad start of else body, expected {"
      case BadEndOfElseBody(pos) => humanizePos(fileMap, fileCoord, pos) + ": Bad end of else body, expected }"
      case BadLetEqualsError(pos) => humanizePos(fileMap, fileCoord, pos) + ": Expected = after declarations"
      case BadMutateEqualsError(pos) => humanizePos(fileMap, fileCoord, pos) + ": Expected = after set destination"
      case BadLetEndError(pos) => humanizePos(fileMap, fileCoord, pos) + ": Expected ; after declarations source"
      case BadWhileCondition(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this while condition. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadWhileBody(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this while body. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadIfCondition(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this if condition. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadIfBody(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this if body. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadElseBody(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this else body. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadStruct(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this struct. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadInterface(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this interface. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadImpl(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this impl. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadFunctionHeaderError(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this function header. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadEachError(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this each/eachI. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadBlockError(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this block. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadResultError(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere in this result expression. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadReturnError(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this return expression. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadDestructError(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this destruct expression. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadStandaloneExpressionError(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this expression. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadMutDestinationError(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this set destination expression. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadMutSourceError(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this set source expression. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadLetDestinationError(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this let destination pattern. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
      case BadLetSourceError(pos, cause) => humanizePos(fileMap, fileCoord, pos) + ": Parse error somewhere inside this let source expression. Imprecise inner error: " + humanize(fileMap, fileCoord, cause)
    }
  }

  def humanizeCombinatorParseError(
    fileMap: FileCoordinateMap[String],
    fileCoord: FileCoordinate,
    cpe: CombinatorParseError) = {

    val CombinatorParseError(pos, msg) = cpe
    humanizePos(fileMap, fileCoord, pos) + ": " + msg + " when trying to parse:\n" + nextThingAndRestOfLine(fileMap, fileCoord, pos) + "\n"
  }
}
