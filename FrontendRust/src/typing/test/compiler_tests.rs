
use super::compiler_test_compilation::compiler_test_compilation;
use bumpalo::Bump;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::utils::code_hierarchy::{self, IPackageResolver, PackageCoordinate};
use std::collections::HashMap;
use crate::typing::types::types::{CoordT, IntT, IRegionT, KindT, OwnershipT, RegionT};
use crate::typing::ast::ast::ParameterT;
use crate::typing::ast::expressions::{LetNormalTE, LocalLookupTE};
use crate::typing::env::function_environment_t::{ILocalVariableT, ReferenceLocalVariableT};
use crate::typing::names::names::{INameT, IVarNameT};
use crate::typing::types::types::{MutabilityT, NeverT};
use crate::typing::templata::templata::{ITemplataT, KindTemplataT, MutabilityTemplataT};
use crate::interner::StrI;
use crate::parsing::tests::utils::expect_1;
use crate::postparsing::names::{CodeNameS, CodeRuneS, FunctionNameS, IFunctionDeclarationNameS, IImpreciseNameS, IImpreciseNameValS, INameS, IRuneValS, TopLevelStructDeclarationNameS};
use crate::solver::solver::{FailedSolve, ISolverError, RuleError, Step};
use crate::typing::ast::ast::{KindExportT, SignatureValT};
use crate::typing::compiler_error_humanizer::humanize;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::typing::infer::compiler_solver::ITypingPassSolverError;
use crate::typing::names::names::{CodeVarNameT, ExportNameT, ExportTemplateNameT, FunctionNameValT, FunctionTemplateNameT, IdT, IdValT, IStructTemplateNameT, InterfaceNameValT, InterfaceTemplateNameT, StructNameT, StructNameValT, StructTemplateNameT};
use crate::typing::overload_resolver::FindFunctionFailure;
use crate::typing::templata::templata_utils::unapply_simple_name;
use crate::typing::types::types::{BoolT, InterfaceTTValT, StructTT, StructTTValT};
use crate::typing::typing_interner::TypingInterner;
use crate::utils::code_hierarchy::FileCoordinateMap;
use crate::utils::range::{CodeLocationS, RangeS};
use crate::utils::source_code_utils::{humanize_pos_code_map, line_containing, line_range_containing, lines_between};
use std::collections::HashSet;
use crate::typing::test::humanize_helper::{assert_humanized_eq, humanize_compile_error};
use crate::typing::test::traverse::NodeRefT;
use crate::typing::ast::expressions::ConstantIntTE;
use crate::typing::ast::expressions::FunctionCallTE;
use crate::typing::ast::expressions::ReferenceExpressionTE;
use crate::typing::ast::ast::PrototypeT;
use crate::typing::names::names::FunctionNameT;
use crate::typing::ast::citizens::IStructMemberT;
use crate::typing::ast::citizens::NormalStructMemberT;
use crate::typing::types::types::VariabilityT;
use crate::typing::ast::citizens::IMemberTypeT;
use crate::typing::ast::citizens::ReferenceMemberTypeT;
use crate::typing::ast::expressions::ReferenceMemberLookupTE;
use crate::typing::templata::templata::CoordTemplataT;
use crate::typing::types::types::KindPlaceholderT;
use crate::typing::names::names::KindPlaceholderNameT;
use crate::typing::names::names::KindPlaceholderTemplateNameT;
use crate::postparsing::names::IRuneS;
use crate::typing::ast::expressions::UpcastTE;
use crate::typing::names::names::InterfaceNameT;
use crate::typing::types::types::ISuperKindTT;
use crate::typing::types::types::InterfaceTT;
use crate::typing::ast::expressions::SoftLoadTE;
use crate::typing::ast::expressions::AddressExpressionTE;
use crate::typing::ast::expressions::LetAndLendTE;
use crate::typing::ast::citizens::StructDefinitionT;
use crate::typing::ast::ast::FunctionHeaderT;
use crate::builtins::builtins::get_embedded_modulized_code_map;
use crate::collect_only_tnode;
use crate::collect_where_tnode;
use crate::tests::tests::get_package_to_resource_resolver;
use crate::tests::tests::load_expected;
use std::iter::empty;
use std::marker::PhantomData;
pub struct CompilerTests {}
impl CompilerTests {}

fn read_code_from_resource(resource_filename: &str) -> String {
    panic!("Unimplemented: read_code_from_resource");
}

#[test]
fn simple_program_returning_an_int_explicit() {
    // We had a bug once looking up "int" in the environment, hence this test.
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "func main() int { return 3; }";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    assert!(main.header.return_type.kind == KindT::Int(IntT { bits: 32 }));
}

#[test]
fn hardcoding_negative_numbers() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "exported func main() int { return -3; }";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::ConstantInt(
            ConstantIntTE {
                value: ITemplataT::Integer(-3),
                ..
            }
        ) => Some(())
    );
}

#[test]
fn simple_local() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
exported func main() int {
  a = 42;
  return a;
}";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    assert!(main.header.return_type.kind == KindT::Int(IntT { bits: 32 }));
}

#[test]
fn tests_panic_return_type() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
import v.builtins.panic.*;
exported func main() int {
  x = { __vbi_panic() }();
}";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::LetNormal(LetNormalTE {
            variable: ILocalVariableT::Reference(ReferenceLocalVariableT {
                coord: CoordT { ownership: OwnershipT::Share, kind: KindT::Never(NeverT { from_break: false }), .. },
                ..
            }),
            ..
        }) => Some(())
    );
}

#[test]
fn taking_an_argument_and_returning_it() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "func main(a int) int { return a; }";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");

    let param: &ParameterT = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Parameter(p) => Some(p)
    );
    assert!(param.tyype == CoordT { ownership: OwnershipT::Share, region: RegionT { region: IRegionT::Default }, kind: KindT::Int(IntT { bits: 32 }) });

    let lookup: &LocalLookupTE = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::LocalLookup(l) => Some(l)
    );
    match lookup.local_variable.name() {
        IVarNameT::CodeVar(c) => assert!(c.name.as_str() == "a"),
        _ => panic!("Expected CodeVarNameT"),
    }
    match lookup.local_variable.coord() {
        CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT { bits: 32 }), .. } => {}
        other => panic!("Expected CoordT(Share, _, Int(32)), got {:?}", other),
    }
}

#[test]
fn tests_adding_two_numbers() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "import v.builtins.arith.*;\nexported func main() int { return +(2, 3); }";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");

    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::ConstantInt(
            ConstantIntTE {
                value: ITemplataT::Integer(2),
                ..
            }
        ) => Some(())
    );

    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::ConstantInt(
            ConstantIntTE {
                value: ITemplataT::Integer(3),
                ..
            }
        ) => Some(())
    );

    let func_call: &FunctionCallTE = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(call) => Some(call)
    );

    match func_call.callable.id.local_name {
        INameT::Function(fname) => {
            assert!(fname.template.human_name.as_str() == "+");
        }
        _ => panic!("Expected function name for + operator"),
    }

    assert_eq!(func_call.args.len(), 2);
    match (&func_call.args[0], &func_call.args[1]) {
        (
            ReferenceExpressionTE::ConstantInt(c1),
            ReferenceExpressionTE::ConstantInt(c2)
        ) => {
            match (&c1.value, &c2.value) {
                (
                    ITemplataT::Integer(2),
                    ITemplataT::Integer(3)
                ) => {}
                _ => panic!("Expected ConstantInt(2) and ConstantInt(3)"),
            }
        }
        _ => panic!("Expected function call with ConstantInt arguments"),
    }
}

#[test]
fn simple_struct_read() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
exported struct Moo { hp int; }
exported func main(moo &Moo) int {
  return moo.hp;
}";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
}

#[test]
fn make_array_and_dot_it() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r#"
exported func main() int {
  arr = [#]int(6, 60, 103);
  x = arr.2;
  [_, _, _] = arr;
  return x;
}
"#;
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn simple_struct_instantiate() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r#"
exported struct Moo { hp int; }
exported func main() Moo {
  return Moo(42);
}
"#;
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let _main = coutputs.lookup_function_by_str("main");
}

#[test]
fn call_destructor() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r#"
exported struct Moo { hp int; }
exported func main() int {
  return Moo(42).hp;
}
"#;
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    let _drop_call: &FunctionCallTE = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(call @ FunctionCallTE {
            callable: PrototypeT {
                id: IdT {
                    local_name: INameT::Function(FunctionNameT {
                        template: FunctionTemplateNameT { human_name: StrI("drop"), .. },
                        ..
                    }),
                    ..
                },
                ..
            },
            ..
        }) => Some(call)
    );
}

#[test]
fn custom_destructor() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "#!DeriveStructDrop\n",
        "exported struct Moo { hp int; }\n",
        "func drop(self ^Moo) {\n",
        "  [_] = self;\n",
        "}\n",
        "exported func main() int {\n",
        "  return Moo(42).hp;\n",
        "}\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(
            FunctionCallTE {
                callable: PrototypeT {
                    id: IdT {
                        local_name: INameT::Function(FunctionNameT {
                            template: FunctionTemplateNameT { human_name: StrI("drop"), .. },
                            ..
                        }),
                        ..
                    },
                    ..
                },
                ..
            }
        ) => Some(())
    );
}

#[test]
fn make_constraint_reference() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r#"
struct Moo {}
exported func main() void {
  m = Moo();
  b = &m;
}
"#;
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    let let_normal: &LetNormalTE = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::LetNormal(ln @ LetNormalTE {
            variable: ILocalVariableT::Reference(ReferenceLocalVariableT {
                name: IVarNameT::CodeVar(CodeVarNameT { name: StrI("b"), .. }),
                ..
            }),
            ..
        }) => Some(ln)
    );
    assert_eq!(let_normal.variable.coord().ownership, OwnershipT::Borrow);
}

#[test]
fn recursion() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "exported func main() int { return main(); }";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    // Make sure it inferred the param type and return type correctly
    assert!(coutputs.lookup_function_by_str("main").header.return_type == CoordT { ownership: OwnershipT::Share, region: RegionT { region: IRegionT::Default }, kind: KindT::Int(IntT { bits: 32 }) });
}

#[test]
fn test_overloads() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = load_expected("programs/functions/overloads.vale");
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    assert!(matches!(coutputs.lookup_function_by_str("main").header.return_type,
        CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT { bits: 32 }), .. }
    ));
}

#[test]
fn test_readonly_ufcs() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = load_expected("programs/ufcs.vale");
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    compile.expect_compiler_outputs();
}

#[test]
fn test_readwrite_ufcs() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = load_expected("programs/readwriteufcs.vale");
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    compile.expect_compiler_outputs();
}

#[test]
fn test_templates() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "func bork<T>(a T) T { return a; }\n",
        "exported func main() int { bork(true); bork(2); bork(3) }\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    // Tests that there's only two functions, because we have generics not templates
    assert!(coutputs.get_all_user_functions().len() == 2);
}

#[test]
fn test_taking_a_callable_param() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "func do<F>(callable F) int\n",
        "where func(&F)int, func drop(F)void\n",
        "{\n",
        "  return callable();\n",
        "}\n",
        "exported func main() int { return do({ return 3; }); }\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let do_fn = coutputs.lookup_function_by_str("do");
    assert!(matches!(do_fn.header.return_type,
        CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT { bits: 32 }), .. }
    ));
}

