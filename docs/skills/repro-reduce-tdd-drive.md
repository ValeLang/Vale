---
name: repro-reduce-tdd-drive
description: Use a real-world Vale program (currently roguelike.vale) as a probe to find typing-pass migration gaps; for each panic surfaced, write a minimal Vale-fragment reproducer test, then port one layer of Scala body at a time until the reproducer goes green and the probe advances to the next gap.
---

> **Every time you compact, re-read this file** (`docs/skills/repro-reduce-tdd-drive.md`). It changes often as the TL adds notes about new patterns and gotchas learned during the session. Compaction drops the prior conversation but not the file — if you re-read it, you pick up everything the previous instance learned. If you don't, you'll repeat mistakes that have already been documented.

This loop is for when the typing pass is mostly migrated but you want to **find what's still missing in the order a real program needs it**. Synthetic per-test snippets exercise typing-pass paths the test author had in mind; a real Vale program exercises whatever Vale's compilation actually needs. Each gap the real program hits is a real migration item.

## The pattern

1. First, look at these files in full. Do not skip any. You will need to adhere to all of these.
    * `FrontendRust/docs/usage/test-helpers.md` — test conventions.

2. **Run the probe.** The probe is currently `typing_pass_on_roguelike` in `FrontendRust/src/typing/test/compiler_project_tests.rs`. Run it:

    ```
    RUST_MIN_STACK=33554432 cargo nextest run --manifest-path /Volumes/V/Sylvan/FrontendRust/Cargo.toml \
      -E 'test(typing_pass_on_roguelike)' --run-ignored=all > ./tmp/probe.txt 2>&1
    ```

    The `RUST_MIN_STACK` is required — the typing pass overflows the default 2MB stack.

    If the probe passes, you're done with this skill — congratulations. Otherwise, read the topmost `panicked at <file>:<line>` message in the output. That's your **next gap**.

3. **Confirm the probe is the right one and the source is Scala-clean.** Before assuming the panic represents a real Rust-side migration gap, verify it isn't a roguelike-source issue: run the Scala-side counterpart at `Frontend/IntegrationTests/test/dev/vale/IntegrationTestsA.scala::"Roguelike typing pass"`:

    ```
    cd /Volumes/V/Sylvan/Frontend && sbt 'testOnly dev.vale.IntegrationTestsA -- -t "Roguelike typing pass"' > ./tmp/scala-probe.txt 2>&1
    ```

    The Scala test **must pass**. If it doesn't, the roguelike source is itself broken — fix `Frontend/Tests/test/main/resources/programs/roguelike.vale`, sync to `FrontendRust/src/tests/programs/roguelike.vale`, and only then resume the Rust loop. Common roguelike-source issues (already fixed at time of writing, recurrences are still possible): stale stdlib imports, missing `import string.*` for `==(str, str)`, missing `extern func getch() int`, owned-RSA passed to a borrow-taking `each`/`len` (use `drop_into` and `.method()`-on-local-binding-not-on-chained-call).

4. **Write a minimal Vale-fragment reproducer test** that triggers **only** the gap from step 2. Add it to `FrontendRust/src/typing/test/compiler_project_tests.rs`. The body shape must mirror the existing precedents (`typing_pass_uses_same_instance`, `typing_pass_array_type_convertible`, `typing_pass_tuple_literal`, etc.): direct `TypingPassCompilation::new(..., vec![&BUILTIN, &test_tld], get_code_map(...), ...)` — **not** `compiler_test_compilation`, which only loads TEST_TLD and can't load stdlib-using programs.

    Pick the **smallest** Vale fragment that triggers the panic. Examples from prior loops:
    - `set x = 1` inside a lambda → closure-var mutate.
    - `x = (3, 4);` → tuple literal eval chain.
    - `[a, b] = arr;` where arr is `#[#](3, 4)` → SSA destructure.
    - `m = MyStruct(...); destruct m;` → destruct expression eval.

    Each reproducer is **novel code** with no Scala counterpart, so it WILL trip NNDX/NCWSRX/SPDMX. That's a TL/architect-level escalation per `TL.md`'s NNDX rules — **stop and escalate** (paste the test body into `for-tl.md`); don't try to temp-disable yourself. The TL adds the test for you.

5. **Confirm the reproducer fails on the exact same panic** as the probe:

    ```
    cargo nextest run --manifest-path /Volumes/V/Sylvan/FrontendRust/Cargo.toml \
      -E 'test(<your_new_test_name>)' > ./tmp/probe.txt 2>&1
    ```

    Identical panic message → you're at red, ready to port.

6. **Port ONE layer.** Find the Scala function that the panic stub corresponds to (the `/* ... */` audit-trail block right below it). Translate just that one function's body to Rust, line by line, leaving inner-stub call sites as-is. Don't pre-port the chain.

    Then build:
    ```
    cargo build --manifest-path /Volumes/V/Sylvan/FrontendRust/Cargo.toml --tests > ./tmp/probe.txt 2>&1
    ```
    Then re-run the reproducer.

