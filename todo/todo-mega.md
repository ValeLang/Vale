# Mega Master Plan: Documentation Reorganization

## Context

Today we defined a comprehensive documentation strategy for the Sylvan project. The codebase has ~100+ markdown files scattered across `FrontendRust/zen/`, `FrontendRust/docs/`, `FrontendRust/todo/`, `.claude/rules/`, `Luz/shields/`, and various `src/*/docs/` directories. Many overlap heavily (especially the 5 arena docs), some are stale, and there's no consistent structure.

We created four foundational documents:
- **`docs/meta.md`** — Canonical documentation strategy (10 categories, directory layout, conventions)
- **`docs/skills/document.md`** — Skill for categorizing and placing docs
- **`docs/todo.md`** — Checklist of every existing markdown file to reorganize
- **`Guardian/doc-design-doc.md`** — Requirements for Guardian's documentation support features

This plan covers everything needed to get from current state to the vision.

## Key Decisions (made today, non-negotiable)

### 10 Document Categories
1. **Background** — "things you need to know to read code here" (consumer perspective)
2. **Usage** — "how to interact with this feature when writing code"
3. **Arcana** — cross-cutting concerns with @ID references (Z suffix IDs), live in the feature that *causes* the effect
4. **Shields** — enforceable rules (X suffix IDs), organized by topic/directory
5. **Migration** — ephemeral living docs, shrink as migration completes; plans/proposals are migration-specific
6. **Architecture** — "things you need to know to modify this feature's internals" (maintainer perspective)
7. **Reasoning** — sub-category of architecture; "why we chose X over Y"
8. **Skills** — AI workflow methodology
9. **Bugs** — `#[ignore]`'d tests, not standalone documents
10. **Requirements** — tests ARE requirements, no separate docs

### Directory & File Conventions
- Every major directory gets a `docs/` subdirectory
- Single file: `docs/<category>.md`; multiple files: `docs/<category>/<topic>.md`
- `FrontendRust/zen/` becomes `FrontendRust/docs/`
- Shields: `docs/shields/<HammerCaseTitle>-<ID>.md` with frontmatter `description:` field
- Arcana: `docs/arcana/<HammerCaseTitle>-<ID>.md`

### CLAUDE.md
- Background section uses `@` references (not inlined content)
- Shield section uses plain markdown links with descriptions from frontmatter (NOT `@`, not auto-included)
- Guardian owns delimited sections (`<!-- Guardian:background:begin -->` / `<!-- Guardian:shields:begin -->`)

