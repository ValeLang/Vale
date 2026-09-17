# Documentation Strategy (META)

This document defines how documentation is organized across the Sylvan project. It is the canonical source of truth for documentation structure and conventions.

## Guiding Principles

**Human-readable first.** Everything referenced or mentioned by `CLAUDE.md` must also be discoverable by a human reading the codebase. LLMs navigate documentation the same way humans do — by following links and references — and the project should be accessible to open-source readers who don't have an LLM in the loop.

**Keep Claude's context minimal.** Everything that loads into Claude's context has a cost — it competes with the actual code and conversation for attention. Three mechanisms add to context, from heaviest to lightest:

- **Hard includes** (`@file` in `CLAUDE.md`) — the full doc loads into every session unconditionally. Reserve for the small set of rules that truly must be present in every context.
- **Auto-load rules** (`g_auto_load_when_editing` → `.mdc` files) — the full doc loads whenever Claude edits a matching file. Use narrow globs that target specific files, not `**/*.rs` (which fires on every Rust edit and piles dozens of docs into context at once). Most shields should NOT auto-load.
- **Soft mentions** (`g_mention_in` → SEE ALSO entries) — a one-line description appears in `CLAUDE.md`; Claude reads the full doc only if the task seems relevant. This is the right default for most shields and docs.

When choosing: start with a soft mention. Escalate to auto-load only if Claude repeatedly misses the doc when it should have read it, and only with the narrowest glob that covers the real trigger. Escalate to hard include only for invariants that every session must respect.

## Directory Layout

Every major directory (each compiler pass, the project root) can have a `docs/` subdirectory:

```
Sylvan/
  docs/                          # Project-wide docs
    meta.md                      # This file
  FrontendRust/
    docs/                        # Frontend-wide docs
      shields/
      skills/
    src/
      lexing/docs/
      parsing/docs/
      postparsing/docs/
        shields/
        skills/
      higher_typing/docs/
      solver/docs/
```

Docs live next to the code they describe. Project-wide concerns live in `Sylvan/docs/` or `FrontendRust/docs/`. Pass-specific concerns live in that pass's `docs/` directory.

## Document Categories

Every document belongs to exactly one category. The category determines where the doc lives, how it's discovered, and who its audience is.

For any category, the content lives in either a single file `docs/<category>.md` or, if there are multiple documents, in a subdirectory `docs/<category>/<topic>.md`.

### 1. Background

**Audience:** Anyone reading code in this area.

**Purpose:** General knowledge you need to understand what's going on when you encounter this feature in code. "Things you have to know to read code in this part of the codebase."

**Example:** What the two arenas are, what lifetimes mean, what interning is for.

**Discovery:** Guardian auto-imports all background docs from the current directory and all ancestor directories into the pass's `CLAUDE.md`. This means background knowledge is inherited — project-wide background is available everywhere, and pass-specific background is available within that pass.

**Location:** `docs/background.md` or `docs/background/<topic>.md`

### 2. Usage

**Audience:** Anyone writing code that interacts with this feature.

**Purpose:** How to use the feature correctly. Patterns, APIs, do's and don'ts. "Things you have to know to write code that interacts with this feature."

**Example:** How to intern a new rune type, how to allocate into an arena, how to build and freeze a struct.

**Discovery:** Auto-loaded via a generated `.claude/rules/*.mdc` (from `g_auto_load_when_editing`) when editing matching files.

**Location:** `docs/usage.md` or `docs/usage/<topic>.md`

### 3. Arcana

**Audience:** Anyone debugging or writing code who encounters a non-obvious cross-cutting effect.

**Purpose:** Documents a local thing that has surprising, non-obvious effects elsewhere in the codebase. Each arcana has a unique ID (initialism + Z suffix) and `@ID` references at every affected code site.

The Z suffix is also used for **standalone advisory docs** — reference or pattern docs that describe a concept or procedure but don't have `@ID` backlinks from code sites. The distinction is behavioral: arcana have `@ID` backlinks; advisory docs don't. Advisory docs have no passive discovery mechanism, so they **must** be actively wired: listed via `g_mention_in`, auto-loaded via `g_auto_load_when_editing`, or explicitly linked from another doc's `## See also` section. An unwired advisory doc is invisible.

**Discovery:** `@ID` comments in code point readers to the arcana doc. The doc lives in the `docs/` directory of the feature that *causes* the cross-cutting effect.

