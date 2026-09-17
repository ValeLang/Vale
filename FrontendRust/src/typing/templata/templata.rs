use crate::interner::StrI;
use crate::higher_typing::ast::*;
use crate::postparsing::itemplatatype::{
  BooleanTemplataType, CoordTemplataType, ITemplataType, ImplTemplataType,
  IntegerTemplataType, KindTemplataType, LocationTemplataType,
  MutabilityTemplataType, OwnershipTemplataType, PrototypeTemplataType,
  StringTemplataType, TemplateTemplataType, VariabilityTemplataType,
};
use crate::typing::ast::ast::{FunctionHeaderT, PrototypeT};
use crate::typing::env::environment::*;
use crate::typing::names::names::IdT;
use crate::typing::types::types::*;
use crate::utils::range::RangeS;
use crate::scout_arena::ScoutArena;
use crate::higher_typing::ast::CitizenA;
use std::fmt::Debug;
use std::fmt::Formatter;
use std::fmt::Result;
use std::hash::Hash;
use std::hash::Hasher;
use std::marker::PhantomData;

pub fn expect_mutability<'s, 't>(templata: ITemplataT<'s, 't>) -> ITemplataT<'s, 't> {
  match templata {
    // case t @ MutabilityTemplataT(_) => t
    t @ ITemplataT::Mutability(_) => t,
    // case PlaceholderTemplataT(idT, MutabilityTemplataType()) => PlaceholderTemplataT(idT, MutabilityTemplataType())
    ITemplataT::Placeholder(p) if matches!(p.tyype, ITemplataType::MutabilityTemplataType(_)) => templata,
    // case _ => vfail()
    _ => panic!("expect_mutability: not a mutability"),
  }
}

pub fn expect_variability<'s, 't>(templata: ITemplataT<'s, 't>) -> ITemplataT<'s, 't> {
  match templata {
    // case t @ VariabilityTemplataT(_) => t
    t @ ITemplataT::Variability(_) => t,
    // case PlaceholderTemplataT(idT, VariabilityTemplataType()) => PlaceholderTemplataT(idT, VariabilityTemplataType())
    ITemplataT::Placeholder(p) if matches!(p.tyype, ITemplataType::VariabilityTemplataType(_)) => templata,
    // case _ => vfail()
    _ => panic!("expect_variability: not a variability"),
  }
}

pub fn expect_integer<'s, 't>(templata: ITemplataT<'s, 't>) -> ITemplataT<'s, 't> {
  match templata {
    // case t @ IntegerTemplataT(_) => t
    t @ ITemplataT::Integer(_) => t,
    // case PlaceholderTemplataT(idT, IntegerTemplataType()) => PlaceholderTemplataT(idT, IntegerTemplataType())
    ITemplataT::Placeholder(p) if matches!(p.tyype, ITemplataType::IntegerTemplataType(_)) => templata,
    // case other => vfail(other)
    _ => panic!("expect_integer: not an integer"),
  }
}

fn expect_coord<'s, 't>(templata: ITemplataT<'s, 't>) -> ITemplataT<'s, 't> {
  panic!("Unimplemented: expect_coord");
}

pub fn expect_coord_templata<'s, 't>(templata: ITemplataT<'s, 't>) -> CoordTemplataT<'s, 't> {
  match templata {
    // case t @ CoordTemplataT(_) => t
    ITemplataT::Coord(t) => *t,
    // case other => vfail(other)
    _ => panic!("expect_coord_templata: not a coord"),
  }
}

fn expect_prototype_templata<'s, 't>(templata: ITemplataT<'s, 't>) -> PrototypeTemplataT<'s, 't> {
  panic!("Unimplemented: expect_prototype_templata");
}

fn expect_integer_templata<'s, 't>(templata: ITemplataT<'s, 't>) -> IntegerTemplataT {
  panic!("Unimplemented: expect_integer_templata");
}

