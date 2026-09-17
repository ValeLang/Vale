# Handoff: "Borrowing toArray"

Welcome. You're about to try to fix an integration test that compiles **most** of
a function body but fails at a very specific step. The earlier investigation
(`investigations/borrowing_to_array.md`) already did the hardest part — it
diagnosed the root cause of the original stall and fixed it. What's left is two
smaller, more local problems that the earlier fix revealed. This handoff is
intentionally verbose because you may be new to this compiler. Don't skim; read
it end-to-end the first time.

Expect ~30 minutes to read, then ~1–2 days to finish the work.

## What test we're fixing

File: `Frontend/IntegrationTests/test/dev/vale/AfterRegionsIntegrationTests.scala:199`

```vale
import list.*;

func toArray<E>(list &List<E>) []<mut>&E {
  return []&E(list.len(), { list.get(_) });
}

exported func main() int {
  l = List<int>();
  add(&l, 5);
  add(&l, 9);
  add(&l, 7);
  return l.toArray().get(1);
}
```

It should return `VonInt(9)`. The test checks that you can take a list of values
and produce a runtime-sized array of **borrows** (`&E`) pointing into that list.

The test was originally stalling at a solver-inference step. That's fixed. Now
it fails later, for two reasons. We'll walk through both.

## How to run it

From the repo root (not `Frontend/`):

```bash
cd Frontend
sbt 'testOnly dev.vale.AfterRegionsIntegrationTests -- -t "Borrowing toArray"' 2>&1 | tee /tmp/fixing-borrowing-toarray.txt
```

