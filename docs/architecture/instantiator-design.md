# Instantiating Pass Design

Architecture and design decisions for the instantiating pass. Companion to `docs/architecture/typing-pass-design-v3.md` and `docs/architecture/simplifier-design.md`.

This pass takes typing-pass output (`HinputsT<'s, 't>`) and produces monomorphized output (`HinputsI<'s, 'i>`) for the simplifying pass. It is the pass that resolves generics into concrete instantiations: every templated function/struct/interface gets one concrete instance per reachable use-site, generic placeholders become concrete types, bound arguments get threaded into v-tables, and region modes get collapsed from `sI` (still-placeholdered) through `nI` (per-denizen renumbered) to `cI` (concrete output-ready). Tree-shaking falls out of reachability — uncalled overloads never appear in `HinputsI`.

The Scala source of truth is `Frontend/InstantiatingPass/src/dev/vale/instantiating/`. The Rust implementation lives in `FrontendRust/src/instantiating/`.

---

## Part 1: Arena and Lifetime Model

The instantiator runs at the boundary of two arenas — it reads from `'t` (typing) and writes into `'i` (instantiating). Both arenas survive past the pass; instantiator output is consumed by the simplifying pass without any re-interning at the I→H boundary.

### 1.1 Four Arenas

By the time the instantiator runs, all four input arenas are live:

- **`'p` — Parser arena** (`ParseArena<'p>`). Read-only.
- **`'s` — Scout arena** (`ScoutArena<'s>`). Read-only; the instantiator only ever reads scout-interned names, source strings, and code locations.
- **`'t` — Typing arena** (`TypingInterner<'s, 't>`). Read-only input: `HinputsT<'s, 't>` and everything it transitively reaches (`IdT`, `KindT`, `ITemplataT`, `PrototypeT`, env structs, expression AST).
- **`'i` — Instantiating arena** (`InstantiatingInterner<'s, 'i>`). The instantiator's allocator. Holds interned monomorphized types (region-aware names, kind payloads, prototypes, signatures), the monomorphized definitions (`StructDefinitionI`, `InterfaceDefinitionI`, `FunctionDefinitionI`), and the instantiated expression AST.

`InstantiatingArena<'i>` (`instantiating_arena.rs`) is a thin newtype around `&'i Bump`. `InstantiatingInterner<'s, 'i>` (`instantiating_interner.rs:44`) wraps the bump plus a `RefCell<Inner>` holding 12 HashMap families (see §4).

### 1.2 Lifetime Invariants

1. **`'s: 'i`** — interner-level invariant, carried by every output type that holds `&'s` refs into scout-arena data (most of them).
2. **`'s: 't`** — inherited from the typing arena. The combined where-clause on `InstantiatorI` is `where 's: 't, 's: 'i`.
3. **`'t` and `'i` are siblings** — neither outlives the other; the design carries no `'t: 'i` or `'i: 't` bound. The `InstantiatingInterner<'s, 'i>` deliberately does NOT carry `'t` (load-bearing — see file-header comment at `instantiating_interner.rs:14-16`: "`HinputsI` needs to outlive the typing arena"). This means a future variant of the pipeline *could* drop the typing arena before consuming `HinputsI`, even though the current canonical driver doesn't take advantage of that.
4. **Never `'static`.** All data lives in `'p`/`'s`/`'t`/`'i` or on the stack. `&'static T` would break pointer-equality for interned types (NUSLX).
5. **Arena borrow convention.** Arena parameters take a short borrow lifetime — `&InstantiatingInterner<'s, 'i>` or `&'ctx InstantiatingInterner<'s, 'i>`, never `&'i InstantiatingInterner<'s, 'i>`. `interner.intern_*` methods return `&'i T` from a `&self` borrow.
6. **AASSNCMCX.** No `Vec`/`HashMap`/`String` inside arena-allocated types. Use `&'i [T]` slices and `ArenaIndexMap<'i, K, V>`. Driver structs (`InstantiatedOutputsI`, `InstantiatorI`) live on the stack and may use plain `IndexMap`/`Vec` — same exception as typing's `CompilerOutputs`.

### 1.3 Arena Construction & Drop Order

The canonical driver `FrontendRust/src/pass_manager/pass_manager.rs:807-810` constructs bumps in this order:

```rust
let scout_bump        = bumpalo::Bump::new();
let typing_bump       = bumpalo::Bump::new();
let hammer_bump       = bumpalo::Bump::new();
let instantiating_bump = bumpalo::Bump::new();
```

Rust drops locals in reverse declaration order, so at end-of-scope: `'i` dies first, then `'h`, then `'t`, then `'s`. Hammer and instantiating are siblings; both drop before typing, which drops before scout. There are no explicit `drop()` calls — the order is purely lexical.

### 1.4 Where Each Type Lives

| Type | Lifetimes | Arena |
|---|---|---|
| `HinputsI` | `<'s, 'i>` | `'i` for the struct itself; holds `&'i` slices + `ArenaIndexMap` |
| `InstantiationBoundArgumentsI` | `<'s, 'i>` | `'i`, value-type holding `ArenaIndexMap`s |
| `FunctionDefinitionI` | `<'s, 'i>` (no R) | `'i`, allocated; body is always `cI`-flavored |
| `StructDefinitionI`, `InterfaceDefinitionI` | `<'s, 'i, R>` | `'i`, allocated |
| `IdI`, `INameI`, `ITemplataI` | `<'s, 'i, R>` | `'i`; names interned, IdI/templata not yet sealed |
| `KindIT` | `<'s, 'i, R>` | inline Copy Polyvalue wrapper; compound payloads `&'i`-interned |
| `CoordI` | `<'s, 'i, R>` | inline Copy struct (ownership + kind, no region field) |
| `PrototypeI`, `SignatureI` | `<'s, 'i, R>` | `'i`, interned, `MustIntern` seal |
| `StructIT`, `InterfaceIT`, `StaticSizedArrayIT`, `RuntimeSizedArrayIT` | `<'s, 'i, R>` | `'i`, interned, `MustIntern` seal |
| Expression AST (`ReferenceExpressionIE`/`AddressExpressionIE`/`ExpressionIE`) | `<'s, 'i, R>` (always used at `cI`) | `'i`, allocated, not interned |
| `EdgeI`, `InterfaceEdgeBlueprintI` | `<'s, 'i>` | `'i`, allocated |
| `KindExportI`, `FunctionExportI`, `KindExternI`, `FunctionExternI` | `<'s, 'i>` | `'i`, allocated |
| `DenizenBoundToDenizenCallerBoundArgI` | `<'s, 't, 'i>` | Stack-owned; uses heap `IndexMap` |
| `InstantiatedOutputsI` | `<'s, 't, 'i>` | Stack-owned accumulator (mutable; heap `IndexMap`) |
| `InstantiatorI` (god struct) | `<'s, 'ctx, 't, 'i>` | Stack |
| `InstantiatedCompilation` (driver) | `<'s, 'ctx, 't, 'i, 'p>` | Stack |

### 1.5 Mutual-Recursion Shape

The I-type graph is mutually recursive but every edge is `&'s`, `&'i`, or a `<'s, 'i, R>`-parameterized inline value: `CoordI<R> → KindIT<R> → StructIT<R> → IdI<R> → INameI<R> → ITemplataI<R> → CoordI<R>`. Same-type self-recursion (e.g. `ForwarderFunctionNameI.inner: IFunctionNameI`) resolves via the inline 16-byte sub-enum. No `Box`/`Vec` needed in arena types.

### 1.6 Why a Separate `InstantiatingInterner`

Scala used one `Interner` for the entire pipeline. The Rust port splits into per-pass interners (`ScoutArena`, `TypingInterner`, `InstantiatingInterner`, `HammerInterner`) so each arena drops independently. The instantiator holds **both** the typing-pass interner (for reading T-side data via helpers like `Compiler::get_super_template`) **and** the instantiating-pass interner (for emitting I-side data). The dual-interner pattern is a Rust-specific adaptation, not a design choice.

---

## Part 2: The God Struct (`InstantiatorI`)

### 2.1 Architecture

Scala's `Instantiator` class (already a single big class in Scala — no sub-instantiators to collapse) ports to `InstantiatorI<'s, 'ctx, 't, 'i>` at `instantiator.rs:235`:

```rust
pub struct InstantiatorI<'s, 'ctx, 't, 'i>
where 's: 't, 's: 'i,
{
    pub opts: &'ctx GlobalOptions,
    pub interner: &'ctx InstantiatingInterner<'s, 'i>,
    pub typing_interner: &'ctx TypingInterner<'s, 't>,
    pub keywords: &'ctx Keywords<'s>,
    pub hinputs: &'ctx HinputsT<'s, 't>,
}
```

Immutable-only configuration plus the input `HinputsT` (read-only). Mutable state threads through `&mut InstantiatedOutputsI<'s, 't, 'i>` on every translation method call.

`hinputs` lives on the god struct (not threaded through every call) because every `translate_*` method needs T-side lookups (e.g. `self.hinputs.functions.iter().find(...)` inside `find_function`/`find_struct`/`find_impl`); lifting it to the struct removes it from ~50 method signatures.

### 2.2 `&self` + `&mut monouts` Enables Re-entrancy

Deep mutual recursion (e.g. `translate_ref_expr → translate_function_call → translate_prototype → translate_function → translate_ref_expr`) works because `&self` is shared-borrowed and `&mut monouts` is re-borrowed at each call level. Same idiom as the typing pass.

### 2.3 ~52-55 `translate_*` Methods on `InstantiatorI`

