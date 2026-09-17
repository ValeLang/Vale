
//! A deterministic, insertion-ordered hash map with arena-allocated backing storage.
//!
//! `ArenaIndexMap` combines a `hashbrown::HashMap` (for O(1) key lookup) with a
//! `bumpalo::collections::Vec` (for insertion-ordered storage). Both structures
//! allocate their backing memory from the same `bumpalo::Bump` arena, so all
//! memory is freed in one shot when the arena is dropped.
//!
//! Iteration always follows insertion order and is fully deterministic across
//! runs, platforms, and hash seeds — the hash table is never iterated directly.
//!
//! # Cargo.toml dependencies
//!
//! ```toml
//! [dependencies]
//! bumpalo = { version = "3", features = ["collections", "allocator-api2"] }
//! hashbrown = "0.16"
//! rustc-hash = "2"
//! ```

use bumpalo::collections::Vec as BumpVec;
use bumpalo::Bump;
use hashbrown::HashMap;
use rustc_hash::FxBuildHasher;
use std::fmt;

// ---------------------------------------------------------------------------
// Core struct
// ---------------------------------------------------------------------------

/// A deterministic, insertion-ordered map backed by a bump arena.
///
/// - **Lookup**: O(1) average via hash table.
/// - **Insert**: O(1) amortized (append to vec + hash insert).
/// - **Iteration**: Insertion order, always deterministic.
/// - **Memory**: Both the hash table buckets and entry vec live in the arena.
pub struct ArenaIndexMap<'bump, K, V> {
  /// Maps keys → indices into `entries`.
  indices: HashMap<K, usize, FxBuildHasher, &'bump Bump>,
  /// Insertion-ordered entries.
  entries: BumpVec<'bump, (K, V)>,
}

// ---------------------------------------------------------------------------
// Construction
// ---------------------------------------------------------------------------

