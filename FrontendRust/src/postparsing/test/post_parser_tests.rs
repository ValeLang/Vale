// Run with: cargo test --manifest-path FrontendRust/Cargo.toml --lib postparsing::test::post_parser_tests

use bumpalo::Bump;
use crate::cast;
use crate::compile_options::GlobalOptions;
use crate::interner::StrI;
use crate::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::parsing::ast::{IMacroInclusionP, LoadAsP, VariabilityP};
use crate::postparsing::ast::{IStructMemberS, ProgramS};
use crate::postparsing::expressions::{
  ConstantIntSE, DotSE, FunctionCallSE, IExpressionSE, IVariableUseCertainty, LetSE, LoadPartSE, LocalLoadSE,
  LocalS, OutsideLoadSE, OverloadSetSE, OwnershippedSE, ReturnSE,
};
use crate::postparsing::patterns::patterns::{AtomSP, CaptureS};
use crate::postparsing::names::{CodeNameS, CodeRuneS, IFunctionDeclarationNameS, IImpreciseNameS, IRuneS, IRuneValS, IVarNameS};
use crate::postparsing::post_parser::{ICompileErrorS, PostParser};
use crate::postparsing::rules::rules::{ILiteralSL, LiteralSR, MaybeCoercingLookupSR};
use crate::postparsing::test::traverse::NodeRefS;
use crate::parsing::tests::utils::compile_file;
use crate::parsing::tests::utils::{expect_1, expect_2, expect_3};
use crate::postparsing::ast::IBodyS;
use crate::parsing::ast::MutabilityP;
use crate::postparsing::ast::IGenericParameterTypeS;
use crate::postparsing::expressions::ConstantBoolSE;
use crate::postparsing::ast::ParameterS;
use crate::postparsing::rules::RuneUsage;
use crate::postparsing::expressions::ConsecutorSE;
use crate::postparsing::post_parser::VariableNameAlreadyExists;
use crate::postparsing::post_parser::RuneExplicitTypeConflictS;
use crate::collect_only_snode;
use crate::collect_only_snodes;
use crate::collect_where_snode;
use crate::collect_where_snodes;

fn compile<'s, 'ctx, 'p>(
  scout_arena: &'ctx ScoutArena<'s>,
  keywords: &'ctx Keywords<'s>,
  parse_arena: &'ctx ParseArena<'p>,
  code: &str,
) -> ProgramS<'s>
where 'p: 's,
{
  let options = GlobalOptions {
    sanity_check: true,
    use_overload_index: true,
    use_optimized_solver: true,
    verbose_errors: false,
    debug_output: false,
  };

  let keywords_p = Keywords::new_for_parse(parse_arena);
  let only_file = compile_file(parse_arena, &keywords_p, code).unwrap();
  // Re-intern FileCoordinate from 'p into 's
  let file_coord_s = scout_arena.intern_file_coordinate(
    scout_arena.intern_package_coordinate(
      scout_arena.intern_str(only_file.file_coord.package_coord.module.as_str()),
      &only_file.file_coord.package_coord.packages.iter().map(|s| scout_arena.intern_str(s.as_str())).collect::<Vec<_>>(),
    ),
    only_file.file_coord.filepath.as_str(),
  );
  let post_parser = PostParser::new(options, scout_arena, keywords, &keywords_p, parse_arena);
  post_parser
    .scout_program(file_coord_s, &only_file)
    .unwrap()
}

fn compile_for_error<'s, 'ctx, 'p>(
  scout_arena: &'ctx ScoutArena<'s>,
  keywords: &'ctx Keywords<'s>,
  parse_arena: &'ctx ParseArena<'p>,
  code: &str,
) -> ICompileErrorS<'s>
where 'p: 's,
{
  let options = GlobalOptions {
    sanity_check: true,
    use_overload_index: true,
    use_optimized_solver: true,
    verbose_errors: false,
    debug_output: false,
  };

  let keywords_p = Keywords::new_for_parse(parse_arena);
  let only_file = compile_file(parse_arena, &keywords_p, code).unwrap();
  // Re-intern FileCoordinate from 'p into 's
  let file_coord_s = scout_arena.intern_file_coordinate(
    scout_arena.intern_package_coordinate(
      scout_arena.intern_str(only_file.file_coord.package_coord.module.as_str()),
      &only_file.file_coord.package_coord.packages.iter().map(|s| scout_arena.intern_str(s.as_str())).collect::<Vec<_>>(),
    ),
    only_file.file_coord.filepath.as_str(),
  );
  let post_parser = PostParser::new(options, scout_arena, keywords, &keywords_p, parse_arena);
  match post_parser.scout_program(file_coord_s, &only_file) {
    Ok(_) => panic!("Accidentally compiled!"),
    Err(e) => e,
  }
}