`InstantiatorI` exposes around 52-55 `translate_*` methods (raw count of `pub fn translate_` lines: 55; deduped by name: 54; subtracting the free-function entry `translate` and the renamed-from-Scala `translate_method`: ~52). This matches the Scala side's ~57 `translate*` methods 1:1, with a small number of Scala helpers (`translateRegionId`, `translateRegionName`, overloaded `translateId` variants) not yet ported.

### 2.4 Method vs Free Function

Most translation lives as methods on `impl InstantiatorI`. **Free-function modules** for translation helpers:

- **`reintern.rs`** — T→I boundary helpers (`name`, `id`, `prototype`, `signature`, `coord`, `kind`, `struct_payload`, `interface_payload`, `static_sized_array_payload`, `runtime_sized_array_payload`, `templata`). 11 free functions, **all `panic!()` stubs as of current state**. Architectural choice: free functions, because the interner stays pure-`'i`-side and the instantiator is stateful, so the bridge doesn't fit on either.
- **`region_counter.rs`** — `count_*` helpers walking `sI` trees. 37 free functions, plus a tiny `CounterI` helper struct with 3 methods (`new`, `count`, `assemble_map`).
- **`region_collapser_individual.rs`** — 27 `collapse_*` free functions (sI→cI, fresh per-call map).
- **`region_collapser_consistent.rs`** — 30 `collapse_*` free functions (sI→nI, externally supplied map).

Everything else (the ~52 translation methods) lives on `impl InstantiatorI` and takes `&self`. In particular, `translate_id`, `translate_name`, `translate_export_name`, `translate_export_template_name`, `translate_name_substituting`, `translate_id_from_substitutions` are **methods**, not free functions — most need the interner via `self.interner.intern_*`.

### 2.5 `DenizenBoundToDenizenCallerBoundArgI`

A companion struct (`instantiator.rs:53-56`) threaded as `&` arg through ~50 method signatures:

```rust
pub struct DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>
where 's: 't, 's: 'i
{
    pub func_id_to_bound_arg_prototype:
        IndexMap<IdT<'s, 't>, &'i PrototypeI<'s, 'i, sI>>,
    pub bound_param_impl_id_to_bound_arg_impl_id:
        IndexMap<IdT<'s, 't>, IdI<'s, 'i, sI>>,
}
```

Two `IndexMap`s keyed by typing-pass id (`IdT`, the bound-parameter side) and valued by sI-mode instantiator id/prototype (the resolved caller-side). The `.plus()` method unions two of these via `union_maps_expect_no_conflict`. Carries `<'s, 't, 'i>` — three lifetimes — because keys are `'t`-side and values are `'i`-side.

### 2.6 No `IEnvironmentI`

The instantiating pass has **no environment type**, deliberately. After monomorphization, scope resolution is already settled — every templata reference was pinned to a concrete instantiated value during typing. The instantiator's analogue is the **substitution map** `IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>` mapping typing-pass placeholders to concrete instantiated templatas, threaded explicitly through every method that needs it alongside `DenizenBoundToDenizenCallerBoundArgI`.

Don't introduce an `IEnvironmentI` even when it would clean up signatures — Scala parity wins.

---

## Part 3: `InstantiatedOutputsI` (the Monouts Accumulator)

### 3.1 Structure

Stack-owned mutable accumulator threaded through every method. Carries `<'s, 't, 'i>`. From `instantiator.rs:88-108`, 19 fields:

```rust
pub struct InstantiatedOutputsI<'s, 't, 'i> where 's: 't, 's: 'i {
    // Definition outputs (keyed by cI ids)
    pub functions: IndexMap<IdI<'s, 'i, cI>, &'i FunctionDefinitionI<'s, 'i>>,
    pub structs: IndexMap<IdI<'s, 'i, cI>, &'i StructDefinitionI<'s, 'i, cI>>,
    pub interfaces_without_methods: IndexMap<IdI<'s, 'i, cI>, &'i InterfaceDefinitionI<'s, 'i, cI>>,

    // Mutability metadata
    pub struct_to_mutability: IndexMap<IdI<'s, 'i, cI>, MutabilityI>,
    pub interface_to_mutability: IndexMap<IdI<'s, 'i, cI>, MutabilityI>,
    pub impl_to_mutability: IndexMap<IdI<'s, 'i, cI>, MutabilityI>,

    // Bound storage (keyed by sI ids; abstract_func by cI)
    pub struct_to_bounds:        IndexMap<IdI<'s, 'i, sI>, DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>>,
    pub interface_to_bounds:     IndexMap<IdI<'s, 'i, sI>, DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>>,
    pub impl_to_bounds:          IndexMap<IdI<'s, 'i, sI>, DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>>,
    pub abstract_func_to_bounds: IndexMap<IdI<'s, 'i, cI>, (DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>,
                                                            &'i InstantiationBoundArgumentsI<'s, 'i>)>,

    // Edge/dispatch tables
    pub interface_to_impls: IndexMap<IdI<'s, 'i, cI>, Vec<(IdT<'s, 't>, IdI<'s, 'i, cI>)>>,
    pub interface_to_abstract_func_to_virtual_index:
        IndexMap<IdI<'s, 'i, cI>, IndexMap<PrototypeI<'s, 'i, cI>, usize>>,
    pub impls: IndexMap<IdI<'s, 'i, cI>,
                        (ICitizenIT<'s, 'i, cI>,
                         IdI<'s, 'i, cI>,
                         DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>,
                         InstantiationBoundArgumentsI<'s, 'i>)>,
    pub interface_to_impl_to_abstract_prototype_to_override:
        IndexMap<IdI<'s, 'i, cI>,
                 IndexMap<IdI<'s, 'i, cI>,
                          IndexMap<PrototypeI<'s, 'i, cI>, PrototypeI<'s, 'i, cI>>>>,

    // Worklists (drained FIFO via Vec::remove(0))
    pub new_impls: Vec<(IdT<'s, 't>, IdI<'s, 'i, nI>, InstantiationBoundArgumentsI<'s, 'i>)>,
    pub new_abstract_funcs: Vec<(PrototypeT<'s, 't>, PrototypeI<'s, 'i, nI>, usize, IdI<'s, 'i, cI>,
                                  InstantiationBoundArgumentsI<'s, 'i>)>,
    pub new_functions: Vec<(PrototypeT<'s, 't>, PrototypeI<'s, 'i, nI>, InstantiationBoundArgumentsI<'s, 'i>,
                             Option<DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i>>)>,

    // Externs (final outputs)
    pub kind_externs: Vec<KindExternI<'s, 'i>>,
    pub function_externs: Vec<FunctionExternI<'s, 'i>>,
}
```

### 3.2 Worklist-Driven Discovery Loop

`translate_method` runs a single while-loop drainage at `instantiator.rs:406-436`:

```rust
loop {
    if let Some(item) = monouts.new_functions.first() { ... self.translate_function(...); }
    else if let Some(item) = monouts.new_impls.first() { ... self.translate_impl(...); }
    else if let Some(item) = monouts.new_abstract_funcs.first() { ... self.translate_abstract_func(...); }
    else { break; }
}
```

Each translate may push more work onto the three queues. Worklist completion = fixpoint = monomorphization complete.

The queues use `Vec<(...)>` not `VecDeque`, drained via `Vec::remove(0)` (FIFO). Scala uses `mutable.Queue` with `dequeue()`; the Rust port chose `Vec`+`remove(0)` for simplicity, accepting O(n) drain on small queues. Semantic FIFO behavior is preserved.

Scala had additional `newStructs`/`newInterfaces` queues; these are preserved as commented-out Scala in the Rust audit trail but not used — the Rust port translates structs and interfaces eagerly as they're encountered (`instantiator.rs:407-417` comment: "We make structs and interfaces eagerly as we come across them").

### 3.3 Why `IndexMap`, Not `HashMap`, Not `ArenaIndexMap`

- `IndexMap` (not `HashMap`): insertion-ordered iteration is **load-bearing** for output determinism. `HinputsI.functions` etc. must come out in a stable order so wire-format goldens stay byte-stable.
- Heap `IndexMap` (not `ArenaIndexMap`): `InstantiatedOutputsI` is mutated by the pass — values are inserted, queues are drained, v-tables are populated. Arena-backed maps are append-only once frozen. The accumulator is stack-owned so heap allocation is fine (AASSNCMCX exception, same as `CompilerOutputs`).

The per-definition payloads stored *inside* the IndexMaps are arena-allocated (`&'i FunctionDefinitionI`, etc.) and survive past the accumulator's drop.

### 3.4 Keys Are `IdI<R>` By Value, Not `PtrKey`

Every key in `InstantiatedOutputsI` is an `IdI<R>` or `PrototypeI<R>` by value, not wrapped in `PtrKey<'i, T>`. I-side interned types have derived `PartialEq`/`Eq`/`Hash` that delegate to interned `&'i` ref fields (slice and inner-enum compare → effectively ptr-eq on canonical payloads) and `&'s` ref fields (ptr-eq), so canonical-identity hashing falls out without a `PtrKey` wrapper.

This differs from typing-pass `CompilerOutputs`, which does use `PtrKey<'t, T>` in several places.

### 3.5 `cI`-Keyed vs `sI`-Keyed Maps

The accumulator uses two flavors of keys deliberately:

