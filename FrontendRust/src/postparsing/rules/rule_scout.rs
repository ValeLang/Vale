// Per @DSAUIMZ, all borrow_val() calls in this file borrow from a stack-local
// LocationInDenizenBuilder instead of arena-allocating. The slice is promoted
// to permanent arena storage only inside intern_rune on a miss.

use crate::scout_arena::ScoutArena;
use crate::keywords::Keywords;
use crate::parsing::ast::{BuiltinCallPR, ComponentsPR, EqualsPR, IntPT, IRulexPR, ITypePR, ITemplexPT, OwnershipPT};
use crate::postparsing::ast::LocationInDenizenBuilder;
use crate::postparsing::itemplatatype::{
  BooleanTemplataType, CoordTemplataType, ITemplataType, IntegerTemplataType, KindTemplataType,
  LocationTemplataType, MutabilityTemplataType, OwnershipTemplataType, PackTemplataType,
  PrototypeTemplataType, RegionTemplataType, VariabilityTemplataType,
};
use crate::postparsing::names::{CodeRuneS, IImpreciseNameS, IRuneS, IRuneValS, ImplicitRuneValS};
use crate::postparsing::post_parser::{IEnvironmentS, PostParser};
use crate::postparsing::rules::rules::{
  CoordComponentsSR, EqualsSR, IntLiteralSL, IsInterfaceSR, IRulexSR, OneOfSR,
  OwnershipLiteralSL, RuneUsage,
};
use crate::postparsing::rules::rules::ILiteralSL;
use crate::postparsing::rules::templex_scout::translate_templex;
use std::collections::{HashMap, HashSet};
use crate::postparsing::itemplatatype::ImplTemplataType;
use crate::postparsing::rules::rules::DefinitionCoordIsaSR;
use crate::postparsing::rules::rules::CallSiteCoordIsaSR;
use crate::postparsing::rules::rules::PackSR;
use crate::postparsing::rules::rules::KindComponentsSR;
use crate::postparsing::rules::rules::PrototypeComponentsSR;

// Returns:
// - new rules produced on the side while translating the given rules
// - the translated versions of the given rules
pub fn translate_rulexes<'s, 'p>(
  scout_arena: &ScoutArena<'s>,
  keywords: &Keywords<'s>,
  env: IEnvironmentS<'s>,
  lidb: &mut LocationInDenizenBuilder,
  builder: &mut Vec<IRulexSR<'s>>,
  rune_to_explicit_type: &mut Vec<(IRuneS<'s>, ITemplataType<'s>)>,
  context_region: IRuneS<'s>,
  rules_p: &[IRulexPR<'p>],
) -> Vec<RuneUsage<'s>> {
  rules_p
    .iter()
    .map(|rule_p| {
      let mut child_lidb = lidb.child();
      translate_rulex(
        scout_arena,
        keywords,
        env.clone(),
        &mut child_lidb,
        builder,
        rune_to_explicit_type,
        context_region.clone(),
        rule_p,
      )
    })
    .collect()
}

