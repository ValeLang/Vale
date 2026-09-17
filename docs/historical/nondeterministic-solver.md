# Nondeterministic Solver — Investigation Report

**Date:** 2026-05-17
**Trigger:** `compiler_solver_tests::detects_conflict_between_types` in the Rust port produces an error variant the Scala test doesn't enumerate.
**Outcome:** No fix landed; the divergence is documented as a known migration gap pending a deeper solver-determinism redesign.

---

## Summary

The Scala-to-Rust port of the typing-pass solver inherits an order-dependent firing strategy from Scala. The Scala implementation accidentally produces a stable order via `Map[Int, _].keySet.headOption` over `immutable.HashMap` iteration — the comment claims "lowest ID, keep it deterministic" but the implementation does no such sort. The Rust port took the comment at face value and used `.min()`, which actually does pick the lowest open rule index. The resulting firing-order divergence:

- Changes the *shape* of errors produced for some tests (e.g. the conflict test).
- Changes which *overload candidates succeed* for legitimately compilable programs.

Naive alignment in either direction breaks well-formed tests in the other codebase. Properly fixing this would require a solver-framework redesign so rule-firing order is provably irrelevant to outcomes.

---

## Driving test

`compiler_solver_tests::detects_conflict_between_types` (Rust at `FrontendRust/src/typing/test/compiler_solver_tests.rs:1422-1448`; Scala at `Frontend/TypingPass/test/dev/vale/typing/CompilerSolverTests.scala:564-583`):

```scala
test("Detects conflict between types") {
  val compile = CompilerTestCompilation.test(
    """
      |struct ShipA {}
      |struct ShipB {}
      |exported func main<N Kind>() where N Kind = ShipA, N Kind = ShipB {
      |}
      |""".stripMargin)
  compile.getCompilerOutputs() match {
    case Err(TypingPassSolverError(_, FailedSolve(_, _, _, _, SolverConflict(_, StructDefinitionTemplataT(...ShipA...), StructDefinitionTemplataT(...ShipB...))))) =>
    case Err(TypingPassSolverError(_, FailedSolve(_, _, _, _, SolverConflict(_, StructDefinitionTemplataT(...ShipB...), StructDefinitionTemplataT(...ShipA...))))) =>
    case Err(TypingPassSolverError(_, FailedSolve(_, _, _, _, SolverConflict(_, KindTemplataT(StructTT(...ShipA...)), KindTemplataT(StructTT(...ShipB...)))))) =>
    case Err(TypingPassSolverError(_, FailedSolve(_, _, _, _, SolverConflict(_, KindTemplataT(StructTT(...ShipB...)), KindTemplataT(StructTT(...ShipA...)))))) =>
    case Err(TypingPassSolverError(_, FailedSolve(_, _, _, _, RuleError(CallResultWasntExpectedType(_, KindTemplataT(StructTT(...ShipB...))))))) =>
    case Err(TypingPassSolverError(_, FailedSolve(_, _, _, _, RuleError(CallResultWasntExpectedType(_, KindTemplataT(StructTT(...ShipA...))))))) =>
    case other => vfail(other)
  }
}
```

Six enumerated arms across two error shapes: bare `SolverConflict` (four orderings) and `RuleError(CallResultWasntExpectedType(...))` (two orderings). The Scala author knew the precise firing path was not pinned down and enumerated multiple acceptable shapes — but they treated all six as equally valid, never landing on a single canonical answer.

---

## Observed Rust output

After threading the test through the Rust port with `#[derive(Debug)]` on the error chain (`ITypingPassSolverError`, `IResolvingError`, `IConclusionResolveError`, `IDefiningError`, `FindFunctionFailure`, `IsntParent`, `ResolveFailure`, `ICalleeCandidate` + 3 candidate structs, `IFindFunctionFailureReason`, `RuneTypeSolveError`, 8 rune-type error structs, `IRuneTypeRuleError`, `FunctionHeaderT`, `ParameterT`, `IFunctionAttributeT`, `AbstractT`, `ExternT`, `KindExportT`, `ICompileErrorT`) and a diagnostic `panic!("DIAG: {:#?}", err)` in the catch-all, Rust produces a *seventh* shape:

```
ICompileErrorT::TypingPassSolverError(FailedSolve(_, _, _, _,
    ISolverError::RuleError(RuleError {
        err: ITypingPassSolverError::InternalSolverError {
            range: [...],
            err: ISolverError::SolverConflict(SolverConflict {
                rune: ImplicitRune(...),
                previous_conclusion: Kind(KindTemplataT { kind: Struct(ShipB) }),
                new_conclusion:      Kind(KindTemplataT { kind: Struct(ShipA) }),
            })
        }
    })))
```