- **`cI`-keyed**: any map whose values are *final* output (`functions`, `structs`, `interfaces_without_methods`, `*_to_mutability`, `interface_to_impls`, `interface_to_abstract_func_to_virtual_index`, `impls`, `abstract_func_to_bounds`, `interface_to_impl_to_abstract_prototype_to_override`).
- **`sI`-keyed**: any map whose values are *per-instantiation substitution data* (`struct_to_bounds`, `interface_to_bounds`, `impl_to_bounds`). Bound resolution happens *before* region collapse, so its keys are pre-collapse.

There is no `function_to_bounds` field; per-function bound resolution flows through the explicitly-threaded `DenizenBoundToDenizenCallerBoundArgI` argument rather than a stored map.

If you find yourself looking up a `cI` id in an `sI`-keyed map (or vice versa), something upstream went wrong — don't paper over it; trace back to find the collapse-site boundary.

### 3.6 Speculative Writes Don't Apply

Unlike typing-pass `CompilerOutputs` (which speculates during overload resolution), the instantiator never speculates. Every write is the result of a confirmed reach from an exported entry point. No rollback machinery.

### 3.7 The One Named Mutation API

`add_method_to_v_table(impl_id, super_interface_id, abstract_prototype_c, override_prototype_c)` at `instantiator.rs:187-194` is the one named mutation method. All other mutations are direct field manipulation (`monouts.functions.insert(...)`, `monouts.new_functions.push(...)`).

---

## Part 4: Interning Surface — `InstantiatingInterner`

### 4.1 The 12-HashMap Structure

`instantiating_interner.rs:51-69`. **12 HashMaps = 4 logical types × 3 region modes**:

| | `sI` | `nI` | `cI` |
|---|---|---|---|
| Kind payloads (Struct/Interface/SSA/RSA) | `kind_payload_val_to_ref_si` | `..._ni` | `..._ci` |
| Prototype | `prototype_val_to_ref_si` | `..._ni` | `..._ci` |
| Signature | `signature_val_to_ref_si` | `..._ni` | `..._ci` |
| Name (72 variants) | `name_val_to_ref_si` | `..._ni` | `..._ci` |

This is the architectural symmetry that pays for the region-mode-as-generic-parameter design: every interned type exists in three pointer-distinct flavors so cross-mode references don't accidentally alias.

The 3 separate per-mode maps stay even though `nI`/`cI` are type aliases for `sI` at runtime (§5.2) — the type-level separation is the load-bearing part, and the architect's call (Slab 16a/16b) was to keep them separate so future runtime semantics on R won't require reshaping the interner.

### 4.2 Family-Level Methods (12) + Per-Concrete Wrappers (~216)

Each (type × region mode) pair has one family-level dispatch method (e.g. `intern_kind_payload_si`, `intern_prototype_ci`, `intern_name_ni`). On top of those, each concrete variant gets typed convenience wrappers — 4 kind payloads × 3 modes = 12 kind wrappers; 72 concrete `*NameI` types × 3 modes = 216 name wrappers.

Hand-writing 216 wrappers would be miserable; they're generated by macros:

- `impl_intern_name_wrappers!` (lines 76-99) — standard variant for names with `<'s, 'i, R>`.
- `impl_intern_name_wrappers_5lt!` (lines 103-126) — 5-lifetime variant for the 14 name structs that hold `ITemplataI` references and need `'p` and `'t` in scope.

Both macros use **`paste!`** (`::paste::paste!` at lines 78 and 105) to construct the per-mode method names (`[<$method _si>]`, `[<$method _ni>]`, `[<$method _ci>]`). The `paste` crate is a real build dependency (`Cargo.toml: paste = "1"`).

The third macro `alloc_name_canonical!` (lines 132-212) is a giant match shared across all three per-mode family methods, with one arm per name variant that allocates the val payload into the arena and wraps it as the matching canonical-enum variant. R stays phantom — same code for sI/nI/cI.

### 4.3 Val/Ref Pair Pattern

Same as typing and simplifying. Each interned type `XI<'s,'i,R>` has a sibling `XValI<'s,'i,R>` (Value-type, derives Hash/Eq) used as the HashMap query key. The canonical `&'i XI` has `_must_intern: MustIntern(())` seal field. Construction of `MustIntern` is private to the interner module, so only `intern_*` methods can produce sealed canonicals (per @SICZ).

### 4.4 Arena Utility API

`instantiating_interner.rs:237-256` exposes raw arena access:

- `bump() -> &'i Bump`
- `alloc<T>(T) -> &'i mut T`
- `alloc_slice_copy<T: Copy>(&[T]) -> &'i [T]`
- `alloc_slice_from_vec<T>(Vec<T>) -> &'i [T]`
- `alloc_index_map<K, V>() -> ArenaIndexMap<'i, K, V>`
- `alloc_index_map_from_iter<K, V, I>(I) -> ArenaIndexMap<'i, K, V>`

### 4.5 Deferred Intern Families

`instantiating_interner.rs:685-687` notes two intern families not yet wired:

- **`intern_id_*`** — `IdI` is allocated but not yet sealed (no `_must_intern` field, no `IdIValI` mirror, no `intern_id_*` methods). Adding it requires settling `IdI` shape stability. The contrast with the sibling types is sharp: `IdT` (typing-side) **is** sealed, as are all the `*IT`/`PrototypeI`/`SignatureI`/`*NameI` types on the I-side. Until `IdI` is sealed, `IdI`-keyed maps fall back to structural comparison rather than canonical-pointer equality — slightly slower but correct (because the slice and inner-name fields of `IdI` are already canonical-ref).
- **`intern_templata_*`** — intentionally not planned. `ITemplataI` is Value-type per Scala parity (`ITemplataT` likewise — typing pass agrees).

---

## Part 5: The Region-Mode Generic `R: IRegionsModeIT`

This is the instantiator's defining architectural concept.

### 5.1 Three Modes

`ast/types.rs:152-199`. Three region modes, each defined for the type system but only `sI` is a real struct:

- **`sI`** — **Subjective.** "Have I done the bound substitution yet?" Per `reintern.rs:10-11`, instantiation starts in `sI` — the T→I boundary always produces `sI`. Templates with placeholders are substituted with concrete instantiations as data flows `T → sI`. Most translation methods operate in `sI`.

- **`nI`** — **New.** "Stands for new. Serves as a starting point for a new instantiation." (Scala `types.scala:74` verbatim.) The queue-time mode for `new_impls`/`new_functions`/`new_abstract_funcs` after per-denizen region renumbering by `region_collapser_consistent`.

- **`cI`** — **Collapsed.** Fully-instantiated, region-modes fully resolved. The final form of `HinputsI` output is all `cI` (see §6.7).

Definitive name origins: the CCFCTS doc at `docs/InstantiatorRegions.md:325` titled "Can Cast From **Collapsed** To **Subjective**" gives `c` and `s`. The Scala class comment names `n` directly. Pervasive variable names like `returnSubjectiveIT`, `memberSubjectiveIT` confirm `s` = subjective in code usage.

### 5.2 CCFCTS: `nI` and `cI` Are Type Aliases for `sI`

`ast/types.rs:175-196`:

```rust
mod region_mode_sealed { pub trait Sealed {} }
pub trait IRegionsModeIT: region_mode_sealed::Sealed {}

#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
#[allow(non_camel_case_types)]
pub struct sI;
impl region_mode_sealed::Sealed for sI {}
impl IRegionsModeIT for sI {}

// Region modes are covariant in Scala (`nI <: sI`); Rust erases that covariance.
// Per CCFCTS the modes are inert zero-member tags, so `nI`/`cI` alias `sI` to
// make ASTs across modes interchangeable.
#[allow(non_camel_case_types)] pub type nI = sI;
#[allow(non_camel_case_types)] pub type cI = sI;
```

**Only `sI` is a real struct.** `nI` and `cI` are type aliases for `sI`. This is **CCFCTS** — "Can Cast From Collapsed To Subjective" (canonical definition at `docs/InstantiatorRegions.md:325`). The three modes are *inert* at runtime — there's nothing to compare or convert; they're purely compile-time markers.

**Why type aliases instead of newtypes?** Two reasons:

1. Erases Scala's `nI <: sI` covariance subtyping. Rust doesn't have subtyping; type aliases sidestep the issue.
2. Enables `unsafe std::mem::transmute(...)` casts and pointer reinterprets between mode-flavors at well-defined sites.

**The cost.** If anyone adds a member to `sI`/`nI`/`cI`, the aliasing breaks and the transmutes become UB. The zero-member invariant is load-bearing. Sealing (`region_mode_sealed::Sealed`) keeps external code from minting new modes; internal discipline keeps the file from breaking it.

The Scala original was: `class sI() extends IRegionsModeI` / `class nI() extends sI` / `class cI() extends IRegionsModeI` — three distinct classes with `nI <: sI` subtyping. The Rust port erased the distinctness because runtime semantics are nil and the type-level R parameter on each I-suffix type carries enough static distinction.

### 5.3 Cast Sites

Three load-bearing places exploit the aliasing:

- **`instantiator.rs:1131`** in `translate_override`: `let templata_s: ITemplataI<'s, 'i, sI> = unsafe { std::mem::transmute(templata_c) };` — reuses a `cI` templata as `sI` to feed it into the dispatcher substitutions.
- **`instantiator.rs:1607`** in `translate_abstract_func`: `let desired_abstract_prototype_s: PrototypeI<'s, 'i, sI> = unsafe { std::mem::transmute(*desired_abstract_prototype_n) };` — reuses an `nI` prototype as `sI`.
- **`instantiator.rs:1423`** in `translate_impl`: `let impl_id_s: &IdI<'s, 'i, sI> = unsafe { &*(_impl_id_n as *const IdI<'s, 'i, nI> as *const IdI<'s, 'i, sI>) };` — pointer reinterpret because `IdI` is too big to transmute by value cheaply.