#[test]
fn simple_struct() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "#!DeriveStructDrop\n",
        "struct MyStruct { a int; }\n",
        "exported func main() {\n",
        "  ms = MyStruct(7);\n",
        "  [_] = ms;\n",
        "}\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    // Check the struct was made
    coutputs.structs.iter().find(|def| matches!(def,
        StructDefinitionT {
            template_name: IdT {
                local_name: INameT::StructTemplate(StructTemplateNameT { human_name: StrI("MyStruct"), .. }),
                ..
            },
            instantiated_citizen: StructTT {
                id: IdT {
                    local_name: INameT::Struct(StructNameT {
                        template: IStructTemplateNameT::StructTemplate(
                            StructTemplateNameT { human_name: StrI("MyStruct"), .. }
                        ),
                        ..
                    }),
                    ..
                },
                ..
            },
            weakable: false,
            mutability: ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }),
            members: [IStructMemberT::Normal(NormalStructMemberT {
                name: IVarNameT::CodeVar(CodeVarNameT { name: StrI("a"), .. }),
                variability: VariabilityT::Final,
                tyype: IMemberTypeT::Reference(ReferenceMemberTypeT {
                    reference: CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT { bits: 32 }), .. },
                }),
            })],
            is_closure: false,
            ..
        }
    )).unwrap();
    // Check there's a constructor
    let _ = collect_where_tnode!(
        NodeRefT::FunctionDefinition(coutputs.lookup_function_by_str("MyStruct")),
        NodeRefT::FunctionHeader(h @ FunctionHeaderT {
            id: IdT {
                local_name: INameT::Function(FunctionNameT {
                    template: FunctionTemplateNameT { human_name: StrI("MyStruct"), .. },
                    ..
                }),
                ..
            },
            params: [ParameterT {
                name: IVarNameT::CodeVar(CodeVarNameT { name: StrI("a"), .. }),
                virtuality: None,
                tyype: CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT { bits: 32 }), .. },
                ..
            }],
            return_type: CoordT {
                ownership: OwnershipT::Own,
                kind: KindT::Struct(StructTT {
                    id: IdT {
                        local_name: INameT::Struct(StructNameT {
                            template: IStructTemplateNameT::StructTemplate(
                                StructTemplateNameT { human_name: StrI("MyStruct"), .. }
                            ),
                            ..
                        }),
                        ..
                    },
                    ..
                }),
                ..
            },
            ..
        }) => Some(h)
    );
    let main = coutputs.lookup_function_by_str("main");
    // Check that we call the constructor
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(
            FunctionCallTE {
                callable: PrototypeT {
                    id: IdT {
                        local_name: INameT::Function(FunctionNameT {
                            template: FunctionTemplateNameT { human_name: StrI("MyStruct"), .. },
                            ..
                        }),
                        ..
                    },
                    ..
                },
                args: [ReferenceExpressionTE::ConstantInt(
                    ConstantIntTE {
                        value: ITemplataT::Integer(7),
                        ..
                    }
                )],
                ..
            }
        ) => Some(())
    );
}

#[test]
fn calls_destructor_on_local_var() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "struct Muta { }\n",
        "func destructor(m ^Muta) {\n",
        "  Muta[ ] = m;\n",
        "}\n",
        "exported func main() {\n",
        "  a = Muta();\n",
        "}\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(
            FunctionCallTE {
                callable: PrototypeT {
                    id: IdT {
                        local_name: INameT::Function(FunctionNameT {
                            template: FunctionTemplateNameT { human_name: StrI("drop"), .. },
                            ..
                        }),
                        ..
                    },
                    ..
                },
                ..
            }
        ) => Some(())
    );
    let all_calls = collect_where_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(_fpc) => Some(())
    );
    assert_eq!(all_calls.len(), 2);
}

#[test]
fn tests_defining_an_empty_interface_and_an_implementing_struct() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "sealed interface MyInterface { }\n",
        "struct MyStruct { }\n",
        "impl MyInterface for MyStruct;\n",
        "func main(a MyStruct) {}\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    let interfaces_matching: Vec<_> = coutputs.interfaces.iter()
        .filter(|d| unapply_simple_name(&d.template_name).as_deref() == Some("MyInterface")
            && !d.weakable
            && matches!(d.mutability, ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }))
            && d.internal_methods.is_empty())
        .collect();
    let interface_def = expect_1(&interfaces_matching);

    let structs_matching: Vec<_> = coutputs.structs.iter()
        .filter(|d| unapply_simple_name(&d.template_name).as_deref() == Some("MyStruct")
            && !d.weakable
            && matches!(d.mutability, ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }))
            && !d.is_closure)
        .collect();
    let struct_def = expect_1(&structs_matching);

    assert!(coutputs.interface_to_sub_citizen_to_edge.iter()
        .flat_map(|(_, sub_map)| sub_map.values())
        .any(|edge| {
            edge.sub_citizen.id() == struct_def.instantiated_citizen.id &&
            edge.super_interface == interface_def.instantiated_interface.id
        }));
}

#[test]
fn tests_defining_a_non_empty_interface_and_an_implementing_struct() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "exported sealed interface MyInterface {\n",
        "  func bork(virtual self &MyInterface);\n",
        "}\n",
        "exported struct MyStruct { }\n",
        "impl MyInterface for MyStruct;\n",
        "func bork(self &MyStruct) {}\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    let interfaces_matching: Vec<_> = coutputs.interfaces.iter()
        .filter(|d| unapply_simple_name(&d.template_name).as_deref() == Some("MyInterface")
            && !d.weakable
            && matches!(d.mutability, ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable })))
        .collect();
    let interface_def = expect_1(&interfaces_matching);

    let bork_method = interface_def.internal_methods.iter()
        .find(|(proto, _)| unapply_simple_name(&proto.id).as_deref() == Some("bork"))
        .unwrap();
    let _ = bork_method;

    let structs_matching: Vec<_> = coutputs.structs.iter()
        .filter(|d| unapply_simple_name(&d.template_name).as_deref() == Some("MyStruct")
            && !d.weakable
            && matches!(d.mutability, ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }))
            && !d.is_closure)
        .collect();
    let struct_def = expect_1(&structs_matching);

    assert!(coutputs.interface_to_sub_citizen_to_edge.iter()
        .flat_map(|(_, sub_map)| sub_map.values())
        .any(|edge| {
            edge.sub_citizen.id() == struct_def.instantiated_citizen.id &&
            edge.super_interface == interface_def.instantiated_interface.id
        }));
}

#[test]
fn stamps_an_interface_template_via_a_function_return() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.drop.*;\n",
        "\n",
        "sealed interface MyInterface<X Ref> where func drop(X)void { }\n",
        "\n",
        "struct SomeStruct<X Ref> where func drop(X)void { x X; }\n",
        "impl<X> MyInterface<X> for SomeStruct<X>;\n",
        "\n",
        "func doAThing<T>(t T) SomeStruct<T>\n",
        "where func drop(T)void {\n",
        "  return SomeStruct<T>(t);\n",
        "}\n",
        "\n",
        "exported func main() {\n",
        "  doAThing(4);\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn reads_a_struct_member() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "#!DeriveStructDrop\n",
        "struct MyStruct { a int; }\n",
        "exported func main() int {\n",
        "  ms = MyStruct(7);\n",
        "  x = ms.a;\n",
        "  [_] = ms;\n",
        "  return x;\n",
        "}\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    let main = coutputs.lookup_function_by_str("main");
    // check for the member access
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::ReferenceMemberLookup(
            ReferenceMemberLookupTE {
                struct_expr: ReferenceExpressionTE::SoftLoad(SoftLoadTE { target_ownership: OwnershipT::Borrow, .. }),
                member_name: IVarNameT::CodeVar(CodeVarNameT { name: StrI("a"), .. }),
                member_reference: CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT { bits: 32 }), .. },
                variability: VariabilityT::Final,
                ..
            }
        ) => Some(())
    );
}

#[test]
fn automatically_drops_struct() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "struct MyStruct { a int; }\n",
        "exported func main() int {\n",
        "  ms = MyStruct(7);\n",
        "  return ms.a;\n",
        "}\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    let main = coutputs.lookup_function_by_str("main");
    // check for the call to drop
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(
            FunctionCallTE {
                callable: PrototypeT {
                    id: IdT {
                        init_steps: [INameT::StructTemplate(StructTemplateNameT { human_name: StrI("MyStruct"), .. })],
                        local_name: INameT::Function(FunctionNameT {
                            template: FunctionTemplateNameT { human_name: StrI("drop"), .. },
                            template_args: &[],
                            parameters: [CoordT {
                                ownership: OwnershipT::Own,
                                kind: KindT::Struct(StructTT {
                                    id: IdT {
                                        local_name: INameT::Struct(StructNameT {
                                            template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("MyStruct"), .. }),
                                            template_args: &[],
                                            ..
                                        }),
                                        ..
                                    },
                                    ..
                                }),
                                ..
                            }],
                            ..
                        }),
                        ..
                    },
                    return_type: CoordT { ownership: OwnershipT::Share, kind: KindT::Void(_), .. },
                },
                ..
            }
        ) => Some(())
    );
}

#[test]
fn tests_stamping_an_interface_template_from_a_function_param() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "interface MyOption<T Ref> { }\n",
        "func main(a &MyOption<int>) { }\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let interface_template_name = compile.typing_interner.intern_interface_template_name(
        InterfaceTemplateNameT {
            human_namee: scout_arena.intern_str("MyOption"),
        });
    let template_args_vec = vec![
        ITemplataT::Coord(
            compile.typing_interner.alloc(CoordTemplataT {
                coord: CoordT {
                    ownership: OwnershipT::Share,
                    region: RegionT { region: IRegionT::Default },
                    kind: KindT::Int(IntT { bits: 32 }),
                },
            })
        ),
    ];
    let interface_name = compile.typing_interner.intern_interface_name(
        InterfaceNameValT {
            template: interface_template_name,
            template_args: &template_args_vec,
        });
    let test_tld = scout_arena.intern_package_coordinate(scout_arena.intern_str("test"), &[]);
    let interface_id = compile.typing_interner.intern_id(
        IdValT {
            package_coord: test_tld,
            init_steps: &[],
            local_name: INameT::Interface(interface_name),
        });
    let interface_tt = compile.typing_interner.intern_interface_tt(
        InterfaceTTValT { id: *interface_id });
    let expected_coord = CoordT {
        ownership: OwnershipT::Borrow,
        region: RegionT { region: IRegionT::Default },
        kind: KindT::Interface(interface_tt),
    };

    let coutputs = compile.expect_compiler_outputs();
    coutputs.lookup_interface_by_template_name(interface_template_name);
    let main = coutputs.lookup_function_by_str("main");
    assert_eq!(main.header.params[0].tyype, expected_coord);
}

#[test]
fn reports_mismatched_return_type_when_expecting_void() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "exported func main() { 73 }\n";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err()
        .unwrap_or_else(|| panic!("expected Err(BodyResultDoesntMatch), got Ok"));
    match &err {
        ICompileErrorT::BodyResultDoesntMatch { function_name, expected_return_type, result_type, .. } => {
            match function_name {
                IFunctionDeclarationNameS::FunctionName(fn_name) => assert_eq!(fn_name.name.as_str(), "main"),
                other => panic!("expected FunctionName: {:?}", other),
            }
            assert_eq!(expected_return_type.ownership, OwnershipT::Share);
            match expected_return_type.kind {
                KindT::Void(_) => {}
                other => panic!("expected VoidT: {:?}", other),
            }
            assert_eq!(result_type.ownership, OwnershipT::Share);
            match result_type.kind {
                KindT::Int(_) => {}
                other => panic!("expected IntT: {:?}", other),
            }
        }
        _other => panic!("expected BodyResultDoesntMatch"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:1:1:
exported func main() { 73 }
At test:0.vale:1:1:
exported func main() { 73 }
Function test:0.vale:1:1: main return type void doesn't match body's result: i32
"#,
    );
}

#[test]
fn tests_exporting_function() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "exported func moo() { }\n";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let moo = coutputs.lookup_function_by_str("moo");
    let export = expect_1(&coutputs.function_exports);
    assert_eq!(export.prototype, moo.header.to_prototype());
}

#[test]
fn tests_exporting_struct() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "exported struct Moo { a int; }\n";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let moo = coutputs.lookup_struct_by_str("Moo");
    let export = expect_1(&coutputs.kind_exports);
    assert_eq!(export.tyype, KindT::from(&moo.instantiated_citizen));
}

#[test]
fn tests_exporting_interface() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "exported sealed interface IMoo { func hi(virtual this &IMoo) void; }\n";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let moo = coutputs.lookup_interface_by_human_name("IMoo");
    let export = expect_1(&coutputs.kind_exports);
    assert_eq!(export.tyype, KindT::from(&moo.instantiated_interface));
}

