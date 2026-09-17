
use std::marker::PhantomData;

use super::simple_solver_state::SimpleSolverState;
use indexmap::{IndexMap, IndexSet};
use std::collections::HashMap;
use std::collections::HashSet;
use std::convert::Infallible;
use std::hash::Hash;

#[derive(Clone, Debug, PartialEq)]
pub struct Step<Rule, Rune, Conclusion>
where
    Rune: Eq + Hash,
{
    pub complex: bool,
    pub solved_rules: Vec<(i32, Rule)>,
    pub added_rules: Vec<Rule>,
    pub conclusions: HashMap<Rune, Conclusion>,
}
impl<Rule, Rune, Conclusion> Step<Rule, Rune, Conclusion>
where
    Rune: Eq + Hash,
{
}

#[derive(Clone, Debug, PartialEq)]
pub struct FailedSolve<Rule, Rune, Conclusion, ErrType>
where
    Rune: Eq + Hash,
{
    pub steps: Vec<Step<Rule, Rune, Conclusion>>,
    pub conclusions: HashMap<Rune, Conclusion>,
    pub unsolved_rules: Vec<Rule>,
    pub unsolved_runes: Vec<Rune>,
    pub error: ISolverError<Rune, Conclusion, ErrType>,
}
impl<Rule, Rune, Conclusion, ErrType> FailedSolve<Rule, Rune, Conclusion, ErrType>
where
    Rune: Eq + Hash,
{
}

#[derive(Clone, Debug, PartialEq)]
pub enum ISolverError<Rune, Conclusion, ErrType> {
  SolverConflict(SolverConflict<Rune, Conclusion, ErrType>),
  RuleError(RuleError<Rune, Conclusion, ErrType>),
  SolveIncomplete(SolveIncomplete<Rune, Conclusion, ErrType>),
}

#[derive(Clone, Debug, PartialEq)]
pub struct SolveIncomplete<Rune, Conclusion, ErrType> {
    pub _phantom: PhantomData<(Rune, Conclusion, ErrType)>,
}
#[derive(Clone, Debug, PartialEq)]
pub struct SolverConflict<Rune, Conclusion, ErrType> {
    pub rune: Rune,
    pub previous_conclusion: Conclusion,
    pub new_conclusion: Conclusion,
    pub _phantom: PhantomData<ErrType>,
}
impl<Rune, Conclusion, ErrType> SolverConflict<Rune, Conclusion, ErrType> {}

#[derive(Clone, Debug, PartialEq)]
pub struct RuleError<Rune, Conclusion, ErrType> {
    pub err: ErrType,
    pub _phantom: PhantomData<(Rune, Conclusion)>,
}
impl<Rune, Conclusion, ErrType> RuleError<Rune, Conclusion, ErrType> {}

pub fn make_solver_state<Rule, Rune, Conclusion>(
    sanity_check: bool,
    _use_optimized_solver: bool,
    rule_to_puzzles: Box<dyn Fn(&Rule) -> Vec<Vec<Rune>>>,
    rule_to_runes: &dyn Fn(&Rule) -> Vec<Rune>,
    initial_rules: Vec<Rule>,
    initially_known_runes: IndexMap<Rune, Conclusion>,
    all_runes: Vec<Rune>,
) -> SimpleSolverState<Rule, Rune, Conclusion>
where
    Rule: Clone,
    Rune: Clone + Hash + Eq,
    Conclusion: Clone + PartialEq,
{
    let mut solver_state = SimpleSolverState::new(rule_to_puzzles, all_runes.clone());

    if sanity_check {
        for rule in &initial_rules {
            for rune in rule_to_runes(rule) {
                assert!(all_runes.contains(&rune), "vassert: rune from rule must be in allRunes");
            }
        }
        for rune in initially_known_runes.keys() {
            assert!(all_runes.contains(rune), "vassert: known rune must be in allRunes");
        }
    }

    if sanity_check {
        solver_state.sanity_check();
    }

    // Matches Scala: solverState.commitStep(false, Vector(), initiallyKnownRunes, initialRules.toVector).getOrDie()
    match solver_state.commit_step::<Infallible>(
        false,
        vec![],
        initially_known_runes,
        initial_rules,
        IndexSet::new(),
    ) {
        Ok(()) => {},
        Err(_) => panic!("Initial commitStep should not fail"),
    }

    if sanity_check {
        solver_state.sanity_check();
    }

    solver_state
}

