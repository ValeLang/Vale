
use super::test_rules::*;
use crate::solver::{ISolverError, RuleError, SimpleSolverState};
use crate::scout_arena::ScoutArena;
use indexmap::{IndexMap, IndexSet};
use std::collections::HashMap;
use std::collections::HashSet;
use std::marker::PhantomData;

pub struct TestRuleSolver<'ctx, 's> {
    pub scout_arena: &'ctx ScoutArena<'s>,
}

impl<'ctx, 's> TestRuleSolver<'ctx, 's> {

pub fn complex_solve_impl(
    &self,
    _state: &(),
    _env: &(),
    solver_state: &mut SimpleSolverState<TestRule, i64, String>,
) -> Result<(), ISolverError<i64, String, String>> {
    let unsolved_rules = solver_state.get_unsolved_rules();
    let receiver_runes: Vec<i64> = {
        let mut v: Vec<i64> = unsolved_rules
            .iter()
            .filter_map(|r| {
                if let TestRule::Send(Send { receiver_rune, .. }) = r {
                    Some(*receiver_rune)
                } else {
                    None
                }
            })
            .collect();
        v.sort();
        v.dedup();
        v
    };
    let mut new_conclusions: IndexMap<i64, String> = IndexMap::new();
    for receiver in receiver_runes {
        let receive_rules: Vec<&TestRule> = unsolved_rules
            .iter()
            .filter(|r| {
                if let TestRule::Send(Send { receiver_rune, .. }) = r {
                    *receiver_rune == receiver
                } else {
                    false
                }
            })
            .collect();
        let call_rules: Vec<&TestRule> = unsolved_rules
            .iter()
            .filter(|r| {
                if let TestRule::Call(Call { result_rune, .. }) = r {
                    *result_rune == receiver
                } else {
                    false
                }
            })
            .collect();
        let sender_conclusions: Vec<String> = receive_rules
            .iter()
            .filter_map(|r| {
                if let TestRule::Send(s) = r {
                    solver_state.get_conclusion(&s.sender_rune)
                } else {
                    None
                }
            })
            .collect();
        let call_templates: Vec<String> = call_rules
            .iter()
            .filter_map(|r| {
                if let TestRule::Call(c) = r {
                    solver_state.get_conclusion(&c.name_rune)
                } else {
                    None
                }
            })
            .collect();
        let any_unknown_constraints = sender_conclusions.len() != receive_rules.len()
            || call_rules.len() != call_templates.len();
        if let Some(receiver_instantiation) = self.solve_receives(
            sender_conclusions,
            call_templates,
            any_unknown_constraints,
        )
        {
            new_conclusions.insert(receiver, receiver_instantiation);
        }
    }
    // Complex solve only produces conclusions, not solved/new rules.
    solver_state.commit_step::<String>(true, vec![], new_conclusions, vec![], IndexSet::new())?;
    Ok(())
}

pub fn solve_impl(
  &self,
  _state: &(),
  _env: &(),
  solver_state: &mut SimpleSolverState<TestRule, i64, String>,
  rule_index: i32,
  rule: &TestRule,
) -> Result<(), ISolverError<i64, String, String>> {
    match rule {
        TestRule::Equals(Equals { left_rune, right_rune }) => {
            match solver_state.get_conclusion(left_rune) {
                Some(left) => {
                    solver_state.commit_step::<String>(false, vec![rule_index],[(*right_rune, left)].into_iter().collect(), vec![], IndexSet::new())
                }
                None => {
                    let right = solver_state
                        .get_conclusion(right_rune)
                        .expect("right rune must have conclusion");
                    solver_state.commit_step::<String>(false, vec![rule_index],[(*left_rune, right)].into_iter().collect(), vec![], IndexSet::new())
                }
            }
        }
        TestRule::Lookup(Lookup { rune, name }) => {
            solver_state.commit_step::<String>(false, vec![rule_index],[(*rune, name.clone())].into_iter().collect(), vec![], IndexSet::new())
        }
        TestRule::Literal(Literal { rune, value }) => {
            solver_state.commit_step::<String>(false, vec![rule_index],[(*rune, value.clone())].into_iter().collect(), vec![], IndexSet::new())
        }
        TestRule::OneOf(OneOf { coord_rune, possible_values }) => {
            let literal = solver_state
                .get_conclusion(coord_rune)
                .expect("OneOf rune must have conclusion");
            if !possible_values.contains(&literal) {
                return Err(ISolverError::RuleError(RuleError {
                    err: "conflict!".to_string(),
                    _phantom: PhantomData,
                }));
            }
            solver_state.commit_step::<String>(false, vec![rule_index],IndexMap::new(), vec![], IndexSet::new())
        }
        TestRule::CoordComponents(CoordComponents {
            coord_rune,
            ownership_rune,
            kind_rune,
        }) => {
            match solver_state.get_conclusion(coord_rune) {
                Some(combined) => {
                    let parts: Vec<&str> = combined.split('/').collect();
                    let (ownership, kind) = (parts[0].to_string(), parts[1].to_string());
                    solver_state.commit_step::<String>(false, vec![rule_index],[(*ownership_rune, ownership), (*kind_rune, kind)].into_iter().collect(), vec![], IndexSet::new())
                }
                None => {
                    let ownership = solver_state
                        .get_conclusion(ownership_rune)
                        .expect("ownership required");
                    let kind = solver_state
                        .get_conclusion(kind_rune)
                        .expect("kind required");
                    let combined = format!("{}/{}", ownership, kind);
                    solver_state.commit_step::<String>(false, vec![rule_index],[(*coord_rune, combined)].into_iter().collect(), vec![], IndexSet::new())
                }
            }
        }
        TestRule::Pack(Pack {
            result_rune,
            member_runes,
        }) => {
            match solver_state.get_conclusion(result_rune) {
                Some(result) => {
                    let parts: Vec<&str> = result.split(',').collect();
                    let conclusions: IndexMap<i64, String> = member_runes.iter().zip(parts.iter())
                        .map(|(rune, part)| (*rune, (*part).to_string()))
                        .collect();
                    solver_state.commit_step::<String>(false, vec![rule_index],conclusions, vec![], IndexSet::new())
                }
                None => {
                    let result: String = member_runes
                        .iter()
                        .map(|r| {
                            solver_state
                                .get_conclusion(r)
                                .expect("member rune must have conclusion")
                        })
                        .collect::<Vec<_>>()
                        .join(",");
                    solver_state.commit_step::<String>(false, vec![rule_index],[(*result_rune, result)].into_iter().collect(), vec![], IndexSet::new())
                }
            }
        }
        TestRule::Call(Call {
            result_rune,
            name_rune,
            arg_rune,
        }) => {
            let maybe_result = solver_state.get_conclusion(result_rune);
            let maybe_name = solver_state.get_conclusion(name_rune);
            let maybe_arg = solver_state.get_conclusion(arg_rune);
            match (maybe_result, maybe_name, maybe_arg) {
                (Some(result), Some(template_name), _) => {
                    let prefix = format!("{}:", template_name);
                    assert!(result.starts_with(&prefix));
                    let arg = result[prefix.len()..].to_string();
                    solver_state.commit_step::<String>(false, vec![rule_index],[(*arg_rune, arg)].into_iter().collect(), vec![], IndexSet::new())
                }
                (_, Some(template_name), Some(arg)) => {
                    let result = format!("{}:{}", template_name, arg);
                    solver_state.commit_step::<String>(false, vec![rule_index],[(*result_rune, result)].into_iter().collect(), vec![], IndexSet::new())
                }
                _ => panic!("Call rule needs name+arg or result+name"),
            }
        }
        TestRule::Send(Send {
            sender_rune,
            receiver_rune,
        }) => {
            let receiver = solver_state
                .get_conclusion(receiver_rune)
                .expect("receiver must have conclusion");
            if receiver == "ISpaceship" || receiver == "IWeapon:int" {
                let new_rule = TestRule::Implements(Implements {
                    sub_rune: *sender_rune,
                    super_rune: *receiver_rune,
                });
                solver_state.commit_step::<String>(false, vec![rule_index],IndexMap::new(), vec![new_rule], IndexSet::new())
            } else {
                solver_state.commit_step::<String>(false, vec![rule_index],[(*sender_rune, receiver)].into_iter().collect(), vec![], IndexSet::new())
            }
        }
        TestRule::Implements(Implements { sub_rune, super_rune }) => {
            let sub = solver_state
                .get_conclusion(sub_rune)
                .expect("sub must have conclusion");
            let suuper = solver_state
                .get_conclusion(super_rune)
                .expect("super must have conclusion");
            match (sub.as_str(), suuper.as_str()) {
                (x, y) if x == y => {},
                ("Firefly", "ISpaceship") => {},
                ("Serenity", "ISpaceship") => {},
                ("Flamethrower:int", "IWeapon:int") => {},
                _ => panic!("Unimplemented Implements case: {} -> {}", sub, suuper),
            }
            solver_state.commit_step::<String>(false, vec![rule_index],IndexMap::new(), vec![], IndexSet::new())
        }
    }
}

fn instantiate_ancestor_template(&self, descendants: Vec<String>, ancestor_template: &str) -> String {
    let descendant = descendants.first().expect("descendants non-empty");
    match (descendant.as_str(), ancestor_template) {
        (x, y) if x == y => descendant.clone(),
        (x, y) if !x.contains(':') => y.to_string(),
        ("Flamethrower:int", "IWeapon") => "IWeapon:int".to_string(),
        ("Rockets:int", "IWeapon") => "IWeapon:int".to_string(),
        other => panic!("Unimplemented instantiate_ancestor_template: {:?}", other),
    }
}

fn get_ancestors(&self, descendant: &str, include_self: bool) -> Vec<String> {
    let self_and_ancestors: Vec<String> = match self.get_template(descendant).as_str() {
        "Firefly" => vec!["ISpaceship".to_string()],
        "Serenity" => vec!["ISpaceship".to_string()],
        "ISpaceship" => vec![],
        "Flamethrower" => vec!["IWeapon".to_string()],
        "Rockets" => vec!["IWeapon".to_string()],
        "IWeapon" => vec![],
        "int" => vec![],
        other => panic!("Unimplemented get_ancestors: {}", other),
    };
    let mut result = self_and_ancestors;
    if include_self {
        result.push(descendant.to_string());
    }
    result
}

fn get_template(&self, tyype: &str) -> String {
    if tyype.contains(':') {
        tyype.split(':').next().unwrap_or(tyype).to_string()
    } else {
        tyype.to_string()
    }
}

fn solve_receives(
    &self,
    senders: Vec<String>,
    call_templates: Vec<String>,
    any_unknown_constraints: bool,
) -> Option<String> {
    let sender_templates: Vec<String> = senders.iter().map(|s| self.get_template(s)).collect();
    assert!(call_templates.iter().collect::<HashSet<_>>().len() <= 1);
    let sender_ancestor_lists: Vec<Vec<String>> = sender_templates
        .iter()
        .map(|s| self.get_ancestors(s, true))
        .collect();
    let common_ancestors: HashSet<String> = sender_ancestor_lists
        .iter()
        .fold(None, |acc: Option<HashSet<String>>, list| {
            let set: HashSet<String> = list.iter().cloned().collect();
            Some(match acc {
                None => set,
                Some(a) => a.intersection(&set).cloned().collect(),
            })
        })
        .unwrap_or_default();
    let call_templates_set: HashSet<String> =
        call_templates.iter().cloned().collect();
    let common_ancestors_call_constrained = if call_templates_set.is_empty() {
        common_ancestors
    } else {
        common_ancestors
            .intersection(&call_templates_set)
            .cloned()
            .collect()
    };
    let common_ancestors_narrowed =
        self.narrow(common_ancestors_call_constrained, any_unknown_constraints);
    if common_ancestors_narrowed.is_empty() {
        None
    } else {
        let ancestor_template = common_ancestors_narrowed
            .iter()
            .next()
            .expect("non-empty");
        Some(self.instantiate_ancestor_template(senders, ancestor_template))
    }
}

fn narrow(
    &self,
    ancestor_template_unnarrowed: HashSet<String>,
    any_unknown_constraints: bool,
) -> HashSet<String> {
    let ancestor_template = if ancestor_template_unnarrowed.len() > 1 {
        if any_unknown_constraints {
            panic!("narrow: any_unknown_constraints with multiple ancestors");
        } else {
            ancestor_template_unnarrowed
                .into_iter()
                .filter(|x| *x != "ISpaceship" && *x != "IWeapon")
                .collect()
        }
    } else {
        ancestor_template_unnarrowed
    };
    assert!(ancestor_template.len() <= 1);
    ancestor_template
}

}

pub fn rule_to_puzzles(rule: &TestRule) -> Vec<Vec<i64>> {
    rule.all_puzzles()
}
