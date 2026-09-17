# DO NOT FOLLOW — Historical, Obsolete (v2)

> **This document is historical and obsolete.** It described a four-arena model (`'p, 's, 'e, 't`) with a separate env arena, generic `IdT<'t, T>`, interned `KindT` / `ITemplataT`, env-arena allocation, and IDs-not-refs in heavy templatas. **Every one of those decisions was reversed during Slabs 2–4.**
>
> The current authoritative design lives in **`TL-HANDOFF.md` at the repository root**. Specifically: three arenas (`'p, 's, 't`) with envs in `'t`, monomorphic `IdT<'s, 't>`, inline-owned `KindT` / `ITemplataT` wrappers over interned payloads, and heavy templatas holding direct scout refs. See also `FrontendRust/docs/reasoning/idt-typed-view-alternatives.md` and `FrontendRust/docs/reasoning/environments-per-denizen-long-term.md`.
>
> Preserved only for audit-trail. **Do not use this doc to guide implementation work — it will lead you in the wrong direction on every major question.**

---

# Typing Pass Migration Design (v2, obsolete)

This document describes the architectural decisions for migrating `src/typing/` from Scala to Rust. It supersedes `typing-pass-design.md` (v1), which was written before the per-pass-arena split (commit `10082060`) and contains outdated lifetime model details.

---

## Part 0: What Changed From v1

| v1 | v2 |
|---|---|
| `'a` interner arena + `'t` typing arena (two lifetimes) | `'p, 's, 'e, 't` (four arenas) |
| `Compiler<'a, 's, 'ctx, 't>` | `Compiler<'s, 'ctx, 'e, 't>` |
| `IEnvironmentT<'a, 's, 't>` with `Rc`-based env refs | `&'e IEnvironmentT<'s, 'e, 't>` arena-allocated, no `Rc` |
| Typing types carry `'a, 's, 't` | Typing output types carry only `'t` |
| `OverloadSetT` carries `Rc<IEnvironmentT>` directly | `OverloadSetT<'t>` carries `ctx_id`; env in side table |
| Heavy templatas carry `&'s FunctionA` directly | Heavy templatas carry `&'t IdT<'t>`; side tables |
| Envs stored in `Rc`, eagerly dropped | Envs arena-allocated in `'e`, die at pass end |

---

## Part 1: Arena and Lifetime Model

### 1.1 Four Arenas

The typing pass operates with four separate `bumpalo::Bump` arenas, each with its own lifetime:

- **`'p` — Parser arena (`ParseArena<'p>`)**: Interned strings (`StrI<'p>`), package/file coordinates, parser AST. Unchanged from existing codebase.
- **`'s` — Scout arena (`ScoutArena<'s>`)**: Postparser + higher-typing output (`FunctionA<'s>`, `StructA<'s>`, etc.), interned postparser names (`INameS<'s>`, `IRuneS<'s>`). Unchanged.
- **`'e` — Env arena (new, `EnvArena<'e>`)**: Typing-pass environments. Created at typing-pass start, dropped at pass end. All env instances arena-allocated; parent chains via `&'e` refs.
- **`'t` — Typing arena (new, `TypingInterner<'t>`)**: Interned typing-pass types (names, kinds, coords, templatas) and output AST (`FunctionDefinitionT`, expressions, etc.). **Created before the typing pass, outlives the typing pass.** Drops when the downstream consumer (instantiator) no longer needs its output.

All four arenas use `bumpalo` (not bump-scope). Arena-allocated structs follow AASSNCMCX: no `Vec`/`HashMap`/`String` inside arena types; use arena slices and arena-backed sorted-pair maps instead.

### 1.2 Lifetime Invariants

1. **`'t` must outlive `'s, 'e, 'p`**: the typing arena is created first (in the call frame above the typing pass) and dropped last. Scout and env arenas die at pass end; only `'t` data survives.
2. **No `'t`-allocated type holds `&'s`, `&'p`, or `&'e` refs.** Cross-arena references from output into earlier arenas are never allowed. Any scout/parser data referenced from `'t` output must be **re-interned** into `'t` at the pass boundary.
3. **Working-state types (`CompilerOutputs`, `IEnvironmentT`) may hold `&'s`/`&'e` refs.** These types die at pass end, so their refs never dangle.
4. **`'t` is syntactically independent of `'s`, `'e`, `'p`.** No bound like `'t: 's` is declared, because `'t` must be free to outlive them. Rust's type system accepts this as long as no `'t`-typed value transitively contains a `'s`/`'e`/`'p` ref.
5. **Re-interning at the `'s → 't` boundary** is mandatory for: `StrI`, package/file coordinates, `RangeS`, `CodeLocationS`, `IImpreciseNameS`, any other scout-lifetime data that appears in typing output.

### 1.3 Arena Construction Order

```rust
fn run_typing_pass<'p, 's, 't>(
    parse_arena: &ParseArena<'p>,
    scout_arena: &ScoutArena<'s>,
    typing_interner: &'t TypingInterner<'t>,  // created by caller, outlives pass
    program_a: &'s ProgramA<'s>,
) -> HinputsT<'t> {
    let env_arena = EnvArena::new();  // 'e begins here
    let compiler = Compiler::new(parse_arena, scout_arena, &env_arena, typing_interner, ...);
    let hinputs = compiler.compile(program_a);
    hinputs  // HinputsT<'t>, free of 's/'e/'p refs
    // env_arena drops here → 'e gone
    // caller may drop scout_arena, parse_arena → 's, 'p gone
}
```

