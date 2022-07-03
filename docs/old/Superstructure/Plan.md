phase 0:

-   Figure out if incarnations are immutables are mutables. i think they
    > might have to be shared mutable\... yikes. it\'s weird, because
    > we\'re mimicking single ownership. that\'s what makes it okay.

-   Make it so only certain realms can be journaled.

    -   Things inside can only point to things outside via weak
        > pointers.

    -   In the future, journaled areas might be able to have strong
        > pointers to other journaled areas\... but for now, let\'s
        > disallow it.

    -   This will solve superstate; all the superstate should go above.

    -   This will solves transients like IImpulse.

-   Make it so we can store incarnations. They\'re immutable so they can
    > go anywhere. Also want to store a rootincarnation somehow.

-   Make it so we can store roots outside of the time tracked areas?

-   make it so a revert uses the current incarnation number; make it
    > work like an assignment.

a struct must opt in to being chrono, so that we know to look into the
chronotree map of ids to current objects, and to make it know it\'s to
be flattened.

if we\'re somewhere inside Database, and we try to access something, how
do we know the root is at Database.game?

well, it\'s in the fat pointer i suppose\...

how do we create something if we dont know where to add it? in other
words, we need the root whenever we want to create something.

yeah, we should make there be only one chronobase per document.

but then, how does that work with forks and stuff?

we apparently need to pass around a factory, and every chrono parameter
needs to be a fat pointer with the root in it. later on, if we do the
realm thing, we can optimize that away.

phase 0:

-   generic-ify the maps, sets, and lists.

phase 0.5:

-   figure out the proper organization for all the superstructure
    > functions, and where they should go. its also awkward that we have
    > extensions everywhere.

phase 1:

-   put in some sugar so it isnt so verbose

-   make it so Snapshot() doesnt return an incarnation, because you can
    > modify an incarnation. return something readonly.

-   make sure we do defensive copying everywhere we have to

-   (chrono) make it so we can downcast interfaces

-   make a superstructure pass checking for things owned by multiple
    > people

phase 2, optimize.

-   the revert does an O(n) comparison of sets\... see if we can get it
    > down to O(d). perhaps if we ordered them by last modification?
    > perhaps every root can maintain a linked hash map, ordered by when
    > last modified\... we could even compare to see if its more or less
    > than O(d) and choose the more efficient thing. we\'d preferably
    > not broadcast two changes for one property though, thatll likely
    > require a hash map.

-   instead of having hash maps, use a chronotree up top

-   add an ID-\>obj cache.

-   when we revert, we do a diff\... what do we do to compare
    > immutables? recursively comparing everything will get insanely
    > expensive. probably best we let the effect handler handle that and
    > be smart if they want. if we use interning and hash codes to short
    > circuit deep equals, it could prove effective.

phase 3:

-   move from C# to C++CLI if it would help us slowly transition to a
    > VIL based one instead

phase 4:

-   compile to VIL instead

maybe someday:

-   (chrono) have marking an interface as sealed, to expose visitors for
    > it

-   chrono: perhaps use object pools in the root somehow? only in native
    > tho\...

-   (chrono) for bunches, perhaps if it gets to be more than 8 (size of
    > cache line) or 16 (two cache lines) we switch to something more
    > efficient? right now, to query based on interface, it loops
    > through the entire thing. in fact, for bunches, we should
    > preallocate the result vector (its just a bunch of size adds)

-   capture version number in the sslog

when we revert to a past incarnation, we should NOT reset our version
number to the past. if we do that, when we modify something, we could be
modifying something that has been snapshot\'d (since the version numbers
are equal).

we \*must\* keep our current version number, and we \*must\* restore old
incarnations as their past versions. or, we can update our current
incarnation to match the old (we do that for structs)

we have a pretty fundamental problem here. lets say we have a draxling,
which takes two actions while the player takes one. if we have one
request go out to firebase, and inside that transaction we do these two
things, we\'ll only receive the final effect from firebase. this is
probably nice if we want to just display the current values\... but not
good for describing what happened inbetween.

some ideas:

-   we can take snapshots between. that way we can slice the world so
    > people can see what happened before and after. the drawback is
    > that we\'re slicing the entire world.

-   we can more heavily lean on events broadcast at the node level.
    > instead of inferring a hop from a change in position, we would
    > watch out specifically for a hop event. these events can either be
    > a collection on the firestore side, or an extra struct hanging off
    > the unit itself (dangerous because could be more than a meg). this
    > comes with a burden of describing the order of events. two
    > solutions to this:

    -   put the events on some parent object like the Level or the Game

    -   have a counter for the events. the \"next\" variable could live
        > on a higher object like the level or the game.

i kind of like the latter, with the counters.

also, we can creatively cram things into a side collection next to the
node if it gets too big, or if its small enough, put it into the struct
itself.

Consider making only the current level tracked. Not sure how. Not sure
if worth it, either. We can just throw away all past game incarnations
from before we jumped to this level. But is there a bigger problem? If
we just want to journal a specific tiny subset of the game, do we have
to snapshot the entire game? Yes, because that tiny subset will
potentially have references to outside it. But if we make it an entire
realm on its own, then we could do it. In other words, Level could be a
realm on its own. Let\'s save this for later\...
