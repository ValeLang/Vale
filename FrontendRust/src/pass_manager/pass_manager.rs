// Main entry point for the Vale compiler

use crate::compile_options::GlobalOptions;
use crate::higher_typing::higher_typing_error_humanizer;
use crate::utils::source_code_utils;
use crate::interner::StrI;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::keywords::Keywords;
use crate::pass_manager::FullCompilation;
use crate::pass_manager::FullCompilationOptions;
use crate::utils::code_hierarchy::{IPackageResolver, PackageCoordinate};
use bumpalo::Bump;
use std::collections::HashMap;
use std::fs;
use std::path::PathBuf;
use std::sync::Arc;
use crate::utils::code_hierarchy::FileCoordinateMap;
use crate::builtins::builtins::get_code_map as get_builtins_code_map;
use crate::final_ast::ast::PackageH;
use crate::simplifying::hammer::Hammer;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::typing::typing_interner::TypingInterner;
use std::collections::HashSet;
use std::fs::write;
use std::path::Path;
use std::process::exit;
use std::time::Instant;

#[derive(Clone)]
pub enum IFrontendInput<'a> {
  SourceInput {
    package_coord: &'a PackageCoordinate<'a>,
    name: String,
    code: String,
  },
  ModulePathInput {
    module: StrI<'a>,
    module_path: String,
  },
  DirectFilePathInput {
    package_coord: &'a PackageCoordinate<'a>,
    path: String,
  },
}
impl<'a> IFrontendInput<'a> {
  pub fn package_coord<'ctx>(&self, parse_arena: &'ctx ParseArena<'a>) -> &'a PackageCoordinate<'a> {
    match self {
      IFrontendInput::SourceInput { package_coord, .. } => *package_coord,
      IFrontendInput::ModulePathInput { module, .. } => {
        parse_arena.intern_package_coordinate(*module, &[])
      }
      IFrontendInput::DirectFilePathInput { package_coord, .. } => *package_coord,
    }
  }
}

pub struct Options<'a> {
  pub inputs: Vec<IFrontendInput<'a>>,
  pub output_dir_path: Option<String>,
  pub benchmark: bool,
  pub output_vast: bool,
  pub include_builtins: bool,
  pub mode: Option<String>,
  pub sanity_check: bool,
  pub use_optimized_solver: bool,
  pub use_overload_index: bool,
  pub verbose_errors: bool,
  pub debug_output: bool,
}

pub fn parse_opts<'a>(parse_arena: &'a ParseArena<'a>, opts: Options<'a>, list: Vec<String>) -> Options<'a> {
  parse_opts_recursive(parse_arena, opts, &list, 0)
}

