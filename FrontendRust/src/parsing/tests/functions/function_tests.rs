// cargo test --manifest-path FrontendRust/Cargo.toml --lib parsing::tests::functions::function_tests

use bumpalo::Bump;
use crate::cast;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::lexing::errors::ParseError;
use crate::parsing::ast::*;
use crate::parsing::tests::utils::*;
use crate::parsing::tests::utils::{
  assert_destination_local_name, assert_templex_name, expect_1, expect_2, find_func_named,
};
#[test]
fn simple_function() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, "func main() { }");
  let function = find_func_named(&program, "main");
  assert!(function.header.attributes.is_empty());
  assert!(function.header.generic_parameters.is_none());
  assert!(function.header.template_rules.is_none());
  assert!(function.header.params.as_ref().unwrap().params.is_empty());
  assert!(function.header.ret.ret_type.is_none());
  let body = function.body.as_ref().unwrap();
  assert!(body.maybe_pure.is_none());
  assert!(body.maybe_default_region.is_none());
  assert!(matches!(body.inner, IExpressionPE::Void(_)));
}

#[test]
fn functions_with_weird_names() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, "func !=() { }");
  assert_eq!(program.denizens.len(), 1);
  find_func_named(&program, "!=");
  let program = compile(&parse_arena, &keywords, "func <=() { }");
  assert_eq!(program.denizens.len(), 1);
  find_func_named(&program, "<=");
  let program = compile(&parse_arena, &keywords, "func >=() { }");
  assert_eq!(program.denizens.len(), 1);
  find_func_named(&program, ">=");
  let program = compile(&parse_arena, &keywords, "func <() { }");
  assert_eq!(program.denizens.len(), 1);
  find_func_named(&program, "<");
  let program = compile(&parse_arena, &keywords, "func >() { }");
  assert_eq!(program.denizens.len(), 1);
  find_func_named(&program, ">");
  let program = compile(&parse_arena, &keywords, "func ==() { }");
  assert_eq!(program.denizens.len(), 1);
  find_func_named(&program, "==");
}

#[test]
fn function_then_struct() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(
    &parse_arena,
    &keywords,
    r#"
      exported func main() int {}

      struct mork { }
    "#,
  );
  assert_eq!(program.denizens.len(), 2);
  assert!(matches!(
    program.denizens[0],
    IDenizenP::TopLevelFunction(_)
  ));
  assert!(matches!(program.denizens[1], IDenizenP::TopLevelStruct(_)));
}

#[test]
fn simple_function_with_return() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(&parse_arena, &keywords, "func sum() int {3}");
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  assert_eq!(function.header.name.as_ref().unwrap().as_str(), "sum");
  assert!(function.header.attributes.is_empty());
  assert!(function.header.params.as_ref().unwrap().params.is_empty());
  assert_templex_name(function.header.ret.ret_type.as_ref().unwrap(), "int");
  let body = function.body.as_ref().unwrap();
  assert_eq!(
    cast!(body.inner, IExpressionPE::ConstantInt).value,
    3
  );
}

#[test]
fn pure_function() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(&parse_arena, &keywords, "pure func sum() {3}");
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  assert_eq!(function.header.name.as_ref().unwrap().as_str(), "sum");
  assert!(matches!(
    function.header.attributes,
    [IAttributeP::PureAttribute(_)]
  ));
  assert!(function.header.params.as_ref().unwrap().params.is_empty());
  assert!(function.header.ret.ret_type.is_none());
  let body = function.body.as_ref().unwrap();
  assert_eq!(
    cast!(body.inner, IExpressionPE::ConstantInt).value,
    3
  );
}

#[test]
fn extern_function() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, "extern func sum();");
  let function = find_func_named(&program, "sum");
  assert!(matches!(
    function.header.attributes,
    [IAttributeP::ExternAttribute(_)]
  ));
  assert!(function.header.params.as_ref().unwrap().params.is_empty());
  assert!(function.header.ret.ret_type.is_none());
  assert!(function.body.is_none());
}

#[test]
fn function_ending_with_set() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(
    &parse_arena,
    &keywords,
    r#"
      func moo() {
        set bork = value
      }
    "#,
  );
  cast!(denizen, IDenizenP::TopLevelFunction);
}

