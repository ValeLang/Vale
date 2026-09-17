// Named wrapper around the instantiating-pass bump arena (`&'i Bump`).
//
// Currently a thin newtype around `&'i Bump`. Forward-compatible with future
// arena-deterministic maps work; downstream callers should depend on the named
// `InstantiatingArena<'i>` type, not on `Bump` directly.

use bumpalo::Bump;

/// Temporary state (see @TFITCX)
pub struct InstantiatingArena<'i> {
    bump: &'i Bump,
}

impl<'i> InstantiatingArena<'i> {
    pub fn new(bump: &'i Bump) -> Self {
        InstantiatingArena { bump }
    }

    pub fn bump(&self) -> &'i Bump { self.bump }
}
