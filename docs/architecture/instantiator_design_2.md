# Instantiating Pass Design

Architecture and design decisions for the **instantiating pass**. Sister doc to `typing-pass-design-v3.md`. **Some design decisions here are recovered-from-code rather than architect-deliberated** — flagged inline where that's the case.

For the operational status of the instantiator, see `FrontendRust/src/typing/typing-pass-todo.md`.

---

## Part 1: Arena and Lifetime Model

The instantiating pass takes typing-pass output (`HinputsT<'s, 't>`) and monomorphizes it, producing instantiated AST (`HinputsI<'s, 'i>`). Like typing, the design uses **pragmatic arena retention**: instantiator output lives past the pass and is consumed by the hammer pass directly. Output types carry `<'s, 'i>` and can hold `&'s` refs into scout-arena data (e.g. `&'s IImpreciseNameS` for export names).

**The trade.** Cost: scout arena memory and instantiating arena memory both survive into the hammer pass. Benefit: 1:1 Scala line-for-line port, no side-table plumbing, no re-interning at the I→H boundary beyond what the hammer interner does for its own canonical types.

### 1.1 Three Arenas Plus One Inherited

- **`'s` — Scout arena** (`ScoutArena<'s>`). Inherited from earlier passes; the instantiator only reads it.
- **`'t` — Typing arena** (`TypingInterner<'s, 't>`). Inherited input. Dies after the instantiator finishes.
- **`'i` — Instantiating arena** (`InstantiatingArena<'i>`, `instantiating_arena.rs:10`). New, owned by the instantiator. Outlives the hammer pass.

`InstantiatingArena<'i>` is a thin newtype around `&'i Bump` — same shape as `ScoutArena<'p>`/`TypingInterner`. It's forward-compatible scaffolding; today it carries no interning maps (those live on `InstantiatingInterner`).

### 1.2 Lifetime Invariants

1. **`'s` outlives `'t` and `'i`** independently. Both `where 's: 't` and `where 's: 'i` are declared on output types. Rust doesn't infer outlives from drop order — declare it.
2. **`'t` and `'i` are siblings, not nested.** Neither outlives the other; both die after the typing-instantiator-hammer sequence completes. The `InstantiatingInterner` deliberately does NOT carry `'t` (see `instantiating_interner.rs:17`: "`HinputsI` needs to outlive the typing arena"). This is **load-bearing**: the hammer pass receives `HinputsI<'s, 'i>` and must not be tied to `'t`.
3. **Arena borrow convention.** Arena parameters take a short borrow lifetime (`&InstantiatingArena<'i>` or `&'ctx InstantiatingArena<'i>`), never the arena's own lifetime. Same rule as typing (typing §1.2.5).
4. **Never `'static`.** All data lives in `'p`, `'s`, `'t`, `'i`, or on the stack. `&'static T` breaks pointer-equality for interned types (NUSLX).
5. **AASSNCMCX.** No `Vec`/`HashMap`/`String` inside arena-allocated types. Use `&'i [T]` slices and `ArenaIndexMap<'i, K, V>`. Driver structs (`InstantiatedOutputsI`, `InstantiatorI`) live on the stack and may use plain `IndexMap`/`Vec` — same exception as typing's `CompilerOutputs`.

### 1.3 Arena Construction Order

```rust
fn run_pipeline() {
    let parse_arena = ParseArena::new();
    let scout_arena = ScoutArena::new();
    let typing_bump = Bump::new();
    let typing_interner = TypingInterner::new(&typing_bump);
    let hinputs_t = run_typing_pass(...);

    let instantiating_bump = Bump::new();
    let instantiating_interner = InstantiatingInterner::new(&instantiating_bump);
    let hinputs_i = instantiating::translate(
        opts, &instantiating_interner, &typing_interner,
        keywords, &hinputs_t,
    );

    // hammer pass reads hinputs_i...

    // instantiating_bump drops: 'i dies
    // typing_bump drops: 't dies
    // scout_arena drops: 's dies
}
```

