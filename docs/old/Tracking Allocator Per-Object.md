Note: These mechanisms won\'t work, because we need 16 bits for scope
tethering, which leaves no room for a function pointer in the object
metadata for allocator\'d objects. TODO: Go through this doc and see
which approaches are still viable.

# Usage

## Simple Custom Allocator

In Vale, when allocating something, we can specify which allocator it
should come from. For example:

> allocator MyArena = Arena(reuseThres=1mb, crashThres=inf)
>
> fn main() {
>
> arena = MyArena();
>
> shipA = \^arena Ship(1337);
>
> }

In the above example, we:

-   Declared an allocator type MyArena which starts to reuse memory when
    > it hits 1mb.

-   Made an instance of that allocator, in a local called arena.

-   Allocated a Ship from it.

## Changing Default Allocator for a Call

We can also change the default allocator when we call a pure function.
This is called an **allocator\'d call.**

> allocator MyPool = Pool(crashThres=inf)
>
> fn addShips(a &Ship, b &Ship) \^Ship pure {
>
> \^Ship(a.crew + b.crew)
>
> }
>
> fn main() {
>
> shipA = \'MyPool() \^Ship(1337);
>
> }

In the above example:

-   Declared an allocator type MyArena which starts to reuse memory when
    > it hits 1mb.

-   Made an instance of that allocator, in a local called arena.

-   Allocated a Ship from it.

In the future, we *might* lift the pure restriction.

## Using a Homogenous Allocator

The aforementioned allocators are heterogenous, which means we can
allocate any type from them. We can also make homogenous ones:

> allocator MyPool\<T\> = HomPool\<T\>(crashThres=inf)
>
> fn main() {
>
> pool = MyPool\<Ship\>();
>
> shipA = \^@pool Ship(1337);
>
> }

The compiler ensures we\'re only pulling the correct type out of the
allocator.

We cannot use these for allocator\'d calls.

## Iterating

Iterating over objects in a pool is particularly useful for things like
ECS.

We can iterate over all objects in HomPool:

> allocator MyPool\<T\> = HomPool\<T\>(crashThres=inf)
>
> fn main() {
>
> pool = MyPool\<Ship\>();
>
> shipA = \^@pool Ship(1337);
>
> each ship in pool {
>
> print(ship.crew);
>
> }
>
> }

And, given the type, we can iterate over all objects in a MultiPool:

> allocator MyPool\<T\> = MultiPool\<T\>(crashThres=inf)
>
> fn main() {
>
> pool = MyPool();
>
> shipA = \^@pool Ship(1337);
>
> each ship in pool.all\<Ship\>() {
>
> print(ship.crew);
>
> }
>
> }

## Things To Keep In Mind

-   If you drop an allocator before all of its contained objects are
    > dropped, it will halt the program.

-   Users of your library can override any allocator to do anything they
    > want, by specifying its name and new arguments.

    -   This is why we have to declare allocators up front.

# Design

TL;DR:

-   We\'ll use the object\'s metadata bits to know which allocator free
    > function to call.

-   If is_allocatord bit is false, we\'re using the malloc allocator,
    > and will call hgmFree.

-   If is_allocatord bit is true, we\'ll interpret the object\'s safety
    > bytes as a free function ptr.

-   The allocator\'s free function can get information about its
    > specific allocator from the top of the 4kb page containing the
    > allocation.

## Background

Every object has 8 bytes of metadata at the top:

-   1 byte is the \"metadata byte\", which contain the tether bit and
    > owned-by-vale bit.

-   7 bytes are the \"safety bytes\", which is used to for lock-and-key
    > comparing. The only constraint on these 7 bytes is that they must
    > not change during an object\'s lifetime, and then it must change
    > between then and any possible shape-change.

The safety bytes are used differently depending on the region:

-   HGM puts a generation in there, and increments it whenever we free
    > the object.

-   Arena doesn\'t care what\'s in there, but it does change it before
    > the arena is destroyed, if any references outlive the arena.

-   Pool doesn\'t care what\'s in there, and it even reuses the same
    > spot for other instances of the same type, but it does change it
    > before the arena is destroyed, if any references outlive the
    > arena.

There\'s a lot of flexibility in the latter two. We can basically put
whatever we want in the safety bytes, for the lifetime of the allocator.

## What We\'ll Do

So we\'ll make use of that flexibility. We will:

-   Add a bit in the object\'s metadata byte, is_allocatord.

    -   If it\'s false, call the normal hgmFree().

    -   If it\'s true, look in the object\'s safety bytes.

