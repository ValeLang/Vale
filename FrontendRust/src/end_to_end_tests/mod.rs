//! End-to-end harness: drives a real Vale program (file, directory, or inline
//! source) through `pass_manager::build` (the production entry `valec` uses),
//! invokes clang on the produced `.o` + abi files, and provides a
//! `CompiledProgram` whose binary can be exec'd and asserted on.
//!
//! This is the unified home for in-process backend tests, replacing the
//! out-of-process `TesterRust` driver and folding in inline-source and
//! pass_manager-driven tests that previously lived in
//! `backend_ffi/metal_lowerer.rs` and `pass_manager/end_to_end_test.rs`.

use std::path::{Path, PathBuf};

pub mod tests;

pub struct CompiledProgram {
    exe: PathBuf,
    pub cwd: PathBuf,
    _work: tempfile::TempDir,
    _extra_keepalive: Vec<tempfile::TempDir>,
}

pub struct ExecResult {
    pub exit_code: i32,
    pub stdout: String,
    pub stderr: String,
}

/// Compile a Vale program to a native binary via `pass_manager::build`.
///
/// - `primary_vale` may be a single `.vale` file or a directory; the directory
///   case is registered as a `vtest=<dir>` project (whose `.vale` files at the
///   top level get walked).
/// - `extra_vale` adds further `vtest=...` inputs.
/// - `extra_c` lists additional C sources to link with clang (e.g. extern
///   tests' `native/test.c`).
/// - `region` is `"unsafe-fast"` or `"naive-rc"`; forwarded to the backend.
/// - `extra_backend_flags` is appended verbatim to the backend argv (e.g.
///   `&["--enable_replaying", "true"]` for replay tests).
pub fn compile_program(
    primary_vale: &Path,
    extra_vale: &[&Path],
    extra_c: &[&Path],
    region: &str,
    extra_backend_flags: &[&str],
) -> CompiledProgram {
    compile_inputs(
        std::iter::once(primary_vale)
            .chain(extra_vale.iter().copied())
            .map(|p| p.to_path_buf())
            .collect(),
        extra_c,
        region,
        extra_backend_flags,
        Vec::new(),
    )
}

/// Compile a single inline Vale program to a native binary.
///
/// Writes `code` to a `test.vale` in a temp source directory, registers it
/// as the `vtest=` project, and dispatches through the same harness path
/// as `compile_program`. The temp source directory is kept alive for the
/// life of the returned `CompiledProgram`.
pub fn compile_inline(
    code: &str,
    region: &str,
    extra_backend_flags: &[&str],
) -> CompiledProgram {
    let src_dir = tempfile::tempdir().unwrap();
    let src_file = src_dir.path().join("test.vale");
    std::fs::write(&src_file, code).unwrap();
    compile_inputs(
        vec![src_dir.path().to_path_buf()],
        &[],
        region,
        extra_backend_flags,
        vec![src_dir],
    )
}

fn compile_inputs(
    vale_inputs: Vec<PathBuf>,
    extra_c: &[&Path],
    region: &str,
    extra_backend_flags: &[&str],
    keepalive: Vec<tempfile::TempDir>,
) -> CompiledProgram {
    let parse_bump = bumpalo::Bump::new();
    let parse_arena = crate::parse_arena::ParseArena::new(&parse_bump);
    let keywords = crate::keywords::Keywords::new_for_parse(&parse_arena);

    let work = tempfile::tempdir().unwrap();
    let out_dir = work.path().join("out");
    std::fs::create_dir_all(&out_dir).unwrap();

    let mut cli_args: Vec<String> = vec![
        "build".to_string(),
        "--output_dir".to_string(),
        out_dir.display().to_string(),
        "--output_vast".to_string(),
        "true".to_string(),
        "--include_builtins".to_string(),
        "true".to_string(),
        "--sanity_check".to_string(),
        "false".to_string(),
    ];
    for input in &vale_inputs {
        cli_args.push(format!("vtest={}", input.display()));
    }
    let opts = crate::pass_manager::pass_manager::parse_opts(
        &parse_arena,
        crate::pass_manager::pass_manager::Options {
            inputs: vec![],
            output_dir_path: None,
            benchmark: false,
            output_vast: true,
            include_builtins: true,
            mode: None,
            sanity_check: false,
            use_optimized_solver: true,
            use_overload_index: true,
            verbose_errors: false,
            debug_output: false,
        },
        cli_args,
    );

    let mut backend_argv: Vec<String> = vec![
        "backend".to_string(),
        "--output_dir".to_string(),
        out_dir.display().to_string(),
        "--region_override".to_string(),
        region.to_string(),
    ];
    for f in extra_backend_flags {
        backend_argv.push((*f).to_string());
    }
    let backend_argv_refs: Vec<&str> = backend_argv.iter().map(|s| s.as_str()).collect();

    let builtins_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .parent()
        .unwrap()
        .join("Backend/builtins");
    let test_builtins = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .parent()
        .unwrap()
        .join("Backend/test_builtins/testbuiltins.c");

    let mut extra_inputs: Vec<PathBuf> = vec![test_builtins];
    for c in extra_c {
        extra_inputs.push(c.to_path_buf());
    }

    let clang_cfg = crate::pass_manager::pass_manager::ClangConfig {
        builtins_dir,
        extra_inputs,
        clang_path: None,
        libc_path: None,
        executable_name: "a.out".to_string(),
        asan: false,
        debug_symbols: false,
        pic: false,
        pie: false,
        windows: false,
    };

    let bp = crate::pass_manager::pass_manager::build(
        &parse_arena,
        &keywords,
        &opts,
        &backend_argv_refs,
        &clang_cfg,
    )
    .unwrap_or_else(|e| panic!("pass_manager::build failed:\n{}", e));
    assert_eq!(bp.rc, 0, "backend returned {} (region={})", bp.rc, region);

    CompiledProgram {
        exe: bp.exe_path,
        cwd: out_dir,
        _work: work,
        _extra_keepalive: keepalive,
    }
}

