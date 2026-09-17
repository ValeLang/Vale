
use std::hash::{Hash, Hasher};
use std::fmt::Debug;
use std::fmt::Formatter;
use std::fmt::Result;
use std::ptr::eq;

/// Value-type (see @TFITCX)
///
/// PtrKey wraps a `&'t T` and hashes/compares by the *outer* pointer address —
/// i.e. where in memory the wrapped `T` lives, not the content of `T`.
///
/// **Use only for `T` whose identity is by-address** per @IEOIBZ — that is,
/// `/// Arena-allocated` types where two distinct allocations are distinct things
/// (e.g. `IEnvironmentT`, `FunctionA`, `FunctionHeaderT`, expression nodes).
///
/// **Do not use for types with canonical content-based Hash/Eq.** `IdT`,
/// `SignatureT`, `PrototypeT`, and other Interned/Value-types already implement
/// content-canonical Hash/Eq via interner-deduplicated inner pointers — wrapping
/// them in `PtrKey` is redundant for canonical-ref insertions and **incorrect**
/// when constructed from a by-value Copy of the type held in a struct field
/// (the outer address differs from the canonical interner ref). Use the bare
/// type as the map key instead.
pub struct PtrKey<'t, T: ?Sized>(pub &'t T);

impl<'t, T: ?Sized> PartialEq for PtrKey<'t, T> {
    fn eq(&self, other: &Self) -> bool {
        eq(self.0, other.0)
    }
}

impl<'t, T: ?Sized> Eq for PtrKey<'t, T> {}

impl<'t, T: ?Sized> Hash for PtrKey<'t, T> {
    fn hash<H: Hasher>(&self, state: &mut H) {
        (self.0 as *const T as *const ()).hash(state)
    }
}

impl<'t, T: ?Sized> Copy for PtrKey<'t, T> {}

impl<'t, T: ?Sized> Clone for PtrKey<'t, T> {
    fn clone(&self) -> Self { *self }
}

impl<'t, T: ?Sized + Debug> Debug for PtrKey<'t, T> {
    fn fmt(&self, f: &mut Formatter<'_>) -> Result {
        write!(f, "PtrKey({:p})", self.0 as *const T)
    }
}
