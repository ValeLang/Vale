use super::compiler_test_compilation::compiler_test_compilation;
use bumpalo::Bump;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::utils::code_hierarchy::{self, IPackageResolver, PackageCoordinate};
use std::collections::HashMap;
use crate::builtins::builtins::get_code_map;
use crate::compile_options::GlobalOptions;
use crate::instantiating::InstantiatorCompilationOptions;
use crate::tests::tests::get_package_to_resource_resolver;
use crate::typing::compilation::TypingPassCompilation;
use std::sync::Arc;
use crate::utils::range::CodeLocationS;
use crate::typing::names::names::FunctionTemplateNameT;
use crate::typing::names::names::FunctionNameValT;
use crate::typing::names::names::IdValT;
use crate::typing::names::names::INameT;
use crate::typing::names::names::IdT;
use crate::typing::names::names::LambdaCitizenTemplateNameT;
use crate::typing::names::names::LambdaCitizenNameT;
use crate::typing::types::types::StructTTValT;
use crate::typing::types::types::CoordT;
use crate::typing::types::types::OwnershipT;
use crate::typing::types::types::{IRegionT, RegionT};
use crate::typing::types::types::KindT;
use crate::typing::names::names::LambdaCallFunctionTemplateNameValT;
use crate::typing::names::names::LambdaCallFunctionNameValT;
use crate::typing::names::names::StructTemplateNameT;
use crate::interner::StrI;
use crate::typing::typing_interner::TypingInterner;
use std::fs::read_to_string;
use std::marker::PhantomData;

#[test]
fn function_has_correct_name() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "exported func main() { }";
    let resolver = code_hierarchy::test_from_map(
            &parse_arena,
            HashMap::from([("test.vale".to_string(), code.to_string())]),
        )
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let id = {
        let typing_interner = &compile.typing_interner;
        let package_coord = scout_arena.intern_package_coordinate(scout_arena.intern_str("test"), &[]);
        let main_loc = CodeLocationS {
            file: scout_arena.intern_file_coordinate(package_coord, "test.vale"),
            offset: 0,
        };
        let main_template_name = typing_interner.intern_function_template_name(
            FunctionTemplateNameT {
                human_name: scout_arena.intern_str("main"),
                code_location: main_loc,
            });
        let main_name = typing_interner.intern_function_name(
            FunctionNameValT {
                template: main_template_name,
                template_args: &[],
                parameters: &[],
            });
        *typing_interner.intern_id(IdValT {
            package_coord,
            init_steps: &[],
            local_name: INameT::Function(main_name),
        })
    };
    let coutputs = compile.expect_compiler_outputs();
    assert_eq!(coutputs.functions.first().unwrap().header.id, id);
}

#[test]
fn lambda_has_correct_name() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "exported func main() { {}() }";
    let resolver = code_hierarchy::test_from_map(
            &parse_arena,
            HashMap::from([("test.vale".to_string(), code.to_string())]),
        )
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let lambda_func_id = {
        let typing_interner = &compile.typing_interner;
        let package_coord = scout_arena.intern_package_coordinate(scout_arena.intern_str("test"), &[]);
        let file_coord = scout_arena.intern_file_coordinate(package_coord, "test.vale");
        let main_loc = CodeLocationS { file: file_coord, offset: 0 };
        let main_template_name = typing_interner.intern_function_template_name(
            FunctionTemplateNameT {
                human_name: scout_arena.intern_str("main"),
                code_location: main_loc,
            });
        let main_name = typing_interner.intern_function_name(
            FunctionNameValT {
                template: main_template_name,
                template_args: &[],
                parameters: &[],
            });
        let lambda_loc = CodeLocationS { file: file_coord, offset: 23 };
        let lambda_citizen_template_name = typing_interner.intern_lambda_citizen_template_name(
            LambdaCitizenTemplateNameT {
                code_location: lambda_loc,
            });
        let lambda_citizen_name = typing_interner.intern_lambda_citizen_name(
            LambdaCitizenNameT { template: lambda_citizen_template_name });
        let lambda_citizen_id = typing_interner.intern_id(IdValT {
            package_coord,
            init_steps: &[INameT::Function(main_name)],
            local_name: INameT::LambdaCitizen(lambda_citizen_name),
        });
        let lambda_struct = typing_interner.intern_struct_tt(
            StructTTValT { id: *lambda_citizen_id });
        let lambda_share_coord = CoordT {
            ownership: OwnershipT::Share,
            region: RegionT { region: IRegionT::Default },
            kind: KindT::Struct(lambda_struct),
        };
        let lambda_func_template_name = typing_interner.intern_lambda_call_function_template_name(
            LambdaCallFunctionTemplateNameValT {
                code_location: lambda_loc,
                param_types: &[lambda_share_coord],
            });
        let lambda_func_name = typing_interner.intern_lambda_call_function_name(
            LambdaCallFunctionNameValT {
                template: lambda_func_template_name,
                template_args: &[],
                parameters: &[lambda_share_coord],
            });
        *typing_interner.intern_id(IdValT {
            package_coord,
            init_steps: &[
                INameT::Function(main_name),
                INameT::LambdaCitizenTemplate(lambda_citizen_template_name),
            ],
            local_name: INameT::LambdaCallFunction(lambda_func_name),
        })
    };
    let coutputs = compile.expect_compiler_outputs();
    let lam_func = coutputs.lookup_lambda_in("main");
    assert_eq!(lam_func.header.id, lambda_func_id);
}

