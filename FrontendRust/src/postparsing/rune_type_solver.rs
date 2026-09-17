
use crate::postparsing::itemplatatype::{ITemplataType, CoordTemplataType, KindTemplataType};
use crate::postparsing::names::{IRuneS, IImpreciseNameS, IImpreciseNameValS, RuneNameValS};
use crate::postparsing::ast::GenericParameterS;
use crate::postparsing::rules::rules::{IRulexSR, RuneUsage};
use crate::scout_arena::ScoutArena;
use crate::solver::{FailedSolve, ISolverError, SimpleSolverState, SolveIncomplete, RuleError, make_solver_state};
use crate::utils::range::RangeS;
use crate::postparsing::itemplatatype::*;
use indexmap::{IndexMap, IndexSet};
use std::collections::HashMap;
use crate::postparsing::itemplatatype::ImplTemplataType;
use std::collections::HashSet;
use std::marker::PhantomData;

#[derive(Debug)]
pub struct RuneTypeSolveError<'s> {
  pub range: Vec<RangeS<'s>>,
  pub failed_solve: FailedSolve<IRulexSR<'s>, IRuneS<'s>, ITemplataType<'s>, IRuneTypeRuleError<'s>>,
}

#[derive(Debug)]
pub enum IRuneTypeRuleError<'s> {
  FoundCitizenDidntMatchExpectedType(FoundCitizenDidntMatchExpectedType<'s>),
  FoundTemplataDidntMatchExpectedType(FoundTemplataDidntMatchExpectedType<'s>),
  NotEnoughArgumentsForGenericCall(NotEnoughArgumentsForGenericCall<'s>),
  GenericCallArgTypeMismatch(GenericCallArgTypeMismatch<'s>),
  TooManyMatchingTypes(RuneTypingTooManyMatchingTypes<'s>),
  CouldntFindType(RuneTypingCouldntFindType<'s>),
}
impl<'s> IRuneTypeRuleError<'s> {
  pub fn as_lookup_failed(self) -> Option<IRuneTypingLookupFailedError<'s>> {
    match self {
      IRuneTypeRuleError::TooManyMatchingTypes(x) => Some(IRuneTypingLookupFailedError::TooManyMatchingTypes(x)),
      IRuneTypeRuleError::CouldntFindType(x) => Some(IRuneTypingLookupFailedError::CouldntFindType(x)),
      _ => None,
    }
  }
  
}

impl<'s> From<IRuneTypingLookupFailedError<'s>> for IRuneTypeRuleError<'s> {
  fn from(e: IRuneTypingLookupFailedError<'s>) -> Self {
    match e {
      IRuneTypingLookupFailedError::TooManyMatchingTypes(x) => IRuneTypeRuleError::TooManyMatchingTypes(x),
      IRuneTypingLookupFailedError::CouldntFindType(x) => IRuneTypeRuleError::CouldntFindType(x),
    }
  }
}

#[derive(Debug)]
pub struct FoundCitizenDidntMatchExpectedType<'s> {
  pub range: Vec<RangeS<'s>>,
  pub expected_type: ITemplataType<'s>,
  pub actual_type: ITemplataType<'s>,
}

#[derive(Debug)]
pub struct FoundTemplataDidntMatchExpectedType<'s> {
  pub range: Vec<RangeS<'s>>,
  pub expected_type: ITemplataType<'s>,
  pub actual_type: ITemplataType<'s>,
}

#[derive(Debug)]
pub struct NotEnoughArgumentsForGenericCall<'s> {
  pub range: Vec<RangeS<'s>>,
  pub index_of_non_defaulting_param: i32,
}

#[derive(Debug)]
pub struct GenericCallArgTypeMismatch<'s> {
  pub range: Vec<RangeS<'s>>,
  pub expected_type: ITemplataType<'s>,
  pub actual_type: ITemplataType<'s>,
  pub param_index: i32,
}

pub enum IRuneTypingLookupFailedError<'s> {
  TooManyMatchingTypes(RuneTypingTooManyMatchingTypes<'s>),
  CouldntFindType(RuneTypingCouldntFindType<'s>),
}

#[derive(Debug)]
pub struct RuneTypingTooManyMatchingTypes<'s> {
  pub range: RangeS<'s>,
  pub name: IImpreciseNameS<'s>,
}
impl<'s> RuneTypingTooManyMatchingTypes<'s> {

} // end impl RuneTypingTooManyMatchingTypes

