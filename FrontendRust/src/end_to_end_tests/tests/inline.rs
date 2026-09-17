//! Inline-source end-to-end tests. The Vale program lives as a string in the
//! test body; the harness writes it to a tempdir before running through
//! `pass_manager::build`. Useful for tiny shape-of-the-pipeline tests where
//! a full file on disk would be overkill.
//!
//! Includes the two tests previously stuck under `#[ignore]` in
//! `pass_manager/end_to_end_test.rs` — the harness now drives the full
//! backend + clang + exec path, so they're live.

use crate::end_to_end_tests::assert_inline_compile_and_run;

#[test]
fn pass_manager_main_builds_simple_program_end_to_end() {
    assert_inline_compile_and_run("exported func main() int { return 3; }", "unsafe-fast", 3);
}

#[test]
fn pass_manager_main_builds_program_using_builtin_some() {
    assert_inline_compile_and_run(
        "exported func main() int { x = Some<int>(3); return 0; }",
        "unsafe-fast",
        0,
    );
}
