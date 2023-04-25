package dev.vale.typing

import dev.vale.postparsing._
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.solver.IIncompleteOrFailedSolve
import dev.vale.typing.infer.ITypingPassSolverError
import dev.vale.typing.templata.ITemplata
import dev.vale.{PackageCoordinate, RangeS, vbreak, vcurious, vfail, vpass}
import dev.vale.typing.types._
import dev.vale.postparsing.RuneTypeSolveError
import dev.vale.solver.FailedSolve
import OverloadResolver.{EvaluateFunctionFailure, FindFunctionFailure, IFindFunctionFailureReason}
import dev.vale.typing.ast.{KindExportT, SignatureT}
import dev.vale.typing.names.{IdT, IFunctionNameT, IFunctionTemplateNameT, INameT, IVarNameT}
import dev.vale.typing.ast._
import dev.vale.typing.types.InterfaceTT

case class CompileErrorExceptionT(err: ICompileErrorT) extends RuntimeException {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}

sealed trait ICompileErrorT { def range: List[RangeS] }
case class CouldntNarrowDownCandidates(range: List[RangeS], candidates: Vector[RangeS]) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class CouldntSolveRuneTypesT(range: List[RangeS], error: RuneTypeSolveError) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}
case class NotEnoughGenericArgs(range: List[RangeS]) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ImplSubCitizenNotFound(range: List[RangeS], name: IImpreciseNameS) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ImplSuperInterfaceNotFound(range: List[RangeS], name: IImpreciseNameS) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ImmStructCantHaveVaryingMember(range: List[RangeS], structName: INameS, memberName: String) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ImmStructCantHaveMutableMember(range: List[RangeS], structName: INameS, memberName: String) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantReconcileBranchesResults(range: List[RangeS], thenResult: CoordT, elseResult: CoordT) extends ICompileErrorT {
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
case class IndexedArrayWithNonInteger(range: List[RangeS], types: CoordT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class WrongNumberOfDestructuresError(range: List[RangeS], actualNum: Int, expectedNum: Int) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantDowncastUnrelatedTypes(range: List[RangeS], sourceKind: KindT, targetKind: KindT, candidates: Vector[IIncompleteOrFailedSolve[IRulexSR, IRuneS, ITemplata[ITemplataType], ITypingPassSolverError]]) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class CantDowncastToInterface(range: List[RangeS], targetKind: InterfaceTT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CouldntFindTypeT(range: List[RangeS], name: String) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ArrayElementsHaveDifferentTypes(range: List[RangeS], types: Set[CoordT]) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class UnexpectedArrayElementType(range: List[RangeS], expectedType: CoordT, actualType: CoordT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class InitializedWrongNumberOfElements(range: List[RangeS], expectedNumElements: Int, numElementsInitialized: Int) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class NewImmRSANeedsCallable(range: List[RangeS]) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CannotSubscriptT(range: List[RangeS], tyype: KindT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class NonReadonlyReferenceFoundInPureFunctionParameter(range: List[RangeS], paramName: IVarNameT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CouldntFindIdentifierToLoadT(range: List[RangeS], name: IImpreciseNameS) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class CouldntFindMemberT(range: List[RangeS], memberName: String) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class BodyResultDoesntMatch(range: List[RangeS], functionName: IFunctionDeclarationNameS, expectedReturnType: CoordT, resultType: CoordT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CouldntConvertForReturnT(range: List[RangeS], expectedType: CoordT, actualType: CoordT) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class CouldntConvertForMutateT(range: List[RangeS], expectedType: CoordT, actualType: CoordT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantMoveOutOfMemberT(range: List[RangeS], name: IVarNameT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CouldntFindFunctionToCallT(range: List[RangeS], fff: FindFunctionFailure) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  vpass()
}
case class CouldntEvaluateFunction(range: List[RangeS], eff: IFindFunctionFailureReason) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CouldntEvaluatImpl(range: List[RangeS], eff: IIncompleteOrFailedCompilerSolve) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class CouldntFindOverrideT(range: List[RangeS], fff: FindFunctionFailure) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class ExportedFunctionDependedOnNonExportedKind(range: List[RangeS], paackage: PackageCoordinate, signature: SignatureT, nonExportedKind: KindT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ExternFunctionDependedOnNonExportedKind(range: List[RangeS], paackage: PackageCoordinate, signature: SignatureT, nonExportedKind: KindT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ExportedImmutableKindDependedOnNonExportedKind(range: List[RangeS], paackage: PackageCoordinate, exportedKind: KindT, nonExportedKind: KindT) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class TypeExportedMultipleTimes(range: List[RangeS], paackage: PackageCoordinate, exports: Vector[KindExportT]) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantUseUnstackifiedLocal(range: List[RangeS], localId: IVarNameT) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
case class CantUnstackifyOutsideLocalFromInsideWhile(range: List[RangeS], localId: IVarNameT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantRestackifyOutsideLocalFromInsideWhile(range: List[RangeS], localId: IVarNameT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class FunctionAlreadyExists(oldFunctionRange: RangeS, newFunctionRange: RangeS, signature: IdT[IFunctionNameT]) extends ICompileErrorT {
  override def range: List[RangeS] = List(newFunctionRange)
  vpass()
}
case class CantMutateFinalMember(range: List[RangeS], struct: StructTT, memberName: IVarNameT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantMutateFinalElement(range: List[RangeS], coord: CoordT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantUseReadonlyReferenceAsReadwrite(range: List[RangeS]) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class LambdaReturnDoesntMatchInterfaceConstructor(range: List[RangeS]) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class IfConditionIsntBoolean(range: List[RangeS], actualType: CoordT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class WhileConditionIsntBoolean(range: List[RangeS], actualType: CoordT) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantMoveFromGlobal(range: List[RangeS], name: String) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class HigherTypingInferError(range: List[RangeS], err: RuneTypeSolveError) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class AbstractMethodOutsideOpenInterface(range: List[RangeS]) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
//case class NotEnoughToSolveError(range: List[RangeS], conclusions: Map[IRuneS, ITemplata[ITemplataType]], unknownRunes: Iterable[IRuneS]) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TypingPassSolverError(range: List[RangeS], failedSolve: IIncompleteOrFailedCompilerSolve) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}
//case class CompilerSolverConflict(range: List[RangeS], conclusions: Map[IRuneS, ITemplata[ITemplataType]], rune: IRuneS, conflictingNewConclusion: ITemplata[ITemplataType]) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantImplNonInterface(range: List[RangeS], templata: ITemplata[ITemplataType]) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class NonCitizenCantImpl(range: List[RangeS], templata: ITemplata[ITemplataType]) extends ICompileErrorT { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
// REMEMBER: Add any new errors to the "Humanize errors" test

case class RangedInternalErrorT(range: List[RangeS], message: String) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vbreak()
}

object ErrorReporter {
  def report(err: ICompileErrorT): Nothing = {
    throw CompileErrorExceptionT(err)
  }
}
