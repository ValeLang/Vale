use bumpalo::Bump;
use crate::postparsing::ast::ProgramS;
use crate::postparsing::post_parser::{
  ExternHasBodyS, ICompileErrorS, InterfaceMethodNeedsSelf, VariableNameAlreadyExists,
};
use crate::postparsing::post_parser_error_humanizer::humanize;
use crate::postparsing::names::IVarNameS;
use crate::utils::code_hierarchy::FileCoordinateMap;
use crate::utils::range::RangeS;
use crate::utils::source_code_utils::{
  humanize_pos_code_map, line_containing, line_range_containing, lines_between,
};
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::Keywords;

fn compile<'s, 'ctx, 'p>(
  _scout_arena: &'ctx ScoutArena<'s>,
  _keywords: &'ctx Keywords<'s>,
  _parse_arena: &'ctx ParseArena<'p>,
  _code: &str,
) -> ProgramS<'s>
{
  panic!("Unimplemented: compile");
}

fn compile_for_error<'s, 'ctx, 'p>(
  _scout_arena: &'ctx ScoutArena<'s>,
  _keywords: &'ctx Keywords<'s>,
  _parse_arena: &'ctx ParseArena<'p>,
  _code: &str,
) -> ICompileErrorS<'s>
{
  panic!("Unimplemented: compile_for_error");
}

#[test]
fn humanize_errors() {
  let scout_bump = Bump::new();
  let scout_arena = ScoutArena::new(&scout_bump);
  let code_map = FileCoordinateMap::<'_, String>::test(&scout_arena, "blah blah blah\nblah blah blah".to_string());
  let tz = RangeS::test_zero(&scout_arena);

  let humanize_pos = |x: &_| humanize_pos_code_map(&code_map, x);
  let lines_between_fn = |x: &_, y: &_| lines_between(&code_map, x, y);
  let line_range_containing_fn = |x: &_| line_range_containing(&code_map, x);
  let line_containing_fn = |x: &_| line_containing(&code_map, x);

  let err1 = ICompileErrorS::VariableNameAlreadyExists(VariableNameAlreadyExists {
    range: tz.clone(),
    name: IVarNameS::CodeVarName(scout_arena.intern_str("Spaceship")),
  });
  assert!(!humanize(humanize_pos, lines_between_fn, line_range_containing_fn, line_containing_fn, &err1).is_empty());

  let err2 = ICompileErrorS::InterfaceMethodNeedsSelf(InterfaceMethodNeedsSelf { range: tz.clone() });
  assert!(!humanize(humanize_pos, lines_between_fn, line_range_containing_fn, line_containing_fn, &err2).is_empty());

  let err3 = ICompileErrorS::ExternHasBodyS(ExternHasBodyS { range: tz.clone() });
  assert!(!humanize(humanize_pos, lines_between_fn, line_range_containing_fn, line_containing_fn, &err3).is_empty());
}
