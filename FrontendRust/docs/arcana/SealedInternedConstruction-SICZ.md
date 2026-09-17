# Sealed Interned Construction (SICZ)

Every TFITCX-Interned type has a `pub _must_intern: MustIntern` field whose tuple constructor is private to `typing_interner.rs`. External code cannot write `IdT { ..., _must_intern: MustIntern(()) }` — the unit field's constructor is inaccessible — so the only way to obtain an `IdT` is via the corresponding `intern_*` method on `TypingInterner`.

This makes the TFITCX-Interned category compiler-enforced rather than discipline-enforced. "Interned" stops meaning "the author intended this to come from the interner" and starts meaning "every instance in the program demonstrably came from the interner."

The seal exists because Interned types' equality semantics depend on it. `IdT::eq` compares the `init_steps` slice via `std::ptr::eq`, which is correct only if the slice came from the canonical arena allocation inside `intern_id`. An un-interned `IdT { init_steps: [...] }` literal would have a different slice pointer than the canonical one, and the assertion `header.to_signature().id == needle_signature.id` would silently fail. We hit exactly this bug before sealing — `assemble_name` was constructing un-interned `IdT`s — and the fix was sealing.

**How this affects authoring code:** to construct an `IdT`, any of the 15 Val-keyed Name types, or the 5 Scala-interned kind types (`StructTT`, `InterfaceTT`, `StaticSizedArrayTT`, `RuntimeSizedArrayTT`, `OverloadSetT`), you must call the corresponding `intern_*` method on `TypingInterner`. Constructing a struct literal anywhere else fails with E0423 "constructor is not visible here due to private fields."

**Interactions with @WVSBIZ and @DSAUIMZ:** WVSBIZ defines *which* values become Interned. SICZ defines *how* the rule is enforced (privacy on the witness field). DSAUIMZ governs what happens *inside* an `intern_*` method when the value contains a slice — the slice is arena-allocated only on a miss.

**Where:** `MustIntern(())` is defined at the top of `FrontendRust/src/typing/typing_interner.rs`. The unit tuple field is private; only methods in that module can construct one. Adding a new Interned type means adding `pub _must_intern: MustIntern` to its definition and filling the field inside an `intern_*` method that returns `&'t Self`.