fn translate_rulex<'s, 'p>(
  scout_arena: &ScoutArena<'s>,
  keywords: &Keywords<'s>,
  env: IEnvironmentS<'s>,
  lidb: &mut LocationInDenizenBuilder,
  builder: &mut Vec<IRulexSR<'s>>,
  rune_to_explicit_type: &mut Vec<(IRuneS<'s>, ITemplataType<'s>)>,
  context_region: IRuneS<'s>,
  rulex: &IRulexPR<'p>,
) -> RuneUsage<'s> {
  let file = match &env {
    IEnvironmentS::Environment(environment) => environment.file,
    IEnvironmentS::FunctionEnvironment(function_environment) => function_environment.file,
  };
  match rulex {
    IRulexPR::Typed(typed_rule) => {
      let rune = match &typed_rule.rune {
        Some(rune_name) => scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS { name: scout_arena.intern_str(rune_name.str().as_str()) })),
        None => {
          let mut child_lidb = lidb.child();
          scout_arena.intern_rune(IRuneValS::ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val())))
        }
      };
      let tyype = translate_type(scout_arena, typed_rule.tyype);
      rune_to_explicit_type.push((rune.clone(), tyype));
      RuneUsage {
        range: PostParser::eval_range(file, typed_rule.range),
        rune,
      }
    }
    IRulexPR::Templex(templex) => {
      let mut child_lidb = lidb.child();
      translate_templex(
        scout_arena,
        keywords,
        env,
        &mut child_lidb,
        builder,
        context_region,
        templex,
      )
    }
    IRulexPR::Equals(EqualsPR { range, left, right }) => {
      let mut child_lidb = lidb.child();
      let rune = scout_arena.intern_rune(IRuneValS::ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val())));
      let left_usage = {
        let mut child_lidb = lidb.child();
        translate_rulex(scout_arena,
          keywords,
          env.clone(),
          &mut child_lidb,
          builder,
          rune_to_explicit_type,
          context_region.clone(),
          left,
        )
      };
      let right_usage = {
        let mut child_lidb = lidb.child();
        translate_rulex(scout_arena,
          keywords,
          env.clone(),
          &mut child_lidb,
          builder,
          rune_to_explicit_type,
          context_region.clone(),
          right,
        )
      };
      builder.push(IRulexSR::Equals(EqualsSR {
        range: PostParser::eval_range(file, *range),
        left: left_usage,
        right: right_usage,
      }));
      RuneUsage {
        range: PostParser::eval_range(file, *range),
        rune,
      }
    }
    IRulexPR::BuiltinCall(BuiltinCallPR { range, name, args }) => {
      if name.str() == keywords.is_interface {
        assert_eq!(args.len(), 1, "POSTPARSER_IS_INTERFACE_ARGS_LEN");
        let mut child_lidb = lidb.child();
        let arg_rune = translate_rulex(scout_arena,
          keywords,
          env.clone(),
          &mut child_lidb,
          builder,
          rune_to_explicit_type,
          context_region.clone(),
          &args[0],
        );
        // val resultRune = ImplicitRuneS(lidb.child().consume())
        builder.push(IRulexSR::IsInterface(IsInterfaceSR {
          range: PostParser::eval_range(file, *range),
          rune: arg_rune.clone(),
        }));
        // runeToExplicitType.put(resultRune, KindTemplataType())
        rune_to_explicit_type.push((
          arg_rune.rune.clone(),
          ITemplataType::KindTemplataType(KindTemplataType {}),
        ));
        RuneUsage {
          range: PostParser::eval_range(file, *range),
          rune: arg_rune.rune,
        }
      } else if name.str() == keywords.implements {
        assert_eq!(args.len(), 2, "POSTPARSER_IMPLEMENTS_ARGS_LEN");
        let struct_rune = translate_rulex(scout_arena, keywords, env.clone(), &mut lidb.child(), builder, rune_to_explicit_type, context_region.clone(), &args[0]);
        rune_to_explicit_type.push((struct_rune.rune.clone(), ITemplataType::CoordTemplataType(CoordTemplataType {})));
        let interface_rune = translate_rulex(scout_arena, keywords, env.clone(), &mut lidb.child(), builder, rune_to_explicit_type, context_region.clone(), &args[1]);
        rune_to_explicit_type.push((interface_rune.rune.clone(), ITemplataType::CoordTemplataType(CoordTemplataType {})));

        let mut child_lidb = lidb.child();
        let result_rune_s = RuneUsage {
          range: PostParser::eval_range(file, *range),
          rune: scout_arena.intern_rune(IRuneValS::ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val()))),
        };
        rune_to_explicit_type.push((result_rune_s.rune.clone(), ITemplataType::ImplTemplataType(ImplTemplataType {})));

        // Only appears in definition; filtered out when solving call site
        builder.push(IRulexSR::DefinitionCoordIsa(DefinitionCoordIsaSR {
          range: PostParser::eval_range(file, *range),
          result_rune: result_rune_s.clone(),
          sub_rune: struct_rune.clone(),
          super_rune: interface_rune.clone(),
        }));
        // Only appears in call site; filtered out when solving definition
        builder.push(IRulexSR::CallSiteCoordIsa(CallSiteCoordIsaSR {
          range: PostParser::eval_range(file, *range),
          result_rune: Some(result_rune_s),
          sub_rune: struct_rune.clone(),
          super_rune: interface_rune,
        }));

        RuneUsage {
          range: PostParser::eval_range(file, *range),
          rune: struct_rune.rune,
        }
      } else if name.str() == keywords.ref_list_compound_mutability {
        panic!("POSTPARSER_TRANSLATE_RULEX_BUILTINCALL_REF_LIST_COMPOUND_MUTABILITY_NOT_YET_IMPLEMENTED")
      } else if name.str() == keywords.refs {
        let arg_runes: Vec<RuneUsage<'s>> =
          args.iter().map(|arg| {
            translate_rulex(scout_arena, keywords, env.clone(), &mut lidb.child(), builder, rune_to_explicit_type, context_region.clone(), arg)
          }).collect();

        let mut child_lidb = lidb.child();
        let result_rune = RuneUsage {
          range: PostParser::eval_range(file, *range),
          rune: scout_arena.intern_rune(IRuneValS::ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val()))),
        };
        builder.push(IRulexSR::Pack(PackSR {
          range: PostParser::eval_range(file, *range),
          result_rune: result_rune.clone(),
          members: scout_arena.alloc_slice_from_vec(arg_runes),
        }));
        rune_to_explicit_type.push((result_rune.rune.clone(), ITemplataType::PackTemplataType(PackTemplataType { element_type: &*scout_arena.alloc(ITemplataType::CoordTemplataType(CoordTemplataType {})) })));

        result_rune
      } else if name.str() == keywords.any {
        let literals: Vec<ILiteralSL> = args
          .iter()
          .map(|arg| match arg {
            IRulexPR::Templex(templex) => match templex {
              ITemplexPT::Int(IntPT { value, .. }) => {
                ILiteralSL::IntLiteral(IntLiteralSL { value: *value })
              }
              ITemplexPT::Ownership(OwnershipPT(_, ownership)) => {
                ILiteralSL::OwnershipLiteral(OwnershipLiteralSL {
                  ownership: *ownership,
                })
              }
              _ => panic!("POSTPARSER_BUILTINCALL_ANY_ARG_NOT_INT_OR_OWNERSHIP"),
            },
            _ => panic!("POSTPARSER_BUILTINCALL_ANY_ARG_NOT_TEMPLEX"),
          })
          .collect();
        assert!(!literals.is_empty(), "POSTPARSER_ANY_LITERALS_EMPTY");
        let distinct_types: HashSet<_> =
          literals.iter().map(|l| l.get_type()).collect();
        assert_eq!(distinct_types.len(), 1, "POSTPARSER_ANY_LITERALS_MIXED_TYPES");
        let explicit_type = literals.first().unwrap().get_type();
        let mut child_lidb = lidb.child();
        let result_rune = RuneUsage {
          range: PostParser::eval_range(file, *range),
          rune: scout_arena.intern_rune(IRuneValS::ImplicitRune(ImplicitRuneValS::new(child_lidb.borrow_val()))),
        };
        builder.push(IRulexSR::OneOf(OneOfSR {
          range: PostParser::eval_range(file, *range),
          rune: result_rune.clone(),
          literals: scout_arena.alloc_slice_from_vec(literals),
        }));
        rune_to_explicit_type.push((result_rune.rune.clone(), explicit_type));
        result_rune
      } else {
        panic!("POSTPARSER_TRANSLATE_RULEX_BUILTINCALL_NOT_YET_IMPLEMENTED")
      }
    }
    IRulexPR::Components(ComponentsPR {
      range,
      container: tyype,
      components,
    }) => {
      let mut rune_child_lidb = lidb.child();
      let rune = RuneUsage {
        range: PostParser::eval_range(file, *range),
        rune: scout_arena.intern_rune(IRuneValS::ImplicitRune(ImplicitRuneValS::new(rune_child_lidb.borrow_val()))),
      };
      rune_to_explicit_type.push((rune.rune.clone(), translate_type(scout_arena, *tyype)));
      match tyype {
        ITypePR::CoordType => {
          // vregionmut() // Put back in with regions
          // if (componentsP.size != 3) {
          //   vfail("Ref rule should have three components! Found: " + componentsP.size)
          // }
          if components.len() != 2 {
            panic!("POSTPARSER_COMPONENTS_REF_SHOULD_HAVE_TWO_COMPONENTS")
          }
          // vregionmut() // Put back in with regions
          // val Vector(ownershipRuneS, regionRuneS, kindRuneS) =
          let mut translate_child_lidb = lidb.child();
          let component_usages = translate_rulexes(
            scout_arena,
            keywords,
            env,
            &mut translate_child_lidb,
            builder,
            rune_to_explicit_type,
            context_region,
            components,
          );
          let ownership_rune = component_usages[0].clone();
          let kind_rune = component_usages[1].clone();
          builder.push(IRulexSR::CoordComponents(CoordComponentsSR {
            range: PostParser::eval_range(file, *range),
            result_rune: rune.clone(),
            ownership_rune,
            kind_rune,
          }));
        }
        ITypePR::KindType => {
          if components.len() != 1 {
            panic!("Kind rule should have one component! Found: {}", components.len())
          }
          let mut translate_child_lidb = lidb.child();
          let component_usages = translate_rulexes(
            scout_arena,
            keywords,
            env,
            &mut translate_child_lidb,
            builder,
            rune_to_explicit_type,
            context_region,
            components,
          );
          let mutability_rune = component_usages[0].clone();
          builder.push(IRulexSR::KindComponents(KindComponentsSR {
            range: PostParser::eval_range(file, *range),
            kind_rune: rune.clone(),
            mutability_rune,
          }));
        }
        ITypePR::PrototypeType => {
          if components.len() != 2 {
            panic!("Prot rule should have two components! Found: {}", components.len())
          }
          let mut translate_child_lidb = lidb.child();
          let component_usages = translate_rulexes(
            scout_arena,
            keywords,
            env,
            &mut translate_child_lidb,
            builder,
            rune_to_explicit_type,
            context_region,
            components,
          );
          let params_rune = component_usages[0].clone();
          let return_rune = component_usages[1].clone();
          builder.push(IRulexSR::PrototypeComponents(PrototypeComponentsSR {
            range: PostParser::eval_range(file, *range),
            result_rune: rune.clone(),
            params_rune,
            return_rune,
          }));
  
        }
        _ => panic!("POSTPARSER_COMPONENTS_INVALID_TYPE_FOR_COMPONENTS_RULE"),
      }
      rune
    }
    _ => panic!("POSTPARSER_TRANSLATE_RULEX_NOT_YET_IMPLEMENTED"),
  }
}

