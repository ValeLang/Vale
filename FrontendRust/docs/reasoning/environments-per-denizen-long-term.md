# Reasoning: Typing-Pass Arenas — Why Today's Design, and Where It's Heading

The current typing-pass arena model (single `'t` interner for the whole pass, envs allocated into `'t` via builder/Box pattern, `TemplatasStoreT` using `ArenaIndexMap`) is described in `docs/architecture/typing-pass-design-v3.md` Part 1. This doc records *why* that's the migration choice, what's wrong with it long-term, the empirical audit that justifies the target design, the target itself, and further future direction toward LSP.

## TL;DR

- **Migration-phase (today's design):** envs in the `'t` typing arena alongside names, kinds, templatas. Single `TypingInterner<'t>` for the whole pass. Chosen for Scala parity and uniformity with Slabs 1-3; costs of the model (builder-freeze churn, slice-based lookup, no `HashMap`-in-env) are acceptable at migration scale.
- **Long-term (post-Slab-8):** split typing-pass storage into **two tiers** — a program-wide `'out` outputs arena for declarations + resolved types + skeleton envs, and a **per-top-level-denizen scratchpad arena** (`'scratch`) for working envs + transient templatas + solver state. A side table of `&'scratch env` with `EnvIdx(u32)` indices covers the scratchpad-env handoff to arena-allocated types. The scratchpad drops at the end of each worklist item; the outputs arena survives the whole pass.
- **Further out (LSP):** per-denizen arenas + `DefId`-indexed cross-denizen refs + shattered `GlobalEnvironment` + two-tier interner with single-writer cleanup promotion + periodic interner rebuild.

Scala parity is why the migration picks arenas. Cross-denizen edge analysis + `bumpalo`'s no-destructor semantics + per-denizen memory bounds are why the long-term target is two-tier per-denizen. Incremental recompilation on source change is why LSP needs a further evolution on top of that.

---

## The constraints driving the long-term target

### Cross-denizen edges audit

An empirical audit of the Scala source catalogued cross-denizen data flow. Summary:

- **Resolved-definition lookups are pervasive and unavoidable.** Function body typing routinely does `coutputs.lookupStruct(id)` / `lookupInterface(id)` to resolve `.field` accesses and method calls. The target struct/interface was typed by a *different* top-level denizen. These lookups are real cross-denizen reads and force a tier of long-lived data.
- **Function-call memoization is narrower than it looks.** `getOrEvaluateFunctionForHeader` appears to cross denizens but actually serves as a header cache. Only lambdas (intra-containing-function) hit the templated-banner path. For non-lambda calls, declared-return functions have headers computable from templates + scout `'s` data alone. *Only inferred-return functions would need cross-denizen body typing* — and only lambdas can have inferred returns, and lambdas can't be called cross-denizen (lambda `IdT`s bake in the containing function; `LambdaCallFunctionNameT` explicitly returns `None` from imprecise-name lookup). Category 2 collapses entirely.
- **Env escape classification (per-variant, audited):**
  - Escape to outputs tier (skeleton): `PackageEnvironmentT`, `CitizenEnvironmentT`, `BuildingFunctionEnvironmentWithClosuredsT`. Hold declaration-level data that other denizens must read during their typing (for method resolution, generic-bound reuse, override resolution in `EdgeCompiler`).
  - Stay in scratchpad (working): `NodeEnvironmentT`, `GeneralEnvironmentT`, `ExportEnvironmentT`, `ExternEnvironmentT`, `FunctionEnvironmentT`, `BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT`. Only read during their own top-level denizen's compilation.
- **Heavy-templata escape classification:**
  - Escape: `FunctionTemplataT` (stored in `FunctionHeaderT.maybeOriginFunctionTemplata` → HinputsT) and `ExternFunctionTemplataT` (held by persistent envs).
  - Stay: `StructDefinitionTemplataT`, `InterfaceDefinitionTemplataT`, `ImplDefinitionTemplataT`.
  - `FunctionTemplataT` escape is refactorable: no consumer actually reads `outer_env` — all readers access `function.range` / `.params` / `.name` which live in `'s`. Replace the field with `maybeOriginFunctionA: Option<&'s FunctionA>` and `FunctionTemplataT` moves to scratchpad cleanly.
- **Pointer-equality assumptions.** `IdT::Hash` / `Eq` use `std::ptr::eq` on `init_steps` and `package_coord`. Two denizens constructing structurally-equal `IdT`s would fail cross-denizen HashMap lookups unless both go through a shared interner. This forces a choice on interning strategy (see below).

### `bumpalo` does not run destructors

`bumpalo::Bump::alloc<T>(&self, val: T) -> &mut T` frees memory on arena drop but does not invoke `T::drop`. So arena-allocated structs must be plain data (no `Drop` impls that matter, no heap-owning fields).

Implication for envs: as long as envs are arena-allocated, they can't hold `HashMap`, `Vec`, `Rc`, or anything with real drop semantics. That's why the migration-phase `TemplatasStoreT` is slice-based and builder-freeze. Moving envs off the arena (or to a drop-honoring container) unlocks `HashMap`-in-`TemplatasStoreT` and natural incremental mutation.

### Per-denizen memory bounds

For batch typing of a large codebase, peak memory during `NodeEnvironmentT` mutation is bounded by the total cross-codebase env churn if envs live in one program-wide arena. Splitting by denizen bounds peak memory to the single largest function's working set.

---

## Long-term target: two-tier per-denizen model

### Two arenas

```
'out      — Global outputs arena (one per typing pass, dropped at pass end)
            lives as long as HinputsT
'scratch  — Per-top-level-denizen scratchpad (one per Phase-3 worklist item)
            dropped when that worklist item's compilation returns
```

### What lives in `'out` (program-wide)

- Resolved definitions: `StructDefinitionT`, `InterfaceDefinitionT`, `ImplT`, `EdgeT`, `FunctionDefinitionT`, `FunctionHeaderT`, `FunctionBannerT`.
- Interned types (globally deduplicated): `StructTT`, `InterfaceTT`, `CoordT`, `KindT`, `PrototypeT`, `SignatureT`, `IdT`, all concrete name structs.
- Skeleton envs (cross-denizen readable): `PackageEnvironmentT`, `CitizenEnvironmentT`, `BuildingFunctionEnvironmentWithClosuredsT`.
- `ExternFunctionTemplataT` (globally-visible extern declarations).
- `HinputsT`.

### What lives in `'scratch` (per-denizen)

- Working envs: `NodeEnvironmentT`, `GeneralEnvironmentT`, `ExportEnvironmentT`, `ExternEnvironmentT`, `FunctionEnvironmentT`, `BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT`.
- Scratchpad-safe heavy templatas: `StructDefinitionTemplataT`, `InterfaceDefinitionTemplataT`, `ImplDefinitionTemplataT`, `FunctionTemplataT` (after the `maybeOriginFunctionA` refactor).
- Transient `OverloadSetT` and solver state.
- Side table for scratchpad envs (see below).

### Side table + `EnvIdx` for scratchpad envs

Scratchpad envs are allocated into the per-denizen bumpalo arena (stable addresses). A side table `Vec<&'scratch IEnvironmentT<'s, 'scratch, 'out>>` lives on the scratchpad; arena-allocated types that need to refer to scratchpad envs hold `EnvIdx(u32)` indices:

```rust
pub struct FunctionTemplataT<'s, 'scratch> {
    pub outer_env: EnvIdx,          // index into per-denizen side table
    pub function: &'s FunctionA<'s>,
}

pub struct OverloadSetT<'s, 'scratch> {
    pub env: EnvIdx,
    pub name: &'s IImpreciseNameS<'s>,
}
```

Benefits:
- **No `Drop` leak risk from `Rc` in arena.** Scratchpad types are either plain arena-data or live in containers with explicit drop semantics.
- **Cycle-proof.** `EnvIdx` can't form refcount cycles.
- **Deterministic identity.** Two `OverloadSetT`s with the same `EnvIdx` hash-match without depending on heap addresses, so interning stays reproducible.

Caveat: `TemplatasStoreT` lookup performance — if we want `HashMap<INameT, IEnvEntryT>` instead of slice+linear-scan, envs themselves need to be off the bump arena into a drop-honoring container (`typed-arena::Arena<IEnvT>`, `Vec<Box<IEnvT>>`, or similar) so their internal `HashMap`s get dropped properly. Sub-decision during the refactor; not a blocker either way.

### Typing order / worklist semantics

Scala's typing pass runs in three phases (confirmed by audit of `Compiler.scala`):

1. **Indexing phase.** All top-level structs and interfaces are declared; outer envs created. Allocates into `'out`.
2. **Compiling phase.** Struct/interface bodies fully compiled (members resolved, inner envs created). Method headers deferred onto `CompilerOutputs.deferredFunctionCompiles`. Struct definitions land in `'out`.
3. **Deferred worklist phase.** `CompilerOutputs` holds two queues — `deferredFunctionCompiles` (function headers) and `deferredFunctionBodyCompiles` (bodies). A nested loop drains them. Each worklist item represents compiling one top-level denizen's function (or one generic instantiation's body).

