// Run with: cargo test --manifest-path FrontendRust/Cargo.toml --lib parsing::tests::patterns::type_and_destructure_tests

use bumpalo::Bump;
use crate::cast;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::parsing::ast::{INameDeclarationP, ITemplexPT, PatternPP};
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
fn empty_destructure() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ Muta[]");
  let destination = pattern.destination.as_ref().unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  assert_templex_name(pattern.templex.as_ref().unwrap(), "Muta");
  let destructure = pattern.destructure.as_ref().unwrap();
  assert!(destructure.patterns.is_empty());
}

#[test]
fn templated_destructure() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  {
    let pattern = compile(&parse_arena, &keywords, "_ Muta<int>[]");
    let destination = pattern.destination.as_ref().unwrap();
    assert!(matches!(
      destination.decl,
      INameDeclarationP::IgnoredLocalNameDeclaration(_)
    ));
    assert!(destination.mutate.is_none());
    let call = cast!(pattern.templex.as_ref().unwrap(), ITemplexPT::Call);
    assert_templex_name(call.template, "Muta");
    let first_arg = expect_1(&call.args);
    assert_templex_name(first_arg, "int");
    let destructure = pattern.destructure.as_ref().unwrap();
    assert!(destructure.patterns.is_empty());
  }

  {
    let pattern = compile(&parse_arena, &keywords, "_ Muta<R>[]");
    let destination = pattern.destination.as_ref().unwrap();
    assert!(matches!(
      destination.decl,
      INameDeclarationP::IgnoredLocalNameDeclaration(_)
    ));
    assert!(destination.mutate.is_none());
    let call = cast!(pattern.templex.as_ref().unwrap(), ITemplexPT::Call);
    assert_templex_name(call.template, "Muta");
    let first_arg = expect_1(&call.args);
    assert_templex_name(first_arg, "R");
    let destructure = pattern.destructure.as_ref().unwrap();
    assert!(destructure.patterns.is_empty());
  }
}

#[test]
fn destructure_with_type_outside() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ (int, bool)[a, b]");
  let destination = pattern.destination.as_ref().unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  let tuple = cast!(pattern.templex.as_ref().unwrap(), ITemplexPT::Tuple);
  let (int_, bool_) = expect_2(&tuple.elements);
  assert_templex_name(int_, "int");
  assert_templex_name(bool_, "bool");
  let destructure = pattern.destructure.as_ref().unwrap();
  let (a_pattern, b_pattern) = expect_2(&destructure.patterns);
  let a_destination = a_pattern.destination.as_ref().unwrap();
  assert_destination_local_name(a_destination, "a");
  assert!(a_destination.mutate.is_none());
  assert!(a_pattern.templex.is_none());
  assert!(a_pattern.destructure.is_none());
  let b_destination = b_pattern.destination.as_ref().unwrap();
  assert_destination_local_name(b_destination, "b");
  assert!(b_destination.mutate.is_none());
  assert!(b_pattern.templex.is_none());
  assert!(b_pattern.destructure.is_none());
}

#[test]
fn destructure_with_typeless_capture() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ Muta[b]");
  let destination = pattern.destination.as_ref().unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  assert_templex_name(pattern.templex.as_ref().unwrap(), "Muta");
  let destructure = pattern.destructure.as_ref().unwrap();
  let b_pattern = expect_1(&destructure.patterns);
  let b_destination = b_pattern.destination.as_ref().unwrap();
  assert_destination_local_name(b_destination, "b");
  assert!(b_destination.mutate.is_none());
  assert!(b_pattern.templex.is_none());
  assert!(b_pattern.destructure.is_none());
}

#[test]
fn destructure_with_typed_capture() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ Muta[b Marine]");
  let destination = pattern.destination.as_ref().unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  assert_templex_name(pattern.templex.as_ref().unwrap(), "Muta");
  let destructure = pattern.destructure.as_ref().unwrap();
  let b_pattern = expect_1(&destructure.patterns);
  let b_destination = b_pattern.destination.as_ref().unwrap();
  assert_destination_local_name(b_destination, "b");
  assert!(b_destination.mutate.is_none());
  assert_templex_name(b_pattern.templex.as_ref().unwrap(), "Marine");
  assert!(b_pattern.destructure.is_none());
}

#[test]
fn destructure_with_unnamed_capture() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ Muta[_ Marine]");
  let destination = pattern.destination.as_ref().unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  assert_templex_name(pattern.templex.as_ref().unwrap(), "Muta");
  let destructure = pattern.destructure.as_ref().unwrap();
  let unnamed_pattern = expect_1(&destructure.patterns);
  let unnamed_destination = unnamed_pattern.destination.as_ref().unwrap();
  assert!(matches!(
    unnamed_destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(unnamed_destination.mutate.is_none());
  assert_templex_name(unnamed_pattern.templex.as_ref().unwrap(), "Marine");
  assert!(unnamed_pattern.destructure.is_none());
}

#[test]
fn destructure_with_runed_capture() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ Muta[_ R]");
  let destination = pattern.destination.as_ref().unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  assert_templex_name(pattern.templex.as_ref().unwrap(), "Muta");
  let destructure = pattern.destructure.as_ref().unwrap();
  let unnamed_pattern = expect_1(&destructure.patterns);
  let unnamed_destination = unnamed_pattern.destination.as_ref().unwrap();
  assert!(matches!(
    unnamed_destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(unnamed_destination.mutate.is_none());
  assert_templex_name(unnamed_pattern.templex.as_ref().unwrap(), "R");
  assert!(unnamed_pattern.destructure.is_none());
}

