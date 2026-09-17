use bumpalo::Bump;
use crate::cast;
use crate::compile_options::GlobalOptions;
use crate::interner::StrI;
use crate::parsing::tests::utils::compile_file;
use crate::parsing::ast::OwnershipP;
use crate::postparsing::ast::{GenericParameterS, ParameterS, ProgramS};
use crate::postparsing::ast::IBodyS::CodeBody;
use crate::postparsing::ast::IGenericParameterTypeS::CoordGenericParameterType;
use crate::postparsing::names::{CodeNameS, CodeRuneS, IRuneValS, IVarNameS};
use crate::postparsing::names::IRuneS::{CodeRune, ImplicitRune};
use crate::postparsing::patterns::{AtomSP, CaptureS};
use crate::postparsing::names::IImpreciseNameS;
use crate::postparsing::rules::rules::{AugmentSR, MaybeCoercingLookupSR};
use crate::postparsing::rules::RuneUsage;
use crate::postparsing::test::traverse::NodeRefS;
use crate::postparsing::post_parser::{CouldntFindRuneS, ICompileErrorS, PostParser};
use crate::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::collect_only_snode;

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
fn coord_rune_rule() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(&scout_arena, &keywords, &parse_arena, "func main<T>(moo T) { }");
  let main = program1.lookup_function("main");

  // vregionmut() // Take out with regions
  // Should have T, the default region, and the return rune
  assert_eq!(main.rune_to_predicted_type.len(), 2);
  // // Should have T, the default region, and the return rune
  // assert_eq!(main.rune_to_predicted_type.len(), 3);

  // vregionmut() // see below
  match main.generic_params {
    [GenericParameterS {
      rune: RuneUsage {
        rune: CodeRune(CodeRuneS { name: StrI("T") }),
        ..
      },
      tyype: CoordGenericParameterType(_),
      default: None,
      ..
    }] => {
      // Put this back in when we have regions
      // , _ // implicit default region
    }
    _ => panic!("expected GenericParameterS(_, RuneUsage(_, CodeRuneS(StrI(\"T\"))), CoordGenericParameterTypeS, None)"),
  }
}

#[test]
fn returned_rune() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(&scout_arena, &keywords, &parse_arena, "func main<T>(moo T) T { moo }");
  let main = program1.lookup_function("main");

  let t_name = scout_arena.intern_str("T");
  let t_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS { name: t_name }));
  assert!(
    main.generic_params.iter().any(|p| p.rune.rune == t_rune),
    "genericParams should contain rune for T"
  );
  match &main.maybe_ret_coord_rune {
    Some(RuneUsage {
      rune: CodeRune(CodeRuneS { name: StrI("T") }),
      ..
    }) => {}
    _ => panic!("expected Some(RuneUsage(_, CodeRuneS(\"T\")))"),
  }
}

#[test]
fn borrowed_rune() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(&scout_arena, &keywords, &parse_arena, "func main<T>(moo &T) { }");
  let main = program1.lookup_function("main");

  let t_coord_rune_from_params = match main.params {
    [ParameterS {
      pre_checked: false,
      pattern:
        AtomSP {
          name: Some(CaptureS {
            name: IVarNameS::CodeVarName(StrI("moo")),
            mutate: false,
          }),
          coord_rune: Some(RuneUsage {
            rune: tcr @ ImplicitRune(_),
            ..
          }),
          destructure: None,
          ..
        },
      ..
    }] => tcr,
    _ => panic!("param structure did not match"),
  };

  let t_coord_rune_from_rules: &RuneUsage<'_> = collect_only_snode!(
    NodeRefS::Function(main),
    NodeRefS::AugmentRule(AugmentSR {
      inner_rune: RuneUsage {
        rune: CodeRune(CodeRuneS { name: StrI("T") }),
        ..
      },
      ownership: Some(OwnershipP::Borrow),
      result_rune,
      ..
    }) => Some(result_rune)
  );

  assert_eq!(t_coord_rune_from_params, &t_coord_rune_from_rules.rune);
}

#[test]
fn anonymous_typed_param() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(&scout_arena, &keywords, &parse_arena, "func main(_ int) { }");
  let main = program1.lookup_function("main");

  let param_rune = match main.params {
    [ParameterS {
      pre_checked: false,
      pattern:
        AtomSP {
          name: None,
          coord_rune: Some(RuneUsage {
            rune: pr @ ImplicitRune(_),
            ..
          }),
          destructure: None,
          ..
        },
      ..
    }] => pr,
    _ => panic!("param structure did not match (expected anonymous typed param)"),
  };

  let rule_rune: RuneUsage = collect_only_snode!(
    NodeRefS::Function(main),
    NodeRefS::MaybeCoercingLookupRule(MaybeCoercingLookupSR {
      name: IImpreciseNameS::CodeName(CodeNameS { name: StrI("int") }),
      rune: pr,
      ..
    }) => Some(pr)
  );
  assert_eq!(&rule_rune.rune, param_rune);
}

// #[test]
// fn regioned_pure_function() {
//   panic!("Unmigrated test: regioned_pure_function");
// }

// #[test]
// fn regioned_additive_function() {
//   panic!("Unmigrated test: regioned_additive_function");
// }

#[test]
fn test_param_less_lambda_identifying_runes() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {do({ return 3; })}",
  );
  let main = program1.lookup_function("main");

  // vregionmut() // Put this back in when we have regions
  // main.genericParams.size shouldEqual 1 // only the default region
  // Take this out when we have regions
  assert_eq!(main.generic_params.len(), 0);

  let code_body = cast!(&main.body, CodeBody);
  let lambda = collect_only_snode!(
    NodeRefS::Expression(code_body.body.block.expr),
    NodeRefS::Function(lambda_func) => Some(lambda_func)
  );
  // vregionmut() // Put this back in when we have regions
  // lambda.function.genericParams.size shouldEqual 1 // only the default region
  // Take this out when we have regions
  assert_eq!(lambda.generic_params.len(), 0);
}

#[test]
fn test_one_param_lambda_identifying_runes() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {do({ _ })}",
  );
  let main = program1.lookup_function("main");

  // vregionmut() // Put this back in when we have regions
  // main.genericParams.size shouldEqual 1 // Only the default region
  // Take this out when we have regions
  assert_eq!(main.generic_params.len(), 0);

  let code_body = cast!(&main.body, CodeBody);
  let lambda = collect_only_snode!(
    NodeRefS::Expression(code_body.body.block.expr),
    NodeRefS::Function(lambda_func) => Some(lambda_func)
  );
  // vregionmut() // Put this back in when we have regions
  // // magic param + default region
  // lambda.function.genericParams.size shouldEqual 2
  // Take this out when we have regions
  assert_eq!(lambda.generic_params.len(), 1);
}

#[test]
fn report_that_default_region_must_be_mentioned_in_generic_params() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let err = compile_for_error(
    &scout_arena,
    &keywords,
    &parse_arena,
    "pure func main<r'>(ship &r'Spaceship) t'{ }",
  );
  match &err {
    ICompileErrorS::CouldntFindRuneS(CouldntFindRuneS { ref name, .. }) => {
      match name.as_str() {
        "t" => {}
        _ => panic!("expected CouldntFindRuneS with name \"t\", got {:?}", err),
      }
    }
    _ => panic!("expected CouldntFindRuneS with name \"t\", got {:?}", err),
  }
}