**Each worklist item gets its own `'scratch` arena.** When the item returns, its scratchpad drops. Deferred work the item enqueued (new bodies, new instantiations) is processed as separate worklist items with their own fresh scratchpads.

Nested compilation *within* one worklist item (struct instantiation triggered by body typing, lambda compilation within the same top-level function) shares that item's single scratchpad. Lambdas don't need their own scratchpad — they're allocated into the containing top-level function's.

### Interning strategy

Cross-denizen lookups via HashMap keys (`lookupStruct(id)` etc.) need `IdT` to hash/equal consistently across denizens. Two options:

**Option A (recommended):** Single globally-scoped `TypingInterner` for the whole pass. `IdT`, concrete names, interned types all go through one interner with pointer-identity-implies-structural-equality as its invariant. Ptr-eq `Hash`/`Eq` works everywhere. Allows two-tier arena for envs/templatas while keeping the type/name identity model simple. This matches Slab 4's interner implementation, so no long-term shift is required.

**Option B:** Per-denizen interner. Requires switching `IdT`, heavy-templata, and container-wrapper `Hash`/`Eq` from `std::ptr::eq` to structural. Enables fully-independent per-denizen arenas for interned types, but costs a refactor of ~10 `Hash`/`Eq` impls and adds a structural-hash cost to every HashMap operation. Not worth the effort unless parallel per-denizen typing becomes a goal (see "What this doesn't give").

