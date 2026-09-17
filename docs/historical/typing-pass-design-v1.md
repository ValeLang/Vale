# DO NOT FOLLOW — Historical, Obsolete (v1)

> **This document is historical and obsolete.** It was the first-cut typing-pass design (the `'a` interner arena + `'t` typing arena, two-lifetime model) and was superseded by v2 (Apr 14, 2026), then by `typing-pass-migration-setup.md` (Apr 20, 2026), and finally by the current authoritative design in **`TL-HANDOFF.md` at the repository root**.
>
> Its arena model, lifetime conventions, type families, and slab plan are all wrong relative to the shipping code. Do not consult this doc for current architecture.
>
> Preserved only for audit-trail of how the design evolved.

---

# Typing Pass Migration Design (v1, obsolete)

This document describes the architectural decisions for migrating `src/typing/` from Scala to Rust. It was produced by analyzing the full Scala codebase (in block comments) and identifying patterns that need special treatment in Rust.

---

## Part 1: Arena and Lifetime Model

### 1.1 Two Arenas, Two Lifetimes

The typing pass uses two arenas:

- **`'a` — Interner arena (existing, unchanged)**: Holds cross-pass data: `StrI`, `PackageCoordinate`, `FileCoordinate`, postparsing names (`INameS`, `IRuneS`), postparsing AST nodes (`FunctionA`, `StructA`, etc.). Longest-lived. Managed by the existing `Interner<'a>`.

- **`'t` — Typing pass arena (new)**: Holds everything produced by the typing pass — both interned type-system values and output AST nodes. Managed by a new `TypingInterner<'a, 't>`. Dies when the typing pass output is no longer needed.

The relationship: `'a` outlives `'t`. Typing-pass types hold `&'a` references to interner data and `&'t` references to other typing-pass data.

Previous passes follow the same pattern: `'p` for the parser arena, `'s` for the postparser arena. The typing pass adds `'t`.

### 1.2 `TypingInterner<'a, 't>`

A new struct that owns the `'t` arena and provides interning (deduplication) for typing-pass types. Structurally identical to the existing `Interner<'a>` but scoped to the typing pass.

```rust
struct TypingInterner<'a, 't> {
    arena: &'t bumpalo::Bump,

    // Interning maps: owned Val key → canonical &'t ref
    coord_map: RefCell<HashMap<CoordValT<'a, 't>, &'t CoordT<'a, 't>>>,
    kind_map: RefCell<HashMap<KindValT<'a, 't>, &'t KindT<'a, 't>>>,
    name_map: RefCell<HashMap<INameValT<'a, 't>, &'t INameT<'a, 't>>>,
    templata_map: RefCell<HashMap<ITemplataValT<'a, 't>, &'t ITemplataT<'a, 't>>>,
    id_map: RefCell<HashMap<IdValT<'a, 't>, &'t IdT<'a, 't>>>,
    prototype_map: RefCell<HashMap<PrototypeValT<'a, 't>, &'t PrototypeT<'a, 't>>>,
    // Sub-enum maps as needed (IFunctionNameT, IStructNameT, etc.)
}
```

Uses `RefCell` for interior mutability (same pattern as the existing `Interner`). Interning methods take `&self` and mutate the internal maps.

The interning flow follows IDEPFL:
1. Build an owned Val with all data inline (children already canonical `&'t` refs)
2. Look it up in the HashMap — if found, return existing `&'t` ref
3. If new, allocate into the `'t` arena via `arena.alloc(...)`, store the mapping, return `&'t` ref

### 1.3 What Goes In Each Arena

**`'a` interner arena (existing, unchanged):**
- `StrI<'a>` — interned strings
- `PackageCoordinate<'a>`, `FileCoordinate<'a>` — source locations
- `INameS<'a>`, `IRuneS<'a>`, `IImpreciseNameS<'a>` — postparsing names
- `FunctionA<'a, 's>`, `StructA<'a, 's>`, `InterfaceA<'a, 's>`, `ImplA<'a, 's>` — higher-typing output (referenced by typing-pass environments)

**`'t` typing arena (new) — interned types (deduplicated via `TypingInterner`):**
- `INameT<'a, 't>` hierarchy (~60 concrete types)
- `IdT<'a, 't>` — package coord + name path + local name
- `CoordT<'a, 't>` — ownership + region + kind
- `KindT<'a, 't>` — all variants (primitives and complex)
- `ITemplataT<'a, 't>` — template argument values
- `PrototypeT<'a, 't>` — function prototype (id + return type)
- `SignatureT<'a, 't>` — function signature

