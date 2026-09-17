---
description: Arena-allocated structs must use arena slices and ArenaIndexMap, not Vec, HashMap, or String.
g_model: SimpleSmall
g_context: definition
g_defs: struct
g_assumes: TFITCX
g_when_mentioned: "Arena-allocated"
---

# Arena-Allocated Structs Should Not Contain Malloc'd Collections (AASSNCMCX)

Arena-allocated structs (those stored as `&'s T` via `bumpalo::Bump::alloc`) must not contain `Vec`, `HashMap`, or `String` fields. Bumpalo does not run destructors, so a `Vec<T>` inside an arena-allocated struct leaks its heap allocation when the arena drops.

Use `&'s [T]` (via `alloc_slice_from_vec()`) and `ArenaIndexMap<'s, K, V>` (via `ArenaIndexMap::from_iter_in()`) instead.

## Examples

**DENY:**
```rust
pub struct StructS<'s> {
    pub rules: Vec<IRulexSR<'s>>,  // heap-allocated inside arena struct
    pub rune_to_type: HashMap<IRuneS<'s>, ITemplataType>,  // heap-allocated
}
```

**ALLOW:**
```rust
pub struct StructS<'s> {
    pub rules: &'s [IRulexSR<'s>],  // arena slice
    pub rune_to_type: ArenaIndexMap<'s, IRuneS<'s>, ITemplataType>,  // arena map
}
```

**ALLOW:**
```rust
// Working accumulators are NOT arena-allocated — Vec/HashMap is fine here
pub struct Astrouts<'s> {
    pub structs: Vec<&'s StructA<'s>>,
    pub code_location_to_maybe_type: HashMap<CodeLocationS<'s>, Option<ITemplataType>>,
}
```

## Clarifications

* `ArenaIndexMap` is the arena-friendly replacement for `HashMap` in frozen output structs.
* Builder types, error types, and working accumulators (`Astrouts`, `EnvironmentS`, `StackFrame`) are NOT arena-allocated and may contain `Vec`/`HashMap`.
