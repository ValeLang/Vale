use crate::typing::compiler::Compiler;
use crate::postparsing::ast::LocationInDenizen;
use crate::utils::range::RangeS;
use crate::postparsing::names::*;
use crate::postparsing::rules::rules::{IRulexSR, RuneUsage};
use crate::typing::overload_resolver::{FindFunctionFailure, IFindFunctionFailureReason};
use crate::typing::infer_compiler::IResolvingError;
use crate::typing::infer::compiler_solver::ITypingPassSolverError;
use crate::solver::solver::{FailedSolve, ISolverError};
use crate::typing::ast::ast::*;
use crate::typing::ast::expressions::*;
use crate::typing::env::environment::*;
use crate::typing::env::function_environment_t::*;
use crate::typing::names::names::*;
use crate::typing::types::types::*;
use crate::typing::compiler_outputs::*;
use crate::typing::templata::templata::*;
use crate::typing::compiler_error_reporter::ICompileErrorT;

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn evaluate_call(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        context_region: RegionT,
        callable_expr: ReferenceExpressionTE<'s, 't>,
        explicit_template_arg_rules_s: &[IRulexSR<'s>],
        explicit_template_arg_runes_s: &[IRuneS<'s>],
        receiving_rune_to_explicit_template_arg_rune: &[(RuneUsage<'s>, RuneUsage<'s>)],
        given_args_exprs_2: &[ReferenceExpressionTE<'s, 't>],
    ) -> Result<ReferenceExpressionTE<'s, 't>, ICompileErrorT<'s, 't>> {
        match callable_expr.result().coord.kind {
            KindT::Never(NeverT { from_break: true }) => { panic!("vwat"); }
            KindT::Never(NeverT { from_break: false }) | KindT::Bool(_) => {
                panic!("wot {:?}", callable_expr.result().coord.kind);
            }
            KindT::OverloadSet(overload_set) => {
                let unconverted_args_pointer_types_2: Vec<CoordT<'s, 't>> =
                    given_args_exprs_2.iter().map(|e| e.result().coord).collect();

                // We want to get the prototype here, not the entire header, because
                // we might be in the middle of a recursive call like:
                // func main():Int(main())
                let stamp_result = match self.find_function(
                        overload_set.env,
                        coutputs,
                        range,
                        call_location,
                        *overload_set.name,
                        explicit_template_arg_rules_s,
                        explicit_template_arg_runes_s,
                        receiving_rune_to_explicit_template_arg_rune,
                        context_region,
                        &unconverted_args_pointer_types_2,
                        &[],
                        false)?
                {
                    Err(e @ FindFunctionFailure { name: IImpreciseNameS::CodeName(CodeNameS { name: as_name }), .. }) if *as_name == self.keywords.r#as => {
                        let isa_failures: Vec<(KindT<'s, 't>, KindT<'s, 't>, FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>)> =
                            e.rejected_callee_to_reason.iter().filter_map(|(_, reason)| {
                                match reason {
                                    IFindFunctionFailureReason::InferFailure { reason: fs } => {
                                        match &fs.error {
                                            ISolverError::RuleError(rule_err) => {
                                                match &rule_err.err {
                                                    ITypingPassSolverError::IsaFailed { sub, suuper } => Some((*sub, *suuper, fs.clone())),
                                                    _ => None,
                                                }
                                            }
                                            _ => None,
                                        }
                                    }
                                    IFindFunctionFailureReason::FindFunctionResolveFailure { reason: IResolvingError::ResolvingSolveFailedOrIncomplete(fs) } => {
                                        match &fs.error {
                                            ISolverError::RuleError(rule_err) => {
                                                match &rule_err.err {
                                                    ITypingPassSolverError::IsaFailed { sub, suuper } => Some((*sub, *suuper, fs.clone())),
                                                    _ => None,
                                                }
                                            }
                                            _ => None,
                                        }
                                    }
                                    _ => None,
                                }
                            }).collect();
                        if !isa_failures.is_empty() {
                            let (sub, suuper, _) = isa_failures[0].clone();
                            let failed_solves: Vec<_> = isa_failures.into_iter().map(|(_, _, fs)| fs).collect();
                            return Err(ICompileErrorT::CantDowncastUnrelatedTypes {
                                range: self.typing_interner.alloc_slice_copy(range),
                                source_kind: suuper,
                                target_kind: sub,
                                candidates: self.typing_interner.alloc_slice_from_vec(failed_solves),
                            });
                        } else {
                            return Err(ICompileErrorT::CouldntFindFunctionToCallT {
                                range: self.typing_interner.alloc_slice_copy(range),
                                fff: e,
                            });
                        }
                    }
                    Err(e) => return Err(ICompileErrorT::CouldntFindFunctionToCallT {
                        range: self.typing_interner.alloc_slice_copy(range),
                        fff: e,
                    }),
                    Ok(x) => x,
                };

                let snapshot = nenv.snapshot(self.typing_interner);
                let snapshot_env = IInDenizenEnvironmentT::Node(snapshot);
                let param_types = stamp_result.prototype.param_types();
                let args_exprs_2 =
                    self.convert_exprs(
                        snapshot_env, coutputs, range, call_location,
                        given_args_exprs_2, &param_types);

                self.check_types(
                    coutputs,
                    snapshot_env,
                    range,
                    call_location,
                    &param_types,
                    &args_exprs_2.iter().map(|a| a.result().coord).collect::<Vec<_>>(),
                    true);

                assert!(coutputs.get_instantiation_bounds(self.typing_interner, stamp_result.prototype.id).is_some());
                let result_te = stamp_result.prototype.return_type;
                Ok(ReferenceExpressionTE::FunctionCall(self.typing_interner.alloc(FunctionCallTE {
                    callable: stamp_result.prototype,
                    args: self.typing_interner.alloc_slice_from_vec(args_exprs_2),
                    return_type: result_te,
                })))
            }
            other => {
                self.evaluate_custom_call(
                    nenv,
                    coutputs,
                    life,
                    range,
                    call_location,
                    context_region,
                    other,
                    explicit_template_arg_rules_s,
                    explicit_template_arg_runes_s,
                    receiving_rune_to_explicit_template_arg_rune,
                    callable_expr,
                    given_args_exprs_2)
            }
        }
    }

    pub fn evaluate_custom_call(
        &self,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        context_region: RegionT,
        kind: KindT<'s, 't>,
        explicit_template_arg_rules_s: &[IRulexSR<'s>],
        explicit_template_arg_runes_s: &[IRuneS<'s>],
        receiving_rune_to_explicit_template_arg_rune: &[(RuneUsage<'s>, RuneUsage<'s>)],
        given_callable_unborrowed_expr_2: ReferenceExpressionTE<'s, 't>,
        given_args_exprs_2: &[ReferenceExpressionTE<'s, 't>],
    ) -> Result<ReferenceExpressionTE<'s, 't>, ICompileErrorT<'s, 't>> {
        // Whether we're given a borrow or an own, the call itself will be given a borrow.
        let given_callable_borrow_expr_2: ReferenceExpressionTE<'s, 't> =
            match given_callable_unborrowed_expr_2.result().coord {
                CoordT { ownership: OwnershipT::Borrow | OwnershipT::Share, .. } => given_callable_unborrowed_expr_2,
                CoordT { ownership: OwnershipT::Own, .. } => {
                    panic!("Unimplemented: evaluate_custom_call OwnT makeTemporaryLocal");
                }
                _ => { panic!("Unimplemented: evaluate_custom_call unexpected ownership"); }
            };

        let env = nenv.snapshot(self.typing_interner);

        let args_types_2: Vec<CoordT<'s, 't>> = given_args_exprs_2.iter().map(|e| e.result().coord).collect();
        let closure_param_type = CoordT { ownership: given_callable_borrow_expr_2.result().coord.ownership, region: RegionT { region: IRegionT::Default }, kind };
        let mut param_filters = vec![closure_param_type];
        param_filters.extend_from_slice(&args_types_2);

        let env_ref = IInDenizenEnvironmentT::Node(env);
        let resolved = match self.find_function(
                env_ref,
                coutputs,
                range,
                call_location,
                self.scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: self.keywords.underscores_call })),
                explicit_template_arg_rules_s,
                explicit_template_arg_runes_s,
                receiving_rune_to_explicit_template_arg_rune,
                context_region,
                &param_filters,
                &[],
                false)?
        {
            Err(_e) => { panic!("CouldntFindFunctionToCallT"); }
            Ok(x) => x,
        };

        let mutability = self.get_mutability(coutputs, kind);
        let ownership =
            match mutability {
                ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) => OwnershipT::Borrow,
                ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => OwnershipT::Share,
                ITemplataT::Placeholder(_) => OwnershipT::Borrow,
                _ => { panic!("Unimplemented: evaluate_custom_call unexpected mutability"); }
            };
        assert!(given_callable_borrow_expr_2.result().coord.ownership == ownership);
        let actual_callable_expr_2 = given_callable_borrow_expr_2;

        let mut actual_args_exprs_2: Vec<ReferenceExpressionTE<'s, 't>> = vec![actual_callable_expr_2];
        actual_args_exprs_2.extend_from_slice(given_args_exprs_2);

        let arg_types: Vec<CoordT<'s, 't>> = actual_args_exprs_2.iter().map(|e| e.result().coord).collect();
        if arg_types != resolved.prototype.param_types() {
            panic!("arg param type mismatch. params: {:?} args: {:?}", resolved.prototype.param_types(), arg_types);
        }

        self.check_types(coutputs, env_ref, range, call_location, &resolved.prototype.param_types(), &arg_types, true);

        assert!(coutputs.get_instantiation_bounds(self.typing_interner, resolved.prototype.id).is_some());
        let result_te = resolved.prototype.return_type;
        Ok(ReferenceExpressionTE::FunctionCall(self.typing_interner.alloc(FunctionCallTE {
            callable: resolved.prototype,
            args: self.typing_interner.alloc_slice_from_vec(actual_args_exprs_2),
            return_type: result_te,
        })))
    }

    pub fn check_types(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        parent_ranges: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        params: &[CoordT<'s, 't>],
        args: &[CoordT<'s, 't>],
        exact: bool,
    ) {
        assert!(params.len() == args.len());
        for (params_head, args_head) in params.iter().zip(args.iter()) {
            if params_head == args_head {
                // match, nothing to do
            } else {
                if !exact {
                    panic!("implement: checkTypes non-exact isTypeConvertible");
                } else {
                    match args_head.kind {
                        KindT::Never(_) => {
                            // This is fine, no conversion will ever actually happen.
                            // This can be seen in this call: +(5, panic())
                        }
                        _ => {
                            // do stuff here.
                            // also there is one special case here, which is when we try to hand in
                            // an owning when they just want a borrow, gotta account for that here
                            panic!("do stuff {:?} and {:?}", args_head, params_head);
                        }
                    }
                }
            }
        }
    }

    pub fn evaluate_prefix_call(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        region: RegionT,
        callable_reference_expr_2: ReferenceExpressionTE<'s, 't>,
        explicit_template_arg_rules_s: &[IRulexSR<'s>],
        explicit_template_arg_runes_s: &[IRuneS<'s>],
        receiving_rune_to_explicit_template_arg_rune: &[(RuneUsage<'s>, RuneUsage<'s>)],
        args_exprs_2: &[ReferenceExpressionTE<'s, 't>],
    ) -> Result<ReferenceExpressionTE<'s, 't>, ICompileErrorT<'s, 't>> {
        let call_expr =
            self.evaluate_call(
                coutputs,
                nenv,
                life,
                range,
                call_location,
                region,
                callable_reference_expr_2,
                explicit_template_arg_rules_s,
                explicit_template_arg_runes_s,
                receiving_rune_to_explicit_template_arg_rune,
                args_exprs_2)?;
        Ok(call_expr)
    }

}