#[derive(Debug)]
pub struct RuneTypingCouldntFindType<'s> {
  pub range: RangeS<'s>,
  pub name: IImpreciseNameS<'s>,
}
impl<'s> RuneTypingCouldntFindType<'s> {

} // end impl RuneTypingCouldntFindType

#[derive(Debug)]
pub struct FoundTemplataDidntMatchExpectedTypeA<'s> {
  pub range: Vec<RangeS<'s>>,
  pub expected_type: ITemplataType<'s>,
  pub actual_type: ITemplataType<'s>,
}

pub struct FoundPrimitiveDidntMatchExpectedType<'s> {
  pub range: Vec<RangeS<'s>>,
  pub expected_type: ITemplataType<'s>,
  pub actual_type: ITemplataType<'s>,
}

#[derive(PartialEq)]
pub enum IRuneTypeSolverLookupResult<'s> {
  Primitive(PrimitiveRuneTypeSolverLookupResult<'s>),
  Citizen(CitizenRuneTypeSolverLookupResult<'s>),
  Templata(TemplataLookupResult<'s>),
}

#[derive(PartialEq)]
pub struct PrimitiveRuneTypeSolverLookupResult<'s> {
  pub tyype: ITemplataType<'s>,
}

#[derive(PartialEq)]
pub struct CitizenRuneTypeSolverLookupResult<'s> {
  pub tyype: ITemplataType<'s>,
  pub generic_params: &'s [&'s GenericParameterS<'s>],
}

#[derive(PartialEq)]
pub struct TemplataLookupResult<'s> {
  pub templata: ITemplataType<'s>,
}

pub trait IRuneTypeSolverEnv<'s> {
  fn lookup(
    &self,
    range: RangeS<'s>,
    name: IImpreciseNameS<'s>,
  ) -> Result<IRuneTypeSolverLookupResult<'s>, IRuneTypingLookupFailedError<'s>>;
}

pub struct RuneTypeSolver<'s, 'ctx> {
  pub scout_arena: &'ctx ScoutArena<'s>,
}

impl<'s, 'ctx> RuneTypeSolver<'s, 'ctx> {
  pub fn solve_rune_type<E: IRuneTypeSolverEnv<'s>>(
    &self,
    sanity_check: bool,
    env: &E,
    range: Vec<RangeS<'s>>,
    predicting: bool,
    rules_s: &[IRulexSR<'s>],
    additional_runes: &[IRuneS<'s>],
    expect_complete_solve: bool,
    unpreprocessed_initially_known_runes: IndexMap<IRuneS<'s>, ITemplataType<'s>>,
  ) -> Result<
    IndexMap<IRuneS<'s>, ITemplataType<'s>>,
    RuneTypeSolveError<'s>,
  > {
    solve_rune_type(self.scout_arena, sanity_check, env, range, predicting, rules_s, additional_runes, expect_complete_solve, unpreprocessed_initially_known_runes)
  }
  
}
fn get_runes_rune_type<'s>(
  rule: &IRulexSR<'s>,
) -> Vec<IRuneS<'s>> {
  rule.rune_usages().iter().map(|ru| ru.rune.clone()).collect()
}

