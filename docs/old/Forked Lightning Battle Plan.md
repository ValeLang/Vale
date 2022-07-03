# Forked Lightning --- Battle Plan

*BECAUSE GLORY IS OURS FOR THE TAKING*

This is our roadmap of how we\'ll get from here to the finish line.

See also [[Repl.it
Plan]{.underline}](https://docs.google.com/document/d/1yJAYmYZFAjyAUBeqpaHVbytxcvtpjkr_ZsOZCTR-9WI/edit#),
[[Vision for the Cross-Platform
Core]{.underline}](https://vale.dev/blog/cross-platform-core-vision),
[[AST
comments]{.underline}](https://github.com/Verdagon/Vale/tree/master/Valestrom/Metal/src/net/verdagon/vale/metal),
and the [[repl.it with the
code]{.underline}](https://repl.it/@ForkedLightning/ForkedLightningWeb).

**OUR BATTLE IS DONE, AND WE EMERGE STRONGER THAN EVER BEFORE**

**Our victory is in fate\'s hands now.**

~~JS Transpiler (Veim)~~

-   ~~Basic program with an integer: fn main() { 42 }~~

-   ~~Adding two integers: fn main() { 40 + 2 }~~

    -   This actually calls the + function, which itself calls out into
        > an extern function \_\_addIntInt to do its adding (that\'s
        > right, the Vale compiler doesn\'t actually know how to add,
        > lol)

    -   Someday, Valestrom will have nice inlining that takes away the
        > intermediate + function.

-   ~~Printing: fn main() { print(\"hello!\"); }~~

    -   This will bring in the function \"print\", which calls the
        > extern function \"\_\_print\". We\'ll need to hook that up to
        > the browser\'s console.log.

-   ~~Adding strings: fn main() { println(\"hello!\"); }~~

    -   This brings in the function \"println\" which adds a \"\\n\" to
        > the given string.

-   ~~Struct with storing~~:

-   ~~Nested structs:
    > [[Samples/structs/structs.vale]{.underline}](https://github.com/Verdagon/Vale/blob/master/Valestrom/Samples/test/main/resources/structs/structs.vale)~~

-   ~~List the next samples (Verdagon)~~

-   ~~interfaces:~~

    -   [~~[virtuals/calling.vale]{.underline}~~](https://github.com/Verdagon/Vale/blob/master/Valestrom/Samples/test/main/resources/virtuals/calling.vale)

    -   [~~[virtuals/retUpcast.vale]{.underline}~~](https://github.com/Verdagon/Vale/blob/master/Valestrom/Samples/test/main/resources/virtuals/retUpcast.vale)

    -   [~~[virtuals/mutinterface.vale]{.underline}~~](https://github.com/Verdagon/Vale/blob/master/Valestrom/Samples/test/main/resources/virtuals/mutinterface.vale)

-   ~~branching:~~

    -   [~~[if/if.vale]{.underline}~~](https://github.com/Verdagon/Vale/blob/master/Valestrom/Samples/test/main/resources/if/if.vale)

    -   [~~[while/while.vale]{.underline}~~](https://github.com/Verdagon/Vale/blob/master/Valestrom/Samples/test/main/resources/while/while.vale)

-   ~~arrays:~~

    -   ~~[[arrays/knownsizeimmarray.vale]{.underline}](https://github.com/Verdagon/Vale/blob/master/Valestrom/Samples/test/main/resources/arrays/knownsizeimmarray.vale)~~

    -   [~~[arrays/immusa.vale]{.underline}~~](https://github.com/Verdagon/Vale/blob/master/Valestrom/Samples/test/main/resources/arrays/immusa.vale)

    -   [~~[arrays/immusalen.vale]{.underline}~~](https://github.com/Verdagon/Vale/blob/master/Valestrom/Samples/test/main/resources/arrays/immusalen.vale)

    -   ~~[[arrays/mutusa.vale]{.underline}](https://github.com/Verdagon/Vale/blob/master/Valestrom/Samples/test/main/resources/arrays/mutusa.vale)~~

    -   ~~[[arrays/mutusalen.vale]{.underline}](https://github.com/Verdagon/Vale/blob/master/Valestrom/Samples/test/main/resources/arrays/mutusalen.vale)~~

    -   [~~[arrays/swapmutusadestroy.vale]{.underline}~~](https://github.com/Verdagon/Vale/blob/master/Valestrom/Samples/test/main/resources/arrays/swapmutusadestroy.vale)

-   ~~final program: little @ walking around~~

~~Deliverable Writeups (Verdagon)~~

-   ~~Rewrite of the \"heres what we\'re submitting and why we\'re
    > awesome\"~~

-   ~~Rewrite of the explanation for hybrid-generational memory~~

~~Valestrom Fixes and Upgrades (Verdagon)~~

-   ~~(first) Make the \"\" type field be \"\_\_type\" instead, so its
    > easier to auto-map from JSON~~

-   ~~Externs; make it so we can export a struct. Currently tree shaking
    > takes out anything not indirectly used by main, which means
    > without main, everything\'s stripped away.~~

-   ~~Rename name to optName~~

-   ~~Simple extern names; right now names are things like F(\"main\")
    > which is a little awkward to call into from JS.~~

~~Vale Code (Verdagon)~~

-   ~~Make simple benchmarkable program~~

    -   ~~Something that aliases/compares references a lot without
        > dereferencing?~~

~~New Memory Model (Verdagon)~~

-   ~~Fast mode~~

-   ~~Resilient Mode~~

-   ~~V0: generations~~

-   ~~First draft of the \"heres what we\'re submitting and why we\'re
    > awesome\"~~

-   ~~V2: genmalloc~~

~~Logistics (Verdagon)~~

-   ~~Set up a github repo under
    > [[http://github.com/ValeLang]{.underline}](http://github.com/ValeLang)~~

**Stretch Goals**

-   Java transpiler

-   Valestrom extern generics, to use the platforms\' native array
    > types, TBD

-   Inline simple imm functions like adding?
