# Handoff: "Make array without type" and "Call Array<> without element type"

This document was originally written speculatively. It has been updated after
a reconnaissance pass confirmed what each test actually does. Read everything
in order.

## What you're solving

Two tests in `Frontend/IntegrationTests/test/dev/vale/AfterRegionsIntegrationTests.scala`
currently fail. They are in "category G" (solver/inference bugs) in
`quest.md`. Despite the earlier hypothesis that they are variants of the
same bug, **they fail in different places** and the "fix one, the other
falls out" story is wrong.

### Test 1: `Make array without type` (line 186)

```vale
exported func main() int {
  a = #[](10, {_});
  return a.3;
}
```

Expected: returns `VonInt(3)`. The `#[]` creates a static-sized array whose
**element type is not written** — the compiler must infer it from the generator
lambda `{_}`.

**Where it fails (confirmed):**

- `ConstructArrayPE` → `ExpressionScout.scala:488-534` produces
  `NewRuntimeSizedArraySE` (yes, `#[]` with no size lowers to RSA when the
  postparser sees the shape; this is existing behavior).
- The postparser sets `maybeTypeRuneS = None` (line 491-495, because
  `maybeTypePT` is `None`) and adds `LiteralSR(MutableP)` as the default
  mutability (line 500).
- In `ArrayCompiler.evaluateRuntimeSizedArrayFromCallable`, the main solver
  completes with `mutability = mut`. We then hit the hard guard at line
  253-256:
  ```scala
  if (maybeElementTypeRune.isEmpty) {
    throw CompileErrorExceptionT(RangedInternalErrorT(parentRanges,
      "Must specify element for arrays."))
  }
  ```

**Important**: even after you remove that guard, the `MutableT` branch at
line 283-311 calls `overloadResolver.findFunction("Array", ...)` against the
Vale-level `Array<M, E, G>(n int, generator G)` in `Builtins/.../arrays.vale:36`.
That function has the same `where func(&G, int)E` bound as the `imm` version
and thus will hit the **exact same E-inference stall described for test 2
below**. So: fixing the MSAE guard is necessary but not sufficient for test 1.

### Test 2: `Call Array<> without element type` (line 172)

```vale
exported func main() int {
  a = Array<imm>(3, {13 + _});
  sum = 0;
  drop_into(a, &(e) => { set sum = sum + e; });
  return sum;
}
```

Expected: returns `VonInt(42)`.

**Where it fails (confirmed):** this test does **not** enter `ArrayCompiler`
at all. It's an ordinary user-level function call to the builtin
`Array<M Mutability, E Ref imm, G>(n int, generator &G)` from
`arrays.vale:51`. The failure is inside `OverloadResolver` in the candidate's
own solve:

```
Candidate 1 (of 3): :arrays.vale:51:1:
Couldn't solve some runes: _1215, _7111, E, _7111.kind
...
_6111 = &G               -> G: main.λC (closure)
_12111 = &G              -> &closure
_1213 = (_12111, _121211) -> (&closure, i32)
(complex)
Unsolved rule: _1215 = callsite-func __call(_1213)E
Unsolved rule: _1215 = resolve-func __call(_1213)E
Unsolved rule: _7111.kind = Array<M, E>
```

Candidates 2 and 3 are rejected on an unrelated mutability conflict
(`_113111: was mut but now concluding imm`) — they're the mut-only Array
signatures.

## The actual root cause (shared by both tests)

The bound `func(&G, int)E` lowers to two rules:

- `CallSiteFuncSR(protoRune, "__call", paramListRune, returnRune)`
  - Puzzle at `CompilerSolver.scala:242`: `Vector(Vector(resultRune.rune))`.
  - Fires only when the prototype rune is already known; it then decomposes
    the prototype into params+return. It cannot **find** a prototype.
- `ResolveSR(protoRune, "__call", paramListRune, returnRune)`
  - Puzzle at line 245: `Vector(Vector(paramsListRune.rune, returnRune.rune))`.
  - Handler at line 629-639 `vassertSome`s both param-list and return, then
    calls `delegate.predictFunction(name, params, return)` to mint a prototype.
    Requires return **already known**.

