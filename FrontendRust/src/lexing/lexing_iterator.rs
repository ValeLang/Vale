use super::ast::RangeL;
use std::cmp::min;

/// Lexing iterator for traversing source code
/// Matches Scala's LexingIterator
#[derive(Clone, Debug)]
pub struct LexingIterator<'a> {
  pub code: &'a str,
  pub position: usize, // Byte position in the string
  pub comments: Vec<RangeL>,
  
}
impl<'a> LexingIterator<'a> {
  /// Get the rest of the code from current position (for debugging)
  pub fn rest(&self) -> &str {
    &self.code[self.position..]
  }
  
  /// Consume comments and whitespace
  pub fn consume_comments_and_whitespace(&mut self) {
    // consumeComments will consume any whitespace that comes before the comment
    self.consume_comments();
    self.consume_whitespace();
  }
  
  /// Find end of whitespace without consuming
  fn find_whitespace_end(&self) -> usize {
    let mut pos = self.position;
    while pos < self.code.len() {
      match self.code[pos..].chars().next().unwrap() {
        ' ' | '\n' | '\r' | '\t' => {
          pos += 1;
        }
        _ => break,
      }
    }
    pos
  }
  
  /// Consume all types of comments
  fn consume_comments(&mut self) {
    self.consume_line_comments();
    self.consume_chevron_comments();
    self.consume_ellipses_comments();
  }
  
  /// Skip to past a specific character
  fn skip_to_past(&mut self, needle: char) -> bool {
    while !self.at_end() {
      let c = self.advance();
      if c == needle {
        return true;
      }
    }
    false
  }
  
  /// Consume chevron comments (« »)
  fn consume_chevron_comments(&mut self) {
    let pos_after_whitespace = self.find_whitespace_end();

    if pos_after_whitespace < self.code.len() && self.code[pos_after_whitespace..].starts_with('«')
    {
      let begin = self.position;
      self.position = pos_after_whitespace + '«'.len_utf8();

      self.skip_to_past('»');

      self.comments.push(RangeL(begin as i32, self.position as i32));

      self.consume_comments();
    }
  }
  
  /// Consume ellipses comments (...)
  fn consume_ellipses_comments(&mut self) {
    let pos_after_whitespace = self.find_whitespace_end();

    if pos_after_whitespace + 2 < self.code.len()
      && self.code.as_bytes()[pos_after_whitespace] == b'.'
      && self.code.as_bytes()[pos_after_whitespace + 1] == b'.'
      && self.code.as_bytes()[pos_after_whitespace + 2] == b'.'
    {
      let begin = self.position;
      self.position = pos_after_whitespace + 3;
      assert!(self.position <= self.code.len());
      self.comments.push(RangeL(begin as i32, self.position as i32));
      self.consume_comments();
    }

    self.try_skip_str("...");
  }
  
  /// Consume line comments (//)
  fn consume_line_comments(&mut self) {
    let pos_after_whitespace = self.find_whitespace_end();

    if pos_after_whitespace + 2 <= self.code.len()
      && self.code.as_bytes()[pos_after_whitespace] == b'/'
      && self.code.as_bytes()[pos_after_whitespace + 1] == b'/'
    {
      let begin = pos_after_whitespace;
      self.position = pos_after_whitespace + 2;
      assert!(self.position <= self.code.len());
      self.skip_to_past('\n');
      self.comments.push(RangeL(begin as i32, (self.position - 1) as i32));
      self.consume_comments();
    }
  }
  
  /// Peek ahead to get a substring of exact length
  pub fn peek_exact(&self, n: usize) -> Option<&str> {
    if self.position + n > self.code.len() {
      None
    } else {
      Some(&self.code[self.position..self.position + n])
    }
  }

  /// Advance by one character and return it
  pub fn advance(&mut self) -> char {
    if self.at_end() {
      '\0'
    } else {
      let c = self.peek();
      self.position += c.len_utf8();
      c
    }
  }
  
  /// Try to skip a specific character
  pub fn try_skip(&mut self, c: char) -> bool {
    if self.peek() == c {
      self.advance();
      true
    } else {
      false
    }
  }
  
  // Optimize: could replace with xor and bitwise and for small strings
  /// Try to skip a specific string
  pub fn try_skip_str(&mut self, s: &str) -> bool {
    if self.code[self.position..].starts_with(s) {
      self.position += s.len();
      true
    } else {
      false
    }
  }
  
  // Optimize: could replace with xor and bitwise and for small strings
  // A complete word is one that doesn't have any more word characters after it
  /// Try to skip a complete word (must be followed by non-identifier char)
  pub fn try_skip_complete_word(&mut self, word: &str) -> bool {
    if !self.code[self.position..].starts_with(word) {
      return false;
    }

    // Check that the next character is not an identifier character
    let after_pos = self.position + word.len();
    if after_pos < self.code.len() {
      let next_char = self.code[after_pos..].chars().next().unwrap();
      if next_char.is_alphanumeric() || next_char == '_' {
        return false;
      }
    }

    self.position += word.len();
    true
  }
  
  pub fn new(code: &'a str) -> Self {
    LexingIterator {
      code,
      position: 0,
      comments: Vec::new(),
    }
  }

  pub fn at_end(&self) -> bool {
    self.position >= self.code.len()
  }
  
  /// Skip to a specific position
  pub fn skip_to(&mut self, pos: usize) {
    self.position = pos;
  }
  
  pub fn get_pos(&self) -> i32 {
    self.position as i32
  }
  
  /// Consume whitespace
  pub fn consume_whitespace(&mut self) {
    while !self.at_end() {
      match self.peek() {
        ' ' | '\n' | '\r' | '\t' => {
          self.advance();
        }
        _ => break,
      }
    }
  }
  
  /// Peek if a string matches (without advancing)
  pub fn peek_string(&self, s: &str) -> bool {
    self.code[self.position..].starts_with(s)
  }
  
  /// Peek if a complete word matches (without advancing)
  pub fn peek_complete_word(&self, word: &str) -> bool {
    if !self.code[self.position..].starts_with(word) {
      return false;
    }

    let after_pos = self.position + word.len();
    if after_pos < self.code.len() {
      let next_char = self.code[after_pos..].chars().next().unwrap();
      if next_char.is_alphanumeric() || next_char == '_' {
        return false;
      }
    }

    true
  }
  
  /// Peek at the current character without advancing
  pub fn peek(&self) -> char {
    if self.at_end() {
      '\0'
    } else {
      self.code[self.position..].chars().next().unwrap()
    }
  }
  
  /// Peek ahead n characters (returns String)
  pub fn peek_n(&self, n: usize) -> Option<String> {
    if self.position + n > self.code.len() {
      None
    } else {
      Some(self.code[self.position..min(self.position + n, self.code.len())].to_string())
    }
  }
  
}