#[test]
fn struct_has_correct_name() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

exported struct MyStruct { a int; }
";
    let resolver = code_hierarchy::test_from_map(
            &parse_arena,
            HashMap::from([("test.vale".to_string(), code.to_string())]),
        )
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let struct_ = coutputs.lookup_struct_by_str("MyStruct");
    match struct_.template_name {
        IdT {
            package_coord: x,
            init_steps: [],
            local_name: INameT::StructTemplate(StructTemplateNameT { human_name: StrI("MyStruct"), .. }),
            ..
        } => {
            assert!(x.is_test());
        }
        _ => panic!("struct.templateName didn't match expected pattern"),
    }
}


// NOVEL CODE — TDD reproducer for the is_type_convertible missing-cases
// panic surfaced by typing_pass_on_roguelike (RuntimeSizedArray vs anything).
// Scala equivalent at TemplataCompiler.scala:951-953 / 960-962: source-side
// or target-side RSA/SSA both return false.
#[test]
fn typing_pass_array_type_convertible() {
    

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);

    // Loads list.vale, whose `drop` for a List exercises is_type_convertible
    // on a RuntimeSizedArray vs a placeholder.
    let source = r"
import list.*;
exported func main() {
  l = List<int>();
  l.add(3);
}
";

    let builtin_coord = parse_arena.intern_package_coordinate(parser_keywords.empty_string, &[]);
    let test_tld = parse_arena.intern_package_coordinate(parse_arena.intern_str("test"), &[]);
    let resolver = code_hierarchy::test_from_map(
            &parse_arena,
            HashMap::from([("0.vale".to_string(), source.to_string())]),
        )
        .or(get_code_map(&parse_arena, &parser_keywords)
)
        .or(get_package_to_resource_resolver());
    let global_options = GlobalOptions {
        sanity_check: true,
        use_overload_index: true,
        use_optimized_solver: true,
        verbose_errors: true,
        debug_output: false,
    };
    let instantiator_options = InstantiatorCompilationOptions {
        debug_out: Arc::new(|_x: &str| {}),
    };
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = TypingPassCompilation::new(
        &typing_interner,
        &scout_arena,
        &keywords,
        &parser_keywords,
        &parse_arena,
        vec![builtin_coord, test_tld],
        &resolver,
        global_options,
        instantiator_options,
    );
    compile.expect_compiler_outputs();
}

// NOVEL CODE — TDD reproducer for the same_instance_macro panic surfaced by
// typing_pass_on_roguelike. Scala equivalent at SameInstanceMacro.scala:
// emits a FunctionHeaderT + Block(Return(IsSameInstance(ArgLookup(0),
// ArgLookup(1)))) for the `===` builtin.
#[test]
fn typing_pass_uses_same_instance() {
    

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);

    // Minimal program that triggers the `===` (vale_same_instance) builtin.
    let source = r"
