# CFWG handoff — overload sets passed through generic functions

> **Status:** Active. 2 failing AfterRegions tests (1.5 "Test overload set" and 1.6 "Tests overload set and concept function"). Multi-day project (3–7 days). The architectural decision is genuinely open — read the design space section in full before writing code, and discuss the choice with Evan.

This is a handoff to a junior engineer. It's long on purpose. **Read top to bottom once before touching code.** Vale's typing pass overload-resolution + solver machinery is dense, and this corner is one of the densest. The cheapest way to avoid going in circles is to absorb the context first.

Sections are ordered: orientation → vocabulary → the actual problem → existing machinery you can lean on → design space → suggested attack plan → pitfalls → resources → acronyms.

---

## 1. The one-paragraph orientation

You're working in **Vale's typing pass**, specifically the **overload resolution + solver + bound-discharge** machinery. Two failing tests demonstrate the same gap: when a bare function name (e.g. `myfunc`) is passed as a value to a generic function (e.g. `mylist.each(myfunc)`), the compiler wraps the name as an `OverloadSetT` and the generic's where-clause says "I can call my F arg with this signature." The compiler has to discharge that bound at the callsite — i.e. verify a real callable matching the bound's signature exists, given F's actual value is an OverloadSet. There's no mechanism today to coerce an OverloadSet into a concrete prototype during bound discharge, so the call fails. Your job is to add that mechanism.

> **Status note (2026-04-30):** The previous fallback builtin, `functor1.vale`, has been deleted as vestigial. We verified end-to-end: removing the file and its `Builtins.scala` registration left AfterRegions at 36/5/8 (unchanged) and the wider regression suite (CompilerTests, CompilerVirtualTests, CompilerGenericsTests, IntegrationTestsA/B/C) at 199/199 (unchanged). No test in the codebase actually exercised functor1's hardcoded-`drop` `__call(v void, …)` path. The handoff lore below about "the resolver falls through to functor1" describes a path that no longer exists — and never load-bearingly existed. **CFWG is now a pure greenfield design with no legacy shim to preserve.** Sections that mention functor1 below are kept for historical context but should not influence the design.

The full design is documented in `docs/Generics.md` under **CFWG (Concept Functions With Generics)** at line 201. Two design alternatives are sketched there. A third path opened up after BRRZ (see arcana). None of them is obviously the right one yet.

---

## 2. Prerequisites — what you need to know about Vale before starting

If a section feels too elementary, skim it. The handoff is calibrated for someone who has not previously worked in the typing pass.

### 2.1 The compilation pipeline

```
Source code
  → parser           (AST nodes ending in P)
  → lexer            (AST ending in L)
  → postparser       ("scout"; AST ending in S — FunctionS, IExpressionSE)
  → higher-typing    (AST ending in A — FunctionA)
  → typing pass      (AST ending in T — FunctionDefinitionT, CoordT, KindT)
  → instantiator     (monomorphized AST — FunctionI)
  → simplifier       ("hammer"; flat AST)
  → backend          (LLVM IR)
```

You'll mostly live in the **typing pass** (`Frontend/TypingPass/src/dev/vale/typing/`). One change might bleed into the **postparser** (`Frontend/PostParsingPass/src/dev/vale/postparsing/`) where bound rules are scouted. The **instantiator** is a downstream consumer; you probably won't change it unless your design produces a new kind of bound prototype that needs special instantiator handling.

### 2.2 OverloadSetT — the central concept here

When the user writes a bare function name in argument position — for example `myfunc` in `each(arr, myfunc)`, or `print` in `moo("hello", print)` — the typing pass wraps it as a value of kind `OverloadSetT(env, name)`. This is a deferred lookup: *"the imprecise name `name` resolved to multiple overloads in `env`; I haven't picked one yet."*

OverloadSetT is structurally:
```scala
case class OverloadSetT(
  overloadsEnv: IEnvironmentT,
  nameInOverloadsEnv: IImpreciseNameS
)
```

The `overloadsEnv` field is a **reference** to whatever env the bare name was looked up in (typically the calling env or package env). It's not a custom env synthesized for the OverloadSet — it's a real env with lots of other things in it.

The actual overload gets picked when something concrete happens with the OverloadSet. There are two existing paths:

