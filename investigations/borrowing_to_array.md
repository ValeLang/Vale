# Borrowing toArray — Investigation

## Reproduction

```bash
cd Frontend
sbt 'testOnly dev.vale.AfterRegionsIntegrationTests -- -t "Borrowing toArray"' >/tmp/borrowing-toarray.txt 2>&1
```

Test code (`AfterRegionsIntegrationTests.scala:199`):

```vale
func toArray<E>(list &List<E>) []<mut>&E {
  return []&E(list.len(), { list.get(_) });
}
```

Error (end of trace):

```
Couldn't solve some runes: _51111111111, E
Steps:
Supplied:
  added rule: inherit E
  added rule: _51111111111 = &E
  added rule: _511111111211 = mut
  added rule: _5111111113 = final
inherit E
_511111111211 = mut
  _511111111211: mut
_5111111113 = final
  _5111111113: final
(complex)
Unsolved rule: _51111111111 = &E
Unsolved runes: _51111111111 E
```

Key signal: `inherit E` appears in the fired-rules list but has **no conclusion indented beneath it**
(compare to `_511111111211 = mut` with `_511111111211: mut` under it). The rule fires but produces
no value for `E`. Then the `AugmentSR` rule `_51111111111 = &E` can't fire because `E` is unknown.

## Root cause

### The "MKRFA" contract

`RuneParentEnvLookupSR` ("inherit X") rules are meant to be **preprocessed out** at call sites before
being handed to the inferCompiler solver. The contract has two parts:

1. Search `callingEnv` for the rune's name; turn the found templata into an `InitialKnown`.
2. Strip the `RuneParentEnvLookupSR` rule from the rule set before solving.

The typing-pass solver handler for `RuneParentEnvLookupSR` (`CompilerSolver.scala:852-855`) is
explicitly a **no-op**:

```scala
case RuneParentEnvLookupSR(range, rune) => {
  // This rule does nothing, it was actually preprocessed.
  solverState.commitStep[ITypingPassSolverError](
    false, Vector(ruleIndex), Map(), Vector()) match { ... }
}
```

It commits an empty conclusion map. So if the preprocessing didn't happen, the rule fires harmlessly
and `rune` never gets a value — which is exactly the observed failure.

The canonical preprocessing site is `OverloadResolver.scala:311-325`:

```scala
// We preprocess out the rune parent env lookups, see MKRFA.
val (initialKnowns, rulesWithoutRuneParentEnvLookups) =
  rulesWithoutImplicitCoercionsA.foldLeft((Vector[InitialKnown](), Vector[IRulexSR]()))({
    case ((previousConclusions, remainingRules), RuneParentEnvLookupSR(_, rune)) => {
      val templata =
        vassertSome(
          callingEnv.lookupNearestWithImpreciseName(
            interner.intern(RuneNameS(rune.rune)), Set(TemplataLookupContext)))
      val newConclusions = previousConclusions :+ InitialKnown(rune, templata)
      (newConclusions, remainingRules)
    }
    case ((previousConclusions, remainingRules), rule) => {
      (previousConclusions, remainingRules :+ rule)
    }
  })
```

### Where the contract is violated