fn get_puzzles_rune_type<'s>(
  predicting: bool,
  rule: &IRulexSR<'s>,
) -> Vec<Vec<IRuneS<'s>>> {

  match rule {
    IRulexSR::Equals(x) => vec![vec![x.left.rune.clone()], vec![x.right.rune.clone()]],
    IRulexSR::Lookup(_x) => {
      if predicting {
        vec![]
      } else {
        vec![vec![]]
      }
    }
    IRulexSR::MaybeCoercingLookup(x) => {
      if predicting {
        vec![]
      } else {
        vec![vec![x.rune.rune.clone()]]
      }
    }
    IRulexSR::RuneParentEnvLookup(x) => {
      if predicting {
        vec![]
      } else {
        vec![vec![x.rune.rune.clone()]]
      }
    }
    IRulexSR::MaybeCoercingCall(x) => {
      vec![vec![x.result_rune.rune.clone(), x.template_rune.rune.clone()]]
    }
    IRulexSR::Pack(_) => vec![vec![]],
    IRulexSR::DefinitionCoordIsa(_) => vec![vec![]],
    IRulexSR::CallSiteCoordIsa(_) => vec![vec![]],
    IRulexSR::KindComponents(_) => vec![vec![]],
    IRulexSR::CoordComponents(_) => vec![vec![]],
    IRulexSR::PrototypeComponents(_) => vec![vec![]],
    IRulexSR::Resolve(_) => vec![vec![]],
    IRulexSR::CallSiteFunc(_) => vec![vec![]],
    IRulexSR::DefinitionFunc(_) => vec![vec![]],
    IRulexSR::OneOf(_) => vec![vec![]],
    IRulexSR::IsConcrete(x) => vec![vec![x.rune.rune.clone()]],
    IRulexSR::IsInterface(_) => vec![vec![]],
    IRulexSR::IsStruct(_) => vec![vec![]],
    IRulexSR::CoerceToCoord(_) => vec![vec![]],
    IRulexSR::Literal(_) => vec![vec![]],
    IRulexSR::Augment(_) => vec![vec![]],
    IRulexSR::RefListCompoundMutability(_) => vec![vec![]],
    IRulexSR::Call(_) => panic!("IRulexSR::Call not yet migrated in rune_type get_puzzles"),
    IRulexSR::CoordSend(_) => panic!("IRulexSR::CoordSend not yet migrated in rune_type get_puzzles"),
    IRulexSR::IndexList(_) => panic!("IRulexSR::IndexList not yet migrated in rune_type get_puzzles"),
  }
}

fn solve_rule<'s, E: IRuneTypeSolverEnv<'s>>(
  scout_arena: &ScoutArena<'s>,
  env: &E,
  rule_index: i32,
  rule: &IRulexSR<'s>,
  solver_state: &mut SimpleSolverState<
    IRulexSR<'s>,
    IRuneS<'s>,
    ITemplataType<'s>,
  >,
) -> Result<(), ISolverError<
  IRuneS<'s>,
  ITemplataType<'s>,
  IRuneTypeRuleError<'s>,
