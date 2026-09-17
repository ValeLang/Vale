use crate::utils::range::{RangeS, CodeLocationS};
use crate::interner::Interner;
use crate::postparsing::*;
use crate::postparsing::names::*;
use crate::scout_arena::ScoutArena;
use crate::typing::typing_interner::TypingInterner;
use crate::postparsing::rules::rules::*;
use crate::typing::names::names::*;
use crate::typing::types::types::*;
use crate::typing::templata::templata::*;
use crate::typing::ast::ast::*;
use crate::typing::ast::citizens::*;
use crate::typing::ast::expressions::*;
use crate::typing::compiler_error_reporter::*;
use crate::typing::compiler_outputs::*;
use crate::typing::infer::compiler_solver::*;
use crate::typing::infer_compiler::*;
use crate::typing::overload_resolver::*;
use crate::typing::compilation::TypingPassOptions;
use crate::solver::solver::*;
use crate::higher_typing::ast::*;
use crate::higher_typing::ast::FunctionA;
use crate::typing::citizen::struct_compiler::*;
use crate::utils::code_hierarchy::FileCoordinate;
use crate::typing::types::types::OwnershipT;
use crate::postparsing::post_parser_error_humanizer::humanize_imprecise_name;
use crate::postparsing::post_parser_error_humanizer::humanize_rule;
use crate::postparsing::post_parser_error_humanizer::humanize_rune;
use crate::solver::solver_error_humanizer::humanize_failed_solve as solver_humanize_failed_solve;
use crate::utils::source_code_utils::humanize_package;
use std::iter::once;

