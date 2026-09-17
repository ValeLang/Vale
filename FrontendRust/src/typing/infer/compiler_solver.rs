use indexmap::{IndexMap, IndexSet};
use std::collections::{HashMap, HashSet};

use crate::utils::range::RangeS;
use crate::postparsing::itemplatatype::ITemplataType;
use crate::postparsing::rules::rules::*;
use crate::postparsing::names::*;
use crate::postparsing::*;
use crate::solver::solver::*;
use crate::solver::simple_solver_state::*;
use crate::typing::compiler::Compiler;
use crate::typing::citizen::impl_compiler::IsParentResult;
use crate::typing::ast::ast::*;
use crate::typing::ast::citizens::*;
use crate::typing::ast::expressions::*;
use crate::typing::compiler_outputs::*;
use crate::typing::templata::templata::*;
use crate::typing::types::types::*;
use crate::typing::names::names::*;
use crate::typing::env::environment::*;
use crate::typing::env::function_environment_t::*;
use crate::typing::env::i_env_entry::*;
use crate::higher_typing::ast::*;
use crate::interner::Interner;
use crate::keywords::Keywords;
use crate::typing::infer_compiler::InferEnv;
use crate::typing::overload_resolver::FindFunctionFailure;
use crate::typing::citizen::impl_compiler::IsntParent;
use crate::typing::citizen::struct_compiler::ResolveFailure;
use crate::typing::templata::conversions::evaluate_ownership;
use crate::parsing::ast::ast::OwnershipP;
use crate::typing::templata::templata::KindTemplataT;
use crate::typing::types::types::OwnershipT;
use crate::typing::templata::templata::CoordTemplataT;
use crate::typing::templata::conversions::evaluate_mutability;
use crate::typing::templata::conversions::evaluate_variability;
use crate::typing::typing_interner::TypingInterner;
use crate::typing::templata::templata::MutabilityTemplataT;
use crate::typing::templata::templata::OwnershipTemplataT;
use crate::typing::templata::templata::VariabilityTemplataT;
use crate::postparsing::rules::rule_scout::get_kind_equivalent_runes_iter;
use crate::solver::solver::make_solver_state;
use crate::typing::templata::templata::expect_integer;
use crate::typing::templata::templata::expect_mutability;
use crate::typing::templata::templata::expect_variability;
use std::iter::once;
use std::marker::PhantomData;

#[derive(Copy, Clone, Debug)]
pub enum ITypingPassSolverError<'s, 't> {
    KindIsNotConcrete { kind: KindT<'s, 't> },
    KindIsNotInterface { kind: KindT<'s, 't> },
    KindIsNotStruct { kind: KindT<'s, 't> },
    CouldntFindFunction { range: &'t [RangeS<'s>], fff: FindFunctionFailure<'s, 't> },
    CouldntFindImpl { range: &'t [RangeS<'s>], fail: &'t IsntParent<'s, 't> },
    CouldntResolveKind { rf: &'t ResolveFailure<'s, 't, KindT<'s, 't>> },
    CantShareMutable { kind: KindT<'s, 't> },
    CantSharePlaceholder { kind: KindT<'s, 't> },
    BadIsaSubKind { kind: KindT<'s, 't> },
    BadIsaSuperKind { kind: KindT<'s, 't> },
    SendingNonCitizen { kind: KindT<'s, 't> },
    CantCheckPlaceholder { range: &'t [RangeS<'s>] },
    ReceivingDifferentOwnerships { params: &'t [(IRuneS<'s>, CoordT<'s, 't>)] },
    SendingNonIdenticalKinds { send_coord: CoordT<'s, 't>, receive_coord: CoordT<'s, 't> },
    NoCommonAncestors { params: &'t [(IRuneS<'s>, CoordT<'s, 't>)] },
    LookupFailed { name: IImpreciseNameS<'s> },
    NoAncestorsSatisfyCall { params: &'t [(IRuneS<'s>, CoordT<'s, 't>)] },
    CantDetermineNarrowestKind { kinds: &'t [KindT<'s, 't>] },
    OwnershipDidntMatch { coord: CoordT<'s, 't>, expected_ownership: OwnershipT },
    CallResultWasntExpectedType { expected: ITemplataT<'s, 't>, actual: ITemplataT<'s, 't> },
    CallResultIsntCallable { result: ITemplataT<'s, 't> },
    OneOfFailed { rule: OneOfSR<'s> },
    IsaFailed { sub: KindT<'s, 't>, suuper: KindT<'s, 't> },
    WrongNumberOfTemplateArgs { expected_min_num_args: i32, expected_max_num_args: i32 },
    FunctionDoesntHaveName { range: &'t [RangeS<'s>], name: IFunctionNameT<'s, 't> },
    CantGetComponentsOfPlaceholderPrototype { range: &'t [RangeS<'s>] },
    ReturnTypeConflict { range: &'t [RangeS<'s>], expected_return_type: CoordT<'s, 't>, actual: PrototypeT<'s, 't> },
    InternalSolverError { range: &'t [RangeS<'s>], err: &'t ISolverError<IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>> },
}

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn get_runes(&self, rule: IRulexSR<'s>) -> Vec<IRuneS<'s>> {
        let result: Vec<IRuneS<'s>> = rule.rune_usages().iter().map(|ru| ru.rune).collect();
        if self.opts.global_options.sanity_check {
            // val sanityChecked: Vector[RuneUsage] =
            //   rule match {
            let sanity_checked: Vec<RuneUsage<'s>> =
                match rule {
                    //     case LookupSR(range, rune, literal) => Vector(rune)
                    IRulexSR::Lookup(r) => vec![r.rune],
                    //     case RuneParentEnvLookupSR(range, rune) => Vector(rune)
                    IRulexSR::RuneParentEnvLookup(r) => vec![r.rune],
                    //     case EqualsSR(range, left, right) => Vector(left, right)
                    IRulexSR::Equals(r) => vec![r.left, r.right],
                    //     case DefinitionCoordIsaSR(range, result, sub, suuper) => Vector(result, sub, suuper)
                    IRulexSR::DefinitionCoordIsa(r) => vec![r.result_rune, r.sub_rune, r.super_rune],
                    //     case CallSiteCoordIsaSR(range, result, sub, suuper) => result.toVector ++ Vector(sub, suuper)
                    IRulexSR::CallSiteCoordIsa(r) => {
                        let mut v: Vec<RuneUsage<'s>> = r.result_rune.into_iter().collect();
                        v.push(r.sub_rune);
                        v.push(r.super_rune);
                        v
                    }
                    //     case KindComponentsSR(range, resultRune, mutabilityRune) => Vector(resultRune, mutabilityRune)
                    IRulexSR::KindComponents(r) => vec![r.kind_rune, r.mutability_rune],
                    //     case CoordComponentsSR(range, resultRune, ownershipRune, kindRune) => Vector(resultRune, ownershipRune, kindRune)
                    IRulexSR::CoordComponents(r) => vec![r.result_rune, r.ownership_rune, r.kind_rune],
                    //     case PrototypeComponentsSR(range, resultRune, paramsRune, returnRune) => Vector(resultRune, paramsRune, returnRune)
                    IRulexSR::PrototypeComponents(r) => vec![r.result_rune, r.params_rune, r.return_rune],
                    //     case DefinitionFuncSR(range, resultRune, name, paramsListRune, returnRune) => Vector(resultRune, paramsListRune, returnRune)
                    IRulexSR::DefinitionFunc(r) => vec![r.result_rune, r.params_list_rune, r.return_rune],
                    //     case CallSiteFuncSR(range, resultRune, name, paramsListRune, returnRune) => Vector(resultRune, paramsListRune, returnRune)
                    IRulexSR::CallSiteFunc(r) => vec![r.prototype_rune, r.params_list_rune, r.return_rune],
                    //     case ResolveSR(range, resultRune, name, paramsListRune, returnRune) => Vector(resultRune, paramsListRune, returnRune)
                    IRulexSR::Resolve(r) => vec![r.result_rune, r.params_list_rune, r.return_rune],
                    //     case OneOfSR(range, rune, literals) => Vector(rune)
                    IRulexSR::OneOf(r) => vec![r.rune],
                    //     case IsConcreteSR(range, rune) => Vector(rune)
                    IRulexSR::IsConcrete(r) => vec![r.rune],
                    //     case IsInterfaceSR(range, rune) => Vector(rune)
                    IRulexSR::IsInterface(r) => vec![r.rune],
                    //     case IsStructSR(range, rune) => Vector(rune)
                    IRulexSR::IsStruct(r) => vec![r.rune],
                    //     case CoerceToCoordSR(range, coordRune, kindRune) => Vector(coordRune, kindRune)
                    IRulexSR::CoerceToCoord(r) => vec![r.coord_rune, r.kind_rune],
                    //     case LiteralSR(range, rune, literal) => Vector(rune)
                    IRulexSR::Literal(r) => vec![r.rune],
                    //     case AugmentSR(range, resultRune, ownership, innerRune) => Vector(resultRune, innerRune)
                    IRulexSR::Augment(r) => vec![r.result_rune, r.inner_rune],
                    //     case CallSR(range, resultRune, templateRune, args) => Vector(resultRune, templateRune) ++ args
                    IRulexSR::Call(r) => {
                        let mut v = vec![r.result_rune, r.template_rune];
                        v.extend_from_slice(r.args);
                        v
                    }
                    //     case PackSR(range, resultRune, members) => Vector(resultRune) ++ members
                    IRulexSR::Pack(r) => {
                        let mut v = vec![r.result_rune];
                        v.extend_from_slice(r.members);
                        v
                    }
                    //     case CoordSendSR(range, senderRune, receiverRune) => Vector(senderRune, receiverRune)
                    IRulexSR::CoordSend(r) => vec![r.sender_rune, r.receiver_rune],
                    //     case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => Vector(resultRune, coordListRune)
                    IRulexSR::RefListCompoundMutability(r) => vec![r.result_rune, r.coord_list_rune],
                    //     case other => vimpl(other)
                    other => panic!("get_runes sanity check: unhandled rule {:?}", other),
                };
            //   vassert(result sameElements sanityChecked.map(_.rune))
            let sanity_runes: Vec<IRuneS<'s>> = sanity_checked.iter().map(|ru| ru.rune).collect();
            assert!(result.iter().zip(sanity_runes.iter()).all(|(a, b)| a == b) && result.len() == sanity_runes.len());
        }
        result
    }

}

