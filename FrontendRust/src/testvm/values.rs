use std::cell::Cell;
use std::collections::HashMap;
use std::marker::PhantomData;
use crate::interner::StrI;
use crate::final_ast::types::{KindHT, CoordH, LocationH, OwnershipH, OpaqueHT};
use crate::final_ast::ast::{PrototypeH, StructDefinitionH};
use crate::final_ast::instructions::Local;
use crate::final_ast::types::BoolHT;
use crate::final_ast::types::FloatHT;
use crate::final_ast::types::IntHT;
use crate::final_ast::types::StrHT;
use crate::final_ast::types::VoidHT;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::testvm::vivem::ConstraintViolatedExceptionV;
use crate::testvm::vivem::PrintStream;
use crate::testvm::vivem::VmRuntimeErrorV;
use std::fmt::Display;
use std::fmt::Formatter;
use std::fmt::Result as FmtResult;
use std::io::Write;

/// Temporary state
#[derive(PartialEq, Eq, Hash, Clone, Copy)]
pub struct RRReferenceV<'v, 'h, 's>
where 's: 'h, 'h: 'v,
{
  pub hamut: CoordH<'s, 'h>,
  pub _phantom: PhantomData<&'v ()>,
}

// (Realized by `impl Hash for RRReferenceV<'v, 'h, 's>` below.)

/// Temporary state
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct RRKindV<'v, 'h, 's>
where 's: 'h, 'h: 'v,
{
  pub hamut: KindHT<'s, 'h>,
  pub _phantom: PhantomData<&'v ()>,
}

// (Realized by `impl Hash for RRKindV<'v, 'h, 's>` below.)

/// Temporary state
pub struct AllocationV<'v, 'h, 's> {
  pub reference: ReferenceV<'v, 'h, 's>,
  pub kind: KindV<'v, 'h, 's>,
  pub referrers: HashMap<IObjectReferrerV<'v, 'h, 's>, i32>,
}

impl<'v, 'h, 's> AllocationV<'v, 'h, 's> {
  pub fn id(&self) -> AllocationIdV<'v, 'h, 's> {
    panic!("Unimplemented: id");
  }

  pub fn increment_ref_count(&mut self, referrer: IObjectReferrerV<'v, 'h, 's>) {
    if matches!(self.kind, KindV::Void(_)) {
      return;
    }
    let referrers = &mut self.referrers;
    match referrer {
      IObjectReferrerV::RegisterToObjectReferrer(_) => {
        // We can have multiple of these, thats fine
      }
      _ => {
        if referrers.contains_key(&referrer) {
          panic!("nooo");
        }
      }
    }
    let current = *referrers.get(&referrer).unwrap_or(&0);
    referrers.insert(referrer, current + 1);
  }

  pub fn decrement_ref_count(&mut self, referrer: IObjectReferrerV<'v, 'h, 's>) {
    if matches!(self.kind, KindV::Void(_)) {
      return;
    }
    let referrers = &mut self.referrers;
    if !referrers.contains_key(&referrer) {
      panic!("nooooo");
    }
    let new_count = *referrers.get(&referrer).unwrap() - 1;
    referrers.insert(referrer, new_count);
    if new_count == 0 {
      referrers.remove(&referrer);
      assert!(!referrers.contains_key(&referrer));
    }
  }

  pub fn get_ref_count(&self) -> i32 {
    panic!("Unimplemented: get_ref_count");
  }

  pub fn ensure_ref_count(&self, scout_arena: &ScoutArena<'s>, maybe_ownership_filter: Option<&'v [OwnershipH]>, expected_num: i32) -> Result<(), VmRuntimeErrorV<'s>> {
    if matches!(self.kind, KindV::Void(_)) {
      // Void has no RC
      return Ok(());
    }
    let referrers: Vec<(&IObjectReferrerV<'v, 'h, 's>, &i32)> = match maybe_ownership_filter {
      None => self.referrers.iter().collect(),
      Some(ownership_filter) => self.referrers.iter().filter(|(key, _)| ownership_filter.contains(&key.ownership())).collect(),
    };
    let matching_referrers: Vec<i32> = referrers.iter().map(|(_, v)| **v).collect();
    if matching_referrers.len() as i32 != expected_num {
      let msg = format!("Expected {} of {}but was {}:\n{:?}",
        expected_num,
        maybe_ownership_filter.map(|of| format!("{:?} ", of)).unwrap_or_default(),
        matching_referrers.len(),
        matching_referrers);
      return Err(VmRuntimeErrorV::ConstraintViolatedException(ConstraintViolatedExceptionV { msg: scout_arena.intern_str(&msg) }));
    }
    Ok(())
  }

  pub fn print_refs(&self, vivem_dout: &mut PrintStream) {
    if self.get_total_ref_count(None) > 0 {
      let referrers_str = self.referrers.iter().map(|(_k, _v)| -> String { panic!("vimpl: referrers.mkString entry toString") }).collect::<Vec<_>>().join(" ");
      writeln!(vivem_dout, "o{}: {}", self.reference.alloc_id().num, referrers_str).unwrap();
    }
  }

  pub fn get_total_ref_count(&self, maybe_ownership_filter: Option<OwnershipH>) -> i32 {
    if matches!(self.kind, KindV::Void(_)) {
      return 1;
    }
    let referrers = &self.referrers;
    let result = match maybe_ownership_filter {
      None => referrers.len() as i32,
      Some(ownership_filter) => referrers.keys().filter(|k| k.ownership() == ownership_filter).count() as i32,
    };
    result
  }

  pub fn finalize(&self) {
    panic!("Unimplemented: finalize");
  }
}

