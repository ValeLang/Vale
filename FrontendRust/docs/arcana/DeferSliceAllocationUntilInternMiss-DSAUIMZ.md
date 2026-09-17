# Defer Slice Allocation Until Intern Miss (DSAUIMZ)

Val types are **transient** — they exist only long enough to check "does this already exist in the arena?" and are then either discarded (hit) or promoted into permanent arena storage (miss). When a transient Val contains a slice, that slice must also be transient: borrowed from the stack, not pre-allocated in the arena. If the slice were arena-allocated before the intern check, a hit would leave dead arena space that can never be freed.

The `'tmp` lifetime encodes transience. It's tied to a local builder's `Vec` on the stack, which dies when the function returns. The transient Val can't escape, can't be stored, can't outlive its purpose. The intern method is the gateway between transient and permanent — it either finds an existing permanent form or promotes the transient into one.

## Internable Units

A Val struct defines an **internable unit** — the boundary around everything that gets interned together as one atomic operation. The Val is what you hash and check the map for. Everything inside it — slices, maps, sets — is part of the unit's identity and gets promoted to the arena together on a miss.

The naming convention makes this visible: `Val` in the name marks an internable unit boundary. Types *without* `Val` — like `LocationInDenizen`, a bare `&[i32]` slice, a `HashMap` — are **parts** of their owning internable unit. They get arena-allocated when the unit is promoted, not independently and not before.

An internable unit's contents fall into two categories:
- **Already-permanent references** to other interned types (e.g., `IRuneS<'s>`, `StrI<'s>`) — these are just pointers to data that's already in the arena. They were part of a *different* internable unit that was promoted earlier. Free to copy.
- **Owned transient data** — slices, collections, or primitives that are unique to this unit. These are what `'tmp` borrows from the stack. They become permanent only when the unit is promoted.

This is why slices inside Vals must be transient: they're part of the internable unit, and the unit hasn't been committed to the arena yet when you're checking the map.

## Privacy Enforcement

Privacy enforces this boundary. Transient Val types keep their slice fields **private**, constructible only via builder methods that borrow from stack temporaries. The promotion method `promote_in()` is `pub(crate)` and only called inside `intern_*` methods on a miss.

## Where

- `LocationInDenizenVal<'tmp>` — transient form of `LocationInDenizen<'s>`. Private `path` field, constructed only via `LocationInDenizenBuilder::borrow_val()`
- `ImplicitRuneValS<'tmp>` and 6 other `*ValS<'tmp>` structs — transient forms with private `lid` field
- `IRuneValS<'s, 'tmp>` — the `'tmp` lifetime carries the transient borrow
- `ScoutArena::intern_rune()` — the gateway: accepts transient `IRuneValS<'s, 'tmp>`, promotes to permanent only on miss
- `RuneValQuery` — wrapper enabling heterogeneous lookup (transient key against permanent stored keys) via `hashbrown::Equivalent`
- All `borrow_val()` call sites in templex_scout, rule_scout, function_scout
- Future: typing pass internable types with `Vector[ITemplataT]` fields follow the same pattern

## Cross-cutting effect

Every new internable type that contains a slice must follow the transient/permanent split: private fields on the transient Val, construction only via a borrowing method, promotion only inside the intern method. Violating this (e.g., making Val fields pub and pre-allocating the slice in the arena) silently wastes arena space — no compiler error, no runtime error, just growing memory use proportional to intern hit rate.

Rust's type system cannot express `'tmp != 's` (you can't say "this lifetime must not be the arena lifetime"), so privacy is the enforcement mechanism. `LocationInDenizenVal`'s field is private and only constructible via `borrow_val()`, which borrows from the builder's `Vec<i32>` on the stack — guaranteeing `'tmp` is a stack lifetime, not `'s`.

## Why it exists

The typing pass will intern the same types hundreds of times (e.g., every use of `int` resolves to the same `CoordT`). Template arg slices would be pre-allocated and wasted on every hit. The transient/permanent split with privacy enforcement makes the correct path (borrow from temporary) the only path.
