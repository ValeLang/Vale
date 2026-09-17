# Vale

**Never commit unless the architect says the literal phrase "fire commit" — no other phrasing ("just commit", "go ahead", "ship it", etc.) authorizes a commit.**

This is the Vale compiler. The `FrontendRust/` tree is a Rust compiler frontend.

## Build & Test

Always run **`cargo build --lib`** after making changes. The project builds as a library. Use **`cargo check`** for faster iteration.

Eliminate all compiler warnings (unused imports, unused variables, dead code) before saying you're done. Variables prefixed with `_` are intentionally unused and don't count.

## Agent Rules

**Never use spawned agents (the Agent tool) to make code modifications.** All edits must be made directly by the main conversation using Read/Edit/Write tools. Spawned agents may only be used for **read-only tasks**: searching, exploring, analyzing, reading files, running read-only commands. The only exception is agents defined in `.claude/agents/` which are explicitly human-written and approved for modifications.

**When spawning any agent**, the prompt must include clear instructions that the agent **must not modify any files in this project** — only the main conversation and the human are allowed to do that. Agents are free to create and read/write temporary files in `/tmp` for their own use.

## Bulk Edits

See the `scripting` skill (`docs/skills/scripting.md`) for the full bulk-edit / `safe-script-runner` protocol. Quick rule: `sed`/`perl -pi` outlawed, prefer the Edit tool up to ~40 invocations, `safe-script-runner` for Python transforms beyond that, never parallelize bulk edits.

## Build & Run Convention

Always pipe `cargo run`, `cargo test`, `cargo build`, `cargo check`, and all `sbt` output into a fixed file in `./tmp/` (use the same file for the entire session/project, e.g. `./tmp/refactor-project.txt`). Come up with a name instead of refactor-project.txt, and then use the same file for the rest of the session.

**Never chain a heavy command with `| tail`, `| head`, `| grep`.** Run the build/test with `>` as one command (redirecting fully to the file) and the inspection as a separate follow-up command. Chaining defeats the purpose: you lose the ability to re-analyze a different part of the output without re-running the expensive build.

DO have them in separate commands:

```bash
cargo run --bin benchmark -- --model openai/gpt-oss-20b > ./tmp/fixing-bug-1047-quest.txt 2>&1
tail -20 ./tmp/fixing-bug-1047-quest.txt
# Later, to see a different part:
head -40 ./tmp/fixing-bug-1047-quest.txt
grep "error" ./tmp/fixing-bug-1047-quest.txt
```

```bash
sbt 'testOnly dev.vale.AfterRegionsIntegrationTests' > ./tmp/fixing-borrowing-test.txt 2>&1
grep "SUCCESS" ./tmp/fixing-borrowing-test.txt
# Later, to see a different part:
tail -30 ./tmp/fixing-borrowing-test.txt
# Later, do some changes to the code, and then same command into same file:
sbt 'testOnly dev.vale.AfterRegionsIntegrationTests' > ./tmp/fixing-borrowing-test.txt 2>&1
grep "SUCCESS" ./tmp/fixing-borrowing-test.txt
```

DON'T chain them together like this:

```bash
# This is bad:
cargo build --lib > ./tmp/build4.txt && grep -B2 "i_env_entry" ./tmp/build4.txt | grep "src/" | head -20
```

Instead, they must be separate entire commands.

DON'T use a different file for each build like this:

```bash
sbt 'testOnly dev.vale.AfterRegionsIntegrationTests' > ./tmp/borrowing-build1.txt 2>&1
grep "SUCCESS" ./tmp/borrowing-build1.txt
# BAD: Don't use a different file
sbt 'testOnly dev.vale.AfterRegionsIntegrationTests' > ./tmp/borrowing-build2.txt 2>&1
grep "SUCCESS" ./tmp/borrowing-build2.txt
```

Instead, use the same file.

## SEE ALSO (auto)

- **Read when planning or making a large change to the typing pass (FrontendRust/src/typing/).** → docs/architecture/typing-pass-ai-guide.md
- **Read when an external real-world program surfaces a compiler bug and you need to reduce it to a minimal in-tree repro before fixing.** → docs/skills/bug-investigation.md
- **Read when investigating a compiler bug by tracing execution with debug printouts and narrowing the call graph.** → docs/skills/collapsed-call-tree.md
- **Read when starting a new feature, to follow the gated discuss/plan/stub/test/implement sequence.** → docs/skills/feature-development-flow.md
- **Read when the architect says the literal phrase "fire commit" (or you're about to commit + sync as a TL).** → docs/skills/fire-commit.md
- **Read when the architect says the literal phrase "fire rebase".** → docs/skills/fire-rebase.md
- **Read when reviewing or critiquing a plan for testing correctness before implementation.** → docs/skills/good-testing.md
- **Read when a Guardian shield just fired or failed at hook time and you need to diagnose it.** → docs/skills/guardian-diagnose.md
- **Read when acting as JR in a Guardian-gated loop.** → docs/skills/guardian-jr.md
- **Read when a human tells you to ordain yourself or gives you the Guardian ordain password.** → docs/skills/guardian-ordain.md
- **Read when promoting an LLM-mode shield to Rust mode with a deterministic companion program.** → docs/skills/guardian-rustify.md
- **Read when acting as or setting up the architect, TL, or JR in a Guardian-gated loop.** → docs/skills/guardian-tl.md
- **Read when the architect asks what `experimental` has that `master` might want, or asks to pull specific commits from `experimental`.** → docs/skills/merging-from-experimental.md
- **Read when authoring or running any bulk-edit script (`./tmp/scripts/*.py`, shell loops over many files, or any per-file transform across more than a handful of files).** → docs/skills/scripting.md
- **Read when writing a plan that includes implementation work — every such plan needs an RFIGA list, defined here.** → docs/skills/tdd.md