**Location:** `docs/arcana/<HammerCaseTitle>-<ID>.md` (e.g., `docs/arcana/PostParserSynthesizesParserASTNodes-PPSPASTNZ.md`)

**Placement:** Arcana and advisory docs that apply across all projects live in `Luz/arcana/`. Only move a doc to `Luz/` if it is genuinely project-agnostic.

**ID convention:** Uppercase initialism of title words, Z suffix.

### 4. Shields

**Audience:** AI agents and reviewers enforcing code quality.

**Purpose:** Enforceable rules and constraints. Each shield has a unique ID (initialism + X suffix).

**Discovery:** Guardian discovers shields by scanning for initialisms in parentheses ending in X. Guardian auto-updates the shield list in the containing directory's `CLAUDE.md` as plain markdown links with descriptions from the shield's frontmatter `description:` field.

**Location:** `docs/shields/<HammerCaseTitle>-<ID>.md` (e.g., `docs/shields/NoExpensiveClones-NECX.md`)

**ID convention:** Uppercase initialism of title words, X suffix.

**Placement:** Shields that apply to a specific feature live in that feature's `docs/shields/`. Shields that apply across all projects live in `Luz/shields/`. Only move a shield to `Luz/` if it is genuinely project-agnostic.

**Every shield must be discoverable via at least one of:**
- **Included** — auto-loaded via `g_auto_load_when_editing` in frontmatter (Claude Code loads it deterministically when editing matching files)
- **Mentioned** — listed via `g_mention_in` in frontmatter (appears in a CLAUDE.md `## SEE ALSO` section), or referenced by an `@ID` comment in an arcana doc
- **Triggered** — listed in `guardian.toml` (either active or explicitly excluded with a comment explaining why)

A shield that satisfies none of these is invisible — neither humans nor Guardian will ever find it.

### 5. Architecture

**Audience:** Anyone modifying the feature's own implementation.

**Purpose:** Internal design, data flow, invariants, and implementation details that a maintainer needs to understand before changing the feature. "Things you have to know to modify this feature's internals."

**Example:** How the two-phase build-then-freeze lifecycle works inside arena allocation, how the interning dedup HashMap interacts with the bump allocator.

**Discovery:** Auto-loaded via a generated `.claude/rules/*.mdc` (from `g_auto_load_when_editing`) when editing the feature's code.

**Location:** `docs/architecture.md` or `docs/architecture/<topic>.md`

### 6. Reasoning (sub-category of Architecture)

**Audience:** Anyone wondering "why is it done this way?" or "where is this heading?"

**Purpose:** Records the alternatives considered and why the current approach was chosen, **and future plans** the code is not yet implementing — target designs, deferred refactors, alternatives held in reserve for post-migration. Software architecture is about evolution, so the place that records *why it looks the way it does today* is also the place that records *where we want it to go*. Lives alongside the architecture it explains, and is always cross-referenced from the relevant Architecture doc so readers discover the future plan while reading about the current design.

**Location:** `docs/reasoning.md` or `docs/reasoning/<topic>.md`

### 7. Skills

**Audience:** AI agents executing specific processes.

**Purpose:** Step-by-step methodology for LLM-driven workflows like migration audits, slice pipelines, or batch parity checks.

**Discovery:** Lives in `docs/skills/<skill-name>.md`. Referenced from `.claude/skills/<skill-name>/SKILL.md` as a symlink (`SKILL.md → ../../../docs/skills/<skill-name>.md`) so Claude Code's skill loader finds it while the source of truth stays in `docs/`.

**Location:** `docs/skills/<skill-name>.md`

### 8. Bugs

**Audience:** Anyone investigating known issues.

**Purpose:** Known bugs and limitations are documented as `#[ignore]`'d tests in the nearest `tests` directory, with explanatory comments describing the bug and expected behavior. Tests *are* the bug tracker.

**Location:** `#[ignore]`'d tests in code, not standalone documents.

### 9. Requirements

**Audience:** Anyone wondering what the system should do.

**Purpose:** Our tests serve as our requirements. They are the source of truth for what the system is expected to do. Other processes will consolidate them for the website; we don't maintain separate requirements documents.

**Location:** Tests in code, not standalone documents.

## Auto-Load Rules and Skill Symlinks

