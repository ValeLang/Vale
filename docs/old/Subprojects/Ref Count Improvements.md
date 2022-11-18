Valence has the potential to be the fastest RC'd language ever (erlang
could have, but it went the GC route instead, boo).

There was a paper a while ago that talked about ref count coalescing,
which removed 99% of ref count changes.

Maybe we can do it across function boundaries too, kind of like we
planned with the sidekick thread.

When moving, we shouldnt increment or decrement.

We can also track unique references. These, we can increment/decrement
atomically.

We can track that things have come from arguments, and not
increment/decrement them until they escape the known lifetime of those
arguments.