pub fn translate_type<'s>(scout_arena: &ScoutArena<'s>, tyype: ITypePR) -> ITemplataType<'s> {
  match tyype {
    ITypePR::PrototypeType => ITemplataType::PrototypeTemplataType(PrototypeTemplataType {}),
    ITypePR::IntType => ITemplataType::IntegerTemplataType(IntegerTemplataType {}),
    ITypePR::BoolType => ITemplataType::BooleanTemplataType(BooleanTemplataType {}),
    ITypePR::OwnershipType => ITemplataType::OwnershipTemplataType(OwnershipTemplataType {}),
    ITypePR::MutabilityType => ITemplataType::MutabilityTemplataType(MutabilityTemplataType {}),
    ITypePR::VariabilityType => ITemplataType::VariabilityTemplataType(VariabilityTemplataType {}),
    ITypePR::LocationType => ITemplataType::LocationTemplataType(LocationTemplataType {}),
    ITypePR::CoordType => ITemplataType::CoordTemplataType(CoordTemplataType {}),
    ITypePR::CoordListType => ITemplataType::PackTemplataType(PackTemplataType {
      element_type: scout_arena.alloc(ITemplataType::CoordTemplataType(CoordTemplataType {})),
    }),
    ITypePR::KindType => ITemplataType::KindTemplataType(KindTemplataType {}),
    ITypePR::RegionType => ITemplataType::RegionTemplataType(RegionTemplataType {}),
    ITypePR::CitizenTemplateType => {
      panic!("POSTPARSER_TRANSLATE_TYPE_CITIZEN_TEMPLATE_NOT_YET_IMPLEMENTED")
    }
  }
}

