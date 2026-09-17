# Investigation: "Test returning empty seq" (Category G #1)

## Test

`Frontend/IntegrationTests/test/dev/vale/AfterRegionsIntegrationTests.scala:51`

```vale
export () as Tup0;
exported func main() () { return (); }
```

## Original failure

`Couldn't solve some runes: _2112` — originates in `HigherTypingPass`'s
`RuneTypeSolver` (stack trace at
`HigherTypingCompilation.expectAstrouts`, `HigherTypingPass.scala:833`),
*not* the typing-pass solver.

## Root cause

`()` lowered (via `TemplexScout.scala`'s `TuplePT` case, lines 265-281
pre-fix) to a pair:

```
MaybeCoercingLookupSR(_2112, "Tup0")
MaybeCoercingCallSR(_2111, _2112, Vector())
```

`RuneTypeSolver`'s rule puzzles:

- `MaybeCoercingLookupSR` (lines 131-140): needs the lookup rune known.
- `MaybeCoercingCallSR` (lines 151-156): needs both `resultRune` and
  `templateRune` known.

Only `_2111` was seeded (by `translateExport` at `HigherTypingPass.scala:579`
as `KindTemplataType()`); `_2112` had no source of its shape. The
pre-processing pass at `RuneTypeSolver.scala:421-447` would normally seed
`_2112` from `env.lookup("Tup0")`, but the carve-out at line 441
deliberately skips seeding when the looked-up templata type is
`TemplateTemplataType(Vector(), KindTemplataType())` — exactly `Tup0`'s
signature. The comment explains: "we don't know whether we'll coerce this
into a kind or a coord."

Non-empty tuples (`Tup1<int>`, `Tup2<int,int>`, …) don't hit the carve-out
because their template type has a non-empty arg list, matching line 444
instead. They get seeded and the call rule fires.

`Tup0` is the only zero-arg kind template used via `TuplePT`'s lowering in
existing passing-or-failing tests, so this deadlock is unique to empty
tuples.

## Options considered

1. **Bidirectional puzzle on `MaybeCoercingCallSR`** — broken in general by
   Kind→Coord coercion on args: given `argRune : Kind`, you can't tell
   whether the template param is `Kind` (identity) or `Coord` (coerced).
2. **`TemplateComponentsSR` analogous to `CoordComponentsSR` /
   `PrototypeComponentsSR`** — same coercion problem. Templates aren't
   three-way lossless relations like coords are.
3. **Pre-processor cross-rule context (Version A)** — detect that a lookup
   rune appears in a `MaybeCoercingCallSR`'s `templateRune` slot, then
   safely seed from `env.lookup`. General but adds new machinery.
4. **Structural `IsTemplateSR` rule (Version B)** — explicit first-class
   structural fact. Most principled; biggest refactor.
5. **Complex-solve / speculative retry** — mirrors typing-pass solver's
   `incrementallySolve`. Infrastructure-heavy.
6. **Simpler empty-tuple lowering (chosen)** — for empty arity, emit a
   single `MaybeCoercingLookupSR` where the lookup rune *is* the result
   rune, skipping the call rule. Matches how bare zero-arg kind templates
   like `Spaceship` are already handled (single `MaybeCoercingLookupSR`,
   implicit-call arm at `RuneTypeSolver.scala:345-349` does the zero-arg
   call automatically).

The chosen fix matches the codebase's existing pattern rather than adding
new pattern.

## Fix applied

`Frontend/PostParsingPass/src/dev/vale/postparsing/rules/TemplexScout.scala`
— `TuplePT` case branches on `elements.isEmpty`:

- Empty: emit a single `MaybeCoercingLookupSR(rangeS, resultRuneS,
  Tup0)`. No separate `templateRuneS`, no `MaybeCoercingCallSR`.
- Non-empty: unchanged two-rule emission.

`PackPT` is only constructed by `ParsedLoader` (from serialized JSON
parse trees used in tests), never by the live parser, so no empty-arity
`PackPT` reaches the rune-type solver in real compilations. Left alone.

## Results

**Solver deadlock fixed.** `HigherTypingPass` now completes for this
program. No regressions in the regression sweep:

- `IntegrationTests` (A/B/C) + `TupleTests`: 92/92 pass.
- Full `sbt test`: 1064/1087 pass, 23/1087 fail. Every failing test is
  pre-existing and listed in `quest.md`'s remaining categories (A/B/F/G/H/J).

**Test still fails — now for a different reason.** `compile.run(Vector())`
in the test hits a VM-level memory leak during heap check. The trace shows
`main` allocating a `Tup0{}` struct (`o502`), returning it, the VM
discarding the return ref (`ExpressionVivem.discard`), refcount going to 0
— but the allocation remains in the map:

```
Ending program o502rc1->0
Checking for leaks
o502
```

This is a distinct, pre-existing bug in the test VM's drop/dealloc path
for zero-member structs. It was invisible before because no previously
passing test exercised "function returning an empty tuple" — the solver
deadlock blocked compilation.

## Files modified

- `Frontend/PostParsingPass/src/dev/vale/postparsing/rules/TemplexScout.scala`
  — `TuplePT` case, empty-arity branch.

## Verification

- Target test goes from "Couldn't solve some runes" at higher-typing to
  "Memory leaks!" at VM runtime — compilation fully succeeds, a later
  distinct issue surfaces.
- `sbt test`: same 23 tests fail as before (all pre-existing, per
  `quest.md`). No new failures introduced.

## Status

- **Solver fix: done.** The Category G #1 root cause ("Couldn't solve
  `_2112` for empty tuple `Tup0`") is resolved.
- **VM leak follow-up: fixed.** See
  `investigations/test_returning_empty_seq_vm_leak.md`. The VM leak
  (Vivem's `OwnH` cleanup branch does nothing at end-of-program) was
  resolved by marking `Tup0` as `imm` in `tup0.vale`, making its return
  reference Share and routing it through the existing StructHT
  destructure+dealloc path.
- **Test passes end-to-end.** Full `sbt test`: 1065 pass / 22 fail. The
  "Test returning empty seq" is no longer in the failing list; all 22
  remaining failures are pre-existing.
