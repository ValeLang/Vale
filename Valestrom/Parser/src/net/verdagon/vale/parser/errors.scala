package net.verdagon.vale.parser

import net.verdagon.vale.{FileCoordinate, FileCoordinateMap, vcurious, vimpl, vpass}

case class FailedParse(
  codeMapSoFar: FileCoordinateMap[String],
  fileCoord: FileCoordinate,
  error: IParseError,
) { override def hashCode(): Int = vcurious() }

sealed trait IParseError {
  def pos: Int
  def errorId: String
}
case class UnrecognizedTopLevelThingError(pos: Int) extends IParseError { override def errorId: String = "P1001"; override def hashCode(): Int = vcurious() }
case class BadStartOfStatementError(pos: Int) extends IParseError { override def errorId: String = "P1002"; override def hashCode(): Int = vcurious() }
case class StatementAfterResult(pos: Int) extends IParseError { override def errorId: String = "P1003"; override def hashCode(): Int = vcurious() }
case class StatementAfterReturn(pos: Int) extends IParseError { override def errorId: String = "P1004"; override def hashCode(): Int = vcurious() }
case class BadExpressionEnd(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def hashCode(): Int = vcurious() }
case class BadExpressionBegin(pos: Int) extends IParseError {
  override def errorId: String = "P1005"; override def hashCode(): Int = vcurious()
  vpass()
}
case class BadParamEnd(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def hashCode(): Int = vcurious() }
case class BadBinaryFunctionName(pos: Int) extends IParseError {
  override def errorId: String = "P1005"; override def hashCode(): Int = vcurious()
  vpass()
}
case class BadTemplateCallee(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def hashCode(): Int = vcurious() }
case class UnknownTupleOrSubExpression(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def hashCode(): Int = vcurious() }

case class BadRangeOperand(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def hashCode(): Int = vcurious() }
case class NeedSemicolon(pos: Int) extends IParseError {
  override def errorId: String = "P1005"; override def hashCode(): Int = vcurious()
  vpass()
}
case class DontNeedSemicolon(pos: Int) extends IParseError {
  override def errorId: String = "P1005"; override def hashCode(): Int = vcurious()
  vpass()
}

case class BadDot(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def hashCode(): Int = vcurious() }
case class CantTemplateCallMember(pos: Int) extends IParseError { override def errorId: String = "P1005"; override def hashCode(): Int = vcurious() }

case class NeedWhitespaceAroundBinaryOperator(pos: Int) extends IParseError {
  override def errorId: String = "P1006"; override def hashCode(): Int = vcurious()
  vpass()
}
case class BadFunctionBodyError(pos: Int) extends IParseError { override def errorId: String = "P1006"; override def hashCode(): Int = vcurious() }

case class BadUnicodeChar(pos: Int) extends IParseError { override def errorId: String = "P1009"; override def hashCode(): Int = vcurious() }
case class BadStringInterpolationEnd(pos: Int) extends IParseError { override def errorId: String = "P1009"; override def hashCode(): Int = vcurious() }

case class BadStartOfBlock(pos: Int) extends IParseError { override def errorId: String = "P1009"; override def hashCode(): Int = vcurious() }
case class BadEndOfBlock(pos: Int) extends IParseError { override def errorId: String = "P1009"; override def hashCode(): Int = vcurious() }

case class BadStartOfWhileCondition(pos: Int) extends IParseError { override def errorId: String = "P1007"; override def hashCode(): Int = vcurious() }
case class BadEndOfWhileCondition(pos: Int) extends IParseError { override def errorId: String = "P1008"; override def hashCode(): Int = vcurious() }
case class BadStartOfWhileBody(pos: Int) extends IParseError { override def errorId: String = "P1009"; override def hashCode(): Int = vcurious() }
case class BadEndOfWhileBody(pos: Int) extends IParseError { override def errorId: String = "P1010"; override def hashCode(): Int = vcurious() }

case class BadStartOfIfCondition(pos: Int) extends IParseError { override def errorId: String = "P1011"; override def hashCode(): Int = vcurious() }
case class BadEndOfIfCondition(pos: Int) extends IParseError { override def errorId: String = "P1012"; override def hashCode(): Int = vcurious() }
case class BadStartOfIfBody(pos: Int) extends IParseError { override def errorId: String = "P1013"; override def hashCode(): Int = vcurious() }
case class BadEndOfIfBody(pos: Int) extends IParseError { override def errorId: String = "P1014"; override def hashCode(): Int = vcurious() }
case class BadStartOfElseBody(pos: Int) extends IParseError { override def errorId: String = "P1015"; override def hashCode(): Int = vcurious() }
case class BadEndOfElseBody(pos: Int) extends IParseError { override def errorId: String = "P1016"; override def hashCode(): Int = vcurious() }

