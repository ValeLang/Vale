
use crate::instantiating::ast::names::*;
use crate::instantiating::ast::types::*;
use crate::instantiating::ast::ast::*;
use crate::instantiating::ast::citizens::*;
use crate::instantiating::ast::templata::*;
use crate::instantiating::ast::hinputs::*;
use crate::instantiating::ast::expressions::*;
use crate::instantiating::instantiating_interner::InstantiatingInterner;
use crate::typing::typing_interner::TypingInterner;
use crate::instantiating::region_collapser_individual;
use crate::instantiating::region_collapser_consistent;
use crate::instantiating::region_counter;
use crate::instantiating::collector;
use crate::instantiating::collector::NodeRefI;
use crate::typing::names::names::*;
use crate::typing::ast::ast::*;
use crate::typing::ast::citizens::*;
use crate::typing::types::types::*;
use crate::typing::hinputs_t::*;
use crate::typing::compiler::Compiler;
use crate::utils::vassert::vassert_one;
use crate::postparsing::names::IRuneS;
use crate::utils::arena_index_map::ArenaIndexMap;
use crate::keywords::Keywords;
use crate::compile_options::GlobalOptions;
use crate::typing::templata::templata::ITemplataT;
use crate::typing::ast::expressions::{ReferenceExpressionTE, ExpressionTE, AddressExpressionTE};
use crate::typing::env::function_environment_t::{ILocalVariableT, ReferenceLocalVariableT, AddressibleLocalVariableT};
use indexmap::IndexMap;
use crate::instantiating::ast::ast::ExternI;
use crate::instantiating::ast::ast::ICitizenAttributeI;
use crate::instantiating::ast::ast::KindExternI;
use crate::instantiating::ast::ast::LocationInFunctionEnvironmentI;
use crate::instantiating::ast::ast::ReferenceLocalVariableI;
use crate::instantiating::ast::citizens::AddressMemberTypeI;
use crate::instantiating::ast::citizens::IMemberTypeI;
use crate::instantiating::ast::citizens::InterfaceDefinitionI;
use crate::instantiating::ast::citizens::ReferenceMemberTypeI;
use crate::instantiating::ast::citizens::StructDefinitionI;
use crate::instantiating::ast::citizens::StructMemberI;
use crate::instantiating::ast::expressions::AddressExpressionIE;
use crate::instantiating::ast::expressions::AddressMemberLookupIE;
use crate::instantiating::ast::expressions::ArgLookupIE;
use crate::instantiating::ast::expressions::ArrayLengthIE;
use crate::instantiating::ast::expressions::AsSubtypeIE;
use crate::instantiating::ast::expressions::ConstructIE;
use crate::instantiating::ast::expressions::DestroyIE;
use crate::instantiating::ast::expressions::DestroyMutRuntimeSizedArrayIE;
use crate::instantiating::ast::expressions::DestroyStaticSizedArrayIntoFunctionIE;
use crate::instantiating::ast::expressions::DestroyStaticSizedArrayIntoLocalsIE;
use crate::instantiating::ast::expressions::ExpressionIE;
use crate::instantiating::ast::expressions::ExternFunctionCallIE;
use crate::instantiating::ast::expressions::FunctionCallIE;
use crate::instantiating::ast::expressions::InterfaceFunctionCallIE;
use crate::instantiating::ast::expressions::IsSameInstanceIE;
use crate::instantiating::ast::expressions::LocalLookupIE;
use crate::instantiating::ast::expressions::MutateIE;
use crate::instantiating::ast::expressions::NewImmRuntimeSizedArrayIE;
use crate::instantiating::ast::expressions::NewMutRuntimeSizedArrayIE;
use crate::instantiating::ast::expressions::PopRuntimeSizedArrayIE;
use crate::instantiating::ast::expressions::PushRuntimeSizedArrayIE;
use crate::instantiating::ast::expressions::ReferenceExpressionIE;
use crate::instantiating::ast::expressions::ReferenceMemberLookupIE;
use crate::instantiating::ast::expressions::RestackifyIE;
use crate::instantiating::ast::expressions::RuntimeSizedArrayCapacityIE;
use crate::instantiating::ast::expressions::RuntimeSizedArrayLookupIE;
use crate::instantiating::ast::expressions::SoftLoadIE;
use crate::instantiating::ast::expressions::StaticArrayFromCallableIE;
use crate::instantiating::ast::expressions::StaticArrayFromValuesIE;
use crate::instantiating::ast::expressions::StaticSizedArrayLookupIE;
use crate::instantiating::ast::expressions::UpcastIE;
use crate::instantiating::ast::names::AnonymousSubstructConstructorNameI;
use crate::instantiating::ast::names::AnonymousSubstructConstructorTemplateNameI;
use crate::instantiating::ast::names::AnonymousSubstructImplNameI;
use crate::instantiating::ast::names::AnonymousSubstructImplTemplateNameI;
use crate::instantiating::ast::names::AnonymousSubstructNameI;
use crate::instantiating::ast::names::AnonymousSubstructTemplateNameI;
use crate::instantiating::ast::names::ClosureParamNameI;
use crate::instantiating::ast::names::ConstructingMemberNameI;
use crate::instantiating::ast::names::ForwarderFunctionNameI;
use crate::instantiating::ast::names::ForwarderFunctionTemplateNameI;
use crate::instantiating::ast::names::ICitizenTemplateNameI;
use crate::instantiating::ast::names::IImplNameI;
use crate::instantiating::ast::names::IImplTemplateNameI;
use crate::instantiating::ast::names::IInterfaceTemplateNameI;
use crate::instantiating::ast::names::ImplNameI;
use crate::instantiating::ast::names::ImplTemplateNameI;
use crate::instantiating::ast::names::InterfaceNameI;
use crate::instantiating::ast::names::InterfaceTemplateNameI;
use crate::instantiating::ast::names::IterableNameI;
use crate::instantiating::ast::names::IterationOptionNameI;
use crate::instantiating::ast::names::IteratorNameI;
use crate::instantiating::ast::names::LambdaCitizenNameI;
use crate::instantiating::ast::names::LambdaCitizenTemplateNameI;
use crate::instantiating::ast::names::MagicParamNameI;
use crate::instantiating::ast::names::RawArrayNameI;
use crate::instantiating::ast::names::RuntimeSizedArrayNameI;
use crate::instantiating::ast::names::RuntimeSizedArrayTemplateNameI;
use crate::instantiating::ast::names::SelfNameI;
use crate::instantiating::ast::names::StaticSizedArrayNameI;
use crate::instantiating::ast::names::StaticSizedArrayTemplateNameI;
use crate::instantiating::ast::names::StructNameI;
use crate::instantiating::ast::names::StructTemplateNameI;
use crate::instantiating::ast::names::TypingPassBlockResultVarNameI;
use crate::instantiating::ast::names::TypingPassTemporaryVarNameI;
use crate::instantiating::ast::templata::MutabilityTemplataI;
use crate::instantiating::ast::templata::RegionTemplataI;
use crate::instantiating::ast::templata::VariabilityTemplataI;
use crate::instantiating::ast::templata::expect_integer_templata;
use crate::instantiating::ast::templata::expect_mutability_templata;
use crate::instantiating::ast::templata::expect_variability_templata;
use crate::instantiating::ast::types::BoolIT;
use crate::instantiating::ast::types::IntIT;
use crate::instantiating::ast::types::InterfaceITValI;
use crate::instantiating::ast::types::KindIT;
use crate::instantiating::ast::types::OwnershipI;
use crate::instantiating::ast::types::StrIT;
use crate::instantiating::ast::types::StructITValI;
use crate::instantiating::ast::types::VoidIT;
use crate::instantiating::region_collapser_consistent::collapse_impl_id as collapse_impl_id_consistent;
use crate::instantiating::region_collapser_consistent::collapse_prototype as collapse_prototype_consistent;
use crate::instantiating::region_collapser_individual::collapse_citizen;
use crate::instantiating::region_collapser_individual::collapse_coord;
use crate::instantiating::region_collapser_individual::collapse_impl_id;
use crate::instantiating::region_collapser_individual::collapse_interface_id;
use crate::instantiating::region_collapser_individual::collapse_prototype;
use crate::instantiating::region_collapser_individual::collapse_runtime_sized_array;
use crate::instantiating::region_collapser_individual::collapse_static_sized_array;
use crate::instantiating::region_collapser_individual::collapse_struct_id;
use crate::instantiating::region_collapser_individual::collapse_var_name;
use crate::instantiating::region_counter::count_impl_id_map;
use crate::instantiating::region_counter::count_prototype_map;
use crate::typing::ast::ast::ICitizenAttributeT;
use crate::typing::ast::ast::LocationInFunctionEnvironmentT;
use crate::typing::ast::citizens::IMemberTypeT;
use crate::typing::ast::citizens::NormalStructMemberT;
use crate::typing::ast::expressions::AddressMemberLookupTE;
use crate::typing::ast::expressions::ArgLookupTE;
use crate::typing::ast::expressions::ArrayLengthTE;
use crate::typing::ast::expressions::AsSubtypeTE;
use crate::typing::ast::expressions::BorrowToWeakTE;
use crate::typing::ast::expressions::ConstructTE;
use crate::typing::ast::expressions::DeferTE;
use crate::typing::ast::expressions::DestroyMutRuntimeSizedArrayTE;
use crate::typing::ast::expressions::DestroyStaticSizedArrayIntoFunctionTE;
use crate::typing::ast::expressions::DestroyStaticSizedArrayIntoLocalsTE;
use crate::typing::ast::expressions::DestroyTE;
use crate::typing::ast::expressions::ExternFunctionCallTE;
use crate::typing::ast::expressions::FunctionCallTE;
use crate::typing::ast::expressions::InterfaceFunctionCallTE;
use crate::typing::ast::expressions::IsSameInstanceTE;
use crate::typing::ast::expressions::LetAndLendTE;
use crate::typing::ast::expressions::LocalLookupTE;
use crate::typing::ast::expressions::LockWeakTE;
use crate::typing::ast::expressions::MutateTE;
use crate::typing::ast::expressions::NewImmRuntimeSizedArrayTE;
use crate::typing::ast::expressions::NewMutRuntimeSizedArrayTE;
use crate::typing::ast::expressions::PopRuntimeSizedArrayTE;
use crate::typing::ast::expressions::PushRuntimeSizedArrayTE;
use crate::typing::ast::expressions::ReferenceMemberLookupTE;
use crate::typing::ast::expressions::RuntimeSizedArrayCapacityTE;
use crate::typing::ast::expressions::RuntimeSizedArrayLookupTE;
use crate::typing::ast::expressions::SoftLoadTE;
use crate::typing::ast::expressions::StaticArrayFromCallableTE;
use crate::typing::ast::expressions::StaticArrayFromValuesTE;
use crate::typing::ast::expressions::StaticSizedArrayLookupTE;
use crate::typing::ast::expressions::TupleTE;
use crate::typing::ast::expressions::UpcastTE;
use crate::typing::names::names::AnonymousSubstructConstructorNameT;
use crate::typing::names::names::AnonymousSubstructConstructorTemplateNameT;
use crate::typing::names::names::AnonymousSubstructImplNameT;
use crate::typing::names::names::AnonymousSubstructImplTemplateNameT;
use crate::typing::names::names::AnonymousSubstructTemplateNameT;
use crate::typing::names::names::ClosureParamNameT;
use crate::typing::names::names::ExternFunctionNameT;
use crate::typing::names::names::ForwarderFunctionNameT;
use crate::typing::names::names::ForwarderFunctionTemplateNameT;
use crate::typing::names::names::FunctionBoundNameT;
use crate::typing::names::names::FunctionBoundTemplateNameT;
use crate::typing::names::names::ICitizenTemplateNameT;
use crate::typing::names::names::IInstantiationNameT;
use crate::typing::names::names::IInterfaceNameT;
use crate::typing::names::names::INameT;
use crate::typing::names::names::ImplNameT;
use crate::typing::names::names::ImplTemplateNameT;
use crate::typing::names::names::InterfaceNameT;
use crate::typing::names::names::InterfaceTemplateNameT;
use crate::typing::names::names::IterableNameT;
use crate::typing::names::names::IterationOptionNameT;
use crate::typing::names::names::IteratorNameT;
use crate::typing::names::names::MagicParamNameT;
use crate::typing::names::names::RawArrayNameT;
use crate::typing::names::names::RuntimeSizedArrayNameT;
use crate::typing::names::names::StaticSizedArrayNameT;
use crate::typing::names::names::StructTemplateNameT;
use crate::typing::names::names::TypingPassBlockResultVarNameT;
use crate::typing::names::names::TypingPassTemporaryVarNameT;
use crate::typing::templata::templata::PlaceholderTemplataT;
use crate::typing::types::types::IRegionT;
use crate::utils::utils::union_maps_expect_no_conflict;
use std::collections::HashMap;
use std::collections::HashSet;
use std::marker::PhantomData;
use std::mem::discriminant;
use std::mem::transmute;
use crate::instantiating::ast::types::MutabilityI;
use crate::typing::types::types::KindT;
use crate::typing::types::types::MutabilityT;
/// Temporary state
#[derive(Clone, PartialEq, Eq)]
pub struct DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i> where 's: 't, 's: 'i {
    pub func_id_to_bound_arg_prototype: IndexMap<IdT<'s, 't>, &'i PrototypeI<'s, 'i, sI>>,
    pub bound_param_impl_id_to_bound_arg_impl_id: IndexMap<IdT<'s, 't>, IdI<'s, 'i, sI>>,
}

impl<'s, 't, 'i> DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i> where 's: 't, 's: 'i {
    pub fn plus(&self, that: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>) -> DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i> {
        DenizenBoundToDenizenCallerBoundArgI {
            func_id_to_bound_arg_prototype: union_maps_expect_no_conflict(&self.func_id_to_bound_arg_prototype, &that.func_id_to_bound_arg_prototype, |x, y| x == y),
            bound_param_impl_id_to_bound_arg_impl_id: union_maps_expect_no_conflict(&self.bound_param_impl_id_to_bound_arg_impl_id, &that.bound_param_impl_id_to_bound_arg_impl_id, |x, y| x == y),
        }
    }
}

/// Temporary state
pub struct InstantiatedOutputsI<'s, 't, 'i> where 's: 't, 's: 'i {
    pub functions: IndexMap<IdI<'s, 'i, cI>, &'i FunctionDefinitionI<'s, 'i>>,
    pub structs: IndexMap<IdI<'s, 'i, cI>, &'i StructDefinitionI<'s, 'i, cI>>,
    pub interfaces_without_methods: IndexMap<IdI<'s, 'i, cI>, &'i InterfaceDefinitionI<'s, 'i, cI>>,
    pub struct_to_mutability: IndexMap<IdI<'s, 'i, cI>, MutabilityI>,
    pub struct_to_bounds: IndexMap<IdI<'s, 'i, sI>, DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>>,
    pub interface_to_mutability: IndexMap<IdI<'s, 'i, cI>, MutabilityI>,
    pub interface_to_bounds: IndexMap<IdI<'s, 'i, sI>, DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>>,
    pub impl_to_mutability: IndexMap<IdI<'s, 'i, cI>, MutabilityI>,
    pub impl_to_bounds: IndexMap<IdI<'s, 'i, sI>, DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>>,
    pub interface_to_impls: IndexMap<IdI<'s, 'i, cI>, Vec<(IdT<'s, 't>, IdI<'s, 'i, cI>)>>,
    pub interface_to_abstract_func_to_virtual_index: IndexMap<IdI<'s, 'i, cI>, IndexMap<PrototypeI<'s, 'i, cI>, usize>>,
    pub impls: IndexMap<IdI<'s, 'i, cI>, (ICitizenIT<'s, 'i, cI>, IdI<'s, 'i, cI>, DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, InstantiationBoundArgumentsI<'s, 'i>)>,
    pub abstract_func_to_bounds: IndexMap<IdI<'s, 'i, cI>, (DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, &'i InstantiationBoundArgumentsI<'s, 'i>)>,
    pub interface_to_impl_to_abstract_prototype_to_override: IndexMap<IdI<'s, 'i, cI>, IndexMap<IdI<'s, 'i, cI>, IndexMap<PrototypeI<'s, 'i, cI>, PrototypeI<'s, 'i, cI>>>>,
    pub new_impls: Vec<(IdT<'s, 't>, IdI<'s, 'i, nI>, InstantiationBoundArgumentsI<'s, 'i>)>,
    pub new_abstract_funcs: Vec<(PrototypeT<'s, 't>, PrototypeI<'s, 'i, nI>, usize, IdI<'s, 'i, cI>, InstantiationBoundArgumentsI<'s, 'i>)>,
    pub new_functions: Vec<(PrototypeT<'s, 't>, PrototypeI<'s, 'i, nI>, InstantiationBoundArgumentsI<'s, 'i>, Option<DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>>)>,
    pub kind_externs: Vec<KindExternI<'s, 'i>>,
    pub function_externs: Vec<FunctionExternI<'s, 'i>>,
}

impl<'s, 't, 'i> InstantiatedOutputsI<'s, 't, 'i> where 's: 't, 's: 'i {
  pub fn new() -> Self {
    InstantiatedOutputsI {
      functions: IndexMap::new(),
      structs: IndexMap::new(),
      interfaces_without_methods: IndexMap::new(),
      struct_to_mutability: IndexMap::new(),
      struct_to_bounds: IndexMap::new(),
      interface_to_mutability: IndexMap::new(),
      interface_to_bounds: IndexMap::new(),
      impl_to_mutability: IndexMap::new(),
      impl_to_bounds: IndexMap::new(),
      interface_to_impls: IndexMap::new(),
      interface_to_abstract_func_to_virtual_index: IndexMap::new(),
      impls: IndexMap::new(),
      abstract_func_to_bounds: IndexMap::new(),
      interface_to_impl_to_abstract_prototype_to_override: IndexMap::new(),
      new_impls: Vec::new(),
      new_abstract_funcs: Vec::new(),
      new_functions: Vec::new(),
      kind_externs: Vec::new(),
      function_externs: Vec::new(),
    }
  }
    pub fn add_method_to_v_table(&mut self, impl_id: IdI<'s, 'i, cI>, super_interface_id: IdI<'s, 'i, cI>, abstract_func_prototype: PrototypeI<'s, 'i, cI>, override_: PrototypeI<'s, 'i, cI>) {
        let map = self.interface_to_impl_to_abstract_prototype_to_override
            .entry(super_interface_id).or_insert_with(IndexMap::new)
            .entry(impl_id).or_insert_with(IndexMap::new);
        assert!(!map.contains_key(&abstract_func_prototype));
        map.insert(abstract_func_prototype, override_);
    }
}

pub fn translate<'s, 'ctx, 't, 'i>(opts: &'ctx GlobalOptions, interner: &'ctx InstantiatingInterner<'s, 'i>, typing_interner: &'ctx TypingInterner<'s, 't>, keywords: &'ctx Keywords<'s>, hinputs: &'ctx HinputsT<'s, 't>) -> HinputsI<'s, 'i>
where 's: 't, 's: 'i {
    let mut monouts = InstantiatedOutputsI::new();
    let instantiator = InstantiatorI { opts, interner, typing_interner, keywords, hinputs };
    instantiator.translate_method(&mut monouts)
}

/// Temporary state
pub struct InstantiatorI<'s, 'ctx, 't, 'i> where 's: 't, 's: 'i {
    pub opts: &'ctx GlobalOptions,
    pub interner: &'ctx InstantiatingInterner<'s, 'i>,
    // Scala used one Interner; Rust split it into typing + instantiating, so the instantiator holds
    // the typing half too, for T-side helpers like TemplataCompiler::get_super_template.
    pub typing_interner: &'ctx TypingInterner<'s, 't>,
    pub keywords: &'ctx Keywords<'s>,
    pub hinputs: &'ctx HinputsT<'s, 't>,
}

