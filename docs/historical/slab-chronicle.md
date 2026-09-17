# Typing Pass Migration — Slab Chronicle (Historical)

**This is a historical record.** The slab-based scaffolding phase (Slabs 0–14b) is complete. All type definitions, method signatures, and placeholder types are done. The active work is now test-driven body migration (Slab 15+). See `TL.md` for the current design spec and operating instructions.

---

## Slab Summary Table

| Slab | Scope | Status | Tag / handoff |
|---|---|---|---|
| 0 | arena substrate | done | (scaffolding; no tag) |
| 1 | leaf types (OwnershipT, primitive KindT payloads, leaf templatas) | done | commit `9fd7641c` |
| 2 | name hierarchy (~60 concrete names, 22 sub-enums, IdT, ValT companions) | done | `slab-2-complete` · `FrontendRust/docs/migration/handoff-slab-2.md` |
| 3 | Kind / Coord / Templata trio | done | `slab-3-complete` · `FrontendRust/docs/migration/handoff-slab-3.md` |
| 4 | environments + real interner bodies | done | `slab-4-complete` · `FrontendRust/docs/migration/handoff-slab-4.md` |
| 5 | expression AST (3 enums + 53 payload structs, `ast/expressions.rs`) | done | `slab-5-complete` · `FrontendRust/docs/migration/handoff-slab-5.md` |
| 6 | `CompilerOutputs` data + `PtrKey` + `DeferredActionT` | done | `slab-6-complete` · `FrontendRust/docs/migration/handoff-slab-6.md` |
| 7 | `HinputsT` residual cleanup + `Compiler` shell + `run_typing_pass` | done | `slab-7-complete` · `FrontendRust/docs/migration/handoff-slab-7.md` |
| 8 | `CompilerOutputs` method signatures | done | `slab-8-complete` · `FrontendRust/docs/migration/handoff-slab-8.md` |
| 9 | `object TemplataCompiler` method signatures | done | `slab-9-complete` · `FrontendRust/docs/migration/handoff-slab-9.md` |
| 10 | compiler.rs residuals + orphans + templata_compiler.rs tail methods | done | `slab-10-complete` · `FrontendRust/docs/migration/handoff-slab-10.md` |
| 11 | expression-layer sigs: expression/pattern/block/call_compiler | done | `slab-11-complete` · `FrontendRust/docs/migration/handoff-slab-11.md` |
| 12 | solver + resolver sigs: infer/overload/impl/reachability + IInfererDelegate deletion | done | `slab-13-complete` · `FrontendRust/docs/migration/handoff-slab-12.md` |
| 13 | function/citizen/macros sigs: function_compiler/function_body/destructor/struct_compiler_core/anonymous_interface_macro | done | `slab-13-complete` · `FrontendRust/docs/migration/handoff-slab-13.md` |
| 14 | placeholder types flesh-out: ICompileErrorT (55 variants), IBoundArgumentsSource (trait→enum), IFunctionGenerator (trait→enum), IPlaceholderSubstituter (trait def), InferEnv, solver structs, error/result enums, HinputsT::new() | done | `FrontendRust/docs/migration/handoff-slab-14.md` |
| 14b | second-wave placeholder flesh-out: CitizenDefinitionT, IStructMemberT, IMemberTypeT, IFunctionAttributeT, ICitizenAttributeT, ICalleeCandidate, function result enums, ITypingPassSolverError (28 variants), misc demotions | done | `slab-14b-complete` · `FrontendRust/docs/migration/handoff-slab-14b.md` |

---

## Slab Descriptions

### Slab 0 — Arena substrate scaffolding.

### Slab 1 — Leaf types
Real Copy enums for OwnershipT / MutabilityT / VariabilityT / LocationT; primitive KindT payloads; leaf-value templatas.

### Slab 2 — Name hierarchy
Monomorphic IdT; ~60 concrete name structs + 22 inline-owned sub-enums per the DAG rule; From/TryFrom bridges; IDEPFL `*ValT` companions.

### Slab 3 — Kind/Coord/Templata trio
KindT inline wrapper + interned concrete payloads; ITemplataT inline wrapper; CoordListTemplataValT for the one slice-bearing templata payload; PrototypeValT/SignatureValT with `'tmp`.

