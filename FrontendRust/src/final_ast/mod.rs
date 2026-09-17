// From Frontend/FinalAST/src/dev/vale/finalast/
//
// H-side output AST. Mirrors Scala's `dev.vale.finalast` package.
pub mod types;
pub mod ast;
pub mod instructions;
pub use types::*;
pub use ast::*;
pub use instructions::*;

// Test-only traversal/collector scaffolding (no Scala counterpart; mirrors
// typing/test/traverse.rs). Public so other passes' tests (e.g. simplifying) can use it.
#[cfg(test)]
pub mod test;