// Mirror of canonical Scala CompilerErrorHumanizer.humanize. Per MACTX, several canonical
// arms (CantUseRuneValueAsExpression, KindIsNotStruct, CouldntFindImpl, CantSharePlaceholder,
// NoCommonAncestors, CantDetermineNarrowestKind, FunctionDoesntHaveName, InternalSolverError)
// are present in the audit-trail above but their Rust ICompileErrorT variants haven't been
// added yet — they fall through to the catch-all `_ => panic!` below until ported.
pub fn humanize<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, verbose: bool, code_map: &dyn Fn(CodeLocationS<'s>) -> String, lines_between: &dyn Fn(CodeLocationS<'s>, CodeLocationS<'s>) -> Vec<RangeS<'s>>, line_range_containing: &dyn Fn(CodeLocationS<'s>) -> RangeS<'s>, line_containing: &dyn Fn(CodeLocationS<'s>) -> String, err: ICompileErrorT<'s, 't>) -> String {
  let error_str_body = match &err {
    ICompileErrorT::TypingPassDefiningError { range: _, inner } => {
      humanize_defining_error(scout_arena, typing_interner, verbose, code_map, lines_between, line_range_containing, line_containing, inner)
    }
    ICompileErrorT::TypingPassResolvingError { range: _, inner } => {
      humanize_resolving_error(scout_arena, typing_interner, verbose, code_map, lines_between, line_range_containing, line_containing, inner)
    }
    ICompileErrorT::RangedInternalErrorT { range: _, message } => {
      format!("Internal error: {}", message)
    }
    ICompileErrorT::CouldntFindOverrideT { range, fff } => {
      format!("Couldn't find an override:\n{}",
        humanize_find_function_failure(scout_arena, typing_interner, verbose, code_map, lines_between, line_range_containing, line_containing, range.to_vec(), fff))
    }
    ICompileErrorT::NewImmRSANeedsCallable { range: _ } => {
      "To make an immutable runtime-sized array, need two params: capacity int, plus lambda to populate that many elements.".to_string()
    }
    ICompileErrorT::CouldntSolveRuneTypesT { range: _, error: _ } => {
      panic!("implement: humanize CouldntSolveRuneTypesT")
    }
    ICompileErrorT::UnexpectedArrayElementType { range: _, expected_type, actual_type } => {
      format!("Unexpected type for array element, tried to put a {} into an array of {}",
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *actual_type }))),
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *expected_type }))))
    }
    ICompileErrorT::IndexedArrayWithNonInteger { range: _, types } => {
      format!("Indexed array with non-integer: {}",
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *types }))))
    }
    ICompileErrorT::CantUseReadonlyReferenceAsReadwrite { range: _ } => {
      "Can't make readonly reference into a readwrite one!".to_string()
    }
    ICompileErrorT::CantReconcileBranchesResults { range: _, then_result, else_result } => {
      "If branches return different types: ".to_string()
        + &humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *then_result })))
        + " and "
        + &humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *else_result })))
    }
    ICompileErrorT::CantMoveOutOfMemberT { range: _, name } => {
      format!("Cannot move out of member ({:?})", name)
    }
    ICompileErrorT::CantMutateFinalMember { range: _, struct_, member_name } => {
      format!("Cannot mutate final member '{}' of container {}",
        printable_var_name(*member_name),
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Kind(typing_interner.alloc(KindTemplataT { kind: KindT::Struct(typing_interner.intern_struct_tt(StructTTValT { id: struct_.id })) }))))
    }
    ICompileErrorT::CantMutateFinalElement { range: _, coord } => {
      format!("Cannot change a slot in array {} to point to a different element; it's an array of final references.",
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *coord }))))
    }
    ICompileErrorT::LambdaReturnDoesntMatchInterfaceConstructor { range: _ } => {
      "Argument function return type doesn't match interface method param".to_string()
    }
    ICompileErrorT::CantUseUnstackifiedLocal { range: _, local_id } => {
      format!("Can't use local that was already moved: {}",
        humanize_name(scout_arena, typing_interner, code_map, INameT::from(*local_id), None))
    }
    ICompileErrorT::CantUnstackifyOutsideLocalFromInsideWhile { range: _, local_id } => {
      format!("Can't move a local ({:?}) from inside a while loop.", local_id)
    }
    ICompileErrorT::CannotSubscriptT { range: _, tyype } => {
      format!("Cannot subscript type: {}!",
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Kind(typing_interner.alloc(KindTemplataT { kind: *tyype }))))
    }
    ICompileErrorT::CouldntConvertForReturnT { range: _, expected_type, actual_type } => {
      format!("Couldn't convert {} to expected return type {}",
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *actual_type }))),
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *expected_type }))))
    }
    ICompileErrorT::CouldntConvertForMutateT { range: _, expected_type, actual_type } => {
      format!("Mutate couldn't convert {:?} to expected destination type {:?}", actual_type, expected_type)
    }
    ICompileErrorT::CouldntFindMemberT { range: _, member_name } => {
      format!("Couldn't find member {}!", member_name)
    }
    ICompileErrorT::CouldntEvaluatImpl { range: _, eff } => {
      format!("Couldn't evaluate impl statement:\n{}",
        humanize_candidate_and_failed_solve(scout_arena, typing_interner, code_map, lines_between, line_range_containing, line_containing, eff))
    }
    ICompileErrorT::BodyResultDoesntMatch { range: _, function_name, expected_return_type, result_type } => {
      format!("Function {} return type {} doesn't match body's result: {}",
        printable_name(scout_arena, typing_interner, code_map, INameS::FunctionDeclaration(scout_arena.alloc(*function_name))),
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *expected_return_type }))),
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *result_type }))))
    }
    ICompileErrorT::CouldntFindIdentifierToLoadT { range: _, name } => {
      format!("Couldn't find anything named `{}`!", humanize_imprecise_name(*name))
    }
    ICompileErrorT::CantUseRuneValueAsExpression { range: _, rune } => {
      format!("Can't use rune `{}` as a value expression. Did you mean a local variable with a similar name?", humanize_rune(*rune))
    }
    ICompileErrorT::WeakableImplingMismatch { range: _, struct_weakable, interface_weakable } => {
      format!("Weakable mismatch in impl: struct {} weakable, but interface {}.",
        if *struct_weakable { "is" } else { "is not" },
        if *interface_weakable { "is" } else { "is not" })
    }
    ICompileErrorT::TookWeakRefOfNonWeakableError { range: _ } => {
      "Took a weak reference of something that isn't weakable. Did you mean to add the `weakable` keyword?".to_string()
    }
    ICompileErrorT::NonReadonlyReferenceFoundInPureFunctionParameter { range: _, param_name } => {
      format!("Parameter `{:?}` should be readonly, because it's in a pure function.", param_name)
    }
    ICompileErrorT::CouldntFindTypeT { range: _, name } => {
      format!("Couldn't find any type named `{:?}`!", name)
    }
    ICompileErrorT::CouldntNarrowDownCandidates { range: _, candidates } => {
      let parts: Vec<String> = candidates.iter().map(|proto| {
        format!("\n  {}", humanize_id(scout_arena, typing_interner, code_map, proto.id, None))
      }).collect();
      format!("Multiple candidates for call:{}", parts.join(""))
    }
    ICompileErrorT::ImmStructCantHaveVaryingMember { range: _, struct_name, member_name } => {
      format!("Immutable struct (\"{}\") cannot have varying member (\"{}\").",
        printable_name(scout_arena, typing_interner, code_map, *struct_name), member_name)
    }
    ICompileErrorT::ImmStructCantHaveMutableMember { range: _, struct_name, member_name } => {
      format!("Immutable struct (\"{}\") cannot have mutable member (\"{}\").",
        printable_name(scout_arena, typing_interner, code_map, *struct_name), member_name)
    }
    ICompileErrorT::WrongNumberOfDestructuresError { range: _, actual_num, expected_num } => {
      format!("Wrong number of receivers; receiving {} but should be {}.", actual_num, expected_num)
    }
    ICompileErrorT::CantDowncastUnrelatedTypes { range: _, source_kind, target_kind, candidates: _ } => {
      format!("Can't downcast `{}` to unrelated `{}`",
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Kind(typing_interner.alloc(KindTemplataT { kind: *source_kind }))),
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Kind(typing_interner.alloc(KindTemplataT { kind: *target_kind }))))
    }
    ICompileErrorT::CantDowncastToInterface { range: _, target_kind } => {
      format!("Can't downcast to an interface ({:?}) yet.", target_kind)
    }
    ICompileErrorT::ArrayElementsHaveDifferentTypes { range: _, types } => {
      let types_str = types.iter().map(|c| format!("{:?}", c)).collect::<Vec<_>>().join(", ");
      format!("Array's elements have different types: {}", types_str)
      // "Array's elements have different types: " + types.mkString(", ")
    }
    ICompileErrorT::ExportedFunctionDependedOnNonExportedKind { range: _, paackage, signature, non_exported_kind } => {
      format!(r"Exported function:
{}
depends on kind:
{}
that wasn't exported from package {}",
        humanize_signature(scout_arena, typing_interner, code_map, **signature),
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Kind(typing_interner.alloc(KindTemplataT { kind: *non_exported_kind }))),
        humanize_package(paackage))
    }
    ICompileErrorT::TypeExportedMultipleTimes { range: _, paackage: _, exports } => {
      let parts: Vec<String> = exports.iter().map(|export| {
        let pos_str = code_map(export.range.begin);
        let line = line_containing(export.range.begin);
        format!("\n  {}: {}", pos_str, line)
      }).collect();
      format!("Type exported multiple times:{}", parts.join(""))
    }
    ICompileErrorT::ExternFunctionDependedOnNonExportedKind { range: _, paackage, signature, non_exported_kind } => {
      format!("Extern function {} depends on kind {} that wasn't exported from package {}",
        humanize_signature(scout_arena, typing_interner, code_map, **signature),
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Kind(typing_interner.alloc(KindTemplataT { kind: *non_exported_kind }))),
        humanize_package(paackage))
    }
    ICompileErrorT::ExportedImmutableKindDependedOnNonExportedKind { range: _, paackage, exported_kind, non_exported_kind } => {
      format!("Exported kind {} depends on kind {} that wasn't exported from package {}",
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Kind(typing_interner.alloc(KindTemplataT { kind: *exported_kind }))),
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Kind(typing_interner.alloc(KindTemplataT { kind: *non_exported_kind }))),
        humanize_package(paackage))
    }
    ICompileErrorT::InitializedWrongNumberOfElements { range: _, expected_num_elements, num_elements_initialized } => {
      format!("Supplied {} elements, but expected {}.", num_elements_initialized, expected_num_elements)
    }
    ICompileErrorT::CouldntFindFunctionToCallT { range, fff } => {
      humanize_find_function_failure(scout_arena, typing_interner, verbose, code_map, lines_between, line_range_containing, line_containing, range.to_vec(), fff)
    }
    ICompileErrorT::CouldntEvaluateFunction { range: _, eff } => {
      format!("Couldn't evaluate function:\n{}",
        humanize_defining_error(scout_arena, typing_interner, verbose, code_map, lines_between, line_range_containing, line_containing, eff))
    }
    ICompileErrorT::FunctionAlreadyExists { old_function_range, new_function_range: _, signature } => {
      format!("Function {} already exists! Previous declaration at:\n{}",
        humanize_id(scout_arena, typing_interner, code_map, *signature, None),
        code_map(old_function_range.begin))
    }
    ICompileErrorT::AbstractMethodOutsideOpenInterface { range: _ } => {
      "Open (non-sealed) interfaces can't have abstract methods defined outside the interface.".to_string()
    }
    ICompileErrorT::IfConditionIsntBoolean { range: _, actual_type } => {
      format!("If condition should be a bool, but was: {:?}", actual_type)
    }
    ICompileErrorT::WhileConditionIsntBoolean { range: _, actual_type } => {
      format!("If condition should be a bool, but was: {:?}", actual_type)
    }
    ICompileErrorT::CantImplNonInterface { range: _, templata } => {
      format!("Can't extend a non-interface: {:?}", templata)
    }
    ICompileErrorT::NonCitizenCantImpl { range: _, templata: _ } => {
      panic!("implement: humanize NonCitizenCantImpl")
    }
    ICompileErrorT::TypingPassSolverError { range: _, failed_solve } => {
      humanize_candidate_and_failed_solve(scout_arena, typing_interner, code_map, lines_between, line_range_containing, line_containing, failed_solve)
    }
    ICompileErrorT::HigherTypingInferError { range: _, err } => {
      let inner_msg = match &err.failed_solve.error {
        ISolverError::RuleError(re) => {
          crate::postparsing::post_parser_error_humanizer::humanize_rune_type_error(code_map, &re.err)
        }
        ISolverError::SolverConflict(_) | ISolverError::SolveIncomplete(_) => {
          format!("{:?}", err.failed_solve.error)
        }
      };
      format!(": Couldn't solve generics types:\n{}", inner_msg)
    }
    ICompileErrorT::TooManyTypesWithNameT { range: _, name: _ } => {
      panic!("implement: humanize TooManyTypesWithNameT")
    }
    ICompileErrorT::NotEnoughGenericArgs { range: _ } => {
      panic!("implement: humanize NotEnoughGenericArgs")
    }
    ICompileErrorT::ImplSubCitizenNotFound { range: _, name: _ } => {
      panic!("implement: humanize ImplSubCitizenNotFound")
    }
    ICompileErrorT::ImplSuperInterfaceNotFound { range: _, name: _ } => {
      panic!("implement: humanize ImplSuperInterfaceNotFound")
    }
    ICompileErrorT::CantRestackifyOutsideLocalFromInsideWhile { range: _, local_id: _ } => {
      panic!("implement: humanize CantRestackifyOutsideLocalFromInsideWhile")
    }
    ICompileErrorT::CouldntEvaluateStruct { range: _, eff: _ } => {
      panic!("implement: humanize CouldntEvaluateStruct")
    }
    ICompileErrorT::CouldntEvaluateInterface { range: _, eff: _ } => {
      panic!("implement: humanize CouldntEvaluateInterface")
    }
  };
  // err.range.reverse.map(range => { ... }).mkString("") + errorStrBody + "\n"
  let prefix: String = err.range().iter().rev().map(|range| {
    let pos_str = code_map(range.begin);
    let line_contents = line_containing(range.begin);
    format!("At {}:\n{}\n", pos_str, line_contents)
  }).collect::<Vec<_>>().join("");
  format!("{}{}\n", prefix, error_str_body)
}