struct MyStruct { }
exported func main() bool {
  a = MyStruct();
  b = MyStruct();
  return &a === &b;
}
";

    let builtin_coord = parse_arena.intern_package_coordinate(parser_keywords.empty_string, &[]);
    let test_tld = parse_arena.intern_package_coordinate(parse_arena.intern_str("test"), &[]);
    let resolver = code_hierarchy::test_from_map(
            &parse_arena,
            HashMap::from([("0.vale".to_string(), source.to_string())]),
        )
        .or(get_code_map(&parse_arena, &parser_keywords)
)
        .or(get_package_to_resource_resolver());
    let global_options = GlobalOptions {
        sanity_check: true,
        use_overload_index: true,
        use_optimized_solver: true,
        verbose_errors: true,
        debug_output: false,
    };
    let instantiator_options = InstantiatorCompilationOptions {
        debug_out: Arc::new(|_x: &str| {}),
    };
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = TypingPassCompilation::new(
        &typing_interner,
        &scout_arena,
        &keywords,
        &parser_keywords,
        &parse_arena,
        vec![builtin_coord, test_tld],
        &resolver,
        global_options,
        instantiator_options,
    );
    // Just exercise the path; success means generate_function_body_same_instance ran.
    compile.expect_compiler_outputs();
}

#[test]
fn typing_pass_ssa_destructure() {
    

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);

    let source = r"
exported func main() int {
  arr = #[#](3, 4);
  [a, b] = arr;
  return a + b;
}
";

    let builtin_coord = parse_arena.intern_package_coordinate(parser_keywords.empty_string, &[]);
    let test_tld = parse_arena.intern_package_coordinate(parse_arena.intern_str("test"), &[]);
    let resolver = code_hierarchy::test_from_map(
            &parse_arena,
            HashMap::from([("0.vale".to_string(), source.to_string())]),
        )
        .or(get_code_map(&parse_arena, &parser_keywords)
)
        .or(get_package_to_resource_resolver());
    let global_options = GlobalOptions {
        sanity_check: true,
        use_overload_index: true,
        use_optimized_solver: true,
        verbose_errors: true,
        debug_output: false,
    };
    let instantiator_options = InstantiatorCompilationOptions {
        debug_out: Arc::new(|_x: &str| {}),
    };
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = TypingPassCompilation::new(
        &typing_interner,
        &scout_arena,
        &keywords,
        &parser_keywords,
        &parse_arena,
        vec![builtin_coord, test_tld],
        &resolver,
        global_options,
        instantiator_options,
    );
    compile.expect_compiler_outputs();
}

// NOVEL CODE — TDD reproducer for the `evaluate_addressible_lookup_for_mutate
// — AddressibleClosureVariableT` panic surfaced by typing_pass_on_roguelike.
// Triggered by `set x = ...` inside a lambda where x is captured from the
// enclosing function scope.
#[test]
fn typing_pass_closure_var_mutate() {
    

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);

    let source = r"
exported func main() {
  x = 0;
  l = () => { set x = 1; };
  l();
}
";

    let builtin_coord = parse_arena.intern_package_coordinate(parser_keywords.empty_string, &[]);
    let test_tld = parse_arena.intern_package_coordinate(parse_arena.intern_str("test"), &[]);
    let resolver = code_hierarchy::test_from_map(
            &parse_arena,
            HashMap::from([("0.vale".to_string(), source.to_string())]),
        )
        .or(get_code_map(&parse_arena, &parser_keywords)
)
        .or(get_package_to_resource_resolver());
    let global_options = GlobalOptions {
        sanity_check: true,
        use_overload_index: true,
        use_optimized_solver: true,
        verbose_errors: true,
        debug_output: false,
    };
    let instantiator_options = InstantiatorCompilationOptions {
        debug_out: Arc::new(|_x: &str| {}),
    };
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = TypingPassCompilation::new(
        &typing_interner,
        &scout_arena,
        &keywords,
        &parser_keywords,
        &parse_arena,
        vec![builtin_coord, test_tld],
        &resolver,
        global_options,
        instantiator_options,
    );
    compile.expect_compiler_outputs();
}

// NOVEL CODE — TDD reproducer for the `evaluate_expression — Tuple` chain
// surfaced by typing_pass_on_roguelike. Exercises evaluate_expression Tuple
// arm → resolve_tuple → make_tuple_coord → make_tuple_kind. Scala equivalent
// at ExpressionCompiler.scala:869-876 (case TupleSE) + SequenceCompiler.scala.
#[test]
fn typing_pass_tuple_literal() {
    

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);

    let source = r"