pub fn get_puzzles<'s>(rule: IRulexSR<'s>) -> Vec<Vec<IRuneS<'s>>> {
    //   rule match {
    match rule {
            //     // This means we can solve this puzzle and dont need anything to do it.
            //     case LookupSR(range, _, _) => Vector(Vector())
            IRulexSR::Lookup(_) => vec![vec![]],
            //     case RuneParentEnvLookupSR(range, rune) => Vector(Vector())
            IRulexSR::RuneParentEnvLookup(_) => vec![vec![]],
            //     case CallSR(range, resultRune, templateRune, args) => {
            //       Vector(
            //         Vector(templateRune.rune) ++ args.map(_.rune),
            //         Vector(resultRune.rune, templateRune.rune))
            //     }
            IRulexSR::Call(r) => {
                let mut first = vec![r.template_rune.rune];
                first.extend(r.args.iter().map(|a| a.rune));
                vec![first, vec![r.result_rune.rune, r.template_rune.rune]]
            }
            //     case PackSR(range, resultRune, members) => Vector(Vector(resultRune.rune), members.map(_.rune))
            IRulexSR::Pack(r) => {
                vec![vec![r.result_rune.rune], r.members.iter().map(|m| m.rune).collect()]
            }
            //     case KindComponentsSR(range, kindRune, mutabilityRune) => Vector(Vector(kindRune.rune))
            IRulexSR::KindComponents(r) => vec![vec![r.kind_rune.rune]],
            //     case CoordComponentsSR(range, resultRune, ownershipRune, kindRune) => Vector(Vector(resultRune.rune), Vector(ownershipRune.rune, kindRune.rune))
            IRulexSR::CoordComponents(r) => vec![vec![r.result_rune.rune], vec![r.ownership_rune.rune, r.kind_rune.rune]],
            //     case PrototypeComponentsSR(range, resultRune, paramsRune, returnRune) => Vector(Vector(resultRune.rune))
            IRulexSR::PrototypeComponents(r) => vec![vec![r.result_rune.rune]],
            //     case CallSiteFuncSR(range, resultRune, name, paramListRune, returnRune) => Vector(Vector(resultRune.rune))
            IRulexSR::CallSiteFunc(r) => vec![vec![r.prototype_rune.rune]],
            //     // Definition doesn't need the placeholder to be present, it's what populates the placeholder.
            //     case DefinitionFuncSR(range, placeholderRune, name, paramListRune, returnRune) => Vector(Vector(paramListRune.rune, returnRune.rune))
            IRulexSR::DefinitionFunc(r) => vec![vec![r.params_list_rune.rune, r.return_rune.rune]],
            //     // Per @BRRZ, ResolveSR fires in one of two modes: when both params and return
            //     // are known (existing predict path, postponing real resolution per SFWPRL), or
            //     // when only params are known (real overload lookup to discover the return).
            //     // Handler below branches on which condition triggered.
            //     case ResolveSR(range, resultRune, name, paramsListRune, returnRune) =>
            //       Vector(
            //         Vector(paramsListRune.rune, returnRune.rune),
            //         Vector(paramsListRune.rune))
            IRulexSR::Resolve(r) => vec![
                vec![r.params_list_rune.rune, r.return_rune.rune],
                vec![r.params_list_rune.rune],
            ],
            //     case OneOfSR(range, rune, literals) => Vector(Vector(rune.rune))
            IRulexSR::OneOf(r) => vec![vec![r.rune.rune]],
            //     case EqualsSR(range, leftRune, rightRune) => Vector(Vector(leftRune.rune), Vector(rightRune.rune))
            IRulexSR::Equals(r) => vec![vec![r.left.rune], vec![r.right.rune]],
            //     case IsConcreteSR(range, rune) => Vector(Vector(rune.rune))
            IRulexSR::IsConcrete(r) => vec![vec![r.rune.rune]],
            //     case IsInterfaceSR(range, rune) => Vector(Vector(rune.rune))
            IRulexSR::IsInterface(r) => vec![vec![r.rune.rune]],
            //     case IsStructSR(range, rune) => Vector(Vector(rune.rune))
            IRulexSR::IsStruct(r) => vec![vec![r.rune.rune]],
            //     case CoerceToCoordSR(range, coordRune, kindRune) => Vector(Vector(coordRune.rune), Vector(kindRune.rune))
            IRulexSR::CoerceToCoord(r) => vec![vec![r.coord_rune.rune], vec![r.kind_rune.rune]],
            //     case LiteralSR(range, rune, literal) => Vector(Vector())
            IRulexSR::Literal(_) => vec![vec![]],
            //     case AugmentSR(range, resultRune, ownership, innerRune) => Vector(Vector(innerRune.rune), Vector(resultRune.rune))
            IRulexSR::Augment(r) => vec![vec![r.inner_rune.rune], vec![r.result_rune.rune]],
            //     // See SAIRFU, this will replace itself with other rules.
            //     case CoordSendSR(range, senderRune, receiverRune) => Vector(Vector(senderRune.rune), Vector(receiverRune.rune))
            IRulexSR::CoordSend(r) => vec![vec![r.sender_rune.rune], vec![r.receiver_rune.rune]],
            //     case DefinitionCoordIsaSR(range, resultRune, senderRune, receiverRune) => Vector(Vector(senderRune.rune, receiverRune.rune))
            IRulexSR::DefinitionCoordIsa(r) => vec![vec![r.sub_rune.rune, r.super_rune.rune]],
            //     case CallSiteCoordIsaSR(range, resultRune, senderRune, receiverRune) => Vector(Vector(senderRune.rune, receiverRune.rune))
            IRulexSR::CallSiteCoordIsa(r) => vec![vec![r.sub_rune.rune, r.super_rune.rune]],
            //     case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => Vector(Vector(coordListRune.rune))
            IRulexSR::RefListCompoundMutability(r) => vec![vec![r.coord_list_rune.rune]],
            other => panic!("get_puzzles: unhandled rule {:?}", other),
        }
    }

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn make_solver_state_solver(
        &self,
        _range: Vec<RangeS<'s>>,
        env: InferEnv<'s, 't>,
        state: &mut CompilerOutputs<'s, 't>,
        rules: Vec<IRulexSR<'s>>,
        initial_rune_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>>,
        initially_known_rune_to_templata: IndexMap<IRuneS<'s>, ITemplataT<'s, 't>>,
    ) -> SimpleSolverState<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>> {
        for rule in &rules {
            for rune_usage in rule.rune_usages() {
                assert!(initial_rune_to_type.contains_key(&rune_usage.rune));
            }
        }

        // These two shouldn't both be in the rules, see SROACSD.
        assert!(
            rules.iter().all(|r| !matches!(r, IRulexSR::CallSiteFunc(_))) ||
            rules.iter().all(|r| !matches!(r, IRulexSR::DefinitionFunc(_))));
        // These two shouldn't both be in the rules, see SROACSD.
        assert!(
            rules.iter().all(|r| !matches!(r, IRulexSR::CallSiteCoordIsa(_))) ||
            rules.iter().all(|r| !matches!(r, IRulexSR::DefinitionCoordIsa(_))));

        for (rune, templata) in &initially_known_rune_to_templata {
            if self.opts.global_options.sanity_check {
                self.sanity_check_conclusion(&env, state, *rune, *templata);
            }
            assert_eq!(templata.tyype(self.scout_arena), *initial_rune_to_type.get(rune).unwrap());
        }

        let all_runes: Vec<IRuneS<'s>> = initial_rune_to_type.keys().copied().collect();

        let rule_to_puzzles: Box<dyn Fn(&IRulexSR<'s>) -> Vec<Vec<IRuneS<'s>>>> =
            Box::new(|rule| get_puzzles(*rule));
        let rule_to_runes: &dyn Fn(&IRulexSR<'s>) -> Vec<IRuneS<'s>> =
            &|rule| self.get_runes(*rule);

        make_solver_state(
            self.opts.global_options.sanity_check,
            self.opts.global_options.use_optimized_solver,
            rule_to_puzzles,
            rule_to_runes,
            rules,
            initially_known_rune_to_templata,
            all_runes,
        )
    }

    pub fn advance_infer(
        &self,
        env: InferEnv<'s, 't>,
        state: &mut CompilerOutputs<'s, 't>,
        solver_state: &mut SimpleSolverState<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>>,
    ) -> Result<bool, FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>> {
        //   solverState.sanityCheck()
        solver_state.sanity_check();
        for (_rune, _conclusion) in solver_state.userify_conclusions() {
            // Scala calls sanityCheckConclusion here; skipped for now
        }
        // Stage 1: Do simple solves
        match solver_state.get_next_solvable() {
            None => {}
            Some(solving_rule_index) => {
                let rule = *solver_state.get_rule(solving_rule_index);
                let steps_before = solver_state.get_steps().len();
                match self.solve(state, env, solver_state, solving_rule_index, rule) {
                    Ok(()) => {}
                    Err(e) => return Err(FailedSolve {
                        steps: solver_state.get_steps(),
                        conclusions: solver_state.get_conclusions().into_iter().collect(),
                        unsolved_rules: solver_state.get_unsolved_rules(),
                        unsolved_runes: solver_state.get_unsolved_runes(),
                        error: e,
                    }),
                }
                let steps_after = solver_state.get_steps().len();
                assert!(steps_after == steps_before + 1);
                // Per @CSCDSRZ, only true after simple solve.
                assert!(solver_state.rule_is_solved(solving_rule_index));
                solver_state.sanity_check();
                return Ok(true);
            }
        }
        // Stage 2: Do a complex solve if available.
        if !solver_state.get_unsolved_rules().is_empty() {
            let conclusions_before = solver_state.get_conclusions().len();
            match complex_solve(self, self.typing_interner, state, env, solver_state) {
                Ok(()) => {}
                Err(e) => return Err(FailedSolve {
                    steps: solver_state.get_steps(),
                    conclusions: solver_state.get_conclusions().into_iter().collect(),
                    unsolved_rules: solver_state.get_unsolved_rules(),
                    unsolved_runes: solver_state.get_unsolved_runes(),
                    error: e,
                }),
            }
            solver_state.sanity_check();
            let conclusions_after = solver_state.get_conclusions().len();
            // Per @CSCDSRZ, check conclusion count (not rules solved) for progress.
            if conclusions_after == conclusions_before {
                // There's nothing more to be done. Let's continue on to stage 3.
            } else {
                return Ok(true);
            }
        } else {
            // No more rules to solve, so continue to the wrapping up stages of the solve.
        }
        // Stage 3: We're done!
        Ok(false)
    }

    pub fn continue_solver(
        &self,
        env: InferEnv<'s, 't>,
        state: &mut CompilerOutputs<'s, 't>,
        solver_state: &mut SimpleSolverState<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>>,
    ) -> Result<(), FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>> {
        //   while ( {
        while {
            //     advanceInfer(
            //       env, state, solverState, delegate
            //     ) match {
            //       case Ok(continue) => continue
            //       case Err(f@FailedSolve(_, _, _, _, _)) => return Err(f)
            //     }
            self.advance_infer(env, state, solver_state)?
        } {}
        //   // If we get here, then there's nothing more the solver can do.
        //   Ok(Unit)
        Ok(())
    }

}

pub fn sanity_check_conclusion<'s, 't>(
    env: InferEnv<'s, 't>,
    state: CompilerOutputs<'s, 't>,
    rune: IRuneS<'s>,
    conclusion: ITemplataT<'s, 't>,
) {
    panic!("Unimplemented: sanity_check_conclusion");
}

fn complex_solve<'s, 'ctx, 't, 'a>(
    compiler: &'a Compiler<'s, 'ctx, 't>,
    typing_interner: &'a TypingInterner<'s, 't>,
    state: &mut CompilerOutputs<'s, 't>,
    env: InferEnv<'s, 't>,
    solver_state: &mut SimpleSolverState<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>>,
) -> Result<(), ISolverError<IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>>
where 's: 't,
{
    complex_solve_inner(compiler, typing_interner, state, env, solver_state)
}

fn complex_solve_inner<'s, 'ctx, 't, 'a>(
    compiler: &'a Compiler<'s, 'ctx, 't>,
    typing_interner: &'a TypingInterner<'s, 't>,
    state: &mut CompilerOutputs<'s, 't>,
    env: InferEnv<'s, 't>,
    solver_state: &mut SimpleSolverState<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>>,
) -> Result<(), ISolverError<IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>>
where 's: 't,
{
    let _env = env;
    let unsolved_rules = solver_state.get_unsolved_rules();

    let unsolved_receiver_runes: Vec<IRuneS<'s>> = unsolved_rules.iter().filter_map(|rule| {
        match rule {
            IRulexSR::CoordSend(r) => Some(r.receiver_rune.rune),
            IRulexSR::CallSiteCoordIsa(r) => Some(r.super_rune.rune),
            _ => None,
        }
    }).collect();

    let receiver_runes = get_kind_equivalent_runes_iter(
        &unsolved_rules,
        unsolved_receiver_runes.into_iter(),
    );

    let new_conclusions: IndexMap<IRuneS<'s>, ITemplataT<'s, 't>> = receiver_runes.iter().filter_map(|receiver| {
        let runes_sending_to_this_receiver = get_kind_equivalent_runes_iter(
            &unsolved_rules,
            unsolved_rules.iter().filter_map(|rule| match rule {
                IRulexSR::CoordSend(r) if r.receiver_rune.rune == *receiver => Some(r.sender_rune.rune),
                IRulexSR::CallSiteCoordIsa(r) if r.super_rune.rune == *receiver => Some(r.sub_rune.rune),
                _ => None,
            }),
        );
        let call_rules_template_runes: Vec<IRuneS<'s>> =
            unsolved_rules.iter().filter_map(|rule| match rule {
                IRulexSR::Call(r) if receiver_runes.contains(&r.result_rune.rune) => Some(r.template_rune.rune),
                _ => None,
            }).collect();
        let sender_conclusions: Vec<(IRuneS<'s>, CoordT<'s, 't>)> =
            runes_sending_to_this_receiver.iter().filter_map(|sender_rune| {
                solver_state.get_conclusion(sender_rune).and_then(|templata| match templata {
                    ITemplataT::Coord(ct) => Some((*sender_rune, ct.coord)),
                    _ => panic!("vwat: sender conclusion not a coord: {:?}", templata),
                })
            }).collect();
        let call_templates: Vec<ITemplataT<'s, 't>> =
            get_kind_equivalent_runes_iter(
                &unsolved_rules,
                call_rules_template_runes.iter().copied(),
            ).iter().filter_map(|rune| solver_state.get_conclusion(rune)).collect();
        assert!(call_templates.iter().map(|t| *t).collect::<HashSet<_>>().len() <= 1);
        let all_senders_known = sender_conclusions.len() == runes_sending_to_this_receiver.len();
        let all_calls_known = call_rules_template_runes.len() == call_templates.len();
        match solve_receives(compiler, typing_interner, state, _env, sender_conclusions.clone(), call_templates, all_senders_known, all_calls_known) {
            Err(e) => return Some(Err(ISolverError::RuleError(RuleError { err: e, _phantom: PhantomData }))),
            Ok(None) => return None,
            Ok(Some(receiver_instantiation_kind)) => {
                let possible_coords: Vec<CoordT<'s, 't>> = {
                    let mut v: Vec<CoordT<'s, 't>> = unsolved_rules.iter().filter_map(|rule| match rule {
                        IRulexSR::Augment(r) if r.result_rune.rune == *receiver => {
                            let ownership = evaluate_ownership(r.ownership.expect("vassertSome: augment ownership"));
                            Some(CoordT { ownership, region: RegionT { region: IRegionT::Default }, kind: receiver_instantiation_kind })
                        }
                        _ => None,
                    }).collect();
                    for (_, coord) in sender_conclusions.iter() {
                        v.push(CoordT { ownership: coord.ownership, region: RegionT { region: IRegionT::Default }, kind: receiver_instantiation_kind });
                    }
                    v
                };
                if possible_coords.is_empty() {
                    Some(Ok((*receiver, ITemplataT::Kind(typing_interner.alloc(KindTemplataT { kind: receiver_instantiation_kind })))))
                } else {
                    let ownerships: HashSet<OwnershipT> = possible_coords.iter().map(|c| c.ownership).collect();
                    let ownership = match ownerships.len() {
                        0 => panic!("vwat: no ownerships in possible_coords"),
                        1 => *ownerships.iter().next().unwrap(),
                        _ => {
                            let params = typing_interner.alloc_slice_from_vec(sender_conclusions);
                            return Some(Err(ISolverError::RuleError(RuleError { err: ITypingPassSolverError::ReceivingDifferentOwnerships { params }, _phantom: PhantomData })));
                        }
                    };
                    Some(Ok((*receiver, ITemplataT::Coord(typing_interner.alloc(CoordTemplataT {
                        coord: CoordT { ownership, region: RegionT { region: IRegionT::Default }, kind: receiver_instantiation_kind },
                    })))))
                }
            }
        }
    }).collect::<Result<IndexMap<_, _>, _>>().map_err(|e| e)?;

    // Per @CSCDSRZ, complex solve only produces conclusions — empty solvedRules and newRules is correct.
    match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(true, vec![], new_conclusions, vec![], IndexSet::new()) {
        Ok(_) => {}
        Err(e) => return Err(e),
    }

    Ok(())
}