#[test]
fn lookup_plus() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int { return +(3, 4); }",
  );
  let main = program.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  match code_body.body.block.expr {
    IExpressionSE::Return(ReturnSE {
      inner:
        IExpressionSE::FunctionCall(FunctionCallSE {
          callable_expr:
            IExpressionSE::OverloadSet(OverloadSetSE {
              lookup: OutsideLoadSE { parts, .. },
            }),
          ..
        }),
      ..
    }) => match &parts.first().expect("non-empty parts").name {
      IImpreciseNameS::CodeName(code_name) => assert_eq!(code_name.name.as_str(), "+"),
      _ => panic!("expected CodeName in OverloadSet first part"),
    },
    _ => panic!("expected return +(3, 4) structure"),
  }
}

#[test]
fn test_struct() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(&scout_arena, &keywords, &parse_arena, "struct Moo { x int; }");
  let imoo = program.lookup_struct("Moo");

  collect_only_snode!(
    NodeRefS::Struct(imoo),
    NodeRefS::LiteralRule(
      literal_rule @ LiteralSR {
        literal: ILiteralSL::MutabilityLiteral(mutability_literal),
        ..
      }
    ) if mutability_literal.mutability == MutabilityP::Mutable
      && literal_rule.rune == imoo.mutability_rune => Some(())
  );
  
  let only_member = expect_1(&imoo.members);
  collect_only_snode!(
    NodeRefS::Struct(imoo),
    NodeRefS::MaybeCoercingLookupRule(
      MaybeCoercingLookupSR {
        name: IImpreciseNameS::CodeName(code_name),
        rune,
        ..
      }
    ) if code_name.name.as_str() == "int" && *rune == *only_member.type_rune() => Some(())
  );

  let normal_member = cast!(only_member, IStructMemberS::NormalStructMember);
  assert_eq!(normal_member.name.as_str(), "x");
  assert_eq!(normal_member.variability, VariabilityP::Final);
}

#[test]
fn linear_struct() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(&scout_arena, &keywords, &parse_arena, "linear struct Moo { x int; }");
  let moo_struct = program.lookup_struct("Moo");
  collect_only_snode!(
    NodeRefS::Struct(moo_struct),
    NodeRefS::MacroCallAttribute(macro_call)
      if macro_call.include == IMacroInclusionP::DontCallMacro
        && macro_call.macro_name.as_str() == "DeriveStructDrop" => Some(())
  );
}

#[test]
fn lambda() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int { return {_ + _}(4, 6); }",
  );
  let main = program.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let lambda = match code_body.body.block.expr {
    IExpressionSE::Return(ReturnSE {
      inner:
        IExpressionSE::FunctionCall(FunctionCallSE {
          callable_expr:
            IExpressionSE::Ownershipped(OwnershippedSE {
              inner_expr: IExpressionSE::Function(lambda_function),
              target_ownership: LoadAsP::LoadAsBorrow,
              ..
            }),
          arg_exprs:
            [
              IExpressionSE::ConstantInt(ConstantIntSE {
                value: 4,
                ..
              }),
              IExpressionSE::ConstantInt(ConstantIntSE {
                value: 6,
                ..
              }),
            ],
          ..
        }),
      ..
    }) => &lambda_function.function,
    _ => panic!("expected return {{_ + _}}(4, 6) structure"),
  };

  let (first_generic_param, second_generic_param) = expect_2(lambda.generic_params);
  match &first_generic_param.tyype {
    IGenericParameterTypeS::CoordGenericParameterType(coord_type) => {
      assert_eq!(coord_type.coord_region, None);
      assert!(!coord_type.region_mutable);
    }
    _ => panic!("expected first lambda generic param to be a CoordGenericParameterType"),
  }
  match &second_generic_param.tyype {
    IGenericParameterTypeS::CoordGenericParameterType(coord_type) => {
      assert_eq!(coord_type.coord_region, None);
      assert!(!coord_type.region_mutable);
    }
    _ => panic!("expected second lambda generic param to be a CoordGenericParameterType"),
  }
  let first_magic_param_rune = match first_generic_param.rune.rune {
    IRuneS::MagicParamRune(magic_param_rune) => magic_param_rune,
    _ => panic!("expected first lambda generic param to be a magic param rune"),
  };
  let second_magic_param_rune = match second_generic_param.rune.rune {
    IRuneS::MagicParamRune(magic_param_rune) => magic_param_rune,
    _ => panic!("expected second lambda generic param to be a magic param rune"),
  };
  assert_ne!(
    first_magic_param_rune, second_magic_param_rune,
    "expected two different magic param runes"
  );
}

#[test]
fn interface() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(&scout_arena, &keywords, &parse_arena, "interface IMoo { func blork(virtual this &IMoo, a bool)void; }");
  let imoo = program.lookup_interface("IMoo");
  let blork = expect_1(&imoo.internal_methods);
  let function_name = cast!(&blork.name, IFunctionDeclarationNameS::FunctionName);
  assert_eq!(function_name.name.as_str(), "blork");
}

