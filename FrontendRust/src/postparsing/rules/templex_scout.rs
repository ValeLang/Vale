// Per @DSAUIMZ, all borrow_val() calls in this file borrow from a stack-local
// LocationInDenizenBuilder instead of arena-allocating. The slice is promoted
// to permanent arena storage only inside intern_rune on a miss.

use crate::scout_arena::ScoutArena;
use crate::keywords::Keywords;
use crate::parsing::ast::{
  BoolPT, IntPT, ITemplexPT, ITemplexPT::NameOrRune, LocationPT, MutabilityPT, NameOrRunePT,
  NameP, OwnershipPT, RegionRunePT, StringPT, VariabilityPT,
};
use crate::postparsing::ast::LocationInDenizenBuilder;
use crate::postparsing::itemplatatype::ITemplataType;
use crate::postparsing::names::{
  CodeNameS, CodeRuneS, IImpreciseNameS, IImpreciseNameValS::CodeName, ImplicitRuneValS, IRuneS,
};
use crate::postparsing::names::IRuneValS::{CodeRune, ImplicitRune};
use crate::postparsing::post_parser::{IEnvironmentS, PostParser};
use crate::postparsing::rules::rules::IRulexSR::{Lookup, MaybeCoercingCall, MaybeCoercingLookup};
use crate::postparsing::rules::rules::{
  AugmentSR, BoolLiteralSL, ILiteralSL, IntLiteralSL, IRulexSR, LiteralSR, LocationLiteralSL,
  LookupSR, MaybeCoercingCallSR, MaybeCoercingLookupSR, MutabilityLiteralSL, OwnershipLiteralSL,
  RuneParentEnvLookupSR, RuneUsage, StringLiteralSL, VariabilityLiteralSL,
};
use crate::utils::range::RangeS;
use crate::postparsing::rules::rules::{
  CallSiteFuncSR, DefinitionFuncSR, PackSR, ResolveSR,
};
use std::collections::HashMap;
use crate::interner::StrI;
use crate::postparsing::itemplatatype::CoordTemplataType;

fn add_literal_rule<'s>(scout_arena: &ScoutArena<'s>,
  lidb: &mut LocationInDenizenBuilder,
  rule_builder: &mut Vec<IRulexSR<'s>>,
  range_s: RangeS<'s>,
  value_sr: ILiteralSL<'s>,
) -> RuneUsage<'s> {
  let mut child_lidb = lidb.child();
  let rune_s = RuneUsage {
    range: range_s.clone(),
    rune: scout_arena.intern_rune(ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val()))),
  };
  rule_builder.push(IRulexSR::Literal(LiteralSR {
    range: range_s,
    rune: rune_s.clone(),
    literal: value_sr,
  }));
  rune_s
}

fn add_rune_parent_env_lookup_rule<'s>(_scout_arena: &ScoutArena<'s>,
  _lidb: &mut LocationInDenizenBuilder,
  rule_builder: &mut Vec<IRulexSR<'s>>,
  range_s: RangeS<'s>,
  rune_s: IRuneS<'s>,
) -> RuneUsage<'s> {
  let usage = RuneUsage {
    range: range_s.clone(),
    rune: rune_s,
  };
  rule_builder.push(IRulexSR::RuneParentEnvLookup(RuneParentEnvLookupSR {
      range: range_s,
      rune: usage.clone(),
    },
  ));
  usage
}

fn add_lookup_rule<'s>(scout_arena: &ScoutArena<'s>,
  lidb: &mut LocationInDenizenBuilder,
  rule_builder: &mut Vec<IRulexSR<'s>>,
  range_s: RangeS<'s>,
  // Nearest enclosing region marker, see RADTGCA.
  _context_region: IRuneS<'s>,
  name_sn: IImpreciseNameS<'s>,
) -> RuneUsage<'s> {
  let mut child_lidb = lidb.child();
  let rune_s = RuneUsage {
    range: range_s.clone(),
    rune: scout_arena.intern_rune(ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val()))),
  };
  rule_builder.push(MaybeCoercingLookup(MaybeCoercingLookupSR {
    range: range_s,
    rune: rune_s.clone(),
    name: name_sn,
  }));
  rune_s
}

