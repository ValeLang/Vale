use bumpalo::Bump;
use crate::interner::StrI;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::postparsing::names::{CodeNameS, FunctionNameS, IFunctionDeclarationNameS, IImpreciseNameValS};
use crate::scout_arena::ScoutArena;
use crate::typing::ast::expressions::{AddressExpressionTE, ConstantIntTE, LocalLookupTE, MutateTE, ReferenceExpressionTE, ReferenceMemberLookupTE};
use crate::typing::compiler_error_humanizer::humanize;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::typing::env::function_environment_t::{ILocalVariableT, ReferenceLocalVariableT};
use crate::typing::names::names::{CodeVarNameT, FunctionNameValT, FunctionTemplateNameT, IdT, IdValT, INameT, IStructTemplateNameT, IVarNameT, RawArrayNameT, StaticSizedArrayNameT, StructNameValT, StructTemplateNameT};
use crate::typing::templata::templata::{ITemplataT, KindTemplataT, MutabilityTemplataT, VariabilityTemplataT};
use crate::typing::test::compiler_test_compilation::compiler_test_compilation;
use crate::typing::test::humanize_helper::{assert_humanized_eq, humanize_compile_error};
use crate::typing::types::types::{CoordT, IntT, IRegionT, KindT, MutabilityT, OwnershipT, RegionT, StaticSizedArrayTT, StructTTValT, VariabilityT};
use crate::typing::typing_interner::TypingInterner;
use crate::utils::code_hierarchy::{self, FileCoordinateMap, IPackageResolver, PackageCoordinate};
use crate::utils::range::{CodeLocationS, RangeS};
use crate::utils::source_code_utils::{humanize_pos_code_map, line_containing, line_range_containing, lines_between};
use std::collections::HashMap;
use crate::typing::test::traverse::NodeRefT;
use crate::typing::names::names::StructNameT;
use crate::typing::overload_resolver::FindFunctionFailure;
use crate::builtins::builtins::get_embedded_modulized_code_map;
use crate::collect_only_tnode;
use std::marker::PhantomData;


pub fn read_code_from_resource(resource_filename: &str) -> String {
  panic!("Unimplemented: read_code_from_resource");
}

#[test]
fn test_mutating_a_local_var() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

exported func main() {a = 3; set a = 4; }
";
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
        NodeRefT::Mutate(MutateTE {
            destination_expr: AddressExpressionTE::LocalLookup(LocalLookupTE {
                local_variable: ILocalVariableT::Reference(ReferenceLocalVariableT {
                    name: IVarNameT::CodeVar(CodeVarNameT { name: StrI("a"), .. }),
                    variability: VariabilityT::Varying,
                    ..
                }),
                ..
            }),
            source_expr: ReferenceExpressionTE::ConstantInt(ConstantIntTE {
                value: ITemplataT::Integer(4),
                ..
            }),
        }) => Some(())
    );

    let lookup: &LocalLookupTE = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::LocalLookup(l) => Some(l)
    );
    let result_coord = lookup.result().coord;
    assert_eq!(result_coord, CoordT { ownership: OwnershipT::Share, region: RegionT { region: IRegionT::Default }, kind: KindT::Int(IntT { bits: 32 }) });
}

#[test]
fn test_mutable_member_permission() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

struct Engine { fuel int; }
struct Spaceship { engine! Engine; }
exported func main() {
  ship = Spaceship(Engine(10));
  set ship.engine = Engine(15);
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");

    let lookup: &ReferenceMemberLookupTE = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::ReferenceMemberLookup(l) => Some(l)
    );
    let result_coord = lookup.result().coord;
    // See RMLRMO, it should result in the same type as the member.
    match result_coord {
        CoordT { ownership: OwnershipT::Own, kind: KindT::Struct(_), .. } => {}
        x => panic!("{:?}", x),
    }
}

#[test]
fn local_set_upcasts() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
import v.builtins.drop.*;

interface IXOption<T Ref> where func drop(T)void { }
struct XSome<T Ref> where func drop(T)void { value T; }
impl<T Ref> IXOption<T> for XSome<T> where func drop(T)void;
struct XNone<T Ref> where func drop(T)void { }
impl<T Ref> IXOption<T> for XNone<T> where func drop(T)void;

exported func main() {
  m IXOption<int> = XNone<int>();
  set m = XSome(6);
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
    let main = coutputs.lookup_function_by_str("main");
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Mutate(MutateTE {
            source_expr: ReferenceExpressionTE::Upcast(_),
            ..
        }) => Some(())
    );
}

#[test]
fn expr_set_upcasts() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
import v.builtins.drop.*;

interface IXOption<T Ref> where func drop(T)void { }
struct XSome<T Ref> where func drop(T)void { value T; }
impl<T Ref> IXOption<T> for XSome<T>;
struct XNone<T Ref> where func drop(T)void { }
impl<T Ref> IXOption<T> for XNone<T>;

