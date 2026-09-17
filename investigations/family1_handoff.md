# Family 1 handoff — impl-bound propagation into overload resolution

> **Status:** Partially resolved as of 2026-04-30. Tests 4.1 and 4.2 (the impl-bound-on-placeholder cases) shipped via Solution C in `OverloadResolver.getPlaceholderImplBoundEnvs`, principle-aligned with @BDPFWDZ. Tests 4.3, 4.4, 4.5, 4.6 still open. Tests 4.5 and 4.6 are the CFWG (Concept Functions With Generics) sub-family and need their own plan. Test 4.3 is a concrete-receiver override-search case, mostly orthogonal to Solution C. Test 4.4 has a test-design bug on top of Family 1 mechanics. Sections below are kept for context but the "shared root cause" is now narrower than originally scoped.

This is a handoff to a junior engineer. It's long on purpose. **Read top-to-bottom once before touching code.** The Vale compiler's bound-propagation machinery has half a dozen interacting parts; the cheapest way to avoid going in circles is to absorb the context first.

If a section feels too elementary, skim it. Sections are ordered: orientation → vocabulary → the actual problem → the 6 tests → existing machinery → suggested attack plan → pitfalls.

---

## 1. The one-paragraph orientation

You're working in **Vale's typing pass**, specifically the **overload resolution + solver** machinery. You're fixing 6 tests that all fail because **the compiler can't use a generic function's `where` clause to inform method calls inside that function's body**. In the old template system, when a function like `func launchGeneric<T>(x &T) { x.launch(); }` was called with `T=Raza`, the body was recompiled with T concrete and `x.launch()` resolved trivially. In the new generics system, the body is type-checked once with T abstract, and `x.launch()` has to be validated against the bounds (`where implements(T, IShip)`) without knowing T's concrete value. **The bounds are declared but not propagated into overload resolution** — that's the gap, and that's what you're closing.

The full design is documented in `docs/Generics.md` under **CFWG (Concept Functions With Generics, line 201)** and **NBIFP (Need Bound Information From Parameters, line 489)**. Two design alternatives exist; either is viable. The key insight is that the compiler needs to treat the `where` clause's declared bounds as facts available to the overload resolver during body type-checking.

---

## 2. Prerequisites — what you need to know about Vale before starting

### 2.1 The compilation pipeline

```
Source code
  → parser           (output: AST nodes ending in `P` — e.g. FunctionP, ParameterP)
  → lexer            (output: lexer AST ending in `L` — e.g. FunctionL, StructL)
  → postparser       ("scout"; output: scouted AST ending in `S` — FunctionS, IExpressionSE)
  → higher-typing    (output: AST ending in `A` — FunctionA)
  → typing pass      (output: typed AST ending in `T` — FunctionDefinitionT, CoordT, KindT)
  → instantiator     (output: monomorphized AST ending in `I` — FunctionI)
  → simplifier       ("hammer"; output: simple AST)
  → backend          (LLVM IR generation)
```

You'll mostly live in the **typing pass** (`Frontend/TypingPass/src/...`). Some changes may bleed into the **postparser** (`Frontend/PostParsingPass/src/...`) where rules are scouted. The **instantiator** is mentioned as a reference in some places but you probably won't change it.

The same conceptual entity exists at multiple stages: `FunctionA` (higher-typing), `FunctionS` (scout), `FunctionDefinitionT` (typing), `FunctionI` (instantiator). They're transformations of each other.

### 2.2 Runes, generic params, bounds — vocabulary

- **Rune**: a named type variable. In `func foo<T>(x T) where T Ref` , `T` is a rune. Runes can be **explicit** (user-written, like `T`) or **implicit** (compiler-generated, e.g. for an anonymous coord in `(x, y) => ...`).
- **Identifying rune** = **generic parameter**: a rune that's part of the function's external identity. The compiler needs to know its value to pick which instantiation to call. `<T>` in `func foo<T>(...)` is an identifying rune. Internally, identifying runes are **placeholders** during def-time type-checking and concrete templatas at instantiation time.
- **Bound rune / bound clause**: something declared in `where`. Three flavors that show up in Family 1:
  - `where T Ref` — declares T's *kind* (Ref means coord, Kind means kind, etc.). Type-level only.
  - `where implements(T, ISomeInterface)` — declares "any T passed must implement ISomeInterface." This is what we'd call a **trait bound** in Rust.
  - `where func name(T)R` — declares "there must exist a function called `name` taking T and returning R." This is a **concept-function bound** (CFWG territory).
- **Placeholder**: at def-time, identifying runes (and per the deferred-solving exploration, possibly other unsolved runes) are represented as `KindPlaceholderT(IdT(_, _, KindPlaceholderNameT(...)))`. They look like normal kinds but their `id` is a placeholder name. Substitution at instantiation time replaces them.
- **OverloadSetT**: a special "kind" that means "the name `myfunc` resolved here, but we don't know which overload until we see the call." When you write `mylist.each(myfunc)` and `myfunc` has multiple overloads, the argument is typed as `OverloadSetT(env, "myfunc")`. The eventual overload is picked when the callee says "I want F to be callable with this signature."

