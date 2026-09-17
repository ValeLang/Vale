use bumpalo::Bump;
use crate::cast;
use crate::compile_options::GlobalOptions;
use crate::interner::StrI;
use crate::parsing::tests::utils::compile_file;
use crate::postparsing::ast::{IBodyS, ProgramS};
use crate::postparsing::expressions::{
  ConsecutorSE, FunctionCallSE, FunctionSE, IExpressionSE, IVariableUseCertainty, LetSE, LocalS,
  OwnershippedSE,
};
use crate::postparsing::names::IVarNameS;
use crate::postparsing::post_parser::{ICompileErrorS, PostParser};
use crate::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::postparsing::test::traverse::NodeRefS;
use crate::postparsing::post_parser::VariableNameAlreadyExists;
use crate::postparsing::expressions::BlockSE;
use crate::collect_only_snode;

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

#[test]
fn regular_variable() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int { x = 4; }",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let locals = &code_body.body.block.locals;
  assert_eq!(locals.len(), 1);
  let local = &locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(StrI(\"x\")), NotUsed x6)"),
  }
}

#[test]
fn typeless_local_has_no_coord_rune() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int { x = 4; }",
  );
  let main = program1.lookup_function("main");
  let local = collect_only_snode!(
    NodeRefS::Function(main),
    NodeRefS::Expression(IExpressionSE::Let(
      let_se @ LetSE { .. }
    )) => Some(let_se)
  );
  assert_eq!(local.pattern.coord_rune, None);
}

#[test]
fn reports_defining_same_name_variable() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let err = compile_for_error(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() { x = 4; x = 5; }",
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
fn self_is_pointing_to_function() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int { x = 4; doBlarks(&x); }",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::Used,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), Used, NotUsed, ...)"),
  }
}

#[test]
fn self_is_pointing_to_method() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int { x = 4; x.doBlarks(); }",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::Used,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), Used, NotUsed, ...)"),
  }
}

#[test]
fn self_is_moving_to_function() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int { x = 4; doBlarks(x); }",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::Used,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), NotUsed, Used, ...)"),
  }
}

#[test]
fn self_is_moving_to_method() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int { x = 4; (x).doBlarks(); }",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::Used,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), NotUsed, Used, ...)"),
  }
}

#[test]
fn self_is_mutating_mutable() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int { x = 4; set x = 6; }",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::Used,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), NotUsed, NotUsed, Used, ...)"),
  }
}

#[test]
fn self_is_moving_and_mutating_same_variable() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int { x = 4; set x = +(x, 1); }",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::Used,
      self_mutated: IVariableUseCertainty::Used,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), NotUsed, Used, Used, ...)"),
  }
}

#[test]
fn child_is_pointing() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  ({ doBlarks(&x); })();
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::Used,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(..., child_borrowed: Used)"),
  }
}

#[test]
fn child_is_moving() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  ({ doBlarks(x); })();
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::Used,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(..., child_moved: Used)"),
  }
}

#[test]
fn child_is_mutating() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  ({ set x = 9; })();
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::Used,
    } => {}
    _ => panic!("expected LocalS(..., child_mutated: Used)"),
  }
}

#[test]
fn self_maybe_pointing() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { doBlarks(&x); } else { }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::Used,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), Used, NotUsed, ...)"),
  }
}

#[test]
fn self_maybe_moving() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { doBlarks(x); } else { }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::Used,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), NotUsed, Used, ...)"),
  }
}

#[test]
fn self_maybe_mutating() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { set x = 9; } else { }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::Used,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), NotUsed, NotUsed, Used, ...)"),
  }
}

#[test]
fn children_maybe_pointing() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { { doBlarks(&x); }(); } else { }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::Used,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(..., child_borrowed: Used)"),
  }
}

#[test]
fn children_maybe_moving() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { { doBlarks(x); }(); } else { }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::Used,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(..., child_moved: Used)"),
  }
}

#[test]
fn children_maybe_mutating() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { { set x = 9; }(); } else { }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::Used,
    } => {}
    _ => panic!("expected LocalS(..., child_mutated: Used)"),
  }
}

