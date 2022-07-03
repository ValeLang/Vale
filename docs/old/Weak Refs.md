Three kinds of references to mutable objects in V:

-   Owning (let x: Marine = ...)

-   Strong (let x: &Marine = ...)

-   Weak (let x: &&Marine = ...)

We make a new weak reference, like this:

> let m = Marine(); // m is an owning reference
>
> let w = &&m;

Keep in mind: we can\'t have a reference to another thread\'s mutable
objects.

### Option A: Object has a pointer to its own WeakTable

To have a weak reference to something, the pointee must implement
IWeakRefable (name TBD). When a structure implements IWeakRefable, it
will contain a hidden field:

> struct Marine implements IWeakRefable {
>
> WeakTable\* table;
>
> ...
>
> }

The WeakTable is simply a list of all the weak pointers pointing at this
object.

> struct WeakTable {
>
> i32 capacity;
>
> i32 numEntries;
>
> WeakRefFatPtr\* ptrs\[0\];
>
> }

At first, Marine\'s .table field will ~~point to null~~ point to a
global const WeakTable { 0, 0, \[\] }.

A weak reference is secretly a fat pointer:

> struct WeakRefFatPtr\<T\> {
>
> T\* target;
>
> int indexInTargetWeakTable;
>
> }

To make a new WeakRefFatPtr, we:

1.  If target\'s table\'s numEntries == capacity, double the size of the
    > target\'s table.

2.  Store the target\'s table\'s numEntries into the WeakRefFatPtr\'s
    > indexInTargetWeakTable.

3.  Add a pointer to this WeakRefFatPtr to the target\'s table.

To remove a WeakRefFatPtr, we conceptually:

1.  Check if we\'re the last entry. If so, delete the table. Otherwise:

2.  Swap our position in the target\'s table with the last entry in the
    > target\'s table.

3.  Remove ourselves from the target\'s table.

With optimizations, specifically (with \"myweak\" being our
WeakRefFatPtr):

-   if myweak-\>target-\>table-\>numEntries == 1:

    -   delete myweak-\>target-\>table

    -   myweak-\>target-\>table = null

-   else:

    -   let otherweak =
        > myweak-\>target-\>table-\>ptrs\[myweak-\>target-\>numEntries -
        > 1\]

    -   otherweak-\>indexInTargetWeakTable =
        > myweak-\>indexInTargetWeakTable.

    -   myweak-\>target-\>table-\>ptrs\[myweak-\>indexInTargetWeakTable\]
        > = otherweak;

    -   myweak-\>target-\>table-\>numEntries\--;

To destroy the target object, we set all WeakRefFatPtrs\' .target to
null:

> if (obj-\>table != null) {
>
> for (int i = 0 to obj-\>table-\>numEntries) {
>
> obj-\>table-\>ptrs\[i\]-\>target = null;
>
> }
>
> }

#### Threading Considerations

When we send a cluster to another thread, we\'d need to recurse over
everything in the cluster, nulling out any weakrefs that are outside the
cluster pointing in, and nulling out any weakrefs inside the cluster
pointing out.

-   Recursively go through the cluster, and put all objects into a
    > HashSet\<void\*\>.

-   Go through the cluster, and for each object:

    -   Look into its WeakTable. Null out incoming weakrefs that are not
        > part of the set.

    -   Look at the object\'s type info for the locations of all
        > weakrefs inside the object. For each weakref inside this
        > object:

        -   If it\'s pointing outside the cluster, null out the
            > reference.

-   Send the cluster to the other thread.

### Option B: Thread-global HashMap\<void\*, WeakTable\*\>

Similar to the previous, but instead of the object having a pointer to
its weaktable, we have a giant hash map per-thread, Map\<void\*,
WeakTable\> (called \"megamap\" in this doc). This would mean we don\'t
need an intrusive pointer, and can have a weakref to **any** object, and
it\'s a zero-cost abstraction, except for sending to other threads.

Benefits:

-   Don\'t need a pointer in every object.

-   Can have a weakref to **any** object.

-   Don\'t need to explicitly extend IWeakRefable.

Drawbacks:

-   To add or remove weakrefs, or destroy the object, the other approach
    > has an indirection to get the WeakTable, which might be a cache
    > miss. However, this approach has to look it up in a global hash
    > map:

    -   Need the hash map\'s size (probably hot in the cache).

    -   Need to read the entry of the hash map, almost definitely a
        > cache miss.

> So, let\'s say, this approach has 1.1 cache misses vs first
> approach\'s 0.5 cache misses.

#### Threading Considerations

When we send a cluster to another thread, we\'d have to recurse through
the cluster, and remove all the WeakTables from this thread\'s megamap.
Similar to other approach. Differences:

-   Instead of a HashSet\<void\*\>, we\'d have a HashMap\<void\*,
    > WeakTable\*\>. Similar to the megamap, but only for this cluster.
    > It\'s like a mini-megamap.

-   In the first loop, we\'d pull the WeakTable\* from the megamap and
    > into the mini-megamap.

-   When the other thread receives it, they\'ll incorporate the
    > mini-megamap into their megamap.

notes:

-   it\'s unfortunate that because of V\'s restrictions, we have to
    > recurse over every mutable object as it\'s sent across thread
    > boundaries... pony does it, so it can\'t be too bad.

-   if every mutable object has an i32 ID (might be needed for other
    > features) then the megamap can be a hashmap\<i32, WeakTable\*\>
    > and weakrefs can be only 8 bytes.

-   instead of dealing with recursion, we could just disallow sending
    > anything with weakrefs over thread boundaries? or perhaps **enable
    > this whole auto-nulling-on-send thing via compiler flag**? then
    > it\'d be truly zero-cost.
