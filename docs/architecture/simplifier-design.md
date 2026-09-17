# Simplifying Pass (Hammer) Design

Architecture and design decisions for the simplifying pass. The Scala code called this pass "Hammer" — that's the verb (and the `H`-suffix convention) you'll see throughout. Sister doc to `typing-pass-design-v3.md` and `instantiator-design.md`.

The simplifying pass is **pass 6** in the Vale frontend pipeline (parse → postparse → higher-typing → typing → instantiate → **simplify** → emit). It consumes `HinputsI<'s, 'i>` (the monomorphic instantiated denizen graph from the instantiator) and produces `ProgramH<'s, 'h>` (the wire-format-ready "H-AST" in `src/final_ast/`). The Hammer reads `'i`-mode (`cI`-collapsed) instantiator IR and lowers everything — names, kinds, coords, prototypes, expressions, definitions — into backend-shaped equivalents.

A separate `VonHammer` pass walks the produced `ProgramH` and emits `IVonData` (a heap-owned JSON-shaped wire format consumed by the backend / on-disk format). `VonHammer` doesn't have its own struct — it lives as additional `impl Hammer` blocks per the god-struct collapse.

---

## Part 1: Arena and Lifetime Model

The approach inherits the typing-pass / instantiator philosophy: **scout names and packages carry through unchanged** (no re-interning `StrI<'s>` / `PackageCoordinate<'s>`), the instantiator output is read directly through `&'i` refs, and a dedicated `HammerArena<'h>` holds the H-side AST. The Hammer is the **last pass that uses `'i`** — the H-AST it produces holds no `'i` refs, only `'s` (scout strings/coords carried through) and `'h` (newly-interned hammer types).

