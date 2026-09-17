use std::collections::{HashMap as StdHashMap, HashSet};
use indexmap::IndexMap;

use crate::typing::templata::templata::{FunctionTemplataT, ITemplataT, StructDefinitionTemplataT};
use crate::utils::arena_index_map::ArenaIndexMap;
use crate::utils::range::CodeLocationS;

use crate::postparsing::names::{ArbitraryNameS, ClosureParamImpreciseNameS, CodeNameS, IImpreciseNameS, IImpreciseNameValS, LambdaImpreciseNameS, LambdaStructImpreciseNameValS, PlaceholderImpreciseNameS, PrototypeNameS, RuneNameValS, SelfNameS, AnonymousSubstructTemplateImpreciseNameValS};
use crate::typing::names::names::{ICitizenTemplateNameT, IInterfaceTemplateNameT};
use crate::scout_arena::ScoutArena;
use crate::typing::env::function_environment_t::{
  BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT,
  BuildingFunctionEnvironmentWithClosuredsT, FunctionEnvironmentT, NodeEnvironmentT,
};
use crate::typing::env::i_env_entry::IEnvEntryT;
use crate::typing::names::names::{IdT, INameT, IInstantiationNameT, ITemplateNameT};
use crate::typing::typing_interner::TypingInterner;
use crate::typing::env::function_environment_t::lookup_with_imprecise_name_inner;
use crate::interner::StrI;
use crate::typing::macros::macros::FunctionBodyMacro;
use crate::typing::templata::templata::InterfaceDefinitionTemplataT;
use crate::typing::templata::templata::ImplDefinitionTemplataT;
use crate::postparsing::names::ImplImpreciseNameValS;
use crate::postparsing::names::ImplSubCitizenImpreciseNameValS;
use crate::postparsing::names::ImplSuperInterfaceImpreciseNameValS;
use crate::typing::types::types::KindT;
use std::hash::Hash;
use std::hash::Hasher;
use std::mem::discriminant;

