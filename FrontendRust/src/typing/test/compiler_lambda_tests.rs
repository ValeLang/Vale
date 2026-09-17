use bumpalo::Bump;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::typing::ast::ast::ParameterT;
use crate::typing::ast::expressions::FunctionCallTE;
use crate::typing::names::names::IVarNameT;
use crate::typing::test::compiler_test_compilation::compiler_test_compilation;
use crate::typing::types::types::{CoordT, IntT, IRegionT, KindT, OwnershipT, RegionT};
use crate::utils::code_hierarchy::{self, IPackageResolver, PackageCoordinate};
use std::collections::HashMap;
use crate::typing::test::traverse::NodeRefT;
use crate::typing::names::names::CodeVarNameT;
use crate::interner::StrI;
use crate::typing::typing_interner::TypingInterner;
use crate::builtins::builtins::get_embedded_modulized_code_map;
use crate::collect_only_tnode;
use crate::tests::tests::get_package_to_resource_resolver;

pub struct CompilerLambdaTests;

impl CompilerLambdaTests {}

fn read_code_from_resource(resource_filename: &str) -> String {
    panic!("Unimplemented: read_code_from_resource");
}

#[test]
fn simple_lambda() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "\nexported func main() int { return { 7 }(); }\n";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let expected = CoordT { ownership: OwnershipT::Share, region: RegionT { region: IRegionT::Default }, kind: KindT::Int(IntT { bits: 32 }) };
    assert_eq!(coutputs.lookup_lambda_in("main").header.return_type, expected);
    assert_eq!(coutputs.lookup_function_by_str("main").header.return_type, expected);
}

#[test]
fn lambda_with_one_magic_arg() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "\nexported func main() int { return {_}(3); }\n";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let lambda = coutputs.lookup_lambda_in("main");
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(lambda),
        NodeRefT::Parameter(
            ParameterT {
                virtuality: None,
                tyype: CoordT {
                    ownership: OwnershipT::Share,
                    kind: KindT::Int(IntT { bits: 32 }),
                    ..
                },
                ..
            }
        ) => Some(())
    );
    assert_eq!(
        coutputs.lookup_lambda_in("main").header.return_type,
        CoordT { ownership: OwnershipT::Share, region: RegionT { region: IRegionT::Default }, kind: KindT::Int(IntT { bits: 32 }) },
    );
}

#[test]
fn lambda_is_reused() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
exported func main() {
  lam = x => x;
  lam(4);
  lam(7);
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let lambdas = coutputs.lookup_lambdas_in("main");
    assert_eq!(lambdas.len(), 1);
}

#[test]
fn lambda_called_with_different_types() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
exported func main() {
  lam = x => x;
  lam(4);
  lam(true);
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let lambdas = coutputs.lookup_lambdas_in("main");
    assert_eq!(lambdas.len(), 2);
}

#[test]
fn curried_lambda() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r#"
exported func main() {
  lam = x => y => 7;
  lam(true)(4);
  lam(true)("hello");
}
"#;
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let lambdas = coutputs.lookup_lambdas_in("main");
    assert_eq!(lambdas.len(), 3);
}

#[test]
fn lambda_with_a_type_specified_param() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
import v.builtins.arith.*;
exported func main() int {
  return (a int) => {+(a,a)}(3);
}
";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let lambda = coutputs.lookup_lambda_in("main");
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(lambda),
        NodeRefT::Parameter(
            ParameterT {
                name: IVarNameT::CodeVar(CodeVarNameT { name: StrI("a"), .. }),
                virtuality: None,
                tyype: CoordT {
                    ownership: OwnershipT::Share,
                    kind: KindT::Int(IntT { bits: 32 }),
                    ..
                },
                ..
            }
        ) => Some(())
    );
    assert!(coutputs.name_is_lambda_in(lambda.header.id, "main"));
    let main = coutputs.lookup_function_by_str("main");
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(FunctionCallTE { callable, .. }) => {
            assert!(coutputs.name_is_lambda_in(callable.id, "main"));
            Some(())
        }
    );
}

#[test]
fn tests_lambda_and_concept_function() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r#"
import v.builtins.print.*;
import v.builtins.drop.*;
import v.builtins.str.*;

func moo<X, F>(x X, f F)
where func(&F, &X)void, func drop(X)void, func drop(F)void {
  f(&x);
}
exported func main() {
  moo("hello", { print(_); });
}
"#;
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    compile.expect_compiler_outputs();
}

#[test]
fn lambda_inside_different_function_with_same_name() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r#"
import printutils.*;

func helperFunc(x int) {
  { print(x); }();
}
func helperFunc(x str) {
  { print(x); }();
}
exported func main() {
  helperFunc(4);
  helperFunc("bork");
}
"#;
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    compile.expect_compiler_outputs();
}

#[test]
fn lambda_inside_template() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r#"
import v.builtins.drop.*;
import printutils.*;

func helperFunc<T>(x T)
where func print(&T)void, func drop(T)void
{
  { print(x); }();
}
exported func main() {
  helperFunc(4);
  helperFunc("bork");
}
"#;
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    compile.expect_compiler_outputs();
}

#[test]
fn curried_lambda_inside_template() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r#"
import v.builtins.drop.*;
func helper<T>(x &T) &T {
  lam = a => b => x;
  return lam(true)(7);
}
exported func main() {
  helper(4);
  helper("bork");
}
"#;
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let lambdas = coutputs.lookup_lambdas_in("helper");
    assert_eq!(lambdas.len(), 2);
}

