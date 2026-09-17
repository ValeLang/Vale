# Documentation Reorganization TODO

We are migrating all documentation to the structure defined in `docs/meta.md`. To process a file, use the `good-doc` skill: read it, categorize its content (background / usage / arcana / shields / architecture / reasoning / skills), write each part to the correct `docs/` directory, add `@ID` references at code sites for any arcana, then delete the original.

## Current state (2026-05)

The **infrastructure is built and ahead of this checklist**:

- **`Luz/manifest-sync/`** — Rust tool that parses `g_`-prefixed doc frontmatter and regenerates `CLAUDE.md` `## SEE ALSO` soft-mention blocks, `.claude/rules/` auto-load symlinks, and `.claude/skills/` symlinks. This is the realized form of what was once planned as "Guardian docs rebuild/check/list" (the old `Guardian/doc-design-doc.md` is retired).
- **`docs/meta.md`** — matured into the full spec: context-cost principles, the `g_` frontmatter table, `Luz/arcana/` placement, cross-reference chains.
- **Skills (#8)** — source of truth in `docs/skills/` (19 docs), surfaced via `.claude/skills/<name>/SKILL.md` symlinks. The `/document` skill became `good-doc`.
- **`Luz/`** — reorganized: `Luz/zen/ → Luz/arcana/`; 47 shields + 7 arcana + skills + manifest-sync + hooks.
- **`FrontendRust/docs/`** — populated: 6 arcana, architecture/background/reasoning docs, 4 Sylvan-specific shields.
- **`FrontendRust/zen/`** — **dissolved** (this pass): shield dupes deleted, `PPSPASTNZ` → `src/postparsing/docs/arcana/`, dead `NOT WORKING` python parity scripts + 77MB stale `logs/` removed.

Remaining work is the **cleanup tail** below.

---

## Remaining work

### `.claude/rules/` — DONE

Converted all 12 hand-written `.mdc`/`.md` rules into `docs/` docs with `g_read_when` + `g_auto_load_when_editing` frontmatter; `manifest-sync` now generates the `.claude/rules/*.mdc` (flat dashed names, project-root-relative globs). Homes: build-test, interning-patterns, style-guide → `FrontendRust/docs/usage/`; solver/parser/postparser mappings, guidelines, organization, IDEPFL interning, differences → the respective `src/<pass>/docs/architecture/`.

**Follow-up:** `usage/interning-patterns.md` overlaps `architecture/interning-dual-enum.md` (IDEPFL) and the arena arcana — dedup in a later curation pass.

### FrontendRust loose files

- [ ] `FrontendRust/todo.md` (kept per request)
- [x] `FrontendRust/cursor_setup.md` — deleted
- [ ] `FrontendRust/thoughts.md` — design scratchpad (Coord/region/ownership musings); decide reasoning-doc vs leave
- [ ] `FrontendRust/manual/building.md` — 2-line build/test command ref; fold into `docs/usage/build-test.md` or leave
- [ ] `FrontendRust/src/gripes.md` (leave per request)

### Skills — DONE

All `.claude/skills/*/SKILL.md` are now symlinks to `docs/skills/` sources. Fixed this pass: `collapsed-call-tree` (was direct-to-Luz; now `docs/skills/collapsed-call-tree.md` → Luz symlink); `good-testing`/`tdd`/`feature-development-flow` (were verbatim copies; now symlinks into Luz, avoiding SEE ALSO double-mention). `repro-reduce-tdd-drive` gained its `.claude/skills` entry.

### CLAUDE.md content audit (Phase 9)

- [ ] Audit every `CLAUDE.md` for inlined content that belongs in a categorized doc. Root `./CLAUDE.md` still inlines operational guidance (Bulk Sed Protocol, Build & Run Convention, lifetime model) — decide keep vs. move-to-doc + `@`-reference.

### Guardian config decision

- [ ] `guardian.toml` still uses `include_shields` (3 mode sections) + coverage check. The earlier plan called for removing it in favor of auto-discovery from `shields_dirs`. Confirm whether to keep explicit lists (current) or move to auto-discovery, and record the decision.

### Sylvan project-level docs (lower priority — Vale language docs, not migration)

- [ ] Triage the loose Vale design docs in `docs/` (`Architecture.md`, `Environments.md`, `Generics.md`, `Catalyst.md`, `Allocators.md`, etc.) and the `docs/old/`, `docs/notes/`, `docs/refactor-thoughts/`, `docs/historical/` directories.
- [ ] Top-level: `README.md` (stays), `architectural-direction.md`, `build-compiler.md`, `compiler-overview.md`.

### Luz shields triage (mostly settled)

- [ ] Spot-check that remaining `Luz/shields/` are genuinely project-agnostic; the Sylvan/arena-specific ones already moved to `FrontendRust/docs/shields/` (AASSNCMCX, ATDCX, TFITCX).
