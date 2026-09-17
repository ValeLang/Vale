// Run with: cargo test --manifest-path FrontendRust/Cargo.toml --lib parsing::tests::patterns::destructure_parser_tests

use bumpalo::Bump;
use crate::parsing::ast::{INameDeclarationP, PatternPP};
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::parsing::tests::utils::{
  assert_destination_local_name, assert_templex_name, compile_pattern_expect, expect_1, expect_2,
};

fn compile<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> PatternPP<'p>
where
  'p: 'ctx,
{
  compile_pattern_expect(parse_arena, keywords, code)
}

#[test]
fn only_empty_destructure() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "[]");
  assert!(pattern.destination.is_none());
  assert!(pattern.templex.is_none());
  let destructure = pattern.destructure.as_ref().unwrap();
  assert!(destructure.patterns.is_empty());
}

#[test]
fn one_element_destructure() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "[a]");
  assert!(pattern.destination.is_none());
  assert!(pattern.templex.is_none());
  let destructure = pattern.destructure.as_ref().unwrap();
  let a_pattern = expect_1(&destructure.patterns);
  assert_destination_local_name(a_pattern.destination.as_ref().unwrap(), "a");
  assert!(a_pattern.templex.is_none());
  assert!(a_pattern.destructure.is_none());
}

#[test]
fn one_typed_element_destructure() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "[ _ A ]");
  assert!(pattern.destination.is_none());
  assert!(pattern.templex.is_none());
  let destructure = pattern.destructure.as_ref().unwrap();
  let inner_pattern = expect_1(&destructure.patterns);
  assert!(matches!(
    inner_pattern
      .destination
      .as_ref()
      .map(|destination| &destination.decl),
    None | Some(INameDeclarationP::IgnoredLocalNameDeclaration(_))
  ));
  assert_templex_name(inner_pattern.templex.as_ref().unwrap(), "A");
  assert!(inner_pattern.destructure.is_none());
}

#[test]
fn only_two_element_destructure() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "[a, b]");
  assert!(pattern.destination.is_none());
  assert!(pattern.templex.is_none());
  let destructure = pattern.destructure.as_ref().unwrap();
  let (a_pattern, b_pattern) = expect_2(&destructure.patterns);
  assert_destination_local_name(a_pattern.destination.as_ref().unwrap(), "a");
  assert!(a_pattern.templex.is_none());
  assert!(a_pattern.destructure.is_none());
  assert_destination_local_name(b_pattern.destination.as_ref().unwrap(), "b");
  assert!(b_pattern.templex.is_none());
  assert!(b_pattern.destructure.is_none());
}

#[test]
fn two_element_destructure_with_ignore() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "[_, b]");
  assert!(pattern.destination.is_none());
  assert!(pattern.templex.is_none());
  let destructure = pattern.destructure.as_ref().unwrap();
  let (ignored_pattern, b_pattern) = expect_2(&destructure.patterns);
  let ignored_destination = ignored_pattern.destination.as_ref().unwrap();
  assert!(matches!(
    ignored_destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(ignored_destination.mutate.is_none());
  assert!(ignored_pattern.templex.is_none());
  assert!(ignored_pattern.destructure.is_none());
  assert_destination_local_name(b_pattern.destination.as_ref().unwrap(), "b");
  assert!(b_pattern.templex.is_none());
  assert!(b_pattern.destructure.is_none());
}

#[test]
fn capture_with_destructure() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "a [x, y]");
  assert_destination_local_name(pattern.destination.as_ref().unwrap(), "a");
  assert!(pattern.templex.is_none());
  let destructure = pattern.destructure.as_ref().unwrap();
  let (x_pattern, y_pattern) = expect_2(&destructure.patterns);
  assert_destination_local_name(x_pattern.destination.as_ref().unwrap(), "x");
  assert!(x_pattern.templex.is_none());
  assert!(x_pattern.destructure.is_none());
  assert_destination_local_name(y_pattern.destination.as_ref().unwrap(), "y");
  assert!(y_pattern.templex.is_none());
  assert!(y_pattern.destructure.is_none());
}

#[test]
fn type_with_destructure() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "A[a, b]");
  assert!(pattern.destination.is_none());
  assert_templex_name(pattern.templex.as_ref().unwrap(), "A");
  let destructure = pattern.destructure.as_ref().unwrap();
  let (a_pattern, b_pattern) = expect_2(&destructure.patterns);
  assert_destination_local_name(a_pattern.destination.as_ref().unwrap(), "a");
  assert!(a_pattern.templex.is_none());
  assert!(a_pattern.destructure.is_none());
  assert_destination_local_name(b_pattern.destination.as_ref().unwrap(), "b");
  assert!(b_pattern.templex.is_none());
  assert!(b_pattern.destructure.is_none());
}

