use crate::compile_options::GlobalOptions;
use crate::keywords::Keywords;
use crate::lexing::ast::*;
use crate::lexing::errors::FailedParse;
use crate::lexing::errors::ParseError;
use crate::parsing::ast::*;
use crate::parsing::expression_parser::ExpressionParser;
use crate::parsing::parse_utils::{parse_region as parse_region_shared, try_skip_past_keyword_while};
use crate::parsing::pattern_parser::PatternParser;
use crate::parsing::expression_parser::ScrambleIterator;
use crate::parsing::templex_parser::TemplexParser;
use crate::utils::code_hierarchy::{FileCoordinate, PackageCoordinate};
use crate::utils::code_hierarchy::{FileCoordinateMap, IPackageResolver};
use std::collections::HashMap;
use crate::parsing::parse_and_explore;
use crate::parse_arena::ParseArena;
use std::collections::HashSet;

type ParseResult<T> = Result<T, ParseError>;

/// Main parser coordinating all parsing operations
/// Matches Scala's Parser class
pub struct Parser<'p, 'ctx> {
  // VV: crate::
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  pub templex_parser: TemplexParser<'p, 'ctx>,
  pub pattern_parser: PatternParser<'p, 'ctx>,
  pub expression_parser: ExpressionParser<'p, 'ctx>,
}

