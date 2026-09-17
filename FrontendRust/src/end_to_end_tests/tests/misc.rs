#![allow(non_snake_case)]

use crate::end_to_end_tests::{assert_compile_and_run, programs_dir};

fn p(rel: &str) -> std::path::PathBuf {
    programs_dir().join(rel)
}

#[test] fn mutswaplocals_unsafe_fast()              { assert_compile_and_run(&p("programs/mutswaplocals.vale"), "unsafe-fast", 42); }
#[test] fn mutswaplocals_naive_rc()                 { assert_compile_and_run(&p("programs/mutswaplocals.vale"), "naive-rc", 42); }
#[test] fn mutswaplocals_resilient_v3()             { assert_compile_and_run(&p("programs/mutswaplocals.vale"), "resilient-v3", 42); }
#[test] fn mutswaplocals_safe()                     { assert_compile_and_run(&p("programs/mutswaplocals.vale"), "safe", 42); }
#[test] fn mutswaplocals_safe_fastest()             { assert_compile_and_run(&p("programs/mutswaplocals.vale"), "safe-fastest", 42); }
#[test] fn restackify_unsafe_fast()                 { assert_compile_and_run(&p("programs/restackify.vale"), "unsafe-fast", 42); }
#[test] fn restackify_naive_rc()                    { assert_compile_and_run(&p("programs/restackify.vale"), "naive-rc", 42); }
#[test] fn restackify_resilient_v3()                { assert_compile_and_run(&p("programs/restackify.vale"), "resilient-v3", 42); }
#[test] fn restackify_safe()                        { assert_compile_and_run(&p("programs/restackify.vale"), "safe", 42); }
#[test] fn restackify_safe_fastest()                { assert_compile_and_run(&p("programs/restackify.vale"), "safe-fastest", 42); }
#[test] fn destructure_restackify_unsafe_fast()     { assert_compile_and_run(&p("programs/destructure_restackify.vale"), "unsafe-fast", 42); }
#[test] fn destructure_restackify_naive_rc()        { assert_compile_and_run(&p("programs/destructure_restackify.vale"), "naive-rc", 42); }
#[test] fn destructure_restackify_resilient_v3()    { assert_compile_and_run(&p("programs/destructure_restackify.vale"), "resilient-v3", 42); }
#[test] fn destructure_restackify_safe()            { assert_compile_and_run(&p("programs/destructure_restackify.vale"), "safe", 42); }
#[test] fn destructure_restackify_safe_fastest()    { assert_compile_and_run(&p("programs/destructure_restackify.vale"), "safe-fastest", 42); }
#[test] fn loop_restackify_unsafe_fast()            { assert_compile_and_run(&p("programs/loop_restackify.vale"), "unsafe-fast", 42); }
#[test] fn loop_restackify_naive_rc()               { assert_compile_and_run(&p("programs/loop_restackify.vale"), "naive-rc", 42); }
#[test] fn loop_restackify_resilient_v3()           { assert_compile_and_run(&p("programs/loop_restackify.vale"), "resilient-v3", 42); }
#[test] fn loop_restackify_safe()                   { assert_compile_and_run(&p("programs/loop_restackify.vale"), "safe", 42); }
#[test] fn loop_restackify_safe_fastest()           { assert_compile_and_run(&p("programs/loop_restackify.vale"), "safe-fastest", 42); }
#[test] fn mutlocal_unsafe_fast()                   { assert_compile_and_run(&p("programs/mutlocal.vale"), "unsafe-fast", 42); }
#[test] fn mutlocal_naive_rc()                      { assert_compile_and_run(&p("programs/mutlocal.vale"), "naive-rc", 42); }
#[test] fn mutlocal_resilient_v3()                  { assert_compile_and_run(&p("programs/mutlocal.vale"), "resilient-v3", 42); }
#[test] fn mutlocal_safe()                          { assert_compile_and_run(&p("programs/mutlocal.vale"), "safe", 42); }
#[test] fn mutlocal_safe_fastest()                  { assert_compile_and_run(&p("programs/mutlocal.vale"), "safe-fastest", 42); }
#[test] fn constraintRef_unsafe_fast()              { assert_compile_and_run(&p("programs/constraintRef.vale"), "unsafe-fast", 8); }
#[test] fn constraintRef_naive_rc()                 { assert_compile_and_run(&p("programs/constraintRef.vale"), "naive-rc", 8); }
#[test] fn constraintRef_resilient_v3()             { assert_compile_and_run(&p("programs/constraintRef.vale"), "resilient-v3", 8); }
#[test] fn constraintRef_safe()                     { assert_compile_and_run(&p("programs/constraintRef.vale"), "safe", 8); }
#[test] fn constraintRef_safe_fastest()             { assert_compile_and_run(&p("programs/constraintRef.vale"), "safe-fastest", 8); }
#[test] fn unstackifyret_unsafe_fast()              { assert_compile_and_run(&p("programs/unstackifyret.vale"), "unsafe-fast", 42); }
#[test] fn unstackifyret_naive_rc()                 { assert_compile_and_run(&p("programs/unstackifyret.vale"), "naive-rc", 42); }
#[test] fn unstackifyret_resilient_v3()             { assert_compile_and_run(&p("programs/unstackifyret.vale"), "resilient-v3", 42); }
#[test] fn unstackifyret_safe()                     { assert_compile_and_run(&p("programs/unstackifyret.vale"), "safe", 42); }
#[test] fn unstackifyret_safe_fastest()             { assert_compile_and_run(&p("programs/unstackifyret.vale"), "safe-fastest", 42); }
#[test] fn unreachablemoot_unsafe_fast()            { assert_compile_and_run(&p("programs/unreachablemoot.vale"), "unsafe-fast", 42); }
#[test] fn unreachablemoot_naive_rc()               { assert_compile_and_run(&p("programs/unreachablemoot.vale"), "naive-rc", 42); }
#[test] fn unreachablemoot_resilient_v3()           { assert_compile_and_run(&p("programs/unreachablemoot.vale"), "resilient-v3", 42); }
#[test] fn unreachablemoot_safe()                   { assert_compile_and_run(&p("programs/unreachablemoot.vale"), "safe", 42); }
#[test] fn unreachablemoot_safe_fastest()           { assert_compile_and_run(&p("programs/unreachablemoot.vale"), "safe-fastest", 42); }
#[test] fn panic_unsafe_fast()                      { assert_compile_and_run(&p("programs/panic.vale"), "unsafe-fast", 1); }
#[test] fn panic_naive_rc()                         { assert_compile_and_run(&p("programs/panic.vale"), "naive-rc", 1); }
#[test] fn panic_resilient_v3()                     { assert_compile_and_run(&p("programs/panic.vale"), "resilient-v3", 1); }
#[test] fn panic_safe()                             { assert_compile_and_run(&p("programs/panic.vale"), "safe", 1); }
#[test] fn panic_safe_fastest()                     { assert_compile_and_run(&p("programs/panic.vale"), "safe-fastest", 1); }
#[test] fn panicnot_unsafe_fast()                   { assert_compile_and_run(&p("programs/panicnot.vale"), "unsafe-fast", 42); }
#[test] fn panicnot_naive_rc()                      { assert_compile_and_run(&p("programs/panicnot.vale"), "naive-rc", 42); }
#[test] fn panicnot_resilient_v3()                  { assert_compile_and_run(&p("programs/panicnot.vale"), "resilient-v3", 42); }
#[test] fn panicnot_safe()                          { assert_compile_and_run(&p("programs/panicnot.vale"), "safe", 42); }
#[test] fn panicnot_safe_fastest()                  { assert_compile_and_run(&p("programs/panicnot.vale"), "safe-fastest", 42); }
#[test] fn nestedblocks_unsafe_fast()               { assert_compile_and_run(&p("programs/nestedblocks.vale"), "unsafe-fast", 42); }
#[test] fn nestedblocks_naive_rc()                  { assert_compile_and_run(&p("programs/nestedblocks.vale"), "naive-rc", 42); }
#[test] fn nestedblocks_resilient_v3()              { assert_compile_and_run(&p("programs/nestedblocks.vale"), "resilient-v3", 42); }
#[test] fn nestedblocks_safe()                      { assert_compile_and_run(&p("programs/nestedblocks.vale"), "safe", 42); }
#[test] fn nestedblocks_safe_fastest()              { assert_compile_and_run(&p("programs/nestedblocks.vale"), "safe-fastest", 42); }