/// Polyvalue (see @TFITCX) — derive Eq/Hash; never hand-roll `ptr::eq` on the outer `&self` (see @PVECFPZ).
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IEnvironmentT<'s, 't>
where 's: 't,
{
    Package(&'t PackageEnvironmentT<'s, 't>),
    Citizen(&'t CitizenEnvironmentT<'s, 't>),
    Function(&'t FunctionEnvironmentT<'s, 't>),
    Node(&'t NodeEnvironmentT<'s, 't>),
    BuildingWithClosureds(&'t BuildingFunctionEnvironmentWithClosuredsT<'s, 't>),
    BuildingWithClosuredsAndTemplateArgs(&'t BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't>),
    General(&'t GeneralEnvironmentT<'s, 't>),
    Export(&'t ExportEnvironmentT<'s, 't>),
    Extern(&'t ExternEnvironmentT<'s, 't>),
}

impl<'s, 't> IEnvironmentT<'s, 't> where 's: 't {
  pub fn to_string(&self) -> String {
    panic!("Unimplemented: to_string");
  }
  
  pub fn global_env(&self) -> &'t GlobalEnvironmentT<'s, 't> {
    match self {
      IEnvironmentT::Package(e) => e.global_env,
      IEnvironmentT::Citizen(e) => e.global_env,
      IEnvironmentT::Function(e) => e.global_env,
      IEnvironmentT::Node(e) => e.parent_function_env.global_env,
      IEnvironmentT::BuildingWithClosureds(e) => e.global_env,
      IEnvironmentT::BuildingWithClosuredsAndTemplateArgs(e) => e.global_env,
      IEnvironmentT::General(e) => e.global_env,
      IEnvironmentT::Export(e) => e.global_env,
      IEnvironmentT::Extern(e) => e.global_env,
    }
  }
  
  pub fn templatas(&self) -> &TemplatasStoreT<'s, 't> {
    panic!("Unimplemented: templatas");
  }
  
  pub fn lookup_with_imprecise_name_inner(
    &self,
    name_s: IImpreciseNameS<'s>,
    lookup_filter: HashSet<ILookupContext>,
    get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    match self {
      IEnvironmentT::Package(e) => e.lookup_with_imprecise_name_inner(name_s, &lookup_filter, get_only_nearest, interner),
      IEnvironmentT::Citizen(e) => e.lookup_with_imprecise_name_inner(name_s, &lookup_filter, get_only_nearest, interner),
      IEnvironmentT::Function(e) => e.lookup_with_imprecise_name_inner(name_s, &lookup_filter, get_only_nearest, interner),
      IEnvironmentT::Node(e) => e.lookup_with_imprecise_name_inner(name_s, &lookup_filter, get_only_nearest, interner),
      IEnvironmentT::BuildingWithClosureds(e) => e.lookup_with_imprecise_name_inner(name_s, &lookup_filter, get_only_nearest, interner),
      IEnvironmentT::BuildingWithClosuredsAndTemplateArgs(e) => e.lookup_with_imprecise_name_inner(name_s, &lookup_filter, get_only_nearest, interner),
      IEnvironmentT::General(e) => e.lookup_with_imprecise_name_inner(name_s, &lookup_filter, get_only_nearest, interner),
      IEnvironmentT::Export(e) => e.lookup_with_imprecise_name_inner(name_s, &lookup_filter, get_only_nearest, interner),
      IEnvironmentT::Extern(e) => e.lookup_with_imprecise_name_inner(name_s, &lookup_filter, get_only_nearest, interner),
    }
  }
  
  pub fn lookup_with_name_inner(
    &self,
    name_s: INameT<'s, 't>,
    lookup_filter: HashSet<ILookupContext>,
    get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    match self {
      IEnvironmentT::Citizen(c) => c.lookup_with_name_inner(name_s, &lookup_filter, get_only_nearest, interner),
      IEnvironmentT::Node(e) => e.lookup_with_name_inner(name_s, &lookup_filter, get_only_nearest, interner),
      IEnvironmentT::Function(e) => e.lookup_with_name_inner(name_s, &lookup_filter, get_only_nearest, interner),
      IEnvironmentT::Package(p) => p.lookup_with_name_inner(name_s, &lookup_filter, get_only_nearest, interner),
      _ => panic!("implement: lookup_with_name_inner for {:?}", discriminant(self)),
    }
  }
  
  pub fn lookup_all_with_imprecise_name(
    &self,
    name_s: IImpreciseNameS<'s>,
    lookup_filter: HashSet<ILookupContext>,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    self.lookup_with_imprecise_name_inner(name_s, lookup_filter, false, interner)
  }
  
  pub fn lookup_all_with_name(
    &self,
    name_s: INameT<'s, 't>,
    lookup_filter: HashSet<ILookupContext>,
  ) -> Vec<ITemplataT<'s, 't>> {
    panic!("Unimplemented: lookup_all_with_name");
  }
  
  pub fn lookup_nearest_with_name(
    &self,
    name_s: INameT<'s, 't>,
    lookup_filter: HashSet<ILookupContext>,
    interner: &TypingInterner<'s, 't>,
  ) -> Option<ITemplataT<'s, 't>> {
    let results = self.lookup_with_name_inner(name_s, lookup_filter, true, interner);
    match results.len() {
      0 => None,
      1 => Some(results[0]),
      _ => panic!("Too many with name {:?}: {:?}", name_s, results),
    }
  }
  
  pub fn lookup_nearest_with_imprecise_name(
    &self,
    name_s: IImpreciseNameS<'s>,
    lookup_filter: HashSet<ILookupContext>,
    interner: &TypingInterner<'s, 't>,
  ) -> Option<ITemplataT<'s, 't>> {
    let results = self.lookup_with_imprecise_name_inner(name_s, lookup_filter, true, interner);
    match results.len() {
      0 => None,
      1 => Some(results.into_iter().next().unwrap()),
      _ => panic!("Too many with name: {:?}", name_s),
    }
  }

  pub fn id(&self) -> IdT<'s, 't> {
    match self {
      IEnvironmentT::Package(e) => e.id,
      IEnvironmentT::Citizen(e) => e.id,
      IEnvironmentT::Function(e) => e.id,
      IEnvironmentT::Node(e) => e.parent_function_env.id,
      IEnvironmentT::BuildingWithClosureds(e) => e.id,
      IEnvironmentT::BuildingWithClosuredsAndTemplateArgs(e) => e.id,
      IEnvironmentT::General(e) => e.id,
      IEnvironmentT::Export(e) => e.id,
      IEnvironmentT::Extern(e) => e.id,
    }
  }
  
}

/// Polyvalue (see @TFITCX) — derive Eq/Hash; never hand-roll `ptr::eq` on the outer `&self` (see @PVECFPZ).
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IInDenizenEnvironmentT<'s, 't>
where 's: 't,
{
    Citizen(&'t CitizenEnvironmentT<'s, 't>),
    Function(&'t FunctionEnvironmentT<'s, 't>),
    Node(&'t NodeEnvironmentT<'s, 't>),
    BuildingWithClosureds(&'t BuildingFunctionEnvironmentWithClosuredsT<'s, 't>),
    BuildingWithClosuredsAndTemplateArgs(&'t BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't>),
    General(&'t GeneralEnvironmentT<'s, 't>),
    Export(&'t ExportEnvironmentT<'s, 't>),
    Extern(&'t ExternEnvironmentT<'s, 't>),
}

impl<'s, 't> IInDenizenEnvironmentT<'s, 't> where 's: 't {
  pub fn root_compiling_denizen_env(&self) -> IInDenizenEnvironmentT<'s, 't> {
    match self {
      IInDenizenEnvironmentT::Citizen(e) => e.root_compiling_denizen_env(),
      IInDenizenEnvironmentT::Function(e) => e.root_compiling_denizen_env(),
      IInDenizenEnvironmentT::Node(e) => e.parent_function_env.root_compiling_denizen_env(),
      IInDenizenEnvironmentT::BuildingWithClosureds(_) => *self,
      IInDenizenEnvironmentT::BuildingWithClosuredsAndTemplateArgs(_) => *self,
      IInDenizenEnvironmentT::General(e) => e.root_compiling_denizen_env(),
      IInDenizenEnvironmentT::Export(_) => *self,
      IInDenizenEnvironmentT::Extern(_) => *self,
    }
  }
  
  pub fn denizen_id(&self) -> IdT<'s, 't> {
    match self {
      IInDenizenEnvironmentT::Citizen(e) => e.template_id,
      IInDenizenEnvironmentT::Function(e) => e.id,
      IInDenizenEnvironmentT::Node(e) => e.parent_function_env.id,
      IInDenizenEnvironmentT::BuildingWithClosureds(e) => e.id,
      IInDenizenEnvironmentT::BuildingWithClosuredsAndTemplateArgs(e) => e.id,
      IInDenizenEnvironmentT::General(e) => e.id,
      IInDenizenEnvironmentT::Export(e) => e.id,
      IInDenizenEnvironmentT::Extern(e) => e.id,
    }
  }
  
  pub fn denizen_template_id(&self) -> IdT<'s, 't> {
    match self {
      IInDenizenEnvironmentT::Citizen(e) => e.template_id,
      IInDenizenEnvironmentT::Function(e) => e.template_id,
      IInDenizenEnvironmentT::Node(e) => e.parent_function_env.template_id,
      IInDenizenEnvironmentT::BuildingWithClosureds(e) => e.id,
      IInDenizenEnvironmentT::BuildingWithClosuredsAndTemplateArgs(e) => e.id,
      IInDenizenEnvironmentT::General(e) => e.template_id,
      IInDenizenEnvironmentT::Export(e) => e.template_id,
      IInDenizenEnvironmentT::Extern(e) => e.template_id,
    }
  }
  
// Inherited from IEnvironmentT
  pub fn lookup_nearest_with_imprecise_name(
    &self,
    name_s: IImpreciseNameS<'s>,
    lookup_filter: HashSet<ILookupContext>,
    interner: &TypingInterner<'s, 't>,
  ) -> Option<ITemplataT<'s, 't>> {
    let as_env: IEnvironmentT<'s, 't> = (*self).into();
    as_env.lookup_nearest_with_imprecise_name(name_s, lookup_filter, interner)
  }
  
// Inherited from IEnvironmentT
  pub fn lookup_nearest_with_name(
    &self,
    name_s: INameT<'s, 't>,
    lookup_filter: HashSet<ILookupContext>,
    interner: &TypingInterner<'s, 't>,
  ) -> Option<ITemplataT<'s, 't>> {
    let as_env: IEnvironmentT<'s, 't> = (*self).into();
    as_env.lookup_nearest_with_name(name_s, lookup_filter, interner)
  }
  
// Inherited from IEnvironmentT
  pub fn lookup_all_with_name(
    &self,
    name_s: INameT<'s, 't>,
    lookup_filter: HashSet<ILookupContext>,
  ) -> Vec<ITemplataT<'s, 't>> {
    let as_env: IEnvironmentT<'s, 't> = (*self).into();
    as_env.lookup_all_with_name(name_s, lookup_filter)
  }
  
// Inherited from IEnvironmentT
  pub fn lookup_all_with_imprecise_name(
    &self,
    name_s: IImpreciseNameS<'s>,
    lookup_filter: HashSet<ILookupContext>,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    let as_env: IEnvironmentT<'s, 't> = (*self).into();
    as_env.lookup_all_with_imprecise_name(name_s, lookup_filter, interner)
  }

// Inherited from IEnvironmentT
  pub fn lookup_with_name_inner(
    &self,
    name_s: INameT<'s, 't>,
    lookup_filter: HashSet<ILookupContext>,
    get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    let as_env: IEnvironmentT<'s, 't> = (*self).into();
    as_env.lookup_with_name_inner(name_s, lookup_filter, get_only_nearest, interner)
  }
  
// Inherited from IEnvironmentT
  pub fn lookup_with_imprecise_name_inner(
    &self,
    name_s: IImpreciseNameS<'s>,
    lookup_filter: HashSet<ILookupContext>,
    get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    let as_env: IEnvironmentT<'s, 't> = (*self).into();
    as_env.lookup_with_imprecise_name_inner(name_s, lookup_filter, get_only_nearest, interner)
  }
  
// Inherited from IEnvironmentT
  pub fn templatas(&self) -> &'t TemplatasStoreT<'s, 't> {
    match self {
      IInDenizenEnvironmentT::Citizen(e) => e.templatas,
      IInDenizenEnvironmentT::Function(e) => e.templatas,
      IInDenizenEnvironmentT::Node(e) => e.templatas,
      IInDenizenEnvironmentT::BuildingWithClosureds(e) => e.templatas,
      IInDenizenEnvironmentT::BuildingWithClosuredsAndTemplateArgs(e) => e.templatas,
      IInDenizenEnvironmentT::General(e) => e.templatas,
      IInDenizenEnvironmentT::Export(e) => e.templatas,
      IInDenizenEnvironmentT::Extern(e) => e.templatas,
    }
  }
  
  pub fn global_env(&self) -> &'t GlobalEnvironmentT<'s, 't> {
    match self {
      IInDenizenEnvironmentT::Citizen(e) => e.global_env,
      IInDenizenEnvironmentT::Function(e) => e.global_env,
      IInDenizenEnvironmentT::Node(e) => e.parent_function_env.global_env,
      IInDenizenEnvironmentT::BuildingWithClosureds(e) => e.global_env,
      IInDenizenEnvironmentT::BuildingWithClosuredsAndTemplateArgs(e) => e.global_env,
      IInDenizenEnvironmentT::General(e) => e.global_env,
      IInDenizenEnvironmentT::Export(e) => e.global_env,
      IInDenizenEnvironmentT::Extern(e) => e.global_env,
    }
  }
  
  pub fn id(&self) -> IdT<'s, 't> {
    match self {
      IInDenizenEnvironmentT::Citizen(e) => e.id,
      IInDenizenEnvironmentT::Function(e) => e.id,
      IInDenizenEnvironmentT::Node(e) => e.parent_function_env.id,
      IInDenizenEnvironmentT::BuildingWithClosureds(e) => e.id,
      IInDenizenEnvironmentT::BuildingWithClosuredsAndTemplateArgs(e) => e.id,
      IInDenizenEnvironmentT::General(e) => e.id,
      IInDenizenEnvironmentT::Export(e) => e.id,
      IInDenizenEnvironmentT::Extern(e) => e.id,
    }
  }
  
}
/// Miscellaneous (see @TFITCX)
#[derive(Clone, Copy, PartialEq, Eq, Hash)]
pub enum ILookupContext {
  TemplataLookupContext,
  ExpressionLookupContext,
}

// Macro-dispatch fields (functorHelper, *Macro, nameToStructDefinedMacro, etc.)
// from the Scala case class below are omitted here; they moved to `Compiler` as
// part of the god-struct refactor. See docs/migration/handoff-god-struct-progress.md.
// Exception: `name_to_function_body_macro` is preserved as a field per Scala
// parity — the Scala lookup is via Map[StrI, IFunctionBodyMacro], realized in
// Rust as ArenaIndexMap<StrI, FunctionBodyMacro> (dispatch-tag enum at
// macros::macros::FunctionBodyMacro).
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct GlobalEnvironmentT<'s, 't>
where 's: 't,
{
  pub name_to_top_level_environment:
    &'t [(&'t IdT<'s, 't>, &'t TemplatasStoreT<'s, 't>)],
  pub name_to_function_body_macro:
    ArenaIndexMap<'t, StrI<'s>, FunctionBodyMacro>,
  pub builtins: &'t TemplatasStoreT<'s, 't>,
}

pub fn entry_matches_filter<'s, 't>(
  entry: &IEnvEntryT<'s, 't>,
  contexts: &HashSet<ILookupContext>,
) -> bool {
  match entry {
    IEnvEntryT::Function(_) => contexts.contains(&ILookupContext::ExpressionLookupContext),
    IEnvEntryT::Impl(_) => contexts.contains(&ILookupContext::TemplataLookupContext),
    IEnvEntryT::Struct(_) => contexts.contains(&ILookupContext::TemplataLookupContext),
    IEnvEntryT::Interface(_) => contexts.contains(&ILookupContext::TemplataLookupContext),
    IEnvEntryT::Templata(templata) => {
      match templata {
        ITemplataT::Placeholder(..) => contexts.contains(&ILookupContext::TemplataLookupContext),
        ITemplataT::Isa(_) => contexts.contains(&ILookupContext::TemplataLookupContext),
        ITemplataT::Coord(_) => contexts.contains(&ILookupContext::TemplataLookupContext),
        ITemplataT::CoordList(_) => contexts.contains(&ILookupContext::TemplataLookupContext),
        ITemplataT::Prototype(_) => true,
        ITemplataT::Kind(_) => contexts.contains(&ILookupContext::TemplataLookupContext),
        ITemplataT::StructDefinition(_) => contexts.contains(&ILookupContext::TemplataLookupContext),
        ITemplataT::InterfaceDefinition(_) => contexts.contains(&ILookupContext::TemplataLookupContext),
        ITemplataT::RuntimeSizedArrayTemplate(_) => contexts.contains(&ILookupContext::TemplataLookupContext),
        ITemplataT::StaticSizedArrayTemplate(_) => contexts.contains(&ILookupContext::TemplataLookupContext),
        ITemplataT::Boolean(_) => true,
        ITemplataT::Function(_) => contexts.contains(&ILookupContext::ExpressionLookupContext),
        ITemplataT::ImplDefinition(_) => contexts.contains(&ILookupContext::ExpressionLookupContext),
        ITemplataT::Integer(_) => true,
        ITemplataT::String(_) => true,
        ITemplataT::Location(_) => contexts.contains(&ILookupContext::TemplataLookupContext),
        ITemplataT::Mutability(_) => contexts.contains(&ILookupContext::TemplataLookupContext),
        ITemplataT::Ownership(_) => contexts.contains(&ILookupContext::TemplataLookupContext),
        ITemplataT::Variability(_) => contexts.contains(&ILookupContext::TemplataLookupContext),
        ITemplataT::ExternFunction(_) => contexts.contains(&ILookupContext::ExpressionLookupContext),
      }
    }
  }
}

pub fn entry_to_templata<'s, 't>(
  defining_env: IEnvironmentT<'s, 't>,
  entry: IEnvEntryT<'s, 't>,
  interner: &TypingInterner<'s, 't>,
) -> ITemplataT<'s, 't>
where 's: 't,
{
  match entry {
    IEnvEntryT::Function(func) => {
        ITemplataT::Function(interner.alloc(FunctionTemplataT {
            outer_env: defining_env,
            function: func,
        }))
    }
    IEnvEntryT::Struct(struct_a) => {
        ITemplataT::StructDefinition(interner.alloc(StructDefinitionTemplataT {
            declaring_env: defining_env,
            origin_struct: struct_a,
        }))
    }
    IEnvEntryT::Interface(interface_a) => {
        ITemplataT::InterfaceDefinition(interner.alloc(InterfaceDefinitionTemplataT {
            declaring_env: defining_env,
            origin_interface: interface_a,
        }))
    }
    IEnvEntryT::Impl(impl_a) => ITemplataT::ImplDefinition(interner.alloc(ImplDefinitionTemplataT {
        env: defining_env,
        impl_: impl_a,
    })),
    IEnvEntryT::Templata(templata) => templata,
  }
}

pub fn get_imprecise_name<'s, 't>(
  scout_arena: &ScoutArena<'s>,
  name_t: INameT<'s, 't>,
) -> Option<IImpreciseNameS<'s>> {
  match name_t {
    INameT::FunctionTemplate(f) => Some(scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: f.human_name }))),
    INameT::Primitive(p) => Some(scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: p.human_name }))),
    INameT::StructTemplate(s) => Some(scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: s.human_name }))),
    INameT::InterfaceTemplate(i) => Some(scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: i.human_namee }))),
    INameT::Rune(r) => Some(scout_arena.intern_imprecise_name(IImpreciseNameValS::RuneName(RuneNameValS { rune: r.rune }))),
    INameT::LambdaCitizen(lc) => get_imprecise_name(scout_arena, INameT::LambdaCitizenTemplate(lc.template)),
    INameT::LambdaCitizenTemplate(_loc) => Some(scout_arena.intern_imprecise_name(
        IImpreciseNameValS::LambdaStructImpreciseName(LambdaStructImpreciseNameValS {
            lambda_name: scout_arena.intern_imprecise_name(IImpreciseNameValS::LambdaImpreciseName(LambdaImpreciseNameS {})),
        }))),
    INameT::ClosureParam(_cp) => Some(scout_arena.intern_imprecise_name(IImpreciseNameValS::ClosureParamImpreciseName(ClosureParamImpreciseNameS {}))),
    INameT::Self_(_) => Some(scout_arena.intern_imprecise_name(IImpreciseNameValS::SelfName(SelfNameS {}))),
    INameT::Arbitrary(_) => Some(scout_arena.intern_imprecise_name(IImpreciseNameValS::ArbitraryName(ArbitraryNameS {}))),
    INameT::ReachablePrototype(_) => None,
    INameT::FunctionBound(fb) => get_imprecise_name(scout_arena, INameT::FunctionBoundTemplate(fb.template)),
    INameT::FunctionBoundTemplate(fbt) => Some(scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: fbt.human_name }))),
    INameT::PredictedFunction(pf) => get_imprecise_name(scout_arena, INameT::PredictedFunctionTemplate(pf.template)),
    INameT::PredictedFunctionTemplate(pft) => Some(scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: pft.human_name }))),
    INameT::LambdaCallFunction(_) => None,
    INameT::KindPlaceholder(kp) => Some(scout_arena.intern_imprecise_name(IImpreciseNameValS::PlaceholderImpreciseName(PlaceholderImpreciseNameS { index: kp.template.index }))),
    INameT::Struct(s) => get_imprecise_name(scout_arena, s.template.into()),
    INameT::Interface(i) => get_imprecise_name(scout_arena, INameT::InterfaceTemplate(i.template)),
    INameT::Function(f) => get_imprecise_name(scout_arena, INameT::FunctionTemplate(f.template)),
    INameT::ForwarderFunction(f) => get_imprecise_name(scout_arena, INameT::ForwarderFunctionTemplate(f.template)),
    INameT::ForwarderFunctionTemplate(f) => get_imprecise_name(scout_arena, f.inner.into()),
    INameT::ImplTemplate(_) => panic!("Unimplemented or unreachable: ImplTemplateNameT — Scala vwat()"),
    INameT::AnonymousSubstructTemplate(astn) => {
        let inner_name = get_imprecise_name(scout_arena, astn.interface.into());
        inner_name.map(|x| scout_arena.intern_imprecise_name(IImpreciseNameValS::AnonymousSubstructTemplateImpreciseName(AnonymousSubstructTemplateImpreciseNameValS { interface_imprecise_name: x })))
    }
    INameT::AnonymousSubstructConstructorTemplate(asct) => {
        match asct.substruct {
            ICitizenTemplateNameT::StructTemplate(st) => {
                Some(scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: st.human_name })))
            }
            ICitizenTemplateNameT::AnonymousSubstructTemplate(astn) => {
                match astn.interface {
                    IInterfaceTemplateNameT::InterfaceTemplate(it) => {
                        Some(scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: it.human_namee })))
                    }
                }
            }
            _ => panic!("Unimplemented: get_imprecise_name for AnonymousSubstructConstructorTemplate with substruct {:?}", asct.substruct),
        }
    }
    INameT::AnonymousSubstruct(a) => get_imprecise_name(scout_arena, INameT::AnonymousSubstructTemplate(a.template)),
    _ => panic!("Unimplemented: get_imprecise_name for {:?}", name_t),
  }
}

