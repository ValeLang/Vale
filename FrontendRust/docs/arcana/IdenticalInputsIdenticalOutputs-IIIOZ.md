# Identical Inputs, Identical Outputs (IIIOZ)

Sylvan's compiler must be deterministic across runs: same inputs → byte-identical outputs (artifacts, error text, panic locations, debug dumps). Stronger than "correct" — we commit to *which* correct output.

Most language defaults break this. Known enemies, and our stance:

**Randomly-seeded `HashMap`/`HashSet`.** Iteration order changes per process. Use `IndexMap`/`IndexSet`/`ArenaIndexMap` whenever iteration flows into output. Lookup-only and cardinality-only `HashMap` is fine. Prefer insertion-ordered containers over sort-on-iterate (faster).

**Pointer addresses (ASLR).** Never let `*const`, `as usize`, `{:p}` reach output (`Debug`, `Display`, panics, errors). Pointer-identity comparisons (`ptr::eq`, interner `ptr_eq`) are fine — they compare, they don't read.

**Threading, RNGs, system clock, atomics, `read_dir` order.** All forbidden in compiler logic. The frontend is single-threaded by design.

**How this affects writing code:** Reaching for `HashMap` whose `.iter()` feeds anywhere downstream? Use `IndexMap` instead. Populating an `IndexMap` from `HashMap.iter()` freezes random order into a stable container — fix the upstream HashMap too. Every `IndexMap`-from-`HashMap` conversion is a latent determinism bug.
