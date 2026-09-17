// cargo test --manifest-path FrontendRust/Cargo.toml --lib parsing::tests::statement_tests

use bumpalo::Bump;
use crate::interner::StrI;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::lexing::errors::ParseError;
use crate::parsing::ast::*;
use crate::parsing::tests::utils::*;

#[test]
fn simple_let() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_block_contents_expect(&parse_arena, &keywords, "x = 4;");
  match &expr {
    IExpressionPE::Consecutor(ConsecutorPE {
      inners: [
        IExpressionPE::Let(LetPE {
          pattern: PatternPP {
            destination: Some(DestinationLocalP {
              decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("x"))),
              ..
            }),
            templex: None,
            destructure: None,
            ..
          },
          source: IExpressionPE::ConstantInt(ConstantIntPE { value: 4, .. }),
          ..
        }),
        IExpressionPE::Void(_),
        ..
      ],
      ..
    }) => {}
    _ => panic!("expected x = 4; structure"),
  }
}

#[test]
fn multiple_statements() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_block_contents_expect(&parse_arena, &keywords, "4");
  match &expr {
    IExpressionPE::ConstantInt(ConstantIntPE { value: 4, .. }) => {}
    _ => panic!("expected 4"),
  }

  let expr = compile_block_contents_expect(&parse_arena, &keywords, "4;");
  match &expr {
    IExpressionPE::Consecutor(ConsecutorPE {
      inners: [
        IExpressionPE::ConstantInt(ConstantIntPE { value: 4, .. }),
        IExpressionPE::Void(_),
        ..
      ],
      ..
    }) => {}
    _ => panic!("expected 4;"),
  }

  let expr = compile_block_contents_expect(&parse_arena, &keywords, "4; 3");
  match &expr {
    IExpressionPE::Consecutor(ConsecutorPE {
      inners: [
        IExpressionPE::ConstantInt(ConstantIntPE { value: 4, .. }),
        IExpressionPE::ConstantInt(ConstantIntPE { value: 3, .. }),
        ..
      ],
      ..
    }) => {}
    _ => panic!("expected 4; 3"),
  }

  let expr = compile_block_contents_expect(&parse_arena, &keywords, "4; 3;");
  match &expr {
    IExpressionPE::Consecutor(ConsecutorPE {
      inners: [
        IExpressionPE::ConstantInt(ConstantIntPE { value: 4, .. }),
        IExpressionPE::ConstantInt(ConstantIntPE { value: 3, .. }),
        IExpressionPE::Void(_),
        ..
      ],
      ..
    }) => {}
    _ => panic!("expected 4; 3;"),
  }
}

#[test]
fn test_8() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "[x, y] = (4, 5);");
  match &expr {
    IExpressionPE::Let(LetPE {
      pattern: PatternPP {
        destination: None,
        templex: None,
        destructure: Some(DestructureP {
          patterns: [
            PatternPP {
              destination: Some(DestinationLocalP {
                decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("x"))),
                ..
              }),
              templex: None,
              destructure: None,
              ..
            },
            PatternPP {
              destination: Some(DestinationLocalP {
                decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("y"))),
                ..
              }),
              templex: None,
              destructure: None,
              ..
            },
            ..
          ],
          ..
        }),
        ..
      },
      source: IExpressionPE::Tuple(TuplePE {
        elements: [
          IExpressionPE::ConstantInt(ConstantIntPE { value: 4, .. }),
          IExpressionPE::ConstantInt(ConstantIntPE { value: 5, .. }),
          ..
        ],
        ..
      }),
      ..
    }) => {}
    _ => panic!("expected [x, y] = (4, 5); structure"),
  }
}

#[test]
fn test_9() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "set x.a = 5;");
  match &expr {
    IExpressionPE::Mutate(MutatePE {
      mutatee: IExpressionPE::Dot(DotPE {
        left: IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
          template_args: None,
        }),
        member: NameP(_, StrI("a")),
        ..
      }),
      source: IExpressionPE::ConstantInt(ConstantIntPE { value: 5, .. }),
      ..
    }) => {}
    _ => panic!("expected set x.a = 5; structure"),
  }
}

