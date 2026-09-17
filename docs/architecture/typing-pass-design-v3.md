# Typing Pass Design — v3

Architecture and design decisions for the typing pass. This is the authoritative design reference.

The historical design docs (`docs/historical/typing-pass-design-v1.md`, `docs/historical/typing-pass-design-v2.md`, `docs/historical/typing-pass-migration-setup.md`) are obsolete — they each carry "DO NOT FOLLOW" banners.

---

## Part 1: Arena and Lifetime Model

The approach is **pragmatic arena retention**: scout data lives past the typing pass, so typing output can reference it directly. Output types carry `<'s, 't>` — they can hold `&'s` refs into the scout arena. Heavy templatas hold `&'s FunctionA` / `&'s StructA` directly. No re-interning, no side tables for origin data. Envs allocate into the typing arena.

**The trade.** Cost: scout arena memory (FunctionA/StructA/etc.) retained through instantiation; typing arena memory (envs, typing-pass output) retained through instantiation. Rough estimate: hundreds of MB to a few GB for large programs. Fine for batch compilation; reconsider for long-running LSP-style use (see Risks / Open Questions). Benefit: the port maps to Scala line-for-line in most places. No side-table plumbing. Faster to implement, easier to review for Scala parity.

### 1.1 Three Arenas

- **`'p` — Parser arena (`ParseArena<'p>`)**: parser AST, `StrI<'p>`, coords.
- **`'s` — Scout arena (`ScoutArena<'s>`)**: postparser + higher-typing output (`FunctionA<'s>`, `StructA<'s>`, etc.), interned postparser names (`INameS<'s>`, `IRuneS<'s>`, `IImpreciseNameS<'s>`). Unchanged for the typing pass.
- **`'t` — Typing arena (`TypingInterner<'t>`)**: interned typing-pass types (names, kinds, coords, templatas), typing-pass output AST (function defs, expressions, `HinputsT`), **and typing-pass environments** (`IEnvironmentT` and its 9 concrete variants). Created before the typing pass; outlives the typing pass.

All three arenas use `bumpalo`. Arena-allocated structs follow AASSNCMCX: no `Vec`/`HashMap`/`String` inside arena types.

### 1.2 Lifetime Invariants

1. **`'s` outlives `'t`**. Expressed as `where 's: 't` on every output type that transitively holds `&'s` data. Rust does not enforce outlives via drop order — we must declare the bound. Representative structs that carry this bound include `HinputsT<'s, 't>`, `IEnvironmentT<'s, 't>`, `KindT<'s, 't>`, and every interned typing-pass type.
2. **`'t` outlives the typing pass.** Created by the caller before the pass; dropped by the caller after the instantiator is done.
3. **`'s` outlives the instantiator**, because `HinputsT<'s, 't>` contains `&'s` refs. Scout arena drops after the instantiator completes (or later).
4. **Only two arena lifetimes beyond `'p`:** `'s` and `'t`. Envs live in `'t` (with `&'s`-lifetimed content like `FunctionA` refs and `INameS` references flowing through via field types).
5. **Arena borrow convention.** Arena parameters use a short borrow lifetime (elided or named `'ctx`), never the arena's own lifetime. Write `&ScoutArena<'s>` or `&'ctx ScoutArena<'s>`, never `&'s ScoutArena<'s>`. Same for the typing arena. The decoupling works because `arena.alloc(&self, val: T) -> &'t mut T` returns arena-lifetimed data from a short `&self` borrow — callers don't need to hold the arena for all of `'t`/`'s` just to allocate into it.

### 1.3 Arena Construction Order

```
fn top_level_driver() {
    let parse_arena = ParseArena::new();
    // ... parser produces parser AST into 'p ...

    let scout_arena = ScoutArena::new();
    // ... postparser/higher-typing produce into 's ...

    let typing_bump = Bump::new();
    let typing_interner = TypingInterner::new(&typing_bump);
    let hinputs = run_typing_pass(&scout_arena, &typing_interner, ... );

    // ... instantiator runs over HinputsT<'s, 't> ...

    // typing_interner / typing_bump drop (after instantiator): 't dies
    // scout_arena drops: 's dies
    // parse_arena drops: 'p dies
}
```

Rust's drop order (reverse of declaration) naturally enforces `'t < 's < 'p` lifetime nesting.

### 1.4 Where Each Type Lives

| Type | Lifetimes | Arena |
|---|---|---|
| `HinputsT` | `<'s, 't>` | `'t` for the struct itself, holds `&'s` and `&'t` refs |
| `FunctionDefinitionT`, `StructDefinitionT`, `InterfaceDefinitionT`, `ImplT`, `EdgeT` | `<'s, 't>` | `'t` |
| `KindT`, `IdT`, `INameT`, `ITemplataT` (all variants) | `<'s, 't>` | `'t`, interned |
| `CoordT` | `<'s, 't>` | inline Copy, not interned |
| `ReferenceExpressionTE`, `AddressExpressionTE` | `<'s, 't>` | `'t`, not interned |
| `OverloadSetT` | `<'s, 't>` | `'t`, interned |
| Heavy templatas (`FunctionTemplataT`, `StructDefinitionTemplataT`, etc.) | `<'s, 't>` | `'t`; hold `&'t` env refs and `&'s FunctionA`/`&'s StructA` directly |
| `IEnvironmentT` and sub-types | `<'s, 't>` | `'t` (allocated in typing arena) |
| `GlobalEnvironmentT` | `<'s, 't>` | `'t`; one per typing pass |
| `CompilerOutputs` | `<'s, 't>` | stack-owned; heap-backed HashMaps; dies at pass end |
| `Compiler` (god struct) | `<'s, 'ctx, 't>` | stack; dies at pass end |

### 1.5 Type Inventory By Arena

Bucket-level summary. Per-type detail lives in §6.x (type system) and §3 (envs).

- **`'s` scout arena (read-only inputs):** scout-interned coords/names (`StrI`, `RangeS`, `INameS`, `IRuneS`, `IImpreciseNameS`, …) plus higher-typing output (`FunctionA`, `StructA`, `InterfaceA`, `ImplA`).
- **`'t` typing arena, interned:** ~75 concrete name types, `IdT`, the 5 sealed kind payloads + `KindPlaceholderT`, the 6 interned templata payloads, plus `PrototypeT` / `SignatureT` (Value-type, opt-in dedup). See §6.1.
- **`'t` typing arena, allocated but not interned:** definitions (`FunctionDefinitionT`, `FunctionHeaderT`, `StructDefinitionT`, `InterfaceDefinitionT`, `ImplT`, `EdgeT`, `OverrideT`, `ParameterT`), the expression hierarchy (`ReferenceExpressionTE` 48 variants + `AddressExpressionTE` 5 variants), `InstantiationBoundArgumentsT`, the 5 heavy templata payloads, `HinputsT`, and the 9 concrete env types + `GlobalEnvironmentT`. `TemplatasStoreT` lives in this bucket and is held as `&'t TemplatasStoreT` inside envs.
- **Polyvalue enums (~16 bytes, not arena-stored) — closed-set fat pointers, see @TFITCX / @PVECFPZ:** `INameT` + 21 name sub-enums; `KindT` + 3 sub-enums (`ICitizenTT`, `ISubKindTT`, `ISuperKindTT`); `InternedKindPayloadT`; `ITemplataT`; env wrapper enums (`IEnvironmentT` 9 variants, `IInDenizenEnvironmentT` 8-variant subset, `IEnvEntryT` 5). Eq/Hash derive — never hand-roll `ptr::eq(self, other)` on the outer `&self` (silently breaks under by-value use).
- **Inline Copy value-types (16 bytes, not arena-stored):** `CoordT`; `OwnershipT`/`MutabilityT`/`VariabilityT`/`LocationT`/`RegionT`; small-Copy templata variants (`MutabilityTemplataT`, etc.); `IVariableT` 4 variants, `ILocalVariableT` 2 (variant payloads are by-value structs, not refs).
- **Neither arena (stack/heap):** `CompilerOutputs` (stack accumulator), `Compiler` (stack god struct), env builders (heap-backed until `build_in`/`snapshot`), `DeferredActionT` entries in `VecDeque`.

