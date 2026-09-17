
use crate::solver::{SimpleSolverState, FailedSolve, ISolverError, make_solver_state};
use super::test_rules::TestRule;
use indexmap::{IndexMap, IndexSet};
use std::collections::HashMap;
use std::collections::HashSet;
use crate::utils::range::RangeS;
use bumpalo::Bump;
use super::test_rules::Literal;
use super::test_rules::Equals;
use super::test_rules::OneOf;
use super::test_rules::CoordComponents;
use super::test_rules::Pack;
use super::test_rules::Send;
use super::test_rules::Call;
use super::test_rules::Lookup;
use crate::scout_arena::ScoutArena;
const COMPLEX_RULE_SET_RULES: Vec<()> = vec![];

const COMPLEX_RULE_SET_EQUALS_RULES: Vec<i32> = vec![];

    fn test_simple_and_optimized() {
        panic!("Unimplemented: test_simple_and_optimized");
    }

// Local advance helper, inlined from the former generic Solver.advance.
// Returns true if there's more to be done, false if we've gotten as far as we can.
fn advance(
    solver_state: &mut SimpleSolverState<TestRule, i64, String>,
    solve_rule: &super::test_rule_solver::TestRuleSolver,
) -> Result<bool, FailedSolve<TestRule, i64, String, String>> {
    solver_state.sanity_check();
    // Stage 1: simple solve
    match solver_state.get_next_solvable() {
        None => {} // continue onto complex solve
        Some(rule_index) => {
            let rule = solver_state.get_rule(rule_index).clone();
            let steps_before = solver_state.get_steps().len();
            match solve_rule.solve_impl(&(), &(), solver_state, rule_index, &rule) {
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
            assert!(solver_state.rule_is_solved(rule_index));
            solver_state.sanity_check();
            return Ok(true);
        }
    }
    // Stage 2: complex solve
    if !solver_state.get_unsolved_rules().is_empty() {
        let conclusions_before = solver_state.get_conclusions().len();
        match solve_rule.complex_solve_impl(&(), &(), solver_state) {
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
        if conclusions_after > conclusions_before {
            return Ok(true);
        }
    }
    Ok(false)
}

    #[test]
    fn simple_int_rule() {

        let rules: Vec<TestRule> = vec![
            TestRule::Literal(Literal { rune: -1, value: "1337".to_string() }),
        ];
        let result = get_conclusions(rules, true, IndexMap::new());
        let expected: HashMap<i64, String> = [(-1, "1337".to_string())].into_iter().collect();
        assert_eq!(result, expected);
    }

    #[test]
    fn equals_transitive() {

        let rules: Vec<TestRule> = vec![
            TestRule::Equals(Equals {
                left_rune: -2,
                right_rune: -1,
            }),
            TestRule::Literal(Literal {
                rune: -1,
                value: "1337".to_string(),
            }),
        ];
        let result = get_conclusions(rules, true, IndexMap::new());
        let expected: HashMap<i64, String> =
            [(-1, "1337".to_string()), (-2, "1337".to_string())].into_iter().collect();
        assert_eq!(result, expected);
    }

    #[test]
    fn incomplete_solve() {

        let rules: Vec<TestRule> = vec![TestRule::OneOf(OneOf {
            coord_rune: -1,
            possible_values: vec!["1448".to_string(), "1337".to_string()],
        })];
        let result = get_conclusions(rules, false, IndexMap::new());
        let expected: HashMap<i64, String> = HashMap::new();
        assert_eq!(result, expected);
    }

    #[test]
    fn half_complete_solve() {

        let rules: Vec<TestRule> = vec![
            TestRule::OneOf(OneOf {
                coord_rune: -1,
                possible_values: vec!["1448".to_string(), "1337".to_string()],
            }),
            TestRule::Literal(Literal {
                rune: -2,
                value: "1337".to_string(),
            }),
        ];
        let result = get_conclusions(rules, false, IndexMap::new());
        let expected: HashMap<i64, String> =
            [(-2, "1337".to_string())].into_iter().collect();
        assert_eq!(result, expected);
    }

    #[test]
    fn one_of() {

        let rules: Vec<TestRule> = vec![
            TestRule::OneOf(OneOf {
                coord_rune: -1,
                possible_values: vec!["1448".to_string(), "1337".to_string()],
            }),
            TestRule::Literal(Literal {
                rune: -1,
                value: "1337".to_string(),
            }),
        ];
        let result = get_conclusions(rules, true, IndexMap::new());
        let expected: HashMap<i64, String> =
            [(-1, "1337".to_string())].into_iter().collect();
        assert_eq!(result, expected);
    }

    #[test]
    fn solves_a_components_rule() {

        let rules: Vec<TestRule> = vec![
            TestRule::CoordComponents(CoordComponents {
                coord_rune: -1,
                ownership_rune: -2,
                kind_rune: -3,
            }),
            TestRule::Literal(Literal {
                rune: -2,
                value: "1337".to_string(),
            }),
            TestRule::Literal(Literal {
                rune: -3,
                value: "1448".to_string(),
            }),
        ];
        let result = get_conclusions(rules, true, IndexMap::new());
        let expected: HashMap<i64, String> = [
            (-1, "1337/1448".to_string()),
            (-2, "1337".to_string()),
            (-3, "1448".to_string()),
        ]
        .into_iter()
        .collect();
        assert_eq!(result, expected);
    }

    #[test]
    fn reverse_solve_a_components_rule() {

        let rules: Vec<TestRule> = vec![
            TestRule::CoordComponents(CoordComponents {
                coord_rune: -1,
                ownership_rune: -2,
                kind_rune: -3,
            }),
            TestRule::Literal(Literal {
                rune: -1,
                value: "1337/1448".to_string(),
            }),
        ];
        let result = get_conclusions(rules, true, IndexMap::new());
        let expected: HashMap<i64, String> = [
            (-1, "1337/1448".to_string()),
            (-2, "1337".to_string()),
            (-3, "1448".to_string()),
        ]
        .into_iter()
        .collect();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_infer_pack() {

        let rules: Vec<TestRule> = vec![
            TestRule::Literal(Literal { rune: -1, value: "1337".to_string() }),
            TestRule::Literal(Literal { rune: -2, value: "1448".to_string() }),
            TestRule::Pack(Pack {
                result_rune: -3,
                member_runes: vec![-1, -2],
            }),
        ];
        let result = get_conclusions(rules, true, IndexMap::new());
        let expected: HashMap<i64, String> = [
            (-1, "1337".to_string()),
            (-2, "1448".to_string()),
            (-3, "1337,1448".to_string()),
        ]
        .into_iter()
        .collect();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_infer_pack_from_result() {

        let rules: Vec<TestRule> = vec![
            TestRule::Literal(Literal {
                rune: -3,
                value: "1337,1448".to_string(),
            }),
            TestRule::Pack(Pack {
                result_rune: -3,
                member_runes: vec![-1, -2],
            }),
        ];
        let result = get_conclusions(rules, true, IndexMap::new());
        let expected: HashMap<i64, String> = [
            (-1, "1337".to_string()),
            (-2, "1448".to_string()),
            (-3, "1337,1448".to_string()),
        ]
        .into_iter()
        .collect();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_infer_pack_from_empty_result() {

        let rules: Vec<TestRule> = vec![
            TestRule::Literal(Literal {
                rune: -3,
                value: "".to_string(),
            }),
            TestRule::Pack(Pack {
                result_rune: -3,
                member_runes: vec![],
            }),
        ];
        let result = get_conclusions(rules, true, IndexMap::new());
        let expected: HashMap<i64, String> = [(-3, "".to_string())].into_iter().collect();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_cant_solve_empty_pack() {

        let rules: Vec<TestRule> = vec![TestRule::Pack(Pack {
            result_rune: -3,
            member_runes: vec![],
        })];
        let result = get_conclusions(rules, false, IndexMap::new());
        let expected: HashMap<i64, String> = HashMap::new();
        assert_eq!(result, expected);
    }

    #[test]
    fn complex_rule_set() {

        let rules: Vec<TestRule> = vec![
            TestRule::Literal(Literal {
                rune: -3,
                value: "1448".to_string(),
            }),
            TestRule::CoordComponents(CoordComponents {
                coord_rune: -6,
                ownership_rune: -5,
                kind_rune: -5,
            }),
            TestRule::Literal(Literal {
                rune: -2,
                value: "1337".to_string(),
            }),
            TestRule::Equals(Equals {
                left_rune: -4,
                right_rune: -2,
            }),
            TestRule::OneOf(OneOf {
                coord_rune: -4,
                possible_values: vec!["1337".to_string(), "73".to_string()],
            }),
            TestRule::Equals(Equals {
                left_rune: -1,
                right_rune: -5,
            }),
            TestRule::CoordComponents(CoordComponents {
                coord_rune: -1,
                ownership_rune: -2,
                kind_rune: -3,
            }),
            TestRule::Equals(Equals {
                left_rune: -6,
                right_rune: -7,
            }),
        ];
        let conclusions = get_conclusions(rules, true, IndexMap::new());
        assert_eq!(
            conclusions.get(&-7),
            Some(&"1337/1448/1337/1448".to_string())
        );
    }

    #[test]
    fn test_receiving_struct_to_struct() {

        let rules: Vec<TestRule> = vec![
            TestRule::Literal(Literal {
                rune: -1,
                value: "Firefly".to_string(),
            }),
            TestRule::Send(Send {
                sender_rune: -2,
                receiver_rune: -1,
            }),
        ];
        let result = get_conclusions(rules, true, IndexMap::new());
        let expected: HashMap<i64, String> = [
            (-1, "Firefly".to_string()),
            (-2, "Firefly".to_string()),
        ]
        .into_iter()
        .collect();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_receive_struct_from_sent_interface() {

        let rules: Vec<TestRule> = vec![
            TestRule::Literal(Literal {
                rune: -1,
                value: "Firefly".to_string(),
            }),
            TestRule::Literal(Literal {
                rune: -2,
                value: "ISpaceship".to_string(),
            }),
            TestRule::Send(Send {
                sender_rune: -2,
                receiver_rune: -1,
            }),
        ];
        let failed = expect_solve_failure(rules);

        // commitStep appends the step before checking conflicts, so the conflicting step
        // is captured in the audit trail (matching Scala behavior).
        let conclusions_set: HashSet<(i64, String)> = failed
            .steps
            .iter()
            .flat_map(|s| {
                s.conclusions
                    .iter()
                    .map(|(r, c)| (*r, c.clone()))
            })
            .collect();
        let expected_conclusions: HashSet<(i64, String)> = [
            (-1, "Firefly".to_string()),
            (-2, "ISpaceship".to_string()),
            (-2, "Firefly".to_string()),
        ]
        .into_iter()
        .collect();
        assert_eq!(conclusions_set, expected_conclusions);

        assert_eq!(failed.unsolved_rules.len(), 1);
        match &failed.unsolved_rules[0] {
            TestRule::Send(s) => {
                assert_eq!(s.sender_rune, -2);
                assert_eq!(s.receiver_rune, -1);
            }
            _ => panic!("Expected Send in unsolved_rules"),
        }

        match &failed.error {
            ISolverError::SolverConflict(conflict) => {
                assert_eq!(conflict.rune, -2);
                assert_eq!(conflict.previous_conclusion, "ISpaceship");
                assert_eq!(conflict.new_conclusion, "Firefly");
            }
            _ => panic!("Expected SolverConflict(-2, \"ISpaceship\", \"Firefly\")"),
        }
    }

    #[test]
    fn test_receive_interface_from_sent_struct() {

        let rules: Vec<TestRule> = vec![
            TestRule::Literal(Literal {
                rune: -1,
                value: "ISpaceship".to_string(),
            }),
            TestRule::Literal(Literal {
                rune: -2,
                value: "Firefly".to_string(),
            }),
            TestRule::Send(Send {
                sender_rune: -2,
                receiver_rune: -1,
            }),
        ];
        let result = get_conclusions(rules, true, IndexMap::new());
        let expected: HashMap<i64, String> = [
            (-1, "ISpaceship".to_string()),
            (-2, "Firefly".to_string()),
        ]
        .into_iter()
        .collect();
        assert_eq!(result, expected);
    }

    // Tests @CSCDSRZ: complex solve infers the receiver kind from senders.
    #[test]
    fn test_complex_solve_most_specific_ancestor() {

        let rules: Vec<TestRule> = vec![
            TestRule::Literal(Literal {
                rune: -2,
                value: "Firefly".to_string(),
            }),
            TestRule::Send(Send {
                sender_rune: -2,
                receiver_rune: -1,
            }),
        ];
        let result = get_conclusions(rules, true, IndexMap::new());
        let expected: HashMap<i64, String> = [
            (-1, "Firefly".to_string()),
            (-2, "Firefly".to_string()),
        ]
        .into_iter()
        .collect();
        assert_eq!(result, expected);
    }

    // Tests @CSCDSRZ: complex solve finds the common ancestor of multiple senders.
    #[test]
    fn test_complex_solve_calculate_common_ancestor() {

        let rules: Vec<TestRule> = vec![
            TestRule::Literal(Literal {
                rune: -2,
                value: "Firefly".to_string(),
            }),
            TestRule::Literal(Literal {
                rune: -3,
                value: "Serenity".to_string(),
            }),
            TestRule::Send(Send {
                sender_rune: -2,
                receiver_rune: -1,
            }),
            TestRule::Send(Send {
                sender_rune: -3,
                receiver_rune: -1,
            }),
        ];
        let result = get_conclusions(rules, true, IndexMap::new());
        let expected: HashMap<i64, String> = [
            (-1, "ISpaceship".to_string()),
            (-2, "Firefly".to_string()),
            (-3, "Serenity".to_string()),
        ]
        .into_iter()
        .collect();
        assert_eq!(result, expected);
    }

    // Tests @CSCDSRZ: complex solve picks a descendant that satisfies a call constraint.
    #[test]
    fn test_complex_solve_descendant_satisfying_call() {

        let rules: Vec<TestRule> = vec![
            TestRule::Literal(Literal {
                rune: -2,
                value: "Flamethrower:int".to_string(),
            }),
            TestRule::Send(Send {
                sender_rune: -2,
                receiver_rune: -1,
            }),
            TestRule::Call(Call {
                result_rune: -1,
                name_rune: -3,
                arg_rune: -4,
            }),
            TestRule::Literal(Literal {
                rune: -3,
                value: "IWeapon".to_string(),
            }),
        ];
        let result = get_conclusions(rules, true, IndexMap::new());
        let expected: HashMap<i64, String> = [
            (-1, "IWeapon:int".to_string()),
            (-4, "int".to_string()),
            (-2, "Flamethrower:int".to_string()),
            (-3, "IWeapon".to_string()),
        ]
        .into_iter()
        .collect();
        assert_eq!(result, expected);
    }

    #[test]
    fn partial_solve() {

        let scout_bump = Bump::new();
        let scout_arena = ScoutArena::new(&scout_bump);
        let rules: Vec<TestRule> = vec![
            TestRule::Literal(Literal { rune: -2, value: "A".to_string() }),
            TestRule::Call(Call {
                result_rune: -3,
                name_rune: -1,
                arg_rune: -2,
            }),
        ];
        let all_runes: Vec<i64> = {
            let mut v: Vec<i64> = rules.iter().flat_map(|r| r.all_runes()).collect();
            v.sort();
            v.dedup();
            v
        };
        let test_solver = super::test_rule_solver::TestRuleSolver {
            scout_arena: &scout_arena,
        };
        let mut solver_state = make_solver_state(
            true,
            false,
            Box::new(super::test_rule_solver::rule_to_puzzles),
            &|rule: &super::test_rules::TestRule| rule.all_runes(),
            rules,
            IndexMap::new(),
            all_runes,
        );

        while advance(&mut solver_state, &test_solver).expect("advance") {}
        let first_conclusions: HashMap<i64, String> =
            solver_state.userify_conclusions().into_iter().collect();
        assert_eq!(first_conclusions.get(&-2), Some(&"A".to_string()));

        let mut new_conclusions = IndexMap::new();
        new_conclusions.insert(-1i64, "Firefly".to_string());
        solver_state
            .commit_step::<String>(false, vec![], new_conclusions, vec![], IndexSet::new())
            .expect("commit_step");

        while advance(&mut solver_state, &test_solver).expect("advance") {}
        let second_conclusions: HashMap<i64, String> =
            solver_state.userify_conclusions().into_iter().collect();
        assert_eq!(second_conclusions.get(&-1), Some(&"Firefly".to_string()));
        assert_eq!(second_conclusions.get(&-2), Some(&"A".to_string()));
        assert_eq!(
            second_conclusions.get(&-3),
            Some(&"Firefly:A".to_string())
        );
    }

    #[test]
    fn predicting() {

        let predictions = solve_with_puzzler(Box::new(|rule: &TestRule| match rule {
            TestRule::Lookup(_) => vec![],
            other => other.all_puzzles(),
        }));
        assert_eq!(predictions.len(), 1, "predicting mode should solve only rune -2");
        assert_eq!(predictions.get(&-2), Some(&"1337".to_string()));

        let conclusions = solve_with_puzzler(Box::new(|rule: &TestRule| rule.all_puzzles()));
        let expected: HashMap<i64, String> = [
            (-1, "Firefly".to_string()),
            (-2, "1337".to_string()),
            (-3, "Firefly:1337".to_string()),
        ]
        .into_iter()
        .collect();
        assert_eq!(conclusions, expected);
    }

    fn solve_with_puzzler(
        puzzler: Box<dyn Fn(&super::test_rules::TestRule) -> Vec<Vec<i64>>>,
    ) -> HashMap<i64, String> {

        let scout_bump = Bump::new();
        let scout_arena = ScoutArena::new(&scout_bump);
        let rules: Vec<TestRule> = vec![
            TestRule::Lookup(Lookup {
                rune: -1,
                name: "Firefly".to_string(),
            }),
            TestRule::Literal(Literal {
                rune: -2,
                value: "1337".to_string(),
            }),
            TestRule::Call(Call {
                result_rune: -3,
                name_rune: -1,
                arg_rune: -2,
            }),
        ];
        let all_runes: Vec<i64> = {
            let mut v: Vec<i64> = rules.iter().flat_map(|r| r.all_runes()).collect();
            v.sort();
            v.dedup();
            v
        };
        let test_solver = super::test_rule_solver::TestRuleSolver { scout_arena: &scout_arena };
        let mut solver_state = make_solver_state(
            true,
            false,
            puzzler,
            &|rule: &super::test_rules::TestRule| rule.all_runes(),
            rules,
            IndexMap::new(),
            all_runes,
        );
        while advance(&mut solver_state, &test_solver).expect("advance") {}
        solver_state.userify_conclusions().into_iter().collect()
    }

    #[test]
    fn test_conflict() {

        let rules: Vec<TestRule> = vec![
            TestRule::Literal(Literal {
                rune: -1,
                value: "1448".to_string(),
            }),
            TestRule::Literal(Literal {
                rune: -1,
                value: "1337".to_string(),
            }),
        ];
        let failed = expect_solve_failure(rules);
        match &failed.error {
            ISolverError::SolverConflict(conflict) => {
                let mut conclusions =
                    vec![conflict.previous_conclusion.clone(), conflict.new_conclusion.clone()];
                conclusions.sort();
                assert_eq!(conclusions, ["1337", "1448"]);
            }
            _ => panic!("Expected SolverConflict"),
        }
    }

    fn expect_solve_failure(
        rules: Vec<super::test_rules::TestRule>,
    ) -> FailedSolve<TestRule, i64, String, String> {

        let scout_bump = Bump::new();
        let scout_arena = ScoutArena::new(&scout_bump);
        let all_runes: Vec<i64> = {
            let mut v: Vec<i64> = rules.iter().flat_map(|r| r.all_runes()).collect();
            v.sort();
            v.dedup();
            v
        };
        let test_solver = super::test_rule_solver::TestRuleSolver {
            scout_arena: &scout_arena,
        };
        let mut solver_state = make_solver_state(
            true,
            false,
            Box::new(super::test_rule_solver::rule_to_puzzles),
            &|rule: &super::test_rules::TestRule| rule.all_runes(),
            rules,
            IndexMap::new(),
            all_runes,
        );

        loop {
            match advance(&mut solver_state, &test_solver) {
                Ok(continue_flag) => {
                    if !continue_flag {
                        break;
                    }
                }
                Err(f) => return f,
            }
        }

        panic!("Incorrectly completed the solve")
    }

    fn get_conclusions(
        rules: Vec<super::test_rules::TestRule>,
        expect_complete_solve: bool,
        initially_known_runes: IndexMap<i64, String>,
    ) -> HashMap<i64, String> {

        let scout_bump = Bump::new();
        let scout_arena = ScoutArena::new(&scout_bump);
        let all_runes_from_rules: HashSet<i64> =
            rules.iter().flat_map(|r| r.all_runes()).collect();
        let all_runes: Vec<i64> = {
            let mut v: Vec<i64> = rules
                .iter()
                .flat_map(|r| r.all_runes())
                .chain(initially_known_runes.keys().cloned())
                .collect();
            v.sort();
            v.dedup();
            v
        };
        let test_solver = super::test_rule_solver::TestRuleSolver {
            scout_arena: &scout_arena,
        };
        let mut solver_state = make_solver_state(
            true,
            false,
            Box::new(super::test_rule_solver::rule_to_puzzles),
            &|rule: &super::test_rules::TestRule| rule.all_runes(),
            rules,
            initially_known_runes,
            all_runes,
        );

        while advance(&mut solver_state, &test_solver).expect("advance") {}

        let conclusions: HashMap<i64, String> =
            solver_state.userify_conclusions().into_iter().collect();
        let conclusions_keys: HashSet<i64> =
            conclusions.keys().cloned().collect();
        assert_eq!(expect_complete_solve, conclusions_keys == all_runes_from_rules);

        conclusions
    }
