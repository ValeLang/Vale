// cargo test --manifest-path FrontendRust/Cargo.toml --lib parsing::tests::expression_tests

use crate::interner::StrI;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::lexing::errors::ParseError;
use crate::parsing::ast::*;
use crate::parsing::tests::utils::*;
use bumpalo::Bump;

#[test]
fn simple_int() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "4");
  assert!(matches!(expr, IExpressionPE::ConstantInt(ConstantIntPE { value: 4, .. })));
}

#[test]
fn simple_bool() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "true");
  assert!(matches!(expr, IExpressionPE::ConstantBool(ConstantBoolPE { value: true, .. })));
}

#[test]
fn i64() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "4i64");
  assert!(matches!(
    expr,
    IExpressionPE::ConstantInt(ConstantIntPE { value: 4, bits: Some(64), .. })
  ));
}

#[test]
fn binary_operator() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "4 + 5");
  match &expr {
    IExpressionPE::BinaryCall(BinaryCallPE {
      function_name: NameP(_, StrI("+")),
      left_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 4, .. }),
      right_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 5, .. }),
      ..
    }) => {}
    _ => panic!("expected 4 + 5 structure"),
  }
}

#[test]
fn floats() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "4.2");
  assert!(matches!(expr, IExpressionPE::ConstantFloat(ConstantFloatPE { value: 4.2, .. })));
}

#[test]
fn number_range() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "0..5");
  match &expr {
    IExpressionPE::Range(RangePE {
      from_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 0, .. }),
      to_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 5, .. }),
      ..
    }) => {}
    _ => panic!("expected 0..5 structure"),
  }
}

#[test]
fn add_as_call() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "+(4, 5)");
  match &expr {
    IExpressionPE::FunctionCall(FunctionCallPE {
      callable_expr:
        IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("+"))),
          template_args: None,
        }),
      arg_exprs,
      ..
    }) if matches!(
      arg_exprs,
      [
        IExpressionPE::ConstantInt(ConstantIntPE { value: 4, .. }),
        IExpressionPE::ConstantInt(ConstantIntPE { value: 5, .. }),
        ..
      ]
    ) => {}
    _ => panic!("expected +(4, 5) structure"),
  }
}

#[test]
fn passing_eq_overload_set() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "moo(4, ==)");
  match &expr {
    IExpressionPE::FunctionCall(FunctionCallPE {
      callable_expr:
        IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("moo"))),
          ..
        }),
      arg_exprs:
        [IExpressionPE::ConstantInt(ConstantIntPE { value: 4, .. }), IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("=="))),
          template_args: None,
        }), ..],
      ..
    }) => {}
    _ => panic!("expected moo(4, ==) structure"),
  }
}

#[test]
fn call_then_binary_operator() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "str(i) + 5");
  match &expr {
    IExpressionPE::BinaryCall(BinaryCallPE {
      function_name: NameP(_, StrI("+")),
      left_expr:
        IExpressionPE::FunctionCall(FunctionCallPE {
          callable_expr:
            IExpressionPE::Lookup(LookupPE {
              name: IImpreciseNameP::LookupName(NameP(_, StrI("str"))),
              template_args: None,
            }),
          arg_exprs,
          ..
        }),
      right_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 5, .. }),
      ..
    }) => match arg_exprs {
      [IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("i"))),
        template_args: None,
      })] => {}
      _ => panic!("expected str(i) + 5 structure"),
    },
    _ => panic!("expected str(i) + 5 structure"),
  }
}

#[test]
fn range() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "a..b");
  match &expr {
    IExpressionPE::Range(RangePE {
      from_expr:
        IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("a"))),
          template_args: None,
        }),
      to_expr:
        IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("b"))),
          template_args: None,
        }),
      ..
    }) => {}
    _ => panic!("expected a..b structure"),
  }
}

#[test]
fn regular_call() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "x(y)");
  match &expr {
    IExpressionPE::FunctionCall(FunctionCallPE {
      callable_expr:
        IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
          template_args: None,
        }),
      arg_exprs,
      ..
    }) => match arg_exprs {
      [IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("y"))),
        template_args: None,
      })] => {}
      _ => panic!("expected x(y) structure"),
    },
    _ => panic!("expected x(y) structure"),
  }
}

#[test]
fn not() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "not y");
  match &expr {
    IExpressionPE::Not(NotPE {
      inner:
        IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("y"))),
          template_args: None,
        }),
      ..
    }) => {}
    _ => panic!("expected not y structure"),
  }
}

#[test]
fn borrowing_result_of_function_call() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "&Muta()");
  match &expr {
    IExpressionPE::Augment(AugmentPE {
      target_ownership: OwnershipP::Borrow,
      inner:
        IExpressionPE::FunctionCall(FunctionCallPE {
          callable_expr:
            IExpressionPE::Lookup(LookupPE {
              name: IImpreciseNameP::LookupName(NameP(_, StrI("Muta"))),
              template_args: None,
            }),
          arg_exprs,
          ..
        }),
      ..
    }) if arg_exprs.is_empty() => {}
    _ => panic!("expected &Muta() structure"),
  }
}

