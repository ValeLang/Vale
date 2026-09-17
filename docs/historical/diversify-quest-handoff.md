# Diversify Quest — Investigation Handoff

**Date written:** 2026-05-15
**Branch:** `rustmigrate-z` (uncommitted changes; see "Code state" section)

## TL;DR

We're trying to find a cheap, fast LLM model (or model-config combo) that's good enough to replace Sonnet as the SPDMX (Scala Parity During Migration) shield judge in Guardian. The investigation surfaced (a) a real Guardian bug in how shield rules get logged, (b) several Guardian filtering quirks that affect comparison validity, and (c) a *very surprising* result where the Claude backend itself caught only 6/17 cases — calling our entire premise about which cases are "known violations" into question.

**Most urgent open question:** Is our 17-case test set really a set of known-violations, or are some of them historically `allow`? The user paused me from checking systematically — that check needs to happen before any further model comparisons are meaningful.

## Background (pre-session state)

A prior Claude Code session had built `Guardian/test-models/` to compare 8 OpenRouter models. It got nonsense results: most models scored badly because the prompts being sent had a literal `{{shield_rule}}` placeholder string instead of the actual rule body. The test-models harness was reading `data_substituted.txt` artifacts from `guardian-logs/`, which only have the *code change* substituted; the rule substitution happened later, in memory, inside `run_shield_file` and was never logged.

The user wanted (a) Guardian fixed to log the *fully*-substituted prompt, (b) the comparison re-run to get a real read on model performance.

**The 17 test cases** were chosen from production guardian-logs as cases that had SPDMX entries:

```
variability, element_type, root_compiling_denizen_env, coerce_kind_to_coord,
get_mutability, substitute_templatas_in_templata, narrow_down_callable_overloads,
custom_destructor, generate_function_body_struct_drop, evaluate_function_for_header_core,
compile_struct_core, assemble_prototype, resolve_static_sized_array, solve_rule,
evaluate, evaluate_expression, compile_runtime_sized_array
```

I selected them by `find <guardian-logs> -name "<case>--*.ScalaParityDuringMigration-SPDMX.data_substituted.txt" | head -1` — picking the FIRST match. **I assumed all 17 were known-deny verdicts.** That assumption is unverified — see "open questions."

## Code Changes Made This Session (uncommitted)

### Guardian (Rust)

1. **`Guardian/ShieldFile/src/lib.rs:942`** — added one line to log the fully-substituted prompt:
   ```rust
   let check_logger = logger.child(check_name);
   check_logger.write_artifact("prompt.txt", data);   // NEW
   let mut session = Session::new(...);
   ```
   Per-vote artifacts now land at `<log_dir>/<...>.vote{0,1,2}.prompt.txt`.

2. **`Guardian/ShieldFile/tests/shield_run.rs`** — added 6 tests covering this:
   - `test_on_diff_writes_prompt_artifact`
   - `test_on_definition_writes_prompt_artifact`
   - `test_on_command_writes_prompt_artifact`
   - `test_prompt_artifact_contains_diff_and_file_path`
   - `test_multi_vote_writes_one_prompt_per_vote`
   - `test_program_mode_shield_writes_no_prompt_artifact`
   - All passing.

3. **`Guardian/src/setup.rs:93-95`** — `allow_exceptions` reads from env var `GUARDIAN_ALLOW_EXCEPTIONS` (defaults to true). Lets us A/B with vs without the per-violation exception-fork mechanism.

4. **`Guardian/CLAUDE.md`** — added explicit notes: `OPENROUTER_API_KEY`, `GUARDIAN_OPENCODE_ROOT`, `GUARDIAN_BUN_PATH` are all required to run the live tests; failures from forgetting them are not flakes.

### Test harness