**Important** (from the repo's `CLAUDE.md`): never pipe `sbt` output through
`| tail`, `| grep`, or `| head` in the same command. `sbt` output is slow and
expensive; redirect fully to a file with `tee`, then `grep`/`tail`/`head` the
file as a separate follow-up command. Re-reading the file is free; re-running
sbt is not.

```bash
grep -E "error|FAILED|SUCCESS" /tmp/fixing-borrowing-toarray.txt
tail -60 /tmp/fixing-borrowing-toarray.txt
```

Use **one file name for the whole session**. The one above is fine.

## Before you touch anything, read these

In order:

1. **`docs/Generics.md` sections SFWPRL (line 353) and STCMBDP (line 392)** —
   the "predict now, resolve later" architecture that governs how the solver
   decides when to verify function calls.
2. **`docs/arcana/EachCallSiteIsItsOwnSolve-ECSIIOSZ.md`** — explains that every
   call site in source code spins up its own fresh solver.
3. **`docs/arcana/BoundReturnResolution-BRRZ.md`** — a recently-added mechanism
   in the same territory; useful because it exercised many of the same files.
4. **`investigations/borrowing_to_array.md`** — the earlier investigation that
   got us here. Read the whole thing. Especially sections "Secondary issue A"
   and "Secondary issue B" — that's exactly what's left for you to do.

Don't skip these. If you work on category-G tests without the SFWPRL and
ECSIIOSZ context, you will make changes that break invariants you didn't know
were invariants.

## Vocabulary cheat sheet

You'll see these constantly:

- **Rune.** A named type variable the solver fills in. Written like `E`,
  `_51111111111`. Every rune has a *type* (is it a Coord? a Kind? an Int?) and
  eventually a *value* (like `CoordT(share, IntT)`).
- **Coord.** `CoordT(ownership, region, kind)`. A reference to a kind with a
  specific ownership (`own`, `borrow`, `share`, `weak`).
- **Kind.** A type like `IntT`, `StructTT`, `RuntimeSizedArrayTT`. Doesn't have
  ownership; you need a coord to use it.
- **Rule.** A constraint between runes. `AugmentSR(outer, &, inner)` says
  `outer = augmented(inner, borrow)`; `LookupSR(rune, name)` says `rune`
  equals whatever `name` resolves to.
- **Placeholder.** `KindPlaceholderT` — a stand-in for an unresolved generic
  param inside a function body. Different from a solver unknown; it means
  "the caller will fill this in at each call site."
- **Instantiator.** A later compiler pass that substitutes concrete types for
  placeholders once a generic function is actually called with specific
  types.
- **Stamp.** Verb: to monomorphize a generic function for specific types. Noun:
  the result.
- **SR / PE / SE / AE suffixes.** Scout-rule / parser-AST / postparser-AST /
  higher-typing-AST versions of a node.
- **`T` suffix.** Typing-pass type (`CoordT`, `StructTT`). **`I` suffix.**
  Instantiator-pass type.
- **UFCS.** Vale's `x.f(y)` sugar for `f(x, y)` — a free function named `f`
  whose first param matches `x`.

## What's fixed and what's left

### Fixed in the previous session

The `toArray` function **body** now compiles correctly. The generator lambda
`{ list.get(_) }` resolves to a prototype returning `&E` (a borrow of E). The
array constructor builds an `Array<mut, &E>` with the borrow preserved through
the solver. You can verify this with targeted printlns in `ArrayCompiler.scala`
around the `MutableT` branch.

That fix was about the "MKRFA" contract — a preprocessing step that turns
`RuneParentEnvLookupSR` rules into `InitialKnown`s before the solver runs.
Three `ArrayCompiler` methods were skipping this preprocessing, which meant
the rune `E` was never seeded from `callingEnv` and the solver stalled. See
`investigations/borrowing_to_array.md` for the full diagnosis and fix.

### Still broken: two separate issues

**Issue A (solver-level): `AugmentSR` collapses `&T` to `share T` when T is
immutable.**

The test's `main()` calls `l.toArray()` with `E := int`. When the solver
resolves that call, it fires `AugmentSR(outer, borrow, innerRune)` where
`innerRune` has value `int`. The handler at
`Frontend/TypingPass/src/dev/vale/typing/infer/CompilerSolver.scala:946-963`
says:

```scala
case AugmentSR(range, outerCoordRune, maybeAugmentOwnership, innerRune) => {
  ...
  case Some(augmentOwnership) => {
    delegate.getMutability(state, innerCoord.kind) match {
      case MutabilityTemplataT(ImmutableT) => innerCoord.ownership   // <-- this line
      case PlaceholderTemplataT(_, MutabilityTemplataType()) => Conversions.evaluateOwnership(augmentOwnership)
      case MutabilityTemplataT(MutableT) => Conversions.evaluateOwnership(augmentOwnership)
    }
  }
  ...
}
```

Translation: "if the inner type is immutable (like `int`), discard the borrow
augment and use the inner's existing ownership." Which for `int` means
`share` — not `borrow`.

This is **pre-regions Vale semantics**. Before the regions feature, you
literally couldn't borrow an immutable; every reference to an immutable was
`share`. The "Borrowing toArray" test name is a clue: the whole point of the
test is that with regions, you CAN have `&int` as a meaningful distinct type.

So the resolved return type becomes `Array<mut, share int>`, but the test
signature says `Array<mut, &E>`. Mismatch, overload resolution fails, test
fails.

**Issue B (stdlib gap): no `func get` for `Array<...>`.**

Even if Issue A were fixed, the last line of `main()` is
`return l.toArray().get(1);`. In Vale, `x.y(z)` is sugar for `y(x, z)` (UFCS).
So `foo.get(1)` is looking for a free function named `get` whose first
parameter accepts `Array<mut, ...>`.

The stdlib has no such function. Arrays use `arr[i]` indexing syntax as their
canonical access; there's no free `get(arr, i)`. The stdlib does have
`Opt.get` and `List.get`, which is what shows up in the current test
failure's rejected-candidates list — seven candidates, all the wrong shape.

So once Issue A is fixed, the test will shift to failing at `l.toArray().get(1)`
with a different error (can't find function `get`).

### Why these are two problems, not one

Issue A is a language-semantics decision. It affects every program that uses
a borrow of an immutable type. If you naively change the `ImmutableT` case to
preserve the augment, you will probably break other tests elsewhere.

Issue B is localized to `main()` and the stdlib. Fixing it has no semantic
consequences — it's adding a function definition or changing the test to use
`[1]` indexing.

Fix A **before** you look at B. B's existence has been verified; A is where
the real work is.

## Suggested first hour

### Step 0: Baseline

```bash
cd Frontend
sbt 'testOnly dev.vale.AfterRegionsIntegrationTests -- -t "Borrowing toArray"' 2>&1 | tee /tmp/fixing-borrowing-toarray.txt
tail -80 /tmp/fixing-borrowing-toarray.txt
```

Find the "Couldn't find function" message (Issue B) or the resolution failure
that reveals Issue A. Save the full trace to refer back to.

### Step 1: Read `CompilerSolver.scala` lines 909–970

This is the `AugmentSR` handler. Understand the three cases
(`ImmutableT`, `PlaceholderT`, `MutableT`) and what they return. Write
down, in your own words, what this handler is saying.

### Step 2: Read every test that exercises AugmentSR on an immutable

```bash
grep -rn "&int\|&bool\|borrow.*int\|&str" Frontend/Tests/test/main/resources/ 2>&1 | head -40
```

You're looking for existing working programs that use borrows of
immutable types. These are your **regression surface** — changes to the
`ImmutableT` case can break them.

Many will turn out to be comments or unrelated. Skim.

### Step 3: Look at `docs/Generics.md` for regions content

Grep for "region" in `docs/Generics.md`. The regions feature is documented
(partially). Read whatever you can find. The key insight: regions are the
feature that makes `&int` meaningful — it's saying "this int belongs to a
specific region, and I'm borrowing it from that region."

### Step 4: Decide your approach

You have two shapes to choose from:

**A1. Conservative.** Keep `ImmutableT => innerCoord.ownership` as the
default, but add a new case for "the surrounding code has explicitly asked
for a borrow and we're in a regions-aware context." This is the smallest
surface area but requires threading a new bit through.

**A2. Regions-permissive.** Change `ImmutableT` to preserve the augment,
matching the `MutableT` case. This is the cleanest code change but may break
pre-regions semantics elsewhere in the codebase.

Either one is defensible. Don't pick yet — first go do Step 5.

### Step 5: Instrument the test

Add a println in the `AugmentSR` handler that prints the inner kind,
the maybeAugmentOwnership, and the new ownership it's computing. Run the
test. Confirm you see:

```
AugmentSR: inner=IntT(32), augment=Some(Borrow), result=Share   <-- the bug
```

Also add a println at `ArrayCompiler.scala` around the mutable-branch
findFunction result. Confirm that you see `Array<mut, share int>` in the
returned prototype. **Don't commit** the printlns; you'll remove them.

This instrumentation is your proof that Issue A exists and that fixing the
`AugmentSR` case is what changes the outcome.

### Step 6: Try the regions-permissive fix (A2) first

Change `ImmutableT => innerCoord.ownership` to
`ImmutableT => Conversions.evaluateOwnership(augmentOwnership)`. Rebuild.
Run:

1. The targeted `Borrowing toArray` test. See if it now advances to Issue B.
2. **The full test suite.** This is mandatory. You're changing shared solver
   behavior. Grep failures for any that look like "ownership mismatch" or
   "can't borrow" or "BadIsaSubKind" — these are the shape of ownership
   regressions.

```bash
sbt 'test' 2>&1 | tee /tmp/fixing-borrowing-toarray.txt
grep -E "^\[info\] - .*FAILED" /tmp/fixing-borrowing-toarray.txt | sort
```

Compare that list against `quest.md`'s known-failing set. Any new failure is
a regression **you** caused.

If there are new regressions, you have three options:

- **A2 unmodified, add exceptions:** the regressions tell you exactly which
  cases still need the `share` behavior. Look for a pattern — maybe it's
  only when the augment is called implicitly by some other rule.
- **A1 (conservative) instead:** thread a "regions-aware" bit through
  `InferEnv` or through `AugmentSR`'s emission. Only apply the new behavior
  when that bit is set.
- **Leave Issue A alone for now and fix Issue B:** document Issue A clearly
  and punt. This is a legitimate choice — the test expects both issues
  fixed, but you can split them across two commits / two sessions.

Don't tie yourself in knots here. If A2 introduces >3 regressions that
aren't easy to classify, fall back to A1 or punt.

### Step 7: Fix Issue B

Assuming A2 (or A1) works, the test will now fail looking for `get` on an
array. Options:

- Add a stdlib function. Probably in `Frontend/Tests/test/main/resources/array/`:
  create a `get/` directory with a file that defines `func get<M,E>(arr &[]<M>E, i int) &E`
  or similar. Wire it up in `Builtins.scala` (see how `each/` and `has/`
  are wired).
- Change the test to use `[1]` indexing: `return l.toArray()[1];`. This
  is less satisfying but is consistent with existing stdlib practice
  (arrays use `[]` indexing; `get` is for `Opt` and `List`).

Preferred: add the stdlib function. It's the feature gap that the test name
implies.

### Step 8: Verify end-to-end

```bash
cd Frontend
sbt 'testOnly dev.vale.AfterRegionsIntegrationTests -- -t "Borrowing toArray"' 2>&1 | tee /tmp/fixing-borrowing-toarray.txt
tail -30 /tmp/fixing-borrowing-toarray.txt
```

Expect either `succeeded 1, failed 0` (win) or a new failure mode to
investigate. If the test passes, run the full suite once more to confirm no
regressions slipped in between your Step 6 run and your final state.

## Things NOT to do

- **Don't modify the test.** The test captures intent; if you think it's wrong,
  raise it with the maintainer. The only legitimate test change is Issue B's
  fallback (`[1]` indexing) — and only if you can't add an `Array.get`.
- **Don't delete `vassert`s or `vimpl`s to get past a failure.** Those exist
  to catch real bugs. If a test passes because you deleted a check, you hid
  the bug; you didn't fix it.
- **Don't edit the MKRFA preprocessing sites.** Four places in the codebase now
  have the same fold-pattern. There's a queued refactor to consolidate them
  into an `InferCompiler` helper (see `docs/refactor-thoughts/mkrfa-protocol-leak.md`).
  Don't do that as part of this work — it's a separate task. Focus.
- **Don't touch `ResolveSR` or `CallSiteFuncSR`.** BRRZ (recently shipped)
  made careful choices about those rules; changing them affects every call
  site in the codebase.
- **Don't assume the `AugmentSR` change is trivial.** It's a 1-line change in
  code but a potentially-cross-cutting change in semantics. Always run the
  full suite after.

## How to know you're on track

- You can describe, in one sentence: "The resolved return type of
  `toArray().E = int` is `share int` instead of `borrow int`, because
  `AugmentSR`'s `ImmutableT` case drops the borrow augment."
- You have a reproducible baseline showing the test fails specifically
  because of Issue A (not some earlier stall).
- You've read at least three existing tests that use borrows of immutables,
  to understand what you might break.
- You've decided on approach A1 vs A2 based on evidence, not on guesswork.

## How to know you're off track

- You're editing `ArrayCompiler.scala` without clear reason. (The `ArrayCompiler`
  fix from the earlier session is complete; changes there now are likely
  misdirected.)
- You've added code that conditionally activates "only for arrays" or "only
  for toArray" — that's a special-case hack, a tell for a confused fix.
- You're modifying `ExpressionScout.scala`'s array handling. (The postparser
  is not the problem.)
- You've created a new rule type in `CompilerSolver.scala`. (The existing
  rules are sufficient.)
