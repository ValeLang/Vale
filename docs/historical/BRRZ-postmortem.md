# BRRZ implementation post-mortem

**Task:** restore the capability to infer a generic function's return rune from a `where func(...)R` bound when the caller supplies concrete values for the bound's parameter runes. Lost in the 2022 templates-to-generics transition; tracked as MSAE ("Must Specify Array Element") in `docs/Generics.md:862`.

**Outcome:** three `AfterRegions` tests recovered (`Make array without type`, `Call Array<> without element type`, `Bound-driven return rune cannot be inferred from lambda (MSAE general)`). Two new edge-case tests added and passing. HashMap-style regression-guard test added and still correctly fails-to-compile. Zero regressions in the full 1069-test suite. See `docs/Generics.md` BRRZ section and `docs/arcana/BoundReturnResolution-BRRZ.md`.

## What the change actually was

**Core:** relaxed `ResolveSR`'s puzzle in `CompilerSolver.scala:259` to fire when only `paramsListRune` is known (previously required both `paramsListRune` and `returnRune`). Split the handler at `CompilerSolver.scala:636` into two branches: the existing `predictFunction` path when both are known, and a new branch that calls `delegate.resolveFunction` (a real overload lookup) when only params are known. Added `resolveFunction` to the solver's `IInfererDelegate` trait (it already existed on the outer `IInferCompilerDelegate` trait for post-solve use).

**Supporting:**
- Removed the MSAE guard at `ArrayCompiler.scala:253`.
- Added an `@BRRZ` arcana doc at `docs/arcana/BoundReturnResolution-BRRZ.md`.
- Added an `@SROACSD` annotation to the filter functions at `InferCompiler.scala:787-803` (the existing invariant that `ResolveSR` and `DefinitionFuncSR` never coexist in a solve; BRRZ depends on it).
- Added a BRRZ section to `docs/Generics.md` and amended the MSAE and "...but not return types" sections.

**Regression guard:** a new test at `AfterRegionsErrorTests.scala` that reproduces the HashMap-style shape Evan originally removed the capability to avoid. It must fail-to-compile; if it ever starts passing, the safety property has drifted.

## Where the plan was wrong

The original plan had 10 steps. Two were wrong, for instructive reasons.

**Step 4 (synthesize an implicit element rune in `ExpressionScout`) was wrong.** The plan assumed the main `ArrayCompiler` solver needed a rune as a target to fill in when the user omits the element type in `#[](10, {_})`. I implemented it and it broke things: the synthesized rune had no rule connecting it to anything, so the solver stalled on it with `Unsolved runes: _3111111111`. I reverted the synthesis and the test passed anyway, because:

- The `ImmutableT` branch at `ArrayCompiler.scala:260` already handled `maybeElementTypeRune = None` gracefully via `.foreach` (no-op on None); the element type comes from `getArrayGeneratorPrototype(callableTE).returnType` directly.
- The `MutableT` branch at `ArrayCompiler.scala:283` calls `overloadResolver.findFunction("Array", ...)` which goes through the stdlib `Array<M,E,G>(n int, generator G) where func(&G,int)E`. That bound's `ResolveSR`, now relaxed, infers E — same mechanism as test 2.

The MSAE guard removal alone was sufficient. The synthesis was actively harmful.

**Step 6 (remove redundant main-solve seeding) was a no-op.** The seeding at `ArrayCompiler.scala:173-174` is belt-and-suspenders with `RuneTypeSolver.scala:210`'s automatic typing of any `ResolveSR`'s `returnRune`. Either mechanism alone is sufficient; leaving both in is fine.

Net result: the implementation was smaller than the plan, not larger. That's the good failure mode.

## Surprises during investigation

**MSAE had an official acronym and doc section from 2022.** The `// Temporary until we can figure out MSAE.` comment in `ArrayCompiler.scala:254` wasn't a random placeholder — `docs/Generics.md:862` had a full section documenting the regression. Reading it revealed the capability was removed deliberately, not incidentally.

**The docs contradicted themselves.** The MSAE section says "we'll need to add that back in" (aspirational). The "...but not return types" section at line 529 says "we can't incorporate bounds from return types" (forbidden). Both written by the same author at different times, never reconciled. The real distinction: outer return-type annotation inference = forbidden (lets callers skip declaring bound args), bound-return rune inference = fine (the post-solve check still verifies bound args). BRRZ restores only the latter.

**`ResolveSR` doesn't actually find any function today.** The handler is a pure constructor. `predictFunction` just stitches together `name + paramCoords + returnCoord` into a `PredictedFunctionNameT` marker. Real resolution happens post-solve in `checkResolvingConclusionsAndResolve:295` via the already-existing `delegate.resolveFunction`. So my "new capability" wasn't new at all — it's the same machinery, invoked at a different point in the pipeline.

**Two `IInfererDelegate` traits exist.** The inner one (in `CompilerSolver.scala`) had `predictFunction` but not `resolveFunction`. The outer one (on `InferCompiler.IInferCompilerDelegate`) had `resolveFunction`. Had to mirror it onto the inner trait and implement in `Compiler.scala`.