#[test]
fn specifying_heap() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "^Muta()");
  match &expr {
    IExpressionPE::Augment(AugmentPE {
      target_ownership: OwnershipP::Own,
      inner: IExpressionPE::FunctionCall(FunctionCallPE { .. }),
      ..
    }) => {}
    _ => panic!("expected ^Muta() structure"),
  }
}

#[test]
fn inline_call_ignored() {
  // The inl keyword is just parsed as an Own augment. It's effectively a no-op.
  // This is probably to better syntax-highlight the inl keyword even though we ignore it.
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "inl Muta()");
  match &expr {
    IExpressionPE::Augment(AugmentPE {
      target_ownership: OwnershipP::Own,
      inner:
        IExpressionPE::FunctionCall(FunctionCallPE {
          callable_expr:
            IExpressionPE::Lookup(LookupPE {
              name: IImpreciseNameP::LookupName(NameP(_, StrI("Muta"))),
              template_args: None,
            }),
          arg_exprs,
          ..
        }),
      ..
    }) if arg_exprs.is_empty() => {}
    _ => panic!("expected inl Muta() structure"),
  }
}

#[test]
fn method_call() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "x . shout ()");
  match &expr {
    IExpressionPE::MethodCall(MethodCallPE {
      subject_expr:
        IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
          template_args: None,
        }),
      method_lookup:
        LookupPE { name: IImpreciseNameP::LookupName(NameP(_, StrI("shout"))), template_args: None },
      arg_exprs,
      ..
    }) if arg_exprs.is_empty() => {}
    _ => panic!("expected x . shout () structure"),
  }
}

#[test]
fn mapping_method_call() {
  // These arent implemented yet, we currently just parse these as method calls to support
  // snippets on the site.
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "x *. shout ()");
  match &expr {
    IExpressionPE::MethodCall(MethodCallPE {
      subject_expr:
        IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
          template_args: None,
        }),
      method_lookup:
        LookupPE { name: IImpreciseNameP::LookupName(NameP(_, StrI("shout"))), template_args: None },
      arg_exprs,
      ..
    }) if arg_exprs.is_empty() => {}
    _ => panic!("expected x *. shout () structure"),
  }
}

#[test]
fn method_on_member() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "x.moo.shout()");
  match &expr {
    IExpressionPE::MethodCall(MethodCallPE {
      subject_expr:
        IExpressionPE::Dot(DotPE {
          left:
            IExpressionPE::Lookup(LookupPE {
              name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
              template_args: None,
            }),
          member: NameP(_, StrI("moo")),
          ..
        }),
      method_lookup:
        LookupPE { name: IImpreciseNameP::LookupName(NameP(_, StrI("shout"))), template_args: None },
      arg_exprs,
      ..
    }) if arg_exprs.is_empty() => {}
    _ => panic!("expected x.moo.shout() structure"),
  }
}

#[test]
fn moving_method_call() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "(x ).shout()");
  match &expr {
    IExpressionPE::MethodCall(MethodCallPE {
      subject_expr:
        IExpressionPE::SubExpression(SubExpressionPE {
          inner:
            IExpressionPE::Lookup(LookupPE {
              name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
              template_args: None,
            }),
          ..
        }),
      method_lookup:
        LookupPE { name: IImpreciseNameP::LookupName(NameP(_, StrI("shout"))), template_args: None },
      arg_exprs,
      ..
    }) if arg_exprs.is_empty() => {}
    _ => panic!("expected (x ).shout() structure"),
  }
}

#[test]
fn templated_function_call() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr =
    compile_expression_expect(&parse_arena, &keywords, "toArray<imm>( &result)");
  match &expr {
    IExpressionPE::FunctionCall(FunctionCallPE {
      callable_expr:
        IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("toArray"))),
          template_args: Some(TemplateArgsP { args, .. }),
        }),
      arg_exprs,
      ..
    }) => match (args, arg_exprs) {
      (
        [ITemplexPT::Mutability(MutabilityPT(_, MutabilityP::Immutable)), ..],
        [IExpressionPE::Augment(AugmentPE {
          target_ownership: OwnershipP::Borrow,
          inner:
            IExpressionPE::Lookup(LookupPE {
              name: IImpreciseNameP::LookupName(NameP(_, StrI("result"))),
              template_args: None,
            }),
          ..
        })],
      ) => {}
      _ => panic!("expected toArray<imm>( &result) structure"),
    },
    _ => panic!("expected toArray<imm>( &result) structure"),
  }
}

