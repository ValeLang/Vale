---
g_read_when: "Read when interning runes, names, or imprecise names in the postparser."
g_auto_load_when_editing:
  - FrontendRust/src/postparsing/*.rs
---

# Postparser Interning: Dual-Enum Pattern For Lookups (IDEPFL)

Scala's `Interner` used GC-backed `HashMap[T, T]` to canonicalize case classes. Rust replaces this with arena-backed interning on `ScoutArena<'s>` using two parallel enums per type hierarchy: a **reference enum** (permanent, holds `&'s` pointers to arena data) and a **value enum** (transient, used as HashMap lookup keys). The transient Val exists only to check "does this already exist?" — then it's either discarded (hit) or promoted into permanent arena storage (miss).

---

## The Five Dual-Enum Pairs

| Permanent Enum (canonical) | Transient Enum (lookup key) | ScoutArena Method |
|---|---|---|
| `IRuneS<'s>` | `IRuneValS<'s, 'tmp>` | `intern_rune()` |
| `IImpreciseNameS<'s>` | `IImpreciseNameValS<'s>` | `intern_imprecise_name()` |
| `INameS<'s>` | `INameValS<'s>` | `intern_name()` |
| `IFunctionDeclarationNameS<'s>` | `IFunctionDeclarationNameValS<'s>` | via `INameS` |
| `IVarNameS<'s>` | `IVarNameValS<'s>` | via `INameS` |

Note: `IRuneValS` has a second lifetime `'tmp` because some of its variants contain transient slice data (see @DSAUIMZ). The other Val enums don't have slices yet, so they use only `'s`.

---

## How They Differ

The **permanent enum** holds `&'s` references to arena-allocated payloads:

```rust
pub enum IRuneS<'s> {
  CodeRune(&'s CodeRuneS<'s>),
  ImplicitRune(&'s ImplicitRuneS<'s>),
  ImplicitRegionRune(&'s ImplicitRegionRuneS<'s>),
  // ...
}
```

The **transient enum** holds payload structs by value for HashMap lookup. The `'tmp` lifetime borrows from stack temporaries, not the arena (see @DSAUIMZ):

```rust
pub enum IRuneValS<'s, 'tmp> {
  CodeRune(CodeRuneS<'s>),                   // simple: same struct, no slice
  ImplicitRune(ImplicitRuneValS<'tmp>),      // transient: borrows slice via 'tmp
  ImplicitRegionRune(ImplicitRegionRuneValS<'s>),  // shallow: holds canonical refs
  // ...
}
```

---

## Why Both Exist

You can't skip the transient Val enum because:

1. The permanent enum contains `&'s` pointers — you can't construct one without first allocating into the arena.
2. You need a hashable key to check whether a value was already interned.
3. If you allocated into the arena first and then checked, you'd waste arena space on duplicates. The transient Val avoids this — it lives on the stack and costs nothing to discard on a hit.

---

## Interning Flow

1. **Build a transient Val** — construct an `IRuneValS<'s, 'tmp>`. For variants with slices, borrow from a stack-local builder (see @DSAUIMZ). For scalar variants, just fill in the fields.
2. **Look it up** — the scout arena checks `hashbrown::HashMap<IRuneValS<'s, 's>, IRuneS<'s>>` using a `RuneValQuery` wrapper for heterogeneous lookup. If found, return the existing permanent `IRuneS`. The transient Val is discarded — zero arena cost.
3. **Promote if new** — on a miss, promote the transient Val to permanent: arena-allocate any slices via `promote_in()`, allocate the payload struct, wrap the `&'s` ref in the corresponding `IRuneS` variant, store the mapping, return it.

```rust
// Caller builds a transient Val that borrows from the builder's stack Vec:
let rune = self.scout_arena.intern_rune(
    IRuneValS::ImplicitRune(ImplicitRuneValS::new(lidb.child().borrow_val()))
);
// On HIT: the transient Val is discarded, nothing allocated
// On MISS: the path slice and ImplicitRuneS are arena-allocated
// Either way, rune is IRuneS::ImplicitRune(&'s ImplicitRuneS)
```

---

## Internable Units

A Val struct defines an **internable unit** — the boundary around everything that gets interned together atomically. The Val is what you hash and look up. Its inner data falls into two categories:

- **Already-permanent references** to other interned types (`IRuneS<'s>`, `StrI<'s>`, `INameS<'s>`) — pointers to data from a previously-promoted internable unit. Free to copy.
- **Owned transient data** — slices or primitives unique to this unit. Borrowed from the stack via `'tmp`, promoted to the arena only on a miss.

`Val` in the name marks an internable unit boundary. Types without `Val` (like `LocationInDenizen`, a bare slice) are **parts** of their owning unit — they get arena-allocated when the unit is promoted, not before (see @DSAUIMZ).

---

## Three Kinds of Val Variants

### Simple: same struct in both enums

When a payload struct contains only simple/Copy fields (like `StrI<'s>`), the transient Val enum holds the same struct type by value. No separate Val struct is needed:

- `IRuneS::CodeRune(&'s CodeRuneS<'s>)` — permanent
- `IRuneValS::CodeRune(CodeRuneS<'s>)` — transient (same struct, trivially cheap)

### Shallow: separate Val struct for nested interned types

When a payload struct contains references to *other* interned types, a separate Val struct exists. The Val struct holds the child as an already-canonical owned `IRuneS<'s>` (it's "shallow" — children must be interned first):

```rust
// Permanent payload (lives in arena):
pub struct ImplicitRegionRuneS<'s> {
  pub original_rune: IRuneS<'s>,
}

// Transient lookup key (for HashMap):
pub struct ImplicitRegionRuneValS<'s> {
  pub original_rune: IRuneS<'s>,
}
```

The fields look identical in this case, but they're separate types so the type system enforces going through the scout arena. You can't accidentally use a Val where a canonical ref is expected.

Note: `IRuneS<'s>` is already just a tagged pointer (discriminant + `&'s` to arena payload), so holding it owned vs `&'s IRuneS<'s>` is storing the tagged pointer directly vs a pointer-to-a-pointer. Owned is simpler and equally cheap. Identity is checked via `IRuneS::ptr_eq`/`canonical_ptr` which look at the inner payload pointer.

Other shallow Val structs follow the same pattern:
- `ImplicitCoercionOwnershipRuneValS` — holds `IRuneS<'s>` for its child rune
- `AnonymousSubstructImplDeclarationNameValS` — holds `&'s TopLevelInterfaceDeclarationNameS<'s>`
- `ImplImpreciseNameValS` — holds two `IImpreciseNameS<'s>` children
- `ForwarderFunctionDeclarationNameValS` — holds `IFunctionDeclarationNameS<'s>`

**Rule**: intern children first, then build the parent Val with canonical child runes.

### Transient with `'tmp`: Val struct that defers slice allocation

Per @DSAUIMZ, when a payload struct contains a slice (like `LocationInDenizen { path: &'s [i32] }`), the transient Val holds a **borrowed** version of that slice via a `'tmp` lifetime, not an arena-allocated one. The slice is only arena-allocated inside the intern method on a miss.

```rust
// Permanent payload (lives in arena):
pub struct ImplicitRuneS<'s> {
  pub lid: LocationInDenizen<'s>,  // path: &'s [i32] — arena-allocated
}

// Transient lookup key (borrows from stack):
pub struct ImplicitRuneValS<'tmp> {
  lid: LocationInDenizenVal<'tmp>,  // path: &'tmp [i32] — borrows builder's Vec
}
```

The transient Val's fields are **private** — constructible only via `borrow_val()` which ties `'tmp` to a stack-local `LocationInDenizenBuilder`. This privacy ensures callers cannot accidentally pre-allocate the slice in the arena.

The `'tmp` lifetime is what makes the Val truly transient: it borrows from a local that dies when the function returns, so the Val physically cannot outlive its intended check-and-discard purpose.

Seven rune variants use this pattern: `ImplicitRuneValS`, `PureBlockRegionRuneValS`, `CallRegionRuneValS`, `CallPureMergeRegionRuneValS`, `LetImplicitRuneValS`, `MagicParamRuneValS`, `LocalDefaultRegionRuneValS`.

---

## Identity via `ptr_eq`

The canonical enums provide `ptr_eq()` and `canonical_ptr()` methods. Since the scout arena guarantees structurally equal values get the same arena allocation, pointer equality is identity equality:

```rust
impl<'s> IRuneS<'s> {
  pub fn canonical_ptr(&self) -> *const () { /* extracts inner &'s pointer */ }
  pub fn ptr_eq(&self, other: &IRuneS<'s>) -> bool {
    std::ptr::eq(self.canonical_ptr(), other.canonical_ptr())
  }
}
```

This mirrors Scala's `eq` (reference equality) after interning.

---

## Conversion: Ref → Val

`IFunctionDeclarationNameS` provides `to_val()` for converting a canonical reference back to a value key. This is used when you have an existing canonical name and need to build a parent Val that wraps it.

---

## What Scala Had

In Scala, all of these were just `case class`es extending `sealed trait`s with `IInterning`. The `Interner` used `HashMap[T, T]` where the JVM's GC managed memory and `eq` gave reference identity. There was no transient/permanent distinction — the GC handled discarding duplicates silently. Rust splits each sealed trait into two enums to make the transient/permanent boundary explicit: transient Vals for lookup, permanent refs for storage.
