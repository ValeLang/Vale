package net.verdagon.vale.astronomer

import net.verdagon.vale.astronomer.ruletyper.RuleTyperSolveFailure
import net.verdagon.vale.scout.RangeS

case class CompileErrorExceptionA(err: ICompileErrorA) extends RuntimeException

sealed trait ICompileErrorA { def range: RangeS }
case class CouldntFindTypeA(range: RangeS, name: String) extends ICompileErrorA
case class CouldntSolveRulesA(range: RangeS, failure: RuleTyperSolveFailure[List[IRulexAR]]) extends ICompileErrorA
case class CircularModuleDependency(range: RangeS, modules: Set[String]) extends ICompileErrorA
case class WrongNumArgsForTemplateA(range: RangeS, expectedNumArgs: Int, actualNumArgs: Int) extends ICompileErrorA

case class RangedInternalErrorA(range: RangeS, message: String) extends ICompileErrorA

object ErrorReporter {
  def report(err: ICompileErrorA): Nothing = {
    throw CompileErrorExceptionA(err)
  }
}
