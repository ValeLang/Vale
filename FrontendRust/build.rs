// Builds the C++ backend as a static library and emits link directives so the
// FrontendRust crate can call into it via FFI.
//
// Requires LLVM 16. The build prefers `$LLVM_DIR` / `$LLVM_CONFIG`; falls back
// to the Homebrew arm64 prefix on macOS.

use std::env;
use std::path::PathBuf;
use std::process::Command;

fn main() {
    let backend_dir = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap())
        .parent()
        .expect("FrontendRust must live next to Backend/")
        .join("Backend");

    let llvm_config = locate_llvm_config();
    let llvm_dir = run(&llvm_config, &["--cmakedir"]);

    // Build only the backend_lib static library target. Skips the legacy
    // `backend` executable (which keeps building via plain cmake for now).
    let dst = cmake::Config::new(&backend_dir)
        .define("LLVM_DIR", &llvm_dir)
        .define("CMAKE_BUILD_TYPE", "Debug")
        .build_target("backend_lib")
        .build();

    // cmake-rs places build artifacts under `<dst>/build/`.
    let build_dir = dst.join("build");
    println!("cargo:rustc-link-search=native={}", build_dir.display());
    println!("cargo:rustc-link-lib=static=backend_lib");

    // LLVM static libs.
    let llvm_libdir = run(&llvm_config, &["--libdir"]);
    println!("cargo:rustc-link-search=native={}", llvm_libdir);

    let llvm_libs = run(&llvm_config, &[
        "--libs", "--link-static",
        "core", "support", "irreader", "passes",
        "aarch64asmparser", "aarch64codegen", "aarch64desc", "aarch64disassembler", "aarch64info",
        "x86asmparser", "x86codegen", "x86desc", "x86disassembler", "x86info",
        "webassemblyasmparser", "webassemblycodegen", "webassemblydesc", "webassemblydisassembler", "webassemblyinfo",
    ]);
    for lib in llvm_libs.split_whitespace() {
        if let Some(name) = lib.strip_prefix("-l") {
            println!("cargo:rustc-link-lib=static={}", name);
        }
    }

    // System libs LLVM itself needs.
    let system_libs = run(&llvm_config, &["--system-libs", "--link-static"]);
    for lib in system_libs.split_whitespace() {
        if let Some(name) = lib.strip_prefix("-l") {
            println!("cargo:rustc-link-lib=dylib={}", name);
        }
    }

    // C++ stdlib. Rust links libc++ on macOS by default.
    if cfg!(target_os = "macos") {
        // Homebrew installs libs like zstd outside the default search path.
        if std::path::Path::new("/opt/homebrew/lib").exists() {
            println!("cargo:rustc-link-search=native=/opt/homebrew/lib");
        }
        println!("cargo:rustc-link-lib=dylib=c++");
        println!("cargo:rustc-link-lib=framework=CoreFoundation");
    } else {
        println!("cargo:rustc-link-lib=dylib=stdc++");
    }

    println!("cargo:rerun-if-changed={}", backend_dir.join("src/ffi.cpp").display());
    println!("cargo:rerun-if-changed={}", backend_dir.join("src/metal/metal_cache_ffi.cpp").display());
    println!("cargo:rerun-if-changed={}", backend_dir.join("src/metal/metal_cache_ffi.h").display());
    println!("cargo:rerun-if-changed={}", backend_dir.join("CMakeLists.txt").display());
    println!("cargo:rerun-if-env-changed=LLVM_CONFIG");
    println!("cargo:rerun-if-env-changed=LLVM_DIR");
}

fn locate_llvm_config() -> PathBuf {
    if let Ok(path) = env::var("LLVM_CONFIG") {
        return PathBuf::from(path);
    }
    let homebrew = PathBuf::from("/opt/homebrew/opt/llvm@16/bin/llvm-config");
    if homebrew.exists() {
        return homebrew;
    }
    PathBuf::from("llvm-config")
}

fn run(prog: &PathBuf, args: &[&str]) -> String {
    let out = Command::new(prog)
        .args(args)
        .output()
        .unwrap_or_else(|e| panic!("failed to exec {} {:?}: {}", prog.display(), args, e));
    if !out.status.success() {
        panic!(
            "{} {:?} failed: {}",
            prog.display(),
            args,
            String::from_utf8_lossy(&out.stderr)
        );
    }
    String::from_utf8(out.stdout).unwrap().trim().to_string()
}