### 2.3 Bounds at the typing pass

Every generic function has two related rule sets:

1. **Definition rules** — used when the function's *own body* is being type-checked. Filters at `InferCompiler.includeRuleInDefinitionSolve` (line 801).
2. **Call-site rules** — used when a *caller* invokes the function. Filters at `InferCompiler.includeRuleInCallSiteSolve` (line 790).

The same `where` clause emits multiple rule variants — `DefinitionFuncSR`, `CallSiteFuncSR`, `ResolveSR`, `DefinitionCoordIsaSR`, `CallSiteCoordIsaSR` — to drive these two solves separately. **Family 1's gap is that the body-time solve doesn't honor the implications of `implements(T, X)` and `func name(...)` bounds for purposes of overload resolution.**

### 2.4 Where the relevant machinery lives

These are the files you'll spend most of your time in. **Read each one's top comment + the function signatures before editing anything.**

| File | What lives there |
|---|---|
| `Frontend/TypingPass/src/dev/vale/typing/OverloadResolver.scala` | The function `findPotentialFunction` is the heart of overload resolution. It tries to match candidates against (name, args). Family 1's symptom is rejection here that *should* be acceptance. |
| `Frontend/TypingPass/src/dev/vale/typing/infer/CompilerSolver.scala` | The rule-firing logic. `RefListCompoundMutabilitySR`, `AugmentSR`, `CallSR`, `ResolveSR`, `CallSiteFuncSR`, `DefinitionFuncSR`, `CallSiteCoordIsaSR`, `DefinitionCoordIsaSR`, `MaybeCoercingCallSR` — all here. The handler at line 909 (AugmentSR) and line 1048 (RefListCompoundMutabilitySR) are good examples of how rules commit conclusions. |
| `Frontend/TypingPass/src/dev/vale/typing/InferCompiler.scala` | Drives solver invocations. Has the def/call-site rule filters (lines 790-808). Sees the result of solves. |
| `Frontend/TypingPass/src/dev/vale/typing/EdgeCompiler.scala` | Handles impl declarations. The current filter at lines 421-428 strips nested-type-arg bounds (this is the IRBFPTIPT gap mentioned in quest.md). Also where impl→virtual-dispatcher wiring lives. |
| `Frontend/TypingPass/src/dev/vale/typing/function/FunctionCompilerSolvingLayer.scala` | Top-level solve drivers for function definitions. The plumbing at lines 555-557 is the existing partial bound-propagation work referenced in quest.md. |
| `Frontend/TypingPass/src/dev/vale/typing/citizen/ImplCompiler.scala` | `isParent(coutputs, env, callRange, callLocation, subKind, superKind)` — the function that decides whether an impl exists for a given pair. Returns `IsParent(...)` or `IsntParent`. Used by `AsSubtypeMacro` and elsewhere. |
| `Frontend/PostParsingPass/src/dev/vale/postparsing/rules/RuleScout.scala` | Where `where` clauses get translated into the rule types listed above. Lines 165-175 (`refListCompoundMutability`), 199-204 (`func` clauses produce DefinitionFuncSR + CallSiteFuncSR + ResolveSR triple). |
| `Frontend/Builtins/src/dev/vale/resources/functor1.vale` | The hack that's blocking 1.5 and 1.6. 4 lines. Read it. |

### 2.5 The CFWG and NBIFP design docs

**`docs/Generics.md:201` — CFWG (Concept Functions With Generics)**: this is the design you're implementing. It describes two alternative approaches:

1. **Prototype-based** — when the user writes `where F Prot = func name(P1)R`, the compiler creates rules that look up `name` via overload resolution and bind F's prototype to the result.
2. **Placeholder-based** — F is a generic param of unknown kind; the bound `func name(P1)R` adds rules that constrain F to be callable with the right shape.

Either is viable. They produce equivalent end-states but differ in solver complexity.

**`docs/Generics.md:489` — NBIFP (Need Bound Information From Parameters)**: this is the *companion* problem. CFWG is about declaring bounds; NBIFP is about how a function in scope can use bounds attached to its *parameters' types* (e.g. `func bork<LamT>(self &BorkForwarder<LamT>) int { return (self.lam)(); }` works because `BorkForwarder<LamT>`'s `where __call(&LamT)int` bound flows into bork). Family 1's tests are mostly CFWG, but understanding NBIFP helps because the two interact.

**`docs/arcana/BoundReturnResolution-BRRZ.md`**: closely related. BRRZ already established the precedent of "real overload lookup at call site mid-solve" for return types. The CFWG fix you're doing is the parameter-and-method-call analog. Read this doc before designing — it has detailed safety analysis you can reuse.

### 2.6 The deferred-solving design exploration