#[test]
fn test_1_pe() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, r#"set board.PE.PE.symbol = "v";"#);
  match &expr {
    IExpressionPE::Mutate(MutatePE {
      mutatee: IExpressionPE::Dot(DotPE {
        left: IExpressionPE::Dot(DotPE {
          left: IExpressionPE::Dot(DotPE {
            left: IExpressionPE::Lookup(LookupPE {
              name: IImpreciseNameP::LookupName(NameP(_, StrI("board"))),
              template_args: None,
            }),
            member: NameP(_, StrI("PE")),
            ..
          }),
          member: NameP(_, StrI("PE")),
          ..
        }),
        member: NameP(_, StrI("symbol")),
        ..
      }),
      source: IExpressionPE::ConstantStr(ConstantStrPE { value: StrI("v"), .. }),
      ..
    }) =>
    {}
    _ => panic!(r#"expected set board.PE.PE.symbol = "v"; structure"#),
  }
}

#[test]
fn test_simple_let() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "x = 3;");
  match &expr {
    IExpressionPE::Let(LetPE {
      pattern: PatternPP {
        destination: Some(DestinationLocalP {
                decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("x"))),
          ..
        }),
        templex: None,
        destructure: None,
        ..
      },
      source: IExpressionPE::ConstantInt(ConstantIntPE { value: 3, .. }),
      ..
    }) => {}
    _ => panic!("expected x = 3; structure"),
  }
}

#[test]
fn test_simple_mut() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "set x = 5;");
  match &expr {
    IExpressionPE::Mutate(MutatePE {
      mutatee: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
        template_args: None,
      }),
      source: IExpressionPE::ConstantInt(ConstantIntPE { value: 5, .. }),
      ..
    }) => {}
    _ => panic!("expected set x = 5; structure"),
  }
}

#[test]
fn test_expr_starting_with_return() {
  // This test is here because we had a bug where we didn't check that there
  // was whitespace after a "return".
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "retcode()");
  match &expr {
    IExpressionPE::FunctionCall(FunctionCallPE {
      callable_expr: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("retcode"))),
        template_args: None,
      }),
      arg_exprs: [],
      ..
    }) => {}
    _ => panic!("expected retcode() structure"),
  }
}

#[test]
fn test_inner_set() {
  // This test is here because we had a bug where we didn't check that there
  // was whitespace after a "return".
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "oldArray = set list.array = newArray;");
  match &expr {
    IExpressionPE::Let(LetPE {
      pattern: PatternPP {
        destination: Some(DestinationLocalP {
          decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("oldArray"))),
          ..
        }),
        templex: None,
        destructure: None,
        ..
      },
      source: IExpressionPE::Mutate(MutatePE {
        mutatee: IExpressionPE::Dot(DotPE {
          left: IExpressionPE::Lookup(LookupPE {
            name: IImpreciseNameP::LookupName(NameP(_, StrI("list"))),
            template_args: None,
          }),
          member: NameP(_, StrI("array")),
          ..
        }),
        source: IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("newArray"))),
          template_args: None,
        }),
        ..
      }),
      ..
    }) =>
    {}
    _ => panic!("expected oldArray = set list.array = newArray; structure"),
  }
}

#[test]
fn test_if_statement_producing() {
  // This test is here because we had a bug where we didn't check that there
  // was whitespace after a "return".
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "if true { 3 } else { 4 }");
  match &expr {
    IExpressionPE::If(IfPE {
      condition: IExpressionPE::ConstantBool(ConstantBoolPE { value: true, .. }),
      then_body: BlockPE {
        inner: IExpressionPE::ConstantInt(ConstantIntPE { value: 3, .. }),
        ..
      },
      else_body: BlockPE {
        inner: IExpressionPE::ConstantInt(ConstantIntPE { value: 4, .. }),
        ..
      },
      ..
    }) => {}
    _ => panic!("expected if true {{ 3 }} else {{ 4 }} structure"),
  }
}

