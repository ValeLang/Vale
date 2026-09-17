# When Values Should Be Interned (WVSBIZ)

This doc answers two related questions: where does an immutable value live (arena vs inline), and if arena, should it be interned (deduplicated) or just allocated. The mutation-vs-not question is settled first by `docs/architecture/arenas.md` — arenas are immutable, so anything that mutates uses Box / non-arena container / by-value patterns and never reaches this framework.

## Arena vs Inline: Seven Principles

Default: store inline as a Copy value-type. Promote to arena-allocated (accessed via `&'t T`) if any of the following holds.

1. **Size.** ≤128 bits → inline. Larger → arena. Copying a large value at every callsite is wasteful; arena allocation lets every reference be a thin pointer.

2. **Dynamic length.** Slices and collections of unknown compile-time length must be arena-allocated (`&'t [T]`). Inline storage would require a heap `Vec`, which violates AASSNCMCX in arena-allocated parents.

3. **Interned.** If the value should deduplicate (two structurally equal instances must share a pointer for fast `ptr::eq`), the payload must be arena-allocated. The wrapper enum that names it can still be stored inline — `IRuneS<'s>` is a tagged pointer (Copy, inline) whose `&'s CodeRuneS<'s>` payload lives in the arena.

4. **Identity.** If two distinct allocations are distinct *things* (not just equal values), the type must be arena-allocated. Per @IEOIBZ, identity types use `std::ptr::eq` for equality, which requires stable arena addresses. The expression hierarchy (`ReferenceExpressionTE`, `AddressExpressionTE`, etc.) is the canonical case — each instance is a distinct AST node even if structurally equal.

5. **Sharing.** If multiple parents hold the same value, arena-allocate so the shared refs are cheap copies of `&'t T`. Single-owner trees can stay inline. Interning is a special case of sharing — the interner *is* the sharing mechanism.

6. **Recursion.** Recursive types must arena-allocate at the recursion edge, otherwise the type has infinite size. `IEnvironmentT` holding `parent: &'t IEnvironmentT` is the canonical case.

7. **Back-pointers (`&'t self` methods, design v3 §3.4a).** If methods on the type need to emit `&'t Self` (wrap-self, embed-self in a child's `parent` field), the receiver must live in the arena. You can't hand out a `&'t` borrow into a stack value or `Vec` interior.

If none of the seven apply, the value stays inline as a Copy value-type. `CoordT`, `OwnershipT`, `RegionT`, `RangeS`, `CodeLocationS` are examples — small, no subcollections, single-owner, no identity, no recursion, no back-pointers.

**Definitional components are arena-allocated.** Types like `ParameterT` and `NormalStructMemberT` don't have independent identity, but they *define* part of an identity-bearing output (`FunctionHeaderT`, `StructDefinitionT`). They are the parameter, they are the member — not a reference to one. They're arena-allocated along with their parent (typically as an inline element of an arena slice the parent owns). The test: if something *is* part of a definition, it's arena-allocated; if it *refers to* a definition, it's a value (and may be interned per the rules below).

## When Arena-Allocated Becomes Interned

Once a value is arena-allocated, the next question is whether to intern it (deduplicate via `intern_*` so structurally equal values share a pointer) or just allocate it (each `arena.alloc` produces a distinct allocation). Intern when any of these holds:

1. **Subcollections.** If the value contains a variable-length collection (like a list of template args or parameter types), interning lets the collection live as an arena slice (`&'t [T]`) inside the interned payload, avoiding heap allocation entirely.

2. **Size.** If the value is large enough that copying it repeatedly through the wrapper would be expensive, interning stores it once and the wrapper becomes a thin `&'t` pointer.

3. **Enum budget.** If the value is stored as a variant in a Copy enum (`KindT`, `ITemplataT`), the enum's size is its largest variant. Interning the payload behind `&'t` keeps every variant pointer-sized, keeping the enum small and Copy. This is why `StructTT` and `CoordTemplataT` are interned even though they're individually small — they live inside `KindT` and `ITemplataT` respectively.

If none apply, the arena-allocated type stays uninterned (each `arena.alloc` is a fresh distinct allocation). Identity types — definitions, environments, expression nodes — are arena-allocated and *never* interned, because their whole point is that each allocation is a distinct thing. Interning would erase identity.

## Scala Parity Overrides The Heuristics

Rust's Interned set is anchored to Scala's `IInterning` trait — types that extend `IInterning` in Scala (sealed traits `INameT` and `ICitizenTT`, plus `StaticSizedArrayTT`, `RuntimeSizedArrayTT`, `OverloadSetT`) are Interned in Rust. Types that don't (the Templata variants, `SignatureT`, `PrototypeT`, `KindPlaceholderT`) stay as Value-types or arena-allocated-non-interned per @TFITCX even when they superficially fit one of the heuristics above. The one deliberate divergence is `IdT` — Scala leaves it as a plain case class and gets correct equality from `Vector`'s structural compare; Rust interns it specifically for `&'t [INameT]` slice-pointer-equality performance, which Scala didn't need.

Once a type is classified as Interned, construction must go through the interner — see @SICZ for the privacy enforcement.

## See also

- `docs/architecture/arenas.md` — the immutability invariant and mutation patterns (Box / non-arena container / by-value); arena-vs-inline is the immutable-side decision after that filter.
- @TFITCX — the six categories every struct/enum must be classified into.
- @SICZ — how Interned types are sealed against external construction via `MustIntern`.
- @IEOIBZ — identity-bearing arena-allocated types implement `PartialEq`/`Hash` via `std::ptr::eq`/`std::ptr::hash`.
- @DSAUIMZ — slice allocation deferred until intern miss.
