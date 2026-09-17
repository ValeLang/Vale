# Family 1.4 handoff — "Lambda is incompatible anonymous interface"

> **Status:** Active. 1 failing AfterRegions test. Mid-sized project (1–3 days). The bug isn't deep — it's mostly a **test-design problem with one underlying machinery dependency**. Read top to bottom once before touching code; the test is doubly-broken in a way that's easy to misdiagnose.

This is a handoff for a junior engineer. It's long on purpose. Don't skim. The Vale compiler's anonymous-interface machinery is dense and the mistakes that bite here are usually "I fixed half the bug and didn't notice the other half."

Sections: orientation → vocabulary → the test → why it fails (currently AND why it would still fail after the obvious fix) → existing machinery → suggested attack plan → pitfalls → resources → acronyms.

---

## 1. The one-paragraph orientation

You're working on AfterRegions test **1.4 "Lambda is incompatible anonymous interface"** in `Frontend/TypingPass/test/dev/vale/typing/AfterRegionsErrorTests.scala:197`. The test asserts `Err(BodyResultDoesntMatch(...))` — the user wrote a lambda whose body type is incompatible with an interface method's expected return type, and the compiler should report it. The test currently fails because (a) the lambda body the test source uses isn't actually incompatible (it returns `int`, matching the expected `int`), so the compiler succeeds with `Ok` instead of producing an error, and (b) even if it WERE incompatible, the compile path goes through Vale's "anonymous substruct" machinery (the synthesized class made when you instantiate a generic interface with a lambda), which has rough edges. Your job: make the test source actually incompatible, find out where the compile path leads, and either fix or document any blockers en route to a real `BodyResultDoesntMatch` error.

> **Status note (2026-04-30):** Quest.md previously said the test "blocks on generic-interface-anonymous-subclass machinery." That was true at one point. Today, when you run the test, you'll see `vwat(wat)` from the `case Ok(wat) =>` arm — meaning compile **succeeds**. Quest.md hasn't been updated. Trust the test runner over quest.md when in doubt.

---

## 2. Prerequisites — what you need to know before starting

Skim what you already know.

### 2.1 The compilation pipeline

```
Source code
  → parser          (AST nodes ending in P)
  → lexer           (AST ending in L)
  → postparser      ("scout"; AST ending in S — InterfaceS, FunctionS, IExpressionSE)
  → higher-typing   (AST ending in A — InterfaceA, FunctionA)
  → typing pass     (AST ending in T — InterfaceDefinitionT, CoordT, FunctionDefinitionT)
  → instantiator    (monomorphized — FunctionI)
  → simplifier      ("hammer")
  → backend         (LLVM)
```

You'll mostly live in the **typing pass** (`Frontend/TypingPass/src/dev/vale/typing/`), specifically the macro layer that synthesizes anonymous substructs and the function-body compiler that runs the body-vs-return-type check. You probably won't change anything before higher-typing.

### 2.2 What's an "anonymous substruct"?