#[test]
fn tests_single_expression_and_single_statement_functions_returns() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "struct MyThing { value int; }\n",
        "func moo() MyThing { return MyThing(4); }\n",
        "exported func main() { moo(); }\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let moo = coutputs.lookup_function_by_str("moo");
    match moo.header.return_type {
        CoordT {
            ownership: OwnershipT::Own,
            kind: KindT::Struct(StructTT {
                id: IdT {
                    init_steps: &[],
                    local_name: INameT::Struct(StructNameT {
                        template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("MyThing"), .. }),
                        ..
                    }),
                    ..
                },
                ..
            }),
            ..
        } => {}
        other => panic!("moo.header.returnType: {:?}", other),
    }
    let main = coutputs.lookup_function_by_str("main");
    match main.header.return_type {
        CoordT { ownership: OwnershipT::Share, kind: KindT::Void(_), .. } => {}
        other => panic!("main.header.returnType: {:?}", other),
    }
}

#[test]
fn tests_calling_a_templated_struct_s_constructor() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.drop.*;\n",
        "struct MySome<T Ref> where func drop(T)void { value T; }\n",
        "exported func main() int {\n",
        "  return MySome<int>(4).value;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    coutputs.lookup_struct_by_template_name(
        StructTemplateNameT {
            human_name: scout_arena.intern_str("MySome"),
        });

    let constructor = coutputs.lookup_function_by_str("MySome");
    match constructor.header {
        FunctionHeaderT {
            id: IdT {
                local_name: INameT::Function(FunctionNameT {
                    template: FunctionTemplateNameT { human_name: StrI("MySome"), .. },
                    template_args: [ITemplataT::Coord(CoordTemplataT { coord: CoordT {
                        ownership: OwnershipT::Own,
                        kind: KindT::KindPlaceholder(KindPlaceholderT {
                            id: IdT {
                                local_name: INameT::KindPlaceholder(KindPlaceholderNameT {
                                    template: KindPlaceholderTemplateNameT {
                                        index: 0,
                                        rune: IRuneS::CodeRune(CodeRuneS { name: StrI("T") }),
                                        ..
                                    },
                                }),
                                ..
                            },
                            ..
                        }),
                        ..
                    } })],
                    parameters: [CoordT {
                        ownership: OwnershipT::Own,
                        kind: KindT::KindPlaceholder(KindPlaceholderT {
                            id: IdT {
                                local_name: INameT::KindPlaceholder(KindPlaceholderNameT {
                                    template: KindPlaceholderTemplateNameT { index: 0, .. },
                                }),
                                ..
                            },
                            ..
                        }),
                        ..
                    }],
                    ..
                }),
                ..
            },
            attributes: &[],
            params: [ParameterT {
                name: IVarNameT::CodeVar(CodeVarNameT { name: StrI("value"), .. }),
                virtuality: None,
                tyype: CoordT {
                    ownership: OwnershipT::Own,
                    kind: KindT::KindPlaceholder(KindPlaceholderT {
                        id: IdT {
                            local_name: INameT::KindPlaceholder(KindPlaceholderNameT {
                                template: KindPlaceholderTemplateNameT { index: 0, .. },
                            }),
                            ..
                        },
                        ..
                    }),
                    ..
                },
                ..
            }],
            return_type: CoordT {
                ownership: OwnershipT::Own,
                kind: KindT::Struct(StructTT {
                    id: IdT {
                        local_name: INameT::Struct(StructNameT {
                            template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("MySome"), .. }),
                            template_args: [ITemplataT::Coord(CoordTemplataT { coord: CoordT {
                                ownership: OwnershipT::Own,
                                kind: KindT::KindPlaceholder(KindPlaceholderT {
                                    id: IdT {
                                        local_name: INameT::KindPlaceholder(KindPlaceholderNameT {
                                            template: KindPlaceholderTemplateNameT { index: 0, .. },
                                        }),
                                        ..
                                    },
                                    ..
                                }),
                                ..
                            } })],
                            ..
                        }),
                        ..
                    },
                    ..
                }),
                ..
            },
            maybe_origin_function_templata: Some(_),
            ..
        } => {}
        other => panic!("constructor.header: {:?}", other),
    }

    let main = coutputs.lookup_function_by_str("main");
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(
            FunctionCallTE {
                callable: PrototypeT {
                    id: IdT {
                        local_name: INameT::Function(FunctionNameT {
                            template: FunctionTemplateNameT { human_name: StrI("MySome"), .. },
                            ..
                        }),
                        ..
                    },
                    ..
                },
                ..
            }
        ) => Some(())
    );
}

#[test]
fn tests_upcasting_from_a_struct_to_an_interface() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = include_str!("../../tests/programs/virtuals/upcasting.vale");
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    let main = coutputs.lookup_function_by_str("main");

    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::LetNormal(LetNormalTE {
            variable: ILocalVariableT::Reference(ReferenceLocalVariableT {
                name: IVarNameT::CodeVar(CodeVarNameT { name: StrI("x"), .. }),
                variability: VariabilityT::Final,
                coord: CoordT {
                    ownership: OwnershipT::Own,
                    kind: KindT::Interface(InterfaceTT {
                        id: IdT {
                            local_name: INameT::Interface(InterfaceNameT {
                                template: InterfaceTemplateNameT { human_namee: StrI("MyInterface"), .. },
                                ..
                            }),
                            ..
                        },
                        ..
                    }),
                    ..
                },
            }),
            ..
        }) => Some(())
    );

    let upcast: &UpcastTE = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Upcast(u) => Some(u)
    );

    match upcast.result().coord {
        CoordT {
            ownership: OwnershipT::Own,
            kind: KindT::Interface(InterfaceTT {
                id: IdT {
                    package_coord: x,
                    init_steps: &[],
                    local_name: INameT::Interface(InterfaceNameT {
                        template: InterfaceTemplateNameT { human_namee: StrI("MyInterface"), .. },
                        template_args: &[],
                        ..
                    }),
                    ..
                },
                ..
            }),
            ..
        } => assert!(x.is_test()),
        other => panic!("upcast result coord: {:?}", other),
    }
    match upcast.inner_expr.result().coord {
        CoordT {
            ownership: OwnershipT::Own,
            kind: KindT::Struct(StructTT {
                id: IdT {
                    package_coord: x,
                    init_steps: &[],
                    local_name: INameT::Struct(StructNameT {
                        template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("MyStruct"), .. }),
                        template_args: &[],
                        ..
                    }),
                    ..
                },
                ..
            }),
            ..
        } => assert!(x.is_test()),
        other => panic!("inner expr coord: {:?}", other),
    }
}

#[test]
fn tests_calling_a_virtual_function() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = include_str!("../../tests/programs/virtuals/calling.vale");
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    let main = coutputs.lookup_function_by_str("main");

    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Upcast(u @ UpcastTE {
            target_super_kind: ISuperKindTT::Interface(InterfaceTT {
                id: IdT {
                    local_name: INameT::Interface(InterfaceNameT {
                        template: InterfaceTemplateNameT { human_namee: StrI("Car"), .. },
                        ..
                    }),
                    ..
                },
                ..
            }),
            ..
        }) => {
            match u.inner_expr.result().coord.kind {
                KindT::Struct(StructTT {
                    id: IdT {
                        local_name: INameT::Struct(StructNameT {
                            template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("Toyota"), .. }),
                            ..
                        }),
                        ..
                    },
                    ..
                }) => {}
                other => panic!("inner expr kind: {:?}", other),
            }
            match u.result().coord.kind {
                KindT::Interface(InterfaceTT {
                    id: IdT {
                        package_coord: pc,
                        init_steps: &[],
                        local_name: INameT::Interface(InterfaceNameT {
                            template: InterfaceTemplateNameT { human_namee: StrI("Car"), .. },
                            template_args: &[],
                            ..
                        }),
                        ..
                    },
                    ..
                }) => {
                    assert!(pc.is_test());
                }
                other => panic!("upcast result kind: {:?}", other),
            }
            Some(())
        }
    );
}

#[test]
fn tests_upcasting_has_the_right_stuff() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = include_str!("../../tests/programs/virtuals/calling.vale");
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    let main = coutputs.lookup_function_by_str("main");

    let upcast: &UpcastTE = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Upcast(u @ UpcastTE {
            target_super_kind: ISuperKindTT::Interface(InterfaceTT {
                id: IdT {
                    local_name: INameT::Interface(InterfaceNameT {
                        template: InterfaceTemplateNameT { human_namee: StrI("Car"), .. },
                        ..
                    }),
                    ..
                },
                ..
            }),
            ..
        }) => Some(u)
    );

    match upcast.inner_expr.result().coord.kind {
        KindT::Struct(StructTT {
            id: IdT {
                local_name: INameT::Struct(StructNameT {
                    template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("Toyota"), .. }),
                    ..
                }),
                ..
            },
            ..
        }) => {}
        other => panic!("inner expr kind: {:?}", other),
    }
    match upcast.result().coord.kind {
        KindT::Interface(InterfaceTT {
            id: IdT {
                package_coord: x,
                init_steps: &[],
                local_name: INameT::Interface(InterfaceNameT {
                    template: InterfaceTemplateNameT { human_namee: StrI("Car"), .. },
                    template_args: &[],
                    ..
                }),
                ..
            },
            ..
        }) => assert!(x.is_test()),
        other => panic!("upcast result kind: {:?}", other),
    }

    let impl_edge = coutputs.lookup_edge(upcast.impl_name);
    assert!(impl_edge.sub_citizen.id() == upcast.inner_expr.result().coord.kind.expect_citizen().id());
    assert!(impl_edge.super_interface == upcast.result().coord.kind.expect_citizen().id());

//    freePrototype.fullName.last.parameters.head shouldEqual up.result.reference
}

#[test]
fn tests_calling_a_virtual_function_through_a_borrow_ref() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = include_str!("../../tests/programs/virtuals/callingThroughBorrow.vale");
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    let main = coutputs.lookup_function_by_str("main");

    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(FunctionCallTE {
            callable: PrototypeT {
                id: IdT {
                    local_name: INameT::Function(
                        FunctionNameT {
                            template: FunctionTemplateNameT { human_name: StrI("doCivicDance"), .. },
                            ..
                        }
                    ),
                    ..
                },
                return_type: CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT::I32), .. },
                ..
            },
            ..
        }) => {
//        vassert(f.callable.paramTypes == Vector(Coord(Borrow,InterfaceRef2(simpleName("Car")))))
            Some(())
        }
    );
}

#[test]
fn tests_calling_a_templated_function_with_explicit_template_args() {
    // Tests putting MyOption<int> as the type of x.
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "func moo<T> () where T Ref { }\n",
        "exported func main() {\n",
        "  moo<int>();\n",
        "}\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn tests_destructuring_borrow_doesnt_compile_to_destroy() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "\n",
        "struct Vec3i {\n",
        "  x int;\n",
        "  y int;\n",
        "  z int;\n",
        "}\n",
        "\n",
        "exported func main() int {\n",
        "  v = Vec3i(3, 4, 5);\n",
        "\t [x, y, z] = &v;\n",
        "  return y;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    let destroys = collect_where_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Destroy(_) => Some(())
    );
    assert_eq!(destroys.len(), 0);
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::ReferenceMemberLookup(
            ReferenceMemberLookupTE {
                struct_expr: ReferenceExpressionTE::SoftLoad(
                    SoftLoadTE {
                        expr: AddressExpressionTE::LocalLookup(
                            LocalLookupTE {
                                local_variable: ILocalVariableT::Reference(
                                    ReferenceLocalVariableT {
                                        variability: VariabilityT::Final,
                                        coord: CoordT { kind: KindT::Struct(_), .. },
                                        ..
                                    }
                                ),
                                ..
                            }
                        ),
                        target_ownership: OwnershipT::Borrow,
                    }
                ),
                member_name: IVarNameT::CodeVar(
                    CodeVarNameT { name: StrI("x"), .. }
                ),
                member_reference: CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT { bits: 32 }), .. },
                variability: VariabilityT::Final,
                ..
            }
        ) => Some(())
    );
}

