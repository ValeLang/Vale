use crate::end_to_end_tests::{assert_compile_and_run, programs_dir};

fn p(rel: &str) -> std::path::PathBuf {
    programs_dir().join(rel)
}

#[test] fn structimm_unsafe_fast()              { assert_compile_and_run(&p("programs/structs/structimm.vale"), "unsafe-fast", 5); }
#[test] fn structimm_naive_rc()                 { assert_compile_and_run(&p("programs/structs/structimm.vale"), "naive-rc", 5); }
#[test] fn structimm_resilient_v3()             { assert_compile_and_run(&p("programs/structs/structimm.vale"), "resilient-v3", 5); }
#[test] fn structimm_safe()                     { assert_compile_and_run(&p("programs/structs/structimm.vale"), "safe", 5); }
#[test] fn structimm_safe_fastest()             { assert_compile_and_run(&p("programs/structs/structimm.vale"), "safe-fastest", 5); }
#[test] fn memberrefcount_unsafe_fast()         { assert_compile_and_run(&p("programs/structs/memberrefcount.vale"), "unsafe-fast", 5); }
#[test] fn memberrefcount_naive_rc()            { assert_compile_and_run(&p("programs/structs/memberrefcount.vale"), "naive-rc", 5); }
#[test] fn memberrefcount_resilient_v3()        { assert_compile_and_run(&p("programs/structs/memberrefcount.vale"), "resilient-v3", 5); }
#[test] fn memberrefcount_safe()                { assert_compile_and_run(&p("programs/structs/memberrefcount.vale"), "safe", 5); }
#[test] fn memberrefcount_safe_fastest()        { assert_compile_and_run(&p("programs/structs/memberrefcount.vale"), "safe-fastest", 5); }
#[test] fn bigstructimm_unsafe_fast()           { assert_compile_and_run(&p("programs/structs/bigstructimm.vale"), "unsafe-fast", 42); }
#[test] fn bigstructimm_naive_rc()              { assert_compile_and_run(&p("programs/structs/bigstructimm.vale"), "naive-rc", 42); }
#[test] fn bigstructimm_resilient_v3()          { assert_compile_and_run(&p("programs/structs/bigstructimm.vale"), "resilient-v3", 42); }
#[test] fn bigstructimm_safe()                  { assert_compile_and_run(&p("programs/structs/bigstructimm.vale"), "safe", 42); }
#[test] fn bigstructimm_safe_fastest()          { assert_compile_and_run(&p("programs/structs/bigstructimm.vale"), "safe-fastest", 42); }
#[test] fn structmut_unsafe_fast()              { assert_compile_and_run(&p("programs/structs/structmut.vale"), "unsafe-fast", 8); }
#[test] fn structmut_naive_rc()                 { assert_compile_and_run(&p("programs/structs/structmut.vale"), "naive-rc", 8); }
#[test] fn structmut_resilient_v3()             { assert_compile_and_run(&p("programs/structs/structmut.vale"), "resilient-v3", 8); }
#[test] fn structmut_safe()                     { assert_compile_and_run(&p("programs/structs/structmut.vale"), "safe", 8); }
#[test] fn structmut_safe_fastest()             { assert_compile_and_run(&p("programs/structs/structmut.vale"), "safe-fastest", 8); }
#[test] fn structmutstore_unsafe_fast()         { assert_compile_and_run(&p("programs/structs/structmutstore.vale"), "unsafe-fast", 42); }
#[test] fn structmutstore_naive_rc()            { assert_compile_and_run(&p("programs/structs/structmutstore.vale"), "naive-rc", 42); }
#[test] fn structmutstore_resilient_v3()        { assert_compile_and_run(&p("programs/structs/structmutstore.vale"), "resilient-v3", 42); }
#[test] fn structmutstore_safe()                { assert_compile_and_run(&p("programs/structs/structmutstore.vale"), "safe", 42); }
#[test] fn structmutstore_safe_fastest()        { assert_compile_and_run(&p("programs/structs/structmutstore.vale"), "safe-fastest", 42); }
#[test] fn structmutstoreinner_unsafe_fast()    { assert_compile_and_run(&p("programs/structs/structmutstoreinner.vale"), "unsafe-fast", 42); }
#[test] fn structmutstoreinner_naive_rc()       { assert_compile_and_run(&p("programs/structs/structmutstoreinner.vale"), "naive-rc", 42); }
#[test] fn structmutstoreinner_resilient_v3()   { assert_compile_and_run(&p("programs/structs/structmutstoreinner.vale"), "resilient-v3", 42); }
#[test] fn structmutstoreinner_safe()           { assert_compile_and_run(&p("programs/structs/structmutstoreinner.vale"), "safe", 42); }
#[test] fn structmutstoreinner_safe_fastest()   { assert_compile_and_run(&p("programs/structs/structmutstoreinner.vale"), "safe-fastest", 42); }