impl<'s, 'ctx, 't, 'i> InstantiatorI<'s, 'ctx, 't, 'i> where 's: 't, 's: 'i {
    pub fn translate_method(&self, monouts: &mut InstantiatedOutputsI<'s, 't, 'i>) -> HinputsI<'s, 'i> {
        let HinputsT {
            interfaces: _interfaces_t,
            structs: _structs_t,
            functions: _functions_t,
            interface_to_edge_blueprints: _interface_to_edge_blueprints_t,
            interface_to_sub_citizen_to_edge: _interface_to_sub_citizen_to_edge_t,
            instantiation_name_to_instantiation_bounds: _instantiation_name_to_function_bound_to_rune_t,
            kind_exports: kind_exports_t,
            function_exports: function_exports_t,
            kind_externs: _kind_externs_t,
            function_externs: function_externs_t,
            sub_citizen_to_interface_to_edge: _,
        } = self.hinputs;

        let kind_exports_c: Vec<KindExportI<'s, 'i>> =
            kind_exports_t.iter().map(|&kind_export_t| {
                let KindExportT { range, tyype, id: export_placeholdered_id_t, exported_name } = kind_export_t;
                let export_id_s = self.translate_id(
                    export_placeholdered_id_t,
                    |export_name_t: &INameT<'s, 't>| -> INameValI<'s, 'i, sI> {
                        match export_name_t {
                            INameT::Export(ExportNameT { template: ExportTemplateNameT { code_loc, .. }, .. }) => {
                                INameValI::Export(ExportNameI {
                                    template: ExportTemplateNameI { _marker: PhantomData, code_loc: *code_loc },
                                    region: RegionTemplataI { pure_height: 0, _marker: PhantomData },
                                })
                            }
                            _ => panic!("Unimplemented: translate_method kind_exports translateId closure"),
                        }
                    });
                let export_id_c =
                    region_collapser_individual::collapse_export_id(self.interner, region_counter::count_export_id(&export_id_s), &export_id_s);
                let substitutions = self.assemble_placeholder_map(export_placeholdered_id_t, &export_id_s);
                let denizen_bound_to_denizen_caller_supplied_thing = DenizenBoundToDenizenCallerBoundArgI {
                    func_id_to_bound_arg_prototype: IndexMap::new(),
                    bound_param_impl_id_to_bound_arg_impl_id: IndexMap::new(),
                };
                let kind_it = self.translate_kind(
                    monouts,
                    export_placeholdered_id_t,
                    &denizen_bound_to_denizen_caller_supplied_thing,
                    &substitutions,
                    &RegionT { region: IRegionT::Default },
                    &tyype);
                let kind_ct = region_collapser_individual::collapse_kind(self.interner, &kind_it);
                KindExportI {
                    range: *range,
                    tyype: kind_ct,
                    id: export_id_c,
                    exported_name: *exported_name,
                }
            }).collect();

        let function_exports_c: Vec<FunctionExportI<'s, 'i>> =
            function_exports_t.iter().map(|&function_export_t| {
                let FunctionExportT { range, prototype: prototype_t, export_id: export_placeholdered_id_t, exported_name } = function_export_t;
                let perspective_region_t = RegionT { region: IRegionT::Default };
                let export_id_s = self.translate_id(
                    export_placeholdered_id_t,
                    |export_name_t: &INameT<'s, 't>| -> INameValI<'s, 'i, sI> {
                        match export_name_t {
                            INameT::Export(ExportNameT { template: ExportTemplateNameT { code_loc, .. }, .. }) => {
                                INameValI::Export(ExportNameI {
                                    template: ExportTemplateNameI { _marker: PhantomData, code_loc: *code_loc },
                                    region: RegionTemplataI { pure_height: 0, _marker: PhantomData },
                                })
                            }
                            _ => panic!("Unimplemented: translate_method function_exports translateId closure"),
                        }
                    });
                let export_id_c =
                    region_collapser_individual::collapse_export_id(self.interner, region_counter::count_export_id(&export_id_s), &export_id_s);
                let substitutions = self.assemble_placeholder_map(export_placeholdered_id_t, &export_id_s);

                let denizen_bound_to_denizen_caller_supplied_thing = DenizenBoundToDenizenCallerBoundArgI {
                    func_id_to_bound_arg_prototype: IndexMap::new(),
                    bound_param_impl_id_to_bound_arg_impl_id: IndexMap::new(),
                };
                let (_, prototype_c) =
                    self.translate_prototype(
                        monouts,
                        export_placeholdered_id_t,
                        &denizen_bound_to_denizen_caller_supplied_thing,
                        &substitutions,
                        &perspective_region_t,
                        &prototype_t);

                // Scala's `Collector.all(prototypeC, {case PlaceholderTemplataT => vwat()})` sanity check is omitted:
                // Rust's I-side AST is statically typed (PrototypeI holds only ITemplataI, which has no placeholder
                // variant), so a leftover typing-pass placeholder can't reach a typed PrototypeI. (Architect-approved parity gap.)
                FunctionExportI {
                    range: *range,
                    prototype: self.interner.intern_prototype_ci(PrototypeIValI { id: prototype_c.id, return_type: prototype_c.return_type }),
                    export_id: export_id_c,
                    exported_name: *exported_name,
                }
            }).collect();

        let non_generic_func_externs_c: Vec<FunctionExternI<'s, 'i>> =
            function_externs_t.iter().flat_map(|&function_extern_t| -> Option<FunctionExternI<'s, 'i>> {
                let FunctionExternT { range: _range, extern_placeholdered_id: extern_placeholdered_id_t, prototype: prototype_t, extern_name: _externed_name, generic_parameter_inheritance: maybe_inheritance } = function_extern_t;
                let is_generic = !IInstantiationNameT::try_from(prototype_t.id.local_name).unwrap().template_args().is_empty();
                if is_generic {
                    // We don't handle generic externs yet, that comes later when we see what instantiations are actually needed.
                    // We handle those like we handle normal non-extern generic functions.
                    None
                } else {
                    let perspective_region_t = RegionT { region: IRegionT::Default };

                    let extern_id_s = self.translate_id(
                        extern_placeholdered_id_t,
                        |extern_name_t: &INameT<'s, 't>| -> INameValI<'s, 'i, sI> {
                            match extern_name_t {
                                INameT::Extern(ExternNameT { template: ExternTemplateNameT { code_loc, .. }, .. }) => {
                                    INameValI::Extern(ExternNameI {
                                        template: ExternTemplateNameI { _marker: PhantomData, code_loc: *code_loc },
                                        region: RegionTemplataI { pure_height: 0, _marker: PhantomData },
                                    })
                                }
                                _ => panic!("Unimplemented: translate_method function_externs translateId closure"),
                            }
                        });
                    let _extern_id_c =
                        region_collapser_individual::collapse_extern_id(self.interner, region_counter::count_extern_id(&extern_id_s), &extern_id_s);

                    let substitutions = self.assemble_placeholder_map(extern_placeholdered_id_t, &extern_id_s);

                    let denizen_bound_to_denizen_caller_supplied_thing = DenizenBoundToDenizenCallerBoundArgI {
                        func_id_to_bound_arg_prototype: IndexMap::new(),
                        bound_param_impl_id_to_bound_arg_impl_id: IndexMap::new(),
                    };
                    let (_, prototype_c) =
                        self.translate_prototype(
                            monouts,
                            extern_placeholdered_id_t,
                            &denizen_bound_to_denizen_caller_supplied_thing,
                            &substitutions,
                            &perspective_region_t,
                            &prototype_t);

                    // Scala's `Collector.all(prototypeC, {case PlaceholderTemplataT => vwat()})` sanity check is omitted:
                    // Rust's I-side AST is statically typed (PrototypeI holds only ITemplataI, no placeholder variant),
                    // so a leftover typing-pass placeholder can't reach a typed PrototypeI. (Architect-approved parity gap.)
                    Some(FunctionExternI {
                        prototype: self.interner.intern_prototype_ci(PrototypeIValI { id: prototype_c.id, return_type: prototype_c.return_type }),
                        num_inherited_generic_parameters: maybe_inheritance.as_ref().map(|i| i.num_inherited_generic_parameters).unwrap_or(0),
                    })
                }
            }).collect();

        while {
            // We make structs and interfaces eagerly as we come across them
            // if (monouts.newStructs.nonEmpty) {
            //   val newStructName = monouts.newStructs.dequeue()
            //   DenizentranslateStructDefinition(opts, interner, keywords, hinputs, monouts, newStructName)
            //   true
            // } else if (monouts.newInterfaces.nonEmpty) {
            //   val (newInterfaceName, calleeRuneToSuppliedPrototype) = monouts.newInterfaces.dequeue()
            //   DenizentranslateInterfaceDefinition(
            //     opts, interner, keywords, hinputs, monouts, newInterfaceName, calleeRuneToSuppliedPrototype)
            //   true
            // } else
            if !monouts.new_functions.is_empty() {
                let (new_func_id_t, new_func_id_n, instantiation_bound_args, maybe_denizen_bound_to_denizen_caller_supplied_thing) =
                    monouts.new_functions.remove(0);
                self.translate_function(
                    monouts, &new_func_id_t, &new_func_id_n, &instantiation_bound_args,
                    maybe_denizen_bound_to_denizen_caller_supplied_thing.as_ref());
                true
            } else if !monouts.new_impls.is_empty() {
                let (impl_id_t, impl_id_n, instantiation_bounds_for_unsubstituted_impl) = monouts.new_impls.remove(0);
                self.translate_impl(monouts, &impl_id_t, &impl_id_n, instantiation_bounds_for_unsubstituted_impl);
                true
            } else if !monouts.new_abstract_funcs.is_empty() {
                let (abstract_func_t, abstract_func, virtual_index, interface_id, instantiation_bound_args) = monouts.new_abstract_funcs.remove(0);
                self.translate_abstract_func(monouts, &interface_id, &abstract_func_t, &abstract_func, virtual_index, instantiation_bound_args);
                true
            } else {
                false
            }
        } {}

        let interface_edge_blueprints =
            ArenaIndexMap::from_iter_in(
                monouts.interface_to_abstract_func_to_virtual_index.iter().map(|(interface, abstract_func_prototypes)| -> (IdI<'s, 'i, cI>, InterfaceEdgeBlueprintI<'s, 'i>) {
                    let mut entries: Vec<(&'i PrototypeI<'s, 'i, cI>, i32)> = Vec::new();
                    for (proto, idx) in abstract_func_prototypes.iter() {
                        entries.push((self.interner.alloc(*proto), *idx as i32));
                    }
                    (*interface, InterfaceEdgeBlueprintI { interface: *interface, super_family_root_headers: self.interner.bump().alloc_slice_fill_iter(entries.into_iter()) })
                }),
                self.interner.bump());

        let interfaces: Vec<InterfaceDefinitionI<'s, 'i, cI>> =
            monouts.interfaces_without_methods.values().map(|interface| {
                let InterfaceDefinitionI { instantiated_interface: ref_, attributes, weakable, mutability, .. } = **interface;
                let map = monouts.interface_to_abstract_func_to_virtual_index.get(&ref_.id).expect("vassertSome: interface_to_abstract_func_to_virtual_index");
                let mut methods_entries: Vec<(&'i PrototypeI<'s, 'i, cI>, i32)> = Vec::new();
                for (proto, idx) in map.iter() {
                    methods_entries.push((self.interner.alloc(*proto), *idx as i32));
                }
                InterfaceDefinitionI {
                    instantiated_interface: ref_,
                    attributes,
                    weakable,
                    mutability,
                    rune_to_function_bound: ArenaIndexMap::new_in(self.interner.bump()),
                    rune_to_impl_bound: ArenaIndexMap::new_in(self.interner.bump()),
                    internal_methods: self.interner.bump().alloc_slice_fill_iter(methods_entries.into_iter()),
                    _marker: PhantomData,
                }
            }).collect();

        let interface_to_sub_citizen_to_edge =
            ArenaIndexMap::from_iter_in(
                monouts.interface_to_impls.iter().map(|(interface, impls)| -> (IdI<'s, 'i, cI>, ArenaIndexMap<'i, IdI<'s, 'i, cI>, EdgeI<'s, 'i>>) {
                    let inner_iter = impls.iter().map(|(_impl_id_t, impl_id_i)| -> (IdI<'s, 'i, cI>, EdgeI<'s, 'i>) {
                        let (sub_citizen, parent_interface, _, _) = monouts.impls.get(impl_id_i).expect("vassertSome: monouts.impls");
                        assert!(parent_interface == interface);
                        let abstract_func_to_virtual_index = monouts.interface_to_abstract_func_to_virtual_index.get(interface).expect("vassertSome: interface_to_abstract_func_to_virtual_index");
                        let abstract_func_prototype_to_override_prototype = abstract_func_to_virtual_index.iter().map(|(abstract_func_prototype, virtual_index)| -> (IdI<'s, 'i, cI>, &'i PrototypeI<'s, 'i, cI>) {
                            let override_prototype = monouts.interface_to_impl_to_abstract_prototype_to_override
                                .get(interface).expect("vassertSome interface_to_impl_to_abstract_prototype_to_override (interface)")
                                .get(impl_id_i).expect("vassertSome interface_to_impl_to_abstract_prototype_to_override (impl)")
                                .get(abstract_func_prototype).expect("vassertSome interface_to_impl_to_abstract_prototype_to_override (abstract_func_prototype)");
                            assert!(IFunctionNameI::try_from(abstract_func_prototype.id.local_name).unwrap().parameters()[*virtual_index].kind !=
                                IFunctionNameI::try_from(override_prototype.id.local_name).unwrap().parameters()[*virtual_index].kind);
                            (abstract_func_prototype.id, self.interner.alloc(*override_prototype))
                        });
                        let edge = EdgeI {
                            edge_id: *impl_id_i,
                            sub_citizen: *sub_citizen,
                            super_interface: *interface,
                            rune_to_func_bound: ArenaIndexMap::new_in(self.interner.bump()),
                            rune_to_impl_bound: ArenaIndexMap::new_in(self.interner.bump()),
                            abstract_func_to_override_func: ArenaIndexMap::from_iter_in(abstract_func_prototype_to_override_prototype, self.interner.bump()),
                        };
                        (sub_citizen.id(), edge)
                    });
                    (*interface, ArenaIndexMap::from_iter_in(inner_iter, self.interner.bump()))
                }),
                self.interner.bump());

        let result_hinputs =
            HinputsI {
                interfaces: self.interner.alloc_slice_from_vec(interfaces),
                structs: self.interner.alloc_slice_from_vec(monouts.structs.values().copied().collect()),
                functions: self.interner.alloc_slice_from_vec(monouts.functions.values().copied().collect()),
                interface_to_edge_blueprints: interface_edge_blueprints,
                interface_to_sub_citizen_to_edge,
                kind_exports: self.interner.alloc_slice_from_vec(kind_exports_c),
                function_exports: self.interner.alloc_slice_from_vec(function_exports_c),
                kind_externs: ArenaIndexMap::from_iter_in(
                    monouts.kind_externs.iter().map(|x| -> (&'i StructIT<'s, 'i, cI>, KindExternI<'s, 'i>) {
                        (x.r#struct, *x)
                    }),
                    self.interner.bump()),
                function_externs: self.interner.alloc_slice_from_vec(
                    non_generic_func_externs_c.into_iter().chain(monouts.function_externs.iter().copied()).collect()),
            };
        result_hinputs
    }

    // Rust adaptation (SPDMX): Scala's translateId[T <: INameT, Y <: INameI[sI]] is generic over the
    // narrow name type, but Rust collapsed IdT/IdI's name param into the wide INameT/INameI enums (see
    // IdI, names.rs:24-28). translate_id mirrors that collapse: `func` takes the wide &INameT and returns
    // the transient INameValI<sI>, which we intern to the permanent INameI. Takes &self for the interner.
    pub fn translate_id(
        &self,
        id_t: &IdT<'s, 't>,
        func: impl Fn(&INameT<'s, 't>) -> INameValI<'s, 'i, sI>,
    ) -> IdI<'s, 'i, sI> {
        let init_steps_i = id_t.init_steps.iter().map(Self::translate_name).collect::<Vec<_>>();
        IdI {
            package_coord: id_t.package_coord,
            init_steps: self.interner.alloc_slice_from_vec(init_steps_i),
            local_name: self.interner.intern_name_si(func(&id_t.local_name)),
        }
    }

    pub fn translate_export_name(_denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _export_name_t: &ExportNameT<'s, 't>) -> ExportNameI<'s, sI> {
        panic!("Unimplemented: translate_export_name");
    }

    pub fn translate_export_template_name(_export_template_name_t: &ExportTemplateNameT<'s>) -> ExportTemplateNameI<'s, sI> {
        panic!("Unimplemented: translate_export_template_name");
    }

    pub fn translate_name(_t: &INameT<'s, 't>) -> INameI<'s, 'i, sI> {
        panic!("Unimplemented: translate_name");
    }

    pub fn collapse_and_translate_interface_definition(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _interface_id_t: &IdT<'s, 't>, _interface_id_s: &IdI<'s, 'i, sI>, _instantiation_bound_args: &InstantiationBoundArgumentsI<'s, 'i>) {
        let interface_def_t = self.find_interface(_interface_id_t);
        let denizen_bound_to_denizen_caller_supplied_thing = Self::assemble_instantiation_bound_param_to_arg(&interface_def_t.instantiation_bound_params, _instantiation_bound_args);
        if let Some(x) = _monouts.interface_to_bounds.get(_interface_id_s) {
            assert!(*x == denizen_bound_to_denizen_caller_supplied_thing, "vcurious: interface_to_bounds mismatch");
        }
        _monouts.interface_to_bounds.insert(*_interface_id_s, denizen_bound_to_denizen_caller_supplied_thing.clone());
        let substitutions = self.assemble_placeholder_map(&interface_def_t.instantiated_interface.id, _interface_id_s);
        let interface_id_c = collapse_interface_id(self.interner, _interface_id_s);
        self.translate_collapsed_interface_definition(_monouts, _interface_id_t, &denizen_bound_to_denizen_caller_supplied_thing, &substitutions, &interface_id_c, interface_def_t);
    }

    pub fn assemble_instantiation_bound_param_to_arg(instantiation_bound_params: &InstantiationBoundArgumentsT<'s, 't>, instantiation_bound_args: &InstantiationBoundArgumentsI<'s, 'i>) -> DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i> {
        assert!(instantiation_bound_args.rune_to_function_bound_arg.len() == instantiation_bound_params.rune_to_bound_prototype.len());
        assert!(
            instantiation_bound_args.caller_rune_to_callee_rune_to_reachable_func.iter().filter(|(_, v)| !v.is_empty()).count() ==
                instantiation_bound_params.rune_to_citizen_rune_to_reachable_prototype.iter().filter(|(_, v)| !v.citizen_rune_to_reachable_prototype.is_empty()).count());
        assert!(instantiation_bound_args.rune_to_impl_bound_arg.len() == instantiation_bound_params.rune_to_bound_impl.len());
        DenizenBoundToDenizenCallerBoundArgI {
            func_id_to_bound_arg_prototype:
                instantiation_bound_args.rune_to_function_bound_arg.iter().map(|(callee_rune, supplied_function_i)| -> (IdT<'s, 't>, &'i PrototypeI<'s, 'i, sI>) {
                    (instantiation_bound_params.rune_to_bound_prototype.get(callee_rune).expect("vassertSome: rune_to_bound_prototype").id, *supplied_function_i)
                }).chain(
                    instantiation_bound_args.caller_rune_to_callee_rune_to_reachable_func.iter().flat_map(|(caller_rune, callee_rune_to_reachable_func)| -> Vec<(IdT<'s, 't>, &'i PrototypeI<'s, 'i, sI>)> {
                        if !callee_rune_to_reachable_func.is_empty() {
                            let m = instantiation_bound_params.rune_to_citizen_rune_to_reachable_prototype.get(caller_rune).expect("vassertSome: rune_to_citizen_rune_to_reachable_prototype");
                            assert!(m.citizen_rune_to_reachable_prototype.len() == callee_rune_to_reachable_func.len());
                            callee_rune_to_reachable_func.iter().map(|(callee_rune, reachable_func_i)| {
                                let reachable_func_t = m.citizen_rune_to_reachable_prototype.get(callee_rune).expect("vassertSome: citizen_rune_to_reachable_prototype");
                                (reachable_func_t.id, *reachable_func_i)
                            }).collect()
                        } else {
                            Vec::new()
                        }
                    })
                ).collect(),
            bound_param_impl_id_to_bound_arg_impl_id:
                instantiation_bound_args.rune_to_impl_bound_arg.iter().map(|(callee_rune, supplied_impl_t)| -> (IdT<'s, 't>, IdI<'s, 'i, sI>) {
                    (*instantiation_bound_params.rune_to_bound_impl.get(callee_rune).expect("vassertSome: rune_to_bound_impl"), *supplied_impl_t)
                }).collect(),
        }
    }

    pub fn assemble_callee_denizen_function_bounds(_callee_rune_to_receiver_bound_t: &IndexMap<IRuneS<'s>, IdT<'s, 't>>, _callee_rune_to_supplied_prototype: &IndexMap<IRuneS<'s>, PrototypeI<'s, 'i, sI>>) -> IndexMap<IdT<'s, 't>, PrototypeI<'s, 'i, sI>> {
        panic!("Unimplemented: assemble_callee_denizen_function_bounds");
    }

    pub fn assemble_callee_denizen_impl_bounds(_callee_rune_to_receiver_bound_t: &IndexMap<IRuneS<'s>, IdT<'s, 't>>, _callee_rune_to_supplied_impl: &IndexMap<IRuneS<'s>, IdI<'s, 'i, sI>>) -> IndexMap<IdT<'s, 't>, IdI<'s, 'i, sI>> {
        panic!("Unimplemented: assemble_callee_denizen_impl_bounds");
    }

    pub fn collapse_and_translate_struct_definition(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _struct_id_t: &IdT<'s, 't>, _struct_id_s: &IdI<'s, 'i, sI>, _instantiation_bound_args: &InstantiationBoundArgumentsI<'s, 'i>) {
        let struct_def_t = self.find_struct(_struct_id_t);
        let denizen_bound_to_denizen_caller_supplied_thing =
            Self::assemble_instantiation_bound_param_to_arg(&struct_def_t.instantiation_bound_params, _instantiation_bound_args);
        match _monouts.struct_to_bounds.get(_struct_id_s) {
            Some(_x) => {
                return;
            }
            None => {}
        }
        _monouts.struct_to_bounds.insert(*_struct_id_s, denizen_bound_to_denizen_caller_supplied_thing.clone());
        let substitutions = self.assemble_placeholder_map(&struct_def_t.instantiated_citizen.id, _struct_id_s);
        let struct_id_c = collapse_struct_id(self.interner, _struct_id_s);
        self.translate_collapsed_struct_definition(_monouts, _struct_id_t, &denizen_bound_to_denizen_caller_supplied_thing, &substitutions, _struct_id_t, &struct_id_c, struct_def_t);
    }

    pub fn find_struct(&self, _struct_id: &IdT<'s, 't>) -> &'t StructDefinitionT<'s, 't> {
        let target = Compiler::get_super_template(self.typing_interner, *_struct_id);
        let matches: Vec<_> = self.hinputs.structs.iter().filter(|s| Compiler::get_super_template(self.typing_interner, s.instantiated_citizen.id) == target).collect();
        assert_eq!(matches.len(), 1);
        matches[0]
    }

    pub fn find_interface(&self, _interface_id: &IdT<'s, 't>) -> &'t InterfaceDefinitionT<'s, 't> {
        let target = Compiler::get_super_template(self.typing_interner, *_interface_id);
        let matches: Vec<_> = self.hinputs.interfaces.iter().filter(|i| Compiler::get_super_template(self.typing_interner, i.instantiated_interface.id) == target).collect();
        assert_eq!(matches.len(), 1);
        matches[0]
    }

    pub fn find_impl(&self, _impl_id: &IdT<'s, 't>) -> &'t EdgeT<'s, 't> {
        panic!("Unimplemented: find_impl");
    }

