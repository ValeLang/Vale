use bumpalo::Bump;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::typing::test::compiler_test_compilation::compiler_test_compilation;
use crate::typing::test::humanize_helper::{assert_humanized_eq, humanize_compile_error};
use crate::utils::code_hierarchy::{self, IPackageResolver, PackageCoordinate};
use std::collections::HashMap;
use crate::interner::StrI;
use crate::postparsing::names::{IImpreciseNameS, CodeNameS};
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::typing::overload_resolver::FindFunctionFailure;
use crate::typing::ast::expressions::FunctionCallTE;
use crate::typing::names::names::{IFunctionNameT, INameT};
use crate::typing::templata::templata::ITemplataT;
use crate::typing::types::types::{CoordT, KindT, OwnershipT};
use crate::typing::ast::expressions::ReferenceExpressionTE;
use crate::typing::ast::expressions::ConstantIntTE;
use crate::typing::types::types::IntT;
use crate::postparsing::itemplatatype::ITemplataType;
use crate::typing::ast::expressions::BlockTE;
use crate::typing::ast::expressions::ConsecutorTE;
use crate::typing::ast::expressions::ReturnTE;
use crate::typing::ast::expressions::VoidLiteralTE;
use crate::typing::typing_interner::TypingInterner;
use crate::utils::range::{CodeLocationS, RangeS};
use crate::utils::code_hierarchy::FileCoordinateMap;
use crate::utils::source_code_utils::{humanize_pos_code_map, line_containing, line_range_containing, lines_between};
use crate::postparsing::names::{CodeRuneS, IRuneS, IRuneValS};
use crate::postparsing::ast::LocationInDenizenBuilder;
use crate::postparsing::rules::rules::{CoordComponentsSR, IRulexSR, KindComponentsSR, RuneUsage};
use crate::solver::solver::{FailedSolve, ISolverError, RuleError, SolveIncomplete, Step};
use crate::typing::ast::ast::SignatureT;
use crate::typing::compiler_error_humanizer::humanize;
use crate::typing::infer::compiler_solver::ITypingPassSolverError;
use crate::typing::names::names::FunctionNameValT;
use crate::typing::names::names::FunctionTemplateNameT;
use crate::typing::names::names::IdValT;
use crate::typing::names::names::InterfaceNameValT;
use crate::typing::names::names::InterfaceTemplateNameT;
use crate::typing::names::names::IStructTemplateNameT;
use crate::typing::names::names::KindPlaceholderNameT;
use crate::typing::names::names::KindPlaceholderTemplateNameT;
use crate::typing::names::names::StructNameValT;
use crate::typing::names::names::StructTemplateNameT;
use crate::typing::templata::templata::OwnershipTemplataT;
use crate::typing::types::types::InterfaceTTValT;
use crate::typing::types::types::{IRegionT, RegionT};
use crate::typing::types::types::StructTTValT;
use crate::typing::ast::expressions::UpcastTE;
use crate::postparsing::names::{IStructDeclarationNameS, TopLevelStructDeclarationNameS};
use crate::higher_typing::ast::StructA;
use crate::solver::solver::SolverConflict;
use crate::typing::names::names::IdT;
use crate::typing::names::names::StructNameT;
use crate::typing::templata::templata::StructDefinitionTemplataT;
use crate::typing::templata::templata::KindTemplataT;
use crate::typing::types::types::StructTT;
use crate::typing::templata::templata::CoordTemplataT;
use crate::typing::test::traverse::NodeRefT;
use crate::postparsing::names::INameValS;
use crate::postparsing::names::IFunctionDeclarationNameValS;
use crate::postparsing::names::FunctionNameS;
use crate::postparsing::names::DenizenDefaultRegionRuneS;
use crate::typing::names::names::ExportTemplateNameT;
use crate::typing::names::names::ExportNameT;
use crate::typing::ast::ast::KindExportT;
use crate::utils::code_hierarchy::FileCoordinate;
use crate::postparsing::names::ImplicitRuneValS;
use crate::typing::types::types::ISuperKindTT;
use crate::typing::ast::ast::PrototypeT;
use crate::typing::names::names::FunctionNameT;
use crate::typing::names::names::FunctionBoundNameT;
use crate::typing::names::names::FunctionBoundTemplateNameT;
use crate::typing::types::types::KindPlaceholderT;
use crate::builtins::builtins::get_embedded_modulized_code_map;
use crate::collect_only_tnode;
use crate::collect_where_tnode;
use crate::typing::templata::templata_utils::unapply_simple_name;
use std::collections::HashSet;
use std::marker::PhantomData;

fn read_code_from_resource(resource_filename: &str) -> String {
    panic!("Unimplemented: read_code_from_resource");
}

#[test]
fn test_simple_generic_function() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "\nfunc bork<T>(a T) T { return a; }\n";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();

    assert_eq!(coutputs.get_all_user_functions().len(), 1);
}

#[test]
fn test_lacking_drop_function() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "\nfunc bork<T>(a T) { }\n";
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
        r#"At test:0.vale:2:1:
func bork<T>(a T) { }
At test:0.vale:2:22:
func bork<T>(a T) { }
Couldn't find a suitable function drop(Kind$bork.T). No function with that name exists.

"#,
    );
}