**The trade.** Cost: hammer arena memory for the final AST (mostly bounded by the instantiator output's size). The H-AST is consumed by `vonify_program` (which produces an owned `IVonData` tree, freeing `'h` to drop once VON emission is done) and by the test VM (which holds `&'h ProgramH` for the duration of a test). Benefit: simplifying-pass code mirrors Scala line-for-line. No re-interned name plumbing.

### 1.1 Four Arenas

By the time Hammer runs, four arenas are live:

- **`'p` — Parser arena (`ParseArena<'p>`)**: read-only at this point.
- **`'s` — Scout arena (`ScoutArena<'s>`)**: holds `StrI<'s>`, `PackageCoordinate<'s>`, `INameS<'s>`, code locations. Carried through unchanged.
- **`'i` — Instantiating arena (`InstantiatingInterner<'s, 'i>`)**: holds the input `HinputsI<'s, 'i>` and every `I`-suffix type it transitively reaches. Read-only here.
- **`'h` — Hammer arena (`HammerInterner<'s, 'h>`)**: simplifying-pass output. Created before the pass starts; outlives the pass.

The typing arena (`'t`) is logically still alive but Hammer doesn't touch it — the instantiator already projected typing-pass data into `'i`-flavored shapes. From the Hammer's vantage there are effectively four arenas, and its job is `'i` → `'h` translation.

All four arenas use `bumpalo`. `HammerArena<'h>` is a thin newtype around `&'h Bump`. `HammerInterner<'s, 'h>` wraps the bump plus a `RefCell<Inner>` holding three `StdHashMap`s. H-side arena-allocated structs follow AASSNCMCX: no `Vec`/`HashMap`/`String` inside arena types — slices are `&'h [T]`, maps are `ArenaIndexMap<'h, K, V>`.

### 1.2 Lifetime Invariants

1. **`'s: 'i`** — inherited from the instantiator.
2. **`'i: 'h`** — H-side output references I-side input directly: `Hamuts` holds `HashMap<&'i StructIT<'s,'i,cI>, &'h StructHT<'s,'h>>` and similar shapes. The instantiator arena must outlive the hammer arena during translation. The bound is declared on every type that crosses the boundary (`Hamuts`, `Hammer`, `Locals`).
3. **`'s: 'h`** — composing 1 and 2. Explicit in the god struct's where-clause (`where 's: 'i, 's: 'h, 'i: 'h`).
4. **Region-mode resolved before Hammer.** The Hammer reads only `cI`-mode I-side input. The instantiator's worklist has driven all `sI → nI → cI` collapses before the I → H boundary. The H-side has no `R` region-mode generic at all.
5. **Arena borrow convention.** Hammer parameters take a short borrow lifetime (elided or named `'ctx`), never the arena's own lifetime. Write `&HammerInterner<'s, 'h>` or `&'ctx HammerInterner<'s, 'h>`, never `&'h HammerInterner<'s, 'h>`. `HammerInterner::alloc(&self, T) -> &'h T` returns arena-lifetimed data from a short `&self` borrow.
6. **Never `'static`.** Same NUSLX rule. `&'static T` has a different pointer than a structurally identical `&'h T`, breaking ptr-identity for `MustIntern`-sealed types.
7. **One new arena lifetime beyond the instantiator.** No per-package or per-function sub-arenas.

### 1.3 Arena Construction Order

```
fn top_level_driver() {
    // ... parse / scout / typing / instantiating produce HinputsI<'s, 'i> ...

    let hammer_bump = Bump::new();
    let hammer_interner = HammerInterner::new(&hammer_bump);

    let hammer = Hammer {
        interner: &hammer_interner,
        keywords: &keywords,
        scout_arena: &scout_arena,
        instantiating_interner: &instantiating_interner,
    };
    let program_h: &'h ProgramH<'s, 'h> = hammer.translate(&hinputs_i);

    // ... vonify_program(program_h) -> IVonData (heap, owned), or testvm reads program_h directly ...

    // hammer_bump drops: 'h dies
    // instantiating_bump drops: 'i dies
    // typing_bump drops: 't dies
    // scout_arena drops: 's dies
    // parse_arena drops: 'p dies
}
```

Rust's drop order (reverse of declaration) naturally enforces `'h < 'i < 't < 's < 'p`.

### 1.4 Where Each Type Lives

| Type | Lifetimes | Arena |
|---|---|---|
| `ProgramH` | `<'s, 'h>` | `'h`, allocated, holds a `PackageCoordinateMap` of `PackageH` by value |
| `PackageH` | `<'s, 'h>` | `'h`, holds `&'h [...]` slices and `&'h ArenaIndexMap`s |
| `FunctionH`, `StructDefinitionH`, `InterfaceDefinitionH`, `EdgeH` | `<'s, 'h>` | `'h`, allocated, not interned |
| `IdH`, `PrototypeH` | `<'s, 'h>` | `'h`, interned, `MustIntern` seal |
| `StructHT`, `InterfaceHT`, `StaticSizedArrayHT`, `RuntimeSizedArrayHT` | `<'s, 'h>` | `'h`, interned, `MustIntern` seal |
| `OpaqueHT` | `<'s, 'h>` | `'h`, allocated-but-not-deduped, held as `&'h OpaqueHT` |
| `KindHT` | `<'s, 'h>` | inline 16-byte Polyvalue wrapper, Copy |
| `ExpressionH`, `IExpressionH`, `ReferenceExpressionH`, `AddressExpressionH` | `<'s, 'h>` | inline Polyvalue wrapper, Copy; payload is `&'h XH` |
| ~50 expression payload structs (`StackifyH`, `CallH`, `IfH`, …) | `<'s, 'h>` | `'h`, allocated, not interned |
| `CoordH`, `OwnershipH`, `LocationH`, `Mutability`, `Variability` | inline Copy values | passed by value |
| `Hamuts` (the accumulator) | `<'s, 'i, 'h>` | stack, heap-backed `HashMap`s |
| `Hammer` (god struct) | `<'s, 'i, 'h, 'ctx>` | stack, immutable config only |
| `Locals` (per-function var tracker) | `<'s, 'i, 'h>` | stack |
| `HammerCompilation` | 6 lifetimes | stack driver wrapper |
| `IVonData` (wire-format output) | (no lifetime) | heap, owned `String`/`Vec` |

### 1.5 Type Inventory By Arena

Bucket-level summary. Per-type detail lives in §6 (H-types) and §3 (Hamuts).

- **`'i` instantiating arena (read-only inputs):** `HinputsI<'s, 'i>` plus everything it transitively reaches — `StructIT<'s,'i,cI>`, `InterfaceIT`, `PrototypeI`, `IdI`, `ITemplataI`, and the I-side expression AST. Never mutated.
- **`'h` hammer arena, interned:** `IdH`, `PrototypeH`, plus the 4 sealed kind payloads (`StructHT`, `InterfaceHT`, `StaticSizedArrayHT`, `RuntimeSizedArrayHT`). Each interned type carries `_must_intern: MustIntern` and has a transient `*ValH` mirror used as the HashMap query key.
- **`'h` hammer arena, allocated but not interned:** `ProgramH`, `PackageH`, definitions (`FunctionH`, `StructDefinitionH`, `InterfaceDefinitionH`, `EdgeH`, `StructMemberH`, `InterfaceMethodH`), the expression payload structs (one per `ExpressionH` variant), `StaticSizedArrayDefinitionHT`, `RuntimeSizedArrayDefinitionHT`, `OpaqueHT`, `HamutsFunctionExtern`, `HamutsKindExtern`.
- **Polyvalue inline wrappers (16 bytes, Copy, not arena-stored) — see @TFITCX / @PVECFPZ:** `KindHT` (11 variants), `ExpressionH` (50 variants), `IExpressionH` (2 variants — `ReferenceExpressionH`/`AddressExpressionH`), `InternedKindPayloadH`/`InternedKindPayloadValH` (4 variants each — the kind-payload interner family-dispatcher).
- **Value-type primitives (Copy, no interning):** `CoordH`, `OwnershipH`, `LocationH`, `Mutability`, `Variability`, `IntHT`, `VoidHT`, `BoolHT`, `StrHT`, `FloatHT`, `NeverHT`, `CodeLocation`, `RegionH`, `SimpleId`/`SimpleIdStep`.
- **Neither arena (stack/heap):** `Hamuts` (stack accumulator with heap maps), `Hammer` (stack god struct), `Locals` (stack per-function), `HammerCompilation` (stack driver), `IVonData` (heap owned).

**Casting identity rule.** Polyvalue enums compare structurally on their 16-byte (tag + ref/value) layout — derive `PartialEq`/`Eq`/`Hash`. Interned canonical refs compare via `ptr::eq` on the `&'h T`; the `MustIntern` seal guarantees canonical pointer identity. Casting up the sub-enum hierarchy (e.g. concrete `&'h StructHT` → `KindHT::Struct(...)`) is a stack-only rewrap; no interner involvement.

**Equality opt-out for the expression hierarchy** (`ExpressionH`, `IExpressionH`, `ReferenceExpressionH`, `AddressExpressionH`, and all ~50 per-variant payload structs, plus `FunctionH` and `PackageH` which embed expressions transitively) — see @IEOIBZ. Driving reason: `ConstantF64H` holds `f64`, which can't derive `Eq`/`Hash`. The whole expression-AST family drops them uniformly: `#[derive(Copy, Clone, Debug)]` only. `CoordH` / `KindHT` / interned payloads keep the full `Copy, Clone, PartialEq, Eq, Hash, Debug`.

### 1.6 Mutual-Recursion Shape

The H-type graph is mutually recursive but every edge is an `&'s`, `&'i`, or `&'h` ref — pointer-sized, finite. `CoordH → KindHT → StructHT → IdH → ...`. `ExpressionH → CallH → PrototypeH → IdH`. Self-recursion via `ExpressionH` variants (`BlockH.inner: ExpressionH`, `ConsecutorH.exprs: &'h [ExpressionH]`) is resolved by inline 16-byte sub-enum values held behind `&'h` payload refs — no `Box`, no `Vec` in arena types.

The `PackageH` cross-reference tables (`ArenaIndexMap` keyed on `&'h IdH`) close the graph back up at consumption time: e.g. a `&'h StructHT` is resolved to its `&'h StructDefinitionH` via `PackageH.struct_id_to_struct_def`.

### 1.7 IVonData Is Heap, Not Arena

`IVonData` (`src/von/ast.rs`) is the wire-format representation — emitted to disk as a string or fed directly to the backend. It's a **runtime data type**, not a program-AST type. Variants own their data: `VonStr { value: String }`, `VonObject { tyype: String, members: Vec<VonMember> }`, `VonArray { members: Vec<IVonData> }`. No lifetime parameters anywhere. Reasons:

1. The consumer (file writer or backend) doesn't care about arena lifetimes.
2. The output is serialized — pointer-identity doesn't carry meaning across the boundary.
3. Wire-format generation is a one-shot walk; no benefit to interning or deduping.

This is the **one place where the AASSNCMCX rule is relaxed** — the explicit boundary where in-memory arena layout exits and runtime serialization takes over. Don't try to arena-allocate the `String`s; the output crosses an FFI/disk boundary where arena lifetimes don't carry.

### 1.8 Polyvalue Density Is Lower Than Upstream

Unlike the typing pass (with its deep `INameT`-21-variant / `KindT`-3-sub-enum-family hierarchies) and the instantiator (`R`-mode-parameterized everything), the H-AST is mostly flat. `KindHT` has 11 variants — 6 inline primitives plus 4 `&'h` payload variants plus `OpaqueHT`. There is no `INameH` hierarchy at all — names are pre-computed strings on `IdH`. The Polyvalue derive pattern still applies to multi-variant wrappers (`KindHT`, `ExpressionH`, `IExpressionH`, `InternedKindPayloadH`), but the depth is shallower than upstream.

---

## Part 2: The God Struct

### 2.1 Architecture

All Scala sub-hammers (`NameHammer`, `StructHammer`, `TypeHammer`, `FunctionHammer`, `BlockHammer`, `ExpressionHammer`, `LetHammer`, `LoadHammer`, `MutateHammer`, `VonHammer`) collapse into a single `Hammer` struct:

```
pub struct Hammer<'s, 'i, 'h, 'ctx>
where 's: 'i, 's: 'h, 'i: 'h,
{
    pub interner: &'ctx HammerInterner<'s, 'h>,
    pub keywords: &'ctx Keywords<'s>,
    pub scout_arena: &'ctx ScoutArena<'s>,
    pub instantiating_interner: &'ctx InstantiatingInterner<'s, 'i>,
}
```

Immutable configuration only — four borrowed refs. Mutable state threads through `&mut Hamuts<'s, 'i, 'h>` on every call. Per-function transient state (variable tracking) lives in a `Locals<'s, 'i, 'h>` stack value constructed at function-translation entry.

**Not on the god struct:**
- **No Hamuts field** — threaded as `&mut hamuts` per call (matches typing-pass `CompilerOutputs` precedent).
- **No Locals field** — constructed per-function inside `translate_function`, threaded as `&mut locals` to expression-level methods.
- **No VonHammer field, no VonHammer struct** — `vonify_*` methods are colocated in `von_hammer.rs` as additional `impl Hammer` blocks. `HammerCompilation::get_von_hammer()` returns a freshly constructed `Hammer` value, not a cached one.
- **No options field on the god struct itself.** Options live on `HammerCompilation` (the driver wrapper) as `HammerCompilationOptions`.

**File-level organization.** Each per-area file (`name_hammer.rs`, `type_hammer.rs`, `struct_hammer.rs`, `function_hammer.rs`, `block_hammer.rs`, `expression_hammer.rs`, `let_hammer.rs`, `load_hammer.rs`, `mutate_hammer.rs`, `von_hammer.rs`) contains `impl<'s, 'i, 'h, 'ctx> Hammer<'s, 'i, 'h, 'ctx> where ... { ... }` blocks. No state lives in any of these files — they exist purely for code organization.

### 2.2 `&self` + `&mut hamuts` + `&mut locals` Enables Re-entrancy

Deep mutual recursion (e.g. `translate_function → translate_expression → translate_block → translate_let → translate_expression`) works because `&self` allows shared borrow, and the two mutable accumulators re-borrow at each call level. `&mut hamuts` carries program-wide state (functions, structs, interfaces, externs); `&mut locals` carries per-function variable state.

The two-mutable-ref pattern is the Rust translation of Scala's `HamutsBox` + `LocalsBox` mutable wrappers. See §2.3.

### 2.3 Collapsed Mutable Wrappers: `HamutsBox` and `LocalsBox`

Scala has a recurring pattern: an immutable struct (`Hamuts`) wrapped in a mutable wrapper (`HamutsBox`) that holds it as `var inner: Hamuts`. The Rust port **collapses both onto a single struct mutated via `&mut self`** (architect directive, matching the typing-pass `CompilerOutputs` precedent). Same for `LocalsBox` → `Locals`.

**Don't reintroduce the Box wrappers** even when the borrow checker complains. The complaint is almost always solvable by re-borrowing at a higher scope (copy-out-then-mutate per §3.3). The Scala `Box.snapshot` API survives as `Locals::snapshot(&self) -> Locals` (an arena-free clone of the `HashMap`/`HashSet` state) used for if-branch and block-scope snapshots in `block_hammer.rs` and `expression_hammer.rs::translate_if`.

### 2.4 No Macros, No Delegates

Macros only fire during typing-pass scout-to-typing translation. By the time we reach the Hammer, all macro outputs are already concrete typing/instantiator definitions. No macro dispatch enum needed.

Scala's hammer delegate traits (`IExpressionHammerDelegate`, etc.) are unnecessary — every method calls `self.method(...)`.

### 2.5 VonHammer Collapsed Onto Hammer

Scala's `VonHammer` was a stateful sub-compiler (holding `nameHammer`, `typeHammer`) emitting VON wire-format from the H-side AST. In the god-struct collapse, all that state is already on `Hammer`. `von_hammer.rs` makes the collapse explicit: ~32 `vonify_*` methods spread across `impl Hammer` blocks, no struct field, no cache.

`HammerCompilation::get_von_hammer(&self) -> Hammer<...>` constructs a fresh `Hammer` value with the appropriate field borrows on each call. The Scala `vonHammerCache: Option[VonHammer]` field is deleted — there is nothing to cache.

VonHammer runs **after** `Hammer::translate` produces and caches `ProgramH`. The integration-test entry point (`run_compilation::get_hamuts`) calls them in sequence:

```
let hamuts = self.hammer_compilation.get_hamuts();         // runs Hammer::translate, caches ProgramH
self.hammer_compilation.get_von_hammer().vonify_program(hamuts);  // separate vonify pass
hamuts
```

The Hammer pipeline is fully synchronous — no deferred queues — so there's no chance of VonHammer running on a not-yet-finished `Hamuts`.

### 2.6 Method vs Free Function Under The Collapse

When porting a Scala `def`, decide method-on-`Hammer` vs free function by inspecting what the **Scala body uses**:

- **Scala body uses no `this`** (pure on its arguments) → may become a Rust free function. Mirror the function name and file location; only the receiver drops. SPDMX exception Q codifies this.
- **Scala body uses `this`** (reads fields, calls methods on the same class) → must stay a method on `Hammer`.

Concrete examples: `name_hammer.rs` carries free functions `simplify_id` / `simplify_name` / `simplify_templata` / `simplify_kind` / `simplify_coord` / `translate_package_coordinate`. These were Scala's `object NameHammer` static methods — pure on their arguments — so they translate as free fns that take the interner and scout arena explicitly. Body-bearing methods like `translate_full_name` stay on `impl Hammer` because they need `self.interner` and `self.scout_arena`.

The hammer side also has a few free helpers in `hammer.rs` itself: `flatten_and_filter_voids`, `consecutive`, `consecrash` — used by the expression translator to build consecutor nodes from filtered child expressions.

---

## Part 3: Hamuts — The Mutable Accumulator

### 3.1 Structure

`Hamuts<'s, 'i, 'h>` is the program-wide mutable accumulator. Carries three lifetimes. **15 fields**, all heap-backed `HashMap` or `Vec`, lives on the stack:

```
pub struct Hamuts<'s, 'i, 'h>
where 's: 'i, 'i: 'h,
{
    // Naming dedup (used for shortened-name disambiguation).
    pub human_name_to_full_name_to_id: HashMap<String, HashMap<String, i32>>,

    // Struct translation tables — cross-arena (&'i key → &'h or by-value value).
    pub struct_t_to_opaque_h: HashMap<&'i StructIT<'s, 'i, cI>, &'h OpaqueHT<'s, 'h>>,
    pub struct_t_to_struct_h: HashMap<&'i StructIT<'s, 'i, cI>, &'h StructHT<'s, 'h>>,
    pub struct_t_to_struct_def_h: HashMap<&'i StructIT<'s, 'i, cI>, StructDefinitionH<'s, 'h>>,
    pub struct_defs: Vec<StructDefinitionH<'s, 'h>>,   // ordered definition list

    // Array translation tables.
    pub static_sized_arrays:
        HashMap<&'i StaticSizedArrayIT<'s, 'i, cI>, StaticSizedArrayDefinitionHT<'s, 'h>>,
    pub runtime_sized_arrays:
        HashMap<&'i RuntimeSizedArrayIT<'s, 'i, cI>, RuntimeSizedArrayDefinitionHT<'s, 'h>>,

    // Interface translation tables.
    pub interface_t_to_interface_h:
        HashMap<&'i InterfaceIT<'s, 'i, cI>, &'h InterfaceHT<'s, 'h>>,
    pub interface_t_to_interface_def_h:
        HashMap<&'i InterfaceIT<'s, 'i, cI>, InterfaceDefinitionH<'s, 'h>>,

    // Function translation tables.
    pub function_refs:
        HashMap<&'i PrototypeI<'s, 'i, cI>, FunctionRefH<'s, 'h>>,
    pub function_defs:
        HashMap<&'i PrototypeI<'s, 'i, cI>, FunctionH<'s, 'h>>,

    // Package-grouped exports / externs (nested HashMaps).
    pub package_coord_to_export_name_to_function:
        HashMap<PackageCoordinate<'s>, HashMap<StrI<'s>, &'h PrototypeH<'s, 'h>>>,
    pub package_coord_to_export_name_to_kind:
        HashMap<PackageCoordinate<'s>, HashMap<StrI<'s>, KindHT<'s, 'h>>>,
    pub package_coord_to_prototype_to_extern:
        HashMap<PackageCoordinate<'s>, HashMap<&'h PrototypeH<'s, 'h>, HamutsFunctionExtern<'s, 'h>>>,
    pub package_coord_to_kind_to_extern:
        HashMap<PackageCoordinate<'s>, HashMap<&'h OpaqueHT<'s, 'h>, HamutsKindExtern<'s, 'h>>>,
}
```

The Rust field count and order match Scala's `case class Hamuts(...)` byte-for-byte (Scala has the same 15 fields). No origin side tables — H-side data references I-side data directly via `&'i` refs.

### 3.2 Cross-Arena HashMap Pattern

The translation tables use `HashMap<&'i KeyI, &'h ValH>` — keys borrow from the input arena, values from the output arena. Both sides are `MustIntern`-sealed so their `Hash` and `Eq` are already pointer-identity-based.

**No `PtrKey` wrapper.** The typing pass uses `PtrKey<'t, T>` because some typing-arena types aren't sealed at the layer being keyed. The Hammer doesn't need that — every key type is already sealed canonical, so direct `&'i T` / `&'h T` keys hash and compare by pointer natively. (This is a deliberate divergence from typing-pass §4.2; don't import the `PtrKey` convention.)

