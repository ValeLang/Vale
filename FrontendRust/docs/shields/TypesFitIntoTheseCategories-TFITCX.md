---
description: Every struct and enum must have a doc comment categorizing it as arena-allocated, value-type, interned, interning transient, temporary state, or miscellaneous.
g_model: SimpleSmall
g_context: definition
g_primary: rust
g_program: TypesFitIntoTheseCategories-TFITCX
g_defs: struct, enum
---

# Types Fit Into These Categories (TFITCX)

Every struct and enum definition must have a `///` doc comment above it (before any `#[derive(...)]` or other attributes) categorizing it into exactly one of these seven categories:

- `/// Arena-allocated (see @TFITCX)` — identity-bearing output of a pass, or definitional component of one; stored as `&'s T` or `&'t T` via `arena.alloc()`, immutable after construction, no Clone
- `/// Value-type (see @TFITCX)` — derives Copy, stored inline in slices or struct fields; no identity, no subcollections
- `/// Interned (see @TFITCX)` — a canonical deduplicated handle returned by an intern method (see @WVSBIZ for when to intern). Must include a `pub _must_intern: MustIntern` field per @SICZ, sealing construction to the interner module.
- `/// Interning transient (see @TFITCX)` — ephemeral lookup key for intern methods; never stored, discarded after hit/miss check
- `/// Polyvalue (see @TFITCX)` — Copy enum, ~16 bytes (discriminant + payload word), whose variants hold *non-owning values*. A payload may be (a) a non-owning reference to Arena-allocated identity-bearing data (e.g. `&'t CitizenEnvironmentT`), (b) a Copy handle to an interned type (e.g. `IdT`), or (c) a small inline value with no identity (e.g. `MutabilityTemplataT(MutabilityT)`). Mix freely per variant. By-value passing is the default (two words = pass like a Rust fat pointer). Eq/Hash **must derive** — never hand-roll `std::ptr::eq(self, other)` on the outer `&self`; that hashes the stack address and silently breaks under by-value use. See @PVECFPZ.
- `/// Temporary state (see @TFITCX)` — mutable working data during a pass (environments, builders, accumulators), may Clone
- `/// Miscellaneous type (see @TFITCX)` — doesn't fit the above (errors, solver state, test infrastructure, configuration)

## Examples

**DENY:**
```rust
pub struct StructS<'s> {
    pub name: INameS<'s>,
}
```

**DENY:**
```rust
// Wrong category prefix
pub struct StructS<'s> {
    /// This is a postparser AST node
    pub name: INameS<'s>,
}
```

**ALLOW:**
```rust
/// Arena-allocated (see @TFITCX)
pub struct StructS<'s> {
    pub name: INameS<'s>,
}
```

**ALLOW:**
```rust
/// Value-type (see @TFITCX)
pub struct RangeS<'s> {
    pub begin: CodeLocationS<'s>,
    pub end: CodeLocationS<'s>,
}
```

**ALLOW:**
```rust
/// Temporary state (see @TFITCX)
pub struct StackFrame<'s> {
    pub locals: VariableDeclarations<'s>,
}
```

**ALLOW** (Interned with seal field per @SICZ):
```rust
/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StructTT<'s, 't> {
  pub id: IdT<'s, 't>,
  pub _must_intern: MustIntern,
}
```

## Exceptions

A. Structs inside `/* ... */` Scala block comments (commented-out Scala code, not active Rust).

B. Test-only structs (inside `#[cfg(test)]` modules).

## See also

- @WVSBIZ — full decision framework for arena-vs-inline (seven principles), and when an arena-allocated value should be interned vs just allocated.
- @SICZ — how Interned types are sealed against external construction via `MustIntern`.
- @IEOIBZ — identity-bearing Arena-allocated types implement `PartialEq`/`Hash` via `std::ptr::eq`/`std::ptr::hash`.
- @PVECFPZ — Polyvalue enums as closed-set fat pointers; the by-value eq/hash trap.
- `FrontendRust/docs/architecture/arenas.md` — arenas are immutable; mutation goes through Box / non-arena container / by-value patterns instead.