#[test]
fn templated_method_call() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr =
    compile_expression_expect(&parse_arena, &keywords, "result.toArray <imm> ()");
  match &expr {
    IExpressionPE::MethodCall(MethodCallPE {
      subject_expr:
        IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("result"))),
          template_args: None,
        }),
      method_lookup:
        LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("toArray"))),
          template_args: Some(TemplateArgsP { args, .. }),
        },
      arg_exprs,
      ..
    }) if arg_exprs.is_empty() => match args {
      [ITemplexPT::Mutability(MutabilityPT(_, MutabilityP::Immutable)), ..] => {}
      _ => panic!("expected result.toArray <imm> () structure"),
    },
    _ => panic!("expected result.toArray <imm> () structure"),
  }
}

#[test]
fn custom_binaries() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "not y florgle not x");
  match &expr {
    IExpressionPE::BinaryCall(BinaryCallPE {
      function_name: NameP(_, StrI("florgle")),
      left_expr:
        IExpressionPE::Not(NotPE {
          inner:
            IExpressionPE::Lookup(LookupPE {
              name: IImpreciseNameP::LookupName(NameP(_, StrI("y"))),
              template_args: None,
            }),
          ..
        }),
      right_expr:
        IExpressionPE::Not(NotPE {
          inner:
            IExpressionPE::Lookup(LookupPE {
              name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
              template_args: None,
            }),
          ..
        }),
      ..
    }) => {}
    _ => panic!("expected not y florgle not x structure"),
  }
}

#[test]
fn custom_with_noncustom_binaries() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "a + b florgle x * y");

  match &expr {
    IExpressionPE::BinaryCall(BinaryCallPE {
      function_name: NameP(_, StrI("florgle")),
      left_expr:
        IExpressionPE::BinaryCall(BinaryCallPE {
          function_name: NameP(_, StrI("+")),
          left_expr:
            IExpressionPE::Lookup(LookupPE {
              name: IImpreciseNameP::LookupName(NameP(_, StrI("a"))),
              template_args: None,
            }),
          right_expr:
            IExpressionPE::Lookup(LookupPE {
              name: IImpreciseNameP::LookupName(NameP(_, StrI("b"))),
              template_args: None,
            }),
          ..
        }),
      right_expr:
        IExpressionPE::BinaryCall(BinaryCallPE {
          function_name: NameP(_, StrI("*")),
          left_expr:
            IExpressionPE::Lookup(LookupPE {
              name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
              template_args: None,
            }),
          right_expr:
            IExpressionPE::Lookup(LookupPE {
              name: IImpreciseNameP::LookupName(NameP(_, StrI("y"))),
              template_args: None,
            }),
          ..
        }),
      ..
    }) => {}
    _ => panic!("expected a + b florgle x * y structure"),
  }
}

#[test]
fn template_calling() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  {
    let expr = compile_expression_expect(&parse_arena, &keywords, "MyNone< int >()");
    match &expr {
      IExpressionPE::FunctionCall(FunctionCallPE {
        callable_expr: IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("MyNone"))),
          template_args: Some(TemplateArgsP {
            args: [ITemplexPT::NameOrRune(NameOrRunePT(NameP(_, StrI("int")))), ..],
            ..
          }),
        }),
        arg_exprs: [],
        ..
      }) => {}
      _ => panic!("expected MyNone<int>() structure"),
    }
  }

  {
    let expr =
      compile_expression_expect(&parse_arena, &keywords, "MySome< MyNone <int> >()");
    match &expr {
      IExpressionPE::FunctionCall(FunctionCallPE {
        callable_expr: IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("MySome"))),
          template_args: Some(TemplateArgsP {
            args: [ITemplexPT::Call(CallPT {
              template: ITemplexPT::NameOrRune(NameOrRunePT(NameP(_, StrI("MyNone")))),
              args: [ITemplexPT::NameOrRune(NameOrRunePT(NameP(_, StrI("int")))), ..],
              ..
            }), ..],
            ..
          }),
        }),
        arg_exprs: [],
        ..
      }) =>
      {}
      _ => panic!("expected MySome<MyNone<int>>() structure"),
    }
  }
}

#[test]
fn greater_than_or_equal() {
  // It turns out, this was only parsing "9 >=" because it was looking for > specifically (in fact, it was looking
  // for + - * / < >) so it parsed as >(9, =) which was bad. We changed the infix operator parser to expect the
  // whitespace on both sides, so that it was forced to parse the entire thing.
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "9 >= 3");
  match &expr {
    IExpressionPE::BinaryCall(BinaryCallPE {
      function_name: NameP(_, StrI(">=")),
      left_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 9, .. }),
      right_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 3, .. }),
      ..
    }) => {}
    _ => panic!("expected 9 >= 3 structure"),
  }
}

