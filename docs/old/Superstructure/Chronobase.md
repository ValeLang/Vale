Immutable maps (and other structures) in Scala and other functional
languages have methods to update them, which return an entire new map,
but which shares as many references to all the parts of the original map
as it can. Let's take that one step further, and make it apply to entire
hierarchies of classes.

chronobase AthariaDB {

struct Game { shrubs: Vector\[Shrub\]; units: Vector\[Unit\]; }

struct Shrub { location: Coord; }

struct Unit { hp: Int; location: Coord; items: Vector\[Item\] }

struct Item { name: Str; }

}

let myDB = AthariaDB();

Lets say we fill it like this:

Game:

-   Unit 1, hp 10, location (5, 6):

    -   items:

        -   Item 1, name "hello"

        -   Item 2, name "blah"

-   Unit 2, hp 15, location (9, 8):

    -   items:

        -   Item 3, name "bloop"

-   Shrub 1, location (3, 3)

Green means the structure is at version 1.

let gameRef = myDB.root; // gameRef owns a GameRef now.

let myGameSnapshot1 = gameRef.snapshot(); // this is actually a Game\*,
completely immutable

Lets say we then add an item to Unit 2. Things in blue are version 2.

Game:

-   Unit 1, hp 10, location (5, 6):

    -   items:

        -   Item 1, name "hello"

        -   Item 2, name "blah"

-   Unit 2, hp 15, location (9, 8):

    -   items:

        -   Item 3, name "bloop"

        -   Item 4, name "flopplewop"

-   Shrub 1, location (3, 3)

But what would this code look like? It would probably be a

myAthariaDB.unitAddItem(unit2.id, myAthariaDB.newItem("flopplewop"))

Maybe we can make it less tedious.

To update a shrub's location, we would conceptually have a

myAthariaDB.updateShrubLocation(shrubID, Coord(6, 7))

but let's instead have a ShrubRef which contains myAthariaDB and
shrubID. We could then do

myShrubRef.updateLocation(Coord(6, 7))

or better yet,

myShrubRef.location = Coord(6, 7).

Now we have a immutable database which can be modified in a very
mutable-style way.

For the above hierarchy, these structs are generated (bold are the
non-obvious things):

struct Game { **\_id: Int;** shrubs: Vector\[Shrub\*\]; units:
Vector\[Unit\*\]; **items: Vector\[Item\*\];** }

struct Shrub { **\_id: Int;** location: Coord; }

struct Unit { **\_id: Int;** hp: Int; location: Coord; **itemId: Int;**
}

struct Item { **\_id: Int;** name: Str; }

struct AthariaDB { currentRoot: Game\*; } // currentRoot is the only
thing that ever changes

struct GameRef { root: GameRoot\*; }

struct UnitRef { root: GameRoot\*; **unitId: Int;** }

struct ShrubRef { root: GameRoot\*; **shrubId: Int;** }

struct ItemRef { root: GameRoot\*; **itemId: Int;** }

\_id is zero until it enters the Chronobase, at which point it receives
the next available ID. Only one place in the chronobase ever actually
has a pointer to the instance, the rest of the places just use that ID.
To update that instance, just re-point that pointer to a new instance.

Things in the chronobase are own, borrow, or weak. Any instance can only
be owned once in a given Chronobase; to the user, they aren't shared,
but all references are actually shared, because multiple chronobases can
share the same object. The sharedOwning and sharedBorrow and sharedWeak
counts are held in a roster like normal.

Ownership path:

Different snapshots can all point at the same immutable instance of
something, with different paths, so the instance can't store the path.
The root \*could\* contain a map\<type, map\<ID, pathString\>\>. It
would be immutable, and shared when the database is forked/snapshotted.

Ownership path would be useful for something like polymer. Rather
expensive to keep track of though.

### Optimization

We can have a "version number" per instance.

chronobase AthariaDB {

struct Game { shrubs: Vector\[Shrub\]; units: Vector\[Unit\]; }

struct Shrub { location: Coord; }

struct Unit { hp: Int; location: Coord; items: Vector\[Item\] }

struct Item { name: Str; }

}

generates these structs (bold are the non-obvious things):

struct Game { **\_id: Int; \_version: Int;** shrubs: Vector\[Shrub\*\];
units: Vector\[Unit\*\]; **items: Vector\[Item\*\]** }

struct Shrub { **\_id: Int; \_version: Int;** location: Coord; }

struct Unit { **\_id: Int; \_version: Int;** hp: Int; location: Coord;
**itemId: Int;** }

struct Item { **\_id: Int; \_version: Int;** name: Str; }

struct AthariaDB { currentRoot: Game\*; **int \_workingVersion;** }

struct GameRef { root: GameRoot\*; }

