use crate::end_to_end_tests::{assert_compile_and_run, programs_dir};

fn p(rel: &str) -> std::path::PathBuf {
    programs_dir().join(rel)
}

#[test] fn while_unsafe_fast()  { assert_compile_and_run(&p("programs/while/while.vale"), "unsafe-fast", 42); }
#[test] fn while_naive_rc()     { assert_compile_and_run(&p("programs/while/while.vale"), "naive-rc", 42); }
#[test] fn while_resilient_v3() { assert_compile_and_run(&p("programs/while/while.vale"), "resilient-v3", 42); }
#[test] fn while_safe()         { assert_compile_and_run(&p("programs/while/while.vale"), "safe", 42); }
#[test] fn while_safe_fastest() { assert_compile_and_run(&p("programs/while/while.vale"), "safe-fastest", 42); }
