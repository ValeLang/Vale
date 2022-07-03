Metastate is state that\'s in the document, but not journaled by the
chronobase. For example, in incendian falls (where metastate was known
as \"superstate\" but i want to save that term for something cooler
which we\'ll actually have) we had a LiveUnitByLocationMap, which served
as a cache of location to any live unit. We also had a
timeShiftingState, and a player directive there. These were needed in
the superstructure to do various interesting things, but they didn\'t
make much sense to track. We also had a LevelSuperstate which had some
caches for the level.

These things all lived outside the chronobase. Considered making it so
that they could live inside the chronobase, and only certain complete
subtrees of the data were chrono-tracked. For example, Game would own a
list of Level which would each own a ChronoLevel. But, if we tried to
revert, we wouldn\'t know where to put a resurrected ChronoLevel. The
containing Level might not even exist!

For that reason, a chronobase must contain only journaled nodes.

A document however may contain a chronobase. We\'d put the metastate in
the document, just not inside the chronobase. Also, this all means that
we can have multiple chronobases in the document.

Notes:

Let\'s consider:

-   Non-chrono root struct Game.

-   Game contains non-chrono struct Level.

-   Each level contains a chrono struct JournaledLevel.

Imagine:

-   We have three Level, and each of course contains a JournaledLevel.

-   Then we snapshot.

-   We then delete one of the Level.

-   We try to revert.

One of the JournaledLevel is now orphaned!

This means we need a restriction: whatever contains any chronobase root
must outlive the **entire** chronobase.

for now, lets just say that the root can contain a superstructure.

in the future, we might expand it to be any immortal object can contain
a superstructure.

sometime after that, we could expand it to say that any object that
outlives the chronobase can have a reference into it.

I\'m still unclear if we can have for example Game containing two
journaled fields that are journaled together. The alternative is that
theyre completely separate chronobases. Must consider.

we can always have borrow references from elsewhere\...

we can\'t make it so there are holes in the chronobase; we cant make
certain structs exceptions.

we have two options:

-   have an overarching chronobase class, which can contain structs or
    > chronodes. structs can contain structs or chronodes. chronodes can
    > only contain chronodes.

    -   we\'d have a non-chrono Game, containing non-chrono Levels, Game
        > could own the ChronoGame, Level could own the ChronoLevel.

    -   in reverting, if a ChronoLevel goes away, we\'d have to somehow
        > know to kill the containing Level.

    -   this is scary because if the outside world doesn\'t play along,
        > then when we revert, we\'ll have an orphan ChronoLevel that\'s
        > owned by nobody.

-   make it so each chronobase is completely distinct, separate,
    > isolated.

    -   we\'d have a non-chrono Game, containing non-chrono Levels, Game
        > could own the chronobase, Level would have a strong or weak
        > reference to the ChronoLevel.

    -   in reverting, a ChronoLevel would go away. if the Level has a
        > strong reference to it, we need to somehow know to kill it.

i think we have to go with the latter. the orphan problem is just too
real.

we still have the problem of strong references outside pointing in,
being broken by reverting. for that, we have solutions:

-   use weak references, and react to delete events, like in incendian
    > falls

-   use strong references, and conjure a \"preview\" of the reverted
    > state to compare against. before the revert, destroy controllers
    > for the dying things. after the revert, make controllers for the
    > new things. we would do this via a diff.

-   instead of a preview, just notify things that are about to die, via
    > list of creations and deletions or something.
