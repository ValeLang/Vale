for extern functions:

-   if no region annotation on type, it points within the block; itll be
    > copied.

-   if theres a region annotation representing vale, eg \'main, then
    > itll be a handle into vale memory

-   if theres an \'extern then itll be a raw pointer into surrounding
    > memory.

fn extfun(ship \'vale Spaceship, arr Array\<imm, int\>, file \'host
File) int extern;

-   ship has \'vale, so it\'s always a pointer into vale memory, it\'s
    > never copied.

-   arr has neither, so it\'s copied.

-   file has \'host, so it\'s always a pointer into C memory, it\'s
    > never copied.

-   the extern on the body says that the code is defined elsewhere.

\'extern has to be ref-counted, because:

-   In JVM/CLR/JS, we need to know when to release the reference from
    > the table.

-   In Swift, itll compile to the object\'s regular ref-counting.

-   In C, well, I guess it doesn\'t need to be ref-counted.

We\'ll have:

-   receiveRefFromHost, which brings something from the host (does a
    > roster check)

-   copyFromHost, which brings in a copy

-   sendRefToHost, which sends something to the host (perhaps adds to
    > the roster)

-   copyToHost, which sends something to the host (perhaps adds to the
    > roster)

-   transmigrate, which copies between internal regions

\'me can refer to this function\'s region, perhaps

# HGM\'s Safety Boundary

We need a way for HGM to doublecheck that, when unsafe (C, C++, Unsafe
Vale, Rust, whatever) gives us a reference, it still points to something
valid.

Remember that above every allocation, we have 64 bits, which contains:

-   48b generation number

-   1b scope tether

-   15b unused

Two possible approaches:

-   If there are \<2\^15 exported types, we can put a \"exported type
    > id\" there. When we receive a reference from the outside world,
    > check that its type ID matches.

-   Let\'s put a random number in that 15b when we allocate the object.
    > When we send a reference to a vale object into the outside world,
    > we\'ll include the 15b with it. Then, when we receive that
    > reference back into vale code, we can assert that the random 15b
    > agrees.

The first one is a bit more secure against someone trying to conjure one
of these things out in unsafe land. The exported type IDs are never
exposed to unsafe, so it works well for a doublecheck\... but the random
number would approach would include the 15b with it. We could do a
combination of these; 2\^14 exported types, and then 2\^14 random.

We\'ll still be theoretically breakable because:

-   we\'re sharing a stack with unsafe, so they can just write a
    > function that goes downward in the stack.

    -   actually, we can use a different entire stack for unsafe things.
        > when we call into an extern, we\'ll set rbp to it. when we
        > return from it, we\'ll restore rbp. when our export is called
        > into, we\'ll do something similar.

-   the foundation of HGM is around null pointers and segfaults, and
    > unsafe code can just register signal handlers that can longjmp and
    > do whatever.

-   unsafe can do multithreading shenanigans to break us.

-   we\'re in the same address space, they could figure out a pointer to
    > dereference.

