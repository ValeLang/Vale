// Run with: cargo test --manifest-path FrontendRust/Cargo.toml --lib parsing::tests::rules::rule_tests

use bumpalo::Bump;
use crate::cast;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::parsing::ast::*;
use crate::parsing::tests::traverse::NodeRefP;
use crate::parsing::tests::utils::*;
use crate::collect_only_rulex;

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
fn relations() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  {
    let rule = compile(&parse_arena, &keywords, "implements(MyObject, IObject)");
    let builtin = collect_only_rulex!(
      &rule,
      NodeRefP::Rulex(IRulexPR::BuiltinCall(builtin)) => Some(builtin)
    );
    assert_eq!(builtin.name.as_str(), "implements");
    let (myobject_, iobject_) = expect_2(&builtin.args);
    assert_templex_name(cast!(myobject_, IRulexPR::Templex), "MyObject");
    assert_templex_name(cast!(iobject_, IRulexPR::Templex), "IObject");
  }

  {
    let rule = compile(&parse_arena, &keywords, "implements(R, IObject)");
    let builtin = collect_only_rulex!(
      &rule,
      NodeRefP::Rulex(IRulexPR::BuiltinCall(builtin)) => Some(builtin)
    );
    assert_eq!(builtin.name.as_str(), "implements");
    let (r_, iobject_) = expect_2(&builtin.args);
    assert_templex_name(cast!(r_, IRulexPR::Templex), "R");
    assert_templex_name(cast!(iobject_, IRulexPR::Templex), "IObject");
  }

  {
    let rule = compile(&parse_arena, &keywords, "implements(MyObject, T)");
    let builtin = collect_only_rulex!(
      &rule,
      NodeRefP::Rulex(IRulexPR::BuiltinCall(builtin)) => Some(builtin)
    );
    assert_eq!(builtin.name.as_str(), "implements");
    let (myobject_, t_) = expect_2(&builtin.args);
    assert_templex_name(cast!(myobject_, IRulexPR::Templex), "MyObject");
    assert_templex_name(cast!(t_, IRulexPR::Templex), "T");
  }

  {
    let rule = compile(&parse_arena, &keywords, "exists(func +(T)int)");
    let builtin = collect_only_rulex!(
      &rule,
      NodeRefP::Rulex(IRulexPR::BuiltinCall(builtin)) => Some(builtin)
    );
    assert_eq!(builtin.name.as_str(), "exists");
    let func = cast!(cast!(expect_1(&builtin.args), IRulexPR::Templex), ITemplexPT::Func);
    assert_eq!(func.name.as_str(), "+");
    assert_templex_name(*expect_1(func.parameters), "T");
    assert_templex_name(func.return_type, "int");
  }
}

#[test]
fn super_complicated() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  compile(&parse_arena, &keywords, "C = any([#I]X, [#N]T)");
}

#[test]
fn destructure_prototype() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "Prot[_, _, T] = moo");
  let equals = collect_only_rulex!(&rule, NodeRefP::Rulex(IRulexPR::Equals(equals)) => Some(equals));
  let left = cast!(equals.left, IRulexPR::Components);
  assert_eq!(left.container, ITypePR::PrototypeType);
  let (first_, second_, t_) = expect_3(&left.components);
  cast!(cast!(first_, IRulexPR::Templex), ITemplexPT::AnonymousRune);
  cast!(cast!(second_, IRulexPR::Templex), ITemplexPT::AnonymousRune);
  assert_templex_name(cast!(t_, IRulexPR::Templex), "T");
  assert_templex_name(cast!(equals.right, IRulexPR::Templex), "moo");
}

#[test]
fn func() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "func moo()T");
  let func = collect_only_rulex!(&rule, NodeRefP::Templex(ITemplexPT::Func(func)) => Some(func));
  assert_eq!(func.name.as_str(), "moo");
  assert!(func.parameters.is_empty());
  assert_templex_name(func.return_type, "T");
}

#[test]
fn prototype_with_coords() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let rule = compile(&parse_arena, &keywords, "Prot[_, pack(int, bool), _]");
  let components = collect_only_rulex!(
    &rule,
    NodeRefP::Rulex(IRulexPR::Components(components)) => Some(components)
  );
  assert_eq!(components.container, ITypePR::PrototypeType);
  let (first_, pack_, third_) = expect_3(&components.components);
  cast!(cast!(first_, IRulexPR::Templex), ITemplexPT::AnonymousRune);
  let pack_call = cast!(pack_, IRulexPR::BuiltinCall);
  assert_eq!(pack_call.name.as_str(), "pack");
  let (int_, bool_) = expect_2(&pack_call.args);
  assert_templex_name(cast!(int_, IRulexPR::Templex), "int");
  assert_templex_name(cast!(bool_, IRulexPR::Templex), "bool");
  cast!(cast!(third_, IRulexPR::Templex), ITemplexPT::AnonymousRune);
}