#[test]
fn test_destruct() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "destruct x;");
  match &expr {
    IExpressionPE::Destruct(DestructPE {
      inner: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
        template_args: None,
      }),
      ..
    }) => {}
    _ => panic!("expected destruct x; structure"),
  }
}

#[test]
fn test_unlet() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "unlet x");
  match &expr {
    IExpressionPE::Unlet(UnletPE {
      name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
      ..
    }) => {}
    _ => panic!("expected unlet x structure"),
  }
}

#[test]
fn dot_on_function_calls_result() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "Wizard(8).charges");
  match &expr {
    IExpressionPE::Dot(DotPE {
      left: IExpressionPE::FunctionCall(FunctionCallPE {
        callable_expr: IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("Wizard"))),
          template_args: None,
        }),
        arg_exprs: [IExpressionPE::ConstantInt(ConstantIntPE { value: 8, .. }), ..],
        ..
      }),
      member: NameP(_, StrI("charges")),
      ..
    }) => {}
    _ => panic!("expected Wizard(8).charges structure"),
  }
}

#[test]
fn let_with_pattern_with_only_a_capture() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "a = m;");
  match &expr {
    IExpressionPE::Let(LetPE {
      pattern: PatternPP {
        destination: Some(DestinationLocalP {
          decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("a"))),
          ..
        }),
        templex: None,
        destructure: None,
        ..
      },
      source: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("m"))),
        template_args: None,
      }),
      ..
    }) => {}
    _ => panic!("expected a = m; structure"),
  }
}

#[test]
fn let_with_simple_pattern() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "a Moo = m;");
  match &expr {
    IExpressionPE::Let(LetPE {
      pattern: PatternPP {
        destination: Some(DestinationLocalP {
          decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("a"))),
          ..
        }),
        templex: Some(ITemplexPT::NameOrRune(NameOrRunePT(NameP(_, StrI("Moo"))))),
        destructure: None,
        ..
      },
      source: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("m"))),
        template_args: None,
      }),
      ..
    }) => {}
    _ => panic!("expected a Moo = m; structure"),
  }
}

#[test]
fn let_with_simple_pattern_in_destructure() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "[a Moo] = m;");
  match &expr {
    IExpressionPE::Let(LetPE {
      pattern: PatternPP {
        destination: None,
        templex: None,
        destructure: Some(DestructureP {
          patterns: [PatternPP {
            destination: Some(DestinationLocalP {
              decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("a"))),
              ..
            }),
            templex: Some(ITemplexPT::NameOrRune(NameOrRunePT(NameP(_, StrI("Moo"))))),
            destructure: None,
            ..
          }, ..],
          ..
        }),
        ..
      },
      source: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("m"))),
        template_args: None,
      }),
      ..
    }) => {}
    _ => panic!("expected [a Moo] = m; structure"),
  }
}

#[test]
fn let_with_destructuring_pattern() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "Muta[ ] = m;");
  match &expr {
    IExpressionPE::Let(LetPE {
      pattern: PatternPP {
        destination: None,
        templex: Some(ITemplexPT::NameOrRune(NameOrRunePT(NameP(_, StrI("Muta"))))),
        destructure: Some(DestructureP { patterns: [], .. }),
        ..
      },
      source: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("m"))),
        template_args: None,
      }),
      ..
    }) => {}
    _ => panic!("expected Muta[ ] = m; structure"),
  }
}

#[test]
fn destructure_pattern_with_let_and_set() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "[a, set x] = m;");
  match &expr {
    IExpressionPE::Let(LetPE {
      pattern: PatternPP {
        destination: None,
        templex: None,
        destructure: Some(DestructureP {
          patterns: [
            PatternPP {
              destination: Some(DestinationLocalP {
                decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("a"))),
                mutate: None,
                ..
              }),
              templex: None,
              destructure: None,
              ..
            },
            PatternPP {
              destination: Some(DestinationLocalP {
                decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("x"))),
                mutate: Some(_),
                ..
              }),
              templex: None,
              destructure: None,
              ..
            },
            ..
          ],
          ..
        }),
        ..
      },
      ..
    }) => {}
    _ => panic!("expected [a, set x] = m; structure"),
  }
}

