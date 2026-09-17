use std::collections::HashMap;
use indexmap::IndexMap;
use crate::utils::range::RangeS;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::postparsing::ast::{GenericParameterS, LocationInDenizen};
use crate::postparsing::itemplatatype::{ITemplataType, CoordTemplataType};
use crate::postparsing::rules::rules::CoordSendSR;
use crate::postparsing::names::*;
use crate::postparsing::rules::rules::*;
use crate::typing::ast::ast::*;
use crate::typing::citizen::struct_compiler::{IResolveOutcome, ResolveFailure};
use crate::typing::compiler::Compiler;
use crate::typing::compiler_outputs::*;
use crate::typing::env::environment::*;
use crate::typing::env::i_env_entry::IEnvEntryT;
use crate::typing::hinputs_t::*;
use crate::typing::infer::compiler_solver::ITypingPassSolverError;
use crate::typing::names::names::*;
use crate::typing::templata::templata::*;
use crate::typing::types::types::*;
use crate::typing::templata_compiler::IBoundArgumentsSource;
use crate::solver::solver::{FailedSolve, ISolverError, RuleError, SolveIncomplete};
use crate::solver::simple_solver_state::SimpleSolverState;
use crate::typing::types::types::{ISubKindTT, ISuperKindTT};
use crate::typing::citizen::impl_compiler::IsParentResult;
use crate::typing::citizen::impl_compiler::IsntParent;
use crate::typing::overload_resolver::FindFunctionFailure;
use crate::typing::names::names::ImplBoundNameValT;
use crate::typing::names::names::IdValT;
use crate::typing::templata::templata::expect_integer;
use crate::typing::templata::templata::expect_mutability;
use crate::typing::templata::templata::expect_variability;
use std::collections::HashSet;
use std::marker::PhantomData;

/// Temporary state (see @TFITCX)
pub struct CompleteResolveSolve<'s, 't> {
    pub conclusions: IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>,
    pub rune_to_bound: &'t InstantiationBoundArgumentsT<'s, 't>,
}

/// Temporary state (see @TFITCX)
pub struct CompleteDefineSolve<'s, 't> {
    pub conclusions: IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>,
    pub rune_to_bound: &'t InstantiationBoundArgumentsT<'s, 't>,
}

#[derive(Debug)]
pub enum IConclusionResolveError<'s, 't> {
    CouldntFindImplForConclusionResolve {
        range: &'t [RangeS<'s>],
        fail: IsntParent<'s, 't>,
    },
    CouldntFindKindForConclusionResolve(ResolveFailure<'s, 't, KindT<'s, 't>>),
    CouldntFindFunctionForConclusionResolve {
        range: &'t [RangeS<'s>],
        fff: FindFunctionFailure<'s, 't>,
    },
    ReturnTypeConflictInConclusionResolve {
        range: &'t [RangeS<'s>],
        expected_return_type: CoordT<'s, 't>,
        actual: &'t PrototypeT<'s, 't>,
    },
}

#[derive(Debug)]
pub enum IResolvingError<'s, 't> {
    ResolvingSolveFailedOrIncomplete(FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>),
    ResolvingResolveConclusionError(Box<IConclusionResolveError<'s, 't>>),
}

#[derive(Debug)]
pub enum IDefiningError<'s, 't> {
    DefiningSolveFailedOrIncomplete(FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>),
    DefiningResolveConclusionError(IConclusionResolveError<'s, 't>),
}

#[derive(Copy, Clone)]
pub struct InferEnv<'s, 't> {
    pub original_calling_env: IInDenizenEnvironmentT<'s, 't>,
    pub parent_ranges: &'t [RangeS<'s>],
    pub call_location: LocationInDenizen<'s>,
    pub self_env: IEnvironmentT<'s, 't>,
    pub context_region: RegionT,
}

pub struct InitialSend<'s, 't> {
    pub sender_rune: RuneUsage<'s>,
    pub receiver_rune: RuneUsage<'s>,
    pub send_templata: ITemplataT<'s, 't>,
}

#[derive(Copy, Clone)]
pub struct InitialKnown<'s, 't> {
    pub rune: RuneUsage<'s>,
    pub templata: ITemplataT<'s, 't>,
}

