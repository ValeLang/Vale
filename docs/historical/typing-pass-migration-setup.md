# DO NOT FOLLOW — Folded Into TL-HANDOFF.md

> **This document's still-correct content has been folded into `TL-HANDOFF.md` at the repository root.** TL-HANDOFF is now the single authoritative source for the typing-pass design and operating spec.
>
> This file is preserved for audit-trail (it was the design-of-record from 2026-04-14 to 2026-04-24, with TL-HANDOFF acting as the override layer until the merge). Reading it directly is no longer necessary; the TL-HANDOFF version has all corrections applied (Slab-4 lifetimes, post-Slab-9 status, the phantom-`+T`-erasure principle for monomorphic types) integrated into a single coherent doc.

---

# Typing Pass Migration Design (folded into TL-HANDOFF.md)

This document describes the architectural decisions for migrating `src/typing/` from Scala to Rust.

The approach is **pragmatic arena retention**: scout data lives past the typing pass, so typing output can reference it directly. Output types carry `<'s, 't>` — they can hold `&'s` refs into the scout arena. Heavy templatas hold `&'s FunctionA` / `&'s StructA` directly. No re-interning, no side tables for origin data. Envs allocate into the typing arena (not scout — see §3.1).

## Status (2026-04-20)

Handed off; see `TL-HANDOFF.md` at the repository root for the current state summary, design decisions that supersede parts of this doc, and the next-slab entry point.

**Phase 1 — lifetime-parameter correction** — complete. Every `pub struct` / `pub enum` / `pub trait` in `src/typing/` carries the generics specified in §1.5.

**Phase 2 — god-struct refactor** — complete. All ~20 sub-compilers and 15 macros merged onto a single `Compiler<'s, 'ctx, 't>`. Macro dispatch via four Copy unit-variant enums. Vestigial `*Compiler` PhantomData holders deleted. Only `Compiler` remains. `IInfererDelegate` stays vestigial (fn signatures in `compiler_solver.rs` still take `&dyn`; later cleanup). See `FrontendRust/docs/migration/handoff-god-struct-progress.md`.