**`'t` typing arena (new) — output AST nodes (allocated but not deduplicated):**
- `FunctionDefinitionT<'a, 't>` — compiled function with header + body
- `FunctionHeaderT<'a, 't>` — function signature + attributes
- `StructDefinitionT<'a, 't>` — compiled struct
- `InterfaceDefinitionT<'a, 't>` — compiled interface
- `ImplT<'a, 't>` — compiled impl
- `EdgeT<'a, 't>`, `OverrideT<'a, 't>` — interface dispatch tables
- `ParameterT<'a, 't>` — function parameters
- `ReferenceExpressionTE<'a, 't>` — reference expression nodes (~38 variants)
- `AddressExpressionTE<'a, 't>` — address expression nodes (~6 variants)
- `ILocalVariableT<'a, 't>` — local variable declarations
- `InstantiationBoundArgumentsT<'a, 't>` — bound arguments per instantiation
- `HinputsT<'a, 't>` — the final output of the typing pass

**Neither arena (heap or stack):**
- `Rc<IEnvironmentT<'a, 's, 't>>` — environments (reference-counted, build-then-freeze)
- `CompilerOutputs<'a, 's, 't>` — mutable accumulator (stack-owned)
- `NodeEnvironmentBox`, `FunctionEnvironmentBoxT` — mutable wrappers (stack-local)
- `GlobalEnvironment` — heap-allocated, holds macros and top-level stores
- `TemplatasStore` — owned by environments (inside Rc)
- Deferred compilation closures — `Box<dyn FnOnce(...)>`, heap-allocated

---

## Part 2: The God Struct

### 2.1 Architecture

All ~15 Scala sub-compiler classes (`FunctionCompiler`, `ExpressionCompiler`, `StructCompiler`, `ImplCompiler`, `OverloadResolver`, `InferCompiler`, `TemplataCompiler`, `ConvertHelper`, `DestructorCompiler`, `VirtualCompiler`, `SequenceCompiler`, `ArrayCompiler`, `EdgeCompiler`, `BlockCompiler`, `PatternCompiler`, `CallCompiler`, `LocalHelper`) are collapsed into methods on a single struct.

```rust
struct Compiler<'a, 's, 'ctx, 't> {
    interner: &'ctx Interner<'a>,
    typing_interner: &'ctx TypingInterner<'a, 't>,
    keywords: &'ctx Keywords<'a>,
    opts: &'ctx TypingPassOptions,
    global_env: &'ctx GlobalEnvironment<'a, 's, 't>,
    name_translator: NameTranslator<'a>,
    // ... other immutable config
}
```

### 2.2 Why This Works: `&self` + `&mut coutputs`

The god struct holds only **immutable configuration**. All mutable state is passed as separate `&mut` parameters:

```rust
impl<'a, 's, 'ctx, 't> Compiler<'a, 's, 'ctx, 't> {
    fn evaluate_expression(
        &self,
        coutputs: &mut CompilerOutputs<'a, 's, 't>,
        nenv: &mut NodeEnvironmentBox<'a, 's, 't>,
        expr: &IExpressionSE<'a, 's>,
    ) -> Result<ReferenceExpressionTE<'a, 't>, CompileErrorT<'a, 't>> {
        // ...
    }
}
```

This avoids the re-entrancy problem. The typing pass has deep mutual recursion:

```
evaluate_expression → evaluate_prefix_call → find_function → evaluate_function_for_header
    → evaluate_block_statements → evaluate_expression  (re-entrant!)
```

With `&mut self`, this chain would require two simultaneous mutable borrows. But with `&self` (immutable config) plus `&mut coutputs` (passed through), re-entrancy on `&self` is fine — multiple immutable borrows are allowed. The `&mut coutputs` is re-borrowed at each call level, which Rust handles naturally.

### 2.3 Macros

In Scala, macro objects (`AsSubtypeMacro`, `LockWeakMacro`, `StructDropMacro`, etc.) store references to sub-compilers (`expressionCompiler`, `destructorCompiler`, etc.). In the god struct approach, macros **cannot** store references to the god struct (that would be self-referential).

Instead, macros receive the god struct at call time:

```rust
trait IFunctionGenerator<'a, 's, 't> {
    fn generate(
        &self,
        compiler: &Compiler<'a, 's, '_, 't>,  // receives god struct as parameter
        coutputs: &mut CompilerOutputs<'a, 's, 't>,
        env: &FunctionEnvironmentT<'a, 's, 't>,
        // ...
    ) -> FunctionHeaderT<'a, 't>;
}
```

Macros are stored in `GlobalEnvironment` and are stateless — they hold only configuration data (like their name), not compiler references. All compilation logic goes through the `compiler` parameter they receive.

### 2.4 Delegate Traits Eliminated

In Scala, the sub-compilers were wired via anonymous delegate traits (`IExpressionCompilerDelegate`, `IFunctionCompilerDelegate`, `IBlockCompilerDelegate`, `IInfererDelegate`, etc.) that forwarded calls between sub-compilers. With the god struct, these are unnecessary — every method is on the same struct and can call any other method directly via `self.method_name(...)`.

---

## Part 3: `CompilerOutputs`

### 3.1 Structure

`CompilerOutputs` is a mutable struct with ~25 `HashMap`/`HashSet`/`VecDeque` fields. It accumulates all compiled definitions, environment mappings, instantiation bounds, and deferred evaluation queues.

```rust
pub struct CompilerOutputs<'a, 's, 't> {
    // Function registries
    return_types_by_signature: HashMap<&'t SignatureT<'a, 't>, &'t CoordT<'a, 't>>,
    signature_to_function: HashMap<&'t SignatureT<'a, 't>, &'t FunctionDefinitionT<'a, 't>>,
    env_by_function_signature: HashMap<&'t SignatureT<'a, 't>, Rc<IEnvironmentT<'a, 's, 't>>>,

    // Declaration tracking (prevents infinite recursion)
    function_declared_names: HashMap<&'t IdT<'a, 't>, RangeS<'a>>,
    type_declared_names: HashSet<&'t IdT<'a, 't>>,

    // Environment storage
    function_name_to_outer_env: HashMap<&'t IdT<'a, 't>, Rc<IEnvironmentT<'a, 's, 't>>>,
    function_name_to_inner_env: HashMap<&'t IdT<'a, 't>, Rc<IEnvironmentT<'a, 's, 't>>>,
    type_name_to_outer_env: HashMap<&'t IdT<'a, 't>, Rc<IEnvironmentT<'a, 's, 't>>>,
    type_name_to_inner_env: HashMap<&'t IdT<'a, 't>, Rc<IEnvironmentT<'a, 's, 't>>>,

    // Type metadata
    type_name_to_mutability: HashMap<&'t IdT<'a, 't>, &'t ITemplataT<'a, 't>>,
    interface_name_to_sealed: HashMap<&'t IdT<'a, 't>, bool>,

    // Definitions
    struct_template_name_to_definition: HashMap<&'t IdT<'a, 't>, &'t StructDefinitionT<'a, 't>>,
    interface_template_name_to_definition: HashMap<&'t IdT<'a, 't>, &'t InterfaceDefinitionT<'a, 't>>,

    // Impls + reverse indexes
    all_impls: HashMap<&'t IdT<'a, 't>, &'t ImplT<'a, 't>>,
    sub_citizen_template_to_impls: HashMap<&'t IdT<'a, 't>, Vec<&'t ImplT<'a, 't>>>,
    super_interface_template_to_impls: HashMap<&'t IdT<'a, 't>, Vec<&'t ImplT<'a, 't>>>,

    // Exports/externs
    kind_exports: Vec<&'t KindExportT<'a, 't>>,
    function_exports: Vec<&'t FunctionExportT<'a, 't>>,
    kind_externs: Vec<&'t KindExternT<'a, 't>>,
    function_externs: Vec<&'t FunctionExternT<'a, 't>>,

    // Instantiation bounds
    instantiation_name_to_bounds: HashMap<&'t IdT<'a, 't>, &'t InstantiationBoundArgumentsT<'a, 't>>,

    // Deferred evaluation queues (see 3.2)
    deferred_function_body_compiles: VecDeque<DeferredEvaluatingFunctionBody<'a, 's, 't>>,
    finished_deferred_function_body_compiles: HashSet<&'t PrototypeT<'a, 't>>,
    deferred_function_compiles: VecDeque<DeferredEvaluatingFunction<'a, 's, 't>>,
    finished_deferred_function_compiles: HashSet<&'t IdT<'a, 't>>,
}
```