**`investigations/deferred-solving-across-def-callsite.md`** captures a separate-but-related architectural exploration. It proposes letting the def-time solve be incomplete and finishing at each call site. **Don't conflate it with Family 1.** Family 1 can be done without deferred-solving. But the same code paths are touched, and your work might end up informing or being informed by that direction. If you find yourself wanting to defer rules, read that doc — it's where that design lives.

---

## 3. The shared root cause

In Vale's old template system, generic functions were re-compiled per call site with concrete types. So:

```vale
func launchGeneric<T>(x &T) where implements(T, IShip) {
  x.launch();   // resolves trivially when T=Raza, x: &Raza, launch(&Raza) exists
}
launchGeneric(Raza());
```

…compiled the body with `T=Raza`, and `x.launch()` was just `(x: &Raza).launch()` — a normal method call. Bounds declarations were essentially documentation; nothing checked them at compile time because per-call-site expansion did the work.

In the new generics system, `launchGeneric`'s body is type-checked **once**, with T as a placeholder. So `x.launch()` has to be validated against the bound `implements(T, IShip)` without knowing T's concrete value. The compiler needs to reason: "T implements IShip, so T has all of IShip's methods, including `launch`. Therefore `x.launch()` is OK." It doesn't currently do this.

When the overload resolver searches for `launch(&T)`, it tries each candidate function. The candidate `func launch(self &Raza)` is rejected with **"Bad super kind in isa: Raza"** — meaning "the candidate wants Raza, but you have T, and we don't see how T relates to Raza." The `implements(T, IShip)` bound is *declared* in the function's where clause but *not consulted* during this rejection.

The same gap appears in three flavors:

- **Method calls on generic-typed values** (1.1, 1.2): `x.launch()` where x: T, with `where implements(T, ISome)`.
- **Virtual dispatch error reporting** (1.3): error path of the same machinery — when an interface impl is missing the override, the error reporter doesn't see the type as implementing the interface.
- **`__call` via OverloadSet** (1.5, 1.6): `mylist.each(myfunc)` passes `myfunc` as an `OverloadSetT`. The `each` function expects `where func(&F, &T)void`. The `__call(F, T)` lookup goes to `functor1.vale` which has hardcoded "drop" in its bound. Doesn't generalize.

Each test surfaces a different facet but they share the same root.

---

## 4. The 6 tests, in detail

Read each test carefully. Run it. See the actual error. Don't trust descriptions — verify.

### 4.1 — Method call on generic data — RESOLVED 2026-04-30

**RESOLVED:** Solution C (`OverloadResolver.getPlaceholderImplBoundEnvs`) walks the placeholder's IsaTemplataT to IShip's outer env at lookup time, surfacing the abstract `launch(virtual self &IShip)` as a candidate. The inner per-call-site solve verifies T isa IShip via `ImplCompiler.isParent`. Test source updated: `vimpl()` replaced with assertions that main's body has a `FunctionCallTE` for `launchGeneric<Raza>` and launchGeneric's body has a `FunctionCallTE` for `launch`. Per @BDPFWDZ. Sections below are kept for historical context.


**File:** `Frontend/TypingPass/test/dev/vale/typing/AfterRegionsTests.scala:22`

**Source:**
```vale
sealed interface IShip { func launch(virtual self &IShip); }
struct Raza { fuel int; }
impl IShip for Raza;
func launch(self &Raza) { }

func launchGeneric<T>(x &T) where implements(T, IShip) { x.launch(); }

exported func main() { launchGeneric(&Raza(42)); }
```

**Current failure:**
```
Couldn't find a suitable function launch(&Kind$launchGeneric.T). Rejected candidates:
Candidate 1 (of 1): tvl:140
Bad super kind in isa: Raza
func launch(self &Raza) { }
```

**Trailing `vimpl()`:** yes, near line 51. Once compilation works, also replace this with real assertions on launch counts / no-upcast checks.

**Why it fails:** The overload search finds `func launch(self &Raza)` as the only candidate. To accept it, the search must recognize that the call-site arg type `&T` (where T is a placeholder) is compatible with `&Raza` because `implements(T, IShip)` and `IShip for Raza` together imply T can be dispatched as IShip, and IShip has a launch method... but currently the search doesn't follow this reasoning.

**Fix shape:** When `findPotentialFunction` rejects a candidate with "Bad super kind in isa," check the calling function's `implements`-bounds. If any bound `implements(T, SomeInterface)` exists where the rejected candidate is a method on `SomeInterface`, accept the candidate (the dispatch will go through SomeInterface's vtable).

### 4.2 — Impl rule — RESOLVED 2026-04-30

**RESOLVED:** Same Solution C fix as 4.1. Test source updated from `x T` (owned, would have required a `where func drop(T)void` bound) to `x &T` (borrow, no drop bound needed). Test assertion was originally checking the typing pass had monomorphized to Firefly via `templateArgs.last`, but per the current generics architecture monomorphization happens in the instantiator pass — the typing pass produces only the placeholder-T template. Test now asserts the template's `templateArgs.last` is the placeholder T, and that main's body has a `FunctionCallTE` resolving to `genericGetFuel<Firefly>`. Per @BDPFWDZ. Sections below kept for historical context.


