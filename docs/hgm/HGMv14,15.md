Builds on [[HGM
V11]{.underline}](https://docs.google.com/document/d/1LYnaVWqfzcwjpLWgkQc_G5Mdk0dksm869qOxQ9Llh2A/edit),
which specifies how the stack works. It doesn\'t build upon HGM V12.

This talks about how we can make avoid impossible situations where we
can\'t retire an object that we need to.

# V14: You Don\'t Need to Increment That Generation

This is fundamental to V14: there are a lot of cases where it\'s
actually safe to skip incrementing a generation number.

## Inline Array Elements

If we pop and then push in a vector, we don\'t actually have to
increment the generation. In this:

> myVec = Vec\<Spaceship\>();
>
> myVec.add(Spaceship(10));
>
> myVec.add(Spaceship(20));
>
> myVec.pop();
>
> myVec.add(Spaceship(30));
>
> myVec.pop();
>
> myVec.add(Spaceship(40));
>
> myVec.pop();

we don\'t have to be incrementing the second entry three times.

In fact, we *can\'t* do that, because if we hit the maximum generation,
we can\'t retire the allocation; it\'s stuck there.

It\'s safe to not increment it though, because we know that the next
element that could possibly be there *will* be a Spaceship.

Anyone who has an old reference to an old Spaceship in that location
will still access *a* Spaceship, so it\'s safe.

## Inline Struct Members

Recall how an inline struct reuses the generation of its parent.

If a struct has an inline member:

> struct Spaceship {
>
> engine! inl Engine;
>
> }
>
> struct Engine {
>
> fuel int;
>
> }

and we swap out its engine for another one, we don\'t have to increment
its generation, because we know they\'re the same type.

Note that when the parent struct dies, we need to increment the
generation.

## Inline Locals

A stack frame can be thought of as a struct, where the locals are the
members. It follows the same logic as inline struct members above.

However, this only works if we do a linked stack approach, where we use
a free-list of stack frames to constantly reuse. That way, if we hit the
maximum generation for a chunk, we can retire it.

## Weakables

All this means we can\'t use our generation to know whether a weakable
object is dead or not. Rather unfortunate. For those, we can use an LGT
though.

# V15: Randomized Generations

This might be the way we\'d always do it, or might be an option.

Basically: we don\'t increment generations, we use a PRNG. Or a
thread-local \"next allocation ID\" as our generation. Also, we make it
a 32 bit generation, not a 48 bit one.

However, there are big benefits.

## Gen-Ref Has 32-bit Offset

Since a generation is 32 bits instead of 48, we now have an extra 16
bits to play with in our GenRef struct. So, we can have this:

-   64 bit pointer to object

-   32 bit generation

-   32 bit offset to generation

## Arrays No Longer Need Generations For Every Object

Previously, offsets were max 16 bits. Since that isn\'t far enough to
reach to the beginning of a large array, it meant that we had to have
generation numbers all up and down the array.

But now it can!

This means arrays no longer have to have a generation for every element.

An array can have one generation, which it uses for all elements.

This could *drastically* cut down on heap fragmentation, we can use
gigantic buckets like normal malloc does.

HOWEVER, we probably will want one anyway, because:

-   We want that scope tether bit, so we can scope tether on a
    > per-element basis, otherwise we keep accumulating zombie element
    > copies

-   We can prevent use-after-pop with it, since we randomize.

## Can Release Memory to OS Better

When a page is completely used up, we can release it to the OS, and not
care what the old generations were.

If we reuse it, we can just fill it with new random generations. We
don\'t have to remember any old generations, or do any even-odd merging
or anything.

To avoid segfaults, we virtual mmap to a page of random numbers.

## Collision Risk, Mitigation

There could be collisions, where we wouldn\'t be able to detect memory
unsafety. It would be a 1/(2\^32) chance for any given memory unsafety
to go undetected.

Except it might be more complicated than that.

If we reuse a given allocation 1,000,000 times (roughly 2\^20) and keep
a reference to each dead object there, then there\'s only a 1/(2\^12)
chance that the memory unsafety will go undetected. That\'s not great.

There\'s a slight risk of this happening in the heap. A free-list might
want to reuse a certain allocation a lot. They like doing that, they\'re
free-lists.

This is an even bigger concern on the stack. We often spend most of our
time in stack frames 5-50. Some of those will be used *very* often.

To mitigate that, we can have a counter on every allocation, counting
down to when it should be retired.

Alternatively, we can randomly roll a dice. If rand() % 1000 == 0, then
we send it to the back of the free-list. We can use some other number
than 1000, chosen to make us more comfortable.

Wait, all this math might be off. This might not be a problem. I think
theres a e.g. 1/(2\^12) chance that there *exists* *some* reference
*somewhere* whose dereference wouldnt be caught. But for any given
dereference, it\'s still only a 1/(2\^32) chance that dereferencing it
would cause uncaught memory safety.

## Tradeoff

In the end, this makes our defense imperfect, but it solves our memory
leaking and fragmentation issue. It could be good for a long-running
server.

This is something we should add on later, so people are aware that Vale
*can* have perfect memory safety.

Extra thoughts:

-   Since we have a 32 bit offset, we could keep the generation pretty
    > far from the object. In which case, it becomes easier to share
    > ref-less objects with C, with no risk. We could share entire
    > buffers of ref-less objects this way.
