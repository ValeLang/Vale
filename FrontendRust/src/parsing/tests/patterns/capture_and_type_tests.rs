
use bumpalo::Bump;
use crate::cast;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::parsing::ast::{INameDeclarationP, ITemplexPT, OwnershipP, PatternPP};
use crate::parsing::tests::utils::{
  assert_destination_local_name, assert_templex_name, compile_pattern_expect,
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
fn no_capture_with_type() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ int");
  assert_templex_name(pattern.templex.as_ref().unwrap(), "int");
  assert!(pattern.destructure.is_none());
}

#[test]
fn capture_with_type() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "a int");
  assert_destination_local_name(pattern.destination.as_ref().unwrap(), "a");
  assert_templex_name(pattern.templex.as_ref().unwrap(), "int");
  assert!(pattern.destructure.is_none());
}

#[test]
fn simple_capture_with_tame() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "a T");
  assert_destination_local_name(pattern.destination.as_ref().unwrap(), "a");
  assert_templex_name(pattern.templex.as_ref().unwrap(), "T");
  assert!(pattern.destructure.is_none());
}

#[test]
fn capture_with_borrow_tame() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "arr &R");
  assert_destination_local_name(pattern.destination.as_ref().unwrap(), "arr");
  let interpreted = cast!(pattern.templex.as_ref().unwrap(), ITemplexPT::Interpreted);
  assert_eq!(
    interpreted.maybe_ownership.as_ref().unwrap().1,
    OwnershipP::Borrow
  );
  assert!(interpreted.maybe_region.is_none());
  assert_templex_name(interpreted.inner, "R");
  assert!(pattern.destructure.is_none());
}

#[test]
fn capture_with_self_in_front() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "self.arr &&R");
  let destination = pattern.destination.as_ref().unwrap();
  let member_name = cast!(
    &destination.decl,
    INameDeclarationP::ConstructingMemberNameDeclaration
  );
  assert_eq!(member_name.as_str(), "arr");
  assert!(destination.mutate.is_none());
  let interpreted = cast!(pattern.templex.as_ref().unwrap(), ITemplexPT::Interpreted);
  assert_eq!(
    interpreted.maybe_ownership.as_ref().unwrap().1,
    OwnershipP::Weak
  );
  assert!(interpreted.maybe_region.is_none());
  assert_templex_name(interpreted.inner, "R");
  assert!(pattern.destructure.is_none());
}

