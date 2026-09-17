use std::collections::HashMap;
use bumpalo::Bump;
use crate::postparsing::ast::ProgramS;
use crate::postparsing::itemplatatype::{
  CoordTemplataType, IntegerTemplataType, ITemplataType, KindTemplataType, MutabilityTemplataType,
  OwnershipTemplataType, VariabilityTemplataType,
};
use crate::postparsing::names::{CodeRuneS, IRuneValS};
use crate::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::postparsing::post_parser::ICompileErrorS;
use crate::postparsing::test::post_parser_test_compilation;
use crate::utils::code_hierarchy::{self, IPackageResolver, PackageCoordinate};

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
    // PostParserErrorHumanizer not yet ported; use unwrap() for now (documented gap).
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
fn predict_simple_templex() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let parser_keywords = Keywords::new_for_parse(&parse_arena);
  let code = "func main(a int) {}";
  let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
      .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
  let program = compile(
    &scout_arena,
    &keywords,
    &parser_keywords,
    &parse_arena,
    &resolver,
    code,
  );
  let main = program.lookup_function("main");
  let rune = &main.params.first().unwrap().pattern.coord_rune.as_ref().unwrap().rune;
  let predicted = main
    .rune_to_predicted_type
    .get(&rune)
    .expect("expected rune in rune_to_predicted_type");
  assert_eq!(predicted, &ITemplataType::CoordTemplataType(CoordTemplataType {}));
}

#[test]
fn can_know_rune_type_from_simple_equals() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let parser_keywords = Keywords::new_for_parse(&parse_arena);
  let code = "func main<T, Y>(a T) where Y = T {}";
  let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
      .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
  let program = compile(
    &scout_arena,
    &keywords,
    &parser_keywords,
    &parse_arena,
    &resolver,
    code,
  );
  let main = program.lookup_function("main");

  let t_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
    name: scout_arena.intern_str("T"),
  }));
  let y_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
    name: scout_arena.intern_str("Y"),
  }));

  let t_predicted = main.rune_to_predicted_type.get(&t_rune).unwrap();
  let y_predicted = main.rune_to_predicted_type.get(&y_rune).unwrap();

  assert_eq!(
    t_predicted,
    &ITemplataType::CoordTemplataType(CoordTemplataType {})
  );
  assert_eq!(
    y_predicted,
    &ITemplataType::CoordTemplataType(CoordTemplataType {})
  );
}

#[test]
fn predict_knows_type_from_or_rule() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let parser_keywords = Keywords::new_for_parse(&parse_arena);
  let code = "func main<M Ownership>(a int) where M = any(own, borrow) {}";
  let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
      .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
  let program = compile(
    &scout_arena,
    &keywords,
    &parser_keywords,
    &parse_arena,
    &resolver,
    code,
  );
  let main = program.lookup_function("main");

  let m_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
    name: scout_arena.intern_str("M"),
  }));
  let m_predicted = main.rune_to_predicted_type.get(&m_rune).unwrap();
  assert_eq!(
    m_predicted,
    &ITemplataType::OwnershipTemplataType(OwnershipTemplataType {})
  );
}

#[test]
fn predict_coord_component_types() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  // vregionmut() // Put back in with regions
  // val program =
  //   compile(
  //     """
  //       |func main<T>(a T)
  //       |where T = Ref[O, R, K], O Ownership, R Region, K Kind {}
  //       |""".stripMargin, interner)
  // Take out with regions
  let parser_keywords = Keywords::new_for_parse(&parse_arena);
  let code = "func main<T>(a T) where T = Ref[O, K], O Ownership, K Kind {}";
  let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
      .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
  let program = compile(
    &scout_arena,
    &keywords,
    &parser_keywords,
    &parse_arena,
    &resolver,
    code,
  );
  let main = program.lookup_function("main");

  let t_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
    name: scout_arena.intern_str("T"),
  }));
  let o_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
    name: scout_arena.intern_str("O"),
  }));
  let k_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
    name: scout_arena.intern_str("K"),
  }));
  assert_eq!(
    main.rune_to_predicted_type.get(&t_rune),
    Some(&ITemplataType::CoordTemplataType(CoordTemplataType {}))
  );
  assert_eq!(
    main.rune_to_predicted_type.get(&o_rune),
    Some(&ITemplataType::OwnershipTemplataType(OwnershipTemplataType {}))
  );
  // vregionmut() // Put back in with regions
  // vassertSome(main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("R"))))) shouldEqual RegionTemplataType()
  assert_eq!(
    main.rune_to_predicted_type.get(&k_rune),
    Some(&ITemplataType::KindTemplataType(KindTemplataType {}))
  );
}