When the user writes `IFunction1<int, int>({_ * 2})` (calling the IFunction1 interface's "constructor" with a lambda), the compiler doesn't have a real concrete struct that implements `IFunction1`. So at compile time, it synthesizes one. It's called the **anonymous substruct** of the interface. It has the form:

```
struct IFunction1.anonymous { /* captures */ }
impl IFunction1 for IFunction1.anonymous;
func __call(self &IFunction1.anonymous, a int) int { /* lambda body */ }
```

This synthesis is driven by `Frontend/TypingPass/src/dev/vale/typing/macros/AnonymousInterfaceMacro.scala` (553 lines, dense). The macro fires when an interface is defined; it adds an entry to the interface's environment that says "if someone calls this interface's name with a lambda arg, here's how to build the substruct."

Two related interface forms behave identically at this level:
- **`IFunction1<M Mut, P Ref, R Ref>`** — the existing builtin in `Frontend/Tests/test/main/resources/ifunction/ifunction1/ifunction1.vale`. Used in `AfterRegionsTests.scala "Basic IFunction1 anonymous subclass"`, which **passes** (so the basic machinery works for this one).
- **`AFunction1<P Ref>`** — the user-defined version in 1.4's source. Its anonymous substruct is `AFunction1.anonymous`. Should behave the same. If 1.4 is failing for a reason that doesn't apply to `Basic IFunction1`, that's a clue.

There's also a related-but-different test: `AfterRegionsTests.scala:91 "Generic interface anonymous subclass"` (currently `ignore`d), which uses a different interface name (`Bork<T>`) and exercises full instantiation through `f.bork()`. Comparing 1.4's source to that one is informative — the shapes are similar.

### 2.3 What's `BodyResultDoesntMatch`?

The error type for "the function body's actual return type doesn't match the declared/expected return type." Defined at `Frontend/TypingPass/src/dev/vale/typing/CompilerErrorReporter.scala:69`:

```scala
case class BodyResultDoesntMatch(
  range: List[RangeS],
  functionName: IFunctionDeclarationNameS,
  expectedReturnType: CoordT,
  resultType: CoordT
) extends ICompileErrorT
```

Two active throw sites in `Frontend/TypingPass/src/dev/vale/typing/function/FunctionBodyCompiler.scala`:
- **Line 94** — generic functions, after solving the body's type.
- **Line 130** — non-generic functions.

Both fire when `expectedType != actualType` after type-checking the function body.

The error type works fine. What's missing is **getting the compiler to that check** with mismatched types.

### 2.4 The `(_) => { 4 }` lambda syntax

`(_)` declares a single anonymous parameter — the parameter type is inferred from context. `{ 4 }` is the body, an int literal. So this lambda has signature `(int) -> int` when matched against `__call(virtual &AFunction1<int>, int) int`.

**Crucial:** The lambda body `{ 4 }` returns `int`, and the interface's `__call` expects `int`. These match. There is no incompatibility in this source. To get `BodyResultDoesntMatch`, the lambda body needs to return a different type than what the interface declares.

---

## 3. The failing test

**File:** `Frontend/TypingPass/test/dev/vale/typing/AfterRegionsErrorTests.scala:197`

**Source as written:**
```vale
interface AFunction1<P Ref> {
  func __call(virtual this &AFunction1<P>, a P) int;
}
exported func main() {
  arr = AFunction1<int>((_) => { 4 });
}
```

**Test handler:**
```scala
compile.getCompilerOutputs() match {
  case Err(BodyResultDoesntMatch(_, _, _, _)) =>           // PASS
  case Err(other) => vwat(humanized(other))                 // FAIL — wrong error
  case Ok(wat) => vwat(wat)                                 // FAIL — no error
}
```

**Current behavior:** Falls into the `Ok(wat)` case. Compile succeeds. No error fires. `vwat` blows up.

You can verify with:
```bash
cd /Volumes/V/ValeAfterRegions/Frontend
sbt 'testOnly dev.vale.typing.AfterRegionsErrorTests -- -t "Lambda is incompatible anonymous interface"' 2>&1 | head -20
```

You'll see `wat: HinputsT(...)` followed by a giant data dump (the whole compiled program) — that's the `vwat(wat)` from the `Ok` case.

---

## 4. Why this is doubly-broken

There are two independent reasons this test doesn't pass. **Don't fix one and assume you're done.**

### Reason A — The test source has no incompatibility

The lambda body `{ 4 }` returns `int`. The interface method's return type is `int`. These match. So even with perfect compiler machinery, the test's source code wouldn't trigger `BodyResultDoesntMatch`. To exercise the error, you have to change the test source so the lambda body returns something other than `int`. Example:

```vale
arr = AFunction1<int>((_) => { "hello" });   // body returns str, expected int
```

Or:

```vale
arr = AFunction1<int>((_) => { true });      // body returns bool, expected int
```

This part is mostly a typing edit; should be quick. Don't be tempted to skip it because the test "works" today returning `int` — it doesn't actually exercise the assertion either way.

### Reason B — The synthesized substruct path may or may not reach the body-check

Once the source actually has a mismatch, the compiler has to:
1. Recognize `AFunction1<int>(lambda)` as instantiating an anonymous substruct of `AFunction1`.
2. Synthesize the substruct, including a synthesized `__call(self &..., a int) <resulttype>` whose body is the lambda body.
3. Type-check that synthesized `__call`. Discover the body returns `str` (or `bool`, etc.), but `__call`'s expected return type is `int`.
4. Throw `BodyResultDoesntMatch` with the right ranges and types.

Step 1 works (`Basic IFunction1 anonymous subclass` passes — same general path). Step 2's machinery is in `AnonymousInterfaceMacro.scala`. Step 3-4 happen in `FunctionBodyCompiler.scala`. **Whether step 3 actually fires the comparison correctly for synthesized `__call`s is the open question.**

What you might find:
- **Best case:** step 3 fires correctly, body type comes back as `str`, expected is `int`, `BodyResultDoesntMatch` throws. The mismatched test source alone makes the test pass. Done.
- **Middle case:** step 3 fires but the body's expected type is wrong (e.g. inferred as `str` instead of `int`), so the comparison doesn't trigger. You'll need to find where the expected type is set for the synthesized body and fix it.
- **Worst case:** step 3 doesn't fire at all because the synthesized body never gets through `FunctionBodyCompiler`. Then you'll need to walk back through `AnonymousInterfaceMacro` and figure out where the body is supposed to be type-checked. This is the one that could take 3 days.

### Why the test source's `int`-returning lambda doesn't surface anything

When the lambda body is `int` and the expected is `int`, there's nothing to fail on. The synthesized `__call` looks like `__call(self &..., a int) int { 4 }`, which type-checks cleanly. So we can't tell from the current source whether step 3 (the actual body comparison) is even firing — it would be a no-op even if it were broken.

This is why I emphasized "doubly-broken": the source masks any potential machinery bug because there's nothing to compare.

---

## 5. Existing machinery you'll touch

### 5.1 The lambda → anonymous-interface path

When the user writes `AFunction1<int>(lambda)`, the parser sees a constructor-style call on the interface. Then:

1. **Higher-typing** turns the user's interface into an `InterfaceA` with `genericParameters` and abstract methods.
2. **Typing pass** registers the interface and runs all `IOnInterfaceDefinedMacro` macros — including `AnonymousInterfaceMacro` — which add entries to the interface's environment for synthesizing the anonymous substruct.
3. When the user later writes `AFunction1<int>(lambda)`, overload resolution finds the synthesized "constructor" and stamps the substruct.

`Frontend/TypingPass/src/dev/vale/typing/macros/AnonymousInterfaceMacro.scala` is the file. Open it and skim `getInterfaceSiblingEntries` — it's the entry point.

### 5.2 The body type-check

`Frontend/TypingPass/src/dev/vale/typing/function/FunctionBodyCompiler.scala`.

Two `BodyResultDoesntMatch` throws (lines 94 and 130). The pre-condition is "we already know the function's expected return type" and "we just compiled the body and got an actual type." Compare; if mismatched, throw.

For the anonymous substruct's synthesized `__call`, the expected return type comes from the interface's abstract `__call` method (inherited via `inheritedMethodRune` and friends in `AnonymousInterfaceMacro`). The actual return type comes from compiling the lambda body. Both have to match — including the substituted generic params (e.g. `P` substituted with `int` for `AFunction1<int>`).

### 5.3 The basic working case to mimic

`Frontend/TypingPass/test/dev/vale/typing/AfterRegionsTests.scala:503` "Basic IFunction1 anonymous subclass":

```vale
import ifunction.ifunction1.*;
exported func main() int {
  f = IFunction1<mut, int, int>({_});
  return (f)(7);
}
```

Passes. Compares to 1.4's source: same shape, different interface, same lambda style. If 1.4 was failing on machinery alone (not test-design), this would too — and it doesn't. So the machinery basically works; 1.4's gap is mostly Reason A.

The IFunction1 source is in `Frontend/Tests/test/main/resources/ifunction/ifunction1/ifunction1.vale` — read it for orientation. Notice the mutability parameter `M Mut`; Vale's builtin lambda interface is generic in mutability while user-defined `AFunction1` isn't. Probably orthogonal to your bug, but worth knowing.

### 5.4 The aspirational "Generic interface anonymous subclass" test

`AfterRegionsTests.scala:91` (currently `ignore`d). Uses `Bork<T>` and exercises the same anonymous-substruct path *plus* calling a method on the resulting value. If you find issues with the anonymous-substruct machinery on the way to fixing 1.4, fixes might unblock this test too — keep an eye on it.

---

## 6. Suggested attack plan

### Phase 0 — Verify baseline (15 min)

```bash
cd /Volumes/V/ValeAfterRegions/Frontend
sbt 'testOnly dev.vale.AfterRegionsIntegrationTests dev.vale.typing.AfterRegionsTests dev.vale.typing.AfterRegionsErrorTests dev.vale.parsing.AfterRegionsTests dev.vale.parsing.functions.AfterRegionsFunctionTests dev.vale.postparsing.AfterRegionsErrorTests' 2>&1 | tee /Volumes/V/ValeAfterRegions/tmp/baseline.txt
grep -E "Tests: succeeded|FAILED \*\*\*" /Volumes/V/ValeAfterRegions/tmp/baseline.txt
```

Expected: `Tests: succeeded 36, failed 2, canceled 0, ignored 13, pending 0`. Failing tests are `Lambda is incompatible anonymous interface` and `Map function`. If the count differs, something already changed before you started.

### Phase 1 — Read everything (½ day)

1. `quest.md` Family 1 section, especially the 1.4 entry. Note that quest.md hasn't been updated since the test moved from "fails on machinery" to "succeeds without error" — read it knowing the current state is `Ok(wat)`, not a machinery failure.
2. `investigations/family1_handoff.md` — original Family 1 handoff. Lots of shared context with this doc.
3. `Frontend/TypingPass/test/dev/vale/typing/AfterRegionsErrorTests.scala:195-223` — the test itself.
4. `Frontend/TypingPass/test/dev/vale/typing/AfterRegionsTests.scala:91-104, 503-514` — the related tests (one ignored, one passing).
5. `Frontend/TypingPass/src/dev/vale/typing/CompilerErrorReporter.scala:69` — the error type.
6. `Frontend/TypingPass/src/dev/vale/typing/function/FunctionBodyCompiler.scala` — both throw sites and surrounding context. Focus on what the expected/actual types look like at the point of comparison.
7. `Frontend/TypingPass/src/dev/vale/typing/macros/AnonymousInterfaceMacro.scala` — skim. Don't try to understand all 553 lines on first pass; focus on `getInterfaceSiblingEntries`, the synthesized struct, and how the `__call` body is compiled.

Take notes on:
- Where in the synthesized `__call`'s compilation does the expected return type come from?
- Where does the body actually get type-checked?
- For the "Basic IFunction1" test that passes, does the body comparison fire and just succeed (because both are int)?

### Phase 2 — Make the test source actually incompatible (1 hour)

Edit the test source to use a clearly-mismatched lambda body:

```vale
arr = AFunction1<int>((_) => { "hello" });
```

Re-run the test:
```bash
sbt 'testOnly dev.vale.typing.AfterRegionsErrorTests -- -t "Lambda is incompatible anonymous interface"' 2>&1 | tee /Volumes/V/ValeAfterRegions/tmp/run.txt
grep -E "FAILED|wat|Couldn't" /Volumes/V/ValeAfterRegions/tmp/run.txt | head -20
```

There are three possible outcomes — write down which one you see:

- **Outcome A (best):** test passes. `BodyResultDoesntMatch(...)` was thrown. **Done.**
- **Outcome B (medium):** test still fails, but with a different error. The error trace tells you where the body-vs-return-type comparison didn't fire (or fired wrong). Move to Phase 3.
- **Outcome C (worst):** test still gets `Ok(wat)`. The synthesized body wasn't type-checked at all. The substituted-generic-mismatch is being dropped silently somewhere. Move to Phase 4.

### Phase 3 — If outcome B (½ to 1 day)

Read the error you got. Common shapes:
- "Couldn't find a suitable function" → overload resolution can't match the lambda. Check that the lambda's parameter type is being inferred correctly (P=int).
- "Solver conflict" → the solver disagrees on a rune's value. Look at the rune dump in the error message.
- An assertion / `vwat` → a code path was hit that wasn't expected. Look at the stack trace and figure out which vassert fired.

Add `println` at `FunctionBodyCompiler.scala:94` and `:130`:
```scala
println(s"DEBUG_1_4 BodyResultDoesntMatch site: function=${function1.name} expected=$expectedType actual=$actualType")
```

Re-run. If the prints don't fire, the body-check isn't reaching those throw sites — go upstream.

### Phase 4 — If outcome C (1–2 days)

The synthesized `__call` body is being compiled but its return type is being inferred (or accepted) without comparison to the expected return type from the interface. This is a real machinery bug.

Likely sites:
- `AnonymousInterfaceMacro.scala` around the synthesized `__call`'s expected return type — see if it's getting set to "whatever the body returns" rather than "the interface's declared return type."
- `FunctionBodyCompiler.scala` — the comparison logic. Maybe it's only running when the function has an explicit return-type annotation, and the synthesized `__call` doesn't have one.

Pattern: instrument the entry to body compilation, log the expected return type passed in. If it equals the body's actual type, you've found where the wrong expected type is set.

### Phase 5 — Verify and clean up (½ day)

1. Run the target test alone.
2. Run the full AfterRegions sweep — counts should move from `36/2/13` to `37/1/13`.
3. Run the wider regression sweep:
   ```bash
   sbt 'testOnly dev.vale.typing.CompilerTests dev.vale.typing.CompilerVirtualTests dev.vale.typing.CompilerGenericsTests dev.vale.IntegrationTestsA dev.vale.IntegrationTestsB dev.vale.IntegrationTestsC' 2>&1 | tee /Volumes/V/ValeAfterRegions/tmp/wider.txt
   grep -E "Tests: succeeded|FAILED \*\*\*" /Volumes/V/ValeAfterRegions/tmp/wider.txt
   ```
4. Update `quest.md`:
   - Top status line: `36 / 2 / 13` → `37 / 1 / 13`.
   - Verify line.
   - 1.4 section: move to "Resolved" with notes on what was needed (test-source mismatch alone, or test-source + a real fix).
5. If you uncovered architectural bugs that didn't end up needing a fix for 1.4 specifically, document them in `investigations/` for a future agent.

---

## 7. Pitfalls

### 7.1 Don't trust quest.md's failure description

quest.md still says 1.4 "blocks on generic-interface-anonymous-subclass machinery" — that was the failure mode at one point, but it's no longer accurate. Today the test ends in `Ok(wat)`. Read the test runner output as ground truth.

### 7.2 The doubly-broken trap

Easy mistake: edit `AnonymousInterfaceMacro.scala` to "fix" something, see the test still fail, conclude the bug is deep. The test will *always* fail with `Ok(wat)` until you change the test source, regardless of any compiler fix. Phase 2 is non-optional.

### 7.3 Wide blast radius

`AnonymousInterfaceMacro` and `FunctionBodyCompiler` are touched by virtually every program with a lambda or interface method. Run the AfterRegions and wider sweeps after every non-trivial change.

### 7.4 Don't pivot without asking

Per Evan's `~/.claude/CLAUDE.md`: "Don't pivot unilaterally." If your initial direction (Phase 2 alone, or Phase 3 fix) hits a wall, stop and ask before starting a different direction.

### 7.5 DMTP

When debugging, don't write a throwaway `.vale` file. Add a new test case in `AfterRegionsErrorTests.scala` or similar. Throwaways aren't valuable; tests are.

### 7.6 Don't use `git checkout` to revert

Per Evan's `CLAUDE.md`: never use `git checkout` to revert a file. Use `git diff` and apply changes manually.

### 7.7 The "test passes typing but I want it to error" mindset

`expectCompilerOutputs()` throws if compilation fails. `getCompilerOutputs()` returns `Either[Error, Outputs]` and lets you pattern match. This test uses the latter — it expects compilation to *fail* in a specific way. Don't change it to `expectCompilerOutputs()`; you'll lose the assertion.

### 7.8 Anonymous substruct vs anonymous parameter lambdas (Family 2 distinction)

Family 2's "anonymous-param lambdas" — already resolved — was about lambdas with `_` placeholders in *parameter* position (`(_) => ...` where `_` is the underscore-named param). Family 1.4 is about anonymous *substructs* — synthesized classes that wrap lambdas to make them implement an interface. Different concepts, similar names. Don't confuse them.

---

## 8. Resources

- **`quest.md`** — master tracker. Family 1.4 section (with stale state — see 7.1).
- **`investigations/family1_handoff.md`** — original Family 1 handoff. Family 1.1 / 1.2 are resolved; the structural sections are still informative.
- **`investigations/cfwg_handoff.md`** — Family 1.5/1.6 handoff (now deprioritized but a good model for handoff-writing).
- **`docs/Generics.md`** — design bible. Look for sections on lambdas, generic interfaces, anonymous substructs.
- **`docs/arcana/LambdasAreGenericTemplatesNotGenerics-LAGTNGZ.md`** — directly relevant: lambdas are templates, not generics. Has implications for how the synthesized `__call`'s body gets compiled.
- **`Frontend/Tests/test/main/resources/ifunction/ifunction1/ifunction1.vale`** — the builtin reference implementation of an `IFunction1`-style interface.
- **`Frontend/TypingPass/src/dev/vale/typing/macros/AnonymousInterfaceMacro.scala`** — the synthesis machinery.
- **`Frontend/TypingPass/src/dev/vale/typing/function/FunctionBodyCompiler.scala`** — the body type-check.

When in doubt, **ask Evan**. The anonymous-substruct machinery is a subtle area with a lot of conventions that aren't fully documented.

---

## 9. Definition of done

1.4 is "done" when:

1. The test source uses a clearly-mismatched lambda body (e.g. `(_) => { "hello" }` against `int` return).
2. The test asserts `Err(BodyResultDoesntMatch(_, _, _, _))` and that pattern matches.
3. Full AfterRegions suite is at **37 / 1 / 13** (one more pass, one fewer fail).
4. No regressions in `dev.vale.typing.CompilerTests`, `CompilerVirtualTests`, `CompilerGenericsTests`, `IntegrationTestsA/B/C`.
5. `quest.md` updated: counts, verify line, 1.4 marked Resolved with notes.
6. Any new architectural concepts uncovered are written to `investigations/` or `docs/arcana/`.
7. The `// This test does not pass yet, use #[ignore].` comment is removed from the test (it does pass now).

---

## 10. Acronyms cheat sheet

- **AnonymousInterfaceMacro** — the typing-pass macro that synthesizes anonymous substructs for interfaces.
- **`__call`** — the conventional name for an interface method that gets invoked when the interface value is called like a function (`f(x)` desugars to `f.__call(x)`).
- **`BodyResultDoesntMatch`** — the error type for "function body's actual type ≠ declared expected type."
- **`vwat`** / **`vfail`** / **`vimpl`** — Vale test/assert helpers. `vwat` = "this case should never fire"; `vfail` = "abort with message"; `vimpl` = "not yet implemented."
- **LAGTNGZ** — Lambdas Are Generic Templates Not Generics. Relevant arcana for how the synthesized `__call`'s body gets compiled.
- **DMTP** — Don't Make Temporary Programs (skill rule).

If you see an acronym not in this list: `grep -rn "ACRONYM" /Volumes/V/ValeAfterRegions/docs/` should find its definition.

Good luck. Phase 0 → Phase 1 (the read) → Phase 2 (the source edit) is probably 70% of this — don't underinvest in reading. If Phase 2 alone makes the test pass, you're done before lunch.
