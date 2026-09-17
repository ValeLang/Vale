use crate::typing::compiler::Compiler;
use crate::typing::compiler_outputs::CompilerOutputs;
use crate::typing::names::names::*;
use crate::typing::types::types::*;
use crate::typing::templata::templata::*;
use crate::typing::ast::ast::*;
use crate::typing::env::environment::*;
use crate::typing::env::i_env_entry::IEnvEntryT;
use crate::typing::typing_interner::{MustIntern, TypingInterner};
use crate::keywords::Keywords;
use crate::typing::hinputs_t::{InstantiationBoundArgumentsT, InstantiationReachableBoundArgumentsT};
use crate::postparsing::names::{IRuneS, IImpreciseNameS};
use crate::postparsing::ast::{GenericParameterS, IRegionMutabilityS, LocationInDenizen};
use crate::postparsing::itemplatatype::ITemplataType;
use crate::postparsing::rules::rules::{EqualsSR, IRulexSR, RuneUsage};
use crate::typing::infer_compiler::include_rule_in_call_site_solve;
use crate::postparsing::rune_type_solver::IRuneTypeSolverEnv;
use crate::utils::range::RangeS;
use indexmap::IndexMap;
use std::collections::HashMap;
use crate::typing::types::types::{KindPlaceholderT, KindT};
use crate::typing::names::names::IInstantiationNameT;
use crate::typing::names::names::{ISuperKindNameT, ITemplateNameT};
use crate::typing::names::names::{INameValT, IdValT};
use crate::typing::names::names::StructNameValT;
use crate::typing::names::names::INameT;
use crate::typing::types::types::StructTTValT;
use crate::typing::names::names::FunctionBoundNameT;
use crate::typing::names::names::ImplBoundNameT;
use crate::typing::names::names::InterfaceNameValT;
use crate::typing::types::types::InterfaceTTValT;
use crate::typing::names::names::IPlaceholderNameT;
use crate::typing::names::names::IFunctionNameT;
use crate::typing::ast::ast::PrototypeValT;
use crate::typing::citizen::impl_compiler::IsParentResult;
use crate::postparsing::itemplatatype::KindTemplataType;
use crate::postparsing::itemplatatype::CoordTemplataType;
use crate::postparsing::ast::IGenericParameterTypeS;
use crate::postparsing::ast::CoordGenericParameterTypeS;
use crate::scout_arena::ScoutArena;
use crate::postparsing::rune_type_solver::IRuneTypeSolverLookupResult;
use crate::postparsing::rune_type_solver::IRuneTypingLookupFailedError;
use crate::postparsing::rune_type_solver::TemplataLookupResult;
use crate::typing::env::environment::ILookupContext;
use crate::typing::templata::templata::ITemplataT;
use crate::postparsing::rune_type_solver::CitizenRuneTypeSolverLookupResult;
use crate::postparsing::rune_type_solver::RuneTypingCouldntFindType;
use std::collections::HashSet;
use std::iter::empty;
use std::marker::PhantomData;

