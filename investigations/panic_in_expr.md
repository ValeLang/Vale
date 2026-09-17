# Investigation: `panic_in_expr` — typing pass fails to resolve `+(int, Never)`

## Test

`integration_tests::tests::hammer_tests::panic_in_expr`:
```vale
import intrange.*;
exported func main() int { return 3 + __vbi_panic(); }
```

Typing pass returns `CouldntFindFunctionToCallT` for `+(int, Never)`. All 4 `+` candidates rejected.

## Collapsed call tree

```
- TypingPassCompilation#expect_compiler_outputs ... CompilerSolver#advance_infer ... SimpleSolverState#get_next_solvable:
    Returns LOWEST rule index whose puzzle is satisfiable (via Vec::min). Differs from Scala (see below).

- advance_infer loop fires rules in INDEX ORDER 0,1,2,3,4,5,6,7 for the `+(int,int)` candidate.
  Rules 0-5 are Literal/Lookup/CoerceToCoord; together they populate the receiver param rune (ImplicitRune[3,1,1,1,1] → Coord(Int)).
  Rules 6, 7 are CoordSendSR for arg 0 and arg 1.

- compiler_solver#solve_rule#CoordSendSR (rule #7, sender=ArgumentRune(1)):
    By the time this rule fires, BOTH runes are in conclusions:
      sender_rune  = ArgumentRune(arg_index: 1) = Coord(Never)    [from __vbi_panic()]
      receiver_rune = ImplicitRune[3,1,1,1,1]    = Coord(Int(32))
    Rule takes Some-receiver branch (compiler_solver.rs:1622), checks is_ancestor(Int)=false,
    calls commit_step(sender_rune → Coord(Int)). Existing Never ≠ new Int → SolverConflict.

- simple_solver_state#commit_step:
    Strict `existing != new` check at line 154. No Never bypass.
    Returns Err(SolverConflict { rune: ArgumentRune(1), previous: Coord(Never), new: Coord(Int) }).
```

## Why Scala's same rule body works

Scala `CompilerSolver.scala:794` None-receiver branch checks `delegate.isDescendant(sender_coord.kind)`.
Scala `Compiler.scala:244` makes `isDescendant(NeverT) = true`.
So when CoordSendSR fires with **only sender known** (receiver still None), the rule:
  - Enters None-branch (line 792)
  - isDescendant(Never)=true
  - Defers via newRule = CallSiteCoordIsaSR (line 797-798) — CoordSendSR closes successfully.
  - Later CallSiteCoordIsaSR fires with NeverT bypass at `CompilerSolver.scala:731-732` → succeeds.

**Key requirement**: CoordSendSR must fire while receiver is None.
**In Rust**: receiver is populated by lower-indexed rules before CoordSendSR fires → Some-branch → conflict.
**In Scala**: scheduling allows CoordSendSR to fire earlier (see scheduling diff below).

## Root-cause candidate

`FrontendRust/src/solver/simple_solver_state.rs:248-261` — Rust `get_next_solvable` uses `.min()` to pick the literally lowest rule index. **The Rust comment claims this matches Scala** ("Get rule with lowest ID, keep it deterministic (matches Scala)"), but the **Scala version uses `.keySet.headOption`** on `Map[Int, Vector[Vector[Rune]]]` (`Frontend/Solver/src/dev/vale/solver/SimpleSolverState.scala:113-123`).

`Map.keySet.headOption` on Scala's immutable.HashMap iterates in HASH order, not key order. The Scala behavior is **non-deterministic w.r.t. rule index** — it just picks whatever the HashMap's keySet iterator returns first.

The migration bug: the Rust port "fixed" Scala's apparent non-determinism by adding `.min()`, but the actual Scala behavior probably picks rules in an order that happens to fire CoordSendSR while receiver is still None. The Rust deterministic-lowest order forces CoordSendSR to fire AFTER the param-lookup rules populate receiver, breaking the None-branch bypass.

## Confirmed via instrumentation

Trace excerpt (`tmp/panic_in_expr-trace.txt`, around line 25840):
```
[PIE] picking rule #0: Literal     ← populates ArgumentRune(1) = Coord(Never)
[PIE] picking rule #1: CoerceToCoord
[PIE] picking rule #2: Lookup
[PIE] picking rule #3: CoerceToCoord
[PIE] picking rule #4: Lookup
[PIE] picking rule #5: CoerceToCoord  ← by here, receiver is populated
[PIE] picking rule #6: CoordSend       ← arg 0, sender=Int, receiver=Int — Some-branch, no conflict (equal)
[PIE]   CoordSendSR body: sender_rune=ArgumentRune(0), receiver_rune=ImplicitRune[2,1,1,1,1]
[PIE]     sender conclusion: Some("Coord(Int)")
[PIE]     receiver conclusion: Some("Coord(Int)")
[PIE]     → Some-branch
[PIE]     → isAncestor(Int) = false
[PIE] picking rule #7: CoordSend       ← arg 1, sender=Never, receiver=Int — Some-branch, CONFLICT
[PIE]   CoordSendSR body: sender_rune=ArgumentRune(1), receiver_rune=ImplicitRune[3,1,1,1,1]
[PIE]     sender conclusion: Some("Coord(Never)")
[PIE]     receiver conclusion: Some("Coord(Int)")
[PIE]     → Some-branch
[PIE]     → isAncestor(Int) = false
[PIE] CONFLICT in commit_step: solved_rule_indices=[7], complex=false, new_rules.len()=0, conclusions.len()=1
```

The conflict at rule #7 is exactly the leaf SolverConflict from JR's round-62 diagnostic.

## The likely fix (NOT MY CALL — diagnose only per skill)

Whichever way the user prefers:
- **(a)** Change Rust `get_next_solvable` to non-deterministically match Scala's iteration order. Hard to make exactly match.
- **(b)** Change Rust `get_next_solvable` to prefer CoordSendSR / specific rule classes that benefit from the None-branch. Heuristic; awkward.
- **(c)** Add a Rust-only Never-bypass in CoordSendSR's Some-receiver branch (or in commit_step's equality check). Bytecode-different from Scala but observationally equivalent.

This is the architect's design call. NOT making it per the skill — diagnose only.

## Instrumentation still in place

- `FrontendRust/src/typing/infer/compiler_solver.rs`: rule-pick eprintln + CoordSendSR body eprintln.
- `FrontendRust/src/solver/simple_solver_state.rs`: commit_step CONFLICT eprintln.
- `FrontendRust/src/typing/compilation.rs`: extended diagnostic for CouldntFindFunctionToCallT.

Scala files: untouched.