#[test]
fn capture_and_type_with_destructure() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "a A[x, y]");
  assert_destination_local_name(pattern.destination.as_ref().unwrap(), "a");
  assert_templex_name(pattern.templex.as_ref().unwrap(), "A");
  let destructure = pattern.destructure.as_ref().unwrap();
  let (x_pattern, y_pattern) = expect_2(&destructure.patterns);
  assert_destination_local_name(x_pattern.destination.as_ref().unwrap(), "x");
  assert!(x_pattern.templex.is_none());
  assert!(x_pattern.destructure.is_none());
  assert_destination_local_name(y_pattern.destination.as_ref().unwrap(), "y");
  assert!(y_pattern.templex.is_none());
  assert!(y_pattern.destructure.is_none());
}

#[test]
fn capture_with_types_inside() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "a [_ int, _ bool]");
  assert_destination_local_name(pattern.destination.as_ref().unwrap(), "a");
  assert!(pattern.templex.is_none());
  let destructure = pattern.destructure.as_ref().unwrap();
  let (int_pattern, bool_pattern) = expect_2(&destructure.patterns);
  assert!(matches!(
    int_pattern
      .destination
      .as_ref()
      .map(|destination| &destination.decl),
    None | Some(INameDeclarationP::IgnoredLocalNameDeclaration(_))
  ));
  assert_templex_name(int_pattern.templex.as_ref().unwrap(), "int");
  assert!(int_pattern.destructure.is_none());
  assert!(matches!(
    bool_pattern
      .destination
      .as_ref()
      .map(|destination| &destination.decl),
    None | Some(INameDeclarationP::IgnoredLocalNameDeclaration(_))
  ));
  assert_templex_name(bool_pattern.templex.as_ref().unwrap(), "bool");
  assert!(bool_pattern.destructure.is_none());
}

#[test]
fn destructure_with_type_inside() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "[a int, b bool]");
  assert!(pattern.destination.is_none());
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
fn nested_destructures_a() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "[a, [b, c]]");
  assert!(pattern.destination.is_none());
  assert!(pattern.templex.is_none());
  let destructure = pattern.destructure.as_ref().unwrap();
  let (a_pattern, nested_pattern) = expect_2(&destructure.patterns);
  assert_destination_local_name(a_pattern.destination.as_ref().unwrap(), "a");
  assert!(a_pattern.templex.is_none());
  assert!(a_pattern.destructure.is_none());
  assert!(nested_pattern.destination.is_none());
  assert!(nested_pattern.templex.is_none());
  let nested_destructure = nested_pattern.destructure.as_ref().unwrap();
  let (b_pattern, c_pattern) = expect_2(&nested_destructure.patterns);
  assert_destination_local_name(b_pattern.destination.as_ref().unwrap(), "b");
  assert!(b_pattern.templex.is_none());
  assert!(b_pattern.destructure.is_none());
  assert_destination_local_name(c_pattern.destination.as_ref().unwrap(), "c");
  assert!(c_pattern.templex.is_none());
  assert!(c_pattern.destructure.is_none());
}

#[test]
fn nested_destructures_b() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "[[a], b]");
  assert!(pattern.destination.is_none());
  assert!(pattern.templex.is_none());
  let destructure = pattern.destructure.as_ref().unwrap();
  let (nested_pattern, b_pattern) = expect_2(&destructure.patterns);
  assert!(nested_pattern.destination.is_none());
  assert!(nested_pattern.templex.is_none());
  let nested_destructure = nested_pattern.destructure.as_ref().unwrap();
  let a_pattern = expect_1(&nested_destructure.patterns);
  assert_destination_local_name(a_pattern.destination.as_ref().unwrap(), "a");
  assert!(a_pattern.templex.is_none());
  assert!(a_pattern.destructure.is_none());
  assert_destination_local_name(b_pattern.destination.as_ref().unwrap(), "b");
  assert!(b_pattern.templex.is_none());
  assert!(b_pattern.destructure.is_none());
}

#[test]
fn nested_destructures_c() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "[[[a]]]");
  assert!(pattern.destination.is_none());
  assert!(pattern.templex.is_none());
  let outer_destructure = pattern.destructure.as_ref().unwrap();
  let middle_pattern = expect_1(&outer_destructure.patterns);
  assert!(middle_pattern.destination.is_none());
  assert!(middle_pattern.templex.is_none());
  let middle_destructure = middle_pattern.destructure.as_ref().unwrap();
  let inner_pattern = expect_1(&middle_destructure.patterns);
  assert!(inner_pattern.destination.is_none());
  assert!(inner_pattern.templex.is_none());
  let inner_destructure = inner_pattern.destructure.as_ref().unwrap();
  let a_pattern = expect_1(&inner_destructure.patterns);
  assert_destination_local_name(a_pattern.destination.as_ref().unwrap(), "a");
  assert!(a_pattern.templex.is_none());
  assert!(a_pattern.destructure.is_none());
}

