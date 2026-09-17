---
name: guardian-teach
description: "Process a // VV: violation comment in Rust code — match it to an existing Guardian shield or create a new one, then add a test case to the shield's tests/ directory."
---

# Violation Vetter

The user has added a `// VV: <description>` comment to a Rust fn/struct/enum/etc. definition to flag a violation they found. Your job is to identify which Guardian shield this violates (or help create a new one), then record the violation as a test case.

Only process ONE `// VV:` comment per invocation (the first one found).

## Step 1: Find the VV comment

Search the working tree for the first `// VV:` comment in Rust files:

```
grep -rn "// VV:" --include="*.rs" | head -1
```

Extract the **file path**, **line number**, and **description** (the text after `// VV:`). Read the surrounding code to understand which definition (fn/struct/enum/impl/trait) the comment is attached to.

## Step 2: Recommend a shield

Read the shield file names and their first few lines from `Luz/shields/`:

```
for f in Luz/shields/*.md; do echo "=== $f ==="; head -8 "$f"; echo; done
```

Based on the violation description and the code context, present the user with options:

1. **Best existing shield match** — name it and explain why it fits
2. **Second-best match** (if any)
3. **New shield proposal** — suggest a name, code (4-8 letter uppercase acronym + X suffix), and one-sentence rule description

Make a recommendation and ask:

> Which shield should this violation be filed under?
> 1. [ExistingShield-CODEX] (recommended)
> 2. [OtherShield-CODEX]
> 3. New shield: [ProposedName-CODEX] — "[rule description]"

## Step 3: Remove the VV comment

Once the user has chosen a shield, **remove the `// VV:` comment line from the source file before doing anything else**. The comment is metadata for this skill and must not appear in the contextified diff or test case.

## Step 4: Generate and verify the contextified diff

Run Guardian's contextified diff subcommand. It accepts `--line` and auto-detects which definition contains that line:

```bash
cd /Volumes/V/Sylvan && \
Guardian/target/debug/guardian contextified-diff \
  --file <file_path> \
  --line <line_number> \
  --base HEAD
```

If the `contextified-diff` subcommand doesn't exist yet, fall back to a manual approach:
1. Get the definition boundaries (find the fn/struct/enum block start and end)
2. Run `git diff HEAD -- <file>` to get the raw diff
3. Extract the portion relevant to this definition
4. Format it similarly to the contextified diffs in `FrontendRust/guardian-logs/` (look at an example for the format)

**Verify the output.** Read the contextified diff and check that:
- It contains the code around where the `// VV:` comment was
- The definition boundaries look correct (not too narrow, not too wide)
- It is not empty or showing an unrelated definition

If it looks wrong, try adjusting the line number (the VV comment removal shifted lines by 1) or fall back to the manual approach.

## Step 5: Create the test case

**If the user chose a new shield (option 3)**, first work with them to define it before proceeding:
- Discuss what pattern is being prohibited or required
- Agree on shield name, code, model tier (`SimpleSmall`/`SimpleMedium`/`AgenticSmall`), and rule text (DO / NEVER / Examples / Clarifications sections)
- Create the shield file at `Luz/shields/ShieldName-CODEX.md` with proper frontmatter

**Then, for both existing and new shields:**

1. Determine the tests directory: `Luz/shields/<ShieldName-CODEX>/tests/`. Create it if it doesn't exist (`mkdir -p`).
2. Find the next case number by looking at existing `NNN.diff` files and picking the next integer.
3. Write `NNN.diff` (zero-padded 3-digit, e.g. `001.diff`) with the contextified diff content.
4. Write `NNN.expected.json`:
```json
{
  "violations": [
    {"reason": "<concise description derived from the // VV: comment and code context>"}
  ]
}
```

## Step 6: Report

Tell the user:
- Which shield the violation was filed under
- Case number created
- The violation reason
- The files created

## Notes

- The `// VV:` comment describes what's WRONG with the code, not what's right.
- Shield files live at `Luz/shields/`. The flat `.md` file stays where it is — do NOT move it into the directory.
- The tests directory is `Luz/shields/<ShieldName-CODEX>/tests/` (a folder next to the flat file). VV cases go directly to `tests/` (TDD-style target state), not to `disagreements/`.
- When writing the violation reason for `NNN.expected.json`, be concise but specific enough that someone reading it understands what the LLM should catch.
- Case files use zero-padded 3-digit numbering: `001.diff`, `001.expected.json`, `001.referenced_defs.txt`.