Drop order (reverse of declaration) gives `'i,'t < 's < 'p`. Note that `'i` and `'t` can interleave — what matters is they both outlive their pass and both die before `'s`.

### 1.4 Where Each Type Lives

| Type | Lifetimes | Arena |
|---|---|---|
| `HinputsI` | `<'s, 'i>` | Stack-allocated struct, holds `&'i` refs + `ArenaIndexMap` in `'i` |
| `FunctionDefinitionI`, `StructDefinitionI`, `InterfaceDefinitionI`, `EdgeI` | `<'s, 'i>` / `<'s, 'i, R>` | `'i` |
| `IdI`, `INameI`, `KindIT`, `ITemplataI` | `<'s, 'i, R>` | `'i`, interned (kind payloads, names, signatures, prototypes) |
| `CoordI` | `<'s, 'i, R>` | Inline Copy, not interned |
| `ReferenceExpressionIE`, `AddressExpressionIE` | `<'s, 'i, R>` | `'i`, not interned |
| `InstantiatedOutputsI` | `<'s, 't, 'i>` | Stack-owned, heap `IndexMap`s |
| `InstantiatorI` (god struct) | `<'s, 'ctx, 't, 'i>` | Stack |

### 1.5 The `R` Region-Mode Type Parameter

**The instantiator is the only pass that carries a region-mode generic on nearly every AST node.** Three sentinel marker types in `ast/types.rs:175-196`:

```rust
pub struct sI;  // "subjective": initial mode at the T→I boundary
pub struct nI;  // "new": start of a fresh instantiation
pub struct cI;  // "collapsed/concrete": output mode (final)
```

`nI` and `cI` are **type aliases** for `sI` today (`pub type nI = sI; pub type cI = sI;`). The covariance Scala carried via `+R` is erased at the type level — `R` exists only as a phantom marker on AST struct generics to prevent accidentally mixing tree phases.

**If regions ever gain runtime semantics, the type aliasing must be revisited** (comment at `ast/types.rs:184`). For the migration, treat `R` as inert and ensure new AST struct/enum definitions thread `R` through their generic list per established sites.

---

## Part 2: The God Struct

### 2.1 Architecture

All Scala sub-instantiators (FunctionInstantiator, ExpressionInstantiator, TypeInstantiator, etc.) collapse into a single `InstantiatorI` struct in `instantiator.rs:235`:

```rust
pub struct InstantiatorI<'s, 'ctx, 't, 'i> {
    pub opts: &'ctx GlobalOptions,
    pub interner: &'ctx InstantiatingInterner<'s, 'i>,
    pub typing_interner: &'ctx TypingInterner<'s, 't>,
    pub keywords: &'ctx Keywords<'s>,
    pub hinputs: &'ctx HinputsT<'s, 't>,
}
```

Immutable configuration plus the input `HinputsT` (read-only). Mutable state threads through `&mut InstantiatedOutputsI<'s, 't, 'i>` on every translation method.

**Two interners, not one.** Scala had a single shared `Interner`; the Rust port must thread both `typing_interner` and `instantiating_interner` because translation methods need both T-side helpers (e.g. `TemplataCompiler::get_super_template`) reading the T-side interner and I-side construction populating the I-side interner. The dual-interner pattern is a Rust necessity, not a design choice.

**Inferred:** the `InstantiatorI`-as-god-struct decision mirrors typing's `Compiler` decision (typing §2.1). The Sylvan transplant pre-applied this collapse; the Vale drift-reconciliation kept it.

### 2.2 `&self` + `&mut monouts` Enables Re-entrancy

Same pattern as typing. The deep mutual recursion of translation methods (`translate_function → translate_block → translate_expression → translate_function_call → ...`) works because `&self` allows shared borrow and `&mut monouts` is re-borrowed at each call level.

### 2.3 No Sub-Instantiator Structs

Methods spread across `impl InstantiatorI<…> { ... }` blocks in `instantiator.rs` (one block per method, ~73 impl blocks for 74 methods — Scala-parity layout). No `name_instantiator`, `type_instantiator`, or `expression_instantiator` field exists. The Scala sub-class state was all configuration that's already on `InstantiatorI`.