#[test]
fn extern_function_generated() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, r#"extern("bork") func sum();"#);
  let function = find_func_named(&program, "sum");
  let builtin = cast!(
    expect_1(&function.header.attributes),
    IAttributeP::BuiltinAttribute
  );
  assert_eq!(builtin.generator_name.as_str(), "bork");
  assert!(function.header.params.as_ref().unwrap().params.is_empty());
  assert!(function.header.ret.ret_type.is_none());
  assert!(function.body.is_none());
}

#[test]
fn extern_function_with_return() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, "extern func sum() int;");
  let function = find_func_named(&program, "sum");
  assert!(matches!(
    function.header.attributes,
    [IAttributeP::ExternAttribute(_)]
  ));
  assert!(function.header.params.as_ref().unwrap().params.is_empty());
  assert_templex_name(function.header.ret.ret_type.as_ref().unwrap(), "int");
  assert!(function.body.is_none());
}

#[test]
fn abstract_function() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(&parse_arena, &keywords, "abstract func sum();");
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  assert_eq!(function.header.name.as_ref().unwrap().as_str(), "sum");
  assert!(matches!(
    function.header.attributes,
    [IAttributeP::AbstractAttribute(_)]
  ));
  assert!(function.header.params.as_ref().unwrap().params.is_empty());
  assert!(function.header.ret.ret_type.is_none());
  assert!(function.body.is_none());
}

#[test]
fn pure_and_default_region() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(
    &parse_arena,
    &keywords,
    "pure func findNearbyUnits() i'int i'{ }",
  );
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  assert_eq!(
    function.header.name.as_ref().unwrap().as_str(),
    "findNearbyUnits"
  );
  assert!(matches!(
    function.header.attributes,
    [IAttributeP::PureAttribute(_)]
  ));
  assert!(function.header.params.as_ref().unwrap().params.is_empty());
  let ret_type = cast!(
    function.header.ret.ret_type.as_ref().unwrap(),
    ITemplexPT::Interpreted
  );
  assert!(ret_type.maybe_ownership.is_none());
  let ret_region = ret_type.maybe_region.as_ref().unwrap();
  assert_eq!(ret_region.name.as_ref().unwrap().as_str(), "i");
  assert_templex_name(ret_type.inner, "int");
  let body = function.body.as_ref().unwrap();
  let default_region = body.maybe_default_region.as_ref().unwrap();
  assert_eq!(default_region.name.as_ref().unwrap().as_str(), "i");
  assert!(matches!(body.inner, IExpressionPE::Void(_)));
}

#[test]
fn return_isolate() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(&parse_arena, &keywords, "func findNearbyUnits() 'int { }");
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  assert_eq!(
    function.header.name.as_ref().unwrap().as_str(),
    "findNearbyUnits"
  );
  assert!(function.header.attributes.is_empty());
  assert!(function.header.params.as_ref().unwrap().params.is_empty());
  let ret_type = cast!(
    function.header.ret.ret_type.as_ref().unwrap(),
    ITemplexPT::Interpreted
  );
  assert!(ret_type.maybe_ownership.is_none());
  assert!(ret_type.maybe_region.is_some());
  assert!(ret_type.maybe_region.as_ref().unwrap().name.is_none());
  assert_templex_name(ret_type.inner, "int");
}

#[test]
fn coord_generic_with_associated_region() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(
    &parse_arena,
    &keywords,
    "func findNearbyUnits<t', t'T>(x T) { }",
  );
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  assert_eq!(
    function.header.name.as_ref().unwrap().as_str(),
    "findNearbyUnits"
  );
  let generic_params = &function.header.generic_parameters.as_ref().unwrap().params;
  let (first_param, second_param) = expect_2(generic_params);
  assert_eq!(first_param.name.as_str(), "t");
  assert_eq!(
    first_param.maybe_type.as_ref().unwrap().tyype,
    ITypePR::RegionType
  );
  assert!(first_param.coord_region.is_none());
  assert!(first_param.attributes.is_empty());
  assert!(first_param.maybe_default.is_none());
  assert_eq!(second_param.name.as_str(), "T");
  assert!(second_param.maybe_type.is_none());
  assert_eq!(
    second_param
      .coord_region
      .as_ref()
      .unwrap()
      .name
      .as_ref()
      .unwrap()
      .str()
      .as_str(),
    "t"
  );
  assert!(second_param.attributes.is_empty());
  assert!(second_param.maybe_default.is_none());
}

#[test]
fn attribute_after_return() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(&parse_arena, &keywords, "abstract func sum() int;");
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  assert_eq!(function.header.name.as_ref().unwrap().as_str(), "sum");
  assert!(matches!(
    function.header.attributes,
    [IAttributeP::AbstractAttribute(_)]
  ));
  assert_templex_name(function.header.ret.ret_type.as_ref().unwrap(), "int");
  assert!(function.body.is_none());
}