#[test]
fn indexing() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "arr [4]");
  match &expr {
    IExpressionPE::BraceCall(BraceCallPE {
      subject_expr: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("arr"))),
        template_args: None,
      }),
      arg_exprs: [IExpressionPE::ConstantInt(ConstantIntPE { value: 4, .. }), ..],
      ..
    }) => {}
    _ => panic!("expected arr [4] structure"),
  }
}

#[test]
fn single_arg_brace_lambda() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "x => { x }");
  match &expr {
    IExpressionPE::Lambda(LambdaPE {
      function: FunctionP {
        header: FunctionHeaderP {
          params: Some(ParamsP {
            params: [ParameterP {
              pattern: Some(PatternPP {
                destination: Some(DestinationLocalP {
                  decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("x"))),
                  ..
                }),
                ..
              }),
              ..
            }, ..],
            ..
          }),
          ..
        },
        body: Some(BlockPE {
          inner: IExpressionPE::Lookup(LookupPE {
            name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
            template_args: None,
          }),
          ..
        }),
        ..
      },
      ..
    }) => {}
    _ => panic!("expected x => {{ x }} structure"),
  }
}

#[test]
fn single_arg_no_brace_lambda() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "x => x");
  match &expr {
    IExpressionPE::Lambda(LambdaPE {
      function: FunctionP {
        header: FunctionHeaderP {
          params: Some(ParamsP {
            params: [ParameterP {
              pattern: Some(PatternPP {
                destination: Some(DestinationLocalP {
                  decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("x"))),
                  ..
                }),
                ..
              }),
              ..
            }, ..],
            ..
          }),
          ..
        },
        body: Some(BlockPE {
          inner: IExpressionPE::Lookup(LookupPE {
            name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
            template_args: None,
          }),
          ..
        }),
        ..
      },
      ..
    }) => {}
    _ => panic!("expected x => x structure"),
  }
}

#[test]
fn single_arg_typed_brace_lambda() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "(x int) => { x }");
  match &expr {
    IExpressionPE::Lambda(LambdaPE {
      function: FunctionP {
        header: FunctionHeaderP {
          params: Some(ParamsP {
            params: [ParameterP {
              pattern: Some(PatternPP {
                destination: Some(DestinationLocalP {
                  decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("x"))),
                  ..
                }),
                templex: Some(ITemplexPT::NameOrRune(NameOrRunePT(NameP(_, StrI("int"))))),
                ..
              }),
              ..
            }, ..],
            ..
          }),
          ..
        },
        body: Some(BlockPE {
          inner: IExpressionPE::Lookup(LookupPE {
            name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
            template_args: None,
          }),
          ..
        }),
        ..
      },
      ..
    }) => {}
    _ => panic!("expected (x int) => {{ x }} structure"),
  }
}

#[test]
fn argless_lambda() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "{_}");
  match &expr {
    IExpressionPE::Lambda(LambdaPE {
      function: FunctionP {
        header: FunctionHeaderP { params: None, ret: FunctionReturnP { ret_type: None, .. }, .. },
        body: Some(BlockPE { inner: IExpressionPE::MagicParamLookup(_), .. }),
        ..
      },
      ..
    }) => {}
    _ => panic!("expected {{_}} structure"),
  }
}

#[test]
fn multi_arg_typed_brace_lambda() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "(x, y) => x");
  match &expr {
    IExpressionPE::Lambda(LambdaPE {
      function: FunctionP {
        header: FunctionHeaderP {
          params: Some(ParamsP {
            params: [
              ParameterP {
                pattern: Some(PatternPP {
                  destination: Some(DestinationLocalP {
                    decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("x"))),
                    ..
                  }),
                  ..
                }),
                ..
              },
              ParameterP {
                pattern: Some(PatternPP {
                  destination: Some(DestinationLocalP {
                    decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("y"))),
                    ..
                  }),
                  ..
                }),
                ..
              },
            ],
            ..
          }),
          ..
        },
        body: Some(BlockPE {
          inner: IExpressionPE::Lookup(LookupPE {
            name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
            template_args: None,
          }),
          ..
        }),
        ..
      },
      ..
    }) => {}
    _ => panic!("expected (x, y) => x structure"),
  }
}

#[test]
fn destructuring_lambda() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "([x, y]) => x");
  match &expr {
    IExpressionPE::Lambda(LambdaPE {
      function: FunctionP {
        header: FunctionHeaderP {
          params: Some(ParamsP {
            params: [ParameterP {
              pattern: Some(PatternPP {
                destination: None,
                templex: None,
                destructure: Some(DestructureP {
                  patterns: [
                    PatternPP {
                      destination: Some(DestinationLocalP {
                        decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("x"))),
                        ..
                      }),
                      ..
                    },
                    PatternPP {
                      destination: Some(DestinationLocalP {
                        decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("y"))),
                        ..
                      }),
                      ..
                    },
                  ],
                  ..
                }),
                ..
              }),
              ..
            }, ..],
            ..
          }),
          ..
        },
        body: Some(BlockPE {
          inner: IExpressionPE::Lookup(LookupPE {
            name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
            template_args: None,
          }),
          ..
        }),
        ..
      },
      ..
    }) => {}
    _ => panic!("expected ([x, y]) => x structure"),
  }
}