#[test]
fn test_having_drop_function_concept_function() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "\nfunc bork<T>(a T) where func drop(T)void { }\n";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let bork = coutputs.lookup_function_by_str("bork");

    // Only identifying template arg coord should be of PlaceholderT(0)
    let template_args = IFunctionNameT::try_from(bork.header.id.local_name).unwrap().template_args();
    assert_eq!(template_args.len(), 1);
    match template_args[0] {
        ITemplataT::Coord(ct) => match ct.coord {
            CoordT { ownership: OwnershipT::Own, kind: KindT::KindPlaceholder(kp), .. } => match kp.id.local_name {
                INameT::KindPlaceholder(kpn) => assert_eq!(kpn.template.index, 0),
                _ => panic!("expected KindPlaceholder local_name"),
            },
            _ => panic!("expected CoordT with KindPlaceholder"),
        },
        _ => panic!("expected Coord template arg"),
    }

    // Make sure it calls drop, and that it has the right placeholders
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(bork),
        NodeRefT::FunctionCall(FunctionCallTE {
            callable: PrototypeT {
                id: IdT {
                    local_name: INameT::FunctionBound(FunctionBoundNameT {
                        template: FunctionBoundTemplateNameT { human_name: StrI("drop"), .. },
                        template_args: &[],
                        parameters: &[CoordT {
                            ownership: OwnershipT::Own,
                            kind: KindT::KindPlaceholder(KindPlaceholderT {
                                id: IdT {
                                    init_steps: &[INameT::FunctionTemplate(FunctionTemplateNameT { human_name: StrI("bork"), .. })],
                                    local_name: INameT::KindPlaceholder(KindPlaceholderNameT {
                                        template: KindPlaceholderTemplateNameT { index: 0, .. },
                                    }),
                                    ..
                                },
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
        }) => Some(())
    );
}

#[test]
fn test_calling_a_generic_function_with_a_concept_function() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
func moo(x int) { }

func bork<T>(a T) T where func moo(T)void { a }

exported func main() {
  bork(3);
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");

    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(FunctionCallTE {
            callable: PrototypeT {
                id: IdT {
                    local_name: INameT::Function(FunctionNameT {
                        template: FunctionTemplateNameT { human_name: StrI("bork"), .. },
                        template_args: &[ITemplataT::Coord(CoordTemplataT {
                            coord: CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT::I32), .. },
                        })],
                        parameters: &[CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT::I32), .. }],
                        ..
                    }),
                    ..
                },
                return_type: CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT::I32), .. },
            },
            args: &[ReferenceExpressionTE::ConstantInt(ConstantIntTE { value: ITemplataT::Integer(3), bits: 32, .. })],
            ..
        }) => Some(())
    );
}

#[test]
fn test_rune_type_in_generic_param() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "\nfunc bork<I Int>() int { I }\n";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("bork");
    let template_args = IFunctionNameT::try_from(main.header.id.local_name).unwrap().template_args();
    match template_args {
        [ITemplataT::Placeholder(p)] => match p.tyype {
            ITemplataType::IntegerTemplataType(_) => {}
            _ => panic!("expected IntegerTemplataType"),
        },
        _ => panic!("expected Vector(PlaceholderTemplataT(_, IntegerTemplataType()))"),
    }
}

#[test]
fn test_single_parameter_function() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
struct Functor1<F Prot = func(P1)R> imm
where P1 Ref, R Ref { }

func __call<F Prot = func(P1)R>(self &Functor1<F>, param1 P1) R
where P1 Ref, R Ref {
  F(param1)
}

exported func main() int {
  Functor1({_})(4)
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let _compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
}

#[test]
fn test_calling_a_generic_function_with_a_drop_concept_function() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
func bork<T>(a T) where func drop(T)void {
}

struct Mork {}

exported func main() {
  bork(Mork());
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let bork = coutputs.lookup_function_by_str("main");
    let prototype = match bork.body {
        ReferenceExpressionTE::Block(BlockTE { inner }) => match inner {
            ReferenceExpressionTE::Return(ReturnTE { source_expr }) => match source_expr {
                ReferenceExpressionTE::Consecutor(ConsecutorTE { exprs }) => match exprs {
                    [ReferenceExpressionTE::FunctionCall(call), ReferenceExpressionTE::VoidLiteral(VoidLiteralTE { .. })] => call.callable,
                    _ => panic!("expected Vector(FunctionCallTE, VoidLiteralTE)"),
                },
                _ => panic!("expected ConsecutorTE"),
            },
            _ => panic!("expected ReturnTE"),
        },
        _ => panic!("expected BlockTE"),
    };
    match prototype.id.local_name {
        INameT::Function(fn_name) => {
            assert_eq!(fn_name.template.human_name.0, "bork");
            let template_arg_coord = match fn_name.template_args {
                [ITemplataT::Coord(ct)] => ct.coord,
                _ => panic!("expected Vector(CoordTemplataT(templateArgCoord))"),
            };
            let arg = match fn_name.parameters {
                [a] => *a,
                _ => panic!("expected Vector(arg)"),
            };
            match prototype.return_type {
                CoordT { ownership: OwnershipT::Share, kind: KindT::Void(_), .. } => {}
                _ => panic!("expected Share Void return_type"),
            }
            match template_arg_coord {
                CoordT { ownership: OwnershipT::Own, kind: KindT::Struct(stt), .. } => match stt.id.local_name {
                    INameT::Struct(sn) => match sn.template {
                        IStructTemplateNameT::StructTemplate(st) => assert_eq!(st.human_name.0, "Mork"),
                        _ => panic!("expected StructTemplate"),
                    },
                    _ => panic!("expected Struct local_name"),
                },
                _ => panic!("expected Own Struct(Mork)"),
            }
            assert_eq!(arg, template_arg_coord);
        }
        _ => panic!("expected Function local_name"),
    }
}

