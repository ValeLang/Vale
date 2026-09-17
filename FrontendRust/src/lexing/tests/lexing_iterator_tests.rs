use crate::lexing::lexing_iterator::LexingIterator;

#[test]
fn advance_past_multibyte_lands_on_next_char() {
  let mut iter = LexingIterator::new("«x");
  let c = iter.advance();
  assert_eq!(c, '«');
  assert_eq!(iter.position, 2);
  assert_eq!(iter.peek(), 'x');
}

#[test]
#[ignore]
fn peek_n_one_at_multibyte_boundary() {
  let iter = LexingIterator::new("«x");
  let got = iter.peek_n(1);
  assert_eq!(got.as_deref(), Some("«"));
}

#[test]
fn try_skip_str_through_multibyte() {
  let mut iter = LexingIterator::new("«x");
  assert!(iter.try_skip_str("«"));
  assert_eq!(iter.position, 2);
  assert!(iter.try_skip_str("x"));
  assert_eq!(iter.position, 3);
  assert!(iter.at_end());
}

#[test]
#[ignore]
fn chevron_comment_consumption_leaves_position_at_byte_index() {
  let code = "«mynote»rest";
  let mut iter = LexingIterator::new(code);
  while !iter.at_end() && iter.peek() != '»' {
    iter.advance();
  }
  iter.advance();
  let expected_byte_pos = code.find("rest").unwrap();
  assert_eq!(iter.position, expected_byte_pos);
  assert_eq!(iter.peek(), 'r');
}

#[test]
fn angle_is_open_or_close_misreads_after_multibyte_drift() {
  use bumpalo::Bump;
  use crate::parse_arena::ParseArena;
  use crate::keywords::Keywords;
  use crate::parsing::tests::utils::compile_file;

  let code = "\
exported func main() int {\n\
  s = \"«»«»«»«»«»«»«»«»«»«»\";\n\
  if (a > b) {\n\
    return 1;\n\
  }\n\
  return 0;\n\
}\n";
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let _ = compile_file(&parse_arena, &keywords, code)
    .unwrap_or_else(|e| panic!("UTF-8 angle misread reproduced: {:?}", e));
}

#[test]
#[ignore]
fn testvale_full_file_parses() {
  use bumpalo::Bump;
  use crate::parse_arena::ParseArena;
  use crate::keywords::Keywords;
  use crate::parsing::tests::utils::compile_file;

  let path = "/Volumes/V/Vale3/VmdSiteGen/src/testvale.vale";
  let code = std::fs::read_to_string(path)
    .unwrap_or_else(|e| panic!("Probe needs VmdSiteGen checkout at {}: {}", path, e));
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let _ = compile_file(&parse_arena, &keywords, &code)
    .unwrap_or_else(|e| panic!("testvale.vale failed to parse: {:?}", e));
}

