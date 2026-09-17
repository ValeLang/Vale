use bumpalo::Bump;
use crate::compile_options::GlobalOptions;
use crate::higher_typing::HigherTypingCompilation;
use crate::higher_typing::astronomer_error_reporter::ICompileErrorA;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::keywords::Keywords;
use crate::postparsing::itemplatatype::{CoordTemplataType, ITemplataType, PackTemplataType};
use crate::postparsing::names::{CodeRuneS, IRuneValS};
use crate::utils::code_hierarchy::{self, IPackageResolver, PackageCoordinate};
use std::collections::HashMap;
// TODO: rename

fn compile_program_for_error<'s, 'ctx, 'p>(
    compilation: &mut HigherTypingCompilation<'s, 'ctx, 'p>,
) -> ICompileErrorA<'s>
{
    match compilation.get_astrouts() {
        Ok(result) => panic!("Expected error, but actually parsed invalid program:\n{:?}", result),
        Err(err) => err,
    }
}

fn setup_test<'s, 'ctx, 'p>(
    scout_arena: &'ctx ScoutArena<'s>,
    keywords: &'ctx Keywords<'s>,
    parser_keywords: &'ctx Keywords<'p>,
    parse_arena: &'ctx ParseArena<'p>,
    resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>>,
) -> HigherTypingCompilation<'s, 'ctx, 'p> {
    let options = GlobalOptions {
        sanity_check: true,
        use_overload_index: true,
        use_optimized_solver: true,
        verbose_errors: false,
        debug_output: false,
    };
    let test_module = parse_arena.intern_str("test");
    let test_tld_ref = parse_arena.intern_package_coordinate(test_module, &[]);
    HigherTypingCompilation::new(
        scout_arena,
        keywords,
        parser_keywords,
        parse_arena,
        vec![test_tld_ref],
        resolver,
        options,
    )
}

