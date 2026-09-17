
use bumpalo::Bump;
use crate::cast;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::parsing::ast::*;
use crate::parsing::tests::utils::*;
use crate::parsing::tests::utils::{
  assert_destination_local_name, assert_templex_name, expect_1, expect_2,
};
#[test]
fn capture_with_destructure_with_type_inside() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile_pattern_expect(&parse_arena, &keywords, "a [a int, b bool]");
  assert_destination_local_name(pattern.destination.as_ref().unwrap(), "a");
  assert!(pattern.templex.is_none());
  let destructure = pattern.destructure.as_ref().unwrap();
  let (a_pattern, b_pattern) = expect_2(&destructure.patterns);
  assert_destination_local_name(a_pattern.destination.as_ref().unwrap(), "a");
  assert_templex_name(a_pattern.templex.as_ref().unwrap(), "int");
  assert!(a_pattern.destructure.is_none());
  assert_destination_local_name(b_pattern.destination.as_ref().unwrap(), "b");
  assert_templex_name(b_pattern.templex.as_ref().unwrap(), "bool");
  assert!(b_pattern.destructure.is_none());
}

#[test]
fn capture_with_empty_sequence_type() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile_pattern_expect(&parse_arena, &keywords, "a ()");
  assert_destination_local_name(pattern.destination.as_ref().unwrap(), "a");
  let tuple = cast!(pattern.templex.as_ref().unwrap(), ITemplexPT::Tuple);
  assert!(tuple.elements.is_empty());
  assert!(pattern.destructure.is_none());
}

#[test]
fn empty_destructure() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile_pattern_expect(&parse_arena, &keywords, "[]");
  assert!(pattern.destination.is_none());
  assert!(pattern.templex.is_none());
  let destructure = pattern.destructure.as_ref().unwrap();
  assert!(destructure.patterns.is_empty());
}

#[test]
fn capture_with_empty_destructure() {
  // Needs the space between the braces, see https://github.com/ValeLang/Vale/issues/434
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile_pattern_expect(&parse_arena, &keywords, "a [ ]");
  assert_destination_local_name(pattern.destination.as_ref().unwrap(), "a");
  assert!(pattern.templex.is_none());
  let destructure = pattern.destructure.as_ref().unwrap();
  assert!(destructure.patterns.is_empty());
}

#[test]
fn destructure_with_nested_atom() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile_pattern_expect(&parse_arena, &keywords, "a [b int]");
  assert_destination_local_name(pattern.destination.as_ref().unwrap(), "a");
  assert!(pattern.templex.is_none());
  let destructure = pattern.destructure.as_ref().unwrap();
  let b_pattern = expect_1(&destructure.patterns);
  assert_destination_local_name(b_pattern.destination.as_ref().unwrap(), "b");
  assert_templex_name(b_pattern.templex.as_ref().unwrap(), "int");
  assert!(b_pattern.destructure.is_none());
}

