
# Restrict Ptr Param For Next Generation (RPPFNG)

We *could* invoke a whole pseudo-random number generator every time we allocate an object.

However, it could be a better compromise to just increment a central number. This central number could be global, or thread-local, or even live on the stack.

Right now, it lives on the stack, and we pass a restrict pointer through to every call. This is why we do so many +1s when accessing arguments.

Instead of simply incrementing it, we use an arbitrary prime number, see PRGNA.


# Pseudo Random Generation Number Addends (PRGNA)

Instead of incrementing the next generation number every time we use it, we add an arbitrary prime number to it.

A given place in the code has that arbitrary number hard-coded. If that instruction is run twice, it'll add the same amount. In other words, the arbitrary number is chosen at compile time.

These prime numbers are in a big table in the backend.