The inner `(previous=ShipB, new=ShipA)` pair carries the same logical conflict as the Scala test's expected shapes, but it's wrapped one layer deeper — inside an `InternalSolverError` from a rule body that called `commit_step` and got `Err(SolverConflict)` back.

---

## Where the wrapping comes from

Both Scala and Rust have identical wrapping discipline in `solveCallRule` / `EqualsSR` / etc.: when `commit_step` returns `Err(SolverConflict(...))` from inside a rule body, the rule wraps it as `Err(InternalSolverError(range, err))`. Compare:

- Scala `EqualsSR` body, `Frontend/TypingPass/src/dev/vale/typing/infer/CompilerSolver.scala:728-737`:
  ```scala
  case EqualsSR(range, leftRune, rightRune) => {
    solverState.getConclusion(leftRune.rune) match {
      case None => commitStep(...) match { case Ok(_) => Ok(()); case Err(e) => Err(InternalSolverError(range :: env.parentRanges, e)) }
      case Some(left) => commitStep(...) match { case Ok(_) => Ok(()); case Err(e) => Err(InternalSolverError(range :: env.parentRanges, e)) }
    }
  }
  ```

- Rust `solve_call_rule`, `FrontendRust/src/typing/infer/compiler_solver.rs:2426-2441`:
  ```rust
  ITemplataT::StructDefinition(it) => {
      let args: Vec<ITemplataT<'s, 't>> = arg_runes.iter().map(|...| ...).collect();
      let kind = self.predict_struct(state, ..., *it, &args);
      let conclusions = HashMap::from([(result_rune.rune, ITemplataT::Kind(...))]);
      match solver_state.commit_step::<...>(false, vec![rule_index], conclusions, vec![]) {
          Ok(_) => Ok(()),
          Err(e) => Err(ITypingPassSolverError::InternalSolverError { range: ranges_slice, err: error }),
      }
  }
  ```

The wrap shape is identical. So if both languages take the same path, both produce `RuleError(InternalSolverError(SolverConflict(...)))`. The 6 Scala arms — none of which match this shape — suggest Scala usually *doesn't* take that path.

---

## Verifying which path Scala takes

Instrumented the Scala test to tag each of the 6 arms with `println("DIAG-ARM-N")`. Running just `Detects conflict between types`:

```
DIAG-ARM-6
```

So Scala matches arm 6: `RuleError(CallResultWasntExpectedType(_, KindTemplataT(StructTT(ShipA))))` — and never falls into the catch-all. The `CallResultWasntExpectedType` is emitted by Scala's `solveCallRule` at `CompilerSolver.scala:1031,1034,1049,1052,1084,1087,1116,1119` — directly from the rule body when `result_rune` is already concluded and the call's expected template doesn't match. **Crucially, that emission has no `InternalSolverError` wrap** because it's a structured type-check rejection, not a commit-step conflict.

So Scala fires `CallSR` *after* `result_rune` has been concluded by some other rule, lands in the `Some(result) → CallResultWasntExpectedType` branch, and returns a clean shape. Rust fires `CallSR` *before* `result_rune` is concluded, lands in the `None → commit_step` branch, hits a conflict, and wraps.

---

## Why the firing order differs

`SimpleSolverState.getNextSolvable` is supposed to pick the next ready rule to fire.

**Scala** (`Frontend/Solver/src/dev/vale/solver/SimpleSolverState.scala:103-113`):

```scala
def getNextSolvable(): Option[Int] = {
  openRuleToPuzzleToRunes
    .filter({ case (_, puzzleToRunes) => ... ready test ... })
    // Get rule with lowest ID, keep it deterministic
    .keySet
    .headOption
}
```

The comment claims "lowest ID, keep it deterministic." It's wrong. `openRuleToPuzzleToRunes` is typed `Map[Int, Vector[Vector[Rune]]]` — an `immutable.HashMap` once it has more than 4 entries. `.filter` rebuilds the map into another HashMap. `.keySet.headOption` returns whatever the HashMap iterator's first key happens to be — **hash-bucket-iteration order, not numerical-min order**.

**Rust** (`FrontendRust/src/solver/simple_solver_state.rs:235-248`):