impl<'bump, K, V> ArenaIndexMap<'bump, K, V>
where
    K: Hash + Eq + Clone,
{
  /// Creates an empty map allocating from `bump`.
  pub fn new_in(bump: &'bump Bump) -> Self {
    Self {
      indices: HashMap::with_hasher_in(FxBuildHasher, bump),
      entries: BumpVec::new_in(bump),
    }
  }

  /// Creates an empty map with preallocated capacity.
  pub fn with_capacity_in(capacity: usize, bump: &'bump Bump) -> Self {
    Self {
      indices: HashMap::with_capacity_and_hasher_in(capacity, FxBuildHasher, bump),
      entries: BumpVec::with_capacity_in(capacity, bump),
    }
  }
// ---------------------------------------------------------------------------
// Mutation
// ---------------------------------------------------------------------------
  /// Inserts a key-value pair. If the key already exists, the value is
  /// overwritten and the old value is returned. Insertion order of the
  /// key is preserved (the key stays at its original position).
  pub fn insert(&mut self, key: K, value: V) -> Option<V> {
    match self.indices.get(&key) {
      Some(&idx) => {
        let old = replace(&mut self.entries[idx].1, value);
        Some(old)
      }
      None => {
        let idx = self.entries.len();
        self.indices.insert(key.clone(), idx);
        self.entries.push((key, value));
        None
      }
    }
  }

  /// Inserts a key-value pair only if the key is absent.
  /// Returns a mutable reference to the (possibly existing) value.
  pub fn entry_or_insert(&mut self, key: K, default: V) -> &mut V {
    if let Some(&idx) = self.indices.get(&key) {
      &mut self.entries[idx].1
    } else {
      let idx = self.entries.len();
      self.indices.insert(key.clone(), idx);
      self.entries.push((key, default));
      &mut self.entries[idx].1
    }
  }

  /// Extends the map with entries from an iterator.
  pub fn extend<I: IntoIterator<Item = (K, V)>>(&mut self, iter: I) {
    for (k, v) in iter {
      self.insert(k, v);
    }
  }

  /// Retains only entries for which the predicate returns true.
  /// **Note**: This is O(n) and rebuilds the index. Insertion order of
  /// retained entries is preserved.
  pub fn retain<F>(&mut self, mut f: F)
  where
      F: FnMut(&K, &V) -> bool,
  {
    let bump = self.entries.bump();
    let old_entries = replace(&mut self.entries, BumpVec::new_in(bump));
    self.indices.clear();

    for (k, v) in old_entries {
      if f(&k, &v) {
        let idx = self.entries.len();
        self.indices.insert(k.clone(), idx);
        self.entries.push((k, v));
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Read access
// ---------------------------------------------------------------------------

impl<'bump, K, V> ArenaIndexMap<'bump, K, V>
where
    K: Hash + Eq,
{
  /// Returns a reference to the value associated with the key.
  pub fn get(&self, key: &K) -> Option<&V> {
    self.indices.get(key).map(|&idx| &self.entries[idx].1)
  }

  /// Returns a mutable reference to the value associated with the key.
  pub fn get_mut(&mut self, key: &K) -> Option<&mut V> {
    self.indices
        .get(key)
        .copied()
        .map(move |idx| &mut self.entries[idx].1)
  }

  /// Returns true if the map contains the key.
  pub fn contains_key(&self, key: &K) -> bool {
    self.indices.contains_key(key)
  }

  /// Returns the number of entries.
  pub fn len(&self) -> usize {
    self.entries.len()
  }

  /// Returns true if the map is empty.
  pub fn is_empty(&self) -> bool {
    self.entries.is_empty()
  }

  /// Returns the entry at the given insertion-order index.
  pub fn get_index(&self, index: usize) -> Option<(&K, &V)> {
    self.entries.get(index).map(|(k, v)| (k, v))
  }

  /// Returns the insertion-order index for a key.
  pub fn get_index_of(&self, key: &K) -> Option<usize> {
    self.indices.get(key).copied()
  }
}

// ---------------------------------------------------------------------------
// Freezing (convert to arena slice)
// ---------------------------------------------------------------------------

impl<'bump, K, V> ArenaIndexMap<'bump, K, V> {
  /// Consumes the map and returns a bump-allocated slice of entries in
  /// insertion order. The hash table's memory becomes dead space in the
  /// arena (freed when the arena drops).
  pub fn into_bump_slice(self) -> &'bump [(K, V)] {
    self.entries.into_bump_slice()
  }

  /// Consumes the map and returns a bump-allocated slice of entries
  /// sorted by key. Useful when you want deterministic sorted-order
  /// iteration and O(log n) binary search on the frozen slice.
  pub fn into_sorted_bump_slice(mut self) -> &'bump [(K, V)]
  where
      K: Ord,
  {
    self.entries
        .sort_unstable_by(|a, b| a.0.cmp(&b.0));
    self.entries.into_bump_slice()
  }
}

// ---------------------------------------------------------------------------
// Iterators
// ---------------------------------------------------------------------------

/// An iterator over references to key-value pairs in insertion order.
pub struct Iter<'a, K, V> {
  inner: SliceIter<'a, (K, V)>,
}

impl<'a, K, V> Iterator for Iter<'a, K, V> {
  type Item = (&'a K, &'a V);

  fn next(&mut self) -> Option<Self::Item> {
    self.inner.next().map(|(k, v)| (k, v))
  }

  fn size_hint(&self) -> (usize, Option<usize>) {
    self.inner.size_hint()
  }
}

impl<K, V> ExactSizeIterator for Iter<'_, K, V> {}
impl<K, V> DoubleEndedIterator for Iter<'_, K, V> {
  fn next_back(&mut self) -> Option<Self::Item> {
    self.inner.next_back().map(|(k, v)| (k, v))
  }
}

/// An iterator over mutable references to key-value pairs in insertion order.
pub struct IterMut<'a, K, V> {
  inner: SliceIterMut<'a, (K, V)>,
}

impl<'a, K, V> Iterator for IterMut<'a, K, V> {
  type Item = (&'a K, &'a mut V);

  fn next(&mut self) -> Option<Self::Item> {
    self.inner.next().map(|(k, v)| (&*k, v))
  }

  fn size_hint(&self) -> (usize, Option<usize>) {
    self.inner.size_hint()
  }
}

impl<K, V> ExactSizeIterator for IterMut<'_, K, V> {}

/// An iterator over keys in insertion order.
pub struct Keys<'a, K, V> {
  inner: Iter<'a, K, V>,
}

impl<'a, K, V> Iterator for Keys<'a, K, V> {
  type Item = &'a K;

  fn next(&mut self) -> Option<Self::Item> {
    self.inner.next().map(|(k, _)| k)
  }

  fn size_hint(&self) -> (usize, Option<usize>) {
    self.inner.size_hint()
  }
}

impl<K, V> ExactSizeIterator for Keys<'_, K, V> {}