#[test]
fn ret() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "return 3;");
  match &expr {
    IExpressionPE::Return(ReturnPE {
      expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 3, .. }),
      ..
    }) => {}
    _ => panic!("expected return 3; structure"),
  }
}

#[test]
fn foreach() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "foreach i in myList { }");
  match &expr {
    IExpressionPE::Each(EachPE {
      maybe_pure: None,
      entry_pattern: PatternPP {
        destination: Some(DestinationLocalP {
          decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("i"))),
          ..
        }),
        templex: None,
        destructure: None,
        ..
      },
      iterable_expr: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("myList"))),
        template_args: None,
      }),
      body: BlockPE {
        inner: IExpressionPE::Void(_),
        ..
      },
      ..
    }) => {}
    _ => panic!("expected foreach i in myList {{ }} structure"),
  }
}

#[test]
fn foreach_with_borrow() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "foreach i in &myList { }");
  match &expr {
    IExpressionPE::Each(EachPE {
      maybe_pure: None,
      entry_pattern: PatternPP {
        destination: Some(DestinationLocalP {
          decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("i"))),
          ..
        }),
        templex: None,
        destructure: None,
        ..
      },
      iterable_expr: IExpressionPE::Augment(AugmentPE {
        target_ownership: OwnershipP::Borrow,
        inner: IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("myList"))),
          template_args: None,
        }),
        ..
      }),
      body: BlockPE {
        inner: IExpressionPE::Void(_),
        ..
      },
      ..
    }) => {}
    _ => panic!("expected foreach i in &myList {{ }} structure"),
  }
}

#[test]
fn foreach_with_two_receivers() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "foreach [a, b] in myList { }");
  match &expr {
    IExpressionPE::Each(EachPE {
      maybe_pure: None,
      entry_pattern: PatternPP {
        destination: None,
        templex: None,
        destructure: Some(DestructureP {
          patterns: [
            PatternPP {
              destination: Some(DestinationLocalP {
                decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("a"))),
                ..
              }),
              templex: None,
              destructure: None,
              ..
            },
            PatternPP {
              destination: Some(DestinationLocalP {
                decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("b"))),
                ..
              }),
              templex: None,
              destructure: None,
              ..
            },
            ..
          ],
          ..
        }),
        ..
      },
      iterable_expr: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("myList"))),
        template_args: None,
      }),
      body: BlockPE {
        inner: IExpressionPE::Void(_),
        ..
      },
      ..
    }) => {}
    _ => panic!("expected foreach [a, b] in myList {{ }} structure"),
  }
}

#[test]
fn foreach_complex_iterable() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "foreach i in myList = 3; myList { }");
  match &expr {
    IExpressionPE::Each(EachPE {
      maybe_pure: None,
      entry_pattern: PatternPP {
        destination: Some(DestinationLocalP {
          decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("i"))),
          ..
        }),
        templex: None,
        destructure: None,
        ..
      },
      iterable_expr: IExpressionPE::Consecutor(ConsecutorPE {
        inners: [
          IExpressionPE::Let(LetPE {
            pattern: PatternPP {
              destination: Some(DestinationLocalP {
                decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("myList"))),
                ..
              }),
              templex: None,
              destructure: None,
              ..
            },
            source: IExpressionPE::ConstantInt(ConstantIntPE { value: 3, .. }),
            ..
          }),
          IExpressionPE::Lookup(LookupPE {
            name: IImpreciseNameP::LookupName(NameP(_, StrI("myList"))),
            template_args: None,
          }),
          ..
        ],
        ..
      }),
      body: BlockPE {
        inner: IExpressionPE::Void(_),
        ..
      },
      ..
    }) => {}
    _ => panic!("expected foreach i in myList = 3; myList {{ }} structure"),
  }
}