### Slab 4 — Envs + real interner bodies
9 env structs + 2 wrapper enums + GlobalEnvironmentT + TemplatasStoreT + IEnvEntryT + IVariableT/ILocalVariableT + 4 concrete variables, all Scala-parity. 9 env-specific builders + TemplatasStoreBuilder with `build_in(interner) -> &'t FooEnvironmentT`. TypingInterner got real bodies (560 lines) with 6 family-level hashbrown HashMaps keyed on tagged-union Val enums (INameValT 72 variants / InternedKindPayloadValT 6 / InternedTemplataPayloadValT 6), plus ~84 per-concrete thin-wrapper methods via four `impl_intern_*_wrapper_*` macros. Five heavy-templata env refs flipped `&'s` → `&'t`. Box stubs deleted.

### Slab 5 — Expression AST
3 enums (48 + 5 + 2 variants) + 53 payload structs + result types. `#[derive(PartialEq, Debug)]` family-wide.

### Slab 6 — CompilerOutputs data shape
23-field struct with PtrKey-keyed HashMaps, DeferredActionT 2-variant enum, `::new()` constructor. PtrKey newtype in its own module `ptr_key.rs`. Method signatures deferred to Slab 8.

### Slab 7 — HinputsT + Compiler shell + run_typing_pass
Two `()` → `&'t PrototypeT` flips, `_phantom` deletion, `Compiler::compile_program` and `Compiler::drain_all_deferred` panic-stub methods, `pub fn run_typing_pass(...)` free fn.

### Slab 8 — CompilerOutputs method signatures
Free-fn stubs lifted into one-fn `impl<'s, 't> CompilerOutputs<'s, 't> where 's: 't { pub fn ... }` blocks. `&self`/`&mut self` per mutation, `&'t` returns, IdT by value, env params `&'t IInDenizenEnvironmentT<'s, 't>`. Bodies panic-stubbed.

### Slab 9 — TemplataCompiler object methods
Free-fn stubs lifted onto `impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't> where 's: 't`. `interner` and `keywords` parameters dropped (use `self.typing_interner` / `self.keywords`). `bound_arguments_source: &'t dyn IBoundArgumentsSource<'s, 't>` on the 11 substitute_* methods.

### Slab 10 — Compiler.rs residuals + orphans + TemplataCompiler tail
Flipped `()` placeholders in compiler.rs methods (`preprocess_struct`, `preprocess_interface`, `determine_macros_to_call`, `ensure_deep_exports`, `is_root_function`, `is_root_struct`, `is_root_interface`, `evaluate`) and orphan static fns; filled `local_helper.rs` 2 orphans + `struct_compiler.rs` 1 orphan; filled 14 bare-`(&self)` tail impls in `templata_compiler.rs`. Split giant impl blocks.

### Slab 11 — Expression-layer signatures
`expression_compiler.rs`, `pattern_compiler.rs`, `block_compiler.rs`, `call_compiler.rs`. Novel patterns: `NodeEnvironmentBox` → `&mut NodeEnvironmentBuilder<'s, 't>`, `FunctionEnvironmentBoxT` → `&mut FunctionEnvironmentBuilder<'s, 't>`, closure params use `impl FnOnce(...)`.

### Slab 12 — Solver + resolver signatures
`infer_compiler.rs` + `overload_resolver.rs` + `impl_compiler.rs` + `reachability.rs`. Deleted `IInfererDelegate` vestigial trait + dropped `&dyn IInfererDelegate` params from all signatures. `InferEnv<'s>` is a PhantomData placeholder (pass by value as Copy). `r#continue` raw identifier preserved.

### Slab 13 — Function/citizen/macros signatures (final signature-rewrite slab)
`function_compiler.rs` + `function_body_compiler.rs` + `destructor_compiler.rs` + `struct_compiler_core.rs` + `anonymous_interface_macro.rs`. Preserved `_core`, `_ext`, `_anonymous_interface` suffix disambiguators.

