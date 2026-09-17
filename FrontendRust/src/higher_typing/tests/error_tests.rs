use crate::higher_typing::astronomer_error_reporter::CouldntSolveRulesA;
use crate::higher_typing::astronomer_error_reporter::ICompileErrorA;
use crate::higher_typing::higher_typing_error_humanizer::humanize;
use crate::higher_typing::higher_typing_pass::HigherTypingCompilation;
use crate::higher_typing::tests::test_compilation::test;
use crate::postparsing::names::CodeNameS;
use crate::postparsing::names::IImpreciseNameS;
use crate::postparsing::rune_type_solver::IRuneTypeRuleError;
use crate::postparsing::rune_type_solver::RuneTypeSolveError;
use crate::postparsing::rune_type_solver::RuneTypingCouldntFindType;
use crate::solver::solver::FailedSolve;
use crate::solver::solver::ISolverError;
use crate::solver::solver::RuleError;
use crate::utils::source_code_utils::humanize_pos_code_map;
use crate::utils::source_code_utils::line_containing;
use crate::utils::source_code_utils::line_range_containing;
use crate::utils::source_code_utils::lines_between;
use crate::interner::StrI;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::utils::range::CodeLocationS;

fn compile_program_for_error<'s, 'ctx, 'p>(
    compilation: &mut HigherTypingCompilation<'s, 'ctx, 'p>,
) -> ICompileErrorA<'s> {
    match compilation.get_astrouts() {
        Ok(result) => panic!("Expected error, but actually parsed invalid program:\n{:?}", result),
        Err(err) => err,
    }
}

#[test]
fn report_type_not_found() {
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let compilation_bump = bumpalo::Bump::new();
    report_type_not_found_inner(&parse_bump, &scout_bump, &compilation_bump);
}

