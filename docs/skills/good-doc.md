---
name: good-doc
description: Document information by splitting it into the correct categories (background, usage, arcana, shields, architecture, reasoning, skills) and writing it to the appropriate docs/ directories.
---

# Document

The user wants to document something. Your job is to categorize the information and write it to the correct locations per the documentation strategy in `docs/meta.md`.

## Step 1: Understand what's being documented

Ask the user (or infer from context) what they want to document. Gather the full picture before writing anything.

## Step 2: Read the documentation strategy

Read `docs/meta.md` to refresh on the category definitions and conventions.

## Step 3: Categorize

Split the information into the categories it belongs to. A single piece of knowledge often spans multiple categories. Present the split to the user for approval before writing.

The categories are:

1. **Background** — General knowledge needed to read code in this area. Background docs must **as concise as possible** and should reference other docs for details rather than repeating information inline, because background docs are included in every prompt to every LLM, and they should keep noise to a minimum.
2. **Usage** — How to interact with this feature correctly when writing code.
3. **Arcana** — Cross-cutting concerns with non-obvious effects elsewhere. Has a unique ID (initialism + Z suffix) and `@ID` references at affected code sites.
4. **Shields** — Enforceable rules/constraints. Has a unique ID (initialism + X suffix).
5. **Migration** — Ephemeral migration status, known Scala/Rust differences, workarounds.
6. **Architecture** — Internal design, data flow, invariants for modifying the feature itself. Architecture docs should also surface *where the feature is heading* — architecture is about evolution, not just the current snapshot. If there's a planned refactor or a target design the code is converging toward, mention it here with a link to the Reasoning doc that holds the details.
7. **Reasoning** — Why the current approach was chosen over alternatives, **and future plans** the code is not yet implementing. If a design has a known target shape that's deferred (post-migration, post-benchmarking, pending a decision), it belongs here. Sub-category of architecture. Always cross-referenced from the relevant Architecture doc so readers discover the future plan while reading about the current design.
8. **Skills** — Step-by-step AI workflow methodology.
9. **Bugs** — Known bugs go as `#[ignore]`'d tests, not documents.
10. **Requirements** — Tests are requirements, not documents.

For each piece of information, identify:
- Which category it belongs to
- Which feature/directory it's closest to (determines which `docs/` directory it goes in)
- Whether it extends an existing doc or needs a new one

## Step 3b: Extract enforceable rules

After categorizing, actively ask: **"Is any part of this wisdom concrete and enforceable?"** Shields are the most durable form of documentation — they can't drift because Guardian checks them. Any time you learn something that could be a rule, propose it as a candidate shield.

Present to the user:
- What the candidate shield would enforce (one sentence)
- A proposed title and ID
- Whether it's checkable by Guardian (pattern in code reviews) or only by the compiler/tests

The user decides which candidates are worth making into shields. Don't silently categorize something as "just background" when it could also be an enforceable rule.

Examples of wisdom → shield extraction:
- "We learned that stringly-typed errors are hard to test" → Shield: `NoStringlyTypedData-NSTDX` — error types must use structured data, not string messages
- "Arena types shouldn't clone" → Shield: `ArenaTypesDontClone-ATDCX` — already exists
- "The compiler now requires explicit drop bounds" → Not a shield (enforced by compiler itself), just background/usage docs

## Step 4: Check for existing docs

Before creating new files, check whether relevant docs already exist in the target `docs/` directories. Prefer extending existing docs over creating new ones.

## Step 5: Write the documents

For each category, write to the appropriate location:

- Single file: `docs/<category>.md`
- Multiple files: `docs/<category>/<topic>.md`

Follow the naming conventions from `docs/meta.md`.

### Arcana-specific steps

If any piece of information is an arcana (cross-cutting concern):

1. **Generate title and ID.** The title describes the concern plainly (does NOT contain the word "arcana"). The ID is an uppercase initialism of the title words with Z appended. Keep the acronym readable (4-10 letters before the Z). Present to user for approval.

