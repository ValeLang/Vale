use crate::end_to_end_tests::{assert_compile_and_run, programs_dir};

fn p(rel: &str) -> std::path::PathBuf {
    programs_dir().join(rel)
}

#[test] fn interfaceimm_unsafe_fast()    { assert_compile_and_run(&p("programs/virtuals/interfaceimm.vale"), "unsafe-fast", 42); }
#[test] fn interfaceimm_naive_rc()       { assert_compile_and_run(&p("programs/virtuals/interfaceimm.vale"), "naive-rc", 42); }
#[test] fn interfaceimm_resilient_v3()   { assert_compile_and_run(&p("programs/virtuals/interfaceimm.vale"), "resilient-v3", 42); }
#[test] fn interfaceimm_safe()           { assert_compile_and_run(&p("programs/virtuals/interfaceimm.vale"), "safe", 42); }
#[test] fn interfaceimm_safe_fastest()   { assert_compile_and_run(&p("programs/virtuals/interfaceimm.vale"), "safe-fastest", 42); }
#[test] fn interfacemut_unsafe_fast()    { assert_compile_and_run(&p("programs/virtuals/interfacemut.vale"), "unsafe-fast", 42); }
#[test] fn interfacemut_naive_rc()       { assert_compile_and_run(&p("programs/virtuals/interfacemut.vale"), "naive-rc", 42); }
#[test] fn interfacemut_resilient_v3()   { assert_compile_and_run(&p("programs/virtuals/interfacemut.vale"), "resilient-v3", 42); }
#[test] fn interfacemut_safe()           { assert_compile_and_run(&p("programs/virtuals/interfacemut.vale"), "safe", 42); }
#[test] fn interfacemut_safe_fastest()   { assert_compile_and_run(&p("programs/virtuals/interfacemut.vale"), "safe-fastest", 42); }
