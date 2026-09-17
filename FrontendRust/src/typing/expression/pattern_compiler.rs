use crate::typing::compiler::Compiler;
use crate::postparsing::ast::LocationInDenizen;
use crate::utils::range::RangeS;
use crate::postparsing::names::*;
use crate::postparsing::patterns::patterns::AtomSP;
use crate::postparsing::rules::rules::{IRulexSR, RuneParentEnvLookupSR};
use crate::postparsing::itemplatatype::ITemplataType;
use crate::typing::ast::ast::*;
use crate::typing::ast::expressions::*;
use crate::typing::env::environment::*;
use crate::typing::env::function_environment_t::*;
use crate::typing::names::names::*;
use crate::typing::types::types::*;
use crate::typing::compiler_outputs::*;
use crate::typing::env::i_env_entry::IEnvEntryT;
use crate::postparsing::rules::RuneUsage;
use crate::typing::infer_compiler::{InferEnv, InitialKnown, InitialSend};
use crate::typing::templata::templata::{ITemplataT, CoordTemplataT};
use crate::typing::templata_compiler::IBoundArgumentsSource;
use crate::typing::ast::citizens::{IStructMemberT, NormalStructMemberT, IMemberTypeT, ReferenceMemberTypeT};
use crate::parsing::ast::LoadAsP;
use crate::postparsing::expressions::IExpressionSE;
use indexmap::IndexMap;
use std::collections::HashMap;
use std::collections::HashSet;
use crate::postparsing::names::IRuneValS;
use crate::higher_typing::higher_typing_pass::explicify_lookups;
use std::iter::once;
use std::marker::PhantomData;

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't, 't: 'ctx, 's: 'ctx,
{
    pub fn translate_pattern_list_pattern(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        patterns_a: &'t [&'s AtomSP<'s>],
        pattern_inputs_te: &'t [ReferenceExpressionTE<'s, 't>],
        region: RegionT,
        after_patterns_success_continuation: impl FnOnce(
            &Compiler<'s, 'ctx, 't>,
            &mut CompilerOutputs<'s, 't>,
            &mut NodeEnvironmentBox<'s, 't>,
            &[ILocalVariableT<'s, 't>],
        ) -> ReferenceExpressionTE<'s, 't> + 'ctx,
    ) -> ReferenceExpressionTE<'s, 't> {
        self.iterate_translate_list_and_maybe_continue(
            coutputs, nenv, life, parent_ranges, call_location,
            self.typing_interner.alloc_slice_copy(&[]), patterns_a, pattern_inputs_te, region,
            after_patterns_success_continuation)
    }

    pub fn iterate_translate_list_and_maybe_continue(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        live_capture_locals: &'t [ILocalVariableT<'s, 't>],
        patterns_a: &'t [&'s AtomSP<'s>],
        pattern_inputs_te: &'t [ReferenceExpressionTE<'s, 't>],
        region: RegionT,
        after_patterns_success_continuation: impl FnOnce(
            &Compiler<'s, 'ctx, 't>,
            &mut CompilerOutputs<'s, 't>,
            &mut NodeEnvironmentBox<'s, 't>,
            &[ILocalVariableT<'s, 't>],
        ) -> ReferenceExpressionTE<'s, 't> + 'ctx,
    ) -> ReferenceExpressionTE<'s, 't> {
        let names: Vec<_> = live_capture_locals.iter().map(|l| l.name()).collect();
        let distinct: HashSet<_> = names.iter().collect();
        assert!(names.len() == distinct.len());

        match (patterns_a.is_empty(), pattern_inputs_te.is_empty()) {
            (true, true) => after_patterns_success_continuation(self, coutputs, nenv, live_capture_locals),
            (false, false) => {
                let head_pattern_a = patterns_a[0];
                let head_pattern_input_te = pattern_inputs_te[0];
                let tail_patterns_a: &'t [&'s AtomSP<'s>] = &patterns_a[1..];
                let tail_pattern_inputs_te: &'t [ReferenceExpressionTE<'s, 't>] = &pattern_inputs_te[1..];
                self.inner_translate_sub_pattern_and_maybe_continue(
                    coutputs, nenv, life.add(self.typing_interner, 0), parent_ranges, call_location,
                    head_pattern_a, live_capture_locals, head_pattern_input_te, region,
                    move |compiler, coutputs, nenv, _life, live_capture_locals_raw| {
                        let live_capture_locals: &'t [ILocalVariableT<'s, 't>] = compiler.typing_interner.alloc_slice_copy(live_capture_locals_raw);
                        let names: Vec<_> = live_capture_locals.iter().map(|l| l.name()).collect();
                        let distinct: HashSet<_> = names.iter().collect();
                        assert!(names.len() == distinct.len());

                        compiler.iterate_translate_list_and_maybe_continue(
                            coutputs, nenv, life.add(compiler.typing_interner, 1), parent_ranges, call_location,
                            live_capture_locals, tail_patterns_a, tail_pattern_inputs_te, region,
                            after_patterns_success_continuation)
                    })
            }
            _ => panic!("mismatched patterns and inputs"),
        }
    }

    pub fn infer_and_translate_pattern(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        rules_with_implicitly_coercing_lookups_s: &[IRulexSR<'s>],
        rune_a_to_type_with_implicitly_coercing_lookups_s: &IndexMap<IRuneS<'s>, ITemplataType<'s>>,
        pattern: &'s AtomSP<'s>,
        unconverted_input_expr: ReferenceExpressionTE<'s, 't>,
        region: RegionT,
        after_patterns_success_continuation: impl FnOnce(
            &Compiler<'s, 'ctx, 't>,
            &mut CompilerOutputs<'s, 't>,
            &mut NodeEnvironmentBox<'s, 't>,
            LocationInFunctionEnvironmentT<'t>,
            &[ILocalVariableT<'s, 't>],
        ) -> ReferenceExpressionTE<'s, 't> + 'ctx,
    ) -> ReferenceExpressionTE<'s, 't> {
        // The rules are different depending on the incoming type.
        // See Impl Rule For Upcasts (IRFU).
        let converted_input_expr = match &pattern.coord_rune {
            None => {
                unconverted_input_expr
            }
            Some(receiver_rune) => {
                let mut rune_a_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
                    rune_a_to_type_with_implicitly_coercing_lookups_s.clone();
                // We've now calculated all the types of all the runes, but the LookupSR rules are still a bit
                // loose. We intentionally ignored the types of the things they're looking up, so we could know
                // what types we *expect* them to be, so we could coerce.
                // That coercion is good, but lets make it more explicit.
                let mut rule_builder: Vec<IRulexSR<'s>> = Vec::new();
                let snapshot = nenv.snapshot(self.typing_interner);
                let snapshot_env = IInDenizenEnvironmentT::Node(snapshot);
                let rune_type_solve_env = self.create_rune_type_solver_env(snapshot_env);
                match explicify_lookups(
                    &rune_type_solve_env,
                    self.scout_arena,
                    &mut rune_a_to_type,
                    &mut rule_builder,
                    rules_with_implicitly_coercing_lookups_s.to_vec(),
                ) {
                    Err(_e) => panic!("implement: infer_and_translate_pattern — explicifyLookups error"),
                    Ok(()) => {}
                }
                let rules_a = rule_builder;

                // We preprocess out the rune parent env lookups, see MKRFA.
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
                                    let templata = snapshot_env.lookup_nearest_with_imprecise_name(
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

                let invocation_range: Vec<RangeS<'s>> =
                    once(pattern.range).chain(parent_ranges.iter().copied()).collect();
                let complete_define_solve =
                    // We could probably just solveForResolving (see DBDAR) but seems right to solveForDefining since we're
                    // declaring a bunch of things.
                    self.solve_for_defining(
                        InferEnv {
                            original_calling_env: snapshot_env,
                            parent_ranges: self.typing_interner.alloc_slice_copy(parent_ranges),
                            call_location,
                            self_env: IEnvironmentT::from(IInDenizenEnvironmentT::Node(snapshot)),
                            context_region: nenv.default_region(),
                        },
                        coutputs,
                        &rules_without_rune_parent_env_lookups,
                        &rune_a_to_type,
                        &invocation_range,
                        call_location,
                        &initial_knowns,
                        &[InitialSend {
                            sender_rune: RuneUsage {
                                range: pattern.range,
                                rune: self.scout_arena.intern_rune(
                                    IRuneValS::PatternInputRune(PatternInputRuneS {
                                        code_loc: pattern.range.begin,
                                    })),
                            },
                            receiver_rune: receiver_rune.clone(),
                            send_templata: ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT {
                                coord: unconverted_input_expr.result().coord,
                            })),
                        }],
                        &[],
                    ).unwrap_or_else(|_f| {
                        panic!("implement: infer_and_translate_pattern — TypingPassDefiningError");
                    });

                nenv.add_entries(
                    self.scout_arena,
                    self.typing_interner,
                    &complete_define_solve.conclusions.iter()
                        .map(|(key, value)| {
                            let name: INameT<'s, 't> = self.typing_interner.intern_rune_name(RuneNameT { rune: *key}).into();
                            let entry = IEnvEntryT::Templata(*value);
                            (name, entry)
                        })
                        .collect::<Vec<_>>());
                let expected_coord = match complete_define_solve.conclusions.get(&receiver_rune.rune) {
                    Some(ITemplataT::Coord(coord_templata)) => coord_templata.coord,
                    _ => panic!("Expected coord templata for receiver rune"),
                };

                let range_list: Vec<RangeS<'s>> =
                    once(pattern.range).chain(parent_ranges.iter().copied()).collect();
                self.convert(
                    snapshot_env, coutputs, &range_list, call_location,
                    unconverted_input_expr, expected_coord)
            }
        };

        self.inner_translate_sub_pattern_and_maybe_continue(
            coutputs, nenv, life, parent_ranges, call_location,
            pattern, self.typing_interner.alloc_slice_copy(&[]), converted_input_expr, region,
            move |compiler, coutputs, nenv, life, live_capture_locals| {
                after_patterns_success_continuation(compiler, coutputs, nenv, life, live_capture_locals)
            })
    }

    pub fn inner_translate_sub_pattern_and_maybe_continue(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        pattern: &'s AtomSP<'s>,
        previous_live_capture_locals: &'t [ILocalVariableT<'s, 't>],
        input_expr: ReferenceExpressionTE<'s, 't>,
        region: RegionT,
        after_sub_pattern_success_continuation: impl FnOnce(
            &Compiler<'s, 'ctx, 't>,
            &mut CompilerOutputs<'s, 't>,
            &mut NodeEnvironmentBox<'s, 't>,
            LocationInFunctionEnvironmentT<'t>,
            &[ILocalVariableT<'s, 't>],
        ) -> ReferenceExpressionTE<'s, 't> + 'ctx,
    ) -> ReferenceExpressionTE<'s, 't> {
        {
            let names: Vec<_> = previous_live_capture_locals.iter().map(|l| l.name()).collect();
            let distinct: Vec<_> = {
                let mut seen = Vec::new();
                for n in &names {
                    if !seen.contains(n) { seen.push(*n); }
                }
                seen
            };
            assert!(names == distinct);
        }

        // TODO(CRASTBU): make test that we have the right type in there, cuz the coordRuneA seems to be unused

        let mut current_instructions: Vec<ReferenceExpressionTE<'s, 't>> = Vec::new();

        let (maybe_capture_local_var_t, expr_to_destructure_or_drop_or_pass_te) =
            match &pattern.name {
                None => (None, input_expr),
                Some(capture_s) => {
                    let local_name_t = self.translate_var_name_step(capture_s.name);
                    let range_list: Vec<RangeS<'s>> =
                        once(pattern.range).chain(parent_ranges.iter().copied()).collect();
                    let local_t = if capture_s.mutate {
                        let local_t = match nenv.declared_locals().iter().find(|v| v.name() == local_name_t) {
                            Some(IVariableT::ReferenceLocal(rlv)) => ILocalVariableT::Reference(*rlv),
                            _ => panic!("expected ReferenceLocalVariableT in declared_locals"),
                        };
                        nenv.mark_local_restackified(local_name_t);
                        current_instructions.push(
                            ReferenceExpressionTE::Restackify(self.typing_interner.alloc(RestackifyTE {
                                variable: local_t,
                                source_expr: input_expr,
                            })));
                        local_t
                    } else {
                        let (_block_env, block_expr) = nenv.nearest_block_env(self.typing_interner)
                            .expect("Expected nearest block env");
                        let block_se = match block_expr {
                            IExpressionSE::Block(b) => b,
                            _ => panic!("Expected BlockSE from nearestBlockEnv"),
                        };
                        let local_s = block_se.locals.iter()
                            .find(|l| l.var_name == capture_s.name)
                            .expect("Expected local");
                        let local_t = self.make_user_local_variable(
                            coutputs, nenv, &range_list, local_s, input_expr.result().coord);
                        current_instructions.push(
                            ReferenceExpressionTE::LetNormal(self.typing_interner.alloc(LetNormalTE {
                                variable: local_t,
                                expr: input_expr,
                            })));
                        local_t
                    };
                    let local_lookup =
                        AddressExpressionTE::LocalLookup(self.typing_interner.alloc(LocalLookupTE {
                            range: pattern.range,
                            local_variable: local_t,
                        }));
                    let captured_local_alias_te =
                        self.soft_load(nenv, &range_list, local_lookup, LoadAsP::LoadAsBorrow, region);
                    (Some(local_t), captured_local_alias_te)
                }
            };

        if maybe_capture_local_var_t.is_some() {
            assert!(expr_to_destructure_or_drop_or_pass_te.result().coord.ownership != OwnershipT::Own);
        }

        let mut live_capture_locals: Vec<ILocalVariableT<'s, 't>> = previous_live_capture_locals.to_vec();
        if let Some(local_t) = maybe_capture_local_var_t {
            live_capture_locals.push(local_t);
        }
        {
            let names: Vec<_> = live_capture_locals.iter().map(|l| l.name()).collect();
            let distinct: Vec<_> = {
                let mut seen = Vec::new();
                for n in &names {
                    if !seen.contains(n) { seen.push(*n); }
                }
                seen
            };
            assert!(names == distinct);
        }

        let destructure_exprs: Vec<ReferenceExpressionTE<'s, 't>> = match pattern.destructure {
            None => {
                let mut result: Vec<ReferenceExpressionTE<'s, 't>> = Vec::new();
                match &pattern.name {
                    None => {
                        // If we didn't store it, and we aren't destructuring it, then we're just ignoring it. Let's drop it.
                        let snap = IInDenizenEnvironmentT::Node(nenv.snapshot(self.typing_interner));
                        let ranges: Vec<RangeS<'s>> =
                            once(pattern.range).chain(parent_ranges.iter().copied()).collect();
                        // Until a test path forces Result conversion through this pattern_compiler site.
                        result.push(self.drop(snap, coutputs, &ranges, call_location, region, expr_to_destructure_or_drop_or_pass_te)
                            .unwrap_or_else(|_| panic!("Unimplemented: Result propagation through pattern_compiler drop")));
                    }
                    Some(_) => {
                        // We aren't destructuring it, but we stored it, so just do nothing.
                    }
                }
                result.push(after_sub_pattern_success_continuation(
                    self, coutputs, nenv, life.add(self.typing_interner, 0), &live_capture_locals));
                result
            }
            Some(list_of_maybe_destructure_member_patterns) => {
                let ranges: &'t [RangeS<'s>] = self.typing_interner.alloc_slice_copy(
                    &once(pattern.range).chain(parent_ranges.iter().copied()).collect::<Vec<_>>());
                let list_refs: &'t [&'s AtomSP<'s>] = self.typing_interner.alloc_slice_copy(
                    &list_of_maybe_destructure_member_patterns.iter().collect::<Vec<_>>());
                let live_capture_locals_t: &'t [ILocalVariableT<'s, 't>] = self.typing_interner.alloc_slice_copy(&live_capture_locals);
                match expr_to_destructure_or_drop_or_pass_te.result().coord.ownership {
                    OwnershipT::Own => {
                        vec![self.destructure_owning(
                            coutputs, nenv, life.add(self.typing_interner, 1),
                            ranges, call_location, live_capture_locals_t,
                            expr_to_destructure_or_drop_or_pass_te,
                            list_refs,
                            region,
                            after_sub_pattern_success_continuation)]
                    }
                    OwnershipT::Borrow | OwnershipT::Share => {
                        vec![self.destructure_non_owning_and_maybe_continue(
                            coutputs, nenv, life.add(self.typing_interner, 2),
                            ranges, call_location, live_capture_locals_t,
                            expr_to_destructure_or_drop_or_pass_te,
                            list_refs,
                            region,
                            after_sub_pattern_success_continuation)]
                    }
                    OwnershipT::Weak => panic!("implement: innerTranslateSubPatternAndMaybeContinue — destructure weak"),
                }
            }
        };

        let mut all_exprs = current_instructions;
        all_exprs.extend(destructure_exprs);
        self.consecutive(&all_exprs)
    }

    pub fn destructure_owning(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        initial_live_capture_locals: &'t [ILocalVariableT<'s, 't>],
        input_expr: ReferenceExpressionTE<'s, 't>,
        list_of_maybe_destructure_member_patterns: &'t [&'s AtomSP<'s>],
        region: RegionT,
        after_destructure_success_continuation: impl FnOnce(
            &Compiler<'s, 'ctx, 't>,
            &mut CompilerOutputs<'s, 't>,
            &mut NodeEnvironmentBox<'s, 't>,
            LocationInFunctionEnvironmentT<'t>,
            &[ILocalVariableT<'s, 't>],
        ) -> ReferenceExpressionTE<'s, 't> + 'ctx,
    ) -> ReferenceExpressionTE<'s, 't> {
        {
            let names: Vec<_> = initial_live_capture_locals.iter().map(|l| l.name()).collect();
            let distinct: Vec<_> = {
                let mut seen = Vec::new();
                for n in &names { if !seen.contains(n) { seen.push(*n); } }
                seen
            };
            assert!(names == distinct);
        }
        let expected_container_kind = match input_expr.result().coord.ownership {
            OwnershipT::Own => input_expr.result().coord.kind.clone(),
            _ => panic!("destructureOwning: expected Own"),
        };
        match expected_container_kind {
            KindT::Struct(_) => {
                // Example:
                //   struct Marine { bork: Bork; }
                //   Marine(b) = m;
                // In this case, expectedStructType1 = TypeName1("Marine") and
                // destructureMemberPatterns = Vector(CaptureSP("b", FinalP, None)).
                // Since we're receiving an owning reference, and we're *not* capturing
                // it in a variable, it will be destroyed and we will harvest its parts.
                self.translate_destroy_struct_inner_and_maybe_continue(
                    coutputs, nenv, life.add(self.typing_interner, 0),
                    parent_ranges, call_location, initial_live_capture_locals,
                    list_of_maybe_destructure_member_patterns, input_expr, region,
                    after_destructure_success_continuation)
            }
            KindT::StaticSizedArray(static_sized_array_t) => {
                let size_templata = static_sized_array_t.size();
                let size = match size_templata {
                    ITemplataT::Placeholder(_) => panic!("implement: destructureOwning StaticSizedArray — RangedInternalErrorT: Can't create static sized array by values, can't guarantee size is correct!"),
                    ITemplataT::Integer(size) => {
                        if size != list_of_maybe_destructure_member_patterns.len() as i64 {
                            panic!("implement: destructureOwning StaticSizedArray — RangedInternalErrorT: Wrong num exprs!");
                        }
                        size
                    }
                    _ => panic!("vwat"),
                };
                let element_type = static_sized_array_t.element_type();
                let element_locals: Vec<ReferenceLocalVariableT<'s, 't>> = (0..size as usize).map(|i| {
                    self.make_temporary_local(nenv, life.add(self.typing_interner, (3 + i) as i32), element_type)
                }).collect();
                let destroy_te = ReferenceExpressionTE::DestroyStaticSizedArrayIntoLocals(self.typing_interner.alloc(DestroyStaticSizedArrayIntoLocalsTE {
                    expr: input_expr,
                    static_sized_array: self.typing_interner.alloc(*static_sized_array_t),
                    destination_reference_variables: self.typing_interner.alloc_slice_from_vec(element_locals.clone()),
                }));
                let live_capture_locals: Vec<ILocalVariableT<'s, 't>> = initial_live_capture_locals.iter().copied()
                    .chain(element_locals.iter().map(|l: &ReferenceLocalVariableT<'s, 't>| ILocalVariableT::Reference(*l)))
                    .collect();
                {
                    let names: Vec<_> = live_capture_locals.iter().map(|l: &ILocalVariableT<'s, 't>| l.name()).collect();
                    let distinct: Vec<_> = { let mut seen = Vec::new(); for n in &names { if !seen.contains(n) { seen.push(*n); } } seen };
                    assert!(names == distinct);
                }
                if element_locals.len() != list_of_maybe_destructure_member_patterns.len() {
                    panic!("implement: destructureOwning StaticSizedArray — WrongNumberOfDestructuresError");
                }
                let live_capture_locals_slice = self.typing_interner.alloc_slice_from_vec(live_capture_locals);
                let element_locals_slice = self.typing_interner.alloc_slice_from_vec(
                    element_locals.into_iter().map(|l| ILocalVariableT::Reference(l)).collect()
                );
                let lets = self.make_lets_for_own_and_maybe_continue(
                    coutputs, nenv, life.add(self.typing_interner, 4), parent_ranges, call_location,
                    live_capture_locals_slice, element_locals_slice, list_of_maybe_destructure_member_patterns, region,
                    Box::new(after_destructure_success_continuation));
                self.consecutive(&[destroy_te, lets])
            }
            KindT::RuntimeSizedArray(_) => {
                if !list_of_maybe_destructure_member_patterns.is_empty() {
                    panic!("implement: destructureOwning RuntimeSizedArray — RangedInternalErrorT: Can only destruct RSA with zero destructure targets.");
                }
                ReferenceExpressionTE::DestroyMutRuntimeSizedArray(self.typing_interner.alloc(DestroyMutRuntimeSizedArrayTE {
                    array_expr: input_expr,
                }))
            }
            _ => panic!("implement: destructureOwning — non-struct kind"),
        }
    }

    pub fn destructure_non_owning_and_maybe_continue(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        range: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        live_capture_locals: &'t [ILocalVariableT<'s, 't>],
        container_te: ReferenceExpressionTE<'s, 't>,
        list_of_maybe_destructure_member_patterns: &'t [&'s AtomSP<'s>],
        region: RegionT,
        after_destructure_success_continuation: impl FnOnce(
            &Compiler<'s, 'ctx, 't>,
            &mut CompilerOutputs<'s, 't>,
            &mut NodeEnvironmentBox<'s, 't>,
            LocationInFunctionEnvironmentT<'t>,
            &[ILocalVariableT<'s, 't>],
        ) -> ReferenceExpressionTE<'s, 't> + 'ctx,
    ) -> ReferenceExpressionTE<'s, 't> {
        {
            let names: Vec<_> = live_capture_locals.iter().map(|l| l.name()).collect();
            let distinct: Vec<_> = {
                let mut seen = Vec::new();
                for n in &names { if !seen.contains(n) { seen.push(*n); } }
                seen
            };
            assert!(names == distinct);
        }

        let local_t = self.make_temporary_local(nenv, life.add(self.typing_interner, 0), container_te.result().coord);
        let let_te = ReferenceExpressionTE::LetNormal(self.typing_interner.alloc(LetNormalTE {
            variable: ILocalVariableT::Reference(local_t),
            expr: container_te,
        }));
        let local_lookup = AddressExpressionTE::LocalLookup(self.typing_interner.alloc(LocalLookupTE {
            range: range[0],
            local_variable: ILocalVariableT::Reference(local_t),
        }));
        let container_aliasing_expr_te: ReferenceExpressionTE<'s, 't> =
            self.soft_load(nenv, range, local_lookup, LoadAsP::LoadAsBorrow, region);
        let iterate_expr = self.iterate_destructure_non_owning_and_maybe_continue(
            coutputs, nenv, life.add(self.typing_interner, 1), range, call_location, live_capture_locals,
            container_te.result().coord, container_aliasing_expr_te, 0,
            list_of_maybe_destructure_member_patterns, region, Box::new(after_destructure_success_continuation));
        self.consecutive(&[let_te, iterate_expr])
    }

    pub fn iterate_destructure_non_owning_and_maybe_continue(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        live_capture_locals: &'t [ILocalVariableT<'s, 't>],
        expected_container_coord: CoordT<'s, 't>,
        container_aliasing_expr_te: ReferenceExpressionTE<'s, 't>,
        member_index: i32,
        list_of_maybe_destructure_member_patterns: &'t [&'s AtomSP<'s>],
        region: RegionT,
        after_destructure_success_continuation: Box<dyn FnOnce(
            &Compiler<'s, 'ctx, 't>,
            &mut CompilerOutputs<'s, 't>,
            &mut NodeEnvironmentBox<'s, 't>,
            LocationInFunctionEnvironmentT<'t>,
            &[ILocalVariableT<'s, 't>],
        ) -> ReferenceExpressionTE<'s, 't> + 'ctx>,
    ) -> ReferenceExpressionTE<'s, 't> {
        {
            let names: Vec<_> = live_capture_locals.iter().map(|l| l.name()).collect();
            let distinct: Vec<_> = {
                let mut seen = Vec::new();
                for n in &names { if !seen.contains(n) { seen.push(*n); } }
                seen
            };
            assert!(names == distinct);
        }

        let CoordT { kind: expected_container_kind, .. } = expected_container_coord;

        match list_of_maybe_destructure_member_patterns {
            [] => after_destructure_success_continuation(self, coutputs, nenv, life.add(self.typing_interner, 0), live_capture_locals),
            [head_maybe_destructure_member_pattern, tail_destructure_member_pattern_maybes @ ..] => {
                let head_maybe_destructure_member_pattern = *head_maybe_destructure_member_pattern;
                let tail_destructure_member_pattern_maybes: &'t [&'s AtomSP<'s>] = self.typing_interner.alloc_slice_copy(tail_destructure_member_pattern_maybes);
                let env = IInDenizenEnvironmentT::Node(nenv.snapshot(self.typing_interner));
                let member_addr_expr_te = match expected_container_kind {
                    KindT::Struct(struct_tt) => {
                        self.load_from_struct(coutputs, env, head_maybe_destructure_member_pattern.range, region, container_aliasing_expr_te, *struct_tt, member_index)
                    }
                    KindT::StaticSizedArray(static_sized_array_t) => {
                        self.load_from_static_sized_array(head_maybe_destructure_member_pattern.range, *static_sized_array_t, expected_container_coord, expected_container_coord.ownership, container_aliasing_expr_te, member_index)
                    }
                    _ => panic!("implement: iterate_destructure_non_owning_and_maybe_continue — unknown container kind"),
                };
                let member_ownership_in_struct = member_addr_expr_te.result().coord.ownership;
                let coerce_to_ownership = self.load_result_ownership(member_ownership_in_struct);
                let load_expr = ReferenceExpressionTE::SoftLoad(self.typing_interner.alloc(SoftLoadTE {
                    expr: member_addr_expr_te,
                    target_ownership: coerce_to_ownership,
                }));
                let next_member_index = member_index + 1;
                self.inner_translate_sub_pattern_and_maybe_continue(
                    coutputs, nenv, life.add(self.typing_interner, 1), parent_ranges, call_location,
                    head_maybe_destructure_member_pattern, live_capture_locals, load_expr, region,
                    Box::new(move |compiler: &Compiler<'s, 'ctx, 't>, coutputs: &mut CompilerOutputs<'s, 't>, nenv: &mut NodeEnvironmentBox<'s, 't>, life: LocationInFunctionEnvironmentT<'t>, live_capture_locals: &[ILocalVariableT<'s, 't>]| {
                        let live_capture_locals: &'t [ILocalVariableT<'s, 't>] = compiler.typing_interner.alloc_slice_copy(live_capture_locals);
                        {
                            let names: Vec<_> = live_capture_locals.iter().map(|l| l.name()).collect();
                            let distinct: Vec<_> = {
                                let mut seen = Vec::new();
                                for n in &names { if !seen.contains(n) { seen.push(*n); } }
                                seen
                            };
                            assert!(names == distinct);
                        }
                        compiler.iterate_destructure_non_owning_and_maybe_continue(
                            coutputs, nenv, life,
                            parent_ranges, call_location, live_capture_locals,
                            expected_container_coord, container_aliasing_expr_te, next_member_index,
                            tail_destructure_member_pattern_maybes, region, after_destructure_success_continuation)
                    }))
            }
        }
    }

    pub fn translate_destroy_struct_inner_and_maybe_continue(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        initial_live_capture_locals: &'t [ILocalVariableT<'s, 't>],
        inner_patterns: &'t [&'s AtomSP<'s>],
        input_struct_expr: ReferenceExpressionTE<'s, 't>,
        region: RegionT,
        after_destroy_success_continuation: impl FnOnce(
            &Compiler<'s, 'ctx, 't>,
            &mut CompilerOutputs<'s, 't>,
            &mut NodeEnvironmentBox<'s, 't>,
            LocationInFunctionEnvironmentT<'t>,
            &[ILocalVariableT<'s, 't>],
        ) -> ReferenceExpressionTE<'s, 't> + 'ctx,
    ) -> ReferenceExpressionTE<'s, 't> {
        {
            let names: Vec<_> = initial_live_capture_locals.iter().map(|l| l.name()).collect();
            let distinct: Vec<_> = {
                let mut seen = Vec::new();
                for n in &names { if !seen.contains(n) { seen.push(*n); } }
                seen
            };
            assert!(names == distinct);
        }
        let struct_tt = match &input_struct_expr.result().coord.kind {
            KindT::Struct(s) => *s,
            _ => panic!("translateDestroyStructInnerAndMaybeContinue: expected Struct kind"),
        };
        // We don't pattern match against closure structs.
        let struct_def_t = coutputs.lookup_struct(struct_tt.id, self);
        let substituter = self.get_placeholder_substituter(
            self.opts.global_options.sanity_check,
            nenv.function_environment().template_id,
            struct_tt.id,
            IBoundArgumentsSource::InheritBoundsFromTypeItself,
        );
        let member_locals: Vec<ReferenceLocalVariableT<'s, 't>> = struct_def_t.members.iter()
            .enumerate()
            .map(|(i, member)| {
                let unsubstituted_member_coord = match member {
                    IStructMemberT::Normal(NormalStructMemberT { tyype: IMemberTypeT::Reference(ReferenceMemberTypeT { reference }), .. }) => *reference,
                    IStructMemberT::Normal(NormalStructMemberT { tyype: IMemberTypeT::Address(_), .. }) => panic!("implement: translateDestroyStructInnerAndMaybeContinue — AddressMemberTypeT"),
                    IStructMemberT::Variadic(_) => panic!("implement: translateDestroyStructInnerAndMaybeContinue — VariadicStructMemberT"),
                };
                let member_type = substituter.substitute_for_coord(coutputs, unsubstituted_member_coord);
                self.make_temporary_local(nenv, life.add(self.typing_interner, 1 + i as i32), member_type)
            })
            .collect();
        let struct_tt_ref = self.typing_interner.alloc(struct_tt);
        let member_locals_ref = self.typing_interner.alloc_slice_copy(&member_locals);
        let destroy_te = ReferenceExpressionTE::Destroy(self.typing_interner.alloc(DestroyTE {
            expr: input_struct_expr,
            struct_tt: struct_tt_ref,
            destination_reference_variables: member_locals_ref,
        }));
        let live_capture_locals: Vec<ILocalVariableT<'s, 't>> = initial_live_capture_locals.iter().copied()
            .chain(member_locals.iter().map(|l| ILocalVariableT::Reference(*l)))
            .collect();
        {
            let names: Vec<_> = live_capture_locals.iter().map(|l| l.name()).collect();
            let distinct: Vec<_> = {
                let mut seen = Vec::new();
                for n in &names { if !seen.contains(n) { seen.push(*n); } }
                seen
            };
            assert!(names == distinct);
        }
        if member_locals.len() != inner_patterns.len() {
            panic!("WrongNumberOfDestructuresError: expected {} got {}", inner_patterns.len(), member_locals.len());
        }
        let live_capture_locals: &'t [ILocalVariableT<'s, 't>] = self.typing_interner.alloc_slice_copy(&live_capture_locals);
        let member_locals_as_local: &'t [ILocalVariableT<'s, 't>] = self.typing_interner.alloc_slice_copy(
            &member_locals.iter().map(|l| ILocalVariableT::Reference(*l)).collect::<Vec<_>>());
        let rest_te = self.make_lets_for_own_and_maybe_continue(
            coutputs, nenv, life.add(self.typing_interner, 0),
            parent_ranges, call_location, live_capture_locals,
            member_locals_as_local, inner_patterns, region,
            Box::new(after_destroy_success_continuation));
        self.consecutive(&[destroy_te, rest_te])
    }

    pub fn make_lets_for_own_and_maybe_continue(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        initial_live_capture_locals: &'t [ILocalVariableT<'s, 't>],
        member_local_variables: &'t [ILocalVariableT<'s, 't>],
        inner_patterns: &'t [&'s AtomSP<'s>],
        region: RegionT,
        after_lets_success_continuation: Box<dyn FnOnce(
            &Compiler<'s, 'ctx, 't>,
            &mut CompilerOutputs<'s, 't>,
            &mut NodeEnvironmentBox<'s, 't>,
            LocationInFunctionEnvironmentT<'t>,
            &[ILocalVariableT<'s, 't>],
        ) -> ReferenceExpressionTE<'s, 't> + 'ctx>,
    ) -> ReferenceExpressionTE<'s, 't> {
        {
            let names: Vec<_> = initial_live_capture_locals.iter().map(|l| l.name()).collect();
            let distinct: Vec<_> = {
                let mut seen = Vec::new();
                for n in &names { if !seen.contains(n) { seen.push(*n); } }
                seen
            };
            assert!(names == distinct);
        }
        assert!(member_local_variables.len() == inner_patterns.len());
        match (member_local_variables, inner_patterns) {
            ([], []) => {
                after_lets_success_continuation(self, coutputs, nenv, life.add(self.typing_interner, 0), initial_live_capture_locals)
            }
            ([head_member_local_variable, tail_member_local_variables @ ..], [head_inner_pattern, tail_inner_pattern_maybes @ ..]) => {
                let unlet_expr = self.unlet_local_without_dropping(nenv, head_member_local_variable);
                let unlet_expr_te = ReferenceExpressionTE::Unlet(self.typing_interner.alloc(unlet_expr));
                let live_capture_locals: &'t [ILocalVariableT<'s, 't>] = self.typing_interner.alloc_slice_copy(
                    &initial_live_capture_locals.iter().copied()
                        .filter(|l| l.name() != head_member_local_variable.name())
                        .collect::<Vec<_>>());
                assert!(live_capture_locals.len() == initial_live_capture_locals.len() - 1);
                let head_inner_pattern_range = head_inner_pattern.range;
                let ranges: &'t [RangeS<'s>] = self.typing_interner.alloc_slice_copy(
                    &once(head_inner_pattern_range).chain(parent_ranges.iter().copied()).collect::<Vec<_>>());
                let tail_member_local_variables: &'t [ILocalVariableT<'s, 't>] = self.typing_interner.alloc_slice_copy(tail_member_local_variables);
                let tail_inner_pattern_maybes: &'t [&'s AtomSP<'s>] = self.typing_interner.alloc_slice_copy(tail_inner_pattern_maybes);
                self.inner_translate_sub_pattern_and_maybe_continue(
                    coutputs, nenv, life.add(self.typing_interner, 1),
                    ranges, call_location, head_inner_pattern,
                    live_capture_locals, unlet_expr_te, region,
                    move |compiler, coutputs, nenv, life, live_capture_locals_raw| {
                        let live_capture_locals: &'t [ILocalVariableT<'s, 't>] = compiler.typing_interner.alloc_slice_copy(live_capture_locals_raw);
                        {
                            let names: Vec<_> = initial_live_capture_locals.iter().map(|l| l.name()).collect();
                            let distinct: Vec<_> = {
                                let mut seen = Vec::new();
                                for n in &names { if !seen.contains(n) { seen.push(*n); } }
                                seen
                            };
                            assert!(names == distinct);
                        }
                        compiler.make_lets_for_own_and_maybe_continue(
                            coutputs, nenv, life, parent_ranges, call_location,
                            live_capture_locals, tail_member_local_variables,
                            tail_inner_pattern_maybes, region,
                            after_lets_success_continuation)
                    })
            }
            _ => panic!("make_lets_for_own_and_maybe_continue: mismatched lengths"),
        }
    }

    pub fn load_result_ownership(
        &self,
        member_ownership_in_struct: OwnershipT,
    ) -> OwnershipT {
        match member_ownership_in_struct {
            OwnershipT::Own => OwnershipT::Borrow,
            OwnershipT::Borrow => OwnershipT::Borrow,
            OwnershipT::Weak => OwnershipT::Weak,
            OwnershipT::Share => OwnershipT::Share,
        }
    }

    pub fn load_from_struct(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        env: IInDenizenEnvironmentT<'s, 't>,
        load_range: RangeS<'s>,
        region: RegionT,
        container_alias: ReferenceExpressionTE<'s, 't>,
        struct_tt: StructTT<'s, 't>,
        index: i32,
    ) -> AddressExpressionTE<'s, 't> {
        let struct_def_t = coutputs.lookup_struct(struct_tt.id, self);
        let member = &struct_def_t.members[index as usize];
        let (variability, unsubstituted_member_coord) = match member {
            IStructMemberT::Normal(NormalStructMemberT { variability, tyype: IMemberTypeT::Reference(ReferenceMemberTypeT { reference }), .. }) => (*variability, *reference),
            IStructMemberT::Normal(NormalStructMemberT { tyype: IMemberTypeT::Address(_), .. }) => panic!("implement: load_from_struct — AddressMemberTypeT"),
            IStructMemberT::Variadic(_) => panic!("implement: load_from_struct — VariadicStructMemberT"),
        };
        let instantiation_bounds = coutputs.get_instantiation_bounds(self.typing_interner, struct_tt.id).unwrap();
        let member_type = self.get_placeholder_substituter(
            self.opts.global_options.sanity_check,
            env.denizen_template_id(),
            struct_tt.id,
            IBoundArgumentsSource::UseBoundsFromContainer {
                instantiation_bound_params: struct_def_t.instantiation_bound_params,
                instantiation_bound_arguments: instantiation_bounds,
            },
        ).substitute_for_coord(coutputs, unsubstituted_member_coord);
        AddressExpressionTE::ReferenceMemberLookup(self.typing_interner.alloc(ReferenceMemberLookupTE {
            range: load_range,
            struct_expr: container_alias,
            member_name: *struct_def_t.members[index as usize].name(),
            member_reference: member_type,
            variability,
        }))
    }

    pub fn load_from_static_sized_array(
        &self,
        range: RangeS<'s>,
        static_sized_array_t: StaticSizedArrayTT<'s, 't>,
        _local_coord: CoordT<'s, 't>,
        _struct_ownership: OwnershipT,
        container_alias: ReferenceExpressionTE<'s, 't>,
        index: i32,
    ) -> AddressExpressionTE<'s, 't> {
        let index_expr = ReferenceExpressionTE::ConstantInt(self.typing_interner.alloc(ConstantIntTE {
            value: ITemplataT::Integer(index as i64),
            bits: 32,
            region: RegionT { region: IRegionT::Default },
        }));
        let lookup = self.lookup_in_static_sized_array(range, container_alias, index_expr, static_sized_array_t);
        AddressExpressionTE::StaticSizedArrayLookup(self.typing_interner.alloc(lookup))
    }

}