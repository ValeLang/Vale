package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.{GlobalFunctionFamilyNameA, ICompileErrorA, IFunctionDeclarationNameA, IImpreciseNameStepA, IVarNameA, TopLevelCitizenDeclarationNameA}
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.OverloadTemplar.ScoutExpectedFunctionFailure
import net.verdagon.vale.templar.templata.{PrototypeT, SignatureT}
import net.verdagon.vale.templar.types.{CitizenRefT, CoordT, InterfaceTT, KindT, StructTT}
import net.verdagon.vale.{PackageCoordinate, vpass}

case class CompileErrorExceptionT(err: ICompileErrorT) extends RuntimeException

sealed trait ICompileErrorT { def range: RangeS }
case class ImmStructCantHaveVaryingMember(range: RangeS, structName: TopLevelCitizenDeclarationNameA, memberName: String) extends ICompileErrorT
case class CantDowncastUnrelatedTypes(range: RangeS, sourceKind: KindT, targetKind: KindT) extends ICompileErrorT
case class CantDowncastToInterface(range: RangeS, targetKind: InterfaceTT) extends ICompileErrorT
case class CouldntFindTypeT(range: RangeS, name: String) extends ICompileErrorT
case class ArrayElementsHaveDifferentTypes(range: RangeS, types: Set[CoordT]) extends ICompileErrorT
case class InitializedWrongNumberOfElements(range: RangeS, expectedNumElements: Int, numElementsInitialized: Int) extends ICompileErrorT
case class CannotSubscriptT(range: RangeS, tyype: KindT) extends ICompileErrorT
case class NonReadonlyReferenceFoundInPureFunctionParameter(range: RangeS, paramName: IVarNameT) extends ICompileErrorT
case class CouldntFindIdentifierToLoadT(range: RangeS, name: String) extends ICompileErrorT
case class CouldntFindMemberT(range: RangeS, memberName: String) extends ICompileErrorT
case class BodyResultDoesntMatch(range: RangeS, functionName: IFunctionDeclarationNameA, expectedReturnType: CoordT, resultType: CoordT) extends ICompileErrorT
case class CouldntConvertForReturnT(range: RangeS, expectedType: CoordT, actualType: CoordT) extends ICompileErrorT
case class CouldntConvertForMutateT(range: RangeS, expectedType: CoordT, actualType: CoordT) extends ICompileErrorT
case class CantMoveOutOfMemberT(range: RangeS, name: IVarNameT) extends ICompileErrorT
case class CouldntFindFunctionToCallT(range: RangeS, seff: ScoutExpectedFunctionFailure) extends ICompileErrorT
case class ExportedFunctionDependedOnNonExportedKind(range: RangeS, paackage: PackageCoordinate, signature: SignatureT, nonExportedKind: KindT) extends ICompileErrorT
case class ExternFunctionDependedOnNonExportedKind(range: RangeS, paackage: PackageCoordinate, signature: SignatureT, nonExportedKind: KindT) extends ICompileErrorT
case class ExportedKindDependedOnNonExportedKind(range: RangeS, paackage: PackageCoordinate, exportedKind: KindT, nonExportedKind: KindT) extends ICompileErrorT
case class CantUseUnstackifiedLocal(range: RangeS, localId: IVarNameT) extends ICompileErrorT
case class CantUnstackifyOutsideLocalFromInsideWhile(range: RangeS, localId: IVarNameT) extends ICompileErrorT
case class FunctionAlreadyExists(oldFunctionRange: RangeS, newFunctionRange: RangeS, signature: SignatureT) extends ICompileErrorT {
  override def range: RangeS = newFunctionRange
}
case class CantMutateFinalMember(range: RangeS, fullName2: FullNameT[INameT], memberName: FullNameT[IVarNameT]) extends ICompileErrorT
case class CantMutateFinalElement(range: RangeS, fullName2: FullNameT[INameT]) extends ICompileErrorT
//case class CantMutateReadonlyMember(range: RangeS, structTT: structTT, memberName: FullName2[IVarName2]) extends ICompileErrorT
case class CantUseReadonlyReferenceAsReadwrite(range: RangeS) extends ICompileErrorT
case class LambdaReturnDoesntMatchInterfaceConstructor(range: RangeS) extends ICompileErrorT
case class IfConditionIsntBoolean(range: RangeS, actualType: CoordT) extends ICompileErrorT
case class WhileConditionIsntBoolean(range: RangeS, actualType: CoordT) extends ICompileErrorT
case class CantMoveFromGlobal(range: RangeS, name: String) extends ICompileErrorT
case class InferAstronomerError(err: ICompileErrorA) extends ICompileErrorT {
  override def range: RangeS = err.range
}
case class CantImplStruct(range: RangeS, parent: StructTT) extends ICompileErrorT
// REMEMBER: Add any new errors to the "Humanize errors" test

case class RangedInternalErrorT(range: RangeS, message: String) extends ICompileErrorT

object ErrorReporter {
  def report(err: ICompileErrorT): Nothing = {
    throw CompileErrorExceptionT(err)
  }
}
