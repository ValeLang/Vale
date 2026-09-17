use super::expressions::BlockPE;
use super::pattern::ParameterP;
use super::rules::{IRulexPR, ITypePR};
use super::templex::{ITemplexPT, RegionRunePT};
use crate::StrI;
use crate::lexing::RangeL;
use crate::utils::code_hierarchy::FileCoordinate;

/// Something that exists in the source code. An Option[UnitP] is better than a boolean
/// because it also contains the range it was found.
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct UnitP {
  pub range: RangeL,
}

/// Name in source code
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct NameP<'p>(pub RangeL, pub StrI<'p>);

impl<'p> NameP<'p> {
  pub fn range(&self) -> RangeL {
    self.0
  }

  pub fn str(&self) -> StrI<'p> {
    self.1
  }

  /// Returns the underlying string slice.
  pub fn as_str(&self) -> &'p str {
    self.1.as_str()
  }
}

/// Parsed file
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct FileP<'p> {
  pub file_coord: &'p FileCoordinate<'p>,
  pub comments_ranges: &'p [RangeL],
  pub denizens: &'p [IDenizenP<'p>],
}

#[derive(Debug, PartialEq)]
pub enum IDenizenP<'p> {
  TopLevelFunction(FunctionP<'p>),
  TopLevelStruct(StructP<'p>),
  TopLevelInterface(InterfaceP<'p>),
  TopLevelImpl(ImplP<'p>),
  TopLevelExportAs(ExportAsP<'p>),
  TopLevelImport(ImportP<'p>),
}

#[derive(Debug, PartialEq)]
pub struct ImplP<'p> {
  pub range: RangeL,
  pub generic_params: Option<GenericParametersP<'p>>,
  pub template_rules: Option<TemplateRulesP<'p>>,
  // Option because we can say `impl MyInterface;` inside a struct.
  pub struct_: Option<ITemplexPT<'p>>,
  pub interface: ITemplexPT<'p>,
  pub attributes: &'p [IAttributeP<'p>],
}

#[derive(Debug, PartialEq)]
pub struct ExportAsP<'p> {
  pub range: RangeL,
  pub struct_: ITemplexPT<'p>,
  pub exported_name: NameP<'p>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct ImportP<'p> {
  pub range: RangeL,
  pub module_name: NameP<'p>,
  pub package_steps: &'p [NameP<'p>],
  pub importee_name: NameP<'p>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct WeakableAttributeP {
  pub range: RangeL,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct SealedAttributeP {
  pub range: RangeL,
}

impl IRuneAttributeP {
  pub fn range(&self) -> RangeL {
    match self {
      IRuneAttributeP::ImmutableRuneAttribute(r) => *r,
      IRuneAttributeP::MutableRuneAttribute(r) => *r,
      IRuneAttributeP::ReadOnlyRegionRuneAttribute(r) => *r,
      IRuneAttributeP::ReadWriteRegionRuneAttribute(r) => *r,
      IRuneAttributeP::ImmutableRegionRuneAttribute(r) => *r,
      IRuneAttributeP::AdditiveRegionRuneAttribute(r) => *r,
      IRuneAttributeP::PoolRuneAttribute(r) => *r,
      IRuneAttributeP::ArenaRuneAttribute(r) => *r,
      IRuneAttributeP::BumpRuneAttribute(r) => *r,
    }
  }
}

#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum IMacroInclusionP {
  CallMacro,
  DontCallMacro,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct MacroCallP<'p> {
  pub range: RangeL,
  pub inclusion: IMacroInclusionP,
  pub name: NameP<'p>,
}

#[derive(Debug, PartialEq)]
pub struct StructP<'p> {
  pub range: RangeL,
  pub name: NameP<'p>,
  pub attributes: &'p [IAttributeP<'p>],
  pub mutability: Option<ITemplexPT<'p>>,
  pub identifying_runes: Option<GenericParametersP<'p>>,
  pub template_rules: Option<TemplateRulesP<'p>>,
  pub maybe_default_region_rune: Option<RegionRunePT<'p>>,
  pub body_range: RangeL,
  pub members: StructMembersP<'p>,
}

#[derive(Debug, PartialEq)]
pub struct StructMembersP<'p> {
  pub range: RangeL,
  pub contents: &'p [IStructContent<'p>],
}

#[derive(Debug, PartialEq)]
pub enum IStructContent<'p> {
  StructMethod(FunctionP<'p>),
  NormalStructMember(NormalStructMemberP<'p>),
  VariadicStructMember(VariadicStructMemberP<'p>),
}
#[derive(Debug, PartialEq)]
pub struct NormalStructMemberP<'p> {
  pub range: RangeL,
  pub name: NameP<'p>,
  pub variability: VariabilityP,
  pub tyype: ITemplexPT<'p>,
}
#[derive(Debug, PartialEq)]
pub struct VariadicStructMemberP<'p> {
  pub range: RangeL,
  pub variability: VariabilityP,
  pub tyype: ITemplexPT<'p>,
}

