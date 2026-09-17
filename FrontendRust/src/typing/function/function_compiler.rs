use crate::higher_typing::ast::FunctionA;
use crate::postparsing::ast::{LocationInDenizen, IBodyS};
use crate::postparsing::names::{IFunctionDeclarationNameS, IVarNameS};
use crate::typing::ast::ast::FunctionHeaderT;
use crate::typing::ast::citizens::NormalStructMemberT;
use crate::typing::compiler::Compiler;
use crate::typing::compiler_outputs::CompilerOutputs;
use crate::typing::env::environment::{IInDenizenEnvironmentT, IEnvironmentT};
use crate::typing::env::function_environment_t::NodeEnvironmentT;
use crate::typing::templata::templata::*;
use crate::typing::types::types::*;
use crate::typing::names::names::*;
use crate::typing::env::environment::ILookupContext;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use std::collections::HashSet;
use crate::utils::range::RangeS;
use crate::typing::ast::citizens::{IMemberTypeT, ReferenceMemberTypeT, AddressMemberTypeT};
use crate::typing::env::function_environment_t::{IVariableT, ReferenceLocalVariableT, AddressibleLocalVariableT, ReferenceClosureVariableT, AddressibleClosureVariableT};
use crate::typing::templata::templata::PrototypeTemplataT;
use crate::postparsing::names::IRuneS;
use crate::typing::hinputs_t::InstantiationBoundArgumentsT;
use crate::typing::infer_compiler::IDefiningError;
use crate::typing::infer_compiler::InitialKnown;
use crate::typing::infer_compiler::IResolvingError;
use crate::typing::ast::ast::PrototypeT;
use crate::typing::overload_resolver::IFindFunctionFailureReason;
use indexmap::IndexMap;
use std::collections::HashMap;
use std::marker::PhantomData;

// deleted: delegate trait removed per god-struct refactor (Compiler now holds all methods directly)

pub enum IEvaluateFunctionResult<'s, 't> {
    EvaluateFunctionSuccess(EvaluateFunctionSuccess<'s, 't>),
    EvaluateFunctionFailure(EvaluateFunctionFailure<'s, 't>),
}

pub struct EvaluateFunctionSuccess<'s, 't> {
    pub prototype: &'t PrototypeTemplataT<'s, 't>,
    pub inferences: IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>,
    pub instantiation_bound_args: &'t InstantiationBoundArgumentsT<'s, 't>,
}

pub struct EvaluateFunctionFailure<'s, 't> {
    pub reason: IDefiningError<'s, 't>,
}

pub enum IDefineFunctionResult<'s, 't> {
    DefineFunctionSuccess(DefineFunctionSuccess<'s, 't>),
    DefineFunctionFailure(DefineFunctionFailure<'s, 't>),
}

pub struct DefineFunctionSuccess<'s, 't> {
    pub prototype: &'t PrototypeTemplataT<'s, 't>,
    pub inferences: IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>,
    pub instantiation_bound_params: &'t InstantiationBoundArgumentsT<'s, 't>,
}

pub struct DefineFunctionFailure<'s, 't> {
    pub reason: IDefiningError<'s, 't>,
}

pub enum IResolveFunctionResult<'s, 't> {
    ResolveFunctionSuccess(ResolveFunctionSuccess<'s, 't>),
    ResolveFunctionFailure(ResolveFunctionFailure<'s, 't>),
}

pub struct ResolveFunctionSuccess<'s, 't> {
    pub prototype: &'t PrototypeTemplataT<'s, 't>,
    pub inferences: IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>,
}

pub struct ResolveFunctionFailure<'s, 't> {
    pub reason: IResolvingError<'s, 't>,
}

pub enum IStampFunctionResult<'s, 't> {
    StampFunctionSuccess(StampFunctionSuccess<'s, 't>),
    StampFunctionFailure(StampFunctionFailure<'s, 't>),
}

pub struct StampFunctionSuccess<'s, 't> {
    pub prototype: &'t PrototypeT<'s, 't>,
    pub inferences: IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>,
}

