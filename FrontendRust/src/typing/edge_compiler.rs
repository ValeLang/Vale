use crate::postparsing::ast::LocationInDenizen;
use crate::typing::compiler::Compiler;
use std::collections::HashMap;
use indexmap::IndexMap;
use crate::utils::range::RangeS;
use crate::postparsing::names::*;
use crate::postparsing::*;
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
use crate::interner::Interner;
use crate::utils::arena_index_map::ArenaIndexMap;
use crate::typing::function::function_compiler::IDefineFunctionResult;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::typing::names::names::{KindPlaceholderNameT, KindPlaceholderTemplateNameT};
use crate::typing::types::types::{IRegionT, KindPlaceholderT, KindT, RegionT};
use crate::typing::templata::templata::PlaceholderTemplataT;
use crate::typing::env::environment::child_of;
use crate::typing::infer_compiler::InitialKnown;
use crate::typing::templata_compiler::IBoundArgumentsSource;
use crate::postparsing::rules::rules::RuneUsage;
use crate::utils::range::CodeLocationS;
use crate::postparsing::names::{IRuneValS, DispatcherRuneFromImplValS};
use crate::typing::names::names::{INameT, IPlaceholderNameT};
use crate::typing::names::names::IImplTemplateNameT;
use crate::typing::templata::templata::expect_coord_templata;
use crate::typing::names::names::IdValT;
use crate::typing::templata::templata::KindTemplataT;
use crate::typing::templata::templata::CoordTemplataT;
use crate::typing::types::types::CoordT;
use crate::postparsing::names::CaseRuneFromImplValS;
use crate::typing::infer_compiler::CompleteResolveSolve;
use std::collections::HashSet;
use std::marker::PhantomData;

pub enum IMethod<'s, 't> {
    NeededOverride(NeededOverride<'s, 't>),
    FoundFunction(FoundFunction<'s, 't>),
}

pub struct NeededOverride<'s, 't> {
    pub name: IImpreciseNameS<'s>,
    pub param_filters: Vec<CoordT<'s, 't>>,
}

pub struct FoundFunction<'s, 't> {
    pub prototype: PrototypeT<'s, 't>,
}

