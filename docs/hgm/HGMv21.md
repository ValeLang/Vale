Builds upon [[HGM
V20]{.underline}](https://docs.google.com/document/d/1m0WBG2mUB8IC6hF4x-9Offfzk2wCEtdIfOgFDkAYLzQ).

# So Far

Recall the Spaceship (also, we added Shield and Projector):

> sealed interface IWeapon { } \... // more subclasses
>
> struct Engine { fuel int; }
>
> struct Shield {
>
> // i64 for generation + tether
>
> projector! Projector;
>
> }
>
> struct Projector { thing \^Thing; } // thing on heap
>
> struct Navigation { antenna \^Antenna; } // antenna on heap
>
> struct Spaceship {
>
> // i64 for Spaceship\'s generation + tether
>
> weapon! IWeapon; // generation + tether combined with tag
>
> engine! Engine; // type-stable, needs no generation
>
> // i64 for generation + tether
>
> nav! Navigation;
>
> // i64 for generation + tether
>
> escapePods! \[3\]\^EscapePod;
>
> position! \[3\]int; // type-stable, needs no generation
>
> // i64 for generation + tether
>
> shield! Shield;
>
> }

We still need the generation+tether for nav and escapePods and shield.

# Problem

Firstly, we want to reduce the number of generations.

In solving this, we\'ll need to be careful:

-   We need to be able to produce a borrow ref from any normal ref, and
    > vice versa. This means that we need to know how to get to the
    > tether and the generation from both.

-   We\'d like to avoid where a reference to one part of a struct
    > forbids setting something in an unrelated part of the struct.

    -   Sometimes, we can actually skip a check on a set, if its
        > completely shape-stable.

# Approaches

## A: Do Nothing

This isn\'t that big a deal.

It\'s only a problem when all of these are true:

-   The inline thing is varying

-   The inline thing isn\'t an interface (so, its a struct or enum)

-   The inling thing has a final owning pointer

    -   Which we can\'t make varying

-   The inline thing can\'t be unique

-   We care about the size of the struct

That\'s a lot of things that have to go wrong.

## B: Array of Scope Tether Bytes

Hot take: we don\'t need inner generations, we just need inner scope
tethers. They can simply be one byte each. And, we can pack them nicely.

For the Spaceship example:

> sealed interface IWeapon { } \... // more subclasses
>
> struct Engine { fuel int; }
>
> struct Shield {
>
> // i64 for generation + tether
>
> bork \^Bork; // bork on heap
>
> projector! Projector;
>
> }
>
> struct Projector { thing \^Thing; } // thing on heap
>
> struct Navigation { antenna \^Antenna; } // antenna on heap
>
> struct Spaceship {
>
> // i64 for Spaceship\'s generation + tether
>
> // i8 for nav\'s tether
>
> // i8 for escapePod\'s tether
>
> // i8 for shield\'s tether
>
> // i8 for shield.projector\'s tether
>
> weapon! IWeapon; // generation + tether combined with tag
>
> engine! Engine; // type-stable, needs no generation
>
> nav! Navigation;
>
> escapePods! \[3\]\^EscapePod;
>
> position! \[3\]int; // type-stable, needs no generation
>
> shield! Shield;
>
> }

Note:

-   These are above the allocation, with the generation, not really in
    > the Spaceship object.

-   There\'s a shield.project tether. All the allocation\'s generations
    > are deeply here in the header, except for unions like weapon.

A borrow ref becomes:

-   64b Pointer to object

-   16b Positive offset to subtract from objptr to get to the i8 scope
    > tether

-   16b Positive offset to subtract from tether to get to the generation

We can add and remove from those two 32bs at the same time, **with some
cleverness.** For example, if we had a borrow ref to the Spaceship, it
might be \[0x120, 8, 8\] and if we wanted:

-   A borrow ref to the contained .engine which is 32 bytes forward,
    > we\'d add 32 to the objptr, and also 32 to the scope tether so
    > it\'s the same one.

-   A borrow ref to the contained .nav which is 40 bytes forward and has
    > the next scope tether, we\'d add 40 to the objptr and EITHER:

    -   Add 39 and 1 to the offsets to get 47 and 9, OR

    -   **Add (39\*2\^16 + 1) to the combined u64 spanning the
        > offsets.**

We only need to access the offset to the scope tether when we need to
tether an inner thing. We just grab it from the borrow ref (could
involve an &) and add it to the object pointer.

A normal ref becomes:

-   64b Pointer to object

-   16b Positive offset to subtract from objptr to get to the i8 scope
    > tether

-   16b Positive offset to subtract from tether to get to the generation

-   32b Generation

## C: Array Of Scope Tether Bits

These are all just booleans, so we could pack them all together if we
wanted to save more space.

We\'d initially have a limit of 16 scope tether bits, up near the top of
the object. They\'ll need to be next to the generation. (Perhaps if we
need more than 16, we can make a second generation)

