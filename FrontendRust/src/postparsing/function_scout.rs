// AFTERM: rename to function_post_parser.rs
// AFTERM: review scout_function
// Per @DSAUIMZ, all borrow_val() calls in this file borrow from a stack-local
// LocationInDenizenBuilder instead of arena-allocating. The slice is promoted
// to permanent arena storage only inside intern_rune on a miss.

use crate::parsing::ast::{FunctionP, GenericParameterP, IAttributeP, ITemplexPT, LoadAsP};
use crate::parsing::ast::rules::get_ordered_rune_declarations_from_rulexes_with_duplicates;
use crate::postparsing::ast::{
  AbstractBodyS, AbstractSP, AdditiveS, BuiltinS, CodeBodyS, CoordGenericParameterTypeS, ExportS,
  ExternBodyS, ExternS, FunctionS, GeneratedBodyS, GenericParameterS, IBodyS, IFunctionAttributeS,
  IGenericParameterTypeS, IRegionMutabilityS, LocationInDenizenBuilder, ParameterS, PureS,
  RegionGenericParameterTypeS,
};
use crate::postparsing::expressions::{
  BlockSE, BodySE, ConsecutorSE, IExpressionSE,
};
use crate::postparsing::itemplatatype::{
  CoordTemplataType, FunctionTemplataType, ITemplataType, KindTemplataType, TemplateTemplataType,
};
use crate::postparsing::patterns::{AtomSP, CaptureS};
use crate::lexing::ast::RangeL;
use crate::postparsing::names::{
  ClosureParamNameS, CodeNameS, CodeRuneS, DenizenDefaultRegionRuneS, FunctionNameS,
  IFunctionDeclarationNameS, IFunctionDeclarationNameValS, IImpreciseNameValS, INameS, INameValS,
  IRuneS, IRuneValS, IVarNameS, IVarNameValS, ImplicitRegionRuneValS, ImplicitRuneValS, LambdaDeclarationNameS,
  LambdaStructDeclarationNameS, MagicParamRuneValS,
};
use crate::postparsing::post_parser::{
  CouldntFindRuneS, ExternHasBodyS, FunctionEnvironmentS, ICompileErrorS, IEnvironmentS,
  InterfaceMethodNeedsSelf, PostParser, RangedInternalErrorS, StackFrame, VirtualAndAbstractGoTogether,
};
use crate::postparsing::patterns::pattern_scout::{get_parameter_captures, translate_pattern};
use crate::postparsing::rules::rule_scout::translate_rulexes;
use crate::postparsing::rules::templex_scout::translate_maybe_type_into_maybe_rune;
use crate::parsing::ast::OwnershipP;
use crate::postparsing::rules::rules::{
  AugmentSR, CoerceToCoordSR, IRulexSR, LookupSR, MaybeCoercingLookupSR, RuneUsage,
};
use crate::postparsing::variable_uses::{VariableDeclarationS, VariableDeclarations, VariableUses};
use crate::utils::range::RangeS;
use crate::utils::code_hierarchy::FileCoordinate;
use std::collections::HashMap;
use crate::utils::arena_index_map::ArenaIndexMap;
use indexmap::IndexSet;
use crate::parsing::ast::BlockPE;
use crate::postparsing::expressions::LocalS;

#[derive(Clone, Debug, PartialEq)]
pub enum IFunctionParent<'s>

{
  FunctionNoParent,
  ParentCitizen(ParentCitizen<'s>),
  ParentFunction {
    parent_stack_frame: StackFrame<'s>,
  },
}

#[derive(Clone, Debug, PartialEq)]
pub struct ParentCitizen<'s> {
  pub citizen_is_interface: bool,
  pub citizen_env: IEnvironmentS<'s>,
  pub citizen_generic_params: &'s [&'s GenericParameterS<'s>],
  pub citizen_rules: Vec<IRulexSR<'s>>,
  pub citizen_rune_to_explicit_type: HashMap<IRuneS<'s>, ITemplataType<'s>>,
}

