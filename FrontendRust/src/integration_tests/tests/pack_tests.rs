use crate::collect_where_tnode;
use crate::integration_tests::tests::run_compilation::test;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::typing::ast::expressions::ReferenceExpressionTE;
use crate::typing::ast::expressions::TupleTE;
use crate::typing::test::traverse::NodeRefT;
use crate::typing::typing_interner::TypingInterner;
use crate::von::ast::IVonData;
use crate::von::ast::VonInt;

pub struct PackTests;

#[test]
fn extract_seq() {
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
  [x, y] = (5, 6);
  return x;
}
",
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let main = coutputs.lookup_function_by_str("main");
        let matches = collect_where_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::Tuple(TupleTE { elements: &[_, _], .. }) => Some(())
        );
        assert_eq!(matches.len(), 1);
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 5 }) => {}
        other => panic!("Expected VonInt(5), got {:?}", other),
    }
}

#[test]
fn nested_seqs() {
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
  [x, [y, z]] = ((4, 5), (6, 7));
  return y;
}
",
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let main = coutputs.lookup_function_by_str("main");
        let matches = collect_where_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::Tuple(TupleTE {
                elements: &[
                    ReferenceExpressionTE::Tuple(TupleTE { elements: &[_, _], .. }),
                    ReferenceExpressionTE::Tuple(TupleTE { elements: &[_, _], .. }),
                ],
                ..
            }) => Some(())
        );
        assert_eq!(matches.len(), 1);
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 6 }) => {}
        other => panic!("Expected VonInt(6), got {:?}", other),
    }
}

#[test]
fn nested_tuples() {
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
  [x, [y, z]] = (5, (6, false));
  return x;
}
",
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let main = coutputs.lookup_function_by_str("main");
        let matches = collect_where_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::Tuple(TupleTE {
                elements: &[
                    _,
                    ReferenceExpressionTE::Tuple(TupleTE { elements: &[_, _], .. }),
                ],
                ..
            }) => Some(())
        );
        assert_eq!(matches.len(), 1);
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 5 }) => {}
        other => panic!("Expected VonInt(5), got {:?}", other),
    }
}

