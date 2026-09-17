use crate::postparsing::names::{IFunctionDeclarationNameS, IImpreciseNameS, INameS, IRuneS};
use crate::postparsing::rules::rules::IRulexSR;
use crate::postparsing::rune_type_solver::RuneTypeSolveError;
use crate::solver::solver::FailedSolve;
use crate::typing::ast::ast::{KindExportT, PrototypeT, SignatureT};
use crate::typing::infer::compiler_solver::ITypingPassSolverError;
use crate::typing::infer_compiler::{IDefiningError, IResolvingError};
use crate::typing::names::names::{IdT, IVarNameT};
use crate::typing::overload_resolver::FindFunctionFailure;
use crate::typing::templata::templata::ITemplataT;
use crate::typing::types::types::{CoordT, InterfaceTT, KindT, StructTT};
use crate::utils::code_hierarchy::PackageCoordinate;
use crate::utils::range::RangeS;
use std::slice::from_ref;

#[derive(Debug)]
pub enum ICompileErrorT<'s, 't> {
    CouldntNarrowDownCandidates { range: &'t [RangeS<'s>], candidates: &'t [PrototypeT<'s, 't>] },
    CouldntSolveRuneTypesT { range: &'t [RangeS<'s>], error: RuneTypeSolveError<'s> },
    NotEnoughGenericArgs { range: &'t [RangeS<'s>] },
    ImplSubCitizenNotFound { range: &'t [RangeS<'s>], name: IImpreciseNameS<'s> },
    ImplSuperInterfaceNotFound { range: &'t [RangeS<'s>], name: IImpreciseNameS<'s> },
    ImmStructCantHaveVaryingMember { range: &'t [RangeS<'s>], struct_name: INameS<'s>, member_name: &'s str },
    ImmStructCantHaveMutableMember { range: &'t [RangeS<'s>], struct_name: INameS<'s>, member_name: &'s str },
    CantReconcileBranchesResults { range: &'t [RangeS<'s>], then_result: CoordT<'s, 't>, else_result: CoordT<'s, 't> },
    IndexedArrayWithNonInteger { range: &'t [RangeS<'s>], types: CoordT<'s, 't> },
    WrongNumberOfDestructuresError { range: &'t [RangeS<'s>], actual_num: i32, expected_num: i32 },
    CantDowncastUnrelatedTypes {
        range: &'t [RangeS<'s>],
        source_kind: KindT<'s, 't>,
        target_kind: KindT<'s, 't>,
        candidates: &'t [FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>],
    },
    CantDowncastToInterface { range: &'t [RangeS<'s>], target_kind: InterfaceTT<'s, 't> },
    CantUseRuneValueAsExpression { range: &'t [RangeS<'s>], rune: IRuneS<'s> },
    CouldntFindTypeT { range: &'t [RangeS<'s>], name: IImpreciseNameS<'s> },
    TooManyTypesWithNameT { range: &'t [RangeS<'s>], name: IImpreciseNameS<'s> },
    ArrayElementsHaveDifferentTypes { range: &'t [RangeS<'s>], types: &'t [CoordT<'s, 't>] },
    UnexpectedArrayElementType { range: &'t [RangeS<'s>], expected_type: CoordT<'s, 't>, actual_type: CoordT<'s, 't> },
    InitializedWrongNumberOfElements { range: &'t [RangeS<'s>], expected_num_elements: i32, num_elements_initialized: i32 },
    NewImmRSANeedsCallable { range: &'t [RangeS<'s>] },
    CannotSubscriptT { range: &'t [RangeS<'s>], tyype: KindT<'s, 't> },
    NonReadonlyReferenceFoundInPureFunctionParameter { range: &'t [RangeS<'s>], param_name: IVarNameT<'s, 't> },
    CouldntFindIdentifierToLoadT { range: &'t [RangeS<'s>], name: IImpreciseNameS<'s> },
    CouldntFindMemberT { range: &'t [RangeS<'s>], member_name: &'s str },
    BodyResultDoesntMatch {
        range: &'t [RangeS<'s>],
        function_name: IFunctionDeclarationNameS<'s>,
        expected_return_type: CoordT<'s, 't>,
        result_type: CoordT<'s, 't>,
    },
    CouldntConvertForReturnT { range: &'t [RangeS<'s>], expected_type: CoordT<'s, 't>, actual_type: CoordT<'s, 't> },
    CouldntConvertForMutateT { range: &'t [RangeS<'s>], expected_type: CoordT<'s, 't>, actual_type: CoordT<'s, 't> },
    CantMoveOutOfMemberT { range: &'t [RangeS<'s>], name: IVarNameT<'s, 't> },
    CouldntFindFunctionToCallT { range: &'t [RangeS<'s>], fff: FindFunctionFailure<'s, 't> },
    CouldntEvaluateFunction { range: &'t [RangeS<'s>], eff: IDefiningError<'s, 't> },
    CouldntEvaluatImpl {
        range: &'t [RangeS<'s>],
        eff: FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>,
    },
    CouldntEvaluateStruct {
        range: &'t [RangeS<'s>],
        eff: FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>,
    },
    CouldntEvaluateInterface {
        range: &'t [RangeS<'s>],
        eff: FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>,
    },
    CouldntFindOverrideT { range: &'t [RangeS<'s>], fff: FindFunctionFailure<'s, 't> },
    ExportedFunctionDependedOnNonExportedKind {
        range: &'t [RangeS<'s>],
        paackage: PackageCoordinate<'s>,
        signature: &'t SignatureT<'s, 't>,
        non_exported_kind: KindT<'s, 't>,
    },
    ExternFunctionDependedOnNonExportedKind {
        range: &'t [RangeS<'s>],
        paackage: PackageCoordinate<'s>,
        signature: &'t SignatureT<'s, 't>,
        non_exported_kind: KindT<'s, 't>,
    },
    ExportedImmutableKindDependedOnNonExportedKind {
        range: &'t [RangeS<'s>],
        paackage: PackageCoordinate<'s>,
        exported_kind: KindT<'s, 't>,
        non_exported_kind: KindT<'s, 't>,
    },
    TypeExportedMultipleTimes { range: &'t [RangeS<'s>], paackage: PackageCoordinate<'s>, exports: &'t [KindExportT<'s, 't>] },
    CantUseUnstackifiedLocal { range: &'t [RangeS<'s>], local_id: IVarNameT<'s, 't> },
    CantUnstackifyOutsideLocalFromInsideWhile { range: &'t [RangeS<'s>], local_id: IVarNameT<'s, 't> },
    CantRestackifyOutsideLocalFromInsideWhile { range: &'t [RangeS<'s>], local_id: IVarNameT<'s, 't> },
    FunctionAlreadyExists { old_function_range: RangeS<'s>, new_function_range: RangeS<'s>, signature: IdT<'s, 't> },
    CantMutateFinalMember { range: &'t [RangeS<'s>], struct_: StructTT<'s, 't>, member_name: IVarNameT<'s, 't> },
    CantMutateFinalElement { range: &'t [RangeS<'s>], coord: CoordT<'s, 't> },
    CantUseReadonlyReferenceAsReadwrite { range: &'t [RangeS<'s>] },
    LambdaReturnDoesntMatchInterfaceConstructor { range: &'t [RangeS<'s>] },
    IfConditionIsntBoolean { range: &'t [RangeS<'s>], actual_type: CoordT<'s, 't> },
    WhileConditionIsntBoolean { range: &'t [RangeS<'s>], actual_type: CoordT<'s, 't> },
    HigherTypingInferError { range: &'t [RangeS<'s>], err: RuneTypeSolveError<'s> },
    AbstractMethodOutsideOpenInterface { range: &'t [RangeS<'s>] },
    TypingPassSolverError {
        range: &'t [RangeS<'s>],
        failed_solve: FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>,
    },
    TypingPassResolvingError { range: &'t [RangeS<'s>], inner: IResolvingError<'s, 't> },
    TypingPassDefiningError { range: &'t [RangeS<'s>], inner: IDefiningError<'s, 't> },
    CantImplNonInterface { range: &'t [RangeS<'s>], templata: ITemplataT<'s, 't> },
    NonCitizenCantImpl { range: &'t [RangeS<'s>], templata: ITemplataT<'s, 't> },
    RangedInternalErrorT { range: &'t [RangeS<'s>], message: &'s str },
    WeakableImplingMismatch { range: &'t [RangeS<'s>], struct_weakable: bool, interface_weakable: bool },
    TookWeakRefOfNonWeakableError { range: &'t [RangeS<'s>] },
}