### 1.4 Where Each Type Lives

| Type | Lifetimes | Arena |
|---|---|---|
| `HinputsT` | `<'t>` | typing arena, survives pass |
| `FunctionDefinitionT`, `StructDefinitionT`, `InterfaceDefinitionT`, `ImplT`, `EdgeT` | `<'t>` | typing arena |
| `KindT`, `CoordT`, `IdT`, `INameT` | `<'t>` | typing arena, interned |
| `ITemplataT` (all variants, leaf and heavy) | `<'t>` | typing arena |
| `ReferenceExpressionTE`, `AddressExpressionTE` | `<'t>` | typing arena, not interned |
| `IEnvironmentT` and sub-types | `<'s, 'e, 't>` | env arena, references scout data |
| `CompilerOutputs` | `<'s, 'e, 't>` | stack; dies at pass end |
| `Compiler` (god struct) | `<'s, 'ctx, 'e, 't>` | stack; dies at pass end |

---

## Part 2: The God Struct

### 2.1 Architecture

All Scala sub-compilers (`FunctionCompiler`, `ExpressionCompiler`, `StructCompiler`, `ImplCompiler`, `OverloadResolver`, `InferCompiler`, `TemplataCompiler`, `ConvertHelper`, `DestructorCompiler`, `VirtualCompiler`, `SequenceCompiler`, `ArrayCompiler`, `EdgeCompiler`, `BlockCompiler`, `PatternCompiler`, `CallCompiler`, `LocalHelper`) collapse into methods on a single `Compiler` struct.

```rust
pub struct Compiler<'s, 'ctx, 'e, 't> {
    pub typing_interner: &'ctx TypingInterner<'t>,
    pub scout_arena: &'ctx ScoutArena<'s>,
    pub env_arena: &'ctx EnvArena<'e>,
    pub keywords: &'ctx Keywords<'t>,  // re-interned into 't
    pub opts: &'ctx TypingPassOptions,
    pub global_env: Rc<IEnvironmentT<'s, 'e, 't>>,  // top-level env, arena-allocated
    pub name_translator: NameTranslator,
}
```

The god struct holds only **immutable configuration**. All mutable state is passed as `&mut` parameters, primarily `&mut CompilerOutputs<'s, 'e, 't>`.

### 2.2 Why `&self` + `&mut coutputs` Works

Deep mutual recursion (e.g. `evaluate_expression → evaluate_prefix_call → find_function → evaluate_function_body → evaluate_expression`) requires re-entrant access. With `&mut self`, this would require two simultaneous mutable borrows of the god struct. With `&self` (immutable config) plus `&mut coutputs`, re-entrancy is fine — multiple immutable borrows of `self` are allowed.

### 2.3 Macros

Macros (`AsSubtypeMacro`, `LockWeakMacro`, `StructDropMacro`, etc.) are stored in the global environment and receive the god struct at call time:

```rust
trait IFunctionGenerator<'s, 'e, 't> {
    fn generate(
        &self,
        compiler: &Compiler<'s, '_, 'e, 't>,
        coutputs: &mut CompilerOutputs<'s, 'e, 't>,
        env: &'e IEnvironmentT<'s, 'e, 't>,
        // ...
    ) -> &'t FunctionHeaderT<'t>;
}
```

`AsSubtypeMacro` (and similar macros with multiple dispatch paths) becomes an enum with one variant per kind, rather than a trait-object hierarchy.

### 2.4 Delegate Traits Eliminated

In Scala, sub-compilers were wired via anonymous delegate traits (`IExpressionCompilerDelegate`, `IFunctionCompilerDelegate`, etc.). With the god struct, these are unnecessary — every method is on the same struct and directly accessible via `self.method_name(...)`.

---

## Part 3: Environments

### 3.1 Arena-Allocated, Not Refcounted

Environments live in the `'e` arena. Parent chains use `&'e` references:

```rust
pub enum IEnvironmentT<'s, 'e, 't> {
    Package(PackageEnvironmentT<'s, 'e, 't>),
    Citizen(CitizenEnvironmentT<'s, 'e, 't>),
    Function(FunctionEnvironmentT<'s, 'e, 't>),
    Node(NodeEnvironmentT<'s, 'e, 't>),
    BuildingWithClosureds(BuildingFunctionEnvironmentWithClosuredsT<'s, 'e, 't>),
    BuildingWithClosuredsAndTemplateArgs(BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 'e, 't>),
    General(GeneralEnvironmentT<'s, 'e, 't>),
    Export(ExportEnvironmentT<'s, 'e, 't>),
    Extern(ExternEnvironmentT<'s, 'e, 't>),
}

pub struct NodeEnvironmentT<'s, 'e, 't> {
    pub parent: &'e IEnvironmentT<'s, 'e, 't>,
    pub parent_function_env: &'e FunctionEnvironmentT<'s, 'e, 't>,
    pub life: LocationInFunctionEnvT<'t>,
    pub templatas: TemplatasStoreT<'t>,  // arena-backed sorted Vec-pair, not HashMap
    pub declared_locals: &'e [&'e ILocalVariableT<'t>],
    pub templata_id: OnceCell<&'t IdT<'t>>,  // cached FunctionTemplata id (see §3.5)
    // ...
}
```

The bound `'t: 'e` is **not declared**. Env types transitively hold `&'t` refs (via templatas), but since `'t` outlives `'e` in all call frames that construct envs, this works without an explicit bound. If rustc requires it in practice, `'t: 'e` is safe to add.