### Slab 14 — Placeholder types flesh-out
Per-type, not per-file. `ICompileErrorT` (55 variants), `IDefiningError` (2), `IResolvingError` (2), `IFindFunctionFailureReason` (11), `IResolveOutcome` (2). `IBoundArgumentsSource` flipped from trait to 2-variant enum. `IFunctionGenerator` flipped from trait to dispatch-tag enum. `IPlaceholderSubstituter` struct definition + 3 panic-stub methods. Solver structs: `InferEnv<'s, 't>`, `InitialKnown`, `InitialSend`, `CompleteDefineSolve`, `CompleteResolveSolve`. `HinputsT::new()` constructor added. Bulk-updated 136 panic messages from `Slab 14` → `Slab 15`.

### Slab 14b — Second-wave placeholder flesh-out
`CitizenDefinitionT` (fold 2 structs), `IStructMemberT` (2 variants), `IMemberTypeT` (2 variants), `IFunctionAttributeT` (4 variants with `ExternT`), `ICitizenAttributeT` (2 variants), `ICalleeCandidate` (3 variants), `AbstractT` (unit struct). Function-result enum triples: `IDefineFunctionResult`, `IResolveFunctionResult`, `IStampFunctionResult`. `ITypingPassSolverError` (28 variants). Reduced `_Phantom` count from 12 → 1 (only `IRegionNameT` remains, deferred — 0 Scala implementors found).

---

## Ground Rule: Preserve The `/* scala */` Audit Trail

The typing/ skeleton has a `/* ... */` block with the Scala source directly below every Rust definition. The `.claude/hooks/check-scala-comments` pre-commit hook does exact-match comparison and rejects any edit inside those blocks. Rules:

- Replace the empty Rust stub in-place with the real definition. Keep the Scala `/* ... */` block below unchanged.
- Never move a Rust definition away from its Scala block.
- A Rust definition grown to need helper structs (e.g. an IdValT companion for an IdT) gets its companion block **adjacent to** the main definition, with a `// (no scala counterpart — …)` note.

---

## Per-slab handoff docs

All per-slab handoff docs live in `FrontendRust/docs/migration/handoff-slab-*.md`. These contain translation tables, step-by-step plans, gotchas, and examples specific to each slab. They remain useful as reference for understanding *why* a particular design choice was made during that slab.

---

## Slab 15 Session Retrospectives

### Slab 15b — Interning approach hardening
During body migration of `simple_program_returning_an_int_explicit`, an assertion `header.to_signature().id == needle_signature.id` was failing because `assemble_name` was constructing un-interned `IdT` values that had different `init_steps` slice pointers than the canonical interned ones. Three rounds of infrastructure work followed:

1. **Sealed 21 TFITCX-Interned types** with `MustIntern` (private-constructor witness field per @SICZ): `IdT`, the 15 transient (slice-bearing) Name types, and the 5 Scala-`IInterning` kind payloads. Construction now requires going through the interner.
2. **Reconciled Rust's Interned classification with Scala's `IInterning` trait.** 14 types reclassified to `/// Value-type` (`SignatureT`, `PrototypeT`, `KindPlaceholderT`, all 11 Templata variants, `CoordListTemplataT`).
3. **Pushed identity-equality down to identity-bearing types** per @IEOIBZ. Manual `std::ptr::eq` + `std::ptr::hash` impls on `IEnvironmentT`, `FunctionA`, `StructA`, `InterfaceA`, `ImplA`, `FunctionHeaderT`. Wrapper types just `#[derive(PartialEq, Eq, Hash)]`. Variant env types kept their `self.id == other.id` impls.

Plus IDEPFL uniformity: added Val mirror types for the 5 sealed kind-payload types.

### Slab 15c — Env-builder & `&'t self` hardening
While migrating `finish_function_maybe_deferred` → `declare_and_evaluate_function_body`, three blockers addressed at TL/architect level:

1. **Removed `FunctionEnvironmentBoxT` from Scala** for `declareAndEvaluateFunctionBody` and `evaluateFunctionBody`. Edited Scala source first, updated Rust audit-trail blocks, then changed Rust signatures to `&'t FunctionEnvironmentT` directly.
2. **Eagerly promoted ~23 panic stubs to `&'t self`** across `function_environment_t.rs` and `environment.rs` for wrap-self / embed-self patterns. Doc rule added as design v3 §3.4a.
3. **Added `snapshot(&self, interner)` to `TemplatasStoreBuilder`, `NodeEnvironmentBox`, `FunctionEnvironmentBuilder`** — Scala's `Box.snapshot` semantics. Design v3 §3.3 updated.

