use crate::typing::compiler::Compiler;
use crate::typing::compiler_outputs::CompilerOutputs;
use crate::typing::env::function_environment_t::FunctionEnvironmentT;
use crate::interner::StrI;
use crate::typing::ast::ast::LocationInFunctionEnvironmentT;
use crate::utils::range::RangeS;
use crate::postparsing::ast::LocationInDenizen;
use crate::higher_typing::ast::FunctionA;
use crate::typing::ast::ast::ParameterT;
use crate::typing::types::types::CoordT;
use crate::typing::ast::ast::FunctionHeaderT;
use crate::typing::ast::expressions::ReferenceExpressionTE;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::typing::names::names::IdT;
use crate::higher_typing::ast::StructA;
use crate::typing::env::i_env_entry::IEnvEntryT;
use crate::higher_typing::ast::InterfaceA;

// Dispatch-tag enum replacing Scala's IFunctionBodyMacro trait; bodies live as
// Compiler::generate_function_body_<suffix> methods.
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum FunctionBodyMacro {
    LockWeak,
    AsSubtype,
    StructDrop,
    StructConstructor,
    AbstractBody,
    SameInstance,
    RsaLen,
    RsaMutableNew,
    RsaImmutableNew,
    RsaDropInto,
    RsaMutableCapacity,
    RsaMutablePop,
    RsaMutablePush,
    SsaLen,
    SsaDropInto,
}

impl FunctionBodyMacro {
    pub fn generate_function_body<'s, 'ctx, 't>(
        &self,
        compiler: &Compiler<'s, 'ctx, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        env: &'t FunctionEnvironmentT<'s, 't>,
        generator_id: StrI<'s>,
        life: LocationInFunctionEnvironmentT<'t>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        origin_function: Option<&'s FunctionA<'s>>,
        param_coords: &[ParameterT<'s, 't>],
        maybe_ret_coord: Option<CoordT<'s, 't>>,
    ) -> Result<(FunctionHeaderT<'s, 't>, ReferenceExpressionTE<'s, 't>), ICompileErrorT<'s, 't>>
    where 's: 't,
    {
        match self {
            FunctionBodyMacro::LockWeak => compiler.generate_function_body_lock_weak(coutputs, env, generator_id, life, call_range, call_location, origin_function, param_coords, maybe_ret_coord),
            FunctionBodyMacro::AsSubtype => compiler.generate_function_body_as_subtype(coutputs, env, generator_id, life, call_range, call_location, origin_function, param_coords, maybe_ret_coord),
            FunctionBodyMacro::StructDrop => Ok(compiler.generate_function_body_struct_drop(coutputs, env, generator_id, life, call_range, call_location, origin_function, param_coords, maybe_ret_coord)),
            FunctionBodyMacro::StructConstructor => Ok(compiler.generate_function_body_struct_constructor(coutputs, env, generator_id, life, call_range, call_location, origin_function, param_coords, maybe_ret_coord)),
            FunctionBodyMacro::AbstractBody => compiler.generate_function_body_abstract_body(coutputs, env, generator_id, life, call_range, call_location, origin_function, param_coords, maybe_ret_coord),
            FunctionBodyMacro::SameInstance => Ok(compiler.generate_function_body_same_instance(coutputs, env, generator_id, life, call_range, call_location, origin_function, param_coords, maybe_ret_coord)),
            FunctionBodyMacro::RsaLen => Ok(compiler.generate_function_body_rsa_len(coutputs, env, generator_id, life, call_range, call_location, origin_function, param_coords, maybe_ret_coord)),
            FunctionBodyMacro::RsaMutableNew => Ok(compiler.generate_function_body_rsa_mutable_new(coutputs, env, generator_id, life, call_range, call_location, origin_function, param_coords, maybe_ret_coord)),
            FunctionBodyMacro::RsaImmutableNew => compiler.generate_function_body_rsa_immutable_new(coutputs, env, generator_id, life, call_range, call_location, origin_function, param_coords, maybe_ret_coord),
            FunctionBodyMacro::RsaDropInto => compiler.generate_function_body_rsa_drop_into(coutputs, env, generator_id, life, call_range, call_location, origin_function, param_coords, maybe_ret_coord),
            FunctionBodyMacro::RsaMutableCapacity => Ok(compiler.generate_function_body_rsa_mutable_capacity(coutputs, env, generator_id, life, call_range, call_location, origin_function, param_coords, maybe_ret_coord)),
            FunctionBodyMacro::RsaMutablePop => Ok(compiler.generate_function_body_rsa_mutable_pop(coutputs, env, generator_id, life, call_range, call_location, origin_function, param_coords, maybe_ret_coord)),
            FunctionBodyMacro::RsaMutablePush => Ok(compiler.generate_function_body_rsa_mutable_push(coutputs, env, generator_id, life, call_range, call_location, origin_function, param_coords, maybe_ret_coord)),
            FunctionBodyMacro::SsaLen => Ok(compiler.generate_function_body_ssa_len(coutputs, env, generator_id, life, call_range, call_location, origin_function, param_coords, maybe_ret_coord)),
            FunctionBodyMacro::SsaDropInto => compiler.generate_function_body_ssa_drop_into(coutputs, env, generator_id, life, call_range, call_location, origin_function, param_coords, maybe_ret_coord),
        }
    }
    
}

// Dispatch-tag enum replacing Scala's IOnStructDefinedMacro trait; bodies live on impl Compiler.
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum OnStructDefinedMacro {
    StructConstructor,
    StructDrop,
}

impl OnStructDefinedMacro {
    pub fn get_struct_sibling_entries<'s, 'ctx, 't>(
        &self,
        compiler: &Compiler<'s, 'ctx, 't>,
        struct_name: IdT<'s, 't>,
        struct_a: &'s StructA<'s>,
    ) -> Vec<(IdT<'s, 't>, IEnvEntryT<'s, 't>)>
    where 's: 't,
    {
        match self {
            OnStructDefinedMacro::StructConstructor => compiler.get_struct_sibling_entries_struct_constructor(struct_name, struct_a),
            OnStructDefinedMacro::StructDrop => compiler.get_struct_sibling_entries_struct_drop(struct_name, struct_a),
        }
    }
    
}

// Dispatch-tag enum replacing Scala's IOnInterfaceDefinedMacro trait; bodies live on impl Compiler.
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum OnInterfaceDefinedMacro {
    AnonymousInterface,
    InterfaceDrop,
}

impl OnInterfaceDefinedMacro {
    pub fn get_interface_sibling_entries<'s, 'ctx, 't>(
        &self,
        compiler: &Compiler<'s, 'ctx, 't>,
        interface_name: IdT<'s, 't>,
        interface_a: &'s InterfaceA<'s>,
    ) -> Vec<(IdT<'s, 't>, IEnvEntryT<'s, 't>)>
    where 's: 't,
    {
        match self {
            OnInterfaceDefinedMacro::AnonymousInterface => compiler.get_interface_sibling_entries_anonymous_interface(interface_name, interface_a),
            OnInterfaceDefinedMacro::InterfaceDrop => compiler.get_interface_sibling_entries_interface_drop(interface_name, interface_a),
        }
    }
    
}

// Dispatch-tag enum replacing Scala's IOnImplDefinedMacro trait; bodies live on impl Compiler.
// (No concrete implementors in the current codebase — Scala initializes this map empty.)
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum OnImplDefinedMacro {}