So nothing in the current rule set covers "given a name and known params,
look up the function and **discover** its return type." That's the gap. G
gets inferred fine (InitialSend on arg 1 → `_6111 = &G` fires); after that
the bound rules stall because E is the return type and no rule produces it.

(For reference: the overload resolver itself can discover a function from
name+params. The solver rule that would use that capability doesn't exist.)

## The two routes to a fix

### Route A: ArrayCompiler-local workaround — **does not fix test 2**

1. In `evaluateRuntimeSizedArrayFromCallable` (and the other two entry
   methods), before running the main solver, call
   `overloadResolver.getArrayGeneratorPrototype(callableTE, ...)` to get the
   callable's `__call` prototype and therefore its return type.
2. If `maybeElementTypeRune` is `None`, synthesize an implicit element rune
   (either in the postparser unconditionally, or by manufacturing one here);
   add `InitialKnown(elementRune, CoordTemplataT(returnType))`.
3. Remove the MSAE hard guard.
4. In the `MutableT` branch (line 283-311), also pass E as an `InitialKnown`
   into the `overloadResolver.findFunction("Array", ...)` call so the
   callee's E-inference stall doesn't re-trigger.

**This fixes test 1 only.** Test 2 never enters ArrayCompiler. Because you
don't want to rewrite tests, this option is insufficient on its own.

### Route B: fix the general bound-driven inference — fixes both

The general gap is that `ResolveSR`/`CallSiteFuncSR` cannot discover a
function's return from name+params. Options:

- **B1**: Change `ResolveSR`'s puzzle/handler so that when `returnRune` is
  unknown but `paramListRune` and the name are known, it performs an
  overload lookup (`overloadResolver.findFunction`) and commits the return
  into `returnRune`. This is the direct analog of what already happens at
  line 638 (`delegate.predictFunction`), except it would actually *search*
  rather than predict.
- **B2**: Add pre-inference in `FunctionCompilerSolvingLayer` /
  `OverloadResolver.solveCandidate` that, before the main solve, looks for
  bound shapes `func(&G, …)E` where G is tied to an arg via InitialSend and
  E is an identifying generic rune; once the arg comes in, eagerly resolve
  `__call(argCoord, …)` and seed E as `InitialKnown`.

B1 is smaller and closer to existing machinery but touches the shared
solver rule handler (the earlier version of this doc warned against that —
but the warning was motivated by not wanting to perturb unrelated tests,
not by any architectural no-fly zone). B2 is more localized but reimplements
logic that `ResolveSR` almost already has.

**Strongly consider B1**: it's a targeted change to one handler (around
`CompilerSolver.scala:629-639`) and to the puzzle (line 245). The delegate
already exposes a `predictFunction` method; we'd want an analog that does a
real lookup, or we'd extend the handler to first call a real overload
lookup and fall back to prediction.

## Risks / things to watch

- Any change to `ResolveSR` semantics runs across every `where func …`
  bound in every test. Run the full suite. Bound-constrained generic
  resolution is load-bearing for generics tests.
- Overload lookup for `__call` on a closure struct must not recurse into
  its own resolution. The closure's `__call` is already generated by the
  typing pass; looking it up should be straightforward (it's a regular
  function on the closure struct).
- Watch the interaction between `ResolveSR` and `CallSiteFuncSR`. These
  pair up (see `arrays.vale` bounds emit both, as seen in the trace). Only
  one should ultimately commit the prototype; the other should then just
  decompose.

## Suggested first steps (after we align on route)

### If we go with Route B1

1. Read `CompilerSolver.scala:629-654` (`ResolveSR` + `CallSiteFuncSR`
   handlers) and `OverloadResolver.findFunction` in full. Understand what
   inputs a real lookup needs vs. what `predictFunction` synthesizes.
2. Find the `InferCompiler.IInfererDelegate` interface (likely implemented
   by `CompilerSolverDelegate` somewhere) and check whether it already has
   an overload-lookup method, or whether a new one must be added.