pub fn humanize_defining_error<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, verbose: bool, code_map: &dyn Fn(CodeLocationS<'s>) -> String, lines_between: &dyn Fn(CodeLocationS<'s>, CodeLocationS<'s>) -> Vec<RangeS<'s>>, line_range_containing: &dyn Fn(CodeLocationS<'s>) -> RangeS<'s>, line_containing: &dyn Fn(CodeLocationS<'s>) -> String, err: &IDefiningError<'s, 't>) -> String {
  panic!("Unimplemented: humanize_defining_error");
}

pub fn humanize_resolve_failure<'s, 't>(verbose: bool, code_map: &dyn Fn(CodeLocationS) -> String, lines_between: &dyn Fn(CodeLocationS, CodeLocationS) -> Vec<RangeS<'s>>, line_range_containing: &dyn Fn(CodeLocationS) -> RangeS<'s>, line_containing: &dyn Fn(CodeLocationS) -> String, fff: ResolveFailure<'s, 't, KindT<'s, 't>>) -> String {
  panic!("Unimplemented: humanize_resolve_failure");
}

pub fn humanize_resolving_error<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, verbose: bool, code_map: &dyn Fn(CodeLocationS<'s>) -> String, lines_between: &dyn Fn(CodeLocationS<'s>, CodeLocationS<'s>) -> Vec<RangeS<'s>>, line_range_containing: &dyn Fn(CodeLocationS<'s>) -> RangeS<'s>, line_containing: &dyn Fn(CodeLocationS<'s>) -> String, error: &IResolvingError<'s, 't>) -> String {
  match error {
    IResolvingError::ResolvingResolveConclusionError(inner) => {
      humanize_conclusion_resolve_error(scout_arena, typing_interner, verbose, code_map, lines_between, line_range_containing, line_containing, inner.as_ref())
    }
    IResolvingError::ResolvingSolveFailedOrIncomplete(inner) => {
      humanize_candidate_and_failed_solve(scout_arena, typing_interner, code_map, lines_between, line_range_containing, line_containing, inner)
    }
  }
}

pub fn humanize_failed_solve<'s, 't>(verbose: bool, code_map: &dyn Fn(CodeLocationS) -> String, lines_between: &dyn Fn(CodeLocationS, CodeLocationS) -> Vec<RangeS<'s>>, line_range_containing: &dyn Fn(CodeLocationS) -> RangeS<'s>, line_containing: &dyn Fn(CodeLocationS) -> String, error: FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>) -> String {
  panic!("Unimplemented: humanize_failed_solve");
}

pub fn humanize_conclusion_resolve_error<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, verbose: bool, code_map: &dyn Fn(CodeLocationS<'s>) -> String, lines_between: &dyn Fn(CodeLocationS<'s>, CodeLocationS<'s>) -> Vec<RangeS<'s>>, line_range_containing: &dyn Fn(CodeLocationS<'s>) -> RangeS<'s>, line_containing: &dyn Fn(CodeLocationS<'s>) -> String, error: &IConclusionResolveError<'s, 't>) -> String {
  match error {
    IConclusionResolveError::ReturnTypeConflictInConclusionResolve { range: _, expected_return_type, actual } => {
      format!(
        "Found function: {} which returns {} but expected return type of {}",
        humanize_id(scout_arena, typing_interner, code_map, actual.id, None),
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: actual.return_type }))),
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *expected_return_type }))))
    }
    other => panic!("Unimplemented humanize_conclusion_resolve_error arm: {:?}", other),
  }
}

