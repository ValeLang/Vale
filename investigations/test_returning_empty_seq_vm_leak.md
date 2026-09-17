# Investigation: VM leak for `main()` returning `Tup0`

## Context

After fixing the `RuneTypeSolver` deadlock for empty tuples (see
`test_returning_empty_seq.md`), `AfterRegionsIntegrationTests.scala:51`
now compiles successfully but fails at runtime:

```
dev.vale.VAssertionFailException: Memory leaks!
  at dev.vale.testvm.AllocationMap.checkForLeaks(Heap.scala:114)
  ...
  at dev.vale.RunCompilation.run(RunCompilation.scala:69)
```

This investigation isolates the cause. **No fix applied.**

## Reproduction

```bash
cd Frontend
sbt 'testOnly dev.vale.AfterRegionsIntegrationTests -- -t "Test returning empty seq"'
```

Test program:

```vale
export () as Tup0;
exported func main() () { return (); }
```

## Diagnostic prints

Temporarily added two `println` sites:

1. `Frontend/TestVM/src/dev/vale/testvm/ExpressionVivem.scala:1207-1209`
   (inside `cleanup`) — log `allocId`, `ownership`, `kind`, plus which
   match arm was taken.
2. `Frontend/TestVM/src/dev/vale/testvm/Heap.scala:347` (inside
   `deallocateIfNoWeakRefs`) — log whether the allocation was removed or
   retained, and the remaining refcount on retention.

Prints removed after investigation (verified with `git diff --stat`
showing zero lines changed on those two files).

## Observed output

```
Ending program o502rc1->0[leak-dbg] cleanup entered: allocId=502 ownership=OwnH kind=StructHT(IdH(Tup0,PackageCoordinate(StrI(),Vector()),Tup0,Tup0))
[leak-dbg] OwnH branch: NOT deallocating alloc=502
```

## Root cause

`ExpressionVivem.cleanup` (lines 1197-1268) is the end-of-program
deallocation path invoked by `Vivem.innerExecute:106` via
`ExpressionVivem.discard`. When the reference's refcount reaches zero,
it dispatches on `expectedReference.ownership`:

```scala
case OwnH => // Do nothing, Vivem often discards owning things,
             // if we're making a new owning reference to it.
case WeakH => heap.deallocateIfNoWeakRefs(...)
case MutableBorrowH | ImmutableBorrowH => // Do nothing.
case MutableShareH | ImmutableShareH => {
  expectedReference.kind match {
    case VoidHT() | IntHT(_) | ... => zero + dealloc
    case StructHT(_) => destructure (recursive cleanup + dealloc)
    ...
  }
}
```

The returned `Tup0` instance has ownership `OwnH`, so cleanup takes the
`OwnH` branch and does nothing — the allocation stays in
`AllocationMap.objectsById`, and `checkForLeaks` at program end fails.

Why `OwnH`? `Tup0` is declared as
`Frontend/Builtins/src/dev/vale/resources/tup0.vale`:

```vale
struct Tup0 { }
```

A bare `struct` is Mutable by default in Vale, so `Tup0` is a mutable
struct. Mutable structs use Own/Borrow references (not Share). When
`main() ()` returns a freshly-constructed `Tup0{}`, the return
reference is `OwnH`.

Contrast with `Tup1` / `Tup2` (used for non-empty tuples) — those are
also declared as mutable structs, but non-empty tuples never get
returned from `main` in any test; they're only used internally, where
the compiler inserts explicit `drop` calls (defined in `tup1.vale`,
`tup2.vale` with `#!DeriveStructDrop` and a custom `drop`). The
internal-use path does not go through Vivem's end-of-program cleanup,
so the `OwnH` "do nothing" behavior is harmless there.

## Why this surfaces only now

Grepping `func main() <T>` in all `.vale` programs under
`Frontend/Tests/.../programs`:

```
programs/**/*.vale : every exported main returns `int` (or `void`).
```