impl<'s, 'p, 'ctx> PostParser<'s, 'p, 'ctx>
{
  pub(crate) fn scout_function(
    &self,
    file_coordinate: &'s FileCoordinate<'s>,
    function: &FunctionP<'p>,
    maybe_parent: IFunctionParent<'s>,
  ) -> Result<(&'s FunctionS<'s>, VariableUses<'s>), ICompileErrorS<'s>>
  {
    // AFTERM: check the order of these various chunks of logic

    let is_parent_function = matches!(&maybe_parent, IFunctionParent::ParentFunction { .. });
    let is_parent_interface = matches!(&maybe_parent, IFunctionParent::ParentCitizen(ParentCitizen { citizen_is_interface: true, .. }));
    let function_name = function.header.name.as_ref();
    let generic_parameters_p: &[GenericParameterP<'p>] = function
      .header
      .generic_parameters
      .as_ref()
      .map(|generic_parameters| generic_parameters.params)
      .unwrap_or(&[]);
    // See: Must Scan For Declared Runes First (MSFDRF)
    let user_specified_identifying_runes: Vec<RuneUsage<'s>> = generic_parameters_p
      .iter()
      .map(|generic_parameter| RuneUsage {
        range: Self::eval_range(file_coordinate, function.range),
        rune: self.scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
          name: self.scout_arena.intern_str(generic_parameter.name.str().as_str()),
        })),
      })
      .collect();
    let user_runes_from_rules: Vec<RuneUsage<'s>> = function
      .header
      .template_rules
      .as_ref()
      .map(|template_rules| {
        get_ordered_rune_declarations_from_rulexes_with_duplicates(template_rules.rules)
          .into_iter()
          .map(|name_p| RuneUsage {
            range: Self::eval_range(file_coordinate, function.range),
            rune: self.scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
              name: self.scout_arena.intern_str(name_p.str().as_str()),
            })),
          })
          .collect()
      })
      .unwrap_or_default();
    let mut user_declared_runes = user_specified_identifying_runes.clone();
    for rune_usage in user_runes_from_rules {
      if !user_declared_runes
        .iter()
        .any(|existing_rune_usage| existing_rune_usage.rune == rune_usage.rune)
      {
        user_declared_runes.push(rune_usage);
      }
    }
    match &maybe_parent {
      IFunctionParent::FunctionNoParent => {
        if function.header.attributes.iter().any(|a| matches!(a, IAttributeP::AbstractAttribute(_))) {
          match &function.header.params {
            None => {
              return Err(ICompileErrorS::VirtualAndAbstractGoTogether(
                VirtualAndAbstractGoTogether {
                  range: Self::eval_range(file_coordinate, function.range),
                },
              ));
            }
            Some(params) => {
              if !params.params.iter().any(|param| param.virtuality.is_some()) {
                return Err(ICompileErrorS::VirtualAndAbstractGoTogether(
                  VirtualAndAbstractGoTogether {
                    range: Self::eval_range(file_coordinate, function.range),
                  },
                ));
              }
            }
          }
        }
      }
      IFunctionParent::ParentFunction { .. } => {}
      IFunctionParent::ParentCitizen(ParentCitizen { citizen_is_interface, .. }) => {
        // When we have traits that can have static methods, this check might need to go away
        if *citizen_is_interface {
          if let Some(params) = &function.header.params {
            if !params.params.iter().any(|param| param.virtuality.is_some()) {
              return Err(ICompileErrorS::InterfaceMethodNeedsSelf(
                InterfaceMethodNeedsSelf {
                  range: Self::eval_range(file_coordinate, function.range),
                },
              ));
            }
          }
        }
      }
    }
    let mut lidb = LocationInDenizenBuilder::new(vec![]);
    let mut rules: Vec<IRulexSR<'s>> = Vec::new();
    let mut rune_to_explicit_type: Vec<(IRuneS<'s>, ITemplataType)> = Vec::new();
    let function_declaration_name = match (&maybe_parent, function_name) {
      (IFunctionParent::ParentFunction { .. }, Some(_)) => {
        panic!("POSTPARSER_SCOUT_LAMBDA_WITH_NAME_NOT_YET_IMPLEMENTED");
      }
      (_, Some(function_name)) => self.scout_arena.intern_name(INameValS::FunctionDeclaration(
        IFunctionDeclarationNameValS::FunctionName(FunctionNameS {
          name: self.scout_arena.intern_str(function_name.str().as_str()),
          code_location: Self::eval_pos(file_coordinate, function.range.begin()),
        }),
      )),
      (IFunctionParent::ParentFunction { .. }, None) => self.scout_arena.intern_name(
        INameValS::FunctionDeclaration(IFunctionDeclarationNameValS::LambdaDeclarationName(
          LambdaDeclarationNameS {
            code_location: Self::eval_pos(file_coordinate, function.range.begin()),
          },
        )),
      ),
      _ => panic!("POSTPARSER_SCOUT_FUNCTION_WITHOUT_NAME"),
    };
    let function_declaration_name_for_env = match &function_declaration_name {
      INameS::FunctionDeclaration(r) => (*r).clone(),
      _ => panic!("POSTPARSER_INTERN_FUNCTION_NAME_EXPECTED_FUNCTION_DECLARATION"),
    };
    let extra_generic_params_from_parent: Vec<&'s GenericParameterS<'s>> = match &maybe_parent {
      IFunctionParent::ParentCitizen(ParentCitizen {
        citizen_generic_params,
        ..
      }) => citizen_generic_params.to_vec(),
      _ => Vec::new(),
    };
    for gp in &extra_generic_params_from_parent {
      rune_to_explicit_type.push((gp.rune.rune.clone(), gp.tyype.tyype()));
    }
    let parent_env: Option<Box<IEnvironmentS<'s>>> = match &maybe_parent {
      IFunctionParent::FunctionNoParent => None,
      IFunctionParent::ParentFunction { parent_stack_frame } => {
        Some(Box::new(IEnvironmentS::FunctionEnvironment(
          parent_stack_frame.parent_env.clone(),
        )))
      }
      IFunctionParent::ParentCitizen(ParentCitizen { citizen_env: interface_env, .. }) => {
        Some(Box::new(interface_env.clone()))
      }
    };
    let declared_runes: IndexSet<IRuneS<'s>> = user_declared_runes
      .iter()
      .map(|rune_usage| rune_usage.rune.clone())
      .collect();
    let function_environment = FunctionEnvironmentS {
      file: file_coordinate,
      name: function_declaration_name_for_env.clone(),
      parent_env,
      declared_runes,
      num_explicit_params: function
        .header
        .params
        .as_ref()
        .map(|params| params.params.len() as i32)
        .unwrap_or(0),
      is_interface_internal_method: matches!(&maybe_parent, IFunctionParent::ParentCitizen(_)),
    };
    let header_range_s = Self::eval_range(file_coordinate, function.header.range);
    let (default_region_rune, _maybe_region_generic_param): (IRuneS<'s>, _) = match function
      .body
      .as_ref()
      .and_then(|body| body.maybe_default_region.as_ref())
    {
      None => {
        let region_range = RangeS::new(
          header_range_s.end.clone(),
          header_range_s.end.clone(),
        );
        let rune = self.scout_arena.intern_rune(IRuneValS::DenizenDefaultRegionRune(
          DenizenDefaultRegionRuneS {
            denizen_name: function_declaration_name.clone(),
          },
        ));
        let implicit_region_generic_param = GenericParameterS {
          range: region_range.clone(),
          rune: RuneUsage {
            range: region_range,
            rune: rune.clone(),
          },
          tyype: IGenericParameterTypeS::RegionGenericParameterType(
            RegionGenericParameterTypeS {
              mutability: IRegionMutabilityS::ReadWriteRegion,
            },
          ),
          default: None,
        };
        (rune, Some(implicit_region_generic_param))
      }
      Some(region_rune_pt) => {
        let region_name = region_rune_pt
          .name
          .as_ref()
          .unwrap_or_else(|| panic!("POSTPARSER_SCOUT_FUNCTION_DEFAULT_REGION_NAME_MISSING"));
        let rune = self.scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
          name: self.scout_arena.intern_str(region_name.str().as_str()),
        }));
        if !function_environment.all_declared_runes().contains(&rune) {
          return Err(ICompileErrorS::CouldntFindRuneS(CouldntFindRuneS {
            range: Self::eval_range(file_coordinate, function.range),
            name: region_name.str().as_str().to_string(),
          }));
        }
        (rune, None)
      }
    };
    let template_rules_p = function
      .header
      .template_rules
      .as_ref()
      .map(|template_rules| template_rules.rules)
      .unwrap_or(&[]);
    match &maybe_parent {
      IFunctionParent::FunctionNoParent => {
        let mut child_lidb = lidb.child();
        translate_rulexes(
          self.scout_arena,
          self.keywords,
          IEnvironmentS::FunctionEnvironment(function_environment.clone()),
          &mut child_lidb,
          &mut rules,
          &mut rune_to_explicit_type,
          default_region_rune.clone(),
          template_rules_p,
        );
      }
      IFunctionParent::ParentFunction { .. } => {
        assert!(
          template_rules_p.is_empty(),
          "POSTPARSER_SCOUT_FUNCTION_TEMPLATE_RULES_NOT_YET_IMPLEMENTED"
        );
      }
      IFunctionParent::ParentCitizen(ParentCitizen { citizen_env: interface_env, .. }) => {
        let mut child_lidb = lidb.child();
        translate_rulexes(
          self.scout_arena,
          self.keywords,
          interface_env.clone(),
          &mut child_lidb,
          &mut rules,
          &mut rune_to_explicit_type,
          default_region_rune.clone(),
          template_rules_p,
        );
      }
    }
    // We'll add the implicit runes to the end, see IRRAE.
    let function_user_specified_generic_parameters_s: Vec<&'s GenericParameterS<'s>> = generic_parameters_p
      .iter()
      .zip(user_specified_identifying_runes.iter())
      .map(|(generic_parameter_p, identifying_rune_s)| {
        let mut child_lidb = lidb.child();
        &*self.scout_arena.alloc(self.scout_generic_parameter(
          IEnvironmentS::FunctionEnvironment(function_environment.clone()),
          &mut child_lidb,
          &mut rune_to_explicit_type,
          &mut rules,
          default_region_rune.clone(),
          generic_parameter_p,
          identifying_rune_s.clone(),
        ))
      })
      .collect::<Vec<_>>();
    let params_p: Vec<_> = function
      .header
      .params
      .as_ref()
      .map(|params| {
        params
          .params
          .iter()
          .map(|param| {
            match &maybe_parent {
              IFunctionParent::FunctionNoParent | IFunctionParent::ParentCitizen(_) => {
                assert!(
                  param.pattern.as_ref().map(|pattern| pattern.templex.is_some()).unwrap_or(false),
                  "POSTPARSER_SCOUT_FUNCTION_PARAM_TYPE_REQUIRED_NOT_YET_IMPLEMENTED"
                );
              }
              IFunctionParent::ParentFunction { .. } => {}
            }
            param
          })
          .collect()
      })
      .unwrap_or_default();
    // We say PerhapsTypeless because we're in a lambda, they might be anonymous params.
    // For lambdas, untyped explicit params (like `(a, b) => ...` or `(_) => ...`) get a
    // synthesized coord rune here that will be added to the function's identifying runes.
    let explicit_params_s_and_synthesized_runes: Vec<(ParameterS<'s>, Option<RuneUsage<'s>>)> = params_p
      .iter()
      .map(|param| {
            let param_range = PostParser::eval_range(file_coordinate, param.range);
            let virtuality = param.virtuality.as_ref().map(|abstract_p| AbstractSP {
              range: PostParser::eval_range(file_coordinate, abstract_p.range),
              is_internal_method: matches!(&maybe_parent, IFunctionParent::ParentCitizen(_)),
            });
            let (pattern, synthesized_rune): (AtomSP<'s>, Option<RuneUsage<'s>>) = match (&param.self_borrow, &param.pattern) {
              (Some(_), None) => {
                let coord_rune = RuneUsage {
                  range: param_range.clone(),
                  rune: self.scout_arena.intern_rune(IRuneValS::ImplicitRune(ImplicitRuneValS::new(lidb.child().borrow_val()))),
                };
                rune_to_explicit_type.push((
                  coord_rune.rune.clone(),
                  ITemplataType::CoordTemplataType(CoordTemplataType {}),
                ));
                let pattern_s = AtomSP {
                  range: param_range.clone(),
                  name: Some(CaptureS {
                    name: IVarNameS::CodeVarName(self.keywords.self_),
                    mutate: false,
                  }),
                  coord_rune: Some(coord_rune),
                  destructure: None,
                };
                (pattern_s, None)
              }
              (None, Some(pattern)) => {
                let mut pattern_lidb = lidb.child();
                let mut rune_to_explicit_type_for_pattern: HashMap<IRuneS<'s>, ITemplataType> =
                  rune_to_explicit_type.iter().cloned().collect();
                let mut pattern_s = translate_pattern(
                  self.scout_arena,
                  self.keywords,
                  StackFrame {
                    file: file_coordinate,
                    name: function_declaration_name_for_env.clone(),
                    parent_env: function_environment.clone(),
                    maybe_parent: None,
                    context_region: default_region_rune.clone(),
                    pure_height: 0,
                    locals: VariableDeclarations { vars: Vec::new() },
                  },
                  &mut pattern_lidb,
                  &mut rules,
                  &mut rune_to_explicit_type_for_pattern,
                  pattern,
                );
                for (rune, tyype) in rune_to_explicit_type_for_pattern {
                  if !rune_to_explicit_type
                    .iter()
                    .any(|(existing_rune, _)| *existing_rune == rune)
                  {
                    rune_to_explicit_type.push((rune, tyype));
                  }
                }
                match pattern_s.coord_rune {
                  None => {
                    // Untyped param (like in `(a) => a`) so make a rune that will be added to
                    // genericParams and to the identifying runes. This only happens for lambdas,
                    // top level functions can't have these (enforced elsewhere).
                    let coord_rune = RuneUsage {
                      range: param_range.clone(),
                      rune: self.scout_arena.intern_rune(IRuneValS::ImplicitRune(ImplicitRuneValS::new(lidb.child().borrow_val()))),
                    };
                    rune_to_explicit_type.push((
                      coord_rune.rune.clone(),
                      ITemplataType::CoordTemplataType(CoordTemplataType {}),
                    ));
                    pattern_s.coord_rune = Some(coord_rune.clone());
                    (pattern_s, Some(coord_rune))
                  }
                  Some(_) => (pattern_s, None),
                }
              }
              _ => panic!("POSTPARSER_SCOUT_FUNCTION_PARAM_FORM_NOT_YET_IMPLEMENTED"),
            };
            (
              ParameterS::new(
                param_range.clone(),
                virtuality,
                param.maybe_pre_checked.is_some(),
                pattern,
              ),
              synthesized_rune,
            )
      })
      .collect::<Vec<(ParameterS<'s>, Option<RuneUsage<'s>>)>>();
    let mut explicit_params_s: Vec<ParameterS<'s>> = Vec::new();
    let mut explicit_params_synthesized_runes: Vec<RuneUsage<'s>> = Vec::new();
    for (param_s, maybe_rune) in explicit_params_s_and_synthesized_runes {
      explicit_params_s.push(param_s);
      if let Some(rune) = maybe_rune {
        explicit_params_synthesized_runes.push(rune);
      }
    }
    // Untyped lambda params (from `(_) =>` or `(a, b) =>`) contribute their synthesized coord runes here so later
    // passes see a uniform FunctionS shape regardless of whether the user wrote `<T>` or an untyped param.
    let extra_generic_params_from_explicit_params_s: Vec<&'s GenericParameterS<'s>> =
      explicit_params_synthesized_runes.into_iter()
        .map(|rune| {
          &*self.scout_arena.alloc(GenericParameterS {
            range: rune.range,
            rune,
            tyype: IGenericParameterTypeS::CoordGenericParameterType(CoordGenericParameterTypeS {
              coord_region: None,
              kind_mutable: true,
              region_mutable: false,
            }),
            default: None,
          })
        })
        .collect();
    let maybe_capture_declarations = match function.body {
      None => None,
      Some(_) => {
        let mut first_params = match &maybe_parent {
          IFunctionParent::FunctionNoParent | IFunctionParent::ParentCitizen(_) => {
            VariableDeclarations { vars: Vec::new() }
          }
          IFunctionParent::ParentFunction { .. } => {
            let closure_param_pos = Self::eval_pos(file_coordinate, function.range.begin());
            let closure_param_name = match self.scout_arena.intern_name(INameValS::VarName(
              IVarNameValS::ClosureParamName(ClosureParamNameS {
                code_location: closure_param_pos,
              }),
            )) {
              INameS::VarName(r) => (*r).clone(),
              _ => panic!("POSTPARSER_INTERN_VAR_NAME_EXPECTED_VAR_NAME"),
            };
            VariableDeclarations {
              vars: vec![VariableDeclarationS {
                name: closure_param_name,
              }],
            }
          }
        };
        for explicit_param_s in &explicit_params_s {
          let param_declarations = VariableDeclarations {
            vars: get_parameter_captures(&explicit_param_s.pattern),
          };
          first_params = first_params.plus_plus(&param_declarations);
        }
        Some(first_params)
      }
    };
    let maybe_ret_coord_rune = match &function.header.ret.ret_type {
      None | Some(ITemplexPT::RegionRune(_)) => {
        if is_parent_function {
          None
        } else {
          let ret_range_s = Self::eval_range(file_coordinate, function.header.ret.range);
          let ret_rune = RuneUsage {
            range: ret_range_s.clone(),
            rune: self.scout_arena.intern_rune(IRuneValS::ImplicitRune(ImplicitRuneValS::new(lidb.child().borrow_val()))),
          };
          rules.push(IRulexSR::MaybeCoercingLookup(MaybeCoercingLookupSR {
            range: ret_range_s.clone(),
            rune: ret_rune.clone(),
            name: self
              .scout_arena
              .intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS {
                name: self.keywords.void,
              })),
          }));
          rune_to_explicit_type.push((
            ret_rune.rune.clone(),
            ITemplataType::CoordTemplataType(CoordTemplataType {}),
          ));
          Some(ret_rune)
        }
      }
      Some(ret_type_p) => {
        let mut ret_lidb = lidb.child();
        let mut rune_to_explicit_type_for_ret: HashMap<IRuneS<'s>, ITemplataType> =
          rune_to_explicit_type.iter().cloned().collect();
        let ret_rune = translate_maybe_type_into_maybe_rune(
          self.scout_arena,
          self.keywords,
          IEnvironmentS::FunctionEnvironment(function_environment.clone()),
          &mut ret_lidb,
          Self::eval_range(file_coordinate, function.header.ret.range),
          &mut rules,
          &mut rune_to_explicit_type_for_ret,
          default_region_rune.clone(),
          Some(ret_type_p),
        );
        for (rune, tyype) in rune_to_explicit_type_for_ret {
          if !rune_to_explicit_type
            .iter()
            .any(|(existing_rune, _)| *existing_rune == rune)
          {
            rune_to_explicit_type.push((rune, tyype));
          }
        }
        if let Some(ret_rune) = &ret_rune {
          rune_to_explicit_type.push((
            ret_rune.rune.clone(),
            ITemplataType::CoordTemplataType(CoordTemplataType {}),
          ));
        }
        ret_rune
      }
    };
    let has_extern_attr = function
      .header
      .attributes
      .iter()
      .any(|attr| matches!(attr, IAttributeP::ExternAttribute(_)));
    let has_abstract_attr = function
      .header
      .attributes
      .iter()
      .any(|attr| matches!(attr, IAttributeP::AbstractAttribute(_)));
    let has_builtin_attr = function
      .header
      .attributes
      .iter()
      .any(|attr| matches!(attr, IAttributeP::BuiltinAttribute(_)));
    if is_parent_interface && has_abstract_attr {
      return Err(ICompileErrorS::RangedInternalErrorS(RangedInternalErrorS {
        range: Self::eval_range(file_coordinate, function.range),
        message: "Dont need abstract here".to_string(),
      }));
    }
    let (body_s, variable_uses, total_params_s, extra_generic_params_from_body) = if is_parent_interface {
      (
        &*self.scout_arena.alloc(IBodyS::AbstractBody(AbstractBodyS {})),
        VariableUses::<'s>::empty(),
        explicit_params_s,
        Vec::<&'s GenericParameterS<'s>>::new(),
      )
    } else if has_abstract_attr {
      (
        &*self.scout_arena.alloc(IBodyS::AbstractBody(AbstractBodyS {})),
        VariableUses::<'s>::empty(),
        explicit_params_s,
        Vec::<&'s GenericParameterS<'s>>::new(),
      )
    } else if has_extern_attr {
      if function.body.is_some() {
        return Err(ICompileErrorS::ExternHasBodyS(ExternHasBodyS {
          range: Self::eval_range(file_coordinate, function.range),
        }));
      }
      (
        &*self.scout_arena.alloc(IBodyS::ExternBody(ExternBodyS {})),
        VariableUses::<'s>::empty(),
        explicit_params_s,
        Vec::<&'s GenericParameterS<'s>>::new(),
      )
    } else if has_builtin_attr {
      let generator_name = function
        .header
        .attributes
        .iter()
        .find_map(|attr| match attr {
          IAttributeP::BuiltinAttribute(builtin_attr) => Some(self.scout_arena.intern_str(builtin_attr.generator_name.str().as_str())),
          _ => None,
        })
        .unwrap_or_else(|| panic!("POSTPARSER_SCOUT_FUNCTION_BUILTIN_ATTR_NOT_FOUND"));
      (
        &*self.scout_arena.alloc(IBodyS::GeneratedBody(GeneratedBodyS { generator_id: generator_name })),
        VariableUses::<'s>::empty(),
        explicit_params_s,
        Vec::<&'s GenericParameterS<'s>>::new(),
      )
    } else {
      let body = function
        .body
        .as_ref()
        .unwrap_or_else(|| panic!("POSTPARSER_SCOUT_FUNCTION_WITHOUT_BODY"));
      if body.maybe_pure.is_some() {
        panic!("POSTPARSER_SCOUT_PURE_BLOCKS_NOT_YET_IMPLEMENTED");
      }
      if body.maybe_default_region.is_some() {
        panic!("POSTPARSER_SCOUT_BLOCK_DEFAULT_REGION_NOT_YET_IMPLEMENTED");
      }
      let parent_stack_frame = match &maybe_parent {
        IFunctionParent::ParentFunction { parent_stack_frame } => Some(parent_stack_frame.clone()),
        _ => None,
      };
      let (body_s, variable_uses, magic_param_names): (&'s BodySE<'s>, VariableUses<'s>, Vec<IVarNameS<'s>>) = self.scout_body(
        function_environment,
        parent_stack_frame,
        &mut lidb,
        default_region_rune,
        body,
        maybe_capture_declarations
          .clone()
          .unwrap_or_else(|| panic!("POSTPARSER_SCOUT_FUNCTION_CAPTURE_DECLARATIONS_EXPECTED")),
      )?;
      if !is_parent_function && !magic_param_names.is_empty() {
        panic!("POSTPARSER_SCOUT_FUNCTION_MAGIC_PARAMS_NOT_YET_IMPLEMENTED");
      }
      if !is_parent_function && !body_s.closured_names.is_empty() {
        panic!(
          "POSTPARSER_SCOUT_FUNCTION_BODY_CLOSURED_NAMES_NOT_EMPTY_NOT_YET_IMPLEMENTED: {:?}",
          body_s.closured_names
        );
      }
      if is_parent_function && !magic_param_names.is_empty() && !explicit_params_s.is_empty() {
        return Err(ICompileErrorS::RangedInternalErrorS(RangedInternalErrorS {
          range: Self::eval_range(file_coordinate, function.range),
          message: "Cant have a lambda with _ and params".to_string(),
        }));
      }
      let mut total_params_s: Vec<ParameterS<'s>> = Vec::new();
      let mut extra_generic_params_from_body = Vec::<&'s GenericParameterS<'s>>::new();
      if is_parent_function {
        let IFunctionParent::ParentFunction { parent_stack_frame } = &maybe_parent else {
          panic!("POSTPARSER_SCOUT_FUNCTION_EXPECTED_PARENT_FUNCTION");
        };
        let closure_struct_kind_rune = self.scout_arena.intern_rune(IRuneValS::ImplicitRune(ImplicitRuneValS::new(lidb.child().borrow_val())));
        let closure_struct_region_rune = self.scout_arena.intern_rune(IRuneValS::ImplicitRegionRune(ImplicitRegionRuneValS { original_rune: closure_struct_kind_rune }));
        let closure_struct_coord_rune = self.scout_arena.intern_rune(IRuneValS::ImplicitRune(ImplicitRuneValS::new(lidb.child().borrow_val())));
        let closure_param_s = self.create_closure_param(
          function.range,
          function_declaration_name_for_env.clone(),
          &mut lidb,
          &mut rules,
          &mut rune_to_explicit_type,
          parent_stack_frame,
          closure_struct_region_rune,
          closure_struct_kind_rune,
          closure_struct_coord_rune,
        );
        total_params_s.push(closure_param_s);
      }
      total_params_s.extend(explicit_params_s);
      if is_parent_function {
        let magic_params: Vec<ParameterS<'s>> =
          self.create_magic_parameters(&mut lidb, magic_param_names, &mut rune_to_explicit_type);
        // Lambdas identifying runes are determined by their magic params.
        // See: Lambdas Dont Need Explicit Identifying Runes (LDNEIR)
        extra_generic_params_from_body.extend(magic_params.iter().map(|magic_param| {
          let coord_rune = magic_param
            .pattern
            .coord_rune
            .as_ref()
            .unwrap_or_else(|| panic!("POSTPARSER_SCOUT_MAGIC_PARAM_WITHOUT_COORD_RUNE"))
            .clone();
          &*self.scout_arena.alloc(GenericParameterS {
            range: magic_param.pattern.range.clone(),
            rune: coord_rune,
            tyype: IGenericParameterTypeS::CoordGenericParameterType(CoordGenericParameterTypeS {
              coord_region: None,
              kind_mutable: true,
              region_mutable: false,
            }),
            default: None,
          })
        }));
        total_params_s.extend(magic_params);
      }
      (
        &*self.scout_arena.alloc(IBodyS::CodeBody(CodeBodyS { body: body_s })),
        variable_uses,
        total_params_s,
        extra_generic_params_from_body,
      )
    };
    // Per @PRIIROZ, parent ones go on the end.
    let mut generic_params: Vec<&'s GenericParameterS<'s>> = function_user_specified_generic_parameters_s;
    generic_params.extend(extra_generic_params_from_explicit_params_s);
    generic_params.extend(extra_generic_params_from_body);
    generic_params.extend(extra_generic_params_from_parent);
    generic_params = generic_params
      .into_iter()
      .filter(|generic_param| {
        !matches!(
          generic_param.tyype,
          IGenericParameterTypeS::RegionGenericParameterType(_)
        )
      })
      .collect();

    let unfiltered_rules_array: Vec<IRulexSR<'s>> = rules;
    let rules_array = match &maybe_parent {
      IFunctionParent::ParentCitizen(_) => unfiltered_rules_array
        .into_iter()
        .filter(|rule| !matches!(rule, IRulexSR::RuneParentEnvLookup(_)))
        .collect::<Vec<_>>(),
      _ => unfiltered_rules_array,
    };

    let unfiltered_attrs_p = function.header.attributes;
    let filtered_attrs: Vec<&IAttributeP<'p>> = match &maybe_parent {
      IFunctionParent::FunctionNoParent => unfiltered_attrs_p
        .iter()
        .filter(|a| !matches!(a, IAttributeP::AbstractAttribute(_)))
        .collect(),
      IFunctionParent::ParentCitizen(_) => unfiltered_attrs_p.iter().collect(),
      IFunctionParent::ParentFunction { .. } => unfiltered_attrs_p.iter().collect(),
    };
    let func_attrs_s: Vec<IFunctionAttributeS<'s>> = filtered_attrs
      .into_iter()
      .map(|attr| match attr {
        IAttributeP::ExportAttribute(_) => IFunctionAttributeS::Export(ExportS {
          package_coordinate: file_coordinate.package_coord,
        }),
        IAttributeP::ExternAttribute(_) => IFunctionAttributeS::Extern(ExternS {
          package_coord: file_coordinate.package_coord,
        }),
        IAttributeP::PureAttribute(_) => IFunctionAttributeS::Pure(PureS),
        IAttributeP::AdditiveAttribute(_) => IFunctionAttributeS::Additive(AdditiveS),
        IAttributeP::BuiltinAttribute(builtin_attr) => IFunctionAttributeS::Builtin(BuiltinS {
          generator_name: self.scout_arena.intern_str(builtin_attr.generator_name.str().as_str()),
        }),
        IAttributeP::AbstractAttribute(_) => panic!("AbstractAttribute should have been filtered"),
        other => panic!("POSTPARSER_SCOUT_FUNCTION_ATTRIBUTE_NOT_YET_IMPLEMENTED: {:?}", other),
      })
      .collect();

    let range_s = Self::eval_range(file_coordinate, function.range);
    let mut rune_to_predicted_type = Self::predict_rune_types(
      self.scout_arena,
      range_s.clone(),
      &user_specified_identifying_runes
        .iter()
        .map(|rune_usage| rune_usage.rune.clone())
        .collect::<Vec<_>>(),
      &mut rune_to_explicit_type,
      &rules_array,
    )?;
    rune_to_predicted_type.retain(|_, tyype| !matches!(tyype, ITemplataType::RegionTemplataType(_)));
    let rules_array: &'s [IRulexSR<'s>] = self.scout_arena.alloc_slice_from_vec(rules_array);
    self.check_identifiability(
      range_s,
      &generic_params
        .iter()
        .map(|generic_param| generic_param.rune.rune.clone())
        .collect::<Vec<_>>(),
      rules_array,
    )?;

    let param_types_vec: Vec<ITemplataType<'s>> = generic_params
        .iter()
        .map(|generic_param| generic_param.tyype.tyype())
        .collect();
    let tyype = TemplateTemplataType {
      param_types: self.scout_arena.alloc_slice_copy(&param_types_vec),
      return_type: self.scout_arena.alloc(ITemplataType::FunctionTemplataType(FunctionTemplataType {})),
    };
    let function_name_ref: &'s IFunctionDeclarationNameS<'s> = match function_declaration_name {
      INameS::FunctionDeclaration(r) => r,
      _ => panic!("POSTPARSER_FUNCTION_NAME_EXPECTED_FUNCTION_DECLARATION"),
    };
    Ok((
      &*self.scout_arena.alloc(
        FunctionS::new(
          Self::eval_range(file_coordinate, function.range),
          function_name_ref,
          self.scout_arena.alloc_slice_from_vec(func_attrs_s),
          self.scout_arena.alloc_slice_from_vec(generic_params),
          rune_to_predicted_type,
          tyype,
          self.scout_arena.alloc_slice_from_vec(total_params_s),
          maybe_ret_coord_rune,
          rules_array,
          body_s,
        )),
      variable_uses,
    ))
  }

