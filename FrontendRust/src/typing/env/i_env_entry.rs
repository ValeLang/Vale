use crate::higher_typing::ast::{FunctionA, ImplA, InterfaceA, StructA};
use crate::typing::templata::templata::ITemplataT;
use std::hash::Hash;
use std::hash::Hasher;
use std::mem::discriminant;
use std::ptr::eq;

/// Polyvalue (see @TFITCX) — derive Eq/Hash; never hand-roll `ptr::eq` on the outer `&self` (see @PVECFPZ).
#[derive(Copy, Clone, Debug)]
pub enum IEnvEntryT<'s, 't>
where 's: 't,
{
  Function(&'s FunctionA<'s>),
  Struct(&'s StructA<'s>),
  Interface(&'s InterfaceA<'s>),
  Impl(&'s ImplA<'s>),
  Templata(ITemplataT<'s, 't>),
}

// FunctionA/StructA/InterfaceA/ImplA are arena-allocated (ATDCX) and don't
// derive PartialEq/Eq/Hash. Compare/hash those variants by pointer identity;
// ITemplataT is itself Eq+Hash (Slab 3).
impl<'s, 't> PartialEq for IEnvEntryT<'s, 't>
where 's: 't,
{
  fn eq(&self, other: &Self) -> bool {
    match (self, other) {
      (IEnvEntryT::Function(a), IEnvEntryT::Function(b)) => eq(*a, *b),
      (IEnvEntryT::Struct(a), IEnvEntryT::Struct(b)) => eq(*a, *b),
      (IEnvEntryT::Interface(a), IEnvEntryT::Interface(b)) => eq(*a, *b),
      (IEnvEntryT::Impl(a), IEnvEntryT::Impl(b)) => eq(*a, *b),
      (IEnvEntryT::Templata(a), IEnvEntryT::Templata(b)) => a == b,
      _ => false,
    }
  }
  
}

impl<'s, 't> Eq for IEnvEntryT<'s, 't> where 's: 't {}
impl<'s, 't> Hash for IEnvEntryT<'s, 't>
where 's: 't,
{
  fn hash<H: Hasher>(&self, state: &mut H) {
    discriminant(self).hash(state);
    match self {
      IEnvEntryT::Function(a) => (*a as *const FunctionA<'s>).hash(state),
      IEnvEntryT::Struct(a) => (*a as *const StructA<'s>).hash(state),
      IEnvEntryT::Interface(a) => (*a as *const InterfaceA<'s>).hash(state),
      IEnvEntryT::Impl(a) => (*a as *const ImplA<'s>).hash(state),
      IEnvEntryT::Templata(t) => t.hash(state),
    }
  }
  
}

