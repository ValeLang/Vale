use crate::keywords::Keywords;
use crate::lexing::ast::*;
use crate::lexing::errors::ParseError;
use crate::parsing::ast::*;
use crate::parsing::expression_parser::ScrambleIterator;
use crate::parsing::templex_parser::TemplexParser;
use crate::parse_arena::ParseArena;

type ParseResult<T> = Result<T, ParseError>;

pub struct PatternParser<'p, 'ctx> {
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
}
// V: why is this cloneable?/
// VA: It isn't — PatternParser has no derive macros at all and is never cloned. Question is moot.
// V: should this be folded into the main Parser struct?
// VA: It could be — its only fields (parse_arena, keywords) duplicate what Parser already holds.
// VA: It exists as a separate struct for method grouping, matching Scala's separate class. Parser
// VA: holds it as a field and creates it in Parser::new(). Keeping it separate is faithful to Scala's
// VA: structure; folding it in would reduce indirection but diverge from the Scala class layout.

impl<'p, 'ctx> PatternParser<'p, 'ctx>
where
  'p: 'ctx,
{
  pub fn new(parse_arena: &'ctx ParseArena<'p>, keywords: &'ctx Keywords<'p>) -> Self {
    PatternParser {
      parse_arena,
      keywords,
    }
  }

  /// Parse a parameter
  pub fn parse_parameter(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    templex_parser: &TemplexParser<'p, 'ctx>,
    index: usize,
    is_in_citizen: bool,
    is_in_function: bool,
    is_in_lambda: bool,
  ) -> ParseResult<ParameterP<'p>> {
    let pattern_begin = iter.get_pos();
    let pattern_range = iter.range();

    if !iter.has_next() {
      return Err(ParseError::EmptyPattern(pattern_begin));
    }

    // Check for 'virtual' keyword (lines 21-29)
    let maybe_virtual = match iter.peek_cloned() {
      None => return Err(ParseError::EmptyParameter(pattern_range.begin())),
      Some(INodeLEEnum::Word(WordLE { range, str })) if str == self.keywords.r#virtual => {
        iter.advance();
        Some(AbstractP { range })
      }
      Some(_) => None,
    };

    // Check for '&self' (lines 31-41)
    let maybe_self_borrow = match iter.peek_n(2).as_slice() {
      [] => return Err(ParseError::EmptyParameter(pattern_range.begin())),
      [None] => return Err(ParseError::EmptyParameter(pattern_range.begin())),
      [None, None] => return Err(ParseError::EmptyParameter(pattern_range.begin())),
      [Some(INodeLEEnum::Symbol(SymbolLE(range1, '&'))), Some(INodeLEEnum::Word(WordLE { range: range2, str }))]
        if *str == self.keywords.self_ =>
      {
        let begin = range1.begin();
        let end = range2.end();
        iter.advance();
        iter.advance();
        Some(RangeL(begin, end))
      }
      _ => None,
    };

    // If we have self borrow, return early (lines 42-45)
    if let Some(_) = maybe_self_borrow {
      return Ok(ParameterP {
        range: pattern_range,
        virtuality: maybe_virtual,
        maybe_pre_checked: None,
        self_borrow: maybe_self_borrow,
        pattern: None,
      });
    }

    // Parse optional name (lines 47-62)
    let maybe_name = match iter.peek2_cloned() {
      (Some(INodeLEEnum::Squared(_)), _) => {
        // Destructure parameter with no name or type, like func moo([a, b, c])
        None
      }
      (Some(INodeLEEnum::Word(_)), Some(INodeLEEnum::Squared(_))) => {
        // Destructure parameter with type but no name, like func moo(Vec3[a, b, c])
        None
      }
      (Some(INodeLEEnum::Word(w)), _) => {
        let word = w.clone();
        iter.advance();
        Some(word)
      }
      _ => return Err(ParseError::BadLocalName(iter.get_pos())),
    };

    // Check for 'pre' keyword (line 64)
    let maybe_pre_checked = iter.try_skip_word(self.keywords.pre);

    // Parse the pattern (lines 66-69)
    let pattern = self.parse_pattern(
      iter,
      templex_parser,
      pattern_begin,
      index,
      is_in_citizen,
      is_in_function,
      is_in_lambda,
      maybe_name,
    )?;

    Ok(ParameterP {
      range: pattern_range,
      virtuality: maybe_virtual,
      maybe_pre_checked,
      self_borrow: maybe_self_borrow,
      pattern: Some(pattern),
    })
  }

  /// Parse a pattern
  pub fn parse_pattern(
    &self,
    iter: &mut ScrambleIterator<'p, '_>,
    templex_parser: &TemplexParser<'p, 'ctx>,
    pattern_begin: i32,
    index: usize,
    is_in_citizen: bool,
    is_in_function: bool,
    is_in_lambda: bool,
    maybe_name_from_parameter: Option<WordLE<'p>>,
  ) -> ParseResult<PatternPP<'p>> {
    // The Scala code used to have an early return here, but it was dead code and has been commented out.
    // We just check for empty pattern with no name.
    if !iter.has_next() {
      if maybe_name_from_parameter.is_none() {
        return Err(ParseError::EmptyPattern(pattern_begin));
      }
      // Fall through to validation logic below
    }

    // Check for 'self.' prefix for constructing members (lines 90-99)
    let is_constructing = match iter.peek2_cloned() {
      (
        Some(INodeLEEnum::Word(WordLE { str: self_str, .. })),
        Some(INodeLEEnum::Symbol(SymbolLE(_, '.'))),
      ) if self_str == self.keywords.self_ => {
        iter.advance();
        iter.advance();
        true
      }
      _ => false,
    };

    // Check for 'set' keyword (lines 101-104)
    let maybe_mutate = iter.try_skip_word(self.keywords.set);
    if maybe_mutate.is_some() && !iter.has_next() {
      return Err(ParseError::CantUseThatLocalName {
        pos: iter.get_pos(),
        name: "set".to_string(),
      });
    }

    // Parse destination local (lines 106-151)
    let maybe_destination_local = match maybe_name_from_parameter {
      Some(WordLE { range, str }) => {
        if str == self.keywords.underscore {
          Some(DestinationLocalP::<'p> {
            decl: INameDeclarationP::IgnoredLocalNameDeclaration(range),
            mutate: None,
          })
        } else {
          Some(DestinationLocalP::<'p> {
            decl: INameDeclarationP::LocalNameDeclaration(NameP(range, str)),
            mutate: None,
          })
        }
      }
      None => {
        // Determine if the next thing is a name (lines 116-129)
        let name_is_next = match iter.peek2_cloned() {
          (None, None) => {
            panic!("Impossible: peek2 should not return (None, None) when has_next is true")
          }
          (None, Some(_)) => panic!("Impossible: peek2 should not return (None, Some(_))"),
          (Some(_), None) => true,
          (Some(first), Some(second)) => {
            // There's a space after the first thing if ranges don't touch
            first.range().end() < second.range().begin()
          }
        };

        if name_is_next {
          match iter.peek_cloned() {
            Some(INodeLEEnum::Word(WordLE { range, str })) => {
              iter.advance();
              if str == self.keywords.underscore {
                Some(DestinationLocalP {
                  decl: INameDeclarationP::IgnoredLocalNameDeclaration(range),
                  mutate: maybe_mutate,
                })
              } else {
                if is_constructing {
                  Some(DestinationLocalP {
                    decl: INameDeclarationP::ConstructingMemberNameDeclaration(NameP(range, str)),
                    mutate: maybe_mutate,
                  })
                } else {
                  Some(DestinationLocalP {
                    decl: INameDeclarationP::LocalNameDeclaration(NameP(range, str)),
                    mutate: maybe_mutate,
                  })
                }
              }
            }
            Some(INodeLEEnum::Squared(_)) => None,
            _ => return Err(ParseError::BadLocalName(iter.get_pos())),
          }
        } else {
          None
        }
      }
    };

    // Stop if we see 'in' keyword (lines 153-158)
    match iter.peek_cloned() {
      Some(INodeLEEnum::Word(WordLE { str, .. })) if str == self.keywords.r#in => {
        // Don't consume it, just stop processing
      }
      _ => {}
    }

    // Determine if next thing is a type (lines 160-174)
    let next_is_type = match iter.peek2_cloned() {
      (None, None) => false,
      (None, Some(_)) => panic!("Impossible: peek2 should not return (None, Some(_))"),
      (Some(INodeLEEnum::Squared(_)), maybe_after) => {
        // If there's something after the squared brackets, it's an array type
        maybe_after.is_some()
      }
      (Some(_), _) => {
        // There's something that's not square-braced, so it's a type
        true
      }
    };

    // Parse optional type (lines 175-194)
    let maybe_type: Option<ITemplexPT<'p>> = if next_is_type {
      Some(templex_parser.parse_templex(iter)?)
    } else {
      if is_in_lambda {
        // Allow it, lambdas can figure out their type from the callee
        None
      } else if is_in_citizen {
        // Allow it, just assume it's the containing struct
        None
      } else if is_in_function {
        return Err(ParseError::LightFunctionMustHaveParamTypes {
          pos: pattern_begin,
          param_index: index as i32,
        });
      } else {
        // Allow it, just a regular pattern
        None
      }
    };

    // Parse optional destructure (lines 196-215)
    let maybe_destructure = match iter.peek_cloned() {
      Some(INodeLEEnum::Squared(SquaredLE {
        range: destructure_range,
        contents: destructure_elements,
      })) => {
        let destructure_elements = destructure_elements.clone();
        iter.advance();

        let destructure_iter = ScrambleIterator::new(&destructure_elements);
        let element_iters = destructure_iter.split_on_symbol(',', false);

        let mut patterns = Vec::new();
        for (index, mut element_iter) in element_iters.into_iter().enumerate() {
          let pos = element_iter.get_pos();
          let pattern = self.parse_pattern(
            &mut element_iter,
            templex_parser,
            pos,
            index,
            false,
            false,
            false,
            None,
          )?;
          patterns.push(pattern);
        }

        Some(DestructureP {
          range: destructure_range,
          patterns: self.parse_arena.alloc_slice_from_vec(patterns),
        })
      }
      Some(other) => return Err(ParseError::BadThingAfterTypeInPattern(other.range().begin())),
      None => None,
    };

    // Return the complete pattern (lines 217-220)
    Ok(PatternPP::<'p> {
      range: RangeL(pattern_begin, iter.get_prev_end_pos()),
      destination: maybe_destination_local,
      templex: maybe_type,
      destructure: maybe_destructure,
    })
  }
  
}