- The number of passing tests went down in total and you can't say exactly
  which ones.

## Files you'll most likely read or touch

- `Frontend/TypingPass/src/dev/vale/typing/infer/CompilerSolver.scala` —
  specifically the `AugmentSR` handler at line 909-970. Probable edit site.
- `Frontend/IntegrationTests/test/dev/vale/AfterRegionsIntegrationTests.scala` —
  the test file. Read, don't edit.
- `Frontend/Tests/test/main/resources/array/` — where an `Array.get` stdlib
  file would go.
- `Frontend/Builtins/src/dev/vale/Builtins.scala` — where new stdlib files
  are wired into the compilation.
- `docs/Generics.md` — context on regions semantics.
- `investigations/borrowing_to_array.md` — the earlier investigation; extend
  it with your findings.

## Files you should NOT touch

- `Frontend/IntegrationTests/test/dev/vale/AfterRegionsIntegrationTests.scala` —
  the test file.
- `Frontend/TypingPass/src/dev/vale/typing/ArrayCompiler.scala` — the previous
  session's fix is complete; no further changes needed.
- `Frontend/PostParsingPass/src/dev/vale/postparsing/ExpressionScout.scala` —
  not the locus of this bug.
- Any file under `docs/arcana/` — those are stable cross-cutting references.

