use crate::collect_only_snode;
use crate::collect_only_tnode;
use crate::collect_where_tnode;
use crate::integration_tests::tests::run_compilation::test;
use crate::integration_tests::tests::run_compilation::test_no_builtins;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::postparsing::expressions::ConstantBoolSE;
use crate::postparsing::expressions::ConstantIntSE;
use crate::postparsing::expressions::IExpressionSE;
use crate::postparsing::expressions::IfSE;
use crate::postparsing::expressions::ReturnSE;
use crate::postparsing::test::traverse::NodeRefS;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::tests::tests::load_expected;
use crate::typing::ast::expressions::IfTE;
use crate::typing::test::traverse::NodeRefT;
use crate::typing::types::types::CoordT;
use crate::typing::types::types::IRegionT;
use crate::typing::types::types::IntT;
use crate::typing::types::types::KindT;
use crate::typing::types::types::OwnershipT;
use crate::typing::types::types::RegionT;
use crate::typing::types::types::StrT;
use crate::typing::typing_interner::TypingInterner;
use crate::von::ast::IVonData;
use crate::von::ast::VonInt;
use crate::von::ast::VonStr;

pub struct IfTests;

#[test]
fn simple_true_branch_returning_an_int() {
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
  return if (true) { 3 } else { 5 };
}
",
    );
    {
        let test_str = scout_arena.intern_str("test");
        let package_coord = scout_arena.intern_package_coordinate(test_str, &[]);
        let file_coord = scout_arena.intern_file_coordinate(package_coord, "0.vale");
        let scoutput = compile.get_scoutput().expect("get_scoutput failed");
        let program_s = scoutput.file_coord_to_contents.get(file_coord).expect("file_coord not in scoutput");
        let main = program_s.lookup_function("main");
        let ret: &ReturnSE = collect_only_snode!(
            NodeRefS::Function(main),
            NodeRefS::Expression(IExpressionSE::Return(r)) => Some(r)
        );
        let iff: &IfSE = collect_only_snode!(
            NodeRefS::Expression(ret.inner),
            NodeRefS::Expression(IExpressionSE::If(i)) => Some(i)
        );
        collect_only_snode!(
            NodeRefS::Expression(iff.condition),
            NodeRefS::Expression(IExpressionSE::ConstantBool(ConstantBoolSE { value: true, .. })) => Some(())
        );
        collect_only_snode!(
            NodeRefS::Expression(iff.then_body.expr),
            NodeRefS::Expression(IExpressionSE::ConstantInt(ConstantIntSE { value: 3, .. })) => Some(())
        );
        collect_only_snode!(
            NodeRefS::Expression(iff.else_body.expr),
            NodeRefS::Expression(IExpressionSE::ConstantInt(ConstantIntSE { value: 5, .. })) => Some(())
        );
    }
    {
        let coutputs = compile.expect_compiler_outputs();
        let main = coutputs.lookup_function_by_str("main");
        collect_only_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::If(_) => Some(())
        );
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 3 }) => {}
        other => panic!("expected VonInt(3), got {:?}", other),
    }
}

#[test]
fn simple_false_branch_returning_an_int() {
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
  return if (false) { 3 } else { 5 };
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 5 }) => {}
        other => panic!("expected VonInt(5), got {:?}", other),
    }
}

#[test]
fn ladder() {
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
    let mut compile = test_no_builtins(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r"
exported func main() int {
  return if (false) { 3 } else if (true) { 5 } else { 7 };
}
",
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let main = coutputs.lookup_function_by_str("main");
        let ifs: Vec<&IfTE> = collect_where_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::If(if2) => Some(if2)
        );
        for iff in &ifs {
            assert_eq!(iff.result().coord, CoordT {
                ownership: OwnershipT::Share,
                region: RegionT { region: IRegionT::Default },
                kind: KindT::Int(IntT::I32),
            });
        }
        assert_eq!(ifs.len(), 2);
        let user_funcs = coutputs.get_all_user_functions();
        for func in &user_funcs {
            match func.header.return_type {
                CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT { bits: 32 }), .. } => {}
                CoordT { ownership: OwnershipT::Share, kind: KindT::Bool(_), .. } => {}
                other => panic!("vwat: {:?}", other),
            }
        }
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 5 }) => {}
        other => panic!("expected VonInt(5), got {:?}", other),
    }
}