### 3.2 Deferred Evaluation Queues

Scala uses `LinkedHashMap` for FIFO-ordered deferred queues with O(1) key lookup. In Rust, we use `VecDeque` + `HashSet`:

```rust
struct DeferredEvaluatingFunctionBody<'a, 's, 't> {
    prototype: &'t PrototypeT<'a, 't>,
    call: Box<dyn FnOnce(&Compiler<'a, 's, '_, 't>, &mut CompilerOutputs<'a, 's, 't>) + 't>,
}
```

The drain loop takes-then-calls to avoid self-referential borrow:

```rust
while let Some(deferred) = coutputs.deferred_function_body_compiles.pop_front() {
    // Closure is now owned, removed from coutputs
    // Safe to pass &mut coutputs to the closure
    (deferred.call)(self, coutputs);
    coutputs.finished_deferred_function_body_compiles.insert(deferred.prototype);
}
```

The nested drain loop structure matches Scala: drain all `deferred_function_compiles` first (inner while), then process one `deferred_function_body_compiles` entry, then re-check (outer while). Deferred body compilation can enqueue more deferred function compilations, which is why the outer loop repeats.

### 3.3 Speculative Writes Are Safe

During overload resolution, `attemptCandidateBanner` writes to `coutputs` (e.g., `addInstantiationBounds`) even for candidates that may later be rejected. This is safe because all writes are **idempotent** — re-writing the same value asserts equality (`vassert(existing == instantiationBoundArgs)`). No rollback mechanism is needed.

### 3.4 HashMap Keys Are Interned Pointers

All `HashMap` keys in `CompilerOutputs` are `&'t` references to interned values (mostly `&'t IdT`). Since `IdT` is interned in the `TypingInterner`, pointer equality is identity equality. `Hash`/`Eq` impls on the reference type use pointer-based comparison, making lookups O(1) without structural traversal.

---

## Part 4: Type System Types

### 4.1 IDEPFL Dual-Enum Pattern

All interned typing-pass types use the IDEPFL dual-enum pattern (reference enum + value enum), interned into the `'t` arena via `TypingInterner`.

The interned type families:
- `INameT` / `INameValT` (~60 variants each)
- `IdT` / `IdValT`
- `CoordT` / `CoordValT`
- `KindT` / `KindValT`
- `ITemplataT` / `ITemplataValT`
- `PrototypeT` / `PrototypeValT`
- `SignatureT` / `SignatureValT`
- Sub-enums (`IFunctionNameT` / `IFunctionNameValT`, `IStructNameT` / `IStructNameValT`, etc.)

Val versions hold owned data for HashMap lookup. For fields that contain other interned types, the shallow pattern applies: the Val holds the already-canonical `&'t` reference. Children must always be interned before parents (bottom-up construction).

Types that are **not** interned in Scala (`CoordT`, `KindT`, `ITemplataT`) are still interned in Rust for consistency and to enable pointer-based identity throughout. Non-interned types in Val name variants (like `templateArgs` containing `ITemplataT` values) hold canonical `&'t` refs since `ITemplataT` is also interned.

### 4.2 The `INameT` Hierarchy

~60 concrete name types organized into ~14 sub-trait enums. Each level of the Scala sealed-trait hierarchy becomes its own Rust enum. Types appearing in multiple sub-traits (DAG pattern) get variants in each relevant sub-enum.

```rust
// Top-level enum
pub enum INameT<'a, 't> {
    FunctionName(&'t FunctionNameT<'a, 't>),
    StructTemplate(&'t StructTemplateNameT<'a>),
    KindPlaceholder(&'t KindPlaceholderNameT<'a, 't>),
    // ... ~57 more variants
}

// Sub-enum for function names
pub enum IFunctionNameT<'a, 't> {
    Function(&'t FunctionNameT<'a, 't>),
    Forwarder(&'t ForwarderFunctionNameT<'a, 't>),
    ExternFunction(&'t ExternFunctionNameT<'a, 't>),  // also in IFunctionTemplateNameT
    // ...
}

// Sub-enum for function template names
pub enum IFunctionTemplateNameT<'a, 't> {
    FunctionTemplate(&'t FunctionTemplateNameT<'a>),
    ExternFunction(&'t ExternFunctionNameT<'a, 't>),  // shared with IFunctionNameT
    Forwarder(&'t ForwarderFunctionTemplateNameT<'a, 't>),
    // ...
}

// From impls for narrow → wide conversion
impl<'a, 't> From<IFunctionNameT<'a, 't>> for IInstantiationNameT<'a, 't> { ... }
impl<'a, 't> From<IFunctionNameT<'a, 't>> for INameT<'a, 't> { ... }
// TryFrom for wide → narrow (fallible)
impl<'a, 't> TryFrom<INameT<'a, 't>> for IFunctionNameT<'a, 't> { ... }
```

