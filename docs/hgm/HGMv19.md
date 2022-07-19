Builds upon [[HGM
V18]{.underline}](https://docs.google.com/document/d/10lVQlkeducyvpbwMtSYLb1DbUGjs8rBvFcTEjS-_aOw/edit#heading=h.1k4427exfc55).

When we have a:

struct Spaceship {

weapon Weapon;

}

fn moo(ship &Spaceship) {

weapon = ship.weapon;

}

one would think that weapon is a fat pointer of:

-   Pointer to weapon

-   Offset to weapon\'s gen

-   Weapon\'s metadata

-   Weapon\'s previous tether value

That would be unfortunate because when we do ship.weapon, we\'re not
just reading ship, we have to reach all the way into weapon to get its
generation.

Nay! Instead, it should be:

-   Pointer to weapon

-   Offset to weapon\'s gen

-   Ship\'s metadata (so we can get its current tether value)

-   Ship\'s previous tether value

In other words, just keep checking at the original ship\'s generation.
Theyre part of the same hierarchy, so it should work fine.

This only applies to things on the stack. When fields or elements, we
store them like we did before. That also means that when we store them,
we have to go fetch the object\'s actual generation. Shouldn\'t be too
bad.

This shouldn\'t apply to normal references, because we might want to
destroy the containing Spaceship to harvest its owning reference to
weapon. Normal references should keep doing what we were doing before.
