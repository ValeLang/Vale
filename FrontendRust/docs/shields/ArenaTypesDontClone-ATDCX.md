---
description: Output data must be Copy or behind &'s — Clone-without-Copy on output data is a smell.
g_model: SimpleSmall
g_context: definition
g_assumes: TFITCX
g_when_mentioned: "Arena-allocated"
---

# Arena Types Don't Clone (ATDCX)

Output data (AST nodes, rules, names, attributes) must be either **Copy** (stored inline, trivially cheap) or **behind `&'s`/`&'p`** (arena-allocated, shared by reference, never duplicated). Clone-without-Copy on output data means something is wrong. Rust requires `Clone` as a supertrait of `Copy`, so Copy types having Clone is fine — the concern is only Clone *without* Copy.

Arena-allocated types (`StructS`, `FunctionS`, `IExpressionSE` variants, `StructA`, `FunctionA`, parser AST nodes, etc.) must NOT derive Clone. Copy types (`StrI`, `IRuneS`, `RangeS`, `CodeLocationS`, `RuneUsage`, attribute/body/member enums) derive Copy+Clone.

Working state (`StackFrame`, `EnvironmentS`, `FunctionEnvironmentS`, `VariableDeclarations`, solver types) may have Clone-without-Copy — they live on the heap and clone for scope forking.

## Examples

**DENY:**
```rust
#[derive(Clone, Debug)]  // Clone without Copy on arena-allocated output
pub struct StructS<'s> {
    pub name: INameS<'s>,
    pub attributes: &'s [ICitizenAttributeS<'s>],
}
```

**DENY:**
```rust
let copy = struct_s.clone();  // cloning arena-allocated output data
```

**ALLOW:**
```rust
#[derive(Copy, Clone, Debug)]  // Copy+Clone on small value type
pub struct RangeS<'s> {
    pub begin: CodeLocationS<'s>,
    pub end: CodeLocationS<'s>,
}
```

**ALLOW:**
```rust
#[derive(Debug)]  // No Clone on arena-allocated type
pub struct StructS<'s> {
    pub name: INameS<'s>,
    pub attributes: &'s [ICitizenAttributeS<'s>],
}
```

**ALLOW:**
```rust
// Working state — Clone is acceptable
let new_frame = stack_frame.clone();
```

## Exceptions

A. `ProgramS` — currently Clone-without-Copy because `EnvironmentA` stores `PackageCoordinateMap<ProgramS>` by value.