pub fn code_locations_match<'s>(
  code_location_a: &CodeLocationS<'s>,
  code_location_b: &CodeLocationS<'s>,
) -> bool {
  panic!("Unimplemented: code_locations_match");
}

// Guardian: disable-all
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct TemplatasStoreT<'s, 't>
where 's: 't,
{
  pub templatas_store_name: &'t IdT<'s, 't>,
  // Per @IIIOZ, env lookup tables are ArenaIndexMap so iteration order is insertion-deterministic across runs.
  pub name_to_entry: ArenaIndexMap<'t, INameT<'s, 't>, IEnvEntryT<'s, 't>>,
  pub imprecise_to_entries: ArenaIndexMap<'t, IImpreciseNameS<'s>, &'t [IEnvEntryT<'s, 't>]>,
}

// Scala `override def equals/hashCode = vcurious()` — mirror with panic.
impl<'s, 't> PartialEq for TemplatasStoreT<'s, 't> where 's: 't {
  fn eq(&self, _other: &Self) -> bool { panic!("vcurious: TemplatasStoreT.eq") }
  
}
impl<'s, 't> Eq for TemplatasStoreT<'s, 't> where 's: 't {}
impl<'s, 't> Hash for TemplatasStoreT<'s, 't> where 's: 't {
  fn hash<H: Hasher>(&self, _state: &mut H) {
    panic!("vcurious: TemplatasStoreT.hash")
  }
  
}

