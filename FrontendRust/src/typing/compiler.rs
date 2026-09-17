use std::collections::HashMap;
use indexmap::IndexMap;
use std::marker::PhantomData;
use crate::higher_typing::ast::{ProgramA, StructA, InterfaceA, FunctionA};
use crate::interner::StrI;
use crate::keywords::Keywords;
use crate::postparsing::ast::{ICitizenAttributeS, LocationInDenizen, MacroCallS};
use crate::typing::citizen::struct_compiler::UncheckedDefiningConclusions;
use crate::typing::ast::citizens::{IStructMemberT, NormalStructMemberT, IMemberTypeT, ReferenceMemberTypeT};
use crate::typing::templata_compiler::IBoundArgumentsSource;
use crate::postparsing::names::{IImpreciseNameS, IRuneS};
use crate::scout_arena::ScoutArena;
use crate::typing::ast::expressions::{ReferenceExpressionTE, ConsecutorTE, VoidLiteralTE};
use crate::typing::ast::ast::{FunctionHeaderT, InterfaceEdgeBlueprintT, KindExportT, PrototypeT};
use crate::typing::hinputs_t::InstantiationBoundArgumentsT;
use crate::typing::compilation::TypingPassOptions;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::typing::compiler_outputs::{CompilerOutputs, DeferredActionT};
use crate::typing::infer_compiler::InferEnv;
use crate::typing::templata::templata::ImplDefinitionTemplataT;
use crate::typing::macros::macros::{OnStructDefinedMacro, OnInterfaceDefinedMacro, FunctionBodyMacro};
use crate::typing::env::environment::{get_imprecise_name, make_top_level_environment, GlobalEnvironmentT, IEnvironmentT, IInDenizenEnvironmentT, PackageEnvironmentT, TemplatasStoreT, TemplatasStoreBuilder};
use crate::typing::env::i_env_entry::IEnvEntryT;
use crate::typing::hinputs_t::HinputsT;
use crate::typing::names::names::{
    IdT, IdValT, INameT, IFunctionTemplateNameT, IInstantiationNameT, ITemplateNameT,
    IStructTemplateNameT, IInterfaceTemplateNameT, IImplTemplateNameT, PackageTopLevelNameT, PrimitiveNameT,
};
use crate::typing::templata::templata::{
    CoordTemplataT, FunctionTemplataT, ITemplataT, InterfaceDefinitionTemplataT, KindTemplataT, MutabilityTemplataT, PlaceholderTemplataT,
    PrototypeTemplataT, RuntimeSizedArrayTemplateTemplataT, StaticSizedArrayTemplateTemplataT, StructDefinitionTemplataT,
};
use crate::typing::types::types::CoordT;
use crate::typing::types::types::{BoolT, FloatT, IntT, KindT, MutabilityT, NeverT, StrT, VoidT};
use crate::typing::typing_interner::TypingInterner;
use crate::typing::types::types::{IRegionT, RegionT};
use crate::typing::function::function_compiler::StampFunctionSuccess;
use crate::typing::overload_resolver::FindFunctionFailure;
use crate::utils::code_hierarchy::{FileCoordinateMap, PackageCoordinate, PackageCoordinateMap};
use crate::utils::range::RangeS;
use crate::typing::types::types::ISubKindTT;
use crate::typing::types::types::ICitizenTT;
use crate::typing::names::names::{PredictedFunctionTemplateNameT, PredictedFunctionNameValT};
use crate::typing::ast::ast::PrototypeValT;
use crate::typing::names::names::FunctionBoundTemplateNameT;
use crate::typing::names::names::FunctionBoundNameValT;
use crate::typing::names::names::{ImplBoundTemplateNameT, ImplBoundNameValT};
use crate::typing::templata::templata::IsaTemplataT;
use crate::typing::names::names::ExportTemplateNameT;
use crate::typing::names::names::ExportNameT;
use crate::typing::env::environment::ExportEnvironmentT;
use crate::typing::citizen::struct_compiler::IResolveOutcome;
use crate::postparsing::names::IStructDeclarationNameS;
use crate::postparsing::ast::IFunctionAttributeS;
use crate::typing::names::names::ExternTemplateNameT;
use crate::typing::names::names::ExternNameT;
use crate::typing::names::names::ExternFunctionNameValT;
use crate::typing::env::environment::ExternEnvironmentT;
use crate::typing::function::function_compiler::IResolveFunctionResult;
use crate::postparsing::names::IFunctionDeclarationNameS;
use crate::postparsing::itemplatatype::ITemplataType;
use crate::parsing::ast::ast::IMacroInclusionP;
use crate::typing::types::types::StaticSizedArrayTT;
use crate::typing::types::types::RuntimeSizedArrayTT;
use crate::typing::types::types::StructTT;
use crate::postparsing::names::IImpreciseNameValS;
use crate::postparsing::names::CodeNameS;
use crate::postparsing::rules::rules::IRulexSR;
use crate::typing::env::function_environment_t::FunctionEnvironmentT;
use crate::typing::ast::ast::LocationInFunctionEnvironmentT;
use crate::typing::ast::ast::ParameterT;
use crate::typing::ast::ast::ICitizenAttributeT;
use crate::typing::names::names::CitizenTemplateNameT;
use std::collections::HashSet;
use std::iter::empty;
use std::iter::once;

#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum IFunctionGenerator {
    StructConstructor,
    StructDrop,
    InterfaceDrop,
    RsaDropInto,
    RsaImmutableNew,
    RsaLen,
    RsaMutableCapacity,
    RsaMutableNew,
    RsaMutablePop,
    RsaMutablePush,
    SsaDropInto,
    SsaLen,
    LockWeak,
    SameInstance,
    AsSubtype,
    AbstractBody,
}

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn print(
        &self,
        // TODO: Slab 14 — Scala uses a by-name parameter here; pick impl Display or &str as the Rust equivalent when porting the body.
        x: (),
    ) {
        panic!("Unimplemented: Slab 15 — body migration");
    }
    
}
pub struct Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub scout_arena: &'ctx ScoutArena<'s>,
    pub typing_interner: &'ctx TypingInterner<'s, 't>,
    pub keywords: &'ctx Keywords<'s>,
    pub opts: &'ctx TypingPassOptions,
}

