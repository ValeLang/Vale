# Solver Refactor Thoughts: The MKRFA Protocol Leak

## ⚠ URGENCY: RECURRING PATTERN WITH NO ENFORCEMENT — REFACTOR SOON

**Status as of April 2026:** MKRFA is a **prose-enforced contract** between
phases. There is no compile-time, runtime, or type-level mechanism preventing
a new caller from violating it. The value solver's
`RuneParentEnvLookupSR` handler is a silent no-op, so violations manifest as
unrelated downstream "couldn't solve some runes" errors rather than clear
faults at the point of the missed preprocessing.

**Known violations discovered so far:**
- `ArrayCompiler.evaluateRuntimeSizedArrayFromCallable` — fixed April 2026
- `ArrayCompiler.evaluateStaticSizedArrayFromValues` — fixed April 2026
- `ArrayCompiler.evaluateStaticSizedArrayFromCallable` — fixed April 2026

All three sat in a single file, next to each other, for ~4 years without
symptoms because the underlying category-G AfterRegions tests that exercise
them were already failing for other reasons. The MKRFA missings only became
visible once the test suite began to advance past the earlier failures.

**Near-term risk:** every new expression-compilation site that spawns its own
solver is a candidate to repeat this bug. Active/upcoming work that could
introduce a violation includes:
- Any new templex or expression handler in `ExpressionCompiler` or related
  compilers that builds a nested rule set from postparser output.
- Category-G and category-H fixes that touch `ArrayCompiler` or add sibling
  expression compilers for other kinds (tuples, structs, lambdas…).
- Any refactor that introduces a new `inferCompiler.makeSolver` /
  `solveForResolving` call site.

**Minimum next action (cheap and safe):** adopt **suggestion 2** below —
extract the MKRFA fold from `OverloadResolver.scala:311-325` into a shared
`InferCompiler.preprocessRuneParentEnvLookups(rules, callingEnv)` helper.
Four call sites drop to one-liners; the contract becomes trivial to honor.

**Strongly recommended second step:** adopt **suggestion 1** — replace the
no-op handler in `CompilerSolver.scala:852` with a `vwat(...)` that names
MKRFA and points at the helper. Any future caller that forgets preprocessing
crashes immediately with a clear message rather than producing the
impossible-to-diagnose "unsolved runes" failure this document traces through.

Together, those two changes eliminate the recurring-bug class at negligible
cost. They should be done the next time someone is already editing
`CompilerSolver.scala` or `InferCompiler.scala`.

---

**Author context:** Written while investigating the failing `Borrowing toArray`
AfterRegions integration test in April 2026. The investigation is captured in
`investigations/borrowing_to_array.md` and discussed in the Category G solver
notes in `handoff.md` / `quest.md`. These thoughts are an after-the-fact
reflection on what the bug hunt revealed about the *architecture*, not the bug
itself.

This document is **not a proposal to do the deeper refactors (suggestions
4-6) now.** Those are scope-expanding and would touch files across the
TypingPass and PostParsingPass. It's a capture of structural smells so that
whoever next touches the solver plumbing has a head start on the "why" — and
so the ideas don't evaporate back into tribal knowledge. But suggestions 1
and 2 should be treated as near-term follow-ups, not aspirations.

---

## Background: the bug that motivated this

### Symptom

The test

```vale
func toArray<E>(list &List<E>) []<mut>&E {
  return []&E(list.len(), { list.get(_) });
}
```

failed with:

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

The telling signal: `inherit E` *appears* in the fired-rules list — but with
no conclusion indented beneath it (compare to `_511111111211 = mut` with
`_511111111211: mut` under it). The rule "fires" producing no value.

### Root cause

`RuneParentEnvLookupSR` — the rule humanized as "inherit X" — is a
pseudo-rule. It exists in the rule vocabulary, the type-solver understands it,
the humanizer prints it — but the *value solver's* handler is intentionally a
no-op:

```scala
// Frontend/TypingPass/src/dev/vale/typing/infer/CompilerSolver.scala:852
case RuneParentEnvLookupSR(range, rune) => {
  // This rule does nothing, it was actually preprocessed.
  solverState.commitStep[ITypingPassSolverError](
    false, Vector(ruleIndex), Map(), Vector()) match { ... }
}
```

