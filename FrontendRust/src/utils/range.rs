use crate::utils::code_hierarchy::FileCoordinate;
use crate::scout_arena::ScoutArena;

impl<'a> RangeS<'a> {
  pub fn internal(scout_arena: &ScoutArena<'a>, internal_num: i32) -> RangeS<'a> {
    assert!(internal_num < 0, "RangeS::internal - internal_num must be negative");
    RangeS::new(
      CodeLocationS::internal(scout_arena, internal_num),
      CodeLocationS::internal(scout_arena, internal_num))
  }
  
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct CodeLocationS<'a> {
  pub file: &'a FileCoordinate<'a>,
  pub offset: i32,
}

impl<'a> CodeLocationS<'a> {
  // Keep in sync with CodeLocation2
  pub fn test_zero(scout_arena: &ScoutArena<'a>) -> CodeLocationS<'a> {
    Self::internal(scout_arena, -1)
  }

  // SPORK
  pub fn internal(scout_arena: &ScoutArena<'a>, internal_num: i32) -> CodeLocationS<'a> {
    assert!(internal_num < 0, "CodeLocationS::internal - internal_num must be negative");
    let package_coord =
      scout_arena.intern_package_coordinate(scout_arena.intern_str(""), &[]);
    let file = scout_arena.intern_file_coordinate(package_coord, "internal");
    CodeLocationS {
      file,
      offset: internal_num,
    }
  }
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct RangeS<'a> {
  pub begin: CodeLocationS<'a>,
  pub end: CodeLocationS<'a>,
}

// Scala's toString was just for debug purposes (covered by #[derive(Debug)])
impl<'a> RangeS<'a> {
  pub fn new(begin: CodeLocationS<'a>, end: CodeLocationS<'a>) -> RangeS<'a> {
    assert!(begin.file == end.file, "RangeS: begin.file != end.file");
    assert!(begin.offset <= end.offset, "RangeS: begin.offset > end.offset");
    RangeS { begin, end }
  }

  // Should only be used in tests.
  pub fn test_zero(scout_arena: &ScoutArena<'a>) -> RangeS<'a> {
    let tz = CodeLocationS::test_zero(scout_arena);
    RangeS::new(tz.clone(), tz)
  }

  // SPORK
  pub fn file(&self) -> &'a FileCoordinate<'a> {
    self.begin.file
  }
}