-   Encode the a pointer in the object\'s safety bytes that we can use
    > to determine the free-function-pointer.

There are two ways to accomplish that. Either:

-   put the free-function-pointer in the safety bytes directly, or

-   put a region pointer into the safety bytes, from which we load the
    > free-function-pointer.

More explained below!

## Approach A: Free-Function-Pointer in Safety Bytes

To get an object\'s free-function-pointer, we will:

-   Load is_allocatord bit from the object\'s metadata byte.

-   Load safety_bytes from object.

-   free_func_ptr = !is_allocatord \* &hgmFree \| is_allocatord \*
    > safety_bytes

And then we call it, giving it the object\'s address.

Note from later: Can we have the type ID xor\'d into the safety bytes?
Then free() could xor that back out before deallocating. Could give
better safety perhaps. Might even be free with uop fusion. Also, we
could sneak some bits in there somewhere, to hint to the allocator where
to find the page metadata (eg 4kb or 64kb). Somewhere next to the scope
tether bits?

## Approach B: Allocator Pointer in Safety Bytes

This assumes:

-   The first 8 bytes of an allocator contains its free function
    > pointer.

-   We have a global immutable 8 bytes called hgmAllocator, whose first
    > 8 bytes of course contains a ptr to hgmFree.

To get an object\'s free-function-pointer, we will:

-   Load is_allocatord bit from the object\'s metadata byte.

-   Load safety_bytes from object.

-   allocator_ptr = !is_allocatord \* &hgmFreeAddr \| is_allocatord \*
    > safety_bytes

-   free_func_ptr = \*region_ptr

## We\'ll Use Approach A In SST Regions

