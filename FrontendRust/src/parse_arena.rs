
// AFTERM: figure out how to deduplicate all the common code across these interners
// (ParseArena, ScoutArena, and future TypingArena all share string/coord interning)

use crate::interner::{InternedSlice, StrI};
use crate::utils::code_hierarchy::{FileCoordinate, PackageCoordinate};
use bumpalo::Bump;
use std::cell::RefCell;
use std::collections::HashMap;
use std::hash::Hash;
use std::hash::Hasher;
use std::ptr::eq;

/// Lookup key for file coordinates; uses String for filepath to allow lookup with arbitrary &str.
#[derive(Clone)]
struct FileCoordLookupKey<'p> {
  package_coord: &'p PackageCoordinate<'p>,
  filepath: String,
}

impl<'p> PartialEq for FileCoordLookupKey<'p> {
  fn eq(&self, other: &Self) -> bool {
    eq(self.package_coord, other.package_coord) && self.filepath == other.filepath
  }
}
impl<'p> Eq for FileCoordLookupKey<'p> {}

impl<'p> Hash for FileCoordLookupKey<'p> {
  fn hash<H: Hasher>(&self, state: &mut H) {
    (self.package_coord as *const PackageCoordinate<'_>).hash(state);
    self.filepath.hash(state);
  }
}

/// Arena + interning maps for the parsing pass.
/// Holds the `'p` Bump arena and deduplication maps for strings,
/// package coordinates, and file coordinates.
pub struct ParseArena<'p> {
  bump: &'p Bump,
  inner: RefCell<ParseArenaInner<'p>>,
}

struct ParseArenaInner<'p> {
  string_to_interned: HashMap<String, &'p str>,
  package_coord_to_ref: HashMap<PackageCoordinate<'p>, &'p PackageCoordinate<'p>>,
  file_coord_to_ref: HashMap<FileCoordLookupKey<'p>, &'p FileCoordinate<'p>>,
}

impl<'p> ParseArena<'p> {
  pub fn new(bump: &'p Bump) -> Self {
    ParseArena {
      bump,
      inner: RefCell::new(ParseArenaInner {
        // Pre-size for keywords (~130 entries) + headroom
        string_to_interned: HashMap::with_capacity(256),
        package_coord_to_ref: HashMap::new(),
        file_coord_to_ref: HashMap::new(),
      }),
    }
  }

  /// Allocate a value into the arena, returning a stable reference.
  pub fn alloc<T>(&self, val: T) -> &'p mut T {
    self.bump.alloc(val)
  }

  /// Allocate a slice copy into the arena.
  pub fn alloc_slice_copy<T: Copy>(&self, src: &[T]) -> &'p [T] {
    self.bump.alloc_slice_copy(src)
  }

  /// Allocate a slice from a Vec into the arena.
  pub fn alloc_slice_from_vec<T>(&self, vec: Vec<T>) -> &'p [T] {
    self.bump.alloc_slice_fill_iter(vec.into_iter())
  }

  /// Intern a string, returning a canonical StrI<'p>.
  pub fn intern_str(&self, s: &str) -> StrI<'p> {
    let mut inner = self.inner.borrow_mut();
    if let Some(&existing) = inner.string_to_interned.get(s) {
      return StrI(existing);
    }
    let arena_str = self.bump.alloc_str(s);
    inner.string_to_interned.insert(s.to_string(), arena_str);
    StrI(arena_str)
  }

  /// Intern a PackageCoordinate, returning a canonical &'p reference.
  pub fn intern_package_coordinate(
    &self,
    module: StrI<'p>,
    packages: &[StrI<'p>],
  ) -> &'p PackageCoordinate<'p> {
    let mut inner = self.inner.borrow_mut();
    let lookup_coord = PackageCoordinate {
      module,
      packages: InternedSlice::new(packages),
    };
    if let Some(existing) = inner.package_coord_to_ref.get(&lookup_coord) {
      return *existing;
    }
    let arena_packages = self.bump.alloc_slice_copy(packages);
    let coord = PackageCoordinate {
      module,
      packages: InternedSlice::new(arena_packages),
    };
    let new_ref: &'p PackageCoordinate<'p> = self.bump.alloc(coord.clone());
    inner.package_coord_to_ref.insert(coord, new_ref);
    new_ref
  }

  /// Intern a FileCoordinate, returning a canonical &'p reference.
  pub fn intern_file_coordinate(
    &self,
    package_coord: &'p PackageCoordinate<'p>,
    filepath: &str,
  ) -> &'p FileCoordinate<'p> {
    let mut inner = self.inner.borrow_mut();
    let lookup_key = FileCoordLookupKey {
      package_coord,
      filepath: filepath.to_string(),
    };
    if let Some(existing) = inner.file_coord_to_ref.get(&lookup_key) {
      return *existing;
    }
    let arena_filepath = self.bump.alloc_str(filepath);
    let coord = FileCoordinate {
      package_coord,
      filepath: StrI(arena_filepath),
    };
    let new_ref: &'p FileCoordinate<'p> = self.bump.alloc(coord.clone());
    let insert_key = FileCoordLookupKey {
      package_coord,
      filepath: filepath.to_string(),
    };
    inner.file_coord_to_ref.insert(insert_key, new_ref);
    new_ref
  }
}
