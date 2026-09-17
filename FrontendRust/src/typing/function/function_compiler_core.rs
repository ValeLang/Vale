use std::collections::HashSet;

use crate::postparsing::ast::{IBodyS, IFunctionAttributeS, LocationInDenizen};
use crate::postparsing::names::*;
use crate::typing::types::types::*;
use crate::typing::ast::ast::*;
use crate::typing::ast::expressions::{ArgLookupTE, BlockTE, ExternFunctionCallTE, GenericParametersInheritance, ReferenceExpressionTE, ReturnTE};
use crate::typing::compiler::Compiler;
use crate::typing::compiler_outputs::{CompilerOutputs, DeferredActionT};
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::typing::env::environment::*;
use crate::typing::env::function_environment_t::*;
use crate::typing::hinputs_t::InstantiationBoundArgumentsT;
use crate::typing::names::names::*;
use crate::typing::templata::templata::*;
use crate::utils::range::RangeS;
use std::marker::PhantomData;

pub struct ResultTypeMismatchError<'s, 't> {
    pub expected_type: CoordT<'s, 't>,
    pub actual_type: CoordT<'s, 't>,
}

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    // Preconditions:
    // - already spawned local env
    // - either no template args, or they were already added to the env.
    // - either no closured vars, or they were already added to the env.
    pub fn evaluate_function_for_header_core(
        &self,
        full_env: &'t FunctionEnvironmentT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        params2: &[ParameterT<'s, 't>],
        instantiation_bound_params: &'t InstantiationBoundArgumentsT<'s, 't>,
    ) -> Result<&'t FunctionHeaderT<'s, 't>, ICompileErrorT<'s, 't>> {
        // fullEnv.id match { case IdT(...drop...) => vpass(); case _ => }
        // (debug pattern match, not functionally needed)

        // val life = LocationInFunctionEnvironmentT(Vector())
        let life = LocationInFunctionEnvironmentT { path: self.typing_interner.alloc_slice_from_vec(Vec::new())};

        // val isDestructor = params2.nonEmpty && params2.head.tyype.ownership == OwnT && ...
        let is_destructor =
            !params2.is_empty() &&
            params2[0].tyype.ownership == OwnershipT::Own &&
            match full_env.id.local_name {
                INameT::Function(func_name) if func_name.template.human_name == self.keywords.drop => true,
                _ => false,
            };

        // val maybeExport = fullEnv.function.attributes.collectFirst { case e@ExportS(_) => e }
        let _maybe_export =
            full_env.function.attributes.iter().find_map(|a| {
                match a {
                    IFunctionAttributeS::Export(e) => Some(e),
                    _ => None,
                }
            });

        // val signature2 = SignatureT(fullEnv.id)
        let signature2: &'t SignatureT<'s, 't> = self.typing_interner.alloc(SignatureT { id: full_env.id });

        // val maybeRetTemplata = fullEnv.function.maybeRetCoordRune match { ... }
        let maybe_ret_templata =
            match &full_env.function.maybe_ret_coord_rune {
                None => None,
                Some(ret_coord_rune) => {
                    let imprecise_name = self.scout_arena.intern_imprecise_name(
                        IImpreciseNameValS::RuneName(RuneNameValS { rune: ret_coord_rune.rune }));
                    let mut lookup_filter = HashSet::new();
                    lookup_filter.insert(ILookupContext::TemplataLookupContext);
                    let full_env_as_i = IEnvironmentT::Function(full_env);
                    full_env_as_i.lookup_nearest_with_imprecise_name(imprecise_name, lookup_filter, self.typing_interner)
                }
            };

        // val maybeRetCoord = maybeRetTemplata match { ... }
        let maybe_ret_coord =
            match maybe_ret_templata {
                None => None,
                Some(ITemplataT::Coord(coord_templata)) => {
                    let ret_coord = coord_templata.coord;
                    coutputs.declare_function_return_type(signature2, ret_coord);
                    Some(ret_coord)
                }
                _ => panic!("Must be a coord!"),
            };

        // val header = fullEnv.function.body match { ... }
        let header =
            match &full_env.function.body {
                IBodyS::CodeBody(_body) => {
                    // val attributesWithoutExport = ...
                    let attributes_without_export: Vec<&IFunctionAttributeS<'s>> =
                        full_env.function.attributes.iter().filter(|a| {
                            !matches!(a, IFunctionAttributeS::Export(_))
                        }).collect();
                    let attributes_t = self.translate_attributes(&attributes_without_export);

                    match maybe_ret_coord {
                        Some(return_coord) => {
                            // val header = finalizeHeader(...)
                            let header =
                                self.finalize_header(full_env, coutputs, attributes_t.clone(), params2, return_coord);

                            // coutputs.deferEvaluatingFunctionBody(DeferredEvaluatingFunctionBody(...))
                            let attributes_t_arena: &'t [IFunctionAttributeT<'s>] =
                                self.typing_interner.alloc_slice_from_vec(attributes_t);
                            let call_range_arena: &'t [RangeS<'s>] =
                                self.typing_interner.alloc_slice_copy(call_range);
                            let params_t_arena: &'t [ParameterT<'s, 't>] =
                                self.typing_interner.alloc_slice_from_vec(params2.to_vec());

                            coutputs.defer_evaluating_function_body(
                                DeferredActionT::EvaluateFunctionBody {
                                    prototype: self.typing_interner.alloc(header.to_prototype()),
                                    full_env_snapshot: full_env,
                                    call_range: call_range_arena,
                                    call_location,
                                    life,
                                    attributes_t: attributes_t_arena,
                                    params_t: params_t_arena,
                                    is_destructor,
                                    maybe_explicit_return_coord: Some(return_coord),
                                    instantiation_bound_params,
                                });

                            header
                        }
                        None => {
                            let attributes_t_arena: &'t [IFunctionAttributeT<'s>] =
                                self.typing_interner.alloc_slice_from_vec(attributes_t);
                            let call_range_arena: &'t [RangeS<'s>] =
                                self.typing_interner.alloc_slice_copy(call_range);
                            let params_t_arena: &'t [ParameterT<'s, 't>] =
                                self.typing_interner.alloc_slice_from_vec(params2.to_vec());
                            let header =
                                self.finish_function_maybe_deferred(
                                    coutputs, full_env, call_range_arena, call_location, life, attributes_t_arena, params_t_arena, is_destructor, None, instantiation_bound_params)?;
                            header
                        }
                    }
                }
                IBodyS::ExternBody(_) => {
                    let ret_coord = maybe_ret_coord.unwrap();
                    let header =
                        self.make_extern_function(
                            coutputs,
                            full_env,
                            full_env.function.range,
                            self.translate_function_attributes(full_env.function.attributes),
                            params2,
                            ret_coord,
                            Some(FunctionTemplataT { outer_env: full_env.parent_env, function: full_env.function }));
                    header
                }
                IBodyS::AbstractBody(_) | IBodyS::GeneratedBody(_) => {
                    let generator_id = match &full_env.function.body {
                        IBodyS::AbstractBody(_) => self.keywords.abstract_body,
                        IBodyS::GeneratedBody(g) => g.generator_id,
                        _ => unreachable!(),
                    };

                    assert!(coutputs.lookup_function(signature2).is_none());

                    let generator = full_env.global_env.name_to_function_body_macro
                        .get(&generator_id)
                        .expect("generator not found in name_to_function_body_macro");
                    let (header, body) = generator.generate_function_body(
                        self, coutputs, full_env, generator_id, life, call_range, call_location,
                        Some(full_env.function), params2, maybe_ret_coord)?;

                    let header: &'t FunctionHeaderT<'s, 't> =
                        self.typing_interner.alloc(header);

                    coutputs.declare_function_return_type(
                        self.typing_interner.alloc(header.to_signature()), header.return_type);

                    let header_sig = self.typing_interner.alloc(header.to_signature());
                    coutputs.add_function(
                        header_sig,
                        self.typing_interner.alloc(FunctionDefinitionT {
                            header,
                            instantiation_bound_params,
                            body,
                        }));

                    if header.to_signature() != *signature2 {
                        panic!("Generator made a function whose signature doesn't match the expected one!");
                    }
                    header
                }
            };

        // if (header.attributes.exists({ case PureT => true case _ => false })) { ... }
        if header.attributes.iter().any(|a| matches!(a, IFunctionAttributeT::Pure)) {
            // (Scala has commented-out purity checks here)
        }

        Ok(header)
    }

    pub fn get_function_prototype_for_call(
        &self,
        full_env: &'t FunctionEnvironmentT<'s, 't>,
        _coutputs: &CompilerOutputs<'s, 't>,
        _call_range: &[RangeS<'s>],
        _params2: &[ParameterT<'s, 't>],
    ) -> PrototypeT<'s, 't> {
        self.get_function_prototype_inner_for_call(full_env, full_env.id)
    }

    pub fn get_function_prototype_inner_for_call(
        &self,
        full_env: &'t FunctionEnvironmentT<'s, 't>,
        id: IdT<'s, 't>,
    ) -> PrototypeT<'s, 't> {
        let ret_coord_rune = full_env.function.maybe_ret_coord_rune.unwrap();
        let imprecise_name = self.scout_arena.intern_imprecise_name(
            IImpreciseNameValS::RuneName(RuneNameValS { rune: ret_coord_rune.rune }));
        let mut lookup_filter = HashSet::new();
        lookup_filter.insert(ILookupContext::TemplataLookupContext);
        let full_env_as_i = IInDenizenEnvironmentT::Function(full_env);
        let return_coord = match full_env_as_i.lookup_nearest_with_imprecise_name(imprecise_name, lookup_filter, self.typing_interner) {
            Some(ITemplataT::Coord(coord_templata)) => coord_templata.coord,
            other => panic!("vwat: unexpected in getFunctionPrototypeInnerForCall: {:?}", other),
        };
        PrototypeT { id, return_type: return_coord }
    }

    pub fn finalize_header(
        &self,
        full_env: &'t FunctionEnvironmentT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        attributes_t: Vec<IFunctionAttributeT<'s>>,
        params_t: &[ParameterT<'s, 't>],
        return_coord: CoordT<'s, 't>,
    ) -> &'t FunctionHeaderT<'s, 't> {
        let header = self.typing_interner.alloc(FunctionHeaderT {
            id: full_env.id,
            attributes: self.typing_interner.alloc_slice_from_vec(attributes_t),
            params: self.typing_interner.alloc_slice_from_vec(params_t.to_vec()),
            return_type: return_coord,
            maybe_origin_function_templata: Some(FunctionTemplataT {
                outer_env: full_env.parent_env,
                function: full_env.function,
            }),
        });
        let sig_ref = self.typing_interner.alloc(header.to_signature());
        coutputs.declare_function_return_type(sig_ref, return_coord);
        header
    }

    pub fn finish_function_maybe_deferred(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        full_env_snapshot: &'t FunctionEnvironmentT<'s, 't>,
        call_range: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        life: LocationInFunctionEnvironmentT<'t>,
        attributes_t: &'t [IFunctionAttributeT<'s>],
        params_t: &'t [ParameterT<'s, 't>],
        is_destructor: bool,
        maybe_explicit_return_coord: Option<CoordT<'s, 't>>,
        instantiation_bound_params: &'t InstantiationBoundArgumentsT<'s, 't>,
    ) -> Result<&'t FunctionHeaderT<'s, 't>, ICompileErrorT<'s, 't>> {
        // val (maybeEvaluatedRetCoord, body2) =
        //   bodyCompiler.declareAndEvaluateFunctionBody(
        //     fullEnvSnapshot, coutputs, life, callRange, callLocation,
        //     fullEnvSnapshot.function, maybeExplicitReturnCoord, paramsT, isDestructor)
        let (maybe_evaluated_ret_coord, body2) =
            self.declare_and_evaluate_function_body(
                full_env_snapshot, coutputs, life, call_range, call_location,
                full_env_snapshot.function, maybe_explicit_return_coord, params_t, is_destructor)?;

        let ret_coord = match (maybe_explicit_return_coord, maybe_evaluated_ret_coord) {
            (Some(c), None) => c,
            (None, Some(c)) => c,
            _ => panic!("Expected exactly one return coord"),
        };
        let header = self.finalize_header(
            full_env_snapshot, coutputs, attributes_t.to_vec(), params_t, ret_coord);

        let _needed_function_bounds = self.assemble_rune_to_function_bound(full_env_snapshot.templatas);
        let _needed_impl_bounds = self.assemble_rune_to_impl_bound(full_env_snapshot.templatas);

        let header_sig = self.typing_interner.alloc(header.to_signature());
        assert!(coutputs.lookup_function(header_sig).is_none());
        let function2 = self.typing_interner.alloc(FunctionDefinitionT {
            header,
            instantiation_bound_params,
            body: ReferenceExpressionTE::Block(self.typing_interner.alloc(BlockTE { inner: body2.inner })),
        });
        coutputs.add_function(header_sig, function2);
        Ok(function2.header)
    }

    pub fn translate_attributes(&self, attributes_a: &[&IFunctionAttributeS<'s>]) -> Vec<IFunctionAttributeT<'s>> {
        attributes_a.iter().map(|a| {
            match a {
                IFunctionAttributeS::UserFunction(_) => IFunctionAttributeT::UserFunction,
                IFunctionAttributeS::Pure(_) => IFunctionAttributeT::Pure,
                IFunctionAttributeS::Additive(_) => IFunctionAttributeT::Additive,
                _ => panic!("implement: translate other function attributes"),
            }
        }).collect()
    }

    pub fn make_extern_function(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        env: &'t FunctionEnvironmentT<'s, 't>,
        range: RangeS<'s>,
        attributes: Vec<IFunctionAttributeT<'s>>,
        params2: &[ParameterT<'s, 't>],
        return_type: CoordT<'s, 't>,
        maybe_origin: Option<FunctionTemplataT<'s, 't>>,
    ) -> &'t FunctionHeaderT<'s, 't> {
        match env.id.local_name {
            INameT::Function(FunctionNameT { template: FunctionTemplateNameT { human_name, .. }, template_args: template_params, parameters, .. }) => {
                let header = self.typing_interner.alloc(FunctionHeaderT {
                    id: env.id,
                    attributes: self.typing_interner.alloc_slice_from_vec(attributes),
                    params: self.typing_interner.alloc_slice_from_vec(params2.to_vec()),
                    return_type,
                    maybe_origin_function_templata: maybe_origin,
                });

                let extern_function_name = self.typing_interner.intern_name(
                    INameValT::ExternFunction(ExternFunctionNameValT { human_name: *human_name, template_args: template_params, parameters }));
                let extern_function_id = self.typing_interner.intern_id(IdValT {
                    package_coord: env.id.package_coord,
                    init_steps: env.id.init_steps,
                    local_name: extern_function_name,
                });
                let extern_prototype = self.typing_interner.alloc(PrototypeT {
                    id: *extern_function_id,
                    return_type: header.return_type,
                });

                coutputs.add_instantiation_bounds(
                    self.opts.global_options.sanity_check,
                    self.typing_interner,
                    env.template_id,
                    extern_prototype.id,
                    self.typing_interner.alloc(InstantiationBoundArgumentsT {
                        rune_to_bound_prototype: self.typing_interner.alloc_index_map(),
                        rune_to_citizen_rune_to_reachable_prototype: self.typing_interner.alloc_index_map(),
                        rune_to_bound_impl: self.typing_interner.alloc_index_map(),
                    }),
                );

                let arg_lookups: Vec<ReferenceExpressionTE<'s, 't>> =
                    header.params.iter().enumerate().map(|(index, param)| {
                        ReferenceExpressionTE::ArgLookup(self.typing_interner.alloc(ArgLookupTE {
                            param_index: index as i32,
                            coord: param.tyype,
                        }))
                    }).collect();

                let function2 = self.typing_interner.alloc(FunctionDefinitionT {
                    header,
                    instantiation_bound_params: self.typing_interner.alloc(InstantiationBoundArgumentsT {
                        rune_to_bound_prototype: self.typing_interner.alloc_index_map(),
                        rune_to_citizen_rune_to_reachable_prototype: self.typing_interner.alloc_index_map(),
                        rune_to_bound_impl: self.typing_interner.alloc_index_map(),
                    }),
                    body: ReferenceExpressionTE::Return(self.typing_interner.alloc(ReturnTE {
                        source_expr: ReferenceExpressionTE::ExternFunctionCall(self.typing_interner.alloc(ExternFunctionCallTE {
                            prototype2: extern_prototype,
                            args: self.typing_interner.alloc_slice_from_vec(arg_lookups),
                        })),
                    })),
                });

                let header_sig = self.typing_interner.alloc(function2.header.to_signature());
                coutputs.declare_function_return_type(header_sig, function2.header.return_type);
                coutputs.add_function(header_sig, function2);
                // Register the extern with coutputs so it survives to HinputsT.functionExterns and reaches
                // the Backend's pragma generation. The placeholderedExternId mirrors what Compiler.scala
                // used to construct: a top-level IdT with an ExternNameT carrying a fresh ExternTemplateNameT
                // keyed on the function's range. Fires for both top-level externs and externs declared
                // inside an extern struct (the latter wouldn't otherwise reach Compiler.scala's loop).
                let extern_template_name = self.typing_interner.intern_extern_template_name(ExternTemplateNameT {
                    code_loc: range.begin,
                });
                let placeholdered_extern_name = self.typing_interner.intern_extern_name(ExternNameT {
                    template: extern_template_name,
                    template_arg: RegionT { region: IRegionT::Default },
                });
                let placeholdered_extern_id = *self.typing_interner.intern_id(IdValT {
                    package_coord: env.id.package_coord,
                    init_steps: &[],
                    local_name: INameT::Extern(placeholdered_extern_name),
                });
                // Per @PRIIROZ, internal-method externs inherit the container's generic params at the end
                // of their templateArgs. Hammer uses this count to reshape the wire-format SimpleId so the
                // inherited args land on the citizen step instead of the function step (i.e.
                // `Vec<i32>::capacity` rather than `Vec::capacity<i32>`), which is what Backend's
                // rustifySimpleId expects per @SMLRZ.
                let maybe_inheritance = match ICitizenTemplateNameT::try_from(extern_prototype.id.init_id(self.typing_interner).local_name) {
                    Ok(_ctn) => {
                        let citizen = coutputs.lookup_citizen_by_template_name(extern_prototype.id.init_id(self.typing_interner));
                        Some(GenericParametersInheritance { num_inherited_generic_parameters: citizen.generic_param_types(self.scout_arena).len() as i32 })
                    }
                    Err(_) => None,
                };
                coutputs.add_function_extern(
                    range, placeholdered_extern_id, extern_prototype, *human_name, maybe_inheritance, self.typing_interner);
                function2.header
            }
            _ => {
                panic!("Only human-named function can be extern!");
            }
        }
    }

    pub fn translate_function_attributes(&self, a: &[IFunctionAttributeS<'s>]) -> Vec<IFunctionAttributeT<'s>> {
        a.iter().map(|attr| {
            match attr {
                IFunctionAttributeS::UserFunction(_) => IFunctionAttributeT::UserFunction,
                IFunctionAttributeS::Extern(extern_s) => IFunctionAttributeT::Extern(ExternT { package_coord: *extern_s.package_coord }),
                _ => panic!("implement: translateFunctionAttributes {:?}", attr),
            }
        }).collect()
    }

}
