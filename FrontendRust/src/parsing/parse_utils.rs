use crate::StrI;
use crate::lexing::ast::INodeLE;
use crate::lexing::ast::INodeLEEnum;
use crate::lexing::ast::SymbolLE;
use crate::lexing::WordLE;
use crate::lexing::ast::RangeL;
use crate::lexing::errors::ParseError;
use crate::parsing::ast::{NameP, RegionRunePT};
use crate::parsing::ScrambleIterator;

type ParseResult<T> = Result<T, ParseError>;

/// Parse optional region marker (e.g., 'a or ' for isolate).
/// Shared between Parser and TemplexParser - mirrors Parser.parseRegion in Parser.scala lines 861-888.
pub fn parse_region<'p>(
  original_iter: &mut ScrambleIterator<'p, '_>,
) -> ParseResult<Option<RegionRunePT<'p>>> {
  let mut tentative_iter = original_iter.clone();
  let rune_begin = tentative_iter.get_pos();

  let maybe_rune = if tentative_iter.try_skip_symbol('\'') {
    None
  } else {
    let region_rune = match tentative_iter.next_word() {
      None => return Ok(None),
      Some(r) => r,
    };

    if !tentative_iter.try_skip_symbol('\'') {
      return Ok(None);
    }

    Some(region_rune)
  };

  let rune_end = tentative_iter.get_prev_end_pos();
  original_iter.skip_to(&tentative_iter);

  let range = RangeL(rune_begin, rune_end);

  Ok(Some(RegionRunePT {
    range,
    name: maybe_rune.map(|z| NameP(RangeL(rune_begin, rune_end), z.str)),
  }))
}

/// Helper method to skip past an equals sign while a condition is true
pub fn try_skip_past_equals_while<'p, 's, F>(
  iter: &mut ScrambleIterator<'p, 's>,
  continue_while: F,
) -> Option<ScrambleIterator<'p, 's>>
where
  F: Fn(&ScrambleIterator<'p, 's>) -> bool,
{
  let mut scouting_iter = iter.clone();
  while continue_while(&scouting_iter) {
    match scouting_iter.peek3_cloned() {
      (Some(prev), Some(INodeLEEnum::Symbol(SymbolLE(range, '='))), Some(next)) => {
        let surrounded_by_spaces = prev.range().end() < range.begin() && range.end() < next.range().begin();
        if surrounded_by_spaces {
          // We'll return this iterator for the things that come before the =
          let mut before_iter = iter.clone();
          before_iter.end = scouting_iter.index + 1;

          // Now modify iter to skip past it
          iter.skip_to(&scouting_iter);
          iter.advance();
          iter.advance();

          return Some(before_iter);
        }
      }
      _ => {}
    }
    scouting_iter.advance();
  }

  None
}

/// Try to skip past a keyword, returning the portion before it
pub fn try_skip_past_keyword_while<'p, 's, F>(
  iter: &mut ScrambleIterator<'p, 's>,
  keyword: StrI<'p>,
  continue_while: F,
) -> Option<(WordLE<'p>, ScrambleIterator<'p, 's>)>
where
  F: Fn(&ScrambleIterator<'p, 's>) -> bool,
{
  let mut scouting_iter = iter.clone();

  while continue_while(&scouting_iter) {
    match scouting_iter.peek_cloned() {
      Some(INodeLEEnum::Word(w)) if w.str == keyword => {
        // We'll return this iterator for the things that come before the keyword
        let mut before_iter = iter.clone();
        before_iter.end = scouting_iter.index;

        // Now modify self to skip past it.
        iter.skip_to(&scouting_iter);
        iter.advance();

        return Some((w.clone(), before_iter));
      }
      _ => {}
    }
    scouting_iter.advance();
  }

  None
}