#[test]
fn humanize_errors() {

    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let _keywords = Keywords::new_for_scout(&scout_arena);
    let typing_interner = TypingInterner::new(&typing_bump);

    let tz = vec![RangeS::test_zero(&scout_arena)];
    let test_package_coord = scout_arena.intern_package_coordinate(scout_arena.intern_str("test"), &[]);
    let tz_code_loc = CodeLocationS::test_zero(&scout_arena);
    let func_template_name = typing_interner.intern_function_template_name(FunctionTemplateNameT { human_name: scout_arena.intern_str("main"), code_location: tz_code_loc});
    let func_template_id = typing_interner.intern_id(IdValT { package_coord: test_package_coord, init_steps: &[], local_name: INameT::FunctionTemplate(func_template_name) });
    let main_func_template = typing_interner.intern_function_template_name(FunctionTemplateNameT { human_name: scout_arena.intern_str("main"), code_location: tz_code_loc});
    let main_func_name = typing_interner.intern_function_name(FunctionNameValT { template: main_func_template, template_args: &[], parameters: &[] });
    let _func_name = typing_interner.intern_id(IdValT { package_coord: test_package_coord, init_steps: &[], local_name: INameT::Function(main_func_name) });
    let denizen_name_s = scout_arena.intern_name(INameValS::FunctionDeclaration(IFunctionDeclarationNameValS::FunctionName(FunctionNameS { name: scout_arena.intern_str("main"), code_location: tz_code_loc })));
    let denizen_default_region_rune_s = DenizenDefaultRegionRuneS { denizen_name: denizen_name_s };
    let kpt_name = typing_interner.intern_kind_placeholder_template_name(KindPlaceholderTemplateNameT { index: 0, rune: IRuneS::DenizenDefaultRegionRune(scout_arena.alloc(denizen_default_region_rune_s))});
    let kp_name = typing_interner.intern_kind_placeholder_name(KindPlaceholderNameT { template: kpt_name });
    let mut region_init_steps: Vec<INameT> = func_template_id.init_steps.to_vec();
    region_init_steps.push(func_template_id.local_name);
    let region_init_steps_slice: &[INameT] = typing_bump.alloc_slice_copy(&region_init_steps);
    let _region_name = typing_interner.intern_id(IdValT { package_coord: func_template_id.package_coord, init_steps: region_init_steps_slice, local_name: INameT::KindPlaceholder(kp_name) });
    let region = RegionT { region: IRegionT::Default };

    let firefly_struct_template_name = typing_interner.intern_struct_template_name(
        StructTemplateNameT { human_name: scout_arena.intern_str("Firefly")});
    let firefly_struct_name = typing_interner.intern_struct_name(
        StructNameValT { template: IStructTemplateNameT::StructTemplate(firefly_struct_template_name), template_args: &[] });
    let firefly_id = typing_interner.intern_id(IdValT { package_coord: test_package_coord, init_steps: &[], local_name: INameT::Struct(firefly_struct_name) });
    let firefly_tt = typing_interner.intern_struct_tt(StructTTValT { id: *firefly_id });
    let firefly_kind = KindT::Struct(firefly_tt);
    let _firefly_coord = CoordT { ownership: OwnershipT::Own, region, kind: firefly_kind };

    let serenity_struct_template_name = typing_interner.intern_struct_template_name(
        StructTemplateNameT { human_name: scout_arena.intern_str("Serenity")});
    let serenity_struct_name = typing_interner.intern_struct_name(
        StructNameValT { template: IStructTemplateNameT::StructTemplate(serenity_struct_template_name), template_args: &[] });
    let serenity_id = typing_interner.intern_id(IdValT { package_coord: test_package_coord, init_steps: &[], local_name: INameT::Struct(serenity_struct_name) });
    let serenity_tt = typing_interner.intern_struct_tt(StructTTValT { id: *serenity_id });
    let serenity_kind = KindT::Struct(serenity_tt);
    let _serenity_coord = CoordT { ownership: OwnershipT::Own, region, kind: serenity_kind };

    let ispaceship_interface_template_name = typing_interner.intern_interface_template_name(
        InterfaceTemplateNameT { human_namee: scout_arena.intern_str("ISpaceship")});
    let ispaceship_interface_name = typing_interner.intern_interface_name(
        InterfaceNameValT { template: ispaceship_interface_template_name, template_args: &[] });
    let ispaceship_id = typing_interner.intern_id(IdValT { package_coord: test_package_coord, init_steps: &[], local_name: INameT::Interface(ispaceship_interface_name) });
    let ispaceship_tt = typing_interner.intern_interface_tt(InterfaceTTValT { id: *ispaceship_id });
    let ispaceship_kind = KindT::Interface(ispaceship_tt);
    let _ispaceship_coord = CoordT { ownership: OwnershipT::Own, region, kind: ispaceship_kind };

    let unrelated_struct_template_name = typing_interner.intern_struct_template_name(
        StructTemplateNameT { human_name: scout_arena.intern_str("Spoon")});
    let unrelated_struct_name = typing_interner.intern_struct_name(
        StructNameValT { template: IStructTemplateNameT::StructTemplate(unrelated_struct_template_name), template_args: &[] });
    let unrelated_id = typing_interner.intern_id(IdValT { package_coord: test_package_coord, init_steps: &[], local_name: INameT::Struct(unrelated_struct_name) });
    let unrelated_tt = typing_interner.intern_struct_tt(StructTTValT { id: *unrelated_id });
    let unrelated_kind = KindT::Struct(unrelated_tt);
    let _unrelated_coord = CoordT { ownership: OwnershipT::Own, region, kind: unrelated_kind };

    let myfunc_template = typing_interner.intern_function_template_name(FunctionTemplateNameT { human_name: scout_arena.intern_str("myFunc"), code_location: tz[0].begin});
    let myfunc_params: &[CoordT] = typing_bump.alloc_slice_copy(&[CoordT { ownership: OwnershipT::Own, region, kind: firefly_kind }]);
    let myfunc_name = typing_interner.intern_function_name(FunctionNameValT { template: myfunc_template, template_args: &[], parameters: myfunc_params });
    let myfunc_id = typing_interner.intern_id(IdValT { package_coord: test_package_coord, init_steps: &[], local_name: INameT::Function(myfunc_name) });
    let _firefly_signature = SignatureT { id: *myfunc_id };

    let export_template_name = typing_interner.intern_export_template_name(ExportTemplateNameT { code_loc: tz[0].begin});
    let firefly_export_name = typing_interner.intern_export_name(ExportNameT { template: export_template_name, region: RegionT { region: IRegionT::Default } });
    let firefly_export_id = typing_interner.intern_id(IdValT { package_coord: test_package_coord, init_steps: &[], local_name: INameT::Export(firefly_export_name) });
    let _firefly_export = KindExportT { range: tz[0], tyype: firefly_kind, id: *firefly_export_id, exported_name: scout_arena.intern_str("Firefly") };
    let serenity_export_name = typing_interner.intern_export_name(ExportNameT { template: export_template_name, region: RegionT { region: IRegionT::Default } });
    let serenity_export_id = typing_interner.intern_id(IdValT { package_coord: test_package_coord, init_steps: &[], local_name: INameT::Export(serenity_export_name) });
    let _serenity_export = KindExportT { range: tz[0], tyype: firefly_kind, id: *serenity_export_id, exported_name: scout_arena.intern_str("Serenity") };

    let code_str = "Hello I am A large piece Of code [that has An error]";
    let filenames_and_sources = FileCoordinateMap::test(&scout_arena, code_str.to_string());

    let test_file = FileCoordinate::test(&scout_arena);
    let make_loc = |pos: i32| CodeLocationS { file: scout_arena.alloc(test_file), offset: pos };
    let make_range = |begin: i32, end: i32| RangeS { begin: make_loc(begin), end: make_loc(end) };

    let humanize_pos = |x| humanize_pos_code_map(&filenames_and_sources, &x);
    let lines_between_f = |x, y| lines_between(&filenames_and_sources, &x, &y);
    let line_range_containing_f = |x| line_range_containing(&filenames_and_sources, &x);
    let line_containing_f = |x| line_containing(&filenames_and_sources, &x);

    let rune_i = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS { name: scout_arena.intern_str("I") }));
    let rune_a = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS { name: scout_arena.intern_str("A") }));
    let rune_an = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS { name: scout_arena.intern_str("An") }));
    let rune_of = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS { name: scout_arena.intern_str("Of") }));
    let mut lid_builder = LocationInDenizenBuilder::new(vec![7]);
    let implicit_rune = scout_arena.intern_rune(IRuneValS::ImplicitRune(ImplicitRuneValS::new(lid_builder.borrow_val())));

    let unsolved_rules: Vec<IRulexSR> = vec![
        IRulexSR::CoordComponents(CoordComponentsSR {
            range: make_range(0, code_str.len() as i32),
            result_rune: RuneUsage { range: make_range(6, 7), rune: rune_i },
            ownership_rune: RuneUsage { range: make_range(11, 12), rune: rune_a },
            kind_rune: RuneUsage { range: make_range(33, 52), rune: implicit_rune },
        }),
        IRulexSR::KindComponents(KindComponentsSR {
            range: make_range(33, 52),
            kind_rune: RuneUsage { range: make_range(33, 52), rune: implicit_rune },
            mutability_rune: RuneUsage { range: make_range(43, 45), rune: rune_an },
        }),
    ];

    let step1 = {
        let mut conclusions = HashMap::new();
        conclusions.insert(rune_a, ITemplataT::Ownership(OwnershipTemplataT { ownership: OwnershipT::Own }));
        Step { complex: false, solved_rules: vec![], added_rules: vec![], conclusions }
    };

    let failed_solve_1 = FailedSolve {
        steps: vec![step1.clone()],
        conclusions: HashMap::new(),
        unsolved_rules: unsolved_rules.clone(),
        unsolved_runes: vec![],
        error: ISolverError::RuleError(RuleError {
            err: ITypingPassSolverError::KindIsNotConcrete { kind: ispaceship_kind },
            _phantom: PhantomData,
        }),
    };
    let text1 = humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between_f, &line_range_containing_f, &line_containing_f,
        ICompileErrorT::TypingPassSolverError { range: typing_bump.alloc_slice_copy(&tz), failed_solve: failed_solve_1 });
    assert!(!text1.is_empty());

    let mut conclusions2 = HashMap::new();
    conclusions2.insert(rune_a, ITemplataT::Ownership(OwnershipTemplataT { ownership: OwnershipT::Own }));
    let failed_solve_2 = FailedSolve {
        steps: vec![step1],
        conclusions: conclusions2,
        unsolved_rules,
        unsolved_runes: vec![rune_i, rune_of, rune_an, implicit_rune],
        error: ISolverError::SolveIncomplete(SolveIncomplete { _phantom: PhantomData }),
    };
    let error_text = humanize(&scout_arena, &typing_interner, false, &humanize_pos, &lines_between_f, &line_range_containing_f, &line_containing_f,
        ICompileErrorT::TypingPassSolverError { range: typing_bump.alloc_slice_copy(&tz), failed_solve: failed_solve_2 });
    println!("{}", error_text);
    assert!(!error_text.is_empty());
    assert!(error_text.contains("\n           ^ A: own"), "missing A:own caret line, got: {}", error_text);
    assert!(error_text.contains("\n      ^ I: (unknown)"), "missing I:(unknown) caret line, got: {}", error_text);
    assert!(error_text.contains("\n                                 ^^^^^^^^^^^^^^^^^^^ _7: (unknown)"), "missing _7:(unknown) caret line, got: {}", error_text);
}

