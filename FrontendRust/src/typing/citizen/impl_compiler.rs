use indexmap::IndexMap;
use std::collections::HashMap;
use crate::typing::compiler::Compiler;
use crate::typing::infer_compiler::*;
use crate::solver::solver::*;
use crate::typing::infer::compiler_solver::ITypingPassSolverError;
use crate::utils::range::RangeS;
use crate::postparsing::names::*;
use crate::postparsing::*;
use crate::postparsing::ast::LocationInDenizen;
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
use crate::higher_typing::ast::*;
use crate::interner::Interner;
use crate::typing::infer_compiler::include_rule_in_call_site_solve;
use crate::postparsing::itemplatatype::ITemplataType;
use crate::typing::env::environment::TemplatasStoreBuilder;
use crate::typing::env::environment::child_of;
use crate::typing::hinputs_t::{InstantiationBoundArgumentsT, InstantiationReachableBoundArgumentsT};
use crate::utils::arena_index_map::ArenaIndexMap;
use std::marker::PhantomData;
use crate::typing::infer_compiler::CompleteResolveSolve;
use crate::typing::types::types::{KindT, InterfaceTT};
use crate::typing::templata::templata::{ITemplataT, KindTemplataT};
use crate::postparsing::names::{IImpreciseNameValS, ImplSubCitizenImpreciseNameValS};
use crate::typing::env::environment::{get_imprecise_name, ILookupContext};
use crate::typing::templata::templata::{ImplDefinitionTemplataT, IsaTemplataT};
use crate::typing::types::types::ICitizenTT;
use crate::postparsing::names::ImplImpreciseNameValS;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::typing::ast::citizens::CitizenDefinitionT;
use crate::typing::hinputs_t::make;
use std::collections::HashSet;

pub enum IsParentResult<'s, 't> {
    IsParent(IsParent<'s, 't>),
    IsntParent(IsntParent<'s, 't>),
}

pub struct IsParent<'s, 't> {
    pub templata: ITemplataT<'s, 't>,
    pub conclusions: IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>,
    pub impl_id: IdT<'s, 't>,
}

