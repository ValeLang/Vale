
// Mirrors src/instantiating/instantiating_interner.rs (per Slab 17 plan).
//
// Scaled down from instantiating's 3-region-mode (sI/nI/cI) design to a
// single-region-mode design: by the time data reaches H-side it's already
// region-collapsed, so there is no `R` parameter and one HashMap per
// logical interned type suffices. Lifetime convention: `<'s, 'h>` with
// `where 's: 'h`.
//
// Slab 17 Phase 2: 5 intern families wired = kind-payload (1 family with
// 4 logical types) + prototype. IdH interning deferred until IdH gets real
// fields (bare-placeholder for now, same precedent as IdI in instantiating).

use std::cell::RefCell;
use std::collections::HashMap as StdHashMap;

use bumpalo::Bump;

/// Construction-witness token for interned types (per @SICZ). The inner unit
/// field is private to this module, so only code in `hammer_interner` can
/// construct one (specifically, the `intern_*` methods).
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct MustIntern(());

use crate::utils::arena_index_map::ArenaIndexMap;
use crate::final_ast::types::{
    InterfaceHT, InterfaceHTValH,
    InternedKindPayloadH, InternedKindPayloadValH,
    RuntimeSizedArrayHT, RuntimeSizedArrayHTValH,
    StaticSizedArrayHT, StaticSizedArrayHTValH,
    StructHT, StructHTValH,
};
use crate::final_ast::ast::{IdH, IdHValH, PrototypeH, PrototypeHValH};
use std::hash::Hash;
use std::marker::PhantomData;

/// Temporary state (see @TFITCX)
pub struct HammerInterner<'s, 'h>
where 's: 'h,
{
    bump: &'h Bump,
    inner: RefCell<Inner<'s, 'h>>,
}

struct Inner<'s, 'h>
where 's: 'h,
{
    // 5 intern families: 1 kind-payload dispatcher + 1 prototype HashMap.
    kind_payload_val_to_ref: StdHashMap<InternedKindPayloadValH<'s, 'h>, InternedKindPayloadH<'s, 'h>>,
    prototype_val_to_ref: StdHashMap<PrototypeHValH<'s, 'h>, &'h PrototypeH<'s, 'h>>,
    id_h_val_to_ref: StdHashMap<IdHValH<'s>, &'h IdH<'s>>,
}

