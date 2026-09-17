use super::ast::{LocationP, MutabilityP, NameP, OwnershipP, VariabilityP};
use super::rules::ITypePR;
use crate::interner::StrI;
use crate::lexing::RangeL;

#[derive(Copy, Clone, Debug, PartialEq)]
pub enum ITemplexPT<'p> {
  AnonymousRune(AnonymousRunePT),
  Bool(BoolPT),
  Point(PointPT<'p>),
  Call(CallPT<'p>),
  Function(FunctionPT<'p>),
  Inline(InlinePT<'p>),
  Int(IntPT),
  RegionRune(RegionRunePT<'p>),
  Location(LocationPT),
  Tuple(TuplePT<'p>),
  Mutability(MutabilityPT),
  NameOrRune(NameOrRunePT<'p>),
  Interpreted(InterpretedPT<'p>),
  Ownership(OwnershipPT),
  Pack(PackPT<'p>),
  Func(FuncPT<'p>),
  StaticSizedArray(StaticSizedArrayPT<'p>),
  RuntimeSizedArray(RuntimeSizedArrayPT<'p>),
  Share(SharePT<'p>),
  String(StringPT<'p>),
  TypedRune(TypedRunePT<'p>),
  Variability(VariabilityPT),
}
impl ITemplexPT<'_> {
  pub fn range(&self) -> RangeL {
    match self {
      ITemplexPT::AnonymousRune(r) => r.range,
      ITemplexPT::Bool(r) => r.range,
      ITemplexPT::Point(r) => r.range,
      ITemplexPT::Call(r) => r.range,
      ITemplexPT::Function(r) => r.range,
      ITemplexPT::Inline(r) => r.range,
      ITemplexPT::Int(r) => r.range,
      ITemplexPT::RegionRune(r) => r.range,
      ITemplexPT::Location(r) => r.range,
      ITemplexPT::Tuple(r) => r.range,
      ITemplexPT::Mutability(r) => r.0,
      ITemplexPT::NameOrRune(n) => n.0.range(),
      ITemplexPT::Interpreted(r) => r.range,
      ITemplexPT::Ownership(r) => r.0,
      ITemplexPT::Pack(p) => p.range,
      ITemplexPT::Func(r) => r.range,
      ITemplexPT::StaticSizedArray(r) => r.range,
      ITemplexPT::RuntimeSizedArray(r) => r.range,
      ITemplexPT::Share(r) => r.range,
      ITemplexPT::String(r) => r.range,
      ITemplexPT::TypedRune(r) => r.range,
      ITemplexPT::Variability(r) => r.0,
    }
  }
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct AnonymousRunePT {
  pub range: RangeL,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct BoolPT {
  pub range: RangeL,
  pub value: bool,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct PointPT<'p> {
  pub range: RangeL,
  pub inner: &'p ITemplexPT<'p>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct CallPT<'p> {
  pub range: RangeL,
  pub template: &'p ITemplexPT<'p>,
  pub args: &'p [&'p ITemplexPT<'p>],
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct FunctionPT<'p> {
  pub range: RangeL,
  pub mutability: Option<&'p ITemplexPT<'p>>,
  pub parameters: &'p PackPT<'p>,
  pub return_type: &'p ITemplexPT<'p>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct InlinePT<'p> {
  pub range: RangeL,
  pub inner: &'p ITemplexPT<'p>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct IntPT {
  pub range: RangeL,
  pub value: i64,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct RegionRunePT<'p> {
  pub range: RangeL,
  pub name: Option<NameP<'p>>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct LocationPT {
  pub range: RangeL,
  pub location: LocationP,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct TuplePT<'p> {
  pub range: RangeL,
  pub elements: &'p [&'p ITemplexPT<'p>],
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct MutabilityPT(pub RangeL, pub MutabilityP);

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct NameOrRunePT<'p>(pub NameP<'p>);
impl<'p> NameOrRunePT<'p> {
  pub fn new(name: NameP<'p>) -> Self {
    assert!(name.as_str() != "_", "vassert: NameOrRunePT name must not be \"_\"");
    Self(name)
  }
// V: do we have anything enforcing that we must go through this constructor? and other constructors in general?
// VA: No. The inner field is `pub`, so callers can write `NameOrRunePT(name)` directly, bypassing
// VA: the vassert in new(). This happens in 4 places: templex_parser.rs (lines 774, 881) and
// VA: parsed_loader.rs (lines 1859, 1944). Since the struct is in a different module from callers,
// VA: removing `pub` from the tuple field would enforce the constructor at compile time.
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct InterpretedPT<'p> {
  pub range: RangeL,
  pub maybe_ownership: Option<&'p OwnershipPT>,
  pub maybe_region: Option<&'p RegionRunePT<'p>>,
  pub inner: &'p ITemplexPT<'p>,
}
impl<'p> InterpretedPT<'p> {
  pub fn new(range: RangeL, maybe_ownership: Option<&'p OwnershipPT>, maybe_region: Option<&'p RegionRunePT<'p>>, inner: &'p ITemplexPT<'p>) -> Self {
    assert!(maybe_ownership.is_some() || maybe_region.is_some(), "vassert: InterpretedPT must have ownership or region");
    Self { range, maybe_ownership, maybe_region, inner }
  }
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct OwnershipPT(pub RangeL, pub OwnershipP);

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct PackPT<'p> {
  pub range: RangeL,
  pub members: &'p [&'p ITemplexPT<'p>],
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct FuncPT<'p> {
  pub range: RangeL,
  pub name: NameP<'p>,
  pub params_range: RangeL,
  pub parameters: &'p [&'p ITemplexPT<'p>],
  pub return_type: &'p ITemplexPT<'p>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct StaticSizedArrayPT<'p> {
  pub range: RangeL,
  pub mutability: &'p ITemplexPT<'p>,
  pub variability: &'p ITemplexPT<'p>,
  pub size: &'p ITemplexPT<'p>,
  pub element: &'p ITemplexPT<'p>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct RuntimeSizedArrayPT<'p> {
  pub range: RangeL,
  pub mutability: &'p ITemplexPT<'p>,
  pub element: &'p ITemplexPT<'p>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct SharePT<'p> {
  pub range: RangeL,
  pub inner: &'p ITemplexPT<'p>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct StringPT<'p> {
  pub range: RangeL,
  pub str: StrI<'p>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct TypedRunePT<'p> {
  pub range: RangeL,
  pub rune: NameP<'p>,
  pub tyype: ITypePR,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct VariabilityPT(pub RangeL, pub VariabilityP);