### 3.2 `TemplatasStoreT` Uses Arena-Backed Sorted Pairs

Scala's `TemplatasStore` uses `Map[INameT, IEnvEntry]`. In Rust we **must not** use `HashMap`/`IndexMap` inside arena types: bumpalo doesn't run destructors (AASSNCMCX), and heap-backed HashMaps at 1M+ envs would blow memory. Instead:

```rust
pub struct TemplatasStoreT<'t> {
    pub name_to_entry: &'t [(INameT<'t>, IEnvEntryT<'t>)],     // sorted by name ptr
    pub imprecise_to_entries: &'t [(IImpreciseNameT<'t>, &'t [IEnvEntryT<'t>])],
    pub id: &'t IdT<'t>,  // the template id this store is associated with (parent-function id)
}
```

Sorted by interned pointer address → binary search is O(log N). Most scopes have <10 entries, where linear scan of a `Vec`-pair slice is faster than HashMap anyway. No `Drop`, no heap backing.

### 3.3 Mutable Building Phase

During construction, an env is mutable — e.g., adding variables/locals/templatas as a block is compiled:

```rust
pub struct NodeEnvironmentBuilder<'s, 'e, 't> {
    pub parent: &'e IEnvironmentT<'s, 'e, 't>,
    pub parent_function_env: &'e FunctionEnvironmentT<'s, 'e, 't>,
    pub life: LocationInFunctionEnvT<'t>,
    pub templatas_pending: Vec<(INameT<'t>, IEnvEntryT<'t>)>,  // heap Vec, OK on stack
    pub declared_locals_pending: Vec<&'e ILocalVariableT<'t>>,
    // ...
}

impl<'s, 'e, 't> NodeEnvironmentBuilder<'s, 'e, 't> {
    pub fn build_in(self, env_arena: &'e EnvArena<'e>) -> &'e NodeEnvironmentT<'s, 'e, 't> {
        // Sort the pending entries, copy into arena slices, construct the final env,
        // allocate into arena, return &'e ref.
        env_arena.alloc(NodeEnvironmentT {
            parent: self.parent,
            // ... freeze self.templatas_pending into &'t [...] via typing_interner
            // ... etc
        })
    }
}
```

Mutable builders live on the stack with heap `Vec`s. When finalized, entries are sorted and copied into arena slices, and the final immutable env is placed in `'e`.

### 3.4 `&'e` vs `&IEnvironmentT` — Storage vs Read

Scala's `NodeEnvironmentBox.snapshot: NodeEnvironmentT` is called ~36 times in ExpressionCompiler.scala — many for **transient reads** (passing to `isTypeConvertible`, parent traversals, etc.), not just for long-term storage.

In Rust, we distinguish:
- **`&IEnvironmentT<'_, 'e, 't>`** (elided lifetime) — read-only borrow, transient, zero cost, used for helpers that just inspect the env.
- **`&'e IEnvironmentT<'s, 'e, 't>`** — arena-pinned reference, needed when storing into a side table, output type, or parent pointer.

`&'e` promotion via arena allocation happens only at explicit "store this env" points. Transient reads use `&` and Rust's lifetime elision handles the rest.

### 3.5 `FunctionTemplata` ID Caching On Envs

Scala's `FunctionEnvironmentT` has `def templata = FunctionTemplataT(parentEnv, this.function)` — produces a fresh instance on every call. In Rust with ID-based side tables, we cache the ID:

```rust
pub struct FunctionEnvironmentT<'s, 'e, 't> {
    // ...
    pub templata_id: OnceCell<&'t IdT<'t>>,
}

impl<'s, 'e, 't> FunctionEnvironmentT<'s, 'e, 't> {
    pub fn templata_id(
        &self,
        compiler: &Compiler<'s, '_, 'e, 't>,
        coutputs: &mut CompilerOutputs<'s, 'e, 't>,
    ) -> &'t IdT<'t> {
        *self.templata_id.get_or_init(|| {
            let id = compiler.typing_interner.intern_id(/* ...templata id... */);
            coutputs.origin_function_a.insert(id, self.function);
            coutputs.origin_env.insert(id, compiler.env_arena.alloc(self.clone()));
            id
        })
    }
}
```

First access allocates; subsequent accesses return the cached ID.

### 3.6 Env-ID Uniqueness Is NOT Required

Environments in Rust do not need unique `id` fields per live instance. Scala allows collisions (`NodeEnvironmentT.id = parent_function_env.id`; GeneralEnvironmentT reuses caller ids; Box wrappers share ids with wrapped envs). Rust does not key any HashMap on env's own `id` field:

- `CompilerOutputs.function_name_to_outer_env` etc. are keyed by **function/type template ids** (which are genuinely unique), not env's own id.
- OverloadSet env lookup uses `ctx_id` (synthetic, allocated fresh per OverloadSet construction), not env id.
- Heavy templata side tables use IDs minted at templata construction, not env id.

Env `id` is used only for diagnostics, for child template-store tagging, and matching Scala's semantics on equality (which Rust doesn't replicate since we never hash envs).

### 3.7 Env Arena Global To Compiler

The `'e` arena lives on the `Compiler` god struct, constructed at typing-pass start, accessible from every compilation method. It is **not** per-denizen or per-function. Required because:

- Closures construct envs that need `'e` allocation at the moment `FunctionTemplataT` is built (from scattered sites like `StructCompilerCore.scala:374`, `AbstractBodyMacro.scala:37`).
- Envs reference each other via parent chains that cross denizen boundaries.

