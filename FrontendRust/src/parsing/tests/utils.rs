use bumpalo::Bump;
use crate::cast;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::lexing::errors::ParseError;
use crate::lexing::lexer::Lexer;
use crate::lexing::lexing_iterator::LexingIterator;
use crate::parsing::ast::*;
use crate::parsing::expression_parser::ExpressionParser;
use crate::parsing::parser::Parser;
use crate::parsing::pattern_parser::PatternParser;
use crate::parsing::expression_parser::ScrambleIterator;
use crate::parsing::templex_parser::TemplexParser;
use crate::parsing::tests::traverse::NodeRefP;
use crate::collect_only;

/// AFTERM: Remove this function and use the one in ParserTestCompilation.scala instead
/// so that it does a round-trip through vonprinter and parsedloader.
/// Compile a Vale file and return the FileP AST
pub fn compile_file<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> Result<FileP<'p>, ParseError>
where
  'p: 'ctx,
{
  let lexer = Lexer::new(parse_arena, keywords);
  let parser = Parser::new(parse_arena, keywords);

  // Lex the entire file
  let mut iter_for_lex = LexingIterator::new(code);

  // Parse denizens one by one
  let mut denizens = Vec::new();
  while !iter_for_lex.at_end() {
    iter_for_lex.consume_comments_and_whitespace();
    if iter_for_lex.at_end() {
      break;
    }
    let denizen_l = lexer.lex_denizen(&mut iter_for_lex)?;
    let denizen_p = parser.parse_denizen(denizen_l)?;
    denizens.push(denizen_p);
  }

  let empty_module = parse_arena.intern_str("");

  let package_coord = parse_arena.intern_package_coordinate(empty_module, &[]);

  let file_coord = parse_arena.intern_file_coordinate(package_coord, "test.vale");

  Ok(FileP {
    file_coord,
    comments_ranges: parse_arena.alloc_slice_copy(&[]),
    denizens: parse_arena.alloc_slice_from_vec(denizens),
  })
}

/// Compile a Vale file and panic if it fails (for tests)
pub fn compile<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> FileP<'p>
where
  'p: 'ctx,
{
  compile_file(parse_arena, keywords, code).unwrap_or_else(|e| panic!("Failed to parse file: {:?}", e))
}

/// Compile denizens (top-level declarations) from code
pub fn compile_denizens<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> Result<&'p [IDenizenP<'p>], ParseError>
where
  'p: 'ctx,
{
  compile_file(parse_arena, keywords, code).map(|file| file.denizens)
}

/// Compile a single denizen from code
pub fn compile_denizen<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> Result<&'p IDenizenP<'p>, ParseError>
where
  'p: 'ctx,
{
  let denizens = compile_denizens(parse_arena, keywords, code)?;
  assert_eq!(denizens.len(), 1, "Expected exactly one denizen");
  Ok(&denizens[0])
}

/// Compile a single denizen and panic if it fails
pub fn compile_denizen_expect<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> &'p IDenizenP<'p>
where
  'p: 'ctx,
{
  compile_denizen(parse_arena, keywords, code).unwrap_or_else(|e| panic!("Failed to parse denizen: {:?}", e))
}

/// Compile a single denizen and expect it to fail with an error, passing the error to a callback
pub fn compile_denizen_for_error<'p, 'ctx, F>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
  callback: F,
)
where
  'p: 'ctx,
  F: FnOnce(ParseError),
{
  match compile_denizen(parse_arena, keywords, code) {
    Ok(_) => panic!("Expected parsing to fail, but it succeeded"),
    Err(e) => callback(e),
  }
}

/// Compile an expression from code
pub fn compile_expression<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> Result<&'p IExpressionPE<'p>, ParseError>
where
  'p: 'ctx,
{
  let lexer = Lexer::new(parse_arena, keywords);
  let expression_parser = ExpressionParser::new(parse_arena, keywords);
  let mut templex_parser = TemplexParser::new(parse_arena, keywords);
  let mut pattern_parser = PatternParser::new(parse_arena, keywords);

  let mut iter_for_lex = LexingIterator::new(code);
  let scramble = lexer.lex_scramble(&mut iter_for_lex, false, false, false)?;
  let mut iter = ScrambleIterator::new(&scramble);
  expression_parser.parse_expression(&mut iter, false, &mut templex_parser, &mut pattern_parser)
}

/// Compile an expression and panic if it fails
pub fn compile_expression_expect<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> &'p IExpressionPE<'p>
where
  'p: 'ctx,
{
  compile_expression(parse_arena, keywords, code).unwrap_or_else(|e| panic!("Failed to parse expression: {:?}", e))
}

/// Compile an expression and expect it to fail, returning the error
pub fn compile_expression_for_error<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> ParseError
where
  'p: 'ctx,
{
  compile_expression(parse_arena, keywords, code).expect_err("Expected parsing to fail")
}