### 2.4 Free Functions vs Methods

Three companion modules ship translation helpers as **free functions**, not methods:

- **`reintern.rs`** — T→I boundary helpers (`translate_id`, `translate_kind`, `translate_coord`, `translate_templata`, etc.). These are pure on their args (interner + T-side value) and don't touch `self`/`monouts`, so free functions are correct per typing §2.5. **All bodies are `panic!()` stubs as of Slab 16h** — the file is signature-only.
- **`region_counter.rs`** — `count_*` helpers walking `sI` trees and accumulating into a `CounterI` collector. Free functions, no `self`.
- **`region_collapser_individual.rs` / `region_collapser_consistent.rs`** — `collapse_*` helpers rewriting `sI` trees into `cI` trees. Free functions taking `(counter_map, interner, value)`.

Everything else (the 74 translation methods on `InstantiatorI`) needs the god struct's fields and is a method.

### 2.5 `DenizenBoundToDenizenCallerBoundArgI`

A companion struct (`instantiator.rs:53`) threaded as `&` arg through ~50 method signatures:

```rust
pub struct DenizenBoundToDenizenCallerBoundArgI<'s, 't, 'i> {
    pub function_bound_to_function_arg: IndexMap<...>,
    pub impl_bound_to_impl_arg: IndexMap<...>,
}
```

**Inferred:** this is the substitution-environment stand-in. The instantiator has no environment type (see Part 3); it threads substitutions explicitly through every method that needs them. Refactoring to bundle these into an env would break Scala parity. Tolerate the threading.

---

## Part 3: No Environments

**The instantiating pass has no `IEnvironmentI` type.** This is a deliberate parity gap with the typing pass:

- Typing-pass envs hold name-to-templata mappings for scope resolution. After monomorphization, scope resolution is already settled — every templata reference has been pinned to a concrete instantiated value during typing.
- The instantiator's analogue is the **substitution map**: `IndexMap<IdT<'s, 't>, ITemplataI<'s, 'i, sI>>` mapping typing-pass placeholders to concrete instantiated templatas. This map is threaded as an explicit parameter to every method that needs it, alongside `DenizenBoundToDenizenCallerBoundArgI`.

**Consequences for porting:**
- Don't introduce an `IEnvironmentI` even when it would clean up signatures — Scala parity wins.
- The `perspective_region_t: &RegionT` parameter (threaded through most methods) is the analogue of typing's "what region am I generating code from?" — it stays an explicit arg.

---

## Part 4: `InstantiatedOutputsI`

### 4.1 Structure

Mutable accumulator threaded through the pass (`instantiator.rs:88`). 19 fields. Same role as typing's `CompilerOutputs`, but stack-owned and stack-mutated — no arena allocation.

- **Definition tables.** `functions: IndexMap<&'i IdI, FunctionDefinitionI>`, `structs`, `interfaces_without_methods`, `impls`.
- **Type metadata.** `struct_to_mutability`, `interface_to_mutability`.
- **Bound tracking.** `function_to_bounds`, `impl_to_bounds`, `abstract_func_to_bounds`.
- **Edge/dispatch tables.** `interface_to_impls`, `interface_to_abstract_func_to_virtual_index`, `interface_to_impl_to_abstract_prototype_to_override`.
- **Worklists.** `new_impls`, `new_abstract_funcs`, `new_functions` — drained by the top-level `translate_method` loop.
- **Externs.** `kind_externs`, `function_externs`.

All `IndexMap` (insertion-ordered determinism). The map values are `&'i T` refs into the instantiating arena; `IndexMap`/`Vec` ownership is on the stack and dies when `translate` returns.

### 4.2 Worklist-Driven Translation

The top-level entry method `translate_method` (`instantiator.rs:255`, ~260 LOC, **real code, not a stub**):