#[test]
fn generic_interface() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "interface IMoo<T> { func blork(virtual this &IMoo, a T)void; }",
  );
  let imoo = program.lookup_interface("IMoo");
  let blork = expect_1(imoo.internal_methods);
  let blork_name = cast!(&blork.name, IFunctionDeclarationNameS::FunctionName);
  assert_eq!(blork_name.name.as_str(), "blork");

  let t_ = scout_arena.intern_str("T");
  let t_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS { name: t_ }));
  let imoo_first_rune = &expect_1(imoo.generic_params).rune.rune;
  assert_eq!(*imoo_first_rune, t_rune);
  assert!(imoo.generic_params.iter().any(|generic_param| generic_param.rune.rune == t_rune));
  assert!(blork.generic_params.iter().any(|generic_param| generic_param.rune.rune == t_rune));
}

#[test]
fn impl_() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(&scout_arena, &keywords, &parse_arena, "impl IMoo for Moo;");
  let impl_ = expect_1(program.impls);

  collect_only_snode!(
    NodeRefS::Impl(impl_),
    NodeRefS::MaybeCoercingLookupRule(MaybeCoercingLookupSR {
      name: IImpreciseNameS::CodeName(CodeNameS {
        name: StrI("Moo"),
        ..
      }),
      rune,
      ..
    }) if *rune == impl_.struct_kind_rune => Some(())
  );
  collect_only_snode!(
    NodeRefS::Impl(impl_),
    NodeRefS::MaybeCoercingLookupRule(MaybeCoercingLookupSR {
      name: IImpreciseNameS::CodeName(CodeNameS {
        name: StrI("IMoo"),
        ..
      }),
      rune,
      ..
    }) if *rune == impl_.interface_kind_rune => Some(())
  );
}

#[test]
fn method_call() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int { return true.shout(); }",
  );
  let main = program.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  collect_only_snode!(
    NodeRefS::Expression(code_body.body.block.expr),
    NodeRefS::Expression(IExpressionSE::Return(ReturnSE {
      inner:
        IExpressionSE::FunctionCall(FunctionCallSE {
          callable_expr:
            IExpressionSE::OverloadSet(OverloadSetSE {
              lookup: OutsideLoadSE { parts, .. },
            }),
          arg_exprs:
            [IExpressionSE::ConstantBool(ConstantBoolSE {
              value: true,
              ..
            })],
          ..
        }),
      ..
    })) if matches!(
      parts.first().map(|p| &p.name),
      Some(IImpreciseNameS::CodeName(CodeNameS { name, .. })) if name.as_str() == "shout"
    ) => Some(())
  );
}

#[test]
fn moving_method_call() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int { x = 4; return (x).shout(); }",
  );
  let main = program.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  collect_only_snode!(
    NodeRefS::Expression(code_body.body.block.expr),
    NodeRefS::Expression(IExpressionSE::Return(ReturnSE {
      inner:
        IExpressionSE::FunctionCall(FunctionCallSE {
          callable_expr:
            IExpressionSE::OverloadSet(OverloadSetSE {
              lookup: OutsideLoadSE { parts, .. },
            }),
          arg_exprs:
            [IExpressionSE::LocalLoad(LocalLoadSE {
              name: IVarNameS::CodeVarName(StrI("x")),
              target_ownership: LoadAsP::Use,
              ..
            })],
          ..
        }),
      ..
    })) if matches!(
      parts.first().map(|p| &p.name),
      Some(IImpreciseNameS::CodeName(CodeNameS { name, .. })) if name.as_str() == "shout"
    ) => Some(())
  );
}

#[test]
fn function_with_magic_lambda_and_regular_lambda() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
      {_};
      (a) => {a};
    }",
  );
  let main = program.lookup_function("main");

  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let block = &code_body.body.block;
  let things = cast!(&block.expr, IExpressionSE::Consecutor).exprs;
  let thing_nodes = things
    .iter()
    .map(|thing| NodeRefS::Expression(*thing))
    .collect::<Vec<_>>();
  let lambdas = collect_where_snodes!(
    &thing_nodes,
    NodeRefS::Expression(IExpressionSE::Function(function)) => Some(function)
  );
  let (first_lambda, second_lambda) = expect_2(&lambdas);

  let (_, first_lambda_second_param) = expect_2(first_lambda.function.params);
  match first_lambda_second_param {
    ParameterS {
      pre_checked: false,
      pattern:
        AtomSP {
          name:
            Some(CaptureS {
              name: IVarNameS::MagicParamName(_),
              mutate: false,
            }),
          coord_rune:
            Some(RuneUsage {
              rune: IRuneS::MagicParamRune(_),
              ..
            }),
          destructure: None,
          ..
        },
      ..
    } => {}
    _ => panic!("expected second param on first lambda to be a magic param"),
  }

  let (_, second_lambda_second_param) = expect_2(second_lambda.function.params);
  match second_lambda_second_param {
    ParameterS {
      pre_checked: false,
      pattern:
        AtomSP {
          name:
            Some(CaptureS {
              name: IVarNameS::CodeVarName(StrI("a")),
              mutate: false,
            }),
          coord_rune:
            Some(RuneUsage {
              rune: IRuneS::ImplicitRune(_),
              ..
            }),
          destructure: None,
          ..
        },
      ..
    } => {}
    _ => panic!("expected second param on second lambda to be code var a with implicit rune"),
  }
}