// (Realized via `impl TryFrom<Wide> for Narrow` or inline match.)

// (Realized via `impl TryFrom<Wide> for Narrow` or inline match.)

/// Temporary state
#[derive(Copy, Clone, Debug)]
pub enum KindV<'v, 'h, 's> {
  Void(VoidV),
  Int(IntV<'v, 'h, 's>),
  Bool(BoolV<'v, 'h, 's>),
  Float(FloatV<'v, 'h, 's>),
  Str(StrV<'v, 'h, 's>),
  Opaque(OpaqueV<'v, 'h, 's>),
  StructInstance(&'v StructInstanceV<'v, 'h, 's>),
  ArrayInstance(&'v ArrayInstanceV<'v, 'h, 's>),
}

impl<'v, 'h, 's> KindV<'v, 'h, 's> where 's: 'h, 'h: 'v {
  pub fn tyype(&self, interner: &HammerInterner<'s, 'h>) -> RRKindV<'v, 'h, 's> {
    match self {
      KindV::Void(v) => v.tyype(interner),
      KindV::Int(v) => v.tyype(interner),
      KindV::Bool(v) => v.tyype(interner),
      KindV::Float(v) => v.tyype(interner),
      KindV::Str(v) => v.tyype(interner),
      KindV::Opaque(v) => v.tyype(interner),
      KindV::StructInstance(v) => v.tyype(interner),
      KindV::ArrayInstance(v) => v.tyype(interner),
    }
  }
}

/// Temporary state
#[derive(Copy, Clone)]
pub enum PrimitiveKindV<'v, 'h, 's> {
  Void(VoidV),
  Int(IntV<'v, 'h, 's>),
  Bool(BoolV<'v, 'h, 's>),
  Float(FloatV<'v, 'h, 's>),
  Str(StrV<'v, 'h, 's>),
  Opaque(OpaqueV<'v, 'h, 's>),
}

// Scala uses subtype polymorphism (`PrimitiveKindV extends KindV`); per SSTREX
// the Rust port uses flat sister enums, so the subset→superset conversion is
// expressed as an explicit `From` impl.
impl<'v, 'h, 's> From<PrimitiveKindV<'v, 'h, 's>> for KindV<'v, 'h, 's> {
  fn from(p: PrimitiveKindV<'v, 'h, 's>) -> Self {
    match p {
      PrimitiveKindV::Void(v) => KindV::Void(v),
      PrimitiveKindV::Int(v) => KindV::Int(v),
      PrimitiveKindV::Bool(v) => KindV::Bool(v),
      PrimitiveKindV::Float(v) => KindV::Float(v),
      PrimitiveKindV::Str(v) => KindV::Str(v),
      PrimitiveKindV::Opaque(v) => KindV::Opaque(v),
    }
  }
}

#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct VoidV;

impl VoidV {
  pub fn tyype<'v, 'h, 's>(&self, _interner: &HammerInterner<'s, 'h>) -> RRKindV<'v, 'h, 's> where 's: 'h, 'h: 'v, {
    RRKindV { hamut: KindHT::VoidHT(VoidHT), _phantom: PhantomData }
  }
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct IntV<'v, 'h, 's>
where 's: 'h, 'h: 'v,
{
  pub value: i64,
  pub bits: i32,
  pub _phantom: PhantomData<(&'v (), &'h (), &'s ())>,
}

impl<'v, 'h, 's> IntV<'v, 'h, 's> {
  pub fn tyype(&self, _interner: &HammerInterner<'s, 'h>) -> RRKindV<'v, 'h, 's> {
    RRKindV { hamut: KindHT::IntHT(IntHT { bits: self.bits }), _phantom: PhantomData }
  }
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct BoolV<'v, 'h, 's>
where 's: 'h, 'h: 'v,
{
  pub value: bool,
  pub _phantom: PhantomData<(&'v (), &'h (), &'s ())>,
}

impl<'v, 'h, 's> BoolV<'v, 'h, 's> {
  pub fn tyype(&self, _interner: &HammerInterner<'s, 'h>) -> RRKindV<'v, 'h, 's> {
    RRKindV { hamut: KindHT::BoolHT(BoolHT), _phantom: PhantomData }
  }
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Debug)]
pub struct FloatV<'v, 'h, 's>
where 's: 'h, 'h: 'v,
{
  pub value: f64,
  pub _phantom: PhantomData<(&'v (), &'h (), &'s ())>,
}

impl<'v, 'h, 's> FloatV<'v, 'h, 's> {
  pub fn tyype(&self, _interner: &HammerInterner<'s, 'h>) -> RRKindV<'v, 'h, 's> {
    RRKindV { hamut: KindHT::FloatHT(FloatHT), _phantom: PhantomData }
  }
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StrV<'v, 'h, 's>
where 's: 'h, 'h: 'v,
{
  pub value: StrI<'s>,
  pub _phantom: PhantomData<(&'v (), &'h ())>,
}

impl<'v, 'h, 's> StrV<'v, 'h, 's> {
  pub fn tyype(&self, _interner: &HammerInterner<'s, 'h>) -> RRKindV<'v, 'h, 's> {
    RRKindV { hamut: KindHT::StrHT(StrHT), _phantom: PhantomData }
  }
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct OpaqueV<'v, 'h, 's>
where 's: 'h, 'h: 'v,
{
  pub opaque_ht: OpaqueHT<'s, 'h>,
  pub _phantom: PhantomData<&'v ()>,
}

impl<'v, 'h, 's> OpaqueV<'v, 'h, 's> {
  pub fn tyype(&self, _interner: &HammerInterner<'s, 'h>) -> RRKindV<'v, 'h, 's> {
    RRKindV { hamut: KindHT::OpaqueHT(_interner.bump().alloc(self.opaque_ht)), _phantom: PhantomData }
  }
}

/// Temporary state
#[derive(Debug)]
pub struct StructInstanceV<'v, 'h, 's>
where 's: 'h, 'h: 'v,
{
  pub struct_h: StructDefinitionH<'s, 'h>,
  pub members: Cell<Option<&'v [ReferenceV<'v, 'h, 's>]>>,
}

impl<'v, 'h, 's> StructInstanceV<'v, 'h, 's> {
  pub fn tyype(&self, interner: &HammerInterner<'s, 'h>) -> RRKindV<'v, 'h, 's> {
    RRKindV { hamut: KindHT::StructHT(self.struct_h.get_ref(interner)), _phantom: PhantomData }
  }

  pub fn get_reference_member(&self, index: i32) -> ReferenceV<'v, 'h, 's> {
    let members = self.members.get().expect("StructInstance has no members");
    let (_tyype, r#ref) = (self.struct_h.members[index as usize].tyype, members[index as usize]);
    r#ref
  }

  pub fn set_reference_member(&self, vivem_bump: &'v bumpalo::Bump, index: i32, reference: ReferenceV<'v, 'h, 's>) {
    let mut new_members: Vec<ReferenceV<'v, 'h, 's>> = self.members.get().expect("StructInstance has no members").to_vec();
    new_members[index as usize] = reference;
    self.members.set(Some(vivem_bump.alloc_slice_copy(&new_members)));
  }

  pub fn zero(&self) {
    self.members.set(None);
  }
}

/// Temporary state
#[derive(Debug)]
pub struct ArrayInstanceV<'v, 'h, 's> {
  pub type_h: CoordH<'s, 'h>,
  pub element_type_h: CoordH<'s, 'h>,
  pub capacity: i32,
  pub elements: Cell<&'v [ReferenceV<'v, 'h, 's>]>,
}

impl<'v, 'h, 's> ArrayInstanceV<'v, 'h, 's> {
  pub fn tyype(&self, _interner: &HammerInterner<'s, 'h>) -> RRKindV<'v, 'h, 's> {
    RRKindV { hamut: self.type_h.kind, _phantom: PhantomData }
  }

  pub fn get_element(&self, index: i64) -> ReferenceV<'v, 'h, 's> {
    let elements = self.elements.get();
    if index < 0 || index as usize >= elements.len() {
      panic!("PanicException");
    }
    elements[index as usize]
  }

  pub fn set_element(&self, vivem_bump: &'v bumpalo::Bump, index: i64, ref_: ReferenceV<'v, 'h, 's>) {
    let elements = self.elements.get();
    if index < 0 || index as usize >= elements.len() {
      panic!("PanicException");
    }
    let mut new_vec = bumpalo::collections::Vec::with_capacity_in(elements.len(), vivem_bump);
    new_vec.extend_from_slice(elements);
    new_vec[index as usize] = ref_;
    self.elements.set(new_vec.into_bump_slice());
  }

  pub fn initialize_element(&self, vivem_bump: &'v bumpalo::Bump, ref_: ReferenceV<'v, 'h, 's>) {
    let elements = self.elements.get();
    assert!(elements.len() < self.capacity as usize);
    let mut new_vec = bumpalo::collections::Vec::with_capacity_in(elements.len() + 1, vivem_bump);
    new_vec.extend_from_slice(elements);
    new_vec.push(ref_);
    self.elements.set(new_vec.into_bump_slice());
  }

  pub fn deinitialize_element(&self) -> ReferenceV<'v, 'h, 's> {
    let elements = self.elements.get();
    assert!(!elements.is_empty());
    let r#ref = elements[elements.len() - 1];
    self.elements.set(&elements[0..elements.len() - 1]);
    r#ref
  }

  pub fn get_size(&self) -> i64 {
    self.elements.get().len() as i64
  }
}