#[test]
fn dot_symbol() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, r#"myPath./("subdir")"#);
  match &expr {
    IExpressionPE::MethodCall(MethodCallPE {
      subject_expr: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("myPath"))),
        template_args: None,
      }),
      method_lookup: LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("/"))),
        template_args: None,
      },
      arg_exprs: [IExpressionPE::ConstantStr(ConstantStrPE { value: StrI("subdir"), .. }), ..],
      ..
    }) => {}
    _ => panic!("expected myPath./(\"subdir\") structure"),
  }
}

#[test]
fn not_equal() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "3 != 4");
  match &expr {
    IExpressionPE::BinaryCall(BinaryCallPE {
      function_name: NameP(_, StrI("!=")),
      left_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 3, .. }),
      right_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 4, .. }),
      ..
    }) => {}
    _ => panic!("expected 3 != 4 structure"),
  }
}

#[test]
fn set_call_isnt_interpreted_as_a_set_expression() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "set(true)");
  match &expr {
    IExpressionPE::FunctionCall(FunctionCallPE {
      callable_expr: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("set"))),
        template_args: None,
      }),
      arg_exprs: [IExpressionPE::ConstantBool(ConstantBoolPE { value: true, .. }), ..],
      ..
    }) => {}
    _ => panic!("expected set(true) structure"),
  }
}

#[test]
fn two_d_array_access() {
  // We had a bug where the lexer was interpreting that 2.1 as a float.
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "arr.2.1");
  match &expr {
    IExpressionPE::Dot(DotPE {
      left: IExpressionPE::Dot(DotPE {
        left: IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("arr"))),
          template_args: None,
        }),
        member: NameP(_, StrI("2")),
        ..
      }),
      member: NameP(_, StrI("1")),
      ..
    }) => {}
    _ => panic!("expected arr.2.1 structure"),
  }
}

#[test]
fn lambda_without_surrounding_parens() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "{ 0 }()");
  match &expr {
    IExpressionPE::FunctionCall(FunctionCallPE {
      callable_expr: IExpressionPE::Lambda(_),
      arg_exprs: [],
      ..
    }) => {}
    _ => panic!("expected {{ 0 }}() structure"),
  }
}

#[test]
fn function_call() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "call(sum)");
  match &expr {
    IExpressionPE::FunctionCall(FunctionCallPE {
      callable_expr: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("call"))),
        template_args: None,
      }),
      arg_exprs: [IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("sum"))),
        template_args: None,
      }), ..],
      ..
    }) => {}
    _ => panic!("expected call(sum) structure"),
  }
}

#[test]
fn test_inner_expression_unlet() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "destroy(unlet enemy)");
  match &expr {
    IExpressionPE::FunctionCall(FunctionCallPE {
      callable_expr: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("destroy"))),
        template_args: None,
      }),
      arg_exprs: [IExpressionPE::Unlet(UnletPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("enemy"))),
        ..
      }), ..],
      ..
    }) => {}
    _ => panic!("expected destroy(unlet enemy) structure"),
  }
}

#[test]
fn detect_break_in_expr() {
  // See BRCOBS
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let err = compile_expression_for_error(&parse_arena, &keywords, "a(b, break)");
  assert!(matches!(err, ParseError::CantUseBreakInExpression(_)));
}

#[test]
fn detect_return_in_expr() {
  // See BRCOBS
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let err = compile_expression_for_error(&parse_arena, &keywords, "a(b, return)");
  assert!(matches!(err, ParseError::CantUseReturnInExpression(_)));
}

#[test]
fn parens() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "2 * (5 - 7)");
  match &expr {
    IExpressionPE::BinaryCall(BinaryCallPE {
      function_name: NameP(_, StrI("*")),
      left_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 2, .. }),
      right_expr: IExpressionPE::SubExpression(SubExpressionPE {
        inner: IExpressionPE::BinaryCall(BinaryCallPE {
          function_name: NameP(_, StrI("-")),
          left_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 5, .. }),
          right_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 7, .. }),
          ..
        }),
        ..
      }),
      ..
    }) => {}
    _ => panic!("expected 2 * (5 - 7) structure"),
  }
}

#[test]
fn precedence_1() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "(5 - 7) * 2");
  match &expr {
    IExpressionPE::BinaryCall(BinaryCallPE {
      function_name: NameP(_, StrI("*")),
      left_expr: IExpressionPE::SubExpression(SubExpressionPE {
        inner: IExpressionPE::BinaryCall(BinaryCallPE {
          function_name: NameP(_, StrI("-")),
          left_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 5, .. }),
          right_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 7, .. }),
          ..
        }),
        ..
      }),
      right_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 2, .. }),
      ..
    }) => {}
    _ => panic!("expected (5 - 7) * 2 structure"),
  }
}