By-value HashMap values (`StructDefinitionH` in `struct_t_to_struct_def_h`) work because these definition structs are small Copy data containing only `&'h` slices and refs.

### 3.3 Side-Table Access — Copy Out Before `&mut`

Same pattern as typing-pass §4.3. Lookups return Copy pointer-sized values (`&'i K`, `&'h V`, or small by-value structs), enabling copy-out-then-mutate without aliasing issues:

```
let interface_h: &'h InterfaceHT<'s, 'h> = *hamuts.interface_t_to_interface_h
    .get(&interface_i)
    .expect("vassertSome");
self.translate_method(hinputs, hamuts, ..., interface_h);  // OK; interface_h is Copy'd out
```

Borrow-checker pain when first migrating a method usually means the fix is short-borrow-and-copy-out, not reinstating the `HamutsBox` wrapper.

### 3.4 Mutator API

Roughly 15 mutator methods on `Hamuts`: `forward_declare_struct`, `add_struct_originating_from_typing_pass`, `add_struct_originating_from_hammer`, `add_opaque`, `forward_declare_interface`, `add_interface`, `add_static_sized_array`, `add_runtime_sized_array`, `forward_declare_function`, `add_function`, `add_kind_export`, `add_function_export`, `add_function_extern`, `add_kind_extern`, plus paired accessors.