>> {

  match rule {
    IRulexSR::KindComponents(x) => {
      solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], [
        (x.kind_rune.rune.clone(), ITemplataType::KindTemplataType(KindTemplataType {})),
        (x.mutability_rune.rune.clone(), ITemplataType::MutabilityTemplataType(MutabilityTemplataType {})),
      ].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::CoordComponents(x) => {
      solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], [
        (x.result_rune.rune.clone(), ITemplataType::CoordTemplataType(CoordTemplataType {})),
        (x.ownership_rune.rune.clone(), ITemplataType::OwnershipTemplataType(OwnershipTemplataType {})),
        (x.kind_rune.rune.clone(), ITemplataType::KindTemplataType(KindTemplataType {})),
      ].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::PrototypeComponents(x) => {
      solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], [
        (x.result_rune.rune.clone(), ITemplataType::PrototypeTemplataType(PrototypeTemplataType {})),
        (x.params_rune.rune.clone(), ITemplataType::PackTemplataType(PackTemplataType { element_type: scout_arena.alloc(ITemplataType::CoordTemplataType(CoordTemplataType {})) })),
        (x.return_rune.rune.clone(), ITemplataType::CoordTemplataType(CoordTemplataType {})),
      ].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::MaybeCoercingCall(x) => {
      match solver_state.get_conclusion(&x.template_rune.rune).expect("MaybeCoercingCallSR: template rune has no conclusion") {
        ITemplataType::TemplateTemplataType(TemplateTemplataType { param_types, return_type: _ }) => {
          let conclusions: IndexMap<IRuneS<'s>, ITemplataType<'s>> = x.args.iter().map(|a| a.rune.clone()).zip(param_types.iter().cloned()).collect();
          solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], conclusions, vec![], IndexSet::new())
        }
        other => panic!("MaybeCoercingCallSR: unexpected template type: {:?}", other),
      }
    }
    IRulexSR::Resolve(x) => {
      solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], [
        (x.result_rune.rune.clone(), ITemplataType::PrototypeTemplataType(PrototypeTemplataType {})),
        (x.params_list_rune.rune.clone(), ITemplataType::PackTemplataType(PackTemplataType { element_type: scout_arena.alloc(ITemplataType::CoordTemplataType(CoordTemplataType {})) })),
        (x.return_rune.rune.clone(), ITemplataType::CoordTemplataType(CoordTemplataType {})),
      ].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::CallSiteFunc(x) => {
      solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], [
        (x.prototype_rune.rune.clone(), ITemplataType::PrototypeTemplataType(PrototypeTemplataType {})),
        (x.params_list_rune.rune.clone(), ITemplataType::PackTemplataType(PackTemplataType { element_type: scout_arena.alloc(ITemplataType::CoordTemplataType(CoordTemplataType {})) })),
        (x.return_rune.rune.clone(), ITemplataType::CoordTemplataType(CoordTemplataType {})),
      ].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::DefinitionFunc(x) => {
      solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], [
        (x.result_rune.rune.clone(), ITemplataType::PrototypeTemplataType(PrototypeTemplataType {})),
        (x.params_list_rune.rune.clone(), ITemplataType::PackTemplataType(PackTemplataType { element_type: scout_arena.alloc(ITemplataType::CoordTemplataType(CoordTemplataType {})) })),
        (x.return_rune.rune.clone(), ITemplataType::CoordTemplataType(CoordTemplataType {})),
      ].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::DefinitionCoordIsa(x) => {
        solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], [
            (x.result_rune.rune.clone(), ITemplataType::ImplTemplataType(ImplTemplataType {})),
            (x.sub_rune.rune.clone(), ITemplataType::CoordTemplataType(CoordTemplataType {})),
            (x.super_rune.rune.clone(), ITemplataType::CoordTemplataType(CoordTemplataType {})),
        ].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::CallSiteCoordIsa(x) => {
        let mut conclusions: IndexMap<IRuneS<'s>, ITemplataType<'s>> = [
            (x.sub_rune.rune.clone(), ITemplataType::CoordTemplataType(CoordTemplataType {})),
            (x.super_rune.rune.clone(), ITemplataType::CoordTemplataType(CoordTemplataType {})),
        ].into_iter().collect();
        if let Some(result_rune) = &x.result_rune {
            conclusions.insert(result_rune.rune.clone(), ITemplataType::ImplTemplataType(ImplTemplataType {}));
        }
        solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], conclusions, vec![], IndexSet::new())
    }
    IRulexSR::OneOf(x) => {
      let types: HashSet<ITemplataType<'s>> = x.literals.iter().map(|l| l.get_type()).collect();
      if types.len() > 1 {
        panic!("OneOf rule's possibilities must all be the same type!");
      }
      let the_type = types.into_iter().next().unwrap();
      solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], [(x.rune.rune.clone(), the_type)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::Equals(x) => {
      let left_conclusion = solver_state.get_conclusion(&x.left.rune);
      match left_conclusion {
        None => {
          let right_conclusion = solver_state.get_conclusion(&x.right.rune).expect("Neither side of EqualsSR has a conclusion");
          solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], [(x.left.rune.clone(), right_conclusion)].into_iter().collect(), vec![], IndexSet::new())
        }
        Some(left) => {
          solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], [(x.right.rune.clone(), left)].into_iter().collect(), vec![], IndexSet::new())
        }
      }
    }
    IRulexSR::IsConcrete(_) => panic!("IRulexSR::IsConcrete not yet migrated in rune_type solve_rule"),
    IRulexSR::IsInterface(x) => {
      solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], [(x.rune.rune.clone(), ITemplataType::KindTemplataType(KindTemplataType {}))].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::IsStruct(_) => panic!("IRulexSR::IsStruct not yet migrated in rune_type solve_rule"),
    IRulexSR::RefListCompoundMutability(_) => panic!("IRulexSR::RefListCompoundMutability not yet migrated in rune_type solve_rule"),
    IRulexSR::CoerceToCoord(x) => {
      solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], [
        (x.coord_rune.rune.clone(), ITemplataType::CoordTemplataType(CoordTemplataType {})),
        (x.kind_rune.rune.clone(), ITemplataType::KindTemplataType(KindTemplataType {})),
      ].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::Literal(x) => {
      solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], [(x.rune.rune.clone(), x.literal.get_type())].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::Lookup(x) => {
      let actual_lookup_result =
          match env.lookup(x.range.clone(), x.name.clone()) {
            Err(_e) => panic!("LookupSR solve error path not yet migrated"),
            Ok(r) => r,
          };
      let tyype = match actual_lookup_result {
        IRuneTypeSolverLookupResult::Primitive(p) => p.tyype,
        IRuneTypeSolverLookupResult::Templata(t) => t.templata,
        IRuneTypeSolverLookupResult::Citizen(c) => c.tyype,
      };
      solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], [(x.rune.rune.clone(), tyype)].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::MaybeCoercingLookup(x) => {
      let actual_lookup_result =
          match env.lookup(x.range.clone(), x.name.clone()) {
            Err(_e) => panic!("MaybeCoercingLookupSR solve error path not yet migrated"),
            Ok(r) => r,
          };
      // AFTERM: lookup_rune_type only validates, doesn't conclude runes. Need to add commitStep here.
      lookup_rune_type(env, solver_state, x.range.clone(), &x.rune, actual_lookup_result)?;
      solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], IndexMap::new(), vec![], IndexSet::new())
    }
    IRulexSR::RuneParentEnvLookup(x) => {
      let lookup_name = scout_arena.intern_imprecise_name(IImpreciseNameValS::RuneName(RuneNameValS { rune: x.rune.rune.clone() }));
      let actual_lookup_result =
          match env.lookup(x.range.clone(), lookup_name) {
            Err(_e) => panic!("RuneParentEnvLookupSR solve error path not yet migrated"),
            Ok(r) => r,
          };
      lookup_rune_type(env, solver_state, x.range.clone(), &x.rune, actual_lookup_result)?;
      solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], IndexMap::new(), vec![], IndexSet::new())
    }
    IRulexSR::Augment(x) => {
      solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], [
        (x.result_rune.rune.clone(), ITemplataType::CoordTemplataType(CoordTemplataType {})),
        (x.inner_rune.rune.clone(), ITemplataType::CoordTemplataType(CoordTemplataType {})),
      ].into_iter().collect(), vec![], IndexSet::new())
    }
    IRulexSR::Pack(x) => {
      let mut conclusions: IndexMap<IRuneS<'s>, ITemplataType<'s>> = x.members.iter()
        .map(|m| (m.rune.clone(), ITemplataType::CoordTemplataType(CoordTemplataType {})))
        .collect();
      conclusions.insert(x.result_rune.rune.clone(), ITemplataType::PackTemplataType(PackTemplataType { element_type: scout_arena.alloc(ITemplataType::CoordTemplataType(CoordTemplataType {})) }));
      solver_state.commit_step::<IRuneTypeRuleError<'s>>(false, vec![rule_index], conclusions, vec![], IndexSet::new())
    }
    IRulexSR::CoordSend(_) => panic!("IRulexSR::CoordSend not yet migrated in rune_type solve_rule"),
    IRulexSR::Call(_) => panic!("IRulexSR::Call not yet migrated in rune_type solve_rule"),
    IRulexSR::IndexList(_) => panic!("IRulexSR::IndexList not yet migrated in rune_type solve_rule"),
  }
}