fn create_closure_param(
  &self,
  range: RangeL,
  func_name: IFunctionDeclarationNameS<'s>,
  lidb: &mut LocationInDenizenBuilder,
  rule_builder: &mut Vec<IRulexSR<'s>>,
  rune_to_explicit_type: &mut Vec<(IRuneS<'s>, ITemplataType)>,
  parent_stack_frame: &StackFrame<'s>,
  _closure_struct_region_rune: IRuneS<'s>,
  closure_struct_kind_rune: IRuneS<'s>,
  closure_struct_coord_rune: IRuneS<'s>,
) -> ParameterS<'s> {
  let closure_param_pos = PostParser::eval_pos(parent_stack_frame.file, range.begin());
  let closure_param_range = RangeS::new(
    closure_param_pos.clone(),
    closure_param_pos.clone(),
  );
  let closure_param_name = match self.scout_arena.intern_name(INameValS::VarName(
    IVarNameValS::ClosureParamName(ClosureParamNameS {
      code_location: closure_param_range.begin.clone(),
    }),
  )) {
    INameS::VarName(r) => (*r).clone(),
    _ => panic!("POSTPARSER_INTERN_VAR_NAME_EXPECTED_VAR_NAME"),
  };
  rune_to_explicit_type.push((
    closure_struct_kind_rune.clone(),
    ITemplataType::KindTemplataType(KindTemplataType {}),
  ));
  let IFunctionDeclarationNameS::LambdaDeclarationName(lambda_name) = func_name else {
    panic!("POSTPARSER_SCOUT_CREATE_CLOSURE_PARAM_NON_LAMBDA_NAME");
  };
  let closure_struct_name =
    self.scout_arena.intern_name(INameValS::LambdaStructDeclaration(LambdaStructDeclarationNameS {
      lambda_name: lambda_name.clone(),
    }));
  let closure_struct_imprecise_name = match &closure_struct_name {
    INameS::LambdaStructDeclaration(r) => (*r).get_imprecise_name(self.scout_arena),
    _ => panic!("POSTPARSER_INTERN_LAMBDA_STRUCT_NAME_EXPECTED_LAMBDA_STRUCT"),
  };
  rule_builder.push(IRulexSR::Lookup(LookupSR {
    range: closure_param_range.clone(),
    rune: RuneUsage {
      range: closure_param_range.clone(),
      rune: closure_struct_kind_rune.clone(),
    },
    name: closure_struct_imprecise_name.clone(),
  }));
  rune_to_explicit_type.push((
    closure_struct_coord_rune.clone(),
    ITemplataType::CoordTemplataType(CoordTemplataType {}),
  ));
  rule_builder.push(IRulexSR::CoerceToCoord(CoerceToCoordSR {
    range: closure_param_range.clone(),
    coord_rune: RuneUsage {
      range: closure_param_range.clone(),
      rune: closure_struct_coord_rune.clone(),
    },
    kind_rune: RuneUsage {
      range: closure_param_range.clone(),
      rune: closure_struct_kind_rune.clone(),
    },
  }));
  let closure_param_type_rune = RuneUsage {
    range: closure_param_range.clone(),
    rune: self.scout_arena.intern_rune(IRuneValS::ImplicitRune(ImplicitRuneValS::new(lidb.child().borrow_val()))),
  };
  rule_builder.push(IRulexSR::Augment(AugmentSR {
    range: closure_param_range.clone(),
    result_rune: closure_param_type_rune.clone(),
    ownership: Some(OwnershipP::Borrow),
    inner_rune: RuneUsage {
      range: closure_param_range.clone(),
      rune: closure_struct_coord_rune,
    },
  }));
  let capture: CaptureS<'s> = CaptureS {
    name: closure_param_name,
    mutate: false,
  };
  let closure_pattern = AtomSP::<'s> {
    range: closure_param_range.clone(),
    name: Some(capture),
    coord_rune: Some(closure_param_type_rune),
    destructure: None,
  };
  return ParameterS::<'s> {
    range: closure_param_range.clone(),
    virtuality: None,
    pre_checked: false,
    pattern: closure_pattern,
  };
}