**File:** `Frontend/TypingPass/test/dev/vale/typing/AfterRegionsTests.scala:202`

**Source:** Like 4.1 but with `getFuel` instead of `launch`, and the test uses `expectCompilerOutputs()` plus an assertion on `genericGetFuel`'s last templateArg being `Firefly` kind.

**Current failure:** `No ancestors satisfy call: (arg 0) = &Kind$genericGetFuel.T`

**Same root cause as 4.1.** Same fix. They'll go green together.

### 4.3 — Reports error

**File:** `Frontend/TypingPass/test/dev/vale/typing/AfterRegionsErrorTests.scala:109`

**Source:**
```vale
interface A { func foo(virtual self &A); }
struct B { }
impl A for B;
// Missing: func foo(self &B);
exported func main() { foo(&B()); }
```

**Expected error:** "missing override for foo on B" (the impl is incomplete).

**Current failure:** `Couldn't find a suitable function foo(&B)` — wrong error. The override-search machinery doesn't see B as implementing A.

**Why it's in this family:** Same `implements`-propagation gap as 4.1/4.2, but in error-reporting code instead of valid-code-path code. Once 4.1's fix lands, the override search can find foo on A, see it should be overridden by B, and emit the missing-override error correctly.

**Trailing `vimpl()`:** yes. After fix, replace with `case Err(MissingOverrideForX(...))` or whatever the right error class is.

### 4.4 — Lambda is incompatible anonymous interface

**File:** `Frontend/TypingPass/test/dev/vale/typing/AfterRegionsErrorTests.scala:191`

**Source:**
```vale
interface AFunction1<P Ref> { func __call(virtual this &AFunction1<P>, a P) int; }
exported func main() {
  arr = AFunction1<int>((_) => { 4 });   // lambda body returns int; interface returns int
}
```

**Expected error:** `BodyResultDoesntMatch(_, _, _, _)` — the lambda body's type should mismatch the interface method's expected return type.

**⚠️ DOUBLE BUG:** This test is broken in two ways:
1. **Family 1 issue**: it depends on `Generic interface anonymous subclass` infrastructure (which is the `ignore`d aspirational test at line 75), so the test never even reaches the body-result-matching phase.
2. **Test design bug**: even if Family 1 lands, the lambda body `(_) => { 4 }` returns `int` and the interface's `__call` also returns `int`. They MATCH. The test would return `Ok`, not `BodyResultDoesntMatch`. Whoever rewrites this needs to make the lambda return something incompatible (e.g., `(_) => { "hello" }` returning `str`).

`BodyResultDoesntMatch` is real — defined and thrown at `FunctionBodyCompiler.scala:94, 130`. The error type works; the test just doesn't trigger it.

**Order:** fix Family 1's main blockers first, then come back to this. Don't try to fix this until 4.1 is solid.

### 4.5 — Test overload set

**File:** `Frontend/IntegrationTests/test/dev/vale/AfterRegionsIntegrationTests.scala:78`

**Source:**
```vale
import array.each.*;
func myfunc(i int) { }
exported func main() int {
  mylist = [#](1, 3, 3, 7);
  mylist.each(myfunc);   // myfunc is an OverloadSetT here
  42
}
```

**Current failure:** `Couldn't find a suitable function __call((overloads: myfunc), i32)` — because the only `__call` candidate is `functor1.vale`'s hack with hardcoded "drop".

**Why it's in this family:** This is the canonical CFWG case. `each` declares `where func(&F, &T)void`. The solver sees F=OverloadSetT(myfunc), T=int, and tries to find `__call(F, T)`. The functor1.vale `__call`:

```vale
// functor1.vale
func __call<P1 Ref, R Ref>(v void, param P1) R
where F Prot = func drop(P1)R { F(param) }
```

…has two issues:

- The `v void` first param doesn't match the OverloadSetT being passed.
- The `where F Prot = func drop(P1)R` literal hardcodes "drop" — emits `resolve-func StrI(drop)(...)`. So this builtin only resolves for the `drop` overload set, not for `myfunc`, `print`, or any other.

The functor1 file is explicitly self-deprecating: *"It's not particularly great because it's got that drop name hardcoded in there. Hopefully soon we can upgrade our generics system to not need this, see CFWG."*

**Fix:** This is where you implement CFWG. Either:

1. **Prototype-based design** (Generics.md:209): emit rules that, given `F` and concrete `P1, R`, look up `name(P1)R` via real overload resolution and bind F's prototype to the result. This is the BRRZ pattern extended to parameter bounds.
2. **Placeholder-based design** (Generics.md:228): F is a generic param; the bound adds rules that constrain F to be callable. Probably more complex but more flexible.

Once CFWG works, rewrite functor1.vale to use the new mechanism (no hardcoded "drop").