pub fn translate_value_templex<'s, 'p>(
  scout_arena: &ScoutArena<'s>,
  templex: &ITemplexPT<'p>,
) -> Option<ILiteralSL<'s>> {
  match templex {
    ITemplexPT::Int(IntPT { value, .. }) => Some(ILiteralSL::IntLiteral(IntLiteralSL {
      value: *value,
    })),
    ITemplexPT::Bool(BoolPT { value, .. }) => Some(ILiteralSL::BoolLiteral(BoolLiteralSL {
      value: *value,
    })),
    ITemplexPT::Mutability(MutabilityPT(_, mutability)) => Some(ILiteralSL::MutabilityLiteral(
      MutabilityLiteralSL {
        mutability: *mutability,
      },
    )),
    ITemplexPT::Variability(VariabilityPT(_, variability)) => Some(ILiteralSL::VariabilityLiteral(
      VariabilityLiteralSL {
        variability: *variability,
      },
    )),
    ITemplexPT::String(StringPT { str, .. }) => Some(ILiteralSL::StringLiteral(
      StringLiteralSL {
        value: scout_arena.intern_str(str.as_str()),
      },
    )),
    ITemplexPT::Location(LocationPT { location, .. }) => Some(ILiteralSL::LocationLiteral(
      LocationLiteralSL {
        location: *location,
      },
    )),
    ITemplexPT::Ownership(OwnershipPT(_, ownership)) => Some(ILiteralSL::OwnershipLiteral(
      OwnershipLiteralSL {
        ownership: *ownership,
      },
    )),
    _ => None,
  }
}

