use crate::utils::arena_index_map::ArenaIndexMap;
use crate::postparsing::names::IRuneS;
use crate::instantiating::ast::types::{cI, CoordI, MutabilityI, VariabilityI, StructIT, InterfaceIT, ICitizenIT};
use crate::instantiating::ast::names::{IdI, IVarNameI};
use crate::instantiating::ast::ast::{ICitizenAttributeI, PrototypeI};
use std::marker::PhantomData;

pub trait CitizenDefinitionI<'s, 'i, R> {}

// Rust-only dispatch enum for the `CitizenDefinitionI` trait (architect-directed).
// Scala uses `CitizenDefinitionI` polymorphically (StructDefinitionI / InterfaceDefinitionI
// both extend it); per NEDCX we use a concrete enum here instead of a `&dyn` trait object.
// Mirrors the kind-level `ICitizenIT` dispatch enum. No Scala counterpart, so no audit-trail.
#[derive(Copy, Clone)]
pub enum ICitizenDefinitionI<'s, 'i, R> {
    StructDefinitionI(&'i StructDefinitionI<'s, 'i, R>),
    InterfaceDefinitionI(&'i InterfaceDefinitionI<'s, 'i, R>),
}
/// Temporary state
pub struct StructDefinitionI<'s, 'i, R> {
    pub instantiated_citizen: &'i StructIT<'s, 'i, cI>,
    pub attributes: &'i [ICitizenAttributeI<'s>],
    pub weakable: bool,
    pub mutability: MutabilityI,
    pub members: &'i [StructMemberI<'s, 'i, R>],
    pub is_closure: bool,
    pub rune_to_function_bound: ArenaIndexMap<'i, IRuneS<'s>, IdI<'s, 'i, cI>>,
    pub rune_to_impl_bound: ArenaIndexMap<'i, IRuneS<'s>, IdI<'s, 'i, cI>>,
}

// (Realized by `impl PartialEq for StructDefinitionI` below.)

// (Realized by `impl Hash for StructDefinitionI` below.)

impl<'s, 'i, R> StructDefinitionI<'s, 'i, R> {
    pub fn get_member_and_index(&self, needle_name: IVarNameI<'s, 'i, cI>) -> Option<(&StructMemberI<'s, 'i, R>, usize)> {
        panic!("Unimplemented: get_member_and_index")
    }
}

/// Temporary state
#[derive(PartialEq, Eq, Hash)]
pub struct StructMemberI<'s, 'i, R> {
    pub name: IVarNameI<'s, 'i, cI>,
    pub variability: VariabilityI,
    pub tyype: IMemberTypeI<'s, 'i, R>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy)]
pub enum IMemberTypeI<'s, 'i, R> {
    ReferenceMemberTypeI(&'i ReferenceMemberTypeI<'s, 'i, R>),
    AddressMemberTypeI(&'i AddressMemberTypeI<'s, 'i, R>),
}

impl<'s, 'i, R> IMemberTypeI<'s, 'i, R> {
    pub fn expect_reference_member(&self) -> () {
        match self {
            _ => panic!("Unimplemented: IMemberTypeI::expect_reference_member dispatch"),
        }
    }
}

impl<'s, 'i, R> IMemberTypeI<'s, 'i, R> {
    pub fn expect_address_member(&self) -> &'i AddressMemberTypeI<'s, 'i, R> {
        match *self {
            // BUG: Scala message says "Expected reference member, was address member!" but function is expectAddressMember; preserving Scala wording.
            IMemberTypeI::ReferenceMemberTypeI(_) => panic!("Expected reference member, was address member!"),
            IMemberTypeI::AddressMemberTypeI(a) => a,
        }
    }
}

/// Temporary state
#[derive(PartialEq, Eq, Hash)]
pub struct AddressMemberTypeI<'s, 'i, R> {
    pub reference: CoordI<'s, 'i, cI>,
    pub _marker: PhantomData<R>,
}

/// Temporary state
#[derive(PartialEq, Eq, Hash)]
pub struct ReferenceMemberTypeI<'s, 'i, R> {
    pub reference: CoordI<'s, 'i, cI>,
    pub _marker: PhantomData<R>,
}

/// Temporary state
pub struct InterfaceDefinitionI<'s, 'i, R> {
    pub instantiated_interface: &'i InterfaceIT<'s, 'i, cI>,
    pub attributes: &'i [ICitizenAttributeI<'s>],
    pub weakable: bool,
    pub mutability: MutabilityI,
    pub rune_to_function_bound: ArenaIndexMap<'i, IRuneS<'s>, IdI<'s, 'i, cI>>,
    pub rune_to_impl_bound: ArenaIndexMap<'i, IRuneS<'s>, IdI<'s, 'i, cI>>,
    pub internal_methods: &'i [(&'i PrototypeI<'s, 'i, cI>, i32)],
    pub _marker: PhantomData<R>,
}

impl<'s, 'i, R> InterfaceDefinitionI<'s, 'i, R> {
    pub fn instantiated_citizen(&self) -> ICitizenIT<'s, 'i, cI> {
        panic!("Unimplemented: instantiated_citizen")
    }
}

// (Realized by `impl PartialEq for InterfaceDefinitionI` below.)

// (Realized by `impl Hash for InterfaceDefinitionI` below.)
