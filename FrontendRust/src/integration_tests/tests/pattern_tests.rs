use crate::instantiating::ast::citizens::IMemberTypeI;
use crate::instantiating::ast::names::INameI;
use crate::instantiating::ast::names::IStructTemplateNameI;
use crate::instantiating::ast::names::IdI;
use crate::instantiating::ast::names::StructNameI;
use crate::instantiating::ast::names::StructTemplateNameI;
use crate::instantiating::ast::types::CoordI;
use crate::instantiating::ast::types::IntIT;
use crate::instantiating::ast::types::KindIT;
use crate::instantiating::ast::types::OwnershipI;
use crate::instantiating::ast::types::StructIT;
use crate::instantiating::ast::types::cI;
use crate::integration_tests::tests::run_compilation::test;
use crate::interner::StrI;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::typing::types::types::CoordT;
use crate::typing::types::types::IRegionT;
use crate::typing::types::types::IntT;
use crate::typing::types::types::KindT;
use crate::typing::types::types::OwnershipT;
use crate::typing::types::types::RegionT;
use crate::typing::typing_interner::TypingInterner;
use crate::von::ast::IVonData;
use crate::von::ast::VonInt;
pub struct PatternTests;

#[test]
fn test_matching_a_multiple_member_seq_of_immutables() {
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
    // Checks that the 5 made it into y, and it was an int
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        "exported func main() int { [x, y] = (4, 5); return y; }",
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let main = coutputs.lookup_function_by_str("main");
        assert_eq!(main.header.return_type, CoordT {
            ownership: OwnershipT::Share,
            region: RegionT { region: IRegionT::Default },
            kind: KindT::Int(IntT::I32),
        });
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 5 }) => {}
        other => panic!("expected VonInt(5), got {:?}", other),
    }
}

#[test]
fn test_matching_a_multiple_member_seq_of_mutables() {
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
    // Checks that the 5 made it into y, and it was an int
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        "\nstruct Marine { hp int; }\nexported func main() int { [x, y] = (Marine(6), Marine(8)); return y.hp; }\n",
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let main = coutputs.lookup_function_by_str("main");
        assert_eq!(main.header.return_type, CoordT {
            ownership: OwnershipT::Share,
            region: RegionT { region: IRegionT::Default },
            kind: KindT::Int(IntT::I32),
        });
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 8 }) => {}
        other => panic!("expected VonInt(8), got {:?}", other),
    }
}

#[test]
fn test_matching_a_multiple_member_pack_of_immutable_and_own() {
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
    // Checks that the 5 made it into y, and it was an int
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        "\nstruct Marine { hp int; }\nexported func main() int { [x, y] = (7, Marine(8)); return y.hp; }\n",
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        // BUG: Scala uses `==` (a pure expression with discarded result) instead of `shouldEqual`; the assertion is dead.
        let _ = coutputs.functions[0].header.return_type == CoordT {
            ownership: OwnershipT::Share,
            region: RegionT { region: IRegionT::Default },
            kind: KindT::Int(IntT::I32),
        };
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 8 }) => {}
        other => panic!("expected VonInt(8), got {:?}", other),
    }
}

#[test]
fn test_matching_a_multiple_member_pack_of_immutable_and_borrow() {
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
    // Checks that the 5 made it into y, and it was an int
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        "\nstruct Marine { hp int; }\nexported func main() int {\n  m = Marine(8);\n  [x, y] = (7, &m);\n  return y.hp;\n}\n",
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        // BUG: Scala uses `==` (a pure expression with discarded result) instead of `shouldEqual`; the assertion is dead.
        let _ = coutputs.functions[0].header.return_type == CoordT {
            ownership: OwnershipT::Share,
            region: RegionT { region: IRegionT::Default },
            kind: KindT::Int(IntT::I32),
        };
    }
    {
        let monouts = compile.get_monouts();
        let tup_def = monouts.lookup_struct_by_name("Tup2");
        let tup_def_member_types: Vec<CoordI<'_, '_, cI>> = tup_def.members.iter().filter_map(|m| match m.tyype {
            IMemberTypeI::AddressMemberTypeI(t) => Some(t.reference),
            IMemberTypeI::ReferenceMemberTypeI(t) => Some(t.reference),
        }).collect();
        match tup_def_member_types.as_slice() {
            [
                CoordI {
                    ownership: OwnershipI::MutableShare,
                    kind: KindIT::IntIT(IntIT { bits: 32, .. }),
                },
                CoordI {
                    ownership: OwnershipI::MutableBorrow,
                    kind: KindIT::StructIT(StructIT {
                        id: IdI {
                            init_steps: &[],
                            local_name: INameI::StructName(StructNameI {
                                template: IStructTemplateNameI::StructTemplate(StructTemplateNameI {
                                    human_name: StrI("Marine"),
                                    ..
                                }),
                                template_args: &[],
                            }),
                            ..
                        },
                        ..
                    }),
                },
            ] => {}
            _ => panic!("tup_def_member_types shape mismatch"),
        }
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 8 }) => {}
        other => panic!("expected VonInt(8), got {:?}", other),
    }
}

#[test]
fn test_destructuring_a_shared() {
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
        "\nimport array.iter.*;\nexported func main() int {\n  sm = #[#](#[#](42, 73, 73));\n  foreach [i, m1] in sm {\n    return i;\n  }\n}\n",
    );
    {
        let _coutputs = compile.expect_compiler_outputs();
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("expected VonInt(42), got {:?}", other),
    }
}

#[test]
fn ignore_destructure() {
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
        "\nstruct Marine {\n  hp int;\n}\nexported func main() int {\n  m = Marine(4);\n  Marine[_] = m;\n  return 42;\n}\n",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("expected VonInt(42), got {:?}", other),
    }
}

