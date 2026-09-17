// From Frontend/TypingPass/src/dev/vale/typing/

// Core entry point
pub mod compilation;
pub use compilation::{TypingPassCompilation, TypingPassOptions};

// Type system and core data structures (high priority - needed for all others)
pub mod types;
pub mod names;
pub mod ast;
pub mod templata;

// Environments and context
pub mod env;

// Basic helpers and outputs
pub mod compiler_outputs;
pub mod hinputs_t;
pub mod ptr_key;

// Top-level compiler orchestration
pub mod compiler;
pub mod typing_interner;

// Error reporting
pub mod compiler_error_humanizer;
pub mod compiler_error_reporter;

// Specific compilers
pub mod array_compiler;
pub mod convert_helper;
pub mod edge_compiler;
pub mod infer_compiler;
pub mod overload_resolver;
pub mod reachability;
pub mod sequence_compiler;
pub mod templata_compiler;

// Sub-compilers grouped by concern
pub mod citizen;
pub mod expression;
pub mod function;
pub mod infer;
pub mod macros;

// Tests
#[cfg(test)]
pub mod test;