### 4.6 — Tests overload set and concept function

**File:** `Frontend/TypingPass/test/dev/vale/typing/AfterRegionsTests.scala:57`

**Source:**
```vale
func moo<X, F>(x X, f F) where func(&F, &X)void, func drop(X)void, func drop(F)void { f(&x); }
exported func main() { moo("hello", print); }
```

**Current failure:** `Couldn't find a suitable function __call((overloads: print), str). No function with that name exists.`

**Same root cause as 4.5.** Same fix. Will go green when CFWG is wired up.

**Historical caveat:** Earlier sessions claimed this test "passes" because `expectCompilerOutputs()` (typing-pass-only) succeeded. But `evalForKind()` (full pipeline) fails. The typing pass tolerates the unresolved bound; later phases don't. **Run the full pipeline (`compile.evalForKind(...)`) when verifying.**

---

## 5. Existing partial machinery

These are pieces that already exist but aren't enough on their own. **Read all of these before designing your fix** — you'll likely build on top of them rather than replacing.

### 5.1 The def/callsite rule split

`InferCompiler.scala:790-808` defines two filters:

```scala
def includeRuleInCallSiteSolve(rule: IRulexSR): Boolean = rule match {
  case DefinitionFuncSR(_, _, _, _, _) => false
  case DefinitionCoordIsaSR(_, _, _, _) => false
  case _ => true
}

def includeRuleInDefinitionSolve(rule: IRulexSR): Boolean = rule match {
  case CallSiteCoordIsaSR(_, _, _, _) => false
  case CallSiteFuncSR(_, _, _, _, _) => false
  case ResolveSR(_, _, _, _, _) => false
  case _ => true
}
```

When the postparser scouts a `where func name(P1)R` clause (`RuleScout.scala:199-204`), it emits **three** rules: `DefinitionFuncSR`, `CallSiteFuncSR`, `ResolveSR`. Each fires only in one phase. The Definition variant says "in the body, treat this name as a known prototype." The CallSite variant says "at the call site, validate that the actual function passed has this signature." The Resolve variant performs the actual lookup at call time.

This is the substrate. Your CFWG implementation hooks into it.

### 5.2 BRRZ — the precedent for "real lookup mid-solve"

`docs/arcana/BoundReturnResolution-BRRZ.md` documents how BRRZ extended the solver with a "real overload lookup mid-solve" capability for return types. **Read this entire arcana before designing your CFWG fix.**

The relevant code:
- `CompilerSolver.scala:245` — `ResolveSR`'s puzzle was relaxed to fire when only paramsListRune is known.
- `CompilerSolver.scala:636` — new handler branch that calls `delegate.resolveFunction`.
- `InferCompiler.scala:295` — the post-solve bound-arg verification that catches the HashMap regression.

The safety analysis in BRRZ is reusable: when you do real lookups mid-solve for parameter bounds (CFWG), the same caller-declares-bounds invariant has to hold. Read the "Why this doesn't re-enable the HashMap regression" section in BRRZ.

### 5.3 ImplCompiler.isParent

`Frontend/TypingPass/src/dev/vale/typing/citizen/ImplCompiler.scala`. The function `isParent(coutputs, env, callRange, callLocation, subKind, superKind)` returns `IsParent(...)` or `IsntParent`. This is what `AsSubtypeMacro` (and likely your fix for 4.1) calls to decide whether a downcast is legitimate.

For Family 1's 4.1 case: when `findPotentialFunction` rejects a candidate with "Bad super kind in isa," your fix can call `isParent` to check if the placeholder T is declared (via bound) to implement the candidate's interface. Currently this consultation doesn't happen.

### 5.4 OverloadSetT and the `__call` lookup chain

`OverloadSetT(env, name)` is a kind. When the typing pass needs to call a value of type `OverloadSetT`, it goes through the `__call` lookup chain:

- `CallCompiler.scala:51` — entry for OverloadSetT-targeted calls.
- `OverloadResolver.scala:197` — the overload search for `__call`.
- `functor1.vale` — the (currently-broken) builtin __call.

For 4.5/4.6, the chain currently resolves to functor1.vale's hardcoded-drop __call. Your CFWG fix should make the chain resolve to a synthesized prototype based on the calling context's name.

### 5.5 The IRBFPTIPT gap (related, possibly relevant)

`EdgeCompiler.scala:421-428` has a filter that strips nested-type-arg bounds. The "Diff iter" test in the ignored bucket is blocked by this. If your Family 1 fix generalizes naturally to nested-type-arg bounds, you might unblock that ignored test as a bonus. Don't aim for it directly — just don't actively prevent it.

---

## 6. Suggested attack plan

This is one viable order. You may discover a different order works better as you investigate.

### Phase 0 — Verify baseline (1 hour)