#[test]
fn predict_call_types() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let parser_keywords = Keywords::new_for_parse(&parse_arena);
  let code = "func main<A, B>(p1 A, p2 B) where A = T<B>, T = Option, A = int {}";
  let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
      .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
  let program = compile(
    &scout_arena,
    &keywords,
    &parser_keywords,
    &parse_arena,
    &resolver,
    code,
  );
  let main = program.lookup_function("main");

  let a_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
    name: scout_arena.intern_str("A"),
  }));
  let b_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
    name: scout_arena.intern_str("B"),
  }));
  let t_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
    name: scout_arena.intern_str("T"),
  }));
  assert_eq!(
    main.rune_to_predicted_type.get(&a_rune),
    Some(&ITemplataType::CoordTemplataType(CoordTemplataType {}))
  );
  assert_eq!(
    main.rune_to_predicted_type.get(&b_rune),
    Some(&ITemplataType::CoordTemplataType(CoordTemplataType {}))
  );
  // We can't know if T it's a Coord->Coord or a Coord->Kind type.
  assert_eq!(main.rune_to_predicted_type.get(&t_rune), None);
}

#[test]
fn predict_array_sequence_types() {
  // Not sure if this test is useful anymore, since we say M, V, N's types up-front now
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let parser_keywords = Keywords::new_for_parse(&parse_arena);
  let code = "func main<M Mutability, V Variability, N Int, E>(t T) where T Ref = [#N]<M, V>E {}";
  let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
      .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
  let program = compile(
    &scout_arena,
    &keywords,
    &parser_keywords,
    &parse_arena,
    &resolver,
    code,
  );
  let main = program.lookup_function("main");

  let m_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
    name: scout_arena.intern_str("M"),
  }));
  let v_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
    name: scout_arena.intern_str("V"),
  }));
  let n_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
    name: scout_arena.intern_str("N"),
  }));
  let e_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
    name: scout_arena.intern_str("E"),
  }));
  let t_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
    name: scout_arena.intern_str("T"),
  }));
  assert_eq!(
    main.rune_to_predicted_type.get(&m_rune),
    Some(&ITemplataType::MutabilityTemplataType(MutabilityTemplataType {}))
  );
  assert_eq!(
    main.rune_to_predicted_type.get(&v_rune),
    Some(&ITemplataType::VariabilityTemplataType(VariabilityTemplataType {}))
  );
  assert_eq!(
    main.rune_to_predicted_type.get(&n_rune),
    Some(&ITemplataType::IntegerTemplataType(IntegerTemplataType {}))
  );
  assert_eq!(
    main.rune_to_predicted_type.get(&e_rune),
    Some(&ITemplataType::CoordTemplataType(CoordTemplataType {}))
  );
  assert_eq!(
    main.rune_to_predicted_type.get(&t_rune),
    Some(&ITemplataType::CoordTemplataType(CoordTemplataType {}))
  );
}

#[test]
fn predict_for_is_interface() {
  // Not sure if this test is useful anymore, since we say Kind up-front now
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let parser_keywords = Keywords::new_for_parse(&parse_arena);
  let code = "func main<A Kind, B Kind>() where A = isInterface(B) {}";
  let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
      .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
  let program = compile(
    &scout_arena,
    &keywords,
    &parser_keywords,
    &parse_arena,
    &resolver,
    code,
  );
  let main = program.lookup_function("main");

  let a_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
    name: scout_arena.intern_str("A"),
  }));
  let b_rune = scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS {
    name: scout_arena.intern_str("B"),
  }));
  assert_eq!(
    main.rune_to_predicted_type.get(&a_rune),
    Some(&ITemplataType::KindTemplataType(KindTemplataType {}))
  );
  assert_eq!(
    main.rune_to_predicted_type.get(&b_rune),
    Some(&ITemplataType::KindTemplataType(KindTemplataType {}))
  );
}

