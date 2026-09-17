
use bumpalo::Bump;
use crate::cast;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::parsing::ast::*;
use crate::parsing::tests::utils::*;

#[test]
fn simple_while_loop() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_block_contents_expect(&parse_arena, &keywords, "while true {}");
  let while_ = cast!(expr, IExpressionPE::While);
  let condition = cast!(while_.condition, IExpressionPE::ConstantBool);
  assert!(condition.value);
  assert!(while_.body.maybe_pure.is_none());
  assert!(while_.body.maybe_default_region.is_none());
  cast!(while_.body.inner, IExpressionPE::Void);
}

#[test]
fn result_after_while_loop() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_block_contents_expect(&parse_arena, &keywords, "while true {} false");
  let consecutor = cast!(expr, IExpressionPE::Consecutor);
  let (while_expr, false_expr) = expect_2(&consecutor.inners);

  let while_ = cast!(while_expr, IExpressionPE::While);
  let condition = cast!(while_.condition, IExpressionPE::ConstantBool);
  assert!(condition.value);
  assert!(while_.body.maybe_pure.is_none());
  assert!(while_.body.maybe_default_region.is_none());
  cast!(while_.body.inner, IExpressionPE::Void);

  let false_ = cast!(false_expr, IExpressionPE::ConstantBool);
  assert!(!false_.value);
}

#[test]
fn while_with_condition_declarations() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_block_contents_expect(&parse_arena, &keywords, "while x = 4; x > 6 { }");
  let while_ = cast!(expr, IExpressionPE::While);

  let condition = cast!(while_.condition, IExpressionPE::Consecutor);
  let (let_x, x_greater_than_six) = expect_2(&condition.inners);

  let let_x = cast!(let_x, IExpressionPE::Let);
  let x_destination = let_x.pattern.destination.as_ref().unwrap();
  assert_destination_local_name(x_destination, "x");
  assert!(let_x.pattern.templex.is_none());
  assert!(let_x.pattern.destructure.is_none());
  let four = cast!(let_x.source, IExpressionPE::ConstantInt);
  assert_eq!(four.value, 4);
  assert_eq!(four.bits, None);

  let greater_than = cast!(x_greater_than_six, IExpressionPE::BinaryCall);
  assert_eq!(greater_than.function_name.str().as_str(), ">");
  assert_lookup_name(greater_than.left_expr, "x");
  let six = cast!(greater_than.right_expr, IExpressionPE::ConstantInt);
  assert_eq!(six.value, 6);
  assert_eq!(six.bits, None);

  assert!(while_.body.maybe_pure.is_none());
  assert!(while_.body.maybe_default_region.is_none());
  cast!(while_.body.inner, IExpressionPE::Void);
}