fn make_loc(pos: i32) {
    panic!("Unimplemented: make_loc");
}

fn make_range(begin: i32, end: i32) {
    panic!("Unimplemented: make_range");
}

#[test]
fn simple_int_rule() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

exported func main() int where N Int = 3 {
  return N;
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    let _ci: &ConstantIntTE = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::ConstantInt(ci @ ConstantIntTE { value: ITemplataT::Integer(3), bits: 32, .. }) => Some(ci)
    );
}

#[test]
fn equals_transitive() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

exported func main() int where N Int = 3, M Int = N {
  return M;
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    let _ci: &ConstantIntTE = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::ConstantInt(ci @ ConstantIntTE { value: ITemplataT::Integer(3), bits: 32, .. }) => Some(ci)
    );
}

#[test]
fn one_of() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

exported func main() int where N Int = any(2, 3, 4), N = 3 {
  return N;
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    let _ci: &ConstantIntTE = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::ConstantInt(ci @ ConstantIntTE { value: ITemplataT::Integer(3), bits: 32, .. }) => Some(ci)
    );
}

#[test]
fn components() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
exported struct MyStruct { }
exported func main() X
where
  MyStruct = Ref[O Ownership, K Kind],
  X Ref = Ref[borrow, K]
{
  return &MyStruct();
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    match coutputs.lookup_function_by_str("main").header.return_type {
        CoordT { ownership: OwnershipT::Borrow, kind: KindT::Struct(_), .. } => {}
        _ => panic!("expected Borrow Struct return_type"),
    }
}

