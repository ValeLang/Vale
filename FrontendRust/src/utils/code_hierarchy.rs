
use indexmap::IndexMap;
use std::collections::HashMap;
use crate::interner::{InternedSlice, StrI};
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::Keywords;
use bumpalo::Bump;
use std::fmt::Display;
use std::fmt::Formatter;
use std::fmt::Result;

pub struct OrResolver<P, F> {
  primary: P,
  fallback: F,
}

impl<'a, T, P, F> IPackageResolver<'a, T> for OrResolver<P, F>
where
  P: IPackageResolver<'a, T>,
  F: IPackageResolver<'a, T>,
{
  fn resolve(&self, package_coord: &'a PackageCoordinate<'a>) -> Option<T> {
    self
      .primary
      .resolve(package_coord)
      .or_else(|| self.fallback.resolve(package_coord))
  }
}

/// Implement IPackageResolver for function pointers (for lambda-style resolvers)
impl<'a, T, F> IPackageResolver<'a, T> for F
where
  F: Fn(&'a PackageCoordinate<'a>) -> Option<T>,
{
  fn resolve(&self, package_coord: &'a PackageCoordinate<'a>) -> Option<T> {
    self(package_coord)
  }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct FileCoordinate<'a> {
  pub package_coord: &'a PackageCoordinate<'a>,
  pub filepath: StrI<'a>,
}
// compareTo and compare methods were commented out in Scala (ordering not implemented)
impl<'a> FileCoordinate<'a> {

  pub fn is_internal(&self) -> bool {
    self.package_coord.is_internal()
  }

  pub fn is_test(&self) -> bool {
    self.package_coord.is_test() && self.filepath == "test.vale"
  }

  pub fn eq_by_value<'b>(&self, other: &FileCoordinate<'b>) -> bool {
    self.filepath.as_str() == other.filepath.as_str()
      && self.package_coord.eq_by_value(other.package_coord)
  }

  pub fn test(scout_arena: &ScoutArena<'a>) -> FileCoordinate<'a> {
    let test_module = scout_arena.intern_str(TEST_MODULE);
    let package_coord = scout_arena.intern_package_coordinate(test_module, &[]);
    *scout_arena.intern_file_coordinate(package_coord, "test.vale")
  }

}
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct PackageCoordinate<'a> {
  pub module: StrI<'a>,
  pub packages: InternedSlice<'a, StrI<'a>>,
}
// compareTo and compare methods were commented out in Scala (ordering not implemented)
impl<'a> PackageCoordinate<'a> {

  pub fn is_internal(&self) -> bool {
    self.module == ""
  }

  pub fn is_test(&self) -> bool {
    self.module == TEST_MODULE && self.packages.is_empty()
  }