1. Destructure `HinputsT`.
2. Translate kind/function exports + non-generic function externs (these seed the worklists).
3. Drain loop: while any worklist non-empty, pop one and translate:
   - `new_functions` → `translate_function`
   - `new_impls` → `translate_impl`
   - `new_abstract_funcs` → `translate_abstract_func`
4. Each translation may push onto other worklists.
5. Assemble final `InterfaceEdgeBlueprintI` / `EdgeI` tables.
6. Pack `HinputsI` and return.

Worklist completion = fixpoint reached = monomorphization complete.

### 4.3 Speculative Writes (Not An Issue)

Unlike typing's `CompilerOutputs`, the instantiator doesn't do speculative writes during overload resolution (resolution already finished in typing). All writes are committed.

---

## Part 5: Region-Mode Machinery

The pass produces three region-mode "phases" of the same AST: **`sI`** (subjective, initial T→I), **`nI`** (new, start of fresh instantiation), **`cI`** (collapsed, output). `nI = cI = sI` at the type level — only the phantom tag changes.

### 5.1 `region_counter.rs`

```rust
pub struct CounterI {
    set: HashSet<i32>,
}
```

Walks `sI` trees collecting every distinct `pure_height: i32` integer, then `assemble_map(&self) -> HashMap<i32, i32>` builds a renumbering into a dense `[0, N)` range. Free-function API: `count_prototype`, `count_id`, `count_coord`, `count_kind`, `count_function_id`, etc.

### 5.2 `region_collapser_individual.rs`

Consumes the counter map and rewrites `<'_, '_, sI>` trees into `<'_, '_, cI>` trees node-by-node, re-interning through the interner. Free-function API mirroring `count_*`: `collapse_prototype`, `collapse_id`, `collapse_coord`, `collapse_kind`, `collapse_function_name`, `collapse_struct_id`, `collapse_interface_id`, `collapse_impl_name`, etc.

### 5.3 `region_collapser_consistent.rs`

The "consistent" sibling — for cases where two trees must share renumbering (e.g. a function signature and its body, or a struct definition and its methods, must agree on which `pure_height` value collapses to which dense index).

### 5.4 `reintern.rs` — The T→I Boundary

Signature-only at present. Free functions to translate `&'t IdT`, `INameT`, `PrototypeT`, `CoordT`, `KindT`, `StructTT`, `InterfaceTT`, `ITemplataT` into their `'i sI`-tagged I-side equivalents.

**Region mode at the T→I boundary is always `sI`.** Per file-header comment. The downstream collapser advances `sI → cI` only after the worklist drain completes.

**Body migration status:** all 11 functions in `reintern.rs` are `panic!()`. Call sites in the live `translate_method` body do their T→I translation inline via destructuring, not via the `reintern::*` helpers (yet). Migrating reintern is a separate phase.

---

## Part 6: Type System Types

### 6.0 Phantom-Generic-Erasure Principle

Same as typing §6.0: Scala's `+T` covariant type parameters are erased to monomorphic widest-form in Rust. Applies uniformly here:

- `IdI[+T <: INameI]` → `IdI<'s, 'i, R>` with `local_name: INameI<'s, 'i, R>`.
- `PrototypeI[+T]` → `PrototypeI<'s, 'i, R>` (no inner generic).
- `ITemplataI[+T <: ITemplataType]` → `ITemplataI<'s, 'i, R>` monomorphic.

Use site narrows via `TryFrom`/`try_into().unwrap()` per the documented runtime stand-in.

### 6.1 Interner Model

`InstantiatingInterner<'s, 'i>` (`instantiating_interner.rs:44`) — `RefCell<Inner>` wrapping `&'i Bump`.

**Twelve intern families = 4 logical interned types × 3 region modes:**

```
intern_kind_payload_si / intern_kind_payload_ni / intern_kind_payload_ci
intern_prototype_si    / intern_prototype_ni    / intern_prototype_ci
intern_signature_si    / intern_signature_ni    / intern_signature_ci
intern_name_si         / intern_name_ni         / intern_name_ci
```