Long-term target uses **Option A**. Per-denizen scratchpads bound the high-churn data (envs, `NodeEnvironmentT` mutation) without needing per-denizen interning of stable types.

---

## Required refactors to reach the target

1. **`FunctionHeaderT.maybeOriginFunctionTemplata` → `maybeOriginFunctionA: Option<&'s FunctionA<'s>>`.** Audit confirmed no consumer touches `outer_env`; all readers (`EdgeCompiler`, `CompilerErrorHumanizer`, `FunctionBannerT.equals`) access only `FunctionA` fields. Narrow refactor (~5 call sites). Unblocks `FunctionTemplataT` for scratchpad.

2. **Delete `envByFunctionSignature`** in `CompilerOutputs`. Dead code per audit — the only accessor is commented out.

3. **Implement `TypingInterner` bodies globally.** `intern_id` / `intern_struct_tt` / etc. — the shape is already right in `typing_interner.rs`, the bodies are `panic!()` placeholders that need bump-alloc + hashbrown wiring. Part of Slab 4 anyway.

4. **Keep `BuildingFunctionEnvironmentWithClosuredsT` in `'out`.** `EdgeCompiler.lookForOverride` uses this env as a live symbol table — it's passed as parent to `GeneralEnvironmentT.childOf` and various resolvers do implicit lookups through it. Refactoring to a small side-struct is harder than it first looks. Accept that this one BuildingEnv lives in outputs alongside the skeleton envs.

---

## Migration path: one post-Slab-8 slab

Scope:

1. Stand up the `'scratch` arena type + side table infrastructure parallel to existing `'t` arena. `Compiler` gets a new `current_denizen_scratchpad: Option<&'scratch Bump>` field + `env_table: Vec<&'scratch IEnvironmentT>`.
2. Split the storage target: the existing `TypingInterner<'t>` becomes `TypingInterner<'out>` for everything that escapes, unchanged in shape. Scratchpad-class types get scratchpad-arena alloc methods.
3. Flip scratchpad-class types to allocate in `'scratch`. Every env field that was `&'t` becomes `&'scratch` or `EnvIdx`; each choice documented.
4. Apply refactor #1 (`maybeOriginFunctionA`) to sever `FunctionTemplataT` from its current `'t` home.
5. Apply refactor #2 (delete dead code).
6. Wire the worklist-item loop to push/pop a scratchpad per item: create arena, compile, drop arena, move to next item.
7. Optional follow-up: move scratchpad envs off the arena into a drop-honoring container (`typed-arena` or `Vec<Box<_>>`) if `HashMap` in `TemplatasStoreT` is desired.

Each step is independently verifiable. Tests should stay green throughout if the commits are sequenced right.