#[test]
fn prototype_rule_call_via_rune() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r#"

func moo(i int, b bool) str { return "hello"; }
exported func main() str
where mooFunc Prot = func moo(int, bool)str
{
  return (mooFunc)(5, true);
}
"#;
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    let call: &FunctionCallTE = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(c) => Some(c)
    );
    assert_eq!(unapply_simple_name(&call.callable.id), Some("moo".to_string()));
}

#[test]
fn prototype_rule_call_directly() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r#"

func moo(i int, b bool) str { return "hello"; }
exported func main() str
where func moo(int, bool)str
{
  return moo(5, true);
}
"#;
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    let call: &FunctionCallTE = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(c) => Some(c)
    );
    assert_eq!(unapply_simple_name(&call.callable.id), Some("moo".to_string()));
}

#[test]
fn send_struct_to_struct() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

struct MyStruct {}
func moo(m MyStruct) { }
exported func main() {
  moo(MyStruct())
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn send_struct_to_interface() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

struct MyStruct {}
interface MyInterface {}
impl MyInterface for MyStruct;
func moo(m MyInterface) { }
exported func main() {
  moo(MyStruct())
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn assume_most_specific_generic_param() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

struct MyStruct {}
interface MyInterface {}
impl MyInterface for MyStruct;
func moo<T>(m T) where func drop(T)void { }
exported func main() {
  moo(MyStruct())
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    let call: &FunctionCallTE = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(c @ FunctionCallTE { args: [_], .. }) => Some(c)
    );
    let arg = call.args[0];
    match arg.result().coord {
        CoordT { kind: KindT::Struct(_), .. } => {}
        _ => panic!("expected Struct arg"),
    }
}

#[test]
fn assume_most_specific_common_ancestor() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
interface IShip {}
struct Firefly {}
impl IShip for Firefly;
struct Serenity {}
impl IShip for Serenity;
func moo<T>(a T, b T) where func drop(T)void { }
exported func main() {
  moo(Firefly(), Serenity())
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let _moo = coutputs.lookup_function_by_str("moo");
    let main = coutputs.lookup_function_by_str("main");
    let _call: &FunctionCallTE = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(c @ FunctionCallTE { args: [_, _], .. }) => Some({
            let fn_name = match c.callable.id.local_name {
                INameT::Function(fn_name) => fn_name,
                _ => panic!("expected Function local_name"),
            };
            match fn_name.template_args[0] {
                ITemplataT::Coord(ct) => match ct.coord {
                    CoordT { kind: KindT::Interface(_), .. } => {}
                    _ => panic!("expected Interface template arg"),
                },
                _ => panic!("expected Coord template arg"),
            }
            c
        })
    );
    let upcasts: Vec<&UpcastTE> = collect_where_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Upcast(u) => Some(u)
    );
    assert_eq!(upcasts.len(), 2);
}

