use crate::higher_typing::ast::FunctionA;
use crate::interner::{Interner, StrI};
use std::collections::{HashMap, HashSet};
use indexmap::IndexMap;
use crate::utils::range::RangeS;
use crate::postparsing::ast::LocationInDenizen;
use crate::postparsing::names::*;
use crate::postparsing::*;
use crate::typing::hinputs_t::*;
use crate::typing::compilation::TypingPassOptions;
use crate::utils::code_hierarchy::PackageCoordinate;
use crate::typing::infer_compiler::{InitialKnown, InitialSend};
use crate::typing::ast::ast::*;
use crate::typing::ast::citizens::*;
use crate::typing::ast::expressions::*;
use crate::typing::env::environment::*;
use crate::typing::env::function_environment_t::*;
use crate::typing::env::i_env_entry::*;
use crate::typing::names::names::*;
use crate::typing::types::types::*;
use crate::typing::templata::templata::*;
use crate::typing::typing_interner::TypingInterner;
use crate::typing::compiler::Compiler;

/// Temporary state (see @TFITCX)
pub enum DeferredActionT<'s, 't>
where 's: 't,
{
    EvaluateFunctionBody {
        prototype: &'t PrototypeT<'s, 't>,
        full_env_snapshot: &'t FunctionEnvironmentT<'s, 't>,
        call_range: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        life: LocationInFunctionEnvironmentT<'t>,
        attributes_t: &'t [IFunctionAttributeT<'s>],
        params_t: &'t [ParameterT<'s, 't>],
        is_destructor: bool,
        maybe_explicit_return_coord: Option<CoordT<'s, 't>>,
        instantiation_bound_params: &'t InstantiationBoundArgumentsT<'s, 't>,
    },
    
    EvaluateFunction {
        name: &'t IdT<'s, 't>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        origin: &'s FunctionA<'s>,
        template_args: &'t [ITemplataT<'s, 't>],
    },
    
}
/// Temporary state (see @TFITCX)
pub struct CompilerOutputs<'s, 't>
where 's: 't,
{
    pub return_types_by_signature:
        HashMap<SignatureT<'s, 't>, CoordT<'s, 't>>,
    // Per @IIIOZ, iterated by get_all_functions → IndexMap for cross-run determinism.
    pub signature_to_function:
        IndexMap<SignatureT<'s, 't>, &'t FunctionDefinitionT<'s, 't>>,

    pub function_declared_names:
        HashMap<IdT<'s, 't>, RangeS<'s>>,
    pub type_declared_names:
        HashSet<IdT<'s, 't>>,

    pub function_name_to_outer_env:
        HashMap<IdT<'s, 't>, IInDenizenEnvironmentT<'s, 't>>,
    pub function_name_to_inner_env:
        HashMap<IdT<'s, 't>, IInDenizenEnvironmentT<'s, 't>>,
    pub type_name_to_outer_env:
        HashMap<IdT<'s, 't>, IInDenizenEnvironmentT<'s, 't>>,
    pub type_name_to_inner_env:
        HashMap<IdT<'s, 't>, IInDenizenEnvironmentT<'s, 't>>,

    pub type_name_to_mutability:
        HashMap<IdT<'s, 't>, ITemplataT<'s, 't>>,
    pub interface_name_to_sealed:
        HashMap<IdT<'s, 't>, bool>,

    // Per @IIIOZ, iterated by get_all_structs / get_all_interfaces → IndexMap for cross-run determinism.
    pub struct_template_name_to_definition:
        IndexMap<IdT<'s, 't>, &'t StructDefinitionT<'s, 't>>,
    pub interface_template_name_to_definition:
        IndexMap<IdT<'s, 't>, &'t InterfaceDefinitionT<'s, 't>>,

    pub all_impls:
        HashMap<IdT<'s, 't>, &'t ImplT<'s, 't>>,
    pub sub_citizen_template_to_impls:
        HashMap<IdT<'s, 't>, Vec<&'t ImplT<'s, 't>>>,
    pub super_interface_template_to_impls:
        HashMap<IdT<'s, 't>, Vec<&'t ImplT<'s, 't>>>,

    pub kind_exports: Vec<&'t KindExportT<'s, 't>>,
    pub function_exports: Vec<&'t FunctionExportT<'s, 't>>,
    pub kind_externs: Vec<&'t KindExternT<'s, 't>>,
    pub function_externs: Vec<&'t FunctionExternT<'s, 't>>,

    pub instantiation_name_to_bounds:
        HashMap<IdT<'s, 't>, &'t InstantiationBoundArgumentsT<'s, 't>>,

    // Per @IIIOZ, deferred queues are IndexMap so drain order is insertion-ordered and deterministic across runs.
    pub deferred_function_body_compiles: IndexMap<PrototypeT<'s, 't>, DeferredActionT<'s, 't>>,
    pub deferred_function_compiles: IndexMap<IdT<'s, 't>, DeferredActionT<'s, 't>>,
    pub finished_deferred_function_body_compiles:
        HashSet<PrototypeT<'s, 't>>,
    pub finished_deferred_function_compiles:
        HashSet<IdT<'s, 't>>,
}