### 4.3 `IdT<T>` — Generic, Interned

```rust
pub struct IdT<'a, 't, T> {
    pub package_coord: &'a PackageCoordinate<'a>,
    pub init_steps: &'t [&'t INameT<'a, 't>],
    pub local_name: T,
}
```

`IdT` is interned in the `TypingInterner`. The generic parameter `T` preserves type-level knowledge of what kind of name is at the leaf (e.g., `IdT<'a, 't, &'t IFunctionNameT<'a, 't>>`). The `init_steps` is an arena-allocated slice of interned name references. `Hash`/`Eq` use pointer-based comparison on all interned components.

### 4.4 `CoordT` and `KindT`

```rust
pub struct CoordT<'a, 't> {
    pub ownership: OwnershipT,
    pub region: RegionT,
    pub kind: &'t KindT<'a, 't>,
}

pub enum KindT<'a, 't> {
    Never(NeverT),
    Void(VoidT),
    Int(IntT),
    Bool(BoolT),
    Str(StrT),
    Float(FloatT),
    Struct(StructTT<'a, 't>),
    Interface(InterfaceTT<'a, 't>),
    StaticSizedArray(StaticSizedArrayTT<'a, 't>),
    RuntimeSizedArray(RuntimeSizedArrayTT<'a, 't>),
    KindPlaceholder(KindPlaceholderT<'a, 't>),
    OverloadSet(OverloadSetT<'a, 't>),
}
```

All `KindT` variants are interned (including primitives, for uniformity). `CoordT` is interned. Both use pointer-based identity via `ptr_eq`.

`OverloadSetT` holds `env: Rc<IEnvironmentT<'a, 's, 't>>` — a special case. Its Val version holds the `Rc` for structural hashing. Interning uses pointer-based comparison on the `Rc` (via `Rc::as_ptr`) plus structural comparison on the name.

### 4.5 `ITemplataT` — Type Parameter Erased

Scala's `ITemplataT[+T <: ITemplataType]` becomes a plain enum with no type parameter:

```rust
pub enum ITemplataT<'a, 't> {
    Coord(&'t CoordTemplataT<'a, 't>),
    Kind(&'t KindTemplataT<'a, 't>),
    Placeholder(&'t PlaceholderTemplataT<'a, 't>),
    Mutability(&'t MutabilityTemplataT),
    Variability(&'t VariabilityTemplataT),
    Ownership(&'t OwnershipTemplataT),
    Integer(&'t IntegerTemplataT),
    Boolean(&'t BooleanTemplataT),
    String(&'t StringTemplataT<'a>),
    Prototype(&'t PrototypeTemplataT<'a, 't>),
    Isa(&'t IsaTemplataT<'a, 't>),
    CoordList(&'t CoordListTemplataT<'a, 't>),
    Function(&'t FunctionTemplataT<'a, 's, 't>),
    StructDefinition(&'t StructDefinitionTemplataT<'a, 's, 't>),
    InterfaceDefinition(&'t InterfaceDefinitionTemplataT<'a, 's, 't>),
    ImplDefinition(&'t ImplDefinitionTemplataT<'a, 's, 't>),
    RuntimeSizedArrayTemplate(&'t RuntimeSizedArrayTemplateTemplataT),
    StaticSizedArrayTemplate(&'t StaticSizedArrayTemplateTemplataT),
    ExternFunction(&'t ExternFunctionTemplataT<'a, 't>),
}
```

The Scala type parameter was informational — `PlaceholderTemplataT` already stores a runtime `tyype: ITemplataType` field, and `expect_*` methods do runtime matching. After erasure, struct fields that were `ITemplataT[MutabilityTemplataType]` become `&'t ITemplataT<'a, 't>`, losing compile-time narrowing but retaining runtime checks.

