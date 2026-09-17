use crate::end_to_end_tests::{assert_compile_and_run_with_c, programs_dir};

/// A reached package's native/*.c is auto-linked. The Vale
/// program declares an extern, calls it from main, and the impl lives in
/// `native/test.c`, but we pass `extra_c=&[]` so the only way the impl
/// reaches clang is via the Frontend-driven walker.
#[test]
fn native_walker_reached_package_included() {
    let dir = programs_dir().join("programs/native_walker/walks_reached");
    assert_compile_and_run_with_c(&dir, &[], "unsafe-fast", 7);
}

/// A non-reached package's `native/*.c` is skipped. The
/// program at `<dir>/test.vale` (package `vtest`) calls an extern in
/// `<dir>/native/test.c`, but does NOT import or reach `vtest.unused`.
/// The `unused/native/broken.c` contains a `#error` that would fail clang
/// immediately if compiled. Build must succeed, proving the walker
/// skipped the unreached package.
#[test]
fn native_walker_unreached_package_excluded() {
    let dir = programs_dir().join("programs/native_walker/skips_unreached");
    assert_compile_and_run_with_c(&dir, &[], "unsafe-fast", 5);
}
