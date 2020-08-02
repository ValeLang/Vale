package net.verdagon.vale.parser

class ParseErrorHumanizer(text: String) {

  def lineAndCol(pos: Int) = {
    var line = 0;
    var col = 0;
    var i = 0
    while (i <= pos) {
      if (text(i) == '\n') {
        line = line + 1;
        col = 0;
      } else {
        col = col + 1;
      }
      i = i + 1;
    }
    (line + 1) + ":" + (col + 1)
  }

  def nextThingAndRestOfLine(position: Int) = {
    // TODO: can optimize this
    text.slice(position, text.length).trim().split("\\n")(0).trim()
  }

  def humanize(err: IParseError): String = {
    err match {
      case CombinatorParseError(pos, msg) => lineAndCol(pos) + ": " + msg + " when trying to parse:\n" + nextThingAndRestOfLine(pos) + "\n"
      case UnrecognizedTopLevelThingError(pos) => lineAndCol(pos) + ": expected fn, struct, interface, or impl, but found:\n"  + nextThingAndRestOfLine(pos) + "\n"
      case BadFunctionBodyError(pos) => lineAndCol(pos) + ": expected a function body, or `;` to note there is none. Found:\n" + nextThingAndRestOfLine(pos) + "\n"
      case BadStartOfStatementError(pos) => lineAndCol(pos) + ": expected `}` to end the block, but found:\n" + nextThingAndRestOfLine(pos) + "\n"
      case StatementAfterResult(pos) => lineAndCol(pos) + ": result statement must be last in the block, but instead found:\n" + nextThingAndRestOfLine(pos) + "\n"
      case StatementAfterReturn(pos) => lineAndCol(pos) + ": return statement must be last in the block, but instead found:\n" + nextThingAndRestOfLine(pos) + "\n"
      case BadExpressionEnd(pos) => lineAndCol(pos) + ": expected `;` or `}` after expression, but found:\n" + nextThingAndRestOfLine(pos) + "\n"
    }
  }
}
