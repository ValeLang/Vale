---
g_read_when: "Read when writing Rust imports or referencing types by path."
g_auto_load_when_editing:
  - "FrontendRust/**/*.rs"
---

# Rust Import Style

- Avoid `crate::` and long module paths in function bodies, type signatures, and match arms.
- Add the necessary `use` statements at the top of the file so types and variants can be referenced by short names.
- Prefer bare names: `RuneUsage`, `IRulexSR`, `LocationInDenizenBuilder` instead of `crate::postparsing::rules::rules::RuneUsage`.
- Import enum variants when possible so match arms use `Environment(x)` instead of `IEnvironmentS::Environment(x)`.
- For associated functions like `PostParser::eval_range`, `::` is sometimes unavoidable without refactoring; that's acceptable.
- Apply to new code and when editing existing Rust files.
