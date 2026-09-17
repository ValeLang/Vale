// AFTERM: instead of get_astrouts, lets make a .build() method that consumes self and returns
// the compiled data. that will nicely destroy the compilation struct which is holding a bunch of
// other things hostage via reference.
// AFTERM: rename Astrouts

use crate::compile_options::GlobalOptions;
use crate::higher_typing::ast::{
    ExportAsA, FunctionA, ImplA, InterfaceA, ProgramA, StructA,
};
use crate::higher_typing::astronomer_error_reporter::{
    CouldntFindTypeA, ICompileErrorA, ILookupFailedErrorA, TooManyMatchingTypesA,
};
use crate::interner::StrI;
use crate::scout_arena::ScoutArena;
use crate::keywords::Keywords;
use crate::lexing::ast::RangeL;
use crate::lexing::errors::FailedParse;
use crate::parsing::ast::FileP;
use crate::postparsing::ast::{
    ExportAsS, FunctionS, IFunctionAttributeS, ImplS, ImportS, InterfaceS, ParameterS, ProgramS,
    StructS, UserFunctionS,
};
use crate::postparsing::itemplatatype::{
    CoordTemplataType, ITemplataType, IntegerTemplataType, KindTemplataType,
    MutabilityTemplataType, TemplateTemplataType, VariabilityTemplataType,
};
use crate::postparsing::names::{IImpreciseNameS, IImplDeclarationNameS, INameS, IRuneS, IStructDeclarationNameS};
use crate::postparsing::rune_type_solver::{
    IRuneTypeSolverEnv, IRuneTypeSolverLookupResult, IRuneTypingLookupFailedError,
};
use crate::postparsing::rules::rules::{IRulexSR, RuneUsage};
use crate::postparsing::post_parser::ICompileErrorS;
use crate::postparsing::ScoutCompilation;
use crate::utils::code_hierarchy::FileCoordinateMap;
use crate::utils::code_hierarchy::{IPackageResolver, PackageCoordinate, PackageCoordinateMap};
use crate::utils::range::RangeS;
use crate::utils::range::CodeLocationS;
use indexmap::IndexMap;
use std::collections::HashMap;
use crate::postparsing::rune_type_solver::PrimitiveRuneTypeSolverLookupResult;
use crate::postparsing::rules::rules::{MaybeCoercingLookupSR, MaybeCoercingCallSR, LookupSR, CallSR, CoerceToCoordSR};
use crate::postparsing::names::{IRuneValS, ImplicitCoercionKindRuneValS};
use crate::postparsing::names::ImplicitCoercionTemplateRuneValS;
use crate::postparsing::rune_type_solver::CitizenRuneTypeSolverLookupResult;
use crate::postparsing::rune_type_solver::TemplataLookupResult;
use crate::postparsing::rune_type_solver::{RuneTypingTooManyMatchingTypes, RuneTypingCouldntFindType};
use crate::postparsing::rune_type_solver::RuneTypeSolver;
use crate::parse_arena::ParseArena;
use crate::higher_typing::astronomer_error_reporter::CouldntSolveRulesA;
use std::any::Any;
use std::collections::HashSet;
use std::iter::once;

pub struct Astrouts<'s> {
  code_location_to_maybe_type: HashMap<CodeLocationS<'s>, Option<ITemplataType<'s>>>,
  code_location_to_struct: HashMap<CodeLocationS<'s>, &'s StructA<'s>>,
  code_location_to_interface: HashMap<CodeLocationS<'s>, &'s InterfaceA<'s>>,
}

pub struct EnvironmentA<'s> {
  maybe_name: Option<&'s INameS<'s>>,
  maybe_parent_env: Option<&'s EnvironmentA<'s>>,
  code_map: &'s PackageCoordinateMap<'s, ProgramS<'s>>,
  rune_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>>,
}

impl<'s> EnvironmentA<'s> {

  pub fn structs_s(&self) -> Vec<&'s StructS<'s>> {
    self.code_map.package_coord_to_contents.values().flat_map(|p| p.structs.iter().copied()).collect()
  }

  pub fn interfaces_s(&self) -> Vec<&'s InterfaceS<'s>> {
    self.code_map.package_coord_to_contents.values().flat_map(|p| p.interfaces.iter().copied()).collect()
  }

  pub fn impls_s(&self) -> Vec<&'s ImplS<'s>> {
    self.code_map.package_coord_to_contents.values().flat_map(|p| p.impls.iter().copied()).collect()
  }

  pub fn functions_s(&self) -> Vec<&'s FunctionS<'s>> {
    self.code_map.package_coord_to_contents.values().flat_map(|p| p.implemented_functions.iter().copied()).collect()
  }

  pub fn exports_s(&self) -> Vec<&'s ExportAsS<'s>> {
    self.code_map.package_coord_to_contents.values().flat_map(|p| p.exports.iter().copied()).collect()
  }

  pub fn imports_s(&self) -> Vec<&'s ImportS<'s>> {
    self.code_map.package_coord_to_contents.values().flat_map(|p| p.imports.iter().copied()).collect()
  }

fn add_runes(&self, new_rune_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>>) -> EnvironmentA<'s> {
  let mut merged = self.rune_to_type.clone();
  merged.extend(new_rune_to_type);
  EnvironmentA {
    maybe_name: self.maybe_name.clone(),
    maybe_parent_env: self.maybe_parent_env,
    code_map: self.code_map,
    rune_to_type: merged,
  }
}

}

