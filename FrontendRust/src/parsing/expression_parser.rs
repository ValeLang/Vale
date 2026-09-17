use crate::keywords::Keywords;
use crate::StrI;
use crate::lexing::ast::*;
use crate::lexing::errors::ParseError;
use crate::parsing::ast::*;
use crate::parsing::parse_utils::try_skip_past_equals_while;
use crate::parsing::parse_utils::try_skip_past_keyword_while;
use crate::parsing::pattern_parser::PatternParser;
use crate::parsing::templex_parser::TemplexParser;
use crate::parse_arena::ParseArena;

type ParseResult<T> = Result<T, ParseError>;

// Helper enum for expression parsing
#[derive(Debug)]
enum ExpressionElement<'p> {
  Data(&'p IExpressionPE<'p>),
  BinaryCall(NameP<'p>, i32), // name and precedence
}

/// Iterator over a scramble of lexed nodes
/// Matches Scala's ScrambleIterator (holds reference to scramble, like Scala)
#[derive(Clone, Debug)]
pub struct ScrambleIterator<'p, 's> {
  pub scramble: &'s ScrambleLE<'p>,
  pub index: usize,
  pub end: usize,
}

impl<'p, 's> ScrambleIterator<'p, 's> {
  /// Create a new iterator over the entire scramble
  pub fn new(scramble: &'s ScrambleLE<'p>) -> Self {
    let end = scramble.elements.len();
    ScrambleIterator {
      scramble,
      index: 0,
      end,
    }
  }
  
  /// Create a new iterator with custom bounds
  pub fn with_bounds(scramble: &'s ScrambleLE<'p>, index: usize, end: usize) -> Self {
    assert!(end <= scramble.elements.len());
    ScrambleIterator {
      scramble,
      index,
      end,
    }
  }

  /// Check if at end of iteration
  pub fn at_end(&self) -> bool {
    self.index == self.end
  }
  
  /// Get the range covered by remaining elements
  pub fn range(&self) -> RangeL {
    if self.index < self.end {
      RangeL(
        self.scramble.elements[self.index].range().begin(),
        self.scramble.elements[self.end - 1].range().end(),
      )
    } else {
      assert!(self.index == self.end);
      RangeL(self.scramble.range.end(), self.scramble.range.end())
    }
  }
  
  /// Get current position
  pub fn get_pos(&self) -> i32 {
    if self.index >= self.end {
      self.scramble.range.end()
    } else {
      self.scramble.elements[self.index].range().begin()
    }
  }
  
  /// Get the end position of the previous element
  pub fn get_prev_end_pos(&self) -> i32 {
    if self.index == 0 {
      self.scramble.range.begin()
    } else {
      self.scramble.elements[self.index - 1].range().end()
    }
  }
  
  /// Peek at the previous element
  pub fn peek_prev(&self) -> Option<&INodeLEEnum<'p>> {
    if self.index > 0 {
      Some(&self.scramble.elements[self.index - 1])
    } else {
      None
    }
  }

  /// Skip to the position of another iterator
  pub fn skip_to(&mut self, that: &ScrambleIterator<'p, 's>) {
    self.index = that.index;
  }
  
  /// Stop iteration (move to end)
  pub fn stop(&mut self) {
    self.index = self.end;
  }
  
  /// Check if there are more elements
  pub fn has_next(&self) -> bool {
    self.index < self.end
  }
  
  /// Peek at the current element
  pub fn peek(&self) -> Option<&INodeLEEnum<'p>> {
    if self.index >= self.end {
      None
    } else {
      Some(&**&self.scramble.elements[self.index])
    }
  }

  /// Peek at the current element, returning owned clone to avoid borrow conflicts.
  pub fn peek_cloned(&self) -> Option<INodeLEEnum<'p>> {
    self.peek().cloned()
  }
  
  /// Take the current element and advance (returning owned)
  pub fn take(&mut self) -> Option<INodeLEEnum<'p>> {
    if self.index >= self.end {
      None
    } else {
      let result = (*self.scramble.elements[self.index]).clone();
      self.index += 1;
      Some(result)
    }
  }
  
  /// Peek at the next n elements
  pub fn peek_n(&self, n: usize) -> Vec<Option<&INodeLEEnum<'p>>> {
    (0..n)
      .map(|i| {
        let idx = self.index + i;
        if idx < self.end {
          Some(&**&self.scramble.elements[idx])
        } else {
          None
        }
      })
      .collect()
  }
  
  /// Peek at the next 2 elements
  pub fn peek2(&self) -> (Option<&INodeLEEnum<'p>>, Option<&INodeLEEnum<'p>>) {
    let first = if self.index < self.end {
      Some(&**&self.scramble.elements[self.index])
    } else {
      None
    };
    let second = if self.index + 1 < self.end {
      Some(&**&self.scramble.elements[self.index + 1])
    } else {
      None
    };
    (first, second)
  }

  /// Peek at the next 2 elements, returning owned clones to avoid borrow conflicts.
  pub fn peek2_cloned(&self) -> (Option<INodeLEEnum<'p>>, Option<INodeLEEnum<'p>>) {
    let (a, b) = self.peek2();
    (a.cloned(), b.cloned())
  }
  
  /// Peek at the next 3 elements
  pub fn peek3(
    &self,
  ) -> (
    Option<&INodeLEEnum<'p>>,
    Option<&INodeLEEnum<'p>>,
    Option<&INodeLEEnum<'p>>,
  ) {
    let first = if self.index < self.end {
      Some(&**&self.scramble.elements[self.index])
    } else {
      None
    };
    let second = if self.index + 1 < self.end {
      Some(&**&self.scramble.elements[self.index + 1])
    } else {
      None
    };
    let third = if self.index + 2 < self.end {
      Some(&**&self.scramble.elements[self.index + 2])
    } else {
      None
    };
    (first, second, third)
  }

  /// Peek at the next 3 elements, returning owned clones to avoid borrow conflicts.
  pub fn peek3_cloned(
    &self,
  ) -> (
    Option<INodeLEEnum<'p>>,
    Option<INodeLEEnum<'p>>,
    Option<INodeLEEnum<'p>>,
  ) {
    let (a, b, c) = self.peek3();
    (a.cloned(), b.cloned(), c.cloned())
  }
  
  /// Check if next element is a specific word
  pub fn peek_word(&self, word: StrI<'_>) -> bool {
    match self.peek() {
      Some(INodeLEEnum::Word(WordLE { str, .. })) => *str == word,
      _ => false,
    }
  }
  
  /// Advance and return a reference to the current element
  pub fn advance(&mut self) -> &INodeLEEnum<'p> {
    assert!(self.has_next());
    let result = &**&self.scramble.elements[self.index];
    self.index += 1;
    result
  }
  
  /// Try to skip a symbol
  pub fn try_skip_symbol(&mut self, symbol: char) -> bool {
    match self.peek() {
      Some(INodeLEEnum::Symbol(SymbolLE(_, c))) if *c == symbol => {
        self.index += 1;
        true
      }
      _ => false,
    }
  }
  
  /// Try to skip multiple symbols in sequence
  pub fn try_skip_symbols(&mut self, symbols: &[char]) -> bool {
    if self.index + symbols.len() > self.end {
      return false;
    }

    for (i, &expected) in symbols.iter().enumerate() {
      match &**&self.scramble.elements[self.index + i] {
        INodeLEEnum::Symbol(SymbolLE(_, c)) if *c == expected => {}
        _ => return false,
      }
    }

    self.index += symbols.len();
    true
  }
  
  /// Get the next word element
  pub fn next_word(&mut self) -> Option<WordLE<'p>> {
    match self.peek() {
      Some(INodeLEEnum::Word(w)) => {
        let result = w.clone();
        self.index += 1;
        Some(result)
      }
      _ => None,
    }
  }
  
  /// Expect a specific word (panics if not found)
  pub fn expect_word(&mut self, str: StrI<'_>) {
    let found = self.try_skip_word(str).is_some();
    assert!(found, "Expected word {:?}", str);
  }
  
  /// Try to skip a specific word
  pub fn try_skip_word(&mut self, str: StrI<'_>) -> Option<RangeL> {
    match self.peek() {
      Some(INodeLEEnum::Word(WordLE { range, str: s })) if *s == str => {
        let result = *range;
        self.index += 1;
        Some(result)
      }
      _ => None,
    }
  }
  
  /// Find the index where a condition is true
  pub fn find_index_where<F>(&self, func: F) -> Option<usize>
  where
    F: Fn(&INodeLEEnum) -> bool,
  {
    for i in self.index..self.end {
      if func(&**&self.scramble.elements[i]) {
        return Some(i);
      }
    }
    None
  }
  
  /// Split the scramble on a specific symbol
  pub fn split_on_symbol(
    &self,
    needle: char,
    include_empty_trailing: bool,
  ) -> Vec<ScrambleIterator<'p, 's>> {
    let mut iters = Vec::new();
    let mut start = self.index;
    let mut i = start;

    while i < self.end {
      match &**&self.scramble.elements[i] {
        INodeLEEnum::Symbol(SymbolLE(_, c)) if *c == needle => {
          iters.push(ScrambleIterator::with_bounds(self.scramble, start, i));
          start = i + 1;
          i += 1;
        }
        _ => {
          i += 1;
        }
      }
    }

    if start < self.end {
      iters.push(ScrambleIterator::with_bounds(self.scramble, start, self.end));
    } else if start == self.end && include_empty_trailing {
      iters.push(ScrambleIterator::with_bounds(self.scramble, start, self.end));
    }

    iters
  }
  
  /// Get remaining elements count
  pub fn remaining(&self) -> usize {
    if self.end > self.index {
      self.end - self.index
    } else {
      0
    }
  }

  /// Check if there are at least n elements remaining
  pub fn has_at_least(&self, n: usize) -> bool {
    self.index + n <= self.end
  }

  /// Consume and return all remaining elements
  pub fn consume_rest(&mut self) -> Vec<INodeLEEnum<'p>> {
    let mut result = Vec::new();
    while self.has_next() {
      result.push(self.take().unwrap());
    }
    result
  }
}