- **Direct call** — the callable's TYPE is OverloadSetT. Handled at `CallCompiler.scala:51-110`. The compiler does a real overload search in `overloadsEnv` for `nameInOverloadsEnv` with the call's args. Bypasses `__call` entirely. Returns a concrete prototype.
- **Recursive unwrap during candidate search** — handled at `OverloadResolver.scala:203-205`. When the resolver finds an OverloadSet as a candidate during some lookup, it recurses into the OverloadSet's env using the wrapped name. This handles cases like `let lam = somefunc; lam(5)` where `lam` is a binding-of-OverloadSet.

The failing case is **neither of these paths**. It's "OverloadSet bound to a generic param F, used inside a function body where F is abstract." The OverloadSet-ness gets hidden behind the placeholder F during body typing; the callsite knows F=OverloadSet but never gets the chance to coerce.

### 2.3 Bounds and where clauses

A generic function's where clause can declare prototype bounds. Two syntactic forms:

- **Named:** `where func name(P1, P2)R` — promises a function with this name and signature is reachable.
- **Anonymous:** `where func(&F, &T)void` — promises a callable with this signature exists. Implicitly uses the name `__call` (the anonymous-call convention). This is the form `each` uses.

Each prototype bound emits three solver rules during postparsing (see `RuleScout.scala` around line 165, `TemplexScout.scala:199-204`):

- `DefinitionFuncSR` — fires during def-time solve. Establishes the bound prototype as a fact in scope so the function body type-checks.
- `CallSiteFuncSR` — fires during call-site solve. Verifies the prototype is real, given concrete arg values.
- `ResolveSR` — fires during call-site solve. Does the actual real overload lookup against the calling env.

The three rules don't all fire in the same solve. They're filtered per phase by SROACSD (see `InferCompiler.scala:790-808`):

```scala
def includeRuleInDefinitionSolve(rule: IRulexSR): Boolean = rule match {
  case CallSiteCoordIsaSR(_, _, _, _) => false
  case CallSiteFuncSR(_, _, _, _, _) => false
  case ResolveSR(_, _, _, _, _) => false
  case _ => true
}
def includeRuleInCallSiteSolve(rule: IRulexSR): Boolean = rule match {
  case DefinitionFuncSR(_, _, _, _, _) => false
  case DefinitionCoordIsaSR(_, _, _, _) => false
  case _ => true
}
```

This phase split is the substrate the CFWG fix has to work within.

### 2.4 Body vs callsite — where the failure fires

A generic function's **body** is type-checked once, with abstract placeholders. Bounds in the body's solve are treated as facts via `DefinitionFuncSR`, which doesn't do real lookup. The body's calls resolve against these abstract facts. The body of `each` works fine on its own; you don't need to change it.

A **callsite** has concrete F, T, etc. The callsite's solve must discharge each bound — verify it's real — via `CallSiteFuncSR + ResolveSR`. This is where the CFWG failure fires.

When you instrument the failure, you'll see the trace:
```
Couldn't find a suitable function each(arr, myfunc).
  Candidate: each<F>(arr, &F) where func(&F, &T)void
    Couldn't find a suitable function __call((overloads: myfunc), int).
      No candidates.
```