**Casting identity rule.** Polyvalue enums compare structurally on their 16 bytes (tag + inner ref/value) — derive `PartialEq`/`Eq`/`Hash`, which delegates to each variant's inner eq (per-variant ref → inner's `id == id`, value → structural). Concrete payloads and interned templata payloads compare via `ptr::eq` on the `&'t` ref (see @IEOIBZ). Casting up a sub-enum hierarchy is a stack-only rewrap via `From`/`TryFrom`; no interner involvement.

**Equality opt-out for the expression hierarchy** (`ReferenceExpressionTE`, `AddressExpressionTE`, `ExpressionTE`, ~50 per-variant struct types) — see @IEOIBZ.

### 1.6 Mutual-Recursion Shape

The interned type graph is mutually recursive but every edge is an `&'s` or `&'t` ref — pointer-sized, finite. No `Box`/`Vec` needed in arena types. `CoordT → KindT → StructTT → IdT → INameT → ITemplataT → CoordT`. Same-type self-recursion (e.g. `ForwarderFunctionNameT.inner: IFunctionNameT<'s, 't>`) is resolved by inline 16-byte sub-enum values — no `Box`.

---

## Part 2: The God Struct

### 2.1 Architecture

All Scala sub-compilers (FunctionCompiler, ExpressionCompiler, StructCompiler, ImplCompiler, OverloadResolver, InferCompiler, TemplataCompiler, ConvertHelper, DestructorCompiler, VirtualCompiler, SequenceCompiler, ArrayCompiler, EdgeCompiler, BlockCompiler, PatternCompiler, CallCompiler, LocalHelper, …) collapse into a single `Compiler` struct:

```
pub struct Compiler<'s, 'ctx, 't> {
    pub scout_arena: &'ctx ScoutArena<'s>,
    pub typing_interner: &'ctx TypingInterner<'s, 't>,
    pub keywords: &'ctx Keywords<'s>,  // scout-interned keywords are fine
    pub opts: &'ctx TypingPassOptions<'s>,
}
```

Immutable configuration only. Mutable state threads through `&mut CompilerOutputs<'s, 't>` on every call.

**Not on the god struct:**
- `global_env` — Scala builds `globalEnv` as a local inside `compile()`, not as a constructor arg. We do the same: the top-level env is a method parameter on `compile_program` (and anything downstream that needs it), not a field.
- `name_translator` — Scala's `NameTranslator` is a pure helper class with no state; its methods translate postparser names through the interner. In Rust, every `Compiler` method already has `self.scout_arena` and `self.typing_interner`, so the helper adds nothing. Its ~6 translate methods move directly onto `impl Compiler` and the struct is deleted.

### 2.2 `&self` + `&mut coutputs` Enables Re-entrancy

Deep mutual recursion (e.g. `evaluate_expression → evaluate_prefix_call → find_function → evaluate_function_body → evaluate_expression`) works because `&self` allows shared borrow, and `&mut coutputs` is re-borrowed at each call level. With `&mut self`, this would require two simultaneous mutable borrows of the god struct.

### 2.3 Macros

Macros (AsSubtypeMacro, LockWeakMacro, StructDropMacro, etc.) were stateful classes in Scala (holding `keywords`, `expressionCompiler`, etc.). In the god-struct refactor, all that state is already on `Compiler`. So macro structs disappear entirely and their methods become ordinary methods on `Compiler`.

Dispatch is via a small Copy unit-variant enum per Scala macro trait — `FunctionBodyMacro`, `OnStructDefinedMacro`, `OnInterfaceDefinedMacro`, `OnImplDefinedMacro`. Each variant tags which `Compiler` method to call. A given macro may appear in multiple enums iff Scala had it extending multiple traits (e.g. `StructDropMacro extends IFunctionBodyMacro with IOnStructDefinedMacro`).

### 2.4 Delegate Traits Eliminated

In Scala, sub-compilers were wired via anonymous delegate traits (IExpressionCompilerDelegate, IFunctionCompilerDelegate, IInfererDelegate, etc.). With the god struct, these are unnecessary — every method calls `self.method(...)` directly.

### 2.5 Method vs Free Function Under The Collapse

When porting a Scala `def`, decide method-on-`Compiler` vs free function by inspecting two things: what the **Scala body uses**, and whether the Rust port has to be **stored in a closure**.