/// Temporary state
#[derive(PartialEq, Eq, Hash, Clone, Copy)]
pub struct AllocationIdV<'v, 'h, 's> {
  pub tyype: RRKindV<'v, 'h, 's>,
  pub num: i32,
}

// (Realized by `impl Hash for AllocationIdV<'v, 'h, 's>` below.)

/// Temporary state
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub struct ReferenceV<'v, 'h, 's> {
  pub actual_kind: RRKindV<'v, 'h, 's>,
  pub seen_as_kind: RRKindV<'v, 'h, 's>,
  pub ownership: OwnershipH,
  pub location: LocationH,
  pub num: i32,
}

impl<'v, 'h, 's> ReferenceV<'v, 'h, 's> {
  pub fn alloc_id(&self) -> AllocationIdV<'v, 'h, 's> {
    AllocationIdV { tyype: RRKindV { hamut: self.actual_kind.hamut, _phantom: PhantomData }, num: self.num }
  }
  pub fn actual_coord(&self) -> RRReferenceV<'v, 'h, 's> {
    RRReferenceV {
      hamut: CoordH {
        ownership: self.ownership,
        location: self.location,
        kind: self.actual_kind.hamut,
      },
      _phantom: PhantomData,
    }
  }
  pub fn seen_as_coord(&self) -> RRReferenceV<'v, 'h, 's> {
    RRReferenceV {
      hamut: CoordH {
        ownership: self.ownership,
        location: self.location,
        kind: self.seen_as_kind.hamut,
      },
      _phantom: PhantomData,
    }
  }
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash)]
pub enum IObjectReferrerV<'v, 'h, 's> {
  VariableToObjectReferrer(VariableToObjectReferrerV<'v, 'h, 's>),
  MemberToObjectReferrer(MemberToObjectReferrerV<'v, 'h, 's>),
  ElementToObjectReferrer(ElementToObjectReferrerV<'v, 'h, 's>),
  RegisterToObjectReferrer(RegisterToObjectReferrerV<'v, 'h, 's>),
  RegisterHoldToObjectReferrer(RegisterHoldToObjectReferrerV<'v, 'h, 's>),
  ArgumentToObjectReferrer(ArgumentToObjectReferrerV<'v, 'h, 's>),
}

impl<'v, 'h, 's> IObjectReferrerV<'v, 'h, 's> {
  pub fn ownership(&self) -> OwnershipH {
    match self {
      IObjectReferrerV::VariableToObjectReferrer(r) => r.ownership,
      IObjectReferrerV::MemberToObjectReferrer(r) => r.ownership,
      IObjectReferrerV::ElementToObjectReferrer(r) => r.ownership,
      IObjectReferrerV::RegisterToObjectReferrer(r) => r.ownership,
      IObjectReferrerV::RegisterHoldToObjectReferrer(r) => r.ownership,
      IObjectReferrerV::ArgumentToObjectReferrer(r) => r.ownership,
    }
  }
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash)]
pub struct VariableToObjectReferrerV<'v, 'h, 's> {
  pub var_addr: VariableAddressV<'v, 'h, 's>,
  pub ownership: OwnershipH,
}

// (Realized by `impl Hash for VariableToObjectReferrerV` below.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash)]
pub struct MemberToObjectReferrerV<'v, 'h, 's> {
  pub member_addr: MemberAddressV<'v, 'h, 's>,
  pub ownership: OwnershipH,
}

// (Realized by `impl Hash for MemberToObjectReferrerV` below.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash)]
pub struct ElementToObjectReferrerV<'v, 'h, 's> {
  pub element_addr: ElementAddressV<'v, 'h, 's>,
  pub ownership: OwnershipH,
}

// (Realized by `impl Hash for ElementToObjectReferrerV` below.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash)]
pub struct RegisterToObjectReferrerV<'v, 'h, 's> {
  pub call_id: CallIdV<'v, 'h, 's>,
  pub ownership: OwnershipH,
}