impl<'s, 't> ICompileErrorT<'s, 't> {
    pub fn range(&self) -> &[RangeS<'s>] {
        match self {
            Self::CouldntNarrowDownCandidates { range, .. } => *range,
            Self::CouldntSolveRuneTypesT { range, .. } => *range,
            Self::NotEnoughGenericArgs { range, .. } => *range,
            Self::ImplSubCitizenNotFound { range, .. } => *range,
            Self::ImplSuperInterfaceNotFound { range, .. } => *range,
            Self::ImmStructCantHaveVaryingMember { range, .. } => *range,
            Self::ImmStructCantHaveMutableMember { range, .. } => *range,
            Self::CantReconcileBranchesResults { range, .. } => *range,
            Self::IndexedArrayWithNonInteger { range, .. } => *range,
            Self::WrongNumberOfDestructuresError { range, .. } => *range,
            Self::CantDowncastUnrelatedTypes { range, .. } => *range,
            Self::CantDowncastToInterface { range, .. } => *range,
            Self::CantUseRuneValueAsExpression { range, .. } => *range,
            Self::CouldntFindTypeT { range, .. } => *range,
            Self::TooManyTypesWithNameT { range, .. } => *range,
            Self::ArrayElementsHaveDifferentTypes { range, .. } => *range,
            Self::UnexpectedArrayElementType { range, .. } => *range,
            Self::InitializedWrongNumberOfElements { range, .. } => *range,
            Self::NewImmRSANeedsCallable { range, .. } => *range,
            Self::CannotSubscriptT { range, .. } => *range,
            Self::NonReadonlyReferenceFoundInPureFunctionParameter { range, .. } => *range,
            Self::CouldntFindIdentifierToLoadT { range, .. } => *range,
            Self::CouldntFindMemberT { range, .. } => *range,
            Self::BodyResultDoesntMatch { range, .. } => *range,
            Self::CouldntConvertForReturnT { range, .. } => *range,
            Self::CouldntConvertForMutateT { range, .. } => *range,
            Self::CantMoveOutOfMemberT { range, .. } => *range,
            Self::CouldntFindFunctionToCallT { range, .. } => *range,
            Self::CouldntEvaluateFunction { range, .. } => *range,
            Self::CouldntEvaluatImpl { range, .. } => *range,
            Self::CouldntEvaluateStruct { range, .. } => *range,
            Self::CouldntEvaluateInterface { range, .. } => *range,
            Self::CouldntFindOverrideT { range, .. } => *range,
            Self::ExportedFunctionDependedOnNonExportedKind { range, .. } => *range,
            Self::ExternFunctionDependedOnNonExportedKind { range, .. } => *range,
            Self::ExportedImmutableKindDependedOnNonExportedKind { range, .. } => *range,
            Self::TypeExportedMultipleTimes { range, .. } => *range,
            Self::CantUseUnstackifiedLocal { range, .. } => *range,
            Self::CantUnstackifyOutsideLocalFromInsideWhile { range, .. } => *range,
            Self::CantRestackifyOutsideLocalFromInsideWhile { range, .. } => *range,
            Self::FunctionAlreadyExists { new_function_range, .. } => from_ref(new_function_range),
            Self::CantMutateFinalMember { range, .. } => *range,
            Self::CantMutateFinalElement { range, .. } => *range,
            Self::CantUseReadonlyReferenceAsReadwrite { range, .. } => *range,
            Self::LambdaReturnDoesntMatchInterfaceConstructor { range, .. } => *range,
            Self::IfConditionIsntBoolean { range, .. } => *range,
            Self::WhileConditionIsntBoolean { range, .. } => *range,
            Self::HigherTypingInferError { range, .. } => *range,
            Self::AbstractMethodOutsideOpenInterface { range, .. } => *range,
            Self::TypingPassSolverError { range, .. } => *range,
            Self::TypingPassResolvingError { range, .. } => *range,
            Self::TypingPassDefiningError { range, .. } => *range,
            Self::CantImplNonInterface { range, .. } => *range,
            Self::NonCitizenCantImpl { range, .. } => *range,
            Self::RangedInternalErrorT { range, .. } => *range,
            Self::WeakableImplingMismatch { range, .. } => *range,
            Self::TookWeakRefOfNonWeakableError { range, .. } => *range,
        }
    }
}

