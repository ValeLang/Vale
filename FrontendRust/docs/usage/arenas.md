# Using Arenas

## Two-Phase Lifecycle

Every arena-allocated struct follows: **build mutable on heap, then freeze into arena.**

Phase 1 (build): Use `Vec`, `HashMap`, `IndexMap` freely. Mutate, push, filter.

Phase 2 (freeze): Convert to arena form and allocate. Never mutate after.

## Conversion Table

| Phase 1 (mutable) | Phase 2 (arena) | How |
|---|---|---|
| `Vec<T>` | `&'s [T]` | `alloc_slice_from_vec(arena, vec)` |
| `Vec<&'s T>` | `&'s [&'s T]` | `alloc_slice_from_vec_of_refs(arena, vec)` |
| `HashMap<K, V>` | `ArenaIndexMap<'s, K, V>` | `ArenaIndexMap::from_iter_in(map.into_iter(), arena)` |
| `String` | `StrI<'x>` | `parse_arena.intern_str(s)` or `scout_arena.intern_str(s)` |
| `LocationInDenizenBuilder` | `LocationInDenizen<'x>` | `builder.consume_in(arena)` |
| `T` (single value) | `&'s T` | `arena.alloc(value)` |

## Re-Interning at Pass Boundaries

When the postparser needs data from the parser arena, re-intern it:

```rust
let s_str: StrI<'s> = scout_arena.intern_str(p_str.as_str());
let s_pkg: &'s PackageCoordinate<'s> = scout_arena.intern_package_coord(...);
let s_file: &'s FileCoordinate<'s> = scout_arena.intern_file_coord(...);
```

## The `'x` Generic Lifetime

Some types live in multiple arenas. `LocationInDenizen<'x>` holds `path: &'x [i32]` — the `'x` unifies with whichever arena owns it. Build with `LocationInDenizenBuilder`, freeze with `consume_in(arena)`.

## No Malloc in Arena Structs

Arena-allocated structs must not contain `Vec`, `HashMap`, or `String`. Bumpalo doesn't run destructors, so these would leak. Use arena slices and `ArenaIndexMap` instead. See shield AASSNCMCX.

## No Clone on Arena Types

Arena-allocated output types must not `#[derive(Clone)]`. They are shared by reference (`&'p`, `&'s`), never duplicated. See shield ATDCX.

This applies to all structs/enums that get `arena.alloc()`'d: postparsing AST nodes (`StructS`, `FunctionS`, etc.), their inner types (attribute/body/member variants), parser AST nodes, and higher typing nodes.

**Exempt:** Copy types (interned handles like `StrI`, `RangeS`) and value types used as HashMap keys (`IRuneS`, `INameS`, `RuneUsage`).