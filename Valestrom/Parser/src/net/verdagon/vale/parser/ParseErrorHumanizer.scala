package net.verdagon.vale.parser

import net.verdagon.vale.{CodeLocationS, FileCoordinate, FileCoordinateMap}
import net.verdagon.vale.SourceCodeUtils.{humanizeFile, humanizePos, nextThingAndRestOfLine}

object ParseErrorHumanizer {
  def humanize(
    fileMap: FileCoordinateMap[String],
    fileCoord: FileCoordinate,
    err: IParseError):
  String = {
    humanize(humanizeFile(fileCoord), fileMap(fileCoord), err)
  }

  def humanize(
      humanizedFilePath: String,
      code: String,
      err: IParseError):
  String = {
    val errorStrBody =
      err match {
        case RangedInternalErrorP(pos, msg) => "Internal error: " + msg
        case UnrecognizableExpressionAfterAugment(pos) => "Unrecognizable expression: "
        case BadMemberEnd(pos) => "Bad member end."
        case OnlyRegionRunesCanHaveMutability(pos) => "Only region runes, such as 'x, can have ro or rw or mut."
        case BadRuleCallParam(pos) => "Bad rule call param."
        case BadFunctionAfterParam(pos) => "Bad end of param."
        case BadRuneTypeError(pos) => "Bad rune type."
        case BadTemplateCallParam(pos) => "Bad template call param."
        case BadDestructureError(pos) => "Bad destructure."
        case BadTypeExpression(pos) => "Bad type expression."
        case BadLocalName(pos) => "Bad local name."
        case BadStringInterpolationEnd(pos) => "Bad string interpolation end."
        case CantUseBreakInExpression(pos) => "Can't use break inside an expression."
        case CantUseReturnInExpression(pos) => "Can't use return inside an expression."
        case BadInterfaceHeader(pos) => "Bad interface header."
        case BadStartOfBlock(pos) => "Bad start of block."
        case DontNeedSemicolon(pos) => "Dont need semicolon."
        case BadDot(pos) => "Bad dot."
        case BadImplFor(pos) => "Bad impl, expected `for`"
        case BadForeachInError(pos) => "Bad foreach, expected `in`."
        case BadAttributeError(pos) => "Bad attribute."
        case BadStructContentsBegin(pos) => "Bad start of struct contents."
        case BadInterfaceMember(pos) => "Bad interface member."
        case BadStringChar(stringBeginPos, pos) => "Bad string character, " + humanizePos(humanizedFilePath, code, stringBeginPos) + "-" + humanizePos(humanizedFilePath, code, pos)
        case BadExpressionBegin(pos) => "Bad start of expression."
        case NeedWhitespaceAroundBinaryOperator(pos) => "Need whitespace around binary operator."
        case UnknownTupleOrSubExpression(pos) => "Saw ( but expression is neither tuple nor sub-expression."
        case NeedSemicolon(pos) => "Need semicolon."
        case BadStructMember(pos) => "Bad struct member."
        case BadBinaryFunctionName(pos) => "Bad binary function name."
//        case CombinatorParseError(pos, msg) => "Internal parser error: " + msg + ":\n"
        case UnrecognizedTopLevelThingError(pos) => "expected func, struct, interface, impl, import, or export, but found:\n"
        case BadFunctionBodyError(pos) => "expected a function body, or `;` to note there is none. Found:\n"
        case BadStartOfStatementError(pos) => "expected `}` to end the block, but found:\n"
        case BadExpressionEnd(pos) => "expected `;` or `}` after expression, but found:\n"
        case IfBlocksMustBothOrNeitherReturn(pos) => "If blocks should either both return, or neither return."
        case ForgotSetKeyword(pos) => "Need `set` keyword to mutate a variable that already exists."
//        case BadImport(pos, cause) => "bad import:\n" + cause.toString + "\n"
        case BadStartOfWhileCondition(pos) => "Bad start of while condition, expected ("
        case BadEndOfWhileCondition(pos) => "Bad end of while condition, expected )"
        case BadStartOfWhileBody(pos) => "Bad start of while body, expected {"
        case BadEndOfWhileBody(pos) => "Bad end of while body, expected }"
        case BadStartOfIfCondition(pos) => "Bad start of if condition, expected ("
        case BadEndOfIfCondition(pos) => "Bad end of if condition, expected )"
        case BadStartOfIfBody(pos) => "Bad start of if body, expected {"
        case BadEndOfIfBody(pos) => "Bad end of if body, expected }"
        case BadStartOfElseBody(pos) => "Bad start of else body, expected {"
        case BadEndOfElseBody(pos) => "Bad end of else body, expected }"
        case BadLetEqualsError(pos) => "Expected = after declarations"
        case BadMutateEqualsError(pos) => "Expected = after set destination"
        case BadLetEndError(pos) => "Expected ; after declarations source"
        case BadArraySizerEnd(pos) => "Bad array sizer; expected ]"
        case BadLetSourceError(pos, cause) => "Parse error somewhere inside this let source expression. Imprecise inner error: " + humanize(humanizedFilePath, code, cause)
        case other => "Internal error: " + other.toString
      }
    val posStr = humanizePos(humanizedFilePath, code, err.pos)
    val nextStuff = nextThingAndRestOfLine(code, err.pos)
    f"${posStr} error ${err.errorId}: ${errorStrBody}\n${nextStuff}\n"
  }
}