Each is a separate `StdHashMap<*ValI, &'i *I>`. Same `*ValI`+`*I` dual-enum pattern as typing — `*ValI` holds transient (`'tmp`-borrowed) data for lookup, `*I` is the canonical arena-allocated form.

**`MustIntern(())` seal** at `instantiating_interner.rs:27` — per @SICZ, only the `intern_*` methods can construct one, sealing the construction path.

**Wrapper macros.** `impl_intern_name_wrappers!` (and a 5-lifetime variant for 14 name structs that hold `ITemplataI`) auto-generate the per-region-mode trios. Adding a new interned name family is one macro invocation, not three.

**Architect call (Slab 16a/16b):** maintain separate maps per (type × region-mode) even though R is phantom today. This is defensive against any future runtime semantics on R. Don't collapse the trio.

### 6.2 `INameI` Hierarchy

Lives in `ast/names.rs` (2,539 LOC — the largest AST file). ~20 concrete name structs, several sub-enums:

- **Top wrapper:** `INameI<'s, 'i, R>` plus its `INameValI` counterpart.
- **Sub-enums:** `ITemplateNameI`, `IFunctionTemplateNameI`, `IFunctionNameI`, `IInstantiationNameI`, `ISuperKindTemplateNameI`, `ISubKindTemplateNameI`, `ICitizenTemplateNameI`, `IStructTemplateNameI`/`IStructNameI`, `IInterfaceTemplateNameI`/`IInterfaceNameI`, `IImplTemplateNameI`/`IImplNameI`, `IRegionNameI`.
- **Concrete:** `RegionNameI`, `ExportNameI`, `ExternNameI`, `ImplNameI`, `LetNameI`, `RawArrayNameI`, plus the typing-pass mirror set.

DAG-parity rule (typing §6.2): a concrete name extending multiple Scala sub-traits gets a variant in each Rust sub-enum.

### 6.3 `IdI<'s, 'i, R>`

```rust
pub struct IdI<'s, 'i, R>
where 's: 'i,
{
    pub package_coord: &'s PackageCoordinate<'s>,
    pub init_steps: &'i [INameI<'s, 'i, R>],
    pub local_name: INameI<'s, 'i, R>,
    pub _must_intern: MustIntern,
    pub _phantom: PhantomData<R>,
}
```

Same shape as typing's `IdT`. Sealed for the same reason: `IdI::eq` uses pointer comparison on `init_steps` slice (per @SICZ).

### 6.4 `CoordI<'s, 'i, R>` — Inline Copy

`CoordI` holds `ownership: OwnershipI`, `region: RegionI`, `kind: KindIT<'s, 'i, R>`. Small, Copy. Not interned — structural equality.

### 6.5 `KindIT<'s, 'i, R>` — Inline Wrapper

```rust
pub enum KindIT<'s, 'i, R> {
    Never(NeverIT<R>), Void(VoidIT<R>), Int(IntIT<R>), ...,
    Struct(&'i StructIT<'s, 'i, R>),
    Interface(&'i InterfaceIT<'s, 'i, R>),
    StaticSizedArray(&'i StaticSizedArrayIT<'s, 'i, R>),
    RuntimeSizedArray(&'i RuntimeSizedArrayIT<'s, 'i, R>),
}
```

Same shape as typing's `KindT`. Primitives inline by value (carrying `R` via `PhantomData`). Compound payloads `&'i` ref to arena-interned.

**Phantom R discipline (WVSBIZ).** Primitives carry `R` only via `PhantomData`; compound payloads are arena-interned and held by `&'i` ref. **Don't mix the two** — interning a primitive variant would create canonical-equality bugs.

### 6.6 `ITemplataI<'s, 'i, R>`

Twenty variants (`ast/templata.rs:133`):

- **Arena-allocated payload refs:** `Coord`, `Kind`, `Function`, `StructDefinition`, `InterfaceDefinition`, `ImplDefinition`, `ExternFunction`, `Isa`, `CoordList`.
- **Inline Copy values:** `Mutability`, `Variability`, `Ownership`, `Location`, `Integer(i64)`, `Boolean(bool)`, `String(StrI<'s>)`, `Prototype`, `Region`, `RuntimeSizedArrayTemplate`, `StaticSizedArrayTemplate`.

