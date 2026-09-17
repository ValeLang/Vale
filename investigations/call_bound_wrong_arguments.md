# Investigation: "Call bound with wrong arguments" test failure

## Bug summary

Test: `dev.vale.typing.AfterRegionsErrorTests` / "Call bound with wrong arguments"

Code under test:
```vale
func add<X>(i int, x &X) where func str(&X)str {
  str(true);
}
```

Expected: Compiler should reject `str(true)` — the bound `func str(&X)str` takes `&X`, not `bool`.

Actual: Compiler correctly rejects it with `CouldntFindFunctionToCallT` containing `SpecificParamDoesntSend`. The test just had a `vimpl(e)` placeholder that never got filled in.

## Collapsed call tree

```
- Compilation of add<X>(i int, x &X) definition:
  - ExpressionCompiler#evaluate(FunctionCallSE "str(true)"):
    - OverloadResolver#findPotentialFunction("str", args=[CoordT(share, RegionT(), BoolT())]):
      Finds one candidate: PrototypeTemplataCalleeCandidate (the bound prototype).
      Bound prototype is: str(&KindPlaceholderT(X)) -> str
      Tries to match bool against &X.
      - checkParamSend(0, CoordT(share, BoolT()), CoordT(borrow, KindPlaceholderT(X))):
        Bool (share) can't be sent to borrow-of-placeholder. 
        REJECTS with SpecificParamDoesntSend(0, ...).
      FAILS with CouldntFindFunctionToCallT containing the rejection.
```

## Root cause

There was no compiler bug. The compiler correctly:
1. Resolves the `str` call to the bound prototype `func str(&X)str`
2. Checks whether `bool` (share) can be sent to `&X` (borrow of placeholder)
3. Rejects with `SpecificParamDoesntSend` — the right error for a param type mismatch

The test had `case Err(e) => vimpl(e)` as a placeholder from when it was first written. The `vimpl()` call throws `VAssertionFailException`, causing the test to fail even though the compiler produced the correct error.

## Resolution

Test-only fix. Updated the match pattern from `vimpl(e)` to structured matching:

```scala
compile.getCompilerOutputs() match {
  case Err(CouldntFindFunctionToCallT(_, fff)) => {
    vassert(fff.rejectedCalleeToReason.size >= 1)
    fff.rejectedCalleeToReason.head._2 match {
      case SpecificParamDoesntSend(0, CoordT(ShareT, _, BoolT()), _) =>
      case other => vfail(other)
    }
  }
  case Err(e) => vfail(e)
  case Ok(_) => vfail()
}
```

Added `SpecificParamDoesntSend` to the import line.

## Why SpecificParamDoesntSend and not InferFailure

The rejection happens **before** the solver runs. `SpecificParamDoesntSend` is a pre-solver check in `OverloadResolver` that tests whether an argument's coord can be sent to a parameter's coord. Since `bool` (share ownership) can never be sent to `&X` (borrow ownership of a placeholder), the check fails immediately without needing to invoke the solver.

This is different from tests like "Detects sending non-citizen to citizen" where the rejection goes through the solver and comes back as `FindFunctionResolveFailure(ResolvingSolveFailedOrIncomplete(FailedSolve(...)))`.

## Why PrototypeTemplataCalleeCandidate

The `str` function isn't a regular function in scope — it's a **function bound** declared in the `where` clause. Function bounds are represented as `PrototypeTemplataCalleeCandidate` (a prototype that exists because of a bound), not `FunctionCalleeCandidate` (a regular function definition). The overload resolver treats both the same way for param checking.

## Score after fix

1049 passed, 37 failed (was 1048/38).