#[test]
fn attribute_before_return() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(&parse_arena, &keywords, "abstract func sum() Int;");
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  assert_eq!(function.header.name.as_ref().unwrap().as_str(), "sum");
  assert!(matches!(
    function.header.attributes,
    [IAttributeP::AbstractAttribute(_)]
  ));
  assert_templex_name(function.header.ret.ret_type.as_ref().unwrap(), "Int");
  assert!(function.body.is_none());
}

#[test]
fn simple_function_with_identifying_rune() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(&parse_arena, &keywords, "func sum<A>(a A){a}");
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  let generic_param = expect_1(&function.header.generic_parameters.as_ref().unwrap().params);
  assert_eq!(generic_param.name.as_str(), "A");
  assert!(generic_param.maybe_type.is_none());
  assert!(generic_param.coord_region.is_none());
  assert!(generic_param.attributes.is_empty());
  assert!(generic_param.maybe_default.is_none());
}

#[test]
fn simple_function_with_coord_typed_identifying_rune() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(&parse_arena, &keywords, "func sum<A Ref>(a A){a}");
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  let generic_param = expect_1(&function.header.generic_parameters.as_ref().unwrap().params);
  assert_eq!(generic_param.name.as_str(), "A");
  assert_eq!(
    generic_param.maybe_type.as_ref().unwrap().tyype,
    ITypePR::CoordType
  );
  assert!(generic_param.coord_region.is_none());
  assert!(generic_param.attributes.is_empty());
  assert!(generic_param.maybe_default.is_none());
}

#[test]
fn simple_function_with_region_typed_identifying_rune() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(&parse_arena, &keywords, "func sum<a'>(){}");
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  let generic_param = expect_1(&function.header.generic_parameters.as_ref().unwrap().params);
  assert_eq!(generic_param.name.as_str(), "a");
  assert_eq!(
    generic_param.maybe_type.as_ref().unwrap().tyype,
    ITypePR::RegionType
  );
  assert!(generic_param.coord_region.is_none());
  assert!(generic_param.attributes.is_empty());
  assert!(generic_param.maybe_default.is_none());
}

#[test]
fn readonly_region_rune() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(&parse_arena, &keywords, "func sum<r' ro>(){}");
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  let generic_param = expect_1(&function.header.generic_parameters.as_ref().unwrap().params);
  assert_eq!(generic_param.name.as_str(), "r");
  assert_eq!(
    generic_param.maybe_type.as_ref().unwrap().tyype,
    ITypePR::RegionType
  );
  assert!(generic_param.coord_region.is_none());
  assert!(matches!(
    generic_param.attributes,
    [IRuneAttributeP::ReadOnlyRegionRuneAttribute(_)]
  ));
  assert!(generic_param.maybe_default.is_none());
}

#[test]
fn simple_function_with_apostrophe_region_typed_identifying_rune() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(&parse_arena, &keywords, "func sum<r'>(a &r'Marine){a}");
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  let generic_param = expect_1(&function.header.generic_parameters.as_ref().unwrap().params);
  assert_eq!(generic_param.name.as_str(), "r");
  assert_eq!(
    generic_param.maybe_type.as_ref().unwrap().tyype,
    ITypePR::RegionType
  );
  assert!(generic_param.coord_region.is_none());
  assert!(generic_param.attributes.is_empty());
  assert!(generic_param.maybe_default.is_none());
}

#[test]
fn pool_region() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(
    &parse_arena,
    &keywords,
    "func sum<r' = pool>(a &r'Marine){a}",
  );
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  let generic_param = expect_1(&function.header.generic_parameters.as_ref().unwrap().params);
  assert_eq!(generic_param.name.as_str(), "r");
  assert_eq!(
    generic_param.maybe_type.as_ref().unwrap().tyype,
    ITypePR::RegionType
  );
  assert!(generic_param.coord_region.is_none());
  assert!(generic_param.attributes.is_empty());
  assert_templex_name(generic_param.maybe_default.as_ref().unwrap(), "pool");
}