// (Realized by `impl Hash for RegisterToObjectReferrerV` below.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash)]
pub struct RegisterHoldToObjectReferrerV<'v, 'h, 's> {
  pub expression_id: ExpressionIdV<'v, 'h, 's>,
  pub ownership: OwnershipH,
}

// (Realized by `impl Hash for RegisterHoldToObjectReferrerV` below.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash)]
pub struct ArgumentToObjectReferrerV<'v, 'h, 's> {
  pub argument_id: ArgumentIdV<'v, 'h, 's>,
  pub ownership: OwnershipH,
}

// (Realized by `impl Hash for ArgumentToObjectReferrerV` below.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash)]
pub struct VariableAddressV<'v, 'h, 's>
where 's: 'h, 'h: 'v,
{
  pub call_id: CallIdV<'v, 'h, 's>,
  pub local: Local<'s, 'h>,
}

// (Realized by `impl Display for VariableAddressV<'v, 'h, 's>` below.)

impl<'v, 'h, 's> Display for VariableAddressV<'v, 'h, 's> where 's: 'h, 'h: 'v {
  fn fmt(&self, f: &mut Formatter<'_>) -> FmtResult {
    write!(f, "*v:{}#v{}", self.call_id, self.local.id.number)
  }
}

// (Realized by `impl PartialEq for VariableAddressV<'v, 'h, 's>` below.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash)]
pub struct MemberAddressV<'v, 'h, 's> {
  pub struct_id: AllocationIdV<'v, 'h, 's>,
  pub field_index: i32,
}

