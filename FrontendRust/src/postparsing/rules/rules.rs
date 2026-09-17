use crate::interner::StrI;
use crate::postparsing::names::IRuneS;
use crate::postparsing::names::IImpreciseNameS;
use crate::postparsing::itemplatatype::{
  BooleanTemplataType, ITemplataType, IntegerTemplataType, LocationTemplataType,
  MutabilityTemplataType, OwnershipTemplataType, StringTemplataType, VariabilityTemplataType,
};
use crate::parsing::ast::{LocationP, MutabilityP, OwnershipP, VariabilityP};
use crate::utils::range::RangeS;

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct RuneUsage<'s> {
  pub range: RangeS<'s>,
  pub rune: IRuneS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub enum IRulexSR<'s> {
  Equals(EqualsSR<'s>),
  Literal(LiteralSR<'s>),
  MaybeCoercingLookup(MaybeCoercingLookupSR<'s>),
  Lookup(LookupSR<'s>),
  MaybeCoercingCall(MaybeCoercingCallSR<'s>),
  Call(CallSR<'s>),
  RuneParentEnvLookup(RuneParentEnvLookupSR<'s>),
  Augment(AugmentSR<'s>),
  OneOf(OneOfSR<'s>),
  IsInterface(IsInterfaceSR<'s>),
  CoordComponents(CoordComponentsSR<'s>),
  CoerceToCoord(CoerceToCoordSR<'s>),
  Pack(PackSR<'s>),
  CallSiteFunc(CallSiteFuncSR<'s>),
  DefinitionFunc(DefinitionFuncSR<'s>),
  Resolve(ResolveSR<'s>),
  CoordSend(CoordSendSR<'s>),
  DefinitionCoordIsa(DefinitionCoordIsaSR<'s>),
  CallSiteCoordIsa(CallSiteCoordIsaSR<'s>),
  KindComponents(KindComponentsSR<'s>),
  PrototypeComponents(PrototypeComponentsSR<'s>),
  IsConcrete(IsConcreteSR<'s>),
  IsStruct(IsStructSR<'s>),
  RefListCompoundMutability(RefListCompoundMutabilitySR<'s>),
  IndexList(IndexListSR<'s>),
}

// V: why cloneable?
// VA: It shouldn't be. Clone is derived but never called anywhere. IRulexSR is Clone-without-Copy
// VA: (ATDCX violation). It's always stored as &'s [IRulexSR<'s>] in output structs. Safe to remove Clone.

impl<'s> IRulexSR<'s> {
  pub fn range<'r>(&'r self) -> &'r RangeS<'s> {
    match self {
      IRulexSR::Equals(x) => &x.range,
      IRulexSR::Literal(x) => &x.range,
      IRulexSR::MaybeCoercingLookup(x) => &x.range,
      IRulexSR::Lookup(x) => &x.range,
      IRulexSR::MaybeCoercingCall(x) => &x.range,
      IRulexSR::Call(x) => &x.range,
      IRulexSR::RuneParentEnvLookup(x) => &x.range,
      IRulexSR::Augment(x) => &x.range,
      IRulexSR::OneOf(x) => &x.range,
      IRulexSR::IsInterface(x) => &x.range,
      IRulexSR::CoordComponents(x) => &x.range,
      IRulexSR::CoerceToCoord(x) => &x.range,
      IRulexSR::Pack(x) => &x.range,
      IRulexSR::CallSiteFunc(x) => &x.range,
      IRulexSR::DefinitionFunc(x) => &x.range,
      IRulexSR::Resolve(x) => &x.range,
      IRulexSR::CoordSend(x) => &x.range,
      IRulexSR::DefinitionCoordIsa(x) => &x.range,
      IRulexSR::CallSiteCoordIsa(x) => &x.range,
      IRulexSR::KindComponents(x) => &x.range,
      IRulexSR::PrototypeComponents(x) => &x.range,
      IRulexSR::IsConcrete(x) => &x.range,
      IRulexSR::IsStruct(x) => &x.range,
      IRulexSR::RefListCompoundMutability(x) => &x.range,
      IRulexSR::IndexList(x) => &x.range,
    }
    
  }
  
  pub fn rune_usages<'r>(&'r self) -> Vec<RuneUsage<'s>> {
    match self {
      IRulexSR::Equals(x) => vec![x.left.clone(), x.right.clone()],
      IRulexSR::Literal(x) => vec![x.rune.clone()],
      IRulexSR::MaybeCoercingLookup(x) => vec![x.rune.clone()],
      IRulexSR::Lookup(x) => vec![x.rune.clone()],
      IRulexSR::MaybeCoercingCall(x) => {
        let mut usages = vec![x.result_rune.clone(), x.template_rune.clone()];
        usages.extend(x.args.iter().cloned());
        usages
      }
      IRulexSR::Call(x) => {
        let mut usages = vec![x.result_rune.clone(), x.template_rune.clone()];
        usages.extend(x.args.iter().cloned());
        usages
      }
      IRulexSR::RuneParentEnvLookup(x) => vec![x.rune.clone()],
      IRulexSR::Augment(x) => vec![x.result_rune.clone(), x.inner_rune.clone()],
      IRulexSR::OneOf(x) => vec![x.rune.clone()],
      IRulexSR::IsInterface(x) => vec![x.rune.clone()],
      IRulexSR::CoordComponents(x) => {
        vec![x.result_rune.clone(), x.ownership_rune.clone(), x.kind_rune.clone()]
      }
      IRulexSR::CoerceToCoord(x) => vec![x.coord_rune.clone(), x.kind_rune.clone()],
      IRulexSR::Pack(x) => {
        let mut usages = vec![x.result_rune.clone()];
        usages.extend(x.members.iter().cloned());
        usages
      }
      IRulexSR::CallSiteFunc(x) => vec![x.prototype_rune.clone(), x.params_list_rune.clone(), x.return_rune.clone()],
      IRulexSR::DefinitionFunc(x) => vec![x.result_rune.clone(), x.params_list_rune.clone(), x.return_rune.clone()],
      IRulexSR::Resolve(x) => vec![x.result_rune.clone(), x.params_list_rune.clone(), x.return_rune.clone()],
      IRulexSR::CoordSend(x) => vec![x.sender_rune.clone(), x.receiver_rune.clone()],
      IRulexSR::DefinitionCoordIsa(x) => vec![x.result_rune.clone(), x.sub_rune.clone(), x.super_rune.clone()],
      IRulexSR::CallSiteCoordIsa(x) => {
        let mut usages: Vec<RuneUsage<'s>> = x.result_rune.iter().cloned().collect();
        usages.push(x.sub_rune.clone());
        usages.push(x.super_rune.clone());
        usages
      }
      IRulexSR::KindComponents(x) => vec![x.kind_rune.clone(), x.mutability_rune.clone()],
      IRulexSR::PrototypeComponents(x) => vec![x.result_rune.clone(), x.params_rune.clone(), x.return_rune.clone()],
      IRulexSR::IsConcrete(x) => vec![x.rune.clone()],
      IRulexSR::IsStruct(x) => vec![x.rune.clone()],
      IRulexSR::RefListCompoundMutability(x) => vec![x.result_rune.clone(), x.coord_list_rune.clone()],
      IRulexSR::IndexList(x) => vec![x.result_rune.clone(), x.list_rune.clone()],
    }
  }
  
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct EqualsSR<'s> {
  pub range: RangeS<'s>,
  pub left: RuneUsage<'s>,
  pub right: RuneUsage<'s>,
}

// See SAIRFU and SRCAMP for what's going on with these rules.
#[derive(Copy, Clone, Debug, PartialEq)]
// MIGALLOW: Rust doesn't need a runeUsages override
pub struct CoordSendSR<'s> {
  pub range: RangeS<'s>,
  pub sender_rune: RuneUsage<'s>,
  pub receiver_rune: RuneUsage<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct DefinitionCoordIsaSR<'s> {
  pub range: RangeS<'s>,
  pub result_rune: RuneUsage<'s>,
  pub sub_rune: RuneUsage<'s>,
  pub super_rune: RuneUsage<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct CallSiteCoordIsaSR<'s> {
  pub range: RangeS<'s>,
  // This is here because when we add this CallSiteCoordIsaSR and its companion DefinitionCoordIsaSR,
  // the DefinitionCoordIsaSR has a resultRune that it usually populates with an ImplTemplata.
  // That rune is in the rules somewhere, but when we filter out the DefinitionCoordIsaSR for call site
  // solves, that rune is still there, and all runes must be solved, so we need something to solve it.
  // So, we make CallSiteCoordIsaSR solve it, and populate it with an ImplTemplata or ImplDefinitionTemplata.
  // It's also similar to how Definition/CallSiteFuncSR work.
  // It also means the call site has access to the impls, which might be nice for ONBIFS and NBIFP.
  // It's an Option because CoordSendSR sometimes produces one of these, and it doesn't care about
  // the result.
  pub result_rune: Option<RuneUsage<'s>>,
  pub sub_rune: RuneUsage<'s>,
  pub super_rune: RuneUsage<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct KindComponentsSR<'s> {
  pub range: RangeS<'s>,
  pub kind_rune: RuneUsage<'s>,
  pub mutability_rune: RuneUsage<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct CoordComponentsSR<'s> {
  pub range: RangeS<'s>,
  pub result_rune: RuneUsage<'s>,
  pub ownership_rune: RuneUsage<'s>,
  pub kind_rune: RuneUsage<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct PrototypeComponentsSR<'s> {
  pub range: RangeS<'s>,
  pub result_rune: RuneUsage<'s>,
  pub params_rune: RuneUsage<'s>,
  pub return_rune: RuneUsage<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct ResolveSR<'s> {
  pub range: RangeS<'s>,
  pub result_rune: RuneUsage<'s>,
  pub name: StrI<'s>,
  pub params_list_rune: RuneUsage<'s>,
  pub return_rune: RuneUsage<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct CallSiteFuncSR<'s> {
  pub range: RangeS<'s>,
  pub prototype_rune: RuneUsage<'s>,
  pub name: StrI<'s>,
  pub params_list_rune: RuneUsage<'s>,
  pub return_rune: RuneUsage<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct DefinitionFuncSR<'s> {
  pub range: RangeS<'s>,
  pub result_rune: RuneUsage<'s>,
  pub name: StrI<'s>,
  pub params_list_rune: RuneUsage<'s>,
  pub return_rune: RuneUsage<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct OneOfSR<'s> {
  pub range: RangeS<'s>,
  pub rune: RuneUsage<'s>,
  pub literals: &'s [ILiteralSL<'s>],
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct IsConcreteSR<'s> {
  pub range: RangeS<'s>,
  pub rune: RuneUsage<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct IsInterfaceSR<'s> {
  pub range: RangeS<'s>,
  pub rune: RuneUsage<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct IsStructSR<'s> {
  pub range: RangeS<'s>,
  pub rune: RuneUsage<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct CoerceToCoordSR<'s> {
  pub range: RangeS<'s>,
  pub coord_rune: RuneUsage<'s>,
  pub kind_rune: RuneUsage<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct RefListCompoundMutabilitySR<'s> {
  pub range: RangeS<'s>,
  pub result_rune: RuneUsage<'s>,
  pub coord_list_rune: RuneUsage<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct LiteralSR<'s> {
  pub range: RangeS<'s>,
  pub rune: RuneUsage<'s>,
  pub literal: ILiteralSL<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct MaybeCoercingLookupSR<'s> {
  pub range: RangeS<'s>,
  pub rune: RuneUsage<'s>,
  pub name: IImpreciseNameS<'s>,
}

// A rule that looks up something that's not a Kind, so it doesn't need a default region.
#[derive(Copy, Clone, Debug, PartialEq)]
pub struct LookupSR<'s> {
  pub range: RangeS<'s>,
  pub rune: RuneUsage<'s>,
  pub name: IImpreciseNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct MaybeCoercingCallSR<'s> {
  pub range: RangeS<'s>,
  pub result_rune: RuneUsage<'s>,
  pub template_rune: RuneUsage<'s>,
  pub args: &'s [RuneUsage<'s>],
}

#[derive(Copy, Clone, Debug, PartialEq)]
// MIGALLOW: Rust doesn't need a runeUsages override
pub struct CallSR<'s> {
  pub range: RangeS<'s>,
  pub result_rune: RuneUsage<'s>,
  pub template_rune: RuneUsage<'s>,
  pub args: &'s [RuneUsage<'s>],
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct IndexListSR<'s> {
  pub range: RangeS<'s>,
  pub result_rune: RuneUsage<'s>,
  pub list_rune: RuneUsage<'s>,
  pub index: i32,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct RuneParentEnvLookupSR<'s> {
  pub range: RangeS<'s>,
  pub rune: RuneUsage<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct AugmentSR<'s> {
  pub range: RangeS<'s>,
  pub result_rune: RuneUsage<'s>,
  pub ownership: Option<OwnershipP>,
  pub inner_rune: RuneUsage<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct PackSR<'s> {
  pub range: RangeS<'s>,
  pub result_rune: RuneUsage<'s>,
  pub members: &'s [RuneUsage<'s>],
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub enum ILiteralSL<'s> {
  IntLiteral(IntLiteralSL),
  StringLiteral(StringLiteralSL<'s>),
  BoolLiteral(BoolLiteralSL),
  MutabilityLiteral(MutabilityLiteralSL),
  LocationLiteral(LocationLiteralSL),
  OwnershipLiteral(OwnershipLiteralSL),
  VariabilityLiteral(VariabilityLiteralSL),
}

impl<'s> ILiteralSL<'s> {
  pub fn get_type<'a>(&self) -> ITemplataType<'a> {
    match self {
      ILiteralSL::IntLiteral(x) => x.get_type(),
      ILiteralSL::StringLiteral(x) => x.get_type(),
      ILiteralSL::BoolLiteral(x) => x.get_type(),
      ILiteralSL::MutabilityLiteral(x) => x.get_type(),
      ILiteralSL::LocationLiteral(x) => x.get_type(),
      ILiteralSL::OwnershipLiteral(x) => x.get_type(),
      ILiteralSL::VariabilityLiteral(x) => x.get_type(),
    }
  }
  
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct IntLiteralSL {
  pub value: i64,
}

impl IntLiteralSL {
  pub fn get_type<'a>(&self) -> ITemplataType<'a> {
    ITemplataType::IntegerTemplataType(IntegerTemplataType {})
  }
  
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct StringLiteralSL<'s> {
  pub value: StrI<'s>,
}

impl<'s> StringLiteralSL<'s> {
  pub fn get_type<'a>(&self) -> ITemplataType<'a> {
    ITemplataType::StringTemplataType(StringTemplataType {})
  }
  
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct BoolLiteralSL {
  pub value: bool,
}

impl BoolLiteralSL {
  pub fn get_type<'a>(&self) -> ITemplataType<'a> {
    ITemplataType::BooleanTemplataType(BooleanTemplataType {})
  }
  
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct MutabilityLiteralSL {
  pub mutability: MutabilityP,
}

impl MutabilityLiteralSL {
  pub fn get_type<'a>(&self) -> ITemplataType<'a> {
    ITemplataType::MutabilityTemplataType(MutabilityTemplataType {})
  }
  
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct LocationLiteralSL {
  pub location: LocationP,
}

impl LocationLiteralSL {
  pub fn get_type<'a>(&self) -> ITemplataType<'a> {
    ITemplataType::LocationTemplataType(LocationTemplataType {})
  }
  
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct OwnershipLiteralSL {
  pub ownership: OwnershipP,
}

impl OwnershipLiteralSL {
  pub fn get_type<'a>(&self) -> ITemplataType<'a> {
    ITemplataType::OwnershipTemplataType(OwnershipTemplataType {})
  }
  
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct VariabilityLiteralSL {
  pub variability: VariabilityP,
}

impl VariabilityLiteralSL {
  pub fn get_type<'a>(&self) -> ITemplataType<'a> {
    ITemplataType::VariabilityTemplataType(VariabilityTemplataType {})
  }
  
}

