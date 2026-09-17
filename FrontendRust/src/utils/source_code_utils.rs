use std::path::Path;

use crate::utils::code_hierarchy::{FileCoordinate, FileCoordinateMap};
use crate::utils::range::{CodeLocationS, RangeS};
use crate::utils::code_hierarchy::PackageCoordinate;

pub fn humanize_pos_path(humanized_file_path: &str, source: &str, pos: i32) -> String {
  let mut line = 0;
  let mut line_begin = 0;
  let mut i = 0;

  while i < pos as usize && i < source.len() {
    if source.chars().nth(i) == Some('\n') {
      line_begin = i + 1;
      line += 1;
    }
    i += 1;
  }

  format!(
    "{}:{}:{}",
    humanized_file_path,
    line + 1,
    i - line_begin + 1
  )
}

pub fn humanize_package<'a>(package_coord: &'a PackageCoordinate<'a>) -> String {
  let mut result = package_coord.module.as_str().to_string();
  for p in package_coord.packages.iter() {
    result.push('.');
    result.push_str(p.as_str());
  }
  result
}

pub fn humanize_file<'a>(coordinate: &FileCoordinate<'a>) -> String {
  format!(
    "{}:{}",
    humanize_package(coordinate.package_coord),
    coordinate.filepath.as_str()
  )
}

pub fn humanize_pos_code_map<'a, 'b>(
  code_map: &FileCoordinateMap<'a, String>,
  code_location_s: &CodeLocationS<'b>,
) -> String {
  let file = code_location_s.file;
  if code_location_s.offset < 0 {
    return format!("{}:{}", humanize_file(file), code_location_s.offset);
  }
  let source = code_map
    .get_by_value(file)
    .expect("humanize_pos_code_map: coordinate not found in code map");
  humanize_pos_path(&humanize_file(file), source, code_location_s.offset)
}

pub fn humanize_pos(file_path: &Path, source: &str, pos: i32) -> String {
  humanize_pos_path(&file_path.display().to_string(), source, pos)
}

fn next_thing_and_rest_of_line_code_map<'a>(
  _code_map: &FileCoordinateMap<'a, String>,
  _file: &FileCoordinate<'a>,
  _position: i32,
) -> String {
  panic!("Unimplemented: next_thing_and_rest_of_line");
}

pub fn next_thing_and_rest_of_line(source: &str, pos: usize) -> String {
  let remaining = &source[pos..];
  remaining
    .split('\n')
    .next()
    .unwrap_or("")
    .trim()
    .to_string()
}

fn line_begin<'a>(
  _code_map: &FileCoordinateMap<'a, String>,
  _code_location_s: &CodeLocationS<'a>,
) -> CodeLocationS<'a> {
  panic!("Unimplemented: line_begin");
}

pub fn line_range_containing<'a, 'b>(
  code_map: &FileCoordinateMap<'a, String>,
  code_location_s: &CodeLocationS<'b>,
) -> RangeS<'b> {
  let file = code_location_s.file;
  let offset = code_location_s.offset;
  if offset < 0 {
    return RangeS::new(
      CodeLocationS { file, offset: -1 },
      CodeLocationS { file, offset: 0 },
    );
  }
  let text = code_map
    .get_by_value(code_location_s.file)
    .expect("line_range_containing: coordinate not found in code map");
  let text_len = text.len() as i32;
  let mut line_begin: i32 = 0;
  while line_begin < text_len {
    let line_end = match text[line_begin as usize..].find('\n') {
      None => text_len,
      Some(i) => line_begin + i as i32,
    };
    if line_begin <= offset && offset <= line_end {
      return RangeS::new(
        CodeLocationS { file: file, offset: line_begin },
        CodeLocationS { file, offset: line_end },
      );
    }
    line_begin = line_end + 1;
  }
  if offset == text_len {
    return RangeS::new(
      CodeLocationS { file: file, offset: line_begin },
      CodeLocationS { file, offset: line_begin },
    );
  }
  panic!("line_range_containing: offset beyond text");
}

pub fn lines_between<'a, 'b>(
  code_map: &FileCoordinateMap<'a, String>,
  begin_code_loc: &CodeLocationS<'b>,
  end_code_loc: &CodeLocationS<'b>,
) -> Vec<RangeS<'b>> {
  assert!(begin_code_loc.file == end_code_loc.file);
  assert!(begin_code_loc.offset <= end_code_loc.offset);

  let file = begin_code_loc.file;
  if file.is_internal() {
    return vec![];
  }
  let range = line_range_containing(code_map, begin_code_loc);
  let mut line_begin = range.begin.offset;
  let mut line_end = range.end.offset;
  let mut result = vec![RangeS::new(
    CodeLocationS { file: file, offset: line_begin },
    CodeLocationS { file: file, offset: line_end },
  )];
  let text = code_map
    .get_by_value(file)
    .expect("lines_between: coordinate not found in code map");
  let text_len = text.len() as i32;
  while line_begin < end_code_loc.offset && line_begin < text_len {
    line_end = match text[line_begin as usize..].find('\n') {
      None => text_len,
      Some(i) => line_begin + i as i32,
    };
    result.push(RangeS::new(
      CodeLocationS { file: file, offset: line_begin },
      CodeLocationS { file: file, offset: line_end },
    ));
    line_begin = line_end + 1;
  }
  result
}

pub fn line_containing<'a, 'b>(
  code_map: &FileCoordinateMap<'a, String>,
  code_location_s: &CodeLocationS<'b>,
) -> String {
  if code_location_s.file.is_internal() {
    return humanize_file(code_location_s.file);
  }
  let range = line_range_containing(code_map, code_location_s);
  let text = code_map
    .get_by_value(code_location_s.file)
    .expect("line_containing: coordinate not found in code map");
  let begin = range.begin.offset as usize;
  let end = range.end.offset as usize;
  text[begin..end].to_string()
}

