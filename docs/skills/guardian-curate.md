---
name: guardian-curate
description: Weekly curation of shield cases. Walk the five cases/need-*/ queues, triage overrides, tune shields, process amendments, retrain trainees, and review implementor feedback.
argument-hint: [optional: path to specific shield family dir]
allowed-tools: Bash(guardian check *), Bash(guardian audit *), Bash(guardian test-shield *), Bash(mv *), Bash(rm *), Bash(ls *), Bash(cargo build *), Bash(cargo test *), Read, Grep, Glob, Edit, Write
---

# Curate Shields

Human-initiated skill for periodic (typically weekly) triage and refinement
of shield cases. Walk through each step with the human, presenting cases and
proposed changes for approval.

Do not use AskUserQuestion in this skill — present cases and proposals as plain text and let the human reply directly.

See `docs/architecture/governance.md` for the separation-of-powers model and
case-flow DAG that this workflow implements.

## Workflow

### Step 1: Triage Overrides

For each shield family directory, check `cases/need-doublecheck-override/`
for cases that haven't been through a review pass.

For each case:
1. Read `NNN.diff` (the contextified diff) and `NNN.context.json` (metadata)
2. Read the shield file itself (e.g. `Luz/shields/ShieldName-CODE/ShieldName-CODE.md`)
   so you understand what rule the shield enforces and what its exceptions are
   before forming an opinion
3. Present the case to the human: show the code, the shield's denial reason,
   and the temp-disable reason
4. Run the appeal-LLM (Opus-tier) to doublecheck the case
4. Route based on appeal result:

**Human says the shield's requirements are wrong** — the shield is
enforcing a rule that shouldn't apply here:
- Move case to `cases/need-shield-amendment/`

**Appeal-LLM sides with implementor** — shield wording is ambiguous or
doesn't cover this pattern well enough:
- Move case to `cases/need-shield-tuning/`
- If the trainee program also denied (disagreed with Opus): file an
  additional copy in `cases/need-trainee-training/`

**Appeal-LLM sides with the shield** — implementor was wrong to
override:
- Move case to `cases/need-implementor-changes/`

After routing a case, remove its `Guardian: temp-disable: ...` comment
from the source file. These live inside `/* ... */` Scala comment blocks.
Use `grep -rn "Guardian: temp-disable:"` to find them. It mentions a log
filename that should match the one we're currently processing.

### Step 2: Tune Shield Prompts

For each shield with cases in `cases/need-shield-tuning/`:
1. Present all cases
2. Cluster cases by error pattern — identify what class of false positive
   each represents
3. Propose shield prompt changes (add clarifications, examples, exceptions)
   to prevent these false positives
4. Present proposed changes to the human for approval
5. Edit the shield file with approved changes

Validate prompt changes by running `guardian test-shield`, which runs
all `tests/cases/` and `cases/need-shield-amendment/` cases for the
shield and reports per-case pass/fail. Use relative paths per repo
convention.

```
cargo run --manifest-path ./Guardian/Cargo.toml --release --bin guardian \
  -- test-shield \
  --shield <path/to/Shield-CODE.md> \
  --config ./FrontendRust/guardian.toml \
  --cache-dir /tmp/guardian-cache \
  --log-level overview \
  > ./tmp/guardian-curate.txt 2>&1
```

A passing run prints `CODE: N/N passed` with per-case results and exits
0. A failure prints which cases failed and exits non-zero.

Report results — which cases now pass, which still fail. Iterate with the
human until satisfied.

If tuning is insufficient (the issue is a rule gap, not an ambiguity),
escalate: move the case to `cases/need-shield-amendment/`.

Once a shield edit lands, any other case in any queue that flags **the
same situation** the edit just addressed is out-of-date — the new
shield prompt would no longer have raised it. For the rest of the
session, discard those cases without re-running the appeal-LLM and
without re-running `guardian test-shield`. Same situation means same
shield code AND the same false-positive class (e.g. a method-to-free-
function conversion of a different function in the same diff, or a
cascading call-site update that exists only because the parent
definition changed). Mention which cases you're discarding to the
human, then `rm` them and strip any matching
`Guardian: temp-disable:` annotations from source. Don't promote stale
cases to `tests/` — the test bank should reflect the current shield,
not a snapshot mid-edit.

