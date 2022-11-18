[[Anders Hejlsberg on Modern Compiler
Construction]{.underline}](https://www.youtube.com/watch?v=wSdV1M7n4gQ&ab_channel=Googol)

Key takeaways:

-   ASTs from other files will be immutable and reusable.

-   The parser AST should all have relative positions, relative to their
    > parent. that way, when someone types, we can rebuild the spine and
    > reuse the rest.

-   Occurs to me, mismatched braces will be nightmarish. kind wish we
    > had significant whitespace like python. perhaps we can use
    > indentation as a hint?

-   Will need generics, so we dont have to deal with
    > monomorphizing/duck-typing.

-   Overloads will be tricky. We\'ll need that overload index\...

[[Incremental Overload Resolution in Object-Oriented Programming
Languages]{.underline}](https://www.mathematik.uni-marburg.de/~seba/publications/incremental-overload-resolution.pdf)

It looks like they take their normal overload resolution approach, look
at the various steps needed for it (looking through all the namespaces
etc for methods) and it caches each step.

I think the first step to all of this is to figure out incremental
compilation. LSP is basically incremental compilation that just stays
alive instead of flushing and dying.
