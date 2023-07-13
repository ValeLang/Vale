Builds upon [HGM V21](HGMv21.md)'s option C.

# Problems

## Problem 1

A borrow ref is made of three things:

* pointer to the object
* offset to its generation/tether bits.
* A bit mask saying which tether bit we're currently looking at.

However, if the generations don't match, we're still pointing at an
object. Then if we try to tether something in it (either now, or later
on when we try to tether an innard), we're writing scope tether bits to
someone else's object. Not great!

The solution is to make a borrow ref these three things instead:

* Pointer to the object
    * If gens match, points to the object.
    * If gens dont match, is null.
* **Pointer** to its generation/tether bits.
    * If gens match, points to the object's generation/tether bits.
    * If gens dont match, points to a \"throwaway\" byte on the stack, or a global or something, whatever as long as its not someone's object's metdata.
* A bit mask saying which tether bit we're currently looking at.

Now, if the generations don't match, we're not writing tether bits into someone else's objects.

(Alternative: the object pointer could be null, and the offset could
actually be super large, pointing at wherever we want, perhaps to the
same throwaway byte we have in mind. A pointer is less instructions
though, so it's slightly better.)

## Problem 2

Not a problem, really. More of an opportunity.

We can bring back the undead cycle, **for heap allocations.** If we
deallocate something, and someone's still tethering part of it that's
stable (not an inline enum) we can throw it on the undead cycle.

This can be opt in, and maybe we can call it resilient mode. It won't
help any inline data, we should be clear about that.

Not necessary, but an interesting improvement to consider.

## Problem 3

A scope tether will do a generation check even if we don't expect the
object to be alive.

That means that we might do a generation check on released memory.

The solution: Don't unmap address space. Make it point to some
underlying randomly filled page.

## Problem 4

If we ever want to drop a borrow ref out of order from how it was
acquired, we might need to redo all the tethers we have since acquiring
it.

Or, rather, redo all the tethers that could possibly be pointing at the
same object. This means we have to be careful about non-sealed interface
tethers, since they might be pointing at the underlying object.

## Problem 5

Regular generational memory has a 1/2\^32 chance that we have a
collision. An erroneous program has a 1/2\^32 chance to have mysterious
behavior, otherwise it will be detected and crash. It's a 1-(1/2\^32)
chance that we'll just halt the program. **However, if you do
everything right, your program won't crash.**

Imagine if we *don't* halt the program, and perhaps just let the
program continue. We'd get some memory corruption. That would be bad.

This badness is actually happening elsewhere in the design: scope
tethering. Recall that if we have a non-owning ref in a local, and the
generations match, it does a scope tether and becomes a borrow ref.

There's a chance that some of them might actually hit that 1/2\^32
collision, and write a scope tether bit, continue, and not crash. This
might happen many times over the course of a long running program. The
longer it runs, the more chance of this corruption happening.

In other words, a scope tether might be writing tether bits to a random
other object. There's a 1/2\^32 chance every time we scope tether a
dead object. If we tether (and then don't access) a million objects in
a row, we'd have a 0.03% chance of data corruption happening.

If we have a tight loop that scope tethers something and doesn't access
it (and therefore doesn't crash on most runs), the program will
accumulate data corruption.

With this problem, we must say **even if you do everything right, Vale
might corrupt your data.**

It's hard to gauge the effect of this corruption. Maybe it's not a
problem. Maybe statistically, it will never really happen, or it's
vastly more likely to be detected and halt than to corrupt data. For now
though, we'll assume it's a problem.

# Problem 5 Approaches

The solutions for problem 1 and 2 are above, this is for problem 3.

## A. Explicit Hard Tethering

We previously allowed tethering a dead object, and then not crashing
until later. We could call that a \"soft tether\".

A \"hard tether\" is when we actually do an assert that the object still
lives. That way, if there's a problem, we'll halt instead of corrupt
memory. This means that once again, there's only a 1/2\^32 chance.

This might be with a keyword like:

`liv x = something.get(blah); // tethers`

vs

`let x = something.get(blah); // doesnt tether`

or perhaps an optional keyword:

`x liv = something.get(blah); // tethers`

vs

`x = something.get(blah); // doesnt tether`

Upside: we definitely give the user more control.

Downside: they probably won't use it as often. Or maybe they will?

Note that with this, we can't automatically tether a non-owning ref
when we explode it out of a struct. It might be pointing at a dead
object.

## B. Explicit Hard Tethering + Static Analysis

If the compiler notices that we're tethering or using something on all
paths after this point in the code, we can tether it ahead of time. We
can be pretty aggressive about it too.

It would even be considered a feature, because we're detecting a dead
object before it's used.

## C. Offer an Option

Perhaps we can offer some sort of option to opt-in to the more risky
behavior. This might be desirable if it's a vanishingly small chance of
this ever happening, and it's a non-safety-critical use case.

## Z. Let it happen

If we can one day prove this isn't actually a problem, we could just
let it happen. Until then, this is a bit out of the question.

# Conclusion

We'll go with A for now. It should give us the speed we need for e.g. ECS approaches.
