package net.verdagon.vale.astronomer

import net.verdagon.vale.astronomer.ruletyper.RuleTyperSolveFailure
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.{vcurious, vimpl, vpass}

case class CompileErrorExceptionA(err: ICompileErrorA) extends RuntimeException { override def hashCode(): Int = vcurious() }

sealed trait ICompileErrorA { def range: RangeS }
case class CouldntFindTypeA(range: RangeS, name: String) extends ICompileErrorA { override def hashCode(): Int = vcurious() }
case class CouldntSolveRulesA(range: RangeS, failure: RuleTyperSolveFailure[Vector[IRulexAR]]) extends ICompileErrorA {
  override def hashCode(): Int = vcurious()
}
case class CircularModuleDependency(range: RangeS, modules: Set[String]) extends ICompileErrorA { override def hashCode(): Int = vcurious() }
case class WrongNumArgsForTemplateA(range: RangeS, expectedNumArgs: Int, actualNumArgs: Int) extends ICompileErrorA { override def hashCode(): Int = vcurious() }

case class RangedInternalErrorA(range: RangeS, message: String) extends ICompileErrorA { override def hashCode(): Int = vcurious() }

object ErrorReporter {
  def report(err: ICompileErrorA): Nothing = {
    throw CompileErrorExceptionA(err)
  }
}
