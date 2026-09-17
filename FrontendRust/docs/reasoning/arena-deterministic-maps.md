# Arena-Allocated Deterministic Map: Problem Statement

## Background

We're migrating a Scala compiler frontend to Rust. The Rust version uses **arena allocation** (`bumpalo::Bump`) for output AST nodes — structs like `StructS`, `FunctionS`, `StructA`, `FunctionA` are allocated into bump arenas, and their fields hold references (`&'s [T]`) into those arenas rather than owning heap-allocated `Vec`s.

We're partway through converting `Vec` fields to arena-allocated slices (`&'s [T]`), and this works well — build a Vec, then call `arena.alloc_slice_fill_iter(vec.into_iter())` to get a `&'s [T]`.

The remaining problem is **HashMap fields**. Many arena-allocated structs contain `HashMap<IRuneS<'a>, ITemplataType>` fields (mapping type-variable runes to their inferred types). These HashMaps have their backing storage on the heap via malloc, which defeats the purpose of arena allocation.

## Requirements

We need a map data structure that satisfies **both** of these properties:

1. **Arena-allocated backing storage**: The map's internal memory (buckets, entries, etc.) must be allocated from a `bumpalo::Bump` arena, not from the global heap allocator. This is so all memory for a compilation pass can be freed in one shot by dropping the arena.

2. **Deterministic iteration order**: Iterating the map (via `.iter()`, `.keys()`, `.values()`, `.into_iter()`) must produce entries in a deterministic order — either insertion order or sorted order. The iteration order must be the same across runs given the same inputs, regardless of platform, pointer values, or hash seed.

## Why Both Matter

- **Arena allocation matters** because the compiler processes many compilation units, and arena-based bulk deallocation is faster and simpler than tracking individual allocations. Structs allocated in the arena shouldn't point to heap-allocated collections that outlive them or fragment memory.

- **Deterministic iteration matters** because the Scala original iterates over maps in several places (filtering rune-to-type maps, splitting header vs member runes, flattening package contents). While most of these operations produce Maps or Sets where iteration order doesn't affect correctness, we want to guarantee determinism across the entire compiler to avoid hard-to-debug nondeterministic compilation failures. We *hate* nondeterminism.

## What Exists Today

| Crate | Arena support | Deterministic iteration | Notes |
|-------|-------------|------------------------|-------|
| `std::collections::HashMap` | No | No | Rust's stdlib HashMap (hashbrown internally) |
| `hashbrown::HashMap<K,V,S,A>` | Yes (allocator-api2 + bumpalo) | No | SwissTable, supports custom allocators on stable Rust |
| `indexmap::IndexMap` | No | Yes (insertion order) | No custom allocator parameter |
| `std::collections::BTreeMap` | No | Yes (sorted order) | No custom allocator parameter |
| Sorted `&'s [(K,V)]` slice | Yes (trivially) | Yes (sorted) | O(log n) lookup via binary search; immutable after construction |

None of the existing options satisfy both requirements simultaneously.

## Usage Pattern

The maps in question are built during a compiler pass and then stored in arena-allocated AST structs. The typical lifecycle is:

```
1. Create an empty map
2. Insert entries incrementally (10-50 entries typically)
3. Sometimes merge two maps (e.g., header + member runes)
4. Store the map in an arena-allocated struct
5. Read from the map via .get() and .contains_key() during later phases
6. Occasionally iterate the map (e.g., .filter(), .iter())
7. Never mutate the map after storing it in the struct
```

The key type (`IRuneS<'a>`) implements `Hash + Eq + Ord + Clone` and is a small enum (tagged pointer, ~16 bytes). The value type (`ITemplataType`) also implements `Hash + Eq + Clone`.

## Possible Approaches to Investigate

### A. Arena-backed IndexMap
Build an IndexMap-like structure (hash table + insertion-order entry vector) where both the hash buckets and the entry storage are allocated from a `bumpalo::Bump`. Could be implemented as a wrapper around hashbrown's `RawTable` (which supports custom allocators) with an arena-allocated entry vector for ordering.

### B. Sorted arena slices
Build the map as a `Vec<(K, V)>` during construction, then sort by key and allocate into the arena as `&'s [(K, V)]`. Lookup via binary search (`O(log n)`). Deterministic (sorted order). Very simple. Downside: O(log n) lookup instead of O(1), and immutable after construction (but our maps are never mutated after storing).

### C. Two-phase approach
During construction (mutable phase), use a regular `IndexMap` on the heap. When storing into the arena-allocated struct, convert to a sorted `&'s [(K, V)]` slice. This gives O(1) insertion during construction and O(log n) lookup during reads, with full arena allocation for the stored form.

### D. Patch indexmap to support allocators
IndexMap internally uses hashbrown. If indexmap exposed the allocator parameter from hashbrown, it would work with bumpalo directly. This would require upstream changes or a fork.

### E. Accept the tradeoff
Use `indexmap::IndexMap` (deterministic, heap-allocated) and accept that these particular fields use heap allocation. The maps are small (10-50 entries) and the heap cost is minimal compared to the large arena-allocated AST nodes they live in.

## Constraints

- Must work on **stable Rust** (no nightly-only features)
- Maps are small (10-50 entries typically, never more than ~200)
- Keys are cheap to compare and hash
- Maps are **write-once, read-many** — never mutated after construction
- The solution will be used in ~20 struct definitions across the compiler frontend
