# Fix: `CoordSendSR` Some-receiver branch when sender is already known

## Status

**NOT LANDED.** Diagnosed, fix designed and verified end-to-end (Scala 1104/1104 ✓, Rust 766/767 with only the dependent test still blocked on a downstream JR cascade unrelated to the solver). Currently reverted — fix to be landed in a coordinated push later, ideally with the Scala fix going upstream too.

## The bug

The typing-pass solver's `CoordSendSR` rule has two puzzles `[[senderRune], [receiverRune]]` — it fires as soon as either side is known. Its body has two branches:

- **None-receiver branch** (Scala `CompilerSolver.scala:792` / Rust `compiler_solver.rs:1586`): sender known, receiver unknown. Uses `isDescendant(sender.kind)` to either defer via `CallSiteCoordIsaSR` (descendant case — includes Never via `Compiler.scala:244`) or commit `receiver = sender_coord`.
- **Some-receiver branch** (Scala `CompilerSolver.scala:805` / Rust `compiler_solver.rs:1622`): receiver known. Uses `isAncestor(receiver.kind)` to either defer (interface case) or commit `sender = receiver_coord`.

The Some-receiver branch's commit assumes sender is **not yet bound**. When sender IS already bound (the typical case via `InitialSend` pre-population) **and** the existing sender conclusion isn't strictly equal to `receiver_coord`, the strict-equality check in `commit_step` fails with `SolverConflict`.

Concrete case: `3 + __vbi_panic()` resolves to overload `+(int, int)`. The `InitialSend` pre-binds `ArgumentRune(1) = Coord(Never)` (from `__vbi_panic()`). When `CoordSendSR(sender=ArgRune1, receiver=ParamRune1)` enters the Some-receiver branch with receiver=Int, it tries `commit(sender = Int)` — conflicts with the pre-bound Never.

## Why Scala works (luck) and Rust doesn't (deterministic)

Scala's `SimpleSolverState.getNextSolvable` uses `Map.keySet.headOption` on `immutable.HashMap`, which iterates in **hash order** — not by key. For the `+(int, int)` candidate, the hash order happens to pick `CoordSendSR` *before* the receiver-populating Lookup/CoerceToCoord rules. So when CoordSendSR fires in Scala, receiver is still None → None-branch → `isDescendant(Never)=true` → deferral → success.

Rust's port at `simple_solver_state.rs:248-261` uses `.min()` to deterministically pick the **lowest rule index**. The Scala code comment claims "Get rule with lowest ID, keep it deterministic," but that's misleading — Scala's actual behavior is hash-deterministic, not numerically-lowest. The Rust port took the comment at face value and changed behavior. With `.min()`, the receiver-populating rules (lower indices, in the candidate's own rule list) fire before CoordSendSR (higher index, appended via InitialSend) → receiver is populated → CoordSendSR enters Some-branch → conflict.

So the underlying bug is in the **rule body's design** (Some-branch can't handle a pre-bound sender), not in either Rust or Scala specifically. Scala dodges it by hash-order luck. Rust's deterministic ordering exposes it.

## The fix

One-condition widening of the existing `if` in the Some-receiver branch:

```scala
case Some(CoordTemplataT(coord)) => {
  // Defer to CallSiteCoordIsaSR (which does proper convertibility verification, including
  // the NeverT bypass) when we can't safely shortcut to commit(sender = receiver_coord):
  //   - sender is already known (the InitialSend pre-binding case): commit would conflict
  //     whenever sender's existing conclusion isn't strictly equal (e.g. Never vs Int).
  //   - receiver is an interface: existing isAncestor deferral.
  if (solverState.getConclusion(senderRune.rune).isDefined || delegate.isAncestor(env, state, coord.kind)) {
    val newRule = CallSiteCoordIsaSR(range, None, senderRune, receiverRune)
    solverState.commitStep(...)
  } else {
    // Receiver is concrete, sender unknown — derive sender = receiver_coord.
    solverState.commitStep(... Map(senderRune.rune -> CoordTemplataT(coord)) ...)
  }
}
```

The diff vs. the original: one `if` condition widened (`isAncestor(coord.kind)` → `getConclusion(senderRune.rune).isDefined || isAncestor(coord.kind)`). Six-line comment block added. No new match, no new nesting.