  pub fn eq_by_value<'b>(&self, other: &PackageCoordinate<'b>) -> bool {
    self.module.as_str() == other.module.as_str()
      && self.packages.as_slice().len() == other.packages.as_slice().len()
      && self.packages.as_slice().iter().zip(other.packages.as_slice().iter())
          .all(|(a, b)| a.as_str() == b.as_str())
  }

  pub fn parent(&self, bump: &'a Bump) -> Option<PackageCoordinate<'a>> {
    if self.packages.is_empty() {
      return None;
    }
    let parent_packages = &self.packages.as_slice()[0..self.packages.len() - 1];
    let arena_packages = bump.alloc_slice_copy(parent_packages);
    Some(PackageCoordinate {
      module: self.module,
      packages: InternedSlice::new(arena_packages),
    })
    // V: do we have a coherent story for when something is inline or in the arena?
    // VA: Not yet. This parent() method takes a raw &Bump and calls alloc_slice_copy directly,
    // VA: bypassing interning. Every other PackageCoordinate constructor goes through
    // VA: parse_arena/scout_arena intern_package_coordinate() which deduplicates. If called, this
    // VA: would produce non-interned coordinates that break pointer-identity equality. Rule: semantic
    // VA: types (PackageCoordinate, FileCoordinate) should always go through an arena intern method;
    // VA: raw bump is only for internal data structures (ArenaIndexMap). This method has zero callers.
  }

  pub fn test_tld<'ctx>(
    parse_arena: &'ctx ParseArena<'a>,
    _keywords: &'ctx Keywords<'a>,
  ) -> &'a PackageCoordinate<'a>
  where
    'a: 'ctx,
  {
    parse_arena.intern_package_coordinate(parse_arena.intern_str(TEST_MODULE), &[])
  }

  pub fn builtin<'ctx>(
    parse_arena: &'ctx ParseArena<'a>,
    keywords: &'ctx Keywords<'a>,
  ) -> &'a PackageCoordinate<'a>
  where
    'a: 'ctx,
  {
    parse_arena.intern_package_coordinate(keywords.empty_string, &[])
  }

  pub fn internal(
    scout_arena: &ScoutArena<'a>,
    keywords: &Keywords<'a>,
  ) -> PackageCoordinate<'a> {
    *scout_arena.intern_package_coordinate(keywords.empty_string, &[])
  }

}
// Realizes Scala's case-class auto-toString for PackageCoordinate:
//   PackageCoordinate(<module>,Vector(<pkg1>, <pkg2>, ...))
// Per Scala convention: no space between case-class fields, comma+space between Vector elements.
// NOTE: Rust StrI's Display canon (interner.rs:40) prints the bare string, while Scala StrI's
// own case-class toString wraps as `StrI(<value>)`. This divergence is inherited from the
// existing canon and not propagated here.
impl<'a> Display for PackageCoordinate<'a> {
  fn fmt(&self, f: &mut Formatter<'_>) -> Result {
    write!(f, "PackageCoordinate({},Vector(", self.module)?;
    let mut first = true;
    for pkg in self.packages.as_slice() {
      if !first { write!(f, ", ")?; }
      write!(f, "{}", pkg)?;
      first = false;
    }
    write!(f, "))")
  }
}

const TEST_MODULE: &str = "test";

pub fn simple<'a, T: Clone>(
    file_coord: &'a FileCoordinate<'a>,
    contents: T,
  ) -> FileCoordinateMap<'a, T> {
    let mut result = FileCoordinateMap::new();
    result.put(file_coord, contents);
    result
  }

pub fn test<'a, C: Clone>(
    scout_arena: &ScoutArena<'a>,
    contents: C,
  ) -> FileCoordinateMap<'a, C> {
    const TEST_MODULE: &str = "test";
    let test_module = scout_arena.intern_str(TEST_MODULE);
    let package_coord = scout_arena.intern_package_coordinate(test_module, &[]);
    let file_coord = scout_arena.intern_file_coordinate(package_coord, "test.vale");
    let mut result = FileCoordinateMap::new();
    result.put(file_coord, contents);
    result
  }

pub fn test_from_vec<'a, T: Clone>(
    parse_arena: &ParseArena<'a>,
    contents: Vec<T>,
  ) -> FileCoordinateMap<'a, T> {
    let mut map = HashMap::new();
    for (index, code) in contents.into_iter().enumerate() {
      map.insert(format!("{}.vale", index), code);
    }
    test_from_map(parse_arena, map)
  }

pub fn test_from_map<'a, T: Clone>(
    parse_arena: &ParseArena<'a>,
    contents: HashMap<String, T>,
  ) -> FileCoordinateMap<'a, T> {
    let mut result = FileCoordinateMap::new();
    let package_coord = parse_arena.intern_package_coordinate(parse_arena.intern_str(TEST_MODULE), &[]);
    for (filepath, file_contents) in contents {
      let file_coord = parse_arena.intern_file_coordinate(package_coord, &filepath);
      result.put(file_coord, file_contents);
    }
    result
  }

#[derive(Clone, Debug)]
pub struct FileCoordinateMap<'a, Contents> {
  pub package_coord_to_file_coords: HashMap<&'a PackageCoordinate<'a>, Vec<&'a FileCoordinate<'a>>>,
  pub file_coord_to_contents: HashMap<&'a FileCoordinate<'a>, Contents>,
}
// mergeNonOverlapping was commented out in Scala (not yet needed)
impl<'a, Contents: Clone> FileCoordinateMap<'a, Contents> {