#[test]
fn tests_making_a_variable_with_a_pattern() {
    // Tests putting MyOption<int> as the type of x.
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "\n",
        "sealed interface MyOption<T> where T Ref { }\n",
        "\n",
        "struct MySome<T> where T Ref {}\n",
        "impl<T> MyOption<T> for MySome<T>;\n",
        "\n",
        "func doSomething(opt MyOption<int>) int {\n",
        "  return 9;\n",
        "}\n",
        "\n",
        "exported func main() int {\n",
        "\tx MyOption<int> = MySome<int>();\n",
        "\treturn doSomething(x);\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn tests_a_linked_list() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = load_expected("programs/virtuals/ordinarylinkedlist.vale");
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn test_borrow_ref() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = load_expected("programs/borrowRef.vale");
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn tests_calling_a_function_with_an_upcast() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "interface ISpaceship {}\n",
        "struct Firefly {}\n",
        "impl ISpaceship for Firefly;\n",
        "func launch(ship &ISpaceship) { }\n",
        "func main() {\n",
        "  launch(&Firefly());\n",
        "}\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    let main = coutputs.lookup_function_by_str("main");

    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Upcast(UpcastTE {
            target_super_kind: ISuperKindTT::Interface(InterfaceTT {
                id: IdT {
                    local_name: INameT::Interface(InterfaceNameT {
                        template: InterfaceTemplateNameT { human_namee: StrI("ISpaceship"), .. },
                        ..
                    }),
                    ..
                },
                ..
            }),
            ..
        }) => Some(())
    );
}

#[test]
fn tests_calling_a_templated_function_with_an_upcast() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "interface ISpaceship<T> where T Ref {}\n",
        "struct Firefly<T> where T Ref {}\n",
        "impl<T> ISpaceship<T> for Firefly<T>;\n",
        "func launch<T>(ship &ISpaceship<T>) { }\n",
        "func main() {\n",
        "  launch(&Firefly<int>());\n",
        "}\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    let main = coutputs.lookup_function_by_str("main");

    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Upcast(UpcastTE {
            target_super_kind: ISuperKindTT::Interface(InterfaceTT {
                id: IdT {
                    local_name: INameT::Interface(InterfaceNameT {
                        template: InterfaceTemplateNameT { human_namee: StrI("ISpaceship"), .. },
                        ..
                    }),
                    ..
                },
                ..
            }),
            ..
        }) => Some(())
    );
}

#[test]
fn tests_upcast_with_generics_has_the_right_stuff() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "interface ISpaceship<T> where T Ref {}\n",
        "struct Firefly<T> where T Ref {}\n",
        "impl<T> ISpaceship<T> for Firefly<T>;\n",
        "func launch<T>(ship &ISpaceship<T>) { }\n",
        "func main() {\n",
        "  launch(&Firefly<int>());\n",
        "}\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    let main = coutputs.lookup_function_by_str("main");

    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Upcast(UpcastTE {
            target_super_kind: ISuperKindTT::Interface(InterfaceTT {
                id: IdT {
                    local_name: INameT::Interface(InterfaceNameT {
                        template: InterfaceTemplateNameT { human_namee: StrI("ISpaceship"), .. },
                        ..
                    }),
                    ..
                },
                ..
            }),
            ..
        }) => Some(())
    );
}

#[test]
fn tests_a_templated_linked_list() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = load_expected("programs/genericvirtuals/templatedlinkedlist.vale");
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn tests_a_foreach_for_a_linked_list() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = load_expected("programs/genericvirtuals/foreachlinkedlist.vale");
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn test_return_from_inside_if_destroys_locals() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "struct Marine { hp int; }\n",
        "exported func main() int {\n",
        "  m = Marine(5);\n",
        "  x =\n",
        "    if (true) {\n",
        "      return 7;\n",
        "    } else {\n",
        "      m.hp\n",
        "    };\n",
        "  return x;\n",
        "}",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    let destructor_calls = collect_where_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(fpc @ FunctionCallTE {
            callable: PrototypeT {
                id: IdT {
                    local_name: INameT::Function(FunctionNameT {
                        template: FunctionTemplateNameT { human_name: StrI("drop"), .. },
                        parameters: [CoordT {
                            ownership: OwnershipT::Own,
                            kind: KindT::Struct(StructTT {
                                id: IdT {
                                    local_name: INameT::Struct(StructNameT {
                                        template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("Marine"), .. }),
                                        ..
                                    }),
                                    ..
                                },
                                ..
                            }),
                            ..
                        }],
                        ..
                    }),
                    init_steps: [INameT::StructTemplate(StructTemplateNameT { human_name: StrI("Marine"), .. })],
                    ..
                },
                ..
            },
            ..
        }) => Some(fpc)
    );
    assert_eq!(destructor_calls.len(), 2);
}

#[test]
fn recursive_struct() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "struct ListNode imm {\n",
        "  tail ListNode;\n",
        "}\n",
        "func main(a ListNode) {}\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn recursive_struct_with_opt() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.opt.*;\n",
        "struct ListNode {\n",
        "  tail Opt<ListNode>;\n",
        "}\n",
        "func main(a ListNode) {}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn templated_imm_struct() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "struct ListNode<T Ref> imm {\n",
        "  tail ListNode<T>;\n",
        "}\n",
        "func main(a ListNode<int>) {}\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn borrow_load_member() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "struct Bork {\n",
        "  x int;\n",
        "}\n",
        "func getX(bork &Bork) int { return bork.x; }\n",
        "struct List {\n",
        "  array! Bork;\n",
        "}\n",
        "exported func main() int {\n",
        "  l = List(Bork(0));\n",
        "  return getX(&l.array);\n",
        "}\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    compile.expect_compiler_outputs();
}

#[test]
fn test_vector_of_struct_templata() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.arrays.*;\n",
        "import v.builtins.drop.*;\n",
        "\n",
        "struct Vec2 imm {\n",
        "  x float;\n",
        "  y float;\n",
        "}\n",
        "struct Pattern imm {\n",
        "  patternTiles []<imm>Vec2;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn if_branches_returns_never_and_struct() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.panicutils.*;\n",
        "exported struct Moo {}\n",
        "exported func main() Moo {\n",
        "  if true {\n",
        "    Moo()\n",
        "  } else {\n",
        "    panic(\"Error in CreateDir\");\n",
        "  }\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn test_return() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "exported func main() int {\n  return 7;\n}";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Return(_) => Some(())
    );
}

#[test]
fn test_return_from_inside_if() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
import v.builtins.panic.*;
exported func main() int {
  if (true) {
    return 7;
  } else {
    return 9;
  }
  __vbi_panic();
}";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    let returns = collect_where_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Return(_) => Some(())
    );
    assert_eq!(returns.len(), 2);
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::ConstantInt(
            ConstantIntTE {
                value: ITemplataT::Integer(7),
                ..
            }
        ) => Some(())
    );
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::ConstantInt(
            ConstantIntTE {
                value: ITemplataT::Integer(9),
                ..
            }
        ) => Some(())
    );
}

#[test]
fn zero_method_anonymous_interface() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "interface MyInterface {}\n",
        "exported func main() {\n",
        "  x = MyInterface();\n",
        "}\n",
    );
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    compile.expect_compiler_outputs();
}

#[test]
fn reports_when_exported_function_depends_on_non_exported_param() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "struct Firefly { }\nexported func moo(firefly &Firefly) { }";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::ExportedFunctionDependedOnNonExportedKind { .. } => {}
        _other => panic!("expected ExportedFunctionDependedOnNonExportedKind"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:2:1:
exported func moo(firefly &Firefly) { }
Exported function:
moo(&Firefly)
depends on kind:
Firefly
that wasn't exported from package test
"#,
    );
}

#[test]
fn reports_when_exported_function_depends_on_non_exported_return() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "import panicutils.*;\nstruct Firefly { }\nexported func moo() &Firefly { __pretend<&Firefly>() }";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::ExportedFunctionDependedOnNonExportedKind { .. } => {}
        _other => panic!("expected ExportedFunctionDependedOnNonExportedKind"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:3:1:
exported func moo() &Firefly { __pretend<&Firefly>() }
Exported function:
moo
depends on kind:
Firefly
that wasn't exported from package test
"#,
    );
}

#[test]
fn reports_when_extern_function_depends_on_non_exported_param() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "struct Firefly { }\nextern func moo(firefly &Firefly);";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::ExternFunctionDependedOnNonExportedKind { .. } => {}
        _other => panic!("expected ExternFunctionDependedOnNonExportedKind"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:2:1:
extern func moo(firefly &Firefly);
Extern function moo depends on kind Firefly that wasn't exported from package test
"#,
    );
}

#[test]
fn reports_when_extern_function_depends_on_non_exported_return() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "struct Firefly imm { }\nextern func moo() &Firefly;";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::ExternFunctionDependedOnNonExportedKind { .. } => {}
        _other => panic!("expected ExternFunctionDependedOnNonExportedKind"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:2:1:
extern func moo() &Firefly;
Extern function moo depends on kind Firefly that wasn't exported from package test
"#,
    );
}

#[test]
fn reports_when_exported_struct_depends_on_non_exported_member() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
exported struct Firefly imm {
  raza Raza;
}
struct Raza imm { }";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::ExportedImmutableKindDependedOnNonExportedKind { .. } => {}
        _other => panic!("expected ExportedImmutableKindDependedOnNonExportedKind"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:2:1:
exported struct Firefly imm {
Exported kind Firefly depends on kind Raza that wasn't exported from package test
"#,
    );
}

#[test]
fn checks_that_we_stored_a_borrowed_temporary_in_a_local() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "struct Muta { }\n",
        "func doSomething(m &Muta, i int) {}\n",
        "exported func main() {\n",
        "  doSomething(&Muta(), 1)\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::LetAndLend(
            LetAndLendTE {
                target_ownership: OwnershipT::Borrow,
                ..
            }
        ) => Some(())
    );
}

// Removed this test because this is now caught in the postparser (the scout pass now throws
// CouldntFindVarToMutateS on an unrecognized name, before the typing pass runs). Per canonical
// Scala which removed it for the same reason.

#[test]
fn reports_when_ssa_from_callable_has_unknown_element_type() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "exported func main() int {\n",
        "  a = #[#5]NoSuchType(&{_ * 42});\n",
        "  return 7;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::HigherTypingInferError { .. } => {}
        other => panic!("expected HigherTypingInferError, got {:?}", other),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:1:1:
exported func main() int {
At test:0.vale:2:7:
  a = #[#5]NoSuchType(&{_ * 42});
: Couldn't solve generics types:
Couldn't find anything with the name 'NoSuchType'
"#,
    );
}

#[test]
fn reports_when_ssa_callable_returns_wrong_element_type() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.arith.*;\n",
        "exported func main() int {\n",
        "  a = #[#5]int(&{ _ == 0 });\n",
        "  return 7;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::UnexpectedArrayElementType { .. } => {}
        other => panic!("expected UnexpectedArrayElementType, got {:?}", other),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:2:1:
exported func main() int {
At test:0.vale:3:7:
  a = #[#5]int(&{ _ == 0 });
Unexpected type for array element, tried to put a bool into an array of i32
"#,
    );
}

#[test]
fn reports_when_rsa_from_callable_has_unknown_element_type() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.arrays.*;\n",
        "import v.builtins.drop.*;\n",
        "exported func main() int {\n",
        "  a = []NoSuchType(3, &(i int) => { i });\n",
        "  return 7;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::HigherTypingInferError { .. } => {}
        other => panic!("expected HigherTypingInferError, got {:?}", other),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:3:1:
exported func main() int {
At test:0.vale:4:7:
  a = []NoSuchType(3, &(i int) => { i });
: Couldn't solve generics types:
Couldn't find anything with the name 'NoSuchType'
"#,
    );
}

#[test]
fn reports_when_rsa_callable_returns_wrong_element_type() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.arrays.*;\n",
        "import v.builtins.arith.*;\n",
        "import v.builtins.drop.*;\n",
        "exported func main() int {\n",
        "  a = #[]int(5, &{ _ == 0 });\n",
        "  return 7;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::UnexpectedArrayElementType { .. } => {}
        other => panic!("expected UnexpectedArrayElementType, got {:?}", other),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:4:1:
