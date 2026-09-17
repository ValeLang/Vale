use bumpalo::Bump;
use crate::interner::StrI;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::postparsing::names::{CodeNameS, IImpreciseNameS};
use crate::scout_arena::ScoutArena;
use crate::typing::ast::expressions::RestackifyTE;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::typing::env::function_environment_t::{ILocalVariableT, ReferenceLocalVariableT};
use crate::typing::names::names::IVarNameT;
use crate::typing::overload_resolver::FindFunctionFailure;
use crate::typing::test::compiler_test_compilation::compiler_test_compilation;
use crate::typing::test::humanize_helper::{assert_humanized_eq, humanize_compile_error};
use crate::utils::code_hierarchy::{self, IPackageResolver, PackageCoordinate};
use std::collections::HashMap;
use crate::typing::test::traverse::NodeRefT;
use crate::typing::names::names::CodeVarNameT;
use crate::typing::typing_interner::TypingInterner;
use crate::builtins::builtins::get_embedded_modulized_code_map;
use crate::collect_only_tnode;
use std::fs::read_to_string;
use std::path::PathBuf;


fn read_code_from_resource(resource_filename: &str) -> String {
    let path = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("src/tests")
        .join(resource_filename);
    read_to_string(&path).expect("readCodeFromResource: file not found")
}

#[test]
fn parenthesized_method_syntax_will_move_instead_of_borrow() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

struct Bork { a int; }
func doSomething(bork Bork) int {
  return bork.a;
}
func main() int {
  bork = Bork(42);
  return (bork).doSomething();
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn calling_a_method_on_a_returned_own_ref_will_supply_owning_arg() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

struct Bork { a int; }
func doSomething(bork Bork) int {
  return bork.a;
}
func main() int {
  return Bork(42).doSomething();
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn explicit_borrow_method_call() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

struct Bork { a int; }
func doSomething(bork &Bork) int {
  return bork.a;
}
func main() int {
  return Bork(42)&.doSomething();
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn calling_a_method_on_a_local_will_supply_borrow_ref() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

struct Bork { a int; }
func doSomething(bork &Bork) int {
  return bork.a;
}
func main() int {
  bork = Bork(42);
  return bork.doSomething();
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn calling_a_method_on_a_member_will_supply_borrow_ref() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

struct Zork { bork Bork; }
struct Bork { a int; }
func doSomething(bork &Bork) int {
  return bork.a;
}
func main() int {
  zork = Zork(Bork(42));
  return zork.bork.doSomething();
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn no_derived_or_custom_drop_gives_error() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"


#!DeriveStructDrop
struct Muta { }

exported func main() {
  Muta();
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err()
        .unwrap_or_else(|| panic!("expected Err(CouldntFindFunctionToCallT), got Ok"));
    match &err {
        ICompileErrorT::CouldntFindFunctionToCallT { fff: FindFunctionFailure { name: IImpreciseNameS::CodeName(CodeNameS { name: StrI("drop") }), .. }, .. } => {}
        _ => panic!("expected CouldntFindFunctionToCallT with FindFunctionFailure(CodeNameS(\"drop\"))"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:7:1:
exported func main() {
At test:0.vale:8:3:
  Muta();
Couldn't find a suitable function drop(Muta). No function with that name exists.

"#,
    );
}

#[test]
fn opt_with_undroppable_contents() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
#!DeriveInterfaceDrop
sealed interface Opt<T> where T Ref { }

#!DeriveStructDrop
struct Some<T> where T Ref { value T; }

impl<T> Opt<T> for Some<T>;

abstract func drop<T>(virtual opt Opt<T>)
where func drop(T)void;

func drop<T>(opt Some<T>)
where func drop(T)void
{
  [x] = opt;
}

abstract func get<T>(virtual opt Opt<T>) T;
func get<T>(opt Some<T>) T {
  [value] = opt;
  return value;
}

#!DeriveStructDrop
struct Spaceship { }

exported func main() {
  s Opt<Spaceship> = Some<Spaceship>(Spaceship());
  // Drops the ship manually
  [ ] = (s).get();
}

";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn opt_with_undroppable_mutable_ref_contents() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
import v.builtins.drop.*;

#!DeriveInterfaceDrop
sealed interface Opt<T Ref> { }

#!DeriveStructDrop
struct Some<T Ref> { value T; }

impl<T> Opt<T> for Some<T>;

abstract func drop<T>(virtual opt Opt<T>)
where func drop(T)void;

func drop<T>(opt Some<T>)
where func drop(T)void
{
  [x] = opt;
}

#!DeriveStructDrop
struct Spaceship { }

struct ContainerWithDerivedDrop {
  maybeThing Opt<&Spaceship>;
}

exported func main() {
  ship = Spaceship();
  c = ContainerWithDerivedDrop(Some<&Spaceship>(&ship));
  [ ] = ship;
}

";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn restackify() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = read_code_from_resource("programs/restackify.vale");
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Restackify(RestackifyTE {
            variable: ILocalVariableT::Reference(ReferenceLocalVariableT {
                name: IVarNameT::CodeVar(CodeVarNameT { name: StrI("ship"), .. }),
                ..
            }),
            ..
        }) => Some(())
    );
}

#[test]
fn loop_restackify() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = read_code_from_resource("programs/loop_restackify.vale");
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Restackify(RestackifyTE {
            variable: ILocalVariableT::Reference(ReferenceLocalVariableT {
                name: IVarNameT::CodeVar(CodeVarNameT { name: StrI("ship"), .. }),
                ..
            }),
            ..
        }) => Some(())
    );
}

#[test]
fn destructure_restackify() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = read_code_from_resource("programs/destructure_restackify.vale");
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Restackify(RestackifyTE {
            variable: ILocalVariableT::Reference(ReferenceLocalVariableT {
                name: IVarNameT::CodeVar(CodeVarNameT { name: StrI("ship"), .. }),
                ..
            }),
            ..
        }) => Some(())
    );
}

