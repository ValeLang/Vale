# Arena Architecture

## ParseArena and ScoutArena

Each arena struct wraps a `&Bump` and owns `RefCell<HashMap<...>>` interning maps:

- **`ParseArena<'p>`**: strings, package coordinates, file coordinates
- **`ScoutArena<'s>`**: strings, package coordinates, file coordinates, names (`INameS`), runes (`IRuneS`), imprecise names (`IImpreciseNameS`)

Interning maps use `HashMap::with_capacity(64)` to avoid rehashing during keyword interns at pass startup.

## Why Immutability Matters

**Nothing in an arena is ever mutated.** This is an absolute invariant, not a guideline:
- **No resizing.** `ArenaIndexMap` hash tables are allocated once at correct size.
- **No dangling pointers.** Nothing points to data that might move.
- **Bulk deallocation.** Dropping the arena frees everything. No individual destructors.
- **Identity stability.** `&'t` references are stable forever, so `std::ptr::eq` is valid identity (per @IEOIBZ).

The type system enforces most of this — you can't get `&mut T` from a `&'t T`. The remaining failure mode is interior mutability (`RefCell`, `Cell`, `Mutex`) inside an arena-allocated struct; treat that as a bug.

If a value's lifecycle needs mutation, it does not go in the arena. See "Mutation patterns" below.

## Output Data vs Working State

All data in the compiler falls into two categories:

**Output data** is the result of a pass — AST nodes, rules, names, types. It lives in the arena, is immutable after construction, and is referenced by everything downstream. Output data must be either:
- **Arena-allocated and accessed by `&'s`/`&'p` reference** (e.g., `StructS`, `FunctionA`, `IExpressionSE` variants), or
- **Copy and stored inline** (e.g., `RangeS`, `StrI`, `CodeLocationS`, `ICitizenAttributeS` variants)

The smell to watch for is **Clone-without-Copy** on output data. Copy types must also derive Clone (Rust requires it as a supertrait), but that Clone is a trivial memcpy — harmless. Clone *without* Copy means the type could hide an expensive heap duplication, and should be investigated. Output data must never contain heap collections (`Vec`, `HashMap`, `Box`).

**Working state** is mutable data used during a pass — scopes, environments, builders, solver state. It lives on the stack or heap and may contain `Vec`, `HashMap`, `Box`. Clone is allowed for working state (e.g., `StackFrame` is cloned on scope entry). Moving working state off the heap (persistent data structures, Rc, arena-backed collections) is a future refactor goal.

## Mutation Patterns

Arenas don't support mutation, period. When a value's lifecycle requires mutation, pick one of three patterns:

- **Box pattern** — Vec-backed mutation buffer that lives outside the arena, with `snapshot(interner)` to freeze the current state into the arena as a fresh `&'t T`. Each snapshot is a new arena allocation; the old one is unchanged. Used by `NodeEnvironmentBox`, `TemplatasStoreBuilder`, `FunctionEnvironmentBuilder`. Mirrors Scala's `*Box` case classes whose `var` field gets replaced on each mutation.
- **Non-arena container** — owns its own `Vec`/`HashMap` collections, lives entirely outside the arena, dies at pass end. Used by `CompilerOutputs` for accumulating per-pass results.
- **By-value** — owned `T` on the stack, mutate freely, then move into final position. Used for short-lived values that get built up and consumed in one scope.

The choice is driven by who mutates and how long the value lives:

| Lifetime | Multiple mutators? | Pattern |
|---|---|---|
| Short, single scope | No | By-value |
| Pass-wide accumulator | Many call sites push into it | Non-arena container |
| Scope-local, frozen at boundaries | Mutated then snapshotted, possibly multiple times | Box |

## Where To Put Data: Arena vs Inline vs Mutable Container

For an immutable value (one that has passed the "needs mutation" filter), the next question is whether it lives in the arena (accessed via `&'t T`) or stored inline by value. The decision framework lives in @WVSBIZ — the seven principles (size, dynamic length, interned, identity, sharing, recursion, back-pointers) and the Scala-parity override.

### Transient Data

A third category, **transient** data, exists only to build or look up permanent output data:

- **Val types** (`IRuneValS`, `INameValS`, etc.) — transient lookup keys for interning. Built on the stack, checked against the intern map, then either discarded (hit) or promoted to permanent (miss). Val types with slices use a `'tmp` lifetime to borrow from stack temporaries, deferring arena allocation until a miss (see @DSAUIMZ).
- **Working accumulators** — hold `HashMap`/`Vec` fields on the stack or heap, build data that eventually freezes into arenas:
  - `Astrouts` — higher typing accumulator, stack-local `&mut`
  - `EnvironmentA` — higher typing scope context, created functionally per scope
  - `EnvironmentS`, `FunctionEnvironmentS` — postparser scope contexts, cloned and boxed
  - `StackFrame` — expression scouting context
  - `LocationInDenizenBuilder` — mutable builder for `LocationInDenizen`. Produces transient `LocationInDenizenVal` via `borrow_val()` or permanent `LocationInDenizen` via `consume_in()`
  - `VariableDeclarations`, `VariableUses` — transient accumulators
  - Error types, solver state — returned via `Result` or mutated during solving

## Arena-Allocated Structs

**Scout arena (`'s`):** `StructS`, `InterfaceS`, `ImplS`, `FunctionS`, `GenericParameterS`, `ParameterS`, `ExportAsS`, `ImportS`, all `IExpressionSE` variants, all `IRulexSR` variant structs, `StructA`, `InterfaceA`, `ImplA`, `FunctionA`, `ExportAsA`, `ProgramA`, all `IRuneS`/`INameS`/`IImpreciseNameS` variant payloads. For interned rune payloads containing `LocationInDenizen` (e.g., `ImplicitRuneS`), the inner `&'s [i32]` path slice is only arena-allocated on intern miss — per @DSAUIMZ, the transient Val borrows from the stack until promotion.

**Parse arena (`'p`):** `PackageCoordinate<'p>`, `FileCoordinate<'p>`.

**Inline (not arena-allocated, not heap):** `CodeLocationS`, `RangeS` — fully `Copy`, stored directly in parent structs.

## The `'x` Generic Lifetime Pattern

Types that live in multiple arenas use a generic `'x` instead of a specific arena lifetime. `LocationInDenizen<'x>` is the model: it holds `&'x [i32]` and `'x` unifies with the owner's arena at each use site. This avoids duplicating types per arena.
