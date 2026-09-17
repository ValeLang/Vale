
use bumpalo::Bump;
use crate::cast;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::parsing::ast::{
  INameDeclarationP, ITemplexPT, MutabilityP, OwnershipP, PatternPP, VariabilityP,
};
use crate::parsing::tests::utils::{
  assert_templex_name, compile_pattern_expect, expect_1, expect_2,
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
fn ignoring_name() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ int");
  let destination = pattern.destination.unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  assert_templex_name(pattern.templex.as_ref().unwrap(), "int");
  assert!(pattern.destructure.is_none());
}

#[test]
fn static_sized_array() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ [#3]MutableStruct");
  let destination = pattern.destination.unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  let ssa = cast!(
    pattern.templex.as_ref().unwrap(),
    ITemplexPT::StaticSizedArray
  );
  assert_eq!(
    cast!(ssa.mutability, ITemplexPT::Mutability).1,
    MutabilityP::Mutable
  );
  assert_eq!(
    cast!(ssa.variability, ITemplexPT::Variability).1,
    VariabilityP::Final
  );
  assert_eq!(cast!(ssa.size, ITemplexPT::Int).value, 3);
  assert_templex_name(ssa.element, "MutableStruct");
  assert!(pattern.destructure.is_none());
}

#[test]
fn static_sized_array_with_imm() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ [#3]<imm>MutableStruct");
  let destination = pattern.destination.unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  let ssa = cast!(
    pattern.templex.as_ref().unwrap(),
    ITemplexPT::StaticSizedArray
  );
  assert_eq!(
    cast!(ssa.mutability, ITemplexPT::Mutability).1,
    MutabilityP::Immutable
  );
  assert_eq!(
    cast!(ssa.variability, ITemplexPT::Variability).1,
    VariabilityP::Final
  );
  assert_eq!(cast!(ssa.size, ITemplexPT::Int).value, 3);
  assert_templex_name(ssa.element, "MutableStruct");
  assert!(pattern.destructure.is_none());
}

#[test]
fn static_sized_array_with_imm_and_vary() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ [#3]<imm, vary>MutableStruct");
  let destination = pattern.destination.unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  let ssa = cast!(
    pattern.templex.as_ref().unwrap(),
    ITemplexPT::StaticSizedArray
  );
  assert_eq!(
    cast!(ssa.mutability, ITemplexPT::Mutability).1,
    MutabilityP::Immutable
  );
  assert_eq!(
    cast!(ssa.variability, ITemplexPT::Variability).1,
    VariabilityP::Varying
  );
  assert_eq!(cast!(ssa.size, ITemplexPT::Int).value, 3);
  assert_templex_name(ssa.element, "MutableStruct");
  assert!(pattern.destructure.is_none());
}

#[test]
fn runtime_sized_array() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ #[]int");
  let destination = pattern.destination.unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  let rsa = cast!(
    pattern.templex.as_ref().unwrap(),
    ITemplexPT::RuntimeSizedArray
  );
  assert_eq!(
    cast!(rsa.mutability, ITemplexPT::Mutability).1,
    MutabilityP::Immutable
  );
  assert_templex_name(rsa.element, "int");
  assert!(pattern.destructure.is_none());
}

#[test]
fn sequence_type() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ (int, bool)");
  let destination = pattern.destination.unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  let tuple = cast!(pattern.templex.as_ref().unwrap(), ITemplexPT::Tuple);
  let (int_t, bool_t) = expect_2(&tuple.elements);
  assert_templex_name(int_t, "int");
  assert_templex_name(bool_t, "bool");
  assert!(pattern.destructure.is_none());
}

#[test]
fn static_sized_array_with_borrow() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ &[#3]MutableStruct");
  let destination = pattern.destination.unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  let interpreted = cast!(pattern.templex.as_ref().unwrap(), ITemplexPT::Interpreted);
  assert_eq!(
    interpreted.maybe_ownership.unwrap().1,
    OwnershipP::Borrow
  );
  assert!(interpreted.maybe_region.is_none());
  let ssa = cast!(interpreted.inner, ITemplexPT::StaticSizedArray);
  assert_eq!(
    cast!(ssa.mutability, ITemplexPT::Mutability).1,
    MutabilityP::Mutable
  );
  assert_eq!(
    cast!(ssa.variability, ITemplexPT::Variability).1,
    VariabilityP::Final
  );
  assert_eq!(cast!(ssa.size, ITemplexPT::Int).value, 3);
  assert_templex_name(ssa.element, "MutableStruct");
  assert!(pattern.destructure.is_none());
}

#[test]
fn static_sized_array_with_weak() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ &&[#3]<_, _>MutableStruct");
  let destination = pattern.destination.unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  let interpreted = cast!(pattern.templex.as_ref().unwrap(), ITemplexPT::Interpreted);
  assert_eq!(
    interpreted.maybe_ownership.unwrap().1,
    OwnershipP::Weak
  );
  assert!(interpreted.maybe_region.is_none());
  let ssa = cast!(interpreted.inner, ITemplexPT::StaticSizedArray);
  cast!(ssa.mutability, ITemplexPT::AnonymousRune);
  cast!(ssa.variability, ITemplexPT::AnonymousRune);
  assert_eq!(cast!(ssa.size, ITemplexPT::Int).value, 3);
  assert_templex_name(ssa.element, "MutableStruct");
  assert!(pattern.destructure.is_none());
}

#[test]
fn call_type() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let pattern = compile(&parse_arena, &keywords, "_ MyOption<MyList<int>>");
  let destination = pattern.destination.unwrap();
  assert!(matches!(
    destination.decl,
    INameDeclarationP::IgnoredLocalNameDeclaration(_)
  ));
  assert!(destination.mutate.is_none());
  let myoption_call = cast!(pattern.templex.as_ref().unwrap(), ITemplexPT::Call);
  assert_templex_name(myoption_call.template, "MyOption");
  let mylist_type = expect_1(&myoption_call.args);
  let mylist_call = cast!(mylist_type, ITemplexPT::Call);
  assert_templex_name(mylist_call.template, "MyList");
  let int_type = expect_1(&mylist_call.args);
  assert_templex_name(int_type, "int");
  assert!(pattern.destructure.is_none());
}