exported func main() int {
At test:0.vale:5:7:
  a = #[]int(5, &{ _ == 0 });
Unexpected type for array element, tried to put a bool into an array of i32
"#,
    );
}

#[test]
fn reports_when_ssa_from_values_has_unknown_element_type() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "exported func main() int {\n",
        "  a = #[#]NoSuchType(1, 2, 3);\n",
        "  return 7;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::HigherTypingInferError { .. } => {}
        other => panic!("expected HigherTypingInferError, got {:?}", other),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:1:1:
exported func main() int {
At test:0.vale:2:7:
  a = #[#]NoSuchType(1, 2, 3);
: Couldn't solve generics types:
Couldn't find anything with the name 'NoSuchType'
"#,
    );
}

#[test]
fn reports_when_ssa_values_have_wrong_element_type() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "exported func main() int {\n",
        "  a = #[#]int(true, false, true);\n",
        "  return 7;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::UnexpectedArrayElementType { .. } => {}
        other => panic!("expected UnexpectedArrayElementType, got {:?}", other),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:1:1:
exported func main() int {
At test:0.vale:2:7:
  a = #[#]int(true, false, true);
Unexpected type for array element, tried to put a bool into an array of i32
"#,
    );
}

#[test]
fn reports_when_rsa_indexed_with_non_integer() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.arrays.*;\n",
        "import v.builtins.drop.*;\n",
        "exported func main() int {\n",
        "  a = Array<mut, int>(3);\n",
        "  return a[true];\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::IndexedArrayWithNonInteger { .. } => {}
        other => panic!("expected IndexedArrayWithNonInteger, got {:?}", other),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:3:1:
exported func main() int {
At test:0.vale:5:10:
  return a[true];
At test:0.vale:5:10:
  return a[true];
Indexed array with non-integer: bool
"#,
    );
}

#[test]
fn reports_when_dot_applied_to_non_container() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "exported func main() int {\n",
        "  x = 5;\n",
        "  return x.foo;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::RangedInternalErrorT { message, .. } if message.contains("Can't apply") => {}
        other => panic!("expected RangedInternalErrorT 'Can't apply', got {:?}", other),
    }
    // TODO: the RangedInternalErrorT message itself includes a Debug-format of the kind; replace at the error-construction site with a humanize_kind call and re-capture.
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:1:1:
exported func main() int {
At test:0.vale:3:10:
  return x.foo;
Internal error: Can't apply .foo to Int(IntT { bits: 32 })
"#,
    );
}

#[test]
fn reports_when_rsa_dot_member_is_not_digit() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.arrays.*;\n",
        "import v.builtins.drop.*;\n",
        "exported func main() int {\n",
        "  a = Array<mut, int>(3);\n",
        "  return a.foo;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::RangedInternalErrorT { message, .. } if message.contains("Array has no member") => {}
        other => panic!("expected RangedInternalErrorT 'Array has no member', got {:?}", other),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:3:1:
exported func main() int {
At test:0.vale:5:10:
  return a.foo;
Internal error: Array has no member named foo
"#,
    );
}

#[test]
fn reports_when_ssa_dot_member_is_not_digit() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "exported func main() int {\n",
        "  a = #[#](1, 2, 3);\n",
        "  return a.foo;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::RangedInternalErrorT { message, .. } if message.contains("Sequence has no member") => {}
        other => panic!("expected RangedInternalErrorT 'Sequence has no member', got {:?}", other),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:1:1:
exported func main() int {
At test:0.vale:3:10:
  return a.foo;
Internal error: Sequence has no member named foo
"#,
    );
}

#[test]
fn reports_when_if_branches_have_different_kinds() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "exported func main() int {\n",
        "  x = if true { 5 } else { 6.0 };\n",
        "  return 7;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::CantReconcileBranchesResults { .. } => {}
        _other => panic!("expected CantReconcileBranchesResults"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:1:1:
exported func main() int {
At test:0.vale:2:7:
  x = if true { 5 } else { 6.0 };
If branches return different types: i32 and float
"#,
    );
}

#[test]
fn reports_when_if_condition_isnt_boolean() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "exported func main() int { if 3 { return 5; } else { return 7; } }";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::IfConditionIsntBoolean { .. } => {}
        _other => panic!("expected IfConditionIsntBoolean"),
    }
    // TODO: humanize IfConditionIsntBoolean uses Debug-format on CoordT; replace with humanize_templata and re-capture.
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:1:1:
exported func main() int { if 3 { return 5; } else { return 7; } }
At test:0.vale:1:31:
exported func main() int { if 3 { return 5; } else { return 7; } }
If condition should be a bool, but was: CoordT { ownership: Share, region: RegionT { region: Default }, kind: Int(IntT { bits: 32 }) }
"#,
    );
}

#[test]
fn reports_when_mutating_after_moving() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "struct Weapon { ammo! int; }\n",
        "struct Marine { weapon! Weapon; }\n",
        "exported func main() int {\n",
        "  m = Marine(Weapon(7));\n",
        "  newWeapon = Weapon(10);\n",
        "  set m.weapon = newWeapon;\n",
        "  set newWeapon.ammo = 11;\n",
        "  return 42;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::CantUseUnstackifiedLocal { local_id: IVarNameT::CodeVar(CodeVarNameT { name: StrI("newWeapon"), .. }), .. } => {}
        _other => panic!("expected CantUseUnstackifiedLocal"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:7:7:
  set newWeapon.ammo = 11;
Can't use local that was already moved: newWeapon
"#,
    );
}

#[test]
fn tests_export_struct_twice() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "exported struct Moo { }\n",
        "export Moo as Bork;\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::TypeExportedMultipleTimes { exports, .. } => {
            assert_eq!(exports.len(), 2);
        }
        _ => panic!("Expected TypeExportedMultipleTimes"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:1:1:
exported struct Moo { }
Type exported multiple times:
  test:0.vale:1:1: exported struct Moo { }
  test:0.vale:2:1: export Moo as Bork;
"#,
    );
}

#[test]
fn reports_when_reading_after_moving() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "struct Weapon { ammo! int; }\n",
        "struct Marine { weapon! Weapon; }\n",
        "exported func main() int {\n",
        "  m = Marine(Weapon(7));\n",
        "  newWeapon = Weapon(10);\n",
        "  set m.weapon = newWeapon;\n",
        "  println(newWeapon.ammo);\n",
        "  return 42;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::CantUseUnstackifiedLocal { local_id: IVarNameT::CodeVar(CodeVarNameT { name: StrI("newWeapon"), .. }), .. } => {}
        _other => panic!("expected CantUseUnstackifiedLocal"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:7:11:
  println(newWeapon.ammo);
Can't use local that was already moved: newWeapon
"#,
    );
}

#[test]
fn reports_when_moving_from_inside_a_while() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "struct Marine { ammo int; }\n",
        "exported func main() int {\n",
        "  m = Marine(7);\n",
        "  while (false) {\n",
        "    drop(m);\n",
        "  }\n",
        "  return 42;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::CantUnstackifyOutsideLocalFromInsideWhile { local_id: IVarNameT::CodeVar(CodeVarNameT { name: StrI("m"), .. }), .. } => {}
        _other => panic!("expected CantUnstackifyOutsideLocalFromInsideWhile"),
    }
    // TODO: humanize CantUnstackifyOutsideLocalFromInsideWhile uses Debug-format on local_id; replace with humanize_name and re-capture.
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r##"At test:0.vale:2:1:
exported func main() int {
At test:0.vale:4:3:
  while (false) {
Can't move a local (CodeVar(CodeVarNameT { name: "m" })) from inside a while loop.
"##,
    );
}

#[test]
fn cant_subscript_non_subscriptable_type() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "struct Weapon { ammo! int; }\n",
        "exported func main() int {\n",
        "  weapon = Weapon(10);\n",
        "  return weapon[42];\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::CannotSubscriptT {
            tyype: KindT::Struct(StructTT {
                id: IdT {
                    local_name: INameT::Struct(StructNameT {
                        template: IStructTemplateNameT::StructTemplate(StructTemplateNameT {
                            human_name: StrI("Weapon"), ..
                        }),
                        template_args: &[],
                        ..
                    }),
                    ..
                },
                ..
            }),
            ..
        } => {}
        _other => panic!("expected CannotSubscriptT for Weapon struct"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:2:1:
exported func main() int {
At test:0.vale:4:10:
  return weapon[42];
Cannot subscript type: Weapon!
"#,
    );
}