struct UnitRef { root: GameRoot\*; **unitId: Int;** }

struct ShrubRef { root: GameRoot\*; **shrubId: Int;** }

struct ItemRef { root: GameRoot\*; **itemId: Int;** }

let myDB = AthariaDB();

Lets say we fill it like this:

Game:

-   Unit 1, hp 10, location (5, 6):

    -   items:

        -   Item 1, name "hello"

        -   Item 2, name "blah"

-   Unit 2, hp 15, location (9, 8):

    -   items:

        -   Item 3, name "bloop"

-   Shrub 1, location (3, 3)

Green means the structure is at version 1.

let gameRef = myDB.root; // gameRef owns a GameRef now.

let myGameSnapshot1 = gameRef.snapshot(); // this is actually a Game\*,
completely immutable

Lets say we then add an item to Unit 2. Things in blue are version 2.

Game:

-   Unit 1, hp 10, location (5, 6):

    -   items:

        -   Item 1, name "hello"

        -   Item 2, name "blah"

-   Unit 2, hp 15, location (9, 8):

    -   items:

        -   Item 3, name "bloop"

        -   Item 4, name "flopplewop"

-   Shrub 1, location (3, 3)

Now, myDB has \_workingVersion = 2 and a pointer to the version 2 Game.

But, myGameSnapshot1 still has a pointer to the immutable version 1
Game, which is still completely immutable.

It might seem like we do logN allocations every time we make a
modification. Imagine we change Unit 2's hp to 13. The naive, logN
approach:

Game:

-   Unit 1, hp 10, location (5, 6):

    -   items:

        -   Item 1, name "hello"

        -   Item 2, name "blah"

-   Unit 2, hp 15, location (9, 8):

    -   items:

        -   Item 3, name "bloop"

        -   Item 4, name "flopplewop"

-   Shrub 1, location (3, 3)

But, we didn't have to do that! Nobody ever had a snapshot to version 2.
Since we know nobody's watching, we can just modify it in place:

Game:

-   Unit 1, hp 10, location (5, 6):

    -   items:

        -   Item 1, name "hello"

        -   Item 2, name "blah"

-   Unit 2, hp 15, location (9, 8):

    -   items:

        -   Item 3, name "bloop"

        -   Item 4, name "flopplewop"

-   Shrub 1, location (3, 3)

Nobody had a snapshot, so nobody wanted things to be frozen in time, so
we just modify the working version of this data and we're good.

This is massively more performant. The naive approach meant that every
tiny modification cause logN allocations. The new approach means just
only allocating it if we need to. The naive approach isn't bounded in
how many allocations it will go through. The new approach means we only
ever allocate enough objects to store the difference between the last
snapshot and the current one.

More optimizations:

-   We can use branch prediction to predict that we wont be allocating
    > new things. That means that modifying things at HEAD is
    > blindlingly fast, amortized to free. However, if we're taking a
    > snapshot every second, then maybe we want to predict that we
    > \*will\* be allocating new things? Perhaps it should be an option?

-   We can keep a counter at HEAD of how many snapshots are in
    > existence. If there are none, that means we have a blank check to
    > modify any object in the entire chronobase. We can do that easily
    > just by setting the \_\_currentVersion back to 1.

### Implementation

Ivytree was designed with chronobase in mind. Here's how it would work.

The ivynode would be completely immutable, nothing would ever change in
it.

For this node:

> ivynode Unit {
>
> hp: Int;
>
> location: Coord;
>
> items: Vector:Item;
>
> }

after adding in all the hidden fields, it would look more like this:

> ivynode Unit {
>
> \_\_vptr: virtual pointer;
>
> \_\_owningCount: Int; // shared ref count
>
> \_\_versionNumber: Int;
>
> \_\_controller: UnitController\*;
>
> hp: Int;
>
> location: Coord;
>
> items: Vector:Item;
>
> }

We added 2 ints and a pointer, so 16 bytes.

The controller would have:

> ivycontroller UnitController {
>
> \_\_vptr: virtual pointer;
>
> \_\_modelCount: Int; // Count models that point at this controller
>
> \_\_borrowCount: Int; // Count outsiders, and ivynodes from current,
> borrow point at me
>
> \_\_weakRoster: \_\_WeakRoster\*; // We really should make this
> opt-in...
>
> \_\_root: RootController\*; // Points at the root so we can get
> currentversion.
>
> \_\_currentModel: Unit\*; // Point at the current Unit snapshot. It
> points back at us.
>
> \_\_ownerController: void\*; // Point at the controller that currently
> owns us.
>
> \_\_ownerFieldId: Int; // The owning controller's field. More
> specifically, the setter for it.
>
> }

We added 3 ints and 2-3 pointers, so 28-36 bytes. In optimized we get
rid of borrows, so 24-32.

