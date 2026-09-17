// Run with: cargo test --manifest-path FrontendRust/Cargo.toml --lib parsing::tests::rules::coord_rule_tests

use bumpalo::Bump;
use crate::cast;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::parsing::ast::*;
use crate::parsing::tests::utils::*;

fn compile<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> IRulexPR<'p>
where
  'p: 'ctx,
{
  compile_rulex_expect(parse_arena, keywords, code)
}

#[test]
fn empty_coord_rule() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "_ Ref");
  let typed = cast!(rule, IRulexPR::Typed);
  assert!(typed.rune.is_none());
  assert_eq!(typed.tyype, ITypePR::CoordType);
}

#[test]
fn coord_with_rune() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "T Ref");
  let typed = cast!(rule, IRulexPR::Typed);
  assert_eq!(typed.rune.as_ref().unwrap().as_str(), "T");
  assert_eq!(typed.tyype, ITypePR::CoordType);
}

#[test]
fn coord_with_destructure_only() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "Ref[_, _, _]");
  let components = cast!(rule, IRulexPR::Components);
  assert_eq!(components.container, ITypePR::CoordType);
  let (first, second, third) = expect_3(&components.components);
  assert!(matches!(cast!(first, IRulexPR::Templex), ITemplexPT::AnonymousRune(_)));
  assert!(matches!(cast!(second, IRulexPR::Templex), ITemplexPT::AnonymousRune(_)));
  assert!(matches!(cast!(third, IRulexPR::Templex), ITemplexPT::AnonymousRune(_)));
}

#[test]
fn coord_with_rune_and_destructure() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "T = Ref[_, _, _]");
  let equals = cast!(rule, IRulexPR::Equals);
  assert_templex_name(cast!(equals.left, IRulexPR::Templex), "T");
  let components = cast!(equals.right, IRulexPR::Components);
  assert_eq!(components.container, ITypePR::CoordType);
  let (first, second, third) = expect_3(&components.components);
  assert!(matches!(cast!(first, IRulexPR::Templex), ITemplexPT::AnonymousRune(_)));
  assert!(matches!(cast!(second, IRulexPR::Templex), ITemplexPT::AnonymousRune(_)));
  assert!(matches!(cast!(third, IRulexPR::Templex), ITemplexPT::AnonymousRune(_)));

  let rule = compile(&parse_arena, &keywords, "T = Ref[own, _, _]");
  let equals = cast!(rule, IRulexPR::Equals);
  assert_templex_name(cast!(equals.left, IRulexPR::Templex), "T");
  let components = cast!(equals.right, IRulexPR::Components);
  assert_eq!(components.container, ITypePR::CoordType);
  let (first, second, third) = expect_3(&components.components);
  let first_templex = cast!(first, IRulexPR::Templex);
  let ownership = cast!(first_templex, ITemplexPT::Ownership);
  assert_eq!(ownership.1, OwnershipP::Own);
  assert!(matches!(cast!(second, IRulexPR::Templex), ITemplexPT::AnonymousRune(_)));
  assert!(matches!(cast!(third, IRulexPR::Templex), ITemplexPT::AnonymousRune(_)));
}

#[test]
fn coord_matches_plain_int() {
  // Coord can do this because I want to be able to say:
  //   func moo
  //   where #T = (Int):Void
  //   (a: #T)
  // instead of:
  //   func moo
  //   rules(
  //     Ref#T[_, Ref[_, Int]]:Ref[_, Void]))
  //   (a: #T)
  // Note from later: I think this is an anachronism, this doesn't test
  // anything with coords.
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "int");
  let templex = cast!(rule, IRulexPR::Templex);
  assert_templex_name(&templex, "int");
}

#[test]
fn coord_with_int_in_kind_rule() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "Ref[_, _, int]");
  let components = cast!(rule, IRulexPR::Components);
  assert_eq!(components.container, ITypePR::CoordType);
  let (first, second, third) = expect_3(&components.components);
  assert!(matches!(cast!(first, IRulexPR::Templex), ITemplexPT::AnonymousRune(_)));
  assert!(matches!(cast!(second, IRulexPR::Templex), ITemplexPT::AnonymousRune(_)));
  assert_templex_name(cast!(third, IRulexPR::Templex), "int");
}

#[test]
fn coord_with_specific_kind_rule() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "Ref[_, _, Kind[mut]]");
  let components = cast!(rule, IRulexPR::Components);
  assert_eq!(components.container, ITypePR::CoordType);
  let (first, second, third) = expect_3(&components.components);
  assert!(matches!(cast!(first, IRulexPR::Templex), ITemplexPT::AnonymousRune(_)));
  assert!(matches!(cast!(second, IRulexPR::Templex), ITemplexPT::AnonymousRune(_)));
  let kind_components = cast!(third, IRulexPR::Components);
  assert_eq!(kind_components.container, ITypePR::KindType);
  let mutability_rule = expect_1(&kind_components.components);
  let mutability = cast!(cast!(mutability_rule, IRulexPR::Templex), ITemplexPT::Mutability);
  assert_eq!(mutability.1, MutabilityP::Mutable);
}

#[test]
fn coord_with_value() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "T Ref = int");
  let equals = cast!(rule, IRulexPR::Equals);
  let typed = cast!(equals.left, IRulexPR::Typed);
  assert_eq!(typed.rune.as_ref().unwrap().as_str(), "T");
  assert_eq!(typed.tyype, ITypePR::CoordType);
  assert_templex_name(cast!(equals.right, IRulexPR::Templex), "int");
}

#[test]
fn coord_with_destructure_and_value() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "Ref[_, _, _] = int");
  let equals = cast!(rule, IRulexPR::Equals);
  let components = cast!(equals.left, IRulexPR::Components);
  assert_eq!(components.container, ITypePR::CoordType);
  let (first, second, third) = expect_3(&components.components);
  assert!(matches!(cast!(first, IRulexPR::Templex), ITemplexPT::AnonymousRune(_)));
  assert!(matches!(cast!(second, IRulexPR::Templex), ITemplexPT::AnonymousRune(_)));
  assert!(matches!(cast!(third, IRulexPR::Templex), ITemplexPT::AnonymousRune(_)));
  assert_templex_name(cast!(equals.right, IRulexPR::Templex), "int");
}

#[test]
fn coord_with_sequence_in_value_spot() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "T Ref = (int, bool)");
  let equals = cast!(rule, IRulexPR::Equals);
  let typed = cast!(equals.left, IRulexPR::Typed);
  assert_eq!(typed.rune.as_ref().unwrap().as_str(), "T");
  assert_eq!(typed.tyype, ITypePR::CoordType);
  let tuple = cast!(cast!(equals.right, IRulexPR::Templex), ITemplexPT::Tuple);
  let (int_, bool_) = expect_2(&tuple.elements);
  assert_templex_name(int_, "int");
  assert_templex_name(bool_, "bool");
}

#[test]
fn lone_tuple_is_sequence() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "(int, bool)");
  let tuple = cast!(cast!(&rule, IRulexPR::Templex), ITemplexPT::Tuple);
  let (int_, bool_) = expect_2(&tuple.elements);
  assert_templex_name(int_, "int");
  assert_templex_name(bool_, "bool");
}

