use crate::builtins::builtins::get_code_map;
use crate::compile_options::GlobalOptions;
use crate::integration_tests::tests::run_compilation::RunCompilation;
use crate::interner::StrI;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_compilation::HammerCompilation;
use crate::simplifying::hammer_compilation::HammerCompilationOptions;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::tests::tests::get_package_to_resource_resolver;
use crate::typing::typing_interner::TypingInterner;
use crate::utils::code_hierarchy::FileCoordinateMap;
use crate::utils::code_hierarchy::PackageCoordinate;
use crate::von::ast::IVonData;
use crate::von::ast::VonInt;
use std::collections::HashMap;
use crate::utils::code_hierarchy::IPackageResolver;

pub struct ImportTests;

#[test]
fn tests_import() {
    let module_a_code =
        r"
import moduleB.moo;

exported func main() int {
  a = moo();
  return a;
}
".to_string();

    let module_b_code =
        "\nfunc moo() int { return 42; }\n".to_string();

    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut map: FileCoordinateMap<String> = FileCoordinateMap::new();
    map.put(
        parse_arena.intern_file_coordinate(
            parse_arena.intern_package_coordinate(parse_arena.intern_str("moduleA"), &[]),
            "moduleA.vale"),
        module_a_code);
    map.put(
        parse_arena.intern_file_coordinate(
            parse_arena.intern_package_coordinate(parse_arena.intern_str("moduleB"), &[]),
            "moduleB.vale"),
        module_b_code);

    let packages_to_build: Vec<&PackageCoordinate> = vec![
        PackageCoordinate::builtin(&parse_arena, &parser_keywords),
        parse_arena.intern_package_coordinate(parse_arena.intern_str("moduleA"), &[]),
    ];
    let base_code_map = get_code_map(&parse_arena, &parser_keywords);
    let resolver_concrete = base_code_map
        .or(map)
        .or(get_package_to_resource_resolver());
    let resolver: &dyn IPackageResolver<HashMap<String, String>> =
        compilation_bump.alloc(resolver_concrete);
    let options = HammerCompilationOptions {
        global_options: GlobalOptions {
            sanity_check: true,
            use_overload_index: true,
            use_optimized_solver: true,
            verbose_errors: true,
            debug_output: true,
        },
        ..HammerCompilationOptions::new()
    };
    let mut compile = RunCompilation {
        interner: &typing_interner,
        hammer_compilation: HammerCompilation::new(
            &scout_arena, &hammer_interner, &typing_interner, &keywords, &parser_keywords, &parse_arena,
            packages_to_build, resolver, options, &instantiating_bump,
        ),
    };

    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("expected VonInt(42), got {:?}", other),
    }
}

#[test]
fn tests_non_imported_module_isnt_brought_in() {
    let module_a_code =
        r"
exported func main() int {
  a = 42;
  return a;
}
".to_string();

    let module_b_code =
        "\nfunc moo() int { return 73; }\n".to_string();

    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let module_a_coord = parse_arena.intern_package_coordinate(parse_arena.intern_str("moduleA"), &[]);
    let module_b_coord = parse_arena.intern_package_coordinate(parse_arena.intern_str("moduleB"), &[]);
    let mut map: FileCoordinateMap<String> = FileCoordinateMap::new();
    map.put(
        parse_arena.intern_file_coordinate(module_a_coord, "moduleA.vale"),
        module_a_code);
    map.put(
        parse_arena.intern_file_coordinate(module_b_coord, "moduleB.vale"),
        module_b_code);

    let packages_to_build: Vec<&PackageCoordinate> = vec![
        PackageCoordinate::builtin(&parse_arena, &parser_keywords),
        module_a_coord,
    ];
    let base_code_map = get_code_map(&parse_arena, &parser_keywords);
    let resolver_concrete = base_code_map
        .or(map)
        .or(get_package_to_resource_resolver());
    let resolver: &dyn IPackageResolver<HashMap<String, String>> =
        compilation_bump.alloc(resolver_concrete);
    let options = HammerCompilationOptions {
        global_options: GlobalOptions {
            sanity_check: true,
            use_overload_index: true,
            use_optimized_solver: true,
            verbose_errors: true,
            debug_output: true,
        },
        ..HammerCompilationOptions::new()
    };
    let mut compile = RunCompilation {
        interner: &typing_interner,
        hammer_compilation: HammerCompilation::new(
            &scout_arena, &hammer_interner, &typing_interner, &keywords, &parser_keywords, &parse_arena,
            packages_to_build, resolver, options, &instantiating_bump,
        ),
    };

    assert!(!compile.get_parseds().unwrap().package_coord_to_file_coords.contains_key(&module_b_coord));

    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("expected VonInt(42), got {:?}", other),
    }
}