pub fn explicify_lookups<'s: 's, E: IRuneTypeSolverEnv<'s>>(env: &E, scout_arena: &ScoutArena<'s>, rune_a_to_type: &mut IndexMap<IRuneS<'s>, ITemplataType<'s>>, rule_builder: &mut Vec<IRulexSR<'s>>, all_rules_with_implicitly_coercing_lookups_s: Vec<IRulexSR<'s>>) -> Result<(), IRuneTypingLookupFailedError<'s>> {
  // Only two rules' results can be coerced: LookupSR and CallSR.
  // Let's look for those and rewrite them to put an explicit coercion in there.
  for rule in all_rules_with_implicitly_coercing_lookups_s {
    match rule {
      IRulexSR::MaybeCoercingCall(MaybeCoercingCallSR { range, result_rune, template_rune, args }) => {
        let expected_type = rune_a_to_type.get(&result_rune.rune).expect("vassertSome").clone();
        let actual_type = match rune_a_to_type.get(&template_rune.rune).expect("vassertSome") {
          ITemplataType::TemplateTemplataType(ttt) => (*ttt.return_type).clone(),
          _ => panic!("vwat"),
        };
        if actual_type == expected_type {
          rule_builder.push(IRulexSR::Call(CallSR { range, result_rune, template_rune, args }));
        } else {
          match (&actual_type, &expected_type) {
            (ITemplataType::KindTemplataType(_), ITemplataType::CoordTemplataType(_)) => {
              let kind_rune_s = scout_arena.intern_rune(IRuneValS::ImplicitCoercionKindRune(ImplicitCoercionKindRuneValS {
                range: range.clone(),
                original_coord_rune: result_rune.rune.clone(),
              }));
              let kind_rune = RuneUsage { range: range.clone(), rune: kind_rune_s.clone() };
              rune_a_to_type.insert(kind_rune_s, ITemplataType::KindTemplataType(KindTemplataType {}));
              rule_builder.push(IRulexSR::Call(CallSR { range: range.clone(), result_rune: kind_rune.clone(), template_rune, args }));
              rule_builder.push(IRulexSR::CoerceToCoord(CoerceToCoordSR { range, coord_rune: result_rune, kind_rune }));
            }
            _ => panic!("vimpl"),
          }
        }
      }
      IRulexSR::MaybeCoercingLookup(MaybeCoercingLookupSR { range, rune: result_rune, name }) => {
        let desired_type = rune_a_to_type.get(&result_rune.rune).expect("vassertSome").clone();
        let actual_lookup_result = env.lookup(range.clone(), name.clone())?;

        match actual_lookup_result {
          IRuneTypeSolverLookupResult::Primitive(PrimitiveRuneTypeSolverLookupResult { tyype: _ }) => {
            match &desired_type {
              ITemplataType::CoordTemplataType(_) => {
                coerce_kind_lookup_to_coord(scout_arena, rune_a_to_type, rule_builder, range, result_rune, &name);
              }
              ITemplataType::KindTemplataType(_) => {
                rule_builder.push(IRulexSR::Lookup(LookupSR { range, rune: result_rune, name }));
              }
              ITemplataType::TemplateTemplataType(ttt) => {
                assert!(!ttt.param_types.is_empty());
                rule_builder.push(IRulexSR::Lookup(LookupSR { range, rune: result_rune, name }));
              }
              _ => panic!("FoundPrimitiveDidntMatchExpectedType not yet migrated as IRuneTypingLookupFailedError variant")
            }
          }
          IRuneTypeSolverLookupResult::Citizen(citizen) => {
            let citizen_template_type = match citizen.tyype {
              ITemplataType::TemplateTemplataType(ttt) => ttt,
              _ => panic!("CitizenRuneTypeSolverLookupResult tyype should be TemplateTemplataType"),
            };
            match &desired_type {
              ITemplataType::KindTemplataType(_) => {
                coerce_kind_template_lookup_to_kind(scout_arena, rune_a_to_type, rule_builder, range, result_rune, &name, citizen_template_type.clone());
              }
              ITemplataType::CoordTemplataType(_) => {
                coerce_kind_template_lookup_to_coord(scout_arena, rune_a_to_type, rule_builder, range, result_rune, &name, citizen_template_type.clone());
              }
              ITemplataType::TemplateTemplataType(ttt) => {
                assert!(!ttt.param_types.is_empty());
                rule_builder.push(IRulexSR::Lookup(LookupSR { range, rune: result_rune, name }));
              }
              _ => panic!("FoundTemplataDidntMatchExpectedTypeA not yet migrated as IRuneTypingLookupFailedError variant")
            }
          }
          IRuneTypeSolverLookupResult::Templata(t) => {
            let actual_type = t.templata;
            match (&actual_type, &desired_type) {
              (x, y) if x == y => {
                rule_builder.push(IRulexSR::Lookup(LookupSR { range, rune: result_rune, name }));
              }
              (ITemplataType::KindTemplataType(_), ITemplataType::CoordTemplataType(_)) => {
                coerce_kind_lookup_to_coord(scout_arena, rune_a_to_type, rule_builder, range, result_rune, &name);
              }
              (ITemplataType::TemplateTemplataType(ttt), ITemplataType::KindTemplataType(_))
                  if matches!(ttt.return_type, ITemplataType::KindTemplataType(_)) => {
                coerce_kind_template_lookup_to_kind(scout_arena, rune_a_to_type, rule_builder, range, result_rune, &name, ttt.clone());
              }
              (ITemplataType::TemplateTemplataType(ttt), ITemplataType::CoordTemplataType(_))
                  if matches!(ttt.return_type, ITemplataType::KindTemplataType(_)) => {
                coerce_kind_template_lookup_to_coord(scout_arena, rune_a_to_type, rule_builder, range, result_rune, &name, ttt.clone());
              }
              _ => panic!("explicify_lookups TemplataLookupResult: unexpected coercion from {:?} to {:?}", actual_type, desired_type),
            }
          }
        }
      }
      rule => {
        rule_builder.push(rule);
      }
    }
  }
  Ok(())
}

fn coerce_kind_lookup_to_coord<'s>(scout_arena: &ScoutArena<'s>, rune_a_to_type: &mut IndexMap<IRuneS<'s>, ITemplataType<'s>>, rule_builder: &mut Vec<IRulexSR<'s>>, range: RangeS<'s>, result_rune: RuneUsage<'s>, name: &IImpreciseNameS<'s>) {
  let kind_rune_s = scout_arena.intern_rune(IRuneValS::ImplicitCoercionKindRune(ImplicitCoercionKindRuneValS {
    range: range.clone(),
    original_coord_rune: result_rune.rune.clone(),
  }));
  let kind_rune = RuneUsage { range: range.clone(), rune: kind_rune_s.clone() };
  rune_a_to_type.insert(kind_rune_s, ITemplataType::KindTemplataType(KindTemplataType {}));
  rule_builder.push(IRulexSR::Lookup(LookupSR { range: range.clone(), rune: kind_rune.clone(), name: name.clone() }));
  rule_builder.push(IRulexSR::CoerceToCoord(CoerceToCoordSR { range, coord_rune: result_rune, kind_rune }));
}

fn coerce_kind_template_lookup_to_kind<'s>(scout_arena: &ScoutArena<'s>, rune_a_to_type: &mut IndexMap<IRuneS<'s>, ITemplataType<'s>>, rule_builder: &mut Vec<IRulexSR<'s>>, range: RangeS<'s>, result_rune: RuneUsage<'s>, name: &IImpreciseNameS<'s>, actual_template_type: TemplateTemplataType<'s>) {
  let template_rune_s = scout_arena.intern_rune(IRuneValS::ImplicitCoercionTemplateRune(ImplicitCoercionTemplateRuneValS {
    range: range.clone(),
    original_kind_rune: result_rune.rune.clone(),
  }));
  let template_rune = RuneUsage { range: range.clone(), rune: template_rune_s.clone() };
  rune_a_to_type.insert(template_rune_s, ITemplataType::TemplateTemplataType(actual_template_type));
  rule_builder.push(IRulexSR::Lookup(LookupSR { range: range.clone(), rune: template_rune.clone(), name: name.clone() }));
  rule_builder.push(IRulexSR::Call(CallSR { range, result_rune, template_rune, args: &[] }));
}