```bash
cd /Volumes/V/ValeAfterRegions/Frontend
sbt 'testOnly dev.vale.AfterRegionsIntegrationTests dev.vale.typing.AfterRegionsTests dev.vale.typing.AfterRegionsErrorTests dev.vale.parsing.AfterRegionsTests dev.vale.parsing.functions.AfterRegionsFunctionTests dev.vale.postparsing.AfterRegionsErrorTests' 2>&1 > /tmp/baseline.txt
grep -E "Tests: succeeded|FAILED \*\*\*" /tmp/baseline.txt
```

Expected: `Tests: succeeded 33, failed 7, canceled 0, ignored 8, pending 0`. The 7 failures should include the 6 Family 1 tests plus "Reports error" (which is 4.3).

If the count differs, something already changed before you started — investigate before proceeding.

### Phase 1 — Read, don't write (1–2 days)

**Read everything below before writing any code.** Junior engineers often skip this and end up making 3-line changes that break 30 tests. The Vale compiler is well-architected but dense. Reading is the cheap part.

1. `quest.md` — full Family 1 section.
2. `docs/Generics.md` — CFWG section (lines 201-258), NBIFP section (lines 489-650). Skim the rest.
3. `docs/arcana/BoundReturnResolution-BRRZ.md` — full doc.
4. `Frontend/Builtins/src/dev/vale/resources/functor1.vale` — 4 lines, plus the comment.
5. `Frontend/TypingPass/src/dev/vale/typing/OverloadResolver.scala` — read top to bottom. It's ~600 lines but most are case-analyses; the structure is clear.
6. `Frontend/TypingPass/src/dev/vale/typing/InferCompiler.scala` — focus on `interpretResults`, `checkResolvingConclusionsAndResolve`, the def/callsite filters at lines 790-808.
7. `Frontend/TypingPass/src/dev/vale/typing/infer/CompilerSolver.scala` — focus on the rule handlers around lines 470-1000. Especially `ResolveSR` (line 600+), `CallSiteFuncSR`, `DefinitionFuncSR`. Look at how AugmentSR commits conclusions (line 909) and how RefListCompoundMutabilitySR does (line 1048) for shape patterns.
8. `Frontend/TypingPass/src/dev/vale/typing/citizen/ImplCompiler.scala` — `isParent` and friends.
9. `Frontend/PostParsingPass/src/dev/vale/postparsing/rules/RuleScout.scala:165-220` — where the rules you'll hook into get scouted.

**Take notes.** Write down what each rule type is for, what its puzzle is, what its handler does. You'll forget otherwise.

### Phase 2 — Pick one test, instrument it (1 day)

**Start with 4.1 or 4.2** — they're the cleanest "Bad super kind in isa" cases. 4.5 and 4.6 are CFWG-proper which is harder. 4.3 is downstream of 4.1. 4.4 is doubly-broken (test design bug + Family 1).

Pick 4.1. Add `println(...)` instrumentation along the rejection path:

- `OverloadResolver.findPotentialFunction` — what candidates does it find? What rejection reasons are produced?
- Where does "Bad super kind in isa" come from? Trace it back — it's likely in a `checkParamSend` or similar.
- What's the current `env` context when the call to `launch` is being resolved? Does it have access to the bounds of `launchGeneric`?

Goal of Phase 2: produce a written explanation of the *exact* sequence of calls that lead to the rejection, with rune values at each step. Write it as a "collapsed call tree" (see `investigations/call_bound_wrong_arguments.md` for the format). **Don't skip this.** A bad fix without understanding the trace will pass 4.1 but break 5 other tests.

### Phase 3 — Design the fix for 4.1 (1 day)

You're choosing one of:

- **Option A**: Modify `OverloadResolver.findPotentialFunction` (or a helper near it) to consult the env's `implements`-bounds when a candidate is rejected with "Bad super kind in isa." If the bound shows the placeholder implements the interface, accept the candidate.
- **Option B**: Add a new solver rule that pre-emptively asserts T-implements-Interface as an env fact, so the overload resolver sees T's full method set.
- **Option C**: Extend `EdgeCompiler.scala`'s impl machinery to register placeholders' bound impls as full impls, so `isParent(T, IShip)` returns `IsParent` when `where implements(T, IShip)` is in scope.

(C) is most "principled" but has wide blast radius — placeholders would now masquerade as concrete implementations. (A) is local but has scattered implementation. (B) is the cleanest in solver terms but might require new postparser plumbing.

**Discuss with the user (Evan) before committing to a design.** This is a deep architectural question and Evan will have opinions. Write a 1-page proposal: which option, why, what files change, what could break.

### Phase 4 — Implement and verify on 4.1 only (1 day)

Apply the fix. Run only 4.1:
```bash
sbt 'testOnly dev.vale.typing.AfterRegionsTests -- -z "Method call on generic data"'
```

