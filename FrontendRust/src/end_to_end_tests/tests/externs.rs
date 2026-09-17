use crate::end_to_end_tests::{assert_compile_and_run_with_c, programs_dir};

fn run(dir_rel: &str, expected: i32) {
    let dir = programs_dir().join(dir_rel);
    // `native/test.c` is auto-discovered by the Frontend-driven walker in
    // pass_manager::build; no need to pass it via extra_c.
    assert_compile_and_run_with_c(&dir, &[], "unsafe-fast", expected);
}

#[test] fn interfacemutreturnexport_unsafe_fast() { run("programs/externs/interfacemutreturnexport", 42); }
#[test] fn interfacemutparamexport_unsafe_fast()  { run("programs/externs/interfacemutparamexport", 42); }
#[test] fn structmutreturnexport_unsafe_fast()    { run("programs/externs/structmutreturnexport", 42); }
#[test] fn structmutparamexport_unsafe_fast()     { run("programs/externs/structmutparamexport", 42); }
#[test] fn structmutparamdeepexport_unsafe_fast() { run("programs/externs/structmutparamdeepexport", 42); }
#[test] fn rsamutparamexport_unsafe_fast()        { run("programs/externs/rsamutparamexport", 10); }
#[test] fn rsamutreturnexport_unsafe_fast()       { run("programs/externs/rsamutreturnexport", 42); }
#[test] fn ssamutparamexport_unsafe_fast()        { run("programs/externs/ssamutparamexport", 10); }
#[test] fn ssamutreturnexport_unsafe_fast()       { run("programs/externs/ssamutreturnexport", 42); }
