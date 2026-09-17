#![feature(box_patterns)]
#![allow(dead_code)]
#![allow(unused_variables, unused_imports)]

pub mod backend_ffi;
pub mod builtins;
pub mod clang;
pub mod compile_options;
pub mod file_coordinate_map;
pub mod final_ast;
pub mod higher_typing;
#[cfg(test)]
pub mod end_to_end_tests;
#[cfg(test)]
pub mod integration_tests;
pub mod instantiating;
pub mod interner;
pub mod keywords;
pub mod parse_arena;
pub mod scout_arena;
pub mod lexing;
pub mod parsing;
pub mod pass_manager;
pub mod postparsing;
pub mod simplifying;
pub mod typing;
pub mod tests;
#[cfg(test)]
pub mod testvm;
pub mod utils;
pub mod von;
#[path = "solver/lib.rs"]
pub mod solver;

pub use interner::StrI;
pub use keywords::Keywords;