fn report_type_not_found_inner<'s>(
    parse_bump: &'s bumpalo::Bump,
    scout_bump: &'s bumpalo::Bump,
    compilation_bump: &'s bumpalo::Bump,
) {

    let parse_arena = ParseArena::new(parse_bump);
    let scout_arena = ScoutArena::new(scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "exported func main(a Bork) {\n}\n";
    let mut compilation = test(
        compilation_bump, &scout_arena, &keywords, &parser_keywords, &parse_arena, code);
    let err = compile_program_for_error(&mut compilation);
    match &err {
        ICompileErrorA::CouldntSolveRules(
            CouldntSolveRulesA {
                error: RuneTypeSolveError {
                    failed_solve: FailedSolve {
                        error: ISolverError::RuleError(RuleError {
                            err: IRuneTypeRuleError::CouldntFindType(
                                RuneTypingCouldntFindType {
                                    name: IImpreciseNameS::CodeName(CodeNameS { name: StrI("Bork") }),
                                    ..
                                }),
                            ..
                        }),
                        ..
                    },
                    ..
                },
                ..
            }
        ) => {
            let code_map = compilation.get_code_map().unwrap();
            let humanize_pos_fn = |x: CodeLocationS<'s>| humanize_pos_code_map(&code_map, &x);
            let lines_between_fn = |x: CodeLocationS<'s>, y: CodeLocationS<'s>| lines_between(&code_map, &x, &y);
            let line_range_containing_fn = |x: CodeLocationS<'s>| line_range_containing(&code_map, &x);
            let line_containing_fn = |x: CodeLocationS<'s>| line_containing(&code_map, &x);
            let error_text =
                humanize(
                    &humanize_pos_fn, &lines_between_fn, &line_range_containing_fn, &line_containing_fn, &err);
            assert!(error_text.contains("Couldn't find anything with the name 'Bork'"));
        }
        _ => panic!("expected CouldntSolveRules(...RuneTypingCouldntFindType(CodeNameS(\"Bork\")))"),
    }
}
#[test]
fn report_type_not_found_with_literal_generic_arg() {
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let compilation_bump = bumpalo::Bump::new();
    report_type_not_found_with_literal_generic_arg_inner(
        &parse_bump, &scout_bump, &compilation_bump);
}

fn report_type_not_found_with_literal_generic_arg_inner<'s>(
    parse_bump: &'s bumpalo::Bump,
    scout_bump: &'s bumpalo::Bump,
    compilation_bump: &'s bumpalo::Bump,
) {
    let parse_arena = ParseArena::new(parse_bump);
    let scout_arena = ScoutArena::new(scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "exported func main(a Bork<5>) {\n}\n";
    let mut compilation = test(
        compilation_bump, &scout_arena, &keywords, &parser_keywords, &parse_arena, code);
    let err = compile_program_for_error(&mut compilation);
    match &err {
        ICompileErrorA::CouldntSolveRules(
            CouldntSolveRulesA {
                error: RuneTypeSolveError {
                    failed_solve: FailedSolve {
                        error: ISolverError::RuleError(RuleError {
                            err: IRuneTypeRuleError::CouldntFindType(
                                RuneTypingCouldntFindType {
                                    name: IImpreciseNameS::CodeName(CodeNameS { name: StrI("Bork") }),
                                    ..
                                }),
                            ..
                        }),
                        ..
                    },
                    ..
                },
                ..
            }
        ) => {
            let code_map = compilation.get_code_map().unwrap();
            let humanize_pos_fn = |x: CodeLocationS<'s>| humanize_pos_code_map(&code_map, &x);
            let lines_between_fn = |x: CodeLocationS<'s>, y: CodeLocationS<'s>| lines_between(&code_map, &x, &y);
            let line_range_containing_fn = |x: CodeLocationS<'s>| line_range_containing(&code_map, &x);
            let line_containing_fn = |x: CodeLocationS<'s>| line_containing(&code_map, &x);
            let error_text =
                humanize(
                    &humanize_pos_fn, &lines_between_fn, &line_range_containing_fn, &line_containing_fn, &err);
            let expected_suffix = r#"Couldn't solve generics rules:
Couldn't find anything with the name 'Bork'
exported func main(a Bork<5>) {
                              ^ _3: (unknown)
                          ^ _211311: (unknown)
                     ^^^^ _211211: (unknown)
                     ^^^^^^^ _2111: (unknown)
Steps:
Unsolved rule: _211211 = "Bork"
Unsolved rule: _211311 = 5
Unsolved rule: _2111 = _211211<_211311>
Unsolved rule: _3 = "void"

exported func main(a Bork<5>) {
"#;
            assert!(
                error_text.ends_with(expected_suffix),
                "humanized error suffix mismatch\nexpected ending: {:?}\nactual: {:?}",
                expected_suffix, error_text,
            );
        }
        _ => panic!("expected CouldntSolveRules(...RuneTypingCouldntFindType(CodeNameS(\"Bork\")))"),
    }
}
#[test]
fn report_type_not_found_with_mutability_literal_generic_arg() {
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let compilation_bump = bumpalo::Bump::new();
    report_type_not_found_with_mutability_literal_generic_arg_inner(
        &parse_bump, &scout_bump, &compilation_bump);
}

fn report_type_not_found_with_mutability_literal_generic_arg_inner<'s>(
    parse_bump: &'s bumpalo::Bump,
    scout_bump: &'s bumpalo::Bump,
    compilation_bump: &'s bumpalo::Bump,
) {
    let parse_arena = ParseArena::new(parse_bump);
    let scout_arena = ScoutArena::new(scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "exported func main(a Bork<mut>) {\n}\n";
    let mut compilation = test(
        compilation_bump, &scout_arena, &keywords, &parser_keywords, &parse_arena, code);
    let err = compile_program_for_error(&mut compilation);
    match &err {
        ICompileErrorA::CouldntSolveRules(
            CouldntSolveRulesA {
                error: RuneTypeSolveError {
                    failed_solve: FailedSolve {
                        error: ISolverError::RuleError(RuleError {
                            err: IRuneTypeRuleError::CouldntFindType(
                                RuneTypingCouldntFindType {
                                    name: IImpreciseNameS::CodeName(CodeNameS { name: StrI("Bork") }),
                                    ..
                                }),
                            ..
                        }),
                        ..
                    },
                    ..
                },
                ..
            }
        ) => {
            let code_map = compilation.get_code_map().unwrap();
            let humanize_pos_fn = |x: CodeLocationS<'s>| humanize_pos_code_map(&code_map, &x);
            let lines_between_fn = |x: CodeLocationS<'s>, y: CodeLocationS<'s>| lines_between(&code_map, &x, &y);
            let line_range_containing_fn = |x: CodeLocationS<'s>| line_range_containing(&code_map, &x);
            let line_containing_fn = |x: CodeLocationS<'s>| line_containing(&code_map, &x);
            let error_text =
                humanize(
                    &humanize_pos_fn, &lines_between_fn, &line_range_containing_fn, &line_containing_fn, &err);
            let expected_suffix = r#"Couldn't solve generics rules:
Couldn't find anything with the name 'Bork'
exported func main(a Bork<mut>) {
                                ^ _3: (unknown)
                          ^^^ _211311: (unknown)
                     ^^^^ _211211: (unknown)
                     ^^^^^^^^^ _2111: (unknown)
Steps:
Unsolved rule: _211211 = "Bork"
Unsolved rule: _211311 = mut
Unsolved rule: _2111 = _211211<_211311>
Unsolved rule: _3 = "void"

exported func main(a Bork<mut>) {
"#;
            assert!(
                error_text.ends_with(expected_suffix),
                "humanized error suffix mismatch\nexpected ending: {:?}\nactual: {:?}",
                expected_suffix, error_text,
            );
        }
        _ => panic!("expected CouldntSolveRules(...RuneTypingCouldntFindType(CodeNameS(\"Bork\")))"),
    }
}
#[test]
fn report_type_not_found_with_augment() {
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let compilation_bump = bumpalo::Bump::new();
    report_type_not_found_with_augment_inner(
        &parse_bump, &scout_bump, &compilation_bump);
}

fn report_type_not_found_with_augment_inner<'s>(
    parse_bump: &'s bumpalo::Bump,
    scout_bump: &'s bumpalo::Bump,
    compilation_bump: &'s bumpalo::Bump,
) {
    let parse_arena = ParseArena::new(parse_bump);
    let scout_arena = ScoutArena::new(scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "exported func main(a &Bork) {\n}\n";
    let mut compilation = test(
        compilation_bump, &scout_arena, &keywords, &parser_keywords, &parse_arena, code);
    let err = compile_program_for_error(&mut compilation);
    match &err {
        ICompileErrorA::CouldntSolveRules(
            CouldntSolveRulesA {
                error: RuneTypeSolveError {
                    failed_solve: FailedSolve {
                        error: ISolverError::RuleError(RuleError {
                            err: IRuneTypeRuleError::CouldntFindType(
                                RuneTypingCouldntFindType {
                                    name: IImpreciseNameS::CodeName(CodeNameS { name: StrI("Bork") }),
                                    ..
                                }),
                            ..
                        }),
                        ..
                    },
                    ..
                },
                ..
            }
        ) => {
            let code_map = compilation.get_code_map().unwrap();
            let humanize_pos_fn = |x: CodeLocationS<'s>| humanize_pos_code_map(&code_map, &x);
            let lines_between_fn = |x: CodeLocationS<'s>, y: CodeLocationS<'s>| lines_between(&code_map, &x, &y);
            let line_range_containing_fn = |x: CodeLocationS<'s>| line_range_containing(&code_map, &x);
            let line_containing_fn = |x: CodeLocationS<'s>| line_containing(&code_map, &x);
            let error_text =
                humanize(
                    &humanize_pos_fn, &lines_between_fn, &line_range_containing_fn, &line_containing_fn, &err);
            let expected_suffix = r#"Couldn't solve generics rules:
Couldn't find anything with the name 'Bork'
exported func main(a &Bork) {
                            ^ _3: (unknown)
                      ^^^^ _211211: (unknown)
                     ^^^^^ _2111: (unknown)
Steps:
Unsolved rule: _211211 = "Bork"
Unsolved rule: _2111 = &_211211
Unsolved rule: _3 = "void"

exported func main(a &Bork) {
"#;
            assert!(
                error_text.ends_with(expected_suffix),
                "humanized error suffix mismatch\nexpected ending: {:?}\nactual: {:?}",
                expected_suffix, error_text,
            );
        }
        _ => panic!("expected CouldntSolveRules(...RuneTypingCouldntFindType(CodeNameS(\"Bork\")))"),
    }
}

