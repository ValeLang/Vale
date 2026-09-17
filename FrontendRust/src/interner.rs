
use std::fmt::{Debug, Display, Formatter};
use std::hash::{Hash, Hasher};
use std::ops::Deref;
use std::fmt::Result;
use std::marker::PhantomData;
use std::slice::Iter;

/// Interned string: a by-value wrapper around arena-backed `&'a str`.
/// Never arena-allocated; just holds a reference to canonical storage.
#[derive(Copy, Clone, PartialEq, Eq, Hash)]
pub struct StrI<'a>(pub &'a str);

/// Placeholder for Scala's `Interner`. Not actually used for interning in
/// Rust (arenas replace it); only kept as a type alias so stale typing-pass
/// stubs that mention `&'ctx Interner<'s>` continue to compile.
pub struct Interner<'s>(pub PhantomData<&'s ()>);

impl<'a> StrI<'a> {
  pub fn as_str(&self) -> &'a str {
    self.0
  }
}

impl Deref for StrI<'_> {
  type Target = str;

  fn deref(&self) -> &Self::Target {
    self.0
  }
}

impl Debug for StrI<'_> {
  fn fmt(&self, f: &mut Formatter<'_>) -> Result {
    Debug::fmt(self.0, f)
  }
}

impl Display for StrI<'_> {
  fn fmt(&self, f: &mut Formatter<'_>) -> Result {
    Display::fmt(self.0, f)
  }
}

impl PartialEq<&str> for StrI<'_> {
  fn eq(&self, other: &&str) -> bool {
    self.0 == *other
  }
}

impl PartialEq<str> for StrI<'_> {
  fn eq(&self, other: &str) -> bool {
    self.0 == other
  }
}

#[derive(Copy, Clone)]
pub struct InternedSlice<'a, T: Copy> {
  slice: &'a [T],
}

impl<'a, T: Copy> InternedSlice<'a, T> {
  pub fn new(slice: &'a [T]) -> Self {
    InternedSlice { slice }
  }

  pub fn as_slice(&self) -> &'a [T] {
    self.slice
  }

  pub fn iter(&self) -> Iter<'a, T> {
    self.slice.iter()
  }

  pub fn is_empty(&self) -> bool {
    self.slice.is_empty()
  }

  pub fn len(&self) -> usize {
    self.slice.len()
  }
}

impl<'a, T: Copy> IntoIterator for &InternedSlice<'a, T> {
  type Item = &'a T;
  type IntoIter = Iter<'a, T>;

  fn into_iter(self) -> Self::IntoIter {
    self.slice.iter()
  }
}

impl<T: Copy + Debug> Debug for InternedSlice<'_, T> {
  fn fmt(&self, f: &mut Formatter<'_>) -> Result {
    Debug::fmt(self.slice, f)
  }
}

impl<T: Copy + PartialEq> PartialEq for InternedSlice<'_, T> {
  fn eq(&self, other: &Self) -> bool {
    self.slice == other.slice
  }
}

impl<T: Copy + Eq> Eq for InternedSlice<'_, T> {}

impl<T: Copy + Hash> Hash for InternedSlice<'_, T> {
  fn hash<H: Hasher>(&self, state: &mut H) {
    self.slice.hash(state);
  }
}
