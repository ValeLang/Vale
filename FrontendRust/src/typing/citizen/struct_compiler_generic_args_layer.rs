use crate::utils::range::RangeS;
use crate::interner::Interner;
use crate::postparsing::*;
use crate::postparsing::names::*;
use crate::postparsing::ast::LocationInDenizen;
use crate::postparsing::rules::*;
use crate::higher_typing::ast::*;
use crate::typing::names::names::*;
use crate::typing::types::types::*;
use crate::typing::templata::templata::*;
use crate::typing::ast::ast::*;
use crate::typing::ast::citizens::*;
use crate::typing::ast::expressions::*;
use crate::typing::env::environment::*;
use crate::typing::env::i_env_entry::IEnvEntryT;
use crate::typing::env::function_environment_t::*;
use crate::postparsing::itemplatatype::ITemplataType;
use crate::typing::compiler_outputs::*;
use crate::typing::citizen::struct_compiler::*;
use crate::typing::compiler::Compiler;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::solver::solver::*;
use crate::typing::infer_compiler::{InferEnv, InitialKnown};
use crate::typing::names::names::IStructTemplateNameT;
use crate::typing::types::types::{IRegionT, StructTTValT, RegionT};
use crate::typing::citizen::struct_compiler::{ResolveSuccess, ResolveFailure, IResolveOutcome};
use indexmap::{IndexMap, IndexSet};
use std::collections::HashMap;
use std::marker::PhantomData;
use crate::typing::infer_compiler::InitialSend;
use crate::typing::infer_compiler::include_rule_in_call_site_solve;
use crate::typing::types::types::InterfaceTTValT;
use crate::typing::infer_compiler::include_rule_in_definition_solve;
use crate::postparsing::ast::GenericParameterS;
use crate::typing::names::names::IdValT;
use crate::typing::templata::templata::expect_mutability;
use std::collections::HashSet;

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn resolve_struct_layer(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        original_calling_env: IInDenizenEnvironmentT<'s, 't>,
        call_range: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        struct_templata: StructDefinitionTemplataT<'s, 't>,
        template_args: &[ITemplataT<'s, 't>],
    ) -> IResolveOutcome<'s, 't, StructTT<'s, 't>> {

        let declaring_env = struct_templata.declaring_env;
        let struct_a = struct_templata.origin_struct;
        let struct_template_name = self.translate_struct_name(struct_a.name);

        // We no longer assume this:
        //   vassert(templateArgs.size == structA.genericParameters.size)
        // because we have default generic arguments now.
        let initial_knowns: Vec<InitialKnown<'s, 't>> =
            struct_a.generic_parameters.iter().zip(template_args.iter()).map(|(generic_param, template_arg)| {
                InitialKnown { rune: generic_param.rune, templata: *template_arg }
            }).collect();

        let call_site_rules = self.assemble_call_site_rules(struct_a.header_rules);

        let context_region = RegionT { region: IRegionT::Default };
        let envs = InferEnv {
            original_calling_env,
            parent_ranges: call_range,
            call_location,
            self_env: declaring_env,
            context_region,
        };
        let header_rune_to_type_map: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            struct_a.header_rune_to_type.iter().map(|(k, v)| (*k, *v)).collect();

        // This checks to make sure it's a valid use of this template.
        let complete_resolve_solve = match self.solve_for_resolving(
            envs, coutputs, &call_site_rules, &header_rune_to_type_map,
            call_range, call_location, struct_a.generic_parameters, &initial_knowns, &[],
        ).unwrap_or_else(|_e| panic!("Unimplemented: ICompileErrorT from solve_for_resolving in resolveStruct")) {
            Ok(ccs) => ccs,
            Err(x) => return IResolveOutcome::ResolveFailure(ResolveFailure {
                range: call_range.to_vec(),
                x,
                _phantom: PhantomData,
            }),
        };

        // We can't just make a StructTT with the args they gave us, because they may have been
        // missing some, in which case we had to run some default rules.
        // Let's use the inferences to make one.
        let final_generic_args: Vec<ITemplataT<'s, 't>> =
            struct_a.generic_parameters.iter()
                .map(|gp| *complete_resolve_solve.conclusions.get(&gp.rune.rune).unwrap())
                .collect();
        let struct_name = struct_template_name.make_struct_name(self.typing_interner, &final_generic_args);
        let id = *declaring_env.id().add_step(self.typing_interner, struct_name);

        coutputs.add_instantiation_bounds(
            self.opts.global_options.sanity_check,
            self.typing_interner,
            original_calling_env.denizen_template_id(),
            id,
            complete_resolve_solve.rune_to_bound,
        );
        let struct_tt = *self.typing_interner.intern_struct_tt(StructTTValT { id });

        IResolveOutcome::ResolveSuccess(ResolveSuccess { kind: struct_tt, _phantom: PhantomData })
    }

    pub fn predict_interface_layer(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        original_calling_env: IInDenizenEnvironmentT<'s, 't>,
        call_range: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        interface_templata: InterfaceDefinitionTemplataT<'s, 't>,
        template_args: &[ITemplataT<'s, 't>],
    ) -> InterfaceTT<'s, 't> {
        let InterfaceDefinitionTemplataT { declaring_env, origin_interface: interface_a } = interface_templata;
        let interface_template_name = self.translate_interface_name(*interface_a.name);

        // We no longer assume this:
        //   vassert(templateArgs.size == interfaceA.genericParameters.size)
        // because we have default generic arguments now.

        let initial_knowns: Vec<InitialKnown<'s, 't>> =
            interface_a.generic_parameters.iter().zip(template_args.iter()).map(|(generic_param, template_arg)| {
                InitialKnown { rune: RuneUsage { range: *call_range.first().expect("vassertSome: callRange.headOption"), rune: generic_param.rune.rune }, templata: *template_arg }
            }).collect();

        let call_site_rules = self.assemble_predict_rules(interface_a.generic_parameters, template_args.len() as i32);
        let call_site_rule_runes: Vec<IRuneS<'s>> = call_site_rules.iter().flat_map(|r| r.rune_usages().into_iter().map(|ru| ru.rune)).collect();
        let runes_for_prediction: HashSet<IRuneS<'s>> =
            interface_a.generic_parameters.iter().map(|gp| gp.rune.rune)
            .chain(call_site_rule_runes.into_iter())
            .collect();
        let defaults_rune_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            interface_a.generic_parameters.iter()
                .filter_map(|gp| gp.default.as_ref())
                .flat_map(|d| d.rune_to_type.iter().map(|(k, v)| (*k, *v)))
                .collect();
        let rune_to_type_for_prediction: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            runes_for_prediction.iter().map(|r|
                (*r,
                 interface_a.rune_to_type.get(r).copied()
                    .unwrap_or_else(|| *defaults_rune_to_type.get(r).expect("rune not in runeToType or defaultsRuneToType")))
            ).collect();

        // This *doesnt* check to make sure it's a valid use of the template. Its purpose is really
        // just to populate any generic parameter default values.

        let context_region = RegionT { region: IRegionT::Default };

        // We're just predicting, see STCMBDP.
        let inferences =
            match self.partial_solve(
                InferEnv { original_calling_env, parent_ranges: call_range, call_location, self_env: declaring_env, context_region },
                coutputs,
                &call_site_rules,
                &rune_to_type_for_prediction,
                call_range,
                &initial_knowns,
                &[],
            ) {
                Ok(i) => i,
                Err(_e) => panic!("vimpl: TypingPassSolverError in predict_interface_layer"),
            };

        // We can't just make an InterfaceTT with the args they gave us, because they may have been
        // missing some, in which case we had to run some default rules.
        // Let's use the inferences to make one.

        let final_generic_args: Vec<ITemplataT<'s, 't>> = interface_a.generic_parameters.iter().map(|gp| {
            *inferences.get(&gp.rune.rune).expect("rune not in inferences")
        }).collect();
        let interface_name = interface_template_name.make_interface_name(self.typing_interner, &final_generic_args);
        let id = declaring_env.id().add_step(self.typing_interner, interface_name);

        // Usually when we make an InterfaceTT we put the instantiation bounds into the coutputs,
        // but we unfortunately can't here because we're just predicting an interface; we'll
        // try to resolve it later and then put the bounds in. Hopefully this InterfaceTT doesn't
        // escape into the wild.
        *self.typing_interner.intern_interface_tt(InterfaceTTValT { id: *id })
    }

    pub fn predict_struct_layer(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        original_calling_env: IInDenizenEnvironmentT<'s, 't>,
        call_range: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        struct_templata: StructDefinitionTemplataT<'s, 't>,
        template_args: &[ITemplataT<'s, 't>],
    ) -> StructTT<'s, 't> {
        let StructDefinitionTemplataT { declaring_env, origin_struct: struct_a } = struct_templata;
        let struct_template_name = self.translate_struct_name(struct_a.name);

        // We no longer assume this:
        //   vassert(templateArgs.size == structA.genericParameters.size)
        // because we have default generic arguments now.

        let initial_knowns: Vec<InitialKnown<'s, 't>> =
            struct_a.generic_parameters.iter().zip(template_args.iter()).map(|(generic_param, template_arg)| {
                InitialKnown { rune: RuneUsage { range: *call_range.first().expect("vassertSome: callRange.headOption"), rune: generic_param.rune.rune }, templata: *template_arg }
            }).collect();

        let call_site_rules = self.assemble_predict_rules(struct_a.generic_parameters, template_args.len() as i32);
        let call_site_rule_runes: Vec<IRuneS<'s>> = call_site_rules.iter().flat_map(|r| r.rune_usages().into_iter().map(|ru| ru.rune)).collect();
        let runes_for_prediction: HashSet<IRuneS<'s>> =
            struct_a.generic_parameters.iter().map(|gp| gp.rune.rune)
            .chain(call_site_rule_runes.into_iter())
            .collect();
        let defaults_rune_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            struct_a.generic_parameters.iter()
                .filter_map(|gp| gp.default.as_ref())
                .flat_map(|d| d.rune_to_type.iter().map(|(k, v)| (*k, *v)))
                .collect();
        let rune_to_type_for_prediction: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            runes_for_prediction.iter().map(|r|
                (*r,
                 struct_a.header_rune_to_type.get(r).copied()
                    .unwrap_or_else(|| *defaults_rune_to_type.get(r).expect("rune not in headerRuneToType or defaultsRuneToType")))
            ).collect();

        // This *doesnt* check to make sure it's a valid use of the template. Its purpose is really
        // just to populate any generic parameter default values.

        // Maybe we should make this incremental too, like when solving definitions?

        let context_region = RegionT { region: IRegionT::Default };

        // We're just predicting, see STCMBDP.
        let inferences =
            match self.partial_solve(
                InferEnv { original_calling_env, parent_ranges: call_range, call_location, self_env: declaring_env, context_region },
                coutputs,
                &call_site_rules,
                &rune_to_type_for_prediction,
                call_range,
                &initial_knowns,
                &[],
            ) {
                Ok(i) => i,
                Err(_e) => panic!("vimpl: TypingPassSolverError in predict_struct_layer"),
            };

        // We can't just make a StructTT with the args they gave us, because they may have been
        // missing some, in which case we had to run some default rules.
        // Let's use the inferences to make one.

        let final_generic_args: Vec<ITemplataT<'s, 't>> = struct_a.generic_parameters.iter().map(|gp| {
            *inferences.get(&gp.rune.rune).expect("rune not in inferences")
        }).collect();
        let struct_name = struct_template_name.make_struct_name(self.typing_interner, &final_generic_args);
        let id = declaring_env.id().add_step(self.typing_interner, struct_name);

        // Usually when we make a StructTT we put the instantiation bounds into the coutputs,
        // but we unfortunately can't here because we're just predicting a struct; we'll
        // try to resolve it later and then put the bounds in. Hopefully this StructTT doesn't
        // escape into the wild.
        *self.typing_interner.intern_struct_tt(StructTTValT { id: *id })
    }

    pub fn resolve_interface_layer(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        original_calling_env: IInDenizenEnvironmentT<'s, 't>,
        call_range: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        interface_templata: InterfaceDefinitionTemplataT<'s, 't>,
        template_args: &[ITemplataT<'s, 't>],
    ) -> IResolveOutcome<'s, 't, InterfaceTT<'s, 't>> {

        let declaring_env = interface_templata.declaring_env;
        let interface_a = interface_templata.origin_interface;
        let interface_template_name = self.translate_interface_name(*interface_a.name);

        // We no longer assume this:
        //   vassert(templateArgs.size == structA.genericParameters.size)
        // because we have default generic arguments now.
        let initial_knowns: Vec<InitialKnown<'s, 't>> =
            interface_a.generic_parameters.iter().zip(template_args.iter()).map(|(generic_param, template_arg)| {
                InitialKnown { rune: generic_param.rune, templata: *template_arg }
            }).collect();

        let call_site_rules = self.assemble_call_site_rules(interface_a.rules);

        let context_region = RegionT { region: IRegionT::Default };
        let envs = InferEnv {
            original_calling_env,
            parent_ranges: call_range,
            call_location,
            self_env: declaring_env,
            context_region,
        };
        let rune_to_type_map: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            interface_a.rune_to_type.iter().map(|(k, v)| (*k, *v)).collect();

        // This checks to make sure it's a valid use of this template.
        let complete_resolve_solve = match self.solve_for_resolving(
            envs, coutputs, &call_site_rules, &rune_to_type_map,
            call_range, call_location, interface_a.generic_parameters, &initial_knowns, &[],
        ).unwrap_or_else(|_e| panic!("Unimplemented: ICompileErrorT from solve_for_resolving in resolveInterface")) {
            Ok(ccs) => ccs,
            Err(x) => return IResolveOutcome::ResolveFailure(ResolveFailure {
                range: call_range.to_vec(),
                x,
                _phantom: PhantomData,
            }),
        };

        // We can't just make an InterfaceTT with the args they gave us, because they may have been
        // missing some, in which case we had to run some default rules.
        // Let's use the inferences to make one.
        let final_generic_args: Vec<ITemplataT<'s, 't>> =
            interface_a.generic_parameters.iter()
                .map(|gp| *complete_resolve_solve.conclusions.get(&gp.rune.rune).unwrap())
                .collect();
        let interface_name = interface_template_name.make_interface_name(self.typing_interner, &final_generic_args);
        let id = *declaring_env.id().add_step(self.typing_interner, interface_name);

        coutputs.add_instantiation_bounds(
            self.opts.global_options.sanity_check,
            self.typing_interner,
            original_calling_env.denizen_template_id(),
            id,
            complete_resolve_solve.rune_to_bound,
        );
        let interface_tt = *self.typing_interner.intern_interface_tt(InterfaceTTValT { id });

        IResolveOutcome::ResolveSuccess(ResolveSuccess { kind: interface_tt, _phantom: PhantomData })
    }

    pub fn compile_struct_layer(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        struct_templata: StructDefinitionTemplataT<'s, 't>,
    ) -> Result<UncheckedDefiningConclusions<'s, 't>, ICompileErrorT<'s, 't>> {
        let declaring_env = struct_templata.declaring_env;
        let struct_a = struct_templata.origin_struct;
        let struct_template_name = self.translate_struct_name(struct_a.name);
        let local_name = match struct_template_name {
            IStructTemplateNameT::StructTemplate(r) => INameT::StructTemplate(r),
            IStructTemplateNameT::AnonymousSubstructTemplate(r) => INameT::AnonymousSubstructTemplate(r),
            IStructTemplateNameT::LambdaCitizenTemplate(r) => INameT::LambdaCitizenTemplate(r),
        };
        let struct_template_id = declaring_env.id().add_step(self.typing_interner, local_name);
        // We declare the struct's outer environment in the precompile stage instead of here because of MDATOEF.
        let outer_env = coutputs.get_outer_env_for_type(parent_ranges, *struct_template_id);
        let all_rules_s: Vec<IRulexSR<'s>> =
            struct_a.header_rules.iter().copied().chain(struct_a.member_rules.iter().copied()).collect();
        let all_rune_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            struct_a.header_rune_to_type.iter().chain(struct_a.members_rune_to_type.iter())
                .map(|(k, v)| (*k, *v)).collect();
        let definition_rules: Vec<IRulexSR<'s>> =
            all_rules_s.iter().copied().filter(|r| include_rule_in_definition_solve(r)).collect();
        let mut all_ranges: Vec<RangeS<'s>> = vec![struct_a.range];
        all_ranges.extend_from_slice(parent_ranges);
        let outer_env_ienv = IEnvironmentT::from(outer_env);
        let envs = InferEnv {
            original_calling_env: outer_env,
            parent_ranges: self.typing_interner.alloc_slice_from_vec(vec![struct_a.range]),
            call_location,
            self_env: outer_env_ienv,
            context_region: RegionT { region: IRegionT::Default },
        };
        let mut solver = self.make_solver_state(envs, coutputs, &definition_rules, &all_rune_to_type, &all_ranges, &[], &[]);
        let get_first_unsolved = |generic_parameters: &'s [&'s GenericParameterS<'s>], is_solved: &dyn Fn(IRuneS<'s>) -> bool| {
            self.get_first_unsolved_identifying_rune(generic_parameters, |rune| is_solved(rune))
        };
        match self.incrementally_solve(envs, coutputs, &mut solver, |coutputs, solver_state| {
            match get_first_unsolved(
                struct_a.generic_parameters,
                &|rune| solver_state.get_conclusion(&rune).is_some(),
            ) {
                None => false,
                Some((generic_param, index)) => {
                    let placeholder_pure_height = None;
                    let templata = self.create_placeholder(
                        coutputs, outer_env, *struct_template_id,
                        generic_param, index, &all_rune_to_type, placeholder_pure_height, true);
                    solver_state.commit_step::<()>(
                        false, vec![], {
                            let mut m = IndexMap::new();
                            m.insert(generic_param.rune.rune, templata);
                            m
                        }, vec![], IndexSet::new()).unwrap();
                    true
                }
            }
        }) {
            Err(_f) => panic!("Unimplemented: TypingPassSolverError in compile_struct_layer"),
            Ok(_) => {}
        }
        let inferences = match self.interpret_results(&all_rune_to_type, &mut solver) {
            Err(_e) => panic!("Unimplemented: TypingPassSolverError in compile_struct_layer interpretResults"),
            Ok(conclusions) => conclusions,
        };
        let unchecked_defining_conclusions = UncheckedDefiningConclusions {
            envs,
            ranges: all_ranges,
            call_location,
            definition_rules: definition_rules.clone(),
            conclusions: inferences.clone(),
        };
        match struct_a.maybe_predicted_mutability {
            None => {
                let mutability = expect_mutability(inferences[&struct_a.mutability_rune.rune]);
                coutputs.declare_type_mutability(struct_template_id, mutability);
            }
            Some(_) => {}
        }
        let template_args: Vec<ITemplataT<'s, 't>> =
            struct_a.generic_parameters.iter().map(|p| inferences[&p.rune.rune]).collect();
        let id = self.assemble_struct_name(*struct_template_id, &template_args);
        let id_steps = id.steps();
        let inner_env_id = self.typing_interner.intern_id(IdValT {
            package_coord: id.package_coord,
            init_steps: &id_steps,
            local_name: id.local_name,
        });
        let inner_env_entries: Vec<(INameT<'s, 't>, IEnvEntryT<'s, 't>)> =
            inferences.iter().map(|(rune, templata)| {
                let rune_name = self.typing_interner.intern_rune_name(RuneNameT { rune: *rune});
                (INameT::Rune(rune_name), IEnvEntryT::Templata(*templata))
            }).collect();
        let mut inner_store = TemplatasStoreBuilder::new(inner_env_id);
        inner_store.add_entries(self.scout_arena, inner_env_entries);
        let inner_templatas = inner_store.build_in(self.typing_interner);
        let inner_env = self.typing_interner.alloc(CitizenEnvironmentT {
            global_env: outer_env.global_env(),
            parent_env: IEnvironmentT::from(outer_env),
            template_id: *struct_template_id,
            id,
            templatas: inner_templatas,
        });
        let inner_env_ref = IInDenizenEnvironmentT::Citizen(inner_env);
        coutputs.declare_type_inner_env(struct_template_id, inner_env_ref);
        self.compile_struct_core(outer_env, inner_env, coutputs, parent_ranges, call_location, struct_a)?;
        Ok(unchecked_defining_conclusions)
    }

    pub fn compile_interface_layer(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        interface_templata: InterfaceDefinitionTemplataT<'s, 't>,
    ) -> Result<UncheckedDefiningConclusions<'s, 't>, ICompileErrorT<'s, 't>> {
        let declaring_env = interface_templata.declaring_env;
        let interface_a = interface_templata.origin_interface;
        let interface_template_name = self.translate_interface_name(*interface_a.name);
        let local_name = match interface_template_name {
            IInterfaceTemplateNameT::InterfaceTemplate(r) => INameT::InterfaceTemplate(r),
        };
        let interface_template_id = declaring_env.id().add_step(self.typing_interner, local_name);
        // We declare the interface's outer environment in the precompile stage instead of here because of MDATOEF.
        let outer_env = coutputs.get_outer_env_for_type(parent_ranges, *interface_template_id);
        let rune_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            interface_a.rune_to_type.iter().map(|(k, v)| (*k, *v)).collect();
        let definition_rules: Vec<IRulexSR<'s>> =
            interface_a.rules.iter().copied().filter(|r| include_rule_in_definition_solve(r)).collect();
        let mut all_ranges: Vec<RangeS<'s>> = vec![interface_a.range];
        all_ranges.extend_from_slice(parent_ranges);
        let outer_env_ienv = IEnvironmentT::from(outer_env);
        let envs = InferEnv {
            original_calling_env: outer_env,
            parent_ranges: self.typing_interner.alloc_slice_from_vec(vec![interface_a.range]),
            call_location,
            self_env: outer_env_ienv,
            context_region: RegionT { region: IRegionT::Default },
        };
        let mut solver = self.make_solver_state(envs, coutputs, &definition_rules, &rune_to_type, &all_ranges, &[], &[]);
        let get_first_unsolved = |generic_parameters: &'s [&'s GenericParameterS<'s>], is_solved: &dyn Fn(IRuneS<'s>) -> bool| {
            self.get_first_unsolved_identifying_rune(generic_parameters, |rune| is_solved(rune))
        };
        match self.incrementally_solve(envs, coutputs, &mut solver, |coutputs, solver_state| {
            match get_first_unsolved(
                interface_a.generic_parameters,
                &|rune| solver_state.get_conclusion(&rune).is_some(),
            ) {
                None => false,
                Some((generic_param, index)) => {
                    let placeholder_pure_height = None;
                    let templata = self.create_placeholder(
                        coutputs, outer_env, *interface_template_id,
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
        }) {
            Err(_f) => panic!("Unimplemented: TypingPassSolverError in compile_interface_layer"),
            Ok(_) => {}
        }
        let inferences = match self.interpret_results(&rune_to_type, &mut solver) {
            Err(_e) => panic!("Unimplemented: TypingPassSolverError in compile_interface_layer interpretResults"),
            Ok(conclusions) => conclusions,
        };
        let unchecked_defining_conclusions = UncheckedDefiningConclusions {
            envs,
            ranges: all_ranges,
            call_location,
            definition_rules: definition_rules.clone(),
            conclusions: inferences.clone(),
        };
        match interface_a.maybe_predicted_mutability {
            None => {
                let mutability = expect_mutability(inferences[&interface_a.mutability_rune.rune]);
                coutputs.declare_type_mutability(interface_template_id, mutability);
            }
            Some(_) => {}
        }
        let template_args: Vec<ITemplataT<'s, 't>> =
            interface_a.generic_parameters.iter().map(|p| inferences[&p.rune.rune]).collect();
        let id = self.assemble_interface_name(*interface_template_id, &template_args);
        let id_steps = id.steps();
        let inner_env_id = self.typing_interner.intern_id(IdValT {
            package_coord: id.package_coord,
            init_steps: &id_steps,
            local_name: id.local_name,
        });
        let inner_env_entries: Vec<(INameT<'s, 't>, IEnvEntryT<'s, 't>)> =
            inferences.iter().map(|(rune, templata)| {
                let rune_name = self.typing_interner.intern_rune_name(RuneNameT { rune: *rune});
                (INameT::Rune(rune_name), IEnvEntryT::Templata(*templata))
            }).collect();
        let mut inner_store = TemplatasStoreBuilder::new(inner_env_id);
        inner_store.add_entries(self.scout_arena, inner_env_entries);
        let inner_templatas = inner_store.build_in(self.typing_interner);
        let inner_env = self.typing_interner.alloc(CitizenEnvironmentT {
            global_env: outer_env.global_env(),
            parent_env: IEnvironmentT::from(outer_env),
            template_id: *interface_template_id,
            id,
            templatas: inner_templatas,
        });
        let inner_env_ref = IInDenizenEnvironmentT::Citizen(inner_env);
        coutputs.declare_type_inner_env(interface_template_id, inner_env_ref);
        self.compile_interface_core(outer_env, inner_env, coutputs, parent_ranges, call_location, interface_a)?;
        Ok(unchecked_defining_conclusions)
    }

    pub fn make_closure_understruct_layer(
        &self,
        containing_function_env: &'t NodeEnvironmentT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        name: IFunctionDeclarationNameS<'s>,
        function_s: &'s FunctionA<'s>,
        members: &[&'t NormalStructMemberT<'s, 't>],
    ) -> Result<(StructTT<'s, 't>, MutabilityT, FunctionTemplataT<'s, 't>), ICompileErrorT<'s, 't>> {
        self.make_closure_understruct_core(
            containing_function_env, coutputs, parent_ranges, call_location, name, function_s, members)
    }

    pub fn assemble_struct_name(
        &self,
        template_name: IdT<'s, 't>,
        template_args: &[ITemplataT<'s, 't>],
    ) -> IdT<'s, 't> {
        let struct_template_name = match template_name.local_name {
            INameT::StructTemplate(r) => IStructTemplateNameT::StructTemplate(r),
            INameT::AnonymousSubstructTemplate(r) => IStructTemplateNameT::AnonymousSubstructTemplate(r),
            INameT::LambdaCitizenTemplate(r) => IStructTemplateNameT::LambdaCitizenTemplate(r),
            _ => panic!("Unimplemented: assemble_struct_name non-struct local_name"),
        };
        let new_local_name = struct_template_name.make_struct_name(self.typing_interner, template_args);
        let steps = template_name.steps();
        *self.typing_interner.intern_id(IdValT {
            package_coord: template_name.package_coord,
            init_steps: &steps,
            local_name: new_local_name,
        })
    }

    pub fn assemble_interface_name(
        &self,
        template_name: IdT<'s, 't>,
        template_args: &[ITemplataT<'s, 't>],
    ) -> IdT<'s, 't> {
        let interface_template_name = match template_name.local_name {
            INameT::InterfaceTemplate(r) => IInterfaceTemplateNameT::InterfaceTemplate(r),
            _ => panic!("Unimplemented: assemble_interface_name non-interface local_name"),
        };
        let new_local_name = interface_template_name.make_interface_name(self.typing_interner, template_args);
        let steps = template_name.steps();
        *self.typing_interner.intern_id(IdValT {
            package_coord: template_name.package_coord,
            init_steps: &steps,
            local_name: new_local_name,
        })
    }

}