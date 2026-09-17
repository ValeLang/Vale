# Func with func bound with missing 'where'

## Test

`Frontend/ParsingPass/test/dev/vale/parsing/functions/AfterRegionsFunctionTests.scala:13`

```scala
test("Func with func bound with missing 'where'") {
  // It parses that func moo as a templex, and apparently a return can be a templex
  compileDenizen("func sum<T>() func moo(&T)void {3}").expectErr() match {
    case null => vimpl()
  }
}
```

**Expected:** Some parse error (specific type TBD — test uses `case null => vimpl()`).
**Actual:** `Ok(TopLevelFunctionP(...))` — parser succeeds, treating `func moo(&T)void` as a function-type return annotation.

## What the parser produces

```
FunctionP(
  name = "sum",
  genericParams = [T],
  params = [],
  return = FuncPT(name="moo", params=[&T], return=void),  // ← treated as return type
  body = {3}
)
```

The correct syntax would be: `func sum<T>() where func moo(&T)void {3}` — with `where` separating the return type from the function bound.

## Collapsed call tree

```
- Lexer#lexFunction on "func sum<T>() func moo(&T)void {3}":
    Lexes "func" keyword, name "sum", generic params <T>, params ().
    Then: lexScramble(iter, stopOnOpenBrace=true, stopOnWhere=false, stopOnSemicolon=true)
  - Lexer#lexScramble for trailing details:
      Lexes tokens until it hits '{': produces [func, moo, (&T), void].
      These become `trailingDetails` (FunctionHeaderL.trailingDetails).
  - Lexer#lexCurlied: lexes {3} as the function body. OK.

- Parser#parseFunction on the FunctionL:
    Receives trailingDetails = scramble[func, moo, ParendLE(&T), void].
  - trySkipPastKeywordWhile(keywords.where, ...):
      Scans for "where" in trailing details — not found.
      Result: entire trailing details = return type, no where clause.
  - TemplexParser#parseTemplex(returnIter) on [func, moo, (&T), void]:
    - TemplexParser#parsePrototype:
        Sees "func" keyword → tries to parse function type templex.
        Parses name "moo", params (&T), return type "void".
        Returns Ok(FuncPT(name="moo", params=[InterpretedPT(borrow, T)], return=NameOrRunePT("void"))).
      SUCCESS — entire trailing details consumed as a FuncPT return type.
```

## Root cause

The `func name(params)return` syntax is ambiguous: it's valid both as a function-type templex (in return position) and as a function bound (in where-clause position). The parser can't distinguish them without the `where` keyword.

## Fix options

### Option 1: Reject `FuncPT` as a return type in `parseFunction`

After parsing the return type, if it's a `FuncPT` and there's no `where` clause, return an error. This assumes function-type return values are never valid in Vale, or are always written differently (e.g., with a type alias, or wrapped in parens).

**Risk:** Could break valid syntax if Vale allows `func foo() func bar(int)int { ... }` meaning "foo returns a function type". Need to check if this is ever intended.

### Option 2: Check for unconsumed tokens after return type

Not applicable — the `FuncPT` consumes everything in `trailingDetails`.

### Option 3: Make `lexScramble` in trailing-details context stop on `func`

Add a `stopOnFunc` parameter to `lexScramble`. In `lexFunction`, pass `stopOnFunc=true` so that `func` in trailing details stops the scramble. Then the trailing details would be empty, `func moo(&T)void` would be left for `lexCurlied` which would fail (it expects `{`), producing `BadFunctionBodyError`.

**Risk:** Breaks valid function-type return annotations if they exist. Also a broader change to `lexScramble`'s API.

### Option 4: Split trailing details on `func` keyword at parse time

In `Parser#parseFunction`, before splitting on `where`, also check for a bare `func` keyword in the trailing details. If `func` appears (not after `where`), treat it as a missing-where error.

**Pro:** Targeted, no lex changes.
**Con:** Must distinguish `func` as part of a return type templex (valid in where-clause rules) vs `func` as a bound-without-where. Could check: if return type would be empty (nothing before the `func`), then it's a missing-where. If there's a return type before the `func`, it's something else.

### Option 5: Detect at parse time: empty return + FuncPT pattern

In `parseFunction`, if:
- No `where` clause was found
- The return type parsed as a `FuncPT`
- There are no other tokens before the `func` in trailingDetails (i.e., the entire return is just a `FuncPT`)

Then return a "missing where" error. This specifically catches `func sum<T>() func moo(&T)void {3}` without affecting `func sum<T>() int` or `func sum<T>() SomeType where func moo(&T)void {3}`.

## Open questions

1. Is `func foo() func bar(int)int { ... }` (function returning a function type) ever valid Vale syntax? If not, Option 1 is simplest.
2. What error type to use? The test has `case null => vimpl()` — no decision has been made. Could define a new `MissingWhereKeyword` error, or reuse an existing one.
3. Should this be caught at lex time (Option 3) or parse time (Options 1/4/5)?
