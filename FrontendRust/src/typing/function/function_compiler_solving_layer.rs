use indexmap::{IndexMap, IndexSet};
use std::collections::{HashMap, HashSet};
use crate::typing::compiler::Compiler;
use crate::typing::function::function_compiler::*;
use crate::typing::compilation::TypingPassOptions;
use crate::utils::code_hierarchy::PackageCoordinate;
use crate::typing::infer_compiler::{include_rule_in_definition_solve, InitialKnown, InitialSend, InferEnv, CompleteResolveSolve, CompleteDefineSolve, IResolvingError, IDefiningError};
use crate::typing::hinputs_t::InstantiationBoundArgumentsT;
use crate::utils::arena_index_map::ArenaIndexMap;
use crate::postparsing::itemplatatype::ITemplataType;
use crate::utils::range::RangeS;
use crate::postparsing::names::*;
use crate::postparsing::ast::*;
use crate::postparsing::*;
use crate::postparsing::rules::*;
use crate::typing::ast::ast::*;
use crate::typing::ast::citizens::*;
use crate::typing::ast::expressions::*;
use crate::typing::env::environment::*;
use crate::typing::env::function_environment_t::*;
use crate::typing::env::i_env_entry::*;
use crate::typing::names::names::*;
use crate::typing::types::types::*;
use crate::typing::templata::templata::*;
use crate::typing::compiler_outputs::*;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::higher_typing::ast::*;
use crate::solver::solver::*;
use crate::interner::Interner;
use crate::keywords::Keywords;
use crate::typing::infer_compiler::IConclusionResolveError;
use crate::typing::infer::compiler_solver::ITypingPassSolverError;
use std::iter::empty;
use std::marker::PhantomData;

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn evaluate_templated_function_from_call_for_prototype_solving(
        &self,
        outer_env: &BuildingFunctionEnvironmentWithClosuredsT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        original_calling_env: IInDenizenEnvironmentT<'s, 't>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        explicit_template_args: &[ITemplataT<'s, 't>],
        context_region: RegionT,
        args: &[CoordT<'s, 't>],
    ) -> IEvaluateFunctionResult<'s, 't> {
        panic!("Unimplemented: evaluate_templated_function_from_call_for_prototype");
    }

    pub fn evaluate_templated_function_from_call_for_banner(
        &self,
        declaring_env: &'t BuildingFunctionEnvironmentWithClosuredsT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        original_calling_env: IInDenizenEnvironmentT<'s, 't>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        already_specified_template_args: &[ITemplataT<'s, 't>],
        context_region: RegionT,
        args: &[CoordT<'s, 't>],
    ) -> Result<IEvaluateFunctionResult<'s, 't>, ICompileErrorT<'s, 't>> {
        let function = declaring_env.function;
        // Check preconditions
        self.check_closure_concerns_handled(declaring_env);

        let call_site_rules = self.assemble_call_site_rules(function.rules);

        let initial_sends = self.assemble_initial_sends_from_args(call_range[0], function, &args.iter().map(|a| Some(*a)).collect::<Vec<_>>());
        let initial_knowns = self.assemble_known_templatas(function, already_specified_template_args);

        let rune_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            function.rune_to_type.iter().map(|(k, v)| (*k, *v)).collect();

        let call_range_t: &'t [RangeS<'s>] = self.typing_interner.alloc_slice_copy(call_range);

        // We could probably just solveForResolving (see DBDAR) but seems more future-proof to solveForDefining.
        let CompleteDefineSolve { conclusions: inferences, rune_to_bound: instantiation_bound_params } =
            match self.solve_for_defining(
                InferEnv {
                    original_calling_env,
                    parent_ranges: call_range_t,
                    call_location,
                    self_env: declaring_env.into(),
                    context_region,
                },
                coutputs,
                &call_site_rules,
                &rune_to_type,
                call_range_t,
                call_location,
                &initial_knowns,
                &initial_sends,
                &[],
            ) {
                Err(e) => return Ok(IEvaluateFunctionResult::EvaluateFunctionFailure(EvaluateFunctionFailure { reason: e })),
                Ok(inferred_templatas) => inferred_templatas,
            };

        // See FunctionCompiler doc for what outer/runes/inner envs are.
        let reachable_bounds: Vec<PrototypeTemplataT<'s, 't>> =
            instantiation_bound_params.rune_to_citizen_rune_to_reachable_prototype.values()
                .flat_map(|r| {
                    panic!("implement: evaluate_templated_function_from_call_for_banner reachable bounds");
                    #[allow(unreachable_code)]
                    empty::<PrototypeTemplataT<'s, 't>>()
                })
                .collect();

        let runed_env: &'t BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't> =
            self.typing_interner.alloc(self.add_runed_data_to_near_env(
                declaring_env,
                &function.generic_parameters.iter().map(|gp| gp.rune.rune).collect::<Vec<_>>(),
                &inferences,
                &reachable_bounds));

        let prototype_templata =
            self.get_or_evaluate_templated_function_for_banner(
                declaring_env, runed_env, coutputs, call_range_t, call_location, function, instantiation_bound_params)?;

        // Lambdas cant have bounds, right?
        assert!(instantiation_bound_params.rune_to_bound_prototype.is_empty(), "vcurious");
        assert!(instantiation_bound_params.rune_to_citizen_rune_to_reachable_prototype.is_empty(), "vcurious");
        assert!(instantiation_bound_params.rune_to_bound_impl.is_empty(), "vcurious");
        let instantiation_bound_args = self.typing_interner.alloc(InstantiationBoundArgumentsT {
            rune_to_bound_prototype: self.typing_interner.alloc_index_map_from_iter(
                instantiation_bound_params.rune_to_bound_prototype.iter()
                    .map(|(_k, _v)| panic!("implement: evaluate_templated_function_from_call_for_banner — rune_to_bound_prototype passthrough"))
            ),
            rune_to_citizen_rune_to_reachable_prototype: self.typing_interner.alloc_index_map_from_iter(
                instantiation_bound_params.rune_to_citizen_rune_to_reachable_prototype.iter()
                    .map(|(_x, _v)| panic!("implement: evaluate_templated_function_from_call_for_banner — InstantiationReachableBoundArgumentsT mapping"))
            ),
            rune_to_bound_impl: self.typing_interner.alloc_index_map_from_iter(
                instantiation_bound_params.rune_to_bound_impl.iter()
                    .map(|(_k, _v)| panic!("implement: evaluate_templated_function_from_call_for_banner — rune_to_bound_impl passthrough"))
            ),
        });
        coutputs.add_instantiation_bounds(
            self.opts.global_options.sanity_check,
            self.typing_interner,
            original_calling_env.denizen_template_id(),
            prototype_templata.prototype.id,
            instantiation_bound_args);
        Ok(IEvaluateFunctionResult::EvaluateFunctionSuccess(EvaluateFunctionSuccess {
            prototype: self.typing_interner.alloc(prototype_templata),
            inferences,
            instantiation_bound_args,
        }))
    }

    pub fn evaluate_templated_light_banner_from_call(
        &self,
        near_env: &'t BuildingFunctionEnvironmentWithClosuredsT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        original_calling_env: IInDenizenEnvironmentT<'s, 't>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        explicit_template_args: &[ITemplataT<'s, 't>],
        context_region: RegionT,
        args: &[CoordT<'s, 't>],
    ) -> Result<IEvaluateFunctionResult<'s, 't>, ICompileErrorT<'s, 't>> {
        let function = near_env.function;
        // Check preconditions
        match &function.body {
            IBodyS::CodeBody(body1) => assert!(body1.body.closured_names.is_empty()),
            _ => {}
        }

        // Per @ECSIIOSZ, this is the per-call-site solver for function call resolution: argument
        // types become InitialSends, explicit template args become InitialKnowns, and
        // assemble_call_site_rules filters per SROACSD.
        let call_site_rules = self.assemble_call_site_rules(function.rules);

        let initial_sends = self.assemble_initial_sends_from_args(call_range[0], function, &args.iter().map(|a| Some(*a)).collect::<Vec<_>>());
        let initial_knowns = self.assemble_known_templatas(function, explicit_template_args);

        let rune_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            function.rune_to_type.iter().map(|(k, v)| (*k, *v)).collect();

        let call_range_t: &'t [RangeS<'s>] = self.typing_interner.alloc_slice_copy(call_range);

        // We could probably just solveForResolving (see DBDAR) but seems more future-proof to solveForDefining.
        let CompleteDefineSolve { conclusions: inferences, rune_to_bound: instantiation_bound_params } =
            match self.solve_for_defining(
                InferEnv {
                    original_calling_env,
                    parent_ranges: call_range_t,
                    call_location,
                    self_env: near_env.into(),
                    context_region,
                },
                coutputs,
                &call_site_rules,
                &rune_to_type,
                call_range_t,
                call_location,
                &initial_knowns,
                &initial_sends,
                &[],
            ) {
                Err(e) => return Ok(IEvaluateFunctionResult::EvaluateFunctionFailure(EvaluateFunctionFailure { reason: e })),
                Ok(inferred_templatas) => inferred_templatas,
            };

        // See FunctionCompiler doc for what outer/runes/inner envs are.
        let reachable_bounds: Vec<PrototypeTemplataT<'s, 't>> =
            instantiation_bound_params.rune_to_citizen_rune_to_reachable_prototype.values()
                .flat_map(|m| m.citizen_rune_to_reachable_prototype.values().copied())
                .map(|p| PrototypeTemplataT { prototype: self.typing_interner.alloc(p) })
                .collect();

        let runed_env: &'t BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't> =
            self.typing_interner.alloc(self.add_runed_data_to_near_env(
                near_env,
                &function.generic_parameters.iter().map(|gp| gp.rune.rune).collect::<Vec<_>>(),
                &inferences,
                &reachable_bounds));

        let prototype_templata =
            self.get_or_evaluate_templated_function_for_banner(
                near_env, runed_env, coutputs, call_range_t, call_location, function, instantiation_bound_params)?;

        // Lambdas cant have bounds, right?
        assert!(instantiation_bound_params.rune_to_bound_prototype.is_empty(), "vcurious");
        assert!(instantiation_bound_params.rune_to_citizen_rune_to_reachable_prototype.is_empty(), "vcurious");
        assert!(instantiation_bound_params.rune_to_bound_impl.is_empty(), "vcurious");
        let instantiation_bound_args = self.typing_interner.alloc(InstantiationBoundArgumentsT {
            rune_to_bound_prototype: self.typing_interner.alloc_index_map_from_iter(
                instantiation_bound_params.rune_to_bound_prototype.iter().map(|(k, v)| (*k, *v))
            ),
            rune_to_citizen_rune_to_reachable_prototype: self.typing_interner.alloc_index_map_from_iter(
                instantiation_bound_params.rune_to_citizen_rune_to_reachable_prototype.iter().map(|(k, v)| (*k, *v))
            ),
            rune_to_bound_impl: self.typing_interner.alloc_index_map_from_iter(
                instantiation_bound_params.rune_to_bound_impl.iter().map(|(k, v)| (*k, *v))
            ),
        });
        coutputs.add_instantiation_bounds(
            self.opts.global_options.sanity_check,
            self.typing_interner,
            original_calling_env.denizen_template_id(),
            prototype_templata.prototype.id,
            instantiation_bound_args);
        Ok(IEvaluateFunctionResult::EvaluateFunctionSuccess(EvaluateFunctionSuccess {
            prototype: self.typing_interner.alloc(prototype_templata),
            inferences,
            instantiation_bound_args,
        }))
    }

    pub fn assemble_known_templatas(
        &self,
        function: &FunctionA<'s>,
        explicit_template_args: &[ITemplataT<'s, 't>],
    ) -> Vec<InitialKnown<'s, 't>> {
        function.generic_parameters.iter()
            .zip(explicit_template_args.iter())
            .map(|(generic_param, explicit_arg)| {
                InitialKnown {
                    rune: generic_param.rune,
                    templata: *explicit_arg,
                }
            })
            .collect()
    }

    pub fn check_closure_concerns_handled(
        &self,
        near_env: &BuildingFunctionEnvironmentWithClosuredsT<'s, 't>,
    ) {
        let function = near_env.function;
        match &function.body {
            IBodyS::CodeBody(code_body) => {
                for name in code_body.body.closured_names.iter() {
                    let translated = self.translate_var_name_step(*name);
                    assert!(near_env.variables.iter().any(|v| v.name() == translated));
                }
            }
            _ => {}
        }
    }

    // IOW, add the necessary data to turn the near env into the runed env.
    // The reachable_bounds_from_params_and_return harvest violates @BDPFWDZ — the bound prototypes
    // are pushed downward from each citizen-typed param's inner env into this near-env.
    pub fn add_runed_data_to_near_env(
        &self,
        near_env: &BuildingFunctionEnvironmentWithClosuredsT<'s, 't>,
        identifying_runes: &[IRuneS<'s>],
        templatas_by_rune: &IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>,
        reachable_bounds_from_params_and_return: &[PrototypeTemplataT<'s, 't>],
    ) -> BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't> {
        let identifying_templatas: Vec<ITemplataT<'s, 't>> =
            identifying_runes.iter().map(|r| *templatas_by_rune.get(r).unwrap()).collect();

        // reachableBoundsFromParamsAndReturn.zipWithIndex.toVector
        //   .map({ case (t, i) => (interner.intern(ReachablePrototypeNameT(i)), TemplataEnvEntry(t)) }) ++
        // templatasByRune.toVector
        //   .map({ case (k, v) => (interner.intern(RuneNameT(k)), TemplataEnvEntry(v)) })
        let entries_list: Vec<(INameT<'s, 't>, IEnvEntryT<'s, 't>)> =
            reachable_bounds_from_params_and_return.iter().enumerate()
                .map(|(i, t)| -> (INameT<'s, 't>, IEnvEntryT<'s, 't>) {
                    let name = self.typing_interner.intern_reachable_prototype_name(ReachablePrototypeNameT { num: i as i32});
                    (INameT::ReachablePrototype(name), IEnvEntryT::Templata(ITemplataT::Prototype(self.typing_interner.alloc(*t))))
                })
                .chain(
                    templatas_by_rune.iter()
                        .map(|(k, v)| {
                            let rune_name = self.typing_interner.intern_rune_name(RuneNameT { rune: *k});
                            (INameT::Rune(rune_name), IEnvEntryT::Templata(*v))
                        })
                )
                .collect();

        // newEntries = templatas.addEntries(interner, entries_list)
        let new_entries = self.typing_interner.alloc(near_env.templatas.add_entries(self.typing_interner, self.scout_arena, entries_list));

        let default_region = RegionT { region: IRegionT::Default };

        let template_args: &'t [ITemplataT<'s, 't>] = self.typing_interner.alloc_slice_from_vec(identifying_templatas);
        BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT {
            global_env: near_env.global_env,
            parent_env: near_env.parent_env,
            id: near_env.id,
            template_args,
            templatas: new_entries,
            function: near_env.function,
            variables: near_env.variables,
            is_root_compiling_denizen: near_env.is_root_compiling_denizen,
            default_region,
        }
    }

    pub fn evaluate_generic_function_from_call_for_prototype(
        &self,
        outer_env: &'t BuildingFunctionEnvironmentWithClosuredsT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        explicit_template_args: &[ITemplataT<'s, 't>],
        context_region: RegionT,
        args: &[Option<CoordT<'s, 't>>],
        container_rune_initial_knowns: &[InitialKnown<'s, 't>],
    ) -> Result<IResolveFunctionResult<'s, 't>, ICompileErrorT<'s, 't>> {
        let function = outer_env.function;
        self.check_closure_concerns_handled(outer_env);

        let call_site_rules = self.assemble_call_site_rules(function.rules);

        let initial_sends = self.assemble_initial_sends_from_args(call_range[0], function, args);

        let call_range_t = self.typing_interner.alloc_slice_copy(call_range);
        let envs = InferEnv {
            original_calling_env: calling_env,
            parent_ranges: call_range_t,
            call_location,
            self_env: IEnvironmentT::BuildingWithClosureds(outer_env),
            context_region,
        };
        let rune_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            function.rune_to_type.iter().map(|(k, v)| (*k, *v)).collect();
        let invocation_range = call_range;
        let initial_knowns: Vec<InitialKnown<'s, 't>> = {
            let mut v = self.assemble_known_templatas(function, explicit_template_args);
            v.extend(container_rune_initial_knowns.iter().copied());
            v
        };
        let include_reachable_bounds_for_runes: Vec<IRuneS<'s>> =
            function.params.iter()
                .flat_map(|p| p.pattern.coord_rune.map(|ru| ru.rune))
                .chain(function.maybe_ret_coord_rune.map(|ru| ru.rune))
                .collect();

        // No MKRFA preprocessing needed: `function.rules` is declaration-scoped (same solver as
        // the function's own generic params), so the postparser never emits RuneParentEnvLookupSR
        // into it. If this site ever starts consuming expression-level rules, MKRFA preprocessing
        // MUST be added — see OverloadResolver.scala:311 and docs/refactor-thoughts/mkrfa-protocol-leak.md.
        let mut solver = self.make_solver_state(
            envs, coutputs, &call_site_rules, &rune_to_type, invocation_range, &initial_knowns, &initial_sends);

        let mut loop_check = function.generic_parameters.len() as i32 + 1;

        // Per @DRSINI, defaults are added here incrementally as a fallback, only for runes
        // that remain unsolved after argument inference.
        match self.incrementally_solve(
            envs, coutputs, &mut solver,
            |_coutputs, solver_state| {
                if loop_check == 0 {
                    panic!("RangedInternalErrorT: Infinite loop detected in incremental call solve!");
                }
                loop_check -= 1;

                match self.get_first_unsolved_identifying_rune(
                    function.generic_parameters,
                    |rune| solver_state.get_conclusion(&rune).is_some(),
                ) {
                    None => false,
                    Some((generic_param, index)) => {
                        assert!(index >= explicit_template_args.len() as i32);

                        match &generic_param.default {
                            Some(default_rules) => {
                                match solver_state.commit_step::<ITypingPassSolverError>(
                                    false, vec![], IndexMap::new(),
                                    default_rules.rules.iter().map(|r| **r).collect(),
                                    default_rules.rune_to_type.iter().map(|(k, _)| *k).collect()) {
                                    Ok(()) => {}
                                    Err(_) => panic!("getOrDie"),
                                };
                                true
                            }
                            None => {
                                false
                            }
                        }
                    }
                }
            },
        ) {
            Err(f) => {
                return Ok(IResolveFunctionResult::ResolveFunctionFailure(ResolveFunctionFailure {
                    reason: IResolvingError::ResolvingSolveFailedOrIncomplete(f),
                }));
            }
            Ok(true) => {}
            Ok(false) => {} // Incomplete, will be detected as SolveIncomplete below.
        }

        let CompleteResolveSolve { conclusions: inferred_templatas, rune_to_bound: rune_to_function_bound } =
            match self.check_resolving_conclusions_and_resolve(
                envs, coutputs, invocation_range, call_location, &rune_to_type, &call_site_rules, &include_reachable_bounds_for_runes, &mut solver,
            )? {
                Err(e) => {
                    return Ok(IResolveFunctionResult::ResolveFunctionFailure(ResolveFunctionFailure {
                        reason: e,
                    }));
                }
                Ok(i) => i,
            };

        let identifying_runes: Vec<IRuneS<'s>> =
            function.generic_parameters.iter().map(|gp| gp.rune.rune).collect();
        let reachable_bound_protos: Vec<PrototypeTemplataT<'s, 't>> =
            rune_to_function_bound.rune_to_citizen_rune_to_reachable_prototype.iter()
                .flat_map(|(_rune, x)| x.citizen_rune_to_reachable_prototype.values().copied())
                .map(|proto| PrototypeTemplataT { prototype: self.typing_interner.alloc(proto) })
                .collect();
        let runed_env = self.typing_interner.alloc(self.add_runed_data_to_near_env(
            outer_env, &identifying_runes, &inferred_templatas, &reachable_bound_protos));

        let prototype = self.get_generic_function_prototype_from_call(
            runed_env, coutputs, call_range, function)?;

        let prototype_templata = self.typing_interner.alloc(PrototypeTemplataT { prototype: self.typing_interner.alloc(prototype) });

        coutputs.add_instantiation_bounds(
            self.opts.global_options.sanity_check,
            self.typing_interner,
            calling_env.root_compiling_denizen_env().denizen_template_id(),
            prototype.id,
            self.typing_interner.alloc(rune_to_function_bound),
        );

        Ok(IResolveFunctionResult::ResolveFunctionSuccess(ResolveFunctionSuccess {
            prototype: prototype_templata,
            inferences: inferred_templatas,
        }))
    }

    pub fn evaluate_generic_virtual_dispatcher_function_for_prototype_solving(
        &self,
        near_env: &'t BuildingFunctionEnvironmentWithClosuredsT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        args: &[Option<CoordT<'s, 't>>],
    ) -> Result<IDefineFunctionResult<'s, 't>, ICompileErrorT<'s, 't>> {
        let function = near_env.function;
        self.check_closure_concerns_handled(near_env);

        let function_definition_rules: Vec<IRulexSR<'s>> =
            function.rules.iter().copied().filter(|r| include_rule_in_definition_solve(r)).collect();

        let initial_sends = self.assemble_initial_sends_from_args(call_range[0], function, args);

        let preliminary_envs = InferEnv {
            original_calling_env: calling_env,
            parent_ranges: self.typing_interner.alloc_slice_copy(call_range),
            call_location,
            self_env: IEnvironmentT::BuildingWithClosureds(near_env),
            context_region: RegionT { region: IRegionT::Default },
        };
        let preliminary_rune_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            function.rune_to_type.iter().map(|(k, v)| (*k, *v)).collect();
        let mut preliminary_solver_state =
            self.make_solver_state(
                preliminary_envs,
                coutputs,
                &function_definition_rules,
                &preliminary_rune_to_type,
                &{
                    let mut ranges = vec![function.range];
                    ranges.extend_from_slice(call_range);
                    ranges
                },
                &[],
                &initial_sends);
        match self.r#continue(preliminary_envs, coutputs, &mut preliminary_solver_state) {
            Ok(()) => {}
            Err(_f) => { panic!("implement: TypingPassSolverError from preliminary continue"); }
        }

        let preliminary_inferences: IndexMap<IRuneS<'s>, ITemplataT<'s, 't>> =
            preliminary_solver_state.userify_conclusions().into_iter().collect();

        let placeholder_initial_knowns_from_function: Vec<InitialKnown<'s, 't>> =
            function.generic_parameters.iter().enumerate().flat_map(|(index, generic_param)| {
                match preliminary_inferences.get(&generic_param.rune.rune) {
                    Some(&x) => Some(InitialKnown { rune: generic_param.rune, templata: x }),
                    None => { panic!("implement: create placeholder for missing preliminary inference"); }
                }
            }).collect();

        let CompleteDefineSolve { conclusions: inferences, rune_to_bound: instantiation_bound_params } =
            match self.solve_for_defining(
                InferEnv {
                    original_calling_env: calling_env,
                    parent_ranges: self.typing_interner.alloc_slice_copy(call_range),
                    call_location,
                    self_env: IEnvironmentT::BuildingWithClosureds(near_env),
                    context_region: RegionT { region: IRegionT::Default },
                },
                coutputs,
                &function_definition_rules,
                &preliminary_rune_to_type,
                &{
                    let mut ranges = vec![function.range];
                    ranges.extend_from_slice(call_range);
                    ranges
                },
                call_location,
                &placeholder_initial_knowns_from_function,
                &[],
                &{
                    let mut runes: Vec<IRuneS<'s>> = function.params.iter()
                        .flat_map(|p| p.pattern.coord_rune.map(|r| r.rune))
                        .collect();
                    if let Some(r) = function.maybe_ret_coord_rune {
                        runes.push(r.rune);
                    }
                    runes
                },
            ) {
                Err(_f) => { panic!("implement: TypingPassDefiningError from solve_for_defining"); }
                Ok(c) => c,
            };
        let reachable_bounds: Vec<PrototypeTemplataT<'s, 't>> =
            instantiation_bound_params.rune_to_citizen_rune_to_reachable_prototype.values()
                .flat_map(|m| m.citizen_rune_to_reachable_prototype.values().copied())
                .map(|p| PrototypeTemplataT { prototype: self.typing_interner.alloc(p) })
                .collect();
        let runed_env =
            self.add_runed_data_to_near_env(
                near_env,
                &function.generic_parameters.iter().map(|p| p.rune.rune).collect::<Vec<_>>(),
                &inferences,
                &reachable_bounds);

        let runed_env_ref = self.typing_interner.alloc(runed_env);
        let prototype =
            self.get_generic_function_prototype_from_call(
                runed_env_ref, coutputs, call_range, function)?;

        Ok(IDefineFunctionResult::DefineFunctionSuccess(DefineFunctionSuccess {
            prototype: self.typing_interner.alloc(PrototypeTemplataT { prototype: self.typing_interner.alloc(prototype) }),
            inferences,
            instantiation_bound_params: instantiation_bound_params,
        }))
    }

    pub fn evaluate_generic_function_from_non_call_solving(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        near_env: &'t BuildingFunctionEnvironmentWithClosuredsT<'s, 't>,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
    ) -> Result<&'t FunctionHeaderT<'s, 't>, ICompileErrorT<'s, 't>> {
        let function = near_env.function;

        let mut range: Vec<RangeS<'s>> = Vec::with_capacity(1 + parent_ranges.len());
        range.push(function.range);
        range.extend_from_slice(parent_ranges);
        self.check_closure_concerns_handled(near_env);

        let function_template_name = self.translate_generic_function_name(function.name);
        let function_name_local: INameT<'s, 't> = match function_template_name {
            IFunctionTemplateNameT::FunctionTemplate(r) => INameT::FunctionTemplate(r),
            IFunctionTemplateNameT::ForwarderFunctionTemplate(r) => INameT::ForwarderFunctionTemplate(r),
            IFunctionTemplateNameT::ConstructorTemplate(r) => INameT::ConstructorTemplate(r),
            IFunctionTemplateNameT::AnonymousSubstructConstructorTemplate(r) => INameT::AnonymousSubstructConstructorTemplate(r),
            IFunctionTemplateNameT::LambdaCallFunctionTemplate(r) => INameT::LambdaCallFunctionTemplate(r),
            IFunctionTemplateNameT::OverrideDispatcherTemplate(r) => INameT::OverrideDispatcherTemplate(r),
            IFunctionTemplateNameT::ExternFunction(r) => INameT::ExternFunction(r),
            IFunctionTemplateNameT::FunctionBoundTemplate(r) => INameT::FunctionBoundTemplate(r),
            IFunctionTemplateNameT::PredictedFunctionTemplate(r) => INameT::PredictedFunctionTemplate(r),
        };
        let function_template_id = near_env.parent_env.id().add_step(self.typing_interner, function_name_local);

        let definition_rules: Vec<IRulexSR<'s>> = function.rules.iter().copied()
            .filter(|r| include_rule_in_definition_solve(r))
            .collect();

        let mut seen = HashSet::new();
        let mut param_and_return_runes: Vec<IRuneS<'s>> = Vec::new();
        for param in function.params.iter() {
            if let Some(coord_rune) = param.pattern.coord_rune {
                if seen.insert(coord_rune.rune) {
                    param_and_return_runes.push(coord_rune.rune);
                }
            }
        }
        if let Some(ret_coord_rune) = function.maybe_ret_coord_rune {
            if seen.insert(ret_coord_rune.rune) {
                param_and_return_runes.push(ret_coord_rune.rune);
            }
        }

        let parent_ranges_alloc = self.typing_interner.alloc_slice_from_vec(parent_ranges.to_vec());
        let near_env_as_in_denizen = IInDenizenEnvironmentT::BuildingWithClosureds(near_env);
        let near_env_as_env = IEnvironmentT::BuildingWithClosureds(near_env);
        let envs = InferEnv {
            original_calling_env: near_env_as_in_denizen,
            parent_ranges: parent_ranges_alloc,
            call_location,
            self_env: near_env_as_env,
            context_region: RegionT { region: IRegionT::Default },
        };

        let rune_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> = function.rune_to_type.iter()
            .map(|(k, v)| (*k, *v))
            .collect();
        let mut solver = self.make_solver_state(
            envs, coutputs, &definition_rules, &rune_to_type, &range, &[], &[]);

        let get_first_unsolved = |generic_parameters: &'s [&'s GenericParameterS<'s>], is_solved: &dyn Fn(IRuneS<'s>) -> bool| {
            self.get_first_unsolved_identifying_rune(generic_parameters, |rune| is_solved(rune))
        };
        let result = self.incrementally_solve(
            envs, coutputs, &mut solver,
            |coutputs, solver_state| {
                match get_first_unsolved(
                    function.generic_parameters,
                    &|rune| solver_state.get_conclusion(&rune).is_some(),
                ) {
                    None => false,
                    Some((generic_param, index)) => {
                        let placeholder_pure_height = None;
                        let templata = self.create_placeholder(
                            coutputs, near_env_as_in_denizen, *function_template_id,
                            generic_param, index, &rune_to_type, placeholder_pure_height, true);
                        solver_state.commit_step::<()>(
                            false, vec![], {
                                let mut m = IndexMap::new();
                                m.insert(generic_param.rune.rune, templata);
                                m
                            }, vec![], IndexSet::new()).unwrap();
                        true
                    }
                }
            });
        match result {
            Err(f) => return Err(ICompileErrorT::TypingPassSolverError {
                range: self.typing_interner.alloc_slice_from_vec(range.clone()),
                failed_solve: f,
            }),
            Ok(true) => {}
            Ok(false) => {} // Incomplete, will be detected in checkDefiningConclusionsAndResolve
        }

        let inferences = match self.interpret_results(&rune_to_type, &mut solver) {
            Err(e) => return Err(ICompileErrorT::TypingPassSolverError {
                range: self.typing_interner.alloc_slice_from_vec(range.clone()),
                failed_solve: e,
            }),
            Ok(conclusions) => conclusions,
        };

        let instantiation_bound_params = match self.check_defining_conclusions_and_resolve(
            envs, coutputs, &range, call_location, &definition_rules, &param_and_return_runes, &inferences,
        ) {
            Err(f) => {
                match f {
                    IConclusionResolveError::CouldntFindFunctionForConclusionResolve { .. } => panic!("TypingPassDefiningError: CouldntFindFunctionForConclusionResolve"),
                    IConclusionResolveError::ReturnTypeConflictInConclusionResolve { .. } => panic!("TypingPassDefiningError: ReturnTypeConflictInConclusionResolve"),
                    IConclusionResolveError::CouldntFindImplForConclusionResolve { .. } => panic!("TypingPassDefiningError: CouldntFindImplForConclusionResolve"),
                    IConclusionResolveError::CouldntFindKindForConclusionResolve(_) => panic!("TypingPassDefiningError: CouldntFindKindForConclusionResolve"),
                }
            }
            Ok(c) => c,
        };

        let identifying_runes: Vec<IRuneS<'s>> = function.generic_parameters.iter()
            .map(|gp| gp.rune.rune)
            .collect();
        let reachable_bounds: Vec<PrototypeTemplataT<'s, 't>> =
            instantiation_bound_params.rune_to_citizen_rune_to_reachable_prototype.iter()
                .flat_map(|(_, rb)| rb.citizen_rune_to_reachable_prototype.iter().map(|(_, proto)| proto))
                .map(|proto| PrototypeTemplataT { prototype: self.typing_interner.alloc(*proto) })
                .collect();
        let runed_env = self.add_runed_data_to_near_env(
            near_env, &identifying_runes, &inferences, &reachable_bounds);
        let runed_env: &'t BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't> =
            self.typing_interner.alloc(runed_env);

        let header = self.get_or_evaluate_function_for_header(
            near_env, runed_env, coutputs, parent_ranges, call_location, function, instantiation_bound_params)?;

        Ok(header)
    }

    pub fn assemble_initial_sends_from_args(
        &self,
        call_range: RangeS<'s>,
        function: &FunctionA<'s>,
        args: &[Option<CoordT<'s, 't>>],
    ) -> Vec<InitialSend<'s, 't>> {
        function.params.iter()
            .map(|p| p.pattern.coord_rune.unwrap())
            .zip(args.iter())
            .enumerate()
            .flat_map(|(arg_index, (param_rune, arg))| {
                match arg {
                    None => None,
                    Some(arg_templata) => {
                        let sender_rune = RuneUsage {
                            range: call_range,
                            rune: self.scout_arena.intern_rune(
                                IRuneValS::ArgumentRune(ArgumentRuneS { arg_index: arg_index as i32 })),
                        };
                        Some(InitialSend {
                            sender_rune,
                            receiver_rune: param_rune,
                            send_templata: ITemplataT::Coord(
                                self.typing_interner.alloc(CoordTemplataT { coord: *arg_templata })),
                        })
                    }
                }
            })
            .collect()
    }

}
