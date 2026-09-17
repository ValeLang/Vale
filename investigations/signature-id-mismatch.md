# Investigation: header.to_signature().id == needle_signature.id assertion failure

## Symptom

`simple_program_returning_an_int_explicit` test panics at `function_compiler_middle_layer.rs#getOrEvaluateFunctionForHeader` with:
```
assertion failed: header_sig.id == needle_signature.id
```

## Collapsed call tree

- `getOrEvaluateFunctionForHeader()` ... `assemble_name()`:
  Builds `function_id: IdT` with `init_steps` pointing to `rued_env.id.init_steps` (an empty slice at addr A).

- `getOrEvaluateFunctionForHeader()` ... `intern_signature()` ... `intern_id()`:
  Creates `needle_signature`. `intern_id` calls `bump.alloc_slice_copy(init_steps)`, allocating a **new empty slice at addr B**.
  So `needle_signature.id.init_steps` → addr B.

- `getOrEvaluateFunctionForHeader()` ... `make_named_env()` ... `assemble_name()`:
  Builds `named_env.id: IdT` with `init_steps` still pointing to `rued_env.id.init_steps` (addr A).
  This becomes `full_env.id`.

- `evaluate_function_for_header_core()` ... `finalize_header()`:
  Header gets `id = full_env.id`, so `header.id.init_steps` → addr A.

- `getOrEvaluateFunctionForHeader()` assertion:
  Compares `header.to_signature().id` (init_steps at addr A) vs `needle_signature.id` (init_steps at addr B).
  `IdT::eq` uses `std::ptr::eq` on `init_steps.as_ptr()` → **false**, even though both are empty slices.

## Root cause

`IdT::eq` (names.rs:184-189) compares `init_steps` by pointer equality:
```rust
std::ptr::eq(self.init_steps.as_ptr(), other.init_steps.as_ptr())
```

When `intern_id` is called, it allocates a fresh copy of `init_steps` via `bump.alloc_slice_copy()`. This produces a new pointer, even for empty slices. The un-interned `IdT` (in the header) still points to the original slice.

The assertion compares an **interned** `SignatureT` (needle) against an **un-interned** `SignatureT` (from `header.to_signature()`). The un-interned one was never passed through `intern_id`, so its `init_steps` is at a different address.

## Debug output

```
header_sig.id.init_steps ptr=0x141005148 len=0
needle_sig.id.init_steps ptr=0x14081bd58 len=0
header_sig.id.local_name == needle_sig.id.local_name: true
header_sig.id.package_coord ptr_eq: true
init_steps ptr_eq: false
```

## Possible fixes

1. **Intern the header's signature before comparing.** Change the assertion to:
   ```rust
   let header_sig = self.typing_interner.intern_signature(SignatureValT {
       id: IdValT { package_coord: header.id.package_coord, init_steps: header.id.init_steps, local_name: header.id.local_name },
   });
   assert!(std::ptr::eq(header_sig, needle_signature));
   ```

2. **Canonicalize empty init_steps.** Make the interner reuse a single empty slice for all empty `init_steps`, so pointer equality holds for `[]` == `[]`.

3. **Make the header's id go through interning.** In `finalize_header` or `evaluate_function_for_header_core`, intern `full_env.id` before storing it in the header.

4. **Change `IdT::eq` to compare init_steps by value instead of pointer.** This would break the intended interning semantics though.

Fix 1 is the most surgical — it matches the Scala semantics (value comparison of case classes) without changing any invariants.
