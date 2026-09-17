// Build orchestration logic.

use std::fs;
use std::path::{Path, PathBuf};
use std::process;

use clap::Args;

use crate::midas;
use crate::frontend::{ProjectDirectoryDeclaration, ProjectNonValeInputDeclaration, ProjectValeInputDeclaration};

/// Flags accepted by `valec build`.
#[derive(Args, Debug)]
pub struct BuildArgs {
    /// Where to write the compiled .ll, .o, and executable.
    #[arg(long, default_value = "build")]
    output_dir: PathBuf,

    /// Name of the produced executable.
    #[arg(short = 'o', default_value = "main")]
    executable_name: String,

    /// Override the location of the builtins/ directory shipped alongside valec.
    #[arg(long)]
    builtins_dir_override: Option<PathBuf>,

    /// Override the clang binary used for the final link.
    #[arg(long)]
    clang_override: Option<String>,

    /// Override the libc include + lib directory.
    #[arg(long)]
    libc_override: Option<String>,

    /// Override the region inference for the whole compilation.
    #[arg(long)]
    region_override: Option<String>,

    /// LLVM optimisation level (e.g. O0, O1, O2, O3).
    #[arg(long, default_value = "O0")]
    opt_level: String,

    /// LLVM CPU target.
    #[arg(long)]
    cpu: Option<String>,

    /// Generational-reference field size in bits.
    #[arg(long)]
    gen_size: Option<String>,

    /// Whitelist of extern names that may be replayed.
    #[arg(long, default_value = "")]
    replay_whitelist_extern: String,

    // --- boolean knobs ---
    #[arg(long, default_value_t = false)]
    benchmark: bool,

    #[arg(long, default_value_t = false)]
    verbose: bool,

    #[arg(long, default_value_t = false)]
    debug_output: bool,

    #[arg(long, default_value_t = true)]
    include_builtins: bool,

    #[arg(long, default_value_t = true)]
    output_vast: bool,

    #[arg(long, default_value_t = false)]
    reuse_vast: bool,

    #[arg(long, default_value_t = true)]
    run_backend: bool,

    #[arg(long, default_value_t = true)]
    run_clang: bool,

    #[arg(long, default_value_t = true)]
    sanity_check: bool,

    #[arg(long, default_value_t = false)]
    enable_replaying: bool,

    #[arg(long, default_value_t = false)]
    enable_side_calling: bool,

    /// Skip linking the standard library.
    #[arg(long, default_value_t = false)]
    no_std: bool,

    #[arg(long, default_value_t = false)]
    flares: bool,

    #[arg(long, default_value_t = false)]
    gen_heap: bool,

    #[arg(long, default_value_t = false)]
    census: bool,

    /// Build with AddressSanitizer.
    #[arg(long, default_value_t = false)]
    asan: bool,

    #[arg(long, default_value_t = false)]
    verify: bool,

    /// Include debug symbols in the executable.
    #[arg(short = 'g', default_value_t = false)]
    debug_symbols: bool,

    /// Emit LLVM IR alongside the executable.
    #[arg(long, default_value_t = false)]
    llvm_ir: bool,

    /// Build with position-independent code.
    #[arg(long, default_value_t = true)]
    pic: bool,

    /// Build a position-independent executable.
    #[arg(long, default_value_t = true)]
    pie: bool,

    /// Emit assembly alongside the executable.
    #[arg(long, default_value_t = true)]
    asm: bool,

    #[arg(long, default_value_t = false)]
    print_mem_overhead: bool,

    #[arg(long, default_value_t = true)]
    elide_checks_for_known_live: bool,

    #[arg(long, default_value_t = true)]
    elide_checks_for_regions: bool,

    #[arg(long, default_value_t = false)]
    use_atomic_rc: bool,

    #[arg(long, default_value_t = true)]
    include_bounds_checks: bool,

    #[arg(long, default_value_t = false)]
    force_all_known_live: bool,

    /// Module=directory and module=file.vale mappings. Any positional arg
    /// containing `=` is parsed as `<name>=<path>`; everything else is rejected.
    #[arg(trailing_var_arg = true)]
    inputs: Vec<String>,
}

