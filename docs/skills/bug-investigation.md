---
name: bug-investigation
description: External real-world report → minimal in-tree regression test asserting correct output → fix. Use when a real-world program surfaces a compiler bug.
g_read_when: Read when an external real-world program surfaces a compiler bug and you need to reduce it to a minimal in-tree repro before fixing.
g_mention_in:
  - CLAUDE.md
---

# Bug Investigation

External real-world report → in-tree regression test asserting *correct* output → fix.

## Loop

1. **Probe.** Run the target real-world program through the binary; observe failure.
2. **Diagnose.** Trace to root cause. Test hypotheses against the actual call graph — don't fix from the surface symptom.
3. **Reduce to minimal in-tree repro.** Bisect / synthesize the smallest self-contained input that reproduces. Embed as a test.
4. **Write the test asserting *correct* output.** It fails now because of the bug; passes after the fix.
5. **Fix.** Apply the root-cause correction.
6. **Verify.** Regression test passes + full suite.

## Rules

- **No fix without an in-tree minimal repro first.** External probes (out-of-tree files, real-world programs, file-path-dependent tests) prove the bug exists; they never license a fix on their own.
- **Tests assert correct behavior, not symptoms.** Don't pin current panic messages or buggy output.
- **No bug-class commentary in test bodies.** The "what went wrong" lives in the commit message. Tests are self-evident from name + code.
- **Tight assertions only.** `assert_eq!` or `ends_with(<deterministic suffix>)`. Never `contains() && contains()`.
- **Tests live in `<module>/tests/<module>_tests.rs`.** Mirror the `parsing/tests/` convention. No inline `#[cfg(test)] mod` in production source.
- **Never modify the user's source.** Probes / hypothesis tests on real-world programs run against `cp -R` copies in `/tmp/`, never in-place edits — and never `sed -i` anywhere.
- **False reduction.** A bisect step that changes the failure mode (new panic site or different error) has strayed onto a different bug. Back up; try a different turn. The new symptom is information, not progress.
- **Verify the in-tree repro fires at the same panic site, not just *any* error.** Run the test and confirm the panic file:line matches the probe. A test that reaches a different failure silently widens scope.
- **Place the minimal repro at the earliest pass that triggers it.** If the panic fires in higher-typing, the test lives in `higher_typing/tests/`, not `pass_manager/end_to_end_test.rs`. End-to-end is the surface that surfaces the gap; the regression test belongs at the lowest layer that still reproduces it — narrower call graph, faster iteration, fewer downstream changes that could mask it.
- **External-repro tests stay `#[ignore]`'d.** Out-of-tree-path / real-world probes document the bug; only the minimal in-tree repro joins the baseline.
- **Don't trust the bug-report's framing.** Verify the cited file:line and the call graph; hypotheses get tested, not adopted. Pre-existing precedent isn't license — if both sites are wrong, fix both.
- **Encoding / byte-vs-char is a recurring bug class.** Rust byte positions vs UTF-16 code units; `chars().nth(byte_index)` is the canonical mistranslation.
- **Nondeterminism → stop and report.** Don't push through suspected races or probabilistic failures.

## Bisecting cross-module triggers

Some panics only fire when multiple Vale packages are loaded together — never on any single package alone. Standard approach when the obvious single-file repro doesn't trigger:

1. Start with the full set that triggers (e.g. real `stdlib` + `vmdparse` + `parseiter` + `vmdsitegencmd`).
2. **Leave-one-out** each top-level package; the ones whose removal makes the panic *disappear* are required.
3. Within each required package, **leave-one-out each `import`** in the main file. Same logic.
4. Within each required imported file, **leave-one-out each function/struct/impl**. Each step preserves the same failure mode; if the failure mode changes, you've strayed (false reduction — back up).
5. Once you have the minimum set, **find the actual triggering function/site** by adding a one-liner `eprintln!()` inside the panic arm, capturing the struct id / name / range, then mapping that back to the source line that produced it. Revert the `eprintln!()` before committing.

This is how the `AnonymousSubstruct` repro was minimized from 1300 lines of real Vale to 7 lines of synthetic.

## Don't trust test-infra panics that look upstream

When the test harness short-circuits to a stub-panic on `Err` returns, the file:line you see is almost never the real bug — the real bug is in the `Err` value above it. Bypass the high-level `expect_*` wrapper and call the lower-level `get_*` variant directly, then pattern-match on the returned `Err(...)` variant for the test assertion.
