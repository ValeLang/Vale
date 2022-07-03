Conditional compilation TL;DR: Vale will:

-   have module-level conditional compilation

-   inside a module, have debug-mode conditional compilation of pure
    > calls.

Metaprogramming TL;DR: Vale won\'t have:

-   C preprocessor macros

-   Rust-style macros, where we assemble an AST

It will have something spiritually similar to C++, but much simpler
syntactically.