#[derive(Debug, PartialEq)]
pub struct InterfaceP<'p> {
  pub range: RangeL,
  pub name: NameP<'p>,
  pub attributes: &'p [IAttributeP<'p>],
  pub mutability: Option<ITemplexPT<'p>>,
  pub maybe_identifying_runes: Option<GenericParametersP<'p>>,
  pub template_rules: Option<TemplateRulesP<'p>>,
  pub maybe_default_region_rune: Option<RegionRunePT<'p>>,
  pub body_range: RangeL,
  pub members: &'p [FunctionP<'p>],
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub enum IAttributeP<'p> {
  WeakableAttribute(WeakableAttributeP),
  SealedAttribute(SealedAttributeP),
  MacroCall(MacroCallP<'p>),
  AbstractAttribute(AbstractAttributeP),
  ExternAttribute(ExternAttributeP),
  BuiltinAttribute(BuiltinAttributeP<'p>),
  ExportAttribute(ExportAttributeP),
  PureAttribute(PureAttributeP),
  AdditiveAttribute(AdditiveAttributeP),
  LinearAttribute(LinearAttributeP),
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct AbstractAttributeP {
  pub range: RangeL,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct ExternAttributeP {
  pub range: RangeL,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct BuiltinAttributeP<'p> {
  pub range: RangeL,
  pub generator_name: NameP<'p>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct ExportAttributeP {
  pub range: RangeL,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct PureAttributeP {
  pub range: RangeL,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct AdditiveAttributeP {
  pub range: RangeL,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct LinearAttributeP {
  pub range: RangeL,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub enum IRuneAttributeP {
  ImmutableRuneAttribute(RangeL),
  MutableRuneAttribute(RangeL),
  ReadOnlyRegionRuneAttribute(RangeL),
  ReadWriteRegionRuneAttribute(RangeL),
  ImmutableRegionRuneAttribute(RangeL),
  AdditiveRegionRuneAttribute(RangeL),
  PoolRuneAttribute(RangeL),
  ArenaRuneAttribute(RangeL),
  BumpRuneAttribute(RangeL),
}

#[derive(Debug, PartialEq)]
pub struct GenericParameterP<'p> {
  pub range: RangeL,
  pub name: NameP<'p>,
  pub maybe_type: Option<GenericParameterTypeP>,
  pub coord_region: Option<RegionRunePT<'p>>,
  pub attributes: &'p [IRuneAttributeP],
  pub maybe_default: Option<ITemplexPT<'p>>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct GenericParameterTypeP {
  pub range: RangeL,
  pub tyype: ITypePR,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct GenericParametersP<'p> {
  pub range: RangeL,
  pub params: &'p [GenericParameterP<'p>],
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct TemplateRulesP<'p> {
  pub range: RangeL,
  pub rules: &'p [IRulexPR<'p>],
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct ParamsP<'p> {
  pub range: RangeL,
  pub params: &'p [ParameterP<'p>],
}

#[derive(Debug, PartialEq)]
pub struct FunctionP<'p> {
  pub range: RangeL,
  pub header: FunctionHeaderP<'p>,
  pub body: Option<&'p BlockPE<'p>>,
}

#[derive(Debug, PartialEq)]
pub struct FunctionReturnP<'p> {
  pub range: RangeL,
  pub ret_type: Option<ITemplexPT<'p>>,
}

#[derive(Debug, PartialEq)]
pub struct FunctionHeaderP<'p> {
  pub range: RangeL,
  pub name: Option<NameP<'p>>,
  pub attributes: &'p [IAttributeP<'p>],
  // If Some(Vector.empty), should show up like the <> in func moo<>(a int, b bool)
  pub generic_parameters: Option<GenericParametersP<'p>>,
  pub template_rules: Option<TemplateRulesP<'p>>,
  pub params: Option<ParamsP<'p>>,
  pub ret: FunctionReturnP<'p>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum MutabilityP {
  Mutable,
  Immutable,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum VariabilityP {
  Final,
  Varying,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum OwnershipP {
  Own,
  Borrow,
  Live,
  Weak,
  Share,
}

/// This represents how to load something.
/// If something's a Share, then nothing will happen,
/// so this only applies to mutables.
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum LoadAsP {
  // This means we want to move it. Thisll become a OwnP or ShareP.
  Move,
  // This means we want to use it, and want to make sure that it doesn't drop.
  // If permission is None, then we're probably in a dot. For example, x.launch()
  // should be mapped to launch(&!x) if x is mutable, or launch(&x) if it's readonly.
  LoadAsBorrow,
  // This means we want to get a weak reference to it. Thisll become a WeakP.
  LoadAsWeak,
  // This represents unspecified. It basically means, use whatever ownership already there.
  Use,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum LocationP {
  Inline,
  Yonder,
}