`CallSiteCoordIsaSR` is the typing-pass's convertibility-verification rule (Scala `CompilerSolver.scala:725-754`). Its body handles strict equality, the NeverT bypass (`subCoord.kind match { case NeverT(_) => true ... }` at line 731), and the general impl-tree lookup. So deferring to it from the Some-branch when sender is already known just routes the verification through the existing convertibility machinery — no new bypass code paths, no Never hardcode.

## Why this fix is principled (not "using CallSiteCoordIsaSR for its Never check")

`CallSiteCoordIsaSR` *is* the convertibility-verification rule. When `CoordSendSR`'s Some-branch finds "receiver known, sender also known," the right operation is to **verify convertibility** (there's nothing left to derive). That's exactly what `CallSiteCoordIsaSR` does. Its Never bypass is a fundamental fact about Never (Never is a subtype of everything) — not a coincidence we're piggybacking on.

This also mirrors the None-branch: when the None-branch can't safely shortcut (`isDescendant(sender.kind)=true`), it defers to `CallSiteCoordIsaSR`. The Some-branch now does the symmetric thing when *it* can't safely shortcut (sender is already bound).

## Verification

### Scala
Applied the change to `Frontend/TypingPass/src/dev/vale/typing/infer/CompilerSolver.scala:805` (Some-receiver branch).

Full Scala test suite: **1104 tests passed, 0 failed, 27 ignored, 0 aborted.** Same baseline as before the change. Zero regressions.

Including:
- `dev.vale.HammerTests`: 11/11 ✓ (`panic in expr` ✓)
- All typing/instantiating/simplifying tests ✓

### Rust
Applied the matching change to `FrontendRust/src/typing/infer/compiler_solver.rs:1622` (Some-receiver branch) + audit comment update.

Full Rust test suite: **766/767 passed.** Only failure is `panic_in_expr` itself, which after the typing-pass fix now panics at `instantiator.rs:5188` (`translate_parameter` body fill — normal JR cascade work, not solver-related). So Option D resolves the typing-pass blocker as designed; the test still has downstream cascade work in the instantiating pass to land before going green.

SCPX: 260/260 ✓ (after updating the Rust audit comment to mirror the new Scala).

## Rejected alternatives (for posterity)

- **Option A** — change `CoordSendSR`'s puzzles from `[[sender], [receiver]]` to `[[sender]]` only. Doesn't actually fix the bug on Rust because the scheduling difference (Lookup rules fire first via `.min()`) still leads to the Some-branch with receiver already populated. Benign on Scala (passes the suite) only because Scala's hash-order already dodged the bug; the puzzle change was a no-op for Scala. **Net: necessary-looking but not sufficient. Misdiagnosed.**

- **Hardcode the Never bypass in the Some-branch.** Special-cases one kind, doesn't generalize, duplicates logic that `CallSiteCoordIsaSR` already implements.

- **Make `commit_step` use `is_type_convertible` instead of strict equality.** Embeds the convertibility check at the solver level. Wider blast radius — affects every rule's commits — and risks masking real conflicts. Riskier than necessary for the actual bug being fixed.

- **Mirror Scala's hash iteration order in Rust's `get_next_solvable`.** Fragile: depends on JVM internals, would inherit Scala's latent bug if another input flips the hash bucketing, and the underlying rule-body bug remains.

## Where the fix should land

- `Frontend/TypingPass/src/dev/vale/typing/infer/CompilerSolver.scala:805` — Scala source of truth.
- `FrontendRust/src/typing/infer/compiler_solver.rs:1622` — Rust port. Audit comment at the matching line should mirror the new Scala.

Also worth cleaning up but not blocking:
- `Frontend/Solver/src/dev/vale/solver/SimpleSolverState.scala:120-121` and `FrontendRust/src/solver/simple_solver_state.rs:249` — the misleading "Get rule with lowest ID, keep it deterministic" comment. Either rephrase to "iterate in hash order" or actually make Scala's behavior numerically-lowest to match the comment. The fix above doesn't depend on this.

## Diagnostic narrative

See `investigations/panic_in_expr.md` for the original collapsed call tree (6 levels of typing-pass diagnostic ratcheting) that led to this finding.
