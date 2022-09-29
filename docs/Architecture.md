
There are currently six phases of the frontend:

 * Lexer
 * Parser
 * PostParser
 * Higher Typing
 * Typing (aka "Compiler")
 * Simplifier


## Vale-Specific Terms

 * Denizen: Any top-level construct, such as struct, interface, function, impl, import, export.
 * Citizen: A struct or an interface.


## Lexer

The "lexer" is actually more of a pre-parser. It converts the input character stream into a hierarchy of tokens. Each node in this hierarchy is either:

 * Symbol
 * Word
 * "Parend": parentheses and their contents.
 * "Squared": square brackets and their contents.
 * "Curlied": curly braces and their contents.
 * "Angled": angle brackets (chevrons) and their contents, unless the angle bracket is a binary operator.

The lexer then *streams* this to the next stage.

### Angle Brackets

Determining whether an angle bracket is an open/close angle bracket or a less-than/greater-than binary operator seems difficult, but is actually pretty simple. It's an open or close chevron if:

 * Previous char is not a `=` (like `=>` for lambdas) and next char is not a `=` (like `>=` and `<=`).
 * There isn't whitespace on *both* sides.

In other words, a chevron is a binary operator if it (and a possible next `=`) has whitespace on both sides. See `angleIsOpenOrClose` for the actual code.

Once we have this tree-shaped thing from the lexer, we can trivially do lookahead in the parser with no cost, which helps us offer a much simpler syntax.

### Streaming

The lexer will actually pause its lexing as soon as it completes each denizen, to hand it off to a handler.

It's up to the handler to determine whether to do parsing right then, or save the lexed results for later.

This streaming is also important because once we're done lexing the import statements, we can eagerly fetch the next file into memory before continuing lexing.


## Parser

The parser will look at the relatively primitive lexed AST and turn it into a much more meaningful AST.


## Post-Parser

This stage does any calculations that are possible given only this denizen's AST, and no knowledge of anything outside.

It will:

 * Figure out what closured variables there are, and whether
theyre mutated from inside closures.
 * Figure out what variables are modified from inside closures.
 * Normalize rules.



## Higher Typing

The higher typing pass is the first one that can look at the rest of the program. It figures out the types of all template runes.

This could be merged into the Typing pass (and sometimes is). One day it will be.


## Typing (aka "Compiler")

This pass does all the type checking and overload resolution. Soon, the Post-Parsing and Higher Typing passes will be merged into this.


## Simplifier

This final phase:

 * Monomorphizes all generics.
 * Turns all double pointers into pointers to structs that contain pointers.
    * This was originally for compiling to JVM, JS, etc.


# Notes

[ArchitectureNotes.md](notes/ArchitectureNotes.md)