fn lookup_rune_type<'s, E: IRuneTypeSolverEnv<'s>>(
  _env: &E,
  solver_state: &mut SimpleSolverState<
    IRulexSR<'s>,
    IRuneS<'s>,
    ITemplataType<'s>,
  >,
  _range: RangeS<'s>,
  rune: &RuneUsage<'s>,
  actual_lookup_result: IRuneTypeSolverLookupResult<'s>,
) -> Result<(), ISolverError<
  IRuneS<'s>,
  ITemplataType<'s>,
  IRuneTypeRuleError<'s>,
>> {
  let expected_type = solver_state.get_conclusion(&rune.rune).expect("lookup_rune_type: no conclusion for rune");
  match actual_lookup_result {
    IRuneTypeSolverLookupResult::Primitive(p) => {
      match &expected_type {
        ITemplataType::CoordTemplataType(_) | ITemplataType::KindTemplataType(_) => {}
        x if *x == p.tyype => {}
        _ => panic!("lookup_rune_type Primitive error path not yet migrated"),
      }
    }
    IRuneTypeSolverLookupResult::Templata(t) => {
      let actual_type = t.templata;
      match (&actual_type, &expected_type) {
        (x, y) if x == y => {} // Matches, so is fine
        (ITemplataType::KindTemplataType(_), ITemplataType::CoordTemplataType(_)) => {} // Will convert, so is fine
        (ITemplataType::TemplateTemplataType(tt), ITemplataType::CoordTemplataType(_) | ITemplataType::KindTemplataType(_))
            if tt.param_types.is_empty()
                && matches!(tt.return_type, ITemplataType::KindTemplataType(_) | ITemplataType::CoordTemplataType(_)) => {
          // Then it's an implicit call.
          match check_generic_call(vec![_range.clone()], &[], &[]) {
            Ok(()) => {},
            Err(e) => return Err(ISolverError::RuleError(RuleError { err: e, _phantom: PhantomData })),
          }
        }
        _ => panic!("lookup_rune_type Templata FoundTemplataDidntMatchExpectedType not yet migrated"),
      }
    }
    IRuneTypeSolverLookupResult::Citizen(c) => {
      match &expected_type {
        ITemplataType::CoordTemplataType(_) | ITemplataType::KindTemplataType(_) => {
          // Then it's an implicit call, straight from being looked up.
          match check_generic_call(vec![_range.clone()], &c.generic_params, &[]) {
            Ok(()) => {},
            Err(e) => return Err(ISolverError::RuleError(RuleError { err: e, _phantom: PhantomData })),
          }
        }
        x if *x == c.tyype => {}
        _ => panic!("lookup_rune_type Citizen error path not yet migrated"),
      }
    }
  }
  Ok(())
}

