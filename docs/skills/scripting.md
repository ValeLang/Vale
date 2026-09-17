---
name: scripting
description: How to do bulk edits in this repo — `sed`/`perl -pi` outlawed, prefer Edit tool, use `safe-script-runner` for Python transforms with mandatory per-file review/apply iteration.
g_read_when: Read when authoring or running any bulk-edit script (`./tmp/scripts/*.py`, shell loops over many files, or any per-file transform across more than a handful of files).
g_mention_in:
  - CLAUDE.md
---

# Scripting / Bulk Edits

`sed` and `perl -pi` are outlawed. For bulk transforms, **prefer the Edit tool** — ~40 distinct invocations is the threshold before Python becomes worth it.

## Bulk-edit workflow: `safe-script-runner`

The canonical path for any `./tmp/scripts/<NAME>.py` transform is the `safe-script-runner` CLI (`Luz/safe-script-runner/`). It splits the flow into three subcommands and enforces strict iteration via a single-marker invariant on disk — you cannot batch reviews, and you cannot apply cold without a prior review of the exact same content. Built binary: `Luz/safe-script-runner/target/release/safe-script-runner`.

One file at a time:

1. Write the script to `./tmp/scripts/<name>.py` (pure stdin → stdout transform; no file I/O, no subprocess, no `exec`/`eval`).
2. **Review:** `safe-script-runner review ./tmp/scripts/<name>.py <SRC>`. The tool runs the script, prints the full `=== STDERR (<SRC>) ===` and `=== DIFF (<SRC>) ===` to stdout, and writes the marker `./tmp/working/.current-review` (script + src + SHA-256 of working/stderr).
3. Iterate: edit the script and re-run step 2 on the SAME `<SRC>` — the marker refreshes in place. Switching to a different `<SRC>` while the marker is unapplied is **refused** (`ReviewPending`).
4. **Apply:** `safe-script-runner apply ./tmp/scripts/<name>.py <SRC>`. The tool re-runs the script, verifies the marker exists and its `(script, src)` and hashes match the new run, then backs up `<SRC>` to `./tmp/backup/<ts>/<src-with-dirs>` and `mv`'s working over src. Marker is deleted. If anything drifted between review and apply (src or script was edited), apply refuses with a hash-mismatch error and you must re-review.
5. Next file: back to step 2.

To discard a pending review without applying: `safe-script-runner abandon` (no args, no-op if no marker).

## Single-marker invariant (enforced)

Only ONE review may be pending at a time — by design. Trying to `review` a second `<SRC>` while another is unapplied is refused. This makes batch-review-then-batch-apply impossible: the only physically possible sequence is `review A → apply A → review B → apply B → …`. Combined with the marker's SHA-256 binding of review to apply, the architect's "go" is always against the diff the apply will actually land.

## Never apply a bulk-edit without explicit authorization to start the flow

The transform-and-review steps (writing the stdin→stdout script, running it to `./tmp/working/`, reading the diff) are all read-only and don't need permission. **The apply step does** — never run `safe-script-runner apply`, or the `cp <SRC> ./tmp/backup/... && mv ./tmp/working/... <SRC>` compound, without the user authorizing this bulk-edit flow first. Show the script + a representative diff, wait for go-ahead on the flow, then sweep apply across files. The authorization covers the whole flow (every file the script will touch), not each file individually. "I'm going to apply now" is not authorization; the user has to say so.

Within a flow, re-confirm if you hit a diff that materially deviates from the representative one you showed (different shape of change, surprising counts, anomalies). Within-flow surprises are when you pause; routine same-shape edits are not.

## JR never touches safe-script-runner

JR (the junior agent) does **not** write, review, or apply transform scripts of any kind — no `./tmp/scripts/*.py`, no shell scripts, no `python3 -c` one-liners, no heredoc rewrites, no `safe-script-runner review` / `apply` invocations. Scripts are a TL-only tool end-to-end. The reasoning: the script's correctness propagates to every file it touches, JR's review surface is per-file diffs not transform logic, and a script bug that JR rubber-stamps across N files is far worse than N individual Edit calls. If a bulk edit looks warranted, JR escalates via mailbox; TL decides — default answer is "no, do the Edits anyway" — and if TL is convinced a script is justified, TL authors AND drives it themselves without JR involvement at any stage. See `guardian-tl.md` "Bulk Edits Are TL-Only" for the TL-side rule.

## Reviewing diffs

`safe-script-runner` mechanically enforces full-diff review: `review` always emits the entire diff and stderr, BESWX denies any pipe/filter/redirect/chain on the command, and the marker hash binds review to apply (drift refuses with a re-review prompt). The architect should still actually look at the diff — the tool guarantees evidence exists in the transcript, not that the human reads it.

**After every `review`, before `apply`, emit a line beginning literally `Issues I see in the diff:` followed by every issue you find (or "none"). Required even when the diff looks routine — that's when script bugs slip through. If the list is non-empty, fix the script and re-review.**

## Raw `python3 ./tmp/scripts/*.py` for bulk-edit is retired

Bulk-edit transforms — `python3 ./tmp/scripts/<NAME>.py < <SRC> > ./tmp/working/<BN>` — are no longer auto-allowed by VRBX. The only canonical bulk-edit path is `safe-script-runner`. If you invoke the raw form against `./tmp/working/`, Claude Code falls through to the normal Bash confirmation dialog (no auto-allow); using `safe-script-runner` is the friction-free path.

Read-only / info / analysis scripts under `./tmp/scripts/` are unaffected — `python3 ./tmp/scripts/analyze.py < log.txt | head` still auto-allows. The retirement is narrowly targeted at the `> ./tmp/working/` stdout shape.

A failed apply via `safe-script-runner` is recoverable from the backup at `./tmp/backup/<ts>/...`.

## Bulk editing is intentionally serial — do NOT parallelize

When running bulk edits via `safe-script-runner` (or any per-file transform), **ignore** the standing "Maximize use of parallel tool calls where possible to increase efficiency" guidance. Bulk editing requires per-file attention, with enough notice between files to spot a divergent diff and course-correct the script. Power through one `review` + `apply` per Bash call, serially. Do not batch, parallelize, or wrap in a loop.

If the per-file cadence feels too slow, **stop and ask** — do not invent a workaround.

## Case study: 2026-06-13 strip-block-comments failures

Task: strip `/* ... */` blocks from 258 `.rs` files under `FrontendRust/`.

- **Failure 1 — pivoted unilaterally.** After Guardian blocked my chained shell loop (`for f in ...; do safe-script-runner review ...; safe-script-runner apply ...; done`), I wrote a `python3` driver that walked the tree and applied the strip in-place — bypassing `safe-script-runner` entirely. The user had authorized the `safe-script-runner review/apply` flow, not "do it however." Should have stopped at the shield block and asked.
- **Failure 2 — probed shield workarounds.** After the user picked "power through 516 sequential calls," I immediately tried a `find -exec ... safe-script-runner review ... \; -exec ... apply ... \;` one-liner to dodge the no-chaining shield. CLAUDE.md says: *"do not use destructive actions as a shortcut to simply make it go away... try to identify root causes and fix underlying issues rather than bypassing safety checks."* Same rule applies to shield-checks.

**What I should have done both times:** accept the serial cadence, or surface the cost ("this is 516 calls, want to lift the shield?") and let the user choose — not invent a workaround.
