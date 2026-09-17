use crate::collect_only_tnode;
use crate::integration_tests::tests::run_compilation::test;
use crate::interner::StrI;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::typing::ast::ast::ParameterT;
use crate::typing::ast::ast::PrototypeT;
use crate::typing::ast::expressions::FunctionCallTE;
use crate::typing::names::names::CodeVarNameT;
use crate::typing::names::names::FunctionNameT;
use crate::typing::names::names::FunctionTemplateNameT;
use crate::typing::names::names::INameT;
use crate::typing::names::names::IStructTemplateNameT;
use crate::typing::names::names::IVarNameT;
use crate::typing::names::names::IdT;
use crate::typing::names::names::StructNameT;
use crate::typing::names::names::StructTemplateNameT;
use crate::typing::templata::templata::CoordTemplataT;
use crate::typing::templata::templata::ITemplataT;
use crate::typing::test::traverse::NodeRefT;
use crate::typing::types::types::CoordT;
use crate::typing::types::types::KindT;
use crate::typing::types::types::OwnershipT;
use crate::typing::types::types::StructTT;
use crate::typing::typing_interner::TypingInterner;
use crate::von::ast::IVonData;
use crate::von::ast::VonInt;

pub struct InferTemplateTests;

#[test]
pub fn test_inferring_a_borrowed_argument() {
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
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r"
struct Muta { hp int; }
func moo<T>(m &T) &T { return m; }
exported func main() int {
  x = Muta(10);
  return moo(&x).hp;
}
",
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let moo = coutputs.lookup_function_by_str("moo");
        match moo.header.params {
            [ParameterT {
                name: IVarNameT::CodeVar(CodeVarNameT { name: StrI("m"), .. }),
                tyype: CoordT { ownership: OwnershipT::Borrow, .. },
                ..
            }] => {}
            _ => panic!("moo.header.params didn't match expected pattern"),
        }
        let main = coutputs.lookup_function_by_str("main");
        collect_only_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::FunctionCall(FunctionCallTE {
                callable: PrototypeT {
                    id: IdT {
                        local_name: INameT::Function(FunctionNameT {
                            template: FunctionTemplateNameT { human_name: StrI("moo"), .. },
                            template_args: &[ITemplataT::Coord(CoordTemplataT {
                                coord: CoordT {
                                    ownership: OwnershipT::Own,
                                    kind: KindT::Struct(StructTT {
                                        id: IdT {
                                            package_coord: x_package_coord,
                                            init_steps: &[],
                                            local_name: INameT::Struct(StructNameT {
                                                template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("Muta"), .. }),
                                                template_args: &[],
                                                ..
                                            }),
                                            ..
                                        },
                                        ..
                                    }),
                                    ..
                                },
                                ..
                            })],
                            ..
                        }),
                        ..
                    },
                    ..
                },
                ..
            }) if x_package_coord.is_test() => Some(())
        );
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 10 }) => {}
        other => panic!("expected VonInt(10), got {:?}", other),
    }
}

#[test]
pub fn test_inferring_a_borrowed_static_sized_array() {
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
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r"
struct Muta { hp int; }
func moo<N Int>(m &[#N]Muta) int { return m[0].hp; }
exported func main() int {
  x = [#](Muta(10));
  return moo(&x);
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 10 }) => {}
        other => panic!("expected VonInt(10), got {:?}", other),
    }
}

#[test]
pub fn test_inferring_an_owning_static_sized_array() {
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
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r"
struct Muta { hp int; }
func moo<N Int>(m [#N]Muta) int { return m[0].hp; }
exported func main() int {
  x = [#](Muta(10));
  return moo(x);
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 10 }) => {}
        other => panic!("expected VonInt(10), got {:?}", other),
    }
}