#[test]
fn constructing_members() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "func MyStruct() {
      self.x = 4;
      self.y = true;
    }",
  );
  let mystruct = program.lookup_function("MyStruct");
  let code_body = cast!(&mystruct.body, IBodyS::CodeBody);
  let block = &code_body.body.block;

  match &block.locals[..] {
    [
      LocalS {
        var_name: IVarNameS::ConstructingMemberName(StrI("x")),
        self_borrowed: IVariableUseCertainty::NotUsed,
        self_moved: IVariableUseCertainty::Used,
        self_mutated: IVariableUseCertainty::NotUsed,
        child_borrowed: IVariableUseCertainty::NotUsed,
        child_moved: IVariableUseCertainty::NotUsed,
        child_mutated: IVariableUseCertainty::NotUsed,
      },
      LocalS {
        var_name: IVarNameS::ConstructingMemberName(StrI("y")),
        self_borrowed: IVariableUseCertainty::NotUsed,
        self_moved: IVariableUseCertainty::Used,
        self_mutated: IVariableUseCertainty::NotUsed,
        child_borrowed: IVariableUseCertainty::NotUsed,
        child_moved: IVariableUseCertainty::NotUsed,
        child_mutated: IVariableUseCertainty::NotUsed,
      },
    ] => {}
    other => panic!("unexpected constructing_members locals: {:?}", other),
  }

  let exprs = match block.expr {
    IExpressionSE::Consecutor(ConsecutorSE { exprs }) => exprs,
    _ => panic!("expected consecutor in constructing_members"),
  };
  let expr_nodes = exprs
    .iter()
    .map(|expr| NodeRefS::Expression(*expr))
    .collect::<Vec<_>>();

  let _ = collect_only_snodes!(
    &expr_nodes,
    NodeRefS::Expression(
      IExpressionSE::Let(LetSE {
        pattern:
          AtomSP {
            name:
              Some(CaptureS {
                name: IVarNameS::ConstructingMemberName(StrI("x")),
                mutate: false,
              }),
            destructure: None,
            ..
          },
        expr: IExpressionSE::ConstantInt(ConstantIntSE { value: 4, .. }),
        ..
      })
    ) => Some(())
  );

  let _ = collect_only_snodes!(
    &expr_nodes,
    NodeRefS::Expression(
      IExpressionSE::Let(LetSE {
        pattern:
          AtomSP {
            name:
              Some(CaptureS {
                name: IVarNameS::ConstructingMemberName(StrI("y")),
                mutate: false,
              }),
            destructure: None,
            ..
          },
        expr: IExpressionSE::ConstantBool(ConstantBoolSE { value: true, .. }),
        ..
      })
    ) => Some(())
  );

  let _ = collect_only_snodes!(
    &expr_nodes,
    NodeRefS::Expression(
      IExpressionSE::FunctionCall(FunctionCallSE {
        callable_expr:
          IExpressionSE::OverloadSet(OverloadSetSE {
            lookup: OutsideLoadSE {
              parts: [LoadPartSE {
                name: IImpreciseNameS::CodeName(CodeNameS { name: StrI("MyStruct") }),
                ..
              }],
              ..
            },
          }),
        arg_exprs: [
          IExpressionSE::LocalLoad(LocalLoadSE {
            name: IVarNameS::ConstructingMemberName(StrI("x")),
            target_ownership: LoadAsP::Use,
            ..
          }),
          IExpressionSE::LocalLoad(LocalLoadSE {
            name: IVarNameS::ConstructingMemberName(StrI("y")),
            target_ownership: LoadAsP::Use,
            ..
          }),
        ],
        ..
      })
    ) => Some(())
  );
}

#[test]
fn initializing_runtime_sized_array_requires_size_and_callable_too_few() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let err = compile_for_error(
    &scout_arena,
    &keywords,
    &parse_arena,
    "func MyStruct() {\n  ship = []();\n}",
  );
  match &err {
    ICompileErrorS::InitializingRuntimeSizedArrayRequiresSizeAndCallable(_) => {}
    _ => panic!(
      "expected InitializingRuntimeSizedArrayRequiresSizeAndCallable(_), got {:?}",
      err
    ),
  }
}