The pattern `originating_from_typing_pass` vs `originating_from_hammer` for structs distinguishes structs that came from an I-side `StructDefinitionI` (typing-pass-defined) versus structs the Hammer synthesizes (e.g. closure boxes via `make_box`). Both end up in the ordered `struct_defs: Vec`; the typing-pass-origin variant also goes into `struct_t_to_struct_def_h`.

Every mutator asserts Scala-style `vassert` preconditions. Re-insertion of an already-registered entry panics — the simplifying pass treats double-registration as a bug, not a recoverable condition. No speculative writes, no rollback machinery (unlike typing pass §4.5).

### 3.5 Hamuts Constructed Inline

There is no `Hamuts::new()` constructor. `Hamuts` is constructed once, inline at the top of `Hammer::translate`, with all 15 fields seeded to `HashMap::new()` / `Vec::new()`. The inline construction is one-shot per simplifying-pass run — there's no reuse to factor out.

### 3.6 `HashMap`, Not `IndexMap`

Hamuts uses `HashMap` (not `IndexMap`). Insertion order doesn't matter for output determinism in this pass — the final `ProgramH` ordering is controlled separately by `struct_defs: Vec<StructDefinitionH>` (the in-emission-order list eventually allocated into `PackageH.structs`). The maps are pure lookup caches, not iteration sources.

This is a deliberate divergence from the instantiator's `InstantiatedOutputsI`, which uses `IndexMap` because the instantiator's worklist drains in insertion order. Don't follow that pattern here.

### 3.7 Per-Package Grouping At Materialization

Most of `Hamuts` is flat (one big map per concern). `Hammer::translate`'s final step **regroups** everything by `PackageCoordinate` into a `PackageCoordinateMap<PackageH>` for the `ProgramH` output:

```
let mut package_to_struct_defs: HashMap<PackageCoordinate<'s>, Vec<StructDefinitionH<'s, 'h>>> =
    HashMap::new();
for sd in hamuts.struct_defs.iter() {
    package_to_struct_defs.entry(sd.id.package_coordinate)
        .or_insert_with(Vec::new).push(*sd);
}
// ... similar regrouping for interfaces, functions, ssa/rsa, exports, externs ...
for package_coord in all_package_coords {
    packages.put(package_coord, PackageH { structs: ..., functions: ..., ... });
}
```

The flat accumulator → per-package output split mirrors Scala.

---

## Part 4: HammerInterner

### 4.1 Structure

`HammerInterner<'s, 'h>` wraps a `&'h Bump` plus `RefCell<Inner>` holding **three `StdHashMap`s**, one per intern family:

```
pub struct HammerInterner<'s, 'h> where 's: 'h {
    bump: &'h Bump,
    inner: RefCell<Inner<'s, 'h>>,
}

struct Inner<'s, 'h> where 's: 'h {
    kind_payload_val_to_ref:
        StdHashMap<InternedKindPayloadValH<'s, 'h>, InternedKindPayloadH<'s, 'h>>,
    prototype_val_to_ref:
        StdHashMap<PrototypeHValH<'s, 'h>, &'h PrototypeH<'s, 'h>>,
    id_h_val_to_ref:
        StdHashMap<IdHValH<'s, 'h>, &'h IdH<'s, 'h>>,
}
```

Three physical maps. The kind-payload map is dispatch-style: keyed by a 4-variant `InternedKindPayloadValH` enum (StructHT / InterfaceHT / StaticSizedArrayHT / RuntimeSizedArrayHT), each routed via a dedicated convenience wrapper. So **6 logical interned types total**: 4 kind payloads + `PrototypeH` + `IdH`.

This is much narrower than the instantiator's 12-family interner (which has 3× region-mode multiplication on top of the underlying types) and the typing pass's 6-family interner. The reason is §1.2 invariant 4: by H-side everything is `cI`-collapsed, so there's no `R` parameter to multiply over.

### 4.2 IDEPFL Dual-Enum Pattern

Same `*ValH` (transient query key) + `*H` (canonical arena ref) pattern as typing/instantiating. The `Val` is content-keyed (derives `PartialEq, Eq, Hash`); the canonical is `MustIntern`-sealed so its pointer identity is the lookup key downstream.

- `IdH<'s, 'h>` / `IdHValH<'s, 'h>` / `intern_id_h`
- `PrototypeH<'s, 'h>` / `PrototypeHValH<'s, 'h>` / `intern_prototype`
- `StructHT<'s, 'h>` / `StructHTValH<'s, 'h>` / `intern_struct_ht`
- `InterfaceHT<'s, 'h>` / `InterfaceHTValH<'s, 'h>` / `intern_interface_ht`
- `StaticSizedArrayHT<'s, 'h>` / `StaticSizedArrayHTValH<'s, 'h>` / `intern_static_sized_array_ht`
- `RuntimeSizedArrayHT<'s, 'h>` / `RuntimeSizedArrayHTValH<'s, 'h>` / `intern_runtime_sized_array_ht`

The four `intern_*_ht` convenience methods route through the underlying `intern_kind_payload` dispatcher and unwrap the variant.

### 4.3 `MustIntern` Seal

Per @SICZ. `pub struct MustIntern(())` — private inner unit means only `hammer_interner.rs` itself can construct one. Every interned struct definition has `pub _must_intern: MustIntern` as a private-module-only field. The seal guarantees canonical pointer identity: two `&'h StructHT` with structurally-equal contents always have the same pointer, because the only way to construct one is through `intern_*`, which deduplicates via the `HashMap`. This enables sound `ptr::eq`-based `PartialEq`/`Hash` on `&'h StructHT` for downstream HashMap keys.

### 4.4 Arena Utilities

`HammerInterner` plays double duty: interner + raw arena allocator. Exposes:
- `bump() -> &'h Bump`
- `alloc<T>(T) -> &'h mut T`
- `alloc_slice_copy<T: Copy>(&[T]) -> &'h [T]`
- `alloc_slice_from_vec<T>(Vec<T>) -> &'h [T]`
- `alloc_index_map<K, V>() -> ArenaIndexMap<'h, K, V>`
- `alloc_index_map_from_iter<K, V, I>(I) -> ArenaIndexMap<'h, K, V>`

Used for emitting non-interned data: expression payloads, definitions, `OpaqueHT` (allocated-but-not-deduped), and the `PackageH` cross-reference `ArenaIndexMap`s.

### 4.5 No OverloadSet, No Heavy Templatas

OverloadSet types **don't exist** at the hammer level. By the time the instantiator has run, all overload resolution is complete; the I-side has concrete prototype references everywhere. There is no `KindHT::OverloadSet` variant.

Heavy templatas (`FunctionTemplata`, `StructDefinitionTemplata`, etc.) similarly don't exist. The instantiator's `ITemplataI` is narrower than typing's `ITemplataT`; the Hammer's translated form doesn't need a templata wrapper at all — concrete `KindHT`, `PrototypeH`, `CoordH` are enough.

So the Hammer's type system is the simplest of the three passes: ~6 logical interned families, no `R` parameter, no templata wrapper, no overload set, no `+T` phantom erasure (the instantiator already collapsed Scala's `+T`).

---

## Part 5: Locals — The Per-Function Companion

`Locals<'s, 'i, 'h>` is the per-function-body local-variable state. Distinct from `Hamuts` (which is program-wide). Constructed fresh inside `translate_function`, threaded as `&mut locals` to expression-level methods, discarded on function exit.

### 5.1 Structure