**Same monomorphization-erasure rule as typing §6.6** — Scala's `[+T <: ITemplataType]` outer phantom is erased.

---

## Part 7: Expression AST

### 7.1 Three Enums

Parallel to typing exactly:

- `ReferenceExpressionIE<'s, 'i, R>` — ~30 variants (`LetNormalIE`, `IfIE`, `WhileIE`, `MutateIE`, `BlockIE`, `FunctionCallIE`, `ConsecutorIE`, `ConstructIE`, ...).
- `AddressExpressionIE<'s, 'i, R>` — `LocalLookupIE`, `ReferenceMemberLookupIE`, etc.
- `ExpressionIE<'s, 'i, R>` — 2-variant wrapper: `Reference(&'i ReferenceExpressionIE) | Address(&'i AddressExpressionIE)`.

**Narrow-use rule** (same as typing §7.1): only `ConstructIE` holds the broad wrapper; every other slot uses the specific reference/address ref.

### 7.2 Arena-Allocated, Not Interned

Expression nodes allocate into `'i` without deduping. Sub-expressions are `&'i` refs; collections are `&'i [T]` slices.

### 7.3 Derives

Same constraint as typing §7.3: `ConstantFloatIE` holds `f64`, drops Eq/Hash uniformly across the expression hierarchy. `IExpressionResultI` keeps the full derives (only holds `CoordI`).

### 7.4 Collector

`collector.rs` (424 LOC) — Rust-only port of Scala's reflective `Collector` for I-side. `NodeRefI` enum + `all_in_*` / `only_in_*` helpers. Same pattern as typing's `test/traverse.rs`.

---

## Part 8: Error Handling

The instantiator inherits `Result<T, ICompileErrorT>` from typing in principle, but in practice **the instantiator should not produce new errors** — all typing-pass errors were caught earlier, and instantiation is supposed to be total over well-typed input.

When a stub `panic!()` fires during body migration, the convention is the same as typing §8.2: unique identifying message naming the unmigrated branch.

**Inferred:** the absence of a dedicated `ICompileErrorI` enum confirms the design intent — instantiation cannot fail on well-typed input. Any "this should be impossible" condition is a `panic!()`, not a `Result::Err`.

---

## Part 9: Driving The Pass

### 9.1 Top-Level Entry

```rust
pub fn translate<'s, 'ctx, 't, 'i>(
    opts: &'ctx GlobalOptions,
    interner: &'ctx InstantiatingInterner<'s, 'i>,
    typing_interner: &'ctx TypingInterner<'s, 't>,
    keywords: &'ctx Keywords<'s>,
    hinputs: &'ctx HinputsT<'s, 't>,
) -> HinputsI<'s, 'i>
where 's: 'i, 's: 't,
{
    let mut monouts = InstantiatedOutputsI::new();
    let instantiator = InstantiatorI {
        opts, interner, typing_interner, keywords, hinputs,
    };
    instantiator.translate_method(&mut monouts)
}
```

Free-function entry per typing §10.1. No `Result` (see Part 8).

### 9.2 Driver Caching: `InstantiatedCompilation`

`instantiated_compilation.rs:264` wraps the pipeline: holds a `TypingPassCompilation` plus the instantiating arena/interner; `get_monouts(&mut self) -> &HinputsI<'s, 'i>` calls `translate` once and caches the result. Used by `HammerCompilation` and by the production-CLI `FullCompilation`.

### 9.3 Hammer Handoff

The hammer pass receives `HinputsI<'s, 'i>` plus the instantiating interner reference. When the hammer pass finishes, the caller lets local bindings drop in scope order: hammer interner first, instantiating interner second, typing interner third, scout arena last.

---

## Part 10: Invariants Summary

