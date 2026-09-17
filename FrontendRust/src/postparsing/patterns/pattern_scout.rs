use std::collections::HashMap;

use crate::scout_arena::ScoutArena;
use crate::keywords::Keywords;
use crate::parsing::ast::{INameDeclarationP, PatternPP};
use crate::postparsing::ast::LocationInDenizenBuilder;
use crate::postparsing::itemplatatype::{CoordTemplataType, ITemplataType};
use crate::postparsing::names::{IRuneS, IVarNameS};
use crate::postparsing::patterns::{AtomSP, CaptureS};
use crate::postparsing::post_parser::{IEnvironmentS, PostParser, StackFrame};
use crate::postparsing::rules::rules::IRulexSR;
use crate::postparsing::rules::templex_scout::translate_maybe_type_into_rune;
use crate::postparsing::variable_uses::VariableDeclarationS;

pub(crate) fn get_parameter_captures<'s>(
  pattern: &AtomSP<'s>,
) -> Vec<VariableDeclarationS<'s>> {
  let mut captures = Vec::new();
  if let Some(capture) = &pattern.name {
    captures.extend(get_capture_captures(capture));
  }
  if let Some(destructure) = pattern.destructure {
    for inner_pattern in destructure {
      captures.extend(get_parameter_captures(inner_pattern));
    }
  }
  captures
}

fn get_capture_captures<'s>(
  capture: &CaptureS<'s>,
) -> Vec<VariableDeclarationS<'s>> {
  if capture.mutate {
    Vec::new()
  } else {
    vec![VariableDeclarationS {
      name: capture.name.clone(),
    }]
  }
}

pub(crate) fn translate_pattern<'s, 'p>(
  scout_arena: &ScoutArena<'s>,
  keywords: &Keywords<'s>,
  stack_frame: StackFrame<'s>,
  lidb: &mut LocationInDenizenBuilder,
  rule_builder: &mut Vec<IRulexSR<'s>>,
  rune_to_explicit_type: &mut HashMap<IRuneS<'s>, ITemplataType>,
  pattern_pp: &PatternPP<'p>,
) -> AtomSP<'s> {
  let maybe_coord_rune = match &pattern_pp.templex {
    None => None,
    Some(type_p) => {
      let mut child_lidb = lidb.child();
      let coord_rune = translate_maybe_type_into_rune(
        scout_arena,
        keywords,
        IEnvironmentS::FunctionEnvironment(stack_frame.parent_env.clone()),
        &mut child_lidb,
        PostParser::eval_range(stack_frame.file, pattern_pp.range),
        rule_builder,
        stack_frame.context_region.clone(),
        Some(type_p),
      );
      rune_to_explicit_type.insert(
        coord_rune.rune.clone(),
        ITemplataType::CoordTemplataType(CoordTemplataType {}),
      );
      Some(coord_rune)
    }
  };

  let maybe_patterns_s = match &pattern_pp.destructure {
    None => None,
    Some(destructure_p) => {
      let mut patterns = Vec::new();
      for inner_pattern_p in destructure_p.patterns {
        let mut child_lidb = lidb.child();
        patterns.push(translate_pattern(
          scout_arena,
          keywords,
          stack_frame.clone(),
          &mut child_lidb,
          rule_builder,
          rune_to_explicit_type,
          inner_pattern_p,
        ));
      }
      Some(scout_arena.alloc_slice_from_vec(patterns))
    }
  };

  let capture_s = match &pattern_pp.destination {
    None => None,
    Some(destination) => {
      let mutate = destination.mutate.is_some();
      match &destination.decl {
        INameDeclarationP::IgnoredLocalNameDeclaration(_) => None,
        INameDeclarationP::LocalNameDeclaration(name_p) => Some(CaptureS {
          name: IVarNameS::CodeVarName(scout_arena.intern_str(name_p.str().as_str())),
          mutate,
        }),
        INameDeclarationP::ConstructingMemberNameDeclaration(name_p) => Some(CaptureS {
          name: IVarNameS::ConstructingMemberName(scout_arena.intern_str(name_p.str().as_str())),
          mutate,
        }),
        INameDeclarationP::IterableNameDeclaration(range) => Some(CaptureS {
          name: IVarNameS::IterableName(PostParser::eval_range(stack_frame.file, *range)),
          mutate,
        }),
        INameDeclarationP::IteratorNameDeclaration(range) => Some(CaptureS {
          name: IVarNameS::IteratorName(PostParser::eval_range(stack_frame.file, *range)),
          mutate,
        }),
        INameDeclarationP::IterationOptionNameDeclaration(range) => Some(CaptureS {
          name: IVarNameS::IterationOptionName(PostParser::eval_range(stack_frame.file, *range)),
          mutate,
        }),
      }
    }
  };

  AtomSP {
    range: PostParser::eval_range(stack_frame.file, pattern_pp.range),
    name: capture_s,
    coord_rune: maybe_coord_rune,
    destructure: maybe_patterns_s,
  }
}
