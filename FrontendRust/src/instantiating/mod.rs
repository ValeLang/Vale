// From Frontend/InstantiatingPass/src/dev/vale/instantiating/
pub mod ast;
// Rust-only port of Scala's reflective Collector, specialized for the I-side value AST.
pub mod collector;
pub mod instantiated_compilation;
pub mod instantiating_arena;
pub mod instantiating_interner;
pub mod reintern;
pub mod instantiated_humanizer;
pub mod region_collapser_consistent;
pub mod region_collapser_individual;
pub mod region_counter;
#[cfg(test)]
pub mod tests;
// instantiator.rs (4416-line translation engine, 70 methods): signatures
// forward-ported to the migrated AST shapes; all method bodies remain `panic!()`.
// Body migration is a separate phase.
pub mod instantiator;

pub use instantiated_compilation::{InstantiatedCompilation, InstantiatorCompilationOptions};
