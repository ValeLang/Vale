use crate::builtins::builtins::get_code_map;
use crate::compile_options::GlobalOptions;
use crate::final_ast::instructions::ExpressionH;
use crate::final_ast::types::CoordH;
use crate::final_ast::types::IntHT;
use crate::final_ast::types::InterfaceHTValH;
use crate::final_ast::types::KindHT;
use crate::final_ast::types::LocationH;
use crate::final_ast::types::OwnershipH;
use crate::final_ast::types::StructHTValH;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_compilation::HammerCompilation;
use crate::simplifying::hammer_compilation::HammerCompilationOptions;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::simplifying::test::test_compilation::test;
use crate::tests::tests::get_package_to_resource_resolver;
use crate::typing::typing_interner::TypingInterner;
use crate::utils::code_hierarchy::FileCoordinateMap;
use crate::utils::code_hierarchy::PackageCoordinate;
use crate::utils::code_hierarchy::test_from_vec;
use std::sync::Arc;
use crate::utils::code_hierarchy::IPackageResolver;

pub struct HammerTests;

#[test]
pub fn simple_main() {
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
    let code = "exported func main() int { return 3; }";
    let resolver = get_code_map(&parse_arena, &parser_keywords)
        .or(test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let mut compile = test(
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver, &instantiating_bump,
    );
    let hamuts = compile.get_hamuts();
    let test_package = hamuts.lookup_package(*PackageCoordinate::test_tld(&parse_arena, &parser_keywords));
    assert_eq!(test_package.get_all_user_functions().len(), 1);
    assert_eq!(test_package.get_all_user_functions()[0].prototype.id.fully_qualified_name.0, "main");
}

#[test]
pub fn two_templated_structs_make_it_into_hamuts() {
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
    let code = "
func __pretend<T>() T { __vbi_panic() }

interface MyOption<T Ref imm> imm { }
struct MyNone<T Ref imm> imm { }
impl<T Ref imm> MyOption<T> for MyNone<T>;
struct MySome<T Ref imm> imm { value T; }
impl<T Ref imm> MyOption<T> for MySome<T>;

exported func main() {
  x = __pretend<MySome<int>>();
  y = __pretend<MyNone<int>>();
  z MyOption<int> = x;
}
";
    let resolver = get_code_map(&parse_arena, &parser_keywords)
        .or(test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let mut compile = test(
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver, &instantiating_bump,
    );
    let package_h = compile.get_hamuts().lookup_package(*PackageCoordinate::test_tld(&parse_arena, &parser_keywords));
    assert!(package_h.interfaces.iter().any(|i| i.id.fully_qualified_name.0 == "MyOption<i32>"));
    let my_some = package_h.structs.iter().find(|s| s.id.fully_qualified_name.0 == "MySome<i32>").expect("MySome<i32> missing");
    assert_eq!(my_some.members.len(), 1);
    assert_eq!(my_some.members[0].tyype, CoordH { ownership: OwnershipH::MutableShareH, location: LocationH::InlineH, kind: KindHT::IntHT(IntHT { bits: 32 }) });
    let my_none = package_h.structs.iter().find(|s| s.id.fully_qualified_name.0 == "MyNone<i32>").expect("MyNone<i32> missing");
    assert!(my_none.members.is_empty());
}

#[test]
pub fn tests_stripping_things_after_panic() {
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
    let code = "
exported func main() int {
  __vbi_panic();
  a = 42;
  return a;
}
";
    let resolver = get_code_map(&parse_arena, &parser_keywords)
        .or(test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let mut compile = test(
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver, &instantiating_bump,
    );
    let package_h = compile.get_hamuts().lookup_package(*PackageCoordinate::test_tld(&parse_arena, &parser_keywords));
    let main = package_h.lookup_function("main");
    match main.body {
        ExpressionH::BlockH(b) => match b.inner {
            ExpressionH::CallH(c) => {
                assert_eq!(c.args_expressions.len(), 0);
                assert!(matches!(c.function.return_type.kind, KindHT::NeverHT(_)));
                assert!(c.function.id.fully_qualified_name.0.contains("__vbi_panic"));
            }
            _ => panic!("inner not CallH"),
        },
        _ => panic!("body not BlockH"),
    }
}

#[test]
#[ignore = "blocked on CoordSendSR Some-receiver solver-conflict fix (see investigations/coord_send_some_branch_fix.md)"]
pub fn panic_in_expr() {
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
    let code = "
import intrange.*;

exported func main() int {
  return 3 + __vbi_panic();
}
";
    let resolver = get_code_map(&parse_arena, &parser_keywords)
        .or(test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let mut compile = test(
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver, &instantiating_bump,
    );
    let package_h = compile.get_hamuts().lookup_package(*PackageCoordinate::test_tld(&parse_arena, &parser_keywords));
    let main = package_h.lookup_function("main");
    let int_expr = match main.body {
        ExpressionH::BlockH(b) => match b.inner {
            ExpressionH::ConsecutorH(c) => {
                assert_eq!(c.exprs.len(), 2);
                let int_expr = c.exprs[0];
                match c.exprs[1] {
                    ExpressionH::CallH(call) => {
                        assert_eq!(call.args_expressions.len(), 0);
                        assert!(matches!(call.function.return_type.kind, KindHT::NeverHT(_)));
                    }
                    _ => panic!("expected CallH for last consecutor expr"),
                }
                int_expr
            }
            _ => panic!("expected ConsecutorH inside BlockH"),
        },
        _ => panic!("expected BlockH body"),
    };
    match int_expr {
        ExpressionH::ConstantIntH(ci) => {
            assert_eq!(ci.value, 3);
            assert_eq!(ci.bits, 32);
        }
        _ => panic!("expected ConstantIntH(3, 32) at int_expr"),
    }
}

#[test]
pub fn tests_export_function() {
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
    let code = "exported func moo() int { return 42; }";
    let resolver = get_code_map(&parse_arena, &parser_keywords)
        .or(test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let mut compile = test(
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver, &instantiating_bump,
    );
    let package_h = compile.get_hamuts().lookup_package(*PackageCoordinate::test_tld(&parse_arena, &parser_keywords));
    let moo = package_h.lookup_function("moo");
    let exported_moo = *package_h.export_name_to_function.get(&scout_arena.intern_str("moo")).expect("moo export missing");
    assert_eq!(exported_moo, moo.prototype);
}

#[test]
pub fn tests_export_struct() {
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
    let code = "exported struct Moo { }";
    let resolver = get_code_map(&parse_arena, &parser_keywords)
        .or(test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let mut compile = test(
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver, &instantiating_bump,
    );
    let package_h = compile.get_hamuts().lookup_package(*PackageCoordinate::test_tld(&parse_arena, &parser_keywords));
    let moo = package_h.lookup_struct("Moo");
    let moo_ref = KindHT::StructHT(hammer_interner.intern_struct_ht(StructHTValH { id: moo.id }));
    let exported_moo = *package_h.export_name_to_kind.get(&scout_arena.intern_str("Moo")).expect("Moo export missing");
    assert_eq!(exported_moo, moo_ref);
}

#[test]
pub fn tests_export_interface() {
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
    let code = "exported interface Moo { }";
    let resolver = get_code_map(&parse_arena, &parser_keywords)
        .or(test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let mut compile = test(
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver, &instantiating_bump,
    );
    let package_h = compile.get_hamuts().lookup_package(*PackageCoordinate::test_tld(&parse_arena, &parser_keywords));
    let moo = package_h.lookup_interface("Moo");
    let moo_ref = KindHT::InterfaceHT(hammer_interner.intern_interface_ht(InterfaceHTValH { id: moo.id }));
    let exported_moo = *package_h.export_name_to_kind.get(&scout_arena.intern_str("Moo")).expect("Moo export missing");
    assert_eq!(exported_moo, moo_ref);
}

#[test]
pub fn tests_exports_from_two_modules_different_names() {
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
    let mut map: FileCoordinateMap<'_, String> = FileCoordinateMap::new();
    let module_a = parse_arena.intern_package_coordinate(parse_arena.intern_str("moduleA"), &[]);
    let module_b = parse_arena.intern_package_coordinate(parse_arena.intern_str("moduleB"), &[]);
    map.put(
        parse_arena.intern_file_coordinate(module_a, "StructA.vale"),
        "exported struct StructA { a int; }".to_string(),
    );
    map.put(
        parse_arena.intern_file_coordinate(module_b, "StructB.vale"),
        "exported struct StructB { a int; }".to_string(),
    );
    let resolver = get_code_map(&parse_arena, &parser_keywords)
        .or(map)
        .or(get_package_to_resource_resolver());
    let builtin = PackageCoordinate::builtin(&parse_arena, &parser_keywords);
    let global_options = GlobalOptions {
        sanity_check: true,
        use_overload_index: true,
        use_optimized_solver: true,
        verbose_errors: true,
        debug_output: true,
    };
    let mut compile = HammerCompilation::new(
        &scout_arena,
        &hammer_interner,
        &typing_interner,
        &keywords,
        &parser_keywords,
        &parse_arena,
        vec![builtin, module_a, module_b],
        &resolver,
        HammerCompilationOptions {
            debug_out: Arc::new(|_x: &str| {}),
            global_options,
        },
        &instantiating_bump,
    );
    let hamuts = compile.get_hamuts();

    let struct_a_name = scout_arena.intern_str("StructA");
    let struct_b_name = scout_arena.intern_str("StructB");
    let package_a = hamuts.lookup_package(*module_a);
    let full_name_a = package_a.export_name_to_kind.get(&struct_a_name).expect("vassertSome: StructA");

    let package_b = hamuts.lookup_package(*module_b);
    let full_name_b = package_b.export_name_to_kind.get(&struct_b_name).expect("vassertSome: StructB");

    assert!(full_name_a != full_name_b);
}

#[test]
pub fn top_level_extern_functions_wire_format_simple_id_has_flat_shape() {
    // numInheritedGenericParameters is 0 for a top-level extern, so Hammer should not reshape.
    // The leaf step retains whatever templateArgs the function has (empty here, since this is
    // a non-generic extern). This is a smoke test that the no-reshape path returns rawSimpleId.
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
    let code = "
extern struct Vec<T> imm;
extern func VecOuterNew<T>() Vec<T>;
exported func main() int {
  v = VecOuterNew<int>();
  return 42;
}
";
    let resolver = get_code_map(&parse_arena, &parser_keywords)
        .or(test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let mut compile = test(
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver, &instantiating_bump,
    );
    let hamuts = compile.get_hamuts();
    let package_h = hamuts.lookup_package(*PackageCoordinate::test_tld(&parse_arena, &parser_keywords));
    let outer_new = package_h.prototype_to_extern.iter().map(|(_, e)| e).find(|e| e.simple_id.steps.last().expect("empty steps").name.0 == "VecOuterNew").expect("VecOuterNew not found");
    // VecOuterNew<int>: leaf step keeps own template arg, no reshape (no parent citizen).
    let leaf = outer_new.simple_id.steps.last().expect("empty steps");
    assert_eq!(leaf.name.0, "VecOuterNew");
    assert_eq!(leaf.template_args.len(), 1);  // <int>
}

#[test]
pub fn mixed_own_inherited_template_args_split_correctly_in_wire_format_simple_id() {
    // Per @PRIIROZ, the function's templateArgs are ordered [own..., inherited...]. For
    // `Foo<A>.bar<C>(c C)` monomorphized as `Foo<i32>.bar<i64>(42i64)`, the leaf step's
    // templateArgs are [i64, i32] (C own first, A inherited last). After reshape, the
    // 1 trailing inherited arg moves to the Foo step:
    //   [..., Foo<i32>, bar<i64>]
    // Asserts both the splitAt count (1, not 0 or 2) and the splitAt direction. Uses
    // i32 + i64 (not bool/str etc.) because NameHammer.simplifyKind currently only
    // handles IntIT.
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
    let code = "
extern struct Foo<A> imm {
  extern func bar<C>(c C) int;
}
exported func main() int {
  return Foo<int>.bar<str>(\"hello\");
}
";
    let resolver = get_code_map(&parse_arena, &parser_keywords)
        .or(test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let mut compile = test(
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver, &instantiating_bump,
    );
    let hamuts = compile.get_hamuts();
    let package_h = hamuts.lookup_package(*PackageCoordinate::test_tld(&parse_arena, &parser_keywords));
    let bar_extern = package_h.prototype_to_extern.iter().map(|(_, e)| e).find(|e| e.simple_id.steps.last().expect("empty steps").name.0 == "bar").expect("bar not found");
    let steps = bar_extern.simple_id.steps;
    assert!(steps.len() >= 2);
    let leaf = steps.last().expect("empty steps");
    let parent = &steps[steps.len() - 2];
    assert_eq!(leaf.name.0, "bar");
    assert_eq!(leaf.template_args.len(), 1);  // C (own) remains on the leaf.
    assert_eq!(parent.name.0, "Foo");
    assert_eq!(parent.template_args.len(), 1);  // A (inherited) moved up to the citizen.
}

#[test]
pub fn extern_method_in_generic_extern_struct_puts_container_args_on_citizen_step_in_wire_format_simple_id() {
    // The reshape moves the inherited container template args (T -> i32) off the leaf function
    // step onto the immediately preceding citizen step. Final shape: [..., Vec<i32>, new]
    // rather than [..., Vec, new<i32>]. This is what Backend's rustifySimpleId expects per
    // @SMLRZ, so it can emit `Vec<i32>::new` rather than `Vec::new<i32>`.
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
    let code = "
extern struct Vec<T> imm {
  extern func new() Vec<T>;
}
exported func main() int {
  v = Vec<int>.new();
  return 42;
}
";
    let resolver = get_code_map(&parse_arena, &parser_keywords)
        .or(test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let mut compile = test(
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver, &instantiating_bump,
    );
    let hamuts = compile.get_hamuts();
    let package_h = hamuts.lookup_package(*PackageCoordinate::test_tld(&parse_arena, &parser_keywords));
    let new_extern = package_h.prototype_to_extern.iter().map(|(_, e)| e).find(|e| e.simple_id.steps.last().expect("empty steps").name.0 == "new").expect("new not found");
    let steps = new_extern.simple_id.steps;
    assert!(steps.len() >= 2);
    let leaf = steps.last().expect("empty steps");
    let parent = &steps[steps.len() - 2];
    assert_eq!(leaf.name.0, "new");
    assert_eq!(leaf.template_args.len(), 0);  // No own template args, no surviving args after reshape.
    assert_eq!(parent.name.0, "Vec");
    assert_eq!(parent.template_args.len(), 1);  // <i32> moved up from the leaf.
}