pub fn humanize_find_function_failure<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, verbose: bool, code_map: &dyn Fn(CodeLocationS<'s>) -> String, lines_between: &dyn Fn(CodeLocationS<'s>, CodeLocationS<'s>) -> Vec<RangeS<'s>>, line_range_containing: &dyn Fn(CodeLocationS<'s>) -> RangeS<'s>, line_containing: &dyn Fn(CodeLocationS<'s>) -> String, invocation_range: Vec<RangeS<'s>>, fff: &FindFunctionFailure<'s, 't>) -> String {
  let FindFunctionFailure { name, args, rejected_callee_to_reason } = fff;
  let args_str = args.iter().map(|tyype| {
    humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *tyype })))
  }).collect::<Vec<_>>().join(", ");
  let tail = if rejected_callee_to_reason.is_empty() {
    "No function with that name exists.\n".to_string()
  } else {
    let parts = rejected_callee_to_reason.iter().enumerate().map(|(index, (candidate, reason))| {
      format!("Candidate {} (of {}): {}{}\n\n",
        index + 1, rejected_callee_to_reason.len(),
        humanize_candidate(scout_arena, typing_interner, code_map, line_range_containing, candidate),
        humanize_rejection_reason(scout_arena, typing_interner, verbose, code_map, lines_between, line_range_containing, line_containing, &invocation_range, reason))
    }).collect::<Vec<_>>().join("");
    format!("Rejected candidates:\n\n{}", parts)
  };
  format!("Couldn't find a suitable function {}({}). {}",
    humanize_imprecise_name(*name),
    args_str,
    tail)
}

pub fn humanize_banner(code_map: &dyn Fn(CodeLocationS) -> String, banner: FunctionBannerT) -> String {
  panic!("Unimplemented: humanize_banner");
}

fn printable_name<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, code_map: &dyn Fn(CodeLocationS<'s>) -> String, name: INameS<'s>) -> String {
  match name {
    INameS::VarName(n) => panic!("implement: printable_name VarName"),
    INameS::TopLevelStructDeclaration(n) => n.name.0.to_string(),
    INameS::TopLevelInterfaceDeclaration(n) => n.name.0.to_string(),
    INameS::FunctionDeclaration(n) => match n {
      IFunctionDeclarationNameS::FunctionName(fn_name) => format!("{}: {}", code_map(fn_name.code_location), fn_name.name.0),
      _ => panic!("implement: printable_name FunctionDeclaration other"),
    },
    _ => panic!("implement: printable_name other"),
  }
}

fn printable_kind_name(kind: KindT) -> String {
  panic!("Unimplemented: printable_kind_name");
}

fn printable_id<'s, 't>(id: IdT<'s, 't>) -> String {
  panic!("Unimplemented: printable_id");
}

fn printable_var_name<'s, 't>(name: IVarNameT<'s, 't>) -> String {
  match name {
    IVarNameT::CodeVar(n) => n.name.0.to_string(),
    _ => panic!("implement: printable_var_name other"),
  }
}

fn get_file(function_a: FunctionA) -> FileCoordinate {
  panic!("Unimplemented: get_file");
}

fn humanize_rejection_reason<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, verbose: bool, code_map: &dyn Fn(CodeLocationS<'s>) -> String, lines_between: &dyn Fn(CodeLocationS<'s>, CodeLocationS<'s>) -> Vec<RangeS<'s>>, line_range_containing: &dyn Fn(CodeLocationS<'s>) -> RangeS<'s>, line_containing: &dyn Fn(CodeLocationS<'s>) -> String, invocation_range: &Vec<RangeS<'s>>, reason: &IFindFunctionFailureReason<'s, 't>) -> String {
  match reason {
    IFindFunctionFailureReason::FindFunctionResolveFailure { reason } => {
      humanize_resolving_error(scout_arena, typing_interner, verbose, code_map, lines_between, line_range_containing, line_containing, reason)
    }
    IFindFunctionFailureReason::SpecificParamDoesntSend { index, argument, parameter } => {
      format!(
        " Index {} argument {} can't be given to expected parameter {}",
        index,
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *argument }))),
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *parameter }))))
    }
    IFindFunctionFailureReason::InferFailure { reason } => {
      humanize_candidate_and_failed_solve(scout_arena, typing_interner, code_map, lines_between, line_range_containing, line_containing, reason)
    }
    IFindFunctionFailureReason::WrongNumberOfArguments { supplied, expected } => {
      format!("Number of params doesn't match! Supplied {} but function takes {}", supplied, expected)
    }
    other => panic!("Unimplemented humanize_rejection_reason arm: {:?}", other),
  }
  
}