Iterate until it passes. **Don't move on yet.** Run the full AfterRegions suite after each significant change:
```bash
sbt 'testOnly dev.vale.AfterRegionsIntegrationTests dev.vale.typing.AfterRegionsTests dev.vale.typing.AfterRegionsErrorTests dev.vale.parsing.AfterRegionsTests dev.vale.parsing.functions.AfterRegionsFunctionTests dev.vale.postparsing.AfterRegionsErrorTests' 2>&1 > /tmp/check.txt
grep -E "Tests: succeeded|FAILED \*\*\*" /tmp/check.txt
```

**Watch for regressions**. If a previously-passing test breaks, your fix is too aggressive. Narrow it.

After 4.1 passes, run a wider sanity check:
```bash
sbt 'testOnly dev.vale.typing.CompilerTests dev.vale.typing.CompilerVirtualTests dev.vale.typing.CompilerGenericsTests dev.vale.IntegrationTestsA dev.vale.IntegrationTestsB dev.vale.IntegrationTestsC' 2>&1 > /tmp/wider.txt
grep -E "Tests: succeeded|FAILED \*\*\*" /tmp/wider.txt
```

(These may have many failures already; just compare numbers to a pre-change baseline you take in Phase 0.)

### Phase 5 — Extend to 4.2 and 4.3 (½–1 day)

4.2 has the same root cause as 4.1. It should pass with your 4.1 fix. If not, the 4.1 fix was too narrow.

4.3 is the error-reporting analog. Once 4.1's machinery lets the override search see B as implementing A, the missing-override path should fire correctly. May need an additional small fix in the error reporter to emit the right error class.

### Phase 6 — Tackle CFWG for 4.5 and 4.6 (2–3 days)

This is the hard part. CFWG is a real design choice. Re-read `Generics.md:201` thoroughly. Decide between prototype-based and placeholder-based. **Discuss with Evan before committing.**

The smallest viable change:
1. Modify the `RuleScout` to emit different rules for `where func name(P1)R` clauses where the name isn't hardcoded (probably this requires no postparser change — the rules already exist).
2. Wire `CallSiteFuncSR` (or a new sibling) to perform overload-set-to-prototype coercion when the F arg is an `OverloadSetT`.
3. Rewrite `functor1.vale` to use the generalized mechanism instead of the hardcoded "drop".

After this works, 4.5 and 4.6 should pass together.

### Phase 7 — Tackle 4.4 (½ day)

The doubly-broken test. After Family 1's other fixes land:
1. Verify the test now reaches the body-result-matching phase (instead of failing earlier).
2. Rewrite the test body so the lambda actually returns something incompatible with the interface (e.g. `(_) => { "hello" }` returning `str`).
3. Verify the assertion fires with the new body.

### Phase 8 — Final verification (½ day)

Full AfterRegions suite. Full broader suite. Update `quest.md` to reflect resolutions:
- Headline `7 Remaining` → `1 Remaining` (only "Reports error" if 4.3 took longer than 4.1, otherwise `0 Remaining`).
- 33 → 39 (or 40) passing.
- Each of the 6 Family 1 tests gets a RESOLVED note matching the style of Family 4.1 / 4.2 / 4.3 in quest.md.

Commit incrementally — one commit per test fixed, with the trace + the fix + the verification. The git history is the documentation for the next person.

---

## 7. Pitfalls — things that have burned people

### 7.1 Wide blast radius

Bound-propagation changes affect every generic function in the compiler. **Run the full AfterRegions suite after every non-trivial change.** A "small fix" can break 30 tests. Don't accumulate changes; verify incrementally.

### 7.2 Don't pivot without asking

The user (Evan) has CLAUDE.md rule: "Don't pivot unilaterally." If you start on Option A and discover it won't work, **ask before switching to Option B**. Sometimes the right answer is "abandon and try X first." That's Evan's call.

### 7.3 Don't make temporary programs (DMTP)

When you want to debug, don't write a temporary `.vale` file. Add a new test case in the project. They'll be valuable.

### 7.4 Resist the lazy fix

Hard-coding the candidate-acceptance for "if rejection is X and bound is Y, accept" works for 4.1 in isolation. It won't generalize to nested cases (e.g. `implements(T, IShip<int>)` where the interface itself has type args). Aim for a fix that's principled enough to handle nesting. If you can't handle nesting yet, document that explicitly in your code comments and tie it to the IRBFPTIPT acronym.

### 7.5 The "test passes typing but fails eval" trap

