
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
fn simple_int() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ int");
  let destination = pattern.destination.as_ref().unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  assert_templex_name(pattern.templex.as_ref().unwrap(), "int");
  assert!(pattern.destructure.is_none());
}

#[test]
fn name_only_capture() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "a");
  let destination = pattern.destination.as_ref().unwrap();
  assert_destination_local_name(destination, "a");
  assert!(destination.mutate.is_none());
  assert!(pattern.templex.is_none());
  assert!(pattern.destructure.is_none());
}

#[test]
fn empty_pattern() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_");
  let destination = pattern.destination.as_ref().unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  assert!(pattern.templex.is_none());
  assert!(pattern.destructure.is_none());
}

#[test]
fn capture_with_type_with_destructure() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "a Moo[a, b]");
  let destination = pattern.destination.as_ref().unwrap();
  assert_destination_local_name(destination, "a");
  assert!(destination.mutate.is_none());
  assert_templex_name(pattern.templex.as_ref().unwrap(), "Moo");
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
fn cstodts() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "moo T[a int]");
  let destination = pattern.destination.as_ref().unwrap();
  assert_destination_local_name(destination, "moo");
  assert!(destination.mutate.is_none());
  assert_templex_name(pattern.templex.as_ref().unwrap(), "T");
  let destructure = pattern.destructure.as_ref().unwrap();
  let a_pattern = expect_1(&destructure.patterns);
  let a_destination = a_pattern.destination.as_ref().unwrap();
  assert_destination_local_name(a_destination, "a");
  assert!(a_destination.mutate.is_none());
  assert_templex_name(a_pattern.templex.as_ref().unwrap(), "int");
  assert!(a_pattern.destructure.is_none());
}

#[test]
fn capture_with_destructure_with_type_outside() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "a (int, bool)[a, b]");
  let destination = pattern.destination.as_ref().unwrap();
  assert_destination_local_name(destination, "a");
  assert!(destination.mutate.is_none());
  let tuple = cast!(pattern.templex.as_ref().unwrap(), ITemplexPT::Tuple);
  let (int_t, bool_t) = expect_2(&tuple.elements);
  assert_templex_name(int_t, "int");
  assert_templex_name(bool_t, "bool");
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