    pub fn translate_override(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, impl_id_t: &IdT<'s, 't>, _impl_id_c: &IdI<'s, 'i, cI>, abstract_func_prototype_t: &PrototypeT<'s, 't>, _abstract_func_prototype_c: &PrototypeI<'s, 'i, cI>, _abstract_func_instantiation_bound_args: &InstantiationBoundArgumentsI<'s, 'i>) {
        let impl_template_id = Compiler::get_impl_template(self.typing_interner, *impl_id_t);
        let edge_t = vassert_one(
            self.hinputs.interface_to_sub_citizen_to_edge.values()
                .flat_map(|sub_to_edge| sub_to_edge.values().copied())
                .filter(|edge| Compiler::get_impl_template(self.typing_interner, edge.edge_id) == impl_template_id));
        let _edge_id = edge_t.edge_id;
        let _edge_sub_citizen = edge_t.sub_citizen;
        let _edge_super_interface = edge_t.super_interface;
        let edge_abstract_func_to_override_func = &edge_t.abstract_func_to_override_func;
        let abstract_func_template_name = Compiler::get_function_template(self.typing_interner, abstract_func_prototype_t.id);
        let abstract_func_placeholdered_name_t = self.hinputs.functions.iter().copied()
            .find(|func| Compiler::get_function_template(self.typing_interner, func.header.id) == abstract_func_template_name)
            .expect("vassertSome abstractFuncPlaceholderedNameT")
            .header.id;
        let override_t = *edge_abstract_func_to_override_func.get(&abstract_func_placeholdered_name_t).expect("vassertSome OverrideT");
        let dispatcher_id_t = override_t.dispatcher_call_id;
        let _impl_placeholder_to_dispatcher_placeholder = override_t.impl_placeholder_to_dispatcher_placeholder;
        let _impl_placeholder_to_case_placeholder = override_t.impl_placeholder_to_case_placeholder;
        let _dispatcher_and_case_placeholdered_impl_reachable_prototypes = &override_t.dispatcher_and_case_placeholdered_impl_reachable_prototypes;
        let _dispatcher_case_id_t = override_t.case_id;
        let _override_prototype_t = override_t.override_prototype;
        let _dispatcher_instantiation_bound_params = override_t.dispatcher_instantiation_bound_params;
        let _dispatcher_template_id = Compiler::get_template(self.typing_interner, dispatcher_id_t);
        let dispatcher_template_args = IInstantiationNameT::try_from(dispatcher_id_t.local_name).unwrap().template_args();
        let dispatcher_placeholder_id_to_supplied_templata: Vec<(IdT<'s, 't>, ITemplataI<'s, 'i, sI>)> =
            dispatcher_template_args.iter().map(|dispatcher_placeholder_templata| {
                let dispatcher_placeholder_id = Compiler::get_placeholder_templata_id(*dispatcher_placeholder_templata);
                let impl_placeholder = _impl_placeholder_to_dispatcher_placeholder.iter().find(|(_, v)| v == dispatcher_placeholder_templata).expect("vassertSome implPlaceholderToDispatcherPlaceholder").0;
                let index = match impl_placeholder.local_name {
                    INameT::KindPlaceholder(kp) => kp.template.index,
                    INameT::NonKindNonRegionPlaceholder(nk) => nk.index,
                    _ => panic!("vwat translate_override dispatcher placeholder index"),
                };
                let impl_id_c_local: IImplNameI<'s, 'i, cI> = _impl_id_c.local_name.try_into().unwrap();
                let templata_c = impl_id_c_local.template_args()[index as usize];
                let templata_s: ITemplataI<'s, 'i, sI> = unsafe { transmute(templata_c) };
                (dispatcher_placeholder_id, templata_s)
            }).collect();
        let case_local_name = match _dispatcher_case_id_t.local_name {
            INameT::OverrideDispatcherCase(n) => n,
            _ => panic!("translate_override: dispatcher_case_id_t.local_name not OverrideDispatcherCase"),
        };
        let dispatcher_case_placeholder_id_to_supplied_templata: Vec<(IdT<'s, 't>, ITemplataI<'s, 'i, sI>)> =
            case_local_name.independent_impl_template_args.iter().enumerate().map(|(_enum_index, case_placeholder_templata)| {
                let case_placeholder_id = Compiler::get_placeholder_templata_id(*case_placeholder_templata);
                let impl_placeholder = _impl_placeholder_to_case_placeholder.iter().find(|(_, v)| v == case_placeholder_templata).expect("vassertSome implPlaceholderToCasePlaceholder").0;
                let index = match impl_placeholder.local_name {
                    INameT::KindPlaceholder(kp) => kp.template.index,
                    _ => panic!("vwat translate_override case placeholder index"),
                };
                let impl_id_c_local: IImplNameI<'s, 'i, cI> = _impl_id_c.local_name.try_into().unwrap();
                let templata_c = impl_id_c_local.template_args()[index as usize];
                let templata_s: ITemplataI<'s, 'i, sI> = unsafe { transmute(templata_c) };
                (case_placeholder_id, templata_s)
            }).collect();
        let dispatcher_placeholder_id_to_supplied_templata_map: HashMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>> =
            dispatcher_placeholder_id_to_supplied_templata.iter().copied().collect();
        let dispatcher_case_placeholder_id_to_supplied_templata_map: HashMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>> =
            dispatcher_case_placeholder_id_to_supplied_templata.iter().copied().collect();
        assert!(dispatcher_placeholder_id_to_supplied_templata_map.len() + dispatcher_case_placeholder_id_to_supplied_templata_map.len() ==
            dispatcher_placeholder_id_to_supplied_templata_map.iter().chain(dispatcher_case_placeholder_id_to_supplied_templata_map.iter()).map(|(k, _)| *k).collect::<HashSet<_>>().len());
        let mut _case_substitutions: HashMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>> = dispatcher_placeholder_id_to_supplied_templata_map.clone();
        _case_substitutions.extend(dispatcher_case_placeholder_id_to_supplied_templata_map.iter().map(|(k, v)| (*k, *v)));

        let impl_rune_to_impl_instantiation_bound_args = &_monouts.impls.get(_impl_id_c).expect("vassertSome monouts.impls").3;
        let _bound_param_prototype_t_to_bound_arg_prototype_i_from_impl: HashMap<IdT<'s, 't>, &'i PrototypeI<'s, 'i, sI>> =
            _dispatcher_and_case_placeholdered_impl_reachable_prototypes.iter().flat_map(|(rune_in_impl, citizen_rune_to_bound)| {
                citizen_rune_to_bound.iter().map(move |(rune_in_citizen, prototype_t)| {
                    let INameT::FunctionBound(_fbn) = prototype_t.id.local_name else {
                        panic!("translate_override: prototype_t.id.local_name not FunctionBound");
                    };
                    let prototype_i = *impl_rune_to_impl_instantiation_bound_args.caller_rune_to_callee_rune_to_reachable_func
                        .get(rune_in_impl).expect("vassertSome rune_in_impl")
                        .get(rune_in_citizen).expect("vassertSome rune_in_citizen");
                    (prototype_t.id, prototype_i)
                })
            }).collect();
        let dispatcher_instantiation_bound_params_to_args = Self::assemble_instantiation_bound_param_to_arg(_dispatcher_instantiation_bound_params, _abstract_func_instantiation_bound_args);

        let mut bound_param_func_id_to_bound_arg_index_map: IndexMap<IdT<'s, 't>, &'i PrototypeI<'s, 'i, sI>> = IndexMap::new();
        for (k, v) in _bound_param_prototype_t_to_bound_arg_prototype_i_from_impl.iter() {
            bound_param_func_id_to_bound_arg_index_map.insert(*k, *v);
        }
        let extra_bounds = DenizenBoundToDenizenCallerBoundArgI {
            func_id_to_bound_arg_prototype: bound_param_func_id_to_bound_arg_index_map,
            bound_param_impl_id_to_bound_arg_impl_id: IndexMap::new(),
        };
        let case_instantiation_bound_params_to_args = dispatcher_instantiation_bound_params_to_args.plus(&extra_bounds);

        let case_substitutions_idx: IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>> = _case_substitutions.iter().map(|(k, v)| (*k, *v)).collect();
        let (_override_prototype_s, override_prototype_c) =
            self.translate_prototype(_monouts, &_dispatcher_case_id_t, &case_instantiation_bound_params_to_args, &case_substitutions_idx, &RegionT { region: IRegionT::Default }, &_override_prototype_t);

        let super_interface_id = _monouts.impls.get(_impl_id_c).expect("vassertSome monouts.impls").1;
        _monouts.add_method_to_v_table(*_impl_id_c, super_interface_id, *_abstract_func_prototype_c, override_prototype_c);
    }

    pub fn translate_impl(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _impl_id_t: &IdT<'s, 't>, _impl_id_n: &IdI<'s, 'i, nI>, _instantiation_bounds_for_unsubstituted_impl: InstantiationBoundArgumentsI<'s, 'i>) {
        // This works because the sI/cI are never actually used in these instances, they are just a
        // compile-time type-system bit of tracking, see CCFCTS.
        let impl_id_s: &IdI<'s, 'i, sI> = unsafe { &*(_impl_id_n as *const IdI<'s, 'i, nI> as *const IdI<'s, 'i, sI>) };
        let impl_id_c = collapse_impl_id(self.interner, impl_id_s);

        let impl_template_id = Compiler::get_impl_template(self.typing_interner, *_impl_id_t);
        let impl_definition = vassert_one(self.hinputs.interface_to_sub_citizen_to_edge.iter().flat_map(|(_, m)| m.values()).filter(|edge| {
            Compiler::get_impl_template(self.typing_interner, edge.edge_id) == impl_template_id
        }));

        let denizen_bound_to_denizen_caller_supplied_thing = Self::assemble_instantiation_bound_param_to_arg(&impl_definition.instantiation_bound_params, &_instantiation_bounds_for_unsubstituted_impl);
        let substitutions = self.assemble_placeholder_map(&impl_definition.edge_id, impl_id_s);
        self.translate_collapsed_impl_definition(_monouts, _impl_id_t, _instantiation_bounds_for_unsubstituted_impl, &denizen_bound_to_denizen_caller_supplied_thing, &substitutions, _impl_id_t, impl_id_s, &impl_id_c, impl_definition);
    }

    pub fn translate_function(&self, monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, desired_prototype_t: &PrototypeT<'s, 't>, desired_prototype_n: &PrototypeI<'s, 'i, nI>, _supplied_bound_args: &InstantiationBoundArgumentsI<'s, 'i>, _maybe_denizen_bound_to_denizen_caller_supplied_thing: Option<&DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>>) -> &'i FunctionDefinitionI<'s, 'i> {
        // This works because the sI/cI are never actually used in these instances, they are just a
        // compile-time type-system bit of tracking, see CCFCTS.
        let desired_prototype_s: &PrototypeI<'s, 'i, sI> = desired_prototype_n;
        let desired_prototype_c =
            region_collapser_individual::collapse_prototype(self.interner, desired_prototype_s);

        let desired_func_super_template_name = Compiler::get_super_template(self.typing_interner, desired_prototype_t.id);
        let func_t =
            vassert_one(self.hinputs.functions.iter().filter(|func_t| {
                Compiler::get_super_template(self.typing_interner, func_t.header.id) == desired_func_super_template_name
            }));

        let denizen_bound_to_denizen_caller_supplied_thing_from_denizen_itself =
            match _maybe_denizen_bound_to_denizen_caller_supplied_thing {
                Some(x) => x.clone(),
                None => Self::assemble_instantiation_bound_param_to_arg(&func_t.instantiation_bound_params, _supplied_bound_args),
            };
        let _args_m: Vec<_> = IFunctionNameI::try_from(desired_prototype_s.id.local_name).unwrap().parameters().iter().map(|c| c.kind).collect();
        let _params_t: Vec<_> = func_t.header.params.iter().map(|p| p.tyype.kind).collect();

        let denizen_bound_to_denizen_caller_supplied_thing_from_denizen_itself_and_params =
            denizen_bound_to_denizen_caller_supplied_thing_from_denizen_itself;

        let denizen_bound_to_denizen_caller_supplied_thing =
            denizen_bound_to_denizen_caller_supplied_thing_from_denizen_itself_and_params;

        let substitutions =
            self.assemble_placeholder_map(&func_t.header.id, &desired_prototype_s.id);

        let monomorphized_func_t =
            self.translate_collapsed_function(
                monouts, &desired_prototype_t.id, &denizen_bound_to_denizen_caller_supplied_thing, &substitutions, &desired_prototype_c, func_t);

        assert!(desired_prototype_c.return_type == monomorphized_func_t.header.return_type);