The *expected* lifecycle: before reaching the value solver, a caller must
walk the rule list, pluck out every `RuneParentEnvLookupSR`, look up each
rune's name in the caller's environment (`callingEnv`), and convert each one
into an `InitialKnown` seed. This is the **MKRFA contract** (named after a
cross-reference tag in `FunctionScout.scala:531` and
`OverloadResolver.scala:311`).

`OverloadResolver.findFunction` (lines 311-325) honors the contract with an
inline fold. `ArrayCompiler.evaluateRuntimeSizedArrayFromCallable` does not —
it just sets `val initialKnowns = Vector()` and hands the full rule set
(including `RuneParentEnvLookupSR(E)`) to `inferCompiler.makeSolver`. The
no-op handler then fires harmlessly; `E` is never seeded; `_51111111111 = &E`
(the `AugmentSR` for `&E`) stalls forever because its puzzle requires `E`'s
conclusion.

### Audit findings

Across the typing pass there are ~13 nested-solver invocation sites. They
divide into two disjoint categories:

| Category | Rule source | `RuneParentEnvLookupSR` risk |
|---|---|---|
| **Declaration-scoped callers** (`FunctionCompilerSolvingLayer`, `StructCompilerGenericArgsLayer`, `ImplCompiler`) | `function.rules`, `structA.headerRules`, `impl.rules` — rules that live in the same solver as their generic params | None. The inherit bridge is never emitted for same-solver references. |
| **Expression-scoped callers** (`OverloadResolver.findFunction`, `ArrayCompiler.*`) | Rules from an expression in a function body, referring to outer generic params | High. Every such caller must honor MKRFA. |

The declaration-scoped callers are safe by accident — their rule source
happens not to include the pseudo-rule. The expression-scoped callers must
each independently remember to preprocess. `OverloadResolver` remembers;
`ArrayCompiler` does not.

---

## What felt wrong

### 1. The bug manifested at a distance from its cause

