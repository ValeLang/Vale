use indexmap::IndexMap;
use std::collections::HashMap;
use std::iter::once;

use crate::utils::range::RangeS;

use crate::postparsing::names::*;

use crate::typing::types::types::*;
use crate::typing::templata::templata::*;
use crate::typing::ast::expressions::*;
use crate::typing::env::environment::*;
use crate::typing::env::function_environment_t::*;
use crate::typing::compiler_outputs::*;
use crate::postparsing::ast::{LocationInDenizen, IRegionMutabilityS};
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::postparsing::itemplatatype::{IntegerTemplataType, MutabilityTemplataType, VariabilityTemplataType, ITemplataType};
use crate::postparsing::rules::rules::*;
use crate::typing::compiler::Compiler;
use crate::typing::names::names::*;
use crate::utils::code_hierarchy::PackageCoordinate;
use crate::postparsing::itemplatatype::CoordTemplataType;
use crate::postparsing::rune_type_solver::solve_rune_type;
use crate::typing::infer_compiler::{CompleteResolveSolve, InferEnv, InitialKnown};
use crate::typing::templata::templata::{expect_integer, expect_mutability, expect_variability};
use std::collections::HashSet;
use crate::typing::types::types::KindT;
use crate::typing::ast::expressions::DestroyStaticSizedArrayIntoFunctionTE;
use crate::typing::templata::templata::expect_coord_templata;
use crate::higher_typing::higher_typing_pass::explicify_lookups;
use crate::postparsing::names::CodeNameS;
use crate::postparsing::names::CodeRuneS;
use crate::postparsing::names::IImpreciseNameValS;
use crate::postparsing::names::IRuneValS;
use crate::postparsing::rules::rules::RuneParentEnvLookupSR;
use crate::postparsing::rules::rules::RuneUsage;
use crate::typing::ast::expressions::FunctionCallTE;
use crate::typing::env::i_env_entry::IEnvEntryT;
use crate::typing::names::names::RuneNameT;
use crate::typing::templata::templata::CoordTemplataT;
use crate::typing::templata::templata::MutabilityTemplataT;
use crate::typing::types::types::MutabilityT;
use std::marker::PhantomData;

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn evaluate_static_sized_array_from_callable(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        region: RegionT,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        rules_with_implicitly_coercing_lookups_s: &[IRulexSR<'s>],
        maybe_element_type_rune_a: Option<IRuneS<'s>>,
        size_rune_a: IRuneS<'s>,
        mutability_rune: IRuneS<'s>,
        variability_rune: IRuneS<'s>,
        callable_te: ReferenceExpressionTE<'s, 't>,
    ) -> Result<StaticArrayFromCallableTE<'s, 't>, ICompileErrorT<'s, 't>> {

        let rune_typing_env = self.create_rune_type_solver_env(calling_env);

        let mut initially_known_runes: IndexMap<IRuneS<'s>, ITemplataType<'s>> = IndexMap::new();
        initially_known_runes.insert(size_rune_a, ITemplataType::IntegerTemplataType(IntegerTemplataType {}));
        initially_known_runes.insert(mutability_rune, ITemplataType::MutabilityTemplataType(MutabilityTemplataType {}));
        initially_known_runes.insert(variability_rune, ITemplataType::VariabilityTemplataType(VariabilityTemplataType {}));
        if let Some(rune) = maybe_element_type_rune_a {
            initially_known_runes.insert(rune, ITemplataType::CoordTemplataType(CoordTemplataType {}));
        }
        let rune_a_to_type_with_implicitly_coercing_lookups_s =
            solve_rune_type(
                self.scout_arena,
                self.opts.global_options.sanity_check,
                &rune_typing_env,
                parent_ranges.to_vec(),
                false,
                rules_with_implicitly_coercing_lookups_s,
                &[],
                true,
                initially_known_runes,
            ).map_err(|e| ICompileErrorT::HigherTypingInferError {
                range: self.typing_interner.alloc_slice_copy(parent_ranges),
                err: e,
            })?;

        let mut rune_a_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            IndexMap::from_iter(rune_a_to_type_with_implicitly_coercing_lookups_s.iter().map(|(k, v)| (*k, *v)));
        let mut rule_builder: Vec<IRulexSR<'s>> = Vec::new();
        match explicify_lookups(
            &rune_typing_env,
            self.scout_arena,
            &mut rune_a_to_type,
            &mut rule_builder,
            rules_with_implicitly_coercing_lookups_s.to_vec(),
        ) {
            Err(_e) => panic!("implement: evaluate_static_sized_array_from_callable — TooManyTypesWithNameT/CouldntFindTypeT"),
            Ok(()) => {}
        }
        let rules_a = rule_builder;
        // We preprocess out the rune parent env lookups, see MKRFA. The fold here is duplicated
        // from OverloadResolver.scala:311-325; when adding a new expression-scoped solver call
        // site, copy this again (or, preferably, land the shared helper refactor queued in
        // docs/refactor-thoughts/mkrfa-protocol-leak.md so neither copy is needed).
        let (initial_knowns, rules_without_rune_parent_env_lookups): (Vec<InitialKnown>, Vec<IRulexSR<'s>>) =
            rules_a.iter().fold(
                (Vec::new(), Vec::new()),
                |(mut previous_conclusions, mut remaining_rules), rule| {
                    match rule {
                        IRulexSR::RuneParentEnvLookup(RuneParentEnvLookupSR { rune, .. }) => {
                            let name = self.scout_arena.intern_imprecise_name(
                                IImpreciseNameValS::RuneName(RuneNameValS { rune: rune.rune }));
                            let mut filter = HashSet::new();
                            filter.insert(ILookupContext::TemplataLookupContext);
                            let templata = calling_env.lookup_nearest_with_imprecise_name(
                                name, filter, self.typing_interner).unwrap();
                            previous_conclusions.push(InitialKnown { rune: *rune, templata });
                            (previous_conclusions, remaining_rules)
                        }
                        rule => {
                            remaining_rules.push(*rule);
                            (previous_conclusions, remaining_rules)
                        }
                    }
                },
            );

        let parent_ranges_t = self.typing_interner.alloc_slice_copy(parent_ranges);
        let CompleteResolveSolve { conclusions: templatas, .. } =
            self.solve_for_resolving(
                InferEnv {
                    original_calling_env: calling_env,
                    parent_ranges: parent_ranges_t,
                    call_location,
                    self_env: IEnvironmentT::from(calling_env),
                    context_region: region,
                },
                coutputs,
                &rules_without_rune_parent_env_lookups,
                &rune_a_to_type,
                parent_ranges,
                call_location,
                &[],
                &initial_knowns,
                &[],
            )
            .unwrap_or_else(|_e| panic!("Unimplemented: ICompileErrorT from solve_for_resolving in evaluate_static_sized_array_from_callable"))
            .unwrap_or_else(|_e| panic!("Unimplemented: evaluate_static_sized_array_from_callable — TypingPassResolvingError"));

        let size = expect_integer(templatas.get(&size_rune_a).copied().expect("vassertSome: sizeRuneA not in templatas"));
        let mutability = expect_mutability(templatas.get(&mutability_rune).copied().expect("vassertSome: mutabilityRune not in templatas"));
        let variability = expect_variability(templatas.get(&variability_rune).copied().expect("vassertSome: variabilityRune not in templatas"));
        let prototype = self.get_array_generator_prototype(
            coutputs, calling_env, parent_ranges, call_location, callable_te, region)?;
        let ssa_mt = self.resolve_static_sized_array(
            mutability, variability, size, prototype.return_type, region);

        if let Some(element_type_rune_a) = maybe_element_type_rune_a {
            let expected_element_type = self.get_array_element_type(&templatas, element_type_rune_a);
            if prototype.return_type != expected_element_type {
                return Err(ICompileErrorT::UnexpectedArrayElementType {
                    range: self.typing_interner.alloc_slice_copy(parent_ranges),
                    expected_type: expected_element_type,
                    actual_type: prototype.return_type,
                });
            }
        }

        Ok(StaticArrayFromCallableTE {
            array_type: self.typing_interner.alloc(ssa_mt),
            region,
            generator: callable_te,
            generator_method: prototype,
        })
    }

    pub fn evaluate_runtime_sized_array_from_callable(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        calling_env: &'t NodeEnvironmentT<'s, 't>,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        region: RegionT,
        rules_with_implicitly_coercing_lookups_s: &[IRulexSR<'s>],
        maybe_element_type_rune: Option<IRuneS<'s>>,
        mutability_rune: IRuneS<'s>,
        size_te: ReferenceExpressionTE<'s, 't>,
        maybe_callable_te: Option<ReferenceExpressionTE<'s, 't>>,
    ) -> Result<ReferenceExpressionTE<'s, 't>, ICompileErrorT<'s, 't>> {
        let rune_typing_env = self.create_rune_type_solver_env(IInDenizenEnvironmentT::Node(calling_env));
        let mut initially_known_runes: IndexMap<IRuneS<'s>, ITemplataType<'s>> = IndexMap::new();
        initially_known_runes.insert(mutability_rune, ITemplataType::MutabilityTemplataType(MutabilityTemplataType {}));
        if let Some(rune) = maybe_element_type_rune {
            initially_known_runes.insert(rune, ITemplataType::CoordTemplataType(CoordTemplataType {}));
        }
        let rune_a_to_type_with_implicitly_coercing_lookups_s =
            solve_rune_type(
                self.scout_arena,
                self.opts.global_options.sanity_check,
                &rune_typing_env,
                parent_ranges.to_vec(),
                false,
                rules_with_implicitly_coercing_lookups_s,
                &[],
                true,
                initially_known_runes,
            ).map_err(|e| ICompileErrorT::HigherTypingInferError {
                range: self.typing_interner.alloc_slice_copy(parent_ranges),
                err: e,
            })?;
        let mut rune_a_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            IndexMap::from_iter(rune_a_to_type_with_implicitly_coercing_lookups_s.iter().map(|(k, v)| (*k, *v)));
        let mut rule_builder: Vec<IRulexSR<'s>> = Vec::new();
        match explicify_lookups(
            &rune_typing_env,
            self.scout_arena,
            &mut rune_a_to_type,
            &mut rule_builder,
            rules_with_implicitly_coercing_lookups_s.to_vec(),
        ) {
            Err(_e) => panic!("implement: evaluate_runtime_sized_array_from_callable — TooManyTypesWithNameT/CouldntFindTypeT"),
            Ok(()) => {}
        }
        let rules_a = rule_builder;
        // We preprocess out the rune parent env lookups, see MKRFA. The fold here is duplicated
        // from OverloadResolver.scala:311-325; when adding a new expression-scoped solver call
        // site, copy this again (or, preferably, land the shared helper refactor queued in
        // docs/refactor-thoughts/mkrfa-protocol-leak.md so neither copy is needed).
        let (initial_knowns, rules_without_rune_parent_env_lookups): (Vec<InitialKnown>, Vec<IRulexSR<'s>>) =
            rules_a.iter().fold(
                (Vec::new(), Vec::new()),
                |(mut previous_conclusions, mut remaining_rules), rule| {
                    match rule {
                        IRulexSR::RuneParentEnvLookup(RuneParentEnvLookupSR { rune, .. }) => {
                            let name = self.scout_arena.intern_imprecise_name(
                                IImpreciseNameValS::RuneName(RuneNameValS { rune: rune.rune }));
                            let mut filter = HashSet::new();
                            filter.insert(ILookupContext::TemplataLookupContext);
                            let templata = IInDenizenEnvironmentT::Node(calling_env).lookup_nearest_with_imprecise_name(
                                name, filter, self.typing_interner).unwrap();
                            previous_conclusions.push(InitialKnown { rune: *rune, templata });
                            (previous_conclusions, remaining_rules)
                        }
                        rule => {
                            remaining_rules.push(*rule);
                            (previous_conclusions, remaining_rules)
                        }
                    }
                },
            );

        let parent_ranges_t = self.typing_interner.alloc_slice_copy(parent_ranges);
        let envs = InferEnv {
            original_calling_env: IInDenizenEnvironmentT::Node(calling_env),
            parent_ranges: parent_ranges_t,
            call_location,
            self_env: IEnvironmentT::from(IInDenizenEnvironmentT::Node(calling_env)),
            context_region: region,
        };
        let mut solver_state = self.make_solver_state(
            envs, coutputs, &rules_without_rune_parent_env_lookups, &rune_a_to_type, parent_ranges, &initial_knowns, &[]);
        match self.incrementally_solve(envs, coutputs, &mut solver_state, |_coutputs, _solver| false) {
            Err(_f) => panic!("implement: evaluate_runtime_sized_array_from_callable — TypingPassSolverError"),
            Ok(true) => {}
            Ok(false) => {}
        }
        let CompleteResolveSolve { conclusions: templatas, .. } =
            self.check_resolving_conclusions_and_resolve(
                envs, coutputs, parent_ranges, call_location, &rune_a_to_type, &rules_without_rune_parent_env_lookups, &[], &mut solver_state)
            .unwrap_or_else(|_e| panic!("Unimplemented: ICompileErrorT from check_resolving_conclusions_and_resolve in evaluate_runtime_sized_array_from_callable"))
            .unwrap_or_else(|_e| panic!("Unimplemented: evaluate_runtime_sized_array_from_callable — TypingPassResolvingError"));
        let mutability = expect_mutability(templatas.get(&mutability_rune).copied().expect("vassertSome: mutabilityRune not in templatas"));
        match mutability {
            ITemplataT::Placeholder(_) => panic!("Unimplemented: evaluate_runtime_sized_array_from_callable — Placeholder mutability"),
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => {
                let callable_te = match maybe_callable_te {
                    None => return Err(ICompileErrorT::NewImmRSANeedsCallable { range: parent_ranges_t }),
                    Some(c) => c,
                };
                let prototype = self.get_array_generator_prototype(
                    coutputs, IInDenizenEnvironmentT::Node(calling_env), parent_ranges, call_location, callable_te, region)?;
                let rsa_mt = self.resolve_runtime_sized_array(prototype.return_type, mutability, region);
                if let Some(element_type_rune_a) = maybe_element_type_rune {
                    let expected_element_type = self.get_array_element_type(&templatas, element_type_rune_a);
                    if prototype.return_type != expected_element_type {
                        return Err(ICompileErrorT::UnexpectedArrayElementType {
                            range: self.typing_interner.alloc_slice_copy(parent_ranges),
                            expected_type: expected_element_type,
                            actual_type: prototype.return_type,
                        });
                    }
                }
                Ok(ReferenceExpressionTE::NewImmRuntimeSizedArray(self.typing_interner.alloc(NewImmRuntimeSizedArrayTE {
                    array_type: self.typing_interner.alloc(rsa_mt),
                    region,
                    size_expr: size_te,
                    generator: callable_te,
                    generator_method: prototype,
                })))
            }
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) => {
                let m_rune_name = self.scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS { name: self.keywords.m }));
                let m_rune_name_t = INameT::Rune(self.typing_interner.intern_rune_name(RuneNameT { rune: m_rune_name}));
                let mut entries: Vec<(INameT<'s, 't>, IEnvEntryT<'s, 't>)> = Vec::new();
                entries.push((m_rune_name_t, IEnvEntryT::Templata(ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }))));
                if let Some(e) = maybe_element_type_rune {
                    let e_rune_name_t = INameT::Rune(self.typing_interner.intern_rune_name(RuneNameT { rune: e}));
                    let element_type = self.get_array_element_type(&templatas, e);
                    entries.push((e_rune_name_t, IEnvEntryT::Templata(ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: element_type })))));
                }
                let extended_env = calling_env.add_entries(self.typing_interner, self.scout_arena, &entries);
                let head_range = parent_ranges[0];
                let mut explicit_rules: Vec<IRulexSR<'s>> = Vec::new();
                explicit_rules.push(IRulexSR::RuneParentEnvLookup(RuneParentEnvLookupSR {
                    range: head_range,
                    rune: RuneUsage { range: head_range, rune: m_rune_name },
                }));
                if let Some(e) = maybe_element_type_rune {
                    explicit_rules.push(IRulexSR::RuneParentEnvLookup(RuneParentEnvLookupSR {
                        range: head_range,
                        rune: RuneUsage { range: head_range, rune: e },
                    }));
                }
                let mut positional_runes: Vec<IRuneS<'s>> = Vec::new();
                positional_runes.push(m_rune_name);
                if let Some(e) = maybe_element_type_rune {
                    positional_runes.push(e);
                }
                let mut args: Vec<CoordT<'s, 't>> = Vec::new();
                args.push(size_te.result().coord);
                if let Some(c) = maybe_callable_te {
                    args.push(c.result().coord);
                }
                let array_imprecise_name = self.scout_arena.intern_imprecise_name(
                    IImpreciseNameValS::CodeName(CodeNameS { name: self.keywords.array }));
                let stamp = self.find_function(
                    IInDenizenEnvironmentT::Node(extended_env),
                    coutputs,
                    parent_ranges,
                    call_location,
                    array_imprecise_name,
                    &explicit_rules,
                    &positional_runes,
                    &[],
                    region,
                    &args,
                    &[],
                    true,
                )?
                    .map_err(|e| ICompileErrorT::CouldntFindFunctionToCallT {
                        range: self.typing_interner.alloc_slice_copy(parent_ranges),
                        fff: e,
                    })?;
                let prototype = stamp.prototype;
                let element_type = match prototype.return_type.kind {
                    KindT::RuntimeSizedArray(rsa) => match rsa.name.local_name {
                        INameT::RuntimeSizedArray(name) => {
                            let raw = name.arr;
                            if raw.mutability != ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) {
                                panic!("Array function returned wrong mutability!");
                            }
                            raw.element_type
                        }
                        _ => panic!("Array function returned wrong type!"),
                    },
                    _ => panic!("Array function returned wrong type!"),
                };
                if let Some(e) = maybe_element_type_rune {
                    let expected_element_type = self.get_array_element_type(&templatas, e);
                    if element_type != expected_element_type {
                        panic!("UnexpectedArrayElementType");
                    }
                }
                assert!(coutputs.get_instantiation_bounds(self.typing_interner, prototype.id).is_some());
                let result_te = prototype.return_type;
                let mut args_te: Vec<ReferenceExpressionTE<'s, 't>> = Vec::new();
                args_te.push(size_te);
                if let Some(c) = maybe_callable_te {
                    args_te.push(c);
                }
                let call_te = ReferenceExpressionTE::FunctionCall(self.typing_interner.alloc(FunctionCallTE {
                    callable: prototype,
                    args: self.typing_interner.alloc_slice_from_vec(args_te),
                    return_type: result_te,
                }));
                Ok(call_te)
            }
            _ => panic!("vwat"),
        }
    }

    pub fn evaluate_static_sized_array_from_values(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        rules_with_implicitly_coercing_lookups_s: &[IRulexSR<'s>],
        maybe_element_type_rune_a: Option<IRuneS<'s>>,
        size_rune_a: IRuneS<'s>,
        mutability_rune_a: IRuneS<'s>,
        variability_rune_a: IRuneS<'s>,
        exprs_2: Vec<ReferenceExpressionTE<'s, 't>>,
        region: RegionT,
    ) -> Result<StaticArrayFromValuesTE<'s, 't>, ICompileErrorT<'s, 't>> {

        let rune_typing_env = self.create_rune_type_solver_env(calling_env);

        let mut initially_known_runes: IndexMap<IRuneS<'s>, ITemplataType<'s>> = IndexMap::new();
        initially_known_runes.insert(size_rune_a, ITemplataType::IntegerTemplataType(IntegerTemplataType {}));
        initially_known_runes.insert(mutability_rune_a, ITemplataType::MutabilityTemplataType(MutabilityTemplataType {}));
        initially_known_runes.insert(variability_rune_a, ITemplataType::VariabilityTemplataType(VariabilityTemplataType {}));
        if let Some(rune) = maybe_element_type_rune_a {
            initially_known_runes.insert(rune, ITemplataType::CoordTemplataType(CoordTemplataType {}));
        }
        // Note: Rust solve_rune_type doesn't accept useOptimizedSolver (pre-existing API difference)
        let rune_a_to_type_with_implicitly_coercing_lookups_s =
            solve_rune_type(
                self.scout_arena,
                self.opts.global_options.sanity_check,
                &rune_typing_env,
                parent_ranges.to_vec(),
                false,
                rules_with_implicitly_coercing_lookups_s,
                &[],
                true,
                initially_known_runes,
            ).map_err(|e| ICompileErrorT::HigherTypingInferError {
                range: self.typing_interner.alloc_slice_copy(parent_ranges),
                err: e,
            })?;

        let member_types: indexmap::IndexSet<CoordT<'s, 't>> =
            exprs_2.iter().map(|e| e.result().coord).collect();
        if member_types.len() > 1 {
            let parent_ranges_t = self.typing_interner.alloc_slice_copy(parent_ranges);
            let types_t = self.typing_interner.alloc_slice_copy(&member_types.iter().copied().collect::<Vec<_>>());
            return Err(ICompileErrorT::ArrayElementsHaveDifferentTypes { range: parent_ranges_t, types: types_t });
        }
        let member_type = *member_types.iter().next().expect("vassert: memberTypes is empty");

        // VIOLATES @IIIOZ: still HashMap because explicify_lookups takes &mut HashMap.
        let mut rune_a_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            IndexMap::from_iter(rune_a_to_type_with_implicitly_coercing_lookups_s.iter().map(|(k, v)| (*k, *v)));
        let mut rule_builder: Vec<IRulexSR<'s>> = Vec::new();
        match explicify_lookups(
            &rune_typing_env,
            self.scout_arena,
            &mut rune_a_to_type,
            &mut rule_builder,
            rules_with_implicitly_coercing_lookups_s.to_vec(),
        ) {
            Err(_e) => panic!("implement: evaluate_static_sized_array_from_values — TooManyTypesWithNameT/CouldntFindTypeT"),
            Ok(()) => {}
        }
        let rules_a = rule_builder;
        // We preprocess out the rune parent env lookups, see MKRFA. The fold here is duplicated
        // from OverloadResolver.scala:311-325; when adding a new expression-scoped solver call
        // site, copy this again (or, preferably, land the shared helper refactor queued in
        // docs/refactor-thoughts/mkrfa-protocol-leak.md so neither copy is needed).
        let (initial_knowns, rules_without_rune_parent_env_lookups): (Vec<InitialKnown>, Vec<IRulexSR<'s>>) =
            rules_a.iter().fold(
                (Vec::new(), Vec::new()),
                |(mut previous_conclusions, mut remaining_rules), rule| {
                    match rule {
                        IRulexSR::RuneParentEnvLookup(RuneParentEnvLookupSR { rune, .. }) => {
                            let name = self.scout_arena.intern_imprecise_name(
                                IImpreciseNameValS::RuneName(RuneNameValS { rune: rune.rune }));
                            let mut filter = HashSet::new();
                            filter.insert(ILookupContext::TemplataLookupContext);
                            let templata = calling_env.lookup_nearest_with_imprecise_name(
                                name, filter, self.typing_interner).unwrap();
                            previous_conclusions.push(InitialKnown { rune: *rune, templata });
                            (previous_conclusions, remaining_rules)
                        }
                        rule => {
                            remaining_rules.push(*rule);
                            (previous_conclusions, remaining_rules)
                        }
                    }
                },
            );

        let parent_ranges_t = self.typing_interner.alloc_slice_copy(parent_ranges);
        let envs = InferEnv {
            original_calling_env: calling_env,
            parent_ranges: parent_ranges_t,
            call_location,
            self_env: IEnvironmentT::from(calling_env),
            context_region: region,
        };
        let mut solver_state = self.make_solver_state(
            envs, coutputs, &rules_without_rune_parent_env_lookups, &rune_a_to_type, parent_ranges, &initial_knowns, &[]);
        match self.incrementally_solve(envs, coutputs, &mut solver_state, |_coutputs, _solver| false) {
            Err(_f) => panic!("implement: evaluate_static_sized_array_from_values — TypingPassSolverError"),
            Ok(true) => {}
            Ok(false) => {}
        }
        let CompleteResolveSolve { conclusions: templatas, .. } =
            self.check_resolving_conclusions_and_resolve(
                envs, coutputs, parent_ranges, call_location, &rune_a_to_type, &rules_without_rune_parent_env_lookups, &[], &mut solver_state)
            .unwrap_or_else(|_e| panic!("Unimplemented: ICompileErrorT from check_resolving_conclusions_and_resolve in evaluate_static_sized_array_from_values"))
            .unwrap_or_else(|_e| panic!("Unimplemented: evaluate_static_sized_array_from_values — TypingPassResolvingError"));

        if let Some(element_type_rune_a) = maybe_element_type_rune_a {
            let expected_element_type = self.get_array_element_type(&templatas, element_type_rune_a);
            if member_type != expected_element_type {
                return Err(ICompileErrorT::UnexpectedArrayElementType {
                    range: self.typing_interner.alloc_slice_copy(parent_ranges),
                    expected_type: expected_element_type,
                    actual_type: member_type,
                });
            }
        }

        let mutability = expect_mutability(templatas.get(&mutability_rune_a).copied().expect("vassertSome: mutabilityRuneA not in templatas"));
        let variability = expect_variability(templatas.get(&variability_rune_a).copied().expect("vassertSome: variabilityRuneA not in templatas"));

        let static_sized_array_type = self.resolve_static_sized_array(
            mutability, variability, ITemplataT::Integer(exprs_2.len() as i64), member_type, region);
        let ownership = match static_sized_array_type.mutability() {
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) => OwnershipT::Own,
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => OwnershipT::Share,
            ITemplataT::Placeholder(p) if matches!(p.tyype, ITemplataType::MutabilityTemplataType(_)) => OwnershipT::Own,
            _ => panic!("vwat"),
        };
        let ssa_ref = self.typing_interner.alloc(static_sized_array_type);
        let ssa_coord = CoordT { ownership, region, kind: KindT::StaticSizedArray(ssa_ref) };
        Ok(StaticArrayFromValuesTE {
            elements: self.typing_interner.alloc_slice_from_vec(exprs_2),
            result_reference: ssa_coord,
            array_type: ssa_ref,
        })
    }

    pub fn evaluate_destroy_static_sized_array_into_callable(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        fate: &'t FunctionEnvironmentT<'s, 't>,
        range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        arr_te: ReferenceExpressionTE<'s, 't>,
        callable_te: ReferenceExpressionTE<'s, 't>,
        context_region: RegionT,
    ) -> Result<DestroyStaticSizedArrayIntoFunctionTE<'s, 't>, ICompileErrorT<'s, 't>> {
        let array_tt = match arr_te.result().coord.kind {
            KindT::StaticSizedArray(s) => s,
            other => panic!("Destroying a non-array with a callable! Destroying: {:?}", other),
        };
        let prototype = self.get_array_consumer_prototype(
            coutputs, fate, range, call_location, callable_te, array_tt.element_type(), context_region)?;
        Ok(DestroyStaticSizedArrayIntoFunctionTE {
            array_expr: arr_te,
            array_type: array_tt,
            consumer: callable_te,
            consumer_method: prototype,
        })
    }

    pub fn evaluate_destroy_runtime_sized_array_into_callable(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        fate: &FunctionEnvironmentT<'s, 't>,
        range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        arr_te: ReferenceExpressionTE<'s, 't>,
        callable_te: ReferenceExpressionTE<'s, 't>,
        context_region: RegionT,
    ) -> DestroyImmRuntimeSizedArrayTE<'s, 't> {
        panic!("Unimplemented: evaluate_destroy_runtime_sized_array_into_callable");
    }

    pub fn compile_static_sized_array(&self, global_env: &'t GlobalEnvironmentT<'s, 't>, coutputs: &mut CompilerOutputs<'s, 't>) {
        // val builtinPackage = PackageCoordinate.BUILTIN(interner, keywords)
        let builtin_package: &'s PackageCoordinate<'s> =
            self.scout_arena.intern_package_coordinate(self.keywords.empty_string, &[]);
        // val templateId =
        //   IdT(builtinPackage, Vector.empty, interner.intern(StaticSizedArrayTemplateNameT()))
        let template_name = self.typing_interner.intern_static_sized_array_template_name(
            StaticSizedArrayTemplateNameT { }
        );
        let template_id = self.typing_interner.intern_id(IdValT {
            package_coord: builtin_package,
            init_steps: &[],
            local_name: INameT::StaticSizedArrayTemplate(template_name),
        });

        // See CSFMSEO and SAFHE.
        // val arrayOuterEnv =
        //   CitizenEnvironmentT(
        //     globalEnv,
        //     PackageEnvironmentT(globalEnv, templateId, globalEnv.nameToTopLevelEnvironment.values.toVector),
        //     templateId,
        //     templateId,
        //     TemplatasStore(templateId, Map(), Map()))
        let global_namespaces: Vec<&TemplatasStoreT<'s, 't>> =
            global_env.name_to_top_level_environment.iter().map(|(_, ts)| *ts).collect();
        let global_namespaces = self.typing_interner.alloc_slice_from_vec(global_namespaces);
        let parent_env = self.typing_interner.alloc(PackageEnvironmentT {
            global_env,
            id: *template_id,
            global_namespaces,
        });
        let empty_templatas = TemplatasStoreBuilder::new(template_id).build_in(self.typing_interner);
        let array_outer_env = self.typing_interner.alloc(CitizenEnvironmentT {
            global_env,
            parent_env: IEnvironmentT::Package(parent_env),
            template_id: *template_id,
            id: *template_id,
            templatas: empty_templatas,
        });
        // coutputs.declareType(templateId)
        coutputs.declare_type(template_id);
        // coutputs.declareTypeOuterEnv(templateId, arrayOuterEnv)
        let array_outer_env_ref: IInDenizenEnvironmentT<'s, 't> =
            IInDenizenEnvironmentT::Citizen(array_outer_env);
        coutputs.declare_type_outer_env(template_id, array_outer_env_ref);

        // val TemplateTemplataType(types, _) = StaticSizedArrayTemplateTemplataT().tyype
        // val Vector(IntegerTemplataType(), MutabilityTemplataType(), VariabilityTemplataType(), CoordTemplataType()) = types
        // (assertion only — types are verified by the placeholder calls below)

        // val sizePlaceholder =
        //   templataCompiler.createNonKindNonRegionPlaceholderInner(
        //     templateId, 0, CodeRuneS(interner.intern(StrI("N"))), IntegerTemplataType())
        let rune_n = self.scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
            name: self.scout_arena.intern_str("N"),
        }));
        let size_placeholder = self.create_non_kind_non_region_placeholder_inner(
            *template_id, 0, rune_n, ITemplataType::IntegerTemplataType(IntegerTemplataType {}),
        );
        // val mutabilityPlaceholder =
        //   templataCompiler.createNonKindNonRegionPlaceholderInner(
        //     templateId, 1, CodeRuneS(interner.intern(StrI("M"))), MutabilityTemplataType())
        let rune_m = self.scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
            name: self.scout_arena.intern_str("M"),
        }));
        let mutability_placeholder = self.create_non_kind_non_region_placeholder_inner(
            *template_id, 1, rune_m, ITemplataType::MutabilityTemplataType(MutabilityTemplataType {}),
        );
        // val variabilityPlaceholder =
        //   templataCompiler.createNonKindNonRegionPlaceholderInner(
        //     templateId, 2, CodeRuneS(interner.intern(StrI("V"))), VariabilityTemplataType())
        let rune_v = self.scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
            name: self.scout_arena.intern_str("V"),
        }));
        let variability_placeholder = self.create_non_kind_non_region_placeholder_inner(
            *template_id, 2, rune_v, ITemplataType::VariabilityTemplataType(VariabilityTemplataType {}),
        );
        // val elementPlaceholder =
        //   templataCompiler.createCoordPlaceholderInner(
        //     coutputs, arrayOuterEnv, templateId, 3, CodeRuneS(interner.intern(StrI("E"))), None, ReadOnlyRegionS, OwnT, true)
        let rune_e = self.scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
            name: self.scout_arena.intern_str("E"),
        }));
        let element_placeholder = self.create_coord_placeholder_inner(
            coutputs,
            array_outer_env_ref,
            *template_id, 3, rune_e, None,
            IRegionMutabilityS::ReadOnlyRegion, OwnershipT::Own, true,
        );

        // val placeholders =
        //   Vector(sizePlaceholder, mutabilityPlaceholder, variabilityPlaceholder, elementPlaceholder)
        let element_placeholder_templata = ITemplataT::Coord(
            self.typing_interner.alloc(element_placeholder));
        let placeholders = [
            size_placeholder, mutability_placeholder, variability_placeholder, element_placeholder_templata,
        ];
        // val id = templateId.copy(localName = templateId.localName.makeCitizenName(interner, placeholders))
        let local_name = template_name.make_citizen_name(self.typing_interner, &placeholders);
        let id = self.typing_interner.intern_id(IdValT {
            package_coord: builtin_package,
            init_steps: &[],
            local_name,
        });
        // vassert(TemplataCompiler.getTemplate(id) == templateId)
        assert!(*Compiler::get_template(self.typing_interner, *id) == *template_id);

        // val arrayInnerEnv =
        //   arrayOuterEnv.copy(
        //     id = id,
        //     templatas = arrayOuterEnv.templatas.copy(templatasStoreName = id))
        let inner_templatas = TemplatasStoreBuilder::new(id).build_in(self.typing_interner);
        let array_inner_env = self.typing_interner.alloc(CitizenEnvironmentT {
            global_env,
            parent_env: array_outer_env.parent_env,
            template_id: array_outer_env.template_id,
            id: *id,
            templatas: inner_templatas,
        });
        let array_inner_env_ref: IInDenizenEnvironmentT<'s, 't> =
            IInDenizenEnvironmentT::Citizen(array_inner_env);
        // coutputs.declareTypeInnerEnv(templateId, arrayInnerEnv)
        coutputs.declare_type_inner_env(template_id, array_inner_env_ref);
    }

    pub fn resolve_static_sized_array(
        &self,
        mutability: ITemplataT<'s, 't>,
        variability: ITemplataT<'s, 't>,
        size: ITemplataT<'s, 't>,
        type_2: CoordT<'s, 't>,
        region: RegionT,
    ) -> StaticSizedArrayTT<'s, 't> {
        let builtin_package: &'s PackageCoordinate<'s> =
            self.scout_arena.intern_package_coordinate(self.keywords.empty_string, &[]);
        let template_name = self.typing_interner.intern_static_sized_array_template_name(
            StaticSizedArrayTemplateNameT { }
        );
        let arr_name = self.typing_interner.intern_raw_array_name(
            RawArrayNameT { mutability, element_type: type_2, self_region: region }
        );
        let ssa_name = self.typing_interner.intern_static_sized_array_name(
            StaticSizedArrayNameT { template: template_name, size, variability, arr: arr_name }
        );
        let id = *self.typing_interner.intern_id(IdValT {
            package_coord: builtin_package,
            init_steps: &[],
            local_name: INameT::StaticSizedArray(ssa_name),
        });
        *self.typing_interner.intern_static_sized_array_tt(StaticSizedArrayTTValT { name: id })
    }

    pub fn compile_runtime_sized_array(&self, global_env: &'t GlobalEnvironmentT<'s, 't>, coutputs: &mut CompilerOutputs<'s, 't>) {
        // val builtinPackage = PackageCoordinate.BUILTIN(interner, keywords)
        let builtin_package: &'s PackageCoordinate<'s> =
            self.scout_arena.intern_package_coordinate(self.keywords.empty_string, &[]);
        // val templateId =
        //   IdT(builtinPackage, Vector.empty, interner.intern(RuntimeSizedArrayTemplateNameT()))
        let template_name = self.typing_interner.intern_runtime_sized_array_template_name(
            RuntimeSizedArrayTemplateNameT { }
        );
        let template_id = self.typing_interner.intern_id(IdValT {
            package_coord: builtin_package,
            init_steps: &[],
            local_name: INameT::RuntimeSizedArrayTemplate(template_name),
        });

        // See CSFMSEO and SAFHE.
        // val arrayOuterEnv =
        //   CitizenEnvironmentT(
        //     globalEnv,
        //     PackageEnvironmentT(globalEnv, templateId, globalEnv.nameToTopLevelEnvironment.values.toVector),
        //     templateId,
        //     templateId,
        //     TemplatasStore(templateId, Map(), Map()))
        let global_namespaces: Vec<&TemplatasStoreT<'s, 't>> =
            global_env.name_to_top_level_environment.iter().map(|(_, ts)| *ts).collect();
        let global_namespaces = self.typing_interner.alloc_slice_from_vec(global_namespaces);
        let parent_env = self.typing_interner.alloc(PackageEnvironmentT {
            global_env,
            id: *template_id,
            global_namespaces,
        });
        let empty_templatas = TemplatasStoreBuilder::new(template_id).build_in(self.typing_interner);
        let array_outer_env = self.typing_interner.alloc(CitizenEnvironmentT {
            global_env,
            parent_env: IEnvironmentT::Package(parent_env),
            template_id: *template_id,
            id: *template_id,
            templatas: empty_templatas,
        });
        // coutputs.declareType(templateId)
        coutputs.declare_type(template_id);
        // coutputs.declareTypeOuterEnv(templateId, arrayOuterEnv)
        let array_outer_env_ref: IInDenizenEnvironmentT<'s, 't> =
            IInDenizenEnvironmentT::Citizen(array_outer_env);
        coutputs.declare_type_outer_env(template_id, array_outer_env_ref);

        // val TemplateTemplataType(types, _) = RuntimeSizedArrayTemplateTemplataT().tyype
        // val Vector(MutabilityTemplataType(), CoordTemplataType()) = types
        // (assertion only — types are verified by the placeholder calls below)

        // val mutabilityPlaceholder =
        //   templataCompiler.createNonKindNonRegionPlaceholderInner(
        //     templateId, 0, CodeRuneS(interner.intern(StrI("M"))), MutabilityTemplataType())
        let rune_m = self.scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
            name: self.scout_arena.intern_str("M"),
        }));
        let mutability_placeholder = self.create_non_kind_non_region_placeholder_inner(
            *template_id, 0, rune_m, ITemplataType::MutabilityTemplataType(MutabilityTemplataType {}),
        );
        // val elementPlaceholder =
        //   templataCompiler.createCoordPlaceholderInner(
        //     coutputs, arrayOuterEnv, templateId, 1, CodeRuneS(interner.intern(StrI("E"))), None, ReadOnlyRegionS, OwnT, true)
        let rune_e = self.scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
            name: self.scout_arena.intern_str("E"),
        }));
        let element_placeholder = self.create_coord_placeholder_inner(
            coutputs,
            array_outer_env_ref,
            *template_id, 1, rune_e, None,
            IRegionMutabilityS::ReadOnlyRegion, OwnershipT::Own, true,
        );

        // val placeholders =
        //   Vector(mutabilityPlaceholder, elementPlaceholder)
        let element_placeholder_templata = ITemplataT::Coord(
            self.typing_interner.alloc(element_placeholder));
        let placeholders = [mutability_placeholder, element_placeholder_templata];
        // val id = templateId.copy(localName = templateId.localName.makeCitizenName(interner, placeholders))
        let local_name = template_name.make_citizen_name(self.typing_interner, &placeholders);
        let id = self.typing_interner.intern_id(IdValT {
            package_coord: builtin_package,
            init_steps: &[],
            local_name,
        });

        // val arrayInnerEnv =
        //   arrayOuterEnv.copy(
        //     id = id,
        //     templatas = arrayOuterEnv.templatas.copy(templatasStoreName = id))
        let inner_templatas = TemplatasStoreBuilder::new(id).build_in(self.typing_interner);
        let array_inner_env = self.typing_interner.alloc(CitizenEnvironmentT {
            global_env,
            parent_env: array_outer_env.parent_env,
            template_id: array_outer_env.template_id,
            id: *id,
            templatas: inner_templatas,
        });
        let array_inner_env_ref: IInDenizenEnvironmentT<'s, 't> =
            IInDenizenEnvironmentT::Citizen(array_inner_env);
        // coutputs.declareTypeInnerEnv(templateId, arrayInnerEnv)
        coutputs.declare_type_inner_env(template_id, array_inner_env_ref);
    }

    pub fn resolve_runtime_sized_array(
        &self,
        type_2: CoordT<'s, 't>,
        mutability: ITemplataT<'s, 't>,
        region: RegionT,
    ) -> RuntimeSizedArrayTT<'s, 't> {
        let builtin_package: &'s PackageCoordinate<'s> =
            self.scout_arena.intern_package_coordinate(self.keywords.empty_string, &[]);
        let template_name = self.typing_interner.intern_runtime_sized_array_template_name(
            RuntimeSizedArrayTemplateNameT { }
        );
        let arr_name = self.typing_interner.intern_raw_array_name(
            RawArrayNameT { mutability, element_type: type_2, self_region: region }
        );
        let rsa_name = self.typing_interner.intern_runtime_sized_array_name(
            RuntimeSizedArrayNameT { template: template_name, arr: arr_name }
        );
        let id = *self.typing_interner.intern_id(IdValT {
            package_coord: builtin_package,
            init_steps: &[],
            local_name: INameT::RuntimeSizedArray(rsa_name),
        });
        *self.typing_interner.intern_runtime_sized_array_tt(RuntimeSizedArrayTTValT { name: id })
    }

    fn get_array_size(&self, templatas: &IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>, size_rune_a: IRuneS<'s>) -> i32 {
        panic!("Unimplemented: get_array_size");
    }

    fn get_array_element_type(&self, templatas: &IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>, type_rune_a: IRuneS<'s>) -> CoordT<'s, 't> {
        let coord_templata = expect_coord_templata(*templatas.get(&type_rune_a).expect("vassertSome: typeRuneA not in templatas"));
        coord_templata.coord
    }

    pub fn lookup_in_static_sized_array(
        &self,
        range: RangeS<'s>,
        container_expr_2: ReferenceExpressionTE<'s, 't>,
        index_expr_2: ReferenceExpressionTE<'s, 't>,
        at: StaticSizedArrayTT<'s, 't>,
    ) -> StaticSizedArrayLookupTE<'s, 't> {
        let variability_templata = at.variability();
        let variability = match variability_templata {
            ITemplataT::Placeholder(_) => VariabilityT::Final,
            ITemplataT::Variability(VariabilityTemplataT { variability }) => variability,
            _ => panic!("vwat"),
        };
        let member_type = at.element_type();
        StaticSizedArrayLookupTE {
            range,
            array_expr: container_expr_2,
            array_type: self.typing_interner.alloc(at),
            index_expr: index_expr_2,
            element_type: member_type,
            variability,
        }
    }

    pub fn lookup_in_unknown_sized_array(
        &self,
        parent_ranges: &[RangeS<'s>],
        range: RangeS<'s>,
        container_expr_2: ReferenceExpressionTE<'s, 't>,
        index_expr_2: ReferenceExpressionTE<'s, 't>,
        rsa: &'t RuntimeSizedArrayTT<'s, 't>,
    ) -> Result<RuntimeSizedArrayLookupTE<'s, 't>, ICompileErrorT<'s, 't>> {
        if index_expr_2.result().coord.kind != KindT::Int(IntT::I32) {
            let range_with_parent: Vec<RangeS<'s>> =
                once(range).chain(parent_ranges.iter().copied()).collect();
            return Err(ICompileErrorT::IndexedArrayWithNonInteger {
                range: self.typing_interner.alloc_slice_from_vec(range_with_parent),
                types: index_expr_2.result().coord,
            });
        }
        let variability = match rsa.mutability() {
            ITemplataT::Placeholder(_) => VariabilityT::Final,
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => VariabilityT::Final,
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) => VariabilityT::Varying,
            _ => panic!("vwat"),
        };
        Ok(RuntimeSizedArrayLookupTE::new(range, container_expr_2, rsa, index_expr_2, variability))
    }

}