`PrototypeTemplataT`'s own type parameter (`T <: IFunctionNameT`) is **orthogonal** to this erasure and can be decided independently.

### 4.6 Mutual Recursion

The type families are mutually recursive:

```
CoordT → KindT → StructTT → IdT → INameT → ITemplataT → CoordT
```

This compiles in Rust because every link in the chain goes through an `&'t` reference (pointer-sized, finite). No `Box` or `Vec` needed — the arena references provide the indirection.

`ForwarderFunctionNameT.inner: &'t IFunctionNameT` and `ForwarderFunctionTemplateNameT.inner: &'t IFunctionTemplateNameT` — same-type recursion resolved by the `&'t` reference, no `Box` needed.

---

## Part 5: Environments

### 5.1 `Rc<IEnvironmentT>` for Long-Lived Storage

Environments follow a strict **build-then-freeze** pattern: they are constructed mutably, then stored immutably and never modified after storage. The `Rc` wraps the **full** `IEnvironmentT` enum (not just `IInDenizenEnvironmentT`), because templata types use the broader `IEnvironmentT`:

- `FunctionTemplataT.outer_env: Rc<IEnvironmentT>`
- `StructDefinitionTemplataT.declaring_env: Rc<IEnvironmentT>`
- `InterfaceDefinitionTemplataT.declaring_env: Rc<IEnvironmentT>`
- `ImplDefinitionTemplataT.env: Rc<IEnvironmentT>`

`PackageEnvironmentT` is `IEnvironmentT` but not `IInDenizenEnvironmentT`, and top-level functions create `FunctionTemplataT(packageEnv, func)`. The `Rc` must encompass the full enum.

```rust
pub enum IEnvironmentT<'a, 's, 't> {
    Package(PackageEnvironmentT<'a, 's, 't>),
    Citizen(CitizenEnvironmentT<'a, 's, 't>),
    Function(FunctionEnvironmentT<'a, 's, 't>),
    Node(NodeEnvironmentT<'a, 's, 't>),
    BuildingWithClosureds(BuildingFunctionEnvironmentWithClosuredsT<'a, 's, 't>),
    BuildingWithClosuredsAndTemplateArgs(BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'a, 's, 't>),
    General(GeneralEnvironmentT<'a, 's, 't>),
    Export(ExportEnvironmentT<'a, 's, 't>),
    Extern(ExternEnvironmentT<'a, 's, 't>),
}
```

Parent chains use `Rc`: each environment holds `parent_env: Rc<IEnvironmentT>`. No circular references exist — parent chains are strictly tree-shaped (parent always created before child).

`entryToTemplata` creates `FunctionTemplataT` on every lookup with `Rc::clone(defining_env)` — cheap (reference count bump).

### 5.2 Mutable Environment Boxes (Stack-Local)

During body compilation, mutable wrappers provide imperative mutation via functional update (replace the inner value with a new copy):

```rust
struct NodeEnvironmentBox<'a, 's, 't> {
    inner: NodeEnvironmentT<'a, 's, 't>,  // owned, not Rc
}

impl NodeEnvironmentBox<'a, 's, 't> {
    fn add_variable(&mut self, new_var: ILocalVariableT<'a, 't>) {
        self.inner = self.inner.add_variable(new_var);
    }

    fn snapshot(&self) -> NodeEnvironmentT<'a, 's, 't> {
        self.inner.clone()
    }
}
```

Boxes are **never stored in `CompilerOutputs`** or templata values. They exist only as `&mut` parameters during expression compilation. The `snapshot()` method returns a clone that can be wrapped in `Rc` if needed for long-lived storage.

For `if`-expression compilation, two child `NodeEnvironmentBox`es are created from the parent's snapshot, used independently for the then/else branches, then their effects are merged back into the parent. No aliased `&mut` — each child is an independent owned value.

---

## Part 6: Expression AST

### 6.1 Three Enums

The expression AST uses three enums:

```rust
// ~38 variants — expressions that produce a reference
pub enum ReferenceExpressionTE<'a, 't> {
    LetNormal(LetNormalTE<'a, 't>),
    If(IfTE<'a, 't>),
    While(WhileTE<'a, 't>),
    FunctionCall(FunctionCallTE<'a, 't>),
    Consecutor(ConsecutorTE<'a, 't>),
    Construct(ConstructTE<'a, 't>),
    // ...
}

// ~6 variants — expressions that produce an address (lvalue)
pub enum AddressExpressionTE<'a, 't> {
    LocalLookup(LocalLookupTE<'a, 't>),
    ReferenceMemberLookup(ReferenceMemberLookupTE<'a, 't>),
    // ...
}

// Wrapper — only needed for ConstructTE.args which mixes both
pub enum ExpressionTE<'a, 't> {
    Reference(&'t ReferenceExpressionTE<'a, 't>),
    Address(&'t AddressExpressionTE<'a, 't>),
}
```