// (no scala counterpart — builder for TemplatasStoreT. Heap Vec/HashMap during
// construction, frozen to arena slices at build_in.)
/// Temporary state (see @TFITCX)
pub struct TemplatasStoreBuilder<'s, 't>
where 's: 't,
{
  pub templatas_store_name: &'t IdT<'s, 't>,
  pub name_to_entry: Vec<(INameT<'s, 't>, IEnvEntryT<'s, 't>)>,
  // Per @IIIOZ: IndexMap so build_in() iteration preserves insertion order (deterministic across runs).
  pub imprecise_to_entries:
    IndexMap<IImpreciseNameS<'s>, Vec<IEnvEntryT<'s, 't>>>,
}

impl<'s, 't> TemplatasStoreBuilder<'s, 't>
where 's: 't,
{
  pub fn new(templatas_store_name: &'t IdT<'s, 't>) -> Self {
    TemplatasStoreBuilder {
      templatas_store_name,
      name_to_entry: Vec::new(),
      imprecise_to_entries: IndexMap::new(),
    }
  }
  
  pub fn add_entries(
    &mut self,
    scout_arena: &ScoutArena<'s>,
    new_entries_list: Vec<(INameT<'s, 't>, IEnvEntryT<'s, 't>)>,
  ) {
    for (name, entry) in &new_entries_list {
      self.name_to_entry.push((*name, *entry));
      match entry {
        IEnvEntryT::Templata(ITemplataT::Prototype(proto_templata)) => {
          if let Some(key_imprecise) = get_imprecise_name(scout_arena, *name) {
            self.imprecise_to_entries.entry(key_imprecise).or_insert_with(Vec::new).push(*entry);
          }
          if let Some(local_imprecise) = get_imprecise_name(scout_arena, proto_templata.prototype.id.local_name) {
            self.imprecise_to_entries.entry(local_imprecise).or_insert_with(Vec::new).push(*entry);
          }
          self.imprecise_to_entries.entry(scout_arena.intern_imprecise_name(IImpreciseNameValS::PrototypeName(PrototypeNameS {}))).or_insert_with(Vec::new).push(*entry);
        }
        IEnvEntryT::Impl(impl_a) => {
          let sub = impl_a.sub_citizen_imprecise_name;
          let sup = impl_a.super_interface_imprecise_name;
          self.imprecise_to_entries.entry(scout_arena.intern_imprecise_name(IImpreciseNameValS::ImplImpreciseName(ImplImpreciseNameValS { sub_citizen_imprecise_name: sub, super_interface_imprecise_name: sup }))).or_insert_with(Vec::new).push(*entry);
          self.imprecise_to_entries.entry(scout_arena.intern_imprecise_name(IImpreciseNameValS::ImplSubCitizenImpreciseName(ImplSubCitizenImpreciseNameValS { sub_citizen_imprecise_name: sub }))).or_insert_with(Vec::new).push(*entry);
          self.imprecise_to_entries.entry(scout_arena.intern_imprecise_name(IImpreciseNameValS::ImplSuperInterfaceImpreciseName(ImplSuperInterfaceImpreciseNameValS { super_interface_imprecise_name: sup }))).or_insert_with(Vec::new).push(*entry);
        }
        IEnvEntryT::Templata(ITemplataT::Isa(isa)) => {
          let sub_local_name = match isa.sub_kind {
            KindT::Struct(stt) => stt.id.local_name,
            KindT::Interface(itt) => itt.id.local_name,
            KindT::KindPlaceholder(kp) => kp.id.local_name,
            _ => panic!("vwat: unexpected sub_kind in IsaTemplataT add_entries: {:?}", isa.sub_kind),
          };
          let super_local_name = match isa.super_kind {
            KindT::Interface(itt) => itt.id.local_name,
            KindT::KindPlaceholder(kp) => kp.id.local_name,
            _ => panic!("vwat: unexpected super_kind in IsaTemplataT add_entries: {:?}", isa.super_kind),
          };
          let sub_imprecise = get_imprecise_name(scout_arena, sub_local_name)
            .unwrap_or_else(|| panic!("vassertSome: no imprecise name for sub_kind {:?}", isa.sub_kind));
          let super_imprecise = get_imprecise_name(scout_arena, super_local_name)
            .unwrap_or_else(|| panic!("vassertSome: no imprecise name for super_kind {:?}", isa.super_kind));
          if let Some(key_imprecise) = get_imprecise_name(scout_arena, *name) {
            self.imprecise_to_entries.entry(key_imprecise).or_insert_with(Vec::new).push(*entry);
          }
          self.imprecise_to_entries.entry(scout_arena.intern_imprecise_name(IImpreciseNameValS::ImplImpreciseName(ImplImpreciseNameValS { sub_citizen_imprecise_name: sub_imprecise, super_interface_imprecise_name: super_imprecise }))).or_insert_with(Vec::new).push(*entry);
          self.imprecise_to_entries.entry(scout_arena.intern_imprecise_name(IImpreciseNameValS::ImplSubCitizenImpreciseName(ImplSubCitizenImpreciseNameValS { sub_citizen_imprecise_name: sub_imprecise }))).or_insert_with(Vec::new).push(*entry);
          self.imprecise_to_entries.entry(scout_arena.intern_imprecise_name(IImpreciseNameValS::ImplSuperInterfaceImpreciseName(ImplSuperInterfaceImpreciseNameValS { super_interface_imprecise_name: super_imprecise }))).or_insert_with(Vec::new).push(*entry);
        }
        _ => {
          if let Some(imprecise) = get_imprecise_name(scout_arena, *name) {
            self.imprecise_to_entries.entry(imprecise).or_insert_with(Vec::new).push(*entry);
          }
        }
      }
    }
  }
  
  pub fn build_in(
    self,
    interner: &TypingInterner<'s, 't>,
  ) -> &'t TemplatasStoreT<'s, 't> {
    let name_to_entry = interner.alloc_index_map_from_iter(self.name_to_entry);
    let imprecise_to_entries = interner.alloc_index_map_from_iter(
      self.imprecise_to_entries.into_iter().map(|(name, entries)| {
        let frozen: &'t [IEnvEntryT<'s, 't>] = interner.alloc_slice_from_vec(entries);
        (name, frozen)
      })
    );
    interner.alloc(TemplatasStoreT {
      templatas_store_name: self.templatas_store_name,
      name_to_entry,
      imprecise_to_entries,
    })
  }
  
  // (no scala counterpart — inverse of `snapshot`. Copies an arena `TemplatasStoreT`
  //  back into a heap builder so a `NodeEnvironmentBox` can be reconstructed from a
  //  `&'t NodeEnvironmentT`. Symmetric with `snapshot`.)
  pub fn from_store(store: &TemplatasStoreT<'s, 't>) -> Self {
    let name_to_entry: Vec<(INameT<'s, 't>, IEnvEntryT<'s, 't>)> =
      (&store.name_to_entry).into_iter().map(|(k, v)| (*k, *v)).collect();
    let mut imprecise_to_entries: IndexMap<IImpreciseNameS<'s>, Vec<IEnvEntryT<'s, 't>>> =
      IndexMap::new();
    for (k, v) in &store.imprecise_to_entries {
      imprecise_to_entries.insert(*k, v.to_vec());
    }
    TemplatasStoreBuilder {
      templatas_store_name: store.templatas_store_name,
      name_to_entry,
      imprecise_to_entries,
    }
  }
  
  pub fn snapshot(
    &self,
    interner: &TypingInterner<'s, 't>,
  ) -> &'t TemplatasStoreT<'s, 't> {
    let name_to_entry = interner.alloc_index_map_from_iter(self.name_to_entry.iter().copied());
    let imprecise_to_entries = interner.alloc_index_map_from_iter(
      self.imprecise_to_entries.iter().map(|(name, entries)| {
        let frozen: &'t [IEnvEntryT<'s, 't>] = interner.alloc_slice_from_vec(entries.clone());
        (*name, frozen)
      })
    );
    interner.alloc(TemplatasStoreT {
      templatas_store_name: self.templatas_store_name,
      name_to_entry,
      imprecise_to_entries,
    })
  }
  
}

