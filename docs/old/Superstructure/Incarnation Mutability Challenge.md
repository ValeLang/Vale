The question: are chronobase incarnations mutable or not?

Lets say:

-   We have nodes A1 B1 C1 in a chronobase.

-   snapshot 1 to 2

-   modify C1 to C2, what\'s mutable and what\'s not?

Well:

-   Before the snapshot, A1 B1 C1 are conceptually mutable.

-   After snapshot, A1 B1 C1 are conceptually immutable.

-   After modify, C2 is definitely mutable.

So, the snapshot made A1 B1 C1 from mutable to immutable.

This is scary, because the root still has references to A1 B1 C1 as
mutable in its map. The snapshot has references to A1 B1 C1 as immutable
in its map.

Since we can hand A B C around, we **must make sure** that A B C are
never modified, and if threading is involved, incremented atomically.

**Basic approach:**

Every root actually has two maps for every kind of node: mutables and
immutables. current and past.

HashMap\<Int, ImmutableAIncarnation\> pastAIncarnations;

HashMap\<Int, MutableAIncarnation\> currentAIncarnations;

Whenever we snapshot, we loop through all currentAIncarnations, make
immutable versions of them, put them in pastAIncarnations.

However, chronobases need to be super efficient. There\'s a better way.

Let\'s have shared mutable objects. They\'re only for compiler plugins,
and \*maybe\* exposed as power user syntax or something. It should be at
least awkward for the regular user to use. Maybe they have to at least
suppress a warning to use them. enable by default a tracing thing that
makes sure memory is always a directed acyclic graph.

**Fast and Loose Approach:**

keep a map of incarnations. anything whose version == the current
version is **treated** as a shared mutable object. anything before
current version is **treated** as an immutable object. **this is risky**
because we can easily get it wrong, but it would be ridiculously fast;
snapshotting is immediate.

VIL needs separate instructions for all these operations, so that we
have this fine level of control.

**Slightly Safer Approach:**

Have a freeze() function for shared mutables. that turn them into
immutables. reference count must be 1 to do this.

We augment the fast approach: Every time we snapshot(), we loop through
everything in the chronobase looking for things from this version. For
each one, we freeze() it to make sure we never modify it.

We definitely want the Fast and Loose approach. The question is, for
development and sanity, do we want Basic or Slightly Safer? I think we
probably want Slightly Safer, since it\'s closer to Fast and Loose in
its implementation.