#[test]
fn humanize_errors() {

    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let typing_interner = TypingInterner::new(&typing_bump);

    let tz_code_loc = CodeLocationS::test_zero(&scout_arena);
    let tz = RangeS::test_zero(&scout_arena);
    let tz_slice: &[RangeS] = typing_bump.alloc_slice_copy(&[tz]);
    let test_tld = scout_arena.intern_package_coordinate(scout_arena.intern_str("test"), &[]);

    let filenames_and_sources = FileCoordinateMap::test(&scout_arena, "blah blah blah\nblah blah blah".to_string());
    let humanize_pos = |x| humanize_pos_code_map(&filenames_and_sources, &x);
    let lines_between = |x, y| lines_between(&filenames_and_sources, &x, &y);
    let line_range_containing = |x| line_range_containing(&filenames_and_sources, &x);
    let line_containing = |x| line_containing(&filenames_and_sources, &x);

    let firefly_struct_template_name = typing_interner.intern_struct_template_name(
        StructTemplateNameT { human_name: scout_arena.intern_str("Firefly")});
    let firefly_struct_name = typing_interner.intern_struct_name(
        StructNameValT { template: IStructTemplateNameT::StructTemplate(firefly_struct_template_name), template_args: &[] });
    let firefly_id = typing_interner.intern_id(IdValT {
        package_coord: test_tld, init_steps: &[], local_name: INameT::Struct(firefly_struct_name),
    });
    let firefly_tt = typing_interner.intern_struct_tt(StructTTValT { id: *firefly_id });
    let firefly_kind = KindT::Struct(firefly_tt);
    let firefly_coord = CoordT { ownership: OwnershipT::Own, region: RegionT { region: IRegionT::Default }, kind: firefly_kind };

    let serenity_struct_template_name = typing_interner.intern_struct_template_name(
        StructTemplateNameT { human_name: scout_arena.intern_str("Serenity")});
    let serenity_struct_name = typing_interner.intern_struct_name(
        StructNameValT { template: IStructTemplateNameT::StructTemplate(serenity_struct_template_name), template_args: &[] });
    let serenity_id = typing_interner.intern_id(IdValT {
        package_coord: test_tld, init_steps: &[], local_name: INameT::Struct(serenity_struct_name),
    });
    let serenity_tt = typing_interner.intern_struct_tt(StructTTValT { id: *serenity_id });
    let serenity_kind = KindT::Struct(serenity_tt);
    let serenity_coord = CoordT { ownership: OwnershipT::Own, region: RegionT { region: IRegionT::Default }, kind: serenity_kind };

    let ispaceship_interface_template_name = typing_interner.intern_interface_template_name(
        InterfaceTemplateNameT { human_namee: scout_arena.intern_str("ISpaceship")});
    let ispaceship_interface_name = typing_interner.intern_interface_name(
        InterfaceNameValT { template: ispaceship_interface_template_name, template_args: &[] });
    let ispaceship_id = typing_interner.intern_id(IdValT {
        package_coord: test_tld, init_steps: &[], local_name: INameT::Interface(ispaceship_interface_name),
    });
    let ispaceship_tt = typing_interner.intern_interface_tt(InterfaceTTValT { id: *ispaceship_id });
    let ispaceship_kind = KindT::Interface(ispaceship_tt);

    let unrelated_struct_template_name = typing_interner.intern_struct_template_name(
        StructTemplateNameT { human_name: scout_arena.intern_str("Spoon")});
    let unrelated_struct_name = typing_interner.intern_struct_name(
        StructNameValT { template: IStructTemplateNameT::StructTemplate(unrelated_struct_template_name), template_args: &[] });
    let unrelated_id = typing_interner.intern_id(IdValT {
        package_coord: test_tld, init_steps: &[], local_name: INameT::Struct(unrelated_struct_name),
    });
    let unrelated_tt = typing_interner.intern_struct_tt(StructTTValT { id: *unrelated_id });
    let unrelated_kind = KindT::Struct(unrelated_tt);

    let myfunc_template_name = typing_interner.intern_function_template_name(
        FunctionTemplateNameT { human_name: scout_arena.intern_str("myFunc"), code_location: tz_code_loc});
    let firefly_func_name = typing_interner.intern_function_name(
        FunctionNameValT { template: myfunc_template_name, template_args: &[], parameters: &[firefly_coord] });
    let firefly_signature_id = typing_interner.intern_id(IdValT {
        package_coord: test_tld, init_steps: &[], local_name: INameT::Function(firefly_func_name),
    });
    let firefly_signature = typing_interner.intern_signature(
        SignatureValT { id: IdValT { package_coord: test_tld, init_steps: &[], local_name: INameT::Function(firefly_func_name) } });

    let export_template_name = typing_interner.intern_export_template_name(
        ExportTemplateNameT { code_loc: tz_code_loc});
    let export_name = typing_interner.intern_export_name(
        ExportNameT { template: export_template_name, region: RegionT { region: IRegionT::Default } });
    let firefly_export_id = typing_interner.intern_id(IdValT {
        package_coord: test_tld, init_steps: &[], local_name: INameT::Export(export_name),
    });
    let firefly_export = KindExportT { range: tz, tyype: firefly_kind, id: *firefly_export_id, exported_name: scout_arena.intern_str("Firefly") };
    let serenity_export_id = typing_interner.intern_id(IdValT {
        package_coord: test_tld, init_steps: &[], local_name: INameT::Export(export_name),
    });
    let serenity_export = KindExportT { range: tz, tyype: firefly_kind, id: *serenity_export_id, exported_name: scout_arena.intern_str("Serenity") };
    let exports_slice: &[KindExportT] = typing_bump.alloc_slice_fill_iter([firefly_export, serenity_export].into_iter());

    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CouldntFindTypeT { range: tz_slice, name: scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: scout_arena.intern_str("Spaceship") })) }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CouldntFindFunctionToCallT { range: tz_slice, fff: FindFunctionFailure {
            name: scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: scout_arena.intern_str("someFunc") })),
            args: &[], rejected_callee_to_reason: &[],
        } }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CouldntFindFunctionToCallT { range: tz_slice, fff: FindFunctionFailure {
            name: scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: scout_arena.intern_str("") })),
            args: &[], rejected_callee_to_reason: &[],
        } }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CannotSubscriptT { range: tz_slice, tyype: firefly_kind }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CouldntFindIdentifierToLoadT { range: tz_slice, name: scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: scout_arena.intern_str("spaceship") })) }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CouldntFindMemberT { range: tz_slice, member_name: "hp" }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::BodyResultDoesntMatch {
            range: tz_slice,
            function_name: IFunctionDeclarationNameS::FunctionName(FunctionNameS {
                name: scout_arena.intern_str("myFunc"),
                code_location: tz_code_loc,
            }),
            expected_return_type: firefly_coord,
            result_type: serenity_coord,
        }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CouldntConvertForReturnT { range: tz_slice, expected_type: firefly_coord, actual_type: serenity_coord }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CouldntConvertForMutateT { range: tz_slice, expected_type: firefly_coord, actual_type: serenity_coord }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CouldntConvertForMutateT { range: tz_slice, expected_type: firefly_coord, actual_type: serenity_coord }).is_empty());
    let hp_var_name: &CodeVarNameT = typing_bump.alloc(CodeVarNameT { name: scout_arena.intern_str("hp")});
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CantMoveOutOfMemberT { range: tz_slice, name: IVarNameT::CodeVar(hp_var_name) }).is_empty());
    let firefly_var_name: &CodeVarNameT = typing_bump.alloc(CodeVarNameT { name: scout_arena.intern_str("firefly")});
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CantUseUnstackifiedLocal { range: tz_slice, local_id: IVarNameT::CodeVar(firefly_var_name) }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CantUnstackifyOutsideLocalFromInsideWhile { range: tz_slice, local_id: IVarNameT::CodeVar(firefly_var_name) }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::FunctionAlreadyExists { old_function_range: tz, new_function_range: tz, signature: *firefly_signature_id }).is_empty());
    let bork_var_name: &CodeVarNameT = typing_bump.alloc(CodeVarNameT { name: scout_arena.intern_str("bork")});
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CantMutateFinalMember { range: tz_slice, struct_: *serenity_tt, member_name: IVarNameT::CodeVar(bork_var_name) }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::LambdaReturnDoesntMatchInterfaceConstructor { range: tz_slice }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::IfConditionIsntBoolean { range: tz_slice, actual_type: firefly_coord }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::WhileConditionIsntBoolean { range: tz_slice, actual_type: firefly_coord }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CantImplNonInterface { range: tz_slice, templata: ITemplataT::Kind(typing_bump.alloc(KindTemplataT { kind: firefly_kind })) }).is_empty());
    let spaceship_snapshot_name_s = scout_arena.intern_struct_declaration_name(
        TopLevelStructDeclarationNameS { name: scout_arena.intern_str("SpaceshipSnapshot"), range: tz });
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::ImmStructCantHaveVaryingMember { range: tz_slice, struct_name: INameS::TopLevelStructDeclaration(spaceship_snapshot_name_s), member_name: "fuel" }).is_empty());
    let candidates_slice: &[FailedSolve<_, _, _, _>] = typing_bump.alloc_slice_fill_iter(empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CantDowncastUnrelatedTypes { range: tz_slice, source_kind: ispaceship_kind, target_kind: unrelated_kind, candidates: candidates_slice }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CantDowncastToInterface { range: tz_slice, target_kind: *ispaceship_tt }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::ExportedFunctionDependedOnNonExportedKind { range: tz_slice, paackage: *test_tld, signature: firefly_signature, non_exported_kind: firefly_kind }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::ExportedImmutableKindDependedOnNonExportedKind { range: tz_slice, paackage: *test_tld, exported_kind: serenity_kind, non_exported_kind: firefly_kind }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::ExternFunctionDependedOnNonExportedKind { range: tz_slice, paackage: *test_tld, signature: firefly_signature, non_exported_kind: firefly_kind }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::TypeExportedMultipleTimes { range: tz_slice, paackage: *test_tld, exports: exports_slice }).is_empty());
    let x_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS { name: scout_arena.intern_str("X") }));
    let mut step_conclusions = HashMap::new();
    step_conclusions.insert(x_rune, ITemplataT::Kind(typing_bump.alloc(KindTemplataT { kind: firefly_kind })));
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::TypingPassSolverError { range: tz_slice, failed_solve: FailedSolve {
            steps: vec![Step { complex: false, solved_rules: vec![], added_rules: vec![], conclusions: step_conclusions }],
            conclusions: HashMap::new(),
            unsolved_rules: vec![],
            unsolved_runes: vec![],
            error: ISolverError::RuleError(RuleError { err: ITypingPassSolverError::KindIsNotConcrete { kind: ispaceship_kind }, _phantom: PhantomData }),
        } }).is_empty());
}

#[test]
fn report_when_multiple_types_in_array() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
exported func main() int {
  arr = [#](true, 42);
  return arr.1;
}";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::ArrayElementsHaveDifferentTypes { types, .. } => {
            let types_set: HashSet<CoordT> = types.iter().copied().collect();
            assert_eq!(types_set, HashSet::from([
                CoordT { ownership: OwnershipT::Share, region: RegionT { region: IRegionT::Default }, kind: KindT::Int(IntT::I32) },
                CoordT { ownership: OwnershipT::Share, region: RegionT { region: IRegionT::Default }, kind: KindT::Bool(BoolT) },
            ]));
        }
        _other => panic!("expected ArrayElementsHaveDifferentTypes"),
    }
    // TODO: humanize ArrayElementsHaveDifferentTypes uses Debug-format on each CoordT; replace with humanize_templata and re-capture.
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:2:1:
exported func main() int {
At test:0.vale:3:9:
  arr = [#](true, 42);
Array's elements have different types: CoordT { ownership: Share, region: RegionT { region: Default }, kind: Bool(BoolT) }, CoordT { ownership: Share, region: RegionT { region: Default }, kind: Int(IntT { bits: 32 }) }
"#,
    );
}

#[test]
fn report_when_abstract_method_defined_outside_open_interface() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
import v.builtins.panic.*;
interface IBlah { }
abstract func bork(virtual moo &IBlah);
exported func main() {
  bork(__vbi_panic());
}";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::AbstractMethodOutsideOpenInterface { .. } => {}
        _other => panic!("expected AbstractMethodOutsideOpenInterface"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:4:1:
abstract func bork(virtual moo &IBlah);
At test:0.vale:4:20:
abstract func bork(virtual moo &IBlah);
Open (non-sealed) interfaces can't have abstract methods defined outside the interface.
"#,
    );
}

#[test]
fn report_when_imm_struct_has_varying_member() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r#"
struct Spaceship imm {
  name! str;
  numWings int;
}
exported func main() {
  ship = Spaceship("Serenity", 2);
  println(ship.name);
}"#;
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::ImmStructCantHaveVaryingMember { .. } => {}
        _other => panic!("expected ImmStructCantHaveVaryingMember"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r##"At test:0.vale:3:3:
  name! str;
Immutable struct ("Spaceship") cannot have varying member ("name").
"##,
    );
}

#[test]
fn report_imm_mut_mismatch_for_generic_type() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
struct MyImmContainer<T Ref> imm
where func drop(T)void { value T; }
struct MyMutStruct { }
exported func main() { x = MyImmContainer<MyMutStruct>(MyMutStruct()); }";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::ImmStructCantHaveMutableMember { .. } => {}
        _other => panic!("expected ImmStructCantHaveMutableMember"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r##"At test:0.vale:3:26:
where func drop(T)void { value T; }
Immutable struct ("MyImmContainer") cannot have mutable member ("value").
"##,
    );
}

#[test]
fn tests_stamping_a_struct_and_its_implemented_interface_from_a_function_param() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.panicutils.*;\n",
        "import v.builtins.drop.*;\n",
        "import panicutils.*;\n",
        "sealed interface MyOption<T Ref> where func drop(T)void { }\n",
        "struct MySome<T Ref> where func drop(T)void { value T; }\n",
        "impl<T> MyOption<T> for MySome<T> where func drop(T)void;\n",
        "func moo(a MySome<int>) { }\n",
        "exported func main() { moo(__pretend<MySome<int>>()); }\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let interface_template_name = compile.typing_interner.intern_interface_template_name(
        InterfaceTemplateNameT {
            human_namee: scout_arena.intern_str("MyOption"),
        });
    let struct_template_name = StructTemplateNameT {
        human_name: scout_arena.intern_str("MySome"),
    };

    let coutputs = compile.expect_compiler_outputs();

    let interface = coutputs.lookup_interface_by_template_name(interface_template_name);
    let my_struct = coutputs.lookup_struct_by_template_name(struct_template_name);

    coutputs.lookup_impl(my_struct.instantiated_citizen.id, interface.instantiated_interface.id);
}

#[test]
fn report_when_imm_contains_varying_member() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
struct Spaceship imm {
  name! str;
  numWings int;
}";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::ImmStructCantHaveVaryingMember { struct_name: INameS::TopLevelStructDeclaration(TopLevelStructDeclarationNameS { name: StrI("Spaceship"), .. }), member_name: "name", .. } => {}
        _other => panic!("expected ImmStructCantHaveVaryingMember for Spaceship.name"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r##"At test:0.vale:3:3:
  name! str;
Immutable struct ("Spaceship") cannot have varying member ("name").
"##,
    );
}

#[test]
fn test_imm_array() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.panic.*;\n",
        "import v.builtins.drop.*;\n",
        "export #[]int as ImmArrInt;\n",
        "exported func main(arr #[]int) {\n",
        "  __vbi_panic();\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    match main.header.params[0].tyype.kind {
        KindT::RuntimeSizedArray(rsa) => {
            match rsa.name.local_name {
                INameT::RuntimeSizedArray(rsan) => {
                    assert_eq!(rsan.arr.mutability, ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }));
                }
                _ => panic!("Expected RuntimeSizedArray local_name"),
            }
        }
        _ => panic!("Expected RuntimeSizedArray kind"),
    }
}

