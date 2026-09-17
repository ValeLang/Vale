// Frontend invocation. Calls `frontend_rust::pass_manager::build`
// in-process (linked as a library); runs the MetalLowerer pipeline straight
// into the C++ backend, no subprocess and no JSON intermediate.

use std::path::{Path, PathBuf};

#[derive(Debug, Clone)]
pub struct ProjectDirectoryDeclaration {
    pub project_name: String,
    pub path: PathBuf,
}

#[derive(Debug, Clone)]
pub struct ProjectValeInputDeclaration {
    pub project_name: String,
    pub path: PathBuf,
}

#[derive(Debug, Clone)]
pub struct ProjectNonValeInputDeclaration {
    pub path: PathBuf,
}

/// Run the complete in-process pipeline: parse → typing → hammer →
/// MetalLowerer → backend_compile_program → clang link. Returns the linked
/// executable path via `BuiltProgram`.
pub fn compile_in_process(
    project_directories: &[ProjectDirectoryDeclaration],
    project_vale_inputs: &[ProjectValeInputDeclaration],
    _project_non_vale_inputs: &[ProjectNonValeInputDeclaration],
    benchmark: bool,
    sanity_check: bool,
    verbose: bool,
    debug_output: bool,
    include_builtins: bool,
    output_dir: &Path,
    backend_argv: Vec<String>,
    clang_cfg: frontend_rust::pass_manager::pass_manager::ClangConfig,
) -> Result<frontend_rust::pass_manager::pass_manager::BuiltProgram, String> {
    // Build the same arg list pass_manager::main consumes, then route it
    // through parse_opts to populate the Options struct.
    let mut frontend_args: Vec<String> = Vec::new();
    frontend_args.push("build".to_string());
    frontend_args.push("--output_dir".to_string());
    frontend_args.push(output_dir.display().to_string());

    if benchmark { frontend_args.push("--benchmark".to_string()); }
    if sanity_check {
        frontend_args.push("--sanity_check".to_string());
        frontend_args.push("true".to_string());
    }
    if verbose { frontend_args.push("--verbose".to_string()); }
    if debug_output { frontend_args.push("--debug_output".to_string()); }
    if !include_builtins {
        frontend_args.push("--include_builtins".to_string());
        frontend_args.push("false".to_string());
    }

    for declaration in project_directories {
        let resolved_path = declaration.path.canonicalize()
            .unwrap_or_else(|_| declaration.path.clone());
        frontend_args.push(format!("{}={}", declaration.project_name, resolved_path.display()));
    }
    for declaration in project_vale_inputs {
        let resolved_path = declaration.path.canonicalize()
            .unwrap_or_else(|_| declaration.path.clone());
        frontend_args.push(format!("{}={}", declaration.project_name, resolved_path.display()));
    }

    let parse_bump = bumpalo::Bump::new();
    let parse_arena = frontend_rust::parse_arena::ParseArena::new(&parse_bump);
    let keywords = frontend_rust::keywords::Keywords::new_for_parse(&parse_arena);

    let opts = frontend_rust::pass_manager::pass_manager::parse_opts(
        &parse_arena,
        frontend_rust::pass_manager::pass_manager::Options {
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
        frontend_args,
    );

    let argv_refs: Vec<&str> = backend_argv.iter().map(|s| s.as_str()).collect();
    frontend_rust::pass_manager::pass_manager::build(
        &parse_arena, &keywords, &opts, &argv_refs, &clang_cfg,
    )
}