/// Compile a statement from code
pub fn compile_statement<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> Result<&'p IExpressionPE<'p>, ParseError>
where
  'p: 'ctx,
{
  let lexer = Lexer::new(parse_arena, keywords);
  let expression_parser = ExpressionParser::new(parse_arena, keywords);
  let mut templex_parser = TemplexParser::new(parse_arena, keywords);
  let mut pattern_parser = PatternParser::new(parse_arena, keywords);

  let mut iter_for_lex = LexingIterator::new(code);
  let scramble = lexer.lex_scramble(&mut iter_for_lex, false, false, false)?;
  let mut iter = ScrambleIterator::new(&scramble);
  expression_parser.parse_statement(&mut iter, false, &mut templex_parser, &mut pattern_parser)
}

/// Compile a statement and panic if it fails
pub fn compile_statement_expect<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> &'p IExpressionPE<'p>
where
  'p: 'ctx,
{
  compile_statement(parse_arena, keywords, code).unwrap_or_else(|e| panic!("Failed to parse statement: {:?}", e))
}

/// Compile block contents from code
pub fn compile_block_contents<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> Result<&'p IExpressionPE<'p>, ParseError>
where
  'p: 'ctx,
{
  let lexer = Lexer::new(parse_arena, keywords);
  let mut parser = Parser::new(parse_arena, keywords);

  let mut iter_for_lex = LexingIterator::new(code);
  let scramble = lexer.lex_scramble(&mut iter_for_lex, false, false, false)?;
  let mut iter = ScrambleIterator::new(&scramble);
  parser.expression_parser.parse_block_contents(
    &mut iter,
    false,
    &mut parser.templex_parser,
    &mut parser.pattern_parser,
  )
}

/// Compile block contents and panic if it fails
pub fn compile_block_contents_expect<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> &'p IExpressionPE<'p>
where
  'p: 'ctx,
{
  compile_block_contents(parse_arena, keywords, code).unwrap_or_else(|e| panic!("Failed to parse block contents: {:?}", e))
}

/// Compile a pattern from code
pub fn compile_pattern<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> Result<PatternPP<'p>, ParseError>
where
  'p: 'ctx,
{
  let lexer = Lexer::new(parse_arena, keywords);
  let mut parser = Parser::new(parse_arena, keywords);

  let mut iter_for_lex = LexingIterator::new(code);
  let scramble = lexer.lex_scramble(&mut iter_for_lex, false, false, false)?;
  let begin = scramble.range.begin();
  let mut iter = ScrambleIterator::new(&scramble);
  parser.pattern_parser.parse_pattern(
    &mut iter,
    &mut parser.templex_parser,
    begin,
    0,
    false,
    false,
    false,
    None,
  )
}

/// Compile a pattern and panic if it fails
pub fn compile_pattern_expect<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> PatternPP<'p>
where
  'p: 'ctx,
{
  compile_pattern(parse_arena, keywords, code).unwrap_or_else(|e| panic!("Failed to parse pattern: {:?}", e))
}

/// Compile a templex (type expression) from code
pub fn compile_templex<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> Result<ITemplexPT<'p>, ParseError>
where
  'p: 'ctx,
{
  let lexer = Lexer::new(parse_arena, keywords);
  let parser = Parser::new(parse_arena, keywords);

  let mut iter_for_lex = LexingIterator::new(code);
  let scramble = lexer.lex_scramble(&mut iter_for_lex, false, false, true)?;
  let mut iter = ScrambleIterator::new(&scramble);
  parser.templex_parser.parse_templex(&mut iter)
}

/// Compile a templex and panic if it fails
pub fn compile_templex_expect<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> ITemplexPT<'p>
where
  'p: 'ctx,
{
  compile_templex(parse_arena, keywords, code).unwrap_or_else(|e| panic!("Failed to parse templex: {:?}", e))
}

/// Compile a rulex (rule expression) from code
pub fn compile_rulex<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> Result<IRulexPR<'p>, ParseError>
where
  'p: 'ctx,
{
  let lexer = Lexer::new(parse_arena, keywords);
  let parser = Parser::new(parse_arena, keywords);

  let mut iter_for_lex = LexingIterator::new(code);
  let scramble = lexer.lex_scramble(&mut iter_for_lex, false, false, true)?;
  let mut iter = ScrambleIterator::new(&scramble);
  parser.templex_parser.parse_rule(&mut iter)
}

/// Compile a rulex and panic if it fails
pub fn compile_rulex_expect<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> IRulexPR<'p>
where
  'p: 'ctx,
{
  compile_rulex(parse_arena, keywords, code).unwrap_or_else(|e| panic!("Failed to parse rulex: {:?}", e))
}

/// Compile a struct from code
pub fn compile_struct<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> Result<&'p StructP<'p>, ParseError>
where
  'p: 'ctx,
{
  let denizen = compile_denizen(parse_arena, keywords, code)?;
  match denizen {
    IDenizenP::TopLevelStruct(s) => Ok(s),
    _ => panic!("Expected TopLevelStruct, got: {:?}", denizen),
  }
}