A borrow ref is:

-   64b Pointer to object

-   16b Positive offset to subtract from object pointer to get to the
    > generation

-   16b Mask to see where the scope tether bit is in those 24 bits.

For example, if we had a borrow ref to the Spaceship, it might be
\[0x120, 8, 1\] and if we wanted:

-   A borrow ref to the contained .engine which is 32 bytes forward,
    > we\'d add 32 to the objptr, and also 32 to the offset so it points
    > to the same place it did.

-   A borrow ref to the contained .nav which is 40 bytes forward and has
    > the next scope tether, we\'d add 40 to the objptr, 40 to the
    > offset, and **left-shift the mask by 1.**

This actually makes it a bit easier to detect when there are any
references anywhere inside something. If we\'re about to change
something whose tether is at 0b000001000, and wanted to check that
nobody has tethers on bits 0b0000111000, we just need to multiply the
first by 0b111 (aka 7).

A normal ref becomes:

-   64b Pointer to object

-   16b Positive offset to subtract from object pointer to get to the
    > generation

-   16b Mask to see where the scope tether bit is in those 24 bits.

-   32b Generation

## D: Array of Scope Tether Bytes, by Depth

Approaches B and C have a \"parallel hierarchy\" of scope tethers, so to
speak.

If we could somehow establish a rule (perhaps in debug mode) that nobody
else should have a tether to anything else in the object, then we could
actually share some of these scope tether locations. Anything with depth
2 could share a tether, anything with depth 3 could share a tether, etc.

We\'re effectively implementing a max() here. assert(max(tethers..depth)
== myTetherDepth)

The big drawback is that a tether on something somewhere else in the
object could panic us if we set something over here.

## E: Use Top Byte of FFOP

This is like approach B, except the scope tether is in the top byte of
the FFOP. Should be compatible with the way the borrow reference was
working in B too.

Possible drawback is having to mask it out before dereferencing a FOP,
but that should be free on ARM with TBI! x86 might be trickier,
especially since it will defeat LLVM optimizations. We can try it on ARM
though, see what kind of speedups we can get. (Wait, what optimizations?
There might not even be any relevant optimizations to defeat. We should
try it anyway.)

The only weird part is that sometimes our tether is ahead of us. Perhaps
we could phrase our borrow ref as:

-   64b Pointer to object

-   16b Positive or negative offset to add to the objptr to get to the
    > scope tether

-   16b Positive offset to subtract from the scope tether to get to the
    > generation

The scope tether will have to be at the top end of the u32, so the
overflow bit doesn\'t mess with things.

Backup: If there\'s any padding in the struct, use some of the padding.
In fact, maybe some types can even offer up their padding to parents to
use as they need.

Note that this might mess with the === operation, since we\'ll have
pointers flying around with basically random upper bytes. Not too
terrible though.

## F: Every Object Has A Tether

There are three interesting kinds of inlines:

-   Unions: these have their own generation and scope tether, so not a
    > problem.

-   Structs: we can put the scope tether in some padding at the end, or
    > in a FOP.

