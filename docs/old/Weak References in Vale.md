Vale just got the last feature it needed for 0.1: weak references!

But these aren\'t any ordinary weak references. They are weird\... and
they are ***fast***.

Here\'s a benchmark comparing Weak references from Swift, C++, Rust, and
Vale:

Swift and C++ are slow because it\'s using Atomic operations. Rust and
Vale are both using non-atomic operations. Vale is slightly faster,
because it fetches the ref count and the objects memory in parallel.

Note that this benchmark isnt comparing *actual* performance of the
languages as a whole. Rust\'s Rc and C++\'s shared_ptr\'s are not the
main way to do things in their languages. Stay tuned, we\'re currently
working on a small roguelike game to benchmark with.

Vale needs very fast weak references, because in resilient mode, we turn
all constraints references into weak references.

They\'ll also needs week references because we don\'t really have shared
mutable references. when one who wants a shared mutable reference one
has to use a constraint reference or a weak reference instead, So people
will be using weak references is a lot more.

**How it works**

every weakable object has a index into the threadlocal weak table.

each element in the WRC table has two things one bit representing
whether the object is still alive and 31 bits counting the number of
weak references.

every weak reference is a pointer to the object, and an index into the
WRC table.

every time we Alias a weak reference we increments of the count in the
weak table.

since we use weakable keyword it is a zero cost abstraction; if you
don\'t use it you don\'t pay for it.

We don\'t detach it from the object like rest and C++ does the biggest
reason is that we wants to be able to make a new week reference from a
regular constraint reference. cplusplus has enable shared from this
which is a nightmare.

when we send an object to another thread we recursively scan the object,
sever its old week count, and get a new one in the new thread. this cost
can be avoided using mutexes, atomic immutables, or structured
concurrency with implicit locking.

Rust doesn\'t even allow you to send reference counted things, but Vale
does. Vale is cool like that.

**Big Deal for Vale**

this is a big deal because it\'s the last big feature we needed before
the 0.1 release. all that\'s remaining is aspiring borrow references
upon last use, and better compiler errors.

**Going faster**

next we\'re going to try and replace all of this counting with just a
generation, where the week table has just the generation, and every week
ref has the index and it\'s expected generation and a pointer to the
object. perhaps this will be configurable per region because it is
theoretically possible that we could run out of generations. This is
really nice though because there would be no aliasing costs.

I can imagine that 99% of programs will use this generational pointer
approach, and Long live programs might use the reference Counting
approach.

If you would like to work on it come by the Discord