impl<'s, 't> TemplatasStoreT<'s, 't> where 's: 't {
  pub fn add_entries(
    &self,
    interner: &TypingInterner<'s, 't>,
    scout_arena: &ScoutArena<'s>,
    new_entries_list: Vec<(INameT<'s, 't>, IEnvEntryT<'s, 't>)>,
  ) -> TemplatasStoreT<'s, 't> {
    // Per @IIIOZ: IndexMap so iteration at line ~1007 preserves new_entries_list source order (deterministic).
    let new_entries: IndexMap<INameT<'s, 't>, IEnvEntryT<'s, 't>> = new_entries_list.iter().cloned().collect();
    assert!(new_entries.len() == new_entries_list.len());

    // combinedEntries = oldEntries ++ newEntries
    let mut combined_entries: Vec<(INameT<'s, 't>, IEnvEntryT<'s, 't>)> = self.name_to_entry.iter().map(|(k, v)| (*k, *v)).collect();
    // Intersection assertion
    for (key, _) in self.name_to_entry.iter() {
      if let Some(new_val) = new_entries.get(key) {
        assert!(self.name_to_entry.get(key) == Some(new_val));
      }
    }
    for (key, val) in new_entries.iter() {
      if !self.name_to_entry.contains_key(key) {
        combined_entries.push((*key, *val));
      }
    }

    // newEntriesByNameS
    let new_entries_by_name_s: Vec<(IImpreciseNameS<'s>, IEnvEntryT<'s, 't>)> =
      new_entries.iter().flat_map(|(key, value)| {
        match value {
          IEnvEntryT::Templata(ITemplataT::Prototype(proto_templata)) => {
            let mut entries = vec![];
            if let Some(key_imprecise) = get_imprecise_name(scout_arena, *key) {
              entries.push((key_imprecise, *value));
            }
            if let Some(local_imprecise) = get_imprecise_name(scout_arena, proto_templata.prototype.id.local_name) {
              entries.push((local_imprecise, *value));
            }
            entries.push((scout_arena.intern_imprecise_name(IImpreciseNameValS::PrototypeName(PrototypeNameS {})), *value));
            entries.into_iter().collect::<Vec<_>>()
          }
          IEnvEntryT::Impl(_) => {
            panic!("Unimplemented: add_entries ImplEnvEntry case");
          }
          IEnvEntryT::Templata(ITemplataT::Isa(isa)) => {
            let sub_local_name = match isa.sub_kind {
              KindT::Struct(stt) => stt.id.local_name,
              KindT::Interface(itt) => itt.id.local_name,
              KindT::KindPlaceholder(kp) => kp.id.local_name,
              _ => panic!("vwat: unexpected sub_kind in IsaTemplataT add_entries: {:?}", isa.sub_kind),
            };
            let super_local_name = match isa.super_kind {
              KindT::Interface(itt) => itt.id.local_name,
              KindT::KindPlaceholder(kp) => kp.id.local_name,
              _ => panic!("vwat: unexpected super_kind in IsaTemplataT add_entries: {:?}", isa.super_kind),
            };
            let sub_imprecise = get_imprecise_name(scout_arena, sub_local_name)
              .unwrap_or_else(|| panic!("vassertSome: no imprecise name for sub_kind {:?}", isa.sub_kind));
            let super_imprecise = get_imprecise_name(scout_arena, super_local_name)
              .unwrap_or_else(|| panic!("vassertSome: no imprecise name for super_kind {:?}", isa.super_kind));
            let mut entries = vec![];
            if let Some(key_imprecise) = get_imprecise_name(scout_arena, *key) {
              entries.push((key_imprecise, *value));
            }
            entries.push((scout_arena.intern_imprecise_name(IImpreciseNameValS::ImplImpreciseName(ImplImpreciseNameValS { sub_citizen_imprecise_name: sub_imprecise, super_interface_imprecise_name: super_imprecise })), *value));
            entries.push((scout_arena.intern_imprecise_name(IImpreciseNameValS::ImplSubCitizenImpreciseName(ImplSubCitizenImpreciseNameValS { sub_citizen_imprecise_name: sub_imprecise })), *value));
            entries.push((scout_arena.intern_imprecise_name(IImpreciseNameValS::ImplSuperInterfaceImpreciseName(ImplSuperInterfaceImpreciseNameValS { super_interface_imprecise_name: super_imprecise })), *value));
            entries
          }
          _ => {
            get_imprecise_name(scout_arena, *key).into_iter().map(|imprecise| (imprecise, *value)).collect::<Vec<_>>()
          }
        }
      }).collect();

    // Group by imprecise name
    // Per @IIIOZ: IndexMap so downstream iteration preserves new_entries_by_name_s source order.
    let mut grouped: IndexMap<IImpreciseNameS<'s>, Vec<IEnvEntryT<'s, 't>>> = IndexMap::new();
    for (name, entry) in &new_entries_by_name_s {
      grouped.entry(*name).or_insert_with(Vec::new).push(*entry);
    }

    // combinedEntriesByNameS =
    //   entriesByImpreciseNameS ++
    //   newEntriesByNameS ++
    //   entriesByImpreciseNameS.keySet.intersect(newEntriesByNameS.keySet)
    //     .map(key => (key -> (entriesByImpreciseNameS(key) ++ newEntriesByNameS(key)))).toMap
    // Per @IIIOZ: IndexMap so the alloc_index_map_from_iter freeze at line ~1072 inherits deterministic order
    // from upstream self.imprecise_to_entries (IndexMap) and grouped (IndexMap).
    let mut combined_by_name_s: IndexMap<IImpreciseNameS<'s>, Vec<IEnvEntryT<'s, 't>>> = IndexMap::new();
    // Step 1: entriesByImpreciseNameS
    for (name, entries) in self.imprecise_to_entries.iter() {
      combined_by_name_s.insert(*name, entries.to_vec());
    }
    // Step 2: ++ newEntriesByNameS (overwrite for matching keys, add for new keys)
    for (name, entries) in &grouped {
      combined_by_name_s.insert(*name, entries.clone());
    }
    // Step 3: ++ intersection-merged (for keys in both old and new, replace with old ++ new)
    for name in self.imprecise_to_entries.keys() {
      if let Some(new_entries_for_key) = grouped.get(name) {
        let old_entries_for_key = self.imprecise_to_entries.get(name).unwrap();
        let mut merged = old_entries_for_key.to_vec();
        merged.extend(new_entries_for_key.iter());
        combined_by_name_s.insert(*name, merged);
      }
    }

    // Build the final store
    let name_to_entry = interner.alloc_index_map_from_iter(combined_entries);
    let imprecise_to_entries = interner.alloc_index_map_from_iter(
      combined_by_name_s.into_iter().map(|(name, entries)| {
        let frozen: &'t [IEnvEntryT<'s, 't>] = interner.alloc_slice_from_vec(entries);
        (name, frozen)
      })
    );
    TemplatasStoreT {
      templatas_store_name: self.templatas_store_name,
      name_to_entry,
      imprecise_to_entries,
    }
  }

  pub fn add_entry(
    &self,
    interner: &TypingInterner<'s, 't>,
    scout_arena: &ScoutArena<'s>,
    name: INameT<'s, 't>,
    entry: IEnvEntryT<'s, 't>,
  ) -> TemplatasStoreT<'s, 't> {
    self.add_entries(interner, scout_arena, vec![(name, entry)])
  }

  pub fn lookup_with_name_inner(
    &self,
    defining_env: IEnvironmentT<'s, 't>,
    name: INameT<'s, 't>,
    lookup_filter: &HashSet<ILookupContext>,
    interner: &TypingInterner<'s, 't>,
  ) -> Option<ITemplataT<'s, 't>> {
    self.name_to_entry.get(&name)
      .filter(|entry| entry_matches_filter(entry, lookup_filter))
      .map(|entry| entry_to_templata(defining_env, *entry, interner))
  }
  
  pub fn lookup_with_imprecise_name_inner(
    &self,
    defining_env: IEnvironmentT<'s, 't>,
    name: IImpreciseNameS<'s>,
    lookup_filter: &HashSet<ILookupContext>,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    let a1 = self.imprecise_to_entries.get(&name).copied().unwrap_or(&[]);
    let a2: Vec<_> = a1.iter().filter(|e| entry_matches_filter(e, lookup_filter)).collect();
    let a3: Vec<ITemplataT<'s, 't>> = a2.iter().map(|e| entry_to_templata(defining_env, **e, interner)).collect();
    a3
  }
  
}

pub fn make_top_level_environment<'s, 't>(
  global_env: &'t GlobalEnvironmentT<'s, 't>,
  namespace_name: IdT<'s, 't>,
  interner: &TypingInterner<'s, 't>,
) -> &'t PackageEnvironmentT<'s, 't> {
  let global_namespaces: Vec<&'t TemplatasStoreT<'s, 't>> =
    global_env.name_to_top_level_environment.iter().map(|(_, ts)| *ts).collect();
  let global_namespaces = interner.alloc_slice_from_vec(global_namespaces);
  interner.alloc(PackageEnvironmentT {
    global_env,
    id: namespace_name,
    global_namespaces,
  })
}