[[http://www.cse.psu.edu/\~gxt29/papers/arabica.pdf]{.underline}](http://www.cse.psu.edu/~gxt29/papers/arabica.pdf):
\"However, native libraries in Java applications, as the "snake in the
grass", is notoriously unsafe \[2\]. Native libraries in a Java
application reside in the same address space as a Java Virtual Machine
(JVM), but are outside the control of Java's security model. Java
provides strong safety and security support, but once a Java application
incorporates native libraries, there is no assurance about the safety
and security of the whole application. Native libraries with programming
bugs or malicious native libraries may cause an unexpected crash of the
JVM, leak of confidential information, or even a complete takeover of
the JVM by attackers.\"

So, Vale will be as safe as java! But, if we need even more solid
safety, we can either:

-   Add a field to every exported object, 32b or 64b perhaps, which
    > contains a random ID just like the 15 bits do.

-   Use a table like assist mode does.

# Host Region

We deal with externs in a way very similar to the way we deal with other
regions:

-   We send immutables and handles between them.

-   We translate objects into different memory layouts.

-   We store struct definitions for them both.

-   We load from members and arrays for both (while translating)

So we\'ll make an IRegion for it.

When we transfer something from C to vale or vice versa, an extern or an
export will be involved. There\'s a boundary there.

When we send something from Vale to C, we\'ll recurse through the
array/object, and:

-   Grab a pointer to the \"scratch space\" which is a growable area of
    > memory.

-   Call **region-\>allocate(), region-\>constructKnownSizeArray(),
    > etc.** for whatever kind of object/array it is. This will bump the
    > pointer upward. This is where the copied object will live. Supply
    > nulls for all the members.

-   Replace the object\'s 8B metadata with an index into a central
    > Array\<\[void\*, u64, void\*\]\>, each entry has:

    -   a pointer to the object,

    -   the old 8B metadata,

    -   \"in-buffer pointer\", a pointer to where in the buffer to find
        > the object.

-   Recurse through all the owned (and shared) members, and repeat.

-   If it\'s a shared reference, then first check if we already crawled
    > it, by interpreting the 8B as an index, seeing if that index is
    > out of bounds of our central array, and seeing if that entry in
    > our array points back at it. Either way, take the in-buffer
    > pointer from that entry and use **region-\>storeMember()** to
    > store it.

-   Once done with that, recurse through the object again (skipping
    > shared stuff). For each object, similarly check if it\'s in our
    > array, then use **region-\>storeMember()** to update the part of
    > the destination buffer to point to the right part of the
    > destination buffer.

-   Go through the array, and put back all the old metadatas.

When we receive something from C to Vale, we\'ll recurse through the
incoming object and do the reverse, using **region-\>loadMember(),
region-\>loadElementFromKSA(), etc.** and at the end,
**region-\>deallocate()**.

At the beginning of the compile, we\'ll use
**region-\>declareKnownSizeArray()**, **region-\>declareEdge()**,
**region-\>declareStruct()**, etc.

# Inlining Between Regions

Cant inline things from other regions into our region. The biggest
reasons is that if we have an HGM struct, with an unsafe thing inlined
in it, suddenly we can dereference the unsafe thing and accidentally
overrun it into the HGM memory.

This also applies to stack frames. Unless we\'re in some sort of unsafe
stack, we can\'t inline unsafe things.

We can still allow the inl keyword, but midas will ignore it and make it
yon.

So, we\'ll have a stack for safe things, and a stack for unsafe things.

# Access Restriction Workaround

If we move an object into the host, it might not have private/protected
etc. This means that they can modify members freely, even if the vale
code marked them as private.

This problem also applies to immutables. Let\'s say we have a class
like:

> struct Vec3 {
>
> x int; y int; z int;
>
> priv distance int;
>
> fn Vec3(x int, y int, z int) {
>
> this.x = x; this.y = y; this.z = z;
>
> this.distance = sqrt(sq(x) + sq(y) + sq(z));
>
> }
>
> fn distance(me) { ret me.distance; }
>
> }

we could move it into C, then C could set the distance to whatever it
wants, and then we have a broken assumption.

A solution could be to prevent moving/copying things into host, but
that\'s extremely restrictive, especially for immutables.

One possible mitigation would be to require \`export\` on any struct to
be able to send it into C, but it wouldn\'t help much, as we can export
a struct from anywhere.

I think in the end, we just have to bite the bullet here. Invariants can
be broken by moving into host. That kind of sucks. We can live with it
though.

# Encrypted References, Different Stacks

(ERDS)

What we mainly want to avoid is bugs where we might accidentally smash
the stack and increment or decrement pointers. So, we don\'t want to
have unsafe objects inline in the regular stack, where we have a lot of
fragile pointers.

But, inlining objects in the stack is important. So, we\'ll let the user
control whether they\'re using the safe stack or the unsafe stack.

-   If they have a \'host before the body, then we\'re in the unsafe
    > stack: unsafe objects can live in the stack, and we have encrypted
    > references to the safe region objects.

-   If we don\'t have a \'host before the body, then we\'re in the safe
    > stack: we have pointers to unsafe objects (whether stack or heap).

We\'ll encrypt references when they\'re reachable by unsafe bugs. More
specific rules:

-   If it\'s a reference to an object in an unsafe region, it\'s
    > **unencrypted.**

-   If it\'s a reference to an object in a safe region:

    -   If it\'s a local or a register or argument in\...

        -   \...safe Vale, we\'re using the safe stack, the ref is
            > **unencrypted.**

        -   \...unsafe, we\'re using the unsafe stack, the ref is
            > **encrypted.**

    -   If it\'s a member\...

        -   \...of a struct in a safe region, then its **unencrypted.**

        -   \...of a struct in an unsafe region, then its **encrypted.**

To turn an unencrypted reference into an encrypted reference, we have to
rotate and xor.

To turn an encrypted reference into an unencrypted one, we have to
un-rotate, un-xor, and check that its type matches, etc.

At this point, the encrypted reference is \"basically an integer\".
It\'s kind of a GUID, really. Maybe don\'t say that, since GUIDs are
often regarded as big and slow.

# Dealias Extern Params and Returns

(DEPAR)

When we extern out into another language, we can\'t guarantee that
they\'re going to call alias or dealias at the right time. C can copy a
reference however it wants.

The next best thing is to dealias it as it heads out into C land, and
alias it again as it comes back into vale land.

for example:

enginesCRef = myCFunction(&ship);

should dealias the Ship cref as it heads out, and then alias the
enginesCRef as it comes in.

This does risk that we\'ll receive an invalid object from the outside
world, but that risk was always there. The programmer has to manually
track the lifetimes anyway.

Perhaps we can do some sort of doublecheck on objects that come in from
the outside world? When an object leaves into C-land, set a bit on them
saying that they left, and add them to a map.

When an object comes back into C-land, check that they\'re in the map,
crash if they aren\'t.

When vale frees an object, check the bit, and if its set, remove it from
the map.

Wouldn\'t be necessary in HGM, but could be useful for assist mode.

For immutables, since we have to deallocate when we hit zero, things get
complicated.

**Option A** (doesnt work)

If we do the above, like with mutables, then one might be zero when it
gets to the outside world. Then they might hand it to some vale function
which increments then decrements then deallocates. Then they\'ll do it
again and cause a use-after-free. Way too easy to get wrong.

**Option B**

We could follow normal vale rules, and increment on aliasing, then the
user would manually have to do the same thing in C land. Way too easy to
get wrong.

**Option C**

We could at the boundaries we only increment and never decrement.

We\'d need a custom drop function. But, then the user could call that
too many times.

**Option D**

We could only copy things to and from the outside world. This makes Vale
immune to any problems in C land.

We can\'t just send deeply sealed things across in a known-size struct
(which would have nicely prevented leaks) because of things like arrays
and strings which are unknown size.

Instead, when sending an immutable across the boundary, we\'ll figure
out the combined size of all the things in it (which we can do, because
no cycles!), malloc a buffer of that size, and copy into it. The C code
will be responsible for freeing that same pointer. Luckily, they don\'t
have to free things recursively.

If copying is too expensive, they can make a mutable wrapper around the
immutable thing that can reach in and grab specific things without doing
super expensive copies.

When we copy something back in, we have to check it to make sure it
won\'t cause any problems in vale; we can\'t just blindly construct a
new hierarchy.

-   The only thing we really have to check for is that itables point to
    > *actual* itables. So, all itables should have a type info i32 and
    > edge i32 as their first entry. Dereference the untrusted itable
    > pointer, grab those i32s, and make sure that they agree with the
    > untrusted itable pointer. Then we can use them.

-   Or, instead, we could use integer constants to say the types of
    > things. In mapping between those constants and the itables, we
    > would by necessity check that its a valid number.

should we have this to-buffer function for:

-   every type imaginable

    -   quite wasteful, most things wont be sent over the boundary

-   every type deeply reachable

    -   could cause surprises in binary size

-   only things annotated with export, and require exported imms only
    > contain exported imms

    -   seems reasonable. also, we can export things away from the
        > definition, which softens the blow.

we could have it easily for arrays and strings.

lets make everything yons unless explicitly specified as imm?

when we hand something in to an exported function, perhaps we dont need
to free it, the exported function can just read the stuff and copy it.

but when we return something from an extern function, we do need to
deallocate then\... itd be nice if we had some sort of scratch space
instead. alas.

**note from later:** none of these options guarantee safety. but, having
generational refs are safe on the outside. i think this means that
assist mode needs a combination of gen refs and RC.

for now though, lets not do any decrementing and incrementing over the
border. the user will have to make sure it only goes back into vale once
for every time it comes out of vale, to keep things balanced.

actually, we can alias and dealias across the border\... for instances.
theres just a problem when one comes back over the border after vale has
deallocated it. so we\'ll do that.

its moot for values because those are copied over the boundary.

still, long term we\'ll need to use gens. maybe an LGT?

# Start RC At One

(SRCAO, SRCAZ)

~~midas currently sometimes automatically initializes the RC for new
objects to 1.~~

~~this is a bad thing, because when we return something from an extern
function, we alias it (see DEPAR). we dont want it to start out as 2.~~

~~in fact, we even had an intermediate dealias at one point, but that
made it temporarily hit zero, which deallocated it.~~

~~anyway, its a bad idea to initialize it to 1. let the user of the
allocation determine whether they want it incremented or not.~~

**note from later:** this is obsolete thinking, because RC on the
boundaries just doesnt work, see DEPAR\'s later note. so, we\'ll start
it at 1.

# Host Region is Not Unsafe Region

The host region and the unsafe region would seem to be super similar,
but there\'s one big difference: their immutables are different. The
host region operates by making entire new massive copies of immutables,
and the unsafe region operates on reference counting.

// // These contain the extra interface methods that Midas adds to
particular interfaces.

// // For example, for every immutable, Midas needs to add a serialize()
method that

// // adds it to an outgoing linear buffer.

// std::unordered_map\<InterfaceReferend\*,
std::vector\<InterfaceMethod\*\>\> interfaceExtraMethods;

// std::unordered_map\<Edge\*, Edge\*\> extraEdgeAdditions;

Every region will need some way to export a handle, there\'s no way
around that. So, every region will have an **encryptReference** method
and a **decryptReference** method.

Every region will need a way to serialize referends into a buffer, but
that can be done with actual vale methods. This is purely for externs,
not for any boundary between safe regions and unsafe regions; e.g.
hgm-\>unsafe will just copy into new RC\'d objects, not go through an
intermediate linear buffer.

we\'ll probably want a ReferendStructs with the right structs to use for
the serializing.

when we\'re calling from assist into extern for example, we\'ll use not
copyAlien, but instead a specific function on unsafe called
copyAlienForExtern.

there is one big difference between unsafe vale and unsafe C: unsafe
vale uses RC\'d stuff.

perhaps for externs we can use something that\'s not even a region,
something like DefaultImmutables. perhaps we can have an IImmutables or
something?

maybe we dont have to do this linear copying? hmmm\...

for now, lets just have two different regions, unsafe and host. but\...
their instances are the same layout. hmm.

maybe we can have a special temporary region just for extern calling?

yeah, the fact that we\'re doing something special for extern calling
could be considered an internal detail of the host region\... man, but
other regions can read from these things to make their own
representations.

what if\... we could have an owning reference to immutables?

this is kind of like an alternate kind of share reference?

it really is a different kind of region.

i mean, all these regions share values\' ways, so maybe host and unsafe
can share instances\' ways?

theyre kind of like\... sub-regions?

well wait, unsafe can still export its definitions. or even, it can
export the definitions the same way unsafe did?

hmm\... host doesnt have to export its

wait, we still need to do this linear thing between safe vale and unsafe
vale. what does that mean?

it means that unsafe doesnt matter. host doesnt matter. we always need
this intermediate thing between untrusting regions.

no, thats not quite true. we just need to *copy* between untrusting
regions. we dont need this intermediate thing. so it really is special
for just extern calls\...

reading from it is really easy, its just loads.

assembling it might be harder? not really, thats special code.

yeah, this can be specific in externs.cpp. but, we need some recursive
functions that read from it. i suppose we read from it the same way we
read from unsafe?

what if we have a method on iregion for linear writing, and a method for
linear reading?

gosh, we could reuse it for serializing too.

yeah, we can have values\' -1th method be for serializing and
unserializing.

would it be faster to do the serializing and unserializing, or to use
the interfaces, or NxN static methods? remember, either way, we\'ll be
doing interface calls for the interfaces.

NxN is probably out. too much code.

we could do serializing for now, and later switch to interfaces. yeah.

the linear region knows to copy things in.

-   when receiving a val ref, awesome proceed

-   when receiving a inst ref:

    -   if its inline, awesome proceed

    -   if its yonder, assert false. yall should be using unsafe instead

the rcimm knows to copy things in, no matter what

right now, linear just statically calculates how much size itll need,
and then mallocs that much. at some point, we might want to have a side
stack of sorts to use for communicating with the vale side. would be
more cache efficient, though it might unfortunately require some
copying. hard to say whats better. might be better to avoid memory
leaks.

hmm, C can do some shenanigans to give another thread\'s reference to
us. need some way to know whether we\'re getting another thread\'s
reference. perhaps we can include the thread ID in the handle. if it
doesnt match the receiving thread, we panic. if the object has since
moved to another thread, the generations wont match, so itll be
correctly null.

for now, when translating between one region and another, we\'ll go
through linear. later on, maybe we can do something more clever. maybe
wasmish?

lets do the serializing thing from valestrom. it seems useful enough.

also, it can be a good use case for virtual generics.

we can have it take in an extern interface called IWriter

for serializing for messages maybe, but for this into-C stuff we
definitely need midas.

wait, dont we want to use interface types instead probably?

no, its uncertain whether those will actually be better or just more
expensive.

packing:
[[https://stackoverflow.com/a/38144117]{.underline}](https://stackoverflow.com/a/38144117)

assist mode needs both RC and gens to make sure that unsafe is doing
things right:

-   it needs RC so we can more guarantee that inside vale code we dont
    > do anything stupid

-   it needs gens as a fallback for things that come back in from the
    > extern.
