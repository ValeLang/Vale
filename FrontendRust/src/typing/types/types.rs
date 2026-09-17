use crate::postparsing::names::IImpreciseNameS;
use crate::typing::names::names::*;
use crate::typing::env::environment::*;
use crate::typing::templata::templata::ITemplataT;
use crate::typing::typing_interner::MustIntern;

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum OwnershipT {
    Share,
    Own,
    Borrow,
    Weak,
}

// merged into OwnershipT above

// merged into OwnershipT above

// merged into OwnershipT above

// merged into OwnershipT above

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum MutabilityT {
    Mutable,
    Immutable,
}

// merged into MutabilityT above

// merged into MutabilityT above

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum VariabilityT {
    Final,
    Varying,
}

// merged into VariabilityT above

// merged into VariabilityT above

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum LocationT {
    Inline,
    Yonder,
}

// merged into LocationT above

// merged into LocationT above

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IRegionT {
  Iso,
  // TODO: Get rid of this when we have an actual default region
  Default,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct RegionT {
  pub region: IRegionT,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct CoordT<'s, 't> {
  pub ownership: OwnershipT,
  pub region: RegionT,
  pub kind: KindT<'s, 't>,
}

// KindT is inline-owned (not arena-interned). Concrete non-primitive payloads
// (StructTT, InterfaceTT, etc.) are arena-interned and held as &'t refs here.
// Primitives inline by value; compound types use &'t to keep the enum small (see @WVSBIZ).
/// Polyvalue (see @TFITCX) — derive Eq/Hash; never hand-roll `ptr::eq` on the outer `&self` (see @PVECFPZ).
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum KindT<'s, 't> {
  Never(NeverT),
  Void(VoidT),
  Int(IntT),
  Bool(BoolT),
  Str(StrT),
  Float(FloatT),
  Struct(&'t StructTT<'s, 't>),
  Interface(&'t InterfaceTT<'s, 't>),
  StaticSizedArray(&'t StaticSizedArrayTT<'s, 't>),
  RuntimeSizedArray(&'t RuntimeSizedArrayTT<'s, 't>),
  KindPlaceholder(&'t KindPlaceholderT<'s, 't>),
  OverloadSet(&'t OverloadSetT<'s, 't>),
}

impl<'s, 't> KindT<'s, 't> {
  pub fn expect_citizen(&self) -> ICitizenTT<'s, 't> {
    match self {
      KindT::Struct(c) => ICitizenTT::Struct(c),
      KindT::Interface(c) => ICitizenTT::Interface(c),
      _ => panic!("vfail"),
    }
  }
  
  pub fn expect_interface(&self) -> &'t InterfaceTT<'s, 't> {
    match self {
      KindT::Interface(c) => c,
      _ => panic!("vfail"),
    }
  }
  
  pub fn expect_struct(&self) -> &'t StructTT<'s, 't> {
    match self {
      KindT::Struct(c) => c,
      _ => panic!("vfail"),
    }
  }
  
  pub fn is_primitive(&self) -> bool {
    match self {
      KindT::Never(_) => true,
      KindT::Void(_) => true,
      KindT::Int(_) => true,
      KindT::Bool(_) => true,
      KindT::Str(_) => false,
      KindT::Float(_) => true,
      KindT::Struct(_) => false,
      KindT::Interface(_) => false,
      KindT::StaticSizedArray(_) => false,
      KindT::RuntimeSizedArray(_) => false,
      KindT::KindPlaceholder(_) => false,
      KindT::OverloadSet(_) => true,
    }
  }
  
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct NeverT {
  pub from_break: bool,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct VoidT;

impl IntT {
    pub const I32: IntT = IntT { bits: 32 };
    pub const I64: IntT = IntT { bits: 64 };

}
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct IntT {
  pub bits: i32,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct BoolT;

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StrT;

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct FloatT;

fn unapply_contents_static_sized_array_tt() {
  panic!("Unimplemented: unapply_contents_static_sized_array_tt");
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StaticSizedArrayTT<'s, 't> {
  pub name: IdT<'s, 't>,
  pub _must_intern: MustIntern,
}

impl<'s, 't> StaticSizedArrayTT<'s, 't> where 's: 't {
  pub fn mutability(&self) -> ITemplataT<'s, 't> {
    match self.name.local_name {
      INameT::StaticSizedArray(ssa_name) => ssa_name.arr.mutability,
      _ => panic!("vwat"),
    }
  }
  
  pub fn element_type(&self) -> CoordT<'s, 't> {
    match self.name.local_name {
      INameT::StaticSizedArray(ssa_name) => ssa_name.arr.element_type,
      _ => panic!("vwat"),
    }
  }
  
  pub fn size(&self) -> ITemplataT<'s, 't> {
    match self.name.local_name {
      INameT::StaticSizedArray(ssa_name) => ssa_name.size,
      _ => panic!("vwat"),
    }
  }
  
  pub fn variability(&self) -> ITemplataT<'s, 't> {
    match self.name.local_name {
      INameT::StaticSizedArray(ssa_name) => ssa_name.variability,
      _ => panic!("vwat"),
    }
  }
  
}

fn unapply_contents_runtime_sized_array_tt() {
  panic!("Unimplemented: unapply_contents_runtime_sized_array_tt");
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StaticSizedArrayTTValT<'s, 't> {
  pub name: IdT<'s, 't>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct RuntimeSizedArrayTT<'s, 't> {
  pub name: IdT<'s, 't>,
  pub _must_intern: MustIntern,
}

impl<'s, 't> RuntimeSizedArrayTT<'s, 't> where 's: 't {
  pub fn mutability(&self) -> ITemplataT<'s, 't> {
    match self.name.local_name {
      INameT::RuntimeSizedArray(rsa_name) => rsa_name.arr.mutability,
      _ => panic!("vwat"),
    }
  }
  
  pub fn element_type(&self) -> CoordT<'s, 't> {
    match self.name.local_name {
      INameT::RuntimeSizedArray(rsa_name) => rsa_name.arr.element_type,
      _ => panic!("vwat"),
    }
  }
  
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct RuntimeSizedArrayTTValT<'s, 't> {
  pub name: IdT<'s, 't>,
}

fn unapply_i_citizen_tt() {
  panic!("Unimplemented: unapply_i_citizen_tt");
}

// Inline-owned wrapper enum; concrete payloads are arena-interned &'t refs.
/// Polyvalue (see @TFITCX) — derive Eq/Hash; never hand-roll `ptr::eq` on the outer `&self` (see @PVECFPZ).
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ISubKindTT<'s, 't> {
  Struct(&'t StructTT<'s, 't>),
  Interface(&'t InterfaceTT<'s, 't>),
  KindPlaceholder(&'t KindPlaceholderT<'s, 't>),
}

impl<'s, 't> ISubKindTT<'s, 't> where 's: 't {
    pub fn id(&self) -> IdT<'s, 't> {
        match self {
            ISubKindTT::Struct(s) => s.id,
            ISubKindTT::Interface(i) => i.id,
            ISubKindTT::KindPlaceholder(kp) => kp.id,
        }
    }
    
    pub fn expect_citizen(&self) -> ICitizenTT<'s, 't> {
        match self {
            ISubKindTT::Struct(s) => ICitizenTT::Struct(s),
            ISubKindTT::Interface(i) => ICitizenTT::Interface(i),
            ISubKindTT::KindPlaceholder(_) => panic!("vfail"),
        }
    }
    
    pub fn expect_interface(&self) -> &'t InterfaceTT<'s, 't> {
        KindT::from(*self).expect_interface()
    }
    
    pub fn expect_struct(&self) -> &'t StructTT<'s, 't> {
        KindT::from(*self).expect_struct()
    }
    
    pub fn is_primitive(&self) -> bool {
        KindT::from(*self).is_primitive()
    }
    
}

// Inline-owned wrapper enum; concrete payloads are arena-interned &'t refs.
/// Polyvalue (see @TFITCX) — derive Eq/Hash; never hand-roll `ptr::eq` on the outer `&self` (see @PVECFPZ).
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ISuperKindTT<'s, 't> {
  Interface(&'t InterfaceTT<'s, 't>),
  KindPlaceholder(&'t KindPlaceholderT<'s, 't>),
}

impl<'s, 't> ISuperKindTT<'s, 't> where 's: 't {
    pub fn id(&self) -> IdT<'s, 't> {
        match self {
            ISuperKindTT::Interface(i) => i.id,
            ISuperKindTT::KindPlaceholder(kp) => kp.id,
        }
    }
    
    pub fn expect_citizen(&self) -> ICitizenTT<'s, 't> {
        KindT::from(*self).expect_citizen()
    }
    
    pub fn expect_interface(&self) -> &'t InterfaceTT<'s, 't> {
        KindT::from(*self).expect_interface()
    }
    
    pub fn expect_struct(&self) -> &'t StructTT<'s, 't> {
        KindT::from(*self).expect_struct()
    }
    
    pub fn is_primitive(&self) -> bool {
        KindT::from(*self).is_primitive()
    }
    
}

// Inline-owned wrapper enum; concrete payloads are arena-interned &'t refs.
/// Polyvalue (see @TFITCX) — derive Eq/Hash; never hand-roll `ptr::eq` on the outer `&self` (see @PVECFPZ).
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ICitizenTT<'s, 't> {
  Struct(&'t StructTT<'s, 't>),
  Interface(&'t InterfaceTT<'s, 't>),
}

impl<'s, 't> ICitizenTT<'s, 't> where 's: 't {
    pub fn id(&self) -> IdT<'s, 't> {
        match self {
            ICitizenTT::Struct(s) => s.id,
            ICitizenTT::Interface(i) => i.id,
        }
    }
    
    pub fn expect_citizen(&self) -> ICitizenTT<'s, 't> {
        *self
    }
    
    pub fn expect_interface(&self) -> &'t InterfaceTT<'s, 't> {
        KindT::from(*self).expect_interface()
    }
    
    pub fn expect_struct(&self) -> &'t StructTT<'s, 't> {
        KindT::from(*self).expect_struct()
    }
    
    pub fn is_primitive(&self) -> bool {
        KindT::from(*self).is_primitive()
    }
    
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StructTT<'s, 't> {
  pub id: IdT<'s, 't>,
  pub _must_intern: MustIntern,
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StructTTValT<'s, 't> {
  pub id: IdT<'s, 't>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct InterfaceTT<'s, 't> {
  pub id: IdT<'s, 't>,
  pub _must_intern: MustIntern,
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct InterfaceTTValT<'s, 't> {
  pub id: IdT<'s, 't>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct OverloadSetT<'s, 't> {
  pub env: IInDenizenEnvironmentT<'s, 't>,
  pub name: &'s IImpreciseNameS<'s>,
  pub _must_intern: MustIntern,
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct OverloadSetTValT<'s, 't> {
  pub env: IInDenizenEnvironmentT<'s, 't>,
  pub name: &'s IImpreciseNameS<'s>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct KindPlaceholderT<'s, 't> {
  pub id: IdT<'s, 't>,
}

// -- Simple / shallow concretes (reuse struct itself as Val) ------------------
// The 6 concrete Kind payloads above (StructTT, InterfaceTT, StaticSizedArrayTT,
// RuntimeSizedArrayTT, KindPlaceholderT, OverloadSetT) are arena-interned but
// have no `&'t [...]` slice fields — each holds either a canonical IdT<'s, 't>
// (canonicalized by IdValT before this Val is constructed) or scout-lifetime
// refs only (OverloadSetT). So their permanent struct doubles as the lookup
// Val. No separate `*ValT` type is defined for any of them.
//
// The wrapper enums KindT / ICitizenTT / ISubKindTT / ISuperKindTT are
// inline-owned 16-byte Copy values (never arena-allocated), so they don't
// need Val companions either. Casts between them are `match`-and-rewrap via
// the From/TryFrom bridges below.

// -- Union enums for the Kind-payload interning family ----------------------
// Per handoff-slab-4.md Gotcha 2: typing interner uses one HashMap per
// sealed-trait family with a tagged-union Val key. These mirror scout's
// INameValS/INameS pattern.
//
// Dispatch enums for kind interning — these types are interned per @WVSBIZ
// (enum budget: KindT stores them behind &'t to stay small and Copy).
/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub enum InternedKindPayloadValT<'s, 't>
where 's: 't,
{
  StructTT(StructTTValT<'s, 't>),
  InterfaceTT(InterfaceTTValT<'s, 't>),
  StaticSizedArrayTT(StaticSizedArrayTTValT<'s, 't>),
  RuntimeSizedArrayTT(RuntimeSizedArrayTTValT<'s, 't>),
  KindPlaceholder(KindPlaceholderT<'s, 't>),
  OverloadSet(OverloadSetTValT<'s, 't>),
}

/// Polyvalue (see @TFITCX) — derive Eq/Hash; never hand-roll `ptr::eq` on the outer `&self` (see @PVECFPZ).
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub enum InternedKindPayloadT<'s, 't>
where 's: 't,
{
  StructTT(&'t StructTT<'s, 't>),
  InterfaceTT(&'t InterfaceTT<'s, 't>),
  StaticSizedArrayTT(&'t StaticSizedArrayTT<'s, 't>),
  RuntimeSizedArrayTT(&'t RuntimeSizedArrayTT<'s, 't>),
  KindPlaceholder(&'t KindPlaceholderT<'s, 't>),
  OverloadSet(&'t OverloadSetT<'s, 't>),
}

// -- From bridges: concrete payload → each wrapper enum it belongs to --------

impl<'s, 't> From<&'t StructTT<'s, 't>> for ICitizenTT<'s, 't> {
  fn from(x: &'t StructTT<'s, 't>) -> Self { ICitizenTT::Struct(x) }
  
}
impl<'s, 't> From<&'t StructTT<'s, 't>> for ISubKindTT<'s, 't> {
  fn from(x: &'t StructTT<'s, 't>) -> Self { ISubKindTT::Struct(x) }
  
}
impl<'s, 't> From<&'t StructTT<'s, 't>> for KindT<'s, 't> {
  fn from(x: &'t StructTT<'s, 't>) -> Self { KindT::Struct(x) }
  
}

impl<'s, 't> From<&'t InterfaceTT<'s, 't>> for ICitizenTT<'s, 't> {
  fn from(x: &'t InterfaceTT<'s, 't>) -> Self { ICitizenTT::Interface(x) }
  
}
impl<'s, 't> From<&'t InterfaceTT<'s, 't>> for ISubKindTT<'s, 't> {
  fn from(x: &'t InterfaceTT<'s, 't>) -> Self { ISubKindTT::Interface(x) }
  
}
impl<'s, 't> From<&'t InterfaceTT<'s, 't>> for ISuperKindTT<'s, 't> {
  fn from(x: &'t InterfaceTT<'s, 't>) -> Self { ISuperKindTT::Interface(x) }
  
}
impl<'s, 't> From<&'t InterfaceTT<'s, 't>> for KindT<'s, 't> {
  fn from(x: &'t InterfaceTT<'s, 't>) -> Self { KindT::Interface(x) }
  
}

impl<'s, 't> From<&'t StaticSizedArrayTT<'s, 't>> for KindT<'s, 't> {
  fn from(x: &'t StaticSizedArrayTT<'s, 't>) -> Self { KindT::StaticSizedArray(x) }
  
}

impl<'s, 't> From<&'t RuntimeSizedArrayTT<'s, 't>> for KindT<'s, 't> {
  fn from(x: &'t RuntimeSizedArrayTT<'s, 't>) -> Self { KindT::RuntimeSizedArray(x) }
  
}

impl<'s, 't> From<&'t KindPlaceholderT<'s, 't>> for ISubKindTT<'s, 't> {
  fn from(x: &'t KindPlaceholderT<'s, 't>) -> Self { ISubKindTT::KindPlaceholder(x) }
  
}
impl<'s, 't> From<&'t KindPlaceholderT<'s, 't>> for ISuperKindTT<'s, 't> {
  fn from(x: &'t KindPlaceholderT<'s, 't>) -> Self { ISuperKindTT::KindPlaceholder(x) }
  
}
impl<'s, 't> From<&'t KindPlaceholderT<'s, 't>> for KindT<'s, 't> {
  fn from(x: &'t KindPlaceholderT<'s, 't>) -> Self { KindT::KindPlaceholder(x) }
  
}

impl<'s, 't> From<&'t OverloadSetT<'s, 't>> for KindT<'s, 't> {
  fn from(x: &'t OverloadSetT<'s, 't>) -> Self { KindT::OverloadSet(x) }
  
}

// -- From bridges: narrow sub-enum → wider sub-enum / KindT ------------------

impl<'s, 't> From<ICitizenTT<'s, 't>> for ISubKindTT<'s, 't> {
  fn from(c: ICitizenTT<'s, 't>) -> Self {
    match c {
      ICitizenTT::Struct(x) => ISubKindTT::Struct(x),
      ICitizenTT::Interface(x) => ISubKindTT::Interface(x),
    }
  }
  
}
impl<'s, 't> From<ICitizenTT<'s, 't>> for KindT<'s, 't> {
  fn from(c: ICitizenTT<'s, 't>) -> Self {
    match c {
      ICitizenTT::Struct(x) => KindT::Struct(x),
      ICitizenTT::Interface(x) => KindT::Interface(x),
    }
  }
  
}
impl<'s, 't> From<ISubKindTT<'s, 't>> for KindT<'s, 't> {
  fn from(s: ISubKindTT<'s, 't>) -> Self {
    match s {
      ISubKindTT::Struct(x) => KindT::Struct(x),
      ISubKindTT::Interface(x) => KindT::Interface(x),
      ISubKindTT::KindPlaceholder(x) => KindT::KindPlaceholder(x),
    }
  }
  
}
impl<'s, 't> From<ISuperKindTT<'s, 't>> for KindT<'s, 't> {
  fn from(s: ISuperKindTT<'s, 't>) -> Self {
    match s {
      ISuperKindTT::Interface(x) => KindT::Interface(x),
      ISuperKindTT::KindPlaceholder(x) => KindT::KindPlaceholder(x),
    }
  }
  
}

// -- TryFrom bridges: wider → narrower ---------------------------------------

impl<'s, 't> TryFrom<KindT<'s, 't>> for ICitizenTT<'s, 't> {
  type Error = ();
  fn try_from(k: KindT<'s, 't>) -> Result<Self, ()> {
    match k {
      KindT::Struct(x) => Ok(ICitizenTT::Struct(x)),
      KindT::Interface(x) => Ok(ICitizenTT::Interface(x)),
      _ => Err(()),
    }
  }
  
}
impl<'s, 't> TryFrom<KindT<'s, 't>> for ISubKindTT<'s, 't> {
  type Error = ();
  fn try_from(k: KindT<'s, 't>) -> Result<Self, ()> {
    match k {
      KindT::Struct(x) => Ok(ISubKindTT::Struct(x)),
      KindT::Interface(x) => Ok(ISubKindTT::Interface(x)),
      KindT::KindPlaceholder(x) => Ok(ISubKindTT::KindPlaceholder(x)),
      _ => Err(()),
    }
  }
  
}
impl<'s, 't> TryFrom<KindT<'s, 't>> for ISuperKindTT<'s, 't> {
  type Error = ();
  fn try_from(k: KindT<'s, 't>) -> Result<Self, ()> {
    match k {
      KindT::Interface(x) => Ok(ISuperKindTT::Interface(x)),
      KindT::KindPlaceholder(x) => Ok(ISuperKindTT::KindPlaceholder(x)),
      _ => Err(()),
    }
  }
  
}
impl<'s, 't> TryFrom<ISubKindTT<'s, 't>> for ICitizenTT<'s, 't> {
  type Error = ();
  fn try_from(s: ISubKindTT<'s, 't>) -> Result<Self, ()> {
    match s {
      ISubKindTT::Struct(x) => Ok(ICitizenTT::Struct(x)),
      ISubKindTT::Interface(x) => Ok(ICitizenTT::Interface(x)),
      _ => Err(()),
    }
  }
  
}
