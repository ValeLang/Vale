use crate::end_to_end_tests::{assert_compile_and_run, programs_dir};

fn p(rel: &str) -> std::path::PathBuf {
    programs_dir().join(rel)
}

#[test] fn lambda_unsafe_fast()       { assert_compile_and_run(&p("programs/lambdas/lambda.vale"), "unsafe-fast", 42); }
#[test] fn lambda_naive_rc()          { assert_compile_and_run(&p("programs/lambdas/lambda.vale"), "naive-rc", 42); }
#[test] fn lambda_resilient_v3()      { assert_compile_and_run(&p("programs/lambdas/lambda.vale"), "resilient-v3", 42); }
#[test] fn lambda_safe()              { assert_compile_and_run(&p("programs/lambdas/lambda.vale"), "safe", 42); }
#[test] fn lambda_safe_fastest()      { assert_compile_and_run(&p("programs/lambdas/lambda.vale"), "safe-fastest", 42); }
#[test] fn lambdamut_unsafe_fast()    { assert_compile_and_run(&p("programs/lambdas/lambdamut.vale"), "unsafe-fast", 42); }
#[test] fn lambdamut_naive_rc()       { assert_compile_and_run(&p("programs/lambdas/lambdamut.vale"), "naive-rc", 42); }
#[test] fn lambdamut_resilient_v3()   { assert_compile_and_run(&p("programs/lambdas/lambdamut.vale"), "resilient-v3", 42); }
#[test] fn lambdamut_safe()           { assert_compile_and_run(&p("programs/lambdas/lambdamut.vale"), "safe", 42); }
#[test] fn lambdamut_safe_fastest()   { assert_compile_and_run(&p("programs/lambdas/lambdamut.vale"), "safe-fastest", 42); }