#[test]
fn initializing_runtime_sized_array_requires_size_and_callable_too_many() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let err = compile_for_error(
    &scout_arena,
    &keywords,
    &parse_arena,
    "func MyStruct() {\n  ship = [](4, {_}, 10);\n}",
  );
  match &err {
    ICompileErrorS::InitializingRuntimeSizedArrayRequiresSizeAndCallable(_) => {}
    _ => panic!(
      "expected InitializingRuntimeSizedArrayRequiresSizeAndCallable(_), got {:?}",
      err
    ),
  }
}

#[test]
fn initializing_static_sized_array_requires_size_and_callable_too_few() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let err = compile_for_error(
    &scout_arena,
    &keywords,
    &parse_arena,
    "func MyStruct() {\n  ship = [#5]();\n}",
  );
  match &err {
    ICompileErrorS::InitializingStaticSizedArrayRequiresSizeAndCallable(_) => {}
    _ => panic!(
      "expected InitializingStaticSizedArrayRequiresSizeAndCallable(_), got {:?}",
      err
    ),
  }
}

#[test]
fn initializing_static_sized_array_requires_size_and_callable_too_many() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let err = compile_for_error(
    &scout_arena,
    &keywords,
    &parse_arena,
    "func MyStruct() {\n  ship = [#5](4, {_});\n}",
  );
  match &err {
    ICompileErrorS::InitializingStaticSizedArrayRequiresSizeAndCallable(_) => {}
    _ => panic!(
      "expected InitializingStaticSizedArrayRequiresSizeAndCallable(_), got {:?}",
      err
    ),
  }
}

#[test]
fn test_loading_from_member() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "func main() {
      moo = MyStruct();
      return moo.x;
    }",
  );
  let main = program.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  collect_only_snode!(
    NodeRefS::Expression(code_body.body.block.expr),
    NodeRefS::Expression(IExpressionSE::Return(ReturnSE {
      inner:
        IExpressionSE::Dot(DotSE {
          left:
            IExpressionSE::LocalLoad(LocalLoadSE {
              name: IVarNameS::CodeVarName(StrI("moo")),
              target_ownership: LoadAsP::LoadAsBorrow,
              ..
            }),
          member: StrI("x"),
          borrow_container: true,
          ..
        }),
      ..
    })) => Some(())
  );
}

#[test]
fn test_loading_from_member_2() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "func main() {
      moo = MyStruct();
      return &moo.x;
    }",
  );
  let main = program.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  collect_only_snode!(
    NodeRefS::Expression(code_body.body.block.expr),
    NodeRefS::Expression(IExpressionSE::Return(ReturnSE {
      inner:
        IExpressionSE::Ownershipped(OwnershippedSE {
          target_ownership: LoadAsP::LoadAsBorrow,
          inner_expr:
            IExpressionSE::Dot(DotSE {
              left:
                IExpressionSE::LocalLoad(LocalLoadSE {
                  name: IVarNameS::CodeVarName(StrI("moo")),
                  target_ownership: LoadAsP::LoadAsBorrow,
                  ..
                }),
              borrow_container: true,
              ..
            }),
          ..
        }),
      ..
    })) => Some(())
  );
}

#[test]
fn constructing_members_borrowing_another_member() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "func MyStruct() {
      self.x = 4;
      self.y = &self.x;
    }",
  );
  let main = program.lookup_function("MyStruct");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let block = &code_body.body.block;

  match &*block.locals {
    [
      LocalS {
        var_name: IVarNameS::ConstructingMemberName(StrI("x")),
        self_borrowed: IVariableUseCertainty::Used,
        self_moved: IVariableUseCertainty::Used,
        self_mutated: IVariableUseCertainty::NotUsed,
        child_borrowed: IVariableUseCertainty::NotUsed,
        child_moved: IVariableUseCertainty::NotUsed,
        child_mutated: IVariableUseCertainty::NotUsed,
      },
      LocalS {
        var_name: IVarNameS::ConstructingMemberName(StrI("y")),
        self_borrowed: IVariableUseCertainty::NotUsed,
        self_moved: IVariableUseCertainty::Used,
        self_mutated: IVariableUseCertainty::NotUsed,
        child_borrowed: IVariableUseCertainty::NotUsed,
        child_moved: IVariableUseCertainty::NotUsed,
        child_mutated: IVariableUseCertainty::NotUsed,
      },
    ] => {}
    other => panic!("unexpected locals: {:?}", other),
  }

  collect_only_snode!(
    NodeRefS::Expression(block.expr),
    NodeRefS::Expression(IExpressionSE::Let(LetSE {
      pattern: AtomSP {
        name: Some(CaptureS {
          name: IVarNameS::ConstructingMemberName(StrI("x")),
          mutate: false,
        }),
        destructure: None,
        ..
      },
      expr: IExpressionSE::ConstantInt(ConstantIntSE { value: 4, .. }),
      ..
    })) => Some(())
  );
  collect_only_snode!(
    NodeRefS::Expression(block.expr),
    NodeRefS::Expression(IExpressionSE::Let(LetSE {
      pattern: AtomSP {
        name: Some(CaptureS {
          name: IVarNameS::ConstructingMemberName(StrI("y")),
          mutate: false,
        }),
        destructure: None,
        ..
      },
      expr: IExpressionSE::LocalLoad(LocalLoadSE {
        name: IVarNameS::ConstructingMemberName(StrI("x")),
        target_ownership: LoadAsP::LoadAsBorrow,
        ..
      }),
      ..
    })) => Some(())
  );
  collect_only_snode!(
    NodeRefS::Expression(block.expr),
    NodeRefS::Expression(IExpressionSE::FunctionCall(FunctionCallSE {
      callable_expr: IExpressionSE::OverloadSet(OverloadSetSE {
        lookup: OutsideLoadSE {
          parts: [LoadPartSE {
            name: IImpreciseNameS::CodeName(CodeNameS { name: StrI("MyStruct") }),
            ..
          }],
          ..
        },
      }),
      arg_exprs: [
        IExpressionSE::LocalLoad(LocalLoadSE {
          name: IVarNameS::ConstructingMemberName(StrI("x")),
          target_ownership: LoadAsP::Use,
          ..
        }),
        IExpressionSE::LocalLoad(LocalLoadSE {
          name: IVarNameS::ConstructingMemberName(StrI("y")),
          target_ownership: LoadAsP::Use,
          ..
        }),
      ],
      ..
    })) => Some(())
  );
}

