use crate::scout_arena::ScoutArena;
use crate::postparsing::names::IRuneS;
use crate::postparsing::rules::rules::IRulexSR;
use crate::solver::{
    FailedSolve, ISolverError, SimpleSolverState, SolveIncomplete, make_solver_state,
};
use crate::utils::range::RangeS;
use indexmap::{IndexMap, IndexSet};
use std::collections::{HashMap, HashSet};
use std::marker::PhantomData;

#[derive(Clone, Debug, PartialEq)]
pub struct IdentifiabilitySolveError<'s> {
  pub range: Vec<RangeS<'s>>,
  pub failed_solve: FailedSolve<IRulexSR<'s>, IRuneS<'s>, bool, IIdentifiabilityRuleError>,
}

#[derive(Clone, Debug, PartialEq)]
pub enum IIdentifiabilityRuleError {}

fn get_runes<'s>(rule: &IRulexSR<'s>) -> Vec<IRuneS<'s>>
where {
  rule.rune_usages().into_iter().map(|u| u.rune).collect()
}

fn get_puzzles<'s>(rule: &IRulexSR<'s>) -> Vec<Vec<IRuneS<'s>>> {
  match rule {
    IRulexSR::Equals(x) => vec![vec![x.left.rune.clone()], vec![x.right.rune.clone()]],
    IRulexSR::MaybeCoercingLookup(_) => vec![vec![]],
    IRulexSR::Lookup(_) => vec![vec![]],
    IRulexSR::RuneParentEnvLookup(_) => {
      // This Vector() literally means nothing can solve this puzzle.
      // It needs to be passed in via identifying rune.
      vec![]
    }
    IRulexSR::MaybeCoercingCall(x) => {
      // We can't determine the template from the result and args because we might be coercing its
      // returned kind to a coord. So we need the template.
      // We can't determine the return type because we don't know whether we're coercing or not.
      let mut second = vec![x.template_rune.rune.clone()];
      second.extend(x.args.iter().map(|a| a.rune.clone()));
      vec![
        vec![x.result_rune.rune.clone(), x.template_rune.rune.clone()],
        second,
      ]
    }
    IRulexSR::Pack(x) => {
      // Packs are always lists of coords
      vec![vec![x.result_rune.rune.clone()], x.members.iter().map(|m| m.rune.clone()).collect()]
    }
    IRulexSR::DefinitionCoordIsa(_) => vec![vec![]],
    IRulexSR::CallSiteCoordIsa(_) => vec![vec![]],
    IRulexSR::KindComponents(_) => vec![vec![]],
    IRulexSR::CoordComponents(_) => vec![vec![]],
    IRulexSR::PrototypeComponents(_) => vec![vec![]],
    IRulexSR::Resolve(_) => vec![vec![]],
    IRulexSR::CallSiteFunc(_) => vec![vec![]],
    IRulexSR::DefinitionFunc(_) => vec![vec![]],
    IRulexSR::OneOf(_) => vec![vec![]],
    IRulexSR::IsConcrete(_) => panic!("IRulexSR::IsConcrete not yet migrated in identifiability get_puzzles"),
    IRulexSR::IsInterface(_) => vec![vec![]],
    IRulexSR::IsStruct(_) => panic!("IRulexSR::IsStruct not yet migrated in identifiability get_puzzles"),
    IRulexSR::CoerceToCoord(_) => vec![vec![]],
    IRulexSR::Literal(_) => vec![vec![]],
    IRulexSR::Augment(_) => vec![vec![]],
    IRulexSR::Call(_) => panic!("IRulexSR::Call not yet migrated in identifiability get_puzzles"),
    IRulexSR::CoordSend(_) => panic!("IRulexSR::CoordSend not yet migrated in identifiability get_puzzles"),
    IRulexSR::RefListCompoundMutability(_) => panic!("IRulexSR::RefListCompoundMutability not yet migrated in identifiability get_puzzles"),
    IRulexSR::IndexList(_) => panic!("IRulexSR::IndexList not yet migrated in identifiability get_puzzles"),
  }
}

