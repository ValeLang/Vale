# Category F: Compiler Logic Gaps — Investigation

5 remaining tests. Each investigated below with root cause and fix assessment.

---

## 1. Reports when two functions with same signature

**Test:** `AfterRegionsErrorTests.scala:130`
**Status:** Returns `Ok` — both functions compile with different template names.
**Expected:** `Err(FunctionAlreadyExists(...))`

### Root cause

The two `exported func moo() int` get different `FunctionTemplateNameT` values because the template name includes the source location (`tvl:1` vs `tvl:42`). The `declareFunction` check in `CompilerOutputs.scala:311` uses the full `IdT` (which includes the template name location), so it sees them as different functions. The collision is only at the exported-signature level — same name, same params, same return type — but the check doesn't operate at that level.

### Fix assessment

**Medium.** Need either:
- A separate duplicate-signature check at the export/edge compilation phase, comparing `FunctionNameT` ignoring template source locations.
- Or a check when adding to `functionDeclaredNames` that compares by "human name + param types + return type" rather than full `IdT`.

This is a detection gap, not a missing error type — `FunctionAlreadyExists` exists and has an active throw site, but the comparison granularity is wrong for this case.

---

## 2. Lambda is incompatible anonymous interface

**Test:** `AfterRegionsErrorTests.scala:190`
**Status:** Returns `Ok` — compilation succeeds.
**Expected:** `Err(BodyResultDoesntMatch(...))`

### Root cause

The test code has `AFunction1<int>` with `func __call(virtual this &AFunction1<P>, a P) int` and the lambda `(_) => { 4 }`. The lambda returns `int` (4), matching the interface method's return type `int`. **The test is wrong** — the lambda IS compatible. For `BodyResultDoesntMatch` to fire, the lambda would need to return a different type (e.g., `void` or `str`).

However, the test's comment says "Depends on Generic interface anonymous subclass" — this is a category H dependency. The real issue is probably that anonymous interface instantiation of generic interfaces isn't fully working. When it does work, some other type mismatch may surface.

### Fix assessment

**Blocked.** This test depends on generic anonymous interface subclassing (category H). The lambda body actually matches the interface method signature. Either the test needs different types to trigger a mismatch, or the underlying generic anonymous subclass feature needs to work first.

---

## 3. Abstract func without virtual

**Test:** `AfterRegionsErrorTests.scala:244`
**Status:** Assertion failure in `AbstractBodyMacro.scala:30`.
**Expected:** `Err(e)` then `vimpl(e)` — expects some error.

### Root cause

`abstract func launch<X, Y, Z>(self &ISpaceship<X, Y, Z>, bork X)` has no `virtual` on `self`. `AbstractBodyMacro.generateFunctionBody` asserts `params2.exists(_.virtuality == Some(AbstractT()))` — this fires because no param has `AbstractT`.

`VirtualAndAbstractGoTogether` error is defined in `PostParser.scala:51` as `ICompileErrorS` but is never thrown. The check belongs in `FunctionScout` (postparsing) — verify that top-level `abstract` functions have at least one `virtual`/`AbstractP` parameter.

### Fix assessment

**Easy.** Add a check in `FunctionScout` (postparsing): if a function has `AbstractAttributeP` but no parameter has `AbstractP` virtuality, throw `VirtualAndAbstractGoTogether`. Then update the test to match on this error. Also add humanization in `PostParserErrorHumanizer`.

Note: the test also has unrelated code (`a = #[](10, {_}); return a.3;`) in the body that would compile independently, but since the function is `abstract`, the body macro fires before any body compilation. The test will need updating once the error type is decided — currently it does `vimpl(e)` on the error.

---

## 4. Inherit reachable bounds (IRBFPTIPT)

**Test:** `AfterRegionsErrorTests.scala:306`
**Status:** "Expected non-empty!" assertion — `vassertSome` failure.
**Expected:** `Err(e)` then `vimpl(e)`.

### Root cause

`BoxA<BoxB<T>>` should inherit `drop(T)` from `BoxB<T>`, but the bound inheritance for nested generic params isn't implemented. This is a core generics feature gap — the compiler doesn't transitively discover bounds from inner type parameters.

### Fix assessment

**High effort.** This is a fundamental generics feature — reachable bound inheritance for nested type parameters. Likely requires changes in `FunctionCompilerSolvingLayer.scala` or wherever reachable bounds are computed. Not a quick fix.

---

## 5. Report when downcasting between unrelated types

**Test:** `AfterRegionsErrorTests.scala:167`
**Status:** Compilation fails with "Couldn't find suitable function `as(&ISpaceship)`" when test expects it to succeed (then `vimpl()`).
**Expected:** Compilation succeeds, then `vimpl()`.

### Root cause

The `as` function (downcast macro) can't be found for `ISpaceship`. This is an `AsSubtypeMacro.scala` issue — the downcast macro may not be properly generating `as` for this interface/struct combination. The test depends on the `as` builtin working for unrelated types, which requires the downcast infrastructure.

### Fix assessment

**Medium-high.** Need to investigate `AsSubtypeMacro` to understand why `as` isn't being generated. This is a B-like test (compilation fails + vimpl at end), making it lower priority — even if the compile succeeds, the test just hits `vimpl()`.

---

## Summary by tractability

| Test | Effort | Quick win? |
|---|---|---|
| Abstract func without virtual | Easy | **Yes** — add check in FunctionScout |
| Reports when two functions with same signature | Medium | Maybe — need to find right comparison level |
| Report when downcasting between unrelated types | Medium-high | No — compile fails + vimpl |
| Lambda is incompatible anonymous interface | Blocked | No — depends on category H |
| Inherit reachable bounds (IRBFPTIPT) | High | No — core generics feature |