pub fn humanize_rule_error<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, code_map: &dyn Fn(CodeLocationS<'s>) -> String, lines_between: &dyn Fn(CodeLocationS<'s>, CodeLocationS<'s>) -> Vec<RangeS<'s>>, line_range_containing: &dyn Fn(CodeLocationS<'s>) -> RangeS<'s>, line_containing: &dyn Fn(CodeLocationS<'s>) -> String, error: ITypingPassSolverError<'s, 't>) -> String {
  match error {
    ITypingPassSolverError::IsaFailed { .. } => {
      panic!("implement: humanize_rule_error IsaFailed");
      // "Kind " + humanizeTemplata(codeMap, KindTemplataT(sub)) + " does not implement interface " + humanizeTemplata(codeMap, KindTemplataT(suuper))
    }
    ITypingPassSolverError::BadIsaSubKind { kind } => {
      format!("Kind {} cannot be a sub-kind.",
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Kind(typing_interner.alloc(KindTemplataT { kind }))))
    }
    ITypingPassSolverError::CantGetComponentsOfPlaceholderPrototype { .. } => {
      panic!("implement: humanize_rule_error CantGetComponentsOfPlaceholderPrototype");
      // "Can't get components of placeholder."
    }
    ITypingPassSolverError::ReturnTypeConflict { .. } => {
      panic!("implement: humanize_rule_error ReturnTypeConflict");
      // "Found function: " + humanizeId(codeMap, actualPrototype.id, None) + " which returns " + humanizeTemplata(codeMap, CoordTemplataT(actualPrototype.returnType)) + " but expected return type of " + humanizeTemplata(codeMap, CoordTemplataT(expectedReturnType))
    }
    ITypingPassSolverError::CantShareMutable { .. } => {
      panic!("implement: humanize_rule_error CantShareMutable");
      // "Can't share a mutable kind: " + humanizeTemplata(codeMap, KindTemplataT(kind))
    }
    ITypingPassSolverError::BadIsaSuperKind { .. } => {
      panic!("implement: humanize_rule_error BadIsaSuperKind");
      // "Bad super kind in isa: " + humanizeTemplata(codeMap, KindTemplataT(kind))
    }
    ITypingPassSolverError::SendingNonIdenticalKinds { .. } => {
      panic!("implement: humanize_rule_error SendingNonIdenticalKinds");
      // "Sending non-identical kinds: " + humanizeTemplata(codeMap, CoordTemplataT(sendCoord)) + " and " + humanizeTemplata(codeMap, CoordTemplataT(receiveCoord))
    }
    ITypingPassSolverError::SendingNonCitizen { .. } => {
      panic!("implement: humanize_rule_error SendingNonCitizen");
      // "Sending non-struct non-interface Kind: " + humanizeTemplata(codeMap, KindTemplataT(kind))
    }
    ITypingPassSolverError::CantCheckPlaceholder { .. } => {
      panic!("implement: humanize_rule_error CantCheckPlaceholder");
      // "Cant check a placeholder!"
    }
    ITypingPassSolverError::CouldntFindFunction { .. } => {
      panic!("implement: humanize_rule_error CouldntFindFunction");
      // "Couldn't find function to call: " +
      //   humanizeFindFunctionFailure(
      //     false, codeMap, linesBetween, lineRangeContaining, lineContaining, range, fff)
    }
    ITypingPassSolverError::CouldntResolveKind { .. } => {
      panic!("implement: humanize_rule_error CouldntResolveKind");
      // "Couldn't find type: " + humanizeResolveFailure(false, codeMap, linesBetween, lineRangeContaining, lineContaining, rf)
    }
    ITypingPassSolverError::WrongNumberOfTemplateArgs { .. } => {
      panic!("implement: humanize_rule_error WrongNumberOfTemplateArgs");
      // if (expectedMinNumArgs == expectedMaxNumArgs) {
      //   "Wrong number of template args, expected " + expectedMinNumArgs + "."
      // } else {
      //   "Wrong number of template args, expected " + expectedMinNumArgs + " or " + expectedMaxNumArgs + "."
      // }
    }
    ITypingPassSolverError::LookupFailed { .. } => {
      panic!("implement: humanize_rule_error LookupFailed");
      // "Couldn't find anything named: " + humanizeImpreciseName(name)
    }
    ITypingPassSolverError::KindIsNotConcrete { kind } => {
      "Expected kind to be concrete, but was not. Kind: ".to_string() + &humanize_kind(scout_arena, typing_interner, code_map, kind, None)
    }
    ITypingPassSolverError::OneOfFailed { .. } => {
      panic!("implement: humanize_rule_error OneOfFailed");
      // "One-of rule failed."
    }
    ITypingPassSolverError::KindIsNotInterface { .. } => {
      panic!("implement: humanize_rule_error KindIsNotInterface");
      // "Expected kind to be interface, but was not. Kind: " + humanizeKind(codeMap, kind, None)
    }
    ITypingPassSolverError::CallResultIsntCallable { .. } => {
      panic!("implement: humanize_rule_error CallResultIsntCallable");
      // "Generic call result isn't callable: " + humanizeTemplata(codeMap, result)
    }
    ITypingPassSolverError::CallResultWasntExpectedType { .. } => {
      panic!("implement: humanize_rule_error CallResultWasntExpectedType");
      // "Expected an instantiation of " + humanizeTemplata(codeMap, expected) + " but got " + humanizeTemplata(codeMap, actual)
    }
    ITypingPassSolverError::OwnershipDidntMatch { .. } => {
      panic!("implement: humanize_rule_error OwnershipDidntMatch");
      // "Given type " + humanizeTemplata(codeMap, CoordTemplataT(coord)) + " doesn't have expected ownership " + humanizeOwnership(Conversions.unevaluateOwnership(expectedOwnership))
    }
    ITypingPassSolverError::ReceivingDifferentOwnerships { .. } => {
      panic!("implement: humanize_rule_error ReceivingDifferentOwnerships");
      // "Received conflicting ownerships: " +
      //   params.map({ case (rune, coord) =>
      //     humanizeRune(rune) + " = " + humanizeTemplata(codeMap, CoordTemplataT(coord))
      //   }).mkString(", ")
    }
    ITypingPassSolverError::NoAncestorsSatisfyCall { .. } => {
      panic!("implement: humanize_rule_error NoAncestorsSatisfyCall");
      // "No ancestors satisfy call: " +
      //   params.map({ case (rune, coord) =>
      //     humanizeRune(rune) + " = " + humanizeTemplata(codeMap, CoordTemplataT(coord))
      //   }).mkString(", ")
    }
    ITypingPassSolverError::KindIsNotStruct { .. } => {
      panic!("implement: humanize_rule_error KindIsNotStruct");
      // "Expected kind to be struct, but was not. Kind: " + humanizeKind(codeMap, kind, None)
    }
    ITypingPassSolverError::CouldntFindImpl { .. } => {
      panic!("implement: humanize_rule_error CouldntFindImpl");
      // "Couldn't find impl: " + fail
    }
    ITypingPassSolverError::CantSharePlaceholder { .. } => {
      panic!("implement: humanize_rule_error CantSharePlaceholder");
      // "Can't share a placeholder kind: " + humanizeTemplata(codeMap, KindTemplataT(kind))
    }
    ITypingPassSolverError::NoCommonAncestors { .. } => {
      panic!("implement: humanize_rule_error NoCommonAncestors");
      // "No common ancestors: " +
      //   params.map({ case (rune, coord) =>
      //     humanizeRune(rune) + " = " + humanizeTemplata(codeMap, CoordTemplataT(coord))
      //   }).mkString(", ")
    }
    ITypingPassSolverError::CantDetermineNarrowestKind { .. } => {
      panic!("implement: humanize_rule_error CantDetermineNarrowestKind");
      // "Can't determine narrowest kind among: " + kinds.map(humanizeKind(codeMap, _, None)).mkString(", ")
    }
    ITypingPassSolverError::FunctionDoesntHaveName { .. } => {
      panic!("implement: humanize_rule_error FunctionDoesntHaveName");
      // "Function doesn't have name: " + humanizeName(codeMap, name)
    }
    ITypingPassSolverError::InternalSolverError { range: _, err } => match err {
      ISolverError::SolverConflict(c) => {
        format!("Solver conflict on rune {}: was {} but now concluding {}",
          humanize_rune(c.rune),
          humanize_templata(scout_arena, typing_interner, code_map, c.previous_conclusion),
          humanize_templata(scout_arena, typing_interner, code_map, c.new_conclusion))
      }
      ISolverError::RuleError(inner) => {
        humanize_rule_error(scout_arena, typing_interner, code_map, lines_between, line_range_containing, line_containing, inner.err)
      }
      ISolverError::SolveIncomplete(_) => "Solve incomplete".to_string(),
    }
  }
}

pub fn humanize_candidate_and_failed_solve<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, code_map: &dyn Fn(CodeLocationS<'s>) -> String, lines_between: &dyn Fn(CodeLocationS<'s>, CodeLocationS<'s>) -> Vec<RangeS<'s>>, line_range_containing: &dyn Fn(CodeLocationS<'s>) -> RangeS<'s>, line_containing: &dyn Fn(CodeLocationS<'s>) -> String, result: &FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>) -> String {
  let (text, _line_begins) = solver_humanize_failed_solve(
    |loc| code_map(*loc),
    |a, b| lines_between(*a, *b),
    |loc| line_range_containing(*loc),
    |loc| line_containing(*loc),
    |rune| humanize_rune(rune),
    |t| humanize_templata(scout_arena, typing_interner, &|loc| code_map(loc), t),
    |err| humanize_rule_error(scout_arena, typing_interner, code_map, lines_between, line_range_containing, line_containing, *err),
    |rule: &IRulexSR<'s>| *rule.range(),
    |rule: &IRulexSR<'s>| rule.rune_usages().iter().map(|u| (u.rune, u.range)).collect(),
    |rule: &IRulexSR<'s>| rule.rune_usages().iter().map(|u| u.rune).collect(),
    |rule: &IRulexSR<'s>| humanize_rule(rule),
    result,
  );
  text
}

pub fn humanize_candidate<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, code_map: &dyn Fn(CodeLocationS<'s>) -> String, line_range_containing: &dyn Fn(CodeLocationS<'s>) -> RangeS<'s>, candidate: &ICalleeCandidate<'s, 't>) -> String {
  match candidate {
    ICalleeCandidate::Header(_) => {
      panic!("implement: humanize_candidate HeaderCalleeCandidate");
      // humanizeId(codeMap, header.id, None)
    }
    ICalleeCandidate::PrototypeTemplata(p) => {
      format!("{}:\n", humanize_name(scout_arena, typing_interner, code_map, p.prototype_t.id.local_name, None))
    }
    ICalleeCandidate::Function(f) => {
      let begin = line_range_containing(f.ft.function.range.begin).begin;
      format!("{}:\n{:?}\n", code_map(begin), line_range_containing(begin).begin)
    }
  }
}

pub fn humanize_templata<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, code_map: &dyn Fn(CodeLocationS<'s>) -> String, templata: ITemplataT<'s, 't>) -> String {
  match templata {
    ITemplataT::RuntimeSizedArrayTemplate(_) => "Array".to_string(),
    ITemplataT::StaticSizedArrayTemplate(_) => "StaticArray".to_string(),
    ITemplataT::InterfaceDefinition(i) => {
      i.origin_interface.name.name.0.to_string()
    }
    ITemplataT::StructDefinition(s) => {
      match s.origin_struct.name {
        crate::postparsing::names::IStructDeclarationNameS::TopLevelStructDeclarationName(n) => n.name.0.to_string(),
        other => panic!("implement: humanize_templata StructDefinition {:?}", other),
      }
    }
    ITemplataT::Variability(variability) => match variability.variability {
      VariabilityT::Final => "final".to_string(),
      VariabilityT::Varying => "vary".to_string(),
    },
    ITemplataT::Integer(value) => {
      value.to_string()
    }
    ITemplataT::Mutability(mutability) => match mutability.mutability {
      MutabilityT::Mutable => "mut".to_string(),
      MutabilityT::Immutable => "imm".to_string(),
    },
    ITemplataT::Ownership(ownership) => match ownership.ownership {
      OwnershipT::Own => "own".to_string(),
      OwnershipT::Borrow => "borrow".to_string(),
      OwnershipT::Weak => "weak".to_string(),
      OwnershipT::Share => "share".to_string(),
    },
    ITemplataT::Prototype(prototype) => {
      humanize_id(scout_arena, typing_interner, code_map, prototype.prototype.id, None)
    }
    ITemplataT::Coord(coord_templata) => humanize_coord(scout_arena, typing_interner, code_map, coord_templata.coord),
    ITemplataT::Kind(kind_templata) => humanize_kind(scout_arena, typing_interner, code_map, kind_templata.kind, None),
    ITemplataT::CoordList(coords) => {
      let inner = coords.coords.iter()
        .map(|c| humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *c }))))
        .collect::<Vec<_>>().join(", ");
      format!("({})", inner)
    }
    ITemplataT::String(value) => {
      panic!("implement: humanize_templata String");
      // "\"" + value + "\""
    }
    ITemplataT::Placeholder(_) => {
      panic!("implement: humanize_templata Placeholder");
      // tyype match {
      //   case CoordTemplataType() => "$" + humanizeId(codeMap, id, None)
      //   case _ => humanizeTemplataType(tyype) + "$" + humanizeId(codeMap, id, None)
      // }
    }
    _ => {
      panic!("implement: humanize_templata other");
      // vimpl(other)
    }
  }
}

