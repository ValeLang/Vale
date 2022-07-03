## Voids Dont Need Discarding

(VDND)

The templar Discards anything when the reference goes away, even Void.

(We don\'t say \"it has to discard everything except for primitives\"
because strings can be rather large and probably refcounted)

Just for simplicity, the templar will actually discard anything even if
it knows it\'s void, because it prefers to be consistent and not
special-case void (which would lead to templating problems).

Even hammer does have voids flying around, as zero byte objects.
However, when they get to the end of their life, they just disappear,
rather than being Discarded.

In other words, hammer turns Discard2 into DiscardH for everything but
void.

The hammer has two instructions that return nothing (as opposed to
Void):

-   ~~Return~~ Made return return a Never.

-   Discard
