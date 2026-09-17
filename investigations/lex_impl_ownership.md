# Investigation: "Reports when non-kind interface/struct in impl" test failures

## Bug summary

Tests: `dev.vale.postparsing.AfterRegionsErrorTests` / "Reports when non-kind interface in impl" and "Reports when non-kind struct in impl"

Code under test:
```vale
// Test 1: ownership on interface
impl &IMoo for Moo;

// Test 2: ownership on struct
impl IMoo for &Moo;
```

Expected: Postparsing should reject these with `CantOwnershipInterfaceInImpl` and `CantOwnershipStructInImpl` respectively — you can't put ownership prefixes on interface/struct names in impl declarations.

Actual: The lexer rejects these earlier with `BadImplInterface` / `BadImplStruct` because `lexIdentifier` can't handle the `&` prefix character. The input never reaches the parser or postparser.

## Root cause

`Lexer.lexImpl` (Lexer.scala:180-215) calls `lexIdentifier` for both the interface and struct name positions. `lexIdentifier` only consumes unicode identifier characters (letters, digits, underscore). When it encounters `&IMoo`, the `&` is not an identifier character, so `lexIdentifier` returns `None`, and the lexer immediately returns `Err(BadImplInterface(...))`.

The error pipeline should be: lexer -> parser (`TemplexParser.parseTemplex` creates `InterpretedPT` from `&` + name) -> postparser (`scoutImpl` matches on `InterpretedPT` and throws `CantOwnershipInterfaceInImpl`/`CantOwnershipStructInImpl`). But the lexer was blocking step 1.

## Resolution

Added `lexImplOwnershipPrefix` helper to `Lexer.scala` that consumes ownership prefix symbols (`&`, `^`) before the identifier, emitting them as `SymbolLE` tokens. These tokens are included in the scramble alongside the identifier and optional generic args.

### Helper method (Lexer.scala)

```scala
private def lexImplOwnershipPrefix(iter: LexingIterator): Vector[SymbolLE] = {
  var symbols = Vector[SymbolLE]()
  while (!iter.atEnd() && (iter.peek() == '&' || iter.peek() == '^')) {
    val begin = iter.getPos()
    val c = iter.peek()
    iter.advance()
    symbols = symbols :+ SymbolLE(RangeL(begin, iter.getPos()), c)
  }
  symbols
}
```

This handles `&` (borrow), `^` (own), and `&&` (weak, as two separate `&` symbols — matching how `TemplexParser.parseInterpreted` uses `trySkipSymbols(Vector('&', '&'))`).

### Changes to lexImpl

Both the interface and struct positions now call `lexImplOwnershipPrefix` before `lexIdentifier`, and include any prefix symbols in the scramble:

```scala
val interfaceOwnershipSymbols = lexImplOwnershipPrefix(iter)
val interfaceName = lexIdentifier(iter) match { ... }
// ...
val elements = interfaceOwnershipSymbols ++ Vector(interfaceName) ++ maybeInterfaceGenericArgs.toVector
```

The same pattern is applied for the struct position.

### No parser or postparser changes needed

The parser already calls `templexParser.parseTemplex` on these scrambles, which handles `&` as an ownership prefix and produces `InterpretedPT`. The postparser's `scoutImpl` already matches on `InterpretedPT` and throws the correct errors. Only the lexer was blocking the pipeline.

## Score after fix

1052 passed, 34 failed (was 1050/36).