#[test]
fn pool_readonly_region() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(
    &parse_arena,
    &keywords,
    "func sum<r' ro = pool>(a &r'Marine){a}",
  );
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  let generic_param = expect_1(&function.header.generic_parameters.as_ref().unwrap().params);
  assert_eq!(generic_param.name.as_str(), "r");
  assert_eq!(
    generic_param.maybe_type.as_ref().unwrap().tyype,
    ITypePR::RegionType
  );
  assert!(generic_param.coord_region.is_none());
  assert!(matches!(
    generic_param.attributes,
    [IRuneAttributeP::ReadOnlyRegionRuneAttribute(_)]
  ));
  assert_templex_name(generic_param.maybe_default.as_ref().unwrap(), "pool");
}

#[test]
fn arena_region() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(
    &parse_arena,
    &keywords,
    "func sum<x' = arena>(a &x'Marine){a}",
  );
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  let generic_param = expect_1(&function.header.generic_parameters.as_ref().unwrap().params);
  assert_eq!(generic_param.name.as_str(), "x");
  assert_eq!(
    generic_param.maybe_type.as_ref().unwrap().tyype,
    ITypePR::RegionType
  );
  assert!(generic_param.coord_region.is_none());
  assert!(generic_param.attributes.is_empty());
  assert_templex_name(generic_param.maybe_default.as_ref().unwrap(), "arena");
}

#[test]
fn readonly_region() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(&parse_arena, &keywords, "func sum<x'>(a &x'Marine){a}");
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  let generic_param = expect_1(&function.header.generic_parameters.as_ref().unwrap().params);
  assert_eq!(generic_param.name.as_str(), "x");
  assert_eq!(
    generic_param.maybe_type.as_ref().unwrap().tyype,
    ITypePR::RegionType
  );
  assert!(generic_param.coord_region.is_none());
  assert!(generic_param.attributes.is_empty());
  assert!(generic_param.maybe_default.is_none());
}

#[test]
fn virtual_function() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(
    &parse_arena,
    &keywords,
    "func doCivicDance(virtual this Car) int;",
  );
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  assert_eq!(
    function.header.name.as_ref().unwrap().as_str(),
    "doCivicDance"
  );
  assert!(function.header.attributes.is_empty());
  let param = expect_1(&function.header.params.as_ref().unwrap().params);
  assert!(matches!(param.virtuality.as_ref(), Some(AbstractP { .. })));
  let pattern = param.pattern.as_ref().unwrap();
  assert_destination_local_name(pattern.destination.as_ref().unwrap(), "this");
  assert_templex_name(pattern.templex.as_ref().unwrap(), "Car");
  assert_templex_name(function.header.ret.ret_type.as_ref().unwrap(), "int");
  assert!(function.body.is_none());
}

#[test]
fn bad_thing_for_body() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let err = compile_denizen(
    &parse_arena,
    &keywords,
    r#"
      func doCivicDance(virtual this Car) moo blork
    "#,
  )
  .unwrap_err();
  assert!(matches!(err, ParseError::BadFunctionBodyError(_)));
}

#[test]
fn function_with_parameter_and_return() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, "func main(moo T) T { }");
  let function = find_func_named(&program, "main");
  let param = expect_1(&function.header.params.as_ref().unwrap().params);
  let pattern = param.pattern.as_ref().unwrap();
  assert_destination_local_name(pattern.destination.as_ref().unwrap(), "moo");
  assert_templex_name(pattern.templex.as_ref().unwrap(), "T");
  assert_templex_name(function.header.ret.ret_type.as_ref().unwrap(), "T");
  assert!(matches!(
    function.body.as_ref().unwrap().inner,
    IExpressionPE::Void(_)
  ));
}

#[test]
fn function_with_generics() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, "func main<T>() { }");
  let function = find_func_named(&program, "main");
  assert!(function.header.attributes.is_empty());
  assert!(function.header.template_rules.is_none());
  let generic_param = expect_1(&function.header.generic_parameters.as_ref().unwrap().params);
  assert_eq!(generic_param.name.as_str(), "T");
  assert!(generic_param.maybe_type.is_none());
  assert!(generic_param.coord_region.is_none());
  assert!(generic_param.attributes.is_empty());
  assert!(generic_param.maybe_default.is_none());
}

#[test]
fn impl_function() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(
    &parse_arena,
    &keywords,
    "func maxHp(virtual this Marine) { return 5; }",
  );
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  assert_eq!(function.header.name.as_ref().unwrap().as_str(), "maxHp");
  let param = expect_1(&function.header.params.as_ref().unwrap().params);
  assert!(matches!(param.virtuality.as_ref(), Some(AbstractP { .. })));
  let pattern = param.pattern.as_ref().unwrap();
  assert_destination_local_name(pattern.destination.as_ref().unwrap(), "this");
  assert_templex_name(pattern.templex.as_ref().unwrap(), "Marine");
  assert!(function.header.ret.ret_type.is_none());
  assert!(function.body.is_some());
}