Memory-wise this is equivalent to Scala's GC approach — envs live until pass end regardless.

---

## Part 4: CompilerOutputs

### 4.1 Structure

`CompilerOutputs` is the mutable accumulator threaded through the entire pass. It carries `<'s, 'e, 't>` because it holds side tables referencing scout and env data:

```rust
pub struct CompilerOutputs<'s, 'e, 't> {
    // Function registries (all keys are 't-interned ids)
    pub return_types_by_signature: HashMap<PtrKey<'t, SignatureT<'t>>, CoordT<'t>>,
    pub signature_to_function: HashMap<PtrKey<'t, SignatureT<'t>>, &'t FunctionDefinitionT<'t>>,
    
    // Declaration tracking
    pub function_declared_names: HashMap<PtrKey<'t, IdT<'t>>, RangeT<'t>>,
    pub type_declared_names: HashSet<PtrKey<'t, IdT<'t>>>,
    
    // Env storage, keyed by function/type template id (per Scala parity)
    pub function_name_to_outer_env: HashMap<PtrKey<'t, IdT<'t>>, &'e IEnvironmentT<'s, 'e, 't>>,
    pub function_name_to_inner_env: HashMap<PtrKey<'t, IdT<'t>>, &'e IEnvironmentT<'s, 'e, 't>>,
    pub type_name_to_outer_env: HashMap<PtrKey<'t, IdT<'t>>, &'e IEnvironmentT<'s, 'e, 't>>,
    pub type_name_to_inner_env: HashMap<PtrKey<'t, IdT<'t>>, &'e IEnvironmentT<'s, 'e, 't>>,
    
    // Side tables: 't-id → 's scout data (die at pass end)
    pub origin_function_a: HashMap<PtrKey<'t, IdT<'t>>, &'s FunctionA<'s>>,
    pub origin_struct_a: HashMap<PtrKey<'t, IdT<'t>>, &'s StructA<'s>>,
    pub origin_interface_a: HashMap<PtrKey<'t, IdT<'t>>, &'s InterfaceA<'s>>,
    pub origin_impl_a: HashMap<PtrKey<'t, IdT<'t>>, &'s ImplA<'s>>,
    pub origin_env: HashMap<PtrKey<'t, IdT<'t>>, &'e IEnvironmentT<'s, 'e, 't>>,
    
    // OverloadSet env lookup (see §5)
    pub overload_resolution_ctx: HashMap<PtrKey<'t, OverloadResolutionCtxIdT<'t>>, &'e IEnvironmentT<'s, 'e, 't>>,
    
    // Definitions, impls, exports, externs — all 't-only
    pub struct_template_name_to_definition: HashMap<PtrKey<'t, IdT<'t>>, &'t StructDefinitionT<'t>>,
    pub interface_template_name_to_definition: HashMap<PtrKey<'t, IdT<'t>>, &'t InterfaceDefinitionT<'t>>,
    pub all_impls: HashMap<PtrKey<'t, IdT<'t>>, &'t ImplT<'t>>,
    pub sub_citizen_template_to_impls: HashMap<PtrKey<'t, IdT<'t>>, &'t [&'t ImplT<'t>]>,
    pub super_interface_template_to_impls: HashMap<PtrKey<'t, IdT<'t>>, &'t [&'t ImplT<'t>]>,
    pub kind_exports: Vec<&'t KindExportT<'t>>,
    pub function_exports: Vec<&'t FunctionExportT<'t>>,
    pub kind_externs: Vec<&'t KindExternT<'t>>,
    pub function_externs: Vec<&'t FunctionExternT<'t>>,
    pub instantiation_name_to_bounds: HashMap<PtrKey<'t, IdT<'t>>, &'t InstantiationBoundArgumentsT<'t>>,
    
    // Deferred evaluation queues (see §4.4)
    pub deferred_actions: VecDeque<DeferredActionT<'s, 'e, 't>>,
    pub finished_deferred_function_body_compiles: HashSet<PtrKey<'t, PrototypeT<'t>>>,
    pub finished_deferred_function_compiles: HashSet<PtrKey<'t, IdT<'t>>>,
}
```

### 4.2 `PtrKey<'t, T>` Newtype For HashMap Keys

Interned `&'t T` refs hash by pointer identity, not structural equality. This is correctness-critical (pointer-eq ⇔ value-eq given correct interning) and fast (O(1) hash).

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

A common operation is "look up something by id, then use it while continuing to mutate `coutputs`." The naïve pattern fails the borrow checker:

```rust
// DOES NOT COMPILE:
let env = coutputs.overload_resolution_ctx.get(&PtrKey(ctx_id)).unwrap();
self.resolve_with_env(coutputs, *env, ...);  // &mut coutputs rejected; env borrows from it
```

**Invariant: all side-table values are pointer-sized `Copy` types (`&'s T`, `&'e T`, `&'t T` refs).** Copy out first:

```rust
let env: &'e IEnvironmentT<'s, 'e, 't> = *coutputs.overload_resolution_ctx.get(&PtrKey(ctx_id)).unwrap();
// env is now an independent ref; coutputs is no longer borrowed.
self.resolve_with_env(coutputs, env, ...);  // OK
```

If any side-table value were an owned struct or `Vec`, this pattern would fail. Enforce at review time: side-table values are always `&'_ Something` or equivalent `Copy`.

### 4.4 Deferred Actions

Scala uses closures in `LinkedHashMap` for deferred evaluation. Rust avoids `Box<dyn FnOnce>` by using a structured enum:

```rust
pub enum DeferredActionT<'s, 'e, 't> {
    EvaluateFunctionBody {
        prototype: &'t PrototypeT<'t>,
        function_env: &'e FunctionEnvironmentT<'s, 'e, 't>,
        origin: &'s FunctionA<'s>,
    },
    EvaluateFunction {
        name: &'t IdT<'t>,
        calling_env: &'e IEnvironmentT<'s, 'e, 't>,
        origin: &'s FunctionA<'s>,
        template_args: &'t [&'t ITemplataT<'t>],
    },
    // Add variants for additional deferred work kinds as needed.
}
```

Drain loop matches on the variant:

```rust
while let Some(action) = coutputs.deferred_actions.pop_front() {
    match action {
        DeferredActionT::EvaluateFunctionBody { prototype, function_env, origin } => {
            self.evaluate_function_body(coutputs, prototype, function_env, origin);
            coutputs.finished_deferred_function_body_compiles.insert(PtrKey(prototype));
        }
        DeferredActionT::EvaluateFunction { name, calling_env, origin, template_args } => {
            self.evaluate_function(coutputs, name, calling_env, origin, template_args);
            coutputs.finished_deferred_function_compiles.insert(PtrKey(name));
        }
    }
}
```

No `Box`, no `dyn`, no hidden captures. Every input is spelled out in the variant; debugging is easier; closures' lifetime-capture subtleties never arise.

### 4.5 Speculative Writes Are Idempotent

During overload resolution, `attempt_candidate_banner` writes to `coutputs` (e.g., `add_instantiation_bounds`) even for candidates that may later be rejected. This is safe because all writes are **idempotent** — re-writing the same value asserts equality (`assert_eq!(existing, new)`). No rollback mechanism is needed.

---

## Part 5: OverloadSet

### 5.1 Transient Type, Vestigial In Output

Per Agent F's investigation:

- **OverloadSet resolution always completes during the typing pass.** No unresolved OverloadSet ever flows into the instantiator.
- **But** an `OverloadSet`-kinded `ReinterpretTE(VoidLiteralTE, ...)` survives in `FunctionDefinitionT.body` as an inert type tag on argument expressions (the instantiator elides these, using `InstantiationBoundArgumentsT.runeToFunctionBoundArg` to find the real prototype).

So `OverloadSetT` must be a valid `KindT<'t>` variant in output, but its content is consumed only during the typing pass.

### 5.2 `OverloadSetT<'t>` — Name + Context ID

```rust
pub struct OverloadSetT<'t> {
    pub name: &'t IImpreciseNameT<'t>,  // re-interned from 's
    pub ctx_id: &'t OverloadResolutionCtxIdT<'t>,
}

pub struct OverloadResolutionCtxIdT<'t> {
    pub disambiguator: u64,  // unique per construction
    pub _marker: PhantomData<&'t ()>,
}
```

Each `OverloadSetT` carries a unique `ctx_id` allocated fresh at construction. During the pass, `CompilerOutputs.overload_resolution_ctx` maps `ctx_id → &'e IEnvironmentT<'s, 'e, 't>`, giving the overload resolver access to the env.

### 5.3 Construction

```rust
impl<'s, 'ctx, 'e, 't> Compiler<'s, 'ctx, 'e, 't> {
    pub fn make_overload_set_kind(
        &self,
        coutputs: &mut CompilerOutputs<'s, 'e, 't>,
        env: &'e IEnvironmentT<'s, 'e, 't>,
        name_s: &'s IImpreciseNameS<'s>,
    ) -> &'t KindT<'t> {
        let name_t = self.typing_interner.intern_imprecise_name(/* re-intern name_s into 't */);
        let ctx_id = self.typing_interner.alloc_fresh_overload_ctx();
        coutputs.overload_resolution_ctx.insert(PtrKey(ctx_id), env);
        self.typing_interner.intern_kind(KindValT::OverloadSet(OverloadSetT { name: name_t, ctx_id }))
    }
}
```

### 5.4 Why Filtered Candidates Don't Work

Earlier consideration: bake the candidate list into `OverloadSetT` at construction time. Agent G confirmed this is insufficient — the resolver uses the env for **5+ purposes beyond "find candidates"**:

1. `RuneParentEnvLookupSR` resolution
2. Rune-type solver context
3. `InferCompiler.solveForResolving` (templata lookup, isDescendant, placeholder root-env checks)
4. `isTypeConvertible`
5. Generic function evaluation (CSSNCE — "callee needs to see functions declared here")
6. Nested-OverloadSet recursion

Filtered candidates would need to re-resolve against these at a later point; the env itself is the minimum sufficient context. Hence ctx_id + side table.

### 5.5 Overload Resolution Threads `&CompilerOutputs`

Scala's `getCandidateBannersInner` reads `overloadSet.env` directly. In Rust it must thread `&CompilerOutputs` (or more generally, `&self` of the god struct + `&mut coutputs`) so that the ctx_id can resolve to an env. This changes the Scala signature, but it's consistent with the god-struct pattern where every compilation method takes `&mut coutputs`.

### 5.6 Post-Pass Behavior

After the typing pass completes:
- `CompilerOutputs` drops, including `overload_resolution_ctx`.
- Any `OverloadSetT` in `HinputsT` has a `ctx_id` that no longer resolves.
- Nothing downstream dereferences `ctx_id` — the instantiator uses `InstantiationBoundArgumentsT` instead.

---

## Part 6: Type System Types

### 6.1 IDEPFL Dual-Enum Pattern