#[derive(Copy, Clone)]
pub enum IBoundArgumentsSource<'s, 't> {
    InheritBoundsFromTypeItself,
    UseBoundsFromContainer {
        instantiation_bound_params: &'t InstantiationBoundArgumentsT<'s, 't>,
        instantiation_bound_arguments: &'t InstantiationBoundArgumentsT<'s, 't>,
    },
}

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn get_top_level_denizen_id(
        &self,
        id: IdT<'s, 't>,
    ) -> IdT<'s, 't> {
        let steps = id.steps();
        let is_instantiation_name = |name: &INameT<'s, 't>| -> bool {
            IInstantiationNameT::try_from(*name).is_ok()
        };
        let index = steps.iter().position(is_instantiation_name);
        let index = index.expect("get_top_level_denizen_id: no IInstantiationNameT found in steps");
        let last_step = steps[index];
        assert!(is_instantiation_name(&last_step), "get_top_level_denizen_id: step at index is not IInstantiationNameT");
        let init_steps_slice = self.typing_interner.alloc_slice_copy(&steps[..index]);
        *self.typing_interner.intern_id(IdValT {
            package_coord: id.package_coord,
            init_steps: init_steps_slice,
            local_name: last_step,
        })
    }

    pub fn get_placeholder_templata_id(
        impl_placeholder: ITemplataT<'s, 't>,
    ) -> IdT<'s, 't> {
        match impl_placeholder {
            ITemplataT::Placeholder(pt) => pt.id,
            ITemplataT::Kind(kt) => match kt.kind {
                KindT::KindPlaceholder(kp) => kp.id,
                _ => panic!("vwat: get_placeholder_templata_id unexpected kind: {:?}", kt.kind),
            },
            ITemplataT::Coord(ct) => match ct.coord.kind {
                KindT::KindPlaceholder(kp) => kp.id,
                _ => panic!("vwat: get_placeholder_templata_id unexpected coord kind: {:?}", ct.coord.kind),
            },
            other => panic!("vwat: get_placeholder_templata_id unexpected templata: {:?}", other),
        }
    }

    // See SFWPRL. Per @DRSINI, this is the only place that eagerly adds default rules.
    // Safe because prediction has no actual arguments being inferred that could conflict.
    pub fn assemble_predict_rules(
        &self,
        generic_parameters: &'s [&'s GenericParameterS<'s>],
        num_explicit_template_args: i32,
    ) -> Vec<IRulexSR<'s>> {
        let mut result: Vec<IRulexSR<'s>> = Vec::new();
        for (index, generic_param) in generic_parameters.iter().enumerate() {
            if (index as i32) >= num_explicit_template_args {
                match &generic_param.default {
                    Some(x) => {
                        for rule in x.rules.iter() {
                            result.push(**rule);
                        }
                    }
                    None => {}
                }
            }
        }
        result
    }

    // Per @DRSINI, default rules are no longer added eagerly here. They're added
    // incrementally by solveForResolving and evaluateGenericFunctionFromCallForPrototype
    // only for runes that remain unsolved after argument inference.
    pub fn assemble_call_site_rules(
        &self,
        rules: &'s [IRulexSR<'s>],
    ) -> Vec<IRulexSR<'s>> {
        rules.iter().copied().filter(|r| include_rule_in_call_site_solve(r)).collect()
    }

    pub fn get_function_template(
        interner: &TypingInterner<'s, 't>,
        id: IdT<'s, 't>,
    ) -> IdT<'s, 't> {
        let func_name = IFunctionNameT::try_from(id.local_name)
            .unwrap_or_else(|_| panic!("get_function_template: not a function name: {:?}", id.local_name));
        let template_local: INameT<'s, 't> = ITemplateNameT::from(func_name.template()).into();
        *interner.intern_id(IdValT {
            package_coord: id.package_coord,
            init_steps: id.init_steps,
            local_name: template_local,
        })
    }

    pub fn get_citizen_template(
        &self,
        id: IdT<'s, 't>,
    ) -> IdT<'s, 't> {
        let local_name = match id.local_name {
            INameT::Struct(s) => {
                match s.template {
                    IStructTemplateNameT::StructTemplate(tmpl) => INameT::StructTemplate(tmpl),
                    IStructTemplateNameT::LambdaCitizenTemplate(tmpl) => INameT::LambdaCitizenTemplate(tmpl),
                    IStructTemplateNameT::AnonymousSubstructTemplate(tmpl) => INameT::AnonymousSubstructTemplate(tmpl),
                }
            }
            INameT::LambdaCitizen(lc) => INameT::LambdaCitizenTemplate(lc.template),
            INameT::Interface(i) => INameT::InterfaceTemplate(i.template),
            INameT::AnonymousSubstruct(a) => INameT::AnonymousSubstructTemplate(a.template),
            _ => panic!("get_citizen_template called with non-citizen name: {:?}", id.local_name),
        };
        *self.typing_interner.intern_id(IdValT {
            package_coord: id.package_coord,
            init_steps: id.init_steps,
            local_name,
        })
    }

    pub fn get_name_template(
        name: INameT<'s, 't>,
    ) -> INameT<'s, 't> {
        match IInstantiationNameT::try_from(name) {
            Ok(x) => INameT::from(x.template()),
            Err(_) => name,
        }
    }

    pub fn get_super_template(
        interner: &TypingInterner<'s, 't>,
        id: IdT<'s, 't>,
    ) -> IdT<'s, 't> {
        let new_init_steps: Vec<INameT<'s, 't>> =
            id.init_steps.iter().map(|n| Self::get_name_template(*n)).collect();
        let new_local_name = Self::get_name_template(id.local_name);
        *interner.intern_id(IdValT {
            package_coord: id.package_coord,
            init_steps: &new_init_steps,
            local_name: new_local_name,
        })
    }

    pub fn get_root_super_template(
        interner: &TypingInterner<'s, 't>,
        id: IdT<'s, 't>,
    ) -> IdT<'s, 't> {
        let mut tentative_id = Self::get_super_template(interner, id);
        loop {
            let contains_lambda = tentative_id.init_steps.iter().any(|n| {
                match n {
                    INameT::LambdaCitizenTemplate(_) => true,
                    INameT::LambdaCallFunctionTemplate(_) => true,
                    INameT::OverrideDispatcherCase(_) => true,
                    _ => false,
                }
            }) || match tentative_id.local_name {
                INameT::LambdaCitizenTemplate(_) => true,
                INameT::LambdaCallFunctionTemplate(_) => true,
                INameT::OverrideDispatcherCase(_) => true,
                _ => false,
            };
            if contains_lambda {
                tentative_id = tentative_id.init_id(interner);
            } else {
                return tentative_id;
            }
        }
    }

    pub fn get_template(
        interner: &TypingInterner<'s, 't>,
        id: IdT<'s, 't>,
    ) -> &'t IdT<'s, 't> {
        let last = IInstantiationNameT::try_from(id.local_name).unwrap();
        interner.intern_id(IdValT {
            package_coord: id.package_coord,
            init_steps: id.init_steps, //.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
            local_name: INameT::from(last.template()),
        })
    }

    pub fn get_sub_kind_template(
        &self,
        id: IdT<'s, 't>,
    ) -> IdT<'s, 't> {
        let last = IInstantiationNameT::try_from(id.local_name)
            .unwrap_or_else(|_| panic!("get_sub_kind_template: unexpected local_name {:?}", id.local_name));
        let template_name = INameT::from(last.template());
        *self.typing_interner.intern_id(IdValT {
            package_coord: id.package_coord,
            init_steps: id.init_steps,
            local_name: template_name,
        })
    }

    pub fn get_super_kind_template(
        &self,
        id: IdT<'s, 't>,
    ) -> IdT<'s, 't> {
        let last = ISuperKindNameT::try_from(id.local_name)
            .unwrap_or_else(|_| panic!("get_super_kind_template: unexpected local_name {:?}", id.local_name));
        let template_name = INameT::from(ITemplateNameT::from(last.template()));
        *self.typing_interner.intern_id(IdValT {
            package_coord: id.package_coord,
            init_steps: id.init_steps,
            local_name: template_name,
        })
    }

    pub fn get_struct_template(
        &self,
        id: IdT<'s, 't>,
    ) -> IdT<'s, 't> {
        let local_name = match id.local_name {
            INameT::Struct(s) => {
                match s.template {
                    IStructTemplateNameT::StructTemplate(tmpl) => INameT::StructTemplate(tmpl),
                    IStructTemplateNameT::LambdaCitizenTemplate(tmpl) => INameT::LambdaCitizenTemplate(tmpl),
                    IStructTemplateNameT::AnonymousSubstructTemplate(tmpl) => INameT::AnonymousSubstructTemplate(tmpl),
                }
            }
            INameT::LambdaCitizen(lc) => INameT::LambdaCitizenTemplate(lc.template),
            INameT::AnonymousSubstruct(a) => INameT::AnonymousSubstructTemplate(a.template),
            _ => panic!("get_struct_template called with non-struct name: {:?}", id.local_name),
        };
        *self.typing_interner.intern_id(IdValT {
            package_coord: id.package_coord,
            init_steps: id.init_steps,
            local_name,
        })
    }

    pub fn get_interface_template(
        &self,
        id: IdT<'s, 't>,
    ) -> IdT<'s, 't> {
        let local_name = match id.local_name {
            INameT::Interface(i) => INameT::InterfaceTemplate(i.template),
            _ => panic!("get_interface_template called with non-interface name"),
        };
        *self.typing_interner.intern_id(IdValT {
            package_coord: id.package_coord,
            init_steps: id.init_steps,
            local_name,
        })
    }

    pub fn get_export_template(
        &self,
        id: IdT<'s, 't>,
    ) -> IdT<'s, 't> {
        panic!("Unimplemented: Slab 10 — body migration");
    }

    pub fn get_extern_template(
        &self,
        id: IdT<'s, 't>,
    ) -> IdT<'s, 't> {
        panic!("Unimplemented: Slab 10 — body migration");
    }

    pub fn get_impl_template(
        interner: &TypingInterner<'s, 't>,
        id: IdT<'s, 't>,
    ) -> IdT<'s, 't> {
        let IdT { package_coord, init_steps, local_name, .. } = id;
        let impl_name = IImplNameT::try_from(local_name).expect("get_impl_template: not an impl name");
        let template = INameT::from(impl_name.template());
        *interner.intern_id(crate::typing::names::names::IdValT { package_coord, init_steps, local_name: template })
    }

    pub fn get_placeholder_template(
        &self,
        id: IdT<'s, 't>,
    ) -> IdT<'s, 't> {
        // val IdT(packageCoord, initSteps, last) = id
        // IdT(packageCoord, initSteps, last.template)
        let template_name = match id.local_name {
            INameT::KindPlaceholder(kp) => INameT::KindPlaceholderTemplate(kp.template),
            _ => panic!("get_placeholder_template: unexpected local_name"),
        };
        *self.typing_interner.intern_id(IdValT {
            package_coord: id.package_coord,
            init_steps: id.init_steps,
            local_name: template_name,
        })
    }

    pub fn assemble_rune_to_function_bound(
        &self,
        templatas: &'t TemplatasStoreT<'s, 't>,
    ) -> HashMap<IRuneS<'s>, &'t PrototypeT<'s, 't>> {
        let mut result = HashMap::new();
        for (name, entry) in templatas.name_to_entry.iter() {
            match (name, entry) {
                (INameT::Rune(rune_name), IEnvEntryT::Templata(ITemplataT::Prototype(proto_templata))) => {
                    match &proto_templata.prototype.id.local_name {
                        INameT::FunctionBound(_) => {
                            result.insert(rune_name.rune, proto_templata.prototype);
                        }
                        _ => {}
                    }
                }
                _ => {}
            }
        }
        result
    }

    pub fn assemble_rune_to_impl_bound(
        &self,
        templatas: &'t TemplatasStoreT<'s, 't>,
    ) -> HashMap<IRuneS<'s>, IdT<'s, 't>> {
        let mut result = HashMap::new();
        for (name, entry) in templatas.name_to_entry.iter() {
            match (name, entry) {
                (INameT::Rune(rune_name), IEnvEntryT::Templata(ITemplataT::Isa(isa))) => {
                    match isa.impl_name.local_name {
                        INameT::ImplBound(_) => {
                            result.insert(rune_name.rune, isa.impl_name);
                        }
                        _ => {}
                    }
                }
                _ => {}
            }
        }
        result
    }

    pub fn substitute_templatas_in_coord(
        coutputs: &mut CompilerOutputs<'s, 't>,
        sanity_check: bool,
        interner: &'ctx TypingInterner<'s, 't>,
        keywords: &'ctx Keywords<'s>,
        original_calling_denizen_id: IdT<'s, 't>,
        needle_template_name: IdT<'s, 't>,
        new_substituting_templatas: &[ITemplataT<'s, 't>],
        bound_arguments_source: IBoundArgumentsSource<'s, 't>,
        coord: CoordT<'s, 't>,
    ) -> CoordT<'s, 't> {
        let CoordT { ownership, region: original_region, kind } = coord;
        let result_region = original_region;
        match Compiler::substitute_templatas_in_kind(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, kind) {
            ITemplataT::Kind(k) => CoordT { ownership, region: result_region, kind: k.kind },
            ITemplataT::Coord(c) => {
                let result_ownership = match (ownership, c.coord.ownership) {
                    (OwnershipT::Share, _) => OwnershipT::Share,
                    (_, OwnershipT::Share) => OwnershipT::Share,
                    (OwnershipT::Own, OwnershipT::Own) => OwnershipT::Own,
                    (OwnershipT::Own, OwnershipT::Borrow) => OwnershipT::Borrow,
                    (OwnershipT::Borrow, OwnershipT::Own) => OwnershipT::Borrow,
                    (OwnershipT::Borrow, OwnershipT::Borrow) => OwnershipT::Borrow,
                    _ => unreachable!("Scala's substituteTemplatasInCoord covers the 5 substantive ownership pairs; remaining Weak-on-substituting-side combinations are degenerate"),
                };
                CoordT { ownership: result_ownership, region: result_region, kind: c.coord.kind }
            }
            _ => unreachable!("Scala's substituteTemplatasInCoord match is exhaustive over KindTemplataT/CoordTemplataT only"),
        }
    }

    pub fn substitute_templatas_in_kind(
        coutputs: &mut CompilerOutputs<'s, 't>,
        sanity_check: bool,
        interner: &'ctx TypingInterner<'s, 't>,
        keywords: &'ctx Keywords<'s>,
        original_calling_denizen_id: IdT<'s, 't>,
        needle_template_name: IdT<'s, 't>,
        new_substituting_templatas: &[ITemplataT<'s, 't>],
        bound_arguments_source: IBoundArgumentsSource<'s, 't>,
        kind: KindT<'s, 't>,
    ) -> ITemplataT<'s, 't> {
        match kind {
            KindT::Int(_) => ITemplataT::Kind(interner.alloc(KindTemplataT { kind })),
            KindT::Bool(_) => ITemplataT::Kind(interner.alloc(KindTemplataT { kind })),
            KindT::Str(_) => ITemplataT::Kind(interner.alloc(KindTemplataT { kind })),
            KindT::Float(_) => ITemplataT::Kind(interner.alloc(KindTemplataT { kind })),
            KindT::Void(_) => ITemplataT::Kind(interner.alloc(KindTemplataT { kind })),
            KindT::Never(_) => ITemplataT::Kind(interner.alloc(KindTemplataT { kind })),
            KindT::RuntimeSizedArray(rsa) => {
                let INameT::RuntimeSizedArray(rsa_name) = rsa.name.local_name else { panic!("vwat") };
                let new_arr_name = interner.intern_raw_array_name(RawArrayNameT {
                    mutability: expect_mutability(Self::substitute_templatas_in_templata(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, rsa_name.arr.mutability)),
                    element_type: Self::substitute_templatas_in_coord(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, rsa_name.arr.element_type),
                    self_region: RegionT { region: IRegionT::Default },
                });
                let new_rsa_name = interner.intern_runtime_sized_array_name(RuntimeSizedArrayNameT {
                    template: rsa_name.template,
                    arr: new_arr_name,
                });
                let new_id = *interner.intern_id(IdValT {
                    package_coord: rsa.name.package_coord,
                    init_steps: rsa.name.init_steps,
                    local_name: INameT::RuntimeSizedArray(new_rsa_name),
                });
                let new_rsa = interner.intern_runtime_sized_array_tt(RuntimeSizedArrayTTValT { name: new_id });
                ITemplataT::Kind(interner.alloc(KindTemplataT { kind: KindT::RuntimeSizedArray(new_rsa) }))
            }
            KindT::StaticSizedArray(ssa) => {
                let INameT::StaticSizedArray(ssa_name) = ssa.name.local_name else { panic!("vwat") };
                let new_arr_name = interner.intern_raw_array_name(RawArrayNameT {
                    mutability: expect_mutability(Self::substitute_templatas_in_templata(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, ssa_name.arr.mutability)),
                    element_type: Self::substitute_templatas_in_coord(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, ssa_name.arr.element_type),
                    self_region: RegionT { region: IRegionT::Default },
                });
                let new_ssa_name = interner.intern_static_sized_array_name(StaticSizedArrayNameT {
                    template: ssa_name.template,
                    size: expect_integer(Self::substitute_templatas_in_templata(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, ssa_name.size)),
                    variability: expect_variability(Self::substitute_templatas_in_templata(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, ssa_name.variability)),
                    arr: new_arr_name,
                });
                let new_id = *interner.intern_id(IdValT {
                    package_coord: ssa.name.package_coord,
                    init_steps: ssa.name.init_steps,
                    local_name: INameT::StaticSizedArray(new_ssa_name),
                });
                let new_ssa = interner.intern_static_sized_array_tt(StaticSizedArrayTTValT { name: new_id });
                ITemplataT::Kind(interner.alloc(KindTemplataT { kind: KindT::StaticSizedArray(new_ssa) }))
            }
            KindT::KindPlaceholder(p) => {
                let index = match p.id.local_name {
                    INameT::KindPlaceholder(kp) => kp.template.index,
                    _ => panic!("KindPlaceholderT has non-KindPlaceholder local_name"),
                };
                if p.id.init_id(interner) == needle_template_name {
                    new_substituting_templatas[index as usize]
                } else {
                    ITemplataT::Kind(interner.alloc(KindTemplataT { kind }))
                }
            }
            KindT::Struct(s) => {
                let new_struct = Compiler::substitute_templatas_in_struct(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, s);
                ITemplataT::Kind(interner.alloc(KindTemplataT { kind: KindT::Struct(new_struct) }))
            }
            KindT::Interface(i) => {
                let new_interface = Compiler::substitute_templatas_in_interface(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, i);
                ITemplataT::Kind(interner.alloc(KindTemplataT { kind: KindT::Interface(new_interface) }))
            }
            KindT::OverloadSet(_) => unreachable!("Scala's substituteTemplatasInKind has no OverloadSet arm; an OverloadSet cannot appear as a substantive kind here"),
        }
    }

    pub fn substitute_templatas_in_struct(
        coutputs: &mut CompilerOutputs<'s, 't>,
        sanity_check: bool,
        interner: &'ctx TypingInterner<'s, 't>,
        keywords: &'ctx Keywords<'s>,
        original_calling_denizen_id: IdT<'s, 't>,
        needle_template_name: IdT<'s, 't>,
        new_substituting_templatas: &[ITemplataT<'s, 't>],
        bound_arguments_source: IBoundArgumentsSource<'s, 't>,
        struct_tt: &'t StructTT<'s, 't>,
    ) -> &'t StructTT<'s, 't> {
        let id = struct_tt.id;
        let new_local_name = match id.local_name {
            INameT::AnonymousSubstruct(asub_name_t) => {
                let new_template_args: Vec<ITemplataT<'s, 't>> = asub_name_t.template_args.iter()
                    .map(|templata| Self::substitute_templatas_in_templata(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, *templata))
                    .collect();
                let new_template_args_ref = interner.alloc_slice_from_vec(new_template_args);
                interner.intern_name(INameValT::AnonymousSubstruct(AnonymousSubstructNameValT {
                    template: asub_name_t.template,
                    template_args: new_template_args_ref,
                }))
            }
            INameT::Struct(struct_name_t) => {
                let new_template_args: Vec<ITemplataT<'s, 't>> = struct_name_t.template_args.iter()
                    .map(|templata| Self::substitute_templatas_in_templata(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, *templata))
                    .collect();
                let new_template_args_ref = interner.alloc_slice_from_vec(new_template_args);
                interner.intern_name(INameValT::Struct(StructNameValT {
                    template: struct_name_t.template,
                    template_args: new_template_args_ref,
                }))
            }
            INameT::LambdaCitizen(lambda_citizen_name_t) => {
                INameT::LambdaCitizen(lambda_citizen_name_t)
            }
            _ => unreachable!("Scala's substituteTemplatasInStruct is exhaustive over AnonymousSubstructNameT/StructNameT/LambdaCitizenNameT"),
        };
        let new_id = interner.intern_id(IdValT {
            package_coord: id.package_coord,
            init_steps: id.init_steps,
            local_name: new_local_name,
        });
        let new_struct = interner.intern_struct_tt(StructTTValT { id: *new_id });
        // See SBITAFD, we need to register bounds for these new instantiations.
        let instantiation_bound_args = coutputs.get_instantiation_bounds(interner, struct_tt.id).unwrap();
        let translated_bounds = interner.alloc(Self::translate_instantiation_bounds(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, instantiation_bound_args));
        coutputs.add_instantiation_bounds(
            sanity_check, interner,
            original_calling_denizen_id,
            new_struct.id,
            translated_bounds);
        new_struct
    }

    pub fn translate_instantiation_bounds(
        coutputs: &mut CompilerOutputs<'s, 't>,
        sanity_check: bool,
        interner: &'ctx TypingInterner<'s, 't>,
        keywords: &'ctx Keywords<'s>,
        original_calling_denizen_id: IdT<'s, 't>,
        needle_template_name: IdT<'s, 't>,
        new_substituting_templatas: &[ITemplataT<'s, 't>],
        bound_arguments_source: IBoundArgumentsSource<'s, 't>,
        instantiation_bound_args: &'t InstantiationBoundArgumentsT<'s, 't>,
    ) -> InstantiationBoundArgumentsT<'s, 't> {
        match bound_arguments_source {
            IBoundArgumentsSource::InheritBoundsFromTypeItself => {
                let x = Self::substitute_templatas_in_bounds(
                    coutputs, sanity_check, interner, keywords,
                    original_calling_denizen_id, needle_template_name,
                    new_substituting_templatas, bound_arguments_source,
                    instantiation_bound_args);
                x
            }
            IBoundArgumentsSource::UseBoundsFromContainer { instantiation_bound_params: container_instantiation_bound_params, instantiation_bound_arguments: container_instantiation_bound_args } => {
                let container_func_bound_to_bound_arg: HashMap<PrototypeT<'s, 't>, PrototypeT<'s, 't>> =
                    container_instantiation_bound_args.rune_to_bound_prototype.iter()
                        .map(|(rune, container_func_bound_arg)| {
                            let param_proto = *container_instantiation_bound_params.rune_to_bound_prototype.get(rune).unwrap();
                            (param_proto, *container_func_bound_arg)
                        })
                        .collect();
                let container_impl_bound_to_bound_arg: HashMap<IdT<'s, 't>, IdT<'s, 't>> =
                    container_instantiation_bound_args.rune_to_bound_impl.iter()
                        .map(|(rune, container_impl_bound_arg)| {
                            let param_impl = *container_instantiation_bound_params.rune_to_bound_impl.get(rune).unwrap();
                            (param_impl, *container_impl_bound_arg)
                        })
                        .collect();
                let rune_to_bound_prototype = interner.alloc_index_map_from_iter(
                    instantiation_bound_args.rune_to_bound_prototype.iter().map(|(rune, func_bound_arg)| {
                        let new_val = match func_bound_arg.id.local_name {
                            INameT::FunctionBound(_) => {
                                *container_func_bound_to_bound_arg.get(func_bound_arg).unwrap()
                            }
                            _ => {
                                // Not sure if this call is really necessary...
                                *Self::substitute_templatas_in_prototype(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, func_bound_arg)
                            }
                        };
                        (*rune, new_val)
                    }));
                let rune_to_citizen_rune_to_reachable_prototype = interner.alloc_index_map_from_iter(
                    instantiation_bound_args.rune_to_citizen_rune_to_reachable_prototype.iter().map(|(callee_rune, reachable_bound_args)| {
                        let new_citizen = interner.alloc_index_map_from_iter(
                            reachable_bound_args.citizen_rune_to_reachable_prototype.iter().map(|(citizen_rune, reachable_prototype)| {
                                let new_val = match reachable_prototype.id.local_name {
                                    INameT::FunctionBound(_) => {
                                        *container_func_bound_to_bound_arg.get(reachable_prototype).unwrap()
                                    }
                                    _ => {
                                        // Not sure if this call is really necessary...
                                        *Self::substitute_templatas_in_prototype(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, reachable_prototype)
                                    }
                                };
                                (*citizen_rune, new_val)
                            }));
                        let new_reachable: &'t InstantiationReachableBoundArgumentsT<'s, 't> = interner.alloc(InstantiationReachableBoundArgumentsT { citizen_rune_to_reachable_prototype: new_citizen });
                        (*callee_rune, new_reachable)
                    }));
                let rune_to_bound_impl = interner.alloc_index_map_from_iter(
                    instantiation_bound_args.rune_to_bound_impl.iter().map(|(rune, impl_bound_arg)| {
                        let new_val = match impl_bound_arg.local_name {
                            INameT::ImplBound(_) => {
                                *container_impl_bound_to_bound_arg.get(impl_bound_arg).unwrap()
                            }
                            _ => {
                                // Not sure if this call is really necessary...
                                Self::substitute_templatas_in_impl_id(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, *impl_bound_arg)
                            }
                        };
                        (*rune, new_val)
                    }));
                InstantiationBoundArgumentsT {
                    rune_to_bound_prototype,
                    rune_to_citizen_rune_to_reachable_prototype,
                    rune_to_bound_impl,
                }
            }
        }
    }

    pub fn substitute_templatas_in_impl_id(
        coutputs: &mut CompilerOutputs<'s, 't>,
        sanity_check: bool,
        interner: &'ctx TypingInterner<'s, 't>,
        keywords: &'ctx Keywords<'s>,
        original_calling_denizen_id: IdT<'s, 't>,
        needle_template_name: IdT<'s, 't>,
        new_substituting_templatas: &[ITemplataT<'s, 't>],
        bound_arguments_source: IBoundArgumentsSource<'s, 't>,
        impl_id: IdT<'s, 't>,
    ) -> IdT<'s, 't> {
        panic!("Unimplemented: Slab 10 — body migration");
    }

    pub fn substitute_templatas_in_bounds(
        coutputs: &mut CompilerOutputs<'s, 't>,
        sanity_check: bool,
        interner: &'ctx TypingInterner<'s, 't>,
        keywords: &'ctx Keywords<'s>,
        original_calling_denizen_id: IdT<'s, 't>,
        needle_template_name: IdT<'s, 't>,
        new_substituting_templatas: &[ITemplataT<'s, 't>],
        bound_arguments_source: IBoundArgumentsSource<'s, 't>,
        bound_args: &'t InstantiationBoundArgumentsT<'s, 't>,
    ) -> InstantiationBoundArgumentsT<'s, 't> {
        let rune_to_bound_prototype = interner.alloc_index_map_from_iter(
            bound_args.rune_to_bound_prototype.iter().map(|(rune, func_bound_arg)| {
                (*rune, *Self::substitute_templatas_in_prototype(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, func_bound_arg))
            }));
        let rune_to_citizen_rune_to_reachable_prototype = interner.alloc_index_map_from_iter(
            bound_args.rune_to_citizen_rune_to_reachable_prototype.iter().map(|(caller_rune, reachable_bound_args)| {
                let new_citizen_rune_to_reachable_prototype = interner.alloc_index_map_from_iter(
                    reachable_bound_args.citizen_rune_to_reachable_prototype.iter().map(|(citizen_rune, reachable_prototype)| {
                        (*citizen_rune, *Self::substitute_templatas_in_prototype(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, reachable_prototype))
                    }));
                let new_reachable: &'t InstantiationReachableBoundArgumentsT<'s, 't> = interner.alloc(InstantiationReachableBoundArgumentsT { citizen_rune_to_reachable_prototype: new_citizen_rune_to_reachable_prototype });
                (*caller_rune, new_reachable)
            }));
        let rune_to_bound_impl = interner.alloc_index_map_from_iter(
            bound_args.rune_to_bound_impl.iter().map(|(rune, impl_bound_arg)| {
                (*rune, Self::substitute_templatas_in_impl_id(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, *impl_bound_arg))
            }));
        InstantiationBoundArgumentsT {
            rune_to_bound_prototype,
            rune_to_citizen_rune_to_reachable_prototype,
            rune_to_bound_impl,
        }
    }

    pub fn substitute_templatas_in_interface(
        coutputs: &mut CompilerOutputs<'s, 't>,
        sanity_check: bool,
        interner: &'ctx TypingInterner<'s, 't>,
        keywords: &'ctx Keywords<'s>,
        original_calling_denizen_id: IdT<'s, 't>,
        needle_template_name: IdT<'s, 't>,
        new_substituting_templatas: &[ITemplataT<'s, 't>],
        bound_arguments_source: IBoundArgumentsSource<'s, 't>,
        interface_tt: &'t InterfaceTT<'s, 't>,
    ) -> &'t InterfaceTT<'s, 't> {
        let id = interface_tt.id;
        let new_local_name = match id.local_name {
            INameT::Interface(interface_name_t) => {
                let new_template_args: Vec<ITemplataT<'s, 't>> = interface_name_t.template_args.iter()
                    .map(|templata| Self::substitute_templatas_in_templata(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, *templata))
                    .collect();
                let new_template_args_ref = interner.alloc_slice_from_vec(new_template_args);
                interner.intern_name(INameValT::Interface(InterfaceNameValT {
                    template: interface_name_t.template,
                    template_args: new_template_args_ref,
                }))
            }
            _ => unreachable!("Scala's substituteTemplatasInInterface match is exhaustive over InterfaceNameT only"),
        };
        let new_id = interner.intern_id(IdValT {
            package_coord: id.package_coord,
            init_steps: id.init_steps,
            local_name: new_local_name,
        });
        let new_interface = interner.intern_interface_tt(InterfaceTTValT { id: *new_id });
        // See SBITAFD, we need to register bounds for these new instantiations.
        let instantiation_bound_args = coutputs.get_instantiation_bounds(interner, interface_tt.id).unwrap();
        let translated_bounds = interner.alloc(Self::translate_instantiation_bounds(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, instantiation_bound_args));
        coutputs.add_instantiation_bounds(
            sanity_check, interner,
            original_calling_denizen_id,
            new_interface.id,
            translated_bounds);
        new_interface
    }

    pub fn substitute_templatas_in_templata(
        coutputs: &mut CompilerOutputs<'s, 't>,
        sanity_check: bool,
        interner: &'ctx TypingInterner<'s, 't>,
        keywords: &'ctx Keywords<'s>,
        original_calling_denizen_id: IdT<'s, 't>,
        needle_template_name: IdT<'s, 't>,
        new_substituting_templatas: &[ITemplataT<'s, 't>],
        bound_arguments_source: IBoundArgumentsSource<'s, 't>,
        templata: ITemplataT<'s, 't>,
    ) -> ITemplataT<'s, 't> {
        match templata {
            ITemplataT::Coord(c) => ITemplataT::Coord(interner.alloc(CoordTemplataT { coord: Compiler::substitute_templatas_in_coord(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, c.coord) })),
            ITemplataT::Kind(k) => Compiler::substitute_templatas_in_kind(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, k.kind),
            ITemplataT::Placeholder(p) => {
                let pn = IPlaceholderNameT::try_from(p.id.local_name).unwrap();
                if p.id.init_id(interner) == needle_template_name {
                    new_substituting_templatas[pn.index() as usize]
                } else {
                    templata
                }
            }
            ITemplataT::Mutability(_) => templata,
            ITemplataT::Variability(_) => templata,
            ITemplataT::Integer(_) => templata,
            ITemplataT::Boolean(_) => templata,
            ITemplataT::Prototype(p) => {
                panic!("Unimplemented: substitute_templatas_in_templata Prototype");
            }
            _ => panic!("vimpl: substitute_templatas_in_templata unexpected templata"),
        }
    }

    pub fn substitute_templatas_in_prototype(
        coutputs: &mut CompilerOutputs<'s, 't>,
        sanity_check: bool,
        interner: &'ctx TypingInterner<'s, 't>,
        keywords: &'ctx Keywords<'s>,
        original_calling_denizen_id: IdT<'s, 't>,
        needle_template_name: IdT<'s, 't>,
        new_substituting_templatas: &[ITemplataT<'s, 't>],
        bound_arguments_source: IBoundArgumentsSource<'s, 't>,
        original_prototype: &'t PrototypeT<'s, 't>,
    ) -> &'t PrototypeT<'s, 't> {
        let package_coord = original_prototype.id.package_coord;
        let init_steps = original_prototype.id.init_steps;
        let func_name = IFunctionNameT::try_from(original_prototype.id.local_name).unwrap();
        let substituted_template_args_vec: Vec<ITemplataT<'s, 't>> = func_name.template_args().iter().map(|templata| {
            Self::substitute_templatas_in_templata(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, *templata)
        }).collect();
        let substituted_template_args = interner.alloc_slice_from_vec(substituted_template_args_vec);
        let substituted_params_vec: Vec<CoordT<'s, 't>> = func_name.parameters().iter().map(|coord| {
            Self::substitute_templatas_in_coord(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, *coord)
        }).collect();
        let substituted_params = interner.alloc_slice_from_vec(substituted_params_vec);
        let substituted_return_type = Self::substitute_templatas_in_coord(coutputs, sanity_check, interner, keywords, original_calling_denizen_id, needle_template_name, new_substituting_templatas, bound_arguments_source, original_prototype.return_type);
        let substituted_func_name = func_name.template().make_function_name(interner, keywords, substituted_template_args, substituted_params);
        let tentative_id = *interner.intern_id(IdValT { package_coord, init_steps, local_name: substituted_func_name });
        let perhaps_imported_id = match tentative_id.local_name {
            INameT::FunctionBound(n) => {
                // Always import a seen function bound into our own environment, see MFBFDP.
                let imported_id = *original_calling_denizen_id.add_step(interner, INameT::FunctionBound(n));
                // It's a function bound, it has no function bounds of its own.
                coutputs.add_instantiation_bounds(
                    sanity_check,
                    interner,
                    original_calling_denizen_id,
                    imported_id,
                    interner.alloc(InstantiationBoundArgumentsT {
                        rune_to_bound_prototype: interner.alloc_index_map_from_iter(empty()),
                        rune_to_citizen_rune_to_reachable_prototype: interner.alloc_index_map_from_iter(empty()),
                        rune_to_bound_impl: interner.alloc_index_map_from_iter(empty()),
                    }),
                );
                imported_id
            }
            _ => {
                // Not really sure if we're supposed to add bounds or something here.
                assert!(coutputs.get_instantiation_bounds(interner, tentative_id).is_some());
                tentative_id
            }
        };
        interner.intern_prototype(PrototypeValT {
            id: IdValT { package_coord: perhaps_imported_id.package_coord, init_steps: perhaps_imported_id.init_steps, local_name: perhaps_imported_id.local_name },
            return_type: substituted_return_type,
        })
    }

    pub fn substitute_templatas_in_function_bound_id(
        coutputs: &mut CompilerOutputs<'s, 't>,
        sanity_check: bool,
        interner: &'ctx TypingInterner<'s, 't>,
        keywords: &'ctx Keywords<'s>,
        original_calling_denizen_id: IdT<'s, 't>,
        needle_template_name: IdT<'s, 't>,
        new_substituting_templatas: &[ITemplataT<'s, 't>],
        bound_arguments_source: IBoundArgumentsSource<'s, 't>,
        original: IdT<'s, 't>,
    ) -> IdT<'s, 't> {
        panic!("Unimplemented: Slab 10 — body migration");
    }
}