#[test]
fn foreach() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "func main() {
      myList = 0;
      foreach i in myList { }
    }",
  );
  let main = program.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let root_expr = code_body.body.block.expr;

  collect_only_snode!(
    NodeRefS::Expression(root_expr),
    NodeRefS::Local(LocalS {
      var_name: IVarNameS::IterableName(_),
      self_borrowed: IVariableUseCertainty::Used,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    }) => Some(())
  );
  collect_only_snode!(
    NodeRefS::Expression(root_expr),
    NodeRefS::Local(LocalS {
      var_name: IVarNameS::IteratorName(_),
      self_borrowed: IVariableUseCertainty::Used,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    }) => Some(())
  );
  collect_only_snode!(
    NodeRefS::Expression(root_expr),
    NodeRefS::Local(LocalS {
      var_name: IVarNameS::IterationOptionName(_),
      self_borrowed: IVariableUseCertainty::Used,
      self_moved: IVariableUseCertainty::Used,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    }) => Some(())
  );
  collect_only_snode!(
    NodeRefS::Expression(root_expr),
    NodeRefS::Local(LocalS {
      var_name: IVarNameS::CodeVarName(StrI("i")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    }) => Some(())
  );

  collect_only_snode!(
    NodeRefS::Expression(root_expr),
    NodeRefS::Expression(IExpressionSE::Let(LetSE {
      pattern: AtomSP {
        name:
          Some(CaptureS {
            name: IVarNameS::IterableName(_),
            mutate: false,
          }),
        coord_rune: None,
        destructure: None,
        ..
      },
      expr:
        IExpressionSE::LocalLoad(LocalLoadSE {
          name: IVarNameS::CodeVarName(StrI("myList")),
          target_ownership: LoadAsP::Use,
          ..
        }),
      ..
    })) => Some(())
  );
  collect_only_snode!(
    NodeRefS::Expression(root_expr),
    NodeRefS::Expression(IExpressionSE::Let(LetSE {
      pattern: AtomSP {
        name:
          Some(CaptureS {
            name: IVarNameS::IteratorName(_),
            mutate: false,
          }),
        coord_rune: None,
        destructure: None,
        ..
      },
      expr:
        IExpressionSE::FunctionCall(FunctionCallSE {
          callable_expr:
            IExpressionSE::OverloadSet(OverloadSetSE {
              lookup: OutsideLoadSE {
                parts: [LoadPartSE {
                  name: IImpreciseNameS::CodeName(CodeNameS {
                    name: StrI("begin"),
                  }),
                  ..
                }],
                ..
              },
            }),
          arg_exprs:
            [IExpressionSE::LocalLoad(LocalLoadSE {
              name: IVarNameS::IterableName(_),
              target_ownership: LoadAsP::LoadAsBorrow,
              ..
            })],
          ..
        }),
      ..
    })) => Some(())
  );
  collect_only_snode!(
    NodeRefS::Expression(root_expr),
    NodeRefS::Expression(IExpressionSE::While(_)) => Some(())
  );
  collect_only_snode!(
    NodeRefS::Expression(root_expr),
    NodeRefS::Expression(IExpressionSE::Let(LetSE {
      pattern: AtomSP {
        name:
          Some(CaptureS {
            name: IVarNameS::IterationOptionName(_),
            mutate: false,
          }),
        coord_rune: None,
        destructure: None,
        ..
      },
      expr:
        IExpressionSE::FunctionCall(FunctionCallSE {
          callable_expr:
            IExpressionSE::OverloadSet(OverloadSetSE {
              lookup: OutsideLoadSE {
                parts: [LoadPartSE {
                  name: IImpreciseNameS::CodeName(CodeNameS {
                    name: StrI("next"),
                  }),
                  ..
                }],
                ..
              },
            }),
          arg_exprs:
            [IExpressionSE::LocalLoad(LocalLoadSE {
              name: IVarNameS::IteratorName(_),
              target_ownership: LoadAsP::LoadAsBorrow,
              ..
            })],
          ..
        }),
      ..
    })) => Some(())
  );
  collect_only_snode!(
    NodeRefS::Expression(root_expr),
    NodeRefS::Expression(IExpressionSE::FunctionCall(FunctionCallSE {
      callable_expr:
        IExpressionSE::OverloadSet(OverloadSetSE {
          lookup: OutsideLoadSE {
            parts: [LoadPartSE {
              name: IImpreciseNameS::CodeName(CodeNameS {
                name: StrI("isEmpty"),
              }),
              ..
            }],
            ..
          },
        }),
      arg_exprs:
        [IExpressionSE::LocalLoad(LocalLoadSE {
          name: IVarNameS::IterationOptionName(_),
          target_ownership: LoadAsP::LoadAsBorrow,
          ..
        })],
      ..
    })) => Some(())
  );
  collect_only_snode!(
    NodeRefS::Expression(root_expr),
    NodeRefS::Expression(IExpressionSE::Break(_)) => Some(())
  );
  collect_only_snode!(
    NodeRefS::Expression(root_expr),
    NodeRefS::Expression(IExpressionSE::Let(LetSE {
      pattern: AtomSP {
        name:
          Some(CaptureS {
            name: IVarNameS::CodeVarName(StrI("i")),
            mutate: false,
          }),
        coord_rune: None,
        destructure: None,
        ..
      },
      expr:
        IExpressionSE::FunctionCall(FunctionCallSE {
          callable_expr:
            IExpressionSE::OverloadSet(OverloadSetSE {
              lookup: OutsideLoadSE {
                parts: [LoadPartSE {
                  name: IImpreciseNameS::CodeName(CodeNameS {
                    name: StrI("get"),
                  }),
                  ..
                }],
                ..
              },
            }),
          arg_exprs:
            [IExpressionSE::LocalLoad(LocalLoadSE {
              name: IVarNameS::IterationOptionName(_),
              target_ownership: LoadAsP::Use,
              ..
            })],
          ..
        }),
      ..
    })) => Some(())
  );
  let iteration_option_uses = collect_where_snode!(
    NodeRefS::Expression(root_expr),
    NodeRefS::Expression(IExpressionSE::LocalLoad(LocalLoadSE {
      name: IVarNameS::IterationOptionName(_),
      target_ownership: LoadAsP::Use,
      ..
    })) => Some(())
  );
  assert!(!iteration_option_uses.is_empty());
}