/// Main build entry point.
pub fn build_stuff(compiler_dir: &Path, args: BuildArgs) {
    let windows = cfg!(windows);

    let builtins_dir = if let Some(override_path) = args.builtins_dir_override.as_ref() {
        if !override_path.is_dir() {
            eprintln!("Error: --builtins-dir-override's value ({}) is not a directory.", override_path.display());
            process::exit(1);
        }
        override_path.clone()
    } else {
        compiler_dir.join("builtins")
    };

    let mut project_directory_declarations = Vec::new();
    let mut project_vale_input_declarations = Vec::new();
    let mut project_non_vale_input_declarations = Vec::new();

    if !args.no_std {
        project_directory_declarations.push(ProjectDirectoryDeclaration {
            project_name: "stdlib".to_string(),
            path: compiler_dir.join("stdlib").join("src"),
        });
    }

    // Parse positional inputs: name=path entries become project declarations.
    for input in &args.inputs {
        let Some((project_name, path_str)) = input.split_once('=') else {
            eprintln!("Unrecognized input: {}", input);
            process::exit(1);
        };
        let path = PathBuf::from(path_str);
        let resolved_path = path.canonicalize().unwrap_or_else(|_| path.clone());

        if resolved_path.is_dir() {
            project_directory_declarations.push(ProjectDirectoryDeclaration {
                project_name: project_name.to_string(),
                path: resolved_path,
            });
        } else if resolved_path
            .file_name()
            .and_then(|n| n.to_str())
            .map(|n| n.ends_with(".vale"))
            .unwrap_or(false)
        {
            project_vale_input_declarations.push(ProjectValeInputDeclaration {
                project_name: project_name.to_string(),
                path: resolved_path,
            });
        } else {
            project_non_vale_input_declarations.push(ProjectNonValeInputDeclaration {
                path: resolved_path,
            });
        }
    }

    if !builtins_dir.exists() {
        eprintln!("Cannot find builtins directory: {}", builtins_dir.display());
        process::exit(1);
    }

    if args.verbose {
        println!("Invoking Frontend...");
    }

    if args.reuse_vast {
        // The in-process MetalLowerer path has no JSON intermediate to reuse.
        eprintln!("Error: --reuse-vast is no longer supported; compilation runs directly in-process.");
        process::exit(1);
    }

    if args.output_dir.exists() {
        println!("Deleting existing {}.", args.output_dir.display());
        if let Err(e) = fs::remove_dir_all(&args.output_dir) {
            eprintln!("Error removing old dir: {}", e);
            process::exit(1);
        }
        assert!(!args.output_dir.exists(), "Removing old dir {} failed!", args.output_dir.display());
    }

    if let Err(e) = fs::create_dir_all(&args.output_dir) {
        eprintln!("Error creating output directory: {}", e);
        process::exit(1);
    }

    let backend_argv = midas::build_backend_argv(
        &args.output_dir,
        args.region_override.as_deref(),
        Some(args.opt_level.as_str()),
        args.cpu.as_deref(),
        args.gen_size.as_deref(),
        &args.executable_name,
        args.flares, args.gen_heap, args.census, args.verify,
        &args.opt_level,
        args.llvm_ir, args.asm,
        args.enable_replaying, &args.replay_whitelist_extern,
        args.enable_side_calling, args.pic, args.print_mem_overhead,
        args.elide_checks_for_known_live, args.elide_checks_for_regions,
        args.use_atomic_rc,
        args.force_all_known_live, args.include_bounds_checks,
    );

    let extra_inputs: Vec<PathBuf> = project_non_vale_input_declarations
        .iter()
        .map(|d| d.path.clone())
        .collect();

    let clang_cfg = frontend_rust::pass_manager::pass_manager::ClangConfig {
        builtins_dir: builtins_dir.clone(),
        extra_inputs,
        clang_path: args.clang_override.clone(),
        libc_path: args.libc_override.clone(),
        executable_name: args.executable_name.clone(),
        asan: args.asan,
        debug_symbols: args.debug_symbols,
        pic: args.pic,
        pie: args.pie,
        windows,
    };

    if !args.run_backend {
        println!("Not running backend, stopping here. (Note: --run-backend=false now also skips clang.)");
        return;
    }

    println!("Running frontend + backend + clang in-process...");
    let bp = match crate::frontend::compile_in_process(
        &project_directory_declarations,
        &project_vale_input_declarations,
        &project_non_vale_input_declarations,
        args.benchmark, args.sanity_check, args.verbose, args.debug_output,
        args.include_builtins,
        &args.output_dir,
        backend_argv,
        clang_cfg,
    ) {
        Ok(result) => result,
        Err(e) => {
            eprintln!("Compilation error: {}", e);
            process::exit(1);
        }
    };

    if bp.rc != 0 {
        eprintln!("Compilation returned error code {}, aborting.", bp.rc);
        process::exit(bp.rc);
    }

    if args.verbose {
        println!("Done! Stems: {:?}", bp.package_stems);
    }
}
