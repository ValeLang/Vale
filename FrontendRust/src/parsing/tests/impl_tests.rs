
use bumpalo::Bump;
use crate::cast;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::parsing::ast::*;
use crate::parsing::tests::utils::*;

#[test]
fn normal_impl() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let file = compile(&parse_arena, &keywords, "impl MyInterface for SomeStruct;");
  let denizen = expect_1(&file.denizens);
  let impl_ = cast!(denizen, IDenizenP::TopLevelImpl);

  assert!(impl_.generic_params.is_none());
  assert!(impl_.template_rules.is_none());
  assert_templex_name(impl_.struct_.as_ref().unwrap(), "SomeStruct");
  assert_templex_name(&impl_.interface, "MyInterface");
  assert_eq!(impl_.attributes.len(), 0);
}

#[test]
fn templated_impl() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let file = compile(&parse_arena, &keywords, "impl<T> MyInterface<T> for SomeStruct<T>;");
  let denizen = expect_1(&file.denizens);
  let impl_ = cast!(denizen, IDenizenP::TopLevelImpl);

  let generic_params = impl_.generic_params.as_ref().unwrap();
  let generic_param = expect_1(&generic_params.params);
  assert_eq!(generic_param.name.as_str(), "T");
  assert!(generic_param.maybe_type.is_none());
  assert!(generic_param.coord_region.is_none());
  assert_eq!(generic_param.attributes.len(), 0);
  assert!(generic_param.maybe_default.is_none());
  assert!(impl_.template_rules.is_none());

  let struct_ = cast!(impl_.struct_.as_ref().unwrap(), ITemplexPT::Call);
  assert_templex_name(struct_.template, "SomeStruct");
  let struct_template_arg = expect_1(&struct_.args);
  assert_templex_name(struct_template_arg, "T");

  let interface = cast!(&impl_.interface, ITemplexPT::Call);
  assert_templex_name(interface.template, "MyInterface");
  let interface_template_arg = expect_1(&interface.args);
  assert_templex_name(interface_template_arg, "T");
  assert_eq!(impl_.attributes.len(), 0);
}

#[test]
fn impling_a_template_call() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let file = compile(&parse_arena, &keywords, "impl IFunction1<mut, int, int> for MyIntIdentity;");
  let denizen = expect_1(&file.denizens);
  let impl_ = cast!(denizen, IDenizenP::TopLevelImpl);

  assert!(impl_.generic_params.is_none());
  assert!(impl_.template_rules.is_none());
  assert_templex_name(impl_.struct_.as_ref().unwrap(), "MyIntIdentity");

  let interface = cast!(&impl_.interface, ITemplexPT::Call);
  assert_templex_name(interface.template, "IFunction1");
  let (mutability_arg, int_arg1, int_arg2) = expect_3(&interface.args);
  assert_eq!(
    cast!(mutability_arg, ITemplexPT::Mutability).1,
    MutabilityP::Mutable
  );
  assert_templex_name(int_arg1, "int");
  assert_templex_name(int_arg2, "int");
  assert_eq!(impl_.attributes.len(), 0);
}
