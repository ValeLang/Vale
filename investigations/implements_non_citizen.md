# Investigation: "Detects sending non-citizen to citizen" test failure

## Bug summary

Test: `dev.vale.typing.AfterRegionsErrorTests` / "Detects sending non-citizen to citizen"

Code under test:
```vale
interface MyInterface {}
func moo<T>(a T) where implements(T, MyInterface) { }
exported func main() { moo(7); }
```

Expected: `CouldntFindFunctionToCallT` rejecting `moo(7)` because `int` is not a citizen (struct/interface) and can't satisfy `implements(T, MyInterface)`. The rejection should be `InferFailure(FailedSolve(..., SendingNonCitizen(IntT(32))))`.

Actual: `CouldntFindFunctionToCallT` for **`drop`**, not `moo`. The compiler never reaches `moo(7)`.

## Collapsed call tree

```
- Compilation of moo<T>(a T) definition:
  - ExpressionCompiler#evaluate(VoidSE):
    Compiles the empty body { } — produces VoidLiteralTE.
  - (implicit drop of param `a` at end of body)
    - OverloadResolver#findPotentialFunction("drop", candidates=2):
      Looks for drop(T) where T is KindPlaceholderT. Finds 2 drop candidates
      (the interface's macro-generated drops). Neither accepts a placeholder T.
      FAILS with CouldntFindFunctionToCallT for drop.
- Compilation of main() body:
  NEVER REACHED — moo's definition failed first.
```

## Root cause

The compiler fails when **defining** `moo`, before any call site is reached.
`moo<T>(a T)` takes ownership of `a`, so at end of body the compiler must drop `a`.
Dropping requires `func drop(T)void`, but `moo` doesn't declare that bound —
it only has `where implements(T, MyInterface)`.

The test was written expecting that:
1. `moo` would compile successfully (perhaps with an implicit `drop` bound from `implements`)
2. `main` would call `moo(7)` 
3. The call-site solver would reject `int` because it doesn't implement `MyInterface`

But step 1 fails because the compiler now requires explicit `drop` bounds for generic params.

## Resolution

Two fixes were needed:

1. **Added `func drop(T)void` to the where clause** — the test's `moo` function takes ownership
   of `a T`, so it needs a `drop` bound. Without it, `moo`'s definition fails before any call site
   is reached.

2. **Updated the test's match pattern** — the error wrapping changed:
   - Old: `InferFailure(FailedSolve(..., SendingNonCitizen(IntT(32))))`
   - New: `FindFunctionResolveFailure(ResolvingSolveFailedOrIncomplete(FailedSolve(..., BadIsaSubKind(IntT(32)))))`
   
   `BadIsaSubKind` is semantically equivalent to `SendingNonCitizen` — `int` is not a valid
   sub-kind for an ISA (implements) check. The error just has a different name now because the
   rejection happens in `CallSiteCoordIsaSR` (line 684 of CompilerSolver.scala) rather than
   `CoordSendSR`.

Test now passes. Score: 1047 passed, 39 failed.