**`verifyConclusions=true` is unused.** The existing post-solve call passes `true`; `OverloadResolver.findFunction` ignores it. Not semantically significant today.

**The HashMap regression from the docs is not reproducible in today's codebase.** I'd been treating it as a live threat. The stdlib's `HashMap<K Ref imm, V, H, E>(hasher H, equator E) HashMap<K,V,H,E>` now declares all the bounds explicitly in a `where` clause. The dangerous shape simply doesn't exist anywhere. So the "HashMap regression" is a shape that could recur, not a current failure mode.

**Only two MSAE-shape sites exist.** `arrays.vale:36` and `arrays.vale:52`. A narrow, specific loss, not a pervasive capability gap.

**Zero existing code triggers BRRZ's new path outside the target tests.** Agent-based enumeration of all `where func(...)R` bounds in stdlib and tests: 27 of 29 have literal returns (no new path), 1 is header-pinned (no new path), only 1 (my own minimal repro test) triggers it. Blast radius is minimal by construction.

## Methodological lessons

**"Scoped narrowly" is a tell.** I caught myself using that phrase when three distinct claims were collapsing into one: (1) structural claim ("rune appears as a bound's return"), (2) negative existential ("nowhere else"), (3) obligation claim ("therefore safe to infer"). The origin-tracking memo at `docs/refactor-thoughts/thoughts-on-origin-tracking.md` called this pattern out explicitly. When tempted to write "scoped narrowly" or "narrow exception," decompose into the actual structural + safety claims, or flag that you can't and aren't sure yet.

**Fallbacks hide uncertainty.** The first version of the plan had a fallback "gate on `InferEnv`'s mode if needed." That was a concession that the argument hadn't closed. Replacing the fallback with concrete investigation (three parallel enumeration agents) closed the argument properly. Fallbacks that silently re-enable old behavior are exactly the shape to avoid — they let regressions slip through by papering over, rather than surfacing the real issue.

**Empirical enumeration beats theoretical worry.** Three red-team agents raised plausible hazards. One ("impl/struct solves might have DefinitionFuncSR + ResolveSR coexisting") sounded serious until the solver-entry-point audit confirmed every solver applies the filter. The worry was valid in the abstract; the codebase doesn't contain it. Separating "possible" from "actual" saved a lot of defensive hedging.

**SFWPRL's "predict" semantics is a load-bearing abstraction.** Once I understood the solver NEVER does real function resolution — it only predicts, and verification is a separate phase — the safety story clarified. The solver is declarative; the verification is imperative. Keeping that split clean is what makes BRRZ work without a gate.

**Git blame on the specific failing line points straight at the original author's intent.** `git blame` on `ArrayCompiler.scala:254` → `f184f4d8` → "Finished transition from templates to generics." That commit also added the MSAE doc section with the rationale. One blame command went from "what's this weird guard?" to "here's exactly why it was added, by the person who added it, on the day they added it."

**Agents are good at enumeration, less good at reasoning about hazards.** The red-team agent that claimed "impl solves are a real hazard" was wrong on the specific mechanism — its claim about `DefinitionFuncSR` putting things in env didn't hold up under manual trace. But the enumeration agents (bound shapes, solver entry points, state dependencies) were reliably accurate because the task was "list everything matching pattern X," not "assess whether this is safe."

**Trust `CompilerOutputs` caching for recursion termination.** The plan worried about unbounded recursion when `findFunction` triggers stamping which triggers more `findFunction`s. The `signatureToFunction` cache terminates this naturally. No depth guards needed. Make repeated work cheap via caching rather than adding depth guards that would need tuning.

**Naming conventions matter, catch mismatches early.** I'd initialized the acronym as `BRRX` (X = shield suffix, wrong for arcana). The convention is arcana = Z suffix, shield = X suffix. Renamed all 32 occurrences before writing the arcana file. Would have been cheaper to catch earlier by reading `docs/meta.md` first.

## What changed in the repo

- `Frontend/TypingPass/src/dev/vale/typing/infer/CompilerSolver.scala` — puzzle relaxation, handler split, `resolveFunction` added to `IInfererDelegate`.
- `Frontend/TypingPass/src/dev/vale/typing/Compiler.scala` — `resolveFunction` implementation added to the anonymous delegate.
- `Frontend/TypingPass/src/dev/vale/typing/ArrayCompiler.scala` — MSAE guard removed (replaced with a BRRZ reference comment).
- `Frontend/TypingPass/src/dev/vale/typing/InferCompiler.scala` — `@SROACSD` annotations added to the filter functions.
- `Frontend/TypingPass/test/dev/vale/typing/AfterRegionsTests.scala` — minimal BRRZ repro test + 2 edge-case tests.
- `Frontend/TypingPass/test/dev/vale/typing/AfterRegionsErrorTests.scala` — HashMap regression-guard test.
- `docs/Generics.md` — MSAE redirected to BRRZ, "...but not return types" amended, new BRRZ section.
- `docs/arcana/BoundReturnResolution-BRRZ.md` — new.
- `docs/refactor-thoughts/thoughts-on-origin-tracking.md` — new (records the rejected alternative).
- `quest.md` — "Make array without type" and "Call Array<> without element type" marked FIXED.