impl<'v, 'h, 's> MemberAddressV<'v, 'h, 's> {
  pub fn to_string(&self) -> String {
    format!("*o:{}.{}", self.struct_id.num, self.field_index)
  }
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash)]
pub struct ElementAddressV<'v, 'h, 's> {
  pub array_id: AllocationIdV<'v, 'h, 's>,
  pub element_index: i64,
}

impl<'v, 'h, 's> ElementAddressV<'v, 'h, 's> {
  pub fn to_string(&self) -> String {
    format!("*o:{}.{}", self.array_id.num, self.element_index)
  }
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct CallIdV<'v, 'h, 's>
where 's: 'h, 'h: 'v,
{
  pub call_depth: i32,
  pub function: &'h PrototypeH<'s, 'h>,
  pub _phantom: PhantomData<&'v ()>,
}

impl<'v, 'h, 's> CallIdV<'v, 'h, 's> {
  pub fn to_string(&self) -> StrI<'s> {
    panic!("Unimplemented: to_string_call_id");
  }
}

impl<'v, 'h, 's> Display for CallIdV<'v, 'h, 's> {
  fn fmt(&self, f: &mut Formatter<'_>) -> FmtResult {
    write!(f, "ƒ{}/{}", self.call_depth, self.function.id.shortened_name.0)
  }
}

