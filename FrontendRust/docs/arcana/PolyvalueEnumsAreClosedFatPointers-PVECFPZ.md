# Polyvalue Enums Are Closed-Set Fat Pointers (PVECFPZ)

A *polyvalue* enum is a `Copy` wrapper enum, ~16 bytes (discriminant + payload word), whose variants hold **non-owning values**. The enum doesn't conceptually own its payload — it *names* something whose canonical home is elsewhere. Canonical examples: `IEnvironmentT`, `IInDenizenEnvironmentT`, `IEnvEntryT`, `ITemplataT`, `KindT`, `INameT`.

## The mental model: closed-set fat pointer

Rust's `&dyn Trait` is two words: data pointer + vtable pointer. A polyvalue enum is two words: discriminant + payload word. Same layout, same Copy-by-value passing convention, same equality regime ("two copies that point at the same thing should compare equal regardless of where on the stack they sit").

The only difference is *how dispatch works*: `&dyn Trait` is open-set (vtable lookup); a polyvalue enum is closed-set (`match` on the discriminant). We pick closed-set when we know all variants at compile time and want match-based codegen. The runtime shape and equality semantics are isomorphic to `&dyn Trait`.

## Per-variant ref-or-value

Each variant chooses whether its payload is a reference or an inline value based on whether the payload carries identity:

- **Has identity (Arena-allocated or interned)** → the variant payload is a non-owning ref (e.g. `Citizen(&'t CitizenEnvironmentT)`) or a canonical-handle wrapper (e.g. `IdT`). Inlining the raw struct would defeat the identity seal — two copies of the inlined bytes would each be "a different thing."
- **No identity (small Copy primitive)** → inline by value (e.g. `MutabilityTemplataT(MutabilityT)`). The pointer would be dead weight; the value *is* the information.

Same enum, same layout, same calling convention — different per-variant choices. `IEnvEntryT` is the textbook example: four variants hold `&'s` refs to identity-bearing AST (Function, Struct, Interface, Impl), one variant holds an `ITemplataT` (itself a polyvalue). No exception, no special case — the same per-variant rule.

A consequence worth naming: a polyvalue can (directly or indirectly) contain references to things that have identity. That's expected — `IdT` already does, and any polyvalue wrapping `IdT` does too. Identity propagates through the layers.

## The eq/hash trap

Hand-rolling `std::ptr::eq(self, other)` on the outer `&self` of a polyvalue **silently breaks under by-value use**.

While the wrapper is always held behind `&'t Outer`, the outer address *is* the stable arena address — ptr-eq on the outer happens to coincide with ptr-eq on the inner. The moment the wrapper gets flipped to by-value, `self` becomes a stack address. Two by-value copies of `IEnvironmentT::Citizen(same_arena_ref)` sit at different stack addresses, compare unequal, hash to different buckets, and silently corrupt any `HashMap<IEnvironmentT, _>` or `HashSet<IEnvironmentT>` keyed on them. It compiles, it doesn't panic — it just gives wrong answers.

**Rule:** polyvalue enums must `#[derive(PartialEq, Eq, Hash)]`. Never hand-roll on the outer `&self`.

**Why the derive is correct.** The derived `eq` matches on the variant and calls `PartialEq::eq` on each payload. For ref payloads, that desugars to `<T as PartialEq>::eq(&*x, &*y)`; the inner type's `eq` (per @IEOIBZ) does the right identity comparison. For inline-value payloads, structural eq is the right thing. The chain bottoms out correctly in both cases.

**If you must hand-roll** (e.g. some variants have payloads that can't derive): `match` on the variant pair and `ptr::eq` the **inner** refs, never the outer `&self`. But by-value passing is the default for polyvalues, so the safest stance is "always derive."

## When to use the Polyvalue category

A type is a polyvalue iff:

1. It's an `enum`.
2. It derives `Copy` (so it's passed by value).
3. Variants hold *non-owning* payloads — refs to identity-bearing things owned elsewhere, canonical handles to interned things, or small inline values.
4. The enum itself is not the canonical owner of any of its variants' payloads.

If instead the enum is the canonical owner of its variants' payloads (the variants *define* identity), it's `/// Arena-allocated`. If the enum derives Copy but its variants are *all* inline value structs (no refs, no canonical handles), it can stay `/// Value-type` — but most wrapper enums in the typing pass are polyvalues.

## See also

- @TFITCX — category list and shield.
- @IEOIBZ — identity-bearing Arena-allocated types implement `PartialEq`/`Hash` via `std::ptr::eq` on `&self`. The inner-type half of the polyvalue derive chain.
- @SICZ — sealed interned construction. The reason canonical-handle wrappers (like `IdT`) can sit inside a polyvalue variant and still preserve identity.
- @WVSBIZ — when an arena-allocated value should be interned vs just allocated.