// deleted: delegate trait removed per god-struct refactor (Compiler now holds all methods directly)

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn solve_for_defining(
        &self,
        envs: InferEnv<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        rules: &[IRulexSR<'s>],
        rune_to_type: &IndexMap<IRuneS<'s>, ITemplataType<'s>>,
        invocation_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        initial_knowns: &[InitialKnown<'s, 't>],
        initial_sends: &[InitialSend<'s, 't>],
        include_reachable_bounds_for_runes: &[IRuneS<'s>],
    ) -> Result<CompleteDefineSolve<'s, 't>, IDefiningError<'s, 't>> {
        let mut solver =
            self.make_solver_state(envs, coutputs, rules, rune_to_type, invocation_range, initial_knowns, initial_sends);
        match self.r#continue(envs, coutputs, &mut solver) {
            Ok(()) => {}
            Err(e) => return Err(IDefiningError::DefiningSolveFailedOrIncomplete(e)),
        }
        let conclusions =
            match self.interpret_results(rune_to_type, &mut solver) {
                Ok(conclusions) => conclusions,
                Err(f) => return Err(IDefiningError::DefiningSolveFailedOrIncomplete(f)),
            };
        match self.check_defining_conclusions_and_resolve(
            envs, coutputs, invocation_range, call_location, rules, include_reachable_bounds_for_runes, &conclusions,
        ) {
            Ok(instantiation_bound_args) => Ok(CompleteDefineSolve { conclusions, rune_to_bound: instantiation_bound_args }),
            Err(x) => Err(IDefiningError::DefiningResolveConclusionError(x)),
        }
    }

    // Per @DRSINI, defaults are added incrementally for unsolved runes rather than eagerly.
    //
    // ⚠ Same MKRFA caller contract as make_solver_state above. Expression-level `rules` must have
    // RuneParentEnvLookupSR preprocessed into `initial_knowns` before this call (see
    // OverloadResolver.scala:311-325). Unenforced; violations are silent.
    pub fn solve_for_resolving(
        &self,
        envs: InferEnv<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        rules: &[IRulexSR<'s>],
        rune_to_type: &IndexMap<IRuneS<'s>, ITemplataType<'s>>,
        invocation_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        generic_parameters: &'s [&'s GenericParameterS<'s>],
        initial_knowns: &[InitialKnown<'s, 't>],
        initial_sends: &[InitialSend<'s, 't>],
    ) -> Result<Result<CompleteResolveSolve<'s, 't>, IResolvingError<'s, 't>>, ICompileErrorT<'s, 't>> {
        let mut solver =
            self.make_solver_state(envs, coutputs, rules, rune_to_type, invocation_range, initial_knowns, initial_sends);
        match self.incrementally_solve(envs, coutputs, &mut solver, |_coutputs, solver_state| {
            match self.get_first_unsolved_identifying_rune(generic_parameters, |rune| solver_state.get_conclusion(&rune).is_some()) {
                None => false,
                Some((generic_param, _index)) => {
                    match &generic_param.default {
                        Some(default_rules) => {
                            let default_rule_vec: Vec<IRulexSR<'s>> = default_rules.rules.iter().map(|r| **r).collect();
                            let new_runes: indexmap::IndexSet<IRuneS<'s>> =
                                default_rules.rune_to_type.iter().map(|(k, _)| *k).collect();
                            solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(
                                false, vec![], IndexMap::new(), default_rule_vec, new_runes
                            ).unwrap();
                            true
                        }
                        None => false,
                    }
                }
            }
        }) {
            Err(f) => return Ok(Err(IResolvingError::ResolvingSolveFailedOrIncomplete(f))),
            Ok(true) => {}
            Ok(false) => {}
        }
        self.check_resolving_conclusions_and_resolve(
            envs, coutputs, invocation_range, call_location, rune_to_type, rules, &[], &mut solver)
    }

    pub fn partial_solve(
        &self,
        envs: InferEnv<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        rules: &[IRulexSR<'s>],
        rune_to_type: &IndexMap<IRuneS<'s>, ITemplataType<'s>>,
        invocation_range: &[RangeS<'s>],
        initial_knowns: &[InitialKnown<'s, 't>],
        initial_sends: &[InitialSend<'s, 't>],
    ) -> Result<IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>, FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>> {
        let mut solver_state =
            self.make_solver_state(envs, coutputs, rules, rune_to_type, invocation_range, initial_knowns, initial_sends);
        match self.r#continue(envs, coutputs, &mut solver_state) {
            Ok(()) => {}
            Err(e) => return Err(e),
        }
        Ok(solver_state.userify_conclusions().into_iter().collect())
    }

    // Per @ECSIIOSZ, each call-site in source is resolved by a fresh SimpleSolverState built here;
    // the caller is responsible for the per-call-site setup contract (MKRFA preprocessing, SROACSD
    // filtering, CSSNCE env threading, DRSINI incremental defaults).
    // ⚠ CALLER CONTRACT: if `rules` come from an expression-level postparser output,
    // they must have had RuneParentEnvLookupSR rules stripped into `initial_knowns` before
    // being passed here (the MKRFA contract — see OverloadResolver.scala:311-325 for the
    // canonical fold). This is NOT enforced at the type level; violations produce silent
    // "couldn't solve" errors at dependent rules rather than faulting at the MKRFA rule.
    // See docs/refactor-thoughts/mkrfa-protocol-leak.md for the queued enforcement work
    // (extract shared helper + replace the no-op handler with vwat).
    pub fn make_solver_state(
        &self,
        envs: InferEnv<'s, 't>,
        state: &mut CompilerOutputs<'s, 't>,
        initial_rules: &[IRulexSR<'s>],
        initial_rune_to_type: &IndexMap<IRuneS<'s>, ITemplataType<'s>>,
        invocation_range: &[RangeS<'s>],
        initial_knowns: &[InitialKnown<'s, 't>],
        initial_sends: &[InitialSend<'s, 't>],
    ) -> SimpleSolverState<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>> {
        let mut rune_to_type = initial_rune_to_type.clone();
        for send in initial_sends {
            rune_to_type.insert(send.sender_rune.rune, ITemplataType::CoordTemplataType(CoordTemplataType {}));
        }
        let mut rules: Vec<IRulexSR<'s>> = initial_rules.to_vec();
        for send in initial_sends {
            rules.push(IRulexSR::CoordSend(CoordSendSR {
                range: send.receiver_rune.range,
                sender_rune: send.sender_rune,
                receiver_rune: send.receiver_rune,
            }));
        }
        let mut already_known: IndexMap<IRuneS<'s>, ITemplataT<'s, 't>> = IndexMap::new();
        for known in initial_knowns {
            if self.opts.global_options.sanity_check {
                self.sanity_check_conclusion(&envs, state, known.rune.rune, known.templata);
            }
            already_known.insert(known.rune.rune, known.templata);
        }
        for send in initial_sends {
            if self.opts.global_options.sanity_check {
                self.sanity_check_conclusion(&envs, state, send.sender_rune.rune, send.send_templata);
            }
            already_known.insert(send.sender_rune.rune, send.send_templata);
        }
        self.make_solver_state_solver(
            invocation_range.to_vec(), envs, state, rules, rune_to_type, already_known)
    }

    pub fn r#continue(
        &self,
        envs: InferEnv<'s, 't>,
        state: &mut CompilerOutputs<'s, 't>,
        solver: &mut SimpleSolverState<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>>,
    ) -> Result<(), FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>> {
        //   compilerSolver.continue(envs, state, solver)
        self.continue_solver(envs, state, solver)
    }

    pub fn check_resolving_conclusions_and_resolve(
        &self,
        envs: InferEnv<'s, 't>,
        state: &mut CompilerOutputs<'s, 't>,
        ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        rune_to_type: &IndexMap<IRuneS<'s>, ITemplataType<'s>>,
        rules: &[IRulexSR<'s>],
        include_reachable_bounds_for_runes: &[IRuneS<'s>],
        solver_state: &mut SimpleSolverState<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>>,
    ) -> Result<Result<CompleteResolveSolve<'s, 't>, IResolvingError<'s, 't>>, ICompileErrorT<'s, 't>> {
        let _steps_stream = solver_state.get_steps();
        let conclusions: IndexMap<IRuneS<'s>, ITemplataT<'s, 't>> =
            solver_state.userify_conclusions().into_iter().collect();

        let all_runes: HashSet<IRuneS<'s>> =
            rune_to_type.keys().copied().chain(solver_state.get_all_runes().into_iter()).collect();

        // During the solve, we postponed resolving structs and interfaces, see SFWPRL.
        // Caller should remember to do that!
        if all_runes.iter().any(|r| !conclusions.contains_key(r)) {
            return Ok(Err(IResolvingError::ResolvingSolveFailedOrIncomplete(
                FailedSolve {
                    steps: solver_state.get_steps(),
                    conclusions: solver_state.get_conclusions().into_iter().collect(),
                    unsolved_rules: solver_state.get_unsolved_rules(),
                    unsolved_runes: solver_state.get_unsolved_runes(),
                    error: ISolverError::SolveIncomplete(SolveIncomplete { _phantom: PhantomData }),
                })));
        }

        let citizens_from_calls: Vec<KindT<'s, 't>> =
            rules.iter()
                .filter_map(|rule| match rule {
                    IRulexSR::Call(call_sr) => Some(call_sr.result_rune.rune),
                    _ => None,
                })
                .map(|rune| *conclusions.get(&rune).unwrap())
                .filter_map(|templata| match templata {
                    ITemplataT::Kind(k) => {
                        match k.kind {
                            KindT::Struct(_) | KindT::Interface(_) => Some(k.kind),
                            _ => None,
                        }
                    }
                    ITemplataT::Coord(c) => {
                        match c.coord.kind {
                            KindT::Struct(_) | KindT::Interface(_) => Some(c.coord.kind),
                            _ => None,
                        }
                    }
                    _ => None,
                })
                .collect();

        let include_reachable_bounds_for_runes_with_citizens: Vec<(IRuneS<'s>, KindT<'s, 't>)> =
            include_reachable_bounds_for_runes.iter()
                .map(|rune| (*rune, *conclusions.get(rune).unwrap()))
                .filter_map(|(rune, templata)| match templata {
                    ITemplataT::Kind(k) => {
                        match k.kind {
                            KindT::Struct(_) | KindT::Interface(_) => Some((rune, k.kind)),
                            _ => None,
                        }
                    }
                    ITemplataT::Coord(c) => {
                        match c.coord.kind {
                            KindT::Struct(_) | KindT::Interface(_) => Some((rune, c.coord.kind)),
                            _ => None,
                        }
                    }
                    _ => None,
                })
                .filter(|(_rune, citizen)| citizens_from_calls.contains(citizen))
                .collect();

        let mut reachable_bounds: Vec<(IRuneS<'s>, &'t InstantiationReachableBoundArgumentsT<'s, 't>)> = Vec::new();
        for (rune, citizen) in include_reachable_bounds_for_runes_with_citizens.into_iter() {
            let citizen_tt = match citizen {
                KindT::Struct(s) => ICitizenTT::Struct(s),
                KindT::Interface(i) => ICitizenTT::Interface(i),
                _ => panic!("implement: reachableBounds — unexpected citizen kind"),
            };
            let reachable = self.get_reachable_bounds(
                self.opts.global_options.sanity_check,
                envs.original_calling_env.denizen_template_id(),
                state,
                citizen_tt,
            );
            let mut citizen_rune_to_reachable_prototype: Vec<(IRuneS<'s>, PrototypeT<'s, 't>)> = vec![];
            for (citizen_rune, caller_placeholdered_citizen_bound) in reachable.citizen_rune_to_reachable_prototype.iter() {
                let return_coord = caller_placeholdered_citizen_bound.return_type;
                let param_coords = caller_placeholdered_citizen_bound.param_types();
                let func_name = IFunctionNameT::try_from(caller_placeholdered_citizen_bound.id.local_name)
                    .unwrap()
                    .template()
                    .human_name();
                let func_success = match self.resolve_function(
                    envs.original_calling_env, state, ranges, call_location,
                    func_name, param_coords, envs.context_region, true,
                )? {
                    Err(e) => return Ok(Err(IResolvingError::ResolvingResolveConclusionError(Box::new(
                        IConclusionResolveError::CouldntFindFunctionForConclusionResolve { range: self.typing_interner.alloc_slice_copy(ranges), fff: e }
                    )))),
                    Ok(x) => x,
                };
                if func_success.prototype.return_type != return_coord {
                    return Ok(Err(IResolvingError::ResolvingResolveConclusionError(Box::new(
                        IConclusionResolveError::ReturnTypeConflictInConclusionResolve { range: self.typing_interner.alloc_slice_copy(ranges), expected_return_type: return_coord, actual: func_success.prototype }
                    ))));
                }
                // citizenRune -> funcSuccess.prototype
                citizen_rune_to_reachable_prototype.push((*citizen_rune, *func_success.prototype));
            }
            let result: &'t InstantiationReachableBoundArgumentsT<'s, 't> = self.typing_interner.alloc(InstantiationReachableBoundArgumentsT {
                citizen_rune_to_reachable_prototype: self.typing_interner.alloc_index_map_from_iter(
                    citizen_rune_to_reachable_prototype.into_iter()),
            });
            reachable_bounds.push((rune, result));
        }

        // Per IIIOZ: `import_reachable_bounds` only does lookups, not iteration-into-output, so a transient HashMap is fine here.
        let reachable_bounds_map: HashMap<IRuneS<'s>, &'t InstantiationReachableBoundArgumentsT<'s, 't>> =
            reachable_bounds.iter().copied().collect();
        let env_with_conclusions = self.import_reachable_bounds(envs.original_calling_env, &reachable_bounds_map);

        // Check all template calls
        for rule in rules.iter() {
            match rule {
                IRulexSR::Call(call_sr) => {
                    let env_with_conclusions_in_denizen: IInDenizenEnvironmentT<'s, 't> =
                        IInDenizenEnvironmentT::General(env_with_conclusions);
                    match self.resolve_template_call_conclusion(
                        env_with_conclusions_in_denizen, state, ranges, call_location, *call_sr, &conclusions)
                    {
                        Ok(()) => {}
                        Err(e) => {
                            let rf = self.typing_interner.alloc(e);
                            return Ok(Err(IResolvingError::ResolvingSolveFailedOrIncomplete(
                                FailedSolve {
                                    steps: solver_state.get_steps(),
                                    conclusions: solver_state.get_conclusions().into_iter().collect(),
                                    unsolved_rules: solver_state.get_unsolved_rules(),
                                    unsolved_runes: solver_state.get_unsolved_runes(),
                                    error: ISolverError::RuleError(RuleError {
                                        err: ITypingPassSolverError::CouldntResolveKind { rf },
                                        _phantom: PhantomData,
                                    }),
                                })));
                        }
                    }
                }
                _ => {}
            }
        }

        let env_with_conclusions_in_denizen: IInDenizenEnvironmentT<'s, 't> =
            IInDenizenEnvironmentT::General(env_with_conclusions);
        let mut runes_and_prototypes: Vec<(IRuneS<'s>, &'t PrototypeT<'s, 't>)> = vec![];
        for rule in rules.iter() {
            match rule {
                IRulexSR::Resolve(r) => {
                    match self.resolve_function_call_conclusion(env_with_conclusions_in_denizen, state, ranges, call_location, *r, &conclusions, envs.context_region)? {
                        Ok(x) => runes_and_prototypes.push(x),
                        Err(e) => return Ok(Err(IResolvingError::ResolvingResolveConclusionError(Box::new(e)))),
                    }
                }
                _ => {}
            }
        }
        {
            let mut seen: HashSet<IRuneS<'s>> = HashSet::new();
            for (rune, _) in runes_and_prototypes.iter() {
                if !seen.insert(*rune) {
                    panic!("vwat: duplicate rune in runesAndPrototypes");
                }
            }
        }

        let runes_and_impls: Vec<(IRuneS<'s>, IdT<'s, 't>)> =
            rules.iter().filter_map(|rule| match rule {
                IRulexSR::CallSiteCoordIsa(r) => {
                    match self.resolve_impl_conclusion(env_with_conclusions_in_denizen, state, ranges, call_location, *r, &conclusions) {
                        Ok(x) => Some(x),
                        Err(e) => panic!("implement: ResolvingResolveConclusionError wrapping in checkResolvingConclusionsAndResolve"),
                    }
                }
                _ => None,
            }).collect();
        {
            let mut seen: HashSet<IRuneS<'s>> = HashSet::new();
            for (rune, _) in runes_and_impls.iter() {
                if !seen.insert(*rune) {
                    panic!("vwat: duplicate rune in runesAndImpls");
                }
            }
        }

        let instantiation_bound_args = self.typing_interner.alloc(
            InstantiationBoundArgumentsT {
                rune_to_bound_prototype: self.typing_interner.alloc_index_map_from_iter(
                    runes_and_prototypes.into_iter().map(|(k, v)| (k, *v))),
                rune_to_citizen_rune_to_reachable_prototype: self.typing_interner.alloc_index_map_from_iter(
                    reachable_bounds.into_iter()
                        .filter(|(_, v)| !v.citizen_rune_to_reachable_prototype.is_empty())),
                rune_to_bound_impl: self.typing_interner.alloc_index_map_from_iter(
                    runes_and_impls.into_iter()),
            });

        Ok(Ok(CompleteResolveSolve {
            conclusions,
            rune_to_bound: instantiation_bound_args,
        }))
    }

    pub fn interpret_results(
        &self,
        rune_to_type: &IndexMap<IRuneS<'s>, ITemplataType<'s>>,
        solver_state: &mut SimpleSolverState<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>>,
    ) -> Result<IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>, FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>> {
        let conclusions: IndexMap<IRuneS<'s>, ITemplataT<'s, 't>> = solver_state.userify_conclusions().into_iter().collect();
        let mut all_runes: HashSet<IRuneS<'s>> = rune_to_type.keys().cloned().collect();
        all_runes.extend(solver_state.get_all_runes());
        // During the solve, we postponed resolving structs and interfaces, see SFWPRL.
        // Caller should remember to do that!
        if all_runes.iter().any(|r| !conclusions.contains_key(r)) {
            Err(
                FailedSolve {
                    steps: solver_state.get_steps(),
                    conclusions: solver_state.get_conclusions().into_iter().collect(),
                    unsolved_rules: solver_state.get_unsolved_rules(),
                    unsolved_runes: solver_state.get_unsolved_runes(),
                    error: ISolverError::SolveIncomplete(SolveIncomplete { _phantom: PhantomData }),
                })
        } else {
            Ok(conclusions)
        }
    }

    // Counter to @BDPFWDZ: this harvests bound prototypes from citizen-typed param inner envs
    // for the caller to push into its near-env. Pull-aligned replacement is to walk the citizen's
    // env at lookup time instead.
    pub fn check_defining_conclusions_and_resolve(
        &self,
        envs: InferEnv<'s, 't>,
        state: &mut CompilerOutputs<'s, 't>,
        invocation_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        initial_rules: &[IRulexSR<'s>],
        include_reachable_bounds_for_runes: &[IRuneS<'s>],
        conclusions: &IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>,
    ) -> Result<&'t InstantiationBoundArgumentsT<'s, 't>, IConclusionResolveError<'s, 't>> {
        let reachable_bounds: HashMap<IRuneS<'s>, &'t InstantiationReachableBoundArgumentsT<'s, 't>> =
            include_reachable_bounds_for_runes
                .iter()
                .map(|rune| {
                    let templata = conclusions.get(rune).unwrap();
                    let maybe_mentioned_kind =
                        match templata {
                            ITemplataT::Kind(KindTemplataT { kind }) => Some(*kind),
                            ITemplataT::Coord(CoordTemplataT { coord: CoordT { kind, .. } }) => Some(*kind),
                            _ => None,
                        };
                    let maybe_id_and_template_id: Option<(IdT<'s, 't>, IdT<'s, 't>)> =
                        match maybe_mentioned_kind {
                            Some(KindT::Struct(s)) => Some((s.id, self.get_citizen_template(s.id))),
                            Some(KindT::Interface(i)) => Some((i.id, self.get_citizen_template(i.id))),
                            Some(_) => None,
                            None => None,
                        };
                    let citizen_rune_to_reachable_prototype = match maybe_id_and_template_id {
                        None => self.typing_interner.alloc_index_map(),
                        Some((id, template_id)) => {
                            let inner_env = state.get_inner_env_for_type(template_id);
                            let substituter =
                                self.get_placeholder_substituter(
                                    self.opts.global_options.sanity_check,
                                    envs.original_calling_env.denizen_template_id(),
                                    id,
                                    IBoundArgumentsSource::InheritBoundsFromTypeItself,
                                );
                            let entries: Vec<(IRuneS<'s>, PrototypeT<'s, 't>)> =
                                inner_env.templatas().name_to_entry.iter()
                                    .filter_map(|(name, entry)| {
                                        match (name, entry) {
                                            (INameT::Rune(rune_name), IEnvEntryT::Templata(ITemplataT::Prototype(proto_templata)))
                                                if matches!(proto_templata.prototype.id.local_name, INameT::FunctionBound(_)) =>
                                            {
                                                match proto_templata.prototype.id.local_name {
                                                    INameT::FunctionBound(fb) => {
                                                        let bound_name = self.typing_interner.intern_function_bound_name(FunctionBoundNameValT { template: fb.template, template_args: fb.template_args, parameters: fb.parameters });
                                                        let new_id = self.typing_interner.intern_id(IdValT { package_coord: proto_templata.prototype.id.package_coord, init_steps: proto_templata.prototype.id.init_steps, local_name: INameT::FunctionBound(bound_name) });
                                                        let prototype = self.typing_interner.intern_prototype(PrototypeValT { id: IdValT { package_coord: new_id.package_coord, init_steps: new_id.init_steps, local_name: new_id.local_name }, return_type: proto_templata.prototype.return_type });
                                                        let subst_prototype = substituter.substitute_for_prototype(state, prototype);
                                                        Some((rune_name.rune, *subst_prototype))
                                                    }
                                                    _ => unreachable!(),
                                                }
                                            }
                                            _ => None,
                                        }
                                    })
                                    .collect();
                            self.typing_interner.alloc_index_map_from_iter(entries.into_iter())
                        }
                    };
                    (*rune, &*self.typing_interner.alloc(InstantiationReachableBoundArgumentsT { citizen_rune_to_reachable_prototype }))
                })
                .collect();
        let environment_for_finalizing: &'t GeneralEnvironmentT<'s, 't> =
            self.import_conclusions_and_reachable_bounds(envs.original_calling_env, conclusions, &reachable_bounds);
        let env_for_resolve: IInDenizenEnvironmentT<'s, 't> =
            IInDenizenEnvironmentT::General(environment_for_finalizing);
        let instantiation_bound_args =
            match self.resolve_conclusions_for_define(
                env_for_resolve, state, invocation_range, call_location, envs.context_region, initial_rules, conclusions, &reachable_bounds) {
                Ok(c) => c,
                Err(e) => return Err(e),
            };
        Ok(instantiation_bound_args)
    }

    pub fn import_reachable_bounds(
        &self,
        original_calling_env: IInDenizenEnvironmentT<'s, 't>,
        reachable_bounds: &HashMap<IRuneS<'s>, &'t InstantiationReachableBoundArgumentsT<'s, 't>>,
    ) -> &'t GeneralEnvironmentT<'s, 't> {
        let new_id: &'t IdT<'s, 't> = self.typing_interner.alloc(original_calling_env.id());
        let new_entries: Vec<(INameT<'s, 't>, IEnvEntryT<'s, 't>)> =
            reachable_bounds.values()
                .flat_map(|rb| rb.citizen_rune_to_reachable_prototype.iter().map(|(_, proto)| proto))
                .enumerate()
                .map(|(index, reachable_bound)| -> (INameT<'s, 't>, IEnvEntryT<'s, 't>) {
                    let name = self.typing_interner.intern_reachable_prototype_name(ReachablePrototypeNameT { num: index as i32});
                    (INameT::ReachablePrototype(name), IEnvEntryT::Templata(ITemplataT::Prototype(self.typing_interner.alloc(PrototypeTemplataT { prototype: self.typing_interner.alloc(*reachable_bound) }))))
                })
                .collect();
        child_of(
            self.typing_interner,
            self.scout_arena,
            original_calling_env,
            original_calling_env.denizen_template_id(),
            new_id,
            new_entries,
        )
    }

    pub fn import_conclusions_and_reachable_bounds(
        &self,
        original_calling_env: IInDenizenEnvironmentT<'s, 't>,
        conclusions: &IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>,
        reachable_bounds: &HashMap<IRuneS<'s>, &'t InstantiationReachableBoundArgumentsT<'s, 't>>,
    ) -> &'t GeneralEnvironmentT<'s, 't> {
        // If this is the original calling env, in other words, if we're the original caller for
        // this particular solve, then lets add all of our templatas to the environment.
        let mut new_entries: Vec<(INameT<'s, 't>, IEnvEntryT<'s, 't>)> =
            conclusions
                .iter()
                .map(|(name_s, templata)| {
                    let rune_name = self.typing_interner.intern_rune_name(RuneNameT { rune: *name_s});
                    (INameT::Rune(rune_name), IEnvEntryT::Templata(*templata))
                })
                .collect();
        // These are the bounds we pulled in from the parameters, return type, impl sub citizen, etc.
        new_entries.extend(
            reachable_bounds.values()
                .flat_map(|rb| rb.citizen_rune_to_reachable_prototype.iter().map(|(_, proto)| proto))
                .enumerate()
                .map(|(index, reachable_bound)| -> (INameT<'s, 't>, IEnvEntryT<'s, 't>) {
                    let name = self.typing_interner.intern_reachable_prototype_name(ReachablePrototypeNameT { num: index as i32});
                    let entry = IEnvEntryT::Templata(ITemplataT::Prototype(self.typing_interner.alloc(PrototypeTemplataT { prototype: reachable_bound })));
                    (INameT::ReachablePrototype(name), entry)
                })
        );
        let new_id: &'t IdT<'s, 't> = self.typing_interner.alloc(original_calling_env.id());
        child_of(
            self.typing_interner,
            self.scout_arena,
            original_calling_env,
            original_calling_env.denizen_template_id(),
            new_id,
            new_entries,
        )
    }

    pub fn resolve_conclusions_for_define(
        &self,
        env: IInDenizenEnvironmentT<'s, 't>,
        state: &mut CompilerOutputs<'s, 't>,
        ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        context_region: RegionT,
        rules: &[IRulexSR<'s>],
        conclusions: &IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>,
        reachable_bounds: &HashMap<IRuneS<'s>, &'t InstantiationReachableBoundArgumentsT<'s, 't>>,
    ) -> Result<&'t InstantiationBoundArgumentsT<'s, 't>, IConclusionResolveError<'s, 't>> {
        // Check all template calls
        for rule in rules {
            match rule {
                IRulexSR::Call(r) => {
                    match self.resolve_template_call_conclusion(env, state, ranges, call_location, *r, conclusions) {
                        Ok(()) => {}
                        Err(e) => return Err(IConclusionResolveError::CouldntFindKindForConclusionResolve(e)),
                    }
                }
                _ => {}
            }
        }

        let runes_and_prototypes: Vec<(IRuneS<'s>, &'t PrototypeT<'s, 't>)> =
            rules.iter().filter_map(|rule| {
                match rule {
                    IRulexSR::DefinitionFunc(r) => {
                        let result_rune = r.result_rune.rune;
                        match conclusions.get(&result_rune).expect("DefinitionFunc result rune missing from conclusions") {
                            ITemplataT::Prototype(proto_templata) => {
                                match proto_templata.prototype.id.local_name {
                                    INameT::FunctionBound(fb) => {
                                        let bound_name = self.typing_interner.intern_function_bound_name(FunctionBoundNameValT { template: fb.template, template_args: fb.template_args, parameters: fb.parameters });
                                        let new_id = self.typing_interner.intern_id(IdValT { package_coord: proto_templata.prototype.id.package_coord, init_steps: proto_templata.prototype.id.init_steps, local_name: INameT::FunctionBound(bound_name) });
                                        let prototype = self.typing_interner.intern_prototype(PrototypeValT { id: IdValT { package_coord: new_id.package_coord, init_steps: new_id.init_steps, local_name: new_id.local_name }, return_type: proto_templata.prototype.return_type });
                                        Some((result_rune, prototype))
                                    }
                                    _ => panic!("DefinitionFunc result conclusion is Prototype but not FunctionBound"),
                                }
                            }
                            other => panic!("DefinitionFunc result conclusion is not Prototype: {:?}", other),
                        }
                    }
                    _ => None,
                }
            }).collect();
        // VIOLATES @IIIOZ: still HashMap because the downstream make() consumer takes HashMap (cascade through ~6 files).
        // Deferred with site 5 main offender (line 861 conclusions).
        let rune_to_prototype: HashMap<IRuneS<'s>, &'t PrototypeT<'s, 't>> = runes_and_prototypes.iter().cloned().collect();
        if rune_to_prototype.len() < runes_and_prototypes.len() {
            panic!("resolve_conclusions_for_define: duplicate rune in runesAndPrototypes");
        }

        let maybe_runes_and_impls: Vec<(IRuneS<'s>, IdT<'s, 't>)> =
            rules.iter().filter_map(|rule| {
                match rule {
                    IRulexSR::DefinitionCoordIsa(r) => {
                        let result_rune = r.result_rune.rune;
                        let isa_templata = match conclusions.get(&result_rune) {
                            Some(ITemplataT::Isa(isa)) => isa,
                            Some(other) => panic!("vwat: expected IsaTemplataT for resultRune in DefinitionCoordIsaSR, got {:?}", other),
                            None => panic!("vassertSome: resultRune not in conclusions for DefinitionCoordIsaSR"),
                        };
                        let impl_bound_name_t = match isa_templata.impl_name.local_name {
                            INameT::ImplBound(bound) => bound,
                            other => panic!("vwat: expected ImplBoundNameT in isa implName local_name, got {:?}", other),
                        };
                        let impl_bound_name = self.typing_interner.intern_impl_bound_name(
                            ImplBoundNameValT {
                                template: impl_bound_name_t.template,
                                template_args: impl_bound_name_t.template_args,
                            }
                        );
                        let impl_id = self.typing_interner.intern_id(IdValT {
                            package_coord: isa_templata.impl_name.package_coord,
                            init_steps: isa_templata.impl_name.init_steps,
                            local_name: INameT::ImplBound(impl_bound_name),
                        });
                        Some((result_rune, *impl_id))
                    }
                    _ => None,
                }
            }).collect();
        // VIOLATES @IIIOZ: HashMap; same cascade as rune_to_prototype above. Deferred.
        let rune_to_impl: HashMap<IRuneS<'s>, IdT<'s, 't>> = maybe_runes_and_impls.iter().cloned().collect();
        if rune_to_impl.len() < maybe_runes_and_impls.len() {
            panic!("resolve_conclusions_for_define: duplicate rune in maybeRunesAndImpls");
        }

        let filtered_reachable_bounds: Vec<(IRuneS<'s>, &'t InstantiationReachableBoundArgumentsT<'s, 't>)> =
            reachable_bounds.iter()
                .filter(|(_, rb)| !rb.citizen_rune_to_reachable_prototype.is_empty())
                .map(|(rune, rb)| (*rune, *rb))
                .collect();
        Ok(make(
            self.typing_interner,
            rune_to_prototype.into_iter().map(|(k, v)| (k, *v)).collect(),
            filtered_reachable_bounds,
            rune_to_impl.into_iter().collect(),
        ))
    }

    pub fn resolve_function_call_conclusion(
        &self,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        state: &mut CompilerOutputs<'s, 't>,
        ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        c: ResolveSR<'s>,
        conclusions: &IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>,
        context_region: RegionT,
    ) -> Result<Result<(IRuneS<'s>, &'t PrototypeT<'s, 't>), IConclusionResolveError<'s, 't>>, ICompileErrorT<'s, 't>> {
        let return_coord = match conclusions.get(&c.return_rune.rune) {
            Some(ITemplataT::Coord(ct)) => ct.coord,
            None => panic!("vwat: returnRune not in conclusions for ResolveSR"),
            Some(other) => panic!("vwat: expected CoordTemplataT for returnRune, got {:?}", other),
        };
        let param_coords = match conclusions.get(&c.params_list_rune.rune) {
            None => panic!("vwat: paramsListRune not in conclusions for ResolveSR"),
            Some(ITemplataT::CoordList(cl)) => cl.coords,
            Some(other) => panic!("vwat: expected CoordListTemplataT for paramsListRune, got {:?}", other),
        };
        let mut full_ranges = Vec::with_capacity(1 + ranges.len());
        full_ranges.push(c.range);
        full_ranges.extend_from_slice(ranges);
        let func_success = match self.resolve_function(calling_env, state, &full_ranges, call_location, c.name, param_coords, context_region, true)? {
            Err(e) => {
                let ranges_slice = self.typing_interner.alloc_slice_from_vec(full_ranges);
                return Ok(Err(IConclusionResolveError::CouldntFindFunctionForConclusionResolve { range: ranges_slice, fff: e }));
            }
            Ok(x) => x,
        };
        if func_success.prototype.return_type != return_coord {
            let ranges_slice = self.typing_interner.alloc_slice_from_vec(full_ranges);
            return Ok(Err(IConclusionResolveError::ReturnTypeConflictInConclusionResolve { range: ranges_slice, expected_return_type: return_coord, actual: func_success.prototype }));
        }
        Ok(Ok((c.result_rune.rune, func_success.prototype)))
    }

    pub fn resolve_impl_conclusion(
        &self,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        state: &mut CompilerOutputs<'s, 't>,
        ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        c: CallSiteCoordIsaSR<'s>,
        conclusions: &IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>,
    ) -> Result<(IRuneS<'s>, IdT<'s, 't>), IConclusionResolveError<'s, 't>> {
        let CallSiteCoordIsaSR { range, result_rune, sub_rune, super_rune } = c;
        let sub_coord = match conclusions.get(&sub_rune.rune) {
            Some(ITemplataT::Coord(ct)) => ct.coord,
            Some(other) => panic!("vwat: expected CoordTemplataT for subRune in resolveImplConclusion, got {:?}", other),
            None => panic!("vwat: subRune not in conclusions for resolveImplConclusion"),
        };
        let sub_kind = match ISubKindTT::try_from(sub_coord.kind) {
            Ok(k) => k,
            Err(_) => panic!("vwat: sub_kind is not ISubKindTT in resolveImplConclusion: {:?}", sub_coord.kind),
        };
        let super_coord = match conclusions.get(&super_rune.rune) {
            Some(ITemplataT::Coord(ct)) => ct.coord,
            Some(other) => panic!("vwat: expected CoordTemplataT for superRune in resolveImplConclusion, got {:?}", other),
            None => panic!("vwat: superRune not in conclusions for resolveImplConclusion"),
        };
        let super_kind = match ISuperKindTT::try_from(super_coord.kind) {
            Ok(k) => k,
            Err(_) => panic!("vwat: super_kind is not ISuperKindTT in resolveImplConclusion: {:?}", super_coord.kind),
        };
        let mut full_ranges = vec![range];
        full_ranges.extend_from_slice(ranges);
        let impl_success = match self.is_parent(state, calling_env, &full_ranges, call_location, sub_kind, super_kind) {
            IsParentResult::IsntParent(x) => {
                let ranges_slice = self.typing_interner.alloc_slice_from_vec(full_ranges);
                return Err(IConclusionResolveError::CouldntFindImplForConclusionResolve { range: ranges_slice, fail: x });
            }
            IsParentResult::IsParent(x) => x,
        };
        let result_rune_s = result_rune.expect("vassertSome: resultRune in CallSiteCoordIsaSR resolveImplConclusion").rune;
        Ok((result_rune_s, impl_success.impl_id))
    }

    pub fn resolve_template_call_conclusion(
        &self,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        state: &mut CompilerOutputs<'s, 't>,
        ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        c: CallSR<'s>,
        conclusions: &IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>,
    ) -> Result<(), ResolveFailure<'s, 't, KindT<'s, 't>>> {
        let CallSR { range, result_rune, template_rune, args: arg_runes } = c;

        // If it was an incomplete solve, then just skip.
        let template = match conclusions.get(&template_rune.rune) {
            Some(t) => *t,
            None => return Ok(()),
        };
        let args: Vec<ITemplataT<'s, 't>> = {
            let mut v = Vec::new();
            for arg_rune in arg_runes.iter() {
                match conclusions.get(&arg_rune.rune) {
                    Some(t) => v.push(*t),
                    None => return Ok(()),
                }
            }
            v
        };

        match template {
            ITemplataT::RuntimeSizedArrayTemplate(_) => {
                let m = args[0];
                let coord = match args[1] {
                    ITemplataT::Coord(ct) => ct.coord,
                    _ => panic!("Expected CoordTemplataT as second arg in resolve_template_call_conclusion RuntimeSizedArrayTemplate"),
                };
                let mutability = expect_mutability(m);
                let context_region = RegionT { region: IRegionT::Default };
                let _rsa = self.resolve_runtime_sized_array(coord, mutability, context_region);
                Ok(())
            }
            ITemplataT::StaticSizedArrayTemplate(_) => {
                let s = args[0];
                let m = args[1];
                let v = args[2];
                let coord = match args[3] {
                    ITemplataT::Coord(ct) => ct.coord,
                    _ => panic!("Expected CoordTemplataT as fourth arg in resolve_template_call_conclusion StaticSizedArrayTemplate"),
                };
                let size = expect_integer(s);
                let mutability = expect_mutability(m);
                let variability = expect_variability(v);
                let context_region = RegionT { region: IRegionT::Default };
                let _ssa = self.resolve_static_sized_array(mutability, variability, size, coord, context_region);
                Ok(())
            }
            ITemplataT::StructDefinition(it) => {
                let mut call_ranges = vec![range];
                call_ranges.extend_from_slice(ranges);
                let call_ranges_slice = self.typing_interner.alloc_slice_from_vec(call_ranges);
                // Per @DRSINI, passes partial args (only written template args, not defaults).
                // resolve_struct adds defaults incrementally via solve_for_resolving for unsolved runes.
                match self.resolve_struct(state, calling_env, call_ranges_slice, call_location, *it, &args) {
                    IResolveOutcome::ResolveSuccess(_kind) => {}
                    IResolveOutcome::ResolveFailure(rf) => return Err(ResolveFailure { range: rf.range, x: rf.x, _phantom: PhantomData }),
                }
                Ok(())
            }
            ITemplataT::InterfaceDefinition(it) => {
                let mut call_ranges = vec![range];
                call_ranges.extend_from_slice(ranges);
                let call_ranges_slice = self.typing_interner.alloc_slice_from_vec(call_ranges);
                // Per @DRSINI, passes partial args (only written template args, not defaults).
                // resolve_interface adds defaults incrementally via solve_for_resolving for unsolved runes.
                match self.resolve_interface(state, calling_env, call_ranges_slice, call_location, *it, &args) {
                    IResolveOutcome::ResolveSuccess(_kind) => {}
                    IResolveOutcome::ResolveFailure(rf) => return Err(ResolveFailure { range: rf.range, x: rf.x, _phantom: PhantomData }),
                }
                Ok(())
            }
            ITemplataT::Kind(_kt) => {
                Ok(())
            }
            other => panic!("vimpl: resolve_template_call_conclusion {:?}", other),
        }
    }

    pub fn incrementally_solve(
        &self,
        envs: InferEnv<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        solver_state: &mut SimpleSolverState<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>>,
        mut on_incomplete_solve: impl FnMut(&mut CompilerOutputs<'s, 't>, &mut SimpleSolverState<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>>) -> bool,
    ) -> Result<bool, FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>> {
        // See IRAGP for why we have this incremental solving/placeholdering.
        //   while ( {
        loop {
            //     continue(envs, coutputs, solverState) match {
            //       case Ok(()) =>
            //       case Err(f) => return Err(f)
            //     }
            self.r#continue(envs, coutputs, solver_state)?;

            //     // During the solve, we postponed resolving structs and interfaces, see SFWPRL.
            //     // Caller should remember to do that!
            //     if (!solverState.isComplete()) {
            if !solver_state.is_complete() {
                //       val continue = onIncompleteSolve(solverState)
                let should_continue = on_incomplete_solve(coutputs, solver_state);
                //       if (!continue) {
                //         return Ok(false)
                //       }
                if !should_continue {
                    return Ok(false);
                }
                //       true
            } else {
                //     } else {
                //       return Ok(true)
                return Ok(true);
            }
        }
        //   }) {}
        //   vfail() // Shouldnt get here
    }

}

// Per @SROACSD, DefinitionFuncSR and DefinitionCoordIsaSR are excluded from
// call-site solves so that ResolveSR and its siblings can't see callee-internal
// prototype declarations. @BRRZ depends on this filter: the relaxed ResolveSR's
// real-lookup branch assumes no sibling DefinitionFuncSR in the same solve.
pub fn include_rule_in_call_site_solve(rule: &IRulexSR) -> bool {
    match rule {
        IRulexSR::DefinitionFunc(_) => false,
        IRulexSR::DefinitionCoordIsa(_) => false,
        _ => true,
    }
}

// Per @SROACSD, ResolveSR, CallSiteFuncSR, and CallSiteCoordIsaSR are excluded
// from definition solves — a function's own definition should not resolve
// its callers' prototypes.
pub fn include_rule_in_definition_solve(rule: &IRulexSR) -> bool {
    match rule {
        IRulexSR::CallSiteCoordIsa(_) => false,
        IRulexSR::CallSiteFunc(_) => false,
        IRulexSR::Resolve(_) => false,
        _ => true,
    }
}