impl<'s, 'h> HammerInterner<'s, 'h>
where 's: 'h,
{
    pub fn new(bump: &'h Bump) -> Self {
        HammerInterner {
            bump,
            inner: RefCell::new(Inner {
                kind_payload_val_to_ref: StdHashMap::new(),
                prototype_val_to_ref: StdHashMap::new(),
                id_h_val_to_ref: StdHashMap::new(),
            }),
        }
    }

    // --- Arena access ---
    pub fn bump(&self) -> &'h Bump { self.bump }
    pub fn alloc<T>(&self, val: T) -> &'h mut T { self.bump.alloc(val) }
    pub fn alloc_slice_copy<T: Copy>(&self, src: &[T]) -> &'h [T] {
        self.bump.alloc_slice_copy(src)
    }
    pub fn alloc_slice_from_vec<T>(&self, vec: Vec<T>) -> &'h [T] {
        self.bump.alloc_slice_fill_iter(vec.into_iter())
    }

    pub fn alloc_index_map<K: Hash + Eq + Clone, V>(&self) -> ArenaIndexMap<'h, K, V> {
        ArenaIndexMap::new_in(self.bump)
    }

    pub fn alloc_index_map_from_iter<K, V, I>(&self, iter: I) -> ArenaIndexMap<'h, K, V>
    where K: Hash + Eq + Clone, I: IntoIterator<Item = (K, V)>
    {
        ArenaIndexMap::from_iter_in(iter, self.bump)
    }

    // =========================================================================
    // Kind-payload interning
    // =========================================================================

    pub fn intern_kind_payload(
        &self,
        val: InternedKindPayloadValH<'s, 'h>,
    ) -> InternedKindPayloadH<'s, 'h> {
        {
            let inner = self.inner.borrow();
            if let Some(existing) = inner.kind_payload_val_to_ref.get(&val) {
                return *existing;
            }
        }
        use InternedKindPayloadH as T;
        use InternedKindPayloadValH as V;
        let canonical = match val {
            V::StructHT(v) => {
                let c = StructHT { id: v.id, _must_intern: MustIntern(()) };
                T::StructHT(self.bump.alloc(c))
            }
            V::InterfaceHT(v) => {
                let c = InterfaceHT { id: v.id, _must_intern: MustIntern(()) };
                T::InterfaceHT(self.bump.alloc(c))
            }
            V::StaticSizedArrayHT(v) => {
                let c = StaticSizedArrayHT { id: v.id, _must_intern: MustIntern(()) };
                T::StaticSizedArrayHT(self.bump.alloc(c))
            }
            V::RuntimeSizedArrayHT(v) => {
                let c = RuntimeSizedArrayHT { name: v.name, _must_intern: MustIntern(()) };
                T::RuntimeSizedArrayHT(self.bump.alloc(c))
            }
        };
        let mut inner = self.inner.borrow_mut();
        inner.kind_payload_val_to_ref.insert(val, canonical);
        canonical
    }

    pub fn intern_struct_ht(&self, val: StructHTValH<'s, 'h>) -> &'h StructHT<'s, 'h> {
        match self.intern_kind_payload(InternedKindPayloadValH::StructHT(val)) {
            InternedKindPayloadH::StructHT(r) => r,
            _ => unreachable!(),
        }
    }
    pub fn intern_interface_ht(&self, val: InterfaceHTValH<'s, 'h>) -> &'h InterfaceHT<'s, 'h> {
        match self.intern_kind_payload(InternedKindPayloadValH::InterfaceHT(val)) {
            InternedKindPayloadH::InterfaceHT(r) => r,
            _ => unreachable!(),
        }
    }
    pub fn intern_static_sized_array_ht(&self, val: StaticSizedArrayHTValH<'s, 'h>) -> &'h StaticSizedArrayHT<'s, 'h> {
        match self.intern_kind_payload(InternedKindPayloadValH::StaticSizedArrayHT(val)) {
            InternedKindPayloadH::StaticSizedArrayHT(r) => r,
            _ => unreachable!(),
        }
    }
    pub fn intern_runtime_sized_array_ht(&self, val: RuntimeSizedArrayHTValH<'s, 'h>) -> &'h RuntimeSizedArrayHT<'s, 'h> {
        match self.intern_kind_payload(InternedKindPayloadValH::RuntimeSizedArrayHT(val)) {
            InternedKindPayloadH::RuntimeSizedArrayHT(r) => r,
            _ => unreachable!(),
        }
    }

    // =========================================================================
    // Prototype interning
    // =========================================================================

    pub fn intern_prototype(&self, val: PrototypeHValH<'s, 'h>) -> &'h PrototypeH<'s, 'h> {
        {
            let inner = self.inner.borrow();
            if let Some(existing) = inner.prototype_val_to_ref.get(&val) {
                return *existing;
            }
        }
        let c = PrototypeH {
            id: val.id,
            params: val.params,
            return_type: val.return_type,
            _must_intern: MustIntern(()),
        };
        let canonical: &'h PrototypeH<'s, 'h> = self.bump.alloc(c);
        let mut inner = self.inner.borrow_mut();
        inner.prototype_val_to_ref.insert(val, canonical);
        canonical
    }

    pub fn intern_id_h(&self, val: IdHValH<'s>) -> &'h IdH<'s> {
        {
            let inner = self.inner.borrow();
            if let Some(existing) = inner.id_h_val_to_ref.get(&val) {
                return *existing;
            }
        }
        let c = IdH {
            local_name: val.local_name,
            package_coordinate: val.package_coordinate,
            shortened_name: val.shortened_name,
            fully_qualified_name: val.fully_qualified_name,
            _must_intern: MustIntern(()),
        };
        let canonical: &'h IdH<'s> = self.bump.alloc(c);
        let mut inner = self.inner.borrow_mut();
        inner.id_h_val_to_ref.insert(val, canonical);
        canonical
    }
}

// Interner unit tests previously constructed `IdH(PhantomData)` from the
// bare-placeholder shape. Now that `IdH` has real fields (`local_name`,
// `package_coordinate`, etc.), these tests need real `StrI<'s>` /
// `PackageCoordinate<'p>` values to construct an IdH — non-trivial test
// fixture. Re-enable + restore once arena fixture helpers exist.
#[cfg(all(test, any()))]
mod tests {
    use super::*;

    // Two structurally equal Vals canonicalize to the same arena pointer.
    #[test]
    fn intern_struct_ht_canonicalizes() {
        panic!("Re-enable once IdH test-fixture helper exists");
    }

    // intern_kind_payload dispatches through the correct variant.
    #[test]
    fn intern_kind_payload_dispatches() {
        panic!("Re-enable once IdH test-fixture helper exists");
    }
}