We\'ll use the free-function-pointer approach for now, because it
involves one less load instruction. (We could switch to approach B if we
ever need to know an object\'s allocator.)

The overall cost of this approach, compared to just calling the correct
statically-known function, is about 3 cycles and a function pointer
call, on every deallocation.

This only applies to SST regions. Further below we\'ll explain SST and
NSC regions, and what we\'ll do in NSC regions.

## What Do These Free Functions Do?

The free functions take in these parameters:

-   Object pointer

-   Type size (ignored by most)

-   TypeInfo pointer (ignored by hgmFree)

-   Allocator pointer (ignored by hgmFree)

It depends on the allocator. Some examples:

-   sst-malloc will scramble the generation and give it back to
    > mimalloc. (expensive)

-   sst-hom-arena will scramble the generation. (extremely cheap)

-   sst-het-pool will:

    -   look at the top 4kb of the page to find a pointer to the pool\'s
        > HashMap\<TypeInfo\*, void\*\*\> typeToFreeListHead.

    -   Load it, and look up the free list head. If not found, add one.

    -   Write that next-pointer into the instance\'s safety bytes.

    -   Set the free list head to point to this object.

Note how the last one looks at the top of the 4kb page, and the other
two don\'t.

## SST vs NSC Regions

### SST Regions

When we start the program (or a thread) we\'re in a SST region, which
stands for Shape-Scope-Tethered. This means that the entire region
allows for scope-tethering objects, which ensure that, for the duration
of a certain scope, **nothing of a different shape is allocated into its
spot in memory.**

One allocator is particularly interested in scope tethers: sst-malloc,
commonly known as HGM, which is our default allocator. It makes sure we
don\'t reuse anything currently scope tethered.

**Every allocator in an SST region needs to obey scope tethers.** In
other words, an allocator needs to make sure that if an object is scope
tethered, **nothing of a different shape is allocated into its spot in
memory.**

Luckily, obeying that is very easy for arenas and pools. We get it for
free:

-   Arenas never reuse memory.

-   Pools only reuse memory for things of the same type (and therefore
    > shape).

So they literally need to do nothing extra.

However, they do need to reserve 8 bytes at the top of every object,
which can contain the scope tether bit. But that\'s fine, we needed that
8b anyway, to put the free-function-pointer in!

### NSC Regions

A region can be NSC, which means Non-Shape-Changing. This means that
nothing in this region will ever change shape.

**The drawback:** We can\'t use sst-malloc, which reuses allocations of
the same size for things of a different shape. We have to use the more
memory-heavy (and coincidentally, faster) approaches.

**The benefit:** No scope tethering ever has to happen in an NSC region.
It\'s faster!

It\'s perfect for temporary cases.

### Seeing SST Regions as NSC Regions

We can temporarily pretend an SST region is an NSC region, **if it\'s
immutable.** After all, if it\'s immutable, we\'ll never try to free
anything in it.

The region borrow checker keeps track of what regions are immutable.

## Deallocating From An NSC Region

NSC allocators, just like SST allocators (except sst-malloc) will embed
the free-function-pointer into the object\'s safety bytes.

(Why do we even have metadata? See [[Skipping NSC Object
Metadata]{.underline}](#skipping-nsc-object-metadata). Might be
possible.)

Since we know we\'re not using sst-malloc, everything has
is_allocatord=1, so we know that the safety bytes contains the
free-function-pointer. We can just load it and use it immediately.

# Planned Allocators

Types of pools:

-   Malloc: uses sst-malloc.

-   Pool: uses sst/nsc-het-pool.

-   Arena: uses sst/nsc-het-arena if reuseThreshold=inf, else
    > sst/nsc-het-hybrid.

-   HomPool: uses sst/nsc-hom-pool

-   HomArena: uses sst/nsc-hom-arena if reuseThreshold=inf, else
    > sst/nsc-hom-hybrid.

-   MultiPool: uses sst/nsc-multi-pool.

-   MultiArena: uses sst/nsc-multi-arena.

The user will never specify sst- vs nsc-, that\'s determined by the
containing region.

NSC (Non-Shape-Changing) allocators:

-   **nsc-hom-arena:** A bump-pointer\'d slab of memory we can allocate
    > a single type from.

-   **nsc-het-arena:** A bump-pointer\'d slab of memory we can allocate
    > any type from.

-   **nsc-hom-pool:** A bump-pointer\'d slab of memory we can allocate a
    > single type from. A free-bitvector helps us reuse memory inside
    > it. Iterable!

-   **nsc-het-pool:** A bump-pointer\'d slab of memory we can allocate
    > multiple types from. A HashMap\<ype, FreeListHead\> helps us reuse
    > memory inside it per-type.

-   **nsc-multi-arena:** A HashMap\<Type, nsc-hom-arena\>.

-   **nsc-multi-pool:** A HashMap\<Type, nsc-hom-pool\>. Iterable (given
    > type)!

-   **nsc-hom-hybrid:** A nsc-hom-arena that, when it hits a max size,
    > turns itself into a nsc-hom-pool.

-   **nsc-het-hybrid:** A nsc-het-arena that records the type for every
    > allocation, and adds to a freelist on deletion, so that when it
    > hits a max size, turns itself into a nsc-het-pool.

SST (Shape-Scope-Tethering) allocators:

-   **sst-malloc:** Normal malloc/free, but with a random generation at
    > the top of every object.

-   **sst-stack:** A regular program stack, where every stack allocation
    > has a generation.

-   **sst-hom-arena:** A nsc-hom-arena but every obj has a gen, incr\'d
    > on free.

-   **sst-het-arena:** A nsc-het-arena but every obj has a gen, incr\'d
    > on free.

-   **sst-hom-pool:** A nsc-hom-pool but every obj has a gen, incr\'d on
    > free.

-   **sst-het-pool:** A nsc-het-pool but every obj has a gen, incr\'d on
    > free.

-   **sst-multi-arena:** A HashMap\<Type, sst-hom-arena\>.

-   **sst-multi-pool:** A HashMap\<Type, sst-hom-pool\>. Iterable (given
    > type)!

-   **sst-hom-hybrid:** A nsc-hom-hybrid but every obj has a gen,
    > incr\'d on free.

-   **sst-het-hybrid:** A nsc-het-hybrid but every obj has a gen,
    > incr\'d on free.

Every allocator (except Malloc) has a panicThreshold argument. When the
size of all contained objects hits it, we halt the program.

All arena allocators also have a reuseThreshold argument.

-   If inf, it\'s a regular arena, constantly allocating until it hits
    > the panicThreshold.

-   If a number, then it will start reusing its memory like a pool when
    > it hits that size.

Arenas can have both specified at the same time.

Arenas *must* have at least one specified, even if it\'s just
panicThreshold=inf.

# Alternatives Considered

We\'ll use the [[Object\'s Metadata Bits and Page
Header]{.underline}](#objects-metadata-bits-and-page-header) approach,
described in the main body.

The other methods have these costs:

-   Un-reusable functions, as they hard-code the allocator for their
    > owning ref params.

-   Bifurcation, as functions are repeated to take owning refs of
    > various allocators.

-   Code-size explosion, as happens when monomorphizing meets
    > interfaces.

-   Extra 8 bytes per object, if we bundle an allocator pointer with an
    > owning ref.

-   Using hidden bits in owning ref, deref costs and limits num
    > simultaneous allocators.

-   Not using built-in malloc.

Whereas the chosen approach, on every free, incurs only 3 cycles and a
function pointer call.

## Tracking Allocator via Type System

The type system tracks what allocator an owning reference is using:

-   shipA is a \@hgm \^Ship.

-   shipB is a \@arena \^Ship.

### Borrow Refs are Allocator-Agnostic

Borrow references are great, they don\'t care what allocator something
came from:

> currentShip &Ship = &shipA;
>
> set currentShip = &shipB;

This is really nice, because a function can take in a borrow reference,
and we could use the function with whatever we want:

> fn printShip(ship &Ship) {
>
> print(ship.fuel);
>
> }
>
> printShip(&shipA);
>
> printShip(&shipB);

In this way, a function taking only borrow refs is **allocator
agnostic**. This is a very good thing.

### Owning Refs Cause Non-Reusable Functions

But how do we make it allocator agnostic if we take in owning refs? This
function is usable for an \@hgm \^Ship\...

> fn explodeShip(ship \@hgm \^Ship) {
>
> print(ship.fuel);
>
> drop(ship); // drops the Ship with the HGM allocator
>
> }

\...but not a \@pool \^Ship.

We don\'t want to **bifurcate;** we don\'t want to write two versions of
our function:

> fn explodeShip(ship \@hgm \^Ship) {
>
> print(ship.fuel);
>
> drop(ship); // drops the Ship with the HGM allocator
>
> }
>
> fn explodeShip(ship \@pool \^Ship) {
>
> print(ship.fuel);
>
> drop(ship); // drops the Ship with the pool allocator
>
> }

### Using Generics To Make Functions Reusable Again

But we can use generics:

> fn explodeShip\<A\>(ship \@A \^Ship) {
>
> print(ship.fuel);
>
> drop(ship); // drops the Ship with the correct allocator
>
> }

Generics do technically solve this, but it means that any function
taking in any owning references (or returning any owning references!)
will need to take in a generic allocator parameter.

#### Implicit Generic Allocator Parameters

We can implicitly add these for the user!

Though, we\'ll need one for *every* owning reference, since we have no
guarantee that all incoming owning references use the same allocator.

This is the actual function header\...

> fn add\<A\>(x \@A \^Buffer, y \@A \^Buffer) \@A \^Buffer {

\...for this snippet:

> fn add(x \^Buffer, y \^Buffer) \^Buffer {
>
> \... // merge y into x and return x
>
> }

but it doesn\'t work:

> bufA = \@hgm \^Buffer(\"hello\");
>
> bufB = \@arena \^Buffer(\"world\");
>
> bufC = add(bufA, bufB); // Compile error! Doesn\'t work

We\'d need to have multiple generic parameters; this would be the actual
function header:

> fn add\<A, B, R = hgm\>(x \@A \^Buffer, y \@B \^Buffer) \@R \^Buffer {
>
> \... // merge y into x and return x
>
> }
>
> bufA = \@hgm \^Buffer(\"hello\");
>
> bufB = \@arena \^Buffer(\"world\");
>
> bufC = add(bufA, bufB); // Works

So far so good! Except\...

#### Code Size Explosion

We\'re making a new version of every function, every time we\'re using a
different combination of allocators.

Luckily, we\'ll only monomorphize the ones we actually use.

\...unless interfaces get involved.

If we have an interface and some subclasses:

> interface IShip {
>
> fn installEngine\<A\>(&self, engine \@A \^Engine);
>
> }
>
> struct Serenity impl IShip {
>
> fn installEngine\<A\>(&self, engine \@A \^Engine) { doThings(engine);
> }
>
> }
>
> struct Raza impl IShip {
>
> fn installEngine\<A\>(&self, engine \@A \^Engine) { explode(engine); }
>
> }

and we:

-   Make a Serenity,

-   upcast it to an IShip,

-   call its installEngine with a \@hgm \^Engine,

-   call its installEngine with a \@arena \^Engine,

-   call its installEngine with a \@pool \^Engine.

We\'ll need three flavors of IShip\'s installEngine:

> interface IShip {
>
> // generated by compiler:
>
> fn installEngine(&self, \@hgm \^Engine);
>
> fn installEngine(&self, \@arena \^Engine);
>
> fn installEngine(&self, \@pool \^Engine);
>
> }

which means we\'ll need three flavors for every subclass as well!

> struct Serenity impl IShip {
>
> fn installEngine(&self, \@hgm \^Engine) { doThings(engine); }
>
> fn installEngine(&self, \@arena \^Engine) { doThings(engine); }
>
> fn installEngine(&self, \@pool \^Engine) { doThings(engine); }
>
> }
>
> struct Raza impl IShip {
>
> fn installEngine(&self, \@hgm \^Engine) { explode(engine); }
>
> fn installEngine(&self, \@arena \^Engine) { explode(engine); }
>
> fn installEngine(&self, \@pool \^Engine) { explode(engine); }
>
> }

Note how, even though we only called installEngine for an IShip that was
actually a Serenity, the compiler doesn\'t know that, so it had to make
three flavors of *every* subclass\'s installEngine. At run-time, we
won\'t even use Raza\'s installEngine, so this is particularly
unfortunate.

This causes a ripple effect. Suddenly, we have to make three flavors of
the explode function. Anything it calls will probably also need three
flavors. This chain reaction across the code base can often lead to a
lot of useless flavors.

If there are any interfaces that are very common, every struct
implementing them is almost guaranteed to have all flavors. Vale has six
allocators, so many functions will have 6\^N flavors, where N is the
number of allocator generic parameters (like A). In functions like
add\<A, B, R\> above, we might need 216 flavors.

This gets even worse with dynamic linking where we don\'t know at
compile-time who might be calling a function with what allocators, so we
have to conservatively make all possible flavors.

This is, alas, unacceptable.

## Tracking Allocator at Run-Time

The last approach, [[Object\'s Metadata Bits and Page
Header]{.underline}](#design), is the best. The rest are here for
exploration and to record reasoning.

### Pointer\'s Low Bits

Every allocator will allocate at least 16 bytes per allocation. Because
of this, the low 4 bits of a reference are unused.

We can identify the allocator in those bits.

This does mean that dereferencing that pointer will take an extra cycle,
as we have to & off those bits.

It also means that we can\'t have multiple arenas or multiple pools.
Those two are most useful when they\'re temporary, and it\'s useful to
have multiple existing at a time. That kind of disqualifies this
approach.

### Fat Pointer

Previously, an owning ref was just one pointer, to an object. Instead,
an owning reference will be two things stuck together:

-   The pointer to the object,

-   A pointer to the allocator to use to free it.

An \^Ship is these two pointers, put together.

The type system no longer needs to track what allocator an owning ref
has. So, we\'ve completely eliminated the need for generics.

But, our memory usage just got higher; we now use an extra 8 bytes per
owning reference.

### Object\'s Metadata Bits

Every object has 8 bytes of metadata at the top:

-   1 byte is the \"metadata byte\", which contain the tether bit and
    > owned-by-vale bit.

-   7 bytes are the \"safety bytes\", which is used to for lock-and-key
    > comparing. The only constraint on these 7 bytes is that they must
    > not change during an object\'s lifetime, and then it must change
    > between then and any possible shape-change.

We could use some of those metadata bits to identify which of the 6
allocators this is from.

However, it suffers the same problem as the Pointer Low Bits approach;
now we have a finite number of kinds of allocators.

### Page Header

Every custom allocator should have 4kb blocks that it uses to allocate
from.

At the top of that page could be a pointer to the allocator that owns
it.

The downside is that we can\'t use the built-in malloc anymore.

### Object\'s Metadata Bits and Page Header

This is the approach we eventually went with, see the main body for this
approach.

## Skipping NSC Object Metadata

There are three bits so far in an object\'s metadata:

-   is_scope_tethered

-   is_allocatord

-   is_owned_by_vale

And the safety bytes contain either:

-   Generation

-   Free function pointer

-   RC

It\'s really tempting to just not have the metadata, because these are
mostly moot:

-   is_scope_tethered is always false, we don\'t scope tether NSC
    > objects.

-   is_allocatord is either irrelevant (imm region) or true for NSC
    > regions.

-   is_owned_by_vale is actually **still relevant.**

And the safety bytes don\'t matter, we could get the free function
pointer from the containing page.

However, an NSC view of a region might point to either:

-   A temporarily-immutable SST region

-   An actual NSC region

Respectively:

-   Arrays in the former will have each entry with 8 bytes metadata.

-   Arrays in the latter would not have the 8 bytes metadata.

When we have an NSC view of a region, we wouldn\'t know how to calculate
an element\'s address, because we wouldn\'t know if it has those 8-byte
metadatas.

Fun fact, for array elements, is_owned_by_vale and the
free-function-pointer are irrelevant. The entire object metadata is
literally useless for NSC array elements.

**Perhaps** we can have a \"element padding\" in the current region
metadata block, containing 0 or 8, that we can add in to the
calculation.

We\'ll need to wait and see if we need any more metadata bits. If we
dont, for NSC array elements at least, then we might be able to do this
approach!