fn solve_rule_impl<'s>(
  rule_index: i32,
  _call_range: &[RangeS<'s>],
  rule: &IRulexSR<'s>,
  solver_state: &mut SimpleSolverState<IRulexSR<'s>, IRuneS<'s>, bool>,
) -> Result<(), ISolverError<IRuneS<'s>, bool, IIdentifiabilityRuleError>> {
  match rule {
    IRulexSR::KindComponents(x) => {
      solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], [(x.kind_rune.rune.clone(), true), (x.mutability_rune.rune.clone(), true)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::CoordComponents(x) => {
      solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], [(x.result_rune.rune.clone(), true), (x.ownership_rune.rune.clone(), true), (x.kind_rune.rune.clone(), true)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::PrototypeComponents(x) => {
      solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], [(x.result_rune.rune.clone(), true), (x.params_rune.rune.clone(), true), (x.return_rune.rune.clone(), true)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::MaybeCoercingCall(x) => {
      let mut conclusions: IndexMap<IRuneS<'s>, bool> = [(x.result_rune.rune.clone(), true), (x.template_rune.rune.clone(), true)].into_iter().collect();
      for arg in x.args {
        conclusions.insert(arg.rune.clone(), true);
      }
      solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], conclusions, vec![], IndexSet::new())
    }
    IRulexSR::Resolve(x) => {
      solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], [(x.result_rune.rune.clone(), true), (x.params_list_rune.rune.clone(), true), (x.return_rune.rune.clone(), true)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::CallSiteFunc(x) => {
      solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], [(x.prototype_rune.rune.clone(), true), (x.params_list_rune.rune.clone(), true), (x.return_rune.rune.clone(), true)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::DefinitionFunc(x) => {
      solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], [(x.result_rune.rune.clone(), true), (x.params_list_rune.rune.clone(), true), (x.return_rune.rune.clone(), true)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::DefinitionCoordIsa(x) => {
        solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], [(x.result_rune.rune.clone(), true), (x.sub_rune.rune.clone(), true), (x.super_rune.rune.clone(), true)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::CallSiteCoordIsa(x) => {
        let mut conclusions: IndexMap<IRuneS<'s>, bool> = [(x.sub_rune.rune.clone(), true), (x.super_rune.rune.clone(), true)].into_iter().collect();
        if let Some(result_rune) = &x.result_rune {
            conclusions.insert(result_rune.rune.clone(), true);
        }
        solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], conclusions, vec![], IndexSet::new())
    }
    IRulexSR::OneOf(x) => {
      solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], [(x.rune.rune.clone(), true)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::Equals(x) => {
      solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], [(x.left.rune.clone(), true), (x.right.rune.clone(), true)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::IsConcrete(_) => panic!("IRulexSR::IsConcrete not yet migrated in identifiability solve_rule"),
    IRulexSR::IsInterface(x) => {
      solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], [(x.rune.rune.clone(), true)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::IsStruct(_) => panic!("IRulexSR::IsStruct not yet migrated in identifiability solve_rule"),
    IRulexSR::RefListCompoundMutability(_) => panic!("IRulexSR::RefListCompoundMutability not yet migrated in identifiability solve_rule"),
    IRulexSR::CoerceToCoord(x) => {
      solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], [(x.kind_rune.rune.clone(), true), (x.coord_rune.rune.clone(), true)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::Literal(x) => {
      solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], [(x.rune.rune.clone(), true)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::Lookup(x) => {
      solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], [(x.rune.rune.clone(), true)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::MaybeCoercingLookup(x) => {
      solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], [(x.rune.rune.clone(), true)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::RuneParentEnvLookup(_) => {
      panic!("unimplemented");
    }
    IRulexSR::Augment(x) => {
      solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], [(x.result_rune.rune.clone(), true), (x.inner_rune.rune.clone(), true)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::Call(_) => panic!("IRulexSR::Call not yet migrated in identifiability solve_rule"),
    IRulexSR::CoordSend(_) => panic!("IRulexSR::CoordSend not yet migrated in identifiability solve_rule"),
    IRulexSR::Pack(x) => {
      let mut conclusions: IndexMap<IRuneS<'s>, bool> = x.members.iter().map(|m| (m.rune.clone(), true)).collect();
      conclusions.insert(x.result_rune.rune.clone(), true);
      solver_state.commit_step::<IIdentifiabilityRuleError>(false, vec![rule_index], conclusions, vec![], IndexSet::new())
    }
    IRulexSR::IndexList(_) => panic!("IRulexSR::IndexList not yet migrated in identifiability solve_rule"),
  }
}

pub(crate) fn solve_identifiability<'s>(
  sanity_check: bool,
  _use_optimized_solver: bool,
  _scout_arena: &ScoutArena<'s>,
  call_range: &[RangeS<'s>],
  rules: &'s [IRulexSR<'s>],
  identifying_runes: &[IRuneS<'s>],
) -> Result<HashMap<IRuneS<'s>, bool>, IdentifiabilitySolveError<'s>> {
  let initially_known_runes: IndexMap<_, _> =
    identifying_runes.iter().map(|r| (r.clone(), true)).collect();

  let all_runes: Vec<IRuneS<'s>> = {
    let mut set = HashSet::new();
    let mut out = Vec::new();
    for r in rules
      .iter()
      .flat_map(get_runes)
      .chain(initially_known_runes.keys().cloned())
    {
      if set.insert(r.clone()) {
        out.push(r);
      }
    }
    out
  };

  let mut solver_state = make_solver_state(
    sanity_check,
    false,
    Box::new(get_puzzles),
    &get_runes,
    rules.to_vec(),
    initially_known_runes,
    all_runes,
  );

  // Inline advance loop (matches Scala's while loop in solve())
  loop {
    solver_state.sanity_check();
    // Stage 1: simple solve
    match solver_state.get_next_solvable() {
      None => break, // No more solvable rules
      Some(rule_index) => {
        let rule = solver_state.get_rule(rule_index).clone();
        let steps_before = solver_state.get_steps().len();
        match solve_rule_impl(rule_index, call_range, &rule, &mut solver_state) {
          Ok(()) => {}
          Err(e) => {
            return Err(IdentifiabilitySolveError {
              range: call_range.to_vec(),
              failed_solve: FailedSolve {
                steps: solver_state.get_steps(),
                conclusions: solver_state.get_conclusions().into_iter().collect(),
                unsolved_rules: solver_state.get_unsolved_rules(),
                unsolved_runes: solver_state.get_unsolved_runes(),
                error: e,
              },
            })
          }
        }
        let steps_after = solver_state.get_steps().len();
        assert!(steps_after == steps_before + 1);
        assert!(solver_state.rule_is_solved(rule_index));
        solver_state.sanity_check();
      }
    }
  }
  // If we get here, then there's nothing more the solver can do.

  let steps = solver_state.get_steps();
  let conclusions: HashMap<_, _> = solver_state.userify_conclusions().into_iter().collect();
  let unsolved_runes = solver_state.get_unsolved_runes();

  if !unsolved_runes.is_empty() {
    Err(IdentifiabilitySolveError {
      range: call_range.to_vec(),
      failed_solve: FailedSolve {
        steps,
        conclusions: conclusions.clone(),
        unsolved_rules: solver_state.get_unsolved_rules(),
        unsolved_runes,
        error: ISolverError::SolveIncomplete(
          SolveIncomplete {
            _phantom: PhantomData,
          }
        ),
      },
    })
  } else {
    Ok(conclusions)
  }
}
