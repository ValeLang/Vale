# Guardian Coverage Gaps — Typing Pass Files

Generated 2026-04-27 (re-audited with sonnet after haiku agents proved unreliable).

Only structs, enums, fns, traits — no bare `impl` blocks.
Files under `FrontendRust/src/typing/`.

---

## Uncovered but justified

These lack `/* scala */` blocks because there is no Scala counterpart.
They already have explanatory comments documenting why.

| File | Line | Definition | Justification |
|------|------|------------|---------------|
| templata_compiler.rs | 68 | `struct IPlaceholderSubstituter` | Scala source is a trait defined inside a method body (lines 65–67 explain). No top-level Scala anchor exists. |
| env/environment.rs | 576 | `struct TemplatasStoreT` | Has `// Guardian: disable-all` on line 573 (pre-annotation). Scala counterpart is `case class TemplatasStore` but the Rust fields diverged during the ArenaIndexMap migration. |
| names/names.rs | 3112–3492 | ~20 `*ValT` structs + `INameValT` enum | IDEPFL Val/Query types — Rust-only interning scaffolding with no Scala counterpart. Section header at line 3059 explains. |

---

## Uncovered, not justified, tiny fix needed

These just need a `/* Guardian: disable-all */` or `// (no scala counterpart)` annotation
added on the line after the closing brace — a one-line edit each.

| File | Line | Definition | What's on the next line | Fix |
|------|------|------------|------------------------|-----|
| compiler_outputs.rs | 66–119 | `struct CompilerOutputs` | blank, then `impl` | Add `/* Guardian: disable-all */` after line 119 (Scala counterpart is `class CompilerOutputs` which is a class with mutable state, not a case class — no clean `/* scala */` block to put here) |
| reachability.rs | 24–31 | `struct Reachables` | blank, then `impl` | Add `/* scala counterpart: class Reachables(...) */` after line 31, or move the `/*` from line 34 up |

---

## Uncovered, not justified, needs more than a tiny fix

None found.

---

## Summary

The original haiku agent audit reported ~460 gaps. After re-auditing with
sonnet agents that actually verified the next line, the real count is:

- **~22 justified** (Val structs + 2 explained structs)
- **2 tiny fixes** (CompilerOutputs, Reachables)
- **0 larger fixes**

Every other file in `src/typing/` is fully covered.