A doc opts into auto-loading by setting `g_auto_load_when_editing` (a list of globs) in its frontmatter. `manifest-sync` then generates a `.claude/rules/*.mdc` that Claude Code loads whenever an edited file matches a glob. These are **generated copies, not symlinks**: each has `globs:`/`description:` frontmatter, an `<!-- AUTO-GENERATED by manifest-sync — do not edit -->` header, and the doc body inlined. Names are the source path flattened with dashes, and globs match project-root-relative paths (so they include the `FrontendRust/` prefix):

```
docs/usage/build-test.md  (g_auto_load_when_editing: [FrontendRust/src/**/*.rs])
  -->  .claude/rules/FrontendRust-docs-usage-build-test.mdc   (generated)
```

Skills (#8) ARE real symlinks: `.claude/skills/<name>/SKILL.md` → `../../../docs/skills/<name>.md`. For a cross-project skill, `docs/skills/<name>.md` is itself a symlink into `Luz/skills/`; manifest-sync's walker skips symlinks, so such skills are mentioned only once.

Shields (#4) are NOT auto-loaded — they appear in `CLAUDE.md` SEE ALSO as plain links via `g_mention_in`.

The source of truth is always the `docs/` file; the generated `.mdc` or symlink exists only for loading.

## CLAUDE.md Auto-Population

Each directory can have a `CLAUDE.md` that Guardian keeps up to date:

- **Background docs (#1):** Auto-imported from current directory and all ancestors. A file in `src/postparsing/` sees project-wide background + postparsing-specific background.
- **Shield lists (#4):** Auto-updated by scanning for X-suffix initialisms in the directory's `docs/shields/`.

## Cross-References Between Categories

Docs link to more specific categories, forming a discovery chain:

- **Background** → links to relevant **Usage** docs
- **Usage** → links to relevant **Arcana** and **Shield** docs
- **Architecture** → links to relevant **Reasoning** and **Skill** docs

Each link is a relative markdown link in a `## See also` section at the bottom of the doc. The good-doc skill maintains these when creating or updating docs.

## Guardian Frontmatter Convention

Docs that participate in Guardian's manifest system use frontmatter fields prefixed with `g_`. Any field that does **not** start with `g_` is silently accepted — it belongs to another system (Claude Code agents, skill files, etc.) and Guardian ignores it. Any unrecognized `g_`-prefixed field is an error.

Known `g_` fields:

| Field | Purpose |
|---|---|
| `g_model` | LLM tier (`SimpleSmall`, `AgenticSmall`, `AgenticSmart`) |
| `g_context` | What Guardian passes to the LLM (`diff`, `definition`, `definition-with-refs`, `command`) |
| `g_program` | Companion Rust program name (for Rust-mode shields) |
| `g_defs` | Definition kinds to filter on (`fn`, `struct`, `enum`, …) |
| `g_when_mentioned` | Pattern expression; shield fires only when matched |
| `g_votes` | Number of LLM votes for majority voting |
| `g_assumes` | Prerequisite shield ID |
| `g_primary` | Authority mode (`rust`, `llm`, `llm_shadowed`) |
| `g_read_when` | "Read when …" sentence; required if `g_mention_in` or `g_auto_load_when_editing` is set |
| `g_mention_in` | CLAUDE.md paths to add a soft SEE ALSO entry |
| `g_auto_load_when_editing` | File globs that trigger auto-loading via `.claude/rules/` |

`g_read_when` must begin with the literal prefix `"Read when"` — manifest-sync enforces this.

### YAML Quoting

YAML interprets certain characters specially. Quote `g_read_when` and `description` values that contain any of:

- `:` followed by a space or end-of-string (e.g. `// VV:`, `crate::`)
- `#` (YAML comment delimiter)
- Leading `*`, `&`, `!`, `|`, `>`, `@`, `%`

When in doubt, wrap the value in double quotes:

```yaml
g_read_when: "Read when adding #[derive(Clone)] or .clone() calls."
description: "Process a // VV: violation comment in Rust code."
```

manifest-sync fails fast on malformed YAML, so a bad frontmatter blocks the entire regeneration pass.

## What Does NOT Get a Document

- **Inventories/catalogs** of structs, functions, or types. These are derivable from code and go stale. If needed during migration, they belong in #5.
- **Anything derivable from `git log` or `git blame`.**
- **Debugging solutions or fix recipes.** The fix is in the code; the commit message has the context.
- **Plans and proposals.** Migration-specific plans (ephemeral, deleted once done) go in #5. Long-term architectural targets the code is converging toward go in #7 (Reasoning) and are cross-referenced from #6 (Architecture).