// (Realized by `impl Hash for CallIdV<'v, 'h, 's>` below.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash)]
pub struct ArgumentIdV<'v, 'h, 's> {
  pub call_id: CallIdV<'v, 'h, 's>,
  pub index: i32,
}

// (Realized by `impl Hash for ArgumentIdV<'v, 'h, 's>` below.)

/// Temporary state
#[derive(Clone)]
pub struct VariableV<'v, 'h, 's> {
  pub id: VariableAddressV<'v, 'h, 's>,
  pub reference: ReferenceV<'v, 'h, 's>,
  pub expected_type: CoordH<'s, 'h>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash)]
pub struct ExpressionIdV<'v, 'h, 's> {
  pub call_id: CallIdV<'v, 'h, 's>,
  pub path: &'v [i32],
}

impl<'v, 'h, 's> ExpressionIdV<'v, 'h, 's> {
  pub fn add_step(&self, bump: &'v bumpalo::Bump, i: i32) -> ExpressionIdV<'v, 'h, 's> {
    let old_len = self.path.len();
    let new_path: &'v mut [i32] = bump.alloc_slice_fill_with(old_len + 1, |idx| {
        if idx < old_len { self.path[idx] } else { i }
    });
    ExpressionIdV { call_id: self.call_id, path: new_path }
  }
}

/// Temporary state
pub enum RegisterV<'v, 'h, 's> {
  ReferenceRegister(&'v ReferenceRegisterV<'v, 'h, 's>),
}

impl<'v, 'h, 's> RegisterV<'v, 'h, 's> {
  pub fn expect_reference_register(&self) -> ReferenceRegisterV<'v, 'h, 's> {
    panic!("Unimplemented: expect_reference_register");
  }
}

/// Temporary state
pub struct ReferenceRegisterV<'v, 'h, 's> {
  pub reference: ReferenceV<'v, 'h, 's>,
}

// (Realized by `impl Hash for ReferenceRegisterV` below.)

/// Temporary state
pub struct VivemPanicV<'v, 'h, 's>
where 's: 'h, 'h: 'v,
{
  pub message: StrI<'s>,
  pub _phantom: PhantomData<(&'v (), &'h ())>,
}

