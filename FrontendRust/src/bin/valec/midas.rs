// Backend (Midas) argv assembly. The actual backend call happens inside
// pass_manager::build (which feeds the in-process MetalCache
// populated by MetalLowerer straight into backend_compile_program).

use std::path::Path;

/// Build the argv that pass_manager::build will feed to
/// backend_compile_program. argv[0] is the conventional program-name
/// placeholder; the rest are valeOptSet-style flags.
#[allow(clippy::too_many_arguments)]
pub fn build_backend_argv(
    output_dir: &Path,
    maybe_region_override: Option<&str>,
    maybe_opt_level: Option<&str>,
    maybe_cpu: Option<&str>,
    maybe_gen_size: Option<&str>,
    // `-o <name>` is consumed by valec for the final clang invocation;
    // the backend writes a fixed `build.o` regardless of this name.
    _executable_name: &str,
    flares: bool,
    gen_heap: bool,
    census: bool,
    verify: bool,
    opt_level: &str,
    llvm_ir: bool,
    asm: bool,
    enable_replaying: bool,
    replay_whitelist_extern: &str,
    enable_side_calling: bool,
    pic: bool,
    print_mem_overhead: bool,
    elide_checks_for_known_live: bool,
    elide_checks_for_regions: bool,
    use_atomic_rc: bool,
    force_all_known_live: bool,
    include_bounds_checks: bool,
) -> Vec<String> {
    let mut command_line_args = Vec::new();
    command_line_args.push("backend".to_string()); // argv[0] placeholder
    command_line_args.push("--verify".to_string());
    command_line_args.push("--output_dir".to_string());
    command_line_args.push(output_dir.display().to_string());

    // Mirrors midas.vale lines 41-109
    if let Some(region_override) = maybe_region_override {
        command_line_args.push("--region_override".to_string());
        command_line_args.push(region_override.to_string());
    }
    if let Some(opt_level_val) = maybe_opt_level {
        command_line_args.push("--opt_level".to_string());
        command_line_args.push(opt_level_val.to_string());
    }
    if let Some(cpu) = maybe_cpu {
        command_line_args.push("--cpu".to_string());
        command_line_args.push(cpu.to_string());
    }
    if let Some(gen_size) = maybe_gen_size {
        command_line_args.push("--gen_size".to_string());
        command_line_args.push(gen_size.to_string());
    }
    if flares {
        command_line_args.push("--flares".to_string());
    }
    if gen_heap {
        command_line_args.push("--gen_heap".to_string());
    }
    if census {
        command_line_args.push("--census".to_string());
    }
    if verify {
        command_line_args.push("--verify".to_string());
    }
    if opt_level != "O0" {
        command_line_args.push("--opt_level".to_string());
        command_line_args.push(opt_level.to_string());
    }
    if llvm_ir {
        command_line_args.push("--llvm_ir".to_string());
    }
    if asm {
        command_line_args.push("--asm".to_string());
    }
    if enable_replaying {
        command_line_args.push("--enable_replaying=true".to_string());
    }
    if !replay_whitelist_extern.is_empty() {
        command_line_args.push(format!("--replay_whitelist_extern={}", replay_whitelist_extern));
    }
    if enable_side_calling {
        command_line_args.push("--enable_side_calling=true".to_string());
    }
    if pic {
        command_line_args.push("--pic".to_string());
    }
    if print_mem_overhead {
        command_line_args.push("--print_mem_overhead=true".to_string());
    }
    if !elide_checks_for_known_live {
        command_line_args.push("--elide_checks_for_known_live=false".to_string());
    }
    if !elide_checks_for_regions {
        command_line_args.push("--elide_checks_for_regions=false".to_string());
    }
    if force_all_known_live {
        command_line_args.push("--force_all_known_live".to_string());
    }
    if !include_bounds_checks {
        command_line_args.push("--include_bounds_checks=false".to_string());
    }
    if use_atomic_rc {
        command_line_args.push("--use_atomic_rc=true".to_string());
    }

    // No vast file paths — MetalLowerer feeds the program directly in-process.
    command_line_args
}

