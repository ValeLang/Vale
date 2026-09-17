
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct RegionTemplataType {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct CoordTemplataType {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct ImplTemplataType {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct KindTemplataType {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct FunctionTemplataType {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct IntegerTemplataType {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct BooleanTemplataType {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct MutabilityTemplataType {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct PrototypeTemplataType {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct StringTemplataType {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct LocationTemplataType {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct OwnershipTemplataType {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct VariabilityTemplataType {}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct PackTemplataType<'s> {
  pub element_type: &'s ITemplataType<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct TemplateTemplataType<'s> {
  pub param_types: &'s [ITemplataType<'s>],
  pub return_type: &'s ITemplataType<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum ITemplataType<'s> {
  RegionTemplataType(RegionTemplataType),
  CoordTemplataType(CoordTemplataType),
  ImplTemplataType(ImplTemplataType),
  KindTemplataType(KindTemplataType),
  FunctionTemplataType(FunctionTemplataType),
  IntegerTemplataType(IntegerTemplataType),
  BooleanTemplataType(BooleanTemplataType),
  MutabilityTemplataType(MutabilityTemplataType),
  PrototypeTemplataType(PrototypeTemplataType),
  StringTemplataType(StringTemplataType),
  LocationTemplataType(LocationTemplataType),
  OwnershipTemplataType(OwnershipTemplataType),
  VariabilityTemplataType(VariabilityTemplataType),
  PackTemplataType(PackTemplataType<'s>),
  TemplateTemplataType(TemplateTemplataType<'s>),
}