/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct PackageEnvironmentT<'s, 't>
where 's: 't,
{
  pub global_env: &'t GlobalEnvironmentT<'s, 't>,
  pub id: IdT<'s, 't>,
  pub global_namespaces: &'t [&'t TemplatasStoreT<'s, 't>],
}

// (Realized by `impl Hash for PackageEnvironmentT` below.)

impl<'s, 't> PackageEnvironmentT<'s, 't> where 's: 't {
  pub fn templatas(&self) -> &TemplatasStoreT<'s, 't> {
    panic!("Unimplemented: templatas");
  }
  
  pub fn lookup_with_name_inner(
    &'t self,
    name: INameT<'s, 't>,
    lookup_filter: &HashSet<ILookupContext>,
    _get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    let mut result: Vec<ITemplataT<'s, 't>> = Vec::new();
    result.extend(self.global_env.builtins.lookup_with_name_inner(
      IEnvironmentT::Package(self), name, lookup_filter, interner));
    for global_namespace in self.global_namespaces {
      let per_namespace_env = interner.alloc(PackageEnvironmentT {
        global_env: self.global_env,
        id: *global_namespace.templatas_store_name,
        global_namespaces: self.global_namespaces,
      });
      result.extend(global_namespace.lookup_with_name_inner(
        IEnvironmentT::Package(per_namespace_env), name, lookup_filter, interner));
    }
    result
  }
  
  pub fn lookup_with_imprecise_name_inner(
    &'t self,
    name: IImpreciseNameS<'s>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    let mut result: Vec<ITemplataT<'s, 't>> = Vec::new();
    result.extend(self.global_env.builtins.lookup_with_imprecise_name_inner(
      IEnvironmentT::Package(self), name, lookup_filter, interner));
    for global_namespace in self.global_namespaces {
      let per_namespace_env = interner.alloc(PackageEnvironmentT {
        global_env: self.global_env,
        id: *global_namespace.templatas_store_name,
        global_namespaces: self.global_namespaces,
      });
      result.extend(global_namespace.lookup_with_imprecise_name_inner(
        IEnvironmentT::Package(per_namespace_env), name, lookup_filter, interner));
    }
    result
  }
  
}

// Id-based Hash/PartialEq — documented exception to @IEOIBZ. Compared via
// `self.id == other.id` (where `id: IdT` is sealed/canonical, so this is
// itself ptr-eq) instead of `std::ptr::eq(self, other)`. Comparisons via
// `IEnvironmentT` go through that enum's ptr-eq impl directly.
impl<'s, 't> PartialEq for PackageEnvironmentT<'s, 't> where 's: 't {
  fn eq(&self, other: &Self) -> bool { self.id == other.id }
  
}
impl<'s, 't> Eq for PackageEnvironmentT<'s, 't> where 's: 't {}
impl<'s, 't> Hash for PackageEnvironmentT<'s, 't> where 's: 't {
  fn hash<H: Hasher>(&self, state: &mut H) { self.id.hash(state); }
  
}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct CitizenEnvironmentT<'s, 't>
where 's: 't,
{
  pub global_env: &'t GlobalEnvironmentT<'s, 't>,
  pub parent_env: IEnvironmentT<'s, 't>,
  pub template_id: IdT<'s, 't>,
  pub id: IdT<'s, 't>,
  pub templatas: &'t TemplatasStoreT<'s, 't>,
}

impl<'s, 't> CitizenEnvironmentT<'s, 't> where 's: 't {
  pub fn denizen_id(&self) -> IdT<'s, 't> {
    panic!("Unimplemented: denizen_id");
  }
  
  pub fn denizen_template_id(&self) -> IdT<'s, 't> {
    panic!("Unimplemented: denizen_template_id");
  }
  
  pub fn root_compiling_denizen_env(&'t self) -> IInDenizenEnvironmentT<'s, 't> {
    match (self.id.local_name, self.parent_env.id().local_name) {
      (id_local, parent_local)
        if IInstantiationNameT::try_from(id_local).is_ok()
          && ITemplateNameT::try_from(parent_local).is_ok() => {
        IInDenizenEnvironmentT::Citizen(self)
      }
      (_, INameT::PackageTopLevel(_)) => {
        IInDenizenEnvironmentT::Citizen(self)
      }
      _ => {
        match IInDenizenEnvironmentT::try_from(self.parent_env) {
          Ok(parent_in_denizen_env) => {
            let result = parent_in_denizen_env.root_compiling_denizen_env();
            assert!(IInstantiationNameT::try_from(result.id().local_name).is_ok(), "vwat");
            result
          }
          Err(_) => { panic!("vwat: parent is not IInDenizenEnvironmentT"); }
        }
      }
    }
  }
  
  pub fn lookup_with_name_inner(
    &'t self,
    name: INameT<'s, 't>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    let result: Vec<_> = self.templatas.lookup_with_name_inner(
      IEnvironmentT::Citizen(self), name, lookup_filter, interner,
    ).into_iter().collect();
    if !result.is_empty() && get_only_nearest {
      result
    } else {
      let mut combined = result;
      combined.extend(self.parent_env.lookup_with_name_inner(name, lookup_filter.clone(), get_only_nearest, interner));
      combined
    }
  }
  
  pub fn lookup_with_imprecise_name_inner(
    &'t self,
    name: IImpreciseNameS<'s>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    let result = self.templatas.lookup_with_imprecise_name_inner(
      IEnvironmentT::Citizen(self), name, lookup_filter, interner,
    );
    if !result.is_empty() && get_only_nearest {
      result
    } else {
      let mut combined = result;
      combined.extend(self.parent_env.lookup_with_imprecise_name_inner(name, lookup_filter.clone(), get_only_nearest, interner));
      combined
    }
  }
  
}

impl<'s, 't> PartialEq for CitizenEnvironmentT<'s, 't> where 's: 't {
  fn eq(&self, other: &Self) -> bool { self.id == other.id }
  
}
impl<'s, 't> Eq for CitizenEnvironmentT<'s, 't> where 's: 't {}
impl<'s, 't> Hash for CitizenEnvironmentT<'s, 't> where 's: 't {
  fn hash<H: Hasher>(&self, state: &mut H) { self.id.hash(state); }
  
}
pub fn child_of<'s, 't>(
  interner: &TypingInterner<'s, 't>,
  scout_arena: &ScoutArena<'s>,
  parent_env: IInDenizenEnvironmentT<'s, 't>,
  new_template_id: IdT<'s, 't>,
  new_id: &'t IdT<'s, 't>,
  new_entries_list: Vec<(INameT<'s, 't>, IEnvEntryT<'s, 't>)>,
) -> &'t GeneralEnvironmentT<'s, 't>
where 's: 't,
{
  let mut builder = TemplatasStoreBuilder::new(new_id);
  builder.add_entries(scout_arena, new_entries_list);
  let templatas = builder.build_in(interner);
  interner.alloc(GeneralEnvironmentT {
    global_env: parent_env.global_env(),
    parent_env,
    template_id: new_template_id,
    id: *new_id,
    templatas,
  })
}

/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct ExportEnvironmentT<'s, 't>
where 's: 't,
{
  pub global_env: &'t GlobalEnvironmentT<'s, 't>,
  pub parent_env: &'t PackageEnvironmentT<'s, 't>,
  pub template_id: IdT<'s, 't>,
  pub id: IdT<'s, 't>,
  pub templatas: &'t TemplatasStoreT<'s, 't>,
}

impl<'s, 't> ExportEnvironmentT<'s, 't> where 's: 't {
  pub fn root_compiling_denizen_env(&'t self) -> IInDenizenEnvironmentT<'s, 't> {
    panic!("Unimplemented: root_compiling_denizen_env");
  }
  
  pub fn denizen_id(&self) -> IdT<'s, 't> {
    panic!("Unimplemented: denizen_id");
  }
  
  pub fn denizen_template_id(&self) -> IdT<'s, 't> {
    panic!("Unimplemented: denizen_template_id");
  }
  
