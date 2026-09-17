# Family 3: Map function — vimpl in evaluateGenericVirtualDispatcherFunctionForPrototype

**Test:** `dev.vale.AfterRegionsIntegrationTests` → "Map function"
**Source:** `Frontend/Tests/test/main/resources/programs/genericvirtuals/mapFunc.vale`
**Failure:** `VAssertionFailException: impl` at `FunctionCompilerSolvingLayer.scala#evaluateGenericVirtualDispatcherFunctionForPrototype` (the `vimpl()` inside the `case None` branch of the `placeholderInitialKnownsFromFunction` flatMap).

## Source under test

```vale
// mapFunc.vale
struct MyEquals9Functor { }
impl IFunction1<mut, int, bool> for MyEquals9Functor;
func __call(this &MyEquals9Functor, i int) bool { return i == 9; }

exported func main() bool {
  a Opt<int> = Some(9);
  f = MyEquals9Functor();
  b Opt<bool> = a.map<int, bool>(&f);
  return b.getOr<bool>(false);
}

// optutils.vale relevant abstract:
abstract func map<T, R>(virtual opt &Opt<T>, func &IFunction1<mut, &T, R>) Opt<R>;
```

## Collapsed call tree

- `EdgeCompiler#compileITables()` ... `EdgeCompiler#lookForOverride(interfaceTemplate=Opt, abstractFunc=map, abstractIndex=0)`:
  Per-interface-per-impl override resolution runs for every `(impl, abstractFunc)` pair. For `map`, builds a "dispatcher" template `OverrideDispatcherTemplateNameT(ImplTemplateNameT(opt.vale:260))` living under `FunctionTemplateNameT(map)`. Creates one fresh dispatcher placeholder for the impl's `T` (via `createOverridePlaceholderMimicking`), then substitutes the abstract `self` param so it becomes `&Opt<dispatcher$0_T>`. Calls `evaluateGenericVirtualDispatcherFunctionForPrototype` with `args = [Some(&Opt<dispatcher$0_T>), None]` (None for the `func` param because there's no dispatcher-side placeholder available for `R`).

- `FunctionCompilerSolvingLayer#evaluateGenericVirtualDispatcherFunctionForPrototype()`:
  Runs a **preliminary solve** with those initial sends. Comment at entry documents the intent: "turn `func map<T, F>(self Opt<T>, f F, t T)` into `func map<F>(self Opt<$0>, f F, t $0)`" — i.e. discover which generic params the self interface pins down, and keep the rest as fresh dispatcher-level placeholders.

  For the `map` call:
  - `function.genericParameters = Vector(CodeRuneS(T), CodeRuneS(R))`
  - Preliminary inferences: `CodeRuneS(T) → CoordT(own, KindPlaceholderT(…DispatcherRuneFromImplS(T)))` — T is solved.
  - `CodeRuneS(R)` → **not in preliminaryInferences** (R only appears in the `func &IFunction1<mut,&T,R>` param and return, both behind the unknown `args[1]`).
  - `runeType = Some(CoordTemplataType())`

  The code then iterates `genericParameters`. For T it produces `InitialKnown(T, <solved coord>)`. For R it hits the `case None` branch:

  ```scala
  case None => {
    // Make a placeholder for every argument even if it has a default, see DUDEWCD.
    //  val runeType = vassertSome(function.runeToType.get(genericParam.rune.rune))
    vimpl()                                                                         // <── FAILS HERE
    val placeholderPureHeight = vregionmut(None)
    val templata =
      templataCompiler.createPlaceholder(
        coutputs, callingEnv, callingEnv.id, genericParam, index, function.runeToType,
        placeholderPureHeight, false)
    Some(InitialKnown(genericParam.rune, templata))
  }
  ```

  The code that follows the `vimpl()` is already the intended implementation (create a coord placeholder for R, stuffed into the solver as an `InitialKnown`). The `vimpl()` is a "haven't-validated-this-path-yet" marker, not a real design blocker.

## Architectural audit — is this area consistent with generics zen?

Concern raised: this preliminary-solve-then-partial-specialize dance feels template-like. Is the whole `evaluateGenericVirtualDispatcherFunctionForPrototype` path an anachronism?

**Verdict: the dispatcher-per-impl architecture is the designed endgame, documented extensively in `docs/Generics.md` §§ GTCII, CDFGI, FODAIR, FOSFC, AFCTD (lines 1000–1394). The specific `vimpl()` is a stale TODO inside an otherwise-valid design.** Details:

- **Dispatcher is not a runtime artifact.** `docs/Generics.md:1099`: *"we're conceptually lowering these abstract functions to match-dispatching functions. We're not actually doing this, just thinking this way."* The dispatcher is a compile-time construct that exists purely to resolve which override to route to at monomorphization time.

- **Per-impl mimicking is required by OWPFRD, not a template holdover.** `docs/Generics.md:62–85` (Only Work with Placeholders From the Root Denizen) forbids foreign placeholders from crossing denizen boundaries. When the abstract `map<T,R>` is compiled to resolve override candidates, it must re-phrase the impl's placeholders into its own namespace (`dis$0` mimicking impl's `I`). `createOverridePlaceholderMimicking` at `EdgeCompiler.scala:152–227` is labeled **NNSPAFOC** (Need New Special Placeholders for Abstract Function Override Case) and its comment explicitly documents the OWPFRD motivation. This is generics-zen-compliant.

- **The preliminary solve is load-bearing, not decorative.** For an abstract like `launch<X,Y,Z>(self &ISpaceship<X,Y,Z>, bork X)` with `impl<I,J> ISpaceship<int, I, J> for Raza<I,J>`, the preliminary solve *must* run against `&ISpaceship<int, dis$0, dis$1>` to discover that X=int (pinned concrete by the impl), Y=dis$0, Z=dis$1 (mimicked from impl placeholders). Without this step, the abstract's X would become a fresh dis$X placeholder and lose the `X=int` information that the impl baked in. `docs/Generics.md:1034–1048` walks through this exact scenario.

