// Arena-backed slice allocation helpers for AST construction.
//
// INVARIANTS (see plan: Arena-Backed AST Slices):
// - 'a: interner arena only (canonical/interned data)
// - 'p: parsed AST arena (FileP graph)
// - 's: postparsed AST arena (ProgramS graph)
//
// These helpers allocate in a Bump; do NOT route parsed/postparsed AST
// collections through Interner or the interner arena.

use bumpalo::Bump;

/// Allocate a copy of a slice in the arena.
/// For `Copy` payloads (e.g. RangeL, NameP).
#[inline(always)]
pub fn alloc_slice_copy<'x, T: Copy>(arena: &'x Bump, slice: &[T]) -> &'x [T] {
  arena.alloc_slice_copy(slice)
}

/// Allocate a slice from an iterator into the arena.
/// For non-Copy node vectors (e.g. Vec<IExpressionPE<'a>>).
/// Iterator must be ExactSizeIterator (e.g. Vec::into_iter()).
#[inline(always)]
pub fn alloc_slice_fill_iter<'x, T, I>(arena: &'x Bump, iter: I) -> &'x [T]
where
  I: IntoIterator<Item = T>,
  I::IntoIter: ExactSizeIterator,
{
  arena.alloc_slice_fill_iter(iter)
}

/// Convenience: allocate a slice from a Vec (moves elements).
#[inline(always)]
pub fn alloc_slice_from_vec<'x, T>(arena: &'x Bump, vec: Vec<T>) -> &'x [T] {
  arena.alloc_slice_fill_iter(vec.into_iter())
}

/// Convenience: allocate a slice from a Vec of refs.
#[inline(always)]
pub fn alloc_slice_from_vec_of_refs<'x, T>(arena: &'x Bump, vec: Vec<&'x T>) -> &'x [&'x T] {
  arena.alloc_slice_copy(&vec)
}