#[test]
fn this_isnt_special_if_was_explicit_param() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "func moo(self &MyStruct) {
      println(self.x);
    }",
  );
  let moo = program.lookup_function("moo");
  let code_body = cast!(&moo.body, IBodyS::CodeBody);
  let function_call = collect_only_snode!(
    NodeRefS::Program(&program),
    NodeRefS::Expression(IExpressionSE::FunctionCall(function_call)) => Some(function_call)
  );
  let overload_set = cast!(function_call.callable_expr, IExpressionSE::OverloadSet);
  let load_part = expect_1(overload_set.lookup.parts);
  let code_name = cast!(&load_part.name, IImpreciseNameS::CodeName);
  assert_eq!(code_name.name.as_str(), "println");
  let dot = cast!(expect_1(&function_call.arg_exprs), IExpressionSE::Dot);
  assert_eq!(dot.member.as_str(), "x");
  assert!(dot.borrow_container);
  let local_load = cast!(dot.left, IExpressionSE::LocalLoad);
  let code_var_name = cast!(&local_load.name, IVarNameS::CodeVarName);
  assert_eq!(code_var_name.as_str(), "self");
  assert_eq!(local_load.target_ownership, LoadAsP::LoadAsBorrow);

  let function_calls = collect_where_snode!(
    NodeRefS::Program(&program),
    NodeRefS::Expression(IExpressionSE::FunctionCall(_)) => Some(())
  );
  assert_eq!(function_calls.len(), 1);

  let _ = code_body;
}

#[test]
fn reports_when_mutating_nonexistant_local() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let err = compile_for_error(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {\n  set a = a + 1;\n}",
  );
  match &err {
    ICompileErrorS::CouldntFindVarToMutateS(c) => assert_eq!(c.name, "a"),
    _ => panic!("expected CouldntFindVarToMutateS(_, \"a\"), got {:?}", err),
  }
}