#[test]
fn multiple_statements_2() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  compile_block_contents_expect(
    &parse_arena,
    &keywords,
    "
      42;
      43;
      ",
  );
}

#[test]
fn if_and_another_statement() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  compile_block_contents_expect(
    &parse_arena,
    &keywords,
    "
      newCapacity = if (true) { 1 } else { 2 };
      newArray = 3;
      ",
  );
}

#[test]
fn test_blocks_trailing_void_presence() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_block_contents_expect(&parse_arena, &keywords, "moo()");
  match &expr {
    IExpressionPE::FunctionCall(FunctionCallPE {
      callable_expr: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("moo"))),
        template_args: None,
      }),
      arg_exprs: [],
      ..
    }) => {}
    _ => panic!("expected moo() structure"),
  }

  let expr = compile_block_contents_expect(&parse_arena, &keywords, "moo();");
  match &expr {
    IExpressionPE::Consecutor(ConsecutorPE {
      inners: [
        IExpressionPE::FunctionCall(FunctionCallPE {
          callable_expr: IExpressionPE::Lookup(LookupPE {
            name: IImpreciseNameP::LookupName(NameP(_, StrI("moo"))),
            template_args: None,
          }),
          arg_exprs: [],
          ..
        }),
        IExpressionPE::Void(_),
        ..
      ],
      ..
    }) => {}
    _ => panic!("expected moo(); structure"),
  }
}

#[test]
fn block_with_statement_and_result() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_block_contents_expect(
    &parse_arena,
    &keywords,
    "
      b;
      a
    ",
  );
  match &expr {
    IExpressionPE::Consecutor(ConsecutorPE {
      inners: [
        IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("b"))),
          template_args: None,
        }),
        IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("a"))),
          template_args: None,
        }),
        ..
      ],
      ..
    }) => {}
    _ => panic!("expected b; a structure"),
  }
}

#[test]
fn block_with_result() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_statement_expect(&parse_arena, &keywords, "3");
  match &expr {
    IExpressionPE::ConstantInt(ConstantIntPE { value: 3, .. }) => {}
    _ => panic!("expected 3"),
  }
}

#[test]
fn block_with_result_that_could_be_an_expr() {
  // = doThings(a); could be misinterpreted as an expression doThings(=, a) if we're
  // not careful.
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_block_contents_expect(
    &parse_arena,
    &keywords,
    "
      a = 2;
      doThings(a)
    ",
  );
  match &expr {
    IExpressionPE::Consecutor(ConsecutorPE {
      inners: [
        IExpressionPE::Let(LetPE {
          pattern: PatternPP {
            destination: Some(DestinationLocalP {
              decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("a"))),
              ..
            }),
            templex: None,
            destructure: None,
            ..
          },
          source: IExpressionPE::ConstantInt(ConstantIntPE { value: 2, .. }),
          ..
        }),
        IExpressionPE::FunctionCall(FunctionCallPE {
          callable_expr: IExpressionPE::Lookup(LookupPE {
            name: IImpreciseNameP::LookupName(NameP(_, StrI("doThings"))),
            template_args: None,
          }),
          arg_exprs: [IExpressionPE::Lookup(LookupPE {
            name: IImpreciseNameP::LookupName(NameP(_, StrI("a"))),
            template_args: None,
          }), ..],
          ..
        }),
        ..
      ],
      ..
    }) =>
    {}
    _ => panic!("expected a = 2; doThings(a) structure"),
  }
}