// deleted: delegate trait removed per god-struct refactor (Compiler now holds all methods directly)

// IPlaceholderSubstituter: Scala source is a trait defined inside TemplataCompiler.getPlaceholderSubstituter,
// so it has no separate top-level case-class anchor in TemplataCompiler.scala. Defined here as a struct per
// Slab 14 Gotcha 9 (single-implementor trait → struct with inherent methods). The seven fields below mirror
// Scala's anonymous-trait-impl closure captures at TemplataCompiler.scala:808-824 (sanityCheck, interner,
// keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource).
pub struct IPlaceholderSubstituter<'s, 'ctx, 't> {
    pub sanity_check: bool,
    pub interner: &'ctx TypingInterner<'s, 't>,
    pub keywords: &'ctx Keywords<'s>,
    pub original_calling_denizen_id: IdT<'s, 't>,
    pub needle_template_name: IdT<'s, 't>,
    pub new_substituting_templatas: &'t [ITemplataT<'s, 't>],
    pub bound_arguments_source: IBoundArgumentsSource<'s, 't>,
}
// Per TL.md "Guardian Annotations For New Definitions Without Scala Counterparts" and the
// LetExprRuneTypeSolverEnv / OverloadRuneTypeSolverEnv precedent (Slab 15f): the methods below realize
// Scala's anonymous `new IPlaceholderSubstituter { override def ... }` block at TemplataCompiler.scala:808-824.
// The Scala bodies live inside getPlaceholderSubstituter (later in the file) so direct adjacency isn't possible
// here — Guardian shields disabled on the impl methods.
impl<'s, 'ctx, 't> IPlaceholderSubstituter<'s, 'ctx, 't> {
    