impl<'s, 't> CompilerOutputs<'s, 't>
where 's: 't,
{
    pub fn new() -> Self {
        Self {
            return_types_by_signature: HashMap::new(),
            signature_to_function: IndexMap::new(),
            function_declared_names: HashMap::new(),
            type_declared_names: HashSet::new(),
            function_name_to_outer_env: HashMap::new(),
            function_name_to_inner_env: HashMap::new(),
            type_name_to_outer_env: HashMap::new(),
            type_name_to_inner_env: HashMap::new(),
            type_name_to_mutability: HashMap::new(),
            interface_name_to_sealed: HashMap::new(),
            struct_template_name_to_definition: IndexMap::new(),
            interface_template_name_to_definition: IndexMap::new(),
            all_impls: HashMap::new(),
            sub_citizen_template_to_impls: HashMap::new(),
            super_interface_template_to_impls: HashMap::new(),
            kind_exports: Vec::new(),
            function_exports: Vec::new(),
            kind_externs: Vec::new(),
            function_externs: Vec::new(),
            instantiation_name_to_bounds: HashMap::new(),
            deferred_function_body_compiles: IndexMap::new(),
            deferred_function_compiles: IndexMap::new(),
            finished_deferred_function_body_compiles: HashSet::new(),
            finished_deferred_function_compiles: HashSet::new(),
        }
    }
    
    pub fn count_denizens(&self) -> i32 {
        panic!("Unimplemented: Slab 10 — body migration");
    }
    
    pub fn peek_next_deferred_function_body_compile(&self) -> Option<&DeferredActionT<'s, 't>> {
        self.deferred_function_body_compiles.values().next()
    }
    
    pub fn mark_deferred_function_body_compiled(
        &mut self,
        prototype_t: &'t PrototypeT<'s, 't>,
    ) {
        // vassert(prototypeT == vassertSome(deferredFunctionBodyCompiles.headOption)._1)
        let first_key = *self.deferred_function_body_compiles.keys().next().unwrap();
        assert!(*prototype_t == first_key);
        // finishedDeferredFunctionBodyCompiles += prototypeT
        self.finished_deferred_function_body_compiles.insert(*prototype_t);
        // deferredFunctionBodyCompiles -= prototypeT
        self.deferred_function_body_compiles.shift_remove(prototype_t);
    }
    
    pub fn peek_next_deferred_function_compile(&self) -> Option<&DeferredActionT<'s, 't>> {
        self.deferred_function_compiles.values().next()
    }
    
    pub fn mark_deferred_function_compiled(
        &mut self,
        name: &'t IdT<'s, 't>,
    ) {
        // vassert(name == vassertSome(deferredFunctionCompiles.headOption)._1)
        let first_key = *self.deferred_function_compiles.keys().next().unwrap();
        assert!(*name == first_key);
        // finishedDeferredFunctionCompiles += name
        self.finished_deferred_function_compiles.insert(*name);
        // deferredFunctionCompiles -= name
        self.deferred_function_compiles.shift_remove(name);
    }
    
    pub fn get_instantiation_name_to_function_bound_to_rune(
        &self,
    ) -> HashMap<IdT<'s, 't>, &'t InstantiationBoundArgumentsT<'s, 't>> {
        self.instantiation_name_to_bounds.clone()
    }
    
    pub fn lookup_function(
        &self,
        signature: &'t SignatureT<'s, 't>,
    ) -> Option<&'t FunctionDefinitionT<'s, 't>> {
        // signatureToFunction.get(signature)
        self.signature_to_function.get(signature).copied()
    }
    
    pub fn get_instantiation_bounds(
        &self,
        interner: &TypingInterner<'s, 't>,
        instantiation_id: IdT<'s, 't>,
    ) -> Option<&'t InstantiationBoundArgumentsT<'s, 't>> {
        let instantiation_id_ref = interner.intern_id(IdValT {
            package_coord: instantiation_id.package_coord,
            init_steps: instantiation_id.init_steps,
            local_name: instantiation_id.local_name,
        });
        self.instantiation_name_to_bounds.get(instantiation_id_ref).copied()
    }
    
    pub fn add_instantiation_bounds(
        &mut self,
        _sanity_check: bool,
        interner: &TypingInterner<'s, 't>,
        _original_calling_template_id: IdT<'s, 't>,
        instantiation_id: IdT<'s, 't>,
        instantiation_bound_args: &'t InstantiationBoundArgumentsT<'s, 't>,
    ) {
        for (_rune, reachable_bound_args) in &instantiation_bound_args.rune_to_citizen_rune_to_reachable_prototype {
            for (_callee_rune, reachable_prototype) in &reachable_bound_args.citizen_rune_to_reachable_prototype {
                match reachable_prototype.id.local_name {
                    INameT::FunctionBound(_) => {
                        let reachable_func_super_template_id_init_steps =
                            Compiler::get_super_template(interner, reachable_prototype.id).init_steps;
                        let original_calling_super_template_id_init_steps =
                            Compiler::get_super_template(interner, _original_calling_template_id).init_steps;
                        assert!(
                            reachable_func_super_template_id_init_steps.starts_with(original_calling_super_template_id_init_steps),
                            "addInstantiationBounds: reachable func super template id init steps doesn't start with original calling super template id init steps"
                        );
                    }
                    _ => {}
                }
            }
        }
        for (_rune, caller_bound_arg_function) in &instantiation_bound_args.rune_to_bound_prototype {
            match caller_bound_arg_function.id.local_name {
                INameT::FunctionBound(_) => {
                    if _sanity_check {
                        let caller_bound_arg_func_super_template_id_init_steps =
                            Compiler::get_super_template(interner, caller_bound_arg_function.id).init_steps;
                        let original_calling_super_template_id_steps =
                            Compiler::get_root_super_template(interner, _original_calling_template_id).init_steps;
                        assert!(
                            caller_bound_arg_func_super_template_id_init_steps.starts_with(original_calling_super_template_id_steps),
                            "addInstantiationBounds: caller bound arg func super template id init steps doesn't start with original calling super template id steps"
                        );
                    }
                }
                _ => {}
            }
        }

        let instantiation_id_ref = interner.intern_id(IdValT {
            package_coord: instantiation_id.package_coord,
            init_steps: instantiation_id.init_steps,
            local_name: instantiation_id.local_name,
        });
        if let Some(existing) = self.instantiation_name_to_bounds.get(instantiation_id_ref) {
            // Theres some ambiguities or something here. sometimes when we evaluate
            // the same thing twice we get different results.
            // It's gonna be especially tricky because we get each function bounds from the overload
            // resolver which only returns one.
            // We avoid this by merging all sorts of function bounds, see MFBFDP.
            assert!(
                existing.rune_to_bound_prototype == instantiation_bound_args.rune_to_bound_prototype &&
                existing.rune_to_citizen_rune_to_reachable_prototype == instantiation_bound_args.rune_to_citizen_rune_to_reachable_prototype &&
                existing.rune_to_bound_impl == instantiation_bound_args.rune_to_bound_impl,
                "addInstantiationBounds: existing bounds != new bounds"
            );
            return;
        }

        self.instantiation_name_to_bounds.insert(*instantiation_id_ref, instantiation_bound_args);
    }
    
    pub fn declare_function_return_type(
        &mut self,
        signature: &'t SignatureT<'s, 't>,
        return_type_2: CoordT<'s, 't>,
    ) {
        match self.return_types_by_signature.get(signature) {
            None => {}
            Some(existing) => assert!(*existing == return_type_2),
        }
        self.return_types_by_signature.insert(*signature, return_type_2);
    }
    
    pub fn add_function(
        &mut self,
        signature: &'t SignatureT<'s, 't>,
        function: &'t FunctionDefinitionT<'s, 't>,
    ) {
        assert!(
            function.body.result().coord.kind == KindT::Never(NeverT { from_break: false }) ||
            function.body.result().coord == function.header.return_type);

        assert!(!self.signature_to_function.contains_key(signature),
            "wot");

        self.signature_to_function.insert(*signature, function);
    }
    
    pub fn declare_function(
        &mut self,
        call_ranges: &[RangeS<'s>],
        name: &'t IdT<'s, 't>,
    ) {
        // functionDeclaredNames.get(name) match {
        //   case Some(oldFunctionRange) => {
        //     throw CompileErrorExceptionT(FunctionAlreadyExists(oldFunctionRange, callRanges.head, name))
        //   }
        //   case None =>
        // }
        if let Some(_old_function_range) = self.function_declared_names.get(name) {
            panic!("implement CompileErrorExceptionT(FunctionAlreadyExists(oldFunctionRange, callRanges.head, name))");
        }
        // functionDeclaredNames.put(name, callRanges.head)
        self.function_declared_names.insert(*name, call_ranges[0]);
    }
    
    pub fn declare_type(
        &mut self,
        template_name: &'t IdT<'s, 't>,
    ) {
        // vassert(!typeDeclaredNames.contains(templateName))
        assert!(!self.type_declared_names.contains(template_name));
        // typeDeclaredNames += templateName
        self.type_declared_names.insert(*template_name);
    }
    
    pub fn declare_type_mutability(
        &mut self,
        template_name: &'t IdT<'s, 't>,
        mutability: ITemplataT<'s, 't>,
    ) {
        // vassert(typeDeclaredNames.contains(templateName))
        assert!(self.type_declared_names.contains(template_name));
        // vassert(!typeNameToMutability.contains(templateName))
        assert!(!self.type_name_to_mutability.contains_key(template_name));
        // typeNameToMutability += (templateName -> mutability)
        self.type_name_to_mutability.insert(*template_name, mutability);
    }
    
    pub fn declare_type_sealed(
        &mut self,
        template_name: IdT<'s, 't>,
        sealed: bool,
    ) {
        assert!(self.type_declared_names.contains(&template_name));
        assert!(!self.interface_name_to_sealed.contains_key(&template_name));
        self.interface_name_to_sealed.insert(template_name, sealed);
    }
    
    pub fn declare_function_inner_env(
        &mut self,
        name_t: &'t IdT<'s, 't>,
        env: IInDenizenEnvironmentT<'s, 't>,
    ) {
        // vassert(functionDeclaredNames.contains(nameT))
        assert!(self.function_declared_names.contains_key(name_t));
        // vassert(!functionNameToInnerEnv.contains(nameT))
        assert!(!self.function_name_to_inner_env.contains_key(name_t));
        // functionNameToInnerEnv += (nameT -> env)
        self.function_name_to_inner_env.insert(*name_t, env);
    }
    
    pub fn declare_function_outer_env(
        &mut self,
        name_t: &'t IdT<'s, 't>,
        env: IInDenizenEnvironmentT<'s, 't>,
    ) {
        // vassert(!functionNameToOuterEnv.contains(nameT))
        assert!(!self.function_name_to_outer_env.contains_key(name_t));
        // functionNameToOuterEnv += (nameT -> env)
        self.function_name_to_outer_env.insert(*name_t, env);
    }
    
    pub fn declare_type_outer_env(
        &mut self,
        name_t: &'t IdT<'s, 't>,
        env: IInDenizenEnvironmentT<'s, 't>,
    ) {
        // vassert(typeDeclaredNames.contains(nameT))
        assert!(self.type_declared_names.contains(name_t));
        // vassert(!typeNameToOuterEnv.contains(nameT))
        assert!(!self.type_name_to_outer_env.contains_key(name_t));
        // vassert(nameT == env.id)
        // (skipped — requires pattern-matching all IInDenizenEnvironmentT variants to extract id)
        // typeNameToOuterEnv += (nameT -> env)
        self.type_name_to_outer_env.insert(*name_t, env);
    }
    
    pub fn declare_type_inner_env(
        &mut self,
        template_id: &'t IdT<'s, 't>,
        env: IInDenizenEnvironmentT<'s, 't>,
    ) {
        // vassert(typeDeclaredNames.contains(templateId))
        assert!(self.type_declared_names.contains(template_id));
        // One should declare the outer env first
        // vassert(typeNameToOuterEnv.contains(templateId))
        assert!(self.type_name_to_outer_env.contains_key(template_id));
        // vassert(!typeNameToInnerEnv.contains(templateId))
        assert!(!self.type_name_to_inner_env.contains_key(template_id));
        // typeNameToInnerEnv += (templateId -> env)
        self.type_name_to_inner_env.insert(*template_id, env);
    }
    
    pub fn add_struct(
        &mut self,
        struct_def: &'t StructDefinitionT<'s, 't>,
    ) {
        if struct_def.mutability == ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) {
            struct_def.members.iter().for_each(|m| {
                match m {
                    IStructMemberT::Normal(NormalStructMemberT { tyype: IMemberTypeT::Address(_), .. }) => {
                        panic!("Immutable structs cant contain address members");
                    }
                    IStructMemberT::Normal(NormalStructMemberT { tyype: IMemberTypeT::Reference(r), .. }) => {
                        if r.reference.ownership != OwnershipT::Share {
                            panic!("ImmutableP contains a non-immutable!");
                        }
                    }
                    IStructMemberT::Variadic(_) => {
                        panic!("implement: immutable struct with variadic members");
                    }
                }
            });
        }
        assert!(self.type_name_to_mutability.contains_key(&struct_def.template_name));
        assert!(!self.struct_template_name_to_definition.contains_key(&struct_def.template_name));
        self.struct_template_name_to_definition.insert(struct_def.template_name, struct_def);
    }
    
    pub fn add_interface(
        &mut self,
        interface_def: &'t InterfaceDefinitionT<'s, 't>,
    ) {
        assert!(self.type_name_to_mutability.contains_key(&interface_def.template_name));
        assert!(self.interface_name_to_sealed.contains_key(&interface_def.template_name));
        assert!(!self.interface_template_name_to_definition.contains_key(&interface_def.template_name));
        self.interface_template_name_to_definition.insert(interface_def.template_name, interface_def);
    }
    
    pub fn add_impl(
        &mut self,
        impl_t: &'t ImplT<'s, 't>,
    ) {
        assert!(!self.all_impls.contains_key(&impl_t.template_id));
        self.all_impls.insert(impl_t.template_id, impl_t);
        self.sub_citizen_template_to_impls
            .entry(impl_t.sub_citizen_template_id)
            .or_insert_with(Vec::new)
            .push(impl_t);
        self.super_interface_template_to_impls
            .entry(impl_t.super_interface_template_id)
            .or_insert_with(Vec::new)
            .push(impl_t);
    }
    
    pub fn get_parent_impls_for_sub_citizen_template(
        &self,
        sub_citizen_template: IdT<'s, 't>,
    ) -> Vec<&'t ImplT<'s, 't>> {
        panic!("Unimplemented: Slab 10 — body migration");
    }
    
    pub fn get_child_impls_for_super_interface_template(
        &self,
        super_interface_template: IdT<'s, 't>,
    ) -> Vec<&'t ImplT<'s, 't>> {
        self.super_interface_template_to_impls
            .get(&super_interface_template)
            .map(|v| v.clone())
            .unwrap_or_default()
    }
    
    pub fn add_kind_export(
        &mut self,
        range: RangeS<'s>,
        kind: KindT<'s, 't>,
        id: IdT<'s, 't>,
        exported_name: StrI<'s>,
        interner: &TypingInterner<'s, 't>,
    ) {
        let export = interner.alloc(KindExportT { range, tyype: kind, id, exported_name });
        self.kind_exports.push(export);
    }
    
    pub fn add_function_export(
        &mut self,
        range: RangeS<'s>,
        function: &'t PrototypeT<'s, 't>,
        export_id: IdT<'s, 't>,
        exported_name: StrI<'s>,
        interner: &TypingInterner<'s, 't>,
    ) {
        assert!(self.get_instantiation_bounds(interner, function.id).is_some());
        let export = interner.alloc(FunctionExportT { range, prototype: *function, export_id, exported_name });
        self.function_exports.push(export);
    }
    
    pub fn add_kind_extern(
        &mut self,
        kind: KindT<'s, 't>,
        package_coord: PackageCoordinate<'s>,
        exported_name: StrI<'s>,
    ) {
        panic!("Unimplemented: Slab 10 — body migration");
    }
    
    pub fn add_function_extern(
        &mut self,
        range: RangeS<'s>,
        extern_placeholdered_id: IdT<'s, 't>,
        function: &'t PrototypeT<'s, 't>,
        exported_name: StrI<'s>,
        generic_parameter_inheritance: Option<GenericParametersInheritance>,
        interner: &TypingInterner<'s, 't>,
    ) {
        let function_extern = interner.alloc(FunctionExternT { range, extern_placeholdered_id, prototype: *function, extern_name: exported_name, generic_parameter_inheritance });
        self.function_externs.push(function_extern);
    }
    
    pub fn defer_evaluating_function_body(
        &mut self,
        devf: DeferredActionT<'s, 't>,
    ) {
        let prototype = match &devf {
            DeferredActionT::EvaluateFunctionBody { prototype, .. } => *prototype,
            _ => panic!("Expected EvaluateFunctionBody"),
        };
        self.deferred_function_body_compiles.insert(*prototype, devf);
    }
    
    pub fn defer_evaluating_function(
        &mut self,
        devf: DeferredActionT<'s, 't>,
    ) {
        let name = match &devf {
            DeferredActionT::EvaluateFunction { name, .. } => *name,
            _ => panic!("Expected EvaluateFunction"),
        };
        self.deferred_function_compiles.insert(*name, devf);
    }
    
    pub fn struct_declared(
        &self,
        template_name: IdT<'s, 't>,
    ) -> bool {
        panic!("Unimplemented: Slab 10 — body migration");
    }
    
    pub fn lookup_mutability(
        &self,
        template_name: IdT<'s, 't>,
    ) -> ITemplataT<'s, 't> {
        match self.type_name_to_mutability.get(&template_name) {
            None => panic!("Still figuring out mutability for struct: {:?}", template_name),
            Some(m) => *m,
        }
    }
    
    pub fn lookup_sealed(
        &self,
        template_name: IdT<'s, 't>,
    ) -> bool {
        match self.interface_name_to_sealed.get(&template_name) {
            None => panic!("vfail: Still figuring out sealed for struct: {:?}", template_name), // See MFDBRE
            Some(m) => *m,
        }
    }
    
    pub fn interface_declared(
        &self,
        template_name: IdT<'s, 't>,
    ) -> bool {
        panic!("Unimplemented: Slab 10 — body migration");
    }
    
    pub fn lookup_struct(
        &self,
        struct_tt: IdT<'s, 't>,
        compiler: &Compiler<'s, '_, 't>,
    ) -> &'t StructDefinitionT<'s, 't> {
        let template_id = compiler.get_struct_template(struct_tt);
        self.lookup_struct_template(template_id)
    }
    
    pub fn lookup_struct_template(
        &self,
        template_name: IdT<'s, 't>,
    ) -> &'t StructDefinitionT<'s, 't> {
        *self.struct_template_name_to_definition.get(&template_name)
            .expect("Struct template not found")
    }
    
    pub fn lookup_interface(
        &self,
        interface_tt: InterfaceTT<'s, 't>,
        compiler: &Compiler<'s, '_, 't>,
    ) -> &'t InterfaceDefinitionT<'s, 't> {
        let template_id = compiler.get_interface_template(interface_tt.id);
        self.lookup_interface_by_template_name(template_id)
    }
    
    pub fn lookup_interface_by_template_name(
        &self,
        template_name: IdT<'s, 't>,
    ) -> &'t InterfaceDefinitionT<'s, 't> {
        match self.interface_template_name_to_definition.get(&template_name) {
            None => panic!("vfail: vassertSome: lookupInterface templateName not found: {:?}", template_name),
            Some(d) => *d,
        }
    }
    
    pub fn lookup_citizen_by_template_name(
        &self,
        template_name: IdT<'s, 't>,
    ) -> CitizenDefinitionT<'s, 't> {
        match template_name.local_name {
            INameT::AnonymousSubstructTemplate(_) => CitizenDefinitionT::Struct(self.lookup_struct_template(template_name)),
            INameT::StructTemplate(_) => CitizenDefinitionT::Struct(self.lookup_struct_template(template_name)),
            INameT::InterfaceTemplate(_) => CitizenDefinitionT::Interface(self.lookup_interface_by_template_name(template_name)),
            _ => panic!("lookup_citizen_by_template_name: unexpected local_name variant: {:?}", template_name),
        }
    }
    
    pub fn lookup_citizen_by_tt(
        &self,
        citizen_tt: ICitizenTT<'s, 't>,
        compiler: &Compiler<'s, '_, 't>,
    ) -> CitizenDefinitionT<'s, 't> {
        match citizen_tt {
            ICitizenTT::Struct(s) => CitizenDefinitionT::Struct(self.lookup_struct(s.id, compiler)),
            ICitizenTT::Interface(i) => CitizenDefinitionT::Interface(self.lookup_interface(*i, compiler)),
        }
    }
    
    pub fn get_all_structs(&self) -> Vec<&'t StructDefinitionT<'s, 't>> {
        self.struct_template_name_to_definition.values().copied().collect()
    }
    
    pub fn get_all_interfaces(&self) -> Vec<&'t InterfaceDefinitionT<'s, 't>> {
        self.interface_template_name_to_definition.values().copied().collect()
    }
    
    pub fn get_all_functions(&self) -> Vec<&'t FunctionDefinitionT<'s, 't>> {
        self.signature_to_function.values().copied().collect()
    }
    
    pub fn get_all_impls(&self) -> Vec<&'t ImplT<'s, 't>> {
        panic!("Unimplemented: Slab 10 — body migration");
    }
    
    pub fn get_env_for_function_signature(
        &self,
        sig: &'t SignatureT<'s, 't>,
    ) -> &'t FunctionEnvironmentT<'s, 't> {
        panic!("Unimplemented: Slab 10 — body migration");
    }
    
    pub fn get_outer_env_for_type(
        &self,
        range: &[RangeS<'s>],
        name: IdT<'s, 't>,
    ) -> IInDenizenEnvironmentT<'s, 't> {
        match self.type_name_to_outer_env.get(&name) {
            None => {
                panic!("No outer env for type: {:?}", name);
            }
            Some(x) => *x,
        }
    }
    
    pub fn get_inner_env_for_type(
        &self,
        name: IdT<'s, 't>,
    ) -> IInDenizenEnvironmentT<'s, 't> {
        *self.type_name_to_inner_env.get(&name).unwrap()
    }
    
    pub fn get_inner_env_for_function(
        &self,
        name: IdT<'s, 't>,
    ) -> IInDenizenEnvironmentT<'s, 't> {
        panic!("Unimplemented: Slab 10 — body migration");
    }
    
    pub fn get_outer_env_for_function(
        &self,
        name: IdT<'s, 't>,
    ) -> IInDenizenEnvironmentT<'s, 't> {
        *self.function_name_to_outer_env.get(&name)
            .expect("vassertSome: get_outer_env_for_function")
    }
    
    pub fn get_return_type_for_signature(
        &self,
        sig: &'t SignatureT<'s, 't>,
    ) -> Option<CoordT<'s, 't>> {
        panic!("Unimplemented: Slab 10 — body migration");
    }
    
    pub fn get_kind_exports(&self) -> Vec<&'t KindExportT<'s, 't>> {
        self.kind_exports.clone()
    }
    
    pub fn get_function_exports(&self) -> Vec<&'t FunctionExportT<'s, 't>> {
        self.function_exports.clone()
    }
    
    pub fn get_kind_externs(&self) -> Vec<&'t KindExternT<'s, 't>> {
        self.kind_externs.clone()
    }
    
    pub fn get_function_externs(&self) -> Vec<&'t FunctionExternT<'s, 't>> {
        self.function_externs.clone()
    }
    
}