pub struct ExpressionParser<'p, 'ctx> {
  parse_arena: &'ctx ParseArena<'p>,
  pub keywords: &'ctx Keywords<'p>,
}

impl<'p, 'ctx> ExpressionParser<'p, 'ctx>
where
    'p: 'ctx,
{
  /// Parse a while loop
  fn parse_while(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<Option<IExpressionPE<'p>>> {
    let while_begin = iter.get_pos();

    let mut tentative_iter = iter.clone();

    let pure = tentative_iter.try_skip_word(self.keywords.pure);

    if tentative_iter
      .try_skip_word(self.keywords.whiile)
      .is_none()
    {
      return Ok(None);
    }

    iter.skip_to(&tentative_iter);

    // Parse condition (lines 255-259)
    let condition = self.parse_block_contents(iter, true, templex_parser, pattern_parser)?;

    // Parse body (lines 261-271)
    let body = match iter.peek_cloned() {
      Some(INodeLEEnum::Curlied(CurliedLE { range: _, contents })) => {
        let contents = contents.clone();
        iter.advance();
        let mut body_iter = ScrambleIterator::new(&contents);
        self.parse_block_contents(&mut body_iter, false, templex_parser, pattern_parser)?
      }
      _ => return Err(ParseError::BadExpressionBegin(iter.get_pos())),
    };

    Ok(Some(IExpressionPE::While(WhilePE {
      range: RangeL(while_begin, iter.get_prev_end_pos()),
      condition: self.parse_arena.alloc(condition),
      body: self.parse_arena.alloc(BlockPE {
        range: body.range(),
        maybe_pure: pure,
        maybe_default_region: None,
        inner: self.parse_arena.alloc(body),
      }),
    })))
  }
  
  /// Parse an explicit block
  fn parse_explicit_block(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<Option<IExpressionPE<'p>>> {
    let block_begin = iter.get_pos();

    let mut tentative_iter = iter.clone();

    let pure = tentative_iter.try_skip_word(self.keywords.pure);

    if tentative_iter.try_skip_word(self.keywords.block).is_none() {
      return Ok(None);
    }

    iter.skip_to(&tentative_iter);

    // Parse body
    let contents = match iter.peek_cloned() {
      Some(INodeLEEnum::Curlied(CurliedLE { contents, .. })) => {
        let contents = contents.clone();
        iter.advance();
        let mut body_iter = ScrambleIterator::new(&contents);
        self.parse_block_contents(&mut body_iter, false, templex_parser, pattern_parser)?
      }
      _ => return Err(ParseError::BadExpressionBegin(iter.get_pos())),
    };

    Ok(Some(IExpressionPE::Block(BlockPE {
      range: RangeL(block_begin, iter.get_prev_end_pos()),
      maybe_pure: pure,
      maybe_default_region: None,
      inner: self.parse_arena.alloc(contents),
    })))
  }
  
  fn parse_foreach(
    &self,
    original_iter: &mut ScrambleIterator<'p, '_>,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<Option<IExpressionPE<'p>>> {
    let each_begin = original_iter.get_pos();

    let mut tentative_iter: ScrambleIterator<'p, '_> = original_iter.clone();

    if tentative_iter
      .try_skip_word(self.keywords.parallel)
      .is_some()
    {
      // do nothing for now
    }

    let pure = tentative_iter.try_skip_word(self.keywords.pure);

    if tentative_iter
      .try_skip_word(self.keywords.foreeach)
      .is_none()
    {
      return Ok(None);
    }
    original_iter.skip_to(&tentative_iter);
    let iter: &mut ScrambleIterator<'p, '_> = original_iter;

    let (in_range, pattern) = match try_skip_past_keyword_while(iter, self.keywords.r#in, |it| {
      match it.peek() {
        // Stop if we hit the end or a semicolon or a curly brace
        None => false,
        Some(INodeLEEnum::Symbol(SymbolLE(_, ';'))) => false,
        Some(INodeLEEnum::Curlied(_)) => false,
        // Continue for anything else
        Some(_) => true,
      }
    }) {
      None => return Err(ParseError::BadForeachInError(iter.get_pos())),
      Some((in_word, mut pattern_iter)) => {
        let pattern_begin = pattern_iter.get_pos();
        let pattern: PatternPP<'p> = pattern_parser.parse_pattern(
          &mut pattern_iter,
          templex_parser,
          pattern_begin,
          0,
          false,
          false,
          false,
          None,
        )?;
        (in_word.range, pattern)
      }
    };

    let iterable_expr = self.parse_block_contents(iter, true, templex_parser, pattern_parser)?;

    let _body_begin = iter.get_pos();

    let body = match iter.peek_cloned() {
      Some(INodeLEEnum::Curlied(CurliedLE { contents, .. })) => {
        let contents = contents.clone();
        iter.advance();
        self.parse_block_contents(
          &mut ScrambleIterator::new(&contents),
          false,
          templex_parser,
          pattern_parser,
        )?
      }
      _ => return Err(ParseError::BadStartOfWhileBody(iter.get_pos())),
    };

    Ok(Some(IExpressionPE::Each(EachPE {
      range: RangeL(each_begin, iter.get_prev_end_pos()),
      maybe_pure: pure,
      entry_pattern: pattern,
      in_keyword_range: in_range,
      iterable_expr: self.parse_arena.alloc(iterable_expr),
      body: self.parse_arena.alloc(BlockPE {
        range: body.range(),
        maybe_pure: None,
        maybe_default_region: None,
        inner: self.parse_arena.alloc(body),
      }),
    })))
  }

  /// Parse an if ladder (if/else if/else)
  fn parse_if_ladder(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<Option<IExpressionPE<'p>>> {
    let if_ladder_begin = iter.get_pos();

    // Check for 'if' keyword (lines 391-394)
    match iter.peek_cloned() {
      Some(INodeLEEnum::Word(WordLE { str, .. })) if str == self.keywords.iff => {}
      _ => return Ok(None),
    }

    // Parse root if (lines 396-400)
    let root_if = self.parse_if_part(iter, templex_parser, pattern_parser)?;

    // Parse else if parts (lines 402-415)
    let mut if_elses = Vec::new();
    while match iter.peek2_cloned() {
      (
        Some(INodeLEEnum::Word(WordLE { str: elsse, .. })),
        Some(INodeLEEnum::Word(WordLE { str: iff, .. })),
      ) if elsse == self.keywords.elsse && iff == self.keywords.iff => true,
      _ => false,
    } {
      iter.advance(); // Skip the else
      if_elses.push(self.parse_if_part(iter, templex_parser, pattern_parser)?);
    }

    // Parse else block (lines 417-436)
    let else_begin = iter.get_pos();
    let maybe_else_block = if iter.try_skip_word(self.keywords.elsse).is_some() {
      let body = match iter.peek_cloned() {
        Some(INodeLEEnum::Curlied(b)) => {
          let b = b.clone();
          iter.advance();
          b
        }
        _ => return Err(ParseError::BadExpressionBegin(iter.get_pos())),
      };

      let mut else_body_iter = ScrambleIterator::new(&body.contents);
      let else_body =
        self.parse_block_contents(&mut else_body_iter, false, templex_parser, pattern_parser)?;

      let else_end = iter.get_pos();
      Some(BlockPE {
        range: RangeL(else_begin, else_end),
        maybe_pure: None,
        maybe_default_region: None,
        inner: self.parse_arena.alloc(else_body),
      })
    } else {
      None
    };

    // Build final else block (lines 438-448)
    let final_else = match maybe_else_block {
      None => {
        let pos = iter.get_prev_end_pos();
        BlockPE {
          range: RangeL(pos, pos),
          maybe_pure: None,
          maybe_default_region: None,
          inner: self.parse_arena.alloc(IExpressionPE::Void(VoidPE {
            range: RangeL(pos, pos),
          })),
        }
      }
      Some(block) => block,
    };

    // Fold right to build nested if/else (lines 449-466)
    let mut root_else_block = final_else;
    for (cond_block, then_block) in if_elses.into_iter().rev() {
      root_else_block = BlockPE {
        range: RangeL(cond_block.range().begin(), then_block.range.end()),
        maybe_pure: None,
        maybe_default_region: None,
        inner: self.parse_arena.alloc(IExpressionPE::If(IfPE {
          range: RangeL(cond_block.range().begin(), then_block.range.end()),
          condition: self.parse_arena.alloc(cond_block),
          then_body: self.parse_arena.alloc(then_block),
          else_body: self.parse_arena.alloc(root_else_block),
        })),
      };
    }

    let (root_condition, root_then) = root_if;
    Ok(Some(IExpressionPE::If(IfPE {
      range: RangeL(if_ladder_begin, iter.get_prev_end_pos()),
      condition: self.parse_arena.alloc(root_condition),
      then_body: self.parse_arena.alloc(root_then),
      else_body: self.parse_arena.alloc(root_else_block),
    })))
  }

  fn next_is_set_expr(&self, iter: &ScrambleIterator) -> bool {
    match iter.peek2_cloned() {
      (
        Some(INodeLEEnum::Word(WordLE {
          range: set_range,
          str: set,
        })),
        Some(other),
      ) if set == self.keywords.set && set_range.end() < other.range().begin() => {
        // Then there's indeed a space after the set. Continue!
        true
      }
      _ => false,
    }
  }
  
  fn parse_mut_expr(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    stop_on_curlied: bool,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<Option<IExpressionPE<'p>>> {
    let mutate_begin = iter.get_pos();
    if !self.next_is_set_expr(iter) {
      return Ok(None);
    }
    iter.advance();

    // Use try_skip_past_equals_while to find the mutatee expression
    let mutatee_expr =
        match try_skip_past_equals_while(iter, |scouting_iter| match scouting_iter.peek_cloned() {
          None => false,
          Some(INodeLEEnum::Symbol(SymbolLE(_, ';'))) => false,
          _ => true,
        }) {
          None => return Err(ParseError::BadMutateEqualsError(iter.get_pos())),
          Some(mut dest_iter) => self.parse_expression(
            &mut dest_iter,
            stop_on_curlied,
            templex_parser,
            pattern_parser,
          )?,
        };

    let source_expr =
        self.parse_expression(iter, stop_on_curlied, templex_parser, pattern_parser)?;

    Ok(Some(IExpressionPE::Mutate(MutatePE {
      range: RangeL(mutate_begin, iter.get_prev_end_pos()),
      mutatee: self.parse_arena.alloc(mutatee_expr),
      source: self.parse_arena.alloc(source_expr),
    })))
  }
  
  fn parse_let(
    &self,
    pattern_iter: &mut ScrambleIterator<'p, '_>,
    iter: &mut ScrambleIterator<'p, '_>,
    stop_on_curlied: bool,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<LetPE<'p>> {
    let pattern_begin = pattern_iter.get_pos();
    let pattern = pattern_parser.parse_pattern(
      pattern_iter,
      templex_parser,
      pattern_begin,
      0,
      false,
      false,
      false,
      None,
    )?;

    // Validate the pattern doesn't use 'set' keyword
    if let Some(DestinationLocalP {
                  decl: INameDeclarationP::LocalNameDeclaration(NameP(_, name)),
                  mutate: None,
                }) = &pattern.destination
    {
      assert!(*name != self.keywords.set);
    }

    let source_expr =
        self.parse_expression(iter, stop_on_curlied, templex_parser, pattern_parser)?;

    Ok(LetPE {
      range: RangeL(pattern.range.begin(), source_expr.range().end()),
      pattern: &*self.parse_arena.alloc(pattern),
      source: self.parse_arena.alloc(source_expr),
    })
  }
  
  /// Parse a single if part (condition and then block)
  fn parse_if_part(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<(&'p IExpressionPE<'p>, BlockPE<'p>)> {
    let if_begin = iter.get_pos();

    if iter.try_skip_word(self.keywords.iff).is_none() {
      return Err(ParseError::BadExpressionBegin(iter.get_pos()));
    }

    // Parse condition (lines 318-321)
    let condition = self.parse_block_contents(iter, true, templex_parser, pattern_parser)?;

    // Parse then block (lines 323-369)
    let body = match iter.peek_cloned() {
      Some(INodeLEEnum::Curlied(CurliedLE { range: _, contents })) => {
        let contents = contents.clone();
        iter.advance();
        let mut body_iter = ScrambleIterator::new(&contents);
        self.parse_block_contents(&mut body_iter, false, templex_parser, pattern_parser)?
      }
      _ => return Err(ParseError::BadExpressionBegin(iter.get_pos())),
    };

    Ok((
      condition,
      BlockPE {
        range: RangeL(if_begin, iter.get_prev_end_pos()),
        maybe_pure: None,
        maybe_default_region: None,
        inner: body,
      },
    ))
  }

  pub fn new(
    parse_arena: &'ctx ParseArena<'p>,
    keywords: &'ctx Keywords<'p>,
  ) -> Self {
    ExpressionParser { parse_arena, keywords }
  }

  /// Parse a block from a curlied expression
  pub fn parse_block(
    &self,
    block_l: &CurliedLE<'p>,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<&'p IExpressionPE<'p>> {
    let mut iter = ScrambleIterator::new(&block_l.contents);
    self.parse_block_contents(&mut iter, false, templex_parser, pattern_parser)
  }
  
  /// Parse block contents
  pub fn parse_block_contents(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    stop_on_curlied: bool,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<&'p IExpressionPE<'p>> {
    let mut statements: Vec<&'p IExpressionPE<'p>> = Vec::new();

    // Parse statements (lines 603-615)
    while match iter.peek_cloned() {
      None => false,
      Some(INodeLEEnum::Curlied(_)) if stop_on_curlied => false,
      Some(_) => {
        let statement =
          self.parse_statement(iter, stop_on_curlied, templex_parser, pattern_parser)?;
        statements.push(statement);
        true
      }
    } {}

    // If we just ate a semicolon, but there's nothing after it, then add a void (lines 617-633)
    if iter.has_next() {
      match iter.peek_cloned() {
        Some(INodeLEEnum::Symbol(SymbolLE(_, ')'))) => {
          // vcurious() - unexpected but continue
        }
        Some(INodeLEEnum::Symbol(SymbolLE(_, ']'))) => {
          // vcurious() - unexpected but continue
        }
        _ => {}
      }
    } else {
      if let Some(prev) = iter.peek_prev() {
        if let INodeLEEnum::Symbol(SymbolLE(range, ';')) = prev {
          statements.push(self.parse_arena.alloc(IExpressionPE::Void(VoidPE {
            range: RangeL(range.end(), range.end()),
          })));
        }
      }
    }

    // Return result (lines 635-639)
    match statements.len() {
      0 => Ok(self.parse_arena.alloc(IExpressionPE::Void(VoidPE {
        range: RangeL(iter.get_pos(), iter.get_pos()),
      }))),
      1 => Ok(statements.into_iter().next().unwrap()),
      _ => Ok(self.parse_arena.alloc(IExpressionPE::Consecutor(ConsecutorPE {
        inners: self.parse_arena.alloc_slice_from_vec(statements),
      })))
    }
  }
  
  /// Parse lone block
  /// Parse lone block expression
  fn parse_lone_block(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<Option<IExpressionPE<'p>>> {
    let mut tentative_iter = iter.clone();

    // The pure/unsafe is a hack to get syntax highlighting work for
    // the future pure block feature.
    tentative_iter.try_skip_word(self.keywords.r#unsafe);
    let pure = tentative_iter.try_skip_word(self.keywords.pure);

    if tentative_iter.try_skip_word(self.keywords.block).is_none() {
      return Ok(None);
    }

    iter.skip_to(&tentative_iter);

    let begin = iter.get_pos();

    let inner = match iter.peek_cloned() {
      Some(INodeLEEnum::Curlied(curlied)) => {
        let curlied_contents = curlied.contents.clone();
        iter.advance();
        let mut contents_iter = ScrambleIterator::new(&curlied_contents);
        match self.parse_block_contents(&mut contents_iter, false, templex_parser, pattern_parser) {
          Err(error) => return Err(error),
          Ok(result) => result,
        }
      }
      _ => {
        return Err(ParseError::BadStartOfBlock(iter.get_pos()));
      }
    };

    Ok(Some(IExpressionPE::Block(BlockPE {
      range: RangeL(begin, iter.get_prev_end_pos()),
      maybe_pure: pure,
      maybe_default_region: None,
      inner: self.parse_arena.alloc(inner),
    })))
  }
  
  fn parse_destruct(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    stop_on_curlied: bool,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<Option<IExpressionPE<'p>>> {
    let begin = iter.get_pos();

    if iter.try_skip_word(self.keywords.destruct).is_none() {
      return Ok(None);
    }

    let inner_expr =
        match self.parse_expression(iter, stop_on_curlied, templex_parser, pattern_parser) {
          Err(e) => return Err(e),
          Ok(x) => x,
        };

    Ok(Some(IExpressionPE::Destruct(DestructPE {
      range: RangeL(begin, iter.get_prev_end_pos()),
      inner: self.parse_arena.alloc(inner_expr),
    })))
  }
  
  /// Parse unlet
  fn parse_unlet(&self, iter: &mut ScrambleIterator<'p, '_>) -> ParseResult<Option<IExpressionPE<'p>>> {
    // Check for 'unlet' keyword
    if let Some(range) = iter.try_skip_word(self.keywords.unlet) {
      // Parse the name to unlet
      match iter.peek_cloned() {
        Some(INodeLEEnum::Word(WordLE {
                                 range: name_range,
                                 str: name_str,
                               })) => {
          let name = IImpreciseNameP::LookupName(NameP(name_range, name_str));
          iter.advance();
          Ok(Some(IExpressionPE::Unlet(UnletPE {
            range: RangeL::new(range.begin(), iter.get_prev_end_pos()),
            name,
          })))
        }
        _ => Ok(None),
      }
    } else {
      Ok(None)
    }
  }
  
  fn parse_return(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    stop_on_curlied: bool,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<Option<IExpressionPE<'p>>> {
    let begin = iter.get_pos();
    if iter.try_skip_word(self.keywords.retuurn).is_none() {
      return Ok(None);
    }

    let inner_expr =
        self.parse_expression(iter, stop_on_curlied, templex_parser, pattern_parser)?;

    if !iter.try_skip_symbol(';') {
      return Err(ParseError::BadExpressionEnd(iter.get_pos()));
    }

    Ok(Some(IExpressionPE::Return(ReturnPE {
      range: RangeL(begin, iter.get_prev_end_pos()),
      expr: self.parse_arena.alloc(inner_expr),
    })))
  }
  
  fn parse_break(&self, iter: &mut ScrambleIterator<'p, '_>) -> ParseResult<Option<IExpressionPE<'p>>> {
    let begin = iter.get_pos();
    if iter.try_skip_word(self.keywords.r#break).is_none() {
      return Ok(None);
    }
    if !iter.try_skip_symbol(';') {
      return Err(ParseError::BadExpressionEnd(iter.get_pos()));
    }
    Ok(Some(IExpressionPE::Break(BreakPE {
      range: RangeL(begin, iter.get_prev_end_pos()),
    })))
  }
  
  /// Parse a statement
  pub fn parse_statement(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    stop_on_curlied: bool,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<&'p IExpressionPE<'p>> {
    if !iter.has_next() {
      return Err(ParseError::BadExpressionBegin(iter.get_pos()));
    }

    // Try various statement types (lines 754-785)
    if let Some(x) = self.parse_while(iter, templex_parser, pattern_parser)? {
      return Ok(self.parse_arena.alloc(x));
    }
    if let Some(x) = self.parse_explicit_block(iter, templex_parser, pattern_parser)? {
      return Ok(self.parse_arena.alloc(x));
    }
    if let Some(x) = self.parse_if_ladder(iter, templex_parser, pattern_parser)? {
      return Ok(self.parse_arena.alloc(x));
    }
    if let Some(x) = self.parse_foreach(iter, templex_parser, pattern_parser)? {
      return Ok(self.parse_arena.alloc(x));
    }
    if let Some(x) = self.parse_break(iter)? {
      return Ok(self.parse_arena.alloc(x));
    }
    if let Some(x) = self.parse_return(iter, stop_on_curlied, templex_parser, pattern_parser)? {
      return Ok(self.parse_arena.alloc(x));
    }

    assert!(iter.has_next());

    // Parse let or lone expression (lines 789-818)
    let let_or_lone_expr: &'p IExpressionPE<'p> = if self.next_is_set_expr(iter) {
      self.parse_arena.alloc(self
          .parse_mut_expr(iter, stop_on_curlied, templex_parser, pattern_parser)?
          .expect("parse_mut_expr should return Some when next_is_set_expr is true"))
    } else {
      match try_skip_past_equals_while(iter, |scouting_iter| match scouting_iter.peek_cloned() {
        None => false,
        Some(INodeLEEnum::Curlied(_)) if stop_on_curlied => false,
        Some(INodeLEEnum::Symbol(SymbolLE(_, ';'))) => false,
        _ => true,
      }) {
        Some(mut dest_iter) => {
          match self.parse_let(&mut dest_iter, iter, stop_on_curlied, templex_parser, pattern_parser) {
            Ok(let_expr) => self.parse_arena.alloc(IExpressionPE::Let(let_expr)),
            Err(ParseError::BadThingAfterTypeInPattern(_)) => {
              return Err(ParseError::ForgotSetKeyword(dest_iter.get_pos()))
            }
            Err(e) => return Err(e),
          }
        }
        None => {
          self.parse_expression(iter, stop_on_curlied, templex_parser, pattern_parser)?
        }
      }
    };

    // Consume optional semicolon (lines 819-827)
    match iter.peek_cloned() {
      None => {}                                             // okay, hit the end
      Some(INodeLEEnum::Curlied(_)) if stop_on_curlied => {} // okay, hit the end
      Some(INodeLEEnum::Symbol(SymbolLE(_, ';'))) => {
        iter.advance(); // consume it to end the statement
      }
      _ => return Err(ParseError::BadExpressionEnd(iter.get_pos())),
    }

    Ok(let_or_lone_expr)
  }
  
  /// Get operator precedence
  /// Get operator precedence
  pub fn get_precedence(&self, str: StrI<'_>) -> i32 {
    if str == self.keywords.dot_dot {
      6
    } else if str == self.keywords.asterisk || str == self.keywords.slash {
      5
    } else if str == self.keywords.plus || str == self.keywords.minus {
      4
    } else if str == self.keywords.spaceship
      || str == self.keywords.less_equals
      || str == self.keywords.less
      || str == self.keywords.greater_equals
      || str == self.keywords.greater
      || str == self.keywords.triple_equals
      || str == self.keywords.double_equals
      || str == self.keywords.not_equals
    {
      2
    } else if str == self.keywords.and || str == self.keywords.or {
      1
    } else {
      3 // Default precedence for custom operators like "mod", "florgle", etc. (Scala line 842)
    }
  }
  
  /// Parse an expression
  pub fn parse_expression(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    stop_on_curlied: bool,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<&'p IExpressionPE<'p>> {
    if !iter.has_next() {
      return Err(ParseError::BadExpressionBegin(iter.get_pos()));
    }

    let mut elements = Vec::new();

    // Parse expression elements (lines 853-890)
    loop {
      let sub_expr = self.parse_expression_data_element(
        iter,
        stop_on_curlied,
        templex_parser,
        pattern_parser,
      )?;
      let sub_expr_range_end = sub_expr.range().end();
      elements.push(ExpressionElement::Data(sub_expr));

      if self.at_expression_end(iter, stop_on_curlied) {
        break;
      } else {
        if sub_expr_range_end == iter.get_pos() {
          return Err(ParseError::NeedWhitespaceAroundBinaryOperator(
            iter.get_pos(),
          ));
        }

        match self.parse_binary_call(iter)? {
          None => break,
          Some(symbol) => {
            let precedence = self.get_precedence(symbol.str());
            elements.push(ExpressionElement::BinaryCall(symbol.clone(), precedence));

            match iter.peek_cloned() {
              None => return Err(ParseError::BadExpressionEnd(iter.get_pos())),
              Some(node) => {
                if symbol.range().end() == node.range().begin() {
                  return Err(ParseError::NeedWhitespaceAroundBinaryOperator(
                    iter.get_pos(),
                  ));
                }
              }
            }
          }
        }
      }
    }

    // Descramble the expression (lines 892-894)
    let (expr_pe, _) = self.descramble_elements(&elements, 0, elements.len() - 1, 1)?;
    Ok(expr_pe)
  }
  
  /// Parse a lookup expression
  pub fn parse_lookup(&self, iter: &mut ScrambleIterator<'p, '_>) -> Option<IExpressionPE<'p>> {
    let begin = iter.get_pos();
    match iter.peek3_cloned() {
      (
        Some(INodeLEEnum::Symbol(SymbolLE(_, '<'))),
        Some(INodeLEEnum::Symbol(SymbolLE(_, '='))),
        Some(INodeLEEnum::Symbol(SymbolLE(_, '>'))),
      ) => {
        iter.advance();
        iter.advance();
        iter.advance();
        Some(IExpressionPE::Lookup(self.parse_arena.alloc(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(
            RangeL(begin, iter.get_prev_end_pos()),
            self.keywords.spaceship,
          )),
          template_args: None,
        })))
      }
      (
        Some(INodeLEEnum::Symbol(SymbolLE(
          range1,
          c1 @ ('=' | '>' | '<' | '!'),
        ))),
        Some(INodeLEEnum::Symbol(SymbolLE(range2, '='))),
        _,
      ) => {
        iter.advance();
        iter.advance();
        let combined = format!("{}{}", c1, '=');
        Some(IExpressionPE::Lookup(self.parse_arena.alloc(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(
            RangeL(range1.begin(), range2.end()),
            self.parse_arena.intern_str(&combined),
          )),
          template_args: None,
        })))
      }
      (Some(INodeLEEnum::Symbol(SymbolLE(range, c))), _, _) => {
        iter.advance();
        Some(IExpressionPE::Lookup(self.parse_arena.alloc(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(
            range,
            self.parse_arena.intern_str(&c.to_string()),
          )),
          template_args: None,
        })))
      }
      (Some(INodeLEEnum::Word(WordLE { range, str })), _, _) => {
        iter.advance();
        Some(IExpressionPE::Lookup(self.parse_arena.alloc(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(range, str)),
          template_args: None,
        })))
      }
      _ => None,
    }
  }

  /// Parse a boolean literal
  pub fn parse_boolean(&self, iter: &mut ScrambleIterator<'p, '_>) -> Option<IExpressionPE<'p>> {
    if let Some(range) = iter.try_skip_word(self.keywords.truue) {
      return Some(IExpressionPE::ConstantBool(ConstantBoolPE {
        range,
        value: true,
      }));
    }
    if let Some(range) = iter.try_skip_word(self.keywords.faalse) {
      return Some(IExpressionPE::ConstantBool(ConstantBoolPE {
        range,
        value: false,
      }));
    }
    None
  }
  
  /// Parse an atomic expression
  pub fn parse_atom(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    stop_on_curlied: bool,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<IExpressionPE<'p>> {
    assert!(iter.has_next());
    let begin = iter.get_pos();

    // Check for keywords that can't be used in expressions (lines 960-969)
    if iter.try_skip_word(self.keywords.r#break).is_some() {
      return Err(ParseError::CantUseBreakInExpression(iter.get_pos()));
    }
    if iter.try_skip_word(self.keywords.retuurn).is_some() {
      return Err(ParseError::CantUseReturnInExpression(iter.get_pos()));
    }
    if iter.try_skip_word(self.keywords.whiile).is_some() {
      return Err(ParseError::CantUseWhileInExpression(iter.get_pos()));
    }

    // Check for underscore (magic param lookup) (lines 970-973)
    if let Some(range) = iter.try_skip_word(self.keywords.underscore) {
      return Ok(IExpressionPE::MagicParamLookup(MagicParamLookupPE {
        range,
      }));
    }

    // Try foreach (lines 974-978)
    if let Some(x) = self.parse_foreach(iter, templex_parser, pattern_parser)? {
      return Ok(x);
    }

    // Try mut expression (lines 980-984)
    if let Some(x) = self.parse_mut_expr(iter, stop_on_curlied, templex_parser, pattern_parser)? {
      return Ok(x);
    }

    // Parse literals (lines 986-1014)
    match iter.peek_cloned() {
      Some(INodeLEEnum::ParsedInteger(ParsedIntegerLE { range, value, bits })) => {
        iter.advance();
        return Ok(IExpressionPE::ConstantInt(ConstantIntPE {
          range,
          value,
          bits,
        }));
      }
      Some(INodeLEEnum::ParsedDouble(ParsedDoubleLE { range, value, .. })) => {
        iter.advance();
        return Ok(IExpressionPE::ConstantFloat(ConstantFloatPE {
          range,
          value,
        }));
      }
      Some(INodeLEEnum::String(StringLE { range, parts })) => {
        iter.advance();

        // Check if it's a simple literal string
        if parts.len() == 1 {
          if let StringPart::Literal { s, .. } = &parts[0] {
            return Ok(IExpressionPE::ConstantStr(ConstantStrPE {
              range,
              value: self.parse_arena.intern_str(s),
            }));
          }
        }

        // String interpolation
        let mut parts_p: Vec<&'p IExpressionPE<'p>> = Vec::new();
        for part in parts {
          match part {
            StringPart::Literal { range, s } => {
              parts_p.push(self.parse_arena.alloc(IExpressionPE::ConstantStr(ConstantStrPE {
                range: *range,
                value: self.parse_arena.intern_str(s.as_str()),
              })));
            }
            StringPart::Expr(scramble) => {
              let scramble_clone = scramble.clone();
              let mut part_iter = ScrambleIterator::new(&scramble_clone);
              let expr =
                self.parse_expression(&mut part_iter, false, templex_parser, pattern_parser)?;
              parts_p.push(expr);
            }
          }
        }
        return Ok(IExpressionPE::StrInterpolate(StrInterpolatePE {
          range,
          parts: self.parse_arena.alloc_slice_from_vec(parts_p),
        }));
      }
      _ => {}
    }

    // Try boolean (lines 1015-1018)
    if let Some(e) = self.parse_boolean(iter) {
      return Ok(e);
    }

    // Try array (lines 1019-1023)
    if let Some(e) = self.parse_array(iter, templex_parser, pattern_parser)? {
      return Ok(e);
    }

    // Try lambda (lines 1024-1028)
    if let Some(e) = self.parse_lambda(iter, templex_parser, pattern_parser)? {
      return Ok(e);
    }

    // Try lookup (lines 1029-1032)
    if let Some(e) = self.parse_lookup(iter) {
      return Ok(e);
    }

    // Try tuple or sub-expression (lines 1033-1039)
    if let Some(e) = self.parse_tuple_or_sub_expression(iter, templex_parser, pattern_parser)? {
      return Ok(e);
    }

    // If nothing matched, error (continuing from line 1039+)
    Err(ParseError::BadExpressionBegin(begin))
  }
  
  /// Parse a spree step (method call, field access, etc.)
  pub fn parse_spree_step(
    &self,
    spree_begin: i32,
    iter: &mut ScrambleIterator<'p, '_>,
    expr_so_far: &'p IExpressionPE<'p>,
    stop_on_curlied: bool,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<Option<IExpressionPE<'p>>> {
    let operator_begin = iter.get_pos();

    // Check for & (borrow augmentation)
    if iter.try_skip_symbol('&') {
      let range_pe = AugmentPE {
      range: RangeL(spree_begin, iter.get_prev_end_pos()),
      target_ownership: OwnershipP::Borrow,
        inner: expr_so_far,
      };
      return Ok(Some(IExpressionPE::Augment(range_pe)));
    }

    // Try template lookup
    match self.parse_template_lookup(iter, expr_so_far, templex_parser)? {
      Some(call) => return Ok(Some(IExpressionPE::Lookup(self.parse_arena.alloc(call)))),
      None => {}
    }

    // Try function call
    match self.parse_function_call(
      iter,
      spree_begin,
      expr_so_far,
      templex_parser,
      pattern_parser,
    )? {
      Some(call) => return Ok(Some(call)),
      None => {}
    }

    // Try brace pack (e.g., foo[1, 2, 3])
    match self.parse_brace_pack(iter, templex_parser, pattern_parser)? {
      Some(arg_exprs) => {
        return Ok(Some(IExpressionPE::BraceCall(BraceCallPE {
          range: RangeL(spree_begin, iter.get_prev_end_pos()),
          operator_range: RangeL(operator_begin, iter.get_prev_end_pos()),
          subject_expr: expr_so_far,
          arg_exprs: self.parse_arena.alloc_slice_from_vec(arg_exprs),
          callable_readwrite: false,
        })));
      }
      None => {}
    }

    // Check for range operator (..)
    if iter.try_skip_symbols(&['.', '.']) {
      let operand = self.parse_atom(iter, stop_on_curlied, templex_parser, pattern_parser)?;
      let range_pe = RangePE {
        range: RangeL(spree_begin, iter.get_prev_end_pos()),
        from_expr: expr_so_far,
        to_expr: self.parse_arena.alloc(operand),
      };
      return Ok(Some(IExpressionPE::Range(range_pe)));
    }

    // Check for map call (*.) or method call (.)
    let is_map_call = iter.try_skip_symbols(&['*', '.']);
    let is_method_call = if is_map_call {
      false
    } else {
      iter.try_skip_symbol('.')
    };

    let operator_end = iter.get_prev_end_pos();

    if is_method_call || is_map_call {
      let name_begin = iter.get_pos();
      let name = match iter.peek_cloned() {
        Some(INodeLEEnum::ParsedInteger(ParsedIntegerLE {
          value: int, bits, ..
        })) => {
          let bits = bits.clone();
          iter.advance();
          if int < 0 {
            return Err(ParseError::BadDot(iter.get_pos()));
          }
          if bits.is_some() {
            return Err(ParseError::BadDot(iter.get_pos()));
          }
          NameP(
            RangeL(name_begin, iter.get_prev_end_pos()),
            self.parse_arena.intern_str(&int.to_string()),
          )
        }
        Some(INodeLEEnum::Symbol(_)) => {
          let name = match iter.peek3_cloned() {
            (
              Some(INodeLEEnum::Symbol(SymbolLE(_, '<'))),
              Some(INodeLEEnum::Symbol(SymbolLE(_, '='))),
              Some(INodeLEEnum::Symbol(SymbolLE(_, '>'))),
            ) => self.keywords.spaceship,
            (
              Some(INodeLEEnum::Symbol(SymbolLE(_, '='))),
              Some(INodeLEEnum::Symbol(SymbolLE(_, '='))),
              Some(INodeLEEnum::Symbol(SymbolLE(_, '='))),
            ) => self.keywords.triple_equals,
            (
              Some(INodeLEEnum::Symbol(SymbolLE(_, '>'))),
              Some(INodeLEEnum::Symbol(SymbolLE(_, '='))),
              _,
            ) => self.keywords.greater_equals,
            (
              Some(INodeLEEnum::Symbol(SymbolLE(_, '<'))),
              Some(INodeLEEnum::Symbol(SymbolLE(_, '='))),
              _,
            ) => self.keywords.less_equals,
            (
              Some(INodeLEEnum::Symbol(SymbolLE(_, '!'))),
              Some(INodeLEEnum::Symbol(SymbolLE(_, '='))),
              _,
            ) => self.keywords.not_equals,
            (
              Some(INodeLEEnum::Symbol(SymbolLE(_, '='))),
              Some(INodeLEEnum::Symbol(SymbolLE(_, '='))),
              _,
            ) => self.keywords.double_equals,
            (Some(INodeLEEnum::Symbol(SymbolLE(_, '+'))), _, _) => self.keywords.plus,
            (Some(INodeLEEnum::Symbol(SymbolLE(_, '-'))), _, _) => self.keywords.minus,
            (Some(INodeLEEnum::Symbol(SymbolLE(_, '*'))), _, _) => self.keywords.asterisk,
            (Some(INodeLEEnum::Symbol(SymbolLE(_, '/'))), _, _) => self.keywords.slash,
            _ => return Err(ParseError::BadDot(iter.get_pos())),
          };
          // Advance by the length of the keyword
          for _ in 0..name.as_str().len() {
            iter.advance();
          }
          NameP(
            RangeL(name_begin, iter.get_prev_end_pos()),
            name,
          )
        }
        Some(INodeLEEnum::Word(WordLE { str, .. })) => {
          iter.advance();
          NameP(
            RangeL(name_begin, iter.get_prev_end_pos()),
            str,
          )
        }
        _ => return Err(ParseError::BadDot(iter.get_pos())),
      };

      let maybe_template_args = match self.parse_chevron_pack(iter, templex_parser)? {
        None => None,
        Some(template_args) => Some(TemplateArgsP {
          range: RangeL(operator_begin, iter.get_prev_end_pos()),
          args: self.parse_arena.alloc_slice_from_vec(template_args),
        }),
      };

      match self.parse_pack(iter, templex_parser, pattern_parser)? {
        Some((range, arg_exprs)) => {
          return Ok(Some(IExpressionPE::MethodCall(MethodCallPE {
            range: RangeL(operator_begin, range.end()),
            subject_expr: expr_so_far,
            operator_range: RangeL(operator_begin, operator_end),
            method_lookup: self.parse_arena.alloc(LookupPE {
              name: IImpreciseNameP::LookupName(name),
              template_args: maybe_template_args,
            }),
            arg_exprs: self.parse_arena.alloc_slice_from_vec(arg_exprs),
          })));
        }
        None => {
          if maybe_template_args.is_some() {
            return Err(ParseError::CantTemplateCallMember(iter.get_pos()));
          }

          return Ok(Some(IExpressionPE::Dot(DotPE {
            range: RangeL(spree_begin, iter.get_prev_end_pos()),
            left: expr_so_far,
            operator_range: RangeL(operator_begin, operator_end),
            member: name,
          })));
        }
      }
    }

    Ok(None)
  }
  
  /// Parse a function call
  pub fn parse_function_call(
    &self,
    original_iter: &mut ScrambleIterator<'p, '_>,
    spree_begin: i32,
    expr_so_far: &'p IExpressionPE<'p>,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<Option<IExpressionPE<'p>>>
  {
    let mut tentative_iter = original_iter.clone();
    let operator_begin = tentative_iter.get_pos();

    match self.parse_pack(&mut tentative_iter, templex_parser, pattern_parser)? {
      None => Ok(None),
      Some((range, args)) => {
        original_iter.skip_to(&tentative_iter);
        Ok(Some(IExpressionPE::FunctionCall(FunctionCallPE {
          range: RangeL(spree_begin, range.end()),
          operator_range: RangeL(operator_begin, range.end()),
          callable_expr: expr_so_far,
          arg_exprs: self.parse_arena.alloc_slice_from_vec(args),
        })))
      }
    }
  }
  
  /// Parse an atom and tight suffixes
  pub fn parse_atom_and_tight_suffixes(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    stop_on_curlied: bool,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<&'p IExpressionPE<'p>> {
    assert!(iter.has_next());
    let begin = iter.get_pos();

    let mut expr_so_far: &'p IExpressionPE<'p> = self.parse_arena.alloc(
      self.parse_atom(iter, stop_on_curlied, templex_parser, pattern_parser)?);

    let mut continuing = true;
    while continuing && iter.has_next() {
      match self.parse_spree_step(
        begin,
        iter,
        expr_so_far,
        stop_on_curlied,
        templex_parser,
        pattern_parser,
      )? {
        None => {
          continuing = false;
        }
        Some(new_expr) => {
          expr_so_far = self.parse_arena.alloc(new_expr);
        }
      }
    }

    Ok(expr_so_far)
  }
  
  /// Parse chevron pack (template arguments)
  pub fn parse_chevron_pack(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    templex_parser: &TemplexParser<'p, 'ctx>,
  ) -> ParseResult<Option<Vec<&'p ITemplexPT<'p>>>>
  {
    match iter.peek_cloned() {
      Some(INodeLEEnum::Angled(AngledLE { contents, .. })) => {
        let contents = contents.clone();
        iter.advance();

        let scramble = ScrambleIterator::new(&contents);
        let element_iters = scramble.split_on_symbol(',', false);

        let mut result: Vec<&'p ITemplexPT<'p>> = vec![];
        for mut element_iter in element_iters {
          let templex = templex_parser.parse_templex(&mut element_iter)?;
          result.push(&*self.parse_arena.alloc(templex));
        }

        Ok(Some(result))
      }
      _ => Ok(None),
    }
  }
  
  /// Parse a template lookup
  pub fn parse_template_lookup(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    expr_so_far: &'p IExpressionPE<'p>,
    templex_parser: &TemplexParser<'p, 'ctx>,
  ) -> ParseResult<Option<LookupPE<'p>>> {
    let operator_begin = iter.get_pos();

    let template_args = match self.parse_chevron_pack(iter, templex_parser)? {
      None => return Ok(None),
      Some(template_args) => TemplateArgsP {
        range: RangeL(operator_begin, iter.get_prev_end_pos()),
        args: self.parse_arena.alloc_slice_from_vec(template_args),
      },
    };

    let result_pe = match expr_so_far {
      IExpressionPE::Lookup(lookup) if lookup.template_args.is_none() => LookupPE {
        name: lookup.name.clone(),
        template_args: Some(template_args),
      },
      _ => return Err(ParseError::BadTemplateCallee(operator_begin)),
    };

    Ok(Some(result_pe))
  }
  
  /// Parse a pack (parens, squares, or curlies)
  pub fn parse_pack(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<Option<(RangeL, Vec<&'p IExpressionPE<'p>>)>> {
    let parend_le = match iter.peek_cloned() {
      Some(INodeLEEnum::Parend(p)) => {
        let p = p.clone();
        iter.advance();
        p
      }
      _ => return Ok(None),
    };

    let segment_iters = ScrambleIterator::new(&parend_le.contents).split_on_symbol(',', false);

    let mut elements = vec![];
    for mut element_iter in segment_iters {
      let expr = self.parse_expression(&mut element_iter, false, templex_parser, pattern_parser)?;
      elements.push(expr);
    }

    Ok(Some((parend_le.range, elements)))
  }
  
  /// Parse a square pack (array/seq literal)
  pub fn parse_square_pack(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<Option<Vec<&'p IExpressionPE<'p>>>> {
    let squared_le = match iter.peek_cloned() {
      Some(INodeLEEnum::Squared(p)) => {
        let p = p.clone();
        iter.advance();
        p
      }
      None => return Ok(None),
      _ => return Ok(None),
    };

    let segment_iters = ScrambleIterator::new(&squared_le.contents).split_on_symbol(',', false);

    let mut elements_p = vec![];
    for mut element_iter in segment_iters {
      let expr = self.parse_expression(&mut element_iter, false, templex_parser, pattern_parser)?;
      elements_p.push(expr);
    }

    Ok(Some(elements_p))
  }
  
  /// Parse a brace pack
  pub fn parse_brace_pack(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<Option<Vec<&'p IExpressionPE<'p>>>> {
    match iter.peek_cloned() {
      Some(INodeLEEnum::Squared(SquaredLE { contents, .. })) => {
        let contents = contents.clone();
        iter.advance();

        let scramble_iter = ScrambleIterator::new(&contents);
        let element_iters: Vec<ScrambleIterator<'p, '_>> = scramble_iter.split_on_symbol(',', false);

        let mut elements = vec![];
        for mut element_iter in element_iters {
          let expr =
            self.parse_expression(&mut element_iter, false, templex_parser, pattern_parser)?;
          elements.push(expr);
        }

        Ok(Some(elements))
      }
      _ => Ok(None),
    }
  }
  
  /// Parse a tuple or sub-expression
  pub fn parse_tuple_or_sub_expression(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<Option<IExpressionPE<'p>>> {
    match iter.peek_cloned() {
      Some(INodeLEEnum::Parend(ParendLE { range, contents })) => {
        let contents = contents.clone();
        iter.advance();

        let mut iters = ScrambleIterator::new(&contents).split_on_symbol(',', true);

        assert!(!iters.is_empty());

        if iters.len() == 1 {
          if !iters[0].has_next() {
            // Then we have e.g. ()
            return Ok(Some(IExpressionPE::Tuple(TuplePE {
              range,
              elements: self.parse_arena.alloc_slice_from_vec(vec![]),
            })));
          } else {
            // Then we have e.g. (true)
            let inner =
              self.parse_expression(&mut iters[0], false, templex_parser, pattern_parser)?;
            return Ok(Some(IExpressionPE::SubExpression(SubExpressionPE {
              range,
              inner: self.parse_arena.alloc(inner),
            })));
          }
        } else {
          // Then we have e.g. (true,) or (true,true) etc.
          let mut element_iters = if !iters.last().unwrap().has_next() {
            // Last is empty, like in (true,) so take it out
            iters.pop();
            iters
          } else {
            iters
          };

          let mut elements_p = vec![];
          for element_iter in element_iters.iter_mut() {
            let expr =
              self.parse_expression(element_iter, false, templex_parser, pattern_parser)?;
            elements_p.push(expr);
          }

          return Ok(Some(IExpressionPE::Tuple(TuplePE {
            range,
            elements: self.parse_arena.alloc_slice_from_vec(elements_p),
          })));
        }
      }
      _ => Ok(None),
    }
  }
  
  /// Parse expression data element
  pub fn parse_expression_data_element(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    stop_on_curlied: bool,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<&'p IExpressionPE<'p>> {
    assert!(iter.has_next());

    let begin = iter.get_pos();

    // Handle … symbol (Scala line 1422-1424)
    if iter.try_skip_symbol('…') {
      return Ok(self.parse_arena.alloc(IExpressionPE::ConstantInt(ConstantIntPE {
        range: RangeL::new(begin, iter.get_prev_end_pos()),
        value: 0,
        bits: None,
      })));
    }

    // Handle single quote prefix (Scala line 1426-1432)
    match iter.peek2_cloned() {
      (Some(INodeLEEnum::Symbol(SymbolLE(_, '\''))), Some(INodeLEEnum::Word(_))) => {
        iter.advance();
        iter.advance();
        return self.parse_expression_data_element(
          iter,
          stop_on_curlied,
          templex_parser,
          pattern_parser,
        );
      }
      _ => {}
    }

    // Handle 'not' keyword (Scala line 1438-1445)
    if iter.try_skip_word(self.keywords.not).is_some() {
      let inner_pe = self.parse_expression_data_element(
        iter,
        stop_on_curlied,
        templex_parser,
        pattern_parser,
      )?;
      let end = inner_pe.range().end();
      return Ok(self.parse_arena.alloc(IExpressionPE::Not(NotPE {
        range: RangeL::new(begin, end),
        inner: inner_pe,
      })));
    }

    // Handle lone blocks (Scala line 1447-1451)
    if let Some(block) = self.parse_lone_block(iter, templex_parser, pattern_parser)? {
      return Ok(self.parse_arena.alloc(block));
    }

    // Handle if ladders (Scala line 1453-1457)
    if let Some(if_expr) = self.parse_if_ladder(iter, templex_parser, pattern_parser)? {
      return Ok(self.parse_arena.alloc(if_expr));
    }

    // Handle destruct (Scala line 1461-1465)
    if let Some(destruct) =
      self.parse_destruct(iter, stop_on_curlied, templex_parser, pattern_parser)?
    {
      return Ok(self.parse_arena.alloc(destruct));
    }

    // Handle foreach (Scala line 1467-1471)
    if let Some(foreach) = self.parse_foreach(iter, templex_parser, pattern_parser)? {
      return Ok(self.parse_arena.alloc(foreach));
    }

    // Handle unlet (Scala line 1473-1477)
    if let Some(unlet) = self.parse_unlet(iter)? {
      return Ok(self.parse_arena.alloc(unlet));
    }

    // Handle transmigration region'expr (Scala line 1479-1493)
    match iter.peek2_cloned() {
      (
        Some(INodeLEEnum::Word(WordLE {
          range: region_range,
          str: region,
        })),
        Some(INodeLEEnum::Symbol(SymbolLE(_, '\''))),
      ) => {
        let region_name = NameP(region_range, region);
        iter.advance();
        iter.advance();
        let inner_pe = self.parse_atom_and_tight_suffixes(
          iter,
          stop_on_curlied,
          templex_parser,
          pattern_parser,
        )?;
        return Ok(self.parse_arena.alloc(IExpressionPE::Transmigrate(TransmigratePE {
          range: RangeL::new(begin, iter.get_prev_end_pos()),
          target_region: region_name,
          inner: inner_pe,
        })));
      }
      _ => {}
    }

    // Handle ownership prefixes ^ & && inl (Scala line 1495-1531)
    let maybe_target_ownership = match iter.peek_cloned() {
      Some(INodeLEEnum::Symbol(SymbolLE(_, '^'))) => {
        iter.advance();
        Some(OwnershipP::Own)
      }
      Some(INodeLEEnum::Symbol(SymbolLE(_, '&'))) => {
        iter.advance();
        match iter.peek_cloned() {
          Some(INodeLEEnum::Symbol(SymbolLE(_, '&'))) => {
            iter.advance();
            Some(OwnershipP::Weak)
          }
          _ => Some(OwnershipP::Borrow),
        }
      }
      Some(INodeLEEnum::Word(WordLE { str, .. })) if str == self.keywords.r#inl => {
        iter.advance();
        Some(OwnershipP::Own)
      }
      _ => None,
    };

    if let Some(target_ownership) = maybe_target_ownership {
      let inner_pe = self.parse_atom_and_tight_suffixes(
        iter,
        stop_on_curlied,
        templex_parser,
        pattern_parser,
      )?;
      return Ok(self.parse_arena.alloc(IExpressionPE::Augment(AugmentPE {
        range: RangeL::new(begin, iter.get_prev_end_pos()),
        target_ownership,
        inner: inner_pe,
      })));
    }

    // Now parse the atom and tight suffixes (Scala line 1541)
    self.parse_atom_and_tight_suffixes(iter, stop_on_curlied, templex_parser, pattern_parser)
  }
  
  /// Parse a braced body
  pub fn parse_braced_body(&self, _iter: &mut ScrambleIterator<'p, '_>) -> ParseResult<BlockPE<'p>> {
    panic!("parse_braced_body: NOT IMPLEMENTED - marked vimpl() in Scala ExpressionParser.scala line 1545")
  }
  
  /// Parse single-arg lambda begin
  pub fn parse_single_arg_lambda_begin(
    &self,
    _original_iter: &mut ScrambleIterator<'p, '_>,
  ) -> Option<ParamsP<'p>> {
    panic!("parse_single_arg_lambda_begin: NOT IMPLEMENTED - marked vimpl() in Scala ExpressionParser.scala line 1563")
  }
  
  /// Parse multi-arg lambda begin
  pub fn parse_multi_arg_lambda_begin(
    &self,
    _original_iter: &mut ScrambleIterator<'p, '_>,
  ) -> Option<ParamsP<'p>> {
    panic!("parse_multi_arg_lambda_begin: NOT IMPLEMENTED - marked vimpl() in Scala ExpressionParser.scala line 1587")
  }
  
  /// Parse a lambda
  pub fn parse_lambda(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<Option<IExpressionPE<'p>>> {
    let begin = iter.get_pos();

    let header_p = match iter.peek3_cloned() {
      // Just a curlied block with no params (e.g., { ... })
      (Some(INodeLEEnum::Curlied(CurliedLE { range, .. })), _, _) => {
        let retuurn = FunctionReturnP {
          range: RangeL(iter.get_pos(), iter.get_pos()),
          ret_type: None,
        };
        // Don't iter.advance() because we still need to parse this later
        FunctionHeaderP {
          range,
          name: None,
          attributes: self.parse_arena.alloc_slice_from_vec(vec![]),
          generic_parameters: None,
          template_rules: None,
          params: None,
          ret: retuurn,
        }
      }
      // Single param lambda: x => ...
      (
        Some(INodeLEEnum::Word(WordLE {
          range: param_range,
          str: param_name,
        })),
        Some(INodeLEEnum::Symbol(SymbolLE(eq_range, '='))),
        Some(INodeLEEnum::Symbol(SymbolLE(gt_range, '>'))),
      ) => {
        if eq_range.end() != gt_range.begin() {
          return Err(ParseError::BadLambdaBegin(eq_range.begin()));
        }
        iter.advance();
        iter.advance();
        iter.advance();

        let param = ParameterP {
          range: param_range,
          virtuality: None,
          maybe_pre_checked: None,
          self_borrow: None,
          pattern: Some(PatternPP {
            range: param_range,
            destination: Some(DestinationLocalP {
              decl: INameDeclarationP::LocalNameDeclaration(NameP(param_range, param_name)),
              mutate: None,
            }),
            templex: None,
            destructure: None,
          }),
        };
        let params = ParamsP {
          range: param_range,
          params: self.parse_arena.alloc_slice_from_vec(vec![param]),
        };
        let retuurn = FunctionReturnP {
          range: RangeL(iter.get_pos(), iter.get_pos()),
          ret_type: None,
        };
        let range = RangeL(begin, iter.get_prev_end_pos());
        FunctionHeaderP {
          range,
          name: None,
          attributes: self.parse_arena.alloc_slice_from_vec(vec![]),
          generic_parameters: None,
          template_rules: None,
          params: Some(params),
          ret: retuurn,
        }
      }
      // Multi-param lambda: (x, y) => ...
      (
        Some(INodeLEEnum::Parend(ParendLE {
          range: params_range,
          contents: params_contents,
        })),
        Some(INodeLEEnum::Symbol(SymbolLE(eq_range, '='))),
        Some(INodeLEEnum::Symbol(SymbolLE(gt_range, '>'))),
      ) => {
        let params_contents = params_contents.clone();
        if eq_range.end() != gt_range.begin() {
          return Err(ParseError::BadLambdaBegin(eq_range.begin()));
        }
        iter.advance();
        iter.advance();
        iter.advance();

        let param_iters = ScrambleIterator::new(&params_contents).split_on_symbol(',', false);

        let mut patterns = vec![];
        for (index, mut pattern_iter) in param_iters.into_iter().enumerate() {
          let param = pattern_parser.parse_parameter(
            &mut pattern_iter,
            templex_parser,
            index,
            false,
            true,
            true,
          )?;
          patterns.push(param);
        }

        let params_p = ParamsP {
          range: params_range,
          params: self.parse_arena.alloc_slice_from_vec(patterns),
        };
        let retuurn = FunctionReturnP {
          range: RangeL(iter.get_pos(), iter.get_pos()),
          ret_type: None,
        };
        let range = RangeL(begin, iter.get_prev_end_pos());
        FunctionHeaderP {
          range,
          name: None,
          attributes: self.parse_arena.alloc_slice_from_vec(vec![]),
          generic_parameters: None,
          template_rules: None,
          params: Some(params_p),
          ret: retuurn,
        }
      }
      (_, _, _) => return Ok(None),
    };

    let body_p = match iter.peek_cloned() {
      Some(INodeLEEnum::Curlied(block_l)) => {
        let block_l = block_l.clone();
        iter.advance();
        // parseBlock returns IExpressionPE, we need to wrap it in BlockPE
        // Scala lines 1697-1707
        let statements_p = self.parse_block(&block_l, templex_parser, pattern_parser)?;
        BlockPE {
          range: block_l.range,
          maybe_pure: None,
          // Would we ever want a lambda with a different default region?
          maybe_default_region: None,
          inner: self.parse_arena.alloc(statements_p),
        }
      }
      Some(_) => {
        // Scala lines 1709-1720
        let result = self.parse_expression(iter, false, templex_parser, pattern_parser)?;
        BlockPE {
          range: result.range(),
          maybe_pure: None,
          // Would we ever want a lambda with a different default region?
          maybe_default_region: None,
          inner: self.parse_arena.alloc(result),
        }
      }
      None => panic!("LAMBDA_MISSING_BODY: Expected body for lambda - not in Scala"),
    };

    let lam = LambdaPE {
      captures: None,
      function: FunctionP {
        range: RangeL(begin, iter.get_prev_end_pos()),
        header: header_p,
        body: Some(self.parse_arena.alloc(body_p)),
      },
    };

    Ok(Some(IExpressionPE::Lambda(lam)))
  }
  
  /// Parse an array literal
  pub fn parse_array(
    &self,
    original_iter: &mut ScrambleIterator<'p, '_>,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_parser: &PatternParser<'p, 'ctx>,
  ) -> ParseResult<Option<IExpressionPE<'p>>> {
    let mut tentative_iter = original_iter.clone();
    let begin = tentative_iter.get_pos();

    let mutability = if tentative_iter.try_skip_symbol('#') {
      ITemplexPT::Mutability(MutabilityPT(
        RangeL(begin, tentative_iter.get_prev_end_pos()),
        MutabilityP::Immutable,
      ))
    } else {
      ITemplexPT::Mutability(MutabilityPT(RangeL(begin, begin), MutabilityP::Mutable))
    };

    // If there's no square, we're not making an array.
    let sizer = match tentative_iter.peek_cloned() {
      Some(INodeLEEnum::Squared(s)) => s.clone(),
      _ => return Ok(None),
    };
    tentative_iter.advance();

    let is_array = match tentative_iter.peek_cloned() {
      // If there's nothing after the square brackets, it's not an array.
      None => false,
      Some(INodeLEEnum::Symbol(SymbolLE(_, '.'))) => false,
      _ => true,
    };

    if !is_array {
      // Not an array, bail.
      // TODO: Someday, we could interpret this occurrence as a way to make a List.
      return Ok(None);
    }

    original_iter.skip_to(&tentative_iter);
    let iter = original_iter;

    let sizer_contents = sizer.contents.clone();
    let mut sizer_iter = ScrambleIterator::new(&sizer_contents);
    let size = if sizer_iter.try_skip_symbol('#') {
      let size_pt = if sizer_iter.has_next() {
        Some(templex_parser.parse_templex(&mut sizer_iter)?)
      } else {
        None
      };
      IArraySizeP::StaticSized(StaticSizedArraySizeP { size_pt })
    } else {
      IArraySizeP::RuntimeSized
    };

    let tyype = match iter.peek_cloned() {
      Some(INodeLEEnum::Parend(_)) => None,
      Some(_) => Some(templex_parser.parse_templex(iter)?),
      None => return Err(ParseError::BadArraySpecifier(iter.get_pos())),
    };

    let args = match self.parse_pack(iter, templex_parser, pattern_parser)? {
      None => return Err(ParseError::BadArraySpecifier(iter.get_pos())),
      Some((_range, e)) => e,
    };

    let initializing_individual_elements = match &size {
      IArraySizeP::StaticSized(StaticSizedArraySizeP { size_pt: None }) => true, // e.g. [#](3, 4, 5)
      IArraySizeP::StaticSized(StaticSizedArraySizeP { size_pt: Some(_) }) => false, // Anything else should take a function.
      IArraySizeP::RuntimeSized => false, // Any runtime sized array should take a function.
    };

    let array_pe = ConstructArrayPE {
      range: RangeL(begin, iter.get_prev_end_pos()),
      type_pt: tyype,
      mutability_pt: Some(mutability),
      variability_pt: None,
      size,
      initializing_individual_elements,
      args: self.parse_arena.alloc_slice_from_vec(args),
    };

    Ok(Some(IExpressionPE::ConstructArray(array_pe)))
  }
  
  /// Descramble - converts scrambled expression elements to properly structured AST
  fn descramble_elements(
    &self,
    elements: &[ExpressionElement<'p>],
    begin_index_inclusive: usize,
    end_index_inclusive: usize,
    min_precedence: i32,
  ) -> ParseResult<(&'p IExpressionPE<'p>, usize)> {
    assert!(!elements.is_empty());
    assert!(elements.len() % 2 == 1);

    const MAX_PRECEDENCE: i32 = 6;

    // Base cases (lines 1832-1839)
    if begin_index_inclusive == end_index_inclusive {
      if let ExpressionElement::Data(expr) = &elements[begin_index_inclusive] {
        return Ok((*expr, begin_index_inclusive + 1));
      } else {
        panic!("Expected DataElement");
      }
    }
    if min_precedence == MAX_PRECEDENCE {
      if let ExpressionElement::Data(expr) = &elements[begin_index_inclusive] {
        return Ok((*expr, begin_index_inclusive + 1));
      } else {
        panic!("Expected DataElement");
      }
    }

    // Recursive descent (lines 1841-1842)
    let (mut left_operand, mut next_index) = self.descramble_elements(
      elements,
      begin_index_inclusive,
      end_index_inclusive,
      min_precedence + 1,
    )?;

    // Process operators at this precedence level (lines 1844-1876)
    while next_index < end_index_inclusive {
      if let ExpressionElement::BinaryCall(_, precedence) = &elements[next_index] {
        if *precedence != min_precedence {
          break;
        }
      } else {
        break;
      }

      let binary_call = if let ExpressionElement::BinaryCall(symbol, _) = &elements[next_index] {
        symbol.clone()
      } else {
        panic!("Expected BinaryCallElement");
      };
      next_index += 1;

      let (right_operand, new_next_index) = self.descramble_elements(
        elements,
        next_index,
        end_index_inclusive,
        min_precedence + 1,
      )?;
      next_index = new_next_index;

      // Construct the appropriate expression (lines 1854-1875)
      left_operand = if binary_call.str() == self.keywords.and {
        self.parse_arena.alloc(IExpressionPE::And(AndPE {
          range: RangeL(left_operand.range().begin(), right_operand.range().end()),
          left: left_operand,
          right: self.parse_arena.alloc(BlockPE {
            range: right_operand.range(),
            maybe_pure: None,
            maybe_default_region: None,
            inner: right_operand,
          }),
        }))
      } else if binary_call.str() == self.keywords.or {
        self.parse_arena.alloc(IExpressionPE::Or(OrPE {
          range: RangeL(left_operand.range().begin(), right_operand.range().end()),
          left: left_operand,
          right: self.parse_arena.alloc(BlockPE {
            range: right_operand.range(),
            maybe_pure: None,
            maybe_default_region: None,
            inner: right_operand,
          }),
        }))
      } else {
        self.parse_arena.alloc(IExpressionPE::BinaryCall(BinaryCallPE {
          range: RangeL(left_operand.range().begin(), right_operand.range().end()),
          function_name: binary_call,
          left_expr: left_operand,
          right_expr: right_operand,
        }))
      };
    }

    Ok((left_operand, next_index))
  }
  
  /// Parse a binary call
  pub fn parse_binary_call(&self, iter: &mut ScrambleIterator<'p, '_>) -> ParseResult<Option<NameP<'p>>> {
    let name = match iter.peek3_cloned() {
      (Some(INodeLEEnum::Word(WordLE { range, str })), _, _) => {
        iter.advance();
        NameP(range, str)
      }
      (
        Some(INodeLEEnum::Symbol(SymbolLE(range, s @ ('+' | '-' | '*' | '/')))),
        _,
        _,
      ) => {
        iter.advance();
        let str_i = match s {
          '+' => self.keywords.plus,
          '-' => self.keywords.minus,
          '*' => self.keywords.asterisk,
          '/' => self.keywords.slash,
          _ => unreachable!(),
        };
        NameP(range, str_i)
      }
      (
        Some(INodeLEEnum::Symbol(SymbolLE(_, '='))),
        Some(INodeLEEnum::Symbol(SymbolLE(_, '='))),
        Some(INodeLEEnum::Symbol(SymbolLE(_, '='))),
      ) => {
        let begin = iter.get_pos();
        iter.advance();
        iter.advance();
        iter.advance();
        let end = iter.get_prev_end_pos();
        NameP(RangeL(begin, end), self.keywords.triple_equals)
      }
      (
        Some(INodeLEEnum::Symbol(SymbolLE(_, '<'))),
        Some(INodeLEEnum::Symbol(SymbolLE(_, '='))),
        Some(INodeLEEnum::Symbol(SymbolLE(_, '>'))),
      ) => {
        let begin = iter.get_pos();
        iter.advance();
        iter.advance();
        iter.advance();
        let end = iter.get_prev_end_pos();
        NameP(RangeL(begin, end), self.keywords.spaceship)
      }
      (
        Some(INodeLEEnum::Symbol(SymbolLE(
          range1,
          s1 @ ('>' | '<' | '=' | '!'),
        ))),
        Some(INodeLEEnum::Symbol(SymbolLE(range2, '='))),
        _,
      ) => {
        let begin = range1.begin();
        let end = range2.end();
        iter.advance();
        iter.advance();
        let str_i = match s1 {
          '!' => self.keywords.not_equals,
          '=' => self.keywords.double_equals,
          '<' => self.keywords.less_equals,
          '>' => self.keywords.greater_equals,
          _ => unreachable!(),
        };
        NameP(RangeL(begin, end), str_i)
      }
      (
        Some(INodeLEEnum::Symbol(SymbolLE(range, s @ ('>' | '<')))),
        _,
        _,
      ) => {
        iter.advance();
        let str_i = match s {
          '>' => self.keywords.greater,
          '<' => self.keywords.less,
          _ => unreachable!(),
        };
        NameP(range, str_i)
      }
      _ => return Ok(None),
    };

    Ok(Some(name))
  }
  
  /// Check if at expression end
  pub fn at_expression_end(&self, iter: &ScrambleIterator, stop_on_curlied: bool) -> bool {
    match iter.peek_cloned() {
      None => true,
      Some(INodeLEEnum::Symbol(SymbolLE(_, ';'))) => true,
      Some(INodeLEEnum::Curlied(_)) if stop_on_curlied => true,
      _ => false,
    }
  }
  
}

