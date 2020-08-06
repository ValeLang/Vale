package net.verdagon.vale.astronomer

import net.verdagon.vale.scout.RangeS

case class CompileErrorExceptionA(err: ICompileErrorA) extends RuntimeException

sealed trait ICompileErrorA
case class CouldntFindType(range: RangeS, name: String) extends ICompileErrorA //  vfail("Nothing found with name " + name)

object ErrorReporter {
  def report(err: ICompileErrorA): Nothing = {
    throw CompileErrorExceptionA(err)
  }
}
