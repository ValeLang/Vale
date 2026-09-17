# Investigation: "Ambiguous call" test failure

## Bug summary

Test: `dev.vale.typing.AfterRegionsErrorTests` / "Ambiguous call"

Code under test:
```vale
func add<X>(i int, x &X) { }
func add<X>(x &X, i int) { }

exported func main() void {
  add(3, 4);
}
```

Expected: Compiler should reject `add(3, 4)` because both overloads match equally well — `add<int>(int, &int)` works for both definitions.

Actual: `vimpl()` crash at `OverloadResolver.scala:622` inside `narrowDownCallableOverloads`. The compiler correctly identifies two matching candidates but hits a placeholder where it should throw an ambiguity error.

## Collapsed call tree

```
- Compilation of main() body:
  - ExpressionCompiler#evaluate(FunctionCallSE "add(3, 4)"):
    - OverloadResolver#findFunction("add", args=[int, int]):
      - findPotentialFunction:
        Resolves both add<X> templates with X=int. Gets two banners:
          1. add<int>(int, &int) from line 1
          2. add<int>(&int, int) from line 2
        Both match (int can be sent to &int via borrow).
        - narrowDownCallableOverloads:
          Scores each parameter for conversion requirements.
          Both candidates score identically (each needs one conversion).
          normalIndicesAndCandidates.size == 2
          CRASH: vimpl(duplicateBanners) at line 622
```

## Root cause

`narrowDownCallableOverloads` in `OverloadResolver.scala` already had the correct error-throwing structure:

```scala
if (normalIndicesAndCandidates.size > 1) {
  val duplicateBanners = normalIndicesAndCandidates.map(_._2)
  throw CompileErrorExceptionT(
    CouldntNarrowDownCandidates(
      callRange,
      vimpl(duplicateBanners)))  // <-- placeholder here
}
```

The `CouldntNarrowDownCandidates` error type existed and was wired into the humanizer. The `vimpl()` was wrapping the second argument because `CouldntNarrowDownCandidates` expected `Vector[RangeS]` (source ranges of the candidate functions), but the `duplicateBanners` were `List[PrototypeT[IFunctionNameT]]` (prototype objects). The old code had a commented-out line `duplicateBanners.map(_.range.getOrElse(...))` — but `PrototypeT` no longer has a `range` field (it was removed per MFBFDP, "Merge Function Bounds From Different Places").

## Resolution

Three changes:

### 1. Changed `CouldntNarrowDownCandidates` to hold prototypes (`CompilerErrorReporter.scala`)

```scala
// Before:
case class CouldntNarrowDownCandidates(range: List[RangeS], candidates: Vector[RangeS])

// After:
case class CouldntNarrowDownCandidates(range: List[RangeS], candidates: Vector[PrototypeT[IFunctionNameT]])
```

Prototypes carry more information than bare ranges (function name, template args, parameter types), and `PrototypeT` doesn't have a `range` field anymore, so storing prototypes directly is the natural choice.

### 2. Replaced `vimpl()` with direct prototype passing (`OverloadResolver.scala`)

```scala
// Before:
vimpl(duplicateBanners)

// After:
duplicateBanners.toVector
```

### 3. Updated humanizer (`CompilerErrorHumanizer.scala`)

```scala
// Before (using ranges):
candidateRanges.map(range => "\n" + codeMap(range.begin) + ":\n  " + lineContaining(range.begin)).mkString("")

// After (using prototypes):
candidates.map(proto => "\n  " + humanizeId(codeMap, proto.id)).mkString("")
```

### 4. Updated test (`AfterRegionsErrorTests.scala`)

```scala
compile.getCompilerOutputs() match {
  case Err(CouldntNarrowDownCandidates(_, candidates)) => {
    vassert(candidates.size == 2)
  }
  case Err(e) => vfail(e)
  case Ok(_) => vfail()
}
```

## Why the vimpl() was there

The error type was designed when prototypes had `range` fields. After the MFBFDP refactoring removed ranges from prototypes (to allow merging bound functions from different sources), the `vimpl()` was left as a "figure out later" placeholder for how to extract location info from the new prototype shape. The fix is to just store the prototypes directly — they contain enough info for both humanization and test assertions.

## Score after fix

1050 passed, 36 failed (was 1049/37).