fn coerce_kind_template_lookup_to_coord<'s>(scout_arena: &ScoutArena<'s>, rune_a_to_type: &mut IndexMap<IRuneS<'s>, ITemplataType<'s>>, rule_builder: &mut Vec<IRulexSR<'s>>, range: RangeS<'s>, result_rune: RuneUsage<'s>, name: &IImpreciseNameS<'s>, ttt: TemplateTemplataType<'s>) {

  let template_rune_s = scout_arena.intern_rune(IRuneValS::ImplicitCoercionTemplateRune(ImplicitCoercionTemplateRuneValS {
    range: range.clone(),
    original_kind_rune: result_rune.rune.clone(),
  }));
  let template_rune = RuneUsage { range: range.clone(), rune: template_rune_s.clone() };

  let kind_rune_s = scout_arena.intern_rune(IRuneValS::ImplicitCoercionKindRune(ImplicitCoercionKindRuneValS {
    range: range.clone(),
    original_coord_rune: result_rune.rune.clone(),
  }));
  let kind_rune = RuneUsage { range: range.clone(), rune: kind_rune_s.clone() };

  rune_a_to_type.insert(template_rune_s, ITemplataType::TemplateTemplataType(ttt));
  rune_a_to_type.insert(kind_rune_s, ITemplataType::KindTemplataType(KindTemplataType {}));
  rule_builder.push(IRulexSR::Lookup(LookupSR { range: range.clone(), rune: template_rune.clone(), name: name.clone() }));
  rule_builder.push(IRulexSR::Call(CallSR { range: range.clone(), result_rune: kind_rune.clone(), template_rune: template_rune.clone(), args: &[] }));
  rule_builder.push(IRulexSR::CoerceToCoord(CoerceToCoordSR { range, coord_rune: result_rune, kind_rune }));
}

pub struct HigherTypingPass<'s, 'ctx> {
  global_options: GlobalOptions,
  scout_arena: &'ctx ScoutArena<'s>,
  keywords: &'ctx Keywords<'s>,
  primitives: HashMap<StrI<'s>, ITemplataType<'s>>,
}