/// Compile a struct and panic if it fails
pub fn compile_struct_expect<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> &'p StructP<'p>
where
  'p: 'ctx,
{
  compile_struct(parse_arena, keywords, code).unwrap_or_else(|e| panic!("Failed to parse struct: {:?}", e))
}

/// Returns the function with the given name.
/// See test_find_func_named_returns_function for an example.
pub fn find_func_named<'p>(file: &'p FileP<'p>, name: &str) -> &'p FunctionP<'p> {
  collect_only!(
      file,
      NodeRefP::Function(function @ FunctionP {
          header: FunctionHeaderP {
              name: Some(NameP(_, s)),
              ..
          },
          ..
      }) if s.as_str() == name => Some(function)
  )
}
#[test]
fn test_find_func_named_returns_function() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, "exported func main() int {}");
  let main_function = find_func_named(&program, "main");
  assert!(main_function.header.params.as_ref().unwrap().params.is_empty());
}

/// Returns the struct with the given name. See find_func_named's test for a similar example.
  pub fn find_struct_named<'p, 'f>(file: &'f FileP<'p>, name: &str) -> &'f StructP<'p>
  where
    'f: 'p,
  {
  collect_only!(
      file,
      NodeRefP::Struct(struct_ @ StructP {
          name: NameP(_, s),
          ..
      }) if s.as_str() == name => Some(struct_)
  )
}

pub fn compile_for_error<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  code: &str,
) -> ParseError
where
  'p: 'ctx,
{
  compile_file(parse_arena, keywords, code).expect_err("Should be error")
}

pub fn assert_lookup_name(expr: &IExpressionPE, expected: &str) {
  let lookup = cast!(expr, IExpressionPE::Lookup);
  assert_name(&lookup.name, expected);
  assert!(lookup.template_args.is_none());
}

pub fn assert_templex_name(expr: &ITemplexPT, expected: &str) {
  let templex_lookup = cast!(expr, ITemplexPT::NameOrRune);
  assert_eq!(templex_lookup.0.str().as_str(), expected);
}

pub fn assert_name(name: &IImpreciseNameP, expected: &str) {
  let lookup_name = cast!(name, IImpreciseNameP::LookupName);
  assert_eq!(lookup_name.str().as_str(), expected);
}

pub fn assert_destination_local_name(destination: &DestinationLocalP, expected: &str) {
  let local_name = cast!(&destination.decl, INameDeclarationP::LocalNameDeclaration);
  assert_eq!(local_name.str().as_str(), expected);
}

/// Asserts that a slice has exactly 1 element and returns it.
pub fn expect_1<T>(elements: &[T]) -> &T {
  assert_eq!(elements.len(), 1, "Expected exactly 1 element, got {}", elements.len());
  &elements[0]
}

/// Asserts that a slice has exactly 2 elements and returns them.
pub fn expect_2<T>(elements: &[T]) -> (&T, &T) {
  assert_eq!(elements.len(), 2, "Expected exactly 2 elements, got {}", elements.len());
  (&elements[0], &elements[1])
}

/// Asserts that a slice has exactly 3 elements and returns them.
pub fn expect_3<T>(elements: &[T]) -> (&T, &T, &T) {
  assert_eq!(elements.len(), 3, "Expected exactly 3 elements, got {}", elements.len());
  (&elements[0], &elements[1], &elements[2])
}

/// Asserts that a slice has exactly 4 elements and returns them.
pub fn expect_4<T>(elements: &[T]) -> (&T, &T, &T, &T) {
  assert_eq!(elements.len(), 4, "Expected exactly 4 elements, got {}", elements.len());
  (&elements[0], &elements[1], &elements[2], &elements[3])
}

/// Asserts that a slice has exactly 5 elements and returns them.
pub fn expect_5<T>(elements: &[T]) -> (&T, &T, &T, &T, &T) {
  assert_eq!(elements.len(), 5, "Expected exactly 5 elements, got {}", elements.len());
  (
    &elements[0],
    &elements[1],
    &elements[2],
    &elements[3],
    &elements[4],
  )
}

/// Asserts that a slice has exactly 6 elements and returns them.
pub fn expect_6<T>(elements: &[T]) -> (&T, &T, &T, &T, &T, &T) {
  assert_eq!(elements.len(), 6, "Expected exactly 6 elements, got {}", elements.len());
  (
    &elements[0],
    &elements[1],
    &elements[2],
    &elements[3],
    &elements[4],
    &elements[5],
  )
}

/// Unwraps an ESCCD-compliant enum (single field enum) and returns a reference to the thing
/// inside. Intended for use in tests only.
#[macro_export]
macro_rules! cast {
  ($value:expr, $variant:path) => {{
    match $value {
      $variant(inner) => inner,
      actual => panic!("expected {}, got {:?}", stringify!($variant), actual),
    }
  }};
}
