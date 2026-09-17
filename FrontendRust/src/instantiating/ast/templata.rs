use crate::instantiating::ast::types::{CoordI, KindIT, OwnershipI, MutabilityI, VariabilityI, LocationI};
use crate::instantiating::ast::ast::{FunctionHeaderI, PrototypeI};
use crate::instantiating::ast::names::{IdI, IImplNameI};
use crate::interner::StrI;
use crate::utils::range::RangeS;
use crate::postparsing::itemplatatype::TemplateTemplataType;
use crate::typing::types::types::KindT;
use std::marker::PhantomData;

pub fn expect_coord<'s, 'i, R>(templata: ITemplataI<'s, 'i, R>) -> ITemplataI<'s, 'i, R> { panic!("Unimplemented: expect_coord"); }

pub fn expect_coord_templata<'s, 'i, R>(templata: ITemplataI<'s, 'i, R>) -> CoordTemplataI<'s, 'i, R> {
    match templata {
        ITemplataI::Coord(t) => t,
        _ => panic!("expect_coord_templata: not a CoordTemplataI"),
    }
}

pub fn expect_integer_templata<'s, 'i, R>(templata: ITemplataI<'s, 'i, R>) -> IntegerTemplataI<R> {
    match templata {
        ITemplataI::Integer(t) => t,
        _ => panic!("vfail"),
    }
}

pub fn expect_mutability_templata<'s, 'i, R>(templata: ITemplataI<'s, 'i, R>) -> MutabilityTemplataI<R> {
    match templata {
        ITemplataI::Mutability(m) => m,
        _ => panic!("expect_mutability_templata: not a MutabilityTemplataI"),
    }
}

pub fn expect_variability_templata<'s, 'i, R>(templata: ITemplataI<'s, 'i, R>) -> VariabilityTemplataI<R> {
    match templata {
        ITemplataI::Variability(t) => t,
        _ => panic!("expect_variability_templata: not a VariabilityTemplataI"),
    }
}

pub fn expect_kind<'s, 'i, R>(templata: ITemplataI<'s, 'i, R>) -> ITemplataI<'s, 'i, R> { panic!("Unimplemented: expect_kind"); }

pub fn expect_kind_templata<'s, 'i, R>(templata: ITemplataI<'s, 'i, R>) -> KindTemplataI<'s, 'i, R> { panic!("Unimplemented: expect_kind_templata"); }

pub fn expect_region_templata<'s, 'i, R>(templata: ITemplataI<'s, 'i, R>) -> RegionTemplataI<R> { panic!("Unimplemented: expect_region_templata"); }

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub enum ITemplataI<'s, 'i, R> {
  Coord(CoordTemplataI<'s, 'i, R>),
  Kind(KindTemplataI<'s, 'i, R>),
  RuntimeSizedArrayTemplate(RuntimeSizedArrayTemplateTemplataI<R>),
  StaticSizedArrayTemplate(StaticSizedArrayTemplateTemplataI<R>),
  Function(FunctionTemplataI<'s, 'i, R>),
  StructDefinition(StructDefinitionTemplataI<'s, 'i, R>),
  InterfaceDefinition(InterfaceDefinitionTemplataI<'s, 'i, R>),
  ImplDefinition(ImplDefinitionTemplataI<'s, 'i, R>),
  Ownership(OwnershipTemplataI<R>),
  Variability(VariabilityTemplataI<R>),
  Mutability(MutabilityTemplataI<R>),
  Location(LocationTemplataI<R>),
  Boolean(BooleanTemplataI<R>),
  Integer(IntegerTemplataI<R>),
  String(StringTemplataI<'s, R>),
  Prototype(PrototypeTemplataI<'s, 'i, R>),
  Isa(IsaTemplataI<'s, 'i, R>),
  CoordList(CoordListTemplataI<'s, 'i, R>),
  Region(RegionTemplataI<R>),
  ExternFunction(ExternFunctionTemplataI<'s, 'i, R>),
}

impl<'s, 'i, R> ITemplataI<'s, 'i, R> {
  pub fn expect_coord_templata(&self) -> CoordTemplataI<'s, 'i, R> { panic!("Unimplemented: expect_coord_templata"); }

  pub fn expect_region_templata(&self) -> RegionTemplataI<R> { panic!("Unimplemented: expect_region_templata"); }
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct CoordTemplataI<'s, 'i, R> {
  pub region: RegionTemplataI<R>,
  pub coord: CoordI<'s, 'i, R>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct KindTemplataI<'s, 'i, R> {
  pub kind: KindIT<'s, 'i, R>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct RuntimeSizedArrayTemplateTemplataI<R> {
  pub _marker: PhantomData<R>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct StaticSizedArrayTemplateTemplataI<R> {
  pub _marker: PhantomData<R>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct FunctionTemplataI<'s, 'i, R> {
  pub env_id: IdI<'s, 'i, R>,
}

impl<'s, 'i, R> FunctionTemplataI<'s, 'i, R> {
  pub fn get_template_name(&self) -> IdI<'s, 'i, R> { panic!("Unimplemented: get_template_name"); }
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct StructDefinitionTemplataI<'s, 'i, R> {
  pub env_id: IdI<'s, 'i, R>,
  pub tyype: TemplateTemplataType<'s>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub enum CitizenDefinitionTemplataI<'s, 'i, R> {
  Struct(StructDefinitionTemplataI<'s, 'i, R>),
  Interface(InterfaceDefinitionTemplataI<'s, 'i, R>),
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct InterfaceDefinitionTemplataI<'s, 'i, R> {
  pub env_id: IdI<'s, 'i, R>,
  pub tyype: TemplateTemplataType<'s>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct ImplDefinitionTemplataI<'s, 'i, R> {
  pub env_id: IdI<'s, 'i, R>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct OwnershipTemplataI<R> {
  pub ownership: OwnershipI,
  pub _marker: PhantomData<R>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct VariabilityTemplataI<R> {
  pub variability: VariabilityI,
  pub _marker: PhantomData<R>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct MutabilityTemplataI<R> {
  pub mutability: MutabilityI,
  pub _marker: PhantomData<R>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct LocationTemplataI<R> {
  pub location: LocationI,
  pub _marker: PhantomData<R>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct BooleanTemplataI<R> {
  pub value: bool,
  pub _marker: PhantomData<R>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct IntegerTemplataI<R> {
  pub value: i64,
  pub _marker: PhantomData<R>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct StringTemplataI<'s, R> {
  pub value: StrI<'s>,
  pub _marker: PhantomData<R>,
}

#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct PrototypeTemplataI<'s, 'i, R> {
  pub declaration_range: RangeS<'s>,
  pub prototype: &'i PrototypeI<'s, 'i, R>,
}

#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct IsaTemplataI<'s, 'i, R> {
  pub declaration_range: RangeS<'s>,
  pub impl_name: IdI<'s, 'i, R>,
  pub sub_kind: KindIT<'s, 'i, R>,
  pub super_kind: KindIT<'s, 'i, R>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct CoordListTemplataI<'s, 'i, R> {
  pub coords: &'i[CoordI<'s, 'i, R>],
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct RegionTemplataI<R> {
  pub pure_height: i32,
  pub _marker: PhantomData<R>,
}

#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ExternFunctionTemplataI<'s, 'i, R> {
  pub header: &'i FunctionHeaderI<'s, 'i>,
  pub _marker: PhantomData<R>,
}