/// An iterator over values in insertion order.
pub struct Values<'a, K, V> {
  inner: Iter<'a, K, V>,
}

impl<'a, K, V> Iterator for Values<'a, K, V> {
  type Item = &'a V;

  fn next(&mut self) -> Option<Self::Item> {
    self.inner.next().map(|(_, v)| v)
  }

  fn size_hint(&self) -> (usize, Option<usize>) {
    self.inner.size_hint()
  }
}

impl<K, V> ExactSizeIterator for Values<'_, K, V> {}

/// An owning iterator (consumes the vec, yields owned pairs).
pub struct IntoIter<'bump, K, V> {
  inner: bumpalo::collections::vec::IntoIter<'bump, (K, V)>,
}

impl<'bump, K, V> Iterator for IntoIter<'bump, K, V> {
  type Item = (K, V);

  fn next(&mut self) -> Option<Self::Item> {
    self.inner.next()
  }

  fn size_hint(&self) -> (usize, Option<usize>) {
    self.inner.size_hint()
  }
}

// --- Trait impls on ArenaIndexMap to vend iterators ---

impl<'bump, K, V> ArenaIndexMap<'bump, K, V>
where
    K: Hash + Eq,
{
  /// Iterates over `(&K, &V)` in insertion order.
  pub fn iter(&self) -> Iter<'_, K, V> {
    Iter {
      inner: self.entries.iter(),
    }
  }

  /// Iterates over `(&K, &mut V)` in insertion order.
  pub fn iter_mut(&mut self) -> IterMut<'_, K, V> {
    IterMut {
      inner: self.entries.iter_mut(),
    }
  }

  /// Iterates over keys in insertion order.
  pub fn keys(&self) -> Keys<'_, K, V> {
    Keys { inner: self.iter() }
  }

  /// Iterates over values in insertion order.
  pub fn values(&self) -> Values<'_, K, V> {
    Values { inner: self.iter() }
  }
}

impl<'bump, K, V> IntoIterator for ArenaIndexMap<'bump, K, V>
where
    K: Hash + Eq,
{
  type Item = (K, V);
  type IntoIter = IntoIter<'bump, K, V>;

  fn into_iter(self) -> Self::IntoIter {
    IntoIter {
      inner: self.entries.into_iter(),
    }
  }
}

impl<'a, 'bump, K, V> IntoIterator for &'a ArenaIndexMap<'bump, K, V>
where
    K: Hash + Eq,
{
  type Item = (&'a K, &'a V);
  type IntoIter = Iter<'a, K, V>;

  fn into_iter(self) -> Self::IntoIter {
    self.iter()
  }
}

// ---------------------------------------------------------------------------
// Trait impls
// ---------------------------------------------------------------------------

use std::hash::Hash;
use std::mem::replace;
use std::slice::Iter as SliceIter;
use std::slice::IterMut as SliceIterMut;

impl<K: fmt::Debug, V: fmt::Debug> fmt::Debug for ArenaIndexMap<'_, K, V>
where
    K: Hash + Eq,
{
  fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
    f.debug_map()
        .entries(self.iter().map(|(k, v)| (k, v)))
        .finish()
  }
}

impl<'bump, K, V> PartialEq for ArenaIndexMap<'bump, K, V>
where
    K: Hash + Eq + PartialEq,
    V: PartialEq,
{
  fn eq(&self, other: &Self) -> bool {
    if self.len() != other.len() {
      return false;
    }
    // Order-sensitive equality: same entries in same insertion order.
    self.entries
        .iter()
        .zip(other.entries.iter())
        .all(|(a, b)| a.0 == b.0 && a.1 == b.1)
  }
}

impl<'bump, K, V> Eq for ArenaIndexMap<'bump, K, V>
where
    K: Hash + Eq,
    V: Eq,
{
}

// ---------------------------------------------------------------------------
// FromIterator (requires a bump reference threaded through)
// ---------------------------------------------------------------------------

impl<'bump, K, V> ArenaIndexMap<'bump, K, V>
where
    K: Hash + Eq + Clone,
{
  /// Constructs a map from an iterator, allocating in the given bump.
  pub fn from_iter_in<I: IntoIterator<Item = (K, V)>>(iter: I, bump: &'bump Bump) -> Self {
    let iter = iter.into_iter();
    let (lower, _) = iter.size_hint();
    let mut map = Self::with_capacity_in(lower, bump);
    for (k, v) in iter {
      map.insert(k, v);
    }
    map
  }
}