5. **`Guardian/test-models/src/main.rs`** — went through several iterations:
   - First: kept calling `Session::ask_json` directly with `--shield-file` doing rule substitution.
   - Then: removed `--shield-file` (decided test-models should consume Guardian's new `prompt.txt` artifact).
   - Then: superseded entirely by `run_comparison.py` (below).
   - Current state: still in tree but no longer used by the comparison work.

6. **`Guardian/test-models/run_comparison.py`** — NEW. Subprocess-based python harness that drives `cargo run --bin guardian -- check-direct` over (shield × model × case) combinations. Reads existing hook artifacts (no shield-text substitution needed). Supports:
   - `--shield <path>` (with exceptions)
   - `--shield-no-exc <path>` (sets `GUARDIAN_ALLOW_EXCEPTIONS=false`)
   - `--model <provider-config.json>` (for opencode backend)
   - `--backend {opencode,claude}`
   - `--case <case_name>` (resolves to `<guardian-logs>/.../<case>--*.contextified_diff.txt` + sibling `.referenced_defs.txt` + `hook-N.request.json`'s `tool_input.file_path`)
   - `--votes <N>` (currently no-op; voting is per-shield via `g_votes:` frontmatter)
   - Prints per-test progress, then end-of-run grid + rollup + diffs.

### Shield variants (sandbox copies, prod untouched)

`Guardian/tmp/shields/` (gitignored):

- **`SPDMX-baseline.md`** — verbatim copy of `Luz/shields/ScalaParityDuringMigration-SPDMX.md`.
- **`SPDMX-voting.md`** — baseline + `g_votes: 3` in frontmatter.
- **`SPDMX-voting-clarified.md`** — voting + 3 new DENY examples (value substitution disguised as borrow adaptation, hardcoded constants, helper-call vs inline) appended before the `## Exceptions` section.
- **`SPDMX-voting-narrow-exceptions.md`** — voting + preamble in `## Exceptions` saying "exceptions are narrow… when uncertain, note the uncertainty in the observation and **allow** (`violation: false`)."
- **`SPDMX-voting-uncertain-flag.md`** — voting + preamble saying "exceptions are narrow… when uncertain, note the uncertainty and **flag** (`violation: true`)."

## Findings, organized

### Finding 1: Voting helps modestly, rule-text additions don't

| Variant | Run 1 | Run 2 | Run 3 | Run 4 |
|---|---|---|---|---|
| `baseline` (g_votes:1) | 10/17 | 13/17 | 12/17 | 12/17 |
| `voting` (g_votes:3) | 13/17 | 15/17 | 13/17 | 14/17 |
| `voting-clarified` (3 DENY examples) | 13/17 | 13/17 | — | — |
| `voting-narrow-exceptions` (uncertain→allow) | — | 13/17 | 12/17 | — |
| `voting-uncertain-flag` (uncertain→flag) | — | — | 13/17 | 14/17 |
| `voting-no-exc` (allow_exceptions=false) | — | — | — | 13/17 |
| `voting-uncertain-flag-no-exc` | — | — | — | 14/17 |

**Conclusions:**
- Voting averages ~13.7 vs baseline ~11.7 = ~+2 caught. Real signal but small.
- None of the rule-text additions (clarifications, narrow-exceptions in either direction) showed signal above per-run noise of ±2.
- Disabling the per-violation exception fork (`allow_exceptions=false`) made essentially no difference. Lenience is happening in the **main check**, not in the post-hoc exception-fork mechanism. Per-violation exception fork is silent for this model.

All numbers above are for `inclusionai/ling-2.6-1t` via OpenRouter (`Guardian/provider-configs/ling-2.6-1t.json`).

### Finding 2: Three "stuck" cases each have a different root cause

Cases that resisted every intervention: `substitute_templatas_in_templata`, `variability`, `solve_rule`.

- **`substitute_templatas_in_templata`: filtered out by Guardian, never reaches LLM.** Log shows `Skipping shield SPDMX-voting (when_mentioned not matched)` — but the shield has no `g_when_mentioned`. The actual cause is `g_defs: fn`. Root cause: `detect_def_kind_from_diff` (`Guardian/ShieldFile/src/lib.rs:1134`) iterates all lines and returns the **last** def-kind seen; for this case a context line `   trait IPlaceholderSubstituter` overrides the actual `+fn substitute_templatas_in_templata` change. Returns `Some("trait")`, fails the `g_defs: fn` filter. **Guardian bug.** Fix: prefer added-line kinds over context-line kinds.

- **`variability`: genuinely a coin flip.** Three votes split 2:1 in either direction across runs. Scala reference is just `def variability: VariabilityT` (signature only, no body shown in the diff). Rust fills in one match arm with real logic. Whether this is "Exception P partial migration" (ALLOW) or "novel logic not in Scala" (DENY) is genuinely ambiguous. Likely needs 5-7 votes or richer context (full Scala source). **Possibly the historical verdict was ALLOW, not DENY** — see Finding 4.

- **`solve_rule`: model commits a decomposition fallacy.** The diff has multiple changes: (a) parameter type changes (`&mut`), (b) body replaced with `panic!`, (c) Scala comment block preserved. Each change individually fits one Exception clause: B (lifetimes/borrowing), A (panic placeholder), K (comments). The model checks each independently, gets a cover, and concludes no violation. What it misses: the Scala body had substantial logic; replacing it with `panic!` is "simplified implementation" (forbidden), not "fresh stub" (Exception A's domain). Root cause: model treats exception-application as per-change rather than per-divergence-as-a-whole. Possible fix: add a rule clause "Exception A doesn't cover replacing existing Scala logic with a panic — only fresh stubs."

### Finding 3: Two distinct exception mechanisms in ShieldFile

(Background, not new — but clarifies what the harness is testing.)

1. **In-line exception application** (in the main shield prompt): the shield text including its `## Exceptions A. B. C. ...` section is sent verbatim to the model. Per @OFPLSCZ ("Observations First, Per-Letter Self-Categorization"), the model writes `reason` first then sets `violation` last, allowing it to reason about exceptions mid-thought.

2. **Post-hoc per-violation exception fork** (`exc0`, `exc1`, …): when the main check returns violations and `allow_exceptions=true`, ShieldFile runs a second LLM call per violation using `Guardian/ShieldFile/exception-check-template.txt` to re-categorize as A-X, Y, or Z. A-X dismisses; Z (or unknown) preserves. This is at `lib.rs:952-997`.

Our with/without `GUARDIAN_ALLOW_EXCEPTIONS` comparison showed mechanism #2 is essentially silent for ling — the main check is already setting `violation: false` directly, so there's nothing for the fork to re-categorize.

### Finding 4: Claude backend caught only 6/17 — premise may be broken

Final run: ran the same 17 cases through `--backend claude` (uses local `claude` CLI with model="sonnet" per `Guardian/Rabble/src/backends/claude.rs:147`).

**Result: 6/17 caught**, far worse than ling's baseline 12/17. Cases caught (DENY): `element_type, narrow_down_callable_overloads, compile_struct_core, assemble_prototype, evaluate, compile_runtime_sized_array`.

Inspected variability — Sonnet returned ALLOW with reasoning citing Exception P, similar to ling. Then I checked the historical verdict for that exact variability case in guardian-logs:

```
$ cat /Volumes/V/Sylvan/FrontendRust/guardian-logs/request-131-1778778785941/hook-131/variability--357.0.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md
# Verdict: allow
```

**The historical verdict was ALLOW.** I'd assumed it was DENY because it appeared in my SPDMX-related search.

**This means our entire "caught/17" metric may be misleading.** Sonnet via the local CLI may actually be agreeing with the historical Sonnet on most of these cases. Our "ling caught 12/17" might mean ling caught 12 things, but only some fraction of those were *correct catches* — the rest were *false positives* against historical truth.

**This is the critical thing to verify before any further model comparison work.** Check each of the 17 cases' actual historical verdict (the `*--*.SPDMX.SPDMX.verdict.md` file) and treat that as ground truth. Recompute every model's true positive / false positive / true negative / false negative against that.

(I was halfway through writing a one-liner to do this check when the user paused me to write this handoff. The command would be:)

```bash
for case in variability element_type root_compiling_denizen_env coerce_kind_to_coord get_mutability \
            substitute_templatas_in_templata narrow_down_callable_overloads custom_destructor \
            generate_function_body_struct_drop evaluate_function_for_header_core compile_struct_core \
            assemble_prototype resolve_static_sized_array solve_rule evaluate evaluate_expression \
            compile_runtime_sized_array; do
  f=$(find /Volumes/V/Sylvan/FrontendRust/guardian-logs \
       -name "${case}--*.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md" \
       2>/dev/null | head -1)
  [ -n "$f" ] && printf "%-40s %s\n" "$case" "$(head -1 "$f" | sed 's/# Verdict: //')" \
                || printf "%-40s MISSING\n" "$case"
done
```

(Note the path uses `<case>--*.SPDMX.SPDMX.verdict.md` — double-SPDMX is intentional, that's how Guardian names verdict artifacts.)

**Important caveat:** for cases where MULTIPLE hook dirs have the same `<case>` name (e.g. `variability` may appear in many requests), `find ... | head -1` picks one arbitrarily. Different selections may have different verdicts. Whatever you pick, it must match the same prefix that `run_comparison.py` resolves to via `discover_case`.

### Finding 5: There's a logging bug in Guardian — "when_mentioned not matched" is the wrong message

`Guardian/ContextifiedShield/src/validate.rs:378` logs `Skipping shield {} (when_mentioned not matched)` whenever any of these returns Ok(None):
- when_mentioned filter mismatch
- defs filter mismatch
- (other Ok(None) returns inside `run_shield_file_on_definition`)

This made debugging the substitute_templatas case much harder than necessary. Worth a one-line fix to make the log message reflect the actual filter.

## All experimental runs and where their artifacts live

All commands assume cwd = `/Volumes/V/Sylvan` and rely on `OPENROUTER_API_KEY` being set (via `Guardian/api_key.txt`).

### Run 1 — initial ling 17-case rerun (rule properly substituted, OLD test-models)

```bash
declare -a ARGS=()
for p in variability element_type root_compiling_denizen_env coerce_kind_to_coord get_mutability \
         substitute_templatas_in_templata narrow_down_callable_overloads custom_destructor \
         generate_function_body_struct_drop evaluate_function_for_header_core compile_struct_core \
         assemble_prototype resolve_static_sized_array solve_rule evaluate evaluate_expression \
         compile_runtime_sized_array; do
  f=$(find /Volumes/V/Sylvan/FrontendRust/guardian-logs \
       -name "${p}--*.ScalaParityDuringMigration-SPDMX.data_substituted.txt" 2>/dev/null | head -1)
  ARGS+=("--prompt" "$f")
done
OPENROUTER_API_KEY=$(cat /Volumes/V/Sylvan/Guardian/api_key.txt) \
  /Volumes/V/Sylvan/Guardian/test-models/target/release/test-models \
    "${ARGS[@]}" \
    --shield-file /Volumes/V/Sylvan/Luz/shields/ScalaParityDuringMigration-SPDMX.md \
    /Volumes/V/Sylvan/Guardian/provider-configs/ling-2.6-1t.json \
    > /Volumes/V/Sylvan/tmp/ling-rerun.txt 2>&1
```

Output: `/Volumes/V/Sylvan/tmp/ling-rerun.txt`. Per-prompt model logs lived under a temp dir reported in the log header (was `/var/folders/.../tmpBsC8gd/`).

Result: 11/17 caught violations. (Note `--shield-file` flag was later removed — this command no longer runs against current test-models source. See git history of `Guardian/test-models/src/main.rs`.)

### Run 2 — first 3-variant comparison via `run_comparison.py`

```bash
rm -rf Guardian/tmp/runs/spdmx-experiment && \
  OPENROUTER_API_KEY=$(cat Guardian/api_key.txt) \
  python3 Guardian/test-models/run_comparison.py \
    --shield Guardian/tmp/shields/SPDMX-baseline.md \
    --shield Guardian/tmp/shields/SPDMX-voting.md \
    --shield Guardian/tmp/shields/SPDMX-voting-clarified.md \
    --model Guardian/provider-configs/ling-2.6-1t.json \
    --case-discovery-root /Volumes/V/Sylvan/FrontendRust/guardian-logs \
    --case variability --case element_type --case root_compiling_denizen_env \
    --case coerce_kind_to_coord --case get_mutability \
    --case substitute_templatas_in_templata --case narrow_down_callable_overloads \
    --case custom_destructor --case generate_function_body_struct_drop \
    --case evaluate_function_for_header_core --case compile_struct_core \
    --case assemble_prototype --case resolve_static_sized_array \
    --case solve_rule --case evaluate --case evaluate_expression \
    --case compile_runtime_sized_array \
    --runs-dir Guardian/tmp/runs/spdmx-experiment \
    --output Guardian/tmp/comparison.csv \
    --parallel 8 \
    > /Volumes/V/Sylvan/tmp/comparison-full.txt 2>&1
```

Output: `/Volumes/V/Sylvan/tmp/comparison-full.txt` (overwritten by later runs).
Per-test artifacts: `Guardian/tmp/runs/spdmx-experiment/<shield>/<model>/<case>/{verdict.json, log/, cache/}`.

Result: baseline 10, voting 13, voting-clarified 13.

### Run 3 — added narrow-exceptions variant

Same as Run 2 plus `--shield Guardian/tmp/shields/SPDMX-voting-narrow-exceptions.md`. 4 shields × 17 cases = 68 tests.
Result: baseline 13, voting 15, clarified 13, narrow-exceptions 13.

### Run 4 — uncertain-flag variant added, dropped clarified

```bash
rm -rf Guardian/tmp/runs/spdmx-experiment && \
  OPENROUTER_API_KEY=$(cat Guardian/api_key.txt) \
  python3 Guardian/test-models/run_comparison.py \
    --shield Guardian/tmp/shields/SPDMX-baseline.md \
    --shield Guardian/tmp/shields/SPDMX-voting.md \
    --shield Guardian/tmp/shields/SPDMX-voting-narrow-exceptions.md \
    --shield Guardian/tmp/shields/SPDMX-voting-uncertain-flag.md \
    --model Guardian/provider-configs/ling-2.6-1t.json \
    [--case ... × 17] \
    --runs-dir Guardian/tmp/runs/spdmx-experiment \
    --output Guardian/tmp/comparison.csv --parallel 8 \
    > /Volumes/V/Sylvan/tmp/comparison-full.txt 2>&1
```

Result: baseline 12, voting 13, narrow 12, flag 13.

### Run 5 — with/without exception fork (5 variants)

After adding `GUARDIAN_ALLOW_EXCEPTIONS` env var support and `--shield-no-exc` flag:

```bash
rm -rf Guardian/tmp/runs/spdmx-experiment && \
  OPENROUTER_API_KEY=$(cat Guardian/api_key.txt) \
  python3 Guardian/test-models/run_comparison.py \
    --shield Guardian/tmp/shields/SPDMX-baseline.md \
    --shield Guardian/tmp/shields/SPDMX-voting.md \
    --shield-no-exc Guardian/tmp/shields/SPDMX-voting.md \
    --shield Guardian/tmp/shields/SPDMX-voting-uncertain-flag.md \
    --shield-no-exc Guardian/tmp/shields/SPDMX-voting-uncertain-flag.md \
    --model Guardian/provider-configs/ling-2.6-1t.json \
    [--case ... × 17] \
    --runs-dir Guardian/tmp/runs/spdmx-experiment \
    --output Guardian/tmp/comparison.csv --parallel 8 \
    > /Volumes/V/Sylvan/tmp/comparison-full.txt 2>&1
```

Result: baseline 12, voting 14, voting-no-exc 13, uncertain-flag 14, uncertain-flag-no-exc 14.

### Run 6 — claude backend, baseline shield only (THE SURPRISING ONE)

```bash
rm -rf Guardian/tmp/runs/claude-full && \
  python3 Guardian/test-models/run_comparison.py \
    --backend claude \
    --shield Guardian/tmp/shields/SPDMX-baseline.md \
    [--case ... × 17] \
    --case-discovery-root /Volumes/V/Sylvan/FrontendRust/guardian-logs \
    --runs-dir Guardian/tmp/runs/claude-full \
    --output Guardian/tmp/runs/claude-full/comparison.csv \
    --parallel 4 \
    > /Volumes/V/Sylvan/tmp/comparison-claude-full.txt 2>&1
```

Output: `/Volumes/V/Sylvan/tmp/comparison-claude-full.txt`.
Per-test artifacts: `Guardian/tmp/runs/claude-full/SPDMX-baseline/claude/<case>/`.

Result: 6/17 "caught" (caveat: this is BEFORE we questioned what "caught" actually means).

### Earlier prior-session run (separate session, ling without rule)

Logs at `/private/var/folders/vb/44dv9sg95tb36s390y4pv8g80000gn/T/.tmpsriLYN/log.inclusionai-ling-2-6-1t.<case>.log`. These show ling reasoning about prompts that contained the literal `{{shield_rule}}` placeholder. Useful for confirming the original false-negative mechanism (model returning `{}` because no rule was visible). Not used by current harness.

## Open questions / suspected bugs

1. **CRITICAL: ground-truth verdicts for the 17 test cases.** Run the bash one-liner above to dump each case's historical verdict from its `*.SPDMX.SPDMX.verdict.md`. Then re-classify every model's results against true verdict. The "caught" metric needs to become "true_positive_rate" + "false_positive_rate".

2. **Is `--backend claude` actually invoking the same Sonnet that produced historical verdicts?** Check:
   - `Guardian/Rabble/src/backends/claude.rs:143-147`: tier→model mapping. SimpleMedium → "sonnet". Local `claude --model sonnet` may be Sonnet 4.6 / 4.7 / latest. Historical verdicts predate this session's date (2026-05-15) — they may have been a different snapshot.
   - Compare prompt structure. Open `Guardian/tmp/runs/claude-full/SPDMX-baseline/claude/variability/log/.../log.check-direct.0.SPDMX-baseline.vote0.log` and the corresponding historical log at `/Volumes/V/Sylvan/FrontendRust/guardian-logs/request-131-1778778785941/hook-131/log.variability--357.0.ScalaParityDuringMigration-SPDMX.log`. Diff them.

3. **Guardian bug: `detect_def_kind_from_diff` returns last detected kind.** `Guardian/ShieldFile/src/lib.rs:1134`. For a contextified diff with `+fn foo()` early and a context line `   trait Bar {` later, returns `"trait"` and fails `g_defs: fn`. Fix: prefer added-line kinds; report all detected kinds (not just last); or scan only added (`+`) lines.

4. **Guardian bug: misleading skip-log message.** `Guardian/ContextifiedShield/src/validate.rs:378` logs `(when_mentioned not matched)` for any Ok(None) return, including defs-filter mismatch. Should report the actual reason.

5. **Decomposition fallacy fix for SPDMX rule.** Add to `## Exceptions` clause A (or as a meta-clause): "Exception A and similar placeholder-related exceptions cover replacing a *fresh* panic stub with logic. They do NOT cover replacing existing Scala logic with a panic. If the Scala comment block contains a non-trivial implementation, replacing it with `panic!()` is a simplified-implementation violation, not a panic-stub fill-in."

6. **Add a "checks the whole diff" step.** After listing exception applications, ask the model: "Considered as a whole, do the exceptions you cited cover ALL the divergences between Rust and Scala in this diff? Or are there divergences that none of your cited exceptions cover?" Two-pass prompt structure may help (described earlier in session notes but not implemented).

7. **`run_comparison.py` denial reasons display.** Each denial wraps `violations[]`; current rollup printing reads `denials[].reason` (often empty) instead of `denials[].violations[].reason`. Was fixed mid-session in `progress_print` but verify the CSV row's `denial_count` matches.

## Reproduction checklist

For someone picking this up:

```bash
# 1. Verify build
cd /Volumes/V/Sylvan
cargo build --release --manifest-path ./Guardian/Cargo.toml --bin guardian \
  > ./Guardian/tmp/runs/build.txt 2>&1

# 2. Get historical verdicts (first thing to do — see open question #1)
for case in variability element_type root_compiling_denizen_env coerce_kind_to_coord get_mutability \
            substitute_templatas_in_templata narrow_down_callable_overloads custom_destructor \
            generate_function_body_struct_drop evaluate_function_for_header_core compile_struct_core \
            assemble_prototype resolve_static_sized_array solve_rule evaluate evaluate_expression \
            compile_runtime_sized_array; do
  f=$(find /Volumes/V/Sylvan/FrontendRust/guardian-logs \
       -name "${case}--*.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md" \
       2>/dev/null | head -1)
  [ -n "$f" ] && printf "%-40s %s\n" "$case" "$(head -1 "$f" | sed 's/# Verdict: //')" \
                || printf "%-40s MISSING\n" "$case"
done

# 3. Re-run ling (or pick another model from Guardian/provider-configs/) to get a new sample
OPENROUTER_API_KEY=$(cat Guardian/api_key.txt) \
  python3 Guardian/test-models/run_comparison.py \
    --shield Guardian/tmp/shields/SPDMX-voting.md \
    --model Guardian/provider-configs/ling-2.6-1t.json \
    --case-discovery-root /Volumes/V/Sylvan/FrontendRust/guardian-logs \
    [--case ... × 17] \
    --runs-dir Guardian/tmp/runs/<new-name> \
    --output Guardian/tmp/<new-name>.csv \
    --parallel 8 \
    > /Volumes/V/Sylvan/tmp/<new-name>.txt 2>&1

# 4. Re-run claude
python3 Guardian/test-models/run_comparison.py \
  --backend claude \
  --shield Guardian/tmp/shields/SPDMX-baseline.md \
  [--case ... × 17] \
  --case-discovery-root /Volumes/V/Sylvan/FrontendRust/guardian-logs \
  --runs-dir Guardian/tmp/runs/<name> \
  --output Guardian/tmp/runs/<name>/comparison.csv \
  --parallel 4
```

Inspecting reasoning for any case: `cat <runs-dir>/<shield>/<model>/<case>/log/*/log.check-direct.0.*.vote*.log`.

## Important paths reference

- Guardian: `/Volumes/V/Sylvan/Guardian/`
- Sylvan repo root: `/Volumes/V/Sylvan/`
- API key: `Guardian/api_key.txt` (gitignored)
- SPDMX prod shield (DO NOT EDIT): `Luz/shields/ScalaParityDuringMigration-SPDMX.md`
- Sandbox shield variants: `Guardian/tmp/shields/`
- Historical hook logs: `FrontendRust/guardian-logs/request-*/hook-*/`
- Provider configs: `Guardian/provider-configs/*.json`
- Run output CSVs: `Guardian/tmp/comparison.csv` (latest) or per-run dirs
- Latest claude run logs: `Guardian/tmp/runs/claude-full/`
- Latest ling run logs: `Guardian/tmp/runs/spdmx-experiment/`

## Addendum 2026-05-15: Fresh post-restart test cases

Guardian server was restarted around noon EST 2026-05-15. The original 17 cases
were judged before this branch's changes and have stale prompts. The cases below
were judged by the *current* Sonnet/Guardian pipeline (the historical truth that
`--backend claude` should be able to reproduce).

Cutoff: log dirs with millisecond timestamp > `1778864400000` (noon EST 2026-05-15).
This yielded 496 request dirs containing 66 SPDMX verdicts. Of those, only 7
were DENY (across 5 distinct functions); the rest were ALLOW. No DENYs larger
than 192 contextified-diff lines exist in this window — re-run the discovery
later once more deny verdicts accumulate.

### How they were found

```bash
# 1. Compute the cutoff timestamp (noon EST today, as unix millis):
date -j -f "%Y-%m-%d %H:%M:%S %Z" "2026-05-15 12:00:00 EST" +%s
# → 1778864400 (multiply by 1000 for the millisecond stamps in dir names)

# 2. List request dirs whose trailing millisecond timestamp is past the cutoff:
find /Volumes/V/Sylvan/FrontendRust/guardian-logs -maxdepth 1 -type d -name "*-1778*" \
  | awk -F- '{ts=$NF; if(ts>1778864400000) print}' > /tmp/recent-dirs.txt

# 3. Collect every SPDMX verdict.md inside those dirs:
while read d; do
  find "$d" -name "*.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md" 2>/dev/null
done < /tmp/recent-dirs.txt > /tmp/recent-spdmx-verdicts.txt

# 4. For each verdict, pair it with its contextified_diff line count and verdict:
while read v; do
  verdict=$(head -1 "$v" | sed 's/# Verdict: //')
  diff_file=$(echo "$v" | sed 's/\.ScalaParityDuringMigration-SPDMX\.ScalaParityDuringMigration-SPDMX\.verdict\.md$/.contextified_diff.txt/')
  size=$( [ -f "$diff_file" ] && wc -l < "$diff_file" || echo 0)
  case_name=$(basename "$v" | sed 's/--.*//')
  printf "%s\t%s\t%s\t%s\n" "$verdict" "$size" "$case_name" "$v"
done < /tmp/recent-spdmx-verdicts.txt | sort -t$'\t' -k1,1 -k2,2n
```

The verdict file's sibling files we care about (same basename, different suffix):
- `<case>--<NNN>.<N>.ScalaParityDuringMigration-SPDMX.contextified_diff.txt` — the diff sent to the model
- `<case>--<NNN>.<N>.ScalaParityDuringMigration-SPDMX.referenced_defs.txt` — referenced-defs context
- `<hook-dir>/hook-NNN.request.json` — original tool_input (file_path lives here)

### Chosen cases (DENY: 7, ALLOW: 5) — varied sizes, distinct functions where possible

**DENY (all 7 available — only 5 distinct functions in this window):**

| size | case | verdict path |
|---|---|---|
| 44  | report_when_multiple_types_in_array    | `FrontendRust/guardian-logs/request-192-1778866908010/hook-192/report_when_multiple_types_in_array--3882.0.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md` |
| 45  | report_when_multiple_types_in_array    | `FrontendRust/guardian-logs/request-194-1778866942770/hook-194/report_when_multiple_types_in_array--3882.0.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md` |
| 51  | evaluate_expected_address_expression   | `FrontendRust/guardian-logs/request-023-1778865189567/hook-023/evaluate_expected_address_expression--784.0.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md` |
| 55  | cant_subscript_non_subscriptable_type  | `FrontendRust/guardian-logs/request-149-1778866518834/hook-149/cant_subscript_non_subscriptable_type--3634.0.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md` |
| 56  | evaluate_lookup_for_load               | `FrontendRust/guardian-logs/request-073-1778865895061/hook-073/evaluate_lookup_for_load--202.0.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md` |
| 67  | cant_subscript_non_subscriptable_type  | `FrontendRust/guardian-logs/request-144-1778866464967/hook-144/cant_subscript_non_subscriptable_type--3634.0.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md` |
| 192 | evaluate_addressible_lookup            | `FrontendRust/guardian-logs/request-067-1778865804535/hook-067/evaluate_addressible_lookup--376.0.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md` |

**ALLOW (5, varied sizes, distinct functions):**

| size | case | verdict path |
|---|---|---|
| 21   | variability                            | `FrontendRust/guardian-logs/request-055-1778865506426/hook-055/variability--357.0.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md` |
| 29   | compile_struct                         | `FrontendRust/guardian-logs/request-299-1778868099510/hook-299/compile_struct--439.0.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md` |
| 86   | evaluate_maybe_virtuality              | `FrontendRust/guardian-logs/request-316-1778868219530/hook-316/evaluate_maybe_virtuality--91.0.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md` |
| 229  | get_or_evaluate_function_for_header    | `FrontendRust/guardian-logs/request-338-1778868413725/hook-338/get_or_evaluate_function_for_header--290.0.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md` |
| 1963 | evaluate_expression                    | `FrontendRust/guardian-logs/request-050-1778865405419/hook-050/evaluate_expression--835.0.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md` |

### Caveats

- DENY pool is shallow: 5 distinct functions, max diff size 192. If you want
  large DENYs, either wait for more to accumulate or look at pre-restart cases
  knowing the prompts are stale.
- `report_when_multiple_types_in_array` and `cant_subscript_non_subscriptable_type`
  each appear twice in the DENY list (two different requests / two different diff
  sizes against the same function). That's intentional — they are distinct hook
  invocations with their own contextified diffs, useful for measuring variance.
- The size column is `wc -l` on `*.contextified_diff.txt`, not raw diff size — it
  includes the contextification scaffolding.

---

# Addendum 2026-05-15 (later same day): full re-run on 10 addendum cases + multi-model sweep

This second addendum captures everything done after the first addendum was
written. The goal was to actually run the 12 post-restart addendum cases (now
condensed to 10 unique function names) through every available model, with and
without voting, with and without referenced_defs, and reach a defensible
decision on Sonnet replacement.

**Headline answer**: ling-2.6-flash 1-vote is the only credible cheap-Sonnet
replacement; it matches ling-2.6-1t accuracy at ~4× the speed. But ling's
accuracy ceiling on Result/error-handling adaptations is real and roughly 60-70%
expected after voting noise is removed. See "Voting dynamics" section for why
single-vote numbers overstate accuracy.

## Section 1: Setup notes / harness gotchas discovered

### 1a. `--case <name>` resolves via `rglob | first` — non-deterministic for duplicates

`Guardian/test-models/run_comparison.py:discover_case` does
`root.rglob(f'{case}--*.contextified_diff.txt')[0]`. For cases like `variability`
or `evaluate_expression` that appear in BOTH pre-restart and post-restart
hook dirs, this picks an arbitrary one — whichever filesystem order returns
first. To reliably hit the post-restart addendum cases:

```bash
mkdir -p /Volumes/V/Sylvan/Guardian/tmp/addendum-root
for d in request-192-1778866908010 request-023-1778865189567 request-149-1778866518834 \
         request-073-1778865895061 request-067-1778865804535 \
         request-055-1778865506426 request-299-1778868099510 request-316-1778868219530 \
         request-338-1778868413725 request-050-1778865405419; do
  cp -R /Volumes/V/Sylvan/FrontendRust/guardian-logs/$d /Volumes/V/Sylvan/Guardian/tmp/addendum-root/
done
```

Then pass `--case-discovery-root /Volumes/V/Sylvan/Guardian/tmp/addendum-root`.
`cp -R` (real copy) rather than symlinks because `pathlib.Path.rglob` doesn't
follow symlinks reliably (Python < 3.13). The `tmp/addendum-root/` tree is
gitignored.

### 1b. `--referenced-defs` is passed through the CLI but does NOT reach the prompt unless the shield says so

This burned ~30 min. `run_comparison.py` always passes `--referenced-defs` to
`guardian check-direct`. But `check-direct` only includes them in the final
prompt if the shield's frontmatter has `g_context: definition-with-refs` (not
plain `definition`). SPDMX prod ships with `g_context: definition`. So all our
prior comparison runs were sending the model the diff alone, no referenced
context. Confirmed by inspecting `*.vote0.prompt.txt` artifacts.

To test with referenced defs: copy SPDMX and change one line:

```bash
sed 's/g_context: definition/g_context: definition-with-refs/' \
  /Volumes/V/Sylvan/Guardian/tmp/shields/SPDMX-baseline.md \
  > /Volumes/V/Sylvan/Guardian/tmp/shields/SPDMX-baseline-refs.md
```

The prompt then includes a "Referenced Definitions" section with the
referenced_defs.txt content, prefaced with "Do NOT flag violations in these
definitions." (See Section 4 — counter-intuitively, this **hurts** accuracy.)

### 1c. 5-vote shield variant

`Guardian/tmp/shields/SPDMX-voting5.md` is a copy of SPDMX-voting.md with
`g_votes: 5` for the gpt-oss-20b run. Other 1-vote runs used SPDMX-baseline.md;
3-vote runs used SPDMX-voting.md.

### 1d. Voting aggregation reminder

`Guardian/ShieldFile/src/lib.rs:1030 run_shield_file_with_voting` does
**majority of denying votes** (count of votes with ≥1 violation), and on a
denying majority, the result is the **union** of all denying votes' violations:

```rust
let violations = if deny_count >= (effective + 1) / 2 {
    deny_violations  // union from all denying votes
} else {
    vec![]
};
```

So voting amplifies the model's tendency: if 60% of single shots deny, then
~65% of 3-vote majorities deny; if 80% deny, ~89%. **Voting moves toward the
model's true belief, away from single-shot noise.** Section 5 unpacks why this
matters here.

## Section 2: Ground truth and the 10 cases used

From the first addendum, restricted to one hook dir per unique function
(`discover_case` collapses duplicates). Ground truth is the
`*.ScalaParityDuringMigration-SPDMX.ScalaParityDuringMigration-SPDMX.verdict.md`
that historical Sonnet produced. **Important: see Section 3, case
`evaluate_maybe_virtuality`, for an example of historical truth being lenient
about a real bug that was fixed in a later commit.** Take "ground truth" as a
strong prior, not infallible.

| # | case | truth | hook dir |
|---|---|---|---|
| 1 | report_when_multiple_types_in_array | DENY | request-192-1778866908010/hook-192 |
| 2 | evaluate_expected_address_expression | DENY | request-023-1778865189567/hook-023 |
| 3 | cant_subscript_non_subscriptable_type | DENY | request-149-1778866518834/hook-149 |
| 4 | evaluate_lookup_for_load | DENY | request-073-1778865895061/hook-073 |
| 5 | evaluate_addressible_lookup | DENY | request-067-1778865804535/hook-067 |
| 6 | variability | ALLOW | request-055-1778865506426/hook-055 |
| 7 | compile_struct | ALLOW | request-299-1778868099510/hook-299 |
| 8 | evaluate_maybe_virtuality | ALLOW\* | request-316-1778868219530/hook-316 |
| 9 | get_or_evaluate_function_for_header | ALLOW | request-338-1778868413725/hook-338 |
| 10 | evaluate_expression | ALLOW | request-050-1778865405419/hook-050 |

\*ALLOW per historical Sonnet, but the diff at the time had a real type bug
(bare `Some(...)` where the new return type was `Result<Option<_>, _>`). Fixed
in a subsequent commit. New runs that flag this as DENY are arguably *more*
correct than historical truth.

## Section 3: Manual review of three contested ALLOW cases

The three cases where models disagreed most with historical truth were dug into
by hand. Findings:

### 3a. `evaluate_maybe_virtuality` — historical truth was lenient

Scala throws `RangedInternalErrorT` and `AbstractMethodOutsideOpenInterface` as
exceptions. The Rust diff changes the signature to
`Result<Option<AbstractT>, ICompileErrorT<'s, 't>>` AND replaces the two throw
sites with `panic!`. The "success" arm in the diff was `Some(crate::typing::ast::ast::AbstractT)`
(without `Ok(...)` wrapping). That's a real type-mismatch / unfinished edit.

The historical Sonnet ALLOWed this, presumably treating the whole change as a
single Result-adaptation gestalt (Exception B). The current file on disk
(`function_compiler_middle_layer.rs:120`) reads `Ok(Some(...))` — fixed in a
later commit. Sonnet baseline and ling baseline both correctly flagged this in
our runs. Sonnet 3v reverted to ALLOW.

**Implication for analysis**: when computing accuracy, you can score this case
either way. We computed both: strict ground-truth (Sonnet baseline = 9/10) and
"adjusted for the real bug" (Sonnet baseline = 10/10). Reported numbers below
use strict ground-truth unless noted.

### 3b. `variability` — ling FP confirmed real

Diff fills in one match arm: `ReferenceMemberLookup(e) => e.variability` (was
`panic!(...)`). Scala reference shows only `def variability: VariabilityT`
(abstract method on the trait). Ling flagged this as a violation arguing "Scala
just declares the method, Rust now contains implementation logic not in the
Scala reference."

That's wrong. In Scala, case-class constructor params auto-satisfy abstract
`def`s on the parent trait. The `ReferenceMemberLookupTE` case class has
`variability: VariabilityT` as a constructor param, which IS the override.
Rust's `e.variability` is exact parity. Sonnet got this right; ling didn't —
ling lacked the parent case-class definition in its context (referenced_defs
only included `VariabilityT`/`MutabilityT` enums, not the case class).

This is the case where you'd expect referenced_defs to help. It didn't (see
Section 4) — the prompt's "do not flag violations in these definitions"
preamble seems to push the model toward leniency on the main diff too.

### 3c. `get_or_evaluate_function_for_header` — ling FP confirmed real

One-character change: added `?` after `self.assemble_function_params(...)`
because the callee now returns `Result`. Pure error-propagation. Scala uses
exceptions; Rust uses `?`. Classic Exception B. Ling flagged it as "Rust adding
error handling Scala doesn't have."

Both `variability` and `gofh` are FPs that ling repeats consistently across
runs. They share a structure: **a `?` or Result-conversion at the surface, with
no novel semantic logic underneath.** Ling can't distinguish surface
Result-adaptation from semantic divergence reliably.

## Section 4: All experimental runs (this session)

All runs target the same 10 cases. Logs in `Guardian/tmp/runs/<name>/`.
CSVs in `<name>/comparison.csv`. Stdout in `/Volumes/V/Sylvan/tmp/<name>.txt`.

| Run | Shield (g_votes) | Model | Backend | Run dir | Scoring |
|---|---|---|---|---|---|
| ling-1t 1v | SPDMX-baseline (1) | inclusionai/ling-2.6-1t | opencode | `addendum-ling-base` | 5 TP / 0 FN / 2 TN / 3 FP → 7/10, avg 20.0s |
| ling-1t 3v | SPDMX-voting (3) | inclusionai/ling-2.6-1t | opencode | `addendum-ling-vote` | 3 TP / 2 FN / 1 TN / 4 FP → 4/10, avg 22.1s |
| sonnet 1v | SPDMX-baseline (1) | sonnet (local CLI) | claude | `addendum-sonnet-base` | 5 TP / 0 FN / 4 TN / 1 FP\* → 9/10, avg 38.7s |
| sonnet 3v | SPDMX-voting (3) | sonnet (local CLI) | claude | `addendum-sonnet-vote` | 3 TP / 2 FN / 5 TN / 0 FP → 8/10, avg 71.5s |
| ling-1t 1v +refs | SPDMX-baseline-refs (1) | inclusionai/ling-2.6-1t | opencode | `addendum-ling-base-refs` | 2 TP / 3 FN / 2 TN / 3 FP → 4/10, avg 11.9s |
| sonnet 1v +refs | SPDMX-baseline-refs (1) | sonnet | claude | `addendum-sonnet-base-refs` | 3 TP / 2 FN / 5 TN / 0 FP → 8/10, avg 32.8s |
| 6-model 1v | SPDMX-baseline (1) | deepseek-v3.2, deepseek-v4-flash, devstral-2512, kimi-k2.5, ling-2.6-flash, mimo-v2.5-pro | opencode | `addendum-multi-1v` | See Section 5 |
| gpt-oss-20b 5v | SPDMX-voting5 (5) | openai/gpt-oss-20b | opencode | `addendum-gptoss-5v` | 3 TP / 2 FN / 1 TN / 4 FP → 4/10, avg 46.6s |
| ling-flash 3v | SPDMX-voting (3) | inclusionai/ling-2.6-flash | opencode | `addendum-lingflash-3v` | 4 TP / 1 FN / 1 TN / 4 FP → 5/10, avg 16.1s |

\*Sonnet 1v's "1 FP" is `evaluate_maybe_virtuality` (the lenient-truth case).
If you adjust for that, Sonnet 1v is 10/10.

Example invocation (the others differ by `--shield`, `--model`, `--backend`,
and `--runs-dir`):

```bash
rm -rf /Volumes/V/Sylvan/Guardian/tmp/runs/addendum-ling-base && \
OPENROUTER_API_KEY=$(cat /Volumes/V/Sylvan/Guardian/api_key.txt) \
python3 /Volumes/V/Sylvan/Guardian/test-models/run_comparison.py \
  --shield /Volumes/V/Sylvan/Guardian/tmp/shields/SPDMX-baseline.md \
  --model /Volumes/V/Sylvan/Guardian/provider-configs/ling-2.6-1t.json \
  --case-discovery-root /Volumes/V/Sylvan/Guardian/tmp/addendum-root \
  --case report_when_multiple_types_in_array \
  --case evaluate_expected_address_expression \
  --case cant_subscript_non_subscriptable_type \
  --case evaluate_lookup_for_load \
  --case evaluate_addressible_lookup \
  --case variability \
  --case compile_struct \
  --case evaluate_maybe_virtuality \
  --case get_or_evaluate_function_for_header \
  --case evaluate_expression \
  --runs-dir /Volumes/V/Sylvan/Guardian/tmp/runs/addendum-ling-base \
  --output /Volumes/V/Sylvan/Guardian/tmp/runs/addendum-ling-base/comparison.csv \
  --parallel 8 \
  > /Volumes/V/Sylvan/tmp/addendum-ling-base.txt 2>&1
```

## Section 5: Multi-model 1-vote sweep

All 6 OpenRouter models in `Guardian/provider-configs/` (deepseek-v3.2,
deepseek-v4-flash, devstral-2512, kimi-k2.5, ling-2.6-flash, mimo-v2.5-pro) ran
against the 10 addendum cases at 1 vote with `SPDMX-baseline.md`. Plus
gpt-oss-20b at 5 votes with `SPDMX-voting5.md` (user-specified).

Per-case grid (D=DENY, A=ALLOW; truth row first):

| | rwmtia | eeae | csns | elfl | eal | var | cs | emv | gofh | ee |
|---|---|---|---|---|---|---|---|---|---|---|
| **truth** | **D** | **D** | **D** | **D** | **D** | **A** | **A** | **A\*** | **A** | **A** |
| sonnet 1v | D | D | D | D | D | A | A | D | A | A |
| sonnet 3v | D | D | A | D | A | A | A | A | A | A |
| sonnet 1v +refs | D | D | A | D | A | A | A | A | A | A |
| ling-1t 1v | D | D | D | D | D | D | A | D | D | A |
| ling-1t 3v | D | A | D | D | A | D | A | D | D | D |
| ling-1t 1v +refs | A | A | A | D | D | D | A | D | D | A |
| ling-flash 1v | D | D | D | D | D | D | D | D | A | A |
| ling-flash 3v | A | D | D | D | D | D | D | D | D | A |
| gpt-oss-20b 5v | D | D | A | D | A | D | D | D | A | D |
| deepseek-v3.2 1v | D | A | A | D | A | A | A | D | A | A |
| kimi-k2.5 1v | D | A | A | D | A | A | D | A | A | A |
| deepseek-v4-flash 1v | A | A | A | D | A | A | A | A | A | A |
| devstral-2512 1v | A | A | A | D | A | A | A | A | A | A |
| mimo-v2.5-pro 1v | A | A | A | D | A | A | A | A | A | A |

Accuracy summary:

| Model | Votes | TP | FN | TN | FP | Acc | Avg s/case | Notes |
|---|---|---|---|---|---|---|---|---|
| sonnet | 1 | 5 | 0 | 4 | 1\* | 9/10 | 38.7 | reference; effectively 10/10 if adjusting emv |
| sonnet | 3 | 3 | 2 | 5 | 0 | 8/10 | 71.5 | voting cost 2 TPs |
| ling-2.6-1t | 1 | 5 | 0 | 2 | 3 | 7/10 | 20.0 | over-flags 3 ALLOWs |
| ling-2.6-1t | 3 | 3 | 2 | 1 | 4 | 4/10 | 22.1 | see Section 6 |
| ling-2.6-flash | 1 | 5 | 0 | 2 | 3 | 7/10 | 5.6 | **same accuracy as 1t, ~4× faster** |
| ling-2.6-flash | 3 | 4 | 1 | 1 | 4 | 5/10 | 16.1 | see Section 6 |
| gpt-oss-20b | 5 | 3 | 2 | 1 | 4 | 4/10 | 46.6 | 5 votes did not converge cleanly |
| deepseek/deepseek-v3.2 | 1 | 2 | 3 | 4 | 1 | 6/10 | 17.0 | very lenient |
| moonshotai/kimi-k2.5 | 1 | 2 | 3 | 4 | 1 | 6/10 | 18.2 | very lenient |
| deepseek/deepseek-v4-flash | 1 | 1 | 4 | 5 | 0 | 6/10 | 80.0 | **5 of 10 cases were API timeouts — see Section 5a** |
| mistralai/devstral-2512 | 1 | 1 | 4 | 5 | 0 | 6/10 | 11.9 | model genuinely lenient — talks itself out of every violation |
| xiaomi/mimo-v2.5-pro | 1 | 1 | 4 | 5 | 0 | 6/10 | 5.7 | model genuinely lenient |

### 5a. The "never-flagged-anything" trio: distinguish setup vs model

deepseek-v4-flash, devstral-2512, and mimo-v2.5-pro all scored 1/10. Looked
identical on the surface but they're three different stories:

- **deepseek-v4-flash → setup problem.** 5 of 10 verdict.json files have
  `errs=1, den=0`. Vote logs show 3-retry timeout chain:
  `[LLM] OpenRouter error: Failed to read OpenRouter response body` →
  retry → retry → fail. Cause: model is slow on heavy diffs and exceeds the
  HTTP read timeout, or OpenRouter routing is flaky. The 1 DENY and 4 ALLOWs
  it produced are from the cases that did complete. **Do not interpret its
  6/10 as model behavior.** A fair comparison needs `--parallel 1` and a
  longer client timeout. Was not re-run today.
- **devstral-2512 → real leniency.** 0 errors across all 10. Returns
  well-formed JSON. On `evaluate_expected_address_expression` (real DENY),
  cited "Exception I" (doesn't exist — SPDMX exceptions go A-X) as cover for
  the Result conversion, then declared each piece a "1:1 translation."
  Coherent reasoning, wrong conclusion.
- **mimo-v2.5-pro → real leniency.** 0 errors. On
  `cant_subscript_non_subscriptable_type` (real DENY), declared the Rust test
  "structurally mirrors the Scala logic" — the Rust test has substantial novel
  scaffolding (`Bump::new()`, arena construction, resolver wiring) the Scala
  test doesn't have at all. Failed to see structural divergence even when
  visible at a glance.

deepseek-v4-flash needs a re-run with `--parallel 1` and longer timeout to give
a meaningful number; the other two are just bad at this task.

## Section 6: Voting is revealing, not degrading — per-vote analysis

The biggest surprise this session was that voting (3v, 5v) consistently *hurt*
accuracy compared to 1v across every ling-family model, contradicting the
original Run 2/3/4/5 finding that voting helped ling by ~+2/17. After
inspecting per-vote outputs, this is **not** a bug or noise — it's voting
working correctly and revealing what the model actually believes.

Recall the aggregation: majority of votes that deny → result is union of all
denying votes' violations. With independent samples this drives toward the
model's true deny-rate `p`:
- 1v: caught with probability `p`
- 3v: caught with probability `p² · (3 − 2p)` ≈ `p³ + 3p²(1−p)`
- At `p = 0.5`: 1v → 0.5, 3v → 0.5 (no change)
- At `p = 0.7`: 1v → 0.7, 3v → 0.784
- At `p = 0.3`: 1v → 0.3, 3v → 0.216

So 3-vote PUSHES OUT from 0.5 — toward the model's actual conviction. If the
model is wrong with conviction (`p` high but on a truth-ALLOW case), 3v makes
it more wrong. If the model is borderline-right by luck (`p` ≈ 0.4 on a
truth-DENY case), 3v makes it wronger.

### 6a. Per-vote breakdown of ling-1t 3v (the run that "lost" 2 TPs vs 1v)

Extracted by parsing each `*.vote{0,1,2}.log` for the model's assistant
response and checking whether `observations` contains any `violation: true`:

| case | truth | v0 | v1 | v2 | majority | 1v had | what voting revealed |
|---|---|---|---|---|---|---|---|
| report_when_multiple_types_in_array | D | A | D | D | D ✓ | D ✓ | ~67% deny — voting confirms |
| evaluate_expected_address_expression | D | A | A | A | **A ✗** | D ✓ | model unanimously ALLOWS; 1v got DENY by ~25% tail event |
| cant_subscript_non_subscriptable_type | D | D | D | D | D ✓ | D ✓ | high-conviction DENY |
| evaluate_lookup_for_load | D | D | D | D | D ✓ | D ✓ | high-conviction DENY |
| evaluate_addressible_lookup | D | A | A | A | **A ✗** | D ✓ | model unanimously ALLOWS; 1v noise again |
| variability | A | A | D | D | **D ✗** | D ✗ | ~67% deny (wrong) |
| compile_struct | A | D | A | A | A ✓ | A ✓ | ~33% deny (mostly right) |
| evaluate_maybe_virtuality | A\* | D | D | D | D ✗ | D ✗ | unanimous DENY (probably right per Section 3a) |
| get_or_evaluate_function_for_header | A | D | D | D | D ✗ | D ✗ | unanimous DENY (wrong) |
| evaluate_expression | A | D | A | D | D ✗ | A ✓ | ~67% deny (wrong); 1v got ALLOW by ~33% tail |

The two TPs that "voting lost" (eeae and eal) were 1v noise: the model truly
believes those are ALLOW. Single-vote got 5/5 TPs only because of variance.

### 6b. Per-vote breakdown of ling-flash 3v (same pattern)

| case | truth | v0 | v1 | v2 | majority | model's true belief |
|---|---|---|---|---|---|---|
| report_when_multiple_types_in_array | D | A | A | D | **A ✗** | ~33% deny — model mostly ALLOWS this real DENY |
| evaluate_expected_address_expression | D | D | D | D | D ✓ | conviction DENY |
| cant_subscript_non_subscriptable_type | D | D | D | D | D ✓ | conviction DENY |
| evaluate_lookup_for_load | D | D | D | D | D ✓ | conviction DENY |
| evaluate_addressible_lookup | D | D | D | D | D ✓ | conviction DENY |
| variability | A | D | D | D | D ✗ | unanimous wrong DENY |
| compile_struct | A | D | D | D | D ✗ | unanimous wrong DENY |
| evaluate_maybe_virtuality | A\* | D | D | D | D ✗ | unanimous wrong DENY (probably right per 3a) |
| get_or_evaluate_function_for_header | A | D | A | D | D ✗ | ~67% wrong DENY |
| evaluate_expression | A | A | A | A | A ✓ | unanimous correct ALLOW |

ling-flash's true belief over these 10 cases:
- Confident on 4/5 real DENYs (eeae, csns, elfl, eal): unanimous deny
- Confident on 1 real ALLOW (ee): unanimous allow
- WRONG conviction on `variability`, `compile_struct`, `emv`: unanimous deny
- WRONG conviction on `report_when_multiple_types_in_array`: mostly allow
- Coin flip on `gofh`: 67% deny (wrong)

So the model's *real* expected accuracy is ~5-6 / 10 on this set, not 7/10.
The 7/10 single-vote number captures a lucky run where rwmtia got DENY (33%
event); a re-run would get rwmtia ALLOW more often than not.

### 6c. Why the original 17-case set showed voting helping

Hypothesized but unverified: the 17-case set likely had more cases where the
model's deny-rate sat above 0.5 on a real DENY (`p > 0.5` truth-D), so voting
nudged 0.7 → 0.78, 0.6 → 0.65, accumulating +2 catches. This addendum's
10-case set has more borderline-but-wrong cases — including several real
ALLOWs where the model has high deny conviction — which voting also amplifies
in the wrong direction. Larger sample needed to confirm. A useful next step
would be: rerun the 17 cases with 3-vote, check per-vote deny rates per case.

## Section 7: Referenced defs experiment

Hypothesis: ling's FPs on `variability` and `gofh` were caused by missing
context (parent case-class definitions / callee signatures). Including
referenced_defs should fix at least some of them.

**Result: refs HURT both ling and Sonnet.** See Section 4 table:

| Model | accuracy w/o refs | accuracy w/ refs | delta |
|---|---|---|---|
| ling-1t 1v | 7/10 (5 TP, 3 FP) | 4/10 (2 TP, 3 FP) | lost 3 TPs |
| sonnet 1v | 9/10 (5 TP, 1 FP) | 8/10 (3 TP, 0 FP) | lost 2 TPs |

ling-1t with refs still flagged `variability`, `evaluate_maybe_virtuality`,
and `gofh` as DENY — refs did NOT fix the originally-hypothesized misclassifications.
Both models became uniformly *more lenient* on real DENYs and gained ~0
correctness on FPs.

**Working theory** (unverified, worth investigating): the prompt template for
`definition-with-refs` prepends the refs block with "do NOT flag violations
in these definitions." This framing may be priming the model to view the whole
diff as "context I shouldn't be aggressive about." The leniency leaked from
the refs section into the main diff judgment. A cleaner experiment would be to
include refs WITHOUT the "do not flag" preamble, or word it differently
("these are background symbols; analyze only the main diff" rather than "do
not flag violations").

Prompt template location: search for "Referenced Definitions" or "Do NOT flag"
in `Guardian/ShieldFile/src/lib.rs`.

## Section 8: Detailed failure-mode analysis for ling family

Across single-vote and 3-vote runs, ling's failures cluster around a single
structural pattern: **changes whose visible surface is Result/error-handling
adaptation.**

Type A — real DENYs that LOOK like Result-adaptation but contain novel logic:
- `evaluate_expected_address_expression`: changes parent_ranges argument
  passing AND inserts `?` propagation. Ling sees `?`, applies Exception B,
  misses the argument change.
- `evaluate_addressible_lookup`: wraps in `Ok(...)` AND adds an unrelated
  match arm. Same blind spot.
- `report_when_multiple_types_in_array`: replaces unmigrated test stub with
  a complete novel arena/parser-setup test body. Some ling models miss this
  as "test migration" (Exception A territory) without checking whether the
  body is faithful to the Scala test.

Type B — real ALLOWs that LOOK like Result-adaptation:
- `get_or_evaluate_function_for_header`: literally one added `?`. Pure
  propagation, no novel logic. Ling sees the `?` and classifies as "Rust
  adding error-handling Scala doesn't have."
- `variability`: replaces panic stub with field access on a case-class
  param. Ling lacks the Scala case-class semantics to know fields auto-impl
  abstract defs.

**Both failure modes spring from the same root cause**: the model can spot
"this involves Result/`?`/error-handling" but cannot reliably determine
whether the adaptation is purely surface (allow under Exception B) or hides
semantic divergence (deny). It applies the exception by lexical pattern, not
by semantic analysis.

This is the same "decomposition fallacy" mentioned in the first half of this
document under `solve_rule`, but scoped specifically to Result/error-handling
adaptations rather than general per-change decomposition.

Sonnet does distinguish these cases. On `gofh`, Sonnet correctly noted "the
only change is a `?` propagation for a callee that now returns Result." On
`eeae`, Sonnet noticed the `parent_ranges` argument list was passed
incorrectly. Sonnet is doing the semantic check; ling is not.

## Section 9: Recommendation and what to verify next

### Decision (per user)

Commit to **ling-2.6-flash at 1 vote** as the Sonnet replacement for SPDMX.
Provider config: `Guardian/provider-configs/ling-2.6-flash.json`.

Rationale:
- Matches ling-2.6-1t accuracy at ~4× the speed (5.6s vs 20.0s per case)
- 7/10 on this 10-case set; **expected true accuracy ~5-6/10 once voting
  noise is accounted for** (Section 6)
- Cheapest credible option — every other OpenRouter model either underflagged
  catastrophically or had setup problems
- 3v made it worse, refs made it worse: don't enable those without a
  follow-up study

### Knowns for the researcher

1. **The "voting helps ling" finding in the original handoff is unstable.**
   It held on the 17-case set, broke completely on this 10-case set. Either
   the 17-case truth was unreliable (the original handoff explicitly flags
   this as urgent open question #1) or the 17-case set had a different mix of
   model-conviction distributions. Worth re-running 17 cases at 3v and
   tabulating per-vote deny-rates per case (vs final-majority) to characterize
   the model's distribution shape.
2. **Single-vote accuracy = sample of true belief. Multi-vote accuracy =
   converged true belief.** When you want a stable estimate, vote. When you
   want lucky tail-event catches, don't.
3. **Ling's blind spot is Result/error-handling adaptations.** Both directions
   (false positive AND false negative). Same structure, opposite labels.
   Any prompt-engineering work on the SPDMX shield should target this
   specifically. Candidate intervention: an explicit "test" in the shield
   that asks the model to enumerate every change and label each as
   (surface-adaptation / semantic-divergence) before reaching a verdict.
4. **Referenced defs hurt accuracy when included with the current prompt
   wording.** Either fix the wording or don't include them. Counter-intuitive
   enough to warrant an isolated experiment.
5. **deepseek-v4-flash was never fairly tested** (50% API timeout rate).
   If the researcher wants another data point for the "is there ANY cheap
   reliable model?" question, this is the one to rerun with `--parallel 1`
   and a longer timeout.
6. **Three models are confirmed too lenient regardless of prompt**:
   deepseek-v3.2 (6/10, 2 TP), kimi-k2.5 (6/10, 2 TP), devstral-2512 (6/10,
   1 TP), mimo-v2.5-pro (6/10, 1 TP). These would let real bugs through.
7. **gpt-oss-20b at 5 votes was no better than ling at 3 votes** despite
   more samples. 4/10 with 4 FPs. Not a candidate.

### Suggested next experiments for the researcher

1. **Bigger sample.** This 10-case set is tiny and skewed (5 DENY, 5 ALLOW,
   3 of the 5 ALLOWs are Result-adaptation-shaped — overrepresented vs
   real-world frequency). Once 50+ post-restart DENYs accumulate, re-do this
   with a stratified sample of ~30 DENYs + ~30 ALLOWs.
2. **Per-vote distribution sweep.** For each model × each case, run 10
   single-shot votes and compute the actual deny-probability. Then you can
   tell which cases are "model conviction" vs "model coin flip", which is
   what determines whether voting helps or hurts.
3. **Refs prompt rewording.** Run ling-flash 1v with three refs-prompt
   variants: current ("do not flag violations in these"), neutral
   ("these are background context"), and skeptical ("these are background;
   pay extra attention to the main diff"). See if leniency leak goes away.
4. **Targeted SPDMX rule tightening.** Add an explicit Exception clause:
   "Exception B (lifetime/error-handling adaptation) applies ONLY when the
   change is purely propagation — adding `?`, wrapping success in `Ok(...)`,
   converting `throw` to `Err`. It does NOT cover replacing Scala body logic
   with `panic!()`, nor changes that combine error-handling with novel
   argument or control-flow modifications. Each suspected adaptation must
   be verified line-by-line against the Scala reference; if Rust does *more*
   than the Scala did, it's not an adaptation, it's a divergence."
   Then rerun the 10 cases with ling-flash to see if it changes anything.
5. **Sonnet 1v "vs Sonnet truth" disagreement audit.** Today's Sonnet 1v
   agreed with historical truth on 9/10 cases. The 1 disagreement
   (evaluate_maybe_virtuality) was actually a real bug that historical truth
   missed. So Sonnet-vs-Sonnet agreement is ≥ 90%, possibly 100% adjusted.
   Worth probing whether the local `claude --model sonnet` is really the same
   as the historical Sonnet (`Guardian/Rabble/src/backends/claude.rs:147`
   maps SimpleMedium → "sonnet"; local claude CLI picks whatever the user's
   default is — Opus 4.7 by date; original handoff open question #2).

### Artifact map (for cold-start)

- All run outputs: `Guardian/tmp/runs/addendum-*/`
- All run stdout: `/Volumes/V/Sylvan/tmp/addendum-*.txt`
- Sandbox shields: `Guardian/tmp/shields/SPDMX-{baseline,baseline-refs,voting,voting5}.md`
- 10-case discovery tree: `Guardian/tmp/addendum-root/` (gitignored)
- Inspecting per-vote reasoning for any case: `<runs-dir>/<shield>/<model>/<case>/log/.../log.check-direct.0.*.vote*.log`
- Prompt-as-sent: `<runs-dir>/.../*.vote0.prompt.txt`
- Per-case verdict JSON: `<runs-dir>/<shield>/<model>/<case>/verdict.json`