`compile.expectCompilerOutputs()` runs only the typing pass. `compile.evalForKind(...)` runs the whole pipeline. Bound issues frequently fail at later phases. **For Family 1 tests, prefer evalForKind whenever possible** — typing-pass success is necessary but not sufficient. (Earlier sessions thought 4.6 "passed" because typing succeeded. It didn't really pass.)

### 7.6 Don't use `git checkout` to revert

Per Evan's CLAUDE.md: "NEVER use `git checkout` to revert a file." Use `git diff` and apply changes manually. This came up after some Cursor agent destroyed work.

### 7.7 Quest 4.4's lambda mismatch

Don't forget the test design bug in 4.4. After Family 1 lands, that test still won't pass without a body rewrite. Two-step fix.

### 7.8 The `OverloadSetT` has no kind

`OverloadSetT(env, name)` masquerades as a kind but isn't a real one — it has no struct/interface backing. When you handle it in your fix, recognize this. The "kind" you're matching against may need special-case handling for `OverloadSetT` vs `StructTT`/`InterfaceTT`.

---

## 8. Resources and where to ask for help

- **`quest.md`** — the master tracker. Family 1 section is current. Family 4.1, 4.2, 4.3, Family 2 RESOLVED notes are good examples of how to write your own RESOLVED notes when finishing tests.
- **`docs/Generics.md`** — the design bible. CFWG (line 201), NBIFP (line 489), and many other acronyms. When you see an unfamiliar acronym in code comments (LAGTNGZ, ECSIIOSZ, OWPFRD, IRBFPTIPT, AFCTD, etc.), grep `docs/` for it. Most have arcana docs.
- **`docs/arcana/`** — focused design docs, one acronym each. Especially:
  - `BoundReturnResolution-BRRZ.md` — directly relevant.
  - `LambdasAreGenericTemplatesNotGenerics-LAGTNGZ.md` — relevant for 4.4.
  - `EachCallSiteIsItsOwnSolve-ECSIIOSZ.md` — substrate concept.
- **`docs/historical/`** — past handoffs (`family2_handoff.md`, etc.) and post-mortems. Read `family2_handoff.md` for an example of how the previous handoff was structured.
- **`docs/historical/BRRZ-postmortem.md`** — what the BRRZ implementation involved. Your CFWG implementation will rhyme.
- **`investigations/`** — per-test investigations. `family3_map_function.md` is a good model for a thorough investigation doc. Several other docs there are relevant context for individual error patterns you'll hit.
- **`investigations/deferred-solving-across-def-callsite.md`** — adjacent design exploration. Don't try to solve deferred-solving as part of Family 1, but be aware of it.

When in doubt, **ask Evan**. The bound-propagation machinery is one of the highest-velocity areas of the compiler and his current mental model isn't fully written down.

---

## 9. Definition of done

Family 1 is "done" when:

1. All 6 Family 1 tests pass (or 4.4 is converted to a working test against a new body).
2. Full AfterRegions suite is at **39/1/8** or **40/0/8** (depending on 4.3's resolution timing).
3. No regressions in `dev.vale.typing.CompilerTests`, `CompilerVirtualTests`, `CompilerGenericsTests`, `IntegrationTestsA/B/C` compared to pre-change baseline.
4. `functor1.vale`'s hardcoded "drop" is replaced (or the file is deleted) per the CFWG design.
5. Each test has a RESOLVED note in `quest.md` matching the style of Family 4.x's RESOLVED notes.
6. `quest.md`'s headline counts and verification line are updated.
7. A summary commit lands documenting the architectural choice (prototype-based vs placeholder-based CFWG) — possibly accompanied by a new arcana doc in `docs/arcana/`.
8. Optional but encouraged: an investigation doc in `investigations/` capturing the journey, traps hit, and what didn't work — for future readers.

Good luck. Take it slow on Phase 1 (read everything). Phase 4 is where the real work happens. Phase 6 (CFWG) is where the design judgment matters most.

---

## 10. Acronyms cheat sheet (you'll see these in code/docs)

- **CFWG** — Concept Functions With Generics. The design you're implementing. `docs/Generics.md:201`.
- **NBIFP** — Need Bound Information From Parameters. Companion problem. `docs/Generics.md:489`.
- **BRRZ** — Bound Return Resolution. Recently-implemented mid-solve real-lookup capability for return types. `docs/arcana/BoundReturnResolution-BRRZ.md`. Direct precedent for your CFWG work.
- **LAGTNGZ** — Lambdas Are Generic Templates, Not Generics. Family 2 resolution. Affects 4.4.
- **IRBFPTIPT** — Impl Rule Bound Function Prototype Type-Internal Property Tracking (or similar). The nested-type-arg-bound gap at `EdgeCompiler.scala:421-428`.
- **ECSIIOSZ** — Each Call Site Is Its Own Solve. The per-call-site solve substrate that BRRZ runs inside.
- **DRSINI** — Default Rules Should Be Incremental Not Initial. Generic param defaults handling.
- **MSAE** — pre-BRRZ acronym for the missing-bound-driven-return capability. Now resolved.
- **ONBIFS** — older NBIFP variant.
- **OWPFRD** — Output Wares Placeholders From Real Denizens (placeholders can't cross denizen boundaries).
- **AFCTD** — Abstract Function's Concrete Templata Direction (templateArgs ordering).
- **DSDCBZ** (placeholder name) — Deferred Solving across Def/Callsite Boundary. The architectural exploration in `investigations/deferred-solving-across-def-callsite.md`. Out of Family 1's scope.

If you see an acronym not in this list: `grep -rn "ACRONYM" /Volumes/V/ValeAfterRegions/docs/` should find its definition.