#[test]
fn reports_when_extern_function_has_body() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let err = compile_for_error(
    &scout_arena,
    &keywords,
    &parse_arena,
    "extern func bork() int {\n  3\n}",
  );
  match &err {
    ICompileErrorS::ExternHasBodyS(_) => {}
    _ => panic!("expected ExternHasBody(_), got {:?}", err),
  }
}

#[test]
fn reports_when_we_forget_set() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let err = compile_for_error(
    &scout_arena,
    &keywords,
    &parse_arena,
    r#"
exported func main() {
  x = "world!";
  x = "changed";
}"#,
  );
  match &err {
    ICompileErrorS::VariableNameAlreadyExists(
      VariableNameAlreadyExists {
        name: IVarNameS::CodeVarName(StrI("x")),
        ..
      },
    ) => {}
    _ => panic!("expected VariableNameAlreadyExists(_, CodeVarName(\"x\")), got {:?}", err),
  }
}

#[test]
fn reports_when_interface_method_doesnt_have_self() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let err = compile_for_error(
    &scout_arena,
    &keywords,
    &parse_arena,
    "interface IMoo { func blork(a bool)void; }",
  );
  match &err {
    ICompileErrorS::InterfaceMethodNeedsSelf(_) => {}
    _ => panic!("expected InterfaceMethodNeedsSelf(_), got {:?}", err),
  }
}

#[test]
fn statement_after_result_or_return() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let err = compile_for_error(
    &scout_arena,
    &keywords,
    &parse_arena,
    r"
func doCivicDance(virtual this Car) {
  return 4;
  7
}",
  );
  match &err {
    ICompileErrorS::StatementAfterReturnS(_) => {}
    _ => panic!("expected StatementAfterReturnS(_), got {:?}", err),
  }
}

#[test]
fn report_type_mismatch() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let err = compile_for_error(
    &scout_arena,
    &keywords,
    &parse_arena,
    r"
struct Vec<N, T> where N Int
{
  values [#N]<imm>T;
}
",
  );
  match &err {
    ICompileErrorS::RuneExplicitTypeConflictS(
      RuneExplicitTypeConflictS {
        rune: IRuneS::CodeRune(CodeRuneS {
          name: StrI("N"),
        }),
        ..
      },
    ) => {}
    _ => panic!("expected RuneExplicitTypeConflictS(_, CodeRune(\"N\"), _), got {:?}", err),
  }
}

#[test]
fn foreach_expr() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "func main() {
      c = 0;
      a = foreach i in c { i };
    }",
  );
  let main = program.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let root_expr = code_body.body.block.expr;

  let map_exprs = collect_where_snode!(
    NodeRefS::Expression(root_expr),
    NodeRefS::Expression(IExpressionSE::Map(_)) => Some(())
  );
  assert_eq!(map_exprs.len(), 1);

  let while_exprs = collect_where_snode!(
    NodeRefS::Expression(root_expr),
    NodeRefS::Expression(IExpressionSE::While(_)) => Some(())
  );
  assert_eq!(while_exprs.len(), 0);
}

// NOVEL CODE — TDD reproducer for the `destruct` expression scout panic
// surfaced by typing_pass_on_roguelike. The Scala equivalent is `case
// DestructPE(range, innerPE) => ...` at ExpressionScout.scala:393.
#[test]
fn destruct_expression() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "struct MyStruct { a int; }\nexported func main() { m = MyStruct(7); destruct m; }",
  );
  let main = program.lookup_function("main");
  let _code_body = cast!(&main.body, IBodyS::CodeBody);
  // Just ensure scout completed without panicking.
}

// NOVEL CODE — TDD reproducer for the AndPE/OrPE expression scout panic
// surfaced by typing_pass_on_roguelike. Scala equivalent at
// ExpressionScout.scala:605/628 uses `newIf` to expand `&&` / `||` into
// short-circuiting conditionals.
#[test]
fn and_or_expression() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() bool { return true and false or true; }",
  );
  let main = program.lookup_function("main");
  let _code_body = cast!(&main.body, IBodyS::CodeBody);
}

// NOVEL CODE — TDD reproducer for the TuplePE expression scout panic
// surfaced by typing_pass_on_roguelike. The Scala equivalent is
// `case TuplePE(range, elementsPE) => ...` at ExpressionScout.scala:486.
#[test]
fn tuple_expression() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() { x = (3, 4); }",
  );
  let main = program.lookup_function("main");
  let _code_body = cast!(&main.body, IBodyS::CodeBody);
}

// NOVEL CODE — TDD reproducer for the StrInterpolatePE expression scout
// panic surfaced by typing_pass_on_roguelike. The Scala equivalent is
// `case StrInterpolatePE(range, partsPE) => ...` at ExpressionScout.scala:254.
#[test]
fn str_interpolate_expression() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() str { return \"\"; }",
  );
  let main = program.lookup_function("main");
  let _code_body = cast!(&main.body, IBodyS::CodeBody);
  // Just ensure scout completed without panicking.
}