    pub fn substitute_for_coord(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        coord_t: CoordT<'s, 't>,
    ) -> CoordT<'s, 't> {
        Compiler::substitute_templatas_in_coord(
            coutputs,
            self.sanity_check,
            self.interner,
            self.keywords,
            self.original_calling_denizen_id,
            self.needle_template_name,
            self.new_substituting_templatas,
            self.bound_arguments_source,
            coord_t,
        )
    }
    
    pub fn substitute_for_interface(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        interface_tt: InterfaceTT<'s, 't>,
    ) -> InterfaceTT<'s, 't> {
        panic!("Unimplemented: Slab 15 — body migration");
    }
    
    pub fn substitute_for_templata(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        templata: ITemplataT<'s, 't>,
    ) -> ITemplataT<'s, 't> {
        Compiler::substitute_templatas_in_templata(
            coutputs,
            self.sanity_check,
            self.interner,
            self.keywords,
            self.original_calling_denizen_id,
            self.needle_template_name,
            self.new_substituting_templatas,
            self.bound_arguments_source,
            templata,
        )
    }
    
    pub fn substitute_for_prototype(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        proto: &'t PrototypeT<'s, 't>,
    ) -> &'t PrototypeT<'s, 't> {
        Compiler::substitute_templatas_in_prototype(
            coutputs,
            self.sanity_check,
            self.interner,
            self.keywords,
            self.original_calling_denizen_id,
            self.needle_template_name,
            self.new_substituting_templatas,
            self.bound_arguments_source,
            proto,
        )
    }
    
    pub fn substitute_for_impl_id(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        impl_id: IdT<'s, 't>,
    ) -> IdT<'s, 't> {
        panic!("Unimplemented: Slab 15 — body migration");
    }
}