```
pub struct Locals<'s, 'i, 'h>
where 's: 'i, 'i: 'h,
{
    pub typing_pass_locals: HashMap<&'i IVarNameI<'s, 'i, cI>, VariableIdH<'s, 'h>>,
    pub unstackified_vars: HashSet<VariableIdH<'s, 'h>>,
    pub locals: HashMap<VariableIdH<'s, 'h>, Local<'s, 'h>>,
    pub next_local_id_number: i32,
}
```

All four fields are heap-backed. Threaded through every expression-translation method that introduces or references a local.

Carries `'i` because `typing_pass_locals` is keyed by `&'i IVarNameI<'s,'i,cI>` — the upstream variable name reference is the canonical identity used to resolve "what's the H-side `VariableIdH` for this I-side `IVarNameI`?".

### 5.2 LocalsBox Collapsed

Scala had `LocalsBox` (mutable wrapper) around immutable `Locals` (functional-update style). Per architect directive (matching `HamutsBox → Hamuts`), the Rust port has a single mutable `Locals` accumulator. Functional-update Scala methods become `&mut self` mutators (`mark_unstackified`, `mark_restackified`, `set_next_local_id_number`, `add_hammer_local`, `add_compiler_local`, `add_typing_pass_local`).

### 5.3 Snapshot

`Locals::snapshot(&self) -> Locals` clones the four fields (heap `Clone` on the `HashMap`/`HashSet`/scalar) into a fresh stack value. Used by `block_hammer.rs::translate_block` and `expression_hammer.rs::translate_if` to capture branch-local mutations without affecting the parent.

The parent then reconciles by walking `child.unstackified_vars` and re-marking the corresponding parent locals as unstackified (the "both branches must agree on what's unstackified" invariant). `set_next_local_id_number` is also propagated parent-ward so child-allocated IDs don't collide on the next allocation.

### 5.4 VariableIdH Allocation

`add_hammer_local` and `add_compiler_local` mint a fresh `VariableIdH { number, height, name }`:
- `number = next_local_id_number` (monotonically increasing).
- `height = locals.len() as i32`.
- `name = None` for hammer-internal temporaries, `Some(&'h IdH)` for compiler-pass locals.

IDs are stable within a function. Across functions they're independently allocated (no global registry — each `translate_function` constructs a fresh `Locals`). Sharing a `Locals` across functions would collide IDs and produce garbage backend stack-frame layouts.

---

## Part 6: Final-AST Type Families

The final AST (`src/final_ast/`) is the wire-format-ready output, organized into three files:

- **`types.rs`** — `CoordH`, `KindHT` + sub-enums, the 4 sealed kind payloads + `*ValH` mirrors, ownership/location/mutability/variability enums, `OpaqueHT`, `SimpleId`/`SimpleIdStep`, `CodeLocation`, `RegionH`, `HamutsFunctionExtern`/`HamutsKindExtern`, the two `*ArrayDefinitionHT` types.
- **`ast.rs`** — `ProgramH`, `PackageH`, `FunctionH`, definitions (`StructDefinitionH`, `InterfaceDefinitionH`, `EdgeH`, `StructMemberH`, `InterfaceMethodH`), `PrototypeH`, `IdH`, `FunctionRefH`, `IFunctionAttributeH`.
- **`instructions.rs`** — `ExpressionH` (50 variants) + ~50 payload structs + `IExpressionH` (2-variant Reference/Address wrapper) + `Local` / `VariableIdH`.

Every lifetime-parameterized struct in this section has an implicit `where 's: 'h` bound (§1.2).

### 6.1 `CoordH` — Inline Copy

```
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct CoordH<'s, 'h> where 's: 'h {
    pub ownership: OwnershipH,
    pub location: LocationH,
    pub kind: KindHT<'s, 'h>,
}
```

Differs from upstream:
- vs `CoordT`: drops the `region: RegionT` field — regions are resolved away by `cI`-collapse.
- vs `CoordI`: adds `location: LocationH` — the Hammer's job includes computing `InlineH`/`YonderH` (the H-only data-layout distinction the backend needs).

Small, Copy, passed by value. Not interned — structural eq.

### 6.2 `KindHT` — Inline Wrapper, Polyvalue

```
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum KindHT<'s, 'h> where 's: 'h {
    // Primitives inline (small Copy)
    IntHT(IntHT), VoidHT(VoidHT), BoolHT(BoolHT), StrHT(StrHT),
    FloatHT(FloatHT), NeverHT(NeverHT),
    // &'h refs to arena-resident payloads
    OpaqueHT(&'h OpaqueHT<'s, 'h>),
    InterfaceHT(&'h InterfaceHT<'s, 'h>),
    StructHT(&'h StructHT<'s, 'h>),
    StaticSizedArrayHT(&'h StaticSizedArrayHT<'s, 'h>),
    RuntimeSizedArrayHT(&'h RuntimeSizedArrayHT<'s, 'h>),
}
```

**11 variants.** Inline-owned, not arena-interned: 16 bytes tag + ref, Copy. Concrete payloads live in the arena (4 interned via `intern_*_ht`, plus `OpaqueHT` allocated-but-not-deduped via `bump().alloc`).

`OpaqueHT` is the H-specific kind representing externally-declared types (the upstream has no body to compile). Its payload holds `package_coord`, `struct_id: &'h IdH`, and `simple_id: SimpleId<'s, 'h>` — non-trivial data, which is why it can't sit inline like the true primitives. It's stored behind `&'h` for the same reason `StructHT`/`InterfaceHT` are.

`KindHT::OverloadSet` does **not** exist — see §4.5.

There are no `ICitizenHT` / `ISubKindHT` / `ISuperKindHT` sub-enum families in current code. Unlike the typing pass and instantiator (which have these for dispatching method-receiver type narrowing), the Hammer doesn't need them — pattern-match on `KindHT` directly.

### 6.3 `IdH` — Interned, Sealed, Flat String Fields

```
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct IdH<'s, 'h> where 's: 'h {
    pub local_name: StrI<'s>,
    pub package_coordinate: PackageCoordinate<'s>,
    pub shortened_name: StrI<'s>,
    pub fully_qualified_name: StrI<'s>,
    pub _must_intern: MustIntern,
    pub _phantom_h: PhantomData<&'h ()>,
}
```

**`IdH` is structurally different from `IdT`/`IdI`.** Typing's `IdT` and instantiating's `IdI` carry `init_steps: &[INameT/INameI]` — a multi-step hierarchical name path with sub-enum-typed steps. H's `IdH` collapses to **four pre-computed `StrI` fields**: a local name (last step), the package, a shortened display name, and a fully-qualified name.

This is the H-side's job: produce a flat string-keyed name the backend can consume directly without walking a step list. The stringification happens in `name_hammer.rs::translate_full_name`, which calls `instantiated_humanizer::humanize_id` and `humanize_name` to produce the shortened/fully-qualified forms, then interns them via `scout_arena.intern_str`.

**There is no `INameH` family.** The H-AST has no name hierarchy at all — flat strings are enough for the backend's wire format. Any reference to `INameH` / `FunctionNameH` / `StructNameH` / etc. in prior design docs was an extrapolation from `INameT`/`INameI` that didn't survive to code.

`IdH` derives `PartialEq, Eq, Hash` directly (the `StrI` and `PackageCoordinate` fields are themselves canonical-pointer-eq, so the structural derive resolves to identity). The `_phantom_h: PhantomData<&'h ()>` keeps `'h` live in the type for future-proofing — useful when a downstream `&'h`-holding field is added without lifetime churn.

`IdH` has a custom `Display` impl emitting Scala case-class auto-toString form for debug parity.

### 6.4 `PrototypeH` — Interned, Sealed

```
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct PrototypeH<'s, 'h> where 's: 'h {
    pub id: &'h IdH<'s, 'h>,
    pub params: &'h [CoordH<'s, 'h>],
    pub return_type: CoordH<'s, 'h>,
    pub _must_intern: MustIntern,
}
```

Mirror of typing's `PrototypeT` and instantiating's `PrototypeI`, but `IdH`-flavored. The slice `params: &'h [CoordH]` is arena-allocated. Equality is the structural derive; soundness rests on the `MustIntern` seal canonicalizing the slice via the `Val`-key intern.

