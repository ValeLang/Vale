Builds upon [[HGM
V19]{.underline}](https://docs.google.com/document/d/1dDX5TjU9hpvG3hK06xds2SU-Kw-lUGsP5RtbBKzUVxU).
TL;DR: Add inner generations, which then simplifies borrow references.

HGM V19 has the insight that we can rely on the ancestor\'s scope
tether, and don\'t need to obtain new scope tethers on anything as we
borrow final members. It turns out, we don\'t need *any* information
about the ancestor scope tether. We thought we needed it so that we
could check if we were the only reference to anything in here, but
that\'s not true, we no longer need to check that we\'re the only
reference, because of **inner generations**, proposed by [[HGM
V18]{.underline}](https://docs.google.com/document/d/10lVQlkeducyvpbwMtSYLb1DbUGjs8rBvFcTEjS-_aOw/edit#heading=h.1k4427exfc55)\'s
GFFOP and IGFCVI and IGFIL.

# Basic Idea

We\'ll have an inner generation for any inline varying struct,
interface, or static array.

> sealed interface IWeapon { } \... // more subclasses
>
> struct Engine { fuel int; }
>
> struct Navigation { antenna \^Antenna; } // antenna on heap
>
> struct Spaceship {
>
> // i64 for Spaceship\'s generation + tether
>
> // all these are inline
>
> weapon! IWeapon;
>
> engine! Engine;
>
> nav! Navigation;
>
> escapePods! \[3\]\^EscapePod;
>
> position! \[3\]int;
>
> }

There will be 5 inner generations added:

> struct Spaceship {
>
> // i64 for Spaceship\'s generation + tether
>
> // i64 for generation + tether
>
> weapon! IWeapon;
>
> // i64 for generation + tether
>
> engine! Engine;
>
> // i64 for generation + tether
>
> nav! Navigation;
>
> // i64 for generation + tether
>
> escapePods! \[3\]\^EscapePod;
>
> // i64 for generation + tether
>
> position! \[3\]int;
>
> }

There are, of course, a lot of generations. Below sections show how we
might simplify some of them away.

# Simpler Borrow Ref

If we had this function:

> fn zork(ship &Spaceship) {
>
> set weapon = Flamethrower(\...);
>
> }

We only need to check ship.weapon generation. We don\'t need to make
sure we\'re the only reference to the ship.

We never have to make sure we\'re the only reference, ever again. We
don\'t need so much in the borrow reference.

Our borrow ref really only needs to be:

-   Pointer to weapon

-   Offset to weapon\'s gen

Much simpler!

# Combined Generation for IVSIs (CGIVSI)

Let\'s focus on the weapon field (plus Spaceship\'s gen and tether, for
reasons):

> struct Spaceship {
>
> // i64 for Spaceship\'s generation + tether
>
> // i64 for generation + tether
>
> weapon! IWeapon;
>
> \...
>
> }

Under the hood, this\'ll look like:

> struct Spaceship {
>
> int64_t spaceshipGenAndTether;
>
> int64_t weaponGenAndTether;
>
> struct {
>
> int64_t tag;
>
> union {
>
> \... // possibilities
>
> } value;
>
> } weapon;
>
> }

**Basic idea:** Let\'s combine weaponGenAndTether and weapon.tag.

Note that we can\'t just use the tag as a generation, because if
Spaceship is an element in an array, and we pop it, then push another,
it will have the same tag even though it\'s a different Weapon instance,
which causes problems in the presence of FOPs.

Some options:

-   If tag is an itable pointer, we **xor** it with
    > spaceshipGenAndTether\'s generation (be sure to mask out the
    > tether). To get the itable, we xor the generation again.

    -   It\'s very convenient that we use fat pointers for interface
        > references. If we did this in a c++ish way, xor\'ing that
        > itable tag would make it impossible to call virtual functions
        > on it.

-   If tag is just a small (u8 or u16) integer, we can put it next to
    > the u48 generation. Be sure to leave room for the 1 bit scope
    > tether though.

Let\'s assume the latter for now, it\'s a bit faster. We could fall back
on the former if we have too many variants perhaps.

Now, our Spaceship struct has one less i64:

> sealed interface IWeapon { } \... // more subclasses
>
> struct Engine { fuel int; }
>
> struct Navigation { antenna \^Antenna; } // antenna on heap
>
> struct Spaceship {
>
> // i64 for Spaceship\'s generation + tether
>
> weapon! IWeapon; **// generation + tether combined with tag**
>
> // i64 for generation + tether
>
> engine! Engine;
>
> // i64 for generation + tether
>
> nav! Navigation;
>
> // i64 for generation + tether
>
> escapePods! \[3\]\^EscapePod;
>
> // i64 for generation + tether
>
> position! \[3\]int;
>
> }

# Use Type Stability for Less Inner Generations (UTSLIG)

There are cases where if we use-after-free, we don\'t incur any risk.
For those, we need no inner generations.

In the example struct above, the deeply type-stable members are:

-   engine, because there\'s just an int in there, there will always be
    > an int in there, as long as this spaceship is alive.

-   position, for the same reason.

(nav and escapePods aren\'t deeply type-stable, because if we replace
them, their final owning points might indirectly have different final
inline sealed interface members.)

So, we can remove those. Now we have:

> sealed interface IWeapon { } \... // more subclasses
>
> struct Engine { fuel int; }
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
> }

There\'s one risk here: if we try to borrow something inline, we\'ll be
scope tethering its parent:

> fn zork(ship \*Spaceship) {
>
> pos = &ship.position;
>
> }

This actually scope-tethers the containing ship, since the position has
no scope tether. The user might need to be aware of this.

Interestingly, this doesn\'t matter if we\'re just changing the
position:

> fn zork(ship &Spaceship) {
>
> set position = \[\]\[3, 4, 5\];
>
> }

because we\'re not doing a tether check there anyway, because it\'s
type-stable.

# User Workarounds

In the above Spaceship, we still have extra generations for nav and
escapePods, because they aren\'t deeply type-stable, because if we
replace them, their final owning points might indirectly have different
final inline sealed interface members.

There are two things the user could do here:

-   Mark them as unique, which would obviate their generations.

-   Make the contained final owning pointers be varying instead.

# Inline Struct Generationed FFOP (ISGFFOP)

(slightly adjusted from GFFOP)

If we wanted, we can do something *very weird*. We can xor the final
owning pointers with their container\'s generation, and have that serve
as the inner object\'s generation. It can also have its last bit serve
as a scope tether.

However, that means that whenever we have *any* reference to *any*
struct or array, we can\'t just use their owning references as is, we
have to xor and bitwise-and them first, even if the containing region is
immutable. And medium-term, it will defeat the optimizer a lot.

Probably best not do this part.

# Notes

i wonder if x! can mean unique mutable, and x!! can mean shared
mutable\...

or maybe just for inlines?

it gives them a generation. hmmm.

it also means we can pass them to &uni things, good for iterator
methods. huh.
