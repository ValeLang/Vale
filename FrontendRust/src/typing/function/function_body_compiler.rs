use crate::higher_typing::ast::FunctionA;
use crate::postparsing::ast::{IBodyS, LocationInDenizen, ParameterS};
use crate::postparsing::expressions::{BodySE, IExpressionSE};
use crate::postparsing::patterns::patterns::AtomSP;
use crate::typing::ast::ast::{LocationInFunctionEnvironmentT, ParameterT};
use crate::typing::ast::expressions::{ArgLookupTE, BlockTE, ReferenceExpressionTE, ReturnTE};
use crate::typing::compiler::Compiler;
use crate::typing::compiler_outputs::CompilerOutputs;
use crate::typing::env::function_environment_t::{FunctionEnvironmentT, NodeEnvironmentBox};
use crate::typing::env::environment::IInDenizenEnvironmentT;
use crate::typing::types::types::{CoordT, KindT, NeverT, OwnershipT, RegionT};
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::utils::range::RangeS;
use std::collections::HashSet;
use std::iter::once;

// deleted: delegate trait removed per god-struct refactor (Compiler now holds all methods directly)

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn declare_and_evaluate_function_body(
        &self,
        func_outer_env: &'t FunctionEnvironmentT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        function_1: &'s FunctionA<'s>,
        maybe_explicit_return_coord: Option<CoordT<'s, 't>>,
        params_2: &'t [ParameterT<'s, 't>],
        is_destructor: bool,
    ) -> Result<(Option<CoordT<'s, 't>>, &'t BlockTE<'s, 't>), ICompileErrorT<'s, 't>> {
        // val bodyS = function1.body match { case CodeBodyS(b) => b; case _ => vwat() }
        let body_s = match &function_1.body {
            IBodyS::CodeBody(b) => b,
            _ => panic!("Expected CodeBodyS"),
        };

        // maybeExplicitReturnCoord match { ... }
        match maybe_explicit_return_coord {
            None => {
                let (body2, returns) = match self.evaluate_function_body(
                    func_outer_env, coutputs, life, parent_ranges,
                    func_outer_env.default_region, call_location,
                    &function_1.params.iter().collect::<Vec<_>>(), params_2, body_s.body,
                    is_destructor, None)?
                {
                    Err(ResultTypeMismatchError { expected_type, actual_type }) => {
                        let range_list: &'t [RangeS<'s>] = self.typing_interner.alloc_slice_copy(
                            &once(function_1.range).chain(parent_ranges.iter().copied()).collect::<Vec<_>>());
                        return Err(ICompileErrorT::BodyResultDoesntMatch {
                            range: range_list,
                            function_name: function_1.name,
                            expected_return_type: expected_type,
                            result_type: actual_type,
                        });
                    }
                    Ok((body, returns)) => (body, returns),
                };

                assert!(body2.result().coord.kind != KindT::Never(NeverT { from_break: true }));
                let return_type2 =
                    if returns.is_empty() && body2.result().coord.kind == KindT::Never(NeverT { from_break: false }) {
                        // No returns yet the body results in a Never. This can happen if we call panic from inside.
                        body2.result().coord
                    } else {
                        assert!(!returns.is_empty());
                        if returns.len() > 1 {
                            panic!("Can't infer return type because {} types are returned", returns.len());
                        }
                        *returns.iter().next().unwrap()
                    };

                Ok((Some(return_type2), body2))
            }
            Some(explicit_ret_coord) => {
                // val (body2, returns) = evaluateFunctionBody(...)
                let (body2, returns) = match self.evaluate_function_body(
                    func_outer_env, coutputs, life, parent_ranges,
                    func_outer_env.default_region, call_location,
                    &function_1.params.iter().collect::<Vec<_>>(), params_2, body_s.body,
                    is_destructor, Some(explicit_ret_coord))?
                {
                    Err(ResultTypeMismatchError { expected_type, actual_type }) => {
                        let range_list: &'t [RangeS<'s>] = self.typing_interner.alloc_slice_copy(
                            &once(function_1.range).chain(parent_ranges.iter().copied()).collect::<Vec<_>>());
                        return Err(ICompileErrorT::BodyResultDoesntMatch {
                            range: range_list,
                            function_name: function_1.name,
                            expected_return_type: expected_type,
                            result_type: actual_type,
                        });
                    }
                    Ok((body, returns)) => (body, returns),
                };

                // vcurious(returns.size <= 1)
                assert!(returns.len() <= 1);
                // (returns.headOption, body2.result.kind) match { ... }
                match (returns.iter().next(), body2.result().coord.kind) {
                    (Some(x), _) if *x == explicit_ret_coord => {
                        // Let it through, it returns the expected type.
                    }
                    (Some(coord), _) if coord.ownership == OwnershipT::Share && coord.kind == KindT::Never(NeverT { from_break: false }) => {
                        // Let it through, it returns a never but we expect something else, that's fine
                    }
                    (None, KindT::Never(NeverT { from_break: false })) => {
                        // Let it through, it doesn't return anything yet it results in a never, which means
                        // we called panic or something from inside.
                    }
                    _ => {
                        panic!("implement: CouldntConvertForReturnT error");
                    }
                }

                Ok((None, body2))
            }
        }
    }

}