        monomorphized_func_t
    }

    pub fn translate_abstract_func(&self, monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, interface_id_c: &IdI<'s, 'i, cI>, desired_abstract_prototype_t: &PrototypeT<'s, 't>, desired_abstract_prototype_n: &PrototypeI<'s, 'i, nI>, virtual_index: usize, supplied_bound_args: InstantiationBoundArgumentsI<'s, 'i>) {
        // sI/cI/nI are compile-time tracking only, see CCFCTS.
        let desired_abstract_prototype_s: PrototypeI<'s, 'i, sI> = unsafe { transmute(*desired_abstract_prototype_n) };
        let desired_abstract_prototype_c =
            collapse_prototype(self.interner, &desired_abstract_prototype_s);

        let desired_super_template_id = Compiler::get_super_template(self.typing_interner, desired_abstract_prototype_t.id);
        let func_t = vassert_one(self.hinputs.functions.iter().copied().filter(|f| {
            Compiler::get_super_template(self.typing_interner, f.header.id) == desired_super_template_id
        }));

        let denizen_bound_to_denizen_caller_supplied_thing_from_denizen_itself =
            Self::assemble_instantiation_bound_param_to_arg(&func_t.instantiation_bound_params, &supplied_bound_args);

        let _args_m: Vec<KindIT<'s, 'i, sI>> = IFunctionNameI::try_from(desired_abstract_prototype_s.id.local_name).unwrap().parameters().iter().map(|c| c.kind).collect();
        let _params_t: Vec<KindT<'s, 't>> = func_t.header.params.iter().map(|p| p.tyype.kind).collect();

        let denizen_bound_to_denizen_caller_supplied_thing = denizen_bound_to_denizen_caller_supplied_thing_from_denizen_itself;

        assert!(!monouts.abstract_func_to_bounds.contains_key(&desired_abstract_prototype_c.id));
        let supplied_bound_args_ref: &'i InstantiationBoundArgumentsI<'s, 'i> = self.interner.bump().alloc(supplied_bound_args);
        monouts.abstract_func_to_bounds.insert(desired_abstract_prototype_c.id, (denizen_bound_to_denizen_caller_supplied_thing, supplied_bound_args_ref));

        let abstract_funcs = monouts.interface_to_abstract_func_to_virtual_index.get_mut(interface_id_c).expect("vassertSome interface_to_abstract_func_to_virtual_index");
        assert!(!abstract_funcs.contains_key(&desired_abstract_prototype_c));
        abstract_funcs.insert(desired_abstract_prototype_c, virtual_index);

        let impls = monouts.interface_to_impls.get(interface_id_c).expect("vassertSome interface_to_impls").clone();
        for (impl_t, impl_) in impls.iter() {
            self.translate_override(monouts, impl_t, impl_, desired_abstract_prototype_t, &desired_abstract_prototype_c, supplied_bound_args_ref);
        }
    }

    pub fn assemble_placeholder_map(&self, id_t: &IdT<'s, 't>, id_s: &IdI<'s, 'i, sI>) -> IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>> {
        let mut result: IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>> = match id_t.init_non_package_id(self.typing_interner) {
            None => IndexMap::new(),
            Some(init_non_package_id_t) => {
                self.assemble_placeholder_map(&init_non_package_id_t, &id_s.init_non_package_id().unwrap())
            }
        };
        match IInstantiationNameT::try_from(id_t.local_name) {
            Ok(_local_name_t) => {
                let instantiation_id_t = id_t;
                let instantiation_id_s =
                    match IInstantiationNameI::try_from(id_s.local_name) {
                        Ok(_) => id_s,
                        Err(_) => {
                            // We could get here if, for example, idT is an instantiation like Vec<int> and idS is a template Vec.
                            panic!("vwat")
                        }
                    };
                let inner = self.assemble_placeholder_map_inner(instantiation_id_t, instantiation_id_s);
                result.extend(inner);
            }
            Err(_) => {}
        }
        result
    }

    pub fn assemble_placeholder_map_inner(&self, id_t: &IdT<'s, 't>, id_s: &IdI<'s, 'i, sI>) -> IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>> {
        let placeholdered_name = id_t;
        IInstantiationNameT::try_from(placeholdered_name.local_name).unwrap().template_args()
            .iter()
            .zip(IInstantiationNameI::try_from(id_s.local_name).unwrap().template_args(self.interner).iter())
            .flat_map(|(template_arg_t, template_arg_i)| -> Vec<(IdT<'s, 't>, ITemplataI<'s, 'i, sI>)> {
                match (template_arg_t, template_arg_i) {
                    (ITemplataT::Coord(ct), c @ ITemplataI::Coord(_)) => {
                        match ct.coord.kind {
                            KindT::KindPlaceholder(kp) => vec![(kp.id, *c)],
                            _ => vec![],
                        }
                    }
                    (ITemplataT::Kind(kt), kind_templata_i) => {
                        match kt.kind {
                            KindT::KindPlaceholder(kp) => vec![(kp.id, *kind_templata_i)],
                            _ => panic!("assemble_placeholder_map_inner: KindTemplataT non-placeholder arm"),
                        }
                    }
                    (ITemplataT::Placeholder(pt), templata_i) => vec![(pt.id, *templata_i)],
                    (ITemplataT::Mutability(mt), ITemplataI::Mutability(mi)) if matches!(mt.mutability, MutabilityT::Mutable) && matches!(mi.mutability, MutabilityI::Mutable) => vec![],
                    (ITemplataT::Mutability(mt), ITemplataI::Mutability(mi)) if matches!(mt.mutability, MutabilityT::Immutable) && matches!(mi.mutability, MutabilityI::Immutable) => vec![],
                    _ => panic!("assemble_placeholder_map_inner: unimplemented arm"),
                }
            })
            .collect()
    }

    pub fn translate_struct_member(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _member: &IStructMemberT<'s, 't>) -> (CoordI<'s, 'i, sI>, StructMemberI<'s, 'i, cI>) {
        match _member {
            IStructMemberT::Normal(n) => {
                let NormalStructMemberT { name, variability, tyype } = n;
                let (member_subjective_it, member_type_i) = match tyype {
                    IMemberTypeT::Reference(r) => {
                        let type_s = self.translate_coord(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &r.reference);
                        let result_ref = ReferenceMemberTypeI {
                            reference: collapse_coord(self.interner, &type_s.coord),
                            _marker: PhantomData,
                        };
                        let result_ref: &'i ReferenceMemberTypeI<'s, 'i, cI> = self.interner.bump().alloc(result_ref);
                        (type_s.coord, IMemberTypeI::ReferenceMemberTypeI(result_ref))
                    }
                    IMemberTypeT::Address(a) => {
                        let type_s = self.translate_coord(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &a.reference);
                        let result = AddressMemberTypeI {
                            reference: collapse_coord(self.interner, &type_s.coord),
                            _marker: PhantomData,
                        };
                        let result: &'i AddressMemberTypeI<'s, 'i, cI> = self.interner.bump().alloc(result);
                        (type_s.coord, IMemberTypeI::AddressMemberTypeI(result))
                    }
                };
                let name_s = Self::translate_var_name(self.interner, name);
                let member_c = StructMemberI {
                    name: collapse_var_name(self.interner, &name_s),
                    variability: Self::translate_variability(variability),
                    tyype: member_type_i,
                };
                (member_subjective_it, member_c)
            }
            IStructMemberT::Variadic(_) => panic!("Unimplemented: translate_struct_member Variadic"),
        }
    }

    pub fn translate_variability(x: &VariabilityT) -> VariabilityI {
        match x {
            VariabilityT::Varying => VariabilityI::Varying,
            VariabilityT::Final => VariabilityI::Final,
        }
    }

    pub fn translate_mutability(_m: &MutabilityT) -> MutabilityI {
        match _m {
            MutabilityT::Mutable => MutabilityI::Mutable,
            MutabilityT::Immutable => MutabilityI::Immutable,
        }
    }

    pub fn translate_prototype(&self, monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, denizen_name: &IdT<'s, 't>, denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, perspective_region_t: &RegionT, desired_prototype_t: &PrototypeT<'s, 't>) -> (PrototypeI<'s, 'i, sI>, PrototypeI<'s, 'i, cI>) {
        let PrototypeT { id: desired_prototype_id_unsubstituted, return_type: desired_prototype_return_type_unsubstituted } = desired_prototype_t;

        let rune_to_bound_args_for_call =
            self.translate_bound_args_for_callee(
                monouts,
                denizen_name,
                denizen_bound_to_denizen_caller_supplied_thing,
                substitutions,
                perspective_region_t,
                self.hinputs.get_instantiation_bound_args(desired_prototype_t.id));

        let return_subjective_it =
            self.translate_coord(
                monouts,
                denizen_name,
                denizen_bound_to_denizen_caller_supplied_thing,
                substitutions,
                perspective_region_t,
                desired_prototype_return_type_unsubstituted);

        let desired_prototype_s =
            self.interner.intern_prototype_si(PrototypeIValI {
                id: self.translate_function_id(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, desired_prototype_id_unsubstituted),
                return_type: return_subjective_it.coord,
            });

        match desired_prototype_t.id {
            IdT { local_name: INameT::FunctionBound(_), .. } => {
                let func_bound_name = desired_prototype_t.id;
                let prototype_s = *denizen_bound_to_denizen_caller_supplied_thing.func_id_to_bound_arg_prototype.get(&func_bound_name).expect("vassertSome: func_id_to_bound_arg_prototype");
                let prototype_c = region_collapser_individual::collapse_prototype(self.interner, prototype_s);
                (*prototype_s, prototype_c)
            }
            IdT { local_name: INameT::ExternFunction(_), .. } => {
                if self.opts.sanity_check {
                    // Scala's `vassert(Collector.all(desiredPrototypeS, {case KindPlaceholderTemplateNameT => }).isEmpty)`
                    // sanity check is omitted: the I-side AST is statically typed and can't structurally hold a
                    // typing-pass placeholder template name, so it's vacuously empty. (Architect-approved parity gap.)
                }
                let desired_prototype_c =
                    region_collapser_individual::collapse_prototype(self.interner, desired_prototype_s);
                (*desired_prototype_s, desired_prototype_c)
            }
            IdT { local_name: last, .. } => {
                match last {
                    INameT::LambdaCallFunction(_) => {
                        // Lambdas Can Call Sibling Lambdas (LCCSL)
                        // If we want to call a lambda, there are three possibilities I've seen:
                        // - We're in the root denizen and we want to call our own lambda.
                        // - We're in a lambda and we want to call an even deeper lambda.
                        // - (This is the weird one) we want to call a *sibling* lambda.
                        // In all cases, make sure the denizen roots of everyone agree.
                        let denizen_root_super_template = Compiler::get_root_super_template(self.typing_interner, *denizen_name);
                        let desired_prototype_root_super_template = Compiler::get_root_super_template(self.typing_interner, desired_prototype_t.id);
                        assert!(denizen_root_super_template == desired_prototype_root_super_template);
                    }
                    _ => {}
                }

                let desired_prototype_c =
                    region_collapser_individual::collapse_prototype(self.interner, desired_prototype_s);
                let desired_prototype_n =
                    region_collapser_consistent::collapse_prototype(
                        self.interner,
                        region_counter::count_prototype_map(desired_prototype_s),
                        desired_prototype_s);

                assert!(region_collapser_individual::collapse_prototype(self.interner, &desired_prototype_n) == desired_prototype_c);

                // If we're instantiating something whose name starts with our name, then we're instantiating our lambda.
                let maybe_denizen_bound_to_denizen_caller_supplied_thing =
                    if Compiler::get_super_template(self.typing_interner, desired_prototype_t.id).steps()
                        .starts_with(&Compiler::get_super_template(self.typing_interner, *denizen_name).steps()) {
                        // We need to supply our bounds to our lambdas, see LCCPGB and LCNBAFA.
                        Some(denizen_bound_to_denizen_caller_supplied_thing.clone())
                    } else {
                        if self.opts.sanity_check {
                            let desired_func_super_template_name = Compiler::get_super_template(self.typing_interner, desired_prototype_t.id);
                            let func_t =
                                vassert_one(self.hinputs.functions.iter().filter(|func_t| {
                                    Compiler::get_super_template(self.typing_interner, func_t.header.id) == desired_func_super_template_name
                                }));
                            assert!(rune_to_bound_args_for_call.rune_to_function_bound_arg.len() == func_t.instantiation_bound_params.rune_to_bound_prototype.len());
                            assert!(
                                rune_to_bound_args_for_call.caller_rune_to_callee_rune_to_reachable_func.iter().filter(|(_, v)| !v.is_empty()).count() ==
                                    func_t.instantiation_bound_params.rune_to_citizen_rune_to_reachable_prototype.iter().filter(|(_, v)| !v.citizen_rune_to_reachable_prototype.is_empty()).count());
                            assert!(rune_to_bound_args_for_call.rune_to_impl_bound_arg.len() == func_t.instantiation_bound_params.rune_to_bound_impl.len());
                        }
                        None
                    };
                monouts.new_functions.push((
                    *desired_prototype_t,
                    desired_prototype_n,
                    rune_to_bound_args_for_call,
                    maybe_denizen_bound_to_denizen_caller_supplied_thing,
                ));
                (*desired_prototype_s, desired_prototype_c)
            }
        }
    }

    pub fn translate_bound_args_for_callee(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, instantiation_bound_args_for_call_unsubstituted: &InstantiationBoundArgumentsT<'s, 't>) -> InstantiationBoundArgumentsI<'s, 'i> {
        let rune_to_supplied_bound_prototype_for_call_unsubstituted =
            &instantiation_bound_args_for_call_unsubstituted.rune_to_bound_prototype;
        // For any that are placeholders themselves, let's translate those into actual prototypes.
        let rune_to_supplied_prototype_for_call: ArenaIndexMap<'i, IRuneS<'s>, &'i PrototypeI<'s, 'i, sI>> =
            ArenaIndexMap::from_iter_in(
                rune_to_supplied_bound_prototype_for_call_unsubstituted.iter().map(|(rune, supplied_prototype_unsubstituted)| {
                    let prototype_s: &'i PrototypeI<'s, 'i, sI> = match supplied_prototype_unsubstituted.id {
                        IdT { local_name: INameT::FunctionBound(_), .. } => {
                            let func_bound_name = supplied_prototype_unsubstituted.id;
                            *_denizen_bound_to_denizen_caller_supplied_thing.func_id_to_bound_arg_prototype.get(&func_bound_name).expect("vassertSome: func_id_to_bound_arg_prototype")
                        }
                        _ => {
                            let (prototype_i, _prototype_c) =
                                self.translate_prototype(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, supplied_prototype_unsubstituted);
                            self.interner.alloc(prototype_i)
                        }
                    };
                    (*rune, prototype_s)
                }),
                self.interner.bump());
        // And now we have a map from the callee's rune to the *instantiated* callee's prototypes.

        let caller_rune_to_callee_rune_to_supplied_reachable_prototype_for_call_unsubstituted =
            &instantiation_bound_args_for_call_unsubstituted.rune_to_citizen_rune_to_reachable_prototype;
        // For any that are placeholders themselves, let's translate those into actual prototypes.
        let rune_to_supplied_reachable_prototype_for_call: ArenaIndexMap<'i, IRuneS<'s>, ArenaIndexMap<'i, IRuneS<'s>, &'i PrototypeI<'s, 'i, sI>>> =
            ArenaIndexMap::from_iter_in(
                caller_rune_to_callee_rune_to_supplied_reachable_prototype_for_call_unsubstituted.iter().map(|(caller_rune, callee_rune_to_supplied_reachable_prototype_for_call_unsubstituted)| {
                    let inner: ArenaIndexMap<'i, IRuneS<'s>, &'i PrototypeI<'s, 'i, sI>> =
                        ArenaIndexMap::from_iter_in(
                            callee_rune_to_supplied_reachable_prototype_for_call_unsubstituted.citizen_rune_to_reachable_prototype.iter().map(|(callee_rune, supplied_reachable_prototype_for_call_unsubstituted)| {
                                let prototype_i: &'i PrototypeI<'s, 'i, sI> = match supplied_reachable_prototype_for_call_unsubstituted.id {
                                    IdT { local_name: INameT::FunctionBound(_), .. } => {
                                        let func_bound_name = supplied_reachable_prototype_for_call_unsubstituted.id;
                                        *_denizen_bound_to_denizen_caller_supplied_thing.func_id_to_bound_arg_prototype.get(&func_bound_name).expect("vassertSome: func_id_to_bound_arg_prototype")
                                    }
                                    _ => {
                                        let (prototype_i, _prototype_c) =
                                            self.translate_prototype(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, supplied_reachable_prototype_for_call_unsubstituted);
                                        self.interner.alloc(prototype_i)
                                    }
                                };
                                (*callee_rune, prototype_i)
                            }),
                            self.interner.bump());
                    (*caller_rune, inner)
                }),
                self.interner.bump());
        // And now we have a map from the callee's rune to the *instantiated* callee's prototypes.

        let rune_to_supplied_impl_for_call_unsubstituted =
            &instantiation_bound_args_for_call_unsubstituted.rune_to_bound_impl;
        // For any that are placeholders themselves, let's translate those into actual prototypes.
        let rune_to_supplied_impl_for_call: ArenaIndexMap<'i, IRuneS<'s>, IdI<'s, 'i, sI>> =
            ArenaIndexMap::from_iter_in(
                rune_to_supplied_impl_for_call_unsubstituted.iter().map(|(rune, supplied_impl_unsubstituted)| {
                    let impl_id_s = match supplied_impl_unsubstituted.local_name {
                        INameT::ImplBound(_) => {
                            *_denizen_bound_to_denizen_caller_supplied_thing.bound_param_impl_id_to_bound_arg_impl_id.get(supplied_impl_unsubstituted).expect("vassertSome bound_param_impl_id_to_bound_arg_impl_id")
                        }
                        _ => {
                            self.translate_impl_id(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, supplied_impl_unsubstituted)
                        }
                    };
                    (*rune, impl_id_s)
                }),
                self.interner.bump());
        // And now we have a map from the callee's rune to the *instantiated* callee's impls.

        InstantiationBoundArgumentsI {
            rune_to_function_bound_arg: rune_to_supplied_prototype_for_call,
            caller_rune_to_callee_rune_to_reachable_func: rune_to_supplied_reachable_prototype_for_call,
            rune_to_impl_bound_arg: rune_to_supplied_impl_for_call,
        }
    }

    pub fn translate_collapsed_struct_definition(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _new_id_t: &IdT<'s, 't>, _new_id: &IdI<'s, 'i, cI>, _struct_def_t: &StructDefinitionT<'s, 't>) {
        let StructDefinitionT { template_name: _, instantiated_citizen: _, attributes, weakable, mutability: mutability_t, members, is_closure, instantiation_bound_params: _ } = _struct_def_t;
        let perspective_region_t = RegionT { region: IRegionT::Default };
        let mutability = expect_mutability_templata(self.translate_templata(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, &perspective_region_t, mutability_t)).mutability;
        if _monouts.struct_to_mutability.contains_key(_new_id) {
            return;
        }
        _monouts.struct_to_mutability.insert(*_new_id, mutability);
        let attributes_i: Vec<ICitizenAttributeI<'s>> = attributes.iter().map(|a| Self::translate_citizen_attribute(a)).collect();
        let members_i: Vec<StructMemberI<'s, 'i, cI>> = members.iter().map(|m| {
            let (_, sm) = self.translate_struct_member(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, &perspective_region_t, m);
            sm
        }).collect();
        let result = StructDefinitionI {
            instantiated_citizen: self.interner.intern_struct_it_ci(StructITValI { id: *_new_id }),
            attributes: self.interner.bump().alloc_slice_fill_iter(attributes_i.into_iter()),
            weakable: *weakable,
            mutability,
            members: self.interner.bump().alloc_slice_fill_iter(members_i.into_iter()),
            is_closure: *is_closure,
            rune_to_function_bound: ArenaIndexMap::new_in(self.interner.bump()),
            rune_to_impl_bound: ArenaIndexMap::new_in(self.interner.bump()),
        };
        assert_eq!(result.instantiated_citizen.id, *_new_id);
        let result_ref: &'i StructDefinitionI<'s, 'i, cI> = self.interner.alloc(result);
        _monouts.structs.insert(result_ref.instantiated_citizen.id, result_ref);
        if result_ref.attributes.iter().any(|a| matches!(a, ICitizenAttributeI::ExternI(_))) {
            _monouts.kind_externs.push(KindExternI { r#struct: result_ref.instantiated_citizen });
        }
    }

    pub fn translate_collapsed_interface_definition(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _new_id_c: &IdI<'s, 'i, cI>, _interface_def_t: &InterfaceDefinitionT<'s, 't>) {
        if _monouts.interface_to_mutability.contains_key(_new_id_c) {
            return;
        }
        let InterfaceDefinitionT { template_name: _, instantiated_interface: _, ref_: _, attributes, weakable, mutability: mutability_t, instantiation_bound_params: _, internal_methods: _ } = _interface_def_t;
        assert!(!_monouts.interface_to_impl_to_abstract_prototype_to_override.contains_key(_new_id_c));
        _monouts.interface_to_impl_to_abstract_prototype_to_override.insert(*_new_id_c, IndexMap::new());
        assert!(!_monouts.interface_to_abstract_func_to_virtual_index.contains_key(_new_id_c));
        _monouts.interface_to_abstract_func_to_virtual_index.insert(*_new_id_c, IndexMap::new());
        assert!(!_monouts.interface_to_impls.contains_key(_new_id_c));
        _monouts.interface_to_impls.insert(*_new_id_c, Vec::new());
        let perspective_region_t = RegionT { region: IRegionT::Default };
        let mutability = expect_mutability_templata(self.translate_templata(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, &perspective_region_t, mutability_t)).mutability;
        assert!(!_monouts.interface_to_mutability.contains_key(_new_id_c));
        _monouts.interface_to_mutability.insert(*_new_id_c, mutability);
        let new_interface_it = self.interner.intern_interface_it_ci(InterfaceITValI { id: *_new_id_c });
        let attributes_i: Vec<ICitizenAttributeI<'s>> = attributes.iter().map(|a| Self::translate_citizen_attribute(a)).collect();
        let result = InterfaceDefinitionI {
            instantiated_interface: new_interface_it,
            attributes: self.interner.bump().alloc_slice_fill_iter(attributes_i.into_iter()),
            weakable: *weakable,
            mutability,
            rune_to_function_bound: ArenaIndexMap::new_in(self.interner.bump()),
            rune_to_impl_bound: ArenaIndexMap::new_in(self.interner.bump()),
            internal_methods: &[],
            _marker: PhantomData,
        };
        let result_ref: &'i InterfaceDefinitionI<'s, 'i, cI> = self.interner.alloc(result);
        _monouts.interfaces_without_methods.insert(result_ref.instantiated_interface.id, result_ref);
        assert_eq!(result_ref.instantiated_interface.id, *_new_id_c);
    }

    pub fn translate_function_header(&self, monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, denizen_name: &IdT<'s, 't>, denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, perspective_region_t: &RegionT, header_t: &FunctionHeaderT<'s, 't>) -> FunctionHeaderI<'s, 'i> {
        let new_id_s =
            self.translate_function_id(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &header_t.id);
        let new_id_c =
            region_collapser_individual::collapse_id(self.interner, &new_id_s, |x| INameI::from(region_collapser_individual::collapse_function_name(self.interner, &IFunctionNameI::try_from(*x).unwrap())));

        let return_it =
            self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &header_t.return_type);
        let return_ic = region_collapser_individual::collapse_coord(self.interner, &return_it.coord);

        let result =
            FunctionHeaderI {
                id: new_id_c,
                attributes: self.interner.alloc_slice_from_vec(header_t.attributes.iter().map(|a| Self::translate_function_attribute(a)).collect()),
                params: self.interner.alloc_slice_from_vec(header_t.params.iter().map(|p| self.translate_parameter(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, p)).collect()),
                return_type: return_ic,
            };

        result
    }

    pub fn translate_function_attribute(x: &IFunctionAttributeT<'s>) -> IFunctionAttributeI<'s> {
        match x {
            IFunctionAttributeT::UserFunction => IFunctionAttributeI::UserFunctionI,
            IFunctionAttributeT::Pure => IFunctionAttributeI::PureI,
            IFunctionAttributeT::Extern(e) => IFunctionAttributeI::ExternI(ExternI { package_coord: e.package_coord }),
            _ => panic!("Unimplemented: translate_function_attribute other"),
        }
    }

    pub fn translate_citizen_attribute(x: &ICitizenAttributeT<'s>) -> ICitizenAttributeI<'s> {
        match x {
            ICitizenAttributeT::Sealed => ICitizenAttributeI::SealedI,
            ICitizenAttributeT::Extern(extern_t) => ICitizenAttributeI::ExternI(ExternI { package_coord: extern_t.package_coord }),
        }
    }

    pub fn translate_collapsed_function(&self, monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, denizen_name: &IdT<'s, 't>, denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, desired_prototype_c: &PrototypeI<'s, 'i, cI>, function_t: &FunctionDefinitionT<'s, 't>) -> &'i FunctionDefinitionI<'s, 'i> {
        if self.opts.sanity_check {
            collector::all_in_substitutions(substitutions, &|node| -> Option<()> {
                if let NodeRefI::Templata(ITemplataI::Region(r)) = node {
                    if r.pure_height > 0 { panic!("vwat: substitutions contains RegionTemplataI(pure_height > 0)") }
                }
                None
            });
        }

        let perspective_region_t = RegionT { region: IRegionT::Default };
          // functionT.header.id.localName.templateArgs.last match {
          //   case PlaceholderTemplataT(IdT(packageCoord, initSteps, r @ RegionPlaceholderNameT(_, _, _, _)), RegionTemplataType()) => {
          //     IdT(packageCoord, initSteps, r)
          //   }
          //   case _ => vwat()
          // }

        let function_id_s =
            self.translate_function_id(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, &perspective_region_t, &function_t.header.id);
        let function_id_c =
            region_collapser_individual::collapse_function_id(self.interner, &function_id_s);

        match monouts.functions.get(&function_id_c) {
            Some(func) => return *func,
            None => {}
        }

        let new_header = self.translate_function_header(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, &perspective_region_t, function_t.header);

        if new_header.to_prototype(self.interner) != *desired_prototype_c {
            self.translate_function_header(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, &perspective_region_t, function_t.header);
            panic!("vfail");
        }

        let (_body_subjective_it, body_ce) =
            self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, &perspective_region_t, &function_t.body);

        let result: &'i FunctionDefinitionI<'s, 'i> =
            self.interner.alloc(FunctionDefinitionI {
                header: new_header,
                rune_to_func_bound: ArenaIndexMap::new_in(self.interner.bump()),
                rune_to_impl_bound: ArenaIndexMap::new_in(self.interner.bump()),
                body: body_ce,
            });

        monouts.functions.insert(result.header.id, result);
        result
    }

    pub fn translate_local_variable(&self, monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, denizen_name: &IdT<'s, 't>, denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, perspective_region_t: &RegionT, variable: &ILocalVariableT<'s, 't>) -> (CoordI<'s, 'i, sI>, ILocalVariableI<'s, 'i>) {
        match variable {
            ILocalVariableT::Reference(r) => {
                let (coord, local) =
                    self.translate_reference_local_variable(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, r);
                (coord, ILocalVariableI::ReferenceLocalVariableI(self.interner.alloc(local)))
            }
            ILocalVariableT::Addressible(a) => {
                let (coord, local) =
                    self.translate_addressible_local_variable(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, a);
                (coord, ILocalVariableI::AddressibleLocalVariableI(self.interner.alloc(local)))
            }
        }
    }

    pub fn translate_reference_local_variable(&self, monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, denizen_name: &IdT<'s, 't>, denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, perspective_region_t: &RegionT, variable: &ReferenceLocalVariableT<'s, 't>) -> (CoordI<'s, 'i, sI>, ReferenceLocalVariableI<'s, 'i>) {
        let ReferenceLocalVariableT { name: id, variability, coord } = variable;
        let coord_s =
            self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, coord);
        let var_name_s = Self::translate_var_name(self.interner, id);
        let local_c =
            ReferenceLocalVariableI {
                name: region_collapser_individual::collapse_var_name(self.interner, &var_name_s),
                variability: Self::translate_variability(variability),
                collapsed_coord: region_collapser_individual::collapse_coord(self.interner, &coord_s.coord),
            };
        (coord_s.coord, local_c)
    }

    pub fn translate_addressible_local_variable(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _variable: &AddressibleLocalVariableT<'s, 't>) -> (CoordI<'s, 'i, sI>, AddressibleLocalVariableI<'s, 'i>) {
        let AddressibleLocalVariableT { name: id, variability, coord } = _variable;
        let coord_s = self.translate_coord(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, &RegionT { region: IRegionT::Default }, coord);
        let var_s = Self::translate_var_name(self.interner, id);
        let local_c = AddressibleLocalVariableI {
            name: collapse_var_name(self.interner, &var_s),
            variability: Self::translate_variability(variability),
            collapsed_coord: collapse_coord(self.interner, &coord_s.coord),
        };
        (coord_s.coord, local_c)
    }

    pub fn translate_addr_expr(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _expr: &AddressExpressionTE<'s, 't>) -> (CoordI<'s, 'i, sI>, AddressExpressionIE<'s, 'i, cI>) {
        match _expr {
            AddressExpressionTE::LocalLookup(ll) => {
                let LocalLookupTE { range: _range, local_variable: local_variable_t } = **ll;
                let (local_subjective_it, local_variable_i) = self.translate_local_variable(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &local_variable_t);
                let result_subjective_it = local_subjective_it;
                let result_ce = AddressExpressionIE::LocalLookup(self.interner.bump().alloc(LocalLookupIE {
                    local_variable: local_variable_i,
                    result: collapse_coord(self.interner, &result_subjective_it),
                }));
                (result_subjective_it, result_ce)
            }
            AddressExpressionTE::ReferenceMemberLookup(rml) => {
                let ReferenceMemberLookupTE { range, struct_expr: struct_expr_t, member_name: member_name_t, member_reference: member_coord_t, variability } = **rml;
                let (_struct_subjective_it, struct_ce) =
                    self.translate_ref_expr(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &struct_expr_t);
                let member_name = Self::translate_var_name(self.interner, &member_name_t);
                let member_coord_s = self.translate_coord(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &member_coord_t);
                let result_subjective_it = member_coord_s;
                let result_ce = AddressExpressionIE::ReferenceMemberLookup(self.interner.bump().alloc(ReferenceMemberLookupIE {
                    range,
                    struct_expr: struct_ce,
                    member_name: collapse_var_name(self.interner, &member_name),
                    member_reference: collapse_coord(self.interner, &result_subjective_it.coord),
                    variability: Self::translate_variability(&variability),
                }));
                (result_subjective_it.coord, result_ce)
            }
            AddressExpressionTE::StaticSizedArrayLookup(s) => {
                let StaticSizedArrayLookupTE { range, array_expr: array_expr_t, array_type: _, index_expr: index_expr_t, element_type: element_type_t, variability } = **s;
                let (_array_subjective_it, array_ce) =
                    self.translate_ref_expr(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &array_expr_t);
                let element_type_s = self.translate_coord(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &element_type_t).coord;
                let (_index_it, index_ce) =
                    self.translate_ref_expr(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &index_expr_t);
                let result_coord = CoordI { ownership: element_type_s.ownership, kind: element_type_s.kind };
                let result_ce = AddressExpressionIE::StaticSizedArrayLookup(self.interner.alloc(StaticSizedArrayLookupIE {
                    range,
                    array_expr: array_ce,
                    index_expr: index_ce,
                    element_type: region_collapser_individual::collapse_coord(self.interner, &result_coord),
                    variability: Self::translate_variability(&variability),
                }));
                (result_coord, result_ce)
            }
            AddressExpressionTE::AddressMemberLookup(a) => {
                let AddressMemberLookupTE { range: _range, struct_expr, member_name, result_type2, variability } = **a;
                let (_struct_it, struct_ce) = self.translate_ref_expr(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &struct_expr);
                let var_name_s = Self::translate_var_name(self.interner, &member_name);
                let result_it = self.translate_coord(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &result_type2);
                let variability_c = Self::translate_variability(&variability);
                let result_ce = AddressExpressionIE::AddressMemberLookup(self.interner.alloc(AddressMemberLookupIE {
                    struct_expr: struct_ce,
                    member_name: collapse_var_name(self.interner, &var_name_s),
                    member_reference: collapse_coord(self.interner, &result_it.coord),
                    variability: variability_c,
                }));
                (result_it.coord, result_ce)
            }
            AddressExpressionTE::RuntimeSizedArrayLookup(rslt) => {
                let RuntimeSizedArrayLookupTE { range: _, array_expr, array_type: rsa_tt, index_expr, variability } = *rslt;
                let (_array_it, array_ce) = self.translate_ref_expr(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &array_expr);
                let _rsa_it = self.translate_runtime_sized_array(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, rsa_tt);
                let (_index_it, index_ce) = self.translate_ref_expr(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &index_expr);
                let variability_c = Self::translate_variability(&variability);
                let element_it = self.translate_coord(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &rsa_tt.element_type());
                let result_it = element_it;
                let result_ce = AddressExpressionIE::RuntimeSizedArrayLookup(self.interner.alloc(RuntimeSizedArrayLookupIE {
                    array_expr: array_ce,
                    index_expr: index_ce,
                    element_type: collapse_coord(self.interner, &element_it.coord),
                    variability: variability_c,
                }));
                (result_it.coord, result_ce)
            }
        }
    }

    pub fn translate_expr(&self, monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, denizen_name: &IdT<'s, 't>, denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, perspective_region_t: &RegionT, expr: &ExpressionTE<'s, 't>) -> (CoordI<'s, 'i, sI>, ExpressionIE<'s, 'i, cI>) {
        match expr {
            ExpressionTE::Reference(r) => {
                let (it, ce) = self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, r);
                (it, ExpressionIE::Reference(ce))
            }
            ExpressionTE::Address(a) => {
                let (it, ce) = self.translate_addr_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, a);
                (it, ExpressionIE::Address(ce))
            }
        }
    }

    pub fn translate_ref_expr(&self, monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, denizen_name: &IdT<'s, 't>, denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, perspective_region_t: &RegionT, expr: &ReferenceExpressionTE<'s, 't>) -> (CoordI<'s, 'i, sI>, ReferenceExpressionIE<'s, 'i, cI>) {
        let _denizen_template_name = Compiler::get_template(self.typing_interner, *denizen_name);
        match expr {
            ReferenceExpressionTE::LetAndLend(lal) => {
                let LetAndLendTE { variable, expr: source_expr_t, target_ownership: outer_ownership_t } = **lal;
                let (source_subjective_it, source_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &source_expr_t);
                let result_ownership_c =
                    Self::translate_ownership(
                        substitutions,
                        perspective_region_t,
                        &Self::compose_ownerships_second(&outer_ownership_t, &source_subjective_it.ownership),
                        &source_expr_t.result().coord.region);
                let result_it = CoordI { ownership: result_ownership_c, kind: source_subjective_it.kind };
                let (_local_it, local_i) =
                    self.translate_local_variable(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &variable);
                let result_ce = ReferenceExpressionIE::LetAndLend(self.interner.bump().alloc(LetAndLendIE {
                    variable: local_i,
                    expr: source_ce,
                    target_ownership: result_ownership_c,
                    result: region_collapser_individual::collapse_coord(self.interner, &result_it),
                }));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::LockWeak(lw) => {
                let LockWeakTE { inner_expr, result_opt_borrow_type, some_constructor, none_constructor, some_impl_name, none_impl_name } = **lw;
                let result_it =
                    self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &result_opt_borrow_type).coord;
                let result_ct = region_collapser_individual::collapse_coord(self.interner, &result_it);
                let (_inner_it_s, inner_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &inner_expr);
                let (_some_proto_s, some_proto_c) =
                    self.translate_prototype(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, some_constructor);
                let (_none_proto_s, none_proto_c) =
                    self.translate_prototype(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, none_constructor);
                let some_impl_id_s = self.translate_impl_id(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &some_impl_name);
                let some_impl_id_c = region_collapser_individual::collapse_impl_id(self.interner, &some_impl_id_s);
                let none_impl_id_s = self.translate_impl_id(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &none_impl_name);
                let none_impl_id_c = region_collapser_individual::collapse_impl_id(self.interner, &none_impl_id_s);
                let result_ce = ReferenceExpressionIE::LockWeak(self.interner.bump().alloc(LockWeakIE {
                    inner_expr: inner_ce,
                    result_opt_borrow_type: result_ct,
                    some_constructor: some_proto_c,
                    none_constructor: none_proto_c,
                    some_impl_name: some_impl_id_c,
                    none_impl_name: none_impl_id_c,
                    result: result_ct,
                }));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::BorrowToWeak(b) => {
                let BorrowToWeakTE { inner_expr } = **b;
                let (inner_it, inner_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &inner_expr);
                let result_it = CoordI { ownership: OwnershipI::Weak, kind: inner_it.kind };
                let result_ct = region_collapser_individual::collapse_coord(self.interner, &result_it);
                (result_it, ReferenceExpressionIE::BorrowToWeak(self.interner.bump().alloc(BorrowToWeakIE { inner_expr: inner_ce, result: result_ct })))
            }
            ReferenceExpressionTE::LetNormal(l) => {
                let (_inner_it, inner_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &l.expr);
                let (_local_it, local_i) =
                    self.translate_local_variable(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &l.variable);
                // env.addTranslatedVariable(variableT.name, vimpl(translatedVariable))
                let subjective_result_it = CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::VoidIT(VoidIT { _marker: PhantomData }) };
                let expr_ce = ReferenceExpressionIE::LetNormal(self.interner.alloc(LetNormalIE {
                    variable: local_i,
                    expr: inner_ce,
                    result: region_collapser_individual::collapse_coord(self.interner, &subjective_result_it),
                }));
                (subjective_result_it, expr_ce)
            }
            ReferenceExpressionTE::Unlet(u) => {
                let (local_it, local_ce) =
                    self.translate_local_variable(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &u.variable);
                let result_it = local_it;
                // val local = env.lookupOriginalTranslatedVariable(variable.name)
                let result_ce = ReferenceExpressionIE::Unlet(self.interner.alloc(UnletIE {
                    variable: local_ce,
                    result: region_collapser_individual::collapse_coord(self.interner, &result_it),
                }));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::Discard(d) => {
                let (_inner_it, inner_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &d.expr);
                let result_ce = ReferenceExpressionIE::Discard(self.interner.alloc(DiscardIE { expr: inner_ce }));
                (CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::VoidIT(VoidIT { _marker: PhantomData }) }, result_ce)
            }
            ReferenceExpressionTE::Defer(d) => {
                let DeferTE { inner_expr, deferred_expr } = **d;
                let (inner_it, inner_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &inner_expr);
                let (_deferred_it, deferred_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &deferred_expr);
                let result_it = inner_it;
                let result_ce = ReferenceExpressionIE::Defer(self.interner.bump().alloc(DeferIE {
                    inner_expr: inner_ce,
                    deferred_expr: deferred_ce,
                    result: region_collapser_individual::collapse_coord(self.interner, &result_it),
                }));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::If(if_te) => {
                let (_condition_it, condition_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &if_te.condition);
                let (then_it, then_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &if_te.then_call);
                let (else_it, else_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &if_te.else_call);
                let result_it =
                    match (then_it, else_it) {
                        (a, b) if a == b => a,
                        (a, CoordI { kind: KindIT::NeverIT(_), .. }) => a,
                        (CoordI { kind: KindIT::NeverIT(_), .. }, b) => b,
                        _ => panic!("vwat"),
                    };
                let result_ce = ReferenceExpressionIE::If(self.interner.alloc(IfIE {
                    condition: condition_ce,
                    then_call: then_ce,
                    else_call: else_ce,
                    result: region_collapser_individual::collapse_coord(self.interner, &result_it),
                }));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::While(w) => {
                let (inner_it, inner_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &w.block.inner);

                // While loops must always produce void.
                // If we want a foreach/map/whatever construct, the loop should instead
                // add things to a list inside; WhileIE shouldnt do it for it.
                let result_it =
                    match inner_it {
                        CoordI { kind: KindIT::VoidIT(_), .. } => inner_it,
                        CoordI { kind: KindIT::NeverIT(NeverIT { from_break: true, .. }), .. } => CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::VoidIT(VoidIT { _marker: PhantomData }) },
                        CoordI { kind: KindIT::NeverIT(NeverIT { from_break: false, .. }), .. } => inner_it,
                        _ => panic!("vwat"),
                    };
                let result_ce =
                    ReferenceExpressionIE::While(self.interner.alloc(WhileIE {
                        block: BlockIE { inner: inner_ce, result: region_collapser_individual::collapse_coord(self.interner, &inner_it) },
                        result: region_collapser_individual::collapse_coord(self.interner, &result_it),
                    }));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::Mutate(m) => {
                let MutateTE { destination_expr: destination_tt, source_expr } = **m;
                let (destination_it, destination_ce) = self.translate_addr_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &destination_tt);
                let (_source_it, source_ce) = self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &source_expr);
                let result_it = destination_it;
                let result_ce = ReferenceExpressionIE::Mutate(self.interner.bump().alloc(MutateIE {
                    destination_expr: destination_ce,
                    source_expr: source_ce,
                    result: region_collapser_individual::collapse_coord(self.interner, &result_it),
                }));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::Restackify(r) => {
                let (_inner_it, inner_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &r.source_expr);
                let (_local_it, local_i) =
                    self.translate_local_variable(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &r.variable);
                // env.addTranslatedVariable(variableT.name, vimpl(translatedVariable))
                let subjective_result_it = CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::VoidIT(VoidIT { _marker: PhantomData }) };
                let expr_ce = ReferenceExpressionIE::Restackify(self.interner.alloc(RestackifyIE {
                    variable: local_i,
                    expr: inner_ce,
                    result: region_collapser_individual::collapse_coord(self.interner, &subjective_result_it),
                }));
                (subjective_result_it, expr_ce)
            }
            ReferenceExpressionTE::Return(r) => {
                let (_inner_it, inner_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &r.source_expr);
                let result_ce = ReferenceExpressionIE::Return(self.interner.alloc(ReturnIE {
                    source_expr: inner_ce,
                }));
                (CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::NeverIT(NeverIT { from_break: false, _marker: PhantomData }) }, result_ce)
            }
            ReferenceExpressionTE::Break(_) => {
                let result_ce = ReferenceExpressionIE::Break(self.interner.alloc(BreakIE(PhantomData)));
                (CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::NeverIT(NeverIT { from_break: true, _marker: PhantomData }) }, result_ce)
            }
            ReferenceExpressionTE::Block(b) => {
                let (inner_it, inner_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &b.inner);
                let result_it = inner_it;
                let result_ce = ReferenceExpressionIE::Block(self.interner.alloc(BlockIE {
                    inner: inner_ce,
                    result: region_collapser_individual::collapse_coord(self.interner, &result_it),
                }));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::Pure(_) => panic!("Unimplemented: translate_ref_expr Pure"),
            ReferenceExpressionTE::Consecutor(c) => {
                let result_tt = c.result().coord;
                let result_it =
                    self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &result_tt)
                        .coord;
                let inners_ce: Vec<_> =
                    c.exprs.iter().map(|inner_te| {
                        self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, inner_te).1
                    }).collect();
                let result_ce = ReferenceExpressionIE::Consecutor(self.interner.alloc(ConsecutorIE {
                    exprs: self.interner.alloc_slice_from_vec(inners_ce),
                    result: region_collapser_individual::collapse_coord(self.interner, &result_it),
                }));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::Tuple(t) => {
                let TupleTE { elements, result_reference } = **t;
                let elements_ce: Vec<_> = elements.iter().map(|element_te| {
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, element_te).1
                }).collect();
                let result_it =
                    self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &result_reference)
                        .coord;
                (result_it, ReferenceExpressionIE::Tuple(self.interner.bump().alloc(TupleIE {
                    elements: self.interner.alloc_slice_from_vec(elements_ce),
                    result: region_collapser_individual::collapse_coord(self.interner, &result_it),
                })))
            }
            ReferenceExpressionTE::StaticArrayFromValues(s) => {
                let StaticArrayFromValuesTE { elements, result_reference, array_type } = **s;
                let result_it =
                    self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &result_reference)
                        .coord;
                let elements_ce: Vec<_> = elements.iter().map(|element_te| {
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, element_te).1
                }).collect();
                let ssa_tt = self.translate_static_sized_array(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &array_type);
                let result_ce = ReferenceExpressionIE::StaticArrayFromValues(self.interner.alloc(StaticArrayFromValuesIE {
                    elements: self.interner.alloc_slice_from_vec(elements_ce),
                    result_reference: region_collapser_individual::collapse_coord(self.interner, &result_it),
                    array_type: region_collapser_individual::collapse_static_sized_array(self.interner, &ssa_tt),
                }));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::ArraySize(_) => panic!("Unimplemented: translate_ref_expr ArraySize"),
            ReferenceExpressionTE::IsSameInstance(isi) => {
                let IsSameInstanceTE { left, right } = **isi;
                let (_left_it, left_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &left);
                let (_right_it, right_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &right);
                let result_ce = ReferenceExpressionIE::IsSameInstance(self.interner.alloc(IsSameInstanceIE {
                    left: left_ce,
                    right: right_ce,
                }));
                (CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::BoolIT(BoolIT { _marker: PhantomData }) }, result_ce)
            }
            ReferenceExpressionTE::AsSubtype(asx) => {
                let AsSubtypeTE { source_expr, target_type: target_subtype, result_result_type, ok_constructor, err_constructor, impl_name: impl_id_t, ok_impl_name: ok_result_impl_id_t, err_impl_name: err_result_impl_id_t } = **asx;
                let (_source_it, source_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &source_expr);
                let result_it = self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &result_result_type).coord;
                let target_coord_s = self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &target_subtype).coord;
                let result_result_coord_s = self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &result_result_type).coord;
                let (_, ok_c) = self.translate_prototype(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, ok_constructor);
                let (_, err_c) = self.translate_prototype(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, err_constructor);
                let impl_id_s = self.translate_impl_id(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &impl_id_t);
                let ok_impl_id_s = self.translate_impl_id(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &ok_result_impl_id_t);
                let err_impl_id_s = self.translate_impl_id(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &err_result_impl_id_t);
                let result_ce = ReferenceExpressionIE::AsSubtype(self.interner.bump().alloc(AsSubtypeIE {
                    source_expr: source_ce,
                    target_type: region_collapser_individual::collapse_coord(self.interner, &target_coord_s),
                    result_result_type: region_collapser_individual::collapse_coord(self.interner, &result_result_coord_s),
                    ok_constructor: self.interner.bump().alloc(ok_c),
                    err_constructor: self.interner.bump().alloc(err_c),
                    impl_name: region_collapser_individual::collapse_impl_id(self.interner, &impl_id_s),
                    ok_impl_name: region_collapser_individual::collapse_impl_id(self.interner, &ok_impl_id_s),
                    err_impl_name: region_collapser_individual::collapse_impl_id(self.interner, &err_impl_id_s),
                    result: region_collapser_individual::collapse_coord(self.interner, &result_it),
                }));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::VoidLiteral(_) => {
                (CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::VoidIT(VoidIT { _marker: PhantomData }) },
                 ReferenceExpressionIE::VoidLiteral(self.interner.alloc(VoidLiteralIE(PhantomData))))
            }
            ReferenceExpressionTE::ConstantInt(c) => {
                let result_ce = ReferenceExpressionIE::ConstantInt(self.interner.alloc(ConstantIntIE {
                    value: expect_integer_templata(self.translate_templata(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &c.value)).value,
                    bits: c.bits,
                    _marker: PhantomData,
                }));
                (CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::IntIT(IntIT { bits: c.bits, _marker: PhantomData }) }, result_ce)
            }
            ReferenceExpressionTE::ConstantBool(c) => {
                let result_ce = ReferenceExpressionIE::ConstantBool(self.interner.alloc(ConstantBoolIE { _marker: PhantomData, value: c.value }));
                (CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::BoolIT(BoolIT { _marker: PhantomData }) }, result_ce)
            }
            ReferenceExpressionTE::ConstantStr(c) => {
                let result_ce = ReferenceExpressionIE::ConstantStr(self.interner.alloc(ConstantStrIE { _marker: PhantomData, value: c.value.0 }));
                (CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::StrIT(StrIT { _marker: PhantomData }) }, result_ce)
            }
            ReferenceExpressionTE::ConstantFloat(c) => {
                let result_ce = ReferenceExpressionIE::ConstantFloat(self.interner.alloc(ConstantFloatIE { _marker: PhantomData, value: c.value }));
                (CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::FloatIT(FloatIT { _marker: PhantomData }) }, result_ce)
            }
            ReferenceExpressionTE::ArgLookup(al) => {
                let ArgLookupTE { param_index, coord: reference } = **al;
                let type_s =
                    self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &reference)
                        .coord;
                let result_ce = ReferenceExpressionIE::ArgLookup(self.interner.alloc(ArgLookupIE { param_index, coord: region_collapser_individual::collapse_coord(self.interner, &type_s) }));
                (type_s, result_ce)
            }
            ReferenceExpressionTE::ArrayLength(al) => {
                let ArrayLengthTE { array_expr } = **al;
                let (_array_it, array_ce) = self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &array_expr);
                let result_it = CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::IntIT(IntIT { bits: 32, _marker: PhantomData }) };
                let result_ce = ReferenceExpressionIE::ArrayLength(self.interner.alloc(ArrayLengthIE {
                    array_expr: array_ce,
                }));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::InterfaceFunctionCall(ifc) => {
                let InterfaceFunctionCallTE { super_function_prototype: super_function_prototype_t, virtual_param_index, result_reference: _result_reference, args } = **ifc;
                let (super_function_prototype_i, super_function_prototype_c) =
                    self.translate_prototype(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, super_function_prototype_t);
                let result_it = super_function_prototype_i.return_type;
                let args_ce: Vec<ReferenceExpressionIE<'s, 'i, cI>> = args.iter().map(|arg| {
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, arg).1
                }).collect();
                let result_ce = ReferenceExpressionIE::InterfaceFunctionCall(self.interner.bump().alloc(InterfaceFunctionCallIE {
                    super_function_prototype: self.interner.bump().alloc(super_function_prototype_c),
                    virtual_param_index,
                    args: self.interner.alloc_slice_from_vec(args_ce),
                    result: region_collapser_individual::collapse_coord(self.interner, &result_it),
                }));
                let interface_id_c = super_function_prototype_c.param_types()[virtual_param_index as usize].kind.expect_interface().id;
                let instantiation_bound_args = self.translate_bound_args_for_callee(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, self.hinputs.get_instantiation_bound_args(super_function_prototype_t.id));
                let super_function_prototype_n =
                    collapse_prototype_consistent(
                        self.interner,
                        count_prototype_map(&super_function_prototype_i),
                        &super_function_prototype_i);
                assert!(collapse_prototype(self.interner, &super_function_prototype_n) == super_function_prototype_c);
                monouts.new_abstract_funcs.push((*super_function_prototype_t, super_function_prototype_n, virtual_param_index as usize, interface_id_c, instantiation_bound_args));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::ExternFunctionCall(efc) => {
                let ExternFunctionCallTE { prototype2, args } = **efc;
                let (prototype_i, prototype_c) = self.translate_prototype(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, prototype2);
                let args_ce: Vec<ReferenceExpressionIE<'s, 'i, cI>> = args.iter().map(|arg_te| self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, arg_te).1).collect();
                let result_it = prototype_i.return_type;
                let result_ce = ReferenceExpressionIE::ExternFunctionCall(self.interner.bump().alloc(ExternFunctionCallIE { prototype2: prototype_c, args: self.interner.bump().alloc_slice_fill_iter(args_ce.into_iter()), result: prototype_c.return_type }));
                match prototype2.id.local_name {
                    INameT::ExternFunction(ExternFunctionNameT { human_name, template_args, .. }) if !template_args.is_empty() => {
                        let num_inherited = self.hinputs.function_externs.iter().find(|fe| {
                            fe.prototype.id.package_coord == prototype2.id.package_coord
                                && fe.prototype.id.init_steps == prototype2.id.init_steps
                                && match fe.prototype.id.local_name {
                                    INameT::ExternFunction(ExternFunctionNameT { human_name: hn, .. }) => hn == human_name,
                                    _ => false,
                                }
                        })
                        .and_then(|fe| fe.generic_parameter_inheritance.as_ref().map(|i| i.num_inherited_generic_parameters))
                        .unwrap_or(0);
                        monouts.function_externs.push(FunctionExternI {
                            prototype: self.interner.intern_prototype_ci(PrototypeIValI { id: prototype_c.id, return_type: prototype_c.return_type }),
                            num_inherited_generic_parameters: num_inherited,
                        });
                    }
                    _ => {}
                }
                (result_it, result_ce)
            }
            ReferenceExpressionTE::FunctionCall(fc) => {
                let FunctionCallTE { callable: prototype_t, args, return_type: _return_type } = fc;
                let inners_ce: Vec<ReferenceExpressionIE<'s, 'i, cI>> = args.iter().map(|arg_te| {
                    let (_arg_it, arg_ce) = self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, arg_te);
                    arg_ce
                }).collect();
                let (prototype_i, prototype_c) = self.translate_prototype(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, prototype_t);
                let return_coord_it = prototype_i.return_type;
                let return_coord_ct = collapse_coord(self.interner, &return_coord_it);
                let result_ce = ReferenceExpressionIE::FunctionCall(self.interner.alloc(FunctionCallIE {
                    callable: prototype_c,
                    args: self.interner.bump().alloc_slice_fill_iter(inners_ce.into_iter()),
                    result: collapse_coord(self.interner, &return_coord_it),
                }));
                let _ = return_coord_ct;
                (return_coord_it, result_ce)
            }
            ReferenceExpressionTE::Reinterpret(_) => panic!("Unimplemented: translate_ref_expr Reinterpret"),
            ReferenceExpressionTE::Construct(c) => {
                let ConstructTE { struct_tt, result_reference, args } = **c;
                let result_it = self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &result_reference).coord;
                let args_ce: Vec<ExpressionIE<'s, 'i, cI>> = args.iter().map(|arg_te| {
                    self.translate_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, arg_te).1
                }).collect();
                let bound_args = self.translate_bound_args_for_callee(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &self.hinputs.get_instantiation_bound_args(struct_tt.id));
                let struct_it = self.translate_struct(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, struct_tt, &bound_args);
                let result_ce = ReferenceExpressionIE::Construct(self.interner.bump().alloc(ConstructIE {
                    struct_tt: *self.interner.intern_struct_it_ci(StructITValI { id: collapse_struct_id(self.interner, &struct_it.id) }),
                    result: collapse_coord(self.interner, &result_it),
                    args: self.interner.bump().alloc_slice_fill_iter(args_ce.into_iter()),
                }));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::NewMutRuntimeSizedArray(nmrsa) => {
                let NewMutRuntimeSizedArrayTE { array_type: array_tt, region: _, capacity_expr } = **nmrsa;
                let array_it = self.translate_runtime_sized_array(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, array_tt);
                let array_mutability = match array_it.name.local_name {
                    INameI::RuntimeSizedArray(n) => n.arr.mutability,
                    _ => panic!("translate_ref_expr NewMutRuntimeSizedArray: local_name not RuntimeSizedArrayNameI"),
                };
                let result_ownership = match array_mutability {
                    MutabilityI::Mutable => OwnershipI::Own,
                    MutabilityI::Immutable => OwnershipI::MutableShare,
                };
                let result_it = CoordI {
                    ownership: result_ownership,
                    kind: KindIT::RuntimeSizedArrayIT(self.interner.alloc(array_it)),
                };
                let (_capacity_it, capacity_ce) = self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &capacity_expr);
                let result_ce = ReferenceExpressionIE::NewMutRuntimeSizedArray(self.interner.alloc(NewMutRuntimeSizedArrayIE {
                    array_type: collapse_runtime_sized_array(self.interner, &array_it),
                    capacity_expr: capacity_ce,
                    result: collapse_coord(self.interner, &result_it),
                }));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::StaticArrayFromCallable(s) => {
                let StaticArrayFromCallableTE { array_type, region: _, generator, generator_method } = **s;
                let ssa_it = self.translate_static_sized_array(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, array_type);
                let (_generator_it, generator_ce) = self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &generator);
                let (_generator_prototype_i, generator_prototype_c) = self.translate_prototype(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, generator_method);
                let result_it = CoordI {
                    ownership: match ssa_it.mutability() {
                        MutabilityI::Mutable => OwnershipI::Own,
                        MutabilityI::Immutable => OwnershipI::MutableShare,
                    },
                    kind: KindIT::StaticSizedArrayIT(self.interner.alloc(ssa_it)),
                };
                let result_ce = ReferenceExpressionIE::StaticArrayFromCallable(self.interner.alloc(StaticArrayFromCallableIE {
                    array_type: collapse_static_sized_array(self.interner, &ssa_it),
                    generator: generator_ce,
                    generator_method: generator_prototype_c,
                    result: collapse_coord(self.interner, &result_it),
                }));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::DestroyStaticSizedArrayIntoFunction(d) => {
                let DestroyStaticSizedArrayIntoFunctionTE { array_expr: array_expr_t, array_type: array_type_t, consumer: consumer_t, consumer_method: consumer_method_t } = **d;
                let (_array_it, array_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &array_expr_t);
                let ssa_it = self.translate_static_sized_array(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, array_type_t);
                let (_consumer_it, consumer_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &consumer_t);
                let (_consumer_prototype_i, consumer_prototype_c) =
                    self.translate_prototype(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, consumer_method_t);
                let result_ce = ReferenceExpressionIE::DestroyStaticSizedArrayIntoFunction(self.interner.alloc(DestroyStaticSizedArrayIntoFunctionIE {
                    array_expr: array_ce,
                    array_type: region_collapser_individual::collapse_static_sized_array(self.interner, &ssa_it),
                    consumer: consumer_ce,
                    consumer_method: consumer_prototype_c,
                }));
                (CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::VoidIT(VoidIT { _marker: PhantomData }) }, result_ce)
            }
            ReferenceExpressionTE::DestroyStaticSizedArrayIntoLocals(d) => {
                let DestroyStaticSizedArrayIntoLocalsTE { expr: expr_t, static_sized_array: ssa_tt, destination_reference_variables } = **d;
                let (source_it, source_ce) = self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &expr_t);
                let (ssa_it, size) = match source_it.kind {
                    KindIT::StaticSizedArrayIT(s) => {
                        match s.name.local_name {
                            INameI::StaticSizedArray(n) => (*s, n.size),
                            _ => panic!("DestroyStaticSizedArrayIntoLocals: local_name not StaticSizedArrayNameI"),
                        }
                    }
                    _ => panic!("DestroyStaticSizedArrayIntoLocals: source_it.kind not StaticSizedArrayIT"),
                };
                assert!(size == destination_reference_variables.len() as i64);
                let dest_vars_vec: Vec<ReferenceLocalVariableI<'s, 'i>> = destination_reference_variables.iter().map(|dest_ref_var_t| {
                    self.translate_reference_local_variable(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, dest_ref_var_t).1
                }).collect();
                let result_ce = ReferenceExpressionIE::DestroyStaticSizedArrayIntoLocals(self.interner.alloc(DestroyStaticSizedArrayIntoLocalsIE {
                    expr: source_ce,
                    static_sized_array: region_collapser_individual::collapse_static_sized_array(self.interner, &ssa_it),
                    destination_reference_variables: self.interner.alloc_slice_from_vec(dest_vars_vec),
                }));
                (CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::VoidIT(VoidIT { _marker: PhantomData }) }, result_ce)
            }
            ReferenceExpressionTE::DestroyMutRuntimeSizedArray(d) => {
                let DestroyMutRuntimeSizedArrayTE { array_expr } = **d;
                let (_array_it, array_ce) = self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &array_expr);
                let result_ce = ReferenceExpressionIE::DestroyMutRuntimeSizedArray(self.interner.alloc(DestroyMutRuntimeSizedArrayIE {
                    array_expr: array_ce,
                }));
                (CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::VoidIT(VoidIT { _marker: PhantomData }) }, result_ce)
            }
            ReferenceExpressionTE::RuntimeSizedArrayCapacity(r) => {
                let RuntimeSizedArrayCapacityTE { array_expr } = **r;
                let (_array_it, array_ce) = self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &array_expr);
                let result_ce = ReferenceExpressionIE::RuntimeSizedArrayCapacity(self.interner.alloc(RuntimeSizedArrayCapacityIE {
                    array_expr: array_ce,
                }));
                (CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::IntIT(IntIT { bits: 32, _marker: PhantomData }) }, result_ce)
            }
            ReferenceExpressionTE::PushRuntimeSizedArray(prsa) => {
                let PushRuntimeSizedArrayTE { array_expr, new_element_expr } = **prsa;
                let (_array_it, array_ce) = self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &array_expr);
                let (_element_it, element_ce) = self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &new_element_expr);
                let result_ce = ReferenceExpressionIE::PushRuntimeSizedArray(self.interner.alloc(PushRuntimeSizedArrayIE {
                    array_expr: array_ce,
                    new_element_expr: element_ce,
                }));
                (CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::VoidIT(VoidIT { _marker: PhantomData }) }, result_ce)
            }
            ReferenceExpressionTE::PopRuntimeSizedArray(p) => {
                let PopRuntimeSizedArrayTE { array_expr, element_type: _ } = **p;
                let (array_it, array_ce) = self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &array_expr);
                let element_it = match array_it.kind {
                    KindIT::RuntimeSizedArrayIT(rsa) => match rsa.name.local_name {
                        INameI::RuntimeSizedArray(n) => n.arr.element_type.coord,
                        _ => panic!("translate_ref_expr PopRuntimeSizedArray: local_name not RuntimeSizedArrayNameI"),
                    },
                    _ => panic!("translate_ref_expr PopRuntimeSizedArray: kind not RuntimeSizedArrayIT"),
                };
                let result_ce = ReferenceExpressionIE::PopRuntimeSizedArray(self.interner.alloc(PopRuntimeSizedArrayIE {
                    array_expr: array_ce,
                    result: collapse_coord(self.interner, &element_it),
                }));
                (element_it, result_ce)
            }
            ReferenceExpressionTE::InterfaceToInterfaceUpcast(_) => panic!("Unimplemented: translate_ref_expr InterfaceToInterfaceUpcast"),
            ReferenceExpressionTE::Upcast(u) => {
                let UpcastTE { inner_expr: inner_expr_unsubstituted, target_super_kind, impl_name: untranslated_impl_id } = *u;
                let impl_id = self.translate_impl_id(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &untranslated_impl_id);
                let result_it = self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &u.result().coord);
                let (_inner_it, inner_ce) = self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &inner_expr_unsubstituted);
                let super_kind_s = self.translate_super_kind(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &target_super_kind);
                let result_ce = ReferenceExpressionIE::Upcast(self.interner.bump().alloc(UpcastIE {
                    inner_expr: inner_ce,
                    target_interface: *self.interner.intern_interface_it_ci(InterfaceITValI { id: collapse_interface_id(self.interner, &super_kind_s.id) }),
                    impl_name: collapse_impl_id(self.interner, &impl_id),
                    result: collapse_coord(self.interner, &result_it.coord),
                }));
                (result_it.coord, result_ce)
            }
            ReferenceExpressionTE::SoftLoad(sl) => {
                let SoftLoadTE { expr: original_inner, target_ownership: original_target_ownership } = **sl;
                let (inner_it, inner_ce) = self.translate_addr_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &original_inner);
                let target_ownership = match (original_target_ownership, inner_it.ownership) {
                    (OwnershipT::Share, OwnershipI::ImmutableShare) => OwnershipI::ImmutableShare,
                    (OwnershipT::Share, OwnershipI::MutableShare) => OwnershipI::MutableShare,
                    (OwnershipT::Borrow, OwnershipI::ImmutableShare) => OwnershipI::ImmutableShare,
                    (OwnershipT::Borrow, OwnershipI::MutableShare) => OwnershipI::MutableShare,
                    (OwnershipT::Borrow, OwnershipI::ImmutableBorrow) => OwnershipI::ImmutableBorrow,
                    (OwnershipT::Borrow, OwnershipI::MutableBorrow) | (OwnershipT::Borrow, OwnershipI::Own) => {
                        // if (coordRegionIsMutable(substitutions, perspectiveRegionT, originalInner.result.coord)) {
                        OwnershipI::MutableBorrow
                        // } else { ImmutableBorrowI }
                    }
                    (OwnershipT::Weak, OwnershipI::ImmutableShare) => OwnershipI::ImmutableShare,
                    (OwnershipT::Weak, OwnershipI::MutableShare) => OwnershipI::MutableShare,
                    (OwnershipT::Weak, OwnershipI::Own) => OwnershipI::Weak,
                    (OwnershipT::Weak, OwnershipI::ImmutableBorrow) => OwnershipI::Weak,
                    (OwnershipT::Weak, OwnershipI::MutableBorrow) => OwnershipI::Weak,
                    (OwnershipT::Weak, OwnershipI::Weak) => OwnershipI::Weak,
                    other => panic!("SoftLoad: vwat {:?}", other),
                };
                let result_it = CoordI { ownership: target_ownership, kind: inner_it.kind };
                let result_ce = ReferenceExpressionIE::SoftLoad(self.interner.bump().alloc(SoftLoadIE { expr: inner_ce, target_ownership, result: collapse_coord(self.interner, &result_it) }));
                (result_it, result_ce)
            }
            ReferenceExpressionTE::Destroy(d) => {
                let DestroyTE { expr: expr_t, struct_tt, destination_reference_variables } = **d;
                let (_source_it, source_ce) =
                    self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &expr_t);
                let bound_args = self.translate_bound_args_for_callee(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &self.hinputs.get_instantiation_bound_args(struct_tt.id));
                let struct_it = self.translate_struct_id(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &struct_tt.id, &bound_args);
                let dest_ref_vars: Vec<ReferenceLocalVariableI<'s, 'i>> =
                    destination_reference_variables.iter().map(|dest_ref_var_t| {
                        self.translate_reference_local_variable(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, dest_ref_var_t).1
                    }).collect();
                let result_ce = ReferenceExpressionIE::Destroy(self.interner.bump().alloc(DestroyIE {
                    expr: source_ce,
                    struct_tt: *self.interner.intern_struct_it_ci(StructITValI { id: collapse_struct_id(self.interner, &struct_it) }),
                    destination_reference_variables: self.interner.bump().alloc_slice_copy(&dest_ref_vars),
                }));
                (CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::VoidIT(VoidIT { _marker: PhantomData }) }, result_ce)
            }
            ReferenceExpressionTE::DestroyImmRuntimeSizedArray(_) => panic!("Unimplemented: translate_ref_expr DestroyImmRuntimeSizedArray"),
            ReferenceExpressionTE::NewImmRuntimeSizedArray(nrsa_t) => {
                let NewImmRuntimeSizedArrayTE { array_type, region: _, size_expr, generator, generator_method } = **nrsa_t;
                let rsa_it = self.translate_runtime_sized_array(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, array_type);
                let (_size_it, size_ce) = self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &size_expr);
                let (_generator_it, generator_ce) = self.translate_ref_expr(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &generator);
                let (_generator_prototype_i, generator_prototype_c) = self.translate_prototype(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, generator_method);
                let array_mutability = match rsa_it.name.local_name {
                    INameI::RuntimeSizedArray(n) => n.arr.mutability,
                    _ => panic!("translate_ref_expr NewImmRuntimeSizedArray: local_name not RuntimeSizedArrayNameI"),
                };
                let result_ownership = match array_mutability {
                    MutabilityI::Mutable => OwnershipI::Own,
                    MutabilityI::Immutable => OwnershipI::MutableShare,
                };
                let result_it = CoordI {
                    ownership: result_ownership,
                    kind: KindIT::RuntimeSizedArrayIT(self.interner.alloc(rsa_it)),
                };
                let result_ce = ReferenceExpressionIE::NewImmRuntimeSizedArray(self.interner.alloc(NewImmRuntimeSizedArrayIE {
                    array_type: collapse_runtime_sized_array(self.interner, &rsa_it),
                    size_expr: size_ce,
                    generator: generator_ce,
                    generator_method: generator_prototype_c,
                    result: collapse_coord(self.interner, &result_it),
                }));
                (result_it, result_ce)
            }
        }
    }

    pub fn maybe_immutabilify(_inner_ie: &ReferenceExpressionIE<'s, 'i, cI>) -> ReferenceExpressionIE<'s, 'i, cI> {
        panic!("Unimplemented: maybe_immutabilify");
    }

    pub fn run_in_new_pure_region<T>(_denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _denizen_template_name: &IdT<'s, 't>, _new_default_region_t: &ITemplataT<'s, 't>, _run: impl Fn(&IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, &RegionT) -> T) -> T {
        panic!("Unimplemented: run_in_new_pure_region");
    }

    pub fn translate_ownership(_substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, ownership_t: &OwnershipT, _region_t: &RegionT) -> OwnershipI {
        match ownership_t {
            OwnershipT::Own => OwnershipI::Own,
            OwnershipT::Borrow => OwnershipI::MutableBorrow,
            OwnershipT::Share => OwnershipI::MutableShare,
            OwnershipT::Weak => panic!("translate_ownership: WeakT vimpl"),
        }
    }

    pub fn compose_ownerships(outer_ownership: &OwnershipT, inner_ownership: &OwnershipI, kind: &KindIT<'s, 'i, sI>) -> OwnershipI {
        match kind {
            KindIT::IntIT(_) | KindIT::BoolIT(_) | KindIT::VoidIT(_) => OwnershipI::MutableShare,
            _ => {
                match (outer_ownership, inner_ownership) {
                    (OwnershipT::Own, OwnershipI::Own) => OwnershipI::Own,
                    (OwnershipT::Own, OwnershipI::MutableShare) | (OwnershipT::Own, OwnershipI::ImmutableShare)
                    | (OwnershipT::Borrow, OwnershipI::MutableShare) | (OwnershipT::Borrow, OwnershipI::ImmutableShare) => {
                        OwnershipI::MutableShare
                    }
                    (OwnershipT::Own, OwnershipI::MutableBorrow) => {
                        // vregionmut() // here too maybe?
                        OwnershipI::MutableBorrow
                    }
                    (OwnershipT::Borrow, OwnershipI::Own) => {
                        // vregionmut() // we'll probably want a regionIsMutable call like above
                        OwnershipI::MutableBorrow
                    }
                    (OwnershipT::Borrow, OwnershipI::MutableBorrow) => {
                        // vregionmut() // we'll probably want a regionIsMutable call like above
                        OwnershipI::MutableBorrow
                    }
                    (OwnershipT::Weak, OwnershipI::Own) => {
                        // vregionmut() // here too maybe?
                        OwnershipI::Weak
                    }
                    (OwnershipT::Share, OwnershipI::MutableShare) => {
                        // vregionmut() // here too maybe?
                        OwnershipI::MutableShare
                    }
                    other => panic!("compose_ownerships: vwat {:?}", other),
                }
            }
        }
    }

    pub fn compose_ownerships_second(outer_ownership: &OwnershipT, inner_ownership: &OwnershipI) -> OwnershipT {
        match (outer_ownership, inner_ownership) {
            (OwnershipT::Own, OwnershipI::Own) => OwnershipT::Own,
            (OwnershipT::Own, OwnershipI::MutableBorrow) => OwnershipT::Borrow,
            (OwnershipT::Borrow, OwnershipI::Own) => OwnershipT::Borrow,
            (OwnershipT::Borrow, OwnershipI::MutableBorrow) => OwnershipT::Borrow,
            (OwnershipT::Borrow, OwnershipI::Weak) => OwnershipT::Weak,
            (OwnershipT::Borrow, OwnershipI::MutableShare) => OwnershipT::Share,
            (OwnershipT::Weak, OwnershipI::Own) => OwnershipT::Weak,
            (OwnershipT::Weak, OwnershipI::MutableBorrow) => OwnershipT::Weak,
            (OwnershipT::Weak, OwnershipI::Weak) => OwnershipT::Weak,
            (OwnershipT::Weak, OwnershipI::MutableShare) => OwnershipT::Share,
            (OwnershipT::Share, OwnershipI::MutableShare) => OwnershipT::Share,
            (OwnershipT::Own, OwnershipI::MutableShare) => OwnershipT::Share,
            other => panic!("compose_ownerships_second: vwat {:?}", other),
        }
    }

    pub fn translate_function_id(&self, monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, denizen_name: &IdT<'s, 't>, denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, perspective_region_t: &RegionT, full_name_t: &IdT<'s, 't>) -> IdI<'s, 'i, sI> {
        let IdT { package_coord: module, init_steps: steps, local_name: last, .. } = *full_name_t;
        let full_name =
            IdI {
                package_coord: module,
                init_steps: self.interner.alloc_slice_from_vec(
                    steps.iter().map(|step| self.translate_name_substituting(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, step)).collect::<Vec<_>>()),
                local_name: INameI::from(self.translate_function_name(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &IFunctionNameT::try_from(last).unwrap())),
            };
        full_name
    }

    pub fn translate_struct_id(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _struct_id_t: &IdT<'s, 't>, _instantiation_bound_args: &InstantiationBoundArgumentsI<'s, 'i>) -> IdI<'s, 'i, sI> {
        let IdT { package_coord: module, init_steps: steps, local_name: last_t, .. } = _struct_id_t;
        let last_t_struct: IStructNameT<'s, 't> = (*last_t).try_into().unwrap();
        let translated_steps: Vec<INameI<'s, 'i, sI>> = steps.iter().map(|n| self.translate_name_substituting(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, n)).collect();
        let struct_name_si = self.translate_struct_name(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &last_t_struct);
        let full_name_s = IdI {
            package_coord: module,
            init_steps: self.interner.bump().alloc_slice_fill_iter(translated_steps.into_iter()),
            local_name: struct_name_si.into(),
        };
        self.collapse_and_translate_struct_definition(_monouts, _struct_id_t, &full_name_s, _instantiation_bound_args);
        full_name_s
    }

    pub fn translate_interface_id(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _interface_id_t: &IdT<'s, 't>, _instantiation_bound_args: &InstantiationBoundArgumentsI<'s, 'i>) -> IdI<'s, 'i, sI> {
        let IdT { package_coord: module, init_steps: steps, local_name: last_t, .. } = _interface_id_t;
        let last_t_interface = match last_t {
            INameT::Interface(i) => IInterfaceNameT::Interface(*i),
            _ => panic!("translate_interface_id: local_name not Interface"),
        };
        let translated_steps: Vec<INameI<'s, 'i, sI>> = steps.iter().map(|_n| panic!("translate_interface_id: non-empty init_steps not yet ported")).collect();
        let interface_name_si = self.translate_interface_name(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &last_t_interface);
        let full_name_s = IdI {
            package_coord: module,
            init_steps: self.interner.bump().alloc_slice_fill_iter(translated_steps.into_iter()),
            local_name: interface_name_si.into(),
        };
        self.collapse_and_translate_interface_definition(_monouts, _interface_id_t, &full_name_s, _instantiation_bound_args);
        full_name_s
    }

    pub fn translate_impl_id(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _impl_id_t: &IdT<'s, 't>) -> IdI<'s, 'i, sI> {
        let IdT { package_coord: module, init_steps: steps, local_name: last_t, .. } = *_impl_id_t;
        match last_t {
            INameT::ImplBound(_) => {
                let impl_bound_name = *_impl_id_t;
                let impl_id_s = *_denizen_bound_to_denizen_caller_supplied_thing.bound_param_impl_id_to_bound_arg_impl_id.get(&impl_bound_name).expect("translate_impl_id: missing impl bound");
                impl_id_s
            }
            _ => {
                let translated_steps: Vec<INameI<'s, 'i, sI>> = steps.iter().map(|s| Self::translate_name(s)).collect();
                let impl_name_i = self.translate_impl_name(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &IImplNameT::try_from(last_t).expect("translate_impl_id: non-impl name"));
                let impl_id_s = IdI { package_coord: module, init_steps: self.interner.bump().alloc_slice_fill_iter(translated_steps.into_iter()), local_name: INameI::from(impl_name_i) };
                let impl_id_n = collapse_impl_id_consistent(self.interner, &count_impl_id_map(&impl_id_s), &impl_id_s);
                let bound_args_for_call_unsubstituted = self.hinputs.get_instantiation_bound_args(*_impl_id_t);
                let rune_to_bound_args_for_new_impl = self.translate_bound_args_for_callee(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &bound_args_for_call_unsubstituted);
                _monouts.new_impls.push((*_impl_id_t, impl_id_n, rune_to_bound_args_for_new_impl));
                impl_id_s
            }
        }
    }

    pub fn translate_citizen_name(&self, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _t: &ICitizenNameT<'s, 't>) -> ICitizenNameI<'s, 'i, sI> {
        panic!("Unimplemented: translate_citizen_name");
    }

    pub fn translate_id_from_substitutions(_substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _id: &IdT<'s, 't>) -> IdI<'s, 'i, sI> {
        panic!("Unimplemented: translate_id_from_substitutions");
    }

    pub fn translate_citizen_id(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _citizen_id_t: &IdT<'s, 't>, _instantiation_bound_args: &InstantiationBoundArgumentsI<'s, 'i>) -> IdI<'s, 'i, sI> {
        panic!("Unimplemented: translate_citizen_id");
    }

    pub fn translate_coord(&self, monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, denizen_name: &IdT<'s, 't>, denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, perspective_region_t: &RegionT, coord_t: &CoordT<'s, 't>) -> CoordTemplataI<'s, 'i, sI> {
        let CoordT { ownership: outer_ownership, region: _outer_region, kind } = coord_t;
        let _outer_region_i = RegionT { region: IRegionT::Default };
          // translateTemplata(
          //   denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, outerRegion)
          //     .expectRegionTemplata()

        match kind {
            KindT::KindPlaceholder(placeholder_id) => {
                let sub = substitutions.get(&placeholder_id.id).expect("translate_coord: missing placeholder substitution");
                match sub {
                    ITemplataI::Coord(c) => {
                        let CoordTemplataI { region: _region, coord: CoordI { ownership: inner_ownership, kind: inner_kind } } = *c;
                        let combined_ownership = Self::compose_ownerships(outer_ownership, &inner_ownership, &inner_kind);
                        CoordTemplataI { region: RegionTemplataI { pure_height: 0, _marker: PhantomData }, coord: CoordI { ownership: combined_ownership, kind: inner_kind } }
                    }
                    ITemplataI::Kind(_) => panic!("Unimplemented: translate_coord KindPlaceholder->Kind"),
                    _ => panic!("Unimplemented: translate_coord KindPlaceholder other"),
                }
            }
            other => {
                // We could, for example, be translating an Vector<myFunc$0, T> (which is temporarily regarded mutable)
                // to an Vector<imm, int> (which is immutable).
                // So, we have to check for that here and possibly make the ownership share.
                let kind = self.translate_kind(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, other);
                let new_ownership =
                    match kind {
                        KindIT::IntIT(_) | KindIT::BoolIT(_) | KindIT::VoidIT(_) => {
                            // We don't want any ImmutableShareH for primitives, it's better to only ever have one
                            // ownership for primitives.
                            OwnershipI::MutableShare
                        }
                        _ => {
                            let mutability = Self::get_mutability(monouts, &region_collapser_individual::collapse_kind(self.interner, &kind));
                            match match (*outer_ownership, mutability) {
                                (_, MutabilityI::Immutable) => OwnershipT::Share,
                                (other, MutabilityI::Mutable) => other,
                            } { // Now  if it's a borrow, figure out whether it's mutable or immutable
                                OwnershipT::Borrow => {
                                    // if (regionIsMutable(substitutions, perspectiveRegionT, expectRegionPlaceholder(outerRegion))) {
                                    OwnershipI::MutableBorrow
                                    // } else {
                                    //   ImmutableBorrowI
                                    // }
                                }
                                OwnershipT::Share => {
                                    // if (regionIsMutable(substitutions, perspectiveRegionT, expectRegionPlaceholder(outerRegion))) {
                                    OwnershipI::MutableShare
                                    // } else {
                                    //   ImmutableShareI
                                    // }
                                }
                                OwnershipT::Own => {
                                    // We don't have this assert because we sometimes can see owning references even
                                    // though we dont hold them, see RMLRMO.
                                    // vassert(regionIsMutable(substitutions, perspectiveRegionT, expectRegionPlaceholder(outerRegion)))
                                    OwnershipI::Own
                                }
                                OwnershipT::Weak => {
                                    OwnershipI::Weak
                                }
                            }
                        }
                    };
//        val newRegion = expectRegionTemplata(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, outerRegion))
                CoordTemplataI { region: RegionTemplataI { pure_height: 0, _marker: PhantomData }, coord: CoordI { ownership: new_ownership, kind } }
            }
        }
    }

    pub fn get_mutability(_monouts: &InstantiatedOutputsI<'s, 't, 'i>, kind_it: &KindIT<'s, 'i, cI>) -> MutabilityI {
        match kind_it {
            KindIT::IntIT(_) | KindIT::BoolIT(_) | KindIT::StrIT(_) | KindIT::NeverIT(_) | KindIT::FloatIT(_) | KindIT::VoidIT(_) => MutabilityI::Immutable,
            KindIT::StructIT(s) => *_monouts.struct_to_mutability.get(&s.id).expect("get_mutability: struct not found"),
            KindIT::InterfaceIT(i) => *_monouts.interface_to_mutability.get(&i.id).expect("get_mutability: interface not found"),
            KindIT::RuntimeSizedArrayIT(rsa) => {
                match rsa.name.local_name {
                    INameI::RuntimeSizedArray(n) => n.arr.mutability,
                    _ => panic!("get_mutability RuntimeSizedArray: local_name not RuntimeSizedArrayNameI"),
                }
            }
            KindIT::StaticSizedArrayIT(ssa) => {
                match ssa.name.local_name {
                    INameI::StaticSizedArray(n) => n.arr.mutability,
                    _ => panic!("get_mutability StaticSizedArray: local_name not StaticSizedArrayNameI"),
                }
            }
        }
    }

    pub fn translate_citizen(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _citizen: &ICitizenTT<'s, 't>, _instantiation_bound_args: &InstantiationBoundArgumentsI<'s, 'i>) -> ICitizenIT<'s, 'i, sI> {
        match _citizen {
            ICitizenTT::Struct(s) => {
                let s_i = self.translate_struct(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, s, _instantiation_bound_args);
                ICitizenIT::StructIT(self.interner.intern_struct_it_si(StructITValI { id: s_i.id }))
            }
            ICitizenTT::Interface(i) => {
                let i_i = self.translate_interface(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, i, _instantiation_bound_args);
                ICitizenIT::InterfaceIT(self.interner.intern_interface_it_si(InterfaceITValI { id: i_i.id }))
            }
        }
    }

    pub fn translate_struct(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _struct: &StructTT<'s, 't>, _instantiation_bound_args: &InstantiationBoundArgumentsI<'s, 'i>) -> StructIT<'s, 'i, sI> {
        let StructTT { id: full_name, .. } = _struct;
        let translated_id = self.translate_struct_id(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, full_name, _instantiation_bound_args);
        let desired_struct = *self.interner.intern_struct_it_si(StructITValI { id: translated_id });
        desired_struct
    }

    pub fn translate_interface(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _interface: &InterfaceTT<'s, 't>, _instantiation_bound_args: &InstantiationBoundArgumentsI<'s, 'i>) -> InterfaceIT<'s, 'i, sI> {
        let InterfaceTT { id: full_name, .. } = _interface;
        let translated_id = self.translate_interface_id(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, full_name, _instantiation_bound_args);
        *self.interner.intern_interface_it_si(crate::instantiating::ast::types::InterfaceITValI { id: translated_id })
    }

    pub fn translate_super_kind(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _kind: &ISuperKindTT<'s, 't>) -> InterfaceIT<'s, 'i, sI> {
        match _kind {
            ISuperKindTT::Interface(i) => {
                let bound_args = self.translate_bound_args_for_callee(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &self.hinputs.get_instantiation_bound_args(i.id));
                self.translate_interface(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, i, &bound_args)
            }
            ISuperKindTT::KindPlaceholder(_) => panic!("Unimplemented: translate_super_kind KindPlaceholder"),
        }
    }

    pub fn translate_placeholder(&self, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _t: &KindPlaceholderT<'s, 't>) -> KindIT<'s, 'i, sI> {
        panic!("Unimplemented: translate_placeholder");
    }

    pub fn translate_static_sized_array(&self, monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, denizen_name: &IdT<'s, 't>, denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, perspective_region_t: &RegionT, ssa_tt: &StaticSizedArrayTT<'s, 't>) -> StaticSizedArrayIT<'s, 'i, sI> {
        let StaticSizedArrayTT { name: id_t, .. } = ssa_tt;
        let IdT { package_coord, init_steps, local_name, .. } = *id_t;
        let ssa_name_t = match local_name {
            INameT::StaticSizedArray(n) => *n,
            _ => panic!("translate_static_sized_array: local_name not StaticSizedArrayNameT"),
        };
        let StaticSizedArrayNameT { template: _, size: size_t, variability: variability_t, arr } = ssa_name_t;
        let RawArrayNameT { mutability: mutability_t, element_type: element_type_t, self_region: _ } = *arr;
        let new_perspective_region_t = RegionT { region: IRegionT::Default };
        let _ssa_region = RegionT { region: IRegionT::Default };
        let int_templata = expect_integer_templata(self.translate_templata(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, &new_perspective_region_t, &size_t)).value;
        let variability_templata = expect_variability_templata(self.translate_templata(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, &new_perspective_region_t, &variability_t)).variability;
        let mutability_templata = expect_mutability_templata(self.translate_templata(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, &new_perspective_region_t, &mutability_t)).mutability;
        let element_type = self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, &new_perspective_region_t, &element_type_t);
        let translated_init_steps: Vec<INameI<'s, 'i, sI>> = init_steps.iter().map(|n| Self::translate_name(n)).collect();
        let local_name_i = INameI::StaticSizedArray(self.interner.alloc(StaticSizedArrayNameI {
            template: StaticSizedArrayTemplateNameI(PhantomData),
            size: int_templata,
            variability: variability_templata,
            arr: RawArrayNameI {
                mutability: mutability_templata,
                element_type,
                self_region: RegionTemplataI { pure_height: 0, _marker: PhantomData },
            },
        }));
        let id_i = IdI {
            package_coord,
            init_steps: self.interner.alloc_slice_from_vec(translated_init_steps),
            local_name: local_name_i,
        };
        *self.interner.intern_static_sized_array_it_si(crate::instantiating::ast::types::StaticSizedArrayITValI { name: id_i })
    }

    pub fn translate_runtime_sized_array(&self, monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, denizen_name: &IdT<'s, 't>, denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, perspective_region_t: &RegionT, rsa_tt: &RuntimeSizedArrayTT<'s, 't>) -> RuntimeSizedArrayIT<'s, 'i, sI> {
        let RuntimeSizedArrayTT { name: id_t, .. } = rsa_tt;
        let IdT { package_coord, init_steps, local_name, .. } = *id_t;
        let rsa_name_t = match local_name {
            INameT::RuntimeSizedArray(n) => *n,
            _ => panic!("translate_runtime_sized_array: local_name not RuntimeSizedArrayNameT"),
        };
        let RuntimeSizedArrayNameT { template: _, arr } = rsa_name_t;
        let RawArrayNameT { mutability: mutability_t, element_type: element_type_t, self_region: _ } = *arr;
        let new_perspective_region_t = RegionT { region: IRegionT::Default };
        let _rsa_region = RegionT { region: IRegionT::Default };
        let mutability_templata = expect_mutability_templata(self.translate_templata(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, &new_perspective_region_t, &mutability_t)).mutability;
        let element_type = self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, &new_perspective_region_t, &element_type_t);
        let translated_init_steps: Vec<INameI<'s, 'i, sI>> = init_steps.iter().map(|n| Self::translate_name(n)).collect();
        let local_name_i = INameI::RuntimeSizedArray(self.interner.intern_runtime_sized_array_name_si(RuntimeSizedArrayNameI {
            template: RuntimeSizedArrayTemplateNameI(PhantomData),
            arr: RawArrayNameI {
                mutability: mutability_templata,
                element_type,
                self_region: RegionTemplataI { pure_height: 0, _marker: PhantomData },
            },
        }));
        let id_i = IdI {
            package_coord,
            init_steps: self.interner.alloc_slice_from_vec(translated_init_steps),
            local_name: local_name_i,
        };
        *self.interner.intern_runtime_sized_array_it_si(crate::instantiating::ast::types::RuntimeSizedArrayITValI { name: id_i })
    }

    pub fn translate_kind(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, kind_t: &KindT<'s, 't>) -> KindIT<'s, 'i, sI> {
        match kind_t {
            KindT::Int(int_t) => KindIT::IntIT(IntIT { bits: int_t.bits, _marker: PhantomData }),
            KindT::Bool(_) => KindIT::BoolIT(BoolIT { _marker: PhantomData }),
            KindT::Float(_) => KindIT::FloatIT(FloatIT { _marker: PhantomData }),
            KindT::Void(_) => KindIT::VoidIT(VoidIT { _marker: PhantomData }),
            KindT::Str(_) => KindIT::StrIT(StrIT { _marker: PhantomData }),
            KindT::Never(never_t) => KindIT::NeverIT(NeverIT { from_break: never_t.from_break, _marker: PhantomData }),
            KindT::KindPlaceholder(_p) => panic!("Unimplemented: translate_kind KindPlaceholder"),
            KindT::Struct(s) => {
                let bound_args = self.translate_bound_args_for_callee(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &self.hinputs.get_instantiation_bound_args(s.id));
                let struct_it = self.translate_struct(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, s, &bound_args);
                KindIT::StructIT(self.interner.alloc(struct_it))
            }
            KindT::Interface(s) => {
                let bound_args = self.translate_bound_args_for_callee(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &self.hinputs.get_instantiation_bound_args(s.id));
                let interface_it = self.translate_interface(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, s, &bound_args);
                KindIT::InterfaceIT(self.interner.alloc(interface_it))
            }
            KindT::StaticSizedArray(a) => KindIT::StaticSizedArrayIT(self.interner.alloc(self.translate_static_sized_array(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, a))),
            KindT::RuntimeSizedArray(a) => KindIT::RuntimeSizedArrayIT(self.interner.alloc(self.translate_runtime_sized_array(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, a))),
            _other => panic!("Unimplemented: translate_kind other"),
        }
    }

    pub fn translate_parameter(&self, monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, denizen_name: &IdT<'s, 't>, denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, perspective_region_t: &RegionT, param_t: &ParameterT<'s, 't>) -> ParameterI<'s, 'i> {
        let ParameterT { name, virtuality, pre_checked, tyype } = param_t;
        let type_it =
            self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, tyype)
                .coord;
        let name_s = Self::translate_var_name(self.interner, name);
        ParameterI {
            name: region_collapser_individual::collapse_var_name(self.interner, &name_s),
            virtuality: virtuality.map(|v| match v { AbstractT => AbstractI }),
            pre_checked: *pre_checked,
            tyype: region_collapser_individual::collapse_coord(self.interner, &type_it),
        }
    }

    pub fn translate_templata(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, templata_t: &ITemplataT<'s, 't>) -> ITemplataI<'s, 'i, sI> {
        let result = match templata_t {
            ITemplataT::Placeholder(p) => {
                let PlaceholderTemplataT { id: n, tyype: _ } = **p;
                *_substitutions.get(&n).expect("translate_templata Placeholder: substitution missing")
            }
            ITemplataT::Integer(value) => ITemplataI::Integer(IntegerTemplataI { value: *value, _marker: PhantomData }),
            ITemplataT::Boolean(_) => panic!("Unimplemented: translate_templata Boolean"),
            ITemplataT::String(_) => panic!("Unimplemented: translate_templata String"),
            ITemplataT::Coord(c) => ITemplataI::Coord(self.translate_coord(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &c.coord)),
            ITemplataT::Mutability(m) => ITemplataI::Mutability(MutabilityTemplataI { mutability: Self::translate_mutability(&m.mutability), _marker: PhantomData }),
            ITemplataT::Variability(v) => ITemplataI::Variability(VariabilityTemplataI { variability: Self::translate_variability(&v.variability), _marker: PhantomData }),
            ITemplataT::Kind(_) => panic!("Unimplemented: translate_templata Kind"),
            _ => panic!("Unimplemented: translate_templata other"),
        };
        result
    }

    pub fn translate_var_name(interner: &InstantiatingInterner<'s, 'i>, name: &IVarNameT<'s, 't>) -> IVarNameI<'s, 'i, sI> {
        match name {
            IVarNameT::TypingPassFunctionResultVar(_) => IVarNameI::TypingPassFunctionResultVar(interner.intern_typing_pass_function_result_var_name_si(TypingPassFunctionResultVarNameI(PhantomData))),
            IVarNameT::CodeVar(x) => IVarNameI::CodeVar(interner.intern_code_var_name_si(CodeVarNameI { _marker: PhantomData, name: x.name })),
            IVarNameT::ClosureParam(ClosureParamNameT { code_location, .. }) => IVarNameI::ClosureParam(interner.intern_closure_param_name_si(ClosureParamNameI { _marker: PhantomData, code_location: *code_location })),
            IVarNameT::TypingPassBlockResultVar(TypingPassBlockResultVarNameT { life: LocationInFunctionEnvironmentT { path, .. } }) => {
                IVarNameI::TypingPassBlockResultVar(interner.intern_typing_pass_block_result_var_name_si(TypingPassBlockResultVarNameI {
                    _marker: PhantomData,
                    life: LocationInFunctionEnvironmentI { path: interner.alloc_slice_from_vec(path.to_vec()) },
                }))
            }
            IVarNameT::TypingPassTemporaryVar(TypingPassTemporaryVarNameT { life: LocationInFunctionEnvironmentT { path, .. } }) => {
                IVarNameI::TypingPassTemporaryVar(interner.intern_typing_pass_temporary_var_name_si(TypingPassTemporaryVarNameI {
                    _marker: PhantomData,
                    life: LocationInFunctionEnvironmentI { path: interner.alloc_slice_from_vec(path.to_vec()) },
                }))
            }
            IVarNameT::ConstructingMember(x) => IVarNameI::ConstructingMember(interner.intern_constructing_member_name_si(ConstructingMemberNameI { _marker: PhantomData, name: x.name })),
            IVarNameT::Iterable(IterableNameT { range, .. }) => IVarNameI::Iterable(interner.intern_iterable_name_si(IterableNameI { _marker: PhantomData, range: *range })),
            IVarNameT::Iterator(IteratorNameT { range, .. }) => IVarNameI::Iterator(interner.intern_iterator_name_si(IteratorNameI { _marker: PhantomData, range: *range })),
            IVarNameT::IterationOption(IterationOptionNameT { range, .. }) => IVarNameI::IterationOption(interner.intern_iteration_option_name_si(IterationOptionNameI { _marker: PhantomData, range: *range })),
            IVarNameT::MagicParam(MagicParamNameT { code_location2, .. }) => IVarNameI::MagicParam(interner.intern_magic_param_name_si(MagicParamNameI { _marker: PhantomData, code_location_2: *code_location2 })),
            IVarNameT::Self_(_) => IVarNameI::Self_(interner.intern_self_name_si(SelfNameI(PhantomData))),
            _ => panic!("Unimplemented: translate_var_name other"),
        }
    }

    pub fn translate_function_template_name(&self, _func_template_name_t: &IFunctionTemplateNameT<'s, 't>) -> IFunctionTemplateNameI<'s, 'i, sI> {
        match _func_template_name_t {
            IFunctionTemplateNameT::FunctionTemplate(ftn) => {
                let FunctionTemplateNameT { human_name, code_location: code_loc, .. } = **ftn;
                IFunctionTemplateNameI::FunctionTemplate(self.interner.intern_function_template_name_si(FunctionTemplateNameI { _marker: PhantomData, human_name, code_location: code_loc }))
            }
            #[allow(unreachable_patterns)]
            other => panic!("translate_function_template_name: unimplemented variant {:?}", discriminant(other)),
        }
    }

    pub fn translate_function_name(&self, monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, denizen_name: &IdT<'s, 't>, denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, perspective_region_t: &RegionT, name: &IFunctionNameT<'s, 't>) -> IFunctionNameI<'s, 'i, sI> {
        match *name {
            IFunctionNameT::Function(function_name_t) => {
                let FunctionNameT { template: function_template_name_t, template_args, parameters: params, .. } = *function_name_t;
                let FunctionTemplateNameT { human_name, code_location: code_loc, .. } = *function_template_name_t;
                IFunctionNameI::Function(
                    self.interner.intern_function_name_x_si(FunctionNameIX {
                        template: FunctionTemplateNameI { _marker: PhantomData, human_name, code_location: code_loc },
                        template_args: self.interner.alloc_slice_from_vec(
                            template_args.iter().map(|template_arg| self.translate_templata(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, template_arg)).collect::<Vec<_>>()),
                        parameters: self.interner.alloc_slice_from_vec(
                            params.iter().map(|param| self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, param).coord).collect::<Vec<_>>()),
                    }))
            }
            IFunctionNameT::ForwarderFunction(n) => {
                let ForwarderFunctionNameT { template, inner } = *n;
                let ForwarderFunctionTemplateNameT { inner: inner_template, index } = *template;
                IFunctionNameI::ForwarderFunction(
                    self.interner.intern_forwarder_function_name_si(ForwarderFunctionNameI {
                        template: *self.interner.intern_forwarder_function_template_name_si(ForwarderFunctionTemplateNameI {
                            inner: self.translate_function_template_name(&inner_template),
                            index,
                        }),
                        inner: self.translate_function_name(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &inner),
                    }))
            }
            IFunctionNameT::ExternFunction(n) => {
                let ExternFunctionNameT { human_name, template_args, parameters, .. } = *n;
                IFunctionNameI::ExternFunction(
                    self.interner.intern_extern_function_name_si(ExternFunctionNameI {
                        human_name,
                        template_args: self.interner.alloc_slice_from_vec(template_args.iter().map(|template_arg| self.translate_templata(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, template_arg)).collect::<Vec<_>>()),
                        parameters: self.interner.alloc_slice_from_vec(parameters.iter().map(|param| self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, param).coord).collect::<Vec<_>>()),
                    }))
            }
            IFunctionNameT::FunctionBound(fbn) => {
                let FunctionBoundNameT { template, template_args, parameters: params, .. } = *fbn;
                let FunctionBoundTemplateNameT { human_name, .. } = *template;
                IFunctionNameI::FunctionBound(
                    self.interner.intern_function_bound_name_si(FunctionBoundNameI {
                        template: FunctionBoundTemplateNameI { _marker: PhantomData, human_name },
                        template_args: self.interner.alloc_slice_from_vec(
                            template_args.iter().map(|template_arg| self.translate_templata(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, template_arg)).collect::<Vec<_>>()),
                        parameters: self.interner.alloc_slice_from_vec(
                            params.iter().map(|param| self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, param).coord).collect::<Vec<_>>()),
                    }))
            }
            IFunctionNameT::AnonymousSubstructConstructor(n) => {
                let AnonymousSubstructConstructorNameT { template, template_args, parameters: params, .. } = *n;
                let inner_template_name_i = match self.translate_name_substituting(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, &INameT::AnonymousSubstructConstructorTemplate(template)) {
                    INameI::AnonymousSubstructConstructorTemplate(x) => *x,
                    _ => panic!("translate_function_name AnonymousSubstructConstructor: expected AnonymousSubstructConstructorTemplate"),
                };
                IFunctionNameI::AnonymousSubstructConstructor(
                    self.interner.intern_anonymous_substruct_constructor_name_si(AnonymousSubstructConstructorNameI {
                        template: inner_template_name_i,
                        template_args: self.interner.alloc_slice_from_vec(template_args.iter().map(|t| self.translate_templata(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, t)).collect::<Vec<_>>()),
                        parameters: self.interner.alloc_slice_from_vec(params.iter().map(|p| self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, p).coord).collect::<Vec<_>>()),
                    }))
            }
            IFunctionNameT::LambdaCallFunction(n) => {
                let LambdaCallFunctionNameT { template: LambdaCallFunctionTemplateNameT { code_location, param_types: param_types_for_generic, .. }, template_args, parameters: param_types, .. } = *n;
                IFunctionNameI::LambdaCallFunction(
                    self.interner.intern_lambda_call_function_name_si(LambdaCallFunctionNameI {
                        template: *self.interner.intern_lambda_call_function_template_name_si(LambdaCallFunctionTemplateNameI {
                            _marker: PhantomData,
                            code_location: *code_location,
                            param_types: self.interner.alloc_slice_from_vec(param_types_for_generic.iter().map(|p| self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, p).coord).collect::<Vec<_>>()),
                        }),
                        template_args: self.interner.alloc_slice_from_vec(template_args.iter().map(|t| self.translate_templata(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, t)).collect::<Vec<_>>()),
                        parameters: self.interner.alloc_slice_from_vec(param_types.iter().map(|p| self.translate_coord(monouts, denizen_name, denizen_bound_to_denizen_caller_supplied_thing, substitutions, perspective_region_t, p).coord).collect::<Vec<_>>()),
                    }))
            }
            IFunctionNameT::OverrideDispatcher(_) => panic!("Unimplemented: translate_function_name OverrideDispatcher"),
            _other => panic!("Unimplemented: translate_function_name other"),
        }
    }

    pub fn translate_impl_name(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _name: &IImplNameT<'s, 't>) -> IImplNameI<'s, 'i, sI> {
        match _name {
            IImplNameT::Impl(n) => {
                let ImplNameT { template: ImplTemplateNameT { code_location_s, .. }, template_args, sub_citizen, .. } = **n;
                let template_args_i: Vec<ITemplataI<'s, 'i, sI>> = template_args.iter().map(|t| self.translate_templata(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, t)).collect();
                let sub_citizen_id = sub_citizen.id();
                let bound_args_for_callee = self.translate_bound_args_for_callee(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &self.hinputs.get_instantiation_bound_args(sub_citizen_id));
                let sub_citizen_i = self.translate_citizen(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &sub_citizen, &bound_args_for_callee);
                IImplNameI::Impl(self.interner.intern_impl_name_si(ImplNameI {
                    template: IImplTemplateNameI::ImplTemplate(self.interner.intern_impl_template_name_si(ImplTemplateNameI { _marker: PhantomData, code_location_s: *code_location_s })),
                    template_args: self.interner.bump().alloc_slice_fill_iter(template_args_i.into_iter()),
                    sub_citizen: sub_citizen_i,
                }))
            }
            IImplNameT::ImplBound(_) => panic!("Unimplemented: translate_impl_name ImplBound"),
            IImplNameT::AnonymousSubstructImpl(n) => {
                let AnonymousSubstructImplNameT { template, template_args, sub_citizen, .. } = **n;
                let AnonymousSubstructImplTemplateNameT { interface, .. } = *template;
                let template_args_i: Vec<ITemplataI<'s, 'i, sI>> = template_args.iter().map(|t| self.translate_templata(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, t)).collect();
                let sub_citizen_id = sub_citizen.id();
                let bound_args_for_callee = self.translate_bound_args_for_callee(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &self.hinputs.get_instantiation_bound_args(sub_citizen_id));
                let sub_citizen_i = self.translate_citizen(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &sub_citizen, &bound_args_for_callee);
                IImplNameI::AnonymousSubstructImpl(self.interner.intern_anonymous_substruct_impl_name_si(AnonymousSubstructImplNameI {
                    template: AnonymousSubstructImplTemplateNameI {
                        interface: self.translate_interface_template_name(&interface),
                    },
                    template_args: self.interner.bump().alloc_slice_fill_iter(template_args_i.into_iter()),
                    sub_citizen: sub_citizen_i,
                }))
            }
        }
    }

    pub fn translate_impl_template_name(_name: &IImplTemplateNameT<'s, 't>) -> IImplTemplateNameI<'s, 'i, sI> {
        panic!("Unimplemented: translate_impl_template_name");
    }

    pub fn translate_struct_name(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _name: &IStructNameT<'s, 't>) -> IStructNameI<'s, 'i, sI> {
        let new_perspective_region_t = RegionT { region: IRegionT::Default };
        match _name {
            IStructNameT::Struct(StructNameT { template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name, .. }), template_args, .. }) => {
                let template_args_si: Vec<ITemplataI<'s, 'i, sI>> = template_args.iter().map(|t| self.translate_templata(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, &new_perspective_region_t, t)).collect();
                IStructNameI::Struct(self.interner.intern_struct_name_si(StructNameI {
                    template: IStructTemplateNameI::StructTemplate(self.interner.intern_struct_template_name_si(StructTemplateNameI { _marker: PhantomData, human_name: *human_name })),
                    template_args: self.interner.bump().alloc_slice_fill_iter(template_args_si.into_iter()),
                }))
            }
            IStructNameT::AnonymousSubstruct(AnonymousSubstructNameT { template, template_args, .. }) => {
                let AnonymousSubstructTemplateNameT { interface, .. } = **template;
                let template_args_si: Vec<ITemplataI<'s, 'i, sI>> = template_args.iter().map(|t| self.translate_templata(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, &new_perspective_region_t, t)).collect();
                IStructNameI::AnonymousSubstruct(self.interner.intern_anonymous_substruct_name_si(AnonymousSubstructNameI {
                    template: *self.interner.intern_anonymous_substruct_template_name_si(AnonymousSubstructTemplateNameI {
                        interface: self.translate_interface_template_name(&interface),
                    }),
                    template_args: self.interner.bump().alloc_slice_fill_iter(template_args_si.into_iter()),
                }))
            }
            IStructNameT::LambdaCitizen(LambdaCitizenNameT { template: LambdaCitizenTemplateNameT { code_location, .. } }) => {
                IStructNameI::LambdaCitizen(self.interner.intern_lambda_citizen_name_si(LambdaCitizenNameI {
                    template: *self.interner.intern_lambda_citizen_template_name_si(LambdaCitizenTemplateNameI { _marker: PhantomData, code_location: *code_location }),
                }))
            }
            other => panic!("translate_struct_name: unimplemented variant {:?}", discriminant(other)),
        }
    }

    pub fn translate_interface_name(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, _name: &IInterfaceNameT<'s, 't>) -> IInterfaceNameI<'s, 'i, sI> {
        match _name {
            IInterfaceNameT::Interface(InterfaceNameT { template: InterfaceTemplateNameT { human_namee: human_name, .. }, template_args, .. }) => {
                let template_args_si: Vec<ITemplataI<'s, 'i, sI>> = template_args.iter().map(|t| self.translate_templata(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, t)).collect();
                IInterfaceNameI::Interface(self.interner.intern_interface_name_si(InterfaceNameI {
                    template: IInterfaceTemplateNameI::InterfaceTemplate(self.interner.intern_interface_template_name_si(InterfaceTemplateNameI { _marker: PhantomData, human_namee: *human_name })),
                    template_args: self.interner.bump().alloc_slice_fill_iter(template_args_si.into_iter()),
                }))
            }
            #[allow(unreachable_patterns)] // mirrors Scala's `case other => vimpl(other)` catch-all; unreachable until more IInterfaceNameT variants migrate
            other => panic!("translate_interface_name: unimplemented variant {:?}", discriminant(other)),
        }
    }

    pub fn translate_interface_template_name(&self, _name: &IInterfaceTemplateNameT<'s, 't>) -> IInterfaceTemplateNameI<'s, 'i, sI> {
        match _name {
            IInterfaceTemplateNameT::InterfaceTemplate(InterfaceTemplateNameT { human_namee, .. }) => {
                IInterfaceTemplateNameI::InterfaceTemplate(self.interner.intern_interface_template_name_si(InterfaceTemplateNameI { _marker: PhantomData, human_namee: *human_namee }))
            }
            #[allow(unreachable_patterns)]
            other => panic!("translate_interface_template_name: unimplemented variant {:?}", discriminant(other)),
        }
    }