fn parse_opts_recursive<'a>(
  parse_arena: &'a ParseArena<'a>,
  mut opts: Options<'a>,
  list: &[String],
  index: usize,
) -> Options<'a> {
  if index >= list.len() {
    return opts;
  }

  let arg = &list[index];

  match arg.as_str() {
    "--output_dir" => {
      if index + 1 >= list.len() {
        eprintln!("--output_dir requires a value");
        exit(22);
      }
      if opts.output_dir_path.is_some() {
        eprintln!("Multiple output files specified!");
        exit(22);
      }
      opts.output_dir_path = Some(list[index + 1].clone());
      parse_opts_recursive(parse_arena, opts, list, index + 2)
    }
    "--output_vast" => {
      if index + 1 >= list.len() {
        eprintln!("--output_vast requires a value");
        exit(22);
      }
      opts.output_vast = list[index + 1].parse().unwrap_or(false);
      parse_opts_recursive(parse_arena, opts, list, index + 2)
    }
    "--sanity_check" => {
      if index + 1 >= list.len() {
        eprintln!("--sanity_check requires a value");
        exit(22);
      }
      opts.sanity_check = list[index + 1].parse().unwrap_or(false);
      parse_opts_recursive(parse_arena, opts, list, index + 2)
    }
    "--include_builtins" => {
      if index + 1 >= list.len() {
        eprintln!("--include_builtins requires a value");
        exit(22);
      }
      opts.include_builtins = list[index + 1].parse().unwrap_or(false);
      parse_opts_recursive(parse_arena, opts, list, index + 2)
    }
    "--use_overload_index" => {
      if index + 1 >= list.len() {
        eprintln!("--use_overload_index requires a value");
        exit(22);
      }
      opts.use_overload_index = list[index + 1].parse().unwrap_or(false);
      parse_opts_recursive(parse_arena, opts, list, index + 2)
    }
    "--simple_solver" => {
      if index + 1 >= list.len() {
        eprintln!("--simple_solver requires a value");
        exit(22);
      }
      opts.use_optimized_solver = !list[index + 1].parse().unwrap_or(false);
      parse_opts_recursive(parse_arena, opts, list, index + 2)
    }
    "--benchmark" => {
      opts.benchmark = true;
      parse_opts_recursive(parse_arena, opts, list, index + 1)
    }
    "-v" | "--verbose" => {
      opts.verbose_errors = true;
      parse_opts_recursive(parse_arena, opts, list, index + 1)
    }
    "--debug_output" => {
      opts.debug_output = true;
      parse_opts_recursive(parse_arena, opts, list, index + 1)
    }
    _ if arg.starts_with("-") => {
      eprintln!("Unknown option {}", arg);
      exit(22);
    }
    _ => {
      if opts.mode.is_none() {
        opts.mode = Some(arg.clone());
        parse_opts_recursive(parse_arena, opts, list, index + 1)
      } else {
        if arg.contains("=") {
          let parts: Vec<&str> = arg.split('=').collect();
          if parts.len() != 2 {
            eprintln!("Arguments can only have 1 equals. Saw: {}", arg);
            exit(22);
          }
          if parts[0].is_empty() {
            eprintln!("Must have a module name before equals. Saw: {}", arg);
            exit(22);
          }
          if parts[1].is_empty() {
            eprintln!("Must have a file path after equals. Saw: {}", arg);
            exit(22);
          }

          let package_coord_str = parts[0];
          let path = parts[1];

          let package_coordinate = if package_coord_str.contains(".") {
            let package_coord_parts: Vec<&str> = package_coord_str.split('.').collect();
            let module = parse_arena.intern_str(package_coord_parts[0]);
            let packages: Vec<StrI<'a>> = package_coord_parts[1..]
              .iter()
              .map(|s| parse_arena.intern_str(s))
              .collect();
            parse_arena.intern_package_coordinate(module, &packages)
          } else {
            parse_arena.intern_package_coordinate(parse_arena.intern_str(package_coord_str), &[])
          };

          let input = if path.ends_with(".vale") {
            IFrontendInput::DirectFilePathInput {
              package_coord: package_coordinate,
              path: path.to_string(),
            }
          } else {
            if !package_coordinate.packages.is_empty() {
              eprintln!("Cannot define a directory for a specific package, only for a module.");
              exit(22);
            }
            IFrontendInput::ModulePathInput {
              module: package_coordinate.module,
              module_path: path.to_string(),
            }
          };

          opts.inputs.push(input);
          parse_opts_recursive(parse_arena, opts, list, index + 1)
        } else {
          eprintln!("Unrecognized input: {}", arg);
          exit(22);
        }
      }
    }
  }
}

pub struct FileSystemResolver<'a> {
  module_roots: HashMap<String, PathBuf>,
  direct_file_inputs: HashMap<&'a PackageCoordinate<'a>, PathBuf>,
}

impl<'a> FileSystemResolver<'a> {
  pub fn new(
    module_roots: HashMap<String, PathBuf>,
    direct_file_inputs: HashMap<&'a PackageCoordinate<'a>, PathBuf>,
  ) -> Self {
    FileSystemResolver {
      module_roots,
      direct_file_inputs,
    }
  }
}

impl<'a> IPackageResolver<'a, HashMap<String, String>> for FileSystemResolver<'a> {
  fn resolve(&self, package_coord: &'a PackageCoordinate<'a>) -> Option<HashMap<String, String>> {
    if let Some(file_path) = self.direct_file_inputs.get(package_coord) {
      if let Ok(code) = fs::read_to_string(file_path) {
        let filepath = file_path.to_string_lossy().to_string();
        let mut result = HashMap::new();
        result.insert(filepath, code);
        return Some(result);
      }
    }

    let module_name = package_coord.module.as_str();
    let module_root = self.module_roots.get(module_name)?;

    // Build path: module_root/package1/package2/...
    let mut dir_path = module_root.clone();
    for package_step in &package_coord.packages {
      dir_path.push(package_step.as_str());
    }

    // Find all .vale files in this directory
    let mut results = HashMap::new();

    if let Ok(entries) = fs::read_dir(&dir_path) {
      for entry in entries.flatten() {
        let path = entry.path();
        if path.extension().and_then(|s| s.to_str()) == Some("vale") {
          if let Ok(code) = fs::read_to_string(&path) {
            let filepath = path.to_string_lossy().to_string();
            results.insert(filepath, code);
          }
        }
      }
    }

    if results.is_empty() {
      None
    } else {
      Some(results)
    }
  }
}