#[test]
fn mutating_as_statement() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_block_contents_expect(&parse_arena, &keywords, "set x = 6;");
  match &expr {
    IExpressionPE::Consecutor(ConsecutorPE {
      inners: [
        IExpressionPE::Mutate(MutatePE {
          mutatee: IExpressionPE::Lookup(LookupPE {
            name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
            template_args: None,
          }),
          source: IExpressionPE::ConstantInt(ConstantIntPE { value: 6, .. }),
          ..
        }),
        IExpressionPE::Void(_),
        ..
      ],
      ..
    }) => {}
    _ => panic!("expected set x = 6; structure"),
  }
}

#[test]
fn lone_block() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_block_contents_expect(
    &parse_arena,
    &keywords,
    "
      block {
        a
      }
    ",
  );
  match &expr {
    IExpressionPE::Block(BlockPE {
      inner: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("a"))),
        template_args: None,
      }),
      ..
    }) => {}
    _ => panic!("expected block {{ a }} structure"),
  }
}

#[test]
fn pure_block() {
  // Just make sure it parses, so that we can highlight it.
  // The pure block feature doesn't actually exist yet.
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  compile_block_contents_expect(
    &parse_arena,
    &keywords,
    "
      pure block {
        a
      }
    ",
  );
}

#[test]
fn unsafe_pure_block() {
  // Just make sure it parses, so that we can highlight it.
  // The unsafe pure block feature doesn't actually exist yet.
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  compile_block_contents_expect(
    &parse_arena,
    &keywords,
    "
      unsafe pure block {
        a
      }
    ",
  );
}

#[test]
fn report_leaving_out_semicolon_or_ending_body_after_expression_for_square() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let err = compile_statement(
    &parse_arena,
    &keywords,
    "
      block {
        floop() ]
      }
      ",
  )
  .unwrap_err();
  assert!(matches!(err, ParseError::BadStartOfStatementError(_)));
}

#[test]
fn empty_block() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_block_contents_expect(
    &parse_arena,
    &keywords,
    "
      block {
      }
      return 3;
      ",
  );
  match &expr {
    IExpressionPE::Consecutor(ConsecutorPE {
      inners: [
        IExpressionPE::Block(BlockPE {
          inner: IExpressionPE::Void(_),
          ..
        }),
        IExpressionPE::Return(ReturnPE {
          expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 3, .. }),
          ..
        }),
        IExpressionPE::Void(_),
        ..
      ],
      ..
    }) => {}
    _ => panic!("expected block {{ }} return 3; structure"),
  }
}

#[test]
fn cant_use_set_as_a_local_name() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let err = compile_statement(&parse_arena, &keywords, "[set] = (6,)").unwrap_err();
  assert!(matches!(
    err,
    ParseError::CantUseThatLocalName { ref name, .. } if name == "set"
  ));
}

#[test]
fn foreach_2() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_block_contents_expect(
    &parse_arena,
    &keywords,
    "
      foreach i in a {
        i
      }
      ",
  );
  match &expr {
    IExpressionPE::Each(EachPE {
      maybe_pure: None,
      entry_pattern: PatternPP {
        destination: Some(DestinationLocalP {
          decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("i"))),
          ..
        }),
        templex: None,
        destructure: None,
        ..
      },
      iterable_expr: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("a"))),
        template_args: None,
      }),
      body: BlockPE {
        inner: IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("i"))),
          template_args: None,
        }),
        ..
      },
      ..
    }) => {}
    _ => panic!("expected foreach i in a {{ i }} structure"),
  }
}

#[test]
fn foreach_expr() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_block_contents_expect(
    &parse_arena,
    &keywords,
    "
      a = foreach i in c { i };
      ",
  );
  match &expr {
    IExpressionPE::Consecutor(ConsecutorPE {
      inners: [
        IExpressionPE::Let(LetPE {
          pattern: PatternPP {
            destination: Some(DestinationLocalP {
              decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("a"))),
              ..
            }),
            templex: None,
            destructure: None,
            ..
          },
          source: IExpressionPE::Each(_),
          ..
        }),
        IExpressionPE::Void(_),
        ..
      ],
      ..
    }) => {}
    _ => panic!("expected a = foreach i in c {{ i }}; structure"),
  }
}