#[test]
fn precedence_2() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "5 - 7 * 2");
  match &expr {
    IExpressionPE::BinaryCall(BinaryCallPE {
      function_name: NameP(_, StrI("-")),
      left_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 5, .. }),
      right_expr: IExpressionPE::BinaryCall(BinaryCallPE {
        function_name: NameP(_, StrI("*")),
        left_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 7, .. }),
        right_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 2, .. }),
        ..
      }),
      ..
    }) => {}
    _ => panic!("expected 5 - 7 * 2 structure"),
  }
}

#[test]
fn static_array_from_values() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "[#](3, 5, 6)");
  match &expr {
    IExpressionPE::ConstructArray(ConstructArrayPE {
      type_pt: None,
      mutability_pt: Some(ITemplexPT::Mutability(MutabilityPT(_, MutabilityP::Mutable))),
      variability_pt: None,
      size: IArraySizeP::StaticSized(StaticSizedArraySizeP { size_pt: None }),
      initializing_individual_elements: true,
      args: [_, _, _, ..],
      ..
    }) => {}
    _ => panic!("expected [#](3, 5, 6) structure"),
  }
}

#[test]
fn static_array_from_values_with_newlines() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "[#](\n3\n)");
  match &expr {
    IExpressionPE::ConstructArray(ConstructArrayPE { .. }) => {}
    _ => panic!("expected [#](\\n3\\n) structure"),
  }
}

#[test]
fn static_array_from_callable_with_rune() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "[#N]({_ * 2})");
  match &expr {
    IExpressionPE::ConstructArray(ConstructArrayPE {
      type_pt: None,
      mutability_pt: Some(ITemplexPT::Mutability(MutabilityPT(_, MutabilityP::Mutable))),
      variability_pt: None,
      size: IArraySizeP::StaticSized(StaticSizedArraySizeP {
        size_pt: Some(ITemplexPT::NameOrRune(NameOrRunePT(NameP(_, StrI("N"))))),
      }),
      initializing_individual_elements: false,
      args: [IExpressionPE::Lambda(_), ..],
      ..
    }) => {}
    _ => panic!("expected [#N]({{_ * 2}}) structure"),
  }
}

#[test]
fn less_than_or_equal() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "a <= b");
  match &expr {
    IExpressionPE::BinaryCall(BinaryCallPE {
      function_name: NameP(_, StrI("<=")),
      left_expr: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("a"))),
        template_args: None,
      }),
      right_expr: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("b"))),
        template_args: None,
      }),
      ..
    }) => {}
    _ => panic!("expected a <= b structure"),
  }
}

#[test]
fn static_array_from_callable() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "[#3](triple)");
  match &expr {
    IExpressionPE::ConstructArray(ConstructArrayPE {
      type_pt: None,
      mutability_pt: Some(ITemplexPT::Mutability(MutabilityPT(_, MutabilityP::Mutable))),
      variability_pt: None,
      size: IArraySizeP::StaticSized(StaticSizedArraySizeP {
        size_pt: Some(ITemplexPT::Int(IntPT { value: 3, .. })),
      }),
      initializing_individual_elements: false,
      args: [_, ..],
      ..
    }) => {}
    _ => panic!("expected [#3](triple) structure"),
  }
}

#[test]
fn immutable_static_array_from_callable() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "#[#3](triple)");
  match &expr {
    IExpressionPE::ConstructArray(ConstructArrayPE {
      type_pt: None,
      mutability_pt: Some(ITemplexPT::Mutability(MutabilityPT(_, MutabilityP::Immutable))),
      variability_pt: None,
      size: IArraySizeP::StaticSized(StaticSizedArraySizeP {
        size_pt: Some(ITemplexPT::Int(IntPT { value: 3, .. })),
      }),
      initializing_individual_elements: false,
      args: [_, ..],
      ..
    }) => {}
    _ => panic!("expected #[#3](triple) structure"),
  }
}

#[test]
fn immutable_static_array_from_callable_no_size() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "#[#](3, 4, 5)");
  match &expr {
    IExpressionPE::ConstructArray(ConstructArrayPE {
      type_pt: None,
      mutability_pt: Some(ITemplexPT::Mutability(MutabilityPT(_, MutabilityP::Immutable))),
      variability_pt: None,
      size: IArraySizeP::StaticSized(StaticSizedArraySizeP { size_pt: None }),
      initializing_individual_elements: true,
      args: [_, _, _, ..],
      ..
    }) => {}
    _ => panic!("expected #[#](3, 4, 5) structure"),
  }
}

