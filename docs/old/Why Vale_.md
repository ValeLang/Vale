# Speed, Safety, and Determinism with Ease

Vale is aims to be the fastest memory-safe language in existence, while
still being easy to use. It aims to be high-level enough that you dont
need to worry about the details, smart enough to be blazing fast, and
give you enough control to write optimal programs.

It does this by offering a spectrum of built-in **memory regions,**
which we can apply to different parts of our program.

Vale\'s default region is [[Hybrid-Generational
Memory]{.underline}](https://vale.dev/blog/hybrid-generational-memory)
(HGM), which is 100% safe, faster than RC and GC, and reclaims memory in
a timely and deterministic manner.

We can make any function use alternate regions, such as the arena
region, by adding region annotations:

> fn **findNearby**\<\'x, \'r\>(**map** \'r Set\<\[int, int\]\>,
> **start** \[int, int\], **radius** int) Set\<\[int, int\]\> \'x {
>
> **open** = Map\<\[int, int\], int\>(start, 0);
>
> **nearby** = Set\<\[int, int\]\>();
>
> while (\[**d**, \[**x**, **y**\]\]) = open.iter().remove() {
>
> nearby.add(\[x, y\]);
>
> if d + 1 \<= radius {
>
> if map.has(\[x - 1, y\] and not nearby.has(\[x - 1, y\]) {
> open.addIfNotPresent(d + 1, \[x - 1, y\]); }
>
> if map.has(\[x + 1, y\] and not nearby.has(\[x + 1, y\]) {
> open.addIfNotPresent(d + 1, \[x + 1, y\]); }
>
> if map.has(\[x, y - 1\] and not nearby.has(\[x, y - 1\]) {
> open.addIfNotPresent(d + 1, \[x, y - 1\]); }
>
> if map.has(\[x, y + 1\] and not nearby.has(\[x, y + 1\]) {
> open.addIfNotPresent(d + 1, \[x, y + 1\]); }
>
> }
>
> }
>
> ret nearby;
>
> }

and to call it:

> nearbyLocs = findNearby\<arena(4gb)\>(map, \[3, 5\], 5);

With this, all allocations inside findNearby will be in the arena region
\'x , and the resulting nearby Set\<\[int, int\]\> will be copied from
\'x to the calling region \'r .

Besides **hgm**, there are three other regions:

-   **arena:** reserves a contiguous slab of user-specified maximum
    > address space, and lazily allocates memory from it in a linear
    > fashion. If it runs out of space, it halts the program.

-   **collarena:** like arena, but when it runs out of space, as a last
    > resort it deterministically traces through the contained objects
    > to find gaps, organizes them into free lists to start pulling from
    > instead.

-   **typepool:** has an arena per type, and reuses freed slots in that
    > arena.

To optimize a program, one could use **hgm** or **typepool** for their
long-lived memory, and **arena** or **collarena** for their more
temporary memory.

By making it incredibly easy to use a spectrum of regions, by simply
adding region annotations, we can enable much faster code where other
languages require invasive refactoring.

# Examples

## Game Development

When an enemy is considering various movements, it can consider each
movement in its own \"arena call\" like above. Then it can compare all
the potential movements in another arena call. Since arenas are \>100x
faster than malloc-based strategies, this can be a considerable speedup.

If we\'re worried about sometimes going over our estimated memory usage,
we\'d use a **collarena** instead, whose tracing is deterministic and
only happens if we overshoot our estimate.

If we have the memory budget, then we can store the world\'s state in a
**typepool** region. Many games do this already manually, in the form of
ECS architectures.

## Server Development

If a server is handling 10000 requests per second, each request\'s call
could use the **arena** region, speeding it up by 100x.

If an arena call uses too much memory, then one can switch to a
**typepool** region, which more efficiently reclaims memory yet avoids
calls to malloc.

## App Development

Similar to games and servers, mobile apps react to requests from the
user, and a lot of the used memory is temporary. That memory can be in
an **arena** region.

## Web Development

When Javascript is too slow, we can hand off our calculations to Vale to
go much faster. Like app development, we can use the faster regions to
serve more temporary actions from the user. If we have more persistent
state, we can store it using **typepool** or **hgm** regions.