1. **All HashMap keys on interned refs use `PtrKey<'i, T>`** for pointer-based hash/eq (same as typing §11.1). Caveat: don't wrap content-canonical types like `IdI` in `PtrKey` — their own `==` is already ptr-eq on inner fields.
2. **Never use `'static`.** Per typing §11.2 / NUSLX.
3. **`InstantiatedOutputsI` is stack-owned** — its `IndexMap`s are heap-backed and die when `translate` returns. AASSNCMCX doesn't apply.
4. **`R` is phantom.** Primitives carry it via `PhantomData`; compound payloads are arena-interned and held by `&'i` ref. Don't mix.
5. **`reintern.rs` bodies are stubs.** Calls into `reintern::*` will panic; body migration is a separate phase.
6. **Worklists are the source of truth for monomorphization completion.** When all three (`new_functions`, `new_impls`, `new_abstract_funcs`) are empty, instantiation is done.

For the rest (`'s: 'i`, AASSNCMCX, `R`-mode aliasing, copy-out-before-`&mut`, sub-enum stack-only rewrap) see §1.2, §1.5, Part 4, §6.1.

---

## Part 11: Recurring Traps

These each have bitten at least once in body migration or are predicted to bite based on typing-pass experience.

- **Forgetting `where 's: 'i`** on a new output type. Rust won't infer the outlives; the rebase will fail at the first call site that tries to thread `&'s` data through. Declare it on the struct.
- **Mixing `R` modes.** Producing an `sI` value into a `cI` slot, or vice versa, is a compile error — that's the point of the phantom marker. Trace the source's region mode through every recursive call.
- **Calling `reintern::*`.** Bodies are stubs; calls panic at runtime. During body migration, T→I translation happens inline inside `translate_*` methods (see `translate_method` body for the pattern).
- **Introducing an environment type.** Tempting when the substitution map gets threaded through 10 method signatures. Scala parity wins — don't.
- **Mutating in arena.** `InstantiatedOutputsI` is stack — fine to mutate. `HinputsI` is arena — immutable after construction; produce a fresh allocation if you need a changed version.
- **Skipping a worklist push.** A `translate_function` body that omits the "push newly-discovered call onto `new_functions`" step silently fails to monomorphize the callee. Mirror the Scala recursion exactly.
- **TUCMPX false-positive on empty maps.** Scala-faithful `IndexMap::new()` for `bound_param_impl_id_to_bound_arg_impl_id` (`instantiator.rs:1185`) is 1:1 parity, not a stub. Temp-disabled per-site; don't "improve" it.
- **Architect-approved parity gaps.** Two appear (`instantiator.rs:344-346, 397-398`): Scala's `Collector.all(prototypeC, { case PlaceholderTemplataT => vwat() })` sanity check is omitted because Rust's typed `PrototypeI` can't structurally hold a placeholder. Don't try to add it back.

---

## Part 12: Migration State

As of session at experimental tip `18e3e4bda`:

- **AST/scaffolding: ~90% migrated.** Every major type/enum/struct exists with the right shape. `ast/` is mostly real (definitions, not panics).
- **Interner: ~100%.** All 12 families wired and tested.
- **Top-level `translate_method`: real code** (~260 LOC).
- **74 translation methods on `InstantiatorI`: ~15-20% migrated by body.** The majority are signature-only ports with `_`-prefixed params and `panic!()` bodies (`mod.rs:15-17`: "signatures forward-ported … all method bodies remain `panic!()`. Body migration is a separate phase").
- **`reintern.rs`: 0%.** Signature-only; all 11 fns panic.
- **Region collapsers / counter: ~70%.** Substantial real code with ~25 panics each in less-common branches.

**Total `panic!()` count: ~260 across the pass.** Top files: `instantiator.rs` (85), `region_counter.rs` (27), `region_collapser_consistent.rs` (25), `region_collapser_individual.rs` (23), `names.rs` (17), `hinputs.rs` (17), `templata.rs`/`types.rs` (11 each).

Many of the CL bucket's Phase 2 tests landed instantiator body fills (`migrate_rsa`, `migrate_ssa`, `each_on_ssa`, etc. drove 3-5 `translate_*` methods each).

---

## Risks / Open Questions

- **`reintern.rs` body migration.** Inline T→I translation works during current body migration but duplicates code paths. A focused `reintern` migration sweep would consolidate; defer until enough call sites exist to validate the API.
- **Region runtime semantics.** `nI = cI = sI` aliasing is forward-compat for "regions become inert." If the design instead grows runtime region members, the marker types need real differentiation and every interning trio becomes load-bearing.
- **`HinputsI` lookup methods are mostly stubs.** ~20 lookup methods on `hinputs.rs` panic. The hammer pass reads `HinputsI` field-by-field today and avoids the lookups, but downstream consumers will hit them.
- **Substitution-threading explosion.** Some methods take 6+ explicit substitution-like args (`DenizenBoundToDenizenCallerBoundArgI`, `substitutions`, `perspective_region_t`, etc.). Bundling them would break Scala parity; tolerate the threading.
- **`// VISTODO: rename Hinputs everywhere`** at `ast/hinputs.rs:1`. Pending rename; watch for `Hinputs` vs `HinputsI` references during refactors.