#[test]
fn tests_calling_an_abstract_function() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = include_str!("../../tests/programs/genericvirtuals/callingAbstract.vale");
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    coutputs.functions.iter().find(|f| {
        matches!(f.header.id.local_name,
            INameT::Function(
                FunctionNameT {
                    template: FunctionTemplateNameT { human_name, .. },
                    ..
                }
            )
            if human_name == "doThing"
        ) && f.header.get_abstract_interface().is_some()
    }).unwrap();
}

#[test]
fn test_struct_default_generic_argument_in_type() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "struct MyHashSet<K Ref, H Int = 5> { }\n",
        "struct MyStruct {\n",
        "  x MyHashSet<bool>();\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let moo = coutputs.lookup_struct_by_str("MyStruct");
    let tyype = collect_only_tnode!(
        NodeRefT::StructDefinition(moo),
        NodeRefT::ReferenceMemberType(rmt) => Some(rmt.reference)
    );
    match tyype {
        CoordT {
            ownership: OwnershipT::Own,
            kind: KindT::Struct(StructTT {
                id: IdT {
                    local_name: INameT::Struct(StructNameT {
                        template: IStructTemplateNameT::StructTemplate(
                            StructTemplateNameT {
                                human_name: StrI("MyHashSet"),
                                ..
                            }
                        ),
                        template_args: [
                            ITemplataT::Coord(
                                CoordTemplataT {
                                    coord: CoordT { ownership: OwnershipT::Share, kind: KindT::Bool(_), .. }
                                }
                            ),
                            ITemplataT::Integer(5),
                        ],
                        ..
                    }),
                    ..
                },
                ..
            }),
            ..
        } => {}
        _ => panic!("unexpected tyype"),
    }
}

#[test]
fn lock_weak_member() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.opt.*;\n",
        "import v.builtins.weak.*;\n",
        "import v.builtins.logic.*;\n",
        "import v.builtins.drop.*;\n",
        "import panicutils.*;\n",
        "import printutils.*;\n",
        "\n",
        "struct Base {\n",
        "  name str;\n",
        "}\n",
        "struct Spaceship {\n",
        "  name str;\n",
        "  origin &&Base;\n",
        "}\n",
        "func printShipBase(ship &Spaceship) {\n",
        "  maybeOrigin = lock(ship.origin);\n",
        "  if (not maybeOrigin.isEmpty()) {\n",
        "    o = maybeOrigin.get();\n",
        "    println(\"Ship base: \" + o.name);\n",
        "  } else {\n",
        "    println(\"Ship base unknown!\");\n",
        "  }\n",
        "}\n",
        "exported func main() {\n",
        "  base = Base(\"Zion\");\n",
        "  ship = Spaceship(\"Neb\", &&base);\n",
        "  printShipBase(&ship);\n",
        "  (base).drop();\n",
        "  printShipBase(&ship);\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn tests_destructuring_shared_doesnt_compile_to_destroy() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "\n",
        "struct Vec3i imm {\n",
        "  x int;\n",
        "  y int;\n",
        "  z int;\n",
        "}\n",
        "\n",
        "exported func main() int {\n",
        "\t Vec3i[x, y, z] = Vec3i(3, 4, 5);\n",
        "  return y;\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    let destroys = collect_where_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Destroy(_) => Some(())
    );
    assert_eq!(destroys.len(), 0);
}

#[test]
fn generates_free_function_for_imm_struct() {
    let code = r#"
        struct Vec3i imm {
          x int;
          y int;
          z int;
        }
      "#;
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn reports_when_exported_ssa_depends_on_non_exported_element() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "export [#5]<imm>Raza as RazaArray;\nstruct Raza imm { }";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::ExportedImmutableKindDependedOnNonExportedKind { .. } => {}
        _other => panic!("expected ExportedImmutableKindDependedOnNonExportedKind"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:1:1:
export [#5]<imm>Raza as RazaArray;
Exported kind StaticArray<5, imm, final, Raza> depends on kind Raza that wasn't exported from package test
"#,
    );
}

#[test]
fn reports_when_exported_rsa_depends_on_non_exported_element() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "export []<imm>Raza as RazaArray;\nstruct Raza imm { }";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err().expect("expected Err, got Ok");
    match &err {
        ICompileErrorT::ExportedImmutableKindDependedOnNonExportedKind { .. } => {}
        _other => panic!("expected ExportedImmutableKindDependedOnNonExportedKind"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:1:1:
export []<imm>Raza as RazaArray;
Exported kind Array<imm, Raza> depends on kind Raza that wasn't exported from package test
"#,
    );
}

#[test]
fn test_make_array() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r#"
import v.builtins.arith.*;
import array.make.*;
import v.builtins.arrays.*;
import v.builtins.drop.*;

exported func main() int {
  a = MakeArray<int>(11, {_});
  return len(&a);
}
"#;
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn test_array_push_pop_len_capacity_drop() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.arrays.*;\n",
        "import v.builtins.drop.*;\n",
        "\n",
        "exported func main() void {\n",
        "  arr = Array<mut, int>(9);\n",
        "  arr.push(420);\n",
        "  arr.push(421);\n",
        "  arr.push(422);\n",
        "  arr.len();\n",
        "  arr.capacity();\n",
        "  // implicit drop with pops\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

#[test]
fn upcast_generic() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.drop.*;\n",
        "\n",
        "interface IShip {}\n",
        "\n",
        "struct Raza { fuel int; }\n",
        "impl IShip for Raza;\n",
        "\n",
        "func doUpcast<T>(x T) IShip\n",
        "where implements(T, IShip) {\n",
        "  i IShip = x;\n",
        "  return i;\n",
        "}\n",
        "\n",
        "exported func main() {\n",
        "  doUpcast(Raza(42));\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();

    let do_upcast = coutputs.lookup_function_by_str("doUpcast");

    collect_only_tnode!(
        NodeRefT::FunctionDefinition(do_upcast),
        NodeRefT::Upcast(u) => {
            match u.inner_expr.result().coord.kind {
                KindT::KindPlaceholder(_) => {}
                other => panic!("sourceExpr.result.coord.kind: {:?}", other),
            }
            match u.target_super_kind {
                ISuperKindTT::Interface(InterfaceTT {
                    id: IdT {
                        init_steps: &[],
                        local_name: INameT::Interface(InterfaceNameT {
                            template: InterfaceTemplateNameT { human_namee: StrI("IShip"), .. },
                            template_args: &[],
                            ..
                        }),
                        ..
                    },
                    ..
                }) => {}
                other => panic!("targetSuperKind: {:?}", other),
            }
            Some(())
        }
    );
}

#[test]
fn downcast_function_rrbfs() {
    // Here we had something interesting happen: the complex solve had a race with the thing that
    // populates identifying runes.
    // Populating identifying runes only happens after the solver has done as much as it possibly
    // can... but the solver sometimes takes a leap (as part of CSALR, SMCMST) to figure out the best type
    // to meet some requirements.
    // The solution was to make it only do that leap when solving call sites.
    // See RRBFS.
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "\n",
        "#!DeriveInterfaceDrop\n",
        "sealed interface Result<OkType Ref, ErrType Ref> { }\n",
        "\n",
        "#!DeriveStructDrop\n",
        "struct Ok<OkType Ref, ErrType Ref> { value OkType; }\n",
        "\n",
        "impl<OkType, ErrType> Result<OkType, ErrType> for Ok<OkType, ErrType>;\n",
        "\n",
        "#!DeriveStructDrop\n",
        "struct Err<OkType Ref, ErrType Ref> { value ErrType; }\n",
        "\n",
        "impl<OkType, ErrType> Result<OkType, ErrType> for Err<OkType, ErrType>;\n",
        "\n",
        "\n",
        "extern(\"vale_as_subtype\")\n",
        "func as<SubType Ref, SuperType Ref>(left &SuperType) Result<&SubType, &SuperType>\n",
        "where implements(SubType, SuperType);\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();

    {

        let as_funcs: Vec<_> = coutputs.functions.iter().filter(|f| {
            matches!(f.header.id.local_name, INameT::Function(FunctionNameT {
                template: FunctionTemplateNameT { human_name: StrI("as"), .. },
                parameters: [CoordT { ownership: OwnershipT::Borrow, .. }],
                ..
            }))
        }).copied().collect();
        let as_func = expect_1(&as_funcs);
        let as_ = collect_only_tnode!(
            NodeRefT::FunctionDefinition(as_func),
            NodeRefT::AsSubtype(as_) => Some(as_)
        );
        let source_expr = as_.source_expr;
        let target_subtype = as_.target_type;
        let result_opt_type = as_.result_result_type;
        let ok_constructor = as_.ok_constructor;
        let err_constructor = as_.err_constructor;

        match source_expr.result().coord {
            CoordT {
                ownership: OwnershipT::Borrow,
                kind: KindT::KindPlaceholder(KindPlaceholderT {
                    id: IdT {
                        init_steps: [INameT::FunctionTemplate(FunctionTemplateNameT { human_name: StrI("as"), .. })],
                        local_name: INameT::KindPlaceholder(KindPlaceholderNameT {
                            template: KindPlaceholderTemplateNameT { index: 1, .. },
                        }),
                        ..
                    },
                    ..
                }),
                ..
            } => {}
            //case CoordT(BorrowT, InterfaceTT(FullNameT(_, Vector(), InterfaceNameT(InterfaceTemplateNameT(StrI("IShip")), Vector())))) =>
            other => panic!("sourceExpr.result.coord: {:?}", other),
        }
        match target_subtype.kind {
            KindT::KindPlaceholder(KindPlaceholderT {
                id: IdT {
                    init_steps: [INameT::FunctionTemplate(FunctionTemplateNameT { human_name: StrI("as"), .. })],
                    local_name: INameT::KindPlaceholder(KindPlaceholderNameT {
                        template: KindPlaceholderTemplateNameT { index: 0, .. },
                    }),
                    ..
                },
                ..
            }) => {}
            KindT::Struct(StructTT {
                id: IdT {
                    init_steps: &[],
                    local_name: INameT::Struct(StructNameT {
                        template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("Raza"), .. }),
                        template_args: &[],
                        ..
                    }),
                    ..
                },
                ..
            }) => {}
            other => panic!("targetSubtype.kind: {:?}", other),
        }
        let (first_generic_arg, second_generic_arg) = match result_opt_type {
            CoordT {
                ownership: OwnershipT::Own,
                kind: KindT::Interface(InterfaceTT {
                    id: IdT {
                        init_steps: &[],
                        local_name: INameT::Interface(InterfaceNameT {
                            template: InterfaceTemplateNameT { human_namee: StrI("Result"), .. },
                            template_args: [first, second],
                            ..
                        }),
                        ..
                    },
                    ..
                }),
                ..
            } => (first, second),
            other => panic!("resultOptType: {:?}", other),
        };
        // They should both be pointers, since we dont really do borrows in structs yet
        match first_generic_arg {
            ITemplataT::Coord(CoordTemplataT {
                coord: CoordT {
                    ownership: OwnershipT::Borrow,
                    kind: KindT::KindPlaceholder(KindPlaceholderT {
                        id: IdT {
                            init_steps: [INameT::FunctionTemplate(FunctionTemplateNameT { human_name: StrI("as"), .. })],
                            local_name: INameT::KindPlaceholder(KindPlaceholderNameT {
                                template: KindPlaceholderTemplateNameT { index: 0, .. },
                            }),
                            ..
                        },
                        ..
                    }),
                    ..
                }
            }) => {}
            other => panic!("firstGenericArg: {:?}", other),
        }
        match second_generic_arg {
            ITemplataT::Coord(CoordTemplataT {
                coord: CoordT {
                    ownership: OwnershipT::Borrow,
                    kind: KindT::KindPlaceholder(KindPlaceholderT {
                        id: IdT {
                            init_steps: [INameT::FunctionTemplate(FunctionTemplateNameT { human_name: StrI("as"), .. })],
                            local_name: INameT::KindPlaceholder(KindPlaceholderNameT {
                                template: KindPlaceholderTemplateNameT { index: 1, .. },
                            }),
                            ..
                        },
                        ..
                    }),
                    ..
                }
            }) => {}
            other => panic!("secondGenericArg: {:?}", other),
        }
        assert_eq!(ok_constructor.id.local_name.parameters()[0], target_subtype);
        assert_eq!(err_constructor.id.local_name.parameters()[0], source_expr.result().coord);
    }
}