The comment near each cast cites CCFCTS.

### 5.4 Which Types Carry R

Almost everything I-suffix carries `R`:

- `CoordI<'s, 'i, R>`, `KindIT<'s, 'i, R>` and all its compound payloads (`StructIT<R>`, `InterfaceIT<R>`, `StaticSizedArrayIT<R>`, `RuntimeSizedArrayIT<R>`).
- `INameI<'s, 'i, R>` and its `INameValI` mirror — 72 variants each.
- All 72 concrete `*NameI` structs.
- `IdI<'s, 'i, R>`.
- `ITemplataI<'s, 'i, R>` (20 variants) plus all payload structs.
- `PrototypeI<'s, 'i, R>`, `SignatureI<'s, 'i, R>`.
- Sub-enum families: `ICitizenIT<R>`, `ISubKindIT<R>`, plus all the name sub-enum families.
- All ~50 expression payload structs plus the three top-level expression wrappers (`ExpressionIE<'s, 'i, R>`, `ReferenceExpressionIE<'s, 'i, R>`, `AddressExpressionIE<'s, 'i, R>`) — see §7.1 for how R is constrained in practice.
- `StructDefinitionI<'s, 'i, R>`, `InterfaceDefinitionI<'s, 'i, R>`.

**Don't carry R:**

- `FunctionDefinitionI<'s, 'i>` — explicitly no R; its body field is hard-coded `ReferenceExpressionIE<'s, 'i, cI>` (`ast/ast.rs:242`).
- Atom enums: `OwnershipI`, `MutabilityI`, `VariabilityI`, `LocationI`.
- `IRegionsModeIT` trait itself.
- `InstantiationBoundArgumentsI<'s, 'i>` — its component slot types reference `sI` explicitly.
- `HinputsI<'s, 'i>` (final output) — each field hard-codes `cI` (see §6.7).
- `EdgeI<'s, 'i>`, `InterfaceEdgeBlueprintI<'s, 'i>`, `KindExternI<'s, 'i>`, `FunctionExportI<'s, 'i>`, `KindExportI<'s, 'i>`, `FunctionExternI<'s, 'i>` — all fixed at `cI` since they're emitted as final output.

### 5.5 The `IdT → IdI<sI> → IdI<nI> → IdI<cI>` Chain

For a single id, the full pipeline is:

1. **`IdT<'s, 't>`** — input from typing pass.
2. **`InstantiatorI::translate_id` / `translate_function_id` / `translate_struct_id` etc.** → produces `IdI<'s, 'i, sI>`. Uses `reintern::*` (currently stubbed) or inline translation. Region mode at the boundary is always `sI`.
3. **`region_collapser_consistent::collapse_id`** (queue-time, when pushing a new instantiation onto the worklists) → `IdI<'s, 'i, nI>`. Uses an externally-supplied per-denizen region renumbering map.
4. **`region_collapser_individual::collapse_id`** (final emission) → `IdI<'s, 'i, cI>`. Builds its own per-node region map via `region_counter::count_*_map(...)`.

This four-stage chain is the architectural reason for the 12-HashMap interner: each stage's output needs to be deduplicated against other outputs at the same stage.

### 5.6 The Two Region Collapsers

Both modules expose ~30 mirror-image free functions (`collapse_prototype`, `collapse_id`, `collapse_function_id`, `collapse_function_name`, `collapse_kind`, `collapse_coord`, `collapse_struct_id`, `collapse_interface_id`, etc.). The split is **two-dimensional**:

| | Input region map | Output mode |
|---|---|---|
| `region_collapser_individual.rs` | builds fresh per-call via `region_counter::count_*_map(...)` | `cI` |
| `region_collapser_consistent.rs` | externally supplied via parameter | `nI` |

Scala header comments at both files spell out the difference:

- Individual: "This one will collapse every node based on only the things it contains. It creates a new collapsing map for each one."
- Consistent: "This one will use one map for the entire deep collapse, rather than making a new map for everything."

Used at different points in the pipeline:

- **`consistent`** runs at *queue-time* — when a `translate_prototype` discovers a new callee, it counts the prototype's regions via `region_counter::count_prototype_map`, calls `region_collapser_consistent::collapse_prototype` to produce the `nI`-form, and pushes that onto `monouts.new_functions`. The shared per-prototype map keeps all parts of the same prototype agreeing on canonical region numbering.
- **`individual`** runs at *final emit* — when finalizing exports, function IDs, or any path that produces directly-emitted `HinputsI` content. Each top-level structure builds its own fresh map, so different exports don't have to coordinate.

The two modules being parallel ~30-function surfaces is a real maintainability cost (adding a new I-side type requires updating both), but the design intent is faithful 1:1 Scala port; consolidating them would diverge from `RegionCollapserIndividual.scala` / `RegionCollapserConsistent.scala`.

### 5.7 `reintern` — The T→I Bridge

`reintern.rs` (123 lines, 11 free functions) is the documented T→I memoization layer. **All 11 functions are currently `panic!()` stubs.** Body migration is a separate phase.