#[test]
fn descendant_satisfying_call() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
interface IShip<T> where T Ref {}
struct Firefly<T> where T Ref {}
impl<T> IShip<T> for Firefly<T>;
func moo<T>(a IShip<T>) { }
exported func main() {
  moo(Firefly<int>())
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let moo = coutputs.lookup_function_by_str("moo");
    match moo.header.params[0].tyype {
        CoordT { kind: KindT::Interface(itt), .. } => match itt.id.local_name {
            INameT::Interface(in_) => match in_.template_args {
                [ITemplataT::Coord(ct)] => match ct.coord {
                    CoordT { kind: KindT::KindPlaceholder(_), .. } => {}
                    _ => panic!("expected KindPlaceholder template arg"),
                },
                _ => panic!("expected single Coord template arg"),
            },
            _ => panic!("expected Interface local_name"),
        },
        _ => panic!("expected Interface param coord"),
    }
    let main = coutputs.lookup_function_by_str("main");
    let calls: Vec<&FunctionCallTE> = collect_where_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(c) => Some(c)
    );
    let _moo_call = calls.iter().find(|c| {
        let is_moo = match c.callable.id.local_name {
            INameT::Function(fn_name) => fn_name.template.human_name.0 == "moo",
            _ => false,
        };
        if !is_moo { return false; }
        if c.args.len() != 1 { return false; }
        let upcast = match c.args[0] {
            ReferenceExpressionTE::Upcast(u) => u,
            _ => return false,
        };
        match upcast.target_super_kind {
            ISuperKindTT::Interface(itt) => match itt.id.local_name {
                INameT::Interface(in_) => {
                    in_.template.human_namee.0 == "IShip" && match in_.template_args {
                        [ITemplataT::Coord(ct)] => matches!(ct.coord, CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT::I32), .. }),
                        _ => false,
                    }
                }
                _ => false,
            },
            _ => false,
        }
    }).expect("expected FunctionCallTE moo(UpcastTE(_, IShip<int>, _))");
}

#[test]
fn reports_incomplete_solve() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