Net space increase: 28 + 24n, where n is the number of historic
versions.

A controller needs to remain around if any models still refer to it.

When no more models point at a given controller, it's deleted.

The root controller will keep this data around:

-   \_\_currentVersion: Int;

If we're changing a field:

-   check if the version is equal to the current working version.

    -   if so, then just update the field, nbd

    -   otherwise, call the object's virtual mutate function, give it:

        -   a pointer to the current version

        -   this object's change-field function to update this field in
            > a new object

        -   owner ID

mutate function is specially made to know what struct this is. it:

-   makes a new struct, and assigns things into it

-   call the given change-field function, to overwrite in the new value
    > for whatever field it was

-   now we **try** to set the owner's pointer on it:

    -   check if the version is equal to the current working version:

        -   if so, then just update the field, with the owner's
            > change-field function

        -   otherwise, call the owner's virtual mutate function, give
            > it:

            -   a pointer to the owner

            -   the owner's change-field function to update the owner's
                > field in the new owner

            -   the owner's owner ID

you can see the recursion.

Modifiable references would be to the controllers. We also need a
companion pointer with it, both bundled into a fat pointer, so we can
access the current version int at the root.

If we knew it was a read-heavy tree, I wonder if we could get by without
having the fat pointer... we would just crawl up the tree until we get
to the root, modifying every single ancestor. Probably not a good idea.

### Snapshots

Can take a snapshot of the current state of the chronobase. This will
give you a pointer to the root of the underlying model. (this will
increment the \_\_owningCount, keeping it, and everything it indirectly
points to, alive). It will also increment the root controller's
\_\_currentVersion.

### Rewinding

We can rewind the entire chronobase to an arbitrary snapshot in the
past. We have to add the 'diffrewindable' or 'reversible' keywords to
choose between the two approaches:

**Diffing**

Do a crawl to find all the changes in the various objects, down far
enough until it hits something it knows it's the same. This is O(c), c =
changed objects.

The controllers can implement "modelChanged, modelDeleted, modelRevived"
methods which tells them when the world has been rewound. They have to
react to \*anything\* changing in any way.

**Reverse Mutating**

We can save all the mutations since the beginning of time (or keep a
Set:Int in root to know how far back our mutations have to go), and
replay them in reverse until we get back to the time we want.

If they need to keep very close track of whats changing, and keep
external state really consistent with the model, they'll probably have
to do this.

**Common Details**

These both will do all sorts of additions, deletions, and replacements,
updating the \_\_borrowCount and \_\_weakCount as they goes.

However, it will NOT reset the \_\_currentVersion to back then, it will
actually increment it by one. This is so we can leave any existing
snapshots (now in the "future") alone.

By taking a bunch of snapshots and then rewinding to one of the earlier
ones, we've effectively forked the database. It's just that when we
rewind, we've frozen the previous branch forever, we can't resume it.

### Forking

To fork a chronobase, we would clone every controller in existence.

We'd also need to make every model have a "controller ID" instead of a
Controller\*, and every root/fork would need to have its own
map\<ControllerID, Controller\*\>

We unfortunately need controllers for their borrow counting and weak
counting. But perhaps there can be a very very simplified version, with
no borrowing going on, only shared things going on, and that would be
able to fork really well. In that world:

let newDb = myDB.fork();

would increment myDB's \_workingVersion from 2 to 3. newDB points at the
same data as myDB, also with \_workingVersion 3. Then you modify things
as normal, and the two databases will naturally diverge.

or\...

Perhaps we can have a chronomap at every root that maps to the
controllers for this root. and every model will have an integer, not a
pointer.

### Sandbox

Let's say env is a gigantic chronobase in the sky.

fn myFunction(env: !Environment, thing: Int) {

otherFunction(env.sandbox())

}

you can do .sandbox() on either a thing you own, or a thing you borrow.
it will produce a thing ("sandbox") that \*they\* own. but when their
sandbox goes out of scope, it auto-rewinds back to your state.

So, you cant modify yours while their sandbox is active unfortunately.
But, this is a great way to do rewinds.

### Controller State

It might be useful for the controller to associate data with each
snapshot of an object. In this case, we should probably make something
that the model can subclass. This seems rather... extreme though.

### Notes

**Different ideas for keeping track of borrows**

Probably not a good idea to just disallow borrows.

Right now we have to update the borrows and weak counts whenever we
rewind; it's not free. But, braid does this, it bites that bullet.

We need to count, or keep track of somewhere, the list of borrow
references in the current version of the graph.

Considered each borrow reference being a circular pointer (bidirectional
linked list) and each object could know all the things that borrow it.
It means that any time we change a pointer, we're basically changing
three objects instead of one... pretty expensive. Very expensive.

