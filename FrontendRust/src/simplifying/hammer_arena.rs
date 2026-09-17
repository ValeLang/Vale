// Named wrapper around the simplifying-pass bump arena (`&'h Bump`).
//
// Currently a thin newtype around `&'h Bump`. Mirrors src/instantiating/instantiating_arena.rs.

use bumpalo::Bump;

/// Temporary state (see @TFITCX)
pub struct HammerArena<'h> {
    bump: &'h Bump,
}

impl<'h> HammerArena<'h> {
    pub fn new(bump: &'h Bump) -> Self {
        HammerArena { bump }
    }

    pub fn bump(&self) -> &'h Bump { self.bump }
}