fn humanize_coord<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, code_map: &dyn Fn(CodeLocationS<'s>) -> String, coord: CoordT<'s, 't>) -> String {
  let CoordT { ownership, region, kind } = coord;
  let ownership_str = match ownership {
    OwnershipT::Own => "",
    OwnershipT::Share => "",
    OwnershipT::Borrow => "&",
    OwnershipT::Weak => "&&",
  };
  let kind_str = humanize_kind(scout_arena, typing_interner, code_map, kind, Some(region));
  format!("{}{}", ownership_str, kind_str)
}

fn humanize_kind<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, code_map: &dyn Fn(CodeLocationS<'s>) -> String, kind: KindT<'s, 't>, containing_region: Option<RegionT>) -> String {
  match kind {
    KindT::Int(IntT { bits }) => format!("i{}", bits),
    KindT::Bool(_) => "bool".to_string(),
    KindT::KindPlaceholder(name) => format!("Kind${}", humanize_id(scout_arena, typing_interner, code_map, name.id, None)),
    KindT::Str(_) => "str".to_string(),
    KindT::Never(_) => "never".to_string(),
    KindT::Void(_) => "void".to_string(),
    KindT::Float(_) => "float".to_string(),
    KindT::OverloadSet(s) => format!("(overloads: {})",
      humanize_imprecise_name(*s.name)),
    KindT::Interface(name) => humanize_id(scout_arena, typing_interner, code_map, name.id, containing_region),
    KindT::Struct(name) => humanize_id(scout_arena, typing_interner, code_map, name.id, containing_region),
    KindT::RuntimeSizedArray(rsa) => {
      format!("Array<{}, {}>",
        humanize_templata(scout_arena, typing_interner, code_map, rsa.mutability()),
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: rsa.element_type() }))))
    }
    KindT::StaticSizedArray(ssa) => {
      format!("StaticArray<{}, {}, {}, {}>",
        humanize_templata(scout_arena, typing_interner, code_map, ssa.size()),
        humanize_templata(scout_arena, typing_interner, code_map, ssa.mutability()),
        humanize_templata(scout_arena, typing_interner, code_map, ssa.variability()),
        humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: ssa.element_type() }))))
    }
  }
}