Zero passing tests return a user-defined struct from `main`. The
Vivem `OwnH` "do nothing" cleanup is untested for that case. The
solver deadlock previously prevented compilation from even reaching
Vivem for this specific test, masking the cleanup gap.

## Relevant files

- `Frontend/TestVM/src/dev/vale/testvm/ExpressionVivem.scala:1197-1268` —
  `cleanup`. The `OwnH` arm at line 1209 is the direct source of the
  leak.
- `Frontend/TestVM/src/dev/vale/testvm/Vivem.scala:99-111` —
  `innerExecute` runs `main`, discards the return ref, then calls
  `heap.checkForLeaks()`.
- `Frontend/TestVM/src/dev/vale/testvm/Heap.scala:104-115` —
  `AllocationMap.checkForLeaks` emits "Memory leaks!".
- `Frontend/TestVM/src/dev/vale/testvm/Heap.scala:341-351` —
  `deallocateIfNoWeakRefs`, the function Share struct cleanup uses but
  Own cleanup does not.
- `Frontend/Builtins/src/dev/vale/resources/tup0.vale` — `struct Tup0 { }`
  with no `imm`, no `#!DeriveStructDrop`, no custom `drop`.
- `Frontend/Builtins/src/dev/vale/resources/tup1.vale`,
  `tup2.vale`, `tupN.vale` — all have `#!DeriveStructDrop` plus
  custom `drop`.

## Candidate fix directions (for user choice, NOT applied)

1. **Mark `Tup0` as `imm`**. `struct Tup0 imm { }` (or equivalent
   syntax) makes its references Share. Cleanup's Share + `StructHT`
   arm then destructures (no members for Tup0) and deallocates.
   Smallest conceptual change; semantically defensible since empty
   tuples carry no mutable state. Risk: need to verify the typing
   pass / exports path is happy with `Tup0` being Share (in
   particular, `export () as Tup0` + the rune-type solver's
   implicit-call path).

2. **Make Vivem's `OwnH` cleanup branch invoke the struct's drop
   function**. This matches real Vale semantics: an Own ref going
   out of scope should call the user-defined drop. Today the VM's
   comment ("Vivem often discards owning things, if we're making a
   new owning reference to it") is pragmatic but incomplete — at
   end-of-program there's no new owning reference taking over. This
   is the architecturally correct fix. Risk: larger VM change;
   requires looking up and invoking the correct drop prototype;
   could surface more latent leaks in other tests (potentially
   uncovering more bugs).

3. **Add an auto-generated / explicit `drop(Tup0) void` in
   `tup0.vale`** mirroring tup1/tup2. This at least gives the
   compiler a drop prototype to insert calls against. Doesn't help
   unless Vivem's cleanup actually invokes it for `OwnH`, so this
   is only a partial fix on its own.

4. **Change the test** to have `main` return `int` instead of `()`.
   Dodges the bug; not a real fix. Not recommended.

## Status

Root cause of the VM leak: **Vivem's end-of-program `cleanup` does
nothing for `OwnH` references, but `main() Tup0`'s return ref is
`OwnH` because `Tup0` is a mutable struct.**

## Fix applied (Option 1)

`Frontend/Builtins/src/dev/vale/resources/tup0.vale`:

```diff
-struct Tup0 { }
+struct Tup0 imm { }
```

This makes `Tup0`'s references use Share ownership. When `main() ()`
returns a `Tup0{}`, the test harness's `discard` now routes through
`ExpressionVivem.cleanup`'s `MutableShareH | ImmutableShareH` →
`StructHT` branch (line 1233), which destructures (no members, so no
recursive work) and calls `deallocateIfNoWeakRefs`. The allocation is
cleanly removed; leak check passes.

The broader VM `OwnH` cleanup gap (Option 2 in the original plan) is
left unaddressed. It's a latent issue that would only surface if
another test returned a user mutable struct from `main`; no current
or planned test does.

## Results

- Target test: `Test returning empty seq` passes end-to-end.
- Full `sbt test`: 1065/1087 pass, 22/1087 fail (one less than before;
  all 22 remaining failures are pre-existing).
- No regressions.
