Builds on [HGM V12](HGMv11,12.md), which specifies how the stack works.

This talks about how we can share generations in the heap too!

## For Single Objects

Have 128-byte chunks for everything. can fit a generation and three
40-byte objects in there! **Those three things share a generation.** And
a tether.

Each slot in that chunk cannot change class. One could be a Spaceship,
one a Marine, one a Base, but they can never be reused for something of
a different shape. When all three are dead, and there\'s no tether, we
are allowed to increment the generation number and change the shape of
this chunk.

**Downside: this could lead to *more* fragmentation;** if we allocate a
ton of chunks for e.g. 40-byte Spaceships and then keep every third one,
we can\'t free anything yet. Even worse for small objects, they keep
their entire chunk alive, yikes.

## For Arrays

We can have a generation every N bytes here too! This would make a
\"span\". For example, 491520 bytes (480kb, or 3840 chunks wide, or
\<2\^12 chunks wide, which means need max 12 bytes for offset).

When we index, somehow take it into account. Do (index / 2\^11) to get
the beginning of the span, read the generation. Then add 16 bytes past
that, then add (index % 2\^11) to that to get the element.

To support random access, we would reserve 8 bytes before every object.

## Thoughts

Not sure if this is a good idea, because it temporarily leaks a little
more memory. But it seems to open up a new dimension of HGM. We can
share generations for multiple objects!

In a way, we did this for stack frames, which worked well because we
could pretty reasonably tie the lifetimes of these things together (into
a block).

I wonder if we can situationally apply this in other ways? How about if
we can statically determine that some objects contain other objects! If
we see that we\'re allocating an object into another object, we can
signal malloc to combine them into the same 128b thing. This will be
super easy if it\'s in a constructor, or if we\'re passing something
into a constructor arg. We can use a bit in a destructor to signal that
this sub-object is being destructed and shouldnt be put on the free
list.
