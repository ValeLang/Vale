package net.verdagon.vale.parser

import net.verdagon.vale.vpass

sealed trait IParseError
case class UnrecognizedTopLevelThingError(pos: Int) extends IParseError
case class BadStartOfStatementError(pos: Int) extends IParseError
case class StatementAfterResult(pos: Int) extends IParseError
case class StatementAfterReturn(pos: Int) extends IParseError
case class BadExpressionEnd(pos: Int) extends IParseError

case class BadFunctionBodyError(pos: Int) extends IParseError

case class BadStartOfWhileCondition(pos: Int) extends IParseError
case class BadEndOfWhileCondition(pos: Int) extends IParseError
case class BadStartOfWhileBody(pos: Int) extends IParseError
case class BadEndOfWhileBody(pos: Int) extends IParseError

case class BadStartOfIfCondition(pos: Int) extends IParseError
case class BadEndOfIfCondition(pos: Int) extends IParseError
case class BadStartOfIfBody(pos: Int) extends IParseError
case class BadEndOfIfBody(pos: Int) extends IParseError
case class BadStartOfElseBody(pos: Int) extends IParseError
case class BadEndOfElseBody(pos: Int) extends IParseError

case class BadLetEqualsError(pos: Int) extends IParseError
case class BadMutateEqualsError(pos: Int) extends IParseError
case class BadLetEndError(pos: Int) extends IParseError

case class BadVPSTException(err: BadVPSTError) extends RuntimeException {
//  println("VPST error:")
//  printStackTrace()
}
case class BadVPSTError(message: String) extends IParseError {
  vpass()
}


// TODO: Get rid of all the below when we've migrated away from combinators.

case class CombinatorParseError(pos: Int, msg: String) extends IParseError
case class BadWhileCondition(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadWhileBody(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadIfCondition(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadIfBody(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadElseBody(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadStruct(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadInterface(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadImpl(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadExport(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadImport(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadFunctionHeaderError(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadEachError(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadBlockError(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadResultError(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadReturnError(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadDestructError(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadStandaloneExpressionError(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadMutDestinationError(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadMutSourceError(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadLetDestinationError(pos: Int, cause: CombinatorParseError) extends IParseError
case class BadLetSourceError(pos: Int, cause: CombinatorParseError) extends IParseError
