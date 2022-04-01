package dev.vale.templar

import dev.vale.astronomer.ICompileErrorA
import dev.vale.scout.{IFunctionDeclarationNameS, IImpreciseNameS, INameS, IRuneS, RuneTypeSolveError}
import dev.vale.scout.rules.IRulexSR
import dev.vale.solver.IIncompleteOrFailedSolve
import dev.vale.templar.infer.ITemplarSolverError
import dev.vale.templar.templata.ITemplata
import dev.vale.{PackageCoordinate, RangeS, vcurious, vfail, vpass}
import dev.vale.templar.types.{CoordT, InterfaceTT, KindT, NeverT, StructTT}
import dev.vale.scout.RuneTypeSolveError
import dev.vale.solver.FailedSolve
import OverloadTemplar.FindFunctionFailure
import dev.vale.templar.ast.{KindExportT, SignatureT}
import dev.vale.templar.names.{FullNameT, IVarNameT}
import dev.vale.templar.ast._
import dev.vale.templar.names.IVarNameT
import dev.vale.templar.types.InterfaceTT
import dev.vale.RangeS

case class CompileErrorExceptionT(err: ICompileErrorT) extends RuntimeException {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}

sealed trait ICompileErrorT { def range: RangeS }
case class CouldntNarrowDownCandidates(range: RangeS, candidates: Vector[RangeS]) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ImmStructCantHaveVaryingMember(range: RangeS, structName: INameS, memberName: String) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantReconcileBranchesResults(range: RangeS, thenResult: CoordT, elseResult: CoordT) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
  thenResult.kind match {
    case NeverT(_) => vfail()
    case _ =>
  }
  elseResult.kind match {
    case NeverT(_) => vfail()
    case _ =>
  }
}
case class IndexedArrayWithNonInteger(range: RangeS, types: CoordT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class WrongNumberOfDestructuresError(range: RangeS, actualNum: Int, expectedNum: Int) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantDowncastUnrelatedTypes(range: RangeS, sourceKind: KindT, targetKind: KindT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantDowncastToInterface(range: RangeS, targetKind: InterfaceTT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CouldntFindTypeT(range: RangeS, name: String) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ArrayElementsHaveDifferentTypes(range: RangeS, types: Set[CoordT]) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class UnexpectedArrayElementType(range: RangeS, expectedType: CoordT, actualType: CoordT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class InitializedWrongNumberOfElements(range: RangeS, expectedNumElements: Int, numElementsInitialized: Int) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class NewImmRSANeedsCallable(range: RangeS) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CannotSubscriptT(range: RangeS, tyype: KindT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class NonReadonlyReferenceFoundInPureFunctionParameter(range: RangeS, paramName: IVarNameT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CouldntFindIdentifierToLoadT(range: RangeS, name: IImpreciseNameS) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class CouldntFindMemberT(range: RangeS, memberName: String) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BodyResultDoesntMatch(range: RangeS, functionName: IFunctionDeclarationNameS, expectedReturnType: CoordT, resultType: CoordT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CouldntConvertForReturnT(range: RangeS, expectedType: CoordT, actualType: CoordT) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class CouldntConvertForMutateT(range: RangeS, expectedType: CoordT, actualType: CoordT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantMoveOutOfMemberT(range: RangeS, name: IVarNameT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CouldntFindFunctionToCallT(range: RangeS, fff: FindFunctionFailure) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CouldntFindOverrideT(range: RangeS, fff: FindFunctionFailure) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ExportedFunctionDependedOnNonExportedKind(range: RangeS, paackage: PackageCoordinate, signature: SignatureT, nonExportedKind: KindT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ExternFunctionDependedOnNonExportedKind(range: RangeS, paackage: PackageCoordinate, signature: SignatureT, nonExportedKind: KindT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ExportedImmutableKindDependedOnNonExportedKind(range: RangeS, paackage: PackageCoordinate, exportedKind: KindT, nonExportedKind: KindT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TypeExportedMultipleTimes(range: RangeS, paackage: PackageCoordinate, exports: Vector[KindExportT]) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantUseUnstackifiedLocal(range: RangeS, localId: IVarNameT) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class CantUnstackifyOutsideLocalFromInsideWhile(range: RangeS, localId: IVarNameT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class FunctionAlreadyExists(oldFunctionRange: RangeS, newFunctionRange: RangeS, signature: SignatureT) extends ICompileErrorT {
  override def range: RangeS = newFunctionRange
  vpass()
}
case class CantMutateFinalMember(range: RangeS, struct: StructTT, memberName: FullNameT[IVarNameT]) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantMutateFinalElement(range: RangeS, coord: CoordT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantUseReadonlyReferenceAsReadwrite(range: RangeS) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class LambdaReturnDoesntMatchInterfaceConstructor(range: RangeS) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class IfConditionIsntBoolean(range: RangeS, actualType: CoordT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class WhileConditionIsntBoolean(range: RangeS, actualType: CoordT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantMoveFromGlobal(range: RangeS, name: String) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class InferAstronomerError(range: RangeS, err: RuneTypeSolveError) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class AbstractMethodOutsideOpenInterface(range: RangeS) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
//case class NotEnoughToSolveError(range: RangeS, conclusions: Map[IRuneS, ITemplata], unknownRunes: Iterable[IRuneS]) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TemplarSolverError(range: RangeS, failedSolve: IIncompleteOrFailedSolve[IRulexSR, IRuneS, ITemplata, ITemplarSolverError]) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
//case class TemplarSolverConflict(range: RangeS, conclusions: Map[IRuneS, ITemplata], rune: IRuneS, conflictingNewConclusion: ITemplata) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantImplNonInterface(range: RangeS, parent: KindT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
// REMEMBER: Add any new errors to the "Humanize errors" test

case class RangedInternalErrorT(range: RangeS, message: String) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

object ErrorReporter {
  def report(err: ICompileErrorT): Nothing = {
    throw CompileErrorExceptionT(err)
  }
}