fn resolve_package_contents<'a>(
  parse_arena: &ParseArena<'a>,
  inputs: &[IFrontendInput<'a>],
  package_coord: &PackageCoordinate<'a>,
) -> Option<HashMap<String, String>>
{
  let module = &package_coord.module;
  let packages = &package_coord.packages;

  let mut source_inputs: Vec<(String, String)> = Vec::new();

  for (index, input) in inputs.iter().enumerate() {
    if input.package_coord(parse_arena).module != *module {
      continue;
    }

    match input {
      IFrontendInput::SourceInput {
        package_coord: _,
        name,
        code,
      } => {
        if packages.is_empty() {
          source_inputs.push((format!("{}({})", index, name), code.clone()));
        }
      }
      IFrontendInput::ModulePathInput {
        module: _,
        module_path,
      } => {
        let mut directory_path = module_path.clone();
        for package_step in packages {
          directory_path.push('/');
          directory_path.push_str(package_step.as_str());
        }

        let directory = Path::new(&directory_path);
        if let Ok(entries) = fs::read_dir(directory) {
          for entry in entries.flatten() {
            let path = entry.path();
            if let Some(name) = path.file_name() {
              let name_str = name.to_string_lossy();
              if name_str.ends_with(".vale") {
                if let Ok(code) = fs::read_to_string(&path) {
                  source_inputs.push((path.display().to_string(), code));
                }
              }
            }
          }
        }
      }
      IFrontendInput::DirectFilePathInput {
        package_coord: _,
        path,
      } => {
        if let Ok(code) = fs::read_to_string(path) {
          source_inputs.push((path.clone(), code));
        }
      }
    }
  }

  let mut filepath_to_source: HashMap<String, String> = HashMap::new();
  for (filepath, code) in source_inputs {
    if filepath_to_source.contains_key(&filepath) {
      panic!("Input filepaths overlap!");
    }
    filepath_to_source.insert(filepath, code);
  }

  Some(filepath_to_source)
}

/// Configuration for the post-backend clang link step that produces the
/// final executable. Both `valec` and the test harness build a `ClangConfig`
/// and hand it to `build`; the function handles abi/builtin walking + clang
/// invocation internally.
pub struct ClangConfig {
  /// Directory holding the Backend builtins (`strings.c`, `assert.c`, etc.).
  pub builtins_dir: PathBuf,
  /// Additional `.c` files to link beyond builtins, the abi auto-walker,
  /// and the Frontend-driven `native/*.c` auto-walker.
  /// Used ONLY for caller-explicit non-Vale inputs: valec's `name=file.c`
  /// CLI form, test-only shims (`testbuiltins.c`), and extern tests'
  /// `native/test.c`. Per-package `native/*.c` isnt needed here, those are
  /// auto-discovered.
  pub extra_inputs: Vec<PathBuf>,
  pub clang_path: Option<String>,
  pub libc_path: Option<String>,
  pub executable_name: String,
  pub asan: bool,
  pub debug_symbols: bool,
  pub pic: bool,
  pub pie: bool,
  pub windows: bool,
}

pub struct BuiltProgram {
  pub rc: i32,
  pub package_stems: Vec<String>,
  pub exe_path: PathBuf,
}