fn solve_receives<'s, 'ctx, 't>(
    compiler: &Compiler<'s, 'ctx, 't>,
    typing_interner: &TypingInterner<'s, 't>,
    state: &mut CompilerOutputs<'s, 't>,
    env: InferEnv<'s, 't>,
    senders: Vec<(IRuneS<'s>, CoordT<'s, 't>)>,
    call_templates: Vec<ITemplataT<'s, 't>>,
    all_senders_known: bool,
    all_calls_known: bool,
) -> Result<Option<KindT<'s, 't>>, ITypingPassSolverError<'s, 't>>
where 's: 't,
{
    let sender_kinds: Vec<KindT<'s, 't>> = senders.iter().map(|(_, coord)| coord.kind).collect();
    if sender_kinds.is_empty() {
        return Ok(None);
    }
    let sender_ancestor_lists: Vec<HashSet<KindT<'s, 't>>> =
        sender_kinds.iter().map(|kind| compiler.get_ancestors(env, state, *kind, true)).collect();
    let common_ancestors: HashSet<KindT<'s, 't>> =
        sender_ancestor_lists.into_iter().reduce(|a, b| a.intersection(&b).copied().collect())
            .unwrap_or_default();
    if common_ancestors.is_empty() {
        let params = typing_interner.alloc_slice_from_vec(senders);
        return Err(ITypingPassSolverError::NoCommonAncestors { params });
    }
    let common_ancestors_call_constrained: HashSet<KindT<'s, 't>> =
        if call_templates.is_empty() {
            common_ancestors
        } else {
            common_ancestors.into_iter().filter(|ancestor| {
                call_templates.iter().any(|template| compiler.kind_is_from_template(state, *ancestor, *template))
            }).collect()
        };
    let narrowed_common_ancestor =
        if common_ancestors_call_constrained.is_empty() {
            let params = typing_interner.alloc_slice_from_vec(senders);
            return Err(ITypingPassSolverError::NoAncestorsSatisfyCall { params });
        } else if common_ancestors_call_constrained.len() == 1 {
            *common_ancestors_call_constrained.iter().next().unwrap()
        } else {
            if !all_senders_known {
                return Ok(None);
            }
            if !all_calls_known {
                return Ok(None);
            }
            match narrow(compiler, typing_interner, env, state, common_ancestors_call_constrained) {
                Ok(x) => x,
                Err(e) => return Err(e),
            }
        };
    Ok(Some(narrowed_common_ancestor))
}