### 6.5 Definition Structs — Allocated, Not Interned

`FunctionH`, `StructDefinitionH`, `InterfaceDefinitionH`, `EdgeH`, `StructMemberH`, `InterfaceMethodH`, plus the two `*ArrayDefinitionHT` shapes, plus `OpaqueHT`. All `'h`-allocated, not interned. Most embed `ExpressionH` (`FunctionH.body: ExpressionH`) or hold `ArenaIndexMap`s (`PackageH`), so they drop `Eq`/`Hash` per §1.5.

```
#[derive(Copy, Clone, Debug)]
pub struct FunctionH<'s, 'h> where 's: 'h {
    pub prototype: &'h PrototypeH<'s, 'h>,
    pub is_abstract: bool,
    pub is_extern: bool,
    pub attributes: &'h [IFunctionAttributeH],
    pub body: ExpressionH<'s, 'h>,
}
```

`FunctionRefH { prototype: &'h PrototypeH }` is a small inline Copy struct used by `Hamuts.function_refs` and by `CallH.function`. Not interned, not allocated separately — pass by value freely.

### 6.6 `ExpressionH` and the 50 Payload Structs

```
#[derive(Copy, Clone, Debug)]
pub enum ExpressionH<'s, 'h> where 's: 'h {
    ConstantVoidH(&'h ConstantVoidH),
    ConstantIntH(&'h ConstantIntH),
    ConstantBoolH(&'h ConstantBoolH),
    ConstantStrH(&'h ConstantStrH<'s, 'h>),
    ConstantF64H(&'h ConstantF64H),
    ArgumentH(&'h ArgumentH<'s, 'h>),
    StackifyH(&'h StackifyH<'s, 'h>),
    RestackifyH(&'h RestackifyH<'s, 'h>),
    UnstackifyH(&'h UnstackifyH<'s, 'h>),
    // ... 41 more variants ...
    DiscardH(&'h DiscardH<'s, 'h>),
    PreCheckBorrowH(&'h PreCheckBorrowH<'s, 'h>),
}
```

**50 variants.** All payloads are arena-allocated `&'h XH` refs, not interned — expression nodes are unique by construction (no dedup needed).

**Derives `Copy, Clone, Debug` only.** Drops `PartialEq`/`Eq`/`Hash` uniformly because `ConstantF64H` holds `f64`. The whole expression-AST family drops them (no `f64::to_bits()` workaround); same philosophy as typing-pass §7.3.

**The dispatcher.** `ExpressionH::result_type(&self) -> CoordH<'s, 'h>` is the type-of dispatcher — one big match arming all 50 variants. Per-arm bodies are field reads or simple computations (e.g. `BorrowToWeakH` builds `CoordH { ownership: WeakH, location: YonderH, kind: inner.result_type().kind }`). This is the abstract-def-dispatcher-on-enum pattern.

`ExpressionH` also has `expect_struct_access`, `expect_int_access`, `expect_bool_access`, `expect_static_sized_array_access`, `expect_runtime_sized_array_access`, `expect_struct_coord`, `expect_static_sized_array_coord`, `expect_runtime_sized_array_coord`, `expect_interface_coord` — narrow-and-extract helpers used by the translators when the variant must be a specific shape.

**Wrapper layered above:** `IExpressionH` is the 2-variant `Reference(&'h ReferenceExpressionH) | Address(&'h AddressExpressionH)` wrapper. This mirrors typing's `ExpressionTE` 2-variant wrapper. The 50-variant enum is `ExpressionH` (the actual instruction set), not `IExpressionH`.

### 6.7 `StructHT` / `InterfaceHT` / Array Types

The 4 sealed kind payloads. Each is Interning-permanent with a `*ValH` mirror.

```
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StructHT<'s, 'h> where 's: 'h {
    pub id: &'h IdH<'s, 'h>,
    pub _must_intern: MustIntern,
}

#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StructHTValH<'s, 'h> where 's: 'h {
    pub id: &'h IdH<'s, 'h>,
}
```

The `*ValH` mirror lacks `_must_intern` — it's the construction-time value used to query the interner. `InterfaceHT` has the same shape. `StaticSizedArrayHT` and `RuntimeSizedArrayHT` follow the pattern with array-shape fields (the relevant array name).

The corresponding **definition** types (`StructDefinitionH`, `InterfaceDefinitionH`, `StaticSizedArrayDefinitionHT`, `RuntimeSizedArrayDefinitionHT`) are temporary state — arena-allocated by-value, held in `Hamuts`'s translation tables, and ultimately landing in `PackageH.structs` / `PackageH.interfaces` / etc.

### 6.8 `ProgramH` and `PackageH`

```
pub struct ProgramH<'s, 'h> where 's: 'h {
    pub packages: PackageCoordinateMap<'s, PackageH<'s, 'h>>,
}

pub struct PackageH<'s, 'h> where 's: 'h {
    pub interfaces: &'h [InterfaceDefinitionH<'s, 'h>],
    pub structs: &'h [StructDefinitionH<'s, 'h>],
    pub functions: &'h [FunctionH<'s, 'h>],
    pub static_sized_arrays: &'h [StaticSizedArrayDefinitionHT<'s, 'h>],
    pub runtime_sized_arrays: &'h [RuntimeSizedArrayDefinitionHT<'s, 'h>],
    // Cross-reference tables for backend consumption:
    pub export_name_to_function: &'h ArenaIndexMap<'h, StrI<'s>, &'h PrototypeH<'s, 'h>>,
    pub export_name_to_kind: &'h ArenaIndexMap<'h, StrI<'s>, KindHT<'s, 'h>>,
    pub prototype_to_extern:
        &'h ArenaIndexMap<'h, &'h PrototypeH<'s, 'h>, HamutsFunctionExtern<'s, 'h>>,
    pub kind_to_extern:
        &'h ArenaIndexMap<'h, &'h OpaqueHT<'s, 'h>, HamutsKindExtern<'s, 'h>>,
}
```

`PackageH` opts out of `Eq`/`Hash` because `ArenaIndexMap` doesn't impl them.

### 6.9 Mutual Recursion

Same shape as typing: `CoordH → KindHT → StructHT → IdH → ...`. All edges are `&'s` or `&'h` refs or inline 16-byte enums. Self-recursion via `ExpressionH` variants is resolved by inline 16-byte sub-enum values behind `&'h` payload refs. No `Box`/`Vec` in arena types.

---

## Part 7: VonHammer — The Wire-Format Boundary

`von_hammer.rs` walks `ProgramH` and emits `IVonData` (`src/von/ast.rs`) — the structured wire format consumed by the backend or written to disk. ~32 `vonify_*` methods, one per H-AST node family. It's the largest file in the pass (~2.5K LOC).

### 7.1 Collapsed Onto Hammer

Per §2.5, `VonHammer` is not a Rust struct. Methods are `impl Hammer` blocks colocated in `von_hammer.rs`. Stateless — every method takes `&self` and reads from arguments only.

### 7.2 `IVonData` Output Type

```
#[derive(Clone, Debug, PartialEq)]
pub enum IVonData {
    Int(VonInt), Float(VonFloat), Bool(VonBool), Str(VonStr),
    Object(VonObject), Array(VonArray),
}

pub struct VonObject {
    pub tyype: String,
    pub id: Option<String>,
    pub members: Vec<VonMember>,
}

pub struct VonMember { pub field_name: String, pub value: IVonData }
pub struct VonArray { pub id: Option<String>, pub members: Vec<IVonData> }
pub struct VonStr { pub value: String }
// ... primitive wrappers ...
```

Fully heap-owned, no lifetime parameters. Per §1.7, the AASSNCMCX boundary.

### 7.3 Type Tags And Field Names Match Scala String-For-String

The `tyype` of a `VonObject` and the `field_name` of a `VonMember` are Scala-faithful strings. Tags: `"ConstantInt"`, `"LocalLoad"`, `"Restackify"`, `"ExternCall"`, `"StructToInterfaceUpcast"`. Field names: `"sourceExpr"`, `"resultType"`, `"argumentIndex"`, `"knownLive"`, `"localName"`, `"memberIndex"` — **camelCase**, not Rust snake_case. The Rust `VonMember` struct stores plain `String`s; `vonify_*` methods supply the camelCase literal explicitly.

