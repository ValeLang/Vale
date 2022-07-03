There are some challenges when a controller on the outside is trying to
listen to something on the inside. For example, in Incendian Falls, a
UnitPresenter has a unit reference, and it wants to display a death
graphic at the location when the unit dies.

In Incendian Falls, when a unit was deleted, if the controller had a
strong reference to the unit, the wingman would have panicked.

If instead the controller had a weak reference to it, there would be no
panic. However, to show the death graphic, it would try to read
unit.location, which would give a nullpointerexception because the unit
no longer exists (unit was a weak pointer).

Some solutions:

-   Have unit.alive, and then sometime in the future, in another request
    > perhaps, we could delete unit.

    -   This was super unfortunate because \*everyone\* had to check
        > whether the unit was still alive, before they did something.
        > It was basically reimplementing weak pointers. Also, the code
        > that normally is in the destructor was moved to onDie, which
        > strikes me as wrong in some way. Destructors are god\'s gift
        > to man, after all.

-   Change it so the unit\'s data is still around, but then do a cleanup
    > afterwards. Something being deleted will broadcast not a
    > DeleteEffect, but instead a\... DestructEffect or something, which
    > means it will be cleaned up soon.

    -   However, this is weird because it implies we can still get
        > references to a dead object, and read from it. If we keep
        > those references around, surely the wingman will panic.

-   Superstructure notifies the presenter right when the unit goes away,
    > before the request even finishes.

    -   However, it would mean the outside world sees an inconsistent
        > view of the superstructure. It\'s also not possible with
        > firebase.

-   In the deletion effect, we could send an incarnation.

    -   However, that only contains this tiny struct\'s information. If
        > the location was in a field of a member struct, it wouldn\'t
        > be enough information.

-   Have a separate receiving/client superstructure, that would play all
    > the effects that happened on the source/server one. As we receive
    > effects, we won\'t apply them immediately. We\'ll look at all the
    > deletion effects, and notify anything that\'s about to be
    > destroyed.

    -   Pretty high cost.

-   Have a separate receiving/client superstructure, that would play all
    > the effects that happened on the source/server one. As we receive
    > effects, apply them to a \"preview\" superstructure, do a diff,
    > and see what\'s going away, and notify them that they\'re going
    > away. Then we apply all those effects to the real one.

    -   Pretty high cost.

    -   Note that we can\'t just swap the preview contents into the root
        > superstruct, because we need to check all borrow pointers and
        > panic if we violated anything, and check all weak pointers to
        > null things out. Though, if we turn off wingman, and if we do
        > weak pointers in a way that doesn\'t require any action when
        > the target is deleted, we **could** do this.

-   Have weak pointers, and if the presenter needs to remember anything,
    > it will have to cache it itself manually.

The only approaches that dont risk inconsistencies are:

-   Send an incarnation in the delete effect.

-   Have an entire other copy of the superstructure.

-   Use weak pointers and have caches.

Sending an incarnation is a very tiny patch over a big hole that will
break eventually, it\'s just not enough information. In top of that, the
weakptr/cache approach is a superset of it; it can just request an
incarnation every time the unit changes.

So, we\'re left with two approaches:

-   Have an entire other copy of the superstructure.

-   Use weak pointers and have caches.

The first one is more compatible with strong references, we can
appropriately delete them at the right time. It\'s also free when we
have client and server on different machines. The weakptr+cache approach
is a bit more scrappy, but arguably faster if everything\'s on the same
machine.