// deleted: delegate trait removed per god-struct refactor (Compiler now holds all methods directly)

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn get_placeholder_substituter(
        &self,
        sanity_check: bool,
        original_calling_denizen_id: IdT<'s, 't>,
        name: IdT<'s, 't>,
        bound_arguments_source: IBoundArgumentsSource<'s, 't>,
    ) -> IPlaceholderSubstituter<'s, 'ctx, 't> {
        let top_level_denizen_id = self.get_top_level_denizen_id(name);
        let top_level_local_name: IInstantiationNameT<'s, 't> =
            top_level_denizen_id.local_name.try_into()
                .unwrap_or_else(|_| panic!("get_placeholder_substituter: topLevelDenizenId.localName must be IInstantiationNameT, got {:?}", top_level_denizen_id.local_name));
        let template_args: &[ITemplataT<'s, 't>] = top_level_local_name.template_args();
        let top_level_denizen_template_id = Compiler::get_template(self.typing_interner, top_level_denizen_id);
        self.get_placeholder_substituter_ext(
            sanity_check,
            original_calling_denizen_id,
            *top_level_denizen_template_id,
            template_args,
            bound_arguments_source,
        )
    }

    pub fn get_placeholder_substituter_ext(
        &self,
        sanity_check: bool,
        original_calling_denizen_id: IdT<'s, 't>,
        needle_template_name: IdT<'s, 't>,
        new_substituting_templatas: &'t [ITemplataT<'s, 't>],
        bound_arguments_source: IBoundArgumentsSource<'s, 't>,
    ) -> IPlaceholderSubstituter<'s, 'ctx, 't> {
        IPlaceholderSubstituter {
            sanity_check,
            interner: self.typing_interner,
            keywords: self.keywords,
            original_calling_denizen_id,
            needle_template_name,
            new_substituting_templatas,
            bound_arguments_source,
        }
    }

    pub fn get_reachable_bounds(
        &self,
        sanity_check: bool,
        original_calling_denizen_id: IdT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        citizen: ICitizenTT<'s, 't>,
    ) -> InstantiationReachableBoundArgumentsT<'s, 't> {
        let citizen_id = match citizen {
            ICitizenTT::Struct(s) => s.id,
            ICitizenTT::Interface(i) => i.id,
        };
        let substituter =
            self.get_placeholder_substituter(
                sanity_check,
                original_calling_denizen_id,
                citizen_id,
                IBoundArgumentsSource::InheritBoundsFromTypeItself,
            );
        let citizen_template_id = self.get_citizen_template(citizen_id);
        let inner_env = coutputs.get_inner_env_for_type(citizen_template_id);
        let citizen_rune_to_reachable_prototype: Vec<(IRuneS<'s>, PrototypeT<'s, 't>)> =
            inner_env.templatas().name_to_entry.iter()
                .filter_map(|(name, entry)| {
                    match (name, entry) {
                        (INameT::Rune(rune_name), IEnvEntryT::Templata(ITemplataT::Prototype(proto_tt))) => {
                            match proto_tt.prototype.id.local_name {
                                INameT::FunctionBound(_) => {
                                    let substituted = substituter.substitute_for_prototype(coutputs, proto_tt.prototype);
                                    Some((rune_name.rune, *substituted))
                                }
                                _ => None,
                            }
                        }
                        _ => None,
                    }
                })
                .collect();
        InstantiationReachableBoundArgumentsT {
            citizen_rune_to_reachable_prototype: self.typing_interner.alloc_index_map_from_iter(
                citizen_rune_to_reachable_prototype.into_iter()),
        }
    }

    pub fn get_first_unsolved_identifying_rune(
        &self,
        generic_parameters: &'s [&'s GenericParameterS<'s>],
        is_solved: impl Fn(IRuneS<'s>) -> bool,
    ) -> Option<(&'s GenericParameterS<'s>, i32)> {
        generic_parameters.iter().enumerate()
            .map(|(index, generic_param)| (generic_param, index as i32, is_solved(generic_param.rune.rune)))
            .filter(|(_, _, solved)| !solved)
            .map(|(generic_param, index, _)| (*generic_param, index))
            .next()
    }

    pub fn create_rune_type_solver_env(
        &self,
        parent_env: IInDenizenEnvironmentT<'s, 't>,
    ) -> TemplataCompilerRuneTypeSolverEnv<'_, 's, 't> {
        TemplataCompilerRuneTypeSolverEnv {
            parent_env,
            typing_interner: self.typing_interner,
            scout_arena: self.scout_arena,
        }
    }
    
}