  pub fn new() -> Self {
    FileCoordinateMap {
      package_coord_to_file_coords: HashMap::new(),
      file_coord_to_contents: HashMap::new(),
    }
  }

  /// Companion-object style constructor for tests. Mirrors FileCoordinateMap.test(scout_arena, contents).
  pub fn test(scout_arena: &ScoutArena<'a>, contents: Contents) -> Self {
    super::code_hierarchy::test(scout_arena, contents)
  }

  pub fn apply(&self, coord: &'a FileCoordinate<'a>) -> &Contents {
    self
      .file_coord_to_contents
      .get(coord)
      .expect("FileCoordinateMap::apply - coordinate not found")
  }

  pub fn get_by_value(&self, coord: &FileCoordinate<'_>) -> Option<&Contents> {
    self.file_coord_to_contents.iter()
      .find(|(k, _)| k.eq_by_value(coord))
      .map(|(_, v)| v)
  }

  // This is different from put in that we can hand in an empty map here.
  // It's the only way to have an empty package in the FileCoordinateMap.
  pub fn put_package(
    &mut self,
    package_coord: &'a PackageCoordinate<'a>,
    new_file_coord_to_contents: HashMap<&'a FileCoordinate<'a>, Contents>,
  ) {
    let file_coords: Vec<&'a FileCoordinate<'a>> =
      new_file_coord_to_contents.keys().cloned().collect();
    self
      .package_coord_to_file_coords
      .insert(package_coord, file_coords);

    for (file_coord, contents) in new_file_coord_to_contents {
      self.file_coord_to_contents.insert(file_coord, contents);
    }
  }

  pub fn put(&mut self, file_coord: &'a FileCoordinate<'a>, contents: Contents) {
    assert!(
      !self.file_coord_to_contents.contains_key(&file_coord),
      "FileCoordinateMap::put - file coordinate already exists"
    );

    self
      .file_coord_to_contents
      .insert(file_coord, contents.clone());

    let package_coord = file_coord.package_coord;
    let file_coords = self
      .package_coord_to_file_coords
      .entry(package_coord)
      .or_insert_with(Vec::new);
    file_coords.push(file_coord);
  }

  pub fn map<T, F>(&self, func: F) -> FileCoordinateMap<'a, T>
  where
    F: Fn(&'a FileCoordinate<'a>, &Contents) -> T,
    T: Clone,
  {
    let mut result_file_coord_to_contents: HashMap<&'a FileCoordinate<'a>, T> = HashMap::new();
    for (file_coord, contents) in &self.file_coord_to_contents {
      result_file_coord_to_contents.insert(file_coord, func(file_coord, contents));
    }
    FileCoordinateMap {
      package_coord_to_file_coords: self.package_coord_to_file_coords.clone(),
      file_coord_to_contents: result_file_coord_to_contents,
    }
  }

  pub fn flat_map<T, F>(&self, func: F) -> Vec<T>
  where
    F: Fn(&'a FileCoordinate<'a>, &Contents) -> T,
  {
    self
      .file_coord_to_contents
      .iter()
      .map(|(file_coord, contents)| func(file_coord, contents))
      .collect()
  }

  pub fn expect_one(&self) -> &Contents {
    assert!(
      self.file_coord_to_contents.len() == 1,
      "FileCoordinateMap::expect_one - expected exactly one entry"
    );
    self.file_coord_to_contents.values().next().unwrap()
  }

