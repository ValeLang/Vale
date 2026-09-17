---
g_read_when: "Read when navigating how Scala scout classes map to PostParser files."
g_auto_load_when_editing:
  - FrontendRust/src/postparsing/*.rs
---

# Postparsing Organization Map

- Scala had separate classes (`ExpressionScout`, `FunctionScout`).
- Rust keeps the same responsibilities, but as `impl PostParser` methods split across files.
- `expression_scout.rs` contains expression-scouting methods on `PostParser`.
- `function_scout.rs` contains function-scouting methods on `PostParser`.
- `post_parser.rs` remains the top-level orchestrator calling those methods via `self`.
- Scala often passed references to narrower sub-components; Rust methods take `&self` (`PostParser`) and can directly access all PostParser sub-parts.
