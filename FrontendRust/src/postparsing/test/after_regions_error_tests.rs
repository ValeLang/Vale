use std::collections::HashMap;
use bumpalo::Bump;
use crate::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::postparsing::ast::ProgramS;
use crate::postparsing::post_parser::ICompileErrorS;
use crate::postparsing::test::post_parser_test_compilation;
use crate::utils::code_hierarchy::{self, IPackageResolver, PackageCoordinate};
use crate::collect_only_snode;
use crate::postparsing::expressions::FunctionSE;
use crate::postparsing::expressions::IExpressionSE;
use crate::postparsing::itemplatatype::CoordTemplataType;
use crate::postparsing::itemplatatype::ITemplataType;
use crate::postparsing::test::traverse::NodeRefS;

fn compile<'s, 'ctx, 'p>(
  scout_arena: &'ctx ScoutArena<'s>,
  keywords: &'ctx Keywords<'s>,
  parser_keywords: &'ctx Keywords<'p>,
  parse_arena: &'ctx ParseArena<'p>,
  package_to_contents_resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>>,
  code: &str,
) -> ProgramS<'s>
where 'p: 's,
{
  let mut compile = post_parser_test_compilation::test(
    scout_arena, keywords, parser_keywords, parse_arena, package_to_contents_resolver, code,
  );
  match compile.get_scoutput() {
    // PostParserErrorHumanizer not yet ported; use .unwrap() for now (documented gap).
    Err(_) => panic!("compile failed"),
    Ok(t) => *t.expect_one(),
  }
}

fn compile_for_error<'s, 'ctx, 'p>(
  scout_arena: &'ctx ScoutArena<'s>,
  keywords: &'ctx Keywords<'s>,
  parser_keywords: &'ctx Keywords<'p>,
  parse_arena: &'ctx ParseArena<'p>,
  package_to_contents_resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>>,
  code: &str,
) -> ICompileErrorS<'s>
where 'p: 's,
{
  let mut compile = post_parser_test_compilation::test(
    scout_arena, keywords, parser_keywords, parse_arena, package_to_contents_resolver, code,
  );
  match compile.get_scoutput() {
    Err(e) => e,
    Ok(_) => panic!("Successfully compiled!"),
  }
}

#[test]
fn reports_when_non_kind_interface_in_impl() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let parser_keywords = Keywords::new_for_parse(&parse_arena);
  let code = r"
struct Moo {}
interface IMoo {}
impl &IMoo for Moo;
";
  let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
      .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
  let err = compile_for_error(
    &scout_arena,
    &keywords,
    &parser_keywords,
    &parse_arena,
    &resolver,
    code,
  );
  match err {
    ICompileErrorS::CantOwnershipInterfaceInImpl(_) => {}
    other => panic!("Expected CantOwnershipInterfaceInImpl, got {:?}", other),
  }
}

#[test]
fn reports_when_non_kind_struct_in_impl() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let parser_keywords = Keywords::new_for_parse(&parse_arena);
  let code = r"
struct Moo {}
interface IMoo {}
impl IMoo for &Moo;
";
  let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
      .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
  let err = compile_for_error(
    &scout_arena,
    &keywords,
    &parser_keywords,
    &parse_arena,
    &resolver,
    code,
  );
  match err {
    ICompileErrorS::CantOwnershipStructInImpl(_) => {}
    other => panic!("Expected CantOwnershipStructInImpl, got {:?}", other),
  }
}

#[test]
fn abstract_func_without_virtual() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let parser_keywords = Keywords::new_for_parse(&parse_arena);
  let code = r"
sealed interface ISpaceship<X Ref, Y Ref, Z Ref> { }
abstract func launch<X, Y, Z>(self &ISpaceship<X, Y, Z>, bork X) where func drop(X)void;
";
  let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
      .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
  let err = compile_for_error(
    &scout_arena,
    &keywords,
    &parser_keywords,
    &parse_arena,
    &resolver,
    code,
  );
  match err {
    ICompileErrorS::VirtualAndAbstractGoTogether(_) => {}
    other => panic!("Expected VirtualAndAbstractGoTogether, got {:?}", other),
  }
}

#[test]
fn test_one_anonymous_param_lambda_identifying_runes() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let parser_keywords = Keywords::new_for_parse(&parse_arena);
  let code = "\nexported func main() int {do((_) => { true })}\n";
  let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
      .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
  let bork = compile(
    &scout_arena,
    &keywords,
    &parser_keywords,
    &parse_arena,
    &resolver,
    code,
  );

  let main = bork.lookup_function("main");
  // We dont support regions yet, so scout should filter them out.
  assert_eq!(main.generic_params.len(), 0);
  let lambda: &FunctionSE = collect_only_snode!(
    NodeRefS::Function(main),
    NodeRefS::Expression(IExpressionSE::Function(
      function_se
    )) => Some(function_se)
  );
  // Per @LAGTNGZ, the postparser creates one GenericParameterS per untyped lambda
  // param, regardless of whether the user wrote `<T>` or `(_)`. LAGTNGZ still governs how the typing pass
  // specializes the lambda (per-call template expansion into LambdaCallFunctionTemplateNameT).
  assert_eq!(lambda.function.generic_params.len(), 1);
  let underscore_param =
    lambda.function.params.iter().find(|p| p.pattern.name.is_none()).unwrap();
  let underscore_rune = underscore_param.pattern.coord_rune.unwrap().rune;
  assert_eq!(
    *lambda.function.rune_to_predicted_type.get(&underscore_rune).unwrap(),
    ITemplataType::CoordTemplataType(CoordTemplataType {})
  );
  assert!(lambda.function.generic_params.iter().any(|p| p.rune.rune == underscore_rune));
}

