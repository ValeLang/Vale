# Reasoning: Output Data Must Be Ref or Copy

Output data (AST nodes, rules, names, types — everything produced by a compiler pass) must be either arena-allocated and accessed by reference, or Copy and stored inline. Never Clone. Never heap collections.

## The constraint

No output type may derive Clone. No output type may contain `Vec`, `HashMap`, `Box`, `String`, or any other heap-allocated collection. Every field in an output type is either a `&'s`/`&'p` arena reference, a `&'s [T]` arena slice, or a Copy value.

## Why

The primary goal is **performance**: prevent any accidental expensive heap duplication. In Scala, the JVM's garbage collector handled sharing transparently — passing a `case class` around just copied a pointer. In Rust, `Clone` on a type with heap fields does a deep copy. A single `.clone()` on a struct containing a `Vec<IRulexSR>` copies every element. If that struct is passed around in a loop (as happens during type solving), the cost compounds.

The ref-or-Copy rule eliminates this class of bug entirely:
- **Arena refs** (`&'s StructS`) are pointer-sized and Copy. Passing them around is free.
- **Copy types** (`RangeS`, `StrI`, attribute enums) are small and memcpy'd. No heap allocation, no destructor.
- **Arena slices** (`&'s [IRulexSR]`) are pointer+length, also Copy. The data lives in the arena.

This also gives flexibility to choose storage strategy without changing the type's API:
- Small types (like `ICitizenAttributeS` variants — a few Copy fields each) can be stored inline in arena slices.
- Large types (like `StructS` — many fields including nested slices) are arena-allocated behind `&'s` pointers.

Both are Copy. Both are free to pass around. The choice is about cache locality and allocation granularity, not ownership semantics.

## What about working state?

Working state (scopes, environments, builders, solver state) is exempt. It lives on the stack/heap, may contain `Vec`/`HashMap`, and may derive Clone. These types are mutable during a pass and are not part of the output. Moving them off the heap (persistent data structures, Rc, arena-backed collections) is a future refactor goal, not a current requirement.

## What Scala had

Scala had no distinction. All data was heap-allocated, garbage-collected, and shared by reference. The JVM made Clone unnecessary — everything was implicitly shared. Rust forces us to be explicit about sharing vs copying, and the ref-or-Copy rule is how we get Scala's sharing semantics back without GC overhead.