2. **Draft the arcana text and get a second approval.** Once the user approves the title and ID, write the tentative arcana doc body inline in chat (not to disk yet) and ask the user to approve the text before it's written to a file. The user may want to tweak wording, add nuance, or cut fluff. Only after they approve the drafted text do you move on to step 3.

3. **Create the arcana doc** at `<feature>/docs/arcana/<HammerCaseTitle>-<ID>.md` in the `docs/` directory of the feature that *causes* the cross-cutting effect. HammerCase with the initialism at the end, like `PostParserSynthesizesParserASTNodes-PPSPASTNZ.md`. Include information such as: a brief description of the concept, at least one example concisely illustrating it, why the concept exists, and what its cross-cutting effect is. If there are other arcana that it affects or is affected by it, mention those as part of regular prose (not as an extra section). Notes:
   * It should be concise. Don't include fluff. Don't be redundant. Get to the point.
   * Instead of long paragraphs, feel free to break things up with newlines.
   * It should be one markdown section, it should not have subsections headers. If it must be long enough that subsections are needed, feel free to use bold lines like, `**Interactions with IDKWTHI:**`.
   * Instead of having a section starting with `**Cross-cutting effect:**`, start it with something else, like `**How this affects call-sites**:` etc.
   * **Focus on *why*, not *what*.** The arcana's job is to explain the strategic reason the code behaves this way — the design invariant, the trade-off, the concern that drives this behavior. It's fine to anchor the reader with a function or type name, but don't narrate tactical implementation: specific call chains, control-flow sequences, "which branch runs when," step-by-step mechanics. Readers come to the arcana for the *why*; they can read the code for the *what*. Tactical narration also dates fast — which is the stronger form of the no-line-numbers rule below.
   * Do NOT reference file/line numbers (e.g. `FunctionCompiler.scala:194`). Code moves around constantly and line-anchored references go stale fast. Refer to code by concepts, function names, type names, or module/file names only — readers can find the current location by searching for those. The `@ID` markers added to code sites in step 5 are the reverse pointer; the arcana doc doesn't need to point back at specific lines.

4. **Find all relevant code sites.** Search the codebase for every place this arcana manifests: struct fields, code blocks, function signatures, comments. Use Grep, Glob, and Read. Be thorough — missing a site defeats the purpose.

5. **Add `@ID` references.** At each relevant site, add a comment referencing the arcana. The reference must always appear in a sentence:
   - `// Per @PPSPASTNZ, synthesize a constructor call as parser AST.`
   - `// Needed because postparser creates parser nodes (see @PPSPASTNZ)`

   Never write a bare `@ID` without a sentence. The sentence gives local context; the `@ID` tells readers where to find the full explanation. Add references in code as comments, and add references to other documentation and other arcana where relevant.

   **Keep code-comment references concise.** Preferably one sentence. Ideally one line. The arcana doc is the place for the full explanation — the comment just needs to tell the reader "this is an instance of `@ID`, go read it" plus whatever local context is genuinely needed to understand what *this* site is doing. If you find yourself writing a three-line comment explaining the arcana again, cut it — readers can follow the `@ID` to the doc.

### Shield-specific steps

If any piece of information is a shield (enforceable rule):

1. **Generate title and ID.** The ID is an uppercase initialism of the title words with X appended. Present to user for approval.

2. **Create the shield doc** at `<feature>/docs/shields/<HammerCaseTitle>-<ID>.md`.

## Step 6: Cross-references

After writing docs, add a `## See also` section with relative markdown links following the cross-reference chain defined in `docs/meta.md`:

- **Background** docs → link to relevant **Usage** docs
- **Usage** docs → link to relevant **Arcana** and **Shield** docs
- **Architecture** docs → link to relevant **Reasoning** and **Skill** docs

Only add links where related docs actually exist. Don't create empty See also sections.

## Step 7: Report

Tell the user:
- What categories the information was split into
- What files were created or updated, and where
- For arcana: how many code sites were annotated
- For shields: the ID created
