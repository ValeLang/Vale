use crate::interner::StrI;
use crate::scout_arena::ScoutArena;
use crate::postparsing::ast::{LocationInDenizen, LocationInDenizenVal};
use crate::utils::code_hierarchy::PackageCoordinate;
use crate::utils::range::{CodeLocationS, RangeS};
use IRuneValS::*;
use std::hash::Hash;
use std::hash::Hasher;
use std::ptr::eq;

/// Canonical interned name. Storage uses arena-backed refs; use `ptr_eq` for identity.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum INameS<'s> {
  FunctionDeclaration(&'s IFunctionDeclarationNameS<'s>),
  ImplDeclaration(&'s ImplDeclarationNameS<'s>),
  AnonymousSubstructImplDeclaration(&'s AnonymousSubstructImplDeclarationNameS<'s>),
  ExportAsName(&'s ExportAsNameS<'s>),
  LetName(&'s LetNameS<'s>),
  TopLevelStructDeclaration(&'s TopLevelStructDeclarationNameS<'s>),
  TopLevelInterfaceDeclaration(&'s TopLevelInterfaceDeclarationNameS<'s>),
  LambdaStructDeclaration(&'s LambdaStructDeclarationNameS<'s>),
  AnonymousSubstructTemplateName(&'s AnonymousSubstructTemplateNameS<'s>),
  RuneName(&'s RuneNameS<'s>),
  RuntimeSizedArrayDeclarationName(&'s RuntimeSizedArrayDeclarationNameS),
  StaticSizedArrayDeclarationName(&'s StaticSizedArrayDeclarationNameS),
  GlobalFunctionFamilyName(&'s GlobalFunctionFamilyNameS<'s>),
  ArbitraryName(&'s ArbitraryNameS),
  VarName(&'s IVarNameS<'s>),
}

impl<'s> INameS<'s> {
  /// Pointer to the canonical interned payload.
  pub fn canonical_ptr(&self) -> *const () {
    match self {
      INameS::FunctionDeclaration(r) => *r as *const _ as *const (),
      INameS::ImplDeclaration(r) => *r as *const _ as *const (),
      INameS::AnonymousSubstructImplDeclaration(r) => *r as *const _ as *const (),
      INameS::ExportAsName(r) => *r as *const _ as *const (),
      INameS::LetName(r) => *r as *const _ as *const (),
      INameS::TopLevelStructDeclaration(r) => *r as *const _ as *const (),
      INameS::TopLevelInterfaceDeclaration(r) => *r as *const _ as *const (),
      INameS::LambdaStructDeclaration(r) => *r as *const _ as *const (),
      INameS::AnonymousSubstructTemplateName(r) => *r as *const _ as *const (),
      INameS::RuneName(r) => *r as *const _ as *const (),
      INameS::RuntimeSizedArrayDeclarationName(r) => *r as *const _ as *const (),
      INameS::StaticSizedArrayDeclarationName(r) => *r as *const _ as *const (),
      INameS::GlobalFunctionFamilyName(r) => *r as *const _ as *const (),
      INameS::ArbitraryName(r) => *r as *const _ as *const (),
      INameS::VarName(r) => *r as *const _ as *const (),
    }
  }
  
  /// Returns true iff both refer to the same canonical interned value.
  #[inline(always)]
  pub fn ptr_eq(&self, other: &INameS<'s>) -> bool {
    eq(self.canonical_ptr(), other.canonical_ptr())
  }
  
  pub fn as_top_level_citizen_name(&self) -> Option<TopLevelCitizenDeclarationNameS<'s>> {
    match self {
      INameS::TopLevelStructDeclaration(s) => Some(TopLevelCitizenDeclarationNameS::TopLevelStructDeclarationName((*s).clone())),
      INameS::TopLevelInterfaceDeclaration(i) => Some(TopLevelCitizenDeclarationNameS::TopLevelInterfaceDeclarationName((*i).clone())),
      _ => None,
    }
  }
  
}

/// Value/key form for interner lookups. Shallow Val structs reference canonical INameS/IFunctionDeclarationNameS/etc.
/// Per @DSAUIMZ, if a variant gains a slice field, add a 'tmp lifetime and use a transient ValS struct.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum INameValS<'s> {
  FunctionDeclaration(IFunctionDeclarationNameValS<'s>),
  ImplDeclaration(ImplDeclarationNameS<'s>),
  AnonymousSubstructImplDeclaration(AnonymousSubstructImplDeclarationNameValS<'s>),
  ExportAsName(ExportAsNameS<'s>),
  LetName(LetNameS<'s>),
  TopLevelStructDeclaration(TopLevelStructDeclarationNameS<'s>),
  TopLevelInterfaceDeclaration(TopLevelInterfaceDeclarationNameS<'s>),
  LambdaStructDeclaration(LambdaStructDeclarationNameS<'s>),
  AnonymousSubstructTemplateName(AnonymousSubstructTemplateNameValS<'s>),
  RuneName(RuneNameValS<'s>),
  RuntimeSizedArrayDeclarationName(RuntimeSizedArrayDeclarationNameS),
  StaticSizedArrayDeclarationName(StaticSizedArrayDeclarationNameS),
  GlobalFunctionFamilyName(GlobalFunctionFamilyNameS<'s>),
  ArbitraryName(ArbitraryNameS),
  VarName(IVarNameValS<'s>),
}

/// Shallow: inner already canonical.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructImplDeclarationNameValS<'s> {
  pub interface: &'s TopLevelInterfaceDeclarationNameS<'s>,
}

/// Shallow: interface_name already canonical.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructTemplateNameValS<'s> {
  pub interface_name: &'s TopLevelInterfaceDeclarationNameS<'s>,
}

// AFTERM: Add arcana for how these sometimes contain INameS even though
// INameS arent interned. Should be fine, but worth looking out for.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum IImpreciseNameS<'s> {
  CodeName(&'s CodeNameS<'s>),
  IterableName(&'s IterableNameS<'s>),
  IteratorName(&'s IteratorNameS<'s>),
  IterationOptionName(&'s IterationOptionNameS<'s>),
  LambdaImpreciseName(&'s LambdaImpreciseNameS),
  PlaceholderImpreciseName(&'s PlaceholderImpreciseNameS),
  LambdaStructImpreciseName(&'s LambdaStructImpreciseNameS<'s>),
  ClosureParamImpreciseName(&'s ClosureParamImpreciseNameS),
  PrototypeName(&'s PrototypeNameS),
  AnonymousSubstructTemplateImpreciseName(&'s AnonymousSubstructTemplateImpreciseNameS<'s>),
  AnonymousSubstructConstructorTemplateImpreciseName(
    &'s AnonymousSubstructConstructorTemplateImpreciseNameS<'s>,
  ),
  ImplImpreciseName(&'s ImplImpreciseNameS<'s>),
  ImplSubCitizenImpreciseName(&'s ImplSubCitizenImpreciseNameS<'s>),
  ImplSuperInterfaceImpreciseName(&'s ImplSuperInterfaceImpreciseNameS<'s>),
  SelfName(&'s SelfNameS),
  RuneName(&'s RuneNameS<'s>),
  ArbitraryName(&'s ArbitraryNameS),
}

impl<'s> IImpreciseNameS<'s> {
  /// Pointer to the canonical interned payload. Use `std::ptr::eq(a.canonical_ptr(), b.canonical_ptr())` for identity comparison.
  pub fn canonical_ptr(&self) -> *const () {
    match self {
      IImpreciseNameS::CodeName(r) => *r as *const _ as *const (),
      IImpreciseNameS::IterableName(r) => *r as *const _ as *const (),
      IImpreciseNameS::IteratorName(r) => *r as *const _ as *const (),
      IImpreciseNameS::IterationOptionName(r) => *r as *const _ as *const (),
      IImpreciseNameS::LambdaImpreciseName(r) => *r as *const _ as *const (),
      IImpreciseNameS::PlaceholderImpreciseName(r) => *r as *const _ as *const (),
      IImpreciseNameS::LambdaStructImpreciseName(r) => *r as *const _ as *const (),
      IImpreciseNameS::ClosureParamImpreciseName(r) => *r as *const _ as *const (),
      IImpreciseNameS::PrototypeName(r) => *r as *const _ as *const (),
      IImpreciseNameS::AnonymousSubstructTemplateImpreciseName(r) => *r as *const _ as *const (),
      IImpreciseNameS::AnonymousSubstructConstructorTemplateImpreciseName(r) => *r as *const _ as *const (),
      IImpreciseNameS::ImplImpreciseName(r) => *r as *const _ as *const (),
      IImpreciseNameS::ImplSubCitizenImpreciseName(r) => *r as *const _ as *const (),
      IImpreciseNameS::ImplSuperInterfaceImpreciseName(r) => *r as *const _ as *const (),
      IImpreciseNameS::SelfName(r) => *r as *const _ as *const (),
      IImpreciseNameS::RuneName(r) => *r as *const _ as *const (),
      IImpreciseNameS::ArbitraryName(r) => *r as *const _ as *const (),
    }
  }
  
  /// Returns true iff both refer to the same canonical interned value.
  #[inline(always)]
  pub fn ptr_eq(&self, other: &IImpreciseNameS<'s>) -> bool {
    eq(self.canonical_ptr(), other.canonical_ptr())
  }
  
}

/// Value-struct for LambdaStructImpreciseNameS key. Shallow: references canonical child.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct LambdaStructImpreciseNameValS<'s> {
  pub lambda_name: IImpreciseNameS<'s>,
}

/// Value-struct for AnonymousSubstructTemplateImpreciseNameS key. Shallow: references canonical child.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructTemplateImpreciseNameValS<'s> {
  pub interface_imprecise_name: IImpreciseNameS<'s>,
}

/// Value-struct for AnonymousSubstructConstructorTemplateImpreciseNameS key. Shallow: references canonical child.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructConstructorTemplateImpreciseNameValS<'s> {
  pub interface_imprecise_name: IImpreciseNameS<'s>,
}

/// Value-struct for ImplImpreciseNameS key. Shallow: references canonical children.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplImpreciseNameValS<'s> {
  pub sub_citizen_imprecise_name: IImpreciseNameS<'s>,
  pub super_interface_imprecise_name: IImpreciseNameS<'s>,
}

/// Value-struct for ImplSubCitizenImpreciseNameS key. Shallow: references canonical child.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplSubCitizenImpreciseNameValS<'s> {
  pub sub_citizen_imprecise_name: IImpreciseNameS<'s>,
}

/// Value-struct for ImplSuperInterfaceImpreciseNameS key. Shallow: references canonical child.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplSuperInterfaceImpreciseNameValS<'s> {
  pub super_interface_imprecise_name: IImpreciseNameS<'s>,
}

/// Value-struct for RuneNameS key. Shallow: references canonical child rune.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct RuneNameValS<'s> {
  pub rune: IRuneS<'s>,
}

/// Value/key form of imprecise name for interner lookups. Storage uses canonical `IImpreciseNameS<'s>`.
/// Per @DSAUIMZ, if a variant gains a slice field, add a 'tmp lifetime and use a transient ValS struct.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum IImpreciseNameValS<'s> {
  CodeName(CodeNameS<'s>),
  IterableName(IterableNameS<'s>),
  IteratorName(IteratorNameS<'s>),
  IterationOptionName(IterationOptionNameS<'s>),
  LambdaImpreciseName(LambdaImpreciseNameS),
  PlaceholderImpreciseName(PlaceholderImpreciseNameS),
  LambdaStructImpreciseName(LambdaStructImpreciseNameValS<'s>),
  ClosureParamImpreciseName(ClosureParamImpreciseNameS),
  PrototypeName(PrototypeNameS),
  AnonymousSubstructTemplateImpreciseName(AnonymousSubstructTemplateImpreciseNameValS<'s>),
  AnonymousSubstructConstructorTemplateImpreciseName(
    AnonymousSubstructConstructorTemplateImpreciseNameValS<'s>,
  ),
  ImplImpreciseName(ImplImpreciseNameValS<'s>),
  ImplSubCitizenImpreciseName(ImplSubCitizenImpreciseNameValS<'s>),
  ImplSuperInterfaceImpreciseName(ImplSuperInterfaceImpreciseNameValS<'s>),
  SelfName(SelfNameS),
  RuneName(RuneNameValS<'s>),
  ArbitraryName(ArbitraryNameS),
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum IVarNameS<'s> {
  CodeVarName(StrI<'s>),
  ConstructingMemberName(StrI<'s>),
  ClosureParamName(&'s ClosureParamNameS<'s>),
  MagicParamName(CodeLocationS<'s>),
  IterableName(RangeS<'s>),
  IteratorName(RangeS<'s>),
  IterationOptionName(RangeS<'s>),
  WhileCondResultName(RangeS<'s>),
  SelfName,
  AnonymousSubstructMemberName(i32),
}

/// Value form for interner lookups.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum IVarNameValS<'s> {
  CodeVarName(StrI<'s>),
  ConstructingMemberName(StrI<'s>),
  ClosureParamName(ClosureParamNameS<'s>),
  MagicParamName(CodeLocationS<'s>),
  IterableName(RangeS<'s>),
  IteratorName(RangeS<'s>),
  IterationOptionName(RangeS<'s>),
  WhileCondResultName(RangeS<'s>),
  SelfName,
  AnonymousSubstructMemberName(i32),
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum IFunctionDeclarationNameS<'s> {
  FunctionName(FunctionNameS<'s>),
  LambdaDeclarationName(LambdaDeclarationNameS<'s>),
  ForwarderFunctionDeclarationName(&'s ForwarderFunctionDeclarationNameS<'s>),
  ConstructorName(&'s ConstructorNameS<'s>),
  ImmConcreteDestructorName(&'s ImmConcreteDestructorNameS<'s>),
  ImmInterfaceDestructorName(&'s ImmInterfaceDestructorNameS<'s>),
}

/// Value form for interner lookups. Shallow variant holds canonical IFunctionDeclarationNameS.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum IFunctionDeclarationNameValS<'s> {
  FunctionName(FunctionNameS<'s>),
  LambdaDeclarationName(LambdaDeclarationNameS<'s>),
  ForwarderFunctionDeclarationName(ForwarderFunctionDeclarationNameValS<'s>),
  ConstructorName(ConstructorNameS<'s>),
  ImmConcreteDestructorName(ImmConcreteDestructorNameS<'s>),
  ImmInterfaceDestructorName(ImmInterfaceDestructorNameS<'s>),
}

/// Shallow: inner already canonical.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ForwarderFunctionDeclarationNameValS<'s> {
  pub inner: IFunctionDeclarationNameS<'s>,
  pub index: i32,
}

impl<'s> IFunctionDeclarationNameS<'s> {
  pub fn package_coordinate(&self) -> &'s PackageCoordinate<'s> {
    match self {
      IFunctionDeclarationNameS::FunctionName(x) => x.code_location.file.package_coord,
      IFunctionDeclarationNameS::LambdaDeclarationName(x) => x.code_location.file.package_coord,
      IFunctionDeclarationNameS::ForwarderFunctionDeclarationName(r) => r.inner.package_coordinate(),
      IFunctionDeclarationNameS::ConstructorName(r) => {
        match &r.tlcd {
          ICitizenDeclarationNameS::TopLevelStructDeclarationName(s) => s.range.begin.file.package_coord,
          ICitizenDeclarationNameS::TopLevelInterfaceDeclarationName(i) => i.range.begin.file.package_coord,
          ICitizenDeclarationNameS::AnonymousSubstructTemplateName(n) => n.interface_name.range.begin.file.package_coord,
        }
      }
      IFunctionDeclarationNameS::ImmConcreteDestructorName(r) => &r.package_coordinate,
      IFunctionDeclarationNameS::ImmInterfaceDestructorName(r) => &r.package_coordinate,
    }
  }

  /// Convert to value form for interning. Clones through refs.
  pub fn to_val(&self) -> IFunctionDeclarationNameValS<'s> {
    match self {
      IFunctionDeclarationNameS::FunctionName(x) => {
        IFunctionDeclarationNameValS::FunctionName(x.clone())
      }
      IFunctionDeclarationNameS::LambdaDeclarationName(x) => {
        IFunctionDeclarationNameValS::LambdaDeclarationName(x.clone())
      }
      IFunctionDeclarationNameS::ForwarderFunctionDeclarationName(r) => {
        IFunctionDeclarationNameValS::ForwarderFunctionDeclarationName(ForwarderFunctionDeclarationNameValS {
          inner: r.inner.clone(),
          index: r.index,
        })
      }
      IFunctionDeclarationNameS::ConstructorName(r) => {
        IFunctionDeclarationNameValS::ConstructorName((*r).clone())
      }
      IFunctionDeclarationNameS::ImmConcreteDestructorName(r) => {
        IFunctionDeclarationNameValS::ImmConcreteDestructorName((*r).clone())
      }
      IFunctionDeclarationNameS::ImmInterfaceDestructorName(r) => {
        IFunctionDeclarationNameValS::ImmInterfaceDestructorName((*r).clone())
      }
    }
  }
  
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum IImplDeclarationNameS<'s> {
  ImplDeclarationName(ImplDeclarationNameS<'s>),
  AnonymousSubstructImplDeclarationName(AnonymousSubstructImplDeclarationNameS<'s>),
}

impl<'s> IImplDeclarationNameS<'s> {
  pub fn package_coordinate(&self) -> &'s PackageCoordinate<'s> {
    match self {
      IImplDeclarationNameS::ImplDeclarationName(x) => x.code_location.file.package_coord,
      IImplDeclarationNameS::AnonymousSubstructImplDeclarationName(x) => x.interface.range.begin.file.package_coord,
    }
  }

  pub fn to_i_name_s(self, scout_arena: &ScoutArena<'s>) -> INameS<'s> {
    match self {
      IImplDeclarationNameS::ImplDeclarationName(p) => {
        scout_arena.intern_name(INameValS::ImplDeclaration(p))
      }
      IImplDeclarationNameS::AnonymousSubstructImplDeclarationName(p) => {
        let interface_ref = match scout_arena.intern_name(
          INameValS::TopLevelInterfaceDeclaration(p.interface)
        ) {
          INameS::TopLevelInterfaceDeclaration(r) => r,
          _ => unreachable!(),
        };
        scout_arena.intern_name(INameValS::AnonymousSubstructImplDeclaration(
          AnonymousSubstructImplDeclarationNameValS { interface: interface_ref }
        ))
      }
    }
  }
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum ICitizenDeclarationNameS<'s> {
  TopLevelStructDeclarationName(TopLevelStructDeclarationNameS<'s>),
  TopLevelInterfaceDeclarationName(TopLevelInterfaceDeclarationNameS<'s>),
  AnonymousSubstructTemplateName(AnonymousSubstructTemplateNameS<'s>),
}
impl<'s> From<TopLevelCitizenDeclarationNameS<'s>> for ICitizenDeclarationNameS<'s> {
  fn from(value: TopLevelCitizenDeclarationNameS<'s>) -> Self {
    match value {
      TopLevelCitizenDeclarationNameS::TopLevelStructDeclarationName(n) =>
        ICitizenDeclarationNameS::TopLevelStructDeclarationName(n),
      TopLevelCitizenDeclarationNameS::TopLevelInterfaceDeclarationName(n) =>
        ICitizenDeclarationNameS::TopLevelInterfaceDeclarationName(n),
    }
  }
}

impl<'s> From<IStructDeclarationNameS<'s>> for ICitizenDeclarationNameS<'s> {
  fn from(value: IStructDeclarationNameS<'s>) -> Self {
    match value {
      IStructDeclarationNameS::TopLevelStructDeclarationName(n) =>
        ICitizenDeclarationNameS::TopLevelStructDeclarationName(n),
      IStructDeclarationNameS::AnonymousSubstructTemplateName(n) =>
        ICitizenDeclarationNameS::AnonymousSubstructTemplateName(n),
    }
  }
}

impl<'s> IStructDeclarationNameS<'s> {
  pub fn range(&self) -> RangeS<'s> {
    match self {
      IStructDeclarationNameS::TopLevelStructDeclarationName(n) => n.range,
      IStructDeclarationNameS::AnonymousSubstructTemplateName(n) => n.interface_name.range,
    }
  }
  
  pub fn get_imprecise_name(&self, scout_arena: &ScoutArena<'s>) -> IImpreciseNameS<'s> {
    match self {
      IStructDeclarationNameS::TopLevelStructDeclarationName(n) => {
        scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: n.name }))
      }
      IStructDeclarationNameS::AnonymousSubstructTemplateName(n) => {
        let interface_imprecise_name = n.interface_name.get_imprecise_name(scout_arena);
        scout_arena.intern_imprecise_name(IImpreciseNameValS::AnonymousSubstructTemplateImpreciseName(AnonymousSubstructTemplateImpreciseNameValS { interface_imprecise_name }))
      }
    }
  }
  
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct LambdaDeclarationNameS<'s> {
  pub code_location: CodeLocationS<'s>,
}

impl<'s> LambdaDeclarationNameS<'s> {

  pub fn get_imprecise_name(&self, scout_arena: &ScoutArena<'s>) -> IImpreciseNameS<'s> {
    scout_arena.intern_imprecise_name(IImpreciseNameValS::LambdaImpreciseName(LambdaImpreciseNameS {}))
  }

}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct LambdaImpreciseNameS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct PlaceholderImpreciseNameS {
  pub index: i32,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct FunctionNameS<'s> {
  pub name: StrI<'s>,
  pub code_location: CodeLocationS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ForwarderFunctionDeclarationNameS<'s> {
  pub inner: IFunctionDeclarationNameS<'s>,
  pub index: i32,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum TopLevelCitizenDeclarationNameS<'s> {
  TopLevelStructDeclarationName(TopLevelStructDeclarationNameS<'s>),
  TopLevelInterfaceDeclarationName(TopLevelInterfaceDeclarationNameS<'s>),
}

impl<'s> TopLevelCitizenDeclarationNameS<'s> {
  pub fn name(&self) -> StrI<'s> {
    match self {
      TopLevelCitizenDeclarationNameS::TopLevelStructDeclarationName(x) => x.name,
      TopLevelCitizenDeclarationNameS::TopLevelInterfaceDeclarationName(x) => x.name,
    }
  }
  
  pub fn range(&self) -> RangeS<'s> {
    match self {
      TopLevelCitizenDeclarationNameS::TopLevelStructDeclarationName(x) => x.range,
      TopLevelCitizenDeclarationNameS::TopLevelInterfaceDeclarationName(x) => x.range,
    }
  }
  
  pub fn package_coordinate(&self) -> &'s PackageCoordinate<'s> {
    match self {
      TopLevelCitizenDeclarationNameS::TopLevelStructDeclarationName(x) => x.range.begin.file.package_coord,
      TopLevelCitizenDeclarationNameS::TopLevelInterfaceDeclarationName(x) => x.range.begin.file.package_coord,
    }
  }
  
  pub fn get_imprecise_name(&self, scout_arena: &ScoutArena<'s>) -> IImpreciseNameS<'s> {
    match self {
      TopLevelCitizenDeclarationNameS::TopLevelStructDeclarationName(x) => {
        scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: x.name }))
      }
      TopLevelCitizenDeclarationNameS::TopLevelInterfaceDeclarationName(x) => {
        scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: x.name }))
      }
    }
  }
  
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum IStructDeclarationNameS<'s> {
  TopLevelStructDeclarationName(TopLevelStructDeclarationNameS<'s>),
  AnonymousSubstructTemplateName(AnonymousSubstructTemplateNameS<'s>),
}
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct TopLevelStructDeclarationNameS<'s> {
  pub name: StrI<'s>,
  pub range: RangeS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct TopLevelInterfaceDeclarationNameS<'s> {
  pub name: StrI<'s>,
  pub range: RangeS<'s>,
}

impl<'s> TopLevelInterfaceDeclarationNameS<'s> {
  pub fn get_imprecise_name(&self, scout_arena: &ScoutArena<'s>) -> IImpreciseNameS<'s> {
    scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: self.name }))
  }
}

impl<'s> From<&TopLevelStructDeclarationNameS<'s>> for TopLevelCitizenDeclarationNameS<'s> {
  fn from(value: &TopLevelStructDeclarationNameS<'s>) -> Self {
    TopLevelCitizenDeclarationNameS::TopLevelStructDeclarationName(value.clone())
  }
}

impl<'s> From<&TopLevelInterfaceDeclarationNameS<'s>> for TopLevelCitizenDeclarationNameS<'s> {
  fn from(value: &TopLevelInterfaceDeclarationNameS<'s>) -> Self {
    TopLevelCitizenDeclarationNameS::TopLevelInterfaceDeclarationName(value.clone())
  }
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct LambdaStructDeclarationNameS<'s> {
  pub lambda_name: LambdaDeclarationNameS<'s>,
}

impl<'s> LambdaStructDeclarationNameS<'s> {

  pub fn get_imprecise_name(&self, scout_arena: &ScoutArena<'s>) -> IImpreciseNameS<'s> {
    let lambda_imprecise_name = self.lambda_name.get_imprecise_name(scout_arena);
    scout_arena.intern_imprecise_name(IImpreciseNameValS::LambdaStructImpreciseName(
      LambdaStructImpreciseNameValS {
        lambda_name: lambda_imprecise_name,
      },
    ))
  }
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct LambdaStructImpreciseNameS<'s> {
  pub lambda_name: IImpreciseNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplDeclarationNameS<'s> {
  pub code_location: CodeLocationS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructImplDeclarationNameS<'s> {
  pub interface: TopLevelInterfaceDeclarationNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ExportAsNameS<'s> {
  pub code_location: CodeLocationS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct LetNameS<'s> {
  pub code_location: CodeLocationS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ClosureParamNameS<'s> {
  pub code_location: CodeLocationS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ClosureParamImpreciseNameS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct PrototypeNameS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct MagicParamNameS<'s> {
  pub code_location: CodeLocationS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructTemplateNameS<'s> {
  pub interface_name: TopLevelInterfaceDeclarationNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructTemplateImpreciseNameS<'s> {
  pub interface_imprecise_name: IImpreciseNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructConstructorTemplateImpreciseNameS<'s> {
  pub interface_imprecise_name: IImpreciseNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructMemberNameS {
  pub index: i32,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct CodeVarNameS<'s> {
  pub name: StrI<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ConstructingMemberNameS<'s> {
  pub name: StrI<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct IterableNameS<'s> {
  pub range: RangeS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct IteratorNameS<'s> {
  pub range: RangeS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct IterationOptionNameS<'s> {
  pub range: RangeS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct WhileCondResultNameS<'s> {
  pub range: RangeS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct RuneNameS<'s> {
  pub rune: IRuneS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct RuntimeSizedArrayDeclarationNameS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct StaticSizedArrayDeclarationNameS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum IRuneS<'s> {
  CodeRune(&'s CodeRuneS<'s>),
  ImplDropCoordRune(&'s ImplDropCoordRuneS),
  ImplDropVoidRune(&'s ImplDropVoidRuneS),
  ImplicitRune(&'s ImplicitRuneS<'s>),
  PureBlockRegionRune(&'s PureBlockRegionRuneS<'s>),
  CallRegionRune(&'s CallRegionRuneS<'s>),
  CallPureMergeRegionRune(&'s CallPureMergeRegionRuneS<'s>),
  ImplicitRegionRune(&'s ImplicitRegionRuneS<'s>),
  ReachablePrototypeRune(&'s ReachablePrototypeRuneS),
  FreeOverrideStructTemplateRune(&'s FreeOverrideStructTemplateRuneS),
  FreeOverrideStructRune(&'s FreeOverrideStructRuneS),
  FreeOverrideInterfaceRune(&'s FreeOverrideInterfaceRuneS),
  LetImplicitRune(&'s LetImplicitRuneS<'s>),
  MagicParamRune(&'s MagicParamRuneS<'s>),
  MemberRune(&'s MemberRuneS),
  LocalDefaultRegionRune(&'s LocalDefaultRegionRuneS<'s>),
  DenizenDefaultRegionRune(&'s DenizenDefaultRegionRuneS<'s>),
  ExportDefaultRegionRune(&'s ExportDefaultRegionRuneS<'s>),
  ExternDefaultRegionRune(&'s ExternDefaultRegionRuneS<'s>),
  ImplicitCoercionOwnershipRune(&'s ImplicitCoercionOwnershipRuneS<'s>),
  ImplicitCoercionKindRune(&'s ImplicitCoercionKindRuneS<'s>),
  ImplicitCoercionTemplateRune(&'s ImplicitCoercionTemplateRuneS<'s>),
  ArraySizeImplicitRune(&'s ArraySizeImplicitRuneS),
  ArrayMutabilityImplicitRune(&'s ArrayMutabilityImplicitRuneS),
  ArrayVariabilityImplicitRune(&'s ArrayVariabilityImplicitRuneS),
  ReturnRune(&'s ReturnRuneS),
  StructNameRune(&'s StructNameRuneS<'s>),
  InterfaceNameRune(&'s InterfaceNameRuneS<'s>),
  SelfRune(&'s SelfRuneS),
  SelfOwnershipRune(&'s SelfOwnershipRuneS),
  SelfKindRune(&'s SelfKindRuneS),
  SelfKindTemplateRune(&'s SelfKindTemplateRuneS<'s>),
  SelfCoordRune(&'s SelfCoordRuneS),
  MacroVoidKindRune(&'s MacroVoidKindRuneS),
  MacroVoidCoordRune(&'s MacroVoidCoordRuneS),
  MacroSelfKindRune(&'s MacroSelfKindRuneS),
  MacroSelfKindTemplateRune(&'s MacroSelfKindTemplateRuneS),
  MacroSelfCoordRune(&'s MacroSelfCoordRuneS),
  ArgumentRune(&'s ArgumentRuneS),
  PatternInputRune(&'s PatternInputRuneS<'s>),
  ExplicitTemplateArgRune(&'s ExplicitTemplateArgRuneS),
  AnonymousSubstructParentInterfaceTemplateRune(
    &'s AnonymousSubstructParentInterfaceTemplateRuneS,
  ),
  AnonymousSubstructParentInterfaceKindRune(&'s AnonymousSubstructParentInterfaceKindRuneS),
  AnonymousSubstructParentInterfaceCoordRune(&'s AnonymousSubstructParentInterfaceCoordRuneS),
  AnonymousSubstructTemplateRune(&'s AnonymousSubstructTemplateRuneS),
  AnonymousSubstructKindRune(&'s AnonymousSubstructKindRuneS),
  AnonymousSubstructCoordRune(&'s AnonymousSubstructCoordRuneS),
  AnonymousSubstructVoidKindRune(&'s AnonymousSubstructVoidKindRuneS),
  AnonymousSubstructVoidCoordRune(&'s AnonymousSubstructVoidCoordRuneS),
  AnonymousSubstructMemberRune(&'s AnonymousSubstructMemberRuneS<'s>),
  AnonymousSubstructMethodSelfBorrowCoordRune(&'s AnonymousSubstructMethodSelfBorrowCoordRuneS<'s>),
  AnonymousSubstructMethodSelfOwnCoordRune(&'s AnonymousSubstructMethodSelfOwnCoordRuneS<'s>),
  AnonymousSubstructDropBoundPrototypeRune(&'s AnonymousSubstructDropBoundPrototypeRuneS<'s>),
  AnonymousSubstructDropBoundParamsListRune(&'s AnonymousSubstructDropBoundParamsListRuneS<'s>),
  AnonymousSubstructFunctionBoundPrototypeRune(&'s AnonymousSubstructFunctionBoundPrototypeRuneS<'s>),
  AnonymousSubstructFunctionBoundParamsListRune(&'s AnonymousSubstructFunctionBoundParamsListRuneS<'s>),
  AnonymousSubstructFunctionInterfaceTemplateRune(
    &'s AnonymousSubstructFunctionInterfaceTemplateRuneS<'s>,
  ),
  AnonymousSubstructFunctionInterfaceKindRune(&'s AnonymousSubstructFunctionInterfaceKindRuneS<'s>),
  AnonymousSubstructMethodInheritedRune(&'s AnonymousSubstructMethodInheritedRuneS<'s>),
  FunctorPrototypeRuneName(&'s FunctorPrototypeRuneNameS),
  FunctorParamRuneName(&'s FunctorParamRuneNameS),
  FunctorReturnRuneName(&'s FunctorReturnRuneNameS),
  DispatcherRuneFromImpl(&'s DispatcherRuneFromImplS<'s>),
  CaseRuneFromImpl(&'s CaseRuneFromImplS<'s>),
}

impl<'s> IRuneS<'s> {
  /// Pointer to the canonical interned payload. Use `std::ptr::eq(a.canonical_ptr(), b.canonical_ptr())` for identity comparison.
  pub fn canonical_ptr(&self) -> *const () {
    match self {
      IRuneS::CodeRune(r) => *r as *const _ as *const (),
      IRuneS::ImplDropCoordRune(r) => *r as *const _ as *const (),
      IRuneS::ImplDropVoidRune(r) => *r as *const _ as *const (),
      IRuneS::ImplicitRune(r) => *r as *const _ as *const (),
      IRuneS::PureBlockRegionRune(r) => *r as *const _ as *const (),
      IRuneS::CallRegionRune(r) => *r as *const _ as *const (),
      IRuneS::CallPureMergeRegionRune(r) => *r as *const _ as *const (),
      IRuneS::ImplicitRegionRune(r) => *r as *const _ as *const (),
      IRuneS::ReachablePrototypeRune(r) => *r as *const _ as *const (),
      IRuneS::FreeOverrideStructTemplateRune(r) => *r as *const _ as *const (),
      IRuneS::FreeOverrideStructRune(r) => *r as *const _ as *const (),
      IRuneS::FreeOverrideInterfaceRune(r) => *r as *const _ as *const (),
      IRuneS::LetImplicitRune(r) => *r as *const _ as *const (),
      IRuneS::MagicParamRune(r) => *r as *const _ as *const (),
      IRuneS::MemberRune(r) => *r as *const _ as *const (),
      IRuneS::LocalDefaultRegionRune(r) => *r as *const _ as *const (),
      IRuneS::DenizenDefaultRegionRune(r) => *r as *const _ as *const (),
      IRuneS::ExportDefaultRegionRune(r) => *r as *const _ as *const (),
      IRuneS::ExternDefaultRegionRune(r) => *r as *const _ as *const (),
      IRuneS::ImplicitCoercionOwnershipRune(r) => *r as *const _ as *const (),
      IRuneS::ImplicitCoercionKindRune(r) => *r as *const _ as *const (),
      IRuneS::ImplicitCoercionTemplateRune(r) => *r as *const _ as *const (),
      IRuneS::ArraySizeImplicitRune(r) => *r as *const _ as *const (),
      IRuneS::ArrayMutabilityImplicitRune(r) => *r as *const _ as *const (),
      IRuneS::ArrayVariabilityImplicitRune(r) => *r as *const _ as *const (),
      IRuneS::ReturnRune(r) => *r as *const _ as *const (),
      IRuneS::StructNameRune(r) => *r as *const _ as *const (),
      IRuneS::InterfaceNameRune(r) => *r as *const _ as *const (),
      IRuneS::SelfRune(r) => *r as *const _ as *const (),
      IRuneS::SelfOwnershipRune(r) => *r as *const _ as *const (),
      IRuneS::SelfKindRune(r) => *r as *const _ as *const (),
      IRuneS::SelfKindTemplateRune(r) => *r as *const _ as *const (),
      IRuneS::SelfCoordRune(r) => *r as *const _ as *const (),
      IRuneS::MacroVoidKindRune(r) => *r as *const _ as *const (),
      IRuneS::MacroVoidCoordRune(r) => *r as *const _ as *const (),
      IRuneS::MacroSelfKindRune(r) => *r as *const _ as *const (),
      IRuneS::MacroSelfKindTemplateRune(r) => *r as *const _ as *const (),
      IRuneS::MacroSelfCoordRune(r) => *r as *const _ as *const (),
      IRuneS::ArgumentRune(r) => *r as *const _ as *const (),
      IRuneS::PatternInputRune(r) => *r as *const _ as *const (),
      IRuneS::ExplicitTemplateArgRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructParentInterfaceTemplateRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructParentInterfaceKindRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructParentInterfaceCoordRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructTemplateRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructKindRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructCoordRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructVoidKindRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructVoidCoordRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructMemberRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructMethodSelfBorrowCoordRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructMethodSelfOwnCoordRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructDropBoundPrototypeRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructDropBoundParamsListRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructFunctionBoundPrototypeRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructFunctionBoundParamsListRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructFunctionInterfaceTemplateRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructFunctionInterfaceKindRune(r) => *r as *const _ as *const (),
      IRuneS::AnonymousSubstructMethodInheritedRune(r) => *r as *const _ as *const (),
      IRuneS::FunctorPrototypeRuneName(r) => *r as *const _ as *const (),
      IRuneS::FunctorParamRuneName(r) => *r as *const _ as *const (),
      IRuneS::FunctorReturnRuneName(r) => *r as *const _ as *const (),
      IRuneS::DispatcherRuneFromImpl(r) => *r as *const _ as *const (),
      IRuneS::CaseRuneFromImpl(r) => *r as *const _ as *const (),
    }
  }

  /// Returns true iff both refer to the same canonical interned value.
  #[inline(always)]
  pub fn ptr_eq(&self, other: &IRuneS<'s>) -> bool {
    eq(self.canonical_ptr(), other.canonical_ptr())
  }
  
}

/// Value-struct for ImplicitRegionRuneS key. Shallow: references canonical child rune.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplicitRegionRuneValS<'s> {
  pub original_rune: IRuneS<'s>,
}

/// Value-struct for ImplicitCoercionOwnershipRuneS key. Shallow: references canonical child rune.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplicitCoercionOwnershipRuneValS<'s> {
  pub range: RangeS<'s>,
  pub original_coord_rune: IRuneS<'s>,
}

/// Value-struct for ImplicitCoercionKindRuneS key. Shallow: references canonical child rune.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplicitCoercionKindRuneValS<'s> {
  pub range: RangeS<'s>,
  pub original_coord_rune: IRuneS<'s>,
}

/// Value-struct for ImplicitCoercionTemplateRuneS key. Shallow: references canonical child rune.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplicitCoercionTemplateRuneValS<'s> {
  pub range: RangeS<'s>,
  pub original_kind_rune: IRuneS<'s>,
}

/// Value-struct for AnonymousSubstructMethodInheritedRuneS key. Shallow: references canonical child rune.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructMethodInheritedRuneValS<'s> {
  pub interface: TopLevelInterfaceDeclarationNameS<'s>,
  pub method: IFunctionDeclarationNameS<'s>,
  pub inner: IRuneS<'s>,
}

/// Value-struct for DispatcherRuneFromImplS key. Shallow: references canonical child rune.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct DispatcherRuneFromImplValS<'s> {
  pub inner_rune: IRuneS<'s>,
}

/// Value-struct for CaseRuneFromImplS key. Shallow: references canonical child rune.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct CaseRuneFromImplValS<'s> {
  pub inner_rune: IRuneS<'s>,
}

// Per @DSAUIMZ, these Val structs have private lid fields to prevent pre-allocation.
// Only constructible via new() which takes a LocationInDenizenVal from borrow_val().

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplicitRuneValS<'tmp> { lid: LocationInDenizenVal<'tmp> }
impl<'tmp> ImplicitRuneValS<'tmp> { pub fn new(lid: LocationInDenizenVal<'tmp>) -> Self { Self { lid } } pub fn lid(&self) -> LocationInDenizenVal<'tmp> { self.lid } }

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct PureBlockRegionRuneValS<'tmp> { lid: LocationInDenizenVal<'tmp> }
impl<'tmp> PureBlockRegionRuneValS<'tmp> { pub fn new(lid: LocationInDenizenVal<'tmp>) -> Self { Self { lid } } pub fn lid(&self) -> LocationInDenizenVal<'tmp> { self.lid } }

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct CallRegionRuneValS<'tmp> { lid: LocationInDenizenVal<'tmp> }
impl<'tmp> CallRegionRuneValS<'tmp> { pub fn new(lid: LocationInDenizenVal<'tmp>) -> Self { Self { lid } } pub fn lid(&self) -> LocationInDenizenVal<'tmp> { self.lid } }

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct CallPureMergeRegionRuneValS<'tmp> { lid: LocationInDenizenVal<'tmp> }
impl<'tmp> CallPureMergeRegionRuneValS<'tmp> { pub fn new(lid: LocationInDenizenVal<'tmp>) -> Self { Self { lid } } pub fn lid(&self) -> LocationInDenizenVal<'tmp> { self.lid } }

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct LetImplicitRuneValS<'tmp> { lid: LocationInDenizenVal<'tmp> }
impl<'tmp> LetImplicitRuneValS<'tmp> { pub fn new(lid: LocationInDenizenVal<'tmp>) -> Self { Self { lid } } pub fn lid(&self) -> LocationInDenizenVal<'tmp> { self.lid } }

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct MagicParamRuneValS<'tmp> { lid: LocationInDenizenVal<'tmp> }
impl<'tmp> MagicParamRuneValS<'tmp> { pub fn new(lid: LocationInDenizenVal<'tmp>) -> Self { Self { lid } } pub fn lid(&self) -> LocationInDenizenVal<'tmp> { self.lid } }

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct LocalDefaultRegionRuneValS<'tmp> { lid: LocationInDenizenVal<'tmp> }
impl<'tmp> LocalDefaultRegionRuneValS<'tmp> { pub fn new(lid: LocationInDenizenVal<'tmp>) -> Self { Self { lid } } pub fn lid(&self) -> LocationInDenizenVal<'tmp> { self.lid } }

/// Per @DSAUIMZ, 'tmp carries a temporary borrow to defer slice allocation.
/// Value/key form of rune for interner lookups. Used when constructing runes before
/// canonicalizing via `intern_rune`. Storage fields use canonical `IRuneS<'s>`.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum IRuneValS<'s, 'tmp> {
  CodeRune(CodeRuneS<'s>),
  ImplDropCoordRune(ImplDropCoordRuneS),
  ImplDropVoidRune(ImplDropVoidRuneS),
  ImplicitRune(ImplicitRuneValS<'tmp>),
  PureBlockRegionRune(PureBlockRegionRuneValS<'tmp>),
  CallRegionRune(CallRegionRuneValS<'tmp>),
  CallPureMergeRegionRune(CallPureMergeRegionRuneValS<'tmp>),
  ImplicitRegionRune(ImplicitRegionRuneValS<'s>),
  ReachablePrototypeRune(ReachablePrototypeRuneS),
  FreeOverrideStructTemplateRune(FreeOverrideStructTemplateRuneS),
  FreeOverrideStructRune(FreeOverrideStructRuneS),
  FreeOverrideInterfaceRune(FreeOverrideInterfaceRuneS),
  LetImplicitRune(LetImplicitRuneValS<'tmp>),
  MagicParamRune(MagicParamRuneValS<'tmp>),
  MemberRune(MemberRuneS),
  LocalDefaultRegionRune(LocalDefaultRegionRuneValS<'tmp>),
  DenizenDefaultRegionRune(DenizenDefaultRegionRuneS<'s>),
  ExportDefaultRegionRune(ExportDefaultRegionRuneS<'s>),
  ExternDefaultRegionRune(ExternDefaultRegionRuneS<'s>),
  ImplicitCoercionOwnershipRune(ImplicitCoercionOwnershipRuneValS<'s>),
  ImplicitCoercionKindRune(ImplicitCoercionKindRuneValS<'s>),
  ImplicitCoercionTemplateRune(ImplicitCoercionTemplateRuneValS<'s>),
  ArraySizeImplicitRune(ArraySizeImplicitRuneS),
  ArrayMutabilityImplicitRune(ArrayMutabilityImplicitRuneS),
  ArrayVariabilityImplicitRune(ArrayVariabilityImplicitRuneS),
  ReturnRune(ReturnRuneS),
  StructNameRune(StructNameRuneS<'s>),
  InterfaceNameRune(InterfaceNameRuneS<'s>),
  SelfRune(SelfRuneS),
  SelfOwnershipRune(SelfOwnershipRuneS),
  SelfKindRune(SelfKindRuneS),
  SelfKindTemplateRune(SelfKindTemplateRuneS<'s>),
  SelfCoordRune(SelfCoordRuneS),
  MacroVoidKindRune(MacroVoidKindRuneS),
  MacroVoidCoordRune(MacroVoidCoordRuneS),
  MacroSelfKindRune(MacroSelfKindRuneS),
  MacroSelfKindTemplateRune(MacroSelfKindTemplateRuneS),
  MacroSelfCoordRune(MacroSelfCoordRuneS),
  ArgumentRune(ArgumentRuneS),
  PatternInputRune(PatternInputRuneS<'s>),
  ExplicitTemplateArgRune(ExplicitTemplateArgRuneS),
  AnonymousSubstructParentInterfaceTemplateRune(AnonymousSubstructParentInterfaceTemplateRuneS),
  AnonymousSubstructParentInterfaceKindRune(AnonymousSubstructParentInterfaceKindRuneS),
  AnonymousSubstructParentInterfaceCoordRune(AnonymousSubstructParentInterfaceCoordRuneS),
  AnonymousSubstructTemplateRune(AnonymousSubstructTemplateRuneS),
  AnonymousSubstructKindRune(AnonymousSubstructKindRuneS),
  AnonymousSubstructCoordRune(AnonymousSubstructCoordRuneS),
  AnonymousSubstructVoidKindRune(AnonymousSubstructVoidKindRuneS),
  AnonymousSubstructVoidCoordRune(AnonymousSubstructVoidCoordRuneS),
  AnonymousSubstructMemberRune(AnonymousSubstructMemberRuneS<'s>),
  AnonymousSubstructMethodSelfBorrowCoordRune(AnonymousSubstructMethodSelfBorrowCoordRuneS<'s>),
  AnonymousSubstructMethodSelfOwnCoordRune(AnonymousSubstructMethodSelfOwnCoordRuneS<'s>),
  AnonymousSubstructDropBoundPrototypeRune(AnonymousSubstructDropBoundPrototypeRuneS<'s>),
  AnonymousSubstructDropBoundParamsListRune(AnonymousSubstructDropBoundParamsListRuneS<'s>),
  AnonymousSubstructFunctionBoundPrototypeRune(AnonymousSubstructFunctionBoundPrototypeRuneS<'s>),
  AnonymousSubstructFunctionBoundParamsListRune(AnonymousSubstructFunctionBoundParamsListRuneS<'s>),
  AnonymousSubstructFunctionInterfaceTemplateRune(AnonymousSubstructFunctionInterfaceTemplateRuneS<'s>),
  AnonymousSubstructFunctionInterfaceKindRune(AnonymousSubstructFunctionInterfaceKindRuneS<'s>),
  AnonymousSubstructMethodInheritedRune(AnonymousSubstructMethodInheritedRuneValS<'s>),
  FunctorPrototypeRuneName(FunctorPrototypeRuneNameS),
  FunctorParamRuneName(FunctorParamRuneNameS),
  FunctorReturnRuneName(FunctorReturnRuneNameS),
  DispatcherRuneFromImpl(DispatcherRuneFromImplValS<'s>),
  CaseRuneFromImpl(CaseRuneFromImplValS<'s>),
}

/// Per @DSAUIMZ, wrapper enabling heterogeneous HashMap lookup.
///
/// The intern map stores `IRuneValS<'s, 's>` keys (both lifetimes = arena).
/// But callers build `IRuneValS<'s, 'tmp>` where 'tmp borrows a stack-local
/// builder (not the arena). We need to look up in the map using the 'tmp version.
///
/// We can't implement `Equivalent<IRuneValS<'s,'s>> for IRuneValS<'s,'tmp>` directly
/// because when 'tmp = 's, the two types are identical, and Rust's blanket impl
/// `Equivalent<K> for K` (from PartialEq) already covers that case. The orphan
/// rules see a potential overlap and reject our impl.
///
/// This wrapper is a distinct type that breaks the overlap. It holds a reference
/// to the query val and delegates Hash/Equivalent to the inner val's contents.
/// The Hash output is identical for equal values regardless of lifetime, because
/// both LocationInDenizenVal and LocationInDenizen hash by slice contents.
pub struct RuneValQuery<'a, 's, 'tmp>(pub &'a IRuneValS<'s, 'tmp>);

impl<'a, 's, 'tmp> Hash for RuneValQuery<'a, 's, 'tmp> {
  fn hash<H: Hasher>(&self, state: &mut H) { self.0.hash(state); }
}

impl<'a, 's, 'tmp> hashbrown::Equivalent<IRuneValS<'s, 's>> for RuneValQuery<'a, 's, 'tmp> {
  fn equivalent(&self, key: &IRuneValS<'s, 's>) -> bool {
    match (self.0, key) {
      // 7 lid variants: compare path contents
      (ImplicitRune(a), ImplicitRune(b)) => a.lid().path() == b.lid().path(),
      (PureBlockRegionRune(a), PureBlockRegionRune(b)) => a.lid().path() == b.lid().path(),
      (CallRegionRune(a), CallRegionRune(b)) => a.lid().path() == b.lid().path(),
      (CallPureMergeRegionRune(a), CallPureMergeRegionRune(b)) => a.lid().path() == b.lid().path(),
      (LetImplicitRune(a), LetImplicitRune(b)) => a.lid().path() == b.lid().path(),
      (MagicParamRune(a), MagicParamRune(b)) => a.lid().path() == b.lid().path(),
      (LocalDefaultRegionRune(a), LocalDefaultRegionRune(b)) => a.lid().path() == b.lid().path(),
      // All other variants: same inner type on both sides, delegate to PartialEq
      (CodeRune(a), CodeRune(b)) => a == b,
      (ImplDropCoordRune(a), ImplDropCoordRune(b)) => a == b,
      (ImplDropVoidRune(a), ImplDropVoidRune(b)) => a == b,
      (ImplicitRegionRune(a), ImplicitRegionRune(b)) => a == b,
      (ReachablePrototypeRune(a), ReachablePrototypeRune(b)) => a == b,
      (FreeOverrideStructTemplateRune(a), FreeOverrideStructTemplateRune(b)) => a == b,
      (FreeOverrideStructRune(a), FreeOverrideStructRune(b)) => a == b,
      (FreeOverrideInterfaceRune(a), FreeOverrideInterfaceRune(b)) => a == b,
      (MemberRune(a), MemberRune(b)) => a == b,
      (DenizenDefaultRegionRune(a), DenizenDefaultRegionRune(b)) => a == b,
      (ExportDefaultRegionRune(a), ExportDefaultRegionRune(b)) => a == b,
      (ExternDefaultRegionRune(a), ExternDefaultRegionRune(b)) => a == b,
      (ImplicitCoercionOwnershipRune(a), ImplicitCoercionOwnershipRune(b)) => a == b,
      (ImplicitCoercionKindRune(a), ImplicitCoercionKindRune(b)) => a == b,
      (ImplicitCoercionTemplateRune(a), ImplicitCoercionTemplateRune(b)) => a == b,
      (ArraySizeImplicitRune(a), ArraySizeImplicitRune(b)) => a == b,
      (ArrayMutabilityImplicitRune(a), ArrayMutabilityImplicitRune(b)) => a == b,
      (ArrayVariabilityImplicitRune(a), ArrayVariabilityImplicitRune(b)) => a == b,
      (ReturnRune(a), ReturnRune(b)) => a == b,
      (StructNameRune(a), StructNameRune(b)) => a == b,
      (InterfaceNameRune(a), InterfaceNameRune(b)) => a == b,
      (SelfRune(a), SelfRune(b)) => a == b,
      (SelfOwnershipRune(a), SelfOwnershipRune(b)) => a == b,
      (SelfKindRune(a), SelfKindRune(b)) => a == b,
      (SelfKindTemplateRune(a), SelfKindTemplateRune(b)) => a == b,
      (SelfCoordRune(a), SelfCoordRune(b)) => a == b,
      (MacroVoidKindRune(a), MacroVoidKindRune(b)) => a == b,
      (MacroVoidCoordRune(a), MacroVoidCoordRune(b)) => a == b,
      (MacroSelfKindRune(a), MacroSelfKindRune(b)) => a == b,
      (MacroSelfKindTemplateRune(a), MacroSelfKindTemplateRune(b)) => a == b,
      (MacroSelfCoordRune(a), MacroSelfCoordRune(b)) => a == b,
      (ArgumentRune(a), ArgumentRune(b)) => a == b,
      (PatternInputRune(a), PatternInputRune(b)) => a == b,
      (ExplicitTemplateArgRune(a), ExplicitTemplateArgRune(b)) => a == b,
      (AnonymousSubstructParentInterfaceTemplateRune(a), AnonymousSubstructParentInterfaceTemplateRune(b)) => a == b,
      (AnonymousSubstructParentInterfaceKindRune(a), AnonymousSubstructParentInterfaceKindRune(b)) => a == b,
      (AnonymousSubstructParentInterfaceCoordRune(a), AnonymousSubstructParentInterfaceCoordRune(b)) => a == b,
      (AnonymousSubstructTemplateRune(a), AnonymousSubstructTemplateRune(b)) => a == b,
      (AnonymousSubstructKindRune(a), AnonymousSubstructKindRune(b)) => a == b,
      (AnonymousSubstructCoordRune(a), AnonymousSubstructCoordRune(b)) => a == b,
      (AnonymousSubstructVoidKindRune(a), AnonymousSubstructVoidKindRune(b)) => a == b,
      (AnonymousSubstructVoidCoordRune(a), AnonymousSubstructVoidCoordRune(b)) => a == b,
      (AnonymousSubstructMemberRune(a), AnonymousSubstructMemberRune(b)) => a == b,
      (AnonymousSubstructMethodSelfBorrowCoordRune(a), AnonymousSubstructMethodSelfBorrowCoordRune(b)) => a == b,
      (AnonymousSubstructMethodSelfOwnCoordRune(a), AnonymousSubstructMethodSelfOwnCoordRune(b)) => a == b,
      (AnonymousSubstructDropBoundPrototypeRune(a), AnonymousSubstructDropBoundPrototypeRune(b)) => a == b,
      (AnonymousSubstructDropBoundParamsListRune(a), AnonymousSubstructDropBoundParamsListRune(b)) => a == b,
      (AnonymousSubstructFunctionBoundPrototypeRune(a), AnonymousSubstructFunctionBoundPrototypeRune(b)) => a == b,
      (AnonymousSubstructFunctionBoundParamsListRune(a), AnonymousSubstructFunctionBoundParamsListRune(b)) => a == b,
      (AnonymousSubstructFunctionInterfaceTemplateRune(a), AnonymousSubstructFunctionInterfaceTemplateRune(b)) => a == b,
      (AnonymousSubstructFunctionInterfaceKindRune(a), AnonymousSubstructFunctionInterfaceKindRune(b)) => a == b,
      (AnonymousSubstructMethodInheritedRune(a), AnonymousSubstructMethodInheritedRune(b)) => a == b,
      (FunctorPrototypeRuneName(a), FunctorPrototypeRuneName(b)) => a == b,
      (FunctorParamRuneName(a), FunctorParamRuneName(b)) => a == b,
      (FunctorReturnRuneName(a), FunctorReturnRuneName(b)) => a == b,
      (DispatcherRuneFromImpl(a), DispatcherRuneFromImpl(b)) => a == b,
      (CaseRuneFromImpl(a), CaseRuneFromImpl(b)) => a == b,
      _ => false,
    }
  }
  
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct CodeRuneS<'s> {
  pub name: StrI<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplDropCoordRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplDropVoidRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplicitRuneS<'s> {
  pub lid: LocationInDenizen<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct PureBlockRegionRuneS<'s> {
  pub lid: LocationInDenizen<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct CallRegionRuneS<'s> {
  pub lid: LocationInDenizen<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct CallPureMergeRegionRuneS<'s> {
  pub lid: LocationInDenizen<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplicitRegionRuneS<'s> {
  pub original_rune: IRuneS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ReachablePrototypeRuneS {
  pub num: i32,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct FreeOverrideStructTemplateRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct FreeOverrideStructRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct FreeOverrideInterfaceRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct LetImplicitRuneS<'s> {
  pub lid: LocationInDenizen<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct MagicParamRuneS<'s> {
  pub lid: LocationInDenizen<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct MemberRuneS {
  pub member_index: i32,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct LocalDefaultRegionRuneS<'s> {
  pub lid: LocationInDenizen<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct DenizenDefaultRegionRuneS<'s> {
  pub denizen_name: INameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ExportDefaultRegionRuneS<'s> {
  pub denizen_name: INameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ExternDefaultRegionRuneS<'s> {
  pub denizen_name: INameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplicitCoercionOwnershipRuneS<'s> {
  pub range: RangeS<'s>,
  pub original_coord_rune: IRuneS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplicitCoercionKindRuneS<'s> {
  pub range: RangeS<'s>,
  pub original_coord_rune: IRuneS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplicitCoercionTemplateRuneS<'s> {
  pub range: RangeS<'s>,
  pub original_kind_rune: IRuneS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ArraySizeImplicitRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ArrayMutabilityImplicitRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ArrayVariabilityImplicitRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ReturnRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct StructNameRuneS<'s> {
  pub struct_name: ICitizenDeclarationNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct InterfaceNameRuneS<'s> {
  pub interface_name: ICitizenDeclarationNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct SelfRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct SelfOwnershipRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct SelfKindRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct SelfKindTemplateRuneS<'s> {
  pub loc: CodeLocationS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct SelfCoordRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct MacroVoidKindRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct MacroVoidCoordRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct MacroSelfKindRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct MacroSelfKindTemplateRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct MacroSelfCoordRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct CodeNameS<'s> {
  pub name: StrI<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct GlobalFunctionFamilyNameS<'s> {
  pub name: StrI<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ArgumentRuneS {
  pub arg_index: i32,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct PatternInputRuneS<'s> {
  pub code_loc: CodeLocationS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ExplicitTemplateArgRuneS {
  pub index: i32,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructParentInterfaceTemplateRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructParentInterfaceKindRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructParentInterfaceCoordRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructTemplateRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructKindRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructCoordRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructVoidKindRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructVoidCoordRuneS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructMemberRuneS<'s> {
  pub interface: TopLevelInterfaceDeclarationNameS<'s>,
  pub method: IFunctionDeclarationNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructMethodSelfBorrowCoordRuneS<'s> {
  pub interface: TopLevelInterfaceDeclarationNameS<'s>,
  pub method: IFunctionDeclarationNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructMethodSelfOwnCoordRuneS<'s> {
  pub interface: TopLevelInterfaceDeclarationNameS<'s>,
  pub method: IFunctionDeclarationNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructDropBoundPrototypeRuneS<'s> {
  pub interface: TopLevelInterfaceDeclarationNameS<'s>,
  pub method: IFunctionDeclarationNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructDropBoundParamsListRuneS<'s> {
  pub interface: TopLevelInterfaceDeclarationNameS<'s>,
  pub method: IFunctionDeclarationNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructFunctionBoundPrototypeRuneS<'s> {
  pub interface: TopLevelInterfaceDeclarationNameS<'s>,
  pub method: IFunctionDeclarationNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructFunctionBoundParamsListRuneS<'s> {
  pub interface: TopLevelInterfaceDeclarationNameS<'s>,
  pub method: IFunctionDeclarationNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructFunctionInterfaceTemplateRuneS<'s> {
  pub interface: TopLevelInterfaceDeclarationNameS<'s>,
  pub method: IFunctionDeclarationNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructFunctionInterfaceKindRuneS<'s> {
  pub interface: TopLevelInterfaceDeclarationNameS<'s>,
  pub method: IFunctionDeclarationNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct AnonymousSubstructMethodInheritedRuneS<'s> {
  pub interface: TopLevelInterfaceDeclarationNameS<'s>,
  pub method: IFunctionDeclarationNameS<'s>,
  pub inner: IRuneS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct FunctorPrototypeRuneNameS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct FunctorParamRuneNameS {
  pub index: i32,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct FunctorReturnRuneNameS {}

// Vale has no notion of Self, it's just a convenient name for a first parameter.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct SelfNameS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ArbitraryNameS {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct DispatcherRuneFromImplS<'s> {
  pub inner_rune: IRuneS<'s>,
}

// Only made by typingpass, see if we can take these out
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct CaseRuneFromImplS<'s> {
  pub inner_rune: IRuneS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ConstructorNameS<'s> {
  pub tlcd: ICitizenDeclarationNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImmConcreteDestructorNameS<'s> {
  pub package_coordinate: PackageCoordinate<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImmInterfaceDestructorNameS<'s> {
  pub package_coordinate: PackageCoordinate<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplImpreciseNameS<'s> {
  pub sub_citizen_imprecise_name: IImpreciseNameS<'s>,
  pub super_interface_imprecise_name: IImpreciseNameS<'s>,
}
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplSubCitizenImpreciseNameS<'s> {
  pub sub_citizen_imprecise_name: IImpreciseNameS<'s>,
}
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplSuperInterfaceImpreciseNameS<'s> {
  pub super_interface_imprecise_name: IImpreciseNameS<'s>,
}