pub fn solve_rune_type<'s, E: IRuneTypeSolverEnv<'s>>(
  // V: we took out self here, do we have a coherent story about when something should be self/impl'd
  // VA: In Scala, solveRuneType was a method on class RuneTypeSolver(interner). In Rust, it was
  // VA: extracted to a free function while RuneTypeSolver exists as a thin delegating wrapper.
  // VA: The dominant pattern across postparsing solvers is free functions: identifiability_solver.rs
  // VA: and rule_scout.rs are entirely free functions with no struct. The RuneTypeSolver struct is
  // VA: the exception — it could be removed to match the peer files, or the free functions could be
  // VA: moved into it to match Scala's class structure. Currently inconsistent.
  scout_arena: &ScoutArena<'s>,
  sanity_check: bool,
  env: &E,
  range: Vec<RangeS<'s>>,
  predicting: bool,
  rules_s: &[IRulexSR<'s>],
  additional_runes: &[IRuneS<'s>],
  expect_complete_solve: bool,
  unpreprocessed_initially_known_runes: IndexMap<IRuneS<'s>, ITemplataType<'s>>,
) -> Result<
  IndexMap<IRuneS<'s>, ITemplataType<'s>>,
  RuneTypeSolveError<'s>,
> {

  // For the non-predicting case, iterate over LookupSR/MaybeCoercingLookupSR rules and pre-compute types via env.lookup.
  // For now, with no rules in the simple test case, this is empty.
  let mut initially_known_runes: IndexMap<IRuneS<'s>, ITemplataType<'s>> = if predicting {
    IndexMap::new()
  } else {
    let mut map = IndexMap::new();
    for rule in rules_s {
      match rule {
        IRulexSR::Lookup(lookup) => {
          match env.lookup(lookup.range.clone(), lookup.name.clone()) {
            Err(_e) => {
              panic!("LookupSR pre-computation error path not yet migrated");
            }
            Ok(result) => {
              let entries: Vec<(IRuneS<'s>, ITemplataType)> = match &result {
                // We don't know whether we'll coerce this into a kind or a coord.
                IRuneTypeSolverLookupResult::Primitive(p) => {
                  match &p.tyype {
                    ITemplataType::KindTemplataType(_) => vec![],
                    ITemplataType::TemplateTemplataType(t) if t.param_types.is_empty() => vec![],
                    other => vec![(lookup.rune.rune.clone(), other.clone())],
                  }
                }
                IRuneTypeSolverLookupResult::Citizen(c) => {
                  match &c.tyype {
                    ITemplataType::TemplateTemplataType(t) if t.param_types.is_empty() && matches!(&*t.return_type, ITemplataType::KindTemplataType(_)) => vec![],
                    other => vec![(lookup.rune.rune.clone(), other.clone())],
                  }
                }
                IRuneTypeSolverLookupResult::Templata(t) => {
                  match &t.templata {
                    ITemplataType::TemplateTemplataType(tt) if tt.param_types.is_empty() && matches!(&*tt.return_type, ITemplataType::KindTemplataType(_)) => vec![],
                    ITemplataType::KindTemplataType(_) => vec![],
                    other => vec![(lookup.rune.rune.clone(), other.clone())],
                  }
                }
              };
              for (k, v) in entries {
                map.insert(k, v);
              }
            }
          }
        }
        IRulexSR::MaybeCoercingLookup(lookup) => {
          match env.lookup(lookup.range.clone(), lookup.name.clone()) {
            Err(e) => {
              return Err(RuneTypeSolveError {
                range: vec![lookup.range.clone()],
                failed_solve: FailedSolve {
                    steps: vec![],
                    conclusions: HashMap::new(),
                    unsolved_rules: rules_s.to_vec(),
                    unsolved_runes: vec![],
                    error: ISolverError::RuleError(
                      RuleError {
                        err: e.into(),
                        _phantom: PhantomData,
                      }
                    ),
                },
              });
            }
            Ok(result) => {
              let entries: Vec<(IRuneS<'s>, ITemplataType)> = match &result {
                // We don't know whether we'll coerce this into a kind or a coord.
                IRuneTypeSolverLookupResult::Primitive(p) => {
                  match &p.tyype {
                    ITemplataType::KindTemplataType(_) => vec![],
                    ITemplataType::TemplateTemplataType(t) if t.param_types.is_empty() => vec![],
                    other => vec![(lookup.rune.rune.clone(), other.clone())],
                  }
                }
                IRuneTypeSolverLookupResult::Citizen(c) => {
                  match &c.tyype {
                    ITemplataType::TemplateTemplataType(t) if t.param_types.is_empty() && matches!(&*t.return_type, ITemplataType::KindTemplataType(_)) => vec![],
                    other => vec![(lookup.rune.rune.clone(), other.clone())],
                  }
                }
                IRuneTypeSolverLookupResult::Templata(t) => {
                  match &t.templata {
                    ITemplataType::TemplateTemplataType(tt) if tt.param_types.is_empty() && matches!(&*tt.return_type, ITemplataType::KindTemplataType(_)) => vec![],
                    ITemplataType::KindTemplataType(_) => vec![],
                    other => vec![(lookup.rune.rune.clone(), other.clone())],
                  }
                }
              };
              for (k, v) in entries {
                map.insert(k, v);
              }
            }
          }
        }
        _ => {
          // Other rules don't contribute to initially known runes
        }
      }
    }
    map
  };
  // unpreprocessedInitiallyKnownRunes comes after (takes priority, see Scala comment)
  for (k, v) in unpreprocessed_initially_known_runes {
    initially_known_runes.insert(k, v);
  }

  // Compute all_runes for solver = rules.flatMap(getRunes) ++ initiallyKnownRunes.keys, deduplicated
  // (additionalRunes are NOT included here — they're added after solving for the completeness check)
  let mut all_runes_set: indexmap::IndexSet<IRuneS<'s>> = indexmap::IndexSet::new();
  for rule in rules_s {
    for rune_usage in rule.rune_usages() {
      all_runes_set.insert(rune_usage.rune.clone());
    }
  }
  for k in initially_known_runes.keys() {
    all_runes_set.insert(k.clone());
  }
  let solver_runes: Vec<IRuneS<'s>> = all_runes_set.into_iter().collect();

  let predicting_copy = predicting;
  let mut solver_state = make_solver_state(
    sanity_check,
    false,
    Box::new(move |rule: &IRulexSR<'s>| get_puzzles_rune_type(predicting_copy, rule)),
    &get_runes_rune_type,
    rules_s.to_vec(),
    initially_known_runes,
    solver_runes,
  );

  // Inline advance loop (matches Scala's while loop in solve())
  loop {
    solver_state.sanity_check();
    solver_state.userify_conclusions().iter().for_each(|(rune, conclusion)| {
      sanity_check_conclusion(rune.clone(), conclusion);
    });
    // Stage 1: simple solve
    match solver_state.get_next_solvable() {
      None => break, // No more solvable rules
      Some(rule_index) => {
        let rule = solver_state.get_rule(rule_index).clone();
        let steps_before = solver_state.get_steps().len();
        match solve_rule(scout_arena, env, rule_index, &rule, &mut solver_state) {
          Ok(()) => {}
          Err(e) => {
            return Err(RuneTypeSolveError {
              range,
              failed_solve: FailedSolve {
                steps: solver_state.get_steps(),
                conclusions: solver_state.get_conclusions().into_iter().collect(),
                unsolved_rules: solver_state.get_unsolved_rules(),
                unsolved_runes: solver_state.get_unsolved_runes(),
                error: e,
              },
            });
          }
        }
        let steps_after = solver_state.get_steps().len();
        assert!(steps_after == steps_before + 1);
        assert!(solver_state.rule_is_solved(rule_index));
        solver_state.sanity_check();
      }
    }
  }

  let conclusions: IndexMap<IRuneS<'s>, ITemplataType<'s>> = solver_state.userify_conclusions().into_iter().collect();
  let unsolved_runes = solver_state.get_unsolved_runes();

  if expect_complete_solve && !unsolved_runes.is_empty() {
    let steps = solver_state.get_steps();
    let unsolved_rules = solver_state.get_unsolved_rules();
    Err(RuneTypeSolveError {
      range,
      failed_solve: FailedSolve {
          steps,
          conclusions: conclusions.clone().into_iter().collect(),
          unsolved_rules,
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

fn sanity_check_conclusion<'s>(
  _rune: IRuneS<'s>,
  _conclusion: &ITemplataType<'s>,
) {
}

fn complex_solve() -> Result<(), ()> {
  panic!("Unimplemented complex_solve");
}

fn solve<'s>(
  _state: (),
  _env: (),
  _solver_state: (),
  _rule_index: usize,
  _rule: &IRulexSR<'s>,
  _step_state: (),
) -> Result<(), ()> {
  panic!("Unimplemented solve");
}

fn check_generic_call_without_defaults<'s>(
  _param_types: &[ITemplataType<'s>],
  _arg_types: &[ITemplataType<'s>],
) -> Result<(), ()> {
  panic!("Unimplemented check_generic_call_without_defaults");
}

fn check_generic_call<'s>(
  range: Vec<RangeS<'s>>,
  citizen_generic_params: &[&GenericParameterS<'s>],
  arg_types: &[ITemplataType<'s>],
) -> Result<(), IRuneTypeRuleError<'s>> {
  for (index, generic_param) in citizen_generic_params.iter().enumerate() {
    if index < arg_types.len() {
      let actual_type = &arg_types[index];
      if generic_param.tyype.tyype() == *actual_type {
        // Matches, proceed.
      } else {
        return Err(IRuneTypeRuleError::GenericCallArgTypeMismatch(GenericCallArgTypeMismatch {
          range: range.clone(),
          expected_type: generic_param.tyype.tyype(),
          actual_type: actual_type.clone(),
          param_index: index as i32,
        }));
      }
    } else {
      if generic_param.default.is_some() {
        // Good, can just use that default
      } else {
        return Err(IRuneTypeRuleError::NotEnoughArgumentsForGenericCall(NotEnoughArgumentsForGenericCall {
          range: range.clone(),
          index_of_non_defaulting_param: index as i32,
        }));
      }
    }
  }

  Ok(())
}