#[test]
fn runtime_array_from_callable_with_rune() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "[](6, {_ * 2})");
  match &expr {
    IExpressionPE::ConstructArray(ConstructArrayPE {
      type_pt: None,
      mutability_pt: Some(ITemplexPT::Mutability(MutabilityPT(_, MutabilityP::Mutable))),
      variability_pt: None,
      size: IArraySizeP::RuntimeSized,
      initializing_individual_elements: false,
      args: [_, _, ..],
      ..
    }) => {}
    _ => panic!("expected [](6, {{_ * 2}}) structure"),
  }
}

#[test]
fn runtime_array_from_callable() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "[](6, triple)");
  match &expr {
    IExpressionPE::ConstructArray(ConstructArrayPE {
      type_pt: None,
      mutability_pt: Some(ITemplexPT::Mutability(MutabilityPT(_, MutabilityP::Mutable))),
      variability_pt: None,
      size: IArraySizeP::RuntimeSized,
      initializing_individual_elements: false,
      args: [_, _, ..],
      ..
    }) => {}
    _ => panic!("expected [](6, triple) structure"),
  }
}

#[test]
fn double_rsa_with_type() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "[][]bool(42)");
  match &expr {
    IExpressionPE::ConstructArray(ConstructArrayPE {
      type_pt: Some(ITemplexPT::RuntimeSizedArray(RuntimeSizedArrayPT {
        mutability: ITemplexPT::Mutability(MutabilityPT(_, MutabilityP::Mutable)),
        element: ITemplexPT::NameOrRune(NameOrRunePT(NameP(_, StrI("bool")))),
        ..
      })),
      mutability_pt: Some(ITemplexPT::Mutability(MutabilityPT(_, MutabilityP::Mutable))),
      variability_pt: None,
      size: IArraySizeP::RuntimeSized,
      initializing_individual_elements: false,
      args: [IExpressionPE::ConstantInt(ConstantIntPE { value: 42, .. }), ..],
      ..
    }) => {}
    _ => panic!("expected [][]bool(42) structure"),
  }
}

#[test]
fn immutable_runtime_array_from_callable() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "#[](6, triple)");
  match &expr {
    IExpressionPE::ConstructArray(ConstructArrayPE {
      type_pt: None,
      mutability_pt: Some(ITemplexPT::Mutability(MutabilityPT(_, MutabilityP::Immutable))),
      variability_pt: None,
      size: IArraySizeP::RuntimeSized,
      initializing_individual_elements: false,
      args: [_, _, ..],
      ..
    }) => {}
    _ => panic!("expected #[](6, triple) structure"),
  }
}

#[test]
fn one_element_tuple() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "(3,)");
  match &expr {
    IExpressionPE::Tuple(TuplePE {
      elements: [IExpressionPE::ConstantInt(ConstantIntPE { value: 3, .. }), ..],
      ..
    }) => {}
    _ => panic!("expected (3,) structure"),
  }
}

#[test]
fn zero_element_tuple() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "()");
  match &expr {
    IExpressionPE::Tuple(TuplePE { elements: [], .. }) => {}
    _ => panic!("expected () structure"),
  }
}

#[test]
fn two_element_tuple() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "(3,4)");
  match &expr {
    IExpressionPE::Tuple(TuplePE {
      elements: [
        IExpressionPE::ConstantInt(ConstantIntPE { value: 3, .. }),
        IExpressionPE::ConstantInt(ConstantIntPE { value: 4, .. }),
        ..
      ],
      ..
    }) => {}
    _ => panic!("expected (3,4) structure"),
  }
}

#[test]
fn three_element_tuple() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "(3,4,5)");
  match &expr {
    IExpressionPE::Tuple(TuplePE {
      elements: [
        IExpressionPE::ConstantInt(ConstantIntPE { value: 3, .. }),
        IExpressionPE::ConstantInt(ConstantIntPE { value: 4, .. }),
        IExpressionPE::ConstantInt(ConstantIntPE { value: 5, .. }),
        ..
      ],
      ..
    }) => {}
    _ => panic!("expected (3,4,5) structure"),
  }
}

#[test]
fn three_element_tuple_trailing_comma() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "(3,4,5,)");
  match &expr {
    IExpressionPE::Tuple(TuplePE {
      elements: [
        IExpressionPE::ConstantInt(ConstantIntPE { value: 3, .. }),
        IExpressionPE::ConstantInt(ConstantIntPE { value: 4, .. }),
        IExpressionPE::ConstantInt(ConstantIntPE { value: 5, .. }),
        ..
      ],
      ..
    }) => {}
    _ => panic!("expected (3,4,5,) structure"),
  }
}

#[test]
fn transmigrate() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "a'x");
  match &expr {
    IExpressionPE::Transmigrate(TransmigratePE {
      target_region: NameP(_, StrI("a")),
      inner: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("x"))),
        template_args: None,
      }),
      ..
    }) => {}
    _ => panic!("expected a'x structure"),
  }
}

