package dev.vale.highertyping

import dev.vale.{RangeS, vcurious, vpass}
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.postparsing.{IImpreciseNameS, RuneTypeSolveError}
import dev.vale.postparsing.RuneTypeSolveError
import dev.vale.RangeS

case class CompileErrorExceptionA(err: ICompileErrorA) extends RuntimeException { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

sealed trait ICompileErrorA { def range: RangeS }
case class CouldntFindTypeA(range: RangeS, name: IImpreciseNameS) extends ICompileErrorA {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class TooManyMatchingTypesA(range: RangeS, name: String) extends ICompileErrorA { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CouldntSolveRulesA(range: RangeS, error: RuneTypeSolveError) extends ICompileErrorA {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}
case class CircularModuleDependency(range: RangeS, modules: Set[String]) extends ICompileErrorA { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class WrongNumArgsForTemplateA(range: RangeS, expectedNumArgs: Int, actualNumArgs: Int) extends ICompileErrorA { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class RangedInternalErrorA(range: RangeS, message: String) extends ICompileErrorA { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

object ErrorReporter {
  def report(err: ICompileErrorA): Nothing = {
    throw CompileErrorExceptionA(err)
  }
}