#[test]
fn self_both_pointing() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { doBoinks(&x); } else { doBloops(&x); }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::Used,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), Used, NotUsed, ...)"),
  }
}

#[test]
fn children_both_pointing() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { { doBoinks(&x); }(); } else { { doBloops(&x); }(); }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::Used,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(..., child_borrowed: Used)"),
  }
}

#[test]
fn self_both_moving() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { doBoinks(x); } else { doBloops(x); }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::Used,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), NotUsed, Used, ...)"),
  }
}

#[test]
fn children_both_moving() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { { doBoinks(x); }(); } else { { doBloops(x); }(); }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::Used,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(..., child_moved: Used)"),
  }
}

#[test]
fn self_both_mutating() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { set x = 9; } else { set x = 8; }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::Used,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), NotUsed, NotUsed, Used, ...)"),
  }
}

#[test]
fn children_both_mutating() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { { set x = 9; }(); } else { { set x = 8; }(); }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::Used,
    } => {}
    _ => panic!("expected LocalS(..., child_mutated: Used)"),
  }
}

#[test]
fn self_pointing_or_moving() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { doThings(&x); } else { moveThis(x); }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::Used,
      self_moved: IVariableUseCertainty::Used,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), Used, Used, NotUsed, ...)"),
  }
}

#[test]
fn children_pointing_or_moving() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { { doThings(&x); }(); } else { { moveThis(x); }(); }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::Used,
      child_moved: IVariableUseCertainty::Used,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(..., child_borrowed: Used, child_moved: Used)"),
  }
}

#[test]
fn self_mutating_or_moving() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { set x = 9; } else { moveThis(x); }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::Used,
      self_mutated: IVariableUseCertainty::Used,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), NotUsed, Used, Used, ...)"),
  }
}

#[test]
fn children_mutating_or_moving() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { { set x = 9; }(); } else { { moveThis(x); }(); }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::Used,
      child_mutated: IVariableUseCertainty::Used,
    } => {}
    _ => panic!("expected LocalS(..., child_moved: Used, child_mutated: Used)"),
  }
}

#[test]
fn self_moving_and_mutating_same_variable() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int { x = 4; set x = +(x, 1); }",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::Used,
      self_mutated: IVariableUseCertainty::Used,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), NotUsed, Used, Used, ...)"),
  }
}

#[test]
fn children_moving_and_mutating_same_variable() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int { x = 4; { set x = +(x, 1); }(); }",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::Used,
      child_mutated: IVariableUseCertainty::Used,
    } => {}
    _ => panic!("expected LocalS(..., child_moved: Used, child_mutated: Used)"),
  }
}

#[test]
fn self_borrowing_param() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "func main(x int) {
  print(&x);
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::Used,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), Used, NotUsed, ...)"),
  }
}

#[test]
fn children_borrowing_param() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "func main(x int) {
  { print(&x); }();
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::Used,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(..., child_borrowed: Used)"),
  }
}

#[test]
fn self_loading_or_mutating_or_moving() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { set x = 9; } else if (true) { moveThis(x); } else { blark(&x); }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::Used,
      self_moved: IVariableUseCertainty::Used,
      self_mutated: IVariableUseCertainty::Used,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), Used, Used, Used, ...)"),
  }
}

#[test]
fn children_loading_or_mutating_or_moving() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  x = 4;
  if (true) { { set x = 9; }(); } else if (true) { { moveThis(x); }(); } else { { blark(&x); }(); }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::Used,
      child_moved: IVariableUseCertainty::Used,
      child_mutated: IVariableUseCertainty::Used,
    } => {}
    _ => panic!("expected LocalS(..., child_*: Used)"),
  }
}

#[test]
fn while_condition_borrowing() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "struct Marine {}
exported func main() int {
  x = Marine();
  while (&x) { }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::Used,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), Used, NotUsed, ...)"),
  }
}

