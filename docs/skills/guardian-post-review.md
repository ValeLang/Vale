---
name: guardian-post-review
description: Process //f violation annotations from a Guardian review. Validates context quality before creating cases in cases/need-shield-amendment/. Invoke after applying a Guardian review patch and marking false positives with //f.
argument-hint: [optional: path to scan, defaults to src/]
allowed-tools: Bash(guardian feedback-line *), Read, Grep, Glob
---

# Process Feedback Annotations

Scan source files for `//f Violation:` annotations left by the user after a Guardian review, validate each one, and create test cases.

## Workflow

1. **Find all `//f` annotations** in the target directory (default: `src/`):
   ```
   Grep for "//f Violation:" across all .rs files
   ```

2. **For each `//f` annotation**, before processing:

   a. **Read the contextified diff** from the `Context:` path in the annotation line. Check:
      - The file exists and is non-empty
      - It contains the definition name mentioned in the violation
      - It looks like a complete contextified diff (not truncated)

   b. **Read the log file** from the `Log:` path. Check:
      - The file exists
      - It contains the LLM's reasoning for the violation

   c. **Sanity-check the annotation**: Read the actual code around the annotation. Check the user's `//f` (false positive) judgment makes sense — the violation reason should NOT actually apply to this code. If it looks like a true positive that was incorrectly marked `//f`, flag it.

   d. **If all checks pass**: Run `guardian feedback-line --file <path> --line <N>` to create the test case and remove the annotation.

   e. **If any check fails**: Skip this annotation and note the problem. Do NOT run feedback-line. Do NOT remove the `//f` line.

3. **Process files bottom-up**: Within each file, process `//f` lines from the last line to the first, so that removing a line doesn't shift the line numbers of annotations above it.

4. **Report summary** to the user:
   - How many `//f` annotations were processed successfully (test cases created)
   - Any `//f` annotations that were skipped, with reasons:
     - Missing or empty contextified diff
     - Truncated context (definition not found in diff)
     - Annotation appears to be a true positive (the violation reason does apply)
   - Remind the user to remove any remaining `//d` lines themselves

## Notes

- `//t` annotations are left untouched — they indicate acknowledged true positives
- `//d` annotations are not processed by this tool — the user removes them manually
- Only `//f` annotations are processed: they become disagreement cases indicating the shield instructions need clarification
- Each case consists of `NNN.diff` (the contextified diff), `NNN.expected.json` (`{"violations": []}`), and `NNN.referenced_defs.txt` (referenced definitions, may be empty) in the `cases/need-shield-amendment/` directory within the shield's companion directory (e.g., `Luz/shields/ShieldName-CODE/cases/need-shield-amendment/`). The `referenced_defs.txt` is read from the `ReferencedDefs:` path in the annotation line; if that path doesn't exist, create an empty file. These are later reviewed during the curation process and may be promoted to `tests/`.
- **Run `guardian feedback-line` from the git repo root**, not from a subdirectory. Paths in annotations (Log, Shield, Context) are relative to the repo root.
- **Strip `(V: ...)` user notes** from annotation lines before processing. The parser expects `//f Violation: CODE: reason...` — parenthetical notes between `Violation:` and the code will break parsing.