impl<'s, 'ctx> HigherTypingPass<'s, 'ctx> {
  pub fn new(
    global_options: GlobalOptions,
    scout_arena: &'ctx ScoutArena<'s>,
    keywords: &'ctx Keywords<'s>,
  ) -> Self {
    let mut primitives = HashMap::new();
    primitives.insert(keywords.int, ITemplataType::KindTemplataType(KindTemplataType {}));
    primitives.insert(keywords.i64, ITemplataType::KindTemplataType(KindTemplataType {}));
    primitives.insert(keywords.str, ITemplataType::KindTemplataType(KindTemplataType {}));
    primitives.insert(keywords.bool, ITemplataType::KindTemplataType(KindTemplataType {}));
    primitives.insert(keywords.float, ITemplataType::KindTemplataType(KindTemplataType {}));
    primitives.insert(keywords.void, ITemplataType::KindTemplataType(KindTemplataType {}));
    primitives.insert(keywords.__never, ITemplataType::KindTemplataType(KindTemplataType {}));
    primitives.insert(keywords.array, ITemplataType::TemplateTemplataType(TemplateTemplataType {
      param_types: scout_arena.alloc_slice_copy(&[
        ITemplataType::MutabilityTemplataType(MutabilityTemplataType {}),
        ITemplataType::CoordTemplataType(CoordTemplataType {}),
      ]),
      return_type: scout_arena.alloc(ITemplataType::KindTemplataType(KindTemplataType {})),
    }));
    primitives.insert(keywords.static_array, ITemplataType::TemplateTemplataType(TemplateTemplataType {
      param_types: scout_arena.alloc_slice_copy(&[
        ITemplataType::IntegerTemplataType(IntegerTemplataType {}),
        ITemplataType::MutabilityTemplataType(MutabilityTemplataType {}),
        ITemplataType::VariabilityTemplataType(VariabilityTemplataType {}),
        ITemplataType::CoordTemplataType(CoordTemplataType {}),
      ]),
      return_type: scout_arena.alloc(ITemplataType::KindTemplataType(KindTemplataType {})),
    }));
    HigherTypingPass {
      global_options,
      scout_arena,
      keywords,
      primitives,
    }
  }

// Returns whether the imprecise name could be referring to the absolute name.
// See MINAAN for what we're doing here.
fn imprecise_name_matches_absolute_name(&self, needle_imprecise_name_s: &IImpreciseNameS, absolute_name: &INameS) -> bool {
  match (needle_imprecise_name_s, absolute_name) {
    (IImpreciseNameS::CodeName(code_name), INameS::TopLevelStructDeclaration(s)) => {
      s.name == code_name.name
    }
    (IImpreciseNameS::CodeName(code_name), INameS::TopLevelInterfaceDeclaration(i)) => {
      i.name == code_name.name
    }
    (IImpreciseNameS::RuneName(_), _) => false,
    _ => panic!("vimpl"),
  }
}

// See MINAAN for what we're doing here.
fn lookup_types(&self, astrouts: &Astrouts<'s>, env: &EnvironmentA<'s>, needle_imprecise_name_s: &IImpreciseNameS<'s>) -> Vec<IRuneTypeSolverLookupResult<'s>> {

  match needle_imprecise_name_s {
    IImpreciseNameS::CodeName(_) => {}
    IImpreciseNameS::RuneName(_) => {}
    _ => panic!("Unexpected imprecise name type in lookup_types"),
  }

  if let IImpreciseNameS::CodeName(code_name) = needle_imprecise_name_s {
    if let Some(x) = self.primitives.get(&code_name.name) {
      return vec![IRuneTypeSolverLookupResult::Primitive(PrimitiveRuneTypeSolverLookupResult { tyype: x.clone() })];
    }
  }

  if let IImpreciseNameS::RuneName(rune_name) = needle_imprecise_name_s {
    if let Some(tyype) = env.rune_to_type.get(&rune_name.rune) {
      return vec![IRuneTypeSolverLookupResult::Templata(TemplataLookupResult { templata: tyype.clone() })];
    }
  }

  let near_struct_types: Vec<_> = env.structs_s().iter()
    .filter(|s| self.imprecise_name_matches_absolute_name(needle_imprecise_name_s, &INameS::TopLevelStructDeclaration(s.name)))
    .map(|s| IRuneTypeSolverLookupResult::Citizen(CitizenRuneTypeSolverLookupResult { tyype: ITemplataType::TemplateTemplataType(s.tyype.clone()), generic_params: s.generic_params }))
    .collect();
  let near_interface_types: Vec<_> = env.interfaces_s().iter()
    .filter(|i| self.imprecise_name_matches_absolute_name(needle_imprecise_name_s, &INameS::TopLevelInterfaceDeclaration(i.name)))
    .map(|i| IRuneTypeSolverLookupResult::Citizen(CitizenRuneTypeSolverLookupResult { tyype: ITemplataType::TemplateTemplataType(i.tyype.clone()), generic_params: i.generic_params }))
    .collect();
  let result: Vec<IRuneTypeSolverLookupResult<'s>> = near_struct_types.into_iter().chain(near_interface_types).collect();

  if !result.is_empty() {
    result
  } else {
    match &env.maybe_parent_env {
      None => vec![],
      Some(parent_env) => self.lookup_types(astrouts, parent_env, needle_imprecise_name_s),
    }
  }
}

fn lookup_type(&self, astrouts: &Astrouts<'s>, env: &EnvironmentA<'s>, range: RangeS<'s>, name: &IImpreciseNameS<'s>) -> Result<IRuneTypeSolverLookupResult<'s>, ILookupFailedErrorA<'s>> {
  let results = self.lookup_types(astrouts, env, name);
  let mut distinct = Vec::new();
  for r in results {
    if !distinct.contains(&r) {
      distinct.push(r);
    }
  }
  match distinct.len() {
    0 => Err(ILookupFailedErrorA::CouldntFindType(CouldntFindTypeA { range, name: name.clone() })),
    1 => Ok(distinct.into_iter().next().unwrap()),
    _ => Err(ILookupFailedErrorA::TooManyMatchingTypes(TooManyMatchingTypesA { range, name: name.clone() })),
  }
}

fn translate_struct(&self, astrouts: &mut Astrouts<'s>, env: &EnvironmentA<'s>, struct_s: &StructS<'s>) -> Result<&'s StructA<'s>, ICompileErrorA<'s>> {
  let StructS {
    range: range_s,
    name: name_s,
    attributes: attributes_s,
    weakable,
    generic_params: generic_parameters_s,
    mutability_rune: mutability_rune_s,
    maybe_predicted_mutability,
    tyype,
    header_rune_to_explicit_type,
    header_predicted_rune_to_type: _,
    header_rules: header_rules_with_implicitly_coercing_lookups_s,
    members_rune_to_explicit_type,
    members_predicted_rune_to_type: _,
    member_rules: member_rules_with_implicitly_coercing_lookups_s,
    members,
    internal_methods: internal_methods_s,
  } = struct_s;

  // Check cache
  if let Some(value) = astrouts.code_location_to_struct.get(&range_s.begin) {
    return Ok(*value);
  }

  // Check for cycles
  match astrouts.code_location_to_maybe_type.get(&range_s.begin) {
    Some(Some(_)) => panic!("vwat: already evaluated struct type but missed cache"),
    Some(None) => {
      panic!("Cycle in determining struct type!");
    }
    None => {}
  }
  astrouts.code_location_to_maybe_type.insert(range_s.begin.clone(), None);

  let all_rules_with_implicitly_coercing_lookups_s: Vec<IRulexSR<'s>> =
    header_rules_with_implicitly_coercing_lookups_s.iter().chain(member_rules_with_implicitly_coercing_lookups_s.iter()).cloned().collect();
  let mut all_rune_to_explicit_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> = header_rune_to_explicit_type.iter().map(|(k, v)| (k.clone(), v.clone())).collect();
  all_rune_to_explicit_type.extend(members_rune_to_explicit_type.iter().map(|(k, v)| (k.clone(), v.clone())));

  let rune_a_to_type_with_implicitly_coercing_lookups_s =
    self.calculate_rune_types(
      astrouts,
      range_s.clone(),
      generic_parameters_s.iter().map(|gp| gp.rune.rune.clone()).collect(),
      all_rune_to_explicit_type,
      &[], // no params for structs
      &all_rules_with_implicitly_coercing_lookups_s,
      env,
    )?;

  let mut rune_a_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
    rune_a_to_type_with_implicitly_coercing_lookups_s;

  let rune_typing_env = HigherTypingRuneTypeSolverEnv {
    pass: self,
    astrouts,
    env,
    range_s: range_s.clone(),
  };

  let mut header_rules_builder: Vec<IRulexSR<'s>> = Vec::new();
  match explicify_lookups(
    &rune_typing_env,
    self.scout_arena,
    &mut rune_a_to_type,
    &mut header_rules_builder,
    header_rules_with_implicitly_coercing_lookups_s.to_vec(),
  ) {
    Ok(()) => {},
    Err(_e) => panic!("explicify_lookups failed for header rules"),
  }

  let mut member_rules_builder: Vec<IRulexSR<'s>> = Vec::new();
  match explicify_lookups(
    &rune_typing_env,
    self.scout_arena,
    &mut rune_a_to_type,
    &mut member_rules_builder,
    member_rules_with_implicitly_coercing_lookups_s.to_vec(),
  ) {
    Ok(()) => {},
    Err(_e) => panic!("explicify_lookups failed for member rules"),
  }

  // Split rune_a_to_type into header vs member portions
  let mut runes_in_header: HashSet<IRuneS<'s>> = HashSet::new();
  for gp in generic_parameters_s.iter() {
    runes_in_header.insert(gp.rune.rune.clone());
    if let Some(ref default) = gp.default {
      for rule in default.rules.iter() {
        for ru in rule.rune_usages() {
          runes_in_header.insert(ru.rune.clone());
        }
      }
    }
  }
  for rule in header_rules_builder.iter() {
    for ru in rule.rune_usages() {
      runes_in_header.insert(ru.rune.clone());
    }
  }

  let header_rune_a_to_type = self.scout_arena.alloc_index_map_from_iter(
    rune_a_to_type.iter().filter(|(k, _)| runes_in_header.contains(k)).map(|(k, v)| (k.clone(), v.clone())),
  );
  let members_rune_a_to_type = self.scout_arena.alloc_index_map_from_iter(
    rune_a_to_type.iter().filter(|(k, _)| !runes_in_header.contains(k)).map(|(k, v)| (k.clone(), v.clone())),
  );

  // Shouldnt fail because we got a complete solve earlier
  astrouts.code_location_to_maybe_type.insert(range_s.begin.clone(), Some(ITemplataType::TemplateTemplataType(tyype.clone())));

  for rule in header_rules_builder.iter() {
    if matches!(rule, IRulexSR::MaybeCoercingCall(_)) { panic!("vwat: MaybeCoercingCallSR in header rules after explicify"); }
  }
  for rule in member_rules_builder.iter() {
    if matches!(rule, IRulexSR::MaybeCoercingCall(_)) { panic!("vwat: MaybeCoercingCallSR in member rules after explicify"); }
  }
  let methods_env = env.add_runes(rune_a_to_type.clone());
  let internal_methods_a: Vec<&'s FunctionA<'s>> = internal_methods_s.iter()
    .map(|method| self.translate_function(astrouts, &methods_env, *method))
    .collect::<Result<Vec<_>, _>>()?;
  let struct_a = self.scout_arena.alloc(StructA::new(
    range_s.clone(),
    IStructDeclarationNameS::TopLevelStructDeclarationName((*name_s).clone()),
    attributes_s,
    *weakable,
    mutability_rune_s.clone(),
    *maybe_predicted_mutability,
    tyype.clone(),
    generic_parameters_s,
    header_rune_a_to_type,
    self.scout_arena.alloc_slice_from_vec(header_rules_builder),
    members_rune_a_to_type,
    self.scout_arena.alloc_slice_from_vec(member_rules_builder),
    members,
    self.scout_arena.alloc_slice_from_vec(internal_methods_a),
  ));
  astrouts.code_location_to_struct.insert(range_s.begin.clone(), struct_a);
  Ok(struct_a)
}

fn get_interface_type(&self, _astrouts: &mut Astrouts<'s>, _env: &EnvironmentA<'s>, _interface_s: &InterfaceS<'s>) -> ITemplataType<'s> {
  panic!("Unimplemented: get_interface_type");
}

fn translate_interface(&self, astrouts: &mut Astrouts<'s>, env: &EnvironmentA<'s>, interface_s: &InterfaceS<'s>) -> Result<&'s InterfaceA<'s>, ICompileErrorA<'s>> {
  let InterfaceS {
    range: range_s,
    name: name_s,
    attributes: attributes_s,
    weakable,
    generic_params: generic_parameters_s,
    rune_to_explicit_type,
    mutability_rune: mutability_rune_s,
    maybe_predicted_mutability,
    predicted_rune_to_type: _,
    tyype,
    rules: rules_with_implicitly_coercing_lookups_s,
    internal_methods: internal_methods_s,
  } = interface_s;

  // Check cache
  if let Some(value) = astrouts.code_location_to_interface.get(&range_s.begin) {
    return Ok(*value);
  }

  // Check for cycles
  match astrouts.code_location_to_maybe_type.get(&range_s.begin) {
    Some(Some(_)) => panic!("vwat: already evaluated interface type but missed cache"),
    Some(None) => {
      panic!("Cycle in determining interface type!");
    }
    None => {}
  }
  astrouts.code_location_to_maybe_type.insert(range_s.begin.clone(), None);

  let rune_a_to_type_with_implicitly_coercing_lookups =
    self.calculate_rune_types(
      astrouts,
      range_s.clone(),
      generic_parameters_s.iter().map(|gp| gp.rune.rune.clone()).collect(),
      rune_to_explicit_type.iter().map(|(k, v)| (k.clone(), v.clone())).collect(),
      &[],
      rules_with_implicitly_coercing_lookups_s,
      env,
    )?;

  // getOrDie because we should have gotten a complete solve
  astrouts.code_location_to_maybe_type.insert(range_s.begin.clone(), Some(ITemplataType::TemplateTemplataType(tyype.clone())));

  let methods_env = env.add_runes(rune_a_to_type_with_implicitly_coercing_lookups.clone());
  let internal_methods_a: Vec<&'s FunctionA<'s>> =
    internal_methods_s.iter().map(|method| {
      self.translate_function(astrouts, &methods_env, method)
    }).collect::<Result<Vec<_>, _>>()?;

  let rune_a_to_type_with_implicitly_coercing_lookups_s =
    self.calculate_rune_types(
      astrouts,
      range_s.clone(),
      generic_parameters_s.iter().map(|gp| gp.rune.rune.clone()).collect(),
      rune_to_explicit_type.iter().map(|(k, v)| (k.clone(), v.clone())).collect(),
      &[],
      rules_with_implicitly_coercing_lookups_s,
      env,
    )?;

  let mut rune_a_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
    rune_a_to_type_with_implicitly_coercing_lookups_s;
  // We've now calculated all the types of all the runes, but the LookupSR rules are still a bit loose...

  let rune_typing_env = HigherTypingRuneTypeSolverEnv {
    pass: self,
    astrouts,
    env,
    range_s: range_s.clone(),
  };

  let mut rule_builder: Vec<IRulexSR<'s>> = Vec::new();
  match explicify_lookups(
    &rune_typing_env,
    self.scout_arena,
    &mut rune_a_to_type,
    &mut rule_builder,
    rules_with_implicitly_coercing_lookups_s.to_vec(),
  ) {
    Ok(()) => {},
    Err(_e) => panic!("explicify_lookups failed for interface"),
  }

  let interface_a = self.scout_arena.alloc(InterfaceA::new(
    range_s.clone(),
    name_s,
    attributes_s,
    *weakable,
    mutability_rune_s.clone(),
    *maybe_predicted_mutability,
    tyype.clone(),
    generic_parameters_s,
    self.scout_arena.alloc_index_map_from_iter(rune_a_to_type.into_iter()),
    self.scout_arena.alloc_slice_from_vec(rule_builder),
    self.scout_arena.alloc_slice_from_vec(internal_methods_a),
  ));
  astrouts.code_location_to_interface.insert(range_s.begin.clone(), interface_a);
  Ok(interface_a)
}

fn translate_impl(&self, astrouts: &mut Astrouts<'s>, env: &EnvironmentA<'s>, impl_s: &ImplS<'s>) -> Result<&'s ImplA<'s>, ICompileErrorA<'s>> {
  let ImplS {
    range: range_s,
    name: name_s,
    user_specified_identifying_runes: identifying_runes_s,
    rules: rules_with_implicitly_coercing_lookups_s,
    rune_to_explicit_type,
    tyype,
    struct_kind_rune: struct_kind_rune_s,
    sub_citizen_imprecise_name,
    interface_kind_rune: interface_kind_rune_s,
    super_interface_imprecise_name,
  } = impl_s;

  // Scala creates runeTypingEnv here, but Rust can't because it borrows astrouts immutably
  // while calculate_rune_types needs &mut astrouts. Created below after mutable borrows end.

  let mut rune_to_explicit_type_with_kinds: IndexMap<IRuneS<'s>, ITemplataType<'s>> = rune_to_explicit_type.iter().map(|(k, v)| (k.clone(), v.clone())).collect();
  rune_to_explicit_type_with_kinds.insert(struct_kind_rune_s.rune.clone(), ITemplataType::KindTemplataType(KindTemplataType {}));
  rune_to_explicit_type_with_kinds.insert(interface_kind_rune_s.rune.clone(), ITemplataType::KindTemplataType(KindTemplataType {}));

  let rune_a_to_type_with_implicitly_coercing_lookups_s =
    self.calculate_rune_types(
      astrouts,
      range_s.clone(),
      identifying_runes_s.iter().map(|gp| gp.rune.rune.clone()).collect(),
      rune_to_explicit_type_with_kinds,
      &[], // Vector()
      rules_with_implicitly_coercing_lookups_s,
      env,
    )?;

  // getOrDie because we should have gotten a complete solve
  astrouts.code_location_to_maybe_type.insert(range_s.begin.clone(), Some(tyype.clone()));

  let mut rune_a_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
    rune_a_to_type_with_implicitly_coercing_lookups_s;
  // We've now calculated all the types of all the runes, but the LookupSR rules are still a bit
  // loose. We intentionally ignored the types of the things they're looking up, so we could know
  // what types we *expect* them to be, so we could coerce.
  // That coercion is good, but lets make it more explicit.

  let rune_typing_env = HigherTypingRuneTypeSolverEnv {
    pass: self,
    astrouts,
    env,
    range_s: range_s.clone(),
  };

  let mut rule_builder: Vec<IRulexSR<'s>> = Vec::new();
  match explicify_lookups(
    &rune_typing_env,
    self.scout_arena,
    &mut rune_a_to_type,
    &mut rule_builder,
    rules_with_implicitly_coercing_lookups_s.to_vec(),
  ) {
    Ok(()) => {},
    Err(_e) => panic!("explicify_lookups failed for impl"),
  }

  Ok(self.scout_arena.alloc(ImplA::new(
    range_s.clone(),
    IImplDeclarationNameS::ImplDeclarationName(name_s.clone()),
    identifying_runes_s,
    self.scout_arena.alloc_slice_from_vec(rule_builder),
    self.scout_arena.alloc_index_map_from_iter(rune_a_to_type.into_iter()),
    struct_kind_rune_s.clone(),
    sub_citizen_imprecise_name.clone(),
    interface_kind_rune_s.clone(),
    super_interface_imprecise_name.clone(),
  )))
}

fn translate_export(&self, astrouts: &mut Astrouts<'s>, env: &EnvironmentA<'s>, export_s: &ExportAsS<'s>) -> Result<&'s ExportAsA<'s>, ICompileErrorA<'s>> {
  let range_s = export_s.range.clone();
  let rules_with_implicitly_coercing_lookups_s = export_s.rules;
  let rune = export_s.rune.clone();
  let rune_a_to_type_with_implicitly_coercing_lookups_s =
    self.calculate_rune_types(
      astrouts,
      range_s.clone(),
      Vec::new(),
      once((rune.rune.clone(), ITemplataType::KindTemplataType(KindTemplataType {}))).collect(),
      &[],
      rules_with_implicitly_coercing_lookups_s,
      env,
    )?;
  let mut rune_a_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
    rune_a_to_type_with_implicitly_coercing_lookups_s;
  let mut rule_builder: Vec<IRulexSR<'s>> = Vec::new();
  let rune_typing_env = HigherTypingRuneTypeSolverEnv {
    pass: self,
    astrouts,
    env,
    range_s: range_s.clone(),
  };
  match explicify_lookups(
    &rune_typing_env,
    self.scout_arena,
    &mut rune_a_to_type,
    &mut rule_builder,
    rules_with_implicitly_coercing_lookups_s.to_vec(),
  ) {
    Ok(()) => {},
    Err(_e) => panic!("explicify_lookups failed"),
  }
  Ok(self.scout_arena.alloc(ExportAsA {
    range: range_s,
    exported_name: export_s.exported_name,
    rules: self.scout_arena.alloc_slice_from_vec(rule_builder),
    rune_to_type: self.scout_arena.alloc_index_map_from_iter(rune_a_to_type.into_iter()),
    type_rune: rune,
  }))
}

fn translate_function(&self, astrouts: &mut Astrouts<'s>, env: &EnvironmentA<'s>, function_s: &'s FunctionS<'s>) -> Result<&'s FunctionA<'s>, ICompileErrorA<'s>> {
  let range_s = function_s.range.clone();
  let name_s = function_s.name.clone();
  let attributes_s = function_s.attributes;
  let identifying_runes_s = function_s.generic_params;
  let rune_to_explicit_type = &function_s.rune_to_predicted_type;
  let tyype = &function_s.tyype;
  let params_s = function_s.params;
  let maybe_ret_coord_rune = &function_s.maybe_ret_coord_rune;
  let rules_with_implicitly_coercing_lookups_s = function_s.rules;
  let body_s = function_s.body;
  // Scala creates runeTypingEnv here, but Rust can't because it borrows astrouts immutably
  // while calculate_rune_types needs &mut astrouts. Created below after mutable borrows end.

  let rune_a_to_type_with_implicitly_coercing_lookups_s =
    self.calculate_rune_types(
      astrouts,
      range_s.clone(),
      identifying_runes_s.iter().map(|gp| gp.rune.rune.clone()).collect(),
      rune_to_explicit_type.iter().map(|(k, v)| (k.clone(), v.clone())).collect(),
      params_s,
      rules_with_implicitly_coercing_lookups_s,
      env,
    )?;

  let mut rune_a_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
    rune_a_to_type_with_implicitly_coercing_lookups_s;
  // We've now calculated all the types of all the runes, but the LookupSR rules are still a bit
  // loose. We intentionally ignored the types of the things they're looking up, so we could know
  // what types we *expect* them to be, so we could coerce.
  // That coercion is good, but lets make it more explicit.
  let mut rule_builder: Vec<IRulexSR<'s>> = Vec::new();
  let rune_typing_env = HigherTypingRuneTypeSolverEnv {
    pass: self,
    astrouts,
    env,
    range_s: range_s.clone(),
  };
  match explicify_lookups(
    &rune_typing_env,
    self.scout_arena,
    &mut rune_a_to_type,
    &mut rule_builder,
    rules_with_implicitly_coercing_lookups_s.to_vec(),
  ) {
    Ok(()) => {},
    Err(_e) => panic!("explicify_lookups failed"),
  }

  let mut attributes: Vec<IFunctionAttributeS<'s>> = attributes_s.to_vec();
  attributes.push(IFunctionAttributeS::UserFunction(UserFunctionS));

  Ok(self.scout_arena.alloc(FunctionA::new(
    range_s,
    name_s,
    self.scout_arena.alloc_slice_from_vec(attributes),
    tyype.clone(),
    identifying_runes_s,
    self.scout_arena.alloc_index_map_from_iter(rune_a_to_type.into_iter()),
    params_s,
    maybe_ret_coord_rune.clone(),
    self.scout_arena.alloc_slice_from_vec(rule_builder),
    *body_s,
  )))
}

fn calculate_rune_types(
  &self,
  astrouts: &mut Astrouts<'s>,
  range_s: RangeS<'s>,
  identifying_runes_s: Vec<IRuneS<'s>>,
  rune_to_explicit_type: IndexMap<IRuneS<'s>, ITemplataType<'s>>,
  params_s: &[ParameterS<'s>],
  rules_s: &[IRulexSR<'s>],
  env: &EnvironmentA<'s>,
) -> Result<IndexMap<IRuneS<'s>, ITemplataType<'s>>, ICompileErrorA<'s>> {
  let rune_typing_env = HigherTypingRuneTypeSolverEnv {
    pass: self,
    astrouts,
    env,
    range_s: range_s.clone(),
  };
  let mut rune_s_to_pre_known_type_a: IndexMap<IRuneS<'s>, ITemplataType<'s>> = rune_to_explicit_type;
  for param in params_s {
    if let Some(ref coord_rune) = param.pattern.coord_rune {
      rune_s_to_pre_known_type_a.insert(coord_rune.rune.clone(), ITemplataType::CoordTemplataType(CoordTemplataType {}));
    }
  }
  let rune_type_solver = RuneTypeSolver {
    scout_arena: self.scout_arena,
  };
  // Violation: RSMSCPX: Scala passes globalOptions.useOptimizedSolver as 2nd arg to solve; Rust's solve_rune_type omits it
  let rune_s_to_type = rune_type_solver.solve_rune_type(
    self.global_options.sanity_check,
    &rune_typing_env,
    vec![range_s.clone()],
    false,
    rules_s,
    &identifying_runes_s,
    true,
    rune_s_to_pre_known_type_a,
  );
  match rune_s_to_type {
    Ok(t) => Ok(t),
    Err(e) => Err(ICompileErrorA::CouldntSolveRules(CouldntSolveRulesA { range: range_s, error: e })),
  }
}

fn translate_program(&self, code_map: &'s PackageCoordinateMap<'s, ProgramS<'s>>, supplied_functions: Vec<&'s FunctionA<'s>>, supplied_interfaces: Vec<&'s InterfaceA<'s>>) -> Result<ProgramA<'s>, ICompileErrorA<'s>> {
  let env = EnvironmentA {
    maybe_name: None,
    maybe_parent_env: None,
    code_map,
    rune_to_type: IndexMap::new(),
  };

  // If something is absent from the map, we haven't started evaluating it yet
  // If there is a None in the map, we started evaluating it
  // If there is a Some in the map, we know the type
  // If we are asked to evaluate something but there is already a None in the map, then we are
  // caught in a cycle.
  let mut astrouts = Astrouts {
    code_location_to_maybe_type: HashMap::new(),
    code_location_to_struct: HashMap::new(),
    code_location_to_interface: HashMap::new(),
  };

  let structs_a: Vec<&'s StructA<'s>> = env.structs_s().into_iter().map(|s| self.translate_struct(&mut astrouts, &env, s)).collect::<Result<Vec<_>, _>>()?;

  let interfaces_a: Vec<&'s InterfaceA<'s>> = env.interfaces_s().into_iter().map(|i| self.translate_interface(&mut astrouts, &env, i)).collect::<Result<Vec<_>, _>>()?;

  let impls_a: Vec<&'s ImplA<'s>> = env.impls_s().into_iter().map(|im| self.translate_impl(&mut astrouts, &env, im)).collect::<Result<Vec<_>, _>>()?;

  let functions_a: Vec<&'s FunctionA<'s>> = env.functions_s().into_iter().map(|f| self.translate_function(&mut astrouts, &env, f)).collect::<Result<Vec<_>, _>>()?;

  let exports_a: Vec<&'s ExportAsA<'s>> = env.exports_s().into_iter().map(|e| self.translate_export(&mut astrouts, &env, e)).collect::<Result<Vec<_>, _>>()?;

  Ok(ProgramA {
    structs: self.scout_arena.alloc_slice_from_vec(structs_a),
    interfaces: self.scout_arena.alloc_slice_from_vec(supplied_interfaces.into_iter().chain(interfaces_a).collect()),
    impls: self.scout_arena.alloc_slice_from_vec(impls_a),
    functions: self.scout_arena.alloc_slice_from_vec(supplied_functions.into_iter().chain(functions_a).collect()),
    exports: self.scout_arena.alloc_slice_from_vec(exports_a),
  })
}

pub fn run_pass(
  &self,
  separate_programs_s: FileCoordinateMap<'s, ProgramS<'s>>,
) -> Result<PackageCoordinateMap<'s, ProgramA<'s>>, ICompileErrorA<'s>> {
  // Merge FileCoordinateMap into PackageCoordinateMap by flattening files per package
  let mut merged_program_s = PackageCoordinateMap::<ProgramS<'s>>::new();
  for (package_coord, file_coords) in &separate_programs_s.package_coord_to_file_coords {
    let programs_s: Vec<&ProgramS<'s>> = file_coords
      .iter()
      .map(|fc| separate_programs_s.file_coord_to_contents.get(fc).unwrap())
      .collect();
    // Flatten all files' contents into one ProgramS per package
    let structs: Vec<&'s StructS<'s>> = programs_s.iter().flat_map(|p| p.structs.iter().copied()).collect();
    let interfaces: Vec<&'s InterfaceS<'s>> = programs_s.iter().flat_map(|p| p.interfaces.iter().copied()).collect();
    let impls: Vec<&'s ImplS<'s>> = programs_s.iter().flat_map(|p| p.impls.iter().copied()).collect();
    let functions: Vec<&'s FunctionS<'s>> = programs_s.iter().flat_map(|p| p.implemented_functions.iter().copied()).collect();
    let exports: Vec<&'s ExportAsS<'s>> = programs_s.iter().flat_map(|p| p.exports.iter().copied()).collect();
    let imports: Vec<&'s ImportS<'s>> = programs_s.iter().flat_map(|p| p.imports.iter().copied()).collect();
    // Leak vecs into slices since ProgramS holds slices
    let merged = ProgramS {
      structs: structs.leak(),
      interfaces: interfaces.leak(),
      impls: impls.leak(),
      implemented_functions: functions.leak(),
      exports: exports.leak(),
      imports: imports.leak(),
    };
    merged_program_s.put(package_coord, merged);
  }

  let merged_program_s = self.scout_arena.alloc(merged_program_s);
  let supplied_functions: Vec<&'s FunctionA<'s>> = Vec::new();
  let supplied_interfaces: Vec<&'s InterfaceA<'s>> = Vec::new();
  let program_a =
    self.translate_program(merged_program_s, supplied_functions, supplied_interfaces)?;

  // Group results by package coordinate
  let ProgramA { structs: structs_a, interfaces: interfaces_a, impls: impls_a, functions: functions_a, exports: exports_a } = program_a;

  let mut package_to_structs_a: HashMap<&'s PackageCoordinate<'s>, Vec<&'s StructA<'s>>> = HashMap::new();
  for &s in structs_a {
    package_to_structs_a.entry(s.range.begin.file.package_coord).or_default().push(s);
  }

  let mut package_to_interfaces_a: HashMap<&'s PackageCoordinate<'s>, Vec<&'s InterfaceA<'s>>> = HashMap::new();
  for &i in interfaces_a {
    package_to_interfaces_a.entry(i.name.range.begin.file.package_coord).or_default().push(i);
  }

  let mut package_to_functions_a: HashMap<&'s PackageCoordinate<'s>, Vec<&'s FunctionA<'s>>> = HashMap::new();
  for &f in functions_a {
    package_to_functions_a.entry(f.name.package_coordinate()).or_default().push(f);
  }

  let mut package_to_impls_a: HashMap<&'s PackageCoordinate<'s>, Vec<&'s ImplA<'s>>> = HashMap::new();
  for &im in impls_a {
    package_to_impls_a.entry(im.name.package_coordinate()).or_default().push(im);
  }

  let mut package_to_exports_a: HashMap<&'s PackageCoordinate<'s>, Vec<&'s ExportAsA<'s>>> = HashMap::new();
  for &e in exports_a {
    package_to_exports_a.entry(e.range.begin.file.package_coord).or_default().push(e);
  }

  let mut all_packages: HashSet<&'s PackageCoordinate<'s>> = HashSet::new();
  all_packages.extend(package_to_structs_a.keys());
  all_packages.extend(package_to_interfaces_a.keys());
  all_packages.extend(package_to_functions_a.keys());
  all_packages.extend(package_to_impls_a.keys());
  all_packages.extend(package_to_exports_a.keys());

  let mut result = PackageCoordinateMap::<ProgramA<'s>>::new();
  for paackage in all_packages {
    let contents = ProgramA {
      structs: self.scout_arena.alloc_slice_from_vec(package_to_structs_a.remove(paackage).unwrap_or_default()),
      interfaces: self.scout_arena.alloc_slice_from_vec(package_to_interfaces_a.remove(paackage).unwrap_or_default()),
      impls: self.scout_arena.alloc_slice_from_vec(package_to_impls_a.remove(paackage).unwrap_or_default()),
      functions: self.scout_arena.alloc_slice_from_vec(package_to_functions_a.remove(paackage).unwrap_or_default()),
      exports: self.scout_arena.alloc_slice_from_vec(package_to_exports_a.remove(paackage).unwrap_or_default()),
    };
    result.put(paackage, contents);
  }
  Ok(result)
}

}

pub struct HigherTypingCompilation<'s, 'ctx, 'p> {
  global_options: GlobalOptions,
  pub scout_arena: &'ctx ScoutArena<'s>,
  pub keywords: &'ctx Keywords<'s>,
  scout_compilation: ScoutCompilation<'s, 'ctx, 'p>,
  astrouts_cache: Option<PackageCoordinateMap<'s, ProgramA<'s>>>,
}

impl<'s, 'ctx, 'p> HigherTypingCompilation<'s, 'ctx, 'p>
{
  
  pub fn new(
    scout_arena: &'ctx ScoutArena<'s>,
    keywords: &'ctx Keywords<'s>,
    parser_keywords: &'ctx Keywords<'p>,
    parse_arena: &'ctx ParseArena<'p>,
    packages_to_build: Vec<&'p PackageCoordinate<'p>>,
    package_to_contents_resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>>,
    global_options: GlobalOptions,
  ) -> Self {
    let scout_compilation = ScoutCompilation::new(
      scout_arena,
      keywords,
      parser_keywords,
      parse_arena,
      packages_to_build,
      package_to_contents_resolver,
      global_options.clone(),
    );

    HigherTypingCompilation {
      global_options,
      scout_arena,
      keywords,
      scout_compilation,
      astrouts_cache: None,
    }
  }

pub fn get_code_map(&mut self) -> Result<FileCoordinateMap<'p, String>, FailedParse<'p>> {
  self.scout_compilation.get_code_map()
}

pub fn get_parseds(&mut self) -> Result<FileCoordinateMap<'p, (FileP<'p>, Vec<RangeL>)>, FailedParse<'p>> {
  self.scout_compilation.get_parseds()
}

pub fn get_vpst_map(&mut self) -> Result<FileCoordinateMap<'p, String>, FailedParse<'p>> {
  self.scout_compilation.get_vpst_map()
}

pub fn get_scoutput(&mut self) -> Result<&FileCoordinateMap<'s, ProgramS<'s>>, ICompileErrorS<'s>> {
  self.scout_compilation.get_scoutput()
}

pub fn get_astrouts(&mut self) -> Result<&PackageCoordinateMap<'s, ProgramA<'s>>, ICompileErrorA<'s>> {
  if self.astrouts_cache.is_some() {
    return Ok(self.astrouts_cache.as_ref().unwrap());
  }
  let scoutput = self.scout_compilation.expect_scoutput().clone();
  let higher_typing_pass = HigherTypingPass::new(
    self.global_options.clone(),
    self.scout_arena,
    self.keywords,
  );
  let astrouts = higher_typing_pass.run_pass(scoutput)?;
  self.astrouts_cache = Some(astrouts);
  Ok(self.astrouts_cache.as_ref().unwrap())
}

pub fn expect_astrouts(&mut self) -> &PackageCoordinateMap<'s, ProgramA<'s>> {
  match self.get_astrouts() {
    Ok(x) => x,
    Err(_e) => {
      panic!("HigherTypingCompilation.expect_astrouts failed")
    }
  }
}
} // end impl HigherTypingCompilation

