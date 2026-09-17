// Run with: cargo test --manifest-path FrontendRust/Cargo.toml --lib parsing::tests::rules::kind_rule_tests
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
fn empty_kind_rule() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "_ Kind");
  let typed = cast!(rule, IRulexPR::Typed);
  assert!(typed.rune.is_none());
  assert_eq!(typed.tyype, ITypePR::KindType);
}

#[test]
fn kind_with_rune() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "T Kind");
  let typed = cast!(rule, IRulexPR::Typed);
  assert_eq!(typed.rune.as_ref().unwrap().as_str(), "T");
  assert_eq!(typed.tyype, ITypePR::KindType);
}

#[test]
fn kind_with_destructure_only() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "Kind[_]");
  let components = cast!(rule, IRulexPR::Components);
  assert_eq!(components.container, ITypePR::KindType);
  let only_component = cast!(expect_1(&components.components), IRulexPR::Templex);
  cast!(only_component, ITemplexPT::AnonymousRune);
}

#[test]
fn kind_matches_plain_int() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "int");
  let templex = cast!(rule, IRulexPR::Templex);
  assert_templex_name(&templex, "int");
}

#[test]
fn kind_with_value() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "T Kind = int");
  let equals = cast!(rule, IRulexPR::Equals);
  let left = cast!(equals.left, IRulexPR::Typed);
  assert_eq!(left.rune.as_ref().unwrap().as_str(), "T");
  assert_eq!(left.tyype, ITypePR::KindType);
  let right = cast!(equals.right, IRulexPR::Templex);
  assert_templex_name(right, "int");
}

#[test]
fn kind_with_sequence_in_value_spot() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "T Kind = (int, bool)");
  let equals = cast!(rule, IRulexPR::Equals);
  let left = cast!(equals.left, IRulexPR::Typed);
  assert_eq!(left.rune.as_ref().unwrap().as_str(), "T");
  assert_eq!(left.tyype, ITypePR::KindType);
  let right = cast!(equals.right, IRulexPR::Templex);
  let tuple = cast!(right, ITemplexPT::Tuple);
  let (int_, bool_) = expect_2(&tuple.elements);
  assert_templex_name(int_, "int");
  assert_templex_name(bool_, "bool");
}

#[test]
fn lone_sequence() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "(int, bool)");
  let templex = cast!(rule, IRulexPR::Templex);
  let tuple = cast!(templex, ITemplexPT::Tuple);
  let (int_, bool_) = expect_2(&tuple.elements);
  assert_templex_name(int_, "int");
  assert_templex_name(bool_, "bool");
}

#[test]
fn templated_struct_one_arg() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "Moo<int>");
  let templex = cast!(rule, IRulexPR::Templex);
  let call = cast!(templex, ITemplexPT::Call);
  assert_templex_name(call.template, "Moo");
  let arg = expect_1(&call.args);
  assert_templex_name(arg, "int");

  let rule = compile(&parse_arena, &keywords, "Moo<@int>");
  let templex = cast!(rule, IRulexPR::Templex);
  let call = cast!(templex, ITemplexPT::Call);
  assert_templex_name(call.template, "Moo");
  let arg = expect_1(&call.args);
  let interpreted = cast!(arg, ITemplexPT::Interpreted);
  assert_eq!(
    interpreted.maybe_ownership.unwrap().1,
    OwnershipP::Share
  );
  assert!(interpreted.maybe_region.is_none());
  assert_templex_name(interpreted.inner, "int");
}

#[test]
fn rwkilc() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "List<int>");
  let templex = cast!(rule, IRulexPR::Templex);
  let call = cast!(templex, ITemplexPT::Call);
  assert_templex_name(call.template, "List");
  let arg = expect_1(&call.args);
  assert_templex_name(arg, "int");

  let rule = compile(&parse_arena, &keywords, "K Int");
  let typed = cast!(rule, IRulexPR::Typed);
  assert_eq!(typed.rune.as_ref().unwrap().as_str(), "K");
  assert_eq!(typed.tyype, ITypePR::IntType);

  let rule = compile(&parse_arena, &keywords, "K<int>");
  let templex = cast!(rule, IRulexPR::Templex);
  let call = cast!(templex, ITemplexPT::Call);
  assert_templex_name(call.template, "K");
  let arg = expect_1(&call.args);
  assert_templex_name(arg, "int");
}

#[test]
fn templated_struct_rune_arg() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "Moo<R>");
  let templex = cast!(rule, IRulexPR::Templex);
  let call = cast!(templex, ITemplexPT::Call);
  assert_templex_name(call.template, "Moo");
  let arg = expect_1(&call.args);
  assert_templex_name(arg, "R");
}

#[test]
fn templated_struct_multiple_args() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "Moo<int, str>");
  let templex = cast!(rule, IRulexPR::Templex);
  let call = cast!(templex, ITemplexPT::Call);
  assert_templex_name(call.template, "Moo");
  let (int_, str_) = expect_2(&call.args);
  assert_templex_name(int_, "int");
  assert_templex_name(str_, "str");
}

#[test]
fn templated_struct_arg_is_another_templated_struct_with_one_arg() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "Moo<Blarg<int>>");
  let templex = cast!(rule, IRulexPR::Templex);
  let call = cast!(templex, ITemplexPT::Call);
  assert_templex_name(call.template, "Moo");
  let nested = cast!(expect_1(&call.args), ITemplexPT::Call);
  assert_templex_name(nested.template, "Blarg");
  let arg = expect_1(&nested.args);
  assert_templex_name(arg, "int");
}

