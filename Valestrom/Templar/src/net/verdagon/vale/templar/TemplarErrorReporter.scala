package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.{GlobalFunctionFamilyNameA, IFunctionDeclarationNameA, IImpreciseNameStepA}
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.OverloadTemplar.ScoutExpectedFunctionFailure
import net.verdagon.vale.templar.types.Coord

case class CompileErrorExceptionT(err: ICompileErrorT) extends RuntimeException

sealed trait ICompileErrorT
case class CouldntFindTypeT(range: RangeS, name: String) extends ICompileErrorT //  vfail("Nothing found with name " + name)
case class CouldntFindFunctionToCallT(range: RangeS, seff: ScoutExpectedFunctionFailure) extends ICompileErrorT
case class CannotSubscriptT(range: RangeS, tyype: Coord) extends ICompileErrorT
case class CouldntFindFunctionToLoadT(range: RangeS, name: GlobalFunctionFamilyNameA) extends ICompileErrorT
case class CouldntFindMemberT(range: RangeS, memberName: String) extends ICompileErrorT
case class BodyResultDoesntMatch(range: RangeS, functionName: IFunctionDeclarationNameA, expectedReturnType: Coord, resultType: Coord) extends ICompileErrorT
case class CouldntConvertForReturnT(range: RangeS, expectedType: Coord, actualType: Coord) extends ICompileErrorT
// ("Can't move out of a member!")
case class CantMoveOutOfMemberT(range: RangeS, name: FullName2[IVarName2]) extends ICompileErrorT

object ErrorReporter {
  def report(err: ICompileErrorT): Nothing = {
    throw CompileErrorExceptionT(err)
  }
}