### 7.4 No Behavior, Only Translation

`von_hammer.rs` is pure translation — doesn't compute, doesn't error (panics on truly unimplemented variants), doesn't allocate into the H arena. The output `IVonData` is owned. This makes it the cleanest part of the pass to migrate: every method is a 1:1 Scala port with no interesting design decisions.

### 7.5 VON Parity As Acceptance Criterion

The simplifying-pass output is the documented acceptance criterion for the full frontend migration — `von_hammer`'s emission must match what the backend reads byte-for-byte. The roadmap's "VON wire-format parity" milestone needs a dedicated diff harness against the Scala backend's output. Body migration tests today check that Rust output goes green; they don't yet compare `IVonData` byte-for-byte to Scala's.

---

## Part 8: Error Handling

### 8.1 Mostly Panic

By the time we reach the Hammer, all user-visible errors have been caught upstream (typing, instantiator). The Hammer's "errors" are programmer-error / invariant violations:
- "Tried to translate a function that wasn't forward-declared" — `panic!`.
- "Expected struct kind, got interface kind" — `panic!`.
- "Unmigrated expression variant" — `panic!`.
- "Already exported a `foo` from package `bar`" — `panic!` (treated as a bug-shaped condition, not a recoverable user error).

The faithful translation of Scala's `vfail()`, `vimpl()`, `vwat()`, `vcurious()` is `panic!`.

### 8.2 No `Result` Return On `translate`

Unlike `Compiler::compile_program`, `Hammer::translate` returns `&'h ProgramH<'s, 'h>` directly — no `Result`. Programmer-error variants panic; well-formed input always succeeds.

The pass-through delegate methods on `HammerCompilation` (`get_parseds`, `get_scoutput`, `get_compiler_outputs`) still return upstream `Result` types verbatim, but those are forwards to earlier passes, not Hammer-emitted errors.

---

## Part 9: Keywords

`Keywords<'s>` — the scout-arena-interned instance flows through from upstream passes. No Hammer-specific `Keywords<'h>` needed. The Hammer references `self.keywords.box_human_name`, `self.keywords.box_member_name`, etc. for the closure-box construction in `struct_hammer.rs::make_box`.

---

## Part 10: Driving The Pass

### 10.1 Top-Level Entry — `Hammer::translate`

```
impl<'s, 'i, 'h, 'ctx> Hammer<'s, 'i, 'h, 'ctx>
where 's: 'h, 's: 'i, 'i: 'h,
{
    pub fn translate(&self, hinputs: &HinputsI<'s, 'i>) -> &'h ProgramH<'s, 'h> { ... }
}
```

`translate` (a method, not a free function) is the public Hammer entry point. Pseudocode of its phases:

1. Destructure `HinputsI` into 9 fields (interfaces, structs, functions, edge tables, exports, externs).
2. Construct fresh stack `Hamuts` with 15 empty maps (no `Hamuts::new()`).
3. Iterate `kind_exports` → `translate_kind` + `hamuts.add_kind_export`.
4. Iterate `function_exports`, `kind_externs`, `function_externs` (mangling and translating externs per the `numInherited` template-args reshuffle, per @PRIIROZ / @SMLRZ).
5. `translate_interfaces` → `translate_structs`.
6. **Two-phase function translate** (user first, then non-user) — for ID stability across runs, matching Scala.
7. Regroup all `Hamuts` definitions by `PackageCoordinate`.
8. Build `PackageH` per package, `interner.alloc_slice_from_vec` for each definition list, `interner.alloc(ArenaIndexMap::...)` for each cross-reference table.
9. `interner.alloc(ProgramH { packages })` and return.