#[test]
fn type_simple_main_function() {
    let scout_bump = Bump::new();
    let parser_arena = Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let scout_keywords = Keywords::new_for_scout(&scout_arena);
    let parse_arena = ParseArena::new(&parser_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec!["exported func main() {\n}\n".to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let mut compilation = setup_test(&scout_arena, &scout_keywords, &parser_keywords, &parse_arena, &resolver);
    let _astrouts = compilation.expect_astrouts();
}

#[test]
fn type_simple_generic_function() {
    let scout_bump = Bump::new();
    let parser_arena = Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let scout_keywords = Keywords::new_for_scout(&scout_arena);
    let parse_arena = ParseArena::new(&parser_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec!["exported func moo<T>() where T Ref {\n}\n".to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let mut compilation = setup_test(&scout_arena, &scout_keywords, &parser_keywords, &parse_arena, &resolver);
    let _astrouts = compilation.expect_astrouts();
}

#[test]
fn infer_coord_type_from_parameters() {
    let scout_bump = Bump::new();
    let parser_arena = Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let scout_keywords = Keywords::new_for_scout(&scout_arena);
    let parse_arena = ParseArena::new(&parser_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec!["exported func moo<T>(x T) {\n}\n".to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let mut compilation = setup_test(&scout_arena, &scout_keywords, &parser_keywords, &parse_arena, &resolver);
    let astrouts = compilation.expect_astrouts();
    let test_module_s = scout_arena.intern_str("test");
    let test_tld = *scout_arena.intern_package_coordinate(test_module_s, &[]);
    let program = astrouts.get(&test_tld).unwrap();
    let main = program.lookup_function_by_str("moo");
    assert_eq!(
        *main.rune_to_type.get(&scout_arena.intern_rune(
            IRuneValS::CodeRune(CodeRuneS { name: scout_keywords.t })
        )).unwrap(),
        ITemplataType::CoordTemplataType(CoordTemplataType {})
    );
}

#[test]
fn type_simple_struct() {
    let scout_bump = Bump::new();
    let parser_arena = Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let scout_keywords = Keywords::new_for_scout(&scout_arena);
    let parse_arena = ParseArena::new(&parser_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec!["struct Moo {\n}\n".to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let mut compilation = setup_test(&scout_arena, &scout_keywords, &parser_keywords, &parse_arena, &resolver);
    let _astrouts = compilation.expect_astrouts();
}

#[test]
fn type_simple_generic_struct() {
    let scout_bump = Bump::new();
    let parser_arena = Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let scout_keywords = Keywords::new_for_scout(&scout_arena);
    let parse_arena = ParseArena::new(&parser_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![r"
struct Moo<T> {
  bork T;
}
".to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let mut compilation = setup_test(&scout_arena, &scout_keywords, &parser_keywords, &parse_arena, &resolver);
    let _astrouts = compilation.expect_astrouts();
}

#[test]
fn template_call_recursively_evaluate() {
    let scout_bump = Bump::new();
    let parser_arena = Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let scout_keywords = Keywords::new_for_scout(&scout_arena);
    let parse_arena = ParseArena::new(&parser_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![r"
struct Moo<T> {
  bork T;
}
struct Bork<T> {
  x Moo<T>;
}
".to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let mut compilation = setup_test(&scout_arena, &scout_keywords, &parser_keywords, &parse_arena, &resolver);
    let astrouts = compilation.expect_astrouts();
    let test_module_s = scout_arena.intern_str("test");
    let test_tld = *scout_arena.intern_package_coordinate(test_module_s, &[]);
    let program = astrouts.get(&test_tld).unwrap();
    let main = program.lookup_struct_by_str("Bork");
    assert_eq!(
        *main.header_rune_to_type.get(&scout_arena.intern_rune(
            IRuneValS::CodeRune(CodeRuneS { name: scout_keywords.t })
        )).unwrap(),
        ITemplataType::CoordTemplataType(CoordTemplataType {})
    );
}

#[test]
fn type_simple_interface() {
    let scout_bump = Bump::new();
    let parser_arena = Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let scout_keywords = Keywords::new_for_scout(&scout_arena);
    let parse_arena = ParseArena::new(&parser_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec!["interface Moo {\n}\n".to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let mut compilation = setup_test(&scout_arena, &scout_keywords, &parser_keywords, &parse_arena, &resolver);
    let _astrouts = compilation.expect_astrouts();
}

#[test]
fn type_simple_generic_interface() {
    let scout_bump = Bump::new();
    let parser_arena = Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let scout_keywords = Keywords::new_for_scout(&scout_arena);
    let parse_arena = ParseArena::new(&parser_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec!["interface Moo<T> where T Ref {\n}\n".to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let mut compilation = setup_test(&scout_arena, &scout_keywords, &parser_keywords, &parse_arena, &resolver);
    let _astrouts = compilation.expect_astrouts();
}

#[test]
fn type_simple_generic_interface_method() {
    let scout_bump = Bump::new();
    let parser_arena = Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let scout_keywords = Keywords::new_for_scout(&scout_arena);
    let parse_arena = ParseArena::new(&parser_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![r"
interface Moo<T> where T Ref {
  func bork(virtual self &Moo<T>) int;
}
".to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let mut compilation = setup_test(&scout_arena, &scout_keywords, &parser_keywords, &parse_arena, &resolver);
    let _astrouts = compilation.expect_astrouts();
}

#[test]
fn infer_generic_type_through_param_type_template_call() {
    let scout_bump = Bump::new();
    let parser_arena = Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let scout_keywords = Keywords::new_for_scout(&scout_arena);
    let parse_arena = ParseArena::new(&parser_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![r"
struct List<T> {
  moo T;
}
exported func moo<T>(x List<T>) {
}
".to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let mut compilation = setup_test(&scout_arena, &scout_keywords, &parser_keywords, &parse_arena, &resolver);
    let astrouts = compilation.expect_astrouts();
    let test_module_s = scout_arena.intern_str("test");
    let test_tld = *scout_arena.intern_package_coordinate(test_module_s, &[]);
    let program = astrouts.get(&test_tld).unwrap();
    let main = program.lookup_function_by_str("moo");
    assert_eq!(
        *main.rune_to_type.get(&scout_arena.intern_rune(
            IRuneValS::CodeRune(CodeRuneS { name: scout_keywords.t })
        )).unwrap(),
        ITemplataType::CoordTemplataType(CoordTemplataType {})
    );
}

#[test]
fn test_evaluate_pack() {
    let scout_bump = Bump::new();
    let parser_arena = Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let scout_keywords = Keywords::new_for_scout(&scout_arena);
    let parse_arena = ParseArena::new(&parser_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![r"
func moo<T RefList>()
where T = Refs(int, bool)
{
}
".to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let mut compilation = setup_test(&scout_arena, &scout_keywords, &parser_keywords, &parse_arena, &resolver);
    let astrouts = compilation.expect_astrouts();
    let test_module_s = scout_arena.intern_str("test");
    let test_tld = *scout_arena.intern_package_coordinate(test_module_s, &[]);
    let program = astrouts.get(&test_tld).unwrap();
    let main = program.lookup_function_by_str("moo");
    assert_eq!(
        *main.rune_to_type.get(&scout_arena.intern_rune(
            IRuneValS::CodeRune(CodeRuneS { name: scout_keywords.t })
        )).unwrap(),
        ITemplataType::PackTemplataType(PackTemplataType {
            element_type: &*scout_arena.alloc(ITemplataType::CoordTemplataType(CoordTemplataType {}))
        })
    );
}

#[test]
fn test_infer_pack_from_result() {
    let scout_bump = Bump::new();
    let parser_arena = Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let scout_keywords = Keywords::new_for_scout(&scout_arena);
    let parse_arena = ParseArena::new(&parser_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![r"
func moo<T>()
where func moo(T, bool)str
{
}
".to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let mut compilation = setup_test(&scout_arena, &scout_keywords, &parser_keywords, &parse_arena, &resolver);
    let astrouts = compilation.expect_astrouts();
    let test_module_s = scout_arena.intern_str("test");
    let test_tld = *scout_arena.intern_package_coordinate(test_module_s, &[]);
    let program = astrouts.get(&test_tld).unwrap();
    let main = program.lookup_function_by_str("moo");
    assert_eq!(
        *main.rune_to_type.get(&scout_arena.intern_rune(
            IRuneValS::CodeRune(CodeRuneS { name: scout_keywords.t })
        )).unwrap(),
        ITemplataType::CoordTemplataType(CoordTemplataType {})
    );
}

#[test]
fn test_infer_pack_from_empty_result() {
    let scout_bump = Bump::new();
    let parser_arena = Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let scout_keywords = Keywords::new_for_scout(&scout_arena);
    let parse_arena = ParseArena::new(&parser_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![r"
func moo<P RefList>()
where P = Refs(), Prot[P, str]
{
}
".to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let mut compilation = setup_test(&scout_arena, &scout_keywords, &parser_keywords, &parse_arena, &resolver);
    let astrouts = compilation.expect_astrouts();
    let test_module_s = scout_arena.intern_str("test");
    let test_tld = *scout_arena.intern_package_coordinate(test_module_s, &[]);
    let program = astrouts.get(&test_tld).unwrap();
    let main = program.lookup_function_by_str("moo");
    assert_eq!(
        *main.rune_to_type.get(&scout_arena.intern_rune(
            IRuneValS::CodeRune(CodeRuneS { name: scout_arena.intern_str("P") })
        )).unwrap(),
        ITemplataType::PackTemplataType(PackTemplataType {
            element_type: &*scout_arena.alloc(ITemplataType::CoordTemplataType(CoordTemplataType {}))
        })
    );
}

// NOVEL CODE
#[test]
fn type_simple_impl() {
    
    let scout_bump = Bump::new();
    let parser_arena = Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let scout_keywords = Keywords::new_for_scout(&scout_arena);
    let parse_arena = ParseArena::new(&parser_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![r"
interface IMoo {
}
struct Moo {
}
impl IMoo for Moo;
".to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let mut compilation = setup_test(&scout_arena, &scout_keywords, &parser_keywords, &parse_arena, &resolver);
    let _astrouts = compilation.expect_astrouts();
}