#[test]
fn param() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(&parse_arena, &keywords, "func call(f F){f()}");
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  let param = expect_1(&function.header.params.as_ref().unwrap().params);
  let pattern = param.pattern.as_ref().unwrap();
  assert_destination_local_name(pattern.destination.as_ref().unwrap(), "f");
  assert_templex_name(pattern.templex.as_ref().unwrap(), "F");
}

#[test]
fn func_with_rules() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(&parse_arena, &keywords, "func sum () where X Int {3}");
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  assert_eq!(function.header.name.as_ref().unwrap().as_str(), "sum");
  assert!(function.header.attributes.is_empty());
  assert!(function.header.generic_parameters.is_none());
  assert!(function.header.template_rules.is_some());
  assert!(function.header.params.is_some());
  assert!(function.header.ret.ret_type.is_none());
  let body = function.body.as_ref().unwrap();
  assert_eq!(
    cast!(body.inner, IExpressionPE::ConstantInt).value,
    3
  );
}

#[test]
fn func_with_func_bound() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(
    &parse_arena,
    &keywords,
    "func sum<T>() where func moo(&T)void {3}",
  );
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  let template_rules = function.header.template_rules.as_ref().unwrap();
  let first_rule = expect_1(&template_rules.rules);
  let first_rule_templex = cast!(first_rule, IRulexPR::Templex);
  let function_bound = cast!(first_rule_templex, ITemplexPT::Func);
  assert_eq!(function_bound.name.as_str(), "moo");
  let first_param = expect_1(&function_bound.parameters);
  let interpreted = cast!(first_param, ITemplexPT::Interpreted);
  assert_eq!(
    interpreted.maybe_ownership.as_ref().unwrap().1,
    OwnershipP::Borrow
  );
  assert!(interpreted.maybe_region.is_none());
  assert_templex_name(interpreted.inner, "T");
  assert_templex_name(function_bound.return_type, "void");
}

#[test]
fn identifying_runes() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(&parse_arena, &keywords, "func wrap<A, F>(a A) { }");
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  let (a_rune, f_rune) = expect_2(&function.header.generic_parameters.as_ref().unwrap().params);
  assert_eq!(a_rune.name.as_str(), "A");
  assert!(a_rune.maybe_type.is_none());
  assert!(a_rune.coord_region.is_none());
  assert!(a_rune.attributes.is_empty());
  assert!(a_rune.maybe_default.is_none());
  assert_eq!(f_rune.name.as_str(), "F");
  assert!(f_rune.maybe_type.is_none());
  assert!(f_rune.coord_region.is_none());
  assert!(f_rune.attributes.is_empty());
  assert!(f_rune.maybe_default.is_none());
  let param = expect_1(&function.header.params.as_ref().unwrap().params);
  let pattern = param.pattern.as_ref().unwrap();
  assert_destination_local_name(pattern.destination.as_ref().unwrap(), "a");
  assert_templex_name(pattern.templex.as_ref().unwrap(), "A");
}

#[test]
fn never_signature() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(&parse_arena, &keywords, "func __vbi_panic() __Never {}");
  let function = cast!(denizen, IDenizenP::TopLevelFunction);
  assert_templex_name(function.header.ret.ret_type.as_ref().unwrap(), "__Never");
}

#[test]
fn should_require_identifying_runes() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let err = compile_denizen(
    &parse_arena,
    &keywords,
    r#"
      func do(callable) int {callable()}
    "#,
  )
  .unwrap_err();
  assert!(matches!(
    err,
    ParseError::LightFunctionMustHaveParamTypes { param_index: 0, .. }
  ));
}

#[test]
fn short_self() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let denizen = compile_denizen_expect(
    &parse_arena,
    &keywords,
    r#"
      interface IMoo {
        func moo(&self) {}
      }
    "#,
  );
  let interface = cast!(denizen, IDenizenP::TopLevelInterface);
  assert_eq!(interface.name.as_str(), "IMoo");
  let function = &interface.members[0];
  assert_eq!(function.header.name.as_ref().unwrap().as_str(), "moo");
  let param = expect_1(&function.header.params.as_ref().unwrap().params);
  assert!(param.virtuality.is_none());
  assert!(param.self_borrow.is_some());
  assert!(param.pattern.is_none());
}

