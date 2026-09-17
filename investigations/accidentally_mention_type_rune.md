# Investigation: "Accidentally mention type rune" test failure

## Bug summary

Test: `dev.vale.typing.AfterRegionsErrorTests` / "Accidentally mention type rune"

Code under test:
```vale
func moo<Z>(z &Z) {
  drop(Z);   // Z is the type rune, not z the variable
}

exported func main() void {
  moo(4);
}
```

Expected: Compiler should produce an error — `Z` is a type parameter rune, not a value, and can't be used as an argument to `drop()`.

Actual: `scala.MatchError: CoordTemplataT(...)` crash at `ExpressionCompiler.scala#evaluate` in the `RuneLookupSE` handler.

## Collapsed call tree

```
- ExpressionCompiler#evaluate(FunctionCallSE "drop(Z)"):
  - ExpressionCompiler#evaluateAndCoerceToReferenceExpressions:
    Evaluates each argument. For `Z`:
    - ExpressionCompiler#evaluate(RuneLookupSE "Z"):
      Looks up rune `Z` in environment via lookupNearestWithImpreciseName.
      Gets CoordTemplataT(CoordT(own, RegionT(), KindPlaceholderT(...Z...)))
      because Z is a type parameter rune that maps to a coord placeholder.
      - templata match { ... }:
        Only has cases for IntegerTemplataT, PlaceholderTemplataT(IntegerTemplataType),
        and PrototypeTemplataT.
        CRASH: MatchError on CoordTemplataT — no catch-all case.
```

## Root cause

`ExpressionCompiler.scala#evaluate`, in the `RuneLookupSE` handler (~line 1004), has a `templata match` that only handles three templata types that make sense as runtime values:

1. `IntegerTemplataT` — compile-time integer constants (e.g., `N` in `func repeat<N int>`)
2. `PlaceholderTemplataT(IntegerTemplataType)` — generic integer placeholders
3. `PrototypeTemplataT` — function prototypes (for passing functions around)

It has no case for `CoordTemplataT`, `KindTemplataT`, `MutabilityTemplataT`, or any other templata types that represent **types rather than values**. When a user writes `Z` (a type rune) in expression position, the rune resolves to a `CoordTemplataT` and the match crashes.

This is a user error (writing `Z` instead of `z`) that should produce a clear compiler error, not a crash.

## Fix

Three changes:

### 1. New error type (`CompilerErrorReporter.scala`)

Added `CantUseRuneValueAsExpression(range, rune)` to the `ICompileErrorT` hierarchy. This is a structured error type per NSTDX (No Stringly-Typed Data) — we match on the error type and rune identity in the test, not on string messages.

```scala
case class CantUseRuneValueAsExpression(range: List[RangeS], rune: IRuneS) extends ICompileErrorT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}
```

### 2. Catch-all in RuneLookupSE handler (`ExpressionCompiler.scala`)

Added a default case to the `templata match` in the `RuneLookupSE` handler:

```scala
case _ => {
  throw CompileErrorExceptionT(CantUseRuneValueAsExpression(range :: parentRanges, runeA))
}
```

This catches `CoordTemplataT`, `KindTemplataT`, `MutabilityTemplataT`, and any other templata type that isn't a valid runtime value.

### 3. Error humanization (`CompilerErrorHumanizer.scala`)

Added a humanization case that produces a helpful message:

```scala
case CantUseRuneValueAsExpression(range, rune) => {
  "Can't use rune `" + humanizeRune(rune) + "` as a value expression. " +
  "Did you mean a local variable with a similar name?"
}
```

The "Did you mean..." hint addresses the most common cause — confusing `Z` (type rune) with `z` (variable).

### 4. Test update (`AfterRegionsErrorTests.scala`)

Updated the test to match on the structured error type:

```scala
compile.getCompilerOutputs() match {
  case Err(CantUseRuneValueAsExpression(_, _)) =>
  case Err(e) => vfail(e)
  case Ok(_) => vfail()
}
```

This replaces the previous `case Err(e) => vimpl(e)` which was a placeholder that never ran (the MatchError crashed first).

## Why the test previously had `vimpl(e)`

The test was written with the expectation that compilation would fail with *some* error, but the specific error type hadn't been decided yet (`vimpl` = "implement later"). However, the test never actually reached the `vimpl()` — the MatchError crash in the expression compiler happened first, propagating as an uncaught exception that ScalaTest reported as a test failure. So the test was "failing for the right reason" (compilation of `drop(Z)` does fail) but "in the wrong way" (crash instead of structured error).

## Design note: why not `RangedInternalErrorT` with a string?

Per shield NSTDX (No Stringly-Typed Data), we use a dedicated error type rather than `RangedInternalErrorT(range, "Can't use rune Z as expression")`. Benefits:

- Tests match on `CantUseRuneValueAsExpression(_, _)` — no brittle `.contains("rune")` checks
- The error carries the `IRuneS` identity so tests can verify *which* rune was misused
- The humanizer owns the English text, keeping it separate from the error semantics
- Future tools (IDE quick-fixes, error catalogs) can pattern-match on the type

## Score after fix

1048 passed, 38 failed (was 1047/39).