// Returns:
// - Rune for this type
pub fn translate_templex<'s, 'p>(scout_arena: &ScoutArena<'s>,
  keywords: &Keywords<'s>,
  env: IEnvironmentS<'s>,
  lidb: &mut LocationInDenizenBuilder,
  rule_builder: &mut Vec<IRulexSR<'s>>,
  // Nearest enclosing region marker, see RADTGCA.
  context_region: IRuneS<'s>,
  templex: &ITemplexPT<'p>,
) -> RuneUsage<'s> {
  
  let file = env.file();
  match translate_value_templex(scout_arena, templex) {
    
    Some(x) => {
      let mut child_lidb = lidb.child();
      add_literal_rule(scout_arena,
        &mut child_lidb,
        rule_builder,
        PostParser::eval_range(file, templex.range()),
        x,
      )
    }
    
    None => match templex {
      
      ITemplexPT::Inline(inline) => translate_templex(
        scout_arena,
        keywords,
        env,
        lidb,
        rule_builder,
        context_region,
        inline.inner,
      ),

      ITemplexPT::AnonymousRune(anonymous_rune) => {
        let mut child_lidb = lidb.child();
        let rune = RuneUsage {
          range: PostParser::eval_range(file, anonymous_rune.range),
          rune: scout_arena.intern_rune(ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val()))),
        };
        rune
      }

      ITemplexPT::RegionRune(RegionRunePT {
        range: _,
        name: None,
      }) => panic!("POSTPARSER_TRANSLATE_TEMPLEX_REGION_RUNE_NONE_NOT_YET_IMPLEMENTED"),

      ITemplexPT::RegionRune(RegionRunePT {
        range,
        name: Some(name),
      }) => {
        let name_s = scout_arena.intern_str(name.str().as_str());
        let is_rune_from_local_env = env.local_declared_runes().contains(
          &scout_arena.intern_rune(CodeRune(CodeRuneS { name: name_s })),
        );
        if is_rune_from_local_env {
          RuneUsage {
            range: PostParser::eval_range(file, *range),
            rune: scout_arena.intern_rune(CodeRune(CodeRuneS { name: name_s })),
          }
        } else {
          // It's from a parent env
          let mut child_lidb = lidb.child();
          add_rune_parent_env_lookup_rule(scout_arena, 
            &mut child_lidb,
            rule_builder,
            PostParser::eval_range(file, *range),
            scout_arena.intern_rune(CodeRune(CodeRuneS { name: name_s })),
          )
        }
      }

      ITemplexPT::NameOrRune(NameOrRunePT(name_or_rune)) => {
        let is_rune_from_env = env.all_declared_runes().contains(&scout_arena.intern_rune(CodeRune(
          CodeRuneS {
            name: scout_arena.intern_str(name_or_rune.str().as_str()),
          },
        )));
        if is_rune_from_env {
          let is_rune_from_local_env = env.local_declared_runes().contains(
            &scout_arena.intern_rune(CodeRune(CodeRuneS {
              name: scout_arena.intern_str(name_or_rune.str().as_str()),
            })),
          );
          if is_rune_from_local_env {
            RuneUsage {
              range: PostParser::eval_range(file, name_or_rune.range()),
              rune: scout_arena.intern_rune(CodeRune(CodeRuneS {
                name: scout_arena.intern_str(name_or_rune.str().as_str()),
              })),
            }
          } else {
            // It's from a parent env
            let mut child_lidb = lidb.child();
            add_rune_parent_env_lookup_rule(scout_arena, 
              &mut child_lidb,
              rule_builder,
              PostParser::eval_range(file, name_or_rune.range()),
              scout_arena.intern_rune(CodeRune(CodeRuneS {
                name: scout_arena.intern_str(name_or_rune.str().as_str()),
              })),
            )
          }
        } else {
          // e.g. "int"
          let name = scout_arena.intern_imprecise_name(CodeName(CodeNameS {
            name: scout_arena.intern_str(name_or_rune.str().as_str()),
          }));
          let mut child_lidb = lidb.child();
          add_lookup_rule(scout_arena,
            &mut child_lidb,
            rule_builder,
            PostParser::eval_range(file, name_or_rune.range()),
            context_region,
            name,
          )
          // For lookups like these, we bring them into the current region.
        }
      }

      ITemplexPT::Interpreted(interpreted) => {
        let range_s = PostParser::eval_range(file, interpreted.range);
        let mut child_lidb = lidb.child();
        let result_rune_s = RuneUsage {
          range: range_s.clone(),
          rune: scout_arena.intern_rune(ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val()))),
        };

        let maybe_region_rune = interpreted.maybe_region.as_ref().map(|region_rune| {
          let region_name = region_rune
            .name
            .as_ref()
            .unwrap_or_else(|| panic!("POSTPARSER_TRANSLATE_TEMPLEX_REGION_NAME_NOT_YET_IMPLEMENTED"))
            .str();
          let rune = scout_arena.intern_rune(CodeRune(CodeRuneS { name: scout_arena.intern_str(region_name.as_str()) }));
          assert!(
            env.all_declared_runes().contains(&rune),
            "POSTPARSER_TRANSLATE_TEMPLEX_UNKNOWN_REGION_NOT_YET_IMPLEMENTED"
          );
          RuneUsage {
            range: PostParser::eval_range(file, interpreted.range),
            rune,
          }
        });
        let new_region = match maybe_region_rune {
          None => context_region.clone(),
          Some(ref region_rune) => region_rune.rune.clone(),
        };
        let mut child_lidb = lidb.child();
        let inner_rune_s = translate_templex(
          scout_arena,
          keywords,
          env,
          &mut child_lidb,
          rule_builder,
          new_region,
          interpreted.inner,
        );
        let ownership =
          interpreted.maybe_ownership.map(|OwnershipPT(_, ownership)| *ownership);
        rule_builder.push(IRulexSR::Augment(AugmentSR {
          range: range_s.clone(),
          result_rune: result_rune_s.clone(),
          ownership,
          inner_rune: inner_rune_s,
        }));
        result_rune_s
      }

      ITemplexPT::Call(call) => {
        let range_s = PostParser::eval_range(file, call.range);
        let mut child_lidb = lidb.child();
        let result_rune_s = RuneUsage {
          range: range_s.clone(),
          rune: scout_arena.intern_rune(ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val()))),
        };
        let mut child_lidb = lidb.child();
        let template_rune_s = translate_templex(
          scout_arena,
          keywords,
          env.clone(),
          &mut child_lidb,
          rule_builder,
          context_region.clone(),
          call.template,
        );
        let mut arg_runes = Vec::<RuneUsage<'s>>::new();
        for arg in call.args {
          let mut child_lidb = lidb.child();
          arg_runes.push(translate_templex(
            scout_arena,
            keywords,
            env.clone(),
            &mut child_lidb,
            rule_builder,
            context_region.clone(),
            arg,
          ));
        }
        rule_builder.push(MaybeCoercingCall(MaybeCoercingCallSR {
          range: range_s,
          result_rune: result_rune_s.clone(),
          template_rune: template_rune_s,
          args: scout_arena.alloc_slice_from_vec(arg_runes),
        }));
        result_rune_s
      }

      ITemplexPT::Function(_function) => {
        panic!("POSTPARSER_TRANSLATE_TEMPLEX_FUNCTION_NOT_YET_IMPLEMENTED")
      }

      ITemplexPT::Func(func) => {
        let range_s = PostParser::eval_range(file, func.range);
        let params_range_s = PostParser::eval_range(file, func.params_range);
        let NameP(_, name_p) = &func.name;
        let name: StrI<'s> = scout_arena.intern_str(name_p.as_str());
        let params_s: Vec<RuneUsage<'s>> =
          func.parameters.iter().map(|param_p| {
            translate_templex(scout_arena, keywords, env.clone(), &mut lidb.child(), rule_builder, context_region.clone(), param_p)
          }).collect();
        let param_list_rune_s = RuneUsage { range: params_range_s.clone(), rune: scout_arena.intern_rune(ImplicitRune(ImplicitRuneValS::new(lidb.child().borrow_val()))) };
        rule_builder.push(IRulexSR::Pack(PackSR { range: params_range_s, result_rune: param_list_rune_s.clone(), members: scout_arena.alloc_slice_from_vec(params_s) }));

        let return_rune_s = translate_templex(scout_arena, keywords, env.clone(), &mut lidb.child(), rule_builder, context_region.clone(), func.return_type);

        let result_rune_s = RuneUsage { range: PostParser::eval_range(file, func.range), rune: scout_arena.intern_rune(ImplicitRune(ImplicitRuneValS::new(lidb.child().borrow_val()))) };

        // Only appears in call site; filtered out when solving definition
        rule_builder.push(IRulexSR::CallSiteFunc(CallSiteFuncSR { range: range_s.clone(), prototype_rune: result_rune_s.clone(), name: name.clone(), params_list_rune: param_list_rune_s.clone(), return_rune: return_rune_s.clone() }));
        // Only appears in definition; filtered out when solving call site
        rule_builder.push(IRulexSR::DefinitionFunc(DefinitionFuncSR { range: range_s.clone(), result_rune: result_rune_s.clone(), name: name.clone(), params_list_rune: param_list_rune_s.clone(), return_rune: return_rune_s.clone() }));
        // Only appears in call site; filtered out when solving definition
        rule_builder.push(IRulexSR::Resolve(ResolveSR { range: range_s, result_rune: result_rune_s.clone(), name: name.clone(), params_list_rune: param_list_rune_s, return_rune: return_rune_s }));

        result_rune_s

      }

      ITemplexPT::Pack(_pack) => panic!("POSTPARSER_TRANSLATE_TEMPLEX_PACK_NOT_YET_IMPLEMENTED"),

      ITemplexPT::StaticSizedArray(static_sized_array) => {
        let range_s = PostParser::eval_range(file, static_sized_array.range);
        let mut child_lidb = lidb.child();
        let result_rune_s = RuneUsage {
          range: range_s.clone(),
          rune: scout_arena.intern_rune(ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val()))),
        };
        let mut child_lidb = lidb.child();
        let template_rune_s = RuneUsage {
          range: range_s.clone(),
          rune: scout_arena.intern_rune(ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val()))),
        };
        rule_builder.push(Lookup(LookupSR {
          range: range_s.clone(),
          rune: template_rune_s.clone(),
          name: scout_arena.intern_imprecise_name(CodeName(CodeNameS {
            name: keywords.static_array,
          })),
        }));
        let mut child_lidb = lidb.child();
        let size_rune_s = translate_templex(
          scout_arena,
          keywords,
          env.clone(),
          &mut child_lidb,
          rule_builder,
          context_region.clone(),
          static_sized_array.size,
        );
        let mut child_lidb = lidb.child();
        let mutability_rune_s = translate_templex(
          scout_arena,
          keywords,
          env.clone(),
          &mut child_lidb,
          rule_builder,
          context_region.clone(),
          static_sized_array.mutability,
        );
        let mut child_lidb = lidb.child();
        let variability_rune_s = translate_templex(
          scout_arena,
          keywords,
          env.clone(),
          &mut child_lidb,
          rule_builder,
          context_region.clone(),
          static_sized_array.variability,
        );
        let mut child_lidb = lidb.child();
        let element_rune_s = translate_templex(
          scout_arena,
          keywords,
          env,
          &mut child_lidb,
          rule_builder,
          context_region,
          static_sized_array.element,
        );
        rule_builder.push(MaybeCoercingCall(MaybeCoercingCallSR {
          range: range_s,
          result_rune: result_rune_s.clone(),
          template_rune: template_rune_s,
          args: scout_arena.alloc_slice_from_vec(vec![size_rune_s, mutability_rune_s, variability_rune_s, element_rune_s]),
        }));
        result_rune_s
      }

      ITemplexPT::RuntimeSizedArray(runtime_sized_array) => {
        let range_s = PostParser::eval_range(file, runtime_sized_array.range);
        let mut child_lidb = lidb.child();
        let result_rune_s = RuneUsage {
          range: range_s.clone(),
          rune: scout_arena.intern_rune(ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val()))),
        };
        let mut child_lidb = lidb.child();
        let template_rune_s = RuneUsage {
          range: range_s.clone(),
          rune: scout_arena.intern_rune(ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val()))),
        };
        rule_builder.push(Lookup(LookupSR {
          range: range_s.clone(),
          rune: template_rune_s.clone(),
          name: scout_arena.intern_imprecise_name(CodeName(CodeNameS {
            name: keywords.array,
          })),
        }));
        let mut child_lidb = lidb.child();
        let mutability_rune_s = translate_templex(
          scout_arena,
          keywords,
          env.clone(),
          &mut child_lidb,
          rule_builder,
          context_region.clone(),
          runtime_sized_array.mutability,
        );
        let mut child_lidb = lidb.child();
        let element_rune_s = translate_templex(
          scout_arena,
          keywords,
          env,
          &mut child_lidb,
          rule_builder,
          context_region,
          runtime_sized_array.element,
        );
        rule_builder.push(MaybeCoercingCall(MaybeCoercingCallSR {
          range: range_s,
          result_rune: result_rune_s.clone(),
          template_rune: template_rune_s,
          args: scout_arena.alloc_slice_from_vec(vec![mutability_rune_s, element_rune_s]),
        }));
        result_rune_s
      }

      ITemplexPT::Tuple(tuple) => {
        let range_s = PostParser::eval_range(file, tuple.range);
        let tuple_name = scout_arena.intern_imprecise_name(CodeName(CodeNameS {
          name: keywords.tuple_human_name[tuple.elements.len()],
        }));
        if tuple.elements.is_empty() {
          // Zero-arg case: lower directly to a single MaybeCoercingLookupSR, matching
          // how any other zero-arg kind template (e.g., `Spaceship`) is handled.
          // Emitting a MaybeCoercingCallSR here would deadlock RuneTypeSolver, since
          // its pre-processor declines to seed Tup0's ambiguous templata shape.
          let mut child_lidb = lidb.child();
          let result_rune_s = RuneUsage {
            range: range_s.clone(),
            rune: scout_arena.intern_rune(ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val()))),
          };
          rule_builder.push(MaybeCoercingLookup(MaybeCoercingLookupSR {
            range: range_s,
            rune: result_rune_s.clone(),
            name: tuple_name,
          }));
          result_rune_s
        } else {
          let mut child_lidb = lidb.child();
          let result_rune_s = RuneUsage {
            range: range_s.clone(),
            rune: scout_arena.intern_rune(ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val()))),
          };
          let mut child_lidb = lidb.child();
          let template_rune_s = RuneUsage {
            range: range_s.clone(),
            rune: scout_arena.intern_rune(ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val()))),
          };
          rule_builder.push(MaybeCoercingLookup(MaybeCoercingLookupSR {
            range: range_s.clone(),
            rune: template_rune_s.clone(),
            name: tuple_name,
          }));
          let mut element_runes = Vec::<RuneUsage<'s>>::new();
          for element in tuple.elements {
            let mut child_lidb = lidb.child();
            element_runes.push(translate_templex(
              scout_arena,
              keywords,
              env.clone(),
              &mut child_lidb,
              rule_builder,
              context_region.clone(),
              element,
            ));
          }
          rule_builder.push(MaybeCoercingCall(MaybeCoercingCallSR {
            range: range_s,
            result_rune: result_rune_s.clone(),
            template_rune: template_rune_s,
            args: scout_arena.alloc_slice_from_vec(element_runes),
          }));
          result_rune_s
        }
      }

      _ => panic!("POSTPARSER_TRANSLATE_TEMPLEX_NOT_YET_IMPLEMENTED"),
    },
  }
}