fn create_magic_parameters(
  &self,
  lidb: &mut LocationInDenizenBuilder,
  lambda_magic_param_names: Vec<IVarNameS<'s>>,
  rune_to_explicit_type: &mut Vec<(IRuneS<'s>, ITemplataType)>,
) -> Vec<ParameterS<'s>> {
  lambda_magic_param_names
    .into_iter()
    .map(|magic_param_name| {
      let code_location = match &magic_param_name {
        IVarNameS::MagicParamName(c) => c.clone(),
        _ => panic!("POSTPARSER_CREATE_MAGIC_PARAMS_EXPECTED_MAGIC_PARAM_NAME"),
      };
      let magic_param_range = RangeS::new(
        code_location.clone(),
        code_location.clone(),
      );
      let magic_param_rune = self.scout_arena.intern_rune(IRuneValS::MagicParamRune(MagicParamRuneValS::new(lidb.child().borrow_val())));
      rune_to_explicit_type.push((
        magic_param_rune.clone(),
        ITemplataType::CoordTemplataType(CoordTemplataType {}),
      ));
      ParameterS::new(
        magic_param_range.clone(),
        None,
        false,
        AtomSP {
          range: magic_param_range.clone(),
          name: Some(CaptureS {
            name: magic_param_name,
            mutate: false,
          }),
          coord_rune: Some(RuneUsage {
            range: magic_param_range,
            rune: magic_param_rune,
          }),
          destructure: None,
        },
      )
    })
    .collect()
}

  #[allow(dead_code)]
  pub(crate) fn scout_lambda(
    &self,
    parent_stack_frame: StackFrame<'s>,
    function: &FunctionP<'p>,
  ) -> Result<(&'s FunctionS<'s>, VariableUses<'s>), ICompileErrorS<'s>>
  {
    let file_coordinate = parent_stack_frame.file;
    self.scout_function(
      file_coordinate,
      function,
      IFunctionParent::ParentFunction { parent_stack_frame },
    )
  }

  fn scout_body(
    &self,
    function_env: FunctionEnvironmentS<'s>,
    parent_stack_frame: Option<StackFrame<'s>>,
    lidb: &mut LocationInDenizenBuilder,
    context_region: IRuneS<'s>,
    body0: &BlockPE<'p>,
    initial_declarations: VariableDeclarations<'s>,
  ) -> Result<
    (
      &'s BodySE<'s>,
      VariableUses<'s>,
      Vec<IVarNameS<'s>>,
    ),
    ICompileErrorS<'s>,
  > {
    let function_body_env: FunctionEnvironmentS<'s> = function_env.child();
    let body_range_s = PostParser::eval_range(function_body_env.file, body0.range);
    let mut new_block_lidb = lidb.child();
    let (block1, self_uses, child_uses): (&'s BlockSE<'s>, VariableUses<'s>, VariableUses<'s>) = self.new_block(
      function_body_env.clone(),
      parent_stack_frame,
      &mut new_block_lidb,
      body_range_s,
      context_region,
      initial_declarations,
      |stack_frame1, scout_contents_lidb| {
        let (stack_frame2, inner_expr, inner_self_uses, inner_child_uses) =
          self.scout_expression_and_coerce(
            stack_frame1,
            scout_contents_lidb,
            body0.inner,
            LoadAsP::Use,
          )?;
        Ok((
          stack_frame2,
          inner_expr,
          inner_self_uses,
          inner_child_uses,
        ))
      },
    )?;

    let magic_param_names: Vec<IVarNameS<'s>> = self_uses
      .uses
      .iter()
      .filter_map(|use_| match &use_.name {
        IVarNameS::MagicParamName(code_location) => {
          Some(
            match self.scout_arena.intern_name(INameValS::VarName(IVarNameValS::MagicParamName(
              code_location.clone(),
            ))) {
              INameS::VarName(r) => (*r).clone(),
              _ => panic!("POSTPARSER_INTERN_MAGIC_PARAM_EXPECTED_VAR_NAME"),
            },
          )
        }
        _ => None,
      })
      .collect();
    let magic_param_vars: Vec<VariableDeclarationS<'s>> = magic_param_names
      .iter()
      .map(|magic_param_name| VariableDeclarationS {
        name: magic_param_name.clone(),
      })
      .collect();
    let magic_param_locals: Vec<LocalS<'s>> = magic_param_vars
      .iter()
      .map(|declared| LocalS {
        var_name: declared.name.clone(),
        self_borrowed: self_uses.is_borrowed(&declared.name),
        self_moved: self_uses.is_moved(&declared.name),
        self_mutated: self_uses.is_mutated(&declared.name),
        child_borrowed: child_uses.is_borrowed(&declared.name),
        child_moved: child_uses.is_moved(&declared.name),
        child_mutated: child_uses.is_mutated(&declared.name),
      })
      .collect();
    let mut combined_locals: Vec<_> = block1.locals.to_vec();
    combined_locals.extend(magic_param_locals);
    let block1 = &*self.scout_arena.alloc(BlockSE {
      range: block1.range.clone(),
      locals: self.scout_arena.alloc_slice_from_vec(combined_locals),
      expr: block1.expr,
    });
    // V: tell me about the above change?
    // VA: This is a faithful translation of Scala's `BlockSE(bodyRangeS, block1.locals ++ magicParamLocals, block1.expr)`.
    // VA: It re-allocates BlockSE with combined locals (original + magic params) into the arena. Not novel logic.
    // VA: One minor divergence: Rust uses block1.range (already computed) while Scala uses bodyRangeS
    // VA: (a fresh evalRange(body0.range)). They likely resolve to the same value but the source differs.
    let all_uses = self_uses.then_merge(&child_uses);
    let uses_of_parent_variables = all_uses
      .uses
      .iter()
      .filter(|use_| {
        if block1.locals.iter().any(|local| local.var_name == use_.name) {
          false
        } else {
          !matches!(use_.name, IVarNameS::MagicParamName(_))
        }
      })
      .cloned()
      .collect::<Vec<_>>();
    let closured_names: Vec<IVarNameS<'s>> = uses_of_parent_variables
      .iter()
      .map(|use_| use_.name.clone())
      .collect();
    let body_s = &*self.scout_arena.alloc(BodySE {
      range: PostParser::eval_range(function_body_env.file, body0.range),
      closured_names: self.scout_arena.alloc_slice_from_vec(closured_names),
      block: block1,
    });
    Ok((body_s, VariableUses { uses: uses_of_parent_variables }, magic_param_names))
  }

  pub(crate) fn scout_interface_member(
    &self,
    parent_interface: ParentCitizen<'s>,
    function_p: &FunctionP<'p>,
  ) -> Result<&'s FunctionS<'s>, ICompileErrorS<'s>>
  {
    let file = parent_interface.citizen_env.file();
    let (function_s, variable_uses) = self.scout_function(file, function_p, IFunctionParent::ParentCitizen(parent_interface))?;
    assert!(variable_uses.uses.is_empty());
    Ok(function_s)
  }

}