`ConstructTE` is the **only** expression node that holds the broad `ExpressionTE` type (because closures can have addressible members). All other nodes hold either `&'t ReferenceExpressionTE` or `&'t AddressExpressionTE` specifically.

### 6.2 Arena-Allocated, Not Interned

Expression nodes are allocated into the `'t` arena but **not** interned (no deduplication). They are built once per compilation and form the function body trees. Sub-expression fields are `&'t ReferenceExpressionTE` or `&'t AddressExpressionTE` — arena references, no `Box`.

Collection fields use arena slices:
- `ConsecutorTE.exprs: &'t [&'t ReferenceExpressionTE<'a, 't>]`
- `FunctionCallTE.args: &'t [&'t ReferenceExpressionTE<'a, 't>]`
- `ConstructTE.args: &'t [ExpressionTE<'a, 't>]`

### 6.3 Visitor/Collector Pattern

Following the established pattern from parsing and postparsing:

1. A `NodeRefT<'a, 't>` enum with one variant per AST node type
2. `visit_*` functions for each node type that recurse into all child fields
3. `collect_in_*` entry points for traversal from a program/function/expression
4. `collect_where_tnodes!` / `collect_only_tnodes!` macros

This is the same pattern used by `NodeRefP` (parsing) and `NodeRefS` (postparsing), defined in their respective `tests/traverse.rs` files.

---

## Part 7: Error Handling

### 7.1 `Result<T, CompileErrorT>` With `?`

Scala's `throw CompileErrorExceptionT(...)` becomes `return Err(CompileErrorT::...)` with `?` propagation throughout. The typing pass has a single catch boundary in Scala (`Compiler.evaluate()`); in Rust this becomes the `Result` return type of the top-level `evaluate()` method.

Many functions that didn't return `Result` in Scala will need to in Rust, because their indirect callees return `Result`. This is expected and fine (see XRRIF principle).

### 7.2 Panic for Unimplemented Code

All unimplemented branches use `panic!()` with unique identifying messages (see XTUCMP principle). Functions that are entirely stubs are acceptable during incremental migration.

---

## Part 8: Summary of Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Sub-compiler architecture | God struct with `&self` | Eliminates delegate traits; `&self` allows re-entrancy |
| Mutable state | `&mut CompilerOutputs` passed as parameter | Separates mutable state from immutable config |
| Typing pass arena | Separate `'t` arena, not the `'a` interner | Each pass owns its types with its own lifetime |
| Typing pass interning | `TypingInterner<'a, 't>` with own HashMaps | Same IDEPFL pattern, scoped to typing pass |
| Interned types | INameT, IdT, CoordT, KindT, ITemplataT, PrototypeT, SignatureT | All type-system values deduplicated for pointer identity |
| INameT hierarchy | Flat enums with sub-enums, From/TryFrom impls | Same pattern as postparsing, handles DAG via shared variants |
| ITemplataT type parameter | Erased — plain enum | Type parameter was informational; runtime `tyype` field preserved |
| IdT | Generic `IdT<T>`, interned | Preserves type-level leaf knowledge; pointer-based Hash/Eq |
| Environments | `Rc<IEnvironmentT>` (full enum) | Build-then-freeze; no circular refs; cheap clone |
| Environment boxes | Owned mutable values, functional update | Stack-local, never stored long-lived |
| Expression AST | 3 enums (Ref + Addr + wrapper), arena-allocated, not interned | Output nodes, built once |
| Expression sub-expressions | `&'t` arena references, arena slices | No Box, no Vec |
| Deferred queues | VecDeque + HashSet, take-then-call | Avoids self-referential borrow |
| Macros | Receive god struct at call time | No stored sub-compiler references |
| Visitor/Collector | NodeRefT enum + visit_* functions + macros | Same pattern as parsing/postparsing |
| Error handling | `Result<T, CompileErrorT>` with `?` | Replaces Scala exceptions |
