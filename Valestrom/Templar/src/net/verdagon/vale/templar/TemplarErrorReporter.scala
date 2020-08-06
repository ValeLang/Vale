package net.verdagon.vale.templar

import net.verdagon.vale.templar.OverloadTemplar.ScoutExpectedFunctionFailure

case class CompileErrorExceptionT(err: ICompileErrorT) extends RuntimeException

sealed trait ICompileErrorT
case class CouldntFindFunctionToCallT(seff: ScoutExpectedFunctionFailure) extends ICompileErrorT

object ErrorReporter {
  def report(err: ICompileErrorT): Nothing = {
    throw CompileErrorExceptionT(err)
  }
}