- **Scala body uses no `this`** (no field reads, no method calls on `this` — pure on its arguments) → may become a Rust free function. *Must* become one when the function is stored in a `Box<dyn Fn>` whose `'static` default would otherwise force a lifetime workaround. Mirror the function name and file location; only the receiver drops. SPDMX exception Q codifies this. Example: `getPuzzles` in the typing-pass solver, `get_puzzles_rune_type` and `get_runes_rune_type` in the postparser solvers.
- **Scala body uses `this`** (reads fields, calls methods on the same class) → must stay a method on `Compiler`, even if the Scala class itself disappears (collapsed by §2.1) or had its methods forwarded through a `delegate: ISomethingDelegate` parameter. The delegate parameter disappears in Rust because §2.4 puts everything on `Compiler`. Example: `solveRule` (was `private def` on `object CompilerRuleSolver`, takes `delegate`; in Rust it's `Compiler::solve_rule(&self, ...)`).

**Common mistake.** Modeling a typing-pass piece after a postparser solver without checking the body. The postparser solvers (`identifiability_solver.rs`, `rune_type_solver.rs`) have free-function `solve_rule_impl` because they have no `Compiler` and no delegate. Typing-pass code touches the delegate methods constantly — those have to be `&self` methods on `Compiler`. Don't generalize the postparser's free-function shape to the typing pass.

---

## Part 3: Environments

### 3.1 Arena-Allocated In `'t`

Environments are allocated directly into the typing arena `'t`. They reference scout data (FunctionA, StructA, INameS) freely via `&'s` fields and reference interned typing-pass data (IdT, ITemplataT, KindT payloads) via the inline wrappers + `&'t` refs.

**Why `'t`, not `'s`**: envs hold TemplatasStoreT which stores `IEnvEntryT::Templata(ITemplataT<'s, 't>)`, which transitively holds `&'t` refs to interned typing-pass payloads. A struct with lifetime `'s` can only hold `&'x` references where `'x: 's`. Since `'s: 't`, `'t: 's` is false — so `'s`-allocated envs cannot hold `&'t` refs. Envs must live in `'t`. The lifetime ordering is still fine: `'t` outlives the typing pass; envs die together with all other typing-pass output when the typing arena drops.

Two parallel wrapper enums. Both hold `&'t` refs to the same concrete payloads, so casting between them is stack-only rewraps (no interner involvement). `IInDenizenEnvironmentT` is an 8-variant subset of `IEnvironmentT`'s 9 — the envs that represent "a denizen currently being compiled" (everything except Package).

```
pub enum IEnvironmentT<'s, 't> {       // 9 variants
    Package(&'t PackageEnvironmentT<'s, 't>),
    Citizen(&'t CitizenEnvironmentT<'s, 't>),
    Function(&'t FunctionEnvironmentT<'s, 't>),
    Node(&'t NodeEnvironmentT<'s, 't>),
    BuildingWithClosureds(&'t BuildingFunctionEnvironmentWithClosuredsT<'s, 't>),
    BuildingWithClosuredsAndTemplateArgs(&'t BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't>),
    General(&'t GeneralEnvironmentT<'s, 't>),
    Export(&'t ExportEnvironmentT<'s, 't>),
    Extern(&'t ExternEnvironmentT<'s, 't>),
}

pub enum IInDenizenEnvironmentT<'s, 't> {  // 8-variant subset (no Package).
    Citizen, Function, Node, BuildingWithClosureds, BuildingWithClosuredsAndTemplateArgs,
    General, Export, Extern
}
```

Every env variant carries `global_env: &'t GlobalEnvironmentT<'s, 't>` as a back-ref (Scala parity; Scala's `IEnvironmentT` has `def globalEnv`). `NodeEnvironmentT` omits it because it delegates via `parent_function_env.global_env` (matching Scala's `override def globalEnv = parentFunctionEnv.globalEnv`).

`IEnvEntryT<'s, 't>` is a 5-variant Polyvalue enum (@TFITCX / @PVECFPZ): `Function(&'s FunctionA<'s>)`, `Struct(&'s StructA<'s>)`, `Interface(&'s InterfaceA<'s>)`, `Impl(&'s ImplA<'s>)`, `Templata(ITemplataT<'s, 't>)`. Not interned. Lives inline in `TemplatasStoreT.name_to_entry`.

Env `Hash`/`PartialEq`/`Eq` come from two layers. The Polyvalue wrapper enums (`IEnvironmentT`, `IInDenizenEnvironmentT`) `#[derive(PartialEq, Eq, Hash)]`; the derive delegates into each variant's inner `&'t FooEnvironmentT::eq`, which compares `self.id == other.id`. `IdT` is interned and impls ptr-eq, so the chain reduces to identity equality on the inner arena ref. **Hand-rolling `std::ptr::eq(self, other)` on the outer `&self` would silently break under by-value use** — Polyvalues are held by value (16-byte Copy) at most call sites, and `self` is then a stack address, not the arena address; two by-value copies of `IEnvironmentT::Citizen(same_ref)` would compare unequal. Deriving avoids that trap (see @PVECFPZ). The variant env types (`PackageEnvironmentT`, `CitizenEnvironmentT`, `FunctionEnvironmentT`, `ExportEnvironmentT`, `ExternEnvironmentT`, `BuildingFunctionEnvironmentWithClosuredsT`, `BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT`) keep their manual `self.id == other.id` impls — a documented exception to @IEOIBZ that's sound because `id: IdT` is sealed/canonical (so id-eq is itself ptr-eq under the hood). `NodeEnvironmentT` uses `(id, life)`, `GeneralEnvironmentT` and `TemplatasStoreT` panic with `vcurious` per Scala. Deriving on the variant env types directly would walk into `TemplatasStoreT`'s maps and diverge from Scala.

### 3.2 `TemplatasStoreT` Uses `ArenaIndexMap`

For 1:1 Scala parity, `TemplatasStoreT` uses `ArenaIndexMap` (the AASSNCMCX-blessed arena-backed hash map) to mirror Scala's `Map[INameT, IEnvEntry]` and `Map[IImpreciseNameS, Vector[IEnvEntry]]`:

```
pub struct TemplatasStoreT<'s, 't> {
    pub templatas_store_name: &'t IdT<'s, 't>,
    pub name_to_entry: ArenaIndexMap<'t, INameT<'s, 't>, IEnvEntryT<'s, 't>>,
    pub imprecise_to_entries: ArenaIndexMap<'t, &'s IImpreciseNameS<'s>, &'t [IEnvEntryT<'s, 't>]>,
}
```

- **`name_to_entry`** — `ArenaIndexMap` keyed by `INameT`. Each Scala `entriesByNameT.get(name)` translates to `name_to_entry.get(&name)`.
- **`imprecise_to_entries`** — `ArenaIndexMap` keyed by `&'s IImpreciseNameS`. Values are `&'t [IEnvEntryT]` slices (the per-imprecise-name overload buckets), matching Scala's `Map[IImpreciseNameS, Vector[IEnvEntry]]`.

`ArenaIndexMap` is not `Copy`/`Clone`, so `TemplatasStoreT` is `/// Arena-allocated` (not value-type). Held as `&'t TemplatasStoreT<'s, 't>` in env structs (matches Scala's GC reference semantics — Scala envs hold a ref to the store, they don't own it). Arena-allocate the store before constructing an env; copy the `&'t` ref into child envs (no clone needed); arena-allocate the result of `add_entries` before storing. `TemplatasStoreBuilder::build_in` already returns `&'t TemplatasStoreT` (allocates internally).

> **Future exploration:** Once body migration is complete and benchmarks exist, profile lookup-heavy paths (overload resolution, name-imprecise lookups during scout-name-to-typing-pass-name resolution). For env kinds where scope sizes are consistently small (block-local, function-local, node) and lookups are frequent, evaluate switching back to unsorted slice-of-pairs with linear scan on a per-env-kind basis.

### 3.3 Mutable Building Phase

During construction, an env is mutable. Builders live on the stack with heap `Vec`s (and heap `HashMap`s for the imprecise-name index); freeze into the typing arena when done. No `&mut FooEnvironmentT` over arena-allocated envs — once a `&'t FooEnvironmentT` is created, it's immutable. Child scopes and mutations produce a fresh builder → fresh arena allocation → fresh `&'t` ref (matches Scala's `NodeEnvironmentBox`, which also allocates a new env per mutation).

`TemplatasStoreBuilder<'s, 't>` is its own stack builder with `Vec<(INameT, IEnvEntryT)>` for the name-to-entry mapping and `HashMap<&'s IImpreciseNameS<'s>, Vec<IEnvEntryT<'s, 't>>>` for the imprecise index (heap during construction, frozen to `ArenaIndexMap` on `build_in`).

Child-scope API: each env has a `make_child(...)` returning a fresh builder (not a `&mut` over arena-allocated data). Of Scala's mutable wrappers, only `NodeEnvironmentBox` survives in Rust — owned-`Vec`-backed mirror of the Scala wrapper, supporting `&mut self` mutation plus a `snapshot` finalizer. The Box pattern is the natural Rust translation of Scala's `var nodeEnvironment` mutation surface (since arena allocation precludes `&mut NodeEnvironmentT`, and arena slices `&'t [...]` aren't growable in place — see the comment above `pub struct NodeEnvironmentBox` in `function_environment_t.rs`). `FunctionEnvironmentBoxT` is **deleted** in Rust (matches SPDMX exception U); functions previously routed through it now operate on `NodeEnvironmentBox` or `FunctionEnvironmentBuilder`. `IDenizenEnvironmentBoxT` (Scala's trait) is also deleted; the call sites that needed it now route through the concrete env types directly.

Builders / boxes support **multiple freezes**, not just one. `snapshot(&self, interner)` produces a fresh `&'t T` view at the current state without consuming the builder, so subsequent mutations and later snapshots remain possible. This mirrors Scala's `Box.snapshot`, which is used in `evaluateFunctionBody` to capture a stable "starting env" before running mutations and then pass both the snapshot and the still-mutating box into `evaluateBlockStatements`. Each snapshot allocates a fresh frozen view (cloning the builder's `Vec`/`HashMap` state into the arena); not free, but not hot — call sites are rare enough that correctness wins over micro-optimization. The `snapshot` pattern applies to `TemplatasStoreBuilder`, `NodeEnvironmentBox`, and `FunctionEnvironmentBuilder`.