pub fn humanize_id<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, code_map: &dyn Fn(CodeLocationS<'s>) -> String, name: IdT<'s, 't>, containing_region: Option<RegionT>) -> String
where 's: 't,
{
  let prefix = if !name.init_steps.is_empty() {
    name.init_steps.iter().map(|n| humanize_name(scout_arena, typing_interner, code_map, *n, None)).collect::<Vec<_>>().join(".") + "."
  } else {
    "".to_string()
  };
  prefix + &humanize_name(scout_arena, typing_interner, code_map, name.local_name, containing_region)
}

pub fn humanize_name<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, code_map: &dyn Fn(CodeLocationS<'s>) -> String, name: INameT<'s, 't>, containing_region: Option<RegionT>) -> String {
  match name {
    INameT::AnonymousSubstructConstructor(n) => panic!("implement: humanize_name AnonymousSubstructConstructor"),
    INameT::AnonymousSubstructConstructorTemplate(n) => panic!("implement: humanize_name AnonymousSubstructConstructorTemplate"),
    INameT::Self_(_) => "self".to_string(),
    INameT::OverrideDispatcherTemplate(n) => panic!("implement: humanize_name OverrideDispatcherTemplate"),
    INameT::OverrideDispatcher(n) => panic!("implement: humanize_name OverrideDispatcher"),
    INameT::Iterator(n) => panic!("implement: humanize_name Iterator"),
    INameT::Iterable(n) => panic!("implement: humanize_name Iterable"),
    INameT::IterationOption(n) => panic!("implement: humanize_name IterationOption"),
    INameT::ImplTemplate(n) => panic!("implement: humanize_name ImplTemplate"),
    INameT::ForwarderFunction(n) => panic!("implement: humanize_name ForwarderFunction"),
    INameT::ForwarderFunctionTemplate(n) => panic!("implement: humanize_name ForwarderFunctionTemplate"),
    INameT::MagicParam(n) => panic!("implement: humanize_name MagicParam"),
    INameT::ClosureParam(n) => panic!("implement: humanize_name ClosureParam"),
    INameT::ConstructingMember(n) => panic!("implement: humanize_name ConstructingMember"),
    INameT::TypingPassBlockResultVar(n) => panic!("implement: humanize_name TypingPassBlockResultVar"),
    INameT::TypingPassFunctionResultVar(n) => panic!("implement: humanize_name TypingPassFunctionResultVar"),
    INameT::TypingPassTemporaryVar(n) => panic!("implement: humanize_name TypingPassTemporaryVar"),
    INameT::FunctionBoundTemplate(n) => n.human_name.0.to_string(),
    INameT::LambdaCallFunctionTemplate(n) => {
      format!("λF:{}", code_map(n.code_location))
    }
    INameT::LambdaCitizenTemplate(n) => {
      format!("λC:{}", code_map(n.code_location))
    }
    INameT::LambdaCallFunction(n) => {
      let params_str = n.parameters.iter()
        .map(|c| humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *c }))))
        .collect::<Vec<_>>().join(", ");
      format!("{}{}({})",
        humanize_name(scout_arena, typing_interner, code_map, INameT::LambdaCallFunctionTemplate(n.template), None),
        humanize_generic_args(scout_arena, typing_interner, code_map, n.template_args, None),
        params_str)
    }
    INameT::FunctionBound(n) => {
      let params_str = n.parameters.iter()
        .map(|c| humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *c }))))
        .collect::<Vec<_>>().join(", ");
      format!("{}{}({})",
        humanize_name(scout_arena, typing_interner, code_map, INameT::FunctionBoundTemplate(n.template), None),
        humanize_generic_args(scout_arena, typing_interner, code_map, n.template_args, None),
        params_str)
    }
    INameT::KindPlaceholder(n) => humanize_name(scout_arena, typing_interner, code_map, INameT::KindPlaceholderTemplate(n.template), None),
    INameT::KindPlaceholderTemplate(n) => {
      humanize_rune(n.rune)
    }
    INameT::CodeVar(n) => n.name.0.to_string(),
    INameT::LambdaCitizen(n) => {
      format!("{}<>", humanize_name(scout_arena, typing_interner, code_map, INameT::LambdaCitizenTemplate(n.template), None))
    }
    INameT::FunctionTemplate(n) => n.human_name.0.to_string(),
    INameT::ExternFunction(n) => n.human_name.0.to_string(),
    INameT::Function(n) => {
      humanize_name(scout_arena, typing_interner, code_map, INameT::FunctionTemplate(n.template), None) +
        &humanize_generic_args(scout_arena, typing_interner, code_map, n.template_args, containing_region) +
        &(if !n.parameters.is_empty() {
          "(".to_string() + &n.parameters.iter().map(|p| humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *p })))).collect::<Vec<_>>().join(", ") + ")"
        } else {
          "".to_string()
        })
    }
    INameT::Struct(sn) => {
      let template_name = match sn.template {
        IStructTemplateNameT::LambdaCitizenTemplate(n) => INameT::LambdaCitizenTemplate(n),
        IStructTemplateNameT::StructTemplate(n) => INameT::StructTemplate(n),
        IStructTemplateNameT::AnonymousSubstructTemplate(n) => INameT::AnonymousSubstructTemplate(n),
      };
      humanize_name(scout_arena, typing_interner, code_map, template_name, None) +
        &humanize_generic_args(scout_arena, typing_interner, code_map, sn.template_args, containing_region)
    }
    INameT::Interface(sn) => {
      humanize_name(scout_arena, typing_interner, code_map, INameT::InterfaceTemplate(sn.template), None) +
        &humanize_generic_args(scout_arena, typing_interner, code_map, sn.template_args, containing_region)
    }
    INameT::AnonymousSubstruct(n) => panic!("implement: humanize_name AnonymousSubstruct"),
    INameT::AnonymousSubstructTemplate(n) => panic!("implement: humanize_name AnonymousSubstructTemplate"),
    INameT::StructTemplate(n) => n.human_name.0.to_string(),
    INameT::InterfaceTemplate(n) => n.human_namee.0.to_string(),
    INameT::NonKindNonRegionPlaceholder(n) => {
      panic!("implement: humanize_name NonKindNonRegionPlaceholder");
      // humanizeRune(rune)
    }
    INameT::RuntimeSizedArray(_) => {
      panic!("implement: humanize_name RuntimeSizedArray");
      // "[]<" +
      //   (mutability match {
      //     case MutabilityTemplataT(ImmutableT) => "imm"
      //     case MutabilityTemplataT(MutableT) => "mut"
      //     case other => humanizeTemplata(codeMap, other)
      //   }) + ", " +
      //   humanizeTemplata(codeMap, CoordTemplataT(elementType))
    }
    INameT::StaticSizedArray(_) => {
      panic!("implement: humanize_name StaticSizedArray");
      // "[]<" +
      //   humanizeTemplata(codeMap, size) + ", "
      //   humanizeTemplata(codeMap, mutability) + ", "
      //   humanizeTemplata(codeMap, variability) + ">"
      //   humanizeTemplata(codeMap, CoordTemplataT(elementType))
    }
    INameT::PredictedFunctionTemplate(n) => {
      n.human_name.0.to_string()
    }
    INameT::PredictedFunction(n) => {
      let params_str = n.parameters.iter()
        .map(|c| humanize_templata(scout_arena, typing_interner, code_map, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT { coord: *c }))))
        .collect::<Vec<_>>().join(", ");
      format!("{}{}({})",
        humanize_name(scout_arena, typing_interner, code_map, INameT::PredictedFunctionTemplate(n.template), None),
        humanize_generic_args(scout_arena, typing_interner, code_map, n.template_args, None),
        params_str)
    }
    _ => panic!("implement: humanize_name other"),
  }
}

fn humanize_generic_args<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, code_map: &dyn Fn(CodeLocationS<'s>) -> String, template_args: &[ITemplataT<'s, 't>], containing_region: Option<RegionT>) -> String {
  if template_args.is_empty() {
    "".to_string()
  } else {
    let init = &template_args[..template_args.len() - 1];
    let last = template_args.last().unwrap();
    let last_str = match containing_region {
      None => humanize_templata(scout_arena, typing_interner, code_map, *last),
      Some(_) => "_".to_string(),
    };
    let parts = init.iter().map(|t| humanize_templata(scout_arena, typing_interner, code_map, *t))
      .chain(once(last_str))
      .collect::<Vec<_>>().join(", ");
    format!("<{}>", parts)
  }
}

pub fn humanize_signature<'s, 't>(scout_arena: &ScoutArena<'s>, typing_interner: &TypingInterner<'s, 't>, code_map: &dyn Fn(CodeLocationS<'s>) -> String, signature: SignatureT<'s, 't>) -> String {
  humanize_id(scout_arena, typing_interner, code_map, signature.id, None)
}