Architectural choice: free functions on `reintern`, not methods on `InstantiatingInterner` (which stays pure-`'i`-side and shouldn't know about `'t`) or on `InstantiatorI` (which is stateful and would entangle the bridge with translation state).

Until reintern bodies are migrated, T→I translation in the live `translate_method` body happens inline inside `translate_*` methods (see `translate_method` body for the pattern — it directly constructs `INameValI::Export(ExportNameI { ... })` instead of calling `reintern::name`).

Region mode at the T→I boundary is **always `sI`**.

---

## Part 6: I-Suffix AST Type Families

Every lifetime+R-parameterized struct has an implicit `where 's: 'i` bound (§1.2). Every `R` parameter has the implicit `R: IRegionsModeIT` bound.

### 6.1 +T-Erasure Principle Inherited From Typing

Same as typing-pass §6.0: Scala's `+T` covariant type parameters are erased to monomorphic widest-form in Rust. Applies uniformly:

- `IdI[+T <: INameI]` → `IdI<'s, 'i, R>` with `local_name: INameI<'s, 'i, R>`.
- `PrototypeI[+T]` → `PrototypeI<'s, 'i, R>` (no inner generic).
- `ITemplataI[+T <: ITemplataType]` → `ITemplataI<'s, 'i, R>` monomorphic.

The R-mode generic does *not* take the place of +T — `R` is for region-mode discrimination; the leaf-name discrimination is done by pattern-matching on `local_name`. Use-site narrowing via `TryFrom`/`try_into().unwrap()`.

### 6.2 `IdI<'s, 'i, R>` — Allocated, Not Sealed

`ast/names.rs:24-29`:

```rust
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct IdI<'s, 'i, R> {
    pub package_coord: &'s PackageCoordinate<'s>,
    pub init_steps: &'i [INameI<'s, 'i, R>],
    pub local_name: INameI<'s, 'i, R>,
}
```

**Currently no `_must_intern` seal** — `IdI` is allocated but not yet sealed, unlike its peers `IdT` (typing) and `IdH` (simplifying). `intern_id_*` methods are in the TODO list (`instantiating_interner.rs:685-687`); the seal happens once `IdI` shape stabilizes. Until then, `IdI`-keyed maps fall back to structural comparison — slightly slower but correct because the slice/local-name fields propagate ptr-eq through the derived impl.

### 6.3 `CoordI<'s, 'i, R>` — Two Fields, Inline Copy

```rust
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct CoordI<'s, 'i, R> where 's: 'i {
    pub ownership: OwnershipI,
    pub kind: KindIT<'s, 'i, R>,
}
```

`ast/types.rs:215-219`. **Exactly two fields: ownership and kind.** No region field. This differs from both the typing-side `CoordT` (which has `region: RegionT`) and the simplifying-side `CoordH` (which has `location: LocationH`). Region information lives in `R` (statically) and inside nested templatas like `CoordTemplataI<R>` (which *does* have a `region` field — but that's the templata wrapper, not the bare `CoordI`).

Small, Copy, derives Eq/Hash, passed by value. Not interned — structural equality.

### 6.4 `KindIT<'s, 'i, R>` — 10 Variants, Inline Wrapper

```rust
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum KindIT<'s, 'i, R> where 's: 'i {
    NeverIT(NeverIT<R>),
    VoidIT(VoidIT<R>),
    IntIT(IntIT<R>),
    BoolIT(BoolIT<R>),
    StrIT(StrIT<R>),
    FloatIT(FloatIT<R>),
    StaticSizedArrayIT(&'i StaticSizedArrayIT<'s, 'i, R>),
    RuntimeSizedArrayIT(&'i RuntimeSizedArrayIT<'s, 'i, R>),
    StructIT(&'i StructIT<'s, 'i, R>),
    InterfaceIT(&'i InterfaceIT<'s, 'i, R>),
}
```

`ast/types.rs:258-269`. **No `KindPlaceholder` variant** — the instantiator's job is to eliminate placeholders by substitution. By the time you have a `KindIT`, every placeholder has been resolved. Placeholder handling lives on the input side: `instantiator.rs:5295` matches `KindT::KindPlaceholder` and looks the id up in the substitutions map.

Primitives (Never/Void/Int/Bool/Str/Float) are inline `TFITCX` Value-types carrying `PhantomData<R>`. The 4 compound payloads (`StructIT`, `InterfaceIT`, `StaticSizedArrayIT`, `RuntimeSizedArrayIT`) are arena-interned and held by `&'i` ref (per @WVSBIZ).

Sub-enum families: `ISubKindIT<'s, 'i, R>` (`:545`) and `ICitizenIT<'s, 'i, R>` (`:559`) — each with `StructIT` and `InterfaceIT` arms.

### 6.5 `INameI` Hierarchy — 72 Variants

`ast/names.rs:135-209`. The wide enum `INameI<'s, 'i, R>` has 72 variants spanning every name kind. Each variant holds a `&'i ConcreteNameI<'s, 'i, R>`.

Sub-enums (each a subset of `INameI`):

- `ITemplateNameI` — 20 variants (the template names).
- `IFunctionTemplateNameI` — 8 variants.
- `IInstantiationNameI` — 19 variants.
- `IFunctionNameI` — 7 variants.
- `IVarNameI` — 17 variants.
- `ICitizenNameI`, `IStructNameI`, `IInterfaceNameI`, `ISubKindNameI`, `ISuperKindNameI`, `IImplNameI`, `IImplTemplateNameI`, `IStructTemplateNameI`, `IInterfaceTemplateNameI`, `ICitizenTemplateNameI`, `ISubKindTemplateNameI`, `ISuperKindTemplateNameI`, `IRegionNameI`.

DAG-parity rule (typing §6.2): a concrete name extending multiple Scala sub-traits gets a variant in each Rust sub-enum. Widening (`From<Narrow> for Wide`) and narrowing (`TryFrom<Wide> for Narrow`) impls are hand-written.

The `INameValI<'s, 'i, R>` mirror has the same 72 variants but holds payloads by value (not `&'i` ref) — it's the interner's HashMap lookup key.

### 6.6 `ITemplataI<'s, 'i, R>` — 20 Variants Including Heavy

`ast/templata.rs:133-154`:

```rust
pub enum ITemplataI<'s, 'i, R> {
    Coord(CoordTemplataI),
    Kind(KindTemplataI),
    RuntimeSizedArrayTemplate(RuntimeSizedArrayTemplateTemplataI),
    StaticSizedArrayTemplate(StaticSizedArrayTemplateTemplataI),
    Function(FunctionTemplataI),                       // heavy
    StructDefinition(StructDefinitionTemplataI),       // heavy
    InterfaceDefinition(InterfaceDefinitionTemplataI), // heavy
    ImplDefinition(ImplDefinitionTemplataI),           // heavy
    Ownership(OwnershipTemplataI),
    Variability(VariabilityTemplataI),
    Mutability(MutabilityTemplataI),
    Location(LocationTemplataI),
    Boolean(BooleanTemplataI),
    Integer(IntegerTemplataI),
    String(StringTemplataI),
    Prototype(PrototypeTemplataI),
    Isa(IsaTemplataI),
    CoordList(CoordListTemplataI),
    Region(RegionTemplataI),                           // instantiating-specific
    ExternFunction(ExternFunctionTemplataI),           // heavy
}
```

20 variants, matching the Scala upstream 1:1. The "heavy" templatas (Function/StructDefinition/InterfaceDefinition/ImplDefinition/ExternFunction) **do** survive into the instantiator — they were carried over by direct port from Scala's `templata.scala`. Heavy templatas don't carry their own bound-arg map; the bound info lives in `InstantiatedOutputsI` keyed by the templata's id.

`Region` is an instantiator-specific variant (Scala parity but the typing-pass `ITemplataT` doesn't have it because regions are inferred during typing without dedicated templatas; the instantiator materializes them).

Note that `CoordTemplataI` does have a `region: RegionTemplataI<R>` field, even though `CoordI` itself does not (§6.3).

### 6.7 `PrototypeI` and `SignatureI` — Interned, Sealed

`ast/ast.rs:667` and `:376`:

```rust
pub struct PrototypeI<'s, 'i, R> {
    pub id: IdI<'s, 'i, R>,
    pub return_type: CoordI<'s, 'i, R>,
    pub _must_intern: MustIntern,
}
```

Both have `*ValI` mirrors and are interned via `intern_prototype_si/ni/ci` and `intern_signature_si/ni/ci`. Sealed with `_must_intern`.

### 6.8 `HinputsI` — Final Output

`ast/hinputs.rs:47-59`:

```rust
pub struct HinputsI<'s, 'i> where 's: 'i {
    pub interfaces: &'i [InterfaceDefinitionI<'s, 'i, cI>],
    pub structs: &'i [&'i StructDefinitionI<'s, 'i, cI>],
    pub functions: &'i [&'i FunctionDefinitionI<'s, 'i>],
    pub interface_to_edge_blueprints:
        ArenaIndexMap<'i, IdI<'s, 'i, cI>, InterfaceEdgeBlueprintI<'s, 'i>>,
    pub interface_to_sub_citizen_to_edge:
        ArenaIndexMap<'i, IdI<'s, 'i, cI>, ArenaIndexMap<'i, IdI<'s, 'i, cI>, EdgeI<'s, 'i>>>,
    pub kind_exports: &'i [KindExportI<'s, 'i>],
    pub function_exports: &'i [FunctionExportI<'s, 'i>],
    pub kind_externs: ArenaIndexMap<'i, &'i StructIT<'s, 'i, cI>, KindExternI<'s, 'i>>,
    pub function_externs: &'i [FunctionExternI<'s, 'i>],
}
```

**Hybrid shape**: definition lists as `&'i` slices, lookup tables as `ArenaIndexMap`. Notable subtlety: `interfaces` holds `InterfaceDefinitionI` **by value** (inline), while `structs` and `functions` hold `&'i T` refs. This asymmetry is because `interfaces` is assembled inline at `instantiator.rs:449-467` from accumulator data, whereas `structs` and `functions` are already-arena-allocated refs pulled from `monouts.structs.values()` / `monouts.functions.values()`.

Every R-parameterized field hard-codes `cI`. `HinputsI` itself doesn't carry an R generic — its contract with the simplifying pass is that Hammer only ever sees `cI`-mode data.

### 6.9 `FunctionDefinitionI` — No R Parameter

```rust
pub struct FunctionDefinitionI<'s, 'i> where 's: 'i {
    pub header: FunctionHeaderI<'s, 'i>,
    pub rune_to_func_bound: ArenaIndexMap<'i, IRuneS<'s>, IdI<'s, 'i, cI>>,
    pub rune_to_impl_bound: ArenaIndexMap<'i, IRuneS<'s>, IdI<'s, 'i, cI>>,
    pub body: ReferenceExpressionIE<'s, 'i, cI>,
}
```

`ast/ast.rs:238`. **No R parameter** — `FunctionDefinitionI` is always `cI`-flavored. The body field hard-codes `ReferenceExpressionIE<'s, 'i, cI>`, and the bounds maps hard-code `IdI<cI>`. This is the only "definition" type without R; `StructDefinitionI<'s, 'i, R>` and `InterfaceDefinitionI<'s, 'i, R>` both carry R (though in practice always used at `cI`).

---

## Part 7: Expression AST

### 7.1 Three Enums — Carries R But Always Used at cI

`ast/expressions.rs`:

- `ExpressionIE<'s, 'i, R>` (`:26-29`) — 2-variant dispatcher (`Reference` / `Address`).
- `ReferenceExpressionIE<'s, 'i, R>` (`:103-153`) — 49 variants, each holding `&'i Payload`.
- `AddressExpressionIE<'s, 'i, R>` (`:159-165`) — 5 variants (`LocalLookup`, `StaticSizedArrayLookup`, `RuntimeSizedArrayLookup`, `ReferenceMemberLookup`, `AddressMemberLookup`).

Every expression type and all ~50 payload structs carry `<'s, 'i, R>` at the type-definition level. **But at every use site, R is locked to `cI`:**

- `FunctionDefinitionI::body: ReferenceExpressionIE<'s, 'i, cI>` (`ast/ast.rs:242`) — the only persistent storage location.
- `InstantiatorI::translate_ref_expr` returns `ReferenceExpressionIE<'s, 'i, cI>` directly (`instantiator.rs:3336`).
- `InstantiatorI::translate_addr_expr` returns `AddressExpressionIE<'s, 'i, cI>` directly (`:3090`).

The R parameter exists for uniformity (every other I-suffix type carries R) but is effectively vestigial — the instantiator translates TE expressions straight to `cI`-flavored IE, skipping `sI`/`nI` entirely. Treat the R generic on expression types as forward-compat scaffolding, not as a live multi-mode capability.

### 7.2 Arena-Allocated, Not Interned

Expression nodes allocate into `'i` without deduping. Sub-expressions are `&'i Payload` refs; collections are `&'i [T]` slices. No `*ValI` companions on expressions.

### 7.3 Derives

`#[derive(Copy, Clone, Debug)]` only — Scala's `vcurious` (panic on equals) on every expression case class becomes Rust's "no `PartialEq`/`Hash`/`Eq` impl," which gives a strictly stronger compile-time error. `ConstantFloatIE` holds `f64`, which couldn't derive `Eq`/`Hash` anyway. Inline result types (like `IExpressionResultI`) keep full Copy/Eq/Hash.

### 7.4 Visitor/Collector

`collector.rs` (424 lines) — Rust-only port of Scala's reflective `Collector`, specialized for the I-side value AST. `NodeRefI` enum + `all_in_*` / `only_in_*` helpers + `collect_*_inode!` macros. The expression-hierarchy walkers (`visit_reference_expression_ie`, `visit_let_normal_ie`, `visit_function_call_ie`, `visit_function_definition`) are partial — extend as use sites require more coverage.

---

## Part 8: Bound-Arg Threading

### 8.1 `InstantiationBoundArgumentsI` — Per-Site Storage

`ast/hinputs.rs:30-35`:

```rust
pub struct InstantiationBoundArgumentsI<'s, 'i> where 's: 'i {
    pub rune_to_function_bound_arg:
        ArenaIndexMap<'i, IRuneS<'s>, &'i PrototypeI<'s, 'i, sI>>,
    pub caller_rune_to_callee_rune_to_reachable_func:
        ArenaIndexMap<'i, IRuneS<'s>,
            ArenaIndexMap<'i, IRuneS<'s>, &'i PrototypeI<'s, 'i, sI>>>,
    pub rune_to_impl_bound_arg:
        ArenaIndexMap<'i, IRuneS<'s>, IdI<'s, 'i, sI>>,
}
```

Three maps:

- `rune_to_function_bound_arg` — for each function bound the call site needs, which concrete function satisfies it.
- `caller_rune_to_callee_rune_to_reachable_func` — for nested bounds (caller-side rune → callee-side rune → satisfying function).
- `rune_to_impl_bound_arg` — for each impl bound, the satisfying impl id.

All bound args stored in `sI` mode; they get collapsed only at final output emission.

### 8.2 `DenizenBoundToDenizenCallerBoundArgI` — Per-Call Threading

See §2.5. The two `IndexMap`s map typing-side bound *parameter* ids to instantiating-side bound *argument* prototypes/ids. Threaded as `&` parameter through ~50 method signatures. Combined via `.plus()` (`union_maps_expect_no_conflict`).

Stack-owned, cloned liberally (Scala used GC'd `Map[IdT, PrototypeI]`; Rust uses `IndexMap` with `.clone()`). Not arena-allocated.

### 8.3 `assemble_instantiation_bound_param_to_arg`

`instantiator.rs:864-895` materializes a `DenizenBoundToDenizenCallerBoundArgI` from a `(callee's instantiation_bound_params, caller's supplied instantiation_bound_args)` pair by zipping them on rune. Used at the start of `translate_impl`, `translate_function`, `translate_abstract_func`, `collapse_and_translate_struct_definition`, `collapse_and_translate_interface_definition`.

### 8.4 Stale-Snapshot Trap

`DenizenBoundToDenizenCallerBoundArgI` is captured at the *call site* where instantiation is decided. If you reuse one across multiple call sites (e.g. iterating over methods of an interface and using the interface's bound-arg map for each method), you may apply the wrong substitution. Mirror Scala's pattern: re-compute the bound-arg map per call site, even when it looks redundant.

---

## Part 9: Error Handling

**No `Result`, no `ICompileErrorI`.** The instantiator uses `panic!` exclusively for unimplemented branches and `vassert*`-style assertions for invariants.

- `pub fn translate(...) -> HinputsI<'s, 'i>` at `instantiator.rs:214` — direct return, no `Result`.
- Scala equivalent `def translate(...): HinputsI` at `Instantiator.scala:102` — also direct return.
- `grep -n "ICompileErrorI"` over both `FrontendRust/src/instantiating/` and `Frontend/InstantiatingPass/` returns zero hits. The type doesn't exist on either side.

The contract: typing-pass output is already valid; all user-level errors were caught earlier. Anything the instantiator hits internally is a programmer-error invariant violation → `panic!`. A separate `Result<HinputsT, ICompileErrorT>` lives on `InstantiatedCompilation::get_compiler_outputs` (it propagates the typing-side error type unchanged from earlier passes), but instantiator-side operations all return values directly.

For unmigrated branches: `panic!("Unimplemented: <branch_name>")` with a unique identifying message.

---

## Part 10: Driving the Pass

### 10.1 Top-Level Entry

Free function at `instantiator.rs:214-219`:

```rust
pub fn translate<'s, 'ctx, 't, 'i>(
    opts: &'ctx GlobalOptions,
    interner: &'ctx InstantiatingInterner<'s, 'i>,
    typing_interner: &'ctx TypingInterner<'s, 't>,
    keywords: &'ctx Keywords<'s>,
    hinputs: &'ctx HinputsT<'s, 't>,
) -> HinputsI<'s, 'i>
where 's: 't, 's: 'i,
{
    let mut monouts = InstantiatedOutputsI::new();
    let instantiator = InstantiatorI { opts, interner, typing_interner, keywords, hinputs };
    instantiator.translate_method(&mut monouts)
}
```

Constructs both `InstantiatedOutputsI::new()` and the `InstantiatorI` value, then calls `translate_method` (Scala's `translate()` renamed to disambiguate from the free fn).

### 10.2 `translate_method` Top-Level Body

`instantiator.rs:255-518`. Real code (not a stub), ~260 LOC:

1. Destructure `HinputsT`.
2. Translate `kind_exports_t` and `function_exports_t` (these seed the worklists by triggering inner `translate_prototype` / `translate_kind` calls).
3. Translate non-generic function externs (generic externs are deferred to callsite-time).
4. Worklist drain loop (§3.2): while any of `new_functions` / `new_impls` / `new_abstract_funcs` is non-empty, pop one and process.
5. Assemble final `interface_edge_blueprints` and `interface_to_sub_citizen_to_edge` tables from accumulator data.
6. Pack `HinputsI` and return.

### 10.3 `InstantiatedCompilation` Coordination Wrapper

`instantiated_compilation.rs:71`. 5-lifetime struct (`<'s, 'ctx, 't, 'i, 'p>`) that owns:

- A `TypingPassCompilation` (which transitively owns typing arena and upstream passes).
- The `InstantiatingInterner` (built from the externally-owned `'i` Bump passed to `new`).
- A cached `monouts_cache: Option<HinputsI<'s, 'i>>` (lazy compute-on-first-access).

Consumer entry: `get_monouts(&mut self) -> &HinputsI<'s, 'i>` calls `instantiator::translate` once and caches.

### 10.4 Hammer Handoff

The hammer pass receives `&HinputsI<'s, 'i>` plus the instantiating interner reference. Caller drop order at end-of-scope (`pass_manager.rs:807-810` declaration order, reversed for drop): `'i` dies first, then `'h`, `'t`, `'s`. Since `HinputsI<'s, 'i>` doesn't structurally borrow `'t` data, the typing arena could theoretically drop after the instantiator finishes — but the canonical driver doesn't take advantage of this.

---

## Part 11: Invariants Summary

1. **Region mode at the T→I boundary is always `sI`.** `reintern.rs:10-11` documents this; the live `translate_method` body produces `sI` form at the boundary even though `reintern::*` bodies are stubs.
2. **`cI` is reachable only via collapse**, never via direct construction at the T→I boundary. The collapse always passes through `region_counter` first to build the renumbering map.
3. **`InstantiatingInterner` does not carry `'t`** (`instantiating_interner.rs:14-16`) — by design, so the typing arena could drop after instantiation.
4. **`nI` and `cI` are type aliases for `sI`** (CCFCTS). Zero-member ZSTs. The three `unsafe transmute` / pointer-reinterpret sites at `instantiator.rs:1131`, `:1423`, `:1607` rely on this invariant.
5. **All bounds stored in `sI` mode.** Both `InstantiationBoundArgumentsI` and `DenizenBoundToDenizenCallerBoundArgI` carry `sI`-typed prototype/id refs. Exception: `abstract_func_to_bounds` keys by `cI` id (because it's keyed by the post-collapse abstract function prototype).
6. **Worklist content is in `nI` mode.** `new_impls`, `new_abstract_funcs`, `new_functions` all carry `IdI<nI>` / `PrototypeI<nI>` after consistent-collapse renumbering at queue-time.
7. **Final output (`HinputsI`) is all `cI`.** Hammer (simplifying) only ever sees `cI`-mode data.
8. **`MustIntern` seal preserves canonical pointer identity** on `&'i StructIT`, `&'i InterfaceIT`, `&'i StaticSizedArrayIT`, `&'i RuntimeSizedArrayIT`, `&'i PrototypeI`, `&'i SignatureI`, and the ~72 concrete `*NameI` types. `IdI` is the unsealed exception (pending future sealing).
9. **Worklists are `Vec`, drained FIFO via `remove(0)`.** Not `VecDeque`. Performance-acceptable because queue lengths are small.
10. **Keys in `InstantiatedOutputsI` are by-value, never `PtrKey`.** I-side interned types have derived `PartialEq`/`Hash` that delegate to interned-ref fields.
11. **Stamp-once idempotency required** — the call graph has cycles. Every top-level `translate_*` checks the relevant `_to_mutability` or `_to_bounds` map and returns early if already processed.
12. **`InstantiatedOutputsI` is stack-owned**, but its arena-allocated values survive past its drop. AASSNCMCX exception is for the accumulator itself; payloads it holds follow the rule.

---

## Part 12: Recurring Traps

1. **`cI` vs `sI` vs `nI` phantom confusion** — the type system blocks naive mixing but the `unsafe transmute` sites enable controlled crossings. When the borrow checker rejects an assignment, check whether the value is in the right mode for the slot you're trying to fill. Don't paper over with `transmute` unless you've literally already seen one at this site.
2. **Forgetting `where 's: 'i`** on new output types. Rust won't infer the outlives; the rebase will fail at the first call site that tries to thread `&'s` data through. Declare it on the struct.
3. **Calling `reintern::*`** — bodies are stubs; calls panic at runtime. During body migration, T→I translation happens inline inside `translate_*` methods.
4. **Introducing an environment type.** Tempting when the substitution map gets threaded through 10 method signatures. Scala parity wins — don't.
5. **Mutating arena-allocated values.** `InstantiatedOutputsI` is stack — fine to mutate. `HinputsI` is arena — immutable after construction; produce a fresh allocation if you need a changed version.
6. **Skipping a worklist push.** A `translate_function` body that omits the "push newly-discovered call onto `new_functions`" step silently fails to monomorphize the callee. Mirror the Scala recursion exactly.
7. **Two-interner field confusion.** `InstantiatorI` holds both `interner` (I-side) and `typing_interner` (T-side). Pattern-matching `coord_t.kind` (T-side) needs `typing_interner`-side variants; constructing the corresponding `CoordI` needs `interner`-side helpers. Convention: if your local is named `*_t`, use `typing_interner`; if it's `*_s`/`*_i`/`*_c`, use `interner`.
8. **`vassert_one` vs `vassert_some`.** `vassert_one(coll.iter().filter(...))` is the idiomatic "expect exactly one match" pattern that asserts uniqueness *and* presence. Don't substitute `coll.iter().find(...).unwrap()` — that's `vassert_some` and only checks presence.
9. **Region collapse position-correctness.** When `region_collapser_individual` ports a Scala arm, the interleave order of arms matters for SCPX (Scala Comment Parity). Scala's source ordering must be preserved at the `intern_*_ci` call ordering.
10. **`IdI` not sealed.** `IdI`-keyed maps fall back to structural eq/hash. Fine in practice (interned slice/inner-enum compare gives canonical identity) but if you see `IdI` equality behavior diverge from your expectation, check whether `init_steps` slices were arena-interned vs heap-allocated.

---

## Part 13: Migration State (as of doc-write)

- **AST/scaffolding**: largely migrated. Every major type exists with the right shape.
- **Interner**: 12 families wired, ~216 macro-generated wrapper trios, all live code. `intern_id_*` deferred until `IdI` is sealed.
- **Top-level `translate_method`**: real code (~260 LOC).
- **~52 `translate_*` methods**: mix of fully-migrated bodies and `panic!` stubs. The trend is healthy — every Phase-2 integration test fill adds a few more bodies.
- **`reintern.rs`**: 11 functions, all `panic!()` stubs. Body migration is a separate phase.
- **Region collapsers / counter**: substantial real code. Each has ~25 `panic!`s in less-common branches (e.g. `AnonymousSubstructConstructor`, `ForwarderFunction` arms in `collapse_function_name`).
- **`HinputsI` lookup methods**: ~20 methods on `hinputs.rs`. About half live, about half `panic!` stubs.


---

## Part 14: Risks / Open Questions

- **Two parallel collapser surfaces.** `region_collapser_individual.rs` and `region_collapser_consistent.rs` are ~30-function mirror images; adding a new I-side type requires updating both. Convention-enforced only. A code-gen via shared `macro_rules!` might be warranted later but would diverge from line-for-line Scala parity.
- **`reintern.rs` body migration.** Inline T→I translation works today but duplicates code paths across `translate_*` methods. A focused `reintern` migration sweep would consolidate; deferred until enough call sites exist to validate the API shape.
- **`IdI` not yet sealed.** Slightly slower hash (structural rather than ptr-eq dispatch) but correct. Sealing requires defining `IdIValI` and `intern_id_*` family methods.
- **CCFCTS `unsafe transmute` discipline.** The three cast sites at `instantiator.rs:1131,:1423,:1607` are load-bearing usages of the `nI = cI = sI` aliasing. If anyone adds a member to `sI`/`nI`/`cI` it becomes UB. Sealed against external code; relies on internal discipline.
- **Region runtime semantics.** Today's `nI = cI = sI` aliasing is forward-compat for "regions become inert." If the design grows runtime region members, the marker types need real differentiation and every interning trio becomes load-bearing.
- **`HinputsI` lookup methods are mostly stubs.** Hammer reads `HinputsI` field-by-field today and avoids the lookups, but downstream consumers will hit them.
- **Substitution-threading explosion.** Some methods take 6+ explicit substitution-like args (`DenizenBoundToDenizenCallerBoundArgI`, `substitutions`, `perspective_region_t`, etc.). Bundling would break Scala parity; tolerate the threading.
- **CoordSendSR `[~]` accumulation.** Same residual blocker as the typing pass — when an instantiation hits the CoordSendSR determinism bug, the test gets `[~]`'d. Architect policy: let the `[~]`s accumulate; land the fix only when blast radius is mapped.
- **Long-running process memory.** Arena retention means the instantiator's output is held for the simplifying pass's duration. Fine for batch compilation; for an LSP-style long-running daemon, the cumulative arena would grow. Two-tier per-denizen arenas would fix this — post-migration redesign.
- **No interactive / LSP support.** Full re-instantiation on every change.

---

## Part 15: Acronyms

Cross-references for acronyms used in this doc:

- **CCFCTS** — "Can Cast From Collapsed To Subjective." Canonical definition at `docs/InstantiatorRegions.md:325`. Justifies the `nI = cI = sI` type-alias collapse.
- **TFITCX** — "Temporary For Interner Types: Copy" (?). Tagged on Value-type variants. See arenas.md / interner discipline.
- **WVSBIZ** — phantom-R discipline in `KindIT`. Primitives carry `R` via `PhantomData`; compounds are arena-interned and held by `&'i` ref.
- **PVECFPZ** — "Polyvalue Equality / Casting" discipline. Wrapper enums derive Eq/Hash; never hand-roll `ptr::eq` on the outer ref.
- **SICZ** — Sealed-Interned-Construction. `MustIntern(())` sealed by module-private unit.
- **PASDZ** — "Placeholder Authoritative-Source = Denizen." Placeholder ids are path-encoded; the flat single-level substitutions map suffices.
- **NUSLX** — "Never Use Static Lifetime." Breaks ptr-eq for interned types.
- **AASSNCMCX** — "Arena Allocated Structs Should Not Contain Maps / Containers." Exceptions: stack-owned accumulators (`InstantiatedOutputsI`, `CompilerOutputs`) — heap maps are fine because they die with the stack frame.
- **HRALII** — ownership-splitting (BorrowI → MutableBorrow/ImmutableBorrow, ShareI → MutableShare/ImmutableShare). The instantiator turns ambiguous borrow/share into a specific Mutable/Immutable form based on region mutability.
- **ICRHRC** — "Why/when we count the regions." File-header citation on both region collapsers. Definition not in `docs/`.
- **LCCSL** — Lambdas Can Call Sibling Lambdas. Edge case handled in `translate_prototype`.
- **LCCPGB**, **LCNBAFA** — lambda-to-lambda bound passing.
- **LHPCTLD** — "Last Has Placeholders, Containing Top Level Denizen." Only top-level denizens have placeholders; the substitution map is shared with containing lambdas.
- **NBIFP** — bound hoisting through parameters (commented-out code preserved as audit trail).
- **TIBANFC** — "Translate Impl Bound Argument Names For Case." Definition in `docs/Generics.md:1404`.
- **IPOMFIC**, **TTTDRM**, **CSHROOR**, **DUDEWCD**, **DINSIE**, **BRCOBS**, **RMLRMO**, **AUMAP**, **HCCSCS**, **NPFCASTN**, **DDSOT**, **NNSPAFOC**, **EHCFBD**, **IMRFDI**, **UINIT**, **PRIIROZ**, **SMLRZ**, **LAGTNGZ**, **TNAD**, **MFBFDP**, **OMCNAGP**, **CDFGI** — various spot citations. Most defined in `docs/`; see grep.

---

## Part 16: Key Files / Directories

| Path | Purpose |
|---|---|
| `docs/architecture/instantiator-design.md` | this doc |
| `docs/architecture/typing-pass-design-v3.md` | upstream pass design (produces `HinputsT`) |
| `docs/architecture/simplifying_pass_design.md` | downstream pass design (consumes `HinputsI`) |
| `docs/InstantiatorRegions.md` | CCFCTS canonical definition + region semantics |
| `docs/Generics.md` | TIBANFC, MKRFA, etc. |
| `FrontendRust/src/instantiating/mod.rs` | module declarations |
| `FrontendRust/src/instantiating/instantiating_arena.rs` | `InstantiatingArena<'i>` newtype |
| `FrontendRust/src/instantiating/instantiating_interner.rs` | 12-family interner, ~216 macro-generated wrappers, `paste!`-based codegen |
| `FrontendRust/src/instantiating/instantiator.rs` | 6538 lines: `InstantiatorI` god struct, `InstantiatedOutputsI`, ~52 `translate_*` methods, `translate_method` driver |
| `FrontendRust/src/instantiating/reintern.rs` | T→I bridge — 11 functions, all panic stubs |
| `FrontendRust/src/instantiating/region_counter.rs` | `CounterI` + 37 `count_*` free functions |
| `FrontendRust/src/instantiating/region_collapser_individual.rs` | 27 `collapse_*` free functions, sI→cI with fresh per-call map |
| `FrontendRust/src/instantiating/region_collapser_consistent.rs` | 30 `collapse_*` free functions, sI→nI with externally-supplied map |
| `FrontendRust/src/instantiating/collector.rs` | `NodeRefI` visitor + `collect_*_inode!` macros |
| `FrontendRust/src/instantiating/instantiated_compilation.rs` | 5-lifetime driver wrapper, lazy `monouts_cache` |
| `FrontendRust/src/instantiating/instantiated_humanizer.rs` | I-side pretty-printer |
| `FrontendRust/src/instantiating/ast/types.rs` | `sI`/`nI`/`cI` markers, `CoordI`, `KindIT`, kind payloads, atom enums |
| `FrontendRust/src/instantiating/ast/names.rs` | 2539 lines: `IdI`, `INameI` (72 variants), all `*NameI` structs, sub-enum families, TryFrom/From conversions |
| `FrontendRust/src/instantiating/ast/ast.rs` | `PrototypeI`, `SignatureI`, `FunctionDefinitionI`, `FunctionHeaderI`, exports/externs, edges, attributes, local variables |
| `FrontendRust/src/instantiating/ast/templata.rs` | `ITemplataI` (20 variants) + payload structs |
| `FrontendRust/src/instantiating/ast/citizens.rs` | `StructDefinitionI`, `InterfaceDefinitionI`, `IMemberTypeI` |
| `FrontendRust/src/instantiating/ast/expressions.rs` | `ExpressionIE`/`ReferenceExpressionIE`/`AddressExpressionIE` + ~50 payloads |
| `FrontendRust/src/instantiating/ast/hinputs.rs` | `HinputsI`, `InstantiationBoundArgumentsI`, ~20 lookup methods |
| `FrontendRust/src/instantiating/ast/templata_utils.rs` | `expect_*` helpers (mostly Scala-comment placeholders) |
| `FrontendRust/src/pass_manager/pass_manager.rs` | top-level driver; arena construction order (lines 807-810) |
| `Frontend/InstantiatingPass/src/dev/vale/instantiating/Instantiator.scala` | Scala source of truth — line-for-line port target |
| `Frontend/InstantiatingPass/src/dev/vale/instantiating/ast/*.scala` | Scala AST source |

---

## Part 17: Long-Term Cleanup TODO

Items below diverge from line-for-line Scala parity; check with the architect before starting. They are ordered by combined "value × independence" — earlier items unlock cleanup that later items depend on.

### Priority 1 — Big architectural simplifications

**1.1 Remove regions entirely (`sI` / `nI` / `cI` / `R` generic).**
Regions were a prototype; the instantiator no longer needs them. Scope of work:

- Delete `region_collapser_individual.rs` (824 lines), `region_collapser_consistent.rs` (830 lines), `region_counter.rs` (970 lines). ~2600 lines gone.
- Drop the `R` generic from every I-side type (~50 struct/enum definitions): `IdI<'s, 'i>`, `INameI<'s, 'i>`, `KindIT<'s, 'i>`, `ITemplataI<'s, 'i>`, `CoordI<'s, 'i>`, `PrototypeI<'s, 'i>`, `SignatureI<'s, 'i>`, `StructIT<'s, 'i>`, `InterfaceIT<'s, 'i>`, `StaticSizedArrayIT<'s, 'i>`, `RuntimeSizedArrayIT<'s, 'i>`, all 72 `*NameI` structs, all sub-enums, all ~50 expression payload structs, `StructDefinitionI`, `InterfaceDefinitionI`.
- Collapse the interner's 12 HashMap families (4 × 3 modes) to 4. Drop the per-mode trio macro structure; the typing pass's paste-free `macro_rules!` shape is the target — see item 1.2.
- Delete `IRegionsModeIT` trait, `region_mode_sealed` module, and the `sI`/`nI`/`cI` markers in `ast/types.rs`.
- Delete the three CCFCTS `unsafe transmute` / pointer-reinterpret sites at `instantiator.rs:1131`, `:1423`, `:1607` — no mode to cast across.
- Delete `ITemplataI::Region` variant and the `RegionTemplataI` struct. Delete `IRegionNameI` / `RegionNameI` / `DenizenDefaultRegionNameI`.
- Drop the `perspective_region_t: &RegionT` parameter from ~50 `translate_*` methods (currently threaded but mostly unused — every call site passes `RegionT { region: IRegionT::Default }`).
- Collapse the `(subjective_it, collapsed_ct)` return-tuple pattern in `translate_prototype`, `translate_function_id`, etc. to a single return.
- Drop `<cI>` annotations on `HinputsI` fields.
- Collapse `InstantiatedOutputsI`'s `sI`-keyed vs `cI`-keyed bounds-storage flavor distinction to one flavor. Removes the asymmetry where `abstract_func_to_bounds` is `cI`-keyed and the others are `sI`-keyed (inconsistency 3g resolves for free).
- Remove `FunctionDefinitionI`-has-no-R vs `Struct/InterfaceDefinitionI`-have-R asymmetry (inconsistency from Part 6).
- Delete `docs/InstantiatorRegions.md`. CCFCTS / ICRHRC / HRALII acronyms drop out of the doc inventory.
- Estimated total: ~5000 lines deleted out of the instantiator's ~19000.

**Verification before starting:** Search for any *still-active* region usage. The `MutabilifyIE` / `ImmutabilifyIE` expression nodes encode the immutable-borrow-back-to-mutable casts after pure blocks — confirm those nodes can survive with a simpler "is_mut: bool" provenance field instead of the full sI/nI/cI machinery. If the typing pass's `RegionT` scaffold still serves a purpose for those nodes, keep `RegionT` on the T-side and only collapse the I-side translation; don't try to remove regions from typing in the same change.

**1.2 Delete the `paste` crate dependency.**
Falls out of item 1.1: once the per-mode `_si`/`_ni`/`_ci` suffix construction is gone, no identifier construction is needed. Adopt the typing-pass macro shape from `FrontendRust/src/typing/typing_interner.rs:57-89` — three small `macro_rules!` macros (`impl_intern_name_wrapper_simple!`, `impl_intern_name_wrapper_transient!`, `impl_intern_kind_wrapper!`) that take the method name as an explicit `$method:ident` parameter. Remove `paste = "1"` from `FrontendRust/Cargo.toml`.

### Priority 2 — Localized cleanups that don't depend on item 1

**2.1 Seal `IdI`.**
Bring `IdI` in line with its peers (`IdT`, `IdH`, all `*IT`/`PrototypeI`/`SignatureI`/`*NameI` types). Pattern to copy verbatim from `FrontendRust/src/typing/names/names.rs:39-46` and `FrontendRust/src/typing/typing_interner.rs:316`:

- Add `pub _must_intern: MustIntern` field to `IdI`.
- Define `IdIValI<'s, 'i, 'tmp>` mirror with a third `'tmp` lifetime so callers can build transient queries from stack-allocated slices.
- Add `intern_id(&self, val: IdIValI<...>) -> &'i IdI<...>` method to `InstantiatingInterner`.
- Convert all current `IdI { ... }` construction sites to `interner.intern_id(IdIValI { ... })`.
- Update `IdI`'s derived `Hash`/`Eq` to use canonical pointer identity via the seal (typing pass shows the pattern).

Eliminates the "is `IdI` interned or just allocated?" caveat that the design has had to keep explaining.

**2.2 Delete `reintern.rs` entirely.**
The module has no Scala counterpart (Scala has no `Reintern` namespace), no Rust call sites (grep over the whole tree returns only the 11 panic-body lines), and the deduplication it was meant to provide is already handled by the `InstantiatingInterner`. The live `translate_method` body already does T→I translation inline (see lines 273-377). Delete `reintern.rs` and remove the `pub mod reintern;` declaration from `mod.rs`.

**2.3 Delete `humanize_signature` from `instantiated_humanizer.rs`.**
Dead code in both Scala and Rust — defined at `InstantiatedHumanizer.scala:211` but no Scala or Rust call site invokes it. Removes the only `panic!` from `instantiated_humanizer.rs`. Leave the Scala source alone (migration policy is Rust-side cleanup only); if Scala ever resurrects it, port it then.

**2.4 Delete `ICitizenDefinitionI` if it remains unused.**
The enum dispatcher in `ast/citizens.rs:30-33` is declared per architect-directed NEDCX but most call sites pattern-match directly on `StructDefinitionI` / `InterfaceDefinitionI`. Audit usage; if no live code depends on the wrapper, delete it. If something does depend on it, leave alone — the architect's call was deliberate.

**2.5 Switch worklists from `Vec` + `remove(0)` to `VecDeque` + `pop_front()`.**
Three-field change in `InstantiatedOutputsI` (`instantiator.rs:103-105`, initializer at `:177-179`) plus drain-loop in `translate_method` (`:418-436`). Brings drain from O(n) to O(1) and matches Scala's `mutable.Queue` + `dequeue()` semantics directly.

### Priority 3 — Cosmetic / nice-to-have

**3.1 Fix `HinputsI` slice-vs-ref asymmetry.**
Currently `interfaces: &'i [InterfaceDefinitionI<...>]` (inline) versus `structs: &'i [&'i StructDefinitionI<...>]` (by ref) — artifact of `translate_method`'s assembly, not intentional. Pick one shape (`&'i [&'i T]` for all three is the natural target since structs/functions already match it) and make the interface assembly arena-allocate the `InterfaceDefinitionI` values up front.

**3.2 Address `interfaces_without_methods` naming.**
The field holds transient state (interface definitions built with `internal_methods: &[]`, later discarded and rebuilt with methods attached via v-table assembly). The current name matches Scala (`interfacesWithoutMethods`) but actively misleads about the relationship between map contents and final emitted `HinputsI`. Three options:

- Keep the name (Scala parity), add a comment explaining the discard-and-rebuild.
- Rename to `pending_interface_definitions` or similar.
- Restructure to use a dedicated `InterfaceDefinitionStubI` type for the transient state, distinct from final `InterfaceDefinitionI`. Heaviest but cleanest — the type system enforces the distinction.

Lean toward option 2 unless Scala parity is hard.