```rust
pub fn get_next_solvable(&self) -> Option<i32> {
    // Get rule with lowest ID, keep it deterministic (matches Scala)
    self.open_rule_to_puzzle_to_runes
        .iter()
        .filter(|(_, puzzle_to_runes)| { ... ready test ... })
        .map(|(rule_index, _)| *rule_index)
        .min()
}
```

The Rust port took the Scala comment at face value and implemented genuine lowest-ID with `.min()`. The two implementations are now semantically different.

### Empirical confirmation

Instrumented Scala's `getNextSolvable` to print `idx`, `rule(idx)`, and the open set on each call. For the conflict test's defining-solve phase, Scala fires:

```
idx 0 = LookupSR(ShipA)
idx 1 = CallSR(template=ShipA-coercion → Y_a)
idx 6 = LookupSR(void)                              ← jumped over 2,3,4,5
idx 2 = EqualsSR(N, Y_a)
idx 5 = EqualsSR(N, Y_b)                            ← jumped over 3,4
idx 7 = CoerceToCoordSR(void)                       ← jumped over 3,4
idx 3 = LookupSR(ShipB)
idx 4 = CallSR(template=ShipB-coercion → Y_b)
```

When `idx 5` fires (EqualsSR(N, Y_b)) — N is already concluded to `KindTemplata(ShipA)` from `idx 2`, Y_b is unconcluded → takes `Some(left) → commitStep(Y_b := N's value)` → **Y_b := KindTemplata(ShipA)**. Then `idx 4` fires (CallSR for ShipB) with `result_rune=Y_b` already concluded → `Some(result)` branch → `kindIsFromTemplate(ShipA, ShipB-template)` returns false → emits `CallResultWasntExpectedType(StructDef(ShipB), KindTemplata(ShipA))` directly.

Rust with `.min()` would fire idx 4 *before* idx 5 — CallSR sees `result_rune` unconcluded → `None` branch → `predict_struct(ShipB) → Y_b := ShipB` → `commit_step` discovers Y_b is transitively N and N is already ShipA → `Err(SolverConflict(N, ShipB, ShipA))` → wrapped in `InternalSolverError`.

The divergence is not subtle; it is the headOption-vs-min decision at the solver core.

### Side note on Scala's "determinism"

Scala's `.keySet.headOption` over an `immutable.HashMap[Int, _]` is technically JVM-implementation-defined. In practice it's reproducible because Scala's HashMap, given small Int keys (whose hash is the int itself), produces a stable bucket layout. But:

- The comment "lowest ID, keep it deterministic" misleads readers into thinking the code does what its Rust transliteration does.
- The "determinism" is incidental to JVM HashMap internals; a Scala upgrade or HashMap-implementation change could alter firing order across the entire test suite without any code change.
- This is fragile by IIIOZ standards (`FrontendRust/docs/arcana/IdenticalInputsIdenticalOutputs-IIIOZ.md`) — pointer-address-style leakage of an implementation detail into output behavior.

---

## What we tried: Scala → `.min` (option a)

Replaced Scala's `getNextSolvable` body with:

```scala
val ready = openRuleToPuzzleToRunes.filter(...).keySet
if (ready.isEmpty) None else Some(ready.min)
```

Ran the full Scala test suite (`sbt test` against `Frontend/`).

**Result:** 1043 passed, 43 failed.

Of those 43, after attributing each failure to its suite (sbt interleaves output, so test names alone are misleading), only **3 are in non-AfterRegions suites** (AfterRegions failures are pre-existing and unrelated):

1. `CompilerSolverTests` — "Detects conflict between types".
2. `CompilerTests` — "Imm generic can contain imm thing".
3. `HammerTests` — "panic in expr".

### Triage

**(1) `Detects conflict between types`** — produces the same `RuleError(InternalSolverError(SolverConflict(KindTemplata(ShipB), KindTemplata(ShipA))))` Rust produces. Fixable by updating the test's arms to match the new shape; 1 file change. *Equivalent error semantically — only the shape changed.*