exported func main() {
  x = (3, 4);
}
";

    let builtin_coord = parse_arena.intern_package_coordinate(parser_keywords.empty_string, &[]);
    let test_tld = parse_arena.intern_package_coordinate(parse_arena.intern_str("test"), &[]);
    let resolver = code_hierarchy::test_from_map(
            &parse_arena,
            HashMap::from([("0.vale".to_string(), source.to_string())]),
        )
        .or(get_code_map(&parse_arena, &parser_keywords)
)
        .or(get_package_to_resource_resolver());
    let global_options = GlobalOptions {
        sanity_check: true,
        use_overload_index: true,
        use_optimized_solver: true,
        verbose_errors: true,
        debug_output: false,
    };
    let instantiator_options = InstantiatorCompilationOptions {
        debug_out: Arc::new(|_x: &str| {}),
    };
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = TypingPassCompilation::new(
        &typing_interner,
        &scout_arena,
        &keywords,
        &parser_keywords,
        &parse_arena,
        vec![builtin_coord, test_tld],
        &resolver,
        global_options,
        instantiator_options,
    );
    compile.expect_compiler_outputs();
}

// NOVEL CODE — TDD reproducer for the `evaluate_expression — Destruct` panic
// surfaced by typing_pass_on_roguelike. Scala equivalent at
// ExpressionCompiler.scala:1387-1429 (case DestructSE).
#[test]
fn typing_pass_destruct_struct() {
    

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);

    let source = r"
struct MyStruct { a int; }
exported func main() {
  m = MyStruct(7);
  destruct m;
}
";

    let builtin_coord = parse_arena.intern_package_coordinate(parser_keywords.empty_string, &[]);
    let test_tld = parse_arena.intern_package_coordinate(parse_arena.intern_str("test"), &[]);
    let resolver = code_hierarchy::test_from_map(
            &parse_arena,
            HashMap::from([("0.vale".to_string(), source.to_string())]),
        )
        .or(get_code_map(&parse_arena, &parser_keywords)
)
        .or(get_package_to_resource_resolver());
    let global_options = GlobalOptions {
        sanity_check: true,
        use_overload_index: true,
        use_optimized_solver: true,
        verbose_errors: true,
        debug_output: false,
    };
    let instantiator_options = InstantiatorCompilationOptions {
        debug_out: Arc::new(|_x: &str| {}),
    };
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = TypingPassCompilation::new(
        &typing_interner,
        &scout_arena,
        &keywords,
        &parser_keywords,
        &parse_arena,
        vec![builtin_coord, test_tld],
        &resolver,
        global_options,
        instantiator_options,
    );
    compile.expect_compiler_outputs();
}

// NOVEL CODE — exploratory: run the typing pass on roguelike.vale to gauge
// how close the migration is to handling a real-world program. Loads the
// roguelike.vale source from disk, rewrites `stdlib.*` imports to use the
// Rust-side on-disk file layout, and feeds the program through
// compiler_test_compilation.
#[test]
fn typing_pass_on_roguelike() {
    

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);

    let source = read_to_string("src/tests/programs/roguelike.vale")
            .expect("could not read src/tests/programs/roguelike.vale");

    // Scala-parity: mirror Benchmark.scala — instantiate TypingPassCompilation
    // directly with packages_to_build=[BUILTIN, TEST_TLD] (the BUILTIN root
    // namespace must be in the list so its files get compiled into the global
    // env), and use Builtins.getCodeMap (puts each builtin in both
    // v.builtins.<module> empty placeholder and root namespace with content).
    let builtin_coord = parse_arena.intern_package_coordinate(parser_keywords.empty_string, &[]);
    let test_tld = parse_arena.intern_package_coordinate(parse_arena.intern_str("test"), &[]);
    let resolver = code_hierarchy::test_from_map(
            &parse_arena,
            HashMap::from([("0.vale".to_string(), source)]),
        )
        .or(get_code_map(&parse_arena, &parser_keywords)
)
        .or(get_package_to_resource_resolver());
    let global_options = GlobalOptions {
        sanity_check: true,
        use_overload_index: true,
        use_optimized_solver: true,
        verbose_errors: true,
        debug_output: true,
    };
    let instantiator_options = InstantiatorCompilationOptions {
        debug_out: Arc::new(|x: &str| println!("{}", x)),
    };
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = TypingPassCompilation::new(
        &typing_interner,
        &scout_arena,
        &keywords,
        &parser_keywords,
        &parse_arena,
        vec![builtin_coord, test_tld],
        &resolver,
        global_options,
        instantiator_options,
    );
    let result = compile.get_compiler_outputs();
    match result {
        Ok(_) => println!("DIAG: roguelike typing pass succeeded"),
        Err(e) => panic!("DIAG: roguelike typing pass failed: {:#?}", e),
    }
}
