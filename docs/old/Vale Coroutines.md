We can have coroutines in Vale!

We\'ll allocate a new stack for each of them, like regular. Probably
have them before a guard page (which uses up virtual address space but
not physical, iirc)

when we want to hand off to another coroutine, we call a certain
function, say, coswitch(); and it does some C magic to jump to another
one.

really, we just need that before any blocking operation, like reading a
file or network or something.

inline mutable objects live in the side stack, inline immutables live on
the main stack. since nothing ever points into the main stack, we can
move it if we wanted to. or, we could use the HGM side stack\'s
algorithm to make a linked stack for the main stack too. so, either way,
we could do what go does for its goroutines.

when we have an HGM reference into a readonly region, we dont need scope
tethering. does that unlock some concurrency for us?

rust: uses async await, and probably structured concurrency. no
coroutines.

vale: uses goroutines with a region borrow checker that is much easier
than rust\'s. plus just one keyword where we want to do the handoffs (in
async code)

go: uses goroutines, just one keyword.

if we were using distinct memory, or channels, we could probably do
something as simple as go. the region borrow checker is only to allow
multiple threads to read the same memory.

we could:

-   use map() with a lambda, and in every iteration, move an object into
    > a coroutine. get the promise back, and then .wait() on it.

-   are these the same?:

    -   call a map function that will take everything in a read-only
        > way, and then start spawning threads, and then gather all the
        > results.

    -   use structured concurrency and region borrow checker?

-   give them all a mutex, and they can all open it read-only

normally, we\'ll allocate 4kb for a stack, and then use guard pages to
grow when needed. (though, the main thread would start with 32kb, or
something bigger)

but, if we want to have this kind of massive scaling and have a ton of
goroutines, we can use \--compact-stacks, which will make it use stack
copying and act like goroutines.