**(2) `Imm generic can contain imm thing`** — small valid program:
```vale
struct MyImmContainer<T Ref imm> imm
where func drop(T)void { value T; }
struct MyMutStruct { }
exported func main() { x = MyImmContainer<MyMutStruct>(MyMutStruct()); }
```
This is a "should compile, no assertion" test. Under `.headOption` it compiles successfully (well, fails by design because MyMutStruct can't satisfy the imm constraint — but the failure is expected). Under `.min` it fails with **a different error**:
```
Couldn't find a suitable function drop(MyMutStruct). Rejected candidates:
[3 candidates listed, all failing with different rule-fire-time mismatches]
```

The firing-order change made overload resolution fail to find a viable `drop` candidate that the `.headOption` order had found.

**(3) `panic in expr`** — small valid program:
```vale
exported func main() int { return 3 + __vbi_panic(); }
```
This SHOULD compile (`3 + never` is well-formed; `never` coerces to any type). Under `.headOption` it does. Under `.min` it fails with:
```
Couldn't find a suitable function +(i32, never). Rejected candidates:
Candidate 1 (of 4): ...
  Conflict on rune (arg 1): was never, now i32
```

The min-order routes the `+` overload-resolution through candidate 1 (i32, i32) first, where it tries to coerce the second arg from `never` to `i32`, hits a commit_step conflict, and bails. Under `.headOption`, the order presumably gets it to a candidate that accepts `never` first, or routes the coercion differently.

### Significance of failures 2 and 3

These two aren't about error-shape pattern matching. The test bodies only call `expectCompilerOutputs()` — they assert that the program compiles. Under `.min`, **legitimate programs that should compile fail to compile** because overload resolution can't find candidates the other firing order found.

This means Scala's solver framework has *order-dependent overload-resolution behavior* beyond the immediate "where does this error wrap" question. The `.headOption` order isn't just an accidentally-deterministic UI quirk — it's load-bearing for the solver's ability to converge on valid overloads at all.

### Fixing the humanizer (separate but related)

Switching to `.min` also surfaced a separate latent bug: `CompilerErrorHumanizer.humanizeRuleError` (`Frontend/TypingPass/src/dev/vale/typing/CompilerErrorHumanizer.scala:436-518`) has no `InternalSolverError` arm and no fallback. Under the old ordering, no test produced an `InternalSolverError` that needed humanizing; under `.min`, several do, and the missing arm crashes with `scala.MatchError`. Adding the arm is a 5-line additive change. It doesn't help the underlying overload-resolution failures (those would still fail, just with a renderable error instead of a MatchError crash), but it's worth recording: there are gaps in the humanizer that this experiment surfaced.

---

## Options considered

### (a) Switch Scala to `.min` and own the consequences

Update Scala's `getNextSolvable` to genuinely use `ready.min`. Patch the humanizer's missing `InternalSolverError` arm. Update the conflict test's arms to the new shape.

**Cost:** The two compile regressions (Imm generic, panic in expr) need genuine solver fixes — not test updates. Each fix requires understanding why the new firing order routes overload resolution down a dead-end candidate, and either (i) reorganizing the rule bodies so they don't depend on firing order, or (ii) reorganizing overload resolution to retry candidates differently. Open-ended; could cascade.

**Risk:** Two failures surfaced from the small fraction of currently-passing non-AfterRegions tests. There are likely more hidden cases where `.headOption`-order happened to find an overload that `.min`-order can't. The 2 we see may be only the tip; full triage requires implementing fixes and re-running.

### (b) Make Rust mimic Scala's HashMap-bucket iteration order

Port the JVM's `immutable.HashMap[Int, _]` bucket-iteration semantics into Rust. Doable in principle — use a deterministic open-addressing map with the same bucket function (`h ^ (h >>> 16)`) and the same initial capacity / load factor.

**Cost:** Implementation work to write the JVM-compatible HashMap; subtle (Scala's HashMap also rebalances on filter, which interacts with bucket assignments).

**Risk:** Brittle — Scala version upgrades or JVM changes could break the parity. Violates IIIOZ in spirit: output is determined by a fragile reproduction of an implementation detail rather than by intent. Locks Rust to a frozen Scala HashMap implementation forever.

### (c) Fix both Scala and Rust to use a principled deterministic order (`.min`)

Combines (a)'s scope plus the deeper solver work to make overload resolution order-insensitive. Honest, principled outcome.

**Cost:** Largest. Requires solver-framework reasoning: identifying which rules' bodies have order-dependent side effects, making them converge regardless. May involve splitting some rule kinds, ordering puzzles differently, retrying after partial conclusions, or similar redesign work.

**Risk:** Big commit, hard to estimate ahead of doing it.

### (d) Surgical fix to the specific rule body that wraps in `InternalSolverError`

Change `EqualsSR`'s `Some(left) → commitStep(right := left)` branch to first peek at the right rune's conclusion and emit a `CallResultWasntExpectedType`-style structured error directly when both sides are concluded with different values, bypassing `commit_step`.

**Cost:** Lower than (c), higher than (a). But this only fixes the *shape* of error for tests that hit `EqualsSR`-with-two-concluded-runes — it doesn't fix `Imm generic` or `panic in expr` because those hit overload-resolution dead-ends, not EqualsSR conflicts.

**Risk:** Each rule body has its own potential order-sensitivity; (d) is whack-a-mole.

### (e) Accept the divergence; document it; update Rust tests to match Rust's actual output

Leave Scala alone. Acknowledge that Rust's `.min` produces a different rule-firing order than Scala's `.headOption`. For the (currently 1) test where this manifests as a different error shape, update the Rust port of the test to match the *wrapped* shape Rust produces. Document this report. When future Rust tests fail with similar order-related shape divergences, apply the same pattern: update the Rust test's arms.

**Cost:** Low. Per-test test-arm updates as they surface.

**Risk:** Rust and Scala have genuinely different error shapes for some tests. Reviewers comparing the two need to be aware. Future Scala solver changes could shift either codebase's behavior. The order-dependent solver bugs remain (in both languages).

**Bonus:** The `#[derive(Debug)]` additions from this investigation should stay — they're broadly useful for diagnostics and SPDMX Exception D allows derive additions freely.

---

## Recommendation

Option (e). The pragmatic concession is the right one given the migration's priorities:

- The Scala test author already enumerated 6 acceptable shapes, which acknowledges in code that the precise firing path was indeterminate. The Rust port adds a 7th shape; treat it like the other 5 the author didn't anticipate.
- Options (a) and (c) require non-trivial solver work that's out of scope for the typing-pass migration.
- Option (b) is fragile and ideologically wrong (mimicking a JVM implementation detail).
- Option (d) is whack-a-mole.

Defer the solver-determinism redesign to post-migration. When the typing pass is done and the codebase is stabilized, revisit. At that point, option (c) becomes attractive — a one-time solver redesign that makes both languages order-insensitive.

---

## Sequels / pointers for the redesign

- The puzzle definitions at `Frontend/.../CompilerSolver.scala:223-262` and Rust's mirror are the right starting point. Each rule kind's puzzle declares "I'm ready when ANY of these rune sets is fully concluded." That ambiguity is what allows firing-order to matter: many rules become ready simultaneously, and which-first changes which path subsequent rules take.
- The specific divergence point is `EqualsSR(left, right)` with puzzle `Vector(Vector(left), Vector(right))` — it fires when either side is solved, then commits the other side to match. Firing it before vs after a peer `CallSR` that competes for the same result rune determines whether the conflict surfaces as `commit_step → SolverConflict` or as `CallSR Some(result) → CallResultWasntExpectedType`.
- A principled fix might require splitting `EqualsSR` into two phases: a "both-sides-solved-check" rule that fires only when both sides are concluded, and a "propagate" rule that fires when one is. Or: have every `commit_step` failure produce a unified error shape regardless of which rule body triggered it.
- The humanizer gap is independent and worth closing regardless: `humanizeRuleError` needs an `InternalSolverError(_, inner)` arm that delegates to humanize the inner `ISolverError`. Same for the Rust mirror at `FrontendRust/src/typing/compiler_error_humanizer.rs:780`.

---

## Artifacts and trail

- Scala test file edits, all reverted: `Frontend/Solver/src/dev/vale/solver/SimpleSolverState.scala`, `Frontend/TypingPass/src/dev/vale/typing/CompilerErrorHumanizer.scala`, `Frontend/TypingPass/test/dev/vale/typing/CompilerSolverTests.scala`.
- Rust test body, reverted to placeholder `#[ignore] panic!("Unmigrated...")` for `detects_conflict_between_types`.
- `#[derive(Debug)]` additions across ~18 Rust types — kept (broadly useful, no semantic impact, SPDMX Exception D covers).
- `evaluate_generic_function_from_non_call` `?`-propagation at `FrontendRust/src/typing/compiler.rs:1748-1749` (the fix for `reports_incomplete_solve` from earlier in this session) — kept separately; unrelated to the solver-determinism issue.
- Test promotion: `detects_conflict_between_types` left at `#[ignore]` pending decision on test-arm shape.
- Full Scala test output: `tmp/scala-fulltests-min.txt`.
- Full Rust diagnostic output: `tmp/diag-reports-incomplete.txt`.