-   Inline arrays: We\'ll likely need an extra byte afterward.

This will increase the space needed.

This can go into the top byte of an FOP if needed.

The benefit of this is that a normal ref and borrow ref don\'t need to
remember how to get to the scope tether; we just know because the scope
tether is in the same place for every instance of the type.

## G: Just Tether the FOP\'s Target

This problem only really arises when there\'s FOPs. So, fuck it, lets
just tether the thing the FOP is pointing at.

It\'ll be a cache miss, but they can avoid pretty easily (see approach
A).

## H: Interior Scope Tether (IST)

We can have two scope tethers in the object:

-   \"container tether\" for references that point directly to the
    > container (e.g. the Spaceship).

-   \"contents tether\" for references that point somewhere inside the
    > container (e.g. shield).

When we need to tether something inside, such as nav or escapePods or
shield, we tether the contents tether.

When we try to set anything inside the allocation, such as the shield,
we assert contents tether is 0.

A borrow reference would need these three things:

-   Pointer to object

-   Offset to generation

-   Offset to tether (to check when we set something)

When we get a borrow reference to something inside, such as to .shield,
we\'ll scope tether the contents tether.

Drawback: When someone tries to set something deep inside, like
.shield.projector, we hit an assertion fail. The only way we can set it
is if we say something like:

> set ship.shield.projector = \...;

Pretty big drawback.

We could bring back the whole \"unique scope tether\" thing to
compensate, perhaps.

## J: Manual Inner Generations And Locking

We require the user to put in their own generations for varying fields.
They\'ll be more cognizant of it, and would understand the limitations a
little more.

Luckily, this only becomes a problem when we have inlines that have
FOPs, which is probably a pretty rare need.

When they don\'t have a generation for a certain field access, it forces
them to use normal references.

Not gonna lie, this is actually a workable approach. It\'s very honest.

We wouldn\'t even need to do any of this locking for ECS, which is a
bunch of inlines but has no FOPs.

# Comparison

-   A: Do nothing isn\'t as good as J. J is more flexible, and its
    > complexity is opt-in.

-   B: Array of Scope Tether Bytes isn\'t quite as good as E, which
    > saves much more space.

-   D: Array of Scope Tether Bytes, by Depth isn\'t good, it has spooky
    > panics at a distance.

-   G: Just Tether the FOP\'s Target causes some cache misses in normal
    > code. An okay last resort.

-   H: Interior Scope Tether isn\'t very good, it doesn\'t let us modify
    > things freely.

-   F: Every Object Has A Tether could work, depending on how much
    > padding is used by most structs.

    -   Could be good, but will likely take some space.

    -   **A good fallback.**

-   J: Manual Inner Generations are pretty good.

    -   They\'re honest, give more control to the programmer, make them
        > aware of the problems.

    -   **A good fallback.**

-   C: Array of Scope Tether Bits could be good.

    -   Drawback: Requires some instructions:

        -   Borrow inline varying thing needs one bitshift

        -   To scope tether, need to load, b-or, store

    -   Benefit: Can check multiple tethers at a time when setting.

    -   **Not bad, pretty simple.**

    -   Might be even better if we had three separate things: obj ptr,
        > gen ptr, tether mask.

-   E: Use Top Byte of FFOP could be good.

    -   Drawback: Needs masking to dereference owning heap pointer,
        > unless theres padding.

    -   Drawback: Requires some instructions:

        -   Borrow inline varying thing needs an extra add

        -   Borrow inline final thing needs an extra add

        -   To get tether pointer, need shift, zext, add

    -   Might be better to just have three separate pointers: obj,
        > tether, gen. **Then, no real drawbacks.**

I\'d say **C is the most promising.** Definitely worth exploring E more,
as its a close second.

# Notes

It\'s funny, we only have the scope tether info in the borrow ref so
that we can later turn it into a normal ref, and then from the normal
ref, get a borrow ref that scope tethers *something*, lol.
