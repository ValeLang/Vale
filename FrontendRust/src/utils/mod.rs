use std::result::Result as StdResult;

pub mod arena_index_map;
pub mod arena_utils;
pub mod code_hierarchy;
pub mod profiler;
pub mod range;
pub mod source_code_utils;
pub mod utils;
pub mod vassert;

/// Result type matching Scala's Result[T, E]
pub type Result<T, E> = StdResult<T, E>;

/// Scala's vpass() — a deliberate no-op used as a breakpoint target for debugging.
#[inline(always)]
pub fn vpass() {}
