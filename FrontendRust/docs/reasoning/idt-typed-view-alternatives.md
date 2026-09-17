# Reasoning: IdT Typed-View Alternatives

`IdT<'s, 't>` represents a typing-pass name path: `myapp::foo::bar<int, bool>::someFunc`. Scala defines it as `IdT[+T <: INameT]` — the phantom outer type parameter records which kind of name the path ends in (function, struct, impl, …). In Rust, the question of how to represent that parameter is surprisingly subtle; this doc captures the current choice and records alternatives for post-migration revisit.

## Chosen (during migration): monomorphic `IdT<'s, 't>`

```rust
pub struct IdT<'s, 't>
where 's: 't,
{
    pub package_coord: &'s PackageCoordinate<'s>,
    pub init_steps: &'t [INameT<'s, 't>],
    pub local_name: INameT<'s, 't>,
}
```

Always the widest form. Callers that need to assert "this IdT's local_name is a `FunctionNameT`" pattern-match on `local_name` at the point of use, like they would in Scala after `match id.localName` discovers `FunctionNameT(…)`. No generic parameter, no typed widening/narrowing.

Rationale for picking this during migration: **Scala parity** and **simplicity**. Scala's `+T` parameter expresses a compile-time contract between call sites, but Rust's enforcement patterns for the same contract (generics, wrappers, unsafe transmute) all have non-trivial costs we don't want to absorb while the real task is getting Scala logic ported faithfully. Post-migration, any of the alternatives below are available.

## Alternatives Considered (deferred)

### 1. Generic `IdT<'s, 't, T: Copy>` with widest-form interning and custom Eq

The original Slab 2 attempt (committed as `1811a12f..8359c0bf`). `IdT` is generic in its leaf-name type `T`. Interned; the interner uses one HashMap keyed by the widest form (`IdValT<..., INameT>`), and each specialization is served by an unsafe type-cast of the widened arena storage. Custom `PartialEq`/`Eq`/`Hash` on `IdT` because identity is per-field (`ptr::eq` on `package_coord` + `init_steps` slice, structural on `local_name`).

- **Pros:** Type-level assertion at call sites (can't accidentally pass a function-id where a struct-id is required). `widen`/`try_narrow` available as methods.
- **Cons:** Sharing arena storage across T-specializations requires layout invariance (`IdT<..., IFunctionNameT>` and `IdT<..., INameT>` must have the same byte layout at runtime). Rust doesn't guarantee this without `#[repr(u8)]` discriminant alignment across every name sub-enum. Without that guarantee, `widen`/`try_narrow` have to actually re-intern (HashMap round-trip, no new allocation but not free either). Call sites carry the T parameter noise in every signature. Also, when working with owned `IdT` values (not `&'t`), `widen(self)` constructs a new struct with a changed local_name variant — which means the interner has to dedup that new form back to the existing canonical storage.

Expected revisit: if post-migration profiling shows the monomorphic design loses to the type-level contract for catching bugs, AND we're willing to pay the layout-invariance + re-intern costs (or switch to Alternative 2).

### 2. Unsafe transmute between typed `&'t IdT<..., T>` views

Keep the generic `IdT<'s, 't, T: Copy>` but use `#[repr(u8)]` (or equivalent) to pin the discriminant byte layout across every name sub-enum so that `IdT<..., IFunctionNameT>` and `IdT<..., INameT>` have byte-identical in-memory representations. Then `widen` and `try_narrow` become `unsafe { transmute(&'t IdT<..., T1>, &'t IdT<..., T2>) }` — zero-cost typed views over the same arena storage.

- **Pros:** Typed widen/narrow is a no-op at runtime. Single arena allocation per logical IdT.
- **Cons:** Requires every name sub-enum to coordinate discriminant values manually (`IFunctionNameT::Function = 0`, `INameT::Function = 0`, etc.) — one mismatch and the compiler silently corrupts data. No ergonomic way to enforce this across 22 sub-enums. Unsafe boundary is easy to violate accidentally when someone adds a variant. Bug surface is large and debugging is awful.

Expected revisit: only if Alternative 3 proves ergonomically unacceptable AND profiling shows the HashMap re-intern cost of Alternative 1 is unacceptable.

### 3. `RawIdT<'s, 't>` canonical + pointer-wrapper typed view

Split the type: `RawIdT<'s, 't>` is the monomorphic canonical form (interned in the arena). A separate `IdT<'s, 't, T: Copy>` is a thin stack-only wrapper holding `&'t RawIdT<'s, 't>` and a `PhantomData<T>` (or a cached narrow `local_name: T` for zero-access-cost).

```rust
pub struct RawIdT<'s, 't> { /* always widest */ }
pub struct IdT<'s, 't, T: Copy> { raw: &'t RawIdT<'s, 't>, _phantom: PhantomData<T> }
```

- **Pros:** One HashMap, one Val type, one intern method. No layout coordination across sub-enums. Typed view is pointer-sized (8 bytes, Copy). `widen`/`try_narrow` are pure stack operations (no HashMap probe, no allocation). Compile-time type-safety preserved.
- **Cons:** Ergonomic tax — `id.local_name` becomes `id.local_name()` (method with a one-arm match that the optimizer may or may not elide). Construction ceremony: `IdT::try_new(raw)?` instead of implicit coercion. Equality semantics: two typed views compare via their inner `raw` ptr; fine as long as `RawIdT` is always interned, but a footgun if someone stack-constructs a `RawIdT`. If we add a cached inline `local_name: T` field to the wrapper to avoid per-access match cost, the wrapper grows to 16–24 bytes (still Copy, still cheap to pass, but not pointer-sized).

Expected revisit: this is the most likely post-migration winner. The ergonomic tax is small compared to the design clarity win. Cached-inline variant is probably what ships.

### 4. Type erasure at every call site (status quo, monomorphic)

What we have today. Always the widest form. No generic T. Callers pattern-match on `local_name` at the point they need narrowing.

- **Pros:** Simplest possible design. Zero ambiguity about interner semantics. Closest to Scala-at-runtime (phantom T is erased at JVM runtime anyway — the `+T` is purely compile-time). Easy to reason about.
- **Cons:** Loses compile-time type assertions at call sites. A function that takes `id: IdT<'s, 't>` can't express "I require an id whose local_name is a FunctionName" via the type system — it has to pattern-match at runtime and panic/err if wrong. This is where Rust idiomatically does better than Scala, and we're giving that up.

Accepted during migration. Revisit post-migration.

## Migration parity note

Scala's `IdT[+T <: INameT]` with specialized narrow-name types (`IdT[IFunctionNameT]`, `IdT[IStructNameT]`, etc.) imposes no runtime cost in Scala because of JVM type erasure — `+T` is compile-time-only. Rust's generics can also be compile-time-only if we go the phantom-wrapper route (Alternative 3), but the default generic-struct approach (Alternative 1) forces us to think about per-specialization storage, which Scala doesn't have to.

The monomorphic approach is the one least likely to cause mapping friction during migration — a Scala `IdT[IFunctionNameT]` becomes a Rust `IdT<'s, 't>` whose usage asserts at pattern-match time that the local_name is `INameT::Function(_)`. Direct translation.

## See also

- `docs/architecture/typing-pass-design-v3.md` §6.3 — current `IdT` design.
- `docs/reasoning/deferred-slice-interning.md` — `'tmp`-lifetime pattern used by `IdValT`.
- `src/typing/names/names.rs` — current `IdT` definition.