---

## What this gains

- **Per-denizen memory bound.** Peak memory during function body typing bounded by the function's working set + skeleton envs, not the whole codebase.
- **Clearer Scala parity.** Each worklist item is self-contained; Scala's implicit per-compile state maps onto a literal per-compile arena.
- **Clean `bumpalo`-compatible cleanup.** Scratchpad drops cleanly; outputs arena drops at pass end. No `Rc` gymnastics.
- **`HashMap`-in-`TemplatasStoreT`** if we go the extra mile of pulling scratchpad envs off the arena into a drop-honoring container. Lookup cost drops from O(n) to O(1) for the scratchpad envs that typically see the most lookups.

## What this doesn't give

- **Parallel per-denizen typing.** Not impossible but requires switching `Rc` → `Arc`, making the outputs interner `Sync`, and handling `CompilerOutputs`'s cross-denizen HashMap-based queries (`lookupStruct` etc.) with shared mutable state. Deferred past this refactor.
- **Early drop of in-progress envs.** Scratchpad drops at worklist-item boundary; we don't selectively drop unused envs mid-item. Would need reachability tracking we don't have. Acceptable for batch typing.
- **Lifetime-param reduction.** Envs still hold `&'s FunctionA`, `IdT<'s,'out>`, etc. Types carry `<'s, 'out, 'scratch>` in the long-term shape — one more lifetime parameter than today's `<'s, 't>`. Tolerable but a visible cost at every signature.

## Why not `Rc` + side-table (the earlier intermediate proposal)?

An intermediate design considered storing all envs in `Vec<Rc<IEnvironmentT>>` on `Compiler`, with arena structs holding `EnvIdx`. The per-denizen design strictly supersedes it because:

- Rc+side-table treated all envs as one tier. It didn't handle the cross-denizen skeleton-vs-working split that the audit revealed. Some envs genuinely need to live in the global outputs tier; `Rc` everywhere doesn't capture that.
- Per-denizen scratchpad has cleaner cleanup (drop a bump, done) than `Rc` refcount cascades.
- `Rc` has real per-access cost (refcount, indirect pointer); `EnvIdx` has a simple array lookup; `&'scratch` has zero overhead. Per-denizen design uses `&'scratch` for most env access and reserves `EnvIdx` for the arena-struct→env crossing.
- Per-denizen interning / deduplication of scratchpad data fits naturally; with `Rc`, there's no natural boundary for scratchpad-only vs shared.

The `EnvIdx` side-table concept survives into the per-denizen design — it's used for the arena-struct-holding-env-reference case. But the broader framing is different.

---

## Further future direction: LSP support

The per-denizen arena design is step 1 on a longer trajectory toward supporting a language server (long-running process, incremental recompilation on edit, hover/goto-def responsiveness). This section captures the direction; concrete design is deferred past the per-denizen refactor.

### Why per-denizen arenas are load-bearing for LSP

Each top-level denizen's `'out` slab and `'scratch` arena form a natural invalidation unit. Source change to denizen A → drop A's slabs → recompile A. Other denizens' outputs stay valid unless A's public surface changed and cascaded. Without per-denizen arenas, the whole typing pass re-runs on every edit, which doesn't scale past small projects.

### Remaining pieces beyond per-denizen arenas

1. **`DefId`-indexed cross-denizen references.** Per-denizen `'out` slabs drop independently. Rust's lifetime system can't express "A holds `&'out_B FunctionHeaderT` and `'out_B` may drop without taking `'out_A` with it." Cross-denizen refs become `(DenizenId, u32)` indices resolved via a top-level `CompilerState`. Lookup returns `&'slab T` for temporary borrow, never stored. This is rustc's `DefId` / `TyCtxt` architecture.

2. **Shatter `GlobalEnvironment` into DefId-keyed registries.** Today's single `GlobalEnvironmentT` struct becomes a set of sparse tables on `CompilerState` — `HashMap<PackageId, &'out_pkg PackageEnvInfo>`, `HashMap<MacroId, MacroKind>`, etc. Each entry is replaceable independently. Per-package `'out` slabs drop with their entries. The "GlobalEnvironment" concept persists conceptually but isn't a struct anymore.

3. **Split interner into local + global with single-writer cleanup.** See below — this has enough shape worth detailing.