/// Drive the full pipeline (parse → scout → typing → instantiating → hammer →
/// MetalLowerer → backend → clang link) and return the linked executable's
/// path. Returns `BuiltProgram { rc, package_stems, exe_path }`. The stems
/// are dot-joined `(project, package_steps...)` strings (e.g. `"__vale"`,
/// `"stdlib.collections.hashmap"`) — one per compiled package.
pub fn build<'p, 'ctx>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  opts: &Options<'p>,
  backend_argv: &[&str],
  clang_cfg: &ClangConfig,
) -> Result<BuiltProgram, String>
where
  'p: 'ctx,
{
  let output_dir_path = opts.output_dir_path.as_ref().unwrap();
  fs::create_dir_all(output_dir_path)
    .map_err(|e| format!("Failed to create output directory: {}", e))?;
  fs::create_dir_all(format!("{}/include", output_dir_path))
    .map_err(|e| format!("Failed to create include directory: {}", e))?;

  let all_inputs = &opts.inputs;

  let package_coords: Vec<&PackageCoordinate<'p>> = all_inputs
    .iter()
    .map(|input| input.package_coord(parse_arena))
    .collect::<HashSet<_>>()
    .into_iter()
    .collect();

  let builtins_code_map = get_builtins_code_map(parse_arena, keywords);
  let mut packages_to_build = vec![PackageCoordinate::builtin(parse_arena, keywords)];
  packages_to_build.extend(package_coords);

  let all_inputs_clone = all_inputs.clone();
  let resolver = builtins_code_map.or(move |package_coord: &'p PackageCoordinate<'p>| {
    resolve_package_contents(parse_arena, &all_inputs_clone, &*package_coord)
  });

  let options = FullCompilationOptions {
    global_options: GlobalOptions {
      sanity_check: opts.sanity_check,
      use_overload_index: opts.use_overload_index,
      use_optimized_solver: opts.use_optimized_solver,
      verbose_errors: opts.verbose_errors,
      debug_output: opts.debug_output,
    },
    debug_out: if opts.debug_output {
      Arc::new(|s: &str| println!("#: {}", s))
    } else {
      Arc::new(|_: &str| {})
    },
  };

  let scout_bump = bumpalo::Bump::new();
  let typing_bump = bumpalo::Bump::new();
  let hammer_bump = bumpalo::Bump::new();
  let instantiating_bump = bumpalo::Bump::new();
  let scout_arena = ScoutArena::new(&scout_bump);
  let scout_keywords = Keywords::new_for_scout(&scout_arena);
  let parser_keywords = Keywords::new_for_parse(parse_arena);
  let hammer_interner = HammerInterner::new(&hammer_bump);
  let typing_interner = TypingInterner::new(&typing_bump);
  let mut compilation = FullCompilation::new(
    &scout_arena,
    &hammer_interner,
    &typing_interner,
    &scout_keywords,
    &parser_keywords,
    parse_arena,
    packages_to_build,
    &resolver,
    options,
    &instantiating_bump,
  );

  let vale_code_map = compilation.get_code_map().expect("getCodeMap failed");

  let parseds = match compilation.get_parseds() {
    Err(failed_parse) => panic!(
      "ParseErrorHumanizer.humanize not yet implemented. FailedParse: {:?}",
      failed_parse
    ),
    Ok(p) => p,
  };

  match compilation.get_scoutput() {
    Err(e) => panic!("PostParserErrorHumanizer.humanize not yet implemented: {:?}", e),
    Ok(_) => {}
  }
  match compilation.get_astrouts() {
    Err(error) => return Err(higher_typing_error_humanizer::humanize(
      &|x| source_code_utils::humanize_pos_code_map(&vale_code_map, &x),
      &|a, b| source_code_utils::lines_between(&vale_code_map, &a, &b),
      &|x| source_code_utils::line_range_containing(&vale_code_map, &x),
      &|x| source_code_utils::line_containing(&vale_code_map, &x),
      &error)),
    Ok(_) => {}
  }
  match compilation.get_compiler_outputs() {
    Err(e) => return Err(crate::typing::compiler_error_humanizer::humanize(
      &scout_arena, &typing_interner, opts.verbose_errors,
      &|x| crate::utils::source_code_utils::humanize_pos_code_map(&vale_code_map, &x),
      &|a, b| crate::utils::source_code_utils::lines_between(&vale_code_map, &a, &b),
      &|x| crate::utils::source_code_utils::line_range_containing(&vale_code_map, &x),
      &|x| crate::utils::source_code_utils::line_containing(&vale_code_map, &x),
      e)),
    Ok(_) => {}
  }

  let program_h = compilation.get_hamuts();

  // Collect (project, package_steps) stems for each compiled package, so
  // the valec bin can find their matching native/*.c dirs at link time.
  // Empty module → "__vale" (Backend's `userFuncName` convention; see the
  // walker's lower_package_coord and Backend/src/vale.cpp).
  let package_coord_stems: Vec<String> = program_h.packages.package_coord_to_contents.iter()
    .map(|(coord, _pkg)| {
      let module = if coord.module.0.is_empty() { "__vale" } else { coord.module.0 };
      let pkg_steps: String = coord.packages.iter().map(|p| format!(".{}", p.0)).collect();
      format!("{}{}", module, pkg_steps)
    })
    .collect();

  // MetalLowerer: H-AST → MetalCache via FFI, replacing readjson.cpp.
  let cache = crate::backend_ffi::metal_cache::MetalCache::new();
  let program = crate::backend_ffi::metal_lowerer::populate_metal_cache(&cache, program_h);

  let rc = crate::backend_ffi::backend_compile_program_safe(&cache, &program, backend_argv);
  if rc != 0 {
    return Ok(BuiltProgram {
      rc,
      package_stems: package_coord_stems,
      exe_path: PathBuf::from(output_dir_path).join(&clang_cfg.executable_name),
    });
  }

  // Clang link: collect builtin .c files + abi/<project>/*.c files written
  // by the backend + caller-supplied extras, then invoke clang. Returns the
  // path of the linked executable.
  let mut clang_inputs: Vec<PathBuf> = Vec::new();
  let obj = PathBuf::from(output_dir_path).join("build.o");
  clang_inputs.push(obj);
  if let Ok(entries) = fs::read_dir(&clang_cfg.builtins_dir) {
    for entry in entries.flatten() {
      let path = entry.path();
      if path.is_file()
        && path.extension().and_then(|s| s.to_str()) == Some("c")
      {
        clang_inputs.push(path);
      }
    }
  }
  let abi_dir = PathBuf::from(output_dir_path).join("abi");
  let mut project_names: HashSet<String> = HashSet::new();
  for stem in &package_coord_stems {
    let parts: Vec<&str> = stem.split('.').collect();
    if parts.is_empty() { continue; }
    project_names.insert(parts[0].to_string());
  }
  for project_name in &project_names {
    let pkg_abi = abi_dir.join(project_name);
    if pkg_abi.is_dir() {
      if let Ok(entries) = fs::read_dir(&pkg_abi) {
        for entry in entries.flatten() {
          let path = entry.path();
          if path.is_file()
            && path.extension().and_then(|s| s.to_str()) == Some("c")
          {
            clang_inputs.push(path);
          }
        }
      }
    }
  }

  // For each PackageCoordinate the Frontend actually reached, look up its
  // project root (from opts.inputs' ModulePathInput entries) and scan that
  // package's `native/` dir for *.c files. Unreached packages' natives are
  // skipped, for example, stdlib subpackages a program doesn't import (e.g.
  // stdlib.date when nothing calls UnixTimestamp) don't get their native impls
  // compiled.
  let module_paths: HashMap<&str, &str> = opts.inputs.iter().filter_map(|input| {
    if let IFrontendInput::ModulePathInput { module, module_path } = input {
      Some((module.0, module_path.as_str()))
    } else {
      None
    }
  }).collect();
  for (coord, _pkg) in program_h.packages.package_coord_to_contents.iter() {
    // Synthetic __vale module (empty module name) has its impls in
    // clang_cfg.builtins_dir; skip the native walk for it.
    if coord.module.0.is_empty() { continue; }
    let project_root = match module_paths.get(coord.module.0) {
      Some(p) => *p,
      None => continue,
    };
    let mut native_dir = PathBuf::from(project_root);
    for step in coord.packages.iter() {
      native_dir.push(step.0);
    }
    native_dir.push("native");
    if !native_dir.is_dir() { continue; }
    if let Ok(entries) = fs::read_dir(&native_dir) {
      for entry in entries.flatten() {
        let path = entry.path();
        if path.is_file()
          && path.extension().and_then(|s| s.to_str()) == Some("c")
        {
          clang_inputs.push(path);
        }
      }
    }
  }

  for p in &clang_cfg.extra_inputs {
    clang_inputs.push(p.clone());
  }

  let clang_process = crate::clang::invoke_clang(
    clang_cfg.windows,
    clang_cfg.clang_path.as_deref(),
    clang_cfg.libc_path.as_deref(),
    &clang_inputs,
    &clang_cfg.executable_name,
    clang_cfg.asan,
    clang_cfg.debug_symbols,
    &PathBuf::from(output_dir_path),
    clang_cfg.pic,
    clang_cfg.pie,
  )?;
  let clang_output = clang_process
    .wait_with_output()
    .map_err(|e| format!("clang wait failed: {}", e))?;
  if !clang_output.status.success() {
    let rc = clang_output.status.code().unwrap_or(-1);
    return Err(format!(
      "clang failed with rc={}:\nstdout={}\nstderr={}",
      rc,
      String::from_utf8_lossy(&clang_output.stdout),
      String::from_utf8_lossy(&clang_output.stderr),
    ));
  }
  let exe_path = PathBuf::from(output_dir_path).join(&clang_cfg.executable_name);
  Ok(BuiltProgram {
    rc,
    package_stems: package_coord_stems,
    exe_path,
  })
}

fn write_file(filepath: &str, s: &str) {
  if filepath == "stdout:" {
    println!("{}", s);
  } else {
    let bytes = s.as_bytes();
    write(filepath, bytes)
      .unwrap_or_else(|e| panic!("Failed to write file {}: {}", filepath, e));
  }
}