fn narrow<'s, 'ctx, 't, 'a>(
    compiler: &'a Compiler<'s, 'ctx, 't>,
    typing_interner: &'a TypingInterner<'s, 't>,
    env: InferEnv<'s, 't>,
    state: &mut CompilerOutputs<'s, 't>,
    kinds: HashSet<KindT<'s, 't>>,
) -> Result<KindT<'s, 't>, ITypingPassSolverError<'s, 't>>
where 's: 't,
{
    assert!(kinds.len() > 1);
    let mut narrowed_ancestors: HashSet<KindT<'s, 't>> = kinds.iter().copied().collect();
    for kind in kinds.iter() {
        let ancestors = compiler.get_ancestors(env, state, *kind, false);
        for ancestor in ancestors {
            narrowed_ancestors.remove(&ancestor);
        }
    }
    if narrowed_ancestors.is_empty() {
        panic!("vwat: narrowed_ancestors empty in narrow");
    } else if narrowed_ancestors.len() == 1 {
        Ok(*narrowed_ancestors.iter().next().unwrap())
    } else {
        let kinds_slice = typing_interner.alloc_slice_from_vec(narrowed_ancestors.into_iter().collect());
        Err(ITypingPassSolverError::CantDetermineNarrowestKind { kinds: kinds_slice })
    }
}

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    fn solve(
        &self,
        state: &mut CompilerOutputs<'s, 't>,
        env: InferEnv<'s, 't>,
        solver_state: &mut SimpleSolverState<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>>,
        rule_index: i32,
        rule: IRulexSR<'s>,
    ) -> Result<(), ISolverError<IRuneS<'s>, ITemplataT<'s, 't>, ITypingPassSolverError<'s, 't>>> {
        //   solveRule(delegate, state, env, ruleIndex, rule, solverState) match {
        //     case Ok(x) => Ok(x)
        //     case Err(e) => Err(RuleError(e))
        //   }
        match self.solve_rule(state, env, rule_index, rule, solver_state) {
            Ok(x) => Ok(x),
            Err(e) => Err(ISolverError::RuleError(RuleError { err: e, _phantom: PhantomData })),
        }
    }

    fn solve_rule(
        &self,
        state: &mut CompilerOutputs<'s, 't>,
        env: InferEnv<'s, 't>,
        rule_index: i32,
        rule: IRulexSR<'s>,
        solver_state: &mut SimpleSolverState<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>>,
    ) -> Result<(), ITypingPassSolverError<'s, 't>> {
        //   rule match {
        match rule {
            //     case KindComponentsSR(...) =>
            //     case KindComponentsSR(range, kindRune, mutabilityRune) => {
            IRulexSR::KindComponents(kc) => {
                let kind = match solver_state.get_conclusion(&kc.kind_rune.rune).expect("kind rune not solved in KindComponentsSR") {
                    ITemplataT::Kind(kt) => kt.kind,
                    _ => panic!("Expected KindTemplataT in KindComponentsSR"),
                };
                let mutability = self.get_mutability(state, kind);
                let mut conclusions = IndexMap::new();
                conclusions.insert(kc.mutability_rune.rune, mutability);
                match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                    Ok(_) => Ok(()),
                    Err(e) => {
                        let ranges = once(kc.range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                        let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                        let error = self.typing_interner.alloc(e);
                        Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                    }
                }
            }
            //     case CoordComponentsSR(range, resultRune, ownershipRune, kindRune) => {
            IRulexSR::CoordComponents(cc) => {
                match solver_state.get_conclusion(&cc.result_rune.rune) {
                    None => {
                        let ownership = match solver_state.get_conclusion(&cc.ownership_rune.rune).expect("ownership rune not solved in CoordComponentsSR") {
                            ITemplataT::Ownership(ot) => ot.ownership,
                            _ => panic!("Expected OwnershipTemplataT in CoordComponentsSR"),
                        };
                        let kind = match solver_state.get_conclusion(&cc.kind_rune.rune).expect("kind rune not solved in CoordComponentsSR") {
                            ITemplataT::Kind(kt) => kt.kind,
                            _ => panic!("Expected KindTemplataT in CoordComponentsSR"),
                        };
                        let new_coord = match self.get_mutability(state, kind) {
                            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => {
                                CoordT { ownership: OwnershipT::Share, region: RegionT { region: IRegionT::Default }, kind }
                            }
                            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) | ITemplataT::Placeholder(PlaceholderTemplataT { .. }) => {
                                CoordT { ownership, region: RegionT { region: IRegionT::Default }, kind }
                            }
                            other => panic!("implement: CoordComponents unexpected mutability {:?}", other),
                        };
                        let new_templata = ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: new_coord }));
                        let mut conclusions = IndexMap::new();
                        conclusions.insert(cc.result_rune.rune, new_templata);
                        match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                            Ok(_) => Ok(()),
                            Err(e) => {
                                let ranges = once(cc.range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                                let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                                let error = self.typing_interner.alloc(e);
                                Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                            }
                        }
                    }
                    Some(coord_templata) => {
                        let coord = match coord_templata {
                            ITemplataT::Coord(ct) => ct.coord,
                            _ => panic!("Expected CoordTemplataT in CoordComponentsSR result"),
                        };
                        let mut conclusions = IndexMap::new();
                        conclusions.insert(cc.ownership_rune.rune, ITemplataT::Ownership(OwnershipTemplataT { ownership: coord.ownership }));
                        conclusions.insert(cc.kind_rune.rune, ITemplataT::Kind(self.typing_interner.alloc(KindTemplataT { kind: coord.kind })));
                        match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                            Ok(_) => Ok(()),
                            Err(e) => {
                                let ranges = once(cc.range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                                let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                                let error = self.typing_interner.alloc(e);
                                Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                            }
                        }
                    }
                }
            }
            //     case PrototypeComponentsSR(...) =>
            IRulexSR::PrototypeComponents(_) => { panic!("Unimplemented: solve_rule PrototypeComponents"); }
            //     case ResolveSR(range, resultRune, name, paramListRune, returnRune) => {
            IRulexSR::Resolve(resolve) => {
                // If we're here, then we're resolving a prototype.
                // This happens at the call-site.
                // The function (or struct) can either supply a default resolve rule (usually
                // via the `func moo(int)void` syntax) or let the caller pass it in.
                let param_coords = match solver_state.get_conclusion(&resolve.params_list_rune.rune).expect("paramListRune not solved in ResolveSR") {
                    ITemplataT::CoordList(cl) => cl.coords,
                    _ => panic!("Expected CoordListTemplataT in ResolveSR paramListRune"),
                };
                //       solverState.getConclusion(returnRune.rune) match {
                //         case Some(CoordTemplataT(returnCoord)) => {
                match solver_state.get_conclusion(&resolve.return_rune.rune) {
                    Some(ITemplataT::Coord(ct)) => {
                        // Existing predict path: both params and return are known. We only pretend
                        // the function exists for now; actual resolution is postponed to after the
                        // solve completes. See SFWPRL in docs/Generics.md:353.
                        let return_coord = ct.coord;
                        let prototype_templata = self.predict_function(env, state, resolve.range, resolve.name, param_coords, return_coord);
                        let new_templata = ITemplataT::Prototype(self.typing_interner.alloc(prototype_templata));
                        let mut conclusions = IndexMap::new();
                        conclusions.insert(resolve.result_rune.rune, new_templata);
                        match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                            Ok(_) => Ok(()),
                            Err(e) => {
                                let ranges = once(resolve.range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                                let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                                let error = self.typing_interner.alloc(e);
                                Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                            }
                        }
                    }
                    Some(_) => panic!("Expected CoordTemplataT in ResolveSR returnRune"),
                    //         case None => {
                    None => {
                        // Per @BRRZ, params are known but return isn't. Do a real overload lookup
                        // (the same delegate.resolveFunction the post-solve phase uses at
                        // InferCompiler.scala:350) so we can discover the return type and unblock
                        // the solver. Safety of this mid-solve lookup:
                        //   - CompilerOutputs.lookupFunction's signatureToFunction cache is the
                        //     recursion terminator for nested bound resolution.
                        //   - RuneTypeSolver.scala:210 already types returnRune as
                        //     CoordTemplataType, so the commitStep below is guaranteed well-typed.
                        //   - Per @SROACSD, no solver call site coexists DefinitionFuncSR with
                        //     ResolveSR, so there is no rule-ordering hazard.
                        //   - All state read by the call chain is frozen env + settled
                        //     CompilerOutputs; the outer solver's in-flight state is never
                        //     consulted.
                        let ranges = once(resolve.range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                        let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                        match self.resolve_function_from_infer_env(
                            env,
                            state,
                            ranges_slice,
                            resolve.name,
                            param_coords,
                        ).expect("CompileErrorExceptionT propagation") {
                            Ok(stamp_result) => {
                                let return_type = stamp_result.prototype.return_type;
                                let mut conclusions = IndexMap::new();
                                conclusions.insert(resolve.result_rune.rune, ITemplataT::Prototype(self.typing_interner.alloc(PrototypeTemplataT { prototype: stamp_result.prototype })));
                                conclusions.insert(resolve.return_rune.rune, ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: return_type })));
                                match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                                    Ok(_) => Ok(()),
                                    Err(e) => {
                                        let error = self.typing_interner.alloc(e);
                                        Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                                    }
                                }
                            }
                            Err(fff) => Err(ITypingPassSolverError::CouldntFindFunction { range: ranges_slice, fff }),
                        }
                    }
                }
            }
            //     case CallSiteFuncSR(range, prototypeRune, name, paramListRune, returnRune) => {
            IRulexSR::CallSiteFunc(csf) => {
                // If we're here, then we're solving in the callsite, not the definition.
                // This should look up a function with that name and param list, and make sure
                // its return matches.
                match solver_state.get_conclusion(&csf.prototype_rune.rune).expect("prototypeRune not solved in CallSiteFuncSR") {
                    ITemplataT::Prototype(proto_templata) => {
                        let prototype = proto_templata.prototype;
                        let mut conclusions = IndexMap::new();
                        conclusions.insert(csf.params_list_rune.rune, ITemplataT::CoordList(self.typing_interner.alloc(CoordListTemplataT { coords: prototype.param_types() })));
                        conclusions.insert(csf.return_rune.rune, ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: prototype.return_type })));
                        match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                            Ok(_) => Ok(()),
                            Err(e) => {
                                let ranges = once(csf.range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                                let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                                let error = self.typing_interner.alloc(e);
                                Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                            }
                        }
                    }
                    _ => {
                        let ranges = once(csf.range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                        let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                        Err(ITypingPassSolverError::CantCheckPlaceholder { range: ranges_slice })
                    }
                }
            }
            //     case DefinitionFuncSR(range, resultRune, name, paramListRune, returnRune) => {
            IRulexSR::DefinitionFunc(def_func) => {
                let param_coords = match solver_state.get_conclusion(&def_func.params_list_rune.rune).expect("DefinitionFunc paramListRune has no conclusion") {
                    ITemplataT::CoordList(cl) => cl.coords,
                    _ => panic!("implement: solve_rule DefinitionFunc non-CoordList paramList"),
                };
                let return_type = match solver_state.get_conclusion(&def_func.return_rune.rune).expect("DefinitionFunc returnRune has no conclusion") {
                    ITemplataT::Coord(ct) => ct.coord,
                    _ => panic!("implement: solve_rule DefinitionFunc non-Coord return"),
                };
                let new_prototype = self.assemble_prototype(env, state, def_func.range, def_func.name, param_coords, return_type);
                let new_templata = ITemplataT::Prototype(self.typing_interner.alloc(PrototypeTemplataT { prototype: new_prototype }));
                let mut conclusions = IndexMap::new();
                conclusions.insert(def_func.result_rune.rune, new_templata);
                match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                    Ok(_) => Ok(()),
                    Err(_e) => { panic!("implement: solve_rule DefinitionFunc InternalSolverError wrapping"); }
                }
            }
            //     case CallSiteCoordIsaSR(...) =>
            IRulexSR::CallSiteCoordIsa(csia) => {
                let sub_templata = solver_state.get_conclusion(&csia.sub_rune.rune)
                    .expect("vassertSome: subRune not solved in CallSiteCoordIsaSR");
                let sub_coord = match sub_templata {
                    ITemplataT::Coord(ct) => ct.coord,
                    _ => panic!("Expected CoordTemplataT for subRune in CallSiteCoordIsaSR"),
                };
                let super_templata = solver_state.get_conclusion(&csia.super_rune.rune)
                    .expect("vassertSome: superRune not solved in CallSiteCoordIsaSR");
                let super_coord = match super_templata {
                    ITemplataT::Coord(ct) => ct.coord,
                    _ => panic!("Expected CoordTemplataT for superRune in CallSiteCoordIsaSR"),
                };

                let resulting_isa_templata: ITemplataT<'s, 't> = if sub_coord == super_coord {
                    ITemplataT::Isa(self.typing_interner.alloc(self.assemble_impl(env, csia.range, sub_coord.kind, super_coord.kind)))
                } else if matches!(sub_coord.kind, KindT::Never(_)) {
                    ITemplataT::Isa(self.typing_interner.alloc(self.assemble_impl(env, csia.range, sub_coord.kind, super_coord.kind)))
                } else {
                    let sub_kind = match ISubKindTT::try_from(sub_coord.kind) {
                        Ok(k) => k,
                        Err(_) => return Err(ITypingPassSolverError::BadIsaSubKind { kind: sub_coord.kind }),
                    };
                    let super_kind = match ISuperKindTT::try_from(super_coord.kind) {
                        Ok(k) => k,
                        Err(_) => return Err(ITypingPassSolverError::BadIsaSuperKind { kind: super_coord.kind }),
                    };
                    match self.is_parent(state, env.original_calling_env, env.parent_ranges, env.call_location, sub_kind, super_kind) {
                        IsParentResult::IsntParent(_) => return Err(ITypingPassSolverError::IsaFailed { sub: sub_coord.kind, suuper: super_coord.kind }),
                        IsParentResult::IsParent(is_parent) => is_parent.templata,
                    }
                };

                let mut conclusions = IndexMap::new();
                if let Some(result_rune) = csia.result_rune {
                    conclusions.insert(result_rune.rune, resulting_isa_templata);
                }
                match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                    Ok(_) => Ok(()),
                    Err(e) => {
                        let ranges = once(csia.range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                        let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                        let error = self.typing_interner.alloc(e);
                        Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                    }
                }
            }
            //     case DefinitionCoordIsaSR(range, resultRune, subRune, superRune) => {
            IRulexSR::DefinitionCoordIsa(dcia) => {
                // If we're here, then we're solving in the definition, not the callsite.
                // Skip checking that they match, just assume they do.
                let sub_templata = solver_state.get_conclusion(&dcia.sub_rune.rune)
                    .expect("vassertSome: subRune not solved in DefinitionCoordIsaSR");
                let sub_kind_unchecked = match sub_templata {
                    ITemplataT::Coord(ct) => ct.coord.kind,
                    _ => panic!("Expected CoordTemplataT for subRune in DefinitionCoordIsaSR"),
                };
                let super_templata = solver_state.get_conclusion(&dcia.super_rune.rune)
                    .expect("vassertSome: superRune not solved in DefinitionCoordIsaSR");
                let super_kind_unchecked = match super_templata {
                    ITemplataT::Coord(ct) => ct.coord.kind,
                    _ => panic!("Expected CoordTemplataT for superRune in DefinitionCoordIsaSR"),
                };
                let sub_kind = match ISubKindTT::try_from(sub_kind_unchecked) {
                    Ok(k) => k,
                    Err(_) => return Err(ITypingPassSolverError::BadIsaSubKind { kind: sub_kind_unchecked }),
                };
                let super_kind = match ISuperKindTT::try_from(super_kind_unchecked) {
                    Ok(k) => k,
                    Err(_) => return Err(ITypingPassSolverError::BadIsaSuperKind { kind: super_kind_unchecked }),
                };
                // Now introduce an impl so that we can later know sub implements super.
                let new_impl = self.assemble_impl(env, dcia.range, sub_kind.into(), super_kind.into());
                let mut conclusions = IndexMap::new();
                conclusions.insert(dcia.result_rune.rune, ITemplataT::Isa(self.typing_interner.alloc(new_impl)));
                match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                    Ok(_) => Ok(()),
                    Err(e) => {
                        let ranges = once(dcia.range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                        let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                        let error = self.typing_interner.alloc(e);
                        Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                    }
                }
            }
            //     case EqualsSR(range, leftRune, rightRune) => {
            IRulexSR::Equals(equals) => {
                match solver_state.get_conclusion(&equals.left.rune) {
                    None => {
                        let right = solver_state.get_conclusion(&equals.right.rune).expect("Neither left nor right rune solved in EqualsSR");
                        let mut conclusions = IndexMap::new();
                        conclusions.insert(equals.left.rune, right.clone());
                        match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                            Ok(_) => Ok(()),
                            Err(e) => {
                                let ranges = once(equals.range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                                let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                                let error = self.typing_interner.alloc(e);
                                Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                            }
                        }
                    }
                    Some(left) => {
                        let left = left.clone();
                        let mut conclusions = IndexMap::new();
                        conclusions.insert(equals.right.rune, left);
                        match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                            Ok(_) => Ok(()),
                            Err(e) => {
                                let ranges = once(equals.range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                                let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                                let error = self.typing_interner.alloc(e);
                                Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                            }
                        }
                    }
                }
            }
            //     case CoordSendSR(...) =>
            IRulexSR::CoordSend(coord_send) => {
                // See IRFU and SRCAMP for what's going on here.
                match solver_state.get_conclusion(&coord_send.receiver_rune.rune) {
                    None => {
                        let sender_templata = solver_state.get_conclusion(&coord_send.sender_rune.rune).expect("Neither receiverRune nor senderRune solved in CoordSendSR");
                        let coord = match sender_templata {
                            ITemplataT::Coord(ct) => ct.coord,
                            _ => panic!("Expected CoordTemplataT in CoordSendSR sender"),
                        };
                        if self.is_descendant_kind(&env, state, coord.kind) {
                            let new_rule = IRulexSR::CallSiteCoordIsa(CallSiteCoordIsaSR {
                                range: coord_send.range,
                                result_rune: None,
                                sub_rune: coord_send.sender_rune,
                                super_rune: coord_send.receiver_rune,
                            });
                            match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], IndexMap::new(), vec![new_rule], IndexSet::new()) {
                                Ok(_) => Ok(()),
                                Err(e) => {
                                    let ranges = once(coord_send.range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                                    let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                                    let error = self.typing_interner.alloc(e);
                                    Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                                }
                            }
                        } else {
                            let mut conclusions = IndexMap::new();
                            conclusions.insert(coord_send.receiver_rune.rune, ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord })));
                            match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                                Ok(_) => Ok(()),
                                Err(e) => {
                                    let ranges = once(coord_send.range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                                    let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                                    let error = self.typing_interner.alloc(e);
                                    Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                                }
                            }
                        }
                    }
                    Some(ITemplataT::Coord(receiver_coord_templata)) => {
                        let coord = receiver_coord_templata.coord;
                        if self.is_ancestor_kind(&env, state, coord.kind) {
                            let new_rule = IRulexSR::CallSiteCoordIsa(CallSiteCoordIsaSR {
                                range: coord_send.range,
                                result_rune: None,
                                sub_rune: coord_send.sender_rune,
                                super_rune: coord_send.receiver_rune,
                            });
                            match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], IndexMap::new(), vec![new_rule], IndexSet::new()) {
                                Ok(_) => Ok(()),
                                Err(e) => {
                                    let ranges = once(coord_send.range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                                    let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                                    let error = self.typing_interner.alloc(e);
                                    Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                                }
                            }
                        } else {
                            let mut conclusions = IndexMap::new();
                            conclusions.insert(coord_send.sender_rune.rune, ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord })));
                            match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                                Ok(_) => Ok(()),
                                Err(e) => {
                                    let ranges = once(coord_send.range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                                    let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                                    let error = self.typing_interner.alloc(e);
                                    Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                                }
                            }
                        }
                    }
                    Some(_other) => { panic!("implement: solve_rule CoordSend unexpected receiver conclusion"); }
                }
            }
            //     case OneOfSR(...) =>
            IRulexSR::OneOf(r) => {
                let result = solver_state.get_conclusion(&r.rune.rune).unwrap();
                let templatas: Vec<ITemplataT<'s, 't>> = r.literals.iter().map(|l| self.literal_to_templata(*l)).collect();
                if templatas.contains(&result) {
                    let ranges: Vec<RangeS<'s>> = once(r.range).chain(env.parent_ranges.iter().copied()).collect();
                    let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                    match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], IndexMap::new(), vec![], IndexSet::new()) {
                        Ok(_) => Ok(()),
                        Err(e) => {
                            let error = self.typing_interner.alloc(e);
                            Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                        }
                    }
                } else {
                    Err(ITypingPassSolverError::OneOfFailed { rule: r })
                }
            }
            //     case IsConcreteSR(...) =>
            IRulexSR::IsConcrete(_) => { panic!("Unimplemented: solve_rule IsConcrete"); }
            //     case IsInterfaceSR(...) =>
            IRulexSR::IsInterface(_) => { panic!("Unimplemented: solve_rule IsInterface"); }
            //     case IsStructSR(...) =>
            IRulexSR::IsStruct(_) => { panic!("Unimplemented: solve_rule IsStruct"); }
            //     case CoerceToCoordSR(...) =>
            IRulexSR::CoerceToCoord(r) => {
                match solver_state.get_conclusion(&r.kind_rune.rune) {
                    None => {
                        let coord_templata = solver_state.get_conclusion(&r.coord_rune.rune).unwrap_or_else(|| panic!("implement: solve_rule CoerceToCoord no coord conclusion either"));
                        let coord = match coord_templata { ITemplataT::Coord(ct) => ct.coord, _ => panic!("implement: solve_rule CoerceToCoord coord conclusion not CoordTemplataT") };
                        match coord.ownership {
                            OwnershipT::Own | OwnershipT::Share => {
                                let mut conclusions = IndexMap::new();
                                conclusions.insert(r.kind_rune.rune, ITemplataT::Kind(self.typing_interner.alloc(KindTemplataT { kind: coord.kind })));
                                let ranges: Vec<RangeS<'s>> = once(r.range).chain(env.parent_ranges.iter().copied()).collect();
                                let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                                match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                                    Ok(_) => Ok(()),
                                    Err(e) => {
                                        let error = self.typing_interner.alloc(e);
                                        Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                                    }
                                }
                            }
                            _ => Err(ITypingPassSolverError::OwnershipDidntMatch { coord, expected_ownership: OwnershipT::Own }),
                        }
                    }
                    Some(kind) => {
                        let ranges: Vec<RangeS<'s>> = once(r.range).chain(env.parent_ranges.iter().copied()).collect();
                        let coerced = self.coerce_to_coord(state, env.original_calling_env, &ranges, kind, RegionT { region: IRegionT::Default });
                        let mut conclusions = IndexMap::new();
                        conclusions.insert(r.coord_rune.rune, coerced);
                        match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                            Ok(_) => Ok(()),
                            Err(_e) => { panic!("Unimplemented: solve_rule CoerceToCoord InternalSolverError wrapping"); }
                        }
                    }
                }
            }
            //     case LiteralSR(range, rune, literal) =>
            IRulexSR::Literal(r) => {
                let templata = self.literal_to_templata(r.literal);
                let mut conclusions = IndexMap::new();
                conclusions.insert(r.rune.rune, templata);
                match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                    Ok(_) => Ok(()),
                    Err(_e) => { panic!("Unimplemented: solve_rule Literal InternalSolverError wrapping"); }
                }
            }
            //     case LookupSR(...) =>
            IRulexSR::Lookup(r) => {
                let ranges: Vec<RangeS<'s>> = once(r.range).chain(env.parent_ranges.iter().copied()).collect();
                let result = match self.lookup_templata_imprecise(env, state, &ranges, r.name) {
                    None => return Err(ITypingPassSolverError::LookupFailed { name: r.name }),
                    Some(x) => x,
                };
                let mut conclusions = IndexMap::new();
                conclusions.insert(r.rune.rune, result);
                match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                    Ok(_) => Ok(()),
                    Err(_e) => { panic!("Unimplemented: solve_rule Lookup InternalSolverError wrapping"); }
                }
            }
            //     case RuneParentEnvLookupSR(...) =>
            IRulexSR::RuneParentEnvLookup(r) => {
                // This rule should never reach the solver — callers are required to preprocess
                // it out (look up the rune in callingEnv, emit an InitialKnown, strip the rule).
                // Canonical preprocessing fold: OverloadResolver.scala:311-325. See MKRFA /
                // docs/refactor-thoughts/mkrfa-protocol-leak.md for the full contract.
                panic!("vwat: RuneParentEnvLookupSR should have been MKRFA-preprocessed before reaching the solver: {:?}", r.rune)
            }
            //     case AugmentSR(...) =>
            IRulexSR::Augment(augment) => {
                match solver_state.get_conclusion(&augment.result_rune.rune) {
                    Some(outer_coord_templata) => {
                        let outer_coord = match outer_coord_templata { ITemplataT::Coord(ct) => ct.coord, _ => panic!("implement: solve_rule Augment outerCoordRune not CoordTemplataT") };
                        let inner_ownership = match augment.ownership {
                            None => outer_coord.ownership,
                            Some(augment_ownership) => {
                                match self.get_mutability(state, outer_coord.kind) {
                                    ITemplataT::Placeholder(_) | ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) => {
                                        if augment_ownership == OwnershipP::Share {
                                            return Err(ITypingPassSolverError::CantShareMutable { kind: outer_coord.kind });
                                        }
                                        if outer_coord.ownership != evaluate_ownership(augment_ownership) {
                                            return Err(ITypingPassSolverError::OwnershipDidntMatch { coord: outer_coord, expected_ownership: evaluate_ownership(augment_ownership) });
                                        }
                                        OwnershipT::Own
                                    }
                                    ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => outer_coord.ownership,
                                    _ => panic!("implement: solve_rule Augment Some unexpected mutability"),
                                }
                            }
                        };
                        let inner_coord = CoordT { ownership: inner_ownership, region: outer_coord.region, kind: outer_coord.kind };
                        let ranges: Vec<RangeS<'s>> = once(augment.range).chain(env.parent_ranges.iter().copied()).collect();
                        let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                        let mut conclusions = IndexMap::new();
                        conclusions.insert(augment.inner_rune.rune, ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: inner_coord })));
                        match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                            Ok(_) => Ok(()),
                            Err(e) => {
                                let error = self.typing_interner.alloc(e);
                                Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                            }
                        }
                    }
                    None => {
                        let inner_templata = solver_state.get_conclusion(&augment.inner_rune.rune).expect("Neither outerCoordRune nor innerRune solved in AugmentSR");
                        let inner_coord = match inner_templata {
                            ITemplataT::Coord(ct) => ct.coord,
                            _ => panic!("Expected CoordTemplataT in AugmentSR inner"),
                        };
                        let new_region = RegionT { region: IRegionT::Default };
                        let new_ownership = match augment.ownership {
                            None => inner_coord.ownership,
                            Some(augment_ownership) => {
                                let mutability = self.get_mutability(state, inner_coord.kind);
                                match mutability {
                                    ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => {
                                        inner_coord.ownership
                                    }
                                    ITemplataT::Placeholder(PlaceholderTemplataT { .. }) => {
                                        if augment_ownership == OwnershipP::Share {
                                            return Err(ITypingPassSolverError::CantSharePlaceholder { kind: inner_coord.kind });
                                        }
                                        evaluate_ownership(augment_ownership)
                                    }
                                    ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) => {
                                        if augment_ownership == OwnershipP::Share {
                                            return Err(ITypingPassSolverError::CantShareMutable { kind: inner_coord.kind });
                                        }
                                        evaluate_ownership(augment_ownership)
                                    }
                                    _ => { panic!("implement: solve_rule Augment unexpected mutability"); }
                                }
                            }
                        };
                        let new_coord = CoordT { ownership: new_ownership, region: new_region, kind: inner_coord.kind };
                        let new_templata = ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: new_coord }));
                        let mut conclusions = IndexMap::new();
                        conclusions.insert(augment.result_rune.rune, new_templata);
                        match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                            Ok(_) => Ok(()),
                            Err(e) => {
                                panic!("implement: solve_rule Augment InternalSolverError wrapping");
                            }
                        }
                    }
                }
            }
            //     case PackSR(range, resultRune, memberRunes) => {
            IRulexSR::Pack(pack) => {
                match solver_state.get_conclusion(&pack.result_rune.rune) {
                    None => {
                        let members: Vec<CoordT<'s, 't>> = pack.members.iter().map(|member_rune| {
                            match solver_state.get_conclusion(&member_rune.rune).expect("Pack member rune has no conclusion") {
                                ITemplataT::Coord(ct) => ct.coord,
                                _ => panic!("implement: solve_rule Pack member non-Coord templata"),
                            }
                        }).collect();
                        let members_slice = self.typing_interner.alloc_slice_from_vec(members);
                        let coord_list = self.typing_interner.alloc(CoordListTemplataT { coords: members_slice });
                        let mut conclusions = IndexMap::new();
                        conclusions.insert(pack.result_rune.rune, ITemplataT::CoordList(coord_list));
                        match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                            Ok(_) => Ok(()),
                            Err(_e) => { panic!("implement: solve_rule Pack None InternalSolverError wrapping"); }
                        }
                    }
                    Some(ITemplataT::CoordList(coord_list_templata)) => {
                        let members = coord_list_templata.coords;
                        assert_eq!(members.len(), pack.members.len());
                        let conclusions: IndexMap<IRuneS<'s>, ITemplataT<'s, 't>> = pack.members.iter().zip(members.iter()).map(|(rune, coord)| {
                            (rune.rune, ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: *coord })))
                        }).collect();
                        match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                            Ok(_) => Ok(()),
                            Err(_e) => { panic!("implement: solve_rule Pack Some InternalSolverError wrapping"); }
                        }
                    }
                    Some(_other) => { panic!("implement: solve_rule Pack unexpected result conclusion type"); }
                }
            }
            //     case CallSR(range, resultRune, templateRune, argRunes) => {
            //       solveCallRule(delegate, state, env, solverState, ruleIndex, range, resultRune, templateRune, argRunes)
            //     }
            IRulexSR::Call(r) => {
                self.solve_call_rule(state, &env, solver_state, rule_index, r.range, r.result_rune, r.template_rune, r.args)
            }
            //     case RefListCompoundMutabilitySR(...) =>
            IRulexSR::RefListCompoundMutability(_) => { panic!("Unimplemented: solve_rule RefListCompoundMutability"); }
            other => panic!("Unimplemented: solve_rule {:?}", other),
        }
    }

    fn solve_call_rule(
        &self,
        state: &mut CompilerOutputs<'s, 't>,
        env: &InferEnv<'s, 't>,
        solver_state: &mut SimpleSolverState<IRulexSR<'s>, IRuneS<'s>, ITemplataT<'s, 't>>,
        rule_index: i32,
        range: RangeS<'s>,
        result_rune: RuneUsage<'s>,
        template_rune: RuneUsage<'s>,
        arg_runes: &[RuneUsage<'s>],
    ) -> Result<(), ITypingPassSolverError<'s, 't>> {
        match solver_state.get_conclusion(&result_rune.rune) {
            Some(result) => {
                let ranges: Vec<RangeS<'s>> = once(range).chain(env.parent_ranges.iter().copied()).collect();
                let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                match result {
                    ITemplataT::Kind(kt) => {
                        match kt.kind {
                            KindT::Struct(struct_tt) => {
                                let struct_name = IStructNameT::try_from(struct_tt.id.local_name).unwrap_or_else(|_| panic!("solve_call_rule Some StructTT: local_name is not IStructNameT"));
                                let template_def = solver_state.get_conclusion(&template_rune.rune).unwrap_or_else(|| panic!("solve_call_rule Some StructTT: template_rune not solved"));
                                match template_def {
                                    ITemplataT::StructDefinition(it) => {
                                        if !self.citizen_is_from_template(ICitizenTT::Struct(struct_tt), template_def) {
                                            return Err(ITypingPassSolverError::CallResultWasntExpectedType { expected: template_def, actual: result });
                                        }
                                        let conclusions: IndexMap<IRuneS<'s>, ITemplataT<'s, 't>> =
                                            struct_name.template_args().iter().zip(arg_runes.iter())
                                                .map(|(template_arg, arg_rune)| (arg_rune.rune, *template_arg))
                                                .collect();
                                        match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                                            Ok(_) => return Ok(()),
                                            Err(e) => {
                                                let error = self.typing_interner.alloc(e);
                                                return Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error });
                                            }
                                        }
                                    }
                                    other => return Err(ITypingPassSolverError::CallResultWasntExpectedType { expected: other, actual: result }),
                                }
                            }
                            KindT::KindPlaceholder(_) => return Err(ITypingPassSolverError::CallResultIsntCallable { result }),
                            KindT::Str(_) | KindT::Int(_) | KindT::Bool(_) | KindT::Float(_) | KindT::Void(_) => {
                                return Err(ITypingPassSolverError::CallResultIsntCallable { result });
                            }
                            KindT::Interface(interface_tt) => {
                                let interface_inner_name = match interface_tt.id.local_name {
                                    INameT::Interface(r) => r,
                                    other => panic!("solve_call_rule Some InterfaceTT: local_name is not IInterfaceNameT: {:?}", other),
                                };
                                let template_def = solver_state.get_conclusion(&template_rune.rune).unwrap_or_else(|| panic!("solve_call_rule Some InterfaceTT: template_rune not solved"));
                                match template_def {
                                    ITemplataT::InterfaceDefinition(_it) => {
                                        if !self.citizen_is_from_template(ICitizenTT::Interface(interface_tt), template_def) {
                                            return Err(ITypingPassSolverError::CallResultWasntExpectedType { expected: template_def, actual: result });
                                        }
                                    }
                                    other => return Err(ITypingPassSolverError::CallResultWasntExpectedType { expected: other, actual: result }),
                                }
                                let conclusions: IndexMap<IRuneS<'s>, ITemplataT<'s, 't>> =
                                    arg_runes.iter().zip(interface_inner_name.template_args.iter())
                                        .map(|(arg_rune, template_arg)| (arg_rune.rune, *template_arg))
                                        .collect();
                                match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                                    Ok(_) => return Ok(()),
                                    Err(e) => {
                                        let error = self.typing_interner.alloc(e);
                                        return Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error });
                                    }
                                }
                            }
                            KindT::RuntimeSizedArray(rsa_tt) => {
                                if arg_runes.len() != 2 {
                                    return Err(ITypingPassSolverError::WrongNumberOfTemplateArgs { expected_min_num_args: 2, expected_max_num_args: 2 });
                                }
                                let template_def = solver_state.get_conclusion(&template_rune.rune).expect("vassertSome: template_rune not solved in RuntimeSizedArray arm");
                                match template_def {
                                    ITemplataT::RuntimeSizedArrayTemplate(_) => {
                                        if !self.kind_is_from_template(state, KindT::RuntimeSizedArray(rsa_tt), template_def) {
                                            return Err(ITypingPassSolverError::CallResultWasntExpectedType { expected: template_def, actual: result });
                                        }
                                    }
                                    other => return Err(ITypingPassSolverError::CallResultWasntExpectedType { expected: other, actual: result }),
                                }
                                let mutability_rune = arg_runes[0];
                                let element_rune = arg_runes[1];
                                let mut conclusions = IndexMap::new();
                                conclusions.insert(mutability_rune.rune, rsa_tt.mutability());
                                conclusions.insert(element_rune.rune, ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: rsa_tt.element_type() })));
                                match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                                    Ok(_) => return Ok(()),
                                    Err(e) => {
                                        let error = self.typing_interner.alloc(e);
                                        return Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error });
                                    }
                                }
                            }
                            KindT::StaticSizedArray(ssa_tt) => {
                                if arg_runes.len() != 4 {
                                    return Err(ITypingPassSolverError::WrongNumberOfTemplateArgs { expected_min_num_args: 4, expected_max_num_args: 4 });
                                }
                                let template_def = solver_state.get_conclusion(&template_rune.rune).expect("vassertSome: template_rune not solved in StaticSizedArray arm");
                                match template_def {
                                    ITemplataT::StaticSizedArrayTemplate(_) => {
                                        if !self.kind_is_from_template(state, KindT::StaticSizedArray(ssa_tt), template_def) {
                                            return Err(ITypingPassSolverError::CallResultWasntExpectedType { expected: template_def, actual: result });
                                        }
                                    }
                                    other => return Err(ITypingPassSolverError::CallResultWasntExpectedType { expected: other, actual: result }),
                                }
                                // We don't take in the region rune here because there's no syntactical way to specify it.
                                let size_rune = arg_runes[0];
                                let mutability_rune = arg_runes[1];
                                let variability_rune = arg_runes[2];
                                let element_rune = arg_runes[3];
                                let mut conclusions = IndexMap::new();
                                conclusions.insert(size_rune.rune, ssa_tt.size());
                                conclusions.insert(mutability_rune.rune, ssa_tt.mutability());
                                conclusions.insert(variability_rune.rune, ssa_tt.variability());
                                conclusions.insert(element_rune.rune, ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: ssa_tt.element_type() })));
                                match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                                    Ok(_) => return Ok(()),
                                    Err(e) => {
                                        let error = self.typing_interner.alloc(e);
                                        return Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error });
                                    }
                                }
                            }
                            _ => unreachable!("Scala's solve_call_rule Some-branch Kind match is exhaustive in practice (RSA/SSA only); other Kind variants vimpl-deferred"),
                        }
                    }
                    _ => unreachable!("Scala's solve_call_rule Some branch handles only Kind result; other ITemplataT variants vimpl-deferred"),
                }
            }
            None => {
                let template = solver_state.get_conclusion(&template_rune.rune).expect("vassertSome: template_rune not solved in solve_call_rule None branch");
                match template {
                    ITemplataT::RuntimeSizedArrayTemplate(_) => {
                        let args: Vec<ITemplataT<'s, 't>> = arg_runes.iter().map(|arg_rune| {
                            solver_state.get_conclusion(&arg_rune.rune).expect("vassertSome: arg_rune not solved in solve_call_rule RuntimeSizedArrayTemplate")
                        }).collect();
                        let m = args[0];
                        let coord = match args[1] {
                            ITemplataT::Coord(ct) => ct.coord,
                            _ => panic!("Expected CoordTemplataT as second arg in solve_call_rule RuntimeSizedArrayTemplate"),
                        };
                        let context_region = RegionT { region: IRegionT::Default };
                        let mutability = expect_mutability(m);
                        let rsa_kind = self.predict_runtime_sized_array_kind(*env, state, coord, mutability, context_region);
                        let mut conclusions = IndexMap::new();
                        conclusions.insert(result_rune.rune, ITemplataT::Kind(self.typing_interner.alloc(KindTemplataT { kind: KindT::RuntimeSizedArray(self.typing_interner.intern_runtime_sized_array_tt(RuntimeSizedArrayTTValT { name: rsa_kind.name })) })));
                        match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                            Ok(_) => Ok(()),
                            Err(e) => {
                                let ranges = once(range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                                let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                                let error = self.typing_interner.alloc(e);
                                Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                            }
                        }
                    }
                    ITemplataT::StaticSizedArrayTemplate(_) => {
                        let args: Vec<ITemplataT<'s, 't>> = arg_runes.iter().map(|arg_rune| {
                            solver_state.get_conclusion(&arg_rune.rune).expect("vassertSome: arg_rune not solved in solve_call_rule StaticSizedArrayTemplate")
                        }).collect();
                        let s = args[0];
                        let m = args[1];
                        let v = args[2];
                        let coord = match args[3] {
                            ITemplataT::Coord(ct) => ct.coord,
                            _ => panic!("Expected CoordTemplataT as fourth arg in solve_call_rule StaticSizedArrayTemplate"),
                        };
                        let context_region = RegionT { region: IRegionT::Default };
                        let size = expect_integer(s);
                        let mutability = expect_mutability(m);
                        let variability = expect_variability(v);
                        let ssa_kind = self.predict_static_sized_array_kind(*env, state, mutability, variability, size, coord, context_region);
                        let mut conclusions = IndexMap::new();
                        conclusions.insert(result_rune.rune, ITemplataT::Kind(self.typing_interner.alloc(KindTemplataT { kind: KindT::StaticSizedArray(self.typing_interner.intern_static_sized_array_tt(StaticSizedArrayTTValT { name: ssa_kind.name })) })));
                        let ranges: Vec<RangeS<'s>> = once(range).chain(env.parent_ranges.iter().copied()).collect();
                        let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                        match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                            Ok(_) => Ok(()),
                            Err(e) => {
                                let error = self.typing_interner.alloc(e);
                                Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                            }
                        }
                    }
                    ITemplataT::StructDefinition(it) => {
                        let args: Vec<ITemplataT<'s, 't>> = arg_runes.iter().map(|arg_rune| {
                            solver_state.get_conclusion(&arg_rune.rune).expect("vassertSome: arg_rune not solved in solve_call_rule")
                        }).collect();
                        let kind = self.predict_struct(state, env.original_calling_env, env.parent_ranges, env.call_location, *it, &args);
                        let mut conclusions = IndexMap::new();
                        conclusions.insert(result_rune.rune, ITemplataT::Kind(self.typing_interner.alloc(KindTemplataT { kind: KindT::Struct(self.typing_interner.intern_struct_tt(StructTTValT { id: kind.id })) })));
                        match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                            Ok(_) => Ok(()),
                            Err(e) => {
                                let ranges = once(range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                                let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                                let error = self.typing_interner.alloc(e);
                                Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                            }
                        }
                    }
                    ITemplataT::InterfaceDefinition(it) => {
                        let args: Vec<ITemplataT<'s, 't>> = arg_runes.iter().map(|arg_rune| {
                            solver_state.get_conclusion(&arg_rune.rune).expect("vassertSome: arg_rune not solved in solve_call_rule")
                        }).collect();
                        // See SFWPRL for why we're calling predict_interface instead of resolve_interface
                        let kind = self.predict_interface(state, env.original_calling_env, env.parent_ranges, env.call_location, *it, &args);
                        let mut conclusions = IndexMap::new();
                        conclusions.insert(result_rune.rune, ITemplataT::Kind(self.typing_interner.alloc(KindTemplataT { kind: KindT::Interface(self.typing_interner.intern_interface_tt(InterfaceTTValT { id: kind.id })) })));
                        match solver_state.commit_step::<ITypingPassSolverError<'s, 't>>(false, vec![rule_index], conclusions, vec![], IndexSet::new()) {
                            Ok(_) => Ok(()),
                            Err(e) => {
                                let ranges = once(range).chain(env.parent_ranges.iter().copied()).collect::<Vec<_>>();
                                let ranges_slice = self.typing_interner.alloc_slice_from_vec(ranges);
                                let error = self.typing_interner.alloc(e);
                                Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error })
                            }
                        }
                    }
                    ITemplataT::Kind(_kt) => { panic!("Unimplemented: solve_call_rule None Kind"); }
                    other => panic!("vimpl: solve_call_rule None {:?}", other),
                }
            }
        }
    }

    fn literal_to_templata(&self, literal: ILiteralSL<'s>) -> ITemplataT<'s, 't> {
        match literal {
            ILiteralSL::MutabilityLiteral(m) => ITemplataT::Mutability(MutabilityTemplataT { mutability: evaluate_mutability(m.mutability) }),
            ILiteralSL::OwnershipLiteral(o) => ITemplataT::Ownership(OwnershipTemplataT { ownership: evaluate_ownership(o.ownership) }),
            ILiteralSL::VariabilityLiteral(v) => ITemplataT::Variability(VariabilityTemplataT { variability: evaluate_variability(v.variability) }),
            ILiteralSL::StringLiteral(s) => ITemplataT::String(s.value),
            ILiteralSL::IntLiteral(i) => ITemplataT::Integer(i.value),
            ILiteralSL::BoolLiteral(_) => unreachable!("Scala's literalToTemplata has no BoolLiteral arm; constructed by TemplexScout but never reaches solver in practice"),
            ILiteralSL::LocationLiteral(_) => unreachable!("Scala's literalToTemplata has no LocationLiteral arm; constructed by TemplexScout but never reaches solver in practice"),
        }
    }
    
}

