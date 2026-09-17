// FFI bridge to the C++ Backend (statically linked via build.rs).

pub mod metal_cache;
pub mod metal_lowerer;

use std::ffi::CString;
use std::os::raw::{c_char, c_int};

extern "C" {
    // AST-driven entry. Caller has populated MetalCache+Program via the
    // metal_cache_ffi.h builders (driven by MetalLowerer against a ProgramH).
    // argv carries valeOptSet-style flags (--output_dir, --region_override,
    // etc.); inputs come from the Program parameter, not from .vast files.
    fn backend_compile_program(
        cache: *mut metal_cache::MetalCacheHandleRaw,
        program: *mut std::ffi::c_void,
        argc: c_int, argv: *mut *mut c_char,
    ) -> i32;
}

/// Safe wrapper around `backend_compile_program`. argv carries CLI-style
/// options (e.g. `["backend", "--output_dir", "<out>"]`); inputs come from
/// the Program parameter, not files.
pub fn backend_compile_program_safe(
    cache: &metal_cache::MetalCache,
    program: &metal_cache::Program<'_>,
    argv: &[&str],
) -> i32 {
    let mut c_strings: Vec<CString> = argv
        .iter()
        .map(|s| CString::new(*s).expect("argv element contained NUL"))
        .collect();
    let mut c_argv: Vec<*mut c_char> =
        c_strings.iter_mut().map(|c| c.as_ptr() as *mut c_char).collect();
    unsafe {
        backend_compile_program(
            cache.raw(),
            program.raw(),
            c_argv.len() as c_int,
            c_argv.as_mut_ptr(),
        )
    }
}