case class BadLetEqualsError(pos: Int) extends IParseError { override def errorId: String = "P1017"; override def hashCode(): Int = vcurious() }
case class BadMutateEqualsError(pos: Int) extends IParseError { override def errorId: String = "P1018"; override def hashCode(): Int = vcurious() }
case class BadLetEndError(pos: Int) extends IParseError { override def errorId: String = "P1019"; override def hashCode(): Int = vcurious() }

case class BadVPSTException(err: BadVPSTError) extends RuntimeException {
//  println("VPST error:")
//  printStackTrace()
}
case class BadVPSTError(message: String) extends IParseError {
  override def pos = 0; override def errorId: String = "P1020"; override def hashCode(): Int = vcurious()
  vpass()
}


// TODO: Get rid of all the below when we've migrated away from combinators.

case class CombinatorParseError(pos: Int, msg: String) extends IParseError {
  override def errorId: String = "P1021"; override def hashCode(): Int = vcurious()
  vpass()
}
case class BadWhileCondition(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1022"; override def hashCode(): Int = vcurious() }
case class BadWhileBody(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1023"; override def hashCode(): Int = vcurious() }
case class BadIfCondition(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1024"; override def hashCode(): Int = vcurious() }
case class BadIfBody(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1025"; override def hashCode(): Int = vcurious() }
case class BadElseBody(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1026"; override def hashCode(): Int = vcurious() }
case class BadStruct(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1027"; override def hashCode(): Int = vcurious() }
case class BadStructName(pos: Int) extends IParseError { override def errorId: String = "P1027"; override def hashCode(): Int = vcurious() }
case class BadStructContentsBegin(pos: Int) extends IParseError { override def errorId: String = "P1027"; override def hashCode(): Int = vcurious() }
case class BadStructContentsEnd(pos: Int) extends IParseError { override def errorId: String = "P1027"; override def hashCode(): Int = vcurious() }
case class BadStructMember(pos: Int) extends IParseError { override def errorId: String = "P1027"; override def hashCode(): Int = vcurious() }
case class BadInterfaceMember(pos: Int) extends IParseError { override def errorId: String = "P1027"; override def hashCode(): Int = vcurious() }
case class BadInterface(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1028"; override def hashCode(): Int = vcurious() }
case class BadImpl(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1029"; override def hashCode(): Int = vcurious() }
case class BadExport(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1030"; override def hashCode(): Int = vcurious() }
case class BadImport(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1031"; override def hashCode(): Int = vcurious() }
case class BadFunctionHeaderError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1032"; override def hashCode(): Int = vcurious() }
case class BadEachError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1033"; override def hashCode(): Int = vcurious() }
case class BadBlockError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1034"; override def hashCode(): Int = vcurious() }
case class BadResultError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1035"; override def hashCode(): Int = vcurious() }
case class BadReturnError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1036"; override def hashCode(): Int = vcurious() }
case class BadDestructError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1037"; override def hashCode(): Int = vcurious() }
case class BadStandaloneExpressionError(pos: Int, cause: CombinatorParseError) extends IParseError {
  override def errorId: String = "P1038"; override def hashCode(): Int = vcurious()
  vpass()
}
case class BadArrayInitializer(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1039"; override def hashCode(): Int = vcurious() }
case class BadArraySpecifier(pos: Int) extends IParseError { override def errorId: String = "P1039"; override def hashCode(): Int = vcurious() }
case class BadMutDestinationError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1039"; override def hashCode(): Int = vcurious() }
case class BadMutSourceError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1040"; override def hashCode(): Int = vcurious() }
case class BadLetDestinationError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1041"; override def hashCode(): Int = vcurious() }
case class BadLetSourceError(pos: Int, cause: IParseError) extends IParseError {
  override def errorId: String = "P1042"; override def hashCode(): Int = vcurious()
  vpass()
}
case class BadForeachInError(pos: Int) extends IParseError {
  override def errorId: String = "P1041"; override def hashCode(): Int = vcurious()
  vpass()
}
case class BadForeachIterableError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1041"; override def hashCode(): Int = vcurious() }
