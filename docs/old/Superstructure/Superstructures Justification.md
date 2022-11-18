Plain superstructures are awesome because:

features:

-   they can be serialized automatically. saves!

-   time travel!

-   it's a convention. people instantly know how your system behaves if
    > they see it's a superstructure.

prevention:

-   they enforce no reentrancy.

-   you can add constraints to them and theyll be super efficient

-   they enforce good design

-   enforce the M in MVC, having that guarantee makes things so much
    > better

-   determinism! no heisenbugs!

debugging:

-   changes can be logged for debugging

-   can browse the effects of every request, makes for awesome
    > debugging. can even add constraints without messing with the
    > replays!

-   can see the data structure change in real time, all firebase-like.

maybe:

-   automatic parallelization for pure functions, for speedup?

-   can be compiled to other runtimes? sql and firebase?

-   we can even have a specific superstructure debugger?

\"Some games like Rome 2 Total War have a client-side database (usually
an embedded version of SQLite) which stores all the values and
properties of every object in the game to make it easy to adjust outside
of the engine.\"

Databases are much easier to browse and understand and query. You can
dump the entire database to a file and use it. You can even export it as
JSON, fiddle around with it in the browser, and reimport it.
Unfortunately, a database won't perform as well as custom-made
structures in memory. Radon gets rid of that distinction; it takes your
regular structures, and mirrors them into the database in a sane,
logical way. You get the benefits of a database, with the speed of
native datastructures.