pub struct StampFunctionFailure<'s, 't> {
    pub reason: IFindFunctionFailureReason<'s, 't>,
}

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn evaluate_generic_function_from_non_call(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        function_templata: FunctionTemplataT<'s, 't>,
    ) -> Result<&'t FunctionHeaderT<'s, 't>, ICompileErrorT<'s, 't>> {
        let env = function_templata.outer_env;
        let function = function_templata.function;
        if function.is_light() {
            let mut new_ranges: Vec<RangeS<'s>> = Vec::with_capacity(1 + parent_ranges.len());
            new_ranges.push(function.range);
            new_ranges.extend_from_slice(parent_ranges);
            self.evaluate_generic_light_function_from_non_call(
                env, coutputs, &new_ranges, call_location, function)
        } else {
            panic!("vfail: I think we need a call to evaluate a lambda?")
        }
    }

    pub fn evaluate_templated_light_function_from_call_for_prototype(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        function_templata: FunctionTemplataT<'s, 't>,
        already_specified_template_args: &[ITemplataT<'s, 't>],
        context_region: RegionT,
        arg_types: &[CoordT<'s, 't>],
    ) -> IEvaluateFunctionResult<'s, 't> {
        panic!("Unimplemented: Slab 15 — body migration");
    }

    pub fn evaluate_templated_function_from_call_for_prototype(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        function_templata: FunctionTemplataT<'s, 't>,
        already_specified_template_args: &[ITemplataT<'s, 't>],
        context_region: RegionT,
        arg_types: &[CoordT<'s, 't>],
    ) -> Result<IEvaluateFunctionResult<'s, 't>, ICompileErrorT<'s, 't>> {
        let FunctionTemplataT { outer_env: declaring_env, function } = function_templata;
        if function.is_light() {
            self.evaluate_templated_light_banner_from_call_closure_or_light(
                declaring_env, coutputs, calling_env, call_range, call_location,
                function, already_specified_template_args, context_region, arg_types)
        } else {
            let lambda_citizen_name_2 =
                match function.name {
                    IFunctionDeclarationNameS::LambdaDeclarationName(lambda_name) => {
                        INameT::LambdaCitizen(self.typing_interner.alloc(LambdaCitizenNameT {
                            template: self.typing_interner.alloc(LambdaCitizenTemplateNameT {
                                code_location: lambda_name.code_location,
                            }),
                        }))
                    }
                    _ => { panic!("vwat"); }
                };

            let lookup_result = declaring_env.lookup_nearest_with_name(
                lambda_citizen_name_2,
                HashSet::from([ILookupContext::TemplataLookupContext]),
                self.typing_interner);
            let closure_struct_ref: StructTT<'s, 't> = match lookup_result {
                Some(ITemplataT::Kind(KindTemplataT { kind: KindT::Struct(s) })) => **s,
                _ => { panic!("Unimplemented: evaluateTemplatedFunctionFromCallForPrototype lookup failed"); }
            };

            let banner = self.evaluate_templated_closure_function_from_call_for_banner(
                declaring_env, coutputs, calling_env, call_range, call_location,
                closure_struct_ref, function, already_specified_template_args,
                context_region, arg_types);
            banner
        }
    }

    pub fn evaluate_templated_function_from_call_for_prototype_ext(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        function_templata: FunctionTemplataT<'s, 't>,
        explicit_template_args: &[ITemplataT<'s, 't>],
        context_region: RegionT,
        arg_types: &[CoordT<'s, 't>],
    ) -> IEvaluateFunctionResult<'s, 't> {
        panic!("Unimplemented: Slab 15 — body migration");
    }

    pub fn evaluate_generic_virtual_dispatcher_function_for_prototype(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        function_templata: FunctionTemplataT<'s, 't>,
        args: &[Option<CoordT<'s, 't>>],
    ) -> Result<IDefineFunctionResult<'s, 't>, ICompileErrorT<'s, 't>> {
        let FunctionTemplataT { outer_env, function } = function_templata;
        self.evaluate_generic_virtual_dispatcher_function_for_prototype_closure_or_light(
            outer_env, coutputs, calling_env, call_range, call_location, function, args)
    }

    pub fn evaluate_generic_light_function_from_call_for_prototype(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        function_templata: FunctionTemplataT<'s, 't>,
        explicit_template_args: &[ITemplataT<'s, 't>],
        context_region: RegionT,
        args: &[CoordT<'s, 't>],
        container_rune_initial_knowns: &[InitialKnown<'s, 't>],
    ) -> Result<IResolveFunctionResult<'s, 't>, ICompileErrorT<'s, 't>> {
        let FunctionTemplataT { outer_env: env, function } = function_templata;
        self.evaluate_generic_light_function_from_call_for_prototype2(
            env, coutputs, calling_env, call_range, call_location, function, explicit_template_args,
            context_region, &args.iter().map(|a| Some(*a)).collect::<Vec<_>>(), container_rune_initial_knowns)
    }

    pub fn evaluate_closure_struct(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        containing_node_env: &'t NodeEnvironmentT<'s, 't>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        name: IFunctionDeclarationNameS<'s>,
        function_a: &'s FunctionA<'s>,
        verify_conclusions: bool,
    ) -> Result<StructTT<'s, 't>, ICompileErrorT<'s, 't>> {
        let code_body = match &function_a.body {
            IBodyS::CodeBody(code_body) => code_body,
            _ => panic!("evaluate_closure_struct: expected CodeBodyS"),
        };
        let closured_names = code_body.body.closured_names;

        // Note, this is where the unordered closuredNames set becomes ordered.
        let closured_var_names_and_types: Vec<&'t NormalStructMemberT<'s, 't>> =
            closured_names.iter().map(|name| {
                self.determine_closure_variable_member(containing_node_env, coutputs, *name)
            }).collect();

        let (struct_tt, _, _function_templata) =
            self.make_closure_understruct(
                containing_node_env, coutputs, call_range, call_location, name, function_a,
                &closured_var_names_and_types)?;

        Ok(struct_tt)
    }

    pub fn determine_closure_variable_member(
        &self,
        env: &'t NodeEnvironmentT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        name: IVarNameS<'s>,
    ) -> &'t NormalStructMemberT<'s, 't> {
        let translated_name = self.translate_var_name_step(name);
        let (variability, tyype) = match env.get_variable(translated_name).unwrap() {
            IVariableT::ReferenceLocal(ReferenceLocalVariableT { variability, coord, .. }) => {
                // See "Captured own is borrow" test for why we do this
                let tyype = match coord.ownership {
                    OwnershipT::Own => IMemberTypeT::Reference(ReferenceMemberTypeT { reference: CoordT { ownership: OwnershipT::Borrow, region: coord.region, kind: coord.kind } }),
                    OwnershipT::Borrow | OwnershipT::Share => IMemberTypeT::Reference(ReferenceMemberTypeT { reference: coord }),
                    OwnershipT::Weak => panic!("implement: determine_closure_variable_member — ReferenceLocalVariableT WeakT"),
                };
                (variability, tyype)
            }
            IVariableT::AddressibleLocal(AddressibleLocalVariableT { variability, coord: reference, .. }) => {
                (variability, IMemberTypeT::Address(AddressMemberTypeT { reference }))
            }
            IVariableT::ReferenceClosure(ReferenceClosureVariableT { variability, coord, .. }) => {
                // See "Captured own is borrow" test for why we do this
                let tyype = match coord.ownership {
                    OwnershipT::Own => IMemberTypeT::Reference(ReferenceMemberTypeT { reference: CoordT { ownership: OwnershipT::Borrow, region: coord.region, kind: coord.kind } }),
                    OwnershipT::Borrow | OwnershipT::Share => IMemberTypeT::Reference(ReferenceMemberTypeT { reference: coord }),
                    OwnershipT::Weak => panic!("implement: determine_closure_variable_member — ReferenceClosureVariableT WeakT"),
                };
                (variability, tyype)
            }
            IVariableT::AddressibleClosure(AddressibleClosureVariableT { variability, coord: reference, .. }) => {
                (variability, IMemberTypeT::Address(AddressMemberTypeT { reference }))
            }
        };
        self.typing_interner.alloc(NormalStructMemberT { name: translated_name, variability, tyype })
    }

}