The error pointed at `_51111111111 = &E` ("the borrow-of-E rule failed to
solve"). Nothing in the error text mentioned `RuneParentEnvLookupSR`, the
no-op handler, `ArrayCompiler`, or MKRFA. Understanding the real cause
required reading:

- `AfterRegionsIntegrationTests.scala` (test)
- `CompilerSolver.scala` (the no-op handler)
- `OverloadResolver.scala` (the canonical preprocessing site)
- `FunctionScout.scala` (the MKRFA cross-reference)
- `ArrayCompiler.scala` (the missing preprocessing)
- `PostParserErrorHumanizer.scala` (to learn that "inherit X" *is* `RuneParentEnvLookupSR`)

That's six files to diagnose a single missed preprocessing step. The actual
informational density of the problem is "one call site forgot to run a
helper."

### 2. The no-op handler actively concealed the bug

The value solver's `RuneParentEnvLookupSR` case *commits a step with empty
conclusions.* That's operationally indistinguishable from "fired successfully
with nothing to contribute," which is a thing many valid rules do during
partial solves.

If it had been `vwat()` instead, every callsite that forgot preprocessing
would crash immediately on first reach with a clear message. The defensive
no-op exists presumably because some caller was known to leave
`RuneParentEnvLookupSR` in the rule list while also providing the
`InitialKnown` — in which case the rule fires, finds the rune already
concluded, and the no-op commit is benign. But the cost of this tolerance is
silent failure when both parts of the protocol are missing. The tolerance is
not worth the concealment.

Stricter: `vwat("RuneParentEnvLookupSR reached the value solver — the caller forgot MKRFA preprocessing. See OverloadResolver.scala:311 for the canonical fold.")`

### 3. The MKRFA contract lives in prose, not in types

Across the codebase, the contract is expressed as:

- An inline 15-line fold in `OverloadResolver.scala`
- A comment cross-reference `// See MKRFA.` in `FunctionScout.scala`
- A comment `// This rule does nothing, it was actually preprocessed.` in `CompilerSolver.scala`

Nothing in any type signature indicates that `makeSolver` / `solveForResolving`
require rules to have been preprocessed. The signature accepts
`Vector[IRulexSR]` and will accept any vector, including vectors still
containing `RuneParentEnvLookupSR`.

A stronger design would lift the contract into types. For example:

```scala
case class PreprocessedRules(rules: Vector[IRulexSR], extraInitialKnowns: Vector[InitialKnown])

object InferCompiler {
  def preprocessCallSiteRules(rules: Vector[IRulexSR], callingEnv: IInDenizenEnvironmentT): PreprocessedRules = ...
}

class InferCompiler {
  def solveForResolving(..., rules: PreprocessedRules, initialKnowns: Vector[InitialKnown], ...): ...
}
```

`PreprocessedRules` is only constructible via `preprocessCallSiteRules`. Any
caller passing raw `Vector[IRulexSR]` is a compile error. The MKRFA bug
becomes impossible to re-introduce.

The declaration-scoped callers still work: they call
`PreprocessedRules(theirRules, Vector())` directly (or via a
`skipPreprocessing` constructor), because their rule source is
*known* not to contain `RuneParentEnvLookupSR`. Making this "I know I'm safe"
claim explicit at each declaration-scoped caller is a feature, not a burden.

### 4. `RuneParentEnvLookupSR` is one node wearing two hats

In the **rune-type solver** (HigherTypingPass), it's a real constraint: "this
rune has whatever type the outer environment gives it — do a type-level
lookup." `RuneTypeSolver.scala:141-149, 289-299` uses it substantively.

In the **value solver** (TypingPass), it's not a rule at all — it's a
directive for the *caller* of the solver, saying "please hand me the
outer-env value for this rune as an `InitialKnown`." The solver itself can't
execute it (the solver doesn't have access to `callingEnv`), which is why
the handler is forced to be a no-op.

Using one AST node to mean two different things in two different passes is a
modeling smell. A cleaner design would separate them:

- A real rule `InheritTypeFromParentEnvSR(rune)` used only by the rune-type
  solver.
- A *closure-capture list* carried alongside the rule set —
  `Vector[(IRuneS, IImpreciseNameS)]` — consumed by the MKRFA preprocessing
  step before solving, never seen by the value solver.

This also eliminates the no-op solver case entirely: if there's no rule of
that shape in the value solver's rule vocabulary, there's nothing to forget
to preprocess. The value solver simply receives value-solver rules.

### 5. `initialKnowns = Vector()` is a structural smell

It appears three times in `ArrayCompiler.scala` (lines 179, 369, plus the
static-from-callable path). Each appearance sits one line above a direct
call to `inferCompiler.makeSolver` / `solveForResolving`. Each one is a
silent declaration of "I know this expression-level rule set won't need
environmental bridging," which is in each case *wrong*.

Any line of the form `val initialKnowns = Vector()` next to an expression-scoped
solver invocation is a bug site. That's not a coincidence — it's a missing
abstraction. `ExpressionSolverInput.build(rules, callingEnv)` would do both
the preprocessing and the `initialKnowns` construction in one call, leaving
no place for the empty-vector literal to live.

### 6. Rule behavior is scattered across files

For a single rule type like `RuneParentEnvLookupSR`, the following
definitions/handlers/transforms live in ~8 different files:

- `rules.scala` — the case class
- `RuneTypeSolver.scala` — the type-solver puzzle + handler
- `CompilerSolver.scala` — the value-solver puzzle + handler (the no-op)
- `OverloadResolver.scala` — the preprocessing fold
- `FunctionScout.scala` — the "filter for interface parents" variant
- `AnonymousInterfaceMacro.scala` — the mapRunes transform
- `PostParserErrorHumanizer.scala` — "inherit X"
- `ArrayCompiler.scala`, `ExpressionCompiler.scala` — sites that emit/consume it

Adding a new rule type or changing an existing one's semantics requires
synchronizing across all of these. The review burden — "did you update every
handler?" — is high and easy to miss.

A co-located design would declare each rule's behavior in one place:

```scala
object RuneParentEnvLookupSR extends IRuleDefinition {
  val caseClass = ...
  def typeSolverPuzzle = ...
  def typeSolverSolve(...) = ...
  def valueSolverPuzzle = Vector(Vector())  // puzzle-less
  def valueSolverSolve = vwat("MKRFA contract — preprocess before solving")
  def humanize(r) = s"inherit ${r.rune}"
  def callSitePreprocess: Option[PreprocessStep] = Some(MKRFA)
}
```

Callers iterate `rules.map(_.definition.valueSolverSolve)` etc. Adding a
rule means writing one object; changing behavior means touching one spot.

### 7. Trace output hid the smoking gun

The solver's step printer formats

```
inherit E
  E: <some value>
```

when there *is* a conclusion, and

```
inherit E
```

(nothing indented) when the commit was empty. A zero-conclusion step looks
visually identical to "I'll get back to this," not "I intentionally did
nothing." A one-line change — print `(no conclusions)` for empty commits —
would have made the root cause obvious on first read of the trace.

---

## Concrete suggestions, ranked by cost

1. **Replace the no-op handler with `vwat()`.** One-line change. Makes the
   MKRFA violation instantly diagnosable: a crash at the exact rule with a
   message pointing at the preprocessing fold. This should be done next time
   someone is already in `CompilerSolver.scala` (the Borrowing-toArray fix
   itself is a natural moment).

2. **Extract the MKRFA fold to a shared helper** (e.g.
   `InferCompiler.preprocessRuneParentEnvLookups(rules, callingEnv)`).
   Replace the inline fold in `OverloadResolver`, and add calls in
   `ArrayCompiler`'s three sites. Medium change. Eliminates duplication; next
   novel expression-scoped caller has a drop-in.

3. **Improve solver trace output.** Print `(no conclusions)` for empty
   commit steps. Small change with high diagnostic leverage.

4. **Introduce `PreprocessedRules` as a distinct type** (section 3's
   design). Medium-large change: updates every solver entry point's
   signature, every caller. Eliminates the entire bug class; makes the
   MKRFA contract impossible to forget.

5. **Split `RuneParentEnvLookupSR` into a type-solver rule plus a
   closure-capture list** (section 4's design). Large change. The cleanest
   long-term story — rules in the value solver are actually rules, not
   directives for the caller — but touches the postparser, both solvers,
   and several humanizers.

6. **Co-locate each rule's definition, handlers, and metadata** (section 6).
   Very large change. Worth considering as part of any major solver refactor,
   but not justified on its own.

Items 1, 2, and 3 are cheap and independently worthwhile. 4 is the durable
architectural fix. 5 is the cleanest modeling fix. 6 is nice-to-have.

---

## For whoever picks this up

- The Borrowing toArray investigation is in `investigations/borrowing_to_array.md`.
- The AfterRegions test backlog is tracked in `quest.md` (category G covers
  solver bugs).
- Several other category-G tests (`Make array without type`, `Call Array<> without element type`)
  plausibly share this root cause — same file, same pattern, same literal
  `initialKnowns = Vector()`.
- The user who drove the Borrowing toArray investigation explicitly asked to
  be reminded about the `vwat()` change (item 1) after the immediate fix
  lands; honor that instead of doing it unilaterally.
- Option 3 from the in-conversation design discussion (push preprocessing
  into `InferCompiler.makeSolver` itself) was rejected because the
  declaration-scoped callers would pay a no-op cost on every invocation and
  because declaration-scoped rule sources are known-safe. Prefer option 2
  (shared helper) or the type-level `PreprocessedRules` story.

## Meta-observation

The MKRFA contract is a specific instance of a general pattern in this
codebase: **protocols between passes that live in prose cross-references
rather than types.** Other examples worth watching for:

- The "AfterRegions" boundary itself — an implicit contract about which
  phase of compilation should see which rule shapes.
- `explicifyLookups` and `MaybeCoercingLookupSR`'s disambiguation duty — a
  similar "this rule exists in an intermediate form then gets rewritten"
  story.
- The `assembleCallSiteRules` / `assemblePredictRules` / `assembleDefineRules`
  family — same rule set, filtered for different solver contexts, with the
  filter living in convention.

Every one of these is a future version of the MKRFA bug waiting to happen.
The most durable protection is to express the invariants in types and
crash-on-violation asserts rather than comments and cross-reference tags.