All interned typing types use the dual-enum pattern: a **reference enum** (canonical, `&'t` refs) and a **value enum** (transient, for HashMap lookup). Scout/postparser re-interning produces `IImpreciseNameT<'t>`, `StrI<'t>`, `RangeT<'t>`, etc.

Interned type families:
- `INameT<'t>` / `INameValT<'t>` (~60 variants)
- `IdT<'t, T>` / `IdValT<'t, T>` (generic, see §6.3)
- `CoordT<'t>` — **inline Copy, not interned** (see §6.4)
- `KindT<'t>` / `KindValT<'t>`
- `ITemplataT<'t>` / `ITemplataValT<'t>`
- `PrototypeT<'t>` / `PrototypeValT<'t>`
- `SignatureT<'t>` / `SignatureValT<'t>`
- `StrI<'t>`, `IImpreciseNameT<'t>`, `RangeT<'t>`, `CodeLocationT<'t>`, `PackageCoordinateT<'t>`, `FileCoordinateT<'t>`

### 6.2 INameT Hierarchy

~60 concrete name types, organized into ~14 sub-trait enums. Shared DAG variants (types that appear under multiple sub-traits) get variants in each relevant sub-enum. `From` for narrow→wide, `TryFrom` for wide→narrow.

New name variants (not in v1) needed for the env-arena design:
- **(optional)** `NodeNameT { function_id, life }` if we later want env-id uniqueness. Current design does not require it.

### 6.3 `IdT<'t, T>` — Generic, Interned

```rust
pub struct IdT<'t, T: Into<&'t INameT<'t>> + Copy> {
    pub package_coord: &'t PackageCoordinateT<'t>,
    pub init_steps: &'t [&'t INameT<'t>],
    pub local_name: T,
}
```

Generic parameter preserves leaf-kind info at the type level. Cast methods widen/narrow:

```rust
impl<'t, T: Into<&'t INameT<'t>> + Copy> IdT<'t, T> {
    pub fn widen(self) -> IdT<'t, &'t INameT<'t>> { ... }
    pub fn widen_to<U: Into<&'t INameT<'t>> + Copy>(self) -> IdT<'t, U>
    where T: Into<U> { ... }
}

impl<'t> IdT<'t, &'t INameT<'t>> {
    pub fn try_narrow<U: Copy>(self) -> Option<IdT<'t, U>>
    where &'t INameT<'t>: TryInto<U> { ... }
}
```

Rust can't replicate Scala's covariance (`IdT[+T <: INameT]`); explicit casts are required. Interning uses a single HashMap keyed by `IdValT<'t, &'t INameT<'t>>` (widest form); narrowing happens on the way out.

### 6.4 `CoordT<'t>` — Inline Copy

`CoordT` is small (3 fields, all Copy or pointer-sized). We make it `Copy + Clone` and pass by value rather than interning:

```rust
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct CoordT<'t> {
    pub ownership: OwnershipT,
    pub region: RegionT,
    pub kind: &'t KindT<'t>,  // interned
}
```

Equality uses structural eq (ownership + region + ptr-eq on kind). Hash combines all three fields. Appears by value in every expression node — no arena allocation per coord, 24 bytes inline.

### 6.5 `KindT<'t>` — Interned

```rust
pub enum KindT<'t> {
    Never,
    Void,
    Int(IntT),  // small, Copy
    Bool,
    Str,
    Float,
    Struct(StructTT<'t>),
    Interface(InterfaceTT<'t>),
    StaticSizedArray(StaticSizedArrayTT<'t>),
    RuntimeSizedArray(RuntimeSizedArrayTT<'t>),
    KindPlaceholder(KindPlaceholderT<'t>),
    OverloadSet(&'t OverloadSetT<'t>),  // see §5
}
```

All variants interned. Pointer-eq on `&'t KindT` is identity.

### 6.6 `ITemplataT<'t>` — Type Parameter Erased, Split Into Leaf/Heavy

Scala's `ITemplataT[+T <: ITemplataType]` becomes a plain enum with no type parameter (type info preserved in a runtime `tyype` field on `PlaceholderTemplataT`):

```rust
pub enum ITemplataT<'t> {
    // Leaf templatas — appear freely in template arg slots in output
    Coord(&'t CoordTemplataT<'t>),
    Kind(&'t KindTemplataT<'t>),
    Placeholder(&'t PlaceholderTemplataT<'t>),
    Mutability(MutabilityTemplataT),
    Variability(VariabilityTemplataT),
    Ownership(OwnershipTemplataT),
    Integer(i64),
    Boolean(bool),
    String(&'t StrI<'t>),
    Prototype(&'t PrototypeTemplataT<'t>),
    Isa(&'t IsaTemplataT<'t>),
    CoordList(&'t CoordListTemplataT<'t>),
    RuntimeSizedArrayTemplate(RuntimeSizedArrayTemplateTemplataT),
    StaticSizedArrayTemplate(StaticSizedArrayTemplateTemplataT),
    
    // Heavy templatas — appear in envs and 3 specific named output slots
    Function(&'t FunctionTemplataT<'t>),
    StructDefinition(&'t StructDefinitionTemplataT<'t>),
    InterfaceDefinition(&'t InterfaceDefinitionTemplataT<'t>),
    ImplDefinition(&'t ImplDefinitionTemplataT<'t>),
    ExternFunction(&'t ExternFunctionTemplataT<'t>),
}
```

Heavy templatas hold IDs, not scout refs:

```rust
pub struct FunctionTemplataT<'t> {
    pub outer_env_id: &'t IdT<'t>,   // look up env in coutputs.origin_env
    pub origin_id: &'t IdT<'t>,      // look up FunctionA in coutputs.origin_function_a
    // Any summary data needed without a side-table lookup is copied inline:
    pub name: &'t IFunctionDeclarationNameT<'t>,
}

pub struct StructDefinitionTemplataT<'t> {
    pub declaring_env_id: &'t IdT<'t>,
    pub origin_id: &'t IdT<'t>,  // look up StructA
    pub name: &'t IStructDeclarationNameT<'t>,
}

pub struct InterfaceDefinitionTemplataT<'t> { /* similar */ }
pub struct ImplDefinitionTemplataT<'t> { /* similar */ }
pub struct ExternFunctionTemplataT<'t> { /* holds an &'t FunctionHeaderT */ }
```

Constraint (by convention, enforced at construction sites):
- **Template-arg slots** (`IdT.init_steps`, `IInstantiationNameT.templateArgs`, etc.) hold only leaf templata variants.
- **Heavy templatas** appear only in: (a) envs' `TemplatasStore`, (b) `ImplT.templata`, (c) `FunctionHeaderT.maybe_origin_function_templata`, (d) `FunctionBannerT.origin_function_templata`.

### 6.7 Mutual Recursion

Types form a mutually recursive graph (`CoordT → KindT → StructTT → IdT → INameT → ITemplataT → CoordT`). Compiles because every link is a `&'t` ref — pointer-sized, finite. No `Box`/`Vec` needed; arena references provide the indirection.

---

## Part 7: Expression AST

### 7.1 Three Enums

```rust
pub enum ReferenceExpressionTE<'t> {
    LetNormal(LetNormalTE<'t>),
    If(IfTE<'t>),
    While(WhileTE<'t>),
    FunctionCall(FunctionCallTE<'t>),
    Consecutor(ConsecutorTE<'t>),
    Construct(ConstructTE<'t>),
    Reinterpret(ReinterpretTE<'t>),  // includes the OverloadSet-tag case
    // ... ~30 more variants
}

pub enum AddressExpressionTE<'t> {
    LocalLookup(LocalLookupTE<'t>),
    ReferenceMemberLookup(ReferenceMemberLookupTE<'t>),
    // ... ~4 more
}

pub enum ExpressionTE<'t> {
    Reference(&'t ReferenceExpressionTE<'t>),
    Address(&'t AddressExpressionTE<'t>),
}
```

Used when a slot can hold either (e.g. `ConstructTE.args` because closures can have addressable members). All other slots are specifically `&'t ReferenceExpressionTE` or `&'t AddressExpressionTE`.

### 7.2 Arena-Allocated, Not Interned

Expression nodes allocate into `'t` but don't dedupe (no HashMap lookup on construction). Each compiled expression is constructed once. Sub-expression fields are `&'t` refs; collections are arena slices.

### 7.3 Pure `<'t>`, Always

Every TE field is pure `'t`. No `'s`/`'e` leak. The OverloadSet case is handled by `OverloadSetT<'t>` (§5) carrying a `ctx_id` — no direct env reference in the TE.

### 7.4 Visitor/Collector Pattern

Following the established pattern from parsing and postparsing: a `NodeRefT<'t>` enum with one variant per AST node type, `visit_*` functions per node, entry points for traversal, and `collect_where_tnodes!`/`collect_only_tnodes!` macros.

---

## Part 8: Error Handling

### 8.1 `Result<T, CompileErrorT>` With `?`

Scala's `throw CompileErrorExceptionT(...)` becomes `return Err(CompileErrorT::...)` with `?` propagation. Single catch boundary at the top-level `Compiler::compile()`.

### 8.2 Panic For Unimplemented

Unimplemented branches use `panic!()` with unique identifying messages. Functions that are fully-stub during incremental migration are acceptable.

---

## Part 9: Keywords

### 9.1 `Keywords<'t>`

`Keywords` holds interned strings used throughout the pass. Since scout-lifetime `StrI<'s>` can't appear in `'t` output, the typing pass uses `Keywords<'t>` with strings re-interned from whichever source. Created once at pass start (or provided by caller).

If the instantiator needs Keywords with its own arena lifetime, it creates a fresh `Keywords<'inst>`. Keywords is cheap to re-create.

---

## Part 10: Driving The Pass

### 10.1 Top-Level Entry

```rust
pub fn run_typing_pass<'p, 's, 't>(
    parse_arena: &ParseArena<'p>,
    scout_arena: &ScoutArena<'s>,
    typing_interner: &'t TypingInterner<'t>,
    keywords: &Keywords<'t>,
    opts: &TypingPassOptions,
    program_a: &'s ProgramA<'s>,
) -> Result<HinputsT<'t>, CompileErrorT<'t>> {
    let env_arena = EnvArena::new();  // 'e
    let compiler = Compiler::new(
        parse_arena, scout_arena, &env_arena, typing_interner, keywords, opts,
    );
    
    let mut coutputs = CompilerOutputs::new();
    compiler.compile_program(&mut coutputs, program_a)?;
    
    // Drain deferred actions to completion.
    compiler.drain_all_deferred(&mut coutputs);
    
    // Extract pure-'t output.
    let hinputs = HinputsT {
        function_definitions: typing_interner.alloc_slice_iter(
            coutputs.signature_to_function.into_values()
        ),
        struct_definitions: typing_interner.alloc_slice_iter(
            coutputs.struct_template_name_to_definition.into_values()
        ),
        interface_definitions: ...,
        edges: ...,
        kind_exports: typing_interner.alloc_slice_from_vec(coutputs.kind_exports),
        function_exports: typing_interner.alloc_slice_from_vec(coutputs.function_exports),
        // ...
    };
    
    Ok(hinputs)
    // coutputs drops here → side tables die → &'s/&'e refs released
    // env_arena drops here → 'e done
    // caller drops scout_arena, parse_arena → 's, 'p done
    // typing_interner outlives this function; HinputsT<'t> flows downstream
}
```

