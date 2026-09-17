// From Frontend/SimplifyingPass/src/dev/vale/simplifying/
pub mod hammer_arena;
pub mod hammer_interner;
pub mod hammer_compilation;
pub mod conversions;
// Cfg-gated as body-migration scope. Their original signatures reference
// types like `HamutsBox` (collapsed into `Hamuts` per architect directive),
// `ExpressionHammer` (renamed `ExpressionHammer`), etc. — restoring real
// signatures is body-migration work, not signature-stub-and-strip.
pub mod block_hammer;
pub mod expression_hammer;
pub mod function_hammer;
pub mod hammer;
pub mod hamuts;
pub mod let_hammer;
pub mod load_hammer;
pub mod mutate_hammer;
pub mod name_hammer;
pub mod struct_hammer;
pub mod type_hammer;
#[cfg(test)]
pub mod test;

pub use hammer_compilation::{HammerCompilation, HammerCompilationOptions};