#[test]
fn call_callable_expr() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr =
    compile_expression_expect(&parse_arena, &keywords, "(something.callable)(3)");
  match &expr {
    IExpressionPE::FunctionCall(FunctionCallPE {
      callable_expr: IExpressionPE::SubExpression(SubExpressionPE {
        inner: IExpressionPE::Dot(DotPE {
          left: IExpressionPE::Lookup(LookupPE {
            name: IImpreciseNameP::LookupName(NameP(_, StrI("something"))),
            template_args: None,
          }),
          member: NameP(_, StrI("callable")),
          ..
        }),
        ..
      }),
      arg_exprs: [_arg, ..],
      ..
    }) => {}
    _ => panic!("expected (something.callable)(3) structure"),
  }
}

#[test]
fn array_indexing() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "board[i]");
  match &expr {
    IExpressionPE::BraceCall(BraceCallPE {
      subject_expr: IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("board"))),
        template_args: None,
      }),
      arg_exprs: [IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("i"))),
        template_args: None,
      }), ..],
      callable_readwrite: false,
      ..
    }) => {}
    _ => panic!("expected board[i] structure"),
  }

  let expr2 = compile_expression_expect(&parse_arena, &keywords, "this.board[i]");
  match &expr2 {
    IExpressionPE::BraceCall(BraceCallPE {
      subject_expr: IExpressionPE::Dot(DotPE {
        left: IExpressionPE::Lookup(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(_, StrI("this"))),
          template_args: None,
        }),
        member: NameP(_, StrI("board")),
        ..
      }),
      arg_exprs: [IExpressionPE::Lookup(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(_, StrI("i"))),
        template_args: None,
      }), ..],
      callable_readwrite: false,
      ..
    }) => {}
    _ => panic!("expected this.board[i] structure"),
  }
}

#[test]
fn mod_and_equal_precedence() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "8 mod 2 == 0");
  match &expr {
    IExpressionPE::BinaryCall(BinaryCallPE {
      function_name: NameP(_, StrI("==")),
      left_expr: IExpressionPE::BinaryCall(BinaryCallPE {
        function_name: NameP(_, StrI("mod")),
        left_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 8, .. }),
        right_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 2, .. }),
        ..
      }),
      right_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 0, .. }),
      ..
    }) => {}
    _ => panic!("expected 8 mod 2 == 0 structure"),
  }
}

#[test]
fn or_and_equal_precedence() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "2 == 0 or false");
  match &expr {
    IExpressionPE::Or(OrPE {
      left: IExpressionPE::BinaryCall(BinaryCallPE {
        function_name: NameP(_, StrI("==")),
        left_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 2, .. }),
        right_expr: IExpressionPE::ConstantInt(ConstantIntPE { value: 0, .. }),
        ..
      }),
      right: BlockPE {
        inner: IExpressionPE::ConstantBool(ConstantBoolPE { value: false, .. }),
        ..
      },
      ..
    }) => {}
    _ => panic!("expected 2 == 0 or false structure"),
  }
}

#[test]
fn test_templated_lambda_param() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let expr = compile_expression_expect(&parse_arena, &keywords, "(a => a + a)(3)");
  match &expr {
    IExpressionPE::FunctionCall(FunctionCallPE {
      callable_expr: IExpressionPE::SubExpression(SubExpressionPE {
        inner: IExpressionPE::Lambda(LambdaPE {
          function: FunctionP {
            header: FunctionHeaderP {
              params: Some(ParamsP {
                params: [ParameterP {
                  pattern: Some(PatternPP {
                    destination: Some(DestinationLocalP {
                      decl: INameDeclarationP::LocalNameDeclaration(NameP(_, StrI("a"))),
                      ..
                    }),
                    templex: None,
                    destructure: None,
                    ..
                  }),
                  ..
                }, ..],
                ..
              }),
              ..
            },
            body: Some(BlockPE {
              inner: IExpressionPE::BinaryCall(BinaryCallPE {
                function_name: NameP(_, StrI("+")),
                left_expr: IExpressionPE::Lookup(LookupPE {
                  name: IImpreciseNameP::LookupName(NameP(_, StrI("a"))),
                  template_args: None,
                }),
                right_expr: IExpressionPE::Lookup(LookupPE {
                  name: IImpreciseNameP::LookupName(NameP(_, StrI("a"))),
                  template_args: None,
                }),
                ..
              }),
              ..
            }),
            ..
          },
          ..
        }),
        ..
      }),
      arg_exprs: [IExpressionPE::ConstantInt(ConstantIntPE { value: 3, .. }), ..],
      ..
    }) => {}
    _ => panic!("expected (a => a + a)(3) structure"),
  }
}