  pub fn lookup_with_name_inner(
    &'t self,
    name: INameT<'s, 't>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
  ) -> Vec<ITemplataT<'s, 't>> {
    panic!("Unimplemented: lookup_with_name_inner");
  }
  
  pub fn lookup_with_imprecise_name_inner(
    &'t self,
    name: IImpreciseNameS<'s>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    let result = self.templatas.lookup_with_imprecise_name_inner(
      IEnvironmentT::Export(self), name, lookup_filter, interner,
    );
    if !result.is_empty() && get_only_nearest {
      result
    } else {
      let mut combined = result;
      combined.extend(self.parent_env.lookup_with_imprecise_name_inner(name, lookup_filter, get_only_nearest, interner));
      combined
    }
  }
  
}

impl<'s, 't> PartialEq for ExportEnvironmentT<'s, 't> where 's: 't {
  fn eq(&self, other: &Self) -> bool { self.id == other.id }
  
}
impl<'s, 't> Eq for ExportEnvironmentT<'s, 't> where 's: 't {}
impl<'s, 't> Hash for ExportEnvironmentT<'s, 't> where 's: 't {
  fn hash<H: Hasher>(&self, state: &mut H) { self.id.hash(state); }
  
}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct ExternEnvironmentT<'s, 't>
where 's: 't,
{
  pub global_env: &'t GlobalEnvironmentT<'s, 't>,
  pub parent_env: &'t PackageEnvironmentT<'s, 't>,
  pub template_id: IdT<'s, 't>,
  pub id: IdT<'s, 't>,
  pub templatas: &'t TemplatasStoreT<'s, 't>,
}

impl<'s, 't> ExternEnvironmentT<'s, 't> where 's: 't {
  pub fn root_compiling_denizen_env(&'t self) -> IInDenizenEnvironmentT<'s, 't> {
    panic!("Unimplemented: root_compiling_denizen_env");
  }
  
  pub fn denizen_id(&self) -> IdT<'s, 't> {
    panic!("Unimplemented: denizen_id");
  }
  
  pub fn denizen_template_id(&self) -> IdT<'s, 't> {
    panic!("Unimplemented: denizen_template_id");
  }
  
  pub fn lookup_with_name_inner(
    &'t self,
    name: INameT<'s, 't>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
  ) -> Vec<ITemplataT<'s, 't>> {
    panic!("Unimplemented: lookup_with_name_inner");
  }
  
  pub fn lookup_with_imprecise_name_inner(
    &'t self,
    name: IImpreciseNameS<'s>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    panic!("Unimplemented: lookup_with_imprecise_name_inner");
  }
  
}

impl<'s, 't> PartialEq for ExternEnvironmentT<'s, 't> where 's: 't {
  fn eq(&self, other: &Self) -> bool { self.id == other.id }
  
}
impl<'s, 't> Eq for ExternEnvironmentT<'s, 't> where 's: 't {}
impl<'s, 't> Hash for ExternEnvironmentT<'s, 't> where 's: 't {
  fn hash<H: Hasher>(&self, state: &mut H) { self.id.hash(state); }
  
}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct GeneralEnvironmentT<'s, 't>
where 's: 't,
{
  pub global_env: &'t GlobalEnvironmentT<'s, 't>,
  pub parent_env: IInDenizenEnvironmentT<'s, 't>,
  pub template_id: IdT<'s, 't>,
  pub id: IdT<'s, 't>,
  pub templatas: &'t TemplatasStoreT<'s, 't>,
}

impl<'s, 't> GeneralEnvironmentT<'s, 't> where 's: 't {
  pub fn denizen_id(&self) -> IdT<'s, 't> {
    panic!("Unimplemented: denizen_id");
  }
  
  pub fn denizen_template_id(&self) -> IdT<'s, 't> {
    panic!("Unimplemented: denizen_template_id");
  }
  
  pub fn root_compiling_denizen_env(&'t self) -> IInDenizenEnvironmentT<'s, 't> {
    self.parent_env.root_compiling_denizen_env()
  }
  
  pub fn lookup_with_name_inner(
    &'t self,
    name: INameT<'s, 't>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
  ) -> Vec<ITemplataT<'s, 't>> {
    panic!("Unimplemented: lookup_with_name_inner");
  }
  
  pub fn lookup_with_imprecise_name_inner(
    &'t self,
    name: IImpreciseNameS<'s>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    lookup_with_imprecise_name_inner(
      IEnvironmentT::General(self), self.templatas, IEnvironmentT::from(self.parent_env), name, lookup_filter, get_only_nearest, interner)
  }
  
}

// Scala `override def equals/hashCode = vcurious()` — mirror with panic.
impl<'s, 't> PartialEq for GeneralEnvironmentT<'s, 't> where 's: 't {
  fn eq(&self, _other: &Self) -> bool { panic!("vcurious: GeneralEnvironmentT.eq") }
  
}
impl<'s, 't> Eq for GeneralEnvironmentT<'s, 't> where 's: 't {}
impl<'s, 't> Hash for GeneralEnvironmentT<'s, 't> where 's: 't {
  fn hash<H: Hasher>(&self, _state: &mut H) {
    panic!("vcurious: GeneralEnvironmentT.hash")
  }
  
}

// Concrete → IEnvironmentT
impl<'s, 't> From<&'t PackageEnvironmentT<'s, 't>> for IEnvironmentT<'s, 't> {
  fn from(e: &'t PackageEnvironmentT<'s, 't>) -> Self { IEnvironmentT::Package(e) }
  
}
impl<'s, 't> From<&'t CitizenEnvironmentT<'s, 't>> for IEnvironmentT<'s, 't> {
  fn from(e: &'t CitizenEnvironmentT<'s, 't>) -> Self { IEnvironmentT::Citizen(e) }
  
}
impl<'s, 't> From<&'t FunctionEnvironmentT<'s, 't>> for IEnvironmentT<'s, 't> {
  fn from(e: &'t FunctionEnvironmentT<'s, 't>) -> Self { IEnvironmentT::Function(e) }
  
}
impl<'s, 't> From<&'t NodeEnvironmentT<'s, 't>> for IEnvironmentT<'s, 't> {
  fn from(e: &'t NodeEnvironmentT<'s, 't>) -> Self { IEnvironmentT::Node(e) }
  
}
impl<'s, 't> From<&'t BuildingFunctionEnvironmentWithClosuredsT<'s, 't>> for IEnvironmentT<'s, 't> {
  fn from(e: &'t BuildingFunctionEnvironmentWithClosuredsT<'s, 't>) -> Self {
    IEnvironmentT::BuildingWithClosureds(e)
  }
  
}
impl<'s, 't> From<&'t BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't>> for IEnvironmentT<'s, 't> {
  fn from(e: &'t BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't>) -> Self {
    IEnvironmentT::BuildingWithClosuredsAndTemplateArgs(e)
  }
  
}
impl<'s, 't> From<&'t GeneralEnvironmentT<'s, 't>> for IEnvironmentT<'s, 't> {
  fn from(e: &'t GeneralEnvironmentT<'s, 't>) -> Self { IEnvironmentT::General(e) }
  
}
impl<'s, 't> From<&'t ExportEnvironmentT<'s, 't>> for IEnvironmentT<'s, 't> {
  fn from(e: &'t ExportEnvironmentT<'s, 't>) -> Self { IEnvironmentT::Export(e) }
  
}
impl<'s, 't> From<&'t ExternEnvironmentT<'s, 't>> for IEnvironmentT<'s, 't> {
  fn from(e: &'t ExternEnvironmentT<'s, 't>) -> Self { IEnvironmentT::Extern(e) }
  
}