// Concrete IRuneTypeSolverEnv produced by `create_rune_type_solver_env` above. The
// Scala anonymous `new IRuneTypeSolverEnv` at TemplataCompiler.scala:1513 closes over
// `parentEnv` and dispatches to either a LambdaStructImpreciseNameS special case or
// `parentEnv.lookupNearestWithImpreciseName`. Same shape pattern as
// `HigherTypingRuneTypeSolverEnv` (higher_typing_pass.rs) and `LetExprRuneTypeSolverEnv`
// (expression_compiler.rs).
pub struct TemplataCompilerRuneTypeSolverEnv<'a, 's, 't>
where
    's: 't,
{
    parent_env: IInDenizenEnvironmentT<'s, 't>,
    typing_interner: &'a TypingInterner<'s, 't>,
    scout_arena: &'a ScoutArena<'s>,
}

impl<'a, 's, 't> IRuneTypeSolverEnv<'s>
for TemplataCompilerRuneTypeSolverEnv<'a, 's, 't>
where
    's: 't,
{
    fn lookup(
        &self,
        range: RangeS<'s>,
        name_s: IImpreciseNameS<'s>,
    ) -> Result<
        IRuneTypeSolverLookupResult<'s>,
        IRuneTypingLookupFailedError<'s>,
    > {
        match name_s {
            IImpreciseNameS::LambdaStructImpreciseName(_) => {
                Ok(IRuneTypeSolverLookupResult::Templata(
                    TemplataLookupResult {
                        templata: ITemplataType::KindTemplataType(
                            KindTemplataType {},
                        ),
                    },
                ))
            }
            _ => {
                let mut filter = HashSet::new();
                filter.insert(ILookupContext::TemplataLookupContext);
                match self.parent_env.lookup_nearest_with_imprecise_name(name_s, filter, self.typing_interner) {
                    Some(ITemplataT::StructDefinition(t)) => {
                        Ok(IRuneTypeSolverLookupResult::Citizen(
                            CitizenRuneTypeSolverLookupResult {
                                tyype: ITemplataType::TemplateTemplataType(
                                    t.origin_struct.tyype,
                                ),
                                generic_params: t.origin_struct.generic_parameters,
                            },
                        ))
                    }
                    Some(ITemplataT::InterfaceDefinition(t)) => {
                        Ok(IRuneTypeSolverLookupResult::Citizen(
                            CitizenRuneTypeSolverLookupResult {
                                tyype: ITemplataType::TemplateTemplataType(
                                    t.origin_interface.tyype,
                                ),
                                generic_params: t.origin_interface.generic_parameters,
                            },
                        ))
                    }
                    Some(x) => {
                        Ok(IRuneTypeSolverLookupResult::Templata(
                            TemplataLookupResult {
                                templata: x.tyype(self.scout_arena),
                            },
                        ))
                    }
                    None => Err(
                        IRuneTypingLookupFailedError::CouldntFindType(
                            RuneTypingCouldntFindType {
                                range,
                                name: name_s,
                            },
                        ),
                    ),
                }
            }
        }
    }

}

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn is_type_convertible(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        source_pointer_type: CoordT<'s, 't>,
        target_pointer_type: CoordT<'s, 't>,
    ) -> bool {
        let CoordT { ownership: target_ownership, region: target_region, kind: target_type } = target_pointer_type;
        let CoordT { ownership: source_ownership, region: source_region, kind: source_type } = source_pointer_type;

        match (&source_type, &target_type) {
            (KindT::Never(_), _) => return true,
            (a, b) if a == b => {}
            (KindT::Void(_) | KindT::Int(_) | KindT::Bool(_) | KindT::Str(_) | KindT::Float(_)
                | KindT::RuntimeSizedArray(_) | KindT::StaticSizedArray(_), _) => {
                return false;
            }
            (_, KindT::Void(_) | KindT::Int(_) | KindT::Bool(_) | KindT::Str(_) | KindT::Float(_)
                | KindT::RuntimeSizedArray(_) | KindT::StaticSizedArray(_)) => {
                return false;
            }
            (_, KindT::Struct(_)) => return false,
            (a, b) if ISubKindTT::try_from(*a).is_ok() && ISuperKindTT::try_from(*b).is_ok() => {
                let source_sub_kind = ISubKindTT::try_from(source_type).unwrap();
                let target_super_kind = ISuperKindTT::try_from(target_type).unwrap();
                match self.is_parent(coutputs, calling_env, parent_ranges, call_location, source_sub_kind, target_super_kind) {
                    IsParentResult::IsParent(_) => {}
                    IsParentResult::IsntParent(_) => return false,
                }
            }
            _ => {
                panic!("vfail: Dont know if we can convert from {:?} to {:?}", source_type, target_type);
            }
        }

        if source_region != target_region {
            return false;
        }

        match (source_ownership, target_ownership) {
            (a, b) if a == b => {}
            // At some point maybe we should automatically convert borrow to pointer and vice versa
            // and perhaps automatically promote borrow or pointer to weak?
            (OwnershipT::Own, OwnershipT::Borrow) => return false,
            (OwnershipT::Own, OwnershipT::Weak) => return false,
            (OwnershipT::Own, OwnershipT::Share) => return false,
            (OwnershipT::Borrow, OwnershipT::Own) => return false,
            (OwnershipT::Borrow, OwnershipT::Weak) => return false,
            (OwnershipT::Borrow, OwnershipT::Share) => return false,
            (OwnershipT::Weak, OwnershipT::Own) => return false,
            (OwnershipT::Weak, OwnershipT::Borrow) => return false,
            (OwnershipT::Weak, OwnershipT::Share) => return false,
            (OwnershipT::Share, OwnershipT::Borrow) => return false,
            (OwnershipT::Share, OwnershipT::Weak) => return false,
            (OwnershipT::Share, OwnershipT::Own) => return false,
            _ => unreachable!(),
        }

        true
    }

    pub fn pointify_kind(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        kind: KindT<'s, 't>,
        region: RegionT,
        ownership_if_mutable: OwnershipT,
    ) -> CoordT<'s, 't> {
        let mutability = self.get_mutability(coutputs, kind);
        let ownership =
            match mutability {
                ITemplataT::Placeholder(_) => { panic!("Unimplemented: pointify_kind PlaceholderTemplataT"); }
                ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) => ownership_if_mutable,
                ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => OwnershipT::Share,
                _ => unreachable!("Scala's pointify_kind mutability match is exhaustive over Mutable/Immutable/Placeholder"),
            };
        match kind {
            KindT::RuntimeSizedArray(_) => { panic!("Unimplemented: pointify_kind RuntimeSizedArray"); }
            KindT::StaticSizedArray(_) => { panic!("Unimplemented: pointify_kind StaticSizedArray"); }
            KindT::Struct(_) => CoordT { ownership, region, kind },
            KindT::Interface(_) => CoordT { ownership, region, kind },
            KindT::Void(_) => CoordT { ownership: OwnershipT::Share, region, kind },
            KindT::Int(_) => CoordT { ownership: OwnershipT::Share, region, kind },
            KindT::Float(_) => CoordT { ownership: OwnershipT::Share, region, kind },
            KindT::Bool(_) => CoordT { ownership: OwnershipT::Share, region, kind },
            KindT::Str(_) => CoordT { ownership: OwnershipT::Share, region, kind },
            _ => unreachable!("Scala's pointify_kind is exhaustive over RSA/SSA/Struct/Interface/Void/Int/Float/Bool/Str — Never/OverloadSet/KindPlaceholder not in Scala"),
        }
    }

    pub fn lookup_templata_by_name(
        &self,
        env: IEnvironmentT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        range: &[RangeS<'s>],
        name: INameT<'s, 't>,
    ) -> ITemplataT<'s, 't> {
        panic!("Unimplemented: Slab 15 — body migration");
    }

    pub fn lookup_templata_by_rune(
        &self,
        env: IEnvironmentT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        range: &[RangeS<'s>],
        name: IImpreciseNameS<'s>,
    ) -> Option<ITemplataT<'s, 't>> {
        // Changed this from AnythingLookupContext to TemplataLookupContext
        // because this is called from StructCompiler to figure out its members.
        // We could instead pipe a lookup context through, if this proves problematic.
        let mut lookup_filter = HashSet::new();
        lookup_filter.insert(ILookupContext::TemplataLookupContext);
        let results = env.lookup_nearest_with_imprecise_name(name, lookup_filter, self.typing_interner);
        if results.iter().count() > 1 {
            panic!("vfail");
        }
        results
    }

    pub fn coerce_kind_to_coord(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        kind: KindT<'s, 't>,
        region: RegionT,
    ) -> CoordT<'s, 't> {
        let mutability = self.get_mutability(coutputs, kind);
        let ownership = match mutability {
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) => OwnershipT::Own,
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => OwnershipT::Share,
            ITemplataT::Placeholder(_) => OwnershipT::Own,
            other => unreachable!("Unexpected mutability templata: {:?}", other),
        };
        CoordT { ownership, region, kind }
    }

    pub fn coerce_to_coord(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        env: IInDenizenEnvironmentT<'s, 't>,
        range: &[RangeS<'s>],
        templata: ITemplataT<'s, 't>,
        region: RegionT,
    ) -> ITemplataT<'s, 't> {
        match templata {
            ITemplataT::Kind(kind_templata) => {
                ITemplataT::Coord(self.typing_interner.alloc(
                    CoordTemplataT { coord: self.coerce_kind_to_coord(coutputs, kind_templata.kind, region) }
                ))
            }
            ITemplataT::Coord(_) => { panic!("vcurious"); }
            ITemplataT::StructDefinition(_) => { panic!("vcurious"); }
            ITemplataT::InterfaceDefinition(_) => { panic!("vcurious"); }
            _ => { panic!("Unimplemented: coerce_to_coord for {:?}", templata); }
        }
    }

    pub fn resolve_struct_template(
        &self,
        struct_templata: &'t StructDefinitionTemplataT<'s, 't>,
    ) -> &'t IdT<'s, 't> {
        let declaring_env = struct_templata.declaring_env;
        let struct_a = struct_templata.origin_struct;
        let translated = self.translate_struct_name(struct_a.name);
        let local_name = match translated {
            IStructTemplateNameT::StructTemplate(r) => INameT::StructTemplate(r),
            IStructTemplateNameT::AnonymousSubstructTemplate(r) => INameT::AnonymousSubstructTemplate(r),
            IStructTemplateNameT::LambdaCitizenTemplate(r) => INameT::LambdaCitizenTemplate(r),
        };
        declaring_env.id().add_step(self.typing_interner, local_name)
    }

    pub fn resolve_interface_template(
        &self,
        interface_templata: &'t InterfaceDefinitionTemplataT<'s, 't>,
    ) -> &'t IdT<'s, 't> {
        let declaring_env = interface_templata.declaring_env;
        let interface_a = interface_templata.origin_interface;
        let translated = self.translate_interface_name(*interface_a.name);
        let local_name = match translated {
            IInterfaceTemplateNameT::InterfaceTemplate(r) => INameT::InterfaceTemplate(r),
        };
        declaring_env.id().add_step(self.typing_interner, local_name)
    }

    pub fn resolve_citizen_template(
        &self,
        citizen_templata: &'t CitizenDefinitionTemplataT<'s, 't>,
    ) -> IdT<'s, 't> {
        panic!("Unimplemented: Slab 15 — body migration");
    }

    pub fn citizen_is_from_template(
        &self,
        actual_citizen_ref: ICitizenTT<'s, 't>,
        expected_citizen_templata: ITemplataT<'s, 't>,
    ) -> bool {
        let citizen_template_id = match expected_citizen_templata {
            ITemplataT::StructDefinition(st) => *self.resolve_struct_template(st),
            ITemplataT::InterfaceDefinition(it) => *self.resolve_interface_template(it),
            ITemplataT::Kind(kt) => {
                match ISubKindTT::try_from(kt.kind) {
                    Ok(sub) => self.get_citizen_template(sub.id()),
                    Err(_) => return false,
                }
            }
            ITemplataT::Coord(ct) => {
                match (ct.coord.ownership, ISubKindTT::try_from(ct.coord.kind)) {
                    (OwnershipT::Own, Ok(sub)) | (OwnershipT::Share, Ok(sub)) => self.get_citizen_template(sub.id()),
                    _ => return false,
                }
            }
            _ => return false,
        };
        self.get_citizen_template(ISubKindTT::from(actual_citizen_ref).id()) == citizen_template_id
    }

    pub fn create_placeholder(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        env: IInDenizenEnvironmentT<'s, 't>,
        name_prefix: IdT<'s, 't>,
        generic_param: &'s GenericParameterS<'s>,
        index: i32,
        rune_to_type: &IndexMap<IRuneS<'s>, ITemplataType<'s>>,
        current_height: Option<i32>,
        register_with_compiler_outputs: bool,
    ) -> ITemplataT<'s, 't> {
        let rune_type = *rune_to_type.get(&generic_param.rune.rune).unwrap();
        let rune = generic_param.rune.rune;
        match rune_type {
            ITemplataType::KindTemplataType(_) => {
                let (kind_mutable, _region_mutable) = match &generic_param.tyype {
                    IGenericParameterTypeS::CoordGenericParameterType(CoordGenericParameterTypeS { kind_mutable, region_mutable, .. }) => {
                        (if *kind_mutable { OwnershipT::Own } else { OwnershipT::Share }, *region_mutable)
                    }
                    _ => (OwnershipT::Own, false),
                };
                ITemplataT::Kind(self.typing_interner.alloc(self.create_kind_placeholder_inner(
                    coutputs, env, name_prefix, index, rune, kind_mutable, register_with_compiler_outputs)))
            }
            ITemplataType::CoordTemplataType(_) => {
                let (kind_mutable, region_mutability) = match &generic_param.tyype {
                    IGenericParameterTypeS::CoordGenericParameterType(CoordGenericParameterTypeS { kind_mutable, region_mutable, .. }) => {
                        (if *kind_mutable { OwnershipT::Own } else { OwnershipT::Share },
                         if *region_mutable { IRegionMutabilityS::ReadWriteRegion } else { IRegionMutabilityS::ReadOnlyRegion })
                    }
                    _ => (OwnershipT::Own, IRegionMutabilityS::ReadOnlyRegion),
                };
                ITemplataT::Coord(self.typing_interner.alloc(self.create_coord_placeholder_inner(
                    coutputs, env, name_prefix, index, rune, current_height,
                    region_mutability, kind_mutable, register_with_compiler_outputs)))
            }
            other_type => {
                self.create_non_kind_non_region_placeholder_inner(name_prefix, index, rune, other_type)
            }
        }
    }

    pub fn create_coord_placeholder_inner(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        env: IInDenizenEnvironmentT<'s, 't>,
        name_prefix: IdT<'s, 't>,
        index: i32,
        rune: IRuneS<'s>,
        current_height: Option<i32>,
        region_mutability: IRegionMutabilityS,
        kind_ownership: OwnershipT,
        register_with_compiler_outputs: bool,
    ) -> CoordTemplataT<'s, 't> {
        // val regionPlaceholderTemplata = RegionT(DefaultRegionT)
        let region_placeholder_templata = RegionT { region: IRegionT::Default };

        // val kindPlaceholderT =
        //   createKindPlaceholderInner(
        //     coutputs, env, namePrefix, index, rune, kindOwnership, registerWithCompilerOutputs)
        let kind_placeholder_t = self.create_kind_placeholder_inner(
            coutputs, env, name_prefix, index, rune, kind_ownership, register_with_compiler_outputs);

        // CoordTemplataT(CoordT(kindOwnership, regionPlaceholderTemplata, kindPlaceholderT.kind))
        CoordTemplataT {
            coord: CoordT {
                ownership: kind_ownership,
                region: region_placeholder_templata,
                kind: kind_placeholder_t.kind,
            }
        }
    }

    pub fn create_kind_placeholder_inner(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        env: IInDenizenEnvironmentT<'s, 't>,
        name_prefix: IdT<'s, 't>,
        index: i32,
        rune: IRuneS<'s>,
        kind_ownership: OwnershipT,
        register_with_compiler_outputs: bool,
    ) -> KindTemplataT<'s, 't> {
        // val kindPlaceholderId =
        //   namePrefix.addStep(
        //     interner.intern(KindPlaceholderNameT(
        //       interner.intern(KindPlaceholderTemplateNameT(index, rune)))))
        let template_name = self.typing_interner.intern_kind_placeholder_template_name(
            KindPlaceholderTemplateNameT { index, rune});
        let placeholder_name = self.typing_interner.intern_kind_placeholder_name(
            KindPlaceholderNameT { template: template_name });
        let kind_placeholder_id = name_prefix.add_step(
            self.typing_interner, INameT::KindPlaceholder(placeholder_name));

        // val kindPlaceholderTemplateId =
        //   TemplataCompiler.getPlaceholderTemplate(kindPlaceholderId)
        let kind_placeholder_template_id_val = self.get_placeholder_template(*kind_placeholder_id);
        let kind_placeholder_template_id = self.typing_interner.intern_id(IdValT {
            package_coord: kind_placeholder_template_id_val.package_coord,
            init_steps: kind_placeholder_template_id_val.init_steps,
            local_name: kind_placeholder_template_id_val.local_name,
        });

        // if (registerWithCompilerOutputs) {
        if register_with_compiler_outputs {
            // coutputs.declareType(kindPlaceholderTemplateId)
            coutputs.declare_type(kind_placeholder_template_id);

            // val mutability = MutabilityTemplataT(kindOwnership match {
            //   case OwnT => MutableT
            //   case ShareT => ImmutableT
            // })
            let mutability = ITemplataT::Mutability(MutabilityTemplataT {
                mutability: match kind_ownership {
                    OwnershipT::Own => MutabilityT::Mutable,
                    OwnershipT::Share => MutabilityT::Immutable,
                    _ => unreachable!("Scala's create_kind_placeholder_inner is exhaustive over Own/Share — Borrow/Weak not valid kind ownerships"),
                },
            });
            // coutputs.declareTypeMutability(kindPlaceholderTemplateId, mutability)
            coutputs.declare_type_mutability(kind_placeholder_template_id, mutability);

            // Per @BDPFWDZ: the placeholder env stays empty. Bound declarations
            // (IsaTemplataT, FunctionBoundNameT) live in the introducing function's near-env, not
            // here. Lookups walk from the calling env to find them.
            // val placeholderEnv = GeneralEnvironmentT.childOf(interner, env, kindPlaceholderTemplateId, kindPlaceholderTemplateId)
            let placeholder_env = child_of(
                self.typing_interner,
                self.scout_arena,
                env,
                *kind_placeholder_template_id,
                kind_placeholder_template_id,
                vec![],
            );
            let placeholder_env_ref: IInDenizenEnvironmentT<'s, 't> =
                IInDenizenEnvironmentT::General(placeholder_env);
            // coutputs.declareTypeOuterEnv(kindPlaceholderTemplateId, placeholderEnv)
            coutputs.declare_type_outer_env(kind_placeholder_template_id, placeholder_env_ref);
            // coutputs.declareTypeInnerEnv(kindPlaceholderTemplateId, placeholderEnv)
            coutputs.declare_type_inner_env(kind_placeholder_template_id, placeholder_env_ref);
        }

        // KindTemplataT(KindPlaceholderT(kindPlaceholderId))
        let kind_placeholder = self.typing_interner.intern_kind_placeholder(
            KindPlaceholderT { id: *kind_placeholder_id });
        KindTemplataT { kind: KindT::KindPlaceholder(kind_placeholder) }
    }

    pub fn create_non_kind_non_region_placeholder_inner(
        &self,
        name_prefix: IdT<'s, 't>,
        index: i32,
        rune: IRuneS<'s>,
        tyype: ITemplataType<'s>,
    ) -> ITemplataT<'s, 't> {
        // val idT = namePrefix.addStep(interner.intern(NonKindNonRegionPlaceholderNameT(index, rune)))
        let placeholder_name = self.typing_interner.intern_non_kind_non_region_placeholder_name(
            NonKindNonRegionPlaceholderNameT { index, rune}
        );
        let id_t = name_prefix.add_step(
            self.typing_interner,
            INameT::NonKindNonRegionPlaceholder(placeholder_name),
        );
        // PlaceholderTemplataT(idT, tyype)
        ITemplataT::Placeholder(self.typing_interner.alloc(PlaceholderTemplataT {
            id: *id_t,
            tyype,
        }))
    }

}