4. **Periodic full global-interner rebuild.** The global interner grows monotonically within an LSP session. Rather than per-entry refcounting, periodic rebuild: walk live denizens, construct a fresh global interner holding only reachable entries, atomic-swap. Can run as background work while the current interner serves ongoing compiles. Trigger mechanics (memory threshold, explicit signal, etc.) and swap protocol are deferred to a future designer.

### Interner design: local + global with cleanup promotion

**Model:**

- **Global interner** persists across compiles. Indexed by content; entries are canonical across the whole session.
- **Per-denizen local interner** is transient — lives for one compile, drops when the denizen compile ends.

**Lookup order during compilation:**

1. When code calls `intern_<T>(val)`, the local interner checks its own map first.
2. On local miss, fall through to the global interner (lookup only — no write).
3. On global miss, intern into local. The value is local-only until cleanup.

**Cleanup (promotion) at denizen compile end:**

A copy-walk over the denizen's output artifacts (FunctionHeaderT, StructDefinitionT, etc. — anything escaping into HinputsT):

1. Deep-walk outputs, recursively visiting every field.
2. For each encountered interned value: look up in global. On miss, intern into global now. On hit, use the existing global entry.
3. Build fresh output artifacts in a new arena slab, with all interior pointers rooted in global-interned entries.
4. Drop the denizen's local arena + local interner. Everything that wasn't externally-visible dies with it.

This is the **only** path by which values enter the global interner — cleanup is the single writer. Simplifies reasoning: during compilation, global is read-only; between compilations, cleanup writes. Fits naturally with single-threaded execution today; adapts cleanly to parallel compilation later (serialize cleanups, parallelize compiles).

**Externally-visible criterion:** transitively reachable from the denizen's output artifacts. Anything the copy-walk touches gets promoted; anything it doesn't gets dropped with the local arena. No explicit tagging needed — reachability defines the set.

### LSP result-return timing

Two kinds of results from a denizen compile; they return at different times:

- **Diagnostics (errors, warnings)** return *before* cleanup. They reference source locations + semantic descriptions, not compiler types — no promotion needed. Time-critical for editor squigglies.
- **Typed results** (AST for hover, type info for goto-def, HinputsT fragments) return *after* cleanup. LSP consumers hold them across edits, so they need to live in global-interned form to survive the denizen's local arena drop.

### Identity strategy: fully-qualified name

Each top-level denizen has a stable `DefId` = its fully-qualified name (package path + item name). Stable across compiles, renaming changes identity (acceptable — a rename is semantically a delete + create). Source-location-based IDs were considered and rejected as too fragile (reordering code shouldn't change identity). UUID-assigned-on-first-compile was considered too complex.

### Denizen unit: top-level only

One `'out` slab and one `'scratch` arena per **top-level denizen** — top-level function, struct, interface, or impl. Nothing smaller. Specifically:

- Lambdas share their containing top-level function's arenas (already decided in the migration-phase design).
- **Monomorphizations do not get their own denizen status.** LSP and monomorphization are orthogonal. Typing produces generic `FunctionDefinitionT` at top-level-function granularity; monomorphization happens post-typing in the instantiator and is not part of the LSP incrementality story. Whether and how the instantiator participates in LSP is a separate concern, out of scope here.

### Deferred decisions

The following are left to the future designer who actually implements LSP mode:

- Global-interner purge trigger and background rebuild protocol.
- Atomic swap mechanics between old and new global interners.
- Parallelism. Per-denizen arenas and single-writer cleanup naturally permit parallel compilation with serialized cleanup, but sequential single-threaded is the current and near-term target. Parallelism is a future goal, not a design constraint now.
- Instantiator incrementality (if monomorphization is made incremental, it's a separate layer over LSP-ready typing).

---

## See also

- `docs/architecture/typing-pass-design-v3.md` Part 1 — current typing-pass arena architecture.
- `FrontendRust/docs/reasoning/arena-deterministic-maps.md` — why arena-backed maps don't exist for us; part of why envs-in-arena forces slice-based `TemplatasStoreT`.
- `FrontendRust/docs/reasoning/idt-typed-view-alternatives.md` — sister doc recording a similar deferred-alternatives decision for `IdT`.
- `Luz/shields/ArenaAllocatedStructsShouldNotContainMallocdCollections-AASSNCMCX.md` — the shield forcing slice-based `TemplatasStoreT` today; lifts when envs move off arena.
- `FrontendRust/docs/shields/ArenaTypesDontClone-ATDCX.md` — the shield that already carves out "working state (envs, solver types) may have `Clone`-without-`Copy`," anticipating the move to heap-storage working state.