### Step 3: Process Shield Amendments

For each shield with cases in `cases/need-shield-amendment/` (from `//f`
annotations and escalated tuning cases):
1. Present the case and the human's annotation
2. Discuss what rule change is needed
3. Human edits the shield rules
4. Validate as in Step 2

### Step 4: Promote to Tests

For each shield with resolved cases from Steps 2 and 3:
1. Cluster cases by code pattern
2. Propose which cases to promote to `tests/` (cap at ~6 examples per
   pattern)
3. Distribute across odd and even case numbers to maintain train/test
   balance for the optimizer
4. Move selected cases: rename and move to `{family_dir}/tests/`
5. Delete remaining resolved cases

### Step 5: Retrain Trainee

For each shield with cases in `cases/need-trainee-training/`:
1. Re-run through shield LLM — if the LLM now agrees with the trainee
   (e.g., prompt was updated in Step 2), delete the case
2. Re-run through trainee program — if it now passes, delete the case
3. If trainee still fails: propose a fix to the Rust program, get human
   approval, implement it
4. Add a unit test to the Rust program's `main.rs` that targets the
   specific logic bug (e.g., wrong regex, missed pattern, incorrect AST
   traversal) — not a full-diff integration test, but a focused test of
   the function/branch that was wrong
5. Run the fixed program through all `tests/` cases and `cargo test` to
   catch regressions
6. Ask the human whether to also promote this case to `tests/`

### Step 6: Review Implementor Cases

For each shield with cases in `cases/need-implementor-changes/`:
1. Present the case to the human: the implementor overrode, Opus sided
   against them
2. The human decides:
   - **Discard** — implementor was just mistaken, no pattern
   - **Note for implementor prompt tuning** — accumulate for implementor
     AI prompt improvement
   - **Override Opus** — if the human disagrees with Opus's reading, move
     the case to `cases/need-shield-amendment/`

### Step 7: Sweep Stale Temp-Disables

Run `grep -rn "temp-disable:" FrontendRust/src/` to find any `Guardian:
temp-disable:` annotations left in source files from previous sessions
that didn't finish cleanup. These live inside `/* ... */` Scala comment
blocks.

For each annotation found, present it to the human with the surrounding
code and Scala reference. Process it the same way as Steps 1–6: evaluate
whether the override was correct, strip the annotation if so, flag for
code changes if not. If the annotation references a pattern now covered
by a shield exception, just strip it.

## Strictness Principle

Be strict. These shields exist because they encode important rules, and
strictness makes them easier for LLMs to follow. A shield with strict
guidelines, strict clarifications, and strict exceptions is far more
effective than one that's loose and hand-wavy. When evaluating overrides,
default toward siding with the shield — the implementor should have a
clear, specific reason why the shield is wrong, not just "it's probably
fine." When tuning shields, add narrow, precise exceptions rather than
broad carve-outs. The goal is rules that are unambiguous enough that an
LLM can follow them mechanically.

Don't trust the reasoning the implementor wrote into a temp-disable — read
it skeptically and check whether it's actually a principled exception or
just an excuse to hack in a violation when our guiding principles would
have called for a more principled approach (refactor the prod code, add
a real exception to the shield, escalate to the architect, etc.).

## Pre-existing Problems Flagged By Shields

If a shield correctly flags something whose problematic shape pre-existed the
current diff (the diff didn't introduce it; it was already there), the
shield is right to fire — that's the system surfacing latent debt, not a
false positive. The proper response is to (1) propose a fix to the prod
code to the human, but do NOT implement the fix unless the human
explicitly approves, and (2) discard the case (and strip its
temp-disable). Don't route to need-shield-tuning, need-shield-amendment,
or need-implementor-changes — the shield wording is correct, the
implementor's local edit was correct, the underlying code is what's
wrong.

## Notes

- Always present cases to the human before moving or deleting them
- When moving cases between directories, renumber to the next available
  case number in the destination
- The `tests/` directory uses odd/even train/test split — distribute
  promoted cases to maintain balance
- The comparison log at `{family_dir}/comparison-log.jsonl` can provide
  aggregate statistics ("trainee disagreed N times this week")