impl<'p, 'ctx> Parser<'p, 'ctx>
where
    'p: 'ctx,
{
  /// Parse a single generic parameter
  fn parse_generic_parameter(
    &self,
    mut iter: ScrambleIterator<'p, '_>,
  ) -> ParseResult<GenericParameterP<'p>> {
    let range = iter.range();

    // Parse optional prefixing region
    let maybe_coord_region = self.parse_prefixing_region(&mut iter)?;

    // Parse the main parameter
    let (name, maybe_type, attributes) = match self.parse_region(&mut iter)? {
      None => {
        // Regular rune parameter
        let name = match iter.peek_cloned() {
          Some(INodeLEEnum::Word(WordLE { range, str })) => {
            let result = NameP(range, str);
            iter.advance();
            result
          }
          _ => return Err(ParseError::BadRuneNameError(iter.get_pos())),
        };

        let type_begin = iter.get_pos();
        let maybe_rune_type = self.templex_parser.parse_rune_type(&mut iter)?;

        let maybe_type = maybe_rune_type.map(|tyype| GenericParameterTypeP {
          range: RangeL(type_begin, iter.get_prev_end_pos()),
          tyype,
        });

        let maybe_attrs = if iter.try_skip_word(self.keywords.imm).is_some() {
          vec![IRuneAttributeP::ImmutableRuneAttribute(RangeL(
            iter.get_prev_end_pos(),
            iter.get_prev_end_pos(),
          ))]
        } else {
          vec![]
        };

        (name, maybe_type, maybe_attrs)
      }
      Some(region) => {
        // Region parameter
        let attributes = if let Some(range) = iter.try_skip_word(self.keywords.ro) {
          vec![IRuneAttributeP::ReadOnlyRegionRuneAttribute(range)]
        } else if let Some(range) = iter.try_skip_word(self.keywords.rw) {
          vec![IRuneAttributeP::ReadWriteRegionRuneAttribute(range)]
        } else if let Some(range) = iter.try_skip_word(self.keywords.additive) {
          vec![IRuneAttributeP::AdditiveRegionRuneAttribute(range)]
        } else if let Some(range) = iter.try_skip_word(self.keywords.imm) {
          vec![IRuneAttributeP::ImmutableRegionRuneAttribute(range)]
        } else {
          vec![]
        };

        let tyype = GenericParameterTypeP {
          range: RangeL(range.begin(), iter.get_prev_end_pos()),
          tyype: ITypePR::RegionType,
        };

        let region_name = region
          .name
          .ok_or(ParseError::BadRuneNameError(iter.get_pos()))?;

        (region_name, Some(tyype), attributes)
      }
    };

    // Parse optional default value
    let maybe_default = if iter.try_skip_symbol('=') {
      Some(self.templex_parser.parse_templex(&mut iter)?)
    } else {
      None
    };

    assert!(iter.at_end());

    Ok(GenericParameterP::<'p> {
      range,
      name,
      maybe_type,
      coord_region: maybe_coord_region,
      attributes: self.parse_arena.alloc_slice_from_vec(attributes),
      maybe_default,
    })
  }
  
  pub fn new(
    parse_arena: &'ctx ParseArena<'p>,
    keywords: &'ctx Keywords<'p>,
  ) -> Self {
    let templex_parser = TemplexParser::new(parse_arena, keywords);
    let pattern_parser = PatternParser::new(parse_arena, keywords);
    let expression_parser = ExpressionParser::new(parse_arena, keywords);

    Parser {
      parse_arena,
      keywords,
      templex_parser,
      pattern_parser,
      expression_parser,
    }
  }

  /// Parse a complete file from lexer output
  pub fn parse_file(&self, file: FileL<'p>) -> ParseResult<FileP<'p>> {
    let FileL {
      denizens,
      comment_ranges,
    } = file;

    let mut parsed_denizens = Vec::new();

    for denizen in denizens {
      let parsed = self.parse_denizen(*denizen)?;
      parsed_denizens.push(parsed);
    }

    let empty_str = self.parse_arena.intern_str("");
    let empty_package = self.parse_arena.intern_package_coordinate(empty_str, &[]);
    let empty_file = self.parse_arena.intern_file_coordinate(empty_package, "");

    Ok(FileP {
      file_coord: empty_file,
      comments_ranges: comment_ranges,
      denizens: self.parse_arena.alloc_slice_from_vec(parsed_denizens),
    })
  }

  /// Parse a top-level denizen
  pub fn parse_denizen(&self, denizen: IDenizenL<'p>) -> ParseResult<IDenizenP<'p>> {
    match denizen {
      IDenizenL::TopLevelFunction(func) => {
        let parsed = self.parse_function(func, false)?;
        Ok(IDenizenP::TopLevelFunction(parsed))
      }
      IDenizenL::TopLevelStruct(struct_) => {
        let parsed = self.parse_struct(struct_)?;
        Ok(IDenizenP::TopLevelStruct(parsed))
      }
      IDenizenL::TopLevelInterface(interface) => {
        let parsed = self.parse_interface(interface)?;
        Ok(IDenizenP::TopLevelInterface(parsed))
      }
      IDenizenL::TopLevelImpl(impl_) => {
        let parsed = self.parse_impl(impl_)?;
        Ok(IDenizenP::TopLevelImpl(parsed))
      }
      IDenizenL::TopLevelExportAs(export) => {
        let parsed = self.parse_export_as(export)?;
        Ok(IDenizenP::TopLevelExportAs(parsed))
      }
      IDenizenL::TopLevelImport(import) => {
        let parsed = self.parse_import(import)?;
        Ok(IDenizenP::TopLevelImport(parsed))
      }
    }
  }

  /// Parse generic parameters from angled brackets
  fn parse_identifying_runes(&self, node: &AngledLE<'p>) -> ParseResult<GenericParametersP<'p>> {
    let iter = ScrambleIterator::new(&node.contents);
    let parts = iter.split_on_symbol(',', false);

    let mut params = Vec::new();
    for part in parts {
      let param = self.parse_generic_parameter(part)?;
      params.push(param);
    }

    Ok(GenericParametersP {
      range: node.range,
      params: self.parse_arena.alloc_slice_from_vec(params),
    })
  }
  
  /// Parse struct member
  fn parse_struct_member(&self, iter: &mut ScrambleIterator<'p, '_>) -> ParseResult<IStructContent<'p>> {
    let begin = iter.get_pos();

    // Parse name (can be a word or integer for variadic)
    let name = match iter.peek_cloned() {
      Some(INodeLEEnum::ParsedInteger(ParsedIntegerLE { range, value, .. })) => {
        let result: NameP<'_> = NameP(range, self.parse_arena.intern_str(&value.to_string()));
        iter.advance();
        result
      }
      _ => match iter.next_word() {
        Some(WordLE { range, str }) => NameP(range, str),
        None => return Err(ParseError::BadStructMember(iter.get_pos())),
      },
    };

    // Parse variability (! means varying)
    let variability = if iter.try_skip_symbol('!') {
      VariabilityP::Varying
    } else {
      VariabilityP::Final
    };

    // Check for variadic (..)
    let variadic = matches!(
      iter.peek2_cloned(),
      (
        Some(INodeLEEnum::Symbol(SymbolLE(_, '.'))),
        Some(INodeLEEnum::Symbol(SymbolLE(_, '.')))
      )
    );
    if variadic {
      iter.advance();
      iter.advance();
    }

    // Parse type
    let tyype = self.templex_parser.parse_templex(iter)?;

    if variadic {
      if name.str() != self.keywords.underscore {
        return Err(ParseError::VariadicStructMemberHasName(iter.get_pos()));
      }

      Ok(IStructContent::VariadicStructMember(
        VariadicStructMemberP {
          range: RangeL(begin, iter.get_prev_end_pos()),
          variability,
          tyype,
        },
      ))
    } else {
      Ok(IStructContent::NormalStructMember(NormalStructMemberP::<'p> {
        range: RangeL(begin, iter.get_prev_end_pos()),
        name,
        variability,
        tyype,
      }))
    }
  }
  
  /// Parse a struct definition
  pub fn parse_struct(&self, struct_l: StructL<'p>) -> ParseResult<StructP<'p>> {
    let StructL {
      range: struct_range,
      name: name_l,
      attributes: attributes_l,
      mutability: maybe_mutability_l,
      identifying_runes: maybe_identifying_runes_l,
      template_rules: maybe_template_rules_l,
      contents_range,
      members: members_l,
      methods: methods_l,
    } = struct_l;

    // Parse identifying runes
    let maybe_identifying_runes = maybe_identifying_runes_l
      .as_ref()
      .map(|runes| self.parse_identifying_runes(runes))
      .transpose()?;

    // Parse template rules
    let maybe_template_rules = maybe_template_rules_l
      .as_ref()
      .map(|rules_scramble| {
        let iter = ScrambleIterator::new(&rules_scramble);
        let parts = iter.split_on_symbol(',', false);
        let mut rules = Vec::new();
        for mut part in parts {
          rules.push(self.templex_parser.parse_rule(&mut part)?);
        }
        Ok(TemplateRulesP {
          range: rules_scramble.range,
          rules: self.parse_arena.alloc_slice_from_vec(rules),
        })
      })
      .transpose()?;

    // Parse attributes
    let mut attributes = Vec::new();
    for attr_l in attributes_l {
      attributes.push(self.parse_attribute(*attr_l)?);
    }

    // Parse mutability
    let maybe_mutability = maybe_mutability_l
      .map(|mut_l| {
        let mut iter = ScrambleIterator::new(&mut_l);
        self.templex_parser.parse_templex(&mut iter)
      })
      .transpose()?;

    // Parse struct members
    let mut contents_vec = Vec::new();
    for member_l in members_l {
      let mut iter = ScrambleIterator::new(member_l);
      contents_vec.push(self.parse_struct_member(&mut iter)?);
    }
    for method_l in methods_l {
      contents_vec.push(IStructContent::StructMethod(self.parse_function(*method_l, true)?));
    }

    let members = StructMembersP {
      range: contents_range,
      contents: self.parse_arena.alloc_slice_from_vec(contents_vec),
    };

    Ok(StructP::<'p> {
      range: struct_range,
      name: self.to_name(name_l),
      attributes: self.parse_arena.alloc_slice_from_vec(attributes),
      mutability: maybe_mutability,
      identifying_runes: maybe_identifying_runes,
      template_rules: maybe_template_rules,
      maybe_default_region_rune: None,
      body_range: contents_range,
      members,
    })
  }

  /// Parse an interface definition
  pub fn parse_interface(&self, interface_l: InterfaceL<'p>) -> ParseResult<InterfaceP<'p>> {
    let InterfaceL {
      range: interface_range,
      name: name_l,
      attributes: attributes_l,
      mutability: maybe_mutability_l,
      maybe_identifying_runes: maybe_identifying_runes_l,
      template_rules: maybe_template_rules_l,
      body_range,
      members: methods,
    } = interface_l;

    // Parse identifying runes
    let maybe_identifying_runes = maybe_identifying_runes_l
      .as_ref()
      .map(|runes| self.parse_identifying_runes(runes))
      .transpose()?;

    // Parse template rules
    let maybe_template_rules = maybe_template_rules_l
      .as_ref()
      .map(|rules_scramble| {
        let iter = ScrambleIterator::new(&rules_scramble);
        let parts = iter.split_on_symbol(',', false);
        let mut rules = Vec::new();
        for mut part in parts {
          rules.push(self.templex_parser.parse_rule(&mut part)?);
        }
        Ok(TemplateRulesP {
          range: rules_scramble.range,
          rules: self.parse_arena.alloc_slice_from_vec(rules),
        })
      })
      .transpose()?;

    // Parse attributes
    let mut attributes = Vec::new();
    for attr_l in attributes_l {
      attributes.push(self.parse_attribute(*attr_l)?);
    }

    // Parse mutability
    let maybe_mutability = maybe_mutability_l
      .map(|mut_l| {
        let mut iter = ScrambleIterator::new(&mut_l);
        self.templex_parser.parse_templex(&mut iter)
      })
      .transpose()?;

    // Parse interface methods
    // Interface methods are in a citizen (interface), so is_in_citizen = true
    let mut members_vec = Vec::new();
    for method_l in methods {
      members_vec.push(self.parse_function(*method_l, true)?);
    }

    Ok(InterfaceP {
      range: interface_range,
      name: self.to_name(name_l),
      attributes: self.parse_arena.alloc_slice_from_vec(attributes),
      mutability: maybe_mutability,
      maybe_identifying_runes,
      template_rules: maybe_template_rules,
      maybe_default_region_rune: None,
      body_range,
      members: self.parse_arena.alloc_slice_from_vec(members_vec),
    })
  }

  /// Parse an impl block
  pub fn parse_impl(&self, impl_l: ImplL<'p>) -> ParseResult<ImplP<'p>> {
    let ImplL {
      range: impl_range,
      identifying_runes: maybe_identifying_runes_l,
      template_rules: maybe_template_rules_l,
      struct_: struct_l,
      interface: interface_l,
      attributes: attributes_l,
    } = impl_l;

    // Parse identifying runes if present
    let maybe_identifying_runes = match maybe_identifying_runes_l {
      Some(user_specified_identifying_runes) => {
        Some(self.parse_identifying_runes(&user_specified_identifying_runes)?)
      }
      None => None,
    };

    // Parse template rules if present
    let maybe_template_rules_p = match maybe_template_rules_l {
      Some(template_rules_scramble) => {
        let iter = ScrambleIterator::new(&template_rules_scramble);
        let rule_iters = iter.split_on_symbol(',', false);
        let mut elements_pr = Vec::new();

        for rule_iter in rule_iters {
          elements_pr.push(self.templex_parser.parse_rule(&mut rule_iter.clone())?);
        }

        Some(TemplateRulesP {
          range: template_rules_scramble.range,
          rules: self.parse_arena.alloc_slice_from_vec(elements_pr),
        })
      }
      None => None,
    };

    // Parse struct templex if present
    let struct_p = match struct_l {
      None => None,
      Some(struct_l) => {
        let mut iter = ScrambleIterator::new(&struct_l);
        Some(self.templex_parser.parse_templex(&mut iter)?)
      }
    };

    // Parse interface templex
    let mut iter = ScrambleIterator::new(&interface_l);
    let interface_p = self.templex_parser.parse_templex(&mut iter)?;

    // Parse attributes
    let mut attributes_p = Vec::new();
    for attribute_l in attributes_l {
      attributes_p.push(self.parse_attribute(*attribute_l)?);
    }

    Ok(ImplP {
      range: impl_range,
      generic_params: maybe_identifying_runes,
      template_rules: maybe_template_rules_p,
      struct_: struct_p,
      interface: interface_p,
      attributes: self.parse_arena.alloc_slice_from_vec(attributes_p),
    })
  }
  
  /// Helper to convert WordLE to NameP
  fn to_name(&self, word: WordLE<'p>) -> NameP<'p> {
    NameP(word.range, word.str)
  }

  /// Parse an export-as declaration
  pub fn parse_export_as(&self, export_l: ExportAsL<'p>) -> ParseResult<ExportAsP<'p>> {
    let mut iter = ScrambleIterator::new(&export_l.contents);

    // Try to find "as" keyword and get everything before it
    let exportee = {
      let mut scouting_iter = iter.clone();
      let mut found_as = false;
      let mut before_iter = iter.clone();

      while scouting_iter.has_next() {
        // Check if we should continue (not at semicolon)
        let should_continue = match scouting_iter.peek_cloned() {
          None => false,
          Some(INodeLEEnum::Symbol(SymbolLE(_, ';'))) => false,
          _ => true,
        };

        if !should_continue {
          break;
        }

        // Check if this is the "as" keyword
        if let Some(INodeLEEnum::Word(WordLE { str, .. })) = scouting_iter.peek_cloned() {
          if str == self.keywords.r#as {
            // Found "as"! Create iterator for everything before it
            before_iter.end = scouting_iter.index;
            iter.skip_to(&scouting_iter);
            iter.advance(); // Skip past "as"
            found_as = true;
            break;
          }
        }

        scouting_iter.advance();
      }

      if !found_as {
        return Err(ParseError::BadExportAs(iter.get_pos()));
      }

      // Parse the templex from everything before "as"
      self.templex_parser.parse_templex(&mut before_iter)?
    };

    // Get the name after "as"
    let name = match iter.peek_cloned() {
      None => return Err(ParseError::BadExportEnd(iter.get_pos())),
      Some(INodeLEEnum::Word(word)) => self.to_name(word.clone()),
      _ => return Err(ParseError::BadExportEnd(iter.get_pos())),
    };

    Ok(ExportAsP {
      range: export_l.range,
      struct_: exportee,
      exported_name: name,
    })
  }
  
  /// Parse an import declaration
  pub fn parse_import(&self, import_l: ImportL<'p>) -> ParseResult<ImportP<'p>> {
    let ImportL {
      range,
      module_name: module_name_l,
      package_steps: package_steps_l,
      importee_name: importee_name_l,
    } = import_l;

    let module_name_p = self.to_name(module_name_l);

    let mut package_steps_p = Vec::new();
    for step in package_steps_l {
      package_steps_p.push(self.to_name(*step));
    }

    let importee_name_p = self.to_name(importee_name_l);

    Ok(ImportP {
      range,
      module_name: module_name_p,
      package_steps: self.parse_arena.alloc_slice_from_vec(package_steps_p),
      importee_name: importee_name_p,
    })
  }
  
  /// Parse an attribute
  fn parse_attribute(&self, attr_l: IAttributeL<'p>) -> ParseResult<IAttributeP<'p>> {
    match attr_l {
      IAttributeL::WeakableAttribute(range) => {
        Ok(IAttributeP::WeakableAttribute(WeakableAttributeP { range }))
      }
      IAttributeL::SealedAttribute(range) => {
        Ok(IAttributeP::SealedAttribute(SealedAttributeP { range }))
      }
      IAttributeL::MacroCall {
        range,
        inclusion,
        name,
      } => Ok(IAttributeP::MacroCall(MacroCallP {
        range,
        inclusion: match inclusion {
          IMacroInclusionL::CallMacro => IMacroInclusionP::CallMacro,
          IMacroInclusionL::DontCallMacro => IMacroInclusionP::DontCallMacro,
        },
        name: self.to_name(name),
      })),
      IAttributeL::AbstractAttribute(range) => {
        Ok(IAttributeP::AbstractAttribute(AbstractAttributeP { range }))
      }
      IAttributeL::ExternAttribute {
        range,
        maybe_custom_name,
      } => {
        match maybe_custom_name {
          None => Ok(IAttributeP::ExternAttribute(ExternAttributeP { range })),
          Some(parend) => {
            // extern("name") becomes BuiltinAttribute
            let iter = ScrambleIterator::new(&parend.contents);
            if let Some(INodeLEEnum::String(string_le)) = iter.peek_cloned() {
              // Extract the string value from the parts
              // For a simple string like "bork", there should be one Literal part
              if string_le.parts.len() == 1 {
                if let StringPart::Literal { s, .. } = &string_le.parts[0] {
                  let name = NameP(string_le.range, *s);
                  return Ok(IAttributeP::BuiltinAttribute(BuiltinAttributeP {
                    range,
                    generator_name: name,
                  }));
                }
              }
              Err(ParseError::BadExternAttribute(range.begin()))
            } else {
              Err(ParseError::BadExternAttribute(range.begin()))
            }
          }
        }
      }
      IAttributeL::ExportAttribute(range) => {
        Ok(IAttributeP::ExportAttribute(ExportAttributeP { range }))
      }
      IAttributeL::PureAttribute(range) => Ok(IAttributeP::PureAttribute(PureAttributeP { range })),
      IAttributeL::AdditiveAttribute(range) => {
        Ok(IAttributeP::AdditiveAttribute(AdditiveAttributeP { range }))
      }
      IAttributeL::LinearAttribute(range) => {
        Ok(IAttributeP::LinearAttribute(LinearAttributeP { range }))
      }
    }
  }
  
  /// Parse a function
  pub fn parse_function(
    &self,
    func_l: FunctionL<'p>,
    is_in_citizen: bool,
  ) -> ParseResult<FunctionP<'p>> {
    let FunctionL {
      range: func_range_l,
      header: header_l,
      body: maybe_body_l,
    } = func_l;

    let FunctionHeaderL {
      range: header_range_l,
      name: name_l,
      attributes: attributes_l,
      maybe_user_specified_identifying_runes: maybe_identifying_runes_l,
      params: params_l,
      trailing_details: original_trailing_details_l,
    } = header_l;

    // Parse identifying runes if present
    let maybe_identifying_runes = match maybe_identifying_runes_l {
      Some(user_specified_identifying_runes) => {
        Some(self.parse_identifying_runes(&user_specified_identifying_runes)?)
      }
      None => None,
    };

    // Parse parameters
    let mut params_p_vec = Vec::new();
    let params_iter = ScrambleIterator::new(&params_l.contents);
    let param_iters = params_iter.split_on_symbol(',', false);

    // Use field splitting to borrow parsers separately
    let Self {
      pattern_parser,
      templex_parser,
      ..
    } = self;

    for (index, pattern_iter) in param_iters.into_iter().enumerate() {
      let mut iter = pattern_iter.clone();
      params_p_vec.push(pattern_parser.parse_parameter(
        &mut iter,
        templex_parser,
        index,
        is_in_citizen,
        true,
        false,
      )?);
    }

    let params_p = ParamsP {
      range: params_l.range,
      params: self.parse_arena.alloc_slice_from_vec(params_p_vec),
    };

    // Parse trailing details to extract return type, where clause, and default region
    let (trailing_details_with_return_and_where, maybe_default_region) =
      self.parse_body_default_region(original_trailing_details_l.clone());

    // TODO: simplify this. It's really just trying to split on "where".
    let mut return_and_where_iter =
      ScrambleIterator::new(&trailing_details_with_return_and_where);

    let (maybe_return_iter, return_end_pos, maybe_rules_iter) =
      match try_skip_past_keyword_while(&mut return_and_where_iter, self.keywords.r#where, |it| {
        it.has_next()
      }) {
        None => {
          // No "where" was found. Use everything remaining.
          (
            Some(return_and_where_iter.clone()),
            trailing_details_with_return_and_where.range.end(),
            None,
          )
        }
        Some((_, return_iter)) => (
          Some(return_iter),
          return_and_where_iter.scramble.range.end(),
          Some(return_and_where_iter.clone()),
        ),
      };

    let return_begin_pos = trailing_details_with_return_and_where.range.begin();
    let maybe_return_type_p = if let Some(mut return_iter) = maybe_return_iter {
      if return_iter.has_next() {
        Some(self.templex_parser.parse_templex(&mut return_iter)?)
      } else {
        None
      }
    } else {
      None
    };

    let return_p = FunctionReturnP {
      range: RangeL(return_begin_pos, return_end_pos),
      ret_type: maybe_return_type_p,
    };

    let maybe_rules_p = maybe_rules_iter
      .map(|rules_iter| {
        let _begin = rules_iter.get_pos();
        let rule_iters = rules_iter.split_on_symbol(',', false);
        let mut rules = Vec::new();
        for mut templex_iter in rule_iters {
          match self.templex_parser.parse_rule(&mut templex_iter) {
            Err(e) => return Err(e),
            Ok(x) => rules.push(x),
          }
        }
        Ok(TemplateRulesP {
          range: rules_iter.scramble.range,
          rules: self.parse_arena.alloc_slice_from_vec(rules),
        })
      })
      .transpose()?;

    // Parse attributes
    let mut attributes_p = Vec::new();
    for attribute_l in attributes_l {
      attributes_p.push(self.parse_attribute(*attribute_l)?);
    }

    let header = FunctionHeaderP {
      range: header_range_l,
      name: Some(self.to_name(name_l)),
      attributes: self.parse_arena.alloc_slice_from_vec(attributes_p),
      generic_parameters: maybe_identifying_runes,
      template_rules: maybe_rules_p,
      params: Some(params_p),
      ret: return_p,
    };

    // Parse body if present
    // Use field splitting to borrow parsers separately
    let Self {
      expression_parser,
      templex_parser,
      pattern_parser,
      ..
    } = self;

    let body_p = match maybe_body_l {
      Some(body_l) => {
        let FunctionBodyL { body: block_l } = body_l;
        let statements_p =
          expression_parser.parse_block(&block_l, templex_parser, pattern_parser)?;
        Some(&*self.parse_arena.alloc(BlockPE {
          range: block_l.range,
          maybe_pure: None,
          maybe_default_region: maybe_default_region,
          inner: &*self.parse_arena.alloc(statements_p),
        }))
      }
      None => None,
    };

    Ok(FunctionP {
      range: func_range_l,
      header,
      body: body_p,
    })
  }
  
  /// Parse body default region from trailing details
  fn parse_body_default_region(
    &self,
    input_scramble: ScrambleLE<'p>,
  ) -> (ScrambleLE<'p>, Option<RegionRunePT<'p>>) {
    if input_scramble.elements.len() < 2 {
      return (input_scramble, None);
    }

    let _last_two = &input_scramble.elements[input_scramble.elements.len() - 2..];

    // The Scala code checks:
    // - Vector(SymbolLE(_, '\'')) => anonymous region (ONE element: just ')
    // - Vector(WordLE(...), SymbolLE(_, '\'')) => named region (TWO elements: word then ')
    // So we need to check the length of elements, not just the last two
    let default_region = match input_scramble.elements.len() {
      1 => {
        // Check if it's just a single apostrophe
        match &*input_scramble.elements[0] {
          INodeLEEnum::Symbol(SymbolLE(symbol_range, '\'')) => Some(RegionRunePT {
            range: *symbol_range,
            name: None,
          }),
          _ => None,
        }
      }
      n if n >= 2 => {
        // Check if the last two elements are word then apostrophe
        let last_two = &input_scramble.elements[n - 2..n];
        match (&*last_two[0], &*last_two[1]) {
          (
            INodeLEEnum::Word(WordLE {
              range: word_range,
              str: region_name,
            }),
            INodeLEEnum::Symbol(SymbolLE(symbol_range, '\'')),
          ) => {
            let range = RangeL(word_range.begin(), symbol_range.end());
            Some(RegionRunePT {
              range,
            name: Some(NameP(*word_range, *region_name)),
            })
          }
          _ => None,
        }
      }
      _ => None,
    };

    if default_region.is_none() {
      return (input_scramble, None);
    }

    // Remove the elements that made up the default region
    let elements_to_remove = match input_scramble.elements.len() {
      1 => 1, // Just the apostrophe
      _ => 2, // Word and apostrophe
    };

    let preceding_elements = &input_scramble.elements
      [..input_scramble.elements.len() - elements_to_remove];

    let preceding_elements_range = if preceding_elements.is_empty() {
      RangeL(input_scramble.range.begin(), input_scramble.range.begin())
    } else {
      RangeL(
        preceding_elements.first().unwrap().range().begin(),
        preceding_elements.last().unwrap().range().end(),
      )
    };

    let preceding_elements_scramble = ScrambleLE {
      range: preceding_elements_range,
      elements: preceding_elements,
    };

    (preceding_elements_scramble, default_region)
  }
  
}

// 'p: interner
// 'p: parsed arena (parsed data outlives 'p; interner outlives parsed)
// Arena is passed in by reference, caller owns it
pub struct ParserCompilation<'p, 'ctx> {
  opts: GlobalOptions,
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  packages_to_build: Vec<&'p PackageCoordinate<'p>>,
  package_to_contents_resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>>,
  code_map_cache: Option<FileCoordinateMap<'p, String>>,
  vpst_map_cache: Option<FileCoordinateMap<'p, String>>,
  parseds_cache: Option<FileCoordinateMap<'p, (FileP<'p>, Vec<RangeL>)>>,
}
impl<'p, 'ctx> ParserCompilation<'p, 'ctx>
where
  'p: 'ctx,
{
  
  pub fn new(
    opts: GlobalOptions,
    parse_arena: &'ctx ParseArena<'p>,
    keywords: &'ctx Keywords<'p>,
    packages_to_build: Vec<&'p PackageCoordinate<'p>>,
    package_to_contents_resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>>,
  ) -> Self {
    ParserCompilation {
      opts,
      parse_arena,
      keywords,
      packages_to_build,
      package_to_contents_resolver,
      code_map_cache: None,
      vpst_map_cache: None,
      parseds_cache: None,
    }
  }

  fn load_and_parse(
    &self,
    needed_packages: &[&'p PackageCoordinate<'p>],
    resolver: &dyn IPackageResolver<'p, HashMap<String, String>>,
  ) -> Result<
    (
      FileCoordinateMap<'p, String>,
      FileCoordinateMap<'p, (FileP<'p>, Vec<RangeL>)>,
    ),
    FailedParse<'p>,
  > {
    let unique_packages: HashSet<_> = needed_packages.iter().collect();
    assert!(
      unique_packages.len() == needed_packages.len(),
      "Duplicate modules in: {:?}",
      needed_packages
    );

    let mut found_code_map: FileCoordinateMap<'p, String> = FileCoordinateMap::<String>::new();
    let mut parsed_map: FileCoordinateMap<'p, (FileP<'p>, Vec<RangeL>)> =
      FileCoordinateMap::new();

    let parser = Parser::new(self.parse_arena, self.keywords);
    let resolver_fn = |package_coord: &'p PackageCoordinate<'p>| resolver.resolve(package_coord);
    parse_and_explore::parse_and_explore(
            self.parse_arena,
            self.keywords,
            self.opts.clone(),
            &parser,
            needed_packages.to_vec(),
            &resolver_fn,
            |_file_coord, _code, _imports, denizen| denizen,
            |file_coord: &'p FileCoordinate<'p>, code, comment_ranges, denizens: Vec<IDenizenP<'p>>| {
                found_code_map.put(file_coord, code.to_string());
                let comments_slice = self.parse_arena.alloc_slice_copy(comment_ranges);
                let denizens_slice = self.parse_arena.alloc_slice_from_vec(denizens);
                let file = FileP {
                    file_coord: file_coord,
                    comments_ranges: comments_slice,
                    denizens: denizens_slice,
                };

                parsed_map.put(file_coord, (file, comment_ranges.to_vec()));
            },
        ).map_err(|e| e)?;

    Ok((found_code_map, parsed_map))
  }
  
  pub fn get_code_map(&mut self) -> Result<FileCoordinateMap<'p, String>, FailedParse<'p>> {
    self.get_parseds()?;
    Ok(self.code_map_cache.clone().unwrap())
  }
  
  pub fn expect_code_map(&self) -> FileCoordinateMap<'p, String> {
    self
      .code_map_cache
      .clone()
      .expect("code_map_cache should be populated")
  }

  pub fn get_parseds(&mut self) -> Result<FileCoordinateMap<'p, (FileP<'p>, Vec<RangeL>)>, FailedParse<'p>> {
    if let Some(ref parseds) = self.parseds_cache {
      return Ok(parseds.clone());
    }

    let packages = self.packages_to_build.clone();
    let (code_map, program_p_map) =
      self.load_and_parse(&packages, self.package_to_contents_resolver)?;

    self.code_map_cache = Some(code_map);
    self.parseds_cache = Some(program_p_map);
    Ok(self.parseds_cache.clone().unwrap())
  }
  
  pub fn expect_parseds(&mut self) -> FileCoordinateMap<'p, (FileP<'p>, Vec<RangeL>)> {
    match self.get_parseds() {
      Err(FailedParse {
        code: _code,
        file_coord: _file_coord,
        error,
      }) => {
        panic!(
          "Parse error: {:?} - need ParseErrorHumanizer.humanize - see Parser.scala lines 818-826",
          error
        )
      }
      Ok(x) => x,
    }
  }
  
  pub fn get_vpst_map(&mut self) -> Result<FileCoordinateMap<'p, String>, FailedParse<'p>> {
    if let Some(ref vpst) = self.vpst_map_cache {
      return Ok(vpst.clone());
    }

    let _parseds = self.get_parseds()?;
    panic!("ParserCompilation.get_vpst_map not yet fully implemented - need to vonify and print. See Parser.scala lines 829-846")
  }
  
  pub fn expect_vpst_map(&mut self) -> FileCoordinateMap<'p, String> {
    self.get_vpst_map().expect("getVpstMap should succeed")
  }
  
}

impl<'p, 'ctx> Parser<'p, 'ctx>
where
    'p: 'ctx,
{
  /// Parse optional prefixing region (e.g., `'a`)
  fn parse_prefixing_region(
    &self,
    original_iter: &mut ScrambleIterator<'p, '_>,
  ) -> ParseResult<Option<RegionRunePT<'p>>> {
    let mut tentative_iter = original_iter.clone();

    let region = match parse_region_shared(&mut tentative_iter)? {
      Some(region) => {
        // Check if the next token immediately follows (no gap)
        match tentative_iter.peek_cloned() {
          Some(next) if next.range().begin() == region.range.end() => region,
          _ => return Ok(None),
        }
      }
      None => return Ok(None),
    };

    original_iter.skip_to(&tentative_iter);
    Ok(Some(region))
  }
  
  /// Parse optional region marker - delegates to shared parse_region (Parser.parseRegion in Scala)
  fn parse_region(
    &self,
    original_iter: &mut ScrambleIterator<'p, '_>,
  ) -> ParseResult<Option<RegionRunePT<'p>>> {
    parse_region_shared(original_iter)
  }
  
}

