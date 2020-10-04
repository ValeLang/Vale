package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.{GlobalFunctionFamilyNameA, IFunctionDeclarationNameA, IImpreciseNameStepA}
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.OverloadTemplar.ScoutExpectedFunctionFailure
import net.verdagon.vale.templar.templata.Signature2
import net.verdagon.vale.templar.types.{Coord, Kind}

case class CompileErrorExceptionT(err: ICompileErrorT) extends RuntimeException

sealed trait ICompileErrorT
case class CouldntFindTypeT(range: RangeS, name: String) extends ICompileErrorT //  vfail("Nothing found with name " + name)
case class CouldntFindFunctionToCallT(range: RangeS, seff: ScoutExpectedFunctionFailure) extends ICompileErrorT
case class CannotSubscriptT(range: RangeS, tyype: Kind) extends ICompileErrorT
case class CouldntFindIdentifierToLoadT(range: RangeS, name: String) extends ICompileErrorT
case class CouldntFindMemberT(range: RangeS, memberName: String) extends ICompileErrorT
case class BodyResultDoesntMatch(range: RangeS, functionName: IFunctionDeclarationNameA, expectedReturnType: Coord, resultType: Coord) extends ICompileErrorT
case class CouldntConvertForReturnT(range: RangeS, expectedType: Coord, actualType: Coord) extends ICompileErrorT
case class CouldntConvertForMutateT(range: RangeS, expectedType: Coord, actualType: Coord) extends ICompileErrorT
case class CantMoveOutOfMemberT(range: RangeS, name: IVarName2) extends ICompileErrorT
case class CantMutateUnstackifiedLocal(range: RangeS, localId: IVarName2) extends ICompileErrorT
case class FunctionAlreadyExists(oldFunctionRange: RangeS, newFunctionRange: RangeS, signature: Signature2) extends ICompileErrorT
// REMEMBER: Add any new errors to the "Humanize errors" test

object ErrorReporter {
  def report(err: ICompileErrorT): Nothing = {
    throw CompileErrorExceptionT(err)
  }
}
