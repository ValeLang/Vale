
use crate::utils::range::{CodeLocationS, RangeS};
use crate::solver::solver::ISolverError;
use crate::solver::solver::FailedSolve;
use std::cmp::max;
use std::collections::HashMap;
use std::collections::HashSet;
use std::hash::Hash;

pub fn humanize_failed_solve<'a, Rule, RuneId, Conclusion, ErrType>(
  code_map: impl Fn(&CodeLocationS<'a>) -> String,
  lines_between: impl Fn(&CodeLocationS<'a>, &CodeLocationS<'a>) -> Vec<RangeS<'a>>,
  line_range_containing: impl Fn(&CodeLocationS<'a>) -> RangeS<'a>,
  line_containing: impl Fn(&CodeLocationS<'a>) -> String,
  humanize_rune: impl Fn(RuneId) -> String,
  humanize_conclusion: impl Fn(Conclusion) -> String,
  humanize_rule_error: impl Fn(&ErrType) -> String,
  get_rule_range: impl Fn(&Rule) -> RangeS<'a>,
  get_rune_usages: impl Fn(&Rule) -> Vec<(RuneId, RangeS<'a>)>,
  rule_to_runes: impl Fn(&Rule) -> Vec<RuneId>,
  rule_to_string: impl Fn(&Rule) -> String,
  result: &FailedSolve<Rule, RuneId, Conclusion, ErrType>,
) -> (String, Vec<CodeLocationS<'a>>)
where
  RuneId: Eq + Hash + Copy,
  Conclusion: Copy,
  CodeLocationS<'a>: PartialEq + Copy,
  RangeS<'a>: PartialEq + Copy,
{
  let error_body = match &result.error {
    ISolverError::SolverConflict(_c) => panic!("implement: humanize_failed_solve SolverConflict"),
    ISolverError::SolveIncomplete(_) => {
      let names: Vec<String> = result.unsolved_runes.iter().map(|r| humanize_rune(*r)).collect();
      format!("Couldn't solve some runes: {}", names.join(", "))
    }
    ISolverError::RuleError(rule_err) => humanize_rule_error(&rule_err.err),
  };
  let _verbose = true;
  let rules_to_summarize: Vec<&Rule> = result.unsolved_rules.iter()
    .filter(|rule| !get_rule_range(rule).file().is_internal())
    .collect();
  let all_line_begin_locs: Vec<CodeLocationS<'a>> = {
    let raw: Vec<CodeLocationS<'a>> = rules_to_summarize.iter().flat_map(|rule| {
      let range = get_rule_range(rule);
      lines_between(&range.begin, &range.end).into_iter().map(|r| r.begin)
    }).collect();
    let mut distinct = Vec::<CodeLocationS<'a>>::new();
    for item in raw { if !distinct.contains(&item) { distinct.push(item); } }
    distinct
  };
  let all_rune_usages: Vec<(RuneId, RangeS<'a>)> = {
    let raw: Vec<(RuneId, RangeS<'a>)> = rules_to_summarize.iter()
      .flat_map(|rule| get_rune_usages(rule))
      .collect();
    let mut distinct = Vec::<(RuneId, RangeS<'a>)>::new();
    for item in raw { if !distinct.contains(&item) { distinct.push(item); } }
    distinct
  };
  let line_begin_loc_to_rune_usage: HashMap<i32, Vec<(RuneId, RangeS<'a>)>> = {
    let mut map: HashMap<i32, Vec<(RuneId, RangeS<'a>)>> = HashMap::new();
    for rune_usage in &all_rune_usages {
      let usage_begin_line = line_range_containing(&rune_usage.1.begin).begin.offset;
      map.entry(usage_begin_line).or_default().push(*rune_usage);
    }
    map
  };
  let incomplete_conclusions: HashMap<RuneId, Conclusion> = result.steps.iter()
    .flat_map(|step| step.conclusions.iter().map(|(r, c)| (*r, *c)))
    .collect();
  let text_from_user_rules: String = {
    let mut sorted_locs = all_line_begin_locs.clone();
    sorted_locs.sort_by_key(|loc| loc.offset);
    sorted_locs.iter().map(|loc| {
      let line = line_containing(loc);
      let rune_lines: String = line_begin_loc_to_rune_usage.get(&loc.offset).map(|usages| {
        let mut sorted_usages = usages.clone();
        sorted_usages.sort_by_key(|u| -u.1.begin.offset);
        sorted_usages.iter().map(|(rune, range)| {
          let num_spaces = range.begin.offset - loc.offset;
          let num_arrows = max(range.end.offset - range.begin.offset, 1);
          let rune_name = humanize_rune(*rune);
          let conclusion_str = incomplete_conclusions.get(rune)
            .map(|c| humanize_conclusion(*c))
            .unwrap_or_else(|| "(unknown)".to_string());
          format!("{}{} {}: {}\n",
            " ".repeat(num_spaces as usize),
            "^".repeat(num_arrows as usize),
            rune_name,
            conclusion_str)
        }).collect()
      }).unwrap_or_default();
      format!("{}\n{}", line, rune_lines)
    }).collect()
  };
  let text_from_steps: String = {
    let fold_result = result.steps.iter().fold(
      ("".to_string(), HashSet::<RuneId>::new()),
      |(string_so_far, previously_printed), step| {
        let new_string = format!("{}{}{}{}{}",
          if !step.complex && step.solved_rules.is_empty() { "Supplied:" } else { "" },
          if step.complex { if step.solved_rules.is_empty() { "(complex)" } else { "(complex)  " } } else { "" },
          step.solved_rules.iter().map(|(_, r)| rule_to_string(r)).collect::<Vec<_>>().join("  ") + "\n",
          {
            let mut entries: Vec<(&RuneId, &Conclusion)> = step.conclusions.iter()
              .filter(|(rune, _)| !previously_printed.contains(rune))
              .collect();
            entries.sort_by_key(|(rune, _)| humanize_rune(**rune));
            entries.iter()
              .map(|(rune, conclusion)| format!("  {}: {}\n", humanize_rune(**rune), humanize_conclusion(**conclusion)))
              .collect::<String>()
          },
          step.added_rules.iter()
            .map(|r| format!("  added rule: {}\n", rule_to_string(r)))
            .collect::<String>(),
        );
        let mut new_printed = previously_printed;
        new_printed.extend(step.conclusions.keys().copied());
        (string_so_far + &new_string, new_printed)
      }
    );
    let unsolved_rules_str: String = result.unsolved_rules.iter()
      .map(|r| format!("Unsolved rule: {}\n", rule_to_string(r)))
      .collect();
    let unsolved_runes_str = if !result.unsolved_runes.is_empty() {
      let names: Vec<String> = result.unsolved_runes.iter().map(|r| humanize_rune(*r)).collect();
      format!("Unsolved runes: {}", names.join(" "))
    } else {
      "".to_string()
    };
    format!("Steps:\n{}{}{}", fold_result.0, unsolved_rules_str, unsolved_runes_str)
  };
  let text = format!("{}\n{}{}", error_body, text_from_user_rules, text_from_steps);
  (text, all_line_begin_locs)
}