---

## Key Files / Directories

| Path | Purpose |
|---|---|
| `docs/architecture/typing-pass-design-v3.md` | Sister doc — the canonical architectural template this doc mirrors |
| `docs/architecture/instantiator_design.md` | This doc |
| `FrontendRust/src/instantiating/instantiator.rs` | 6,506 LOC — `InstantiatorI`, `InstantiatedOutputsI`, `DenizenBoundToDenizenCallerBoundArgI`, `translate` entry, 74 translation methods |
| `FrontendRust/src/instantiating/instantiating_arena.rs` | `InstantiatingArena<'i>` newtype |
| `FrontendRust/src/instantiating/instantiating_interner.rs` | 12-family interner (4 types × 3 region modes), `MustIntern` seal |
| `FrontendRust/src/instantiating/reintern.rs` | T→I boundary helpers (**all stubbed**) |
| `FrontendRust/src/instantiating/region_counter.rs` | `CounterI`, pure_height renumbering map |
| `FrontendRust/src/instantiating/region_collapser_individual.rs` | sI → cI rewrite, single tree |
| `FrontendRust/src/instantiating/region_collapser_consistent.rs` | sI → cI rewrite, paired/consistent numbering |
| `FrontendRust/src/instantiating/collector.rs` | I-side `NodeRefI` + `all_in_*` / `only_in_*` |
| `FrontendRust/src/instantiating/instantiated_compilation.rs` | Pipeline harness, caches `HinputsI` |
| `FrontendRust/src/instantiating/instantiated_humanizer.rs` | I-side pretty-printer |
| `FrontendRust/src/instantiating/ast/types.rs` | `sI`/`nI`/`cI` markers, `CoordI`, `KindIT`, `StructIT`, `InterfaceIT`, ownership/mutability/variability/location enums |
| `FrontendRust/src/instantiating/ast/names.rs` | 2,539 LOC — all I-side name enums/structs |
| `FrontendRust/src/instantiating/ast/ast.rs` | `FunctionDefinitionI`, `FunctionHeaderI`, `PrototypeI`/`SignatureI`, `EdgeI`, exports/externs, attributes, variables |
| `FrontendRust/src/instantiating/ast/expressions.rs` | `ExpressionIE` + 3 expression enums + ~30 variant structs |
| `FrontendRust/src/instantiating/ast/citizens.rs` | `ICitizenDefinitionI`, `StructDefinitionI`, `InterfaceDefinitionI` |
| `FrontendRust/src/instantiating/ast/hinputs.rs` | `HinputsI`, `InstantiationBoundArgumentsI`, ~20 lookup methods (mostly stubs) |
| `FrontendRust/src/instantiating/ast/templata.rs` | `ITemplataI` + 20 concrete templata structs |
| `Frontend/InstantiatingPass/src/dev/vale/instantiating/` | Scala source of truth |
