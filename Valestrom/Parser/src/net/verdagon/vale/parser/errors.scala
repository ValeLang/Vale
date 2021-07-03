package net.verdagon.vale.parser

import net.verdagon.vale.{FileCoordinate, FileCoordinateMap, vpass}

case class FailedParse(
  codeMapSoFar: FileCoordinateMap[String],
  fileCoord: FileCoordinate,
  error: IParseError,
)

sealed trait IParseError {
  def pos: Int
  def errorId: String
}
case class UnrecognizedTopLevelThingError(pos: Int) extends IParseError { override def errorId: String = "P1001" }
case class BadStartOfStatementError(pos: Int) extends IParseError { override def errorId: String = "P1002" }
case class StatementAfterResult(pos: Int) extends IParseError { override def errorId: String = "P1003" }
case class StatementAfterReturn(pos: Int) extends IParseError { override def errorId: String = "P1004" }
case class BadExpressionEnd(pos: Int) extends IParseError { override def errorId: String = "P1005" }

case class BadFunctionBodyError(pos: Int) extends IParseError { override def errorId: String = "P1006" }

case class BadStartOfWhileCondition(pos: Int) extends IParseError { override def errorId: String = "P1007" }
case class BadEndOfWhileCondition(pos: Int) extends IParseError { override def errorId: String = "P1008" }
case class BadStartOfWhileBody(pos: Int) extends IParseError { override def errorId: String = "P1009" }
case class BadEndOfWhileBody(pos: Int) extends IParseError { override def errorId: String = "P1010" }

case class BadStartOfIfCondition(pos: Int) extends IParseError { override def errorId: String = "P1011" }
case class BadEndOfIfCondition(pos: Int) extends IParseError { override def errorId: String = "P1012" }
case class BadStartOfIfBody(pos: Int) extends IParseError { override def errorId: String = "P1013" }
case class BadEndOfIfBody(pos: Int) extends IParseError { override def errorId: String = "P1014" }
case class BadStartOfElseBody(pos: Int) extends IParseError { override def errorId: String = "P1015" }
case class BadEndOfElseBody(pos: Int) extends IParseError { override def errorId: String = "P1016" }

case class BadLetEqualsError(pos: Int) extends IParseError { override def errorId: String = "P1017" }
case class BadMutateEqualsError(pos: Int) extends IParseError { override def errorId: String = "P1018" }
case class BadLetEndError(pos: Int) extends IParseError { override def errorId: String = "P1019" }

case class BadVPSTException(err: BadVPSTError) extends RuntimeException {
//  println("VPST error:")
//  printStackTrace()
}
case class BadVPSTError(message: String) extends IParseError { override def pos = 0; override def errorId: String = "P1020" }


// TODO: Get rid of all the below when we've migrated away from combinators.

case class CombinatorParseError(pos: Int, msg: String)// extends IParseError { override def errorId: String = "P1021" }
case class BadWhileCondition(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1022" }
case class BadWhileBody(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1023" }
case class BadIfCondition(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1024" }
case class BadIfBody(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1025" }
case class BadElseBody(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1026" }
case class BadStruct(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1027" }
case class BadInterface(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1028" }
case class BadImpl(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1029" }
case class BadExport(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1030" }
case class BadImport(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1031" }
case class BadFunctionHeaderError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1032" }
case class BadEachError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1033" }
case class BadBlockError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1034" }
case class BadResultError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1035" }
case class BadReturnError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1036" }
case class BadDestructError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1037" }
case class BadStandaloneExpressionError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1038" }
case class BadMutDestinationError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1039" }
case class BadMutSourceError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1040" }
case class BadLetDestinationError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1041" }
case class BadLetSourceError(pos: Int, cause: CombinatorParseError) extends IParseError { override def errorId: String = "P1042" }