  pub fn resolve(
    &self,
    package_coord: &'a PackageCoordinate<'a>,
  ) -> Option<HashMap<String, Contents>> {
    self
      .package_coord_to_file_coords
      .get(package_coord)
      .map(|file_coords| {
        file_coords
          .iter()
          .map(|file_coord| {
            let contents = self
              .file_coord_to_contents
              .get(file_coord)
              .expect("FileCoordinateMap::resolve - file coord not found in contents");
            (file_coord.filepath.as_str().to_string(), contents.clone())
          })
          .collect()
      })
  }

}
pub fn compose_resolvers<'a, Contents>(
  resolver_a: impl Fn(&'a PackageCoordinate<'a>) -> Option<HashMap<String, Contents>>,
  resolver_b: impl Fn(&'a PackageCoordinate<'a>) -> HashMap<String, Contents>,
  package_coord: &'a PackageCoordinate<'a>,
) -> HashMap<String, Contents> {
  match resolver_a(package_coord) {
    Some(result) => result,
    None => resolver_b(package_coord),
  }
}

pub fn compose_map_and_resolver<'a, Contents>(
  files: &FileCoordinateMap<'a, Contents>,
  then_resolver: impl Fn(&'a PackageCoordinate<'a>) -> HashMap<String, Contents>,
  package_coord: &'a PackageCoordinate<'a>,
) -> HashMap<String, Contents>
where
  Contents: Clone,
{
  match files.resolve(package_coord) {
    Some(filename_to_contents) => filename_to_contents,
    None => then_resolver(package_coord),
  }
}

pub trait IPackageResolver<'a, T> {

  fn resolve(&self, package_coord: &'a PackageCoordinate<'a>) -> Option<T>;

  fn or<F>(self, fallback: F) -> OrResolver<Self, F>
  where
    Self: Sized,
    F: IPackageResolver<'a, T>,
  {
    OrResolver {
      primary: self,
      fallback,
    }
  }

  fn inner_or(
    &self,
    fallback: &impl IPackageResolver<'a, T>,
    package_coord: &'a PackageCoordinate<'a>,
  ) -> Option<T>
  where
    Self: Sized,
  {
    match self.resolve(package_coord) {
      Some(x) => Some(x),
      None => fallback.resolve(package_coord),
    }
  }

}

impl<'a, Contents: Clone> IPackageResolver<'a, HashMap<String, Contents>>
  for FileCoordinateMap<'a, Contents>
{
  fn resolve(&self, package_coord: &'a PackageCoordinate<'a>) -> Option<HashMap<String, Contents>> {
    FileCoordinateMap::resolve(self, package_coord)
  }
}
#[derive(Clone, Debug)]
pub struct PackageCoordinateMap<'a, Contents> {
  pub package_coord_to_contents: IndexMap<&'a PackageCoordinate<'a>, Contents>,
}
impl<'a, Contents> PackageCoordinateMap<'a, Contents> {

  pub fn new() -> Self {
    PackageCoordinateMap {
      package_coord_to_contents: IndexMap::new(),
    }
  }

  pub fn put(&mut self, package_coord: &'a PackageCoordinate<'a>, contents: Contents) {
    self.package_coord_to_contents.insert(package_coord, contents);
  }

  pub fn get(&self, package_coord: &'a PackageCoordinate<'a>) -> Option<&Contents> {
    self.package_coord_to_contents.get(package_coord)
  }

  pub fn expect_one(&self) -> &Contents {
    assert!(
      self.package_coord_to_contents.len() == 1,
      "PackageCoordinateMap::expect_one - expected exactly one entry"
    );
    self.package_coord_to_contents.values().next().unwrap()
  }

  pub fn map<T, F>(&self, func: F) -> PackageCoordinateMap<'a, T>
  where
    F: Fn(&'a PackageCoordinate<'a>, &Contents) -> T,
    T: Clone,
  {
    let mut result = PackageCoordinateMap::new();
    for (package_coord, contents) in &self.package_coord_to_contents {
      result.put(package_coord, func(package_coord, contents));
    }
    result
  }

  pub fn flat_map<T, F>(&self, func: F) -> Vec<T>
  where
    F: Fn(&'a PackageCoordinate<'a>, &Contents) -> T,
  {
    self
      .package_coord_to_contents
      .iter()
      .map(|(package_coord, contents)| func(package_coord, contents))
      .collect()
  }

}