#[test]
fn templated_struct_arg_is_another_templated_struct_with_multiple_arg() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "Moo<Blarg<int, str>>");
  let templex = cast!(rule, IRulexPR::Templex);
  let call = cast!(templex, ITemplexPT::Call);
  assert_templex_name(call.template, "Moo");
  let nested = cast!(expect_1(&call.args), ITemplexPT::Call);
  assert_templex_name(nested.template, "Blarg");
  let (int_, str_) = expect_2(&nested.args);
  assert_templex_name(int_, "int");
  assert_templex_name(str_, "str");
}

#[test]
fn static_sized_array() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let array = cast!(
    compile_templex_expect(&parse_arena, &keywords, "[#_]_"),
    ITemplexPT::StaticSizedArray
  );
  assert_eq!(
    cast!(array.mutability, ITemplexPT::Mutability).1,
    MutabilityP::Mutable
  );
  assert_eq!(
    cast!(array.variability, ITemplexPT::Variability).1,
    VariabilityP::Final
  );
  cast!(array.size, ITemplexPT::AnonymousRune);
  cast!(array.element, ITemplexPT::AnonymousRune);

  let array = cast!(
    compile_templex_expect(&parse_arena, &keywords, "[#_]<imm>_"),
    ITemplexPT::StaticSizedArray
  );
  assert_eq!(
    cast!(array.mutability, ITemplexPT::Mutability).1,
    MutabilityP::Immutable
  );
  assert_eq!(
    cast!(array.variability, ITemplexPT::Variability).1,
    VariabilityP::Final
  );
  cast!(array.size, ITemplexPT::AnonymousRune);
  cast!(array.element, ITemplexPT::AnonymousRune);

  let array = cast!(
    compile_templex_expect(&parse_arena, &keywords, "[#3]int"),
    ITemplexPT::StaticSizedArray
  );
  assert_eq!(
    cast!(array.mutability, ITemplexPT::Mutability).1,
    MutabilityP::Mutable
  );
  assert_eq!(
    cast!(array.variability, ITemplexPT::Variability).1,
    VariabilityP::Final
  );
  assert_eq!(cast!(array.size, ITemplexPT::Int).value, 3);
  assert_templex_name(array.element, "int");

  let array = cast!(
    compile_templex_expect(&parse_arena, &keywords, "[#N]int"),
    ITemplexPT::StaticSizedArray
  );
  assert_eq!(
    cast!(array.mutability, ITemplexPT::Mutability).1,
    MutabilityP::Mutable
  );
  assert_eq!(
    cast!(array.variability, ITemplexPT::Variability).1,
    VariabilityP::Final
  );
  assert_templex_name(array.size, "N");
  assert_templex_name(array.element, "int");

  let array = cast!(
    compile_templex_expect(&parse_arena, &keywords, "[#_]int"),
    ITemplexPT::StaticSizedArray
  );
  assert_eq!(
    cast!(array.mutability, ITemplexPT::Mutability).1,
    MutabilityP::Mutable
  );
  assert_eq!(
    cast!(array.variability, ITemplexPT::Variability).1,
    VariabilityP::Final
  );
  cast!(array.size, ITemplexPT::AnonymousRune);
  assert_templex_name(array.element, "int");

  let array = cast!(
    compile_templex_expect(&parse_arena, &keywords, "[#N]T"),
    ITemplexPT::StaticSizedArray
  );
  assert_eq!(
    cast!(array.mutability, ITemplexPT::Mutability).1,
    MutabilityP::Mutable
  );
  assert_eq!(
    cast!(array.variability, ITemplexPT::Variability).1,
    VariabilityP::Final
  );
  assert_templex_name(array.size, "N");
  assert_templex_name(array.element, "T");
}

#[test]
fn regular_sequence() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let tuple = cast!(
    compile_templex_expect(&parse_arena, &keywords, "()"),
    ITemplexPT::Tuple
  );
  assert_eq!(tuple.elements.len(), 0);

  let tuple = cast!(
    compile_templex_expect(&parse_arena, &keywords, "(int)"),
    ITemplexPT::Tuple
  );
  assert_templex_name(*expect_1(tuple.elements), "int");

  let tuple = cast!(
    compile_templex_expect(&parse_arena, &keywords, "(int, bool)"),
    ITemplexPT::Tuple
  );
  let (int_, bool_) = expect_2(tuple.elements);
  assert_templex_name(int_, "int");
  assert_templex_name(bool_, "bool");

  let tuple = cast!(
    compile_templex_expect(&parse_arena, &keywords, "(_, bool)"),
    ITemplexPT::Tuple
  );
  let (anonymous_, bool_) = expect_2(tuple.elements);
  cast!(anonymous_, ITemplexPT::AnonymousRune);
  assert_templex_name(bool_, "bool");

  let tuple = cast!(
    compile_templex_expect(&parse_arena, &keywords, "(_, _)"),
    ITemplexPT::Tuple
  );
  let (anonymous1_, anonymous2_) = expect_2(tuple.elements);
  cast!(anonymous1_, ITemplexPT::AnonymousRune);
  cast!(anonymous2_, ITemplexPT::AnonymousRune);
}

#[test]
fn prototype_kind_rule() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let templex = compile_templex_expect(&parse_arena, &keywords, "func moo(int)void");
  let prototype = cast!(templex, ITemplexPT::Func);
  assert_eq!(prototype.name.as_str(), "moo");
  assert_templex_name(*expect_1(prototype.parameters), "int");
  assert_templex_name(prototype.return_type, "void");

  let templex = compile_templex_expect(&parse_arena, &keywords, "func moo(T)R");
  let prototype = cast!(templex, ITemplexPT::Func);
  assert_eq!(prototype.name.as_str(), "moo");
  assert_templex_name(*expect_1(prototype.parameters), "T");
  assert_templex_name(prototype.return_type, "R");
}