7. **Reading the result:**
    - **Test passes (green):** good — go to step 8.
    - **Test fails on a NEW `Unimplemented`/`implement:` panic at a different file:line:** that's the next layer down (a function your just-ported body called). Go back to step 6 for that new panic. **Don't write a new reproducer test** — the same reproducer is exercising the chain. One reproducer covers the whole chain it triggers.
    - **Test fails on the SAME panic at the same file:line:** your port didn't reach the panic site or something went wrong. Inspect.
    - **Test fails on a real compile error (no `Unimplemented`/`implement:` in message):** that's a logic bug, NOT a migration gap. Stop and escalate to TL.
    - **Build error (lifetime, type, signature mismatch):** if it's a small adaptation (param ownership, scoped lifetime), fix it. If it's a signature change that propagates across multiple files, **stop and escalate** — those are TL territory.

8. **Reproducer green — confirm no regressions.** Run the full active test suite:
    ```
    cargo nextest run --manifest-path /Volumes/V/Sylvan/FrontendRust/Cargo.toml > ./tmp/probe.txt 2>&1
    ```
    Must be 100% pass (modulo pre-existing `#[ignore]`'d tests). If anything that was previously green is now red, you broke something while porting — go figure out what.

9. **Re-run the probe (step 2) for the next gap.** Loop.

10. **When the probe passes end-to-end:** un-ignore `typing_pass_on_roguelike` if it's still ignored, run full suite once more, and stop.

## Discipline rules

* **One reproducer per probe iteration, not per layer.** The probe surfaces a panic. You write ONE reproducer. As you port layer-by-layer, the reproducer stays the same — you're peeling onion layers, not exploring a new tree. Only when the reproducer goes green and the probe surfaces a DIFFERENT panic do you write a new reproducer.

* **One Scala body per port.** The temptation is "I see the whole chain, let me port resolve_tuple + make_tuple_coord + make_tuple_kind in one shot." Don't. Port resolve_tuple body only. Run. If next panic is make_tuple_coord, port that body only. Run. If next panic is make_tuple_kind, port that body only. Run. The reason is that each layer might have its own surprises (missing types, signature shape mismatches, untyped helper calls) that don't surface until you're actually building against the layer above. Pre-porting compounds error chains that are hard to unwind.

* **No speculative body fills.** If a Scala body calls a function whose Rust counterpart is a panic stub, leave it as a panic call — your next reproducer iteration (or the chain's continuation) will hit it and prompt the next port.

* **Reproducer source must be minimal.** "Minimal" means the smallest Vale fragment that triggers the exact panic. Don't include unrelated constructs that might add OTHER blockers and confuse "did I make progress?"

* **Reproducer must mirror Benchmark-style setup.** Stdlib resolution requires `vec![&BUILTIN, &test_tld]` AND `get_code_map(...)` (full dual-namespace builtin map), NOT `compiler_test_compilation` (which only loads TEST_TLD with the modulized variant). If your reproducer uses stdlib types (`Opt`, `List`, `HashMap`, `===`, arrays, etc.), `compiler_test_compilation` won't even resolve them.

* **The Scala probe must be Scala-clean before any Rust ports.** If `IntegrationTestsA::"Roguelike typing pass"` fails in Scala, the Vale source is broken. Don't bury Rust-side workarounds for a broken-in-both-languages source — fix the source first, sync the file to both copies, then resume.

## Notes

* **Use the Scala counterpart as the source of truth for each body.** The Rust port translates 1:1 from the `/* ... */` block below the stub. No novel logic, no Rust-idiomatic "improvements" beyond what Rust strictly requires to compile.

* **Adjacent helper calls.** Many Scala bodies call helpers like `localHelper.makeTemporaryLocal(...)`, `destructorCompiler.drop(...)`, `templataCompiler.getPlaceholderSubstituter(...)`. In Rust, these flatten onto `Compiler` (per design v3 §2.1 god struct). Call them as `self.make_temporary_local(...)`, `self.drop(...)`, `self.get_placeholder_substituter(...)`. If the helper is itself a stub, that's fine — the next iteration of step 6 (or 7's continuation) will hit it.

* **Adding `interner` / `scout_arena` / `keywords` parameters is fine.** Scala uses GC; Rust often needs an arena handle to allocate. SPDMX Exception B covers it.

* **Stop when you escalate, don't keep driving in parallel.** When you escalate (lifetime puzzle, NNDX-blocked test add, structural change, ambiguous Scala source), stop and wait for the TL response in `for-jr.md`. Never defer or skip the current probe iteration to move on.

* **`for-jr.md` / `for-tl.md` escalation files.** Escalations to TL go in `for-tl.md`; TL responses to JR go in `for-jr.md`. Check `for-jr.md` when the architect says just "z".

* **Update the probe over time.** As the typing pass approaches end-to-end compilation of roguelike, this skill's probe will graduate to the next real-world program (e.g. one of the stdlib's own tests, or an integration-test sample). When that happens, update step 2's test name and step 3's Scala counterpart. The pattern stays; only the probe target changes.