impl CompiledProgram {
    pub fn run(&self, args: &[&str]) -> ExecResult {
        let out = std::process::Command::new(&self.exe)
            .current_dir(&self.cwd)
            .args(args)
            .output()
            .expect("exec failed");
        ExecResult {
            exit_code: out.status.code().unwrap_or(-1),
            stdout: String::from_utf8_lossy(&out.stdout).into_owned(),
            stderr: String::from_utf8_lossy(&out.stderr).into_owned(),
        }
    }
}

pub fn programs_dir() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("src/tests")
}

pub fn assert_compile_and_run(vale_path: &Path, region: &str, expected: i32) {
    let cp = compile_program(vale_path, &[], &[], region, &[]);
    let r = cp.run(&[]);
    assert_eq!(
        r.exit_code, expected,
        "stdout={:?} stderr={:?}",
        r.stdout, r.stderr
    );
}

pub fn assert_compile_and_run_with_c(
    vale_dir: &Path,
    extra_c: &[&Path],
    region: &str,
    expected: i32,
) {
    let cp = compile_program(vale_dir, &[], extra_c, region, &[]);
    let r = cp.run(&[]);
    assert_eq!(
        r.exit_code, expected,
        "stdout={:?} stderr={:?}",
        r.stdout, r.stderr
    );
}

pub fn assert_inline_compile_and_run(code: &str, region: &str, expected: i32) {
    let cp = compile_inline(code, region, &[]);
    let r = cp.run(&[]);
    assert_eq!(
        r.exit_code, expected,
        "stdout={:?} stderr={:?}",
        r.stdout, r.stderr
    );
}

pub fn assert_replay_test(
    vale_dir: &Path,
    extra_c: &[&Path],
    region: &str,
    first_expected: i32,
    repeated_expected: i32,
) {
    let cp = compile_program(
        vale_dir,
        &[],
        extra_c,
        region,
        &["--enable_replaying", "true"],
    );
    let r0 = cp.run(&[]);
    assert_eq!(
        r0.exit_code, first_expected,
        "run 0 (no record/replay): stdout={:?} stderr={:?}",
        r0.stdout, r0.stderr
    );
    let r1 = cp.run(&["--vale_record", "recording.bin"]);
    assert_eq!(
        r1.exit_code, repeated_expected,
        "run 1 (record): stdout={:?} stderr={:?}",
        r1.stdout, r1.stderr
    );
    let r2 = cp.run(&["--vale_replay", "recording.bin"]);
    assert_eq!(
        r2.exit_code, repeated_expected,
        "run 2 (replay): stdout={:?} stderr={:?}",
        r2.stdout, r2.stderr
    );
}

#[cfg(test)]
mod smoke {
    use super::*;

    #[test]
    fn smoke_structimm_unsafe_fast() {
        let p = programs_dir().join("programs/structs/structimm.vale");
        assert_compile_and_run(&p, "unsafe-fast", 5);
    }
}
