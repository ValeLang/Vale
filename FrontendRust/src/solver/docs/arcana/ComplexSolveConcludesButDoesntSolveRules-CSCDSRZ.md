# Complex Solve Concludes But Doesn't Solve Rules (CSCDSRZ)

Complex solve infers conclusions (e.g., receiver types from sender patterns) by examining multiple unsolved rules, but it does **not** mark any of those rules as solved. The rules remain unsolved until the next simple solve pass, which picks them up now that the conclusions they need are available.

## Where

- `CompilerSolver.complexSolveInner` / `CompilerRuleSolver.complexSolve` (Scala and Rust)
- `SimpleSolverState.commitStep` — called with empty `solvedRuleIndices` from complex solve
- `advanceInfer` / `advance` loop — the stage 1 (simple) -> stage 2 (complex) -> back to stage 1 cycle
- `TestRuleSolver.complexSolveInner` in solver tests — same pattern

## Cross-cutting effect

The `commitStep` call from complex solve passes **empty** `solvedRuleIndices` and **empty** `newRules`. This looks like a bug — why would a solve step not solve any rules? — but it's correct. Complex solve's job is only to produce conclusions that unblock simple solves. The actual rule-solving happens in the subsequent simple solve pass.

This means:
1. After a complex solve step, `getUnsolvedRules()` still returns the same rules as before.
2. The only observable effect of a complex solve step is new entries in `getConclusions()`.
3. The `advance` loop must check whether conclusions changed (not rules solved) to decide if progress was made.
4. A rule is only marked solved when a **simple** solve step processes it via `commitStep` with that rule's index in `solvedRuleIndices`.

## Why it exists

Complex solve works by finding patterns across multiple unsolved rules simultaneously (e.g., "these three senders all send to the same receiver, and they're all subtypes of interface X, so the receiver must be X"). It doesn't "solve" any single rule — it synthesizes information from several rules to produce a conclusion. The individual rules that contributed to this inference still need to be processed by simple solve to verify their constraints and mark themselves complete.

If complex solve tried to mark rules as solved, it would need to know which specific rules were "consumed" by the inference — but the inference draws from overlapping sets of rules, and each rule may still have additional constraints to verify once the conclusion is known.
