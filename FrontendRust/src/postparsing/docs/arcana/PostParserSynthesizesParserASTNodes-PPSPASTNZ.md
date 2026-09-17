# PostParser Synthesizes Parser AST Nodes (PPSPASTNZ)

The postparser creates synthetic `IExpressionPE<'p>` nodes (parser-typed AST) during expression scouting. These are not produced by the parser — they are fabricated by the postparser to represent implicit operations like struct constructor calls at the end of function bodies.

## Where

`src/postparsing/expression_scout.rs` — in the block-scouting logic that handles constructing members. The postparser builds `LookupPE`, `DotPE`, and `FunctionCallPE` nodes, then feeds them back through `scout_expression` to produce the final `IExpressionSE` output.

## Cross-cutting effect

Because the postparser constructs parser-typed nodes, it needs access to the `'p` (parser) arena to allocate them. This is why `PostParser` holds both `scout_arena: &'s Bump` (for postparser output) and `parse_arena: &'p Bump` (for synthetic parser nodes). Without this, the per-pass arena model would break — parser types live in `'p`, and the postparser can't allocate `'p`-typed data without the `'p` arena.

## Why it exists

The Scala code does the same thing. The postparser synthesizes a constructor call expression from the struct's member names, then scouts it like any other expression. This reuses the existing expression-scouting logic rather than duplicating it for the synthetic case. Under Scala's single `Interner` arena, this was invisible — everything shared one lifetime. With per-pass arenas, it surfaces as a cross-arena dependency.

## The synthetic nodes are temporary

These parser AST nodes are not stored in the final postparsed output. They are created, scouted (producing `IExpressionSE` nodes), and then abandoned. The `'p` arena keeps them alive until it drops, but nothing in the postparsed AST references them.