### Slab 15d — NodeEnvironmentBox restructuring + expression-hierarchy equality opt-out
1. **`result()` dispatch added on `ReferenceExpressionTE` and `AddressExpressionTE`.** Slice pipeline emitted module-level free fns; wrapped each in proper `impl` blocks dispatching to per-variant `e.result()` panic stubs.
2. **`NodeEnvironmentBuilder` renamed to `NodeEnvironmentBox`** (architect-level Scala-parity rename). With `snapshot()` added in 15c, the type now has full `Box` semantics.
3. **Reversed design v3 §3.3's "Box deleted in Rust" stance.** Walked the Scala audit-trail block at `function_environment_t.rs:822-1020` and sliced in proper Rust impls adjacent to each Scala `/* def ... */`. Implemented `add_variable`, `get_all_locals`, `mark_local_unstackified`. Panic-stubbed the other 22 methods.
4. **`local_helper.rs` 7 method signatures aligned**: `&NodeEnvironmentT` → `&mut NodeEnvironmentBox`.
5. **Dropped `NodeEnvironmentBox::build_in` and `FunctionEnvironmentBuilder::build_in`** — both Rust-only. Only finalizer is `env.snapshot(...)`.
6. **Dropped `derive(PartialEq)` from the entire expression hierarchy in `expressions.rs`** — mirrors Scala's 52 `vcurious()` equals overrides. Documented as a vcurious-mirror exception.
7. **`migration-drive.md` got two new notes**: TFITCX classification check before adding Clone/Copy/PartialEq derives, and a "re-read this skill on every compaction" note.

### Slab 15e — first end-to-end test passing
`simple_program_returning_an_int_explicit` is now **green end-to-end**. The body of `Compiler::evaluate`'s post-deferred phase is wired up; eight `Slab 10` panic stubs in `compiler_outputs.rs` filled in; `compile_i_tables` / `make_interface_edge_blueprints` / `ensure_deep_exports` got Scala-shaped skeletons with panics in unhandled branches; `HinputsT` field types reshaped to `Vec<&'t T>`; `lookup_function_by_human_name` ported verbatim. Driving test promoted to `hardcoding_negative_numbers`.

Three meta-lessons folded into TL.md rules: "untested branches" means code paths not data values; SPDMX-vs-skeleton-with-panics tension resolved via temp-disable; `add_function`'s `signature: &'t SignatureT` is SPDMX-B documented adaptation.

### Slab 15f — typing-pass test traversal + LetSE scaffolding
`hardcoding_negative_numbers` is now **green end-to-end**. Three pieces of TL/architect scaffolding:

1. **`src/typing/test/traverse.rs`** (~1740 lines) — Rust analog of Scala's `Collector.only` / `Collector.all`. Mirrors postparsing precedent. `NodeRefT<'s, 't>` enum with ~95 variants, ~75 `visit_*` walkers, 5 `#[macro_export]` macros. Full upfront coverage; stop-at-trait for the 74 `INameT` variants etc.
2. **`get_rune_types_from_pattern`** at `src/higher_typing/patterns.rs` — verbatim port of Scala's `PatternSUtils.getRuneTypesFromPattern` as a free `pub fn`.
3. **`LetExprRuneTypeSolverEnv`** at the bottom of `expression_compiler.rs` — Scala's anonymous `new IRuneTypeSolverEnv { ... }` becomes a named struct + impl block.

Three meta-lessons: anonymous Scala trait impls map to named per-site Rust structs; test-traversal scaffolding is TL territory; AIMITIPX rule applies to test-traversal patterns (no `if matches!` or guards on `collect_only_tnode!` patterns).