pub struct ResultTypeMismatchError<'s, 't> {
    pub expected_type: CoordT<'s, 't>,
    pub actual_type: CoordT<'s, 't>,
}

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn evaluate_function_body(
        &self,
        func_outer_env: &'t FunctionEnvironmentT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        region: RegionT,
        call_location: LocationInDenizen<'s>,
        params_1: &[&'s ParameterS<'s>],
        params_2: &'t [ParameterT<'s, 't>],
        body_1: &'s BodySE<'s>,
        is_destructor: bool,
        maybe_expected_result_type: Option<CoordT<'s, 't>>,
    ) -> Result<
        Result<(&'t BlockTE<'s, 't>, HashSet<CoordT<'s, 't>>), ResultTypeMismatchError<'s, 't>>,
        ICompileErrorT<'s, 't>,
    > {
        // val env = NodeEnvironmentBox(funcOuterEnv.makeChildNodeEnvironment(body1.block, life))
        let block_as_expr: &'s IExpressionSE<'s> =
            self.scout_arena.alloc(IExpressionSE::Block(body_1.block));
        let mut env = func_outer_env.make_child_node_environment(block_as_expr, life.clone());

        let starting_env = env.snapshot(self.typing_interner);

        // val patternsTE = evaluateLets(env, coutputs, life + 0, body1.range :: parentRanges, callLocation, region, params1, params2)
        let range_list: &'t [RangeS<'s>] = self.typing_interner.alloc_slice_copy(
            &once(body_1.range).chain(parent_ranges.iter().copied()).collect::<Vec<_>>());
        let params_2_refs: Vec<&'t ParameterT<'s, 't>> = params_2.iter().collect();
        let patterns_te = self.evaluate_lets(
            &mut env, coutputs, life.add(self.typing_interner, 0),
            range_list, call_location, region, params_1, &params_2_refs);

        let (statements_from_block, returns_from_inside_maybe_with_never) =
            self.evaluate_block_statements(
                coutputs, starting_env, &mut env, life.add(self.typing_interner, 1),
                parent_ranges, call_location, starting_env.default_region, body_1.block)?;

        let unconverted_body_without_return =
            self.consecutive(&[patterns_te, statements_from_block]);

        let starting_env_ref = IInDenizenEnvironmentT::Node(starting_env);
        let converted_body_without_return = match maybe_expected_result_type {
            None => unconverted_body_without_return,
            Some(expected_result_type) => {
                if self.is_type_convertible(coutputs, starting_env_ref, parent_ranges, call_location,
                    unconverted_body_without_return.result().coord, expected_result_type) {
                    if unconverted_body_without_return.result().coord.kind == KindT::Never(NeverT { from_break: false }) {
                        unconverted_body_without_return
                    } else {
                        let func_outer_env_ref = IInDenizenEnvironmentT::Function(func_outer_env);
                        self.convert(func_outer_env_ref, coutputs, &range_list, call_location,
                            unconverted_body_without_return, expected_result_type)
                    }
                } else {
                    return Ok(Err(ResultTypeMismatchError {
                        expected_type: expected_result_type,
                        actual_type: unconverted_body_without_return.result().coord,
                    }));
                }
            }
        };

        let (converted_body_with_return, returns_maybe_with_never) =
            if converted_body_without_return.result().coord.kind == KindT::Never(NeverT { from_break: false }) {
                (converted_body_without_return, returns_from_inside_maybe_with_never)
            } else {
                let mut returns = returns_from_inside_maybe_with_never;
                returns.insert(converted_body_without_return.result().coord);
                let return_te =
                    ReferenceExpressionTE::Return(self.typing_interner.alloc(ReturnTE { source_expr: converted_body_without_return }));
                (return_te, returns)
            };

        let returns =
            if returns_maybe_with_never.len() > 1 {
                returns_maybe_with_never.into_iter().filter(|c| {
                    !matches!(c, CoordT { ownership: OwnershipT::Share, kind: KindT::Never(NeverT { from_break: false }), .. })
                }).collect()
            } else {
                returns_maybe_with_never
            };

        if is_destructor {
            // If it's a destructor, make sure that we've actually destroyed/moved/unlet'd
            // the parameter. For now, we'll just check if it's been moved away, but soon
            // we'll want fate to track whether it's been destroyed, and do that check instead.
            // We don't want the user to accidentally just move it somewhere, they need to
            // promise it gets destroyed.
            let destructee_name = params_2[0].name;
            if !env.unstackified_locals.contains(&destructee_name) {
                panic!("Destructee wasn't moved/destroyed!");
            }
        }

        Ok(Ok((&*self.typing_interner.alloc(BlockTE { inner: converted_body_with_return }), returns)))
    }

    pub fn evaluate_lets(
        &self,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        range: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        region: RegionT,
        params_1: &[&'s ParameterS<'s>],
        params_2: &[&'t ParameterT<'s, 't>],
    ) -> ReferenceExpressionTE<'s, 't> {
        // val paramLookups2 = params2.zipWithIndex.map({ case (p, index) => ArgLookupTE(index, p.tyype) })
        let param_lookups_2: Vec<ReferenceExpressionTE<'s, 't>> =
            params_2.iter().enumerate().map(|(index, p)| {
                ReferenceExpressionTE::ArgLookup(self.typing_interner.alloc(ArgLookupTE {
                    param_index: index as i32,
                    coord: p.tyype,
                }))
            }).collect();

        let param_lookups_2_refs: &'t [ReferenceExpressionTE<'s, 't>] =
            self.typing_interner.alloc_slice_from_vec(param_lookups_2);
        let patterns: &'t [&'s AtomSP<'s>] = self.typing_interner.alloc_slice_copy(
            &params_1.iter().map(|p| &p.pattern).collect::<Vec<_>>());
        let let_exprs_2 = self.translate_pattern_list(
            coutputs, nenv, life, range, call_location,
            patterns, param_lookups_2_refs, region);

        // todo: at this point, to allow for recursive calls, add a callable type to the environment
        // for everything inside the body to use

        for param in params_1.iter() {
            match (&param.pattern.name, param.pattern.name.as_ref().map(|c| c.mutate)) {
                (Some(capture), Some(false)) => {
                    let translated_name = self.translate_var_name_step(capture.name);
                    if !nenv.declared_locals.iter().any(|l| l.name() == translated_name) {
                        panic!("wot couldnt find {:?}", capture.name);
                    }
                }
                _ => {}
            }
        }

        let_exprs_2
    }

}