// Concrete IRuneTypeSolverEnv for the higher typing pass.
// All 6 Scala anonymous `new IRuneTypeSolverEnv` in this file close over (astrouts, env, rangeS)
// and delegate to lookupType. This struct captures those same fields.
struct HigherTypingRuneTypeSolverEnv<'s, 'ctx, 'env> {
  pass: &'env HigherTypingPass<'s, 'ctx>,
  astrouts: &'env Astrouts<'s>,
  env: &'env EnvironmentA<'s>,
  range_s: RangeS<'s>,
}

impl<'s, 'ctx, 'env> IRuneTypeSolverEnv<'s> for HigherTypingRuneTypeSolverEnv<'s, 'ctx, 'env> {
  fn lookup(
    &self,
    _range: RangeS<'s>,
    name: IImpreciseNameS<'s>,
  ) -> Result<IRuneTypeSolverLookupResult<'s>, IRuneTypingLookupFailedError<'s>> {
    self.pass.lookup_type(self.astrouts, self.env, self.range_s.clone(), &name)
      .map_err(|e| match e {
        ILookupFailedErrorA::CouldntFindType(c) => {
          IRuneTypingLookupFailedError::CouldntFindType(RuneTypingCouldntFindType {
            range: c.range,
            name: c.name,
          })
        }
        ILookupFailedErrorA::TooManyMatchingTypes(t) => {
          IRuneTypingLookupFailedError::TooManyMatchingTypes(RuneTypingTooManyMatchingTypes {
            range: t.range,
            name: t.name,
          })
        }
      })
  }
  
}