// Returns:
// - Rune for this type
fn translate_type_into_rune<'s, 'p>(scout_arena: &ScoutArena<'s>,
  keywords: &Keywords<'s>,
  env: IEnvironmentS<'s>,
  lidb: &mut LocationInDenizenBuilder,
  rule_builder: &mut Vec<IRulexSR<'s>>,
  // Nearest enclosing region marker, see RADTGCA.
  context_region: IRuneS<'s>,
  type_p: &ITemplexPT<'p>,
) -> RuneUsage<'s> {
  let file = env.file();
  match type_p {
    NameOrRune(NameOrRunePT(NameP(
      range,
      name_or_rune,
    )))
      if env.all_declared_runes().contains(&scout_arena.intern_rune(CodeRune(CodeRuneS {
        name: scout_arena.intern_str(name_or_rune.as_str()),
      }))) =>
    {
      let result_rune_s = RuneUsage {
        range: PostParser::eval_range(file, *range),
        rune: scout_arena.intern_rune(CodeRune(CodeRuneS {
          name: scout_arena.intern_str(name_or_rune.as_str()),
        })),
      };
      result_rune_s
    }
    non_rune_templex_p => {
      let mut child_lidb = lidb.child();
      translate_templex(
        scout_arena,
        keywords,
        env,
        &mut child_lidb,
        rule_builder,
        context_region,
        non_rune_templex_p,
      )
    }
  }
}

