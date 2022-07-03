All of these keep track of their space ratio, and any of them can be
compacted.

Compaction requires that nobody anywhere has borrow references to things
in here.

But, likely, things will want references... if we want an updateable
reference to something in here, we must use links.

a link is basically a struct that has a borrow ref in it

**MultisizePageDeque**

Allows adding and removing from both sides.

Allows iterating from either end.

Before every allocation there will be:

-   a uint32_t saying the size of the thing before it,

-   a uint32_t saying the size of the thing after it.

**MultisizePageQueue**

Allows adding to the end, removing from the beginning.

Allows iterating from the beginning.

Before every allocation, there's a vptr.

**MultisizePageStack**

Allows adding and removing from end.

Stack grows backwards in memory (so the vptr can be at the top for
easier popping).

At the beginning of every allocation there's a vptr.

Allows iterating from the top of the stack.

**UniformPageDeque**

Allows adding and removing from both sides.

Allows iterating from either end.

**UniformPageQueue/Stack** built on top of the UniformPageDeque.

**MultisizePool**

free list of various sizes.

**UniformPool**

Allows removing from anywhere. Just has a free list inside. The benefit
here is faster allocation and freeing.

**ContiguousUniformPool**

Allows removing from anywhere. Must be used with proxies, so it can move
things around.

**Iteratorizer for Uniform**

Random iteration order

Can be compacted, but not that necessary.

Two goals:

-   Use minimal space

-   Have better locality for iterating through (for ECS) with prediction

**Ownership**

Allocators that can remove from anywhere in the middle will need to do
some shuffling to keep things contiguous. That means that nothing can
ever have a reference to the underlying data, or, we need to update all
references to point to the new place.

The struct needs to explicitly say that it's managed.

struct MyStruct {

a: Int32;

fn print(virtual this) { println this.a; }

}

This means that it's managed by a stable proxy, but the underlying
struct can move at will.

struct MyStruct {

vptr;

MyStructProxy\* proxy;

a: Int32;

}

struct Proxy:MyStruct {

vptr; // all of these are thunks which forward to instance

MyStruct\* instance;

}

let a = Proxy:MyStruct(76);

This will make both the struct and the proxy. It will make a specific
kind of proxy called a SimpleProxy. When the SimpleProxy goes out of
scope, the underlying object dies as well, just via a free().

let myAllocator = ContiguousUniformPool:MyStruct();

let a: Proxy:MyStruct = myAllocator.new(76);

This is another kind of proxy, a ContiguousUniformPoolProxy. When this
goes out of scope, it tells the pool, and the pool shuffles other things
around.

When we want to loop over everything in a contiguous way, we give a
templated lambda to the allocator:

myAllocator map {(instance)

println instance.a;

}

The allocator will stamp a new version of that for every subclass of
MyStruct.

One might think there would be a problem if MyStruct contained an
OtherStruct. But, that's a reference, and references are by default
stable.

If the person decides to \@Inline it, then it's their own fault if there
are some borrow references alive that get broken and stop the program.
They can use \@Unique or \@Linear if they really want, to make sure that
they don't make these mistakes.

how do we do references to things inside vectors? i think we cant,
because the vector can change literally at any time\...

if we have vector iterators, we might need to have those do some
refcounting as well? if we shift everything above 4, then we need to
make sure no iterators to those exist.

PageList! is usually actually a PageList!:\[\]

but if you say PageList!:MyHeader

then it puts a MyHeader at the front of every page in the page list.

then thePageList.getHeaderFor(&theObject) will grab it, in just 2
instructions (bitwise-and and load). it will of course check beforehand
to make sure that it actually owns the given object.

funny story, the header doesnt even have to be immutable\... it can be
mutable.

every time we change the header, we lose up to 4kb, and release it back
to the ether to do with as it pleases.
