package dev.vale.highertyping

import dev.vale.postparsing.rules.IRulexSR
import dev.vale.postparsing._
import dev.vale.solver.SolverErrorHumanizer
import dev.vale.{FileCoordinateMap, RangeS}
import dev.vale.RangeS
import dev.vale.SourceCodeUtils.{humanizePos, lineContaining, nextThingAndRestOfLine}
import dev.vale.postparsing.PostParserErrorHumanizer
import dev.vale.solver.FailedSolve

object HigherTypingErrorHumanizer {
  def assembleError(
    filenamesAndSources: FileCoordinateMap[String],
    range: RangeS,
    errorStrBody: String) = {
    val posStr = humanizePos(filenamesAndSources, range.begin)
    val nextStuff = lineContaining(filenamesAndSources, range.begin)
    val errorId = "A"
    f"${posStr} error ${errorId}: ${errorStrBody}\n${nextStuff}\n"
  }

  def humanizeRuneTypeSolveError(
    filenamesAndSources: FileCoordinateMap[String],
    err: RuneTypeSolveError):
  String = {
    ": Couldn't solve generics types:\n" +
    SolverErrorHumanizer.humanizeFailedSolve(
      filenamesAndSources,
      PostParserErrorHumanizer.humanizeRune,
      (codeMap, tyype: ITemplataType) => tyype.toString,
      PostParserErrorHumanizer.humanizeRuneTypeError,
      (rule: IRulexSR) => rule.range,
      (rule: IRulexSR) => rule.runeUsages.map(u => (u.rune, u.range)),
      (rule: IRulexSR) => rule.runeUsages.map(_.rune),
      PostParserErrorHumanizer.humanizeRule,
      err.failedSolve)._1
  }

  def humanize(
      filenamesAndSources: FileCoordinateMap[String],
      err: ICompileErrorA):
  String = {
    val errorStrBody =
      err match {
        case RangedInternalErrorA(range, message) => {
          ": internal error: " + message
        }
        case CouldntFindTypeA(range, name) => {
          ": Couldn't find type `" + PostParserErrorHumanizer.humanizeImpreciseName(name) + "`:\n"
        }
        case CouldntSolveRulesA(range, err) => {
          ": Couldn't solve generics rules:\n" +
          SolverErrorHumanizer.humanizeFailedSolve(
            filenamesAndSources,
            PostParserErrorHumanizer.humanizeRune,
            (codeMap, tyype: ITemplataType) => PostParserErrorHumanizer.humanizeTemplataType(tyype),
            PostParserErrorHumanizer.humanizeRuneTypeError,
            (rule: IRulexSR) => rule.range,
            (rule: IRulexSR) => rule.runeUsages.map(u => (u.rune, u.range)),
            (rule: IRulexSR) => rule.runeUsages.map(_.rune),
            PostParserErrorHumanizer.humanizeRule,
            err.failedSolve)._1
        }
        case WrongNumArgsForTemplateA(range, expectedNumArgs, actualNumArgs) => {
          ": Expected " + expectedNumArgs + " template args but received " + actualNumArgs + "\n"
        }
      }
    assembleError(filenamesAndSources, err.range, errorStrBody)
  }
}