### Symlinks
- Only **usage** (#2) and **architecture** (#6) get `.mdc` symlinks into `.claude/rules/`
- Shields do NOT get symlinks (listed in CLAUDE.md instead)
- Symlink paths mirror directory structure: `.claude/rules/postparser/usage/interning.mdc`

### Shield Placement
- Sylvan-specific shields live in feature `docs/shields/` directories
- Only genuinely project-agnostic shields stay in `Luz/shields/`
- `include_shields` in guardian.toml is removed; auto-discovery only, with optional `exclude_shields`

### Guardian Features (from doc-design-doc.md)
- Auto-discover shields from `docs/shields/` directories + `Luz/shields/`
- Scope-aware shield evaluation (shield applies to its subtree)
- CLAUDE.md auto-population (background @refs + shield lists)
- Symlink management for usage + architecture docs
- Arcana validation: structural (orphan/dangling checks) + semantic (Rabble-launched Claude checks changed code for missing @ID references)
- Doc linting (category placement, orphaned docs, duplicate IDs)
- Three new commands: `guardian docs rebuild`, `guardian docs check`, `guardian docs list`

---

## Phase 1: Directory Structure (Day 1, ~30 min)

Create the target directory tree. No content moves yet.

### 1.1 Create FrontendRust docs directories
```
FrontendRust/docs/background/
FrontendRust/docs/usage/
FrontendRust/docs/arcana/
FrontendRust/docs/shields/
FrontendRust/docs/architecture/
FrontendRust/docs/reasoning/
FrontendRust/docs/skills/
```

### 1.2 Create per-pass docs directories
For each of: `lexing`, `parsing`, `postparsing`, `higher_typing`, `solver`, `instantiating`, `pass_manager`
```
FrontendRust/src/<pass>/docs/
FrontendRust/src/<pass>/docs/background/
FrontendRust/src/<pass>/docs/usage/
FrontendRust/src/<pass>/docs/arcana/
FrontendRust/src/<pass>/docs/shields/
FrontendRust/src/<pass>/docs/architecture/
FrontendRust/src/<pass>/docs/reasoning/
```
(Not all subdirs needed for all passes — create on demand as docs are moved)

### 1.3 Create .claude/rules mirror structure
```
.claude/rules/postparser/usage/
.claude/rules/postparser/architecture/
.claude/rules/parser/usage/
.claude/rules/parser/architecture/
... etc
```

---

## Phase 2: Dedup the Arena Cluster (Day 1-2, ~2-3 hours)

These 5 docs heavily overlap (confirmed by 16 agents we ran today). Consolidate before moving.

### Source files
- `FrontendRust/docs/arena-lifetimes.md` — the two arenas, what lives in each, data flow
- `FrontendRust/docs/arena-two-phase-lifecycle.md` — build-mutable-then-freeze pattern
- `FrontendRust/docs/arena-allocated-structs.md` — inventory of arena vs heap structs
- `FrontendRust/docs/per-pass-arenas-migration.md` — plan to eliminate `'a`, per-pass design
- `FrontendRust/zen/ArenaAndMallocSeparation.md` — principle: no malloc in arena structs

### Also related
- `.claude/rules/early-lifetimes.mdc` — restates arena-lifetimes content
- `.claude/rules/general/interning-patterns.mdc` — interning tied to arena model
- `FrontendRust/docs/location-in-denizen.md` — exemplifies arena-generic `'x` pattern
- `FrontendRust/todo/arena-deterministic-map-problem.md` — unsolved problem within cluster

### Target structure
```
FrontendRust/docs/background/arenas.md          — (#1) What the arenas are, lifetimes, what lives where
FrontendRust/docs/usage/arenas.md                — (#2) How to allocate, build-then-freeze, conversions
FrontendRust/docs/architecture/arenas.md         — (#6) Internal design, interning machinery, two-phase lifecycle details
FrontendRust/docs/shields/ArenaAllocatedStructsShouldNotContainMallocdCollections-AASSNCMCX.md  — (#4) from zen/ArenaAndMallocSeparation.md
FrontendRust/docs/reasoning/arena-deterministic-maps.md  — (#7) why we need deterministic arena maps, 5 approaches considered
```

### Process
1. Read all 5 source docs + related files side by side
2. Extract background content → `background/arenas.md`
3. Extract usage patterns → `usage/arenas.md`
4. Extract implementation details → `architecture/arenas.md`
5. Move ArenaAndMallocSeparation to shields (add frontmatter `description:`)
6. Convert arena-deterministic-map-problem to reasoning doc
8. Move location-in-denizen.md — likely `architecture/location-in-denizen.md` or an arcana if it's cross-cutting enough
9. Delete the 5 original files
10. Update `early-lifetimes.mdc` and `interning-patterns.mdc` to reference the new docs instead of restating content

---

## Phase 3: Sort FrontendRust/zen/ (Day 2, ~2-3 hours)

`zen/` is being dissolved into `FrontendRust/docs/`. Process each file per the `/document` skill.

### Shields (move to feature docs/shields/ or keep in Luz/)
- `zen/CloserToScalaNotFurther.md` → already in `Luz/shields/` as CSTNFX. Delete the zen/ copy.
- `zen/NoAddingScalaComments.md` → already in `Luz/shields/` as NAOCSCX. Delete.
- `zen/NoChangesWithoutScalaReference.md` → already in `Luz/shields/` as NCWSRX. Delete.
- `zen/NoMovedDefinitions.md` → already in `Luz/shields/` as NMDX. Delete.
- `zen/NoNewDefinitions.md` → already in `Luz/shields/` as NNDX. Delete.
- `zen/NoNovelCodeDuringMigrations.md` → already in `Luz/shields/` as NNCDMX. Delete.
- `zen/NoRenamedDefinitions.md` → already in `Luz/shields/` as NRDX. Delete.
- `zen/zen.md` → Contains EAW (already `Luz/shields/EliminateAllWarnings-EAWX.md`) and IID (already `Luz/shields/ImmediateInterningDiscipline-IIDX.md`). Delete after verifying Luz copies are complete.

### Arcana (move to feature docs/arcana/)
- `zen/PostParserSynthesizesParserASTNodes-PPSPASTNZ.md` → `FrontendRust/src/postparsing/docs/arcana/PostParserSynthesizesParserASTNodes-PPSPASTNZ.md`

### Architecture
- `zen/typing-pass-design.md` → `FrontendRust/src/higher_typing/docs/architecture/typing-pass.md`

### After all moves, delete `FrontendRust/zen/` directory

---

## Phase 4: Sort Remaining FrontendRust Docs (Day 2-3, ~2 hours)

### FrontendRust/todo/
- `arena-deterministic-map-problem.md` → handled in Phase 2 (reasoning doc)
- `necx-clone-analysis.md` → flagged "lets get rid of this". Verify NECX shield in Luz covers it, then delete.

### FrontendRust/src/ in-tree docs
- `src/postparsing/docs/rc-environments-plan.md` → review; likely delete if stale.
- `src/higher_typing/docs/exceptions/HigherTypingPass.md` → check if still a problem. If yes, keep. If no, delete.

### FrontendRust top-level loose files
- `todo.md` → review and delete if stale
- `cursor_setup.md` → `FrontendRust/docs/usage/cursor-setup.md` or delete if stale
- `thoughts.md` → review, likely delete or fold into reasoning
- `manual/building.md` → `FrontendRust/docs/usage/building.md`
- `check-template.txt` → review, likely `docs/skills/` or delete

---

## Phase 5: Sort .claude/rules/ (Day 3, ~1-2 hours)

These `.mdc` files currently contain mixed content. Each needs to be categorized and either:
- Converted to a proper doc in `docs/` with a symlink back, OR
- Kept as-is if it's purely a Claude-specific rule that doesn't fit any doc category

### Files to process
- `early-lifetimes.mdc` → content should be in background + usage docs; replace with symlinks
- `general/frontendrust-build-test.mdc` → `FrontendRust/docs/usage/build-test.md`, symlink back
- `general/interning-patterns.mdc` → `FrontendRust/docs/usage/interning.md`, symlink back
- `general/no-unsolicited-restructuring.mdc` → shield or keep as Claude-specific rule
- `general/style-guide.mdc` → `FrontendRust/docs/usage/style.md`, symlink back
- `postparser/postparser-organization-map.mdc` → `src/postparsing/docs/architecture/organization.md`
- `postparser/IDEPFL-postparser-interning.md` → `src/postparsing/docs/usage/interning.md` or `architecture/interning.md`

After moving content, each `.mdc` file becomes either a symlink to the new location or is deleted.

---

## Phase 6: Sort .claude/skills/ and .claude/agents/ (Day 3, ~1 hour)

Skills and agents are AI workflow definitions. They should be documented in `docs/skills/` (#8 category) but the executable definitions may need to stay in `.claude/skills/` and `.claude/agents/` for Claude Code to discover them.

### Skills
- `arcana/SKILL.md` → incorporated into `docs/skills/document.md`. Delete after verifying.
- `guardian-teach/SKILL.md`, `guardian-post-review/SKILL.md` → gets a brief entry in `FrontendRust/docs/skills/` explaining the workflow, with the SKILL.md staying in `.claude/skills/` as the executable entrypoint.

### Agents
Agent `.md` files in `.claude/agents/` are executable definitions. They stay where they are but should be referenced from `docs/skills/` docs that describe the overall workflows they participate in.

---

## Phase 7: Triage Luz/shields/ (Day 3-4, ~1-2 hours)

Review each shield in `Luz/shields/` to determine if it's genuinely project-agnostic or Sylvan-specific.

### Likely project-agnostic (stay in Luz/)
General software principles: FFFLX, NGSAX, EAWX, DPAPIX, EANODVX, EMNINCX, EPIFX, FIJX, ITBLUX, NEDCX, OALZDX, PROPRCX, UEFSNSX, BDPDX

### Likely Sylvan/migration-specific (move to FrontendRust/docs/shields/)
Migration rules: CSTNFX, RSMSCPX, MACTX, NCWSRX, NNCDMX, NMDX, NNDX, NRDX, NAOCSCX, PSEX, TUCMPX, SWDWMSX, SHCNEX, SSTREX

### Likely FrontendRust-specific (move to FrontendRust/docs/shields/)
Arena/interning: AASSNCMCX, NECX, ESCCDX, IIDX, NVSEX, NRAFX, SITRPX

### Testing shields (evaluate per-shield)
ATEISPX, AIMITIPX, DCCRX, KICIX, NHCITX, NRICITX, NCTOBPAOPX, PSMONMX, RRIFX, TPUTEFCX, UCMTRSX, UEFIAIX, UUSNNCBX — some are general, some are migration-era

### For each moved shield
1. Add frontmatter `description:` field
2. Move file to target `docs/shields/`
3. Delete from `Luz/shields/`
4. Verify no other projects reference it from Luz

---

## Phase 8: Triage Sylvan/docs/ and Top-Level (Day 4, ~1-2 hours)

### Sylvan/docs/ (pre-existing, Vale language docs)
These are Vale language documentation, not compiler implementation docs. They likely stay where they are or get their own reorganization later:
- `Architecture.md`, `HigherTypingPass.md`, `Environments.md`, etc.
- `docs/old/` — bulk triage: most can probably be archived

### Top-level files
- `README.md` — stays
- `architectural-direction.md` — `docs/architecture/direction.md` or `docs/background/direction.md`
- `build-compiler.md` — `docs/usage/build-compiler.md`
- `compiler-overview.md` — `docs/background/compiler-overview.md`

---

## Phase 9: Update CLAUDE.md Files (Day 4, ~1-2 hours)

### 9.1 Audit ALL CLAUDE.md files
- Read through every CLAUDE.md in the repo (root `.claude/CLAUDE.md`, any per-pass ones)
- For each piece of content, decide: does this belong here, or should it live in a proper categorized doc?
- Strip inlined content that now lives in proper docs
- Replace with `@` references to background docs
- Replace shield list with plain markdown links + descriptions
- Keep only content that is genuinely CLAUDE.md-specific (project overview, build instructions, agent rules)

### 9.2 Create per-pass CLAUDE.md files
For each pass that has docs, create a CLAUDE.md with:
```markdown
<!-- Guardian:background:begin -->
@../../../docs/background/compiler-overview.md
@../../docs/background/arenas.md
@docs/background/<pass-specific>.md
<!-- Guardian:background:end -->

<!-- Guardian:shields:begin -->
- [ShieldName (ID)](docs/shields/ShieldName-ID.md) — description
<!-- Guardian:shields:end -->
```

Initially hand-written; Guardian will auto-maintain these once implemented.

---

## Phase 10: Create Symlinks (Day 4, ~30 min)

For every usage and architecture doc, create `.mdc` symlinks:

```bash
# Example for postparsing
ln -s ../../../FrontendRust/src/postparsing/docs/usage/interning.md \
      .claude/rules/postparser/usage/interning.mdc

ln -s ../../../FrontendRust/src/postparsing/docs/architecture/organization.md \
      .claude/rules/postparser/architecture/organization.mdc
```

Remove old `.mdc` files that have been replaced by symlinks.

---

## Phase 11: Cleanup (Day 4-5, ~1 hour)

### Delete emptied directories
- `FrontendRust/zen/` (all content moved)
- `FrontendRust/todo/` (all content moved or deleted)

### Delete stale files
- `FrontendRust/migrate-direction.md`
- `FrontendRust/todo/necx-clone-analysis.md` (if NECX shield covers it)
- Duplicate zen/ shield copies

### Update docs/todo.md
Check off completed items as we go. This is our progress tracker.

---

## Phase 12: Guardian Implementation (Day 5+, multi-day)

Implement the features specified in `Guardian/doc-design-doc.md`. This is a separate project but should be prioritized after the manual reorganization is complete.

### 12.1 `guardian docs list` (easiest, implement first)
- Scan `docs/shields/`, `docs/arcana/`, `docs/background/`, etc.
- Display grouped by category and scope

### 12.2 `guardian docs check` (read-only validation)
- Shield ID validation (filename matches title, no duplicates)
- Arcana structural checks (orphaned arcana, dangling @ID references)
- Doc linting (category placement, orphaned docs outside docs/)
- Arcana semantic completeness via Rabble (launch Claude instances to check changed code for missing @ID refs)

### 12.3 `guardian docs rebuild` (write operations)
- CLAUDE.md auto-population (background @refs + shield lists with descriptions)
- Symlink creation/cleanup in `.claude/rules/`
- Stale symlink removal

### 12.4 Shield auto-discovery integration
- Remove `include_shields` from guardian.toml
- Shield loading walks `docs/shields/` + `Luz/shields/`
- Scope-aware evaluation (shield applies to its subtree only)

### 12.5 Shield frontmatter requirement
- Validate `description:` field in all shield files
- Error on missing frontmatter during startup

---

## Verification

After each phase:
1. `cargo build --lib` — project still compiles
2. `cargo test` — tests still pass
3. Check that `.claude/rules/` symlinks resolve correctly
4. Grep for broken `@` references in CLAUDE.md files
5. Verify no docs are orphaned (not referenced from any CLAUDE.md or symlink)

After Phase 12:
6. `guardian docs check` passes clean
7. `guardian docs list` shows all docs properly categorized
8. `guardian docs rebuild` produces correct CLAUDE.md and symlinks

---

## Future: Turn `/audit-error-handling` into a Shield

The `/audit-error-handling` skill (`Guardian/.claude/skills/audit-error-handling/SKILL.md`) documents patterns for finding silent failures (FFFL violations). This knowledge should be distilled into a Guardian shield that runs automatically on code changes — catching `let _ =` on Results, `Err(_) => continue` without logging, `.ok()` discarding errors, `eprintln!` without return/panic, etc. The skill documents the exact patterns and tiers; the shield would enforce the most critical ones (Tier 1 and Tier 2) at review time.

---

## Files Created Today (reference)
- `docs/meta.md` — documentation strategy
- `docs/skills/document.md` — the /document skill
- `docs/todo.md` — master checklist
- `Guardian/doc-design-doc.md` — Guardian requirements