No deferred-action drain (unlike typing's `drain_all_deferred`) — simplifying is fully synchronous because by Hammer-time the program is fully monomorphized; no further discovery happens.

### 10.2 `HammerCompilation` Coordination Wrapper

`HammerCompilation<'s, 'h, 'ctx, 't, 'i, 'p>` — six lifetimes — the most of any single struct in the codebase, because it threads through every prior pass's lifetime to orchestrate the full pipeline.

Owns:
- `InstantiatedCompilation<'s, 'ctx, 't, 'i, 'p>` (which transitively owns `TypingPassCompilation`, the typing arena, and all upstream passes).
- The `HammerInterner` reference.
- `Vec<&'ctx PackageCoordinate<'p>>` for packages to build.
- An `IPackageResolver<'p>` reference.
- `HammerCompilationOptions { debug_out: Arc<dyn Fn(&str) + Send + Sync>, global_options: GlobalOptions }`.
- A cached `Option<&'h ProgramH<'s, 'h>>` (`hamuts_cache`) — lazy translate-on-first-access.

**Entry for callers**: `get_hamuts(&mut self) -> &'h ProgramH<'s, 'h>` — lazily runs the pipeline through instantiation and then calls `Hammer::translate`. Idempotent via the cache. `get_von_hammer(&self) -> Hammer<...>` returns a fresh hammer view (no cached VonHammer state).

### 10.3 Test Entry

`simplifying/test/test_compilation.rs::test` builds the standard test `HammerCompilation` with `BUILTIN` + `test` package coordinates. Integration tests use this same path via `integration_tests/tests/run_compilation::test`.

### 10.4 Output Lifecycle

`&'h ProgramH<'s, 'h>` flows to two consumers:

1. **Test VM (`testvm/vivem.rs`)** — reads `ProgramH` directly to interpret programs in tests. Returns `Result<IVonData, VmRuntimeErrorV<'s>>`.
2. **VON emission (`von_hammer::vonify_program`)** — for the real backend's wire-format consumer. Returns `IVonData` (owned, heap).

The caller can run either or both. `'h` stays alive as long as either consumer holds output references.

---

## Part 11: Invariants Summary

1. **All HashMap keys on instantiator-side and hammer-side refs use direct `&'i K` or `&'h K`** (no `PtrKey` newtype). Both sides are `MustIntern`-sealed so pointer-identity hashing/equality works natively.
2. **Never use `'static`** (NUSLX). All data lives in `'p`, `'s`, `'i`, `'h`, or on the stack.
3. **No Hamuts side-table read holds the H-arena borrow live across a mutation.** Copy out before `&mut hamuts` (§3.3).
4. **VonHammer reads `ProgramH`, does not mutate `Hamuts`.** It runs after `Hamuts` has died.
5. **The expression-AST family drops `Eq`/`Hash` uniformly** (driven by `ConstantF64H`'s `f64`). No `to_bits()` workaround; the whole family is `Copy + Clone + Debug` only.
6. **`MustIntern` seal preserves canonical pointer identity** on `&'h StructHT`, `&'h InterfaceHT`, `&'h StaticSizedArrayHT`, `&'h RuntimeSizedArrayHT`, `&'h PrototypeH`, `&'h IdH`. Never construct these except through `intern_*` methods.
7. **Forward-declare-then-fill is mandatory for cyclic denizens** — functions, structs, interfaces. Skipping the forward declare breaks cycle resolution when `bar`'s body calls `foo` while `foo` is mid-translation.
8. **No OverloadSet, no heavy templatas, no `+T` erasure, no `R` region-mode generic** — the Hammer's type system is the simplest of the three passes.
9. **No speculative writes** — every Hammer translation succeeds or panics. Re-insertion of a duplicate key is a panic, not a no-op.
10. **The Hammer is the last user of `'i`.** After `Hammer::translate` returns, `'i` can drop; only `'s` and `'h` remain for downstream consumers.
11. **`Hamuts.struct_defs: Vec<StructDefinitionH>` is the order source** for output determinism — the HashMaps are caches. When materializing `PackageH`, iterate the `Vec`, not the map.
12. **`IVonData` is the heap/serialization boundary** — the one place AASSNCMCX doesn't apply. Don't arena-allocate the `String`s.
13. **Wire-format field names match Scala camelCase** — `"sourceExpr"`, not `"source_expr"`. Backend goldens depend on this.

---

## Part 12: Recurring Traps

- **Reintroducing `HamutsBox` / `LocalsBox`.** The borrow-checker pain when first migrating a method is real, but the fix is short-borrow-and-copy-out, not reinstating the Box.
- **`HashMap<&'i StructIT, _>` borrow conflicts.** Reading the map and then calling a `&mut hamuts` method holds the borrow across the call — copy out the value first.
- **Wire-format string mismatch.** Single biggest VON-side trap is camelCase-vs-snake_case. Always check Scala's `VonHammer.scala` for the exact string when porting a `vonify_*` arm.
- **Extern template-args reshuffling.** `@PRIIROZ` / `@SMLRZ`: extern translation moves trailing `numInherited` template args from the leaf step to the parent step, matching backend `rustifySimpleId` expectations. Forgetting this breaks extern wire format.
- **Mangling stubs.** `mangle_func` / `mangle_name` / `mangle_kind` / `mangle_struct` / `mangle_coord` / `mangle_templata` are mostly `panic!()` stubs; many call sites use shortcuts that work for the current test corpus. Don't fill mangling in just because a stub panicked — verify Scala actually produces the mangled name on that path.
- **`ExpressionH::result_type()` arm coverage.** ~50 arms, one per variant. Missing an arm panics at runtime when that variant is encountered. Land them sibling-arm-by-sibling-arm.
- **Variable-ID sharing across functions.** `Locals.next_local_id_number` starts at 1 per function. Don't try to share `Locals` across functions — IDs would collide.
- **`StructIT` / `InterfaceIT` identity for Hamuts keys.** Hamuts maps are keyed by `&'i StructIT<'s, 'i, cI>` (pointer identity on the I-side seal). Don't wrap in `PtrKey<'i, StructIT>` — the `&'i` ref already hashes by pointer.
- **`cI` phantom carries through.** Hammer reads `cI`-flavored types but never constructs an `sI`/`nI`-flavored one. If you find yourself wanting to read an `sI`-flavored type in Hammer, something upstream is wrong.
- **`'i: 'h` bound omitted.** The Hammer god struct's where-clause must include all three of `'s: 'i`, `'s: 'h`, `'i: 'h`. Omitting `'i: 'h` lets `Hamuts.struct_t_to_struct_h: HashMap<&'i K, &'h V>` carry refs that outlive their keys' arena — unsound.

---

## Risks / Open Questions

- **VON parity not yet verified.** Body migration tests check Rust output goes green; they don't check the IVonData matches Scala byte-for-byte. The roadmap "VON wire-format parity" milestone needs a dedicated diff harness before cutover.
- **Mangling underspecification.** Scala mangling is mostly commented out, so there's no clear spec for what the Rust port should produce. Defer until backend integration forces the question.
- **`metal_printer.rs` is a 4-line empty stub.** The actual serializer is `von_hammer.rs` → `IVonData`. The `metal_printer` name is misleading carryover from Scala; consider renaming or deleting.
- **`HammerCompilation` 6-lifetime soup**: structural consequence of multi-pass orchestration. Working but novel. If a future pass is inserted between instantiating and simplifying, lifetimes need to be re-numbered.
- **`Hammer` is `!Sync`** — `HammerInterner.inner: RefCell<Inner>` precludes thread-safe sharing. Per-package or per-function parallel translation would require a different interner shape.
- **Body migration status.** Common expression branches work end-to-end on simple Vale programs; exotic branches (`InterfaceToInterfaceUpcast`, `Reinterpret`, `DestroyImmRuntimeSizedArray`, some `Mutabilify`/`Immutabilify`, `PreCheckBorrow`) still panic. `vonify_*` for the corresponding variants also panics. Mangling is largely `panic!`. Tracked in the per-bucket migration drives.
- **Incremental compilation.** A future serialization boundary would need to break `'s` refs (re-export `StrI` to owned strings or a serializable id). Not in current scope.
- **`f64` Hash boundary.** The expression-AST family drops `Eq`/`Hash` uniformly. If a future variant introduces a type whose Hash impl is genuinely incompatible (another `f64`-like case), the existing uniform drop already accommodates it.

---

## Key Files / Directories

| Path | Purpose |
|---|---|
| `docs/architecture/simplifier-design.md` | this doc — architecture + design decisions |
| `docs/architecture/typing-pass-design-v3.md` | two passes upstream — foundational sister doc |
| `docs/architecture/instantiator-design.md` | direct upstream pass — sister doc |
| `FrontendRust/src/simplifying/hammer.rs` | god struct `Hammer` + top-level `translate` + `Locals` + `flatten_and_filter_voids` / `consecutive` / `consecrash` + mangling stubs |
| `FrontendRust/src/simplifying/hammer_interner.rs` | `HammerInterner` — 3 HashMap families, `MustIntern` seal, arena utilities |
| `FrontendRust/src/simplifying/hammer_arena.rs` | `HammerArena<'h>` thin newtype around `&'h Bump` |
| `FrontendRust/src/simplifying/hammer_compilation.rs` | 6-lifetime driver wrapper, options, `get_hamuts` cache |
| `FrontendRust/src/simplifying/hamuts.rs` | `Hamuts` 15-field accumulator + mutator API + paired accessors |
| `FrontendRust/src/simplifying/name_hammer.rs` | name translation; `impl Hammer::translate_full_name` + free fns `simplify_id` / `simplify_name` / `simplify_kind` / `simplify_coord` / `simplify_templata` / `translate_package_coordinate` |
| `FrontendRust/src/simplifying/type_hammer.rs` | `translate_kind` / `translate_coord` / `translate_coords` / `translate_prototype` / array translation |
| `FrontendRust/src/simplifying/struct_hammer.rs` | struct/interface/edge translation + `make_box` for closure boxing |
| `FrontendRust/src/simplifying/function_hammer.rs` | `translate_functions` + `translate_function` + attributes |
| `FrontendRust/src/simplifying/block_hammer.rs` | block translation + `Mutabilify`/`Immutabilify` (mostly stubbed) |
| `FrontendRust/src/simplifying/expression_hammer.rs` | the mega-match over `RE::*` + `translate_deferreds` + `translate_if` + `translate_while` + extern/interface calls |
| `FrontendRust/src/simplifying/let_hammer.rs` | Let, restackify, destructuring lets; `BOX_MEMBER_INDEX` module const |
| `FrontendRust/src/simplifying/load_hammer.rs` | local/member loads, address↔reference conversion |
| `FrontendRust/src/simplifying/mutate_hammer.rs` | local/member stores |
| `FrontendRust/src/simplifying/von_hammer.rs` | ~32 `vonify_*` methods; VON wire-format emission (largest file in the pass) |
| `FrontendRust/src/simplifying/conversions.rs` | small I→H enum conversion table (Mutability/Variability/Ownership/Location) |
| `FrontendRust/src/simplifying/test/test_compilation.rs` | standard test `HammerCompilation` builder |
| `FrontendRust/src/final_ast/ast.rs` | `ProgramH`, `PackageH`, definitions, `PrototypeH`, `IdH`, `FunctionRefH`, exports/externs |
| `FrontendRust/src/final_ast/types.rs` | `CoordH`, `KindHT` (11 variants), the 4 sealed kind payloads + `*ValH` mirrors, ownership/location/mutability/variability, `OpaqueHT`, `SimpleId`/`SimpleIdStep`, `CodeLocation` |
| `FrontendRust/src/final_ast/instructions.rs` | `ExpressionH` (50 variants) + ~50 payload structs + `IExpressionH` 2-variant wrapper + `Local`/`VariableIdH` + `result_type` dispatcher |
| `FrontendRust/src/final_ast/metal_printer.rs` | 4-line empty stub; the actual serializer is `von_hammer.rs`. Consider deleting. |
| `FrontendRust/src/von/ast.rs` | `IVonData` + `VonObject`/`VonArray`/`VonMember`/`VonStr`/`VonInt`/`VonBool`/`VonFloat` — heap-owned, no lifetimes |
| `FrontendRust/src/testvm/vivem.rs` | `execute_with_primitive_args` / `execute_with_heap` — testvm entry that reads `&'h ProgramH` |
| `Frontend/SimplifyingPass/src/dev/vale/simplifying/` | Scala source of truth |