`ArrayCompiler.evaluateRuntimeSizedArrayFromCallable` (the handler for `[]<mut>&E(size, callable)`)
receives rules that contain a `RuneParentEnvLookupSR(E)` (emitted by `&E`'s postparser lowering). It
runs the rune-type solver, `explicifyLookups`, then builds a solver directly:

```scala
// ArrayCompiler.scala:179
val initialKnowns = Vector()
...
// ArrayCompiler.scala:192-194
val solver =
  inferCompiler.makeSolver(
    envs, coutputs, rules, runeToType, invocationRange, initialKnowns, initialSends)
```

No MKRFA preprocessing step. The `RuneParentEnvLookupSR(E)` rule survives into the solver, fires
as a no-op, and `E` never gets seeded from `callingEnv`. `_51111111111 = &E` then stalls.

`ArrayCompiler.evaluateStaticSizedArrayFromValues` (for `[#](…)` and `#[N](…)` paths) has the same
shape — `initialKnowns = Vector()` at line 369, no preprocessing — so the related
"Make array without type" and "Call Array<> without element type" failures likely share this root
cause (though they may have additional missing pieces on top).

## Why `inherit E` appears as "fired" in the trace

The solver handler at line 852 commits a step with empty conclusions — so in the trace it looks like
the rule ran. But because the step added nothing to `conclusions`, `E` remains unknown. The missing
indented conclusion under `inherit E` in the trace output is the tell.

## Fix

Apply the MKRFA preprocessing in `ArrayCompiler.evaluateRuntimeSizedArrayFromCallable` (and
`evaluateStaticSizedArrayFromValues`) before calling `makeSolver`. Same shape as
`OverloadResolver.scala:311-325`:

- Fold over `rulesA`, splitting `RuneParentEnvLookupSR` rules out.
- For each, look the rune name up in `callingEnv` and produce an `InitialKnown`.
- Pass the filtered rules + `initialKnowns` to `makeSolver`.

## Alternatives considered

1. **Make the `RuneParentEnvLookupSR` solver handler do the lookup itself.** This would centralize
   the behavior and remove the MKRFA contract. But the solver handler doesn't naturally have access
   to `callingEnv` the way the preprocessing site does, and `OverloadResolver` + `FunctionScout` already
   rely on the preprocessing model (including the "remove the rule entirely" variant in
   `FunctionScout.scala:530-542` for interface parent methods). Changing the solver contract would
   ripple; adding one call site is localized.

2. **Filter the rules in the postparser so `RuneParentEnvLookupSR` never reaches ArrayCompiler.**
   The rule *is* needed at the call site — it tells the solver what to look up. Dropping it earlier
   would break the rune-type solver's ability to know that `E` is expected to have a Coord/Kind type.
   Preprocessing must happen at the boundary where `callingEnv` is available.

## Verification / actual result

Ran the full test suite after applying the fix:

- **22 failing before, 22 failing after.** No regressions.
- `toArray`'s body now compiles successfully. Verified via targeted printlns:
  - Element-type rune resolves to `CoordT(borrow, ..., KindPlaceholderT(E))` ✓
  - Array constructor prototype's return type is `Array<mut, &E>` with the borrow preserved ✓
- The test still fails, but for **two additional, independent issues** revealed by the
  progress. Both are in the typing pass, not the instantiator (my earlier instantiator guess
  was wrong — no `translateCoord` calls fire before the failure).

### Secondary issue A: `AugmentSR` collapses `&T` on immutable `T`

When `l.toArray()` resolves with `E := int`, the solver fires `AugmentSR(outer, BorrowP, E)`
via the inner-known path at `CompilerSolver.scala:891-917`. There:

```scala
delegate.getMutability(state, innerCoord.kind) match {
  case MutabilityTemplataT(ImmutableT) => innerCoord.ownership   // ← drops augment
  case PlaceholderTemplataT(_, MutabilityTemplataType()) => Conversions.evaluateOwnership(augmentOwnership)
  case MutabilityTemplataT(MutableT) => Conversions.evaluateOwnership(augmentOwnership)
}
```

When the kind is immutable (int), the handler **deliberately discards the augment** and keeps
the inner's ownership. The return coord resolves to `Array<mut, share int>`, not
`Array<mut, borrow int>`. This is pre-regions Vale semantics ("you can't borrow an immutable;
it's just share"), but the test name is literally "Borrowing toArray" — preserving the borrow
even on immutable elements is the regions-feature behavior the test is waiting on.

Debug-verified: `getMaybeReturnType` for `toArray[E=own int]` produces
```
CoordT(own, RuntimeSizedArrayTT(mut, CoordT(share, IntT(32)), RegionT()))
```
— share, not borrow.

### Secondary issue B: no `get` function for arrays

Even if issue A were resolved, `l.toArray().get(1)` is UFCS for `get(arr, 1)` — and the
stdlib has no `func get` for `Array<...>` (Vale's canonical syntax is `arr[i]`; no `get` file
in `Frontend/Tests/test/main/resources/array/`). The 7 rejection candidates in the error are
all `List.get` and `Some.get`; none take an array as first param. The test either needs a
stdlib `Array.get`, or the test body needs to use `[1]` indexing.

### Implications for categorization

The test is not category-H blocked. My earlier update to `quest.md` and `handoff.md` was
wrong on that point. The actual remaining blockers are (A) a category-G-flavored typing-pass
`AugmentSR` semantics issue and (B) a missing stdlib function / test mismatch.

## Handoff follow-ups

- "Make array without type" and "Call Array<> without element type" still fail (category G)
  but now benefit from the same MKRFA preprocessing. They likely need element-type inference
  from the generator lambda — a separate feature gap, not an MKRFA issue. Investigate
  separately if prioritized.
- Correct `quest.md`: "Borrowing toArray" is not category-H-blocked; it's blocked on
  `AugmentSR`'s immutable-element coercion plus a missing `Array.get`.
- Correct `handoff.md`: the "Recent example" and the category-G section should point at
  `CompilerSolver.scala:900` (the `ImmutableT => innerCoord.ownership` case) and the
  `Array.get` absence, not at `Instantiator.scala:3174`.
- Follow-up refactoring: extract the MKRFA fold into an `InferCompiler` helper (now 4 inline
  copies) and consider tightening `CompilerSolver.scala:852` from a no-op to `vwat()` to
  surface future MKRFA violations loudly.