- **`map<T, R>` is a legitimate extension of the model, not a mismatch.** The doc's worked examples all have abstract functions where every generic param appears in the self-interface type. `map<T,R>` has R only in the `func` param and the return type. The principled handling, fully in keeping with generics zen, is:
  - T gets pinned from the self-interface substitution (to `dis$0` mimicking impl's I).
  - R — not reachable from self — becomes a fresh dispatcher-owned placeholder `dis$R`.
  - The resulting dispatcher signature is effectively `func mapdis<dis$0, dis$R>(&Opt<dis$0>, &IFunction1<mut, &dis$0, dis$R>) Opt<dis$R>`, and the Instantiator (per AFCTD at `docs/Generics.md:1331–1395`) substitutes both `map$T→concrete` and `dis$R→concrete` at monomorphization.
  - This is exactly what the code under the `vimpl()` tries to do. The `vimpl()` is the bug; the logic beneath it is correct.

- **Where the code *does* feel stale.** Three points where the vibe is right even though the architecture is sound:
  1. The sibling paths (`evaluateGenericFunctionFromNonCall` at line 544+ and `StructCompilerGenericArgsLayer.scala:300+, 405+`) were migrated in Dec 2022 to the **incremental placeholdering** model (`incrementallySolve` + `getFirstUnsolvedIdentifyingRune` + `commitStep`). This dispatcher path was not migrated in that commit. The surface-level mechanical difference (one-shot flatMap vs incremental loop) reads as "older code." In practice, DUDEWCD says "never use defaults when compiling a denizen," so incremental stepping would be a no-op here and both approaches are semantically equivalent. Still, the surface-level inconsistency is what makes this path feel like a leftover.
  2. `registerWithCompilerOutputs: false` in the vimpl-gated call. Every other `createPlaceholder` call in the typing pass passes `true`. Mimicked placeholders in `createOverridePlaceholderMimicking` go through the full `declareType`/`declareTypeMutability`/`declareTypeOuterEnv` registration. A fresh dispatcher-owned R placeholder that skips registration will likely break any downstream phase that does `coutputs.lookupMutability(R)`. The `false` is almost certainly an error carried forward from the pre-2022 one-shot version.
  3. No refactor-thoughts doc mentions consolidating or eliminating the dispatcher layer. If this were planned for deprecation, someone would have queued a note in `docs/refactor-thoughts/`. There is none. The Rust port (`FrontendRust/src/typing/function/function_compiler_solving_layer.rs:434–531`) mirrors Scala byte-for-byte, with the same vimpl, implying the migration effort accepts this design.

**Summary.** The gut feeling that "this looks template-like" picks up on two real signals (stale one-shot surface form vs sibling incremental form; the `false` flag that doesn't match anywhere else). But the underlying architecture — per-impl dispatcher + mimicked placeholders + preliminary-solve-to-discover-bindings + fresh placeholders for unbound-by-self generics — is the documented endgame for virtual dispatch under generics zen. The right fix is a narrow one (remove vimpl, flip false→true), not a rethink. If the dispatcher layer ever gets simplified, it'll be because the Instantiator gains the ability to do override resolution at stamp time (currently the typing pass must emit complete itables); that's a larger refactor not on this quest's critical path.

## Origin of the vimpl()

`git log -S "vimpl()" -- FunctionCompilerSolvingLayer.scala` pins the introduction to commit **`960e0eec` "Now using incremental placeholdering for functions, structs, and interfaces."** (Dec 13, 2022).

That commit refactored the **non-virtual** generic-function path (`evaluateGenericFunctionFromNonCall`) and the struct/interface paths from a one-shot DUDEWCD loop to the new **incremental** model: `inferCompiler.incrementallySolve` + `getFirstUnsolvedIdentifyingRune` + `solver.manualStep`. The refactor was motivated by DRSINI (defaults fed in incrementally, so arg inference can override them before the default fires — see commit `fa84d566` for the full DRSINI rationale).

In the same commit the virtual-dispatcher path (the function we're looking at, then named `evaluateGenericFunctionParentForPrototype`) was **not migrated**. The author dropped `vimpl()` into the `case None` branch and left the pre-migration one-shot `createPlaceholder` call beneath it — a TODO marker saying "this path still needs the incremental treatment." Every subsequent refactor since (name change, solver API cleanups, region additions) has kept the `vimpl()` in place.

Before `960e0eec` (earliest version in `12594576`, Sep 29 2022), the branch just did the one-shot `createPlaceholder` with no `vimpl()` and worked. So this is a regression introduced in Dec 2022 by a partial refactor — it was live code, was disabled as a scaffold-for-later-work, and never got re-enabled.

The question "does this path actually need the full incremental treatment?" is worth considering: incremental solving matters when there are **defaults** to feed in one-at-a-time (DRSINI). For dispatcher compilation, DUDEWCD says "never use defaults when compiling a denizen definition — always use a placeholder." So one-shot creation is semantically correct here; migration to the incremental model would be a no-op. The simplest valid fix is therefore to remove the `vimpl()` and keep the one-shot call (but flip `false` → `true` to match the sibling — see below).

## Root cause

The `vimpl()` is a gate that was never flipped on. **For any generic function whose generic params are not fully determined by the virtual `self` param alone, this branch must run.** Examples that hit it:
- `map<T, R>(&Opt<T>, &IFunction1<mut,&T,R>) Opt<R>` — R is not in `self`.
- Any virtual method that takes a second generic arg or returns a generic coord.

Examples that avoid it (preliminary solve closes everything):
- `len<T>(&Opt<T>) int` — T is fully determined by self.
- `getOr<T>(&Opt<T>, T) T` — same.
- `Result`'s `expect<OkType,ErrType>`, `is_ok`, etc. — both generic params are already pinned by the impl-mimicked self placeholder (I see them in the DEBUG output going through the `Some(x)` branch cleanly).

## Sibling reference — what the fix should look like

`FunctionCompilerSolvingLayer#evaluateGenericFunctionFromNonCall` (same file, ~line 560–595) does exactly the same thing for the non-virtual "define a generic function from its definition" path. It uses `incrementallySolve` + `getFirstUnsolvedIdentifyingRune` and on each unsolved rune calls:

```scala
val templata =
  templataCompiler.createPlaceholder(
    coutputs, nearEnv, functionTemplateId, genericParam, index,
    function.runeToType, placeholderPureHeight, /* registerWithCompilerOutputs */ true)
```

Note the **`true`**. That registers the placeholder template id with `coutputs.declareType`, `declareTypeMutability`, and `declareTypeOuterEnv`/`declareTypeInnerEnv` — all needed by later compilation phases that look up the placeholder.

The broken dispatcher branch currently passes `false`, which would create the id but not register it. Almost certainly wrong for R (downstream will look up its mutability and environments).

`StructCompilerGenericArgsLayer` at lines 324 and 428 also pass `true`. The only `false` callsite in the whole typing pass is the one at line 497 (this one) — strongly suggesting it's a leftover from an incomplete migration.

## Proposed fix

Replace the `vimpl()` with nothing and flip `false` → `true`:

```scala
case None => {
  // Make a placeholder for every argument even if it has a default, see DUDEWCD.
  val placeholderPureHeight = vregionmut(None)
  val templata =
    templataCompiler.createPlaceholder(
      coutputs, callingEnv, callingEnv.id, genericParam, index, function.runeToType,
      placeholderPureHeight, true)
  Some(InitialKnown(genericParam.rune, templata))
}
```

`callingEnv` is the `dispatcherOuterEnv` constructed in `EdgeCompiler#lookForOverride`; its id **is** `dispatcherTemplateId`, so `callingEnv.id` is a valid namePrefix under which to hang a new `KindPlaceholderT` for R. The mimicked T placeholder already lives under the same prefix (see `createOverridePlaceholderMimicking` → `dispatcherOuterEnv` as parent), so this is consistent.

## Risks to watch

1. **Registering under `dispatcherTemplateId`** — if some later phase walks placeholders expecting only impl-mimicked ones under a dispatcher, a fresh R placeholder might trip an assertion. Mitigation: run the full AfterRegions suite, not just this test.
2. **`DispatcherRuneFromImplS` wrapping** — impl-mimicked placeholders have names like `KindPlaceholderTemplateNameT(0, DispatcherRuneFromImplS(CodeRuneS(T)))`. The fresh R placeholder would be `KindPlaceholderTemplateNameT(1, CodeRuneS(R))` (no wrapper). That's probably fine — `DispatcherRuneFromImplS` is a marker that "this came from the impl, not the function" — but worth verifying that the Instantiator doesn't depend on all dispatcher placeholders being wrapped.
3. **Region/height** — `vregionmut(None)` passes `None` for `currentHeight`. The sibling uses the same. Safe.

## Outcome after applying fix

Removed the `vimpl()` and flipped `registerWithCompilerOutputs: false → true`. Debug printouts removed.

**Full AfterRegions battery:** 28 pass / 12 fail / 7 ignored — **identical to baseline; no regressions**. Same 12 tests still fail by name.

**Map function specifically:** no longer fails at the vimpl. Now fails further along, during *deferred* compilation of `optutils.vale`'s `getOr<T>(opt &Some<T>, default T) T { return opt.value; }` override body, with:

```
Couldn't convert &Kind$getOr.T to expected return type Kind$getOr.T
```

### Why the test still fails — new downstream issue

The Family 3 vimpl itself is resolved — the dispatcher path now correctly produces a fresh dispatcher-owned placeholder for R (or any self-unreachable generic). But this unblocks more compilation work, and that work surfaces a pre-existing latent bug in `optutils.vale` that no other test exercises:

- `grep -rln "import optutils" Frontend/Tests/test/main/resources/` returns only `programs/genericvirtuals/mapFunc.vale`.
- Compare `optutils.vale`'s `getOr<T>(opt &Some<T>, default T) T { return opt.value; }` — bare return coerces `&T → T`, works for share T but not owned — against `programs/genericvirtuals/getOr.vale`'s explicitly-`&T`-in-and-out form `func getOr<T>(opt &XSome<T>, default &T) &T { return opt.value; }`. The latter is generics-correct; the former is template-era-correct (assumed T concrete per call site, auto-share-collapse).

This is the same family of issue as `quest.md` Common Pitfalls #2 (ShareT collapse) but in the opposite direction — `&T → T` collapse on return. The abstract-time compile can't know if T is share, so the coercion is refused.

### Verdict

- **Family 3 (the vimpl) is fixed.** The code path is now generics-zen-compliant: virtual dispatcher compile correctly produces fresh placeholders for any abstract generic that the impl's self-interface doesn't pin.
- **Map function test is not yet green** — now blocked on an adjacent optutils.vale stdlib issue that was hidden behind the vimpl. Fixing that is out of scope for "Family 3" as written in quest.md and likely needs either (a) an explicit `&` in the optutils.vale return, changing the abstract/override signatures to return `&T`, or (b) language-level work on `&T → T` coercion when T is share.
- **Quest.md needs an update**: Family 3 was labeled "Unblocks: This test only. Doesn't share infrastructure with Families 1 or 2." The second clause is still true, but "unblocks this test" is now false until the optutils issue is addressed. The vimpl fix should be recorded as landed; Map function becomes a new entry (perhaps Family 5: optutils `&T → T` generic coercion).

## Files changed

- `Frontend/TypingPass/src/dev/vale/typing/function/FunctionCompilerSolvingLayer.scala:493` — removed `vimpl()` line + removed stale commented line above it + flipped `false → true` on the `createPlaceholder` call.
- No other files changed; debug instrumentation reverted.