No explicit `finalize()` method. The extraction is just the natural end of the function; `coutputs` drop happens implicitly at scope exit.

### 10.2 Instantiator Handoff

The instantiator receives `HinputsT<'t>` plus a reference to the typing interner (for further lookups if needed). It does not receive the scout arena, env arena, parser arena, or CompilerOutputs — all are dead. It works purely from `'t` data.

---

## Part 11: Invariants Summary

For quick review during implementation:

1. **No `'t`-allocated struct holds `&'s`, `&'e`, or `&'p` refs.** If you see a field with an `'s` lifetime inside a `'t`-lifetime struct, that's a bug.
2. **All side-table values are pointer-sized `Copy` refs** (`&'s T`, `&'e T`, `&'t T`). No owned structs or `Vec`s as side-table values.
3. **All HashMap keys on interned refs use `PtrKey<'t, T>`**, never raw `&T` with derived `Hash`/`Eq`.
4. **Arena types never contain `Vec`, `HashMap`, `String`, `Rc`, `Box`.** Use arena slices and sorted Vec-pair maps. (Matches existing AASSNCMCX shield.)
5. **`'e` arena is global to `Compiler`**, not per-denizen. Env construction sites must have arena access.
6. **Re-intern scout data (`StrI`, names, ranges, coords) into `'t` at the `'s → 't` boundary.** Never carry `&'s` data into output.
7. **Side-table access: always copy out the Copy ref before continuing to mutate `coutputs`.**
8. **`'t` is lifetime-independent of `'s`, `'e`, `'p`.** No `'t: 's` bounds declared.
9. **Heavy templatas hold IDs, not scout refs.** Lookup via `CompilerOutputs` side tables during the pass.
10. **Overload resolution always completes during the typing pass.** No unresolved overloads reach the instantiator.
11. **Env equality/hashing never used.** `CompilerOutputs` never keys envs by their `.id`; keys are function/type template ids.

---

## Part 12: Slab-Ordered Migration Plan

Implementation proceeds bottom-up through dependency slabs. Each slab produces a compiling sub-layer.

1. **Slab 0**: Arena substrate — `TypingInterner<'t>`, `EnvArena<'e>`, new lifetime conventions.
2. **Slab 1**: Leaf types — `OwnershipT`, `MutabilityT`, `VariabilityT`, `LocationT`, `RegionT`, primitive `KindT` payloads, leaf templata types.
3. **Slab 2**: Re-intern boundary — `StrI<'t>`, `PackageCoordinateT<'t>`, `FileCoordinateT<'t>`, `RangeT<'t>`, `CodeLocationT<'t>`, `IImpreciseNameT<'t>`.
4. **Slab 3**: Name hierarchy — all ~60 `INameT` variants, `IdT<'t, T>` with casts, sub-enums, `INameValT` companions.
5. **Slab 4**: Kind/Coord/Templata trio — non-primitive `KindT` variants, `CoordT<'t>` inline, `ITemplataT<'t>` (leaf + heavy), `PrototypeT`, `SignatureT`, `OverloadSetT`, `OverloadResolutionCtxIdT`.
6. **Slab 5**: Environments — `IEnvironmentT<'s, 'e, 't>` with 9 variants, `TemplatasStoreT<'t>`, builder types, env arena plumbing.
7. **Slab 6**: Expression AST — 3 enums, arena-allocated, `NodeRefT` visitor.
8. **Slab 7**: `CompilerOutputs<'s, 'e, 't>` — all ~25 fields, side tables, deferred-action enum.
9. **Slab 8**: Compiler god struct shell — fields, constructor, top-level driver, `run_typing_pass` entry.
10. **Slab 9**: Function signatures across sub-compilers — file-by-file stub signatures matching Scala, bodies stay `panic!()`.
11. **Slab 10+**: Implementation of individual compilation methods, driven by test pass-rates.

After Slab 9, the project should build clean with hundreds of `panic!()`s awaiting real implementations. Subsequent slabs implement those.

---

## Part 13: Open Questions / Future Work

- **Keyword sharing across passes**: if the instantiator can share a `Keywords<'t>` with the typing pass, one arena allocation is avoided. Not critical.
- **Arena-backed HashMap**: at very large program scales, even `CompilerOutputs`'s heap HashMaps could become a bottleneck. `docs/reasoning/arena-deterministic-maps.md` discusses `ArenaIndexMap`. Future optimization if needed.
- **Lifetime bound `'t: 'e`**: may or may not be needed explicitly depending on rustc's inference. Safe to add. Harmless either way.
- **Parallelization**: the design is single-threaded (`!Sync` arenas, stack `CompilerOutputs`). Parallelizing per-function compilation is a future topic, not addressed here.
- **Source 4 (GeneralEnvironmentT id reuse)**: Scala has intentional id collisions at EdgeCompiler.scala:468 and InferCompiler.scala:477, 496. Since we don't require env-id uniqueness, these translate directly. If we ever need unique env ids, 3 sites need refactoring (see investigation notes).
