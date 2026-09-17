// cargo test --manifest-path FrontendRust/Cargo.toml --lib parsing::tests::top_level_tests

#![allow(nonstandard_style)]

use bumpalo::Bump;
use crate::interner::StrI;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::lexing::ParseError;
use crate::parsing::ast::*;
use crate::parsing::tests::utils::*;

#[test]
fn function_then_struct() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, "exported func main() int {} struct mork { }");
  assert!(matches!(
    program.denizens[0],
    IDenizenP::TopLevelFunction(_)
  ));
  assert!(matches!(program.denizens[1], IDenizenP::TopLevelStruct(_)));
}

#[test]
fn ellipses_ignored() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  // Unicode … symbol is treated as an expression by the parser
  compile(&parse_arena, &keywords, "exported func main() int {x = …;}");
  compile(&parse_arena, &keywords, "exported func main() int {set x = …;}");
  // Three dots is treated as a comment
  compile(&parse_arena, &keywords, "exported func main(...) int {}");
  compile(&parse_arena, &keywords, "exported func main() ... {}");
  compile(&parse_arena, &keywords, "exported func main() int {} ... ");
  compile(&parse_arena, &keywords, "exported func main() int {...}");
  compile(&parse_arena, &keywords, "exported func main() int {moo(...)}");
  compile(&parse_arena, &keywords, "struct Moo {} ... ");
  compile(&parse_arena, &keywords, "struct Moo {...}");
}

#[test]
fn comments_ignored() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  compile(
    &parse_arena,
    &keywords,
    r#"
        exported func main(
                // moo
        ) int {}
        "#,
  );
  compile(
    &parse_arena,
    &keywords,
    r#"
        exported func main()
                // moo
        {}
        "#,
  );
  compile(
    &parse_arena,
    &keywords,
    r#"
        exported func main() int {}
                // moo
        "#,
  );
  compile(
    &parse_arena,
    &keywords,
    r#"
        exported func main() int {
                // moo
        }
        "#,
  );
  compile(
    &parse_arena,
    &keywords,
    r#"
        exported func main() int {
          moo(
                // moo
          )
        }
        "#,
  );
  compile(
    &parse_arena,
    &keywords,
    r#"
        struct Moo {}
                // moo
        "#,
  );
  compile(
    &parse_arena,
    &keywords,
    r#"
        struct Moo {
                // moo
        }
        "#,
  );
  compile(
    &parse_arena,
    &keywords,
    r#"
        struct Moo {
        }
        // moo
        "#,
  );
}

#[test]
fn function_containing_if() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(
    &parse_arena,
    &keywords,
    r#"
        func main() int {
          if true { 3 } else { 4 }
        }
        "#,
  );
  let main = find_func_named(&program, "main");
  assert!(main.body.is_some());
}

#[test]
fn reports_unrecognized_at_top_level() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let err = compile_for_error(
    &parse_arena,
    &keywords,
    r#"
      func main(){}
      blort
      "#,
  );
  assert!(matches!(err, ParseError::UnrecognizedDenizenError(_)));
}

// lol
#[test]
fn funky_function() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(
    &parse_arena,
    &keywords,
    r#"
      funky main() { }
      "#,
  );
  find_func_named(&program, "main");
}

#[test]
fn empty() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(
    &parse_arena,
    &keywords,
    r#"
      func foo() { ... }
      "#,
  );
  let main = &program.denizens[0];
  assert!(matches!(
    main,
    IDenizenP::TopLevelFunction(FunctionP {
      body:
        Some(BlockPE {
          inner: IExpressionPE::Void(VoidPE { .. }),
          ..
        }),
      ..
    })
  ));
}

#[test]
fn exporting_int() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, "export int as NumberThing;");
  assert!(
    matches!(program.denizens[0], IDenizenP::TopLevelExportAs(ExportAsP {
    struct_: ITemplexPT::NameOrRune(NameOrRunePT(NameP(_, StrI("int")))),
    exported_name: NameP(_, StrI("NumberThing")),
    ..
  }))
  );
}

#[test]
fn exporting_imm_array_1() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, "export []<mut>int as IntArray;");
  assert!(
    matches!(program.denizens[0], IDenizenP::TopLevelExportAs(ExportAsP {
    exported_name: NameP(_, StrI("IntArray")),
    ..
  }))
  );
}

#[test]
fn exporting_imm_array_2() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, "export #[]int as IntArray;");
  assert!(
    matches!(program.denizens[0], IDenizenP::TopLevelExportAs(ExportAsP {
    exported_name: NameP(_, StrI("IntArray")),
    ..
  }))
  );
}

#[test]
fn import_wildcard() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, "import somemodule.*;");
  assert!(
    matches!(program.denizens[0], IDenizenP::TopLevelImport(ImportP {
    module_name: NameP(_, StrI("somemodule")),
    package_steps: [],
    importee_name: NameP(_, StrI("*")),
    ..
  }))
  );
}

#[test]
fn import_just_module_and_thing() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, "import somemodule.List;");
  assert!(
    matches!(program.denizens[0], IDenizenP::TopLevelImport(ImportP {
    module_name: NameP(_, StrI("somemodule")),
    package_steps: [],
    importee_name: NameP(_, StrI("List")),
    ..
  }))
  );
}

#[test]
fn full_import() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, "import somemodule.subpackage.List;");
  assert!(
    matches!(program.denizens[0], IDenizenP::TopLevelImport(ImportP {
    module_name: NameP(_, StrI("somemodule")),
    package_steps: [NameP(_, StrI("subpackage"))],
    importee_name: NameP(_, StrI("List")),
    ..
  }))
  );
}

#[test]
fn return_with_region_generics() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, "func strongestDesire() IDesire<r', i'> { }");
  let func = find_func_named(&program, "strongestDesire");
  match func.header.ret.ret_type {
    Some(ITemplexPT::Call(CallPT {
      template: ITemplexPT::NameOrRune(NameOrRunePT(NameP(_, StrI("IDesire")))),
      args: [ITemplexPT::RegionRune(RegionRunePT { name: Some(NameP(_, StrI("r"))), .. }),
             ITemplexPT::RegionRune(RegionRunePT { name: Some(NameP(_, StrI("i"))), .. })],
      ..
    })) => {}
    _ => panic!("Expected return type IDesire<r', i'>"),
  }
}

#[test]
fn bad_start_of_statement() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let err = compile_for_error(
    &parse_arena,
    &keywords,
    r#"
    func doCivicDance(virtual this Car) {
      )
    }
    "#,
  );
  assert!(matches!(err, ParseError::BadStartOfStatementError(_)));
  let err = compile_for_error(
    &parse_arena,
    &keywords,
    r#"
    func doCivicDance(virtual this Car) {
      ]
    }
    "#,
  );
  assert!(matches!(err, ParseError::BadStartOfStatementError(_)));
}