// AFTERM: doublecheck this
#[test]
fn downcast_with_as() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.as.*;\n",
        "import v.builtins.logic.*;\n",
        "import v.builtins.drop.*;\n",
        "\n",
        "interface IShip {}\n",
        "\n",
        "struct Raza { fuel int; }\n",
        "impl IShip for Raza;\n",
        "\n",
        "exported func main() {\n",
        "  ship IShip = Raza(42);\n",
        "  ship.as<Raza>();\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();

    {

        let main_func = coutputs.lookup_function_by_str("main");
        let (as_prototype, as_arg) = collect_only_tnode!(
            NodeRefT::FunctionDefinition(main_func),
            NodeRefT::FunctionCall(c @ FunctionCallTE {
                callable: PrototypeT {
                    id: IdT {
                        local_name: INameT::Function(FunctionNameT {
                            template: FunctionTemplateNameT { human_name: StrI("as"), .. },
                            ..
                        }),
                        init_steps: &[],
                        ..
                    },
                    ..
                },
                args: [_],
                ..
            }) => Some((c.callable, c.args[0]))
        );

        let (as_prototype_template_args, as_prototype_params, as_prototype_return) =
            match as_prototype.id.local_name {
                INameT::Function(fn_name) => (fn_name.template_args, fn_name.parameters, as_prototype.return_type),
                other => panic!("expected Function name: {:?}", other),
            };

        match as_prototype_template_args {
            [
                ITemplataT::Coord(CoordTemplataT { coord: CoordT {
                    ownership: OwnershipT::Own,
                    kind: KindT::Struct(StructTT {
                        id: IdT {
                            init_steps: &[],
                            local_name: INameT::Struct(StructNameT {
                                template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("Raza"), .. }),
                                template_args: &[],
                                ..
                            }),
                            ..
                        },
                        ..
                    }),
                    ..
                }}),
                ITemplataT::Coord(CoordTemplataT { coord: CoordT {
                    ownership: OwnershipT::Own,
                    kind: KindT::Interface(InterfaceTT {
                        id: IdT {
                            init_steps: &[],
                            local_name: INameT::Interface(InterfaceNameT {
                                template: InterfaceTemplateNameT { human_namee: StrI("IShip"), .. },
                                template_args: &[],
                                ..
                            }),
                            ..
                        },
                        ..
                    }),
                    ..
                }}),
            ] => {}
            other => panic!("asPrototypeTemplateArgs: {:?}", other),
        }
        match as_prototype_params {
            [CoordT {
                ownership: OwnershipT::Borrow,
                kind: KindT::Interface(InterfaceTT {
                    id: IdT {
                        init_steps: &[],
                        local_name: INameT::Interface(InterfaceNameT {
                            template: InterfaceTemplateNameT { human_namee: StrI("IShip"), .. },
                            template_args: &[],
                            ..
                        }),
                        ..
                    },
                    ..
                }),
                ..
            }] => {}
            other => panic!("asPrototypeParams: {:?}", other),
        }
        match as_prototype_return {
            CoordT {
                ownership: OwnershipT::Own,
                kind: KindT::Interface(InterfaceTT {
                    id: IdT {
                        init_steps: &[],
                        local_name: INameT::Interface(InterfaceNameT {
                            template: InterfaceTemplateNameT { human_namee: StrI("Result"), .. },
                            template_args: [
                                ITemplataT::Coord(CoordTemplataT { coord: CoordT {
                                    ownership: OwnershipT::Borrow,
                                    kind: KindT::Struct(StructTT {
                                        id: IdT {
                                            init_steps: &[],
                                            local_name: INameT::Struct(StructNameT {
                                                template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("Raza"), .. }),
                                                template_args: &[],
                                                ..
                                            }),
                                            ..
                                        },
                                        ..
                                    }),
                                    ..
                                }}),
                                ITemplataT::Coord(CoordTemplataT { coord: CoordT {
                                    ownership: OwnershipT::Borrow,
                                    kind: KindT::Interface(InterfaceTT {
                                        id: IdT {
                                            init_steps: &[],
                                            local_name: INameT::Interface(InterfaceNameT {
                                                template: InterfaceTemplateNameT { human_namee: StrI("IShip"), .. },
                                                template_args: &[],
                                                ..
                                            }),
                                            ..
                                        },
                                        ..
                                    }),
                                    ..
                                }}),
                            ],
                            ..
                        }),
                        ..
                    },
                    ..
                }),
                ..
            } => {}
            other => panic!("asPrototypeReturn: {:?}", other),
        }
        match as_arg.result().coord {
            CoordT {
                ownership: OwnershipT::Borrow,
                kind: KindT::Interface(InterfaceTT {
                    id: IdT {
                        init_steps: &[],
                        local_name: INameT::Interface(InterfaceNameT {
                            template: InterfaceTemplateNameT { human_namee: StrI("IShip"), .. },
                            template_args: &[],
                            ..
                        }),
                        ..
                    },
                    ..
                }),
                ..
            } => {}
            other => panic!("asArg.result.coord: {:?}", other),
        }
    }

    {

        let as_funcs: Vec<_> = coutputs.functions.iter().filter(|f| {
            matches!(f.header.id.local_name, INameT::Function(FunctionNameT {
                template: FunctionTemplateNameT { human_name: StrI("as"), .. },
                parameters: [CoordT { ownership: OwnershipT::Borrow, .. }],
                ..
            }))
        }).copied().collect();
        let as_func = expect_1(&as_funcs);
        let as_ = collect_only_tnode!(
            NodeRefT::FunctionDefinition(as_func),
            NodeRefT::AsSubtype(as_) => Some(as_)
        );
        let source_expr = as_.source_expr;
        let target_subtype = as_.target_type;
        let result_opt_type = as_.result_result_type;
        let ok_constructor = as_.ok_constructor;
        let err_constructor = as_.err_constructor;

        match source_expr.result().coord {
            CoordT {
                ownership: OwnershipT::Borrow,
                kind: KindT::KindPlaceholder(KindPlaceholderT {
                    id: IdT {
                        init_steps: [INameT::FunctionTemplate(FunctionTemplateNameT { human_name: StrI("as"), .. })],
                        local_name: INameT::KindPlaceholder(KindPlaceholderNameT {
                            template: KindPlaceholderTemplateNameT { index: 1, .. },
                        }),
                        ..
                    },
                    ..
                }),
                ..
            } => {}
            //case CoordT(BorrowT, InterfaceTT(FullNameT(_, Vector(), InterfaceNameT(InterfaceTemplateNameT(StrI("IShip")), Vector())))) =>
            other => panic!("sourceExpr.result.coord: {:?}", other),
        }
        match target_subtype.kind {
            KindT::KindPlaceholder(KindPlaceholderT {
                id: IdT {
                    init_steps: [INameT::FunctionTemplate(FunctionTemplateNameT { human_name: StrI("as"), .. })],
                    local_name: INameT::KindPlaceholder(KindPlaceholderNameT {
                        template: KindPlaceholderTemplateNameT { index: 0, .. },
                    }),
                    ..
                },
                ..
            }) => {}
            KindT::Struct(StructTT {
                id: IdT {
                    init_steps: &[],
                    local_name: INameT::Struct(StructNameT {
                        template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("Raza"), .. }),
                        template_args: &[],
                        ..
                    }),
                    ..
                },
                ..
            }) => {}
            other => panic!("targetSubtype.kind: {:?}", other),
        }
        match result_opt_type {
            CoordT {
                ownership: OwnershipT::Own,
                kind: KindT::Interface(InterfaceTT {
                    id: IdT {
                        init_steps: &[],
                        local_name: INameT::Interface(InterfaceNameT {
                            template: InterfaceTemplateNameT { human_namee: StrI("Result"), .. },
                            template_args: [
                                ITemplataT::Coord(CoordTemplataT { coord: CoordT {
                                    ownership: OwnershipT::Borrow,
                                    kind: KindT::KindPlaceholder(KindPlaceholderT {
                                        id: IdT {
                                            local_name: INameT::KindPlaceholder(KindPlaceholderNameT {
                                                template: KindPlaceholderTemplateNameT { index: 0, .. },
                                            }),
                                            ..
                                        },
                                        ..
                                    }),
                                    ..
                                }}),
                                ITemplataT::Coord(CoordTemplataT { coord: CoordT {
                                    ownership: OwnershipT::Borrow,
                                    kind: KindT::KindPlaceholder(KindPlaceholderT {
                                        id: IdT {
                                            local_name: INameT::KindPlaceholder(KindPlaceholderNameT {
                                                template: KindPlaceholderTemplateNameT { index: 1, .. },
                                            }),
                                            ..
                                        },
                                        ..
                                    }),
                                    ..
                                }}),
                            ],
                            ..
                        }),
                        ..
                    },
                    ..
                }),
                ..
            } => {}
            other => panic!("resultOptType: {:?}", other),
        }
        assert_eq!(ok_constructor.id.local_name.parameters()[0], target_subtype);
        assert_eq!(err_constructor.id.local_name.parameters()[0], source_expr.result().coord);
    }
}

#[test]
fn closure_using_parent_function_s_bound() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.arith.*;\n",
        "\n",
        "func genFunc<T>(a &T) T\n",
        "where func +(&T, &T)T {\n",
        "  { a + a }()\n",
        "}\n",
        "exported func main() int {\n",
        "  genFunc(7)\n",
        "}\n",
    );
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
fn test_struct_default_generic_argument_in_call() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "struct MyHashSet<K Ref, H Int = 5> { }\n",
        "func moo() {\n",
        "  x = MyHashSet<bool>();\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let moo = coutputs.lookup_function_by_str("moo");
    let variable = collect_only_tnode!(
        NodeRefT::FunctionDefinition(moo),
        NodeRefT::LetNormal(let_normal) => Some(let_normal.variable)
    );
    match variable.coord() {
        CoordT {
            ownership: OwnershipT::Own,
            kind: KindT::Struct(StructTT {
                id: IdT {
                    local_name: INameT::Struct(StructNameT {
                        template: IStructTemplateNameT::StructTemplate(
                            StructTemplateNameT {
                                human_name: StrI("MyHashSet"),
                                ..
                            }
                        ),
                        template_args: [
                            ITemplataT::Coord(
                                CoordTemplataT {
                                    coord: CoordT { ownership: OwnershipT::Share, kind: KindT::Bool(_), .. }
                                }
                            ),
                            ITemplataT::Integer(5),
                        ],
                        ..
                    }),
                    ..
                },
                ..
            }),
            ..
        } => {}
        _ => panic!("unexpected coord"),
    }
}

#[test]
fn structs_can_resolve_other_structs_instantiation_bound_arguments() {
    // The definition of Marine<T> was trying to resolve the existence of func drop(int)void.
    // Unfortunately, we don't have an overload index at the time of struct definitions yet, that comes later when
    // we define the functions.
    // Normally this wouldnt be a problem as we can usually use things before we compile them, we just use the templata
    // and solve the whole thing on our own, don't even need to know if it's been compiled yet.
    // However, now that we want to rely on the overload index, and the overload index doesn't exist until we compile
    // the functions, we rely on things being compiled before we use them, hence this problem.
    // The solution is to delay resolving function bounds until functions are compiled, see MCFBRBF.
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "import v.builtins.drop.*;\n",
        "\n",
        "struct XNone<T> where func drop(T)void { }\n",
        "\n",
        "// This function will try to do a resolve for func drop(int)void.\n",
        "struct Marine { weapon XNone<int>; }\n",
        "\n",
        "exported func main() {\n",
        "  m = Marine(XNone<int>());\n",
        "}\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

