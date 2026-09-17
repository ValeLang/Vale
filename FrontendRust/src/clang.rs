// Clang invocation for linking
// Mirrors Coordinator/src/clang.vale

use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

/// Invoke clang to link the final executable
/// Mirrors invoke_clang in clang.vale lines 3-103
pub fn invoke_clang(
    windows: bool,
    maybe_clang_path_override: Option<&str>,
    maybe_libc_path_override: Option<&str>,
    clang_inputs: &[PathBuf],
    exe_name: &str,
    asan: bool,
    debug_symbols: bool,
    output_dir: &Path,
    pic: bool,
    pie: bool,
) -> Result<std::process::Child, String> {
    // Mirrors clang.vale lines 15-24
    let program = if let Some(override_path) = maybe_clang_path_override {
        override_path.to_string()
    } else if windows {
        "cl.exe".to_string()
    } else {
        "clang".to_string()
    };

    // Mirrors clang.vale line 26
    let exe_file = output_dir.join(exe_name);

    // Mirrors clang.vale line 28
    let mut args = Vec::new();

    // Mirrors clang.vale line 30
    args.push(format!("-I{}", output_dir.join("include").display()));

    // Mirrors clang.vale lines 32-40
    if let Some(libc_path_str) = maybe_libc_path_override {
        let libc_path = Path::new(libc_path_str);
        if !libc_path.exists() {
            return Err(format!("libc override dir doesn't exist: {}", libc_path.display()));
        }
        args.push(format!("-I{}", libc_path.join("include").display()));
        args.push(format!("-L{}", libc_path.join("lib").display()));
    }

    // Mirrors clang.vale lines 42-66
    if windows {
        args.push("/ENTRY:\"main\"".to_string());
        args.push("/SUBSYSTEM:CONSOLE".to_string());
        args.push(format!("/Fe:{}", exe_file.display()));
        
        // Mirrors clang.vale lines 48-59
        // Use absolute path for /Fo
        let output_dir_resolved = output_dir.canonicalize()
            .unwrap_or_else(|_| output_dir.to_path_buf());
        args.push(format!("/Fo:{}\\\\", output_dir_resolved.display()));
    } else {
        args.push("-o".to_string());
        args.push(exe_file.display().to_string());
        args.push("-lm".to_string());
    }

    // Mirrors clang.vale lines 68-70
    if debug_symbols {
        args.push("-g".to_string());
    }

    // Mirrors clang.vale lines 72-76
    // Workaround for subprocess stderr handling
    args.push("-Wno-nullability-completeness".to_string());
    args.push("-Wno-availability".to_string());
    args.push("-Wno-format".to_string());

    // Mirrors clang.vale lines 78-84
    if pic {
        args.push("-fPIC".to_string());
    }

    if pie {
        args.push("-fPIE".to_string());
    } else if !windows {
        // Some Linux distros (Ubuntu 22.04+) default the system linker to PIE,
        // which rejects non-PIC objects from the Vale backend with
        // "relocation R_X86_64_32 ... can not be used when making a PIE object".
        // Explicitly disable PIE link when the caller didn't request it.
        args.push("-no-pie".to_string());
    }

    // Mirrors clang.vale lines 86-96
    if asan {
        if windows {
            args.push("/fsanitize=address".to_string());
            args.push("clang_rt.asan_dynamic-x86_64.lib".to_string());
            args.push("clang_rt.asan_dynamic_runtime_thunk-x86_64.lib".to_string());
        } else {
            args.push("-fsanitize=address".to_string());
            args.push("-fsanitize=leak".to_string());
            args.push("-fno-omit-frame-pointer".to_string());
        }
    }

    // Mirrors clang.vale lines 98-100
    for clang_input in clang_inputs {
        args.push(clang_input.display().to_string());
    }

    // Mirrors clang.vale line 102
    let child = Command::new(program)
        .args(&args)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|e| format!("Failed to spawn clang process: {}", e))?;

    Ok(child)
}

