use std::marker::PhantomData;

use crate::instantiating::ast::names::{IdI, IInterfaceNameI, IStructNameI, RuntimeSizedArrayNameI, StaticSizedArrayNameI};
use crate::instantiating::instantiating_interner::MustIntern;
use crate::instantiating::ast::names::INameI;
use crate::instantiating::ast::templata::CoordTemplataI;

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum OwnershipI {
  ImmutableShare,
  MutableShare,
  Own,
  Weak,
  ImmutableBorrow,
  MutableBorrow,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum MutabilityI {
  Mutable,
  Immutable,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum VariabilityI {
  Final,
  Varying,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum LocationI {
  Inline,
  Yonder,
}

// Per architect Slab 16a: kept as pure compile-time phantom marker. The three
// ZST variants below (sI/nI/cI) implement a private `Sealed` trait so only this
// module can introduce new region modes — same closed-set guarantee as Scala's
// `sealed trait IRegionsModeI`.
mod region_mode_sealed {
    pub trait Sealed {}
}
pub trait IRegionsModeIT: region_mode_sealed::Sealed {}

#[allow(non_camel_case_types)]
pub enum IRegionsModeI {
  sI(sI),
  nI(nI),
  cI(cI),
}

#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
#[allow(non_camel_case_types)]
pub struct sI;
impl region_mode_sealed::Sealed for sI {}
impl IRegionsModeIT for sI {}

// Region modes are covariant in Scala (`nI <: sI`); Rust erases that covariance (same rationale as
// +T-erasure). Per CCFCTS the modes are inert zero-member tags, so `nI`/`cI` alias `sI` to make ASTs
// across modes interchangeable (e.g. passing `PrototypeI<nI>` where `PrototypeI<sI>` is expected).
#[allow(non_camel_case_types)]
pub type nI = sI;

#[allow(non_camel_case_types)]
pub type cI = sI;

impl<'s, 'i, R> CoordI<'s, 'i, R> where 's: 'i {
  pub fn void() -> CoordI<'s, 'i, R> { panic!("Unimplemented: void"); }
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct CoordI<'s, 'i, R> where 's: 'i {
  pub ownership: OwnershipI,
  pub kind: KindIT<'s, 'i, R>,
}

// Per Scala parity: primitives (NeverIT, VoidIT, IntIT, BoolIT, StrIT, FloatIT)
// are TFITCX Value-type and held inline; the 4 compound payloads
// (StaticSizedArrayIT, RuntimeSizedArrayIT, StructIT, InterfaceIT) are arena-
// interned and held by `&'i` ref (see @WVSBIZ).
/// Polyvalue (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum KindIT<'s, 'i, R> where 's: 'i {
  NeverIT(NeverIT<R>),
  VoidIT(VoidIT<R>),
  IntIT(IntIT<R>),
  BoolIT(BoolIT<R>),
  StrIT(StrIT<R>),
  FloatIT(FloatIT<R>),
  StaticSizedArrayIT(&'i StaticSizedArrayIT<'s, 'i, R>),
  RuntimeSizedArrayIT(&'i RuntimeSizedArrayIT<'s, 'i, R>),
  StructIT(&'i StructIT<'s, 'i, R>),
  InterfaceIT(&'i InterfaceIT<'s, 'i, R>),
}

impl<'s, 'i, R> KindIT<'s, 'i, R> where 's: 'i {
  pub fn is_primitive(&self) -> bool { panic!("Unimplemented: is_primitive"); }

  pub fn expect_citizen(&self) -> ICitizenIT<'s, 'i, R> { panic!("Unimplemented: expect_citizen"); }

  pub fn expect_interface(&self) -> &'i InterfaceIT<'s, 'i, R> {
    match self {
      KindIT::InterfaceIT(c) => c,
      _ => panic!("expect_interface: not an interface"),
    }
  }

  pub fn expect_struct(&self) -> &'i StructIT<'s, 'i, R> { panic!("Unimplemented: expect_struct"); }
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct NeverIT<R> {
  pub from_break: bool,
  pub _marker: PhantomData<R>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct VoidIT<R> {
  pub _marker: PhantomData<R>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct IntIT<R> {
  pub bits: i32,
  pub _marker: PhantomData<R>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct BoolIT<R> {
  pub _marker: PhantomData<R>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StrIT<R> {
  pub _marker: PhantomData<R>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct FloatIT<R> {
  pub _marker: PhantomData<R>,
}

// (Realized via `impl TryFrom<StaticSizedArrayIT<R>> for ...` or inline match.)

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StaticSizedArrayIT<'s, 'i, R> where 's: 'i {
  pub name: IdI<'s, 'i, R>,
  pub _must_intern: MustIntern,
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StaticSizedArrayITValI<'s, 'i, R> where 's: 'i {
  pub name: IdI<'s, 'i, R>,
}
impl<'s, 'i, R: Copy> StaticSizedArrayIT<'s, 'i, R> where 's: 'i {
  pub fn mutability(self) -> MutabilityI {
    match self.name.local_name {
      INameI::StaticSizedArray(n) => n.arr.mutability,
      _ => panic!("StaticSizedArrayIT::mutability: name.local_name is not StaticSizedArrayNameI"),
    }
  }
  pub fn element_type(self) -> CoordTemplataI<'s, 'i, R> {
    match self.name.local_name {
      INameI::StaticSizedArray(n) => n.arr.element_type,
      _ => panic!("StaticSizedArrayIT::element_type: name.local_name is not StaticSizedArrayNameI"),
    }
  }
  pub fn size(self) -> i64 {
    match self.name.local_name {
      INameI::StaticSizedArray(n) => n.size,
      _ => panic!("StaticSizedArrayIT::size: name.local_name is not StaticSizedArrayNameI"),
    }
  }
  pub fn variability(self) -> VariabilityI {
    match self.name.local_name {
      INameI::StaticSizedArray(n) => n.variability,
      _ => panic!("StaticSizedArrayIT::variability: name.local_name is not StaticSizedArrayNameI"),
    }
  }
}

// (Realized via `impl TryFrom<RuntimeSizedArrayIT<R>> for ...` or inline match.)

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct RuntimeSizedArrayIT<'s, 'i, R> where 's: 'i {
  pub name: IdI<'s, 'i, R>,
  pub _must_intern: MustIntern,
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct RuntimeSizedArrayITValI<'s, 'i, R> where 's: 'i {
  pub name: IdI<'s, 'i, R>,
}
impl<'s, 'i, R: Copy> RuntimeSizedArrayIT<'s, 'i, R> where 's: 'i {
  pub fn mutability(self) -> MutabilityI {
    match self.name.local_name {
      INameI::RuntimeSizedArray(n) => n.arr.mutability,
      _ => panic!("RuntimeSizedArrayIT::mutability: name.local_name is not RuntimeSizedArrayNameI"),
    }
  }
  pub fn element_type(self) -> CoordTemplataI<'s, 'i, R> {
    match self.name.local_name {
      INameI::RuntimeSizedArray(n) => n.arr.element_type,
      _ => panic!("RuntimeSizedArrayIT::element_type: name.local_name is not RuntimeSizedArrayNameI"),
    }
  }
}

// (Realized via `impl TryFrom<ICitizenIT<R>> for ...` or inline match.)

/// Polyvalue (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ISubKindIT<'s, 'i, R> where 's: 'i {
  StructIT(&'i StructIT<'s, 'i, R>),
  InterfaceIT(&'i InterfaceIT<'s, 'i, R>),
}

/// Polyvalue (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ICitizenIT<'s, 'i, R> where 's: 'i {
  StructIT(&'i StructIT<'s, 'i, R>),
  InterfaceIT(&'i InterfaceIT<'s, 'i, R>),
}
impl<'s, 'i, R: Copy> ICitizenIT<'s, 'i, R> where 's: 'i {
    pub fn id(&self) -> IdI<'s, 'i, R> {
        match self {
            ICitizenIT::StructIT(s) => s.id,
            ICitizenIT::InterfaceIT(i) => i.id,
        }
    }
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StructIT<'s, 'i, R> where 's: 'i {
  pub id: IdI<'s, 'i, R>,
  pub _must_intern: MustIntern,
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StructITValI<'s, 'i, R> where 's: 'i {
  pub id: IdI<'s, 'i, R>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct InterfaceIT<'s, 'i, R> where 's: 'i {
  pub id: IdI<'s, 'i, R>,
  pub _must_intern: MustIntern,
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct InterfaceITValI<'s, 'i, R> where 's: 'i {
  pub id: IdI<'s, 'i, R>,
}

// -- Union enums for the Kind-payload interning family ---------------------
// Per architect Slab 16b: kind payloads dispatch through a tagged-union pair.
// R stays phantom — the actual per-(type×region-mode) family separation lives
// in the interner (3 HashMaps per logical type, one per region mode).

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub enum InternedKindPayloadValI<'s, 'i, R> where 's: 'i {
  StructIT(StructITValI<'s, 'i, R>),
  InterfaceIT(InterfaceITValI<'s, 'i, R>),
  StaticSizedArrayIT(StaticSizedArrayITValI<'s, 'i, R>),
  RuntimeSizedArrayIT(RuntimeSizedArrayITValI<'s, 'i, R>),
}

/// Polyvalue (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub enum InternedKindPayloadI<'s, 'i, R> where 's: 'i {
  StructIT(&'i StructIT<'s, 'i, R>),
  InterfaceIT(&'i InterfaceIT<'s, 'i, R>),
  StaticSizedArrayIT(&'i StaticSizedArrayIT<'s, 'i, R>),
  RuntimeSizedArrayIT(&'i RuntimeSizedArrayIT<'s, 'i, R>),
}