#[test]
fn moving_from_inside_if() {
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
    let mut compile = test_no_builtins(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r"
struct Marine { x int; }
exported func main() int {
  m = Marine(5);
  return if (false) {
      [x] = m;
      x
    } else {
      [y] = m;
      y
    };
}
",
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let main = coutputs.lookup_function_by_str("main");
        let ifs: Vec<&IfTE> = collect_where_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::If(if2) => Some(if2)
        );
        for iff in &ifs {
            assert_eq!(iff.result().coord, CoordT {
                ownership: OwnershipT::Share,
                region: RegionT { region: IRegionT::Default },
                kind: KindT::Int(IntT::I32),
            });
        }
        let user_funcs = coutputs.get_all_user_functions();
        for func in &user_funcs {
            match func.header.return_type {
                CoordT { ownership: OwnershipT::Share, kind: KindT::Int(IntT { bits: 32 }), .. } => {}
                CoordT { ownership: OwnershipT::Share, kind: KindT::Bool(_), .. } => {}
                other => panic!("vwat: {:?}", other),
            }
        }
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 5 }) => {}
        other => panic!("expected VonInt(5), got {:?}", other),
    }
}

#[test]
fn if_with_complex_condition() {
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
        r##"
struct Marine { x int; }
exported func main() str {
  m = Marine(5);
  return if (m.x == 5) { "#" }
  else if (0 == 0) { "?" }
  else { "." };
}
"##,
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let main = coutputs.lookup_function_by_str("main");
        let ifs: Vec<&IfTE> = collect_where_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::If(if2) => Some(if2)
        );
        for iff in &ifs {
            assert_eq!(iff.result().coord, CoordT {
                ownership: OwnershipT::Share,
                region: RegionT { region: IRegionT::Default },
                kind: KindT::Str(StrT),
            });
        }
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Str(VonStr { value }) if value == "#" => {}
        other => panic!("expected VonStr(\"#\"), got {:?}", other),
    }
}

#[test]
fn if_with_condition_declaration() {
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
  return if x = 42; x < 50 { x }
    else { 73 };
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("expected VonInt(42), got {:?}", other),
    }
}

#[test]
fn ret_from_inside_if_will_destroy_locals() {
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
struct Marine { hp int; }
func drop(marine Marine) void {
  println("Destroying marine!");
  Marine[weapon] = marine;
}
exported func main() int {
  m = Marine(5);
  x =
    if (true) {
      println("In then!");
      return 7;
    } else {
      println("In else!");
      m.hp
    };
  println("In rest!");
  return x;
}
"#,
    );
    assert_eq!(compile.eval_for_stdout(Vec::new()).unwrap(), "In then!\nDestroying marine!\n");
}

#[test]
fn can_continue_if_other_branch_would_have_returned() {
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
struct Marine { hp int; }
func drop(marine Marine) void {
  println("Destroying marine!");
  Marine[weapon] = marine;
}
exported func main() int {
  m = Marine(5);
  x =
    if (false) {
      println("In then!");
      return 7;
    } else {
      println("In else!");
      m.hp
    };
  println("In rest!");
  return x;
}
"#,
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let _main = coutputs.lookup_function_by_str("main");
    }
    assert_eq!(compile.eval_for_stdout(Vec::new()).unwrap(), r"In else!
In rest!
Destroying marine!
");
}

#[test]
fn destructure_inside_if() {
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
struct Bork {
  num int;
}
struct Moo {
  bork Bork;
}

exported func main() {
  zork = 0;
  while (zork < 4) {
    moo = Moo(Bork(5));
    if (true) {
      [bork] = moo;
      println(bork.num);
    } else {
      drop(moo);
    }
    set zork = zork + 1;
  }
}
",
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let _main = coutputs.lookup_function_by_str("main");
    }
    assert_eq!(compile.eval_for_stdout(Vec::new()).unwrap(), r"5
5
5
5
");
}

#[test]
fn if_nevers() {
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
    let source = load_expected("programs/if/ifnevers.vale");
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        &source,
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("expected VonInt(42), got {:?}", other),
    }
}

#[test]
fn if_with_panics_and_rets() {
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
exported func main() int {
  a = 7;
  if false {
    panic("lol");
    return 73;
  } else {
    return 42;
  }
  return 73;
}

"#,
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("expected VonInt(42), got {:?}", other),
    }
}

#[test]
fn toast() {
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
  a = 0;
  if (a == 2) {
    return 71;
  } else if (a == 5) {
    return 73;
  } else {
    return 42;
  }
}
",
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let _main = coutputs.lookup_function_by_str("main");
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("expected VonInt(42), got {:?}", other),
    }
}