#[test]
fn while_body_maybe_loading() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "struct Marine {}
exported func main() int {
  x = Marine();
  while (true) { doThing(&x); }
}",
  );
  let main = program1.lookup_function("main");
  let code_body = cast!(&main.body, IBodyS::CodeBody);
  let local = &code_body.body.block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::CodeVarName(StrI("x")),
      self_borrowed: IVariableUseCertainty::Used,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(CodeVarNameS(\"x\"), Used, NotUsed, ...)"),
  }
}

fn extract_lambda_block_from_main<'s>(
  body: &'s IBodyS<'s>,
) -> &'s BlockSE<'s> {
  let code_body = cast!(body, IBodyS::CodeBody);
  let block = code_body.body.block;
  let exprs: &[&IExpressionSE] = match block.expr {
    IExpressionSE::Consecutor(ConsecutorSE { exprs }) => exprs,
    IExpressionSE::FunctionCall(fc) => return extract_block_from_lambda_call(fc),
    _ => panic!("expected ConsecutorSE or FunctionCall in block expr"),
  };
  for expr in exprs {
    if let IExpressionSE::FunctionCall(fc) = *expr {
      if let Some(lam_block) = try_extract_block_from_lambda_call(fc) {
        return lam_block;
      }
    }
  }
  panic!("no lambda call found in main body")
}

fn try_extract_block_from_lambda_call<'s>(
  fc: &FunctionCallSE<'s>,
) -> Option<&'s BlockSE<'s>> {
  let inner = match fc.callable_expr {
    IExpressionSE::Ownershipped(OwnershippedSE { inner_expr, .. }) => inner_expr,
    IExpressionSE::Function(func_se) => return extract_block_from_func_se(func_se),
    _ => return None,
  };
  match inner {
    IExpressionSE::Function(func_se) => extract_block_from_func_se(func_se),
    _ => None,
  }
}

fn extract_block_from_func_se<'s>(
  func_se: &FunctionSE<'s>,
) -> Option<&'s BlockSE<'s>> {
  let code_body = match &func_se.function.body {
    IBodyS::CodeBody(c) => c,
    _ => return None,
  };
  Some(code_body.body.block)
}

fn extract_block_from_lambda_call<'s>(
  fc: &FunctionCallSE<'s>,
) -> &'s BlockSE<'s> {
  try_extract_block_from_lambda_call(fc).expect("callable is not lambda")
}
#[test]
fn include_closure_var_in_locals() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "struct Marine {}
exported func main() int {
  m = Marine();
  { m.shout() }();
}",
  );
  let main = program1.lookup_function("main");
  let lam_block = extract_lambda_block_from_main(&main.body);
  let local = &lam_block.locals[0];
  match local {
    LocalS {
      var_name: IVarNameS::ClosureParamName(_),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(ClosureParamNameS(_), NotUsed x6)"),
  }
}

#[test]
fn include_underscore_in_locals() {
  let parse_bump = Bump::new();
  let scout_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let scout_arena = ScoutArena::new(&scout_bump);
  let keywords = Keywords::new_for_scout(&scout_arena);
  let program1 = compile(
    &scout_arena,
    &keywords,
    &parse_arena,
    "exported func main() int {
  { print(_) }(3);
}",
  );
  let main = program1.lookup_function("main");
  let lam_block = extract_lambda_block_from_main(&main.body);
  let locals = &lam_block.locals;
  let closure_param = locals
    .iter()
    .find(|l| matches!(&l.var_name, IVarNameS::ClosureParamName(_)))
    .expect("no ClosureParamName local found");
  match closure_param {
    LocalS {
      var_name: IVarNameS::ClosureParamName(_),
      self_borrowed: IVariableUseCertainty::NotUsed,
      self_moved: IVariableUseCertainty::NotUsed,
      self_mutated: IVariableUseCertainty::NotUsed,
      child_borrowed: IVariableUseCertainty::NotUsed,
      child_moved: IVariableUseCertainty::NotUsed,
      child_mutated: IVariableUseCertainty::NotUsed,
    } => {}
    _ => panic!("expected LocalS(ClosureParamNameS(_), NotUsed x6)"),
  }
}