fn get_rune_kind_template<'s>(
  _rules_s: &[IRulexSR<'s>],
  _rune: IRuneS<'s>,
) -> IImpreciseNameS<'s> {
  panic!("Unimplemented get_rune_kind_template");
}

struct Equivalencies<'s> {
  rune_to_kind_equivalent_runes: indexmap::IndexMap<IRuneS<'s>, indexmap::IndexSet<IRuneS<'s>>>,
}

impl<'s> Equivalencies<'s> {
  fn mark_kind_equivalent(&mut self, rune_a: IRuneS<'s>, rune_b: IRuneS<'s>) {
    self.rune_to_kind_equivalent_runes.entry(rune_a).or_default().insert(rune_b);
    self.rune_to_kind_equivalent_runes.entry(rune_b).or_default().insert(rune_a);
  }

  fn new(rules_s: &[IRulexSR<'s>]) -> Self {
    let mut this = Self { rune_to_kind_equivalent_runes: indexmap::IndexMap::new() };
    for rule in rules_s {
      match rule {
        IRulexSR::CoordComponents(r) => this.mark_kind_equivalent(r.result_rune.rune, r.kind_rune.rune),
        IRulexSR::KindComponents(_) => {}
        IRulexSR::Equals(r) => this.mark_kind_equivalent(r.left.rune, r.right.rune),
        IRulexSR::Call(_) => {}
        IRulexSR::MaybeCoercingCall(_) => {}
        IRulexSR::CallSiteCoordIsa(_) => {}
        IRulexSR::DefinitionCoordIsa(_) => {}
        IRulexSR::CoordSend(_) => {}
        IRulexSR::Augment(r) => this.mark_kind_equivalent(r.result_rune.rune, r.inner_rune.rune),
        IRulexSR::Literal(_) => {}
        IRulexSR::MaybeCoercingLookup(_) => {}
        IRulexSR::CoerceToCoord(r) => this.mark_kind_equivalent(r.coord_rune.rune, r.kind_rune.rune),
        IRulexSR::OneOf(_) => {}
        IRulexSR::CallSiteFunc(_) => {}
        IRulexSR::DefinitionFunc(_) => {}
        IRulexSR::Resolve(_) => {}
        IRulexSR::Pack(_) => {}
        IRulexSR::PrototypeComponents(_) => {}
        IRulexSR::RefListCompoundMutability(_) => {}
        _ => panic!("implement: Equivalencies::new unhandled rule"),
      }
    }
    this
  }

  fn find_transitively_equivalent_into(
    &self,
    found_so_far: &mut indexmap::IndexSet<IRuneS<'s>>,
    rune: IRuneS<'s>,
  ) {
    let equivalents: Vec<IRuneS<'s>> = self.rune_to_kind_equivalent_runes
      .get(&rune)
      .map(|s| s.iter().copied().collect())
      .unwrap_or_default();
    for r in equivalents {
      if !found_so_far.contains(&r) {
        found_so_far.insert(r);
        self.find_transitively_equivalent_into(found_so_far, r);
      }
    }
  }

  fn get_kind_equivalent_runes(&self, rune: IRuneS<'s>) -> indexmap::IndexSet<IRuneS<'s>> {
    let mut set: indexmap::IndexSet<IRuneS<'s>> = indexmap::IndexSet::new();
    set.insert(rune);
    self.find_transitively_equivalent_into(&mut set, rune);
    set
  }

  fn get_kind_equivalent_runes_iter<I>(&self, runes: I) -> indexmap::IndexSet<IRuneS<'s>>
  where
    I: Iterator<Item = IRuneS<'s>>,
  {
    runes.flat_map(|r| self.get_kind_equivalent_runes(r)).collect()
  }
}

pub fn get_kind_equivalent_runes_iter<'s, I>(
  rules_s: &[IRulexSR<'s>],
  runes: I,
) -> indexmap::IndexSet<IRuneS<'s>>
where
  I: Iterator<Item = IRuneS<'s>>,
{
  Equivalencies::new(rules_s).get_kind_equivalent_runes_iter(runes)
}
