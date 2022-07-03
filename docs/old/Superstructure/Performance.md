To optimize further:

-   Add a \"leaf cache\", an array of all the leaf nodes. This would
    > only have to be rootDomain / 32 references, and since rootDomain
    > is \<= 32n, this means that it would be \<=n references. Its slots
    > would \*always\* contain the most recent leaf, meaning that we can
    > do lookups in constant time, no branching, easy. It would speed up
    > modifying things that were already modified this incarnation, but
    > slightly slow down things that weren\'t. Definite win. Perhaps
    > have two versions of chronovector, one that\'s cached and one
    > that\'s not.

-   Once the leaf cache is in, implement an enumerator which simply uses
    > the index. Since reading is mega fast, this will speed up things
    > quite considerably. Or just use an index. Probably just use an
    > index.

-   Currently, addrepeating updates all nodes after the end of the
    > array. this could cause some cache misses (unless these
    > modifications are parallelized somehow). we should make it so it
    > only updates as many as it needs.

-   Switch to C++CLI so that the nodes can directly/inline have their
    > array of nodes, to eliminate an entire indirection. Can\'t do C#
    > because the fixed keyword only supports arrays of primitives.

-   [[https://www.dotnetperls.com/optimization]{.underline}](https://www.dotnetperls.com/optimization)

## \"When should I use a chronobase?\"

Are you ever snapshotting? If no, don\'t use chronobase.

Do you only ever revert to the previous state, such as in a database
transaction? If so, don\'t use chronobase, use linear, and it can
backtrack using the mutation log.

Do you need to keep multiple versions in memory at once? For example, if
you have multiple save states to go back to. If not, don\'t use
chronobase.

So, if you get here, you revert in interesting cases, and you want to
have multiple versions in memory at once.

See the speed section for more considerations.

**Memory**

If you go with a chronobase in this situation, the memory savings will
be massive.

Notes:

perhaps we can also measure memory usage. i know that for lower numbers
of modifications, the space savings is immense. once it gets to about
1/32 though (\~3%), or rather, high enough that we\'ll likely hit all
the chunks (needs statistics, varies with the total size and
randomness), our memory usage could actually hurt. perhaps the rule of
thumb will be to use a chronobase if you think youll modify \<3% of the
nodes in a given request. it does have a smoothing effect however, which
could be nice\... also, this is affected by locality; if you\'re making
a bunch of new nodes with an incrementing ID, and youll likely interact
with those, then things will go much easier for you because those are
all likely in the same chunk of the chronovector. also, for this reason,
different node types shouldnt share an ID pool. if we want to
differentiate then we can build it into the high bits and mask them out
before use.

chronobase excels when you want to compare two versions of history
without using a ton of space.

chronobase is a lot better for storing many versions in memory or on
disk, because of space sharing.

uses (1/32 to 33/32) \* 8n + 32d space per snapshot. naive uses n per
snapshot. assuming avg of 32 bytes per node\... naive uses 32n + 8n
bytes per snapshot.

32n + 8n \> (1/32 to 33/32)\*8n + 32d

assuming worse case, 33/32

40n \> (\<33/32)\*8n + 32d

40n \> (\<33/4)\*n + 32d

160n \> \<33n + 128d

\>127n \> 128d

thats basically\... differences vs total. we never took into account
before that the naive approach has a map too. ours is only a very tiny
bit bigger than theirs.

assuming best case, 1/32

40n \> (\>1/32)\*8n + 32d

160n \> \>n + 32d

\<159n \> 32d

massive savings in chronobase.

keep in mind evan, chronobase is targeting a very specific use case, for
which there are no good options today.

**Speed**

Let\'s say your database **table** has N nodes in it. Let\'s say that
you modify M% of the nodes, and then take a snapshot, and then modify M%
of the nodes, then take a snapshot, etc.

Benchmarks show:

-   With 16 elements, chrono is faster until 1 modifications/snapshot
    > (6%)

-   With 32 elements, chrono is faster until 1 modifications/snapshot
    > (3%)

-   With 64 elements, chrono is faster until 9 modifications/snapshot
    > (14%)

-   With 128 elements, chrono is faster until 30 modifications/snapshot
    > (23%)

-   With 256 elements, chrono is faster until 69 modifications/snapshot
    > (26%)

-   With 512 elements, chrono is faster until 73 modifications/snapshot
    > (14%)

-   With 1024 elements, chrono is faster until 96 modifications/snapshot
    > (9%)

-   With 2048 elements, chrono is faster until 203
    > modifications/snapshot (9%)

-   With 4096 elements, chrono is faster until 421
    > modifications/snapshot (10%)

-   With 8192 elements, chrono is faster until 564
    > modifications/snapshot (6%)

-   With 16384 elements, chrono is faster until 1376
    > modifications/snapshot (8%)

-   With 32768 elements, chrono is faster until 2613
    > modifications/snapshot (7%)

-   With 65536 elements, chrono is faster until 4839
    > modifications/snapshot (7%)

-   With 131072 elements, chrono is faster until 8123
    > modifications/snapshot (6%)

-   With 262144 elements, chrono is faster until 12497
    > modifications/snapshot (4%)

-   With 524288 elements, chrono is faster until 18031
    > modifications/snapshot (3%)

-   With 1048576 elements, chrono is faster until 21271
    > modifications/snapshot (2%)

-   With 2097152 elements, chrono is faster until 33146
    > modifications/snapshot (1%)

-   With 4194304 elements, chrono is faster until 48868
    > modifications/snapshot (1%)

Hence rule of thumb:

-   \<4096, chrono is faster until 10% modifications per snapshot

-   \<200k, chrono is faster until 5% modifications per snapshot

-   \<1mil, chrono is faster until 2% modifications per snapshot

-   \<4mil, chrono is faster until 1% modifications per snapshot

Extra considerations:

-   This assumes random additions and modifications. If the additions
    > and modifications are of objects near each other, the chronovector
    > does much better. For example, if in a level, the 100 units\' IDs
    > are all sequential next to each other, then chronovector will
    > handle this like a champ.

-   If you are adding and modifying and reading the same things over and
    > over, you can add a cache to speed chrono up pretty massively.

-   Chronobase smoothes out the performance cost over time. Reverting a
    > regular thing will be a massive hit to CPU all at once.
