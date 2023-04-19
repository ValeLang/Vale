package dev.vale.highertyping

import dev.vale.postparsing.rules.IRulexSR
import dev.vale.postparsing._
import dev.vale.solver.SolverErrorHumanizer
import dev.vale.{CodeLocationS, FileCoordinateMap, RangeS}
import dev.vale.SourceCodeUtils.{humanizePos, lineContaining, nextThingAndRestOfLine}
import dev.vale.postparsing.PostParserErrorHumanizer
import dev.vale.solver.FailedSolve

object HigherTypingErrorHumanizer {
  def assembleError(
    filenamesAndSources: CodeLocationS => String,
    lineContaining: (CodeLocationS) => String,
    range: RangeS,
    errorStrBody: String) = {
    val posStr = filenamesAndSources(range.begin)
    val nextStuff = lineContaining(range.begin)
    val errorId = "A"
    f"${posStr} error ${errorId}: ${errorStrBody}\n${nextStuff}\n"
  }

  def humanizeRuneTypeSolveError(
    codeMap: CodeLocationS => String,
    linesBetween: (CodeLocationS, CodeLocationS) => Vector[RangeS],
    lineRangeContaining: (CodeLocationS) => RangeS,
    lineContaining: (CodeLocationS) => String,
    err: RuneTypeSolveError):
  String = {
    ": Couldn't solve generics types:\n" +
    SolverErrorHumanizer.humanizeFailedSolve[IRulexSR, IRuneS, ITemplataType, IRuneTypeRuleError](
      codeMap,
      linesBetween,
      lineRangeContaining,
      lineContaining,
      PostParserErrorHumanizer.humanizeRune,
      (tyype: ITemplataType) => tyype.toString,
      err => PostParserErrorHumanizer.humanizeRuneTypeError(codeMap, err),
      (rule: IRulexSR) => rule.range,
      (rule: IRulexSR) => rule.runeUsages.map(u => (u.rune, u.range)),
      (rule: IRulexSR) => rule.runeUsages.map(_.rune),
      PostParserErrorHumanizer.humanizeRule,
      err.failedSolve)._1
  }

  def humanize(
    codeMap: CodeLocationS => String,
    linesBetween: (CodeLocationS, CodeLocationS) => Vector[RangeS],
    lineRangeContaining: (CodeLocationS) => RangeS,
    lineContaining: (CodeLocationS) => String,
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
          SolverErrorHumanizer.humanizeFailedSolve[IRulexSR, IRuneS, ITemplataType, IRuneTypeRuleError](
            codeMap,
            linesBetween,
            lineRangeContaining,
            lineContaining,
            PostParserErrorHumanizer.humanizeRune,
            (tyype: ITemplataType) => PostParserErrorHumanizer.humanizeTemplataType(tyype),
            err => PostParserErrorHumanizer.humanizeRuneTypeError(codeMap, err),
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
    assembleError(codeMap, lineContaining, err.range, errorStrBody)
  }
}