pub struct PartialEdgeT<'s, 't> {
    pub struct_tt: StructTT<'s, 't>,
    pub interface: InterfaceTT<'s, 't>,
    pub methods: Vec<IMethod<'s, 't>>,
}

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn compile_i_tables(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
    ) -> (Vec<&'t InterfaceEdgeBlueprintT<'s, 't>>, HashMap<IdT<'s, 't>, HashMap<IdT<'s, 't>, &'t EdgeT<'s, 't>>>) {
        // val interfaceEdgeBlueprints = makeInterfaceEdgeBlueprints(coutputs)
        let interface_edge_blueprints = self.make_interface_edge_blueprints(coutputs);

        // val itables = interfaceEdgeBlueprints.map(interfaceEdgeBlueprint => { ... })
        let itables: HashMap<IdT<'s, 't>, HashMap<IdT<'s, 't>, &'t EdgeT<'s, 't>>> =
            interface_edge_blueprints.iter().map(|interface_edge_blueprint| {
                let interface_placeholdered_id = interface_edge_blueprint.interface;
                let interface_template_id = self.get_interface_template(interface_placeholdered_id);
                let interface_id = coutputs.lookup_interface_by_template_name(interface_template_id).instantiated_interface.id;
                let overriding_impls = coutputs.get_child_impls_for_super_interface_template(interface_template_id);
                let overriding_citizen_to_found_function: HashMap<IdT<'s, 't>, &'t EdgeT<'s, 't>> =
                    overriding_impls.iter().map(|overriding_impl| -> (IdT<'s,'t>, &'t EdgeT<'s,'t>) {
                        let overriding_citizen_template_id = overriding_impl.sub_citizen_template_id;
                        let found_functions: Vec<(IdT<'s, 't>, &'t OverrideT<'s, 't>)> =
                            interface_edge_blueprint.super_family_root_headers.iter().map(|(abstract_function_prototype, abstract_index)| -> (IdT<'s,'t>, &'t OverrideT<'s,'t>) {
                                let overrride = self.look_for_override(
                                    coutputs,
                                    LocationInDenizen { path: &[] },
                                    overriding_impl,
                                    interface_template_id,
                                    overriding_citizen_template_id,
                                    *abstract_function_prototype,
                                    *abstract_index,
                                ).unwrap_or_else(|_| panic!("implement: ICompileErrorT from look_for_override in compile_i_tables"));
                                (abstract_function_prototype.id, self.typing_interner.alloc(overrride))
                            }).collect();
                        let overriding_citizen = overriding_impl.sub_citizen;
                        assert!(coutputs.get_instantiation_bounds(self.typing_interner, ISubKindTT::from(overriding_citizen).id()).is_some());
                        let super_interface_id = overriding_impl.super_interface.id;
                        assert!(coutputs.get_instantiation_bounds(self.typing_interner, super_interface_id).is_some());
                        let mut abstract_func_to_override_func = ArenaIndexMap::new_in(self.typing_interner.bump());
                        for (k, v) in found_functions {
                            abstract_func_to_override_func.insert(k, v);
                        }
                        let edge = self.typing_interner.alloc(EdgeT {
                            edge_id: overriding_impl.instantiated_id,
                            sub_citizen: overriding_citizen,
                            super_interface: super_interface_id,
                            instantiation_bound_params: overriding_impl.instantiation_bound_params,
                            abstract_func_to_override_func,
                        });
                        let overriding_citizen_def = coutputs.lookup_citizen_by_template_name(overriding_citizen_template_id);
                        (ISubKindTT::from(overriding_citizen_def.instantiated_citizen()).id(), edge)
                    }).collect();
                (interface_id, overriding_citizen_to_found_function)
            }).collect();

        (interface_edge_blueprints, itables)
    }

    pub fn make_interface_edge_blueprints(
        &self,
        coutputs: &CompilerOutputs<'s, 't>,
    ) -> Vec<&'t InterfaceEdgeBlueprintT<'s, 't>> {
        // val x1 = coutputs.getAllFunctions().flatMap(function => function.header.getAbstractInterface match {
        //   case None => Vector.empty
        //   case Some(abstractInterface) => Vector(abstractInterfaceTemplate -> function)
        // })
        let x1: Vec<(IdT<'s, 't>, &'t FunctionDefinitionT<'s, 't>)> =
            coutputs.get_all_functions().iter().flat_map(|function| -> Vec<(IdT<'s, 't>, &'t FunctionDefinitionT<'s, 't>)> {
                match function.header.get_abstract_interface() {
                    None => Vec::new(),
                    Some(abstract_interface) => {
                        let abstract_interface_template = self.get_interface_template(abstract_interface.id);
                        vec![(abstract_interface_template, *function)]
                    }
                }
            }).collect();

        // val x2 = x1.groupBy(_._1)
        // val x3 = x2.mapValues(_.map(_._2))
        // Per @IIIOZ: IndexMap so iteration at line 281 is deterministic across runs.
        // x1 is a Vec built in deterministic source order (from get_all_functions, now IndexMap-backed).
        let mut x3: IndexMap<IdT<'s, 't>, Vec<&'t FunctionDefinitionT<'s, 't>>> = IndexMap::new();
        for (k, v) in x1.into_iter() {
            x3.entry(k).or_insert_with(Vec::new).push(v);
        }

        // val x4 = x3.map({ case (interfaceTemplateId, functions) => ... orderedMethods ... })
        let x4: IndexMap<IdT<'s, 't>, Vec<(PrototypeT<'s, 't>, usize)>> = x3.into_iter().map(|(interface_template_id, functions)| {
            // Sort so that the interface's internal methods are first and in the same order
            // they were declared in. It feels right, and vivem also depends on it
            // when it calls array generators/consumers' first method.
            let interface_def =
                coutputs.get_all_interfaces().into_iter()
                    .find(|i| i.template_name == interface_template_id)
                    .unwrap_or_else(|| panic!("vassertSome: find interface by templateName in x4"));
            // Make sure `functions` has everything that the interface def wanted.
            let functions_set: HashSet<(SignatureT<'s, 't>, usize)> =
                functions.iter().map(|f| (f.header.to_signature(), f.header.get_virtual_index().expect("vassertSome"))).collect();
            let internal_methods_set: HashSet<(SignatureT<'s, 't>, usize)> =
                interface_def.internal_methods.iter().map(|(p, vi)| (p.to_signature(), *vi)).collect();
            let missing = internal_methods_set.difference(&functions_set).count();
            assert!(missing == 0, "vassert: functions missing some internal methods");
            // Move all the internal methods to the front.
            let mut ordered_methods: Vec<(PrototypeT<'s, 't>, usize)> =
                interface_def.internal_methods.iter().map(|(p, vi)| (*p, *vi)).collect();
            for function in functions.iter() {
                let header = &function.header;
                let prototype = header.to_prototype();
                let already_in_internal = interface_def.internal_methods.iter().any(|(p, _)| p.to_signature() == prototype.to_signature());
                if !already_in_internal {
                    let virtual_index = header.get_virtual_index().expect("vassertSome: getVirtualIndex for abstract header");
                    ordered_methods.push((prototype, virtual_index));
                }
            }
            (interface_template_id, ordered_methods)
        }).collect();

        // val abstractFunctionHeadersByInterfaceTemplateId = x4 ++ coutputs.getAllInterfaces().map(...)
        // Some interfaces would be empty and they wouldn't be in x4, so we add them here.
        let mut abstract_function_headers: IndexMap<IdT<'s, 't>, Vec<(PrototypeT<'s, 't>, usize)>> = x4;
        for interface_def in coutputs.get_all_interfaces().iter() {
            abstract_function_headers.entry(interface_def.template_name).or_insert_with(Vec::new);
        }

        // val interfaceEdgeBlueprints = abstractFunctionHeadersByInterfaceTemplateId.map(...).toVector
        abstract_function_headers.into_iter().map(|(interface_template_id, function_headers)| -> &'t InterfaceEdgeBlueprintT<'s, 't> {
            let interface_def = coutputs.lookup_interface_by_template_name(interface_template_id);
            let super_family_root_headers = self.typing_interner.alloc_slice_from_vec(
                function_headers.into_iter().map(|(p, vi)| (p, vi as i32)).collect());
            self.typing_interner.alloc(InterfaceEdgeBlueprintT {
                interface: interface_def.instantiated_interface.id,
                super_family_root_headers,
            })
        }).collect()
    }

    pub fn create_override_placeholder_mimicking(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        original_templata_to_mimic: ITemplataT<'s, 't>,
        dispatcher_outer_env: IInDenizenEnvironmentT<'s, 't>,
        index: i32,
        rune: IRuneS<'s>,
    ) -> ITemplataT<'s, 't> {

        let placeholder_name = self.typing_interner.intern_kind_placeholder_name(KindPlaceholderNameT {
            template: self.typing_interner.intern_kind_placeholder_template_name(KindPlaceholderTemplateNameT {
                index,
                rune,
            }),
        });
        let placeholder_id_ref = dispatcher_outer_env.id().add_step(self.typing_interner, INameT::KindPlaceholder(placeholder_name));
        let placeholder_id = *placeholder_id_ref;
        let placeholder_template_id = self.get_placeholder_template(placeholder_id);
        let placeholder_template_id_ref = self.typing_interner.intern_id(IdValT {
            package_coord: placeholder_template_id.package_coord,
            init_steps: placeholder_template_id.init_steps,
            local_name: placeholder_template_id.local_name,
        });
        coutputs.declare_type(placeholder_template_id_ref);
        coutputs.declare_type_outer_env(
            placeholder_template_id_ref,
            IInDenizenEnvironmentT::from(child_of(
                self.typing_interner,
                self.scout_arena,
                dispatcher_outer_env,
                placeholder_template_id,
                placeholder_template_id_ref,
                vec![],
            )),
        );

        match original_templata_to_mimic {
            ITemplataT::Placeholder(pt) => {
                ITemplataT::Placeholder(self.typing_interner.alloc(PlaceholderTemplataT { id: placeholder_id, tyype: pt.tyype }))
            }
            ITemplataT::Kind(kt) => match kt.kind {
                KindT::KindPlaceholder(kp) => {
                    let original_placeholder_template_id = self.get_placeholder_template(kp.id);
                    let mutability = coutputs.lookup_mutability(original_placeholder_template_id);
                    coutputs.declare_type_mutability(placeholder_template_id_ref, mutability);
                    ITemplataT::Kind(self.typing_interner.alloc(KindTemplataT {
                        kind: KindT::KindPlaceholder(self.typing_interner.intern_kind_placeholder(KindPlaceholderT { id: placeholder_id })),
                    }))
                }
                _ => panic!("vwat: create_override_placeholder_mimicking unexpected kind"),
            },
            ITemplataT::Coord(ct) => match ct.coord.kind {
                KindT::KindPlaceholder(kp) => {
                    let original_placeholder_template_id = self.get_placeholder_template(kp.id);
                    let mutability = coutputs.lookup_mutability(original_placeholder_template_id);
                    coutputs.declare_type_mutability(placeholder_template_id_ref, mutability);
                    ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT {
                        coord: CoordT {
                            ownership: ct.coord.ownership,
                            region: RegionT { region: IRegionT::Default },
                            kind: KindT::KindPlaceholder(self.typing_interner.intern_kind_placeholder(KindPlaceholderT { id: placeholder_id })),
                        },
                    }))
                }
                _ => panic!("vwat: create_override_placeholder_mimicking unexpected coord kind"),
            },
            other => panic!("vwat: create_override_placeholder_mimicking unexpected templata: {:?}", other),
        }
    }

    pub fn look_for_override(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        call_location: LocationInDenizen<'s>,
        impl_t: &'t ImplT<'s, 't>,
        interface_template_id: IdT<'s, 't>,
        sub_citizen_template_id: IdT<'s, 't>,
        abstract_function_prototype: PrototypeT<'s, 't>,
        abstract_index: i32,
    ) -> Result<OverrideT<'s, 't>, ICompileErrorT<'s, 't>> {

        let abstract_func_template_id = Compiler::get_function_template(self.typing_interner, abstract_function_prototype.id);
        let abstract_function_param_unsubstituted_types = abstract_function_prototype.param_types();
        assert!(abstract_index >= 0);
        let abstract_param_unsubstituted_type = abstract_function_param_unsubstituted_types[abstract_index as usize];

        let maybe_origin_function_templata =
            coutputs.lookup_function(self.typing_interner.alloc(abstract_function_prototype.to_signature()))
                .and_then(|f| f.header.maybe_origin_function_templata);

        let range = maybe_origin_function_templata
            .map(|o| o.function.range)
            .unwrap_or_else(|| {
                let loc = CodeLocationS::internal(self.scout_arena, -2976395);
                RangeS { begin: loc, end: loc }
            });

        let origin_function_templata = maybe_origin_function_templata
            .expect("vassertSome: originFunctionTemplata");

        let abstract_func_outer_env = coutputs.get_outer_env_for_function(abstract_func_template_id);

        let dispatcher_template_name = self.typing_interner.intern_override_dispatcher_template_name(
            OverrideDispatcherTemplateNameT { impl_id: impl_t.template_id }
        );
        let dispatcher_template_id_ref = abstract_func_template_id.add_step(
            self.typing_interner,
            INameT::OverrideDispatcherTemplate(dispatcher_template_name),
        );
        let dispatcher_outer_env: &'t GeneralEnvironmentT<'s, 't> = child_of(
            self.typing_interner,
            self.scout_arena,
            abstract_func_outer_env,
            *dispatcher_template_id_ref,
            dispatcher_template_id_ref,
            vec![],
        );

        // Step 1: Get The Compiled Impl's Interface, see GTCII.

        let instantiated_local = IInstantiationNameT::try_from(impl_t.instantiated_id.local_name)
            .expect("impl instantiated_id local_name should be IInstantiationNameT");
        let impl_placeholder_to_dispatcher_placeholder: Vec<(IdT<'s, 't>, ITemplataT<'s, 't>)> =
            instantiated_local.template_args().iter()
                .zip(impl_t.rune_index_to_independence.iter())
                .filter(|(_, &independent)| !independent)
                .map(|(templata, _)| *templata)
                .enumerate()
                .map(|(impl_placeholder_index, impl_placeholder)| {
                    let impl_placeholder_id = Compiler::get_placeholder_templata_id(impl_placeholder);
                    let impl_placeholder_local_name = IPlaceholderNameT::try_from(impl_placeholder_id.local_name)
                        .unwrap_or_else(|_| panic!("vwat: expected IPlaceholderNameT for impl placeholder local_name"));
                    let impl_rune = impl_placeholder_local_name.rune();
                    // Sanity check we're in an impl template, we're about to replace it with a function template
                    match impl_placeholder_id.init_steps.last() {
                        Some(name) => {
                            IImplTemplateNameT::try_from(*name).unwrap_or_else(|_| panic!("vwat: last init step should be IImplTemplateNameT, got {:?}", name));
                        }
                        None => panic!("vwat: last init step should be IImplTemplateNameT, got None"),
                    }
                    let dispatcher_rune = self.scout_arena.intern_rune(IRuneValS::DispatcherRuneFromImpl(DispatcherRuneFromImplValS { inner_rune: impl_rune }));
                    let dispatcher_placeholder = self.create_override_placeholder_mimicking(
                        coutputs,
                        impl_placeholder,
                        IInDenizenEnvironmentT::from(dispatcher_outer_env),
                        impl_placeholder_index as i32,
                        dispatcher_rune,
                    );
                    (impl_placeholder_id, dispatcher_placeholder)
                })
                .collect();
        let dispatcher_placeholders: Vec<ITemplataT<'s, 't>> =
            impl_placeholder_to_dispatcher_placeholder.iter().map(|(_, v)| *v).collect();

        for (impl_placeholder_id, _) in impl_placeholder_to_dispatcher_placeholder.iter() {
            assert!(impl_placeholder_id.init_id(self.typing_interner) == impl_t.template_id);
        }

        let dispatcher_placeholdered_interface: &'t InterfaceTT<'s, 't> = {
            let super_interface_ref = self.typing_interner.alloc(impl_t.super_interface);
            let substituted = Compiler::substitute_templatas_in_kind(
                coutputs,
                self.opts.global_options.sanity_check,
                self.typing_interner,
                self.keywords,
                *dispatcher_template_id_ref,
                impl_t.template_id,
                &dispatcher_placeholders,
                IBoundArgumentsSource::InheritBoundsFromTypeItself,
                KindT::Interface(super_interface_ref),
            );
            match substituted {
                ITemplataT::Kind(k) => k.kind.expect_interface(),
                _ => panic!("expected KindTemplataT from substituteTemplatasInKind"),
            }
        };
        let dispatcher_placeholdered_abstract_param_type = CoordT {
            kind: KindT::Interface(dispatcher_placeholdered_interface),
            ..abstract_param_unsubstituted_type
        };

        // Step 2: Compile Dispatcher Function Given Interface, see CDFGI

        let define_result = self.evaluate_generic_virtual_dispatcher_function_for_prototype(
            coutputs,
            &[range, impl_t.templata.impl_.range],
            call_location,
            IInDenizenEnvironmentT::from(dispatcher_outer_env),
            origin_function_templata,
            &{
                let mut args: Vec<Option<CoordT<'s, 't>>> =
                    abstract_function_prototype.param_types().iter().map(|_| None).collect();
                args[abstract_index as usize] = Some(dispatcher_placeholdered_abstract_param_type);
                args
            },
        )?;
        let (dispatching_func_prototype, dispatcher_inner_inferences, dispatcher_instantiation_bound_params) =
            match define_result {
                IDefineFunctionResult::DefineFunctionFailure(_f) => {
                    panic!("Unimplemented: CouldntEvaluateFunction error from dispatcher");
                }
                IDefineFunctionResult::DefineFunctionSuccess(s) => {
                    (s.prototype, s.inferences, s.instantiation_bound_params)
                }
            };
        let dispatcher_params: Vec<CoordT<'s, 't>> =
            origin_function_templata.function.params.iter()
                .map(|p| p.pattern.coord_rune.unwrap().rune)
                .map(|rune| {
                    let templata = *dispatcher_inner_inferences.get(&rune)
                        .unwrap_or_else(|| panic!("vassertSome: rune {:?} not in dispatcherInnerInferences", rune));
                    expect_coord_templata(templata).coord
                })
                .collect();
        // Any generic parameter of the abstract function that wasn't pinned by the impl's self-type
        // gets a fresh dispatcher-owned placeholder inside evaluate_generic_virtual_dispatcher_function_for_prototype.
        // Collect those so they appear in the dispatcher's templateArgs — the Instantiator's
        // assemble_placeholder_map zips templateArgs with concrete args at monomorphization, so any
        // placeholder that doesn't appear here can't be substituted and trips a vassertSome later.
        // Example: map<T, R>(&Opt<T>, &IFunction1<mut,&T,R>) Opt<R> with impl<I> Opt<I> for Some<I> —
        // T is mimicked from I, but R has no impl-side counterpart and is a fresh placeholder.
        let existing_dispatcher_placeholder_ids: HashSet<IdT<'s, 't>> =
            dispatcher_placeholders.iter()
                .map(|p| Compiler::get_placeholder_templata_id(*p))
                .collect();
        let fresh_dispatcher_placeholders: Vec<ITemplataT<'s, 't>> =
            origin_function_templata.function.generic_parameters.iter()
                .filter_map(|gp| {
                    dispatcher_inner_inferences.get(&gp.rune.rune).and_then(|templata| match *templata {
                        ITemplataT::Coord(&CoordTemplataT { coord: CoordT { kind: KindT::KindPlaceholder(&KindPlaceholderT { id }), .. } }) =>
                            if existing_dispatcher_placeholder_ids.contains(&id) { None } else { Some(*templata) },
                        ITemplataT::Kind(&KindTemplataT { kind: KindT::KindPlaceholder(&KindPlaceholderT { id }) }) =>
                            if existing_dispatcher_placeholder_ids.contains(&id) { None } else { Some(*templata) },
                        _ => None,
                    })
                })
                .collect();
        let all_dispatcher_placeholders: Vec<ITemplataT<'s, 't>> =
            dispatcher_placeholders.iter().chain(fresh_dispatcher_placeholders.iter()).copied().collect();
        let dispatcher_id_ref = {
            let func_name = IFunctionTemplateNameT::try_from(dispatcher_template_id_ref.local_name)
                .expect("dispatcher_template_id local_name should be IFunctionTemplateNameT");
            let local_name = func_name.make_function_name(
                self.typing_interner,
                self.keywords,
                &all_dispatcher_placeholders,
                &dispatcher_params,
            );
            self.typing_interner.intern_id(IdValT {
                package_coord: dispatcher_template_id_ref.package_coord,
                init_steps: dispatcher_template_id_ref.init_steps,
                local_name,
            })
        };
        let dispatcher_inner_env: &'t GeneralEnvironmentT<'s, 't> = child_of(
            self.typing_interner,
            self.scout_arena,
            IInDenizenEnvironmentT::from(dispatcher_outer_env),
            *dispatcher_template_id_ref,
            dispatcher_id_ref,
            dispatcher_inner_inferences.iter().map(|(name_s, templata): (&IRuneS<'s>, &ITemplataT<'s, 't>)| {
                let rune_name = self.typing_interner.intern_rune_name(RuneNameT { rune: *name_s});
                (INameT::from(rune_name), IEnvEntryT::Templata(*templata))
            }).collect(),
        );

        // Step 3: Figure Out Dependent And Independent Runes, see FODAIR.

        let impl_independent_rune_to_impl_placeholder_and_case_placeholder: Vec<(IRuneS<'s>, IdT<'s, 't>, ITemplataT<'s, 't>)> =
            impl_t.templata.impl_.generic_params.iter()
                .map(|p| p.rune.rune)
                .zip(instantiated_local.template_args().iter())
                .zip(impl_t.rune_index_to_independence.iter())
                .filter(|((_, _), &independent)| independent)
                .map(|((impl_rune, templata), _)| (impl_rune, *templata))
                .enumerate()
                .map(|(index, (impl_rune, impl_placeholder_templata))| {
                    let case_rune = self.scout_arena.intern_rune(
                        IRuneValS::CaseRuneFromImpl(
                            CaseRuneFromImplValS { inner_rune: impl_rune }));
                    let impl_placeholder_id = Compiler::get_placeholder_templata_id(impl_placeholder_templata);
                    let case_placeholder = self.create_override_placeholder_mimicking(
                        coutputs, impl_placeholder_templata, IInDenizenEnvironmentT::from(dispatcher_inner_env), index as i32, case_rune);
                    (impl_rune, impl_placeholder_id, case_placeholder)
                })
                .collect();
        let impl_independent_rune_to_case_placeholder: Vec<(IRuneS<'s>, ITemplataT<'s, 't>)> =
            impl_independent_rune_to_impl_placeholder_and_case_placeholder.iter()
                .map(|(rune, _, case_placeholder)| (*rune, *case_placeholder))
                .collect();
        let impl_independent_placeholder_to_case_placeholder: Vec<(IdT<'s, 't>, ITemplataT<'s, 't>)> =
            impl_independent_rune_to_impl_placeholder_and_case_placeholder.iter()
                .map(|(_, impl_placeholder, case_placeholder)| (*impl_placeholder, *case_placeholder))
                .collect();

        let partial_resolve_conclusions =
            self.partial_resolve_impl(
                coutputs,
                &[range],
                call_location,
                IInDenizenEnvironmentT::from(dispatcher_inner_env),
                &{
                    let mut knowns = vec![InitialKnown {
                        rune: RuneUsage { range, rune: impl_t.templata.impl_.interface_kind_rune.rune },
                        templata: ITemplataT::Kind(self.typing_interner.alloc(KindTemplataT { kind: KindT::Interface(dispatcher_placeholdered_interface) })),
                    }];
                    for (rune, templata) in impl_independent_rune_to_case_placeholder.iter() {
                        knowns.push(InitialKnown {
                            rune: RuneUsage { range, rune: *rune },
                            templata: *templata,
                        });
                    }
                    knowns
                },
                impl_t.templata,
            ).unwrap_or_else(|_| panic!("vassert: partialResolveImpl should succeed"));

        // Only grab sub_citizen entries, and assert we can't handle non-empty ones yet.
        let dispatcher_and_case_placeholdered_impl_reachable_prototypes:
                Vec<(IRuneS<'s>, IRuneS<'s>, PrototypeTemplataT<'s, 't>)> =
            partial_resolve_conclusions.iter()
                .filter(|(rune_in_impl, _)| **rune_in_impl == impl_t.templata.impl_.sub_citizen_rune.rune)
                .filter_map(|(rune_in_impl, templata)| match templata {
                    ITemplataT::Kind(kt)  => ICitizenTT::try_from(kt.kind).ok().map(|c| (*rune_in_impl, c)),
                    ITemplataT::Coord(ct) => ICitizenTT::try_from(ct.coord.kind).ok().map(|c| (*rune_in_impl, c)),
                    _ => None,
                })
                .flat_map(|(rune_in_impl, c)| {
                    let citizen_id = c.id();
                    let citizen_template_id = self.get_citizen_template(citizen_id);
                    let substituter = self.get_placeholder_substituter(
                        self.opts.global_options.sanity_check,
                        *dispatcher_template_id_ref,
                        citizen_id,
                        IBoundArgumentsSource::InheritBoundsFromTypeItself,
                    );
                    let raw_entries: Vec<(IRuneS<'s>, &'t FunctionBoundTemplateNameT<'s>, &'t [ITemplataT<'s, 't>], &'t [CoordT<'s, 't>], CoordT<'s, 't>)> = {
                        let citizen_inner_env = coutputs.get_inner_env_for_type(citizen_template_id);
                        citizen_inner_env.templatas().name_to_entry.iter()
                            .filter_map(|(name, entry)| {
                                let rune_in_citizen = match name {
                                    INameT::Rune(r) => r,
                                    _ => return None,
                                };
                                let proto_templata = match entry {
                                    IEnvEntryT::Templata(ITemplataT::Prototype(pt)) => pt,
                                    _ => return None,
                                };
                                let function_bound = match proto_templata.prototype.id.local_name {
                                    INameT::FunctionBound(fb) => fb,
                                    _ => return None,
                                };
                                Some((rune_in_citizen.rune, function_bound.template, function_bound.template_args, function_bound.parameters, proto_templata.prototype.return_type))
                            })
                            .collect()
                    }; // citizen_inner_env borrow released here
                    // Phase 2: apply mutation, building substituted prototypes
                    raw_entries.into_iter().map(|(rune_in_citizen, human_name, template_args, params, return_type)| {
                        let function_bound_template_name = self.typing_interner.intern_function_bound_template_name(
                            FunctionBoundTemplateNameT { human_name: human_name.human_name});
                        let function_bound_name = self.typing_interner.intern_function_bound_name(
                            FunctionBoundNameValT { template: function_bound_template_name, template_args, parameters: params });
                        let sub_citizen_placeholdered_prototype = self.typing_interner.intern_prototype(PrototypeValT {
                            id: IdValT { package_coord: dispatcher_id_ref.package_coord, init_steps: dispatcher_id_ref.init_steps, local_name: INameT::FunctionBound(function_bound_name) },
                            return_type,
                        });
                        let dispatcher_placeholdered_prototype = substituter.substitute_for_prototype(coutputs, sub_citizen_placeholdered_prototype);
                        let prototype_templata = self.typing_interner.alloc(PrototypeTemplataT { prototype: dispatcher_placeholdered_prototype });
                        (rune_in_impl, rune_in_citizen, *prototype_templata)
                    }).collect::<Vec<_>>()
                })
                .collect();

        let dispatcher_inner_env_with_bounds_for_sub_citizen: &'t GeneralEnvironmentT<'s, 't> = child_of(
            self.typing_interner,
            self.scout_arena,
            IInDenizenEnvironmentT::from(dispatcher_inner_env),
            *dispatcher_template_id_ref,
            dispatcher_id_ref,
            dispatcher_and_case_placeholdered_impl_reachable_prototypes.iter().enumerate()
                .map(|(index, (_rune_in_impl, _rune_in_citizen, dispatcher_placeholdered_reachable_prototype))| {
                    let reachable_prototype_name = self.typing_interner.intern_reachable_prototype_name(
                        ReachablePrototypeNameT { num: index as i32});
                    (INameT::ReachablePrototype(reachable_prototype_name), IEnvEntryT::Templata(ITemplataT::Prototype(self.typing_interner.alloc(PrototypeTemplataT { prototype: dispatcher_placeholdered_reachable_prototype.prototype }))))
                })
                .collect(),
        );

        let resolve_result = self.resolve_impl(
            coutputs,
            &[range],
            call_location,
            IInDenizenEnvironmentT::from(dispatcher_inner_env_with_bounds_for_sub_citizen),
            &{
                let mut knowns = vec![InitialKnown {
                    rune: RuneUsage { range, rune: impl_t.templata.impl_.interface_kind_rune.rune },
                    templata: ITemplataT::Kind(self.typing_interner.alloc(KindTemplataT { kind: KindT::Interface(dispatcher_placeholdered_interface) })),
                }];
                for (rune, templata) in impl_independent_rune_to_case_placeholder.iter() {
                    knowns.push(InitialKnown {
                        rune: RuneUsage { range, rune: *rune },
                        templata: *templata,
                    });
                }
                knowns
            },
            impl_t.templata,
        );
        let (impl_conclusions, _impl_instantiation_bound_args_unused) = match resolve_result {
            Ok(CompleteResolveSolve { conclusions, rune_to_bound }) => {
                (conclusions, rune_to_bound)
            }
            Err(_e) => panic!("Unimplemented: TypingPassResolvingError from resolveImpl"),
        };

        // Step 4: Figure Out Struct For Case, see FOSFC.

        let dispatcher_case_placeholdered_sub_citizen: ICitizenTT<'s, 't> = {
            let templata = impl_conclusions.get(&impl_t.templata.impl_.sub_citizen_rune.rune)
                .expect("vassertSome: implConclusions.get(subCitizenRune)");
            match templata {
                ITemplataT::Kind(k) => k.kind.expect_citizen(),
                _ => panic!("expected KindTemplataT for subCitizenRune conclusion"),
            }
        };

        // Step 5: Assemble the Case Environment For Resolving the Override, see ACEFRO

        let override_imprecise_name = get_imprecise_name(self.scout_arena, abstract_function_prototype.id.local_name)
            .expect("vassertSome: getImpreciseName for abstractFunctionPrototype");
        let case_placeholder_templatas: Vec<ITemplataT<'s, 't>> =
            impl_independent_rune_to_case_placeholder.iter().map(|(_, t)| *t).collect();
        let dispatcher_case_name = self.typing_interner.intern_override_dispatcher_case_name(
            OverrideDispatcherCaseNameValT {
                independent_impl_template_args: &case_placeholder_templatas,
            }
        );
        let dispatcher_case_id_ref = dispatcher_id_ref.add_step(
            self.typing_interner,
            INameT::OverrideDispatcherCase(dispatcher_case_name),
        );
        let dispatcher_case_env: &'t GeneralEnvironmentT<'s, 't> = child_of(
            self.typing_interner,
            self.scout_arena,
            IInDenizenEnvironmentT::from(dispatcher_inner_env_with_bounds_for_sub_citizen),
            *dispatcher_case_id_ref,
            dispatcher_case_id_ref,
            vec![],
        );

        // Step 6: Use Case Environment to Find Override, see UCEFO.

        let overriding_param_coord = CoordT {
            kind: KindT::from(dispatcher_case_placeholdered_sub_citizen),
            ..dispatcher_placeholdered_abstract_param_type
        };
        let mut override_function_param_types: Vec<CoordT<'s, 't>> =
            dispatching_func_prototype.prototype.param_types().to_vec();
        override_function_param_types[abstract_index as usize] = overriding_param_coord;

        let extra_envs: Vec<IInDenizenEnvironmentT<'s, 't>> = vec![
            coutputs.get_outer_env_for_type(&[range, impl_t.templata.impl_.range], interface_template_id),
            coutputs.get_outer_env_for_type(&[range, impl_t.templata.impl_.range], sub_citizen_template_id),
        ];
        let found_function = match self.find_function(
            IInDenizenEnvironmentT::from(dispatcher_case_env),
            coutputs,
            &[range, impl_t.templata.impl_.range],
            call_location,
            override_imprecise_name,
            &[],
            &[],
            &[],
            RegionT { region: IRegionT::Default },
            &override_function_param_types,
            &extra_envs,
            true,
        ).unwrap_or_else(|_e| panic!("Unimplemented: ICompileErrorT from find_function in look_for_override")) {
            Err(_e) => panic!("Unimplemented: CouldntFindOverrideT error"),
            Ok(x) => x,
        };

        assert!(coutputs.get_instantiation_bounds(self.typing_interner, found_function.prototype.id).is_some());

        let impl_placeholder_to_dispatcher_placeholder_slice =
            self.typing_interner.alloc_slice_from_vec(impl_placeholder_to_dispatcher_placeholder);
        let impl_independent_placeholder_to_case_placeholder_slice =
            self.typing_interner.alloc_slice_from_vec(impl_independent_placeholder_to_case_placeholder);

        let reachable_map = {
            let mut grouped: HashMap<IRuneS<'s>, Vec<(IRuneS<'s>, PrototypeT<'s, 't>)>> = HashMap::new();
            for (rune_in_impl, rune_in_citizen, prototype_templata) in &dispatcher_and_case_placeholdered_impl_reachable_prototypes {
                grouped.entry(*rune_in_impl).or_default().push((*rune_in_citizen, *prototype_templata.prototype));
            }
            self.typing_interner.alloc_index_map_from_iter(
                grouped.into_iter().map(|(rune_in_impl, inner_entries)| {
                    let inner_map: ArenaIndexMap<'t, IRuneS<'s>, PrototypeT<'s, 't>> = self.typing_interner.alloc_index_map_from_iter(inner_entries.into_iter());
                    (rune_in_impl, inner_map)
                })
            )
        };

        Ok(OverrideT {
            dispatcher_call_id: *dispatcher_id_ref,
            impl_placeholder_to_dispatcher_placeholder: impl_placeholder_to_dispatcher_placeholder_slice,
            impl_placeholder_to_case_placeholder: impl_independent_placeholder_to_case_placeholder_slice,
            dispatcher_and_case_placeholdered_impl_reachable_prototypes: reachable_map,
            case_id: *dispatcher_case_id_ref,
            override_prototype: *found_function.prototype,
            dispatcher_instantiation_bound_params,
        })
    }

}
