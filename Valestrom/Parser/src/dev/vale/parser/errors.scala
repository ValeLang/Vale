package dev.vale.parser

import dev.vale.parser.ast.ITopLevelThingP
import dev.vale.{FileCoordinate, FileCoordinateMap, vcurious, vpass}
import dev.vale.vpass

case class FailedParse(
  codeMapSoFar: FileCoordinateMap[String],
  fileCoord: FileCoordinate,
  error: IParseError,
) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

sealed trait IParseError {
  def pos: Int
  def errorId: String
}
case class RangedInternalErrorP(pos: Int, msg: String) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class UnrecognizableExpressionAfterAugment(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class OnlyRegionRunesCanHaveMutability(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadMemberEnd(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadLocalNameInUnlet(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class FoundBothAbstractAndOverride(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadPrototypeName(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadPrototypeParams(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadRuleCallParam(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadTypeExpression(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadTemplateCallParam(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadDestructureError(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ShareCantBeReadwrite(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadRuneEnd(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadRegionName(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadRuneNameError(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class RegionRuneHasType(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadRuneTypeError(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class UnrecognizedTopLevelThingError(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadStartOfStatementError(pos: Int) extends IParseError { override def errorId: String = "P1002"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadExpressionEnd(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadRule(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class UnexpectedAttributes(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class UnexpectedTopLevelThing(pos: Int, topLevelThing: ITopLevelThingP) extends IParseError { override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class IfBlocksMustBothOrNeitherReturn(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadExpressionBegin(pos: Int) extends IParseError {
  override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class BadStringChar(stringBeginPos: Int, pos: Int) extends IParseError { override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadFunctionName(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadParamEnd(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ForgotSetKeyword(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadBinaryFunctionName(pos: Int) extends IParseError {
  override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class BadTemplateCallee(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class UnknownTupleOrSubExpression(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class BadRangeOperand(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantUseBreakInExpression(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantUseReturnInExpression(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class NeedSemicolon(pos: Int) extends IParseError {
  override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class DontNeedSemicolon(pos: Int) extends IParseError {
  override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}

case class BadDot(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantTemplateCallMember(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class NeedWhitespaceAroundBinaryOperator(pos: Int) extends IParseError {
  override def errorId: String = "P1006"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class BadFunctionBodyError(pos: Int) extends IParseError { override def errorId: String = "P1006"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class BadUnicodeChar(pos: Int) extends IParseError { override def errorId: String = "P1009"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadStringInterpolationEnd(pos: Int) extends IParseError { override def errorId: String = "P1009"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class BadStartOfBlock(pos: Int) extends IParseError { override def errorId: String = "P1009"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadEndOfBlock(pos: Int) extends IParseError { override def errorId: String = "P1009"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class BadStartOfWhileCondition(pos: Int) extends IParseError { override def errorId: String = "P1007"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadEndOfWhileCondition(pos: Int) extends IParseError { override def errorId: String = "P1008"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadStartOfWhileBody(pos: Int) extends IParseError { override def errorId: String = "P1009"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadEndOfWhileBody(pos: Int) extends IParseError { override def errorId: String = "P1010"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class BadStartOfIfCondition(pos: Int) extends IParseError { override def errorId: String = "P1011"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadEndOfIfCondition(pos: Int) extends IParseError { override def errorId: String = "P1012"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadStartOfIfBody(pos: Int) extends IParseError { override def errorId: String = "P1013"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadEndOfIfBody(pos: Int) extends IParseError { override def errorId: String = "P1014"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadStartOfElseBody(pos: Int) extends IParseError { override def errorId: String = "P1015"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadEndOfElseBody(pos: Int) extends IParseError { override def errorId: String = "P1016"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class BadLetEqualsError(pos: Int) extends IParseError { override def errorId: String = "P1017"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadMutateEqualsError(pos: Int) extends IParseError { override def errorId: String = "P1018"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadLetEndError(pos: Int) extends IParseError { override def errorId: String = "P1019"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class BadVPSTException(err: BadVPSTError) extends RuntimeException {
//  println("VPST error:")
//  printStackTrace()
}
case class BadVPSTError(message: String) extends IParseError {
  override def pos = 0; override def errorId: String = "P1020"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}


// TODO: Get rid of all the below when we've migrated away from combinators.

case class BadArraySizerEnd(pos: Int) extends IParseError { override def errorId: String = "P1022"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadStructName(pos: Int) extends IParseError { override def errorId: String = "P1027"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadStructContentsBegin(pos: Int) extends IParseError { override def errorId: String = "P1027"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadStructContentsEnd(pos: Int) extends IParseError { override def errorId: String = "P1027"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadStructMember(pos: Int) extends IParseError { override def errorId: String = "P1027"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadStructMemberType(pos: Int) extends IParseError { override def errorId: String = "P1027"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class VariadicStructMemberHasName(pos: Int) extends IParseError { override def errorId: String = "P1027"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadInterfaceMember(pos: Int) extends IParseError { override def errorId: String = "P1027"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadInterfaceHeader(pos: Int) extends IParseError { override def errorId: String = "P1028"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadImpl(pos: Int) extends IParseError { override def errorId: String = "P1029"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadImplFor(pos: Int) extends IParseError { override def errorId: String = "P1029"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadExportAs(pos: Int) extends IParseError { override def errorId: String = "P1029"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadImportEnd(pos: Int) extends IParseError { override def errorId: String = "P1031"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadImportName(pos: Int) extends IParseError { override def errorId: String = "P1031"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadFunctionParamsBegin(pos: Int) extends IParseError { override def errorId: String = "P1032"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadFunctionAfterParam(pos: Int) extends IParseError { override def errorId: String = "P1032"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadAttributeError(pos: Int) extends IParseError { override def errorId: String = "P1032"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadExportName(pos: Int) extends IParseError { override def errorId: String = "P1037"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadExportEnd(pos: Int) extends IParseError { override def errorId: String = "P1037"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadArraySpecifier(pos: Int) extends IParseError { override def errorId: String = "P1039"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadLocalName(pos: Int) extends IParseError { override def errorId: String = "P1041"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BadLetSourceError(pos: Int, cause: IParseError) extends IParseError {
  override def errorId: String = "P1042"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class BadForeachInError(pos: Int) extends IParseError {
  override def errorId: String = "P1041"; override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
