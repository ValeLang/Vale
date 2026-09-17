# TestVM

Conventions for the in-process Vale VM used by FrontendRust's end-to-end tests (`FrontendRust/src/testvm/`).

## Lifetimes

Every testvm struct, enum, and fn carries three lifetimes:

```rust
<'v, 'h, 's> where 's: 'h, 'h: 'v
```

- `'v` — vivem-pass (per-execution): per-call frames, expression-id paths, mutable VM state.
- `'h` — heap (per-program): allocations, referrers, the heap itself.
- `'s` — scout (per-compilation): names, runes, AST-derived data flowed in from earlier passes.

Bounds `'s: 'h, 'h: 'v` express the outlives chain (scout outlives heap outlives vivem). Types parameterized but not yet using a lifetime carry `PhantomData` for it.

## Naming

V-suffix names disambiguate testvm types from their compiler-pass counterparts:

- `HeapV`, `CallIdV`, `VoidV`, `IntV`, `BoolV`, `FloatV`, `StrV`, `OpaqueV`
- `StructInstanceV`, `ArrayInstanceV`
- `NodeContinueV`, `NodeReturnV`, `NodeBreakV`
- `VmRuntimeErrorV`

## Value-type embed-by-value

Small `Copy`-able value-types embed by value in containing enums; only genuinely allocated payloads stay `&'v`.

Embed by value: `VoidV`, `IntV`, `BoolV`, `FloatV`, `StrV`, `OpaqueV`, `NodeContinueV`, `NodeReturnV`, `NodeBreakV`.

Stay `&'v`: `StructInstanceV`, `ArrayInstanceV`.

Value-types that flow into HashMap keys (referrers, address-types) also derive `Hash, Eq, PartialEq` — apply the cascade through `Variable` / `Member` / `Element` / `Argument` / `ExpressionId` address structs as needed.

## Mutation

Plain `HashMap` / `Vec` + `&mut self` for mutators. No `Cell` / `RefCell` interior mutability. Compile-time borrow checking only.

## Writer threading

`HeapV` owns a single `vivem_dout: &'v mut dyn std::io::Write`. Pass to helpers via `&mut heap.vivem_dout`. No `Box<dyn Write>` wrapper unless the caller genuinely needs to own the writer.
