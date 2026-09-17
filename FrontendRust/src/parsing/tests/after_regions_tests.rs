use bumpalo::Bump;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::lexing::errors::ParseError;
use crate::parsing::tests::utils::{compile_statement, compile_block_contents};

#[test]
fn forgetting_set_when_changing() {
  // This test does not pass yet, use #[ignore].
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let error =
    compile_statement(
      &parse_arena,
      &keywords,
      "ship.x = 4;").unwrap_err();
  match error {
    ParseError::ForgotSetKeyword(_) => {}
    other => panic!("Expected ForgotSetKeyword, got {:?}", other),
  }
}

#[test]
#[ignore = "blocked - Rust parser produces Consecutor(...) for `set x = 7 )` instead of ParseError::BadStartOfStatementError on the stray `)`. Tracked in migration-drive-todo.md Phase 4e."]
fn report_leaving_out_semicolon_or_ending_body_after_expression_for_paren() {
  // This test does not pass yet, use #[ignore].
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let err = compile_block_contents(
    &parse_arena,
    &keywords,
    r"
  a = 3;
  set x = 7 )
").unwrap_err();
  match err {
    ParseError::BadStartOfStatementError(_) => {}
    other => panic!("Expected BadStartOfStatementError, got {:?}", other),
  }
}

