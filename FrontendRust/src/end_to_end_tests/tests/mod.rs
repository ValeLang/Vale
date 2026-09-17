// Per-category test modules. Each file holds the ported #[test]s for that
// TesterRust grouping (one #[test] per program × region pair).

pub mod arrays;
pub mod downcast;
pub mod externs;
pub mod ifelse;
pub mod inline;
pub mod lambdas;
pub mod misc;
pub mod native_walker;
pub mod replay;
pub mod strings;
pub mod structs;
pub mod virtuals;
pub mod while_loop;