#[derive(Debug)]
pub struct IsntParent<'s, 't> {
    pub candidates: Vec<IResolvingError<'s, 't>>,
}

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn resolve_impl(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        initial_knowns: &[InitialKnown<'s, 't>],
        impl_templata: ImplDefinitionTemplataT<'s, 't>,
    ) -> Result<CompleteResolveSolve<'s, 't>, IResolvingError<'s, 't>> {

        let parent_env = impl_templata.env;
        let impl_a = impl_templata.impl_;

        let impl_template_name: IImplTemplateNameT<'s, 't> = self.translate_impl_name(impl_a.name);
        let impl_template_name_local: INameT<'s, 't> = match impl_template_name {
            IImplTemplateNameT::ImplTemplate(r) => INameT::ImplTemplate(r),
            IImplTemplateNameT::ImplBoundTemplate(_) => panic!("Unimplemented: ImplBoundTemplate in resolve_impl"),
            IImplTemplateNameT::AnonymousSubstructImplTemplate(r) => INameT::AnonymousSubstructImplTemplate(r),
        };
        let impl_template_id: &'t IdT<'s, 't> = parent_env.id().add_step(self.typing_interner, impl_template_name_local);

        let outer_env_store = {
            let store = TemplatasStoreBuilder::new(impl_template_id);
            store.build_in(self.typing_interner)
        };
        let outer_env: &'t CitizenEnvironmentT<'s, 't> = self.typing_interner.alloc(CitizenEnvironmentT {
            global_env: parent_env.global_env(),
            parent_env: IEnvironmentT::from(parent_env),
            template_id: *impl_template_id,
            id: *impl_template_id,
            templatas: outer_env_store,
        });

        let call_site_rules: Vec<IRulexSR<'s>> =
            impl_a.rules.iter().copied().filter(|r| include_rule_in_call_site_solve(r)).collect();

        let rune_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            impl_a.rune_to_type.iter().map(|(k, v)| (*k, *v)).collect();

        let mut all_ranges: Vec<RangeS<'s>> = vec![impl_a.range];
        all_ranges.extend_from_slice(parent_ranges);
        let all_ranges_slice = self.typing_interner.alloc_slice_copy(&all_ranges);

        let original_calling_env = calling_env;
        let envs = InferEnv {
            original_calling_env,
            parent_ranges: all_ranges_slice,
            call_location,
            self_env: IEnvironmentT::from(IInDenizenEnvironmentT::Citizen(outer_env)),
            context_region: RegionT { region: IRegionT::Default },
        };
        let mut solver_state = self.make_solver_state(
            envs, coutputs, &call_site_rules, &rune_to_type, all_ranges_slice, initial_knowns, &[]);
        match self.r#continue(envs, coutputs, &mut solver_state) {
            Ok(()) => {}
            Err(e) => return Err(IResolvingError::ResolvingSolveFailedOrIncomplete(e)),
        }
        self.check_resolving_conclusions_and_resolve(
            envs,
            coutputs,
            all_ranges_slice,
            call_location,
            &rune_to_type,
            &call_site_rules,
            &[impl_a.sub_citizen_rune.rune],
            &mut solver_state,
        ).unwrap_or_else(|_e| panic!("Unimplemented: ICompileErrorT from check_resolving_conclusions_and_resolve in resolve_impl"))
    }

    pub fn partial_resolve_impl(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        initial_knowns: &[InitialKnown<'s, 't>],
        impl_templata: ImplDefinitionTemplataT<'s, 't>,
    ) -> Result<IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>, FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>> {

        let parent_env = impl_templata.env;
        let impl_a = impl_templata.impl_;

        let impl_template_name: IImplTemplateNameT<'s, 't> = self.translate_impl_name(impl_a.name);
        let impl_template_name_local: INameT<'s, 't> = match impl_template_name {
            IImplTemplateNameT::ImplTemplate(r) => INameT::ImplTemplate(r),
            IImplTemplateNameT::ImplBoundTemplate(_) => panic!("Unimplemented: ImplBoundTemplate in partial_resolve_impl"),
            IImplTemplateNameT::AnonymousSubstructImplTemplate(r) => INameT::AnonymousSubstructImplTemplate(r),
        };
        let impl_template_id: &'t IdT<'s, 't> = parent_env.id().add_step(self.typing_interner, impl_template_name_local);

        let outer_env_store = {
            let store = TemplatasStoreBuilder::new(impl_template_id);
            store.build_in(self.typing_interner)
        };
        let outer_env: &'t CitizenEnvironmentT<'s, 't> = self.typing_interner.alloc(CitizenEnvironmentT {
            global_env: parent_env.global_env(),
            parent_env: IEnvironmentT::from(parent_env),
            template_id: *impl_template_id,
            id: *impl_template_id,
            templatas: outer_env_store,
        });

        let call_site_rules: Vec<IRulexSR<'s>> =
            impl_a.rules.iter().copied().filter(|r| include_rule_in_call_site_solve(r)).collect();

        let rune_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            impl_a.rune_to_type.iter().map(|(k, v)| (*k, *v)).collect();

        let mut all_ranges: Vec<RangeS<'s>> = vec![impl_a.range];
        all_ranges.extend_from_slice(parent_ranges);
        let all_ranges_slice = self.typing_interner.alloc_slice_from_vec(all_ranges);

        let original_calling_env = calling_env;
        let envs = InferEnv {
            original_calling_env,
            parent_ranges: all_ranges_slice,
            call_location,
            self_env: IEnvironmentT::from(IInDenizenEnvironmentT::Citizen(outer_env)),
            context_region: RegionT { region: IRegionT::Default },
        };
        let mut solver_state = self.make_solver_state(
            envs, coutputs, &call_site_rules, &rune_to_type, all_ranges_slice, initial_knowns, &[]);
        match self.r#continue(envs, coutputs, &mut solver_state) {
            Ok(()) => {}
            Err(e) => return Err(e),
        }
        Ok(solver_state.userify_conclusions().into_iter().collect())
    }

    pub fn compile_impl(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        call_location: LocationInDenizen<'s>,
        impl_templata: ImplDefinitionTemplataT<'s, 't>,
    ) -> Result<(), ICompileErrorT<'s, 't>> {

        let parent_env = impl_templata.env;
        let impl_a = impl_templata.impl_;

        let impl_template_name: IImplTemplateNameT<'s, 't> = self.translate_impl_name(impl_a.name);
        let impl_template_name_local: INameT<'s, 't> = match impl_template_name {
            IImplTemplateNameT::ImplTemplate(r) => INameT::ImplTemplate(r),
            IImplTemplateNameT::ImplBoundTemplate(_) => panic!("Unimplemented: ImplBoundTemplate in compile_impl"),
            IImplTemplateNameT::AnonymousSubstructImplTemplate(r) => INameT::AnonymousSubstructImplTemplate(r),
        };
        let impl_template_id: &'t IdT<'s, 't> = parent_env.id().add_step(self.typing_interner, impl_template_name_local);

        let impl_outer_env_store_ref = {
            let store = TemplatasStoreBuilder::new(impl_template_id);
            store.build_in(self.typing_interner)
        };
        let impl_outer_env: &'t CitizenEnvironmentT<'s, 't> = self.typing_interner.alloc(CitizenEnvironmentT {
            global_env: parent_env.global_env(),
            parent_env: IEnvironmentT::from(parent_env),
            template_id: *impl_template_id,
            id: *impl_template_id,
            templatas: impl_outer_env_store_ref,
        });
        let impl_outer_env_iden: IInDenizenEnvironmentT<'s, 't> =
            IInDenizenEnvironmentT::Citizen(impl_outer_env);

        let rune_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            impl_a.rune_to_type.iter().map(|(k, v)| (*k, *v)).collect();

        let impl_placeholders: Vec<InitialKnown<'s, 't>> =
            impl_a.generic_params.iter().enumerate().map(|(index, generic_param)| {
                let placeholder = self.create_placeholder(
                    coutputs, impl_outer_env_iden, *impl_template_id, generic_param, index as i32, &rune_to_type,
                    None, true);
                InitialKnown { rune: generic_param.rune, templata: placeholder }
            }).collect();

        let definition_rules: Vec<IRulexSR<'s>> =
            impl_a.rules.iter().copied().filter(|r| include_rule_in_definition_solve(r)).collect();

        let envs = InferEnv {
            original_calling_env: impl_outer_env_iden,
            parent_ranges: self.typing_interner.alloc_slice_from_vec(vec![impl_a.range]),
            call_location,
            self_env: IEnvironmentT::from(impl_outer_env_iden),
            context_region: RegionT { region: IRegionT::Default },
        };

        let complete_define_solve = match self.solve_for_defining(
            envs,
            coutputs,
            &definition_rules,
            &rune_to_type,
            &[impl_a.range],
            call_location,
            &impl_placeholders,
            &[],
            &[impl_a.sub_citizen_rune.rune],
        ) {
            Ok(c) => c,
            Err(_e) => {
                panic!("TypingPassDefiningError from compile_impl");
            }
        };

        let inferences = complete_define_solve.conclusions;
        let reachable_bounds_from_sub_citizen = &complete_define_solve.rune_to_bound.rune_to_citizen_rune_to_reachable_prototype;

        let sub_citizen: ICitizenTT<'s, 't> = match inferences.get(&impl_a.sub_citizen_rune.rune) {
            None => panic!("vwat: sub_citizen_rune not in inferences"),
            Some(ITemplataT::Kind(k)) => match k.kind {
                KindT::Struct(s) => ICitizenTT::Struct(s),
                KindT::Interface(i) => ICitizenTT::Interface(i),
                _ => panic!("vwat: sub citizen kind is not a citizen"),
            },
            Some(_) => panic!("vwat: expected KindTemplataT for sub_citizen"),
        };
        let sub_citizen_id = match sub_citizen {
            ICitizenTT::Struct(s) => s.id,
            ICitizenTT::Interface(i) => i.id,
        };
        let sub_citizen_template_id = self.get_citizen_template(sub_citizen_id);

        let super_interface: &'t InterfaceTT<'s, 't> = match inferences.get(&impl_a.interface_kind_rune.rune) {
            None => panic!("vwat: interface_kind_rune not in inferences"),
            Some(ITemplataT::Kind(k)) => match k.kind {
                KindT::Interface(i) => i,
                _ => return Err(ICompileErrorT::CantImplNonInterface {
                    range: self.typing_interner.alloc_slice_copy(&[impl_a.range]),
                    templata: ITemplataT::Kind(*k),
                }),
            },
            Some(other) => return Err(ICompileErrorT::CantImplNonInterface {
                range: self.typing_interner.alloc_slice_copy(&[impl_a.range]),
                templata: *other,
            }),
        };
        let super_interface_template_id = self.get_interface_template(super_interface.id);

        let sub_citizen_weakable = match coutputs.lookup_citizen_by_tt(sub_citizen, self) {
            CitizenDefinitionT::Struct(s) => s.weakable,
            CitizenDefinitionT::Interface(i) => i.weakable,
        };
        let super_interface_weakable = coutputs.lookup_interface(*super_interface, self).weakable;
        if sub_citizen_weakable != super_interface_weakable {
            return Err(ICompileErrorT::WeakableImplingMismatch {
                range: self.typing_interner.alloc_slice_copy(&[impl_a.range]),
                struct_weakable: sub_citizen_weakable,
                interface_weakable: super_interface_weakable,
            });
        }

        let template_args: Vec<ITemplataT<'s, 't>> =
            impl_a.generic_params.iter().map(|p| *inferences.get(&p.rune.rune).expect("rune in inferences")).collect();
        let instantiated_id: IdT<'s, 't> = self.assemble_impl_name(*impl_template_id, &template_args, sub_citizen);
        let instantiated_id_ref: &'t IdT<'s, 't> = self.typing_interner.alloc(instantiated_id);

        let mut inner_env_entries: Vec<(INameT<'s, 't>, IEnvEntryT<'s, 't>)> =
            reachable_bounds_from_sub_citizen.iter()
                .flat_map(|(_, rb)| rb.citizen_rune_to_reachable_prototype.iter().map(|(_, proto)| proto))
                .enumerate()
                .map(|(index, proto)| -> (INameT<'s, 't>, IEnvEntryT<'s, 't>) {
                    let name = self.typing_interner.intern_reachable_prototype_name(
                        ReachablePrototypeNameT { num: index as i32});
                    let entry = IEnvEntryT::Templata(ITemplataT::Prototype(
                        self.typing_interner.alloc(PrototypeTemplataT { prototype: proto })));
                    (INameT::ReachablePrototype(name), entry)
                })
                .collect();
        inner_env_entries.extend(inferences.iter().map(|(name_s, templata)| {
            let rune_name = self.typing_interner.intern_rune_name(
                RuneNameT { rune: *name_s});
            (INameT::Rune(rune_name), IEnvEntryT::Templata(*templata))
        }));

        let impl_inner_env: &'t GeneralEnvironmentT<'s, 't> = child_of(
            self.typing_interner,
            self.scout_arena,
            IInDenizenEnvironmentT::Citizen(impl_outer_env),
            *impl_template_id,
            instantiated_id_ref,
            inner_env_entries,
        );
        let impl_inner_env_iden: IInDenizenEnvironmentT<'s, 't> =
            IInDenizenEnvironmentT::General(impl_inner_env);

        let rune_to_needed_function_bound = self.assemble_rune_to_function_bound(impl_inner_env.templatas);
        let rune_to_needed_impl_bound = self.assemble_rune_to_impl_bound(impl_inner_env.templatas);

        let rune_index_to_independence =
            self.calculate_runes_independence(coutputs, call_location, impl_templata, impl_outer_env_iden, *super_interface);

        let mut rune_to_reachable: ArenaIndexMap<'t, IRuneS<'s>, &'t InstantiationReachableBoundArgumentsT<'s, 't>> =
            self.typing_interner.alloc_index_map();
        for (k, v) in reachable_bounds_from_sub_citizen.iter() {
            rune_to_reachable.insert(*k, *v);
        }

        let instantiation_bound_params = self.typing_interner.alloc(InstantiationBoundArgumentsT {
            rune_to_bound_prototype: self.typing_interner.alloc_index_map_from_iter(
                rune_to_needed_function_bound.into_iter().map(|(k, v)| (k, *v))),
            rune_to_citizen_rune_to_reachable_prototype: rune_to_reachable,
            rune_to_bound_impl: self.typing_interner.alloc_index_map_from_iter(
                rune_to_needed_impl_bound.into_iter()),
        });

        let impl_t = ImplT {
            templata: impl_templata,
            instantiated_id,
            template_id: *impl_template_id,
            sub_citizen_template_id,
            sub_citizen,
            super_interface: *super_interface,
            super_interface_template_id,
            instantiation_bound_params,
            rune_index_to_independence: self.typing_interner.alloc_slice_from_vec(rune_index_to_independence),
        };

        coutputs.declare_type(impl_template_id);
        coutputs.declare_type_outer_env(impl_template_id, impl_outer_env_iden);
        coutputs.declare_type_inner_env(impl_template_id, impl_inner_env_iden);
        coutputs.add_impl(self.typing_interner.alloc(impl_t));
        Ok(())
    }

    pub fn calculate_runes_independence(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        call_location: LocationInDenizen<'s>,
        impl_templata: ImplDefinitionTemplataT<'s, 't>,
        impl_outer_env: IInDenizenEnvironmentT<'s, 't>,
        interface: InterfaceTT<'s, 't>,
    ) -> Vec<bool> {
        let initial_knowns = vec![InitialKnown {
            rune: impl_templata.impl_.interface_kind_rune,
            templata: ITemplataT::Kind(self.typing_interner.alloc(KindTemplataT { kind: KindT::Interface(self.typing_interner.alloc(interface)) })),
        }];
        let partial_case_conclusions = match self.partial_resolve_impl(
            coutputs,
            &[impl_templata.impl_.range],
            call_location,
            impl_outer_env,
            &initial_knowns,
            impl_templata,
        ) {
            Ok(c) => c,
            Err(_e) => panic!("CouldntEvaluatImpl from calculate_runes_independence"),
        };
        impl_templata.impl_.generic_params.iter()
            .map(|p| !partial_case_conclusions.contains_key(&p.rune.rune))
            .collect()
    }

    pub fn assemble_impl_name(
        &self,
        template_name: IdT<'s, 't>,
        template_args: &[ITemplataT<'s, 't>],
        sub_citizen: ICitizenTT<'s, 't>,
    ) -> IdT<'s, 't> {
        let impl_template_name: IImplTemplateNameT<'s, 't> = match template_name.local_name {
            INameT::ImplTemplate(r) => IImplTemplateNameT::ImplTemplate(r),
            INameT::ImplBoundTemplate(r) => IImplTemplateNameT::ImplBoundTemplate(r),
            INameT::AnonymousSubstructImplTemplate(r) => IImplTemplateNameT::AnonymousSubstructImplTemplate(r),
            other => panic!("assemble_impl_name: expected impl template name, got {:?}", other),
        };
        let new_local_name = impl_template_name.make_impl_name(self.typing_interner, template_args, sub_citizen);
        *self.typing_interner.intern_id(IdValT {
            package_coord: template_name.package_coord,
            init_steps: template_name.init_steps,
            local_name: new_local_name,
        })
    }

    pub fn is_descendant(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        kind: ISubKindTT<'s, 't>,
    ) -> bool {
        self.get_parents(coutputs, parent_ranges, call_location, calling_env, kind).is_empty() == false
    }

    pub fn get_impl_parent_given_sub_citizen(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        impl_templata: ImplDefinitionTemplataT<'s, 't>,
        child: ICitizenTT<'s, 't>,
    ) -> Result<InterfaceTT<'s, 't>, IResolvingError<'s, 't>> {

        let initial_knowns = vec![
            InitialKnown {
                rune: impl_templata.impl_.sub_citizen_rune,
                templata: ITemplataT::Kind(self.typing_interner.alloc(KindTemplataT { kind: KindT::from(child) })),
            }
        ];
        let _child_env = coutputs.get_outer_env_for_type(parent_ranges, self.get_citizen_template(child.id()));
        let conclusions = match self.resolve_impl(coutputs, parent_ranges, call_location, calling_env, &initial_knowns, impl_templata) {
            Ok(CompleteResolveSolve { conclusions, .. }) => conclusions,
            Err(x) => return Err(x),
        };
        let parent_tt = conclusions.get(&impl_templata.impl_.interface_kind_rune.rune)
            .unwrap_or_else(|| panic!("vassertSome: interfaceKindRune not in conclusions"));
        match *parent_tt {
            ITemplataT::Kind(kt) => match kt.kind {
                KindT::Interface(i) => Ok(*i),
                _ => panic!("vwat: expected InterfaceTT from interfaceKindRune conclusions"),
            },
            _ => panic!("vwat: expected KindTemplataT from interfaceKindRune conclusions"),
        }
    }

    pub fn get_parents(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        sub_kind: ISubKindTT<'s, 't>,
    ) -> Vec<ISuperKindTT<'s, 't>> {
        let sub_kind_id = sub_kind.id();
        let sub_kind_template_name = self.get_sub_kind_template(sub_kind_id);
        let sub_kind_env = coutputs.get_outer_env_for_type(parent_ranges, sub_kind_template_name);
        let sub_kind_imprecise_name = match get_imprecise_name(self.scout_arena, sub_kind_id.local_name) {
            None => return vec![],
            Some(n) => n,
        };
        let impl_imprecise_name = self.scout_arena.intern_imprecise_name(
            IImpreciseNameValS::ImplSubCitizenImpreciseName(ImplSubCitizenImpreciseNameValS { sub_citizen_imprecise_name: sub_kind_imprecise_name }));
        let lookup_filter = [ILookupContext::TemplataLookupContext].into_iter().collect::<HashSet<_>>();
        let mut matching: Vec<ITemplataT<'s, 't>> = Vec::new();
        matching.extend(sub_kind_env.lookup_all_with_imprecise_name(impl_imprecise_name, lookup_filter.clone(), self.typing_interner));
        matching.extend(calling_env.lookup_all_with_imprecise_name(impl_imprecise_name, lookup_filter, self.typing_interner));
        let mut impl_defs_with_duplicates: Vec<ImplDefinitionTemplataT<'s, 't>> = Vec::new();
        let mut impl_templatas_with_duplicates: Vec<IsaTemplataT<'s, 't>> = Vec::new();
        for m in matching {
            match m {
                ITemplataT::ImplDefinition(it) => impl_defs_with_duplicates.push(*it),
                ITemplataT::Isa(it) => impl_templatas_with_duplicates.push(*it),
                _ => panic!("vwat: unexpected templata in getParents matching"),
            }
        }
        let mut seen_ranges: HashSet<RangeS<'s>> = HashSet::new();
        let impl_defs: Vec<ImplDefinitionTemplataT<'s, 't>> = impl_defs_with_duplicates.into_iter()
            .filter(|d| seen_ranges.insert(d.impl_.range))
            .collect();
        let parents_from_impl_defs: Vec<ISuperKindTT<'s, 't>> = impl_defs.iter().flat_map(|impl_def| {
            match ICitizenTT::try_from(sub_kind) {
                Ok(sub_citizen) => {
                    match self.get_impl_parent_given_sub_citizen(coutputs, parent_ranges, call_location, calling_env, *impl_def, sub_citizen) {
                        Ok(x) => vec![ISuperKindTT::from(&*self.typing_interner.alloc(x))],
                        Err(_) => {
                            // Throwing away error! TODO: Use an index or something instead.
                            vec![]
                        }
                    }
                }
                Err(_) => vec![],
            }
        }).collect();
        let kind_as_kind_t = KindT::from(sub_kind);
        let mut seen_super: HashSet<ISuperKindTT<'s, 't>> = HashSet::new();
        let parents_from_impl_templatas: Vec<ISuperKindTT<'s, 't>> =
            impl_templatas_with_duplicates.iter()
                .filter(|it| it.sub_kind == kind_as_kind_t)
                .filter_map(|it| ISuperKindTT::try_from(it.super_kind).ok())
                .filter(|sk| seen_super.insert(*sk))
                .collect();
        let mut result = parents_from_impl_defs;
        result.extend(parents_from_impl_templatas);
        result
    }

    pub fn is_parent(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        sub_kind_tt: ISubKindTT<'s, 't>,
        super_kind_tt: ISuperKindTT<'s, 't>,
    ) -> IsParentResult<'s, 't> {

        let super_kind_imprecise_name = match get_imprecise_name(self.scout_arena, super_kind_tt.id().local_name) {
            None => return IsParentResult::IsntParent(IsntParent { candidates: vec![] }),
            Some(n) => n,
        };
        let sub_kind_imprecise_name = match get_imprecise_name(self.scout_arena, sub_kind_tt.id().local_name) {
            None => return IsParentResult::IsntParent(IsntParent { candidates: vec![] }),
            Some(n) => n,
        };
        let impl_imprecise_name = self.scout_arena.intern_imprecise_name(
            IImpreciseNameValS::ImplImpreciseName(ImplImpreciseNameValS { sub_citizen_imprecise_name: sub_kind_imprecise_name, super_interface_imprecise_name: super_kind_imprecise_name }));

        let sub_kind_env = coutputs.get_outer_env_for_type(parent_ranges, self.get_sub_kind_template(sub_kind_tt.id()));
        let super_kind_env = coutputs.get_outer_env_for_type(parent_ranges, self.get_super_kind_template(super_kind_tt.id()));

        let lookup_filter = [ILookupContext::TemplataLookupContext].into_iter().collect::<HashSet<_>>();
        let mut matching: Vec<ITemplataT<'s, 't>> = Vec::new();
        matching.extend(calling_env.lookup_all_with_imprecise_name(impl_imprecise_name, lookup_filter.clone(), self.typing_interner));
        matching.extend(sub_kind_env.lookup_all_with_imprecise_name(impl_imprecise_name, lookup_filter.clone(), self.typing_interner));
        matching.extend(super_kind_env.lookup_all_with_imprecise_name(impl_imprecise_name, lookup_filter, self.typing_interner));

        let mut impl_defs_with_duplicates: Vec<ImplDefinitionTemplataT<'s, 't>> = Vec::new();
        let mut impl_templatas_with_duplicates: Vec<IsaTemplataT<'s, 't>> = Vec::new();
        for m in matching {
            match m {
                ITemplataT::ImplDefinition(it) => impl_defs_with_duplicates.push(*it),
                ITemplataT::Isa(it) => impl_templatas_with_duplicates.push(*it),
                _ => panic!("vwat: unexpected templata in isParent matching"),
            }
        }

        // Check if there's already a compiled IsaTemplataT that directly matches.
        if let Some(impl_isa) = impl_templatas_with_duplicates.iter().find(|i| KindT::from(sub_kind_tt) == i.sub_kind && KindT::from(super_kind_tt) == i.super_kind) {
            coutputs.add_instantiation_bounds(
                self.opts.global_options.sanity_check,
                self.typing_interner,
                calling_env.denizen_template_id(),
                impl_isa.impl_name,
                make(self.typing_interner, vec![], vec![], vec![]));
            return IsParentResult::IsParent(IsParent {
                templata: ITemplataT::Isa(self.typing_interner.alloc(*impl_isa)),
                conclusions: IndexMap::new(),
                impl_id: impl_isa.impl_name,
            });
        }

        let mut seen_ranges: HashSet<RangeS<'s>> = HashSet::new();
        let impl_defs: Vec<ImplDefinitionTemplataT<'s, 't>> = impl_defs_with_duplicates.into_iter()
            .filter(|d| seen_ranges.insert(d.impl_.range))
            .collect();

        let results: Vec<Result<(ImplDefinitionTemplataT<'s, 't>, CompleteResolveSolve<'s, 't>), IResolvingError<'s, 't>>> =
            impl_defs.iter().map(|impl_def| {
                let initial_knowns = vec![
                    InitialKnown { rune: impl_def.impl_.sub_citizen_rune, templata: ITemplataT::Kind(self.typing_interner.alloc(KindTemplataT { kind: KindT::from(sub_kind_tt) })) },
                    InitialKnown { rune: impl_def.impl_.interface_kind_rune, templata: ITemplataT::Kind(self.typing_interner.alloc(KindTemplataT { kind: KindT::from(super_kind_tt) })) },
                ];
                self.resolve_impl(coutputs, parent_ranges, call_location, calling_env, &initial_knowns, *impl_def)
                    .map(|ccs| (*impl_def, ccs))
            }).collect();

        let (oks, errs): (Vec<_>, Vec<_>) = results.into_iter().partition(|r| r.is_ok());
        assert!(oks.len() <= 1);
        match oks.into_iter().next() {
            Some(Ok((impl_templata, CompleteResolveSolve { conclusions, rune_to_bound }))) => {
                let template_args: Vec<ITemplataT<'s, 't>> =
                    impl_templata.impl_.generic_params.iter().map(|p| *conclusions.get(&p.rune.rune).unwrap()).collect();
                let impl_template_name: INameT<'s, 't> = match self.translate_impl_name(impl_templata.impl_.name) {
                    IImplTemplateNameT::ImplTemplate(r) => INameT::ImplTemplate(r),
                    IImplTemplateNameT::ImplBoundTemplate(_) => panic!("Unimplemented: ImplBoundTemplate in isParent"),
                    IImplTemplateNameT::AnonymousSubstructImplTemplate(r) => INameT::AnonymousSubstructImplTemplate(r),
                };
                let impl_template_id = impl_templata.env.id().add_step(self.typing_interner, impl_template_name);
                let instantiated_id = self.assemble_impl_name(*impl_template_id, &template_args, sub_kind_tt.expect_citizen());
                coutputs.add_instantiation_bounds(
                    self.opts.global_options.sanity_check,
                    self.typing_interner,
                    calling_env.root_compiling_denizen_env().denizen_template_id(),
                    instantiated_id,
                    rune_to_bound);
                IsParentResult::IsParent(IsParent {
                    templata: ITemplataT::ImplDefinition(self.typing_interner.alloc(impl_templata)),
                    conclusions,
                    impl_id: instantiated_id,
                })
            }
            Some(Err(_)) => unreachable!(),
            None => {
                let err_vec: Vec<IResolvingError<'s, 't>> = errs.into_iter().map(|r| match r { Err(e) => e, Ok(_) => unreachable!() }).collect();
                IsParentResult::IsntParent(IsntParent { candidates: err_vec })
            }
        }
    }

}