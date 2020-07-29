package net.verdagon.vale.parser

sealed trait IParseError
// TODO: Get rid of this when we've migrated away from combinators.
case class CombinatorParseError(pos: Int, msg: String) extends IParseError
case class UnrecognizedTopLevelThingError(pos: Int) extends IParseError
case class BadFunctionBodyError(pos: Int) extends IParseError
case class BadStartOfStatementError(pos: Int) extends IParseError
case class StatementAfterResult(pos: Int) extends IParseError
case class StatementAfterReturn(pos: Int) extends IParseError
case class BadExpressionEnd(pos: Int) extends IParseError
