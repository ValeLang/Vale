package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.{GlobalFunctionFamilyNameA, ICompileErrorA, IFunctionDeclarationNameA, IImpreciseNameStepA, IVarNameA, TopLevelCitizenDeclarationNameA}
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.OverloadTemplar.ScoutExpectedFunctionFailure
import net.verdagon.vale.templar.templata.{PrototypeT, SignatureT}
import net.verdagon.vale.templar.types.{CitizenRefT, CoordT, InterfaceTT, KindT, StructTT}
import net.verdagon.vale.{PackageCoordinate, vcurious, vimpl, vpass}

case class CompileErrorExceptionT(err: ICompileErrorT) extends RuntimeException { override def hashCode(): Int = vcurious() }

sealed trait ICompileErrorT { def range: RangeS }
case class ImmStructCantHaveVaryingMember(range: RangeS, structName: TopLevelCitizenDeclarationNameA, memberName: String) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class CantDowncastUnrelatedTypes(range: RangeS, sourceKind: KindT, targetKind: KindT) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class CantDowncastToInterface(range: RangeS, targetKind: InterfaceTT) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class CouldntFindTypeT(range: RangeS, name: String) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class ArrayElementsHaveDifferentTypes(range: RangeS, types: Set[CoordT]) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class InitializedWrongNumberOfElements(range: RangeS, expectedNumElements: Int, numElementsInitialized: Int) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class CannotSubscriptT(range: RangeS, tyype: KindT) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class NonReadonlyReferenceFoundInPureFunctionParameter(range: RangeS, paramName: IVarNameT) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class CouldntFindIdentifierToLoadT(range: RangeS, name: String) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class CouldntFindMemberT(range: RangeS, memberName: String) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class BodyResultDoesntMatch(range: RangeS, functionName: IFunctionDeclarationNameA, expectedReturnType: CoordT, resultType: CoordT) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class CouldntConvertForReturnT(range: RangeS, expectedType: CoordT, actualType: CoordT) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class CouldntConvertForMutateT(range: RangeS, expectedType: CoordT, actualType: CoordT) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class CantMoveOutOfMemberT(range: RangeS, name: IVarNameT) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class CouldntFindFunctionToCallT(range: RangeS, seff: ScoutExpectedFunctionFailure) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class ExportedFunctionDependedOnNonExportedKind(range: RangeS, paackage: PackageCoordinate, signature: SignatureT, nonExportedKind: KindT) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class ExternFunctionDependedOnNonExportedKind(range: RangeS, paackage: PackageCoordinate, signature: SignatureT, nonExportedKind: KindT) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class ExportedImmutableKindDependedOnNonExportedKind(range: RangeS, paackage: PackageCoordinate, exportedKind: KindT, nonExportedKind: KindT) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class TypeExportedMultipleTimes(range: RangeS, paackage: PackageCoordinate, exports: List[KindExportT]) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class CantUseUnstackifiedLocal(range: RangeS, localId: IVarNameT) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class CantUnstackifyOutsideLocalFromInsideWhile(range: RangeS, localId: IVarNameT) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class FunctionAlreadyExists(oldFunctionRange: RangeS, newFunctionRange: RangeS, signature: SignatureT) extends ICompileErrorT {
  override def range: RangeS = newFunctionRange
}
case class CantMutateFinalMember(range: RangeS, fullName2: FullNameT[INameT], memberName: FullNameT[IVarNameT]) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class CantMutateFinalElement(range: RangeS, fullName2: FullNameT[INameT]) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
//case class CantMutateReadonlyMember(range: RangeS, structTT: structTT, memberName: FullName2[IVarName2]) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class CantUseReadonlyReferenceAsReadwrite(range: RangeS) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class LambdaReturnDoesntMatchInterfaceConstructor(range: RangeS) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class IfConditionIsntBoolean(range: RangeS, actualType: CoordT) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class WhileConditionIsntBoolean(range: RangeS, actualType: CoordT) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class CantMoveFromGlobal(range: RangeS, name: String) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
case class InferAstronomerError(err: ICompileErrorA) extends ICompileErrorT {
  override def range: RangeS = err.range
}
case class CantImplStruct(range: RangeS, parent: StructTT) extends ICompileErrorT { override def hashCode(): Int = vcurious() }
// REMEMBER: Add any new errors to the "Humanize errors" test

case class RangedInternalErrorT(range: RangeS, message: String) extends ICompileErrorT { override def hashCode(): Int = vcurious() }

object ErrorReporter {
  def report(err: ICompileErrorT): Nothing = {
    throw CompileErrorExceptionT(err)
  }
}