// ===========================================================================
// Tests
// ===========================================================================

#[cfg(test)]
mod tests {
  use super::*;

  // -- Basic operations --------------------------------------------------

  #[test]
  fn test_empty_map() {
    let bump = Bump::new();
    let map: ArenaIndexMap<'_, String, i32> = ArenaIndexMap::new_in(&bump);
    assert!(map.is_empty());
    assert_eq!(map.len(), 0);
    assert_eq!(map.get(&"x".to_string()), None);
    assert!(!map.contains_key(&"x".to_string()));
  }

  #[test]
  fn test_insert_and_get() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    assert_eq!(map.insert("a", 1), None);
    assert_eq!(map.insert("b", 2), None);
    assert_eq!(map.insert("c", 3), None);

    assert_eq!(map.get(&"a"), Some(&1));
    assert_eq!(map.get(&"b"), Some(&2));
    assert_eq!(map.get(&"c"), Some(&3));
    assert_eq!(map.get(&"d"), None);
    assert_eq!(map.len(), 3);
  }

  #[test]
  fn test_insert_overwrite_returns_old_value() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    assert_eq!(map.insert("key", 10), None);
    assert_eq!(map.insert("key", 20), Some(10));
    assert_eq!(map.insert("key", 30), Some(20));
    assert_eq!(map.get(&"key"), Some(&30));
    // Overwrite should NOT change length.
    assert_eq!(map.len(), 1);
  }

  #[test]
  fn test_contains_key() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert(42, "hello");
    assert!(map.contains_key(&42));
    assert!(!map.contains_key(&99));
  }

  #[test]
  fn test_get_mut() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert("x", 100);
    if let Some(v) = map.get_mut(&"x") {
      *v += 50;
    }
    assert_eq!(map.get(&"x"), Some(&150));
  }

  // -- Insertion order ---------------------------------------------------

  #[test]
  fn test_insertion_order_preserved() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    let items = vec![("z", 1), ("a", 2), ("m", 3), ("b", 4), ("y", 5)];
    for (k, v) in &items {
      map.insert(*k, *v);
    }
    let collected: Vec<_> = map.iter().map(|(&k, &v)| (k, v)).collect();
    assert_eq!(collected, items);
  }

  #[test]
  fn test_overwrite_preserves_original_position() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert("first", 1);
    map.insert("second", 2);
    map.insert("third", 3);
    // Overwrite "second" — it should stay at index 1.
    map.insert("second", 99);

    let keys: Vec<_> = map.keys().copied().collect();
    assert_eq!(keys, vec!["first", "second", "third"]);
    assert_eq!(map.get(&"second"), Some(&99));
  }

  #[test]
  fn test_deterministic_across_multiple_builds() {
    // Build the same map 10 times — iteration order must be identical.
    let orders: Vec<Vec<(&str, i32)>> = (0..10)
        .map(|_| {
          let bump = Bump::new();
          let mut map = ArenaIndexMap::new_in(&bump);
          for (i, key) in ["delta", "alpha", "charlie", "bravo"].iter().enumerate()
          {
            map.insert(*key, i as i32);
          }
          map.iter().map(|(&k, &v)| (k, v)).collect()
        })
        .collect();

    for order in &orders[1..] {
      assert_eq!(&orders[0], order);
    }
  }

  // -- Indexed access ----------------------------------------------------

  #[test]
  fn test_get_index() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert("a", 10);
    map.insert("b", 20);
    map.insert("c", 30);

    assert_eq!(map.get_index(0), Some((&"a", &10)));
    assert_eq!(map.get_index(1), Some((&"b", &20)));
    assert_eq!(map.get_index(2), Some((&"c", &30)));
    assert_eq!(map.get_index(3), None);
  }

  #[test]
  fn test_get_index_of() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert("x", 1);
    map.insert("y", 2);
    map.insert("z", 3);

    assert_eq!(map.get_index_of(&"x"), Some(0));
    assert_eq!(map.get_index_of(&"z"), Some(2));
    assert_eq!(map.get_index_of(&"w"), None);
  }

  // -- Iterators ---------------------------------------------------------

  #[test]
  fn test_keys_iterator() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert(1, "a");
    map.insert(2, "b");
    map.insert(3, "c");

    let keys: Vec<_> = map.keys().copied().collect();
    assert_eq!(keys, vec![1, 2, 3]);
  }

  #[test]
  fn test_values_iterator() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert("a", 10);
    map.insert("b", 20);

    let values: Vec<_> = map.values().copied().collect();
    assert_eq!(values, vec![10, 20]);
  }

  #[test]
  fn test_iter_mut() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert("a", 1);
    map.insert("b", 2);
    map.insert("c", 3);

    for (_, v) in map.iter_mut() {
      *v *= 10;
    }

    assert_eq!(map.get(&"a"), Some(&10));
    assert_eq!(map.get(&"b"), Some(&20));
    assert_eq!(map.get(&"c"), Some(&30));
  }

  #[test]
  fn test_into_iter() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert("x", 1);
    map.insert("y", 2);

    let collected: Vec<_> = map.into_iter().collect();
    assert_eq!(collected, vec![("x", 1), ("y", 2)]);
  }

  #[test]
  fn test_double_ended_iter() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert("a", 1);
    map.insert("b", 2);
    map.insert("c", 3);

    let reversed: Vec<_> = map.iter().rev().map(|(&k, &v)| (k, v)).collect();
    assert_eq!(reversed, vec![("c", 3), ("b", 2), ("a", 1)]);
  }

  #[test]
  fn test_exact_size_iterator() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert("a", 1);
    map.insert("b", 2);
    map.insert("c", 3);

    let iter = map.iter();
    assert_eq!(iter.len(), 3);
  }

  #[test]
  fn test_for_loop_ref() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert("a", 1);
    map.insert("b", 2);

    let mut collected = Vec::new();
    for (k, v) in &map {
      collected.push((*k, *v));
    }
    assert_eq!(collected, vec![("a", 1), ("b", 2)]);
  }

  // -- entry_or_insert ---------------------------------------------------

  #[test]
  fn test_entry_or_insert_absent() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    let v = map.entry_or_insert("key", 42);
    assert_eq!(*v, 42);
    assert_eq!(map.len(), 1);
  }

  #[test]
  fn test_entry_or_insert_existing() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert("key", 10);
    let v = map.entry_or_insert("key", 99);
    assert_eq!(*v, 10); // existing value, not default
    assert_eq!(map.len(), 1);
  }

  #[test]
  fn test_entry_or_insert_mutate() {
    let bump = Bump::new();
    let mut map: ArenaIndexMap<'_, &str, Vec<i32>> = ArenaIndexMap::new_in(&bump);
    map.entry_or_insert("nums", Vec::new()).push(1);
    map.entry_or_insert("nums", Vec::new()).push(2);
    map.entry_or_insert("nums", Vec::new()).push(3);

    assert_eq!(map.get(&"nums"), Some(&vec![1, 2, 3]));
    assert_eq!(map.len(), 1);
  }

  // -- extend ------------------------------------------------------------

  #[test]
  fn test_extend() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert("a", 1);
    map.extend(vec![("b", 2), ("c", 3), ("a", 99)]);

    assert_eq!(map.len(), 3);
    assert_eq!(map.get(&"a"), Some(&99)); // overwritten
    let keys: Vec<_> = map.keys().copied().collect();
    assert_eq!(keys, vec!["a", "b", "c"]); // "a" stays at position 0
  }

  // -- retain ------------------------------------------------------------

  #[test]
  fn test_retain() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert("a", 1);
    map.insert("b", 2);
    map.insert("c", 3);
    map.insert("d", 4);

    map.retain(|_, v| *v % 2 == 0);

    assert_eq!(map.len(), 2);
    let collected: Vec<_> = map.iter().map(|(&k, &v)| (k, v)).collect();
    assert_eq!(collected, vec![("b", 2), ("d", 4)]);
    // Removed keys are gone from the index too.
    assert!(!map.contains_key(&"a"));
    assert!(!map.contains_key(&"c"));
  }

  #[test]
  fn test_retain_preserves_insertion_order() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    for i in 0..10 {
      map.insert(i, i * 10);
    }
    map.retain(|k, _| k % 3 == 0); // keep 0, 3, 6, 9

    let keys: Vec<_> = map.keys().copied().collect();
    assert_eq!(keys, vec![0, 3, 6, 9]);
  }

  // -- Freezing ----------------------------------------------------------

  #[test]
  fn test_into_bump_slice_insertion_order() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert("z", 1);
    map.insert("a", 2);
    map.insert("m", 3);

    let slice = map.into_bump_slice();
    assert_eq!(slice, &[("z", 1), ("a", 2), ("m", 3)]);
  }

  #[test]
  fn test_into_sorted_bump_slice() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert("z", 1);
    map.insert("a", 2);
    map.insert("m", 3);

    let slice = map.into_sorted_bump_slice();
    assert_eq!(slice, &[("a", 2), ("m", 3), ("z", 1)]);
  }

  #[test]
  fn test_frozen_slice_binary_search() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    for i in (0..50).rev() {
      map.insert(i, i * 100);
    }
    let slice = map.into_sorted_bump_slice();

    // Binary search lookup on the frozen slice.
    for i in 0..50 {
      let idx = slice.binary_search_by_key(&i, |(k, _)| *k).unwrap();
      assert_eq!(slice[idx].1, i * 100);
    }

    // Missing key.
    assert!(slice.binary_search_by_key(&999, |(k, _)| *k).is_err());
  }

  // -- from_iter_in ------------------------------------------------------

  #[test]
  fn test_from_iter_in() {
    let bump = Bump::new();
    let items = vec![("x", 10), ("y", 20), ("z", 30)];
    let map = ArenaIndexMap::from_iter_in(items, &bump);

    assert_eq!(map.len(), 3);
    assert_eq!(map.get(&"y"), Some(&20));
    let keys: Vec<_> = map.keys().copied().collect();
    assert_eq!(keys, vec!["x", "y", "z"]);
  }

  #[test]
  fn test_from_iter_in_with_duplicates() {
    let bump = Bump::new();
    let items = vec![("a", 1), ("b", 2), ("a", 99)];
    let map = ArenaIndexMap::from_iter_in(items, &bump);

    assert_eq!(map.len(), 2);
    assert_eq!(map.get(&"a"), Some(&99)); // last write wins
    let keys: Vec<_> = map.keys().copied().collect();
    assert_eq!(keys, vec!["a", "b"]); // "a" at original position
  }

  // -- Debug + Eq --------------------------------------------------------

  #[test]
  fn test_debug_format() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    map.insert("hello", 1);
    map.insert("world", 2);

    let dbg = format!("{:?}", map);
    assert!(dbg.contains("hello"));
    assert!(dbg.contains("world"));
  }

  #[test]
  fn test_equality() {
    let bump1 = Bump::new();
    let bump2 = Bump::new();

    let mut a = ArenaIndexMap::new_in(&bump1);
    let mut b = ArenaIndexMap::new_in(&bump2);

    a.insert("x", 1);
    a.insert("y", 2);
    b.insert("x", 1);
    b.insert("y", 2);

    assert_eq!(a, b);
  }

  #[test]
  fn test_inequality_different_order() {
    let bump1 = Bump::new();
    let bump2 = Bump::new();

    let mut a = ArenaIndexMap::new_in(&bump1);
    let mut b = ArenaIndexMap::new_in(&bump2);

    a.insert("x", 1);
    a.insert("y", 2);
    b.insert("y", 2);
    b.insert("x", 1);

    // Same entries but different insertion order → not equal.
    assert_ne!(a, b);
  }

  #[test]
  fn test_inequality_different_values() {
    let bump1 = Bump::new();
    let bump2 = Bump::new();

    let mut a = ArenaIndexMap::new_in(&bump1);
    let mut b = ArenaIndexMap::new_in(&bump2);

    a.insert("x", 1);
    b.insert("x", 999);

    assert_ne!(a, b);
  }

  // -- With capacity -----------------------------------------------------

  #[test]
  fn test_with_capacity() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::with_capacity_in(100, &bump);
    for i in 0..100 {
      map.insert(i, i);
    }
    assert_eq!(map.len(), 100);
    assert_eq!(map.get(&50), Some(&50));
  }

  // -- Larger map stress test --------------------------------------------

  #[test]
  fn test_200_entries() {
    let bump = Bump::new();
    let mut map = ArenaIndexMap::new_in(&bump);
    for i in 0..200 {
      map.insert(i, format!("val_{}", i));
    }
    assert_eq!(map.len(), 200);
    assert_eq!(map.get(&0), Some(&"val_0".to_string()));
    assert_eq!(map.get(&199), Some(&"val_199".to_string()));
    assert_eq!(map.get(&200), None);

    // Verify insertion order.
    for (idx, (k, _)) in map.iter().enumerate() {
      assert_eq!(*k, idx);
    }
  }
}