struct Marine {
  weapon! IXOption<int>;
}
exported func main() {
  m = Marine(XNone<int>());
  set m.weapon = XSome(6);
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
    let main = coutputs.lookup_function_by_str("main");
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Mutate(MutateTE {
            source_expr: ReferenceExpressionTE::Upcast(_),
            ..
        }) => Some(())
    );
}

#[test]
fn reports_when_we_try_to_mutate_an_imm_struct() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

struct Vec3 imm { x float; y float; z float; }
exported func main() int {
  v = Vec3(3.0, 4.0, 5.0);
  set v.x = 10.0;
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err()
        .unwrap_or_else(|| panic!("expected Err(CantMutateFinalMember), got Ok"));
    match &err {
        ICompileErrorT::CantMutateFinalMember { struct_, member_name, .. } => {
            match struct_.id.local_name {
                INameT::Struct(StructNameT {
                    template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("Vec3"), .. }),
                    template_args: &[],
                    ..
                }) => {}
                _ => panic!("expected Struct(StructTemplateNameT(\"Vec3\"))"),
            }
            match member_name {
                IVarNameT::CodeVar(CodeVarNameT { name: StrI("x"), .. }) => {}
                _ => panic!("expected CodeVarNameT(\"x\")"),
            }
        }
        _ => panic!("expected CantMutateFinalMember"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:4:1:
exported func main() int {
At test:0.vale:6:7:
  set v.x = 10.0;
Cannot mutate final member 'x' of container Vec3
"#,
    );
}

#[test]
fn reports_when_we_try_to_mutate_a_final_member_in_a_struct() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

struct Vec3 { x float; y float; z float; }
exported func main() int {
  v = Vec3(3.0, 4.0, 5.0);
  set v.x = 10.0;
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err()
        .unwrap_or_else(|| panic!("expected Err(CantMutateFinalMember), got Ok"));
    match &err {
        ICompileErrorT::CantMutateFinalMember { struct_, member_name, .. } => {
            match struct_.id.local_name {
                INameT::Struct(StructNameT {
                    template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("Vec3"), .. }),
                    template_args: &[],
                    ..
                }) => {}
                _ => panic!("expected Struct(StructTemplateNameT(\"Vec3\"))"),
            }
            match member_name {
                IVarNameT::CodeVar(CodeVarNameT { name: StrI("x"), .. }) => {}
                _ => panic!("expected CodeVarNameT(\"x\")"),
            }
        }
        _ => panic!("expected CantMutateFinalMember"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:4:1:
exported func main() int {
At test:0.vale:6:7:
  set v.x = 10.0;
Cannot mutate final member 'x' of container Vec3
"#,
    );
}

#[test]
fn reports_when_we_try_to_mutate_an_element_in_an_imm_static_sized_array() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
import v.builtins.arrays.*;
import v.builtins.drop.*;

exported func main() int {
  arr = #[#10]({_});
  set arr[4] = 10;
  return 73;
}
";
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err()
        .unwrap_or_else(|| panic!("expected Err(CantMutateFinalElement), got Ok"));
    match &err {
        ICompileErrorT::CantMutateFinalElement {
            coord: CoordT {
                kind: KindT::StaticSizedArray(StaticSizedArrayTT {
                    name: IdT {
                        local_name: INameT::StaticSizedArray(StaticSizedArrayNameT {
                            size: ITemplataT::Integer(10),
                            variability: ITemplataT::Variability(VariabilityTemplataT { variability: VariabilityT::Final }),
                            arr: RawArrayNameT {
                                mutability: ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }),
                                element_type: CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT { .. }), .. },
                                ..
                            },
                            ..
                        }),
                        ..
                    },
                    ..
                }),
                ..
            },
            ..
        } => {}
        _ => panic!("expected CantMutateFinalElement"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:5:1:
exported func main() int {
At test:0.vale:7:7:
  set arr[4] = 10;
Cannot change a slot in array StaticArray<10, imm, final, i32> to point to a different element; it's an array of final references.
"#,
    );
}

#[test]
fn reports_when_we_try_to_mutate_a_local_variable_with_wrong_type() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r#"

exported func main() {
  a = 5;
  set a = "blah";
}
"#;
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err()
        .unwrap_or_else(|| panic!("expected Err(CouldntConvertForMutateT), got Ok"));
    match &err {
        ICompileErrorT::CouldntConvertForMutateT { expected_type: CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT { bits: 32 }), .. }, actual_type: CoordT { ownership: OwnershipT::Share, kind: KindT::Str(_), .. }, .. } => {}
        _ => panic!("expected CouldntConvertForMutateT"),
    }
    // TODO: humanize CouldntConvertForMutateT uses Debug-format on CoordT; replace with humanize_templata and re-capture.
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r##"At test:0.vale:3:1:
exported func main() {
At test:0.vale:5:7:
  set a = "blah";
Mutate couldn't convert CoordT { ownership: Share, region: RegionT { region: Default }, kind: Str(StrT) } to expected destination type CoordT { ownership: Share, region: RegionT { region: Default }, kind: Int(IntT { bits: 32 }) }
"##,
    );
}