// Rust adaptation (SPDMX-S): Scala overloads `translateName`. The 1-arg
// `translateName(t: INameT)` is `translate_name` above; this 5-arg version is
// suffixed `_substituting` to disambiguate (Rust lacks overloading). Slice pipeline
// emitted no stub for this overload (TL-added per NNDX escalation).
    pub fn translate_name_substituting(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _perspective_region_t: &RegionT, name: &INameT<'s, 't>) -> INameI<'s, 'i, sI> {
        match *name {
            n if IVarNameT::try_from(n).is_ok() => panic!("Unimplemented: translate_name_substituting IVarNameT"),
            INameT::KindPlaceholderTemplate(_) => panic!("translate_name_substituting: KindPlaceholderTemplate vwat"),
            INameT::KindPlaceholder(_) => panic!("translate_name_substituting: KindPlaceholder vwat"),
            INameT::Struct(_) => panic!("Unimplemented: translate_name_substituting Struct"),
            INameT::ForwarderFunctionTemplate(fftn) => {
                let ForwarderFunctionTemplateNameT { inner, index } = *fftn;
                INameI::ForwarderFunctionTemplate(self.interner.intern_forwarder_function_template_name_si(ForwarderFunctionTemplateNameI {
                    inner: self.translate_function_template_name(&inner),
                    index,
                }))
            }
            INameT::AnonymousSubstructConstructorTemplate(astn) => {
                let AnonymousSubstructConstructorTemplateNameT { substruct, .. } = *astn;
                let substruct_as_name: INameT<'s, 't> = match substruct {
                    ICitizenTemplateNameT::StaticSizedArrayTemplate(x) => x.into(),
                    ICitizenTemplateNameT::RuntimeSizedArrayTemplate(x) => x.into(),
                    ICitizenTemplateNameT::LambdaCitizenTemplate(x) => x.into(),
                    ICitizenTemplateNameT::StructTemplate(x) => x.into(),
                    ICitizenTemplateNameT::InterfaceTemplate(x) => x.into(),
                    ICitizenTemplateNameT::AnonymousSubstructTemplate(x) => x.into(),
                };
                let translated = self.translate_name_substituting(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &substruct_as_name);
                let citizen_template_name_i: ICitizenTemplateNameI<'s, 'i, sI> = ICitizenTemplateNameI::try_from(translated).unwrap();
                INameI::AnonymousSubstructConstructorTemplate(self.interner.intern_anonymous_substruct_constructor_template_name_si(AnonymousSubstructConstructorTemplateNameI { substruct: citizen_template_name_i }))
            }
            INameT::FunctionTemplate(ftn) => {
                let FunctionTemplateNameT { human_name, code_location: code_loc, .. } = *ftn;
                INameI::FunctionTemplate(self.interner.intern_function_template_name_si(FunctionTemplateNameI { _marker: PhantomData, human_name, code_location: code_loc }))
            }
            INameT::StructTemplate(stn) => {
                let StructTemplateNameT { human_name, .. } = *stn;
                INameI::StructTemplate(self.interner.intern_struct_template_name_si(StructTemplateNameI { _marker: PhantomData, human_name }))
            }
            INameT::LambdaCitizenTemplate(LambdaCitizenTemplateNameT { code_location, .. }) => {
                INameI::LambdaCitizenTemplate(self.interner.intern_lambda_citizen_template_name_si(LambdaCitizenTemplateNameI { _marker: PhantomData, code_location: *code_location }))
            }
            INameT::AnonymousSubstructTemplate(astn) => {
                let AnonymousSubstructTemplateNameT { interface, .. } = *astn;
                INameI::AnonymousSubstructTemplate(self.interner.intern_anonymous_substruct_template_name_si(AnonymousSubstructTemplateNameI {
                    interface: self.translate_interface_template_name(&interface),
                }))
            }
            INameT::LambdaCitizen(_) => panic!("Unimplemented: translate_name_substituting LambdaCitizen"),
            INameT::InterfaceTemplate(itn) => {
                let InterfaceTemplateNameT { human_namee, .. } = *itn;
                INameI::InterfaceTemplate(self.interner.intern_interface_template_name_si(InterfaceTemplateNameI { _marker: PhantomData, human_namee }))
            }
            INameT::Function(_) | INameT::ForwarderFunction(_) | INameT::ExternFunction(_) | INameT::FunctionBound(_) | INameT::LambdaCallFunction(_) | INameT::AnonymousSubstructConstructor(_) | INameT::PredictedFunction(_) => {
                let f: IFunctionNameT<'s, 't> = (*name).try_into().unwrap();
                INameI::from(self.translate_function_name(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, _perspective_region_t, &f))
            }
            _ => panic!("Unimplemented: translate_name_substituting other"),
        }
    }

    pub fn translate_collapsed_impl_definition(&self, _monouts: &mut InstantiatedOutputsI<'s, 't, 'i>, _denizen_name: &IdT<'s, 't>, _instantiation_bounds_for_unsubstituted_impl: InstantiationBoundArgumentsI<'s, 'i>, _denizen_bound_to_denizen_caller_supplied_thing: &DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>, _substitutions: &IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>, _impl_id_t: &IdT<'s, 't>, _impl_id_s: &IdI<'s, 'i, sI>, _impl_id_c: &IdI<'s, 'i, cI>, _impl_definition: &EdgeT<'s, 't>) {
        let perspective_region_t = RegionT { region: IRegionT::Default };
        let sub_citizen_bound_args = self.translate_bound_args_for_callee(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, &perspective_region_t, &self.hinputs.get_instantiation_bound_args(_impl_definition.sub_citizen.id()));
        let sub_citizen_s = self.translate_citizen(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, &perspective_region_t, &_impl_definition.sub_citizen, &sub_citizen_bound_args);
        let sub_citizen_c = collapse_citizen(self.interner, &sub_citizen_s);
        let super_interface_bound_args = self.translate_bound_args_for_callee(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, &perspective_region_t, &self.hinputs.get_instantiation_bound_args(_impl_definition.super_interface));
        let super_interface_s = self.translate_interface_id(_monouts, _denizen_name, _denizen_bound_to_denizen_caller_supplied_thing, _substitutions, &perspective_region_t, &_impl_definition.super_interface, &super_interface_bound_args);
        let super_interface_c = collapse_interface_id(self.interner, &super_interface_s);

        let mutability = *_monouts.interface_to_mutability.get(&super_interface_c).expect("translate_collapsed_impl_definition: superInterfaceC mutability missing");
        if _monouts.impl_to_mutability.contains_key(_impl_id_c) {
            return;
        }
        _monouts.impl_to_mutability.insert(*_impl_id_c, mutability);

        // We assemble the EdgeI at the very end of the instantiating stage.

        _monouts.impls.insert(*_impl_id_c, (sub_citizen_c, super_interface_c, _denizen_bound_to_denizen_caller_supplied_thing.clone(), _instantiation_bounds_for_unsubstituted_impl));

        _monouts.interface_to_impl_to_abstract_prototype_to_override.get_mut(&super_interface_c).expect("vassertSome: interface_to_impl_to_abstract_prototype_to_override")
            .insert(*_impl_id_c, IndexMap::new());
        _monouts.interface_to_impls.get_mut(&super_interface_c).expect("vassertSome: interface_to_impls").push((*_impl_id_t, *_impl_id_c));
    }
}
