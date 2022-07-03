&Marine is a regular borrow reference, and \'a &Marine means that it\'s
lifetime constrained, in other words a static borrow. \'&Marine could
mean something eventually, not sure what.

\'a Marine would mean that we\'re defining a lifetime a.

### RC Elision

We can forget doing any sort of ref counting on immutable objects,
definitely.

mutable objects might be a bit more complicated though. If we put
something into a structure or take something out of a structure, we
might need to change the ref count\... unless the struct is templated on
the lifetime. But that\'s fine. In other cases, like for functions and
stuff, we can probably elide them all.

### Mutexes

We might lock a marine mutex, and then a reference could escape:

> let marineLock = marineMutex.lock();
>
> let marine = marineLock.contents;
>
> let weapon = &marine.weapon;
>
> unlet marineLock;
>
> // use weapon

weapon just escaped.

How about we instead say that .lock() returns a static borrow. And if we
have a static borrow, it means that nothing that came from this
reference can leave the lifetime.

> let marineLock = marineMutex.lock(); // marineLock has a lifetime
>
> let marine = marineLock.contents; // since came from marineLock,
> marine has lifetime
>
> let weapon = &marine.weapon; // as does weapon
>
> unlet marineLock;
>
> // use weapon, compile time error, because lifetimes aren\'t subsets

Going from a non-static reference to a static borrow must increment the
ref count.

We\'ll need to be able to annotate lifetimes on functions, to be able to
call them.

> fn getWeapon(marine: \'a &Marine) \'a &Weapon {
>
> return marine.weapon;
>
> }

so\...

rust lifetimes are annoying, but maybe only because they can\'t be built
on top of a runtime system. and at least we only have to deal with it
when we want to optimize, or use mutexes.

how do we move something into or out of the clique? i think we mandate
that the ref count is zero, right? how we do it syntactically? and if it
contains things, do we have to scan them? i think we do\...

so the point of mutexes is not to be faster about moving data to and
from places, it\'s about making it faster to reach in and modify
something about the data.

note that with rust, to do the same, you have to:

-   copy the thing in with a bcopy

-   if it was \"referencing\" something (with index) you have to bcopy
    > the thing it was pointing to

-   you have to contain an rc\<mutex\<refcell\< or something, rife with
    > performance problems

rust multithreading is \*not\* free in general. it\'s only free for
contiguous PODs (no pointers like refcell, since refcell is !sync).

since we\'re scanning everything on its way between threads and into
mutexes, we can make use of that\... the thread\'s allocator can note
what\'s owned by someone else, and can note what things that it\'s using
belong to someone else. it can send messages to the original owner too.

btw, when a thread dies, it will need to give responsibility of its
pages to someone. perhaps the owning thread? (the one that kills it)

these static lifetimes are a potential answer to the superstructure
problem of needing functions! if we can keep the realms separate, we can
interact freely with the superstructure\'s memory! must investigate
more.
