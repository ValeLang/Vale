// Run with: cargo test --manifest-path FrontendRust/Cargo.toml --lib parsing::tests::if_tests

use bumpalo::Bump;
use crate::cast;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::parsing::ast::*;
use crate::parsing::tests::utils::*;

#[test]
fn ifs() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "if true { doBlarks(&x) } else { }");
  let if_ = cast!(expr, IExpressionPE::If);

  let condition = cast!(if_.condition, IExpressionPE::ConstantBool);
  assert!(condition.value);

  assert!(if_.then_body.maybe_pure.is_none());
  assert!(if_.then_body.maybe_default_region.is_none());
  let then_call = cast!(if_.then_body.inner, IExpressionPE::FunctionCall);
  assert_lookup_name(then_call.callable_expr, "doBlarks");
  let first_arg = expect_1(&then_call.arg_exprs);
  let borrow_x = cast!(first_arg, IExpressionPE::Augment);
  assert_eq!(borrow_x.target_ownership, OwnershipP::Borrow);
  assert_lookup_name(borrow_x.inner, "x");

  assert!(if_.else_body.maybe_pure.is_none());
  assert!(if_.else_body.maybe_default_region.is_none());
  cast!(if_.else_body.inner, IExpressionPE::Void);
}

#[test]
fn if_let() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "if [u] = a {}");
  let if_ = cast!(expr, IExpressionPE::If);

  let let_ = cast!(if_.condition, IExpressionPE::Let);
  assert!(let_.pattern.destination.is_none());
  assert!(let_.pattern.templex.is_none());
  let destructure = let_.pattern.destructure.as_ref().unwrap();
  let u_pattern = expect_1(&destructure.patterns);
  let u_destination = u_pattern.destination.as_ref().unwrap();
  assert_destination_local_name(u_destination, "u");
  assert!(u_pattern.templex.is_none());
  assert!(u_pattern.destructure.is_none());
  assert_lookup_name(let_.source, "a");

  assert!(if_.then_body.maybe_pure.is_none());
  assert!(if_.then_body.maybe_default_region.is_none());
  cast!(if_.then_body.inner, IExpressionPE::Void);
  assert!(if_.else_body.maybe_pure.is_none());
  assert!(if_.else_body.maybe_default_region.is_none());
  cast!(if_.else_body.inner, IExpressionPE::Void);
}

#[test]
fn if_with_condition_declarations() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "if x = 4; not x.isEmpty() { }");
  let if_ = cast!(expr, IExpressionPE::If);

  let condition = cast!(if_.condition, IExpressionPE::Consecutor);
  let (let_x, not_x_isempty) = expect_2(&condition.inners);
  let let_x = cast!(let_x, IExpressionPE::Let);
  let x_destination = let_x.pattern.destination.as_ref().unwrap();
  assert_destination_local_name(x_destination, "x");
  assert!(let_x.pattern.templex.is_none());
  assert!(let_x.pattern.destructure.is_none());
  let four = cast!(let_x.source, IExpressionPE::ConstantInt);
  assert_eq!(four.value, 4);
  assert_eq!(four.bits, None);

  let not = cast!(not_x_isempty, IExpressionPE::Not);
  let x_isempty = cast!(not.inner, IExpressionPE::MethodCall);
  assert_lookup_name(x_isempty.subject_expr, "x");
  assert_name(&x_isempty.method_lookup.name, "isEmpty");
  assert!(x_isempty.method_lookup.template_args.is_none());
  assert!(x_isempty.arg_exprs.is_empty());

  assert!(if_.then_body.maybe_pure.is_none());
  assert!(if_.then_body.maybe_default_region.is_none());
  cast!(if_.then_body.inner, IExpressionPE::Void);
  assert!(if_.else_body.maybe_pure.is_none());
  assert!(if_.else_body.maybe_default_region.is_none());
  cast!(if_.else_body.inner, IExpressionPE::Void);
}

#[test]
fn if_with_condition_declarations_and_block_contents() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_block_contents_expect(
    &parse_arena,
    &keywords,
    "newLen = if num == 0 { 1 } else { 2 };",
  );
  let consecutor = cast!(expr, IExpressionPE::Consecutor);
  let (let_new_len, final_void) = expect_2(&consecutor.inners);

  let let_new_len = cast!(let_new_len, IExpressionPE::Let);
  let new_len_destination = let_new_len.pattern.destination.as_ref().unwrap();
  assert_destination_local_name(new_len_destination, "newLen");
  assert!(let_new_len.pattern.templex.is_none());
  assert!(let_new_len.pattern.destructure.is_none());

  let if_ = cast!(let_new_len.source, IExpressionPE::If);
  let equals = cast!(if_.condition, IExpressionPE::BinaryCall);
  assert_eq!(equals.function_name.as_str(), "==");
  assert_lookup_name(equals.left_expr, "num");
  assert_eq!(
    cast!(equals.right_expr, IExpressionPE::ConstantInt).value,
    0
  );

  assert!(if_.then_body.maybe_pure.is_none());
  assert!(if_.then_body.maybe_default_region.is_none());
  assert_eq!(
    cast!(if_.then_body.inner, IExpressionPE::ConstantInt).value,
    1
  );
  assert!(if_.else_body.maybe_pure.is_none());
  assert!(if_.else_body.maybe_default_region.is_none());
  let two = cast!(if_.else_body.inner, IExpressionPE::ConstantInt);
  assert_eq!(two.value, 2);
  assert_eq!(two.bits, None);

  cast!(final_void, IExpressionPE::Void);
}