The outer error is "couldn't find each." The inner error is the actual cause: the where-clause discharge of `each` triggered an `__call` lookup against an OverloadSet receiver, which finds no candidates. (Before functor1.vale was deleted, you'd see one rejected candidate with a `void` conflict or a hardcoded-`drop` mismatch — same end result.)

### 2.5 Architectural principle: `@BDPFWDZ`

Read `docs/arcana/ByDefaultPullFromWhereDeclared-BDPFWDZ.md` before designing. The Vale codebase has a leaning principle: **declarations live in the scope that introduced them, and consumers walk to find them rather than copying them downward.** Whatever you design here should respect that principle by default, or document an exception with justification. The arcana lists currently-cataloged push exceptions; ideally you don't add another, but if your design genuinely needs one, you add it to the audit list.

---

## 3. The two failing tests

### 3.1 Test overload set (1.5)

**File:** `Frontend/IntegrationTests/test/dev/vale/AfterRegionsIntegrationTests.scala:133`

**Source:**
```vale
import array.each.*;
func myfunc(i int) { }
exported func main() int {
  mylist = [#](1, 3, 3, 7);
  mylist.each(myfunc);   // myfunc is OverloadSetT here
  42
}
```

The `each` builtin (at `Frontend/Tests/test/main/resources/array/each/each.vale:2`):
```vale
func each<M Mutability, V Variability, N Int, T, F>(arr &[#N]<M, V>T, func &F) void
where func(&F, &T)void {
  // body iterates and calls func(arr[i])
}
```

**Current failure:**
```
Couldn't find a suitable function each(&StaticArray<4, mut, final, i32>, (overloads: myfunc)).
Rejected candidates:
Candidate 1 (of 2): array.each:each.vale:2:1
  Couldn't find a suitable function __call((overloads: myfunc), i32).
  Candidate 1 (of 1): :functor1.vale:5:1
    Solver conflict on rune _41111.kind: was (overloads: myfunc) but now concluding void
```

**Why it fails:** `each`'s `where func(&F, &T)void` bound has to be discharged. With F=OverloadSet(myfunc) and T=int, the discharge fires a `__call(&OverloadSet, &int)` lookup. With functor1.vale deleted, there are *no* candidates for `__call` on an OverloadSet receiver. The bound discharge fails and the outer `each(...)` call is rejected. (Pre-deletion, functor1.vale was the only candidate — and it failed too, just with a different error: either a `void` conflict on its first param or a hardcoded-`drop` mismatch in its where-clause.)

### 3.2 Tests overload set and concept function (1.6)

**File:** `Frontend/TypingPass/test/dev/vale/typing/AfterRegionsTests.scala:57`

**Source:**
```vale
func moo<X, F>(x X, f F)
where func(&F, &X)void, func drop(X)void, func drop(F)void {
  f(&x);
}
exported func main() {
  moo("hello", print);
}
```

**Why it fails:** Same root cause as 1.5. `moo`'s `where func(&F, &X)void` bound has F=OverloadSet(print) at the callsite. Bound discharge fires a `__call(&OverloadSet, &str)` lookup. No candidate exists. Fails.

**Historical caveat in earlier sessions:** some sessions claimed 1.6 "passes" because `expectCompilerOutputs()` (typing-only) succeeded. But `evalForKind()` (full pipeline) fails. **Always verify with `evalForKind()` for these tests.**

### 3.3 Why these are the same family

Both tests pass an OverloadSet through a generic function with a `where func(...)` bound. Same shape, same failure. Whatever design lands for 1.5 should land 1.6 for free. Verify both pass before claiming victory.

---

## 4. functor1.vale — DELETED (was vestigial)

**Was at:** `Frontend/Builtins/src/dev/vale/resources/functor1.vale` (deleted 2026-04-30, along with its line in `Frontend/Builtins/src/dev/vale/Builtins.scala`).

The original contents:

```vale
// This is mainly used for arrays.
// It's not particularly great because it's got that `drop` name hardcoded in there.
// Hopefully soon we can upgrade our generics system to not need this, see CFWG.
func __call<P1 Ref, R Ref>(v void, param P1) R
where F Prot = func drop(P1)R {
  F(param)
}
```

**Verification it was vestigial:** before deletion we ran the full AfterRegions suite (6 suites) + the wider regression suite (CompilerTests, CompilerVirtualTests, CompilerGenericsTests, IntegrationTestsA/B/C, 199 tests). After deletion the same suites produced **identical results** (36/5/8 + 199/0/0). No test changed status. No callsite in the codebase passes a bare name through a `where func(…)` bound *except* the two failing tests 1.5/1.6, and those failed identically with or without functor1 present. All real-world callers of `each`, `drop_into`, etc. pass lambdas (which are anonymous structs, not OverloadSetT, so they go through the lambda's own `__call`, not functor1's).

**Implications for your design:**

- You don't need to "preserve drop's behavior" or rewrite functor1 as a no-op fallback.
- There is no compatibility shim to coordinate with.
- The CFWG mechanism you build is the *only* mechanism — design it cleanly, without legacy weight.
- The `v void` first-param trick (mentioned in §8.8 below) was a workaround for a path that no longer exists. Don't replicate it.

---

## 5. Existing machinery you can build on

These pieces already exist and are correct. Your design will probably extend or reuse them rather than build from scratch.

### 5.1 The OverloadSet direct-call bypass — `CallCompiler.scala:51-110`

When the callable expression's type is directly OverloadSetT, this path bypasses `__call` resolution entirely:
```scala
case OverloadSetT(overloadSetEnv, functionName) => {
  val prototype =
    overloadCompiler.findFunction(
      overloadSetEnv,                  // search the OverloadSet's env
      ...,
      functionName,                    // for THIS name, not "__call"
      ...,
      unconvertedArgsPointerTypes2,    // matching the actual call args
      ...) match { ... }
  ...
}
```
This works because the callable expression's type tells the compiler "this is an OverloadSet — skip `__call`, do the real overload search." Your design probably needs to extend this principle to the case where OverloadSet-ness is hidden behind a generic param.

### 5.2 The OverloadSet candidate-recursion — `OverloadResolver.scala:203-205`

When `getCandidateBannersInner` encounters a candidate that's itself a `KindTemplataT(OverloadSetT(...))`, it recurses into the OverloadSet's env using the wrapped name:
```scala
case KindTemplataT(OverloadSetT(overloadsEnv, nameInOverloadsEnv)) => {
  getCandidateBannersInner(
    overloadsEnv, coutputs, range, nameInOverloadsEnv, searchedEnvs, results)
}
```
This is for cases like `let lam = somefunc; lam(5)` — when the resolver searches for `lam`, it finds the templata bound to OverloadSet, and recurses to find the actual functions. Doesn't directly help our case but is the existing model for "OverloadSet means redirect."

### 5.3 BRRZ — mid-solve real lookup precedent

Read `docs/arcana/BoundReturnResolution-BRRZ.md` in full before designing. **This is the closest existing precedent for what CFWG needs.**

BRRZ established that a solver rule can do a real overload lookup *during* a solve, against the calling env, using the existing `delegate.resolveFunction` API. It did this for return-type discovery (when `where func(...)R` had R unresolved, BRRZ relaxed `ResolveSR`'s puzzle so it could fire when only paramsList was known and use real lookup to discover R).

The same mechanism is structurally what CFWG needs: at bound discharge time, when F is concretely an OverloadSet and the bound's signature is known, do a real overload lookup in the OverloadSet's env using the bound's signature. The safety story BRRZ established (caller declares the bound args, post-solve verification at `InferCompiler.scala:295` enforces it) probably extends to CFWG's lookups too. Read BRRZ's "Why this doesn't re-enable the HashMap regression" section carefully — the safety analysis is reusable.

### 5.4 The bound-rule scout pipeline

`Frontend/PostParsingPass/src/dev/vale/postparsing/rules/RuleScout.scala` and `TemplexScout.scala`. Where the postparser turns `where func(&F, &T)void` into `DefinitionFuncSR + CallSiteFuncSR + ResolveSR` rules. Most CFWG designs reuse these rules; you probably won't add new rule kinds.

### 5.5 The solver — `CompilerSolver.scala`

The handler bodies for `CallSiteFuncSR`, `DefinitionFuncSR`, and `ResolveSR` live around lines 600-700. BRRZ's relaxation is at `ResolveSR`'s puzzle (~line 245) and a new branch (~line 636). Whatever CFWG design you pick will probably touch one or more of these handlers.

### 5.6 InferCompiler — `InferCompiler.scala`

Drives solver invocations. `solveForDefining` (def-time) vs `solveForResolving` (callsite). The post-solve verification at line 295 (`checkResolvingConclusionsAndResolve`) is the safety net BRRZ relies on; CFWG probably relies on it too.

### 5.7 InstantiationBoundArgumentsT

Per call site, the typing pass passes "bound arguments" to the callee — the concrete prototypes used to discharge each bound. The instantiator reads these to monomorphize. If your CFWG design produces a new kind of bound argument (e.g., "an OverloadSet specialized to this signature"), you'll need to fit it into this structure. See `HinputsT.scala:32-49`.

---

## 6. The design space

This is where the architectural decision lives. **There is not yet a canonical answer.** Each option below has a coherent shape, real tradeoffs, and a real cost. Pick a direction *after* discussion with Evan, not before.

### Option A — Per-OverloadSet wrapper env with `__call` aliases

Synthesize a small wrapper env at OverloadSet construction time. The wrapper has the OverloadSet's overloads indexed under both the wrapped name (e.g. `"myfunc"`) and `__call`. Make `getParamEnvironments` walk this wrapper env when the param's kind is OverloadSet.

**For the failing case:** doesn't directly fix it — the receiver inside `each`'s body has type `&F` (placeholder), not OverloadSet, so `getParamEnvironments` for the receiver wouldn't fire the OverloadSet branch. But it could be combined with a callsite-side coercion that resolves F to a concrete prototype before the body uses it.

**Architecture:** clean for direct OverloadSet calls, less clean for through-generic.
**Cost:** new wrapper-env construction; `getParamEnvironments` extension; doesn't on its own bridge the through-generic gap.

### Option B — Solver-side coercion at bound discharge

Modify the `CallSiteFuncSR` (or `ResolveSR`) handler in `CompilerSolver.scala` to recognize when the F rune resolves to an OverloadSet. When detected, do a real overload lookup in the OverloadSet's env using the rule's expected signature. Bind F's prototype to the resulting concrete prototype. From this point on, F behaves like any other concrete callable.

**For the failing case:** directly addresses the failure. The bound discharge becomes real-lookup-driven (BRRZ-style) when F is OverloadSet. (functor1.vale was already deleted — no shim to remove.)

**Architecture:** parallels BRRZ. Same machinery (`delegate.resolveFunction`), same safety story (post-solve verification).
**Cost:** new branch in a solver rule handler. Solver code is dense; reviewer-attention-heavy.

### Option C — Per-instantiation specialization in the instantiator

Keep F as an abstract placeholder during typing. At instantiation, when stamping `each<...,F=OverloadSet(myfunc)>`, specialize the body's calls to direct-OverloadSet form. The body uses F abstractly during typing; the instantiator specializes per concrete F at monomorphization.

**For the failing case:** would work, but requires the instantiator to know how to specialize body calls based on argument kinds. Currently most specialization happens in the typing pass.

**Architecture:** orthogonal to the rest of the typing pass. Pushes work into the instantiator.
**Cost:** instantiator changes are wider blast-radius than typing-pass changes. May not fit Vale's "typing pass does the work, instantiator stamps" model.

### Option D — Add a "name as generic parameter" language feature

(This option originally proposed rewriting functor1's where clause; functor1 has been deleted, but the underlying language-design idea is preserved here for completeness.) Add the ability for a where-clause to take the calling-context's overload-set name as a parameter instead of having names be syntactic literals. The obstacle is that Vale's where-clause syntax doesn't currently support "the name is a generic parameter." Adding that language feature would let user-space code express CFWG-style dispatch directly, but it's a language-design step, not just a typing-pass change.

**For the failing case:** would resolve it elegantly if the language feature existed. May be the right destination state independent of which path you take to get there.

**Architecture:** the cleanest end state if Vale supports name-as-generic-param.
**Cost:** language design + postparser/scout changes + typing-pass support for name-runes. Substantial.

### Option E — Hybrid

Plausibly some designs combine pieces. For example, Option A (wrapper envs) plus Option B (solver-side coercion at bound discharge) covers both direct-OverloadSet calls (cleaner via wrapper) AND through-generic calls (via coercion).

The CFWG design doc at `docs/Generics.md:201-258` itself sketches two alternatives — "prototype-based" (closer to Option B) and "placeholder-based" (closer to Option D + Option B hybrid). The doc says either is viable but describes the placeholder-based version with a "sucks that we need a functor" aside. Read both before settling.

### What to consider when picking

- **Stay-in-place principle (`@BDPFWDZ`).** Does your design copy declarations downward? If yes, document why. If no, prefer the link-walking version.
- **BRRZ as substrate.** If your design fits naturally with BRRZ's mid-solve real-lookup pattern, you're probably aligned with the codebase's direction.
- **Where the failure actually fires.** It's at the callsite, during bound discharge. Designs that fix it at the callsite are smaller-scoped than designs that change the body or the instantiator.
- **functor1.vale is already gone.** Don't reintroduce a hardcoded-name builtin as a "fallback." The mechanism you build is the only mechanism.

---

## 7. Suggested attack plan

This is one viable order. You may discover a different sequence works better as you investigate.

### Phase 0 — Verify baseline (1 hour)

```bash
cd /Volumes/V/ValeAfterRegions/Frontend
sbt 'testOnly dev.vale.AfterRegionsIntegrationTests dev.vale.typing.AfterRegionsTests dev.vale.typing.AfterRegionsErrorTests dev.vale.parsing.AfterRegionsTests dev.vale.parsing.functions.AfterRegionsFunctionTests dev.vale.postparsing.AfterRegionsErrorTests' 2>&1 > /tmp/baseline.txt
grep -E "Tests: succeeded|FAILED \*\*\*" /tmp/baseline.txt
```

Expected: `Tests: succeeded 36, failed 5, canceled 0, ignored 8, pending 0`. The 5 failures should include "Test overload set" (1.5) and "Tests overload set and concept function" (1.6). If the count differs, something already changed before you started — investigate before proceeding.

### Phase 1 — Read everything (1–2 days)

Don't skip this. Junior engineers often dive into code and end up making changes that work locally and break 30 tests elsewhere. The Vale typing pass is dense.

1. `quest.md` — full Family 1 section, including the resolved 1.1 and 1.2 notes (which describe a related-but-different fix).
2. `docs/Generics.md` — CFWG section (lines 201-258). Both design alternatives.
3. `docs/arcana/BoundReturnResolution-BRRZ.md` — full doc. The substrate.
4. `docs/arcana/ByDefaultPullFromWhereDeclared-BDPFWDZ.md` — the design principle.
5. `docs/arcana/EachCallSiteIsItsOwnSolve-ECSIIOSZ.md` — per-call-site solve model.
6. ~~`Frontend/Builtins/src/dev/vale/resources/functor1.vale`~~ — deleted; see §4 for what was there and why removal was safe.
7. `Frontend/TypingPass/src/dev/vale/typing/expression/CallCompiler.scala` lines 35-130 — the OverloadSet bypass.
8. `Frontend/TypingPass/src/dev/vale/typing/OverloadResolver.scala` — top to bottom, with focus on `findFunction`, `getCandidateBanners`, the recursive unwrap at 203, `getParamEnvironments`, `getPlaceholderImplBoundEnvs` (the recently-added Solution C helper for impl bounds — read it as a model for how the impl-bound family was solved).
9. `Frontend/TypingPass/src/dev/vale/typing/infer/CompilerSolver.scala` — handlers for `ResolveSR`, `CallSiteFuncSR`, `DefinitionFuncSR`. BRRZ's relaxation at line 245 + branch at 636.
10. `Frontend/TypingPass/src/dev/vale/typing/InferCompiler.scala` — `solveForDefining`, `solveForResolving`, `checkResolvingConclusionsAndResolve` (line 295, the post-solve verification).
11. `investigations/family1_handoff.md` — the original Family 1 handoff doc. Tests 1.1 and 1.2 are now resolved, but the doc has lots of structural context shared with this one.

**Take notes.** Write down:
- For each rule kind (DefinitionFuncSR, CallSiteFuncSR, ResolveSR), what its puzzle looks like and what its handler does.
- For each env type that participates in OverloadSet handling, what's in it.
- The exact data flow from "user writes `myfunc`" to "OverloadSetT in the typing pass."

### Phase 2 — Pick a test, instrument it (1 day)

Start with **1.5** — it's an integration test (`evalForKind`), so you'll know when the whole pipeline passes.

Add `println` instrumentation along the failure path:
- At the callsite's solve, log the rule firings — which rule fires, what runes are known/unknown at each step.
- At `OverloadResolver.getCandidateBanners` when called from the bound's solve, log the env and paramFilters and the (empty) candidate list.

Goal: a written explanation of the *exact* sequence of calls and rule firings that lead to the rejection, with rune values at each step. Format it like the existing investigations docs (e.g., `investigations/call_bound_wrong_arguments.md`).

**Don't skip this.** A bad fix without understanding the trace will pass 1.5 but break others. Measure twice, cut once.

### Phase 3 — Design discussion with Evan (1 day)

Write a 1–2 page proposal:
- Which option from Section 6 you're picking, or which hybrid.
- Why (referencing the principle in `@BDPFWDZ`, BRRZ as substrate, etc.).
- What files you'll change.
- What could break (other tests, other code paths).

**Discuss with Evan before writing the fix.** This is a deep architectural question. Evan has opinions. The previous Family 1 handoff explicitly punted the design choice to Evan; this one continues that pattern. Don't unilaterally pick.

### Phase 4 — Implement and verify on 1.5 only (1–2 days)

Apply your fix. Run only 1.5:
```bash
sbt 'testOnly dev.vale.AfterRegionsIntegrationTests -- -z "Test overload set"'
```
Iterate until it passes. **Don't move on yet.** Run the full AfterRegions suite after each significant change:
```bash
sbt 'testOnly dev.vale.AfterRegionsIntegrationTests dev.vale.typing.AfterRegionsTests dev.vale.typing.AfterRegionsErrorTests dev.vale.parsing.AfterRegionsTests dev.vale.parsing.functions.AfterRegionsFunctionTests dev.vale.postparsing.AfterRegionsErrorTests' 2>&1 > /tmp/check.txt
grep -E "Tests: succeeded|FAILED \*\*\*" /tmp/check.txt
```
Watch for regressions. Bound-discharge changes have wide blast radius — every generic function with a where clause is potentially affected.

After 1.5 passes, run the wider sanity check:
```bash
sbt 'testOnly dev.vale.typing.CompilerTests dev.vale.typing.CompilerVirtualTests dev.vale.typing.CompilerGenericsTests dev.vale.IntegrationTestsA dev.vale.IntegrationTestsB dev.vale.IntegrationTestsC' 2>&1 > /tmp/wider.txt
grep -E "Tests: succeeded|FAILED \*\*\*" /tmp/wider.txt
```
Compare counts to a pre-change baseline you took in Phase 0.

### Phase 5 — Verify 1.6 and clean up (½ day)

1.6 should pass with the same fix, since they share root cause. If it doesn't, the fix was too narrow.

(functor1.vale is already deleted; nothing to do here.)

### Phase 6 — Final verification and docs (½ day)

Full AfterRegions suite. Wider regression sweep. Update `quest.md` to reflect resolutions:
- Headline `5 Remaining` → `3 Remaining`.
- `36 / 5 / 8` → `38 / 3 / 8` (or `37 / 4 / 8` if 1.6 needs more).
- 1.5 and 1.6 get RESOLVED notes matching the style of 1.1 and 1.2.
- Update `investigations/family1_handoff.md` with the same.

If your design produced new architectural concepts, write an arcana doc for them. Cross-link from BDPFWDZ if relevant. Cross-link from BRRZ if you extended its substrate.

---

## 8. Pitfalls — things that have burned people

### 8.1 Wide blast radius

Bound-discharge changes affect every generic function in the compiler. **Run the full AfterRegions suite after every non-trivial change.** A "small fix" can break 30 tests. Don't accumulate changes; verify incrementally.

### 8.2 Don't pivot without asking

Per Evan's `~/.claude/CLAUDE.md`: "Don't pivot unilaterally." If you start on Option B and discover it won't work, **ask before switching to Option D**. The right move might be "abandon and try X first." That's Evan's call.

### 8.3 Don't make temporary programs (DMTP)

When debugging, don't write a throwaway `.vale` file. Add a new test case in the project. The test cases are valuable; throwaway programs aren't.

### 8.4 Resist the lazy fix

Special-casing the failure path for `myfunc` specifically (e.g., "if F's name is non-`drop`, do X") works for 1.5 and 1.6 in isolation but won't generalize. Aim for a principled OverloadSet→Prot coercion at bound discharge that handles any name. If you find yourself reaching for a hardcoded name in any new builtin or solver branch, stop — that's how functor1 happened.

### 8.5 The "test passes typing but fails eval" trap

`compile.expectCompilerOutputs()` runs only the typing pass. `compile.evalForKind(...)` runs the whole pipeline. CFWG issues frequently fail at later phases. **For 1.5 and 1.6, prefer evalForKind whenever possible.** Earlier sessions thought 1.6 "passed" because typing succeeded. It didn't really pass.

### 8.6 Don't use `git checkout` to revert

Per Evan's `CLAUDE.md`: never use `git checkout` to revert a file. Use `git diff` and apply changes manually. This came up after a previous agent destroyed work.

### 8.7 The `@BDPFWDZ` principle

The arcana names a design leaning: don't propagate declarations downward. If your fix harvests OverloadSet info into the calling function's near-env (or anywhere else), you're potentially adding a push exception that needs justification in the arcana. The principle doesn't prohibit push, but it requires you to think about why pull wouldn't work first.

### 8.8 The `v void` first-param thing in functor1 (historical)

This subsection is preserved for archaeological context only — functor1.vale is gone. The historical note: functor1's first param `v void` was the OverloadSet being called *through* (the receiver), typed as `void` because the OverloadSet has no runtime representation and its value is meaningless once we've committed to dispatching by name. Don't reintroduce that hack — whatever you design should make the OverloadSet receiver explicit (typed as OverloadSet) or eliminate the receiver argument entirely at the dispatch boundary.

---

## 9. Resources and where to ask for help

- **`quest.md`** — master tracker. Resolved sections (Family 1.1, 1.2, Family 2, Family 4.x) are good models for how to write your own RESOLVED notes.
- **`docs/Generics.md`** — design bible. CFWG (line 201), NBIFP (line 489). When you see an unfamiliar acronym in code or comments, grep `docs/` for it.
- **`docs/arcana/`** — focused design docs, one acronym each. Especially:
  - `BoundReturnResolution-BRRZ.md` — directly relevant precedent.
  - `ByDefaultPullFromWhereDeclared-BDPFWDZ.md` — design principle.
  - `EachCallSiteIsItsOwnSolve-ECSIIOSZ.md` — per-call-site solve model.
- **`docs/historical/`** — past handoffs and post-mortems. `BRRZ-postmortem.md` is the most directly relevant; CFWG's substrate work will rhyme.
- **`investigations/family1_handoff.md`** — the original Family 1 handoff. 1.1 and 1.2 (impl bounds) are resolved; the doc still has lots of structural context.
- **`investigations/`** — per-test investigation docs. `family3_map_function.md` is a model for thorough investigation writing.

When in doubt, **ask Evan**. The bound-propagation machinery is one of the highest-velocity areas of the compiler, and Evan's current mental model isn't fully written down.

---

## 10. Definition of done

CFWG is "done" when:

1. Both 1.5 and 1.6 pass (via evalForKind for 1.5, via expectCompilerOutputs for 1.6 plus a follow-up evalForKind verification if applicable).
2. Full AfterRegions suite is at **38/3/8** or **37/4/8**.
3. No regressions in `dev.vale.typing.CompilerTests`, `CompilerVirtualTests`, `CompilerGenericsTests`, `IntegrationTestsA/B/C` compared to pre-change baseline.
4. functor1.vale stays deleted (already done; don't reintroduce).
5. Each test has a RESOLVED note in `quest.md` matching the style of Family 1.1 / 1.2.
6. `quest.md`'s headline counts and verification line are updated.
7. A summary commit lands documenting the architectural choice (which option, why) — possibly accompanied by a new arcana doc in `docs/arcana/`.
8. If new architectural concepts were introduced, they're cross-linked from BDPFWDZ and/or BRRZ.

Optional but encouraged: an investigation doc in `investigations/` capturing the journey, traps hit, and what didn't work — for future readers.

Good luck. Take it slow on Phase 1 (read everything). Phase 3 (design discussion with Evan) is where the real architectural call happens. Phase 4 is where the code changes; if Phase 1-3 went well, Phase 4 is straightforward.

---

## 11. Acronyms cheat sheet

- **CFWG** — Concept Functions With Generics. The design you're implementing. `docs/Generics.md:201`.
- **BDPFWDZ** — By Default Pull From Where Declared. The Vale architectural principle that should guide your design. `docs/arcana/ByDefaultPullFromWhereDeclared-BDPFWDZ.md`.
- **BRRZ** — Bound Return Resolution. The mid-solve real-lookup precedent your design will likely build on. `docs/arcana/BoundReturnResolution-BRRZ.md`.
- **NBIFP** — Need Bound Information From Parameters. Companion concept (citizen-typed param bound harvesting). Currently a push-style implementation that's flagged as technical debt under @BDPFWDZ.
- **ECSIIOSZ** — Each Call Site Is Its Own Solve. Per-call-site solve model. Substrate that your design will run inside.
- **DRSINI** — Default Rules Should Be Incremental Not Initial. Generic param defaults handling.
- **MKRFA** — Must Know Runes From Above. Not directly relevant but mentioned in adjacent code.
- **SROACSD** — Solve-Rule-Origin-And-Call-Site-vs-Definition. The phase split for which rules fire in which solve.
- **SFWPRL** — Solve First With Predictions, Resolve Later. Predict-then-resolve pattern that BRRZ operates under.
- **LAGTNGZ** — Lambdas Are Generic Templates Not Generics. Mostly orthogonal, but lambdas-as-arguments is a related case worth being aware of.
- **OverloadSetT** — kind for an unresolved bare function name in value position. The central concept here.
- **functor1** — former builtin hack (deleted 2026-04-30 as vestigial). Was at `Frontend/Builtins/src/dev/vale/resources/functor1.vale`. See §4.

If you see an acronym not in this list: `grep -rn "ACRONYM" /Volumes/V/ValeAfterRegions/docs/` should find its definition.