### AASSNCMCX foundational sweep
1. **AASSNCMCX sweep**: convert all `Vec<T>` / `HashMap<K, V>` fields on `/// Arena-allocated` structs to `&'t [T]` and `ArenaIndexMap<'t, K, V>`. `InstantiationBoundArgumentsT` and `InstantiationReachableBoundArgumentsT` reclassified `/// Arena-allocated`; `HinputsT` reclassified `/// Temporary state`; `LocationInFunctionEnvironmentT` reclassified `/// Value-type`; `FunctionDefinitionT.header` flipped to `&'t FunctionHeaderT`.
2. **`PtrKey<'t, T>` scope tightened.** Now for `/// Arena-allocated` identity types only. Drops `PtrKey<'t, IdT>` etc. throughout `CompilerOutputs` (~17 fields).
3. **`IInstantiationNameT::template_args()` dispatch added** at `names/names.rs` (NNDX-blocked add — 21 variants).
4. **`IPlaceholderSubstituter` closure-capture fields added** at `templata_compiler.rs` (5 fields mirroring Scala's anonymous-trait-impl).
5. **`FunctionBodyMacro` dispatch wired** (foundational SSTREX port): `name_to_function_body_macro: ArenaIndexMap` field on `GlobalEnvironmentT`; 15-arm dispatch method.
6. **Doc updates**: `arenas.md` got "nothing in an arena is ever mutated" invariant + Mutation Patterns section; `WVSBIZ` broadened to seven-principle decision framework.
7. **Two new residuals** added: nondeterminism elimination and Arena-allocated vs Temporary state revisit.

Three meta-lessons: anonymous Scala trait impls map to named per-site Rust structs OR dispatch-tag enums depending on shape; JR-level lifetime promotion `&'t self` for embedded-back-pointer cases; `PtrKey<T>` is wrong for canonical-content-hash types.

### Slab 15i — `tests_panic_return_type` end-to-end
Test passes (program: `import v.builtins.panic.*; exported func main() int { x = { __vbi_panic() }(); }`). Four pieces of TL/architect scaffolding:

1. **Sanity-check conclusion pipeline ported on Compiler** — `sanity_check_conclusion`, `get_placeholders_in_id`, `get_placeholders_in_templata`, `get_placeholders_in_kind` (sliced into `compiler.rs:225-319`). Both `infer_compiler.rs::make_solver_state` call sites wired.
2. **`is_descendant_kind`/`is_ancestor_kind` sliced in on Compiler** (`compiler.rs:404-430`). Both panic-stubbed; `_kind` suffix per Compiler/ImplCompiler name-collision pattern.
3. **Layer-skip fix in `evaluate_templated_function_from_call_for_prototype`** — delegate to closure-or-light layer, not direct coercion to `BuildingFunctionEnvironmentWithClosuredsT`.
4. **`make_named_env` arena-allocation pattern** at the call site of `get_or_evaluate_templated_function_for_banner`.

Plus JR-level body migrations: `make_closure_understruct_core` (~150 lines), `make_implicit_drop_function_struct_drop`, `FunctionBodyMacro::generate_function_body` (15-arm dispatch), `IInstantiationNameT::template_args() -> &'t [ITemplataT]` (21-arm dispatch).

Three meta-lessons: Compiler/ImplCompiler name-collision is a recurring pattern; lifetime errors with established AASSNCMCX shapes are JR-level; pre-existing Guardian-flagged parity gaps fix locally if cheap, else pause.

### Slab 15j scaffolding round 2
No new tests passed end-to-end; TL/architect-level infrastructure unblocking JR on `simple_struct_read`.

1. **`IInDenizenEnvironmentT::Export` and `::Extern` variants added** (`env/environment.rs:298`).
2. **`ISubKindTT` trimmed to 3 variants** (`types/types.rs:419`).
3. **`solve_call_rule` slice-pipeline cleanup** (`infer/compiler_solver.rs:1727`).
4. **Structural `PartialEq, Eq` derived on `InstantiationBoundArgumentsT` and `InstantiationReachableBoundArgumentsT`**.
5. **`IFunctionNameT::parameters()` (literal Scala port) + `INameT::parameters()` (Rust adaptation)**.
6. **Top-of-file callout: "JR does not have access to TL.md"**.
7. **`migration-drive.md` one-shot lifetime fix rule**.
8. **Orphan-sweep plan written** at `/Users/verdagon/.claude/plans/lets-do-proactive-please-proud-feather.md`.

Three meta-lessons: identity vs value-bag is decided by Scala equality story not TFITCX; rustc-suggested lifetime fixes are JR-level even when they cascade; cross-check JR's Guardian-verdict reports against the verdict file.
