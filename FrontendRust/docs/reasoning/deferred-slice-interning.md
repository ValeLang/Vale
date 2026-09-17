# Reasoning: Deferred Slice Interning

When interning a type that contains a slice (e.g., `ImplicitRuneS` with `LocationInDenizen { path: &'s [i32] }`), the slice must not be arena-allocated before the intern map is checked. On a hit, the pre-allocated slice would be dead arena space. This matters most for the typing pass, where the same types are interned hundreds of times.

## Alternatives Considered

### 1. Pre-allocate and accept waste (original approach)

Arena-allocate the slice before calling `intern_rune`. On a hit, the slice stays in the arena unused.

- **Pro:** Simple, no additional types or lifetimes.
- **Con:** Wastes arena space proportional to intern hit rate. Acceptable for the scout pass (LocationInDenizen paths are unique), unacceptable for the typing pass (e.g., every use of `int` resolves the same `CoordT`, interning the same template arg slice every time).

### 2. Intern slices separately

Add a `HashMap<&[T], &'s [T]>` that deduplicates slices before interning the parent struct.

- **Pro:** Identical slices across different parent types share storage.
- **Con:** Overhead of hashing every element on every lookup. Most slices are unique (different source positions), so the dedup rate is low. Adds a second intern map that must be maintained.

### 3. bumpalo::Vec (build directly in arena)

Use `bumpalo::collections::Vec` to build the slice data directly in the arena, then `into_bump_slice()` on a miss.

- **Pro:** No heap allocation at all — data goes straight into the arena.
- **Con:** Still wastes arena space on hits (the bumpalo::Vec's buffer can't be freed from the bump arena). Also wastes space on Vec growth (old capacity chunks become dead arena space).

### 4. Two-phase Val with `'tmp` + private fields (chosen)

Val types borrow slice data from stack temporaries via a `'tmp` lifetime. The intern method checks the map using the transient Val. On a hit, nothing is allocated — the Val is discarded. On a miss, the slice is arena-allocated via `promote_in()` and the permanent struct is created.

Privacy on Val fields prevents callers from pre-allocating: `LocationInDenizenVal`'s `path` is private, constructible only via `borrow_val()` which borrows from a `LocationInDenizenBuilder`'s `Vec<i32>` on the stack.

A `RuneValQuery` wrapper is needed for heterogeneous HashMap lookup because `hashbrown::Equivalent<IRuneValS<'s,'s>> for IRuneValS<'s,'tmp>` can't be implemented directly (conflicts with the blanket `Equivalent<K> for K` impl when `'tmp = 's`).

- **Pro:** Zero waste on hits. Privacy enforces correctness. Generalizes to the typing pass's `Vector[ITemplataT]` fields.
- **Con:** Extra lifetime parameter, RuneValQuery wrapper, larger `alloc_rune_canonical` match block. Acceptable complexity for the correctness guarantee.

### 5. Type-system enforcement (`'tmp != 's`)

Considered whether Rust's type system could enforce that `'tmp` is never the arena lifetime `'s`.

- **Result:** Not possible. Rust has no negative lifetime constraints. When `'tmp = 's`, `LocationInDenizenVal<'tmp>` is `LocationInDenizenVal<'s>`, and there's no way to reject that at compile time. Privacy on the struct fields is the enforcement mechanism instead.