// Concrete → IInDenizenEnvironmentT (8 variants; no Package)
impl<'s, 't> From<&'t CitizenEnvironmentT<'s, 't>> for IInDenizenEnvironmentT<'s, 't> {
  fn from(e: &'t CitizenEnvironmentT<'s, 't>) -> Self { IInDenizenEnvironmentT::Citizen(e) }
  
}
impl<'s, 't> From<&'t FunctionEnvironmentT<'s, 't>> for IInDenizenEnvironmentT<'s, 't> {
  fn from(e: &'t FunctionEnvironmentT<'s, 't>) -> Self { IInDenizenEnvironmentT::Function(e) }
  
}
impl<'s, 't> From<&'t NodeEnvironmentT<'s, 't>> for IInDenizenEnvironmentT<'s, 't> {
  fn from(e: &'t NodeEnvironmentT<'s, 't>) -> Self { IInDenizenEnvironmentT::Node(e) }
  
}
impl<'s, 't> From<&'t BuildingFunctionEnvironmentWithClosuredsT<'s, 't>> for IInDenizenEnvironmentT<'s, 't> {
  fn from(e: &'t BuildingFunctionEnvironmentWithClosuredsT<'s, 't>) -> Self {
    IInDenizenEnvironmentT::BuildingWithClosureds(e)
  }
  
}
impl<'s, 't> From<&'t BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't>> for IInDenizenEnvironmentT<'s, 't> {
  fn from(e: &'t BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't>) -> Self {
    IInDenizenEnvironmentT::BuildingWithClosuredsAndTemplateArgs(e)
  }
  
}
impl<'s, 't> From<&'t GeneralEnvironmentT<'s, 't>> for IInDenizenEnvironmentT<'s, 't> {
  fn from(e: &'t GeneralEnvironmentT<'s, 't>) -> Self { IInDenizenEnvironmentT::General(e) }
  
}
impl<'s, 't> From<&'t ExportEnvironmentT<'s, 't>> for IInDenizenEnvironmentT<'s, 't> {
  fn from(e: &'t ExportEnvironmentT<'s, 't>) -> Self { IInDenizenEnvironmentT::Export(e) }
  
}
impl<'s, 't> From<&'t ExternEnvironmentT<'s, 't>> for IInDenizenEnvironmentT<'s, 't> {
  fn from(e: &'t ExternEnvironmentT<'s, 't>) -> Self { IInDenizenEnvironmentT::Extern(e) }
  
}

// Widening: IInDenizenEnvironmentT → IEnvironmentT (always succeeds)
impl<'s, 't> From<IInDenizenEnvironmentT<'s, 't>> for IEnvironmentT<'s, 't> {
  fn from(e: IInDenizenEnvironmentT<'s, 't>) -> Self {
    match e {
      IInDenizenEnvironmentT::Citizen(c) => IEnvironmentT::Citizen(c),
      IInDenizenEnvironmentT::Function(f) => IEnvironmentT::Function(f),
      IInDenizenEnvironmentT::Node(n) => IEnvironmentT::Node(n),
      IInDenizenEnvironmentT::BuildingWithClosureds(b) => IEnvironmentT::BuildingWithClosureds(b),
      IInDenizenEnvironmentT::BuildingWithClosuredsAndTemplateArgs(b) =>
        IEnvironmentT::BuildingWithClosuredsAndTemplateArgs(b),
      IInDenizenEnvironmentT::General(g) => IEnvironmentT::General(g),
      IInDenizenEnvironmentT::Export(e) => IEnvironmentT::Export(e),
      IInDenizenEnvironmentT::Extern(e) => IEnvironmentT::Extern(e),
    }
  }
  
}

// Narrowing: IEnvironmentT → IInDenizenEnvironmentT (errors only on Package)
impl<'s, 't> TryFrom<IEnvironmentT<'s, 't>> for IInDenizenEnvironmentT<'s, 't> {
  type Error = IEnvironmentT<'s, 't>;
  fn try_from(e: IEnvironmentT<'s, 't>) -> Result<Self, Self::Error> {
    match e {
      IEnvironmentT::Citizen(c) => Ok(IInDenizenEnvironmentT::Citizen(c)),
      IEnvironmentT::Function(f) => Ok(IInDenizenEnvironmentT::Function(f)),
      IEnvironmentT::Node(n) => Ok(IInDenizenEnvironmentT::Node(n)),
      IEnvironmentT::BuildingWithClosureds(b) => Ok(IInDenizenEnvironmentT::BuildingWithClosureds(b)),
      IEnvironmentT::BuildingWithClosuredsAndTemplateArgs(b) =>
        Ok(IInDenizenEnvironmentT::BuildingWithClosuredsAndTemplateArgs(b)),
      IEnvironmentT::General(g) => Ok(IInDenizenEnvironmentT::General(g)),
      IEnvironmentT::Export(e) => Ok(IInDenizenEnvironmentT::Export(e)),
      IEnvironmentT::Extern(e) => Ok(IInDenizenEnvironmentT::Extern(e)),
      other @ IEnvironmentT::Package(_) => Err(other),
    }
  }
  
}

// ============================================================================
// Builders — one per env kind. Each owns heap Vec/HashMap for incrementally
// built fields (templatas + slices), then freezes via build_in(interner) into
// an arena-allocated &'t FooEnvironmentT.
// ============================================================================

/// Temporary state (see @TFITCX)
pub struct PackageEnvironmentBuilder<'s, 't>
where 's: 't,
{
  pub global_env: &'t GlobalEnvironmentT<'s, 't>,
  pub id: IdT<'s, 't>,
  pub global_namespaces: Vec<&'t TemplatasStoreT<'s, 't>>,
}

impl<'s, 't> PackageEnvironmentBuilder<'s, 't>
where 's: 't,
{
  pub fn build_in(
    self,
    interner: &TypingInterner<'s, 't>,
  ) -> &'t PackageEnvironmentT<'s, 't> {
    let global_namespaces = interner.alloc_slice_from_vec(self.global_namespaces);
    interner.alloc(PackageEnvironmentT {
      global_env: self.global_env,
      id: self.id,
      global_namespaces,
    })
  }
  
}

/// Temporary state (see @TFITCX)
pub struct CitizenEnvironmentBuilder<'s, 't>
where 's: 't,
{
  pub global_env: &'t GlobalEnvironmentT<'s, 't>,
  pub parent_env: IEnvironmentT<'s, 't>,
  pub template_id: IdT<'s, 't>,
  pub id: IdT<'s, 't>,
  pub templatas_builder: TemplatasStoreBuilder<'s, 't>,
}

impl<'s, 't> CitizenEnvironmentBuilder<'s, 't>
where 's: 't,
{
  pub fn build_in(
    self,
    interner: &TypingInterner<'s, 't>,
  ) -> &'t CitizenEnvironmentT<'s, 't> {
    let templatas = self.templatas_builder.build_in(interner);
    interner.alloc(CitizenEnvironmentT {
      global_env: self.global_env,
      parent_env: self.parent_env,
      template_id: self.template_id,
      id: self.id,
      templatas,
    })
  }
  
}

/// Temporary state (see @TFITCX)
pub struct ExportEnvironmentBuilder<'s, 't>
where 's: 't,
{
  pub global_env: &'t GlobalEnvironmentT<'s, 't>,
  pub parent_env: &'t PackageEnvironmentT<'s, 't>,
  pub template_id: IdT<'s, 't>,
  pub id: IdT<'s, 't>,
  pub templatas_builder: TemplatasStoreBuilder<'s, 't>,
}

impl<'s, 't> ExportEnvironmentBuilder<'s, 't>
where 's: 't,
{
  pub fn build_in(
    self,
    interner: &TypingInterner<'s, 't>,
  ) -> &'t ExportEnvironmentT<'s, 't> {
    let templatas = self.templatas_builder.build_in(interner);
    interner.alloc(ExportEnvironmentT {
      global_env: self.global_env,
      parent_env: self.parent_env,
      template_id: self.template_id,
      id: self.id,
      templatas,
    })
  }
  
}

/// Temporary state (see @TFITCX)
pub struct ExternEnvironmentBuilder<'s, 't>
where 's: 't,
{
  pub global_env: &'t GlobalEnvironmentT<'s, 't>,
  pub parent_env: &'t PackageEnvironmentT<'s, 't>,
  pub template_id: IdT<'s, 't>,
  pub id: IdT<'s, 't>,
  pub templatas_builder: TemplatasStoreBuilder<'s, 't>,
}

impl<'s, 't> ExternEnvironmentBuilder<'s, 't>
where 's: 't,
{
  pub fn build_in(
    self,
    interner: &TypingInterner<'s, 't>,
  ) -> &'t ExternEnvironmentT<'s, 't> {
    let templatas = self.templatas_builder.build_in(interner);
    interner.alloc(ExternEnvironmentT {
      global_env: self.global_env,
      parent_env: self.parent_env,
      template_id: self.template_id,
      id: self.id,
      templatas,
    })
  }
  
}

/// Temporary state (see @TFITCX)
pub struct GeneralEnvironmentBuilder<'s, 't>
where 's: 't,
{
  pub global_env: &'t GlobalEnvironmentT<'s, 't>,
  pub parent_env: IInDenizenEnvironmentT<'s, 't>,
  pub template_id: IdT<'s, 't>,
  pub id: IdT<'s, 't>,
  pub templatas_builder: TemplatasStoreBuilder<'s, 't>,
}

impl<'s, 't> GeneralEnvironmentBuilder<'s, 't>
where 's: 't,
{
  pub fn build_in(
    self,
    interner: &TypingInterner<'s, 't>,
  ) -> &'t GeneralEnvironmentT<'s, 't> {
    let templatas = self.templatas_builder.build_in(interner);
    interner.alloc(GeneralEnvironmentT {
      global_env: self.global_env,
      parent_env: self.parent_env,
      template_id: self.template_id,
      id: self.id,
      templatas,
    })
  }
  
}