// Returns:
// - Rune for this type
pub fn translate_maybe_type_into_rune<'s, 'p>(scout_arena: &ScoutArena<'s>,
  keywords: &Keywords<'s>,
  env: IEnvironmentS<'s>,
  lidb: &mut LocationInDenizenBuilder,
  range: RangeS<'s>,
  rule_builder: &mut Vec<IRulexSR<'s>>,
  context_region: IRuneS<'s>,
  maybe_type_p: Option<&ITemplexPT<'p>>,
) -> RuneUsage<'s> {
  match maybe_type_p {
    None => {
      let mut child_lidb = lidb.child();
      let result_rune_s = RuneUsage {
        range,
        rune: scout_arena.intern_rune(ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val()))),
      };
      result_rune_s
    }
    Some(type_p) => {
      translate_type_into_rune(scout_arena, keywords, env, lidb, rule_builder, context_region, type_p)
    }
  }
}

pub(crate) fn translate_maybe_type_into_maybe_rune<'s, 'p>(scout_arena: &ScoutArena<'s>,
  keywords: &Keywords<'s>,
  env: IEnvironmentS<'s>,
  lidb: &mut LocationInDenizenBuilder,
  range: RangeS<'s>,
  rule_builder: &mut Vec<IRulexSR<'s>>,
  rune_to_explicit_type: &mut HashMap<IRuneS<'s>, ITemplataType>,
  context_region: IRuneS<'s>,
  maybe_type_p: Option<&ITemplexPT<'p>>,
) -> Option<RuneUsage<'s>> {
  if maybe_type_p.is_none() {
    None
  } else {
    let mut child_lidb = lidb.child();
    let result_rune = translate_maybe_type_into_rune(scout_arena,
      keywords,
      env,
      &mut child_lidb,
      range,
      rule_builder,
      context_region,
      maybe_type_p,
    );
    rune_to_explicit_type.insert(
      result_rune.rune.clone(),
      ITemplataType::CoordTemplataType(CoordTemplataType {}),
    );
    Some(result_rune)
  }
}
