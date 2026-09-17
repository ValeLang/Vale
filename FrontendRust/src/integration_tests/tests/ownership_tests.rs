use crate::collect_only_tnode;
use crate::collect_where_tnode;
use crate::integration_tests::tests::run_compilation::test;
use crate::interner::StrI;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::typing::ast::expressions::FunctionCallTE;
use crate::typing::env::function_environment_t::ILocalVariableT;
use crate::typing::env::function_environment_t::ReferenceLocalVariableT;
use crate::typing::names::names::FunctionNameT;
use crate::typing::names::names::FunctionTemplateNameT;
use crate::typing::names::names::INameT;
use crate::typing::names::names::IStructTemplateNameT;
use crate::typing::names::names::IVarNameT;
use crate::typing::names::names::IdT;
use crate::typing::names::names::StructNameT;
use crate::typing::names::names::StructTemplateNameT;
use crate::typing::templata::templata_utils::unapply_function_name_prototype;
use crate::typing::templata::templata_utils::unapply_simple_name;
use crate::typing::test::traverse::NodeRefT;
use crate::typing::types::types::CoordT;
use crate::typing::types::types::KindT;
use crate::typing::types::types::OwnershipT;
use crate::typing::types::types::StructTT;
use crate::typing::types::types::VariabilityT;
use crate::typing::typing_interner::TypingInterner;
use crate::von::ast::IVonData;
use crate::von::ast::VonInt;
pub struct OwnershipTests;

#[test]
fn borrowing_a_temporary_mutable_makes_a_local_var() {
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
exported func main() int {
  return (&Muta(9)).hp;
}
",
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let main = coutputs.lookup_function_by_str("main");
        collect_only_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::LetAndLend(let_te) if matches!(
                let_te.variable,
                ILocalVariableT::Reference(ReferenceLocalVariableT {
                    name: IVarNameT::TypingPassTemporaryVar(_),
                    variability: VariabilityT::Final,
                    ..
                })
            ) => {
                match let_te.expr.result().coord {
                    CoordT {
                        ownership: OwnershipT::Own,
                        kind: KindT::Struct(StructTT { id, .. }),
                        ..
                    } if unapply_simple_name(&id) == Some("Muta".to_string()) => {}
                    other => panic!("unexpected coord: {:?}", other),
                }
                assert_eq!(let_te.target_ownership, OwnershipT::Borrow);
                assert_eq!(let_te.result().coord.ownership, OwnershipT::Borrow);
                Some(())
            }
        );
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 9 }) => {}
        other => panic!("Expected VonInt(9), got {:?}", other),
    }
}

#[test]
fn owning_ref_method_call() {
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
func take(m Muta) int {
  return m.hp;
}
exported func main() int {
  m = Muta(9);
  return (m).hp;
}
",
    );
    {
        let _main = compile.expect_compiler_outputs().lookup_function_by_str("main");
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 9 }) => {}
        other => panic!("Expected VonInt(9), got {:?}", other),
    }
}

#[test]
fn derive_drop() {
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
import printutils.*;

struct Muta { }

exported func main() {
  Muta();
}
",
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let main = coutputs.lookup_function_by_str("main");
        collect_only_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::FunctionCall(FunctionCallTE { callable, .. })
                if unapply_function_name_prototype(callable) == Some("drop".to_string())
                => Some(())
        );
        let matches = collect_where_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::FunctionCall(_) => Some(())
        );
        assert_eq!(matches.len(), 2);
    }
    compile.eval_for_kind_primitive_args(Vec::new()).unwrap();
}

#[test]
fn custom_drop_result_is_an_owning_ref_calls_destructor() {
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
        r#"
import printutils.*;

#!DeriveStructDrop
struct Muta { }

func drop(m ^Muta) void {
  println("Destroying!");
  Muta[ ] = m;
}

exported func main() {
  Muta();
}
"#,
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let main = coutputs.lookup_function_by_str("main");
        collect_only_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::FunctionCall(FunctionCallTE { callable, .. })
                if unapply_function_name_prototype(callable) == Some("drop".to_string())
                => Some(())
        );
        let matches = collect_where_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::FunctionCall(_) => Some(())
        );
        assert_eq!(matches.len(), 2);
    }
    assert_eq!(compile.eval_for_stdout(Vec::new()).unwrap(), "Destroying!\n");
}

#[test]
fn saves_return_value_then_destroys_temporary() {
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
        r#"
import printutils.*;

#!DeriveStructDrop
struct Muta { hp int; }

func drop(m ^Muta) {
  println("Destroying!");
  Muta[hp] = m;
}

exported func main() int {
  return (Muta(10)).hp;
}
"#,
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let main = coutputs.lookup_function_by_str("main");
        collect_only_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::FunctionCall(FunctionCallTE { callable, .. })
                if unapply_function_name_prototype(callable) == Some("drop".to_string())
                => Some(())
        );
    }
    match compile.eval_for_kind_and_stdout(Vec::new()).unwrap() {
        (IVonData::Int(VonInt { value: 10 }), ref s) if s == "Destroying!\n" => {}
        other => panic!("expected (VonInt(10), \"Destroying!\\n\"), got {:?}", other),
    }
}

#[test]
fn calls_destructor_on_local_var() {
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
        r#"
import printutils.*;

#!DeriveStructDrop
struct Muta { }

func drop(m ^Muta) {
  println("Destroying!");
  Muta[ ] = m;
}

exported func main() {
  a = Muta();
}
"#,
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let main = coutputs.lookup_function_by_str("main");
        collect_only_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::FunctionCall(FunctionCallTE { callable, .. })
                if unapply_function_name_prototype(callable) == Some("drop".to_string())
                => Some(())
        );
        let matches = collect_where_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::FunctionCall(_) => Some(())
        );
        assert_eq!(matches.len(), 2);
    }
    assert_eq!(compile.eval_for_stdout(Vec::new()).unwrap(), "Destroying!\n");
}

