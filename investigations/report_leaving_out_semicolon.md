# Report leaving out semicolon or ending body after expression, for paren

## Test

`Frontend/ParsingPass/test/dev/vale/parsing/AfterRegionsTests.scala:21`

```scala
test("Report leaving out semicolon or ending body after expression, for paren") {
  compileBlockContents("""
    |  a = 3;
    |  set x = 7 )
    """.stripMargin).expectErr() match {
    case BadExpressionEnd(_) =>
  }
}
```

**Expected:** `Err(BadExpressionEnd)` — the stray `)` should be detected as an error.
**Actual:** `Ok(ConsecutorPE(...))` — parser succeeds, producing `LetPE(a = 3)` and `MutatePE(set x = 7)`. The `)` is silently ignored.

## Root cause

The `)` never reaches the parser. The lexer's `lexScramble` calls `atEnd` in a loop, and `atEnd` (Lexer.scala:865) returns `true` on `)`:

```scala
iter.peek() match {
  case ')' | '}' | ']' => true   // ← stops scramble here
  ...
}
```

This is by design — `)` normally closes a `ParendLE` that was opened by `lexParend`. But in this test, `compileBlockContents` calls `lexScramble` directly at the top level (no enclosing parens). The `)` is a stray token with no matching `(`. `lexScramble` stops before it and returns a valid scramble of `[a, =, 3, ;, set, x, =, 7]`. The `LexingIterator` still has `)` unconsumed, but `compileBlockContents` never checks for leftover input.

## Collapsed call tree

```
- TestParseUtils#compileBlockContents:
    lexer.lexScramble(iter, false, false, false) on input "\n  a = 3;\n  set x = 7 )\n  "
  - Lexer#lexScramble ... Lexer#atEnd:
      Lexes 8 tokens [WordLE(a), SymbolLE(=), ParsedIntegerLE(3), SymbolLE(;), WordLE(set), WordLE(x), SymbolLE(=), ParsedIntegerLE(7)].
      atEnd sees ')' → returns true → scramble loop stops.
      `)` remains in LexingIterator, never consumed.
  - ExpressionParser#parseBlockContents:
      Receives 8-element scramble. Parses two statements successfully.
      Never knows about the `)` — it wasn't in the scramble.
```

## Fix options

### Option A: Check for leftover input in `compileBlockContents` (test utility fix)

After `lexScramble`, check `!iter.atEnd()` (after skipping whitespace). If there's leftover, return `Err(BadExpressionEnd(iter.getPos()))`.

This only fixes the test utility. In production, `lexScramble` is always called inside `lexParend`/`lexCurlied`/etc. which consume the `)` as a closer. So the production path is fine — only the test entry point is wrong.

**Pro:** Minimal change, correct for this test.
**Con:** Only affects the test utility; a "real" `compileBlockContents` entry point would have the same bug.

### Option B: Fix in `parseBlockContents` (parser level)

After the while loop in `parseBlockContents`, check if the iterator has unconsumed tokens that aren't `;` or curlied-stops. But the parser's `ScrambleIterator` only sees what's in the scramble — it can't detect that the lexer left behind a `)`.

**Verdict:** Not possible at the parser level — the information is lost.

### Option C: Fix in `lexScramble` or `atEnd`

Make `atEnd` not stop on `)` unless we're inside a paren context. This would be a larger refactor and could break other things.

**Verdict:** Too risky for this fix.

## Recommendation

**Option A** — add a leftover-input check in `TestParseUtils.compileBlockContents` after `lexScramble`. This mirrors what the real compiler does (the real entry points lex inside curly braces, so stray `)` would be caught by `lexCurlied` as an error or by the file-level scramble ending check).

However, looking at this more carefully: in the **real** compiler, function bodies are inside `{ }`. The lexer produces `CurliedLE` for the body, and inside that curlied scramble, a stray `)` would also stop the scramble early (same `atEnd` logic). So this might actually be a real bug in production too — a stray `)` inside a function body would be silently ignored.

Need to check: does the real compiler entry point verify that the `CurliedLE`'s closing `}` is found? If so, the `)` would cause a "missing `}`" error rather than silent success. The test's `compileBlockContents` skips that outer structural check.
