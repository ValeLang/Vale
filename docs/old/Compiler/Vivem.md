**Vivem Weaks Keep Objects Allocated** (VWKOA)

In Vivem, a weak reference will keep an object allocated, even though it
might already have been killed by its main owning reference. Itll be in
an undead state where all its members are dead.

Similar to how Resilient Mode\'s constraint refs work.
