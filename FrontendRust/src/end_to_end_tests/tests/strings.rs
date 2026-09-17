use crate::end_to_end_tests::{assert_compile_and_run, programs_dir};

fn p(rel: &str) -> std::path::PathBuf {
    programs_dir().join(rel)
}

#[test] fn stradd_unsafe_fast()     { assert_compile_and_run(&p("programs/strings/stradd.vale"), "unsafe-fast", 42); }
#[test] fn stradd_naive_rc()        { assert_compile_and_run(&p("programs/strings/stradd.vale"), "naive-rc", 42); }
#[test] fn stradd_resilient_v3()    { assert_compile_and_run(&p("programs/strings/stradd.vale"), "resilient-v3", 42); }
#[test] fn stradd_safe()            { assert_compile_and_run(&p("programs/strings/stradd.vale"), "safe", 42); }
#[test] fn stradd_safe_fastest()    { assert_compile_and_run(&p("programs/strings/stradd.vale"), "safe-fastest", 42); }
#[test] fn strneq_unsafe_fast()     { assert_compile_and_run(&p("programs/strings/strneq.vale"), "unsafe-fast", 42); }
#[test] fn strneq_naive_rc()        { assert_compile_and_run(&p("programs/strings/strneq.vale"), "naive-rc", 42); }
#[test] fn strneq_resilient_v3()    { assert_compile_and_run(&p("programs/strings/strneq.vale"), "resilient-v3", 42); }
#[test] fn strneq_safe()            { assert_compile_and_run(&p("programs/strings/strneq.vale"), "safe", 42); }
#[test] fn strneq_safe_fastest()    { assert_compile_and_run(&p("programs/strings/strneq.vale"), "safe-fastest", 42); }
#[test] fn strprint_unsafe_fast()   { assert_compile_and_run(&p("programs/strings/strprint.vale"), "unsafe-fast", 42); }
#[test] fn strprint_naive_rc()      { assert_compile_and_run(&p("programs/strings/strprint.vale"), "naive-rc", 42); }
#[test] fn strprint_resilient_v3()  { assert_compile_and_run(&p("programs/strings/strprint.vale"), "resilient-v3", 42); }
#[test] fn strprint_safe()          { assert_compile_and_run(&p("programs/strings/strprint.vale"), "safe", 42); }
#[test] fn strprint_safe_fastest()  { assert_compile_and_run(&p("programs/strings/strprint.vale"), "safe-fastest", 42); }
#[test] fn inttostr_unsafe_fast()   { assert_compile_and_run(&p("programs/strings/inttostr.vale"), "unsafe-fast", 4); }
#[test] fn inttostr_naive_rc()      { assert_compile_and_run(&p("programs/strings/inttostr.vale"), "naive-rc", 4); }
#[test] fn inttostr_resilient_v3()  { assert_compile_and_run(&p("programs/strings/inttostr.vale"), "resilient-v3", 4); }
#[test] fn inttostr_safe()          { assert_compile_and_run(&p("programs/strings/inttostr.vale"), "safe", 4); }
#[test] fn inttostr_safe_fastest()  { assert_compile_and_run(&p("programs/strings/inttostr.vale"), "safe-fastest", 4); }
#[test] fn i64tostr_unsafe_fast()   { assert_compile_and_run(&p("programs/strings/i64tostr.vale"), "unsafe-fast", 4); }
#[test] fn i64tostr_naive_rc()      { assert_compile_and_run(&p("programs/strings/i64tostr.vale"), "naive-rc", 4); }
#[test] fn i64tostr_resilient_v3()  { assert_compile_and_run(&p("programs/strings/i64tostr.vale"), "resilient-v3", 4); }
#[test] fn i64tostr_safe()          { assert_compile_and_run(&p("programs/strings/i64tostr.vale"), "safe", 4); }
#[test] fn i64tostr_safe_fastest()  { assert_compile_and_run(&p("programs/strings/i64tostr.vale"), "safe-fastest", 4); }