We could have a giant parallel map for these at the root, but that's
horrifying.

We could count it in the model object. Specifically, we can have a
\_\_headBorrowCount and \_\_overallBorrowCount. Modifying at head would
increment or decrement both those counts, but releasing something from
the past would only decrement the second count. But that presents big
challenges for forking, rewinding, and snapshotting.

If we have rewind points, then we need to keep track of a
\_\_headBorrowCount, \_\_rewindBorrowCount, \_\_overallBorrowCount.
Whenever we modify something, we'd check if it was from before the
rewind, and if so, move \_\_headBorrowCount into \_\_rewindBorrowCount.

**Lifetimes**

Considered the roster being a "lifetime" thing, in other words, would
point at the earlist version too. Not that useful though.

**Object IDs**

Considered having a giant map\<ObjectId, HeadMetadata\*\> at HEAD.
Considered having a two dimensional array, and the ID would be sliced
into two indexes to say where in those to look for the head metadata
pointer. The reason for all of this is to save space, an ID is 4 bytes
while a pointer is 8 bytes.

**Rewinding Mutational DB**

Considered a "rewinding mutational" database, where we have snapshots,
and mutations forward from there. Little difference between this and
just wildwest diffing, because controllers would have to react to
seemingly random rewinds.

**Networking**

In networking, we won't even really need that many versions to make this
work, just a "last known server version" and a "client version" (HEAD).
We don't need the full power of snapshotting, but we can use it if we
want to.

Come to think of it, if we go the full reversible mutations route, we
don't even need a last known server version of the chronobase, we would
just do reverse mutations back to that point and then reapply them.

notes:

the chronobase as shown above can be nice for deeply comparing
structures, it makes it basically trivial. it's also very similar to the
immutable DAG, which could come in handy

non-deep version:

ChronoMapController

-   currentmap: ChronoMapLevelNodeController

-   version!: Int32

ChronoMapLevelNodeController:

-   version!: Int32

-   parent: ?ChronoMapLevelNodeController

-   root: ChronoMapController\*

-   currentSnapshot: ChronoMapLevelNodeSnapshot

ChronoMapLeafNodeController\<T\>:

-   version!: Int32

-   parent: ?ChronoMapLevelNodeController

-   root: ChronoMapController\*

-   currentSnapshot: ChronoMapLeafNodeSnapshot\<T\>

-   vtable\<T\>\*

IChronoMapNodeSnapshot:

-   version: Int32

ChronoMapLevelNodeSnapshot extends IChronoMapNodeSnapshot:

-   version: Int32

-   models: IChronoMapNodeSnapshot\*\[32\] // 256 bytes, 4 cache lines.
    > be sure to prefetch.

ChronoMapLeafNodeSnapshot\<T\> extends IChronoMapNodeSnapshot:

-   version: Int32

-   model: inline T

the mutabase should have a single growing pool for its history, 2gb in
size.

then there can be another 2gb slab for "current A"

then another 2gb slab for "current B"

current A is where this version's new things are allocated. anything in
there belongs to the current version, and can be modified at will.

once we snapshot, we lock down current A, and start doing our new things
in current B. hopefully, while we're working on current B, the sidekick
thread is copying things from current B to the history slab, in a
compaction-ish manner. it knows the only places that have references to
it (the ChronoMapLeafNodeSnapshots and the ChronoMapLeafNodeController)

perhaps it assembles an entire new map for that snapshot?

the version number can be something that's not a number but instead is a
pointer, to where in that pool is the most recent version. anything in
the most recent version can be later than that pointer. anything before
it is in a previous version.

also, anything before the previous version should be given to the
compactor to put into the eternal zone.

this way, the ChronoMapNodeController can just compare its model pointer
to the ChronoMapController's version marker

forkable version?

every pointer in the wild is fat, with a pointer to the root box, and an
id.

ChronoMapController

-   currentmap!: ImmHashMap\<UInt64, ChronoMapNodeController\>

-   version!: Int32

ChronoMapLeafNodeSnapshot\<T\> extends IChronoMapNodeSnapshot:

-   borrowCount: UInt32

-   model: non-inline T

model contains version: Int32

prod version:

ChronoMapController

-   currentmap!: ImmHashMap\<UInt64, model\>

model contains version: Int32

perhaps we could have a hash map for each subclass\...

is there any use for the deep version?

in the deep version do we need IDs for everything?

do snapshots have their owned children, or IDs?

when we take a snapshot, perhaps we can crawl the first N objects and
grab their IDs? it would save a TON of latency. our max is 1mb. in fact,
a followup task could do this. this could be expensive though.

and you know, if we're doing this, we might as well just throw the
entire thing into datastore. unless there are concerns about space
usage?

chronobases cant contain non-chronobases.