**Phase 3 — body migration (Slabs 0–14+)** — data-definition and half of signature-rewrite complete; ~122 method signatures remain before body migration.
- ✅ **Slab 0**: arena substrate scaffolding.
- ✅ **Slab 1** (leaf types): merged as commit `9fd7641c`.
- ✅ **Slab 2** (name hierarchy): tagged `slab-2-complete`. `IdT` is **monomorphic** (not generic `T: Copy` as described in §6.3 below — see `FrontendRust/docs/reasoning/idt-typed-view-alternatives.md`). Sub-enum families (`INameT`, `IFunctionNameT`, etc. — 22 of them) went **inline-owned** (not interned) as part of this slab.
- ✅ **Slab 3** (Kind/Coord/Templata trio): tagged `slab-3-complete`. `KindT` and `ITemplataT` also went **inline-owned** wrappers with `&'t` refs to interned concrete payloads (same philosophy as Slab 2). `PrototypeT` and `SignatureT` are monomorphic.
- ✅ **Slab 4** (environments + real interner bodies): tagged `slab-4-complete`. 9 env structs + 2 wrapper enums + sibling `IInDenizenEnvironmentT` + `GlobalEnvironmentT` + `TemplatasStoreT` + `IEnvEntryT` + variable types; 9 env-specific builders with `build_in(interner) -> &'t`. `TypingInterner<'s, 't>` gained real bodies with 6 family-level HashMaps keyed on tagged-union Val enums (`INameValT` / `InternedKindPayloadValT` / `InternedTemplataPayloadValT`), plus ~84 per-concrete wrappers via macros. Envs override `quest.md` §3.1 to live in `'t`, not `'s` (a `'s`-allocated env can't hold `&'t` refs; see §3.1 below). Five heavy-templata env refs flipped from `&'s` to `&'t`. `NodeEnvironmentBox`, `FunctionEnvironmentBoxT`, and `IDenizenEnvironmentBoxT` deleted — builder-freeze pattern subsumes them. Full spec + retrospective: `FrontendRust/docs/migration/handoff-slab-4.md`; current arena architecture: `FrontendRust/docs/architecture/typing-pass-arenas.md`; long-term storage target: `FrontendRust/docs/reasoning/environments-per-denizen-long-term.md`.
- ✅ **Slab 5** (expression AST): tagged `slab-5-complete`. 3 enums (`ReferenceExpressionTE` 48 variants, `AddressExpressionTE` 5 variants, `ExpressionTE` 2-variant wrapper) + 53 payload structs + `IExpressionResultT` / `ReferenceResultT` / `AddressResultT`. `#[derive(PartialEq, Debug)]` family-wide (because of `f64` in `ConstantFloatTE`). Handoff at `FrontendRust/docs/migration/handoff-slab-5.md`.
- ✅ **Slab 6** (`CompilerOutputs` data shape): tagged `slab-6-complete`. 23-field struct + `::new()` constructor + `PtrKey<'t, T>` newtype in its own module + `DeferredActionT<'s, 't>` 2-variant enum replacing the two `DeferredEvaluatingFunction*` stubs. Method stubs left as free-fn panics (see Slab 8). Handoff at `handoff-slab-6.md`.
- ✅ **Slab 7** (`HinputsT` residual cleanup + Compiler scaffolding + `run_typing_pass` entry point): tagged `slab-7-complete`. Two `()` → `&'t PrototypeT<'s, 't>` flips in `hinputs_t.rs` (`InstantiationBoundArgumentsT.rune_to_bound_prototype` + `InstantiationReachableBoundArgumentsT.citizen_rune_to_reachable_prototype`); `_phantom` field deleted; `Compiler::compile_program` and `Compiler::drain_all_deferred` panic-stub methods added; `run_typing_pass` free fn in `compilation.rs` with the `ScoutArena` / `TypingInterner` / `Keywords` / `TypingPassOptions` / `ProgramA` entry-point signature. Handoff at `handoff-slab-7.md`.
- ✅ **Slab 8** (`CompilerOutputs` method signatures): tagged `slab-8-complete`. All 54 private free-fn stubs in `compiler_outputs.rs` lifted into one-fn `impl<'s, 't> CompilerOutputs<'s, 't> where 's: 't` blocks with correct `&self`/`&mut self` receivers, arena-ref returns (`&'t`), `IdT` by value, `PtrKey` HashMap key wrapping in the `get_instantiation_name_to_function_bound_to_rune` return, and Slab-4 `&'t IInDenizenEnvironmentT<'s, 't>` on all env params. Panic messages bumped to `"Unimplemented: Slab 10 — body migration"` (superseded by subsequent re-scoping; see §12.1). Handoff at `handoff-slab-8.md`.
- ✅ **Slab 9** (`object TemplataCompiler` method signatures): tagged `slab-9-complete`. All 35 private free-fn stubs in `templata_compiler.rs` lifted into one-fn `impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't> where 's: 't` blocks with `&self` receiver (god-struct refactor pattern, matching the existing 14 `class TemplataCompiler` impls at the file tail). `interner` and `keywords` parameters dropped across every signature (accessed via `self.typing_interner` / `self.keywords`). `bound_arguments_source: &'t dyn IBoundArgumentsSource<'s, 't>` on substitute_* methods (marker-trait dysfunction acknowledged as Slab 15+ cleanup). `IPlaceholderSubstituter` returns placeholder-`()` with TODO (trait not yet defined). Handoff at `handoff-slab-9.md`.
- ⏳ **Slabs 10–13** (remaining method signatures across sub-compilers): ~122 methods inside existing `impl Compiler` blocks are still bare `(&self)` with Scala `/* def ... */` anchors showing real params, plus ~6 methods in `compiler.rs` with `()`-placeholder param/return types and ~7 orphan free-fn stubs (`local_helper.rs`, `struct_compiler.rs`, `compiler.rs` static utilities). Proposed grouping: **Slab 10** — `compiler.rs` residuals + local_helper/struct_compiler orphans + `templata_compiler.rs` 14 tail methods (~27 sigs); **Slab 11** — expression layer (`expression_compiler.rs` 21 + `pattern_compiler.rs` 12 + `block_compiler.rs` 2 + `call_compiler.rs` 4 = ~39 sigs); **Slab 12** — solver + resolver (`infer_compiler.rs` 14 + `overload_resolver.rs` 11 + `impl_compiler.rs` 9 + `reachability.rs` 1 = ~35 sigs); **Slab 13** — function/citizen/macros (`function_compiler.rs` 8 + `function_body_compiler.rs` 3 + `destructor_compiler.rs` 2 + `struct_compiler_core.rs` 6 + `anonymous_interface_macro.rs` 4 = ~23 sigs).
- ⏳ **Slab 14+** (method body migration): driven by failing tests. Begin with trivial `CompilerOutputs` one-liner bodies (e.g. `lookup_function` = `self.signature_to_function.get(&PtrKey(signature)).copied()`) to establish the pattern, then graduate to TemplataCompiler id-transforms, then substitution engine, then sub-compiler bodies. `ICompileErrorT` variant filling and the `IBoundArgumentsSource` marker-trait → enum conversion land inside this phase as body patterns demand them.

**Prior overrides, now folded into this doc.** The inline-owned-wrapper refactor (IdT monomorphic; `KindT`/`ITemplataT`/name-sub-enums as inline wrappers over interned concrete payloads) that Slabs 2-3 shipped has been incorporated in §§6.1-6.7. The env `'t`-arena correction and sibling `IInDenizenEnvironmentT` enum decided during Slab-4 planning is reflected in §3. Historical context for both is in `docs/reasoning/idt-typed-view-alternatives.md` and `docs/reasoning/environments-per-denizen-long-term.md` respectively. `TL-HANDOFF.md` at repo root keeps a running list of overrides for anyone auditing the sequence of decisions.

Build is clean throughout: `cargo check --lib` passes with 0 errors and 0 warnings.

Known deferred items (separate from the slab plan):
- `HinputsT` now has real fields as of Slab 7 (11 fields, Vec/HashMap-backed per documented AASSNCMCX deviation). Its ~10 `lookup_*` method stubs and the `sub_citizen_to_interface_to_edge` post-hoc-computed field materialization are Slab 14+ body-migration work.
- Sub-compiler free-fn stubs in `env/environment.rs` / `env/function_environment_t.rs` (`entry_matches_filter`, `entry_to_templata`, `lookup_with_name_inner`, etc.) are slice-pipeline artifacts; Slabs 10-13 either wire them into `Compiler` methods or delete them during the per-file signature sweeps.
- Macro dispatcher methods on `Compiler` (`dispatch_function_body_macro` etc.) are not wired yet — Slab 14+, once env-based macro resolution bodies are needed.
- `IInfererDelegate` trait stays vestigial because `compiler_solver.rs` fn sigs still reference `&dyn IInfererDelegate`. Slab 12 signature rewrites remove it.
- `IBoundArgumentsSource` is a marker trait (empty body + two empty unit structs) that the Scala code pattern-matches against. Slab 9 passes it as `&'t dyn` in signatures; Slab 14+ body migration flips it to a pattern-matchable enum when the first body demands it.
- `IPlaceholderSubstituter` trait referenced by `get_placeholder_substituter` / `get_placeholder_substituter_ext` return types doesn't exist yet as a Rust trait. Slab 9 returns `()` with TODO comments; Slab 14+ defines the trait and fills returns.
- `ICompileErrorT<'s, 't>` enum stays `_Phantom`-only with ~80 Scala variants in `/* */` blocks. Variant filling is Slab 14+ as failing tests demand specific errors.
- `LocationInFunctionEnvironmentT.path: Vec<i32>` in `ast/ast.rs` violates AASSNCMCX (heap `Vec` inside a conceptually-arena type). Not blocking; a future cleanup flips it to `&'t [i32]`.
- **Typing storage → two-tier per-denizen arenas** is the scheduled post-signature-rewrite redesign. Migration-phase uses one `'t` arena; the deferred design splits storage into a program-wide `'out` outputs arena (resolved definitions + skeleton envs + interned types) and per-top-level-denizen `'scratch` scratchpad arenas (working envs + transient templatas + solver state), with a side-table + `EnvIdx(u32)` for arena-struct → scratchpad-env handoff. Bounds peak memory per-denizen; enables `HashMap`-in-env for O(1) method lookup; avoids `Rc`-in-arena leak hazards. Predicated on an empirical cross-denizen edge audit (full findings in the reasoning doc). Further out, this is also the load-bearing step toward LSP support. See `FrontendRust/docs/reasoning/environments-per-denizen-long-term.md`.

### The Trade

- **Cost:** scout arena memory (FunctionA/StructA/etc.) retained through instantiation; typing arena memory (envs, typing-pass output) retained through instantiation. Rough estimate: hundreds of MB to a few GB for large programs. Fine for batch compilation; reconsider for long-running LSP-style use.
- **Benefit:** the port maps to Scala line-for-line in most places. No side-table plumbing. Faster to implement, easier to review for Scala parity.

---

## Part 1: Arena and Lifetime Model

### 1.1 Three Arenas

- **`'p` — Parser arena (`ParseArena<'p>`)**: parser AST, `StrI<'p>`, coords.
- **`'s` — Scout arena (`ScoutArena<'s>`)**: postparser + higher-typing output (`FunctionA<'s>`, `StructA<'s>`, etc.), interned postparser names (`INameS<'s>`, `IRuneS<'s>`, `IImpreciseNameS<'s>`). Unchanged for the typing pass.
- **`'t` — Typing arena (`TypingInterner<'t>`)**: interned typing-pass types (names, kinds, coords, templatas), typing-pass output AST (`FunctionDefinitionT`, expressions, `HinputsT`), **and typing-pass environments** (`IEnvironmentT` and its 9 concrete variants). Created before the typing pass; outlives the typing pass.

All three arenas use `bumpalo`. Arena-allocated structs follow AASSNCMCX: no `Vec`/`HashMap`/`String` inside arena types.

### 1.2 Lifetime Invariants

1. **`'s` outlives `'t`**. Expressed as `where 's: 't` on every output type that transitively holds `&'s` data. Rust does **not** enforce outlives via drop order — we must declare the bound. Representative structs that carry this bound include `HinputsT<'s, 't>`, `IEnvironmentT<'s, 't>`, `KindT<'s, 't>`, and every interned typing-pass type.
2. **`'t` outlives the typing pass.** Created by the caller before `run_typing_pass`; dropped by the caller after the instantiator is done.
3. **`'s` outlives the instantiator**, because `HinputsT<'s, 't>` contains `&'s` refs. Scout arena drops after the instantiator completes (or later).
4. **Only two arena lifetimes beyond `'p`:** `'s` and `'t`. Envs live in `'t` (with `&'s`-lifetimed content like `FunctionA` refs and `INameS` references flowing through via field types).
5. **Arena borrow convention.** Arena parameters use a short borrow lifetime (elided or named `'ctx`), never the arena's own lifetime. Write `&ScoutArena<'s>` or `&'ctx ScoutArena<'s>`, never `&'s ScoutArena<'s>`. Same convention for the typing arena: `&TypingInterner<'t>` or `&'ctx TypingInterner<'t>`. This matches `ParseArena` and `ScoutArena` usage in the parser, postparser, and higher-typing passes. The decoupling works because `arena.alloc(&self, val: T) -> &'t mut T` returns arena-lifetimed data from a short `&self` borrow — callers don't need to hold the arena for all of `'t`/`'s` just to allocate into it.

### 1.3 Arena Construction Order

```rust
fn top_level_driver() {
    let parse_arena = ParseArena::new();
    // ... parser produces parser AST into 'p ...
    
    let scout_arena = ScoutArena::new();
    // ... postparser/higher-typing produce into 's ...
    
    let typing_interner = TypingInterner::new();
    let hinputs = run_typing_pass(&scout_arena, &typing_interner, /* program_a */);
    
    // ... instantiator runs over HinputsT<'s, 't> ...
    
    // typing_interner drops (after instantiator): 't dies
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
| `FunctionTemplataT`, `StructDefinitionTemplataT`, etc. | `<'s, 't>` | `'t`; hold `&'s FunctionA`/`&'s StructA` directly |
| `IEnvironmentT` and sub-types | `<'s, 't>` | `'t` (allocated in typing arena; see §3.1) |
| `GlobalEnvironmentT` | `<'s, 't>` | `'t`; one per typing pass |
| `CompilerOutputs` | `<'s, 't>` | stack-owned; heap-backed HashMaps; dies at pass end |
| `Compiler` (god struct) | `<'s, 'ctx, 't>` | stack; dies at pass end |

### 1.5 Full Type Inventory — Which Arena Each Type Lives In

A complete per-type checklist for Slabs 1–6. For each `// mig:` stub, the correct lifetime signature is determined here. Use this as the authoritative reference when filling definitions.

**`'s` scout arena — existing, unchanged by the typing pass:**
- `StrI<'s>`, `PackageCoordinate<'s>`, `FileCoordinate<'s>`, `RangeS<'s>`, `CodeLocationS<'s>` — postparser-interned, reused as-is
- `INameS<'s>`, `IRuneS<'s>`, `IImpreciseNameS<'s>` — postparser names, reused as-is
- `FunctionA<'s>`, `StructA<'s>`, `InterfaceA<'s>`, `ImplA<'s>` — higher-typing output, referenced directly by heavy templatas and envs

**`'t` typing arena — interned (dedup via `TypingInterner`):**
- Concrete name structs (`FunctionNameT`, `StructNameT`, etc. — ~60 of them)
- `IdT<'s, 't>` — monomorphic (always widest form with `local_name: INameT<'s, 't>`)
- Concrete Kind payloads: `StructTT<'s, 't>`, `InterfaceTT<'s, 't>`, `StaticSizedArrayTT<'s, 't>`, `RuntimeSizedArrayTT<'s, 't>`, `KindPlaceholderT<'s, 't>`, `OverloadSetT<'s, 't>`
- Interned templata payloads: `CoordTemplataT<'s, 't>`, `KindTemplataT<'s, 't>`, `PlaceholderTemplataT<'s, 't>`, `PrototypeTemplataT<'s, 't>`, `IsaTemplataT<'s, 't>`, `CoordListTemplataT<'s, 't>`
- `PrototypeT<'s, 't>`, `SignatureT<'s, 't>` — monomorphic

**`'t` typing arena — allocated but NOT interned:**
- `FunctionDefinitionT<'s, 't>`, `FunctionHeaderT<'s, 't>`
- `StructDefinitionT<'s, 't>`, `InterfaceDefinitionT<'s, 't>`, `ImplT<'s, 't>`
- `EdgeT<'s, 't>`, `OverrideT<'s, 't>`
- `ParameterT<'s, 't>`
- `ReferenceExpressionTE<'s, 't>` (~38 variants), `AddressExpressionTE<'s, 't>` (~6 variants)
- `InstantiationBoundArgumentsT<'s, 't>`
- Heavy templata payloads: `FunctionTemplataT`, `StructDefinitionTemplataT`, `InterfaceDefinitionTemplataT`, `ImplDefinitionTemplataT`, `ExternFunctionTemplataT`
- `HinputsT<'s, 't>` (pass output)
- **Environments (Slab 4):** 9 concrete variants (`PackageEnvironmentT`, `CitizenEnvironmentT`, `FunctionEnvironmentT`, `NodeEnvironmentT`, `BuildingFunctionEnvironmentWithClosuredsT`, `BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT`, `GeneralEnvironmentT`, `ExportEnvironmentT`, `ExternEnvironmentT`), plus `GlobalEnvironmentT<'s, 't>` (one per pass). `TemplatasStoreT<'s, 't>` is held inline-by-value inside envs (not independently arena-allocated).

**Inline Copy, NOT interned (Scala-verbatim structural equality):**
- Name sub-enum families (22 of them): `INameT`, `IFunctionNameT`, `IFunctionTemplateNameT`, `IInstantiationNameT`, `IStructNameT`, `IInterfaceNameT`, `ICitizenNameT`, `ISubKindNameT`, `ISuperKindNameT`, `ICitizenTemplateNameT`, `IStructTemplateNameT`, `IInterfaceTemplateNameT`, `ISubKindTemplateNameT`, `ISuperKindTemplateNameT`, `ITemplateNameT`, `IImplNameT`, `IImplTemplateNameT`, `IPlaceholderNameT`, `IVarNameT`, `IRegionNameT`, `CitizenNameT`, `CitizenTemplateNameT`. Each is a 16-byte inline Copy value (tag + 8-byte concrete ref).
- Kind wrapper enums: `KindT<'s, 't>`, `ICitizenTT<'s, 't>`, `ISubKindTT<'s, 't>`, `ISuperKindTT<'s, 't>` — same pattern. Non-primitive variants hold `&'t StructTT` etc.; primitive variants (`Never`, `Void`, `Int`, `Bool`, `Str`, `Float`) hold tiny Copy payloads inline.
- `ITemplataT<'s, 't>` — also inline wrapper. Variants mix `&'t` refs to interned templata payloads with inline Copy-value variants (`Integer(i64)`, `Boolean(bool)`, `Mutability(MutabilityTemplataT)`, etc.).
- `CoordT<'s, 't>` — passed by value, `kind: KindT<'s, 't>` inline.
- `OwnershipT`, `MutabilityT`, `VariabilityT`, `LocationT`, `RegionT` — pure Copy enums.
- Small templata value variants: `MutabilityTemplataT`, `VariabilityTemplataT`, `OwnershipTemplataT`, `RuntimeSizedArrayTemplateTemplataT`, `StaticSizedArrayTemplateTemplataT`.
- Env wrapper enums (Slab 4): `IEnvironmentT<'s, 't>` (9 variants, each holding `&'t FooEnvironmentT`), `IInDenizenEnvironmentT<'s, 't>` (6-variant subset with the same `&'t` payloads), `IEnvEntryT<'s, 't>` (5-variant: `Function`/`Struct`/`Interface`/`Impl` holding `&'s …A<'s>`, `Templata` holding `ITemplataT<'s, 't>`), `IVariableT<'s, 't>` (4 concrete-by-value variants), `ILocalVariableT<'s, 't>` (2-variant subset).

**Casting identity rule.** Wrapper enums compare structurally on their 16 bytes (tag + inner ref). Concrete payloads and interned templata payloads compare via `ptr::eq` on the `&'t` ref. Casting up a sub-enum hierarchy (concrete → sub-enum → super-sub-enum → widest) is a stack-only rewrap via `From`/`TryFrom` impls; no interner involvement.

**Neither arena (stack / heap-Vec / HashMap):**
- `CompilerOutputs<'s, 't>` — stack-owned accumulator, dies at pass end
- `Compiler<'s, 'ctx, 't>` — stack god struct
- Env builders (`NodeEnvironmentBuilder`, `FunctionEnvironmentBuilder`, `TemplatasStoreBuilder`, etc.) — stack-local with heap `Vec`s / `HashMap`s until `build_in(&TypingInterner<'t>)` freezes into `'t`.
- `DeferredActionT` entries in `VecDeque` — owned structs, not `Box<dyn>`

### 1.6 Mutual-Recursion Shape

The interned type graph is mutually recursive but every edge is an `&'s` or `&'t` ref — pointer-sized, finite. No `Box`/`Vec` needed in arena types.

```
CoordT → KindT → StructTT → IdT → INameT → ITemplataT → CoordT
                   ↓
              (also CoordT → KindT directly via CoordT.kind field)
```

Same-type self-recursion (e.g. `ForwarderFunctionNameT.inner: &'t IFunctionNameT<'s, 't>`, `ForwarderFunctionTemplateNameT.inner: &'t IFunctionTemplateNameT<'s, 't>`) is resolved by the `&'t` indirection — no `Box`.

**Ordering implication for Slab filling:** since every edge is a reference, you can declare types in any order within a slab — forward declaration is free. But for human review and staged compilability, order from leaves upward: primitives → `RegionT`/`CoordT` inline → `IdT` generic → `INameT` hierarchy → non-primitive `KindT` variants → `ITemplataT` → prototype/signature → envs → expressions.

---

## Part 2: The God Struct

### 2.1 Architecture

All Scala sub-compilers collapse into a single `Compiler` struct:

```rust
pub struct Compiler<'s, 'ctx, 't> {
    pub scout_arena: &'ctx ScoutArena<'s>,
    pub typing_interner: &'ctx TypingInterner<'t>,
    pub keywords: &'ctx Keywords<'s>,  // scout-interned keywords are fine
    pub opts: &'ctx TypingPassOptions<'s>,
}
```

Immutable configuration only. Mutable state threads through `&mut CompilerOutputs<'s, 't>` on every call.

**Not on the god struct:**
- `global_env` — Scala builds `globalEnv` as a local inside `compile()`, not as a constructor arg. We do the same: the top-level env is a method parameter on `compile_program` (and anything downstream that needs it), not a field.
- `name_translator` — Scala's `NameTranslator` is a pure helper class with no state; its methods just translate postparser names through the interner. In Rust, every `Compiler` method already has `self.scout_arena` and `self.typing_interner`, so the helper adds nothing. Its ~6 translate methods move directly onto `impl Compiler` and the struct is deleted.

### 2.2 `&self` + `&mut coutputs` Enables Re-entrancy

Deep mutual recursion works because `&self` allows shared borrow, and `&mut coutputs` is re-borrowed at each call level.

### 2.3 Macros

Macros (`AsSubtypeMacro`, `LockWeakMacro`, `StructDropMacro`, etc.) were stateful classes in Scala (holding `keywords`, `expressionCompiler`, etc.). In the god-struct refactor, all that state is already on `Compiler`. So macro structs disappear entirely and their methods become ordinary methods on `Compiler`.

Dispatch is via a small Copy unit-variant enum per Scala macro trait — `FunctionBodyMacro`, `OnStructDefinedMacro`, `OnInterfaceDefinedMacro`, `OnImplDefinedMacro`. Each variant tags which `Compiler` method to call.

```rust
pub enum FunctionBodyMacro {
    LockWeak,
    AsSubtype,
    StructDrop,
    StructConstructor,
    // ... one variant per Scala class extending IFunctionBodyMacro
}

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't> where 's: 't {
    // Individual method per Scala class, name-suffixed to avoid collisions.
    pub fn generate_function_body_lock_weak(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        env: &FunctionEnvironmentT<'s, 't>,
        // ...
    ) -> (FunctionHeaderT<'s, 't>, ReferenceExpressionTE<'s, 't>) { ... }

    // Central dispatcher for dynamic lookups out of the env.
    pub fn dispatch_function_body_macro(
        &self,
        which: FunctionBodyMacro,
        coutputs: &mut CompilerOutputs<'s, 't>,
        env: &FunctionEnvironmentT<'s, 't>,
        // ...
    ) -> (FunctionHeaderT<'s, 't>, ReferenceExpressionTE<'s, 't>) {
        match which {
            FunctionBodyMacro::LockWeak => self.generate_function_body_lock_weak(coutputs, env, /* ... */),
            FunctionBodyMacro::AsSubtype => self.generate_function_body_as_subtype(coutputs, env, /* ... */),
            // ...
        }
    }
}
```

A given macro may appear in multiple enums iff Scala had it extending multiple traits (e.g. `StructDropMacro extends IFunctionBodyMacro with IOnStructDefinedMacro` → appears in both `FunctionBodyMacro` and `OnStructDefinedMacro`). Globally the shared implementation is a single `Compiler` method per Scala method, reused across the dispatcher matches.

### 2.4 Delegate Traits Eliminated

In Scala, sub-compilers wired via delegate traits. With the god struct, every method calls `self.method(...)` directly. No `IExpressionCompilerDelegate`, `IInfererDelegate`, etc.

---

## Part 3: Environments

### 3.1 Arena-Allocated In `'t`

Environments are allocated directly into the typing arena `'t`. They reference scout data (`FunctionA`, `StructA`, `INameS`) freely via `&'s` fields and reference interned typing-pass data (`IdT`, `ITemplataT`, `KindT` payloads) via the inline wrappers + `&'t` refs defined in Slabs 2-3.

**Why `'t`, not `'s`** (this corrects earlier versions of this doc): envs hold `TemplatasStoreT` which stores `IEnvEntryT::Templata(ITemplataT<'s, 't>)`, which transitively holds `&'t` refs to interned typing-pass payloads. A struct with lifetime `'s` can only hold `&'x` references where `'x: 's`. Since `'s: 't` (scout outlives typing), `'t: 's` is false — so `'s`-allocated envs cannot hold `&'t` refs. Envs must live in `'t`. The lifetime ordering is still fine: `'t` outlives the typing pass; envs die together with all other typing-pass output when the typing arena drops.

Two parallel wrapper enums. Both hold `&'t` refs to the same concrete payloads, so casting between them is stack-only rewraps (no interner involvement). `IInDenizenEnvironmentT` is a 6-variant subset of `IEnvironmentT`'s 9 — the envs that represent "a denizen currently being compiled."

```rust
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IEnvironmentT<'s, 't> {
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

#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IInDenizenEnvironmentT<'s, 't> {
    Citizen(&'t CitizenEnvironmentT<'s, 't>),
    Function(&'t FunctionEnvironmentT<'s, 't>),
    Node(&'t NodeEnvironmentT<'s, 't>),
    BuildingWithClosureds(&'t BuildingFunctionEnvironmentWithClosuredsT<'s, 't>),
    BuildingWithClosuredsAndTemplateArgs(&'t BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't>),
    General(&'t GeneralEnvironmentT<'s, 't>),
    // No Package/Export/Extern — those are not InDenizen in Scala.
}

pub struct NodeEnvironmentT<'s, 't> {
    pub parent_function_env: &'t FunctionEnvironmentT<'s, 't>,
    pub parent_node_env: Option<&'t NodeEnvironmentT<'s, 't>>,
    pub node: &'s IExpressionSE<'s>,
    pub life: LocationInFunctionEnvironmentT<'s>,
    pub templatas: TemplatasStoreT<'s, 't>,
    pub declared_locals: &'t [IVariableT<'s, 't>],
    pub unstackified_locals: &'t [IVarNameT<'s, 't>],
    pub restackified_locals: &'t [IVarNameT<'s, 't>],
    pub default_region: RegionT,
}
```

Every env variant carries `global_env: &'t GlobalEnvironmentT<'s, 't>` (Scala parity; Scala's `IEnvironmentT` has `def globalEnv`). `NodeEnvironmentT` omits it because it delegates via `parent_function_env.global_env` (matching Scala's `override def globalEnv = parentFunctionEnv.globalEnv`).

`IEnvEntryT<'s, 't>` is a 5-variant inline Copy enum (Slab 4 materializes it out of Scala's `IEnvEntry` sealed trait):

```rust
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IEnvEntryT<'s, 't> {
    Function(&'s FunctionA<'s>),
    Struct(&'s StructA<'s>),
    Interface(&'s InterfaceA<'s>),
    Impl(&'s ImplA<'s>),
    Templata(ITemplataT<'s, 't>),
}
```

Not interned. Lives inline in `TemplatasStoreT.name_to_entry`. Five is the full Scala variant count.

### 3.2 `TemplatasStoreT` Uses Arena-Backed Slice Pairs

Following AASSNCMCX, we avoid heap HashMap inside arena types. Slices live in `'t`:

```rust
#[derive(PartialEq, Eq, Hash, Debug)]
pub struct TemplatasStoreT<'s, 't> {
    pub templatas_store_name: &'t IdT<'s, 't>,
    pub name_to_entry: &'t [(INameT<'s, 't>, IEnvEntryT<'s, 't>)],
    pub imprecise_to_entries: &'t [(&'s IImpreciseNameS<'s>, &'t [IEnvEntryT<'s, 't>])],
}
```

- **`name_to_entry`** — unsorted arena slice. Linear scan in lookup.
- **`imprecise_to_entries`** — nested-slice layout: outer slice of `(key, inner_slice)` pairs; each inner slice is its own arena allocation containing the (1-3 typical, occasionally more) entries sharing that imprecise name.

Unsorted slices, linear-scan lookup (`.iter().find(...)` / `.iter().filter(...)`). Common-case scope size is small (~5–10 entries), where linear scan beats binary search on cache behavior and avoids the sort cost at construction. If profiling later identifies a hot slow scope (a package-level env with hundreds of entries, e.g.), switch that specific env kind to sorted-binary or to a different lookup structure — but not as a default. Decision: linear-scan everywhere during migration.

Lives inline-by-value in env structs (about 48 bytes: 3 slice-pointers + the `&'t IdT` back-ref).

### 3.3 Mutable Building Phase

During construction, an env is mutable. Builders live on the stack with heap `Vec`s (and heap `HashMap`s for the imprecise-name index); freeze into the typing arena when done. No `&mut NodeEnvironmentT` over arena-allocated envs — once a `&'t NodeEnvironmentT` is created, it's immutable. Child scopes and mutations produce a fresh builder → fresh arena allocation → fresh `&'t` ref (matches Scala's `NodeEnvironmentBox`, which also allocates a new `NodeEnvironmentT` per mutation):

```rust
pub struct NodeEnvironmentBuilder<'s, 't> {
    pub parent_function_env: &'t FunctionEnvironmentT<'s, 't>,
    pub parent_node_env: Option<&'t NodeEnvironmentT<'s, 't>>,
    pub node: &'s IExpressionSE<'s>,
    pub life: LocationInFunctionEnvironmentT<'s>,
    pub templatas_builder: TemplatasStoreBuilder<'s, 't>,
    pub declared_locals: Vec<IVariableT<'s, 't>>,
    pub unstackified_locals: Vec<IVarNameT<'s, 't>>,
    pub restackified_locals: Vec<IVarNameT<'s, 't>>,
    pub default_region: RegionT,
}

impl<'s, 't> NodeEnvironmentBuilder<'s, 't> {
    pub fn build_in(self, interner: &TypingInterner<'t>) -> &'t NodeEnvironmentT<'s, 't> {
        // Freeze the templatas builder first — it owns its own heap state.
        let templatas = self.templatas_builder.build_in(interner);
        // Arena-alloc each slice individually.
        let declared_locals = interner.bump().alloc_slice_copy(&self.declared_locals);
        let unstackified_locals = interner.bump().alloc_slice_copy(&self.unstackified_locals);
        let restackified_locals = interner.bump().alloc_slice_copy(&self.restackified_locals);
        interner.bump().alloc(NodeEnvironmentT {
            parent_function_env: self.parent_function_env,
            parent_node_env: self.parent_node_env,
            node: self.node,
            life: self.life,
            templatas,
            declared_locals,
            unstackified_locals,
            restackified_locals,
            default_region: self.default_region,
        })
    }
}
```

`TemplatasStoreBuilder<'s, 't>` is its own stack builder with `Vec<(INameT, IEnvEntryT)>` for the name-to-entry mapping and `HashMap<&'s IImpreciseNameS<'s>, Vec<IEnvEntryT<'s, 't>>>` for the imprecise index (heap HashMap during construction, frozen to nested arena slices on `build_in`).

Child-scope API: `NodeEnvironmentT::make_child(…)` returns a fresh `NodeEnvironmentBuilder` (not a `&mut NodeEnvironmentT` — infeasible over arena-allocated data). Scala's `makeChild` returns a new case class; Rust returns a builder. `NodeEnvironmentBox` (Scala's mutable wrapper) is deleted in Rust — the builder-freeze pattern subsumes it.

### 3.4 Transient Reads Use `&IEnvironmentT`

Scala's `NodeEnvironmentBox.snapshot` is called ~36 times in ExpressionCompiler.scala, mostly for transient reads (passing an `IInDenizenEnvironmentT` to helpers like `isTypeConvertible`).

In Rust, we distinguish:
- **`&IEnvironmentT<'_, 's, 't>`** (elided lifetime) — read-only borrow of the inline wrapper enum (which holds an `&'t` inside). Zero cost, for helpers that only inspect.
- **`&'t IEnvironmentT<'s, 't>`** or **`IEnvironmentT<'s, 't>` by value** — arena-pinned, needed when storing into a parent pointer, output, or a heavy templata.

Promotion to `&'t` happens only at explicit "store this env" points (via `build_in(interner)` on a builder). Transient reads just use `&`. Since `IEnvironmentT` is itself a Copy wrapper (16 bytes), callers can also pass it by value cheaply.

### 3.5 `FunctionTemplata` Is A Plain Computed Value

Matches Scala directly:

```rust
impl<'s, 't> FunctionEnvironmentT<'s, 't> {
    pub fn templata(&self, interner: &TypingInterner<'t>) -> &'t FunctionTemplataT<'s, 't> {
        // FunctionTemplataT is "allocated but NOT interned" — per-call arena alloc, no dedup.
        interner.bump().alloc(FunctionTemplataT {
            outer_env: self.parent_env,
            function: self.function,
        })
    }
}
```

No caching, no `OnceCell`, no ID minting, no side-table insertion. `FunctionTemplataT` is a small struct holding two pointers — trivially cheap to construct on every call. (Per §1.5 it's arena-allocated into `'t` but not interned; each call allocates a fresh `&'t`.)

### 3.6 Env-ID Uniqueness Not Required

Environments don't need unique `id` fields per instance. No HashMap keys on env's own id anywhere in the design:
- `CompilerOutputs.function_name_to_outer_env` etc. are keyed by **function/type template ids** (genuinely unique per Scala's `vassert`).
- OverloadSet holds the env by direct reference, not by id.
- Heavy templatas hold refs, not ids.

Scala's env-id collisions (NodeEnvironment delegating to parent, GeneralEnvironment reusing caller ids, Box wrappers sharing ids) translate as-is.

### 3.7 `GlobalEnvironmentT`

One instance per typing pass, allocated in `'t` early. Every env carries `global_env: &'t GlobalEnvironmentT<'s, 't>` as a back-ref:

```rust
#[derive(PartialEq, Eq, Hash, Debug)]
pub struct GlobalEnvironmentT<'s, 't> {
    pub name_to_top_level_environment:
        &'t [(&'t IdT<'s, 't>, TemplatasStoreT<'s, 't>)],
    pub builtins: TemplatasStoreT<'s, 't>,
}
```

Scala's macro fields (`nameToFunctionBodyMacro: Map[StrI, IFunctionBodyMacro]`, etc.) are **dropped** — macros are methods on `Compiler` now, dispatched via unit-variant enums (see Part 2 and `handoff-god-struct-progress.md`). The struct carries only data.

### 3.8 Why Not Two-Tier Per-Denizen Arenas (Yet)

The migration-phase design uses one `'t` typing arena. A post-Slab-8 redesign splits into a program-wide `'out` outputs arena (for resolved definitions, interned types, skeleton envs) and per-top-level-denizen `'scratch` arenas (for working envs, transient templatas, solver state), with a side-table + `EnvIdx(u32)` for arena-struct → scratchpad-env references. That design is driven by `bumpalo`'s no-destructor semantics + an empirical cross-denizen edge audit + wanting per-denizen memory bounds. Full write-up + audit findings: `FrontendRust/docs/reasoning/environments-per-denizen-long-term.md`. Not in scope for this migration.

---

## Part 4: CompilerOutputs

### 4.1 Structure

Mutable accumulator threaded through the pass. Carries `<'s, 't>`:

```rust
pub struct CompilerOutputs<'s, 't> {
    // Function registries
    pub return_types_by_signature: HashMap<PtrKey<'t, SignatureT<'s, 't>>, CoordT<'s, 't>>,
    pub signature_to_function: HashMap<PtrKey<'t, SignatureT<'s, 't>>, &'t FunctionDefinitionT<'s, 't>>,
    
    // Declaration tracking
    pub function_declared_names: HashMap<PtrKey<'t, IdT<'s, 't>>, RangeS<'s>>,
    pub type_declared_names: HashSet<PtrKey<'t, IdT<'s, 't>>>,
    
    // Env storage (keyed by function/type template id, per Scala parity)
    pub function_name_to_outer_env: HashMap<PtrKey<'t, IdT<'s, 't>>, &'s IEnvironmentT<'s, 't>>,
    pub function_name_to_inner_env: HashMap<PtrKey<'t, IdT<'s, 't>>, &'s IEnvironmentT<'s, 't>>,
    pub type_name_to_outer_env: HashMap<PtrKey<'t, IdT<'s, 't>>, &'s IEnvironmentT<'s, 't>>,
    pub type_name_to_inner_env: HashMap<PtrKey<'t, IdT<'s, 't>>, &'s IEnvironmentT<'s, 't>>,
    
    // Type metadata
    pub type_name_to_mutability: HashMap<PtrKey<'t, IdT<'s, 't>>, ITemplataT<'s, 't>>,
    pub interface_name_to_sealed: HashMap<PtrKey<'t, IdT<'s, 't>>, bool>,
    
    // Definitions
    pub struct_template_name_to_definition: HashMap<PtrKey<'t, IdT<'s, 't>>, &'t StructDefinitionT<'s, 't>>,
    pub interface_template_name_to_definition: HashMap<PtrKey<'t, IdT<'s, 't>>, &'t InterfaceDefinitionT<'s, 't>>,
    
    // Impls + reverse indexes
    pub all_impls: HashMap<PtrKey<'t, IdT<'s, 't>>, &'t ImplT<'s, 't>>,
    // Exceptions to §4.3 copy-out invariant. Access via mem::take / drain + reinsert.
    pub sub_citizen_template_to_impls: HashMap<PtrKey<'t, IdT<'s, 't>>, Vec<&'t ImplT<'s, 't>>>,
    pub super_interface_template_to_impls: HashMap<PtrKey<'t, IdT<'s, 't>>, Vec<&'t ImplT<'s, 't>>>,
    
    // Exports/externs
    pub kind_exports: Vec<&'t KindExportT<'s, 't>>,
    pub function_exports: Vec<&'t FunctionExportT<'s, 't>>,
    pub kind_externs: Vec<&'t KindExternT<'s, 't>>,
    pub function_externs: Vec<&'t FunctionExternT<'s, 't>>,
    
    // Instantiation bounds
    pub instantiation_name_to_bounds: HashMap<PtrKey<'t, IdT<'s, 't>>, &'t InstantiationBoundArgumentsT<'s, 't>>,
    
    // Deferred evaluation queues
    pub deferred_actions: VecDeque<DeferredActionT<'s, 't>>,
    pub finished_deferred_function_body_compiles: HashSet<PtrKey<'t, PrototypeT<'s, 't>>>,
    pub finished_deferred_function_compiles: HashSet<PtrKey<'t, IdT<'s, 't>>>,
}
```

No origin side tables — heavy templatas and OverloadSets hold refs directly.

### 4.2 `PtrKey<'t, T>` Newtype For HashMap Keys

Interned `&'t T` refs hash by pointer identity, not structural equality. Newtype wrapper gives the custom `Hash`/`Eq`.

```rust
pub struct PtrKey<'t, T: ?Sized>(pub &'t T);

impl<'t, T: ?Sized> PartialEq for PtrKey<'t, T> {
    fn eq(&self, other: &Self) -> bool { std::ptr::eq(self.0, other.0) }
}
impl<'t, T: ?Sized> Eq for PtrKey<'t, T> {}
impl<'t, T: ?Sized> Hash for PtrKey<'t, T> {
    fn hash<H: Hasher>(&self, state: &mut H) { (self.0 as *const T as *const ()).hash(state) }
}
impl<'t, T: ?Sized> Copy for PtrKey<'t, T> {}
impl<'t, T: ?Sized> Clone for PtrKey<'t, T> { fn clone(&self) -> Self { *self } }
```

### 4.3 Side-Table Access Pattern — Copy Out Before &mut

The remaining side tables (env maps like `function_name_to_outer_env`) still need the copy-out pattern:

```rust
// Does not compile:
let env = coutputs.function_name_to_outer_env.get(&PtrKey(id)).unwrap();
self.do_stuff(coutputs, env, ...);  // &mut coutputs rejected; env borrows from it

// Works — copy out first:
let env: &'s IEnvironmentT<'s, 't> = *coutputs.function_name_to_outer_env.get(&PtrKey(id)).unwrap();
self.do_stuff(coutputs, env, ...);  // OK; env is Copy'd out
```

**Invariant: most HashMap values in `CompilerOutputs` are pointer-sized `Copy` types** (`&'s T`, `&'t T`), enabling the copy-out-then-mutate pattern.

**Two exceptions:** `sub_citizen_template_to_impls` and `super_interface_template_to_impls` hold `Vec<&'t ImplT<'s, 't>>` because the reverse-index lookups return multiple impls per key. Access these with `mem::take` / `drain` + reinsert, not by-reference read. The `Vec` is cheaply movable (pointer-sized entries) so take-and-restore is fine.

### 4.4 Deferred Actions

Avoid `Box<dyn FnOnce>` via structured enum:

```rust
pub enum DeferredActionT<'s, 't> {
    EvaluateFunctionBody {
        prototype: &'t PrototypeT<'s, 't>,
        function_env: &'s FunctionEnvironmentT<'s, 't>,
        origin: &'s FunctionA<'s>,
    },
    EvaluateFunction {
        name: &'t IdT<'s, 't>,
        calling_env: &'s IEnvironmentT<'s, 't>,
        origin: &'s FunctionA<'s>,
        template_args: &'t [ITemplataT<'s, 't>],
    },
    // Add variants as needed.
}
```

Drain loop matches on the variant. No `Box`, no `dyn`, no hidden captures.

**Invariant:** `DeferredActionT` variants hold only owned or `Copy` context (`&'s` and `&'t` refs, small values). They never reference `CompilerOutputs` itself. This preserves the drain pattern (`pop_front()` → match → call method with `&mut coutputs`) without self-borrow hazards — once the action is popped out, it no longer aliases anything inside `coutputs`, so passing `&mut coutputs` into the handler is safe.

### 4.5 Speculative Writes Are Idempotent

During overload resolution, `attempt_candidate_banner` writes to `coutputs` even for candidates that may be rejected. Safe because writes are idempotent — re-writing asserts equality.

---

## Part 5: OverloadSet

### 5.1 Transient Kind, But Holds Env Directly

- Overload resolution always completes during the typing pass; nothing unresolved reaches the instantiator.
- But an `OverloadSet`-kinded `ReinterpretTE` survives in output as an inert type tag (the instantiator elides it via `InstantiationBoundArgumentsT.runeToFunctionBoundArg`).

Since `'s` lives through instantiation, we hold the env directly — no `ctx_id` indirection needed:

```rust
/// Reachable from HinputsT only inside inert ReinterpretTE args that
/// the instantiator drops on sight. Never dereference env/name during
/// instantiation — they are type-tag placeholders only.
pub struct OverloadSetT<'s, 't> {
    pub env: &'s IEnvironmentT<'s, 't>,
    pub name: &'s IImpreciseNameS<'s>,  // scout-interned, fine to hold
}

pub enum KindT<'s, 't> {
    // ... other variants ...
    OverloadSet(&'t OverloadSetT<'s, 't>),
}
```

### 5.2 Construction

Matches Scala directly:

```rust
impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't> {
    pub fn make_overload_set_kind(
        &self,
        env: &'s IEnvironmentT<'s, 't>,
        name: &'s IImpreciseNameS<'s>,
    ) -> &'t KindT<'s, 't> {
        let overload_set = OverloadSetT { env, name };
        self.typing_interner.intern_kind(KindValT::OverloadSet(overload_set))
    }
}
```

No side-table insertion, no `ctx_id`, no `&mut coutputs` required for the construction itself.

### 5.3 Overload Resolution Uses The Env Directly

The resolver reads `overload_set.env` directly, walks its parent chain, looks up candidates, recurses into nested OverloadSets via their own `env` fields. Matches Scala's `OverloadResolver.scala:210` pattern verbatim. No signature changes to `getCandidateBannersInner` etc. — `&CompilerOutputs` is still threaded through the god-struct methods, but not specifically for env lookup.

### 5.4 Post-Pass

`OverloadSetT<'s, 't>` survives in `HinputsT<'s, 't>` inside inert `ReinterpretTE` args. Its `env` and `name` refs remain valid for as long as `'s` lives. The instantiator elides these on sight via `InstantiationBoundArgumentsT.runeToFunctionBoundArg` and never dereferences them — they're type-tag placeholders.

After the instantiator completes, the caller drops the typing interner (`'t` dies), then the scout arena (`'s` dies), in that order (§1.3). The OverloadSet's refs are never dangling during any live use; after both arenas drop, the entire `HinputsT` is unreachable.

---

## Part 6: Type System Types

Every lifetime-parameterized struct in this section has an implicit `where 's: 't` bound (§1.2). Representative examples are spelled out on `IdT`, `CoordT`, `KindT`, and `ITemplataT` below; others follow the same convention.

### 6.1 IDEPFL Dual-Enum Pattern

All interned typing types use the dual-enum pattern: a **reference enum** (canonical, `&'t` refs) and a **value enum** (transient, HashMap lookup). Scout-lifetimed values (`StrI<'s>`, `IImpreciseNameS<'s>`, `RangeS<'s>`, etc.) are used directly wherever they appear — no re-interning boundary.

Interned type families (each gets its own HashMap dedup + optional `*ValT` lookup key per IDEPFL):
- Concrete name structs (`FunctionNameT`, `StructNameT`, … ~60). 15 have `&'t [...]` slices and get a transient `*ValT<'s, 't, 'tmp>`; the rest reuse the struct as its own Val (Slab 2 Step 6 convention).
- `IdT<'s, 't>` / `IdValT<'s, 't, 'tmp>` — monomorphic (see §6.3).
- Concrete Kind payloads (`StructTT`, `InterfaceTT`, `StaticSizedArrayTT`, `RuntimeSizedArrayTT`, `KindPlaceholderT`, `OverloadSetT`) — all simple/shallow, reuse struct as Val.
- Interned templata payloads (`CoordTemplataT`, `KindTemplataT`, `PlaceholderTemplataT`, `PrototypeTemplataT`, `IsaTemplataT`) — simple/shallow, reuse struct as Val. `CoordListTemplataT` has an arena slice so it gets `CoordListTemplataValT<'s, 't, 'tmp>`.
- `PrototypeT<'s, 't>` / `PrototypeValT<'s, 't, 'tmp>`, `SignatureT<'s, 't>` / `SignatureValT<'s, 't, 'tmp>` — monomorphic; both contain `IdT` so Val needs `'tmp` for the nested `IdValT`'s slice.

**Wrapper enums are NOT interned.** `INameT` + 21 name sub-enums, `KindT` + 3 Kind sub-enums (`ICitizenTT`/`ISubKindTT`/`ISuperKindTT`), and `ITemplataT` are all 16-byte inline Copy values. Sub-enum casts between narrow and wide forms are stack-only rewraps via `From`/`TryFrom`. See §6.2 for the names rationale; §6.5 for Kind; `docs/reasoning/idt-typed-view-alternatives.md` for the overarching design.

**No `'t`-lifetime re-interned versions of `StrI`, `IImpreciseNameS`, `RangeS`, `CodeLocationS`, `PackageCoordinate`, `FileCoordinate`.** Use the scout-arena versions directly. Anywhere you'd be tempted to create a `StrI<'t>` or `RangeT<'t>`, use the existing `StrI<'s>` or `RangeS<'s>` instead.

### 6.2 INameT Hierarchy

~60 concrete name types, ~21 sub-trait enums. Every name type carries `<'s, 't>`.

**DAG rule (critical).** Scala's sealed-trait hierarchy is a DAG — some concrete names extend multiple sub-traits (`ExternFunctionNameT extends IFunctionNameT with IFunctionTemplateNameT`, etc.). In Rust this becomes: **a shared concrete name gets a variant in each sub-enum it belongs to.** Example:

```rust
pub enum IFunctionNameT<'s, 't> {
    Function(&'t FunctionNameT<'s, 't>),
    ForwarderFunction(&'t ForwarderFunctionNameT<'s, 't>),
    ExternFunction(&'t ExternFunctionNameT<'s, 't>),  // also appears below
    // ...
}

pub enum IFunctionTemplateNameT<'s, 't> {
    FunctionTemplate(&'t FunctionTemplateNameT<'s, 't>),
    ExternFunction(&'t ExternFunctionNameT<'s, 't>),  // shared with IFunctionNameT
    ForwarderFunctionTemplate(&'t ForwarderFunctionTemplateNameT<'s, 't>),
    // ...
}
```

**Sub-enums are inline-owned Copy values, NOT arena-interned.** Only the concrete name types (wrapped in the variants above) and `INameT` itself (the widest union of all ~60 concretes) live in the typing arena. The intermediate sub-enums (`IFunctionNameT`, `IFunctionTemplateNameT`, `ICitizenNameT`, etc. — 21 families) are 16-byte `#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]` values built on the stack when needed. Casting a concrete into a sub-enum, or a narrow sub-enum into a wider one, is a pure stack-only rewrap via `.into()` — no interner round-trip.

The consequence for identity: two concrete names (e.g. two `&'t FunctionNameT`) are compared via `ptr::eq` since concretes stay interned. Two owned sub-enum values (e.g. two `IFunctionNameT<'s, 't>`, or two `INameT<'s, 't>`) are compared structurally — but since the tag + inner pointer is 16 bytes, this is a 2-word compare and still cheap.

Bridging via `From<X> for SubEnum` (narrow → wide, infallible — concrete into sub-enum and narrow sub-enum into wider sub-enum) and `TryFrom<INameT<'s, 't>> for SubEnum` (wide → narrow, fallible, match-and-rewrap on the stack). All of these are real implementations in `names.rs`, no interner involvement.

**Self-referential variants** (e.g. `ForwarderFunctionNameT.inner: IFunctionNameT<'s, 't>`, `ForwarderFunctionTemplateNameT.inner: IFunctionTemplateNameT<'s, 't>`) are inline-owned; since sub-enums are 16 bytes and Copy they can live directly as struct fields without `Box` or `&'t`.

No new name variants needed for env handling (env-id uniqueness isn't required).

### 6.3 `IdT<'s, 't>` — Monomorphic, Interned

```rust
pub struct IdT<'s, 't>
where 's: 't,
{
    pub package_coord: &'s PackageCoordinate<'s>,  // scout-lifetimed
    pub init_steps: &'t [INameT<'s, 't>],  // inline INameT values
    pub local_name: INameT<'s, 't>,  // always the widest form
}
```

Scala's `IdT[+T <: INameT]` phantom outer parameter is **erased** in Rust — the Rust port is monomorphic. Callers that need a specific leaf-name variant pattern-match on `local_name` at the point of use, like Scala does after `match id.localName`. See `FrontendRust/docs/reasoning/idt-typed-view-alternatives.md` for the full rationale and the alternatives (generic-with-layout-cast, RawIdT+TypedIdT wrapper, etc.) that were considered and deferred for post-migration revisit.

`IdT` defines custom `PartialEq`/`Eq`/`Hash` (not derive):
- `package_coord`: pointer-eq (scout arena canonicalizes `PackageCoordinate`).
- `init_steps`: slice data pointer + length compare — the typing interner canonicalizes `&'t [INameT]` slices as a whole per IDEPFL, so equal content ⇒ equal slice pointer.
- `local_name`: structural compare on the inline `INameT<'s, 't>` (16 bytes: tag + 8-byte concrete ref).

Interning uses one HashMap per IDEPFL keyed by `IdValT<'s, 't, 'tmp>` (transient, `'tmp`-borrowed init_steps slice). No widen/try_narrow methods — pattern-match instead.

### 6.4 `CoordT<'s, 't>` — Inline Copy

```rust
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct CoordT<'s, 't> {
    pub ownership: OwnershipT,
    pub region: RegionT,
    pub kind: KindT<'s, 't>,  // KindT is inline (16 bytes), so CoordT is ~24 bytes
}
```

Small, Copy, passed by value. Not interned — structural eq.

### 6.5 `KindT<'s, 't>` — Inline Wrapper

```rust
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum KindT<'s, 't> {
    // Primitives inline (small, Slab 1 shape)
    Never(NeverT),                          // NeverT { from_break: bool }
    Void(VoidT),                            // unit
    Int(IntT),                              // IntT { bits: i32 }
    Bool(BoolT), Str(StrT), Float(FloatT),  // unit
    // Non-primitives — &'t refs to interned payloads
    Struct(&'t StructTT<'s, 't>),
    Interface(&'t InterfaceTT<'s, 't>),
    StaticSizedArray(&'t StaticSizedArrayTT<'s, 't>),
    RuntimeSizedArray(&'t RuntimeSizedArrayTT<'s, 't>),
    KindPlaceholder(&'t KindPlaceholderT<'s, 't>),
    OverloadSet(&'t OverloadSetT<'s, 't>),
}
```

`KindT` is **inline-owned** (not arena-interned): 16 bytes tag + ref, Copy. Concrete payloads (`StructTT` etc.) are arena-interned; the wrapper just tags them.

Same philosophy for the three Kind sub-enum families:
```rust
pub enum ICitizenTT<'s, 't>   { Struct(&'t StructTT<'s, 't>), Interface(&'t InterfaceTT<'s, 't>) }
pub enum ISubKindTT<'s, 't>   { Struct(...), Interface(...), StaticSizedArray(...), RuntimeSizedArray(...), KindPlaceholder(...) }
pub enum ISuperKindTT<'s, 't> { Interface(...), KindPlaceholder(...) }
```

DAG rule matches §6.2: a concrete payload gets a variant in each sub-enum it extends in Scala. Casts between `KindT` / `ICitizenTT` / `ISubKindTT` / `ISuperKindTT` are stack-only rewraps via `From`/`TryFrom`.

See `docs/reasoning/idt-typed-view-alternatives.md` — the split "wrapper enums inline, concrete payloads interned" is uniform across names (§6.2), kinds (this section), and templatas (§6.6).

### 6.6 `ITemplataT<'s, 't>` — Inline Wrapper, Phantom Parameters Erased

Scala's `ITemplataT[+T <: ITemplataType]` outer phantom type parameter is **erased** in Rust — the wrapper is a monomorphic `ITemplataT<'s, 't>` enum, and callers that need a specific kind pattern-match on the variant.

```rust
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ITemplataT<'s, 't> {
    // Interned-payload variants — &'t ref to arena-allocated payload
    Coord(&'t CoordTemplataT<'s, 't>),
    Kind(&'t KindTemplataT<'s, 't>),
    Placeholder(&'t PlaceholderTemplataT<'s, 't>),
    Prototype(&'t PrototypeTemplataT<'s, 't>),
    Isa(&'t IsaTemplataT<'s, 't>),
    CoordList(&'t CoordListTemplataT<'s, 't>),
    // Inline Copy-value variants
    Mutability(MutabilityTemplataT),
    Variability(VariabilityTemplataT),
    Ownership(OwnershipTemplataT),
    Integer(i64),
    Boolean(bool),
    String(StrI<'s>),  // scout-lifetimed, not re-interned
    RuntimeSizedArrayTemplate(RuntimeSizedArrayTemplateTemplataT<'s, 't>),
    StaticSizedArrayTemplate(StaticSizedArrayTemplateTemplataT<'s, 't>),
    // Heavy templatas — &'t to scout-ref-holding payloads, not interned
    Function(&'t FunctionTemplataT<'s, 't>),
    StructDefinition(&'t StructDefinitionTemplataT<'s, 't>),
    InterfaceDefinition(&'t InterfaceDefinitionTemplataT<'s, 't>),
    ImplDefinition(&'t ImplDefinitionTemplataT<'s, 't>),
    ExternFunction(&'t ExternFunctionTemplataT<'s, 't>),
}
```

`ITemplataT` itself is **inline-owned**, not arena-interned. Same split as `KindT`: wrapper inline, concrete payloads interned (for the leaf-like group) or arena-allocated-but-not-interned (for the heavy group).

Heavy templata payloads (`FunctionTemplataT`, `StructDefinitionTemplataT`, `InterfaceDefinitionTemplataT`, `ImplDefinitionTemplataT`, `ExternFunctionTemplataT`) hold scout-lifetime refs directly:

```rust
pub struct FunctionTemplataT<'s, 't> {
    pub outer_env: &'s IEnvironmentT<'s, 't>,
    pub function: &'s FunctionA<'s>,
}
// …StructDefinitionTemplataT / InterfaceDefinitionTemplataT / ImplDefinitionTemplataT
// each hold `&'s IEnvironmentT` and one of `&'s StructA` / `&'s InterfaceA` / `&'s ImplA`.
pub struct ExternFunctionTemplataT<'s, 't> {
    pub header: &'t FunctionHeaderT<'s, 't>,
}
```

Since `FunctionA`, `StructA`, etc. don't derive `Eq`/`Hash`, heavy-templata Eq/Hash is via `std::ptr::eq` on the scout refs (the scout arena canonicalizes those). Slab 3 wrote manual `PartialEq`/`Eq`/`Hash` impls for the heavy templata payloads.

`PrototypeTemplataT`'s Scala inner type parameter (`PrototypeTemplataT[T <: IFunctionNameT]`) is also erased — `PrototypeT<'s, 't>` is monomorphic post Slab 2 refactor (see §6.3), so `PrototypeTemplataT<'s, 't>` just holds `&'t PrototypeT<'s, 't>` with no inner phantom.

`ITemplataValT` doesn't exist — since `ITemplataT` itself isn't interned, no Val companion is needed. The six interned-payload variants have their own `*ValT` lookups (all simple/shallow — reuse the payload struct as its own Val; `CoordListTemplataT` with its arena slice gets a transient `CoordListTemplataValT<'s, 't, 'tmp>`).

### 6.7 Mutual Recursion

Types form a mutually recursive graph (`CoordT → KindT → StructTT → IdT → INameT → ITemplataT → CoordT`). Most links are `&'s` or `&'t` refs — pointer-sized, finite. The name sub-enums (`IFunctionNameT`, `IStructNameT`, etc.) are the exception — they're inline 16-byte tagged pointers, one word larger than a raw ref but still finite and `Copy`. No `Box`/`Vec` needed; arena references + inline sub-enums provide the indirection.

---

## Part 7: Expression AST

### 7.1 Three Enums

```rust
pub enum ReferenceExpressionTE<'s, 't> {
    LetNormal(LetNormalTE<'s, 't>),
    If(IfTE<'s, 't>),
    While(WhileTE<'s, 't>),
    FunctionCall(FunctionCallTE<'s, 't>),
    Consecutor(ConsecutorTE<'s, 't>),
    Construct(ConstructTE<'s, 't>),
    Reinterpret(ReinterpretTE<'s, 't>),
    // ... ~30 more variants
}

pub enum AddressExpressionTE<'s, 't> {
    LocalLookup(LocalLookupTE<'s, 't>),
    ReferenceMemberLookup(ReferenceMemberLookupTE<'s, 't>),
    // ... ~4 more
}

pub enum ExpressionTE<'s, 't> {
    Reference(&'t ReferenceExpressionTE<'s, 't>),
    Address(&'t AddressExpressionTE<'s, 't>),
}
```

Used wherever a slot can hold either (e.g. `ConstructTE.args`). All other slots are specifically `&'t ReferenceExpressionTE` or `&'t AddressExpressionTE`.

**Narrow-use rule.** `ConstructTE` is the **only** expression node that holds the broad `ExpressionTE` wrapper (because closure structs can have addressible members mixed with reference-valued ones). Every other slot in every other node uses the specific `&'t ReferenceExpressionTE` or `&'t AddressExpressionTE` — no `ExpressionTE` wrapping. When filling expression definitions, default to the narrow type; only reach for `ExpressionTE` when the Scala field was typed as the broader `ExpressionT` AND the node mixes both in a collection.

### 7.2 Arena-Allocated, Not Interned

Expression nodes are allocated into `'t` but not deduped. Sub-expressions are `&'t` refs; collections are arena slices.

### 7.3 Can Reference Scout Data

TE nodes can freely hold `&'s` refs — e.g., if an expression's type metadata includes a `RangeS<'s>` source location, that's fine.

### 7.4 Visitor/Collector Pattern

Same as parsing/postparsing: `NodeRefT<'s, 't>` enum, `visit_*` functions, entry points, `collect_where_tnodes!`/`collect_only_tnodes!` macros.

---

## Part 8: Error Handling

### 8.1 `Result<T, CompileErrorT>` With `?`

Scala's `throw CompileErrorExceptionT(...)` → `return Err(CompileErrorT::...)` with `?` propagation. Single catch boundary at top-level `Compiler::compile()`.

### 8.2 Panic For Unimplemented

`panic!()` with unique identifying messages for unmigrated branches. Fully-stub functions acceptable during incremental migration.

---

## Part 9: Keywords

`Keywords<'s>` — re-uses scout arena for interned strings. Same Keywords instance can serve the typing pass and (if needed) subsequent passes as long as `'s` lives. No typing-specific `Keywords<'t>` needed.

---

## Part 10: Driving The Pass

### 10.1 Top-Level Entry

```rust
pub fn run_typing_pass<'s, 'ctx, 't>(
    scout_arena: &'ctx ScoutArena<'s>,
    typing_interner: &'ctx TypingInterner<'t>,
    keywords: &'ctx Keywords<'s>,
    opts: &'ctx TypingPassOptions<'s>,
    program_a: &'s ProgramA<'s>,
) -> Result<HinputsT<'s, 't>, CompileErrorT<'s, 't>>
where 's: 't,
{
    let compiler = Compiler::new(scout_arena, typing_interner, keywords, opts);
    let mut coutputs = CompilerOutputs::new();
    
    compiler.compile_program(&mut coutputs, program_a)?;
    compiler.drain_all_deferred(&mut coutputs);
    
    let hinputs = HinputsT {
        function_definitions: typing_interner.alloc_slice_iter(
            coutputs.signature_to_function.into_values()
        ),
        struct_definitions: typing_interner.alloc_slice_iter(
            coutputs.struct_template_name_to_definition.into_values()
        ),
        // ... etc
    };
    
    Ok(hinputs)
    // coutputs drops; its HashMaps (storing 's/'t refs) die cheaply
    // scout_arena and typing_interner survive; instantiator can use them
}
```

No `finalize()`. Natural scope exit.

### 10.2 Instantiator Handoff

The instantiator receives `HinputsT<'s, 't>` plus both arena references. When it's done, the caller lets local bindings drop in scope order (see §1.3): typing interner first, scout arena second, parse arena last.

---

## Part 11: Invariants Summary

1. **`'s` outlives `'t`.** Declared via `where 's: 't` on every type that transitively holds `&'s` data. Rust does not enforce outlives via drop order; the bound must be written.
2. **Arena types never contain `Vec`, `HashMap`, `String`, `Rc`, `Box`.** AASSNCMCX applies. Use arena slices. (One pre-existing exception — `LocationInFunctionEnvironmentT.path: Vec<i32>` — is known debt; see `FrontendRust/docs/migration/handoff-slab-4.md` Gotcha 11.)
3. **All HashMap keys on interned refs use `PtrKey<'t, T>`** for pointer-based hash/eq.
4. **Most `CompilerOutputs` HashMap values are pointer-sized `Copy` refs** (`&'s T`, `&'t T`) — enables the copy-out-then-mutate pattern. Exceptions: `sub_citizen_template_to_impls` and `super_interface_template_to_impls` hold `Vec<&'t ImplT>`; access via `mem::take` / `drain` + reinsert.
5. **Speculative writes are idempotent.** No rollback machinery.
6. **Overload resolution always completes during the typing pass.** No unresolved overloads reach the instantiator.
7. **Env equality/hashing never used.** Envs keyed in CompilerOutputs by external (function/type template) ids, not their own id.
8. **Envs live in the typing arena `'t`.** They transitively hold `&'t` refs (via `ITemplataT` in `IEnvEntryT`), so they can't live in `'s`. They drop when `'t` drops (end of typing pass; the instantiator has already consumed what it needs by then — envs don't need to survive past `'t`).
9. **`DeferredActionT` variants never reference `CompilerOutputs`.** All captured context is owned or `Copy` (`&'s`/`&'t` refs, small values). Preserves the `pop_front → match → handler(&mut coutputs)` drain pattern without self-borrow hazards.
10. **Arena parameters use a short borrow lifetime.** `&ScoutArena<'s>` or `&'ctx ScoutArena<'s>`, never `&'s ScoutArena<'s>`. Matches existing-pass convention (§1.2).

---

## Part 12: Slab-Ordered Migration Plan

### 12.0 Ground Rule: Preserve The `/* scala */` Audit Trail

The typing/ skeleton has a `/* ... */` block with the Scala source directly below every Rust definition. The `.claude/hooks/check-scala-comments` pre-commit hook does exact-match comparison and rejects any edit inside those blocks. Rules:

- Replace the empty Rust stub in-place with the real definition. Keep the Scala `/* ... */` block below unchanged.
- Never move a Rust definition away from its Scala block.
- A Rust definition grown to need helper structs (e.g. an `IdValT` companion for an `IdT`) gets its companion block **adjacent to** the main definition, with a `// (no scala counterpart — …)` note.

(The original `// mig:` marker lines above each stub were removed in Slab 0 Step 0 — they were slice-pipeline artifacts. The `/* scala */` blocks are the audit trail now.)

### 12.1 Slabs

1. ✅ **Slab 0** (arena substrate): `TypingInterner<'t>`, `PtrKey<'t, T>`, lifetime conventions docs. Non-migration Rust scaffolding.
2. ✅ **Slab 1** (leaf types): real Copy enums for `OwnershipT` / `MutabilityT` / `VariabilityT` / `LocationT`; primitive `KindT` payloads; leaf-value templatas. Commit `9fd7641c`.
3. ✅ **Slab 2** (name hierarchy): monomorphic `IdT<'s, 't>` (§6.3); ~60 concrete name structs + 22 inline-owned sub-enums per the §6.2 DAG; `From`/`TryFrom` bridges; IDEPFL `*ValT` companions. Tagged `slab-2-complete`. Handoff at `FrontendRust/docs/migration/handoff-slab-2.md`.
4. ✅ **Slab 3** (Kind/Coord/Templata trio): `KindT` inline wrapper + interned concrete payloads per §6.5; `ITemplataT` inline wrapper with interned + heavy-allocated + inline-value variants per §6.6; `CoordListTemplataValT` for the one slice-bearing templata payload; `PrototypeValT` / `SignatureValT` with `'tmp`. Tagged `slab-3-complete`. Handoff at `FrontendRust/docs/migration/handoff-slab-3.md`.
5. ✅ **Slab 4** (envs + real interner bodies, `env/*.rs` + `typing_interner.rs`): 9 env structs + 2 wrapper enums (`IEnvironmentT` 9 variants + sibling `IInDenizenEnvironmentT` 6 variants) + `GlobalEnvironmentT` + `TemplatasStoreT` + `IEnvEntryT` + `IVariableT`/`ILocalVariableT` + 4 concrete variables, all Scala-parity. 9 env-specific builders + `TemplatasStoreBuilder` with `build_in(interner) -> &'t FooEnvironmentT<'s, 't>`. `TypingInterner<'s, 't>` gained real bodies with 6 family-level `hashbrown::HashMap`s keyed on tagged-union Val enums (`INameValT` 72 variants / `InternedKindPayloadValT` 6 / `InternedTemplataPayloadValT` 6 + canonical `Interned*PayloadT` wrappers), 6 family-level `intern_<family>` methods, plus ~84 per-concrete thin-wrapper methods via four `impl_intern_*_wrapper_*` macros. Val `Hash`/`Eq` uses content-based derive for heterogeneous `'tmp`→`'t` lookup consistency. Envs override §3.1 to live in `'t`, not `'s`. Five heavy-templata env refs flipped `&'s` → `&'t`. `NodeEnvironmentBox` / `FunctionEnvironmentBoxT` / `IDenizenEnvironmentBoxT` deleted — builder-freeze subsumes them. Tagged `slab-4-complete`. Handoff at `FrontendRust/docs/migration/handoff-slab-4.md`.
6. ✅ **Slab 5** (expression AST, `ast/expressions.rs`): 3 enums (`ReferenceExpressionTE` 48 variants, `AddressExpressionTE` 5 variants, wrapper `ExpressionTE` 2-variant) + 53 payload structs + `IExpressionResultT` / `ReferenceResultT` / `AddressResultT`. `#[derive(PartialEq, Debug)]` family-wide (forced by `f64` in `ConstantFloatTE`). Tagged `slab-5-complete`. Handoff at `FrontendRust/docs/migration/handoff-slab-5.md`.
7. ✅ **Slab 6** (`CompilerOutputs<'s, 't>` data shape, `compiler_outputs.rs`): 23-field struct per §4.1 with `PtrKey<'t, T>`-keyed HashMaps, `DeferredActionT<'s, 't>` 2-variant enum (replacing the two `DeferredEvaluatingFunction*` stubs), `::new()` constructor, stack-owned so AASSNCMCX doesn't apply to its internal `HashMap`/`Vec`/`VecDeque`/`HashSet`. `PtrKey<'t, T: ?Sized>` newtype in its own module `ptr_key.rs` with manual `Copy`/`Clone`/`PartialEq`/`Eq`/`Hash`/`Debug` impls per §4.2. Method signatures deferred to Slab 8. Tagged `slab-6-complete`. Handoff at `handoff-slab-6.md`.
8. ✅ **Slab 7** (`HinputsT` residual cleanup + Compiler god struct shell + `run_typing_pass` entry point, `hinputs_t.rs` + `compiler.rs` + `compilation.rs`): two `()` → `&'t PrototypeT<'s, 't>` flips in `InstantiationReachableBoundArgumentsT.citizen_rune_to_reachable_prototype` and `InstantiationBoundArgumentsT.rune_to_bound_prototype` (the upstream PrototypeT became monomorphic in Slab 3, unblocking these); `_phantom` field deleted; `Compiler::compile_program(&self, &mut coutputs, &'s ProgramA) -> Result<(), ICompileErrorT>` and `Compiler::drain_all_deferred(&self, &mut coutputs)` panic-stub methods added; `pub fn run_typing_pass<'s, 'ctx, 't>(scout_arena, typing_interner, keywords, opts, program_a) -> Result<HinputsT, ICompileErrorT>` free fn in `compilation.rs` as the pass entry point. `HinputsT` Vec/HashMap fields remain as documented AASSNCMCX deviation. `Compiler::evaluate` panic-stub left untouched. Tagged `slab-7-complete`. Handoff at `handoff-slab-7.md`.
9. ✅ **Slab 8** (`CompilerOutputs` method signatures, `compiler_outputs.rs`): all 54 private free-fn stubs lifted into one-fn `impl<'s, 't> CompilerOutputs<'s, 't> where 's: 't { pub fn ... }` blocks. Mutating methods (`add_*`, `declare_*`, `defer_*`, `mark_*`) take `&mut self`; read-only methods (`lookup_*`, `get_*`, `peek_*`) take `&self`. Definitions returned by `&'t`, `IdT` passed by value, env params flipped to `&'t IInDenizenEnvironmentT<'s, 't>` per Slab-4 override, `get_instantiation_name_to_function_bound_to_rune` return preserves the `PtrKey<'t, IdT>` key wrapping from the internal field. `add_instantiation_bounds` takes `&'t TypingInterner<'s, 't>`. Bodies panic-stubbed with `"Unimplemented: Slab 10 — body migration"` (message slab-number to be re-aligned when bodies land). Tagged `slab-8-complete`. Handoff at `handoff-slab-8.md`.
10. ✅ **Slab 9** (`object TemplataCompiler` method signatures, `templata_compiler.rs`): all 35 private free-fn stubs at the file head lifted into one-fn `impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't> where 's: 't { pub fn ... (&self, ...) }` blocks, matching the 14 already-lifted `class TemplataCompiler` impls at the file tail. `interner` and `keywords` parameters dropped across every signature (accessed via `self.typing_interner` / `self.keywords`). `coutputs` conservatively `&mut CompilerOutputs<'s, 't>`. `bound_arguments_source: &'t dyn IBoundArgumentsSource<'s, 't>` on all 11 substitute_* methods (marker-trait dysfunction deferred to Slab 14+). `get_placeholder_substituter` / `get_placeholder_substituter_ext` / `create_rune_type_solver_env` return `()` with TODO comments (trait type not yet defined or Box/impl-return decision deferred). `get_first_unsolved_identifying_rune` takes closure as `impl Fn(IRuneS<'s>) -> bool`. Tagged `slab-9-complete`. Handoff at `handoff-slab-9.md`.
11. ⏳ **Slab 10** (compiler.rs residuals + orphan free-fn stubs + templata_compiler.rs tail, ~27 sigs): flip `()` placeholders in 6-8 `compiler.rs` methods (`preprocess_struct`, `preprocess_interface`, `determine_macros_to_call`, `ensure_deep_exports`, `is_root_function`, `is_root_struct`, `is_root_interface`, `evaluate`) and 4 orphan static fns (`print`, `consecutive`, `is_primitive`, `get_mutabilities`, `get_mutability`); fill signatures for `local_helper.rs` 2 orphans + `struct_compiler.rs` 1 orphan; fill 14 bare-`(&self)` tail impls in `templata_compiler.rs` (the original `class TemplataCompiler` instance methods). This slab concentrates cleanup of top-level scaffolding files.
12. ⏳ **Slab 11** (expression-layer signatures, ~39 sigs): `expression_compiler.rs` 21 methods + `pattern_compiler.rs` 12 + `block_compiler.rs` 2 + `call_compiler.rs` 4. All currently bare `(&self)` with Scala `/* */` anchors showing real param lists. Single-concern slab focused on expression AST evaluation.
13. ⏳ **Slab 12** (solver + resolver signatures, ~35 sigs): `infer_compiler.rs` 14 + `overload_resolver.rs` 11 + `impl_compiler.rs` 9 + `reachability.rs` 1. `IInfererDelegate` vestigial trait is deleted in this slab as part of the `compiler_solver.rs` signature cleanup.
14. ⏳ **Slab 13** (function/citizen/macros signatures, ~23 sigs): `function_compiler.rs` 8 + `function_body_compiler.rs` 3 + `destructor_compiler.rs` 2 + `struct_compiler_core.rs` 6 + `anonymous_interface_macro.rs` 4. The remaining 17 macro files are already clean per the audit.
15. ⏳ **Slab 14+** (method body migration, driven by failing tests): begin with trivial `CompilerOutputs` one-liners (e.g. `lookup_function` = `self.signature_to_function.get(&PtrKey(signature)).copied()`), then TemplataCompiler id-transforms, then substitution engine, then sub-compiler bodies. `ICompileErrorT` variant filling, the `IBoundArgumentsSource` marker-trait → enum conversion, and the `IPlaceholderSubstituter` trait definition all land inside this phase as body patterns demand them.

**Per-slab completion criterion:** files in-scope compile in isolation against previously-completed slabs (`panic!` bodies for downstream). The whole crate builds cleanly from Slab 6 onward because every Rust stub now has a valid-if-incomplete signature; adding real param types in Slabs 10-13 doesn't break compilation because no caller currently reaches the panic-stubbed bodies.

After Slab 13, the build is clean with `panic!()` bodies awaiting implementation. Slab 14+ fills them.

### 12.2 Immediate Next Action

Slab 10 (compiler.rs residuals + local_helper/struct_compiler orphans + templata_compiler.rs 14 tail-method sigs, ~27 sigs total). See `TL-HANDOFF.md` at repo root for the transition summary. Handoff doc is **not yet drafted** — incoming TL's first task is to write `FrontendRust/docs/migration/handoff-slab-10.md` in the Slab 8/9 style (translation table, receiver classification, Gotchas). Design spec is Part 2 (god struct) + Slab 9's handoff for the lift-to-Compiler pattern. No design overrides known; the remaining slabs 11-13 follow the same pattern on progressively larger file sets.

---

## Part 13: Open Questions / Future Work

- **Long-running processes (LSP):** scout arena retention through instantiation is prohibitive for an always-on process. The full trajectory is: first redesign typing storage into two-tier per-denizen arenas (see below), then build LSP on top with DefId-indexed cross-denizen refs + shattered `GlobalEnvironment` + local/global split interner + single-writer cleanup promotion. Sketched in `FrontendRust/docs/reasoning/environments-per-denizen-long-term.md` "Further future direction: LSP support". Concrete design deferred past the per-denizen refactor.
- **Incremental compilation:** serializing `HinputsT` to disk requires a serialization boundary that breaks `'s` refs. Batch compilation only for now. Partially addressed by the LSP trajectory above.
- **Parallelization:** single-threaded design (`!Sync` arenas, stack `CompilerOutputs`). Per-function parallelization is a later topic — the per-denizen design allows it naturally (serialize cleanups, parallelize compiles), but isn't a design constraint today.
- **Arena-backed HashMap (`ArenaIndexMap`):** `CompilerOutputs`'s heap HashMaps could move to arena-backed maps at scale. See `docs/reasoning/arena-deterministic-maps.md`.
- **Typed `IdT` narrowing (post-migration):** the migration-phase `IdT<'s, 't>` is monomorphic — callers pattern-match `local_name` at use sites to narrow. Four post-migration alternatives re-introduce compile-time narrowing (generic-with-layout-cast, unsafe-transmute typed views, `RawIdT` + typed-wrapper pair, status-quo erasure). See `FrontendRust/docs/reasoning/idt-typed-view-alternatives.md`.
- **Reverse-destructor arena.** Candidate crate: `bump-scope` (runs Drop via `BumpBox<T>`). Not required by the current design — it sticks to AASSNCMCX. Revisit if the long-term redesign wants `Rc`-in-arena or heap-collection-in-env.
- **Typing storage → two-tier per-denizen arenas.** Scheduled as a post-signature-rewrite redesign (after Slab 13 lands and the build is clean with panic bodies): program-wide `'out` outputs arena (resolved definitions, interned types, skeleton envs `Package`/`Citizen`/`BuildingWithClosureds`, `HinputsT`) + per-top-level-denizen `'scratch` scratchpad arenas (working envs `Node`/`Function`/`General`/`Export`/`Extern`/`BuildingWithClosuredsAndTemplateArgs`, transient templatas, solver state) dropped at each Phase-3 worklist-item boundary. Side-table + `EnvIdx(u32)` for arena-struct → scratchpad-env references. Bounds peak memory per-denizen, enables `HashMap`-in-env for O(1) method lookup, avoids `Rc`-in-arena leak hazard, forms the natural invalidation unit for eventual LSP. Predicated on an empirical cross-denizen edge audit (findings in the reasoning doc). Three prerequisite refactors identified: `FunctionHeaderT.maybeOriginFunctionTemplata` → `maybeOriginFunctionA`; delete dead `envByFunctionSignature`; keep `BuildingFunctionEnvironmentWithClosuredsT` in `'out`. See `FrontendRust/docs/reasoning/environments-per-denizen-long-term.md`.
