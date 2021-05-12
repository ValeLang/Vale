package net.verdagon.vale.astronomer

import net.verdagon.vale.scout.RangeS

case class CompileErrorExceptionA(err: ICompileErrorA) extends RuntimeException

sealed trait ICompileErrorA
case class CouldntFindTypeA(range: RangeS, name: String) extends ICompileErrorA
case class CircularModuleDependency(modules: Set[String]) extends ICompileErrorA
case class WrongNumArgsForTemplateA(range: RangeS, expectedNumArgs: Int, actualNumArgs: Int) extends ICompileErrorA

case class RangedInternalErrorA(range: RangeS, message: String) extends ICompileErrorA

object ErrorReporter {
  def report(err: ICompileErrorA): Nothing = {
    throw CompileErrorExceptionA(err)
  }
}