// (no direct Scala counterpart — derived from `class Compiler(opts, interner, keywords)` in the Scala block above)
impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn new(
        scout_arena: &'ctx ScoutArena<'s>,
        typing_interner: &'ctx TypingInterner<'s, 't>,
        keywords: &'ctx Keywords<'s>,
        opts: &'ctx TypingPassOptions,
    ) -> Self {
        Compiler { scout_arena, typing_interner, keywords, opts }
    }
    
    pub fn get_placeholders_in_id(&self, accum: &mut Vec<IdT<'s, 't>>, id: IdT<'s, 't>) {
        match id.local_name {
            INameT::KindPlaceholder(_) => accum.push(id),
            INameT::KindPlaceholderTemplate(_) => accum.push(id),
            _ => {}
        }
    }

    pub fn get_placeholders_in_templata(&self, accum: &mut Vec<IdT<'s, 't>>, templata: ITemplataT<'s, 't>) {
        match templata {
            ITemplataT::Kind(KindTemplataT { kind }) => self.get_placeholders_in_kind(accum, *kind),
            ITemplataT::Coord(CoordTemplataT { coord: CoordT { kind, .. } }) => self.get_placeholders_in_kind(accum, *kind),
            ITemplataT::Placeholder(PlaceholderTemplataT { id, .. }) => accum.push(*id),
            ITemplataT::Integer(_) => {}
            ITemplataT::Boolean(_) => {}
            ITemplataT::String(_) => {}
            ITemplataT::RuntimeSizedArrayTemplate(_) => {}
            ITemplataT::StaticSizedArrayTemplate(_) => {}
            ITemplataT::Variability(_) => {}
            ITemplataT::Ownership(_) => {}
            ITemplataT::Mutability(_) => {}
            ITemplataT::InterfaceDefinition(_) => {}
            ITemplataT::StructDefinition(_) => {}
            ITemplataT::ImplDefinition(_) => {}
            ITemplataT::CoordList(_) => { panic!("implement: get_placeholders_in_templata CoordList"); }
            ITemplataT::Prototype(_) => { panic!("implement: get_placeholders_in_templata Prototype"); }
            ITemplataT::Isa(_) => { panic!("implement: get_placeholders_in_templata Isa"); }
            _ => { panic!("implement: get_placeholders_in_templata other"); }
        }
    }

    pub fn get_placeholders_in_kind(&self, accum: &mut Vec<IdT<'s, 't>>, kind: KindT<'s, 't>) {
        match kind {
            KindT::Int(_) => {}
            KindT::Bool(_) => {}
            KindT::Float(_) => {}
            KindT::Void(_) => {}
            KindT::Never(_) => {}
            KindT::Str(_) => {}
            KindT::RuntimeSizedArray(rsa) => {
                self.get_placeholders_in_templata(accum, rsa.mutability());
                self.get_placeholders_in_kind(accum, rsa.element_type().kind);
            }
            KindT::StaticSizedArray(ssa) => {
                self.get_placeholders_in_templata(accum, ssa.size());
                self.get_placeholders_in_templata(accum, ssa.mutability());
                self.get_placeholders_in_templata(accum, ssa.variability());
                self.get_placeholders_in_kind(accum, ssa.element_type().kind);
            }
            KindT::Struct(s) => {
                let inst_name = IInstantiationNameT::try_from(s.id.local_name).expect(
                    "StructTT id local_name must be an IInstantiationNameT");
                for arg in inst_name.template_args() {
                    self.get_placeholders_in_templata(accum, *arg);
                }
            }
            KindT::Interface(i) => {
                let inst_name = IInstantiationNameT::try_from(i.id.local_name).expect(
                    "InterfaceTT id local_name must be an IInstantiationNameT");
                for arg in inst_name.template_args() {
                    self.get_placeholders_in_templata(accum, *arg);
                }
            }
            KindT::KindPlaceholder(p) => accum.push(p.id),
            KindT::OverloadSet(_) => {}
        }
    }

    pub fn sanity_check_conclusion(&self, envs: &InferEnv<'s, 't>, _state: &mut CompilerOutputs<'s, 't>, _rune: IRuneS<'s>, templata: ITemplataT<'s, 't>) {
        let mut accum: Vec<IdT<'s, 't>> = Vec::new();
        self.get_placeholders_in_templata(&mut accum, templata);

        if !accum.is_empty() {
            let root_denizen_env = envs.original_calling_env.root_compiling_denizen_env();
            let root_id = root_denizen_env.id();
            let original_calling_env_template_name: IdT<'s, 't> =
                match ITemplateNameT::try_from(root_id.local_name) {
                    Ok(_x) => root_id,
                    Err(_) => {
                        match IInstantiationNameT::try_from(root_id.local_name) {
                            Ok(x) => {
                                *self.typing_interner.intern_id(IdValT {
                                    package_coord: root_id.package_coord,
                                    init_steps: root_id.init_steps,
                                    local_name: INameT::from(x.template()),
                                })
                            }
                            Err(_) => panic!("sanityCheckConclusion: unexpected root id local_name: {:?}", root_id.local_name),
                        }
                    }
                };
            let template_steps = original_calling_env_template_name.steps();
            for placeholder_name in &accum {
                let placeholder_steps = placeholder_name.steps();
                assert!(
                    placeholder_steps.starts_with(&template_steps),
                    "Placeholder {:?} steps don't start with template steps",
                    placeholder_name
                );
            }
        }
    }

    pub fn is_descendant_kind(
        &self,
        _envs: &InferEnv<'s, 't>,
        _coutputs: &mut CompilerOutputs<'s, 't>,
        kind: KindT<'s, 't>,
    ) -> bool {
        match kind {
            KindT::KindPlaceholder(kp) => {
                self.is_descendant(_coutputs, _envs.parent_ranges, _envs.call_location, _envs.original_calling_env,
                    ISubKindTT::KindPlaceholder(kp))
            }
            KindT::RuntimeSizedArray(_) => false,
            KindT::OverloadSet(_) => false,
            KindT::Never(_) => true,
            KindT::StaticSizedArray(_) => false,
            KindT::Struct(s) => {
                self.is_descendant(_coutputs, _envs.parent_ranges, _envs.call_location, _envs.original_calling_env,
                    ISubKindTT::Struct(s))
            }
            KindT::Interface(i) => {
                self.is_descendant(_coutputs, _envs.parent_ranges, _envs.call_location, _envs.original_calling_env,
                    ISubKindTT::Interface(i))
            }
            KindT::Int(_) | KindT::Bool(_) | KindT::Float(_) | KindT::Str(_) | KindT::Void(_) => false,
        }
    }

    pub fn is_ancestor_kind(
        &self,
        _envs: &InferEnv<'s, 't>,
        _coutputs: &mut CompilerOutputs<'s, 't>,
        kind: KindT<'s, 't>,
    ) -> bool {
        match kind {
            KindT::Interface(_) => true,
            _ => false,
        }
    }

    pub fn lookup_templata_imprecise(
        &self,
        envs: InferEnv<'s, 't>,
        state: &mut CompilerOutputs<'s, 't>,
        range: &[RangeS<'s>],
        name: IImpreciseNameS<'s>,
    ) -> Option<ITemplataT<'s, 't>> {
        self.lookup_templata_by_rune(envs.self_env, state, range, name)
    }
    
    pub fn predict_static_sized_array_kind(
        &self,
        _envs: InferEnv<'s, 't>,
        _state: &mut CompilerOutputs<'s, 't>,
        mutability: ITemplataT<'s, 't>,
        variability: ITemplataT<'s, 't>,
        size: ITemplataT<'s, 't>,
        element: CoordT<'s, 't>,
        region: RegionT,
    ) -> StaticSizedArrayTT<'s, 't> {
        self.resolve_static_sized_array(mutability, variability, size, element, region)
    }
    
    pub fn predict_runtime_sized_array_kind(
        &self,
        _envs: InferEnv<'s, 't>,
        _state: &mut CompilerOutputs<'s, 't>,
        element: CoordT<'s, 't>,
        array_mutability: ITemplataT<'s, 't>,
        region: RegionT,
    ) -> RuntimeSizedArrayTT<'s, 't> {
        self.resolve_runtime_sized_array(element, array_mutability, region)
    }
    
    pub fn kind_is_from_template(
        &self,
        _coutputs: &mut CompilerOutputs<'s, 't>,
        actual_citizen_ref: KindT<'s, 't>,
        expected_citizen_templata: ITemplataT<'s, 't>,
    ) -> bool {
        match actual_citizen_ref {
            KindT::RuntimeSizedArray(_) => matches!(expected_citizen_templata, ITemplataT::RuntimeSizedArrayTemplate(_)),
            KindT::StaticSizedArray(_) => matches!(expected_citizen_templata, ITemplataT::StaticSizedArrayTemplate(_)),
            other => {
                match ICitizenTT::try_from(other) {
                    Ok(s) => self.citizen_is_from_template(s, expected_citizen_templata),
                    Err(_) => false,
                }
            }
        }
    }
    
    pub fn get_ancestors(
        &self,
        envs: InferEnv<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        descendant: KindT<'s, 't>,
        include_self: bool,
    ) -> HashSet<KindT<'s, 't>> {
        let mut result: HashSet<KindT<'s, 't>> = HashSet::new();
        if include_self {
            result.insert(descendant);
        }
        match ISubKindTT::try_from(descendant) {
            Ok(s) => {
                for parent in self.get_parents(coutputs, envs.parent_ranges, envs.call_location, envs.original_calling_env, s) {
                    result.insert(KindT::from(parent));
                }
            }
            Err(_) => {}
        }
        result
    }
    
    pub fn struct_is_closure(
        &self,
        _state: &mut CompilerOutputs<'s, 't>,
        _struct_tt: StructTT<'s, 't>,
    ) -> bool {
        panic!("Unimplemented: struct_is_closure");
    }
    
    pub fn predict_function(
        &self,
        envs: InferEnv<'s, 't>,
        _state: &mut CompilerOutputs<'s, 't>,
        _function_range: RangeS<'s>,
        name: StrI<'s>,
        param_coords: &'t [CoordT<'s, 't>],
        return_coord: CoordT<'s, 't>,
    ) -> PrototypeTemplataT<'s, 't> {
        let tmpl = self.typing_interner.intern_predicted_function_template_name(PredictedFunctionTemplateNameT { human_name: name});
        let pred_name = self.typing_interner.intern_predicted_function_name(PredictedFunctionNameValT { template: tmpl, template_args: &[], parameters: param_coords });
        let id = envs.original_calling_env.denizen_id().add_step(self.typing_interner, INameT::PredictedFunction(pred_name));
        let prototype = self.typing_interner.intern_prototype(PrototypeValT { id: IdValT { package_coord: id.package_coord, init_steps: id.init_steps, local_name: id.local_name }, return_type: return_coord });
        PrototypeTemplataT { prototype }
    }
    
    // Per "Compiler/ImplCompiler Name-Collision Disambiguation": Scala's inner
    // IInfererDelegate (solver-side) anonymous-class `resolveFunction`
    // (Compiler.scala:395-413) is flattened onto Rust's Compiler. The 5-arg
    // surface is what the solver calls; the missing 3 args
    // (`call_location`, `context_region`, `verify_conclusions`) are read from
    // the supplied `InferEnv` and a hardcoded `true`, matching Scala's
    // `envs.callLocation`, `envs.contextRegion`, and literal `true`. Body
    // forwards to the sibling 8-arg `resolve_function` (the outer delegate's
    // flatten); Scala's two delegate impls each call `overloadResolver.findFunction`
    // directly, but the Rust port DRYs that through the 8-arg wrapper since both
    // anonymous classes have already been flattened onto the same Compiler.
    pub fn resolve_function_from_infer_env(
        &self,
        env: InferEnv<'s, 't>,
        state: &mut CompilerOutputs<'s, 't>,
        ranges: &[RangeS<'s>],
        name: StrI<'s>,
        param_coords: &[CoordT<'s, 't>],
    ) -> Result<Result<StampFunctionSuccess<'s, 't>, FindFunctionFailure<'s, 't>>, ICompileErrorT<'s, 't>> {
        self.resolve_function(
            env.original_calling_env,
            state,
            ranges,
            env.call_location,
            name,
            param_coords,
            env.context_region,
            true,
        )
    }
    
    pub fn assemble_prototype(
        &self,
        envs: InferEnv<'s, 't>,
        state: &mut CompilerOutputs<'s, 't>,
        _range: RangeS<'s>,
        name: StrI<'s>,
        coords: &'t [CoordT<'s, 't>],
        return_type: CoordT<'s, 't>,
    ) -> &'t PrototypeT<'s, 't> {
        let tmpl = self.typing_interner.intern_function_bound_template_name(FunctionBoundTemplateNameT { human_name: name});
        let bound_name = self.typing_interner.intern_function_bound_name(FunctionBoundNameValT { template: tmpl, template_args: &[], parameters: coords });
        let id = envs.original_calling_env.denizen_id().add_step(self.typing_interner, INameT::FunctionBound(bound_name));
        let result = self.typing_interner.intern_prototype(PrototypeValT { id: IdValT { package_coord: id.package_coord, init_steps: id.init_steps, local_name: id.local_name }, return_type });
        // This is a function bound, and there's no such thing as a function bound with function bounds.
        let empty_bounds = self.typing_interner.alloc(InstantiationBoundArgumentsT {
            rune_to_bound_prototype: self.typing_interner.alloc_index_map_from_iter(empty()),
            rune_to_citizen_rune_to_reachable_prototype: self.typing_interner.alloc_index_map_from_iter(empty()),
            rune_to_bound_impl: self.typing_interner.alloc_index_map_from_iter(empty()),
        });
        state.add_instantiation_bounds(self.opts.global_options.sanity_check, self.typing_interner, envs.original_calling_env.denizen_template_id(), result.id, empty_bounds);
        result
    }
    
    pub fn assemble_impl(
        &self,
        env: InferEnv<'s, 't>,
        range: RangeS<'s>,
        sub_kind: KindT<'s, 't>,
        super_kind: KindT<'s, 't>,
    ) -> IsaTemplataT<'s, 't> {
        let tmpl = self.typing_interner.intern_impl_bound_template_name(
            ImplBoundTemplateNameT { code_location_s: range.begin});
        let bound_name = self.typing_interner.intern_impl_bound_name(
            ImplBoundNameValT { template: tmpl, template_args: &[] });
        let id = *env.original_calling_env.denizen_id().add_step(
            self.typing_interner, INameT::ImplBound(bound_name));
        IsaTemplataT { declaration_range: range, impl_name: id, sub_kind, super_kind }
    }
    
    // Per "Compiler/ImplCompiler Name-Collision Disambiguation": Scala's IInferCompilerDelegate
    // anonymous-class `resolveFunction` (Compiler.scala:455-477) is flattened onto Rust's Compiler.
    pub fn resolve_function(
        &self,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        state: &mut CompilerOutputs<'s, 't>,
        ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        name: StrI<'s>,
        coords: &[CoordT<'s, 't>],
        context_region: RegionT,
        verify_conclusions: bool,
    ) -> Result<Result<StampFunctionSuccess<'s, 't>, FindFunctionFailure<'s, 't>>, ICompileErrorT<'s, 't>> {
        let _ = verify_conclusions;
        self.find_function(
            calling_env,
            state,
            ranges,
            call_location,
            self.scout_arena.intern_imprecise_name(
                IImpreciseNameValS::CodeName(
                    CodeNameS { name })),
            &[],
            &[],
            &[],
            context_region,
            coords,
            &[],
            true)
    }
    
    // Per "Compiler/ImplCompiler Name-Collision Disambiguation": Scala's IStructCompilerDelegate
    // anonymous-class `evaluateGenericFunctionFromNonCallForHeader` (Compiler.scala:536-544) is
    // flattened onto Rust's Compiler struct. Its body delegates to functionCompiler.evaluateGenericFunctionFromNonCall.
    pub fn evaluate_generic_function_from_non_call_for_header(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        function_templata: FunctionTemplataT<'s, 't>,
    ) -> Result<&'t FunctionHeaderT<'s, 't>, ICompileErrorT<'s, 't>> {
        self.evaluate_generic_function_from_non_call(coutputs, parent_ranges, call_location, function_templata)
    }
    
    pub fn scout_expected_function_for_prototype(
        &self,
        _env: IInDenizenEnvironmentT<'s, 't>,
        _coutputs: &mut CompilerOutputs<'s, 't>,
        _call_range: &[RangeS<'s>],
        _call_location: LocationInDenizen<'s>,
        _function_name: IImpreciseNameS<'s>,
        _explicit_template_arg_rules_s: &[IRulexSR<'s>],
        _explicit_template_arg_runes_s: &[IRuneS<'s>],
        _context_region: RegionT,
        _args: &[CoordT<'s, 't>],
        _extra_envs_to_look_in: &[IInDenizenEnvironmentT<'s, 't>],
        _exact: bool,
    ) -> StampFunctionSuccess<'s, 't> {
        panic!("Unimplemented: scout_expected_function_for_prototype");
    }
    
    pub fn generate_function(
        &self,
        _generator: IFunctionGenerator,
        _full_env: &'t FunctionEnvironmentT<'s, 't>,
        _coutputs: &mut CompilerOutputs<'s, 't>,
        _life: LocationInFunctionEnvironmentT<'t>,
        _call_range: &[RangeS<'s>],
        _origin_function: Option<&'s FunctionA<'s>>,
        _param_coords: &[ParameterT<'s, 't>],
        _maybe_ret_coord: Option<CoordT<'s, 't>>,
    ) -> &'t FunctionHeaderT<'s, 't> {
        panic!("Unimplemented: generate_function");
    }
    
    pub fn evaluate<'p>(
        &self,
        _code_map: &FileCoordinateMap<'p, String>,
        package_to_program_a: &PackageCoordinateMap<'s, ProgramA<'s>>,
    ) -> Result<HinputsT<'s, 't>, ICompileErrorT<'s, 't>> {
        let name_to_struct_defined_macro: HashMap<StrI<'s>, OnStructDefinedMacro> = {
            let mut m = HashMap::new();
            m.insert(self.keywords.derive_struct_constructor, OnStructDefinedMacro::StructConstructor);
            m.insert(self.keywords.derive_struct_drop, OnStructDefinedMacro::StructDrop);
            m
        };
        let name_to_interface_defined_macro: HashMap<StrI<'s>, OnInterfaceDefinedMacro> = {
            let mut m = HashMap::new();
            m.insert(self.keywords.derive_interface_drop, OnInterfaceDefinedMacro::InterfaceDrop);
            m.insert(self.keywords.derive_anonymous_substruct, OnInterfaceDefinedMacro::AnonymousInterface);
            m
        };
        let mut id_and_env_entry: Vec<(&'t IdT<'s, 't>, IEnvEntryT<'s, 't>)> = Vec::new();
        for (coord, program_a) in &package_to_program_a.package_coord_to_contents {
            let pkg_top_level_name =
                self.typing_interner.intern_package_top_level_name(PackageTopLevelNameT { });
            let pkg_top_level = INameT::PackageTopLevel(pkg_top_level_name);
            for struct_a in program_a.structs.iter() {
                let struct_template_name = self.translate_struct_name(struct_a.name);
                let struct_name_local: INameT<'s, 't> = match struct_template_name {
                    IStructTemplateNameT::StructTemplate(r) => INameT::StructTemplate(r),
                    IStructTemplateNameT::AnonymousSubstructTemplate(r) => INameT::AnonymousSubstructTemplate(r),
                    IStructTemplateNameT::LambdaCitizenTemplate(_) => panic!("Unimplemented: LambdaCitizenTemplate in struct translation"),
                };
                let package_name = self.typing_interner.intern_id(IdValT {
                    package_coord: coord,
                    init_steps: &[],
                    local_name: pkg_top_level,
                });
                let struct_name_t = package_name.add_step(self.typing_interner, struct_name_local);
                id_and_env_entry.push((struct_name_t, IEnvEntryT::Struct(struct_a)));
                let preprocess_entries = self.preprocess_struct(&name_to_struct_defined_macro, *struct_name_t, struct_a);
                for entry in preprocess_entries {
                    id_and_env_entry.push((entry.0, entry.1));
                }
            }
            for interface_a in program_a.interfaces.iter() {
                let interface_template_name = self.translate_interface_name(*interface_a.name);
                let interface_name_local: INameT<'s, 't> = match interface_template_name {
                    IInterfaceTemplateNameT::InterfaceTemplate(r) => INameT::InterfaceTemplate(r),
                };
                let package_name = self.typing_interner.intern_id(IdValT {
                    package_coord: coord,
                    init_steps: &[],
                    local_name: pkg_top_level,
                });
                let interface_name_t = package_name.add_step(self.typing_interner, interface_name_local);
                id_and_env_entry.push((interface_name_t, IEnvEntryT::Interface(interface_a)));
                let preprocess_entries = self.preprocess_interface(&name_to_interface_defined_macro, *interface_name_t, interface_a);
                for entry in preprocess_entries {
                    id_and_env_entry.push((entry.0, entry.1));
                }
            }
            for impl_a in program_a.impls.iter() {
                let impl_template_name = self.translate_impl_name(impl_a.name);
                let impl_name_local: INameT<'s, 't> = match impl_template_name {
                    IImplTemplateNameT::ImplTemplate(r) => INameT::ImplTemplate(r),
                    IImplTemplateNameT::ImplBoundTemplate(_) => panic!("Unimplemented: ImplBoundTemplate in impl translation"),
                    IImplTemplateNameT::AnonymousSubstructImplTemplate(_) => panic!("Unimplemented: AnonymousSubstructImplTemplate in impl translation"),
                };
                let package_name = self.typing_interner.intern_id(IdValT {
                    package_coord: coord,
                    init_steps: &[],
                    local_name: pkg_top_level,
                });
                let impl_name_t = package_name.add_step(self.typing_interner, impl_name_local);
                id_and_env_entry.push((impl_name_t, IEnvEntryT::Impl(impl_a)));
            }
            for function_a in program_a.functions.iter() {
                let function_template_name =
                    self.translate_generic_function_name(function_a.name);
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
                let package_name = self.typing_interner.intern_id(IdValT {
                    package_coord: coord,
                    init_steps: &[],
                    local_name: pkg_top_level,
                });
                let function_name_t = package_name.add_step(self.typing_interner, function_name_local);
                id_and_env_entry.push((function_name_t, IEnvEntryT::Function(function_a)));
            }
        }

        let pkg_top_level_for_group = INameT::PackageTopLevel(
            self.typing_interner.intern_package_top_level_name(PackageTopLevelNameT { })
        );
        // Per @IIIOZ: IndexMap so iteration at line ~1350 (into global_env.name_to_top_level_environment)
        // preserves id_and_env_entry source order — otherwise the package env's `global_namespaces`
        // slice ends up in random per-process HashMap order, and lookups that walk it nondeterministically
        // pick a different "drop" overload per run.
        let mut namespace_name_to_entries: IndexMap<&'t IdT<'s, 't>, Vec<(INameT<'s, 't>, IEnvEntryT<'s, 't>)>> = IndexMap::new();
        for (name, env_entry) in &id_and_env_entry {
            let package_id = self.typing_interner.intern_id(IdValT {
                package_coord: name.package_coord,
                init_steps: name.init_steps,
                local_name: pkg_top_level_for_group,
            });
            namespace_name_to_entries
                .entry(package_id)
                .or_insert_with(Vec::new)
                .push((name.local_name, *env_entry));
        }
        let mut namespace_name_to_templatas_vec: Vec<(&'t IdT<'s, 't>, &'t TemplatasStoreT<'s, 't>)> = Vec::new();
        for (package_id, entries) in namespace_name_to_entries {
            let mut builder = TemplatasStoreBuilder::new(package_id);
            builder.add_entries(self.scout_arena, entries);
            namespace_name_to_templatas_vec.push((package_id, builder.build_in(self.typing_interner)));
        }

        let builtin_coord: &'s PackageCoordinate<'s> =
            self.scout_arena.intern_package_coordinate(self.keywords.empty_string, &[]);
        let builtin_id = self.typing_interner.intern_id(IdValT {
            package_coord: builtin_coord,
            init_steps: &[],
            local_name: INameT::PackageTopLevel(
                self.typing_interner.intern_package_top_level_name(PackageTopLevelNameT { })
            ),
        });
        let mut builtins_builder = TemplatasStoreBuilder::new(builtin_id);
        let primitives: &[(StrI<'s>, KindT<'s, 't>)] = &[
            (self.keywords.int, KindT::Int(IntT::I32)),
            (self.keywords.i64, KindT::Int(IntT::I64)),
            (self.keywords.bool, KindT::Bool(BoolT)),
            (self.keywords.float, KindT::Float(FloatT)),
            (self.keywords.__never, KindT::Never(NeverT { from_break: false })),
            (self.keywords.str, KindT::Str(StrT)),
            (self.keywords.void, KindT::Void(VoidT)),
        ];
        for (human_name, kind) in primitives {
            let prim = INameT::Primitive(self.typing_interner.intern_primitive_name(
                PrimitiveNameT { human_name: *human_name}
            ));
            let kind_t = ITemplataT::Kind(self.typing_interner.alloc(KindTemplataT { kind: *kind }));
            builtins_builder.name_to_entry.push((prim, IEnvEntryT::Templata(kind_t)));
            if let Some(imprecise) = get_imprecise_name(self.scout_arena, prim) {
                builtins_builder.imprecise_to_entries.entry(imprecise).or_insert_with(Vec::new).push(IEnvEntryT::Templata(kind_t));
            }
        }
        {
            let prim = INameT::Primitive(self.typing_interner.intern_primitive_name(
                PrimitiveNameT { human_name: self.keywords.array}
            ));
            let entry = IEnvEntryT::Templata(
                ITemplataT::RuntimeSizedArrayTemplate(RuntimeSizedArrayTemplateTemplataT { })
            );
            builtins_builder.name_to_entry.push((prim, entry));
            if let Some(imprecise) = get_imprecise_name(self.scout_arena, prim) {
                builtins_builder.imprecise_to_entries.entry(imprecise).or_insert_with(Vec::new).push(entry);
            }
        }
        {
            let prim = INameT::Primitive(self.typing_interner.intern_primitive_name(
                PrimitiveNameT { human_name: self.keywords.static_array}
            ));
            let entry = IEnvEntryT::Templata(
                ITemplataT::StaticSizedArrayTemplate(StaticSizedArrayTemplateTemplataT { })
            );
            builtins_builder.name_to_entry.push((prim, entry));
            if let Some(imprecise) = get_imprecise_name(self.scout_arena, prim) {
                builtins_builder.imprecise_to_entries.entry(imprecise).or_insert_with(Vec::new).push(entry);
            }
        }
        let builtins = builtins_builder.build_in(self.typing_interner);

        let name_to_top_level_environment =
            self.typing_interner.alloc_slice_from_vec(namespace_name_to_templatas_vec);

        let mut name_to_function_body_macro =
            self.typing_interner.alloc_index_map::<StrI<'s>, FunctionBodyMacro>();
        name_to_function_body_macro.insert(self.keywords.abstract_body, FunctionBodyMacro::AbstractBody);
        name_to_function_body_macro.insert(self.keywords.struct_constructor_generator, FunctionBodyMacro::StructConstructor);
        name_to_function_body_macro.insert(self.keywords.drop_generator, FunctionBodyMacro::StructDrop);
        name_to_function_body_macro.insert(self.keywords.vale_runtime_sized_array_len, FunctionBodyMacro::RsaLen);
        name_to_function_body_macro.insert(self.keywords.vale_runtime_sized_array_mut_new, FunctionBodyMacro::RsaMutableNew);
        name_to_function_body_macro.insert(self.keywords.vale_runtime_sized_array_imm_new, FunctionBodyMacro::RsaImmutableNew);
        name_to_function_body_macro.insert(self.keywords.vale_runtime_sized_array_push, FunctionBodyMacro::RsaMutablePush);
        name_to_function_body_macro.insert(self.keywords.vale_runtime_sized_array_pop, FunctionBodyMacro::RsaMutablePop);
        name_to_function_body_macro.insert(self.keywords.vale_runtime_sized_array_capacity, FunctionBodyMacro::RsaMutableCapacity);
        name_to_function_body_macro.insert(self.keywords.vale_static_sized_array_len, FunctionBodyMacro::SsaLen);
        name_to_function_body_macro.insert(self.keywords.vale_runtime_sized_array_drop_into, FunctionBodyMacro::RsaDropInto);
        name_to_function_body_macro.insert(self.keywords.vale_static_sized_array_drop_into, FunctionBodyMacro::SsaDropInto);
        name_to_function_body_macro.insert(self.keywords.vale_lock_weak, FunctionBodyMacro::LockWeak);
        name_to_function_body_macro.insert(self.keywords.vale_same_instance, FunctionBodyMacro::SameInstance);
        name_to_function_body_macro.insert(self.keywords.vale_as_subtype, FunctionBodyMacro::AsSubtype);

        let global_env: &'t GlobalEnvironmentT<'s, 't> = self.typing_interner.alloc(GlobalEnvironmentT {
            name_to_top_level_environment,
            name_to_function_body_macro,
            builtins,
        });

        let mut coutputs = CompilerOutputs::new();

        self.compile_static_sized_array(global_env, &mut coutputs);
        self.compile_runtime_sized_array(global_env, &mut coutputs);

        // Indexing phase
        for (package_id, templatas) in global_env.name_to_top_level_environment {
            let env = make_top_level_environment(global_env, **package_id, self.typing_interner);
            let env_ref: IEnvironmentT<'s, 't> =
                IEnvironmentT::Package(env);
            for (_name, entry) in templatas.name_to_entry.iter() {
                match entry {
                    IEnvEntryT::Struct(struct_a) => {
                        let templata = StructDefinitionTemplataT { declaring_env: env_ref, origin_struct: struct_a };
                        self.precompile_struct(&mut coutputs, templata);
                    }
                    IEnvEntryT::Interface(interface_a) => {
                        let templata = InterfaceDefinitionTemplataT { declaring_env: env_ref, origin_interface: interface_a };
                        self.precompile_interface(&mut coutputs, templata);
                    }
                    _ => {}
                }
            }
        }

        // Compiling phase
        let mut unchecked_defining_conclusionses: Vec<UncheckedDefiningConclusions<'s, 't>> = Vec::new();
        for (package_id, templatas) in global_env.name_to_top_level_environment {
            let env = make_top_level_environment(global_env, **package_id, self.typing_interner);
            let env_ref: IEnvironmentT<'s, 't> =
                IEnvironmentT::Package(env);
            // This makes it so anything starting with an underscore is compiled in the order
            // of their names.
            // AFTERM: is there a better solution here? should we always order things?
            let mut orderable_entries: Vec<(INameT<'s, 't>, IEnvEntryT<'s, 't>)> = Vec::new();
            let mut unordered_entries: Vec<(INameT<'s, 't>, IEnvEntryT<'s, 't>)> = Vec::new();
            for (name, entry) in templatas.name_to_entry.iter() {
                match name {
                    INameT::StructTemplate(s) if s.human_name.0.starts_with("_") =>
                        orderable_entries.push((*name, *entry)),
                    INameT::InterfaceTemplate(i) if i.human_namee.0.starts_with("_") =>
                        orderable_entries.push((*name, *entry)),
                    _ => unordered_entries.push((*name, *entry)),
                }
            }
            // orderedEntries = orderableEntries.sortBy(_._1.humanName.str)
            orderable_entries.sort_by(|(a, _), (b, _)| {
                CitizenTemplateNameT::try_from(*a).unwrap().human_name().0
                    .cmp(CitizenTemplateNameT::try_from(*b).unwrap().human_name().0)
            });
            let all_entries = orderable_entries.into_iter().chain(unordered_entries.into_iter());
            for (_name, entry) in all_entries {
                match entry {
                    IEnvEntryT::Struct(struct_a) => {
                        let templata = StructDefinitionTemplataT { declaring_env: env_ref, origin_struct: struct_a };
                        let unchecked_conclusions =
                            self.compile_struct(&mut coutputs, &[], LocationInDenizen { path: &[] }, templata)?;
                        let maybe_export =
                            struct_a.attributes.iter().find_map(|a| match a { ICitizenAttributeS::Export(e) => Some(e), _ => None });
                        match maybe_export {
                            None => {}
                            Some(export_s) => {
                                let template_name = self.typing_interner.intern_export_template_name(ExportTemplateNameT {
                                    code_loc: struct_a.range.begin,
                                });
                                let template_id_steps: Vec<INameT<'s, 't>> = vec![];
                                let template_id = *self.typing_interner.intern_id(IdValT {
                                    package_coord: package_id.package_coord,
                                    init_steps: &template_id_steps,
                                    local_name: INameT::ExportTemplate(template_name),
                                });
                                let template_id_ref = self.typing_interner.alloc(template_id);
                                let export_outer_templatas = TemplatasStoreBuilder::new(template_id_ref).build_in(self.typing_interner);
                                let _export_outer_env = self.typing_interner.alloc(ExportEnvironmentT {
                                    global_env,
                                    parent_env: env,
                                    template_id,
                                    id: template_id,
                                    templatas: export_outer_templatas,
                                });
                                let placeholdered_export_name = self.typing_interner.intern_export_name(ExportNameT {
                                    template: template_name,
                                    region: RegionT { region: IRegionT::Default },
                                });
                                let placeholdered_export_id_steps: Vec<INameT<'s, 't>> = vec![];
                                let placeholdered_export_id = *self.typing_interner.intern_id(IdValT {
                                    package_coord: package_id.package_coord,
                                    init_steps: &placeholdered_export_id_steps,
                                    local_name: INameT::Export(placeholdered_export_name),
                                });
                                let placeholdered_export_id_ref = self.typing_interner.alloc(placeholdered_export_id);
                                let export_templatas = TemplatasStoreBuilder::new(placeholdered_export_id_ref).build_in(self.typing_interner);
                                let export_env = self.typing_interner.alloc(ExportEnvironmentT {
                                    global_env,
                                    parent_env: env,
                                    template_id,
                                    id: placeholdered_export_id,
                                    templatas: export_templatas,
                                });
                                let export_env_as_iindenizen = IInDenizenEnvironmentT::Export(export_env);
                                let export_call_range = self.typing_interner.alloc_slice_copy(&[struct_a.range]);
                                let export_placeholdered_struct = match self.resolve_struct(
                                    &mut coutputs,
                                    export_env_as_iindenizen,
                                    export_call_range,
                                    LocationInDenizen { path: &[] },
                                    templata,
                                    &[],
                                ) {
                                    IResolveOutcome::ResolveSuccess(s) => self.typing_interner.alloc(s.kind),
                                    IResolveOutcome::ResolveFailure(_f) => panic!("vwat: resolve struct failed for export"),
                                };
                                let export_name = match struct_a.name {
                                    IStructDeclarationNameS::TopLevelStructDeclarationName(n) => n.name,
                                    other => panic!("vwat: {:?}", other),
                                };
                                coutputs.add_kind_export(
                                    struct_a.range,
                                    KindT::Struct(export_placeholdered_struct),
                                    placeholdered_export_id,
                                    export_name,
                                    self.typing_interner,
                                );
                            }
                        }
                        unchecked_defining_conclusionses.push(unchecked_conclusions);
                    }
                    IEnvEntryT::Interface(interface_a) => {
                        let templata = InterfaceDefinitionTemplataT { declaring_env: env_ref, origin_interface: interface_a };
                        let unchecked_conclusions =
                            self.compile_interface(&mut coutputs, &[], LocationInDenizen { path: &[] }, templata)?;
                        let maybe_export =
                            interface_a.attributes.iter().find_map(|a| match a { ICitizenAttributeS::Export(e) => Some(e), _ => None });
                        match maybe_export {
                            None => {}
                            Some(_export_s) => {
                                let template_name = self.typing_interner.intern_export_template_name(ExportTemplateNameT {
                                    code_loc: interface_a.range.begin,
                                });
                                let template_id_steps: Vec<INameT<'s, 't>> = vec![];
                                let template_id = *self.typing_interner.intern_id(IdValT {
                                    package_coord: package_id.package_coord,
                                    init_steps: &template_id_steps,
                                    local_name: INameT::ExportTemplate(template_name),
                                });
                                let template_id_ref = self.typing_interner.alloc(template_id);
                                let export_outer_templatas = TemplatasStoreBuilder::new(template_id_ref).build_in(self.typing_interner);
                                let _export_outer_env = self.typing_interner.alloc(ExportEnvironmentT {
                                    global_env,
                                    parent_env: env,
                                    template_id,
                                    id: template_id,
                                    templatas: export_outer_templatas,
                                });
                                let placeholdered_export_name = self.typing_interner.intern_export_name(ExportNameT {
                                    template: template_name,
                                    region: RegionT { region: IRegionT::Default },
                                });
                                let placeholdered_export_id_steps: Vec<INameT<'s, 't>> = vec![];
                                let placeholdered_export_id = *self.typing_interner.intern_id(IdValT {
                                    package_coord: package_id.package_coord,
                                    init_steps: &placeholdered_export_id_steps,
                                    local_name: INameT::Export(placeholdered_export_name),
                                });
                                let placeholdered_export_id_ref = self.typing_interner.alloc(placeholdered_export_id);
                                let export_templatas = TemplatasStoreBuilder::new(placeholdered_export_id_ref).build_in(self.typing_interner);
                                let export_env = self.typing_interner.alloc(ExportEnvironmentT {
                                    global_env,
                                    parent_env: env,
                                    template_id,
                                    id: placeholdered_export_id,
                                    templatas: export_templatas,
                                });
                                let export_env_as_iindenizen = IInDenizenEnvironmentT::Export(export_env);
                                let export_call_range = self.typing_interner.alloc_slice_copy(&[interface_a.range]);
                                let export_placeholdered_kind = match self.resolve_interface(
                                    &mut coutputs,
                                    export_env_as_iindenizen,
                                    export_call_range,
                                    LocationInDenizen { path: &[] },
                                    templata,
                                    &[],
                                ) {
                                    IResolveOutcome::ResolveSuccess(s) => self.typing_interner.alloc(s.kind),
                                    IResolveOutcome::ResolveFailure(_f) => panic!("vwat: resolve interface failed for export"),
                                };
                                let export_name = interface_a.name.name;
                                coutputs.add_kind_export(
                                    interface_a.range,
                                    KindT::Interface(export_placeholdered_kind),
                                    placeholdered_export_id,
                                    export_name,
                                    self.typing_interner,
                                );
                            }
                        }
                        unchecked_defining_conclusionses.push(unchecked_conclusions);
                    }
                    _ => {}
                }
            }
        }

        // Struct/interface resolution phase
        for unchecked in unchecked_defining_conclusionses.into_iter() {
            let _instantiation_bound_args_unused =
                match self.check_defining_conclusions_and_resolve(
                    unchecked.envs,
                    &mut coutputs,
                    &unchecked.ranges,
                    unchecked.call_location,
                    &unchecked.definition_rules,
                    &[],
                    &unchecked.conclusions,
                ) {
                    Err(_f) => panic!("implement: check_defining_conclusions_and_resolve error in resolution phase"),
                    Ok(c) => c,
                };
        }

        // Impl compile phase
        for (package_id, templatas) in global_env.name_to_top_level_environment {
            let package_env = make_top_level_environment(global_env, **package_id, self.typing_interner);
            let package_env_t: IEnvironmentT<'s, 't> =
                IEnvironmentT::Package(package_env);
            for (_name, entry) in templatas.name_to_entry.iter() {
                match entry {
                    IEnvEntryT::Impl(impl_a) => {
                        let impl_templata = self.typing_interner.alloc(ImplDefinitionTemplataT {
                            env: package_env_t,
                            impl_: impl_a,
                        });
                        self.compile_impl(&mut coutputs, LocationInDenizen { path: &[] }, *impl_templata)?;
                    }
                    _ => {}
                }
            }
        }

        // Function compile phase
        for (package_id, templatas) in global_env.name_to_top_level_environment {
            if !package_id.init_steps.is_empty() {
                continue;
            }
            let global_namespaces: Vec<&TemplatasStoreT<'s, 't>> =
                global_env.name_to_top_level_environment.iter().map(|(_, ts)| *ts).collect();
            let global_namespaces = self.typing_interner.alloc_slice_from_vec(global_namespaces);
            let package_env = self.typing_interner.alloc(PackageEnvironmentT {
                global_env,
                id: **package_id,
                global_namespaces,
            });
            let package_env_t: IEnvironmentT<'s, 't> =
                IEnvironmentT::Package(package_env);
            for (_name, entry) in templatas.name_to_entry.iter() {
                match entry {
                    IEnvEntryT::Function(function_a) => {
                        let templata = FunctionTemplataT {
                            outer_env: package_env_t,
                            function: function_a,
                        };
                        let _header = self.evaluate_generic_function_from_non_call(
                            &mut coutputs, &[], LocationInDenizen { path: &[] }, templata)?;
                        let maybe_export = function_a.attributes.iter().find_map(|a| match a { IFunctionAttributeS::Export(e) => Some(e), _ => None });
                        match maybe_export {
                            None => {}
                            Some(_export_s) => {

                                let template_name = self.typing_interner.intern_export_template_name(ExportTemplateNameT {
                                    code_loc: function_a.range.begin,
                                });
                                let template_id_steps: Vec<INameT<'s, 't>> = vec![];
                                let template_id = *self.typing_interner.intern_id(IdValT {
                                    package_coord: package_id.package_coord,
                                    init_steps: &template_id_steps,
                                    local_name: INameT::ExportTemplate(template_name),
                                });
                                let template_id_ref = self.typing_interner.alloc(template_id);
                                let export_outer_templatas = TemplatasStoreBuilder::new(template_id_ref).build_in(self.typing_interner);
                                let _export_outer_env = self.typing_interner.alloc(ExportEnvironmentT {
                                    global_env,
                                    parent_env: package_env,
                                    template_id,
                                    id: template_id,
                                    templatas: export_outer_templatas,
                                });
                                let region_placeholder = RegionT { region: IRegionT::Default };
                                let placeholdered_export_name = self.typing_interner.intern_export_name(ExportNameT {
                                    template: template_name,
                                    region: region_placeholder,
                                });
                                let placeholdered_export_id_steps: Vec<INameT<'s, 't>> = vec![];
                                let placeholdered_export_id = *self.typing_interner.intern_id(IdValT {
                                    package_coord: package_id.package_coord,
                                    init_steps: &placeholdered_export_id_steps,
                                    local_name: INameT::Export(placeholdered_export_name),
                                });
                                let placeholdered_export_id_ref = self.typing_interner.alloc(placeholdered_export_id);
                                let export_templatas = TemplatasStoreBuilder::new(placeholdered_export_id_ref).build_in(self.typing_interner);
                                let export_env = self.typing_interner.alloc(ExportEnvironmentT {
                                    global_env,
                                    parent_env: package_env,
                                    template_id,
                                    id: placeholdered_export_id,
                                    templatas: export_templatas,
                                });
                                let export_env_as_iindenizen = IInDenizenEnvironmentT::Export(export_env);
                                let call_ranges = self.typing_interner.alloc_slice_copy(&[function_a.range]);
                                let export_placeholdered_prototype =
                                    match self.evaluate_generic_light_function_from_call_for_prototype(
                                        &mut coutputs,
                                        call_ranges,
                                        LocationInDenizen { path: &[] },
                                        export_env_as_iindenizen,
                                        templata,
                                        &[],
                                        region_placeholder,
                                        &[],
                                        &[],
                                    )? {
                                        IResolveFunctionResult::ResolveFunctionSuccess(success) => success.prototype.prototype,
                                        IResolveFunctionResult::ResolveFunctionFailure(failure) => {
                                            return Err(ICompileErrorT::TypingPassResolvingError {
                                                range: self.typing_interner.alloc_slice_copy(&[function_a.range]),
                                                inner: failure.reason,
                                            });
                                        }
                                    };
                                let export_name = match function_a.name {
                                    IFunctionDeclarationNameS::FunctionName(fn_name_s) => fn_name_s.name,
                                    other => panic!("vwat: {:?}", other),
                                };
                                coutputs.add_function_export(
                                    function_a.range,
                                    export_placeholdered_prototype,
                                    placeholdered_export_id,
                                    export_name,
                                    self.typing_interner,
                                );
                            }
                        }
                    }
                    _ => {}
                }
            }
        }

        // Export compile phase
        // packageToProgramA.flatMap({ case (packageCoord, programA) => ... programA.exports.foreach(...) })
        for (coord, program_a) in &package_to_program_a.package_coord_to_contents {
            for export in program_a.exports.iter() {

                let package_top_level_name = self.typing_interner.intern_package_top_level_name(PackageTopLevelNameT { });
                let package_id_steps: Vec<INameT<'s, 't>> = vec![];
                let package_id = *self.typing_interner.intern_id(IdValT {
                    package_coord: coord,
                    init_steps: &package_id_steps,
                    local_name: INameT::PackageTopLevel(package_top_level_name),
                });
                let package_env = make_top_level_environment(global_env, package_id, self.typing_interner);

                let type_rune_t = export.type_rune.clone();

                let template_name = self.typing_interner.intern_export_template_name(ExportTemplateNameT {
                    code_loc: export.range.begin,
                });
                let template_id_steps: Vec<INameT<'s, 't>> = vec![];
                let template_id = *self.typing_interner.intern_id(IdValT {
                    package_coord: coord,
                    init_steps: &template_id_steps,
                    local_name: INameT::ExportTemplate(template_name),
                });
                let template_id_ref = self.typing_interner.alloc(template_id);
                let export_outer_templatas = TemplatasStoreBuilder::new(template_id_ref).build_in(self.typing_interner);
                let _export_outer_env = self.typing_interner.alloc(ExportEnvironmentT {
                    global_env,
                    parent_env: package_env,
                    template_id,
                    id: template_id,
                    templatas: export_outer_templatas,
                });

                let region_placeholder = RegionT { region: IRegionT::Default };

                let placeholdered_export_name = self.typing_interner.intern_export_name(ExportNameT {
                    template: template_name,
                    region: region_placeholder,
                });
                let placeholdered_export_id_steps: Vec<INameT<'s, 't>> = vec![];
                let placeholdered_export_id = *self.typing_interner.intern_id(IdValT {
                    package_coord: coord,
                    init_steps: &placeholdered_export_id_steps,
                    local_name: INameT::Export(placeholdered_export_name),
                });
                let placeholdered_export_id_ref = self.typing_interner.alloc(placeholdered_export_id);
                let export_templatas = TemplatasStoreBuilder::new(placeholdered_export_id_ref).build_in(self.typing_interner);
                let export_env = self.typing_interner.alloc(ExportEnvironmentT {
                    global_env,
                    parent_env: package_env,
                    template_id,
                    id: placeholdered_export_id,
                    templatas: export_templatas,
                });
                let export_env_as_iindenizen = IInDenizenEnvironmentT::Export(export_env);
                let export_env_as_ienv = IEnvironmentT::Export(export_env);

                let rune_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
                    export.rune_to_type.iter().map(|(k, v)| (*k, *v)).collect();

                let parent_ranges_t: &'t [RangeS<'s>] = self.typing_interner.alloc_slice_copy(&[export.range]);

                let complete_define_solve = match self.solve_for_defining(
                    InferEnv {
                        original_calling_env: export_env_as_iindenizen,
                        parent_ranges: parent_ranges_t,
                        call_location: LocationInDenizen { path: &[] },
                        self_env: export_env_as_ienv,
                        context_region: region_placeholder,
                    },
                    &mut coutputs,
                    export.rules,
                    &rune_to_type,
                    parent_ranges_t,
                    LocationInDenizen { path: &[] },
                    &[],
                    &[],
                    &[],
                ) {
                    Err(_f) => panic!("implement: TypingPassDefiningError from export solve_for_defining"),
                    Ok(c) => c,
                };

                match complete_define_solve.conclusions.get(&type_rune_t.rune) {
                    Some(ITemplataT::Kind(kt)) => {
                        coutputs.add_kind_export(
                            export.range,
                            kt.kind,
                            placeholdered_export_id,
                            export.exported_name,
                            self.typing_interner,
                        );
                    }
                    Some(_) => panic!("vimpl"),
                    None => panic!("vfail"),
                }
            }
        }

        // val (interfaceEdgeBlueprints, interfaceToSubCitizenToEdge) =
        //   Profiler.frame(() => { edgeCompiler.compileITables(coutputs) })
        let (interface_edge_blueprints, interface_to_sub_citizen_to_edge) =
            self.compile_i_tables(&mut coutputs);

        // Deferred function compilation loop
        // while (coutputs.peekNextDeferredFunctionBodyCompile().nonEmpty || coutputs.peekNextDeferredFunctionCompile().nonEmpty)
        while coutputs.peek_next_deferred_function_body_compile().is_some() || coutputs.peek_next_deferred_function_compile().is_some() {
            // while (coutputs.peekNextDeferredFunctionCompile().nonEmpty)
            while coutputs.peek_next_deferred_function_compile().is_some() {
                // val nextDeferredEvaluatingFunction = coutputs.peekNextDeferredFunctionCompile().get
                let next_deferred = coutputs.peek_next_deferred_function_compile().unwrap();
                match next_deferred {
                    DeferredActionT::EvaluateFunction {
                        name, calling_env, origin, template_args: _,
                    } => {
                        let name = *name;
                        let calling_env = *calling_env;
                        let origin: &'s FunctionA<'s> = origin;

                        // (nextDeferredEvaluatingFunction.call)(coutputs)
                        // delegate.evaluateGenericFunctionFromNonCallForHeader(
                        //   coutputs, parentRanges, callLocation, FunctionTemplataT(outerEnv, functionA))
                        let outer_env: IEnvironmentT<'s, 't> =
                            IEnvironmentT::from(calling_env);
                        let templata = FunctionTemplataT { outer_env, function: origin };
                        self.evaluate_generic_function_from_non_call_for_header(
                            &mut coutputs, &[], LocationInDenizen { path: &[] }, templata)?;

                        // coutputs.markDeferredFunctionCompiled(nextDeferredEvaluatingFunction.name)
                        coutputs.mark_deferred_function_compiled(name);
                    }
                    _ => panic!("vcurious: unexpected deferred action variant in function-compile loop"),
                }
            }
            // if (coutputs.peekNextDeferredFunctionBodyCompile().nonEmpty)
            if coutputs.peek_next_deferred_function_body_compile().is_some() {
                let next_deferred = coutputs.peek_next_deferred_function_body_compile().unwrap();
                match next_deferred {
                    DeferredActionT::EvaluateFunctionBody {
                        prototype, full_env_snapshot,
                        call_range, call_location, life,
                        attributes_t, params_t, is_destructor,
                        maybe_explicit_return_coord, instantiation_bound_params,
                    } => {
                        let prototype = *prototype;
                        let full_env_snapshot = *full_env_snapshot;
                        let call_range = *call_range;
                        let call_location = *call_location;
                        let life = *life;
                        let attributes_t = *attributes_t;
                        let params_t = *params_t;
                        let is_destructor = *is_destructor;
                        let maybe_explicit_return_coord = *maybe_explicit_return_coord;
                        let instantiation_bound_params = *instantiation_bound_params;

                        // (nextDeferredEvaluatingFunctionBody.call)(coutputs)
                        self.finish_function_maybe_deferred(
                            &mut coutputs, full_env_snapshot, call_range, call_location,
                            life, attributes_t, params_t, is_destructor,
                            maybe_explicit_return_coord, instantiation_bound_params)?;

                        // coutputs.markDeferredFunctionBodyCompiled(nextDeferredEvaluatingFunctionBody.prototypeT)
                        coutputs.mark_deferred_function_body_compiled(prototype);
                    }
                    _ => panic!("implement: unexpected deferred action type"),
                }
            }
        }

        // ensureDeepExports(coutputs)
        self.ensure_deep_exports(&mut coutputs)?;

        // val (reachableInterfaces, reachableStructs, reachableFunctions) =
        //   (coutputs.getAllInterfaces(), coutputs.getAllStructs(), coutputs.getAllFunctions())
        let reachable_interfaces = coutputs.get_all_interfaces();
        let reachable_structs = coutputs.get_all_structs();
        let reachable_functions = coutputs.get_all_functions();

        // interfaceEdgeBlueprints.groupBy(_.interface).mapValues(vassertOne(_))
        let mut interface_to_edge_blueprints: HashMap<IdT<'s, 't>, &'t InterfaceEdgeBlueprintT<'s, 't>> = HashMap::new();
        for blueprint in interface_edge_blueprints.iter() {
            let prev = interface_to_edge_blueprints.insert(blueprint.interface, blueprint);
            assert!(prev.is_none(), "vassertOne: multiple blueprints for same interface");
        }

        // coutputs.getInstantiationNameToFunctionBoundToRune()
        let raw_instantiation_bounds = coutputs.get_instantiation_name_to_function_bound_to_rune();
        let mut instantiation_name_to_instantiation_bounds: HashMap<IdT<'s, 't>, &'t InstantiationBoundArgumentsT<'s, 't>> = HashMap::new();
        for (id, bounds) in raw_instantiation_bounds.iter() {
            instantiation_name_to_instantiation_bounds.insert(*id, *bounds);
        }

        let hinputs = HinputsT {
            interfaces: reachable_interfaces,
            structs: reachable_structs,
            functions: reachable_functions.clone(),
            interface_to_edge_blueprints,
            interface_to_sub_citizen_to_edge,
            instantiation_name_to_instantiation_bounds,
            kind_exports: coutputs.get_kind_exports(),
            function_exports: coutputs.get_function_exports(),
            kind_externs: coutputs.get_kind_externs(),
            function_externs: coutputs.get_function_externs(),
            // sub_citizen_to_interface_to_edge will be populated by instantiator (Scala comment WPBI)
            sub_citizen_to_interface_to_edge: HashMap::new(),
        };

        // vassert(reachableFunctions.toVector.map(_.header.id).distinct.size == reachableFunctions.toVector.map(_.header.id).size)
        {
            let ids: Vec<_> = reachable_functions.iter().map(|f| f.header.id).collect();
            let distinct: HashSet<_> = ids.iter().collect();
            assert!(ids.len() == distinct.len());
        }

        Ok(hinputs)
    }

    pub fn preprocess_struct(
        &self,
        name_to_struct_defined_macro: &HashMap<StrI<'s>, OnStructDefinedMacro>,
        struct_name_t: IdT<'s, 't>,
        struct_a: &'s StructA<'s>,
    ) -> Vec<(&'t IdT<'s, 't>, IEnvEntryT<'s, 't>)> {

        let macro1 = self.scout_arena.alloc(MacroCallS {
            range: struct_a.range,
            include: IMacroInclusionP::CallMacro,
            macro_name: self.keywords.derive_struct_constructor,
        }) as &'s MacroCallS<'s>;
        let macro2 = self.scout_arena.alloc(MacroCallS {
            range: struct_a.range,
            include: IMacroInclusionP::CallMacro,
            macro_name: self.keywords.derive_struct_drop,
        }) as &'s MacroCallS<'s>;
        let default_called_macros = [macro1, macro2];
        let attr_refs: Vec<&'s ICitizenAttributeS<'s>> = struct_a.attributes.iter().collect();
        let macros_to_call = self.determine_macros_to_call(
            name_to_struct_defined_macro,
            &default_called_macros[..],
            &[struct_a.range],
            &attr_refs,
        );
        let mut result = Vec::new();
        for macro_ in macros_to_call {
            for (id, entry) in macro_.get_struct_sibling_entries(self, struct_name_t, struct_a) {
                let id_val = IdValT { package_coord: id.package_coord, init_steps: id.init_steps, local_name: id.local_name };
                result.push((self.typing_interner.intern_id(id_val), entry));
            }
        }
        result
    }
    
    pub fn preprocess_interface(
        &self,
        name_to_interface_defined_macro: &HashMap<StrI<'s>, OnInterfaceDefinedMacro>,
        _interface_name_t: IdT<'s, 't>,
        interface_a: &'s InterfaceA<'s>,
    ) -> Vec<(&'t IdT<'s, 't>, IEnvEntryT<'s, 't>)> {

        let macro1 = self.scout_arena.alloc(MacroCallS {
            range: interface_a.range,
            include: IMacroInclusionP::CallMacro,
            macro_name: self.keywords.derive_interface_drop,
        }) as &'s MacroCallS<'s>;
        let macro2 = self.scout_arena.alloc(MacroCallS {
            range: interface_a.range,
            include: IMacroInclusionP::CallMacro,
            macro_name: self.keywords.derive_anonymous_substruct,
        }) as &'s MacroCallS<'s>;
        let default_called_macros = [macro1, macro2];
        let attr_refs: Vec<&'s ICitizenAttributeS<'s>> = interface_a.attributes.iter().collect();
        let macros_to_call = self.determine_macros_to_call(
            name_to_interface_defined_macro,
            &default_called_macros[..],
            &[interface_a.range],
            &attr_refs,
        );
        let mut result = Vec::new();
        for macro_ in macros_to_call {
            for (id, entry) in macro_.get_interface_sibling_entries(self, _interface_name_t, interface_a) {
                let id_val = IdValT { package_coord: id.package_coord, init_steps: id.init_steps, local_name: id.local_name };
                result.push((self.typing_interner.intern_id(id_val), entry));
            }
        }
        result
    }
    
    pub fn determine_macros_to_call<T: Clone>(
        &self,
        name_to_macro: &HashMap<StrI<'s>, T>,
        default_called_macros: &[&'s MacroCallS<'s>],
        parent_ranges: &[RangeS<'s>],
        attributes: &[&'s ICitizenAttributeS<'s>],
    ) -> Vec<T> {
        let macros_to_call: Vec<&'s MacroCallS<'s>> =
            attributes.iter().fold(default_called_macros.to_vec(), |macros_to_call, attr| {
                match attr {
                    ICitizenAttributeS::MacroCall(mc) if mc.include == IMacroInclusionP::CallMacro => {
                        if macros_to_call.iter().any(|m| m.macro_name == mc.macro_name) {
                            panic!("Calling macro twice: {:?}", mc.macro_name);
                        }
                        let mut result = macros_to_call;
                        result.push(mc);
                        result
                    }
                    ICitizenAttributeS::MacroCall(mc) if mc.include == IMacroInclusionP::DontCallMacro => {
                        macros_to_call.into_iter().filter(|m| m.macro_name != mc.macro_name).collect()
                    }
                    _ => macros_to_call,
                }
            });
        macros_to_call.into_iter().map(|macro_call| {
            match name_to_macro.get(&macro_call.macro_name) {
                None => panic!("Macro not found: {:?}", macro_call.macro_name),
                Some(m) => m.clone(),
            }
        }).collect()
    }
    
    pub fn ensure_deep_exports(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
    ) -> Result<(), ICompileErrorT<'s, 't>> {
        // val packageToKindToExport =
        //   coutputs.getKindExports
        //     .map(kindExport => (kindExport.id.packageCoord, kindExport.tyype, kindExport))
        //     .groupBy(_._1)
        //     .mapValues(
        //       _.map(x => (x._2, x._3))
        //         .groupBy(_._1)
        //         .mapValues({
        //           case Vector() => vwat()
        //           case Vector(only) => only
        //           case multiple => throw CompileErrorExceptionT(TypeExportedMultipleTimes(...))
        //         }))
        let kind_export_triples: Vec<(&'s PackageCoordinate<'s>, KindT<'s, 't>, &'t KindExportT<'s, 't>)> =
            coutputs.get_kind_exports().iter()
                .map(|ke| (ke.id.package_coord, ke.tyype, *ke))
                .collect();
        // Per @IIIOZ: IndexMap so iteration at the package/kind loops below is deterministic.
        // Upstream kind_export_triples is from coutputs.get_kind_exports() (Vec, deterministic).
        let mut grouped_by_package: IndexMap<&'s PackageCoordinate<'s>, Vec<(KindT<'s, 't>, &'t KindExportT<'s, 't>)>> = IndexMap::new();
        for (pc, k, ke) in kind_export_triples.into_iter() {
            grouped_by_package.entry(pc).or_insert_with(Vec::new).push((k, ke));
        }
        let package_to_kind_to_export: IndexMap<&'s PackageCoordinate<'s>, IndexMap<KindT<'s, 't>, &'t KindExportT<'s, 't>>> = {
            let mut result: IndexMap<&'s PackageCoordinate<'s>, IndexMap<KindT<'s, 't>, &'t KindExportT<'s, 't>>> = IndexMap::new();
            for (pc, kind_pairs) in grouped_by_package.into_iter() {
                let mut grouped_by_kind: IndexMap<KindT<'s, 't>, Vec<&'t KindExportT<'s, 't>>> = IndexMap::new();
                for (k, ke) in kind_pairs.into_iter() {
                    grouped_by_kind.entry(k).or_insert_with(Vec::new).push(ke);
                }
                let mut inner: IndexMap<KindT<'s, 't>, &'t KindExportT<'s, 't>> = IndexMap::new();
                for (k, exports) in grouped_by_kind.into_iter() {
                    let only = match exports.as_slice() {
                        [] => panic!("vwat"),
                        [only] => *only,
                        _ => {
                            let exports_copies: Vec<KindExportT<'s, 't>> = exports.iter().map(|ke| KindExportT {
                                range: ke.range,
                                tyype: ke.tyype,
                                id: ke.id,
                                exported_name: ke.exported_name,
                            }).collect();
                            let exports_slice = self.typing_interner.alloc_slice_from_vec(exports_copies);
                            let range_slice = self.typing_interner.alloc_slice_copy(&[exports[0].range]);
                            return Err(ICompileErrorT::TypeExportedMultipleTimes {
                                range: range_slice,
                                paackage: *exports[0].id.package_coord,
                                exports: exports_slice,
                            });
                        }
                    };
                    inner.insert(k, only);
                }
                result.insert(pc, inner);
            }
            result
        };

        // coutputs.getFunctionExports.foreach(funcExport => {
        //   val exportedKindToExport = packageToKindToExport.getOrElse(funcExport.exportId.packageCoord, Map())
        //   (Vector(funcExport.prototype.returnType) ++ funcExport.prototype.paramTypes)
        //     .foreach(paramType => {
        //       if (!Compiler.isPrimitive(paramType.kind) && !exportedKindToExport.contains(paramType.kind)) {
        //         throw CompileErrorExceptionT(ExportedFunctionDependedOnNonExportedKind(...))
        //       }
        //     })
        // })
        let empty_kind_map: IndexMap<KindT<'s, 't>, &'t KindExportT<'s, 't>> = IndexMap::new();
        for func_export in coutputs.get_function_exports().iter() {
            let exported_kind_to_export = package_to_kind_to_export.get(func_export.export_id.package_coord).unwrap_or(&empty_kind_map);
            let all_types: Vec<CoordT<'s, 't>> = once(func_export.prototype.return_type).chain(func_export.prototype.param_types().iter().copied()).collect();
            for param_type in all_types {
                if !self.is_primitive(param_type.kind) && !exported_kind_to_export.contains_key(&param_type.kind) {
                    let range_t = self.typing_interner.alloc_slice_copy(&[func_export.range]);
                    let signature_t = self.typing_interner.alloc(func_export.prototype.to_signature());
                    return Err(ICompileErrorT::ExportedFunctionDependedOnNonExportedKind {
                        range: range_t,
                        paackage: *func_export.export_id.package_coord,
                        signature: signature_t,
                        non_exported_kind: param_type.kind,
                    });
                }
            }
        }

        for function_extern in coutputs.get_function_externs().iter() {
            let exported_kind_to_export = package_to_kind_to_export.get(function_extern.extern_placeholdered_id.package_coord).unwrap_or(&empty_kind_map);
            let all_types: Vec<CoordT<'s, 't>> = once(function_extern.prototype.return_type).chain(function_extern.prototype.param_types().iter().copied()).collect();
            for param_type in all_types {
                if !self.is_primitive(param_type.kind) && !exported_kind_to_export.contains_key(&param_type.kind) {
                    // Method-own and container-inherited template params surface here as
                    // placeholders at definition time (e.g. `extern func bar<C>(c C)` inside
                    // `extern struct Foo<A>` has C and A as KindPlaceholderTs in the wrapper
                    // prototype). Placeholders are substitution slots, not concrete types; the
                    // actual concrete kind for each monomorphization is what matters for ABI,
                    // and gets checked at instantiation.
                    let kind_is_fine_in_extern_func = match param_type.kind {
                        KindT::Struct(s) => coutputs.lookup_struct(s.id, self).attributes.iter().any(|a| matches!(a, ICitizenAttributeT::Extern(_))),
                        KindT::KindPlaceholder(_) => true,
                        _ => false,
                    };
                    if !kind_is_fine_in_extern_func {
                        let range_t = self.typing_interner.alloc_slice_copy(&[function_extern.range]);
                        let signature_t = self.typing_interner.alloc(function_extern.prototype.to_signature());
                        return Err(ICompileErrorT::ExternFunctionDependedOnNonExportedKind {
                            range: range_t,
                            paackage: *function_extern.extern_placeholdered_id.package_coord,
                            signature: signature_t,
                            non_exported_kind: param_type.kind,
                        });
                    }
                }
            }
        }

        // packageToKindToExport.foreach((packageCoord, exportedKindToExport) =>
        //   exportedKindToExport.foreach((exportedKind, (kind, export)) =>
        //     exportedKind match { case StructTT(_) => ...; case contentsStaticSizedArrayTT(...) => ...; ... }))
        for (package_coord, exported_kind_to_export) in package_to_kind_to_export.iter() {
            for (exported_kind, export) in exported_kind_to_export.iter() {
                match exported_kind {
                    KindT::Struct(sr) => {
                        let struct_def = coutputs.lookup_struct(sr.id, self);
                        let substituter =
                            self.get_placeholder_substituter(
                                self.opts.global_options.sanity_check,
                                struct_def.template_name,
                                sr.id,
                                IBoundArgumentsSource::InheritBoundsFromTypeItself,
                            );
                        for member in struct_def.members.iter() {
                            match member {
                                IStructMemberT::Variadic(_) => {
                                    panic!("implement: ensure_deep_exports — VariadicStructMemberT");
                                }
                                IStructMemberT::Normal(NormalStructMemberT { tyype: IMemberTypeT::Address(_), .. }) => {
                                    panic!("implement: ensure_deep_exports — AddressMemberTypeT");
                                }
                                IStructMemberT::Normal(NormalStructMemberT { tyype: IMemberTypeT::Reference(ReferenceMemberTypeT { reference: unsubstituted_member_coord }), .. }) => {
                                    let member_coord = substituter.substitute_for_coord(coutputs, *unsubstituted_member_coord);
                                    let member_kind = member_coord.kind;
                                    if struct_def.mutability == ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable })
                                        && !self.is_primitive(member_kind)
                                        && !exported_kind_to_export.contains_key(&member_kind)
                                    {
                                        let range_t = self.typing_interner.alloc_slice_copy(&[export.range]);
                                        return Err(ICompileErrorT::ExportedImmutableKindDependedOnNonExportedKind {
                                            range: range_t,
                                            paackage: **package_coord,
                                            exported_kind: *exported_kind,
                                            non_exported_kind: member_kind,
                                        });
                                    }
                                }
                            }
                        }
                    }
                    KindT::StaticSizedArray(as_tt) => {
                        let element_kind = as_tt.element_type().kind;
                        if as_tt.mutability() == ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable })
                            && !self.is_primitive(element_kind)
                            && !exported_kind_to_export.contains_key(&element_kind)
                        {
                            let range_t = self.typing_interner.alloc_slice_copy(&[export.range]);
                            return Err(ICompileErrorT::ExportedImmutableKindDependedOnNonExportedKind {
                                range: range_t,
                                paackage: **package_coord,
                                exported_kind: *exported_kind,
                                non_exported_kind: element_kind,
                            });
                        }
                    }
                    KindT::RuntimeSizedArray(rsa) => {
                        let mutability = match rsa.name.local_name {
                            INameT::RuntimeSizedArray(rsan) => rsan.arr.mutability,
                            _ => panic!("vwat"),
                        };
                        let element_kind = match rsa.name.local_name {
                            INameT::RuntimeSizedArray(rsan) => rsan.arr.element_type.kind,
                            _ => panic!("vwat"),
                        };
                        if mutability == ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable })
                            && !self.is_primitive(element_kind)
                            && !exported_kind_to_export.contains_key(&element_kind)
                        {
                            let range_t = self.typing_interner.alloc_slice_copy(&[export.range]);
                            return Err(ICompileErrorT::ExportedImmutableKindDependedOnNonExportedKind {
                                range: range_t,
                                paackage: **package_coord,
                                exported_kind: *exported_kind,
                                non_exported_kind: element_kind,
                            });
                        }
                    }
                    KindT::Interface(_) => {}
                    KindT::KindPlaceholder(_) | KindT::OverloadSet(_) |
                    KindT::Void(_) | KindT::Int(_) | KindT::Bool(_) | KindT::Str(_) | KindT::Float(_) | KindT::Never(_) => {
                        panic!("vwat: unexpected kind in exportedKindToExport");
                    }
                }
            }
        }
        Ok(())
    }
    
    pub fn is_root_function(
        &self,
        function_a: &'s FunctionA<'s>,
    ) -> bool {
        panic!("Unimplemented: Slab 15 — body migration");
    }
    
    pub fn is_root_struct(
        &self,
        struct_a: &'s StructA<'s>,
    ) -> bool {
        panic!("Unimplemented: Slab 15 — body migration");
    }
    
    pub fn is_root_interface(
        &self,
        interface_a: &'s InterfaceA<'s>,
    ) -> bool {
        panic!("Unimplemented: Slab 15 — body migration");
    }
    
    pub fn consecutive(
        &self,
        exprs: &[ReferenceExpressionTE<'s, 't>],
    ) -> ReferenceExpressionTE<'s, 't> {
        match exprs {
            [] => panic!("Shouldn't have zero-element consecutors!"),
            [only] => *only,
            _ => {
                let flattened: Vec<ReferenceExpressionTE<'s, 't>> =
                    exprs.iter().flat_map(|e| {
                        match e {
                            ReferenceExpressionTE::Consecutor(c) => c.exprs.to_vec(),
                            other => vec![*other],
                        }
                    }).collect();

                let without_init_voids: Vec<ReferenceExpressionTE<'s, 't>> = {
                    let (init, last) = flattened.split_at(flattened.len() - 1);
                    let mut filtered: Vec<ReferenceExpressionTE<'s, 't>> = init.iter()
                        .filter(|e| !matches!(e, ReferenceExpressionTE::VoidLiteral(_)))
                        .copied()
                        .collect();
                    filtered.push(last[0]);
                    filtered
                };

                match without_init_voids.as_slice() {
                    [] => panic!("Shouldn't have zero-element consecutors!"),
                    [only] => *only,
                    _ => {
                        let exprs_slice = self.typing_interner.alloc_slice_copy(&without_init_voids);
                        ReferenceExpressionTE::Consecutor(self.typing_interner.alloc(ConsecutorTE { exprs: exprs_slice }))
                    }
                }
            }
        }
    }
    
    pub fn is_primitive(
        &self,
        kind: KindT<'s, 't>,
    ) -> bool {
        match kind {
            KindT::Void(_) | KindT::Int(_) | KindT::Bool(_) | KindT::Str(_) | KindT::Never(_) | KindT::Float(_) => true,
            KindT::KindPlaceholder(_) => false,
            KindT::Struct(_) => false,
            KindT::Interface(_) => false,
            KindT::StaticSizedArray(_) => false,
            KindT::RuntimeSizedArray(_) => false,
            KindT::OverloadSet(_) => false,
        }
    }
    
    pub fn get_mutabilities(
        &self,
        coutputs: &CompilerOutputs<'s, 't>,
        concrete_values2: &[KindT<'s, 't>],
    ) -> Vec<ITemplataT<'s, 't>> {
        panic!("Unimplemented: Slab 15 — body migration");
    }
    
    pub fn get_mutability(
        &self,
        coutputs: &CompilerOutputs<'s, 't>,
        concrete_value2: KindT<'s, 't>,
    ) -> ITemplataT<'s, 't> {
        match concrete_value2 {
            KindT::Never(_) => ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }),
            KindT::Int(_) => ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }),
            KindT::Float(_) => ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }),
            KindT::Bool(_) => ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }),
            KindT::Str(_) => ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }),
            KindT::Void(_) => ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }),
            KindT::KindPlaceholder(kp) => coutputs.lookup_mutability(self.get_placeholder_template(kp.id)),
            KindT::RuntimeSizedArray(rsa) => {
                match rsa.name.local_name {
                    INameT::RuntimeSizedArray(rsan) => rsan.arr.mutability,
                    _ => panic!("Expected RuntimeSizedArray local_name in get_mutability"),
                }
            }
            KindT::StaticSizedArray(ssa) => ssa.mutability(),
            KindT::Struct(s) => coutputs.lookup_mutability(self.get_struct_template(s.id)),
            KindT::Interface(i) => coutputs.lookup_mutability(self.get_interface_template(i.id)),
            KindT::OverloadSet(_) => ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }),
        }
    }
    
}