exported func main() int where N Int {
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err()
        .unwrap_or_else(|| panic!("expected Err(TypingPassSolverError), got Ok"));
    match &err {
        ICompileErrorT::TypingPassSolverError { failed_solve, .. } => {
            assert!(failed_solve.unsolved_rules.is_empty(), "expected empty unsolved_rules");
            assert!(matches!(failed_solve.error, ISolverError::SolveIncomplete(_)));
            let expected_n_rune = IRuneS::CodeRune(scout_arena.alloc(CodeRuneS {
                name: scout_arena.intern_str("N"),
            }));
            let unsolved_set: HashSet<_> = failed_solve.unsolved_runes.iter().copied().collect();
            let mut expected: HashSet<IRuneS> = HashSet::new();
            expected.insert(expected_n_rune);
            assert_eq!(unsolved_set, expected);
        }
        _ => panic!("expected TypingPassSolverError"),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r##"At test:0.vale:3:1:
exported func main() int where N Int {
At test:0.vale:3:1:
exported func main() int where N Int {
Couldn't solve some runes: N
Steps:
Supplied:
  added rule: _21111.kind = "int"
  added rule: coerceToCoord(_21111, _21111.kind)
_21111.kind = "int"
  _21111.kind: i32
coerceToCoord(_21111, _21111.kind)
  _21111: i32
Unsolved runes: N
"##,
    );
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
    let code = r"
import v.builtins.drop.*;

interface MyInterface<X Ref> { }

struct SomeStruct<X Ref> where func drop(X)void { x X; }
impl<X> MyInterface<X> for SomeStruct<X> where func drop(X)void;

func doAThing<T>(t T) SomeStruct<T>
where func drop(T)void {
  return SomeStruct<T>(t);
}

exported func main() {
  doAThing(4);
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(get_embedded_modulized_code_map(&parse_arena, &parser_keywords))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn pointer_becomes_share_if_kind_is_immutable() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"


struct SomeStruct imm { i int; }

func bork(x &SomeStruct) int {
  return x.i;
}

exported func main() int {
  return bork(SomeStruct(7));
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    assert_eq!(coutputs.lookup_function_by_str("bork").header.params[0].tyype.ownership, OwnershipT::Share);
}

#[test]
fn detects_conflict_between_types() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

struct ShipA {}
struct ShipB {}
exported func main<N Kind>() where N Kind = ShipA, N Kind = ShipB {
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let err = compile.get_compiler_outputs().err()
        .unwrap_or_else(|| panic!("expected Err(TypingPassSolverError), got Ok"));
    match &err {
        ICompileErrorT::TypingPassSolverError { failed_solve: FailedSolve { error: ISolverError::SolverConflict(SolverConflict { previous_conclusion: ITemplataT::StructDefinition(StructDefinitionTemplataT { origin_struct: &StructA { name: IStructDeclarationNameS::TopLevelStructDeclarationName(TopLevelStructDeclarationNameS { name: StrI("ShipA"), .. }), .. }, .. }), new_conclusion: ITemplataT::StructDefinition(StructDefinitionTemplataT { origin_struct: &StructA { name: IStructDeclarationNameS::TopLevelStructDeclarationName(TopLevelStructDeclarationNameS { name: StrI("ShipB"), .. }), .. }, .. }), .. }), .. }, .. } => {}
        ICompileErrorT::TypingPassSolverError { failed_solve: FailedSolve { error: ISolverError::SolverConflict(SolverConflict { previous_conclusion: ITemplataT::StructDefinition(StructDefinitionTemplataT { origin_struct: &StructA { name: IStructDeclarationNameS::TopLevelStructDeclarationName(TopLevelStructDeclarationNameS { name: StrI("ShipB"), .. }), .. }, .. }), new_conclusion: ITemplataT::StructDefinition(StructDefinitionTemplataT { origin_struct: &StructA { name: IStructDeclarationNameS::TopLevelStructDeclarationName(TopLevelStructDeclarationNameS { name: StrI("ShipA"), .. }), .. }, .. }), .. }), .. }, .. } => {}
        ICompileErrorT::TypingPassSolverError { failed_solve: FailedSolve { error: ISolverError::SolverConflict(SolverConflict { previous_conclusion: ITemplataT::Kind(&KindTemplataT { kind: KindT::Struct(StructTT { id: IdT { local_name: INameT::Struct(StructNameT { template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("ShipA"), .. }), .. }), .. }, .. }) }), new_conclusion: ITemplataT::Kind(&KindTemplataT { kind: KindT::Struct(StructTT { id: IdT { local_name: INameT::Struct(StructNameT { template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("ShipB"), .. }), .. }), .. }, .. }) }), .. }), .. }, .. } => {}
        ICompileErrorT::TypingPassSolverError { failed_solve: FailedSolve { error: ISolverError::SolverConflict(SolverConflict { previous_conclusion: ITemplataT::Kind(&KindTemplataT { kind: KindT::Struct(StructTT { id: IdT { local_name: INameT::Struct(StructNameT { template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("ShipB"), .. }), .. }), .. }, .. }) }), new_conclusion: ITemplataT::Kind(&KindTemplataT { kind: KindT::Struct(StructTT { id: IdT { local_name: INameT::Struct(StructNameT { template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("ShipA"), .. }), .. }), .. }, .. }) }), .. }), .. }, .. } => {}
        ICompileErrorT::TypingPassSolverError { failed_solve: FailedSolve { error: ISolverError::RuleError(RuleError { err: ITypingPassSolverError::CallResultWasntExpectedType { actual: ITemplataT::Kind(&KindTemplataT { kind: KindT::Struct(StructTT { id: IdT { local_name: INameT::Struct(StructNameT { template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("ShipB"), .. }), .. }), .. }, .. }) }), .. }, .. }), .. }, .. } => {}
        ICompileErrorT::TypingPassSolverError { failed_solve: FailedSolve { error: ISolverError::RuleError(RuleError { err: ITypingPassSolverError::CallResultWasntExpectedType { actual: ITemplataT::Kind(&KindTemplataT { kind: KindT::Struct(StructTT { id: IdT { local_name: INameT::Struct(StructNameT { template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("ShipA"), .. }), .. }), .. }, .. }) }), .. }, .. }), .. }, .. } => {}
        // Rust-only extra arms: Rust's `.min()`-based solver firing order produces a
        // RuleError(InternalSolverError(SolverConflict(...))) shape that Scala's
        // HashMap-iteration-order solver doesn't hit for this test. See
        // docs/historical/nondeterministic-solver.md for the full investigation.
        ICompileErrorT::TypingPassSolverError { failed_solve: FailedSolve { error: ISolverError::RuleError(RuleError { err: ITypingPassSolverError::InternalSolverError { err: ISolverError::SolverConflict(SolverConflict { previous_conclusion: ITemplataT::Kind(&KindTemplataT { kind: KindT::Struct(StructTT { id: IdT { local_name: INameT::Struct(StructNameT { template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("ShipA"), .. }), .. }), .. }, .. }) }), new_conclusion: ITemplataT::Kind(&KindTemplataT { kind: KindT::Struct(StructTT { id: IdT { local_name: INameT::Struct(StructNameT { template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("ShipB"), .. }), .. }), .. }, .. }) }), .. }), .. }, .. }), .. }, .. } => {}
        ICompileErrorT::TypingPassSolverError { failed_solve: FailedSolve { error: ISolverError::RuleError(RuleError { err: ITypingPassSolverError::InternalSolverError { err: ISolverError::SolverConflict(SolverConflict { previous_conclusion: ITemplataT::Kind(&KindTemplataT { kind: KindT::Struct(StructTT { id: IdT { local_name: INameT::Struct(StructNameT { template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("ShipB"), .. }), .. }), .. }, .. }) }), new_conclusion: ITemplataT::Kind(&KindTemplataT { kind: KindT::Struct(StructTT { id: IdT { local_name: INameT::Struct(StructNameT { template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("ShipA"), .. }), .. }), .. }, .. }) }), .. }), .. }, .. }), .. }, .. } => {}
        other => panic!("vfail: {:#?}", other),
    }
    assert_humanized_eq(
        &humanize_compile_error(&mut compile, err),
        r##"At test:0.vale:5:1:
exported func main<N Kind>() where N Kind = ShipA, N Kind = ShipB {
At test:0.vale:5:1:
exported func main<N Kind>() where N Kind = ShipA, N Kind = ShipB {
Solver conflict on rune _123111: was ShipB but now concluding ShipA
exported func main<N Kind>() where N Kind = ShipA, N Kind = ShipB {
                                                            ^^^^^ _123111: ShipA
                                                   ^^^^^^ N: ShipA
                             ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ _3: (unknown)
                             ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ _3.kind: (unknown)
Steps:
Supplied:
  added rule: _113111.gen = "ShipA"
  added rule: _113111 = _113111.gen<>
  added rule: N = _113111
  added rule: _123111.gen = "ShipB"
  added rule: _123111 = _123111.gen<>
  added rule: N = _123111
  added rule: _3.kind = "void"
  added rule: coerceToCoord(_3, _3.kind)
_113111.gen = "ShipA"
  _113111.gen: ShipA
_113111 = _113111.gen<>
  _113111: ShipA
N = _113111
  N: ShipA
_123111.gen = "ShipB"
  _123111.gen: ShipB
_123111 = _123111.gen<>
  _123111: ShipB
N = _123111
Unsolved rule: coerceToCoord(_3, _3.kind)
Unsolved rule: _3.kind = "void"
Unsolved rule: N = _123111
Unsolved runes: _3 _3.kind
"##,
    );
}

#[test]
fn can_match_kind_templata_type_against_struct_env_entry_struct_templata() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

#!DeriveStructDrop
struct SomeStruct<T>
{ x T; }

func bork<X Kind, Z>() Z
where X Kind = SomeStruct<int>, X = SomeStruct<Z> {
  return 9;
}

exported func main() int {
  return bork();
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let bork = coutputs.lookup_function_by_str("bork");
    let template_args = match bork.header.id.local_name {
        INameT::Function(fn_name) => fn_name.template_args,
        _ => panic!("expected Function local_name"),
    };
    let last = *template_args.last().unwrap();
    assert_eq!(last, ITemplataT::Coord(typing_bump.alloc(CoordTemplataT { coord: CoordT { ownership: OwnershipT::Share, region: RegionT { region: IRegionT::Default }, kind: KindT::Int(IntT::I32) } })));
}

#[test]
fn can_destructure_and_assemble_static_sized_array() {

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

func swap<T>(x [#2]T) [#2]T {
  [a, b] = x;
  return [#](b, a);
}

exported func main() int {
  return swap([#](5, 7)).0;
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(get_embedded_modulized_code_map(&parse_arena, &parser_keywords))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();

    let swap = coutputs.lookup_function_by_str("swap");
    let swap_template_args = match swap.header.id.local_name {
        INameT::Function(fn_name) => fn_name.template_args,
        _ => panic!("expected Function local_name"),
    };
    match swap_template_args.last().unwrap() {
        ITemplataT::Coord(ct) => match ct.coord {
            CoordT { ownership: OwnershipT::Own, kind: KindT::KindPlaceholder(kp), .. } => match kp.id {
                IdT {
                    init_steps: [INameT::FunctionTemplate(FunctionTemplateNameT { human_name: StrI("swap"), .. })],
                    local_name: INameT::KindPlaceholder(KindPlaceholderNameT { template: KindPlaceholderTemplateNameT { index: 0, .. } }),
                    ..
                } => {}
                _ => panic!("expected KindPlaceholder local_name inside swap init_step"),
            },
            _ => panic!("expected Own KindPlaceholder coord"),
        },
        _ => panic!("expected Coord template arg"),
    }

    let main = coutputs.lookup_function_by_str("main");
    let call: &FunctionCallTE = collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::FunctionCall(c @ FunctionCallTE {
            callable: PrototypeT {
                id: IdT {
                    local_name: INameT::Function(FunctionNameT {
                        template: FunctionTemplateNameT { human_name: StrI("swap"), .. },
                        ..
                    }),
                    ..
                },
                ..
            },
            ..
        }) => Some(c)
    );
    let call_template_args = match call.callable.id.local_name {
        INameT::Function(fn_name) => fn_name.template_args,
        _ => panic!("expected Function local_name"),
    };
    match call_template_args.last().unwrap() {
        ITemplataT::Coord(ct) => match ct.coord {
            CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT::I32), .. } => {}
            _ => panic!("expected Share Int32 template arg"),
        },
        _ => panic!("expected Coord template arg"),
    }
}

#[test]
fn test_equivalent_identifying_runes_in_functions() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = "\nfunc bork<T, Y>(a T) Y where T = Y { return a; }\n";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn iragp_test_equivalent_identifying_runes_in_struct() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
#!DeriveStructDrop
struct Bork<T, Y> where T = Y { t T; y Y; }
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