#[test]
fn reports_when_we_try_to_override_a_non_interface() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

impl int for Bork;
struct Bork { }
exported func main() {
  Bork();
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err()
        .unwrap_or_else(|| panic!("expected Err(CantImplNonInterface), got Ok"));
    match &err {
        ICompileErrorT::CantImplNonInterface { templata: ITemplataT::Kind(KindTemplataT { kind: KindT::Int(IntT { bits: 32 }) }), .. } => {}
        _ => panic!("expected CantImplNonInterface"),
    }
    // TODO: humanize CantImplNonInterface uses Debug-format on the templata; replace with humanize_templata and re-capture.
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r#"At test:0.vale:3:1:
impl int for Bork;
Can't extend a non-interface: Kind(KindTemplataT { kind: Int(IntT { bits: 32 }) })
"#,
    );
}

#[test]
fn can_mutate_an_element_in_a_runtime_sized_array() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
import v.builtins.arrays.*;
import v.builtins.drop.*;

exported func main() int {
  arr = Array<mut, int>(3);
  arr.push(0);
  arr.push(1);
  arr.push(2);
  set arr[1] = 10;
  return 73;
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
fn can_restackify_in_destructure_pattern() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
#!DeriveStructDrop
struct Ship { fuel int; }

/// TODO: Bring tuples back
#!DeriveStructDrop
struct GetFuelResult { fuel int; ship Ship; }

func GetFuel(ship Ship) GetFuelResult {
  return GetFuelResult(ship.fuel, ship);
}

exported func main() int {
  ship = Ship(42);
  [fuel, set ship] = GetFuel(ship);
  [f] = ship;
  return fuel;
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}
#[test]
fn if_branches_must_move_same_variables() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
struct S { x int; }
func consume(s S) int { return s.x; }
exported func main() int {
  s = S(7);
  result = 0;
  if true {
    set result = consume(s);
  } else {
    set result = 5;
  }
  return result;
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err()
        .unwrap_or_else(|| panic!("expected Err(RangedInternalErrorT) for if-branch-move-mismatch, got Ok"));
    match &err {
        crate::typing::compiler_error_reporter::ICompileErrorT::RangedInternalErrorT { .. } => {},
        other => panic!("expected RangedInternalErrorT for if-branch-move-mismatch, got: {:?}", other),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r##"At test:0.vale:4:1:
exported func main() int {
At test:0.vale:7:3:
  if true {
Internal error: Must move same variables from inside branches!
From then branch: {CodeVar(CodeVarNameT { name: "s" })}
From else branch: {}
"##,
    );
}
#[test]
fn if_branches_moving_same_vars_different_order_compiles() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
struct S { x int; }
func consume(s S) int { return s.x; }
exported func main() int {
  a = S(1);
  b = S(2);
  result = 0;
  if true {
    set result = consume(a);
    set result = consume(b);
  } else {
    set result = consume(b);
    set result = consume(a);
  }
  return result;
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    match compile.get_compiler_outputs() {
        Ok(_) => {},
        Err(e) => panic!("expected Ok (same vars moved in both branches, just different order), got Err: {:?}", e),
    }
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

    let myfunc_template_name = typing_interner.intern_function_template_name(
        FunctionTemplateNameT { human_name: scout_arena.intern_str("myFunc"), code_location: tz_code_loc});
    let myfunc_func_name = typing_interner.intern_function_name(
        FunctionNameValT { template: myfunc_template_name, template_args: &[], parameters: &[] });
    let myfunc_id = typing_interner.intern_id(IdValT {
        package_coord: test_tld, init_steps: &[], local_name: INameT::Function(myfunc_func_name),
    });

    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CouldntFindTypeT { range: tz_slice, name: scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: scout_arena.intern_str("Spaceship") })) }).is_empty());
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
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CantReconcileBranchesResults { range: tz_slice, then_result: firefly_coord, else_result: serenity_coord }).is_empty());
    let firefly_var_name: &CodeVarNameT = typing_bump.alloc(CodeVarNameT { name: scout_arena.intern_str("firefly")});
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::CantUseUnstackifiedLocal { range: tz_slice, local_id: IVarNameT::CodeVar(firefly_var_name) }).is_empty());
    assert!(!humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between, &line_range_containing, &line_containing,
        ICompileErrorT::FunctionAlreadyExists { old_function_range: tz, new_function_range: tz, signature: *myfunc_id }).is_empty());
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
}