#[test]
fn calls_destructor_on_local_var_unless_moved() {
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
    // Should call the destructor in moo, but not in main
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r#"
import printutils.*;

#!DeriveStructDrop
struct Muta { }

func drop(m ^Muta) {
  println("Destroying!");
  Muta[ ] = m;
}

func moo(m ^Muta) {
}

exported func main() {
  a = Muta();
  moo(a);
}
"#,
    );
    {
        let coutputs = compile.expect_compiler_outputs();

        // Destructor should only be calling println, NOT the destructor (itself)
        let destructor = coutputs.functions.iter().find(|func| {
            matches!(func.header.id.local_name,
                INameT::Function(FunctionNameT {
                    template: FunctionTemplateNameT { human_name: StrI("drop"), .. },
                    parameters: [CoordT {
                        ownership: OwnershipT::Own,
                        kind: KindT::Struct(StructTT {
                            id: IdT {
                                local_name: INameT::Struct(StructNameT {
                                    template: IStructTemplateNameT::StructTemplate(StructTemplateNameT {
                                        human_name: StrI("Muta"), ..
                                    }),
                                    ..
                                }),
                                ..
                            },
                            ..
                        }),
                        ..
                    }],
                    ..
                })
            )
        }).unwrap();
        // The only function lookup should be println
        collect_only_tnode!(
            NodeRefT::FunctionDefinition(destructor),
            NodeRefT::FunctionCall(FunctionCallTE { callable, .. })
                if unapply_function_name_prototype(callable) == Some("println".to_string())
                => Some(())
        );
        // Only one call (the above println)
        let destructor_calls = collect_where_tnode!(
            NodeRefT::FunctionDefinition(destructor),
            NodeRefT::FunctionCall(_) => Some(())
        );
        assert_eq!(destructor_calls.len(), 1);

        // moo should be calling the destructor
        let moo = coutputs.lookup_function_by_str("moo");
        collect_only_tnode!(
            NodeRefT::FunctionDefinition(moo),
            NodeRefT::FunctionCall(FunctionCallTE { callable, .. })
                if unapply_function_name_prototype(callable) == Some("drop".to_string())
                => Some(())
        );
        collect_only_tnode!(
            NodeRefT::FunctionDefinition(moo),
            NodeRefT::FunctionCall(_) => Some(())
        );

        // main should not be calling the destructor
        let main = coutputs.lookup_function_by_str("main");
        let main_drops = collect_where_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::FunctionCall(FunctionCallTE { callable, .. })
                if unapply_function_name_prototype(callable) == Some("drop".to_string())
                => Some(true)
        );
        assert_eq!(main_drops.len(), 0);
    }
    assert_eq!(compile.eval_for_stdout(Vec::new()).unwrap(), "Destroying!\n");
}

#[test]
fn saves_return_value_then_destroys_local_var() {
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
        r#"
import printutils.*;

#!DeriveStructDrop
struct Muta { hp int; }

func drop(m ^Muta) {
  println("Destroying!");
  Muta[hp] = m;
}

exported func main() int {
  a = Muta(10);
  return a.hp;
}
"#,
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let main = coutputs.lookup_function_by_str("main");
        collect_only_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::FunctionCall(FunctionCallTE { callable, .. })
                if unapply_function_name_prototype(callable) == Some("drop".to_string())
                => Some(())
        );
        let matches = collect_where_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::FunctionCall(_) => Some(())
        );
        assert_eq!(matches.len(), 2);
    }
    match compile.eval_for_kind_and_stdout(Vec::new()).unwrap() {
        (IVonData::Int(VonInt { value: 10 }), ref s) if s == "Destroying!\n" => {}
        other => panic!("expected (VonInt(10), \"Destroying!\\n\"), got {:?}", other),
    }
}

#[test]
fn gets_from_temporary_struct_a_members_member() {
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
struct Wand {
  charges int;
}
struct Wizard {
  wand ^Wand;
}
exported func main() int {
  return Wizard(Wand(10)).wand.charges;
}
      ",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 10 }) => {}
        other => panic!("Expected VonInt(10), got {:?}", other),
    }
}

#[test]
fn unstackifies_local_vars() {
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
exported func main() int {
  i = 0;
  return i;
}
",
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_function_by_str("main");
    let num_variables: Vec<()> = collect_where_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::LetAndLend(_) | NodeRefT::LetNormal(_) => Some(())
    );
    let unlets: Vec<()> = collect_where_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::Unlet(_) => Some(())
    );
    assert_eq!(unlets.len(), num_variables.len());
}

#[test]
fn basic_builder_pattern() {
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
struct Ship { hp! int; fuel! int; }
func setHp(ship Ship, hp int) Ship {
  set ship.hp = hp;
  return ship;
}
func setFuel(ship Ship, fuel int) Ship {
  set ship.fuel = fuel;
  return ship;
}
exported func main() int {
  ship = Ship(0, 0).setHp(42).setFuel(43);
  return ship.hp;
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("Expected VonInt(42), got {:?}", other),
    }
}

#[test]
fn member_access_on_returned_owning_ref() {
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
struct Ship { hp int; }
exported func main() int {
  return Ship(42).hp;
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("Expected VonInt(42), got {:?}", other),
    }
}