## What to hand back when you're done

1. A writeup appended to `investigations/borrowing_to_array.md`, or a new
   `investigations/borrowing_to_array_issue_a.md` — whichever fits better.
   Cover:
   - What you did for Issue A, with the exact code change.
   - Whether you chose A1 or A2 and why.
   - Any regressions you found in the full suite and how you handled them.
   - What you did for Issue B.
2. An update to `quest.md` — move "Borrowing toArray" out of the
   failing-tests list (it's currently in section G), and add it to the
   recovered list with a short summary.
3. A single commit with a descriptive message following the pattern of recent
   commits. See `git log --oneline | head -20` for style. Reference both the
   investigation file and the original `borrowing_to_array.md`.

## Glossary of acronyms you'll see

Vale's docs use initialism tags to name concepts. Common ones:

- **MKRFA** — "Must Know Runes From Above." The preprocessing contract that
  was the root of the earlier Borrowing toArray fix. See
  `investigations/borrowing_to_array.md`.
- **BRRZ** — "Bound Return Resolution." Recently shipped; relaxed the solver
  to infer bound-return runes. See `docs/arcana/BoundReturnResolution-BRRZ.md`.
- **SFWPRL** — "Solve First With Predictions, Resolve Later." The overall
  pattern for how the solver defers real function resolution.
- **ECSIIOSZ** — "Each Call Site Is Its Own Solve." Every call site has its
  own fresh solver.
- **SROACSD** — "Some Rules Only Apply to Call Site or Definition." Rule
  filtering invariant between call-site and definition solves.
- **DRSINI** — "Default Rules Should Be Incremental, Not Initial." Default
  generic arg rules fire incrementally.

When a code comment says `@FOOBAR`, find the doc at `docs/arcana/*-FOOBAR.md`
or search `docs/Generics.md` for the acronym.

Good luck. When in doubt, instrument first, assume last.
