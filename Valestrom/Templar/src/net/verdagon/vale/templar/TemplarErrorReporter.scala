package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.{GlobalFunctionFamilyNameA, IFunctionDeclarationNameA, IImpreciseNameStepA}
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.OverloadTemplar.ScoutExpectedFunctionFailure
import net.verdagon.vale.templar.types.Coord

case class CompileErrorExceptionT(err: ICompileErrorT) extends RuntimeException

sealed trait ICompileErrorT
case class CouldntFindFunctionToCallT(range: RangeS, seff: ScoutExpectedFunctionFailure) extends ICompileErrorT
case class CouldntFindFunctionToLoadT(range: RangeS, name: GlobalFunctionFamilyNameA) extends ICompileErrorT
case class CouldntFindMemberT(range: RangeS, memberName: String) extends ICompileErrorT
case class BodyResultDoesntMatch(range: RangeS, functionName: IFunctionDeclarationNameA, expectedReturnType: Coord, resultType: Coord) extends ICompileErrorT

object ErrorReporter {
  def report(err: ICompileErrorT): Nothing = {
    throw CompileErrorExceptionT(err)
  }
}