#[test]
fn tests_import_with_paackage() {
    let module_a_code =
        r"
import moduleB.bork.*;

exported func main() int {
  a = moo();
  return a;
}
".to_string();

    let module_b_code =
        "\nfunc moo() int { return 42; }\n".to_string();

    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let module_a_coord = parse_arena.intern_package_coordinate(parse_arena.intern_str("moduleA"), &[]);
    let module_b_coord = parse_arena.intern_package_coordinate(parse_arena.intern_str("moduleB"), &[parse_arena.intern_str("bork")]);
    let mut map: FileCoordinateMap<String> = FileCoordinateMap::new();
    map.put(
        parse_arena.intern_file_coordinate(module_a_coord, "moduleA.vale"),
        module_a_code);
    map.put(
        parse_arena.intern_file_coordinate(module_b_coord, "moduleB.vale"),
        module_b_code);

    let packages_to_build: Vec<&PackageCoordinate> = vec![
        PackageCoordinate::builtin(&parse_arena, &parser_keywords),
        module_a_coord,
    ];
    let base_code_map = get_code_map(&parse_arena, &parser_keywords);
    let resolver_concrete = base_code_map
        .or(map)
        .or(get_package_to_resource_resolver());
    let resolver: &dyn IPackageResolver<HashMap<String, String>> =
        compilation_bump.alloc(resolver_concrete);
    let options = HammerCompilationOptions {
        global_options: GlobalOptions {
            sanity_check: true,
            use_overload_index: true,
            use_optimized_solver: true,
            verbose_errors: true,
            debug_output: true,
        },
        ..HammerCompilationOptions::new()
    };
    let mut compile = RunCompilation {
        interner: &typing_interner,
        hammer_compilation: HammerCompilation::new(
            &scout_arena, &hammer_interner, &typing_interner, &keywords, &parser_keywords, &parse_arena,
            packages_to_build, resolver, options, &instantiating_bump,
        ),
    };

    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("expected VonInt(42), got {:?}", other),
    }
}

#[test]
fn tests_import_of_directory_with_no_vale_files() {
    let module_a_code =
        r"
import moduleB.bork.*;

exported func main() int {
  a = 42;
  return a;
}
".to_string();

    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let module_a_coord = parse_arena.intern_package_coordinate(parse_arena.intern_str("moduleA"), &[]);
    let mut map: FileCoordinateMap<String> = FileCoordinateMap::new();
    map.put(
        parse_arena.intern_file_coordinate(module_a_coord, "moduleA.vale"),
        module_a_code);

    let packages_to_build: Vec<&PackageCoordinate> = vec![
        PackageCoordinate::builtin(&parse_arena, &parser_keywords),
        parse_arena.intern_package_coordinate(parse_arena.intern_str("moduleA"), &[]),
    ];
    let base_code_map = get_code_map(&parse_arena, &parser_keywords);
    let resolver_concrete = base_code_map
        .or(get_package_to_resource_resolver())
        .or(map)
        .or(|pc: &PackageCoordinate| -> Option<HashMap<String, String>> {
            match (pc.module.0, pc.packages.as_slice()) {
                ("moduleB", [StrI("bork")]) => Some(HashMap::new()),
                _ => None,
            }
        });
    let resolver: &dyn IPackageResolver<HashMap<String, String>> =
        compilation_bump.alloc(resolver_concrete);
    let options = HammerCompilationOptions {
        global_options: GlobalOptions {
            sanity_check: true,
            use_overload_index: true,
            use_optimized_solver: true,
            verbose_errors: true,
            debug_output: true,
        },
        ..HammerCompilationOptions::new()
    };
    let mut compile = RunCompilation {
        interner: &typing_interner,
        hammer_compilation: HammerCompilation::new(
            &scout_arena, &hammer_interner, &typing_interner, &keywords, &parser_keywords, &parse_arena,
            packages_to_build, resolver, options, &instantiating_bump,
        ),
    };

    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("expected VonInt(42), got {:?}", other),
    }
}