3. In the `ResolveSR` handler: if `returnRune` is unknown but `paramListRune`
   is known, attempt an overload lookup; if successful, commit both the
   prototype and the return rune.
4. Update the puzzle for `ResolveSR` to fire when `paramsListRune` alone is
   known (dropping the requirement that returnRune be known). Keep the
   existing case as a successful branch.
5. Run test 2 alone; then the whole `AfterRegionsIntegrationTests` suite;
   then `CompilerTests` and `CompilerGenericsTests`.

### If we go with Route A anyway (only fixes test 1)

See the steps in Route A above. Don't touch the test file. Test 2 remains
failing; mark it in `quest.md` as "fix blocked on B-style solver change."

## Known-good instrumentation you can re-use

- `sbt 'testOnly dev.vale.AfterRegionsIntegrationTests -- -t "Make array without type"' 2>&1 | tee /tmp/fixing-array-test.txt`
- `sbt 'testOnly dev.vale.AfterRegionsIntegrationTests -- -t "Call Array<> without element type"' 2>&1 | tee /tmp/fixing-array-test2.txt`
- `grep -A 60 "FAILED" /tmp/fixing-array-test2.txt` gives the full solver
  trace including the "Supplied:", "added rule:", per-step conclusions, and
  final "Unsolved rule:" list.

## Vocabulary cheat sheet (for glancing)

- **SR suffix** — "Scout Rule" — a constraint type from the postparser.
- **SE / PE / SR / AE suffixes** — postparser-, parser-, rule-, higher-typing
  versions of a node.
- **`T` suffix** — typing-pass type (`CoordT`, `StructTT`).
- **`I` suffix** — instantiator-pass type (`CoordI`, `CoordTemplataI`).
- **Templata** — any type-level value the solver computes (coord, kind,
  int, mutability, …).
- **InitialKnown** — `(rune, templata)` seed fed to the solver up front.
- **InitialSend** — `(argRune, actualArgCoord)` seed — tells the solver the
  caller's actual arg coord for a param rune.
- **Placeholder / `KindPlaceholderT`** — stand-in for an unresolved generic
  param inside a function body.
- **Bound** — `where func foo(…)R` on a generic function; emits
  `CallSiteFuncSR` (callee-side) + `ResolveSR` (caller-side) rules.
- **MKRFA** — preprocessing contract that `RuneParentEnvLookupSR` rules
  must be stripped into InitialKnowns before the solver runs. Not the
  issue here, but don't regress it when editing.

## Files most likely relevant

- `Frontend/TypingPass/src/dev/vale/typing/infer/CompilerSolver.scala`
  (`ResolveSR`/`CallSiteFuncSR` handlers around line 629-668 and puzzle
  declarations around line 242-245) — **the probable site of the fix**.
- `Frontend/TypingPass/src/dev/vale/typing/OverloadResolver.scala` —
  reference for what an overload lookup does; probable source of the
  delegate method we'll want to call from the solver.
- `Frontend/TypingPass/src/dev/vale/typing/InferCompiler.scala` — where
  the solver delegate interface lives.
- `Frontend/TypingPass/src/dev/vale/typing/ArrayCompiler.scala` — only
  relevant for test 1, and only for removing MSAE and adding an element
  rune. A Route-A-only fix would live here.
- `Frontend/PostParsingPass/src/dev/vale/postparsing/ExpressionScout.scala`
  lines 488-534 — the `ConstructArrayPE` handler; where one could emit a
  synthetic element rune if needed.

## Files NOT to touch

- `Frontend/IntegrationTests/test/dev/vale/AfterRegionsIntegrationTests.scala`
  — do not modify the tests.
- `Frontend/Builtins/src/dev/vale/resources/arrays.vale` — do not rewrite
  the `Array` builtin signatures.

## What to hand back

1. Writeup at `investigations/make_array_without_type.md` (or a name of
   your choice) covering: reproduction, what the rules looked like, what
   the fix adds, what tests now pass, any new questions.
2. `quest.md` updates — move the two tests out of category G.
3. Commit with a descriptive message following recent commit style.