fn expect_mutability_templata<'s, 't>(templata: ITemplataT<'s, 't>) -> MutabilityTemplataT {
  panic!("Unimplemented: expect_mutability_templata");
}

fn expect_variability_templata<'s, 't>(templata: ITemplataT<'s, 't>) -> ITemplataT<'s, 't> {
  panic!("Unimplemented: expect_variability_templata");
}

fn expect_kind<'s, 't>(templata: ITemplataT<'s, 't>) -> ITemplataT<'s, 't> {
  panic!("Unimplemented: expect_kind");
}

fn expect_kind_templata<'s, 't>(templata: ITemplataT<'s, 't>) -> KindTemplataT<'s, 't> {
  panic!("Unimplemented: expect_kind_templata");
}

// Inline-owned wrapper enum per §6.6. Scala's `ITemplataT[+T <: ITemplataType]`
// Interned payloads behind &'t; scalar variants inline. See @WVSBIZ for why.
/// Polyvalue (see @TFITCX) — derive Eq/Hash; never hand-roll `ptr::eq` on the outer `&self` (see @PVECFPZ).
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ITemplataT<'s, 't> {
  Coord(&'t CoordTemplataT<'s, 't>),
  Kind(&'t KindTemplataT<'s, 't>),
  Placeholder(&'t PlaceholderTemplataT<'s, 't>),
  Mutability(MutabilityTemplataT),
  Variability(VariabilityTemplataT),
  Ownership(OwnershipTemplataT),
  Integer(i64),
  Boolean(bool),
  String(StrI<'s>),
  Prototype(&'t PrototypeTemplataT<'s, 't>),
  Isa(&'t IsaTemplataT<'s, 't>),
  CoordList(&'t CoordListTemplataT<'s, 't>),
  RuntimeSizedArrayTemplate(RuntimeSizedArrayTemplateTemplataT),
  StaticSizedArrayTemplate(StaticSizedArrayTemplateTemplataT),
  Function(&'t FunctionTemplataT<'s, 't>),
  StructDefinition(&'t StructDefinitionTemplataT<'s, 't>),
  InterfaceDefinition(&'t InterfaceDefinitionTemplataT<'s, 't>),
  ImplDefinition(&'t ImplDefinitionTemplataT<'s, 't>),
  ExternFunction(&'t ExternFunctionTemplataT<'s, 't>),
  Location(LocationTemplataT),
}
impl<'s, 't> ITemplataT<'s, 't> where 's: 't {
  pub fn tyype(&self, scout_arena: &ScoutArena<'s>) -> ITemplataType<'s> {
    match self {
      ITemplataT::Coord(_) => ITemplataType::CoordTemplataType(CoordTemplataType {}),
      ITemplataT::Kind(_) => ITemplataType::KindTemplataType(KindTemplataType {}),
      ITemplataT::Placeholder(p) => p.tyype,
      ITemplataT::Mutability(_) => ITemplataType::MutabilityTemplataType(MutabilityTemplataType {}),
      ITemplataT::Variability(_) => ITemplataType::VariabilityTemplataType(VariabilityTemplataType {}),
      ITemplataT::Ownership(_) => ITemplataType::OwnershipTemplataType(OwnershipTemplataType {}),
      ITemplataT::Integer(_) => ITemplataType::IntegerTemplataType(IntegerTemplataType {}),
      ITemplataT::Boolean(_) => ITemplataType::BooleanTemplataType(BooleanTemplataType {}),
      ITemplataT::String(_) => ITemplataType::StringTemplataType(StringTemplataType {}),
      ITemplataT::Prototype(_) => ITemplataType::PrototypeTemplataType(PrototypeTemplataType {}),
      ITemplataT::Isa(_) => ITemplataType::ImplTemplataType(ImplTemplataType {}),
      ITemplataT::ImplDefinition(_) => ITemplataType::ImplTemplataType(ImplTemplataType {}),
      ITemplataT::Location(_) => ITemplataType::LocationTemplataType(LocationTemplataType {}),
      ITemplataT::CoordList(_) => panic!("Unimplemented: tyype on CoordList"),
      ITemplataT::RuntimeSizedArrayTemplate(_) => ITemplataType::TemplateTemplataType(TemplateTemplataType {
        param_types: scout_arena.alloc_slice_copy(&[
          ITemplataType::MutabilityTemplataType(MutabilityTemplataType {}),
          ITemplataType::CoordTemplataType(CoordTemplataType {}),
        ]),
        return_type: scout_arena.alloc(ITemplataType::KindTemplataType(KindTemplataType {})),
      }),
      ITemplataT::StaticSizedArrayTemplate(_) => ITemplataType::TemplateTemplataType(TemplateTemplataType {
        param_types: scout_arena.alloc_slice_copy(&[
          ITemplataType::IntegerTemplataType(IntegerTemplataType {}),
          ITemplataType::MutabilityTemplataType(MutabilityTemplataType {}),
          ITemplataType::VariabilityTemplataType(VariabilityTemplataType {}),
          ITemplataType::CoordTemplataType(CoordTemplataType {}),
        ]),
        return_type: scout_arena.alloc(ITemplataType::KindTemplataType(KindTemplataType {})),
      }),
      ITemplataT::Function(_) => panic!("Unimplemented: tyype on Function"),
      // Note that this might disagree with originStruct.tyype, which might not be a TemplateTemplataType().
      // In Compiler, StructTemplatas are templates, even if they have zero arguments.
      ITemplataT::StructDefinition(s) => ITemplataType::TemplateTemplataType(s.origin_struct.tyype),
      // Note that this might disagree with originStruct.tyype, which might not be a TemplateTemplataType().
      // In Compiler, InterfaceTemplatas are templates, even if they have zero arguments.
      ITemplataT::InterfaceDefinition(i) => ITemplataType::TemplateTemplataType(i.origin_interface.tyype),
      ITemplataT::ExternFunction(_) => panic!("Unimplemented: tyype on ExternFunction"),
    }
  }
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct CoordTemplataT<'s, 't> {
  pub coord: CoordT<'s, 't>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct PlaceholderTemplataT<'s, 't> {
  pub id: IdT<'s, 't>,
  pub tyype: ITemplataType<'s>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct KindTemplataT<'s, 't> {
  pub kind: KindT<'s, 't>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct RuntimeSizedArrayTemplateTemplataT {
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StaticSizedArrayTemplateTemplataT {
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, Debug)]
pub struct FunctionTemplataT<'s, 't> {
  pub outer_env: IEnvironmentT<'s, 't>,
  pub function: &'s FunctionA<'s>,
}
impl<'s, 't> PartialEq for FunctionTemplataT<'s, 't> {
  fn eq(&self, other: &Self) -> bool {
    self.function.range == other.function.range
      && self.function.name == other.function.name
  }
  
}
impl<'s, 't> Eq for FunctionTemplataT<'s, 't> {}
impl<'s, 't> Hash for FunctionTemplataT<'s, 't> {
  fn hash<H: Hasher>(&self, state: &mut H) {
    self.function.range.hash(state);
    self.function.name.hash(state);
  }
  
}

impl<'s, 't> FunctionTemplataT<'s, 't> where 's: 't {
  pub fn get_template_name(&self) -> IdT<'s, 't> {
    panic!("Unimplemented: get_template_name");
  }
  
  pub fn debug_string(&self) -> String {
    panic!("Unimplemented: debug_string");
  }
  
}

// AFTERM: figure out why some templatas compare environment and some don't —
// `FunctionTemplataT.equals` ignores `outerEnv` (Scala templata.scala:161-169)
// but this type's derived equality includes `declaring_env`.
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StructDefinitionTemplataT<'s, 't> {
  pub declaring_env: IEnvironmentT<'s, 't>,
  pub origin_struct: &'s StructA<'s>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IContainer<'s> {
  Interface(ContainerInterface<'s>),
  Struct(ContainerStruct<'s>),
  Function(ContainerFunction<'s>),
  Impl(ContainerImpl<'s>),
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ContainerInterface<'s> {
  pub interface: &'s InterfaceA<'s>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ContainerStruct<'s> {
  pub struct_: &'s StructA<'s>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ContainerFunction<'s> {
  pub function: &'s FunctionA<'s>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ContainerImpl<'s> {
  pub impl_: &'s ImplA<'s>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum CitizenDefinitionTemplataT<'s, 't> {
  Struct(&'t StructDefinitionTemplataT<'s, 't>),
  Interface(&'t InterfaceDefinitionTemplataT<'s, 't>),
}

impl<'s, 't> CitizenDefinitionTemplataT<'s, 't> where 's: 't {
  pub fn declaring_env(&self) -> IEnvironmentT<'s, 't> {
    panic!("Unimplemented: declaring_env");
  }
  
  pub fn origin_citizen(&self) -> &'s dyn CitizenA<'s> {
    panic!("Unimplemented: origin_citizen");
  }
  
}

fn unapply<'s, 't>(c: CitizenDefinitionTemplataT<'s, 't>) -> Option<(IEnvironmentT<'s, 't>, &'s dyn CitizenA<'s>)> {
  panic!("Unimplemented: unapply");
}

// AFTERM: figure out why some templatas compare environment and some don't —
// `FunctionTemplataT.equals` ignores `outerEnv` (Scala templata.scala:161-169)
// but this type's derived equality includes `declaring_env`.
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct InterfaceDefinitionTemplataT<'s, 't> {
  pub declaring_env: IEnvironmentT<'s, 't>,
  pub origin_interface: &'s InterfaceA<'s>,
}

// AFTERM: figure out why some templatas compare environment and some don't —
// `FunctionTemplataT.equals` ignores `outerEnv` (Scala templata.scala:161-169)
// but this type's derived equality includes `env`.
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ImplDefinitionTemplataT<'s, 't> {
  pub env: IEnvironmentT<'s, 't>,
  pub impl_: &'s ImplA<'s>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct OwnershipTemplataT {
    pub ownership: OwnershipT,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct VariabilityTemplataT {
    pub variability: VariabilityT,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct MutabilityTemplataT {
    pub mutability: MutabilityT,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct LocationTemplataT {
    pub location: LocationT,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct BooleanTemplataT {
    pub value: bool,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct IntegerTemplataT {
    pub value: i64,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StringTemplataT<'s> {
    pub value: StrI<'s>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct PrototypeTemplataT<'s, 't> {
  pub prototype: &'t PrototypeT<'s, 't>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct IsaTemplataT<'s, 't> {
  pub declaration_range: RangeS<'s>,
  pub impl_name: IdT<'s, 't>,
  pub sub_kind: KindT<'s, 't>,
  pub super_kind: KindT<'s, 't>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct CoordListTemplataT<'s, 't> {
  pub coords: &'t [CoordT<'s, 't>],
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash)]
pub struct ExternFunctionTemplataT<'s, 't> {
  pub header: &'t FunctionHeaderT<'s, 't>,
}

// FunctionHeaderT doesn't derive Debug yet; render by content (id) for @IIIOZ
// cross-run determinism — pointer addresses vary across runs due to ASLR.
impl<'s, 't> Debug for ExternFunctionTemplataT<'s, 't> {
  fn fmt(&self, f: &mut Formatter<'_>) -> Result {
    f.debug_struct("ExternFunctionTemplataT")
      .field("header_id", &self.header.id)
      .finish()
  }
  
}

// (Templata payload interning family removed — types are TFITCX Value-type per
// Scala parity. Construction goes via `bump.alloc(FooTemplataT { ... })`.)