**`build_in(self, interner)` (consuming finalizer) lives on the genuine one-shot Rust builders: `TemplatasStoreBuilder`, `BuildingFunctionEnvironmentWithClosuredsBuilder`, and `BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsBuilder`.** These types have no Scala wrapper-equivalent and are used at terminal sites where the `Vec::clone` cost of `snapshot` would be wasted. `NodeEnvironmentBox` and `FunctionEnvironmentBuilder` had their `build_in` deleted: Scala's GC just lets a Box die at scope end with the underlying env living on through references, and no Rust call site needed the consuming form. Code that needs an immutable `&'t T` from either uses `snapshot(&self, interner)` and lets the receiver drop normally.

### 3.4 Transient Reads Use `&IEnvironmentT`

Scala's `NodeEnvironmentBox.snapshot` is called ~36 times in ExpressionCompiler.scala, mostly for transient reads. In Rust:

- **`&IEnvironmentT<'_, 's, 't>`** (elided lifetime) — read-only borrow of the inline wrapper enum. Zero cost, for helpers that only inspect.
- **`&'t IEnvironmentT<'s, 't>`** or **`IEnvironmentT<'s, 't>` by value** — arena-pinned, needed when storing into a parent pointer, output, or a heavy templata.

Promotion to `&'t` happens only at explicit "store this env" points (via `build_in(interner)` on a builder). Transient reads just use `&`. Since `IEnvironmentT` is itself a Copy wrapper (16 bytes), callers can also pass it by value cheaply.

### 3.4a `&'t self` On Arena/Interned Types

Methods on arena-allocated or interned types (`FunctionEnvironmentT`, `NodeEnvironmentT`, `PackageEnvironmentT`, `IdT`, etc.) default to `&self`. Promote to `&'t self` **only** when the method's output borrows from `self` itself — not from a field that's already a `&'t T` ref.

The distinction in concrete cases:

- **`&self` is enough** when the output's `'t` traces through a field that already holds `&'t T`. Field-copy-out flattens `&'a (&'t T)` to `&'t T` automatically: `fn templatas(&self) -> &'t TemplatasStoreT { self.templatas }` works under any receiver lifetime, because the field is already `&'t`.
- **`&'t self` is required** when the output's `'t` is `self`'s own arena lifetime. Three concrete shapes trigger it:
  1. **Embed self as a back-pointer** — `fn make_child(&'t self) -> ChildBuilder { ChildBuilder { parent: self, ... } }`. The child holds `parent: &'t Self`; the borrow of `self` must live as long as `'t`.
  2. **Wrap self in a wrapper enum** — `fn lookup(&'t self, ...) { helper(IEnvironmentT::Function(self), ...) }`. The wrap takes `&'t Self` to embed in the variant.
  3. **Return a borrow into a by-value field** — `fn id(&'t self) -> &'t IdT` when `id: IdT` (owned, not a ref). For Copy fields like `IdT`, prefer returning by value to sidestep this entirely. **Important nuance:** the sidestep ("return by value") is only available when *both* the field type and the consumer can flex to by-value. When the consumer is a *struct field* whose type is fixed by Scala parity to be `&'t T` (e.g. `FunctionTemplataT.outer_env: &'t IEnvironmentT<'s, 't>` mirrors Scala's `outerEnv: IEnvironmentT` GC ref), the sidestep isn't available — the embedded ref must be `&'t`. In that case, promote `&'t self` so the field-projection borrow inherits the receiver's `'t` lifetime. Example: `FunctionEnvironmentT::templata(&'t self) -> FunctionTemplataT<'s, 't>` returning `FunctionTemplataT { outer_env: &self.parent_env, function: self.function }` requires `&'t self` because `parent_env: IEnvironmentT<'s, 't>` is a by-value field and the `&self.parent_env` borrow must live `'t` to match `outer_env`'s declared type.

Pure getters of already-`'t`-ref fields don't need `&'t self`. Promotion is a per-method decision based on the output, not a blanket rule on the type.

This is a structural cost of mirroring Scala's class-with-back-pointers in arena-allocated Rust — Scala's GC keeps parents alive transitively, so back-references just work; Rust requires the lifetime to be explicit, and on arena types that lifetime is `'t`. The convention is documented here so reviewers don't flag it as a smell.

### 3.5 `FunctionTemplata` Is A Plain Computed Value

Matches Scala directly: a method on `FunctionEnvironmentT` allocates a fresh `FunctionTemplataT` into `'t` per call. No caching, no `OnceCell`, no ID minting, no side-table insertion — `FunctionTemplataT` is a small struct holding two pointers, trivially cheap to construct on every call. It's arena-allocated into `'t` but not interned.

### 3.6 Env-ID Uniqueness Not Required

Environments don't need unique `id` fields per instance. No HashMap keys on env's own id anywhere in the design:
- `CompilerOutputs.function_name_to_outer_env` etc. are keyed by **function/type template ids** (genuinely unique per Scala's `vassert`).
- OverloadSet holds the env by direct reference, not by id.
- Heavy templatas hold refs, not ids.

Scala's env-id collisions (NodeEnvironment delegating to parent, GeneralEnvironment reusing caller ids, Box wrappers sharing ids) translate as-is.

### 3.7 `GlobalEnvironmentT`

One instance per typing pass, allocated in `'t` early. Every env carries `global_env: &'t GlobalEnvironmentT<'s, 't>` as a back-ref. Holds `name_to_top_level_environment` (slice of `(IdT, TemplatasStoreT)` pairs) and `builtins: TemplatasStoreT`. Scala's macro fields (`nameToFunctionBodyMacro`, etc.) are **dropped** — macros are methods on `Compiler` now (Part 2.3). The struct carries only data.

### 3.8 Why Not Two-Tier Per-Denizen Arenas (Yet)

The migration-phase design uses one `'t` typing arena. A post-migration redesign splits into a program-wide `'out` outputs arena and per-top-level-denizen `'scratch` arenas. Full write-up: `FrontendRust/docs/reasoning/environments-per-denizen-long-term.md`. Not in scope for this migration.

---

## Part 4: CompilerOutputs

### 4.1 Structure

Mutable accumulator threaded through the pass. Carries `<'s, 't>`. 23 fields:

- **Function registries.** `return_types_by_signature: HashMap<PtrKey<'t, SignatureT>, CoordT>`; `signature_to_function: HashMap<PtrKey<'t, SignatureT>, &'t FunctionDefinitionT>`.
- **Declaration tracking.** `function_declared_names: HashMap<PtrKey<'t, IdT>, RangeS>`; `type_declared_names: HashSet<PtrKey<'t, IdT>>`.
- **Env back-refs (keyed by function/type template id).** `function_name_to_outer_env`, `function_name_to_inner_env`, `type_name_to_outer_env`, `type_name_to_inner_env` — all `HashMap<PtrKey<'t, IdT>, IInDenizenEnvironmentT<'s, 't>>`. Narrower in-denizen wrapper, matching Scala.
- **Type metadata.** `type_name_to_mutability: HashMap<PtrKey<'t, IdT>, ITemplataT>`; `interface_name_to_sealed: HashMap<PtrKey<'t, IdT>, bool>`.
- **Definitions.** `struct_template_name_to_definition`, `interface_template_name_to_definition` — `HashMap<PtrKey<'t, IdT>, &'t {Struct|Interface}DefinitionT>`.
- **Impls + reverse indexes.** `all_impls: HashMap<PtrKey<'t, IdT>, &'t ImplT>`; `sub_citizen_template_to_impls`, `super_interface_template_to_impls` — `HashMap<PtrKey<'t, IdT>, Vec<&'t ImplT>>` (the two `Vec`-valued exceptions to §4.3, mutated as new impls are discovered; OK because `CompilerOutputs` is stack-owned, not arena-allocated).
- **Exports/externs.** `kind_exports`, `function_exports`, `kind_externs`, `function_externs` — `Vec<&'t {Kind|Function}{Export|Extern}T>`.
- **Instantiation bounds.** `instantiation_name_to_bounds: HashMap<PtrKey<'t, IdT>, &'t InstantiationBoundArgumentsT>`.
- **Deferred queues.** `deferred_actions: VecDeque<DeferredActionT>`; `finished_deferred_function_body_compiles: HashSet<PtrKey<'t, PrototypeT>>`; `finished_deferred_function_compiles: HashSet<PtrKey<'t, IdT>>`.

No origin side tables — heavy templatas and OverloadSets hold scout refs directly.

### 4.2 `PtrKey<'t, T>` Newtype For HashMap Keys

Interned `&'t T` refs hash by pointer identity, not structural equality. Newtype wrapper gives the custom Hash/Eq. Lives in `src/typing/ptr_key.rs`:

- `PartialEq` uses `std::ptr::eq`.
- `Hash` casts the inner `&'t T` to `*const ()` and hashes that.
- Manual `Copy`/`Clone` (not `#[derive]`) so they work for any `T: ?Sized` without requiring `T: Copy`. The `?Sized` bound is forward-compat for `dyn` / slice keys.

### 4.3 Side-Table Access Pattern — Copy Out Before &mut

The env back-refs need the copy-out pattern to satisfy the borrow checker:

```
// Does not compile:
let env = coutputs.function_name_to_outer_env.get(&PtrKey(id)).unwrap();
self.do_stuff(coutputs, env, ...);  // &mut coutputs rejected; env borrows from it

// Works — copy out first:
let env_t: IInDenizenEnvironmentT<'s, 't> =
    *coutputs.function_name_to_outer_env.get(&PtrKey(id)).unwrap();
self.do_stuff(coutputs, env_t, ...);  // OK; env_t is Copy'd out
```

**Invariant: most HashMap values in `CompilerOutputs` are pointer-sized Copy refs** (`&'s T`, `&'t T`) — enables the copy-out-then-mutate pattern.

**Two exceptions:** `sub_citizen_template_to_impls` and `super_interface_template_to_impls` hold `Vec<&'t ImplT>` because the reverse-index lookups return multiple impls per key. Access these with `mem::take` / `drain` + reinsert, not by-reference read.

### 4.4 Deferred Actions

Avoid `Box<dyn FnOnce>` via structured enum:

```
pub enum DeferredActionT<'s, 't> {
    EvaluateFunctionBody {
        prototype: &'t PrototypeT<'s, 't>,
        function_env: &'t FunctionEnvironmentT<'s, 't>,
        origin: &'s FunctionA<'s>,
    },
    EvaluateFunction {
        name: &'t IdT<'s, 't>,
        calling_env: IInDenizenEnvironmentT<'s, 't>,
        origin: &'s FunctionA<'s>,
        template_args: &'t [ITemplataT<'s, 't>],
    },
    // Add variants as needed.
}
```

`function_env` on `EvaluateFunctionBody` is `&'t FunctionEnvironmentT` (the concrete env type, matches Scala). `calling_env` on `EvaluateFunction` is `IInDenizenEnvironmentT` (the narrow wrapper, also matches Scala).

Drain loop: `pop_front()` → match → call `Compiler` method with `&mut coutputs`. No self-borrow because once the action is popped, it doesn't alias anything inside `coutputs`. No `Box`, no `dyn`, no hidden captures.

**Invariant:** `DeferredActionT` variants hold only owned or `Copy` context (`&'s` and `&'t` refs, small values). They never reference `CompilerOutputs` itself.

### 4.5 Speculative Writes Are Idempotent

During overload resolution, `attempt_candidate_banner` writes to `coutputs` even for candidates that may later be rejected. Safe because writes are idempotent — re-writing asserts equality. No rollback machinery.

---

## Part 5: OverloadSet

### 5.1 Transient Kind, But Holds Env Directly

- Overload resolution always completes during the typing pass; nothing unresolved reaches the instantiator.
- But an `OverloadSet`-kinded `ReinterpretTE` survives in output as an inert type tag (the instantiator elides it via `InstantiationBoundArgumentsT.runeToFunctionBoundArg`).

Since `'s` and `'t` live through instantiation, we hold the env directly — no `ctx_id` indirection needed:

```
pub struct OverloadSetT<'s, 't> {
    pub env: IInDenizenEnvironmentT<'s, 't>,
    pub name: &'s IImpreciseNameS<'s>,  // scout-interned, fine to hold
}

pub enum KindT<'s, 't> {
    // ... other variants ...
    OverloadSet(&'t OverloadSetT<'s, 't>),
}
```

### 5.2 Construction

A method on Compiler interns an `OverloadSetT { env, name }` into a `KindT::OverloadSet`. No side-table insertion, no `ctx_id`, no `&mut coutputs` required.

### 5.3 Overload Resolution Uses The Env Directly

The resolver reads `overload_set.env` directly, walks its parent chain, looks up candidates, recurses into nested OverloadSets via their own `env` fields. Matches Scala's `OverloadResolver.scala:210` pattern verbatim.

### 5.4 Post-Pass

`OverloadSetT<'s, 't>` survives in `HinputsT<'s, 't>` inside inert `ReinterpretTE` args. Its env and name refs remain valid for as long as `'t` and `'s` live. The instantiator elides these on sight.

---

## Part 6: Type System Types

Every lifetime-parameterized struct in this section has an implicit `where 's: 't` bound (§1.2).

### 6.0 Phantom-`+T`-Erasure Principle

**Any Scala `+T <: SomeTrait` type parameter is erased to monomorphic widest-form in Rust.** The Rust port carries no leaf-type generic parameter; callers pattern-match at the use site to narrow, just as Scala does at runtime (`+T` is JVM-erased anyway). This applies uniformly to:

- `IdT[+T <: INameT]` → `IdT<'s, 't>` with `local_name: INameT<'s, 't>` (§6.3)
- `PrototypeT` (no generic in Rust; monomorphic)
- `SignatureT` (no generic in Rust; monomorphic)
- `ITemplataT[+T <: ITemplataType]` → `ITemplataT<'s, 't>` (§6.6)
- `PlaceholderTemplataT[+T <: ITemplataType]` → `PlaceholderTemplataT<'s, 't>` with `tyype: ITemplataType<'s>` (the widest existing postparser enum, *not* `ITemplataT` — the field holds a type *descriptor*, not a templata value)
- `PrototypeTemplataT[T <: IFunctionNameT]` → `PrototypeTemplataT<'s, 't>` (no inner generic; just holds `&'t PrototypeT`)

Rationale + alternatives are recorded in `FrontendRust/docs/reasoning/idt-typed-view-alternatives.md`.

**Erasure rule: widen the field to match the bound, not the enclosing trait.** `IdT[+T <: INameT]` has `local_name: T` — the bound is `INameT`, so the erased field is `INameT<'s, 't>`. `PlaceholderTemplataT[+T <: ITemplataType]` has `tyype: T` — the bound is `ITemplataType`, so the erased field is `ITemplataType<'s>`. A common mistake is widening to `ITemplataT<'s, 't>` (the *value* enum), but `ITemplataType` and `ITemplataT` are different type families: `ITemplataType<'s>` is a postparser-defined type *descriptor* ("what kind of templata?" — e.g. `IntegerTemplataType`, `MutabilityTemplataType`), while `ITemplataT<'s, 't>` is an actual templata *value* ("what does this templata hold?" — e.g. `Integer(42)`, `Kind(&'t StructTT)`). `ITemplataType` is `'s`-only because it never holds typing-pass data.

**No `'t`-lifetime re-interned versions of `StrI`, `IImpreciseNameS`, `RangeS`, `CodeLocationS`, `PackageCoordinate`, `FileCoordinate`.** Use the scout-arena versions directly. Same for `ITemplataType<'s>` — the existing postparsing enum is reused unchanged from typing-pass code.

### 6.1 IDEPFL Dual-Enum Pattern, Sealing, and Scala-Parity Interning

The Interned-vs-Value-type rule (Scala's `IInterning` is the spec, plus `IdT` as a Rust-side divergence), the `MustIntern` seal pattern, and the dual-enum (Val + canonical) lookup machinery are documented in @WVSBIZ ("Scala Parity Overrides The Heuristics"), @SICZ, and @DSAUIMZ. Read those before adding a new Interned type. The typing-pass-specific shape:

**Currently sealed (21 types):** `IdT`, the 15 transient (slice-bearing) Name types (`ImplNameT`, `ImplBoundNameT`, `OverrideDispatcherNameT`, `OverrideDispatcherCaseNameT`, `ExternFunctionNameT`, `FunctionNameT`, `FunctionBoundNameT`, `PredictedFunctionNameT`, `LambdaCallFunctionTemplateNameT`, `LambdaCallFunctionNameT`, `StructNameT`, `InterfaceNameT`, `AnonymousSubstructImplNameT`, `AnonymousSubstructConstructorNameT`, `AnonymousSubstructNameT`), and the 5 kind payloads (`StructTT`, `InterfaceTT`, `StaticSizedArrayTT`, `RuntimeSizedArrayTT`, `OverloadSetT`). The ~57 simple Name types (`PrimitiveNameT`, `PackageTopLevelNameT`, etc.) are Interned per Scala-parity but **not yet sealed** — extending the seal requires `*NameValT` mirrors. Tracked in `FrontendRust/docs/todo.md`.

**Family-level `intern_*` HashMaps (4 families):**
- `intern_name(INameValT) -> INameT` — names. The 15 transient ones above carry `'tmp` per @DSAUIMZ; the ~57 simple ones reuse the canonical as Val.
- `intern_id(IdValT) -> &'t IdT` — monomorphic, slice-bearing (see §6.3).
- `intern_kind_payload(InternedKindPayloadValT) -> InternedKindPayloadT` — the 5 sealed kind types have `*ValT` mirrors (`StructTTValT`, etc.); `KindPlaceholderT` (Value-type) reuses canonical-as-Val.
- `intern_templata_payload(InternedTemplataPayloadValT) -> InternedTemplataPayloadT` — opt-in dedup. All 5 simple variants (`CoordTemplataT`, `KindTemplataT`, `PlaceholderTemplataT`, `PrototypeTemplataT`, `IsaTemplataT`) are Value-type now; `CoordListTemplataT` keeps a `'tmp` Val for its slice.

`SignatureT` / `PrototypeT` are Value-type per Scala parity but flow through opt-in `intern_signature` / `intern_prototype` helpers; other sites construct `SignatureT { id }` directly since the inner `IdT` is sealed-canonical.

**Wrapper enums are NOT interned.** `INameT` + 21 name sub-enums, `KindT` + 3 Kind sub-enums (`ICitizenTT`/`ISubKindTT`/`ISuperKindTT`), and `ITemplataT` are all 16-byte inline Copy values. Sub-enum casts are stack-only rewraps via `From`/`TryFrom`.

**Val types use content-based Hash, not ptr-based** — so query-Val (`'tmp`) and stored-Val (`'t`) hash consistently. The `ptr::eq` treatment stays for canonical `&'t` refs per @IEOIBZ.

### 6.2 INameT Hierarchy

~60 concrete name types, ~21 sub-trait enums. Every name type carries `<'s, 't>`.

**DAG rule (critical).** Scala's sealed-trait hierarchy is a DAG — some concrete names extend multiple sub-traits (`ExternFunctionNameT extends IFunctionNameT with IFunctionTemplateNameT`, etc.). In Rust this becomes: **a shared concrete name gets a variant in each sub-enum it belongs to.** The same `&'t ExternFunctionNameT` can appear as `IFunctionNameT::ExternFunction(...)` and `IFunctionTemplateNameT::ExternFunction(...)`.

**Sub-enums are inline-owned Copy values, NOT arena-interned.** Only concrete name types and INameT itself live in the typing arena. The intermediate sub-enums are 16-byte derived-Copy values built on the stack when needed. Casting is a pure stack-only rewrap via `.into()` — no interner round-trip.

Identity: two concrete names compare via `ptr::eq`. Two owned sub-enum values compare structurally — but since the tag + inner pointer is 16 bytes, this is a 2-word compare and still cheap.

Bridging via `From<X> for SubEnum` (narrow → wide, infallible) and `TryFrom<INameT<'s, 't>> for SubEnum` (wide → narrow, fallible, match-and-rewrap on the stack). Real implementations in `names.rs`, no interner involvement.

**Self-referential variants** (e.g. `ForwarderFunctionNameT.inner: IFunctionNameT<'s, 't>`) are inline-owned; since sub-enums are 16 bytes and Copy they can live directly as struct fields without `Box` or `&'t`.

### 6.3 `IdT<'s, 't>` — Monomorphic, Interned

```
pub struct IdT<'s, 't>
where 's: 't,
{
    pub package_coord: &'s PackageCoordinate<'s>,  // scout-lifetimed
    pub init_steps: &'t [INameT<'s, 't>],          // inline INameT values
    pub local_name: INameT<'s, 't>,                // always the widest form
    pub _must_intern: MustIntern,                  // seal per @SICZ
}
```

Per §6.0, Scala's `IdT[+T <: INameT]` phantom outer parameter is **erased**. Callers that need a specific leaf-name variant pattern-match on `local_name` at the point of use.

IdT is the one Rust-side divergence from Scala-parity interning per §6.1. Scala leaves `IdT` as a plain case class, but Rust seals it because `IdT::eq` uses pointer comparison on the `init_steps` slice — soundness requires that slice to come from the canonical arena allocation in `intern_id` (see @SICZ). Without sealing, two `IdT` literals with structurally equal `init_steps` would have different slice pointers and compare unequal, which is exactly the original `signature-id-mismatch` bug.

IdT defines custom PartialEq/Eq/Hash (not derive) per @IEOIBZ:
- `package_coord`: pointer-eq (scout arena canonicalizes PackageCoordinate).
- `init_steps`: slice data pointer + length compare — the typing interner canonicalizes `&'t [INameT]` slices as a whole per IDEPFL, so equal content ⇒ equal slice pointer (guaranteed by the seal).
- `local_name`: structural compare on the inline INameT (16 bytes).

Interning uses one HashMap per IDEPFL keyed by `IdValT<'s, 't, 'tmp>` (transient, `'tmp`-borrowed init_steps slice). No widen/try_narrow methods — pattern-match instead.

### 6.4 `CoordT<'s, 't>` — Inline Copy

`CoordT` holds `ownership: OwnershipT`, `region: RegionT`, `kind: KindT<'s, 't>`. Small (~24 bytes), Copy, passed by value. Not interned — structural eq.

### 6.5 `KindT<'s, 't>` — Inline Wrapper

```
pub enum KindT<'s, 't> {
    // Primitives inline (small)
    Never(NeverT), Void(VoidT), Int(IntT), Bool(BoolT), Str(StrT), Float(FloatT),
    // Non-primitives — &'t refs to interned payloads
    Struct(&'t StructTT<'s, 't>),
    Interface(&'t InterfaceTT<'s, 't>),
    StaticSizedArray(&'t StaticSizedArrayTT<'s, 't>),
    RuntimeSizedArray(&'t RuntimeSizedArrayTT<'s, 't>),
    KindPlaceholder(&'t KindPlaceholderT<'s, 't>),
    OverloadSet(&'t OverloadSetT<'s, 't>),
}
```

KindT is **inline-owned** (not arena-interned): 16 bytes tag + ref, Copy. Concrete payloads (StructTT etc.) are arena-interned; the wrapper just tags them.

Same philosophy for the three Kind sub-enum families (ICitizenTT, ISubKindTT, ISuperKindTT). Casts between them are stack-only rewraps via From/TryFrom.

### 6.6 `ITemplataT<'s, 't>` — Inline Wrapper, Phantom Parameters Erased

Per §6.0, Scala's `ITemplataT[+T <: ITemplataType]` outer phantom type parameter is **erased** — the wrapper is a monomorphic `ITemplataT<'s, 't>` enum, and callers that need a specific kind pattern-match on the variant.

Variants split into three groups:
- **Arena-cached value variants** (`&'t` ref to arena-allocated payload): Coord, Kind, Placeholder, Prototype, Isa, CoordList. Per §6.1 these payloads are now `/// Value-type` per Scala parity (the Templata family doesn't `extend IInterning` in Scala) — but they still flow through `intern_templata_payload` for opt-in dedup. The variant holds an `&'t` ref because the payload lives in the arena; equality on the ref is structural (auto-derived `PartialEq` on the payload struct, recursing into properly-canonical `IdT` and identity-bearing env refs).
- **Inline Copy-value variants**: Mutability, Variability, Ownership, Integer(i64), Boolean(bool), String(StrI<'s>) — *scout-lifetimed, not re-interned*, RuntimeSizedArrayTemplate, StaticSizedArrayTemplate.
- **Heavy templatas** (`&'t` to env-ref-holding payloads): Function, StructDefinition, InterfaceDefinition, ImplDefinition, ExternFunction.

ITemplataT itself is **inline-owned**, not arena-interned.

Heavy templata payloads hold `&'t` env refs and `&'s` scout refs:

```
pub struct FunctionTemplataT<'s, 't> {
    pub outer_env: IInDenizenEnvironmentT<'s, 't>,
    pub function: &'s FunctionA<'s>,
}
// StructDefinitionTemplataT / InterfaceDefinitionTemplataT / ImplDefinitionTemplataT
// each hold `IInDenizenEnvironmentT` and one of `&'s StructA` / `&'s InterfaceA` / `&'s ImplA`.
pub struct ExternFunctionTemplataT<'s, 't> {
    pub header: &'t FunctionHeaderT<'s, 't>,
}
```

Heavy-templata `Eq`/`Hash` per @IEOIBZ: each wrapper just `#[derive(PartialEq, Eq, Hash)]`, which deref-calls into the inner identity-bearing type's manual identity impl. For `FunctionA`/`StructA`/`InterfaceA`/`ImplA`/`FunctionHeaderT` that's `std::ptr::eq`; for the Polyvalue `IEnvironmentT` (@PVECFPZ) it's the derived enum impl, which in turn delegates to each variant env's `self.id == other.id` (resolving to `IdT` ptr-eq). Either way the wrapper doesn't repeat the identity logic.

`PlaceholderTemplataT.tyype: ITemplataType<'s>` (the widest postparser enum, *not* ITemplataT). The Scala field is `tyype: T` where `T <: ITemplataType` — a *type descriptor*, not a templata value.

`PrototypeTemplataT`'s Scala inner type parameter is also erased (§6.0) — just holds `&'t PrototypeT<'s, 't>`.

### 6.7 Mutual Recursion

Types form a mutually recursive graph (`CoordT → KindT → StructTT → IdT → INameT → ITemplataT → CoordT`). Most links are `&'s` or `&'t` refs — pointer-sized, finite. The name sub-enums are the exception — inline 16-byte tagged pointers, one word larger than a raw ref but still finite and Copy. No `Box`/`Vec` needed.

---

## Part 7: Expression AST

### 7.1 Three Enums

- `ReferenceExpressionTE<'s, 't>` — 48 variants (LetNormal, If, While, FunctionCall, Consecutor, Construct, Reinterpret, …).
- `AddressExpressionTE<'s, 't>` — 5 variants (LocalLookup, ReferenceMemberLookup, …).
- `ExpressionTE<'s, 't>` — 2-variant wrapper: `Reference(&'t ReferenceExpressionTE)` / `Address(&'t AddressExpressionTE)`.

**Narrow-use rule.** `ConstructTE` is the **only** expression node that holds the broad ExpressionTE wrapper. Every other slot uses the specific `&'t ReferenceExpressionTE` or `&'t AddressExpressionTE`.

### 7.2 Arena-Allocated, Not Interned

Expression nodes are allocated into `'t` but not deduped. Sub-expressions are `&'t` refs; collections are arena slices. No `*ValT` companions, no intern methods, no From/TryFrom bridges across the wrapper enums.

`IExpressionResultT<'s, 't>` is a 2-variant inline Copy enum (`Reference(ReferenceResultT) | Address(AddressResultT)`), both result structs hold a single CoordT.

### 7.3 Derives — `#[derive(PartialEq, Debug)]` Only

`ConstantFloatTE` holds `f64`, which can't derive Eq/Hash. The whole expression-AST family drops Eq/Hash uniformly. `IExpressionResultT` / `ReferenceResultT` / `AddressResultT` keep the full `Copy, Clone, PartialEq, Eq, Hash, Debug` — they only hold CoordT, no f64 transitively.

### 7.4 Visitor/Collector Pattern

Same as parsing/postparsing: `NodeRefT<'s, 't>` enum, `visit_*` functions, entry points, `collect_where_tnodes!`/`collect_only_tnodes!` macros.

---

## Part 8: Error Handling

### 8.1 `Result<T, CompileErrorT>` With `?`

Scala's `throw CompileErrorExceptionT(...)` → `return Err(CompileErrorT::...)` with `?` propagation. Single catch boundary at top-level `Compiler::compile()`.

`ICompileErrorT<'s, 't>` has 55 real variants (filled in Slab 14).

### 8.2 Panic For Unimplemented

`panic!()` with unique identifying messages for unmigrated branches. Fully-stub functions acceptable during incremental migration.

---

## Part 9: Keywords

`Keywords<'s>` — re-uses scout arena for interned strings. Same Keywords instance can serve the typing pass and subsequent passes as long as `'s` lives. No typing-specific `Keywords<'t>` needed.

---

## Part 10: Driving The Pass

### 10.1 Top-Level Entry

```
pub fn run_typing_pass<'s, 'ctx, 't>(
    scout_arena: &'ctx ScoutArena<'s>,
    typing_interner: &'ctx TypingInterner<'s, 't>,
    keywords: &'ctx Keywords<'s>,
    opts: &'ctx TypingPassOptions<'s>,
    program_a: &'s ProgramA<'s>,
) -> Result<HinputsT<'s, 't>, ICompileErrorT<'s, 't>>
where 's: 't,
{
    let compiler = Compiler::new(scout_arena, typing_interner, keywords, opts);
    let mut coutputs = CompilerOutputs::new();

    compiler.compile_program(&mut coutputs, program_a)?;
    compiler.drain_all_deferred(&mut coutputs);

    let hinputs = HinputsT { /* materialized from coutputs */ };
    Ok(hinputs)
    // coutputs drops; its HashMaps (storing 's/'t refs) die cheaply
    // scout_arena and typing_interner survive; instantiator can use them
}
```

No `finalize()`. Natural scope exit.

### 10.2 Instantiator Handoff

The instantiator receives `HinputsT<'s, 't>` plus both arena references. When it's done, the caller lets local bindings drop in scope order (§1.3): typing interner first, scout arena second, parse arena last.

---

## Part 11: Invariants Summary

Invariants that aren't fully stated elsewhere in this doc:

1. **All HashMap keys on interned refs use `PtrKey<'t, T>`** for pointer-based hash/eq. (Caveat: `PtrKey` is wrong for content-canonical types like `IdT` whose own `==` is already pointer-equality on inner fields — wrapping in `PtrKey` would compare outer addresses instead. Use `PtrKey` only for `@IEOIBZ`-style identity types.)
2. **Never use `'static`.** All data must live in `'p`, `'s`, or `'t` (or on the stack). `'static` bypasses the arena system: a `&'static T` has a different pointer than a structurally identical `&'s T`, breaking pointer equality for interned types. See `Luz/shields/NeverUseStaticLifetime-NUSLX.md`.
3. **Don't try to fix a `Box<dyn Fn> + &self` deferred-borrow with a lifetime parameter.** A boxed closure capturing `&self` keeps that shared borrow live for the full lifetime of the box. Holding the box across other `&self` calls deadlocks the borrow checker — and threading a closure-lifetime parameter (e.g. `'fn_lt` on `SimpleSolverState`) only sidesteps the `'static` default; it doesn't resolve the conflict. The fix is always to drop the receiver: convert the captured method to a free function (§2.5), or pass the data the closure needs by value. Check call sites before proposing lifetime-threading.

For the rest (`'s` outlives `'t`, AASSNCMCX, copy-out-before-`&mut`, speculative writes idempotent, overload resolution completes, env equality unused, envs live in `'t`, `DeferredActionT` never refs `CompilerOutputs`, arena-param short borrow, +T erasure, Val content-hash, heavy-templata refs) — see §1.1, §1.2, §3.1, §3.6, §4.3, §4.4, §4.5, §5.1, §6.0, §6.1, §6.6.

---

## Risks / Open Questions

- **LSP / long-running use**: scout arena retention through instantiation is memory-heavy; single-arena typing makes batch-only the safe mode. See `FrontendRust/docs/reasoning/environments-per-denizen-long-term.md`.
- **Post-migration design revisits**: the inline-owned-wrapper philosophy makes casts free but gives up compile-time "this IdT's local_name is a FunctionName" assertions. See `FrontendRust/docs/reasoning/idt-typed-view-alternatives.md`.
- **TypingInterner perf**: six hashbrown HashMap maps with heterogeneous `'tmp`→`'t` lookup via Equivalent. Works today but hasn't been measured. Profile before optimizing.
- **Body migration ordering**: the panic stubs have implicit dependency ordering — some bodies call other stubbed methods. The test-driven approach naturally discovers this, but expect some batches to require implementing a chain of 3-5 bodies before a test passes end-to-end.
- **Incremental compilation**: serializing HinputsT to disk requires a serialization boundary that breaks `'s` refs. Batch compilation only for now.
- **Parallelization**: single-threaded design (`!Sync` arenas, stack CompilerOutputs). Per-function parallelization is a later topic.
- **Typing storage → two-tier per-denizen arenas**: scheduled as a post-body-migration redesign. See `FrontendRust/docs/reasoning/environments-per-denizen-long-term.md`.

---

## Key Files / Directories

| Path | Purpose |
|---|---|
| `docs/architecture/typing-pass-design-v3.md` | this doc — architecture + design decisions |
| `FrontendRust/docs/reasoning/environments-per-denizen-long-term.md` | two-tier per-denizen target + LSP direction |
| `FrontendRust/docs/reasoning/idt-typed-view-alternatives.md` | IdT monomorphic / typed-view decision |
| `FrontendRust/docs/reasoning/` | other design-decision docs |
| `FrontendRust/src/typing/names/names.rs` | ~3100 lines: all name types, IdT, From/TryFrom bridges, ValT companions |
| `FrontendRust/src/typing/types/types.rs` | KindT + sub-enums + concrete Kind payloads |
| `FrontendRust/src/typing/templata/templata.rs` | ITemplataT + payload structs |
| `FrontendRust/src/typing/ast/ast.rs` | PrototypeT, SignatureT, IdT-holding structs |
| `FrontendRust/src/typing/ast/expressions.rs` | ~2100 lines: 3 enums + 53 payload structs |
| `FrontendRust/src/typing/compiler_outputs.rs` | CompilerOutputs 23-field struct + 54 methods |
| `FrontendRust/src/typing/templata_compiler.rs` | all 49 method sigs; IBoundArgumentsSource enum + IPlaceholderSubstituter trait |
| `FrontendRust/src/typing/compiler_error_reporter.rs` | ICompileErrorT 55-variant enum |
| `FrontendRust/src/typing/typing_interner.rs` | 560 lines: 6-family HashMap design, ~84 wrappers |
| `FrontendRust/src/typing/compiler.rs` | god struct (Compiler, 4 fields) |
| `FrontendRust/src/typing/hinputs_t.rs` | HinputsT real-fielded with new() constructor |
| `FrontendRust/src/typing/compilation.rs` | TypingPassOptions, run_typing_pass entry point |
| `FrontendRust/src/typing/env/environment.rs` | 5 of 9 env types + wrapper enums + GlobalEnvironmentT + TemplatasStoreT |
| `FrontendRust/src/typing/env/function_environment_t.rs` | 4 of 9 env types + builders + variables |
| `FrontendRust/src/typing/env/i_env_entry.rs` | IEnvEntryT 5-variant enum |
| `FrontendRust/src/typing/test/` | 14 test files; 173 test bodies ready to drive body migration |
| `.claude/hooks/check-scala-comments` | pre-commit hook guarding `/* scala */` blocks |
